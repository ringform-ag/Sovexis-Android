package com.sovexis.domain.did

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sovexis.core.result.Resource
import com.sovexis.domain.crypto.KeyManager
import com.sovexis.domain.identity.ChildType
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis DID 服务实现
 *
 * 【重构说明】2026-05-26
 * - 移除 AccountDao/AccountEntity/AccountRole 依赖
 * - 移除 TokenManager 依赖（活跃 DID 管理由 IdentityManager 负责）
 * - 使用 ChildType 替代 AccountRole
 *
 * @author Sovexis 架构组
 * @since 3.0.0
 */
@Singleton
class DidServiceImpl @Inject constructor(
    private val keyManager: KeyManager,
    @ApplicationContext private val context: Context
) : DidService {

    companion object {
        private const val DID_METHOD = "did:sovexis:0x"
        private const val HASH_SUFFIX_LENGTH = 32
        private const val MASTER_KEY_ALIAS = "sovexis_master_key"
        private const val PREFS_FILE = "did_persistence"
        private const val KEY_ALIAS_PREF = "master_alias"
        private const val KEY_CHILD_IDS = "child_ids"       // JSON array of child DID strings
        private const val KEY_CHILD_PREFIX = "child_info_"  // prefix for per-child metadata
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun createIdentity(alias: String): Resource<DidDocument> {
        return try {
            keyManager.generateKeyPair(MASTER_KEY_ALIAS)
            val publicKeyPem = keyManager.exportPublicKeyPem(MASTER_KEY_ALIAS)
            val did = computeDidIdentifier(publicKeyPem)

            // 持久化别名加密存储 —— 解决应用重启后身份丢失的问题
            try {
                encryptedPrefs.edit().putString(KEY_ALIAS_PREF, alias).apply()
            } catch (_: Exception) {
                // 加密写入失败不影响身份创建主流程
            }

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

            Resource.Success(didDocument)
        } catch (e: Exception) {
            Resource.Error(message = "创建身份失败: ${e.message}", throwable = e)
        }
    }

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

            Resource.Success(didDocument)
        } catch (e: Exception) {
            Resource.Error(message = "恢复身份失败: ${e.message}", throwable = e)
        }
    }

    override suspend fun deriveChildIdentity(type: ChildType, alias: String): Resource<DidInfo> {
        return try {
            val childKeyAlias = "child_${type.name}_${System.currentTimeMillis()}"

            keyManager.generateKeyPair(childKeyAlias)
            val publicKeyPem = keyManager.exportPublicKeyPem(childKeyAlias)
            val did = computeDidIdentifier(publicKeyPem)
            val uniqueCode = generateUniqueCode(did, type.name)
            val displayAlias = alias.ifEmpty { "${type.name}-$uniqueCode" }

            val info = DidInfo(
                did = did,
                alias = displayAlias,
                role = type.name,
                isActive = false,
                created = System.currentTimeMillis()
            )

            // 持久化：追加 child ID 到列表
            val existingJson = encryptedPrefs.getString(KEY_CHILD_IDS, "[]") ?: "[]"
            val arr = JSONArray(existingJson)
            arr.put(did)
            encryptedPrefs.edit().putString(KEY_CHILD_IDS, arr.toString()).apply()

            // 持久化：子身份元数据
            val metaJson = org.json.JSONObject().apply {
                put("alias", displayAlias)
                put("role", type.name)
                put("created", info.created)
            }
            encryptedPrefs.edit().putString(KEY_CHILD_PREFIX + did, metaJson.toString()).apply()

            Resource.Success(info)
        } catch (e: Exception) {
            Resource.Error(message = "派生副账号失败: ${e.message}", throwable = e)
        }
    }

    override suspend fun getActiveDidDocument(): Resource<DidDocument> {
        return try {
            val publicKeyPem = keyManager.exportPublicKeyPem(MASTER_KEY_ALIAS)
            val did = computeDidIdentifier(publicKeyPem)
            // 安全读取别名——卸载重装后 Keystore 密钥变化，解密失败返回空
            val alias = try {
                encryptedPrefs.getString(KEY_ALIAS_PREF, "") ?: ""
            } catch (_: Exception) {
                ""
            }

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

            Resource.Success(didDocument)
        } catch (e: Exception) {
            Resource.Error(message = "获取 DID 文档失败: ${e.message}", throwable = e)
        }
    }

    override fun parseDid(did: String): DidInfo? {
        if (!isValidDid(did)) return null
        return DidInfo(did = did, alias = "", role = "PRIMARY", isActive = false, created = 0)
    }

    override fun isValidDid(did: String): Boolean {
        return did.matches(Regex("^did:sovexis:0x[0-9a-fA-F]{64}$"))
    }

    override suspend fun updateAlias(did: String, newAlias: String): Resource<Unit> {
        return try {
            val childIdsJson = encryptedPrefs.getString(KEY_CHILD_IDS, "[]") ?: "[]"
            val arr = JSONArray(childIdsJson)
            val isChild = (0 until arr.length()).any { arr.getString(it) == did }

            if (isChild) {
                // 更新副账号别名
                val metaJson = encryptedPrefs.getString(KEY_CHILD_PREFIX + did, null)
                if (metaJson != null) {
                    val meta = org.json.JSONObject(metaJson)
                    meta.put("alias", newAlias)
                    encryptedPrefs.edit().putString(KEY_CHILD_PREFIX + did, meta.toString()).apply()
                }
            } else {
                // 更新主账号别名
                encryptedPrefs.edit().putString(KEY_ALIAS_PREF, newAlias).apply()
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(message = "更新别名失败: ${e.message}", throwable = e)
        }
    }

    override suspend fun getAllIdentities(): Resource<List<DidInfo>> {
        return try {
            val identities = mutableListOf<DidInfo>()

            // 主账号
            identities.add(
                DidInfo(
                    did = computeDidIdentifier(keyManager.exportPublicKeyPem(MASTER_KEY_ALIAS)),
                    alias = "",
                    role = "PRIMARY",
                    isActive = true,
                    created = System.currentTimeMillis()
                )
            )

            // 副账号（从持久化中读取）
            val childIdsJson = encryptedPrefs.getString(KEY_CHILD_IDS, "[]") ?: "[]"
            val arr = JSONArray(childIdsJson)
            for (i in 0 until arr.length()) {
                val childDid = arr.getString(i)
                val metaJson = encryptedPrefs.getString(KEY_CHILD_PREFIX + childDid, null)
                if (metaJson != null) {
                    val meta = org.json.JSONObject(metaJson)
                    identities.add(
                        DidInfo(
                            did = childDid,
                            alias = meta.optString("alias", ""),
                            role = meta.optString("role", "STANDARD"),
                            isActive = false,
                            created = meta.optLong("created", System.currentTimeMillis())
                        )
                    )
                }
            }

            Resource.Success(identities)
        } catch (e: Exception) {
            Resource.Error(message = "获取身份列表失败: ${e.message}", throwable = e)
        }
    }

    private fun computeDidIdentifier(publicKeyPem: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyPem.toByteArray(Charsets.UTF_8))
        val suffix = hash.copyOfRange(hash.size - HASH_SUFFIX_LENGTH, hash.size)
        val hex = suffix.joinToString("") { "%02x".format(it) }
        return "did:sovexis:0x$hex"
    }

    private fun generateUniqueCode(did: String, type: String): String {
        val input = "$did$type"
        val bytes = input.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }
}
