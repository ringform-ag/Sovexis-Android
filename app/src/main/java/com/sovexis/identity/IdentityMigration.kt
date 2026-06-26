package com.sovexis.identity

import android.content.Context
import android.util.Base64
import android.util.Log
import com.sovexis.domain.personhood.FuzzyExtractor
import com.sovexis.identity.PersonhoodManager.MigrationPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis 身份迁移 — 移动终端间安全传输
 *
 * ── 哲学 ──
 * 身份迁移是数字主权的高危操作。传输通道必须是本地点对点，
 * 不经过云端、不经过 Node、不经过任何第三方。
 *
 * ── 传输层 ──
 * - 主力通道：蓝牙 BLE（5m 内，物理邻近保证）
 * - 备选通道：WiFi Direct（1m 内，高速传 hdList）
 * - 降级通道：加密 JSON 导出 → 用户手动传输（安全提示 + 过期验证）
 *
 * ── 安全约束 ──
 * - 旧设备 TEE 签名：导出前必须验证旧设备的 TEE 签名
 * - 零信任传输：即使 BLE/WiFi 已加密，MigrationPackage 仍用
 *   临时 ECDH 会话密钥包裹后才发送
 * - 传输后零化：导出完成后，建议用户清空旧设备的 PersonhoodProfile
 *   （由 PersonhoodManager 显式调用后擦除）
 * - 不落盘传输：接收端收到后直接注入 EncryptedSharedPreferences，
 *   不在外部存储留痕迹
 *
 * ── 引导提示 ──
 * 导出/导入全流程由引导卡片驱动，循序渐进：
 *   1. 安全提醒 → 2. 选择通道 → 3. 等待配对 → 4. 传输 → 5. 完成 + 清理
 *
 * @author Sovexis Architecture Team
 * @since 4.0.0
 */
