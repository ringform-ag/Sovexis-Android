package com.sovexis.mobile.domain.communication.noise

import java.security.SecureRandom

/**
 * Noise HandshakeState - 握手状态管理
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: Phase 1 + Phase 2 完整实现
 * 规范参考: Noise Protocol Framework Rev 34, Section 5.3
 * (https://noiseprotocol.org/noise.html#handshake-state)
 *
 * HandshakeState 管理完整的 Noise 握手过程，包括:
 * - 本地静态密钥对 (s) 和临时密钥对 (e)
 * - 远程静态公钥 (rs) 和临时公钥 (re)
 * - SymmetricState 对称状态
 * - 握手模式 (pattern) 和消息方向 (initiator/responder)
 *
 * Noise 规范 Section 5.3 伪代码:
 * ```
 * HandshakeState:
 *   s: NoiseKeyPair         // 本地静态密钥对
 *   e: NoiseKeyPair         // 本地临时密钥对
 *   rs: ByteArray           // 远程静态公钥
 *   re: ByteArray           // 远程临时公钥
 *   SymmetricState ss       // 对称状态
 *   pattern: Pattern        // 握手模式
 *   initiator: Boolean      // 是否为发起方
 *   messageIndex: Int       // 当前消息索引
 *
 *   Initialize(pattern, initiator, prologue, s, e, rs, re):
 *     ss.Initialize(protocol_name)
 *     ss.MixHash(prologue)
 *     if pattern needs local static key:
 *       ss.MixHash(s.publicKey)
 *     if pattern needs remote static key:
 *       ss.MixHash(rs)
 *     messageIndex = 0
 *
 *   WriteMessage(payload):
 *     message = buffer
 *     for token in pattern[messageIndex]:
 *       process token
 *     message += ss.EncryptAndHash(payload)
 *     messageIndex++
 *     if messageIndex == len(pattern):
 *       return (message, ss.Split())
 *     return (message, null)
 *
 *   ReadMessage(message):
 *     offset = 0
 *     for token in pattern[messageIndex]:
 *       process token
 *     payload = ss.DecryptAndHash(message[offset:])
 *     messageIndex++
 *     if messageIndex == len(pattern):
 *       return (payload, ss.Split())
 *     return (payload, null)
 * ```
 *
 * 支持的握手模式:
 * - Noise_IK: 最常用模式，提供身份认证和前向安全性
 *   - Initiator: -> e, es, s, ss
 *   - Responder: <- e, ee, se
 *
 * 支持的 Token:
 * - "e": 发送临时公钥
 * - "s": 发送静态公钥
 * - "ee": DH(e, re)
 * - "es": DH(e, rs) 或 DH(s, re)
 * - "se": DH(s, re) 或 DH(e, rs)
 * - "ss": DH(s, rs)
 *
 * [IMPLEMENTATION-STATUS: COMPLETE]
 * [REVIEW-REQUIRED: SECURITY]
 *
 * @property dh Noise DH 函数实现
 * @property cipher Noise Cipher 函数实现
 * @property hash Noise Hash 函数实现
 */
