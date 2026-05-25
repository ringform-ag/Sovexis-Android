package com.sovexis.mobile.ui.feature.payment

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
import com.sovexis.mobile.ui.zkp.HighRiskDialog
import com.sovexis.mobile.ui.zkp.KdfsPatternView
import android.view.WindowManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel = hiltViewModel(),
    onPaymentComplete: (txId: String) -> Unit,
    onPaymentFailed: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // 安全设置：防止截屏/录屏
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // 超时计时器
    LaunchedEffect(state.step) {
        if (state.step == PaymentStep.BIOMETRIC_PROMPT ||
            state.step == PaymentStep.KDFS_DRAW) {
            delay(60000) // 60 秒超时
            if (state.step == PaymentStep.BIOMETRIC_PROMPT ||
                state.step == PaymentStep.KDFS_DRAW) {
                viewModel.onTimeout()
            }
        }
    }

    // 监听完成状态
    LaunchedEffect(state.step) {
        if (state.step == PaymentStep.COMPLETED) {
            state.txId?.let { onPaymentComplete(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("支付") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state.step) {
                PaymentStep.IDLE -> {
                    PaymentInputSection(
                        onInitiate = { fromDid, toDid, amount ->
                            viewModel.initiatePayment(fromDid, toDid, amount)
                        }
                    )
                }
                PaymentStep.POLICY_CHECK -> {
                    LoadingSection("正在检查支付策略...")
                }
                PaymentStep.HIGH_RISK_DIALOG -> {
                    HighRiskDialogSection(
                        onResult = { rounds ->
                            viewModel.onHighRiskDialogResult(rounds)
                        }
                    )
                }
                PaymentStep.BIOMETRIC_PROMPT -> {
                    BiometricPromptSection(
                        onSuccess = { signature ->
                            viewModel.onBiometricSuccess(signature)
                        },
                        onFailed = { error ->
                            viewModel.onBiometricFailed(error)
                        }
                    )
                }
                PaymentStep.KDFS_DRAW -> {
                    KdfsDrawSection(
                        onPatternComplete = { kdfsHash ->
                            viewModel.onKdfsComplete(kdfsHash)
                        }
                    )
                }
                PaymentStep.ZKP_GENERATING -> {
                    LoadingSection("正在生成零知识证明...")
                }
                PaymentStep.SIGNING -> {
                    LoadingSection("正在签名交易...")
                }
                PaymentStep.SENDING -> {
                    LoadingSection("正在发送交易...")
                }
                PaymentStep.COMPLETED -> {
                    SuccessSection(state.txId)
                }
                PaymentStep.FAILED -> {
                    FailedSection(
                        error = state.error,
                        onRetry = { viewModel.reset() },
                        onCancel = onPaymentFailed
                    )
                }
            }

            // 错误提示
            state.error?.let { error ->
                if (state.step != PaymentStep.FAILED) {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.reset() }) {
                                Text("重试")
                            }
                        }
                    ) {
                        Text(error)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentInputSection(
    onInitiate: (fromDid: String, toDid: String, amount: Double) -> Unit
) {
    var fromDid by remember { mutableStateOf("") }
    var toDid by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "发起支付",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = fromDid,
            onValueChange = { fromDid = it },
            label = { Text("支付方 DID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = toDid,
            onValueChange = { toDid = it },
            label = { Text("收款方 DID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("金额 (AGT)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val amountValue = amount.toDoubleOrNull() ?: 0.0
                if (fromDid.isNotBlank() && toDid.isNotBlank() && amountValue > 0) {
                    onInitiate(fromDid, toDid, amountValue)
                }
            },
            enabled = fromDid.isNotBlank() && toDid.isNotBlank() &&
                    amount.isNotBlank() && amount.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确认支付")
        }
    }
}

@Composable
private fun LoadingSection(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun HighRiskDialogSection(
    onResult: (Int) -> Unit
) {
    // 使用现有的 HighRiskDialog 组件
    HighRiskDialog(
        operationName = "支付签名",
        isRooted = false,
        onSingleRound = { onResult(1) },
        onTwoRound = { onResult(2) },
        onDismiss = { onResult(0) }
    )
}

@Composable
private fun BiometricPromptSection(
    onSuccess: (ByteArray) -> Unit,
    onFailed: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // 触发 BiometricPrompt
        // 注意：实际实现需要在 Fragment/Activity 中调用 BiometricPrompt
        // 这里使用模拟实现
        kotlinx.coroutines.delay(1000)
        // 模拟成功
        onSuccess(ByteArray(32) { it.toByte() })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "请完成生物认证",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "使用指纹或面部识别验证您的身份",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KdfsDrawSection(
    onPatternComplete: (ByteArray) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "请绘制您的安全图案",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "验证您的身份",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        KdfsPatternView(
            gridSize = 4,
            minPoints = 6,
            onPatternComplete = onPatternComplete,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
    }
}

@Composable
private fun SuccessSection(txId: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "支付成功",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "交易 ID: ${txId?.take(16)}...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FailedSection(
    error: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "支付失败",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error ?: "未知错误",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text("重试")
            }
        }
    }
}
