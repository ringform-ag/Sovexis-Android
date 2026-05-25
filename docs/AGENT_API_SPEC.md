# AGENT_API_SPEC.md - Sovexis 本地 Agent API 规格 v1.0

## 模块定位
为个人 AI Agent（如部署在本地电脑的 LLM）提供与 Sovexis 核心交互的接口。Agent 以"管家副账号"身份获得授权，在策略约束下自动执行支付、保险箱读取、凭证签发等操作，并在需要用户确认时发起需索审批。

MVP 阶段定义清晰的 AIDL 接口，使同一设备上的其他应用（如 Termux 中的 Python 脚本）可以通过绑定服务调用。

---

## 1. 身份与授权

### 1.1 管家副账号
- 用户通过 UI 创建类型为 `STEWARD` 的副账号。
- 为其配置相应的策略。
- 管家副账号的私钥由 Keystore 管理，Agent 无法直接接触，只能通过 API 请求签名。

### 1.2 授权令牌
Agent 调用 API 时需提供 Bearer Token。Token 在用户授权 Agent 时生成，与管家副账号绑定，存储于 `EncryptedSharedPreferences`。

Token 生成：`Base64(SHA-256(childDid + randomSalt))`

---

## 2. AIDL 接口定义

创建文件 `app/src/main/aidl/com/sovexis/agent/ISovexisAgentService.aidl`：

```aidl
package com.sovexis.agent;

interface ISovexisAgentService {
    // 准备支付，返回未签名交易 JSON
    String preparePayment(String fromDid, String toDid, double amount, String note);
    // 请求审批并签名提交（需用户交互）
    String requestApprovalAndSubmit(String unsignedTxJson, String childDid);
    // 列出保险箱条目
    String listVaultItems(String childDid);
    // 读取保险箱条目内容（需审批）
    String readVaultItem(String itemId, String childDid);
    // 签发凭证
    String issueCredential(String issuerDid, String subjectDid, in Map claims);
    // 获取余额
    double getBalance(String did);
    // 获取交易历史 JSON
    String getTransactionHistory(String did);
}

```

- 所有返回字符串均为 JSON，格式：

```JSON
{
  "success": true,
  "data": { ... },
  "error": null
}

```

### 2.1 服务实现（简化）

```KOTLIN
class SovexisAgentService : Service() {
    override fun onBind(intent: Intent): IBinder = binder
    private val binder = object : ISovexisAgentService.Stub() {
        override fun preparePayment(fromDid: String, toDid: String, amount: Double, note: String): String {
            // 调用 PaymentManager.preparePayment，返回 JSON
        }
        override fun requestApprovalAndSubmit(unsignedTxJson: String, childDid: String): String {
            // 发起需索审批，等待用户生物认证后签名提交
        }
        // ...
    }
}

```

- 需索审批通过 Notification + PendingIntent 启动一个透明的 ApprovalActivity，在其中完成生物认证和签名。

## 3. 安全约束

- 所有 API 调用必须验证 Token。

- 签名操作必须触发用户生物认证，不能静默完成。

- 操作前检查策略权限。

## 4. 使用示例（Python 通过 Termux）

```python
import subprocess, json

def call_sovexis(method, params):
    cmd = ["am", "startservice", "-n", "com.sovexis/.agent.SovexisAgentService", "--es", "method", method, "--es", "params", json.dumps(params)]
    subprocess.run(cmd)
    # 实际需通过绑定服务或 ContentProvider 获取返回值，此处示意
	
```

- 更好的方式是提供 ContentProvider 或本地 Socket 服务，以便 Agent 获取同步返回值。MVP 可先用 AIDL 绑定服务，配合 Messenger 或广播返回结果。

## 5. 依赖项

- Android Service 组件

- androidx.core:core-ktx

## 6. 移植性

- AIDL 为 Android 特有。未来其他平台可替换为本地 HTTP Server（如 Ktor）提供 REST API，保持接口语义一致。

# 规格版本：1.0

- 最后更新：2026-04-12
- 维护者：Sovexis 架构组
