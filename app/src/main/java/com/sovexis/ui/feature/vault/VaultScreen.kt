package com.sovexis.ui.feature.vault

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.domain.storage.PlainVaultItem
import com.sovexis.ui.components.AccountStateHolder
import com.sovexis.ui.components.SovexisBiometricPrompt
import com.sovexis.ui.navigation.SovexisRoute
import com.sovexis.ui.zkp.KdfsPatternView
import android.view.WindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val FabSize = 48.dp; private val SubFabSize = 40.dp
private val SwipeBtn = 44.dp; private val FabRightMargin = 100.dp

private val DeepTeal = Color(0xFF00897B)
private val SyncGreen = Color(0xFF34A853)
private val SyncOrange = Color(0xFFFF9800)
private val SyncRed = Color(0xFFEF5350)
private val PinBlue = Color(0xFF42A5F5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel = hiltViewModel(),
    ownerDid: String = "",
    navController: NavHostController? = null
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) {
        val w = (context as? android.app.Activity)?.window; w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LaunchedEffect(Unit) { viewModel.init() }
    LaunchedEffect(state.step) {
        if (state.step == VaultStep.COMPLETED) { delay(1000L); viewModel.reset(); viewModel.init() }
    }

    val effectiveOwnerDid = state.ownerDid.ifEmpty { ownerDid }
    val globalAccounts by AccountStateHolder.accounts.collectAsState()
    val displayAccounts = globalAccounts.filter { it.accountType == com.sovexis.domain.identity.AccountType.MASTER || it.isActive }

    // ── FAB 展开/收起 ──
    var fabExpanded by remember { mutableStateOf(false) }
    val fabRotation by animateFloatAsState(if (fabExpanded) 45f else 0f, tween(250))
    // Staggered sub-icon: 2 delays (cloud upload + edit, sync is in topbar)
    val subUploadAlpha by animateFloatAsState(if (fabExpanded) 1f else 0f, tween(180, 0))
    val subNewAlpha by animateFloatAsState(if (fabExpanded) 1f else 0f, tween(180, 30))

    // ── FAB 滑动隐藏 ──
    val listState = rememberLazyListState()
    var fabVisible by remember { mutableStateOf(true) }
    var lastScrollY by remember { mutableStateOf(0f) }

    LaunchedEffect(listState.firstVisibleItemScrollOffset, listState.firstVisibleItemIndex) {
        val currentY = listState.firstVisibleItemIndex * 100f + listState.firstVisibleItemScrollOffset
        val delta = currentY - lastScrollY
        if (delta > 200f && !fabExpanded) fabVisible = false  // scrolled down far → hide
        else if (delta < -100f) fabVisible = true              // scrolled up → show
        lastScrollY = currentY
    }
    val fabOffsetX by animateDpAsState(if (fabVisible) 0.dp else FabSize + FabRightMargin, tween(200))

    // ── 同步历史页面 ──
    if (state.showSyncHistory) {
        SyncHistoryFullPage(
            history = state.syncHistory,
            onBack = { viewModel.hideSyncHistory() },
            onDeleteEntry = { viewModel.deleteSyncHistoryEntry(it) },
            onClearAll = { viewModel.clearAllSyncHistory() })
        return
    }

    ModalNavigationDrawer(drawerState = drawerState,
        drawerContent = {
            com.sovexis.ui.components.SovexisDrawer(accounts = displayAccounts, currentRoute = SovexisRoute.Vault.route,
                onAccountSelected = { }, onNavigate = { route ->
                    navController?.navigate(route) { popUpTo(SovexisRoute.Home.route) { inclusive = false }; launchSingleTop = true }
                    scope.launch { drawerState.close() } })
        }
    ) {
        Scaffold(
            topBar = {
                if (state.isSearching) {
                    TopAppBar(
                        title = {
                            OutlinedTextField(state.searchQuery, { viewModel.updateSearch(it) },
                                placeholder = { Text("搜索笔记标题…") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                trailingIcon = {
                                    Row {
                                        if (state.searchQuery.isNotEmpty())
                                            IconButton(onClick = { viewModel.updateSearch("") }, modifier = Modifier.size(32.dp))
                                            { Icon(Icons.Default.Clear, null, Modifier.size(18.dp)) }
                                        Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent))
                        },
                        navigationIcon = { IconButton(onClick = { viewModel.exitSearch(); focusManager.clearFocus() }) { Icon(Icons.Default.ArrowBack, "返回") } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                } else {
                    TopAppBar(
                title = { Text("保险箱") },
                navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "菜单") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
                actions = {
                    // 左边：搜索
                    IconButton(onClick = { viewModel.startSearch() }) { Icon(Icons.Default.Search, "搜索") }
                    // 右边：同步记录入口 - 大云朵+小循环，有失败时叠红点
                    IconButton(onClick = { viewModel.showSyncHistory() }) {
                        Box {
                            Icon(Icons.Default.CloudSync, "同步记录",
                                tint = if (state.syncFailCount > 0) SyncOrange else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.syncFailCount > 0) {
                                Box(Modifier.align(Alignment.BottomEnd).size(10.dp)
                                    .background(SyncRed, CircleShape))
                            }
                        }
                    }
                }
            )
                }
            },
            floatingActionButton = {
                if (!state.isSearching) {
                    // Sub-icons + FAB in unified Column → identical horizontal center, FAB never shifts
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.offset(x = fabOffsetX)
                    ) {
                        // 上传
                        AnimatedVisibility(
                            visible = fabExpanded && subUploadAlpha > 0f,
                            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.6f, animationSpec = tween(120)),
                            exit = fadeOut(tween(100))
                        ) {
                            FabSubBtn(Icons.Default.CloudUpload, "上传") { fabExpanded = false; viewModel.syncAllToNode() }
                        }
                        // 新建
                        AnimatedVisibility(
                            visible = fabExpanded && subNewAlpha > 0f,
                            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.6f, animationSpec = tween(120)),
                            exit = fadeOut(tween(100))
                        ) {
                            FabSubBtn(Icons.Default.Edit, "新建") { fabExpanded = false; viewModel.showInlineEditor() }
                        }
                        // Main FAB — always at bottom, never moves
                        FloatingActionButton(
                            onClick = { fabExpanded = !fabExpanded },
                            modifier = Modifier.size(FabSize),
                            shape = CircleShape,
                            containerColor = DeepTeal,
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Add, "添加", Modifier.size(26.dp).rotate(fabRotation))
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(Modifier.fillMaxSize().padding(paddingValues)) {
                when (state.step) {
                    VaultStep.IDLE -> {
                        Column(Modifier.fillMaxSize()) {
                            // ── 同步状态栏 ──
                            if (!state.isSearching && state.lastSyncAt > 0) {
                                SyncStatusBar(
                                    lastSyncAt = state.lastSyncAt,
                                    syncedCount = state.syncedItemIds.size,
                                    totalCount = state.items.size,
                                    failCount = state.syncFailCount,
                                    onRefresh = { viewModel.refreshSyncStatus() },
                                    onClearFail = { viewModel.clearSyncFailCount() })
                            }

                            // ── 主内容区 ──
                            Box(Modifier.weight(1f)) {
                                if (state.showInlineEditor) {
                                    InlineEditorPanel(
                                        title = state.inlineTitle,
                                        content = state.inlineContent,
                                        onTitleChange = { viewModel.updateInlineTitle(it) },
                                        onContentChange = { viewModel.updateInlineContent(it) },
                                        onSave = { viewModel.initiateWrite(effectiveOwnerDid, state.inlineTitle, state.inlineContent) },
                                        onCancel = { viewModel.hideInlineEditor() })
                                } else if (state.selectedItem != null) {
                                    VaultViewSection(state.selectedItem!!,
                                        onBack = { viewModel.reset() }, onEdit = { viewModel.enterEditMode() })
                                } else if (state.isEditing) {
                                    VaultEditSection(state.editTitle, state.editContent,
                                        onSave = { t, c -> viewModel.initiateWrite(effectiveOwnerDid, t, c) },
                                        onCancel = { viewModel.cancelEdit() })
                                } else {
                                    VaultItemListSection(
                                        listState = listState,
                                        items = state.items, isLoading = state.isLoading,
                                        searchQuery = state.searchQuery, syncedItemIds = state.syncedItemIds,
                                        pinnedItemIds = state.pinnedItemIds,
                                        onItemClick = { viewModel.initiateRead(it.id, effectiveOwnerDid) },
                                        onItemDelete = { viewModel.initiateDelete(it.id, effectiveOwnerDid) },
                                        onItemPin = { viewModel.togglePin(it.id) })
                                }
                            }
                        }
                    }
                    VaultStep.SYNCING -> LoadingSection("正在同步至节点...")
                    VaultStep.BIOMETRIC_PROMPT -> SovexisBiometricPrompt(
                        title = "验证身份", subtitle = "请使用指纹或面部识别",
                        onSuccess = { viewModel.onBiometricSuccess() }, onFailed = { viewModel.onBiometricFailed(it) })
                    VaultStep.KDFS_DRAW -> KdfsDrawSection { viewModel.onKdfsComplete(it) }
                    VaultStep.DECRYPTING -> LoadingSection("正在解密...")
                    VaultStep.ENCRYPTING -> LoadingSection("正在加密保存...")
                    VaultStep.DELETING -> LoadingSection("正在删除...")
                    VaultStep.COMPLETED -> SuccessSection(state.successMessage ?: "操作成功")
                    VaultStep.FAILED -> ErrorSection(state.error ?: "操作失败", onRetry = { viewModel.reset() })
                }
            }
        }
    }
}

// ═══════════════ FAB Sub-Button ═══════════════
@Composable
private fun FabSubBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick, modifier = Modifier.size(SubFabSize), shape = CircleShape,
        containerColor = Color.White, contentColor = DeepTeal, elevation = FloatingActionButtonDefaults.elevation(2.dp)) {
        Icon(icon, label, Modifier.size(20.dp))
    }
}

// ═══════════════ Sync Status Bar ═══════════════
@Composable
private fun SyncStatusBar(
    lastSyncAt: Long, syncedCount: Int, totalCount: Int, failCount: Int,
    onRefresh: () -> Unit, onClearFail: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val minutesAgo = (System.currentTimeMillis() - lastSyncAt) / 60000

    Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (failCount > 0) Icons.Default.CloudOff else Icons.Default.CloudDone,
            null, Modifier.size(16.dp),
            tint = if (failCount > 0) SyncOrange else SyncGreen)
        Spacer(Modifier.width(6.dp))
        Text("上次同步：${if (minutesAgo < 1) "刚刚" else "${minutesAgo}分钟前"}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (failCount > 0) {
            Spacer(Modifier.width(6.dp))
            Text("($failCount 失败)", style = MaterialTheme.typography.labelSmall, color = SyncRed)
        }
        Spacer(Modifier.weight(1f))
        Text(if (expanded) "收起" else "详情", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
    }

    AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("已同步笔记：$syncedCount/$totalCount", style = MaterialTheme.typography.labelSmall)
                if (failCount > 0) {
                    TextButton(onClick = onClearFail, contentPadding = PaddingValues(4.dp)) {
                        Text("清除", style = MaterialTheme.typography.labelSmall, color = SyncOrange)
                    }
                }
            }
            if (syncedCount < totalCount) {
                LinearProgressIndicator(progress = { syncedCount.toFloat() / totalCount.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = if (failCount > 0) SyncOrange else SyncGreen)
            }
        }
    }
}

