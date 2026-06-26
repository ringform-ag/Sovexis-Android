package com.sovexis.ui.feature.credential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.communication.CryptoCommLayer
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.zkp.ZkpProofData as ZkpProof
import com.sovexis.core.result.getOrThrow
import com.sovexis.domain.communication.RawMessage
import com.sovexis.domain.vc.CredentialService
import com.sovexis.domain.zkp.ZkpCacheManager
import com.sovexis.domain.zkp.ZkpService
import com.sovexis.domain.zkp.ZkpProveRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Arrays
import javax.inject.Inject

/**
 * 凭证出示流程步骤。
 */
enum class CredentialStep {
    /** 空闲状态 */
    IDLE,

    /** 选择披露字段 */
    SELECTING_FIELDS,

    /** 生物认证 */
    BIOMETRIC_PROMPT,

    /** KDFS 图案绘制 */
    KDFS_DRAW,

    /** ZKP 证明生成中 */
    ZKP_GENERATING,

    /** 发送中 */
    SENDING,

    /** 完成 */
    COMPLETED,

    /** 失败 */
    FAILED
}

/**
 * 凭证状态。
 *
 * @param step 当前步骤
 * @param selectedCredentialId 选中的凭证 ID
 * @param disclosedFields 披露的字段列表
 * @param challenge 挑战值
 * @param cachedProof 缓存的证明（缓存命中时非空）
 * @param isLoading 是否加载中
 * @param error 错误信息
 * @param resultMessage 结果消息
 */
data class CredentialState(
    val step: CredentialStep = CredentialStep.IDLE,
    val selectedCredentialId: String? = null,
    val disclosedFields: List<String> = emptyList(),
    val challenge: ByteArray? = null,
    val cachedProof: ZkpProof? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val resultMessage: String? = null
)

/**
 * 凭证出示 ViewModel。
 *
 * 管理凭证出示的完整流程（含 ZKP 缓存 + 选择性披露）。
 */
