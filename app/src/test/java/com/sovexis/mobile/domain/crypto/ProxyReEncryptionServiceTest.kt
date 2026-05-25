package com.sovexis.mobile.domain.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sovexis 代理重加密 (PRE) 单元测试
 *
 * [AI-GENERATED] 陵谦测试用例
 * 生成时间: 2026-05-20
 *
 * 测试覆盖：
 * 1. 密钥对生成
 * 2. 加密/解密流程
 * 3. 完整 PRE 流程（Alice加密 -> 生成重加密密钥 -> 代理转换 -> Bob解密）
 * 4. 错误处理
 */
class ProxyReEncryptionServiceTest {

    private val service = ProxyReEncryptionServiceImpl()

    @Test
    fun testGenerateKeyPair() {
        val result = service.generateKeyPair()

        assertTrue(result.isSuccess)
        val keys = result.getOrThrow()

        // 验证公钥格式（未压缩，65字节）
        assertEquals(65, keys.publicKey.size)
        assertEquals(0x04.toByte(), keys.publicKey[0])  // 未压缩格式标识

        // 验证私钥格式（32字节）
        assertEquals(32, keys.privateKey.size)
    }

    @Test
    fun testEncryptDecrypt() {
        // 生成密钥对
        val aliceKeys = service.generateKeyPair().getOrThrow()

        // 加密数据
        val originalData = "Hello Sovexis PRE!".toByteArray()
        val encrypted = service.encrypt(originalData, aliceKeys.publicKey).getOrThrow()

        // 验证加密结果格式
        assertTrue(encrypted.ciphertext.isNotEmpty())
        assertEquals(12, encrypted.iv.size)  // GCM IV 长度
        assertEquals(65, encrypted.ephemeralPublicKey.size)  // 未压缩公钥

        // 解密数据
        val decrypted = service.decrypt(encrypted, aliceKeys.privateKey).getOrThrow()

        // 验证解密结果
        assertEquals("Hello Sovexis PRE!", String(decrypted))
    }

    @Test
    fun testFullPreFlow() {
        // ========== 1. Alice 生成密钥 ==========
        val aliceKeys = service.generateKeyPair().getOrThrow()

        // ========== 2. Bob 生成密钥 ==========
        val bobKeys = service.generateKeyPair().getOrThrow()

        // ========== 3. Alice 用自己的公钥加密数据 ==========
        val originalData = "Secret message for Bob".toByteArray()
        val encrypted = service.encrypt(originalData, aliceKeys.publicKey).getOrThrow()

        // 验证 Alice 可以解密自己的密文
        val aliceDecrypted = service.decrypt(encrypted, aliceKeys.privateKey).getOrThrow()
        assertEquals("Secret message for Bob", String(aliceDecrypted))

        // ========== 4. Alice 生成重加密密钥 (from Alice -> to Bob) ==========
        val reKey = service.generateReEncryptionKey(
            aliceKeys.privateKey,
            bobKeys.publicKey
        ).getOrThrow()

        // 验证重加密密钥格式（65字节，未压缩）
        assertEquals(65, reKey.keyBytes.size)

        // ========== 5. 代理服务器执行重加密 ==========
        val reEncrypted = service.reEncrypt(encrypted, reKey).getOrThrow()

        // 验证重加密后 ephemeralPublicKey 发生变化
        assertTrue(
            "重加密后 ephemeralPublicKey 应该改变",
            !encrypted.ephemeralPublicKey.contentEquals(reEncrypted.ephemeralPublicKey)
        )

        // 验证密文和 IV 保持不变
        assertTrue(encrypted.ciphertext.contentEquals(reEncrypted.ciphertext))
        assertTrue(encrypted.iv.contentEquals(reEncrypted.iv))

        // ========== 6. Bob 无法用 Alice 的密文解密（验证代理转换的必要性）==========
        val bobFailsToDecryptOriginal = service.decrypt(encrypted, bobKeys.privateKey)
        assertTrue(bobFailsToDecryptOriginal.isFailure)

        // ========== 7. Bob 可以解密重加密后的密文 ==========
        val bobDecrypted = service.decrypt(reEncrypted, bobKeys.privateKey).getOrThrow()
        assertEquals("Secret message for Bob", String(bobDecrypted))

        // ========== 8. 验证解密结果与原始数据一致 ==========
        assertEquals(originalData.contentToString(), bobDecrypted.contentToString())
    }

