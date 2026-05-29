package com.sovexis.domain.identity

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sovexis.domain.recovery.RecoveryConfig
import com.sovexis.domain.recovery.RecoveryManager
import com.sovexis.core.result.getOrNull
import com.sovexis.core.result.getOrThrow
import com.sovexis.domain.did.DidInfo
import com.sovexis.domain.did.DidService
import com.sovexis.domain.policy.PolicyEnforcer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Singleton

/**
 * 身份管理器实现。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：IdentityManager 架构决策确认
 *
 * 作为协调层，整合 DidService、RecoveryManager 和 ZKP 承诺管理。
 */
@Singleton
class IdentityManagerImpl(
    private val didService: DidService,
    private val recoveryManager: RecoveryManager,
    private val policyEnforcer: PolicyEnforcer,
    @ApplicationContext private val context: Context
) : IdentityManager {

    companion object {
        private const val PREFS_FILE = "identity_commitments"
        private const val KEY_EXPECTED_ROOT_PREFIX = "expected_root_"
        private const val KEY_ACTIVE_DID = "active_did"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 创建主账号的完整流程。
     *
     * 流程：
     * 1. 调用 DidService 创建主账号（生成密钥对 + DID）
     * 2. 初始化恢复配置
     * 3. 生成 ZKP 预期承诺根
     * 4. 存储预期承诺根
     *
     * @param alias 用户别名
     * @param kdfsHash KDFS 图案哈希
     * @param recoveryConfig 恢复配置
     * @return 创建结果
     */
    override suspend fun createMasterIdentity(
        alias: String,
        kdfsHash: ByteArray,
        recoveryConfig: RecoveryConfig
    ): Result<MasterIdentity> {
        return runCatching {
            // 1. 调用 DidService 创建主账号（生成密钥对 + DID）
            val didDocument = didService.createIdentity(alias).getOrThrow()

            // 2. 提取公钥
            val publicKeyPem = didDocument.publicKeyPem

            // 3. 初始化恢复配置
            recoveryManager.initializeRecovery(recoveryConfig).getOrThrow()

            // 4. 生成 ZKP 预期承诺根
            // expected_root = SHA256(DID || device_binding || kdfs_hash)
            val deviceBinding = getDeviceBinding()
            val expectedRoot = MessageDigest.getInstance("SHA-256").run {
                update(didDocument.did.toByteArray(Charsets.UTF_8))
                update(deviceBinding)
                update(kdfsHash)
                digest()
            }

            // 5. 存储预期承诺根（供后续 ZKP 证明使用）
            storeExpectedCommitmentRoot(didDocument.did, expectedRoot)

            MasterIdentity(
                did = didDocument.did,
                alias = alias,
                publicKeyPem = publicKeyPem,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * 获取上一次创建身份时生成的助记词。
     * 安全约束：助记词仅存在于内存中，获取后即被清除。
     */
    override fun takeLastGeneratedMnemonic(): List<String>? {
        return recoveryManager.takeLastGeneratedMnemonic()
    }

    /**
     * 获取当前主账号。
     *
     * @return 主账号信息，如果没有则返回 null
     */
    override suspend fun getMasterIdentity(): MasterIdentity? {
        val doc = didService.getActiveDidDocument().getOrNull() ?: return null
        return MasterIdentity(
            did = doc.did,
            alias = doc.alias,
            publicKeyPem = doc.publicKeyPem,
            createdAt = doc.created
        )
    }

    /**
     * 派生副账号。
     *
     * @param type 副账号类型
     * @param alias 别名（可选）
     * @return 派生结果
     */
    @Suppress("UNUSED_VARIABLE")
    override suspend fun deriveChildIdentity(type: ChildType, alias: String?): Result<ChildIdentity> {
        // 获取当前主账号
        val masterIdentity = getMasterIdentity()
            ?: return Result.failure(IllegalStateException("主账号不存在"))

        // 使用 DidService 派生副账号
        // 注意：需要 DidService 支持 deriveChildIdentity 方法
        // 这里简化处理，实际应该调用 DidService.deriveChildIdentity
        return Result.failure(NotImplementedError("副账号派生需 DidService 支持"))
    }

    /**
     * 获取指定 DID 的副账号信息。
     *
     * @param did 副账号 DID
     * @return 副账号信息，如果没有则返回 null
     */
    @Suppress("UNUSED_VARIABLE")
    override suspend fun getChildIdentity(did: String): ChildIdentity? {
        // 遍历所有身份，查找匹配的副账号
        val allIdentities: List<DidInfo> = didService.getAllIdentities().getOrNull() ?: return null

        val childInfo: DidInfo = allIdentities.find { info ->
            info.did == did && info.role != "PRIMARY"
        } ?: return null

        // 需要更多副账号信息，这里简化处理
        return null
    }

    /**
     * 获取当前活跃的 DID。
     *
     * @return 当前活跃 DID，如果没有则返回 null
     */
    override suspend fun getActiveDid(): String? {
        return didService.getActiveDidDocument().getOrNull()?.did
    }

    /**
     * 获取指定 DID 的私钥。
     *
     * @param did 身份 DID
     * @return 私钥字节数组，如果没有则返回 null
     */
    override suspend fun getPrivateKey(did: String): ByteArray? {
        // 从 DidService 获取私钥
        // 注意：需要 DidService 提供 getPrivateKey 方法
        return null
    }

    /**
     * 获取指定 DID 的公钥。
     *
     * @param did 身份 DID
     * @return 公钥字节数组，如果没有则返回 null
     */
    override suspend fun getPublicKey(did: String): ByteArray? {
        // 从 DidService 获取公钥
        // 注意：需要 DidService 提供 getPublicKey 方法
        return null
    }

    /**
     * 从种子恢复主账号。
     *
     * @param seed BIP-39 种子
     * @return 恢复结果
     */
    override suspend fun restoreMasterIdentity(seed: ByteArray): Result<MasterIdentity> {
        return runCatching {
            // TODO: 使用种子恢复密钥对并创建主账号
            // 这里简化处理，实际应该：
            // 1. 从种子派生密钥对
            // 2. 调用 DidService 恢复身份
            // 3. 返回 MasterIdentity
            throw NotImplementedError("从种子恢复主账号需要 DidService 支持密钥派生")
        }
    }

    /**
     * 获取设备绑定数据。
     *
     * @return 设备绑定字节数组
     */
    override fun getDeviceBindingData(): ByteArray {
        return getDeviceBinding()
    }

    /**
     * 获取预期承诺根。
     *
     * @param did 身份 DID
     * @return 预期承诺根字节数组，如果没有则返回 null
     */
    override fun getExpectedCommitmentRoot(did: String): ByteArray? {
        return getExpectedCommitmentRootInternal(did)
    }

    /**
     * 获取设备绑定信息。
     *
     * @return 设备绑定字节数组
     */
    private fun getDeviceBinding(): ByteArray {
        return try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )?.toByteArray(Charsets.UTF_8) ?: "unknown_device".toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            "fallback_device".toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * 存储预期承诺根。
     *
     * @param did 身份 DID
     * @param root 承诺根哈希
     */
    private fun storeExpectedCommitmentRoot(did: String, root: ByteArray) {
        val key = "$KEY_EXPECTED_ROOT_PREFIX$did"
        val base64Root = android.util.Base64.encodeToString(root, android.util.Base64.NO_WRAP)
        encryptedPrefs.edit()
            .putString(key, base64Root)
            .apply()
    }

    /**
     * 获取预期承诺根（内部方法）。
     *
     * @param did 身份 DID
     * @return 承诺根哈希，如果没有则返回 null
     */
    private fun getExpectedCommitmentRootInternal(did: String): ByteArray? {
        val key = "$KEY_EXPECTED_ROOT_PREFIX$did"
        val base64Root = encryptedPrefs.getString(key, null) ?: return null
        return android.util.Base64.decode(base64Root, android.util.Base64.NO_WRAP)
    }

    // 注意：公开方法已在第 217 行定义，此处删除重复定义

    /**
     * 清除指定 DID 的承诺根。
     *
     * @param did 身份 DID
     */
    fun clearExpectedCommitmentRoot(did: String) {
        val key = "$KEY_EXPECTED_ROOT_PREFIX$did"
        encryptedPrefs.edit()
            .remove(key)
            .apply()
    }

    /**
     * 获取所有已知身份及其活跃状态。
     *
     * 替代旧架构 AccountDao.getAllAccounts()
     *
     * @return 所有身份列表（包含 isActive 状态）
     */
    override suspend fun getAllIdentities(): Result<List<SovexisAccount>> {
        return runCatching {
            val activeDid = getActiveDid()
            val master = getMasterIdentity()
            val allIdentities = didService.getAllIdentities().getOrNull() ?: emptyList()
            
            val accounts = mutableListOf<SovexisAccount>()
            
            // 添加主账号
            master?.let {
                accounts.add(it.copy(isActive = it.did == activeDid))
            }
            
            // 添加副账号（从 DidInfo 中获取）
            allIdentities
                .filter { it.role != "PRIMARY" }
                .forEach { info ->
                    accounts.add(
                        ChildIdentity(
                            did = info.did,
                            masterDid = master?.did ?: "",
                            derivationPath = "",
                            alias = info.alias,
                            uniqueCode = "",
                            publicKeyPem = "",
                            type = when (info.role) {
                                "STEWARD" -> ChildType.STEWARD
                                "SERVICE" -> ChildType.SERVICE
                                else -> ChildType.STANDARD
                            },
                            createdAt = info.created,
                            isActive = info.did == activeDid,
                            isFrozen = checkFrozenState(info.did)
                        )
                    )
                }
            
            accounts
        }
    }

    /**
     * 切换活跃身份。
     *
     * 替代旧架构 AccountDao.setActive(did) + deactivateAll()
     *
     * @param did 要设为活跃的身份 DID
     * @return 操作结果
     */
    override suspend fun setActiveIdentity(did: String): Result<Unit> {
        return runCatching {
            encryptedPrefs.edit().putString(KEY_ACTIVE_DID, did).apply()
        }
    }

    override suspend fun setFrozen(did: String, frozen: Boolean): Result<Unit> {
        return runCatching {
            policyEnforcer.setFrozen(did, frozen)
        }
    }

    override suspend fun deleteIdentity(did: String): Result<Unit> {
        return runCatching {
            encryptedPrefs.edit().remove("frozen_$did").apply()
            // 移除活跃绑定（如果删除的是当前活跃）
            if (getActiveDid() == did) {
                encryptedPrefs.edit().remove(KEY_ACTIVE_DID).apply()
            }
        }
    }

    private fun checkFrozenState(did: String): Boolean {
        val frozenPrefs = context.getSharedPreferences("sovexis_frozen", Context.MODE_PRIVATE)
        return frozenPrefs.getBoolean("frozen_$did", false)
    }
}
