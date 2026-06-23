@file:Suppress("all")

package com.sovexis.ui.feature.serviceprovider

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 报价引擎配置底部弹窗 — 底价/黑白名单/自动模式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BidConfigBottomSheet(
    currentAutoBid: Boolean,
    currentMinPrices: Map<String, Double>,
    currentWhitelist: List<String>,
    currentBlacklist: List<String>,
    currentMaxContracts: Int,
    onSave: (BitConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var autoBid by remember { mutableStateOf(currentAutoBid) }
    var storagePrice by remember { mutableStateOf(currentMinPrices["storage"]?.toString() ?: "10") }
    var relayPrice by remember { mutableStateOf(currentMinPrices["relay"]?.toString() ?: "5") }
    var computePrice by remember { mutableStateOf(currentMinPrices["compute"]?.toString() ?: "20") }
    var whitelistText by remember { mutableStateOf(currentWhitelist.joinToString(",")) }
    var blacklistText by remember { mutableStateOf(currentBlacklist.joinToString(",")) }
    var maxContracts by remember { mutableStateOf(currentMaxContracts.toString()) }

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
            Text("报价配置", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // Auto-bid toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("自动报价", fontSize = 14.sp)
                Switch(checked = autoBid, onCheckedChange = { autoBid = it })
            }

            if (!autoBid) {
                Text("关闭时，报价需用户手动确认", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            Text("底价设置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            PriceRow("存储 (token/TB/月)", storagePrice) { storagePrice = it }
            PriceRow("中继 (token/GB)", relayPrice) { relayPrice = it }
            PriceRow("计算 (token/TFLOPS-h)", computePrice) { computePrice = it }

            Spacer(Modifier.height(16.dp))
            Text("白名单 (DID, 逗号分隔)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = whitelistText,
                onValueChange = { whitelistText = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                placeholder = { Text("优先接单节点DID, 逗号分隔") }
            )

            Spacer(Modifier.height(12.dp))
            Text("黑名单 (DID, 逗号分隔)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = blacklistText,
                onValueChange = { blacklistText = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                placeholder = { Text("拒接节点DID, 逗号分隔") }
            )

            Spacer(Modifier.height(12.dp))
            Text("最大并行合约数", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = maxContracts,
                onValueChange = { maxContracts = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    onSave(BitConfig(
                        autoBid = autoBid,
                        minPrices = mapOf(
                            "storage" to (storagePrice.toDoubleOrNull() ?: 10.0),
                            "relay" to (relayPrice.toDoubleOrNull() ?: 5.0),
                            "compute" to (computePrice.toDoubleOrNull() ?: 20.0)
                        ),
                        whitelist = whitelistText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        blacklist = blacklistText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        maxActiveContracts = maxContracts.toIntOrNull() ?: 10
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存配置")
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(110.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
    }
}

data class BitConfig(
    val autoBid: Boolean,
    val minPrices: Map<String, Double>,
    val whitelist: List<String>,
    val blacklist: List<String>,
    val maxActiveContracts: Int
)
