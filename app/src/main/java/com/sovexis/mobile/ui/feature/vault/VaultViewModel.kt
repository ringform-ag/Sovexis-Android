package com.sovexis.mobile.ui.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.storage.PlainVaultItem
import com.sovexis.domain.storage.StorageObfuscator
import com.sovexis.mobile.domain.zkp.ZkpCacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 保险箱操作步骤。
 */
enum class VaultStep {
    /** 空闲状态 */
    IDLE,

    /** 生物认证 */
    BIOMETRIC_PROMPT,

    /** KDFS 图案绘制 */
    KDFS_DRAW,

    /** 解密中 */
    DECRYPTING,

    /** 加密保存中 */
    ENCRYPTING,

    /** 删除中 */
    DELETING,

    /** 完成 */
    COMPLETED,

    /** 失败 */
    FAILED
}

/**
 * 保险箱操作类型。
 */
enum class VaultOperationType {
    /** 读取 */
    READ,

    /** 写入 */
    WRITE,

    /** 删除 */
    DELETE
}

/**
 * 保险箱状态。
 *
 * @param step 当前步骤
 * @param items 保险箱条目列表
 * @param selectedItem 选中的条目
 * @param isEditing 是否处于编辑模式
 * @param editTitle 编辑标题
 * @param editContent 编辑内容
 * @param isLoading 是否加载中
 * @param error 错误信息
 * @param operationType 操作类型
 * @param successMessage 成功消息
 */
data class VaultState(
    val step: VaultStep = VaultStep.IDLE,
    val items: List<PlainVaultItem> = emptyList(),
    val selectedItem: PlainVaultItem? = null,
    val isEditing: Boolean = false,
    val editTitle: String = "",
    val editContent: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val operationType: VaultOperationType? = null,
    val successMessage: String? = null
)

