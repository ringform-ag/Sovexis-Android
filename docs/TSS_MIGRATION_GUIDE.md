# Sovexis 阈值签名 (TSS) 模块迁移指南

> 维护者：Sovexis 架构组  
> 创建日期：2026-05-20  
> 编写者：陵谦 + AI Coder  
> 许可证：Apache 2.0

---

## 一、当前实现信息

### 1.1 库信息

| 属性 | 值 |
|------|-----|
| **库名称** | bnb-chain/tss-lib |
| **许可证** | MIT |
| **审计状态** | Kudelski (2020) |
| **集成方式** | Go → gomobile AAR → Kotlin JNI 封装 |
| **签名算法** | ECDSA (GG20) |
| **密钥曲线** | secp256k1 |
| **包体积增量** | 15-25 MB |

### 1.2 模块结构

```
impl-tss-bnblib/
├── build.gradle.kts                          # 模块构建配置
├── libs/
│   ├── README.md                             # AAR 编译说明
│   └── tss-lib.aar (待放置)                  # gomobile 编译的 AAR
└── src/
    ├── main/java/com/sovexis/tss/
    │   ├── BnbTssSignatureService.kt         # ThresholdSignatureService 实现
    │   ├── GoTssWrapper.kt                   # gomobile AAR 的 Kotlin 封装
    │   ├── message/
    │   │   ├── BluetoothTransceiver.kt       # 蓝牙传输实现
    │   │   ├── MockTransceiver.kt            # 模拟传输（测试用）
    │   │   └── WifiTransceiver.kt (TODO)     # WiFi/局域网传输
    │   ├── storage/
    │   │   ├── ShareStorage.kt               # 存储接口
    │   │   └── AndroidKeystoreShareStorage.kt # Android Keystore 实现
    │   └── model/ (数据模型在 domain 层定义)
    └── test/java/com/sovexis/tss/
        └── ThresholdSignatureContractTest.kt # 契约测试套件
```

---

## 二、迁移触发条件

触发以下任一条件时，应考虑迁移：

| 条件 | 说明 |
|------|------|
| **维护状态** | tss-lib 停止维护超过 12 个月 |
| **安全漏洞** | 发现未修复的安全漏洞 |
| **性能需求** | 需要更优的移动端性能（如 luxfi 的 5ms 签名） |
| **功能需求** | luxfi/threshold 完成 P-256 支持并经过审计 |
| **自主可控** | 需要完全自主控制的实现 |

---

## 三、备选方案对比

| 方案 | 许可证 | 性能 | 审计状态 | 成熟度 | 备注 |
|------|--------|------|----------|--------|------|
| **bnb-chain/tss-lib** (当前) | MIT | 中等 | Kudelski (2020) | ⭐⭐⭐⭐⭐ | 稳定，文档完善 |
| **luxfi/threshold** | MIT | 优秀 (5ms) | 待审计 | ⭐⭐⭐ | 2-of-2 优化，观察中 |
| **自研 2P-ECDSA** | 自主 | 待定 | 需审计 | ⭐⭐ | 长期目标 |

---

## 四、迁移步骤

### 步骤 1: 创建新模块

```bash
# 复制当前模块结构
cp -r impl-tss-bnblib impl-tss-luxfi

# 更新 build.gradle.kts
# - 修改 namespace = "com.sovexis.tss.luxfi"
# - 更新依赖（如果需要）
```

### 步骤 2: 实现 ThresholdSignatureService 接口

```kotlin
// impl-tss-luxfi/src/main/java/com/sovexis/tss/luxfi/
// LuxfiTssSignatureService.kt

@Singleton
class LuxfiTssSignatureService @Inject constructor(
    private val luxfiWrapper: LuxfiWrapper,
    private val shareStorage: ShareStorage
) : ThresholdSignatureService {
    // 实现接口方法...
}
```

### 步骤 3: 运行契约测试

```bash
./gradlew :impl-tss-luxfi:test
```

**必须全部通过以下测试：**

