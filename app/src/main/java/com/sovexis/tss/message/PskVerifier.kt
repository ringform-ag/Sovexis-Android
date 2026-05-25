package com.sovexis.tss.message

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * PSK 预绑定校验器
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: 阈值签名模块 BLE Client 模式重写指令 (陵谦)
 *
 * 为防御芯片级 BLE 漏洞（CVE-2025-44557），在 BLE 链路层之上
 * 提供应用层二次认证。
 *
 * PSK 在首次配对时通过二维码或 NFC 交换，之后每次连接建立时
 * 双方交换 HMAC(PSK, session_nonce) 完成校验。
 *
 * 安全特性：
 * - PSK 使用 EncryptedSharedPreferences 加密存储
 * - HMAC-SHA256 用于挑战-响应验证
 * - 32 字节随机 PSK，SecureRandom 生成
 */
class PskVerifier(private val context: Context) {

    companion object {
        private const val PREF_NAME = "sovexis_psk"
        private const val KEY_PSK = "pre_shared_key"
        private const val PSK_LENGTH = 32
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val NONCE_LENGTH = 32
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            PREF_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** PSK 是否已设置 */
    val isConfigured: Boolean
        get() = prefs.contains(KEY_PSK)

    /**
     * 生成新的 PSK 并返回二维码数据（供首次配对使用）
     *
     * @return Pair<PSK 字节数组, 二维码 Bitmap>
     */
    fun generatePsk(): Pair<ByteArray, Bitmap> {
        val psk = ByteArray(PSK_LENGTH).also { SecureRandom().nextBytes(it) }
        val pskBase64 = Base64.encodeToString(psk, Base64.NO_WRAP)
        prefs.edit().putString(KEY_PSK, pskBase64).apply()

        // 生成二维码
        val qrData = "sovexis-psk:$pskBase64"
        val bitmap = BarcodeEncoder().encodeBitmap(qrData, BarcodeFormat.QR_CODE, 512, 512)
        return psk to bitmap
    }

    /**
     * 从扫描的二维码导入 PSK
     *
     * @param qrContent 二维码内容，格式: "sovexis-psk:<base64>"
     * @return Result<Unit> 导入结果
     */
    fun importPsk(qrContent: String): Result<Unit> {
        return runCatching {
            if (!qrContent.startsWith("sovexis-psk:")) {
                throw IllegalArgumentException("无效的 PSK 二维码格式")
            }
            val pskBase64 = qrContent.removePrefix("sovexis-psk:")
            val psk = Base64.decode(pskBase64, Base64.NO_WRAP)
            if (psk.size != PSK_LENGTH) {
                throw IllegalArgumentException("PSK 长度不正确: ${psk.size}，期望: $PSK_LENGTH")
            }
            prefs.edit().putString(KEY_PSK, pskBase64).apply()
        }
    }

    /**
     * 计算挑战值：HMAC-SHA256(PSK, nonce)
     *
     * @param nonce 随机 nonce（32 字节）
     * @return HMAC 结果（32 字节）
     */
    fun computeChallenge(nonce: ByteArray): ByteArray {
        val psk = getPsk()
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(psk, HMAC_ALGORITHM))
        return mac.doFinal(nonce)
    }

    /**
     * 计算期望的响应值：HMAC-SHA256(PSK, nonce + 0x01)
     *
     * @param nonce 随机 nonce（32 字节）
     * @return HMAC 结果（32 字节）
     */
    fun computeExpectedResponse(nonce: ByteArray): ByteArray {
        val psk = getPsk()
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(psk, HMAC_ALGORITHM))
        mac.update(nonce)
        mac.update(1.toByte())
        return mac.doFinal()
    }

    /**
     * 生成随机 nonce
     *
     * @return 32 字节随机 nonce
     */
    fun generateNonce(): ByteArray {
        return ByteArray(NONCE_LENGTH).also { SecureRandom().nextBytes(it) }
    }

    /**
     * 验证对端的响应
     *
     * @param nonce 发送的 nonce
     * @param response 对端返回的响应
     * @return true 如果验证通过
     */
    fun verifyResponse(nonce: ByteArray, response: ByteArray): Boolean {
        val expected = computeExpectedResponse(nonce)
        return response.contentEquals(expected)
    }

    /**
     * 获取已存储的 PSK
     *
     * @throws IllegalStateException 如果 PSK 未配置
     */
    private fun getPsk(): ByteArray {
        val pskBase64 = prefs.getString(KEY_PSK, null)
            ?: throw IllegalStateException("PSK 未配置，请先执行 generatePsk() 或 importPsk()")
        return Base64.decode(pskBase64, Base64.NO_WRAP)
    }

    /**
     * 清除 PSK（重置配对时使用）
     */
    fun clearPsk() {
        prefs.edit().remove(KEY_PSK).apply()
    }

    /**
     * 导出 PSK 为 Base64 字符串（用于备份或传输）
     *
     * @return Base64 编码的 PSK，如果未配置则返回 null
     */
    fun exportPsk(): String? {
        return prefs.getString(KEY_PSK, null)
    }
}
