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
 * Sovexis DID æœåŠ¡å®žçŽ°
 *
 * ã€å¼•ç”¨æ¥æºã€‘åŸºäºŽåºŸæ¡?IdentityManager.kt é€»è¾‘
 * - DID ç”Ÿæˆç®—æ³•ï¼šåºŸæ¡ˆç¬¬ 290-295 è¡? * - BIP-32 æ´¾ç”Ÿè·¯å¾„ï¼šåºŸæ¡ˆç¬¬ 300-308 è¡? * - å”¯ä¸€æ ‡è¯†ç ç”Ÿæˆï¼šåºŸæ¡ˆç¬?313-318 è¡? *
 * ã€è°ƒæ•´è¯´æ˜Žã€? * 1. é€‚é… Repository æ¨¡å¼
 * 2. ç»Ÿä¸€ä½¿ç”¨ Resource å°è£…ç»“æžœ
 * 3. æ·»åŠ å®Œæ•´æ³¨é‡Šå’Œå¼•ç”¨æ ‡è®? *
 * @author Sovexis æž¶æž„ç»? * @since 3.0.0
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
     * åˆ›å»ºæ–°çš„åŽ»ä¸­å¿ƒåŒ–èº«ä»½
     *
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?IdentityManager.kt ç¬?110-167 è¡Œï¼ˆæµç¨‹å‚è€ƒï¼‰
     * ã€è°ƒæ•´ã€‘ç§»é™?WebAuthn ä¾èµ–ï¼Œä½¿ç”¨çº¯æœ¬åœ°å¯†é’¥ç”Ÿæˆ
     *
     * æµç¨‹ï¼?     * 1. ç”Ÿæˆ ECDSA P-256 å¯†é’¥å¯¹ï¼ˆå­˜å‚¨åˆ?Keystore/StrongBoxï¼?     * 2. å¯¼å‡ºå…¬é’¥ PEM
     * 3. è®¡ç®— SHA-256(PEM_UTF8_BYTES) å–åŽ 32 å­—èŠ‚åå…­è¿›åˆ¶
     * 4. æ‹¼æŽ¥ did:sovexis:0x{hex64}
     * 5. ç»‘å®šåˆ«ååˆ°æœ¬åœ°æ•°æ®åº“
     *
     * @param alias ç”¨æˆ·è‡ªå®šä¹‰åˆ«å?     * @return Resource<DidDocument> åˆ›å»ºç»“æžœï¼ŒåŒ…å?DID æ–‡æ¡£
     */
    override suspend fun createIdentity(alias: String): Resource<DidDocument> {
        return try {
            // æ£€æŸ¥æ˜¯å¦å·²å­˜åœ¨ä¸»è´¦å?            val existingMaster = accountDao.getAccountsByRole(AccountRole.PRIMARY).first().firstOrNull()
            if (existingMaster != null) {
                return Resource.Error(message = "ä¸»è´¦å·å·²å­˜åœ¨")
            }

            // ç”Ÿæˆä¸»å¯†é’?            keyManager.generateKeyPair(MASTER_KEY_ALIAS)

            // å¯¼å‡ºå…¬é’¥ PEM
            val publicKeyPem = keyManager.exportPublicKeyPem(MASTER_KEY_ALIAS)

            // ç”Ÿæˆ DID
            val did = computeDidIdentifier(publicKeyPem)

            // åˆ›å»º DID æ–‡æ¡£
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

            // ä¿å­˜åˆ°æ•°æ®åº“
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
            Resource.Error(message = "åˆ›å»ºèº«ä»½å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * ä»Žå·²æœ‰å¯†é’¥æ¢å¤?DID
     *
     * @param keyAlias Keystore ä¸­çš„å¯†é’¥åˆ«å
     * @param alias ç”¨æˆ·è‡ªå®šä¹‰åˆ«å?     * @return Resource<DidDocument> æ¢å¤ç»“æžœ
     */
    override suspend fun restoreIdentity(keyAlias: String, alias: String): Resource<DidDocument> {
        return try {
            if (!keyManager.keyExists(keyAlias)) {
                return Resource.Error(message = "å¯†é’¥ä¸å­˜åœ? $keyAlias")
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
            Resource.Error(message = "æ¢å¤èº«ä»½å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * æ´¾ç”Ÿå‰¯è´¦å?     *
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?IdentityManager.kt ç¬?184-225 è¡?     *
     * @param type å‰¯è´¦å·ç±»åž‹ï¼ˆSTANDARD/STEWARDï¼?     * @param alias åˆ«å
     * @return Resource<DidInfo> æ´¾ç”Ÿç»“æžœ
     */
    suspend fun deriveChildIdentity(type: AccountRole, alias: String): Resource<DidInfo> {
        return try {
            val masterAccount = accountDao.getAccountsByRole(AccountRole.PRIMARY).first().firstOrNull()
                ?: return Resource.Error(message = "ä¸»è´¦å·ä¸å­˜åœ¨")

            // ç”Ÿæˆæ´¾ç”Ÿè·¯å¾„
            val derivationPath = generateDerivationPath(type)

            // ç”Ÿæˆæ´¾ç”Ÿå¯†é’¥åˆ«å
            val childKeyAlias = "${masterAccount.did}_$derivationPath"

            // ç”Ÿæˆæ–°çš„å¯†é’¥å¯?            keyManager.generateKeyPair(childKeyAlias)

            // å¯¼å‡ºå…¬é’¥
            val publicKeyPem = keyManager.exportPublicKeyPem(childKeyAlias)

            // ç”Ÿæˆ DID
            val did = computeDidIdentifier(publicKeyPem)

            // ç”Ÿæˆå”¯ä¸€æ ‡è¯†ç ?            val uniqueCode = generateUniqueCode(masterAccount.did, derivationPath)

            // ä¿å­˜åˆ°æ•°æ®åº“
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
            Resource.Error(message = "æ´¾ç”Ÿå‰¯è´¦å·å¤±è´? ${e.message}", throwable = e)
        }
    }

    /**
     * èŽ·å–å½“å‰æ´»è·ƒè´¦å·çš?DID æ–‡æ¡£
     *
     * @return Resource<DidDocument> DID æ–‡æ¡£
     */
    override suspend fun getActiveDidDocument(): Resource<DidDocument> {
        return try {
            val activeDid = tokenManager.activeDid.first()
                ?: return Resource.Error(message = "æ²¡æœ‰æ´»è·ƒè´¦å·")

            val account = accountDao.getAccountByDid(activeDid)
                ?: return Resource.Error(message = "è´¦å·ä¸å­˜åœ? $activeDid")

            val didDocument = DidDocument(
                did = account.did,
                alias = account.alias,
                publicKeyPem = account.publicKeyPem,
                keyAlias = account.did, // ç®€åŒ–å¤„ç?                verificationMethods = listOf(
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
            Resource.Error(message = "èŽ·å– DID æ–‡æ¡£å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * è§£æž DID å­—ç¬¦ä¸²�?     *
     * @param did DID å­—ç¬¦ä¸²ï¼Œæ ¼å¼: did:sovexis:0x{64ä½åå…­è¿›åˆ¶}
     * @return DidInfo? è§£æžç»“æžœï¼Œæ ¼å¼æ— æ•ˆè¿”å›ž null
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
     * éªŒè¯ DID æ ¼å¼
     *
     * @param did DID å­—ç¬¦ä¸?     * @return Boolean æ ¼å¼æ˜¯å¦æœ‰æ•ˆ
     */
    override fun isValidDid(did: String): Boolean {
        return did.matches(Regex("^did:sovexis:0x[0-9a-fA-F]{64}$"))
    }

    /**
     * æ›´æ–°åˆ«å
     *
     * @param did åŽ»ä¸­å¿ƒåŒ–èº«ä»½æ ‡è¯†
     * @param newAlias æ–°åˆ«å?     * @return Resource<Unit> æ›´æ–°ç»“æžœ
     */
    override suspend fun updateAlias(did: String, newAlias: String): Resource<Unit> {
        return try {
            val account = accountDao.getAccountByDid(did)
                ?: return Resource.Error(message = "è´¦å·ä¸å­˜åœ? $did")

            val updated = account.copy(alias = newAlias)
            accountDao.updateAccount(updated)

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(message = "æ›´æ–°åˆ«åå¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * èŽ·å–æ‰€æœ‰å·²æ³¨å†Œçš?DID åˆ—è¡¨
     *
     * @return Resource<List<DidInfo>> DID åˆ—è¡¨
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
            Resource.Error(message = "èŽ·å–èº«ä»½åˆ—è¡¨å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * ä»Žå…¬é’?PEM è®¡ç®— DID æ ‡è¯†ç¬?     *
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?IdentityManager.kt ç¬?290-295 è¡?     * SHA-256(PEM_UTF8_BYTES) å–åŽ 32 å­—èŠ‚åå…­è¿›åˆ¶
     *
     * @param publicKeyPem å…¬é’¥ PEM å­—ç¬¦ä¸?     * @return String DID æ ‡è¯†ç¬¦ï¼ˆä¸å«å‰ç¼€ï¼?     */
    private fun computeDidIdentifier(publicKeyPem: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyPem.toByteArray(Charsets.UTF_8))
        // å–åŽ 32 å­—èŠ‚
        val suffix = hash.copyOfRange(hash.size - HASH_SUFFIX_LENGTH, hash.size)
        val hex = suffix.joinToString("") { "%02x".format(it) }
        return "did:sovexis:0x$hex"
    }

    /**
     * ç”Ÿæˆæ´¾ç”Ÿè·¯å¾„
     *
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?IdentityManager.kt ç¬?300-308 è¡?     *
     * @param type è´¦å·ç±»åž‹
     * @return String BIP-32 æ´¾ç”Ÿè·¯å¾„
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
     * ç”Ÿæˆå”¯ä¸€æ ‡è¯†ç ?     *
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?IdentityManager.kt ç¬?313-318 è¡?     *
     * @param masterDid ä¸»è´¦å?DID
     * @param derivationPath æ´¾ç”Ÿè·¯å¾„
     * @return String 8 ä½åå…­è¿›åˆ¶å”¯ä¸€æ ‡è¯†ç ?     */
    private fun generateUniqueCode(masterDid: String, derivationPath: String): String {
        val input = "$masterDid$derivationPath"
        val bytes = input.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }
}
