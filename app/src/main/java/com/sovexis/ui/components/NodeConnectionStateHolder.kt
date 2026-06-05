package com.sovexis.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局节点连接状态持有者
 *
 * 由 MyNodeViewModel 在 connect/disconnect 后同步更新，
 * SovexisDrawer 通过 collectAsState() 订阅实时状态。
 *
 * 状态规则：
 * - 节点管理中没有任何配置 → NOT_CONFIGURED（未设置节点，灰色）
 * - 已连接 1 个节点 → 显示"已连接到{节点名称}"
 * - 已连接 N 个节点 → 显示"已连接 N 个节点"
 * - 有配置但没有任何连接 → 显示"未连接"
 */
data class GlobalNodeState(
    val totalNodes: Int = 0,
    val connectedNodes: List<String> = emptyList(), // 已连接的节点名称列表
    val anyConfigured: Boolean = false
)

object NodeConnectionStateHolder {
    private val _state = MutableStateFlow(GlobalNodeState())
    val state: StateFlow<GlobalNodeState> = _state.asStateFlow()

    fun update(totalNodes: Int, connectedNames: List<String>) {
        _state.value = GlobalNodeState(
            totalNodes = totalNodes,
            connectedNodes = connectedNames,
            anyConfigured = totalNodes > 0
        )
    }
}
