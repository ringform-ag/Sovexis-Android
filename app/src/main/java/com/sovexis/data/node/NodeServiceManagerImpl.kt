package com.sovexis.data.node

import com.sovexis.domain.communication.NodeMessageRouter
import com.sovexis.domain.communication.message.NodeMessageAction
import com.sovexis.domain.node.NodeAccount
import com.sovexis.domain.node.NodeServiceManager
import com.sovexis.domain.node.NodeServiceType
import com.sovexis.domain.node.NodeStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Node 业务管理实现。
 *
 * 遵循架构规范 §2.3 + ADR-002。
 * 通过 [NodeMessageRouter] 发送统一消息协议与 Node 通信。
 *
 * @since 3.0.0
 */
@Singleton
class NodeServiceManagerImpl @Inject constructor(
    private val messageRouter: NodeMessageRouter
) : NodeServiceManager {

    override suspend fun getNodeStatus(): Result<NodeStatus> {
        return messageRouter.sendRequest(NodeMessageAction.GET_STATUS).map { response ->
            val p = response.payload
            NodeStatus(
                running = p["running"] as? Boolean ?: false,
                port = (p["port"] as? Number)?.toInt() ?: 8100,
                version = p["version"] as? String ?: "",
                uptime = p["uptime"] as? String ?: "",
                deviceCount = (p["deviceCount"] as? Number)?.toInt() ?: 0,
                dataDir = p["dataDir"] as? String ?: ""
            )
        }
    }

    override suspend fun getNodePublicKey(): Result<String> {
        return messageRouter.sendRequest(NodeMessageAction.GET_PUBLIC_KEY).map { response ->
            response.payload["publicKey"] as? String ?: ""
        }
    }

    override suspend fun bindAccount(did: String): Result<Unit> {
        return messageRouter.sendRequest(NodeMessageAction.BIND_ACCOUNT, mapOf("did" to did)).map {}
    }

    override suspend fun getBoundAccount(): Result<NodeAccount?> {
        return messageRouter.sendRequest(NodeMessageAction.GET_BOUND_ACCOUNT).map { response ->
            val p = response.payload
            val did = p["did"] as? String
            if (did != null) {
                NodeAccount(
                    did = did,
                    alias = p["alias"] as? String,
                    boundAt = (p["boundAt"] as? Number)?.toLong() ?: 0L,
                    isActive = p["isActive"] as? Boolean ?: false
                )
            } else null
        }
    }

    override suspend fun setServiceState(service: NodeServiceType, enabled: Boolean): Result<Unit> {
        val action = if (enabled) NodeMessageAction.START_SERVICE else NodeMessageAction.STOP_SERVICE
        return messageRouter.sendRequest(action, mapOf("service" to service.name)).map {}
    }

    override suspend fun getServiceStates(): Result<Map<NodeServiceType, Boolean>> {
        return messageRouter.sendRequest(NodeMessageAction.GET_SERVICES).map { response ->
            val p = response.payload
            NodeServiceType.entries.associateWith { type ->
                p[type.name] as? Boolean ?: false
            }
        }
    }
}
