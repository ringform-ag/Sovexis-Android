package com.sovexis.mobile.ui.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sovexis.mobile.ui.components.SovexisDrawer
import com.sovexis.mobile.ui.components.SovexisErrorView
import com.sovexis.mobile.ui.components.SovexisLoadingIndicator
import com.sovexis.mobile.ui.components.SovexisScaffold
import com.sovexis.mobile.ui.navigation.SovexisRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    SovexisScaffold(
        accounts = uiState.allAccounts,
        activeDid = uiState.activeAccount?.did,
        currentRoute = SovexisRoute.Home.route,
        onAccountSelected = viewModel::selectAccount,
        onNavigate = viewModel::navigate,
        onAddSubAccount = { /* TODO: å¯¼èˆªåˆ°æ·»åŠ å‰¯è´¦å· */ },
        onStewardAccount = { /* TODO: å¯¼èˆªåˆ°ç®¡å®¶å‰¯è´¦å· */ },
        topBarTitle = "Sovexis",
        snackbarHostState = snackbarHostState
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // [TODO] å®žçŽ°é¦–é¡µå†…å®¹
            // - å½“å‰èº«ä»½ä¿¡æ¯å¡ç‰‡
            // - å‡­è¯æ¦‚è§ˆ
            // - ä¿é™©ç®±æ¦‚è§?            // - æœ€è¿‘æ´»åŠ?
            uiState.activeAccount?.let { account ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("å½“å‰èº«ä»½", style = MaterialTheme.typography.labelSmall)
                        Text(account.alias, style = MaterialTheme.typography.titleLarge)
                        Text(account.did, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } ?: run {
                SovexisLoadingIndicator()
            }
        }
    }
}
