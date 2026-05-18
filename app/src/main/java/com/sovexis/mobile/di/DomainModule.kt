package com.sovexis.mobile.di

import com.sovexis.mobile.domain.communication.CommunicationService
import com.sovexis.mobile.domain.communication.ServiceRelayAdapter
import com.sovexis.mobile.domain.communication.TransportAdapter
import com.sovexis.mobile.domain.crypto.KeyManager
import com.sovexis.mobile.domain.crypto.KeyManagerImpl
import com.sovexis.mobile.domain.crypto.ProxyReEncryptionService
import com.sovexis.mobile.domain.crypto.ProxyReEncryptionServiceImpl
import com.sovexis.mobile.domain.crypto.ThresholdSignatureService
import com.sovexis.mobile.domain.crypto.ThresholdSignatureServiceImpl
import com.sovexis.mobile.domain.did.DidService
import com.sovexis.mobile.domain.did.DidServiceImpl
import com.sovexis.mobile.domain.policy.PolicyEnforcer
import com.sovexis.mobile.domain.storage.Level1Obfuscator
import com.sovexis.mobile.domain.storage.OramService
import com.sovexis.mobile.domain.storage.StorageObfuscator
import com.sovexis.mobile.domain.vc.CredentialService
import com.sovexis.mobile.domain.vc.CredentialServiceImpl
import com.sovexis.mobile.domain.zkp.ZkpService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context

