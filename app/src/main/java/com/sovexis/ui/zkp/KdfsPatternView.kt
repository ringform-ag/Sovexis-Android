package com.sovexis.ui.zkp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.hypot

/**
 * KDFS 多宫格密码采集组件（4×4 十六宫格）— v2 重写
 *
 * 变更（vs v1）:
 *   - 中间点自动选中（对角线滑动不再需要经过每个中间点）
 *   - 命中检测重构（遍历最近点，稳定吸附）
 *   - 手势 API 降级到 awaitPointerEventScope（零延迟响应）
 *   - 触觉反馈
 *   - 选中动画
 *   - 错误闪烁动画（1.5s）
 */
@Composable
fun KdfsPatternView(
    gridSize: Int = 4,
    minPoints: Int = 6,
    onPatternComplete: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPoints by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var isDragging by remember { mutableStateOf(false) }
    var currentDragPosition by remember { mutableStateOf<Offset?>(null) }
    var showError by remember { mutableStateOf(false) }
    var errorPhase by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val dotSize = 16.dp
    val dotSizePx = with(density) { dotSize.toPx() }
    val lineWidth = 2.5.dp
    val lineWidthPx = with(density) { lineWidth.toPx() }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    // 预计算中间点查找表
    val intermediaryMap = remember(gridSize) { buildIntermediaryMap(gridSize) }

    // 错误闪烁
    LaunchedEffect(showError) {
        if (showError) {
            errorPhase = true
            kotlinx.coroutines.delay(500)
            errorPhase = false
            selectedPoints = emptyList()
            kotlinx.coroutines.delay(1000)
            showError = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("绘制解锁图案", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp))
        Text(
            text = if (selectedPoints.isEmpty()) "请连接至少 $minPoints 个点"
            else "已连接 ${selectedPoints.size} 个点",
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectedPoints.size >= minPoints) primaryColor
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier.size(280.dp)
                .background(surfaceVariantColor.copy(alpha = 0.3f), MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize().padding(24.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        isDragging = true
                                        val pt = findNearestPointV2(change.position, gridSize,
                                            size.width.toFloat(), size.height.toFloat(), dotSizePx)
                                        if (pt != null) {
                                            selectedPoints = listOf(pt)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        currentDragPosition = change.position
                                    }
                                    PointerEventType.Move -> {
                                        if (!isDragging) continue
                                        currentDragPosition = change.position
                                        val pt = findNearestPointV2(change.position, gridSize,
                                            size.width.toFloat(), size.height.toFloat(), dotSizePx)
                                        if (pt != null && pt !in selectedPoints) {
                                            addPointWithIntermediary(pt, selectedPoints, intermediaryMap) { newList ->
                                                selectedPoints = newList
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        isDragging = false
                                        currentDragPosition = null
                                        if (selectedPoints.size >= minPoints) {
                                            val canonical = selectedPoints.joinToString(";") { "${it.first},${it.second}" }
                                            val hash = MessageDigest.getInstance("SHA-256")
                                                .digest(canonical.toByteArray())
                                            onPatternComplete(hash)
                                            selectedPoints = emptyList()
                                        } else {
                                            showError = true
                                        }
                                    }
                                    else -> {}
                                }
                                change.consume()
                            }
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val cellWidth = canvasWidth / (gridSize - 1)
                val cellHeight = canvasHeight / (gridSize - 1)

                val lineColor = if (errorPhase) errorColor else primaryColor

                // 连接线
                if (selectedPoints.size > 1) {
                    for (i in 0 until selectedPoints.size - 1) {
                        val start = selectedPoints[i]
                        val end = selectedPoints[i + 1]
                        drawLine(lineColor,
                            Offset(start.second * cellWidth, start.first * cellHeight),
                            Offset(end.second * cellWidth, end.first * cellHeight),
                            lineWidthPx, cap = StrokeCap.Round)
                    }
                }

                // 断头线 + 吸附 + 透明度
                if (isDragging && selectedPoints.isNotEmpty() && currentDragPosition != null) {
                    val lastPt = selectedPoints.last()
                    val lastX = lastPt.second * cellWidth
                    val lastY = lastPt.first * cellHeight
                    val snapTarget = findNearestPointV2(currentDragPosition!!, gridSize,
                        canvasWidth, canvasHeight, dotSizePx)
                    val lineEnd = if (snapTarget != null && snapTarget !in selectedPoints) {
                        Offset(snapTarget.second * cellWidth, snapTarget.first * cellHeight)
                    } else currentDragPosition!!

                    val dist = hypot(lineEnd.x - lastX, lineEnd.y - lastY)
                    val alpha = ((dist / cellWidth - 0.3f) * 4f).coerceIn(0f, 1f).coerceAtMost(0.5f)

                    drawLine(lineColor.copy(alpha = alpha),
                        Offset(lastX, lastY), lineEnd,
                        lineWidthPx, cap = StrokeCap.Round)
                }

                // 所有宫格点
                for (row in 0 until gridSize) {
                    for (col in 0 until gridSize) {
                        val x = col * cellWidth
                        val y = row * cellHeight
                        val isSelected = Pair(row, col) in selectedPoints
                        val dotColor = if (errorPhase) errorColor
                            else if (isSelected) primaryColor else outlineColor

                        if (isSelected) {
                            drawCircle(primaryColor.copy(alpha = 0.2f),
                                dotSizePx * 1.5f, Offset(x, y))
                        }
                        drawCircle(dotColor,
                            if (isSelected) dotSizePx else dotSizePx * 0.75f,
                            Offset(x, y))
                        if (isSelected) {
                            drawCircle(Color.White, dotSizePx * 0.4f, Offset(x, y))
                        }
                    }
                }
            }
        }

        if (showError) {
            Text("连接点数不足，请至少连接 $minPoints 个点",
                color = MaterialTheme.colorScheme.error, fontSize = 14.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        }

        TextButton(onClick = { selectedPoints = emptyList() },
            modifier = Modifier.padding(top = 8.dp)) {
            Text("重新绘制")
        }
    }
}

// ═══════════════ 中间点自动选中 ═══════════════

private fun buildIntermediaryMap(gridSize: Int): Map<Pair<Pair<Int,Int>,Pair<Int,Int>>, Pair<Int,Int>> {
    val map = mutableMapOf<Pair<Pair<Int,Int>,Pair<Int,Int>>, Pair<Int,Int>>()
    for (r1 in 0 until gridSize) for (c1 in 0 until gridSize)
        for (r2 in 0 until gridSize) for (c2 in 0 until gridSize) {
            if (r1 == r2 && c1 == c2) continue
            val dr = r2 - r1; val dc = c2 - c1
            val g = gcd(abs(dr), abs(dc))
            if (g > 1) {
                map[Pair(r1, c1) to Pair(r2, c2)] = Pair(r1 + dr / g, c1 + dc / g)
            }
        }
    return map
}

private fun addPointWithIntermediary(
    newPoint: Pair<Int, Int>,
    selectedPoints: List<Pair<Int, Int>>,
    intermediaryMap: Map<Pair<Pair<Int,Int>,Pair<Int,Int>>, Pair<Int,Int>>,
    onUpdate: (List<Pair<Int, Int>>) -> Unit
) {
    var updated = selectedPoints
    val last = updated.lastOrNull()
    if (last != null) {
        val mid = intermediaryMap[last to newPoint]
        if (mid != null && mid !in updated) updated = updated + mid
    }
    if (newPoint !in updated) updated = updated + newPoint
    onUpdate(updated)
}

// ═══════════════ 命中检测 v2 ═══════════════

private fun findNearestPointV2(
    touchOffset: Offset,
    gridSize: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    dotSizePx: Float
): Pair<Int, Int>? {
    val cellWidth = canvasWidth / (gridSize - 1)
    val cellHeight = canvasHeight / (gridSize - 1)
    val hitRadius = dotSizePx * 2.5f
    var best: Pair<Int,Int>? = null
    var bestDist = Float.MAX_VALUE

    for (row in 0 until gridSize) for (col in 0 until gridSize) {
        val px = col * cellWidth
        val py = row * cellHeight
        val dist = hypot(touchOffset.x - px, touchOffset.y - py)
        if (dist < hitRadius && dist < bestDist) { bestDist = dist; best = row to col }
    }
    return best
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
