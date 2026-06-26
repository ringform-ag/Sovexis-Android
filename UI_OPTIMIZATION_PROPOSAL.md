# Sovexis Android UI 优化方案
## 基于现有代码的性能与感官优化（不改架构、不改布局）

> 逐文件对照现有代码，只做性能提升和视觉打磨。
> 原则：**不动布局结构，不动功能逻辑，只调参数和替换低效写法。**

---

## 一、性能优化

### 1.1 HomeScreen — 聊天列表重组范围过大

**现状：** `HomeScreen.kt` 中 `LazyColumn` 的 `items` 使用消息列表直接作为 key，每次收到新消息或切换身份时，整个消息列表可能全量重组。

**优化：**
- 为消息 item 使用稳定的 `key = { it.id }`（如果消息模型有 id 字段）
- 聊天输入区的 `OutlinedTextField` 的 `value` 状态用 `remember` + `derivedStateOf` 包裹，避免每次输入触发父级重组
- AI 回复的流式文本（如果有）使用 `LaunchedEffect` + `snapshotFlow` 差量更新，而非全量替换整个文本 state

### 1.2 VaultScreen / SafeBoxScreen — 侧滑手势每帧重计算颜色

**现状：** `SafeBoxScreen.kt` 的 `SafeBoxCard` 中 `animateColorAsState` 依赖 `offsetX`，而 `offsetX` 在拖动时每帧变化，导致 `animateColorAsState` 每帧都启动新动画。

**优化：**
- 将 `animateColorAsState` 替换为直接计算：`when { offsetX < -threshold -> Red else ... }`，去掉动画插值
- 或者改用 `Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }` + `pointerInput` 处理手势，用 `graphicsLayer { translationX = offsetX }` 做偏移（硬件加速，不触发 relayout）

### 1.3 IdentityManagementScreen — 卡片列表无 key

**现状：** `IdentityManagementScreen.kt` 中如果使用了 `LazyColumn` 但未指定 `key`，删除/锁定操作后列表会全量重组。

**优化：**
- `items(accounts, key = { it.did }) { ... }`

### 1.4 CredentialsScreen — Tab 切换全量重组

**现状：** `CredentialsScreen.kt` 的三个 Tab 内容（MyCredentialsTab / IssueTab / VerifyTab）通过 `when` 切换，切换时整个 Column 重组。

**优化：**
- 使用 `AnimatedContent` 或 `Crossfade` 包裹 Tab 内容区域，减少视觉跳变
- 或者将三个 Tab 内容分别提取为 `@Composable` 独立函数（已经是了），确保 `remember` 状态在 Tab 切换时不丢失（如 IssueTab 的 claims map）

### 1.5 PaymentScreen — DropdownMenu 展开/收起重组

**现状：** `PaymentScreen.kt` 的支付方/收款方 DropdownMenu 每次展开都重建所有 `DropdownMenuItem`。

**优化：**
- 将账号列表用 `remember(accounts)` 缓存
- `DropdownMenuItem` 的 disabled 状态计算用 `remember(fromDid, toDid)` 缓存，避免每帧重算

### 1.6 SettingsScreen — Slider 拖动重组

**现状：** `SettingsScreen.kt` 隐蔽传输的 `Slider` 拖动时，`injectionRatio` 每帧变化，导致整个 Column 重组。

**优化：**
- 将 Slider 的 `onValueChange` 改为只在松手时提交：`onValueChangeFinished = { viewModel.setInjectionRatio(it) }`，拖动过程中只更新本地 `remember` 状态
- 或者将 Slider 提取为独立 `@Composable`，用 `remember` 隔离重组范围

### 1.7 通用 — DisposableEffect(FLAG_SECURE) 重复

**现状：** 几乎每个 Screen 都有相同的 `DisposableEffect(Unit) { window?.addFlags(FLAG_SECURE); onDispose { window?.clearFlags(FLAG_SECURE) } }` 代码块，出现 10+ 次。

