package com.sovexis.ui.feature.mynode

import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sovexis.domain.identity.AccountType
import com.sovexis.domain.identity.ChildType
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.ui.components.AccountStateHolder
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val OnlineGreen = Color(0xFF34A853)
private val OfflineGray = Color(0xFF9AA0A6)
private val WarningYellow = Color(0xFFFBBC04)
private val BindingRed = Color(0xFFE53935)
private val CardBg = Color(0xFF1E1E2E)
private val SwipeBtnWidth = 44.dp

@Composable
fun MyNodeScreen(
    viewModel: MyNodeViewModel = hiltViewModel(),
    navController: NavHostController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddNodeSheet by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var pendingNode by remember { mutableStateOf<NodeConfig?>(null) }
    var selectedNode by remember { mutableStateOf<NodeConfig?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    var keyMismatchNode by remember { mutableStateOf<NodeConfig?>(null) }
    var bindingConfirmNode by remember { mutableStateOf<NodeConfig?>(null) }
    LaunchedEffect(uiState.nodes) {
        uiState.nodes.forEach { node ->
            if (!node.isConnecting && node.isConnected && node.error == null && node.lastConnected != "从未连接") {
                snackbarHostState.showSnackbar("「${node.name}」连接成功")
            }
            if (!node.isConnecting && !node.isConnected && node.error != null) {
                snackbarHostState.showSnackbar("「${node.name}」连接失败: ${node.error}")
            }
            if (node.showKeyMismatch) keyMismatchNode = node
        }
    }

    keyMismatchNode?.let { node ->
        AlertDialog(
            onDismissRequest = { viewModel.rejectNewNodeKey(node.id); keyMismatchNode = null },
            icon = { Icon(Icons.Default.Warning, null, tint = BindingRed) },
            title = { Text("节点公钥已变更") },
            text = { Text("「${node.name}」的节点公钥与之前记录的不一致。\n\n这可能是因为节点重装或绑定重置。\n\n是否接受新公钥？") },
            confirmButton = {
                Button(onClick = { viewModel.acceptNewNodeKey(node.id); keyMismatchNode = null
                    scope.launch { snackbarHostState.showSnackbar("「${node.name}」已接受新公钥") } }) { Text("接受") }
            },
            dismissButton = { OutlinedButton(onClick = { viewModel.rejectNewNodeKey(node.id); keyMismatchNode = null }) { Text("拒绝") } }
        )
    }

    bindingConfirmNode?.let { node ->
        AlertDialog(
            onDismissRequest = { bindingConfirmNode = null },
            icon = { Icon(Icons.Default.Lock, null, tint = WarningYellow) },
            title = { Text("确认绑定节点") },
            text = { Text("即将通过密码学验证绑定「${node.name}」（${node.ip}:${node.port}）。\n\n是否继续？") },
            confirmButton = {
                Button(onClick = { bindingConfirmNode = null; viewModel.toggleNodeEnabled(node.id)
                    scope.launch { snackbarHostState.showSnackbar("正在连接「${node.name}」...") } }) { Text("确认绑定") }
            },
            dismissButton = { OutlinedButton(onClick = { bindingConfirmNode = null }) { Text("取消") } }
        )
    }

    SovexisScaffold(
        accounts = emptyList(), activeDid = null, currentRoute = "my_node",
        onAccountSelected = { }, onAddSubAccount = { }, onStewardAccount = { },
        onNavigate = { route -> navController?.navigate(route) {
            popUpTo(SovexisRoute.Home.route) { inclusive = false }; launchSingleTop = true } },
        topBarTitle = "节点管理",
        snackbarHostState = snackbarHostState,
        actions = {
            Box {
                IconButton(onClick = { showAddMenu = true }) { Icon(Icons.Default.Add, contentDescription = "添加节点") }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(text = { Text("扫码添加节点") },
                        onClick = { showAddMenu = false; showAddNodeSheet = true },
                        leadingIcon = { Icon(Icons.Default.QrCodeScanner, null, Modifier.size(20.dp)) })
                    DropdownMenuItem(text = { Text("手动配置节点") },
                        onClick = { showAddMenu = false; showManualAddDialog = true },
                        leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(20.dp)) })
                }
            }
        }
    ) { paddingValues ->
        if (uiState.nodes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Router, null, Modifier.size(64.dp), tint = OfflineGray)
                    Spacer(Modifier.height(16.dp))
                    Text("暂无节点配置", style = MaterialTheme.typography.bodyLarge, color = OfflineGray)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAddMenu = true }) { Text("添加节点") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.nodes, key = { it.id }) { node ->
                    NodeCard(
                        node = node,
                        onClick = { selectedNode = node },
                        onToggleEnabled = {
                            if (!node.isEnabled) bindingConfirmNode = node
                            else viewModel.toggleNodeEnabled(node.id)
                        },
                        onNameChanged = { name -> viewModel.updateNode(node.id) { it.copy(name = name) } },
                        onDelete = {
                            viewModel.deleteNode(node.id)
                            scope.launch { snackbarHostState.showSnackbar("「${node.name}」已删除") }
                        }
                    )
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }

    // QR 扫描（触发后设置 pendingNode，弹出配置对话框）
    if (showAddNodeSheet) {
        AddNodeSheet(
            onDismiss = { showAddNodeSheet = false },
            onScanned = { scannedNode ->
                showAddNodeSheet = false
                pendingNode = scannedNode
                showConfigDialog = true
            }
        )
    }

    // 手动配置对话框（跳过扫码，直接弹配置框）
    LaunchedEffect(showManualAddDialog) {
        if (showManualAddDialog) {
            pendingNode = null
            showManualAddDialog = false
            showConfigDialog = true
        }
    }

    // 统一的节点配置对话框（扫码后预填 / 手动空白）
    if (showConfigDialog) {
        ConfigureNodeDialog(
            prefill = pendingNode,
            onDismiss = { showConfigDialog = false; pendingNode = null },
            onAdd = { node ->
                viewModel.addNode(node); showConfigDialog = false; pendingNode = null
                scope.launch { snackbarHostState.showSnackbar("「${node.name}」已添加") }
                bindingConfirmNode = node
            }
        )
    }

    selectedNode?.let { node ->
        NodeDetailSheet(
            node = node,
            onDismiss = { selectedNode = null },
            onUpdate = { updated ->
                viewModel.updateNode(node.id) { updated }
                selectedNode = null
                scope.launch { snackbarHostState.showSnackbar("「${updated.name}」已保存") }
            }
        )
    }
}

