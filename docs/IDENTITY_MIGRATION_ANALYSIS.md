# Sovexis 身份迁移 · 全面方案分析

> texno · 2026-06-25
> 状态：方案阶段，未实施
> 前置阅读：PersonhoodManager.kt (exportForMigration/importFromMigration)、MyNodeViewModel.kt (binding 流程)

---

## 一、现状审计

当前 `PersonhoodManager` 的迁移骨架只做了两步：

1. `exportForMigration(did, teeSig)` → 打包 hdList + salt + commitments + fingerConfigs → 返回 `MigrationPackage`
2. `importFromMigration(pkg)` → 解包 → `storeHelperData(label, hd)` → 结束

**缺了什么**：

| 缺失 | 风险 |
|------|------|
| 迁移授权令牌（旧设备签名证明"我同意转让"） | 没有密码学证据，任何人拿到 `MigrationPackage` 都能导入 |
| 旧设备迁移后状态机 | 迁移后旧设备 persona 原封不动，两个设备同时活跃 → 双花/双签风险 |
| 新设备 Node 重绑定验证 | 当前绑定用 HMAC+pairingKey，换设备后 pairingKey 已失效 |
| 硬件指纹验证 | 没有任何设备身份证明，Node 无法区分"同一个人的新手机"和"冒充者" |
| 宪法触发 | 迁移操作没有任何 CONST 规则覆盖；现有 CONST-001~011 不涉及设备切换 |
| 迁移完整性校验 | 导入端不校验 salt 的一致性，不验证 commit 对 |

---

## 二、风险定级

身份迁移是 **极端高风险** 操作。

> 理由：迁移 = 将一个人的数字主权从旧设备 **委派** 到新设备。如果此过程被劫持，攻击者获得的是完整的数字身份——包括 DID、存储合约、信任评分、加密保险箱访问权。

建议在现有 `high` 之上新增 `extreme` 级别：

| 级别 | 触发条件 | 用户确认 | C-08 | 冷却期 |
|------|----------|----------|------|--------|
| low | 无敏感参数 | 不需要 | 不需要 | 不需要 |
| medium | 涉及 DID/合约 ID | 可能需要 | 不需要 | 不需要 |
| **high** | 支付/质押/多模态 | 需要 | **需要** | 不需要 |
| **extreme** ✨ | 身份迁移、恢复账户、导出私钥 | **需要** | **需要** | **300s** |

`extreme` 级别的额外约束：
- **双因素确认**：旧设备生物特征 + 新设备生物特征
- **冷却期**：进入迁移流程后，300 秒内未完成传输自动失效
- **降级禁令**：`extreme` 操作无降级路径，失败即阻断
- **日志强制**：操作全程不可变日志（Pebble DB），即使出错也记录

---

## 三、迁移全生命周期

### 3.1 术语定义

| 概念 | 说明 |
|------|------|
| 旧设备 (Old Device) | 当前持有活跃 persona + DID 的手机 |
| 新设备 (New Device) | 无 Sovexis 账号的新手机 |
| 迁移 (Migration) | 将 persona 的**委派权**从旧设备转移到新设备 |
| 迁移令牌 (Transfer Auth Token) | 旧设备 TEE 签发的一次性授权证明 |
| 迁移后状态 (Migrated-Out) | 旧设备 persona 变为只读、不可签名、已迁出 |
| 设备硬指纹 (Device Fingerprint) | Android KeyStore 中 ECDH 公钥的 attestation 证书链 |

**"导出" vs "迁移"** — 正确用词是**迁移**。"导出"暗示数据的一个副本，而 Sovexis 的哲学是 persona 的委派权在同一时刻只能有一个持有者。

### 3.2 状态机

```
旧设备                                      新设备
──────                                     ──────
ACTIVE                                    NO_ACCOUNT
  │                                          │
  │ [用户操作：设置 → 迁移身份到新设备]         │
  │                                          │
  ▼                                          │
MIGRATING                                   │
  │── C-08 令牌 + 生物特征验证                │
  │── TEE 签发 TransferAuthToken             │
  │    {did, device_fingerprint_old,          │
  │     device_fingerprint_new,               │
  │     expires_at, nonce}                    │
  │── 打包 MigrationPackage +                │
  │    TransferAuthToken → AES-GCM → BLE    │
  │                                          │
  │   ═══════════ BLE / WiFi Direct ═══════   │
  │                                          │
  │                                          ▼
  │                                       IMPORTING
  │                                          │── 解密 MigrationPackage
  │                                          │── 验证 TransferAuthToken
  │                                          │    (旧设备 TEE 签名校验)
  │                                          │── 创建本地 persona 结构
  │                                          │── 存储 hdList + salt
  │                                          │── 提示用户手指注册
  │                                          │
  │   ◄── [迁移完成确认] ─────────────────── │
  │                                          │
  ▼                                          ▼
MIGRATED_OUT                              PENDING_NODE_REBIND
  │── persona 标记为 migrated_out           │── 尝试连接旧设备绑定的 Node
  │── 签名能力冻结                            │── 提交 TransferAuthToken
  │── 本地数据保留（用户可手动清除）           │    + 新设备硬指纹
  │── UI 显示"此设备已迁出"                  │── Node 验证：
  │                                          │     1. Token 签名 ← 旧设备 TEE 公钥
  │                                          │     2. 新硬指纹 ≠ 旧硬指纹（证明换了设备）
  │                                          │     3. Token 未过期（5 分钟内）
  │                                          │     4. DID 匹配
  │                                          │
  │                                          ▼
  │                                       ACTIVE (新设备)
  │                                          │── Node 绑定切换到新设备
  │                                          │── persona 注册 BINDING_COMPLETE
  │                                          │── 旧设备标记 BINDING_REVOKED
  │                                          │
  │   ◄── Node 通知：绑定已转移 ──────────── │
  │                                          │
  ▼                                          ▼
MIGRATED_OUT                              ACTIVE
  [用户可选择 "清除本地数据"]                 [正常使用]
```

