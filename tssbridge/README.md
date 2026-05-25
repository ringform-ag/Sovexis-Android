# Sovexis TSS Bridge

Go 中间层项目，用于封装 bnb-chain/tss-lib 的复杂 API，使其可以通过 gomobile 编译为 Android AAR。

## 项目结构

```
tssbridge/
├── go.mod              # Go 模块定义
├── bridge.go           # 暴露给 gomobile 的导出函数（7个函数）
├── state.go            # 会话状态管理（线程安全）
├── keygen.go           # 密钥生成协议逻辑
├── signing.go          # 签名协议逻辑
├── serializer.go       # 消息序列化/反序列化
└── README.md           # 本文件
```

## 导出函数

| 函数 | 说明 |
|------|------|
| `StartKeygen` | 启动密钥生成协议，返回第一条待发送消息 |
| `ProcessKeygenMessage` | 处理对端密钥生成消息，返回下一条消息或 nil |
| `GetKeygenResult` | 获取密钥生成结果（本地份额） |
| `StartSigning` | 启动签名协议，返回第一条待发送消息 |
| `ProcessSigningMessage` | 处理对端签名消息，返回下一条消息或 nil |
| `GetSignatureResult` | 获取完整 ECDSA 签名 |
| `CleanupSession` | 清理会话状态，释放内存 |

## 编译步骤

### 1. 环境准备

```bash
# 安装 Go 1.21+
# 下载地址：https://go.dev/dl/

# 安装 gomobile
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest

# 初始化 gomobile
gomobile init

# 设置环境变量（重要！）
export GOFLAGS=-mod=mod
export GOPROXY=https://goproxy.cn,direct  # 国内用户加速
```

### 2. 下载依赖

```bash
cd tssbridge
go mod tidy
```

### 3. 编译 AAR

```bash
# 编译为 AAR（全架构）
gomobile bind -target=android -o ../app/libs/tssbridge.aar .

# 或仅编译特定架构（减小体积）
gomobile bind -target=android/arm64 -o ../app/libs/tssbridge-arm64.aar .
```

### 4. 验证编译结果

```bash
# 检查 AAR 是否包含 .so 文件
unzip -l app/libs/tssbridge.aar | grep "\.so"

# 预期输出：
# jni/armeabi-v7a/libgojni.so
# jni/arm64-v8a/libgojni.so
```

## 集成到 Android

### 1. 添加依赖

在 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(files("libs/tssbridge.aar"))
}
```

### 2. ProGuard 规则

在 `proguard-rules.pro` 中添加：

```proguard
-keep class sovexis.tssbridge.** { *; }
-keep class go.** { *; }
```

### 3. Kotlin 调用示例

```kotlin
import sovexis.tssbridge.Tssbridge

// 启动密钥生成
val firstMsg = Tssbridge.startKeygen("session1", "alice", "bob")

// 处理对端消息
val nextMsg = Tssbridge.processKeygenMessage("session1", peerMsgBytes)

// 获取结果
val result = Tssbridge.getKeygenResult("session1")

// 清理会话
Tssbridge.cleanupSession("session1")
```

## 注意事项

1. **GOFLAGS**: 必须设置 `export GOFLAGS=-mod=mod`，否则可能报 vendor 错误
2. **Go 版本**: 必须使用 Go 1.21+，低版本不支持 gomobile 的部分特性
3. **NDK**: gomobile 需要 Android NDK，可通过 Android Studio SDK Manager 安装
4. **架构**: gomobile 不支持 x86（32位），仅支持 arm、arm64、amd64
5. **AAR 体积**: 全架构约 15-25MB，单个架构约 5-8MB

## 协议流程

### 密钥生成 (GG20 Keygen)

1. 本地调用 `StartKeygen` 启动协议，获取第一条消息
2. 将消息发送给对端
3. 收到对端消息后，调用 `ProcessKeygenMessage`
4. 如果返回 nil，协议结束；否则继续发送消息给对端
5. 协议结束后，调用 `GetKeygenResult` 获取本地份额

### 签名 (GG20 Signing)

1. 本地调用 `StartSigning` 启动协议，传入待签名数据（32字节哈希）
2. 将消息发送给对端
3. 收到对端消息后，调用 `ProcessSigningMessage`
4. 如果返回 nil，协议结束；否则继续发送消息给对端
5. 协议结束后，调用 `GetSignatureResult` 获取完整签名

## 许可证

MIT License - 与 bnb-chain/tss-lib 保持一致
