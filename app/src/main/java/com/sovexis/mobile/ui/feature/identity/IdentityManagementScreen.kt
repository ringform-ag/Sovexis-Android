package com.sovexis.mobile.ui.feature.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sovexis.mobile.data.local.entity.AccountEntity
import com.sovexis.mobile.ui.components.SovexisScaffold

@Composable
fun IdentityManagementScreen(
    viewModel: IdentityManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SovexisScaffold(
        accounts = uiState.accounts,
        activeDid = uiState.accounts.find { it.isActive }?.did,
        currentRoute = "identity_management",
        onAccountSelected = { /* TODO */ },
        onNavigate = { /* TODO */ },
        onAddSubAccount = { /* TODO */ },
        onStewardAccount = { /* TODO */ },
        topBarTitle = "èº«ä»½ç®¡ç†"
    ) { paddingValues: PaddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            // [TODO] å®žçŽ°èº«ä»½ç®¡ç†é¡µé¢
            // - DID åˆ—è¡¨
            // - DID è¯¦æƒ…å…¥å£
            // - æ·»åŠ å‰¯è´¦å·å…¥å?            // - ç®¡å®¶å‰¯è´¦å·ç®¡ç?
            items(uiState.accounts) { account ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(account.alias, style = MaterialTheme.typography.titleMedium)
                        Text(account.did, style = MaterialTheme.typography.bodySmall)
                        Text("è§’è‰²: ${account.role.name}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
