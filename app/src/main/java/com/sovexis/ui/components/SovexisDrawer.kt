package com.sovexis.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovexis.domain.identity.AccountType
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.ui.navigation.SovexisRoute
import com.sovexis.ui.theme.*

@Composable
fun SovexisDrawer(
    accounts: List<SovexisAccount>,
    activeDid: String?,
    currentRoute: String?,
    onAccountSelected: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onAddSubAccount: () -> Unit,
    onStewardAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nodeState by NodeConnectionStateHolder.state.collectAsState()

    // 从全局主题索引获取当前抽屉配色方案（与设置中的主题联动）
    val drawerColors = DrawerPalettes.getOrElse(themePresetIndex) { DrawerPalettes[DefaultPreset] }

    // 节点状态描述文本
    val nodeStatusText: String
    val nodeStatusColor: Color
    when {
        !nodeState.anyConfigured -> {
            nodeStatusText = "未设置节点"
            nodeStatusColor = Color(0xFF9AA0A6)
        }
        nodeState.connectedNodes.size == 1 -> {
            nodeStatusText = "已连接到${nodeState.connectedNodes[0]}"
            nodeStatusColor = Color(0xFF34A853)
        }
        nodeState.connectedNodes.size > 1 -> {
            nodeStatusText = "已连接${nodeState.connectedNodes.size}个节点"
            nodeStatusColor = Color(0xFF34A853)
        }
        else -> {
            nodeStatusText = "未连接"
            nodeStatusColor = Color(0xFF9AA0A6)
        }
    }

    ModalDrawerSheet(
        modifier = modifier.width(200.dp),
        drawerContainerColor = drawerColors.background
    ) {
        Text("Sovexis", style = MaterialTheme.typography.headlineMedium,
            color = drawerColors.text, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp))
        Text("主权锚点", style = MaterialTheme.typography.bodySmall,
            color = drawerColors.textSecondary,
            modifier = Modifier.padding(start = 20.dp, bottom = 16.dp))
        Divider(color = drawerColors.surface, thickness = 1.dp)

        val sorted = accounts.sortedBy { it.accountType.ordinal }
        sorted.forEach { account ->
            DrawerAccountItem(
                account = account,
                isActive = account.did == activeDid,
                onClick = { onAccountSelected(account.did) },
                drawerColors = drawerColors,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // 节点管理
        TextButton(onClick = { onNavigate(SovexisRoute.MyNode.route) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Icon(Icons.Default.Router, null, Modifier.size(18.dp), tint = drawerColors.active)
            Spacer(Modifier.width(8.dp))
            Text("节点管理", style = MaterialTheme.typography.bodyMedium, color = drawerColors.active)
        }

        // 服务节点状态
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(nodeStatusColor))
            Spacer(Modifier.width(8.dp))
            Text("服务节点状态：$nodeStatusText",
                style = MaterialTheme.typography.bodySmall,
                color = nodeStatusColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth())
        }

        Divider(color = drawerColors.surface, thickness = 1.dp)

        val menuItems = listOf(
            Triple(SovexisRoute.Home.route, "首页", Icons.Default.Home),
            Triple(SovexisRoute.IdentityManagement.route, "身份管理", Icons.Default.Badge),
            Triple(SovexisRoute.Vault.route, "保险箱", Icons.Default.Lock),
            Triple(SovexisRoute.Payment.route, "支付", Icons.Default.ShoppingCart),
            Triple(SovexisRoute.Credentials.route, "凭证", Icons.Default.VerifiedUser),
            Triple(SovexisRoute.Settings.route, "设置", Icons.Default.Settings),
            Triple(SovexisRoute.About.route, "关于", Icons.Default.Info)
        )
        val betaLabels = setOf(SovexisRoute.Payment.route, SovexisRoute.Credentials.route)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(menuItems) { (route, label, icon) ->
                NavigationDrawerItem(
                    icon = { Icon(icon, contentDescription = label) },
                    label = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label)
                            if (route in betaLabels) {
                                Spacer(Modifier.width(6.dp))
                                Text("beta", 
                                    fontSize = 9.sp,
                                    color = Color(0xFFFFA726),
                                    modifier = Modifier
                                        .background(Color(0x1AFFA726), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    },
                    selected = currentRoute == route,
                    onClick = { onNavigate(route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = drawerColors.active.copy(alpha = 0.25f),
                        selectedTextColor = drawerColors.active,
                        selectedIconColor = drawerColors.active,
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = drawerColors.text,
                        unselectedIconColor = drawerColors.textSecondary
                    )
                )
            }
        }
    }
}

// ═════════════════════════════ 可选头像图标列表 ═════════════════════════════

private val avatarIconOptions: List<Pair<String, ImageVector>> = listOf(
    "person" to Icons.Default.Person,
    "face" to Icons.Default.Face,
    "star" to Icons.Default.Star,
    "favorite" to Icons.Default.Favorite,
    "shield" to Icons.Default.Shield,
    "android" to Icons.Default.Android
)

private val avatarIconLabelMap = mapOf(
    "person" to "人物", "face" to "笑脸", "star" to "星标",
    "favorite" to "爱心", "shield" to "盾牌", "android" to "安卓"
)

/** 获取账号的头像图标 key（持久化） */
fun getAvatarIconKey(context: Context, did: String): String {
    return context.getSharedPreferences("sovexis_avatar", Context.MODE_PRIVATE)
        .getString("avatar_$did", "person") ?: "person"
}

/** 保存头像图标选择 */
fun saveAvatarIconKey(context: Context, did: String, key: String) {
    context.getSharedPreferences("sovexis_avatar", Context.MODE_PRIVATE)
        .edit().putString("avatar_$did", key).apply()
}

/** 获取头像图片路径 */
fun getAvatarImagePath(context: Context, did: String): String? {
    return context.getSharedPreferences("sovexis_avatar", Context.MODE_PRIVATE)
        .getString("avatar_img_$did", null)
}

/** 保存头像图片路径 */
fun saveAvatarImagePath(context: Context, did: String, path: String?) {
    val editor = context.getSharedPreferences("sovexis_avatar", Context.MODE_PRIVATE).edit()
    if (path != null) {
        editor.putString("avatar_img_$did", path)
    } else {
        editor.remove("avatar_img_$did")
    }
    editor.apply()
}

@Composable
private fun DrawerAccountItem(
    account: SovexisAccount,
    isActive: Boolean,
    onClick: () -> Unit,
    drawerColors: DrawerPalette,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typeLabel = when (account.accountType) {
        AccountType.MASTER -> "主账号"
        AccountType.CHILD -> "副账号"
        AccountType.STEWARD -> "管家"
        AccountType.SERVICE -> "服务商"
    }
    val avatarColor = when (account.accountType) {
        AccountType.MASTER -> CardMasterAccent
        AccountType.STEWARD -> CardStewardLight
        else -> drawerColors.active
    }
    var showAvatarMenu by remember { mutableStateOf(false) }

    // 持久化的头像图标
    var selectedIconKey by remember { mutableStateOf(getAvatarIconKey(context, account.did)) }
    val selectedIcon = avatarIconOptions.firstOrNull { it.first == selectedIconKey }?.second
        ?: Icons.Default.Person

    val bgColor = if (isActive) drawerColors.active.copy(alpha = 0.15f) else Color.Transparent
    val statusLabel = when (account.accountType) {
        AccountType.MASTER -> "信用: --"
        else -> if (isActive) "活跃" else typeLabel
    }
    val statusDotColor = if (isActive) Color(0xFF34A853) else drawerColors.textSecondary
    val showCreditScore = account.accountType == AccountType.MASTER

    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = bgColor, shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(Modifier.size(40.dp).clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.3f))
                    .clickable { showAvatarMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(selectedIcon, null, Modifier.size(22.dp), tint = avatarColor)
                }
                DropdownMenu(
                    expanded = showAvatarMenu,
                    onDismissRequest = { showAvatarMenu = false }
                ) {
                    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        avatarIconOptions.forEach { (key, ic) ->
                            IconButton(
                                onClick = {
                                    selectedIconKey = key
                                    saveAvatarIconKey(context, account.did, key)
                                    showAvatarMenu = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(ic, avatarIconLabelMap[key] ?: key, Modifier.size(20.dp),
                                    tint = if (key == selectedIconKey) avatarColor else drawerColors.textSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account.alias ?: account.did.take(12) + "...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) drawerColors.active else drawerColors.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(typeLabel, style = MaterialTheme.typography.bodySmall,
                    color = drawerColors.textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusDotColor))
                Spacer(Modifier.width(4.dp))
                Text(statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusDotColor)
            }
        }
    }
}
