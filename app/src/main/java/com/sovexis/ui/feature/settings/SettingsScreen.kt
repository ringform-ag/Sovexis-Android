package com.sovexis.ui.feature.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
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
        accounts = emptyList(), activeDid = null, currentRoute = "settings",
        onAccountSelected = { }, onAddSubAccount = { }, onStewardAccount = { },
        onNavigate = { route -> navController?.navigate(route) {
            popUpTo(SovexisRoute.Home.route) { inclusive = false }; launchSingleTop = true } },
        topBarTitle = "设置"
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp)
                .fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // === 节点连接 ===
            SectionTitle("我的节点")
            var editingHost by remember { mutableStateOf(uiState.nodeHost) }
            var editingPort by remember { mutableStateOf(uiState.nodePort.toString()) }
            var showNodeEdit by remember { mutableStateOf(false) }

            SettingsItem(Icons.Default.Lan, "节点地址",
                "${uiState.nodeHost}:${uiState.nodePort}")
            TextButton(onClick = { showNodeEdit = !showNodeEdit }) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("编辑节点配置")
            }
            if (showNodeEdit) {
                OutlinedTextField(editingHost, { editingHost = it },
                    label = { Text("IP 地址") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(editingPort, { editingPort = it },
                    label = { Text("端口") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    viewModel.setNodeHost(editingHost)
                    viewModel.setNodePort(editingPort.toIntOrNull() ?: 8100)
                    showNodeEdit = false
                }) { Text("保存并连接") }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("主权与安全")

            // 存储安全级别
            val storageLabels = listOf("L0 标准", "L1 虚假读取混淆", "L2 Path ORAM（主权级）")
            SettingsItem(Icons.Default.Storage, "存储安全级别", storageLabels[uiState.storageLevel])
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                storageLabels.forEachIndexed { i, label ->
                    FilterChip(selected = uiState.storageLevel == i,
                        onClick = { viewModel.setStorageLevel(i) },
                        label = { Text("L$i") })
                }
            }

            Spacer(Modifier.height(8.dp))

            // 通信安全级别
            val commLabels = listOf("C0 明文", "C1 Noise IK", "C2 Noise XK")
            SettingsItem(Icons.Default.Shield, "通信安全级别", commLabels[uiState.communicationLevel])
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                commLabels.forEachIndexed { i, label ->
                    FilterChip(selected = uiState.communicationLevel == i,
                        onClick = { viewModel.setCommunicationLevel(i) },
                        label = { Text("C$i") })
                }
            }

            Spacer(Modifier.height(8.dp))

            // TSS 高安全模式
            SettingsRow(Icons.Default.GppMaybe, "TSS 高安全模式",
                if (uiState.tssEnabled) "阈值签名协同已启用" else "已关闭",
                { Switch(uiState.tssEnabled, { viewModel.setTssEnabled(it) }) })

            // KDFS 缓存时间
            val kdfsOptions = listOf(1, 5, 15, 30)
            var kdfsExpanded by remember { mutableStateOf(false) }
            SettingsItem(Icons.Default.Timer, "KDFS 缓存时间", "${uiState.kdfsCacheMinutes} 分钟")
            Box(Modifier.fillMaxWidth()) {
                TextButton(onClick = { kdfsExpanded = true }) { Text("修改") }
                DropdownMenu(kdfsExpanded, { kdfsExpanded = false }) {
                    kdfsOptions.forEach { m ->
                        DropdownMenuItem(text = { Text("$m 分钟") },
                            onClick = { viewModel.setKdfsCacheMinutes(m); kdfsExpanded = false })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("隐私与通信")

            // 隐蔽传输
            SettingsRow(Icons.Default.VisibilityOff, "隐蔽传输",
                if (uiState.covertEnabled) "虚拟事件注入已启用" else "已关闭",
                { Switch(uiState.covertEnabled, { viewModel.setCovertEnabled(it) }) })

            // 虚拟事件注入比例
            if (uiState.covertEnabled) {
                Text("虚拟事件注入比例：${(uiState.injectionRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = uiState.injectionRatio,
                    onValueChange = { viewModel.setInjectionRatio(it) },
                    valueRange = 0.1f..0.5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }

            // 协商失败策略
            val fallbackLabels = listOf("保守回退（逐级降级）", "用户预设（使用上一次配置）", "断开连接")
            SettingsItem(Icons.Default.SwapHoriz, "协商失败策略", fallbackLabels[uiState.fallbackStrategy])
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                fallbackLabels.forEachIndexed { i, label ->
                    FilterChip(selected = uiState.fallbackStrategy == i,
                        onClick = { viewModel.setFallbackStrategy(i) },
                        label = { Text(label.take(4)) })
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("身份与恢复")

            SettingsItem(Icons.Default.Restore, "恢复方法管理", "助记词 / 社交恢复 / 网络分片")
            OutlinedButton(
                onClick = { navController?.navigate(SovexisRoute.IdentityManagement.route) { launchSingleTop = true } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("进入身份管理") }

            Spacer(Modifier.height(16.dp))
            SectionTitle("设备信息")

            SettingsItem(Icons.Default.PhoneAndroid, "StrongBox 安全芯片",
                if (uiState.strongBoxAvailable) "可用 — 密钥存储于硬件安全模块" else "不可用 — 设备不支持",
                if (uiState.strongBoxAvailable) "✅" else "❌")
            SettingsItem(Icons.Default.Info, "Sovexis Android MVP", "v2.1.0 · did:self · ZKP 选择性披露")

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp))
    Divider(modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, switch: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        switch()
    }
}
