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
     * 获取上一次创建身份时生成的助记词（如有）。
     * 安全约束：助记词仅存在于内存中，获取后即被清除不可再获取。
     *
     * @return 助记词列表或 null（未生成或已被获取）
     */
    fun takeLastGeneratedMnemonic(): List<String>?

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

    /**
     * 获取所有已知身份及其活跃状态。
     *
     * 替代旧架构 AccountDao.getAllAccounts()
     *
     * @return 所有身份列表（包含 isActive 状态）
     */
    suspend fun getAllIdentities(): Result<List<SovexisAccount>>

    /**
     * 切换活跃身份。
     *
     * 替代旧架构 AccountDao.setActive(did) + deactivateAll()
     *
     * @param did 要设为活跃的身份 DID
     * @return 操作结果
     */
    suspend fun setActiveIdentity(did: String): Result<Unit>

    /**
     * 设置副账号的熔断状态。
     */
    suspend fun setFrozen(did: String, frozen: Boolean): Result<Unit>

    /**
     * 删除副账号。
     */
    suspend fun deleteIdentity(did: String): Result<Unit>
}

/**
 * 主账号信息。
 *
 * @param did 去中心化身份标识
 * @param alias 用户别名
 * @param publicKeyPem 公钥 PEM 格式
 * @param createdAt 创建时间戳
 * @param isActive 是否为当前活跃身份
 */
data class MasterIdentity(
    override val did: String,
    override val alias: String?,
    val publicKeyPem: String,
    override val createdAt: Long,
    override val isActive: Boolean = false
) : SovexisAccount {
    override val accountType: AccountType get() = AccountType.MASTER
}

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
    override val did: String,
    val masterDid: String,
    val derivationPath: String,
    override val alias: String?,
    val uniqueCode: String,
    val publicKeyPem: String,
    val type: ChildType,
    override val createdAt: Long,
    override val isActive: Boolean = false,
    override val isFrozen: Boolean = false,

    /** 管家副账号的叮嘱字段（自然语言行为准则，预留） */
    val stewardNote: String = ""
) : SovexisAccount {
    override val accountType: AccountType
        get() = when (type) {
            ChildType.STANDARD -> AccountType.CHILD
            ChildType.STEWARD -> AccountType.STEWARD
            ChildType.SERVICE -> AccountType.SERVICE
        }
}

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
