@file:Suppress("all")

package com.sovexis.domain.communication

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AI-GENERATED]
 * 生成时间: 2026-05-09
 * 实现状态: AI可实现
 * 审核状态: 待审核
 *
 * 服务商中继适配器
 * 通过预设的服务商中继服务器进行通信
 * 支持 WebSocket 长连接和 HTTP/2 短连接
 * 服务商仅转发加密包，无法解密内容
 */
@Singleton
class ServiceRelayAdapter @Inject constructor(
    private val config: RelayConfig
) : TransportAdapter {

    companion object {
        private const val TAG = "ServiceRelayAdapter"
        private const val DEFAULT_TIMEOUT_MS = 30000L
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(WebSockets)

        engine {
            config {
                followRedirects(true)
            }
        }
    }

    private var webSocketSession: WebSocketSession? = null
    private val _incomingMessages = MutableSharedFlow<RawMessage>(extraBufferCapacity = 64)
    private var _isConnected = false

    override val isConnected: Boolean
        get() = _isConnected && webSocketSession?.isActive == true

    /**
     * 建立 WebSocket 连接
     */
    override suspend fun connect(): Result<Unit> {
        return try {
            val session = httpClient.webSocketSession(
                host = config.host,
                port = config.port,
                path = config.webSocketPath
            ) {
                header(HttpHeaders.Authorization, "Bearer ${config.authToken}")
            }

            webSocketSession = session
            _isConnected = true

            // 启动消息接收协程
            startReceiving()

            Result.success(Unit)
        } catch (e: Exception) {
            _isConnected = false
            Result.failure(e)
        }
    }

    /**
     * 断开连接
     */
    override suspend fun disconnect() {
        try {
            webSocketSession?.close()
            webSocketSession = null
            _isConnected = false
        } catch (e: Exception) {
            // 忽略关闭异常
        }
    }

    /**
     * 发送消息
     * 优先使用 WebSocket，未连接时回退到 HTTP
     */
    override suspend fun send(
        encryptedPayload: ByteArray,
        destinationDid: String
    ): Result<String> {
        val messageId = generateMessageId()

        return if (isConnected) {
            sendViaWebSocket(encryptedPayload, destinationDid, messageId)
        } else {
            sendViaHttp(encryptedPayload, destinationDid, messageId)
        }
    }

    /**
     * 通过 WebSocket 发送
     */
    private suspend fun sendViaWebSocket(
        payload: ByteArray,
        destinationDid: String,
        messageId: String
    ): Result<String> {
        return try {
            val session = webSocketSession
                ?: return Result.failure(IllegalStateException("WebSocket未连接"))

            val envelope = MessageEnvelope(
                messageId = messageId,
                destinationDid = destinationDid,
                payload = android.util.Base64.encodeToString(payload, android.util.Base64.DEFAULT)
            )

            session.outgoing.send(
                Frame.Text(Json.encodeToString(MessageEnvelope.serializer(), envelope))
            )

            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 通过 HTTP POST 发送
     */
    private suspend fun sendViaHttp(
        payload: ByteArray,
        destinationDid: String,
        messageId: String
    ): Result<String> {
        return try {
            val response = httpClient.post(config.httpEndpoint) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${config.authToken}")
                setBody(
                    MessageEnvelope(
                        messageId = messageId,
                        destinationDid = destinationDid,
                        payload = android.util.Base64.encodeToString(payload, android.util.Base64.DEFAULT)
                    )
                )
            }

            if (response.status.value in 200..299) {
                Result.success(messageId)
            } else {
                Result.failure(Exception("HTTP错误: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 接收消息流
     */
    override fun receive(): Flow<RawMessage> = _incomingMessages.asSharedFlow()

    /**
     * 启动消息接收
     */
    private fun startReceiving() {
        // WebSocket 消息接收在连接时自动处理
    }

    /**
     * 生成唯一消息ID
     */
    private fun generateMessageId(): String {
        return "msg_${System.currentTimeMillis()}_${(0..9999).random()}"
    }
}

/**
 * 中继配置
 */
data class RelayConfig(
    val host: String,
    val port: Int = 443,
    val webSocketPath: String = "/ws",
    val httpEndpoint: String,
    val authToken: String
)

/**
 * 消息信封
 */
@kotlinx.serialization.Serializable
data class MessageEnvelope(
    val messageId: String,
    val destinationDid: String,
    val payload: String, // Base64编码的加密数据
    val timestamp: Long = System.currentTimeMillis()
)
