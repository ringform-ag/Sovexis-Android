package com.sovexis.mobile.ui.feature.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.mobile.domain.crypto.ThresholdSignatureService
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.payment.PaymentManager
import com.sovexis.domain.payment.PrepareResult
import com.sovexis.domain.payment.ZkpProof
import com.sovexis.mobile.domain.policy.PolicyEnforcer
import com.sovexis.mobile.domain.policy.PolicyCheckResult
import com.sovexis.mobile.domain.communication.RawMessage
import com.sovexis.mobile.domain.communication.TransportAdapter
import com.sovexis.mobile.domain.zkp.ZkpCacheManager
import com.sovexis.mobile.domain.zkp.ZkpService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Arrays
import javax.inject.Inject

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

    /** 完成 */
    COMPLETED,

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
    val zkpResults: List<ZkpProofWrapper> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val txId: String? = null
)

/**
 * ZKP 证明包装类。
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
    private val transportAdapter: TransportAdapter,
    // TSS 服务（高安全模式）
    private val tssService: ThresholdSignatureService
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentState())
    val state: StateFlow<PaymentState> = _state.asStateFlow()

    private var storedBiometricSignature: ByteArray? = null

    /**
     * 步骤 1：用户输入支付信息后，执行策略检查。
     *
     * @param fromDid 支付方 DID
     * @param toDid 收款方 DID
     * @param amount 金额
     */
    fun initiatePayment(fromDid: String, toDid: String, amount: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                fromDid = fromDid,
                toDid = toDid,
                amount = amount,
                step = PaymentStep.POLICY_CHECK,
                isLoading = true
            )

            try {
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
                        // 判断是否高安全模式（有 TSS 服务）
                        // TSS 服务已注入，始终为高安全模式
                        val isHighSecurity = true
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = if (isHighSecurity) PaymentStep.HIGH_RISK_DIALOG
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
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = PaymentStep.FAILED,
                    error = "策略检查失败: ${e.message}"
                )
            }
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

                // 生成 ZKP 证明
                val masterDid = identityManager.getActiveDid()
                    ?: throw IllegalStateException("无活跃 DID")
                val childIdentity = identityManager.getChildIdentity(_state.value.fromDid)
                    ?: throw IllegalStateException("副账号不存在")

                val request = com.sovexis.mobile.domain.zkp.ZkpProveRequest(
                    biometricSignature = storedBiometricSignature
                        ?: throw IllegalStateException("生物签名缺失"),
                    deviceBindingData = identityManager.getDeviceBindingData(),
                    kdfsPatternHash = kdfsHash,
                    sessionNonce = paymentManager.getSessionNonce(),
                    publicKeyPem = childIdentity.publicKeyPem,
                    expectedCommitmentRoot = identityManager.getExpectedCommitmentRoot(masterDid)
                        ?: throw IllegalStateException("预期承诺根缺失")
                )

                val proof = zkpService.prove(request).getOrThrow()

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
            } catch (e: Exception) {
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
        try {
            _state.value = _state.value.copy(
                isLoading = true,
                step = PaymentStep.SIGNING
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

                    // 加密发送
                    _state.value = _state.value.copy(step = PaymentStep.SENDING)
                    val rawMessage = RawMessage(
                        messageId = signedTx.txId,
                        payload = signedTx.toByteArray(),
                        senderAddress = _state.value.fromDid,
                        timestamp = signedTx.timestamp
                    )
                    transportAdapter.send(rawMessage.payload, _state.value.toDid).getOrThrow()

                    // 完成
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = PaymentStep.COMPLETED,
                        txId = signedTx.txId
                    )
                }

                is PrepareResult.Denied -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = PaymentStep.FAILED,
                        error = prepareResult.reason
                    )
                }
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                step = PaymentStep.FAILED,
                error = "签名失败: ${e.message}"
            )
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

        _state.value = PaymentState()
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
