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
        topBarTitle = "淇濋櫓绠?
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            // [TODO] 瀹炵幇淇濋櫓绠遍〉闈?            // - 鍔犲瘑鏁版嵁椤瑰垪琛?            // - 鏁版嵁椤硅鎯呭叆鍙?            // - 鍒嗕韩鍏ュ彛锛堜唬鐞嗛噸鍔犲瘑锛?            // - ORAM 娣锋穯鐘舵€佹寚绀?
            items(uiState.items) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(item.itemType, style = MaterialTheme.typography.titleMedium)
                        Text("ID: ${item.itemId}", style = MaterialTheme.typography.bodySmall)
                        Text("鍔犲瘑: ${item.encryptedData.take(32)}...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