### 3.3 关键决策点

#### 决策 1：旧设备迁移后状态 — 冻结/保留/双活？

| 方案 | 描述 | 主权风险 | 推荐 |
|------|------|----------|------|
| A: 冻结 | persona 标记 migrated_out，签名禁用，数据保留 | 低 | ✅ |
| B: 清除 | 立即删除旧设备所有 persona 数据 | 零（但不可逆） | ❌ 用户可能后悔 |
| C: 双活 | 两个设备同时可用 | 高（双花、身份分裂） | ❌ |

**推荐方案 A：冻结但保留。** 用户需要手动操作才能清除旧设备数据。如果迁移过程中新设备损坏（摔了/丢了），用户可以回到旧设备解冻，重新发起迁移。

冻结的具体含义：
- `PersonaState = FROZEN_MIGRATED`
- 所有签名操作返回 `PersonaMigratedOutException`
- Node 不再接受此设备发起的交易
- 用户可以手动 "清除此设备的身份数据"
- 用户可以手动 "解冻并恢复"（需要双向生物验证 + 冷却期）

#### 决策 2：Node 重绑定 — 硬件指纹 or 账号验证？

| 方案 | 描述 | 安全性 | 用户体验 |
|------|------|--------|----------|
| A: 纯账号 | Node 只检查 DID | ❌ 低 | 简单 |
| B: 迁移令牌 | 旧设备 TEE 签发一次性授权 | 🔒 中 | 需要旧设备参与 |
| C: 令牌 + 硬指纹 | B + 新设备 KeyStore attestation | 🔒🔒 高 | 最安全 |

**推荐方案 C：迁移令牌 + 设备硬指纹双层验证。**

流程：
1. 旧设备 TEE 签名 `{did, old_fingerprint, new_fingerprint, nonce, expires_at}` → TransferAuthToken
2. 新设备携带 Token + 自己的 KeyStore attestation chain → Node
3. Node 验证 4 件事：
   - a. Token 签名 ← 旧设备公钥（已知，存储在 binding 记录中）
   - b. Token 中的 `new_fingerprint` == attestation 证书中的公钥 hash
   - c. Token 中的 `old_fingerprint` ≠ attestation 中的公钥 hash（证明是换了设备）
   - d. `expires_at > now()`（5 分钟窗口）
4. 全部通过 → Node 更新 binding 记录，指向新设备公钥
5. Node 推一条 `DeviceBindingRotated` 事件给旧设备（如果还在线）

**为什么不能用纯账号验证？**
当前 MyNodeViewModel 的 HMAC binding 用的是 `pairingKey`。配对码是用户手动扫描二维码生成的，换设备后旧 pairing key 已不在。如果只靠 DID 验证，那么知道 DID 的人就能绑定任何设备——DID 是公开信息。

**为什么需要硬指纹？**
如果没有硬指纹，攻击者可以：
1. 在自己的手机上创建一个同 DID 的身份
2. 声称"我是迁移来的新设备"
3. 如果 Node 只检查 DID + 密码，攻击者可能通过

Android KeyStore attestation 提供了密码学级别的设备身份证明：证书链证明公钥确实生成于 Trusted Execution Environment（TEE），无法被模拟。

#### 决策 3：迁移令牌的密码学构造

```
TransferAuthToken = Sign_TEE_old(
    SHA-256(
        did                          // DID 字符串
        || old_device_fp              // 旧设备 KeyStore ECDH 公钥 hash
        || new_device_fp              // 新设备 KeyStore ECDH 公钥 hash
        || nonce                      // 32 bytes random
        || created_at                 // unix ms
        || expires_at                 // created_at + 300000
    )
)
```

特征：
- TEE 签名保证令牌不可伪造
- `new_device_fp` 嵌入令牌中，绑定到特定新设备
- 300 秒过期窗口防止令牌被截获后延迟使用
- `nonce` 防止重放

### 3.4 引导提示流程（修正版）

