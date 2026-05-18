package com.sovexis.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sovexis.mobile.ui.feature.home.HomeScreen
import com.sovexis.mobile.ui.feature.home.HomeViewModel
import com.sovexis.mobile.ui.feature.identity.IdentityManagementScreen
import com.sovexis.mobile.ui.feature.identity.IdentityManagementViewModel
import com.sovexis.mobile.ui.feature.credentials.CredentialsScreen
import com.sovexis.mobile.ui.feature.credentials.CredentialsViewModel
import com.sovexis.mobile.ui.feature.safebox.SafeBoxScreen
import com.sovexis.mobile.ui.feature.safebox.SafeBoxViewModel
import com.sovexis.mobile.ui.feature.settings.SettingsScreen
import com.sovexis.mobile.ui.feature.settings.SettingsViewModel
import com.sovexis.mobile.ui.feature.splash.SplashScreen
import com.sovexis.mobile.ui.feature.splash.SplashViewModel
import com.sovexis.mobile.ui.feature.onboarding.CreateIdentityScreen
import com.sovexis.mobile.ui.feature.onboarding.CreateIdentityViewModel

@Composable
fun SovexisNavHost(
    navController: NavHostController = androidx.navigation.compose.rememberNavController(),
    drawerState: androidx.compose.material3.DrawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
) {
    NavHost(
        navController = navController,
        startDestination = SovexisRoute.Splash.route
    ) {
        // 启动页
        composable(SovexisRoute.Splash.route) {
            val viewModel: SplashViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            SplashScreen(
                uiState = uiState,
                onNavigateToHome = {
                    navController.navigate(SovexisRoute.Home.route) {
                        popUpTo(SovexisRoute.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(SovexisRoute.CreateIdentity.route) {
                        popUpTo(SovexisRoute.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // åˆ›å»ºèº«ä»½
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
            HomeScreen(viewModel = viewModel)
        }

        // 身份管理
        composable(SovexisRoute.IdentityManagement.route) {
            val viewModel: IdentityManagementViewModel = hiltViewModel()
            IdentityManagementScreen(viewModel = viewModel)
        }

        // 凭证管理
        composable(SovexisRoute.Credentials.route) {
            val viewModel: CredentialsViewModel = hiltViewModel()
            CredentialsScreen(viewModel = viewModel)
        }

        // ä¿é™©ç®?        composable(SovexisRoute.SafeBox.route) {
            val viewModel: SafeBoxViewModel = hiltViewModel()
            SafeBoxScreen(viewModel = viewModel)
        }

        // 设置
        composable(SovexisRoute.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(viewModel = viewModel)
        }

        // 关于
        composable(SovexisRoute.About.route) {
            // TODO: å®žçŽ°å…³äºŽé¡µé¢
        }
    }
}
