package com.sovexis.mobile.ui.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sovexis.mobile.ui.theme.SovexisPrimary

@Composable
fun CreateIdentityScreen(
    viewModel: CreateIdentityViewModel,
    onIdentityCreated: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdDid) {
        if (uiState.createdDid != null) {
            onIdentityCreated()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "åˆ›å»ºæ‚¨çš„èº«ä»½",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sovexis å°†ä¸ºæ‚¨ç”ŸæˆåŽ»ä¸­å¿ƒåŒ–èº«ä»½ï¼ˆDIDï¼?,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = uiState.alias,
                onValueChange = viewModel::updateAlias,
                label = { Text("åˆ«å") },
                placeholder = { Text("è¾“å…¥æ‚¨çš„åˆ«å") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = viewModel::createIdentity,
                enabled = uiState.alias.isNotBlank() && !uiState.isCreating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("åˆ›å»ºèº«ä»½")
                }
            }
        }
    }
}
