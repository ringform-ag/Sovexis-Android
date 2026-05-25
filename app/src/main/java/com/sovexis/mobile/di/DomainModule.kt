package com.sovexis.mobile.di

import android.content.Context
import com.sovexis.domain.communication.CommunicationLevel
import com.sovexis.domain.communication.CovertTransport
import com.sovexis.domain.communication.CryptoCommLayer
import com.sovexis.domain.communication.covert.NegotiationFallbackHandler
import com.sovexis.domain.communication.covert.ParameterNegotiator
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.IdentityManagerImpl
import com.sovexis.domain.payment.PaymentManager
import com.sovexis.domain.payment.PaymentManagerImpl
import com.sovexis.domain.storage.StorageConfig
import com.sovexis.mobile.domain.communication.TransportAdapter
import com.sovexis.mobile.domain.crypto.KeyManager
import com.sovexis.mobile.domain.crypto.KeyManagerImpl
import com.sovexis.mobile.domain.crypto.ProxyReEncryptionService
import com.sovexis.mobile.domain.crypto.ProxyReEncryptionServiceImpl
import com.sovexis.mobile.domain.crypto.ThresholdSignatureService
import com.sovexis.tss.BnbTssSignatureService
import com.sovexis.tss.storage.AndroidKeystoreShareStorage
import com.sovexis.tss.storage.ShareStorage
import com.sovexis.mobile.domain.did.DidService
import com.sovexis.mobile.domain.did.DidServiceImpl
import com.sovexis.mobile.domain.policy.PolicyEnforcer
import com.sovexis.mobile.domain.storage.Level1Obfuscator
import com.sovexis.domain.storage.StorageObfuscator
import com.sovexis.domain.recovery.*
import com.sovexis.mobile.domain.vc.CredentialService
import com.sovexis.mobile.domain.vc.CredentialServiceImpl
import com.sovexis.mobile.domain.zkp.ZkpService
import com.sovexis.mobile.domain.zkp.ZkpServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Sovexis 领域层依赖注入模块
 *
 * 【实现状态汇总】
 * - KeyManager: ✅ 已实现
 * - DidService: ✅ 已实现
 * - CredentialService: ✅ 已实现
 * - PolicyEnforcer: ✅ 已实现
 * - ProxyReEncryptionService: ✅ 已完成（陵谦重写版本）
 * - ThresholdSignatureService: ⚠️ 占位实现（待 AAR 集成后切换回 BnbTssSignatureService）
 * - ZkpService: ⏳ 接口定义
 * - OramService: ⏳ 接口定义
 * - CommunicationService: ⏳ 接口定义
 *
 * @author Sovexis 架构组
 * @since 3.0.0
 * @updated 2026-05-23 - 修复 KSP 编译错误：TSS 绑定切换 + Module 拆分
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    // ========== @Binds 抽象绑定 ==========

    @Binds
    @Singleton
    abstract fun bindKeyManager(impl: KeyManagerImpl): KeyManager

    @Binds
    @Singleton
    abstract fun bindDidService(impl: DidServiceImpl): DidService

    @Binds
    @Singleton
    abstract fun bindCredentialService(impl: CredentialServiceImpl): CredentialService

    @Binds
    @Singleton
    abstract fun bindProxyReEncryptionService(impl: ProxyReEncryptionServiceImpl): ProxyReEncryptionService

    /**
     * 阈值签名服务绑定
     *
     * 【实现来源】BnbTssSignatureService (impl-tss-bnblib 模块)
     * 【实现状态】✅ 已完成（tssbridge.aar 已集成）
     * 【功能】2P-ECDSA 阈值签名 (GG20)
     * 【依赖】ShareStorage, GoTssWrapper
     */
    @Binds
    @Singleton
    abstract fun bindThresholdSignatureService(impl: BnbTssSignatureService): ThresholdSignatureService

    /**
     * 密钥份额存储绑定
     *
     * 【实现来源】AndroidKeystoreShareStorage.kt
     * 【实现状态】✅ 已完成（双层加密重写 2026-05-20）
     * 【功能】双层加密存储密钥份额（内层 HKDF + 外层 Keystore StrongBox）
     * 【安全特性】安全擦除、StrongBox 验证、生物认证绑定
     */
    @Binds
    @Singleton
    abstract fun bindShareStorage(impl: AndroidKeystoreShareStorage): ShareStorage

    @Binds
    @Singleton
    abstract fun bindStorageObfuscator(impl: Level1Obfuscator): StorageObfuscator

    // ZkpService 由 ZkpModule 提供，此处不再重复绑定

    @Binds
    @Singleton
    abstract fun bindNodeTrustVerifier(impl: NodeTrustVerifierImpl): NodeTrustVerifier

    @Binds
    @Singleton
    abstract fun bindIdentityManager(impl: IdentityManagerImpl): IdentityManager

    @Binds
    @Singleton
    abstract fun bindPaymentManager(impl: PaymentManagerImpl): PaymentManager

    // ========== @Provides 静态提供（companion object）==========

    companion object {

        @Provides
        @Singleton
        fun provideGuardianManager(
            nodeTrustVerifier: NodeTrustVerifier
        ): GuardianManager {
            return GuardianManager(nodeTrustVerifier)
        }

        @Provides
        @Singleton
        fun provideSocialRecovery(
            zkpService: ZkpService,
            guardianManager: GuardianManager
        ): SocialRecovery {
            // 创建适配器将 mobile.domain.zkp.ZkpService 转换为 recovery.ZkpService
            val recoveryZkpService = object : com.sovexis.domain.recovery.ZkpService {
                override suspend fun prove(
                    request: com.sovexis.domain.recovery.ZkpProveRequest
                ): Result<com.sovexis.domain.recovery.ZkpProof> {
                    // TODO: 实现转换逻辑
                    return Result.failure(NotImplementedError("ZKP 适配器待实现"))
                }

                override suspend fun verify(
                    request: com.sovexis.domain.recovery.ZkpVerifyRequest
                ): com.sovexis.domain.recovery.ZkpVerifyResult {
                    // TODO: 实现转换逻辑
                    return com.sovexis.domain.recovery.ZkpVerifyResult.Invalid("ZKP 适配器待实现")
                }
            }
            return SocialRecovery(recoveryZkpService, guardianManager)
        }

        @Provides
        @Singleton
        fun provideNetworkRecovery(
            nodeTrustVerifier: NodeTrustVerifier
        ): NetworkRecovery {
            return NetworkRecovery(nodeTrustVerifier)
        }

        @Provides
        @Singleton
        fun provideRecoveryCredentialManager(
            @ApplicationContext context: Context
        ): RecoveryCredentialManager {
            return RecoveryCredentialManager(context)
        }

        @Provides
        @Singleton
        fun provideMnemonicRecovery(
            identityManagerProvider: javax.inject.Provider<IdentityManager>
        ): MnemonicRecovery {
            return MnemonicRecovery(identityManagerProvider)
        }

        @Provides
        @Singleton
        fun provideRecoveryManager(
            mnemonicRecovery: MnemonicRecovery,
            socialRecovery: SocialRecovery,
            networkRecovery: NetworkRecovery,
            credentialManager: RecoveryCredentialManager
        ): RecoveryManager {
            return RecoveryManager(
                mnemonicRecovery,
                socialRecovery,
                networkRecovery,
                credentialManager
            )
        }

        @Provides
        @Singleton
        fun provideInnerTransportAdapter(): TransportAdapter {
            throw NotImplementedError("需要提供实际的 TransportAdapter 实现")
        }

        @Provides
        @Singleton
        fun provideStorageConfig(): StorageConfig {
            return StorageConfig()
        }

        @Provides
        @Singleton
        fun provideCryptoCommLayer(
            innerTransport: TransportAdapter,
            identityManager: IdentityManager,
            config: StorageConfig
        ): CryptoCommLayer {
            return CryptoCommLayer(
                innerTransport = innerTransport,
                identityManager = identityManager,
                communicationLevel = config.communicationLevel
            )
        }

        @Provides
        @Singleton
        fun provideCovertTransport(
            innerTransport: TransportAdapter,
            config: StorageConfig
        ): CovertTransport {
            return CovertTransport(
                innerTransport = innerTransport,
                userLevel = config.covertUserLevel,
                negotiator = ParameterNegotiator(),
                fallbackHandler = NegotiationFallbackHandler(config.covertUserLevel)
            )
        }

        @Provides
        @Singleton
        fun providePolicyEnforcer(@ApplicationContext context: Context): PolicyEnforcer {
            return PolicyEnforcer(context)
        }
    }
}