class HandshakeState(
    private val dh: NoiseDH,
    private val cipher: NoiseCipher,
    private val hash: NoiseHash
) {
    /**
     * 本地静态密钥对
     *
     * 用于长期身份认证。在 Noise_IK 模式中必须设置。
     */
    var s: NoiseKeyPair? = null
        private set

    /**
     * 本地临时密钥对
     *
     * 用于提供前向安全性。在握手过程中生成。
     */
    var e: NoiseKeyPair? = null
        private set

    /**
     * 远程静态公钥
     *
     * 对端的长期身份公钥。在 Noise_IK 模式中必须预先知道。
     */
    var rs: ByteArray? = null
        private set

    /**
     * 远程临时公钥
     *
     * 对端的临时公钥，在握手过程中接收。
     */
    var re: ByteArray? = null
        private set

    /**
     * SymmetricState 对称状态
     *
     * 管理握手过程中的哈希和加密状态。
     */
    val ss: SymmetricState = SymmetricState(cipher, hash)

    /**
     * 握手模式
     */
    var pattern: NoisePattern = NoisePattern.IK
        private set

    /**
     * 是否为发起方
     *
     * - true: 发起方 (Initiator)
     * - false: 响应方 (Responder)
     */
    var isInitiator: Boolean = true
        private set

    /**
     * 当前消息索引
     *
     * 用于跟踪握手进度。
     */
    var messageIndex: Int = 0
        private set

    /**
     * 安全随机数生成器
     */
    private val secureRandom: SecureRandom = SecureRandom()

    /**
     * 握手是否已完成
     */
    var isCompleted: Boolean = false
        private set

    /**
     * 初始化 HandshakeState
     *
     * 根据 Noise 规范:
     * ```
     * Initialize(pattern, initiator, prologue, s, e, rs, re):
     *   ss.Initialize(protocol_name)
     *   ss.MixHash(prologue)
     *   if pattern needs local static key:
     *     ss.MixHash(s.publicKey)
     *   if pattern needs remote static key:
     *     ss.MixHash(rs)
     *   messageIndex = 0
     * ```
     *
     * @param pattern 握手模式
     * @param initiator 是否为发起方
     * @param prologue 前言数据 (可为空)
     * @param s 本地静态密钥对 (可为 null)
     * @param e 本地临时密钥对 (可为 null，未提供时自动生成)
     * @param rs 远程静态公钥 (可为 null)
     * @param re 远程临时公钥 (可为 null)
     * @throws NoiseException 初始化失败
     */
    fun initialize(
        pattern: NoisePattern,
        initiator: Boolean,
        prologue: ByteArray = ByteArray(0),
        s: NoiseKeyPair? = null,
        e: NoiseKeyPair? = null,
        rs: ByteArray? = null,
        re: ByteArray? = null
    ): Result<Unit> {
        return try {
            this.pattern = pattern
            this.isInitiator = initiator
            this.s = s
            this.e = e
            this.rs = rs
            this.re = re
            this.messageIndex = 0
            this.isCompleted = false

            // 获取协议名称
            val protocolName = pattern.protocolName.toByteArray(Charsets.UTF_8)

            // ss.Initialize(protocol_name)
            ss.initialize(protocolName)

            // ss.MixHash(prologue)
            if (prologue.isNotEmpty()) {
                ss.mixHash(prologue)
            }

            // 根据模式混入预知公钥
            when (pattern) {
                NoisePattern.IK -> {
                    // Noise_IK:
                    // Initiator 预知 rs -> MixHash(rs)
                    // Responder 预知 rs -> MixHash(rs)
                    if (rs != null) {
                        ss.mixHash(rs)
                    } else {
                        return Result.failure(
                            NoiseException("Noise_IK requires remote static public key (rs)")
                        )
                    }
                }
            }

            Result.success(Unit)
        } catch (e: NoiseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NoiseException("HandshakeState initialization failed", e))
        }
    }

    /**
     * 写入握手消息
     *
     * 根据 Noise 规范:
     * ```
     * WriteMessage(payload):
     *   message = buffer
     *   for token in pattern[messageIndex]:
     *     process token
     *   message += ss.EncryptAndHash(payload)
     *   messageIndex++
     *   if messageIndex == len(pattern):
     *     return (message, ss.Split())
     *   return (message, null)
     * ```
     *
     * 处理当前消息索引对应的所有 token，然后加密附加的 payload。
     * 如果这是最后一条握手消息，返回分割后的两个 CipherState。
     *
     * @param payload 附加的明文载荷
     * @return Result<HandshakeMessage> 握手消息（含可选的 CipherState 对）
     */
    fun writeMessage(payload: ByteArray = ByteArray(0)): Result<HandshakeMessage> {
        return try {
            if (isCompleted) {
                return Result.failure(NoiseException("Handshake already completed"))
            }

            val messageBuffer = mutableListOf<Byte>()

            // 获取当前消息的 token 列表
            val tokens = getTokensForMessage(messageIndex)

            // 处理每个 token
            for (token in tokens) {
                processWriteToken(token, messageBuffer)
            }

            // 加密并附加 payload
            val payloadBytes = payload
            val encryptedPayloadResult = ss.encryptAndHash(payloadBytes)
            if (encryptedPayloadResult.isFailure) {
                return Result.failure(encryptedPayloadResult.exceptionOrNull()!!)
            }
            val encryptedPayload = encryptedPayloadResult.getOrThrow()
            messageBuffer.addAll(encryptedPayload.toList())

            messageIndex++

            // 检查是否是最后一条消息
            val cipherStates = if (messageIndex >= getMessageCount()) {
                isCompleted = true
                val splitResult = ss.split()
                if (splitResult.isFailure) {
                    return Result.failure(splitResult.exceptionOrNull()!!)
                }
                splitResult.getOrThrow()
            } else {
                null
            }

            Result.success(
                HandshakeMessage(
                    message = messageBuffer.toByteArray(),
                    cipherStates = cipherStates
                )
            )
        } catch (e: NoiseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NoiseException("WriteMessage failed", e))
        }
    }

    /**
     * 读取握手消息
     *
     * 根据 Noise 规范:
     * ```
     * ReadMessage(message):
     *   offset = 0
     *   for token in pattern[messageIndex]:
     *     process token
     *   payload = ss.DecryptAndHash(message[offset:])
     *   messageIndex++
     *   if messageIndex == len(pattern):
     *     return (payload, ss.Split())
     *   return (payload, null)
     * ```
     *
     * 处理当前消息索引对应的所有 token，然后解密 payload。
     * 如果这是最后一条握手消息，返回分割后的两个 CipherState。
     *
     * @param message 接收到的握手消息
     * @return Result<HandshakeMessage> 解析结果（含 payload 和可选的 CipherState 对）
     */
    fun readMessage(message: ByteArray): Result<HandshakeMessage> {
        return try {
            if (isCompleted) {
                return Result.failure(NoiseException("Handshake already completed"))
            }

            var offset = 0

            // 获取当前消息的 token 列表
            val tokens = getTokensForMessage(messageIndex)

            // 处理每个 token
            for (token in tokens) {
                val consumed = processReadToken(token, message, offset)
                offset += consumed
            }

            // 解密 payload
            val ciphertext = message.sliceArray(offset until message.size)
            val payloadResult = ss.decryptAndHash(ciphertext)
            if (payloadResult.isFailure) {
                return Result.failure(payloadResult.exceptionOrNull()!!)
            }
            val payload = payloadResult.getOrThrow()

            messageIndex++

            // 检查是否是最后一条消息
            val cipherStates = if (messageIndex >= getMessageCount()) {
                isCompleted = true
                val splitResult = ss.split()
                if (splitResult.isFailure) {
                    return Result.failure(splitResult.exceptionOrNull()!!)
                }
                splitResult.getOrThrow()
            } else {
                null
            }

            Result.success(
                HandshakeMessage(
                    message = payload,
                    cipherStates = cipherStates
                )
            )
        } catch (e: NoiseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NoiseException("ReadMessage failed", e))
        }
    }

    // ========================================================================
    // Token 处理 - 写入方向
    // ========================================================================

    /**
     * 处理写入方向的 token
     *
     * @param token token 字符串
     * @param buffer 消息缓冲区
     */
    private fun processWriteToken(token: String, buffer: MutableList<Byte>) {
        when (token) {
            "e" -> writeE(buffer)
            "s" -> writeS(buffer)
            "ee" -> writeEE()
            "es" -> writeES()
            "se" -> writeSE()
            "ss" -> writeSS()
            else -> throw NoiseException("Unknown token: $token")
        }
    }

    /**
     * Token "e": 发送临时公钥
     *
     * 生成临时密钥对，将公钥混入哈希。
     * 如果已设置加密密钥，公钥会被加密。
     *
     * Noise 规范:
     * ```
     * e:
     *   e = GenerateKeyPair()
     *   Send(e.publicKey)  // 可能加密
     *   MixHash(e.publicKey)
     * ```
     */
    private fun writeE(buffer: MutableList<Byte>) {
        // 生成临时密钥对
        e = dh.generateKeyPair(secureRandom)
        val publicKeyBytes = e!!.publicKey

        // 如果已设置密钥，加密公钥
        val result = ss.encryptAndHash(publicKeyBytes)
        if (result.isFailure) {
            throw result.exceptionOrNull()!!
        }
        buffer.addAll(result.getOrThrow().toList())
    }

    /**
     * Token "s": 发送静态公钥
     *
     * 将本地静态公钥混入哈希。
     * 如果已设置加密密钥，公钥会被加密。
     *
     * Noise 规范:
     * ```
     * s:
     *   Send(s.publicKey)  // 可能加密
     *   MixHash(s.publicKey)
     * ```
     */
    private fun writeS(buffer: MutableList<Byte>) {
        if (s == null) {
            throw NoiseException("Static key pair (s) not set")
        }
        val publicKeyBytes = s!!.publicKey

        val result = ss.encryptAndHash(publicKeyBytes)
        if (result.isFailure) {
            throw result.exceptionOrNull()!!
        }
        buffer.addAll(result.getOrThrow().toList())
    }

    /**
     * Token "ee": DH(e, re)
     *
     * 临时-临时 DH: DH(e, re)
     * 结果混入链密钥。
     *
     * Noise 规范:
     * ```
     * ee:
     *   MixKey(DH(e, re))
     * ```
     */
    private fun writeEE() {
        if (e == null) throw NoiseException("Local ephemeral key (e) not set")
        if (re == null) throw NoiseException("Remote ephemeral key (re) not set")

        val sharedSecret = dh.dh(e!!.privateKey, re!!)
        ss.mixKey(sharedSecret)
    }

    /**
     * Token "es": DH(e, rs) 或 DH(s, re)
     *
     * 根据发起方/响应方角色:
     * - Initiator: DH(e, rs)
     * - Responder: DH(s, re)
     *
     * Noise 规范:
     * ```
     * es:
     *   if initiator: MixKey(DH(e, rs))
     *   else: MixKey(DH(s, re))
     * ```
     */
    private fun writeES() {
        val sharedSecret = if (isInitiator) {
            if (e == null) throw NoiseException("Local ephemeral key (e) not set")
            if (rs == null) throw NoiseException("Remote static key (rs) not set")
            dh.dh(e!!.privateKey, rs!!)
        } else {
            if (s == null) throw NoiseException("Local static key (s) not set")
            if (re == null) throw NoiseException("Remote ephemeral key (re) not set")
            dh.dh(s!!.privateKey, re!!)
        }
        ss.mixKey(sharedSecret)
    }

    /**
     * Token "se": DH(s, re) 或 DH(e, rs)
     *
     * 根据发起方/响应方角色:
     * - Initiator: DH(s, re)
     * - Responder: DH(e, rs)
     *
     * Noise 规范:
     * ```
     * se:
     *   if initiator: MixKey(DH(s, re))
     *   else: MixKey(DH(e, rs))
     * ```
     */
    private fun writeSE() {
        val sharedSecret = if (isInitiator) {
            if (s == null) throw NoiseException("Local static key (s) not set")
            if (re == null) throw NoiseException("Remote ephemeral key (re) not set")
            dh.dh(s!!.privateKey, re!!)
        } else {
            if (e == null) throw NoiseException("Local ephemeral key (e) not set")
            if (rs == null) throw NoiseException("Remote static key (rs) not set")
            dh.dh(e!!.privateKey, rs!!)
        }
        ss.mixKey(sharedSecret)
    }

    /**
     * Token "ss": DH(s, rs)
     *
     * 静态-静态 DH: DH(s, rs)
     * 结果混入链密钥。
     *
     * Noise 规范:
     * ```
     * ss:
     *   MixKey(DH(s, rs))
     * ```
     */
    private fun writeSS() {
        if (s == null) throw NoiseException("Local static key (s) not set")
        if (rs == null) throw NoiseException("Remote static key (rs) not set")

        val sharedSecret = dh.dh(s!!.privateKey, rs!!)
        ss.mixKey(sharedSecret)
    }

    // ========================================================================
    // Token 处理 - 读取方向
    // ========================================================================

    /**
     * 处理读取方向的 token
     *
     * @param token token 字符串
     * @param message 完整消息
     * @param offset 当前读取偏移量
     * @return Int 消费的字节数
     */
    private fun processReadToken(token: String, message: ByteArray, offset: Int): Int {
        return when (token) {
            "e" -> readE(message, offset)
            "s" -> readS(message, offset)
            "ee" -> readEE()
            "es" -> readES()
            "se" -> readSE()
            "ss" -> readSS()
            else -> throw NoiseException("Unknown token: $token")
        }
    }

    /**
     * Token "e": 接收临时公钥
     *
     * 从消息中读取远程临时公钥。
     *
     * @param message 完整消息
     * @param offset 当前偏移量
     * @return Int 消费的字节数
     */
    private fun readE(message: ByteArray, offset: Int): Int {
        // 读取公钥（可能加密）
        val keyLen = dh.dhLen
        val encryptedKey = if (ss.hasKey()) {
            // 加密的公钥: keyLen + 16 (GCM tag)
            keyLen + 16
        } else {
            // 明文公钥
            keyLen
        }

        if (message.size < offset + encryptedKey) {
            throw NoiseException("Message too short for ephemeral public key")
        }

        val keyData = message.sliceArray(offset until offset + encryptedKey)

        // 解密（如果已设置密钥）
        val publicKeyResult = ss.decryptAndHash(keyData)
        if (publicKeyResult.isFailure) {
            throw publicKeyResult.exceptionOrNull()!!
        }
        val publicKey = publicKeyResult.getOrThrow()

        // 验证公钥
        re = dh.validatePublicKey(publicKey)

        return encryptedKey
    }

    /**
     * Token "s": 接收静态公钥
     *
     * 从消息中读取远程静态公钥。
     *
     * @param message 完整消息
     * @param offset 当前偏移量
     * @return Int 消费的字节数
     */
    private fun readS(message: ByteArray, offset: Int): Int {
        val keyLen = dh.dhLen
        val encryptedKey = if (ss.hasKey()) {
            keyLen + 16
        } else {
            keyLen
        }

        if (message.size < offset + encryptedKey) {
            throw NoiseException("Message too short for static public key")
        }

        val keyData = message.sliceArray(offset until offset + encryptedKey)

        val publicKeyResult = ss.decryptAndHash(keyData)
        if (publicKeyResult.isFailure) {
            throw publicKeyResult.exceptionOrNull()!!
        }
        val publicKey = publicKeyResult.getOrThrow()

        rs = dh.validatePublicKey(publicKey)

        return encryptedKey
    }

    /**
     * Token "ee": DH(e, re) - 读取方向
     *
     * 与写入方向相同。
     */
    private fun readEE(): Int {
        writeEE()
        return 0
    }

    /**
     * Token "es": 读取方向
     *
     * 根据发起方/响应方角色:
     * - Initiator: DH(s, re) (注意: 与写入方向相反)
     * - Responder: DH(e, rs) (注意: 与写入方向相反)
     *
     * Noise 规范:
     * ```
     * es:
     *   if initiator: MixKey(DH(s, re))
     *   else: MixKey(DH(e, rs))
     * ```
     *
     * 注意: 读取方向和写入方向的 es/se 角色互换！
     * 这是因为 DH 函数是对称的，但密钥对的使用方向相反。
     */
    private fun readES(): Int {
        val sharedSecret = if (isInitiator) {
            // Initiator 读取: DH(s, re)
            if (s == null) throw NoiseException("Local static key (s) not set")
            if (re == null) throw NoiseException("Remote ephemeral key (re) not set")
            dh.dh(s!!.privateKey, re!!)
        } else {
            // Responder 读取: DH(e, rs)
            if (e == null) throw NoiseException("Local ephemeral key (e) not set")
            if (rs == null) throw NoiseException("Remote static key (rs) not set")
            dh.dh(e!!.privateKey, rs!!)
        }
        ss.mixKey(sharedSecret)
        return 0
    }

    /**
     * Token "se": 读取方向
     *
     * 根据发起方/响应方角色:
     * - Initiator: DH(e, rs)
     * - Responder: DH(s, re)
     *
     * 注意: 读取方向和写入方向的 es/se 角色互换！
     */
    private fun readSE(): Int {
        val sharedSecret = if (isInitiator) {
            // Initiator 读取: DH(e, rs)
            if (e == null) throw NoiseException("Local ephemeral key (e) not set")
            if (rs == null) throw NoiseException("Remote static key (rs) not set")
            dh.dh(e!!.privateKey, rs!!)
        } else {
            // Responder 读取: DH(s, re)
            if (s == null) throw NoiseException("Local static key (s) not set")
            if (re == null) throw NoiseException("Remote ephemeral key (re) not set")
            dh.dh(s!!.privateKey, re!!)
        }
        ss.mixKey(sharedSecret)
        return 0
    }

    /**
     * Token "ss": DH(s, rs) - 读取方向
     *
     * 与写入方向相同。
     */
    private fun readSS(): Int {
        writeSS()
        return 0
    }

    // ========================================================================
    // 模式定义
    // ========================================================================

    /**
     * 获取指定消息索引的 token 列表
     *
     * Noise_IK 模式:
     * - Initiator 消息 (index 0): -> e, es, s, ss
     * - Responder 消息 (index 1): <- e, ee, se
     *
     * @param messageIndex 消息索引
     * @return List<String> token 列表
     */
    private fun getTokensForMessage(messageIndex: Int): List<String> {
        return when (pattern) {
            NoisePattern.IK -> {
                when (messageIndex) {
                    0 -> {
                        // Initiator: -> e, es, s, ss
                        if (isInitiator) listOf("e", "es", "s", "ss")
                        // Responder: <- e, ee, se
                        else listOf("e", "ee", "se")
                    }
                    1 -> {
                        // Initiator: <- e, ee, se
                        if (isInitiator) listOf("e", "ee", "se")
                        // Responder: -> e, es, s, ss
                        else listOf("e", "es", "s", "ss")
                    }
                    else -> throw NoiseException("Invalid message index: $messageIndex for pattern $pattern")
                }
            }
        }
    }

    /**
     * 获取握手消息总数
     *
     * @return Int 消息总数
     */
    private fun getMessageCount(): Int {
        return when (pattern) {
            NoisePattern.IK -> 2  // 双向各一条消息
        }
    }

    /**
     * 获取握手哈希
     *
     * @return ByteArray 握手哈希值
     */
    fun getHandshakeHash(): ByteArray = ss.getHandshakeHash()

    /**
     * 安全清除所有敏感数据
     */
    fun clear() {
        s?.let {
            NoiseUtils.secureWipe(it.publicKey)
            NoiseUtils.secureWipe(it.privateKey)
        }
        e?.let {
            NoiseUtils.secureWipe(it.publicKey)
            NoiseUtils.secureWipe(it.privateKey)
        }
        rs?.let { NoiseUtils.secureWipe(it) }
        re?.let { NoiseUtils.secureWipe(it) }
        s = null
        e = null
        rs = null
        re = null
        ss.clear()
    }
}

/**
 * 握手消息结果
 *
 * @property message 消息内容
 *   - writeMessage: 完整的握手消息（发送给对端）
 *   - readMessage: 解密后的 payload
 * @property cipherStates 分割后的 CipherState 对（仅最后一条消息）
 *   - first: 发送方向的 CipherState
 *   - second: 接收方向的 CipherState
 */
data class HandshakeMessage(
    val message: ByteArray,
    val cipherStates: Pair<CipherState, CipherState>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HandshakeMessage) return false
        return message.contentEquals(other.message)
    }

    override fun hashCode(): Int = message.contentHashCode()
}
