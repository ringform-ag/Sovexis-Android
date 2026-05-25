package com.sovexis.mobile.ui.feature.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.mobile.data.local.dao.AccountDao
import com.sovexis.mobile.data.local.entity.AccountEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentityManagementUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class IdentityManagementViewModel @Inject constructor(
    private val accountDao: AccountDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdentityManagementUiState())
    val uiState: StateFlow<IdentityManagementUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            accountDao.getAllAccounts().collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
    }
}
