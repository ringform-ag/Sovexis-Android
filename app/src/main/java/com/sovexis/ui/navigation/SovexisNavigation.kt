package com.sovexis.ui.navigation

/**
 * Sovexis 导航路由定义
 */
sealed class SovexisRoute(val route: String) {

    // 启动与认证
    data object Splash : SovexisRoute("splash")
    data object Welcome : SovexisRoute("welcome")
    data object Onboarding : SovexisRoute("onboarding")
    data object CreateIdentity : SovexisRoute("create_identity")

    // 主页面
    data object Home : SovexisRoute("home")
    data object IdentityManagement : SovexisRoute("identity_management")
    data object Credentials : SovexisRoute("credentials")
    data object Payment : SovexisRoute("payment")
    data object Vault : SovexisRoute("vault")
    data object MyNode : SovexisRoute("my_node")
    data object CredentialDetail : SovexisRoute("credential_detail/{credentialId}") {
        fun createRoute(credentialId: String) = "credential_detail/$credentialId"
    }
    data object SafeBox : SovexisRoute("safebox")
    data object Settings : SovexisRoute("settings")
    data object About : SovexisRoute("about")

    // DID 相关
    data object DidDetail : SovexisRoute("did_detail/{did}") {
        fun createRoute(did: String) = "did_detail/$did"
    }
    data object AddSubAccount : SovexisRoute("add_sub_account")

    // VC 相关
    data object PresentCredential : SovexisRoute("present_credential/{credentialId}") {
        fun createRoute(credentialId: String) = "present_credential/$credentialId"
    }

    // 保险箱相关
    data object SafeBoxItemDetail : SovexisRoute("safebox_item/{itemId}") {
        fun createRoute(itemId: String) = "safebox_item/$itemId"
    }
    data object ShareSafeBoxItem : SovexisRoute("share_safebox/{itemId}") {
        fun createRoute(itemId: String) = "share_safebox/$itemId"
    }
}
