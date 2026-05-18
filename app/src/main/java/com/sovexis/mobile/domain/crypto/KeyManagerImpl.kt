package com.sovexis.mobile.domain.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.KeyFactory
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis å¯†é’¥ç®¡ç†å®žçŽ°
 *
 * ã€å¼•ç”¨æ¥æºã€‘åŸºäºŽåºŸæ¡?IdentityManager.kt å¯†é’¥æ“ä½œé€»è¾‘
 * - BIP-32 æ´¾ç”Ÿï¼šåºŸæ¡ˆç¬¬ 323-411 è¡? * - ECDSA P-256 å¯†é’¥ç”Ÿæˆï¼šåºŸæ¡ˆç¬¬ 356-374 è¡? *
 * ã€è°ƒæ•´è¯´æ˜Žã€? * 1. é€‚é… Hilt ä¾èµ–æ³¨å…¥
 * 2. æ·»åŠ  StrongBox ä¼˜å…ˆç­–ç•¥
 * 3. ç»Ÿä¸€å¼‚å¸¸å¤„ç†
 *
 * @author Sovexis æž¶æž„ç»? * @since 3.0.0
 */
@Singleton
class KeyManagerImpl @Inject constructor(
    private val context: Context
) : KeyManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_EC
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val CURVE_NAME = "secp256r1" // P-256
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /**
     * ç”Ÿæˆæ–°çš„ ECDSA P-256 å¯†é’¥å¯?     *
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?IdentityManager.kt ç¬?110-167 è¡Œï¼ˆå¯†é’¥ç”Ÿæˆéƒ¨åˆ†ï¼?     * ã€è°ƒæ•´ã€‘ä¼˜å…ˆä½¿ç”?StrongBoxï¼Œå›žé€€åˆ?TEE
     *
     * @param alias å¯†é’¥åˆ«å
     * @return KeyPair ç”Ÿæˆçš„å¯†é’¥å¯¹
     * @throws CryptoException å¯†é’¥ç”Ÿæˆå¤±è´¥
     */
    override suspend fun generateKeyPair(alias: String): KeyPair {
        return try {
            // æ£€æŸ¥å¯†é’¥æ˜¯å¦å·²å­˜åœ¨
            if (keyStore.containsAlias(alias)) {
                throw CryptoException("å¯†é’¥å·²å­˜åœ? $alias")
            }

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KEY_ALGORITHM,
                ANDROID_KEYSTORE
            )

            // æž„å»º KeyGenParameterSpec
            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_NAME))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)

            // ä¼˜å…ˆå°è¯• StrongBox
            if (isStrongBoxAvailable()) {
                try {
                    builder.setIsStrongBoxBacked(true)
                    keyPairGenerator.initialize(builder.build())
                    return keyPairGenerator.generateKeyPair()
                } catch (e: StrongBoxUnavailableException) {
                    // StrongBox ä¸å¯ç”¨ï¼Œå›žé€€åˆ?TEE
                    builder.setIsStrongBoxBacked(false)
                }
            }

            // ä½¿ç”¨ TEE
            keyPairGenerator.initialize(builder.build())
            keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            throw CryptoException("å¯†é’¥ç”Ÿæˆå¤±è´¥: ${e.message}", e)
        }
    }

    /**
     * èŽ·å–æŒ‡å®šåˆ«åçš„å…¬é’?     *
     * @param alias å¯†é’¥åˆ«å
     * @return PublicKey å…¬é’¥
     * @throws KeyNotFoundException å¯†é’¥ä¸å­˜åœ?     */
    override suspend fun getPublicKey(alias: String): PublicKey {
        val entry = keyStore.getEntry(alias, null)
            ?: throw KeyNotFoundException("å¯†é’¥ä¸å­˜åœ? $alias")
        return (entry as KeyStore.PrivateKeyEntry).certificate.publicKey
    }

    /**
     * ä½¿ç”¨ç§é’¥å¯¹æ•°æ®è¿›è¡Œç­¾å?     *
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?IdentityManager.kt ç¬?261-265 è¡?     *
     * @param alias å¯†é’¥åˆ«å
     * @param data å¾…ç­¾åæ•°æ?     * @return ByteArray ç­¾åç»“æžœ
     * @throws CryptoException ç­¾åå¤±è´¥
     */
    override suspend fun sign(alias: String, data: ByteArray): ByteArray {
        return try {
            val entry = keyStore.getEntry(alias, null)
                ?: throw KeyNotFoundException("å¯†é’¥ä¸å­˜åœ? $alias")

            val privateKey = (entry as KeyStore.PrivateKeyEntry).privateKey
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)
            signature.update(data)
            signature.sign()
        } catch (e: Exception) {
            throw CryptoException("ç­¾åå¤±è´¥: ${e.message}", e)
        }
    }

    /**
     * ä½¿ç”¨å…¬é’¥éªŒè¯ç­¾å
     *
     * @param publicKey å…¬é’¥
     * @param data åŽŸå§‹æ•°æ®
     * @param signature ç­¾åæ•°æ®
     * @return Boolean ç­¾åæ˜¯å¦æœ‰æ•ˆ
     */
    override suspend fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * åˆ é™¤æŒ‡å®šåˆ«åçš„å¯†é’?     *
     * @param alias å¯†é’¥åˆ«å
     */
    override suspend fun deleteKey(alias: String) {
        keyStore.deleteEntry(alias)
    }

    /**
     * æ£€æŸ¥æŒ‡å®šåˆ«åçš„å¯†é’¥æ˜¯å¦å­˜åœ¨
     *
     * @param alias å¯†é’¥åˆ«å
     * @return Boolean å¯†é’¥æ˜¯å¦å­˜åœ¨
     */
    override suspend fun keyExists(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    /**
     * å¯¼å‡ºå…¬é’¥ä¸?PEM æ ¼å¼
     *
     * @param alias å¯†é’¥åˆ«å
     * @return String PEM æ ¼å¼çš„å…¬é’¥å­—ç¬¦ä¸²
     */
    override suspend fun exportPublicKeyPem(alias: String): String {
        val publicKey = getPublicKey(alias)
        val encoded = publicKey.encoded
        val base64 = Base64.encodeToString(encoded, Base64.DEFAULT)

        return buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            // æ¯è¡Œ 64 å­—ç¬¦
            base64.chunked(64).forEach { appendLine(it) }
            appendLine("-----END PUBLIC KEY-----")
        }
    }

    /**
     * æ£€æŸ¥è®¾å¤‡æ˜¯å¦æ”¯æŒ?StrongBox
     *
     * @return Boolean æ˜¯å¦æ”¯æŒ StrongBox ç¡¬ä»¶å®‰å…¨æ¨¡å—
     */
    override fun isStrongBoxAvailable(): Boolean {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KEY_ALGORITHM,
                ANDROID_KEYSTORE
            )
            val builder = KeyGenParameterSpec.Builder(
                "test_strongbox",
                KeyProperties.PURPOSE_SIGN
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_NAME))
                .setIsStrongBoxBacked(true)

            keyPairGenerator.initialize(builder.build())
            // å°è¯•ç”Ÿæˆæµ‹è¯•å¯†é’¥
            val testKey = keyPairGenerator.generateKeyPair()
            // æ¸…ç†æµ‹è¯•å¯†é’¥
            keyStore.deleteEntry("test_strongbox")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ä»Žå­—èŠ‚æ•°ç»„æ¢å¤ç§é’?     *
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?IdentityManager.kt ç¬?356-361 è¡?     *
     * @param privateKeyBytes PKCS8 ç¼–ç çš„ç§é’¥å­—èŠ?     * @return PrivateKey ç§é’¥å¯¹è±¡
     */
    fun privateKeyFromBytes(privateKeyBytes: ByteArray): PrivateKey {
        val spec = ECNamedCurveTable.getParameterSpec(CURVE_NAME)
        val privateKeySpec = ECPrivateKeySpec(BigInteger(1, privateKeyBytes), spec)
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        return keyFactory.generatePrivate(privateKeySpec)
    }

    /**
     * ä»Žç§é’¥æ´¾ç”Ÿå…¬é’?     *
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?IdentityManager.kt ç¬?366-374 è¡?     *
     * @param privateKey ç§é’¥
     * @return PublicKey å…¬é’¥
     */
    fun derivePublicKeyFromPrivate(privateKey: PrivateKey): PublicKey {
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        val privateKeySpec = keyFactory.getKeySpec(privateKey, ECPrivateKeySpec::class.java)
        val spec = ECNamedCurveTable.getParameterSpec(CURVE_NAME)
        val curve = spec.curve
        val point = curve.createPoint(BigInteger.ZERO, BigInteger.ONE).multiply(privateKeySpec.d)
        val publicKeySpec = org.bouncycastle.jce.spec.ECPublicKeySpec(point, spec)
        return keyFactory.generatePublic(publicKeySpec)
    }

    /**
     * ç”Ÿæˆ AES-GCM å¯†é’¥ç”¨äºŽæ•°æ®åŠ å¯†
     *
     * @param alias å¯†é’¥åˆ«å
     * @return SecretKey ç”Ÿæˆçš„å¯†é’?     */
    suspend fun generateAesKey(alias: String): SecretKey {
        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            if (isStrongBoxAvailable()) {
                try {
                    builder.setIsStrongBoxBacked(true)
                } catch (_: StrongBoxUnavailableException) {
                    builder.setIsStrongBoxBacked(false)
                }
            }

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        } catch (e: Exception) {
            throw CryptoException("AES å¯†é’¥ç”Ÿæˆå¤±è´¥: ${e.message}", e)
        }
    }

    /**
     * ä½¿ç”¨ AES-GCM åŠ å¯†æ•°æ®
     *
     * @param alias å¯†é’¥åˆ«å
     * @param plaintext æ˜Žæ–‡æ•°æ®
     * @return Pair<ByteArray, ByteArray> (å¯†æ–‡, IV)
     */
    suspend fun encryptWithAes(alias: String, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val entry = keyStore.getEntry(alias, null)
            ?: throw KeyNotFoundException("å¯†é’¥ä¸å­˜åœ? $alias")

        val secretKey = (entry as KeyStore.SecretKeyEntry).secretKey
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv

        return Pair(ciphertext, iv)
    }

    /**
     * ä½¿ç”¨ AES-GCM è§£å¯†æ•°æ®
     *
     * @param alias å¯†é’¥åˆ«å
     * @param ciphertext å¯†æ–‡æ•°æ®
     * @param iv åˆå§‹åŒ–å‘é‡?     * @return ByteArray æ˜Žæ–‡æ•°æ®
     */
    suspend fun decryptWithAes(alias: String, ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val entry = keyStore.getEntry(alias, null)
            ?: throw KeyNotFoundException("å¯†é’¥ä¸å­˜åœ? $alias")

        val secretKey = (entry as KeyStore.SecretKeyEntry).secretKey
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(ciphertext)
    }
}
