# BUILD.md - Sovexis Android MVP 构建指南 v1.0

## 环境要求
- Android Studio Hedgehog | 2024.1.1+
- Kotlin 1.9.22
- Gradle 8.2
- Android SDK 35
- 目标设备：Android 15 真机或模拟器（需支持指纹/面部识别）

---

## 2. 项目结构
Sovexis/
├── app/src/main/java/com/sovexis/
│ ├── identity/
│ ├── policy/
│ ├── payment/
│ ├── vault/
│ ├── credential/
│ ├── agent/
│ ├── adapters/
│ ├── security/
│ ├── ui/
│ └── data/
├── app/src/main/res/
└── build.gradle.kts
---

## 3. 关键依赖

```kotlin
dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // QR Code
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

```

## 4. 构建与运行

- 克隆项目，用 Android Studio 打开。

- 同步 Gradle。

- 连接 Android 15 设备或启动模拟器（需设置指纹）。

- 点击 Run。

## 5. 签名配置

- 使用 debug 签名即可。

## 6. 已知限制

- WebAuthn 部分模拟器不可用，建议真机测试。

- 服务商适配器目前仅模拟。

# 构建版本：1.0

- 最后更新：2026-04-12
- 维护者：Sovexis 架构组