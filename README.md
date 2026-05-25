# Sovexis · 个人数字主权基座

> **定义权与定价权不应属于任何政治厂商。每一个个体都应拥有自己的数字身份、自己的加密保险箱、自己的支付通道、自己的AI代理人——无需许可，不可剥夺。**

---

## 核心原则

1. **主权优先于便利**：任何功能不得以牺牲用户数据控制权为代价
2. **可验证信任**：开源、密码学证明，无需信任任何中心化实体
3. **协议即宪法**：治理规则由协议层硬编码，杜绝人为特权
4. **高约束成本设计**：违规在签名前被阻断，让犯错成本远高于守规成本
5. **成本最小化与市场驱动**：数据流与计算存储由竞争性服务商提供，Sovexis 仅做身份与支付路由

---

## 技术架构总览

| 模块 | 核心功能 | 技术栈 | 状态 |
|------|----------|--------|------|
| 身份模块 | 主账号生物绑定（WebAuthn）、副账号 BIP-32 派生 | Android Keystore, WebAuthn | ✅ 已实现 |
| 策略引擎 | 副账号权限配置、冲突检测、操作前强制检查 | Kotlin, kotlinx.serialization | ✅ 已实现 |
| 支付模块 | 交易构造与 ECDSA 签名、模拟账本、策略限额约束 | Java Crypto, Room | ✅ 已实现 |
| 保险箱 | 端到端加密笔记存储（AES-GCM + HKDF）、Room 持久化 | AES-GCM, Room | ✅ 已实现 |
| 可验证凭证 | 凭证签发/验证、二维码分享、W3C VC 轻量实现 | ZXing, kotlinx.serialization | ✅ 已实现 |
| 本地 Agent API | 供个人 AI 调用的 AIDL 接口，支持需索审批 | AIDL, Service | ✅ 已实现 |
| 服务商适配器 | 预留第三方服务接入接口（AI 模型、数据、法币） | 抽象接口 | 🔄 预留 |
| TSS 阈值签名 | 2-of-2 阈值签名、蓝牙传输、密钥份额管理 | bnb-chain/tss-lib | ✅ 已实现 |
| ZKP 零知识证明 | 身份验证隐私保护、选择性披露 | 缓存策略 | 🔄 部分实现 |

---

## 当前状态

**Sovexis v2.1.0（Android MVP 框架）**

- 目标平台：Android 15 (API 35)
- 架构：Jetpack Compose + MVVM + Hilt 依赖注入
- 安全：Android Keystore StrongBox + BiometricPrompt

### 测试统计

```
66 测试，59 通过，7 已知算法占位
```

---

## 快速开始

### 环境要求

- Android Studio Hedgehog | 2024.1.1+
- Kotlin 1.9.22
- Gradle 8.2
- Android SDK 35
- 目标设备：Android 15 真机或模拟器（需支持指纹/面部识别）

### 构建

```bash
# 克隆项目
git clone <repository-url>
cd Sovexis

# 使用 Android Studio 打开项目
# 或使用命令行
./gradlew assembleDebug
```

### 运行测试

```bash
# 运行所有单元测试
./gradlew test

# 运行特定模块测试
./gradlew :app:test
./gradlew :domain:test
```

### 安装到设备

```bash
# 连接 Android 设备后
./gradlew installDebug
```

---

## 项目结构

```
Sovexis/
├── app/                          # 主应用模块
│   ├── src/main/java/com/sovexis/mobile/
│   │   ├── ui/                   # Jetpack Compose UI
│   │   ├── di/                   # Hilt 依赖注入配置
│   │   └── SovexisApplication.kt # 应用入口
│   └── build.gradle.kts
├── domain/                       # 领域层（业务逻辑）
├── data/                         # 数据层（存储、网络）
├── impl-tss-bnblib/             # TSS 阈值签名实现
├── docs/                         # 架构文档
│   ├── AGENTS.md                 # 任务调度中枢
│   ├── PROJECT_OVERVIEW.md       # 项目起源与愿景
│   ├── MODULES.md                # 模块索引与接口契约
│   ├── BUILD.md                  # 构建指南
│   ├── *_SPEC.md                 # 各模块详细规格
│   └── ...
└── README.md                     # 本文件
```

---

## 相关文档

| 文档 | 描述 |
|------|------|
| [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) | 项目起源与愿景 |
| [docs/AGENTS.md](docs/AGENTS.md) | 任务调度中枢 |
| [docs/MODULES.md](docs/MODULES.md) | 模块索引与接口契约 |
| [docs/BUILD.md](docs/BUILD.md) | 构建指南 |
| [docs/IDENTITY_SPEC.md](docs/IDENTITY_SPEC.md) | 身份模块规格 |
| [docs/POLICY_SPEC.md](docs/POLICY_SPEC.md) | 策略引擎规格 |
| [docs/PAYMENT_SPEC.md](docs/PAYMENT_SPEC.md) | 支付模块规格 |
| [docs/VAULT_SPEC.md](docs/VAULT_SPEC.md) | 保险箱模块规格 |
| [docs/CREDENTIAL_SPEC.md](docs/CREDENTIAL_SPEC.md) | 可验证凭证模块规格 |
| [docs/AGENT_API_SPEC.md](docs/AGENT_API_SPEC.md) | 本地 Agent API 规格 |
| [docs/ADAPTER_SPEC.md](docs/ADAPTER_SPEC.md) | 服务商适配器接口规范 |
| [docs/UI_FLOW_SPEC.md](docs/UI_FLOW_SPEC.md) | UI 交互流程与视觉规范 |
| [docs/INPUT_INTERFACE_SPEC.md](docs/INPUT_INTERFACE_SPEC.md) | 用户输入接口规范 |
| [docs/TEST_BOUNDARY_RULES.md](docs/TEST_BOUNDARY_RULES.md) | 测试边界守则 |
| [docs/TSS_MIGRATION_GUIDE.md](docs/TSS_MIGRATION_GUIDE.md) | TSS 模块迁移指南 |
| [docs/IDENTITY_CREDENTIAL_BINDING.md](docs/IDENTITY_CREDENTIAL_BINDING.md) | 身份与凭证绑定关系 |

---

## 安全声明

- 私钥必须存储在 Android Keystore 中，不得明文落盘
- 所有加密操作使用 `javax.crypto` 或 `android.security.keystore`
- 支付签名前必须调用策略检查
- P0 和 P1 级别的输入页面设置 `FLAG_SECURE` 防止录屏

---

## 许可证

```
Copyright 2026 Sovexis Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

部分组件采用 MIT 许可证，详见各模块源码文件头。

---

## 联系我们

- **架构组**: architecture@sovexis.org
- **安全团队**: security@sovexis.org
- **Issue 追踪**: https://github.com/sovexis/mobile/issues

---

**"我们不是在开发一个 App，而是在起草一份数字世界的独立宣言。"**

*— ringform，2026*
