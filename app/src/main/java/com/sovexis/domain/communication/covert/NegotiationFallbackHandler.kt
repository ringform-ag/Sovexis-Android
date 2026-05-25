package com.sovexis.domain.communication.covert

/**
 * 协商失败策略枚举。
 *
 * - A: 保守回退（使用保守参数继续通信）
 * - B: 终止连接
 * - C: 提示风险（Snackbar 提示）
 * - D: 用户选择（弹窗提供选项）
 */
enum class FallbackStrategy {
    A,  // 保守回退
    B,  // 终止连接
    C,  // 提示风险
    D   // 用户选择
}

/**
 * Snackbar 提示内容。
 *
 * @param message 提示消息
 * @param durationMs 持续时间（毫秒）
 * @param showRetryButton 是否显示重试按钮
 */
data class SnackbarMessage(
    val message: String,
    val durationMs: Long = 4000L,
    val showRetryButton: Boolean = false
)

/**
 * 协商失败处理器。
 *
 * 根据用户级别（L0/L1/L2）执行不同的协商失败处理策略链。
 *
 * @param userLevel 用户级别（0=公开, 1=普通, 2=严格）
 */
class NegotiationFallbackHandler(
    private val userLevel: Int  // 0=公开, 1=普通, 2=严格
) {

    init {
        require(userLevel in 0..2) {
            "用户级别必须在 0-2 范围内，实际: $userLevel"
        }
    }

    /**
     * 获取策略执行链。
     *
     * @return 策略执行顺序列表
     */
    fun getStrategyChain(): List<FallbackStrategy> {
        return when (userLevel) {
            0 -> listOf(FallbackStrategy.C, FallbackStrategy.A, FallbackStrategy.D, FallbackStrategy.B)
            1 -> listOf(FallbackStrategy.A, FallbackStrategy.D, FallbackStrategy.B)
            2 -> listOf(FallbackStrategy.D, FallbackStrategy.B)
            else -> listOf(FallbackStrategy.A, FallbackStrategy.B)
        }
    }

    /**
     * 判断是否需要弹窗（L0 无弹窗，L1/L2 有弹窗）。
     *
     * @return true 表示需要弹窗
     */
    fun requiresDialog(): Boolean {
        return userLevel >= 1
    }

    /**
     * 获取超时自动选择策略。
     *
     * @return 超时后自动执行的策略
     */
    fun getTimeoutFallback(): FallbackStrategy {
        return when (userLevel) {
            0 -> FallbackStrategy.A  // L0 超时不适用（无弹窗），但兜底
            1 -> FallbackStrategy.A  // L1 超时 → 保守回退
            2 -> FallbackStrategy.B  // L2 超时 → 终止连接
            else -> FallbackStrategy.A
        }
    }

    /**
     * 获取超时时间（毫秒）。
     *
     * @return 超时时间
     */
    fun getTimeoutMs(): Long = 30_000L  // 30 秒

    /**
     * 获取策略对应的 Snackbar 消息。
     *
     * @param strategy 策略类型
     * @return Snackbar 消息内容
     */
    fun getSnackbarMessage(strategy: FallbackStrategy): SnackbarMessage? {
        return when (strategy) {
            FallbackStrategy.A -> SnackbarMessage(
                message = "安全参数协商失败，已启用安全通信模式",
                durationMs = 4000L
            )
            FallbackStrategy.B -> SnackbarMessage(
                message = "安全参数协商失败，通信已终止",
                durationMs = 4000L,
                showRetryButton = true
            )
            FallbackStrategy.C -> SnackbarMessage(
                message = "当前网络环境存在安全风险，已自动启用保护",
                durationMs = 4000L
            )
            FallbackStrategy.D -> null  // D 策略使用弹窗，不显示 Snackbar
        }
    }

    /**
     * 获取用户级别描述。
     *
     * @return 用户级别描述字符串
     */
    fun getUserLevelDescription(): String {
        return when (userLevel) {
            0 -> "公开用户 (L0)"
            1 -> "普通用户 (L1)"
            2 -> "严格用户 (L2)"
            else -> "未知用户级别"
        }
    }
}

/**
 * 需要协商弹窗异常。
 *
 * 当 L1/L2 用户需要手动选择协商失败策略时抛出。
 *
 * @param chain 策略执行链
 */
class NeedNegotiationDialogException(val chain: List<FallbackStrategy>) : 
    Exception("需要用户选择协商失败策略")
