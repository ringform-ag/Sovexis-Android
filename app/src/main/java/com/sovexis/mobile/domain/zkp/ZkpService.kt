package com.sovexis.mobile.domain.zkp

import com.sovexis.mobile.core.result.Resource

/**
 * Sovexis é›¶çŸ¥è¯†è¯æ˜ŽæœåŠ¡æŽ¥å£
 *
 * [AI-GENERATED]
 * ç”Ÿæˆæ—¶é—´: 2026-05-09
 * å®žçŽ°çŠ¶æ€: âš ï¸ AIéƒ¨åˆ†å®žçŽ°
 * äººå·¥è¡¥å……: Groth16ç”µè·¯è®¾è®¡ã€æœ‰é™åŸŸè¿ç®—ä¼˜åŒ–
 *
 * åŸºäºŽ Microsoft Crescent (2025)
 * ä½¿ç”¨ Groth16 é›¶çŸ¥è¯†è¯æ˜ŽåŒ…è£…ç”Ÿç‰©è®¤è¯
 * ä»…è¯æ˜Ž"å¯å‡ºç¤ºå‡­è¯"è€Œä¸æ³„éœ²æ•æ„Ÿä¿¡æ¯ï¼ˆå¦‚ DIDã€å‡ºç”Ÿæ—¥æœŸã€ç”Ÿç‰©ç‰¹å¾ï¼‰
 *
 * è®¡ç®—åˆ†ä¸¤é˜¶æ®µï¼š
 * - Prepareï¼ˆä¸€æ¬¡æ€§æœ¬åœ°ç”Ÿæˆï¼‰ï¼šç”Ÿæˆ ZK ç”µè·¯å‚æ•°ã€è§è¯å’Œè¯æ˜Žå¯†é’¥
 * - Showï¼ˆæ¯æ¬¡å‡ºç¤ºå¿«é€Ÿè¯æ˜Žï¼‰ï¼šä½¿ç”¨é¢„ç”Ÿæˆå‚æ•°å¿«é€Ÿç”Ÿæˆè¯æ˜Ž
 *
 * [MANUAL-IMPLEMENTATION-REQUIRED]
 * åŽŸå› : Groth16ç”µè·¯éœ€è¦å¯†ç å­¦ä¸“å®¶è®¾è®¡
 * å‚è€ƒ https://www.microsoft.com/en-us/research/project/crescent/
 * é¢„ä¼°å·¥æ—¶: 40h
 * æŠ€èƒ½è¦æ±‚: é›¶çŸ¥è¯†è¯æ˜Žã€Circomç”µè·¯è®¾è®¡
 */
interface ZkpService {

    /**
     * å‡†å¤‡é˜¶æ®µ - ä¸€æ¬¡æ€§æœ¬åœ°ç”Ÿæˆ?     * ç”Ÿæˆ ZK ç”µè·¯å‚æ•°ã€è§è¯å’Œè¯æ˜Žå¯†é’¥
     *
     * @param credentialType å‡­è¯ç±»åž‹
     * @param privateInputs ç§æœ‰è¾“å…¥ï¼ˆå¦‚å‡ºç”Ÿæ—¥æœŸã€DID ç­‰æ•æ„Ÿä¿¡æ¯ï¼‰
     * @param publicInputs å…¬å…±è¾“å…¥ï¼ˆå¦‚å¹´é¾„èŒƒå›´è¦æ±‚ç­‰ï¼‰
     * @return Resource<ZkpPrepareResult> å‡†å¤‡ç»“æžœï¼ŒåŒ…å«è¯æ˜Žå‚æ•?ID
     */
    suspend fun prepare(
        credentialType: String,
        privateInputs: Map<String, Any>,
        publicInputs: Map<String, Any>
    ): Resource<ZkpPrepareResult>

    /**
     * å‡ºç¤ºé˜¶æ®µ - å¿«é€Ÿç”Ÿæˆè¯æ˜?     * ä½¿ç”¨é¢„ç”Ÿæˆå‚æ•°å¿«é€Ÿç”Ÿæˆé›¶çŸ¥è¯†è¯æ˜Ž
     *
     * @param prepareResultId å‡†å¤‡é˜¶æ®µè¿”å›žçš„å‚æ•?ID
     * @param challenge æŒ‘æˆ˜å€¼ï¼ˆç”±éªŒè¯æ–¹æä¾›ï¼?     * @return Resource<ZkpProof> ç”Ÿæˆçš„é›¶çŸ¥è¯†è¯æ˜Ž
     */
    suspend fun show(
        prepareResultId: String,
        challenge: String? = null
    ): Resource<ZkpProof>

    /**
     * éªŒè¯é›¶çŸ¥è¯†è¯æ˜?     *
     * @param proof é›¶çŸ¥è¯†è¯æ˜?     * @param publicInputs å…¬å…±è¾“å…¥
     * @return Resource<ZkpVerifyResult> éªŒè¯ç»“æžœ
     */
    suspend fun verify(
        proof: ZkpProof,
        publicInputs: Map<String, Any>
    ): Resource<ZkpVerifyResult>

    /**
     * æ¸…ç†é¢„ç”Ÿæˆå‚æ•?     *
     * @param prepareResultId å‚æ•° ID
     */
    suspend fun cleanup(prepareResultId: String)
}

/**
 * ZKP å‡†å¤‡ç»“æžœ
 */
data class ZkpPrepareResult(
    val prepareResultId: String,
    val circuitType: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * é›¶çŸ¥è¯†è¯æ˜? */
data class ZkpProof(
    val proofId: String,
    val proofBytes: ByteArray,
    val publicInputs: Map<String, Any>,
    val proofType: String = "Groth16"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZkpProof) return false
        return proofId == other.proofId
    }

    override fun hashCode(): Int = proofId.hashCode()
}

/**
 * ZKP éªŒè¯ç»“æžœ
 */
data class ZkpVerifyResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
