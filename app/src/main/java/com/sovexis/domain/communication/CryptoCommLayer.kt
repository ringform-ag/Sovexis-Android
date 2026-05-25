package com.sovexis.domain.communication

import com.sovexis.domain.communication.noise.*
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.mobile.domain.communication.RawMessage
import com.sovexis.mobile.domain.communication.TransportAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 加密通信层装饰器。
 *
 * 实现 TransportAdapter 接口，对上层业务透明。
 * 内部管理 Noise 会话的建立、加密/解密、轮换。
 *
 * 静态公钥获取策略：
 *   - 服务商公钥：应用内预置（WhiteList）
 *   - 家庭节点/硬件令牌公钥：通过二维码或 NFC 交换
 *   - 手机对手机：二维码传递
 *   - 禁止通过网络查询公钥（MITM 窗口防御）
 *
 * 安全约束：
 *   - 每 1000 条消息或 1 小时自动重建会话
 *   - 解密失败立即回滚，不修改任何状态
 *   - 不支持协议降级协商
 */
class CryptoCommLayer(
    private val innerTransport: TransportAdapter,
    private val identityManager: IdentityManager,
    private val communicationLevel: CommunicationLevel = CommunicationLevel.STANDARD
) : TransportAdapter {

    /** 活跃的 Noise 会话（key = remoteDid） */
    private val sessions = ConcurrentHashMap<String, NoiseSession>()

    /** 已注册的静态公钥（key = remoteDid, value = 公钥字节） */
    private val knownPublicKeys = ConcurrentHashMap<String, ByteArray>()

    private val mutex = Mutex()

    // ── TransportAdapter 接口实现 ──

    override val isConnected: Boolean
        get() = innerTransport.isConnected

    override suspend fun connect(): Result<Unit> {
        return innerTransport.connect()
    }

    override suspend fun disconnect() {
        // 清理所有会话
        sessions.values.forEach { session ->
            session.sendKey.fill(0)
            session.receiveKey.fill(0)
        }
        sessions.clear()
        innerTransport.disconnect()
    }

    override suspend fun send(encryptedPayload: ByteArray, destinationDid: String): Result<String> {
        return runCatching {
            // 1. 获取或建立 Noise 会话
            val session = getOrEstablishSession(destinationDid)

            // 2. 加密消息
            val cipherState = NoiseCipherState().apply {
                initializeKey(session.sendKey)
                setNonce(session.messageCount)
            }
            val ciphertext = cipherState.encryptWithAd(null, encryptedPayload)

            // 3. 更新消息计数
            session.messageCount++

            // 4. 交给内层传输
            val result = innerTransport.send(ciphertext, destinationDid).getOrThrow()

            // 5. 检查是否需要轮换
            if (session.needsRotation()) {
                rotateSession(destinationDid)
            }

            result
        }
    }

    override fun receive(): Flow<RawMessage> = flow {
        innerTransport.receive().collect { encryptedMessage ->
            val remoteDid = encryptedMessage.senderAddress

            val session = getOrEstablishSession(remoteDid)

            try {
                val cipherState = NoiseCipherState().apply {
                    initializeKey(session.receiveKey)
                    setNonce(session.messageCount)
                }
                val plaintext = cipherState.decryptWithAd(null, encryptedMessage.payload)
                session.messageCount++

                val decryptedMessage = RawMessage(
                    messageId = encryptedMessage.messageId,
                    payload = plaintext,
                    senderAddress = remoteDid,
                    timestamp = encryptedMessage.timestamp
                )
                emit(decryptedMessage)
            } catch (e: SecurityException) {
                // 解密失败，不修改任何状态
                throw e
            }

            // 检查是否需要轮换
            if (session.needsRotation()) {
                rotateSession(remoteDid)
            }
        }
    }

    // ── 会话管理 ──

    /**
     * 获取或建立与远程 DID 的 Noise 会话。
     */
    private suspend fun getOrEstablishSession(remoteDid: String): NoiseSession {
        // 检查现有会话
        val existing = sessions[remoteDid]
        if (existing != null && !existing.isExpired() && !existing.needsRotation()) {
            return existing
        }

        return mutex.withLock {
            // 双重检查
            val rechecked = sessions[remoteDid]
            if (rechecked != null && !rechecked.isExpired() && !rechecked.needsRotation()) {
                return@withLock rechecked
            }

            // 获取对端静态公钥
            val remoteStaticPubKey = getRemoteStaticPublicKey(remoteDid)
                ?: throw IllegalStateException("无法获取远程 DID 的静态公钥: $remoteDid")

            // 执行 Noise 握手
            val session = establishSession(remoteDid, remoteStaticPubKey)
            sessions[remoteDid] = session
            session
        }
    }

    /**
     * 执行完整的 Noise 握手。
     */
    private suspend fun establishSession(
        remoteDid: String,
        remoteStaticPubKey: ByteArray
    ): NoiseSession {
        val pattern = when (communicationLevel) {
            CommunicationLevel.STANDARD -> NoiseProtocol.HandshakePattern.IK
            CommunicationLevel.PRIVATE -> NoiseProtocol.HandshakePattern.IK
            CommunicationLevel.SOVEREIGN -> NoiseProtocol.HandshakePattern.XK
        }

        val activeDid = identityManager.getActiveDid()
            ?: throw IllegalStateException("无活跃 DID")
        val localStaticKey = identityManager.getPrivateKey(activeDid)
            ?: throw IllegalStateException("无法获取私钥: $activeDid")

        val handshakeState = NoiseHandshakeState(
            pattern = pattern,
            isInitiator = true,
            localStaticKey = localStaticKey,
            remoteStaticPublicKey = remoteStaticPubKey
        )

        // 执行握手（消息交换通过内部传输层）
        // 简化实现：假设握手消息可以直接封装在 RawMessage 中
        val session = handshakeState.completeHandshake()

        // 存储对方的静态公钥
        knownPublicKeys[remoteDid] = remoteStaticPubKey

        return session
    }

    /**
     * 轮换会话。
     */
    private suspend fun rotateSession(remoteDid: String) {
        mutex.withLock {
            val oldSession = sessions.remove(remoteDid) ?: return@withLock
            // 清理旧会话的密钥
            oldSession.sendKey.fill(0)
            oldSession.receiveKey.fill(0)
            // 新会话会在下次通信时自动建立
        }
    }

    /**
     * 获取远程 DID 的静态公钥。
     *
     * 获取顺序：
     * 1. 本地缓存（knownPublicKeys）
     * 2. 应用预置白名单
     * 3. 禁止网络查询
     */
    private fun getRemoteStaticPublicKey(remoteDid: String): ByteArray? {
        // 1. 本地缓存
        knownPublicKeys[remoteDid]?.let { return it }

        // 2. 应用预置白名单
        val whiteListKey = PreConfiguredKeys.getPublicKey(remoteDid)
        if (whiteListKey != null) {
            knownPublicKeys[remoteDid] = whiteListKey
            return whiteListKey
        }

        // 3. 不通过网络查询
        return null
    }

    /**
     * 注册远程静态公钥（用于二维码/NFC 交换后注册）。
     */
    suspend fun registerRemotePublicKey(remoteDid: String, publicKey: ByteArray) {
        mutex.withLock {
            knownPublicKeys[remoteDid] = publicKey
        }
    }
}

/**
 * 预配置公钥白名单。
 */
object PreConfiguredKeys {
    private val whiteList = ConcurrentHashMap<String, ByteArray>()

    fun getPublicKey(did: String): ByteArray? {
        return whiteList[did]
    }

    fun registerPublicKey(did: String, publicKey: ByteArray) {
        whiteList[did] = publicKey
    }
}
