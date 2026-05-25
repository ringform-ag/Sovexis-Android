@file:OptIn(ExperimentalMaterial3Api::class)

package com.sovexis.ui.recovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sovexis.domain.recovery.MnemonicRecovery
import com.sovexis.domain.recovery.RecoveryManager
import com.sovexis.domain.recovery.RecoveryMethod
import kotlinx.coroutines.launch

/**
 * 账户恢复屏幕。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：Sovexis · 账户恢复机制完整实现指令
 *
 * 提供三条恢复路径的入口。
 *
 * @param recoveryManager 恢复管理器
 * @param onRecoveryComplete 恢复完成回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(
    recoveryManager: RecoveryManager,
    onRecoveryComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf<RecoveryMethod?>(null) }
    var mnemonicInput by remember { mutableStateOf("") }
    var isRecovering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账户恢复") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 错误消息
            errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // 恢复方法选择
            Text(
                text = "选择恢复方式",
                style = MaterialTheme.typography.titleMedium
            )

            // 助记词恢复
            RecoveryMethodCard(
                method = RecoveryMethod.MNEMONIC,
                title = "助记词恢复",
                description = "使用 BIP-39 助记词恢复账户",
                icon = Icons.Default.Key,
                isSelected = selectedMethod == RecoveryMethod.MNEMONIC,
                onClick = { selectedMethod = RecoveryMethod.MNEMONIC }
            )

            // 社交恢复
            RecoveryMethodCard(
                method = RecoveryMethod.SOCIAL,
                title = "社交恢复",
                description = "通过监护人批准恢复账户",
                icon = Icons.Default.People,
                isSelected = selectedMethod == RecoveryMethod.SOCIAL,
                onClick = { selectedMethod = RecoveryMethod.SOCIAL }
            )

            // 网络恢复
            RecoveryMethodCard(
                method = RecoveryMethod.NETWORK_SHARD,
                title = "网络恢复",
                description = "从分布式网络节点恢复分片",
                icon = Icons.Default.Cloud,
                isSelected = selectedMethod == RecoveryMethod.NETWORK_SHARD,
                onClick = { selectedMethod = RecoveryMethod.NETWORK_SHARD }
            )

            Spacer(modifier = Modifier.weight(1f))

            // 助记词输入区域
            if (selectedMethod == RecoveryMethod.MNEMONIC) {
                OutlinedTextField(
                    value = mnemonicInput,
                    onValueChange = { mnemonicInput = it },
                    label = { Text("输入助记词（用空格分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }

            // 恢复按钮
            Button(
                onClick = {
                    if (selectedMethod != null) {
                        isRecovering = true
                        errorMessage = null
                        scope.launch {
                            val result = recoveryManager.recover(
                                method = selectedMethod!!,
                                context = buildRecoveryContext(selectedMethod!!, mnemonicInput)
                            )
                            isRecovering = false
                            if (result.isSuccess) {
                                onRecoveryComplete()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMethod != null && !isRecovering
            ) {
                if (isRecovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("恢复中...")
                } else {
                    Text("开始恢复")
                }
            }
        }
    }
}

/**
 * 恢复方法卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryMethodCard(
    method: RecoveryMethod,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = if (isSelected) 4.dp else 0.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
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
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 构建恢复上下文。
 */
private fun buildRecoveryContext(
    method: RecoveryMethod,
    mnemonicInput: String
): com.sovexis.domain.recovery.RecoveryContext {
    return when (method) {
        RecoveryMethod.MNEMONIC -> {
            val words = mnemonicInput.trim().split("\\s+".toRegex())
            com.sovexis.domain.recovery.RecoveryContext(
                mnemonicWords = words
            )
        }
        RecoveryMethod.SOCIAL -> {
            com.sovexis.domain.recovery.RecoveryContext(
                guardianApprovals = emptyList()
            )
        }
        RecoveryMethod.NETWORK_SHARD -> {
            com.sovexis.domain.recovery.RecoveryContext(
                networkShards = emptyList()
            )
        }
    }
}
