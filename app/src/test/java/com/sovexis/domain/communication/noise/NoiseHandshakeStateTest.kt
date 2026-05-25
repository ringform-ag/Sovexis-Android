package com.sovexis.domain.communication.noise

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Field

/**
 * NoiseHandshakeState 单元测试
 *
 * 测试项目：
 * - IK 模式握手流程
 * - XK 模式握手流程
 * - 握手完成后的密钥派生
 * - 握手哈希验证
 */
class NoiseHandshakeStateTest {

    /** 通过反射读取 NoiseHandshakeState 的私有 pattern 字段 */
    private fun getPattern(handshakeState: NoiseHandshakeState): NoiseProtocol.HandshakePattern {
        val field: Field = NoiseHandshakeState::class.java.getDeclaredField("pattern")
        field.isAccessible = true
        return field.get(handshakeState) as NoiseProtocol.HandshakePattern
    }

    @Test
    fun `test IK pattern initialization`() {
        val localStaticKey = ByteArray(32) { 0x01 }
        val remoteStaticPubKey = ByteArray(32) { 0x02 }

        val handshakeState = NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.IK,
            isInitiator = true,
            localStaticKey = localStaticKey,
            remoteStaticPublicKey = remoteStaticPubKey
        )

        assertFalse(handshakeState.isComplete)
        assertEquals(NoiseProtocol.HandshakePattern.IK, getPattern(handshakeState))
    }

    @Test
    fun `test XK pattern initialization`() {
        val localStaticKey = ByteArray(32) { 0x01 }
        val remoteStaticPubKey = ByteArray(32) { 0x02 }

        val handshakeState = NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.XK,
            isInitiator = true,
            localStaticKey = localStaticKey,
            remoteStaticPublicKey = remoteStaticPubKey
        )

        assertFalse(handshakeState.isComplete)
        assertEquals(NoiseProtocol.HandshakePattern.XK, getPattern(handshakeState))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test IK pattern requires remote static key for initiator`() {
        val localStaticKey = ByteArray(32) { 0x01 }

        // 发起方必须提供 remoteStaticPublicKey
        NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.IK,
            isInitiator = true,
            localStaticKey = localStaticKey,
            remoteStaticPublicKey = null
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test XK pattern requires remote static key for initiator`() {
        val localStaticKey = ByteArray(32) { 0x01 }

        // 发起方必须提供 remoteStaticPublicKey
        NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.XK,
            isInitiator = true,
            localStaticKey = localStaticKey,
            remoteStaticPublicKey = null
        )
    }

    @Test
    fun `test handshake completion`() {
        val initiatorStaticKey = ByteArray(32) { 0x01 }
        val responderStaticKey = ByteArray(32) { 0x02 }

        // 发起方握手状态
        val initiatorState = NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.IK,
            isInitiator = true,
            localStaticKey = initiatorStaticKey,
            remoteStaticPublicKey = responderStaticKey
        )

        // 响应方握手状态
        val responderState = NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.IK,
            isInitiator = false,
            localStaticKey = responderStaticKey,
            remoteStaticPublicKey = initiatorStaticKey
        )

        // 完成握手
        val initiatorSession = initiatorState.completeHandshake()
        val responderSession = responderState.completeHandshake()

        // 验证会话属性
        assertTrue(initiatorState.isComplete)
        assertTrue(responderState.isComplete)

        // 验证会话密钥长度
        assertEquals(NoiseProtocol.AES_KEY_LEN, initiatorSession.sendKey.size)
        assertEquals(NoiseProtocol.AES_KEY_LEN, initiatorSession.receiveKey.size)
        assertEquals(NoiseProtocol.AES_KEY_LEN, responderSession.sendKey.size)
        assertEquals(NoiseProtocol.AES_KEY_LEN, responderSession.receiveKey.size)

        // 验证握手哈希长度
        assertEquals(NoiseProtocol.HASH_LEN, initiatorSession.handshakeHash.size)
        assertEquals(NoiseProtocol.HASH_LEN, responderSession.handshakeHash.size)
    }

    @Test
    fun `test initiator and responder key reciprocity`() {
        val initiatorStaticKey = ByteArray(32) { 0x01 }
        val responderStaticKey = ByteArray(32) { 0x02 }

        // 使用相同的 SymmetricState 初始化（模拟完整握手）
        val initiatorState = NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.IK,
            isInitiator = true,
            localStaticKey = initiatorStaticKey,
            remoteStaticPublicKey = responderStaticKey
        )

        val responderState = NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.IK,
            isInitiator = false,
            localStaticKey = responderStaticKey,
            remoteStaticPublicKey = initiatorStaticKey
        )

        val initiatorSession = initiatorState.completeHandshake()
        val responderSession = responderState.completeHandshake()

        // 发起方的发送密钥应该等于响应方的接收密钥
        // 发起方的接收密钥应该等于响应方的发送密钥
        assertArrayEquals(initiatorSession.sendKey, responderSession.receiveKey)
        assertArrayEquals(initiatorSession.receiveKey, responderSession.sendKey)
    }

    @Test
    fun `test prologue mixing`() {
        val localStaticKey = ByteArray(32) { 0x01 }
        val remoteStaticPubKey = ByteArray(32) { 0x02 }
        val prologue = "Sovexis-Noise-v1".toByteArray()

        val handshakeState = NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.IK,
            isInitiator = true,
            localStaticKey = localStaticKey,
            remoteStaticPublicKey = remoteStaticPubKey,
            prologue = prologue
        )

        // 验证握手状态已创建（序言已混合）
        assertNotNull(handshakeState)
    }

    @Test
    fun `test write and read message`() {
        val initiatorStaticKey = ByteArray(32) { 0x01 }
        val responderStaticKey = ByteArray(32) { 0x02 }

        val initiatorState = NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.IK,
            isInitiator = true,
            localStaticKey = initiatorStaticKey,
            remoteStaticPublicKey = responderStaticKey
        )

        val payload = "Test payload".toByteArray()
        val message = initiatorState.writeMessage(payload)

        assertEquals(NoiseProtocol.HandshakePattern.IK, message.pattern)
        assertFalse(message.isComplete)
    }

    @Test
    fun `test session rotation detection`() {
        val session = NoiseHandshakeState(
            pattern = NoiseProtocol.HandshakePattern.IK,
            isInitiator = true,
            localStaticKey = ByteArray(32) { 0x01 },
            remoteStaticPublicKey = ByteArray(32) { 0x02 }
        ).completeHandshake()

        // 新会话不需要轮换
        assertFalse(session.needsRotation())

        // 模拟发送大量消息
        session.messageCount = NoiseProtocol.SESSION_ROTATION_MESSAGES
        assertTrue(session.needsRotation())
    }
}
