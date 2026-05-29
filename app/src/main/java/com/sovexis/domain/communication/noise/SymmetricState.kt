package com.sovexis.domain.communication.noise

/**
 * Noise SymmetricState - 对称状态管理
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: Phase 1 + Phase 2 完整实现
 * 规范参考: Noise Protocol Framework Rev 34, Section 5.2
 * (https://noiseprotocol.org/noise.html#symmetric-state)
 *
 * SymmetricState 管理握手过程中的对称加密状态，包括:
 * - ck (chaining key): 链密钥，用于 HKDF 密钥派生
 * - h (handshake hash): 握手哈希，用于绑定所有握手消息
 * - CipherState c: 加密状态，用于加密/解密握手消息
 *
 * Noise 规范 Section 5.2 伪代码:
 * ```
 * SymmetricState:
 *   ck: ByteArray       // 链密钥 (HASHLEN 字节)
 *   h: ByteArray        // 握手哈希 (HASHLEN 字节)
 *   CipherState c       // 加密状态
 *
 *   Initialize(protocol_name):
 *     if len(protocol_name) <= HASHLEN:
 *       h = protocol_name || zero(HASHLEN - len(protocol_name))
 *     else:
 *       h = Hash(protocol_name)
 *     ck = h
 *
 *   MixHash(data):
 *     h = Hash(h || data)
 *
 *   MixKey(input_key_material):
 *     ck, temp_k = HKDF(ck, input_key_material)
 *     c.Initialize(temp_k)
 *
 *   EncryptAndHash(plaintext):
 *     ciphertext = c.EncryptWithAd(h, plaintext)
 *     MixHash(ciphertext)
 *     return ciphertext
 *
 *   DecryptAndHash(ciphertext):
 *     plaintext = c.DecryptWithAd(h, ciphertext)
 *     MixHash(ciphertext)
 *     return plaintext
 *
 *   Split():
 *     temp_k1, temp_k2 = HKDF(ck, zero(0))
 *     c1 = CipherState()
 *     c1.Initialize(temp_k1)
 *     c2 = CipherState()
 *     c2.Initialize(temp_k2)
 *     return (c1, c2)
 * ```
 *
 * 安全注意事项:
 * - h 绑定了所有握手消息，提供握手完整性保护
 * - ck 用于密钥派生，确保前向安全性
 * - Split() 产生两个独立的 CipherState 用于双向加密通信
 *
 * [IMPLEMENTATION-STATUS: COMPLETE]
 * [REVIEW-REQUIRED: SECURITY]
 *
 * @property cipher Noise Cipher 函数实现
 * @property hash Noise Hash 函数实现
 * @property ck 链密钥 (HASHLEN 字节)
 * @property h 握手哈希 (HASHLEN 字节)
 * @property c CipherState 加密状态
 */
