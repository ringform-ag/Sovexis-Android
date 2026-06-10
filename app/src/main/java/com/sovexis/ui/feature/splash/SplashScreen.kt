package com.sovexis.ui.feature.splash

import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sovexis.ui.components.SovexisBiometricPrompt
import com.sovexis.ui.theme.SovexisPrimary

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToWelcome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (uiState.step) {
            SplashStep.CHECKING -> {
                // 欢迎界面 — 淡入动画 + 1.5s 缓冲
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(800))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Sovexis", style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold, color = SovexisPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("主权锚点", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(48.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = SovexisPrimary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在初始化…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SplashStep.AUTH_REQUIRED -> {
                SovexisBiometricPrompt(
                    title = "解锁 Sovexis",
                    subtitle = if (uiState.authFailed) "认证失败，请重试" else "请使用指纹或面部识别登录",
                    onSuccess = {
                        viewModel.onBiometricSuccess(it)
                    },
                    onFailed = { error ->
                        viewModel.onBiometricFailed(error)
                    }
                )
            }

            SplashStep.READY -> {
                if (uiState.hasIdentity == true) {
                    LaunchedEffect(Unit) { onNavigateToHome() }
                } else {
                    LaunchedEffect(Unit) { onNavigateToWelcome() }
                }
            }
        }
    }
}
