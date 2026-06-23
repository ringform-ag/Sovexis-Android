package com.sovexis.ui.feature.serviceprovider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 服务商管理 Screen
 *
 * 入口默认隐藏，仅当用户完成服务商升级流程后显示。
 * 开发版本可通过 SettingsScreen 开发者选项手动开启查看。
 *
 * 绑定 ServiceProviderViewModel 方法：
 * - confirmBid / rejectBid — 报价确认/拒绝
 * - queryRevenue — 收益查询
 * - lockServiceStake / unlockServiceStake — 质押锁定/释放
 */

data class StakeInputState(
    val showLockDialog: Boolean = false,
    val showUnlockDialog: Boolean = false,
    val lockAmount: String = "",
    val stakeServiceDID: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceProviderScreen(
    viewModel: ServiceProviderViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var stakeInput by remember { mutableStateOf(StakeInputState()) }
    var disputeInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务商管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            uiState.snackbar?.let { msg ->
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Section: 报价管理 ──
            item {
                Text(
                    "报价管理",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (uiState.pendingBids.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )) {
                        Text(
                            "暂无待处理报价",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.pendingBids, key = { it.requestId }) { bid ->
                    BidCard(
                        bid = bid,
                        onConfirm = { viewModel.confirmBid(bid.requestId) },
                        onReject = { viewModel.rejectBid(bid.requestId) }
                    )
                }
            }

            // ── Section: 质押管理 ──
            item {
                Text(
                    "质押管理",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            stakeInput = stakeInput.copy(showLockDialog = true)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("锁定质押")
                    }

                    OutlinedButton(
                        onClick = {
                            stakeInput = stakeInput.copy(showUnlockDialog = true)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("释放质押")
                    }
                }
            }

            // ── Section: 收益 ──
            item {
                Text(
                    "收益概况",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("累计收益", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "%.2f SOV".format(uiState.revenueTotal),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        if (uiState.revenueBreakdown.isNotEmpty()) {
                            uiState.revenueBreakdown.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${item.serviceType} (${item.contractCount}单)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "%.2f".format(item.amount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.queryRevenue() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("刷新收益")
                        }
                    }
                }
            }
        }

        // ── Dialogs ──

        // 质押锁定对话框
        if (stakeInput.showLockDialog) {
            AlertDialog(
                onDismissRequest = { stakeInput = StakeInputState() },
                title = { Text("锁定质押") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = stakeInput.stakeServiceDID,
                            onValueChange = { stakeInput = stakeInput.copy(stakeServiceDID = it) },
                            label = { Text("服务 DID") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = stakeInput.lockAmount,
                            onValueChange = { stakeInput = stakeInput.copy(lockAmount = it) },
                            label = { Text("质押金额") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val amt = stakeInput.lockAmount.toDoubleOrNull() ?: 0.0
                        if (stakeInput.stakeServiceDID.isNotEmpty() && amt > 0) {
                            viewModel.lockServiceStake(stakeInput.stakeServiceDID, amt)
                            stakeInput = StakeInputState()
                        }
                    }) { Text("确认") }
                },
                dismissButton = {
                    TextButton(onClick = { stakeInput = StakeInputState() }) { Text("取消") }
                }
            )
        }

        // 质押释放对话框
        if (stakeInput.showUnlockDialog) {
            AlertDialog(
                onDismissRequest = { stakeInput = StakeInputState() },
                title = { Text("释放质押") },
                text = {
                    OutlinedTextField(
                        value = stakeInput.stakeServiceDID,
                        onValueChange = { stakeInput = stakeInput.copy(stakeServiceDID = it) },
                        label = { Text("服务 DID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (stakeInput.stakeServiceDID.isNotEmpty()) {
                            viewModel.unlockServiceStake(stakeInput.stakeServiceDID)
                            stakeInput = StakeInputState()
                        }
                    }) { Text("确认释放") }
                },
                dismissButton = {
                    TextButton(onClick = { stakeInput = StakeInputState() }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun BidCard(
    bid: BidProposal,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(bid.serviceType, fontWeight = FontWeight.Bold)
                Text(
                    "${bid.price} ${bid.priceUnit}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "容量: %.1f GB | SLA: %.0f%% | 有效期: %s".format(bid.capacity, bid.sla * 100, bid.expiresAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onReject) { Text("拒绝") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm) { Text("确认") }
            }
        }
    }
}
