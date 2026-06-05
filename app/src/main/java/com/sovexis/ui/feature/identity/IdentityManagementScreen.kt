@file:OptIn(ExperimentalMaterial3Api::class)

package com.sovexis.ui.feature.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.sovexis.domain.identity.AccountType
import com.sovexis.domain.identity.ChildType
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.ui.components.*
import com.sovexis.ui.navigation.SovexisRoute
import com.sovexis.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Gold = Color(0xFFFFD700)
private val CardHeight = 120.dp
private val SwipeBtnWidth = 44.dp  // 侧滑按钮宽度：参考原"切换活跃"长方形尺寸

@Composable
fun IdentityManagementScreen(
    viewModel: IdentityManagementViewModel = hiltViewModel(),
    navController: NavHostController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var newAlias by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(ChildType.STANDARD) }
    var showDid by remember { mutableStateOf(mapOf<String, Boolean>()) }
    // 复制DID生物认证
    var showBiometricForCopy by remember { mutableStateOf(false) }
    var pendingCopyDid by remember { mutableStateOf("") }

    // 主账号头像
    var showMasterAvatarSheet by remember { mutableStateOf(false) }
    var masterAvatarImage by remember { mutableStateOf<Bitmap?>(null) }
    val masterAccount = uiState.accounts.find { it.accountType == AccountType.MASTER }
    var pendingAvatarBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = decodeAndCropCenter(context, it)
                if (bitmap != null) {
                    pendingAvatarBitmap = bitmap
                    masterAvatarImage = bitmap
                }
            } catch (_: Exception) { }
        }
    }

    val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
    val savedMasterPath = masterAccount?.did?.let { getAvatarImagePath(context, it) }
    val savedMasterBitmap = remember(savedMasterPath) {
        savedMasterPath?.let { path ->
            try { val f = File(path); if (f.exists()) BitmapFactory.decodeFile(path) else null }
            catch (_: Exception) { null }
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // 切活跃后同步全局
    LaunchedEffect(uiState.accounts) {
        AccountStateHolder.update(uiState.accounts)
    }

    // 抽屉仅显示主账号 + 当前活跃
    val drawerAccounts = remember(uiState.accounts) {
        val master = uiState.accounts.filter { it.accountType == AccountType.MASTER }
        val activeSub = uiState.accounts.filter {
            it.accountType != AccountType.MASTER && it.isActive
        }
        master + activeSub
    }

    SovexisScaffold(
        accounts = drawerAccounts,
        activeDid = drawerAccounts.find { it.isActive }?.did,
        currentRoute = "identity_management",
        onAccountSelected = { did -> viewModel.setActive(did) },
        onNavigate = { route ->
            navController?.navigate(route) {
                popUpTo(SovexisRoute.Home.route)
                launchSingleTop = true
            }
        },
        onAddSubAccount = { showAddDialog = true },
        onStewardAccount = { },
        topBarTitle = "身份管理",
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建身份")
            }
        }
    ) { paddingValues ->
        val main = uiState.accounts.filter { it.accountType == AccountType.MASTER }
        val children = uiState.accounts.filter { it.accountType != AccountType.MASTER }

        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(main) { _, account ->
                val didVisible = showDid[account.did] ?: false
                AccountCard(
                    account = account,
                    cardColors = Triple(CardMasterDark, CardMasterGold, CardMasterAccent),
                    didVisible = didVisible,
                    onToggleDid = { showDid = showDid + (account.did to !didVisible) },
                    isMaster = true,
                    onAvatarClick = { showMasterAvatarSheet = true },
                    avatarBitmap = masterAvatarImage ?: savedMasterBitmap,
                    modifier = Modifier,
                    balance = uiState.balances[account.did],
                    onCopyDid = {
                        pendingCopyDid = account.did
                        showBiometricForCopy = true
                    },
                    onSaveAlias = { newAlias -> viewModel.updateAlias(account.did, newAlias) }
                )
            }

            itemsIndexed(children) { _, account ->
                val didVisible = showDid[account.did] ?: false
                val colors = when (account.accountType) {
                    AccountType.STEWARD -> Triple(CardStewardGreen, CardStewardLight, CardStewardGreen)
                    else -> Triple(CardDefaultBg, CardDefaultAccent, CardDefaultBg)
                }
                AccountCard(
                    account = account,
                    cardColors = colors,
                    didVisible = didVisible,
                    onToggleDid = { showDid = showDid + (account.did to !didVisible) },
                    isMaster = false,
                    onAvatarClick = { },
                    avatarBitmap = null,
                    modifier = Modifier,
                    balance = uiState.balances[account.did],
                    onSetActive = if (!account.isActive && !account.isFrozen)
                        {{ viewModel.setActive(account.did) }} else null,
                    onLock = if (!account.isFrozen && !account.isActive)
                        {{ viewModel.setFrozen(account.did, true) }} else null,
                    onUnlock = if (account.isFrozen)
                        {{ viewModel.setFrozen(account.did, false) }} else null,
                    onDelete = {{ viewModel.delete(account.did) }},
                    onSettings = {{ }},
                    onCopyDid = {
                        pendingCopyDid = account.did
                        showBiometricForCopy = true
                    },
                    onSaveAlias = { newAlias -> viewModel.updateAlias(account.did, newAlias) }
                )
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }

    // 复制DID生物认证
    if (showBiometricForCopy) {
        val clipboardManager = LocalClipboardManager.current
        SovexisBiometricPrompt(
            title = "身份验证",
            subtitle = "请验证身份以复制完整DID",
            onSuccess = {
                showBiometricForCopy = false
                clipboardManager.setText(AnnotatedString(pendingCopyDid))
                scope.launch {
                    snackbarHostState.showSnackbar("DID已复制到剪贴板")
                }
            },
            onFailed = {
                showBiometricForCopy = false
            }
        )
    }

    // 头像弹窗
    if (showMasterAvatarSheet) {
        AlertDialog(
            onDismissRequest = { showMasterAvatarSheet = false },
            title = { Text("个性化头像") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showMasterAvatarSheet = false
                        galleryLauncher.launch("image/*")
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("从图库中选择图片"); Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = {
                        showMasterAvatarSheet = false
                        masterAccount?.did?.let { did ->
                            saveAvatarImagePath(context, did, null)
                            masterAvatarImage = null
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Restore, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("恢复为默认头像"); Spacer(Modifier.weight(1f))
                    }
                }
            },
            confirmButton = { TextButton({ showMasterAvatarSheet = false }) { Text("关闭") } }
        )
    }

    if (pendingAvatarBitmap != null) {
        AlertDialog(
            onDismissRequest = { pendingAvatarBitmap = null },
            title = { Text("个性化你的头像") },
            text = {
                val bmp = pendingAvatarBitmap!!
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.size(200.dp).clip(CircleShape).border(2.dp, CardMasterAccent, CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (bmp.width > 0) {
                            androidx.compose.foundation.Image(bmp.asImageBitmap(), "头像",
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("确认后将保存为您的头像", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val s = rememberCoroutineScope()
                    IconButton(onClick = {
                        masterAccount?.did?.let { did ->
                            val file = File(avatarDir, "master_${did.take(12)}.jpg")
                            try {
                                FileOutputStream(file).use { out ->
                                    pendingAvatarBitmap!!.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                }
                                saveAvatarImagePath(context, did, file.absolutePath)
                                masterAvatarImage = pendingAvatarBitmap!!
                            } catch (_: Exception) { }
                        }
                        pendingAvatarBitmap = null
                        s.launch { snackbarHostState.showSnackbar("头像已更新") }
                    }) { Icon(Icons.Default.Check, null, tint = Color(0xFF34A853), modifier = Modifier.size(24.dp)) }
                }
            },
            dismissButton = { TextButton({ pendingAvatarBitmap = null; masterAvatarImage = savedMasterBitmap }) { Text("取消") } }
        )
    }

    if (showAddDialog) {
        val types = ChildType.entries
        val typeNames = listOf("标准副账号", "管家", "服务商")
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加副账号") },
            text = {
                Column {
                    Text("选择副账号类型：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    types.forEachIndexed { i, t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = newType == t, onClick = { newType = t })
                            Spacer(Modifier.width(8.dp)); Text(typeNames[i])
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(newAlias, { newAlias = it },
                        label = { Text("副账号别名（可选）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addSubAccount(newType, newAlias)
                    showAddDialog = false; newAlias = ""; newType = ChildType.STANDARD
                }) { Text("创建") }
            },
            dismissButton = { TextButton({ showAddDialog = false; newAlias = ""; newType = ChildType.STANDARD }) { Text("取消") } }
        )
    }
}

private fun decodeAndCropCenter(context: android.content.Context, uri: Uri): Bitmap? {
    val input = context.contentResolver.openInputStream(uri) ?: return null
    val bitmap = BitmapFactory.decodeStream(input); input.close()
    val size = minOf(bitmap.width, bitmap.height)
    val x = maxOf((bitmap.width - size) / 2, 0)
    val y = maxOf((bitmap.height - size) / 2, 0)
    val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
    val scale = 256f / size
    val matrix = Matrix().apply { postScale(scale, scale) }
    val result = Bitmap.createBitmap(cropped, 0, 0, size, size, matrix, true)
    if (result != cropped) cropped.recycle()
    if (result != bitmap) bitmap.recycle()
    return result
}

// ═════════════════════════════ 卡片组件 (100dp, SwipeToDismiss) ═════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountCard(
    account: SovexisAccount,
    cardColors: Triple<Color, Color, Color>,
    didVisible: Boolean,
    onToggleDid: () -> Unit,
    isMaster: Boolean,
    onAvatarClick: () -> Unit,
    avatarBitmap: Bitmap?,
    modifier: Modifier = Modifier,
    onSetActive: (() -> Unit)? = null,
    onLock: (() -> Unit)? = null,
    onUnlock: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onCopyDid: (() -> Unit)? = null,
    balance: Double? = null,
    onSaveAlias: ((String) -> Unit)? = null,
    onLoadPolicy: ((String) -> Unit)? = null,
    policyConfig: com.sovexis.domain.policy.PolicyConfig? = null,
    onSavePolicy: ((com.sovexis.domain.policy.PolicyConfig) -> Unit)? = null,
    onDismissPolicy: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val (bgColor, accentColor, _) = cardColors

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var isEditingName by remember { mutableStateOf(false) }
    var editingName by remember(account.did) { mutableStateOf(account.alias ?: "") }
    var showSubAvatarMenu by remember { mutableStateOf(false) }
    
    // 副账号头像：关键修复 — 不缓存，每次重组从 prefs 读取
    var subAvatarKey by remember(account.did) { mutableStateOf(getAvatarIconKey(context, account.did)) }
    // 强制在 showSubAvatarMenu 关闭后刷新
    LaunchedEffect(showSubAvatarMenu) {
        if (!showSubAvatarMenu) {
            val latest = getAvatarIconKey(context, account.did)
            if (latest != subAvatarKey) subAvatarKey = latest
        }
    }

    val isSteward = account.accountType == AccountType.STEWARD
    val typeLabel = when (account.accountType) {
        AccountType.MASTER -> "主账号"
        AccountType.STEWARD -> "管家"
        AccountType.SERVICE -> "服务商"
        else -> "副账号"
    }

    // ═══════════════════ 状态层 ═══════════════════
    // 层1: 锁定←→解锁（控制状态，见文档"锁定/解锁"）—— 在主/副卡片的类型标签行右侧显示
    //       仅冻结时高对比度显示 Lock 图标 + "已锁定"，解冻默认不显示
    val isFrozen = account.isFrozen

    // 层2: 活跃←→静默（检测状态，基于节点在线情况）—— 在名称列与齿轮之间显示
    //       活跃才显示 CheckCircle + "活跃"，静默不显示（默认）
    //       [TODO] 需要节点端对齐规则：若该副账号归属的节点在加密网络中在线 → 活跃
    val nodeOnline = false // 暂定默认静默

    // ═══════════════════ 双向侧滑展开 ═══════════════════
    // 右滑 → 左侧红色删除按钮；左滑 → 右侧锁定/解锁按钮
    val hasSwipeActions = !isMaster && (onLock != null || onUnlock != null || onDelete != null)
    val hasDelete = onDelete != null
    val hasLock = onLock != null || onUnlock != null
    val locked = account.isFrozen

    val density = LocalDensity.current
    // 每侧按钮宽度
    val btnWidthPx = with(density) { SwipeBtnWidth.toPx() }

    // 偏移范围：-btnWidthPx（左滑，揭示右侧锁定）→ +btnWidthPx（右滑，揭示左侧删除）
    val minOffset = if (hasLock) -btnWidthPx else 0f
    val maxOffset = if (hasDelete) +btnWidthPx else 0f

    var isSwipedOpen by remember { mutableStateOf(false) }
    val swipeOffset = remember { Animatable(0f) }
    val animationSpec = remember { spring<Float>(dampingRatio = 0.7f, stiffness = 400f) }
    val scope = rememberCoroutineScope()

    if (hasSwipeActions) {
        Box(modifier = Modifier.fillMaxWidth().height(CardHeight)) {
            // ── 底层：左侧删除按钮 ──
            if (hasDelete) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(SwipeBtnWidth).fillMaxHeight()
                        .background(Color(0xFFEF5350).copy(alpha = 0.9f), RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                        .clickable {
                            showDeleteDialog = true
                            scope.launch { swipeOffset.animateTo(0f, animationSpec); isSwipedOpen = false }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Delete, null, Modifier.size(22.dp), tint = Color.White)
                        Text("删除", style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            // ── 底层：右侧锁定/解锁按钮 ──
            if (hasLock) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(SwipeBtnWidth).fillMaxHeight()
                        .background(
                            if (locked) Color(0xFFFF9800).copy(alpha = 0.9f) else Color(0xFF607D8B).copy(alpha = 0.85f),
                            RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                        )
                        .clickable {
                            if (locked) showUnlockDialog = true else showLockDialog = true
                            scope.launch { swipeOffset.animateTo(0f, animationSpec); isSwipedOpen = false }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (locked) Icons.Default.LockOpen else Icons.Default.Lock, null,
                            Modifier.size(22.dp), tint = Color.White
                        )
                        Text(if (locked) "解锁" else "锁定", style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            // ── 上层：卡片本体（双向滑动）──
            Box(
                Modifier
                    .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                    .fillMaxSize()
                    .pointerInput(minOffset, maxOffset) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val threshold = btnWidthPx * 0.35f
                                scope.launch {
                                    val v = swipeOffset.value
                                    when {
                                        v > threshold -> { swipeOffset.animateTo(maxOffset, animationSpec); isSwipedOpen = true }
                                        v < -threshold -> { swipeOffset.animateTo(minOffset, animationSpec); isSwipedOpen = true }
                                        else -> { swipeOffset.animateTo(0f, animationSpec); isSwipedOpen = false }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { swipeOffset.animateTo(0f, animationSpec); isSwipedOpen = false }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                scope.launch {
                                    val newOffset = (swipeOffset.value + dragAmount).coerceIn(minOffset, maxOffset)
                                    swipeOffset.snapTo(newOffset)
                                }
                            }
                        )
                    }
                    .clickable(enabled = isSwipedOpen) {
                        scope.launch { swipeOffset.animateTo(0f, animationSpec); isSwipedOpen = false }
                    }
            ) {
                AccountCardContent(
                    account, bgColor, accentColor, isMaster, avatarBitmap,
                    didVisible, onToggleDid, onAvatarClick, isEditingName, editingName,
                    onEditingNameChange = { editingName = it },
                    onToggleEdit = { isEditingName = it },
                    subAvatarKey, showSubAvatarMenu,
                    onToggleSubAvatarMenu = { showSubAvatarMenu = it },
                    onSubAvatarChanged = { subAvatarKey = it },
                    isFrozen, nodeOnline,
                    typeLabel, onSettings = { showSettings = true },
                    onCopyDid = onCopyDid,
                    balance = balance,
                    onSaveAlias = onSaveAlias,
                    onLoadPolicy = viewModel::loadPolicy,
                    policyConfig = uiState.currentPolicy,
                    onSavePolicy = viewModel::savePolicy,
                    onDismissPolicy = viewModel::dismissPolicy
                )
            }
        }
    } else {
        AccountCardContent(
            account, bgColor, accentColor, isMaster, avatarBitmap,
            didVisible, onToggleDid, onAvatarClick, isEditingName, editingName,
            onEditingNameChange = { editingName = it },
            onToggleEdit = { isEditingName = it },
            subAvatarKey, showSubAvatarMenu,
            onToggleSubAvatarMenu = { showSubAvatarMenu = it },
            onSubAvatarChanged = { subAvatarKey = it },
            isFrozen, nodeOnline,
            typeLabel, onSettings = { showSettings = true },
            onCopyDid = onCopyDid,
            balance = balance,
            onSaveAlias = onSaveAlias,
            onLoadPolicy = viewModel::loadPolicy,
            policyConfig = uiState.currentPolicy,
            onSavePolicy = viewModel::savePolicy,
            onDismissPolicy = viewModel::dismissPolicy
        )
    }

    // ── 弹窗 ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${account.alias ?: account.did.take(12)}」吗？\n此操作不可恢复。") },
            confirmButton = { TextButton({ showDeleteDialog = false; onDelete?.invoke() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ showDeleteDialog = false }) { Text("取消") } }
        )
    }
    if (showLockDialog) {
        AlertDialog(
            onDismissRequest = { showLockDialog = false },
            title = { Text("确认锁定") },
            text = { Text("锁定后 DID 和密钥保持不变，但所有操作将被拦截。\n\n确认锁定「${account.alias ?: account.did.take(12)}」？") },
            confirmButton = { TextButton({ showLockDialog = false; onLock?.invoke() }) { Text("锁定") } },
            dismissButton = { TextButton({ showLockDialog = false }) { Text("取消") } }
        )
    }
    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = { Text("确认解锁") },
            text = { Text("解锁后将恢复「${account.alias ?: account.did.take(12)}」的正常操作。\n确认解锁？") },
            confirmButton = { TextButton({ showUnlockDialog = false; onUnlock?.invoke() }) { Text("解锁") } },
            dismissButton = { TextButton({ showUnlockDialog = false }) { Text("取消") } }
        )
    }
    if (showSettings) {
        // 加载策略
        LaunchedEffect(account.did) {
            onLoadPolicy?.invoke(account.did)
        }
        val config = policyConfig
        var perTxLimit by remember { mutableStateOf(config?.payment?.perTxLimit?.toString() ?: "10000") }
        var dailyLimit by remember { mutableStateOf(config?.payment?.dailyLimit?.toString() ?: "100000") }
        var totalLimit by remember { mutableStateOf(config?.payment?.totalLimit?.toString() ?: "1000000") }
        var allowRead by remember { mutableStateOf(config?.vault?.allowRead ?: true) }
        var allowWrite by remember { mutableStateOf(config?.vault?.allowWrite ?: true) }
        var allowDelete by remember { mutableStateOf(config?.vault?.allowDelete ?: true) }

        AlertDialog(
            onDismissRequest = { showSettings = false; onDismissPolicy?.invoke() },
            title = { Text("权限策略 — ${account.alias ?: account.did.take(12)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("支付限额", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(perTxLimit, { perTxLimit = it }, label = { Text("单笔限额 (AGT)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(dailyLimit, { dailyLimit = it }, label = { Text("当日限额 (AGT)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(totalLimit, { totalLimit = it }, label = { Text("累计限额 (AGT)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))

                    Spacer(Modifier.height(4.dp))
                    Text("保险箱权限", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("允许读取", style = MaterialTheme.typography.bodyMedium)
                        Switch(allowRead, { allowRead = it })
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("允许写入", style = MaterialTheme.typography.bodyMedium)
                        Switch(allowWrite, { allowWrite = it })
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("允许删除", style = MaterialTheme.typography.bodyMedium)
                        Switch(allowDelete, { allowDelete = it })
                    }

                    SettingOption("卡片配色", if (isMaster) "黑金" else if (isSteward) "深绿" else "蓝灰")
                    SettingOption("DID 方法", "did:sovexis")
                    SettingOption("账号类型", typeLabel)
                }
            },
            confirmButton = {
                Button({
                    val current = config ?: com.sovexis.domain.policy.PolicyConfig(boundChildDid = account.did)
                    val newPayment = current.payment.copy(
                        perTxLimit = perTxLimit.toDoubleOrNull() ?: current.payment.perTxLimit,
                        dailyLimit = dailyLimit.toDoubleOrNull() ?: current.payment.dailyLimit,
                        totalLimit = totalLimit.toDoubleOrNull() ?: current.payment.totalLimit
                    )
                    val newVault = current.vault.copy(
                        allowRead = allowRead, allowWrite = allowWrite, allowDelete = allowDelete
                    )
                    onSavePolicy?.invoke(current.copy(payment = newPayment, vault = newVault, updatedAt = System.currentTimeMillis()))
                    showSettings = false
                }) { Text("保存策略") }
            },
            dismissButton = {
                TextButton({ showSettings = false; onDismissPolicy?.invoke() }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AccountCardContent(
    account: SovexisAccount,
    bgColor: Color,
    accentColor: Color,
    isMaster: Boolean,
    avatarBitmap: Bitmap?,
    didVisible: Boolean,
    onToggleDid: () -> Unit,
    onAvatarClick: () -> Unit,
    isEditingName: Boolean,
    editingName: String,
    onEditingNameChange: (String) -> Unit,
    onToggleEdit: (Boolean) -> Unit,
    subAvatarKey: String,
    showSubAvatarMenu: Boolean,
    onToggleSubAvatarMenu: (Boolean) -> Unit,
    onSubAvatarChanged: (String) -> Unit,
    // 控制状态: 锁定/解锁
    isFrozen: Boolean,
    // 检测状态: 活跃/静默（节点在线判断，暂默认静默）
    nodeOnline: Boolean,
    typeLabel: String,
    onSettings: () -> Unit,
    onCopyDid: (() -> Unit)? = null,
    balance: Double? = null,
    onSaveAlias: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().height(CardHeight),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(bgColor, accentColor.copy(alpha = 0.15f), bgColor))))
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 头像
                    val avMod = if (isMaster)
                        Modifier.size(28.dp).clip(CircleShape).clickable { onAvatarClick() }
                    else
                        Modifier.size(28.dp).clip(CircleShape).clickable { onToggleSubAvatarMenu(true) }

                    Box(avMod.background(accentColor.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        if (isMaster && avatarBitmap != null) {
                            androidx.compose.foundation.Image(avatarBitmap.asImageBitmap(), "头像",
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            val iconKey = if (isMaster) getAvatarIconKey(context, account.did) else subAvatarKey
                            val icon = avatarIconMap()[iconKey] ?: Icons.Default.Person
                            Icon(icon, null, Modifier.size(15.dp), tint = accentColor)
                        }
                    }

                    // 副账号头像下拉
                    if (!isMaster) {
                        DropdownMenu(expanded = showSubAvatarMenu, onDismissRequest = { onToggleSubAvatarMenu(false) }) {
                            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                avatarIconMap().forEach { (key, ic) ->
                                    IconButton(onClick = {
                                        saveAvatarIconKey(context, account.did, key)
                                        onSubAvatarChanged(key)
                                        onToggleSubAvatarMenu(false)
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(ic, null, Modifier.size(18.dp), tint = accentColor)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // 名称 + 类型行
                    Column(Modifier.weight(1f)) {
                        if (isEditingName) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(editingName, onEditingNameChange, singleLine = true,
                                    textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp),
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                        focusedBorderColor = accentColor, unfocusedBorderColor = accentColor.copy(alpha = 0.5f)))
                                IconButton({
                                    onSaveAlias?.invoke(editingName)
                                    onToggleEdit(false)
                                }, Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Check, "确定", Modifier.size(12.dp), tint = accentColor)
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(account.alias ?: "未命名",
                                    style = MaterialTheme.typography.titleSmall, color = Color.White,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                IconButton({ onToggleEdit(true) }, Modifier.size(18.dp)) {
                                    Icon(Icons.Default.Edit, "编辑", Modifier.size(10.dp), tint = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }
                        // 类型标签 + 锁定状态（控制层）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(typeLabel, style = MaterialTheme.typography.labelSmall,
                                color = accentColor.copy(alpha = 0.7f), fontSize = 12.sp)
                            // 层1: 锁定状态 — 仅冻结时高对比度显示
                            if (isFrozen) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Lock, null, Modifier.size(13.dp),
                                    tint = Color(0xFFEF5350))
                                Spacer(Modifier.width(2.dp))
                                Text("已锁定", style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFEF5350), fontSize = 12.sp)
                            }
                        }
                    }

                    // 层2: 检测状态（活跃/静默）— 位置不变，仅在活跃时显示
                    if (nodeOnline) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp),
                            tint = Color(0xFF34A853))
                        Spacer(Modifier.width(2.dp))
                        Text("活跃", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF34A853), fontSize = 11.sp)
                    }

                    // 齿轮
                    IconButton(onClick = onSettings, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Settings, null, Modifier.size(14.dp), tint = accentColor.copy(alpha = 0.7f))
                    }
                }

                // 余额行
                balance?.let { bal ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Widgets, null, Modifier.size(16.dp),
                            tint = accentColor.copy(alpha = 0.8f))
                        Spacer(Modifier.width(4.dp))
                        Text("%,.2f AGT".format(bal),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.weight(1f))

                // DID 一行 — Sovexis DID:{后10位} + 展示/隐藏 + 4图标位 + 复制 + 1图标位
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sovexis DID:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    Text(
                        if (didVisible) account.did.takeLast(10) else "••••••••••",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // 展示/隐藏
                    IconButton(onClick = onToggleDid, modifier = Modifier.size(18.dp)) {
                        Icon(if (didVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.5f))
                    }
                    // 4 个图标占位
                    repeat(4) { Box(Modifier.size(18.dp)) }
                    // 复制完整DID（触发生物认证后复制）
                    if (onCopyDid != null) {
                        IconButton(onClick = onCopyDid, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                    // 1 个图标占位（卡片最右侧）
                    Box(Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun avatarIconMap(): Map<String, ImageVector> = mapOf(
    "person" to Icons.Default.Person, "face" to Icons.Default.Face,
    "star" to Icons.Default.Star, "favorite" to Icons.Default.Favorite,
    "shield" to Icons.Default.Shield, "android" to Icons.Default.Android
)

@Composable
private fun SettingOption(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
