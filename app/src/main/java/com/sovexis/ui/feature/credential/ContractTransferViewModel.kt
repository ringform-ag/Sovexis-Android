package com.sovexis.ui.feature.credential

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.communication.WebSocketManager
import com.sovexis.domain.credential.CredentialIssuer
import com.sovexis.domain.credential.toJson
import com.sovexis.domain.identity.IdentityManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * 合约承接面板 UI 状态
 */
data class ContractTransferUiState(
    val isVisible: Boolean = false,
    val oldDID: String = "",
    val contractIDs: List<String> = emptyList(),
    val countdownSeconds: Int = 0,
    val countdownDisplay: String = "",
    val isCountdownWarning: Boolean = false,    // < 5分钟变红
    val availableAccounts: List<AccountOption> = emptyList(),
    val isProcessing: Boolean = false,
    val snackbarMessage: String? = null
)

/**
 * 副账号选项
 */
data class AccountOption(
    val did: String,
    val displayName: String,
    val isSelected: Boolean = false
)

/**
 * ContractTransferViewModel — 合约承接弹窗逻辑
 *
 * 生命周期与宿主 Activity 绑定（通过 AndroidViewModel）。
 * 倒计时在 viewModelScope 中运行，Activity 销毁时自动取消。
 * 弹窗显示期间切到后台，倒计时继续；回来时状态正确。
 */
