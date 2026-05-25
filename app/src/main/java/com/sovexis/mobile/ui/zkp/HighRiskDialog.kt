package com.sovexis.mobile.ui.zkp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 高风险操作真假混淆弹窗
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-21
 * 实现状态: ✅ 可用 UI 样板（后期可重构优化）
 * 参考文档: Sovexis · ZKP 模块完整实现指令 (陵谦)
 *
 * 触发条件：
 * - TSS 高安全模式签名
 * - L2 主权存储映射表恢复
 * - 主账号恢复流程
 * - 设备标签为 RISK_ROOTED 后的所有 P0 操作
 *
 * 机制：
 * - 用户自己决定走一套真、一套假
 * - 软件层不指定真假，不存储真假标记
 * - 攻击者看到的所有输入都是真实用户操作
 *
 * @param operationName 高风险操作名称
 * @param isRooted 设备是否已 Root（影响风险提示）
 * @param onSingleRound 仅输入一轮（不使用混淆）
 * @param onTwoRound 输入两轮（启用混淆）
 * @param onDismiss 取消操作
 */
@Composable
fun HighRiskDialog(
    operationName: String,
    isRooted: Boolean = false,
    onSingleRound: () -> Unit,
    onTwoRound: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 警告图标
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "警告",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 标题
                Text(
                    text = "高风险操作",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 操作名称
                Text(
                    text = operationName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Root 设备警告（如果适用）
                if (isRooted) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "⚠️ 检测到设备已 Root，当前环境安全性降低。建议仅在可信环境中继续操作。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 说明文字
                Text(
                    text = "当前操作涉及敏感信息，为保护您的隐私，请进行多轮输入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "其中只有一轮是真实有效的，由您自行决定哪一轮为真。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮组
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 两轮输入（推荐）
                    Button(
                        onClick = onTwoRound,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("输入两轮（启用混淆）")
                    }

                    // 单轮输入
                    OutlinedButton(
                        onClick = onSingleRound,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("仅输入一轮")
                    }

                    // 取消
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消操作")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 提示文字
                Text(
                    text = "提示：系统不会记录哪一轮为真，仅您自己知道。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 高风险操作确认弹窗（简化版，用于非混淆场景）
 *
 * @param operationName 操作名称
 * @param description 操作描述
 * @param onConfirm 确认回调
 * @param onDismiss 取消回调
 */
@Composable
fun HighRiskConfirmDialog(
    operationName: String,
    description: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "警告",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("高风险操作")
        },
        text = {
            Column {
                Text(
                    text = operationName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(description)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
