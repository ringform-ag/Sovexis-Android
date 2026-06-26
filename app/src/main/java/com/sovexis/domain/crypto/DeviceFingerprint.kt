package com.sovexis.domain.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis 设备硬指纹 — Android KeyStore attestation。
 *
 * 用于身份迁移流程中新旧设备的密码学身份证明。
 * KeyStore attestation 证明 ECDH 公钥确实生成于 TEE/StrongBox，
 * 无法被模拟或软件生成。
 *
 * 流程：
 *   1. getOrCreateDeviceKey(alias) → 生成/检索 KeyStore 中的 ECDH P-256 密钥对
 *   2. getDeviceFingerprint(alias)  → SHA-256(attestation chain 中的公钥)
 *   3. exportAttestationChain(alias) → 导出证书链 (传给 Node 验证)
 *
 * @author Sovexis Architecture Team
 * @since 4.0.0 — 身份迁移硬指纹
 */
@Singleton
class DeviceFingerprint @Inject constructor() {

    companion object {
        private const val TAG = "DeviceFingerprint"
        private const val KEY_ALIAS_PREFIX = "sovexis_device_id_"
        private const val EC_CURVE = "secp256r1"
    }

    /**
     * 获取或创建设备身份密钥对。
     * 密钥存放于 Android KeyStore，受 TEE 保护。
     */
    fun getOrCreateDeviceKey(alias: String = "default"): PublicKey {
        val fullAlias = "$KEY_ALIAS_PREFIX$alias"
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)

        if (ks.containsAlias(fullAlias)) {
            val cert = ks.getCertificate(fullAlias)
            if (cert != null) {
                return cert.publicKey
            }
        }

        // 生成新密钥对
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        kpg.initialize(
            KeyGenParameterSpec.Builder(fullAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(ByteArray(32).also { SecureRandom().nextBytes(it) })
                .build()
        )
        val kp = kpg.generateKeyPair()
        return kp.public
    }

    /**
     * 计算设备硬指纹 hash。
     * SHA-256 of the attestation certificate chain's leaf public key.
     */
    fun getDeviceFingerprint(alias: String = "default"): String {
        val fullAlias = "$KEY_ALIAS_PREFIX$alias"
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val cert = ks.getCertificate(fullAlias) as? X509Certificate
                ?: throw IllegalStateException("no attestation certificate for $fullAlias")
            val pubKey = cert.publicKey.encoded
            val hash = MessageDigest.getInstance("SHA-256").digest(pubKey)
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "getDeviceFingerprint failed for $alias", e)
            "INVALID_FINGERPRINT"
        }
    }

    /**
     * 导出 attestation 证书链 (Base64 PEM)。
     * 传给 Node，Node 用它验证设备身份。
     */
    fun exportAttestationChain(alias: String = "default"): String {
        val fullAlias = "$KEY_ALIAS_PREFIX$alias"
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val chain = ks.getCertificateChain(fullAlias)
            chain.joinToString("\n") { cert ->
                "-----BEGIN CERTIFICATE-----\n" +
                Base64.encodeToString(cert.encoded, Base64.DEFAULT) +
                "\n-----END CERTIFICATE-----"
            }
        } catch (e: Exception) {
            Log.e(TAG, "exportAttestationChain failed for $alias", e)
            ""
        }
    }

    /**
     * TEE 签名函数包装 — 供迁移令牌签发使用。
     */
    fun teeSign(alias: String, data: ByteArray): ByteArray {
        val fullAlias = "$KEY_ALIAS_PREFIX$alias"
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val entry = ks.getEntry(fullAlias, null) as KeyStore.PrivateKeyEntry
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(entry.privateKey)
        sig.update(data)
        return sig.sign()
    }
}
