package com.sovexis.domain.zkp

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ZKP 证明缓存管理器
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-21
 * 实现状态: ✅ 已完成
 * 参考文档: Sovexis · ZKP 模块完整实现指令 (陵谦)
 *
 * 缓存策略：
 * - 凭证出示场景：缓存 1 小时（DEFAULT_CACHE_TTL_MS）
 * - 支付/签发/解密场景：不使用缓存，每次新生成
 * - requireFresh=true 时忽略缓存
 */
class ZkpCacheManager {

    companion object {
        private const val DEFAULT_CACHE_TTL_MS = 60 * 60 * 1000L  // 1 小时
        private const val KDFS_CACHE_TTL_MS = 5 * 60 * 1000L      // 5 分钟（KDFS 图案缓存）
        private const val KDFS_CACHE_KEY = "kdfs_pattern_hash"
    }

    private val cache = mutableMapOf<String, ZkpCacheEntry>()
    private val kdfsCache = mutableMapOf<String, KdfsCacheEntry>()
    private val mutex = Mutex()

    private data class ZkpCacheEntry(
        val proof: ZkpProofData,
        val cachedAt: Long
    )

    /**
     * 获取缓存的 ZKP 证明
     */
    suspend fun get(cacheKey: String, requireFresh: Boolean = false): ZkpProofData? {
        if (requireFresh) return null
        return mutex.withLock {
            val entry = cache[cacheKey] ?: return null
            val age = System.currentTimeMillis() - entry.cachedAt
            if (age > DEFAULT_CACHE_TTL_MS) {
                cache.remove(cacheKey)
                null
            } else {
                entry.proof
            }
        }
    }

    /**
     * 缓存 ZKP 证明
     */
    suspend fun put(cacheKey: String, proof: ZkpProofData) {
        mutex.withLock {
            cache[cacheKey] = ZkpCacheEntry(
                proof = proof,
                cachedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * 使缓存失效
     */
    suspend fun invalidate(cacheKey: String) {
        mutex.withLock {
            cache.remove(cacheKey)
        }
    }

    /**
     * 清空所有缓存
     */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
        }
    }

    /**
     * 生成缓存键
     */
    fun generateCacheKey(credentialId: String, challenge: ByteArray): String {
        return "present_${credentialId}_${challenge.contentHashCode()}"
    }

    /**
     * 缓存 KDFS 图案哈希。
     *
     * @param did 身份 DID
     * @param kdfsHash KDFS 图案哈希
     */
    suspend fun cacheKdfs(did: String, kdfsHash: ByteArray) {
        mutex.withLock {
            kdfsCache[did] = KdfsCacheEntry(
                hash = kdfsHash.copyOf(),
                cachedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * 获取缓存的 KDFS 图案哈希。
     *
     * @param did 身份 DID
     * @return KDFS 图案哈希，如果过期或不存在则返回 null
     */
    suspend fun getCachedKdfs(did: String): ByteArray? {
        return mutex.withLock {
            val entry = kdfsCache[did] ?: return null
            val age = System.currentTimeMillis() - entry.cachedAt
            if (age > KDFS_CACHE_TTL_MS) {
                kdfsCache.remove(did)
                null
            } else {
                entry.hash.copyOf()
            }
        }
    }

    /**
     * 获取缓存的 KDFS 图案哈希（使用默认 DID）。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 凭证出示流程应用层串联指令
     *
     * @return KDFS 图案哈希，如果过期或不存在则返回 null
     */
    suspend fun getCachedKdfs(): ByteArray? {
        // 获取第一个可用的 KDFS 缓存（简化实现）
        return mutex.withLock {
            kdfsCache.values.firstOrNull()?.let { entry ->
                val age = System.currentTimeMillis() - entry.cachedAt
                if (age > KDFS_CACHE_TTL_MS) {
                    null
                } else {
                    entry.hash.copyOf()
                }
            }
        }
    }

    /**
     * 缓存 KDFS 图案哈希（使用默认键）。
     *
     * [AI-GENERATED]
     * 实现状态: ✅ 已完成（2026-05-22）
     * 参考文档: Sovexis · 凭证出示流程应用层串联指令
     *
     * @param kdfsHash KDFS 图案哈希
     */
    suspend fun putCachedKdfs(kdfsHash: ByteArray) {
        mutex.withLock {
            kdfsCache[KDFS_CACHE_KEY] = KdfsCacheEntry(
                hash = kdfsHash.copyOf(),
                cachedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * 使 KDFS 缓存失效。
     *
     * @param did 身份 DID
     */
    suspend fun invalidateKdfs(did: String) {
        mutex.withLock {
            kdfsCache.remove(did)
        }
    }
}

/**
 * KDFS 缓存条目。
 */
private data class KdfsCacheEntry(
    val hash: ByteArray,
    val cachedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KdfsCacheEntry) return false
        return hash.contentEquals(other.hash) && cachedAt == other.cachedAt
    }

    override fun hashCode(): Int {
        var result = hash.contentHashCode()
        result = 31 * result + cachedAt.hashCode()
        return result
    }
}