/**
 * 保险箱 ViewModel。
 *
 * 管理保险箱的加密读取、写入、删除流程。
 * 根据存储安全级别决定是否需要 KDFS 认证：
 * - L0/L1: 仅需 BiometricPrompt
 * - L2 (SOVEREIGN): BiometricPrompt + KDFS
 *
 * [AI-GENERATED]
 * 实现状态: ✅ 已完成（2026-05-22）
 * 参考文档: Sovexis · 保险箱操作流程应用层串联指令
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val storageObfuscator: StorageObfuscator,
    private val identityManager: IdentityManager,
    private val zkpCacheManager: ZkpCacheManager
) : ViewModel() {

    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private var pendingOperation: (() -> Unit)? = null

    /**
     * 加载保险箱列表（无需认证，仅展示条目标题）。
     *
     * @param ownerDid 所有者 DID
     */
    fun loadItems(ownerDid: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val items = storageObfuscator.listItems(ownerDid)
                _state.value = _state.value.copy(
                    items = items,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 发起读取操作。
     *
     * @param itemId 条目 ID
     * @param ownerDid 所有者 DID
     */
    fun initiateRead(itemId: String, ownerDid: String) {
        pendingOperation = {
            viewModelScope.launch {
                _state.value = _state.value.copy(
                    isLoading = true,
                    step = VaultStep.DECRYPTING
                )
                try {
                    val plainItem = storageObfuscator.obfuscatedRead(itemId, ownerDid)
                    _state.value = _state.value.copy(
                        selectedItem = plainItem,
                        isLoading = false,
                        step = VaultStep.COMPLETED,
                        successMessage = "解密成功"
                    )
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = VaultStep.FAILED,
                        error = "解密失败: ${e.message}"
                    )
                }
            }
        }
        beginAuthentication(VaultOperationType.READ)
    }

    /**
     * 发起写入操作。
     *
     * @param ownerDid 所有者 DID
     * @param title 标题
     * @param content 内容
     */
    fun initiateWrite(ownerDid: String, title: String, content: String) {
        val itemId = UUID.randomUUID().toString()
        pendingOperation = {
            viewModelScope.launch {
                _state.value = _state.value.copy(
                    isLoading = true,
                    step = VaultStep.ENCRYPTING
                )
                try {
                    storageObfuscator.obfuscatedWrite(itemId, ownerDid, title, content)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = VaultStep.COMPLETED,
                        successMessage = "加密保存成功"
                    )
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = VaultStep.FAILED,
                        error = "加密保存失败: ${e.message}"
                    )
                }
            }
        }
        beginAuthentication(VaultOperationType.WRITE)
    }

    /**
     * 发起删除操作。
     *
     * @param itemId 条目 ID
     * @param ownerDid 所有者 DID
     */
    fun initiateDelete(itemId: String, ownerDid: String) {
        pendingOperation = {
            viewModelScope.launch {
                _state.value = _state.value.copy(
                    isLoading = true,
                    step = VaultStep.DELETING
                )
                try {
                    storageObfuscator.obfuscatedDelete(itemId, ownerDid)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = VaultStep.COMPLETED,
                        successMessage = "删除成功"
                    )
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = VaultStep.FAILED,
                        error = "删除失败: ${e.message}"
                    )
                }
            }
        }
        beginAuthentication(VaultOperationType.DELETE)
    }

    /**
     * 进入编辑模式。
     */
    fun enterEditMode() {
        val item = _state.value.selectedItem ?: return
        _state.value = _state.value.copy(
            isEditing = true,
            editTitle = item.title,
            editContent = item.content
        )
    }

    /**
     * 取消编辑。
     */
    fun cancelEdit() {
        _state.value = _state.value.copy(
            isEditing = false,
            editTitle = "",
            editContent = ""
        )
    }

    /**
     * 开始认证流程：BiometricPrompt → KDFS（如需）。
     */
    private fun beginAuthentication(operationType: VaultOperationType) {
        _state.value = _state.value.copy(
            operationType = operationType,
            step = VaultStep.BIOMETRIC_PROMPT
        )
    }

    /**
     * BiometricPrompt 成功回调。
     */
    fun onBiometricSuccess() {
        val needsKdfs = needsKdfsPattern()
        if (needsKdfs) {
            // L2 用户需要 KDFS
            viewModelScope.launch {
                val cachedKdfs = zkpCacheManager.getCachedKdfs()
                if (cachedKdfs != null) {
                    // 缓存期内，直接执行操作
                    pendingOperation?.invoke()
                    pendingOperation = null
                } else {
                    _state.value = _state.value.copy(step = VaultStep.KDFS_DRAW)
                }
            }
        } else {
            // 不需要 KDFS，直接执行操作
            pendingOperation?.invoke()
            pendingOperation = null
        }
    }

    /**
     * KDFS 图案完成回调。
     *
     * @param kdfsHash KDFS 图案哈希
     */
    fun onKdfsComplete(kdfsHash: ByteArray) {
        viewModelScope.launch {
            zkpCacheManager.putCachedKdfs(kdfsHash)
            pendingOperation?.invoke()
            pendingOperation = null
        }
    }

    /**
     * BiometricPrompt 失败回调。
     */
    fun onBiometricFailed(error: String) {
        pendingOperation = null
        _state.value = _state.value.copy(
            step = VaultStep.FAILED,
            error = "生物认证失败: $error"
        )
    }

    /**
     * 判断是否需要 KDFS 图案（仅 L2 SOVEREIGN 级别）。
     */
    private fun needsKdfsPattern(): Boolean {
        return storageObfuscator.getConfig()?.level == com.sovexis.domain.storage.StorageLevel.SOVEREIGN
    }

    /**
     * 重置到空闲状态。
     */
    fun reset() {
        pendingOperation = null
        _state.value = _state.value.copy(
            step = VaultStep.IDLE,
            selectedItem = null,
            error = null,
            successMessage = null,
            isEditing = false,
            operationType = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        pendingOperation = null
    }
}