**优化：**
- 提取为 `Modifier.secureScreen()` 扩展函数或 `SecureScreen()` 包装 Composable，统一管理
- 减少代码重复，也避免每个 Screen 独立持有 Activity 引用

---

## 二、感官优化（视觉打磨）

### 2.1 主题色对比度 — 部分组合不达标

**现状：** `Color.kt` 中 `onSurfaceVariant` 在深色主题下可能对比度不足。`SettingsScreen.kt` 大量使用 `onSurfaceVariant` 作为二级文本颜色。

**优化：**
- 检查当前 `onSurfaceVariant` 的实际 hex 值，确保在 `surface` 背景上 >= 4.5:1
- 如果不达标，将 `onSurfaceVariant` 从当前值调亮 1-2 级（例如从 `#9AA0A6` 调到 `#A0A8B0`）

### 2.2 SafeBoxScreen — 侧滑操作视觉反馈不够明确

**现状：** 删除/上传背景色在 `offsetX > 20f` 时就出现，但阈值是 200f，用户在 20-200f 之间看到的是半透明色块，操作意图不清晰。

**优化：**
- 将背景色出现的起点从 20f 提高到 60f，减少误触时的视觉干扰
- 在 `offsetX > dismissThreshold` 时增加轻微的 haptic feedback（`LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.CONFIRM)`）

### 2.3 NotificationScreen — 已读/未读区分度不够

**现状：** 未读通知的背景色是 `accentColor.copy(alpha = 0.05f)`，在深色主题下几乎不可见。

**优化：**
- 将未读背景 alpha 从 0.05 提高到 0.08-0.10
- 或者给未读通知左侧加一个 3dp 宽的 `accentColor` 竖条（类似 Slack/Discord 的未读指示器）

### 2.4 PaymentScreen — PendingSection 颜色硬编码

**现状：** `PendingSection` 和 `FailedSection` 中使用 `Color(0xFFFFA726)` 硬编码橙色。

**优化：**
- 替换为 `MaterialTheme.colorScheme.tertiary` 或自定义 semantic token `SovexisPending`
- 与主题系统联动，切换主题时颜色跟随变化

### 2.5 CredentialsScreen — 验证结果卡片颜色硬编码

**现状：** `VerifyTab` 中验证结果使用 `Color(0xFF1B5E20)` (深绿) 和 `Color(0xFFB71C1C)` (深红) 硬编码。

**优化：**
- 替换为 `MaterialTheme.colorScheme.primaryContainer` / `MaterialTheme.colorScheme.errorContainer`
- 或者自定义 `SovexisSuccess` / `SovexisError` token

### 2.6 RecoveryScreen — TopAppBar 颜色不一致

**现状：** `RecoveryScreen.kt` 使用 `primaryContainer` 作为 TopAppBar 背景色，而其他页面（通过 `SovexisScaffold`）使用 `surface`。

**优化：**
- 统一为 `surface`，与主框架保持一致
- 或者将 RecoveryScreen 也接入 `SovexisScaffold`（需要评估导航结构）

### 2.7 CreateIdentityScreen — 同上，TopAppBar 颜色不一致

**现状：** `CreateIdentityScreen.kt` 也使用 `primaryContainer` 作为 TopAppBar 背景。

**优化：**
- 同 RecoveryScreen，统一为 `surface`

### 2.8 ServiceProviderScreen — TopAppBar 颜色不一致

**现状：** `ServiceProviderScreen.kt` 使用 `surface` + `onSurface`，但没有使用 `SovexisScaffold`，缺少抽屉导航。

**优化：**
- 接入 `SovexisScaffold`，统一导航体验（如果该页面需要抽屉的话）
- 或者至少统一 TopAppBar 样式参数

### 2.9 Drawer — beta 标签颜色硬编码

**现状：** `SovexisDrawer.kt` 中 beta 标签使用 `Color(0xFFFFA726)` 和 `Color(0x1AFFA726)` 硬编码。

**优化：**
- 替换为 `MaterialTheme.colorScheme.tertiary` 或自定义 `SovexisBeta` token

### 2.10 Drawer — 节点连接状态颜色硬编码

