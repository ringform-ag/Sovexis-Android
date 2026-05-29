package com.sovexis.ui.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.storage.PlainVaultItem
import com.sovexis.domain.storage.StorageObfuscator
import com.sovexis.domain.zkp.RootDetector
import com.sovexis.domain.zkp.ZkpCacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class VaultStep { IDLE, BIOMETRIC_PROMPT, KDFS_DRAW, DECRYPTING, ENCRYPTING, DELETING, COMPLETED, FAILED }
enum class VaultOperationType { READ, WRITE, DELETE }

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
    val ownerDid: String = ""
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val storageObfuscator: StorageObfuscator,
    private val identityManager: IdentityManager,
    private val zkpCacheManager: ZkpCacheManager
) : ViewModel() {

    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private var pendingOperation: (() -> Unit)? = null

    fun init() {
        viewModelScope.launch {
            try {
                val ownerDid = identityManager.getActiveDid() ?: ""
                _state.update { it.copy(ownerDid = ownerDid) }
                if (ownerDid.isNotEmpty()) loadItems(ownerDid)
            } catch (e: Exception) {
                _state.update { it.copy(error = "初始化失败: ${e.message}") }
            }
        }
    }

    private fun ownerDid(): String = _state.value.ownerDid

    fun loadItems(ownerDid: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val items = storageObfuscator.listItems(ownerDid)
                _state.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "加载失败: ${e.message}") }
            }
        }
    }

    fun initiateRead(itemId: String, ownerDid: String) {
        pendingOperation = {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, step = VaultStep.DECRYPTING) }
                try {
                    val item = storageObfuscator.obfuscatedRead(itemId, ownerDid)
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
                    _state.update { it.copy(isLoading = false, step = VaultStep.COMPLETED, successMessage = "加密保存成功") }
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

    fun startNewItem() {
        _state.update { it.copy(step = VaultStep.IDLE, isEditing = true, selectedItem = null, editTitle = "", editContent = "") }
    }

    fun cancelEdit() {
        _state.update { it.copy(isEditing = false, editTitle = "", editContent = "") }
    }

    fun onBiometricSuccess() {
        val needsKdfs = needsKdfsPattern()
        if (needsKdfs) {
            viewModelScope.launch {
                val cachedKdfs = zkpCacheManager.getCachedKdfs()
                if (cachedKdfs != null) {
                    pendingOperation?.invoke()
                    pendingOperation = null
                } else {
                    _state.update { it.copy(step = VaultStep.KDFS_DRAW) }
                }
            }
        } else {
            pendingOperation?.invoke()
            pendingOperation = null
        }
    }

    fun onKdfsComplete(kdfsHash: ByteArray) {
        viewModelScope.launch {
            zkpCacheManager.putCachedKdfs(kdfsHash)
            pendingOperation?.invoke()
            pendingOperation = null
        }
    }

    fun onBiometricFailed(error: String) {
        _state.update { it.copy(step = VaultStep.FAILED, error = error) }
        pendingOperation = null
    }

    fun reset() {
        _state.update { it.copy(step = VaultStep.IDLE, selectedItem = null, isEditing = false, error = null, successMessage = null) }
    }

    private fun beginAuthentication(type: VaultOperationType) {
        val isRooted = RootDetector.isDeviceRooted()
        _state.update { it.copy(step = VaultStep.BIOMETRIC_PROMPT) }
    }

    private fun needsKdfsPattern(): Boolean = true
}
