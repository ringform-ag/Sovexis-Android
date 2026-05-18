package com.sovexis.mobile.domain.crypto

import java.security.KeyPair
import java.security.PublicKey

/**
 * Sovexis å¯†é’¥ç®¡ç†æŽ¥å£
 *
 * åŸºäºŽ Android Keystore + StrongBox (API 30+)
 * è´Ÿè´£å¯†é’¥çš„ç”Ÿæˆã€å­˜å‚¨ã€ç­¾åå’ŒéªŒè¯
 *
 * [TODO] å¾…å®žçŽ°ï¼šæŽ¥å…¥åºŸæ¡ˆæ ¸å¿ƒä»£ç åŽå¡«å……å…·ä½“å®žçŽ? */
interface KeyManager {

    /**
     * ç”Ÿæˆæ–°çš„ ECDSA P-256 å¯†é’¥å¯?     * å¯†é’¥å­˜å‚¨åœ?Android Keystore / StrongBox ä¸?     *
     * @param alias å¯†é’¥åˆ«åï¼ˆé€šå¸¸ä½¿ç”¨ DID ä½œä¸ºåˆ«åï¼?     * @return KeyPair ç”Ÿæˆçš„å¯†é’¥å¯¹
     * @throws CryptoException å¯†é’¥ç”Ÿæˆå¤±è´¥
     */
    suspend fun generateKeyPair(alias: String): KeyPair

    /**
     * èŽ·å–æŒ‡å®šåˆ«åçš„å…¬é’?     *
     * @param alias å¯†é’¥åˆ«å
     * @return PublicKey å…¬é’¥
     * @throws KeyNotFoundException å¯†é’¥ä¸å­˜åœ?     */
    suspend fun getPublicKey(alias: String): PublicKey

    /**
     * ä½¿ç”¨ç§é’¥å¯¹æ•°æ®è¿›è¡Œç­¾å?     *
     * @param alias å¯†é’¥åˆ«å
     * @param data å¾…ç­¾åæ•°æ?     * @return ByteArray ç­¾åç»“æžœ
     * @throws CryptoException ç­¾åå¤±è´¥
     */
    suspend fun sign(alias: String, data: ByteArray): ByteArray

    /**
     * ä½¿ç”¨å…¬é’¥éªŒè¯ç­¾å
     *
     * @param publicKey å…¬é’¥
     * @param data åŽŸå§‹æ•°æ®
     * @param signature ç­¾åæ•°æ®
     * @return Boolean ç­¾åæ˜¯å¦æœ‰æ•ˆ
     */
    suspend fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean

    /**
     * åˆ é™¤æŒ‡å®šåˆ«åçš„å¯†é’?     *
     * @param alias å¯†é’¥åˆ«å
     */
    suspend fun deleteKey(alias: String)

    /**
     * æ£€æŸ¥æŒ‡å®šåˆ«åçš„å¯†é’¥æ˜¯å¦å­˜åœ¨
     *
     * @param alias å¯†é’¥åˆ«å
     * @return Boolean å¯†é’¥æ˜¯å¦å­˜åœ¨
     */
    suspend fun keyExists(alias: String): Boolean

    /**
     * å¯¼å‡ºå…¬é’¥ä¸?PEM æ ¼å¼
     *
     * @param alias å¯†é’¥åˆ«å
     * @return String PEM æ ¼å¼çš„å…¬é’¥å­—ç¬¦ä¸²
     */
    suspend fun exportPublicKeyPem(alias: String): String

    /**
     * æ£€æŸ¥è®¾å¤‡æ˜¯å¦æ”¯æŒ?StrongBox
     *
     * @return Boolean æ˜¯å¦æ”¯æŒ StrongBox ç¡¬ä»¶å®‰å…¨æ¨¡å—
     */
    fun isStrongBoxAvailable(): Boolean
}

/**
 * å¯†é’¥æ“ä½œå¼‚å¸¸
 */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * å¯†é’¥æœªæ‰¾åˆ°å¼‚å¸? */
class KeyNotFoundException(message: String) : Exception(message)