class ContractTransferViewModel @Inject constructor(
    application: Application,
    private val wsManager: WebSocketManager,
    private val credentialIssuer: CredentialIssuer,
    private val identityManager: IdentityManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ContractTransferVM"
        private const val COUNTDOWN_WARNING_THRESHOLD = 300 // 5 分钟
    }

    private val _uiState = MutableStateFlow(ContractTransferUiState())
    val uiState: StateFlow<ContractTransferUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        // 自注册 WebSocket 回调 — 无需 HomeViewModel 手动接线
        wsManager.setOnDelegationRevoked { oldDID, contractIDs, countdownSeconds ->
            onDelegationRevoked(oldDID, contractIDs, countdownSeconds)
        }
        wsManager.setOnShellAccountChanged { shellDID, status ->
            onShellAccountChanged(shellDID, status)
        }
        // WebSocket 重连后自动刷新合约承接状态
        viewModelScope.launch {
            wsManager.connectionState.collect { state ->
                if (state == WebSocketManager.ConnectionState.CONNECTED) {
                    refreshState()
                }
            }
        }
    }

    /**
     * 收到 identity_delegation_revoked 推送时调用。
     * 如果已有弹窗，合并合约列表并取最小超时。
     */
    fun onDelegationRevoked(oldDID: String, contractIDs: List<String>, countdownSeconds: Int) {
        val current = _uiState.value

        if (current.isVisible) {
            // 合并：追加合约 ID（去重）、取最小超时
            val merged = (current.contractIDs + contractIDs).distinct()
            val newCountdown = minOf(current.countdownSeconds, countdownSeconds)
            _uiState.update {
                it.copy(
                    oldDID = current.oldDID.ifEmpty { oldDID },
                    contractIDs = merged,
                    countdownSeconds = newCountdown,
                    countdownDisplay = formatCountdown(newCountdown)
                )
            }
            Log.i(TAG, "合并委派撤销: oldDID=$oldDID mergedContracts=${merged.size} timeout=${newCountdown}s")
        } else {
            _uiState.update {
                it.copy(
                    isVisible = true,
                    oldDID = oldDID,
                    contractIDs = contractIDs,
                    countdownSeconds = countdownSeconds,
                    countdownDisplay = formatCountdown(countdownSeconds),
                    isCountdownWarning = countdownSeconds < COUNTDOWN_WARNING_THRESHOLD
                )
            }
        }

        // 加载副账号选项
        loadAccountOptions()
        // 启动倒计时
        startCountdown()
    }

    /**
     * 用户选择承接
     */
    fun onAcceptTransfer(selectedDID: String) {
        val state = _uiState.value
        if (state.isProcessing || state.oldDID.isEmpty()) return

        _uiState.update { it.copy(isProcessing = true) }

        viewModelScope.launch {
            try {
                // 1. 签发合约转让凭证（含新 C-02 + 转让凭证）
                val (newIdentityCred, transferCred) = credentialIssuer.issueContractTransfer(
                    oldDID = state.oldDID,
                    newDID = selectedDID,
                    contractIDs = state.contractIDs
                )

                // 2. 先发送新身份委派凭证
                val issuedMsg = JSONObject().apply {
                    put("type", "credential_issued")
                    put("payload", JSONObject().apply {
                        put("credential", JSONObject(newIdentityCred.toJson()))
                    })
                }
                wsManager.sendRawMessage(issuedMsg.toString())

                // 3. 发送承接请求
                val transferMsg = JSONObject().apply {
                    put("type", "accept_contract_transfer")
                    put("payload", JSONObject().apply {
                        put("old_did", state.oldDID)
                        put("new_did", selectedDID)
                        put("contract_ids", JSONArray(state.contractIDs))
                        put("transfer_credential", JSONObject(transferCred.toJson()))
                    })
                }
                wsManager.sendRawMessage(transferMsg.toString())

                // 等待 ack（简化：直接关闭弹窗）
                delay(1000)

                dismiss()
                _uiState.update { it.copy(snackbarMessage = "合约已承接至 $selectedDID") }
                Log.i(TAG, "承接成功: ${state.oldDID} → $selectedDID")

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, snackbarMessage = "承接失败: ${e.message}")
                }
                Log.e(TAG, "承接失败", e)
            }
        }
    }

    /**
     * 用户点击"稍后处理" — 关闭弹窗但保留通知栏选项
     */
    fun onDismiss() {
        dismiss()
        _uiState.update { it.copy(snackbarMessage = "合约承接可稍后处理") }
    }

    /**
     * 清理弹窗状态（用户主动关闭或超时）
     */
    fun dismiss() {
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update {
            ContractTransferUiState(isVisible = false)
        }
    }

    /**
     * 预留：WebSocket 重连后刷新状态
     */
    fun refreshState() {
        viewModelScope.launch {
            val msg = JSONObject().apply { put("type", "get_credential_status") }
            wsManager.sendRawMessage(msg.toString())
            // Node 端会返回 credential_status，由 WebSocketManager 分发给本 VM
        }
    }

    /**
     * 处理 Node 端推送的 shell_account_disposed
     */
    fun onShellAccountChanged(shellDID: String, status: String) {
        if (status == "activated") {
            // 兜底激活 — 关闭弹窗（如果还在显示）
            dismiss()
            _uiState.update {
                it.copy(snackbarMessage = "已启用隐私保护兜底，合约由临时身份继续履行")
            }
        } else if (status == "completed") {
            _uiState.update {
                it.copy(snackbarMessage = "临时身份已完成合约，已自动销毁")
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ── 私有方法 ──

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = _uiState.value.countdownSeconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                val warning = remaining < COUNTDOWN_WARNING_THRESHOLD
                _uiState.update {
                    it.copy(
                        countdownSeconds = remaining,
                        countdownDisplay = formatCountdown(remaining),
                        isCountdownWarning = warning
                    )
                }
            }
            // 倒计时归零 — 自动关闭弹窗（兜底已由 Node 端 triggerFallback 处理）
            dismiss()
        }
    }

    private fun loadAccountOptions() {
        viewModelScope.launch {
            try {
                val allAccounts = identityManager.getAllIdentities().getOrNull() ?: emptyList()
                val revokedDID = _uiState.value.oldDID

                val options = allAccounts
                    .filter { acc ->
                        acc.did != revokedDID &&
                        !acc.isFrozen &&
                        !acc.did.startsWith("did:sovexis:shell:")
                    }
                    .map { AccountOption(did = it.did, displayName = it.alias ?: it.did.takeLast(12)) }

                _uiState.update { it.copy(availableAccounts = options) }
            } catch (e: Exception) {
                Log.e(TAG, "加载副账号列表失败", e)
            }
        }
    }

    private fun formatCountdown(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}
