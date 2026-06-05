package com.sovexis.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 交易通知状态（生命周期枚举）。
 */
enum class TxNotifyStatus {
    /** 签名中 */
    SIGNING,
    /** 已签名待提交 */
    SIGNED_AWAITING_SUBMIT,
    /** 已提交待确认（本地挂起） */
    SUBMITTED_PENDING,
    /** 节点已确认 */
    CONFIRMED,
    /** 失败 */
    FAILED,
    /** 已取消 */
    CANCELLED
}

/**
 * 单条交易通知。
 *
 * @param id 通知唯一 ID
 * @param txId 交易 ID
 * @param amount 金额
 * @param fromDid 支出方 DID（截断显示）
 * @param toDid 收款方 DID（截断显示）
 * @param status 当前状态
 * @param statusLabel 用户可读状态文案
 * @param timestamp 创建时间戳
 */
data class TransactionNotification(
    val id: String,
    val txId: String,
    val amount: Double,
    val fromDid: String,
    val toDid: String,
    val status: TxNotifyStatus,
    val statusLabel: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 全局交易通知状态持有者（单例）。
 *
 * 用途：HomeScreen、PaymentScreen 等任何页面都能读取到最新的交易通知列表，
 * 无需通过 ViewModel 传递。NotificationBar 订阅此 StateFlow 即可自动刷新。
 */
object TransactionNotificationHolder {

    private val _notifications = MutableStateFlow<List<TransactionNotification>>(emptyList())
    val notifications: StateFlow<List<TransactionNotification>> = _notifications.asStateFlow()

    /** 是否存在未读（即状态非 CONFIRMED / CANCELLED 的）通知 */
    val hasUnread: Boolean
        get() = _notifications.value.any { it.status != TxNotifyStatus.CONFIRMED && it.status != TxNotifyStatus.CANCELLED }

    /**
     * 添加或更新一条通知（按 id 去重，新状态覆盖旧状态）。
     */
    fun upsert(notification: TransactionNotification) {
        val current = _notifications.value.toMutableList()
        val idx = current.indexOfFirst { it.id == notification.id }
        if (idx >= 0) {
            current[idx] = notification
        } else {
            current.add(0, notification) // 新通知置顶
        }
        _notifications.value = current
    }

    /**
     * 标记为已取消（移除通知或改变状态）。
     */
    fun markCancelled(txId: String) {
        val current = _notifications.value.toMutableList()
        val idx = current.indexOfFirst { it.txId == txId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(status = TxNotifyStatus.CANCELLED, statusLabel = "已取消")
        }
        _notifications.value = current
    }

    /**
     * 清除所有通知。
     */
    fun clear() {
        _notifications.value = emptyList()
    }
}
