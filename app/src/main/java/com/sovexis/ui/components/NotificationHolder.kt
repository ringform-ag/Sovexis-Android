package com.sovexis.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val txId: String = "",
    val isRead: Boolean = false
)

object NotificationHolder {
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    fun add(notification: NotificationItem) {
        _notifications.value = listOf(notification) + _notifications.value
    }

    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    fun clear() {
        _notifications.value = emptyList()
    }

    fun unreadCount(): Int = _notifications.value.count { !it.isRead }
}
