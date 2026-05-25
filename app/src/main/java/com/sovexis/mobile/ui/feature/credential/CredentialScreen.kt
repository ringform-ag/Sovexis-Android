@file:OptIn(ExperimentalMaterial3Api::class)

package com.sovexis.mobile.ui.feature.credential

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sovexis.mobile.ui.zkp.KdfsPatternView
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import kotlinx.coroutines.delay

@Composable
fun CredentialScreen(
    viewModel: CredentialViewModel = hiltViewModel(),
    credentialId: String? = null,
    availableFields: List<String> = emptyList(),
    challenge: ByteArray? = null,
    onPresentationComplete: () -> Unit
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
        if (state.step == CredentialStep.COMPLETED) {
            delay(1500L)
            onPresentationComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("出示凭证") },
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
                CredentialStep.IDLE -> {
                    if (credentialId != null && challenge != null) {
                        // 自动进入字段选择
                        CredentialSelectSection(
                            credentialId = credentialId,
                            availableFields = availableFields,
                            onInitiate = { selectedFields ->
                                viewModel.initiatePresentation(
                                    credentialId = credentialId,
                                    disclosedFields = selectedFields,
                                    challenge = challenge
                                )
                            }
                        )
                    } else {
                        // 显示提示
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("请选择要出示的凭证")
                        }
                    }
                }
                CredentialStep.SELECTING_FIELDS -> {
                    CredentialSelectSection(
                        credentialId = state.selectedCredentialId ?: "",
                        availableFields = availableFields,
                        onInitiate = { selectedFields ->
                            viewModel.initiatePresentation(
                                credentialId = state.selectedCredentialId ?: "",
                                disclosedFields = selectedFields,
                                challenge = state.challenge ?: byteArrayOf()
                            )
                        }
                    )
                }
                CredentialStep.KDFS_DRAW -> {
                    KdfsDrawSection(
                        onPatternComplete = { kdfsHash ->
                            viewModel.onKdfsComplete(kdfsHash)
                        }
                    )
                }
                CredentialStep.BIOMETRIC_PROMPT -> {
                    BiometricPromptSection(
                        onSuccess = { signature ->
                            viewModel.onBiometricSuccess(signature)
                        },
                        onFailed = {
                            viewModel.onBiometricFailed()
                        }
                    )
                }
                CredentialStep.ZKP_GENERATING -> {
                    LoadingSection("正在生成隐私证明...")
                }
                CredentialStep.SENDING -> {
                    LoadingSection("正在出示凭证...")
                }
                CredentialStep.COMPLETED -> {
                    SuccessSection(state.cachedProof != null)
                }
                CredentialStep.FAILED -> {
                    ErrorSection(
                        error = state.error ?: "出示失败",
                        onRetry = { viewModel.reset() }
                    )
                }
            }

            // 错误提示
            state.error?.let { error ->
                if (state.step != CredentialStep.FAILED) {
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
private fun CredentialSelectSection(
    credentialId: String,
    availableFields: List<String>,
    onInitiate: (List<String>) -> Unit
) {
    var selectedFields by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "选择要披露的信息",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "凭证 ID: ${credentialId.take(16)}...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 字段选择列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableFields) { field ->
                FieldSelectionItem(
                    field = field,
                    isSelected = field in selectedFields,
                    onToggle = {
                        selectedFields = if (field in selectedFields) {
                            selectedFields - field
                        } else {
                            selectedFields + field
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 全选/取消全选
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = { selectedFields = availableFields.toSet() }
            ) {
                Text("全选")
            }
            TextButton(
                onClick = { selectedFields = emptySet() }
            ) {
                Text("取消全选")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onInitiate(selectedFields.toList()) },
            enabled = selectedFields.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确认出示 (${selectedFields.size} 个字段)")
        }
    }
}

@Composable
private fun FieldSelectionItem(
    field: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = field,
                style = MaterialTheme.typography.bodyLarge
            )
        }
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
            text = "用于生成隐私证明",
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
private fun BiometricPromptSection(
    onSuccess: (ByteArray) -> Unit,
    onFailed: () -> Unit
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
private fun SuccessSection(usedCachedProof: Boolean) {
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
            text = "凭证出示成功",
            style = MaterialTheme.typography.headlineSmall
        )
        if (usedCachedProof) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "（使用缓存证明）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorSection(
    error: String,
    onRetry: () -> Unit
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
            text = "出示失败",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}
