package com.sovexis.ui.feature.home

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.sovexis.domain.NodeErrorMapper
import com.sovexis.domain.communication.NodeMessageRouter
import com.sovexis.domain.communication.WebSocketManager
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.domain.payment.PaymentManager
import com.sovexis.ui.components.AccountStateHolder
import com.sovexis.ui.components.TransactionNotificationHolder
import com.sovexis.ui.feature.credential.ContractTransferViewModel
import com.sovexis.core.common.UiEvent
import com.sovexis.core.result.getOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class HomeUiState(
    val activeAccount: SovexisAccount? = null,
    val allAccounts: List<SovexisAccount> = emptyList(),
    val currentRoute: String = "home",
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val selectedNode: String = "本地模式",
    val nodeModel: String = "",         // 模型名称，从节点拉取
    val availableNodes: List<String> = listOf("本地模式"),
    val nodeConnected: Boolean = false,
    // SYNC-004 P1: 承接/兜底 Snackbar
    val snackbarMessage: String? = null,
    val activeIdentityDisplay: String = "",
    val isShellMode: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val identityManager: IdentityManager,
    private val paymentManager: PaymentManager,
    private val nodeRouter: NodeMessageRouter? = null,
    private val wsManager: WebSocketManager? = null,
    private val contractTransferVM: ContractTransferViewModel? = null,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "sovexis_nodes_secure",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        loadAccounts()
        refreshNodeList()
        observeContractTransfer()
    }

    private fun observeContractTransfer() {
        val cvm = contractTransferVM ?: return
        viewModelScope.launch {
            cvm.uiState.collect { transferState ->
                transferState.snackbarMessage?.let { msg ->
                    _uiState.update { it.copy(snackbarMessage = msg) }
                    cvm.clearSnackbar()
                }
            }
        }
        // 活跃身份指示器
        viewModelScope.launch {
            AccountStateHolder.activeIdentityDID.collect { did ->
                if (did.isNotEmpty()) {
                    _uiState.update { it.copy(activeIdentityDisplay = did) }
                }
            }
        }
        viewModelScope.launch {
            AccountStateHolder.isShellMode.collect { shell ->
                _uiState.update { it.copy(isShellMode = shell) }
            }
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val result = identityManager.getAllIdentities().getOrNull()
            AccountStateHolder.update(result ?: emptyList())
            _uiState.update {
                it.copy(
                    activeAccount = result?.firstOrNull(),
                    allAccounts = result ?: emptyList()
                )
            }
        }
    }

    fun refreshNodeList() {
        val nodes = loadSavedNodes()
        val nodeNames = mutableListOf("本地模式")
        nodes.forEach { n -> nodeNames.add("${n.name} (${n.ip}:${n.port})") }
        _uiState.update { it.copy(availableNodes = nodeNames) }
        // Keep selected node if it still exists
        val cur = _uiState.value.selectedNode
        if (cur != "本地模式" && !nodeNames.contains(cur)) {
            _uiState.update { it.copy(selectedNode = "本地模式", nodeConnected = false) }
        }
    }

    fun selectAccount(did: String) {
        viewModelScope.launch {
            identityManager.setActiveIdentity(did)
            AccountStateHolder.setActiveIdentity(did)
            loadAccounts()
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun navigate(route: String) {
        viewModelScope.launch { _events.send(UiEvent.Navigate(route)) }
    }

    fun cancelTransaction(txId: String) {
        viewModelScope.launch {
            try {
                paymentManager.cancelTransaction(txId)
                TransactionNotificationHolder.markCancelled(txId)
            } catch (_: Exception) {}
        }
    }

    // ===== 聊天 =====

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val userMsg = ChatMessage(content = text, isUser = true)
        _uiState.update {
            it.copy(messages = it.messages + userMsg, inputText = "", isLoading = true)
        }

        val node = _uiState.value.selectedNode

        viewModelScope.launch {
            try {
                val replyText = if (node == "本地模式") {
                    simulateLocalReply(text)
                } else {
                    sendToNode(node, text)
                }
                val replyMsg = ChatMessage(content = replyText, isUser = false)
                _uiState.update {
                    it.copy(messages = it.messages + replyMsg, isLoading = false)
                }
            } catch (e: Exception) {
                val errMsg = ChatMessage(content = NodeErrorMapper.translate(e.message), isUser = false)
                _uiState.update {
                    it.copy(messages = it.messages + errMsg, isLoading = false)
                }
            }
        }
    }

    fun selectNode(node: String) {
        _uiState.update { it.copy(selectedNode = node, nodeConnected = false, nodeModel = "") }
        if (node != "本地模式") {
            checkNodeStatus(node)
        }
    }

    // ===== 节点通信 =====

    private fun checkNodeStatus(node: String) {
        viewModelScope.launch {
            try {
                val info = parseNodeInfo(node)
                val (connected, model) = withContext(Dispatchers.IO) {
                    try {
                        val url = URL("http://${info.first}:${info.second}/healthz")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 2000; conn.readTimeout = 2000
                        val ok = conn.responseCode == 200
                        // 从 healthz 响应中提取模型名称
                        val body = if (ok) conn.inputStream.bufferedReader().readText() else ""
                        val m = extractJsonField(body, "model") ?: ""
                        Pair(ok, m)
                    } catch (_: Exception) { Pair(false, "") }
                }
                _uiState.update { it.copy(nodeConnected = connected, nodeModel = model) }
            } catch (_: Exception) {}
        }
    }

    private suspend fun sendToNode(node: String, text: String): String {
        return withContext(Dispatchers.IO) {
            val info = parseNodeInfo(node)

            // 优先：通过 WebSocket 发送 steward_chat（支持 InferWithPlanning）
            if (wsManager?.isConnected() == true) {
                try {
                    val deferred = CompletableDeferred<String>()
                    val sessionId = "chat_${System.currentTimeMillis()}"
                    wsManager.setOnChatResponse { sid, msg ->
                        if (sid == sessionId) deferred.complete(msg)
                    }
                    wsManager.sendMessage(sessionId, text)
                    withTimeout(15000L) { deferred.await() }
                } catch (_: TimeoutCancellationException) {
                    "管家 AI 响应超时"
                } catch (_: Exception) {
                    // WebSocket failed, fall through to HTTP
                    ""
                }.takeIf { it.isNotEmpty() }?.let { return@withContext it }
            }

            // 备选：通过 NodeMessageRouter
            try {
                if (nodeRouter != null) {
                    val result = nodeRouter.sendRequest("chat", mapOf("message" to text))
                    val msg = result.getOrNull()
                    if (msg != null) {
                        return@withContext msg.payload.getOrDefault("reply", msg.payload.toString()).toString()
                    }
                }
            } catch (_: Exception) { /* fallback to HTTP */ }

            // 兜底：HTTP POST → 公开端点
            val url = URL("http://${info.first}:${info.second}/api/v1/steward/chat")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8000; conn.readTimeout = 15000
            val body = """{"message":"$text"}"""
            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText().let { resp ->
                    extractJsonField(resp, "reply") ?: "管家 AI 对话功能即将开放"
                }
            } else {
                "节点响应异常 (${conn.responseCode})"
            }
        }
    }

    private fun simulateLocalReply(text: String): String {
        return when {
            text.contains("你好") || text.contains("hello", ignoreCase = true) ->
                "您好！我是 Sovexis 本地助手，当前运行在本地模式。\n\n连接节点后可获得完整的 AI 对话能力。"
            text.contains("余额") || text.contains("balance", ignoreCase = true) ->
                "当前余额功能已就绪，请前往「支付」页面查看详细余额和交易记录。"
            text.contains("身份") || text.contains("DID") ->
                "您的去中心化身份 (DID) 已在身份管理页面维护。\n\n主账号 DID 末尾: ${_uiState.value.activeAccount?.did?.takeLast(12) ?: "N/A"}"
            else ->
                "收到您的消息。当前处于本地模式，完整 AI 对话需要连接 Sovexis 节点。\n\n如需帮助，您可以尝试输入：\n• 你好\n• 余额\n• 身份"
        }
    }

    // ===== 节点列表 =====

    private fun loadSavedNodes(): List<SavedNode> {
        val raw = securePrefs.getString("nodes_list", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 3) {
                SavedNode(parts.getOrElse(0){""}, parts.getOrElse(1){"Node"}, parts.getOrElse(2){""}, parts.getOrElse(3){"8100"}.toIntOrNull()?:8100)
            } else null
        }
    }

    /** Parse "Name (ip:port)" → Pair(ip, port) */
    private fun parseNodeInfo(nodeLabel: String): Pair<String, Int> {
        val match = Regex("""\(([\d.]+):(\d+)\)""").find(nodeLabel)
        return if (match != null) {
            Pair(match.groupValues[1], match.groupValues[2].toIntOrNull() ?: 8100)
        } else {
            Pair("127.0.0.1", 8100)
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        val m = Regex(""""$field"\s*:\s*"([^"]*)"""").find(json)
            ?: Regex(""""$field"\s*:\s*([^,}\]]+)""").find(json)
        return m?.groupValues?.getOrNull(1)?.trim()?.trim('"')
    }

    data class SavedNode(val id: String, val name: String, val ip: String, val port: Int)
}
