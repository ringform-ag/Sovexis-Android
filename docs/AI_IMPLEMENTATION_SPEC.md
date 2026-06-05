# Sovexis 进阶模块实现状态 v3.0

> 维护者：Sovexis 架构组  
> 创建日期：2026-05-09  
> 版本：v3.0  
> 用途：记录所有进阶模块的当前实现状态、已知限制和待办事项  
> 实现者：Texno（核心实现）+ 陵谦（架构设计与关键密码学算法）

---

## 一、模块状态一览

| 模块 | 核心文件 | 状态 | 备注 |
|------|----------|------|------|
| ZKP 零知识证明 | `ZkpService`, `ZkpProverImpl`, `ZkpVerifierImpl`, `MoproLib`, `CircuitPathProvider`, `ZkpCacheManager`, `RootDetector` | ✅ 已完成 | Mopro FFI v0.3.6，纯 Rust witness，cargo ndk 交叉编译 arm64-v8a |
| 代理重加密 (PRE) | `ProxyReEncryptionService`, `PreModels` | ✅ 已完成 | 陵谦重写，完整 ECDH + AES-GCM |
| 阈值签名 (TSS) | `BnbTssSignatureService`, `GoTssWrapper`, `BluetoothTransceiver`, `ShareStorage` | ✅ 已完成 | 2P-ECDSA, BLE, AAR 集成 |
| 存储混淆 L1 | `Level1Obfuscator` | ✅ 已完成 | 虚假读取实现 |
| 存储混淆 L2 (Path ORAM) | `PathOramImpl`, `OramBucket`, `PositionMap` | ✅ 框架完成 | 待 Android 集成测试 |
| 通信架构 (Noise IK) | `CryptoCommLayer`, `NoiseHandshakeState`, `NoiseCipherState` | ✅ 已完成 | IK/XK 模式 |
| 隐蔽传输 | `CovertTransport`, `ConstantRateScheduler`, `WebTrafficCamouflage` | ✅ 已完成 | 流量填充 + JA4 伪装 |
| 账户恢复 | `RecoveryManager`, `MnemonicRecovery`, `SocialRecovery`, `NetworkRecovery` | ✅ 已完成 | 3种恢复 + 身份导入 |
| 启动流程 | `SplashScreen`, `WelcomeScreen`, `SplashViewModel` | ✅ 已完成 | BiometricPrompt 登录 + 创建/恢复入口 |
| 身份管理 | `IdentityManagementScreen`, `IdentityManagementViewModel` | ✅ 已完成 | 主/副账号列表 + 熔断 + 删除 |
| Node 连接 | `MyNodeScreen`, `MyNodeViewModel` | ✅ 已完成 | IP+端口+公钥+Noise握手预置 |
| Node GUI | `app.go`, `main.go` (Wails), `frontend/dist/` | ✅ 代码就绪 | 仪表盘+服务+网络+日志+设置 |
| Node 后端 | `api/server.go`, `noise/session.go`, `storage/provider.go`, `tss/signer.go` | ✅ 已完成 | Go/Gin/Pebble/Noise/TSS |

---

## 二、ZKP 模块

| 组件 | 文件 | 状态 |
|------|------|------|
| ZkpService 接口 | `domain/zkp/ZkpService.kt` | ✅ 已完成 |
| ZkpModels | `domain/zkp/ZkpModels.kt` | ✅ 已完成 |
| ZkpProverImpl | `domain/zkp/ZkpProverImpl.kt` | ✅ Mopro 集成（`generateCircomProof`） |
| ZkpVerifierImpl | `domain/zkp/ZkpVerifierImpl.kt` | ✅ Mopro 集成（`verifyCircomProof`） |
| MoproLib (JNI 桥接) | `domain/zkp/MoproLib.kt` | ✨ 新增 |
| CircuitPathProvider | `domain/zkp/CircuitPathProvider.kt` | ✨ 新增（assets→filesDir） |
| RootDetector | `domain/zkp/RootDetector.kt` | ✅ 已完成 |
| ZkpCacheManager | `domain/zkp/ZkpCacheManager.kt` | ✅ 已完成（1 小时 TTL） |
| CredentialPresentationZkp | `domain/vc/CredentialPresentationZkp.kt` | ✅ 已完成 |
| KdfsPatternView | `ui/zkp/KdfsPatternView.kt` | ✅ 可用 |
| HighRiskDialog | `ui/zkp/HighRiskDialog.kt` | ✅ 可用（30s 倒计时） |
| ZkpModule (DI) | `di/ZkpModule.kt` | ✅ 已完成 |

**已知限制**：Mopro JitPack 构建因仓库为纯 Rust 项目暂未成功，.so + Kotlin 绑定需通过 `mopro build android` CLI 生成。电路文件（multiplier.wasm + zkey）待通过 mopro CLI 生成后放入 `assets/circuit/`。旧文件 `ZkpServiceImpl.kt` 和 `ZkpNative.kt` 已移除。

---

## 三、代理重加密模块（PRE）

陵谦 2026-05-20 重写版本，基于 Dart `proxy_recrypt` 算法移植。

