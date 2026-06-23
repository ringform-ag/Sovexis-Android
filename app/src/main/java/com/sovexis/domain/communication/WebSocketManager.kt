package com.sovexis.domain.communication

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket 实时通信管理器
 *
 * 与 Node 端 /ws 端点建立长连接，处理：
 * - steward_chat → steward_chat_response 双向实时对话
 * - binding_status 绑定状态变更推送
 *
 * 断线重连：指数退避 1s → 2s → 4s → 8s → 16s → 30s，最多 5 次
 */
@Singleton
class WebSocketManager @Inject constructor() {

    companion object {
        private const val TAG = "WebSocketMgr"
        private const val NORMAL_CLOSURE = 1000
    }

    private var webSocket: WebSocket? = null
    private var nodeIp: String = ""
    private var nodePort: Int = 8100
    private var deviceDid: String = ""
    private var connected = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var onBindingStatusChanged: ((status: String, boundDid: String) -> Unit)? = null
    private var onChatResponse: ((sessionId: String, message: String) -> Unit)? = null
    private var onCredentialAck: ((credId: String, status: String) -> Unit)? = null
    private var onServiceStakeChanged: ((action: String, serviceDID: String, amount: Double) -> Unit)? = null
    private var onBidConfirmed: ((requestId: String) -> Unit)? = null
    private var onRevenueReport: ((json: JSONObject) -> Unit)? = null
    private var onPersonhoodResult: ((action: String, did: String, status: String) -> Unit)? = null
    private var onConnectionEstablished: (() -> Unit)? = null
    private var onContractChanged: ((contractId: String, status: String) -> Unit)? = null
    private var onProofVerified: ((contractId: String, proofHash: String) -> Unit)? = null
    private var onGuardianApproval: ((recoveryId: String, guardianDid: String, proofId: String) -> Unit)? = null

