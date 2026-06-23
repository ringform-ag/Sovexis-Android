package com.sovexis.ui.feature.credentials

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.core.result.Resource
import com.sovexis.core.result.getOrNull
import com.sovexis.domain.communication.WebSocketManager
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.MasterIdentity
import com.sovexis.domain.sync.NodeSyncClient
import com.sovexis.domain.vc.CredentialService
import com.sovexis.domain.vc.VerifiableCredential
import com.sovexis.domain.vc.VerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

enum class CredentialTab { MY_CREDENTIALS, ISSUE, VERIFY }

data class CredentialsUiState(
    val credentials: List<VerifiableCredential> = emptyList(),
    val activeAccount: MasterIdentity? = null,
    val isLoading: Boolean = false,
    val selectedTab: CredentialTab = CredentialTab.MY_CREDENTIALS,
    val issueType: String = "",
    val issueClaims: Map<String, String> = emptyMap(),
    val issueResult: String = "",
    val verifyInput: String = "",
    val verifyResult: VerificationResult? = null,
    val selectedCredentialId: String? = null,
    val presentationJson: String? = null,
    val qrBitmap: android.graphics.Bitmap? = null,
    val error: String? = null,
    // 同步状态
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val syncedCredentialIds: Set<String> = emptySet()
)

