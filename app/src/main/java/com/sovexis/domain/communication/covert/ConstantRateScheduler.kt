package com.sovexis.domain.communication.covert

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.security.SecureRandom
import kotlin.math.roundToLong

/**
 * 恒定速率调度器。
 *
 * 无论有无真实数据需要发送，以固定频率（默认 50ms）向外发送数据包。
 * 真实数据到达时，替换下一个调度槽位。空闲时发送填充包。
 *
 * @param baseIntervalMs 基础发送间隔（默认 50ms）
 * @param jitterMs 随机抖动范围（默认 ±5ms）
 * @param packetSize 填充包大小（默认 512 bytes）
 */
class ConstantRateScheduler(
    private val baseIntervalMs: Long = 50,
    private val jitterMs: Long = 5,
    private val packetSize: Int = 512
) {
    private val random = SecureRandom()
    private val packetChannel = Channel<ByteArray>(Channel.BUFFERED)
    private var running = false
    private var job: Job? = null

    /**
     * 启动调度器。
     *
     * @param scope 协程作用域
     */
    fun start(scope: CoroutineScope) {
        if (running) return
        running = true
        job = scope.launch(Dispatchers.IO) {
            while (isActive && running) {
                // 尝试获取真实数据，若没有则生成填充包
                val packet = packetChannel.tryReceive().getOrNull() ?: generatePaddingPacket()
                // 发送 packet（由 CovertTransport 调用 innerTransport.send）
                val delay = baseIntervalMs + (random.nextDouble() * 2 - 1) * jitterMs
                delay(delay.roundToLong())
            }
        }
    }

    /**
     * 停止调度器。
     */
    fun stop() {
        running = false
        job?.cancel()
        packetChannel.close()
    }

    /**
     * 将真实数据包插入调度队列，替换下一个槽位。
     *
     * @param data 真实数据包
     */
    suspend fun enqueueRealPacket(data: ByteArray) {
        packetChannel.send(data)
    }

    /**
     * 生成随机填充包。
     *
     * @return 随机填充字节数组
     */
    private fun generatePaddingPacket(): ByteArray {
        val padding = ByteArray(packetSize)
        random.nextBytes(padding)
        return padding
    }
}