**现状：** `SovexisDrawer.kt` 中已连接状态使用 `Color(0xFF34A853)` 硬编码。

**优化：**
- 替换为语义 token（如 `SovexisConnected` / `Success`）

### 2.11 全局 — 硬编码颜色汇总

以下是所有需要从硬编码替换为 token 的位置：

| 文件 | 硬编码值 | 建议替换 |
|---|---|---|
| `SovexisDrawer.kt:51` | `Color(0xFF34A853)` | semantic `Success` |
| `SovexisDrawer.kt:109` | `Color(0xFFFFA726)` | semantic `Warning` |
| `SovexisDrawer.kt:111` | `Color(0x1AFFA726)` | `Warning.copy(alpha=0.1f)` |
| `NotificationScreen.kt:97-101` | 6个 action 颜色 | semantic tokens |
| `NotificationScreen.kt:112` | `accentColor.copy(alpha=0.05f)` | 提高到 0.08-0.10 |
| `PaymentScreen.kt:177` | `Color(0xFFFFA726)` | semantic `Warning/Pending` |
| `PaymentScreen.kt:421` | `Color(0xFFFFA726)` | 同上 |
| `PaymentScreen.kt:426` | `Color(0xFFFFA726)` | 同上 |
| `SafeBoxScreen.kt:127` | `Color(0xFFEF5350)` | semantic `Error` |
| `SafeBoxScreen.kt:143` | `Color(0xFFEF5350)` | 同上 |
| `SafeBoxScreen.kt:160` | `Color(0xFF42A5F5)` | semantic `Info` |
| `CredentialsScreen.kt:280` | `Color(0xFF1E1E1E)` | `surfaceVariant` |
| `CredentialsScreen.kt:281` | `Color(0xFFD4D4D4)` | `onSurfaceVariant` |
| `CredentialsScreen.kt:470` | `Color(0xFF5C6BC0)` | `primary` 或 `tertiary` |
| `CredentialsScreen.kt:509` | `Color(0xFF1B5E20)` | semantic `Success` |
| `CredentialsScreen.kt:512` | `Color(0xFFB71C1C)` | semantic `Error` |
| `SettingsScreen.kt:210` | `Color(0xFF34D399)` | semantic `Success` |
| `SettingsScreen.kt:179` | `✅` / `❌` emoji | 替换为 `Icon` composable |

---

## 三、交互体验优化

### 3.1 支付流程 — Loading 状态缺少进度感

**现状：** `PaymentScreen.kt` 的 `LoadingSection` 只有一个 `LinearProgressIndicator` + 文字，用户不知道当前在哪一步。

**优化：**
- 将 5 个 Loading 状态（POLICY_CHECK / ZKP_GENERATING / SIGNING / SENDING / BIOMETRIC_PROMPT）合并为一个带步骤指示的 Loading 组件
- 显示当前步骤名称 + 总步骤数（如 "步骤 2/4：生成零知识证明"）
- 使用 `IndeterminateLinearProgressIndicator` 或自定义步骤点指示器

### 3.2 VaultScreen — 同步状态缺少时间信息

**现状：** 同步状态只显示 "已同步" 或 "同步中"，没有最后同步时间。

**优化：**
- 在同步状态旁增加 "最后同步: 14:30" 这样的时间戳
- 同步失败时显示具体错误原因而非仅 "同步失败"

### 3.3 HomeScreen — 节点选择器缺少视觉反馈

**现状：** 节点选择 chips 切换时没有动画过渡。

**优化：**
- 使用 `AnimatedContent` 或 `Modifier.animateContentSize()` 包裹 chip 区域
- 切换时增加短暂的 fade 过渡

### 3.4 IdentityManagement — 卡片锁定/解锁缺少过渡动画

**现状：** 身份卡片锁定后直接改变 UI 状态，没有视觉过渡。

**优化：**
- 锁定时增加 `AnimatedVisibility` 过渡（fade + slight scale）
- 锁定图标增加脉冲动画提示用户注意

### 3.5 Settings — 隐蔽传输滑块缺少数值标签

