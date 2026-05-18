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
 * Sovexis æ±‰å ¡ä¾§æ‹‰å¯¼èˆªæŠ½å±‰
 *
 * ç»“æž„ï¼? * - é¡¶éƒ¨ï¼šä¸»è´¦å·ä¿¡æ¯ï¼ˆDID + åˆ«åï¼? * - ä¸­éƒ¨ï¼šå‰¯è´¦å·åˆ—è¡¨ï¼ˆå¯åˆ‡æ¢ï¼? * - ç®¡å®¶å‰¯è´¦å·å…¥å? * - åˆ†å‰²çº? * - å¯¼èˆªèœå•é¡? */
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
            text = "ä¸»æ€ƒèŠ‚ç‚¹",
            style = MaterialTheme.typography.bodySmall,
            color = SovexisDrawerTextSecondary,
            modifier = Modifier.padding(start = 20.dp, bottom = 16.dp)
        )

        HorizontalDivider(color = SovexisDrawerSurface, thickness = 1.dp)

        // ========== ä¸»è´¦å?==========
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

        // ========== å‰¯è´¦å·åˆ—è¡?==========
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

        // æ·»åŠ å‰¯è´¦å?        OutlinedButton(
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
            Text("æ·»åŠ å‰¯è´¦å?, style = MaterialTheme.typography.bodyMedium)
        }

        // ç®¡å®¶å‰¯è´¦å?        TextButton(
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
                "ç®¡å®¶å‰¯è´¦å?,
                style = MaterialTheme.typography.bodyMedium,
                color = SovexisSecondary
            )
        }

        HorizontalDivider(color = SovexisDrawerSurface, thickness = 1.dp)

        // ========== å¯¼èˆªèœå• ==========
        val menuItems = listOf(
            Triple(SovexisRoute.Home.route, "é¦–é¡µ", Icons.Default.Home),
            Triple(SovexisRoute.IdentityManagement.route, "èº«ä»½ç®¡ç†", Icons.Default.Badge),
            Triple(SovexisRoute.Credentials.route, "å‡­è¯", Icons.Default.VerifiedUser),
            Triple(SovexisRoute.SafeBox.route, "ä¿é™©ç®?, Icons.Default.Lock),
            Triple(SovexisRoute.Settings.route, "è®¾ç½®", Icons.Default.Settings),
            Triple(SovexisRoute.About.route, "å…³äºŽ", Icons.Default.Info)
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
            // å¤´åƒå ä½
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
                    text = if (account.role == AccountRole.PRIMARY) "ä¸»è´¦å? else "å‰¯è´¦å?,
                    style = MaterialTheme.typography.bodySmall,
                    color = SovexisDrawerTextSecondary
                )
            }

            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "å½“å‰è´¦å·",
                    tint = SovexisPrimaryLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
