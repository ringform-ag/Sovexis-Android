package com.sovexis.mobile.domain.communication.noise

import java.security.SecureRandom

/**
 * Noise 协议常量定义、模式枚举与工厂方法
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: Phase 1 + Phase 2 完整实现
 * 规范参考: Noise Protocol Framework Rev 34
 * (https://noiseprotocol.org/noise.html)
 *
 * 本文件提供:
 * 1. Noise 协议全局常量
 * 2. NoisePattern 枚举 - 支持的握手模式定义
 * 3. NoiseSession - 完整的 Noise 会话封装
 * 4. NoiseProtocolFactory - 工厂方法，简化协议初始化
 *
 * 协议组合命名规则 (Noise 规范 Section 3):
 * Noise_<Pattern>_<DH>_<Cipher>_<Hash>
 * 例如: Noise_IK_25519_AESGCM_SHA256
 *
 * [IMPLEMENTATION-STATUS: COMPLETE]
 * [REVIEW-REQUIRED: SECURITY]
 */

// ============================================================================
// Noise 协议常量
// ============================================================================

/**
 * Noise 协议全局常量
 *
 * [AI-GENERATED]
 * [IMPLEMENTATION-STATUS: COMPLETE]
 */
object NoiseConstants {

    /**
     * Noise 协议规范版本
     */
    const val NOISE_SPEC_VERSION = "Rev 34"

    /**
     * Noise 协议规范 URL
     */
    const val NOISE_SPEC_URL = "https://noiseprotocol.org/noise.html"

    /**
     * 默认 DH 函数名称
     */
    const val DEFAULT_DH_NAME = "25519"

    /**
     * 默认 Cipher 函数名称
     */
    const val DEFAULT_CIPHER_NAME = "AESGCM"

    /**
     * 默认 Hash 函数名称
     */
    const val DEFAULT_HASH_NAME = "SHA256"

    /**
     * X25519 DH 密钥长度 (字节)
     */
    const val DH_LEN = 32

    /**
     * AES-256-GCM 密钥长度 (字节)
     */
    const val CIPHER_KEY_LEN = 32

    /**
     * AES-256-GCM Tag 长度 (字节)
     */
    const val CIPHER_TAG_LEN = 16

    /**
     * SHA-256 哈希长度 (字节)
     */
    const val HASH_LEN = 32

    /**
     * SHA-256 块长度 (字节)
     */
    const val HASH_BLOCK_LEN = 64

    /**
     * Noise nonce 最大安全值 (2^63 - 1)
     */
    const val MAX_NONCE = (1L shl 63) - 1

    /**
     * Sovexis 自定义 prologue 标识
     */
    const val SOVEXIS_PROLOGUE = "Sovexis-Noise-v1"
}

// ============================================================================
// Noise Pattern 枚举
// ============================================================================

/**
 * Noise 握手模式枚举
 *
 * 定义了支持的 Noise 协议握手模式。
 * 每个模式定义了 DH 交换的顺序和方向。
 *
 * 模式命名规则 (Noise 规范 Section 6):
 * - 第一个字母: Initiator 的预知公钥
 *   - "I": 预知 Responder 的静态公钥 (rs)
 *   - "N": 不预知任何公钥
 *   - "X": 不预知任何公钥，但发送静态公钥
 * - 第二个字母: Responder 的预知公钥
 *   - "K": 预知 Initiator 的静态公钥 (s)
 *   - "N": 不预知任何公钥
 *   - "X": 不预知任何公钥，但发送静态公钥
 * - 第三个字母 (可选): 单向模式
 *   - "1": 单向，只有 Initiator 发送消息
 *
 * 当前支持的模式:
 * - IK: 双向，Initiator 预知 rs，Responder 预知 s
 *   - 最常用模式，提供双向身份认证
 *   - 握手消息: 2 条 (双向各一条)
 *
 * [AI-GENERATED]
 * [IMPLEMENTATION-STATUS: COMPLETE]
 *
 * @property patternName 模式名称 (如 "IK")
 * @property protocolName 完整协议名称 (如 "Noise_IK_25519_AESGCM_SHA256")
 * @property messageCount 握手消息总数
 * @property isOneWay 是否为单向模式
 * @property initiatorPreSharedInitiatorStatic Initiator 是否预知自己的静态公钥
 * @property initiatorPreSharedResponderStatic Initiator 是否预知 Responder 的静态公钥
 * @property responderPreSharedInitiatorStatic Responder 是否预知 Initiator 的静态公钥
 */