| 组件 | 文件 | 状态 |
|------|------|------|
| PreModels | `domain/crypto/PreModels.kt` | ✅ 已完成 |
| ProxyReEncryptionService 接口 | `domain/crypto/ProxyReEncryptionService.kt` | ✅ 已完成 |
| ProxyReEncryptionServiceImpl | `domain/crypto/ProxyReEncryptionServiceImpl.kt` | ✅ 已完成 |
| 单元测试 | `domain/crypto/ProxyReEncryptionServiceTest.kt` | ✅ 已完成 |

**算法**：P-256 ECDH 密钥协商 + AES-256-GCM 加密，依赖 SpongyCastle。

---

## 四、阈值签名模块（TSS）

| 组件 | 文件 | 状态 |
|------|------|------|
| ThresholdSignatureService | `domain/crypto/ThresholdSignatureService.kt` | ✅ 已完成 |
| BnbTssSignatureService | `tss/BnbTssSignatureService.kt` | ✅ 已完成（AAR 集成） |
| GoTssWrapper | `tss/GoTssWrapper.kt` | ✅ 已完成 |
| BluetoothTransceiver | `tss/message/BluetoothTransceiver.kt` | ✅ 已完成（BLE + CVE 安全） |
| MockTransceiver | `tss/message/MockTransceiver.kt` | ✅ 已完成 |
| ShareStorage | `tss/storage/ShareStorage.kt` | ✅ 已完成 |
| AndroidKeystoreShareStorage | `tss/storage/AndroidKeystoreShareStorage.kt` | ✅ 已完成（双层加密） |
| ShareEncryptionLayer | `tss/storage/ShareEncryptionLayer.kt` | ✅ 已完成 |
| 契约测试 | `androidTest/.../ThresholdSignatureContractTest.kt` | ✅ TSS-001~006 |
| 蓝牙测试 | `androidTest/.../BluetoothTransceiverTest.kt` | ✅ TSS-BLE-001~006 |

**库**：bnb-chain/tss-lib (Go) → gomobile AAR → Kotlin JNI，ECDSA (GG20)，secp256k1。

**演进路线**：当前 v3.1 (bnb-chain) → 未来 v4.0 评估 luxfi/threshold → 长期 v5.0 自研。

---

## 五、存储混淆模块

| 组件 | 文件 | 状态 |
|------|------|------|
| StorageLevel | `domain/storage/StorageLevel.kt` | ✅ 已完成 |
| Level1Obfuscator | `domain/storage/Level1Obfuscator.kt` | ✅ 已完成 |
| PathOramImpl | `domain/storage/PathOramImpl.kt` | ⏳ 框架（待 Android 集成测试） |
| OramBucket + Dao | `domain/storage/OramBucket.kt` | ✅ 已完成 |
| PositionMapEntry + Dao | `domain/storage/PositionMapEntry.kt` | ✅ 已完成 |
| PlainVaultItem | `domain/storage/PlainVaultItem.kt` | ✅ 已完成 |
| VaultItemEntity + VaultDao | `domain/storage/VaultItemEntity.kt` | ✅ 已完成 |

**Path ORAM 参数**：树高 10，桶大小 Z=4，Stash 上限 50，总桶数 2047。

**已修正**：`isOnPath`→`isBucketOnPath`，`writePath` 最深优先，`getLeafPositionForTest` 条件编译。

---

## 六、通信架构模块

| 组件 | 文件 | 状态 |
|------|------|------|
| TransportAdapter | `domain/communication/TransportAdapter.kt` | ✅ 已完成 |
| ServiceRelayAdapter | `domain/communication/ServiceRelayAdapter.kt` | ✅ 已完成（WS + HTTP） |
| LanTcpTransportAdapter | `domain/communication/LanTcpTransportAdapter.kt` | ✅ 已完成（局域网 WS） |
| CryptoCommLayer | `domain/communication/CryptoCommLayer.kt` | ✅ 已完成（Noise IK） |
| NoiseHandshakeState | `domain/communication/noise/NoiseHandshakeState.kt` | ✅ 已完成 |
| NoiseCipherState | `domain/communication/noise/NoiseCipherState.kt` | ✅ 已完成 |
| NoiseSymmetricState | `domain/communication/noise/NoiseSymmetricState.kt` | ✅ 已完成 |
| NoiseDH | `domain/communication/noise/NoiseDH.kt` | ✅ 已完成（X25519） |
| CommunicationLevel | `domain/communication/CommunicationLevel.kt` | ✅ 已完成（C0/C1/C2） |
| CovertTransport | `domain/communication/CovertTransport.kt` | ✅ 已完成 |
| CovertNegotiationDialog | `ui/covert/CovertNegotiationDialog.kt` | ✅ 已完成 |

**安全约束**：禁止 PSK（CVE-2026-24785），禁止网络查询公钥（MITM），解体失败不回滚。

---

## 七、账户恢复模块

