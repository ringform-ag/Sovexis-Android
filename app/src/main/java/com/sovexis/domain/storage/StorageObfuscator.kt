package com.sovexis.domain.storage

/**
 * 存储混淆器接口
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-09
 * 实现状态: ✅ 已完成
 * 审核状态: 待审核
 *
 * 存储混淆器接口，定义混淆读写操作的标准接口。
 * Level 1（Level1Obfuscator）和 Level 2（PathOramImpl）都实现此接口。
 *
 * 设计目标：屏蔽不同混淆级别的实现差异，调用方无需关心底层是哪种混淆。
 */
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
     * 执行真实写入的同时附带虚假读取，使写入模式不可区分
     *
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

    // ========== 保险箱操作接口 ==========

    /**
     * 列出保险箱中的所有条目（仅标题，不解密内容）。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 保险箱操作流程应用层串联指令
     *
     * @param ownerDid 所有者 DID
     * @return 保险箱条目列表
     */
    suspend fun listItems(ownerDid: String): List<PlainVaultItem>

    /**
     * 混淆读取保险箱条目（解密内容）。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 保险箱操作流程应用层串联指令
     *
     * @param itemId 条目 ID
     * @param ownerDid 所有者 DID
     * @return 解密后的保险箱条目
     */
    suspend fun obfuscatedRead(itemId: String, ownerDid: String): PlainVaultItem

    /**
     * 混淆写入保险箱条目（加密存储）。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 保险箱操作流程应用层串联指令
     *
     * @param itemId 条目 ID
     * @param ownerDid 所有者 DID
     * @param title 标题
     * @param content 内容
     */
    suspend fun obfuscatedWrite(itemId: String, ownerDid: String, title: String, content: String)

    /**
     * 混淆删除保险箱条目。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 保险箱操作流程应用层串联指令
     *
     * @param itemId 条目 ID
     * @param ownerDid 所有者 DID
     */
    suspend fun obfuscatedDelete(itemId: String, ownerDid: String)

    /**
     * 获取当前存储配置。
     *
     * @return 存储配置，如果没有则返回 null
     */
    fun getConfig(): StorageConfig?
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
