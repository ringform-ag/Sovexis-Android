package com.sovexis.domain.policy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis 策略执行器
 *
 * 【引用来源】基于废案 PolicyEnforcer.kt 逻辑
 * - 策略配置结构：废案第 1-133 行
 * - 权限检查函数：废案第 183-217 行
 * - Markdown 导入导出：废案第 220-326 行
 *
 * 【调整说明】
 * 1. 适配 Hilt 依赖注入
 * 2. 添加副账号策略绑定
 * 3. 增强策略验证逻辑
 *
 * @author Sovexis 架构组
 * @since 3.0.0
 */
@Singleton
class PolicyEnforcer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json { encodeDefaults = true }
    private val storage = PolicyStorage(context)

    /**
     * 获取策略配置
     *
     * @param childDid 副账号 DID
     * @return PolicyConfig? 策略配置
     */
    suspend fun getPolicy(childDid: String): PolicyConfig? = storage.load(childDid)

    /**
     * 保存策略配置
     *
     * 【引用来源】废案 PolicyEnforcer.kt 第 176-180 行
     *
     * @param policy 策略配置
     * @return Result<PolicyConfig> 保存结果（自动修正后）
     */
    suspend fun savePolicy(policy: PolicyConfig): Result<PolicyConfig> {
        val corrected = PolicyValidator.validateAndCorrect(policy)
        storage.save(corrected)
        return Result.success(corrected)
    }

    /**
     * 检查副账号是否被熔断。
     */
    suspend fun checkFrozen(childDid: String): PolicyCheckResult {
        // 从 IdentityManager 获取 isFrozen 状态
        val manager = context.getSharedPreferences("sovexis_frozen", android.content.Context.MODE_PRIVATE)
        val frozen = manager.getBoolean("frozen_$childDid", false)
        return if (frozen) PolicyCheckResult.Denied("副账号已熔断，所有操作被拦截", "isFrozen")
        else PolicyCheckResult.Allowed
    }

    /**
     * 设置副账号熔断状态。
     */
    fun setFrozen(childDid: String, frozen: Boolean) {
        val manager = context.getSharedPreferences("sovexis_frozen", android.content.Context.MODE_PRIVATE)
        manager.edit().putBoolean("frozen_$childDid", frozen).apply()
    }

    /**
     * 检查支付权限
     *
     * 【引用来源】废案 PolicyEnforcer.kt 第 183-199 行
     *
     * @param fromDid 支付方 DID
     * @param toDid 接收方 DID
     * @param amount 金额
     * @param asset 资产类型
     * @param dailyUsed 当日已用金额
     * @param totalUsed 累计已用金额
     * @return PolicyCheckResult 检查结果
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun checkPayment(
        fromDid: String,
        toDid: String,
        amount: Double,
        asset: String,
        dailyUsed: Double,
        totalUsed: Double
    ): PolicyCheckResult {
        val policy = getPolicy(fromDid)
            ?: return PolicyCheckResult.Denied("策略不存在", "policy")

        if (asset !in policy.payment.allowedAssets) {
            return PolicyCheckResult.Denied("资产类型未授权 $asset", "payment.allowedAssets")
        }

        if (amount > policy.payment.perTxLimit) {
            return PolicyCheckResult.Denied("超出单笔限额 ${policy.payment.perTxLimit}", "payment.perTxLimit")
        }

        if (dailyUsed + amount > policy.payment.dailyLimit) {
            return PolicyCheckResult.Denied("超出单日限额 ${policy.payment.dailyLimit}", "payment.dailyLimit")
        }

        if (totalUsed + amount > policy.payment.totalLimit) {
            return PolicyCheckResult.Denied("超出累计限额 ${policy.payment.totalLimit}", "payment.totalLimit")
        }

        return PolicyCheckResult.Allowed
    }

    /**
     * 检查保险箱读取权限
     *
     * 【引用来源】废案 PolicyEnforcer.kt 第 201-205 行
     */
    suspend fun checkVaultRead(childDid: String): PolicyCheckResult {
        val policy = getPolicy(childDid)
            ?: return PolicyCheckResult.Denied("策略不存在", "policy")
        return if (policy.vault.allowRead) PolicyCheckResult.Allowed
        else PolicyCheckResult.Denied("保险箱读取权限未开启", "vault.allowRead")
    }

    /**
     * 检查保险箱写入权限
     *
     * 【引用来源】废案 PolicyEnforcer.kt 第 207-211 行
     */
    suspend fun checkVaultWrite(childDid: String): PolicyCheckResult {
        val policy = getPolicy(childDid)
            ?: return PolicyCheckResult.Denied("策略不存在", "policy")
        return if (policy.vault.allowWrite) PolicyCheckResult.Allowed
        else PolicyCheckResult.Denied("保险箱写入权限未开启", "vault.allowWrite")
    }

    /**
     * 检查保险箱删除权限
     *
     * 【引用来源】废案 PolicyEnforcer.kt 第 213-217 行
     */
    suspend fun checkVaultDelete(childDid: String): PolicyCheckResult {
        val policy = getPolicy(childDid)
            ?: return PolicyCheckResult.Denied("策略不存在", "policy")
        return if (policy.vault.allowDelete) PolicyCheckResult.Allowed
        else PolicyCheckResult.Denied("保险箱删除权限未开启", "vault.allowDelete")
    }

    /**
     * 导出策略为 Markdown
     *
     * 【引用来源】废案 PolicyEnforcer.kt 第 220-256 行
     *
     * @param policy 策略配置
     * @return String Markdown 格式策略文档
     */
    fun exportToMarkdown(policy: PolicyConfig): String {
        return buildString {
            appendLine("# 副账号权限声明")
            appendLine()
            appendLine("## 基本信息")
            appendLine("- 绑定DID: ${policy.boundChildDid}")
            appendLine("- 版本: ${policy.version}")
            appendLine("- 策略ID: ${policy.policyId}")
            appendLine()
            appendLine("## 消息权限")
            appendLine("- 允许发送: ${policy.message.allowSend}")
            appendLine("- 允许接收: ${policy.message.allowReceive}")
            if (policy.message.whitelistDids.isNotEmpty()) {
                appendLine("- 白名单DID:")
                policy.message.whitelistDids.forEach { appendLine("  - $it") }
            }
            appendLine()
            appendLine("## 支付权限")
            appendLine("- 单笔限额: ${policy.payment.perTxLimit}")
            appendLine("- 单日限额: ${policy.payment.dailyLimit}")
            appendLine("- 累计限额: ${policy.payment.totalLimit}")
            appendLine("- 允许资产: ${policy.payment.allowedAssets.joinToString()}")
            appendLine()
            appendLine("## 数据保险箱权限")
            appendLine("- 读取: ${policy.vault.allowRead}")
            appendLine("- 写入: ${policy.vault.allowWrite}")
            appendLine("- 删除: ${policy.vault.allowDelete}")
            appendLine()
            appendLine("## API 权限")
            if (policy.api.allowedEndpoints.isNotEmpty()) {
                appendLine("- 允许端点:")
                policy.api.allowedEndpoints.forEach { appendLine("  - $it") }
            } else {
                appendLine("- 允许端点: (无限制)")
            }
            appendLine()
            appendLine("## 网络权限")
            appendLine("- 允许联网: ${policy.network.allowAccess}")
        }
    }

    /**
     * 从 Markdown 导入策略
     *
     * 【引用来源】废案 PolicyEnforcer.kt 第 258-326 行
     *
     * @param markdown Markdown 格式策略文档
     * @param childDid 副账号 DID
     * @return Result<PolicyConfig> 导入结果
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
                    line.startsWith("## 消息权限") -> section = "message"
                    line.startsWith("## 支付权限") -> section = "payment"
                    line.startsWith("## 数据保险箱权限") -> section = "vault"
                    line.startsWith("## API 权限") -> section = "api"
                    line.startsWith("## 网络权限") -> section = "network"
                    line.startsWith("- 允许发送:") && section == "message" ->
                        messageAllowSend = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: true
                    line.startsWith("- 允许接收:") && section == "message" ->
                        messageAllowReceive = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: true
                    line.startsWith("  - ") && section == "message" && line.contains("did:sovexis") ->
                        whitelistDids.add(line.substringAfter("- ").trim())
                    line.startsWith("- 单笔限额:") && section == "payment" ->
                        perTxLimit = line.substringAfter(":").trim().toDoubleOrNull() ?: 10.0
                    line.startsWith("- 单日限额:") && section == "payment" ->
                        dailyLimit = line.substringAfter(":").trim().toDoubleOrNull() ?: 50.0
                    line.startsWith("- 累计限额:") && section == "payment" ->
                        totalLimit = line.substringAfter(":").trim().toDoubleOrNull() ?: 500.0
                    line.startsWith("- 允许资产:") && section == "payment" -> {
                        val assets = line.substringAfter(":").trim()
                        allowedAssets.clear()
                        allowedAssets.addAll(assets.split(",").map { it.trim() })
                    }
                    line.startsWith("- 读取:") && section == "vault" ->
                        vaultRead = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: true
                    line.startsWith("- 写入:") && section == "vault" ->
                        vaultWrite = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: false
                    line.startsWith("- 删除:") && section == "vault" ->
                        vaultDelete = line.substringAfter(":").trim().toBooleanStrictOrNull() ?: false
                    line.startsWith("  - ") && section == "api" && !line.contains("(无限制)") ->
                        allowedEndpoints.add(line.substringAfter("- ").trim())
                    line.startsWith("- 允许联网:") && section == "network" ->
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

// ========== 策略数据类 ==========

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

// ========== 策略验证器 ==========

object PolicyValidator {
    /**
     * 验证并修正策略配置
     *
     * 【引用来源】废案 PolicyEnforcer.kt 第 84-133 行
     */
    fun validateAndCorrect(policy: PolicyConfig): PolicyConfig {
        var corrected = policy

        // 读写矛盾：如果允许写但不允许读，自动开启读权限
        if (corrected.vault.allowWrite && !corrected.vault.allowRead) {
            corrected = corrected.copy(
                vault = corrected.vault.copy(allowRead = true),
                updatedAt = System.currentTimeMillis()
            )
        }

        // 单笔超日限：单笔限额不能超过日限额
        if (corrected.payment.perTxLimit > corrected.payment.dailyLimit) {
            corrected = corrected.copy(
                payment = corrected.payment.copy(dailyLimit = corrected.payment.perTxLimit),
                updatedAt = System.currentTimeMillis()
            )
        }

        // API 无网络：如果配置了 API 权限但没有网络权限，自动开启网络
        if (corrected.api.allowedEndpoints.isNotEmpty() && !corrected.network.allowAccess) {
            corrected = corrected.copy(
                network = corrected.network.copy(allowAccess = true),
                updatedAt = System.currentTimeMillis()
            )
        }

        // 限额非正数处理
        if (corrected.payment.perTxLimit <= 0) {
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

// ========== 策略存储 ==========

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
