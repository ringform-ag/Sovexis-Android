package com.sovexis.tss.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.util.Arrays

/**
 * ShareStorage 合约测试套件
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: Sovexis · AndroidKeystoreShareStorage 完整实现指令 (陵谦)
 *
 * 测试覆盖：
 * - TSS-STORE-001: 写入后读取一致
 * - TSS-STORE-002: 擦除后不可恢复
 * - TSS-STORE-003: 错误会话 ID 解密失败
 * - TSS-STORE-004: StrongBox 可用性检测
 * - TSS-STORE-005: 安全警告在无 StrongBox 时非空
 *
 * 测试环境: Android Instrumentation Test（需要真实 Android 环境）
 */
@RunWith(AndroidJUnit4::class)
class ShareStorageContractTest {

    private lateinit var storage: AndroidKeystoreShareStorage
    private val testMasterDid = "did:sovexis:test123"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = AndroidKeystoreShareStorage(context)
    }

    /**
     * TSS-STORE-001: 写入后读取一致
     *
     * 验证：双层加密后写入的份额，使用相同的生物认证会话 ID 可以正确读取
     */
    @Test
    fun `TSS-STORE-001 写入后读取一致`() = runBlocking {
        val shareId = "test-share-001"
        val plainShare = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val sessionId = ByteArray(16).also { SecureRandom().nextBytes(it) }

        // 保存（注意：saveWithBiometricSession 会擦除 plainShare）
        val shareCopy = plainShare.copyOf()
        storage.saveWithBiometricSession(shareId, shareCopy, sessionId, testMasterDid).getOrThrow()

        // 加载
        val loaded = storage.loadWithBiometricSession(shareId, sessionId, testMasterDid).getOrThrow()

        // 验证
        assertArrayEquals("读取的份额应与写入的一致", plainShare, loaded)

        // 清理
        Arrays.fill(loaded, 0)
        storage.secureDelete(shareId).getOrThrow()
    }

    /**
     * TSS-STORE-002: 擦除后不可恢复
     *
     * 验证：安全擦除后，份额无法被读取
     */
    @Test
    fun `TSS-STORE-002 擦除后不可恢复`() = runBlocking {
        val shareId = "test-share-002"
        val plainShare = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val sessionId = ByteArray(16).also { SecureRandom().nextBytes(it) }

        // 保存
        storage.saveWithBiometricSession(shareId, plainShare.copyOf(), sessionId, testMasterDid).getOrThrow()

        // 擦除
        storage.secureDelete(shareId).getOrThrow()

        // 验证不存在
        assertFalse("擦除后份额不应存在", storage.exists(shareId))

        // 验证无法读取
        val result = storage.loadWithBiometricSession(shareId, sessionId, testMasterDid)
        assertTrue("擦除后读取应失败", result.isFailure)
    }

    /**
     * TSS-STORE-003: 错误会话 ID 解密失败
     *
     * 验证：使用不同的生物认证会话 ID 解密应失败
     * （内层密钥派生依赖 sessionId，不同的 sessionId 派生出不同的密钥）
     */
    @Test
    fun `TSS-STORE-003 错误会话 ID 解密失败`() = runBlocking {
        val shareId = "test-share-003"
        val plainShare = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val sessionId = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val wrongSessionId = ByteArray(16).also { SecureRandom().nextBytes(it) }

        // 确保两个 sessionId 不同
        while (wrongSessionId.contentEquals(sessionId)) {
            SecureRandom().nextBytes(wrongSessionId)
        }

        // 保存
        storage.saveWithBiometricSession(shareId, plainShare.copyOf(), sessionId, testMasterDid).getOrThrow()

        // 使用错误的 sessionId 尝试读取
        val result = storage.loadWithBiometricSession(shareId, wrongSessionId, testMasterDid)

        // 验证失败
        assertTrue("错误的 sessionId 应导致解密失败", result.isFailure)

        // 清理
        storage.secureDelete(shareId).getOrThrow()
    }

    /**
     * TSS-STORE-004: StrongBox 可用性检测
     *
     * 验证：StrongBox 检测方法不崩溃
     * 注意：不强制断言 true/false（取决于测试设备）
     */
    @Test
    fun `TSS-STORE-004 StrongBox 可用性检测`() {
        val available = storage.isStrongBoxAvailable()
        // 不强制断言 true/false（取决于设备），但方法不能崩溃
        assertNotNull("StrongBox 检测结果不应为 null", available)
    }

    /**
     * TSS-STORE-005: 安全警告在无 StrongBox 时非空
     *
     * 验证：如果设备不支持 StrongBox，应返回安全警告
     */
    @Test
    fun `TSS-STORE-005 安全警告在无 StrongBox 时非空`() {
        val warning = storage.getSecurityWarning()
        if (!storage.isStrongBoxAvailable()) {
            assertNotNull("无 StrongBox 时应返回安全警告", warning)
            assertTrue("安全警告应包含 StrongBox 相关信息", warning!!.contains("StrongBox"))
        }
    }

    /**
     * TSS-STORE-006: 旧接口 save/load 应抛出异常
     *
     * 验证：ShareStorage 接口的 save/load 方法应引导使用生物认证版本
     */
    @Test
    fun `TSS-STORE-006 旧接口应抛出异常`() = runBlocking {
        val result = storage.save("test-id", ByteArray(32))
        assertTrue("save 应抛出 UnsupportedOperationException", result.isFailure)
        assertTrue(
            "异常消息应引导使用新方法",
            result.exceptionOrNull() is UnsupportedOperationException
        )

        val loadResult = storage.load("test-id")
        assertTrue("load 应抛出 UnsupportedOperationException", loadResult.isFailure)
    }

    /**
     * TSS-STORE-007: 安全擦除验证
     *
     * 验证：ShareEncryptionLayer.secureWipe 正确覆写数据
     */
    @Test
    fun `TSS-STORE-007 安全擦除验证`() {
        val data = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val original = data.copyOf()

        // 擦除
        storage.encryptionLayer.secureWipe(data)

        // 验证数据已被覆写为全零
        assertArrayEquals("擦除后数据应全为零", ByteArray(32), data)

        // 验证与原始数据不同
        assertFalse("擦除后数据应与原始数据不同", data.contentEquals(original))
    }

    /**
     * TSS-STORE-008: 多个份额独立存储
     *
     * 验证：多个份额可以独立存储和读取
     */
    @Test
    fun `TSS-STORE-008 多个份额独立存储`() = runBlocking {
        val sessionId = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val shares = mapOf(
            "share-A" to ByteArray(32).also { SecureRandom().nextBytes(it) },
            "share-B" to ByteArray(32).also { SecureRandom().nextBytes(it) },
            "share-C" to ByteArray(32).also { SecureRandom().nextBytes(it) }
        )

        // 保存所有份额
        for ((id, share) in shares) {
            storage.saveWithBiometricSession(id, share.copyOf(), sessionId, testMasterDid).getOrThrow()
        }

        // 读取并验证
        for ((id, original) in shares) {
            val loaded = storage.loadWithBiometricSession(id, sessionId, testMasterDid).getOrThrow()
            assertArrayEquals("份额 $id 应一致", original, loaded)
            Arrays.fill(loaded, 0)
        }

        // 清理
        for (id in shares.keys) {
            storage.secureDelete(id).getOrThrow()
        }
    }
}