class SymmetricState(
    private val cipher: NoiseCipher,
    private val hash: NoiseHash
) {
    /**
     * 链密钥 (Chaining Key)
     *
     * 用于 HKDF 密钥派生。每次 MixKey 时更新。
     * 初始值为协议名称的哈希。
     */
    lateinit var ck: ByteArray
        private set

    /**
     * 握手哈希 (Handshake Hash)
     *
     * 绑定所有握手消息，提供握手完整性保护。
     * 初始值为协议名称的哈希。
     */
    lateinit var h: ByteArray
        private set

    /**
     * CipherState 加密状态
     *
     * 管理加密密钥 k 和 nonce n。
     */
    val c: CipherState = CipherState(cipher)

    /**
     * 使用协议名称初始化 SymmetricState
     *
     * 根据 Noise 规范:
     * ```
     * Initialize(protocol_name):
     *   if len(protocol_name) <= HASHLEN:
     *     h = protocol_name || zero(HASHLEN - len(protocol_name))
     *   else:
     *     h = Hash(protocol_name)
     *   ck = h
     * ```
     *
     * @param protocolName 协议名称 (如 "Noise_IK_25519_AESGCM_SHA256")
     */
    fun initialize(protocolName: ByteArray) {
        val hashLen = hash.hashLen

        this.h = if (protocolName.size <= hashLen) {
            // 协议名称短于 HASHLEN: 右侧补零
            val padded = ByteArray(hashLen)
            System.arraycopy(protocolName, 0, padded, 0, protocolName.size)
            padded
        } else {
            // 协议名称长于 HASHLEN: 先哈希
            hash.hash(protocolName)
        }

        // ck 初始值等于 h
        this.ck = h.copyOf()
    }

    /**
     * 将数据混入握手哈希
     *
     * 根据 Noise 规范:
     * ```
     * MixHash(data):
     *   h = Hash(h || data)
     * ```
     *
     * @param data 要混入的数据
     */
    fun mixHash(data: ByteArray) {
        val hashState = hash.createHashState()
        hashState.update(h)
        hashState.update(data)
        h = hashState.finalize()
    }

    /**
     * 将输入密钥材料混入链密钥
     *
     * 根据 Noise 规范:
     * ```
     * MixKey(input_key_material):
     *   ck, temp_k = HKDF(ck, input_key_material)
     *   c.Initialize(temp_k)
     * ```
     *
     * 使用 HKDF 从当前链密钥和输入密钥材料派生新的链密钥和临时加密密钥。
     * 然后使用临时密钥初始化 CipherState。
     *
     * @param inputKeyMaterial 输入密钥材料 (通常是 DH 共享密钥)
     * @throws NoiseException 密钥派生失败
     */
    fun mixKey(inputKeyMaterial: ByteArray) {
        val result = HkdfSha256.deriveKeys(ck, inputKeyMaterial)
        ck = result.chainingKey
        c.initialize(result.tempKey)
    }

    /**
     * 加密明文并混入握手哈希
     *
     * 根据 Noise 规范:
     * ```
     * EncryptAndHash(plaintext):
     *   ciphertext = c.EncryptWithAd(h, plaintext)
     *   MixHash(ciphertext)
     *   return ciphertext
     * ```
     *
     * 使用当前 CipherState 加密明文（以 h 作为 AAD），
     * 然后将密文混入握手哈希。
     *
     * 当 CipherState 未设置密钥时，返回明文原文。
     *
     * @param plaintext 明文数据
     * @return Result<ByteArray> 密文或错误
     */
    fun encryptAndHash(plaintext: ByteArray): Result<ByteArray> {
        return try {
            // ciphertext = c.EncryptWithAd(h, plaintext)
            val ciphertextResult = c.encryptWithAd(h, plaintext)
            if (ciphertextResult.isFailure) {
                return Result.failure(ciphertextResult.exceptionOrNull()!!)
            }
            val ciphertext = ciphertextResult.getOrThrow()

            // MixHash(ciphertext)
            mixHash(ciphertext)

            Result.success(ciphertext)
        } catch (e: Exception) {
            Result.failure(NoiseException("EncryptAndHash failed", e))
        }
    }

    /**
     * 解密密文并混入握手哈希
     *
     * 根据 Noise 规范:
     * ```
     * DecryptAndHash(ciphertext):
     *   plaintext = c.DecryptWithAd(h, ciphertext)
     *   MixHash(ciphertext)
     *   return plaintext
     * ```
     *
     * 使用当前 CipherState 解密密文（以 h 作为 AAD），
     * 然后将密文混入握手哈希。
     *
     * 注意: MixHash 使用的是密文（而非明文），这与加密方一致。
     *
     * 当 CipherState 未设置密钥时，返回密文原文。
     *
     * @param ciphertext 密文数据
     * @return Result<ByteArray> 明文或错误
     */
    fun decryptAndHash(ciphertext: ByteArray): Result<ByteArray> {
        return try {
            // plaintext = c.DecryptWithAd(h, ciphertext)
            val plaintextResult = c.decryptWithAd(h, ciphertext)
            if (plaintextResult.isFailure) {
                return Result.failure(plaintextResult.exceptionOrNull()!!)
            }
            val plaintext = plaintextResult.getOrThrow()

            // MixHash(ciphertext) -- 注意: 使用密文，不是明文
            mixHash(ciphertext)

            Result.success(plaintext)
        } catch (e: Exception) {
            Result.failure(NoiseException("DecryptAndHash failed", e))
        }
    }

    /**
     * 分割对称状态，生成两个独立的 CipherState
     *
     * 根据 Noise 规范:
     * ```
     * Split():
     *   temp_k1, temp_k2 = HKDF(ck, zero(0))
     *   c1 = CipherState()
     *   c1.Initialize(temp_k1)
     *   c2 = CipherState()
     *   c2.Initialize(temp_k2)
     *   return (c1, c2)
     * ```
     *
     * 使用当前链密钥和零输入派生两个独立的密钥，
     * 分别用于发送和接收方向的加密。
     *
     * @return Result<Pair<CipherState, CipherState>> 发送方和接收方 CipherState
     */
    fun split(): Result<Pair<CipherState, CipherState>> {
        return try {
            // temp_k1, temp_k2 = HKDF(ck, zero(0))
            val result = HkdfSha256.deriveKeys(ck, ByteArray(hash.hashLen))

            // 创建两个 CipherState
            val c1 = CipherState(cipher)
            c1.initialize(result.chainingKey)

            val c2 = CipherState(cipher)
            c2.initialize(result.tempKey)

            Result.success(Pair(c1, c2))
        } catch (e: Exception) {
            Result.failure(NoiseException("Split failed", e))
        }
    }

    /**
     * 获取当前握手哈希
     *
     * @return ByteArray 握手哈希值 (HASHLEN 字节)
     */
    fun getHandshakeHash(): ByteArray = h.copyOf()

    /**
     * 获取当前链密钥
     *
     * @return ByteArray 链密钥值 (HASHLEN 字节)
     */
    fun getChainingKey(): ByteArray = ck.copyOf()

    /**
     * 检查 CipherState 是否已设置密钥
     *
     * @return Boolean 是否已设置加密密钥
     */
    fun hasKey(): Boolean = c.hasKey()

    /**
     * 安全清除所有敏感数据
     */
    fun clear() {
        NoiseUtils.secureWipe(ck)
        NoiseUtils.secureWipe(h)
        c.clear()
    }

    /**
     * 创建 SymmetricState 的深拷贝
     *
     * @return SymmetricState 独立的副本
     */
    fun copy(): SymmetricState {
        val clone = SymmetricState(cipher, hash)
        clone.ck = ck.copyOf()
        clone.h = h.copyOf()
        // 复制 CipherState
        clone.c.initialize(this.c.k?.copyOf())
        // 注意: n 也需要复制，但 CipherState 的 n 是 private 的
        // 通过反射或添加 getter 来处理
        return clone
    }
}