// ═══════════════ Inline Editor Panel ═══════════════
@Composable
private fun InlineEditorPanel(
    title: String, content: String,
    onTitleChange: (String) -> Unit, onContentChange: (String) -> Unit,
    onSave: () -> Unit, onCancel: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                IconButton(onCancel) { Icon(Icons.Default.Close, "取消", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("新建笔记", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSave, enabled = title.isNotBlank()) { Text("保存", color = DeepTeal) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { onTitleChange(it) }, placeholder = { Text("标题") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DeepTeal))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(content, { onContentChange(it) }, placeholder = { Text("内容") },
                modifier = Modifier.fillMaxWidth().weight(1f), maxLines = Int.MAX_VALUE,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DeepTeal))
        }
    }
}

// ═══════════════ Sync History Full Page ═══════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncHistoryFullPage(
    history: List<SyncHistoryEntry>,
    onBack: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    onClearAll: () -> Unit
) {
    // Group by date
    val grouped = history.groupBy {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("同步记录") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = onClearAll) { Text("清空所有", color = SyncRed) }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudDone, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f))
                    Spacer(Modifier.height(12.dp))
                    Text("暂无同步记录", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (date, entries) ->
                    item(key = date) {
                        Text(date, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(entries, key = { it.id }) { entry ->
                        SyncHistoryCard(entry, onDelete = { onDeleteEntry(entry.id) })
                    }
                }
                // Most recent summary
                if (history.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        val latest = history.first()
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))) {
                            Column(Modifier.padding(16.dp)) {
                                Text("最近一次同步", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("时间：${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(latest.timestamp))}", style = MaterialTheme.typography.bodySmall)
                                Text("结果：${latest.successCount} 成功 / ${latest.failCount} 失败 (共 ${latest.totalItems})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncHistoryCard(entry: SyncHistoryEntry, onDelete: () -> Unit) {
    val swipeAnim = remember { Animatable(0f) }
    val swScope = rememberCoroutineScope()
    val density = LocalDensity.current; val delPx = with(density) { 80.dp.toPx() }
    val sAnim = remember { spring<Float>(dampingRatio = 0.7f) }

    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Delete underlay
        Box(Modifier.align(Alignment.CenterEnd).width(80.dp).fillMaxHeight()
            .background(SyncRed.copy(0.9f), RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .clickable { onDelete(); swScope.launch { swipeAnim.animateTo(0f, sAnim) } },
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Delete, null, Modifier.size(22.dp), tint = Color.White)
                Text("删除", style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 9.sp)
            }
        }
        // Card
        Box(Modifier.offset { IntOffset(swipeAnim.value.roundToInt(), 0) }.fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { swScope.launch {
                        if (swipeAnim.value < -delPx * 0.35f) swipeAnim.animateTo(-delPx, sAnim)
                        else swipeAnim.animateTo(0f, sAnim)
                    } },
                    onHorizontalDrag = { _, d -> swScope.launch { swipeAnim.snapTo((swipeAnim.value + d).coerceIn(-delPx, 0f)) } })
            }
        ) {
            Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (entry.status) {
                            "success" -> Icons.Default.CheckCircle
                            "failed" -> Icons.Default.Error
                            else -> Icons.Default.Warning
                        }, null, Modifier.size(24.dp),
                        tint = when (entry.status) {
                            "success" -> SyncGreen; "failed" -> SyncRed; else -> SyncOrange
                        })
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp)),
                            style = MaterialTheme.typography.bodySmall)
                        Text("${entry.successCount} 成功 / ${entry.failCount} 失败 (共 ${entry.totalItems})",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ═══════════════ Item List ═══════════════
@Composable
private fun VaultItemListSection(
    listState: androidx.compose.foundation.lazy.LazyListState,
    items: List<PlainVaultItem>, isLoading: Boolean, searchQuery: String = "",
    syncedItemIds: Set<String> = emptySet(), pinnedItemIds: Set<String> = emptySet(),
    onItemClick: (PlainVaultItem) -> Unit, onItemDelete: (PlainVaultItem) -> Unit,
    onItemPin: (PlainVaultItem) -> Unit
) {
    if (isLoading) { LoadingSection("加载中..."); return }

    val filtered = remember(items, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) items
        else items.filter { it.title.lowercase().contains(q) }
    }
    val sorted = remember(filtered, pinnedItemIds) {
        filtered.sortedByDescending { pinnedItemIds.contains(it.id) }
    }

    if (sorted.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (searchQuery.isNotEmpty()) {
                    Icon(Icons.Default.SearchOff, null, Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Text("没有匹配的笔记", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Icon(Icons.Default.Lock, null, Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))
                    Text("暂无加密笔记", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("点击右下角 + 创建第一条", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
        return
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sorted, key = { it.id }) { item ->
            VaultItemCard(item, item.id in syncedItemIds, item.id in pinnedItemIds,
                onClick = { onItemClick(item) }, onDelete = { onItemDelete(item) }, onPin = { onItemPin(item) })
        }
    }
}

// ═══════════════ Item Card with swipe ═══════════════
@Composable
private fun VaultItemCard(
    item: PlainVaultItem, isSynced: Boolean, isPinned: Boolean,
    onClick: () -> Unit, onDelete: () -> Unit, onPin: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val density = LocalDensity.current; val btnPx = with(density) { SwipeBtn.toPx() }
    val maxRight = btnPx; val maxLeft = -btnPx
    val swipeOffset = remember { Animatable(0f) }
    val anim = remember { spring<Float>(dampingRatio = 0.7f) }
    val swScope = rememberCoroutineScope()

    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Left: delete
        Box(Modifier.align(Alignment.CenterStart).width(SwipeBtn).fillMaxHeight()
            .background(Color(0xFFEF5350).copy(0.9f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .clickable { showDeleteDialog = true; swScope.launch { swipeOffset.animateTo(0f, anim) } },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Delete, null, Modifier.size(22.dp), tint = Color.White)
        }
        // Right: pin
        Box(Modifier.align(Alignment.CenterEnd).width(SwipeBtn).fillMaxHeight()
            .background(PinBlue.copy(0.9f), RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .clickable { onPin(); swScope.launch { swipeOffset.animateTo(0f, anim) } },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PushPin, null, Modifier.size(22.dp).rotate(if (isPinned) 0f else 45f), tint = Color.White)
        }
        // Card
        Box(Modifier.offset { IntOffset(swipeOffset.value.roundToInt(), 0) }.fillMaxSize()
            .pointerInput(maxRight) {
                detectHorizontalDragGestures(
                    onDragEnd = { swScope.launch {
                        when { swipeOffset.value > btnPx * 0.35f -> swipeOffset.animateTo(maxRight, anim)
                            swipeOffset.value < -btnPx * 0.35f -> swipeOffset.animateTo(maxLeft, anim)
                            else -> swipeOffset.animateTo(0f, anim) } } },
                    onDragCancel = { swScope.launch { swipeOffset.animateTo(0f, anim) } },
                    onHorizontalDrag = { _, d -> swScope.launch { swipeOffset.snapTo((swipeOffset.value + d).coerceIn(maxLeft, maxRight)) } })
            }) {
            Card(Modifier.fillMaxSize().clickable(onClick = onClick),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isPinned) { Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = PinBlue); Spacer(Modifier.width(6.dp)) }
                    Icon(Icons.Default.Lock, null, tint = DeepTeal, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDate(item.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.authorName.isNotEmpty()) {
                                Text(" — ${item.authorName}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (isSynced) Icon(Icons.Default.CloudDone, null, Modifier.size(16.dp).padding(end = 4.dp), tint = SyncGreen)
                    else Icon(Icons.Default.CloudQueue, null, Modifier.size(16.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                }
            }
        }
    }
    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { showDeleteDialog = false }, title = { Text("确认删除") },
        text = { Text("确定要删除「${item.title}」吗？") },
        confirmButton = { TextButton({ showDeleteDialog = false; onDelete() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ showDeleteDialog = false }) { Text("取消") } })
}

// ═══════════════ View / Edit / Utility ═══════════════
@Composable private fun VaultViewSection(item: PlainVaultItem, onBack: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Text("查看笔记", style = MaterialTheme.typography.titleMedium)
            IconButton(onEdit) { Icon(Icons.Default.Edit, "编辑") }
        }
        Spacer(Modifier.height(24.dp))
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("更新于 ${formatDate(item.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.authorName.isNotEmpty()) Text(" — ${item.authorName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text(item.content, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable private fun VaultEditSection(initT: String, initC: String, onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf(initT) }; var content by remember { mutableStateOf(initC) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton(onCancel) { Icon(Icons.Default.Close, "取消") }
            Text("编辑笔记", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onSave(title, content) }) { Text("保存", color = DeepTeal) }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(content, { content = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth().weight(1f), maxLines = Int.MAX_VALUE)
    }
}
@Composable private fun LoadingSection(msg: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(msg) } } }
@Composable private fun SuccessSection(msg: String) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = SyncGreen); Spacer(Modifier.height(12.dp)); Text(msg) } } }
@Composable private fun ErrorSection(error: String, onRetry: () -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Error, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.height(12.dp)); Text(error); Spacer(Modifier.height(8.dp)); OutlinedButton(onRetry) { Text("重试") } } } }
@Composable private fun KdfsDrawSection(onComplete: (ByteArray) -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { KdfsPatternView(onPatternComplete = onComplete, modifier = Modifier.size(280.dp)) } } }
private fun formatDate(ts: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
