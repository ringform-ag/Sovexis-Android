package com.sovexis.ui.feature.safebox

import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sovexis.domain.storage.VaultItemEntity
import com.sovexis.ui.components.SovexisScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeBoxScreen(
    viewModel: SafeBoxViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // 笔记详情查看
    var selectedItem by remember { mutableStateOf<VaultItemEntity?>(null) }

    SovexisScaffold(
        accounts = emptyList(),
        activeDid = uiState.activeDid ?: uiState.activeAccount?.did,
        currentRoute = "safebox",
        onAccountSelected = { },
        onNavigate = { },
        onAddSubAccount = { },
        onStewardAccount = { },
        topBarTitle = "保险箱"
    ) { paddingValues: PaddingValues ->
        if (uiState.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("保险箱为空", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("新建加密笔记开始使用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    SafeBoxCard(
                        item = item,
                        onClick = { selectedItem = item },
                        onDelete = { viewModel.deleteItem(item.id) },
                        onUploadToNode = { viewModel.uploadToNode(item.id) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // 笔记详情弹窗
    selectedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text("保险箱条目", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text("ID: ${item.id.take(16)}...", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            item.contentCipher.toString(Charsets.UTF_8).take(200),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = { TextButton({ selectedItem = null }) { Text("关闭") } }
        )
    }
}

/**
 * 保险箱卡片 — 侧滑操作。
 *
 * 左侧 swipe → 删除（红色）
 * 右侧 swipe → 上传节点（蓝色）
 * 点击 → 查看笔记内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafeBoxCard(
    item: VaultItemEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onUploadToNode: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val dismissThreshold = 200f

    val bgColor by animateColorAsState(
        when {
            offsetX < -dismissThreshold -> Color(0xFFEF5350).copy(alpha = 0.15f)
            offsetX > dismissThreshold -> Color(0xFF42A5F5).copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "bg"
    )

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(bgColor)
        .clickable { onClick() }
    ) {
        // 左侧删除背景
        if (offsetX < -20f) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 16.dp).width(60.dp).height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEF5350).copy(alpha = if (offsetX < -dismissThreshold) 0.9f else 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Color.White)
                    if (offsetX < -dismissThreshold) {
                        Spacer(Modifier.width(4.dp))
                        Text("删除", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }
        // 右侧上传背景
        if (offsetX > 20f) {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).width(80.dp).height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF42A5F5).copy(alpha = if (offsetX > dismissThreshold) 0.9f else 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("上传", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp), tint = Color.White)
                }
            }
        }

        // 主卡片内容
        Column(
            Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Note, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.titleCipher.toString(Charsets.UTF_8).take(40).replace("\n", " "),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("ID: ${item.id.take(12)}... · 保险箱条目",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 侧滑提示
                Icon(Icons.Default.Swipe, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
        }
        // 触发操作
        LaunchedEffect(offsetX) {
            if (offsetX < -dismissThreshold * 1.5f) {
                onDelete()
                offsetX = 0f
            } else if (offsetX > dismissThreshold * 1.5f) {
                onUploadToNode()
                offsetX = 0f
            }
        }
    }
}
