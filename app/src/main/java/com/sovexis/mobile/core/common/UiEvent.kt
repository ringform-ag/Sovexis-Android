package com.sovexis.mobile.core.common

/**
 * 通用一次�?UI 事件
 */
sealed class UiEvent {
    data class ShowSnackbar(val message: String, val action: String? = null) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
    object PopBackStack : UiEvent()
    data class ShowBiometricPrompt(val title: String, val subtitle: String, val callback: (Boolean) -> Unit) : UiEvent()
    data class ShowDialog(val title: String, val message: String, val onConfirm: (() -> Unit)? = null) : UiEvent()
}
