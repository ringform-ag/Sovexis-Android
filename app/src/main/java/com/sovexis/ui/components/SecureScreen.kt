package com.sovexis.ui.components

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Sovexis 安全屏幕包装器 — 自动添加 FLAG_SECURE 防截屏。
 *
 * 替代各页面中重复的 DisposableEffect(FLAG_SECURE) 代码块。
 *
 * 用法：
 *   SecureScreen {
 *       YourScreenContent()
 *   }
 */
@Composable
fun SecureScreen(content: @Composable () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    content()
}