```
新设备 (无账号)                    旧设备 (有账号)
─────────────                      ─────────────
欢迎页                            设置页
「从其他设备迁移身份」              「迁移身份到新设备」
    │                                  │
    ▼                                  ▼
安全提醒                           安全提醒
"此操作将把身份委派权               "此操作将把身份委派权
 转移到本设备。                      转移到新设备。
 旧设备将被冻结。"                   旧设备将被冻结。"
    │                                  │
    ▼                                  ▼
选择传输通道                        选择传输通道
（蓝牙/WiFi Direct）                （蓝牙/WiFi Direct）
    │                                  │
    ▼                                  ▼
等待配对                            生成迁移令牌
"请在旧设备上打开迁移，              [生物特征验证]
 并确保两台设备靠近。"               [C-08 授权确认]
                                    正在打包…
                                        │
    ════════════ BLE / WiFi Direct ════════
    │                                  │
    ▼                                  ▼
接收加密数据                          发送完成
验证 TransferAuthToken                "迁移数据已发送。"
导入 persona                          "旧设备已被冻结。"
    │
    ▼
迁入完成
"正在连接您的 Node…"
提交 TransferAuthToken
+ 新设备硬指纹
→ Node 验证通过
→ 绑定切换完成
    │
    ▼
"迁移完成。请验证您的
 生物特征以激活新设备。"
    │
    ▼
手指注册 → bioHash 验证 → 进入主页
```

---

## 四、宪法扩展

### CONST-012：身份迁移保护

```
ID: CONST-012
Description: 身份迁移为极端高风险操作，需双因素确认 + 冷却期
Type: RuleMigrationCheck
BlockMsg: 宪法规则 CONST-012 阻止：身份迁移需要旧设备 TEE 签名 + 新设备硬指纹 + C-08 令牌
```

行为：
- 迁移操作自动评级 `extreme`
- 要求 `C08Checker` 同时校验旧设备和新设备的令牌
- 迁移 Package 必须包含 `TransferAuthToken`，否则拒绝
- 迁移窗口 300 秒，超时需重新发起

### CONST-013：迁移后旧设备冻结

```
ID: CONST-013
Description: 迁移完成后，旧设备 persona 自动进入冻结状态，不可逆
Type: RuleFuncCheck
BlockMsg: 宪法规则 CONST-013 阻止：旧设备 persona 已迁移，签名能力已冻结
```

行为：
- 迁移完成的确认信号发出后，`PersonhoodManager` 自动调用 `freezePersona(did)`
- 冻结后签名操作全部返回 `PersonaMigratedOutException`
- 用户必须经过 **解冻流程**（双向生物验证 + 冷却期 + Node 确认）才能恢复旧设备
- Node 端同步更新 `binding_status = DEVICE_MIGRATED`

---

## 五、数据流安全分析

### 威胁模型

| 攻击者 | 能力 | 防御 |
|--------|------|------|
| 被动窃听者 | 监听 BLE 流量 | AES-256-GCM 包裹后传输 |
| 中间人 | 拦截并重放 BLE 包 | TransferAuthToken 含 nonce + 5min 窗口 |
| 恶意新设备 | 冒充接收者 | Token 内嵌 `new_device_fp` |
| 已丢失旧设备 | 旧设备被偷，攻击者尝试解冻 | 解冻需生物特征验证 + Node 确认 |
| 双花攻击 | 旧设备迁移后继续签名 | Node 端拒绝 migrated-out 设备的交易 |

### 不暴露的信息

- **指纹模板**：bioHash 只存 commit，指纹模板本身不传输（模糊提取器在本地完成）
- **私钥**：不传输。新设备通过 DeriveKey 从盐值重新派生，不在线路上出现
- **Node 认证凭据**：通过 TransferAuthToken 间接授权，原始凭据不出旧设备

---

## 六、TODO 清单（实施阶段）

| 序号 | 任务 | 涉及文件 | 依赖 |
|------|------|----------|------|
| T1 | PersonaState 新增 `FROZEN_MIGRATED` 状态 | PersonhoodManager.kt | — |
| T2 | `exportForMigration` 新增 TransferAuthToken 签发 | PersonhoodManager.kt + KeyManager.kt | TEE 签名支持 |
| T3 | `MigrationPackage` 结构新增 `authToken` + `oldFingerprint` + `newFingerprint` | PersonhoodManager.kt, IdentityMigration.kt | T1, T2 |
| T4 | `importFromMigration` 新增 Token 验证 + persona 结构创建 | PersonhoodManager.kt | T3 |
| T5 | 旧设备 `freezePersona(did)` — 迁移后自动调用 | PersonhoodManager.kt | T1 |
| T6 | 新设备 KeyStore attestation 工具函数 | DeviceFingerprint.kt (新文件) | — |
| T7 | CONST-012 / CONST-013 宪法规则 | constitution.go | — |
| T8 | Node 端 `POST /binding/migrate` 端点 | Node: steward API | T6 |
| T9 | Node 端 DEVICE_MIGRATED binding 状态 | Node: steward/binding | T8 |
| T10 | 设置页 "迁移身份到新设备" → 用词修正 | SettingsScreen.kt | — |
| T11 | 迁移向导：新增冷却期倒计时 + 双因素确认 | SettingsScreen.kt + MigrationImportScreen.kt | — |
| T12 | 迁移完成后自动触发 Node 重绑定 | MigrationImportViewModel.kt + MyNodeViewModel.kt | T8 |