enum class NoisePattern(
    val patternName: String,
    val protocolName: String,
    val messageCount: Int,
    val isOneWay: Boolean = false,
    val initiatorPreSharedInitiatorStatic: Boolean = false,
    val initiatorPreSharedResponderStatic: Boolean = false,
    val responderPreSharedInitiatorStatic: Boolean = false
) {
    /**
     * Noise_IK 模式
     *
     * 双向握手模式:
     * - Initiator 预知 Responder 的静态公钥 (rs)
     * - Responder 预知 Initiator 的静态公钥 (s)
     *
     * 握手流程:
     * ```
     * Initiator                          Responder
     * ----------                          ---------
     * -> e, es, s, ss
     *                                     <- e, ee, se
     * ```
     *
     * Token 说明:
     * - e: 发送临时公钥
     * - es: DH(e, rs) - Initiator 临时/Responder 静态
     * - s: 发送静态公钥 (已加密)
     * - ss: DH(s, rs) - 双方静态
     * - ee: DH(e, re) - 双方临时
     * - se: DH(s, re) - Initiator 静态/Responder 临时
     *
     * 安全属性:
     * - 双向身份认证 (双方验证对方静态公钥)
     * - 前向安全性 (临时密钥提供)
     * - 抵抗密钥泄露伪装 (KCI)
     */
    IK(
        patternName = "IK",
        protocolName = "Noise_IK_25519_AESGCM_SHA256",
        messageCount = 2,
        isOneWay = false,
        initiatorPreSharedInitiatorStatic = false,
        initiatorPreSharedResponderStatic = true,
        responderPreSharedInitiatorStatic = true
    );

    companion object {
        /**
         * 根据协议名称查找模式
         *
         * @param protocolName 完整协议名称
         * @return NoisePattern? 对应的模式，未找到返回 null
         */
        fun fromProtocolName(protocolName: String): NoisePattern? {
            return entries.find { it.protocolName == protocolName }
        }

        /**
         * 根据模式名称查找模式
         *
         * @param patternName 模式名称 (如 "IK")
         * @return NoisePattern? 对应的模式，未找到返回 null
         */
        fun fromPatternName(patternName: String): NoisePattern? {
            return entries.find { it.patternName == patternName }
        }
    }
}

// ============================================================================
// Noise 会话
// ============================================================================

/**
 * Noise 会话封装
 *
 * 封装完整的 Noise 协议会话，包括握手阶段和传输阶段。
 *
 * 生命周期:
 * 1. 创建会话 (通过 NoiseProtocolFactory)
 * 2. 执行握手 (writeMessage / readMessage)
 * 3. 获取传输 CipherState (split)
 * 4. 安全通信 (encrypt / decrypt)
 * 5. 关闭会话 (close)
 *
 * [AI-GENERATED]
 * [IMPLEMENTATION-STATUS: COMPLETE]
 *
 * @property handshakeState 握手状态
 * @property sendCipher 发送方向 CipherState (握手完成后可用)
 * @property receiveCipher 接收方向 CipherState (握手完成后可用)
 */
