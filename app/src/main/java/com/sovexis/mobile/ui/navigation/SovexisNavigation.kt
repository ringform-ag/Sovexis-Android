package com.sovexis.mobile.ui.navigation

/**
 * Sovexis å¯¼èˆªè·¯ç”±å®šä¹‰
 */
sealed class SovexisRoute(val route: String) {

    // å¯åŠ¨ä¸Žè®¤è¯?    data object Splash : SovexisRoute("splash")
    data object Onboarding : SovexisRoute("onboarding")
    data object CreateIdentity : SovexisRoute("create_identity")

    // ä¸»é¡µé?    data object Home : SovexisRoute("home")
    data object IdentityManagement : SovexisRoute("identity_management")
    data object Credentials : SovexisRoute("credentials")
    data object CredentialDetail : SovexisRoute("credential_detail/{credentialId}") {
        fun createRoute(credentialId: String) = "credential_detail/$credentialId"
    }
    data object SafeBox : SovexisRoute("safebox")
    data object Settings : SovexisRoute("settings")
    data object About : SovexisRoute("about")

    // DID ç›¸å…³�?    data object DidDetail : SovexisRoute("did_detail/{did}") {
        fun createRoute(did: String) = "did_detail/$did"
    }
    data object AddSubAccount : SovexisRoute("add_sub_account")

    // VC ç›¸å…³�?    data object PresentCredential : SovexisRoute("present_credential/{credentialId}") {
        fun createRoute(credentialId: String) = "present_credential/$credentialId"
    }

    // ä¿é™©ç®±ç›¸å…³�?    data object SafeBoxItemDetail : SovexisRoute("safebox_item/{itemId}") {
        fun createRoute(itemId: String) = "safebox_item/$itemId"
    }
    data object ShareSafeBoxItem : SovexisRoute("share_safebox/{itemId}") {
        fun createRoute(itemId: String) = "share_safebox/$itemId"
    }
}
