package com.sovexis.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sovexis.domain.identity.SovexisAccount
import kotlinx.coroutines.launch

/**
 * Sovexis 主布局 Scaffold
 *
 * 使用 PermanentNavigationDrawer 模式
 * 状态栏保留：通过 Modifier.windowInsetsPadding(WindowInsets.statusBars) 在各页面处理
 *
 * 注意：禁止使用 enableEdgeToEdge() 或 WindowCompat.setDecorFitsSystemWindows(window, false)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SovexisScaffold(
    accounts: List<SovexisAccount>,
    activeDid: String?,
    currentRoute: String?,
    onAccountSelected: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onAddSubAccount: () -> Unit,
    onStewardAccount: () -> Unit,
    topBarTitle: String,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SovexisDrawer(
                accounts = accounts,
                activeDid = activeDid,
                currentRoute = currentRoute,
                onAccountSelected = {
                    onAccountSelected(it)
                    scope.launch { drawerState.close() }
                },
                onNavigate = {
                    onNavigate(it)
                    scope.launch { drawerState.close() }
                },
                onAddSubAccount = {
                    onAddSubAccount()
                    scope.launch { drawerState.close() }
                },
                onStewardAccount = {
                    onStewardAccount()
                    scope.launch { drawerState.close() }
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "打开菜单")
                        }
                    },
                    actions = actions,
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
