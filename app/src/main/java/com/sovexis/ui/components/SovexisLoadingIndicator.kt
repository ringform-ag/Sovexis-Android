package com.sovexis.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Sovexis 全屏加载指示器
 * 使用 LinearProgressIndicator 替代 CircularProgressIndicator，
 * 避免 Compose BOM 2024.01.00 中动画库 keyframes API 不匹配导致的 NoSuchMethodError。
 */
@Composable
fun SovexisLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.5f)
        )
    }
}

/**
 * 内联小加载条——用于 LoadingSection 等场景
 */
@Composable
fun SovexisLoadingBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(modifier = modifier.fillMaxWidth().padding(horizontal = 64.dp))
}
