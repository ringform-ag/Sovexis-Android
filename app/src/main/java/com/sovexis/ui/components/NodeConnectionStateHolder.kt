package com.sovexis.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局节点连接状态 + 信用等级持有者
 *
 * 由 MyNodeViewModel 在 connect/disconnect 后同步更新。
 * SovexisDrawer 通过 collectAsState() 订阅实时状态。
 *
 * 连接状态三态：
 * - anyConfigured == false → NOT_CONFIGURED（未设置节点）
 * - anyConfigured && connectedNodes.isEmpty() → DISCONNECTED（未连接）
 * - anyConfigured && connectedNodes.isNotEmpty() → CONNECTED（已连接）
 *
 * 信用信息由 Node /healthz 下发，MyNodeViewModel 解析后传入。
 */
data class GlobalNodeState(
    val totalNodes: Int = 0,
    val connectedNodes: List<String> = emptyList(),
    val anyConfigured: Boolean = false,
    // 4.0.0: 信用等级（LV 1-60，由 Node 计算）
    val creditLevel: Int = 0,
    val creditExperience: Long = 0
)

object NodeConnectionStateHolder {
    private val _state = MutableStateFlow(GlobalNodeState())
    val state: StateFlow<GlobalNodeState> = _state.asStateFlow()

    fun update(totalNodes: Int, connectedNames: List<String>) {
        _state.value = GlobalNodeState(
            totalNodes = totalNodes,
            connectedNodes = connectedNames,
            anyConfigured = totalNodes > 0,
            creditLevel = _state.value.creditLevel,
            creditExperience = _state.value.creditExperience
        )
    }

    /** 连接成功后更新信用信息 */
    fun setCredit(level: Int, experience: Long) {
        if (level > 0) {
            _state.value = _state.value.copy(creditLevel = level, creditExperience = experience)
        }
    }

    /** 断开所有节点时重置信用 */
    fun resetCredit() {
        _state.value = _state.value.copy(creditLevel = 0, creditExperience = 0)
    }
}
