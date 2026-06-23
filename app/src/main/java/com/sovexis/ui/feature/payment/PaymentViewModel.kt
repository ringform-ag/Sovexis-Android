@file:Suppress("all")

package com.sovexis.ui.feature.payment

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.di.EncryptedPrefs
import com.sovexis.domain.crypto.ThresholdSignatureService
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.core.result.getOrNull
import com.sovexis.domain.payment.PaymentManager
import com.sovexis.domain.payment.PrepareResult
import com.sovexis.domain.zkp.ZkpProof
import com.sovexis.domain.policy.PolicyEnforcer
import com.sovexis.domain.policy.PolicyCheckResult
import com.sovexis.domain.communication.RawMessage
import com.sovexis.domain.communication.CryptoCommLayer
import com.sovexis.domain.communication.WebSocketManager
import com.sovexis.domain.credential.CredentialIssuer
import com.sovexis.domain.zkp.RootDetector
import com.sovexis.domain.zkp.ZkpCacheManager
import com.sovexis.domain.zkp.ZkpService
import com.sovexis.ui.components.TransactionNotification
import com.sovexis.ui.components.TransactionNotificationHolder
import com.sovexis.ui.components.TxNotifyStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Arrays
import javax.inject.Inject

/**
 * 收款账号池条目 — 用于下拉栏展示。
 *
 * @param did 完整 DID
 * @param alias 别名（本地账号）或空
 * @param isLocal 是否为本地账号
 * @param lastTxTime 最后一次交易时间（仅非本地账号）
 */
data class RecipientEntry(
    val did: String,
    val alias: String? = null,
    val isLocal: Boolean = false,
    val lastTxTime: Long? = null
)

/**
 * 支付流程步骤。
 */
enum class PaymentStep {
    /** 空闲状态 */
    IDLE,

    /** 策略检查中 */
    POLICY_CHECK,

    /** 高风险弹窗（仅 TSS 模式） */
    HIGH_RISK_DIALOG,

    /** 生物认证 */
    BIOMETRIC_PROMPT,

    /** KDFS 图案绘制 */
    KDFS_DRAW,

    /** ZKP 证明生成中 */
    ZKP_GENERATING,

    /** 签名中 */
    SIGNING,

    /** 发送中 */
    SENDING,

    /** 已提交待确认（离线挂起，等待节点共识） */
    SUBMITTED_PENDING,

    /** 失败 */
    FAILED
}

/**
 * 支付状态。
 *
 * @param step 当前步骤
 * @param amount 金额
 * @param fromDid 支付方 DID
 * @param toDid 收款方 DID
 * @param riskRounds 用户选择的混淆轮次
 * @param currentRound 当前执行的第几轮
 * @param zkpResults ZKP 证明列表
 * @param isLoading 是否加载中
 * @param error 错误信息
 * @param txId 交易 ID
 */
data class PaymentState(
    val step: PaymentStep = PaymentStep.IDLE,
    val amount: Double = 0.0,
    val fromDid: String = "",
    val toDid: String = "",
    val riskRounds: Int = 1,
    val currentRound: Int = 0,
    val zkpResults: List<ZkpProof> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val txId: String? = null,
    val pendingAmount: Double = 0.0,
    val isDeviceRooted: Boolean = false,
    // 账本查询余额（仅 CONFIRMED 交易计算）
    val balance: Double = 1000.0,
    // [TEST] 充值弹窗是否显示
    val showRechargeDialog: Boolean = false,
    // 高风险弹窗"下次不再提醒"
    val skipHighRiskExplanation: Boolean = false,
    // 支付方本地账号池
    val fromAccounts: List<SovexisAccount> = emptyList(),
    // 收款方账号池（本地 + 已保存的非本地）
    val toAccounts: List<RecipientEntry> = emptyList(),
    // 已保存的非本地收款账号
    val savedRecipients: List<RecipientEntry> = emptyList(),
    // 支付方下拉
    val showFromDropdown: Boolean = false,
    // 收款方下拉
    val showToDropdown: Boolean = false,
    // 收款方非本地时,勾选保存为常用收款账号
    val saveAsRecipient: Boolean = false,
    // 收款账号池管理弹窗
    val showRecipientManager: Boolean = false,
    // 输入的收款方 DID（用于非本地输入）
    val manualToDid: String = "",
    // 本地账号余额映射
    val accountBalances: Map<String, Double> = emptyMap(),
    // SYNC-004 P1: 交易确认状态
    val txVerifyStatus: String? = null,  // "confirmed" / "unverified"
    val verifyMessage: String? = null
)

