package com.sovexis.domain.crypto

/**
 * Sovexis 代理重加密 (PRE) 服务接口
 *
 * [AI-GENERATED] 陵谦重写版本
 * 生成时间: 2026-05-20
 * 许可证: Apache 2.0
 * 实现状态: ✅ 完整实现（基于 Dart proxy_recrypt 白盒逻辑翻译）
 *
 * 基于椭圆曲线 secp256r1 (P-256) 的代理重加密方案
 * 使用 ECDH + AES-GCM，全程不解密原文，代理不可见明文
 *
 * 安全说明：
 * - 依赖 SpongyCastle (BouncyCastle Android 版本) 进行椭圆曲线运算
 * - 使用 SHA-256 作为简易 KDF
 * - 使用 AES-256-GCM 进行数据加密
 */
interface ProxyReEncryptionService {

    /**
     * 生成 P-256 密钥对
     *
     * @return Result<Keys> 包含公钥（65字节未压缩格式）和私钥（32字节）
     */
    fun generateKeyPair(): Result<Keys>

    /**
     * 使用公钥加密数据
     *
     * @param data 明文数据
     * @param publicKey 接收方公钥（未压缩，65 字节）
     * @return Result<EncryptedMessage> 加密后的消息
     */
    fun encrypt(data: ByteArray, publicKey: ByteArray): Result<EncryptedMessage>

    /**
     * 生成重加密密钥
     *
     * 由授权方（Alice）执行，使用自己的私钥和被授权方（Bob）的公钥生成重加密密钥
     * reKey = sk_A^(-1) * pk_B (mod n)
     *
     * @param fromPrivateKey 授权方私钥（Alice，32字节）
     * @param toPublicKey 被授权方公钥（Bob，未压缩，65字节）
     * @return Result<ReEncryptionKey> 重加密密钥
     */
    fun generateReEncryptionKey(
        fromPrivateKey: ByteArray,
        toPublicKey: ByteArray
    ): Result<ReEncryptionKey>

    /**
     * 代理重加密：将 Alice 的密文转换为 Bob 可解密的密文
     *
     * 代理服务器执行此操作，只需重加密密钥和原始密文
     * 转换后的密文使用转换后的临时公钥，Bob 可以用自己的私钥解密
     * 代理无法获取任何明文信息
     *
     * @param encryptedMessage Alice 的原始密文
     * @param reEncryptionKey 重加密密钥
     * @return Result<EncryptedMessage> 转换后的密文（Bob 可解密）
     */
    fun reEncrypt(
        encryptedMessage: EncryptedMessage,
        reEncryptionKey: ReEncryptionKey
    ): Result<EncryptedMessage>

    /**
     * 解密密文
     *
     * @param encryptedMessage 密文（可以是原始加密或重加密后的）
     * @param privateKey 私钥
     * @return Result<ByteArray> 解密后的明文
     */
    fun decrypt(
        encryptedMessage: EncryptedMessage,
        privateKey: ByteArray
    ): Result<ByteArray>
}
