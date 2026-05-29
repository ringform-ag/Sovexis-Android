package com.sovexis.ui.feature.safebox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isLoading: Boolean = false
)

@HiltViewModel
class SafeBoxViewModel @Inject constructor(
    private val vaultDao: VaultDao,
    private val identityManager: IdentityManager
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
}
