package com.sovexis.mobile.domain.did

import com.sovexis.mobile.core.result.Resource

/**
 * Sovexis DID æœåŠ¡æŽ¥å£
 *
 * å®žçŽ° did:self + Sovexis DID æ–¹æ³•
 * å¯?ECDSA P-256 å…¬é’¥ PEM çš?UTF-8 å­—èŠ‚è®¡ç®— SHA-256ï¼Œå–å?32 å­—èŠ‚åå…­è¿›åˆ¶
 * æ‹¼æŽ¥ä¸?did:sovexis:0x{64ä½}
 *
 * ä»…ä¾èµ–æœ¬åœ°å…¬é’¥ï¼Œæ— éœ€ä»»ä½•ç½‘ç»œæŸ¥è¯¢æˆ–æ³¨å†? * æ‰€æœ‰ç»‘å®šä¿¡æ¯ï¼ˆDID -> alias -> credentialId -> åŠ å¯†ç§å­ï¼‰åªå­˜åœ¨äºŽæœ¬æœ? *
 * [TODO] å¾…å®žçŽ°ï¼šæŽ¥å…¥åºŸæ¡ˆæ ¸å¿ƒä»£ç åŽå¡«å……å…·ä½“å®žçŽ? */
interface DidService {

    /**
     * åˆ›å»ºæ–°çš„åŽ»ä¸­å¿ƒåŒ–èº«ä»½
     *
     * æµç¨‹ï¼?     * 1. ç”Ÿæˆ ECDSA P-256 å¯†é’¥å¯¹ï¼ˆå­˜å‚¨åˆ?Keystore/StrongBoxï¼?     * 2. å¯¼å‡ºå…¬é’¥ PEM
     * 3. è®¡ç®— SHA-256(PEM_UTF8_BYTES) å–åŽ 32 å­—èŠ‚
     * 4. æ‹¼æŽ¥ä¸?did:sovexis:0x{64ä½åå…­è¿›åˆ¶}
     * 5. ç»‘å®šåˆ«ååˆ°æœ¬åœ°æ•°æ®åº“
     *
     * @param alias ç”¨æˆ·è‡ªå®šä¹‰åˆ«å?     * @return Resource<DidDocument> åˆ›å»ºç»“æžœï¼ŒåŒ…å?DID æ–‡æ¡£
     */
    suspend fun createIdentity(alias: String): Resource<DidDocument>

    /**
     * ä»Žå·²æœ‰å¯†é’¥æ¢å¤?DID
     *
     * @param keyAlias Keystore ä¸­çš„å¯†é’¥åˆ«å
     * @param alias ç”¨æˆ·è‡ªå®šä¹‰åˆ«å?     * @return Resource<DidDocument> æ¢å¤ç»“æžœ
     */
    suspend fun restoreIdentity(keyAlias: String, alias: String): Resource<DidDocument>

    /**
     * èŽ·å–å½“å‰æ´»è·ƒè´¦å·çš?DID æ–‡æ¡£
     *
     * @return Resource<DidDocument> DID æ–‡æ¡£
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
     * éªŒè¯ DID æ ¼å¼
     *
     * @param did DID å­—ç¬¦ä¸?     * @return Boolean æ ¼å¼æ˜¯å¦æœ‰æ•ˆ
     */
    fun isValidDid(did: String): Boolean

    /**
     * æ›´æ–°åˆ«å
     *
     * @param did åŽ»ä¸­å¿ƒåŒ–èº«ä»½æ ‡è¯†
     * @param newAlias æ–°åˆ«å?     * @return Resource<Unit> æ›´æ–°ç»“æžœ
     */
    suspend fun updateAlias(did: String, newAlias: String): Resource<Unit>

    /**
     * èŽ·å–æ‰€æœ‰å·²æ³¨å†Œçš?DID åˆ—è¡¨
     *
     * @return Resource<List<DidInfo>> DID åˆ—è¡¨
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
 * DID ä¿¡æ¯æ‘˜è¦
 */
data class DidInfo(
    val did: String,
    val alias: String,
    val role: String,  // PRIMARY / SUB / STEWARD
    val isActive: Boolean,
    val created: Long
)

/**
 * éªŒè¯æ–¹æ³•
 */
data class VerificationMethod(
    val id: String,
    val type: String,           // EcdsaSecp256r1VerificationKey2019
    val controller: String,
    val publicKeyPem: String
)
