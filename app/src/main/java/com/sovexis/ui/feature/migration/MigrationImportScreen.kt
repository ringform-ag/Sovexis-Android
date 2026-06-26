package com.sovexis.ui.feature.migration

import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationImportScreen(
    viewModel: MigrationImportViewModel = hiltViewModel(),
    onImportComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("从其他设备迁移") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.phase == MigrationImportPhase.Complete) onImportComplete()
                        else viewModel.cancel()
                    }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            when (state.phase) {
                MigrationImportPhase.Start -> {
                    SafetyReminderContent(onNext = { viewModel.advance() })
                }
                MigrationImportPhase.Input -> {
                    var did by remember { mutableStateOf("") }
                    var data by remember { mutableStateOf("") }

                    Icon(
                        Icons.Default.SwapHoriz, null,
                        Modifier.size(40.dp).align(Alignment.CenterHorizontally),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "输入迁移数据",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请在原设备的 Sovexis 设置页中导出身份，然后将加密数据黏贴到下方。\n"
                            + "传输通过蓝牙或 WiFi Direct 在本地点对点完成，不经任何外部服务器。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = did, onValueChange = { did = it },
                        label = { Text("被迁移主账号 DID") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠️ 此 DID 必须与导出设备上的主账号 DID 完全一致",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFFFBBF24)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = data, onValueChange = { data = it },
                        label = { Text("加密的迁移数据 (Base64)") },
                        singleLine = false, maxLines = 5, modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp)
                    )
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.import(did, data) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = did.isNotBlank() && data.isNotBlank() && !state.loading
                    ) {
                        Text("开始迁移")
                    }

                    if (state.loading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("请保持两台设备靠近…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    state.error?.let { err ->
                        Spacer(Modifier.height(12.dp))
                        Text(err, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                MigrationImportPhase.RebindNode -> {
                    if (state.loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text(state.rebindStatus ?: "正在连接节点…",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }

                MigrationImportPhase.Complete -> {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        Modifier.size(56.dp).align(Alignment.CenterHorizontally),
                        tint = Color(0xFF34D399)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "迁移完成 ✅",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "身份数据已安全迁移到本设备。\n\n"
                            + "下一步建议：\n"
                            + "• 用已注册的手指验证身份\n"
                            + "• 确认 bioHash 一致后即可正常使用\n"
                            + "• 如果原设备不再使用，请删除其上的 Sovexis 应用数据",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onImportComplete,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("进入 Sovexis") }
                }
            }

            Spacer(Modifier.weight(2f))
        }
    }
}

@Composable
private fun ColumnScope.SafetyReminderContent(onNext: () -> Unit) {
    Icon(
        Icons.Default.Shield, null,
        Modifier.size(40.dp).align(Alignment.CenterHorizontally),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "身份迁移 · 安全提醒",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "您即将从另一台设备导入身份数据。\n\n"
            + "• 传输完全在本地完成，不经过任何外部服务器\n"
            + "• 两台设备必须在物理邻近范围（蓝牙 5m / WiFi Direct 1m）\n"
            + "• 原设备的原始生物特征永远不会离开设备——只传输辅助数据\n"
            + "• 数据在传输过程中已加密（AES-256-GCM）\n\n"
            + "请在原设备上打开「设置 → 导出身份到新设备」，生成迁移数据。",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth()
    ) { Text("我已阅读，继续") }
}
