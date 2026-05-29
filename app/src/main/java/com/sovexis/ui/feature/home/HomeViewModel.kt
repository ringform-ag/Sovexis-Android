package com.sovexis.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.core.common.UiEvent
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.SovexisAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val activeAccount: SovexisAccount? = null,
    val allAccounts: List<SovexisAccount> = emptyList(),
    val currentRoute: String = "home"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val identityManager: IdentityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val result = identityManager.getAllIdentities().getOrNull()
            if (result != null) {
                _uiState.update {
                    it.copy(
                        allAccounts = result,
                        activeAccount = result.find { acc -> acc.isActive }
                    )
                }
            }
        }
    }

    fun selectAccount(did: String) {
        viewModelScope.launch {
            identityManager.setActiveIdentity(did)
            loadAccounts()  // 重新加载以更新 isActive 状态
        }
    }

    fun navigate(route: String) {
        viewModelScope.launch {
            _events.send(UiEvent.Navigate(route))
        }
    }
}
