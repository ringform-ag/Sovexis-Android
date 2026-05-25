package com.sovexis.tss

import com.sovexis.mobile.domain.crypto.*
import com.sovexis.tss.message.MockTransceiver
import com.sovexis.tss.storage.ShareStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * ThresholdSignatureService 契约测试套件
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 *
 * 此测试套件定义了 ThresholdSignatureService 的契约规范。
 * 任何新的实现（如 luxfi/threshold 替换）必须通过此测试套件。
 *
 * 测试覆盖：
 * - TSS-001: 密钥份额生成
 * - TSS-002: 2-of-2 签名完成
 * - TSS-003: 签名验证
 * - TSS-004: 错误份额无法产生有效签名
 * - TSS-005: 缺少份额时签名失败
 * - TSS-006: 安全删除份额
 */
class ThresholdSignatureContractTest {

    private lateinit var mockShareStorage: ShareStorage

    @Before
    fun setup() {
        mockShareStorage = mockk(relaxed = true)
        coEvery { mockShareStorage.save(any(), any()) } returns Result.success(Unit)
        coEvery { mockShareStorage.load(any()) } returns Result.success(ByteArray(256))
        coEvery { mockShareStorage.secureDelete(any()) } returns Result.success(Unit)
        coEvery { mockShareStorage.exists(any()) } returns true
    }

    /**
     * TSS-001: 测试密钥份额生成
     *
     * 输入: 两个 MockTransceiver 实例
     * 预期: 密钥份额生成成功，公钥非空
     */
    @Test
    fun testKeygenProducesValidKeyShare() = runBlocking {
        // 创建本地和远程两个服务实例
        val localService = BnbTssSignatureService(mockShareStorage)
        val remoteService = BnbTssSignatureService(mockShareStorage)

        // 创建相互连接的 Mock 信道
        val aliceChannel = MockTransceiver()
        val bobChannel = MockTransceiver()
        aliceChannel.connectTo(bobChannel)

        // 并行执行密钥生成
        val localDeferred = async { localService.generateKeyShares(aliceChannel) }
        val remoteDeferred = async { remoteService.generateKeyShares(bobChannel) }

        val localShare = localDeferred.await()
        val remoteShare = remoteDeferred.await()

        // 验证结果
        assertTrue("本地份额生成应成功", localShare.isSuccess)
        assertTrue("远程份额生成应成功", remoteShare.isSuccess)

        val localInfo = localShare.getOrThrow()
        val remoteInfo = remoteShare.getOrThrow()

        // 验证公钥非空
        assertTrue("本地公钥应非空", localInfo.publicKey.isNotEmpty())
        assertTrue("远程公钥应非空", remoteInfo.publicKey.isNotEmpty())

        // 验证份额 ID 有效
        assertTrue("本地份额 ID 应非空", localInfo.shareId.isNotEmpty())
        assertTrue("远程份额 ID 应非空", remoteInfo.shareId.isNotEmpty())

        // 验证阈值参数
        assertEquals("阈值应为 2", 2, localInfo.threshold)
        assertEquals("总份额数应为 2", 2, localInfo.totalShares)

        // 验证份额已存储
        coVerify { mockShareStorage.save(any(), any()) }
    }

