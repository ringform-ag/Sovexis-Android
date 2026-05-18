package com.sovexis.mobile.ui.feature.credentials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.mobile.data.local.dao.CredentialDao
import com.sovexis.mobile.data.local.dao.AccountDao
import com.sovexis.mobile.data.local.entity.AccountEntity
import com.sovexis.mobile.data.local.entity.CredentialEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CredentialsUiState(
    val credentials: List<CredentialEntity> = emptyList(),
    val activeAccount: AccountEntity? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class CredentialsViewModel @Inject constructor(
    private val credentialDao: CredentialDao,
    private val accountDao: AccountDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CredentialsUiState())
    val uiState: StateFlow<CredentialsUiState> = _uiState.asStateFlow()

    init {
        loadActiveAccount()
    }

    private fun loadActiveAccount() {
        viewModelScope.launch {
            accountDao.getActiveAccount().collect { account ->
                _uiState.update { it.copy(activeAccount = account) }
                account?.let { loadCredentials(it.did) }
            }
        }
    }

    private fun loadCredentials(ownerDid: String) {
        viewModelScope.launch {
            credentialDao.getCredentialsByOwner(ownerDid).collect { credentials ->
                _uiState.update { it.copy(credentials = credentials) }
            }
        }
    }
}
