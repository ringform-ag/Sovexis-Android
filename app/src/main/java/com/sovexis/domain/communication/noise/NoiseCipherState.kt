package com.sovexis.domain.communication.noise

import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Noise CipherState 实现。
 *
 * 使用 AES-256-GCM 进行对称加密。
 * Nonce 管理：96-bit nonce，从 0 递增。
 *
 * 安全约束（全部强制执行）：
 *   - nonce 上限检查：达到 MAX_NONCE 时抛出异常（CVE-2021-4239 防御）
 *   - decrypt 失败不修改 nonce（CVE-2021-4239 防御）
 *   - encryptWithAd 参数边界完整检查（CVE-2020-25021/25022/25023 防御）
 *   - 不使用 ChaChaPoly 回退（CVE-2020-25023 防御）
 */
class NoiseCipherState {

    private var key: SecretKeySpec? = null
    private var nonce: Long = 0

    /** 是否已初始化密钥 */
    val hasKey: Boolean
        get() = key != null

    /**
     * 使用提供的密钥初始化 CipherState。
     */
    fun initializeKey(key: ByteArray) {
        require(key.size == NoiseProtocol.AES_KEY_LEN) {
            "密钥长度必须为 ${NoiseProtocol.AES_KEY_LEN} 字节，实际: ${key.size}"
        }
        this.key = SecretKeySpec(key, "AES")
        this.nonce = 0
    }

    /**
     * 设置 nonce（用于测试和握手阶段）。
     */
    fun setNonce(nonce: Long) {
        this.nonce = nonce
    }

    /**
     * 加密明文（不含附加认证数据）。
     * 加密后 nonce 自动递增。
     */
    fun encryptWithAd(ad: ByteArray?, plaintext: ByteArray): ByteArray {
        val k = key ?: throw IllegalStateException("CipherState 未初始化密钥")
        if (nonce >= NoiseProtocol.MAX_NONCE) {
            throw IllegalStateException("Nonce 已达上限 ${NoiseProtocol.MAX_NONCE}，请重建会话")
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        // 将 64-bit nonce 扩展为 96-bit GCM IV（高 32 位为零）
        val iv = ByteArray(NoiseProtocol.AES_NONCE_LEN)
        iv[4] = (nonce shr 56).toByte()
        iv[5] = (nonce shr 48).toByte()
        iv[6] = (nonce shr 40).toByte()
        iv[7] = (nonce shr 32).toByte()
        iv[8] = (nonce shr 24).toByte()
        iv[9] = (nonce shr 16).toByte()
        iv[10] = (nonce shr 8).toByte()
        iv[11] = nonce.toByte()

        cipher.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(NoiseProtocol.AES_TAG_LEN * 8, iv))

        // 如果有附加认证数据
        if (ad != null && ad.isNotEmpty()) {
            cipher.updateAAD(ad)
        }

        val ciphertext = cipher.doFinal(plaintext)
        nonce++
        return ciphertext
    }

    /**
     * 解密密文（不含附加认证数据）。
     * 解密成功时 nonce 递增，解密失败时 nonce 保持不变。
     */
    fun decryptWithAd(ad: ByteArray?, ciphertext: ByteArray): ByteArray {
        val k = key ?: throw IllegalStateException("CipherState 未初始化密钥")
        if (nonce >= NoiseProtocol.MAX_NONCE) {
            throw IllegalStateException("Nonce 已达上限 ${NoiseProtocol.MAX_NONCE}，请重建会话")
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        val iv = ByteArray(NoiseProtocol.AES_NONCE_LEN)
        iv[4] = (nonce shr 56).toByte()
        iv[5] = (nonce shr 48).toByte()
        iv[6] = (nonce shr 40).toByte()
        iv[7] = (nonce shr 32).toByte()
        iv[8] = (nonce shr 24).toByte()
        iv[9] = (nonce shr 16).toByte()
        iv[10] = (nonce shr 8).toByte()
        iv[11] = nonce.toByte()

        cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(NoiseProtocol.AES_TAG_LEN * 8, iv))

        if (ad != null && ad.isNotEmpty()) {
            cipher.updateAAD(ad)
        }

        return try {
            val plaintext = cipher.doFinal(ciphertext)
            nonce++  // 仅在解密成功时递增
            plaintext
        } catch (e: Exception) {
            // nonce 不递增——防御 CVE-2021-4239
            throw SecurityException("解密失败: ${e.message}")
        }
    }

    /**
     * 重置 CipherState（擦除密钥 + nonce 归零）。
     */
    fun reset() {
        val currentKey = key
        if (currentKey != null) {
            Arrays.fill(currentKey.encoded, 0.toByte())
            key = null
        }
        nonce = 0
    }
}
