package com.sovexis.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sovexis.mobile.R
import com.sovexis.mobile.data.local.entity.AccountEntity
import com.sovexis.mobile.data.local.entity.AccountRole
import com.sovexis.mobile.ui.navigation.SovexisRoute
import com.sovexis.mobile.ui.theme.*

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
    accounts: List<AccountEntity>,
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

        // ========== 主账号 ==========
        val primaryAccount = accounts.find { it.role == AccountRole.PRIMARY }
        primaryAccount?.let { account ->
            AccountItem(
                account = account,
                isActive = account.did == activeDid,
                isSelected = false,
                onClick = { onAccountSelected(account.did) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // ========== 副账号列表 ==========
        val subAccounts = accounts.filter { it.role == AccountRole.SUB }
        if (subAccounts.isNotEmpty()) {
            subAccounts.forEach { account ->
                AccountItem(
                    account = account,
                    isActive = account.did == activeDid,
                    isSelected = false,
                    onClick = { onAccountSelected(account.did) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
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

        // 管家副账号
        TextButton(
            onClick = onStewardAccount,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                Icons.Default.AdminPanelSettings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = SovexisSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "管家副账号",
                style = MaterialTheme.typography.bodyMedium,
                color = SovexisSecondary
            )
        }

        Divider(color = SovexisDrawerSurface, thickness = 1.dp)

        // ========== 导航菜单 ==========
        val menuItems = listOf(
            Triple(SovexisRoute.Home.route, "首页", Icons.Default.Home),
            Triple(SovexisRoute.IdentityManagement.route, "身份管理", Icons.Default.Badge),
            Triple(SovexisRoute.Credentials.route, "凭证", Icons.Default.VerifiedUser),
            Triple(SovexisRoute.SafeBox.route, "保险箱", Icons.Default.Lock),
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
                        selectedContainerColor = SovexisDrawerActive.copy(alpha = 0.2f),
                        selectedTextColor = SovexisPrimaryLight,
                        unselectedTextColor = SovexisDrawerText,
                        unselectedIconColor = SovexisDrawerTextSecondary
                    )
                )
            }
        }
    }
}

@Composable
private fun AccountItem(
    account: AccountEntity,
    isActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    Text(
                        text = account.alias.take(1).uppercase(),
                        color = if (isActive) Color.White else SovexisDrawerText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.alias,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) SovexisPrimaryLight else SovexisDrawerText,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = if (account.role == AccountRole.PRIMARY) "主账号" else "副账号",
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
