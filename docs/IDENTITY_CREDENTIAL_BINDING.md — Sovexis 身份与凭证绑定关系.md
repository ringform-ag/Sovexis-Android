# IDENTITY_CREDENTIAL_BINDING.md — Sovexis 身份与凭证绑定关系

**创建日期**：2026-05-21  
**创建者**：陵谦  
**适用范围**：Sovexis 身份层与凭证层开发者  
**关联文档**：`AI_IMPLEMENTATION_SPEC.md`, `docs/INPUT_INTERFACE_SPEC.md`

---

## 第一章：主账号与副账号的密钥层级

### 1.1 BIP-32 派生结构

Sovexis 使用 BIP-32 分层确定性钱包结构管理密钥：

```
m/44'/60'/0'/0/0     主账号（Master Identity）
  ├── m/44'/60'/0'/1/0   副账号 1（Child Identity 1）
  ├── m/44'/60'/0'/1/1   副账号 2（Child Identity 2）
  └── ...
```

### 1.2 密钥用途

| 派生路径 | 用途 | 控制方 |
|----------|------|--------|
| m/44'/60'/0'/0/0 | 主账号 DID、主密钥对 | 用户（助记词持有者） |
| m/44'/60'/0'/1/* | 副账号 DID、业务密钥对 | 主账号授权委派 |

### 1.3 安全边界

- 主账号私钥 **永不离开** 安全存储（Android Keystore StrongBox）
- 副账号私钥可由主账号 **随时撤销**
- 副账号无法 **独立签发** 凭证（必须由主账号委派）

---

## 第二章：凭证与身份的绑定

### 2.1 凭证数据结构

```json
{
  "@context": ["https://www.w3.org/2018/credentials/v1"],
  "id": "urn:uuid:...",
  "type": ["VerifiableCredential"],
  "issuer": "did:sovexis:master#keys-1",
  "issuanceDate": "2026-05-21T00:00:00Z",
  "credentialSubject": {
    "id": "did:sovexis:child-1",
    "name": "副账号 1",
    "permissions": ["payment:1000", "credential:present"]
  },
  "proof": { ... }
}
```

### 2.2 `credentialSubject.id` 与 DID 的对应关系

| 凭证类型 | credentialSubject.id | 说明 |
|----------|---------------------|------|
| 主账号凭证 | 主账号 DID | 用户自身身份凭证 |
| 副账号凭证 | 副账号 DID | 由主账号签发的子身份凭证 |
| 委派凭证 | 副账号 DID + 委派声明 | 主账号授权副账号代表自己 |

### 2.3 绑定验证

验证凭证时，必须检查：
1. `issuer` 是否为已知的 Sovexis DID
2. `credentialSubject.id` 是否与当前操作身份匹配
3. 凭证签名是否有效
4. 凭证是否过期或被撤销

---

## 第三章：委派凭证

### 3.1 委派机制

主账号可以为副账号签发**委派凭证**（Delegation Credential），授权副账号代表自己执行特定操作。

### 3.2 委派凭证结构

```json
{
  "type": ["VerifiableCredential", "DelegationCredential"],
  "issuer": "did:sovexis:master",
  "credentialSubject": {
    "id": "did:sovexis:child-1",
    "delegatedBy": "did:sovexis:master",
    "scope": ["payment:limit:1000", "credential:present:read-only"],
    "expiry": "2026-12-31T23:59:59Z"
  }
}
```

### 3.3 委派限制

- **时间限制**：委派凭证必须设置过期时间
- **范围限制**：明确指定副账号可执行的操作类型和限额
- **撤销机制**：主账号可随时签发撤销凭证使委派失效

---

## 第四章：凭证通道

### 4.1 轻量级凭证挂载

**凭证通道**（Credential Channel）是一种无需独立 DID 的凭证挂载机制。

### 4.2 使用场景

- 临时性凭证（一次性验证码）
- 低敏感度凭证（会员等级、偏好设置）
- 设备绑定凭证（与特定设备关联，不绑定身份）

### 4.3 技术实现

```kotlin
data class CredentialChannel(
    val channelId: String,           // 通道唯一标识
    val parentDid: String,           // 所属主账号 DID
    val credentialType: String,      // 凭证类型
    val payload: EncryptedPayload,   // 加密内容
    val deviceBinding: DeviceBinding // 设备绑定信息
)
```

### 4.4 与独立 DID 的区别

