package com.sovexis.di

import android.content.Context
import com.sovexis.domain.communication.CryptoCommLayer
import com.sovexis.domain.communication.CommunicationLevel
import com.sovexis.domain.communication.RelayConfig
import com.sovexis.domain.communication.TransportAdapter
import com.sovexis.domain.communication.ServiceRelayAdapter
import com.sovexis.domain.crypto.KeyManager
import com.sovexis.domain.crypto.KeyManagerImpl
import com.sovexis.domain.crypto.ThresholdSignatureService
import com.sovexis.domain.crypto.ThresholdSignatureServiceImpl
import com.sovexis.domain.did.DidService
import com.sovexis.domain.did.DidServiceImpl
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.IdentityManagerImpl
import com.sovexis.domain.payment.PaymentManager
import com.sovexis.domain.payment.PaymentManagerImpl
import com.sovexis.domain.policy.PolicyEnforcer
import com.sovexis.domain.recovery.GuardianManager
import com.sovexis.domain.recovery.MnemonicRecovery
import com.sovexis.domain.recovery.NetworkRecovery
import com.sovexis.domain.recovery.NodeTrustVerifier
import com.sovexis.domain.recovery.NodeTrustVerifierImpl
import com.sovexis.domain.recovery.RecoveryCredentialManager
import com.sovexis.domain.recovery.RecoveryManager
import com.sovexis.domain.recovery.SocialRecovery
import com.sovexis.data.local.AppDatabase
import com.sovexis.data.local.dao.SafeBoxDao
import com.sovexis.domain.storage.Level1Obfuscator
import com.sovexis.domain.storage.StorageObfuscator
import com.sovexis.domain.storage.VaultDao
import com.sovexis.domain.vc.CredentialService
import com.sovexis.domain.vc.CredentialServiceImpl
import com.sovexis.domain.zkp.ZkpService
import com.sovexis.domain.communication.NodeMessageRouter
import com.sovexis.data.communication.NodeMessageRouterImpl
import com.sovexis.domain.node.NodeServiceManager
import com.sovexis.data.node.NodeServiceManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    // ===== 基础设施 =====
    // Context 由 Hilt @ApplicationContext 提供，无需额外绑定

    // ===== 密码学基础 =====

    @Provides
    @Singleton
    fun provideKeyManager(@ApplicationContext context: Context): KeyManager {
        return KeyManagerImpl(context)
    }

    // ===== DID 与身份 =====

    @Provides
    @Singleton
    fun provideDidService(
        keyManager: KeyManager,
        @ApplicationContext context: Context
    ): DidService {
        return DidServiceImpl(keyManager, context)
    }

    @Provides
    @Singleton
    fun provideIdentityManager(
        didService: DidService,
        recoveryManager: RecoveryManager,
        policyEnforcer: PolicyEnforcer,
        @ApplicationContext context: Context
    ): IdentityManager {
        return IdentityManagerImpl(didService, recoveryManager, policyEnforcer, context)
    }

    // PolicyEnforcer 通过 @Inject constructor 自动提供，DomainModule 无需重复绑定

    // ===== 支付 =====

    @Provides
    @Singleton
    fun providePaymentManager(
        @ApplicationContext context: Context,
        keyManager: KeyManager
    ): PaymentManager {
        return PaymentManagerImpl(context, keyManager)
    }

    // ===== 凭证 =====

    @Provides
    @Singleton
    fun provideCredentialService(
        @ApplicationContext context: Context,
        didService: DidService
    ): CredentialService {
        return CredentialServiceImpl(context, didService)
    }

    // ZkpService 和 ZkpCacheManager 由 ZkpModule 统一提供，避免重复绑定

    @Provides
    @Singleton
    fun provideThresholdSignatureService(): ThresholdSignatureService {
        return ThresholdSignatureServiceImpl()
    }

    // ===== 恢复机制 =====

    @Provides
    @Singleton
    fun provideNodeTrustVerifier(): NodeTrustVerifier {
        return NodeTrustVerifierImpl()
    }

    @Provides
    @Singleton
    fun provideMnemonicRecovery(
        identityManagerProvider: Provider<IdentityManager>
    ): MnemonicRecovery {
        return MnemonicRecovery(identityManagerProvider)
    }

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
        return SocialRecovery(zkpService, guardianManager)
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
    fun provideRecoveryManager(
        mnemonicRecovery: MnemonicRecovery,
        socialRecovery: SocialRecovery,
        networkRecovery: NetworkRecovery,
        credentialManager: RecoveryCredentialManager
    ): RecoveryManager {
        return RecoveryManager(
            mnemonicRecovery = mnemonicRecovery,
            socialRecovery = socialRecovery,
            networkRecovery = networkRecovery,
            credentialManager = credentialManager
        )
    }

    // ===== 存储混淆 =====

    @Provides
    @Singleton
    fun provideStorageObfuscator(
        vaultDao: VaultDao
    ): StorageObfuscator {
        return Level1Obfuscator(vaultDao)
    }

    // ===== 通信架构 =====

    @Provides
    @Singleton
    fun provideRelayConfig(): RelayConfig {
        return RelayConfig(
            host = "localhost",
            port = 443,
            webSocketPath = "/ws",
            httpEndpoint = "/api",
            authToken = ""
        )
    }

    @Provides
    @Singleton
    fun provideTransportAdapter(config: RelayConfig): TransportAdapter {
        return ServiceRelayAdapter(config)
    }

    @Provides
    @Singleton
    fun provideCryptoCommLayer(
        transportAdapter: TransportAdapter,
        identityManager: IdentityManager
    ): CryptoCommLayer {
        return CryptoCommLayer(
            innerTransport = transportAdapter,
            identityManager = identityManager,
            communicationLevel = CommunicationLevel.STANDARD
        )
    }

    // ===== 数据库 =====
    // AppDatabase 由 AppModule 统一提供，DomainModule 仅提供 DAO

    @Provides
    @Singleton
    fun provideSafeBoxDao(appDatabase: AppDatabase): SafeBoxDao {
        return appDatabase.safeBoxDao()
    }

    @Provides
    @Singleton
    fun provideVaultDao(appDatabase: AppDatabase): VaultDao {
        return appDatabase.vaultDao()
    }

    // ===== Node 集成 =====

    @Provides
    @Singleton
    fun provideNodeMessageRouter(
        cryptoCommLayer: CryptoCommLayer
    ): NodeMessageRouter {
        return NodeMessageRouterImpl(cryptoCommLayer)
    }

    @Provides
    @Singleton
    fun provideNodeServiceManager(
        messageRouter: NodeMessageRouter
    ): NodeServiceManager {
        return NodeServiceManagerImpl(messageRouter)
    }
}
