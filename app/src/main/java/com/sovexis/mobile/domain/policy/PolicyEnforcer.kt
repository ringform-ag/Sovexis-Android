package com.sovexis.mobile.domain.policy

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis ç­–ç•¥æ‰§è¡Œå™? * 
 * ã€å¼•ç”¨æ¥æºã€‘åŸºäºŽåºŸæ¡?PolicyEnforcer.kt é€»è¾‘
 * - ç­–ç•¥é…ç½®ç»“æž„ï¼šåºŸæ¡ˆç¬¬ 1-133 è¡? * - æƒé™æ£€æŸ¥å‡½æ•°ï¼šåºŸæ¡ˆç¬?183-217 è¡? * - Markdown å¯¼å…¥å¯¼å‡ºï¼šåºŸæ¡ˆç¬¬ 220-326 è¡? * 
 * ã€è°ƒæ•´è¯´æ˜Žã€? * 1. é€‚é… Hilt ä¾èµ–æ³¨å…¥
 * 2. æ·»åŠ å‰¯è´¦å·ç­–ç•¥ç»‘å®? * 3. å¢žå¼ºç­–ç•¥éªŒè¯é€»è¾‘
 * 
 * @author Sovexis æž¶æž„ç»? * @since 3.0.0
 */
@Singleton
class PolicyEnforcer @Inject constructor(
    private val context: Context
) {

    private val json = Json { encodeDefaults = true }
    private val storage = PolicyStorage(context)

    /**
     * èŽ·å–ç­–ç•¥é…ç½®
     * 
     * @param childDid å‰¯è´¦å?DID
     * @return PolicyConfig? ç­–ç•¥é…ç½®
     */
    suspend fun getPolicy(childDid: String): PolicyConfig? = storage.load(childDid)

    /**
     * ä¿å­˜ç­–ç•¥é…ç½®
     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?PolicyEnforcer.kt ç¬?176-180 è¡?     * 
     * @param policy ç­–ç•¥é…ç½®
     * @return Result<PolicyConfig> ä¿å­˜ç»“æžœï¼ˆè‡ªåŠ¨ä¿®æ­£åŽï¼?     */
    suspend fun savePolicy(policy: PolicyConfig): Result<PolicyConfig> {
        val corrected = PolicyValidator.validateAndCorrect(policy)
        storage.save(corrected)
        return Result.success(corrected)
    }

    /**
     * æ£€æŸ¥æ”¯ä»˜æƒé™?     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?PolicyEnforcer.kt ç¬?183-199 è¡?     * 
     * @param fromDid æ”¯ä»˜æ–?DID
     * @param toDid æŽ¥æ”¶æ–?DID
     * @param amount é‡‘é¢
     * @param asset èµ„äº§ç±»åž‹
     * @param dailyUsed å½“æ—¥å·²ç”¨é‡‘é¢
     * @param totalUsed ç´¯è®¡å·²ç”¨é‡‘é¢
     * @return PolicyCheckResult æ£€æŸ¥ç»“æž?     */
    suspend fun checkPayment(
        fromDid: String,
        toDid: String,
        amount: Double,
        asset: String,
        dailyUsed: Double,
        totalUsed: Double
    ): PolicyCheckResult {
        val policy = getPolicy(fromDid)
            ?: return PolicyCheckResult.Denied("ç­–ç•¥ä¸å­˜åœ?, "policy")

        if (asset !in policy.payment.allowedAssets) {
            return PolicyCheckResult.Denied("èµ„äº§ç±»åž‹æœªæŽˆæ? $asset", "payment.allowedAssets")
        }

        if (amount > policy.payment.perTxLimit) {
            return PolicyCheckResult.Denied("è¶…å‡ºå•ç¬”é™é¢ ${policy.payment.perTxLimit}", "payment.perTxLimit")
        }

        if (dailyUsed + amount > policy.payment.dailyLimit) {
            return PolicyCheckResult.Denied("è¶…å‡ºå•æ—¥é™é¢ ${policy.payment.dailyLimit}", "payment.dailyLimit")
        }

        if (totalUsed + amount > policy.payment.totalLimit) {
            return PolicyCheckResult.Denied("è¶…å‡ºç´¯è®¡é™é¢ ${policy.payment.totalLimit}", "payment.totalLimit")
        }

        return PolicyCheckResult.Allowed
    }

    /**
     * æ£€æŸ¥ä¿é™©ç®±è¯»å–æƒé™
     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?PolicyEnforcer.kt ç¬?201-205 è¡?     */
    suspend fun checkVaultRead(childDid: String): PolicyCheckResult {
        val policy = getPolicy(childDid)
            ?: return PolicyCheckResult.Denied("ç­–ç•¥ä¸å­˜åœ?, "policy")
        return if (policy.vault.allowRead) PolicyCheckResult.Allowed
        else PolicyCheckResult.Denied("ä¿é™©ç®±è¯»å–æƒé™æœªå¼€å?, "vault.allowRead")
    }

    /**
     * æ£€æŸ¥ä¿é™©ç®±å†™å…¥æƒé™
     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?PolicyEnforcer.kt ç¬?207-211 è¡?     */
    suspend fun checkVaultWrite(childDid: String): PolicyCheckResult {
        val policy = getPolicy(childDid)
            ?: return PolicyCheckResult.Denied("ç­–ç•¥ä¸å­˜åœ?, "policy")
        return if (policy.vault.allowWrite) PolicyCheckResult.Allowed
        else PolicyCheckResult.Denied("ä¿é™©ç®±å†™å…¥æƒé™æœªå¼€å?, "vault.allowWrite")
    }

    /**
     * æ£€æŸ¥ä¿é™©ç®±åˆ é™¤æƒé™
     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?PolicyEnforcer.kt ç¬?213-217 è¡?     */
    suspend fun checkVaultDelete(childDid: String): PolicyCheckResult {
        val policy = getPolicy(childDid)
            ?: return PolicyCheckResult.Denied("ç­–ç•¥ä¸å­˜åœ?, "policy")
        return if (policy.vault.allowDelete) PolicyCheckResult.Allowed
        else PolicyCheckResult.Denied("ä¿é™©ç®±åˆ é™¤æƒé™æœªå¼€å?, "vault.allowDelete")
    }

    /**
     * å¯¼å‡ºç­–ç•¥ä¸?Markdown
     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?PolicyEnforcer.kt ç¬?220-256 è¡?     * 
     * @param policy ç­–ç•¥é…ç½®
     * @return String Markdown æ ¼å¼ç­–ç•¥æ–‡æ¡£
     */
    fun exportToMarkdown(policy: PolicyConfig): String {
        return buildString {
            appendLine("# å‰¯è´¦å·æƒé™å£°æ˜?)
            appendLine()
            appendLine("## åŸºæœ¬ä¿¡æ¯")
            appendLine("- ç»‘å®šDID: ${policy.boundChildDid}")
            appendLine("- ç‰ˆæœ¬: ${policy.version}")
            appendLine("- ç­–ç•¥ID: ${policy.policyId}")
            appendLine()
            appendLine("## æ¶ˆæ¯æƒé™")
            appendLine("- å…è®¸å‘é€? ${policy.message.allowSend}")
            appendLine("- å…è®¸æŽ¥æ”¶: ${policy.message.allowReceive}")
            if (policy.message.whitelistDids.isNotEmpty()) {
                appendLine("- ç™½åå•DID:")
                policy.message.whitelistDids.forEach { appendLine("  - $it") }
            }
            appendLine()
            appendLine("## æ”¯ä»˜æƒé™")
            appendLine("- å•ç¬”é™é¢: ${policy.payment.perTxLimit}")
            appendLine("- å•æ—¥é™é¢: ${policy.payment.dailyLimit}")
            appendLine("- ç´¯è®¡é™é¢: ${policy.payment.totalLimit}")
            appendLine("- å…è®¸èµ„äº§: ${policy.payment.allowedAssets.joinToString()}")
            appendLine()
            appendLine("## æ•°æ®ä¿é™©ç®±æƒé™?)
            appendLine("- è¯»å–: ${policy.vault.allowRead}")
            appendLine("- å†™å…¥: ${policy.vault.allowWrite}")
            appendLine("- åˆ é™¤: ${policy.vault.allowDelete}")
            appendLine()
            appendLine("## API æƒé™")
            if (policy.api.allowedEndpoints.isNotEmpty()) {
                appendLine("- å…è®¸ç«¯ç‚¹:")
                policy.api.allowedEndpoints.forEach { appendLine("  - $it") }
            } else {
                appendLine("- å…è®¸ç«¯ç‚¹: (æ— é™åˆ?")
            }
            appendLine()
            appendLine("## ç½‘ç»œæƒé™")
            appendLine("- å…è®¸è”ç½‘: ${policy.network.allowAccess}")
        }
    }

    /**
     * ä»?Markdown å¯¼å…¥ç­–ç•¥
     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?PolicyEnforcer.kt ç¬?258-326 è¡?     * 
     * @param markdown Markdown æ ¼å¼ç­–ç•¥æ–‡æ¡£
     * @param childDid å‰¯è´¦å?DID
     * @return Result<PolicyConfig> å¯¼å…¥ç»“æžœ
     */
    fun importFromMarkdown(markdown: String, childDid: String): Result<PolicyConfig> {
        return try {
            val lines = markdown.lines()
            var messageAllowSend = true
            var messageAllowReceive = true
            val whitelistDids = mutableListOf<String>()
            var perTxLimit = 10.0
            var dailyLimit = 50.0
            var totalLimit = 500.0
            val allowedAssets = mutableListOf("AGT")
            var vaultRead = true
            var vaultWrite = false
            var vaultDelete = false
            val allowedEndpoints = mutableListOf<String>()
            var networkAllow = true

            var section = ""
            for (line in lines) {
                when {
                    line.startsWith("## æ¶ˆæ¯æƒé™") -> section = "message"
                    line.startsWith("## æ”¯ä»˜æƒé™") -> section = "payment"
                    line.startsWith("## æ•°æ®ä¿é™©ç®±æƒé™?) -> section = "vault"
                    line.startsWith("## API æƒé™") -> section = "api"
                    line.startsWith("## ç½‘ç»œæƒé™") -> section = "network"
                    line.startsWith("- å…è®¸å‘é€?") && section == "message" ->
                        messageAllowSend = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: true
                    line.startsWith("- å…è®¸æŽ¥æ”¶:") && section == "message" ->
                        messageAllowReceive = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: true
                    line.startsWith("  - ") && section == "message" && line.contains("did:sovexis") ->
                        whitelistDids.add(line.substringAfter("- ").trim())
                    line.startsWith("- å•ç¬”é™é¢:") && section == "payment" ->
                        perTxLimit = line.substringAfter(":").trim().toDoubleOrNull() ?: 10.0
                    line.startsWith("- å•æ—¥é™é¢:") && section == "payment" ->
                        dailyLimit = line.substringAfter(":").trim().toDoubleOrNull() ?: 50.0
                    line.startsWith("- ç´¯è®¡é™é¢:") && section == "payment" ->
                        totalLimit = line.substringAfter(":").trim().toDoubleOrNull() ?: 500.0
                    line.startsWith("- å…è®¸èµ„äº§:") && section == "payment" -> {
                        val assets = line.substringAfter(":").trim()
                        allowedAssets.clear()
                        allowedAssets.addAll(assets.split(",").map { it.trim() })
                    }
                    line.startsWith("- è¯»å–:") && section == "vault" ->
                        vaultRead = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: true
                    line.startsWith("- å†™å…¥:") && section == "vault" ->
                        vaultWrite = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: false
                    line.startsWith("- åˆ é™¤:") && section == "vault" ->
                        vaultDelete = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: false
                    line.startsWith("  - ") && section == "api" && !line.contains("(æ— é™åˆ?") ->
                        allowedEndpoints.add(line.substringAfter("- ").trim())
                    line.startsWith("- å…è®¸è”ç½‘:") && section == "network" ->
                        networkAllow = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: true
                }
            }

            val policy = PolicyConfig(
                boundChildDid = childDid,
                message = MessagePolicy(messageAllowSend, messageAllowReceive, whitelistDids),
                payment = PaymentPolicy(perTxLimit, dailyLimit, totalLimit, allowedAssets),
                vault = VaultPolicy(vaultRead, vaultWrite, vaultDelete),
                api = ApiPolicy(allowedEndpoints),
                network = NetworkPolicy(networkAllow)
            )
            Result.success(PolicyValidator.validateAndCorrect(policy))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ========== ç­–ç•¥æ•°æ®ç±?==========

@Serializable
data class PolicyConfig(
    val version: String = "1.0",
    val policyId: String = UUID.randomUUID().toString(),
    val boundChildDid: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val message: MessagePolicy = MessagePolicy(),
    val payment: PaymentPolicy = PaymentPolicy(),
    val vault: VaultPolicy = VaultPolicy(),
    val api: ApiPolicy = ApiPolicy(),
    val network: NetworkPolicy = NetworkPolicy()
)

@Serializable
data class MessagePolicy(
    val allowSend: Boolean = true,
    val allowReceive: Boolean = true,
    val whitelistDids: List<String> = emptyList()
)

@Serializable
data class PaymentPolicy(
    val perTxLimit: Double = 10.0,
    val dailyLimit: Double = 50.0,
    val totalLimit: Double = 500.0,
    val allowedAssets: List<String> = listOf("AGT")
)

@Serializable
data class VaultPolicy(
    val allowRead: Boolean = true,
    val allowWrite: Boolean = false,
    val allowDelete: Boolean = false
)

@Serializable
data class ApiPolicy(
    val allowedEndpoints: List<String> = emptyList()
)

@Serializable
data class NetworkPolicy(
    val allowAccess: Boolean = true
)

sealed class PolicyCheckResult {
    object Allowed : PolicyCheckResult()
    data class Denied(val reason: String, val policyField: String) : PolicyCheckResult()
}

// ========== ç­–ç•¥éªŒè¯å™?==========

object PolicyValidator {
    /**
     * éªŒè¯å¹¶ä¿®æ­£ç­–ç•¥é…ç½?     * 
     * ã€å¼•ç”¨æ¥æºã€‘åºŸæ¡?PolicyEnforcer.kt ç¬?84-133 è¡?     */
    fun validateAndCorrect(policy: PolicyConfig): PolicyConfig {
        var corrected = policy

        // è¯»å†™çŸ›ç›¾ï¼šå¦‚æžœå…è®¸å†™ä½†ä¸å…è®¸è¯»ï¼Œè‡ªåŠ¨å¼€å¯è¯»æƒé™
        if (corrected.vault.allowWrite && !corrected.vault.allowRead) {
            corrected = corrected.copy(
                vault = corrected.vault.copy(allowRead = true),
                updatedAt = System.currentTimeMillis()
            )
        }

        // å•ç¬”è¶…æ—¥é™ï¼šå•ç¬”é™é¢ä¸èƒ½è¶…è¿‡æ—¥é™é¢?        if (corrected.payment.perTxLimit > corrected.payment.dailyLimit) {
            corrected = corrected.copy(
                payment = corrected.payment.copy(dailyLimit = corrected.payment.perTxLimit),
                updatedAt = System.currentTimeMillis()
            )
        }

        // API æ— ç½‘ç»œï¼šå¦‚æžœé…ç½®äº?API æƒé™ä½†æ²¡æœ‰ç½‘ç»œæƒé™ï¼Œè‡ªåŠ¨å¼€å¯ç½‘ç»?        if (corrected.api.allowedEndpoints.isNotEmpty() && !corrected.network.allowAccess) {
            corrected = corrected.copy(
                network = corrected.network.copy(allowAccess = true),
                updatedAt = System.currentTimeMillis()
            )
        }

        // é™é¢éžæ­£æ•°å¤„ç?        if (corrected.payment.perTxLimit <= 0) {
            corrected = corrected.copy(
                payment = corrected.payment.copy(perTxLimit = 10.0),
                updatedAt = System.currentTimeMillis()
            )
        }
        if (corrected.payment.dailyLimit <= 0) {
            corrected = corrected.copy(
                payment = corrected.payment.copy(dailyLimit = 50.0),
                updatedAt = System.currentTimeMillis()
            )
        }
        if (corrected.payment.totalLimit <= 0) {
            corrected = corrected.copy(
                payment = corrected.payment.copy(totalLimit = 500.0),
                updatedAt = System.currentTimeMillis()
            )
        }

        return corrected
    }
}

// ========== ç­–ç•¥å­˜å‚¨ ==========

class PolicyStorage(context: Context) {
    private val prefs = context.getSharedPreferences("sovexis_policies", Context.MODE_PRIVATE)

    private fun keyFor(childDid: String) = "policy_$childDid"

    suspend fun save(policy: PolicyConfig) {
        val json = Json.encodeToString(PolicyConfig.serializer(), policy)
        prefs.edit().putString(keyFor(policy.boundChildDid), json).apply()
    }

    suspend fun load(childDid: String): PolicyConfig? {
        val json = prefs.getString(keyFor(childDid), null) ?: return null
        return Json.decodeFromString(PolicyConfig.serializer(), json)
    }

    suspend fun delete(childDid: String) {
        prefs.edit().remove(keyFor(childDid)).apply()
    }
}
