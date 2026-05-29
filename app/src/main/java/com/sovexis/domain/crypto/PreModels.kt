package com.sovexis.domain.crypto

/**
 * Sovexis 代理重加密 (PRE) 数据模型
 *
 * [AI-GENERATED] 陵谦重写版本
 * 生成时间: 2026-05-20
 * 许可证: Apache 2.0
 *
 * 基于椭圆曲线 secp256r1 (P-256) 的代理重加密方案
 * 核心流程：
 * 1. Alice 用自己的公钥加密数据 → 生成 EncryptedMessage
 * 2. Alice 用自己的私钥 + Bob 的公钥生成 ReEncryptionKey
 * 3. 代理服务器用 ReEncryptionKey 转换密文 → Bob 可解密的密文
 * 4. Bob 用自己的私钥解密
 *
 * 全程不解密原文，代理不可见明文。
 */

/**
 * 密钥对
 *
 * @property publicKey 公钥字节（未压缩，65 字节，0x04 || x || y）
 * @property privateKey 私钥字节（32 字节大整数）
 */
data class Keys(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Keys) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

/**
 * 加密消息
 *
 * @property ciphertext AES-GCM 加密后的密文
 * @property iv 初始化向量（12 字节）
 * @property ephemeralPublicKey 临时公钥点（未压缩，65 字节）
 */
data class EncryptedMessage(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val ephemeralPublicKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedMessage) return false
        return ciphertext.contentEquals(other.ciphertext) &&
                iv.contentEquals(other.iv) &&
                ephemeralPublicKey.contentEquals(other.ephemeralPublicKey)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ephemeralPublicKey.contentHashCode()
        return result
    }

    /**
     * 复制并更新临时公钥（用于重加密转换）
     */
    fun withNewEphemeralKey(newEphemeralKey: ByteArray): EncryptedMessage {
        return EncryptedMessage(
            ciphertext = ciphertext,
            iv = iv,
            ephemeralPublicKey = newEphemeralKey
        )
    }
}

/**
 * 重加密密钥
 *
 * @property keyBytes 转换密钥字节（未压缩点坐标，65 字节）
 */
data class ReEncryptionKey(
    val keyBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReEncryptionKey) return false
        return keyBytes.contentEquals(other.keyBytes)
    }

    override fun hashCode(): Int {
        return keyBytes.contentHashCode()
    }
}
