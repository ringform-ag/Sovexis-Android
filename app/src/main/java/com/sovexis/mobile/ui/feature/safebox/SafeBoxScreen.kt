package com.sovexis.mobile.ui.feature.safebox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sovexis.mobile.ui.components.SovexisScaffold

@Composable
fun SafeBoxScreen(
    viewModel: SafeBoxViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SovexisScaffold(
        accounts = emptyList(),
        activeDid = uiState.activeAccount?.did,
        currentRoute = "safebox",
        onAccountSelected = { },
        onNavigate = { },
        onAddSubAccount = { },
        onStewardAccount = { },
        topBarTitle = "保险箱"
    ) { paddingValues: PaddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            // [TODO] 实现保险箱页面
            // - 加密数据项列表
            // - 数据项详情入口
            // - 分享入口（代理重加密）
            // - ORAM 混淆状态指示
            items(uiState.items) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(item.itemType, style = MaterialTheme.typography.titleMedium)
                        Text("ID: ${item.itemId}", style = MaterialTheme.typography.bodySmall)
                        Text("加密: ${item.encryptedData.take(32)}...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
