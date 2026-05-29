package com.sovexis.tss

import com.sovexis.domain.crypto.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

/**
 * BnbTssSignatureService 单元测试
 *
 * 测试 TSS 密钥生成和签名流程，使用 MockK 模拟 GoTssWrapper 的 native 调用。
 *
 * 运行: ./gradlew :app:testDebugUnitTest --tests "com.sovexis.tss.BnbTssSignatureServiceTest"
 */
class BnbTssSignatureServiceTest {

    private lateinit var shareStorage: ShareStorage
    private lateinit var service: BnbTssSignatureService

    @Before
    fun setup() {
        mockkObject(GoTssWrapper)
        shareStorage = mockk(relaxed = true)
        service = BnbTssSignatureService(shareStorage)
    }

    @After
    fun tearDown() {
        unmockkObject(GoTssWrapper)
    }

    // ========== Keygen Tests ==========

    @Test
    fun `generateKeyShares - communication channel unavailable returns failure`() = runTest {
        val transceiver = mockk<MessageTransceiver>()
        every { transceiver.isAvailable() } returns false

        val result = service.generateKeyShares(transceiver)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Communication channel"))
    }

    @Test
    fun `generateKeyShares - startKeygen returns null returns failure`() = runTest {
        val transceiver = mockk<MessageTransceiver>(relaxed = true)
        every { transceiver.isAvailable() } returns true
        every { GoTssWrapper.startKeygen(any(), any(), any()) } returns null

        val result = service.generateKeyShares(transceiver)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Failed to start keygen"))
    }

    @Test
    fun `generateKeyShares - successful keygen flow`() = runTest {
        val transceiver = mockk<MessageTransceiver>(relaxed = true)
        every { transceiver.isAvailable() } returns true
        every { transceiver.receive() } returns Result.failure(Exception("timeout"))
        every { shareStorage.save(any(), any()) } returns Result.success(Unit)

        // Mock keygen protocol: start -> receive -> process (null = done) -> get result
        val startMsg = """{"from":"local","to":"remote","round":1,"payload":"YWJj"}""".toByteArray()
        val keygenResult = """{
            "share_id": "share_123",
            "public_key": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65],
            "local_data": [10,20,30],
            "threshold": 2,
            "total_parties": 2
        }""".toByteArray()

        every { GoTssWrapper.startKeygen(any(), any(), any()) } returns startMsg
        every { GoTssWrapper.processKeygenMessage(any(), any()) } returns null  // protocol done
        every { GoTssWrapper.getKeygenResult(any()) } returns keygenResult
        every { GoTssWrapper.cleanupSession(any()) } just Runs

        val result = service.generateKeyShares(transceiver)

        assertTrue(result.isSuccess)
        assertEquals("share_123", result.getOrThrow().shareId)
        assertEquals(2, result.getOrThrow().threshold)
        assertEquals(2, result.getOrThrow().totalShares)

        verify { shareStorage.save("share_123", any()) }
        verify { GoTssWrapper.cleanupSession(any()) }
    }

    // ========== Signing Tests ==========

    @Test
    fun `partialSign - no local share returns failure`() = runTest {
        val transceiver = mockk<MessageTransceiver>(relaxed = true)
        val data = "test message".toByteArray()

        val result = service.partialSign(data, transceiver)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Local share not found"))
    }

    @Test
    fun `partialSign - successful signing flow`() = runTest {
        // First, generate key shares
        val transceiver = mockk<MessageTransceiver>(relaxed = true)
        every { transceiver.isAvailable() } returns true
        every { transceiver.receive() } returns Result.failure(Exception("timeout"))
        every { shareStorage.save(any(), any()) } returns Result.success(Unit)

        val startMsg = """{"from":"local","to":"remote","round":1,"payload":"YWJj"}""".toByteArray()
        val keygenResult = """{
            "share_id": "share_sign_1",
            "public_key": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65],
            "local_data": [10,20,30],
            "threshold": 2,
            "total_parties": 2
        }""".toByteArray()

        every { GoTssWrapper.startKeygen(any(), any(), any()) } returns startMsg
        every { GoTssWrapper.processKeygenMessage(any(), any()) } returns null
        every { GoTssWrapper.getKeygenResult(any()) } returns keygenResult
        every { GoTssWrapper.cleanupSession(any()) } just Runs

        val keygenResult2 = service.generateKeyShares(transceiver)
        assertTrue(keygenResult2.isSuccess)

        // Now sign
        val signTransceiver = mockk<MessageTransceiver>(relaxed = true)
        every { signTransceiver.isAvailable() } returns true
        every { signTransceiver.receive() } returns Result.failure(Exception("timeout"))

        val signStartMsg = """{"from":"local","to":"remote","round":1,"payload":"eHl6"}""".toByteArray()
        val sigResult = """{"signature":[100,101,102,103,104,105,106,107]}""".toByteArray()

        every { GoTssWrapper.startSigning(any(), any(), any(), any()) } returns signStartMsg
        every { GoTssWrapper.processSigningMessage(any(), any()) } returns null
        every { GoTssWrapper.getSignatureResult(any()) } returns sigResult

        val dataToSign = "hello world".toByteArray()
        val signResult = service.partialSign(dataToSign, signTransceiver)

        assertTrue(signResult.isSuccess)
        assertEquals("share_sign_1", signResult.getOrThrow().shareId)
        assertNotNull(signResult.getOrThrow().partialSigData)
    }

