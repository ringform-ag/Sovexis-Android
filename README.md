# Sovexis · 个人数字主权基座

**定义权与定价权不应属于任何政治厂商。**

Sovexis 是一个完全去中心化、端到端加密、开源可信的个人数字主权运行时环境。它运行在 Android 设备上，让你夺回对身份、数据、支付和社交关系的定义权与定价权。

**Sovexis 不是一个 App，而是一个协议生态。**


## 核心原则

- **主权优先于便利**：任何功能不得以牺牲用户数据控制权为代价
- **可验证信任**：开源、密码学证明，无需信任任何中心化实体
- **协议即宪法**：治理规则由协议层硬编码，杜绝人为特权
- **存在即消失**：密码学混淆使得操作系统无法区分用户正在使用 Sovexis 还是运行随机噪声


## 技术架构

| 模块 | 功能 | 状态 |
|------|------|------|
| 身份 | DID + BIP-32 副账号 + 凭证通道 | ✅ |
| 策略引擎 | JSON 权限配置 + 冲突检测 | ✅ |
| 支付 | ECDSA 签名 + 累计限额 | ✅ |
| 保险箱 | AES-GCM + Path ORAM 存储混淆 | ✅ |
| 代理重加密 | 基于 ECDH 的密文转换 | ✅ |
| 凭证 | W3C VC + 委派凭证体系 | ⚠️ |
| ZKP | 四元承诺 Groth16 证明 | ⚠️ 待 Mopro 集成 |
| TSS | 2P-ECDSA 门限签名 | ✅ |
| 通信 | Noise IK/XK + 隐蔽传输 | ✅ |
| 恢复 | 三条路径可选 | ⚠️ |
| Sovexis Node | 加密存储 + TSS 协同 + 信任评分 | ✅ |


## 当前状态

```
Sovexis v2.1.0（Android MVP 框架）
Android 端编译通过，Node 端全部就绪
66 测试，59 通过，7 已知算法占位
```


## 快速开始

**环境要求**：Android Studio Otter 3+, JDK 17, Gradle 8.7, Go 1.25+

```bash
# 构建 Android 端
./gradlew :app:assembleDebug

# 运行测试
./gradlew :app:testDebugUnitTest

# 构建 Node 端
cd sovexis-node && go build ./cmd/sovexis-node
```


## 许可证

Apache 2.0


## 相关文档

- [白皮书](WHITEPAPER.md)
- [架构规范](AI_IMPLEMENTATION_SPEC.md)
- [贡献指南](CONTRIBUTING.md)


## 联系我们

- 创建者：ringform
- 架构师：陵谦
- 仓库：https://github.com/ringform-ag/Sovexis
