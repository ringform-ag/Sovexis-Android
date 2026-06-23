package com.sovexis.ui.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SplashStep { CHECKING, AUTH_REQUIRED, LOADING, READY }

data class SplashUiState(
    val step: SplashStep = SplashStep.CHECKING,
    val isLoading: Boolean = true,
    val hasIdentity: Boolean? = null,
    val activeDid: String? = null,
    val authFailed: Boolean = false,
    val retryAttempts: Int = 0
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val identityManager: IdentityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init { checkExistingIdentity() }

    companion object {
        /** 欢迎界面最短展示时间（毫秒），确保用户感知到品牌过渡 */
        private const val MIN_SPLASH_DURATION_MS = 1500L
    }

    private fun checkExistingIdentity() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val master = withContext(Dispatchers.IO) {
                identityManager.getMasterIdentity()
            }
            // 最短 1.5 秒缓冲，给组件加载留时间
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < MIN_SPLASH_DURATION_MS) {
                delay(MIN_SPLASH_DURATION_MS - elapsed)
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
            it.copy(authFailed = true, retryAttempts = it.retryAttempts + 1,
                step = if (it.retryAttempts + 1 >= 3) SplashStep.LOADING else SplashStep.AUTH_REQUIRED)
        }
    }

    /**
     * 重试生物认证（最多 3 次，超过进入冷静期）。
     * 重试只是标记 authFailed=false + AUTH_REQUIRED，让 BiometricPrompt 重新弹出。
     */
    fun retryAuth() {
        _uiState.update {
            it.copy(authFailed = false, step = SplashStep.AUTH_REQUIRED)
        }
    }

    /** 是否已进入冷静期（3 次失败后） */
    fun isInCooldown(): Boolean = _uiState.value.retryAttempts >= 3
}
