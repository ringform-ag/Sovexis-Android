package com.sovexis.mobile.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sovexis.mobile.ui.components.SovexisScaffold

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SovexisScaffold(
        accounts = emptyList(),
        activeDid = null,
        currentRoute = "settings",
        onAccountSelected = { },
        onNavigate = { },
        onAddSubAccount = { },
        onStewardAccount = { },
        topBarTitle = "设置"
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // [TODO] 实现设置页面
            // - 主题切换
            // - 语言切换
            // - 安全设置（生物认证、阈值签名模式）
            // - 通信设置（隐私中继配置）
            // - 存储管理
            // - 关于

            Text("设置页面", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text("StrongBox 可用: ${uiState.strongBoxAvailable}")
        }
    }
}
