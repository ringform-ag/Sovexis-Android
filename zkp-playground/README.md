# Sovexis ZKP Playground

[AI-GENERATED]
生成时间: 2026-05-20
实现状态: FRAMEWORK - 框架文件，待人工完善

## 概述

Sovexis ZKP Playground 是基于 [arkworks-rs](https://github.com/arkworks-rs) 的零知识证明 (ZKP) 原生库，使用 Groth16 zkSNARK 协议。本库编译为 Android 动态链接库 (.so)，通过 JNI 供 Kotlin 层调用。

### 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| ZKP 协议 | Groth16 zkSNARK | - |
| 密码学库 | arkworks-rs | 0.6.0 |
| 椭圆曲线 | BN254 | 0.6.0 |
| 编程语言 | Rust | 2021 edition |
| JNI 桥接 | jni-rs | 0.21.1 |
| 目标平台 | Android (aarch64, armv7) | API 30+ |

### 项目结构

```
zkp-playground/
  Cargo.toml              # Rust 项目配置
  src/
    lib.rs                # 库入口，模块导出，JNI_OnLoad
    groth16_native.rs     # Groth16 原生实现 (setup/prove/verify)
    jni_bridge.rs         # JNI 桥接层 (Java <-> Rust 类型转换)
```

## 依赖安装

### 1. 安装 Rust 工具链

```bash
# 安装 rustup (如果尚未安装)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# 安装稳定版工具链
rustup install stable
rustup default stable

# 添加 Android 目标
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
```

### 2. 安装 Android NDK

```bash
# 通过 Android Studio SDK Manager 安装 NDK
# 或通过命令行:
sdkmanager "ndk;26.1.10909125"

# 设置环境变量
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/26.1.10909125
```

### 3. 配置 Cargo 交叉编译

创建或编辑 `~/.cargo/config.toml`:

```toml
# Android NDK 工具链配置
# 请根据实际 NDK 安装路径和 API 级别调整

[target.aarch64-linux-android]
linker = "PATH_TO_NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android30-clang"
rustflags = ["-C", "link-arg=-Wl,--no-rosegment"]

[target.armv7-linux-androideabi]
linker = "PATH_TO_NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/armv7a-linux-androideabi30-clang"
rustflags = ["-C", "link-arg=-Wl,--no-rosegment"]

# Linux 示例路径
# linker = "/home/user/Android/Sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang"

# macOS 示例路径
# linker = "/Users/user/Library/Android/sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android30-clang"

# Windows 示例路径 (MSYS2/Cygwin)
# linker = "C:/Users/user/AppData/Local/Android/Sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android30-clang.cmd"
```

## 构建步骤

### 宿主机构建 (开发/测试)

```bash
cd zkp-playground

# Debug 构建
cargo build

# Release 构建
cargo build --release

# 运行测试
cargo test
```

### Android 交叉编译

```bash
cd zkp-playground

# 编译 aarch64 (大多数现代 Android 设备)
cargo build --release --target aarch64-linux-android

# 编译 armv7 (旧设备兼容)
cargo build --release --target armv7-linux-androideabi
```

编译产物位置:
- `target/aarch64-linux-android/release/libsovexis_zkp.so`
- `target/armv7-linux-androideabi/release/libsovexis_zkp.so`

## Android 集成指南

### 1. 复制 .so 文件

将编译产物复制到 Android 项目的 jniLibs 目录:

```
app/src/main/jniLibs/
  arm64-v8a/
    libsovexis_zkp.so      # aarch64
  armeabi-v7a/
    libsovexis_zkp.so      # armv7
```

### 2. Kotlin JNI 接口

在 Kotlin 代码中声明 native 方法:

```kotlin
package com.sovexis.mobile.domain.zkp

object ZkpNative {
    init {
        System.loadLibrary("sovexis_zkp")
    }

    external fun setup(circuitType: String): Array<ByteArray>
    external fun prove(
        pk: ByteArray,
        privateInputs: ByteArray,
        publicInputs: ByteArray
    ): ByteArray
    external fun verify(
        vk: ByteArray,
        proof: ByteArray,
        publicInputs: ByteArray
    ): Boolean
    external fun getLibVersion(): String
    external fun cleanup(prepareResultId: String)
}
```

### 3. 使用示例

```kotlin
// 1. 可信设置
val (pkBytes, vkBytes) = ZkpNative.setup("bio_auth")

// 2. 生成证明
val privateInputs = """{"secret_age": 25}""".toByteArray(Charsets.UTF_8)
val publicInputs = """{"min_age": 18}""".toByteArray(Charsets.UTF_8)
val proofBytes = ZkpNative.prove(pkBytes, privateInputs, publicInputs)

// 3. 验证证明
val isValid = ZkpNative.verify(vkBytes, proofBytes, publicInputs)
println("证明验证结果: $isValid")

// 4. 清理
ZkpNative.cleanup("prepare_result_id")
```

### 4. Gradle 配置 (可选 - 自动化构建)

在 `app/build.gradle.kts` 中添加:

```kotlin
android {
    // ...
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

// 可选: 使用 cargo-ndk 自动化构建
tasks.register("buildZkpNative") {
    exec {
        workingDir = file("../zkp-playground")
        commandLine("cargo", "ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a", "-o", "../app/src/main/jniLibs", "build", "--release")
    }
}
```

## 性能参考

| 操作 | BN254 简单电路 | BN254 10k 约束 |
|------|---------------|----------------|
| Setup | ~1-2 秒 | ~10-30 秒 |
| Prove | ~1-3 秒 | ~5-15 秒 |
| Verify | ~1-5 毫秒 | ~1-5 毫秒 |
| 证明体积 | ~200 字节 | ~200 字节 |

## 安全注意事项

1. **ProvingKey 保密**: ProvingKey 必须安全存储，泄露后任何人都可以伪造证明
2. **可信设置**: 生产环境应使用 MPC 仪式 (MPC Ceremony) 进行去中心化可信设置
3. **随机数**: 确保使用密码学安全的随机数生成器
4. **内存安全**: Rust 的所有权系统保证了内存安全，但仍需注意 JNI 边界

## 人工完善清单

- [ ] 设计具体的 R1CS 电路（生物认证、DID 证明等）
- [ ] 实现私有输入/公共输入的 JSON 解析
- [ ] 添加范围证明 gadget (ark_r1cs_std)
- [ ] 性能基准测试与优化
- [ ] 可信设置安全审计
- [ ] ProvingKey 安全存储方案
- [ ] 错误处理完善
- [ ] 集成测试

## 参考资料

- [Groth16 论文](https://eprint.iacr.org/2016/260)
- [arkworks-rs 文档](https://docs.rs/ark-groth16/0.6.0/)
- [arkworks-rs GitHub](https://github.com/arkworks-rs)
- [jni-rs 文档](https://docs.rs/jni/0.21.1/)
- [Microsoft Crescent](https://www.microsoft.com/en-us/research/project/crescent/)
- [BN254 曲线](https://eips.ethereum.org/EIPS/eip-196)
