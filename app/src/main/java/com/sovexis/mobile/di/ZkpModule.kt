package com.sovexis.mobile.di

import android.content.Context
import com.sovexis.mobile.domain.vc.CredentialPresentationZkp
import com.sovexis.mobile.domain.zkp.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ZKP 模块 DI 绑定
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-21
 * 实现状态: ✅ 已完成
 * 参考文档: Sovexis · ZKP 模块完整实现指令 (陵谦)
 */
@Module
@InstallIn(SingletonComponent::class)
object ZkpModule {

    @Provides
    @Singleton
    fun provideZkpService(
        @ApplicationContext context: Context
    ): ZkpService {
        return ZkpProverImpl(context)
    }

    @Provides
    @Singleton
    fun provideZkpVerifier(): ZkpVerifier {
        return ZkpVerifierImpl()
    }

    @Provides
    @Singleton
    fun provideZkpCacheManager(): ZkpCacheManager {
        return ZkpCacheManager()
    }

    @Provides
    @Singleton
    fun provideCredentialPresentationZkp(
        zkpService: ZkpService,
        cacheManager: ZkpCacheManager
    ): CredentialPresentationZkp {
        return CredentialPresentationZkp(zkpService, cacheManager)
    }
}
