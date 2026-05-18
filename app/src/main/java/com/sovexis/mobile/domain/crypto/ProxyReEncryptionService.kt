package com.sovexis.mobile.domain.crypto

import com.sovexis.mobile.core.result.Resource

/**
 * Sovexis ä»£ç†é‡�åŠ å¯†æœåŠ¡æŽ¥å£
 *
 * [AI-GENERATED]
 * ç”Ÿæˆæ—¶é—´: 2026-05-09
 * å®žçŽ°çŠ¶æ€�: âš ï¸� AIéƒ¨åˆ†å®žçŽ°
 * äººå·¥è¡¥å……: æ ¸å¿ƒç®—æ³•å®‰å…¨å®¡è®¡
 *
 * åŸºäºŽ Dart proxy_recrypt æ€æƒ³çš„ Kotlin è½»é‡�é‡�ç§»æ¤�
 * ä¿�é™©ç®±æ•°æ�®åˆ†äº«åœºæ™¯ï¼šç”Ÿæˆ�"é‡�åŠ å¯†å¯†é’¥"ç”±æœ�åŠ¡å•†å®Œæˆ�å¯†æ–‡è½¬æ�¢ï¼Œæ— éœ€è§£å¯†æˆ�æ˜Žæ–‡
 * ä½¿ç”¨ AES-GCM + ECDHï¼ŒAPK ä½“ç§¯å¢žé‡�çº¦ 80KB
 *
 * [MANUAL-IMPLEMENTATION-REQUIRED]
 * åŽŸå› : é‡�åŠ å¯†ç®—æ³•éœ€å®‰å…¨å®¡è®¡ç¡®ä¿�ä¸�æ³„éœ²ç§�é’¥
 * å�‚è€ƒ https://github.com/konstantinullrich/proxy_recrypt
 * é¢„ä¼°å·¥æ—¶: 20h
 * æŠ€èƒ½è¦�æ±‚: å¯†ç �å­¦ã€�å®‰å…¨å®¡è®¡
 * ðŸ”’ éœ€å®‰å…¨å®¡è®¡
 */
interface ProxyReEncryptionService {

    /**
     * ç”Ÿæˆé‡åŠ å¯†å¯†é’?     *
     * ä»Žå‘é€è€…å¯†é’¥å¯¹å’ŒæŽ¥æ”¶è€…å…¬é’¥ç”Ÿæˆé‡åŠ å¯†å¯†é’¥
     * æœåŠ¡å•†ä½¿ç”¨æ­¤å¯†é’¥å°†å¯†æ–‡ä»Žå‘é€è€…è½¬æ¢ä¸ºæŽ¥æ”¶è€…å¯è§£å¯†çš„å½¢å¼?     *
     * @param senderKeyAlias å‘é€è€…å¯†é’¥åˆ«å?     * @param receiverPublicKeyPem æŽ¥æ”¶è€…å…¬é’?PEM
     * @return Resource<ReEncryptionKey> é‡åŠ å¯†å¯†é’?     */
    suspend fun generateReEncryptionKey(
        senderKeyAlias: String,
        receiverPublicKeyPem: String
    ): Resource<ReEncryptionKey>

    /**
     * åŠ å¯†æ•°æ®ï¼ˆç”¨äºŽä¿é™©ç®±å­˜å‚¨ï¼?     *
     * @param plaintext æ˜Žæ–‡æ•°æ®
     * @param ownerKeyAlias æ‰€æœ‰è€…å¯†é’¥åˆ«å?     * @return Resource<EncryptedPayload> åŠ å¯†ç»“æžœ
     */
    suspend fun encrypt(
        plaintext: ByteArray,
        ownerKeyAlias: String
    ): Resource<EncryptedPayload>

    /**
     * è§£å¯†æ•°æ®
     *
     * @param encryptedPayload åŠ å¯†è½½è·
     * @param ownerKeyAlias æ‰€æœ‰è€…å¯†é’¥åˆ«å?     * @return Resource<ByteArray> è§£å¯†ç»“æžœ
     */
    suspend fun decrypt(
        encryptedPayload: EncryptedPayload,
        ownerKeyAlias: String
    ): Resource<ByteArray>

    /**
     * ä»£ç†é‡åŠ å¯?     * å°†å¯†æ–‡ä»Žå‘é€è€…è½¬æ¢ä¸ºæŽ¥æ”¶è€…å¯è§£å¯†çš„å½¢å¼?     * æ­¤æ“ä½œç”±æœåŠ¡å•†æ‰§è¡Œï¼Œä¸æŽ¥è§¦æ˜Žæ–?     *
     * @param encryptedPayload åŽŸå§‹åŠ å¯†è½½è·
     * @param reEncryptionKey é‡åŠ å¯†å¯†é’?     * @return Resource<EncryptedPayload> é‡åŠ å¯†åŽçš„è½½è?     */
    suspend fun reEncrypt(
        encryptedPayload: EncryptedPayload,
        reEncryptionKey: ReEncryptionKey
    ): Resource<EncryptedPayload>
}

/**
 * é‡åŠ å¯†å¯†é’? */
data class ReEncryptionKey(
    val keyId: String,
    val keyBytes: ByteArray,
    val senderDid: String,
    val receiverDid: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReEncryptionKey) return false
        return keyId == other.keyId
    }
    override fun hashCode(): Int = keyId.hashCode()
}

/**
 * åŠ å¯†è½½è·
 */
data class EncryptedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val authTag: ByteArray,
    val algorithm: String = "AES-GCM-256",
    val encryptedFor: String  // DID æˆ–å¯†é’¥æ ‡è¯?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return encryptedFor == other.encryptedFor
    }
    override fun hashCode(): Int = encryptedFor.hashCode()
}
