package com.sovexis.ui.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sovexis.ui.theme.themePresetIndex
import com.sovexis.identity.IdentityMigration
import com.sovexis.identity.PersonhoodManager
import com.sovexis.identity.MigrationGuideStep
import com.sovexis.domain.crypto.DeviceFingerprint

data class SettingsUiState(
    val storageLevel: Int = 1,
    val communicationLevel: Int = 1,
    val tssEnabled: Boolean = false,
    val kdfsCacheMinutes: Int = 5,
    val covertEnabled: Boolean = true,
    val injectionRatio: Float = 0.3f,
    val negotiationSecurityLevel: Int = 1,
    val strongBoxAvailable: Boolean = false,
    val themePreset: Int = 0,
    val autoSwitchTheme: Boolean = false,
    // ── 身份导出 ──
    val showExportDialog: Boolean = false,
    val exportStep: MigrationGuideStep? = null,
    val exportData: String? = null,
    val exportChecksum: String? = null,
    val exportLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityMigration: IdentityMigration,
    private val personhoodManager: PersonhoodManager,
    private val deviceFingerprint: DeviceFingerprint
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
        private const val KEY_NEGOTIATION_LEVEL = "sovexis_settings_negotiation_level"
    }

    private val themePrefs = context.getSharedPreferences("sovexis_theme", Context.MODE_PRIVATE)
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
            val tp = themePrefs.getInt("theme_preset", 0)
            themePresetIndex = tp
            _uiState.update {
                it.copy(
                    storageLevel = prefs.getInt(KEY_STORAGE_LEVEL, 1),
                    communicationLevel = prefs.getInt(KEY_COMM_LEVEL, 1),
                    tssEnabled = prefs.getBoolean(KEY_TSS_ENABLED, false),
                    kdfsCacheMinutes = prefs.getInt(KEY_KDFS_CACHE, 5),
                    covertEnabled = prefs.getBoolean(KEY_COVERT_ENABLED, true),
                    injectionRatio = prefs.getFloat(KEY_INJECTION_RATIO, 0.3f),
                    negotiationSecurityLevel = prefs.getInt(KEY_NEGOTIATION_LEVEL, 1),
                    themePreset = themePrefs.getInt("theme_preset", 0),
                    autoSwitchTheme = themePrefs.getBoolean("auto_switch", false),
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

    fun setNegotiationSecurityLevel(level: Int) {
        prefs.edit().putInt(KEY_NEGOTIATION_LEVEL, level).apply()
        _uiState.update { it.copy(negotiationSecurityLevel = level) }
    }

    fun setThemePreset(index: Int) {
        themePrefs.edit().putInt("theme_preset", index).apply()
        themePresetIndex = index
        _uiState.update { it.copy(themePreset = index) }
    }

    @Deprecated("自动切换主题缺 UI，后续补")
    fun setAutoSwitchTheme(enabled: Boolean) {
        themePrefs.edit().putBoolean("auto_switch", enabled).apply()
        _uiState.update { it.copy(autoSwitchTheme = enabled) }
    }

    // ── 身份导出 ──

    fun startExport() {
        _uiState.update {
            it.copy(showExportDialog = true, exportStep = MigrationGuideStep.SafetyReminder)
        }
    }

    fun advanceExport() {
        val step = _uiState.value.exportStep ?: return
        val next = when (step) {
            is MigrationGuideStep.SafetyReminder -> MigrationGuideStep.ChannelSelect
            is MigrationGuideStep.ChannelSelect -> MigrationGuideStep.ExportReady("")
            else -> step
        }
        _uiState.update { it.copy(exportStep = next) }
    }

    fun confirmExport() {
        viewModelScope.launch {
            _uiState.update { it.copy(exportLoading = true, exportStep = MigrationGuideStep.Transferring) }

            val did = "did:sovexis:master:export" // TODO: 从 IdentityManager 获取活跃主账号 DID

            // 获取设备硬指纹
            val oldFp = deviceFingerprint.getDeviceFingerprint()
            val newFp = "" // 迁移前不知道新设备指纹，留空

            // 一键导出：打包 + 签发 TransferAuthToken + AES-GCM 加密
            val result = identityMigration.exportEncoded(
                did = did,
                oldFp = oldFp,
                newFp = newFp,
                teeSig = "BIO_AUTH_TEE_SIGNATURE".toByteArray(),
                signer = { data -> deviceFingerprint.teeSign("default", data) },
                sessionKey = deriveExportSessionKey(did)
            )
            result.onSuccess { encoded ->
                val checksum = "${did.takeLast(8)}:${encoded.length}"
                _uiState.update {
                    it.copy(
                        exportLoading = false,
                        exportStep = MigrationGuideStep.ExportReady(checksum),
                        exportData = encoded,
                        exportChecksum = checksum
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(exportLoading = false,
                        exportStep = MigrationGuideStep.Error("迁移失败: ${e.message}"))
                }
            }
        }
    }

    /** 迁移完成后冻结旧设备 */
    fun finalizeExport() {
        val did = "did:sovexis:master:export" // TODO: from IdentityManager
        personhoodManager.freezePersonaAfterMigration(did)
        personhoodManager.setPersonaMigratedAt(did)
        _uiState.update {
            it.copy(showExportDialog = false, exportStep = null,
                exportData = null, exportChecksum = null, exportLoading = false)
        }
    }

    fun dismissExport() {
        _uiState.update {
            it.copy(showExportDialog = false, exportStep = null,
                exportData = null, exportChecksum = null, exportLoading = false)
        }
    }

    private fun deriveExportSessionKey(did: String): ByteArray {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(did.toByteArray())
        return hash.copyOf(32)
    }
}
