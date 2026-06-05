package com.sovexis.ui.feature.safebox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.communication.CryptoCommLayer
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.MasterIdentity
import com.sovexis.domain.storage.VaultDao
import com.sovexis.domain.storage.VaultItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val cryptoCommLayer: CryptoCommLayer? = null
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

    /** 上传条目到节点（占位 — 连接 cryptoCommLayer，最终端口暂不实现） */
    fun uploadToNode(itemId: String) {
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { vaultDao.getItem(itemId) }
                    ?: return@launch
                val nodeDid = _uiState.value.activeDid
                    ?: return@launch
                // 连接 node 通信层，发送到节点
                cryptoCommLayer?.send(item.encryptedData.toByteArray(Charsets.UTF_8), nodeDid)
                    ?: _uiState.update { it.copy(message = "通信层未就绪") }
                _uiState.update { it.copy(message = "已发送上传请求（待节点确认）") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "上传失败: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
