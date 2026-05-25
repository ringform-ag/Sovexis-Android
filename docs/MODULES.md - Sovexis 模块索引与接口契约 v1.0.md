# MODULES.md - Sovexis 模块索引与接口契约 v1.0

## 模块一览

| 模块 | 规格文档 | 核心类 | 版本 | 状态 |
|------|----------|--------|------|------|
| 身份模块 | `IDENTITY_SPEC.md` | `IdentityManager` | 1.0 | 待实现 |
| 策略引擎 | `POLICY_SPEC.md` | `PolicyEnforcer` | 1.0 | 待实现 |
| 支付模块 | `PAYMENT_SPEC.md` | `PaymentManager`, `MockLedger` | 1.0 | 待实现 |
| 保险箱 | `VAULT_SPEC.md` | `VaultManager` | 1.0 | 待实现 |
| 可验证凭证 | `CREDENTIAL_SPEC.md` | `CredentialManager` | 1.0 | 待实现 |
| Agent API | `AGENT_API_SPEC.md` | `ISovexisAgentService` | 1.0 | 待实现 |
| 适配器 | `ADAPTER_SPEC.md` | `ServiceAdapter` | 1.0 | 待实现 |
| UI 流程 | `UI_FLOW_SPEC.md` | - | 1.0 | 待实现 |

## 接口契约摘要

- 身份模块提供签名和公钥解析。
- 策略引擎提供权限检查。
- 支付模块调用策略检查，构造交易并签名提交。
- 保险箱模块调用策略检查，派生密钥进行加解密。
- 凭证模块调用身份模块签名并验证。
- Agent API 封装上述模块供外部调用。

---

**维护者**：Sovexis 架构组