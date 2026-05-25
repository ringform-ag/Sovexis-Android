package com.sovexis.domain.communication.noise

import java.security.MessageDigest
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Noise SymmetricState 实现。
 *
 * 管理混合哈希和 CipherState。
 * 核心操作：
 *   - InitializeSymmetric(protocol_name): 初始哈希 = HASH(protocol_name)
 *   - MixKey(ikm): 通过 HKDF 派生新密钥
 *   - MixHash(data): 混合数据到哈希中
 *   - EncryptAndHash(plaintext): 加密明文并混合哈希
 *   - DecryptAndHash(ciphertext): 解密密文并混合哈希
 *   - Split(): 派生发送/接收密钥对
 */
class NoiseSymmetricState(
    private val protocolName: String
) {
    private var hash: ByteArray
    private var ck: ByteArray            // 链密钥
    val cipherState = NoiseCipherState()

    init {
        // InitializeSymmetric
        hash = MessageDigest.getInstance("SHA-256").digest(protocolName.toByteArray())
        ck = hash.copyOf()
    }

    /**
     * MixKey(ikm):
     * ck, temp_k = HKDF(ck, ikm)
     * 初始化或重新初始化 cipherState 使用 temp_k
     */
    fun mixKey(ikm: ByteArray) {
        val (newCk, tempK) = hkdf(ck, ikm)
        ck = newCk
        // 如果 IKM 为空（预共享密钥缺失），不初始化 CipherState
        if (ikm.isNotEmpty()) {
            cipherState.initializeKey(tempK)
        }
    }

    /**
     * MixHash(data):
     * hash = HASH(hash || data)
     */
    fun mixHash(data: ByteArray) {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(hash)
        md.update(data)
        hash = md.digest()
    }

    /**
     * EncryptAndHash(plaintext):
     * ciphertext = EncryptWithAd(hash, plaintext)
     * MixHash(ciphertext)
     * 返回 ciphertext
     */
    fun encryptAndHash(plaintext: ByteArray): ByteArray {
        val ciphertext = cipherState.encryptWithAd(hash, plaintext)
        mixHash(ciphertext)
        return ciphertext
    }

    /**
     * DecryptAndHash(ciphertext):
     * plaintext = DecryptWithAd(hash, ciphertext)
     * MixHash(ciphertext)
     * 返回 plaintext
     */
    fun decryptAndHash(ciphertext: ByteArray): ByteArray {
        val plaintext = cipherState.decryptWithAd(hash, ciphertext)
        mixHash(ciphertext)
        return plaintext
    }

    /**
     * Split():
     * temp_k1, temp_k2 = HKDF(ck, zerolen)
     * 返回 Pair<发送密钥, 接收密钥>
     */
    fun split(): Pair<ByteArray, ByteArray> {
        val (_, tempK1) = hkdf(ck, ByteArray(0))
        val (_, tempK2) = hkdf(ck, tempK1)
        // 如果 temp_k1 不存在（即 ck 在密钥交换后为空），使用空操作 HKDF
        return if (tempK1.isNotEmpty()) tempK1 to tempK2 else tempK2 to tempK2
    }

    /**
     * 获取当前握手哈希值。
     */
    fun getHandshakeHash(): ByteArray = hash.copyOf()

    // ── HKDF 辅助 ──
    // HKDF(salt, ikm) → (output1, output2)
    private fun hkdf(salt: ByteArray, ikm: ByteArray): Pair<ByteArray, ByteArray> {
        val prk = hmacSha256(salt, ikm)
        val output1 = hmacSha256(prk, byteArrayOf(0x01))
        val output2 = hmacSha256(prk, output1 + byteArrayOf(0x02))
        return output1 to output2
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
