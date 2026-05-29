package com.sovexis.domain.communication.message

/**
 * 统一消息协议定义。
 *
 * 遵循架构规范 四.1 + ADR-008：
 * 所有 App↔Node、Node↔Node 通信使用统一的 JSON 消息格式。
 *
 * @since 3.0.0
 */

/**
 * 消息类型枚举。
 */
enum class NodeMessageType {
    /** App → Node 请求，必须带 requestId */
    REQUEST,

    /** Node → App 响应，必须带相同 requestId */
    RESPONSE,

    /** Node → App 主动推送，不带 requestId */
    PUSH,

    /** Node ↔ Node 对等通信（预留） */
    PEER,

    /** 管家AI → Node/App 自主行为（预留） */
    STEWARD
}

/**
 * 预定义 action 常量。
 */
object NodeMessageAction {
    // 状态查询
    const val GET_STATUS = "getStatus"
    const val GET_PUBLIC_KEY = "getPublicKey"

    // 服务管理
    const val START_SERVICE = "startService"
    const val STOP_SERVICE = "stopService"
    const val GET_SERVICES = "getServices"

    // 归属账号
    const val BIND_ACCOUNT = "bindAccount"
    const val GET_BOUND_ACCOUNT = "getBoundAccount"

    // 存储
    const val STORE_SHARD = "storeShard"
    const val RETRIEVE_SHARD = "retrieveShard"
    const val DELETE_SHARD = "deleteShard"
    const val PROOF_STORAGE = "proofStorage"

    // TSS
    const val TSS_KEYGEN = "tssKeygen"
    const val TSS_SIGN = "tssSign"

    // 信任
    const val GET_TRUST_SCORE = "getTrustScore"

    // 管家（预留）
    const val STEWARD_ACTION = "stewardAction"
}

/**
 * 统一消息外壳。
 */
data class NodeMessage(
    val version: Int = 1,
    val type: NodeMessageType,
    val action: String,
    val requestId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: Map<String, Any> = emptyMap()
)
