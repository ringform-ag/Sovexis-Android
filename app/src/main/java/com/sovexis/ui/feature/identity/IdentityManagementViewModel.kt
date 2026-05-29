package com.sovexis.ui.feature.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.SovexisAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentityManagementUiState(
    val accounts: List<SovexisAccount> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class IdentityManagementViewModel @Inject constructor(
    private val identityManager: IdentityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdentityManagementUiState())
    val uiState: StateFlow<IdentityManagementUiState> = _uiState.asStateFlow()

    init { loadAccounts() }

    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = identityManager.getAllIdentities().getOrNull()
            _uiState.update {
                it.copy(accounts = result ?: emptyList(), isLoading = false)
            }
        }
    }

    fun setActive(did: String) {
        viewModelScope.launch {
            identityManager.setActiveIdentity(did)
            _uiState.update { it.copy(message = "已切换活跃身份") }
            loadAccounts()
        }
    }

    fun setFrozen(did: String, frozen: Boolean) {
        viewModelScope.launch {
            identityManager.setFrozen(did, frozen)
            _uiState.update { it.copy(message = if (frozen) "已熔断" else "已恢复") }
            loadAccounts()
        }
    }

    fun delete(did: String) {
        viewModelScope.launch {
            identityManager.deleteIdentity(did)
            _uiState.update { it.copy(message = "已删除") }
            loadAccounts()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
