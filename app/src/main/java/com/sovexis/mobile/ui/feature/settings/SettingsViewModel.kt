package com.sovexis.mobile.ui.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val theme: String = "system",
    val language: String = "zh",
    val strongBoxAvailable: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setTheme(theme: String) {
        _uiState.update { it.copy(theme = theme) }
        // [TODO] 保存主题设置�?DataStore
    }

    fun setLanguage(language: String) {
        _uiState.update { it.copy(language = language) }
        // [TODO] 保存语言设置�?DataStore
    }
}
