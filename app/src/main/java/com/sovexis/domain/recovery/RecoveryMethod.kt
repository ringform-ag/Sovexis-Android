package com.sovexis.domain.recovery

import com.sovexis.domain.zkp.ZkpProof

/**
 * 账户恢复方法。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：Sovexis · 账户恢复机制完整实现指令
 */
enum class RecoveryMethod {
    /** 社交恢复：监护人批准 */
    SOCIAL,

    /** 助记词恢复：BIP-39 助记词 */
    MNEMONIC,

    /** 分布式加密网络恢复：TSS 分片 + 有偿存储节点 */
    NETWORK_SHARD
}

/**
 * 恢复配置（在主账号创建时设定，存储在本地加密存储中）。
 *
 * @param enabledMethods 启用的恢复方法列表
 * @param socialThreshold 社交恢复阈值（监护人批准数量）
 * @param socialGuardians 监护人列表
 * @param networkShardCount 网络恢复分片总数
 * @param networkShardThreshold 网络恢复所需分片数
 * @param networkNodeIds 存储分片的节点 DID 列表
 * @param timeLockHours 恢复时间锁（小时）
 */
data class RecoveryConfig(
    val enabledMethods: List<RecoveryMethod> = listOf(RecoveryMethod.MNEMONIC),
    val socialThreshold: Int = 3,
    val socialGuardians: List<GuardianInfo> = emptyList(),
    val networkShardCount: Int = 3,
    val networkShardThreshold: Int = 2,
    val networkNodeIds: List<String> = emptyList(),
    val timeLockHours: Int = 24
)

/**
 * 监护人信息。
 *
 * @param did 监护人的 DID
 * @param guardianType 监护人类型
 * @param alias 监护人别名
 */
data class GuardianInfo(
    val did: String,
    val guardianType: GuardianType,
    val alias: String? = null
)

/**
 * 监护人类型枚举。
 */
enum class GuardianType {
    /** 授权服务商 */
    AUTHORIZED_SERVICE,

    /** 其他真实用户 */
    REAL_USER,

    /** 硬件令牌 */
    HARDWARE_TOKEN
}

/**
 * 恢复上下文，包含恢复所需的所有输入。
 *
 * @param mnemonicWords 助记词列表（用于助记词恢复）
 * @param mnemonicPassphrase 助记词密码短语（可选）
 * @param guardianApprovals 监护人批准列表（用于社交恢复）
 * @param networkShards 网络分片列表（用于网络恢复）
 * @param biometricSessionId 生物认证会话 ID
 * @param kdfsPatternHash KDFS 图案哈希
 */
data class RecoveryContext(
    val mnemonicWords: List<String>? = null,
    val mnemonicPassphrase: String? = null,
    val guardianApprovals: List<GuardianApproval>? = null,
    val networkShards: List<NetworkShard>? = null,
    val biometricSessionId: ByteArray? = null,
    val kdfsPatternHash: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecoveryContext) return false
        return mnemonicWords == other.mnemonicWords &&
            mnemonicPassphrase == other.mnemonicPassphrase &&
            guardianApprovals == other.guardianApprovals &&
            networkShards == other.networkShards &&
            biometricSessionId.contentEquals(other.biometricSessionId) &&
            kdfsPatternHash.contentEquals(other.kdfsPatternHash)
    }

    override fun hashCode(): Int {
        var result = mnemonicWords?.hashCode() ?: 0
        result = 31 * result + (mnemonicPassphrase?.hashCode() ?: 0)
        result = 31 * result + (guardianApprovals?.hashCode() ?: 0)
        result = 31 * result + (networkShards?.hashCode() ?: 0)
        result = 31 * result + (biometricSessionId?.contentHashCode() ?: 0)
        result = 31 * result + (kdfsPatternHash?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * 监护人批准。
 *
 * @param guardianDid 监护人 DID
 * @param zkpProof ZKP 证明（由 ZkpService 生成）
 * @param timestamp 批准时间戳
 */
data class GuardianApproval(
    val guardianDid: String,
    val zkpProof: ZkpProof,
    val timestamp: Long
)

/**
 * 网络分片。
 *
 * @param nodeDid 节点 DID
 * @param shardData 加密分片数据
 * @param proof 节点提供的存储证明
 */
data class NetworkShard(
    val nodeDid: String,
    val shardData: ByteArray,
    val proof: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NetworkShard) return false
        return nodeDid == other.nodeDid &&
            shardData.contentEquals(other.shardData) &&
            proof.contentEquals(other.proof)
    }

    override fun hashCode(): Int {
        var result = nodeDid.hashCode()
        result = 31 * result + shardData.contentHashCode()
        result = 31 * result + proof.contentHashCode()
        return result
    }
}

/**
 * 恢复请求。
 *
 * @param recoveryId 恢复请求 ID
 * @param requesterDid 请求者 DID
 * @param guardianDids 监护人 DID 列表
 * @param threshold 恢复阈值
 * @param timestamp 请求时间戳
 */
data class RecoveryRequest(
    val recoveryId: String,
    val requesterDid: String,
    val guardianDids: List<String>,
    val threshold: Int,
    val timestamp: Long
)
