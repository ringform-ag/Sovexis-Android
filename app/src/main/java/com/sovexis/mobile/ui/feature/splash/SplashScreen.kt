package com.sovexis.mobile.ui.feature.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sovexis.mobile.ui.theme.SovexisPrimary

@Composable
fun SplashScreen(
    uiState: SplashUiState,
    onNavigateToHome: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Sovexis",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = SovexisPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ä¸»æƒèŠ‚ç‚¹",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(48.dp))
                CircularProgressIndicator()
            }
        }
    }

    // æ ¹æ®çŠ¶æ€å¯¼èˆ?    if (!uiState.isLoading) {
        LaunchedEffect(uiState.hasIdentity) {
            if (uiState.hasIdentity == true) {
                onNavigateToHome()
            } else {
                onNavigateToOnboarding()
            }
        }
    }
}
