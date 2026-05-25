package com.sovexis.domain.communication

/**
 * 通信安全模式分级。
 *
 * 用户可在策略面板中选择，默认 C0（标准模式）。
 * 高级用户可自行升级至 C1/C2。
 */
enum class CommunicationLevel {
    /**
     * C0 - 标准模式（默认）
     * 使用 Noise_IK_25519_AESGCM_SHA256
     * 发起方静态公钥明文发送
     */
    STANDARD,

    /**
     * C1 - 隐私模式
     * 使用 Noise_IK_25519_AESGCM_SHA256
     * 每次会话生成新临时密钥对，不同会话间不可链接
     */
    PRIVATE,

    /**
     * C2 - 主权模式
     * 使用 Noise_XK_25519_AESGCM_SHA256
     * 发起方静态公钥加密发送，被动窃听者不可见
     */
    SOVEREIGN
}