| 测试 ID | 测试名称 | 说明 |
|---------|----------|------|
| TSS-001 | testKeygenProducesValidKeyShare | 密钥份额生成 |
| TSS-002 | test2of2SigningCompletes | 2-of-2 签名完成 |
| TSS-003 | testSignatureVerifiesCorrectly | 签名验证 |
| TSS-004 | testWrongShareCannotProduceValidSig | 错误份额检测 |
| TSS-005 | testPartialSignWithMissingShareFails | 缺失份额处理 |
| TSS-006 | testDeleteShareRemovesKeyMaterial | 安全删除 |

### 步骤 4: 性能基准测试

记录以下指标并与旧实现对比：

```kotlin
// 密钥生成时间
val keygenTime = measureTimeMillis {
    service.generateKeyShares(transceiver)
}

// 签名时间
val signTime = measureTimeMillis {
    service.partialSign(data, transceiver)
}

// 合并时间
val combineTime = measureTimeMillis {
    service.combineSignatures(local, remote)
}
```

### 步骤 5: 更新 DI 绑定

修改 `app/src/main/java/com/sovexis/mobile/di/DomainModule.kt`：

```kotlin
@Binds
@Singleton
abstract fun bindThresholdSignatureService(
    impl: LuxfiTssSignatureService  // 改为新实现
): ThresholdSignatureService
```

### 步骤 6: 折旧旧模块

在 `impl-tss-bnblib` 中添加 `@Deprecated` 注解：

```kotlin
@Deprecated(
    message = "已迁移至 impl-tss-luxfi，将在 v3.3 中删除",
    replaceWith = ReplaceWith("com.sovexis.tss.luxfi.LuxfiTssSignatureService"),
    level = DeprecationLevel.WARNING
)
class BnbTssSignatureService { ... }
```

保留 2 个版本周期后删除旧模块。

---

## 五、文件变更检查清单

| 操作 | 文件路径 |
|------|----------|
| 新增 | `impl-tss-luxfi/` (完整模块) |
| 修改 | `app/src/main/java/com/sovexis/mobile/di/DomainModule.kt` |
| 验证 | `impl-tss-bnblib/src/test/.../ThresholdSignatureContractTest.kt` |
| 更新 | `docs/TSS_MIGRATION_GUIDE.md` |
| 删除 | `impl-tss-bnblib/` (2 版本后) |

---

## 六、AAR 编译指南

### 6.1 环境准备

```bash
# 1. 安装 Go 1.20+
# 访问 https://golang.org/dl/ 下载安装

# 2. 安装 gomobile
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init

# 3. 设置 ANDROID_HOME 环境变量
export ANDROID_HOME=/path/to/android/sdk
```

### 6.2 编译 tss-lib

```bash
# 1. 克隆仓库
git clone https://github.com/bnb-chain/tss-lib.git
cd tss-lib

# 2. 编译为 AAR
gomobile bind -target=android -o tss-lib.aar ./ecdsa/

# 3. 复制到项目
cp tss-lib.aar /path/to/Sovexis/impl-tss-bnblib/libs/
```

### 6.3 更新依赖

在 `impl-tss-bnblib/build.gradle.kts` 中取消注释：

```kotlin
dependencies {
    implementation(files("libs/tss-lib.aar"))
    // ...
}
```

---

## 七、演进路线

```
当前 (v3.1): bnb-chain/tss-lib (Go via gomobile)
    ↓
未来 (v4.0): 评估迁移至 luxfi/threshold
    - 条件：luxfi 完成 P-256 支持并经过审计
    - 优势：5ms 签名速度（当前约 50-100ms）
    ↓
长期 (v5.0+): 可选迁移至自研 2P-ECDSA
    - 条件：安全性需要完全自主控制
    - 投入：约 6 个月开发 + 审计
```

---

## 八、联系方式

如有迁移相关问题，请联系：

- **架构组**: wierbluce@outlook.com

---

**本指南为 Sovexis 项目阈值签名模块迁移的官方参考文档。**
