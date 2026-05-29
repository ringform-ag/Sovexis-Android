# AGENTS.md — Sovexis 项目任务调度中枢 v3.0

## 角色定位

我是 **Sovexis 首席架构师 · 陵谦**，负责将项目愿景拆解为可执行的模块任务，并调度 AI 程序工程师（Texno）完成各模块的开发与集成。

本文档是 **任务调度中枢**，记录所有已完成任务和待办事项。

## 项目架构

```
Sovexis/
├── app/                         # Android App (Kotlin + Compose + Hilt)
│   └── src/main/java/com/sovexis/
│       ├── di/                  # Hilt 依赖注入
│       ├── domain/              # 领域层 (identity/policy/payment/did/crypto/vc/zkp/storage/recovery/communication)
│       ├── tss/                 # TSS 阈值签名 (BnbTss + Bluetooth + ShareStorage)
│       ├── ui/                  # Compose UI (19 routes + 6-tab drawer)
│       └── platform/            # Application + MainActivity
└── docs/                        # App 端规格文档

Sovexis node/                    # Sovexis Node (Go + Gin + Wails GUI)
├── cmd/sovexis-node/            # CLI 入口
├── internal/                    # noise/storage/tss/api/discovery/trust
├── frontend/dist/               # Wails GUI 前端 (Vanilla JS)
└── docs/                        # Node 端规格文档
```

## 已完成任务清单（截至 2026-05-29）

### Phase 0 — 框架搭建
- ✅ Hilt DI 架构（AppModule + DomainModule + ZkpModule）
- ✅ Jetpack Compose UI 框架（SovexisScaffold + SovexisDrawer + 19路由导航）
- ✅ Room 数据库（AppDatabase + SafeBoxDao + VaultDao）

### Phase 1 — 核心模块
- ✅ 身份模块：IdentityManager + DidService + KeyManager（AndroidKeyStore + ECDSA P-256）
- ✅ 策略引擎：PolicyEnforcer（JSON策略 + 冲突检测 + isFrozen 熔断 + Markdown 导入导出）
- ✅ 密码学基础：KeyManager（密钥生成/签名/验证/代理重加密）

### Phase 2 — 应用功能
- ✅ 支付模块：PaymentManager + 策略前置检查 + 高风险弹窗(30s倒计时) + ZKP 证明流程
- ✅ 保险箱：VaultManager + StorageObfuscator + Level1Obfuscator + PathORAM
- ✅ 凭证模块：CredentialService + CredentialPresentationZkp（选择性披露）
- ✅ TSS 阈值签名：BnbTssSignatureService（2P-ECDSA + BLE 传输 + 份额加密存储）
- ✅ ZKP 零知识证明：ZkpService（Groth16 + ZkpCacheManager + Mopro 集成预留）

### Phase 3 — 启动与身份管理
- ✅ 启动流程：SplashScreen → CHECKING → AUTH_REQUIRED(BioPrompt) → READY → Home/Welcome
- ✅ WelcomeScreen：创建新身份 / 恢复已有身份 两入口
- ✅ 身份管理：IdentityManagementScreen（主账号 + 副账号列表 + 熔断 + 删除 + 切换活跃）
- ✅ 恢复流程：RecoveryScreen（助记词/社交/网络/身份导入 4种方式）

### Phase 4 — 设置与通信
- ✅ SettingsScreen：8开关组（存储级别/通信级别/TSS/KDFS缓存/隐蔽传输/协商策略/恢复方法/StrongBox）
- ✅ 通信层：CryptoCommLayer（Noise IK）+ ServiceRelayAdapter（中继）+ LanTcpTransportAdapter（局域网WebSocket）
- ✅ 隐蔽传输：CovertTransport + CovertNegotiationDialog

