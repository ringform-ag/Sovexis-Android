package com.sovexis.mobile.ui.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.mobile.core.result.Resource
import com.sovexis.mobile.domain.did.DidDocument
import com.sovexis.mobile.domain.did.DidService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateIdentityUiState(
    val alias: String = "",
    val isCreating: Boolean = false,
    val error: String? = null,
    val createdDid: DidDocument? = null
)

@HiltViewModel
class CreateIdentityViewModel @Inject constructor(
    private val didService: DidService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateIdentityUiState())
    val uiState: StateFlow<CreateIdentityUiState> = _uiState.asStateFlow()

    fun updateAlias(alias: String) {
        _uiState.update { it.copy(alias = alias, error = null) }
    }

    fun createIdentity() {
        val alias = _uiState.value.alias
        if (alias.isBlank()) {
            _uiState.update { it.copy(error = "请输入别�?) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            when (val result = didService.createIdentity(alias)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isCreating = false, createdDid = result.data) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isCreating = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }
}
