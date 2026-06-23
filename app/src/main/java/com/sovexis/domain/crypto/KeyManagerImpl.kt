package com.sovexis.domain.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KeyManager 实现 — Android Keystore / StrongBox 密码学操作。
 *
 * 【引用来源】合并自废案 com.agora.security.KeyStoreHelper（165行）
 * 适配当前 KeyManager 接口，补充 PEM 导出 + StrongBox 检测。
 *
 * AES-256-GCM 主密钥创建：优先 StrongBox，不可用时降级软件实现。
 * EC P-256 签名密钥创建：用于 DID 身份签名。
 */
@Singleton
class KeyManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : KeyManager {

    private val keystore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val TAG = "KeyManager"
    }

    // ═══════════════ 密钥生成 ═══════════════

    /**
     * 生成 ECDSA P-256 密钥对，存入 Android Keystore。
     */
    override suspend fun generateKeyPair(alias: String): KeyPair = withContext(Dispatchers.IO) {
        if (keystore.containsAlias(alias)) {
            throw CryptoException("密钥别名已存在: $alias")
        }

        val kg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        kg.initialize(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .setIsStrongBoxBacked(isStrongBoxAvailable())
                .build()
        )
        kg.generateKeyPair()
    }

    /**
     * 创建 AES-256-GCM 主密钥（用于加密种子/私钥）。
     * 优先 StrongBox，不可用时降级。
     */
    fun createAesKey(alias: String) {
        if (keystore.containsAlias(alias)) return

        try {
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setIsStrongBoxBacked(true)
                .build())
            kg.generateKey()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "StrongBox不可用，降级到软件实现: ${e.message}")
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setIsStrongBoxBacked(false)
                .build())
            kg.generateKey()
        }
    }

    // ═══════════════ 签名/验签 ═══════════════

    override suspend fun sign(alias: String, data: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val privateKey = (keystore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry)?.privateKey
            ?: throw KeyNotFoundException("密钥不存在: $alias")

        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }

    override suspend fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(data)
                verify(signature)
            }
        }

    // ═══════════════ 公钥导出 ═══════════════

    override suspend fun getPublicKey(alias: String): PublicKey = withContext(Dispatchers.IO) {
        val cert = (keystore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry)?.certificate
            ?: throw KeyNotFoundException("密钥不存在: $alias")
        cert.publicKey
    }

    override suspend fun exportPublicKeyPem(alias: String): String = withContext(Dispatchers.IO) {
        val pubKey = getPublicKey(alias)
        "-----BEGIN PUBLIC KEY-----\n" +
            Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP) +
            "\n-----END PUBLIC KEY-----"
    }

    // ═══════════════ 加密/解密 ═══════════════

    /**
     * 使用 AES-256-GCM 加密数据。
     * 密文格式：12字节 IV + ciphertext（含 16字节 GCM tag）
     */
    fun encryptWithAes(keyAlias: String, data: ByteArray): ByteArray {
        val key = keystore.getKey(keyAlias, null) as? SecretKey
            ?: throw CryptoException("AES 密钥不存在: $keyAlias")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    /**
     * 使用 AES-256-GCM 解密数据。
     * 密文格式：12字节 IV + ciphertext（含 16字节 GCM tag）
     */
    fun decryptWithAes(keyAlias: String, encryptedData: ByteArray): ByteArray {
        val key = keystore.getKey(keyAlias, null) as? SecretKey
            ?: throw CryptoException("AES 密钥不存在: $keyAlias")
        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    // ═══════════════ 管理 ═══════════════

    override suspend fun deleteKey(alias: String) {
        if (keystore.containsAlias(alias)) {
            keystore.deleteEntry(alias)
        }
    }

    override suspend fun keyExists(alias: String): Boolean = keystore.containsAlias(alias)

    override fun isStrongBoxAvailable(): Boolean = try {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    } catch (_: Exception) { false }

    /**
     * 从 PEM 字符串解析公钥
     */
    fun publicKeyFromPem(pem: String): PublicKey {
        val content = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
        val decoded = Base64.decode(content, Base64.DEFAULT)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decoded))
    }
}