@HiltViewModel
class CredentialViewModel @Inject constructor(
    private val credentialService: CredentialService,
    private val identityManager: IdentityManager,
    private val zkpService: ZkpService,
    private val zkpCacheManager: ZkpCacheManager,
    private val cryptoCommLayer: CryptoCommLayer
) : ViewModel() {

    private val _state = MutableStateFlow(CredentialState())
    val state: StateFlow<CredentialState> = _state.asStateFlow()

    /**
     * 步骤 1：用户选择凭证和披露字段后，发起出示。
     *
     * @param credentialId 凭证 ID
     * @param disclosedFields 披露字段列表
     * @param challenge 挑战值
     * @param requireFresh 是否要求新鲜证明（忽略缓存）
     */
    fun initiatePresentation(
        credentialId: String,
        disclosedFields: List<String>,
        challenge: ByteArray,
        requireFresh: Boolean = false
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedCredentialId = credentialId,
                disclosedFields = disclosedFields,
                challenge = challenge,
                isLoading = true
            )

            try {
                // 生成缓存键
                val cacheKey = generateCacheKey(credentialId, challenge, disclosedFields)

                // 先查缓存
                val cachedProof = zkpCacheManager.get(cacheKey, requireFresh)
                if (cachedProof != null) {
                    // 缓存命中——直接组装 VP
                    _state.value = _state.value.copy(
                        cachedProof = cachedProof,
                        isLoading = false,
                        step = CredentialStep.SENDING
                    )
                    assembleAndSendPresentation(cachedProof)
                    return@launch
                }

                // 缓存未命中——走完整流程
                // 判断 KDFS 缓存
                val kdfsHash = zkpCacheManager.getCachedKdfs()
                if (kdfsHash != null) {
                    // KDFS 在缓存期内，跳过绘制
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = CredentialStep.BIOMETRIC_PROMPT
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        step = CredentialStep.KDFS_DRAW
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = CredentialStep.FAILED,
                    error = "出示初始化失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 步骤 2：BiometricPrompt 成功回调（异步生成 ZKP）。
     *
     * @param biometricSignature 生物认证签名
     */
    fun onBiometricSuccess(biometricSignature: ByteArray) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                step = CredentialStep.ZKP_GENERATING
            )

            try {
                // 异步生成 ZKP 证明
                val proof = withContext(Dispatchers.IO) {
                    generateZkpProof(biometricSignature)
                }

                // 缓存证明
                val selectedId = _state.value.selectedCredentialId
                    ?: throw IllegalStateException("selectedCredentialId not set")
                val challenge = _state.value.challenge
                    ?: throw IllegalStateException("challenge not set")
                val cacheKey = generateCacheKey(
                    selectedId,
                    challenge,
                    _state.value.disclosedFields
                )
                zkpCacheManager.put(cacheKey, proof)

                _state.value = _state.value.copy(
                    isLoading = false,
                    step = CredentialStep.SENDING
                )
                assembleAndSendPresentation(proof)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = CredentialStep.FAILED,
                    error = "ZKP 生成失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 步骤 3：KDFS 图案完成回调。
     *
     * @param kdfsHash KDFS 图案哈希
     */
    fun onKdfsComplete(kdfsHash: ByteArray) {
        viewModelScope.launch {
            // 将 KDFS 哈希存入缓存，5 分钟内无需重绘
            zkpCacheManager.putCachedKdfs(kdfsHash)
            _state.value = _state.value.copy(step = CredentialStep.BIOMETRIC_PROMPT)
        }
    }

    /**
     * 组装 VerifiablePresentation 并发送。
     *
     * @param proof ZKP 证明
     */
    private suspend fun assembleAndSendPresentation(proof: ZkpProof) {
        try {
                val credentialId = _state.value.selectedCredentialId
                    ?: throw IllegalStateException("selectedCredentialId not set")
            val disclosedFields = _state.value.disclosedFields
            val fromDid = identityManager.getActiveDid()
                ?: throw IllegalStateException("无活跃 DID")

            // 创建 VP（含选择性披露 + ZKP 证明）
            val presentation = credentialService.createPresentation(
                credentialId = credentialId,
                disclosureFields = disclosedFields,
                proof = proof
            ).getOrThrow()

            // 加密发送
            val rawMessage = RawMessage(
                messageId = presentation.presentationId,
                payload = presentation.toString().toByteArray(),
                senderAddress = fromDid,
                timestamp = System.currentTimeMillis()
            )
            cryptoCommLayer.send(rawMessage.payload, fromDid).getOrThrow()

            _state.value = _state.value.copy(
                isLoading = false,
                step = CredentialStep.COMPLETED,
                resultMessage = "凭证出示成功"
            )
        } catch (e: UnsupportedOperationException) {
            // C-08 令牌层 / VC 框架尚未实现 —— 友好提示
            _state.value = _state.value.copy(
                isLoading = false,
                step = CredentialStep.FAILED,
                error = "零知识凭证出示功能即将开放"
            )
        } catch (e: NotImplementedError) {
            _state.value = _state.value.copy(
                isLoading = false,
                step = CredentialStep.FAILED,
                error = "凭证出示功能即将开放"
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                step = CredentialStep.FAILED,
                error = "出示失败: ${e.message}"
            )
        } finally {
            // 安全擦除
            proof.proofBytes?.let { Arrays.fill(it, 0) }
        }
    }

    /**
     * 生成 ZKP 证明。
     *
     * @param biometricSignature 生物认证签名
     * @return ZKP 证明
     */
    private suspend fun generateZkpProof(biometricSignature: ByteArray): ZkpProof {
        val activeDid = identityManager.getActiveDid()
            ?: throw IllegalStateException("无活跃 DID")
        val childIdentity = identityManager.getChildIdentity(activeDid)
            ?: throw IllegalStateException("副账号不存在")

        val request = ZkpProveRequest(
            biometricSignature = biometricSignature,
            deviceBindingData = identityManager.getDeviceBindingData(),
            kdfsPatternHash = zkpCacheManager.getCachedKdfs()
                ?: throw IllegalStateException("KDFS 未缓存"),
            sessionNonce = _state.value.challenge
                ?: throw IllegalStateException("challenge not set"),
            publicKeyPem = childIdentity.publicKeyPem,
            expectedCommitmentRoot = identityManager.getExpectedCommitmentRoot(activeDid)
                ?: throw IllegalStateException("预期承诺根缺失")
        )

        val proof = zkpService.prove(request).getOrThrow()
        // ZkpProofData 已实现 ZkpProof 接口，直接返回
        return proof
    }

    /**
     * 生成缓存键：SHA256(credentialId + challenge + disclosedFields)
     */
    private fun generateCacheKey(
        credentialId: String,
        challenge: ByteArray,
        disclosedFields: List<String>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(credentialId.toByteArray())
        digest.update(challenge)
        disclosedFields.sorted().forEach { digest.update(it.toByteArray()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * BiometricPrompt 失败回调。
     *
     * @param error 错误信息
     */
    fun onBiometricFailed(error: String) {
        _state.value = _state.value.copy(
            step = CredentialStep.FAILED,
            error = error
        )
    }

    /**
     * 重置流程。
     */
    fun reset() {
        _state.value = CredentialState()
    }

    override fun onCleared() {
        super.onCleared()
        // 安全清理
        _state.value.challenge?.let { Arrays.fill(it, 0) }
        _state.value.cachedProof?.proofBytes?.let { Arrays.fill(it, 0) }
    }
}
