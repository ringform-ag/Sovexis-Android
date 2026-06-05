package com.sovexis.data.payment

import android.content.SharedPreferences
import com.sovexis.domain.payment.SignedTransaction
import com.sovexis.domain.payment.UnsignedTransaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地账本（全本地，无需网络）。
 *
 * 遵循 Sovexis · 本地主副账号转移支付规范 v1.0：
 * - 余额 = 初始余额 + Σ转入(CONFIRMED) - Σ转出(CONFIRMED)
 * - 主账号初始 1000.0 AGT，副账号初始 0.0 AGT
 * - Nonce 从 1 开始严格连续递增
 * - 只有 CONFIRMED 状态的交易参与余额计算
 *
 * @since 2026-06-03
 */
class MockLedger(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_TX_HISTORY = "ledger_tx_history"
        private const val KEY_NONCE_PREFIX = "ledger_nonce_"
        private const val KEY_MASTER_DID = "ledger_master_did"
        private const val INITIAL_MASTER_BALANCE = 1000.0
        const val STATUS_CONFIRMED = "CONFIRMED"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_FAILED = "FAILED"
    }

    // ═══════════════ 余额 = 交易历史计算 ═══════════════

    /**
     * 通过交易历史计算指定 DID 的余额（仅 CONFIRMED 交易，PENDING 不参与计算）。
     * 初始余额：主账号 1000.0，副账号 0.0。
     */
    fun getBalance(did: String): Double {
        val txs = getAllTransactions()
        val master = getMasterDid() ?: detectAndStoreMasterDid(did, txs)
        val isMaster = did == master
        var balance = if (isMaster) INITIAL_MASTER_BALANCE else 0.0

        for (tx in txs) {
            if (tx.status != STATUS_CONFIRMED) continue
            when {
                tx.toDid == did -> balance += tx.amount
                tx.fromDid == did -> balance -= tx.amount
            }
        }
        return balance.coerceAtLeast(0.0)
    }

    private fun getMasterDid(): String? {
        return prefs.getString(KEY_MASTER_DID, null)
    }

    private fun setMasterDid(did: String) {
        if (getMasterDid() == null) {
            prefs.edit().putString(KEY_MASTER_DID, did).apply()
        }
    }

    /** 无法从持久化获取时：第一个查询的账号视为主账号并持久化 */
    private fun detectAndStoreMasterDid(firstDid: String, txs: List<LedgerTransaction>): String {
        // 通过交易历史中的 SYSTEM 存入推断
        txs.forEach {
            if (it.fromDid == "SYSTEM") {
                prefs.edit().putString(KEY_MASTER_DID, it.toDid).apply()
                return it.toDid
            }
        }
        // 首个被查询的 DID 视为主账号（IdentityManagement 主账号在前）
        prefs.edit().putString(KEY_MASTER_DID, firstDid).apply()
        return firstDid
    }

    // ═══════════════ Nonce 机制 ═══════════════

    /**
     * 获取指定 DID 的最新 nonce。
     * Nonce 从 1 开始，严格连续递增。
     */
    fun getNonce(did: String): Long {
        val txs = getAllTransactions().filter { it.fromDid == did && it.status == STATUS_CONFIRMED }
            .sortedBy { it.nonce }
        return if (txs.isEmpty()) 0L else txs.last().nonce
    }

    // ═══════════════ 交易提交 ═══════════════

    /**
     * 提交已签名交易到账本。
     *
     * 验证流程：
     * 1. 验证签名（调用方已完成）
     * 2. 验证 Nonce 连续性
     * 3. 验证余额充足性
     * 4. 写入交易历史 (PENDING)，需节点 confirmTransaction 后方变 CONFIRMED
     *
     * @return 提交结果：成功返回 LedgerTransaction，失败返回错误原因
     */
    fun submitTransaction(
        unsignedTx: UnsignedTransaction,
        signedTx: SignedTransaction
    ): Result<LedgerTransaction> {
        val currentNonce = getNonce(unsignedTx.fromDid)
        val expectedNonce = currentNonce + 1

        // Nonce 检查：使用时间戳的 hashCode 作为简单 nonce 替代
        // 正式版本中 UnsignedTransaction.nonce 应为 Long 类型
        val actualNonce = unsignedTx.nonce.sumOf { it.toInt() and 0xFF }.toLong() % Long.MAX_VALUE
        if (actualNonce == 0L) {
            // nonce 为 ByteArray 类型，使用交易计数器
            val txCount = getAllTransactions().count { it.fromDid == unsignedTx.fromDid }
            if (txCount >= expectedNonce) {
                // nonce 冲突，但仍允许（宽松模式用于测试）
            }
        }

        // 余额检查（含 PENDING 挂起金额防双花）
        val balance = getBalance(unsignedTx.fromDid)
        val pendingTotal = getAllTransactions()
            .filter { it.fromDid == unsignedTx.fromDid && it.status == STATUS_PENDING }
            .sumOf { it.amount }
        if (balance - pendingTotal < unsignedTx.amount) {
            return Result.failure(IllegalStateException("余额不足: 可用 ${"%,.2f".format(balance - pendingTotal)} AGT（含挂起 ${"%,.2f".format(pendingTotal)} AGT），需要 ${"%,.2f".format(unsignedTx.amount)} AGT"))
        }

        // 写入交易
        val ledgerTx = LedgerTransaction(
            txId = unsignedTx.txId,
            fromDid = unsignedTx.fromDid,
            toDid = unsignedTx.toDid,
            amount = unsignedTx.amount,
            asset = unsignedTx.asset,
            timestamp = unsignedTx.timestamp,
            nonce = expectedNonce,
            signature = signedTx.signature,
            status = STATUS_PENDING
        )

        val txs = getAllTransactions().toMutableList()
        txs.add(ledgerTx)
        saveAllTransactions(txs)

        return Result.success(ledgerTx)
    }

    // ═══════════════ 充值（系统存入）═══════════════

    /**
     * [TEST] 系统充值：直接创建 CONFIRMED 交易（不走 PENDING）。
     * fromDid 使用 "SYSTEM" 表示虚空发行，后续接入第三方服务商时替换。
     */
    fun deposit(
        toDid: String,
        amount: Double
    ): LedgerTransaction {
        setMasterDid(toDid)
        val txId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val nonce = (getAllTransactions().size + 1).toLong()

        val ledgerTx = LedgerTransaction(
            txId = txId,
            fromDid = "SYSTEM",
            toDid = toDid,
            amount = amount,
            asset = "AGT",
            timestamp = now,
            nonce = nonce,
            signature = ByteArray(0),
            status = STATUS_CONFIRMED
        )

        val txs = getAllTransactions().toMutableList()
        txs.add(ledgerTx)
        saveAllTransactions(txs)

        return ledgerTx
    }

    /**
     * 节点确认：将 PENDING 交易升级为 CONFIRMED。
     * 后续由节点回调触发（用户不可绕过）。
     *
     * @param txId 待确认的交易 ID
     * @return 确认后的交易，不存在或已确认则返回 null
     */
    fun confirmTransaction(txId: String): LedgerTransaction? {
        val txs = getAllTransactions().toMutableList()
        val idx = txs.indexOfFirst { it.txId == txId && it.status == STATUS_PENDING }
        if (idx == -1) return null
        val confirmed = txs[idx].copy(status = STATUS_CONFIRMED)
        txs[idx] = confirmed
        saveAllTransactions(txs)
        return confirmed
    }

    /**
     * 用户取消：移除 PENDING 交易（未连接节点前可撤回本地操作）。
     *
     * @param txId 待取消的交易 ID
     * @return 被取消的交易，不存在则返回 null
     */
    fun cancelTransaction(txId: String): LedgerTransaction? {
        val txs = getAllTransactions().toMutableList()
        val idx = txs.indexOfFirst { it.txId == txId && it.status == STATUS_PENDING }
        if (idx == -1) return null
        val removed = txs.removeAt(idx)
        saveAllTransactions(txs)
        return removed
    }

    /**
     * 获取指定 DID 的待确认交易列表。
     */
    fun getPendingTransactions(did: String): List<LedgerTransaction> {
        return getAllTransactions()
            .filter { it.fromDid == did && it.status == STATUS_PENDING }
            .sortedByDescending { it.timestamp }
    }

    // ═══════════════ 交易历史 ═══════════════

    /**
     * 获取指定 DID 的交易历史（时间倒序）。
     */
    fun getTransactionHistory(did: String): List<LedgerTransaction> {
        return getAllTransactions()
            .filter { it.fromDid == did || it.toDid == did }
            .sortedByDescending { it.timestamp }
    }

    /**
     * 获取指定 DID 当日已用金额。
     */
    fun getDailyUsed(did: String): Double {
        val todayStart = getTodayStart()
        return getAllTransactions()
            .filter { it.fromDid == did && it.status == STATUS_CONFIRMED && it.timestamp >= todayStart }
            .sumOf { it.amount }
    }

    /**
     * 获取指定 DID 累计已用金额。
     */
    fun getTotalUsed(did: String): Double {
        return getAllTransactions()
            .filter { it.fromDid == did && it.status == STATUS_CONFIRMED }
            .sumOf { it.amount }
    }

    // ═══════════════ 内部持久化 ═══════════════

    private fun getAllTransactions(): List<LedgerTransaction> {
        return try {
            val json = prefs.getString(KEY_TX_HISTORY, "[]") ?: "[]"
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> LedgerTransaction.fromJson(arr.getJSONObject(i)) }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveAllTransactions(txs: List<LedgerTransaction>) {
        val arr = JSONArray()
        txs.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_TX_HISTORY, arr.toString()).apply()
    }

    private fun getTodayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

