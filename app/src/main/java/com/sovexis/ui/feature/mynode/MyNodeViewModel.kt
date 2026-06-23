package com.sovexis.ui.feature.mynode

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sovexis.domain.communication.CryptoCommLayer
import com.sovexis.domain.communication.WebSocketManager
import com.sovexis.domain.credential.CredentialIssuer
import com.sovexis.domain.credential.toJson
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.ui.components.NodeConnectionStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import org.json.JSONObject

enum class BindingStatus { UNBOUND, BOUND, PENDING, KEY_CHANGED }

data class VerifyResult(val verified: Boolean, val pending: Boolean, val message: String)

data class NodeServiceStatus(
    val storageAvailable: Boolean = false,
    val tssAvailable: Boolean = false,
    val aiAvailable: Boolean = false,
    val paymentAvailable: Boolean = false
)

data class NodeConfig(
    val id: String = "",
    val name: String = "未命名节点",
    val ip: String = "192.168.1.100",
    val port: Int = 8100,
    val did: String = "",
    val publicKey: String = "",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isEnabled: Boolean = true,
    val latency: Int = 0,
    val version: String = "",
    val lastConnected: String = "从未连接",
    val services: NodeServiceStatus = NodeServiceStatus(),
    val noiseReady: Boolean = false,
    val bindingStatus: BindingStatus = BindingStatus.UNBOUND,
    val bindingType: String = "self",
    val pairingKey: String = "",
    val capabilities: List<String> = emptyList(),
    val boundAccountName: String = "",
    val showKeyMismatch: Boolean = false,
    val error: String? = null
)

data class MyNodeUiState(
    val nodes: List<NodeConfig> = emptyList(),
    val selectedNodeId: String? = null,
    val isAddingNode: Boolean = false,
    val nodeIp: String = "192.168.1.100",
    val nodePort: Int = 8100,
    val nodeDid: String = "",
    val nodeVersion: String = "",
    val isConnected: Boolean = false,
    val lastConnectedTime: String = "从未连接",
    val storageBackupEnabled: Boolean = false,
    val tssCooEnabled: Boolean = false,
    val aiInferenceEnabled: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null,
    val nodePublicKey: String = "",
    val storedNodePublicKey: String = "",
    val showKeyMismatch: Boolean = false,
    val manualPublicKey: String = "",
    val noiseKeyRegistered: Boolean = false
)

