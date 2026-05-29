package com.sovexis.ui.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SplashStep { CHECKING, AUTH_REQUIRED, READY }

data class SplashUiState(
    val step: SplashStep = SplashStep.CHECKING,
    val isLoading: Boolean = true,
    val hasIdentity: Boolean? = null,
    val activeDid: String? = null,
    val authFailed: Boolean = false
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val identityManager: IdentityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init { checkExistingIdentity() }

    private fun checkExistingIdentity() {
        viewModelScope.launch {
            val master = withContext(Dispatchers.IO) {
                identityManager.getMasterIdentity()
            }
            if (master != null) {
                // 有身份 → 需要生物认证
                _uiState.update {
                    it.copy(isLoading = false, hasIdentity = true,
                        activeDid = master.did, step = SplashStep.AUTH_REQUIRED)
                }
            } else {
                // 无身份 → 跳到 Welcome 选择页
                _uiState.update {
                    it.copy(isLoading = false, hasIdentity = false,
                        step = SplashStep.READY)
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onBiometricSuccess(biometricSignature: ByteArray) {
        _uiState.update {
            it.copy(step = SplashStep.READY, authFailed = false)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onBiometricFailed(error: String) {
        _uiState.update {
            it.copy(authFailed = true, step = SplashStep.AUTH_REQUIRED)
        }
    }

    fun retryAuth() {
        _uiState.update {
            it.copy(authFailed = false, step = SplashStep.AUTH_REQUIRED)
        }
    }
}
