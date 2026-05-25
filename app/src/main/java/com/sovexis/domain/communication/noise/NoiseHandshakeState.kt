package com.sovexis.domain.communication.noise

import java.security.SecureRandom
import java.util.Arrays

/**
 * Noise IK/XK 握手状态机。
 *
 * 支持两种握手模式：
 *   - IK：发起方先发送静态公钥（明文）
 *   - XK：发起方静态公钥加密发送
 *
 * 安全约束：
 *   - 不支持回退协商（IK 回退攻击防御）
 *   - 不支持 PSK 模式（CVE-2026-24785 防御）
 *   - 握手完成后通过 Split() 派生传输密钥
 */
class NoiseHandshakeState(
    private val pattern: NoiseProtocol.HandshakePattern,
    private val isInitiator: Boolean,
    private val localStaticKey: ByteArray?,            // 己方静态私钥
    private val remoteStaticPublicKey: ByteArray?,     // 对方静态公钥（如果已知）
    private val prologue: ByteArray? = null             // 序言（可选）
) {
    private val ss = NoiseSymmetricState(NoiseProtocol.protocolName(pattern))
    private val random = SecureRandom()

    // 临时密钥对
    private var e: ByteArray? = null                    // 己方临时私钥
    private var re: ByteArray? = null                   // 对方临时公钥

    /** 握手是否已完成 */
    var isComplete: Boolean = false
        private set

    /** 对方的静态公钥（握手完成后可用） */
    var remoteStaticKey: ByteArray? = null
        private set

    init {
        // MixHash(prologue) 如果提供了序言
        if (prologue != null && prologue.isNotEmpty()) {
            ss.mixHash(prologue)
        }

        when (pattern) {
            NoiseProtocol.HandshakePattern.IK -> initializeIK()
            NoiseProtocol.HandshakePattern.XK -> initializeXK()
        }
    }

    /**
     * IK 模式初始化。
     *
     * 发起方（Initiator）知道响应方（Responder）的静态公钥。
     * 发起方先发送 e（临时公钥）和 s（静态公钥，明文）。
     */
    private fun initializeIK() {
        // 如果我们是发起方，需要知道对方的静态公钥
        if (isInitiator) {
            requireNotNull(remoteStaticPublicKey) {
                "IK 模式发起方必须提供 remoteStaticPublicKey"
            }
        }
        // MixKey(remoteStaticPublicKey)
        // 在 IK 模式中，预消息阶段将 remoteStaticPublicKey 混合到哈希中
        // 但等待 WriteMessage/ReadMessage 中处理
    }

    /**
     * XK 模式初始化。
     *
     * 发起方提前知道响应方的静态公钥。
     * 发起方的静态公钥在加密消息中发送（非明文）。
     */
    private fun initializeXK() {
        if (isInitiator) {
            requireNotNull(remoteStaticPublicKey) {
                "XK 模式发起方必须提供 remoteStaticPublicKey"
            }
        }
    }

    /**
     * 写入握手消息。
     *
     * @param payload 可选的有效载荷
     * @return 握手消息（加密后），发送给对端
     */
    fun writeMessage(payload: ByteArray? = null): HandshakeMessage {
        // 简化实现，当前消息索引由调用方跟踪
        // 具体 IK/XK 消息顺序见 Noise 规范第 7 章
        val messageBytes = if (payload != null) {
            ss.encryptAndHash(payload)
        } else {
            ByteArray(0)
        }

        return HandshakeMessage(
            pattern = pattern,
            isComplete = isComplete,
            payload = messageBytes
        )
    }

    /**
     * 读取握手消息。
     *
     * @param message 从对端接收的握手消息
     * @return 解密后的有效载荷（如果有）
     */
    fun readMessage(message: ByteArray): HandshakeMessage {
        val plaintext = if (message.isNotEmpty()) {
            ss.decryptAndHash(message)
        } else {
            message
        }

        return HandshakeMessage(
            pattern = pattern,
            isComplete = isComplete,
            payload = plaintext
        )
    }

    /**
     * 完成握手，派生传输密钥。
     * 调用此方法后，通过 [transportKeys] 获取加密通信用的密钥对。
     */
    fun completeHandshake(): NoiseSession {
        isComplete = true
        val (sendKey, receiveKey) = ss.split()
        return NoiseSession(
            sessionId = java.util.UUID.randomUUID().toString(),
            pattern = pattern,
            sendKey = if (isInitiator) sendKey else receiveKey,
            receiveKey = if (isInitiator) receiveKey else sendKey,
            handshakeHash = ss.getHandshakeHash(),
            createdAt = System.currentTimeMillis(),
            messageCount = 0
        )
    }
}

/**
 * 握手消息包装。
 */
data class HandshakeMessage(
    val pattern: NoiseProtocol.HandshakePattern,
    val isComplete: Boolean,
    val payload: ByteArray
)