@HiltViewModel
class MyNodeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoCommLayer: CryptoCommLayer,
    private val wsManager: WebSocketManager,
    private val identityManager: IdentityManager,
    private val credentialIssuer: CredentialIssuer
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyNodeUiState())
    val uiState: StateFlow<MyNodeUiState> = _uiState.asStateFlow()

    companion object {
        private const val PREFS_NODE = "sovexis_node_config"
        private const val KEY_NODES_LIST = "nodes_list"
        private const val KEY_NODE_PREFIX = "node_"
        private const val KEY_PAIRING_KEY_PREFIX = "node_pairing_key_"
        private const val KEY_BINDING_STATUS_PREFIX = "node_binding_status_"
        private const val TAG = "MyNodeVM"
    }

    private val prefs by lazy {
        val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, PREFS_NODE, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    init {
        loadNodesList()
        syncNodeState()
        startKeepAlive()
    }

    private fun startKeepAlive() {
        viewModelScope.launch {
            while (true) {
                delay(15_000L) // 每15秒检查一次连接状态
                val nodes = _uiState.value.nodes
                var needsSync = false
                nodes.forEach { node ->
                    if (node.isEnabled && !node.isConnecting) {
                        try {
                            val url = URL("http://${node.ip}:${node.port}/healthz")
                            val conn = (url.openConnection() as HttpURLConnection).apply {
                                connectTimeout = 3000; readTimeout = 3000
                            }
                            val alive = conn.responseCode == 200
                            if (alive && !node.isConnected) {
                                // 恢复连接状态
                                updateNode(node.id) {
                                    it.copy(isConnected = true, error = null)
                                }
                                needsSync = true
                            } else if (!alive && node.isConnected) {
                                // 检测到断开
                                updateNode(node.id) {
                                    it.copy(isConnected = false, error = "连接已断开",
                                        bindingStatus = BindingStatus.UNBOUND)
                                }
                                needsSync = true
                            }
                        } catch (_: Exception) {
                            if (node.isConnected) {
                                updateNode(node.id) {
                                    it.copy(isConnected = false, error = "连接已断开",
                                        bindingStatus = BindingStatus.UNBOUND)
                                }
                                needsSync = true
                            }
                        }
                    }
                }
                if (needsSync) syncNodeState()
            }
        }
    }

    private fun loadNodesList() {
        try {
            val nodesJson = prefs.getString(KEY_NODES_LIST, null)
            if (nodesJson.isNullOrEmpty()) {
                _uiState.update { it.copy(nodes = emptyList()) }
            } else {
                val nodes = parseNodesJson(nodesJson).map { node ->
                    node.copy(bindingStatus = loadBindingState(node.id))
                }
                _uiState.update { it.copy(nodes = nodes) }
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(nodes = emptyList()) }
        }
    }

    private fun parseNodesJson(json: String): List<NodeConfig> {
        return json.split(";").filter { it.isNotBlank() }.map { nodeStr ->
            val parts = nodeStr.split("|")
            val pk = parts.getOrElse(6) { "" }
            NodeConfig(
                id = parts.getOrElse(0) { "unknown" },
                name = parts.getOrElse(1) { "未命名节点" },
                ip = parts.getOrElse(2) { "192.168.1.100" },
                port = parts.getOrElse(3) { "8100" }.toIntOrNull() ?: 8100,
                did = parts.getOrElse(4) { "" },
                publicKey = parts.getOrElse(5) { "" },
                pairingKey = if (pk.isNotEmpty()) pk else fetchPairingKey(parts.getOrElse(0) { "" })
            )
        }
    }

    private fun saveNodesList(nodes: List<NodeConfig>) {
        val json = nodes.joinToString(";") { node ->
            val pk = node.pairingKey.ifEmpty { fetchPairingKey(node.id) }
            "${node.id}|${node.name}|${node.ip}|${node.port}|${node.did}|${node.publicKey}|${pk}"
        }
        prefs.edit().putString(KEY_NODES_LIST, json).apply()
    }

    private fun fetchPairingKey(nodeId: String): String {
        return prefs.getString("${KEY_PAIRING_KEY_PREFIX}$nodeId", "") ?: ""
    }

    private fun persistBindingState(nodeId: String, status: BindingStatus) {
        prefs.edit().putString("${KEY_BINDING_STATUS_PREFIX}$nodeId", status.name).apply()
    }

    private fun loadBindingState(nodeId: String): BindingStatus {
        val s = prefs.getString("${KEY_BINDING_STATUS_PREFIX}$nodeId", "UNBOUND") ?: "UNBOUND"
        return try { BindingStatus.valueOf(s) } catch (_: Exception) { BindingStatus.UNBOUND }
    }

    fun persistPairingKey(nodeId: String, key: String) {
        prefs.edit().putString("${KEY_PAIRING_KEY_PREFIX}$nodeId", key).apply()
    }

    fun addNode(node: NodeConfig) {
        val nodeId = "node_${System.currentTimeMillis()}"
        val defaultName = "节点 ${(_uiState.value.nodes.size + 1)}"
        val newNode = node.copy(id = nodeId, name = defaultName)
        val newNodes = _uiState.value.nodes + newNode
        _uiState.update { it.copy(nodes = newNodes) }
        saveNodesList(newNodes)
        if (newNode.pairingKey.isNotEmpty()) persistPairingKey(nodeId, newNode.pairingKey)
        syncNodeState()
    }

    fun updateNode(nodeId: String, update: (NodeConfig) -> NodeConfig) {
        val newNodes = _uiState.value.nodes.map { node ->
            if (node.id == nodeId) {
                val updated = update(node)
                if (updated.pairingKey.isNotEmpty() && updated.pairingKey != node.pairingKey) {
                    persistPairingKey(nodeId, updated.pairingKey)
                }
                updated
            } else node
        }
        _uiState.update { it.copy(nodes = newNodes) }
        saveNodesList(newNodes)
    }

    fun deleteNode(nodeId: String) {
        val newNodes = _uiState.value.nodes.filter { it.id != nodeId }
        _uiState.update { it.copy(nodes = newNodes) }
        saveNodesList(newNodes)
        syncNodeState()
    }

    fun toggleNodeEnabled(nodeId: String) {
        updateNode(nodeId) { node ->
            if (node.isEnabled) {
                node.copy(isEnabled = false, isConnected = false, error = null)
            } else {
                viewModelScope.launch { connectNode(nodeId) }
                node.copy(isEnabled = true, isConnecting = true, error = null)
            }
        }
    }

    fun acceptNewNodeKey(nodeId: String) {
        updateNode(nodeId) { it.copy(showKeyMismatch = false) }
    }

    fun rejectNewNodeKey(nodeId: String) {
        updateNode(nodeId) { it.copy(showKeyMismatch = false) }
    }

    private fun syncNodeState() {
        val nodes = _uiState.value.nodes
        val connectedSet = mutableListOf<String>()
        nodes.forEach { node ->
            if (node.isConnected) {
                connectedSet.add(if (node.did.isNotEmpty()) node.did.take(12) + "..." else node.name)
            }
        }
        NodeConnectionStateHolder.update(nodes.size, connectedSet)
    }

    private suspend fun connectNode(nodeId: String) = withContext(Dispatchers.IO) {
        val node = _uiState.value.nodes.find { it.id == nodeId } ?: return@withContext
        try {
            // Step 1: Health check
            val healthUrl = URL("http://${node.ip}:${node.port}/healthz")
            val healthConn = (healthUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000; readTimeout = 5000
            }
            if (healthConn.responseCode != 200) throw Exception("节点未响应")

            // Step 2: Get node public key
            val pubKeyUrl = URL("http://${node.ip}:${node.port}/binding/public-key")
            val pubConn = (pubKeyUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000; readTimeout = 5000
            }
            val pubBody = pubConn.inputStream.bufferedReader().readText()
            val pubJson = JSONObject(pubBody)
            val nodePubKey = pubJson.optString("publicKey", "")
            if (nodePubKey.isEmpty()) throw Exception("无法获取节点公钥")

            // Step 3: Register key to CryptoCommLayer
            val pubKeyBytes = Base64.decode(nodePubKey, Base64.NO_WRAP)
            cryptoCommLayer.registerRemotePublicKey(node.did.ifEmpty { "node:$nodeId" }, pubKeyBytes)

            // Step 4: Fetch node info (name, DID, capabilities)
            val infoUrl = URL("http://${node.ip}:${node.port}/api/v1/node/info")
            val infoConn = (infoUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000; readTimeout = 5000
            }
            var nodeName = node.name
            var nodeDid = node.did
            var nodeVersion = ""
            val caps = mutableListOf<String>()
            if (infoConn.responseCode == 200) {
                val infoBody = infoConn.inputStream.bufferedReader().readText()
                val infoJson = JSONObject(infoBody)
                nodeName = infoJson.optString("name", node.name)
                nodeDid = infoJson.optString("did", node.did)
                nodeVersion = infoJson.optString("version", "")
                val capsArr = infoJson.optJSONArray("capabilities")
                if (capsArr != null) {
                    for (i in 0 until capsArr.length()) caps.add(capsArr.getString(i))
                }
            }

            // Step 5: HMAC binding verification
            val bindingStatus = tryHmacBinding(node, nodePubKey)

            // Update node state
            updateNode(nodeId) {
                it.copy(
                    isConnected = true, isConnecting = false,
                    name = nodeName, did = nodeDid,
                    publicKey = nodePubKey, version = nodeVersion,
                    capabilities = caps,
                    services = NodeServiceStatus(
                        storageAvailable = "storage" in caps,
                        tssAvailable = "tss" in caps,
                        aiAvailable = "ai" in caps,
                        paymentAvailable = "payment" in caps
                    ),
                    bindingStatus = bindingStatus,
                    lastConnected = java.text.SimpleDateFormat("MM-dd HH:mm",
                        java.util.Locale.getDefault()).format(System.currentTimeMillis()),
                    error = null
                )
            }
            persistBindingState(nodeId, bindingStatus)

            // 绑定成功后：签发 C-01 代理权凭证 → 建立 WebSocket → 推送凭证
            if (bindingStatus == BindingStatus.BOUND) {
                val master = identityManager.getMasterIdentity()
                if (master != null) {
                    // Step 5b: 签发 C-01 代理权凭证
                    val nodeDID = nodeDid.ifEmpty { "node:${node.id}" }
                    try {
                        val c01 = credentialIssuer.issueAgentDelegation(master.did, nodeDID)
                        Log.i(TAG, "C-01 issued: id=${c01.id} for node=$nodeDID")

                        // Step 6: 建立 WebSocket 连接
                        wsManager.connect(node.ip, node.port, master.did)
                        Log.i(TAG, "WebSocket connected after binding: did=${master.did}")

                        // 首条消息：推送 C-01 到 Node 端
                        wsManager.setOnConnectionEstablished {
                            val issuedMsg = org.json.JSONObject().apply {
                                put("type", "credential_issued")
                                put("payload", org.json.JSONObject().apply {
                                    put("credential", org.json.JSONObject(c01.toJson()))
                                })
                            }
                            wsManager.sendRawMessage(issuedMsg.toString())
                            Log.i(TAG, "C-01 pushed to Node: ${c01.id}")
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "C-01 issuance failed, binding rolled back", ex)
                        updateNode(nodeId) {
                            it.copy(isConnected = false, isConnecting = false,
                                error = "凭证签发失败: ${ex.message}")
                        }
                        persistBindingState(nodeId, BindingStatus.UNBOUND)
                        return@withContext
                    }
                } else {
                    wsManager.connect(node.ip, node.port, master?.did ?: "")
                    Log.i(TAG, "WebSocket connected (no master identity)")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "连接失败", e)
            updateNode(nodeId) {
                it.copy(isConnected = false, isConnecting = false, error = e.message)
            }
        }
    }

    private suspend fun tryHmacBinding(node: NodeConfig, nodePubKey: String): BindingStatus {
        val pairingSeedB64 = node.pairingKey.ifEmpty {
            fetchPairingKey(node.id)
        }
        if (pairingSeedB64.isEmpty()) return BindingStatus.UNBOUND

        try {
            val seed = Base64.decode(pairingSeedB64, Base64.NO_WRAP)
            val challenge = SecureRandom().let { rng -> ByteArray(32).also { rng.nextBytes(it) } }
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(seed, "HmacSHA256"))
            val response = mac.doFinal(challenge)

            val verifyUrl = URL("http://${node.ip}:${node.port}/binding/verify")
            val conn = (verifyUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000; readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val body = JSONObject().apply {
                put("challenge", Base64.encodeToString(challenge, Base64.NO_WRAP))
                put("response", Base64.encodeToString(response, Base64.NO_WRAP))
            }
            conn.outputStream.write(body.toString().toByteArray())

            if (conn.responseCode in 200..299) {
                val respBody = conn.inputStream.bufferedReader().readText()
                val respJson = JSONObject(respBody)
                if (respJson.optBoolean("verified", false)) return BindingStatus.BOUND
                if (respJson.optBoolean("pending", false)) return BindingStatus.PENDING
            }
            return BindingStatus.UNBOUND
        } catch (_: Exception) {
            return BindingStatus.UNBOUND
        }
    }
}
