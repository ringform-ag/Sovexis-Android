package com.sovexis.domain.communication.noise

import org.junit.Assert.*
import org.junit.Test

/**
 * NoiseCipherState 单元测试
 *
 * 测试项目：
 * - 基本加密/解密流程
 * - Nonce 递增验证
 * - 解密失败不修改 nonce（CVE-2021-4239 防御）
 * - 密钥重置
 */
class NoiseCipherStateTest {

    @Test
    fun `test basic encryption and decryption`() {
        val cipher = NoiseCipherState()
        val key = ByteArray(NoiseProtocol.AES_KEY_LEN) { it.toByte() }
        cipher.initializeKey(key)

        val plaintext = "Hello, Noise Protocol!".toByteArray()
        val ciphertext = cipher.encryptWithAd(null, plaintext)

        // 密文应该包含认证标签（16字节）
        assertEquals(plaintext.size + NoiseProtocol.AES_TAG_LEN, ciphertext.size)

        // 解密验证
        val cipher2 = NoiseCipherState()
        cipher2.initializeKey(key)
        val decrypted = cipher2.decryptWithAd(null, ciphertext)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `test nonce increments after encryption`() {
        val cipher = NoiseCipherState()
        val key = ByteArray(NoiseProtocol.AES_KEY_LEN) { 0x01 }
        cipher.initializeKey(key)

        val plaintext = ByteArray(32) { 0xAA.toByte() }

        // 加密 100 条消息
        val ciphertexts = mutableListOf<ByteArray>()
        repeat(100) {
            ciphertexts.add(cipher.encryptWithAd(null, plaintext))
        }

        // 验证每条密文都不同（nonce 递增导致）
        for (i in 1 until ciphertexts.size) {
            assertFalse("密文 $i 应该与之前的不同", ciphertexts[i].contentEquals(ciphertexts[i - 1]))
        }
    }

    @Test
    fun `test nonce does not increment on decryption failure`() {
        val cipher = NoiseCipherState()
        val key = ByteArray(NoiseProtocol.AES_KEY_LEN) { 0x02 }
        cipher.initializeKey(key)

        val plaintext = "Test message".toByteArray()
        val ciphertext = cipher.encryptWithAd(null, plaintext)

        // 创建新的 cipher 实例用于解密
        val decryptCipher = NoiseCipherState()
        decryptCipher.initializeKey(key)

        // 第一次成功解密
        val decrypted1 = decryptCipher.decryptWithAd(null, ciphertext)
        assertArrayEquals(plaintext, decrypted1)

        // 创建另一个 cipher 实例，尝试解密篡改的密文
        val decryptCipher2 = NoiseCipherState()
        decryptCipher2.initializeKey(key)

        val tamperedCiphertext = ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0] + 1).toByte()

        // 解密应该失败
        assertThrows(SecurityException::class.java) {
            decryptCipher2.decryptWithAd(null, tamperedCiphertext)
        }

        // 现在用正确的密文解密应该成功（nonce 没有递增）
        val decrypted2 = decryptCipher2.decryptWithAd(null, ciphertext)
        assertArrayEquals(plaintext, decrypted2)
    }

    @Test
    fun `test cipher state reset`() {
        val cipher = NoiseCipherState()
        val key = ByteArray(NoiseProtocol.AES_KEY_LEN) { 0x03 }
        cipher.initializeKey(key)

        assertTrue(cipher.hasKey)

        cipher.reset()

        assertFalse(cipher.hasKey)
        assertThrows(IllegalStateException::class.java) {
            cipher.encryptWithAd(null, ByteArray(10))
        }
    }

    @Test
    fun `test encryption with additional data`() {
        val cipher = NoiseCipherState()
        val key = ByteArray(NoiseProtocol.AES_KEY_LEN) { 0x04 }
        cipher.initializeKey(key)

        val plaintext = "Secret message".toByteArray()
        val ad = "Additional authenticated data".toByteArray()

        val ciphertext = cipher.encryptWithAd(ad, plaintext)

        // 使用相同的 AD 解密
        val cipher2 = NoiseCipherState()
        cipher2.initializeKey(key)
        val decrypted = cipher2.decryptWithAd(ad, ciphertext)
        assertArrayEquals(plaintext, decrypted)

        // 使用不同的 AD 解密应该失败
        val cipher3 = NoiseCipherState()
        cipher3.initializeKey(key)
        val wrongAd = "Wrong AD".toByteArray()
        assertThrows(SecurityException::class.java) {
            cipher3.decryptWithAd(wrongAd, ciphertext)
        }
    }

    private fun <T : Throwable> assertThrows(expectedType: Class<T>, block: () -> Unit) {
        try {
            block()
            fail("Expected ${expectedType.simpleName} to be thrown")
        } catch (e: Throwable) {
            if (!expectedType.isInstance(e)) {
                fail("Expected ${expectedType.simpleName} but got ${e.javaClass.simpleName}")
            }
        }
    }
}
