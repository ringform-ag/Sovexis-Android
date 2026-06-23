package com.sovexis.ui.feature.safebox

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.communication.CryptoCommLayer
import com.sovexis.domain.communication.WebSocketManager
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.MasterIdentity
import com.sovexis.domain.storage.VaultDao
import com.sovexis.domain.storage.VaultItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

data class SafeBoxUiState(
    val items: List<VaultItemEntity> = emptyList(),
    val activeAccount: MasterIdentity? = null,
    val activeDid: String? = null,
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SafeBoxViewModel @Inject constructor(
    private val vaultDao: VaultDao,
    private val identityManager: IdentityManager,
    private val wsManager: WebSocketManager? = null,
    private val cryptoCommLayer: CryptoCommLayer? = null,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafeBoxUiState())
    val uiState: StateFlow<SafeBoxUiState> = _uiState.asStateFlow()

    init {
        loadAccountAndItems()
    }

    private fun loadAccountAndItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // 获取当前活跃的身份
            val activeAccount = identityManager.getMasterIdentity()
            val activeDid = identityManager.getActiveDid()

            _uiState.update { state ->
                state.copy(
                    activeAccount = activeAccount,
                    activeDid = activeDid
                )
            }

            // 如果存在活跃 DID，加载对应的保险箱条目
            if (activeDid != null) {
                val items = withContext(Dispatchers.IO) {
                    vaultDao.getItems(activeDid)
                }
                _uiState.update { state ->
                    state.copy(
                        items = items,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 刷新保险箱列表（供外部调用，如在新增/删除条目后）
     */
    @Deprecated("外部刷新无 UI 调用，使用 loadItems 替代")
    fun refreshItems() {
        loadAccountAndItems()
    }

    /** 删除保险箱条目 */
    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { vaultDao.delete(itemId) }
                _uiState.update { it.copy(message = "已删除") }
                loadAccountAndItems()
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "删除失败: ${e.message}") }
            }
        }
    }

    /** 上传条目到节点（通过 WebSocket vault_sync 或 CryptoCommLayer） */
    fun uploadToNode(itemId: String) {
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { vaultDao.getItem(itemId) }
                    ?: run { _uiState.update { it.copy(message = "条目不存在") }; return@launch }
                val nodeDid = _uiState.value.activeDid
                    ?: run { _uiState.update { it.copy(message = "无活跃身份") }; return@launch }

                // 构建 vault_sync 消息
                val syncMsg = org.json.JSONObject().apply {
                    put("type", "vault_sync")
                    put("payload", org.json.JSONObject().apply {
                        put("item_id", item.id)
                        put("owner_did", item.ownerDid)
                        put("title_cipher", android.util.Base64.encodeToString(item.titleCipher, android.util.Base64.NO_WRAP))
                        put("content_cipher", android.util.Base64.encodeToString(item.contentCipher, android.util.Base64.NO_WRAP))
                        put("iv", android.util.Base64.encodeToString(item.iv, android.util.Base64.NO_WRAP))
                        put("updated_at", item.updatedAt)
                    })
                }

                if (wsManager != null) {
                    wsManager.sendRawMessage(syncMsg.toString())
                    _uiState.update { it.copy(message = "已发送上传请求") }
                } else if (cryptoCommLayer != null) {
                    cryptoCommLayer.send(item.contentCipher, nodeDid)
                    _uiState.update { it.copy(message = "已发送上传请求（待节点确认）") }
                } else {
                    _uiState.update { it.copy(message = "通信层未就绪（需连接 Node）") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "上传失败: ${e.message}") }
            }
        }
    }

    /**
     * 加密并新增保险箱条目。
     *
     * 使用 AES-256-GCM，密钥由设备绑定数据 + owner DID 派生。
     */
    fun addEncryptedItem(title: String, content: String) {
        viewModelScope.launch {
            try {
                val ownerDid = _uiState.value.activeDid
                    ?: run { _uiState.update { it.copy(message = "无活跃身份") }; return@launch }
                val key = deriveEncryptionKey(ownerDid)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")

                // 各自独立 IV，禁止复用（AES-GCM nonce 必须唯一）
                val ivTitle = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
                val ivContent = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }

                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, ivTitle))
                val titleCipher = cipher.doFinal(title.toByteArray(Charsets.UTF_8))
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, ivContent))
                val contentCipher = cipher.doFinal(content.toByteArray(Charsets.UTF_8))

                // 存储 content 的 IV（title 的 IV 存储在 VaultItemEntity.iv 中）；
                // content IV 拼接在 contentCipher 末尾（12 bytes），读取时分离。
                val storedCipher = contentCipher + ivContent

                val entity = VaultItemEntity(
                    id = UUID.randomUUID().toString(),
                    ownerDid = ownerDid,
                    titleCipher = titleCipher,
                    contentCipher = storedCipher,
                    iv = ivTitle,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) { vaultDao.insert(entity) }
                _uiState.update { it.copy(message = "已加密保存") }
                loadAccountAndItems()
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "加密失败: ${e.message}") }
            }
        }
    }

    /**
     * 解密保险箱条目内容。
     *
     * @return Pair(标题, 内容) 或 null
     */
    suspend fun decryptItem(item: VaultItemEntity): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val ownerDid = item.ownerDid
                val key = deriveEncryptionKey(ownerDid)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")

                // title 用 item.iv 解密
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, item.iv))
                val title = String(cipher.doFinal(item.titleCipher), Charsets.UTF_8)

                // content 的 IV 在末尾 12 bytes
                val contentBody = item.contentCipher.copyOf(item.contentCipher.size - 12)
                val contentIv = item.contentCipher.copyOfRange(item.contentCipher.size - 12, item.contentCipher.size)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, contentIv))
                val content = String(cipher.doFinal(contentBody), Charsets.UTF_8)

                title to content
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 从设备绑定 + owner DID 派生 32 字节加密密钥。
     */
    private fun deriveEncryptionKey(ownerDid: String): ByteArray {
        val deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val binding = "$deviceId:$ownerDid:SovexisSafeBox"
        return MessageDigest.getInstance("SHA-256").digest(binding.toByteArray(Charsets.UTF_8)).copyOf(32)
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
