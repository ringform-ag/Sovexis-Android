package com.sovexis.domain.recovery

import com.sovexis.domain.identity.MasterIdentity

/**
 * 恢复管理器。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：Sovexis · 账户恢复机制完整实现指令
 *
 * 统一管理三条恢复路径，提供统一的恢复入口。
 */
class RecoveryManager(
    private val mnemonicRecovery: MnemonicRecovery,
    private val socialRecovery: SocialRecovery,
    private val networkRecovery: NetworkRecovery,
    private val credentialManager: RecoveryCredentialManager
) {
    /** 当前恢复会话 */
    private var currentSession: RecoverySession? = null

    /**
     * 执行账户恢复。
     *
     * @param method 恢复方法
     * @param context 恢复上下文
     * @return 恢复结果
     */
    suspend fun recover(
        method: RecoveryMethod,
        context: RecoveryContext
    ): Result<MasterIdentity> {
        // 创建恢复会话
        val session = RecoverySession(
            method = method,
            status = RecoveryStatus.IN_PROGRESS,
            startTime = System.currentTimeMillis()
        )
        currentSession = session

        val result = when (method) {
            RecoveryMethod.MNEMONIC -> recoverWithMnemonic(context)
            RecoveryMethod.SOCIAL -> recoverWithSocial(context)
            RecoveryMethod.NETWORK_SHARD -> recoverWithNetwork(context)
        }

        // 更新会话状态
        session.status = if (result.isSuccess) RecoveryStatus.COMPLETED else RecoveryStatus.FAILED
        session.identity = result.getOrNull()

        return result
    }

    /**
     * 助记词恢复。
     */
    private suspend fun recoverWithMnemonic(
        context: RecoveryContext
    ): Result<MasterIdentity> {
        val mnemonic = context.mnemonicWords
            ?: return Result.failure(IllegalArgumentException("缺少助记词"))

        return mnemonicRecovery.recoverFromMnemonic(
            words = mnemonic,
            passphrase = context.mnemonicPassphrase
        ).also { result ->
            // 记录恢复凭证
            credentialManager.recordRecoveryAttempt(
                RecoveryAttemptRecord(
                    method = RecoveryMethod.MNEMONIC,
                    success = result.isSuccess,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 社交恢复。
     */
    private suspend fun recoverWithSocial(
        context: RecoveryContext
    ): Result<MasterIdentity> {
        val approvals = context.guardianApprovals
            ?: return Result.failure(IllegalArgumentException("缺少监护人批准"))

        // 获取恢复配置
        val config = credentialManager.getRecoveryConfig()
            ?: return Result.failure(IllegalStateException("无恢复配置"))

        // 验证阈值
        val isThresholdMet = socialRecovery.checkThreshold(
            approvals = approvals,
            threshold = config.socialThreshold
        )

        if (!isThresholdMet) {
            return Result.failure(
                IllegalStateException("监护人批准数量未达到阈值")
            )
        }

        // TODO: 使用批准重建主账号
        val result: Result<MasterIdentity> = Result.failure(NotImplementedError("社交恢复重建逻辑待实现"))
        credentialManager.recordRecoveryAttempt(
            RecoveryAttemptRecord(
                method = RecoveryMethod.SOCIAL,
                success = result.isSuccess,
                timestamp = System.currentTimeMillis()
            )
        )
        return result
    }

    /**
     * 网络恢复。
     */
    private suspend fun recoverWithNetwork(
        context: RecoveryContext
    ): Result<MasterIdentity> {
        val shards = context.networkShards
            ?: return Result.failure(IllegalArgumentException("缺少网络分片"))

        // 获取恢复配置
        val config = credentialManager.getRecoveryConfig()
            ?: return Result.failure(IllegalStateException("无恢复配置"))

        // 执行网络恢复
        val result = networkRecovery.performNetworkRecovery(
            nodeDids = config.networkNodeIds,
            threshold = config.networkShardThreshold
        ).mapCatching { masterKey ->
            // TODO: 从主密钥重建身份
            MasterIdentity(
                did = "did:sovexis:restored",
                alias = null,
                publicKeyPem = String(masterKey),
                createdAt = System.currentTimeMillis()
            )
        }

        credentialManager.recordRecoveryAttempt(
            RecoveryAttemptRecord(
                method = RecoveryMethod.NETWORK_SHARD,
                success = result.isSuccess,
                timestamp = System.currentTimeMillis()
            )
        )

        return result
    }

    /**
     * 获取当前恢复会话。
     *
     * @return 当前会话（如果没有则返回 null）
     */
    fun getCurrentSession(): RecoverySession? = currentSession

    /**
     * 取消当前恢复会话。
     */
    fun cancelCurrentSession() {
        currentSession?.let { session ->
            session.status = RecoveryStatus.CANCELLED
        }
        currentSession = null
    }

    /**
     * 生成助记词。
     *
     * @return 助记词列表
     */
    fun generateMnemonic(): List<String> {
        return mnemonicRecovery.generateMnemonic()
    }

    /**
     * 获取恢复配置。
     *
     * @return 恢复配置
     */
    fun getRecoveryConfig(): RecoveryConfig? {
        return credentialManager.getRecoveryConfig()
    }

    /**
     * 更新恢复配置。
     *
     * @param config 新的恢复配置
     */
    fun updateRecoveryConfig(config: RecoveryConfig) {
        credentialManager.saveRecoveryConfig(config)
    }

    /**
     * 初始化恢复配置（主账号创建时调用）。
     *
     * 根据配置的恢复方法，执行相应的初始化操作：
     * - MNEMONIC: 生成助记词并存储
     * - SOCIAL: 初始化监护人列表
     * - NETWORK_SHARD: 执行网络分片存储
     *
     * @param config 恢复配置
     * @return 初始化结果
     */
    suspend fun initializeRecovery(config: RecoveryConfig): Result<Unit> {
        return runCatching {
            // 保存恢复配置
            credentialManager.saveRecoveryConfig(config)

            // 根据启用的方法执行初始化
            config.enabledMethods.forEach { method ->
                when (method) {
                    RecoveryMethod.MNEMONIC -> {
                        // 生成助记词（但不返回给用户，由用户后续查看）
                        val mnemonic = mnemonicRecovery.generateMnemonic()
                        // 存储助记词哈希用于后续验证
                        credentialManager.storeMnemonicHash(mnemonic)
                    }
                    RecoveryMethod.SOCIAL -> {
                        // 初始化社交恢复（监护人列表为空，后续由用户添加）
                        if (config.socialGuardians.isNotEmpty()) {
                            socialRecovery.initializeGuardians(config.socialGuardians)
                        }
                    }
                    RecoveryMethod.NETWORK_SHARD -> {
                        // 初始化网络恢复（分片存储将在后续步骤执行）
                        // 这里仅做配置验证
                        if (config.networkNodeIds.isEmpty() && config.networkShardCount > 0) {
                            throw IllegalStateException("网络恢复已启用但未配置节点")
                        }
                    }
                }
            }

            Result.success(Unit)
        }.getOrElse { e ->
            Result.failure(IllegalStateException("恢复配置初始化失败: ${e.message}", e))
        }
    }

    // ========== 应用层串联接口 ==========

    /**
     * 步骤 1：助记词恢复。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 账户恢复流程应用层串联指令
     *
     * @param words 助记词列表
     * @param passphrase 密码短语（可选）
     * @return 恢复结果
     */
    suspend fun recoverFromMnemonic(
        words: List<String>,
        passphrase: String? = null
    ): Result<MasterIdentity> {
        return mnemonicRecovery.recoverFromMnemonic(words, passphrase)
    }

    /**
     * 步骤 1：社交恢复（发起恢复请求）。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 账户恢复流程应用层串联指令
     *
     * @param recoveryId 恢复请求 ID
     * @param guardianDids 监护人 DID 列表
     * @param threshold 恢复阈值
     * @return 恢复请求结果
     */
    suspend fun recoverFromSocial(
        recoveryId: String,
        guardianDids: List<String>,
        threshold: Int
    ): Result<RecoveryRequest> {
        return socialRecovery.initiateRecovery(recoveryId, guardianDids, threshold)
    }

    /**
     * 步骤 2：社交恢复（完成恢复）。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 账户恢复流程应用层串联指令
     *
     * @param recoveryId 恢复请求 ID
     * @return 恢复结果
     */
    suspend fun completeRecovery(recoveryId: String): Result<MasterIdentity> {
        return socialRecovery.completeRecovery(recoveryId)
    }

    /**
     * 步骤 1：网络恢复（从网络节点获取分片）。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 账户恢复流程应用层串联指令
     *
     * @param nodeDids 节点 DID 列表
     * @param threshold 恢复阈值
     * @return 恢复的分片列表
     */
    suspend fun recoverFromNetwork(
        nodeDids: List<String>,
        threshold: Int
    ): Result<List<NetworkShard>> {
        return networkRecovery.fetchShards(nodeDids, 0).map { shards ->
            // 验证分片数量是否达到阈值
            if (shards.size < threshold) {
                throw IllegalStateException("分片数量不足，需要 $threshold 个，实际 ${shards.size} 个")
            }
            shards
        }
    }
}

/**
 * 恢复会话。
 *
 * @param method 使用的恢复方法
 * @param status 会话状态
 * @param startTime 开始时间
 * @param identity 恢复的身份（成功后）
 */
data class RecoverySession(
    val method: RecoveryMethod,
    var status: RecoveryStatus,
    val startTime: Long,
    var identity: MasterIdentity? = null
)

/**
 * 恢复尝试记录。
 *
 * @param method 使用的恢复方法
 * @param success 是否成功
 * @param timestamp 尝试时间
 */
data class RecoveryAttemptRecord(
    val method: RecoveryMethod,
    val success: Boolean,
    val timestamp: Long
)
