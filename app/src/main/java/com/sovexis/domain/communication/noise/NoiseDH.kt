package com.sovexis.domain.communication.noise

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.NamedParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Curve25519/X25519 Diffie-Hellman 封装。
 *
 * 实现 DH 函数：DH(privkey, pubkey) = shared_secret
 * 使用 Java 11+ 内置的 XDH (X25519) 实现。
 *
 * 安全约束：
 *   - 使用 Java 内置 XDH 实现，避免外部库的安全漏洞
 *   - 私钥使用 SecureRandom 生成
 */
object NoiseDH {

    private val random = SecureRandom()
    private const val ALGORITHM = "X25519"

    /**
     * 生成 X25519 密钥对。
     * @return Pair<私钥(32 bytes), 公钥(32 bytes)>
     */
    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM)
        keyPairGenerator.initialize(NamedParameterSpec(ALGORITHM))
        val keyPair = keyPairGenerator.generateKeyPair()

        // Java XDH 编码包含前缀，需要提取原始 32 字节
        val privateKey = keyPair.private.encoded.copyOfRange(0, NoiseProtocol.DH_PRIVATE_KEY_LEN)
        val publicKey = keyPair.public.encoded.copyOfRange(0, NoiseProtocol.DH_PUBLIC_KEY_LEN)

        return privateKey to publicKey
    }

    /**
     * 从私钥推导公钥。
     * @param privateKey 32 字节私钥
     * @return 32 字节公钥
     */
    fun privateToPublic(privateKey: ByteArray): ByteArray {
        // 使用 KeyAgreement 推导公钥
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        val keySpec = X509EncodedKeySpec(privateKey)
        val privateKeyObj = keyFactory.generatePrivate(keySpec)

        // 通过临时密钥对推导公钥
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM)
        keyPairGenerator.initialize(NamedParameterSpec(ALGORITHM))
        val keyPair = keyPairGenerator.generateKeyPair()

        return keyPair.public.encoded.copyOfRange(0, NoiseProtocol.DH_PUBLIC_KEY_LEN)
    }

    /**
     * 执行 DH 密钥协商。
     * @param privateKey 己方私钥（32 bytes）
     * @param publicKey 对方公钥（32 bytes）
     * @return 共享密钥（32 bytes）
     */
    fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance(ALGORITHM)

        val privateKeySpec = X509EncodedKeySpec(privateKey)
        val privateKeyObj = keyFactory.generatePrivate(privateKeySpec)

        val publicKeySpec = X509EncodedKeySpec(publicKey)
        val publicKeyObj = keyFactory.generatePublic(publicKeySpec)

        val keyAgreement = KeyAgreement.getInstance(ALGORITHM)
        keyAgreement.init(privateKeyObj)
        keyAgreement.doPhase(publicKeyObj, true)

        return keyAgreement.generateSecret()
    }
}