### Phase 5 — Node 集成
- ✅ Node CLI 入口（Go/Gin/Pebble/Noise/TSS/mDNS/Trust）
- ✅ Node `/healthz` 返回 publicKey + did + version
- ✅ Node 控制台输出公钥（醒目框框）
- ✅ MyNodeScreen：IP+端口+公钥输入+连接+3服务开关+Noise状态指示
- ✅ 公钥注册到 CryptoCommLayer + PreConfiguredKeys（重启后复用）
- ✅ Node Wails GUI（仪表盘+服务开关+网络+日志+设置）
- ✅ Node docs/ 文档体系（10篇规格文档）

### Phase 6 — 安全加固
- ✅ 网络配置：network_security_config（局域网明文 HTTP）
- ✅ 公钥验证：连接时比对，不匹配警告中间人攻击
- ✅ 错误兜底：NetworkOnMainThreadException → Dispatchers.IO 修复
- ✅ Node 安全约束：NUL字节过滤/速率限制/Noise轮换/PSK禁用/0700权限
- ✅ 身份导出：RecoveryScreen 身份导入（.sovexis-identity 文件选择器）

### Phase 7 — 架构规范化（2026-05-29）
- ✅ domain/node/ 包：NodeServiceManager 接口 + NodeStatus/NodeAccount/NodeServiceType
- ✅ domain/communication/message/ 统一消息协议：NodeMessageType（5种）+ NodeMessage + NodeMessageAction
- ✅ domain/communication/NodeMessageRouter 接口：sendRequest/sendPush/registerListener
- ✅ ChildIdentity.stewardNote 管家叮嘱字段预留
- ✅ MasterKeys → MasterKey.Builder 迁移（3 文件）
- ✅ Node internal/service/ 层（5 个 Service 文件，合并业务+数据访问）
- ✅ Node internal/domain/message.go 统一消息协议
- ✅ Node 端分层决策：扁平化 API + Service 两层，删除 repository/
- ✅ 架构文档 v2.0.0 修订（全文 [AS-IS]/[TO-BE] 标注 + 命名统一 + 债务更新）
- ✅ 删除冗余 ui/recovery/RecoveryScreen.kt（旧路径）
- ✅ 陵谦 + Texno 联合审计完成

## 待办事项

| 优先级 | 任务 | 说明 |
|--------|------|------|
| P1 | App ↔ Node WebSocket 联调 | `LanTcpTransportAdapter` 替换 `HttpURLConnection` |
| P1 | Node 端 WebSocket `/ws` 端点 | 当前 API 不包含 WebSocket 路由 |
| P2 | TSS 蓝牙传输联调 | BLE 真机测试 |
| P2 | 开机自启 + 系统托盘 | Node GUI 托盘菜单 |
| P3 | AI 推理服务 | Node 端部署私有模型 |
| P3 | 服务商适配器真实接入 | DeepSeek 等真实 API |

## 文档体系

| 层级 | 文档 |
|------|------|
| App 总览 | `PROJECT_OVERVIEW.md`, `AGENTS.md`, `MODULES.md`, `BUILD.md` |
| App 规格 | `IDENTITY_SPEC.md`, `POLICY_SPEC.md`, `PAYMENT_SPEC.md`, `VAULT_SPEC.md`, `CREDENTIAL_SPEC.md` |
| App 其他 | `ADAPTER_SPEC.md`, `AGENT_API_SPEC.md`, `UI_FLOW_SPEC.md`, `TSS_MIGRATION_GUIDE.md`, `TEST_BOUNDARY_RULES.md` |
| Node 总览 | `Sovexis node/docs/NODE_OVERVIEW.md`, `MODULES.md`, `BUILD.md` |
| Node 规格 | `NOISE_SPEC.md`, `STORAGE_SPEC.md`, `TSS_SPEC.md`, `API_SPEC.md`, `GUI_SPEC.md` |
| 集成 | `ANDROID_INTEGRATION.md`, `SECURITY_SPEC.md` |

## AI 程序工程师行动指令

- **当前阶段**：Phase 6 完成，进入稳定与联调阶段
- **下一步**：WebSocket 联调（App ↔ Node）
- **安全红线**：私钥不落盘、签名前策略检查、Noise 握手带外公钥交换

---
- Sovexis 首席架构师 · 陵谦
- 于 2026 年 数字方舟 奠基之际