/**
 * ZKP 证明包装器。实现 ZkpProof 接口。
 */
data class ZkpProofWrapper(
    override val proofId: String,
    override val proofBytes: ByteArray?
) : ZkpProof {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZkpProofWrapper) return false
        return proofId == other.proofId
    }

    override fun hashCode(): Int = proofId.hashCode()
}

/**
 * 支付 ViewModel。
 *
 * 管理支付签名的完整流程（标准模式 + 高安全模式）。
 */
@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentManager: PaymentManager,
    private val policyEnforcer: PolicyEnforcer,
    private val identityManager: IdentityManager,
    private val zkpService: ZkpService,
    private val zkpCacheManager: ZkpCacheManager,
    private val cryptoCommLayer: CryptoCommLayer,
    private val wsManager: WebSocketManager,
    private val credentialIssuer: CredentialIssuer,
    @EncryptedPrefs private val encryptedPrefs: SharedPreferences,
    // TSS（可选，仅主账号可用）
    private val tssService: ThresholdSignatureService? = null
) : ViewModel() {

    private val _state = MutableStateFlow(
        PaymentState(skipHighRiskExplanation = encryptedPrefs.getBoolean(KEY_SKIP_RISK, false))
    )
    val state: StateFlow<PaymentState> = _state.asStateFlow()

    private var storedBiometricSignature: ByteArray? = null

    companion object {
        private const val KEY_SKIP_RISK = "high_risk_skip_explanation"
        private const val KEY_SAVED_RECIPIENTS = "saved_recipients"
    }

    init {
        loadAccountsAndRecipients()
        registerTransactionCallback()
    }

    private fun registerTransactionCallback() {
        wsManager.setOnTransactionConfirmed { credJson ->
            viewModelScope.launch {
                val verified = withContext(Dispatchers.IO) {
                    credentialIssuer.verifyTransactionConfirmation(credJson)
                }
                if (verified) {
                    _state.value = _state.value.copy(
                        txVerifyStatus = "confirmed",
                        verifyMessage = "交易已确认"
                    )
                    // 刷新余额
                    val did = _state.value.fromDid
                    if (did.isNotBlank()) refreshBalanceForDid(did)
                } else {
                    _state.value = _state.value.copy(
                        txVerifyStatus = "unverified",
                        verifyMessage = "确认异常，请查看详情"
                    )
                }
            }
        }
    }

    private fun refreshBalanceForDid(did: String) {
        refreshBalances(did)
    }

    /** 原子刷新：单一协程内同时更新 balance/pendingAmount/accountBalances，消除竞态 */
    private fun refreshBalances(selectedDid: String) {
        viewModelScope.launch {
            try {
                val bal = withContext(Dispatchers.IO) { paymentManager.getBalance(selectedDid) }
                val pending = withContext(Dispatchers.IO) { paymentManager.getPendingAmount(selectedDid) }
                val balances = mutableMapOf<String, Double>()
                _state.value.fromAccounts.forEach { acc ->
                    try { balances[acc.did] = withContext(Dispatchers.IO) { paymentManager.getBalance(acc.did) } } catch (_: Exception) {}
                }
                _state.value = _state.value.copy(balance = bal, pendingAmount = pending, accountBalances = balances)
            } catch (_: Exception) {}
        }
    }

    private fun loadAccountsAndRecipients() {
        viewModelScope.launch {
            try {
                val result = identityManager.getAllIdentities()
                val accounts = result.getOrNull() ?: emptyList()
                val saved = loadSavedRecipients()
                val toPool = buildToAccounts(accounts, saved)
                // 加载本地账号余额
                val balances = mutableMapOf<String, Double>()
                accounts.forEach { acc ->
                    try { balances[acc.did] = withContext(Dispatchers.IO) { paymentManager.getBalance(acc.did) } } catch (_: Exception) {}
                }
                _state.value = _state.value.copy(
                    fromAccounts = accounts,
                    toAccounts = toPool,
                    savedRecipients = saved,
                    accountBalances = balances
                )
                // 恢复挂起金额（PENDING 交易持久化在账本中）
                val firstDid = accounts.firstOrNull()?.did
                if (firstDid != null) refreshBalanceForDid(firstDid)
            } catch (_: Exception) {}
        }
    }

    private fun buildToAccounts(local: List<SovexisAccount>, saved: List<RecipientEntry>): List<RecipientEntry> {
        val list = mutableListOf<RecipientEntry>()
        local.forEach { list.add(RecipientEntry(it.did, it.alias, isLocal = true)) }
        saved.forEach { list.add(it) }
        return list
    }

    private fun loadSavedRecipients(): List<RecipientEntry> {
        return try {
            val json = encryptedPrefs.getString(KEY_SAVED_RECIPIENTS, "[]") ?: "[]"
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RecipientEntry(
                    did = obj.getString("did"),
                    alias = obj.optString("alias", null),
                    isLocal = false,
                    lastTxTime = if (obj.has("lastTxTime")) obj.getLong("lastTxTime") else null
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun persistSavedRecipients() {
        val arr = JSONArray()
        _state.value.savedRecipients.forEach {
            val obj = JSONObject()
            obj.put("did", it.did)
            it.alias?.let { a -> obj.put("alias", a) }
            it.lastTxTime?.let { t -> obj.put("lastTxTime", t) }
            arr.put(obj)
        }
        encryptedPrefs.edit().putString(KEY_SAVED_RECIPIENTS, arr.toString()).apply()
    }

    // ========== 下拉栏 ==========

    fun toggleFromDropdown() {
        _state.value = _state.value.copy(
            showFromDropdown = !_state.value.showFromDropdown,
            showToDropdown = false
        )
    }

    fun toggleToDropdown() {
        _state.value = _state.value.copy(
            showToDropdown = !_state.value.showToDropdown,
            showFromDropdown = false
        )
    }

    fun selectFromAccount(did: String) {
        // 不可选为收款方相同的 DID
        if (did == _state.value.toDid) return
        _state.value = _state.value.copy(fromDid = did, showFromDropdown = false)
        try {
            refreshBalanceForDid(did)
        } catch (_: Exception) {}
    }

    fun selectToAccount(did: String) {
        // 不可选为支付方相同的 DID
        if (did == _state.value.fromDid) return
        val isLocal = _state.value.savedRecipients.none { it.did == did }
            && _state.value.fromAccounts.any { it.did == did }
        _state.value = _state.value.copy(
            toDid = did, showToDropdown = false,
            // 非本地账号显示保存勾选框
            saveAsRecipient = !isLocal
        )
    }

    fun updateManualToDid(did: String) {
        val isLocal = _state.value.fromAccounts.any { it.did == did }
            || _state.value.savedRecipients.any { it.did == did }
        _state.value = _state.value.copy(
            manualToDid = did,
            toDid = did,
            saveAsRecipient = did.isNotBlank() && !isLocal
        )
    }

    fun toggleSaveAsRecipient() {
        _state.value = _state.value.copy(saveAsRecipient = !_state.value.saveAsRecipient)
    }

    /** 支付成功后保存非本地收款账号 */
    private fun maybeSaveRecipient() {
        if (!_state.value.saveAsRecipient) return
        val toDid = _state.value.toDid
        val isLocal = _state.value.fromAccounts.any { it.did == toDid }
        if (isLocal || _state.value.savedRecipients.any { it.did == toDid }) return
        val updated = _state.value.savedRecipients + RecipientEntry(
            did = toDid, isLocal = false, lastTxTime = System.currentTimeMillis()
        )
        _state.value = _state.value.copy(savedRecipients = updated,
            toAccounts = buildToAccounts(_state.value.fromAccounts, updated))
        persistSavedRecipients()
    }

    // ========== 收款账号池管理 ==========

    fun toggleRecipientManager() {
        _state.value = _state.value.copy(showRecipientManager = !_state.value.showRecipientManager)
    }

    fun deleteSavedRecipient(did: String) {
        val updated = _state.value.savedRecipients.filter { it.did != did }
        _state.value = _state.value.copy(
            savedRecipients = updated,
            toAccounts = buildToAccounts(_state.value.fromAccounts, updated)
        )
        persistSavedRecipients()
    }

    /**
     * 步骤 1：用户输入支付信息后，执行策略检查。
     *
     * @param fromDid 支付方 DID
     * @param toDid 收款方 DID
     * @param amount 金额
     */
    fun initiatePayment(fromDid: String, toDid: String, amount: Double) {
        // 支付方与收款方不能相同
        if (fromDid == toDid) {
            _state.value = _state.value.copy(
                step = PaymentStep.FAILED,
                error = "支付方和收款方不能是同一个账号"
            )
            return
        }

        // 余额不足检查（含 PENDING 挂起金额）
        val available = _state.value.balance - _state.value.pendingAmount
        if (amount > available && _state.value.balance > 0) {
            _state.value = _state.value.copy(
                step = PaymentStep.FAILED,
                error = "余额不足: 可用 ${"%,.2f".format(available)} AGT（挂起 ${"%,.2f".format(_state.value.pendingAmount)} AGT），需要 ${"%,.2f".format(amount)} AGT"
            )
            return
        }

        // 检测 Root 状态
        val isRooted = try { RootDetector.isDeviceRooted() } catch (_: Exception) { false }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                fromDid = fromDid,
                toDid = toDid,
                amount = amount,
                step = PaymentStep.POLICY_CHECK,
                isLoading = true,
                isDeviceRooted = isRooted
            )

            try {
                // 策略不存在时自动创建默认策略
                ensureDefaultPolicy(fromDid)

                val dailyUsed = paymentManager.getDailyUsed(fromDid)
                val totalUsed = paymentManager.getTotalUsed(fromDid)

                val policyResult = policyEnforcer.checkPayment(
                    fromDid = fromDid,
                    toDid = toDid,
                    amount = amount,
                    asset = "AGT",
                    dailyUsed = dailyUsed,
                    totalUsed = totalUsed
                )

                when (policyResult) {
                    is PolicyCheckResult.Allowed -> {
                        // 检查副账号是否被熔断
                        val frozenResult = policyEnforcer.checkFrozen(fromDid)
                        if (frozenResult is PolicyCheckResult.Denied) {
                            _state.value = _state.value.copy(
                                isLoading = false, step = PaymentStep.FAILED,
                                error = frozenResult.reason
                            )
                            return@launch
                        }
                        // 高风险弹窗触发条件：设备已 Root → 始终弹窗警告
                        // TSS 可用时额外启用多轮混淆，无 TSS 则仅单轮确认
                        val needsHighRiskDialog = isRooted || tssService != null
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = if (needsHighRiskDialog) PaymentStep.HIGH_RISK_DIALOG
                            else PaymentStep.BIOMETRIC_PROMPT
                        )
                    }

                    is PolicyCheckResult.Denied -> {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = PaymentStep.FAILED,
                            error = policyResult.reason
                        )
                    }
                }
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = PaymentStep.FAILED,
                    error = "策略检查失败: ${e.message}"
                )
            }
        }
    }

    private suspend fun ensureDefaultPolicy(did: String) {
        try {
            if (policyEnforcer.getPolicy(did) != null) return
            val default = com.sovexis.domain.policy.PolicyConfig(
                boundChildDid = did,
                payment = com.sovexis.domain.policy.PaymentPolicy(
                    perTxLimit = 10000.0,
                    dailyLimit = 100000.0,
                    totalLimit = 1000000.0,
                    allowedAssets = listOf("AGT")
                ),
                vault = com.sovexis.domain.policy.VaultPolicy(allowRead = true, allowWrite = true, allowDelete = true)
            )
            policyEnforcer.savePolicy(default)
        } catch (_: Exception) {
            // 策略保存失败不阻断支付流程
        }
    }

    /**
     * 步骤 2（TSS）：高风险弹窗回调。
     *
     * @param rounds 用户选择的轮次（0 表示取消）
     */
    fun onHighRiskDialogResult(rounds: Int) {
        if (rounds == 0) {
            // 用户取消
            _state.value = _state.value.copy(step = PaymentStep.IDLE)
            return
        }
        _state.value = _state.value.copy(
            riskRounds = rounds,
            currentRound = 1,
            step = PaymentStep.BIOMETRIC_PROMPT
        )
    }

    /**
     * 高风险弹窗"下次不再提醒"勾选回调 — 持久化到 EncryptedSharedPreferences。
     */
    fun onSkipHighRiskExplanation(skip: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_SKIP_RISK, skip).apply()
        _state.value = _state.value.copy(skipHighRiskExplanation = skip)
    }

    /**
     * 步骤 3：BiometricPrompt 成功回调。
     *
     * @param biometricSignature 生物认证签名
     */
    fun onBiometricSuccess(biometricSignature: ByteArray) {
        // 安全存储生物签名
        storedBiometricSignature = biometricSignature.copyOf()

        viewModelScope.launch {
            // 判断是否需要 KDFS（缓存期内跳过）
            val kdfsHash = zkpCacheManager.getCachedKdfs(_state.value.fromDid)
            if (kdfsHash != null) {
                onKdfsComplete(kdfsHash)
            } else {
                _state.value = _state.value.copy(step = PaymentStep.KDFS_DRAW)
            }
        }
    }

    /**
     * 步骤 4：KDFS 图案完成回调。
     *
     * @param kdfsHash KDFS 图案哈希
     */
    fun onKdfsComplete(kdfsHash: ByteArray) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                step = PaymentStep.ZKP_GENERATING
            )

            try {
                // 缓存 KDFS 哈希
                zkpCacheManager.cacheKdfs(_state.value.fromDid, kdfsHash)

                // ZKP 不可用时用空白证明跳过，避免 Mopro 原生库缺位闪退
                val zkpAvailable = withContext(Dispatchers.IO) { zkpService.isZkpAvailable() }
                val proofData = if (zkpAvailable) {
                    val masterDid = identityManager.getActiveDid()
                        ?: throw IllegalStateException("无活跃 DID")
                    val masterIdentity = identityManager.getMasterIdentity()
                    val isFromMaster = masterIdentity?.did == _state.value.fromDid
                    val publicKeyPem: String
                    if (isFromMaster && masterIdentity != null) {
                        publicKeyPem = masterIdentity.publicKeyPem
                    } else {
                        val childIdentity = identityManager.getChildIdentity(_state.value.fromDid)
                            ?: throw IllegalStateException("副账号不存在: ${_state.value.fromDid.takeLast(12)}")
                        publicKeyPem = childIdentity.publicKeyPem
                    }
                    val request = com.sovexis.domain.zkp.ZkpProveRequest(
                        biometricSignature = storedBiometricSignature
                            ?: throw IllegalStateException("生物签名缺失"),
                        deviceBindingData = identityManager.getDeviceBindingData(),
                        kdfsPatternHash = kdfsHash,
                        sessionNonce = paymentManager.getSessionNonce(),
                        publicKeyPem = publicKeyPem,
                        expectedCommitmentRoot = identityManager.getExpectedCommitmentRoot(masterDid)
                            ?: throw IllegalStateException("预期承诺根缺失")
                    )
                    withContext(Dispatchers.IO) { zkpService.prove(request).getOrThrow() }
                } else {
                    // 降级：ZKP 不可用，返回空白证明数据
                    com.sovexis.domain.zkp.ZkpProofData(
                        proofBytes = "ZKP_UNAVAILABLE".toByteArray(Charsets.UTF_8),
                        publicInputs = emptyList(),
                        riskLabel = "ZKP_UNAVAILABLE"
                    )
                }

                val proof = proofData

                // 更新状态
                val currentRounds = _state.value.riskRounds
                val currentRound = _state.value.currentRound
                val proofWrapper = ZkpProofWrapper(
                    proofId = proof.proofId,
                    proofBytes = proof.proofBytes
                )
                val allProofs = _state.value.zkpResults + proofWrapper

                // 安全擦除临时存储的生物签名
                storedBiometricSignature?.let { Arrays.fill(it, 0) }
                storedBiometricSignature = null

                if (currentRound >= currentRounds) {
                    // 所有轮次完成，进入签名
                    _state.value = _state.value.copy(
                        zkpResults = allProofs,
                        isLoading = false,
                        step = PaymentStep.SIGNING
                    )
                    executeSignature(allProofs)
                } else {
                    // 继续下一轮
                    _state.value = _state.value.copy(
                        zkpResults = allProofs,
                        currentRound = currentRound + 1,
                        isLoading = false,
                        step = PaymentStep.BIOMETRIC_PROMPT
                    )
                }
            } catch (e: Throwable) {
                // 安全擦除临时存储
                storedBiometricSignature?.let { Arrays.fill(it, 0) }
                storedBiometricSignature = null

                _state.value = _state.value.copy(
                    isLoading = false,
                    step = PaymentStep.FAILED,
                    error = "ZKP 生成失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 执行签名并发送。
     *
     * @param proofs ZKP 证明列表
     */
    private suspend fun executeSignature(proofs: List<ZkpProof>) {
        val notifyId = "tx_${_state.value.fromDid.takeLast(8)}_${System.currentTimeMillis()}"
        try {
            _state.value = _state.value.copy(
                isLoading = true,
                step = PaymentStep.SIGNING
            )
            TransactionNotificationHolder.upsert(
                TransactionNotification(
                    id = notifyId, txId = "", amount = _state.value.amount,
                    fromDid = _state.value.fromDid, toDid = _state.value.toDid,
                    status = TxNotifyStatus.SIGNING, statusLabel = "签名中"
                )
            )

            // 准备交易
            val prepareResult = paymentManager.preparePayment(
                fromDid = _state.value.fromDid,
                toDid = _state.value.toDid,
                amount = _state.value.amount,
                note = null
            )

            when (prepareResult) {
                is PrepareResult.Ready -> {
                    // 签名
                    val signedTx = paymentManager.signAndSubmit(
                        prepareResult.unsignedTx,
                        proofs
                    ).getOrThrow()

                    // 加密发送 — 仅对非本地收款方（外部DID）执行
                    val isLocalTo = _state.value.toAccounts.any { it.did == _state.value.toDid && it.isLocal }
                    if (!isLocalTo) {
                        _state.value = _state.value.copy(step = PaymentStep.SENDING)
                        val rawMessage = RawMessage(
                            messageId = signedTx.txId,
                            payload = signedTx.toByteArray(),
                            senderAddress = _state.value.fromDid,
                            timestamp = signedTx.timestamp
                        )
                        cryptoCommLayer.send(rawMessage.payload, _state.value.toDid).getOrThrow()
                    }

                    // 本地转账完成 — 交易已挂起等待节点确认
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = PaymentStep.SUBMITTED_PENDING,
                        txId = signedTx.txId,
                        pendingAmount = _state.value.pendingAmount + _state.value.amount
                    )
                    TransactionNotificationHolder.upsert(
                        TransactionNotification(
                            id = notifyId, txId = signedTx.txId, amount = _state.value.amount,
                            fromDid = _state.value.fromDid, toDid = _state.value.toDid,
                            status = TxNotifyStatus.SUBMITTED_PENDING, statusLabel = "待节点确认"
                        )
                    )
                    refreshBalanceForDid(_state.value.fromDid)
                    // 保存非本地收款账号（如勾选）
                    maybeSaveRecipient()
                }

                is PrepareResult.Denied -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = PaymentStep.FAILED,
                        error = prepareResult.reason
                    )
                }
            }
        } catch (e: Throwable) {
            _state.value = _state.value.copy(
                isLoading = false,
                step = PaymentStep.FAILED,
                error = "签名失败: ${e.message}"
            )
            TransactionNotificationHolder.upsert(
                TransactionNotification(
                    id = notifyId, txId = "", amount = _state.value.amount,
                    fromDid = _state.value.fromDid, toDid = _state.value.toDid,
                    status = TxNotifyStatus.FAILED, statusLabel = "签名失败"
                )
            )
            // 失败后刷新余额，确保账本状态一致（PENDING 交易可能已写入）
            val fromDid = _state.value.fromDid
            if (fromDid.isNotBlank()) refreshBalanceForDid(fromDid)
        } finally {
            // 安全擦除
            proofs.forEach { it.proofBytes?.let { bytes -> Arrays.fill(bytes, 0) } }
        }
    }

    /**
     * BiometricPrompt 失败回调。
     *
     * @param error 错误信息
     */
    fun onBiometricFailed(error: String) {
        // 安全擦除临时存储
        storedBiometricSignature?.let { Arrays.fill(it, 0) }
        storedBiometricSignature = null

        _state.value = _state.value.copy(
            step = PaymentStep.FAILED,
            error = "生物认证失败: $error"
        )
    }

    /**
     * 取消本地 PENDING 交易（用户主动撤回）。
     *
     * @param txId 待取消的交易 ID
     */
    fun cancelTransaction(txId: String) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { paymentManager.cancelTransaction(txId) }
                if (ok) {
                    TransactionNotificationHolder.markCancelled(txId)
                    val fromDid = _state.value.fromDid
                    if (fromDid.isNotBlank()) refreshBalanceForDid(fromDid)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * 超时回调。
     */
    fun onTimeout() {
        // 安全擦除临时存储
        storedBiometricSignature?.let { Arrays.fill(it, 0) }
        storedBiometricSignature = null

        _state.value = _state.value.copy(
            step = PaymentStep.FAILED,
            error = "操作超时，已自动取消"
        )
    }

    /**
     * 重置流程。
     */
    fun reset() {
        // 安全擦除临时存储
        storedBiometricSignature?.let { Arrays.fill(it, 0) }
        storedBiometricSignature = null

        _state.value.zkpResults.forEach {
            it.proofBytes?.let { bytes -> Arrays.fill(bytes, 0) }
        }

        val current = _state.value
        _state.value = PaymentState(
            balance = current.balance,
            pendingAmount = current.pendingAmount,
            skipHighRiskExplanation = current.skipHighRiskExplanation,
            fromAccounts = current.fromAccounts,
            toAccounts = current.toAccounts,
            savedRecipients = current.savedRecipients,
            accountBalances = current.accountBalances
        )
    }

    // ========== [TEST] 测试用充值功能 ==========
    // 后续接入 MockLedger 后，以下方法将被账本 API 替换。
    // 当前仅用于主副账号余额支付转移测试。

    /**
     * [TEST] 显示充值弹窗。
     */
    fun showRechargeDialog() {
        _state.value = _state.value.copy(showRechargeDialog = true)
    }

    /**
     * [TEST] 关闭充值弹窗。
     */
    fun dismissRechargeDialog() {
        _state.value = _state.value.copy(showRechargeDialog = false)
    }

    /**
     * [TEST] 执行充值（默认充值到主账号）。
     * 当前通过 MockLedger.deposit 写入 CONFIRMED 交易，
     * 后续接入第三方服务商时替换为正式充值接口。
     *
     * @param rechargeAmount 充值金额
     */
    fun rechargeBalance(rechargeAmount: Double) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(showRechargeDialog = false)
                // 找主账号
                val masterDid = _state.value.fromAccounts
                    .find { it.accountType == com.sovexis.domain.identity.AccountType.MASTER }?.did
                    ?: return@launch
                withContext(Dispatchers.IO) { paymentManager.deposit(masterDid, rechargeAmount) }
                // 原子刷新余额
                val selectedDid = _state.value.fromDid
                if (selectedDid.isNotBlank()) refreshBalanceForDid(selectedDid) else refreshBalances(masterDid)
            } catch (_: Exception) {}
        }
    }

    /**
     * Node 支付路径：签发 C-08 令牌 → WebSocket payment_submit → 等待 payment_confirmed。
     *
     * 保留本地 MockLedger 作为离线缓存，Node 在线时通过此路径实现主权授权支付。
     * Node 端验证 C-08 令牌后执行支付，销毁令牌，签发 C-03 凭证回传。
     */
    fun payViaNode() {
        val fromDid = _state.value.fromDid
        val toDid = _state.value.toDid
        val amount = _state.value.amount
        if (fromDid.isBlank() || toDid.isBlank() || amount <= 0) return

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(step = PaymentStep.SIGNING)

                // 1. 签发 C-08 一次性授权令牌
                val token = credentialIssuer.issueAuthorizationToken(
                    action = "confirm_payment",
                    target = "payment:${fromDid}:${toDid}:${amount}:${System.currentTimeMillis()}",
                    parameters = mapOf("amount" to amount, "recipient" to toDid)
                )
                Log.i("PaymentVM", "C-08 issued: ${token.id}")

                // 2. 通过 WebSocket 发送 payment_submit
                val txId = "tx:${System.currentTimeMillis()}"
                val msg = org.json.JSONObject().apply {
                    put("type", "payment_submit")
                    put("payload", org.json.JSONObject().apply {
                        put("auth_token", token.id)
                        put("transaction", org.json.JSONObject().apply {
                            put("id", txId)
                            put("from_did", fromDid)
                            put("to_did", toDid)
                            put("amount", amount)
                        })
                    })
                }
                wsManager.sendRawMessage(msg.toString())

                _state.value = _state.value.copy(
                    step = PaymentStep.SUBMITTED_PENDING,
                    txId = txId,
                    pendingAmount = _state.value.pendingAmount + amount
                )
                Log.i("PaymentVM", "Payment submitted to Node: $txId")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = PaymentStep.FAILED,
                    error = "支付授权失败: ${e.message}"
                )
                Log.e("PaymentVM", "payViaNode failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 安全清理敏感数据
        storedBiometricSignature?.let { Arrays.fill(it, 0) }
        _state.value.zkpResults.forEach {
            it.proofBytes?.let { bytes -> Arrays.fill(bytes, 0) }
        }
    }
}
