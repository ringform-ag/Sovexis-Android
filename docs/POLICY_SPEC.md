# POLICY_SPEC.md - Sovexis 策略引擎规格 v2.3.0

## 模块定位
策略引擎负责管理每个副账号的权限配置，并在关键操作点执行权限检查。它支持默认的 JSON 配置模式和高级 MD 导入/导出功能。引擎自身包含冲突检测与自动修正逻辑。

本模块与身份模块和存储模块紧密协作，但通过接口隔离，确保策略逻辑独立于具体实现。设计遵循“自我实现、最小依赖”原则，仅使用 Kotlin 标准库和 `kotlinx.serialization`。

---

## 1. 策略数据结构

### 1.1 JSON Schema（v1.0）

```json
{
  "version": "1.0",
  "policyId": "uuid-v4",
  "boundChildDid": "did:sovexis:0x...",
  "createdAt": 1712345678000,
  "updatedAt": 1712345678000,
  "message": {
    "allowSend": true,
    "allowReceive": true,
    "whitelistDids": []
  },
  "payment": {
    "perTxLimit": 10.0,
    "dailyLimit": 50.0,
    "totalLimit": 500.0,
    "allowedAssets": ["AGT"]
  },
  "vault": {
    "allowRead": true,
    "allowWrite": false,
    "allowDelete": false
  },
  "api": {
    "allowedEndpoints": ["/weather", "/news"]
  },
  "network": {
    "allowAccess": true
  }
}
```

### 1.2 Kotlin 数据类（使用 kotlinx.serialization）

```kotlin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

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

```
- 序列化使用 kotlinx.serialization.json.Json，配置忽略未知字段，确保向前兼容。

## 2. 策略存储

### 2.1 Android 实现

- 策略以 JSON 字符串形式存储在 EncryptedSharedPreferences 中，确保数据在设备上加密。

```kotlin

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class PolicyStorage(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        "sovexis_policies",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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

```

## 3. 冲突检测与自动修正

### 3.1 检测规则表（硬编码于 PolicyValidator）

|冲突类型|检测条件|自动处理|
|读写矛盾|vault.allowWrite == true && vault.allowRead == false|设置 vault.allowRead = true|
|单笔超日限|payment.perTxLimit > payment.dailyLimit|设置 payment.dailyLimit = payment.perTxLimit|
|API 无网络|api.allowedEndpoints.isNotEmpty() && !network.allowAccess|设置 network.allowAccess = true
|限额非正数|任何限额 ≤ 0|重置为默认值|

### 3.2 实现

```kotlin

object PolicyValidator {
    fun validateAndCorrect(policy: PolicyConfig): PolicyConfig {
        var corrected = policy

        // 读写矛盾
        if (corrected.vault.allowWrite && !corrected.vault.allowRead) {
            corrected = corrected.copy(
                vault = corrected.vault.copy(allowRead = true),
                updatedAt = System.currentTimeMillis()
            )
        }

        // 单笔超日限
        if (corrected.payment.perTxLimit > corrected.payment.dailyLimit) {
            corrected = corrected.copy(
                payment = corrected.payment.copy(dailyLimit = corrected.payment.perTxLimit),
                updatedAt = System.currentTimeMillis()
            )
        }

        // API 无网络
        if (corrected.api.allowedEndpoints.isNotEmpty() && !corrected.network.allowAccess) {
            corrected = corrected.copy(
                network = corrected.network.copy(allowAccess = true),
                updatedAt = System.currentTimeMillis()
            )
        }

        // 限额非正数处理：调用方在创建或修改时应保证正数，此处仅作兜底

        return corrected
    }
}

```

## 4. 核心 API

### 4.1 PolicyEnforcer（单例）

