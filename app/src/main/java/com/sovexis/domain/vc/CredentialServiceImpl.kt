package com.sovexis.domain.vc

import android.content.Context
import android.util.Base64
import android.util.Log
import com.sovexis.core.result.Resource
import com.sovexis.domain.crypto.KeyManager
import com.sovexis.domain.zkp.ZkpProof
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CredentialService 实现 — W3C 可验证凭证签发/验证。
 *
 * 【引用来源】合并自废案 com.agora.CredentialManager（269行）
 * 提供通用 VC 签发、签名验证、存储能力。
 * 当前 CredentialIssuer 保持现有签发接口不变，内部可调用本服务。
 */
@Singleton
class CredentialServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager
) : CredentialService {

    companion object {
        private const val TAG = "CredentialService"
        private const val CONTEXT_URI = "https://sovexis.io/credentials/v1"
    }

    // ═══════════════ VC 签发 ═══════════════

    override suspend fun issueCredential(
        ownerDid: String,
        credentialType: String,
        claims: Map<String, Any>
    ): Resource<VerifiableCredential> = withContext(Dispatchers.IO) {
        try {
            val subject = mapOf("id" to ownerDid) + claims
            val unsigned = VerifiableCredential(
                credentialId = "cred:${UUID.randomUUID()}",
                context = listOf(CONTEXT_URI),
                type = listOf("VerifiableCredential", credentialType),
                issuer = ownerDid,
                issuanceDate = Instant.now().toString(),
                expirationDate = null,
                credentialSubject = subject,
                proof = Proof(
                    type = "EcdsaSecp256r1Signature2019",
                    created = Instant.now().toString(),
                    verificationMethod = "$ownerDid#keys-1",
                    proofPurpose = "assertionMethod",
                    proofValue = ""
                )
            )

            // 规范化 JSON 并签名
            val unsignedJson = vcToCanonicalJson(unsigned)
            val hash = MessageDigest.getInstance("SHA-256").digest(unsignedJson.toByteArray(Charsets.UTF_8))
            val sig = keyManager.sign(ownerDid, hash)
            val proofValue = Base64.encodeToString(sig, Base64.NO_WRAP)

            val signed = unsigned.copy(proof = unsigned.proof.copy(proofValue = proofValue))
            Resource.Success(signed)
        } catch (e: Exception) {
            Log.e(TAG, "签发凭证失败", e)
            Resource.Error(code = null, message = e.message ?: "签发凭证失败")
        }
    }

    // ═══════════════ VP 创建 ═══════════════

    override suspend fun createPresentation(
        credentialId: String,
        disclosureFields: List<String>?
    ): Resource<VerifiablePresentation> = withContext(Dispatchers.IO) {
        Resource.Error(code = null, message = "VP 创建需结合 ZKP 证明，使用重载方法",
            throwable = UnsupportedOperationException("VP 创建需结合 ZKP 证明，使用重载方法"))
    }

    override suspend fun createPresentation(
        credentialId: String,
        disclosureFields: List<String>?,
        proof: ZkpProof
    ): Resource<VerifiablePresentation> = withContext(Dispatchers.IO) {
        Resource.Error(code = null, message = "ZKP-based VP creation delegated to CredentialIssuer",
            throwable = UnsupportedOperationException("ZKP-based VP creation delegated to CredentialIssuer"))
    }

    // ═══════════════ 验证 ═══════════════

    override suspend fun verifyCredential(credentialJson: String): Resource<VerificationResult> =
        withContext(Dispatchers.IO) {
            try {
                val obj = org.json.JSONObject(credentialJson)
                val proofObj = obj.optJSONObject("proof")
                    ?: return@withContext Resource.Success(VerificationResult(false, listOf("缺少 proof")))

                val proofValue = proofObj.optString("proofValue", "")
                if (proofValue.isEmpty()) return@withContext Resource.Success(VerificationResult(false, listOf("proofValue 为空")))

                // 构建无签名版 JSON 以计算哈希
                val unsignedObj = org.json.JSONObject(credentialJson)
                unsignedObj.remove("proof")
                val unsignedJson = unsignedObj.toString()
                val hash = MessageDigest.getInstance("SHA-256").digest(unsignedJson.toByteArray(Charsets.UTF_8))

                val issuer = obj.optString("issuer", "")
                val pubKey = resolvePublicKey(issuer)
                    ?: return@withContext Resource.Success(VerificationResult(false, listOf("无法解析签发方公钥: $issuer")))

                val sig = Base64.decode(proofValue, Base64.DEFAULT)
                val valid = keyManager.verify(pubKey, hash, sig)

                Resource.Success(VerificationResult(valid, if (valid) emptyList() else listOf("签名无效")))
            } catch (e: Exception) {
                Resource.Success(VerificationResult(false, listOf("验证异常: ${e.message}")))
            }
        }

    override suspend fun verifyPresentation(presentationJson: String): Resource<VerificationResult> =
        withContext(Dispatchers.IO) {
            // VP 验证委托 CredentialIssuer
            Resource.Success(VerificationResult(false, listOf("VP 验证委托至 CredentialIssuer")))
        }

    // ═══════════════ 凭证管理 ═══════════════

    override suspend fun getCredentialsByOwner(ownerDid: String): Resource<List<VerifiableCredential>> =
        withContext(Dispatchers.IO) {
            try {
                val allJson = loadStoredCredentialsJson()
                val result = allJson
                    .filter { it.contains(ownerDid) }
                    .mapNotNull { parseCredentialJson(it) }
                Resource.Success(result)
            } catch (e: Exception) { Resource.Error(code = null, message = e.message ?: "获取凭证列表失败") }
        }

    override suspend fun revokeCredential(credentialId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val all = loadStoredCredentialsJson().toMutableList()
                all.removeAll { it.contains(credentialId) }
                saveStoredCredentialsJson(all)
                Resource.Success(Unit)
            } catch (e: Exception) { Resource.Error(code = null, message = e.message ?: "撤销凭证失败") }
        }

    // ═══════════════ 存储辅助 ═══════════════

    fun storeCredential(vc: VerifiableCredential) {
        val json = vcToCanonicalJson(vc)
        val all = loadStoredCredentialsJson().toMutableList()
        all.removeAll { it.contains(vc.credentialId) }
        all.add(json)
        saveStoredCredentialsJson(all)
    }

    // ═══════════════ 内部 ═══════════════

    private fun vcToCanonicalJson(vc: VerifiableCredential): String {
        val obj = org.json.JSONObject()
        obj.put("@context", org.json.JSONArray(vc.context))
        obj.put("id", vc.credentialId)
        obj.put("type", org.json.JSONArray(vc.type))
        obj.put("issuer", vc.issuer)
        obj.put("issuanceDate", vc.issuanceDate)
        vc.expirationDate?.let { obj.put("expirationDate", it) }
        obj.put("credentialSubject", org.json.JSONObject(vc.credentialSubject))
        obj.put("proof", org.json.JSONObject().apply {
            put("type", vc.proof.type)
            put("created", vc.proof.created)
            put("verificationMethod", vc.proof.verificationMethod)
            put("proofPurpose", vc.proof.proofPurpose)
            put("proofValue", vc.proof.proofValue)
        })
        return obj.toString()
    }

    /** 签发方公钥解析 — 从本地存储的 DID 文档中获取 */
    private suspend fun resolvePublicKey(issuerDid: String): PublicKey? {
        val all = context.getSharedPreferences("sovexis_did_identities", Context.MODE_PRIVATE)
        val json = all.getString("all_dids", null) ?: return null
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val doc = arr.getJSONObject(i)
                if (doc.getString("did") == issuerDid) {
                    val pem = doc.getString("publicKeyPem")
                    val content = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "").replace("\n", "")
                    val decoded = Base64.decode(content, Base64.DEFAULT)
                    return java.security.KeyFactory.getInstance("EC")
                        .generatePublic(java.security.spec.X509EncodedKeySpec(decoded))
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun parseCredentialJson(json: String): VerifiableCredential? {
        return try {
            val obj = org.json.JSONObject(json)
            val subjectObj = obj.getJSONObject("credentialSubject")
            val subject = mutableMapOf<String, Any>()
            subjectObj.keys().forEach { subject[it] = subjectObj.get(it) }
            val proofObj = obj.getJSONObject("proof")
            VerifiableCredential(
                credentialId = obj.getString("id"),
                context = jsonArrayToList(obj.getJSONArray("@context")),
                type = jsonArrayToList(obj.getJSONArray("type")),
                issuer = obj.getString("issuer"),
                issuanceDate = obj.getString("issuanceDate"),
                expirationDate = obj.optString("expirationDate", null),
                credentialSubject = subject,
                proof = Proof(proofObj.getString("type"), proofObj.getString("created"),
                    proofObj.getString("verificationMethod"), proofObj.getString("proofPurpose"),
                    proofObj.getString("proofValue"))
            )
        } catch (_: Exception) { null }
    }

    private fun jsonArrayToList(arr: org.json.JSONArray): List<String> =
        (0 until arr.length()).map { arr.getString(it) }

    private fun loadStoredCredentialsJson(): List<String> {
        val prefs = context.getSharedPreferences("sovexis_vc_store", Context.MODE_PRIVATE)
        val json = prefs.getString("stored_vcs", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveStoredCredentialsJson(list: List<String>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it) }
        context.getSharedPreferences("sovexis_vc_store", Context.MODE_PRIVATE)
            .edit().putString("stored_vcs", arr.toString()).apply()
    }

    // ═══════════════ 工具 ═══════════════

    /** 当前 ISO 时间戳 */
    fun nowIso(): String = Instant.now().toString()

    /** 生成 QR 码占位实现 — 后续接入 ZXing 库 */
    fun generateQRCode(json: String): android.graphics.Bitmap? = null
}