    // SYNC-004 callbacks
    private var onDelegationRevoked: ((oldDID: String, contractIDs: List<String>, countdownSeconds: Int) -> Unit)? = null
    private var onShellAccountChanged: ((shellDID: String, status: String) -> Unit)? = null
    private var onTransactionConfirmed: ((credJson: String) -> Unit)? = null
    private val vaultSyncHandlers = mutableMapOf<String, (sessionId: String, responseJson: String) -> Unit>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING }

    data class ChatMessage(
        val id: String,
        val role: String,      // "user" or "assistant"
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun connect(ip: String, port: Int, did: String) {
        if (connected || webSocket != null) {
            disconnect()
        }
        nodeIp = ip
        nodePort = port
        deviceDid = did
        reconnectAttempts = 0
        doConnect()
    }

    fun disconnect() {
        reconnectAttempts = maxReconnectAttempts // stop reconnecting
        webSocket?.close(NORMAL_CLOSURE, "Manual disconnect")
        webSocket = null
        connected = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun isConnected(): Boolean = connected

    fun setOnBindingStatusChanged(callback: (status: String, boundDid: String) -> Unit) {
        onBindingStatusChanged = callback
    }

    fun setOnDelegationRevoked(callback: (oldDID: String, contractIDs: List<String>, countdownSeconds: Int) -> Unit) {
        onDelegationRevoked = callback
    }

    fun setOnShellAccountChanged(callback: (shellDID: String, status: String) -> Unit) {
        onShellAccountChanged = callback
    }

    fun setOnTransactionConfirmed(callback: (credJson: String) -> Unit) {
        onTransactionConfirmed = callback
    }

    fun setOnChatResponse(callback: (sessionId: String, message: String) -> Unit) {
        onChatResponse = callback
    }

    fun setOnCredentialAck(callback: (credId: String, status: String) -> Unit) {
        onCredentialAck = callback
    }

    fun setOnServiceStakeChanged(callback: (action: String, serviceDID: String, amount: Double) -> Unit) {
        onServiceStakeChanged = callback
    }

    fun setOnBidConfirmed(callback: (requestId: String) -> Unit) {
        onBidConfirmed = callback
    }

    fun setOnRevenueReport(callback: (json: JSONObject) -> Unit) {
        onRevenueReport = callback
    }

    fun setOnPersonhoodResult(callback: (action: String, did: String, status: String) -> Unit) {
        onPersonhoodResult = callback
    }

    fun setOnConnectionEstablished(callback: () -> Unit) {
        onConnectionEstablished = callback
    }

    fun setOnContractChanged(callback: (contractId: String, status: String) -> Unit) {
        onContractChanged = callback
    }

    fun setOnProofVerified(callback: (contractId: String, proofHash: String) -> Unit) {
        onProofVerified = callback
    }

    fun setOnGuardianApproval(callback: (recoveryId: String, guardianDid: String, proofId: String) -> Unit) {
        onGuardianApproval = callback
    }

    fun sendRawMessage(rawJson: String) {
        if (!connected || webSocket == null) {
            Log.w(TAG, "Not connected, cannot send")
            return
        }
        scope.launch {
            try {
                webSocket?.send(rawJson)
                Log.d(TAG, "WS sent: ${rawJson.take(80)}...")
            } catch (e: Exception) {
                Log.e(TAG, "WS send error: ${e.message}")
            }
        }
    }

    fun sendMessage(sessionId: String, message: String) {
        if (!connected || webSocket == null) {
            Log.w(TAG, "Not connected, cannot send")
            return
        }
        val msg = JSONObject().apply {
            put("type", "steward_chat")
            put("sessionId", sessionId)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
        }
        scope.launch {
            try {
                webSocket?.send(msg.toString())
            } catch (e: Exception) {
                Log.e(TAG, "WS send error: ${e.message}")
            }
        }
        _chatMessages.value = _chatMessages.value + ChatMessage(
                id = "msg_${System.currentTimeMillis()}",
                role = "user",
                content = message
            )
    }

    private fun doConnect() {
        _connectionState.value = ConnectionState.CONNECTING
        val url = "ws://$nodeIp:$nodePort/ws?deviceDid=$deviceDid"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.CONNECTED
                Log.i(TAG, "WebSocket connected: $url")
                onConnectionEstablished?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
                Log.i(TAG, "WebSocket closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.i(TAG, "WebSocket closed: $code $reason")
                tryReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.e(TAG, "WebSocket failure: ${t.message}")
                tryReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val msg = JSONObject(text)
            val type = msg.optString("type", "")

            when (type) {
                "steward_chat_response" -> {
                    val sessionId = msg.optString("sessionId", "")
                    val content = msg.optString("message", "")
                    val error = msg.optString("error", "")
                    val displayContent = if (error.isNotEmpty()) error else content

                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        id = "msg_${System.currentTimeMillis()}",
                        role = "assistant",
                        content = displayContent
                    )
                    onChatResponse?.invoke(sessionId, displayContent)
                }
                "binding_status" -> {
                    val status = msg.optString("status", "")
                    val boundDid = msg.optString("boundDid", "")
                    onBindingStatusChanged?.invoke(status, boundDid)
                }
                "vault_sync_response" -> {
                    val sessionId = msg.optString("sessionId", "")
                    val handler = vaultSyncHandlers.remove(sessionId)
                    if (handler != null) {
                        handler.invoke(sessionId, text)
                    } else {
                        Log.d(TAG, "No handler registered for vault_sync session $sessionId")
                    }
                }
                // SYNC-004: 委派撤销 + 壳账户通知 + 交易确认
                "identity_delegation_revoked" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val oldDID = payload.optString("old_did", "")
                    val countdown = payload.optInt("countdown_seconds", 300)
                    val idsArr = payload.optJSONArray("contract_ids")
                    val ids = mutableListOf<String>()
                    if (idsArr != null) for (i in 0 until idsArr.length()) ids.add(idsArr.getString(i))
                    onDelegationRevoked?.invoke(oldDID, ids, countdown)
                }
                "shell_account_disposed" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val shellDID = payload.optString("shell_did", "")
                    val status = payload.optString("status", "")
                    onShellAccountChanged?.invoke(shellDID, status)
                }
                "transaction_confirmed" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val credJson = payload.optJSONObject("credential")?.toString() ?: ""
                    onTransactionConfirmed?.invoke(credJson)
                }
                "credential_ack" -> {
                    val credId = msg.optString("cred_id", "")
                    val status = msg.optString("status", "")
                    onCredentialAck?.invoke(credId, status)
                }
                "credential_revoked" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val credId = payload.optString("cred_id", "")
                    onCredentialAck?.invoke(credId, "revoked")
                }
                "service_stake_locked" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val svcDID = payload.optString("service_did", "")
                    val amount = payload.optDouble("amount", 0.0)
                    onServiceStakeChanged?.invoke("locked", svcDID, amount)
                }
                "service_stake_unlocked" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val svcDID = payload.optString("service_did", "")
                    val amount = payload.optDouble("amount", 0.0)
                    onServiceStakeChanged?.invoke("unlocked", svcDID, amount)
                }
                "bid_confirmed" -> {
                    val requestId = msg.optJSONObject("payload")?.optString("request_id", "") ?: ""
                    onBidConfirmed?.invoke(requestId)
                }
                "revenue_report" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    onRevenueReport?.invoke(payload)
                }
                "personhood_registered", "personhood_verified", "personhood_recovered" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val did = payload.optString("did", "")
                    val status = payload.optString("status", "")
                    onPersonhoodResult?.invoke(type, did, status)
                }
                "contract_status_changed" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val contractId = payload.optString("contract_id", "")
                    val status = payload.optString("status", "")
                    onContractChanged?.invoke(contractId, status)
                }
                "proof_verified" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val contractId = payload.optString("contract_id", "")
                    val proofHash = payload.optString("proof_hash", "")
                    onProofVerified?.invoke(contractId, proofHash)
                }
                "contract_list" -> {
                    // handled by StorageContractsViewModel via get_contracts response
                    val payload = msg.optJSONObject("payload") ?: return
                    onRevenueReport?.invoke(payload) // reuse revenue-like JSON parsing
                }
                "payment_confirmed" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val credJson = payload.optJSONObject("confirmation_credential")?.toString() ?: ""
                    if (credJson.isNotEmpty()) onTransactionConfirmed?.invoke(credJson)
                }
                "guardian_approval" -> {
                    val payload = msg.optJSONObject("payload") ?: return
                    val recoveryId = payload.optString("recovery_id", "")
                    val guardianDid = payload.optString("guardian_did", "")
                    val proofId = payload.optString("proof_id", "")
                    onGuardianApproval?.invoke(recoveryId, guardianDid, proofId)
                }
                else -> {
                    Log.d(TAG, "Unhandled message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse message error: ${e.message}")
        }
    }

    private fun tryReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.w(TAG, "Max reconnect attempts reached")
            return
        }

        reconnectAttempts++
        val delayMs = minOf(1000L * (1L shl (reconnectAttempts - 1)), 30000L)

        _connectionState.value = ConnectionState.RECONNECTING
        Log.i(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts/$maxReconnectAttempts)")

        scope.launch {
            delay(delayMs)
            if (reconnectAttempts <= maxReconnectAttempts) {
                doConnect()
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    /**
     * Register a one-time handler for vault_sync_response.
     */
    fun registerVaultSyncResponseHandler(sessionId: String, handler: (sessionId: String, responseJson: String) -> Unit) {
        vaultSyncHandlers[sessionId] = handler
    }
}
