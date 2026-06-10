package com.sovexis.ui.feature.home

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.ui.components.NotificationHolder
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavHostController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 消费 ViewModel 导航事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is com.sovexis.core.common.UiEvent.Navigate -> {
                    navController?.navigate(event.route) {
                        popUpTo(SovexisRoute.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
                is com.sovexis.core.common.UiEvent.ShowSnackbar -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }
                is com.sovexis.core.common.UiEvent.ShowError -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }
            }
        }
    }

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // 滚动到底部（新消息）
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // 节点/模型下拉菜单
    var showNodeMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }

    SovexisScaffold(
        accounts = uiState.allAccounts,
        activeDid = uiState.activeAccount?.did,
        currentRoute = SovexisRoute.Home.route,
        onAccountSelected = viewModel::selectAccount,
        onNavigate = viewModel::navigate,
        onAddSubAccount = {
            navController?.navigate(SovexisRoute.AddSubAccount.route) { launchSingleTop = true }
        },
        onStewardAccount = {
            navController?.navigate(SovexisRoute.IdentityManagement.route) { launchSingleTop = true }
        },
        topBarTitle = "Sovexis",
        snackbarHostState = snackbarHostState,
        onCancelTransaction = { txId -> viewModel.cancelTransaction(txId) },
        actions = {
            val unreadCount by NotificationHolder.notifications.collectAsState().let {
                remember { derivedStateOf { NotificationHolder.unreadCount() } }
            }
            BadgedBox(badge = { if (unreadCount > 0) Badge(containerColor = MaterialTheme.colorScheme.error) { Text("$unreadCount") } }) {
                IconButton(onClick = { navController?.navigate(SovexisRoute.Notifications.route) { launchSingleTop = true } }) {
                    Icon(Icons.Default.Notifications, contentDescription = "通知")
                }
            }
        }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ===== 顶部选择器：节点 + 模型 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 节点选择器
                Box {
                    AssistChip(
                        onClick = { showNodeMenu = true },
                        label = { Text(uiState.selectedNode, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                if (uiState.nodeConnected) Icons.Default.Cloud else Icons.Default.Dns,
                                null, Modifier.size(14.dp),
                                tint = if (uiState.nodeConnected) Color(0xFF34A853)
                                       else if (uiState.selectedNode != "本地模式") Color(0xFFFBBC04)
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp))
                        },
                        modifier = Modifier.height(32.dp)
                    )
                    DropdownMenu(expanded = showNodeMenu, onDismissRequest = { showNodeMenu = false }) {
                        uiState.availableNodes.forEach { node ->
                            DropdownMenuItem(
                                text = { Text(node, fontSize = 13.sp) },
                                onClick = { viewModel.selectNode(node); showNodeMenu = false },
                                leadingIcon = {
                                    if (node == uiState.selectedNode)
                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        }
                    }
                }

                // 模型选择器
                Box {
                    AssistChip(
                        onClick = { showModelMenu = true },
                        label = { Text(uiState.selectedModel, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Psychology, null, Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp))
                        },
                        modifier = Modifier.height(32.dp)
                    )
                    DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                        uiState.availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model, fontSize = 13.sp) },
                                onClick = { viewModel.selectModel(model); showModelMenu = false },
                                leadingIcon = {
                                    if (model == uiState.selectedModel)
                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
            }

            // ===== 消息列表 =====
            if (uiState.messages.isEmpty()) {
                // 空状态
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Psychology, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("Sovexis 本地助手", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("连接节点后可使用完整 AI 对话", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { msg ->
                        ChatBubble(message = msg)
                    }
                    // 加载中指示器
                    if (uiState.isLoading) {
                        item {
                            Row(Modifier.padding(start = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("思考中...", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ===== 输入栏 =====
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = { viewModel.updateInput(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入消息...", fontSize = 14.sp) },
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.sendMessage() },
                        enabled = uiState.inputText.isNotBlank() && !uiState.isLoading,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50))
                            .background(
                                if (uiState.inputText.isNotBlank() && !uiState.isLoading)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(Icons.Default.Send, "发送",
                            modifier = Modifier.size(18.dp).rotate(-45f),
                            tint = if (uiState.inputText.isNotBlank() && !uiState.isLoading)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!message.isUser) {
            Row(Modifier.padding(start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, null, Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text("Sovexis", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isUser) 16.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 16.dp
                ))
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(message.content, style = MaterialTheme.typography.bodyMedium, color = textColor)
        }
    }
}
