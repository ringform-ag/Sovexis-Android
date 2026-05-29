package com.sovexis.tss.message

import com.sovexis.domain.crypto.MessageTransceiver
import com.sovexis.domain.crypto.TssMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 模拟通信传输实现
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 完整实现
 *
 * 基于内存队列的模拟传输，用于本地测试和演示。
 * 两个 MockTransceiver 实例可以通过 connectTo() 方法相互连接，
 * 模拟真实的网络通信，但数据仅在内存中传递。
 *
 * 使用方式：
 * ```kotlin
 * val alice = MockTransceiver()
 * val bob = MockTransceiver()
 * alice.connectTo(bob)  // 建立双向连接
 *
 * // 现在 alice 和 bob 可以互相发送/接收消息
 * ```
 */
class MockTransceiver : MessageTransceiver {

    private val mutex = Mutex()
    private var peer: MockTransceiver? = null
    private val incomingChannel = Channel<TssMessage>(Channel.UNLIMITED)
    private var isConnected = false

    /**
     * 连接到另一个 MockTransceiver 实例
     *
     * 建立双向连接，使双方可以互相通信。
     */
    suspend fun connectTo(other: MockTransceiver): Result<Unit> {
        mutex.withLock {
            if (isConnected) {
                return Result.failure(IllegalStateException("已经连接到其他实例"))
            }
            if (other == this) {
                return Result.failure(IllegalArgumentException("不能连接到自己"))
            }

            peer = other
            isConnected = true

            // 建立双向连接
            other.mutex.withLock {
                other.peer = this
                other.isConnected = true
            }

            return Result.success(Unit)
        }
    }

    override suspend fun send(message: TssMessage): Result<Unit> {
        mutex.withLock {
            if (!isConnected) {
                return Result.failure(IllegalStateException("未连接到其他实例"))
            }

            val peerInstance = peer
                ?: return Result.failure(IllegalStateException("对等端不存在"))

            // 将消息发送到对等端的接收队列
            val sent = peerInstance.incomingChannel.trySend(message)
            if (!sent.isSuccess) {
                return Result.failure(IllegalStateException("发送失败"))
            }

            return Result.success(Unit)
        }
    }

    override suspend fun receive(): Result<TssMessage> {
        if (!isConnected) {
            return Result.failure(IllegalStateException("未连接到其他实例"))
        }

        // 从接收队列获取消息（阻塞等待）
        val message = incomingChannel.receiveCatching()
        return if (message.isSuccess) {
            Result.success(message.getOrThrow())
        } else {
            Result.failure(message.exceptionOrNull() ?: IllegalStateException("接收失败"))
        }
    }

    override suspend fun isAvailable(): Boolean {
        return isConnected && !incomingChannel.isClosedForReceive
    }

    override suspend fun close() {
        mutex.withLock {
            isConnected = false
            incomingChannel.close()

            // 断开对等端
            peer?.mutex?.withLock {
                if (peer?.peer == this) {
                    peer?.isConnected = false
                    peer?.incomingChannel?.close()
                }
            }
            peer = null
        }
    }

    /**
     * 获取当前连接状态
     */
    fun isConnected(): Boolean = isConnected

    /**
     * 获取对等端实例（调试用）
     */
    suspend fun getPeer(): MockTransceiver? {
        return mutex.withLock { peer }
    }
}
