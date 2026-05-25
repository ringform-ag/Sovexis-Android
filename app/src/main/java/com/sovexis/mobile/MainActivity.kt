package com.sovexis.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sovexis.mobile.ui.navigation.SovexisNavHost
import com.sovexis.mobile.ui.theme.SovexisTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Sovexis 主入口 Activity
 *
 * 注意：禁止使用 WindowCompat.setDecorFitsSystemWindows(window, false)
 *       禁止使用 enableEdgeToEdge()
 *       状态栏保留由各页面通过 Modifier.windowInsetsPadding(WindowInsets.statusBars) 处理
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SovexisTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SovexisNavHost()
                }
            }
        }
    }
}