class NoiseSession(
    private val handshakeState: HandshakeState
) {
    /**
     * 发送方向 CipherState
     *
     * 握手完成后，使用此 CipherState 加密发送的数据。
     */
    var sendCipher: CipherState? = null
        private set

    /**
     * 接收方向 CipherState
     *
     * 握手完成后，使用此 CipherState 解密接收的数据。
     */
    var receiveCipher: CipherState? = null
        private set

    /**
     * 握手是否已完成
     */
    val isHandshakeComplete: Boolean
        get() = handshakeState.isCompleted

    /**
     * 当前握手消息索引
     */
    val messageIndex: Int
        get() = handshakeState.messageIndex

    /**
     * 握手模式
     */
    val pattern: NoisePattern
        get() = handshakeState.pattern

    /**
     * 是否为发起方
     */
    val isInitiator: Boolean
        get() = handshakeState.isInitiator

    /**
     * 写入握手消息
     *
     * @param payload 附加载荷
     * @return Result<ByteArray> 完整的握手消息字节
     */
    fun writeHandshakeMessage(payload: ByteArray = ByteArray(0)): Result<ByteArray> {
        return try {
            val result = handshakeState.writeMessage(payload)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull()!!)
            }

            val handshakeMessage = result.getOrThrow()

            // 如果握手完成，保存 CipherState
            if (handshakeMessage.cipherStates != null) {
                val (c1, c2) = handshakeMessage.cipherStates
                if (handshakeState.isInitiator) {
                    sendCipher = c1
                    receiveCipher = c2
                } else {
                    sendCipher = c2
                    receiveCipher = c1
                }
            }

            Result.success(handshakeMessage.message)
        } catch (e: Exception) {
            Result.failure(NoiseException("Failed to write handshake message", e))
        }
    }

    /**
     * 读取握手消息
     *
     * @param message 接收到的握手消息
     * @return Result<ByteArray> 解密后的 payload
     */
    fun readHandshakeMessage(message: ByteArray): Result<ByteArray> {
        return try {
            val result = handshakeState.readMessage(message)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull()!!)
            }

            val handshakeMessage = result.getOrThrow()

            // 如果握手完成，保存 CipherState
            if (handshakeMessage.cipherStates != null) {
                val (c1, c2) = handshakeMessage.cipherStates
                if (handshakeState.isInitiator) {
                    sendCipher = c1
                    receiveCipher = c2
                } else {
                    sendCipher = c2
                    receiveCipher = c1
                }
            }

            Result.success(handshakeMessage.message)
        } catch (e: Exception) {
            Result.failure(NoiseException("Failed to read handshake message", e))
        }
    }

    /**
     * 加密传输数据
     *
     * 使用发送方向 CipherState 加密数据。
     *
     * @param plaintext 明文数据
     * @return Result<ByteArray> 加密后的数据
     * @throws NoiseException 握手未完成
     */
    fun encrypt(plaintext: ByteArray): Result<ByteArray> {
        val cipher = sendCipher
            ?: return Result.failure(NoiseException("Handshake not completed. Cannot encrypt."))
        return cipher.encryptWithAd(ByteArray(0), plaintext)
    }

    /**
     * 解密传输数据
     *
     * 使用接收方向 CipherState 解密数据。
     *
     * @param ciphertext 密文数据
     * @return Result<ByteArray> 解密后的明文
     * @throws NoiseException 握手未完成
     */
    fun decrypt(ciphertext: ByteArray): Result<ByteArray> {
        val cipher = receiveCipher
            ?: return Result.failure(NoiseException("Handshake not completed. Cannot decrypt."))
        return cipher.decryptWithAd(ByteArray(0), ciphertext)
    }

    /**
     * 获取握手哈希
     *
     * @return ByteArray 握手哈希值
     */
    fun getHandshakeHash(): ByteArray = handshakeState.getHandshakeHash()

    /**
     * 关闭会话并清除敏感数据
     */
    fun close() {
        sendCipher?.clear()
        receiveCipher?.clear()
        handshakeState.clear()
        sendCipher = null
        receiveCipher = null
    }
}

// ============================================================================
// Noise 协议工厂
// ============================================================================

/**
 * Noise 协议工厂
 *
 * 提供便捷的工厂方法来创建 Noise 协议组件和会话。
 *
 * 使用示例:
 * ```kotlin
 * // 创建 Initiator 会话
 * val session = NoiseProtocolFactory.createInitiatorSession(
 *     pattern = NoisePattern.IK,
 *     localStaticKeyPair = myKeyPair,
 *     remoteStaticPublicKey = theirPublicKey,
 *     prologue = "Sovexis-Noise-v1".toByteArray()
 * )
 *
 * // 创建 Responder 会话
 * val session = NoiseProtocolFactory.createResponderSession(
 *     pattern = NoisePattern.IK,
 *     localStaticKeyPair = myKeyPair,
 *     remoteStaticPublicKey = theirPublicKey,
 *     prologue = "Sovexis-Noise-v1".toByteArray()
 * )
 * ```
 *
 * [AI-GENERATED]
 * [IMPLEMENTATION-STATUS: COMPLETE]
 */
object NoiseProtocolFactory {

    /**
     * 创建默认的 DH 函数实现 (X25519)
     *
     * @return NoiseDH X25519 DH 函数
     */
    fun createDH(): NoiseDH {
        return X25519DH()
    }

    /**
     * 创建默认的 Cipher 函数实现 (AES-256-GCM)
     *
     * @return NoiseCipher AES-256-GCM Cipher 函数
     */
    fun createCipher(): NoiseCipher {
        return AesGcmCipher()
    }

    /**
     * 创建默认的 Hash 函数实现 (SHA-256)
     *
     * @return NoiseHash SHA-256 Hash 函数
     */
    fun createHash(): NoiseHash {
        return Sha256Hash()
    }

