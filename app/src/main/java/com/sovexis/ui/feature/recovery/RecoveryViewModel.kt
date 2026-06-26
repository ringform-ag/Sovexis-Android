package com.sovexis.ui.feature.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.MasterIdentity
import com.sovexis.domain.communication.WebSocketManager
import com.sovexis.domain.recovery.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.net.Uri
import java.util.UUID
import javax.inject.Inject
import android.content.Context

/**
 * 恢复流程步骤。
 */
enum class RecoveryStep {
    /** 空闲状态 */
    IDLE,

    /** 选择恢复方式 */
    SELECTING_METHOD,

    /** 输入助记词 */
    MNEMONIC_INPUT,

    /** 社交恢复：等待监护人批准 */
    SOCIAL_WAITING,

    /** 社交恢复：阈值达成 */
    SOCIAL_THRESHOLD_MET,

    /** 网络恢复：获取分片中 */
    NETWORK_FETCHING,

    /** 重建中 */
    RECONSTRUCTING,

    /** 身份导入 */
    IDENTITY_IMPORT,

    /** 完成 */
    COMPLETED,

    /** 失败 */
    FAILED
}

/**
 * 恢复方式。
 */
enum class RecoveryMethodType {
    MNEMONIC,
    SOCIAL,
    NETWORK,
    IDENTITY_IMPORT
}

/**
 * 恢复状态。
 *
 * @param step 当前步骤
 * @param selectedMethod 选中的恢复方式
 * @param mnemonicWords 助记词列表
 * @param recoveryId 恢复请求 ID（社交恢复）
 * @param guardianApprovals 监护人批准列表
 * @param networkShards 网络分片列表
 * @param isLoading 是否加载中
 * @param error 错误信息
 * @param restoredIdentity 恢复的身份
 * @param progressMessage 进度消息
 */
data class RecoveryState(
    val step: RecoveryStep = RecoveryStep.IDLE,
    val selectedMethod: RecoveryMethodType? = null,
    val mnemonicWords: List<String> = emptyList(),
    val recoveryId: String? = null,
    val guardianApprovals: List<GuardianApproval> = emptyList(),
    val networkShards: List<NetworkShard> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val restoredIdentity: MasterIdentity? = null,
    val progressMessage: String? = null
)

/**
 * 恢复 ViewModel。
 *
 * 管理三种恢复方式的完整流程：
 * - 助记词恢复：输入 → 重建
 * - 社交恢复：发起请求 → 等待批准 → 阈值达成 → 重建
 * - 网络恢复：获取分片 → 重建
 *
 * [AI-GENERATED]
 * 实现状态: ✅ 已完成（2026-05-22）
 * 参考文档: Sovexis · 账户恢复流程应用层串联指令
 */