**现状：** `Slider` 上方只显示百分比文字，滑块 thumb 上没有当前值。

**优化：**
- 使用 `Slider` + `Text` 组合，在 thumb 上方显示当前值
- 或者使用 Material 3 的 `Slider` with label parameter（API 31+ 支持）

---

## 四、代码质量优化（不影响功能）

### 4.1 提取共享 Loading 组件

**现状：** `LoadingSection` 在 `PaymentScreen.kt`、`RecoveryScreen.kt` 中重复定义，逻辑几乎相同。

**优化：**
- 提取到 `ui/components/SovexisLoading.kt`，统一参数：`message: String`, `step: Int?`, `totalSteps: Int?`

### 4.2 提取共享 EmptyState 组件

**现状：** `NotificationScreen`、`SafeBoxScreen`、`CredentialsScreen` 都有类似的空状态 UI（Icon + Title + Description）。

**优化：**
- 提取为 `SovexisEmptyState(icon, title, description, actionLabel?, onAction?)` 组件

### 4.3 提取共享 ErrorSection 组件

**现状：** `PaymentScreen.FailedSection`、`RecoveryScreen.ErrorSection` 结构几乎相同。

**优化：**
- 提取为 `SovexisErrorSection(error, onRetry, onCancel?)` 组件

---

## 五、优先级排序

| 优先级 | 项目 | 类型 | 工作量 | 影响 |
|---|---|---|---|---|
| **P0** | 2.11 全局硬编码颜色替换为 token | 感官 | 低 | 主题切换一致性 |
| **P0** | 1.7 提取 `secureScreen()` 去重 | 性能/代码 | 低 | 减少 10+ 处重复 |
| **P1** | 1.2 SafeBoxCard 侧滑手势改用 graphicsLayer | 性能 | 低 | 拖动流畅度 |
| **P1** | 2.3 通知未读指示器增强 | 感官 | 低 | 可用性 |
| **P1** | 2.6/2.7 Recovery + CreateIdentity TopAppBar 统一 | 感官 | 低 | 视觉一致性 |
| **P2** | 1.6 Settings Slider 拖动隔离重组 | 性能 | 低 | 拖动流畅度 |
| **P2** | 3.1 支付 Loading 增加步骤指示 | 交互 | 中 | 用户焦虑感 |
| **P2** | 4.1/4.2/4.3 提取共享组件 | 代码 | 低 | 可维护性 |
| **P3** | 1.3/1.4/1.5 列表 key + Tab 重组隔离 | 性能 | 低 | 大数据量时 |
| **P3** | 2.2 侧滑视觉反馈阈值调整 | 感官 | 低 | 操作清晰度 |
| **P3** | 3.3/3.4 节点选择器 + 身份卡片动画 | 交互 | 中 | 过渡流畅度 |
| **P3** | 3.5 Slider 数值标签 | 交互 | 低 | 可用性 |

---

## 六、KdfsPatternView 交互优化（v2 新增）

> 之前标注为"不建议动"，经分析发现滑动体验有 6 个明确问题，需要优化。

### 6.1 问题诊断：为什么滑动"变扭"

对照 AOSP `LockPatternView` 和开源 `PatternLockView` 库，当前实现有以下问题：

**P0 — 影响核心交互**

| # | 问题 | 现状 | 影响 |
|---|---|---|---|
| 1 | **无中间点自动选中** | 从 (0,0) 滑到 (0,2) 时，(0,1) 不会自动加入路径 | 用户必须精确经过每个中间点，对角线滑动尤其困难 |
| 2 | **命中检测算法不稳定** | `findNearestPoint` 用四舍五入 `(x/cellWidth + 0.5f).toInt()` | 在单元格边界处一个像素偏差就会跳到相邻点，造成意外吸附 |
| 3 | **手势 API 层级过高** | 使用 `detectDragGestures`（高层封装） | 内部有拖拽判定阈值，导致按下到开始绘制的延迟；无法精细控制 Press/Move/Up |

**P1 — 影响体验质感**

