package com.sovexis.domain.recovery

import com.sovexis.domain.communication.covert.VirtualEventInjector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * 账户恢复机制单元测试。
 *
 * 测试项目：
 * - 恢复方法枚举
 * - 恢复配置验证
 * - 节点信任验证
 * - 监护人管理
 */
class RecoveryTest {

    // ── RecoveryMethod 测试 ──

    @Test
    fun `test recovery method enum values`() {
        assertEquals(3, RecoveryMethod.values().size)
        assertNotNull(RecoveryMethod.valueOf("SOCIAL"))
        assertNotNull(RecoveryMethod.valueOf("MNEMONIC"))
        assertNotNull(RecoveryMethod.valueOf("NETWORK_SHARD"))
    }

    // ── RecoveryConfig 测试 ──

    @Test
    fun `test default recovery config`() {
        val config = RecoveryConfig()

        assertEquals(1, config.enabledMethods.size)
        assertEquals(RecoveryMethod.MNEMONIC, config.enabledMethods.first())
        assertEquals(3, config.socialThreshold)
        assertEquals(3, config.networkShardCount)
        assertEquals(2, config.networkShardThreshold)
        assertEquals(24, config.timeLockHours)
    }

    @Test
    fun `test recovery config with all methods enabled`() {
        val config = RecoveryConfig(
            enabledMethods = listOf(
                RecoveryMethod.MNEMONIC,
                RecoveryMethod.SOCIAL,
                RecoveryMethod.NETWORK_SHARD
            ),
            socialThreshold = 5,
            socialGuardians = listOf(
                GuardianInfo("did:sovexis:guardian1", GuardianType.REAL_USER, "Alice"),
                GuardianInfo("did:sovexis:guardian2", GuardianType.REAL_USER, "Bob"),
                GuardianInfo("did:sovexis:guardian3", GuardianType.AUTHORIZED_SERVICE, "Service")
            ),
            networkShardCount = 5,
            networkShardThreshold = 3,
            networkNodeIds = listOf("node1", "node2", "node3")
        )

        assertEquals(3, config.enabledMethods.size)
        assertEquals(5, config.socialThreshold)
        assertEquals(3, config.socialGuardians.size)
        assertEquals(5, config.networkShardCount)
        assertEquals(3, config.networkShardThreshold)
        assertEquals(3, config.networkNodeIds.size)
    }

    // ── GuardianInfo 测试 ──

    @Test
    fun `test guardian info creation`() {
        val guardian = GuardianInfo(
            did = "did:sovexis:guardian1",
            guardianType = GuardianType.REAL_USER,
            alias = "Alice"
        )

        assertEquals("did:sovexis:guardian1", guardian.did)
        assertEquals(GuardianType.REAL_USER, guardian.guardianType)
        assertEquals("Alice", guardian.alias)
    }

    @Test
    fun `test guardian type enum values`() {
        assertEquals(3, GuardianType.values().size)
        assertNotNull(GuardianType.valueOf("AUTHORIZED_SERVICE"))
        assertNotNull(GuardianType.valueOf("REAL_USER"))
        assertNotNull(GuardianType.valueOf("HARDWARE_TOKEN"))
    }

    // ── NodeTrustVerifier 测试 ──

    @Test
    fun `test node trust verifier default implementation`() {
        val verifier = NodeTrustVerifierImpl()

        // 添加节点到黑名单
        verifier.addToBlacklist("did:sovexis:malicious")
        assertTrue(verifier.isBlacklisted("did:sovexis:malicious"))

        // 从黑名单移除
        verifier.removeFromBlacklist("did:sovexis:malicious")
        assertFalse(verifier.isBlacklisted("did:sovexis:malicious"))

        // 清空缓存
        verifier.clearCache()
    }

    @Test
    fun `test node trust result creation`() {
        val trustedResult = NodeTrustResult(
            isTrusted = true,
            score = 80
        )
        assertTrue(trustedResult.isTrusted)
        assertEquals(80, trustedResult.score)
        assertNull(trustedResult.reason)

        val untrustedResult = NodeTrustResult(
            isTrusted = false,
            score = 30,
            reason = "评分低于最低阈值 50"
        )
        assertFalse(untrustedResult.isTrusted)
        assertEquals(30, untrustedResult.score)
        assertEquals("评分低于最低阈值 50", untrustedResult.reason)
    }

    // ── RecoveryContext 测试 ──

    @Test
    fun `test recovery context creation`() {
        val context = RecoveryContext(
            mnemonicWords = listOf("abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident"),
            mnemonicPassphrase = "mySecretPassphrase"
        )

        assertEquals(12, context.mnemonicWords?.size)
        assertEquals("mySecretPassphrase", context.mnemonicPassphrase)
        assertNull(context.guardianApprovals)
        assertNull(context.networkShards)
    }

    @Test
    fun `test recovery context equality`() {
        val context1 = RecoveryContext(
            mnemonicWords = listOf("word1", "word2")
        )
        val context2 = RecoveryContext(
            mnemonicWords = listOf("word1", "word2")
        )
        val context3 = RecoveryContext(
            mnemonicWords = listOf("word1", "word3")
        )

        assertEquals(context1, context2)
        assertNotEquals(context1, context3)
    }

    // ── GuardianManager 测试 ──

    @Test
    fun `test guardian manager operations`() = runBlocking {
        val nodeVerifier = NodeTrustVerifierImpl()
        val guardianManager = GuardianManager(nodeVerifier)

        // 添加监护人
        val guardian = GuardianInfo(
            did = "did:sovexis:guardian1",
            guardianType = GuardianType.REAL_USER,
            alias = "Alice"
        )
        val addResult = guardianManager.addGuardian(guardian)
        assertTrue(addResult.isSuccess)

        // 验证监护人存在
        assertTrue(guardianManager.isGuardian("did:sovexis:guardian1"))
        assertEquals(1, guardianManager.getGuardianCount())

        // 按类型获取监护人
        assertEquals(1, guardianManager.getUserGuardians().size)
        assertEquals(0, guardianManager.getServiceGuardians().size)

        // 移除监护人
        val removeResult = guardianManager.removeGuardian("did:sovexis:guardian1")
        assertTrue(removeResult.isSuccess)
        assertEquals(0, guardianManager.getGuardianCount())
    }

    // ── RecoveryStatus 测试 ──

    @Test
    fun `test recovery status enum values`() {
        assertEquals(5, RecoveryStatus.values().size)
        assertNotNull(RecoveryStatus.valueOf("PENDING"))
        assertNotNull(RecoveryStatus.valueOf("IN_PROGRESS"))
        assertNotNull(RecoveryStatus.valueOf("COMPLETED"))
        assertNotNull(RecoveryStatus.valueOf("FAILED"))
        assertNotNull(RecoveryStatus.valueOf("CANCELLED"))
    }

    // ── VirtualEventInjector 测试（复用） ──

    @Test
    fun `test virtual event injector ratios`() {
        assertEquals(0.1, VirtualEventInjector.getDefaultRatioForUserLevel(0), 0.001)
        assertEquals(0.2, VirtualEventInjector.getDefaultRatioForUserLevel(1), 0.001)
        assertEquals(0.3, VirtualEventInjector.getDefaultRatioForUserLevel(2), 0.001)
    }

    private fun fail(message: String) {
        throw AssertionError(message)
    }
}
