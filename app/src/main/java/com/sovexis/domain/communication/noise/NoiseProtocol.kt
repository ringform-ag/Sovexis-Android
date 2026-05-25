package com.sovexis.domain.communication.noise

/**
 * Noise 协议常量与模式定义。
 *
 * 参考：Noise Protocol Framework Revision 34
 * 实现模式：
 *   - Noise_IK_25519_AESGCM_SHA256 (C0 STANDARD / C1 PRIVATE)
 *   - Noise_XK_25519_AESGCM_SHA256 (C2 SOVEREIGN)
 *
 * 安全约束：
 *   - 不使用 PSK 模式（CVE-2026-24785 防御）
 *   - 不支持协议协商/降级切换（IK 回退攻击防御）
 *   - 固定使用 12 字节 AES-GCM IV
 */
object NoiseProtocol {

    /** DH 函数名称 */
    const val DH_FUNCTION = "25519"

    /** 对称加密算法 */
    const val CIPHER_FUNCTION = "AESGCM"

    /** 哈希函数 */
    const val HASH_FUNCTION = "SHA256"

    /** DH 公钥长度（字节） */
    const val DH_PUBLIC_KEY_LEN = 32

    /** DH 私钥长度（字节） */
    const val DH_PRIVATE_KEY_LEN = 32

    /** AES-GCM 密钥长度（字节） */
    const val AES_KEY_LEN = 32

    /** AES-GCM 认证标签长度（字节） */
    const val AES_TAG_LEN = 16

    /** AES-GCM IV / Nonce 长度（字节） */
    const val AES_NONCE_LEN = 12

    /** 哈希输出长度（字节） */
    const val HASH_LEN = 32

    /** 最大 nonce 值（Long.MAX_VALUE，之后必须重建会话） */
    const val MAX_NONCE: Long = Long.MAX_VALUE

    /** 强制会话轮换的消息数（CVE-2021-4239 防御） */
    const val SESSION_ROTATION_MESSAGES: Long = 1000

    /** 协议名称前缀 */
    const val PROTOCOL_PREFIX = "Noise"

    /** 支持的握手模式 */
    enum class HandshakePattern(val protocolName: String) {
        /** IK 模式：发起方明文发送静态公钥 */
        IK("IK"),
        /** XK 模式：发起方静态公钥加密发送 */
        XK("XK")
    }

    /**
     * 构建完整协议名称
     * 格式：Noise_<pattern>_<dh>_<cipher>_<hash>
     */
    fun protocolName(pattern: HandshakePattern): String {
        return "${PROTOCOL_PREFIX}_${pattern.protocolName}_${DH_FUNCTION}_${CIPHER_FUNCTION}_${HASH_FUNCTION}"
    }

    /** IK 模式的完整协议名称 */
    const val IK_PROTOCOL_NAME = "Noise_IK_25519_AESGCM_SHA256"

    /** XK 模式的完整协议名称 */
    const val XK_PROTOCOL_NAME = "Noise_XK_25519_AESGCM_SHA256"
}
