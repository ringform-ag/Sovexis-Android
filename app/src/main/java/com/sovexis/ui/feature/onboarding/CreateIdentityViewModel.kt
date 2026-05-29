package com.sovexis.ui.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.recovery.RecoveryConfig
import com.sovexis.domain.zkp.RootDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Arrays
import javax.inject.Inject

/**
 * 创建身份流程步骤。
 */
enum class CreateIdentityStep {
    /** 空闲状态 */
    IDLE,

    /** 等待生物认证 */
    BIOMETRIC_PROMPT,

    /** 首次绘制 KDFS 图案 */
    KDFS_DRAW_FIRST,

    /** 确认 KDFS 图案 */
    KDFS_DRAW_CONFIRM,

    /** 配置恢复方法 */
    RECOVERY_SETUP,

    /** 生成身份中 */
    GENERATING,

    /** 创建完成 */
    COMPLETED
}

/**
 * 创建身份状态。
 *
 * @param step 当前步骤
 * @param alias 用户别名
 * @param kdfsHash KDFS 图案哈希
 * @param recoveryConfig 恢复配置
 * @param error 错误信息
 * @param isLoading 是否加载中
 */
data class CreateIdentityState(
    val step: CreateIdentityStep = CreateIdentityStep.IDLE,
    val alias: String = "",
    val kdfsHash: ByteArray? = null,
    val recoveryConfig: RecoveryConfig? = null,
    val mnemonicWords: List<String> = emptyList(),  // 新增字段
    val error: String? = null,
    val isLoading: Boolean = false,
    val isDeviceRooted: Boolean = false,
    val riskLabel: String? = null,
    val biometricSignature: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CreateIdentityState) return false
        return step == other.step &&
            alias == other.alias &&
            kdfsHash.contentEquals(other.kdfsHash) &&
            recoveryConfig == other.recoveryConfig &&
            error == other.error &&
            isLoading == other.isLoading &&
            isDeviceRooted == other.isDeviceRooted &&
            riskLabel == other.riskLabel &&
            biometricSignature.contentEquals(other.biometricSignature)
    }

    override fun hashCode(): Int {
        var result = step.hashCode()
        result = 31 * result + alias.hashCode()
        result = 31 * result + (kdfsHash?.contentHashCode() ?: 0)
        result = 31 * result + (recoveryConfig?.hashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + isDeviceRooted.hashCode()
        result = 31 * result + (riskLabel?.hashCode() ?: 0)
        result = 31 * result + (biometricSignature?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * 创建身份 ViewModel。
 *
 * 管理完整的身份创建流程：
 * 1. 别名输入
 * 2. 生物认证
 * 3. KDFS 图案绘制（首次）
 * 4. KDFS 图案确认
 * 5. 恢复配置选择
 * 6. 生成身份
 */
@HiltViewModel
class CreateIdentityViewModel @Inject constructor(
    private val identityManager: IdentityManager
) : ViewModel() {

    private val _state = MutableStateFlow(CreateIdentityState())
    val state: StateFlow<CreateIdentityState> = _state.asStateFlow()

    private var firstKdfsHash: ByteArray? = null

    /**
     * 步骤 1：用户输入别名后，触发 BiometricPrompt。
     *
     * @param alias 用户输入的别名
     */
    fun onAliasEntered(alias: String) {
        // 检测 Root 状态
        val isRooted = RootDetector.isDeviceRooted()
        _state.value = _state.value.copy(
            alias = alias,
            step = CreateIdentityStep.BIOMETRIC_PROMPT,
            isDeviceRooted = isRooted,
            riskLabel = if (isRooted) "RISK_ROOTED" else null
        )
    }

    /**
     * 步骤 2：BiometricPrompt 成功后回调。
     *
     * @param biometricSignature 生物认证签名
     */
    fun onBiometricSuccess(biometricSignature: ByteArray) {
        _state.value = _state.value.copy(
            biometricSignature = biometricSignature,
            step = CreateIdentityStep.KDFS_DRAW_FIRST
        )
        // biometricSignature 暂存，待 KDFS 完成后一起使用
        // 实际使用时需要安全存储
    }

    /**
     * 步骤 2b：BiometricPrompt 失败。
     *
     * @param error 错误信息
     */
    fun onBiometricFailed(error: String) {
        _state.value = _state.value.copy(
            step = CreateIdentityStep.IDLE,
            error = "生物认证失败: $error"
        )
    }

    /**
     * 步骤 3：用户首次绘制 KDFS 图案完成。
     *
     * @param kdfsHash KDFS 图案哈希
     */
    fun onKdfsFirstComplete(kdfsHash: ByteArray) {
        firstKdfsHash = kdfsHash.copyOf()
        _state.value = _state.value.copy(
            kdfsHash = kdfsHash,
            step = CreateIdentityStep.KDFS_DRAW_CONFIRM
        )
    }

    /**
     * 步骤 4：用户确认 KDFS 图案。
     *
     * @param kdfsHash 确认的 KDFS 图案哈希
     */
    fun onKdfsConfirmComplete(kdfsHash: ByteArray) {
        if (!firstKdfsHash.contentEquals(kdfsHash)) {
            // 图案不匹配，清除首次哈希，要求重新绘制
            firstKdfsHash?.let { Arrays.fill(it, 0) }
            firstKdfsHash = null
            _state.value = _state.value.copy(
                step = CreateIdentityStep.KDFS_DRAW_FIRST,
                error = "两次绘制的图案不一致，请重新绘制"
            )
            return
        }

        // 图案匹配，进入恢复配置选择
        _state.value = _state.value.copy(
            kdfsHash = kdfsHash,
            step = CreateIdentityStep.RECOVERY_SETUP,
            error = null
        )

        // 清除临时存储的首次哈希
        firstKdfsHash?.let { Arrays.fill(it, 0) }
        firstKdfsHash = null
    }

    /**
     * 步骤 5：用户选择恢复方法后，生成主账号。
     *
     * @param config 恢复配置
     */
    fun onRecoveryConfigSelected(config: RecoveryConfig) {
        _state.value = _state.value.copy(
            recoveryConfig = config,
            isLoading = true,
            step = CreateIdentityStep.GENERATING
        )

        viewModelScope.launch {
            try {
                val kdfsHash = _state.value.kdfsHash
                    ?: throw IllegalStateException("KDFS 哈希缺失")

                // 调用 IdentityManager 创建主账号
                val result = identityManager.createMasterIdentity(
                    alias = _state.value.alias,
                    kdfsHash = kdfsHash,
                    recoveryConfig = config
                )
                val masterIdentity = result.getOrThrow()

                               // 生成助记词
                val mnemonicRecovery = com.sovexis.domain.recovery.MnemonicRecovery(
                    identityManagerProvider = object : javax.inject.Provider<com.sovexis.domain.identity.IdentityManager> {
                        override fun get(): com.sovexis.domain.identity.IdentityManager = identityManager
                    }
                )
                val mnemonicWords = mnemonicRecovery.generateMnemonic()
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = CreateIdentityStep.COMPLETED,
                    mnemonicWords = mnemonicWords
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = CreateIdentityStep.IDLE,
                    error = "身份创建失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 重置流程。
     */
    fun reset() {
        firstKdfsHash?.let { Arrays.fill(it, 0) }
        firstKdfsHash = null
        _state.value.kdfsHash?.let { Arrays.fill(it, 0) }
        _state.value = CreateIdentityState()
    }

    /**
     * 清除错误信息。
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        // 安全清理敏感数据
        firstKdfsHash?.let { Arrays.fill(it, 0) }
        _state.value.kdfsHash?.let { Arrays.fill(it, 0) }
    }
}