| # | 问题 | 现状 | 影响 |
|---|---|---|---|
| 4 | **无触觉反馈** | 选中点时无 haptic | 用户无法通过触感确认点已连接 |
| 5 | **无选中动画** | 点的选中是瞬间切换颜色和大小 | 视觉上"硬切"，缺少反馈感 |
| 6 | **断头线跟随手指抖动** | 拖拽线直接连接手指原始坐标 | 线条在两点之间呈现不规则角度变化 |

**P2 — 提升精致度**

| # | 问题 | 建议 |
|---|---|---|
| 7 | 线宽 4dp 偏粗 | 降至 2.5dp |
| 8 | 断头线无透明度渐变 | 近距离时线条更透明，避免短线条视觉噪音 |
| 9 | 内圈点 `dotSizePx * 0.6f` ≈ 10dp 偏小 | 增大到 `* 0.75f` ≈ 12dp |
| 10 | 错误提示 3 秒隐藏 | 改为 1.5 秒，与闪烁动画同步 |

### 6.2 优化方案

#### 6.2.1 中间点自动选中算法

预计算查找表：对于 4x4 网格中任意两点 (r1,c1) → (r2,c2)，如果它们之间存在共线的中间点（GCD > 1），则记录该中间点。

```kotlin
// 预计算（一次性，可用 remember 缓存）
fun buildIntermediaryMap(gridSize: Int): Map<Pair<Pair<Int,Int>, Pair<Int,Int>>> {
    val map = mutableMapOf<Pair<Pair<Int,Int>>, Pair<Int,Int>>()
    for (r1 in 0 until gridSize) {
        for (c1 in 0 until gridSize) {
            for (r2 in r1 until gridSize) {
                for (c2 in 0 until gridSize) {
                    if (r1 == r2 && c1 == c2) continue
                    val dr = r2 - r1; val dc = c2 - c1
                    val g = gcd(abs(dr), abs(dc))
                    if (g > 1) {
                        val mr = r1 + dr / g; val mc = c1 + dc / g
                        map[Pair(r1,c1) to Pair(r2,c2)] = Pair(mr, mc)
                    }
                }
            }
        }
    }
    return map
}

// 在 onDrag 中，每次添加新点时：
fun addPointWithIntermediary(newPoint: Pair<Int, Int>) {
    val lastPoint = selectedPoints.lastOrNull() ?: return
    // 检查 lastPoint → newPoint 之间是否有中间点
    val mid = intermediaryMap[lastPoint to newPoint]
    if (mid != null && mid !in selectedPoints) {
        selectedPoints = selectedPoints + mid
    }
    if (newPoint !in selectedPoints) {
        selectedPoints = selectedPoints + newPoint
    }
}
```

4x4 网格示例：从 (0,0) → (0,3) 会自动插入 (0,1) 和 (0,2)；从 (0,0) → (3,3) 会自动插入 (1,1) 和 (2,2)。

#### 6.2.2 命中检测重构

替换四舍五入为遍历所有点取最近距离：

```kotlin
private fun findNearestPoint(
    touchOffset: Offset,
    gridSize: Int,
    canvasWidth: Float,
    canvasHeight: Float
): Pair<Int, Int>? {
    val cellWidth = canvasWidth / (gridSize - 1)
    val cellHeight = canvasHeight / (gridSize - 1)
    var bestPoint: Pair<Int, Int>? = null
    var bestDistance = Float.MAX_VALUE
    // 命中半径：点视觉半径的 2.5 倍（约 40dp），而非单元格宽度的 50%
    val hitRadius = dotSizePx * 2.5f

    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val px = col * cellWidth
            val py = row * cellHeight
            val dist = hypot(touchOffset.x - px, touchOffset.y - py)
            if (dist < hitRadius && dist < bestDistance) {
                bestDistance = dist
                bestPoint = row to col
            }
        }
    }
    return bestPoint
}
```

#### 6.2.3 手势 API 降级

将 `detectDragGestures` 替换为 `awaitPointerEventScope`：

