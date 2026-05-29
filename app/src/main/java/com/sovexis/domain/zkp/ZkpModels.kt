package com.sovexis.domain.zkp

import java.util.UUID

/**
 * ZKP 证明接口。
 */
interface ZkpProof {
    val proofId: String
    val proofBytes: ByteArray?
}

/**
 * ZKP 证明请求（由调用方构造）
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-21
 * 实现状态: ✅ 已完成
 * 参考文档: Sovexis · ZKP 模块完整实现指令 (陵谦)
 */
data class ZkpProveRequest(
    val biometricSignature: ByteArray,     // BiometricPrompt 通过后 Keystore 的 ECDSA 签名
    val deviceBindingData: ByteArray,      // 设备绑定信息（Android ID + Keystore 设备签名）
    val kdfsPatternHash: ByteArray,        // KDFS 图案的 SHA-256
    val sessionNonce: ByteArray,           // 服务商提供的 32 字节随机数
    val publicKeyPem: String,              // ECDSA P-256 公钥 PEM
    val expectedCommitmentRoot: ByteArray  // 注册时存储的 SHA256(bio || dev || kdfs)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZkpProveRequest) return false
        return biometricSignature.contentEquals(other.biometricSignature) &&
                deviceBindingData.contentEquals(other.deviceBindingData) &&
                kdfsPatternHash.contentEquals(other.kdfsPatternHash) &&
                sessionNonce.contentEquals(other.sessionNonce) &&
                publicKeyPem == other.publicKeyPem &&
                expectedCommitmentRoot.contentEquals(other.expectedCommitmentRoot)
    }

    override fun hashCode(): Int {
        var result = biometricSignature.contentHashCode()
        result = 31 * result + deviceBindingData.contentHashCode()
        result = 31 * result + kdfsPatternHash.contentHashCode()
        result = 31 * result + sessionNonce.contentHashCode()
        result = 31 * result + publicKeyPem.hashCode()
        result = 31 * result + expectedCommitmentRoot.contentHashCode()
        return result
    }
}

/**
 * ZKP 证明结果（内部数据类）
 * 实现 com.sovexis.domain.payment.ZkpProof 接口
 */
data class ZkpProofData(
    override val proofId: String = UUID.randomUUID().toString(),
    override val proofBytes: ByteArray,             // Groth16 证明（约 128 bytes）
    val publicInputs: List<String>,        // 公开输入（session_nonce, public_key, expected_root）
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,           // 过期时间（用于缓存场景）
    val riskLabel: String = "CLEAN"        // 风险标签：CLEAN 或 RISK_ROOTED
) : ZkpProof {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZkpProofData) return false
        return proofId == other.proofId &&
                proofBytes.contentEquals(other.proofBytes) &&
                publicInputs == other.publicInputs &&
                createdAt == other.createdAt &&
                expiresAt == other.expiresAt &&
                riskLabel == other.riskLabel
    }

    override fun hashCode(): Int {
        var result = proofId.hashCode()
        result = 31 * result + proofBytes.contentHashCode()
        result = 31 * result + publicInputs.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (expiresAt?.hashCode() ?: 0)
        result = 31 * result + riskLabel.hashCode()
        return result
    }
}

/**
 * ZKP 验证请求（由验证方发送）
 */
data class ZkpVerifyRequest(
    val proofBytes: ByteArray,
    val publicInputs: List<String>,
    val verificationKey: ByteArray         // Groth16 验证密钥
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZkpVerifyRequest) return false
        return proofBytes.contentEquals(other.proofBytes) &&
                publicInputs == other.publicInputs &&
                verificationKey.contentEquals(other.verificationKey)
    }

    override fun hashCode(): Int {
        var result = proofBytes.contentHashCode()
        result = 31 * result + publicInputs.hashCode()
        result = 31 * result + verificationKey.contentHashCode()
        return result
    }
}

/**
 * ZKP 验证结果
 */
sealed class ZkpVerifyResult {
    object Valid : ZkpVerifyResult()
    data class Invalid(val reason: String) : ZkpVerifyResult()
}

/**
 * ZKP 证明缓存条目
 */
data class ZkpCacheEntry(
    val proofId: String,
    val proof: ZkpProof,
    val credentialType: String,            // 凭证类型（用于缓存键）
    val cachedAt: Long = System.currentTimeMillis()
)
