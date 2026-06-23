@file:Suppress("all")

package com.sovexis.ui.feature.credential

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 合约承接底部弹窗 — 倒计时 + 受影响合约 + 副账号选择 + 操作按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractTransferBottomSheet(
    uiState: ContractTransferUiState,
    onAccept: (selectedDID: String) -> Unit,
    onDismiss: () -> Unit,
    onSelectAccount: (AccountOption) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.isVisible) {
        if (!uiState.isVisible) scope.launch { sheetState.hide() }
        else scope.launch { sheetState.show() }
    }

    LaunchedEffect(sheetState.isVisible) {
        if (!sheetState.isVisible && uiState.isVisible) onDismiss()
    }

    if (uiState.isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // ── Header: 倒计时 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "合约承接",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.countdownDisplay,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isCountdownWarning) Color(0xFFE53935)
                                else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "副账号 ${uiState.oldDID.takeLast(12)} 的身份委派已失效，以下合约需要新的承接身份。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(12.dp))

                // ── 受影响合约列表 ──
                Text(
                    "受影响合约 (${uiState.contractIDs.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    if (uiState.contractIDs.isEmpty()) {
                        Text("无合约", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn {
                            items(uiState.contractIDs) { cid ->
                                Text(
                                    text = cid,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── 副账号选择列表 ──
                Text(
                    "选择承接账号",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))

                if (uiState.availableAccounts.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAccept("") }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("+ 新建副账号", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 160.dp)
                    ) {
                        items(uiState.availableAccounts) { account ->
                            val isSel = account.isSelected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectAccount(account) }
                                    .background(
                                        if (isSel) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSel,
                                    onClick = { onSelectAccount(account) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(account.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        account.did.takeLast(16),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAccept("") }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("+ 新建副账号", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── 操作按钮 ──
                val selectedDID = uiState.availableAccounts.firstOrNull { it.isSelected }?.did ?: ""
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isProcessing
                    ) {
                        Text("稍后处理")
                    }
                    Button(
                        onClick = { onAccept(selectedDID) },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isProcessing &&
                                (selectedDID.isNotEmpty() || uiState.availableAccounts.isEmpty())
                    ) {
                        if (uiState.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("选择承接")
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
