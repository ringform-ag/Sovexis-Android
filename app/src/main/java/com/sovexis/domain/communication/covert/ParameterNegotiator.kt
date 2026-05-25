package com.sovexis.domain.communication.covert

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 隐蔽传输参数。
 *
 * @param version 参数版本
 * @param padding_ratio 填充比例
 * @param packet_size 包大小
 * @param camouflage_level 伪装级别（chrome/firefox/none）
 * @param fragmentation 是否启用分片
 * @param injection_ratio 虚拟事件注入比例
 */
@Serializable
data class CovertParameters(
    val version: Int = 1,
    val padding_ratio: Double = 0.2,
    val packet_size: Int = 512,
    val camouflage_level: String = "chrome",
    val fragmentation: Boolean = false,
    val injection_ratio: Double = 0.2
)

/**
 * 动态参数协商器。
 *
 * 在 Noise 握手完成后，通过加密通道协商伪装参数。
 * 协商数据包格式为 JSON，大小 < 256 bytes。
 *
 * @param timeoutMs 协商超时（默认 5000ms）
 * @param maxRetries 最大重试次数（默认 2）
 */
class ParameterNegotiator(
    private val timeoutMs: Long = 5000,
    private val maxRetries: Int = 2
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = false
    }

    /**
     * 序列化参数为字节数组。
     *
     * @param params 隐蔽传输参数
     * @return JSON 字节数组
     */
    fun serializeParameters(params: CovertParameters): ByteArray {
        return json.encodeToString(CovertParameters.serializer(), params).toByteArray()
    }

    /**
     * 反序列化字节数组为参数。
     *
     * @param data JSON 字节数组
     * @return 隐蔽传输参数，解析失败返回 null
     */
    fun deserializeParameters(data: ByteArray): CovertParameters? {
        return try {
            json.decodeFromString(CovertParameters.serializer(), String(data))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 根据用户级别获取默认参数。
     *
     * @param userLevel 用户级别（0=公开, 1=普通, 2=严格）
     * @return 默认参数
     */
    fun getDefaultParameters(userLevel: Int): CovertParameters {
        return when (userLevel) {
            0 -> CovertParameters(padding_ratio = 0.1, injection_ratio = 0.1)
            1 -> CovertParameters(padding_ratio = 0.2, injection_ratio = 0.2)
            2 -> CovertParameters(padding_ratio = 0.3, injection_ratio = 0.3)
            else -> CovertParameters()
        }
    }

    /**
     * 获取保守参数（协商失败时使用）。
     *
     * @return 保守参数
     */
    fun getConservativeParameters(): CovertParameters {
        return CovertParameters(
            padding_ratio = 0.3,
            packet_size = 512,
            camouflage_level = "chrome",
            fragmentation = true,
            injection_ratio = 0.3
        )
    }

    /**
     * 获取协商超时时间。
     *
     * @return 超时时间（毫秒）
     */
    fun getTimeoutMs(): Long = timeoutMs

    /**
     * 获取最大重试次数。
     *
     * @return 最大重试次数
     */
    fun getMaxRetries(): Int = maxRetries
}