// ═════════════════════════════ 节点卡片（含左滑删除）═════════════════════════════

@Composable
private fun NodeCard(
    node: NodeConfig,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    var didVisible by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }
    var editingName by remember(node.id) { mutableStateOf(node.name) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val (statusText, statusColor) = when {
        node.isConnecting -> "连接中" to WarningYellow
        !node.isConnected -> "未连接" to OfflineGray
        node.bindingStatus == BindingStatus.BOUND -> "已绑定" to OnlineGreen
        node.bindingStatus == BindingStatus.PENDING -> "等待确认" to WarningYellow
        node.bindingStatus == BindingStatus.KEY_CHANGED -> "身份已变更" to BindingRed
        else -> "已连接" to OnlineGreen
    }

    // ══ 侧滑：左滑删除 ══
    val density = LocalDensity.current
    val btnWidthPx = with(density) { SwipeBtnWidth.toPx() }
    val maxOffset = btnWidthPx // right-swipe reveals left delete button
    var isSwipedOpen by remember { mutableStateOf(false) }
    val swipeOffset = remember { Animatable(0f) }
    val animSpec = remember { spring<Float>(dampingRatio = 0.7f, stiffness = 400f) }
    val swipeScope = rememberCoroutineScope()

    // IntrinsicSize.Min → 父容器高度由内容子项决定，fillMaxHeight 才能生效
    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Left delete button (underlay)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(SwipeBtnWidth)
                .fillMaxHeight()
                .background(Color(0xFFEF5350).copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                .clickable {
                    showDeleteDialog = true
                    swipeScope.launch { swipeOffset.animateTo(0f, animSpec); isSwipedOpen = false }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Delete, null, Modifier.size(22.dp), tint = Color.White)
                Text("删除", style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp)
            }
        }

        // Main card (overlay, drags right)
        Box(
            Modifier
                .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(maxOffset) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            swipeScope.launch {
                                if (swipeOffset.value > btnWidthPx * 0.35f) {
                                    swipeOffset.animateTo(maxOffset, animSpec); isSwipedOpen = true
                                } else {
                                    swipeOffset.animateTo(0f, animSpec); isSwipedOpen = false
                                }
                            }
                        },
                        onDragCancel = { swipeScope.launch { swipeOffset.animateTo(0f, animSpec); isSwipedOpen = false } },
                        onHorizontalDrag = { _, dragAmount ->
                            swipeScope.launch { swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceIn(0f, maxOffset)) }
                        }
                    )
                }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                // 必须 fillMaxWidth() 而非 fillMaxSize() — fillMaxSize 强制撑满高度，
                // 导致 animateContentSize + AnimatedVisibility 即使内容折叠也无法收缩卡片。
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    // Row 1: 头像 + 名称/类型 + 编辑 + 开关（参照身份卡片样式）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 头像 — 28dp 圆形，根据节点类型使用不同图标
                        val nodeAvatarIcon = when (node.bindingType) {
                            "self" -> Icons.Default.Computer
                            "network" -> Icons.Default.Language
                            else -> Icons.Default.Person
                        }
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(OnlineGreen.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(nodeAvatarIcon, null, Modifier.size(15.dp), tint = OnlineGreen)
                        }

                        Spacer(Modifier.width(8.dp))

                        // 名称 + 类型标签
                        Column(Modifier.weight(1f)) {
                            if (isEditingName) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(editingName, { editingName = it }, singleLine = true,
                                        textStyle = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp),
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                            focusedBorderColor = OnlineGreen, unfocusedBorderColor = OfflineGray))
                                    IconButton({
                                        if (editingName.isNotBlank()) onNameChanged(editingName.trim()); isEditingName = false
                                    }, Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Check, "确定", Modifier.size(12.dp), tint = OnlineGreen)
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(node.name,
                                        style = MaterialTheme.typography.titleSmall, color = Color.White,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false))
                                    IconButton(onClick = { isEditingName = true; editingName = node.name },
                                        modifier = Modifier.size(18.dp)) {
                                        Icon(Icons.Default.Edit, "编辑名称", Modifier.size(12.dp),
                                            tint = Color.White.copy(alpha = 0.5f))
                                    }
                                }
                            }
                            // 类型标签
                            val typeLabel = when (node.bindingType) {
                                "self" -> "我的节点"
                                "network" -> "网络节点"
                                else -> node.bindingType.ifEmpty { "个人节点" }
                            }
                            Text(typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp)
                        }

                        Spacer(Modifier.width(8.dp))
                        Switch(checked = node.isEnabled && node.isConnected, onCheckedChange = { onToggleEnabled() },
                            enabled = !node.isConnecting, modifier = Modifier.height(24.dp))
                    }

                    Spacer(Modifier.height(6.dp))

                    // Row 2: 连接状态 (先状态，后DID)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(6.dp))
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }

                    Spacer(Modifier.height(6.dp))

                    // Row 3: DID — 与身份卡片 "Sovexis DID:{后10位}" 完全一致的显示逻辑
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayDid = if (node.did.isNotEmpty()) {
                            node.did
                                .removePrefix("did:sovexis:")
                                .removePrefix("0x")
                                .removePrefix("node:")
                        } else ""
                        // 前缀标签（始终显示，与身份卡片的 "Sovexis DID:" 同字体同色）
                        Text("Node DID:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                        // DID 值（展开→完整hex；折叠→全部打点，不部分泄露）
                        Text(
                            if (node.did.isEmpty()) "未配置"
                            else if (didVisible) displayDid
                            else "••••••••••",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // 小眼睛（展示/隐藏）
                        IconButton(onClick = { didVisible = !didVisible }, modifier = Modifier.size(18.dp)) {
                            Icon(if (didVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.5f))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Row 4: 功能标签 — 在线展开；离线折叠（仅显示名称/状态/DID）
                    val isOnline = node.isConnected
                    AnimatedVisibility(
                        visible = isOnline,
                        enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)),
                        exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f))
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (node.services.storageAvailable) ServiceIndicator("存储", true, Icons.Default.Storage)
                            if (node.services.tssAvailable) ServiceIndicator("TSS", true, Icons.Default.Key)
                            if (node.services.aiAvailable) ServiceIndicator("AI", true, Icons.Default.Psychology)
                            if (node.services.paymentAvailable) ServiceIndicator("支付", true, Icons.Default.Payments)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除节点「${node.name}」吗？此操作不可恢复。") },
            confirmButton = {
                Button(onClick = { showDeleteDialog = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton({ showDeleteDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ServiceIndicator(name: String, available: Boolean, icon: ImageVector) {
    val color = if (available) OnlineGreen else OfflineGray
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ═════════════════════════════ 扫描二维码（解析后弹配置框）═════════════════════════════

@Composable
private fun AddNodeSheet(onDismiss: () -> Unit, onScanned: (NodeConfig) -> Unit) {
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            if (scanned.startsWith("sovexis-binding:")) {
                val parts = scanned.removePrefix("sovexis-binding:").split(":")
                if (parts.size >= 4) {
                    onScanned(NodeConfig(id = "node_${System.currentTimeMillis()}", name = "",
                        ip = parts[2], port = parts[3].toIntOrNull() ?: 8100,
                        publicKey = parts[1], pairingKey = parts[0]))
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        scanLauncher.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE); setPrompt("扫描节点二维码")
            setBeepEnabled(false); setOrientationLocked(true)
        })
    }
}

// ═════════════════════════════ 统一节点配置对话框 ═════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureNodeDialog(
    prefill: NodeConfig?,
    onDismiss: () -> Unit,
    onAdd: (NodeConfig) -> Unit
) {
    val globalAccounts by AccountStateHolder.accounts.collectAsState()
    var name by remember { mutableStateOf(prefill?.name ?: "") }
    var ip by remember { mutableStateOf(prefill?.ip ?: "192.168.1.100") }
    var port by remember { mutableStateOf((prefill?.port ?: 8100).toString()) }
    var pubKey by remember { mutableStateOf(prefill?.publicKey ?: "") }
    var pubKeyVisible by remember { mutableStateOf(false) }
    var isOwnNode by remember { mutableStateOf(true) }  // true = 自有节点, false = 网络节点
    var accountExpanded by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<SovexisAccount?>(null) }

    // 默认选中主账号（自有节点自动锁定为主账号）
    LaunchedEffect(globalAccounts) {
        if (selectedAccount == null && globalAccounts.isNotEmpty()) {
            selectedAccount = globalAccounts.firstOrNull { it.accountType == AccountType.MASTER }
                ?: globalAccounts.first()
        }
    }

    // 当切换到自有节点时，锁定为主账号
    LaunchedEffect(isOwnNode) {
        if (isOwnNode) {
            selectedAccount = globalAccounts.firstOrNull { it.accountType == AccountType.MASTER }
        }
    }

    // 仅网络节点可选的账号列表（主账号 + 标准副账号）
    val selectableAccounts = if (isOwnNode) emptyList()
        else globalAccounts.filter { it.accountType == AccountType.MASTER || it.accountType == AccountType.CHILD }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ══ 节点类型选择 ══
                Text("节点类型", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = isOwnNode,
                        onClick = { isOwnNode = true },
                        label = { Text("自有节点") },
                        leadingIcon = if (isOwnNode) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isOwnNode,
                        onClick = { isOwnNode = false },
                        label = { Text("网络节点") },
                        leadingIcon = if (!isOwnNode) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ══ 连接身份选择（仅网络节点显示） ══
                if (!isOwnNode && selectableAccounts.isNotEmpty()) {
                    Text("连接身份", style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedAccount?.let {
                                val typeStr = if (it.accountType == AccountType.MASTER) "主" else "副"
                                "${it.alias ?: it.did.take(12)} · $typeStr"
                            } ?: "请选择身份",
                            onValueChange = {},
                            readOnly = true, singleLine = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) }
                        )
                        ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                            selectableAccounts.forEach { acc ->
                                val label = acc.alias ?: acc.did.take(16)
                                val typeStr = if (acc.accountType == AccountType.MASTER) "主账号" else "副账号"
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("$label · $typeStr", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                            Text(acc.did.take(24) + "…", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    },
                                    onClick = { selectedAccount = acc; accountExpanded = false },
                                    leadingIcon = {
                                        Icon(if (acc.accountType == AccountType.MASTER) Icons.Default.Shield else Icons.Default.Person,
                                            null, Modifier.size(20.dp),
                                            tint = if (acc.isActive) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                )
                            }
                        }
                    }
                } else if (isOwnNode) {
                    // 自有节点：静默显示主账号信息
                    selectedAccount?.let { acc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, Modifier.size(14.dp), tint = OnlineGreen)
                            Spacer(Modifier.width(4.dp))
                            Text("主账号直连 · ${acc.alias ?: acc.did.take(12)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider(color = OfflineGray.copy(alpha = 0.3f))

                OutlinedTextField(name, { name = it }, label = { Text("节点名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ip, { ip = it }, label = { Text("IP 地址（IPv4/IPv6）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text("端口") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // 公钥（默认隐藏）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = if (pubKeyVisible || pubKey.isEmpty()) pubKey else "••••••••••••••••",
                        onValueChange = { pubKey = it },
                        label = { Text("节点公钥") },
                        placeholder = { Text("Base64 编码公钥") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { pubKeyVisible = !pubKeyVisible }, modifier = Modifier.size(36.dp)) {
                        Icon(if (pubKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val node = NodeConfig(
                        id = "node_${System.currentTimeMillis()}",
                        name = name.ifEmpty { "节点 ${ip.takeLast(4)}" },
                        ip = ip,
                        port = port.toIntOrNull() ?: 8100,
                        publicKey = pubKey,
                        pairingKey = prefill?.pairingKey ?: "",
                        did = selectedAccount?.did ?: "",
                        bindingType = if (isOwnNode) "self" else "network"
                    )
                    onAdd(node)
                }) { Text("添加") }
            }
        }
    )
}

// ═════════════════════════════ 节点详情设置弹窗 ═════════════════════════════

@Composable
private fun NodeDetailSheet(
    node: NodeConfig,
    onDismiss: () -> Unit,
    onUpdate: (NodeConfig) -> Unit
) {
    var name by remember { mutableStateOf(node.name) }
    var ip by remember { mutableStateOf(node.ip) }
    var port by remember { mutableStateOf(node.port.toString()) }
    var pubKey by remember { mutableStateOf(node.publicKey) }
    var pubKeyVisible by remember { mutableStateOf(false) }
    var configExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节点设置", style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.fillMaxWidth(0.94f),
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxHeight(0.55f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Capabilities
                Text("节点能力", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServiceIndicator("存储", node.services.storageAvailable, Icons.Default.Storage)
                    ServiceIndicator("TSS", node.services.tssAvailable, Icons.Default.Key)
                    ServiceIndicator("AI", node.services.aiAvailable, Icons.Default.Psychology)
                    ServiceIndicator("支付", node.services.paymentAvailable, Icons.Default.Payments)
                }

                // Binding / connection status
                val (bindText, bindColor) = when {
                    !node.isConnected -> "未连接" to OfflineGray
                    node.bindingStatus == BindingStatus.BOUND -> "已绑定" to OnlineGreen
                    node.bindingStatus == BindingStatus.PENDING -> "等待确认" to WarningYellow
                    node.bindingStatus == BindingStatus.KEY_CHANGED -> "身份已变更" to BindingRed
                    node.did.isNotEmpty() -> "已配对" to OnlineGreen
                    else -> null to OfflineGray
                }
                if (bindText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bIcon = if (node.bindingType == "self") Icons.Default.Computer else Icons.Default.Language
                        Icon(bIcon, null, Modifier.size(16.dp), tint = bindColor)
                        Spacer(Modifier.width(6.dp))
                        Text(bindText, style = MaterialTheme.typography.labelMedium, color = bindColor)
                    }
                }

                HorizontalDivider(color = OfflineGray.copy(alpha = 0.3f))

                // Collapsible config section
                Row(Modifier.fillMaxWidth().clickable { configExpanded = !configExpanded },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("节点配置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(
                        if (configExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (configExpanded) {
                    OutlinedTextField(name, { name = it }, label = { Text("节点名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ip, { ip = it }, label = { Text("IP 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(port, { port = it }, label = { Text("端口") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    // PubKey with eye toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = if (pubKeyVisible) pubKey else "••••••••••••••••",
                            onValueChange = { pubKey = it },
                            label = { Text("节点公钥") },
                            singleLine = true, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { pubKeyVisible = !pubKeyVisible }, modifier = Modifier.size(36.dp)) {
                            Icon(if (pubKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    onUpdate(node.copy(name = name, ip = ip, port = port.toIntOrNull() ?: 8100, publicKey = pubKey))
                }) { Text("保存") }
            }
        }
    )
}

@Composable
private fun ServiceSwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = OfflineGray)
        }
        Switch(checked, onToggle, enabled = enabled)
    }
}
