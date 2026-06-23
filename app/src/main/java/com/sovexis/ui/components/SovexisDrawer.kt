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
    currentRoute: String?,
    onAccountSelected: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 方案D-B：用 remember 缓存节点连接状态快照，避免每个帧收集 Flow 导致重组
    val nodeState by remember {
        NodeConnectionStateHolder.state
    }.collectAsState()

    // 从全局主题索引获取当前抽屉配色方案（与设置中的主题联动）
    val drawerColors = DrawerPalettes.getOrElse(themePresetIndex) { DrawerPalettes[DefaultPreset] }

    // 节点状态描述文本 — 用 remember 缓存，仅在 nodeState 或 drawerColors 变化时重算
    val (nodeStatusText, nodeStatusColor) = remember(nodeState, drawerColors) {
        when {
            !nodeState.anyConfigured -> "未设置节点" to drawerColors.textSecondary
            nodeState.connectedNodes.size == 1 -> "已连接到${nodeState.connectedNodes[0]}" to Color(0xFF34A853)
            nodeState.connectedNodes.size > 1 -> "已连接${nodeState.connectedNodes.size}个节点" to Color(0xFF34A853)
            else -> "未连接" to drawerColors.textSecondary
        }
    }

    // 方案D-B：菜单项和 beta 标签完全记忆化 — 不随抽屉重组而重新创建
    val menuItems = remember {
        listOf(
            Triple(SovexisRoute.Home.route, "首页", Icons.Default.Home),
            Triple(SovexisRoute.MyNode.route, "节点管理", Icons.Default.Router),
            Triple(SovexisRoute.IdentityManagement.route, "身份管理", Icons.Default.Badge),
            Triple(SovexisRoute.Vault.route, "保险箱", Icons.Default.Lock),
            Triple(SovexisRoute.Payment.route, "支付", Icons.Default.ShoppingCart),
            Triple(SovexisRoute.Credentials.route, "凭证", Icons.Default.VerifiedUser),
            Triple(SovexisRoute.Settings.route, "设置", Icons.Default.Settings),
            Triple(SovexisRoute.About.route, "关于", Icons.Default.Info)
        )
    }
    val betaLabels = remember { setOf(SovexisRoute.Payment.route, SovexisRoute.Credentials.route) }

    // 主账号列表 — 记忆化
    val masterAccounts = remember(accounts) {
        accounts.filter { it.accountType == AccountType.MASTER }
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
        HorizontalDivider(color = drawerColors.surface, thickness = 1.dp)

        masterAccounts.forEach { account ->
            DrawerAccountItem(
                account = account,
                onClick = { onAccountSelected(account.did) },
                drawerColors = drawerColors,
                nodeStatusLabel = nodeStatusText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

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
    onClick: () -> Unit,
    drawerColors: DrawerPalette,
    nodeStatusLabel: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val avatarColor = CardMasterAccent
    var showAvatarMenu by remember { mutableStateOf(false) }

    // 持久化的头像图标
    var selectedIconKey by remember { mutableStateOf(getAvatarIconKey(context, account.did)) }
    val selectedIcon = avatarIconOptions.firstOrNull { it.first == selectedIconKey }?.second
        ?: Icons.Default.Person

    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Transparent, shape = MaterialTheme.shapes.medium
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
                    color = drawerColors.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(nodeStatusLabel, style = MaterialTheme.typography.bodySmall,
                    color = drawerColors.textSecondary)
            }
        }
    }
}
