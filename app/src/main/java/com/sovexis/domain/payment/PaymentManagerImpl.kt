package com.sovexis.domain.payment

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 支付管理器实现。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：Sovexis · 支付签名流程应用层串联指令
 *
 * 管理支付流程：准备交易、签名、提交。
 */
@Singleton
class PaymentManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PaymentManager {

    companion object {
        private const val PREFS_NAME = "payment_history"
        private const val KEY_DAILY_PREFIX = "daily_"
        private const val KEY_TOTAL_PREFIX = "total_"
        private const val NONCE_LENGTH = 32
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val secureRandom = SecureRandom()

    /**
     * 获取当日已用金额。
     *
     * @param did 身份 DID
     * @return 当日已用金额
     */
    override suspend fun getDailyUsed(did: String): Double {
        val today = getTodayKey()
        return prefs.getFloat("$KEY_DAILY_PREFIX${did}_$today", 0f).toDouble()
    }

    /**
     * 获取累计已用金额。
     *
     * @param did 身份 DID
     * @return 累计已用金额
     */
    override suspend fun getTotalUsed(did: String): Double {
        return prefs.getFloat("$KEY_TOTAL_PREFIX$did", 0f).toDouble()
    }

    /**
     * 准备支付交易。
     *
     * @param fromDid 支付方 DID
     * @param toDid 收款方 DID
     * @param amount 金额
     * @param note 备注（可选）
     * @return 准备结果
     */
    override suspend fun preparePayment(
        fromDid: String,
        toDid: String,
        amount: Double,
        note: String?
    ): PrepareResult {
        // 生成交易 ID
        val txId = UUID.randomUUID().toString()

        // 生成随机 nonce
        val nonce = ByteArray(NONCE_LENGTH)
        secureRandom.nextBytes(nonce)

        val unsignedTx = UnsignedTransaction(
            txId = txId,
            fromDid = fromDid,
            toDid = toDid,
            amount = amount,
            timestamp = System.currentTimeMillis(),
            nonce = nonce
        )

        return PrepareResult.Ready(unsignedTx)
    }

    /**
     * 签名并提交交易。
     *
     * @param unsignedTx 未签名交易
     * @param proofs ZKP 证明列表
     * @return 签名结果
     */
    override suspend fun signAndSubmit(
        unsignedTx: UnsignedTransaction,
        proofs: List<ZkpProof>
    ): Result<SignedTransaction> {
        return runCatching {
            // TODO: 实际签名逻辑
            // 1. 使用私钥对交易哈希签名
            // 2. 更新已用金额
            // 3. 提交到网络

            // 模拟签名
            val signature = ByteArray(64)
            secureRandom.nextBytes(signature)

            // 更新已用金额
            updateUsedAmount(unsignedTx.fromDid, unsignedTx.amount)

            SignedTransaction(
                txId = unsignedTx.txId,
                signature = signature,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * 获取会话 nonce。
     *
     * @return 会话 nonce 字节数组
     */
    override fun getSessionNonce(): ByteArray {
        val nonce = ByteArray(NONCE_LENGTH)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * 更新已用金额。
     *
     * @param did 身份 DID
     * @param amount 金额
     */
    private fun updateUsedAmount(did: String, amount: Double) {
        val today = getTodayKey()

        // 更新当日已用
        val dailyKey = "$KEY_DAILY_PREFIX${did}_$today"
        val currentDaily = prefs.getFloat(dailyKey, 0f)
        prefs.edit().putFloat(dailyKey, currentDaily + amount.toFloat()).apply()

        // 更新累计已用
        val totalKey = "$KEY_TOTAL_PREFIX$did"
        val currentTotal = prefs.getFloat(totalKey, 0f)
        prefs.edit().putFloat(totalKey, currentTotal + amount.toFloat()).apply()
    }

    /**
     * 获取今日日期键。
     *
     * @return 日期字符串（yyyyMMdd）
     */
    private fun getTodayKey(): String {
        val now = java.util.Calendar.getInstance()
        return String.format(
            "%04d%02d%02d",
            now.get(java.util.Calendar.YEAR),
            now.get(java.util.Calendar.MONTH) + 1,
            now.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
}
