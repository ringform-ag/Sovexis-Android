package com.sovexis.domain.communication.noise

/**
 * 已建立的 Noise 会话。
 *
 * 包含传输密钥对和会话元数据。
 *
 * 安全约束：
 *   - 每 1000 条消息或 1 小时后自动过期（CVE-2021-4239 防御）
 *   - 消息计数由 CryptoCommLayer 管理
 */
data class NoiseSession(
    val sessionId: String,
    val pattern: NoiseProtocol.HandshakePattern,
    val sendKey: ByteArray,                    // 发送密钥（AES-256）
    val receiveKey: ByteArray,                 // 接收密钥（AES-256）
    val handshakeHash: ByteArray,              // 握手哈希（用于验证）
    val createdAt: Long = System.currentTimeMillis(),
    var messageCount: Long = 0
) {
    /** 会话是否已过期 */
    fun isExpired(ttlMs: Long = 60 * 60 * 1000L): Boolean {
        val age = System.currentTimeMillis() - createdAt
        return age > ttlMs
    }

    /** 是否需要因消息数量而轮换 */
    fun needsRotation(maxMessages: Long = NoiseProtocol.SESSION_ROTATION_MESSAGES): Boolean {
        return messageCount >= maxMessages
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoiseSession) return false
        return sessionId == other.sessionId
    }

    override fun hashCode(): Int = sessionId.hashCode()
}