    @Test
    fun testReEncryptionKeyGeneration() {
        val aliceKeys = service.generateKeyPair().getOrThrow()
        val bobKeys = service.generateKeyPair().getOrThrow()

        val reKey = service.generateReEncryptionKey(
            aliceKeys.privateKey,
            bobKeys.publicKey
        ).getOrThrow()

        // 验证重加密密钥格式
        assertEquals(65, reKey.keyBytes.size)
        assertEquals(0x04.toByte(), reKey.keyBytes[0])  // 未压缩格式标识
    }

    @Test
    fun testMultipleReEncryptionKeys() {
        val aliceKeys = service.generateKeyPair().getOrThrow()
        val bobKeys = service.generateKeyPair().getOrThrow()
        val charlieKeys = service.generateKeyPair().getOrThrow()

        // Alice -> Bob 的重加密密钥
        val reKeyAB = service.generateReEncryptionKey(
            aliceKeys.privateKey,
            bobKeys.publicKey
        ).getOrThrow()

        // Alice -> Charlie 的重加密密钥
        val reKeyAC = service.generateReEncryptionKey(
            aliceKeys.privateKey,
            charlieKeys.publicKey
        ).getOrThrow()

        // 验证两个重加密密钥不同
        assertTrue(!reKeyAB.keyBytes.contentEquals(reKeyAC.keyBytes))

        // 加密数据
        val data = "Test data".toByteArray()
        val encrypted = service.encrypt(data, aliceKeys.publicKey).getOrThrow()

        // Bob 使用 AB 密钥可以解密
        val reEncryptedAB = service.reEncrypt(encrypted, reKeyAB).getOrThrow()
        val bobDecrypted = service.decrypt(reEncryptedAB, bobKeys.privateKey).getOrThrow()
        assertEquals("Test data", String(bobDecrypted))

        // Charlie 使用 AC 密钥可以解密
        val reEncryptedAC = service.reEncrypt(encrypted, reKeyAC).getOrThrow()
        val charlieDecrypted = service.decrypt(reEncryptedAC, charlieKeys.privateKey).getOrThrow()
        assertEquals("Test data", String(charlieDecrypted))
    }

    @Test
    fun testLargeDataEncryption() {
        val aliceKeys = service.generateKeyPair().getOrThrow()

        // 测试较大数据（1MB）
        val largeData = ByteArray(1024 * 1024) { (it % 256).toByte() }
        val encrypted = service.encrypt(largeData, aliceKeys.publicKey).getOrThrow()
        val decrypted = service.decrypt(encrypted, aliceKeys.privateKey).getOrThrow()

        assertTrue(decrypted.contentEquals(largeData))
    }

    @Test
    fun testEmptyDataEncryption() {
        val aliceKeys = service.generateKeyPair().getOrThrow()

        // 测试空数据
        val emptyData = ByteArray(0)
        val encrypted = service.encrypt(emptyData, aliceKeys.publicKey).getOrThrow()
        val decrypted = service.decrypt(encrypted, aliceKeys.privateKey).getOrThrow()

        assertTrue(decrypted.isEmpty())
    }

    @Test
    fun testInvalidPublicKey() {
        val aliceKeys = service.generateKeyPair().getOrThrow()

        // 测试无效公钥
        val invalidPublicKey = ByteArray(65) { 0x04 }
        val result = service.encrypt("Test".toByteArray(), invalidPublicKey)

        // 应该失败（无效的曲线点）
        assertTrue(result.isFailure)
    }

    @Test
    fun testKeyPairUniqueness() {
        // 生成多对密钥，验证每对都不同
        val keys1 = service.generateKeyPair().getOrThrow()
        val keys2 = service.generateKeyPair().getOrThrow()

        assertTrue(!keys1.publicKey.contentEquals(keys2.publicKey))
        assertTrue(!keys1.privateKey.contentEquals(keys2.privateKey))
    }
}
