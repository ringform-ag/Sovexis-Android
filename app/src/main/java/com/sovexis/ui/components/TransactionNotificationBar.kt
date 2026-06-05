package com.sovexis.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 交易通知滑动栏。
 *
 * 出现在 TopAppBar 下方，从右往左弹出动画，展示最新的交易状态。
 * 点击可展开汇总面板。
 *
 * @param onShowSummary 用户点击通知栏时回调，外部显示汇总面板
 * @param modifier 修饰符
 */
@Composable
fun TransactionNotificationBar(
    onShowSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notifications by TransactionNotificationHolder.notifications.collectAsState()
    val active = notifications.firstOrNull { it.status != TxNotifyStatus.CANCELLED }

    // 从右往左弹出动画
    AnimatedVisibility(
        visible = active != null,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(400)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        active?.let { notification ->
            val (bgColor, icon, statusColor) = when (notification.status) {
                TxNotifyStatus.SIGNING -> Triple(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    Icons.Default.Edit,
                    MaterialTheme.colorScheme.primary
                )
                TxNotifyStatus.SUBMITTED_PENDING -> Triple(
                    Color(0x1AFFA726),
                    Icons.Default.Schedule,
                    Color(0xFFFFA726)
                )
                TxNotifyStatus.CONFIRMED -> Triple(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    Icons.Default.CheckCircle,
                    MaterialTheme.colorScheme.primary
                )
                TxNotifyStatus.FAILED -> Triple(
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    Icons.Default.Error,
                    MaterialTheme.colorScheme.error
                )
                else -> Triple(
                    MaterialTheme.colorScheme.surfaceVariant,
                    Icons.Default.Info,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(bgColor)
                    .clickable { onShowSummary() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${notification.statusLabel} · %,.2f AGT".format(notification.amount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "→ ${notification.toDid.takeLast(12)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    Icons.Default.ChevronRight, contentDescription = "查看详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 交易通知汇总面板（底部弹出 Sheet）。
 *
 * 展示所有活跃的交易通知，每条均可取消（PENDING 状态）。
 *
 * @param onDismiss 关闭面板回调
 * @param onCancel 取消某条 PENDING 交易的回调，接收 txId
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSummarySheet(
    onDismiss: () -> Unit,
    onCancel: (txId: String) -> Unit
) {
    val notifications by TransactionNotificationHolder.notifications.collectAsState()
    val active = notifications.filter { it.status != TxNotifyStatus.CANCELLED }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "交易通知",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { TransactionNotificationHolder.clear(); onDismiss() }) {
                    Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))

            if (active.isEmpty()) {
                Text(
                    "暂无交易通知",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                active.forEach { notification ->
                    val (statusIcon, statusColor) = statusVisuals(notification.status)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    notification.statusLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "%,.2f AGT".format(notification.amount),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Text("支出: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(notification.fromDid.takeLast(12), style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                            }
                            Row {
                                Text("收款: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(notification.toDid.takeLast(12), style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(notification.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            // 取消按钮 — 仅 PENDING 状态
                            if (notification.status == TxNotifyStatus.SUBMITTED_PENDING) {
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = { onCancel(notification.txId) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("取消本地操作", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 状态到图标/颜色的映射 */
@Composable
private fun statusVisuals(status: TxNotifyStatus): Pair<ImageVector, Color> = when (status) {
    TxNotifyStatus.SIGNING -> Icons.Default.Edit to MaterialTheme.colorScheme.primary
    TxNotifyStatus.SIGNED_AWAITING_SUBMIT -> Icons.Default.Description to MaterialTheme.colorScheme.primary
    TxNotifyStatus.SUBMITTED_PENDING -> Icons.Default.Schedule to Color(0xFFFFA726)
    TxNotifyStatus.CONFIRMED -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
    TxNotifyStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
    TxNotifyStatus.CANCELLED -> Icons.Default.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
}