@HiltViewModel
class CredentialsViewModel @Inject constructor(
    private val credentialService: CredentialService,
    private val identityManager: IdentityManager,
    private val syncClient: NodeSyncClient,
    private val wsManager: WebSocketManager? = null
) : ViewModel() {

    companion object {
        private const val TAG = "CredentialsViewModel"
    }

    private val _uiState = MutableStateFlow(CredentialsUiState())
    val uiState: StateFlow<CredentialsUiState> = _uiState.asStateFlow()

    init { loadActiveAccount() }

    private fun loadActiveAccount() {
        viewModelScope.launch {
            val account = identityManager.getMasterIdentity()
            _uiState.update { it.copy(activeAccount = account) }
            account?.let { loadCredentials(it.did) }
        }
    }

    fun refresh() {
        val did = _uiState.value.activeAccount?.did ?: return
        loadCredentials(did)
    }

    private fun loadCredentials(ownerDid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    credentialService.getCredentialsByOwner(ownerDid)
                }
                val credentials = result.getOrNull() ?: emptyList()
                _uiState.update { it.copy(credentials = credentials, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "加载凭证失败", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── 同步 ──

    fun syncAllToNode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = "正在同步凭证...", error = null) }
            try {
                val credentials = _uiState.value.credentials
                if (credentials.isEmpty()) {
                    _uiState.update { it.copy(isSyncing = false, syncMessage = "没有需要同步的凭证") }
                    return@launch
                }

                var successCount = 0
                var failCount = 0
                for (vc in credentials) {
                    val syncItem = NodeSyncClient.SyncCredentialItem(
                        id = vc.credentialId,
                        type = vc.type.joinToString(", "),
                        content = buildVcJson(vc),
                        issuedAt = System.currentTimeMillis()
                    )
                    val result = syncClient.uploadCredential(syncItem)
                    if (result.isSuccess) {
                        successCount++
                        _uiState.update { it.copy(syncedCredentialIds = it.syncedCredentialIds + vc.credentialId) }
                    } else {
                        failCount++
                    }
                }

                _uiState.update {
                    it.copy(isSyncing = false, syncMessage = "同步完成: $successCount 成功, $failCount 失败")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncing = false, syncMessage = "同步失败: ${e.message}", error = e.message) }
            }
        }
    }

    private fun buildVcJson(vc: VerifiableCredential): String {
        // 将 VerifiableCredential 序列化为 JSON 字符串。
        // 凭证以原文（含签发者签名）形式同步到 Node。
        val claimsJson = vc.credentialSubject.entries.joinToString(",") { (k, v) ->
            "\"$k\": \"$v\""
        }
        return """{"@context":"${vc.context}","id":"${vc.credentialId}","type":["${vc.type.joinToString("\",\"")}"],"issuer":"${vc.issuer}","issuanceDate":"${vc.issuanceDate}","credentialSubject":{$claimsJson},"proof":{"type":"${vc.proof.type}","proofValue":"${vc.proof.proofValue}"}}"""
    }

    // ── 原有操作 ──

    fun selectTab(tab: CredentialTab) {
        _uiState.update { it.copy(selectedTab = tab, error = null) }
    }

    fun updateIssueType(type: String) {
        _uiState.update { it.copy(issueType = type) }
    }

    fun updateIssueClaims(claims: Map<String, String>) {
        _uiState.update { it.copy(issueClaims = claims) }
    }

    fun issueCredential() {
        val state = _uiState.value
        val ownerDid = state.activeAccount?.did ?: return
        val type = state.issueType.ifBlank { return }
        val claims = state.issueClaims.ifEmpty { return }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    credentialService.issueCredential(ownerDid, type, claims.mapValues { it.value as Any })
                }
                when (result) {
                    is Resource.Success -> {
                        val vc = result.data
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                issueResult = "签发成功: ${vc.credentialId.takeLast(12)}",
                                issueType = "",
                                issueClaims = emptyMap(),
                                selectedTab = CredentialTab.MY_CREDENTIALS
                            )
                        }
                        loadCredentials(ownerDid)
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = result.message) }
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun presentCredential(credentialId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    credentialService.createPresentation(credentialId)
                }
                when (result) {
                    is Resource.Success -> {
                        val vp = result.data
                        val json = buildVpJsonString(vp)
                        val qr = (credentialService as? com.sovexis.domain.vc.CredentialServiceImpl)?.generateQRCode(json)
                        _uiState.update {
                            it.copy(isLoading = false, selectedCredentialId = credentialId, presentationJson = json, qrBitmap = qr)
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = result.message) }
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun dismissPresentation() {
        _uiState.update { it.copy(selectedCredentialId = null, presentationJson = null, qrBitmap = null) }
    }

    fun updateVerifyInput(input: String) {
        _uiState.update { it.copy(verifyInput = input) }
    }

    fun verifyCredential() {
        val input = _uiState.value.verifyInput.ifBlank { return }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    credentialService.verifyCredential(input)
                }
                when (result) {
                    is Resource.Success -> _uiState.update { it.copy(isLoading = false, verifyResult = result.data) }
                    is Resource.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun revokeCredential(credentialId: String) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    credentialService.revokeCredential(credentialId)
                }
                // 同时通过 WebSocket 通知 Node 端撤销
                if (wsManager?.isConnected() == true) {
                    val msg = JSONObject().apply {
                        put("type", "revoke_credential")
                        put("payload", JSONObject().apply {
                            put("cred_id", credentialId)
                        })
                    }
                    wsManager.sendRawMessage(msg.toString())
                }
                when (result) {
                    is Resource.Success -> refresh()
                    is Resource.Error -> _uiState.update { it.copy(error = result.message) }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    @Deprecated("当前无使用场景，保留以备后续 QR 验证功能")
    fun generateQrForVerify(input: String) {
        viewModelScope.launch {
            val qr = withContext(Dispatchers.IO) {
                (credentialService as? com.sovexis.domain.vc.CredentialServiceImpl)?.generateQRCode(input)
            }
            _uiState.update { it.copy(qrBitmap = qr) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun buildVpJsonString(vp: com.sovexis.domain.vc.VerifiablePresentation): String {
        val holder = vp.verifiableCredential.firstOrNull()?.credentialSubject?.get("id")?.toString() ?: ""
        return buildString {
            appendLine("{")
            appendLine("  \"@context\": ${vp.context},")
            appendLine("  \"id\": \"${vp.presentationId}\",")
            appendLine("  \"type\": ${vp.type},")
            appendLine("  \"holder\": \"$holder\",")
            appendLine("  \"proof\": {")
            appendLine("    \"type\": \"${vp.proof.type}\",")
            appendLine("    \"proofValue\": \"${vp.proof.proofValue.take(40)}...\"")
            appendLine("  }")
            appendLine("}")
        }
    }
}
