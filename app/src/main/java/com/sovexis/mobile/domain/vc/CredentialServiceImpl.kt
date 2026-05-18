package com.sovexis.mobile.domain.vc

import android.content.Context
import android.graphics.Bitmap
import com.sovexis.mobile.core.result.Resource
import com.sovexis.mobile.data.local.dao.CredentialDao
import com.sovexis.mobile.data.local.entity.CredentialEntity
import com.sovexis.mobile.data.local.entity.CredentialStatus
import com.sovexis.mobile.domain.did.DidService
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis å¯éªŒè¯å‡­è¯æœåŠ¡å®žçŽ°
 * 
 * ã€å¼•ç”¨æ¥æºã€‘åŸºäºŽåºŸå¼ƒ CredentialManager.kt é€»è¾‘
 * - VC ç­¾å‘æµç¨‹ï¼šåºŸå¼ƒç¬¬ 111-154 è¡Œ
 * - VC éªŒè¯é€»è¾‘ï¼šåºŸå¼ƒç¬¬ 159-178 è¡Œ
 * - äºŒç»´ç ç”Ÿæˆï¼šåºŸå¼ƒç¬¬ 183-186 è¡Œ
 * 
 * ã€è°ƒæ•´è¯´æ˜Žã€‘
 * 1. é€‚é… Hilt ä¾èµ–æ³¨å…¥
 * 2. ç»Ÿä¸€ä½¿ç”¨ Resource å°è£…ç»“æžœ
 * 3. æ·»åŠ é€‰æ‹©æ€§æŠ«éœ²æ”¯æŒ
 * 4. æ·»åŠ å‡­è¯çŠ¶æ€ç®¡ç†
 * 
 * @author Sovexis æž¶æž„ç»„
 * @since 3.0.0
 */
