# BUILD.md — Sovexis Android MVP 构建指南 v3.0

## 环境要求
- Android Studio Hedgehog | 2024.1.1+
- Kotlin 1.9.22
- Gradle 8.2
- compileSdk 34, minSdk 30 (Android 11+), targetSdk 34
- 目标设备：Android 15 真机或模拟器（需支持指纹/面部识别）

## 项目结构（当前实际）

```
Sovexis/app/src/main/java/com/sovexis/
├── di/
│   ├── AppModule.kt            # Hilt：数据库、网络、SharedPreferences
│   ├── DomainModule.kt         # Hilt：所有领域层依赖
│   └── ZkpModule.kt            # Hilt：ZKP 相关依赖
├── domain/
│   ├── identity/               # IdentityManager, MasterIdentity, ChildIdentity, SovexisAccount
│   ├── policy/                 # PolicyEnforcer（策略执行 + 熔断）
│   ├── payment/                # PaymentManager
│   ├── did/                    # DidService（DID 生成与解析）
│   ├── crypto/                 # KeyManager, ThresholdSignatureService, ProxyReEncryption
│   ├── vc/                     # CredentialService, CredentialPresentationZkp
│   ├── zkp/                    # ZkpService, ZkpCacheManager, ZkpModels
│   ├── storage/                # VaultDao, StorageObfuscator, PathOram, Level1Obfuscator
│   ├── recovery/               # RecoveryManager, MnemonicRecovery, SocialRecovery, GuardianManager
│   └── communication/          # CryptoCommLayer, TransportAdapter, ServiceRelayAdapter, LanTcpTransportAdapter
├── tss/                        # BnbTssSignatureService, ShareStorage, BluetoothTransceiver
├── ui/
│   ├── feature/
│   │   ├── splash/             # SplashScreen + BiometricPrompt 登录
│   │   ├── welcome/            # WelcomeScreen（创建/恢复入口）
│   │   ├── onboarding/         # CreateIdentityScreen（助记词 3 列网格）
│   │   ├── home/               # 首页 + 抽屉导航
│   │   ├── identity/           # IdentityManagementScreen（主/副账号列表+熔断+删除）
│   │   ├── payment/            # PaymentScreen + 高风险弹窗 + ZKP 流程
│   │   ├── vault/              # VaultScreen（保险箱）
│   │   ├── safebox/            # SafeBoxScreen（旧兼容）
│   │   ├── credentials/        # CredentialsScreen
│   │   ├── credential/         # CredentialScreen（凭证详情）
│   │   ├── settings/           # SettingsScreen（8开关组：存储/通信/TSS/KDFS/隐蔽传输）
│   │   ├── mynode/             # MyNodeScreen（IP+端口+公钥+连接+3服务开关+Noise状态）
│   │   ├── recovery/           # RecoveryScreen（助记词/社交/网络/身份导入 4种恢复）
│   │   └── about/              # AboutScreen
│   ├── components/             # SovexisScaffold, SovexisDrawer, SovexisBiometricPrompt, etc.
│   ├── navigation/             # SovexisRoute（19路由）+ SovexisNavHost
│   └── theme/                  # Color, Theme, Type
└── platform/                   # SovexisApplication (Hilt), MainActivity
```

## 关键依赖

```kotlin
dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DI
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Network
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    implementation("io.ktor:ktor-client-websockets:2.3.7")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

## 构建与运行

- 用 Android Studio 打开 `Sovexis/` 目录
- 同步 Gradle
- 连接 Android 设备或启动模拟器
- 点击 Run

## 当前模块实现状态

| 模块 | 状态 |
|------|------|
| 身份（IdentityManager） | ✅ 已实现（DidService + IdentityManagerImpl） |
| 策略引擎（PolicyEnforcer） | ✅ 已实现（含熔断机制） |
| 支付（PaymentManager） | ✅ 已实现（含 ZKP + TSS 流程） |
| 保险箱（VaultManager） | ✅ 已实现 |
| 凭证（CredentialService） | ✅ 已实现（含 ZKP 选择性披露） |
| 恢复（RecoveryManager） | ✅ 已实现（4种恢复方式 + 身份导入） |
| TSS 阈值签名 | ✅ 已实现（2P-ECDSA + 蓝牙传输） |
| ZKP 零知识证明 | ✅ 已实现（ZkpModule 独立管理） |
| 通信（CryptoCommLayer + LanTcp） | ✅ 已实现（Noise IK + WebSocket） |
| 启动流程（Splash + Welcome + BioPrompt） | ✅ 已实现 |
| 副账号管理（IdentityManagementScreen） | ✅ 已实现（列表+创建+熔断+删除） |
| 设置（SettingsScreen） | ✅ 已实现（8开关组） |
| Node 连接（MyNodeScreen） | ✅ 已实现（IP+端口+公钥+连接+服务开关） |

## 签名配置

- Debug 签名即可

## Sovexis Node（桌面端）

参见 `Sovexis node/docs/BUILD.md`

# 构建版本：3.0
- 最后更新：2026-05-29
- 维护者：Sovexis 架构组
