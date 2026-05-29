# MODULES.md — Sovexis 模块索引与接口契约 v3.0

## 模块一览

| 模块 | 规格文档 | 核心类 | 版本 | 状态 |
|------|----------|--------|------|------|
| 身份模块 | `IDENTITY_SPEC.md` | `IdentityManager` | 1.0 | ✅ 已实现 |
| 策略引擎 | `POLICY_SPEC.md` | `PolicyEnforcer` | 1.0 | ✅ 已实现（含 isFrozen 熔断） |
| 支付模块 | `PAYMENT_SPEC.md` | `PaymentManager` | 1.0 | ✅ 已实现（含 ZKP + TSS） |
| 保险箱 | `VAULT_SPEC.md` | `VaultManager` | 1.0 | ✅ 已实现（Level1Obfuscator + PathORAM） |
| 可验证凭证 | `CREDENTIAL_SPEC.md` | `CredentialService` | 1.0 | ✅ 已实现（含 CredentialPresentationZkp） |
| 恢复机制 | — | `RecoveryManager` | 1.0 | ✅ 已实现（助记词/社交/网络/身份导入） |
| DID 服务 | — | `DidService` | 1.0 | ✅ 已实现 |
| 密码学基础 | — | `KeyManager` | 1.0 | ✅ 已实现（AndroidKeyStore + ECDSA + PRE） |
| TSS 阈值签名 | `TSS_MIGRATION_GUIDE.md` | `BnbTssSignatureService` | 1.0 | ✅ 已实现（2P-ECDSA + BLE + 份额加密存储） |
| ZKP 零知识证明 | — | `ZkpService` | 1.0 | ✅ 已实现（Groth16 + Mopro 集成预留） |
| 加密通信 | — | `CryptoCommLayer` | 1.0 | ✅ 已实现（Noise IK + ServiceRelayAdapter + LanTcpTransportAdapter） |
| 隐蔽传输 | — | `CovertTransport` | 1.0 | ✅ 已实现 |
| Agent API | `AGENT_API_SPEC.md` | — | 1.0 | 🔲 已定义接口 |
| 服务商适配器 | `ADAPTER_SPEC.md` | — | 1.0 | 🔲 已定义接口 |
| UI 流程 | `UI_FLOW_SPEC.md` | — | 3.0 | ✅ 已实现（19 路由 + 6 页签抽屉） |
| Node 集成 | — | `MyNodeViewModel`, `NodeServiceManager`, `NodeMessageRouter` | 1.0 | ✅ 已实现（IP+端口+公钥+Noise握手预置+消息路由+节点业务管理） |
| 统一消息协议 | — | `NodeMessageProtocol`, `NodeMessage`, `NodeMessageType` | 1.0 | ✅ 已定义（request/response/push/peer/steward） |
| Sovexis Node | 见 `Sovexis node/docs/` | — | 1.0 | ✅ 已实现（Go/Gin/Pebble/Noise/TSS/Wails GUI + Service/Repository/Domain 分层） |

## App ↔ Node 接口契约

| App 模块 | Node 端点 | 用途 |
|----------|----------|------|
| `MyNodeViewModel` | `GET /healthz` | 连接验证 + 公钥获取 |
| `CryptoCommLayer` | `POST /noise/handshake` | Noise IK 握手 |
| `VaultViewModel` | `POST /storage/store` | 加密分片上传 |
| `VaultViewModel` | `GET /storage/retrieve/{shardID}` | 分片检索 |
| `PaymentViewModel`（TSS 高安全） | `POST /tss/sign` | 协同签名 |

## 内部依赖关系

```
UI Layer (Compose)
  └── ViewModel (Hilt @HiltViewModel)
        └── Domain Layer (@Singleton)
              ├── IdentityManager → DidService → KeyManager
              ├── PolicyEnforcer → EncryptedSharedPreferences
              ├── PaymentManager → PolicyEnforcer + KeyManager
              ├── CredentialService → DidService + KeyManager
              ├── VaultManager → PolicyEnforcer + StorageObfuscator
              ├── RecoveryManager → MnemonicRecovery + SocialRecovery + NetworkRecovery
              ├── CryptoCommLayer → TransportAdapter + IdentityManager + Noise
              ├── ZkpService → ZkpProverImpl + ZkpVerifierImpl + ZkpCacheManager
              └── TSS → BnbTssSignatureService + ShareStorage + BluetoothTransceiver
```

---
**维护者**：Sovexis 架构组
