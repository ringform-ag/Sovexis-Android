package com.sovexis.domain.communication.covert

import java.security.SecureRandom
import java.util.UUID

/**
 * 虚拟事件注入器。
 *
 * 在真实消息之间随机穿插虚拟事件。虚拟事件的 DID 随机生成，不与任何真实身份关联。
 * 注入比例按用户分类继承。
 *
 * @param injectionRatio 注入比例（默认 20%，L0=10%, L1=20%, L2=30%）
 */
class VirtualEventInjector(
    private val injectionRatio: Double = 0.2
) {
    private val random = SecureRandom()

    init {
        require(injectionRatio in 0.0..1.0) {
            "注入比例必须在 0.0-1.0 范围内，实际: $injectionRatio"
        }
    }

    /**
     * 判断是否在当前位置注入虚拟事件。
     *
     * @return true 表示应该注入虚拟事件
     */
    fun shouldInject(): Boolean {
        return random.nextDouble() < injectionRatio
    }

    /**
     * 生成虚拟事件的 DID。
     *
     * @return 虚拟 DID 字符串
     */
    fun generateVirtualDid(): String {
        val randomHex = UUID.randomUUID().toString().replace("-", "")
        return "did:sovexis:virtual:${randomHex.substring(0, 32)}"
    }

    /**
     * 生成虚拟消息负载。
     *
     * @param size 负载大小（默认 128 bytes）
     * @return 随机字节数组
     */
    fun generateVirtualPayload(size: Int = 128): ByteArray {
        val payload = ByteArray(size)
        random.nextBytes(payload)
        return payload
    }

    /**
     * 获取当前注入比例。
     *
     * @return 注入比例
     */
    fun getInjectionRatio(): Double = injectionRatio

    companion object {
        /**
         * 根据用户级别获取默认注入比例。
         *
         * @param userLevel 用户级别（0=公开, 1=普通, 2=严格）
         * @return 默认注入比例
         */
        fun getDefaultRatioForUserLevel(userLevel: Int): Double {
            return when (userLevel) {
                0 -> 0.1  // L0 公开: 10%
                1 -> 0.2  // L1 普通: 20%
                2 -> 0.3  // L2 严格: 30%
                else -> 0.2
            }
        }

        /**
         * 获取用户可配置的最大注入比例。
         *
         * @param userLevel 用户级别（0=公开, 1=普通, 2=严格）
         * @return 最大注入比例
         */
        fun getMaxRatioForUserLevel(userLevel: Int): Double {
            return when (userLevel) {
                0 -> 0.1  // L0 不可配置
                1 -> 0.4  // L1 可上调至 40%
                2 -> 0.5  // L2 可上调至 50%
                else -> 0.2
            }
        }
    }
}
