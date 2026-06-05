package com.sovexis.domain.vc

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.sovexis.core.result.Resource
import com.sovexis.data.local.dao.CredentialDao
import com.sovexis.data.local.entity.CredentialEntity
import com.sovexis.data.local.entity.CredentialStatus
import com.sovexis.domain.crypto.KeyManager
import com.sovexis.domain.did.DidService
import com.sovexis.domain.policy.PolicyCheckResult
import com.sovexis.domain.policy.PolicyEnforcer
import com.sovexis.domain.zkp.ZkpProof
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis 可验证凭证服务实现
 *
 * 2026-06-03 实现：基于 KeyManager ECDSA 签名的 VC/VP 签发/出示/验证链路。
 * 不依赖 Multipaz/Inji 外部库，使用自签名 JSON VC 格式。
 *
 * @author Texno + ringform
 */
@Singleton
class CredentialServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val didService: DidService,
    private val credentialDao: CredentialDao,
    private val keyManager: KeyManager,
    private val policyEnforcer: PolicyEnforcer
) : CredentialService {

    // ========== 常量 ==========

    companion object {
        private const val CONTEXT_VC_V1 = "https://www.w3.org/2018/credentials/v1"
        private const val PROOF_TYPE = "EcdsaSecp256r1Signature2019"
        private const val KEY_ALIAS_PREFIX = "vc_signing_key_"
        private const val QR_SIZE = 512
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ========== 凭证签发 ==========

    override suspend fun issueCredential(
        ownerDid: String,
        credentialType: String,
        claims: Map<String, Any>
    ): Resource<VerifiableCredential> {
        return try {
            // 1) 策略检查
            enforceVaultWrite(ownerDid)

            // 2) 确保签发密钥存在
            val keyAlias = "$KEY_ALIAS_PREFIX$ownerDid"
            if (!keyManager.keyExists(keyAlias)) {
                keyManager.generateKeyPair(keyAlias)
            }

            // 3) 构建 VC JSON
            val credentialId = "urn:uuid:${UUID.randomUUID()}"
            val issuedAt = dateFormat.format(Date())
            val issuerDid = getIssuerDid()

            // 凭证声明（credentialSubject）
            val subject = JSONObject().apply {
                put("id", ownerDid)
                val claimsObj = JSONObject()
                claims.forEach { (k, v) -> claimsObj.put(k, v) }
                put("claims", claimsObj)
            }

            // 选择性披露字段列表
            val disclosureFields = claims.keys.toList()

            // proof 前体（待签名）
            val proofBody = JSONObject().apply {
                put("type", PROOF_TYPE)
                put("created", issuedAt)
                put("verificationMethod", "$issuerDid#keys-1")
                put("proofPurpose", "assertionMethod")
            }

            // 完整 VC
            val vcJson = JSONObject().apply {
                put("@context", JSONArray(listOf(CONTEXT_VC_V1)))
                put("id", credentialId)
                put("type", JSONArray(listOf("VerifiableCredential", credentialType)))
                put("issuer", issuerDid)
                put("issuanceDate", issuedAt)
                put("credentialSubject", subject)
                put("selectiveDisclosureFields", JSONArray(disclosureFields))
                put("proof", proofBody)
            }

            val vcJsonString = vcJson.toString()

            // 4) ECDSA 签名（签名 VC JSON 不含 proofValue 的部分）
            val signature = keyManager.sign(keyAlias, vcJsonString.toByteArray(Charsets.UTF_8))
            val signatureB64 = Base64.encodeToString(signature, Base64.NO_WRAP)
            vcJson.getJSONObject("proof").put("proofValue", signatureB64)

            // 5) 存入 Room
            val entity = CredentialEntity(
                credentialId = credentialId,
                ownerDid = ownerDid,
                credentialType = credentialType,
                issuerDid = issuerDid,
                issuanceDate = System.currentTimeMillis(),
                expirationDate = null,
                credentialJson = vcJson.toString(),
                presentationJson = null,
                status = CredentialStatus.ACTIVE,
                selectiveDisclosureFields = JSONArray(disclosureFields).toString()
            )
            credentialDao.insertCredential(entity)

            // 6) 返回域模型
            val vc = entityToVC(entity)
            Resource.Success(vc)
        } catch (e: Exception) {
            Resource.Error(message = "签发凭证失败: ${e.message}")
        }
    }

    // ========== 凭证出示（基础版） ==========

    override suspend fun createPresentation(
        credentialId: String,
        disclosureFields: List<String>?
    ): Resource<VerifiablePresentation> {
        return try {
            val entity = credentialDao.getCredentialById(credentialId)
                ?: return Resource.Error(message = "凭证不存在: $credentialId")

            if (entity.status != CredentialStatus.ACTIVE) {
                return Resource.Error(message = "凭证状态异常: ${entity.status}")
            }

            val ownerDid = entity.ownerDid

            // 1) 加载原始 VC
            val vcJson = JSONObject(entity.credentialJson)
            // 选择性披露：过滤 credentialSubject.claims
            if (!disclosureFields.isNullOrEmpty()) {
                val claims = vcJson.getJSONObject("credentialSubject").getJSONObject("claims")
                val filtered = JSONObject()
                disclosureFields.forEach { field ->
                    if (claims.has(field)) filtered.put(field, claims.get(field))
                }
                vcJson.getJSONObject("credentialSubject").put("claims", filtered)
                vcJson.put("selectiveDisclosureFields", JSONArray(disclosureFields))
            }

            // 2) 构建 VP
            val presentationId = "urn:uuid:${UUID.randomUUID()}"
            val issuerDid = getIssuerDid()
            val now = dateFormat.format(Date())

            val vpJson = JSONObject().apply {
                put("@context", JSONArray(listOf(CONTEXT_VC_V1)))
                put("id", presentationId)
                put("type", JSONArray(listOf("VerifiablePresentation")))
                put("verifiableCredential", JSONArray(listOf(vcJson)))
                put("holder", ownerDid)
                put("proof", JSONObject().apply {
                    put("type", PROOF_TYPE)
                    put("created", now)
                    put("verificationMethod", "$issuerDid#keys-1")
                    put("proofPurpose", "authentication")
                })
            }

            // 3) ECDSA 签名
            val vpString = vpJson.toString()
            val keyAlias = "$KEY_ALIAS_PREFIX$issuerDid"
            if (!keyManager.keyExists(keyAlias)) {
                keyManager.generateKeyPair(keyAlias)
            }
            val signature = keyManager.sign(keyAlias, vpString.toByteArray(Charsets.UTF_8))
            val signatureB64 = Base64.encodeToString(signature, Base64.NO_WRAP)
            vpJson.getJSONObject("proof").put("proofValue", signatureB64)

            // 4) 更新数据库
            val updatedEntity = entity.copy(presentationJson = vpJson.toString())
            credentialDao.updateCredential(updatedEntity)

            val vp = entityToVP(presentationId, entity, vpJson)
            Resource.Success(vp)
        } catch (e: Exception) {
            Resource.Error(message = "创建出示失败: ${e.message}")
        }
    }

    // ========== 凭证出示（ZKP 版） ==========

    override suspend fun createPresentation(
        credentialId: String,
        disclosureFields: List<String>?,
        proof: ZkpProof
    ): Resource<VerifiablePresentation> {
        return try {
            val entity = credentialDao.getCredentialById(credentialId)
                ?: return Resource.Error(message = "凭证不存在: $credentialId")

            if (entity.status != CredentialStatus.ACTIVE) {
                return Resource.Error(message = "凭证状态异常: ${entity.status}")
            }

            val vcJson = JSONObject(entity.credentialJson)
            if (!disclosureFields.isNullOrEmpty()) {
                val claims = vcJson.getJSONObject("credentialSubject").getJSONObject("claims")
                val filtered = JSONObject()
                disclosureFields.forEach { field ->
                    if (claims.has(field)) filtered.put(field, claims.get(field))
                }
                vcJson.getJSONObject("credentialSubject").put("claims", filtered)
                vcJson.put("selectiveDisclosureFields", JSONArray(disclosureFields))
            }

            val presentationId = "urn:uuid:${UUID.randomUUID()}"
            val issuerDid = getIssuerDid()
            val now = dateFormat.format(Date())

            val vpJson = JSONObject().apply {
                put("@context", JSONArray(listOf(CONTEXT_VC_V1)))
                put("id", presentationId)
                put("type", JSONArray(listOf("VerifiablePresentation")))
                put("verifiableCredential", JSONArray(listOf(vcJson)))
                put("holder", entity.ownerDid)
                put("proof", JSONObject().apply {
                    put("type", "Groth16Proof2023")
                    put("created", now)
                    put("verificationMethod", "$issuerDid#zkp-verification-key")
                    put("proofPurpose", "authentication")
                    put("proofValue", Base64.encodeToString(
                        proof.proofBytes ?: ByteArray(0), Base64.NO_WRAP))
                })
            }

            val keyAlias = "$KEY_ALIAS_PREFIX$issuerDid"
            if (!keyManager.keyExists(keyAlias)) {
                keyManager.generateKeyPair(keyAlias)
            }
            val vpString = vpJson.toString()
            val signature = keyManager.sign(keyAlias, vpString.toByteArray(Charsets.UTF_8))
            vpJson.getJSONObject("proof").put("holderSignature",
                Base64.encodeToString(signature, Base64.NO_WRAP))

            val updatedEntity = entity.copy(presentationJson = vpJson.toString())
            credentialDao.updateCredential(updatedEntity)

            val vp = entityToVP(presentationId, entity, vpJson)
            Resource.Success(vp)
        } catch (e: Exception) {
            Resource.Error(message = "创建 ZKP 出示失败: ${e.message}")
        }
    }

    // ========== 凭证验证 ==========

    override suspend fun verifyCredential(credentialJson: String): Resource<VerificationResult> {
        return try {
            val vcJson = JSONObject(credentialJson)
            val proofObj = vcJson.optJSONObject("proof")
                ?: return Resource.Success(VerificationResult(false, listOf("缺少 proof 字段")))

            val proofValue = proofObj.optString("proofValue")
            if (proofValue.isEmpty()) {
                return Resource.Success(VerificationResult(false, listOf("缺少 proofValue")))
            }

            // 验证 ECDSA 签名
            // 1) 获取签发方公钥
            val verificationMethod = proofObj.optString("verificationMethod")
            val issuerDid = verificationMethod.split("#").first()
            val keyAlias = "$KEY_ALIAS_PREFIX$issuerDid"

            if (!keyManager.keyExists(keyAlias)) {
                return Resource.Success(VerificationResult(false,
                    listOf("签发方公钥不存在: $issuerDid")))
            }

            // 2) 重建签名原文（去掉 proofValue 后的 VC JSON）
            val unsignedVc = JSONObject(credentialJson)
            unsignedVc.getJSONObject("proof").remove("proofValue")
            val unsignedBytes = unsignedVc.toString().toByteArray(Charsets.UTF_8)

            // 3) 验证签名
            val signature = Base64.decode(proofValue, Base64.NO_WRAP)
            val publicKey = keyManager.getPublicKey(keyAlias)
            val valid = keyManager.verify(publicKey, unsignedBytes, signature)

            Resource.Success(VerificationResult(valid))
        } catch (e: Exception) {
            Resource.Success(VerificationResult(false, listOf("验证异常: ${e.message}")))
        }
    }

    override suspend fun verifyPresentation(presentationJson: String): Resource<VerificationResult> {
        return try {
            val vpJson = JSONObject(presentationJson)
            val proofObj = vpJson.optJSONObject("proof")
                ?: return Resource.Success(VerificationResult(false, listOf("缺少 proof 字段")))

            val proofValue = proofObj.optString("proofValue")
            val warnings = mutableListOf<String>()

            // 基础 ECDSA 签名验证
            if (proofValue.isNotEmpty()) {
                val verificationMethod = proofObj.optString("verificationMethod")
                val issuerDid = verificationMethod.split("#").first()
                val keyAlias = "$KEY_ALIAS_PREFIX$issuerDid"

                if (keyManager.keyExists(keyAlias)) {
                    val unsignedVp = JSONObject(presentationJson)
                    unsignedVp.getJSONObject("proof").remove("proofValue")
                    if (unsignedVp.getJSONObject("proof").has("holderSignature")) {
                        unsignedVp.getJSONObject("proof").remove("holderSignature")
                    }
                    val unsignedBytes = unsignedVp.toString().toByteArray(Charsets.UTF_8)

                    val signature = Base64.decode(proofValue, Base64.NO_WRAP)
                    val publicKey = keyManager.getPublicKey(keyAlias)
                    val valid = keyManager.verify(publicKey, unsignedBytes, signature)

                    if (!valid) {
                        return Resource.Success(VerificationResult(false,
                            listOf("ECDSA 签名验证失败")))
                    }
                } else {
                    warnings.add("签发方公钥不可用，跳过 ECDSA 验证")
                }
            }

            // ZKP 验证（Groth16Proof2023）
            val proofTypeStr = proofObj.optString("type")
            if (proofTypeStr == "Groth16Proof2023") {
                // ZKP 验证由 ZkpService.verify() 完成，这里只标记 proof 类型
                warnings.add("Groth16 ZKP 验证需通过 ZkpService 完成")
            }

            Resource.Success(VerificationResult(true, warnings = warnings))
        } catch (e: Exception) {
            Resource.Success(VerificationResult(false, listOf("验证异常: ${e.message}")))
        }
    }

    // ========== 凭证列表 ==========

    override suspend fun getCredentialsByOwner(ownerDid: String): Resource<List<VerifiableCredential>> {
        return try {
            val entities = credentialDao.getCredentialsByOwnerOnce(ownerDid)
            val vcs = entities.map { entityToVC(it) }
            Resource.Success(vcs)
        } catch (e: Exception) {
            Resource.Error(message = "获取凭证列表失败: ${e.message}")
        }
    }

    // ========== 凭证撤销 ==========

    override suspend fun revokeCredential(credentialId: String): Resource<Unit> {
        return try {
            val entity = credentialDao.getCredentialById(credentialId)
                ?: return Resource.Error(message = "凭证不存在: $credentialId")

            // 策略检查
            enforceVaultDelete(entity.ownerDid)

            credentialDao.updateStatus(credentialId, CredentialStatus.REVOKED)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(message = "撤销凭证失败: ${e.message}")
        }
    }

    // ========== 二维码生成 ==========

    /**
     * 将凭证 JSON 编码为二维码。
     */
    fun generateQRCode(content: String, size: Int = QR_SIZE): Bitmap? {
        return try {
            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.encodeBitmap(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 为凭证生成二维码（便捷方法）。
     */
    fun generateCredentialQRCode(credential: VerifiableCredential, size: Int = QR_SIZE): Bitmap? {
        return try {
            val vcJson = JSONObject().apply {
                put("@context", JSONArray(credential.context))
                put("id", credential.credentialId)
                put("type", JSONArray(credential.type))
                put("issuer", credential.issuer)
                put("issuanceDate", credential.issuanceDate)
                credential.expirationDate?.let { put("expirationDate", it) }
                put("credentialSubject", JSONObject(credential.credentialSubject))
                put("proof", JSONObject().apply {
                    put("type", credential.proof.type)
                    put("proofValue", credential.proof.proofValue)
                })
            }
            generateQRCode(vcJson.toString(), size)
        } catch (e: Exception) {
            null
        }
    }

    // ========== 辅助方法 ==========

    private suspend fun getIssuerDid(): String {
        return when (val result = didService.getActiveDidDocument()) {
            is Resource.Success -> result.data.did
            is Resource.Error -> throw IllegalStateException("无法获取当前 DID: {result.message}")
            is Resource.Loading -> throw IllegalStateException("DID 服务未就绪")
        }
    }

    private suspend fun enforceVaultWrite(ownerDid: String) {
        when (val result = policyEnforcer.checkVaultWrite(ownerDid)) {
            is PolicyCheckResult.Allowed -> {}
            is PolicyCheckResult.Denied -> throw SecurityException(result.reason)
        }
    }

    private suspend fun enforceVaultDelete(ownerDid: String) {
        when (val result = policyEnforcer.checkVaultDelete(ownerDid)) {
            is PolicyCheckResult.Allowed -> {}
            is PolicyCheckResult.Denied -> throw SecurityException(result.reason)
        }
    }



    private fun entityToVC(entity: CredentialEntity): VerifiableCredential {
        val json = JSONObject(entity.credentialJson)
        val proofJson = json.getJSONObject("proof")
        val subjectJson = json.getJSONObject("credentialSubject")

        val contextList = mutableListOf<String>()
        val ctxArr = json.optJSONArray("@context")
        if (ctxArr != null) {
            for (i in 0 until ctxArr.length()) contextList.add(ctxArr.getString(i))
        }

        val typeList = mutableListOf<String>()
        val typeArr = json.optJSONArray("type")
        if (typeArr != null) {
            for (i in 0 until typeArr.length()) typeList.add(typeArr.getString(i))
        }

        val subjectMap = mutableMapOf<String, Any>()
        subjectJson.keys().forEach { key -> subjectMap[key] = subjectJson.get(key) }

        val fieldsArr = json.optJSONArray("selectiveDisclosureFields")
        val fields = if (fieldsArr != null) {
            (0 until fieldsArr.length()).map { fieldsArr.getString(it) }
        } else null

        return VerifiableCredential(
            credentialId = entity.credentialId,
            context = contextList,
            type = typeList,
            issuer = json.optString("issuer", entity.issuerDid),
            issuanceDate = json.optString("issuanceDate", dateFormat.format(Date(entity.issuanceDate))),
            expirationDate = entity.expirationDate?.let { dateFormat.format(Date(it)) },
            credentialSubject = subjectMap,
            proof = Proof(
                type = proofJson.optString("type"),
                created = proofJson.optString("created"),
                verificationMethod = proofJson.optString("verificationMethod"),
                proofPurpose = proofJson.optString("proofPurpose"),
                proofValue = proofJson.optString("proofValue")
            ),
            selectiveDisclosureFields = fields
        )
    }

    private fun entityToVP(
        presentationId: String,
        entity: CredentialEntity,
        vpJson: JSONObject
    ): VerifiablePresentation {
        val proofJson = vpJson.getJSONObject("proof")
        return VerifiablePresentation(
            presentationId = presentationId,
            context = listOf(CONTEXT_VC_V1),
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(entityToVC(entity)),
            proof = Proof(
                type = proofJson.optString("type"),
                created = proofJson.optString("created"),
                verificationMethod = proofJson.optString("verificationMethod"),
                proofPurpose = proofJson.optString("proofPurpose"),
                proofValue = proofJson.optString("proofValue")
            )
        )
    }
}
