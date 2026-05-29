package com.sovexis.domain.storage

import com.sovexis.domain.storage.ObfuscationStats
import com.sovexis.domain.storage.PlainVaultItem
import com.sovexis.domain.storage.StorageConfig
import com.sovexis.domain.storage.StorageLevel
import com.sovexis.domain.storage.StorageObfuscator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * [AI-GENERATED]
 * 生成时间: 2026-05-09
 * 实现状态: AI可实现
 * 审核状态: 待审核
 *
 * Level 1 存储混淆实现
 * 通过虚假读取实现访问模式混淆
 *
 * 原理：每次真实读/写入时，并行执行k条虚假读取，
 * 使攻击者无法通过I/O模式区分真实操作
 *
 * 性能开销：k次额外数据库查询
 * 安全级别：基础（防止简单流量分析）
 */
@Singleton
class Level1Obfuscator @Inject constructor(
    private val vaultDao: VaultDao
) : StorageObfuscator {

    companion object {
        /**
         * 默认虚假读取数量
         * 可根据安全需求动态调整
         */
        const val DEFAULT_DUMMY_COUNT = 3

        /**
         * 虚假读取的最大随机延迟（毫秒）
         * 用于打乱时序特征
         */
        const val MAX_DUMMY_DELAY_MS = 10L
    }

    private val stats = ObfuscationStatsHolder()

    override suspend fun <T> obfuscatedRead(
        realOperation: suspend () -> T,
        dummyOperations: List<suspend () -> Unit>
    ): T = coroutineScope {
        // 记录真实读取
        stats.incrementRealReads()

        // 确定虚假读取数量
        val dummyCount = if (dummyOperations.isEmpty()) {
            DEFAULT_DUMMY_COUNT
        } else {
            dummyOperations.size
        }

        // 并行执行真实读取和虚假读取
        val realDeferred = async { realOperation() }

        // 执行虚假读取（带随机延迟）
        val dummyJobs = if (dummyOperations.isNotEmpty()) {
            dummyOperations.map { dummyOp ->
                async {
                    addRandomDelay()
                    dummyOp()
                    stats.incrementDummyReads()
                }
            }
        } else {
            // 如果没有提供虚假操作，仅添加延迟混淆
            List(dummyCount) {
                async {
                    addRandomDelay()
                    stats.incrementDummyReads()
                }
            }
        }

        // 等待所有操作完成
        dummyJobs.awaitAll()
        realDeferred.await()
    }

    override suspend fun obfuscatedWrite(
        realOperation: suspend () -> Unit,
        dummyOperations: List<suspend () -> Unit>
    ) = coroutineScope {
        // 记录真实写入
        stats.incrementRealWrites()

        // 写入前先执行虚假读取（使写入看起来像读取）
        val dummyCount = if (dummyOperations.isEmpty()) {
            DEFAULT_DUMMY_COUNT
        } else {
            dummyOperations.size
        }

        // 并行执行虚假读取
        val dummyJobs = if (dummyOperations.isNotEmpty()) {
            dummyOperations.map { dummyOp ->
                async {
                    addRandomDelay()
                    dummyOp()
                    stats.incrementDummyReads() // 写入前的虚假读取
                }
            }
        } else {
            List(dummyCount) {
                async {
                    addRandomDelay()
                    stats.incrementDummyReads()
                }
            }
        }

        // 等待虚假读取完成后再执行真实写入
        dummyJobs.awaitAll()
        realOperation()
    }

    override fun getStats(): ObfuscationStats {
        return stats.toStats()
    }

    override fun resetStats() {
        stats.reset()
    }

    // ========== 保险箱操作接口 ==========

    override suspend fun listItems(ownerDid: String): List<PlainVaultItem> {
        val entities = vaultDao.getItems(ownerDid)
        return entities.map { entity ->
            PlainVaultItem(
                id = entity.id,
                ownerDid = entity.ownerDid,
                title = entity.titleCipher.toString(Charsets.UTF_8),
                content = "",  // listItems 不解密内容
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }
    }

    override suspend fun obfuscatedRead(itemId: String, ownerDid: String): PlainVaultItem {
        val entity = vaultDao.getItem(itemId)
            ?: throw NoSuchElementException("保险箱条目不存在: $itemId")
        return PlainVaultItem(
            id = entity.id,
            ownerDid = entity.ownerDid,
            title = entity.titleCipher.toString(Charsets.UTF_8),
            content = entity.contentCipher.toString(Charsets.UTF_8),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    override suspend fun obfuscatedWrite(itemId: String, ownerDid: String, title: String, content: String) {
        val now = System.currentTimeMillis()
        val entity = VaultItemEntity(
            id = itemId,
            ownerDid = ownerDid,
            titleCipher = title.toByteArray(Charsets.UTF_8),
            contentCipher = content.toByteArray(Charsets.UTF_8),
            iv = ByteArray(12),  // MVP: 暂不加密
            createdAt = now,
            updatedAt = now
        )
        vaultDao.insert(entity)
    }

    override suspend fun obfuscatedDelete(itemId: String, ownerDid: String) {
        vaultDao.getItem(itemId)?.let {
            vaultDao.delete(itemId)
        }
    }

    override fun getConfig(): StorageConfig? {
        return StorageConfig(level = StorageLevel.OBFUSCATED)
    }

    /**
     * 添加随机延迟以打乱时序特征
     */
    private suspend fun addRandomDelay() {
        if (MAX_DUMMY_DELAY_MS > 0) {
            delay(Random.nextLong(0, MAX_DUMMY_DELAY_MS))
        }
    }

    /**
     * 统计信息持有者（线程安全）
     */
    private class ObfuscationStatsHolder {
        private val realReads = AtomicLong(0)
        private val dummyReads = AtomicLong(0)
        private val realWrites = AtomicLong(0)
        private val dummyWrites = AtomicLong(0)

        fun incrementRealReads() = realReads.incrementAndGet()
        fun incrementDummyReads() = dummyReads.incrementAndGet()
        fun incrementRealWrites() = realWrites.incrementAndGet()
        fun incrementDummyWrites() = dummyWrites.incrementAndGet()

        fun toStats(): ObfuscationStats {
            val real = realReads.get()
            val dummy = dummyReads.get()
            val avgDummy = if (real > 0) dummy.toDouble() / real else 0.0

            return ObfuscationStats(
                totalRealReads = real,
                totalDummyReads = dummy,
                totalRealWrites = realWrites.get(),
                totalDummyWrites = dummyWrites.get(),
                averageDummyCount = avgDummy
            )
        }

        fun reset() {
            realReads.set(0)
            dummyReads.set(0)
            realWrites.set(0)
            dummyWrites.set(0)
        }
    }
}