/**
 * Sovexis é¢†åŸŸå±‚ä¾èµ–æ³¨å…¥æ¨¡å? *
 * ã€å®žçŽ°çŠ¶æ€æ±‡æ€»ã€? * - KeyManager: âœ?å·²å®žçŽ°ï¼ˆåŸºäºŽåºŸæ¡ˆ IdentityManager å¯†é’¥é€»è¾‘ï¼? * - DidService: âœ?å·²å®žçŽ°ï¼ˆåŸºäºŽåºŸæ¡ˆ BIP-32 æ´¾ç”Ÿé€»è¾‘ï¼? * - CredentialService: âœ?å·²å®žçŽ°ï¼ˆåŸºäºŽåºŸæ¡ˆ CredentialManagerï¼? * - PolicyEnforcer: âœ?å·²å®žçŽ°ï¼ˆåŸºäºŽåºŸæ¡ˆç­–ç•¥å±‚ï¼‰
 * - ZkpService: â?æŽ¥å£å®šä¹‰ï¼ˆå¾… Microsoft Crescent åº“æŽ¥å…¥ï¼? * - ProxyReEncryptionService: â?æŽ¥å£å®šä¹‰ï¼ˆå¾… proxy_recrypt ç§»æ¤ï¼? * - ThresholdSignatureService: â?æŽ¥å£å®šä¹‰ï¼ˆå¾… RGSS å®žçŽ°ï¼? * - OramService: â?æŽ¥å£å®šä¹‰ï¼ˆå¾… MegaBlocks/V-ORAM å®žçŽ°ï¼? * - CommunicationService: â?æŽ¥å£å®šä¹‰ï¼ˆå¾…äº”å±‚æž¶æž„å®žçŽ°ï¼? *
 * @author Sovexis æž¶æž„ç»? * @since 3.0.0
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    // ========== å·²å®žçŽ°æœåŠ¡ç»‘å®?==========

    /**
     * å¯†é’¥ç®¡ç†å™¨ç»‘å®?     *
     * ã€å®žçŽ°æ¥æºã€‘KeyManagerImpl.kt
     * ã€å¼•ç”¨åºŸæ¡ˆã€‘IdentityManager.kt ç¬?261-265, 356-374 è¡?     * ã€åŠŸèƒ½ã€‘ECDSA P-256 å¯†é’¥ç”Ÿæˆã€ç­¾åã€éªŒè¯ã€StrongBox æ”¯æŒ
     */
    @Binds
    @Singleton
    abstract fun bindKeyManager(impl: KeyManagerImpl): KeyManager

    /**
     * DID æœåŠ¡ç»‘å®š
     *
     * ã€å®žçŽ°æ¥æºã€‘DidServiceImpl.kt
     * ã€å¼•ç”¨åºŸæ¡ˆã€‘IdentityManager.kt ç¬?290-295, 300-308, 313-318 è¡?     * ã€åŠŸèƒ½ã€‘did:self è‡ªæ³¨å†Œã€BIP-32 æ´¾ç”Ÿã€åˆ«åç®¡ç?     */
    @Binds
    @Singleton
    abstract fun bindDidService(impl: DidServiceImpl): DidService

    /**
     * å‡­è¯æœåŠ¡ç»‘å®š
     *
     * ã€å®žçŽ°æ¥æºã€‘CredentialServiceImpl.kt
     * ã€å¼•ç”¨åºŸæ¡ˆã€‘CredentialManager.kt ç¬?111-154, 159-178, 183-186 è¡?     * ã€åŠŸèƒ½ã€‘W3C VC ç­¾å‘/éªŒè¯ã€VP åˆ›å»º/éªŒè¯ã€é€‰æ‹©æ€§æŠ«éœ?     */
    @Binds
    @Singleton
    abstract fun bindCredentialService(impl: CredentialServiceImpl): CredentialService

    // ========== AIå·²å®žçŽ°æœåŠ¡ï¼ˆæ¡†æž¶ç‰ˆæœ¬ï¼Œéœ€äººå·¥å®¡æ ¸æ ¸å¿ƒç®—æ³•ï¼?=========

    /**
     * ä»£ç†é‡åŠ å¯†æœåŠ¡ç»‘å®?     *
     * ã€å®žçŽ°æ¥æºã€‘ProxyReEncryptionServiceImpl.kt
     * ã€å®žçŽ°çŠ¶æ€ã€‘âš ï¸?AIéƒ¨åˆ†å®žçŽ° - æ¡†æž¶å®Œæˆï¼Œæ ¸å¿ƒç®—æ³•å¾…å®‰å…¨å®¡è®¡
     * ã€åŠŸèƒ½ã€‘ECDH + AES-GCM ä»£ç†é‡åŠ å¯?     */
    @Binds
    @Singleton
    abstract fun bindProxyReEncryptionService(impl: ProxyReEncryptionServiceImpl): ProxyReEncryptionService

    /**
     * é˜ˆå€¼ç­¾åæœåŠ¡ç»‘å®?     *
     * ã€å®žçŽ°æ¥æºã€‘ThresholdSignatureServiceImpl.kt
     * ã€å®žçŽ°çŠ¶æ€ã€‘âš ï¸?AIéƒ¨åˆ†å®žçŽ° - RGSSè¿‡æ¸¡æ–¹æ¡ˆï¼?P-ECDSAå¾…äººå·¥å®žçŽ?     * ã€åŠŸèƒ½ã€‘RGSSç§˜å¯†å…±äº« + é˜ˆå€¼ç­¾åæ¡†æž?     */
    @Binds
    @Singleton
    abstract fun bindThresholdSignatureService(impl: ThresholdSignatureServiceImpl): ThresholdSignatureService

    /**
     * å­˜å‚¨æ··æ·†å™¨ç»‘å®?     *
     * ã€å®žçŽ°æ¥æºã€‘Level1Obfuscator.kt
     * ã€å®žçŽ°çŠ¶æ€ã€‘âœ… AIå¯å®žçŽ?- Level 1 è™šå‡è¯»å–å®žçŽ°å®Œæˆ
     * ã€åŠŸèƒ½ã€‘è®¿é—®æ¨¡å¼æ··æ·†ï¼Œé˜²æ­¢I/Oåˆ†æž
     */
    @Binds
    @Singleton
    abstract fun bindStorageObfuscator(impl: Level1Obfuscator): StorageObfuscator

    // ========== å¾…å®žçŽ°æœåŠ¡ï¼ˆæŽ¥å£å®šä¹‰å·²å­˜åœ¨ï¼‰==========

    // @Binds @Singleton
    // abstract fun bindZkpService(impl: ZkpServiceImpl): ZkpService
    // ã€å¾…å®žçŽ°ã€‘Microsoft Crescent ZKP ç”Ÿç‰©è®¤è¯ - éœ€å¯†ç å­¦ä¸“å®¶å®žçŽ°ç”µè·?
    // @Binds @Singleton
    // abstract fun bindOramService(impl: OramServiceImpl): OramService
    // ã€å¾…å®žçŽ°ã€‘MegaBlocks/V-ORAM å­˜å‚¨æ··æ·† - Level 2 å®Œæ•´ORAM

    // @Binds @Singleton
    // abstract fun bindCommunicationService(impl: CommunicationServiceImpl): CommunicationService
    // ã€å¾…å®žçŽ°ã€‘äº”å±‚é€šä¿¡æž¶æž„ - Noiseåè®®éœ€äººå·¥å®žçŽ°

    companion object {
        /**
         * ç­–ç•¥æ‰§è¡Œå™¨æä¾?         *
         * ã€å®žçŽ°æ¥æºã€‘PolicyEnforcer.kt
         * ã€å¼•ç”¨åºŸæ¡ˆã€‘PolicyEnforcer.kt ç¬?1-326 è¡?         * ã€åŠŸèƒ½ã€‘å‰¯è´¦å·æƒé™ç­–ç•¥ç®¡ç†ã€Markdown å¯¼å…¥å¯¼å‡º
         *
         * ä½¿ç”¨ @Provides è€Œéž @Bindsï¼Œå› ä¸?PolicyEnforcer éœ€è¦?Context å‚æ•°
         */
        @Provides
        @Singleton
        fun providePolicyEnforcer(@ApplicationContext context: Context): PolicyEnforcer {
            return PolicyEnforcer(context)
        }
    }
}
