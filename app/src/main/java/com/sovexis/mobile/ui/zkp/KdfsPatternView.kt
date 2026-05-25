package com.sovexis.mobile.ui.zkp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest

/**
 * KDFS 多宫格密码采集组件（4×4 十六宫格）
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-21
 * 实现状态: ✅ 可用 UI 样板（后期可重构优化）
 * 参考文档: Sovexis · ZKP 模块完整实现指令 (陵谦)
 *
 * 用途：作为 HKDF 的 info 参数参与密钥派生，不直接作为解锁密码。
 * 缓存策略：默认 5 分钟内无需重绘（由 ZkpCacheManager 管理）。
 *
 * @param gridSize 宫格规模，默认 4×4
 * @param minPoints 最少连接点数，默认 6
 * @param onPatternComplete 回调：返回 SHA-256 哈希
 * @param modifier Compose 修饰符
 */
@Composable
fun KdfsPatternView(
    gridSize: Int = 4,
    minPoints: Int = 6,
    onPatternComplete: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // 状态：当前已连接的点序列 (row, col)
    var selectedPoints by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    // 状态：是否正在拖拽
    var isDragging by remember { mutableStateOf(false) }
    // 状态：当前拖拽位置（用于实时绘制连接线）
    var currentDragPosition by remember { mutableStateOf<Offset?>(null) }
    // 状态：错误提示
    var showError by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val dotSize = 16.dp
    val dotSizePx = with(density) { dotSize.toPx() }
    val lineWidth = 4.dp

    // 获取主题颜色（在Composable上下文中）
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = "绘制解锁图案",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 提示文字
        Text(
            text = if (selectedPoints.isEmpty()) "请连接至少 $minPoints 个点"
            else "已连接 ${selectedPoints.size} 个点",
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectedPoints.size >= minPoints) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 宫格画布
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(
                    color = surfaceVariantColor.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val point = findNearestPoint(offset, gridSize, size.width.toFloat(), size.height.toFloat())
                                if (point != null && point !in selectedPoints) {
                                    selectedPoints = selectedPoints + point
                                }
                                currentDragPosition = offset
                            },
                            onDrag = { change, _ ->
                                val offset = change.position
                                currentDragPosition = offset
                                val point = findNearestPoint(offset, gridSize, size.width.toFloat(), size.height.toFloat())
                                if (point != null && point !in selectedPoints) {
                                    selectedPoints = selectedPoints + point
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                currentDragPosition = null
                                if (selectedPoints.size >= minPoints) {
                                    // 序列化为规范字符串并哈希
                                    val canonical = selectedPoints.joinToString(";") { "${it.first},${it.second}" }
                                    val hash = MessageDigest.getInstance("SHA-256")
                                        .digest(canonical.toByteArray())
                                    onPatternComplete(hash)
                                    // 重置
                                    selectedPoints = emptyList()
                                } else {
                                    // 点数不足，显示错误
                                    showError = true
                                    selectedPoints = emptyList()
                                }
                            }
                        )
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val cellWidth = canvasWidth / (gridSize - 1)
                val cellHeight = canvasHeight / (gridSize - 1)

                // 绘制连接线
                if (selectedPoints.size > 1) {
                    for (i in 0 until selectedPoints.size - 1) {
                        val start = selectedPoints[i]
                        val end = selectedPoints[i + 1]
                        val startX = start.second * cellWidth
                        val startY = start.first * cellHeight
                        val endX = end.second * cellWidth
                        val endY = end.first * cellHeight

                        drawLine(
                            color = primaryColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = lineWidth.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 绘制当前拖拽的临时连接线
                if (isDragging && selectedPoints.isNotEmpty() && currentDragPosition != null) {
                    val lastPoint = selectedPoints.last()
                    val lastX = lastPoint.second * cellWidth
                    val lastY = lastPoint.first * cellHeight

                    drawLine(
                        color = primaryColor.copy(alpha = 0.5f),
                        start = Offset(lastX, lastY),
                        end = currentDragPosition!!,
                        strokeWidth = lineWidth.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 绘制所有宫格点
                for (row in 0 until gridSize) {
                    for (col in 0 until gridSize) {
                        val x = col * cellWidth
                        val y = row * cellHeight
                        val isSelected = Pair(row, col) in selectedPoints

                        // 外圈（选中时显示）
                        if (isSelected) {
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.2f),
                                radius = dotSizePx * 1.5f,
                                center = Offset(x, y)
                            )
                        }

                        // 内圈（点本身）
                        drawCircle(
                            color = if (isSelected) primaryColor else outlineColor,
                            radius = if (isSelected) dotSizePx else dotSizePx * 0.6f,
                            center = Offset(x, y)
                        )

                        // 中心白点（选中时）
                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = dotSizePx * 0.4f,
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }
        }

        // 错误提示
        if (showError) {
            Text(
                text = "连接点数不足，请至少连接 $minPoints 个点",
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            // 3秒后自动隐藏错误
            LaunchedEffect(showError) {
                kotlinx.coroutines.delay(3000)
                showError = false
            }
        }

        // 重置按钮
        TextButton(
            onClick = { selectedPoints = emptyList() },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("重新绘制")
        }
    }
}

/**
 * 在 gridSize×gridSize 网格中查找距离触摸点最近的点。
 */
private fun findNearestPoint(
    touchOffset: Offset,
    gridSize: Int,
    canvasWidth: Float,
    canvasHeight: Float
): Pair<Int, Int>? {
    val cellWidth = canvasWidth / (gridSize - 1)
    val cellHeight = canvasHeight / (gridSize - 1)

    val col = (touchOffset.x / cellWidth + 0.5f).toInt().coerceIn(0, gridSize - 1)
    val row = (touchOffset.y / cellHeight + 0.5f).toInt().coerceIn(0, gridSize - 1)

    // 检查距离是否足够近（避免边缘误触）
    val pointX = col * cellWidth
    val pointY = row * cellHeight
    val distance = kotlin.math.hypot(touchOffset.x - pointX, touchOffset.y - pointY)
    val threshold = kotlin.math.min(cellWidth, cellHeight) * 0.5f

    return if (distance <= threshold) row to col else null
}
