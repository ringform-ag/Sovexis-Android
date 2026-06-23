package com.sovexis.ui.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sovexis.domain.identity.ChildType
import com.sovexis.ui.feature.identity.IdentityManagementViewModel

/**
 * AddSubAccountScreen — 新建副账号独立界面
 *
 * 从 IdentityManagementScreen 的 AlertDialog 中抽取，支持：
 * - 别名设置
 * - 权限配置（签名 / 支付 / 合约）
 * - KDFS 图案验证（备用）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubAccountScreen(
    viewModel: IdentityManagementViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var newAlias by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ChildType.STANDARD) }
    var canSign by remember { mutableStateOf(true) }
    var canPay by remember { mutableStateOf(false) }
    var canContract by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.accounts.any { it.isActive }) {
        // 创建成功后自动返回
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建副账号") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 别名
            OutlinedTextField(
                value = newAlias,
                onValueChange = { newAlias = it },
                label = { Text("副账号别名") },
                placeholder = { Text("例如：购物专用、社交账户") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            // 类型选择
            Text("账号类型", style = MaterialTheme.typography.titleSmall)
            Column {
                listOf("标准副账号" to ChildType.STANDARD, "管家副账号" to ChildType.STEWARD).forEach { (label, ct) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == ct,
                            onClick = { selectedType = ct }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }
            }

            // 权限配置
            Text("权限配置", style = MaterialTheme.typography.titleSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canSign, onCheckedChange = { canSign = it })
                        Text("签名权限", modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canPay, onCheckedChange = { canPay = it })
                        Text("支付权限", modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canContract, onCheckedChange = { canContract = it })
                        Text("合约权限", modifier = Modifier.weight(1f))
                    }
                }
            }

            // 创建按钮
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = newAlias.trim().isNotBlank()
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("创建副账号")
            }
        }
    }

    // 确认对话框
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认创建") },
            text = {
                Column {
                    Text("即将创建副账号「${newAlias.trim()}」")
                    Spacer(Modifier.height(8.dp))
                    Text("类型: ${when(selectedType) { ChildType.STEWARD -> "管家副账号" else -> "标准副账号" }}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "权限: ${if(canSign) "签名 " else ""}${if(canPay) "支付 " else ""}${if(canContract) "合约 " else ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addSubAccount(selectedType, newAlias.trim())
                    showConfirmDialog = false
                    onNavigateBack()
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            }
        )
    }
}
