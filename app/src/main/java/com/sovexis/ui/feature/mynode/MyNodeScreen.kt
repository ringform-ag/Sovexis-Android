package com.sovexis.ui.feature.mynode

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute
import kotlinx.coroutines.launch

private val OnlineGreen = Color(0xFF34A853)
private val OfflineGray = Color(0xFF9AA0A6)
private val WarningYellow = Color(0xFFFBBC04)
private val CardBg = Color(0xFF1E1E2E)
private val CardBgLight = Color(0xFF2A2A3E)

@Composable
fun MyNodeScreen(
    viewModel: MyNodeViewModel = hiltViewModel(),
    navController: NavHostController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddNodeSheet by remember { mutableStateOf(false) }
    var selectedNode by remember { mutableStateOf<NodeConfig?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // 连接结果提示
    LaunchedEffect(uiState.nodes) {
        uiState.nodes.forEach { node ->
            if (!node.isConnecting && node.isConnected && node.error == null && node.lastConnected != "从未连接") {
                snackbarHostState.showSnackbar("「${node.name}」连接成功")
            }
            if (!node.isConnecting && !node.isConnected && node.error != null) {
                snackbarHostState.showSnackbar("「${node.name}」连接失败: ${node.error}")
            }
        }
    }

    SovexisScaffold(
        accounts = emptyList(), activeDid = null, currentRoute = "my_node",
        onAccountSelected = { }, onAddSubAccount = { }, onStewardAccount = { },
        onNavigate = { route -> navController?.navigate(route) {
            popUpTo(SovexisRoute.Home.route) { inclusive = false }; launchSingleTop = true } },
        topBarTitle = "节点管理",
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = { showAddNodeSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加节点")
            }
        }
    ) { paddingValues ->
        if (uiState.nodes.isEmpty()) {
            // 空状态
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Router, null, Modifier.size(64.dp), tint = OfflineGray)
                    Spacer(Modifier.height(16.dp))
                    Text("暂无节点配置", style = MaterialTheme.typography.bodyLarge, color = OfflineGray)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAddNodeSheet = true }) { Text("添加节点") }
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
                        onToggleEnabled = { viewModel.toggleNodeEnabled(node.id) },
                        onConnect = { viewModel.connectNode(node.id) },
                        onNameChanged = { newName ->
                            viewModel.updateNode(node.id) { it.copy(name = newName) }
                        }
                    )
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }

    // 添加节点弹窗
    if (showAddNodeSheet) {
        AddNodeSheet(
            onDismiss = { showAddNodeSheet = false },
            onAdd = { node ->
                viewModel.addNode(node)
                showAddNodeSheet = false
                scope.launch { snackbarHostState.showSnackbar("「${node.name}」已添加") }
            }
        )
    }

    // 节点详情弹窗
    selectedNode?.let { node ->
        NodeDetailSheet(
            node = node,
            onDismiss = { selectedNode = null },
            onUpdate = { updated ->
                viewModel.updateNode(node.id) { updated }
                selectedNode = null
                scope.launch { snackbarHostState.showSnackbar("「${updated.name}」已保存") }
            },
            onDelete = {
                viewModel.deleteNode(node.id)
                selectedNode = null
                scope.launch { snackbarHostState.showSnackbar("「${node.name}」已删除") }
            }
        )
    }
}

// ═════════════════════════════ 节点卡片 ═════════════════════════════

@Composable
private fun NodeCard(
    node: NodeConfig,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onConnect: () -> Unit,
    onNameChanged: (String) -> Unit
) {
    var didVisible by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }
    var editingName by remember(node.id) { mutableStateOf(node.name) }

    val statusColor = when {
        node.isConnecting -> WarningYellow
        node.isConnected -> OnlineGreen
        else -> OfflineGray
    }
    val statusText = when {
        node.isConnecting -> "连接中"
        node.isConnected -> "在线"
        else -> "离线"
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(170.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // ══ 顶部行：节点名称 [+铅笔] — 连接状态 [中间] — 开关 [最右] ══
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 左区：名称 + 编辑铅笔，weight 占用剩余空间将右侧推至端
                if (isEditingName) {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = OnlineGreen,
                            unfocusedBorderColor = OfflineGray
                        )
                    )
                    IconButton(onClick = {
                        if (editingName.isNotBlank()) {
                            onNameChanged(editingName.trim())
                        }
                        isEditingName = false
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Check, "确定", Modifier.size(18.dp), tint = OnlineGreen)
                    }
                } else {
                    Text(node.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = true))
                    
                    IconButton(
                        onClick = { isEditingName = true; editingName = node.name },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Edit, "编辑名称", Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.5f))
                    }
                }

                Spacer(Modifier.width(8.dp))
                
                // 中区：连接状态
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(4.dp))
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
                
                Spacer(Modifier.width(8.dp))
                
                // 右区：启用开关（固定最右侧）
                Switch(
                    checked = node.isEnabled && node.isConnected,
                    onCheckedChange = { onToggleEnabled() },
                    enabled = !node.isConnecting,
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ══ DID 两行显示 ══
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DID:", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { didVisible = !didVisible }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        if (didVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null, Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            Text(
                if (didVisible && node.did.isNotEmpty()) node.did
                else if (node.did.isNotEmpty()) "••••••••••••••••••••"
                else "未配置",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            // ══ 底部：三个服务状态 ══
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceIndicator("存储", node.services.storageAvailable, Icons.Default.Storage)
                ServiceIndicator("TSS", node.services.tssAvailable, Icons.Default.Key)
                ServiceIndicator("AI", node.services.aiAvailable, Icons.Default.Psychology)
            }
        }
    }
}

