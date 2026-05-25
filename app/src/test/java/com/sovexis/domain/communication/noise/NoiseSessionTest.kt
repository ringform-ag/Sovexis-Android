package com.sovexis.domain.communication.noise

import org.junit.Assert.*
import org.junit.Test

/**
 * NoiseSession 单元测试
 *
 * 测试项目：
 * - 会话过期检测
 * - 消息数量轮换检测
 * - 会话相等性
 */
class NoiseSessionTest {

    @Test
    fun `test session expiration`() {
        val session = createTestSession()

        // 新会话不应该过期
        assertFalse(session.isExpired())
        assertFalse(session.isExpired(1000L)) // 1秒TTL

        // 创建过期会话
        val expiredSession = createTestSession(createdAt = System.currentTimeMillis() - 2 * 60 * 60 * 1000L) // 2小时前
        assertTrue(expiredSession.isExpired()) // 默认1小时TTL
        assertFalse(expiredSession.isExpired(3 * 60 * 60 * 1000L)) // 3小时TTL下不过期
    }

    @Test
    fun `test session rotation based on message count`() {
        val session = createTestSession()

        // 新会话不需要轮换
        assertFalse(session.needsRotation())

        // 设置消息计数接近上限
        session.messageCount = NoiseProtocol.SESSION_ROTATION_MESSAGES - 1
        assertFalse(session.needsRotation())

        // 达到上限
        session.messageCount = NoiseProtocol.SESSION_ROTATION_MESSAGES
        assertTrue(session.needsRotation())

        // 超过上限
        session.messageCount = NoiseProtocol.SESSION_ROTATION_MESSAGES + 100
        assertTrue(session.needsRotation())
    }

    @Test
    fun `test session equality`() {
        val session1 = createTestSession(sessionId = "test-id-1")
        val session2 = createTestSession(sessionId = "test-id-1")
        val session3 = createTestSession(sessionId = "test-id-2")

        assertEquals(session1, session2)
        assertNotEquals(session1, session3)
        assertEquals(session1.hashCode(), session2.hashCode())
    }

    @Test
    fun `test session data integrity`() {
        val sendKey = ByteArray(32) { 0x01 }
        val receiveKey = ByteArray(32) { 0x02 }
        val handshakeHash = ByteArray(32) { 0x03 }

        val session = NoiseSession(
            sessionId = "test-session",
            pattern = NoiseProtocol.HandshakePattern.IK,
            sendKey = sendKey,
            receiveKey = receiveKey,
            handshakeHash = handshakeHash,
            messageCount = 42
        )

        assertEquals("test-session", session.sessionId)
        assertEquals(NoiseProtocol.HandshakePattern.IK, session.pattern)
        assertArrayEquals(sendKey, session.sendKey)
        assertArrayEquals(receiveKey, session.receiveKey)
        assertArrayEquals(handshakeHash, session.handshakeHash)
        assertEquals(42L, session.messageCount)
    }

    private fun createTestSession(
        sessionId: String = "test-session-id",
        createdAt: Long = System.currentTimeMillis()
    ): NoiseSession {
        return NoiseSession(
            sessionId = sessionId,
            pattern = NoiseProtocol.HandshakePattern.IK,
            sendKey = ByteArray(32) { 0x01 },
            receiveKey = ByteArray(32) { 0x02 },
            handshakeHash = ByteArray(32) { 0x03 },
            createdAt = createdAt,
            messageCount = 0
        )
    }
}
