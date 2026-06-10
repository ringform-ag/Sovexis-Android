package com.sovexis.ui.feature.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.ui.components.SovexisBiometricPrompt
import com.sovexis.ui.components.AccountStateHolder
import com.sovexis.domain.storage.PlainVaultItem
import com.sovexis.ui.navigation.SovexisRoute
import com.sovexis.ui.zkp.KdfsPatternView
import android.view.WindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.init()
    }

    LaunchedEffect(state.step) {
        if (state.step == VaultStep.COMPLETED) {
            delay(1000L)
            viewModel.reset()
            viewModel.init()
        }
    }

    val effectiveOwnerDid = state.ownerDid.ifEmpty { ownerDid }
    val globalAccounts by AccountStateHolder.accounts.collectAsState()
    // 统一过滤：仅显示主账号 + 当前活跃的副账号（与 SovexisScaffold 一致）
    val displayAccounts = globalAccounts.filter {
        it.accountType == com.sovexis.domain.identity.AccountType.MASTER || it.isActive
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            com.sovexis.ui.components.SovexisDrawer(
                accounts = displayAccounts,
                currentRoute = SovexisRoute.Vault.route,
                onAccountSelected = { },
                onNavigate = { route ->
                    navController?.navigate(route) {
                        popUpTo(SovexisRoute.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("保险箱") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "打开菜单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        // 同步按钮
                        if (state.syncMessage != null && !state.isSyncing) {
                            Text(state.syncMessage!!,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp))
                        }
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).padding(end = 8.dp),
                                strokeWidth = 2.dp)
                        }
                        IconButton(
                            onClick = { viewModel.syncAllToNode() },
                            enabled = !state.isSyncing && state.items.isNotEmpty()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "同步至节点",
                                tint = if (state.syncedItemIds.size >= state.items.size && state.items.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.startNewItem() }) {
                            Icon(Icons.Default.Add, contentDescription = "新建笔记")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                when (state.step) {
                    VaultStep.IDLE -> {
                        if (state.isEditing) {
                            VaultEditSection(
                                initialTitle = state.editTitle,
                                initialContent = state.editContent,
                                onSave = { title, content ->
                                    viewModel.initiateWrite(effectiveOwnerDid, title, content)
                                },
                                onCancel = { viewModel.cancelEdit() }
                            )
                        } else if (state.selectedItem != null) {
                            VaultViewSection(
                                item = state.selectedItem!!,
                                onBack = { viewModel.reset() },
                                onEdit = { viewModel.enterEditMode() }
                            )
                        } else {
                            VaultItemListSection(
                                items = state.items,
                                isLoading = state.isLoading,
                                syncedItemIds = state.syncedItemIds,
                                onItemClick = { item ->
                                    viewModel.initiateRead(item.id, effectiveOwnerDid)
                                },
                                onItemDelete = { item ->
                                    viewModel.initiateDelete(item.id, effectiveOwnerDid)
                                }
                            )
                        }
                    }
                    VaultStep.SYNCING -> LoadingSection("正在同步至节点...")
                    VaultStep.BIOMETRIC_PROMPT -> {
                        SovexisBiometricPrompt(
                            title = "验证身份",
                            subtitle = "请使用指纹或面部识别",
                            onSuccess = { viewModel.onBiometricSuccess() },
                            onFailed = { error -> viewModel.onBiometricFailed(error) }
                        )
                    }
                    VaultStep.KDFS_DRAW -> {
                        KdfsDrawSection(
                            onPatternComplete = { kdfsHash ->
                                viewModel.onKdfsComplete(kdfsHash)
                            }
                        )
                    }
                    VaultStep.DECRYPTING -> LoadingSection("正在解密...")
                    VaultStep.ENCRYPTING -> LoadingSection("正在加密保存...")
                    VaultStep.DELETING -> LoadingSection("正在删除...")
                    VaultStep.COMPLETED -> SuccessSection(state.successMessage ?: "操作成功")
                    VaultStep.FAILED -> {
                        ErrorSection(
                            error = state.error ?: "操作失败",
                            onRetry = { viewModel.reset() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItemListSection(
    items: List<PlainVaultItem>,
    isLoading: Boolean,
    syncedItemIds: Set<String> = emptySet(),
    onItemClick: (PlainVaultItem) -> Unit,
    onItemDelete: (PlainVaultItem) -> Unit
) {
    if (isLoading && items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 64.dp))
        }
    } else if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("保险箱为空", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("点击右上角 + 创建第一条加密笔记", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                VaultItemCard(
                    item = item,
                    isSynced = item.id in syncedItemIds,
                    onClick = { onItemClick(item) },
                    onDelete = { onItemDelete(item) }
                )
            }
        }
    }
}

@Composable
private fun VaultItemCard(
    item: PlainVaultItem,
    isSynced: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDate(item.updatedAt), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.authorName.isNotEmpty()) {
                        Text(" — ${item.authorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // 同步状态指示器
            Icon(
                imageVector = if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = if (isSynced) "已同步" else "未同步",
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                tint = if (isSynced) Color(0xFF34A853) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${item.title}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun VaultViewSection(item: PlainVaultItem, onBack: () -> Unit, onEdit: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("查看笔记", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("更新于 ${formatDate(item.updatedAt)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.authorName.isNotEmpty()) {
                Text(" — ${item.authorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(item.content, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun VaultEditSection(
    initialTitle: String, initialContent: String,
    onSave: (String, String) -> Unit, onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, contentDescription = "取消") }
            Text("编辑笔记", style = MaterialTheme.typography.titleMedium)
            IconButton(
                onClick = { if (title.isNotBlank() && content.isNotBlank()) onSave(title, content) },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Icon(Icons.Default.Check, contentDescription = "保存",
                    tint = if (title.isNotBlank() && content.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("内容") },
            modifier = Modifier.fillMaxWidth().weight(1f), maxLines = Int.MAX_VALUE)
    }
}

@Composable
private fun KdfsDrawSection(onPatternComplete: (ByteArray) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("请绘制您的安全图案", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("主权级存储需要额外验证", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))
        KdfsPatternView(gridSize = 4, minPoints = 6, onPatternComplete = onPatternComplete,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f))
    }
}

@Composable
private fun LoadingSection(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(message, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SuccessSection(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(message, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ErrorSection(error: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Error, contentDescription = null,
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(error, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRetry) { Text("返回") }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
