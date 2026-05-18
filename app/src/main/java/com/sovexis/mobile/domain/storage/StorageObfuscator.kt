package com.sovexis.mobile.domain.storage

/**
 * [AI-GENERATED]
 * 生成时间: 2026-05-09
 * 实现状�? �?AI可实�? * 审核状�? 待审�? *
 * 存储混淆器接�? * 定义混淆读写操作的标准接�? */
interface StorageObfuscator {

    /**
     * 混淆读取
     * 执行真实读取的同时附带虚假读取，防止访问模式泄露
     *
     * @param T 返回数据类型
     * @param realOperation 真实读取操作
     * @param dummyOperations 虚假读取操作列表
     * @return 真实读取结果
     */
    suspend fun <T> obfuscatedRead(
        realOperation: suspend () -> T,
        dummyOperations: List<suspend () -> Unit> = emptyList()
    ): T

    /**
     * 混淆写入
     * 执行真实写入的同时附带虚假读取，使写入模式不可区�?     *
     * @param realOperation 真实写入操作
     * @param dummyOperations 虚假读取操作列表
     */
    suspend fun obfuscatedWrite(
        realOperation: suspend () -> Unit,
        dummyOperations: List<suspend () -> Unit> = emptyList()
    )

    /**
     * 获取混淆统计信息
     *
     * @return ObfuscationStats 统计信息
     */
    fun getStats(): ObfuscationStats

    /**
     * 重置统计信息
     */
    fun resetStats()
}

/**
 * 混淆统计信息
 */
data class ObfuscationStats(
    val totalRealReads: Long = 0,
    val totalDummyReads: Long = 0,
    val totalRealWrites: Long = 0,
    val totalDummyWrites: Long = 0,
    val averageDummyCount: Double = 0.0
)
