package com.sovexis.domain.communication

import com.sovexis.domain.communication.message.NodeMessage
import com.sovexis.domain.communication.message.NodeMessageType

/**
 * Node 消息路由接口。
 *
 * 遵循架构规范 四.2 + ADR-008：
 * 负责将收到的消息按 type 分发到对应的处理器。
 * App 端通过此接口与 Node 的 `NodeMessageRouter` 对应。
 *
 * @since 3.0.0
 */
interface NodeMessageRouter {

    /**
     * 发送消息并等待响应（request-response 模式）。
     *
     * @param action 操作名
     * @param payload 消息载荷
     * @return 响应消息
     */
    suspend fun sendRequest(action: String, payload: Map<String, Any> = emptyMap()): Result<NodeMessage>

    /**
     * 发送消息不等待响应（用于 push）。
     */
    suspend fun sendPush(action: String, payload: Map<String, Any> = emptyMap()): Result<Unit>

    /**
     * 注册消息处理器。收到指定 type 的消息时回调。
     * 用于接收 Node 主动推送的 PUSH / PEER / STEWARD 消息。
     */
    fun registerListener(type: NodeMessageType, listener: (NodeMessage) -> Unit)
}
