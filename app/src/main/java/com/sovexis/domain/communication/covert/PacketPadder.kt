package com.sovexis.domain.communication.covert

import java.security.SecureRandom

/**
 * 数据包填充器。
 *
 * 将所有消息（包括真实消息和虚拟消息）填充至固定大小。
 * 填充比例按用户分类动态调整。
 *
 * @param targetPacketSize 目标包大小（默认 512 bytes）
 * @param paddingRatio 填充比例（默认 20%，范围 0.1-0.3）
 */
class PacketPadder(
    private val targetPacketSize: Int = 512,
    private val paddingRatio: Double = 0.2  // 默认 20%
) {
    private val random = SecureRandom()

    init {
        require(paddingRatio in 0.1..0.3) {
            "填充比例必须在 0.1-0.3 范围内，实际: $paddingRatio"
        }
    }

    /**
     * 将消息填充至目标大小。
     *
     * @param data 原始消息数据
     * @return 填充后的字节数组
     * @throws IllegalArgumentException 如果消息大小超过目标包大小
     */
    fun pad(data: ByteArray): ByteArray {
        require(data.size <= targetPacketSize) {
            "消息大小 ${data.size} 超过目标包大小 $targetPacketSize"
        }
        val padded = ByteArray(targetPacketSize)
        System.arraycopy(data, 0, padded, 0, data.size)
        // 填充剩余字节为随机值
        val paddingLen = targetPacketSize - data.size
        if (paddingLen > 0) {
            val padding = ByteArray(paddingLen)
            random.nextBytes(padding)
            System.arraycopy(padding, 0, padded, data.size, paddingLen)
        }
        return padded
    }

    /**
     * 剥离填充，还原原始消息。
     *
     * @param padded 填充后的数据
     * @param originalSize 原始消息大小
     * @return 原始消息字节数组
     */
    fun unpad(padded: ByteArray, originalSize: Int): ByteArray {
        return padded.copyOfRange(0, originalSize)
    }
}
