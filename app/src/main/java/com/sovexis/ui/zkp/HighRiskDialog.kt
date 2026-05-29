package com.sovexis.ui.zkp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@Composable
fun HighRiskDialog(
    operationName: String,
    isRooted: Boolean = false,
    onSingleRound: () -> Unit,
    onTwoRound: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(30) }
    var skipNextTime by remember { mutableStateOf(false) }

    // 30 秒倒计时
    LaunchedEffect(Unit) {
        while (countdown > 0) { delay(1000L); countdown-- }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (view.context as? android.app.Activity)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 警告图标
                Icon(Icons.Default.Warning, contentDescription = "警告",
                    modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))

                Text("高风险操作", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Text(operationName, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                // ====== 倒计时栏 ======
                val isUrgent = countdown <= 10
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isUrgent) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (isUrgent) "⏰ 即将超时，剩余 ${countdown} 秒" else "⏳ 剩余 ${countdown} 秒后自动取消",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUrgent) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (isRooted) {
                    Surface(modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("⚠️ 检测到设备已 Root，当前环境安全性降低。建议仅在可信环境中继续操作。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text("当前操作涉及敏感信息，为保护您的隐私，请进行多轮输入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text("其中只有一轮是真实有效的，由您自行决定哪一轮为真。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onTwoRound, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("输入两轮（启用混淆）") }

                    OutlinedButton(onClick = onSingleRound, modifier = Modifier.fillMaxWidth()
                    ) { Text("仅输入一轮") }

                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()
                    ) { Text("取消操作") }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ====== "下次不再提醒" 勾选框 ======
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = skipNextTime,
                        onCheckedChange = { skipNextTime = it }
                    )
                    Text("下次不再提醒", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("提示：系统不会记录哪一轮为真，仅您自己知道。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun HighRiskConfirmDialog(
    operationName: String,
    description: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = "警告", tint = MaterialTheme.colorScheme.error) },
        title = { Text("高风险操作") },
        text = {
            Column {
                Text(operationName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(description)
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
