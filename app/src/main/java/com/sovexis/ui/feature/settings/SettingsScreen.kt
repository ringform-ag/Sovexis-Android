package com.sovexis.ui.feature.settings

import android.view.WindowManager
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute
import com.sovexis.ui.theme.ThemePresets

/** 设置行统一高度 */
private val SettingsRowHeight = 52.dp

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
            modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp)
                .fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            SectionTitle("界面主题")

            val currentTheme = ThemePresets.getOrNull(uiState.themePreset)
            var themeExpanded by remember { mutableStateOf(false) }
            SettingsDropdownRow(
                icon = Icons.Default.Palette, title = "配色方案",
                current = currentTheme?.name ?: "深空青",
                expanded = themeExpanded,
                onExpand = { themeExpanded = true },
                onDismiss = { themeExpanded = false },
                items = ThemePresets.map { it.name },
                selectedIndex = uiState.themePreset
            ) { i -> viewModel.setThemePreset(i); themeExpanded = false }

            Spacer(Modifier.height(16.dp))
            SectionTitle("节点连接")

            OutlinedTextField(
                value = uiState.nodeHost,
                onValueChange = { viewModel.setNodeHost(it) },
                label = { Text("节点 Host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.nodePort.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { viewModel.setNodePort(it) }
                },
                label = { Text("节点端口") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("主权与安全")

            val storageLabels = listOf("L0 标准", "L1 虚假读取混淆", "L2 Path ORAM（主权级）")
            var storageExpanded by remember { mutableStateOf(false) }
            SettingsDropdownRow(
                icon = Icons.Default.Storage, title = "存储安全级别",
                current = storageLabels[uiState.storageLevel],
                expanded = storageExpanded,
                onExpand = { storageExpanded = true },
                onDismiss = { storageExpanded = false },
                items = storageLabels,
                selectedIndex = uiState.storageLevel
            ) { i -> viewModel.setStorageLevel(i); storageExpanded = false }

            val commLabels = listOf("C0 明文", "C1 Noise IK", "C2 Noise XK")
            var commExpanded by remember { mutableStateOf(false) }
            SettingsDropdownRow(
                icon = Icons.Default.Shield, title = "通信安全级别",
                current = commLabels[uiState.communicationLevel],
                expanded = commExpanded,
                onExpand = { commExpanded = true },
                onDismiss = { commExpanded = false },
                items = commLabels,
                selectedIndex = uiState.communicationLevel
            ) { i -> viewModel.setCommunicationLevel(i); commExpanded = false }

            SettingsSwitchRow(Icons.Default.GppMaybe, "TSS 高安全模式",
                if (uiState.tssEnabled) "阈值签名协同已启用" else "已关闭",
                uiState.tssEnabled, { viewModel.setTssEnabled(it) })

            val kdfsOptions = listOf(1, 5, 15, 30)
            var kdfsExpanded by remember { mutableStateOf(false) }
            SettingsDropdownRow(
                icon = Icons.Default.Timer, title = "KDFS 缓存时间",
                current = "${uiState.kdfsCacheMinutes} 分钟",
                expanded = kdfsExpanded,
                onExpand = { kdfsExpanded = true },
                onDismiss = { kdfsExpanded = false },
                items = kdfsOptions.map { "$it 分钟" },
                selectedIndex = kdfsOptions.indexOf(uiState.kdfsCacheMinutes)
            ) { i -> viewModel.setKdfsCacheMinutes(kdfsOptions[i]); kdfsExpanded = false }

            Spacer(Modifier.height(16.dp))
            SectionTitle("隐私与通信")

            SettingsSwitchRow(Icons.Default.VisibilityOff, "隐蔽传输",
                if (uiState.covertEnabled) "虚拟事件注入已启用" else "已关闭",
                uiState.covertEnabled, { viewModel.setCovertEnabled(it) })

            if (uiState.covertEnabled) {
                Text("虚拟事件注入比例：${(uiState.injectionRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 34.dp))
                Slider(
                    value = uiState.injectionRatio,
                    onValueChange = { viewModel.setInjectionRatio(it) },
                    valueRange = 0.1f..0.5f, steps = 3,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp)
                )
            }

            // 协商安全等级（4 级）
            val negoLabels = listOf("公开", "普通（推荐）", "严格", "每次询问")
            val negoDescs = listOf(
                "不弹窗，自动使用安全模式",
                "弹窗三选一，30s超时自动安全模式",
                "弹窗二选一，不自动降级安全参数",
                "每次协商失败时自主决定"
            )
            var negoExpanded by remember { mutableStateOf(false) }
            SettingsDropdownRow(
                icon = Icons.Default.Security, title = "协商安全等级",
                current = negoLabels[uiState.negotiationSecurityLevel],
                expanded = negoExpanded,
                onExpand = { negoExpanded = true },
                onDismiss = { negoExpanded = false },
                items = negoLabels,
                selectedIndex = uiState.negotiationSecurityLevel
            ) { i -> viewModel.setNegotiationSecurityLevel(i); negoExpanded = false }

            // 当前等级描述
            Text(negoDescs[uiState.negotiationSecurityLevel],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp, top = 2.dp))

            Spacer(Modifier.height(16.dp))
            SectionTitle("身份与恢复")

            SettingsInfoRow(Icons.Default.Restore, "恢复方法管理", "助记词 / 社交恢复 / 网络分片")
            OutlinedButton(
                onClick = { navController?.navigate(SovexisRoute.IdentityManagement.route) { launchSingleTop = true } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) { Text("进入身份管理") }

            Spacer(Modifier.height(16.dp))
            SectionTitle("设备信息")

            SettingsInfoRow(Icons.Default.PhoneAndroid, "StrongBox 安全芯片",
                if (uiState.strongBoxAvailable) "可用 — 密钥存储于硬件安全模块" else "不可用 — 设备不支持",
                if (uiState.strongBoxAvailable) "✅" else "❌")
            SettingsInfoRow(Icons.Default.Info, "Sovexis Android MVP", "v2.1.0 · did:self · ZKP 选择性披露")

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp))
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

/** 纯信息行（无交互），统一高度 */
@Composable
private fun SettingsInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, subtitle: String, trailing: String? = null
) {
    Row(Modifier.fillMaxWidth().height(SettingsRowHeight), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

/** Switch 行，统一高度 */
@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(SettingsRowHeight), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onToggle)
    }
}

/** 统一下拉行：左侧图标+标题，右侧当前值+箭头；整行可点击；统一高度 */
@Composable
private fun SettingsDropdownRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    current: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(SettingsRowHeight).clickable { onExpand() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(current, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 4.dp))
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Anchor dropdown to the trailing edge via a 0-width spacer
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box {
                DropdownMenu(expanded, onDismiss) {
                    items.forEachIndexed { i, label ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label,
                                        fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Normal)
                                    if (i == selectedIndex) {
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Default.Check, null, Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            onClick = { onSelect(i) }
                        )
                    }
                }
            }
        }
    }
}
