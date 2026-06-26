@file:Suppress("all")

package com.sovexis.domain.communication

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 局域网 TCP 传输适配器
 *
 * 通过 WebSocket 连接 Sovexis Node，用于 Android ↔ Node 联调。
 * 支持自动重连和心跳检测。
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-25
 * 实现状态: ✅ 可用于联调
 *
 * @param config 局域网连接配置
 */
@Singleton
class LanTcpTransportAdapter @Inject constructor(
    private val config: LanTcpConfig
) : TransportAdapter {

    companion object {
        private const val TAG = "LanTcpTransport"
        private const val PING_INTERVAL_MS = 30_000L
        private const val RECONNECT_BASE_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val MESSAGE_TIMEOUT_MS = 10_000L
    }

    private val gson = Gson()
    private val _isConnected = AtomicBoolean(false)
    private val messageCounter = AtomicLong(0)

    private val httpClient = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = PING_INTERVAL_MS
        }
    }

    private var wsSession: WebSocketSession? = null
    private val _incomingMessages = MutableSharedFlow<RawMessage>(extraBufferCapacity = 64)
    private val connectionMutex = Mutex()
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<RawMessage>>()

    override val isConnected: Boolean
        get() = _isConnected.get() && wsSession?.isActive == true

    override suspend fun connect(): Result<Unit> = connectionMutex.withLock {
        try {
            if (isConnected) return Result.success(Unit)

            val scheme = if (config.useTls) "wss" else "ws"
            wsSession = httpClient.webSocketSession(
                host = config.host,
                port = config.port,
                path = config.path
            ) {
                // 可选: 添加认证头
                // header(HttpHeaders.Authorization, "Bearer ${config.authToken}")
            }

            _isConnected.set(true)

            // 启动消息接收
            launchMessageReceiver()

            Result.success(Unit)
        } catch (e: Exception) {
            _isConnected.set(false)
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        connectionMutex.withLock {
            try {
                wsSession?.close()
                wsSession = null
            } catch (_: Exception) {}
            _isConnected.set(false)
        }
    }

    override suspend fun send(
        encryptedPayload: ByteArray,
        destinationDid: String
    ): Result<String> {
        if (!isConnected) {
            return Result.failure(IllegalStateException("Not connected to Node"))
        }

        return try {
            val messageId = generateMessageId()
            val envelope = LanMessageEnvelope(
                messageId = messageId,
                destinationDid = destinationDid,
                payload = Base64.encodeToString(encryptedPayload, Base64.NO_WRAP)
            )

            val json = gson.toJson(envelope)
            wsSession?.outgoing?.send(Frame.Text(json))

            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun receive(): Flow<RawMessage> = _incomingMessages.asSharedFlow()

    /**
     * 发送并等待响应（用于请求-响应模式）
     */
    suspend fun sendAndWaitResponse(
        encryptedPayload: ByteArray,
        destinationDid: String,
        timeoutMs: Long = MESSAGE_TIMEOUT_MS
    ): Result<RawMessage> {
        val sendResult = send(encryptedPayload, destinationDid)
        if (sendResult.isFailure) return Result.failure(sendResult.exceptionOrNull()!!)

        val messageId = sendResult.getOrThrow()
        val deferred = CompletableDeferred<RawMessage>()
        pendingResponses[messageId] = deferred

        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
                ?.let { Result.success(it) }
                ?: Result.failure(java.util.concurrent.TimeoutException("Response timeout for $messageId"))
        } finally {
            pendingResponses.remove(messageId)
        }
    }

    /**
     * 带自动重连的连接
     */
    suspend fun connectWithRetry(): Result<Unit> {
        var lastError: Throwable? = null

        for (attempt in 0 until MAX_RECONNECT_ATTEMPTS) {
            val result = connect()
            if (result.isSuccess) return result

            lastError = result.exceptionOrNull()
            val delay = RECONNECT_BASE_DELAY_MS * (1L shl attempt) // 指数退避
            kotlinx.coroutines.delay(delay)
        }

        return Result.failure(lastError ?: Exception("Max reconnect attempts reached"))
    }

    private fun launchMessageReceiver() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = wsSession ?: return@launch
                for (frame in session.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            try {
                                val json = frame.readText()
                                val envelope = gson.fromJson(json, LanMessageEnvelope::class.java)
                                val payload = Base64.decode(envelope.payload, Base64.NO_WRAP)

                                val message = RawMessage(
                                    messageId = envelope.messageId,
                                    payload = payload,
                                    senderAddress = "${config.host}:${config.port}",
                                    timestamp = envelope.timestamp
                                )

                                // 通知等待的请求
                                pendingResponses.remove(envelope.messageId)?.complete(message)

                                // 广播消息
                                _incomingMessages.emit(message)
                            } catch (e: Exception) {
                                // 解析失败，跳过
                            }
                        }
                        is Frame.Ping -> {
                            session.outgoing.send(Frame.Pong(frame.readBytes()))
                        }
                        is Frame.Close -> {
                            _isConnected.set(false)
                            break
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _isConnected.set(false)
            }
        }
    }

    private fun generateMessageId(): String {
        return "lan_${messageCounter.incrementAndGet()}_${System.currentTimeMillis()}"
    }
}

/**
 * 局域网 TCP 连接配置
 */
data class LanTcpConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8100,
    val useTls: Boolean = false,
    val path: String = "/ws",
    val authToken: String = ""
)

/**
 * WebSocket 消息信封（Gson 序列化版本）
 * 注意：与 ServiceRelayAdapter.MessageEnvelope 字段兼容但序列化方式不同
 */
private data class LanMessageEnvelope(
    @SerializedName("message_id") val messageId: String,
    @SerializedName("destination_did") val destinationDid: String,
    @SerializedName("payload") val payload: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)
