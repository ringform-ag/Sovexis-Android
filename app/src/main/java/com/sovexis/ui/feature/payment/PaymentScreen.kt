package com.sovexis.ui.feature.payment

import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.ui.components.SovexisBiometricPrompt
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute
import com.sovexis.ui.zkp.HighRiskDialog
import com.sovexis.ui.zkp.KdfsPatternView
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel = hiltViewModel(),
    navController: NavHostController? = null,
    onPaymentComplete: (txId: String) -> Unit = {},
    onPaymentFailed: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    LaunchedEffect(state.step) {
        if (state.step == PaymentStep.BIOMETRIC_PROMPT || state.step == PaymentStep.KDFS_DRAW) {
            delay(60000)
            if (state.step == PaymentStep.BIOMETRIC_PROMPT || state.step == PaymentStep.KDFS_DRAW) {
                viewModel.onTimeout()
            }
        }
        if (state.step == PaymentStep.SUBMITTED_PENDING) {
            snackbarHostState.showSnackbar("交易已提交，等待节点确认")
            state.txId?.let { onPaymentComplete(it) }
        }
    }

    SovexisScaffold(
        accounts = emptyList(),
        activeDid = null,
        currentRoute = "payment",
        onAccountSelected = { },
        onNavigate = { route ->
            navController?.navigate(route) {
                popUpTo(SovexisRoute.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        },
        onAddSubAccount = { },
        onStewardAccount = { },
        topBarTitle = "支付",
        snackbarHostState = snackbarHostState,
        onCancelTransaction = { txId -> viewModel.cancelTransaction(txId) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state.step) {
                PaymentStep.IDLE -> PaymentInputSection(
                    state = state,
                    viewModel = viewModel,
                    onShowRecharge = { viewModel.showRechargeDialog() }
                )
                PaymentStep.POLICY_CHECK -> LoadingSection("正在检查支付策略...")
                PaymentStep.HIGH_RISK_DIALOG -> HighRiskDialogSection(
                    state = state,
                    onResult = { viewModel.onHighRiskDialogResult(it) },
                    onSkipNextTime = { viewModel.onSkipHighRiskExplanation(it) }
                )
                PaymentStep.BIOMETRIC_PROMPT -> SovexisBiometricPrompt(
                    title = "支付验证",
                    subtitle = "请使用指纹或面部识别确认支付",
                    onSuccess = { viewModel.onBiometricSuccess(it) },
                    onFailed = { viewModel.onBiometricFailed(it) }
                )
                PaymentStep.KDFS_DRAW -> KdfsDrawSection(
                    onPatternComplete = { viewModel.onKdfsComplete(it) }
                )
                PaymentStep.ZKP_GENERATING -> LoadingSection("正在生成零知识证明...")
                PaymentStep.SIGNING -> LoadingSection("正在签名交易...")
                PaymentStep.SENDING -> LoadingSection("正在发送交易...")
                PaymentStep.SUBMITTED_PENDING -> PendingSection(state.txId, state.pendingAmount)
                PaymentStep.FAILED -> FailedSection(state.error, { viewModel.reset() }, onPaymentFailed)
            }

            // 充值弹窗
            if (state.showRechargeDialog) {
                RechargeDialog(
                    onDismiss = { viewModel.dismissRechargeDialog() },
                    onRecharge = { amount -> viewModel.rechargeBalance(amount) }
                )
            }
            // 收款账号池管理弹窗
            if (state.showRecipientManager) {
                RecipientManagerDialog(
                    recipients = state.savedRecipients,
                    onDismiss = { viewModel.toggleRecipientManager() },
                    onDelete = { viewModel.deleteSavedRecipient(it) }
                )
            }
        }
    }
}

// ========== 支付输入区 ==========

