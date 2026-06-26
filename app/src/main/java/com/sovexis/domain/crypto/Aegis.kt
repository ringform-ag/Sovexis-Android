package com.sovexis.domain.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * Sovexis 临时加密器 — 仅用于身份迁移传输层包裹。
 *
 * AES-256-GCM with 12-byte IV prepended to ciphertext.
 * 这不是持久化加密方案，仅用于两台设备间一次性的安全传输。
 *
 * @author Sovexis Architecture Team
 * @since 4.0.0 — 身份迁移传输加密
 */
object Aegis {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BYTES = 32
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN = 128

    /**
     * Encrypts plaintext with AES-256-GCM.
     * Returns: iv(12) || ciphertext || tag(16)
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
        val spec = GCMParameterSpec(GCM_TAG_LEN, iv)
        val secretKey = SecretKeySpec(key.copyOf(KEY_SIZE_BYTES), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /**
     * Decrypts ciphertext (iv(12) || ciphertext || tag(16)) with AES-256-GCM.
     */
    fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray {
        if (ciphertext.size < GCM_IV_LEN + 16) {
            throw IllegalArgumentException("密文太短")
        }
        val iv = ciphertext.copyOfRange(0, GCM_IV_LEN)
        val ct = ciphertext.copyOfRange(GCM_IV_LEN, ciphertext.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LEN, iv)
        val secretKey = SecretKeySpec(key.copyOf(KEY_SIZE_BYTES), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ct)
    }
}