@Composable
private fun ServiceIndicator(name: String, available: Boolean, icon: ImageVector) {
    val color = if (available) OnlineGreen else OfflineGray
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ═════════════════════════════ 添加节点弹窗 ═════════════════════════════

@Composable
private fun AddNodeSheet(
    onDismiss: () -> Unit,
    onAdd: (NodeConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("8100") }
    var pubKey by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("连接账号", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedAccount == 0, onClick = { selectedAccount = 0 })
                    Spacer(Modifier.width(4.dp))
                    Text("主账号（默认）", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedAccount == 1, onClick = { selectedAccount = 1 })
                    Spacer(Modifier.width(4.dp))
                    Text("副账号", style = MaterialTheme.typography.bodyMedium, color = OfflineGray)
                    Spacer(Modifier.width(4.dp))
                    Text("(后续开放)", style = MaterialTheme.typography.labelSmall, color = OfflineGray)
                }

                Divider(color = OfflineGray.copy(alpha = 0.3f))

                OutlinedTextField(name, { name = it }, label = { Text("节点名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ip, { ip = it }, label = { Text("IP 地址") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text("端口") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pubKey, { pubKey = it }, label = { Text("节点公钥（可选）") },
                    placeholder = { Text("Base64 编码公钥") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val nodeId = "node_${System.currentTimeMillis()}"
                onAdd(NodeConfig(
                    id = nodeId,
                    name = name.ifEmpty { "节点 ${ip.takeLast(4)}" },
                    ip = ip,
                    port = port.toIntOrNull() ?: 8100,
                    publicKey = pubKey
                ))
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

// ═════════════════════════════ 节点详情设置弹窗 ═════════════════════════════

@Composable
private fun NodeDetailSheet(
    node: NodeConfig,
    onDismiss: () -> Unit,
    onUpdate: (NodeConfig) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(node.name) }
    var ip by remember { mutableStateOf(node.ip) }
    var port by remember { mutableStateOf(node.port.toString()) }
    var pubKey by remember { mutableStateOf(node.publicKey) }
    var storageEnabled by remember { mutableStateOf(node.services.storageAvailable) }
    var tssEnabled by remember { mutableStateOf(node.services.tssAvailable) }
    var aiEnabled by remember { mutableStateOf(node.services.aiAvailable) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteFinal by remember { mutableStateOf(false) }

    if (showDeleteFinal) {
        // 二次确认
        AlertDialog(
            onDismissRequest = { showDeleteFinal = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("最终确认") },
            text = { Text("此操作将删除「${node.name}」的所有配置和数据，\n不可恢复。确认删除？") },
            confirmButton = {
                Button(
                    onClick = { showDeleteFinal = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除") }
            },
            dismissButton = { OutlinedButton({ showDeleteFinal = false }) { Text("取消") } }
        )
    } else if (showDeleteConfirm) {
        // 一次确认
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除节点「${node.name}」吗？") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; showDeleteFinal = true }) {
                    Text("继续", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ showDeleteConfirm = false }) { Text("取消") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("节点设置", modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        ),
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("删除", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.94f),
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                        .fillMaxHeight(0.55f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // === 节点服务（放上面）===
                    Text("节点服务", style = MaterialTheme.typography.titleSmall)
                    ServiceSwitchRow("存储备份", "保险箱数据加密分片上传", storageEnabled, node.isConnected) {
                        storageEnabled = it
                    }
                    ServiceSwitchRow("TSS 协同签名", "高安全支付第二签名方", tssEnabled, node.isConnected) {
                        tssEnabled = it
                    }
                    ServiceSwitchRow("AI 推理", "本地AI请求转发到节点", aiEnabled, node.isConnected) {
                        aiEnabled = it
                    }

                    Divider(color = OfflineGray.copy(alpha = 0.3f))

                    // === 节点配置（放下面）===
                    Text("节点配置", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(name, { name = it }, label = { Text("节点名称") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ip, { ip = it }, label = { Text("IP 地址") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(port, { port = it }, label = { Text("端口") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pubKey, { pubKey = it }, label = { Text("节点公钥") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())

                    if (node.did.isNotEmpty()) {
                        Text("DID: ${node.did}", style = MaterialTheme.typography.labelSmall,
                            color = OfflineGray)
                    }
                    if (node.version.isNotEmpty()) {
                        Text("版本: ${node.version}", style = MaterialTheme.typography.labelSmall,
                            color = OfflineGray)
                    }
                }
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onUpdate(node.copy(
                            name = name,
                            ip = ip,
                            port = port.toIntOrNull() ?: 8100,
                            publicKey = pubKey
                        ))
                    }) { Text("保存") }
                }
            }
        )
    }
}

@Composable
private fun ServiceSwitchRow(
    title: String, subtitle: String, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = OfflineGray)
        }
        Switch(checked, onToggle, enabled = enabled)
    }
}
