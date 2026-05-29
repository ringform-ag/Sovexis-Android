package com.sovexis.domain.communication

import kotlinx.coroutines.flow.Flow

/**
 * [AI-GENERATED]
 * ç”Ÿæˆæ—¶é—´: 2026-05-09
 * å®žçŽ°çŠ¶æ€? âœ?AIå¯å®žçŽ? * å®¡æ ¸çŠ¶æ€? å¾…å®¡æ ? *
 * ä¼ è¾“é€‚é…å™¨æŽ¥å? * æŠ½è±¡åº•å±‚ä¼ è¾“æœºåˆ¶ï¼Œæ”¯æŒå¤šç§ä¼ è¾“æ–¹å¼? */
interface TransportAdapter {

    /**
     * è¿žæŽ¥çŠ¶æ€?     */
    val isConnected: Boolean

    /**
     * å»ºç«‹è¿žæŽ¥
     *
     * @return Result<Unit> è¿žæŽ¥ç»“æžœ
     */
    suspend fun connect(): Result<Unit>

    /**
     * æ–­å¼€è¿žæŽ¥
     */
    suspend fun disconnect()

    /**
     * å‘é€åŠ å¯†æ¶ˆæ?     *
     * @param encryptedPayload åŠ å¯†åŽçš„æ¶ˆæ¯è½½è·
     * @param destinationDid ç›®æ ‡DID
     * @return Result<String> æ¶ˆæ¯ID
     */
    suspend fun send(
        encryptedPayload: ByteArray,
        destinationDid: String
    ): Result<String>

    /**
     * æŽ¥æ”¶æ¶ˆæ¯æµ?     *
     * @return Flow<RawMessage> åŽŸå§‹æ¶ˆæ¯æµ?     */
    fun receive(): Flow<RawMessage>
}

/**
 * åŽŸå§‹æ¶ˆæ¯
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
