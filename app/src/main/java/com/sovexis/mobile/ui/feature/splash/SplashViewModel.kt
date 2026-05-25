package com.sovexis.mobile.ui.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.mobile.domain.did.DidService
import com.sovexis.mobile.domain.did.DidInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashUiState(
    val isLoading: Boolean = true,
    val hasIdentity: Boolean? = null,
    val activeIdentity: DidInfo? = null
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val didService: DidService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkExistingIdentity()
    }

    private fun checkExistingIdentity() {
        viewModelScope.launch {
            // [TODO] 检查本地是否已有身�?            delay(1500) // 模拟启动延迟
            _uiState.update { it.copy(isLoading = false, hasIdentity = false) }
        }
    }
}
