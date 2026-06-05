package com.sovexis.ui.feature.credentials

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sovexis.domain.vc.VerifiableCredential
import com.sovexis.domain.vc.VerificationResult
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(
    viewModel: CredentialsViewModel = hiltViewModel(),
    navController: NavHostController? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    SovexisScaffold(
        accounts = emptyList(),
        activeDid = uiState.activeAccount?.did,
        currentRoute = "credentials",
        onAccountSelected = { },
        onNavigate = { route ->
            navController?.navigate(route) {
                popUpTo(SovexisRoute.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        },
        onAddSubAccount = { },
        onStewardAccount = { },
        topBarTitle = "凭证"
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Tab 栏
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                Tab(
                    selected = uiState.selectedTab == CredentialTab.MY_CREDENTIALS,
                    onClick = { viewModel.selectTab(CredentialTab.MY_CREDENTIALS) },
                    text = { Text("我的凭证") }
                )
                Tab(
                    selected = uiState.selectedTab == CredentialTab.ISSUE,
                    onClick = { viewModel.selectTab(CredentialTab.ISSUE) },
                    text = { Text("签发") }
                )
                Tab(
                    selected = uiState.selectedTab == CredentialTab.VERIFY,
                    onClick = { viewModel.selectTab(CredentialTab.VERIFY) },
                    text = { Text("验证") }
                )
            }

            // 错误提示
            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(error, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        IconButton(onClick = { viewModel.dismissError() }) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                }
            }

            // 加载提示
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Tab 内容
            when (uiState.selectedTab) {
                CredentialTab.MY_CREDENTIALS -> MyCredentialsTab(uiState, viewModel)
                CredentialTab.ISSUE -> IssueTab(uiState, viewModel)
                CredentialTab.VERIFY -> VerifyTab(uiState, viewModel)
            }
        }
    }
}

// ==================== Tab 1: 我的凭证 ====================

@Composable
private fun MyCredentialsTab(
    state: CredentialsUiState,
    viewModel: CredentialsViewModel
) {
    if (state.selectedCredentialId != null) {
        // 出示详情
        PresentationDetail(state, viewModel)
        return
    }

    val credentials = state.credentials
    if (credentials.isEmpty() && !state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(16.dp))
                Text("暂无凭证", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "切换到'签发'标签创建您的第一个凭证",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(credentials) { vc ->
                CredentialCard(
                    credential = vc,
                    onPresent = { viewModel.presentCredential(vc.credentialId) },
                    onRevoke = { viewModel.revokeCredential(vc.credentialId) },
                    onShowQR = {
                        viewModel.presentCredential(vc.credentialId)
                    }
                )
            }
        }
    }
}

@Composable
private fun CredentialCard(
    credential: VerifiableCredential,
    onPresent: () -> Unit,
    onRevoke: () -> Unit,
    onShowQR: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    credential.type.firstOrNull() ?: "凭证",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "签发方: ${credential.issuer.takeLast(16)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "签发日期: ${credential.issuanceDate.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onPresent) { Text("出示") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onShowQR) { Text("二维码") }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onRevoke,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            }
        }
    }
}

@Composable
private fun PresentationDetail(
    state: CredentialsUiState,
    viewModel: CredentialsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.QrCode,
            null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("凭证出示", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        // 二维码
        state.qrBitmap?.let { bitmap ->
            Card(
                modifier = Modifier.size(256.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "凭证二维码",
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // JSON 预览
        state.presentationJson?.let { json ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Text(
                    text = json,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFD4D4D4),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.dismissPresentation() }) {
            Text("返回列表")
        }
    }
}

// ==================== Tab 2: 签发 ====================

@Composable
private fun IssueTab(
    state: CredentialsUiState,
    viewModel: CredentialsViewModel
) {
    var selectedType by remember { mutableStateOf("") }
    var claimKey by remember { mutableStateOf("") }
    var claimValue by remember { mutableStateOf("") }
    var claims by remember { mutableStateOf(mutableMapOf<String, String>()) }

    val templates = listOf(
        "AgeCredential" to "年龄凭证",
        "EarlySupporterCredential" to "早期支持者",
        "DeveloperCredential" to "开发者",
        "MembershipCredential" to "会员",
        "IdentityCredential" to "身份凭证"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 凭证类型选择
        Text("选择凭证类型", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates) { (type, label) ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type; viewModel.updateIssueType(type) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 自定义类型
        OutlinedTextField(
            value = selectedType,
            onValueChange = { selectedType = it; viewModel.updateIssueType(it) },
            label = { Text("或输入自定义类型") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(20.dp))

        // 声明键值对
        Text("声明 (Claims)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        // 已添加的 claims
        claims.forEach { (k, v) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$k: $v", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    IconButton(onClick = {
                        claims = claims.toMutableMap().also { it.remove(k) }
                        viewModel.updateIssueClaims(claims)
                    }) {
                        Icon(Icons.Default.Close, "删除", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = claimKey,
                onValueChange = { claimKey = it },
                label = { Text("键") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = claimValue,
                onValueChange = { claimValue = it },
                label = { Text("值") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (claimKey.isNotBlank() && claimValue.isNotBlank()) {
                        claims = claims.toMutableMap().also { it[claimKey] = claimValue }
                        viewModel.updateIssueClaims(claims)
                        claimKey = ""
                        claimValue = ""
                    }
                }
            ) {
                Icon(Icons.Default.Add, "添加")
            }
        }

        Spacer(Modifier.height(24.dp))

        // 签发按钮
        Button(
            onClick = { viewModel.issueCredential() },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedType.isNotBlank() && claims.isNotEmpty() && !state.isLoading
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("签发凭证")
        }
    }
}

// ==================== Tab 3: 验证 ====================

@Composable
private fun VerifyTab(
    state: CredentialsUiState,
    viewModel: CredentialsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("验证凭证", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "粘贴凭证 JSON 或扫描二维码来验证凭证有效性",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // 扫描按钮
        val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let { scanned ->
                viewModel.updateVerifyInput(scanned)
                viewModel.verifyCredential()
            }
        }
        Button(
            onClick = {
                val options = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("扫描凭证二维码")
                    setBeepEnabled(true)
                    setOrientationLocked(true)
                }
                scanLauncher.launch(options)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
        ) {
            Icon(Icons.Default.QrCodeScanner, null)
            Spacer(Modifier.width(8.dp))
            Text("扫码验证")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.verifyInput,
            onValueChange = { viewModel.updateVerifyInput(it) },
            label = { Text("凭证 JSON") },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            maxLines = 20
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { viewModel.verifyCredential() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.verifyInput.isNotBlank() && !state.isLoading
        ) {
            Icon(Icons.Default.VerifiedUser, null)
            Spacer(Modifier.width(8.dp))
            Text("验证凭证")
        }

        Spacer(Modifier.height(16.dp))

        // 验证结果
        state.verifyResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.isValid)
                        Color(0xFF1B5E20)
                    else
                        Color(0xFFB71C1C)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.isValid) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            null,
                            tint = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (result.isValid) "凭证有效" else "凭证无效",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    result.errors.forEach { err ->
                        Spacer(Modifier.height(4.dp))
                        Text(err, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    result.warnings.forEach { warn ->
                        Spacer(Modifier.height(4.dp))
                        Text(warn, color = Color(0xFFFFF59D), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
