package com.sovexis.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.communication.NodeMessageRouter
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.domain.payment.PaymentManager
import com.sovexis.ui.components.AccountStateHolder
import com.sovexis.ui.components.TransactionNotificationHolder
import com.sovexis.core.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val selectedNode: String = "本地测试节点",
    val selectedModel: String = "Sovexis Local",
    val availableNodes: List<String> = listOf("本地测试节点"),
    val availableModels: List<String> = listOf("Sovexis Local")
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val identityManager: IdentityManager,
    private val paymentManager: PaymentManager,
    private val nodeRouter: NodeMessageRouter? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val result = com.sovexis.core.result.getOrNull(identityManager.getAllIdentities())
            AccountStateHolder.update(result ?: emptyList())
            _uiState.update {
                it.copy(
                    activeAccount = result?.firstOrNull(),
                    allAccounts = result ?: emptyList()
                )
            }
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
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            try {
                // 占位：模拟本地响应。接入节点时改为 nodeRouter.sendRequest("chat", ...)
                val replyText = simulateLocalReply(text)
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
        _uiState.update { it.copy(selectedNode = node) }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    private fun simulateLocalReply(text: String): String {
        return when {
            text.contains("你好") || text.contains("hello", ignoreCase = true) ->
                "您好！我是 Sovexis 本地助手，当前运行在离线模式。\n\n连接节点后可获得完整的 AI 对话能力。"
            text.contains("余额") || text.contains("balance", ignoreCase = true) ->
                "当前余额功能已就绪，请前往「支付」页面查看详细余额和交易记录。"
            text.contains("身份") || text.contains("DID") ->
                "您的去中心化身份 (DID) 已在身份管理页面维护。\n\n主账号 DID 末尾: ${_uiState.value.activeAccount?.did?.takeLast(12) ?: "N/A"}"
            else ->
                "收到您的消息。当前处于离线模式，完整 AI 对话需要连接 Sovexis 节点。\n\n如需帮助，您可以尝试输入：\n• 你好\n• 余额\n• 身份"
        }
    }
}
