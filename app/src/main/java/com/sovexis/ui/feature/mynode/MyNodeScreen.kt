package com.sovexis.ui.feature.mynode

import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute

private val OnlineGreen = Color(0xFF34A853)
private val OfflineGray = Color(0xFF9AA0A6)

@Composable
fun MyNodeScreen(
    viewModel: MyNodeViewModel = hiltViewModel(),
    navController: NavHostController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    SovexisScaffold(
        accounts = emptyList(), activeDid = null, currentRoute = "my_node",
        onAccountSelected = { }, onAddSubAccount = { }, onStewardAccount = { },
        onNavigate = { route -> navController?.navigate(route) {
            popUpTo(SovexisRoute.Home.route) { inclusive = false }; launchSingleTop = true } },
        topBarTitle = "我的节点"
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp)
                .fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // === 连接状态卡片 ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isConnected) OnlineGreen.copy(alpha = 0.08f)
                                     else OfflineGray.copy(alpha = 0.08f))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (uiState.isConnected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            null,
                            tint = if (uiState.isConnected) OnlineGreen else OfflineGray,
                            modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (uiState.isConnected) "已连接" else if (uiState.isConnecting) "连接中…" else "未连接",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("最后连接: ${uiState.lastConnectedTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (uiState.nodeDid.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("DID: ${uiState.nodeDid.take(24)}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uiState.nodeVersion.isNotEmpty()) {
                        Text("版本: ${uiState.nodeVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uiState.isConnected) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (uiState.noiseKeyRegistered) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (uiState.noiseKeyRegistered) OnlineGreen else OfflineGray
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (uiState.noiseKeyRegistered) "Noise IK 握手已就绪" else "明文连接（未注册公钥）",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.noiseKeyRegistered) OnlineGreen else OfflineGray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // === 手动配置 ===
            Text("节点配置", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            var ipText by remember { mutableStateOf(uiState.nodeIp) }
            var portText by remember { mutableStateOf(uiState.nodePort.toString()) }
            var pubKeyText by remember { mutableStateOf(uiState.manualPublicKey) }
            OutlinedTextField(ipText, { ipText = it }, label = { Text("IP 地址") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(portText, { portText = it }, label = { Text("端口") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(pubKeyText, { pubKeyText = it }, label = { Text("节点公钥") },
                placeholder = { Text("粘贴 Node 的 Base64 编码公钥（可选）") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.setNodePublicKey(pubKeyText)
                    viewModel.setNode(ipText, portText.toIntOrNull() ?: 8100)
                    viewModel.connect()
                },
                enabled = !uiState.isConnecting && ipText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (uiState.isConnecting) "连接中…" else "连接") }

            uiState.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))

            // === 服务开关 ===
            Text("节点服务", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            ServiceSwitch(Icons.Default.Backup, "存储备份", "保险箱数据加密分片后上传到节点",
                uiState.storageBackupEnabled, uiState.isConnected
            ) { viewModel.setStorageBackup(it) }

            ServiceSwitch(Icons.Default.Key, "TSS 协同签名", "高安全模式支付使用节点作为第二签名方",
                uiState.tssCooEnabled, uiState.isConnected
            ) { viewModel.setTssCoo(it) }

            ServiceSwitch(Icons.Default.SmartToy, "AI 推理（即将开放）", "本地 AI Agent 推理请求转发到节点",
                uiState.aiInferenceEnabled, uiState.isConnected
            ) { viewModel.setAiInference(it) }
        }
    }

    // 公钥不匹配警告弹窗
    if (uiState.showKeyMismatch) {
        AlertDialog(
            onDismissRequest = { viewModel.rejectNewKey() },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("节点公钥已变更") },
            text = {
                Column {
                    Text("节点公钥与本地存储不匹配，可能存在中间人攻击。", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("公钥变更合法场景：重装节点、更换硬件后主动重置绑定。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("服务器公钥: ${uiState.nodePublicKey.take(24)}…",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.acceptNewKey() }) { Text("信任并更新") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.rejectNewKey() }) { Text("断开连接") }
            }
        )
    }
}

@Composable
private fun ServiceSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
    checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else OfflineGray)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onToggle, enabled = enabled)
        }
    }
}
