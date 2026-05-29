package com.sovexis.domain.vc

import com.sovexis.core.result.Resource

/**
 * Sovexis å¯éªŒè¯å‡­è¯æœåŠ¡æŽ¥å£
 *
 * åŸºäºŽ Multipaz (0.5.0) + Inji (v0.19.0) åº“
 * ä¿ç•™ç®€åŒ– VC JSON æ¨¡åž‹ï¼Œåºåˆ—åŒ–å±‚å¯æŠ½æ¢ä¸º SD-JWT æ ¼å¼
 * Inji åº“è´Ÿè´£å‡­è¯çš„ç­¾å‘/å‡ºç¤º/éªŒè¯æµç¨‹æ ‡å‡†åŒ–
 * æ”¯æŒ Verifiable Presentation (VP) æµç¨‹åŠé€‰æ‹©æ€§æŠ«éœ²
 *
 * [TODO] å¾…å®žçŽ°ï¼šæŽ¥å…¥ Multipaz/Inji åº“åŽå¡«å……å…·ä½“å®žçŽ°
 */
interface CredentialService {

    /**
     * ç­¾å‘æ–°çš„å¯éªŒè¯å‡­è¯?     *
     * @param ownerDid æŒæœ‰è€?DID
     * @param credentialType å‡­è¯ç±»åž‹ï¼ˆå¦‚ "IdentityCredential", "AgeCredential"ï¼?     * @param claims å‡­è¯å£°æ˜Žé”®å€¼å¯¹
     * @return Resource<VerifiableCredential> ç­¾å‘ç»“æžœ
     */
    suspend fun issueCredential(
        ownerDid: String,
        credentialType: String,
        claims: Map<String, Any>
    ): Resource<VerifiableCredential>

    /**
     * åˆ›å»ºå¯éªŒè¯è¡¨è¾?(VP)
     *
     * @param credentialId å‡­è¯ ID
     * @param disclosureFields é€‰æ‹©æ€§æŠ«éœ²çš„å­—æ®µåˆ—è¡¨ï¼ˆç©ºè¡¨ç¤ºå…¨éƒ¨æŠ«éœ²ï¼?     * @return Resource<VerifiablePresentation> VP åˆ›å»ºç»“æžœ�?     */
    suspend fun createPresentation(
        credentialId: String,
        disclosureFields: List<String>? = null
    ): Resource<VerifiablePresentation>

    /**
     * åˆ›å»ºå¯éªŒè¯è¡¨è¾?(VP)ï¼ˆå¸¦ ZKP è¯æ˜Žï¼?     *
     * [AI-GENERATED]
     * å®žçŽ°çŠ¶æ€: âœ… å·²å®Œæˆï¼ˆ2026-05-22ï¼?     * å‚è€ƒæ–‡æ¡£: Sovexis Â· å‡­è¯å‡ºç¤ºæµç¨‹åº”ç”¨å±‚ä¸²è”æŒ‡ä»¤
     *
     * @param credentialId å‡­è¯ ID
     * @param disclosureFields é€‰æ‹©æ€§æŠ«éœ²çš„å­—æ®µåˆ—è¡¨ï¼ˆç©ºè¡¨ç¤ºå…¨éƒ¨æŠ«éœ²ï¼?     * @param proof ZKP è¯æ˜Ž
     * @return Resource<VerifiablePresentation> VP åˆ›å»ºç»“æžœ�?     */
    suspend fun createPresentation(
        credentialId: String,
        disclosureFields: List<String>?,
        proof: com.sovexis.domain.zkp.ZkpProof
    ): Resource<VerifiablePresentation>

    /**
     * éªŒè¯å¯éªŒè¯å‡­è¯?     *
     * @param credentialJson VC JSON æ•°æ®
     * @return Resource<VerificationResult> éªŒè¯ç»“æžœ
     */
    suspend fun verifyCredential(credentialJson: String): Resource<VerificationResult>

    /**
     * éªŒè¯å¯éªŒè¯è¡¨è¾?     *
     * @param presentationJson VP JSON æ•°æ®
     * @return Resource<VerificationResult> éªŒè¯ç»“æžœ
     */
    suspend fun verifyPresentation(presentationJson: String): Resource<VerificationResult>

    /**
     * èŽ·å–æŒ‡å®š DID çš„æ‰€æœ‰å‡­è¯?     *
     * @param ownerDid æŒæœ‰è€?DID
     * @return Resource<List<VerifiableCredential>> å‡­è¯åˆ—è¡¨
     */
    suspend fun getCredentialsByOwner(ownerDid: String): Resource<List<VerifiableCredential>>

    /**
     * æ’¤é”€å‡­è¯
     *
     * @param credentialId å‡­è¯ ID
     * @return Resource<Unit> æ’¤é”€ç»“æžœ
     */
    suspend fun revokeCredential(credentialId: String): Resource<Unit>
}

/**
 * å¯éªŒè¯å‡­è¯? */
data class VerifiableCredential(
    val credentialId: String,
    val context: List<String>,
    val type: List<String>,
    val issuer: String,
    val issuanceDate: String,
    val expirationDate: String?,
    val credentialSubject: Map<String, Any>,
    val proof: Proof,
    val selectiveDisclosureFields: List<String>? = null
)

/**
 * å¯éªŒè¯è¡¨è¾? */
data class VerifiablePresentation(
    val presentationId: String,
    val context: List<String>,
    val type: List<String>,
    val verifiableCredential: List<VerifiableCredential>,
    val proof: Proof
)

/**
 * è¯æ˜Žä¿¡æ¯
 */
data class Proof(
    val type: String,
    val created: String,
    val verificationMethod: String,
    val proofPurpose: String,
    val proofValue: String
)

/**
 * éªŒè¯ç»“æžœ
 */
data class VerificationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
