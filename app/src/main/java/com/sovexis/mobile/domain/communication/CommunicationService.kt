package com.sovexis.mobile.domain.communication

import com.sovexis.mobile.core.result.Resource

/**
 * Sovexis é€šä¿¡æž¶æž„æœåŠ¡æŽ¥å£
 *
 * [AI-GENERATED]
 * ç”Ÿæˆæ—¶é—´: 2026-05-09
 * å®žçŽ°çŠ¶æ€? âš ï¸ AIéƒ¨åˆ†å®žçŽ°
 * äººå·¥è¡¥å……: Noiseåè®®å®Œæ•´å®žçŽ°
 *
 * äº”å±‚é€šä¿¡æž¶æž„ï¼ˆè‡ªä¸‹è€Œä¸Šï¼‰ï¼š
 * 1. ç‰©ç†å±‚ï¼šAndroid Keystore + StrongBox + æœ¬åœ°å­˜å‚¨
 * 2. ä¼ è¾“å±‚ï¼šæœåŠ¡å•†é€‚é…å™?-> éšç§ä¸­ç»§ (Nym/Mixnet) + å¯¹ç­‰å¹¿æ’­ (Dandelion++/Flooding)
 * 3. éšè”½åè®®å±‚ï¼ˆæœªæ¥å¯ç”¨ï¼‰ï¼šæµé‡æ•´å½¢ã€éšæœºè™šæ‹Ÿäº‹ä»¶æ³¨å…¥ã€éžäº¤äº’å¼ä¸Šä¸‹æ–‡åˆ‡æ¢
 * 4. åŠ å¯†é€šä¿¡å±‚ï¼šECDH P-256/X25519 å¯†é’¥åå•† + AES-GCM-256 æ¶ˆæ¯åŠ å¯† + DID èº«ä»½è®¤è¯
 * 5. åº”ç”¨å±‚ï¼šæ”¯ä»˜ / å‡­è¯ / ä¿é™©ç®?/ Agent API
 *
 * [MANUAL-IMPLEMENTATION-REQUIRED]
 * åŽŸå› : Noiseåè®®æ¡æ‰‹å¤æ‚ï¼Œéœ€ä¸“ä¸šå®žçŽ°
 * å‚è€? https://noiseprotocol.org/noise.html
 * é¢„ä¼°å·¥æ—¶: 30h
 * æŠ€èƒ½è¦æ±? å¯†ç å­¦åè®®ã€ç½‘ç»œå®‰å…? * ðŸ”’ éœ€å®‰å…¨å®¡è®¡
 */
interface CommunicationService {

    // ========== åŠ å¯†é€šä¿¡å±?==========

    /**
     * ECDH å¯†é’¥åå•†
     * æ”¯æŒ P-256 å’?X25519 ä¸¤ç§æ›²çº¿
     *
     * @param peerPublicKeyPem å¯¹ç«¯å…¬é’¥ PEM
     * @param curveType æ›²çº¿ç±»åž‹
     * @return Resource<SharedSecret> å…±äº«å¯†é’¥
     */
    suspend fun keyAgreement(
        peerPublicKeyPem: String,
        curveType: CurveType = CurveType.P256
    ): Resource<SharedSecret>

    /**
     * åŠ å¯†æ¶ˆæ¯
     *
     * @param plaintext æ˜Žæ–‡æ¶ˆæ¯
     * @param sharedSecret å…±äº«å¯†é’¥
     * @param associatedData å…³è”æ•°æ®ï¼ˆAEADï¼?     * @return Resource<EncryptedMessage> åŠ å¯†æ¶ˆæ¯
     */
    suspend fun encryptMessage(
        plaintext: ByteArray,
        sharedSecret: SharedSecret,
        associatedData: ByteArray? = null
    ): Resource<EncryptedMessage>

    /**
     * è§£å¯†æ¶ˆæ¯
     *
     * @param encryptedMessage åŠ å¯†æ¶ˆæ¯
     * @param sharedSecret å…±äº«å¯†é’¥
     * @param associatedData å…³è”æ•°æ®ï¼ˆAEADï¼?     * @return Resource<ByteArray> æ˜Žæ–‡æ¶ˆæ¯
     */
    suspend fun decryptMessage(
        encryptedMessage: EncryptedMessage,
        sharedSecret: SharedSecret,
        associatedData: ByteArray? = null
    ): Resource<ByteArray>

    // ========== ä¼ è¾“å±?==========

    /**
     * é€šè¿‡éšç§ä¸­ç»§å‘é€æ¶ˆæ?     *
     * @param recipientDid æŽ¥æ”¶è€?DID
     * @param encryptedMessage åŠ å¯†æ¶ˆæ¯
     * @return Resource<String> æ¶ˆæ¯ ID
     */
    suspend fun sendViaRelay(
        recipientDid: String,
        encryptedMessage: EncryptedMessage
    ): Resource<String>

    /**
     * é€šè¿‡å¯¹ç­‰å¹¿æ’­å‘é€æ¶ˆæ?     * Dandelion++ / Flooding åè®®
     *
     * @param encryptedMessage åŠ å¯†æ¶ˆæ¯
     * @param ttl ç”Ÿå­˜æ—¶é—´
     * @return Resource<String> æ¶ˆæ¯ ID
     */
    suspend fun broadcastPeer(
        encryptedMessage: EncryptedMessage,
        ttl: Int = 3
    ): Resource<String>

    /**
     * æŽ¥æ”¶æ¶ˆæ¯
     *
     * @return Resource<List<EncryptedMessage>> å¾…å¤„ç†æ¶ˆæ¯åˆ—è¡?     */
    suspend fun receiveMessages(): Resource<List<EncryptedMessage>>

    // ========== DID èº«ä»½è®¤è¯ ==========

    /**
     * DID è®¤è¯æ¡æ‰‹
     *
     * @param peerDid å¯¹ç«¯ DID
     * @param challenge æŒ‘æˆ˜å€?     * @return Resource<DidAuthResult> è®¤è¯ç»“æžœ
     */
    suspend fun didAuthHandshake(
        peerDid: String,
        challenge: String
    ): Resource<DidAuthResult>
}

/**
 * æ›²çº¿ç±»åž‹
 */
enum class CurveType {
    P256,    // ECDH P-256
    X25519   // ECDH X25519
}

/**
 * å…±äº«å¯†é’¥
 */
data class SharedSecret(
    val secretBytes: ByteArray,
    val curveType: CurveType,
    val derivedAt: Long = System.currentTimeMillis()
)

/**
 * åŠ å¯†æ¶ˆæ¯
 */
data class EncryptedMessage(
    val messageId: String,
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val authTag: ByteArray,
    val senderDid: String? = null,
    val recipientDid: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val protocol: String = "Sovexis-v1"
)

/**
 * DID è®¤è¯ç»“æžœ
 */
data class DidAuthResult(
    val peerDid: String,
    val isAuthenticated: Boolean,
    val sessionKey: SharedSecret? = null,
    val errorMessage: String? = null
)
