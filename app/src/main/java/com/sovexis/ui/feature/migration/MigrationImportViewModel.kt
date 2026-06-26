package com.sovexis.ui.feature.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.identity.IdentityMigration
import com.sovexis.domain.crypto.DeviceFingerprint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject

enum class MigrationImportPhase { Start, Input, RebindNode, Complete }

data class MigrationImportState(
    val phase: MigrationImportPhase = MigrationImportPhase.Start,
    val loading: Boolean = false,
    val error: String? = null,
    val rebindStatus: String? = null
)

@HiltViewModel
class MigrationImportViewModel @Inject constructor(
    private val identityMigration: IdentityMigration,
    private val deviceFingerprint: DeviceFingerprint
) : ViewModel() {

    private val _state = MutableStateFlow(MigrationImportState())
    val state: StateFlow<MigrationImportState> = _state.asStateFlow()

    fun advance() {
        _state.update { it.copy(phase = MigrationImportPhase.Input) }
    }

    fun cancel() {
        _state.update { MigrationImportState() }
    }

    fun import(did: String, encodedData: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val sessionKey = deriveSessionKey(did)

            val result = withContext(Dispatchers.IO) {
                identityMigration.import(encodedData, sessionKey, did)
            }

            result.onSuccess {
                _state.update { it.copy(loading = false, phase = MigrationImportPhase.RebindNode) }
                // 自动触发 Node 重绑定
                rebindNode(did)
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, error = "导入失败: ${e.message}")
                }
            }
        }
    }

    private suspend fun rebindNode(did: String) {
        _state.update { it.copy(loading = true, rebindStatus = "正在连接节点…") }
        withContext(Dispatchers.IO) {
            try {
                val newFp = deviceFingerprint.getDeviceFingerprint()
                // Build migration request body
                val body = JSONObject().apply {
                    put("did", did)
                    put("device_fingerprint", newFp)
                }
                // POST /binding/migrate to the connected Node
                // Node IP/port obtained from settings or shared preferences; fallback to localhost
                val nodeIp = "127.0.0.1"  // TODO: resolve from NodeDiscovery or settings
                val nodePort = 8100
                val url = URL("http://$nodeIp:$nodePort/binding/migrate")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    _state.update { it.copy(loading = false, phase = MigrationImportPhase.Complete, rebindStatus = "节点绑定迁移完成") }
                } else {
                    val errMsg = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                    _state.update { it.copy(loading = false, error = "节点绑定迁移失败: $errMsg") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "节点绑定迁移失败: ${e.message}") }
            }
        }
    }

    private fun deriveSessionKey(did: String): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256").digest(did.toByteArray())
        return hash.copyOf(32)
    }
}
