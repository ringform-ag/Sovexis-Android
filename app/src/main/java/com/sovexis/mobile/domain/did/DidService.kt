package com.sovexis.mobile.domain.did

import com.sovexis.mobile.core.result.Resource

/**
 * Sovexis DID 服务接口
 *
 * 实现 did:self + Sovexis DID 方法
 * 对 ECDSA P-256 公钥 PEM 的 UTF-8 字节计算 SHA-256，取后 32 字节十六进制
 * 拼接为 did:sovexis:0x{64位}
 *
 * 仅依赖本地公钥，无需任何网络查询或注册
 * 所有绑定信息（DID -> alias -> credentialId -> 加密种子）只存在于本地
 *
 * [TODO] 待实现：接入废弃核心代码后填充具体实现
 */
interface DidService {

    /**
     * 创建新的去中心化身份
     *
     * 流程：
     * 1. 生成 ECDSA P-256 密钥对（存储到 Keystore/StrongBox）
     * 2. 导出公钥 PEM
     * 3. 计算 SHA-256(PEM_UTF8_BYTES) 取后 32 字节
     * 4. 拼接为 did:sovexis:0x{64位十六进制}
     * 5. 绑定别名到本地数据库
     *
     * @param alias 用户自定义别名
     * @return Resource<DidDocument> 创建结果，包含 DID 文档
     */
    suspend fun createIdentity(alias: String): Resource<DidDocument>

    /**
     * 从已有密钥恢复 DID
     *
     * @param keyAlias Keystore 中的密钥别名
     * @param alias 用户自定义别名
     * @return Resource<DidDocument> 恢复结果
     */
    suspend fun restoreIdentity(keyAlias: String, alias: String): Resource<DidDocument>

    /**
     * 获取当前活跃账号的 DID 文档
     *
     * @return Resource<DidDocument> DID 文档
     */
    suspend fun getActiveDidDocument(): Resource<DidDocument>

    /**
     * 解析 DID 字符串
     *
     * @param did DID 字符串，格式: did:sovexis:0x{64位十六进制}
     * @return DidInfo? 解析结果，格式无效返回 null
     */
    fun parseDid(did: String): DidInfo?

    /**
     * 验证 DID 格式
     *
     * @param did DID 字符串
     * @return Boolean 格式是否有效
     */
    fun isValidDid(did: String): Boolean

    /**
     * 更新别名
     *
     * @param did 去中心化身份标识
     * @param newAlias 新别名
     * @return Resource<Unit> 更新结果
     */
    suspend fun updateAlias(did: String, newAlias: String): Resource<Unit>

    /**
     * 获取所有已注册的 DID 列表
     *
     * @return Resource<List<DidInfo>> DID 列表
     */
    suspend fun getAllIdentities(): Resource<List<DidInfo>>
}

/**
 * DID 文档
 */
data class DidDocument(
    val did: String,                    // did:sovexis:0x{64位}
    val alias: String,                  // 用户别名
    val publicKeyPem: String,           // ECDSA P-256 公钥 PEM
    val keyAlias: String,               // Keystore 密钥别名
    val verificationMethods: List<VerificationMethod>,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis()
)

/**
 * DID 信息摘要
 */
data class DidInfo(
    val did: String,
    val alias: String,
    val role: String,  // PRIMARY / SUB / STEWARD
    val isActive: Boolean,
    val created: Long
)

/**
 * 验证方法
 */
data class VerificationMethod(
    val id: String,
    val type: String,           // EcdsaSecp256r1VerificationKey2019
    val controller: String,
    val publicKeyPem: String
)