| 组件 | 文件 | 状态 |
|------|------|------|
| RecoveryMethod | `domain/recovery/RecoveryMethod.kt` | ✅ 已完成 |
| MnemonicRecovery | `domain/recovery/MnemonicRecovery.kt` | ✅ 已完成（BIP-39 12词） |
| GuardianManager | `domain/recovery/GuardianManager.kt` | ✅ 已完成 |
| NodeTrustVerifier | `domain/recovery/NodeTrustVerifier.kt` | ✅ 已完成 |
| SocialRecovery | `domain/recovery/SocialRecovery.kt` | ✅ 已完成 |
| NetworkRecovery | `domain/recovery/NetworkRecovery.kt` | ✅ 已完成 |
| RecoveryManager | `domain/recovery/RecoveryManager.kt` | ✅ 已完成 |
| RecoveryScreen | `ui/feature/recovery/RecoveryScreen.kt` | ✅ 已完成（4种方式） |

---

## 八、UI 与 Node 集成

| 功能 | 文件 | 状态 |
|------|------|------|
| 启动页 + BioPrompt | `SplashScreen`, `SplashViewModel` | ✅ 已完成 |
| 欢迎页 | `WelcomeScreen` | ✅ 已完成 |
| 创建身份 | `CreateIdentityScreen`, `CreateIdentityViewModel` | ✅ 已完成 |
| 首页 + 抽屉 | `HomeScreen`, `SovexisDrawer`, `SovexisScaffold` | ✅ 已完成 |
| 身份管理 | `IdentityManagementScreen`, `IdentityManagementViewModel` | ✅ 已完成 |
| 支付 | `PaymentScreen`, `PaymentViewModel` | ✅ 已完成 |
| 保险箱 | `VaultScreen`, `VaultViewModel` | ✅ 已完成 |
| 凭证 | `CredentialScreen`, `CredentialViewModel` | ✅ 已完成 |
| 设置 | `SettingsScreen`, `SettingsViewModel` | ✅ 已完成 |
| 我的节点 | `MyNodeScreen`, `MyNodeViewModel` | ✅ 已完成 |
| 恢复 | `RecoveryScreen`, `RecoveryViewModel` | ✅ 已完成 |
| Node CLI | `cmd/sovexis-node/main.go` | ✅ 已完成 |
| Node API | `internal/api/server.go` | ✅ 已完成（12 端点） |
| Node GUI | `main.go`, `app.go`, `frontend/dist/` | ✅ 代码就绪 |

---

## 九、待办事项

| 优先级 | 任务 | 说明 |
|--------|------|------|
| P1 | App ↔ Node WebSocket 联调 | `LanTcpTransportAdapter` 替换 `HttpURLConnection` |
| P1 | Node 端 WebSocket `/ws` 端点 | 当前不包含 WS 路由 |
| P1 | ZKP Mopro 集成 ✅ | 已完成：Mopro FFI v0.3.6，`cargo ndk` 交叉编译 `libsovexis_zkp.so`，纯 Rust witness，端到端 prove/verify 链路打通 |
| P1 | 测试电路替换 | 当前使用 `multiplier2_wc` 测试电路，需密码学专家编写四元承诺电路 |
| P2 | Path ORAM Android 集成测试 | 树初始化 + 读写一致性验证 |
| P2 | TSS 蓝牙真机联调 | BLE 配对完成流程 |
| P2 | Node 开机自启 + 系统托盘 | Windows 注册表 + systray |
| P3 | AI 推理服务 | Node 端部署私有模型 |
| P3 | 服务商适配器真实接入 | DeepSeek 等真实 API |

---

## 十、版本历史

| 版本 | 日期 | 变更 | 作者 |
|------|------|------|------|
| 1.0~1.6 | 2026-05-09~05-20 | PRE + TSS + Path ORAM 框架 | aicoder + 陵谦 |
| v2.0~2.2 | 2026-05-22 | Noise IK + CovertTransport + TSS AAR 集成 | aicoder + 陵谦 |
| v2.3 | 2026-05-24 | TSS 模块合并至 :app + 文档审计 | aicoder + 陵谦 |
| v3.0 | 2026-05-29 | 重构为"实现状态记录"；新增 Splash/Welcome/IdentityManagement/MyNode/Node GUI/Node docs 共 10+ 模块 | Texno + 陵谦 |
| v3.1 | 2026-06-03 | Mopro ZKP 集成收尾：.so 交叉编译、JitPack 依赖移除、wasm 引用清理、端到端验证、技术债务记录 | Texno |

---
## 十一、ZKP 模块技术债务

| 债务项 | 说明 | 优先级 | 预计版本 |
|--------|------|--------|----------|
| 测试电路替换 | 当前使用 `multiplier2_wc` 测试电路 (c = a×b)，需密码学专家编写四元承诺电路 | P1 | v2.3.0 |
| JitPack 依赖移除 | Mopro 纯 Rust 无 AAR，改用 `cargo ndk` 离线编译 + 手动部署 `.so` (NDK 30.0.14904198, arm64-v8a) | P2 | 待 Mopro 官方发布 Android AAR |
| wasm 引用清理 | 已清理 wasm 路径引用，保留注释说明 | — | 已完成 |

---
**维护者**：Sovexis 架构组
