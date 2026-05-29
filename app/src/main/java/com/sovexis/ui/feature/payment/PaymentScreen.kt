package com.sovexis.ui.feature.payment

import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
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
        if (state.step == PaymentStep.COMPLETED) {
            snackbarHostState.showSnackbar("支付成功")
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
        snackbarHostState = snackbarHostState
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state.step) {
                PaymentStep.IDLE -> PaymentInputSection(
                    onInitiate = { from, to, amt -> viewModel.initiatePayment(from, to, amt) }
                )
                PaymentStep.POLICY_CHECK -> LoadingSection("正在检查支付策略...")
                PaymentStep.HIGH_RISK_DIALOG -> HighRiskDialogSection(
                    isRooted = state.isDeviceRooted,
                    onResult = { viewModel.onHighRiskDialogResult(it) }
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
                PaymentStep.COMPLETED -> SuccessSection(state.txId)
                PaymentStep.FAILED -> FailedSection(state.error, { viewModel.reset() }, onPaymentFailed)
            }
        }
    }
}

@Composable
private fun PaymentInputSection(
    onInitiate: (String, String, Double) -> Unit
) {
    var fromDid by remember { mutableStateOf("") }
    var toDid by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("发起支付", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(fromDid, { fromDid = it }, label = { Text("支付方 DID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(toDid, { toDid = it }, label = { Text("收款方 DID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(amount, { amount = it }, label = { Text("金额 (AGT)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val v = amount.toDoubleOrNull() ?: 0.0
                if (fromDid.isNotBlank() && toDid.isNotBlank() && v > 0) onInitiate(fromDid, toDid, v)
            },
            enabled = fromDid.isNotBlank() && toDid.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
            modifier = Modifier.fillMaxWidth()
        ) { Text("确认支付") }
    }
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
private fun HighRiskDialogSection(isRooted: Boolean, onResult: (Int) -> Unit) {
    HighRiskDialog("支付签名", isRooted,
        onSingleRound = { onResult(1) }, onTwoRound = { onResult(2) }, onDismiss = { onResult(0) })
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
private fun SuccessSection(txId: String?) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("支付成功", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
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