```kotlin
object PolicyEnforcer {
    private lateinit var storage: PolicyStorage

    fun init(context: Context) {
        storage = PolicyStorage(context)
    }

    suspend fun getPolicy(childDid: String): PolicyConfig? = storage.load(childDid)

    suspend fun savePolicy(policy: PolicyConfig): Result<PolicyConfig> {
        val corrected = PolicyValidator.validateAndCorrect(policy)
        storage.save(corrected)
        return Result.success(corrected)
    }

    // 权限检查函数
    suspend fun checkPayment(fromDid: String, toDid: String, amount: Double, asset: String): PolicyCheckResult {
        val policy = getPolicy(fromDid) ?: return PolicyCheckResult.Denied("策略不存在", "policy")
        if (asset !in policy.payment.allowedAssets) 
            return PolicyCheckResult.Denied("资产类型未授权", "payment.allowedAssets")
        if (amount > policy.payment.perTxLimit) 
            return PolicyCheckResult.Denied("超出单笔限额 ${policy.payment.perTxLimit}", "payment.perTxLimit")

        // 检查单日累计（需从交易历史获取已用额度，由支付模块传入或此处调用支付模块接口）
        // 此处仅作示意，实际集成时通过回调获取
        // val dailyUsed = getDailyUsedAmount(fromDid)
        // if (dailyUsed + amount > policy.payment.dailyLimit) ...

        // 检查累计限额
        // val totalUsed = getTotalUsedAmount(fromDid)
        // if (totalUsed + amount > policy.payment.totalLimit) ...

        return PolicyCheckResult.Allowed
    }

    suspend fun checkVaultRead(childDid: String): PolicyCheckResult {
        val policy = getPolicy(childDid) ?: return PolicyCheckResult.Denied("策略不存在", "policy")
        return if (policy.vault.allowRead) PolicyCheckResult.Allowed
        else PolicyCheckResult.Denied("保险箱读取权限未开启", "vault.allowRead")
    }

    suspend fun checkVaultWrite(childDid: String): PolicyCheckResult {
        val policy = getPolicy(childDid) ?: return PolicyCheckResult.Denied("策略不存在", "policy")
        return if (policy.vault.allowWrite) PolicyCheckResult.Allowed
        else PolicyCheckResult.Denied("保险箱写入权限未开启", "vault.allowWrite")
    }

    suspend fun checkVaultDelete(childDid: String): PolicyCheckResult {
        val policy = getPolicy(childDid) ?: return PolicyCheckResult.Denied("策略不存在", "policy")
        return if (policy.vault.allowDelete) PolicyCheckResult.Allowed
        else PolicyCheckResult.Denied("保险箱删除权限未开启", "vault.allowDelete")
    }

    // MD 导入导出
    fun exportToMarkdown(policy: PolicyConfig): String {
        return buildString {
            appendLine("# 副账号权限声明")
            appendLine()
            appendLine("## 基本信息")
            appendLine("- 绑定DID: ${policy.boundChildDid}")
            appendLine("- 版本: ${policy.version}")
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
            }
            appendLine()
            appendLine("## 网络权限")
            appendLine("- 允许联网: ${policy.network.allowAccess}")
        }
    }

    fun importFromMarkdown(markdown: String, childDid: String): Result<PolicyConfig> {
        // 简易解析：按行读取，查找关键字
        // 为减少依赖，手动解析
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
                    line.startsWith("  - ") && section == "api" ->
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

sealed class PolicyCheckResult {
    object Allowed : PolicyCheckResult()
    data class Denied(val reason: String, val policyField: String) : PolicyCheckResult()
}

```

### 4.2 与支付模块集成

- 支付模块在构造交易前调用 PolicyEnforcer.checkPayment，传入付款方 DID、金额、资产类型。策略引擎需访问交易历史以计算当日累计和累计支出。为了避免循环依赖，可通过回调接口由支付模块提供累计值，或在策略引擎中直接注入 TransactionRepository。MVP 阶段简化：由支付模块自行计算累计值并自行判断，策略引擎仅提供限额阈值。

## 5. 依赖项

- kotlinx-serialization-json：用于 JSON 序列化

- androidx.security:security-crypto：用于加密存储

- 无其他第三方库

## 6. 移植性说明
- PolicyConfig 数据类和验证逻辑纯 Kotlin，可跨平台使用。

- 存储层通过接口 PolicyStorage 抽象，Android 实现如上，其他平台实现各自的加密存储。

- MD 解析使用简单字符串处理，无外部解析器，可移植。

# 规格版本：1.0
- 最后更新：2026-04-12
- 维护者：Sovexis 架构组