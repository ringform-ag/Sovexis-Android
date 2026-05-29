package com.sovexis.ui.feature.credentials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.MasterIdentity
import com.sovexis.domain.vc.CredentialService
import com.sovexis.domain.vc.VerifiableCredential
import com.sovexis.core.result.getOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CredentialsUiState(
    val credentials: List<VerifiableCredential> = emptyList(),
    val activeAccount: MasterIdentity? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class CredentialsViewModel @Inject constructor(
    private val credentialService: CredentialService,
    private val identityManager: IdentityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CredentialsUiState())
    val uiState: StateFlow<CredentialsUiState> = _uiState.asStateFlow()

    init {
        loadActiveAccount()
    }

    private fun loadActiveAccount() {
        viewModelScope.launch {
            val account = identityManager.getMasterIdentity()
            _uiState.update { it.copy(activeAccount = account) }
            account?.let { loadCredentials(it.did) }
        }
    }

    private fun loadCredentials(ownerDid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = withContext(Dispatchers.IO) {
                credentialService.getCredentialsByOwner(ownerDid)
            }
            val credentials: List<VerifiableCredential> = result.getOrNull() ?: emptyList()
            _uiState.update { it.copy(credentials = credentials, isLoading = false) }
        }
    }
}
