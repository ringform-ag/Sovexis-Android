package com.sovexis.mobile.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.mobile.core.common.UiEvent
import com.sovexis.mobile.data.local.dao.AccountDao
import com.sovexis.mobile.data.local.entity.AccountEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val activeAccount: AccountEntity? = null,
    val allAccounts: List<AccountEntity> = emptyList(),
    val currentRoute: String = "home"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountDao: AccountDao
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
            accountDao.getAllAccounts().collect { accounts ->
                _uiState.update {
                    it.copy(
                        allAccounts = accounts,
                        activeAccount = accounts.find { acc -> acc.isActive }
                    )
                }
            }
        }
    }

    fun selectAccount(did: String) {
        viewModelScope.launch {
            accountDao.deactivateAll()
            accountDao.setActive(did)
            accountDao.updateLastUsed(did)
        }
    }

    fun navigate(route: String) {
        viewModelScope.launch {
            _events.send(UiEvent.Navigate(route))
        }
    }
}
