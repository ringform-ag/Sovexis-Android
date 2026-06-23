package com.sovexis.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sovexis.ui.feature.about.AboutScreen
import com.sovexis.ui.feature.notifications.NotificationScreen
import com.sovexis.ui.feature.home.HomeScreen
import com.sovexis.ui.feature.home.HomeViewModel
import com.sovexis.ui.feature.identity.IdentityManagementScreen
import com.sovexis.ui.feature.identity.IdentityManagementViewModel
import com.sovexis.ui.feature.credentials.CredentialsScreen
import com.sovexis.ui.feature.credentials.CredentialsViewModel
import com.sovexis.ui.feature.safebox.SafeBoxScreen
import com.sovexis.ui.feature.safebox.SafeBoxViewModel
import com.sovexis.ui.feature.settings.SettingsScreen
import com.sovexis.ui.feature.settings.SettingsViewModel
import com.sovexis.ui.feature.onboarding.AddSubAccountScreen
import com.sovexis.ui.feature.onboarding.CreateIdentityScreen
import com.sovexis.ui.feature.onboarding.CreateIdentityViewModel
import com.sovexis.ui.feature.payment.PaymentScreen
import com.sovexis.ui.feature.payment.PaymentViewModel
import com.sovexis.ui.feature.vault.VaultScreen
import com.sovexis.ui.feature.vault.VaultViewModel
import com.sovexis.ui.feature.mynode.MyNodeScreen
import com.sovexis.ui.feature.mynode.MyNodeViewModel
import com.sovexis.ui.feature.welcome.WelcomeScreen
import com.sovexis.ui.feature.serviceprovider.ServiceProviderScreen
import com.sovexis.ui.feature.serviceprovider.ServiceProviderViewModel
import com.sovexis.ui.feature.contracts.StorageContractsScreen

@Composable
fun SovexisNavHost(
    startRoute: String = SovexisRoute.Home.route,
    @Suppress("UNUSED_PARAMETER")
    navController: NavHostController = androidx.navigation.compose.rememberNavController(),
    @Suppress("UNUSED_PARAMETER")
    drawerState: androidx.compose.material3.DrawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        // 欢迎页（无身份时显示）
        composable(SovexisRoute.Welcome.route) {
            WelcomeScreen(
                onCreateIdentity = {
                    navController.navigate(SovexisRoute.CreateIdentity.route) {
                        popUpTo(SovexisRoute.Welcome.route) { inclusive = true }
                    }
                },
                onRecoverIdentity = {
                    navController.navigate(SovexisRoute.IdentityManagement.route) {
                        popUpTo(SovexisRoute.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        // 创建身份
        composable(SovexisRoute.CreateIdentity.route) {
            val viewModel: CreateIdentityViewModel = hiltViewModel()
            CreateIdentityScreen(
                viewModel = viewModel,
                onIdentityCreated = {
                    navController.navigate(SovexisRoute.Home.route) {
                        popUpTo(SovexisRoute.CreateIdentity.route) { inclusive = true }
                    }
                }
            )
        }

        // 首页
        composable(SovexisRoute.Home.route) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(viewModel = viewModel, navController = navController)
        }

        // 身份管理
        composable(SovexisRoute.IdentityManagement.route) {
            val viewModel: IdentityManagementViewModel = hiltViewModel()
            IdentityManagementScreen(viewModel = viewModel, navController = navController)
        }

        // 副账号创建（独立 Screen）
        composable(SovexisRoute.AddSubAccount.route) {
            val viewModel: IdentityManagementViewModel = hiltViewModel()
            AddSubAccountScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 支付
        composable(SovexisRoute.Payment.route) {
            val viewModel: PaymentViewModel = hiltViewModel()
            PaymentScreen(
                viewModel = viewModel,
                navController = navController,
                onPaymentComplete = {
                    navController.navigate(SovexisRoute.Home.route) {
                        popUpTo(SovexisRoute.Payment.route) { inclusive = true }
                    }
                },
                onPaymentFailed = {
                    navController.popBackStack()
                }
            )
        }

        // 凭证管理
        composable(SovexisRoute.Credentials.route) {
            val viewModel: CredentialsViewModel = hiltViewModel()
            CredentialsScreen(viewModel = viewModel, navController = navController)
        }

        // 保险箱（VaultScreen）
        composable(SovexisRoute.Vault.route) {
            val vaultViewModel: VaultViewModel = hiltViewModel()
            VaultScreen(viewModel = vaultViewModel, navController = navController)
        }

        // 保险箱（旧兼容路由）
        composable(SovexisRoute.SafeBox.route) {
            val viewModel: SafeBoxViewModel = hiltViewModel()
            SafeBoxScreen(viewModel = viewModel)
        }

        // 设置
        composable(SovexisRoute.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(viewModel = viewModel, navController = navController)
        }

        // 关于
        composable(SovexisRoute.About.route) {
            AboutScreen(navController = navController)
        }

        composable(SovexisRoute.Notifications.route) {
            NotificationScreen(navController = navController)
        }

        // 我的节点
        composable(SovexisRoute.MyNode.route) {
            val viewModel: MyNodeViewModel = hiltViewModel()
            MyNodeScreen(viewModel = viewModel, navController = navController)
        }

        // 服务商管理（默认隐藏，开发版可手动导航）
        composable(SovexisRoute.ServiceProvider.route) {
            val viewModel: ServiceProviderViewModel = hiltViewModel()
            ServiceProviderScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        // 存储合约管理
        composable(SovexisRoute.StorageContracts.route) {
            StorageContractsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
