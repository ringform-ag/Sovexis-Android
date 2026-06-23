package com.sovexis.ui.feature.vault

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.storage.PlainVaultItem
import com.sovexis.domain.storage.StorageObfuscator
import com.sovexis.domain.storage.VaultDao
import com.sovexis.domain.sync.NodeSyncClient
import com.sovexis.domain.zkp.RootDetector
import com.sovexis.domain.zkp.ZkpCacheManager
import com.sovexis.ui.components.NotificationHolder
import com.sovexis.ui.components.NotificationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class VaultStep { IDLE, BIOMETRIC_PROMPT, KDFS_DRAW, DECRYPTING, ENCRYPTING, DELETING, COMPLETED, FAILED, SYNCING }
enum class VaultOperationType { READ, WRITE, DELETE }

data class SyncHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val successCount: Int = 0,
    val failCount: Int = 0,
    val totalItems: Int = 0,
    val status: String = "success" // success / partial / failed
)

data class VaultState(
    val step: VaultStep = VaultStep.IDLE,
    val items: List<PlainVaultItem> = emptyList(),
    val isLoading: Boolean = false,
    val selectedItem: PlainVaultItem? = null,
    val isEditing: Boolean = false,
    val editTitle: String = "",
    val editContent: String = "",
    val error: String? = null,
    val successMessage: String? = null,
    val ownerDid: String = "",
    val masterName: String = "",
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val syncedItemIds: Set<String> = emptySet(),
    val lastSyncAt: Long = 0,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val pinnedItemIds: Set<String> = emptySet(),
    val syncHistory: List<SyncHistoryEntry> = emptyList(),
    val showSyncHistory: Boolean = false,
    val syncFailCount: Int = 0,
    val showInlineEditor: Boolean = false,
    val inlineTitle: String = "",
    val inlineContent: String = ""
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val storageObfuscator: StorageObfuscator,
    private val identityManager: IdentityManager,
    private val zkpCacheManager: ZkpCacheManager,
    private val syncClient: NodeSyncClient,
    private val vaultDao: VaultDao
) : ViewModel() {

    companion object {
        private const val TAG = "VaultViewModel"
        private const val PREFS_SYNC_HISTORY = "vault_sync_history"
    }

    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private var pendingOperation: (() -> Unit)? = null

    fun init() {
        viewModelScope.launch {
            try {
                val ownerDid = identityManager.getActiveDid() ?: ""
                val master = identityManager.getMasterIdentity()
                val masterName = master?.alias ?: ""
                _state.update { it.copy(ownerDid = ownerDid, masterName = masterName) }
                if (ownerDid.isNotEmpty()) {
                    loadItems(ownerDid)
                    refreshSyncStatus()
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "初始化失败: ${e.message}") }
            }
        }
    }

    fun loadItems(ownerDid: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val authorName = _state.value.masterName
                val items = storageObfuscator.listItems(ownerDid)
                    .map { it.copy(authorName = authorName) }
                _state.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "加载失败: ${e.message}") }
            }
        }
    }

    // ── 同步 ──

    fun syncAllToNode() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncMessage = "正在同步...", error = null) }
            try {
                val items = _state.value.items
                if (items.isEmpty()) {
                    _state.update { it.copy(isSyncing = false, syncMessage = "没有需要同步的笔记") }
                    return@launch
                }

                var successCount = 0
                var failCount = 0
                for (item in items) {
                    val entity = vaultDao.getItem(item.id)
                    if (entity == null) { failCount++; continue }
                    val syncItem = NodeSyncClient.SyncVaultItem(
                        id = item.id,
                        titleCipher = android.util.Base64.encodeToString(entity.titleCipher, android.util.Base64.NO_WRAP),
                        contentCipher = android.util.Base64.encodeToString(entity.contentCipher, android.util.Base64.NO_WRAP),
                        iv = android.util.Base64.encodeToString(entity.iv, android.util.Base64.NO_WRAP),
                        createdAt = entity.createdAt,
                        modifiedAt = entity.updatedAt
                    )
                    val result = syncClient.uploadVaultItem(syncItem)
                    if (result.isSuccess) { successCount++; _state.update { it.copy(syncedItemIds = it.syncedItemIds + item.id) } }
                    else { failCount++; Log.w(TAG, "同步失败: ${item.id}, ${result.exceptionOrNull()?.message}") }
                }

                val status = when {
                    failCount == 0 -> "success"
                    successCount == 0 -> "failed"
                    else -> "partial"
                }
                val entry = SyncHistoryEntry(
                    successCount = successCount, failCount = failCount,
                    totalItems = items.size, status = status
                )
                // Keep last 50 history entries, prune old ones
                val newHistory = (listOf(entry) + _state.value.syncHistory).take(50)

                _state.update {
                    it.copy(isSyncing = false,
                        syncMessage = "同步完成: $successCount 成功, $failCount 失败",
                        lastSyncAt = System.currentTimeMillis(),
                        syncHistory = newHistory,
                        syncFailCount = if (failCount > 0) it.syncFailCount + failCount else it.syncFailCount)
                }

                if (failCount > 0) {
                    NotificationHolder.add(NotificationItem(
                        id = "sync_${System.currentTimeMillis()}",
                        title = "保险箱同步",
                        message = "$successCount 成功, $failCount 失败",
                        action = "sync_failed"))
                } else {
                    NotificationHolder.add(NotificationItem(
                        id = "sync_${System.currentTimeMillis()}",
                        title = "保险箱同步完成",
                        message = "全部 $successCount 条笔记已同步",
                        action = "sync_success"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步异常", e)
                _state.update { it.copy(isSyncing = false, syncMessage = "同步失败: ${e.message}", error = e.message) }
            }
        }
    }

    fun refreshSyncStatus() {
        viewModelScope.launch {
            try {
                val manifest = syncClient.getVaultManifest()
                manifest.onSuccess { entries ->
                    _state.update { it.copy(syncedItemIds = entries.map { e -> e.id }.toSet()) }
                }
                val status = syncClient.getSyncStatus()
                status.onSuccess { s ->
                    if (s.lastSyncAt > 0) {
                        _state.update { it.copy(lastSyncAt = s.lastSyncAt * 1000) }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun clearSyncFailCount() { _state.update { it.copy(syncFailCount = 0) } }
    fun deleteSyncHistoryEntry(id: String) {
        _state.update { it.copy(syncHistory = it.syncHistory.filter { e -> e.id != id }) }
    }
    fun clearAllSyncHistory() { _state.update { it.copy(syncHistory = emptyList(), syncFailCount = 0) } }
    fun showSyncHistory() { _state.update { it.copy(showSyncHistory = true) } }
    fun hideSyncHistory() { _state.update { it.copy(showSyncHistory = false) } }

    // ── 原有操作 ──

    fun initiateRead(itemId: String, ownerDid: String) {
        pendingOperation = {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, step = VaultStep.DECRYPTING) }
                try {
                    val item = storageObfuscator.obfuscatedRead(itemId, ownerDid)
                        .copy(authorName = _state.value.masterName)
                    _state.update { it.copy(selectedItem = item, isLoading = false, step = VaultStep.IDLE, isEditing = false) }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, step = VaultStep.FAILED, error = "解密失败: ${e.message}") }
                }
            }
        }
        beginAuthentication(VaultOperationType.READ)
    }

    fun initiateWrite(ownerDid: String, title: String, content: String) {
        val itemId = UUID.randomUUID().toString()
        pendingOperation = {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, step = VaultStep.ENCRYPTING) }
                try {
                    storageObfuscator.obfuscatedWrite(itemId, ownerDid, title, content)
                    _state.update { it.copy(
                        isLoading = false, step = VaultStep.COMPLETED,
                        successMessage = "加密保存成功", showInlineEditor = false
                    ) }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, step = VaultStep.FAILED, error = "加密保存失败: ${e.message}") }
                }
            }
        }
        beginAuthentication(VaultOperationType.WRITE)
    }

    fun initiateDelete(itemId: String, ownerDid: String) {
        pendingOperation = {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, step = VaultStep.DELETING) }
                try {
                    storageObfuscator.obfuscatedDelete(itemId, ownerDid)
                    _state.update { it.copy(isLoading = false, step = VaultStep.COMPLETED, successMessage = "已删除") }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, step = VaultStep.FAILED, error = "删除失败: ${e.message}") }
                }
            }
        }
        beginAuthentication(VaultOperationType.DELETE)
    }

    fun enterEditMode() {
        val item = _state.value.selectedItem ?: return
        _state.update { it.copy(isEditing = true, editTitle = item.title, editContent = item.content) }
    }

    // ── 内联编辑面板 ──
    fun showInlineEditor() { _state.update { it.copy(showInlineEditor = true, inlineTitle = "", inlineContent = "") } }
    fun hideInlineEditor() { _state.update { it.copy(showInlineEditor = false, inlineTitle = "", inlineContent = "") } }
    fun updateInlineTitle(t: String) { _state.update { it.copy(inlineTitle = t) } }
    fun updateInlineContent(c: String) { _state.update { it.copy(inlineContent = c) } }

    fun startNewItem() {
        _state.update { it.copy(step = VaultStep.IDLE, isEditing = true, selectedItem = null, editTitle = "", editContent = "") }
    }

    fun cancelEdit() {
        _state.update { it.copy(isEditing = false, editTitle = "", editContent = "", showInlineEditor = false) }
    }

    // ── 搜索 ──

    fun startSearch() { _state.update { it.copy(isSearching = true, searchQuery = "") } }
    fun updateSearch(q: String) { _state.update { it.copy(searchQuery = q) } }
    fun exitSearch() { _state.update { it.copy(isSearching = false, searchQuery = "") } }

    // ── Pin ──

    fun togglePin(itemId: String) {
        _state.update { state ->
            val pins = state.pinnedItemIds.toMutableSet()
            if (pins.contains(itemId)) pins.remove(itemId) else pins.add(itemId)
            state.copy(pinnedItemIds = pins)
        }
    }

    fun onBiometricSuccess() {
        val needsKdfs = needsKdfsPattern()
        if (needsKdfs) {
            viewModelScope.launch {
                val cachedKdfs = zkpCacheManager.getCachedKdfs()
                if (cachedKdfs != null) {
                    pendingOperation?.invoke(); pendingOperation = null
                } else {
                    _state.update { it.copy(step = VaultStep.KDFS_DRAW) }
                }
            }
        } else {
            pendingOperation?.invoke(); pendingOperation = null
        }
    }

    fun onKdfsComplete(kdfsHash: ByteArray) {
        viewModelScope.launch {
            zkpCacheManager.putCachedKdfs(kdfsHash)
            pendingOperation?.invoke(); pendingOperation = null
        }
    }

    fun onBiometricFailed(error: String) {
        _state.update { it.copy(step = VaultStep.FAILED, error = error) }
        pendingOperation = null
    }

    fun reset() {
        _state.update { it.copy(step = VaultStep.IDLE, selectedItem = null, isEditing = false, error = null, successMessage = null, syncMessage = null, showInlineEditor = false) }
    }

    private fun beginAuthentication(type: VaultOperationType) {
        _state.update { it.copy(step = VaultStep.BIOMETRIC_PROMPT) }
    }

    private fun needsKdfsPattern(): Boolean = true
}
