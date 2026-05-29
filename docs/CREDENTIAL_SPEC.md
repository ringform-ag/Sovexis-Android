# CREDENTIAL_SPEC.md - Sovexis 可验证凭证模块规格 v2.3.0

## 模块定位
可验证凭证模块实现 W3C Verifiable Credential 的轻量级版本，用于签发、持有和验证数字凭证。在 Sovexis 生态中，凭证可用于授权第三方服务、证明身份属性、协议认同等。

MVP 阶段实现基本的签发与验证功能，支持二维码分享。

---

## 1. 凭证数据模型（简化 VC）

### 1.1 JSON 结构

```json
{
  "@context": ["https://www.w3.org/2018/credentials/v1"],
  "id": "urn:uuid:...",
  "type": ["VerifiableCredential"],
  "issuer": "did:sovexis:0x...",
  "issuanceDate": "2026-04-12T10:00:00Z",
  "credentialSubject": {
    "id": "did:sovexis:0x...",
    "claim": { ... }
  },
  "proof": {
    "type": "EcdsaSecp256r1Signature2019",
    "created": "2026-04-12T10:00:00Z",
    "verificationMethod": "did:sovexis:0x...#keys-1",
    "proofPurpose": "assertionMethod",
    "proofValue": "base64url..."
  }
}

```

### 1.2 Kotlin 数据类

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class VerifiableCredential(
    @SerialName("@context")
    val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    val id: String = UUID.randomUUID().toString(),
    val type: List<String> = listOf("VerifiableCredential"),
    val issuer: String,
    val issuanceDate: String,
    val credentialSubject: CredentialSubject,
    val proof: Proof? = null
)

@Serializable
data class CredentialSubject(
    val id: String,
    val claim: JsonObject   // 任意 JSON 对象
)

@Serializable
data class Proof(
    val type: String = "EcdsaSecp256r1Signature2019",
    val created: String,
    val verificationMethod: String,
    val proofPurpose: String = "assertionMethod",
    val proofValue: String
)

```

- 日期格式使用 ISO 8601：SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

## 2. 签名与验证

### 2.1 签发流程

```kotlin
object CredentialManager {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun issueCredential(
        issuerDid: String,
        subjectDid: String,
        claims: Map<String, Any>,
        identityManager: IdentityManager
    ): Result<VerifiableCredential> {
        // 策略检查（复用保险箱写入权限）
        if (PolicyEnforcer.checkVaultWrite(issuerDid) !is PolicyCheckResult.Allowed)
            return Result.failure(SecurityException("无签发权限"))

        val subject = CredentialSubject(
            id = subjectDid,
            claim = JsonObject(claims.mapValues { it.value.toJsonElement() })
        )
        val unsignedVc = VerifiableCredential(
            issuer = issuerDid,
            issuanceDate = currentIsoTimestamp(),
            credentialSubject = subject
        )

        // 规范化 JSON
        val jsonString = json.encodeToString(VerifiableCredential.serializer(), unsignedVc)
        val hash = MessageDigest.getInstance("SHA-256").digest(jsonString.toByteArray())

        // 签名
        val signature = identityManager.signWithChildIdentity(issuerDid, hash).getOrThrow()

        val proof = Proof(
            created = currentIsoTimestamp(),
            verificationMethod = "$issuerDid#keys-1",
            proofValue = Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        )
        return Result.success(unsignedVc.copy(proof = proof))
    }

    fun verifyCredential(vcJson: String, publicKeyResolver: (String) -> PublicKey?): VerificationResult {
        return try {
            val vc = json.decodeFromString(VerifiableCredential.serializer(), vcJson)
            val proof = vc.proof ?: return VerificationResult.Failure("缺少 proof")
            val unsignedVc = vc.copy(proof = null)
            val jsonString = json.encodeToString(VerifiableCredential.serializer(), unsignedVc)
            val hash = MessageDigest.getInstance("SHA-256").digest(jsonString.toByteArray())

            val publicKey = publicKeyResolver(vc.issuer) ?: return VerificationResult.Failure("无法获取签发方公钥")
            val signature = Base64.getUrlDecoder().decode(proof.proofValue)
            val isValid = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(hash)
            }.verify(signature)

            if (isValid) VerificationResult.Success else VerificationResult.Failure("签名无效")
        } catch (e: Exception) {
            VerificationResult.Failure(e.message ?: "验证异常")
        }
    }
}

sealed class VerificationResult {
    object Success : VerificationResult()
    data class Failure(val reason: String) : VerificationResult()
}

```

### 2.2 公钥解析

- publicKeyResolver 由身份模块提供：根据 DID 从本地数据库或网络解析公钥。MVP 阶段仅从本地 IdentityManager.getChildIdentity(did) 获取。

## 3. 二维码支持

### 3.1 生成二维码

- 使用 ZXing Android Embedded：

```kotlin
import com.journeyapps.barcodescanner.BarcodeEncoder

fun generateQRCode(vc: VerifiableCredential): Bitmap {
    val json = Json.encodeToString(vc)
    return BarcodeEncoder().encodeBitmap(json, BarcodeFormat.QR_CODE, 512, 512)
}

```

### 3.2 扫描解析

- 使用 ScanIntentIntegrator 或 CameraX + ML Kit 实现，扫描结果字符串传入 verifyCredential。

## 4. 凭证模板（MVP）

- 内置三个模板，用于快速填充 claims：

```kotlin
enum class CredentialTemplate(val displayName: String, val claims: Map<String, Any>) {
    AGE_OVER_18("年龄超过18岁", mapOf("ageOver" to 18)),
    EARLY_SUPPORTER("早期支持者", mapOf("role" to "early_supporter")),
    DEVELOPER("开发者认证", mapOf("certification" to "developer"))
}

```

## 5. 存储

- 已签发的凭证可存储于本地 Room 数据库，用于“我的凭证”列表展示。

```kotlin
@Entity
data class StoredCredential(
    @PrimaryKey val id: String,
    val issuerDid: String,
    val subjectDid: String,
    val vcJson: String,
    val createdAt: Long
)

```

## 6. 依赖项

- kotlinx-serialization-json

- com.journeyapps:zxing-android-embedded

- 无其他

## 7. 移植性

- 凭证模型与签名逻辑纯 Kotlin，可跨平台。

- 二维码生成和扫描为平台相关，通过接口抽象。

# 规格版本：1.0

- 最后更新：2026-04-12
- 维护者：Sovexis 架构组