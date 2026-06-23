package com.sovexis.domain.credential

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CredentialIssuer — 安卓端凭证签发器
 *
 * 负责签发、验证、撤销 Sovexis 数字凭证。
 * 凭证格式遵循统一凭证规范（v1.0），签名使用 Ed25519。
 */
@Singleton
class CredentialIssuer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CredentialIssuer"
        private const val KEY_ALIAS = "sovexis_credential_signing"
        private const val CONTEXT_URI = "https://sovexis.io/credentials/v1"
    }

    // 本地缓存：代理权凭证 ID（从 Node 端获取或本地记录）
    private var agentDelegationCredId: String? = null

    /**
     * 签发代理权凭证 (C-01) — 凭证委派链的根
     *
     * 在首次 Node 绑定成功后调用，主账号签发代理权给 Node 管家。
     * 每个 Node 拥有独立的管家密钥对，因此 C-01 按 nodeDID 分别签发。
     *
     * 签名边界（重要）：
     *   管家使用自身 Node 密钥对签署常规操作（存储合约/P2P 消息/PoSt 证明）。
     *   主账号级操作（支付确认/身份切换/服务商升级）必须通过 C-08
     *   一次性授权令牌回传 Android 端签名。
     *
     * @param masterDID 主账号 DID，签发者
     * @param nodeDID   目标 Node 节点的管家 DID，持有者
     * @return 已签名的代理权凭证
     */
    suspend fun issueAgentDelegation(
        masterDID: String,
        nodeDID: String
    ): Credential = withContext(Dispatchers.IO) {
        val credId = "cred:agent:$nodeDID:${UUID.randomUUID()}"
        val now = Instant.now()

        val cred = Credential(
            context = CONTEXT_URI,
            id = credId,
            type = CredentialType.AGENT_DELEGATION,
            version = "1.0",
            issuer = IssuerInfo(did = masterDID, delegationChain = emptyList()),
            holder = HolderInfo(did = nodeDID, role = "butler"),
            subject = SubjectInfo(delegatedIdentity = nodeDID),
            permissions = PermissionSet(
                canSign = true,
                canPay = false,
                canContract = true,
                canDelegate = false,
                canBroadcast = true
            ),
            validity = ValidityPeriod(
                issuedAt = now.toString(),
                expiresAt = null,       // 永久有效，直至解绑
                revocable = true
            ),
            proof = CredentialProof(
                type = "Ed25519Signature2020",
                created = now.toString(),
                verificationMethod = "did:sovexis:$masterDID#keys-1",
                proofValue = ""
            )
        )

        val signed = signCredential(cred)
        agentDelegationCredId = signed.id
        Log.i(TAG, "签发代理权凭证 C-01: $credId master=$masterDID → node=$nodeDID")
        signed
    }

    /**
     * 签发身份委派凭证 (C-02)
     *
     * @param subAccountDID 副账号 DID
     * @param permissions 权限集合
     * @param existingAgentCredId 已有代理权凭证 ID（作为委派链根）
     */
    suspend fun issueIdentityDelegation(
        subAccountDID: String,
        permissions: PermissionSet,
        existingAgentCredId: String? = null
    ): Credential = withContext(Dispatchers.IO) {
        val credId = "cred:id:$subAccountDID:${UUID.randomUUID()}"
        val now = Instant.now()

        // 委派链：引用代理权凭证
        val delegationChain = mutableListOf<String>()
        val parentId = existingAgentCredId ?: agentDelegationCredId
        if (!parentId.isNullOrEmpty()) {
            delegationChain.add(parentId)
        }

        val cred = Credential(
            context = CONTEXT_URI,
            id = credId,
            type = CredentialType.IDENTITY_DELEGATION,
            version = "1.0",
            issuer = IssuerInfo(did = getMasterDID(), delegationChain = delegationChain),
            holder = HolderInfo(did = subAccountDID, role = "butler"),
            subject = SubjectInfo(delegatedIdentity = subAccountDID),
            permissions = permissions,
            validity = ValidityPeriod(
                issuedAt = now.toString(),
                revocable = true
            ),
            proof = CredentialProof(
                type = "Ed25519Signature2020",
                created = now.toString(),
                verificationMethod = "did:sovexis:${getMasterDID()}#keys-1",
                proofValue = ""
            )
        )

        // 签名
        val signed = signCredential(cred)
        Log.i(TAG, "签发身份委派凭证: $credId -> $subAccountDID")
        signed
    }

    /**
     * 签发任务授权凭证 (C-07)
     */
    suspend fun issueTaskAuthorization(
        taskId: String,
        allowedTools: List<String>,
        maxSteps: Int,
        existingAgentCredId: String? = null
    ): Credential = withContext(Dispatchers.IO) {
        val credId = "cred:task:$taskId"
        val now = Instant.now()

        val delegationChain = mutableListOf<String>()
        val parentId = existingAgentCredId ?: agentDelegationCredId
        if (!parentId.isNullOrEmpty()) {
            delegationChain.add(parentId)
        }

        val cred = Credential(
            context = CONTEXT_URI,
            id = credId,
            type = CredentialType.TASK_AUTHORIZATION,
            version = "1.0",
            issuer = IssuerInfo(did = getMasterDID(), delegationChain = delegationChain),
            holder = HolderInfo(did = getMasterDID(), role = "butler"),
            subject = SubjectInfo(
                taskId = taskId,
                allowedTools = allowedTools,
                maxSteps = maxSteps
            ),
            permissions = PermissionSet(
                canSign = false,
                canPay = false,
                canContract = false,
                canDelegate = false,
                canBroadcast = true
            ),
            validity = ValidityPeriod(
                issuedAt = now.toString(),
                revocable = true
            ),
            proof = CredentialProof(
                type = "Ed25519Signature2020",
                created = now.toString(),
                verificationMethod = "did:sovexis:${getMasterDID()}#keys-1",
                proofValue = ""
            )
        )

        val signed = signCredential(cred)
        Log.i(TAG, "签发任务授权凭证: $credId for task $taskId")
        signed
    }

    /**
     * 验证交易确认凭证 (C-03)
     */
    suspend fun verifyTransactionConfirmation(credJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cred = parseCredential(credJson)
                ?: return@withContext false

            // 检查类型
            if (cred.type != CredentialType.TRANSACTION_CONFIRMATION) {
                Log.w(TAG, "凭证类型不匹配: ${cred.type}")
                return@withContext false
            }

            // 验证签名
            if (!verifySignature(cred)) {
                Log.w(TAG, "凭证签名验证失败")
                return@withContext false
            }

            // 检查有效期
            if (cred.isExpired()) {
                Log.w(TAG, "凭证已过期")
                return@withContext false
            }

            Log.i(TAG, "交易确认凭证验证通过: ${cred.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "验证交易确认凭证失败", e)
            false
        }
    }

    /**
     * 撤销凭证
     */
    suspend fun revokeCredential(credId: String) = withContext(Dispatchers.IO) {
        // 本地记录撤销
        val revokedSet = loadRevokedSet().toMutableSet()
        revokedSet.add(credId)
        saveRevokedSet(revokedSet)

        Log.i(TAG, "凭证已撤销: $credId")
    }

    /**
     * 对凭证签名
     */
    private fun signCredential(cred: Credential): Credential {
        // 移除 proof 字段后计算签名消息
        val proofCopy = cred.proof
        cred.proof = CredentialProof("", "", "", "")
        val msgJson = cred.toJson()
        cred.proof = proofCopy

        // Ed25519 签名（通过 Android Keystore）
        val sigBytes = signWithEd25519(msgJson)
        val sigB64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP)

        return cred.copy(
            proof = cred.proof.copy(proofValue = sigB64)
        )
    }

    /**
     * Ed25519 签名（简化实现，实际应通过 Keystore）
     */
    private fun signWithEd25519(message: String): ByteArray {
        // 实际实现：从 Android Keystore 取出 Ed25519 密钥对签名
        // 当前返回消息 hash 占位
        return MessageDigest.getInstance("SHA-256").digest(message.toByteArray())
    }

    /**
     * 验证凭证签名
     */
    private fun verifySignature(cred: Credential): Boolean {
        if (cred.proof.proofValue.isEmpty()) return false
        try {
            val sig = Base64.decode(cred.proof.proofValue, Base64.NO_WRAP)
            // 实际验证：从 verificationMethod 解析公钥
            return sig.size >= 64 // Ed25519 签名大小占位检查
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * 解析凭证 JSON
     */
    fun parseCredential(json: String): Credential? {
        return try {
            val obj = JSONObject(json)
            val issuer = obj.getJSONObject("issuer")
            val holder = obj.getJSONObject("holder")
            val subject = obj.getJSONObject("subject")
            val perms = obj.getJSONObject("permissions")
            val validity = obj.getJSONObject("validity")
            val proof = obj.getJSONObject("proof")

            // 解析委派链
            val chainArr = issuer.optJSONArray("delegation_chain")
            val chain = mutableListOf<String>()
            if (chainArr != null) {
                for (i in 0 until chainArr.length()) chain.add(chainArr.getString(i))
            }

            // 解析 allowedTools
            val toolsArr = subject.optJSONArray("allowed_tools")
            val tools = mutableListOf<String>()
            if (toolsArr != null) {
                for (i in 0 until toolsArr.length()) tools.add(toolsArr.getString(i))
            }

            Credential(
                context = obj.optString("@context", CONTEXT_URI),
                id = obj.optString("id", ""),
                type = CredentialType.from(obj.optString("type", "")),
                version = obj.optString("version", "1.0"),
                issuer = IssuerInfo(
                    did = issuer.optString("did", ""),
                    delegationChain = chain
                ),
                holder = HolderInfo(
                    did = holder.optString("did", ""),
                    role = holder.optString("role", "butler")
                ),
                subject = SubjectInfo(
                    delegatedIdentity = subject.optString("delegated_identity", ""),
                    transactionId = subject.optString("transaction_id", ""),
                    taskId = subject.optString("task_id", ""),
                    allowedTools = tools,
                    maxSteps = subject.optInt("max_steps", 0),
                    contractId = subject.optString("contract_id", ""),
                    trustScore = subject.optInt("trust_score", 0)
                ),
                permissions = PermissionSet(
                    canSign = perms.optBoolean("can_sign", true),
                    canPay = perms.optBoolean("can_pay", false),
                    canContract = perms.optBoolean("can_contract", true),
                    canDelegate = perms.optBoolean("can_delegate", false),
                    canBroadcast = perms.optBoolean("can_broadcast", true)
                ),
                validity = ValidityPeriod(
                    issuedAt = validity.optString("issued_at", ""),
                    expiresAt = validity.optString("expires_at", "").ifEmpty { null },
                    revocable = validity.optBoolean("revocable", true)
                ),
                proof = CredentialProof(
                    type = proof.optString("type", ""),
                    created = proof.optString("created", ""),
                    verificationMethod = proof.optString("verification_method", ""),
                    proofValue = proof.optString("proof_value", "")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析凭证失败", e)
            null
        }
    }

    // ── 本地状态管理 ──

    private fun getMasterDID(): String {
        // 从 IdentityManager / 安全存储获取主账号 DID
        val prefs = context.getSharedPreferences("sovexis_cred", Context.MODE_PRIVATE)
        return prefs.getString("master_did", "") ?: ""
    }

    fun setAgentDelegationCredId(credId: String) {
        agentDelegationCredId = credId
    }

    // ── SYNC-004: 合约转让凭证签发 ──

    /**
     * 签发服务商资质凭证 (C-09)
     */
    suspend fun issueServiceProviderCredential(
        serviceDID: String,
        services: List<ServiceDeclaration>,
        regions: List<String>,
        totalStake: Double,
        zkpProof: ByteArray? = null,
        existingAgentCredId: String? = null
    ): Credential = withContext(Dispatchers.IO) {
        val agentCredId = existingAgentCredId ?: agentDelegationCredId ?: ""
        val credId = "cred:svc:$serviceDID"
        val now = Instant.now()

        val delegationChain = mutableListOf<String>()
        if (agentCredId.isNotEmpty()) delegationChain.add(agentCredId)

        val cred = Credential(
            context = CONTEXT_URI,
            id = credId,
            type = CredentialType.SERVICE_PROVIDER,
            version = "1.0",
            issuer = IssuerInfo(did = getMasterDID(), delegationChain = delegationChain),
            holder = HolderInfo(did = serviceDID, role = "butler"),
            subject = SubjectInfo(
                services = services,
                regions = regions,
                totalStake = totalStake,
                oracleEndorsement = zkpProof
            ),
            permissions = PermissionSet(
                canSign = true,
                canPay = false,
                canContract = true,
                canDelegate = false,
                canBroadcast = true,
                canBid = true,
                canAdvertise = true
            ),
            validity = ValidityPeriod(issuedAt = now.toString(), revocable = true),
            proof = CredentialProof(
                type = "Ed25519Signature2020",
                created = now.toString(),
                verificationMethod = "did:sovexis:${getMasterDID()}#keys-1",
                proofValue = ""
            )
        )

        val signed = signCredential(cred)
        Log.i(TAG, "签发服务商资质凭证: $credId did=$serviceDID services=${services.size}")
        signed
    }

    /**
     * 签发一次性行为令牌 (C-08)
     * 5 分钟有效期，单次使用，行为绑定。
     * 支持: sign_contract, confirm_payment, transfer_contract, submit_proof, recover_shards
     */
    suspend fun issueAuthorizationToken(
        action: String,
        target: String,
        parameters: Map<String, Any> = emptyMap(),
        existingAgentCredId: String? = null
    ): Credential = withContext(Dispatchers.IO) {
        val agentCredId = existingAgentCredId ?: agentDelegationCredId ?: ""
        val tokenId = "cred:token:${action}_${target}_${UUID.randomUUID()}"
        val now = Instant.now()
        val expiresAt = now.plusSeconds(300) // 5 分钟

        val delegationChain = mutableListOf<String>()
        if (agentCredId.isNotEmpty()) delegationChain.add(agentCredId)

        val cred = Credential(
            context = CONTEXT_URI,
            id = tokenId,
            type = CredentialType.AUTHORIZATION_TOKEN,
            version = "1.0",
            issuer = IssuerInfo(did = getMasterDID(), delegationChain = delegationChain),
            holder = HolderInfo(did = getMasterDID(), role = "butler"),
            subject = SubjectInfo(
                action = action,
                target = target,
                parameters = parameters
            ),
            permissions = PermissionSet(
                canSign = false,
                canPay = false,
                canContract = false,
                canDelegate = false,
                canBroadcast = false
            ),
            validity = ValidityPeriod(
                issuedAt = now.toString(),
                expiresAt = expiresAt.toString(),
                revocable = false,
                singleUse = true
            ),
            proof = CredentialProof(
                type = "Ed25519Signature2020",
                created = now.toString(),
                verificationMethod = "did:sovexis:${getMasterDID()}#keys-1",
                proofValue = ""
            )
        )
        val signed = signCredential(cred)
        Log.i(TAG, "签发一次性令牌: $tokenId action=$action target=$target")
        signed
    }

    /**
     * 签发合约转让凭证 — 用户主动承接（方案B）
     * 1. 先为新副账号签发 C-02 身份委派凭证（委派链→代理权凭证）
     * 2. 再构建合约转让凭证（委派链包含：旧C-02引用 + 新C-02引用 + 代理权凭证引用）
     *
     * 这样对方节点验证时能追溯完整主权链路：新副账号 ← 代理权 ← 主账号
     */
    suspend fun issueContractTransfer(
        oldDID: String,
        newDID: String,
        contractIDs: List<String>,
        existingAgentCredId: String? = null
    ): Pair<Credential, Credential> = withContext(Dispatchers.IO) {
        val agentCredId = existingAgentCredId ?: agentDelegationCredId ?: ""

        // Step 1: 为新副账号签发身份委派凭证 (C-02)
        val newIdentityCred = issueIdentityDelegation(
            subAccountDID = newDID,
            permissions = PermissionSet(
                canSign = true,
                canPay = false,
                canContract = false,
                canDelegate = false,
                canBroadcast = true
            ),
            existingAgentCredId = agentCredId
        )

        // Step 2: 构建合约转让凭证（委派链=旧C-02→新C-02→代理权）
        // 查找旧 C-02 凭证 ID（从本地缓存或构造）
        val oldC02CredId = "cred:id:$oldDID:latest"

        val transferCredId = "cred:transfer:$oldDID->$newDID:${UUID.randomUUID()}"
        val now = Instant.now()

        val transferCred = Credential(
            context = CONTEXT_URI,
            id = transferCredId,
            type = CredentialType.STORAGE_CONTRACT,
            version = "1.0",
            issuer = IssuerInfo(
                did = oldDID,
                delegationChain = listOf(oldC02CredId, newIdentityCred.id, agentCredId)
            ),
            holder = HolderInfo(did = newDID, role = "butler"),
            subject = SubjectInfo(delegatedIdentity = newDID),
            permissions = PermissionSet(
                canSign = true,
                canPay = false,
                canContract = false,
                canDelegate = false,
                canBroadcast = false
            ),
            validity = ValidityPeriod(issuedAt = now.toString(), revocable = false),
            proof = CredentialProof(
                type = "Ed25519Signature2020",
                created = now.toString(),
                verificationMethod = "did:sovexis:${getMasterDID()}#keys-1",
                proofValue = ""
            )
        )

        val signedTransfer = signCredential(transferCred)
        Log.i(TAG, "签发合约转让凭证: $transferCredId chain=[$oldC02CredId → ${newIdentityCred.id} → $agentCredId]")
        Pair(newIdentityCred, signedTransfer)
    }

    /**
     * 签发存储合约凭证 (C-04) — 创建存储需求（单方签发）。
     *
     * 合约是承诺，不是文件。这份凭证证明用户在网络上发布了一份存储需求，
     * 包含容量、期限、SLA 要求和质押金额。对方节点接受后，双方在 Node 端完成双签。
     *
     * @param masterDID 主账号 DID
     * @param capacity  存储容量（GB）
     * @param durationDays 合约期限（天）
     * @param stake     质押金额（AGT）
     * @param slaUptime SLA 可用性要求（0.999 = 99.9%）
     * @param existingAgentCredId 代理权凭证 ID
     */
    suspend fun issueStorageContract(
        masterDID: String,
        capacity: Double,
        durationDays: Int,
        stake: Double,
        slaUptime: Double,
        existingAgentCredId: String? = null
    ): Credential = withContext(Dispatchers.IO) {
        val agentCredId = existingAgentCredId ?: agentDelegationCredId ?: ""
        val contractId = "cred:contract:${UUID.randomUUID()}"
        val now = Instant.now()
        val expiresAt = now.plusSeconds(durationDays.toLong() * 86400)

        val delegationChain = mutableListOf<String>()
        if (agentCredId.isNotEmpty()) delegationChain.add(agentCredId)

        val cred = Credential(
            context = CONTEXT_URI,
            id = contractId,
            type = CredentialType.STORAGE_CONTRACT,
            version = "1.0",
            issuer = IssuerInfo(did = masterDID, delegationChain = delegationChain),
            holder = HolderInfo(did = masterDID, role = "user"),
            subject = SubjectInfo(
                contractId = contractId,
                services = listOf(ServiceDeclaration(
                    serviceType = "storage",
                    capacity = capacity,
                    slaUptime = slaUptime,
                    slaResponseMs = 0,
                    unitPrice = "议价",
                    stakeLocked = stake
                )),
                totalStake = stake,
                durationDays = durationDays
            ),
            permissions = PermissionSet(
                canSign = false,       // 需对方签署后生效
                canPay = false,
                canContract = false,
                canDelegate = false,
                canBroadcast = true
            ),
            validity = ValidityPeriod(
                issuedAt = now.toString(),
                expiresAt = expiresAt.toString(),
                revocable = true
            ),
            proof = CredentialProof(
                type = "Ed25519Signature2020",
                created = now.toString(),
                verificationMethod = "did:sovexis:$masterDID#keys-1",
                proofValue = ""
            )
        )

        val signed = signCredential(cred)
        Log.i(TAG, "签发存储合约凭证 C-04: $contractId capacity=${capacity}GB duration=${durationDays}d stake=$stake")
        signed
    }

    private fun loadRevokedSet(): Set<String> {
        val prefs = context.getSharedPreferences("sovexis_cred", Context.MODE_PRIVATE)
        return prefs.getStringSet("revoked_ids", emptySet()) ?: emptySet()
    }

    private fun saveRevokedSet(set: Set<String>) {
        val prefs = context.getSharedPreferences("sovexis_cred", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("revoked_ids", set).apply()
    }
}

/**
 * 序列化凭证为 JSON（顶层扩展函数）
 */
fun Credential.toJson(): String {
    val obj = JSONObject().apply {
        put("@context", context)
        put("id", id)
        put("type", type.value)
        put("version", version)
        put("issuer", JSONObject().apply {
            put("did", issuer.did)
            put("delegation_chain", JSONArray(issuer.delegationChain))
        })
        put("holder", JSONObject().apply {
            put("did", holder.did)
            put("role", holder.role)
        })
        put("subject", JSONObject().apply {
            if (subject.delegatedIdentity.isNotEmpty()) put("delegated_identity", subject.delegatedIdentity)
            if (subject.transactionId.isNotEmpty()) put("transaction_id", subject.transactionId)
            if (subject.taskId.isNotEmpty()) put("task_id", subject.taskId)
            if (subject.allowedTools.isNotEmpty()) put("allowed_tools", JSONArray(subject.allowedTools))
            if (subject.maxSteps > 0) put("max_steps", subject.maxSteps)
            if (subject.contractId.isNotEmpty()) put("contract_id", subject.contractId)
            if (subject.trustScore > 0) put("trust_score", subject.trustScore)
        })
        put("permissions", JSONObject().apply {
            put("can_sign", permissions.canSign)
            put("can_pay", permissions.canPay)
            put("can_contract", permissions.canContract)
            put("can_delegate", permissions.canDelegate)
            put("can_broadcast", permissions.canBroadcast)
        })
        put("validity", JSONObject().apply {
            put("issued_at", validity.issuedAt)
            if (!validity.expiresAt.isNullOrEmpty()) put("expires_at", validity.expiresAt)
            put("revocable", validity.revocable)
        })
        put("proof", JSONObject().apply {
            put("type", proof.type)
            put("created", proof.created)
            put("verification_method", proof.verificationMethod)
            put("proof_value", proof.proofValue)
        })
    }
    return obj.toString(2)
}

// ── Data Classes ──

enum class CredentialType(val value: String) {
    AGENT_DELEGATION("C-01"),
    IDENTITY_DELEGATION("C-02"),
    TRANSACTION_CONFIRMATION("C-03"),
    STORAGE_CONTRACT("C-04"),
    TRUST_PROOF("C-05"),
    TSS_SHARE("C-06"),
    TASK_AUTHORIZATION("C-07"),
    AUTHORIZATION_TOKEN("C-08"),
    SERVICE_PROVIDER("C-09");

    companion object {
        fun from(value: String): CredentialType =
            entries.find { it.value == value } ?: AGENT_DELEGATION
    }
}

data class Credential(
    val context: String,
    val id: String,
    val type: CredentialType,
    val version: String,
    val issuer: IssuerInfo,
    val holder: HolderInfo,
    val subject: SubjectInfo,
    val permissions: PermissionSet,
    val validity: ValidityPeriod,
    var proof: CredentialProof // mutable for signing
) {
    fun isExpired(): Boolean {
        if (validity.expiresAt.isNullOrEmpty()) return false
        return try {
            val t = Instant.parse(validity.expiresAt)
            Instant.now().isAfter(t)
        } catch (_: Exception) { false }
    }
}

data class IssuerInfo(
    val did: String,
    val delegationChain: List<String> = emptyList()
)

data class HolderInfo(
    val did: String,
    val role: String = "butler"
)

data class SubjectInfo(
    val delegatedIdentity: String = "",
    val transactionId: String = "",
    val taskId: String = "",
    val allowedTools: List<String> = emptyList(),
    val maxSteps: Int = 0,
    val contractId: String = "",
    val trustScore: Int = 0,
    val services: List<ServiceDeclaration> = emptyList(),
    val regions: List<String> = emptyList(),
    val totalStake: Double = 0.0,
    val durationDays: Int = 0,              // C-04: 合约期限
    val oracleEndorsement: ByteArray? = null,
    // C-08 一次性令牌
    val action: String = "",
    val target: String = "",
    val parameters: Map<String, Any> = emptyMap()
)

data class ServiceDeclaration(
    val serviceType: String,     // storage / relay / compute
    val capacity: Double,         // TB / Mbps / TFLOPS
    val slaUptime: Double,        // 0.999
    val slaResponseMs: Int,       // ms
    val unitPrice: String,        // "议价" or range
    val stakeLocked: Double       // 该项服务单独质押
)

data class PermissionSet(
    val canSign: Boolean = true,
    val canPay: Boolean = false,
    val canContract: Boolean = true,
    val canDelegate: Boolean = false,
    val canBroadcast: Boolean = true,
    val canBid: Boolean = false,
    val canAdvertise: Boolean = false
)

data class ValidityPeriod(
    val issuedAt: String,
    val expiresAt: String? = null,
    val revocable: Boolean = true,
    val singleUse: Boolean = false  // C-08: 一次性使用
)

data class CredentialProof(
    val type: String,
    val created: String,
    val verificationMethod: String,
    val proofValue: String
)
