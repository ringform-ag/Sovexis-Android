package com.sovexis.domain.payment

import com.sovexis.domain.zkp.ZkpProof

/**
 * 支付管理器接口。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：Sovexis · 支付签名流程应用层串联指令
 *
 * 管理支付流程：准备交易、签名、提交。
 */
interface PaymentManager {
    /**
     * 获取当日已用金额。
     *
     * @param did 身份 DID
     * @return 当日已用金额
     */
    suspend fun getDailyUsed(did: String): Double

    /**
     * 获取累计已用金额。
     *
     * @param did 身份 DID
     * @return 累计已用金额
     */
    suspend fun getTotalUsed(did: String): Double

    /**
     * 准备支付交易。
     *
     * @param fromDid 支付方 DID
     * @param toDid 收款方 DID
     * @param amount 金额
     * @param note 备注（可选）
     * @return 准备结果
     */
    suspend fun preparePayment(
        fromDid: String,
        toDid: String,
        amount: Double,
        note: String?
    ): PrepareResult

    /**
     * 签名并提交交易。
     *
     * @param unsignedTx 未签名交易
     * @param proofs ZKP 证明列表
     * @return 签名结果
     */
    suspend fun signAndSubmit(
        unsignedTx: UnsignedTransaction,
        proofs: List<ZkpProof>
    ): Result<SignedTransaction>

    /**
     * 获取会话 nonce。
     *
     * @return 会话 nonce 字节数组
     */
    fun getSessionNonce(): ByteArray
}

/**
 * 准备结果。
 */
sealed class PrepareResult {
    /**
     * 准备就绪。
     *
     * @param unsignedTx 未签名交易
     */
    data class Ready(val unsignedTx: UnsignedTransaction) : PrepareResult()

    /**
     * 被拒绝。
     *
     * @param reason 拒绝原因
     * @param code 错误代码
     */
    data class Denied(val reason: String, val code: String) : PrepareResult()
}

/**
 * 未签名交易。
 *
 * @param txId 交易 ID
 * @param fromDid 支付方 DID
 * @param toDid 收款方 DID
 * @param amount 金额
 * @param asset 资产类型
 * @param timestamp 时间戳
 * @param nonce 随机数
 */
data class UnsignedTransaction(
    val txId: String,
    val fromDid: String,
    val toDid: String,
    val amount: Double,
    val asset: String = "AGT",
    val timestamp: Long,
    val nonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnsignedTransaction) return false
        return txId == other.txId
    }

    override fun hashCode(): Int = txId.hashCode()
}

/**
 * 签名交易。
 *
 * @param txId 交易 ID
 * @param signature 签名
 * @param timestamp 时间戳
 */
data class SignedTransaction(
    val txId: String,
    val signature: ByteArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedTransaction) return false
        return txId == other.txId
    }

    override fun hashCode(): Int = txId.hashCode()

    /**
     * 转换为字节数组。
     */
    fun toByteArray(): ByteArray {
        return "$txId|$timestamp".toByteArray(Charsets.UTF_8) + signature
    }
}
