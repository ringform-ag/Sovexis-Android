package com.sovexis.domain.node

/**
 * Node 业务管理接口。
 *
 * 遵循架构规范（ADR-002）：定义 Node 端能力在 App 端的调用抽象。
 * 由 [NodeRepository] 在 Data 层实现。
 *
 * @since 3.0.0
 */
interface NodeServiceManager {

    /** 获取 Node 运行状态 */
    suspend fun getNodeStatus(): Result<NodeStatus>

    /** 获取 Node 静态公钥（用于 App 端连接前预置） */
    suspend fun getNodePublicKey(): Result<String>

    /** 绑定归属账号 DID */
    suspend fun bindAccount(did: String): Result<Unit>

    /** 获取已绑定的归属账号 */
    suspend fun getBoundAccount(): Result<NodeAccount?>

    /** 启动/停止指定服务 */
    suspend fun setServiceState(service: NodeServiceType, enabled: Boolean): Result<Unit>

    /** 获取所有服务状态 */
    suspend fun getServiceStates(): Result<Map<NodeServiceType, Boolean>>
}

/**
 * Node 运行状态数据类。
 */
data class NodeStatus(
    val running: Boolean,
    val port: Int,
    val version: String,
    val uptime: String,
    val deviceCount: Int,
    val dataDir: String
)

/**
 * Node 归属账号信息。
 */
data class NodeAccount(
    val did: String,
    val alias: String?,
    val boundAt: Long,
    val isActive: Boolean
)

/**
 * Node 提供的服务类型。
 */
enum class NodeServiceType {
    STORAGE_BACKUP,
    TSS_CO_SIGN,
    AI_INFERENCE
}
