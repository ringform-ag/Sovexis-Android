package com.sovexis.ui.feature.home

import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.ui.components.SovexisDrawer
import com.sovexis.ui.components.SovexisErrorView
import com.sovexis.ui.components.SovexisLoadingIndicator
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavHostController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 消费 ViewModel 导航事件——修复抽屉点击无导航的问题
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is com.sovexis.core.common.UiEvent.Navigate -> {
                    navController?.navigate(event.route) {
                        popUpTo(SovexisRoute.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
                is com.sovexis.core.common.UiEvent.ShowSnackbar -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }
                is com.sovexis.core.common.UiEvent.ShowError -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }
            }
        }
    }

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    SovexisScaffold(
        accounts = uiState.allAccounts,
        activeDid = uiState.activeAccount?.did,
        currentRoute = SovexisRoute.Home.route,
        onAccountSelected = viewModel::selectAccount,
        onNavigate = viewModel::navigate,
        onAddSubAccount = {
            navController?.navigate(SovexisRoute.AddSubAccount.route) {
                launchSingleTop = true
            }
        },
        onStewardAccount = {
            navController?.navigate(SovexisRoute.IdentityManagement.route) {
                launchSingleTop = true
            }
        },
        topBarTitle = "Sovexis",
        snackbarHostState = snackbarHostState
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // [TODO] 实现首页内容
            // - 当前身份信息卡片
            // - 凭证概览
            // - 保险箱概览
            // - 最近活动
            uiState.activeAccount?.let { account ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("当前身份", style = MaterialTheme.typography.labelSmall)
                        Text(account.alias ?: "未命名", style = MaterialTheme.typography.titleLarge)
                        Text(account.did, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } ?: run {
                SovexisLoadingIndicator()
            }
        }
    }
}