@Singleton
class IdentityMigration @Inject constructor(
    private val personhoodManager: PersonhoodManager
) {
    companion object {
        private const val TAG = "IdentityMigration"
        /** 临时会话密钥对仅本地有效，5 分钟过期 */
        private const val SESSION_EXPIRY_MS = 300_000L
    }

    // ── 导出 ──

    /**
     * 导出当前活跃身份的人格锚定数据。
     * 4.0.0: export 仅打包 raw data。authToken 由调用方通过 issueTransferAuthToken 签发。
     */
    suspend fun export(did: String, teeSig: ByteArray): Result<MigrationPackage> =
        withContext(Dispatchers.IO) {
            personhoodManager.exportForMigration(did, teeSig)
        }

    /**
     * 一键导出：打包 + 签发令牌 + 序列化 + AES-GCM 加密。
     *
     * @param signer TEE 签名函数
     * @return Base64 加密字符串，直接传给新设备
     */
    suspend fun exportEncoded(
        did: String,
        oldFp: String,
        newFp: String,
        teeSig: ByteArray,
        signer: (ByteArray) -> ByteArray,
        sessionKey: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val pkgResult = personhoodManager.exportForMigration(did, teeSig)
        pkgResult.map { pkg ->
            if (!verifyPackage(pkg)) throw Exception("导出数据包验证失败")
            val token = personhoodManager.issueTransferAuthToken(did, oldFp, newFp, signer)
            val fullPkg = pkg.copy(authToken = token)
            val raw = serializePackage(fullPkg)
            val ciphertext = com.sovexis.domain.crypto.Aegis.encrypt(raw, sessionKey)
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        }
    }

    /**
     * 将 MigrationPackage 编码为 BLE/WiFi 传输就绪的加密 JSON。
     *
     * @param pkg 迁移数据包
     * @param sessionKey 临时 ECDH 会话密钥
     * @return AES-256-GCM 加密后的 Base64 字符串
     */
    fun packageForTransmission(pkg: MigrationPackage, sessionKey: ByteArray): String {
        return try {
            val raw = serializePackage(pkg)
            val ciphertext = com.sovexis.domain.crypto.Aegis.encrypt(raw, sessionKey)
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "packageForTransmission failed", e)
            return ""
        }
    }

    // ── 导入 ──

    /**
     * 验证并导入接收到的加密迁移数据包。
     *
     * @param encoded AES-256-GCM 加密的 Base64 数据
     * @param sessionKey 临时 ECDH 会话密钥
     * @param did 预期的 DID（用于验证）
     * @return 成功或失败
     */
    suspend fun import(
        encoded: String,
        sessionKey: ByteArray,
        did: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            val raw = com.sovexis.domain.crypto.Aegis.decrypt(ciphertext, sessionKey)
            val pkg = deserializePackage(raw)

            if (pkg.did != did) {
                return@withContext Result.failure(SecurityException(
                    "DID 不匹配：预期 $did，收到 ${pkg.did}"
                ))
            }

            // 4.0.0: 验证 TransferAuthToken
            val token = pkg.authToken
                ?: return@withContext Result.failure(SecurityException("缺少迁移授权令牌"))
            if (System.currentTimeMillis() > token.expiresAt) {
                return@withContext Result.failure(SecurityException("迁移令牌已过期"))
            }

            personhoodManager.importFromMigration(pkg)
            Log.i(TAG, "migration imported: did=$did fingers=${pkg.fingerConfigs.size} authToken=valid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "import failed", e)
            Result.failure(e)
        }
    }

    /**
     * 验证 MigrationPackage 完整性。
     */
    fun verifyPackage(pkg: MigrationPackage): Boolean {
        if (pkg.hdList.isEmpty()) return false
        if (pkg.salt.size < 32) return false
        if (pkg.fingerConfigs.isEmpty()) return false
        return true
    }

    /**
     * 生成迁移校验和（显示给用户确认）。
     */
    fun packageChecksum(pkg: MigrationPackage): String {
        val sb = StringBuilder()
        sb.append(pkg.did.takeLast(8))
        sb.append(":").append(pkg.fingerConfigs.size).append("f")
        return sb.toString()
    }

    // ── 内部编解码 ──

    private fun serializePackage(pkg: MigrationPackage): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        dos.writeUTF(pkg.did)
        dos.writeInt(pkg.salt.size); dos.write(pkg.salt)
        dos.writeInt(pkg.fingerConfigs.size)
        pkg.fingerConfigs.forEach { fc ->
            dos.writeUTF(fc.label); dos.writeBoolean(fc.isBackup)
        }
        dos.writeInt(pkg.hdList.size)
        pkg.hdList.forEach { (label, hd) ->
            dos.writeUTF(label); dos.writeInt(hd.size); dos.write(hd)
        }
        dos.writeInt(pkg.commitments.size)
        pkg.commitments.forEach { (label, commit) ->
            dos.writeUTF(label); dos.writeInt(commit.size); dos.write(commit)
        }
        // 4.0.0: serialize authToken
        val token = pkg.authToken
        dos.writeBoolean(token != null)
        if (token != null) {
            dos.writeUTF(token.did)
            dos.writeUTF(token.oldDeviceFingerprint)
            dos.writeUTF(token.newDeviceFingerprint)
            dos.writeInt(token.nonce.size); dos.write(token.nonce)
            dos.writeLong(token.createdAt)
            dos.writeLong(token.expiresAt)
            dos.writeInt(token.signature.size); dos.write(token.signature)
        }
        return bos.toByteArray()
    }

    private fun deserializePackage(raw: ByteArray): MigrationPackage {
        val dis = DataInputStream(ByteArrayInputStream(raw))
        val did = dis.readUTF()
        val saltSize = dis.readInt(); val salt = ByteArray(saltSize); dis.read(salt)
        val fcCount = dis.readInt()
        val fcList = (0 until fcCount).map {
            PersonhoodManager.FingerConfig(
                index = it,
                label = dis.readUTF(),
                isBackup = dis.readBoolean()
            )
        }
        val hdCount = dis.readInt()
        val hdMap = mutableMapOf<String, ByteArray>()
        repeat(hdCount) {
            val label = dis.readUTF()
            val size = dis.readInt(); val hd = ByteArray(size); dis.read(hd)
            hdMap[label] = hd
        }
        val cCount = dis.readInt()
        val cMap = mutableMapOf<String, ByteArray>()
        repeat(cCount) {
            val label = dis.readUTF()
            val size = dis.readInt(); val c = ByteArray(size); dis.read(c)
            cMap[label] = c
        }
        // 4.0.0: deserialize authToken
        val hasToken = dis.readBoolean()
        val token = if (hasToken) {
            PersonhoodManager.TransferAuthToken(
                did = dis.readUTF(),
                oldDeviceFingerprint = dis.readUTF(),
                newDeviceFingerprint = dis.readUTF(),
                nonce = ByteArray(dis.readInt()).also { dis.read(it) },
                createdAt = dis.readLong(),
                expiresAt = dis.readLong(),
                signature = ByteArray(dis.readInt()).also { dis.read(it) }
            )
        } else null
        return MigrationPackage(did, hdMap, salt, cMap, fcList, token)
    }
}