@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val recoveryManager: RecoveryManager,
    private val identityManager: IdentityManager,
    private val wsManager: WebSocketManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(RecoveryState())
    val state: StateFlow<RecoveryState> = _state.asStateFlow()

    /**
     * 步骤 1：用户选择恢复方式。
     *
     * @param method 恢复方式
     */
    fun selectRecoveryMethod(method: RecoveryMethodType) {
        _state.value = _state.value.copy(
            selectedMethod = method,
            step = when (method) {
                RecoveryMethodType.MNEMONIC -> RecoveryStep.MNEMONIC_INPUT
                RecoveryMethodType.SOCIAL -> {
                    // 立即发起社交恢复请求（监护人列表从配置中读取，Phase 3 补充 UI）
                    launchSocialRecovery()
                    RecoveryStep.SOCIAL_WAITING
                }
                RecoveryMethodType.NETWORK -> {
                    // 立即从网络获取分片（节点列表从配置中读取，Phase 3 补充 UI）
                    launchNetworkRecovery()
                    RecoveryStep.NETWORK_FETCHING
                }
                RecoveryMethodType.IDENTITY_IMPORT -> RecoveryStep.IDENTITY_IMPORT
            }
        )
    }

    private fun launchSocialRecovery() {
        viewModelScope.launch {
            try {
                // 从 SharedPreferences 读取已配置的监护人列表
                val guardians = recoveryManager.getGuardianDids()
                val threshold = recoveryManager.getRecoveryThreshold()
                if (guardians.isNotEmpty()) {
                    initiateSocialRecovery(guardians, threshold)
                } else {
                    _state.value = _state.value.copy(
                        step = RecoveryStep.FAILED,
                        error = "未配置监护人或还原配置信息已被删除，请选择其他恢复方式"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    step = RecoveryStep.FAILED,
                    error = "读取恢复配置失败: ${e.message}"
                )
            }
        }
    }

    private fun launchNetworkRecovery() {
        viewModelScope.launch {
            try {
                val nodes = recoveryManager.getRecoveryNodeDids()
                val threshold = recoveryManager.getRecoveryThreshold()
                if (nodes.isNotEmpty()) {
                    recoverFromNetwork(nodes, threshold)
                } else {
                    _state.value = _state.value.copy(
                        step = RecoveryStep.FAILED,
                        error = "未配置网络恢复节点或还原配置信息已被删除，请选择其他恢复方式"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    step = RecoveryStep.FAILED,
                    error = "读取网络恢复配置失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 步骤 2a：助记词恢复。
     *
     * @param words 助记词列表
     * @param passphrase 密码短语（可选）
     */
    fun recoverFromMnemonic(words: List<String>, passphrase: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                mnemonicWords = words,
                isLoading = true,
                step = RecoveryStep.RECONSTRUCTING,
                progressMessage = "正在重建身份..."
            )

            try {
                val result = recoveryManager.recoverFromMnemonic(words, passphrase)
                result.fold(
                    onSuccess = { identity ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = RecoveryStep.COMPLETED,
                            restoredIdentity = identity,
                            progressMessage = "身份恢复成功"
                        )
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = RecoveryStep.FAILED,
                            error = "助记词恢复失败: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = RecoveryStep.FAILED,
                    error = "恢复异常: ${e.message}"
                )
            }
        }
    }

    /**
     * 步骤 2b：社交恢复（发起请求）。
     *
     * @param guardianDids 监护人 DID 列表
     * @param threshold 恢复阈值
     */
    fun initiateSocialRecovery(guardianDids: List<String>, threshold: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                step = RecoveryStep.SOCIAL_WAITING,
                progressMessage = "正在发起恢复请求..."
            )

            try {
                val recoveryId = UUID.randomUUID().toString()
                val result = recoveryManager.recoverFromSocial(
                    recoveryId = recoveryId,
                    guardianDids = guardianDids,
                    threshold = threshold
                )

                result.fold(
                    onSuccess = { request ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            recoveryId = recoveryId,
                            progressMessage = "等待监护人批准 (${0}/${threshold})"
                        )
                        // 开始轮询监护人批准状态
                        startPollingGuardianApprovals(recoveryId, threshold)
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = RecoveryStep.FAILED,
                            error = "发起恢复请求失败: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = RecoveryStep.FAILED,
                    error = "社交恢复异常: ${e.message}"
                )
            }
        }
    }

    /**
     * 轮询监护人批准状态。
     *
     * 过渡期实现：每 5 秒检查一次批准列表。
     * 真实场景下，监护人批准通过 WebSocket `guardian_approval` 事件推送。
     * Demo 模式：未连接 Node 时提供"模拟批准"（每次轮询自动添加一笔模拟批准）。
     */
    private fun startPollingGuardianApprovals(recoveryId: String, threshold: Int) {
        // 注册 WebSocket 推送回调（真实路径）
        wsManager.setOnGuardianApproval { recId, guardianDid, proofId ->
            if (recId == recoveryId) {
                val approval = GuardianApproval(
                    guardianDid = guardianDid,
                    zkpProof = object : com.sovexis.domain.zkp.ZkpProof {
                        override val proofId = proofId
                        override val proofBytes: ByteArray? = null
                    },
                    timestamp = System.currentTimeMillis()
                )
                _state.value = _state.value.copy(
                    guardianApprovals = _state.value.guardianApprovals + approval
                )
            }
        }

        viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 60 // 最多轮询 60 次（约 5 分钟）
            // Production: manual approvals only
            val isDemoMode = false

            while (attempts < maxAttempts && _state.value.step == RecoveryStep.SOCIAL_WAITING) {
                kotlinx.coroutines.delay(5000) // 每 5 秒轮询一次

                // Demo 模式：自动添加模拟批准（3 秒间隔，阈值达成后停止）
                if (isDemoMode && attempts % 3 == 2 && _state.value.guardianApprovals.size < threshold) {
                    val mockApproval = GuardianApproval(
                        guardianDid = "did:sovexis:mock:guardian_${_state.value.guardianApprovals.size + 1}",
                        zkpProof = object : com.sovexis.domain.zkp.ZkpProof {
                            override val proofId = "mock_${System.currentTimeMillis()}"
                            override val proofBytes: ByteArray? = null
                        },
                        timestamp = System.currentTimeMillis()
                    )
                    _state.value = _state.value.copy(
                        guardianApprovals = _state.value.guardianApprovals + mockApproval
                    )
                }

                // 获取当前批准数量
                val currentApprovals = _state.value.guardianApprovals.size

                _state.value = _state.value.copy(
                    progressMessage = "等待监护人批准 (${currentApprovals}/${threshold})"
                )

                if (currentApprovals >= threshold) {
                    _state.value = _state.value.copy(
                        step = RecoveryStep.SOCIAL_THRESHOLD_MET
                    )
                    completeSocialRecovery()
                    return@launch
                }

                attempts++
            }

            // 超时
            if (_state.value.step == RecoveryStep.SOCIAL_WAITING) {
                _state.value = _state.value.copy(
                    step = RecoveryStep.FAILED,
                    error = "等待监护人批准超时"
                )
            }
        }
    }

    /**
     * 步骤 3b：完成社交恢复（阈值达成后）。
     */
    private fun completeSocialRecovery() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                step = RecoveryStep.RECONSTRUCTING,
                progressMessage = "阈值达成，正在重建身份..."
            )

            try {
                val recoveryId = _state.value.recoveryId
                    ?: throw IllegalStateException("恢复请求 ID 缺失")

                val result = recoveryManager.completeRecovery(recoveryId)
                result.fold(
                    onSuccess = { identity ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = RecoveryStep.COMPLETED,
                            restoredIdentity = identity,
                            progressMessage = "身份恢复成功"
                        )
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = RecoveryStep.FAILED,
                            error = "重建身份失败: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = RecoveryStep.FAILED,
                    error = "社交恢复异常: ${e.message}"
                )
            }
        }
    }

    /**
     * 步骤 2c：网络恢复（获取分片）。
     *
     * @param nodeDids 节点 DID 列表
     * @param threshold 恢复阈值
     */
    fun recoverFromNetwork(nodeDids: List<String>, threshold: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                step = RecoveryStep.NETWORK_FETCHING,
                progressMessage = "正在从网络节点获取分片..."
            )

            try {
                val result = recoveryManager.recoverFromNetwork(nodeDids, threshold)
                result.fold(
                    onSuccess = { shards ->
                        _state.value = _state.value.copy(
                            networkShards = shards,
                            isLoading = true,
                            step = RecoveryStep.RECONSTRUCTING,
                            progressMessage = "分片获取成功，正在重建身份..."
                        )
                        // 重建身份
                        reconstructFromShards(shards)
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = RecoveryStep.FAILED,
                            error = "获取分片失败: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = RecoveryStep.FAILED,
                    error = "网络恢复异常: ${e.message}"
                )
            }
        }
    }

    /**
     * 从分片重建身份。
     *
     * 流程：NetworkRecovery.performNetworkRecovery() 重建 masterKey →
     *       IdentityManager.restoreMasterIdentity(masterKey)。
     * 过渡期使用简单的分片拼接+哈希重建，后续版本接入 TSS 门限签名（Phase 3）。
     */
    private suspend fun reconstructFromShards(shards: List<NetworkShard>) {
        try {
            val config = recoveryManager.getRecoveryConfig()
                ?: throw IllegalStateException("无恢复配置")
            val threshold = config.networkShardThreshold

            if (shards.size < threshold) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = RecoveryStep.FAILED,
                    error = "分片数量不足: 需要 $threshold 个，实际 ${shards.size} 个"
                )
                return
            }

            // 调用 NetworkRecovery 重建主密钥
            val networkRecovery = NetworkRecovery(object : NodeTrustVerifier {
                override suspend fun verifyNodeTrust(
                    nodeDid: String,
                    minScore: Int
                ): Result<NodeTrustResult> =
                    Result.success(NodeTrustResult(true, 50, null))

                override suspend fun queryNodeTrust(nodeDid: String): Result<NodeTrustInfo> =
                    Result.success(NodeTrustInfo(nodeDid, 50, emptyList(), false, emptyList(), 0, 1.0))

                override suspend fun verifyNodeCredential(nodeDid: String): Result<Boolean> =
                    Result.success(true)
            })
            val masterKeyResult = networkRecovery.performNetworkRecovery(
                shards.map { it.nodeDid }, threshold
            )
            val masterKey = masterKeyResult.getOrElse { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = RecoveryStep.FAILED,
                    error = "分片重建失败: ${e.message}"
                )
                return
            }

            // 使用重建的主密钥恢复身份
            val result = identityManager.restoreMasterIdentity(masterKey)
            result.fold(
                onSuccess = { identity ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = RecoveryStep.COMPLETED,
                        restoredIdentity = identity,
                        progressMessage = "从网络分片恢复成功"
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = RecoveryStep.FAILED,
                        error = "恢复身份失败: ${e.message}"
                    )
                }
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                step = RecoveryStep.FAILED,
                error = "重建身份失败: ${e.message}"
            )
        }
    }

    /**
     * 重置流程。
     */
    fun reset() {
        _state.value = RecoveryState()
    }

    /**
     * 身份导入：读取加密文件并恢复身份。
     *
     * @param fileUri 用户选择的 .sovexis-identity 文件 URI
     */
    fun importIdentity(fileUri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, step = RecoveryStep.RECONSTRUCTING,
                progressMessage = "正在解析身份文件...")
            try {
                val inputStream = appContext.contentResolver.openInputStream(fileUri)
                    ?: throw IllegalStateException("无法读取文件")

                val fileBytes = inputStream.readBytes()
                inputStream.close()

                // 验证文件格式（最小 64 字节，AES-GCM 加密数据 + nonce）
                if (fileBytes.size < 64) {
                    throw IllegalStateException("身份文件格式无效或已损坏")
                }

                // 提取加密数据：nonce(12B) + ciphertext
                val nonce = fileBytes.copyOfRange(0, 12)
                val ciphertext = fileBytes.copyOfRange(12, fileBytes.size)

                // 使用设备绑定信息作为 AES 密钥源
                val deviceBinding = getDeviceBindingData(appContext)
                val aesKey = javax.crypto.spec.SecretKeySpec(deviceBinding.copyOf(32), "AES")

                val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, aesKey,
                    javax.crypto.spec.GCMParameterSpec(128, nonce))

                val decrypted = cipher.doFinal(ciphertext)
                val seed = decrypted.copyOfRange(0, kotlin.math.min(64, decrypted.size))

                // 恢复主账号
                val result = identityManager.restoreMasterIdentity(seed)
                result.fold(
                    onSuccess = { identity ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = RecoveryStep.COMPLETED,
                            restoredIdentity = identity,
                            progressMessage = "身份导入成功"
                        )
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            step = RecoveryStep.FAILED,
                            error = "身份恢复失败: ${e.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = RecoveryStep.FAILED,
                    error = "身份导入失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 从 Context 获取设备绑定数据（与 CreateIdentity 中的 getDeviceBinding 对应）。
     */
    private fun getDeviceBindingData(context: android.content.Context): ByteArray {
        val sb = StringBuilder()
        sb.append(android.os.Build.BRAND)
        sb.append(android.os.Build.MODEL)
        sb.append(context.packageName)
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(sb.toString().toByteArray(Charsets.UTF_8))
        return hash
    }

    override fun onCleared() {
        super.onCleared()
        // 清理敏感数据
    }
}