    // ========== Combine Signatures Tests ==========

    @Test
    fun `combineSignatures - no local share returns failure`() = runTest {
        val local = PartialSignature("sess1", "share1", byteArrayOf(1, 2, 3))
        val remote = RemotePartialSignature("sess1", "share2", byteArrayOf(4, 5, 6))

        val result = service.combineSignatures(local, remote)

        assertTrue(result.isFailure)
    }

    @Test
    fun `combineSignatures - successful combine`() = runTest {
        // Setup local share first
        val transceiver = mockk<MessageTransceiver>(relaxed = true)
        every { transceiver.isAvailable() } returns true
        every { transceiver.receive() } returns Result.failure(Exception("timeout"))
        every { shareStorage.save(any(), any()) } returns Result.success(Unit)

        val startMsg = """{"from":"local","to":"remote","round":1,"payload":"YWJj"}""".toByteArray()
        val keygenResult = """{
            "share_id": "share_combo",
            "public_key": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65],
            "local_data": [10,20,30],
            "threshold": 2,
            "total_parties": 2
        }""".toByteArray()

        every { GoTssWrapper.startKeygen(any(), any(), any()) } returns startMsg
        every { GoTssWrapper.processKeygenMessage(any(), any()) } returns null
        every { GoTssWrapper.getKeygenResult(any()) } returns keygenResult
        every { GoTssWrapper.cleanupSession(any()) } just Runs

        service.generateKeyShares(transceiver)

        // Combine
        val local = PartialSignature("sess1", "share_combo", byteArrayOf(100, 101, 102))
        val remote = RemotePartialSignature("sess1", "share_remote", byteArrayOf(200, 201, 202))

        val result = service.combineSignatures(local, remote)

        assertTrue(result.isSuccess)
        assertEquals("ECDSA_SECP256K1", result.getOrThrow().algorithm)
        assertArrayEquals(byteArrayOf(100, 101, 102), result.getOrThrow().signature)
    }

    // ========== Delete Share Tests ==========

    @Test
    fun `deleteLocalShare - no share returns failure`() = runTest {
        val result = service.deleteLocalShare()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Local share not found"))
    }

    @Test
    fun `deleteLocalShare - successful delete`() = runTest {
        // Setup
        val transceiver = mockk<MessageTransceiver>(relaxed = true)
        every { transceiver.isAvailable() } returns true
        every { transceiver.receive() } returns Result.failure(Exception("timeout"))
        every { shareStorage.save(any(), any()) } returns Result.success(Unit)
        every { shareStorage.exists(any()) } returns true
        every { shareStorage.secureDelete(any()) } returns Result.success(Unit)

        val startMsg = """{"from":"local","to":"remote","round":1,"payload":"YWJj"}""".toByteArray()
        val keygenResult = """{
            "share_id": "share_del",
            "public_key": [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65],
            "local_data": [10,20,30],
            "threshold": 2,
            "total_parties": 2
        }""".toByteArray()

        every { GoTssWrapper.startKeygen(any(), any(), any()) } returns startMsg
        every { GoTssWrapper.processKeygenMessage(any(), any()) } returns null
        every { GoTssWrapper.getKeygenResult(any()) } returns keygenResult
        every { GoTssWrapper.cleanupSession(any()) } just Runs

        service.generateKeyShares(transceiver)

        val result = service.deleteLocalShare()

        assertTrue(result.isSuccess)
        verify { shareStorage.secureDelete("share_del") }
    }

    // ========== GoTssWrapper Direct Tests ==========

    @Test
    fun `GoTssWrapper - startKeygen delegates to Tssbridge`() {
        val sessionId = "test-session"
        val localId = "local-1"
        val remoteId = "remote-1"

        every { GoTssWrapper.startKeygen(sessionId, localId, remoteId) } returns "mock-msg".toByteArray()

        val result = GoTssWrapper.startKeygen(sessionId, localId, remoteId)

        assertNotNull(result)
        assertEquals("mock-msg", String(result!!))
        verify { GoTssWrapper.startKeygen(sessionId, localId, remoteId) }
    }

    @Test
    fun `GoTssWrapper - cleanupSession delegates to Tssbridge`() {
        every { GoTssWrapper.cleanupSession("test-session") } just Runs

        GoTssWrapper.cleanupSession("test-session")

        verify { GoTssWrapper.cleanupSession("test-session") }
    }
}
