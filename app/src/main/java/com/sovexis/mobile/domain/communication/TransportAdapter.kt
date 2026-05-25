package com.sovexis.mobile.domain.communication

import kotlinx.coroutines.flow.Flow

/**
 * [AI-GENERATED]
 * 生成时间: 2026-05-09
 * 实现状�? �?AI可实�? * 审核状�? 待审�? *
 * 传输适配器接�? * 抽象底层传输机制，支持多种传输方�? */
interface TransportAdapter {

    /**
     * 连接状�?     */
    val isConnected: Boolean

    /**
     * 建立连接
     *
     * @return Result<Unit> 连接结果
     */
    suspend fun connect(): Result<Unit>

    /**
     * 断开连接
     */
    suspend fun disconnect()

    /**
     * 发送加密消�?     *
     * @param encryptedPayload 加密后的消息载荷
     * @param destinationDid 目标DID
     * @return Result<String> 消息ID
     */
    suspend fun send(
        encryptedPayload: ByteArray,
        destinationDid: String
    ): Result<String>

    /**
     * 接收消息�?     *
     * @return Flow<RawMessage> 原始消息�?     */
    fun receive(): Flow<RawMessage>
}

/**
 * 原始消息
 */
data class RawMessage(
    val messageId: String,
    val payload: ByteArray,
    val senderAddress: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawMessage) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}