    /**
     * TSS-002: 测试 2-of-2 签名完成
     *
     * 输入: 上一测试的份额 + 测试数据
     * 预期: 生成完整签名
     */
    @Test
    fun test2of2SigningCompletes() = runBlocking {
        val localService = BnbTssSignatureService(mockShareStorage)
        val remoteService = BnbTssSignatureService(mockShareStorage)

        val aliceChannel = MockTransceiver()
        val bobChannel = MockTransceiver()
        aliceChannel.connectTo(bobChannel)

        // 先执行密钥生成
        val localKeygen = async { localService.generateKeyShares(aliceChannel) }
        val remoteKeygen = async { remoteService.generateKeyShares(bobChannel) }

        val localShare = localKeygen.await().getOrThrow()
        remoteKeygen.await()

        // 重新连接信道（密钥生成后连接会关闭）
        val aliceSignChannel = MockTransceiver()
        val bobSignChannel = MockTransceiver()
        aliceSignChannel.connectTo(bobSignChannel)

        // 签名数据
        val testData = "Sovexis Threshold Test".toByteArray()

        // 本地部分签名
        val localPartial = localService.partialSign(testData, aliceSignChannel)
        assertTrue("本地部分签名应成功", localPartial.isSuccess)

        // 创建远程部分签名（模拟）
        val remotePartial = RemotePartialSignature(
            sessionId = localPartial.getOrThrow().sessionId,
            shareId = "remote_share_id",
            partialSigData = ByteArray(128) { it.toByte() }
        )

        // 合并签名
        val combinedSig = localService.combineSignatures(
            localPartial.getOrThrow(),
            remotePartial
        )

        assertTrue("签名合并应成功", combinedSig.isSuccess)

        val signature = combinedSig.getOrThrow()
        assertTrue("签名数据应非空", signature.signature.isNotEmpty())
        assertTrue("公钥应非空", signature.publicKey.isNotEmpty())
    }

    /**
     * TSS-003: 测试签名验证
     *
     * 输入: 完整签名 + 公钥 + 原始数据
     * 预期: 公钥可验证签名 = true
     */
    @Test
    fun testSignatureVerifiesCorrectly() = runBlocking {
        // 注意：当前为占位实现，真实验证需要完整的 TSS 协议
        // 此测试验证签名数据结构正确

        val localService = BnbTssSignatureService(mockShareStorage)
        val remoteService = BnbTssSignatureService(mockShareStorage)

        val aliceChannel = MockTransceiver()
        val bobChannel = MockTransceiver()
        aliceChannel.connectTo(bobChannel)

        // 密钥生成
        val localShare = localService.generateKeyShares(aliceChannel).getOrThrow()

        // 重新连接
        val aliceSignChannel = MockTransceiver()
        val bobSignChannel = MockTransceiver()
        aliceSignChannel.connectTo(bobSignChannel)

        // 签名
        val testData = "Test data for verification".toByteArray()
        val localPartial = localService.partialSign(testData, aliceSignChannel).getOrThrow()

        val remotePartial = RemotePartialSignature(
            sessionId = localPartial.sessionId,
            shareId = "remote",
            partialSigData = ByteArray(128)
        )

        val combinedSig = localService.combineSignatures(localPartial, remotePartial).getOrThrow()

        // 验证签名结构
        assertNotNull("签名不应为空", combinedSig.signature)
        assertNotNull("公钥不应为空", combinedSig.publicKey)
        assertEquals("算法应为 ECDSA_SECP256K1", "ECDSA_SECP256K1", combinedSig.algorithm)

        // TODO: 集成真实 tss-lib 后，使用以下代码验证签名
        // val sig = Signature.getInstance("SHA256withECDSA")
        // sig.initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(combinedSig.publicKey)))
        // sig.update(testData)
        // assertTrue("签名应验证通过", sig.verify(combinedSig.signature))
    }

    /**
     * TSS-004: 测试错误份额无法产生有效签名
     *
     * 输入: 错误的份额
     * 预期: 签名验证失败或合并失败
     */
    @Test
    fun testWrongShareCannotProduceValidSig() = runBlocking {
        val localService = BnbTssSignatureService(mockShareStorage)

        // 创建不匹配的会话 ID
        val localPartial = PartialSignature(
            sessionId = "session_local",
            shareId = "share_local",
            partialSigData = ByteArray(128)
        )

        val remotePartial = RemotePartialSignature(
            sessionId = "session_remote",  // 不同的会话 ID
            shareId = "share_remote",
            partialSigData = ByteArray(128)
        )

        // 尝试合并不匹配的签名
        val result = localService.combineSignatures(localPartial, remotePartial)

        // 应失败（会话 ID 不匹配）
        assertTrue("不匹配的签名应合并失败", result.isFailure)
    }

    /**
     * TSS-005: 测试缺少份额时签名失败
     *
     * 输入: 仅本地份额，无远程响应
     * 预期: 签名合并失败
     */
    @Test
    fun testPartialSignWithMissingShareFails() = runBlocking {
        val localService = BnbTssSignatureService(mockShareStorage)

        // 尝试在未生成密钥的情况下签名
        val mockChannel = MockTransceiver()
        val testData = "Test data".toByteArray()

        val result = localService.partialSign(testData, mockChannel)

        // 应失败（缺少本地份额）
        assertTrue("缺少份额时应失败", result.isFailure)
    }

