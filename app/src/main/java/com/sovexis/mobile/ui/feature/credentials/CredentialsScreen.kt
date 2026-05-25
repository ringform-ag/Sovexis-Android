package com.sovexis.mobile.ui.feature.credentials

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
fun CredentialsScreen(
    viewModel: CredentialsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SovexisScaffold(
        accounts = emptyList(),
        activeDid = uiState.activeAccount?.did,
        currentRoute = "credentials",
        onAccountSelected = { },
        onNavigate = { },
        onAddSubAccount = { },
        onStewardAccount = { },
        topBarTitle = "å‡­è¯"
    ) { paddingValues: PaddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            // [TODO] å®žçŽ°å‡­è¯ç®¡ç†é¡µé¢
            // - å‡­è¯åˆ—è¡¨ï¼ˆç±»åž‹å›¾æ ?+ åç§° + çŠ¶æ€ï¼‰
            // - å‡­è¯è¯¦æƒ…å…¥å£
            // - å‡ºç¤ºå‡­è¯å…¥å£
            // - ZKP éªŒè¯å…¥å£

            items(uiState.credentials) { credential ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(credential.credentialType, style = MaterialTheme.typography.titleMedium)
                        Text("ID: ${credential.credentialId}", style = MaterialTheme.typography.bodySmall)
                        Text("çŠ¶æ€? ${credential.status.name}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