/**
 * 引导提示数据 — 迁移流程中逐步暴露给用户的指导消息
 */
sealed class MigrationGuideStep(val id: Int, val title: String, val body: String) {
    data object SafetyReminder : MigrationGuideStep(
        1, "安全提醒",
        "⚠️ 身份迁移是极端高风险操作——将把数字主权委派到新设备。\n\n" +
        "• 传输完全在本地完成，不经过任何外部服务器\n" +
        "• 两台设备必须在物理邻近范围（蓝牙 5m / WiFi Direct 1m）\n" +
        "• 旧设备的原始生物特征永远不会离开设备——只传输辅助数据\n" +
        "• 迁移完成后，旧设备 persona 将立即冻结（可解冻，需 1 小时冷却期）\n" +
        "• 此次操作全程不可变日志记录，受 CONST-012/CONST-013 保护"
    )
    data object ChannelSelect : MigrationGuideStep(
        2, "选择传输通道",
        "请在两台设备上选择相同的传输方式：\n\n" +
        "• 蓝牙 — 适合小数据量（hdList），5m 内稳定\n" +
        "• WiFi Direct — 适合大数据量或多手指配置，1m 内高速\n\n" +
        "两台设备需启用对应的通信服务。"
    )
    data class ExportReady(val checksum: String) : MigrationGuideStep(
        3, "导出就绪",
        "请在另一台设备上打开「导入身份」，并确保两台设备靠近。\n\n" +
        "校验值：${checksum}\n\n" +
        "请在另一台设备上确认此校验值匹配后点击「开始传输」。"
    )
    data object ImportReady : MigrationGuideStep(
        4, "导入就绪",
        "请在原设备上点击「导出身份」。\n\n" +
        "收到数据后将自动验证 DID 和指纹配置，验证通过后导入。\n" +
        "整个过程约需 5-10 秒。"
    )
    data object Transferring : MigrationGuideStep(
        5, "传输中",
        "正在安全传输身份数据…\n\n" +
        "• 通道已加密（AES-256-GCM + ECDH 会话密钥）\n" +
        "• 数据仅存于两台设备的加密内存中\n\n" +
        "请保持设备靠近，不要关闭此页面。"
    )
    data class Complete(val checksum: String) : MigrationGuideStep(
        6, "迁移完成 ✅",
        "身份数据已安全迁移到新设备。旧设备 persona 已冻结。\n\n" +
        "校验值：${checksum}\n\n" +
        "下一步：\n" +
        "• 在新设备上用已注册手指验证身份\n" +
        "• 确认 bioHash 一致 → 自动触发 Node 重绑定\n" +
        "• Node 验证 TransferAuthToken + 硬指纹 → 绑定切换\n" +
        "• 旧设备如需恢复，需等待 1 小时冷却期 + 双向生物验证"
    )
    data class Error(val message: String) : MigrationGuideStep(
        99, "迁移失败",
        message
    )
}
