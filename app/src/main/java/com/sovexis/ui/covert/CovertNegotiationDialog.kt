package com.sovexis.ui.covert

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sovexis.domain.communication.covert.FallbackStrategy
import com.sovexis.domain.communication.covert.NegotiationFallbackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 隐蔽传输协商失败弹窗。
 *
 * 根据用户级别（L1/L2）显示不同的策略选项。
 * 超时自动选择策略（L1→保守回退，L2→终止连接）。
 *
 * @param userLevel 用户级别（1=普通, 2=严格）
 * @param onStrategySelected 策略选择回调
 * @param onDismiss 关闭回调
 */
@Composable
fun CovertNegotiationDialog(
    userLevel: Int,
    onStrategySelected: (FallbackStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    val handler = remember { NegotiationFallbackHandler(userLevel) }
    val timeoutMs = handler.getTimeoutMs()
    var remainingTime by remember { mutableStateOf(timeoutMs / 1000) }
    val scope = rememberCoroutineScope()

    // 超时倒计时
    LaunchedEffect(Unit) {
        while (remainingTime > 0) {
            delay(1000)
            remainingTime--
        }
        // 超时自动选择
        val timeoutStrategy = handler.getTimeoutFallback()
        onStrategySelected(timeoutStrategy)
    }

    AlertDialog(
        onDismissRequest = { /* 禁止点击外部关闭 */ },
        title = {
            Text(
                text = "安全参数协商失败",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "无法与对端协商隐蔽传输参数。请选择处理方式：",
                    style = MaterialTheme.typography.bodyMedium
                )

                // 倒计时提示
                Text(
                    text = "${remainingTime}秒后自动选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // 根据用户级别显示不同选项
                when (userLevel) {
                    1 -> L1Options(onStrategySelected)
                    2 -> L2Options(onStrategySelected)
                }
            }
        },
        confirmButton = {
            // 确认按钮在选项中处理
        },
        dismissButton = {
            // 关闭按钮在选项中处理
        }
    )
}

/**
 * L1 普通用户选项。
 *
 * 选项：
 * - 保守回退（默认）
 * - 自定义设置
 * - 终止连接
 */
@Composable
private fun L1Options(
    onStrategySelected: (FallbackStrategy) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 保守回退（默认）
        OutlinedButton(
            onClick = { onStrategySelected(FallbackStrategy.A) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("使用保守模式继续")
        }

        // 自定义设置
        OutlinedButton(
            onClick = { onStrategySelected(FallbackStrategy.D) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("自定义设置")
        }

        // 终止连接
        TextButton(
            onClick = { onStrategySelected(FallbackStrategy.B) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("终止连接", color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * L2 严格用户选项。
 *
 * 选项：
 * - 自定义设置
 * - 终止连接（默认）
 */
@Composable
private fun L2Options(
    onStrategySelected: (FallbackStrategy) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 自定义设置
        OutlinedButton(
            onClick = { onStrategySelected(FallbackStrategy.D) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("自定义设置")
        }

        // 终止连接（默认）
        Button(
            onClick = { onStrategySelected(FallbackStrategy.B) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("终止连接")
        }
    }
}

/**
 * 隐蔽传输参数设置弹窗。
 *
 * 允许用户调整隐蔽传输参数。
 *
 * @param currentRatio 当前注入比例
 * @param maxRatio 最大注入比例
 * @param onConfirm 确认回调
 * @param onDismiss 关闭回调
 */
@Composable
fun CovertParameterSettingsDialog(
    currentRatio: Double,
    maxRatio: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var ratio by remember { mutableStateOf(currentRatio.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "隐蔽传输设置",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "虚拟事件注入比例：${(ratio * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )

                Slider(
                    value = ratio,
                    onValueChange = { ratio = it },
                    valueRange = 0.1f..maxRatio.toFloat(),
                    steps = ((maxRatio - 0.1) * 10).toInt() - 1
                )

                Text(
                    text = "注入比例越高，隐蔽性越强，但会增加流量消耗",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(ratio.toDouble()) }
            ) {
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