| 特性 | 独立 DID | 凭证通道 |
|------|----------|----------|
| DID 文档 | 有 | 无 |
| 密钥对 | 独立 | 复用父账号 |
| 撤销 | 需链上操作 | 父账号直接删除 |
| 适用场景 | 长期业务身份 | 临时/低敏感凭证 |

---

## 第五章：签发策略

### 5.1 谁可以签发

| 签发者 | 可签发凭证 | 限制 |
|--------|-----------|------|
| 主账号 | 所有类型 | 无 |
| 副账号 | 仅凭证通道 | 不能签发独立 DID 凭证 |
| 服务商 | 服务声明凭证 | 需用户授权 |

### 5.2 为什么副账号不能签发独立 DID 凭证

1. **安全边界**：副账号密钥可能存储在安全性较低的环境中
2. **撤销复杂性**：副账号签发的凭证需要主账号才能撤销，增加管理复杂度
3. **信任链断裂**：副账号本身是由主账号派生，再签发凭证会导致信任链过长

### 5.3 签发流程

```
主账号发起签发
    ↓
生成凭证内容
    ↓
使用主账号私钥签名
    ↓
存储凭证（本地/云端）
    ↓
返回凭证 ID
```

---

## 第六章：出示策略

### 6.1 ZKP 缓存

凭证出示时使用 ZKP 证明，支持缓存策略：
- **缓存时长**：1 小时
- **缓存键**：`present_${credentialId}_${challengeHash}`
- **强制刷新**：`requireFresh=true` 时忽略缓存

### 6.2 选择性披露

用户可以选择性披露凭证中的字段：
- 必须披露：`id`, `type`, `issuer`, `issuanceDate`
- 可选披露：`credentialSubject` 中的具体字段
- 不披露：`proof`（验证时使用，不出示给第三方）

### 6.3 出示流程

```
用户发起出示请求
    ↓
检查 ZKP 缓存
    ↓
缓存命中 → 返回缓存证明
缓存未命中 → 生成新 ZKP 证明
    ↓
附加凭证内容（选择性披露）
    ↓
返回完整出示数据
```

---

## 第七章：撤销策略

### 7.1 委派撤销

主账号可随时撤销对副账号的委派：
- 签发撤销凭证（Revocation Credential）
- 更新本地撤销列表
- 通知相关服务商

### 7.2 服务商撤销

服务商可在以下情况下撤销凭证：
- 用户违反服务条款
- 凭证过期
- 安全事件（密钥泄露等）

### 7.3 过期自动失效

所有凭证必须设置过期时间：
- 主账号凭证：默认 1 年
- 副账号凭证：默认 6 个月
- 委派凭证：根据委派范围设置（通常 1-3 个月）

### 7.4 撤销验证

验证凭证时，必须检查：
1. 凭证是否在有效期内
2. 签发者是否已撤销该凭证
3. 服务商是否已标记该凭证为无效

---

## 第八章：未来展望

### 8.1 承载现实世界身份的特殊副账号

未来版本可能支持将现实世界身份（如政府 ID、护照）映射为特殊副账号：

```kotlin
data class RealWorldIdentity(
    val type: IdentityType,        // PASSPORT, DRIVERS_LICENSE, etc.
    val issuer: String,            // 签发机构
    val documentNumber: String,    // 文档编号（加密存储）
    val verificationStatus: Status // 验证状态
)
```

### 8.2 设计原则

- **隐私保护**：现实世界身份信息加密存储，仅在必要时解密
- **最小披露**：出示时仅披露必要字段（如"已成年"而非具体出生日期）
- **用户控制**：用户完全控制何时、向谁披露现实世界身份信息

### 8.3 与现有架构的关系

现实世界身份副账号：
- 使用标准 BIP-32 派生路径
- 遵循相同的委派和出示策略
- 支持相同的 ZKP 证明机制

---

## 附录：凭证类型速查表

| 凭证类型 | 签发者 | 出示场景 | 有效期 |
|----------|--------|----------|--------|
| MasterIdentity | 主账号自己 | 身份验证 | 1 年 |
| ChildIdentity | 主账号 | 业务操作 | 6 个月 |
| Delegation | 主账号 | 授权副账号 | 1-3 个月 |
| ServiceClaim | 服务商 | 服务访问 | 按服务约定 |
| CredentialChannel | 主账号/副账号 | 临时凭证 | 按场景设定 |

---

**文档状态**：✅ 初稿完成，随身份层和凭证层实现迭代更新
