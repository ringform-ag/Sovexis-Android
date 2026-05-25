package com.sovexis.mobile.ui.feature.safebox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.mobile.data.local.dao.SafeBoxDao
import com.sovexis.mobile.data.local.dao.AccountDao
import com.sovexis.mobile.data.local.entity.AccountEntity
import com.sovexis.mobile.data.local.entity.SafeBoxItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafeBoxUiState(
    val items: List<SafeBoxItemEntity> = emptyList(),
    val activeAccount: AccountEntity? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class SafeBoxViewModel @Inject constructor(
    private val safeBoxDao: SafeBoxDao,
    private val accountDao: AccountDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafeBoxUiState())
    val uiState: StateFlow<SafeBoxUiState> = _uiState.asStateFlow()

    init {
        loadActiveAccount()
    }

    private fun loadActiveAccount() {
        viewModelScope.launch {
            accountDao.getActiveAccount().collect { account ->
                _uiState.update { it.copy(activeAccount = account) }
                account?.let { loadItems(it.did) }
            }
        }
    }

    private fun loadItems(ownerDid: String) {
        viewModelScope.launch {
            safeBoxDao.getItemsByOwner(ownerDid).collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }
}
