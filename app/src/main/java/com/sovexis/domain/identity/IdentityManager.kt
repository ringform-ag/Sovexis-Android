package com.sovexis.domain.identity

import com.sovexis.domain.recovery.RecoveryConfig

/**
 * 身份管理器接口。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：IdentityManager 架构决策确认
 *
 * 作为协调层，负责身份创建的完整流程：
 * 1. 调用 DidService 生成身份
 * 2. 存储 KDFS 承诺
 * 3. 初始化恢复配置
 * 4. 生成 ZKP 预期承诺根
 */
interface IdentityManager {
    /**
     * 创建主账号的完整流程。
     *
     * @param alias 用户别名
     * @param kdfsHash KDFS 图案哈希
     * @param recoveryConfig 恢复配置
     * @return 创建结果
     */
    suspend fun createMasterIdentity(
        alias: String,
        kdfsHash: ByteArray,
        recoveryConfig: RecoveryConfig
    ): Result<MasterIdentity>

    /**
     * 获取当前主账号。
     *
     * @return 主账号信息，如果没有则返回 null
     */
    suspend fun getMasterIdentity(): MasterIdentity?

    /**
     * 派生副账号。
     *
     * @param type 副账号类型
     * @param alias 别名（可选）
     * @return 派生结果
     */
    suspend fun deriveChildIdentity(type: ChildType, alias: String?): Result<ChildIdentity>

    /**
     * 获取指定 DID 的副账号信息。
     *
     * @param did 副账号 DID
     * @return 副账号信息，如果没有则返回 null
     */
    suspend fun getChildIdentity(did: String): ChildIdentity?

    /**
     * 获取当前活跃的 DID。
     *
     * @return 当前活跃 DID，如果没有则返回 null
     */
    suspend fun getActiveDid(): String?

    /**
     * 获取指定 DID 的私钥。
     *
     * @param did 身份 DID
     * @return 私钥字节数组，如果没有则返回 null
     */
    suspend fun getPrivateKey(did: String): ByteArray?

    /**
     * 获取指定 DID 的公钥。
     *
     * @param did 身份 DID
     * @return 公钥字节数组，如果没有则返回 null
     */
    suspend fun getPublicKey(did: String): ByteArray?

    /**
     * 从种子恢复主账号。
     *
     * @param seed BIP-39 种子
     * @return 恢复结果
     */
    suspend fun restoreMasterIdentity(seed: ByteArray): Result<MasterIdentity>

    /**
     * 获取设备绑定数据。
     *
     * @return 设备绑定字节数组
     */
    fun getDeviceBindingData(): ByteArray

    /**
     * 获取预期承诺根。
     *
     * @param did 身份 DID
     * @return 预期承诺根字节数组，如果没有则返回 null
     */
    fun getExpectedCommitmentRoot(did: String): ByteArray?
}

/**
 * 主账号信息。
 *
 * @param did 去中心化身份标识
 * @param alias 用户别名
 * @param publicKeyPem 公钥 PEM 格式
 * @param createdAt 创建时间戳
 */
data class MasterIdentity(
    val did: String,
    val alias: String?,
    val publicKeyPem: String,
    val createdAt: Long
)

/**
 * 副账号信息。
 *
 * @param did 去中心化身份标识
 * @param masterDid 主账号 DID
 * @param derivationPath 派生路径
 * @param alias 用户别名
 * @param uniqueCode 唯一标识码
 * @param publicKeyPem 公钥 PEM 格式
 * @param type 副账号类型
 * @param createdAt 创建时间戳
 */
data class ChildIdentity(
    val did: String,
    val masterDid: String,
    val derivationPath: String,
    val alias: String?,
    val uniqueCode: String,
    val publicKeyPem: String,
    val type: ChildType,
    val createdAt: Long
)

/**
 * 副账号类型枚举。
 */
enum class ChildType {
    /** 标准副账号 */
    STANDARD,

    /** 管理员账号 */
    STEWARD,

    /** 服务账号 */
    SERVICE
}
