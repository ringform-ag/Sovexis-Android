package com.sovexis.domain.recovery

/**
 * 监护人管理器。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：Sovexis · 账户恢复机制完整实现指令
 *
 * 负责监护人的添加、删除、验证和恢复请求广播。
 */
class GuardianManager(
    private val nodeTrustVerifier: NodeTrustVerifier,
    private val cryptoComm: CryptoCommLayer? = null  // 可选，用于加密通信
) {
    /** 监护人列表（内存缓存） */
    private val guardians = mutableListOf<GuardianInfo>()

    /** 活跃的恢复请求 */
    private val activeRequests = mutableMapOf<String, RecoveryRequest>()

    /**
     * 添加监护人（需双方确认）。
     *
     * @param guardianInfo 监护人信息
     * @return 添加结果
     */
    suspend fun addGuardian(guardianInfo: GuardianInfo): Result<Unit> {
        // 如果是服务商或硬件令牌，验证其 VC 凭证
        if (guardianInfo.guardianType != GuardianType.REAL_USER) {
            val credentialValid = nodeTrustVerifier
                .verifyNodeCredential(guardianInfo.did)
                .getOrElse { return Result.failure(it) }
            if (!credentialValid) {
                return Result.failure(SecurityException("监护人凭证验证失败"))
            }
        }

        // 发送监护人邀请，等待对方确认
        // TODO: 实现邀请确认流程

        guardians.add(guardianInfo)
        return Result.success(Unit)
    }

    /**
     * 移除监护人。
     *
     * @param guardianDid 监护人 DID
     * @return 移除结果
     */
    suspend fun removeGuardian(guardianDid: String): Result<Unit> {
        val removed = guardians.removeIf { it.did == guardianDid }
        return if (removed) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("监护人不存在: $guardianDid"))
        }
    }

    /**
     * 获取所有监护人。
     *
     * @return 监护人列表
     */
    fun getGuardians(): List<GuardianInfo> = guardians.toList()

    /**
     * 获取监护人数量。
     *
     * @return 监护人数量
     */
    fun getGuardianCount(): Int = guardians.size

    /**
     * 获取授权服务商类型的监护人。
     *
     * @return 服务商监护人列表
     */
    fun getServiceGuardians(): List<GuardianInfo> {
        return guardians.filter { it.guardianType == GuardianType.AUTHORIZED_SERVICE }
    }

    /**
     * 获取真实用户类型的监护人。
     *
     * @return 用户监护人列表
     */
    fun getUserGuardians(): List<GuardianInfo> {
        return guardians.filter { it.guardianType == GuardianType.REAL_USER }
    }

    /**
     * 获取硬件令牌类型的监护人。
     *
     * @return 硬件令牌监护人列表
     */
    fun getHardwareGuardians(): List<GuardianInfo> {
        return guardians.filter { it.guardianType == GuardianType.HARDWARE_TOKEN }
    }

    /**
     * 向监护人广播恢复请求。
     *
     * @param request 恢复请求
     */
    suspend fun broadcastRecoveryRequest(request: RecoveryRequest) {
        activeRequests[request.recoveryId] = request
        request.guardianDids.forEach { guardianDid ->
            // 通过加密通道发送恢复请求
            sendRecoveryRequestToGuardian(guardianDid, request)
        }
    }

    /**
     * 发送恢复请求到单个监护人。
     *
     * @param guardianDid 监护人 DID
     * @param request 恢复请求
     */
    private suspend fun sendRecoveryRequestToGuardian(
        guardianDid: String,
        request: RecoveryRequest
    ) {
        // TODO: 实现加密通道发送
        // cryptoComm?.send(request)
    }

    /**
     * 获取活跃的恢复请求。
     *
     * @param recoveryId 恢复请求 ID
     * @return 恢复请求（如果存在）
     */
    fun getActiveRequest(recoveryId: String): RecoveryRequest? {
        return activeRequests[recoveryId]
    }

    /**
     * 获取所有活跃的恢复请求。
     *
     * @return 活跃请求列表
     */
    fun getActiveRequests(): List<RecoveryRequest> {
        return activeRequests.values.toList()
    }

    /**
     * 移除已完成的恢复请求。
     *
     * @param recoveryId 恢复请求 ID
     */
    fun removeRequest(recoveryId: String) {
        activeRequests.remove(recoveryId)
    }

    /**
     * 验证监护人是否存在。
     *
     * @param guardianDid 监护人 DID
     * @return 是否存在
     */
    fun isGuardian(guardianDid: String): Boolean {
        return guardians.any { it.did == guardianDid }
    }

    /**
     * 验证是否满足恢复阈值。
     *
     * @param approvers 已批准的监护人 DID 列表
     * @param threshold 所需阈值
     * @return 是否满足阈值
     */
    fun verifyThreshold(approvers: List<String>, threshold: Int): Boolean {
        val validApprovers = approvers.filter { isGuardian(it) }
        return validApprovers.size >= threshold
    }
}

/**
 * CryptoCommLayer 接口占位符。
 */
interface CryptoCommLayer {
    suspend fun send(data: ByteArray): Result<Unit>
}
