package com.sovexis.data.communication

import com.sovexis.domain.communication.CryptoCommLayer
import com.sovexis.domain.communication.NodeMessageRouter
import com.sovexis.domain.communication.message.NodeMessage
import com.sovexis.domain.communication.message.NodeMessageType
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Node 消息路由实现。
 *
 * 遵循架构规范 §4.2 + ADR-008。
 * 将 Domain 层的 [NodeMessageRouter] 接口委托给 [CryptoCommLayer] 进行实际消息传输。
 *
 * @since 3.0.0
 */
@Singleton
class NodeMessageRouterImpl @Inject constructor(
    private val cryptoCommLayer: CryptoCommLayer
) : NodeMessageRouter {

    private val listeners = mutableMapOf<NodeMessageType, MutableList<(NodeMessage) -> Unit>>()

    override suspend fun sendRequest(action: String, payload: Map<String, Any>): Result<NodeMessage> {
        return runCatching {
            val requestId = java.util.UUID.randomUUID().toString()
            val msg = NodeMessage(
                type = NodeMessageType.REQUEST,
                action = action,
                requestId = requestId,
                payload = payload
            )
            val json = toJson(msg)
            val responseBytes = cryptoCommLayer.send(json.toByteArray(Charsets.UTF_8), "node").getOrThrow()
            // TODO: 解析 Node 返回的 JSON 为 NodeMessage
            // 当前阶段占位：返回成功确认
            NodeMessage(
                type = NodeMessageType.RESPONSE,
                action = action,
                requestId = requestId
            )
        }
    }

    override suspend fun sendPush(action: String, payload: Map<String, Any>): Result<Unit> {
        return runCatching {
            val msg = NodeMessage(
                type = NodeMessageType.PUSH,
                action = action,
                payload = payload
            )
            val json = toJson(msg)
            cryptoCommLayer.send(json.toByteArray(Charsets.UTF_8), "node").getOrThrow()
        }
    }

    override fun registerListener(type: NodeMessageType, listener: (NodeMessage) -> Unit) {
        synchronized(listeners) {
            listeners.getOrPut(type) { mutableListOf() }.add(listener)
        }
    }

    /** 序列化 [NodeMessage] 为 JSON 字符串 */
    private fun toJson(msg: NodeMessage): String {
        val json = JSONObject()
        json.put("version", msg.version)
        json.put("type", msg.type.name.lowercase())
        json.put("action", msg.action)
        msg.requestId?.let { json.put("requestId", it) }
        json.put("timestamp", msg.timestamp)
        if (msg.payload.isNotEmpty()) {
            json.put("payload", JSONObject(msg.payload))
        }
        return json.toString()
    }
}
