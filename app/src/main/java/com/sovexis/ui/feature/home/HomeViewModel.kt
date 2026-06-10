package com.sovexis.ui.feature.home

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.sovexis.domain.communication.NodeMessageRouter
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.domain.payment.PaymentManager
import com.sovexis.ui.components.AccountStateHolder
import com.sovexis.ui.components.TransactionNotificationHolder
import com.sovexis.core.common.UiEvent
import com.sovexis.core.result.getOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val selectedModel: String = "Sovexis Local",
    val availableNodes: List<String> = listOf("本地模式"),
    val availableModels: List<String> = listOf("Sovexis Local", "Qwen2.5-7B", "DeepSeek-V3"),
    val nodeConnected: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val identityManager: IdentityManager,
    private val paymentManager: PaymentManager,
    private val nodeRouter: NodeMessageRouter? = null,
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
            loadAccounts()
        }
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
        val model = _uiState.value.selectedModel

        viewModelScope.launch {
            try {
                val replyText = if (node == "本地模式") {
                    simulateLocalReply(text)
                } else {
                    sendToNode(node, model, text)
                }
                val replyMsg = ChatMessage(content = replyText, isUser = false)
                _uiState.update {
                    it.copy(messages = it.messages + replyMsg, isLoading = false)
                }
            } catch (e: Exception) {
                val errMsg = ChatMessage(content = "错误: ${e.message}", isUser = false)
                _uiState.update {
                    it.copy(messages = it.messages + errMsg, isLoading = false)
                }
            }
        }
    }

    fun selectNode(node: String) {
        _uiState.update { it.copy(selectedNode = node, nodeConnected = false) }
        if (node != "本地模式") {
            checkNodeStatus(node)
        }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    // ===== 节点通信 =====

    private fun checkNodeStatus(node: String) {
        viewModelScope.launch {
            try {
                val info = parseNodeInfo(node)
                val connected = withContext(Dispatchers.IO) {
                    try {
                        val url = URL("http://${info.first}:${info.second}/healthz")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 2000; conn.readTimeout = 2000
                        conn.responseCode == 200
                    } catch (_: Exception) { false }
                }
                _uiState.update { it.copy(nodeConnected = connected) }
            } catch (_: Exception) {}
        }
    }

    private suspend fun sendToNode(node: String, model: String, text: String): String {
        return withContext(Dispatchers.IO) {
            // Try WebSocket via NodeMessageRouter first
            try {
                if (nodeRouter != null) {
                    val result = nodeRouter.sendRequest("chat", mapOf("model" to model, "message" to text))
                    val msg = result.getOrNull()
                    if (msg != null) {
                        return@withContext msg.payload.getOrDefault("reply", msg.payload.toString()).toString()
                    }
                }
            } catch (_: Exception) { /* fallback to HTTP */ }

            // Fallback: HTTP POST to steward endpoint
            val info = parseNodeInfo(node)
            val url = URL("http://${info.first}:${info.second}/api/v1/steward/chat")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8000; conn.readTimeout = 15000
            val body = """{"model":"$model","message":"$text"}"""
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
