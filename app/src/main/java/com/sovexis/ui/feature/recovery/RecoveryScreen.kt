@file:OptIn(ExperimentalMaterial3Api::class)

package com.sovexis.ui.feature.recovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.view.WindowManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun RecoveryScreen(
    viewModel: RecoveryViewModel = hiltViewModel(),
    onRecoveryComplete: () -> Unit
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

    // 监听完成状态
    LaunchedEffect(state.step) {
        if (state.step == RecoveryStep.COMPLETED) {
            kotlinx.coroutines.delay(1500L)
            onRecoveryComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("恢复账户") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state.step) {
                RecoveryStep.IDLE,
                RecoveryStep.SELECTING_METHOD -> {
                    RecoveryMethodSelectionSection(
                        onSelectMethod = { method ->
                            viewModel.selectRecoveryMethod(method)
                        }
                    )
                }
                RecoveryStep.MNEMONIC_INPUT -> {
                    MnemonicInputSection(
                        onSubmit = { words, passphrase ->
                            viewModel.recoverFromMnemonic(words, passphrase)
                        },
                        onBack = { viewModel.reset() }
                    )
                }
                RecoveryStep.SOCIAL_WAITING -> {
                    SocialRecoveryWaitingSection(
                        progressMessage = state.progressMessage
                    )
                }
                RecoveryStep.NETWORK_FETCHING -> {
                    LoadingSection(state.progressMessage ?: "正在获取分片...")
                }
                RecoveryStep.RECONSTRUCTING -> {
                    LoadingSection(state.progressMessage ?: "正在重建身份...")
                }
                RecoveryStep.COMPLETED -> {
                    SuccessSection("身份恢复成功")
                }
                RecoveryStep.FAILED -> {
                    ErrorSection(
                        error = state.error ?: "恢复失败",
                        onRetry = { viewModel.reset() }
                    )
                }
                RecoveryStep.IDENTITY_IMPORT -> {
                    IdentityImportSection(
                        onFileSelected = { viewModel.importIdentity(it) },
                        onBack = { viewModel.reset() }
                    )
                }
                else -> {
                    // 其他状态
                    LoadingSection("处理中...")
                }
            }

            // 错误提示
            state.error?.let { error ->
                if (state.step != RecoveryStep.FAILED) {
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
private fun RecoveryMethodSelectionSection(
    onSelectMethod: (RecoveryMethodType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "选择恢复方式",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "请选择您在创建账户时配置的恢复方式",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        // 助记词恢复
        RecoveryMethodCard(
            icon = Icons.Default.Lock,
            title = "助记词恢复",
            description = "使用 12 个单词的助记词恢复账户",
            onClick = { onSelectMethod(RecoveryMethodType.MNEMONIC) }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 社交恢复
        RecoveryMethodCard(
            icon = Icons.Default.People,
            title = "社交恢复",
            description = "由您信任的监护人协助恢复",
            onClick = { onSelectMethod(RecoveryMethodType.SOCIAL) }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 网络恢复
        RecoveryMethodCard(
            icon = Icons.Default.Cloud,
            title = "加密网络恢复",
            description = "从分布式加密网络节点恢复",
            onClick = { onSelectMethod(RecoveryMethodType.NETWORK) }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 身份导入
        RecoveryMethodCard(
            icon = Icons.Default.FileOpen,
            title = "身份导入",
            description = "导入 .sovexis-identity 加密身份文件",
            onClick = { onSelectMethod(RecoveryMethodType.IDENTITY_IMPORT) }
        )
    }
}

@Composable
private fun RecoveryMethodCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MnemonicInputSection(
    onSubmit: (List<String>, String?) -> Unit,
    onBack: () -> Unit
) {
    var words by remember { mutableStateOf(List(12) { "" }) }
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "输入助记词",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "请输入您的 12 个助记词单词",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 助记词输入网格
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(words.size) { index ->
                OutlinedTextField(
                    value = words[index],
                    onValueChange = { newValue ->
                        words = words.toMutableList().apply {
                            this[index] = newValue.trim().lowercase()
                        }
                    },
                    label = { Text("单词 ${index + 1}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 密码短语（可选）
        if (showPassphrase) {
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("密码短语（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            TextButton(
                onClick = { showPassphrase = true },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("添加密码短语（高级）")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("返回")
            }
            Button(
                onClick = {
                    onSubmit(words, passphrase.takeIf { it.isNotBlank() })
                },
                enabled = words.all { it.isNotBlank() },
                modifier = Modifier.weight(1f)
            ) {
                Text("恢复")
            }
        }
    }
}

@Composable
private fun SocialRecoveryWaitingSection(
    progressMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "等待监护人批准",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = progressMessage ?: "正在等待...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "已通过加密通道向监护人发送恢复请求",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun LoadingSection(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun SuccessSection(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
private fun ErrorSection(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun IdentityImportSection(
    onFileSelected: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onFileSelected(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FileOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "导入加密身份文件",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "选择之前导出的 .sovexis-identity 文件\n来恢复您的身份到当前设备",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("选择文件")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "导入后需生物认证验证身份归属",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
