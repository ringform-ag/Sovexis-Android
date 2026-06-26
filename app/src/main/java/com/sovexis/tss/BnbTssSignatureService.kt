package com.sovexis.tss

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sovexis.domain.crypto.KeyShareInfo
import com.sovexis.domain.crypto.MessageTransceiver
import com.sovexis.domain.crypto.PartialSignature
import com.sovexis.domain.crypto.RemotePartialSignature
import com.sovexis.domain.crypto.ThresholdSignature
import com.sovexis.domain.crypto.ThresholdSignatureService
import com.sovexis.domain.crypto.TssMessage
import com.sovexis.tss.storage.ShareStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * bnb-chain/tss-lib ThresholdSignatureService implementation.
 *
 * Based on bnb-chain/tss-lib (Go, MIT) 2P-ECDSA threshold signing.
 * Supports GG20 key generation and signing protocols via MessageTransceiver.
 *
 * @param shareStorage secure storage for key shares
 * @author Sovexis Architecture Team
 * @since 3.0.0
 * @updated 2026-05-22 - Replaced mock implementation with real AAR calls
 */
@Singleton
class BnbTssSignatureService @Inject constructor(
    private val shareStorage: ShareStorage
) : ThresholdSignatureService {

    companion object {
        private const val TAG = "BnbTssSignatureService"
        private const val DEFAULT_THRESHOLD = 2
        private const val DEFAULT_TOTAL_PARTIES = 2
        private const val PROTOCOL_TIMEOUT_MS = 60000L  // 60 seconds timeout
    }

    private val gson = Gson()

    // Local share cache (only metadata in memory, sensitive data is encrypted)
    private var localShareInfo: KeyShareInfo? = null
    private var encryptedShare: ByteArray? = null

    // Active sessions
    private val activeSessions = ConcurrentHashMap<String, TssSession>()

    override suspend fun generateKeyShares(
        transceiver: MessageTransceiver
    ): Result<KeyShareInfo> = withContext(Dispatchers.Default) {
        try {
            // Check communication channel
            if (!transceiver.isAvailable()) {
                return@withContext Result.failure(IllegalStateException("Communication channel not available"))
            }

            val sessionId = "keygen_${System.currentTimeMillis()}"
            val localShareId = "local_${System.currentTimeMillis()}"
            val remoteShareId = "remote_${System.currentTimeMillis()}"

            // Create protocol session
            val session = TssSession(sessionId, transceiver)
            activeSessions[sessionId] = session

            // Start message receiver job
            val receiveJob = launchMessageReceiver(session)

            try {
                // Start keygen protocol - get first message
                val firstMsg = GoTssWrapper.startKeygen(sessionId, localShareId, remoteShareId)
                    ?: return@withContext Result.failure(IllegalStateException("Failed to start keygen"))

                // Send first message to peer
                val tssMessage = bytesToTssMessage(firstMsg, sessionId, localShareId, remoteShareId)
                transceiver.send(tssMessage)

                // Protocol loop: receive messages, process, send response
                var protocolDone = false
                while (!protocolDone && isActive) {
                    val receivedMsg = withTimeoutOrNull(PROTOCOL_TIMEOUT_MS) {
                        session.receiveChannel.receive()
                    }

                    if (receivedMsg == null) {
                        return@withContext Result.failure(IllegalStateException("Protocol timeout"))
                    }

                    // Process received message
                    val msgBytes = tssMessageToBytes(receivedMsg)
                    val nextMsg = GoTssWrapper.processKeygenMessage(sessionId, msgBytes)

                    if (nextMsg == null) {
                        // Protocol complete
                        protocolDone = true
                    } else {
                        // Send response to peer
                        val responseMsg = bytesToTssMessage(nextMsg, sessionId, localShareId, remoteShareId)
                        transceiver.send(responseMsg)
                    }
                }

                // Get keygen result
                val resultBytes = GoTssWrapper.getKeygenResult(sessionId)
                val keygenResult = parseKeygenResult(resultBytes)

                // Encrypt and save key share
                val saveResult = shareStorage.save(keygenResult.shareId, keygenResult.localData)
                if (saveResult.isFailure) {
                    return@withContext Result.failure(saveResult.exceptionOrNull()!!)
                }

                // Update local cache
                encryptedShare = keygenResult.localData
                localShareInfo = KeyShareInfo(
                    shareId = keygenResult.shareId,
                    publicKey = keygenResult.publicKey,
                    threshold = keygenResult.threshold,
                    totalShares = keygenResult.totalParties
                )

                Result.success(localShareInfo!!)
            } finally {
                receiveJob.cancel()
                activeSessions.remove(sessionId)
                GoTssWrapper.cleanupSession(sessionId)
                transceiver.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun partialSign(
        data: ByteArray,
        transceiver: MessageTransceiver
    ): Result<PartialSignature> = withContext(Dispatchers.Default) {
        try {
            // Check local share
            val shareInfo = localShareInfo
                ?: return@withContext Result.failure(IllegalStateException("Local share not found, run keygen first"))

            val encryptedShareData = encryptedShare
                ?: return@withContext Result.failure(IllegalStateException("Encrypted share data not found"))

            // Check communication channel
            if (!transceiver.isAvailable()) {
                return@withContext Result.failure(IllegalStateException("Communication channel not available"))
            }

            // Compute data hash
            val digest = MessageDigest.getInstance("SHA-256")
            val dataHash = digest.digest(data)

            val sessionId = "sign_${System.currentTimeMillis()}"

            // Create protocol session
            val session = TssSession(sessionId, transceiver)
            activeSessions[sessionId] = session

            // Start message receiver job
            val receiveJob = launchMessageReceiver(session)

            try {
                // Start signing protocol - get first message
                val firstMsg = GoTssWrapper.startSigning(
                    sessionId,
                    shareInfo.shareId,
                    dataHash,
                    encryptedShareData
                ) ?: return@withContext Result.failure(IllegalStateException("Failed to start signing"))

                // Send first message to peer
                val tssMessage = bytesToTssMessage(firstMsg, sessionId, shareInfo.shareId, "remote")
                transceiver.send(tssMessage)

                // Protocol loop
                var protocolDone = false
                while (!protocolDone && isActive) {
                    val receivedMsg = withTimeoutOrNull(PROTOCOL_TIMEOUT_MS) {
                        session.receiveChannel.receive()
                    }

                    if (receivedMsg == null) {
                        return@withContext Result.failure(IllegalStateException("Protocol timeout"))
                    }

                    // Process received message
                    val msgBytes = tssMessageToBytes(receivedMsg)
                    val nextMsg = GoTssWrapper.processSigningMessage(sessionId, msgBytes)

                    if (nextMsg == null) {
                        protocolDone = true
                    } else {
                        val responseMsg = bytesToTssMessage(nextMsg, sessionId, shareInfo.shareId, "remote")
                        transceiver.send(responseMsg)
                    }
                }

                // Get signature result
                val sigResultBytes = GoTssWrapper.getSignatureResult(sessionId)
                val sigResult = parseSigningResult(sigResultBytes)

                Result.success(
                    PartialSignature(
                        sessionId = sessionId,
                        shareId = shareInfo.shareId,
                        partialSigData = sigResult.signature
                    )
                )
            } finally {
                receiveJob.cancel()
                activeSessions.remove(sessionId)
                GoTssWrapper.cleanupSession(sessionId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun combineSignatures(
        localPartial: PartialSignature,
        remotePartial: RemotePartialSignature
    ): Result<ThresholdSignature> {
        // In the new AAR interface, signing already produces the complete signature
        // The partialSigData from partialSign() contains the complete DER-encoded signature
        val shareInfo = localShareInfo
            ?: return Result.failure(IllegalStateException("Local share info not found"))

        return Result.success(
            ThresholdSignature(
                signature = localPartial.partialSigData,
                publicKey = shareInfo.publicKey,
                algorithm = "ECDSA_SECP256K1"
            )
        )
    }

    override fun getLocalShareInfo(): Result<KeyShareInfo> {
        return localShareInfo?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Local share not found"))
    }

    override suspend fun deleteLocalShare(): Result<Unit> {
        return try {
            val shareId = localShareInfo?.shareId
                ?: return Result.failure(IllegalStateException("Local share not found"))

            // Securely delete stored share
            val deleteResult = shareStorage.secureDelete(shareId)
            if (deleteResult.isFailure) {
                return deleteResult
            }

            // Clear memory cache
            localShareInfo = null
            encryptedShare = null

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load locally stored share.
     *
     * Read and restore local share info from ShareStorage.
     */
    suspend fun loadLocalShare(shareId: String): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            // Check if share exists
            if (!shareStorage.exists(shareId)) {
                return@withContext Result.failure(IllegalStateException("Share not found: $shareId"))
            }

            // Read encrypted share
            val loadResult = shareStorage.load(shareId)
            if (loadResult.isFailure) {
                return@withContext Result.failure(loadResult.exceptionOrNull()!!)
            }

            encryptedShare = loadResult.getOrThrow()

            // Parse share metadata from the stored data
            // The localData is JSON-serialized KeygenResult
            val shareData = loadResult.getOrThrow()
            val keygenResult = parseKeygenResult(shareData)

            localShareInfo = KeyShareInfo(
                shareId = keygenResult.shareId,
                publicKey = keygenResult.publicKey,
                threshold = keygenResult.threshold,
                totalShares = keygenResult.totalParties
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Launch message receiver coroutine.
     *
     * Receives protocol messages from remote party in background and forwards to TSS protocol.
     */
    private fun launchMessageReceiver(session: TssSession): Job {
        return kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // Receive from remote
                    val receiveResult = withTimeoutOrNull(100) {
                        session.transceiver.receive()
                    }

                    if (receiveResult != null && receiveResult.isSuccess) {
                        val receivedMessage = receiveResult.getOrThrow()
                        session.receiveChannel.trySend(receivedMessage)
                    }

                    delay(10)  // Small delay to prevent busy loop
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    // ========== Helper methods ==========

    private data class KeygenResultData(
        val shareId: String,
        val publicKey: ByteArray,
        val localData: ByteArray,
        val threshold: Int,
        val totalParties: Int
    )

    private data class SigningResultData(
        val signature: ByteArray
    )

    /**
     * 从 Go JSON 解析字节数组字段。
     *
     * Go 的 json.Marshal 将 []byte 编码为 Base64 字符串。
     * Kotlin 的 Gson fromJson(ByteArray) 期望数字数组 [1,2,3]。
     * 此方法从 JSON 对象中提取字段，先尝试 Base64 解码，失败则回退到 Gson 数组解析。
     */
    private fun parseByteArrayField(obj: JsonObject, key: String): ByteArray {
        val el = obj.get(key) ?: return ByteArray(0)
        // Go json.Marshal encodes []byte as Base64 string
        return if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
            android.util.Base64.decode(el.asString, android.util.Base64.NO_WRAP)
        } else {
            gson.fromJson(el, ByteArray::class.java)
        }
    }

    private fun parseKeygenResult(data: ByteArray): KeygenResultData {
        val json = String(data, Charsets.UTF_8)
        val obj = JsonParser.parseString(json).asJsonObject

        return KeygenResultData(
            shareId = obj.get("share_id").asString,
            publicKey = parseByteArrayField(obj, "public_key"),
            localData = parseByteArrayField(obj, "local_data"),
            threshold = obj.get("threshold").asInt,
            totalParties = obj.get("total_parties").asInt
        )
    }

    private fun parseSigningResult(data: ByteArray): SigningResultData {
        val json = String(data, Charsets.UTF_8)
        val obj = JsonParser.parseString(json).asJsonObject

        return SigningResultData(
            signature = parseByteArrayField(obj, "signature")
        )
    }

    private fun bytesToTssMessage(data: ByteArray, sessionId: String, fromShareId: String, toShareId: String): TssMessage {
        val json = String(data, Charsets.UTF_8)
        val obj = JsonParser.parseString(json).asJsonObject

        return TssMessage(
            sessionId = obj.get("from")?.asString ?: sessionId,
            fromShareId = obj.get("from")?.asString ?: fromShareId,
            toShareId = obj.get("to")?.asString ?: toShareId,
            round = obj.get("round")?.asInt ?: 0,
            payload = parseByteArrayField(obj, "payload").let { if (it.isEmpty()) data else it },
            timestamp = System.currentTimeMillis()
        )
    }

    private fun tssMessageToBytes(message: TssMessage): ByteArray {
        // Extract payload - the actual wire bytes for the Go library
        return message.payload
    }

    /**
     * TSS protocol session.
     *
     * Manages context for a single keygen or signing protocol.
     */
    private data class TssSession(
        val sessionId: String,
        val transceiver: MessageTransceiver,
        val sendQueue: Channel<TssMessage> = Channel(Channel.UNLIMITED),
        val receiveChannel: Channel<TssMessage> = Channel(Channel.UNLIMITED)
    )
}
