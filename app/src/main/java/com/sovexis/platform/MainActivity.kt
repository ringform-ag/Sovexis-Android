package com.sovexis.platform

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.sovexis.ui.feature.splash.SplashScreen
import com.sovexis.ui.feature.splash.SplashViewModel
import com.sovexis.ui.navigation.SovexisNavHost
import com.sovexis.ui.theme.SovexisPrimary
import com.sovexis.ui.theme.SovexisTheme
import com.sovexis.ui.theme.themePresetIndex
import dagger.hilt.android.AndroidEntryPoint

/**
 * Sovexis 主入口 Activity
 *
 * 状态栏保留：通过 Modifier.windowInsetsPadding(WindowInsets.statusBars) 在各页面处理。
 *
 * 架构：Splash 从 NavHost 中抽出，作为 MainActivity 的前置阶段。
 * 主题切换（Crossfade）只影响主 App 树，绝不会重新触发启动页生物认证。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ① setContent 前从 SharedPreferences 还原上次配色
        val saved = getSharedPreferences("sovexis_theme", MODE_PRIVATE).getInt("theme_preset", 0)
        themePresetIndex = saved

        setContent {
            var splashDone by remember { mutableStateOf(false) }
            var startOnWelcome by remember { mutableStateOf(false) }

            Crossfade(targetState = splashDone, animationSpec = tween(500)) { done ->
                if (!done) {
                    // ── 阶段 1：Splash（独立组合根，不参与 SovexisTheme·Crossfade）──
                    SplashApp(
                        onNavigateToHome = { splashDone = true },
                        onNavigateToWelcome = {
                            startOnWelcome = true
                            splashDone = true
                        }
                    )
                } else {
                    // ── 阶段 2：主 App（SovexisTheme + Crossfade 主题切换）──
                    SovexisTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            SovexisNavHost(startRoute = if (startOnWelcome) "welcome" else "home")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 独立 Splash 阶段——简单 MaterialTheme 深色表面，不与主 App 的 SovexisTheme 共享组合根。
 * 完成生物认证后回调 [onDone]，MainActivity Crossfade 平滑过渡到主 App。
 */
@Composable
private fun SplashApp(onNavigateToHome: () -> Unit, onNavigateToWelcome: () -> Unit) {
    val viewModel: SplashViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // 最小 MaterialTheme，仅用于 SplashScreen 内部的 colorScheme 引用
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0A1618),
            onBackground = Color(0xFFD0EDEE),
            surface = Color(0xFF0A1618),
            onSurface = Color(0xFFD0EDEE),
            onSurfaceVariant = Color(0xFF90B8BA)
        )
    ) {
        when (uiState.step) {
            com.sovexis.ui.feature.splash.SplashStep.CHECKING -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(visible = visible, enter = fadeIn(tween(800))) {
                    Column(Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text("Sovexis", style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold, color = SovexisPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text("主权锚点", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(48.dp))
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp,
                            color = SovexisPrimary.copy(alpha = 0.6f))
                        Spacer(Modifier.height(16.dp))
                        Text("正在初始化…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }

            com.sovexis.ui.feature.splash.SplashStep.AUTH_REQUIRED -> {
                SplashScreen(
                    viewModel = viewModel,
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToWelcome = onNavigateToWelcome
                )
            }

            com.sovexis.ui.feature.splash.SplashStep.LOADING -> {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp,
                        color = SovexisPrimary.copy(alpha = 0.6f))
                }
            }

            com.sovexis.ui.feature.splash.SplashStep.READY -> {
                LaunchedEffect(uiState.hasIdentity) {
                    if (uiState.hasIdentity == true) onNavigateToHome()
                    else onNavigateToWelcome()
                }
            }
        }
    }
}