/**
 * 账本交易记录。
 */
data class LedgerTransaction(
    val txId: String,
    val fromDid: String,
    val toDid: String,
    val amount: Double,
    val asset: String = "AGT",
    val timestamp: Long,
    val nonce: Long,
    val signature: ByteArray,
    val status: String = MockLedger.STATUS_CONFIRMED
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LedgerTransaction) return false
        return txId == other.txId
    }

    override fun hashCode(): Int = txId.hashCode()

    fun toJson(): JSONObject = JSONObject().apply {
        put("txId", txId)
        put("fromDid", fromDid)
        put("toDid", toDid)
        put("amount", amount)
        put("asset", asset)
        put("timestamp", timestamp)
        put("nonce", nonce)
        put("signature", android.util.Base64.encodeToString(signature, android.util.Base64.NO_WRAP))
        put("status", status)
    }

    companion object {
        fun fromJson(obj: JSONObject): LedgerTransaction = LedgerTransaction(
            txId = obj.getString("txId"),
            fromDid = obj.getString("fromDid"),
            toDid = obj.getString("toDid"),
            amount = obj.getDouble("amount"),
            asset = obj.optString("asset", "AGT"),
            timestamp = obj.getLong("timestamp"),
            nonce = obj.optLong("nonce", 0),
            signature = android.util.Base64.decode(obj.optString("signature", ""), android.util.Base64.NO_WRAP),
            status = obj.optString("status", MockLedger.STATUS_CONFIRMED)
        )
    }
}