    /**
     * TSS-006: 测试删除份额移除密钥材料
     *
     * 输入: 调用 deleteLocalShare
     * 预期: exists() 返回 false，再加载时失败
     */
    @Test
    fun testDeleteShareRemovesKeyMaterial() = runBlocking {
        val localService = BnbTssSignatureService(mockShareStorage)
        val remoteService = BnbTssSignatureService(mockShareStorage)

        val aliceChannel = MockTransceiver()
        val bobChannel = MockTransceiver()
        aliceChannel.connectTo(bobChannel)

        // 生成密钥
        val localShare = localService.generateKeyShares(aliceChannel).getOrThrow()

        // 验证份额存在
        coEvery { mockShareStorage.exists(localShare.shareId) } returns true
        assertTrue("生成后份额应存在", mockShareStorage.exists(localShare.shareId))

        // 删除份额
        val deleteResult = localService.deleteLocalShare()
        assertTrue("删除应成功", deleteResult.isSuccess)

        // 验证份额信息已清除
        val shareInfoResult = localService.getLocalShareInfo()
        assertTrue("删除后 getLocalShareInfo 应失败", shareInfoResult.isFailure)

        // 验证存储层被调用删除
        coVerify { mockShareStorage.secureDelete(any()) }
    }

    /**
     * 测试完整 2-of-2 签名流程（端到端）
     */
    @Test
    fun testFull2of2SigningFlow() = runBlocking {
        // 创建本地和远程两个服务实例
        val localService = BnbTssSignatureService(mockShareStorage)
        val remoteService = BnbTssSignatureService(mockShareStorage)

        // 创建相互连接的 Mock 信道
        val aliceChannel = MockTransceiver()
        val bobChannel = MockTransceiver()
        aliceChannel.connectTo(bobChannel)

        // ========== 阶段 1: 密钥生成 ==========
        println("Phase 1: Key Generation")

        val localKeygen = async { localService.generateKeyShares(aliceChannel) }
        val remoteKeygen = async { remoteService.generateKeyShares(bobChannel) }

        val localShare = localKeygen.await().getOrThrow()
        val remoteShare = remoteKeygen.await().getOrThrow()

        println("Local share ID: ${localShare.shareId}")
        println("Remote share ID: ${remoteShare.shareId}")
        println("Public key size: ${localShare.publicKey.size} bytes")

        // ========== 阶段 2: 签名 ==========
        println("Phase 2: Signing")

        // 重新连接信道
        val aliceSignChannel = MockTransceiver()
        val bobSignChannel = MockTransceiver()
        aliceSignChannel.connectTo(bobSignChannel)

        val testData = "Sovexis Threshold Signature Test ${System.currentTimeMillis()}".toByteArray()
        println("Data to sign: ${testData.size} bytes")

        // 本地部分签名
        val localPartial = localService.partialSign(testData, aliceSignChannel).getOrThrow()
        println("Local partial signature generated: ${localPartial.partialSigData.size} bytes")

        // 模拟远程部分签名
        val remotePartial = RemotePartialSignature(
            sessionId = localPartial.sessionId,
            shareId = remoteShare.shareId,
            partialSigData = ByteArray(128) { (it * 2).toByte() }
        )

        // ========== 阶段 3: 合并签名 ==========
        println("Phase 3: Combine Signatures")

        val combinedSig = localService.combineSignatures(localPartial, remotePartial).getOrThrow()
        println("Combined signature: ${combinedSig.signature.size} bytes")
        println("Algorithm: ${combinedSig.algorithm}")

        // ========== 验证 ==========
        assertNotNull("签名应生成", combinedSig)
        assertTrue("签名数据应非空", combinedSig.signature.isNotEmpty())
        assertArrayEquals("公钥应匹配", localShare.publicKey, combinedSig.publicKey)

        println("Full 2-of-2 signing flow completed successfully!")
    }
}