    /**
     * 创建 HandshakeState
     *
     * @param pattern 握手模式
     * @param initiator 是否为发起方
     * @param prologue 前言数据
     * @param localStaticKeyPair 本地静态密钥对
     * @param localEphemeralKeyPair 本地临时密钥对 (可选)
     * @param remoteStaticPublicKey 远程静态公钥
     * @param remoteEphemeralPublicKey 远程临时公钥 (可选)
     * @return Result<HandshakeState> 初始化后的 HandshakeState
     */
    fun createHandshakeState(
        pattern: NoisePattern,
        initiator: Boolean,
        prologue: ByteArray = ByteArray(0),
        localStaticKeyPair: NoiseKeyPair? = null,
        localEphemeralKeyPair: NoiseKeyPair? = null,
        remoteStaticPublicKey: ByteArray? = null,
        remoteEphemeralPublicKey: ByteArray? = null
    ): Result<HandshakeState> {
        return try {
            val dh = createDH()
            val cipher = createCipher()
            val hash = createHash()

            val handshakeState = HandshakeState(dh, cipher, hash)
            val initResult = handshakeState.initialize(
                pattern = pattern,
                initiator = initiator,
                prologue = prologue,
                s = localStaticKeyPair,
                e = localEphemeralKeyPair,
                rs = remoteStaticPublicKey,
                re = remoteEphemeralPublicKey
            )

            if (initResult.isFailure) {
                return Result.failure(initResult.exceptionOrNull()!!)
            }

            Result.success(handshakeState)
        } catch (e: Exception) {
            Result.failure(NoiseException("Failed to create HandshakeState", e))
        }
    }

    /**
     * 创建 Initiator 会话
     *
     * 创建并初始化一个 Noise 发起方会话。
     *
     * @param pattern 握手模式 (默认 Noise_IK)
     * @param localStaticKeyPair 本地静态密钥对
     * @param remoteStaticPublicKey 远程静态公钥
     * @param prologue 前言数据 (默认 "Sovexis-Noise-v1")
     * @return Result<NoiseSession> 初始化后的 Noise 会话
     */
    fun createInitiatorSession(
        pattern: NoisePattern = NoisePattern.IK,
        localStaticKeyPair: NoiseKeyPair? = null,
        remoteStaticPublicKey: ByteArray? = null,
        prologue: ByteArray = NoiseConstants.SOVEXIS_PROLOGUE.toByteArray(Charsets.UTF_8)
    ): Result<NoiseSession> {
        return try {
            val handshakeStateResult = createHandshakeState(
                pattern = pattern,
                initiator = true,
                prologue = prologue,
                localStaticKeyPair = localStaticKeyPair,
                remoteStaticPublicKey = remoteStaticPublicKey
            )

            if (handshakeStateResult.isFailure) {
                return Result.failure(handshakeStateResult.exceptionOrNull()!!)
            }

            Result.success(NoiseSession(handshakeStateResult.getOrThrow()))
        } catch (e: Exception) {
            Result.failure(NoiseException("Failed to create initiator session", e))
        }
    }

    /**
     * 创建 Responder 会话
     *
     * 创建并初始化一个 Noise 响应方会话。
     *
     * @param pattern 握手模式 (默认 Noise_IK)
     * @param localStaticKeyPair 本地静态密钥对
     * @param remoteStaticPublicKey 远程静态公钥
     * @param prologue 前言数据 (默认 "Sovexis-Noise-v1")
     * @return Result<NoiseSession> 初始化后的 Noise 会话
     */
    fun createResponderSession(
        pattern: NoisePattern = NoisePattern.IK,
        localStaticKeyPair: NoiseKeyPair? = null,
        remoteStaticPublicKey: ByteArray? = null,
        prologue: ByteArray = NoiseConstants.SOVEXIS_PROLOGUE.toByteArray(Charsets.UTF_8)
    ): Result<NoiseSession> {
        return try {
            val handshakeStateResult = createHandshakeState(
                pattern = pattern,
                initiator = false,
                prologue = prologue,
                localStaticKeyPair = localStaticKeyPair,
                remoteStaticPublicKey = remoteStaticPublicKey
            )

            if (handshakeStateResult.isFailure) {
                return Result.failure(handshakeStateResult.exceptionOrNull()!!)
            }

            Result.success(NoiseSession(handshakeStateResult.getOrThrow()))
        } catch (e: Exception) {
            Result.failure(NoiseException("Failed to create responder session", e))
        }
    }

    /**
     * 生成 X25519 密钥对
     *
     * @param secureRandom 安全随机数生成器 (可选)
     * @return Result<NoiseKeyPair> X25519 密钥对
     */
    fun generateKeyPair(secureRandom: SecureRandom = SecureRandom()): Result<NoiseKeyPair> {
        return try {
            val dh = createDH()
            Result.success(dh.generateKeyPair(secureRandom))
        } catch (e: Exception) {
            Result.failure(NoiseException("Failed to generate key pair", e))
        }
    }

    /**
     * 执行 DH 密钥交换
     *
     * @param privateKey 本地私钥
     * @param publicKey 远程公钥
     * @return Result<ByteArray> 共享密钥
     */
    fun dhKeyExchange(privateKey: ByteArray, publicKey: ByteArray): Result<ByteArray> {
        return try {
            val dh = createDH()
            Result.success(dh.dh(privateKey, publicKey))
        } catch (e: Exception) {
            Result.failure(NoiseException("DH key exchange failed", e))
        }
    }
}
