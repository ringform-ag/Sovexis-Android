package com.sovexis.mobile.domain.crypto

import com.sovexis.mobile.core.result.Resource

/**
 * Sovexis é˜ˆå€¼ç­¾å��æœ�åŠ¡æ�¥å�£
 *
 * [AI-GENERATED]
 * ç”Ÿæˆ�æ—¶é—´: 2026-05-09
 * å®žç�°çŠ¶æ€�: âš ï¸� AIéƒ¨åˆ†å®žç�°
 * äººå·¥è¡¥å……: 2P-ECDSAå��è®®å®žç�°
 *
 * åŸºäºŽ RGSSï¼ˆRandom Grid Secret Sharingï¼‰çš„ IoT çº§è½»é‡�æ–¹æ¡ˆ
 * é«˜å®‰å…¨æ¨¡å¼�ä¸‹ï¼Œç­¾å��å¯†é’¥æ‹†åˆ†ä¸ºä¸¤ä»½é¢�ï¼š
 * - ä»½é¢� 1ï¼šç§»åŠ¨ç«¯
 * - ä»½é¢� 2ï¼šå®¶åº­æœ�åŠ¡å™¨ / ç¡¬ä»¶ä»¤ç‰Œ
 *
 * ç­¾å��è®¡ç®—ä»…éœ€ SHA-256 å’Œå¼‚æˆ–è¿�ç®—
 *
 * [MANUAL-IMPLEMENTATION-REQUIRED]
 * åŽŸå› : 2P-ECDSAå��è®®å¤�æ�‚ï¼Œéœ€å¯†ç �å­¦ä¸“å®¶å®žç�°
 * å�‚è€ƒ https://eprint.iacr.org/2017/552 (Lindell 2P-ECDSA)
 * é¢„ä¼°å·¥æ—¶: 60h
 * æŠ€èƒ½è¦�æ±‚ï¼šå¤šæ–¹è®¡ç®—ã€�ECDSAå��è®®
 * ðŸ”’ éœ€å®‰å…¨å®¡è®¡
 *
 * è¿‡æ¸¡æ–¹æ¡ˆ: RGSSåˆ†å‰² + æœ¬åœ°é‡�ç»„ç­¾å��ï¼ˆå®‰å…¨æ€§æœªæ��å�‡ï¼‰
 */
interface ThresholdSignatureService {

    /**
     * ç”Ÿæˆé˜ˆå€¼ç­¾åå¯†é’¥ä»½é¢?     *
     * å°†ä¸»å¯†é’¥æ‹†åˆ†ä¸?n ä»½ï¼Œéœ€è¦?t ä»½æ‰èƒ½é‡å»ºç­¾å?     * MVP é˜¶æ®µï¼št=2, n=2ï¼ˆç§»åŠ¨ç«¯ + å®¶åº­æœåŠ¡å™?ç¡¬ä»¶ä»¤ç‰Œï¼?     *
     * @param keyAlias å¯†é’¥åˆ«å
     * @param shares ä»½é¢æ•°é‡
     * @param threshold é‡å»ºé˜ˆå€?     * @return Resource<ThresholdKeyShares> å¯†é’¥ä»½é¢
     */
    suspend fun generateKeyShares(
        keyAlias: String,
        shares: Int = 2,
        threshold: Int = 2
    ): Resource<ThresholdKeyShares>

    /**
     * ä½¿ç”¨æœ¬åœ°ä»½é¢è¿›è¡Œéƒ¨åˆ†ç­¾å
     *
     * @param shareId ä»½é¢ ID
     * @param data å¾…ç­¾åæ•°æ?     * @return Resource<PartialSignature> éƒ¨åˆ†ç­¾å
     */
    suspend fun partialSign(
        shareId: String,
        data: ByteArray
    ): Resource<PartialSignature>

    /**
     * åˆå¹¶éƒ¨åˆ†ç­¾åä¸ºå®Œæ•´ç­¾å?     *
     * @param partialSignatures éƒ¨åˆ†ç­¾ååˆ—è¡¨ï¼ˆè‡³å°?threshold ä¸ªï¼‰
     * @return Resource<ThresholdSignature> å®Œæ•´ç­¾å
     */
    suspend fun combineSignatures(
        partialSignatures: List<PartialSignature>
    ): Resource<ThresholdSignature>

    /**
     * éªŒè¯é˜ˆå€¼ç­¾å?     *
     * @param publicKey å…¬é’¥
     * @param data åŽŸå§‹æ•°æ®
     * @param signature é˜ˆå€¼ç­¾å?     * @return Boolean ç­¾åæ˜¯å¦æœ‰æ•ˆ
     */
    suspend fun verify(
        publicKey: ByteArray,
        data: ByteArray,
        signature: ThresholdSignature
    ): Boolean

    /**
     * èŽ·å–æœ¬åœ°å­˜å‚¨çš„ä»½é¢ä¿¡æ?     *
     * @return Resource<List<KeyShareInfo>> ä»½é¢åˆ—è¡¨
     */
    suspend fun getLocalShares(): Resource<List<KeyShareInfo>>
}

/**
 * é˜ˆå€¼å¯†é’¥ä»½é¢? */
data class ThresholdKeyShares(
    val keyAlias: String,
    val threshold: Int,
    val shares: List<KeyShare>
)

/**
 * å¯†é’¥ä»½é¢
 */
data class KeyShare(
    val shareId: String,
    val shareData: ByteArray,
    val location: ShareLocation,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyShare) return false
        return shareId == other.shareId
    }
    override fun hashCode(): Int = shareId.hashCode()
}

/**
 * ä»½é¢å­˜å‚¨ä½ç½®
 */
enum class ShareLocation {
    LOCAL,          // ç§»åŠ¨ç«¯æœ¬åœ°ï¼ˆKeystoreï¼?    HOME_SERVER,    // å®¶åº­æœåŠ¡å™?    HARDWARE_TOKEN  // ç¡¬ä»¶ä»¤ç‰Œ
}

/**
 * ä»½é¢ä¿¡æ¯æ‘˜è¦
 */
data class KeyShareInfo(
    val shareId: String,
    val location: ShareLocation,
    val keyAlias: String,
    val createdAt: Long
)

/**
 * éƒ¨åˆ†ç­¾å
 */
data class PartialSignature(
    val shareId: String,
    val signatureData: ByteArray
)

/**
 * é˜ˆå€¼ç­¾å? */
data class ThresholdSignature(
    val signatureBytes: ByteArray,
    val signerShares: List<String>
)
