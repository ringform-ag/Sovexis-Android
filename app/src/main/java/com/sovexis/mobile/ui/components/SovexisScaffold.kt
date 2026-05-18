package com.sovexis.mobile.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sovexis.mobile.data.local.entity.AccountEntity

/**
 * Sovexis ä¸»å¸ƒå±€ Scaffold
 *
 * ä½¿ç”¨ PermanentNavigationDrawer æ¨¡å¼
 * çŠ¶æ€æ ä¿ç•™ï¼šé€šè¿‡ Modifier.windowInsetsPadding(WindowInsets.statusBars) åœ¨å„é¡µé¢å¤„ç†
 *
 * æ³¨æ„ï¼šç¦æ­¢ä½¿ç”¨ enableEdgeToEdge() æˆ– WindowCompat.setDecorFitsSystemWindows(window, false)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SovexisScaffold(
    accounts: List<AccountEntity>,
    activeDid: String?,
    currentRoute: String?,
    onAccountSelected: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onAddSubAccount: () -> Unit,
    onStewardAccount: () -> Unit,
    topBarTitle: String,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SovexisDrawer(
                accounts = accounts,
                activeDid = activeDid,
                currentRoute = currentRoute,
                onAccountSelected = {
                    onAccountSelected(it)
                    drawerState.close()
                },
                onNavigate = {
                    onNavigate(it)
                    drawerState.close()
                },
                onAddSubAccount = {
                    onAddSubAccount()
                    drawerState.close()
                },
                onStewardAccount = {
                    onStewardAccount()
                    drawerState.close()
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    navigationIcon = {
                        IconButton(onClick = { drawerState.open() }) {
                            Icon(Icons.Default.Menu, contentDescription = "æ‰“å¼€èœå•")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}