```kotlin
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        val down = awaitFirstDown(requireUnconsumed = false)
        isDragging = true
        // ACTION_DOWN: 检测起始点
        val startPoint = findNearestPoint(down.position, ...)
        if (startPoint != null) selectedPoints = listOf(startPoint)
        currentDragPosition = down.position

        while (down.pressed) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            // ACTION_MOVE: 检测新点（含中间点）
            currentDragPosition = change.position
            val nearest = findNearestPoint(change.position, ...)
            if (nearest != null && nearest !in selectedPoints) {
                addPointWithIntermediary(nearest)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        // ACTION_UP: 完成图案
        isDragging = false
        currentDragPosition = null
        if (selectedPoints.size >= minPoints) {
            onPatternComplete(hashPattern(selectedPoints))
        } else {
            showError = true
        }
        selectedPoints = emptyList()
    }
}
```

#### 6.2.4 选中动画

为每个点维护动画状态，选中时播放缩放动画：

```kotlin
// 在 Canvas 绘制中，用 animateFloatAsState 驱动点的缩放
val dotScale by animateFloatAsState(
    targetValue = if (isSelected) 1.0f else 0.6f,
    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
    label = "dotScale"
)
// 选中时外圈扩散动画
val ringScale by animateFloatAsState(
    targetValue = if (isSelected) 1.5f else 0f,
    animationSpec = tween(200),
    label = "ringScale"
)
```

#### 6.2.5 断头线吸附 + 透明度渐变

```kotlin
// 断头线端点：如果手指接近某个未选中点，吸附到该点中心
val snapTarget = findNearestPoint(currentDragPosition, ...)
val lineEnd = if (snapTarget != null && snapTarget !in selectedPoints) {
    Offset(snapTarget.second * cellWidth, snapTarget.first * cellHeight)
} else {
    currentDragPosition!!
}

// 透明度：基于手指到最后一个选中点的距离
val dist = hypot(lineEnd.x - lastX, lineEnd.y - lastY)
val alpha = (dist / cellWidth - 0.3f) * 4f
    .coerceIn(0f, 1f)
    .coerceAtMost(0.5f) // 最大 50% 透明度
```

#### 6.2.6 错误状态闪烁动画

```kotlin
// 错误时：所有已选点和线变红，500ms 后清除
var errorPhase by remember { mutableStateOf(false) }
if (showError) {
    LaunchedEffect(Unit) {
        errorPhase = true  // 触发颜色变红
        delay(500)
        errorPhase = false
        selectedPoints = emptyList()
        delay(1000)
        showError = false
    }
}
// 绘制时：
val lineColor = if (errorPhase) MaterialTheme.colorScheme.error else primaryColor
val dotColor = if (errorPhase) MaterialTheme.colorScheme.error
    else if (isSelected) primaryColor else outlineColor
```

### 6.3 优先级

| 优先级 | 项目 | 工作量 |
|---|---|---|
| **P0** | 6.2.1 中间点自动选中算法 | 中 |
| **P0** | 6.2.2 命中检测重构 | 低 |
| **P0** | 6.2.3 手势 API 降级 | 中 |
| **P1** | 6.2.4 选中动画 + 触觉反馈 | 低 |
| **P1** | 6.2.5 断头线吸附 + 透明度 | 低 |
| **P1** | 6.2.6 错误闪烁动画 | 低 |
| **P2** | 线宽/点大小/间距微调 | 低 |

---

## 七、不建议动的部分

以下现有设计经过评估，建议保持不变：

1. **Drawer 200dp 宽度** — 虽然偏窄，但在手机上功能足够，改宽会影响单手操作
2. **主题预设系统（7 个 ThemePreset + 7 个 DrawerPalette）** — 体系完整，不建议删减
3. **Card 的 14dp 圆角** — 与 Material 3 默认一致，不需要统一到 12dp
4. **SovexisScaffold 的 ModalNavigationDrawer 模式** — 架构合理，不需要改为 PermanentDrawer
5. **FLAG_SECURE 防截屏机制** — 安全应用必须，只是代码组织可以优化（见 1.7）
