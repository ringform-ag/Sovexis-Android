package com.sovexis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sovexis.domain.identity.AccountType
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.platform.R
import com.sovexis.ui.navigation.SovexisRoute
import com.sovexis.ui.theme.*

/**
 * Sovexis 汉堡侧拉导航抽屉
 *
 * 结构：
 * - 顶部：主账号信息（DID + 别名）
 * - 中部：副账号列表（可切换）
 * - 管家副账号入口
 * - 分割线
 * - 导航菜单项
 */
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
    ModalDrawerSheet(
        modifier = modifier.width(300.dp),
        drawerContainerColor = SovexisDrawerBackground
    ) {
        // ========== 顶部标题 ==========
        Text(
            text = "Sovexis",
            style = MaterialTheme.typography.headlineMedium,
            color = SovexisDrawerText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp)
        )
        Text(
            text = "主节点",
            style = MaterialTheme.typography.bodySmall,
            color = SovexisDrawerTextSecondary,
            modifier = Modifier.padding(start = 20.dp, bottom = 16.dp)
        )

        Divider(color = SovexisDrawerSurface, thickness = 1.dp)

        // ========== 账号列表（按 accountType 排序，MASTER 始终在最前） ==========
        val sorted = accounts.sortedBy { it.accountType.ordinal }
        sorted.forEach { account ->
            DrawerAccountItem(
                account = account,
                isActive = account.did == activeDid,
                onClick = { onAccountSelected(account.did) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // 添加副账号
        OutlinedButton(
            onClick = onAddSubAccount,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = SovexisDrawerTextSecondary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加副账号", style = MaterialTheme.typography.bodyMedium)
        }

        // 我的节点
        TextButton(
            onClick = { onNavigate(SovexisRoute.MyNode.route) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                Icons.Default.Router,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = SovexisSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "我的节点",
                style = MaterialTheme.typography.bodyMedium,
                color = SovexisSecondary
            )
        }

        Divider(color = SovexisDrawerSurface, thickness = 1.dp)

        // ========== 导航菜单 ==========
        val menuItems = listOf(
            Triple(SovexisRoute.Home.route, "首页", Icons.Default.Home),
            Triple(SovexisRoute.Payment.route, "支付", Icons.Default.ShoppingCart),
            Triple(SovexisRoute.Vault.route, "保险箱", Icons.Default.Lock),
            Triple(SovexisRoute.IdentityManagement.route, "身份管理", Icons.Default.Badge),
            Triple(SovexisRoute.Credentials.route, "凭证", Icons.Default.VerifiedUser),
            Triple(SovexisRoute.Settings.route, "设置", Icons.Default.Settings),
            Triple(SovexisRoute.About.route, "关于", Icons.Default.Info)
        )

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(menuItems) { (route, label, icon) ->
                NavigationDrawerItem(
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label) },
                    selected = currentRoute == route,
                    onClick = { onNavigate(route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = SovexisDrawerActive.copy(alpha = 0.25f),
                        selectedTextColor = SovexisPrimaryLight,
                        selectedIconColor = SovexisPrimaryLight,
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = SovexisDrawerText,
                        unselectedIconColor = SovexisDrawerTextSecondary
                    )
                )
            }
        }
    }
}

@Composable
private fun DrawerAccountItem(
    account: SovexisAccount,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (account.accountType) {
        AccountType.MASTER -> Icons.Filled.Person
        AccountType.CHILD -> Icons.Outlined.PersonOutline
        AccountType.STEWARD -> Icons.Filled.Computer
        AccountType.SERVICE -> Icons.Filled.Build
    }

    val typeLabel = when (account.accountType) {
        AccountType.MASTER -> "主账号"
        AccountType.CHILD -> "副账号"
        AccountType.STEWARD -> "管家"
        AccountType.SERVICE -> "服务商"
    }

    val color = when {
        isActive -> SovexisPrimaryLight
        account.accountType == AccountType.MASTER -> Color(0xFFFFD700)
        else -> SovexisDrawerText
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isActive) SovexisDrawerActive.copy(alpha = 0.15f) else Color.Transparent,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像占位
            Surface(
                modifier = Modifier.size(40.dp),
                color = if (isActive) SovexisPrimary else SovexisDrawerSurface,
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = typeLabel,
                        modifier = Modifier.size(20.dp),
                        tint = if (isActive) Color.White else SovexisDrawerText
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.alias ?: account.did.take(12) + "...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) SovexisPrimaryLight else SovexisDrawerText,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = SovexisDrawerTextSecondary
                )
            }

            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "当前账号",
                    tint = SovexisPrimaryLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
