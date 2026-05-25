package com.sovexis.mobile.ui.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sovexis.domain.recovery.RecoveryConfig
import com.sovexis.domain.recovery.RecoveryMethod
import com.sovexis.mobile.ui.zkp.KdfsPatternView
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIdentityScreen(
    viewModel: CreateIdentityViewModel = hiltViewModel(),
    onIdentityCreated: () -> Unit
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建您的数字主权身份") },
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
                CreateIdentityStep.IDLE -> {
                    AliasInputSection(
                        initialAlias = state.alias,
                        onAliasEntered = viewModel::onAliasEntered
                    )
                }
                CreateIdentityStep.BIOMETRIC_PROMPT -> {
                    BiometricPromptSection(
                        onSuccess = { signature ->
                            viewModel.onBiometricSuccess(signature)
                        },
                        onFailed = { error ->
                            viewModel.onBiometricFailed(error)
                        }
                    )
                }
                CreateIdentityStep.KDFS_DRAW_FIRST -> {
                    KdfsDrawSection(
                        title = "请绘制您的安全图案",
                        subtitle = "此图案将保护您的数字主权（至少连接 6 个点）",
                        onPatternComplete = { kdfsHash ->
                            viewModel.onKdfsFirstComplete(kdfsHash)
                        }
                    )
                }
                CreateIdentityStep.KDFS_DRAW_CONFIRM -> {
                    KdfsDrawSection(
                        title = "请再次绘制以确认",
                        subtitle = "确保您能记住此图案",
                        onPatternComplete = { kdfsHash ->
                            viewModel.onKdfsConfirmComplete(kdfsHash)
                        }
                    )
                }
                CreateIdentityStep.RECOVERY_SETUP -> {
                    RecoverySetupSection(
                        onConfigSelected = { config ->
                            viewModel.onRecoveryConfigSelected(config)
                        }
                    )
                }
                CreateIdentityStep.GENERATING -> {
                    GeneratingSection()
                }
                CreateIdentityStep.COMPLETED -> {
                    LaunchedEffect(Unit) {
                        onIdentityCreated()
                    }
                }
            }

            // 错误提示
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearError) {
                            Text("确定")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun AliasInputSection(
    initialAlias: String,
    onAliasEntered: (String) -> Unit
) {
    var alias by remember { mutableStateOf(initialAlias) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "为您的数字身份设置一个别名",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "此别名仅用于本地显示，不会上传到任何服务器",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it },
            label = { Text("别名") },
            placeholder = { Text("例如：我的主账号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onAliasEntered(alias) },
            enabled = alias.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始创建")
        }
    }
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
    title: String,
    subtitle: String,
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
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
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
private fun RecoverySetupSection(
    onConfigSelected: (RecoveryConfig) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(RecoveryMethod.MNEMONIC) }
    var showMnemonicWarning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "选择账户恢复方式",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "选择一种方式在设备丢失时恢复您的身份",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 助记词恢复（推荐）
        RecoveryMethodCard(
            title = "助记词恢复（推荐）",
            description = "生成 12 个单词，安全保管后可离线恢复",
            isSelected = selectedMethod == RecoveryMethod.MNEMONIC,
            onClick = { selectedMethod = RecoveryMethod.MNEMONIC }
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 社交恢复
        RecoveryMethodCard(
            title = "社交恢复",
            description = "由您信任的监护人协助恢复",
            isSelected = selectedMethod == RecoveryMethod.SOCIAL,
            onClick = { selectedMethod = RecoveryMethod.SOCIAL }
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 网络分片恢复
        RecoveryMethodCard(
            title = "加密网络恢复",
            description = "将加密分片存储于公共加密网络节点",
            isSelected = selectedMethod == RecoveryMethod.NETWORK_SHARD,
            onClick = { selectedMethod = RecoveryMethod.NETWORK_SHARD }
        )

        Spacer(modifier = Modifier.weight(1f))

        // 助记词警告提示
        if (selectedMethod == RecoveryMethod.MNEMONIC) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ 重要提示",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "助记词是恢复身份的唯一方式，请将其写在纸上并妥善保管，不要截图或拍照。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                if (selectedMethod == RecoveryMethod.MNEMONIC) {
                    showMnemonicWarning = true
                } else {
                    val config = RecoveryConfig(
                        enabledMethods = listOf(selectedMethod),
                        socialThreshold = 3,
                        networkShardCount = 3,
                        networkShardThreshold = 2,
                        timeLockHours = 24
                    )
                    onConfigSelected(config)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确认并生成身份")
        }
    }

    // 助记词警告对话框
    if (showMnemonicWarning) {
        AlertDialog(
            onDismissRequest = { showMnemonicWarning = false },
            title = { Text("确认使用助记词恢复") },
            text = {
                Text("您选择了助记词恢复方式。创建完成后，请务必安全保管生成的 12 个单词。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMnemonicWarning = false
                        val config = RecoveryConfig(
                            enabledMethods = listOf(RecoveryMethod.MNEMONIC),
                            socialThreshold = 3,
                            networkShardCount = 3,
                            networkShardThreshold = 2,
                            timeLockHours = 24
                        )
                        onConfigSelected(config)
                    }
                ) {
                    Text("我已了解")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMnemonicWarning = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun RecoveryMethodCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GeneratingSection() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "正在生成您的数字主权身份...",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "正在创建加密密钥、配置恢复机制",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
