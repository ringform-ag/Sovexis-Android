package com.sovexis.mobile.domain.did

import com.sovexis.mobile.core.result.Resource
import com.sovexis.mobile.data.local.TokenManager
import com.sovexis.mobile.data.local.dao.AccountDao
import com.sovexis.mobile.data.local.entity.AccountEntity
import com.sovexis.mobile.data.local.entity.AccountRole
import com.sovexis.mobile.domain.crypto.KeyManager
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis DID 服务实现
 *
 * 【引用来源】基于废案 IdentityManager.kt 逻辑
 * - DID 生成算法：废案第 290-295 行
 * - BIP-32 派生路径：废案第 300-308 行
 * - 唯一标识码生成：废案第 313-318 行
 *
 * 【调整说明】
 * 1. 适配 Repository 模式
 * 2. 统一使用 Resource 封装结果
 * 3. 添加完整注释和引用标记
 *
 * @author Sovexis 架构组
 * @since 3.0.0
 */
@Singleton
class DidServiceImpl @Inject constructor(
    private val keyManager: KeyManager,
    private val accountDao: AccountDao,
    private val tokenManager: TokenManager
) : DidService {

    companion object {
        private const val DID_METHOD = "did:sovexis:0x"
        private const val HASH_SUFFIX_LENGTH = 32  // SHA-256 后 32 字节 = 64 位十六进制
        private const val BIP32_SEED_LENGTH = 32
        private const val MASTER_KEY_ALIAS = "sovexis_master_key"
    }

    /**
     * 创建新的去中心化身份
     *
     * 【引用来源】废案 IdentityManager.kt 第 110-167 行（流程参考）
     * 【调整】移除 WebAuthn 依赖，使用纯本地密钥生成
     *
     * 流程：
     * 1. 生成 ECDSA P-256 密钥对（存储到 Keystore/StrongBox）
     * 2. 导出公钥 PEM
     * 3. 计算 SHA-256(PEM_UTF8_BYTES) 取后 32 字节十六进制
     * 4. 拼接 did:sovexis:0x{hex64}
     * 5. 绑定别名到本地数据库
     *
     * @param alias 用户自定义别名
     * @return Resource<DidDocument> 创建结果，包含 DID 文档
     */
    override suspend fun createIdentity(alias: String): Resource<DidDocument> {
        return try {
            // 检查是否已存在主账号
            val existingMaster = accountDao.getAccountsByRole(AccountRole.PRIMARY).first().firstOrNull()
            if (existingMaster != null) {
                return Resource.Error(message = "主账号已存在")
            }

            // 生成主密钥
            keyManager.generateKeyPair(MASTER_KEY_ALIAS)

            // 导出公钥 PEM
            val publicKeyPem = keyManager.exportPublicKeyPem(MASTER_KEY_ALIAS)

            // 生成 DID
            val did = computeDidIdentifier(publicKeyPem)

            // 创建 DID 文档
            val didDocument = DidDocument(
                did = did,
                alias = alias,
                publicKeyPem = publicKeyPem,
                keyAlias = MASTER_KEY_ALIAS,
                verificationMethods = listOf(
                    VerificationMethod(
                        id = "$did#keys-1",
                        type = "EcdsaSecp256r1VerificationKey2019",
                        controller = did,
                        publicKeyPem = publicKeyPem
                    )
                )
            )

            // 保存到数据库
            val accountEntity = AccountEntity(
                did = did,
                alias = alias,
                role = AccountRole.PRIMARY,
                publicKeyPem = publicKeyPem,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )
            accountDao.insertAccount(accountEntity)
            tokenManager.setActiveDid(did)

            Resource.Success(didDocument)
        } catch (e: Exception) {
            Resource.Error(message = "创建身份失败: ${e.message}", throwable = e)
        }
    }

    /**
     * 从已有密钥恢复 DID
     *
     * @param keyAlias Keystore 中的密钥别名
     * @param alias 用户自定义别名
     * @return Resource<DidDocument> 恢复结果
     */
    override suspend fun restoreIdentity(keyAlias: String, alias: String): Resource<DidDocument> {
        return try {
            if (!keyManager.keyExists(keyAlias)) {
                return Resource.Error(message = "密钥不存在: $keyAlias")
            }

            val publicKeyPem = keyManager.exportPublicKeyPem(keyAlias)
            val did = computeDidIdentifier(publicKeyPem)

            val didDocument = DidDocument(
                did = did,
                alias = alias,
                publicKeyPem = publicKeyPem,
                keyAlias = keyAlias,
                verificationMethods = listOf(
                    VerificationMethod(
                        id = "$did#keys-1",
                        type = "EcdsaSecp256r1VerificationKey2019",
                        controller = did,
                        publicKeyPem = publicKeyPem
                    )
                )
            )

            val accountEntity = AccountEntity(
                did = did,
                alias = alias,
                role = AccountRole.PRIMARY,
                publicKeyPem = publicKeyPem,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )
            accountDao.insertAccount(accountEntity)
            tokenManager.setActiveDid(did)

            Resource.Success(didDocument)
        } catch (e: Exception) {
            Resource.Error(message = "恢复身份失败: ${e.message}", throwable = e)
        }
    }

    /**
     * 派生副账号
     *
     * 【引用来源】废案 IdentityManager.kt 第 184-225 行
     *
     * @param type 副账号类型（STANDARD/STEWARD）
     * @param alias 别名
     * @return Resource<DidInfo> 派生结果
     */
    suspend fun deriveChildIdentity(type: AccountRole, alias: String): Resource<DidInfo> {
        return try {
            val masterAccount = accountDao.getAccountsByRole(AccountRole.PRIMARY).first().firstOrNull()
                ?: return Resource.Error(message = "主账号不存在")

            // 生成派生路径
            val derivationPath = generateDerivationPath(type)

            // 生成派生密钥别名
            val childKeyAlias = "${masterAccount.did}_$derivationPath"

            // 生成新的密钥对
            keyManager.generateKeyPair(childKeyAlias)

            // 导出公钥
            val publicKeyPem = keyManager.exportPublicKeyPem(childKeyAlias)

            // 生成 DID
            val did = computeDidIdentifier(publicKeyPem)

            // 生成唯一标识码
            val uniqueCode = generateUniqueCode(masterAccount.did, derivationPath)

            // 保存到数据库
            val childEntity = AccountEntity(
                did = did,
                alias = alias.ifEmpty { "${type.name}-$uniqueCode" },
                role = type,
                publicKeyPem = publicKeyPem,
                isActive = false,
                createdAt = System.currentTimeMillis()
            )
            accountDao.insertAccount(childEntity)

            Resource.Success(
                DidInfo(
                    did = did,
                    alias = childEntity.alias,
                    role = type.name,
                    isActive = false,
                    created = childEntity.createdAt
                )
            )
        } catch (e: Exception) {
            Resource.Error(message = "派生副账号失败: ${e.message}", throwable = e)
        }
    }

    /**
     * 获取当前活跃账号的 DID 文档
     *
     * @return Resource<DidDocument> DID 文档
     */
    override suspend fun getActiveDidDocument(): Resource<DidDocument> {
        return try {
            val activeDid = tokenManager.activeDid.first()
                ?: return Resource.Error(message = "没有活跃账号")

            val account = accountDao.getAccountByDid(activeDid)
                ?: return Resource.Error(message = "账号不存在: $activeDid")

            val didDocument = DidDocument(
                did = account.did,
                alias = account.alias,
                publicKeyPem = account.publicKeyPem,
                keyAlias = account.did, // 简化处理
                verificationMethods = listOf(
                    VerificationMethod(
                        id = "${account.did}#keys-1",
                        type = "EcdsaSecp256r1VerificationKey2019",
                        controller = account.did,
                        publicKeyPem = account.publicKeyPem
                    )
                ),
                created = account.createdAt,
                updated = account.lastUsedAt ?: account.createdAt
            )

            Resource.Success(didDocument)
        } catch (e: Exception) {
            Resource.Error(message = "获取 DID 文档失败: ${e.message}", throwable = e)
        }
    }

    /**
     * 解析 DID 字符串
     *
     * @param did DID 字符串，格式: did:sovexis:0x{64位十六进制}
     * @return DidInfo? 解析结果，格式无效返回 null
     */
    override fun parseDid(did: String): DidInfo? {
        if (!isValidDid(did)) return null
        return DidInfo(
            did = did,
            alias = "",
            role = "PRIMARY",
            isActive = false,
            created = 0
        )
    }

    /**
     * 验证 DID 格式
     *
     * @param did DID 字符串
     * @return Boolean 格式是否有效
     */
    override fun isValidDid(did: String): Boolean {
        return did.matches(Regex("^did:sovexis:0x[0-9a-fA-F]{64}$"))
    }

    /**
     * 更新别名
     *
     * @param did 去中心化身份标识
     * @param newAlias 新别名
     * @return Resource<Unit> 更新结果
     */
    override suspend fun updateAlias(did: String, newAlias: String): Resource<Unit> {
        return try {
            val account = accountDao.getAccountByDid(did)
                ?: return Resource.Error(message = "账号不存在: $did")

            val updated = account.copy(alias = newAlias)
            accountDao.updateAccount(updated)

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(message = "更新别名失败: ${e.message}", throwable = e)
        }
    }

    /**
     * 获取所有已注册的 DID 列表
     *
     * @return Resource<List<DidInfo>> DID 列表
     */
    override suspend fun getAllIdentities(): Resource<List<DidInfo>> {
        return try {
            val accounts = accountDao.getAllAccounts().first()
            val didInfoList = accounts.map { account ->
                DidInfo(
                    did = account.did,
                    alias = account.alias,
                    role = account.role.name,
                    isActive = account.isActive,
                    created = account.createdAt
                )
            }
            Resource.Success(didInfoList)
        } catch (e: Exception) {
            Resource.Error(message = "获取身份列表失败: ${e.message}", throwable = e)
        }
    }

    /**
     * 从公钥 PEM 计算 DID 标识符
     *
     * 【引用来源】废案 IdentityManager.kt 第 290-295 行
     * SHA-256(PEM_UTF8_BYTES) 取后 32 字节十六进制
     *
     * @param publicKeyPem 公钥 PEM 字符串
     * @return String DID 标识符（不含前缀）
     */
    private fun computeDidIdentifier(publicKeyPem: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyPem.toByteArray(Charsets.UTF_8))
        // 取后 32 字节
        val suffix = hash.copyOfRange(hash.size - HASH_SUFFIX_LENGTH, hash.size)
        val hex = suffix.joinToString("") { "%02x".format(it) }
        return "did:sovexis:0x$hex"
    }

    /**
     * 生成派生路径
     *
     * 【引用来源】废案 IdentityManager.kt 第 300-308 行
     *
     * @param type 账号类型
     * @return String BIP-32 派生路径
     */
    private fun generateDerivationPath(type: AccountRole): String {
        val typeIndex = when (type) {
            AccountRole.PRIMARY -> 0
            AccountRole.SUB -> 0  // STANDARD
            AccountRole.STEWARD -> 1
        }
        val timestamp = System.currentTimeMillis() % 10000
        return "m/44'/60'/$typeIndex'/0/$timestamp"
    }

    /**
     * 生成唯一标识码
     *
     * 【引用来源】废案 IdentityManager.kt 第 313-318 行
     *
     * @param masterDid 主账号 DID
     * @param derivationPath 派生路径
     * @return String 8 位十六进制唯一标识码
     */
    private fun generateUniqueCode(masterDid: String, derivationPath: String): String {
        val input = "$masterDid$derivationPath"
        val bytes = input.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }
}
