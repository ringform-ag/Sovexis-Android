package com.sovexis.domain.vault

import android.util.Log
import com.sovexis.domain.communication.WebSocketManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 保险箱数据同步管理器
 *
 * 通过已建立的 WebSocket 通道将加密笔记同步至自有 Node 节点。
 *
 * 核心原则：
 * - 手动触发为主，自动同步为可选
 * - 传输保持加密，不解密
 * - 仅同步已绑定自有节点
 */
@Singleton
class VaultSyncManager @Inject constructor(
    private val wsManager: WebSocketManager
) {
    companion object {
        private const val TAG = "VaultSyncMgr"
        private const val TIMEOUT_SECONDS = 30L
    }

    enum class SyncStatus { IDLE, SYNCING, SUCCESS, PARTIAL, FAILED }
    enum class ItemSyncStatus { SYNCED, PENDING_SYNC, CONFLICT }

    data class SyncableItem(
        val itemId: String,
        val encryptedData: String,   // Base64 AES-GCM ciphertext
        val iv: String,              // Base64 IV
        val timestamp: Long
    )

    data class SyncResult(
        val status: SyncStatus,
        val syncedCount: Int,
        val failedItems: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _syncState = MutableStateFlow(SyncStatus.IDLE)
    val syncState: StateFlow<SyncStatus> = _syncState.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    // Items pending sync: itemId → SyncableItem
    private val pendingItems = mutableMapOf<String, SyncableItem>()

    // Item sync status: itemId → ItemSyncStatus
    private val itemStatuses = mutableMapOf<String, ItemSyncStatus>()

    // Callback for UI refresh after sync
    private var onSyncCompleted: ((SyncResult) -> Unit)? = null

    /**
     * Queue an item for sync.
     */
    fun markPendingSync(itemId: String, encryptedData: String, iv: String) {
        pendingItems[itemId] = SyncableItem(
            itemId = itemId,
            encryptedData = encryptedData,
            iv = iv,
            timestamp = System.currentTimeMillis()
        )
        itemStatuses[itemId] = ItemSyncStatus.PENDING_SYNC
        _pendingCount.value = pendingItems.size
    }

    /**
     * Mark an item as created/re-edited → PENDING_SYNC.
     */
    fun onItemModified(itemId: String, encryptedData: String, iv: String) {
        markPendingSync(itemId, encryptedData, iv)
    }

    /**
     * Get sync status for a specific item.
     */
    fun getItemSyncStatus(itemId: String): ItemSyncStatus {
        return itemStatuses[itemId] ?: ItemSyncStatus.SYNCED
    }

    /**
     * Manual sync — triggers via WebSocket.
     */
    fun syncNow() {
        if (pendingItems.isEmpty()) {
            _syncState.value = SyncStatus.SUCCESS
            return
        }
        if (_syncState.value == SyncStatus.SYNCING) return

        _syncState.value = SyncStatus.SYNCING

        val sessionId = UUID.randomUUID().toString()
        val items = JSONArray()
        val snapshotMap = pendingItems.toMap()

        for ((_, item) in snapshotMap) {
            items.put(JSONObject().apply {
                put("itemId", item.itemId)
                put("encryptedData", item.encryptedData)
                put("iv", item.iv)
                put("timestamp", item.timestamp)
            })
        }

        val msg = JSONObject().apply {
            put("type", "vault_sync")
            put("sessionId", sessionId)
            put("items", items)
            put("timestamp", System.currentTimeMillis())
        }

        // Register one-time response handler
        val job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // Send via WebSocket
            wsManager.sendRawMessage(msg.toString())

            // Wait for response with timeout
            val result = withTimeoutOrNull(TIMEOUT_SECONDS * 1000L) {
                suspendCancellableCoroutine<SyncResult> { cont ->
                    val handler: (String, String) -> Unit = { responseSessionId, responseJson ->
                        if (responseSessionId == sessionId) {
                            try {
                                val resp = JSONObject(responseJson)
                                val statusStr = resp.optString("status", "failed")
                                val syncedCount = resp.optInt("syncedCount", 0)
                                val failedArr = resp.optJSONArray("failedItems")
                                val failedList = mutableListOf<String>()
                                if (failedArr != null) {
                                    for (i in 0 until failedArr.length()) {
                                        failedList.add(failedArr.getString(i))
                                    }
                                }
                                val syncResult = SyncResult(
                                    status = when (statusStr) {
                                        "success" -> SyncStatus.SUCCESS
                                        "partial" -> SyncStatus.PARTIAL
                                        else -> SyncStatus.FAILED
                                    },
                                    syncedCount = syncedCount,
                                    failedItems = failedList
                                )
                                cont.resume(syncResult) {}
                            } catch (_: Exception) {
                                cont.resume(SyncResult(SyncStatus.FAILED, 0, emptyList())) {}
                            }
                        }
                    }
                    // Register handler on the wsManager side
                    wsManager.registerVaultSyncResponseHandler(sessionId, handler)
                }
            }

            val finalResult = result ?: SyncResult(SyncStatus.FAILED, 0, emptyList(), System.currentTimeMillis())

            // Update statuses based on result
            when (finalResult.status) {
                SyncStatus.SUCCESS -> {
                    snapshotMap.keys.forEach { id ->
                        pendingItems.remove(id)
                        itemStatuses[id] = ItemSyncStatus.SYNCED
                    }
                }
                SyncStatus.PARTIAL -> {
                    snapshotMap.keys.forEach { id ->
                        if (id !in finalResult.failedItems) {
                            pendingItems.remove(id)
                            itemStatuses[id] = ItemSyncStatus.SYNCED
                        }
                    }
                }
                SyncStatus.FAILED -> {
                    // All remain PENDING_SYNC
                }
                else -> {}
            }

            _pendingCount.value = pendingItems.size
            _lastSyncedAt.value = System.currentTimeMillis()
            _lastSyncResult.value = finalResult
            _syncState.value = finalResult.status
            onSyncCompleted?.invoke(finalResult)
        }

        // Register a timeout fallback
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            delay(TIMEOUT_SECONDS * 1000L + 2000L)
            if (_syncState.value == SyncStatus.SYNCING) {
                _syncState.value = SyncStatus.FAILED
                _lastSyncResult.value = SyncResult(SyncStatus.FAILED, 0, emptyList())
                onSyncCompleted?.invoke(SyncResult(SyncStatus.FAILED, 0, emptyList()))
            }
        }
    }

    /**
     * Background sync — app going to background, try to sync pending items.
     * Limited to 30s, no retry on failure.
     */
    fun backgroundSync() {
        if (pendingItems.isEmpty() || _syncState.value == SyncStatus.SYNCING) return
        syncNow()
    }

    /**
     * Auto sync after create/edit — only if user enabled it.
     */
    fun autoSyncIfEnabled(autoSyncEnabled: Boolean) {
        if (!autoSyncEnabled || pendingItems.isEmpty()) return
        syncNow()
    }

    fun setOnSyncCompleted(callback: (SyncResult) -> Unit) {
        onSyncCompleted = callback
    }

    fun getPendingItemsForDisplay(): List<SyncableItem> {
        return pendingItems.values.toList()
    }

    fun reset() {
        pendingItems.clear()
        itemStatuses.clear()
        _pendingCount.value = 0
        _syncState.value = SyncStatus.IDLE
        _lastSyncResult.value = null
        _lastSyncedAt.value = null
    }
}
