package com.sovexis.ui.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val storageLevel: Int = 1,
    val communicationLevel: Int = 1,
    val tssEnabled: Boolean = false,
    val kdfsCacheMinutes: Int = 5,
    val covertEnabled: Boolean = true,
    val injectionRatio: Float = 0.3f,
    val fallbackStrategy: Int = 0,
    val strongBoxAvailable: Boolean = false,
    val nodeHost: String = "192.168.1.100",
    val nodePort: Int = 8100
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    companion object {
        private const val PREFS_FILE = "sovexis_settings"
        private const val KEY_STORAGE_LEVEL = "sovexis_settings_storage_level"
        private const val KEY_COMM_LEVEL = "sovexis_settings_communication_level"
        private const val KEY_TSS_ENABLED = "sovexis_settings_tss_enabled"
        private const val KEY_KDFS_CACHE = "sovexis_settings_kdfs_cache_minutes"
        private const val KEY_COVERT_ENABLED = "sovexis_settings_covert_enabled"
        private const val KEY_INJECTION_RATIO = "sovexis_settings_injection_ratio"
        private const val KEY_FALLBACK_STRATEGY = "sovexis_settings_fallback_strategy"
        private const val KEY_NODE_HOST = "sovexis_settings_node_host"
        private const val KEY_NODE_PORT = "sovexis_settings_node_port"
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, PREFS_FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    init { load() }

    private fun load() {
        try {
            _uiState.update {
                it.copy(
                    storageLevel = prefs.getInt(KEY_STORAGE_LEVEL, 1),
                    communicationLevel = prefs.getInt(KEY_COMM_LEVEL, 1),
                    tssEnabled = prefs.getBoolean(KEY_TSS_ENABLED, false),
                    kdfsCacheMinutes = prefs.getInt(KEY_KDFS_CACHE, 5),
                    covertEnabled = prefs.getBoolean(KEY_COVERT_ENABLED, true),
                    injectionRatio = prefs.getFloat(KEY_INJECTION_RATIO, 0.3f),
                    fallbackStrategy = prefs.getInt(KEY_FALLBACK_STRATEGY, 0),
                    nodeHost = prefs.getString(KEY_NODE_HOST, "192.168.1.100") ?: "192.168.1.100",
                    nodePort = prefs.getInt(KEY_NODE_PORT, 8100),
                    strongBoxAvailable = try {
                        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)
                    } catch (_: Exception) { false }
                )
            }
        } catch (_: Exception) { }
    }

    fun setStorageLevel(level: Int) {
        prefs.edit().putInt(KEY_STORAGE_LEVEL, level).apply()
        _uiState.update { it.copy(storageLevel = level) }
    }

    fun setCommunicationLevel(level: Int) {
        prefs.edit().putInt(KEY_COMM_LEVEL, level).apply()
        _uiState.update { it.copy(communicationLevel = level) }
    }

    fun setTssEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TSS_ENABLED, enabled).apply()
        _uiState.update { it.copy(tssEnabled = enabled) }
    }

    fun setKdfsCacheMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_KDFS_CACHE, minutes).apply()
        _uiState.update { it.copy(kdfsCacheMinutes = minutes) }
    }

    fun setCovertEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COVERT_ENABLED, enabled).apply()
        _uiState.update { it.copy(covertEnabled = enabled) }
    }

    fun setInjectionRatio(ratio: Float) {
        prefs.edit().putFloat(KEY_INJECTION_RATIO, ratio).apply()
        _uiState.update { it.copy(injectionRatio = ratio) }
    }

    fun setFallbackStrategy(strategy: Int) {
        prefs.edit().putInt(KEY_FALLBACK_STRATEGY, strategy).apply()
        _uiState.update { it.copy(fallbackStrategy = strategy) }
    }

    fun setNodeHost(host: String) {
        prefs.edit().putString(KEY_NODE_HOST, host).apply()
        _uiState.update { it.copy(nodeHost = host) }
    }

    fun setNodePort(port: Int) {
        prefs.edit().putInt(KEY_NODE_PORT, port).apply()
        _uiState.update { it.copy(nodePort = port) }
    }
}
