package com.sovexis.ui.feature.mynode

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sovexis.domain.communication.CryptoCommLayer
import com.sovexis.domain.communication.PreConfiguredKeys
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
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject

/** 节点服务状态 */
data class NodeServiceStatus(
    val storageAvailable: Boolean = false,
    val tssAvailable: Boolean = false,
    val aiAvailable: Boolean = false
)

/** 单个节点配置 */
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
    val error: String? = null
)

data class MyNodeUiState(
    val nodes: List<NodeConfig> = emptyList(),
    val selectedNodeId: String? = null,
    val isAddingNode: Boolean = false,
    // 兼容旧字段（用于节点详情页）
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
    private val cryptoCommLayer: CryptoCommLayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyNodeUiState())
    val uiState: StateFlow<MyNodeUiState> = _uiState.asStateFlow()

    companion object {
        private const val PREFS_NODE = "sovexis_node_config"
        private const val KEY_IP = "node_ip"
        private const val KEY_PORT = "node_port"
        private const val KEY_DID = "node_did"
        private const val KEY_STORAGE = "node_storage_backup"
        private const val KEY_TSS = "node_tss_coo"
        private const val KEY_AI = "node_ai_inference"
        private const val KEY_NODE_PUBKEY = "node_public_key"
        private const val KEY_MANUAL_PUBKEY = "node_manual_public_key"
        private const val KEY_NODES_LIST = "nodes_list"
        private const val KEY_NODE_PREFIX = "node_"
    }

    private val prefs by lazy {
        val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, PREFS_NODE, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    init {
        loadConfig()
        loadNodesList()
        syncNodeState()
        checkConnection()
    }

    private fun loadConfig() {
        try {
            _uiState.update {
                it.copy(
                    nodeIp = prefs.getString(KEY_IP, "192.168.1.100") ?: "192.168.1.100",
                    nodePort = prefs.getInt(KEY_PORT, 8100),
                    nodeDid = prefs.getString(KEY_DID, "") ?: "",
                    storageBackupEnabled = prefs.getBoolean(KEY_STORAGE, false),
                    tssCooEnabled = prefs.getBoolean(KEY_TSS, false),
                    aiInferenceEnabled = prefs.getBoolean(KEY_AI, false),
                    storedNodePublicKey = prefs.getString(KEY_NODE_PUBKEY, "") ?: "",
                    manualPublicKey = prefs.getString(KEY_MANUAL_PUBKEY, "") ?: ""
                )
            }
        } catch (_: Exception) { }
    }

    private fun loadNodesList() {
        try {
            val nodesJson = prefs.getString(KEY_NODES_LIST, null)
            if (nodesJson.isNullOrEmpty()) {
                // 默认创建一个示例节点
                val defaultNode = NodeConfig(
                    id = "default",
                    name = "我的节点",
                    ip = _uiState.value.nodeIp,
                    port = _uiState.value.nodePort,
                    did = _uiState.value.nodeDid,
                    publicKey = _uiState.value.storedNodePublicKey
                )
                _uiState.update { it.copy(nodes = listOf(defaultNode)) }
                saveNodesList(listOf(defaultNode))
            } else {
                // 解析节点列表
                val nodes = parseNodesJson(nodesJson)
                _uiState.update { it.copy(nodes = nodes) }
            }
        } catch (_: Exception) {
            val defaultNode = NodeConfig(id = "default", name = "我的节点")
            _uiState.update { it.copy(nodes = listOf(defaultNode)) }
        }
    }

    private fun parseNodesJson(json: String): List<NodeConfig> {
        // 简单解析：id|name|ip|port|did|pubKey;id|name|...
        return json.split(";").filter { it.isNotBlank() }.map { nodeStr ->
            val parts = nodeStr.split("|")
            NodeConfig(
                id = parts.getOrElse(0) { "unknown" },
                name = parts.getOrElse(1) { "未命名节点" },
                ip = parts.getOrElse(2) { "192.168.1.100" },
                port = parts.getOrElse(3) { "8100" }.toIntOrNull() ?: 8100,
                did = parts.getOrElse(4) { "" },
                publicKey = parts.getOrElse(5) { "" }
            )
        }
    }

    private fun saveNodesList(nodes: List<NodeConfig>) {
        val json = nodes.joinToString(";") { node ->
            "${node.id}|${node.name}|${node.ip}|${node.port}|${node.did}|${node.publicKey}"
        }
        prefs.edit().putString(KEY_NODES_LIST, json).apply()
    }

    fun addNode(node: NodeConfig) {
        val newNodes = _uiState.value.nodes + node
        _uiState.update { it.copy(nodes = newNodes) }
        saveNodesList(newNodes)
        syncNodeState()
    }

    fun updateNode(nodeId: String, update: (NodeConfig) -> NodeConfig) {
        val newNodes = _uiState.value.nodes.map { node ->
            if (node.id == nodeId) update(node) else node
        }
        _uiState.update { it.copy(nodes = newNodes) }
        saveNodesList(newNodes)
    }

    fun deleteNode(nodeId: String) {
        val newNodes = _uiState.value.nodes.filter { it.id != nodeId }
        _uiState.update { it.copy(nodes = newNodes, selectedNodeId = null) }
        saveNodesList(newNodes)
        syncNodeState()
    }

    fun toggleNodeEnabled(nodeId: String) {
        val node = _uiState.value.nodes.find { it.id == nodeId } ?: return
        if (!node.isEnabled) {
            updateNode(nodeId) { it.copy(isEnabled = true, isConnecting = true) }
            connectNode(nodeId)
        } else {
            updateNode(nodeId) { it.copy(isEnabled = false, isConnected = false) }
            syncNodeState()
        }
    }

    fun connectNode(nodeId: String) {
        val node = _uiState.value.nodes.find { it.id == nodeId } ?: return
        
        updateNode(nodeId) { it.copy(isConnecting = true, error = null) }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://${node.ip}:${node.port}/healthz")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val startTime = System.currentTimeMillis()
                val code = conn.responseCode
                val latency = (System.currentTimeMillis() - startTime).toInt()
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val serverPubKey = extractJsonField(body, "publicKey")
                val serverDid = extractJsonField(body, "did")
                val version = extractJsonField(body, "version").ifEmpty { "unknown" }
                val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())

                // 检查服务可用性
                val services = checkServicesAvailable(node.ip, node.port)

                updateNode(nodeId) {
                    it.copy(
                        isConnected = true,
                        isConnecting = false,
                        isEnabled = true,
                        did = serverDid.ifEmpty { it.did },
                        publicKey = serverPubKey.ifEmpty { it.publicKey },
                        version = version,
                        latency = latency,
                        lastConnected = now,
                        services = services,
                        noiseReady = serverPubKey.isNotEmpty(),
                        error = null
                    )
                }
                syncNodeState()
            } catch (e: Exception) {
                updateNode(nodeId) {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        error = e.message ?: "连接失败"
                    )
                }
                syncNodeState()
            }
        }
    }

    private fun checkServicesAvailable(ip: String, port: Int): NodeServiceStatus {
        // 检查各服务端点是否可用
        var storage = false
        var tss = false
        var ai = false
        
        try {
            val url = URL("http://$ip:$port/api/v1/storage/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            storage = conn.responseCode == 200
            conn.disconnect()
        } catch (_: Exception) {}
        
        try {
            val url = URL("http://$ip:$port/api/v1/tss/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            tss = conn.responseCode == 200
            conn.disconnect()
        } catch (_: Exception) {}
        
        try {
            val url = URL("http://$ip:$port/api/v1/ai/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            ai = conn.responseCode == 200
            conn.disconnect()
        } catch (_: Exception) {}
        
        return NodeServiceStatus(storage, tss, ai)
    }

    fun selectNode(nodeId: String?) {
        _uiState.update { it.copy(selectedNodeId = nodeId) }
    }

    /** 同步节点连接状态到全局持有者（抽屉菜单读取） */
    private fun syncNodeState() {
        val nodes = _uiState.value.nodes
        val connected = nodes.filter { it.isConnected }.map { it.name }
        NodeConnectionStateHolder.update(nodes.size, connected)
    }

    /** 仅持久化 IP/端口，不自动连接（由 Button 显式调用 connect()）。 */
    fun setNode(ip: String, port: Int) {
        prefs.edit().putString(KEY_IP, ip).putInt(KEY_PORT, port).apply()
        _uiState.update { it.copy(nodeIp = ip, nodePort = port) }
    }

    fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isConnecting = true, error = null) }

            // 0. 提前注册手动输入的公钥
            val manualKey = _uiState.value.manualPublicKey
            if (manualKey.isNotEmpty()) {
                try {
                    val keyBytes = Base64.decode(manualKey, Base64.DEFAULT)
                    val nodeDid = _uiState.value.nodeDid.ifEmpty {
                        val ipPort = "${_uiState.value.nodeIp}:${_uiState.value.nodePort}"
                        "node:${sha256hex(ipPort).take(16)}"
                    }
                    cryptoCommLayer.registerRemotePublicKey(nodeDid, keyBytes)
                    PreConfiguredKeys.registerPublicKey(nodeDid, keyBytes)
                    prefs.edit().putString(KEY_NODE_PUBKEY, manualKey).apply()
                    _uiState.update { it.copy(storedNodePublicKey = manualKey) }
                } catch (_: Exception) { }
            }

            try {
                val nodeIp = _uiState.value.nodeIp
                val nodePort = _uiState.value.nodePort
                val url = URL("http://$nodeIp:$nodePort/healthz")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000; conn.readTimeout = 3000
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val serverPubKey = extractJsonField(body, "publicKey")
                val serverDid = extractJsonField(body, "did")
                val version = extractJsonField(body, "version").ifEmpty { body.take(40) }
                val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())

                val effectiveKey = manualKey.ifEmpty { serverPubKey }
                val storedKey = _uiState.value.storedNodePublicKey

                if (effectiveKey.isNotEmpty() && storedKey.isNotEmpty() && effectiveKey != storedKey) {
                    _uiState.update {
                        it.copy(isConnecting = false, showKeyMismatch = true,
                            nodePublicKey = effectiveKey, nodeVersion = version,
                            lastConnectedTime = now, nodeDid = serverDid)
                    }
                } else {
                    establishNoiseSession(serverDid, effectiveKey, serverPubKey, version, now)
                }
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotEmpty() }
                    ?: "连接失败，请检查网络配置和节点公钥"
                _uiState.update {
                    it.copy(isConnected = false, isConnecting = false, error = errorMsg)
                }
            }
        }
    }

    private suspend fun establishNoiseSession(
        serverDid: String,
        effectiveKey: String,
        serverPubKey: String,
        version: String,
        now: String
    ) {
        try {
            val nodeDid = serverDid.ifEmpty {
                val ipPort = "${_uiState.value.nodeIp}:${_uiState.value.nodePort}"
                "node:${sha256hex(ipPort).take(16)}"
            }
            prefs.edit().putString(KEY_DID, nodeDid).apply()

            var keyRegistered = false
            if (effectiveKey.isNotEmpty()) {
                val keyBytes = Base64.decode(effectiveKey, Base64.DEFAULT)
                cryptoCommLayer.registerRemotePublicKey(nodeDid, keyBytes)
                PreConfiguredKeys.registerPublicKey(nodeDid, keyBytes)
                keyRegistered = true
                if (serverPubKey.isNotEmpty()) {
                    prefs.edit().putString(KEY_NODE_PUBKEY, serverPubKey).apply()
                } else {
                    prefs.edit().putString(KEY_NODE_PUBKEY, effectiveKey).apply()
                }
            }

            _uiState.update {
                it.copy(
                    isConnected = true, isConnecting = false, showKeyMismatch = false,
                    nodePublicKey = effectiveKey.ifEmpty { serverPubKey },
                    storedNodePublicKey = effectiveKey.ifEmpty { serverPubKey },
                    nodeVersion = version, lastConnectedTime = now, nodeDid = nodeDid,
                    noiseKeyRegistered = keyRegistered
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isConnected = true, isConnecting = false,
                    nodeDid = serverDid.ifEmpty {
                        "node:${sha256hex("${_uiState.value.nodeIp}:${_uiState.value.nodePort}").take(16)}"
                    },
                    nodeVersion = version, lastConnectedTime = now)
            }
        }
    }

    fun checkConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(500L)
            connect()
        }
    }

    fun setStorageBackup(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STORAGE, enabled).apply()
        _uiState.update { it.copy(storageBackupEnabled = enabled) }
    }

    fun setTssCoo(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TSS, enabled).apply()
        _uiState.update { it.copy(tssCooEnabled = enabled) }
    }

    fun setAiInference(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI, enabled).apply()
        _uiState.update { it.copy(aiInferenceEnabled = enabled) }
    }

    fun acceptNewKey() {
        val key = _uiState.value.nodePublicKey
        prefs.edit().putString(KEY_NODE_PUBKEY, key).apply()
        _uiState.update { it.copy(storedNodePublicKey = key, showKeyMismatch = false, isConnected = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nodeDid = _uiState.value.nodeDid
                val keyBytes = Base64.decode(key, Base64.DEFAULT)
                cryptoCommLayer.registerRemotePublicKey(nodeDid, keyBytes)
                PreConfiguredKeys.registerPublicKey(nodeDid, keyBytes)
                _uiState.update { it.copy(noiseKeyRegistered = true) }
            } catch (_: Exception) { }
        }
    }

    fun rejectNewKey() {
        _uiState.update {
            it.copy(showKeyMismatch = false, isConnected = false,
                error = "公钥不匹配，已断开连接")
        }
    }

    fun setNodePublicKey(key: String) {
        prefs.edit().putString(KEY_MANUAL_PUBKEY, key).apply()
        _uiState.update { it.copy(manualPublicKey = key) }
    }

    private fun extractJsonField(json: String, field: String): String {
        return try {
            val keyIdx = json.indexOf("\"$field\"")
            if (keyIdx < 0) return ""
            val colonIdx = json.indexOf(':', keyIdx)
            if (colonIdx < 0) return ""
            val slice = json.substring(colonIdx + 1).trimStart()
            if (slice.startsWith("\"")) {
                slice.substring(1).takeWhile { it != '"' }
            } else {
                slice.takeWhile { it != ',' && it != '}' && it != '\n' }.trim()
            }
        } catch (_: Exception) { "" }
    }

    private fun sha256hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