@Composable
private fun PaymentInputSection(
    state: PaymentState,
    viewModel: PaymentViewModel,
    onShowRecharge: () -> Unit
) {
    var amount by remember { mutableStateOf("") }

    val fromDid = state.fromDid
    val toDid = state.toDid
    val sameDid = fromDid.isNotBlank() && toDid.isNotBlank() && fromDid == toDid

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("发起支付", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // 余额显示 + 充值按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("余额", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (state.fromDid.isBlank()) "— AGT" else "%,.2f AGT".format(state.balance),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = if (state.fromDid.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (state.pendingAmount > 0 && state.fromDid.isNotBlank()) {
                    Text("挂起 %,.2f AGT（待节点确认）".format(state.pendingAmount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFA726))
                }
            }
            FilledTonalButton(onClick = onShowRecharge) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("充值", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 管理收款账号按钮
        if (state.savedRecipients.isNotEmpty()) {
            TextButton(
                onClick = { viewModel.toggleRecipientManager() },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.ManageAccounts, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("管理收款账号 (${state.savedRecipients.size})", fontSize = 12.sp)
            }
        }

        // ═══════════ 支付方 ═══════════
        Text("支付方", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))

        // 显示已选 + 下拉按钮
        Box {
            OutlinedTextField(
                value = fromDisplay(fromDid, state.fromAccounts),
                onValueChange = {},
                readOnly = true,
                label = { Text(if (fromDid.isBlank()) "选择支付账号" else "已选择") },
                trailingIcon = {
                    IconButton(onClick = { viewModel.toggleFromDropdown() }) {
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            DropdownMenu(
                expanded = state.showFromDropdown,
                onDismissRequest = { viewModel.toggleFromDropdown() },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                if (state.fromAccounts.isEmpty()) {
                    DropdownMenuItem(text = { Text("无本地账号") }, onClick = {})
                }
                state.fromAccounts.forEach { acc ->
                    val disabled = acc.did == toDid
                    val bal = state.accountBalances[acc.did]
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, Modifier.size(16.dp),
                                    tint = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.alias ?: "未命名", style = MaterialTheme.typography.bodyMedium,
                                        color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.onSurface)
                                    Text("DID:${acc.did.takeLast(10)}", style = MaterialTheme.typography.labelSmall,
                                        color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                bal?.let {
                                    Text("%,.2f AGT".format(it), style = MaterialTheme.typography.labelSmall,
                                        color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        onClick = { viewModel.selectFromAccount(acc.did) },
                        enabled = !disabled
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ═══════════ 收款方 ═══════════
        Text("收款方", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))

        Box {
            OutlinedTextField(
                value = toDisplay(toDid, state.toAccounts),
                onValueChange = { viewModel.updateManualToDid(it) },
                label = { Text(if (toDid.isBlank()) "选择或输入收款方 DID" else "已选择") },
                trailingIcon = {
                    IconButton(onClick = { viewModel.toggleToDropdown() }) {
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            DropdownMenu(
                expanded = state.showToDropdown,
                onDismissRequest = { viewModel.toggleToDropdown() },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                if (state.toAccounts.isEmpty()) {
                    DropdownMenuItem(text = { Text("无可用账号") }, onClick = {})
                }
                state.toAccounts.forEach { entry ->
                    val disabled = entry.did == fromDid
                    val bal = if (entry.isLocal) state.accountBalances[entry.did] else null
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val iconTint = if (entry.isLocal) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary
                                Icon(Icons.Default.Person, null, Modifier.size(16.dp),
                                    tint = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    else iconTint)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val name = entry.alias ?: "DID:${entry.did.takeLast(10)}"
                                    Text(name, style = MaterialTheme.typography.bodyMedium,
                                        color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.onSurface)
                                    val sub = if (entry.lastTxTime != null)
                                        "上次交易 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.lastTxTime!!))}"
                                    else if (entry.isLocal) "本地账号" else ""
                                    Text(sub, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                bal?.let {
                                    Text("%,.2f AGT".format(it), style = MaterialTheme.typography.labelSmall,
                                        color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        onClick = { viewModel.selectToAccount(entry.did) },
                        enabled = !disabled
                    )
                }
            }
        }

        // 收款方非本地时 — 保存勾选框
        if (state.saveAsRecipient) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = true, onCheckedChange = { viewModel.toggleSaveAsRecipient() })
                Text("保存为常用收款账号", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 金额
        OutlinedTextField(amount, { amount = it }, label = { Text("金额 (AGT)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true, modifier = Modifier.fillMaxWidth())

        // 同 DID 警告
        if (sameDid) {
            Spacer(Modifier.height(4.dp))
            Text("支付方与收款方不能为同一账号", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val v = amount.toDoubleOrNull() ?: 0.0
                if (fromDid.isNotBlank() && toDid.isNotBlank() && v > 0 && !sameDid)
                    viewModel.initiatePayment(fromDid, toDid, v)
            },
            enabled = fromDid.isNotBlank() && toDid.isNotBlank()
                && (amount.toDoubleOrNull() ?: 0.0) > 0 && !sameDid,
            modifier = Modifier.fillMaxWidth()
        ) { Text("确认支付") }

        Spacer(Modifier.height(80.dp))
    }
}

private fun fromDisplay(did: String, accounts: List<SovexisAccount>): String {
    if (did.isBlank()) return ""
    val acc = accounts.find { it.did == did }
    return if (acc != null) "${acc.alias ?: "未命名"} (${did.takeLast(6)})" else did.takeLast(10)
}

private fun toDisplay(did: String, accounts: List<RecipientEntry>): String {
    if (did.isBlank()) return ""
    val entry = accounts.find { it.did == did }
    return if (entry != null) {
        if (entry.alias != null) "${entry.alias} (${did.takeLast(6)})"
        else "DID:${did.takeLast(10)}"
    } else did.takeLast(10)
}

@Composable
private fun LoadingSection(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 64.dp))
            Spacer(Modifier.height(24.dp))
            Text(message, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HighRiskDialogSection(
    state: PaymentState,
    onResult: (Int) -> Unit,
    onSkipNextTime: (Boolean) -> Unit
) {
    HighRiskDialog("支付签名", state.isDeviceRooted,
        onSingleRound = { onResult(1) }, onTwoRound = { onResult(2) }, onDismiss = { onResult(0) },
        skipNextTime = state.skipHighRiskExplanation,
        onSkipNextTimeChanged = onSkipNextTime
    )
}

@Composable
private fun KdfsDrawSection(onPatternComplete: (ByteArray) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("请绘制您的安全图案", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("验证您的身份", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        KdfsPatternView(4, 6, onPatternComplete, Modifier.fillMaxWidth().aspectRatio(1f))
    }
}

@Composable
private fun PendingSection(txId: String?, pendingAmount: Double) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Schedule, null, tint = Color(0xFFFFA726), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("交易已挂起", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("%,.2f AGT 等待节点确认".format(pendingAmount),
            style = MaterialTheme.typography.bodyLarge, color = Color(0xFFFFA726), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("本地记账已完成，需 Sovexis 节点网络共识后正式生效",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("交易 ID: ${txId?.take(16)}...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FailedSection(error: String?, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("支付失败", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(error ?: "未知错误", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onCancel, Modifier.weight(1f)) { Text("取消") }
            Button(onRetry, Modifier.weight(1f)) { Text("重试") }
        }
    }
}

// ========== [TEST] 充值弹窗 — 纯测试用 ==========
// 后续接入 MockLedger 后，此弹窗将被账本充值 API 替换。
// 当前用于主副账号余额支付转移测试。

@Composable
private fun RechargeDialog(
    onDismiss: () -> Unit,
    onRecharge: (Double) -> Unit
) {
    var selectedAmount by remember { mutableStateOf(0.0) }
    val presetAmounts = listOf(50.0, 100.0, 200.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("[TEST] 充值余额") },
        text = {
            Column {
                Text(
                    "充值金额计入本地账本，Node 连接后由节点确认。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.height(12.dp))
                Text("选择金额:", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                // 快捷金额
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetAmounts.forEach { amt ->
                        FilterChip(
                            selected = selectedAmount == amt,
                            onClick = { selectedAmount = amt },
                            label = { Text("${amt.toInt()}") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("主账号余额将增加: %,.2f AGT".format(selectedAmount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(
                onClick = { onRecharge(selectedAmount) },
                enabled = selectedAmount > 0
            ) { Text("确认充值") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ========== 收款账号池管理弹窗 ==========

@Composable
private fun RecipientManagerDialog(
    recipients: List<RecipientEntry>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理收款账号") },
        text = {
            if (recipients.isEmpty()) {
                Text("暂无已保存的非本地收款账号", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(recipients) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "DID:${entry.did.takeLast(16)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    entry.lastTxTime?.let {
                                        Text(
                                            "上次交易 ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDelete(entry.did) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close, "删除",
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
