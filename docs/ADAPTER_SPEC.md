# ADAPTER_SPEC.md - Sovexis 服务商适配器接口规范 v1.0

## 模块定位
定义第三方服务商（如 AI 模型提供商、数据提供商、法币兑换商）接入 Sovexis 的统一适配器接口。MVP 阶段仅定义接口并实现一个模拟适配器用于演示，为未来真实服务商接入预留清晰的扩展点。

---

## 1. 适配器抽象基类

```kotlin
abstract class ServiceAdapter(protected val context: Context) {
    abstract val serviceId: String
    abstract val serviceName: String
    abstract val supportedActions: List<String>

    abstract suspend fun submit(request: ServiceRequest): Result<ServiceResponse>
    abstract suspend fun estimateFee(request: ServiceRequest): Double
    abstract suspend fun isAvailable(): Boolean
}

```

### 1.1 ServiceRequest / ServiceResponse

```kotlin
@Serializable
data class ServiceRequest(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val params: Map<String, @Serializable(with = AnySerializer::class) Any>,
    val authCredential: String? = null
)

@Serializable
data class ServiceResponse(
    val requestId: String,
    val success: Boolean,
    val data: Map<String, @Serializable(with = AnySerializer::class) Any>? = null,
    val error: String? = null,
    val proof: ServiceProof? = null
)

@Serializable
data class ServiceProof(
    val serviceId: String,
    val timestamp: Long,
    val requestHash: String,
    val signature: String
)

```

## 2. 模拟适配器示例（DeepSeek 模拟）

```kotlin
class DeepSeekMockAdapter(context: Context) : ServiceAdapter(context) {
    override val serviceId = "deepseek"
    override val serviceName = "DeepSeek AI (模拟)"
    override val supportedActions = listOf("text_generation", "code_generation")

    override suspend fun submit(request: ServiceRequest): Result<ServiceResponse> {
        delay(1000)
        val content = "这是来自 DeepSeek 的模拟回复：您的问题是关于 ${request.params["prompt"]}"
        return Result.success(ServiceResponse(
            requestId = request.id,
            success = true,
            data = mapOf("content" to content),
            proof = generateMockProof(request)
        ))
    }

    override suspend fun estimateFee(request: ServiceRequest): Double {
        val promptLength = (request.params["prompt"] as? String)?.length ?: 0
        return (promptLength / 1000.0) * 0.001
    }

    override suspend fun isAvailable(): Boolean = true

    private fun generateMockProof(request: ServiceRequest): ServiceProof {
        return ServiceProof(
            serviceId = serviceId,
            timestamp = System.currentTimeMillis(),
            requestHash = "mock_hash_${request.id.take(8)}",
            signature = "mock_signature"
        )
    }
}

```

## 3. 适配器注册与路由

```kotlin
object ServiceRegistry {
    private val adapters = mutableListOf<ServiceAdapter>()

    fun register(adapter: ServiceAdapter) { adapters.add(adapter) }

    fun getAdaptersForAction(action: String): List<ServiceAdapter> {
        return adapters.filter { action in it.supportedActions && runBlocking { it.isAvailable() } }
    }

    suspend fun routeRequest(request: ServiceRequest): Result<ServiceResponse> {
        val candidates = getAdaptersForAction(request.action)
        if (candidates.isEmpty()) return Result.failure(NoServiceException())
        val best = candidates.minByOrNull { it.estimateFee(request) }!!
        return best.submit(request)
    }
}

```

## 4. 法币兑换适配器（预留）

```kotlin
abstract class FiatBridgeAdapter(context: Context) : ServiceAdapter(context) {
    abstract suspend fun exchange(amount: Double, fromAsset: String, toAsset: String): Result<ExchangeResult>
    abstract suspend fun getExchangeRate(fromAsset: String, toAsset: String): Double
}

```

## 5. 与支付模块的集成

- 服务费用通过 Sovexis 支付模块结算。

- ServiceRequest.authCredential 可包含支付证明。

# 规格版本：1.0

- 最后更新：2026-04-12
- 维护者：Sovexis 架构组