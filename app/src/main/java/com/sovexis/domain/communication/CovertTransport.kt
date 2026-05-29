package com.sovexis.domain.communication

import com.sovexis.domain.communication.covert.*
import com.sovexis.domain.communication.RawMessage
import com.sovexis.domain.communication.TransportAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 隐蔽传输装饰器。
 *
 * 实现恒定速率调度、数据包填充、Web流量伪装、动态参数协商、虚拟事件注入，
 * 以及用户分级协商失败处理策略。
 *
 * @param innerTransport 内层传输适配器
 * @param userLevel 用户级别（0=公开, 1=普通, 2=严格）
 * @param negotiator 参数协商器
 * @param fallbackHandler 协商失败处理器
 */
class CovertTransport(
    private val innerTransport: TransportAdapter,
    private val userLevel: Int = 1,
    private val negotiator: ParameterNegotiator = ParameterNegotiator(),
    private val fallbackHandler: NegotiationFallbackHandler = NegotiationFallbackHandler(userLevel)
) : TransportAdapter {

    private val scheduler = ConstantRateScheduler()
    private var padder: PacketPadder
    private var injector: VirtualEventInjector
    private val camouflage = WebTrafficCamouflage()

    private var parameters: CovertParameters? = null
    private var sessionScope: CoroutineScope? = null
    private val mutex = Mutex()

    init {
        val defaultRatio = VirtualEventInjector.getDefaultRatioForUserLevel(userLevel)
        padder = PacketPadder(paddingRatio = defaultRatio)
        injector = VirtualEventInjector(injectionRatio = defaultRatio)
    }

    // ── TransportAdapter 接口 ──

    override val isConnected: Boolean
        get() = innerTransport.isConnected

    override suspend fun connect(): Result<Unit> {
        return innerTransport.connect()
    }

    override suspend fun disconnect() {
        scheduler.stop()
        sessionScope?.cancel()
        innerTransport.disconnect()
    }

    override suspend fun send(encryptedPayload: ByteArray, destinationDid: String): Result<String> {
        return runCatching {
            // 1. 填充真实消息
            val padded = padder.pad(encryptedPayload)
            // 2. 随机注入虚拟事件
            if (injector.shouldInject()) {
                injectVirtualEvent()
            }
            // 3. 加入调度器队列（实际发送由调度器控制）
            scheduler.enqueueRealPacket(padded)
            // 返回消息 ID
            "cvt-${System.currentTimeMillis()}"
        }
    }

    override fun receive(): Flow<RawMessage> = flow {
        // 由调度器驱动接收，实际实现需要与内层传输协调
        innerTransport.receive().collect { message ->
            // 解填充并发射
            emit(message)
        }
    }

    // ── 协商流程 ──

    /**
     * 启动隐蔽传输并协商参数。
     *
     * @param scope 协程作用域
     * @return 协商结果
     */
    suspend fun start(scope: CoroutineScope): Result<CovertParameters> {
        sessionScope = scope
        scheduler.start(scope)
        return negotiateParameters()
    }

    /**
     * 协商隐蔽传输参数。
     *
     * @return 协商结果
     */
    suspend fun negotiateParameters(): Result<CovertParameters> {
        val defaultParams = negotiator.getDefaultParameters(userLevel)
        return try {
            withTimeout(negotiator.getTimeoutMs()) {
                // 发送协商请求（由 CryptoCommLayer 加密后发送）
                val request = negotiator.serializeParameters(defaultParams)
                // TODO: 实际发送协商请求并等待响应
                // 若协商成功，更新参数
                mutex.withLock {
                    parameters = defaultParams
                    updateComponentsWithParameters(defaultParams)
                }
                Result.success(defaultParams)
            }
        } catch (e: TimeoutCancellationException) {
            handleNegotiationFailure()
        }
    }

    // ── 协商失败处理 ──

    private suspend fun handleNegotiationFailure(): Result<CovertParameters> {
        val chain = fallbackHandler.getStrategyChain()

        if (!fallbackHandler.requiresDialog()) {
            // L0：自动执行策略链，无弹窗
            return executeAutoFallback(chain)
        }

        // L1/L2：弹窗等待用户选择
        return executeDialogFallback(chain)
    }

    private suspend fun executeAutoFallback(chain: List<FallbackStrategy>): Result<CovertParameters> {
        for (strategy in chain) {
            when (strategy) {
                FallbackStrategy.C -> {
                    // Snackbar: "当前网络环境存在安全风险"
                    // 实际应用中通过回调通知 UI 层
                }
                FallbackStrategy.A -> {
                    val conservativeParams = negotiator.getConservativeParameters()
                    mutex.withLock {
                        parameters = conservativeParams
                        updateComponentsWithParameters(conservativeParams)
                    }
                    return Result.success(conservativeParams)
                }
                FallbackStrategy.D -> {
                    // L0 不弹窗，跳过
                    continue
                }
                FallbackStrategy.B -> {
                    return Result.failure(SecurityException("协商失败，通信终止"))
                }
            }
        }
        return Result.failure(SecurityException("协商失败，无可用策略"))
    }

    private suspend fun executeDialogFallback(chain: List<FallbackStrategy>): Result<CovertParameters> {
        // 弹窗逻辑由 UI 层（CovertNegotiationDialog）处理
        // 此处返回需要弹窗的状态
        return Result.failure(NeedNegotiationDialogException(chain))
    }

    // ── 辅助方法 ──

    private fun injectVirtualEvent(): ByteArray {
        return injector.generateVirtualPayload()
    }

    private fun updateComponentsWithParameters(params: CovertParameters) {
        // 更新填充器
        padder = PacketPadder(
            targetPacketSize = params.packet_size,
            paddingRatio = params.padding_ratio
        )
        // 更新注入器
        injector = VirtualEventInjector(injectionRatio = params.injection_ratio)
    }

    /**
     * 应用用户选择的协商失败策略。
     *
     * @param strategy 用户选择的策略
     * @return 应用结果
     */
    suspend fun applyFallbackStrategy(strategy: FallbackStrategy): Result<CovertParameters> {
        return when (strategy) {
            FallbackStrategy.A -> {
                val conservativeParams = negotiator.getConservativeParameters()
                mutex.withLock {
                    parameters = conservativeParams
                    updateComponentsWithParameters(conservativeParams)
                }
                Result.success(conservativeParams)
            }
            FallbackStrategy.B -> {
                disconnect()
                Result.failure(SecurityException("用户选择终止通信"))
            }
            FallbackStrategy.D -> {
                // 自定义设置 - 使用默认参数
                val defaultParams = negotiator.getDefaultParameters(userLevel)
                mutex.withLock {
                    parameters = defaultParams
                    updateComponentsWithParameters(defaultParams)
                }
                Result.success(defaultParams)
            }
            else -> Result.failure(IllegalArgumentException("不支持的策略: $strategy"))
        }
    }

    /**
     * 获取当前参数。
     *
     * @return 当前隐蔽传输参数
     */
    fun getCurrentParameters(): CovertParameters? = parameters

    /**
     * 获取 JA4 指纹。
     *
     * @param browserType 浏览器类型
     * @return JA4 指纹字符串
     */
    fun getJA4Fingerprint(browserType: String = "chrome"): String {
        return camouflage.generateJA4Fingerprint(browserType)
    }

    /**
     * 获取随机 SNI 域名。
     *
     * @return SNI 域名
     */
    fun randomSniHost(): String {
        return camouflage.randomSniHost()
    }
}
