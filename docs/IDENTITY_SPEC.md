# IDENTITY_SPEC.md - Sovexis 身份模块规格 v1.0

## 模块定位
身份模块是 Sovexis 数字主权基座的根基。它负责在 Android 15 设备上创建与管理主权身份，包括：
- 基于 WebAuthn 的生物特征绑定的主账号创建
- 基于 BIP-32 的副账号派生
- 主/副账号的安全存储与生命周期管理
- 隐身地址（一次性收款地址）的生成

本模块遵循“自我实现、最小依赖”原则，优先使用 Android 原生 API（Keystore、WebAuthn），避免引入重型第三方密码库，以便未来向其他平台移植时仅需替换平台相关调用。

---

## 1. 身份模型定义

### 1.1 主账号 (MasterIdentity)

```kotlin
data class MasterIdentity(
    val did: String,                 // 格式: did:sovexis:0x{64位十六进制}
    val alias: String?,              // 用户自定义后缀（如“-我的主账号”）
    val credentialId: String,        // WebAuthn 凭证 ID（Base64URL）
    val publicKeyPem: String,        // ECDSA P-256 公钥 PEM
    val encryptedSeed: ByteArray,    // BIP-32 种子，使用 Keystore 主密钥加密存储
    val createdAt: Long              // Unix 时间戳（毫秒）
)

```
- did 生成规则：对公钥 PEM 的 UTF-8 字节计算 SHA-256，取后 32 字节，转换为十六进制，前缀 did:sovexis:。
- 主账号唯一性：一个设备只能存在一个主账号（通过 credentialId 唯一索引保证）。
- 生物绑定：主账号创建过程中必须成功完成 WebAuthn 平台认证器注册。

### 1.2 副账号 (ChildIdentity)

```kotlin
data class ChildIdentity(
    val did: String,                 // 格式: did:sovexis:0x{64位十六进制}
    val masterDid: String,           // 所属主账号 DID
    val derivationPath: String,      // BIP-32 派生路径，如 "m/44'/60'/0'/0/0"
    val alias: String?,              // 用户自定义名称
    val uniqueCode: String,          // 8 位十六进制唯一标识码，用于 UI 展示
    val publicKeyPem: String,        // ECDSA P-256 公钥 PEM
    val encryptedPrivateKey: ByteArray, // 加密后的私钥（用于签名）
    val type: ChildType,             // 副账号类型
    val createdAt: Long
)

enum class ChildType {
    STANDARD,   // 标准副账号
    STEWARD,    // 管家副账号（供 AI Agent 使用）
    SERVICE     // 服务商副账号（预留）
}
```
- 派生规则：使用 BIP-32 从主账号种子派生，硬化派生。
- 唯一标识码：sha256(masterDid + derivationPath).substring(0, 8)。
- 密钥对：每个副账号拥有独立的 ECDSA P-256 密钥对，用于签名（支付、凭证签发）。
- 加密存储：私钥使用 Android Keystore 生成的 AES-256-GCM 密钥加密后存储。

## 2. Android 安全存储设计

### 2.1 Android Keystore 密钥体系

密钥别名	类型	用途	存储位置
sovexis_master_key	AES-256-GCM	加密主账号种子和副账号私钥	Android Keystore (StrongBox 优先)
sovexis_auth_key_{did}	ECDSA P-256	副账号交易签名（由 Keystore 管理，私钥不可导出）	Android Keystore
sovexis_webautn_credential	WebAuthn 凭证	平台认证器关联	系统凭据存储
- 原则：所有涉及签名的密钥应尽可能由 Keystore 硬件管理；对于需要派生子密钥的场景（BIP-32），种子必须使用 sovexis_master_key 加密后存储于 EncryptedSharedPreferences。

### 2.2 存储介质

- EncryptedSharedPreferences：存储身份元数据（DID、别名、加密种子、派生路径等）。使用 MasterKeys.AES256_GCM_SPEC 加密。

- DataStore (Proto)：备选方案，对于复杂结构可迁移至 Proto DataStore，MVP 阶段使用 EncryptedSharedPreferences 足够。

2.3 安全警告
- 主账号种子不得以明文形式存储在任何位置。

- 副账号私钥优先使用 Keystore 非对称密钥（不可导出），若需要 BIP-32 派生，则必须加密存储派生出的私钥，且加解密过程在 Keystore 内完成或使用 Keystore 密钥包裹。

## 3. 核心 API

### 3.1 IdentityManager（单例）

```kotlin
interface IdentityManager {
    // 主账号管理
    suspend fun createMasterIdentity(alias: String?, activity: FragmentActivity): Result<MasterIdentity>
    suspend fun getMasterIdentity(): MasterIdentity?
    suspend fun updateMasterAlias(alias: String): Result<Unit>
    
    // 副账号管理
    suspend fun deriveChildIdentity(type: ChildType, alias: String?): Result<ChildIdentity>
    suspend fun listChildIdentities(type: ChildType? = null): List<ChildIdentity>
    suspend fun getChildIdentity(did: String): ChildIdentity?
    suspend fun deleteChildIdentity(did: String): Result<Unit>
    
    // 签名操作（供支付/凭证模块调用）
    suspend fun signWithChildIdentity(did: String, data: ByteArray): Result<ByteArray>
    
    // 隐身地址
    fun generateStealthAddress(receiverPublicKeyPem: String): String
}
```
### 3.2 WebAuthn 集成
- 使用 AndroidX androidx.credentials:credentials-play-services-auth 和 androidx.credentials:credentials。

- 注册流程：

1.调用 credentialManager.createCredential，传入 PublicKeyCredentialCreationOptions。
2.获取 PublicKeyCredential 后，提取 credentialId 和公钥。
3.生成主账号 DID 和种子，并用 Keystore 密钥加密种子。
4.保存主账号记录。

### 3.3 BIP-32 实现

- 自我实现轻量级 BIP-32（遵循规范，仅需支持 secp256k1 和 P-256 曲线）。鉴于我们使用 P-256，需基于 HMAC-SHA512 实现 CKD 函数。

- 参考实现：使用 javax.crypto.Mac 和 java.security 包，不依赖第三方库。

```kotlin
// 伪代码示意
fun deriveChildKey(seed: ByteArray, path: String): ECKeyPair {
    val masterKey = bip32MasterKey(seed)
    return derivePath(masterKey, path)
}
```

## 4. 数据迁移与兼容性

- 版本 1：存储结构如上述定义。后续升级需提供 IdentityMigrator 处理 schema 变更。
- MVP 阶段不实现复杂迁移，若发生存储冲突，提示用户重置应用（演示环境可接受）。

## 5. 单元测试要求

- 测试 BIP-32 派生向量的正确性（使用已知测试向量）。

- 测试 DID 生成规则的一致性。

- 测试隐身地址的生成与恢复逻辑。

## 6. 依赖项（Android 15）

```kotlin
// build.gradle.kts
dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    // 无其他第三方密码库，使用标准库实现
}
```

## 7. 接口预留（多平台移植）

- 将 Android 特定调用封装在 platform/AndroidKeyStoreProvider.kt 中。

- 定义通用接口 KeyStoreProvider，为未来 iOS（Keychain）、桌面（操作系统密钥链）移植留下清晰边界。

# 规格版本：1.0
- 最后更新：2026-04-12
- 维护者：Sovexis 架构组