package com.sovexis.ui.feature.identity

import android.view.WindowManager
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
import com.sovexis.domain.identity.AccountType
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute
import java.text.SimpleDateFormat
import java.util.*

private val Gold = Color(0xFFFFD700)
private val SteelBlue = Color(0xFF4682B4)

@Composable
fun IdentityManagementScreen(
    viewModel: IdentityManagementViewModel = hiltViewModel(),
    navController: NavHostController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    SovexisScaffold(
        accounts = uiState.accounts,
        activeDid = uiState.accounts.find { it.isActive }?.did,
        currentRoute = "identity_management",
        onAccountSelected = { did -> viewModel.setActive(did) },
        onNavigate = { route ->
            navController?.navigate(route) {
                popUpTo(SovexisRoute.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        },
        onAddSubAccount = {
            navController?.navigate(SovexisRoute.AddSubAccount.route) { launchSingleTop = true }
        },
        onStewardAccount = { },
        topBarTitle = "身份管理",
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = {
                navController?.navigate(SovexisRoute.AddSubAccount.route) { launchSingleTop = true }
            }) {
                Icon(Icons.Default.Add, contentDescription = "新建身份")
            }
        }
    ) { paddingValues: PaddingValues ->
        val main = uiState.accounts.filter { it.accountType == AccountType.MASTER }
        val children = uiState.accounts.filter { it.accountType != AccountType.MASTER }

        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            // 主账号信息
            if (main.isNotEmpty()) {
                item { SectionHeader("主账号") }
                items(main) { account -> MasterAccountCard(account) }
            }

            // 副账号管理
            if (children.isNotEmpty()) {
                item { SectionHeader("副账号列表") }
                items(children, key = { it.did }) { account ->
                    ChildAccountCard(
                        account = account,
                        onSetActive = { viewModel.setActive(account.did) },
                        onFreeze = { viewModel.setFrozen(account.did, true) },
                        onUnfreeze = { viewModel.setFrozen(account.did, false) },
                        onDelete = { viewModel.delete(account.did) }
                    )
                }
            }

            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun MasterAccountCard(account: SovexisAccount) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, Modifier.size(28.dp), tint = Gold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(account.alias ?: "未命名", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Surface(color = Gold.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                            Text("主账号",
                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = Gold)
                        }
                    }
                    Text(account.did, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("创建时间: ${formatDate(account.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (account.isActive) {
                Text("当前活跃", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ChildAccountCard(
    account: SovexisAccount,
    onSetActive: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFreezeDialog by remember { mutableStateOf(false) }
    val isMaster = account.accountType == AccountType.MASTER
    val badgeColor = if (isMaster) Gold else SteelBlue
    val typeLabel = when (account.accountType) {
        AccountType.MASTER -> "主账号"
        AccountType.CHILD -> "标准副账号"
        AccountType.STEWARD -> "管家"
        AccountType.SERVICE -> "服务商"
    }
    val typeIcon = when (account.accountType) {
        AccountType.STEWARD -> Icons.Default.Computer
        AccountType.SERVICE -> Icons.Default.Build
        else -> Icons.Default.PersonOutline
    }

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(typeIcon, null, Modifier.size(28.dp),
                    tint = if (account.isFrozen) Color.Gray else badgeColor)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(account.alias ?: "未命名", style = MaterialTheme.typography.titleMedium,
                            color = if (account.isFrozen) Color.Gray else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(8.dp))
                        Surface(color = badgeColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                            Text(typeLabel,
                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = badgeColor)
                        }
                    }
                    Text("DID: ${account.did.take(16)}…", style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("创建: ${formatDate(account.createdAt)} · 状态: ${if (account.isFrozen) "已熔断" else if (account.isActive) "活跃" else "正常"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (account.isFrozen) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!account.isActive && !account.isFrozen) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onSetActive, Modifier.weight(1f)) { Text("切换活跃") }
                    OutlinedButton(onClick = { showFreezeDialog = true }, Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("熔断") }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    ) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)); Text("删除") }
                }
            }

            if (account.isActive) {
                Spacer(Modifier.height(4.dp))
                Text("当前活跃", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                if (account.isFrozen) {
                    OutlinedButton(onUnfreeze, Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("解除熔断") }
                }
            }

            if (account.isFrozen && !account.isActive) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onUnfreeze, Modifier.weight(1f)) { Text("解除熔断") }
                    TextButton(onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    ) { Text("删除") }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${account.alias ?: account.did.take(12)}」吗？\n此操作不可恢复，DID 将永久移除。") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    if (showFreezeDialog) {
        AlertDialog(
            onDismissRequest = { showFreezeDialog = false },
            title = { Text("确认熔断") },
            text = { Text("熔断不等于删除。DID 和密钥保持不变，但所有操作将被拦截。\n\n确认熔断「${account.alias ?: account.did.take(12)}」？") },
            confirmButton = {
                TextButton(onClick = { showFreezeDialog = false; onFreeze() }) {
                    Text("熔断", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showFreezeDialog = false }) { Text("取消") } }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return "—"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