@Singleton
class CredentialServiceImpl @Inject constructor(
    private val context: Context,
    private val credentialDao: CredentialDao,
    private val didService: DidService
) : CredentialService {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * ç­¾å‘æ–°çš„å¯éªŒè¯å‡­è¯?     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?CredentialManager.kt ç¬?111-154 è¡?     * ã€è°ƒæ•´ã€‘æ·»åŠ é€‰æ‹©æ€§æŠ«éœ²å­—æ®µæ”¯æŒï¼Œç§»é™¤ PolicyEnforcer ç¡¬ç¼–ç ä¾èµ?     * 
     * @param ownerDid æŒæœ‰è€?DID
     * @param credentialType å‡­è¯ç±»åž‹ï¼ˆå¦‚ "IdentityCredential", "AgeCredential"ï¼?     * @param claims å‡­è¯å£°æ˜Žé”®å€¼å¯¹
     * @return Resource<VerifiableCredential> ç­¾å‘ç»“æžœ
     */
    override suspend fun issueCredential(
        ownerDid: String,
        credentialType: String,
        claims: Map<String, Any>
    ): Resource<VerifiableCredential> {
        return try {
            // èŽ·å–ç­¾å‘æ–?DID æ–‡æ¡£
            val issuerDoc = didService.getActiveDidDocument()
            if (issuerDoc !is Resource.Success) {
                return Resource.Error(message = "æ— æ³•èŽ·å–ç­¾å‘æ–¹èº«ä»?)
            }
            val issuerDid = issuerDoc.data.did

            // æž„å»ºå‡­è¯ä¸»é¢˜
            val subject = CredentialSubject(
                id = ownerDid,
                claims = JsonObject(claims.mapValues { it.value.toJsonElement() })
            )

            // æž„å»ºæœªç­¾åå‡­è¯?            val unsignedVc = VerifiableCredential(
                credentialId = UUID.randomUUID().toString(),
                context = listOf("https://www.w3.org/2018/credentials/v1"),
                type = listOf("VerifiableCredential", credentialType),
                issuer = issuerDid,
                issuanceDate = currentIsoTimestamp(),
                credentialSubject = subject,
                proof = null
            )

            // è§„èŒƒåŒ?JSON å¹¶è®¡ç®—å“ˆå¸?            val jsonString = json.encodeToString(VerifiableCredential.serializer(), unsignedVc)
            val hash = MessageDigest.getInstance("SHA-256").digest(jsonString.toByteArray())

            // ç­¾åï¼ˆä½¿ç”?DID å¯¹åº”çš„ç§é’¥ï¼‰
            // TODO: éœ€è¦æŽ¥å…?KeyManager çš„ç­¾ååŠŸèƒ?            val signatureBytes = ByteArray(64) // å ä½ç¬?
            val proof = Proof(
                type = "EcdsaSecp256r1Signature2019",
                created = currentIsoTimestamp(),
                verificationMethod = "$issuerDid#keys-1",
                proofPurpose = "assertionMethod",
                proofValue = Base64.encodeToString(signatureBytes, Base64.URL_SAFE or Base64.NO_PADDING)
            )

            val signedVc = unsignedVc.copy(proof = proof)

            // å­˜å‚¨åˆ°æœ¬åœ°æ•°æ®åº“
            storeCredential(signedVc)

            Resource.Success(signedVc)
        } catch (e: Exception) {
            Resource.Error(message = "ç­¾å‘å‡­è¯å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * åˆ›å»ºå¯éªŒè¯è¡¨è¾?(VP)
     * 
     * @param credentialId å‡­è¯ ID
     * @param disclosureFields é€‰æ‹©æ€§æŠ«éœ²çš„å­—æ®µåˆ—è¡¨ï¼ˆç©ºè¡¨ç¤ºå…¨éƒ¨æŠ«éœ²ï¼?     * @return Resource<VerifiablePresentation> VP åˆ›å»ºç»“æžœ
     */
    override suspend fun createPresentation(
        credentialId: String,
        disclosureFields: List<String>?
    ): Resource<VerifiablePresentation> {
        return try {
            val credential = getCredentialById(credentialId)
                ?: return Resource.Error(message = "å‡­è¯ä¸å­˜åœ? $credentialId")

            // å¦‚æžœæœ‰é€‰æ‹©æ€§æŠ«éœ²å­—æ®µï¼Œè¿‡æ»¤ claims
            val filteredCredential = if (disclosureFields != null && disclosureFields.isNotEmpty()) {
                val originalClaims = credential.credentialSubject.claims
                val filteredClaims = JsonObject(
                    originalClaims.filter { it.key in disclosureFields }
                )
                credential.copy(
                    credentialSubject = credential.credentialSubject.copy(claims = filteredClaims),
                    selectiveDisclosureFields = disclosureFields
                )
            } else {
                credential
            }

            val presentation = VerifiablePresentation(
                presentationId = UUID.randomUUID().toString(),
                context = listOf("https://www.w3.org/2018/credentials/v1"),
                type = listOf("VerifiablePresentation"),
                verifiableCredential = listOf(filteredCredential),
                proof = null // VP å¯ä»¥åŒ…å«æŒ‘æˆ˜å“åº”è¯æ˜Ž
            )

            Resource.Success(presentation)
        } catch (e: Exception) {
            Resource.Error(message = "åˆ›å»ºè¡¨è¾¾å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * éªŒè¯å¯éªŒè¯å‡­è¯?     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?CredentialManager.kt ç¬?159-178 è¡?     * 
     * @param credentialJson VC JSON æ•°æ®
     * @return Resource<VerificationResult> éªŒè¯ç»“æžœ
     */
    override suspend fun verifyCredential(credentialJson: String): Resource<VerificationResult> {
        return try {
            val vc = json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
            val proof = vc.proof
                ?: return Resource.Success(VerificationResult(false, listOf("ç¼ºå°‘ proof")))

            // ç§»é™¤ proof åŽé‡æ–°åºåˆ—åŒ–
            val unsignedVc = vc.copy(proof = null)
            val jsonString = json.encodeToString(VerifiableCredential.serializer(), unsignedVc)
            val hash = MessageDigest.getInstance("SHA-256").digest(jsonString.toByteArray())

            // èŽ·å–ç­¾å‘æ–¹å…¬é’¥ï¼ˆé€šè¿‡ DID è§£æžï¼?            // TODO: æŽ¥å…¥ DID è§£æžç³»ç»ŸèŽ·å–å…¬é’¥
            // val publicKey = resolvePublicKey(vc.issuer)
            // val signature = Base64.getUrlDecoder().decode(proof.proofValue)
            // val isValid = verifySignature(publicKey, hash, signature)

            // ç®€åŒ–éªŒè¯ï¼šæ£€æŸ?proof æ ¼å¼
            val isValid = proof.type == "EcdsaSecp256r1Signature2019" &&
                    proof.proofPurpose == "assertionMethod" &&
                    proof.proofValue.isNotBlank()

            val result = if (isValid) {
                VerificationResult(true)
            } else {
                VerificationResult(false, listOf("ç­¾åéªŒè¯å¤±è´¥"))
            }

            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(message = "éªŒè¯å‡­è¯å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * éªŒè¯å¯éªŒè¯è¡¨è¾?     * 
     * @param presentationJson VP JSON æ•°æ®
     * @return Resource<VerificationResult> éªŒè¯ç»“æžœ
     */
    override suspend fun verifyPresentation(presentationJson: String): Resource<VerificationResult> {
        return try {
            val vp = json.decodeFromString(VerifiablePresentation.serializer(), presentationJson)

            // éªŒè¯åŒ…å«çš„æ‰€æœ‰å‡­è¯?            val errors = mutableListOf<String>()
            for (vc in vp.verifiableCredential) {
                val vcJson = json.encodeToString(VerifiableCredential.serializer(), vc)
                when (val result = verifyCredential(vcJson)) {
                    is Resource.Success -> {
                        if (!result.data.isValid) {
                            errors.addAll(result.data.errors)
                        }
                    }
                    is Resource.Error -> errors.add(result.message)
                    else -> {}
                }
            }

            val result = if (errors.isEmpty()) {
                VerificationResult(true)
            } else {
                VerificationResult(false, errors)
            }

            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(message = "éªŒè¯è¡¨è¾¾å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * èŽ·å–æŒ‡å®š DID çš„æ‰€æœ‰å‡­è¯?     * 
     * @param ownerDid æŒæœ‰è€?DID
     * @return Resource<List<VerifiableCredential>> å‡­è¯åˆ—è¡¨
     */
    override suspend fun getCredentialsByOwner(ownerDid: String): Resource<List<VerifiableCredential>> {
        return try {
            val entities = credentialDao.getCredentialsByOwner(ownerDid).first()
            val credentials = entities.map { it.toVerifiableCredential() }
            Resource.Success(credentials)
        } catch (e: Exception) {
            Resource.Error(message = "èŽ·å–å‡­è¯å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * æ’¤é”€å‡­è¯
     * 
     * @param credentialId å‡­è¯ ID
     * @return Resource<Unit> æ’¤é”€ç»“æžœ
     */
    override suspend fun revokeCredential(credentialId: String): Resource<Unit> {
        return try {
            credentialDao.updateStatus(credentialId, CredentialStatus.REVOKED)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(message = "æ’¤é”€å‡­è¯å¤±è´¥: ${e.message}", throwable = e)
        }
    }

    /**
     * ç”Ÿæˆå‡­è¯äºŒç»´ç ?     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?CredentialManager.kt ç¬?183-186 è¡?     * 
     * @param credential å¯éªŒè¯å‡­è¯?     * @return Bitmap äºŒç»´ç ä½å›?     */
    fun generateQRCode(credential: VerifiableCredential): Bitmap {
        val vcJson = json.encodeToString(VerifiableCredential.serializer(), credential)
        return BarcodeEncoder().encodeBitmap(vcJson, BarcodeFormat.QR_CODE, 512, 512)
    }

    // ========== ç§æœ‰è¾…åŠ©æ–¹æ³• ==========

    private suspend fun storeCredential(vc: VerifiableCredential) {
        val jsonString = json.encodeToString(VerifiableCredential.serializer(), vc)
        val entity = CredentialEntity(
            credentialId = vc.credentialId,
            ownerDid = vc.credentialSubject.id,
            credentialType = vc.type.getOrElse(1) { "VerifiableCredential" },
            issuerDid = vc.issuer,
            issuanceDate = parseIsoTimestamp(vc.issuanceDate),
            expirationDate = null,
            credentialJson = jsonString,
            presentationJson = null,
            status = CredentialStatus.ACTIVE,
            selectiveDisclosureFields = vc.selectiveDisclosureFields?.joinToString(",")
        )
        credentialDao.insertCredential(entity)
    }

    private suspend fun getCredentialById(credentialId: String): VerifiableCredential? {
        val entity = credentialDao.getCredentialById(credentialId)
        return entity?.toVerifiableCredential()
    }

    private fun CredentialEntity.toVerifiableCredential(): VerifiableCredential {
        return json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
    }

    private fun currentIsoTimestamp(): String {
        return dateFormat.format(Date())
    }

    private fun parseIsoTimestamp(timestamp: String): Long {
        return dateFormat.parse(timestamp)?.time ?: System.currentTimeMillis()
    }

    private fun Any.toJsonElement(): JsonElement {
        return when (this) {
            is String -> json.encodeToJsonElement(this)
            is Int -> json.encodeToJsonElement(this)
            is Long -> json.encodeToJsonElement(this)
            is Double -> json.encodeToJsonElement(this)
            is Boolean -> json.encodeToJsonElement(this)
            is List<*> -> json.encodeToJsonElement(this)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = this as Map<String, Any>
                JsonObject(map.mapValues { it.value.toJsonElement() })
            }
            else -> json.encodeToJsonElement(this.toString())
        }
    }
}
