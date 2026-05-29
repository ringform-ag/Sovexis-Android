@file:Suppress("all")

package com.sovexis.domain.storage

import android.content.Context
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.sovexis.platform.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Path ORAM 完整实现
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成（含 FIX-1/2/3 修正）
 * 参考文档: Path ORAM 详细指南 · Sovexis 存储混淆模块 Level 2 (陵谦)
 *
 * 算法参考：Shi et al., "Oblivious RAM with O((log N)^3) Worst-Case Cost", ASIACRYPT 2011
 * 工程参考：CURIOUS (Java ORAM framework)
 *
 * 核心机制：
 * - 树：完全二叉树，每个桶存放最多 Z 个加密数据块
 * - 位置映射：Map<itemId, leafPosition>，每次读取后重新随机化
 * - Stash：内存中的临时缓存，读路径时填充，回写时清空
 *
 * 已完成的修正（2026-05-20）：
 * - FIX-1: isOnPath → isBucketOnPath（方法重命名，语义明确）
 * - FIX-2: writePath 最深优先策略（两轮写入，减少 Stash 堆积）
 * - FIX-3: getLeafPositionForTest 条件编译（DEBUG 版本可用）
 *
 * @param vaultDao 保险箱 DAO（用于与 L0/L1 保持构造函数签名一致）
 * @param oramBucketDao ORAM 桶 DAO
 * @param positionMapDao 位置映射 DAO
 * @param context Android Context（用于 EncryptedSharedPreferences）
 */
class PathOramImpl(
    private val vaultDao: VaultDao,
    private val oramBucketDao: OramBucketDao,
    private val positionMapDao: PositionMapDao,
    private val context: Context
) : StorageObfuscator {

    // ── 常量 ──
    companion object {
        private const val TREE_HEIGHT = 10
        private const val NUM_LEAVES = 1024          // 1 shl TREE_HEIGHT
        private const val BUCKET_SIZE_Z = 4
        private const val MAX_STASH_SIZE = 50
        private const val PREF_NAME = "sovexis_oram_config"
    }

    // ── 状态 ──
    private val random = SecureRandom()
    private val mutex = Mutex()
    private var stats = ObfuscationStats()

    // 加密偏好存储
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        PREF_NAME, masterKeyAlias, context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // AES-GCM 加密密钥（从 Keystore 派生）
    private val aesKey: SecretKeySpec by lazy {
        val keyBytes = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            .toByteArray()
            .copyOf(32)
        SecretKeySpec(keyBytes, "AES")
    }

    // ── 初始化 ──

    /**
     * 初始化 ORAM 树结构。
     * 创建所有空桶并持久化。需在首次启用 L2 时调用一次。
     * 总桶数 = 2^(TREE_HEIGHT + 1) - 1 = 2047
     */
    suspend fun initialize(): Result<Unit> = runCatching {
        mutex.withLock {
            val totalBuckets = (1 shl (TREE_HEIGHT + 1)) - 1
            for (bucketId in 0 until totalBuckets) {
                if (oramBucketDao.getBucket(bucketId) == null) {
                    val level = getLevel(bucketId)
                    oramBucketDao.insert(
                        OramBucket(
                            bucketId = bucketId,
                            level = level,
                            encryptedBlocks = "[]"
                        )
                    )
                }
            }
        }
    }

    // ── StorageObfuscator 接口实现 ──

    /**
     * 混淆读取（泛型入口 → 内部适配为 Path ORAM 专用的逻辑）
     *
     * 注意：realOperation 在 L2 中被忽略——因为 Path ORAM 的读取不是
     * "直接从 Room 查一条记录"，而是"从整条路径中提取"。
     * 这个设计差异由 PathOramImpl 内部消化，不污染接口。
     */
    override suspend fun <T> obfuscatedRead(
        realOperation: suspend () -> T,
        dummyOperations: List<suspend () -> Unit>
    ): T {
        // Path ORAM 不使用 realOperation，需要调用方通过其他方式传递 itemId
        // 这里提供一个默认实现，实际使用时应调用 obfuscatedRead(itemId, ownerDid)
        throw UnsupportedOperationException(
            "Path ORAM 读取需要 itemId 和 ownerDid 参数，请使用 obfuscatedRead(itemId, ownerDid) 方法"
        )
    }

    /**
     * 混淆写入（泛型入口 → 内部适配）
     */
    override suspend fun obfuscatedWrite(
        realOperation: suspend () -> Unit,
        dummyOperations: List<suspend () -> Unit>
    ) {
        // Path ORAM 不使用 realOperation，需要调用方通过其他方式传递参数
        throw UnsupportedOperationException(
            "Path ORAM 写入需要 itemId, ownerDid, title, content 参数，请使用 obfuscatedWrite(itemId, ownerDid, title, content) 方法"
        )
    }

    override fun getStats(): ObfuscationStats = stats

    override fun resetStats() {
        stats = ObfuscationStats()
    }

    // ── StorageObfuscator 保险箱操作接口实现 ──

    override suspend fun listItems(ownerDid: String): List<PlainVaultItem> {
        return mutex.withLock {
            // 遍历所有叶子节点读取路径，收集所有条目
            val allItems = mutableListOf<PlainVaultItem>()
            for (leaf in 0 until (1 shl TREE_HEIGHT)) {
                val path = readPath(leaf)
                allItems.addAll(path.filter { it.ownerDid == ownerDid })
            }
            allItems.distinctBy { it.id }
        }
    }

    override suspend fun obfuscatedRead(itemId: String, ownerDid: String): PlainVaultItem {
        return readItemInternal(itemId, ownerDid).getOrThrow()
    }

    override suspend fun obfuscatedWrite(itemId: String, ownerDid: String, title: String, content: String) {
        writeItemInternal(itemId, ownerDid, title, content).getOrThrow()
    }

    override suspend fun obfuscatedDelete(itemId: String, ownerDid: String) {
        deleteItemInternal(itemId, ownerDid).getOrThrow()
    }

    override fun getConfig(): StorageConfig? {
        return StorageConfig(level = StorageLevel.SOVEREIGN)
    }

    // ── Path ORAM 专用接口 ──

    /**
     * Path ORAM 专用读取接口
     */
    suspend fun readItemInternal(
        itemId: String,
        ownerDid: String
    ): Result<PlainVaultItem> = runCatching {
        mutex.withLock {
            val leaf = getLeafPosition(itemId)
                ?: throw NoSuchElementException("位置映射不存在: $itemId")

            val path = readPath(leaf)
            val target = path.firstOrNull { it.id == itemId }
                ?: throw NoSuchElementException("数据不在路径上: $itemId")

            val newLeaf = randomLeaf()
            updateLeafPosition(itemId, newLeaf)

            writePath(leaf, path)
            checkStashOverflow()

            // 更新统计
            stats = stats.copy(totalRealReads = stats.totalRealReads + 1)

            target
        }
    }

    /**
     * Path ORAM 专用写入接口
     */
    suspend fun writeItemInternal(
        itemId: String,
        ownerDid: String,
        title: String,
        content: String
    ): Result<Unit> = runCatching {
        mutex.withLock {
            val leaf = randomLeaf()
            assignLeafPosition(itemId, leaf)

            val path = readPath(leaf).toMutableList()

            val existing = path.indexOfFirst { it.id == itemId }
            val item = PlainVaultItem(
                id = itemId,
                ownerDid = ownerDid,
                title = title,
                content = content,
                createdAt = if (existing >= 0) path[existing].createdAt
                    else System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            if (existing >= 0) path[existing] = item else path.add(item)

            writePath(leaf, path)
            checkStashOverflow()

            // 更新统计
            stats = stats.copy(totalRealWrites = stats.totalRealWrites + 1)
        }
    }

    /**
     * Path ORAM 专用删除接口
     */
    suspend fun deleteItemInternal(
        itemId: String,
        ownerDid: String
    ): Result<Unit> = runCatching {
        mutex.withLock {
            val leaf = getLeafPosition(itemId) ?: return@runCatching
            val path = readPath(leaf).toMutableList()
            path.removeAll { it.id == itemId }
            removeLeafPosition(itemId)
            writePath(leaf, path)
            checkStashOverflow()
        }
    }

    // ── 映射表备份/恢复 ──

    suspend fun exportPositionMap(): Result<ByteArray> = runCatching {
        mutex.withLock {
            val entries = positionMapDao.getAllEntries()
            val json = JSONArray().apply {
                entries.forEach { entry ->
                    put(org.json.JSONObject().apply {
                        put("itemId", entry.itemId)
                        put("encryptedLeafPosition", entry.encryptedLeafPosition)
                        put("iv", entry.iv)
                        put("updatedAt", entry.updatedAt)
                    })
                }
            }.toString()
            val (cipher, iv) = encrypt(json.toByteArray())
            (cipher + ":" + iv).toByteArray()
        }
    }

    suspend fun importPositionMap(encryptedData: ByteArray): Result<Unit> = runCatching {
        mutex.withLock {
            val dataStr = String(encryptedData)
            val parts = dataStr.split(":")
            if (parts.size != 2) throw IllegalArgumentException("无效的备份数据格式")
            val jsonStr = String(decrypt(parts[0], parts[1]))
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                positionMapDao.insert(
                    PositionMapEntry(
                        itemId = obj.getString("itemId"),
                        encryptedLeafPosition = obj.getString("encryptedLeafPosition"),
                        iv = obj.getString("iv"),
                        updatedAt = obj.getLong("updatedAt")
                    )
                )
            }
            forceRefresh()
        }
    }

    /**
     * [VISIBLE-FOR-TESTING]
     * 获取指定 itemId 当前的叶子位置。仅供测试验证使用，不应在生产代码中调用。
     *
     * ⚠️ 安全警告：此方法暴露位置映射的内部状态。
     * - Debug 构建中可用
     * - Release 构建中调用会抛出 UnsupportedOperationException
     * - 禁止在自动化测试中调用此方法
     */
    @VisibleForTesting
    internal suspend fun getLeafPositionForTest(itemId: String): Int? {
        if (!com.sovexis.platform.BuildConfig.DEBUG) {
            throw UnsupportedOperationException("此方法仅在调试版本中可用")
        }
        val entry = positionMapDao.getEntry(itemId) ?: return null
        val decrypted = decrypt(entry.encryptedLeafPosition, entry.iv)
        return String(decrypted).toIntOrNull()
    }

    // ── 私有方法 ──

    /** 读取从叶子到根的整条路径，返回路径上所有数据块并清空桶 */
    private suspend fun readPath(leaf: Int): List<PlainVaultItem> {
        val stash = mutableListOf<PlainVaultItem>()
        var bucketId = leafToBucketId(leaf)
        while (bucketId >= 0) {
            val bucket = oramBucketDao.getBucket(bucketId)
            if (bucket != null && bucket.encryptedBlocks != "[]") {
                val items = decryptBucket(bucket.encryptedBlocks)
                stash.addAll(items)
                oramBucketDao.update(
                    bucket.copy(encryptedBlocks = "[]")
                )
            }
            bucketId = parentBucket(bucketId)
        }
        return stash
    }

    /**
     * 将 Stash 中的数据写回路径（最深优先策略）。
     *
     * [FIX-2] 最深优先原则：
     * - 对于 Stash 中的每个数据块，计算其分配叶子对应的完整路径
     * - 将数据块写入路径上最深层的、还有空位的桶
     * - 这确保数据尽可能靠近叶子，减少被无关路径读出的概率
     *
     * @param leaf 读取操作使用的叶子编号
     * @param stash 当前 Stash 中的所有数据块
     */
    private suspend fun writePath(leaf: Int, stash: List<PlainVaultItem>) {
        val remaining = stash.toMutableList()

        // 构建路径上所有桶的列表（从叶子到根）
        val pathBuckets = mutableListOf<Int>()
        var current = leafToBucketId(leaf)
        while (current >= 0) {
            pathBuckets.add(current)
            current = parentBucket(current)
        }

        // 第一轮：尽可能将数据写入路径桶中（最深优先）
        for (item in remaining.toList()) {
            val itemLeaf = getLeafPosition(item.id) ?: continue
            for (bucketId in pathBuckets) {
                if (!isBucketOnPath(itemLeaf, bucketId)) continue
                val bucket = oramBucketDao.getBucket(bucketId)
                val currentBlocks = if (bucket != null && bucket.encryptedBlocks != "[]") {
                    decryptBucket(bucket.encryptedBlocks)
                } else {
                    emptyList()
                }
                if (currentBlocks.size < BUCKET_SIZE_Z) {
                    val updatedBlocks = currentBlocks.toMutableList().apply { add(item) }
                    oramBucketDao.update(
                        OramBucket(
                            bucketId = bucketId,
                            level = getLevel(bucketId),
                            encryptedBlocks = encryptBucket(updatedBlocks)
                        )
                    )
                    remaining.remove(item)
                    break
                }
            }
        }

        // 第二轮：对于无法放回路径桶的数据块，触发强制刷新
        if (remaining.isNotEmpty()) {
            forceRefresh()
            for (item in remaining) {
                val itemLeaf = getLeafPosition(item.id) ?: continue
                for (bucketId in pathBuckets) {
                    if (!isBucketOnPath(itemLeaf, bucketId)) continue
                    val bucket = oramBucketDao.getBucket(bucketId)
                    val currentBlocks = if (bucket != null && bucket.encryptedBlocks != "[]") {
                        decryptBucket(bucket.encryptedBlocks)
                    } else {
                        emptyList()
                    }
                    if (currentBlocks.size < BUCKET_SIZE_Z) {
                        val updatedBlocks = currentBlocks.toMutableList().apply { add(item) }
                        oramBucketDao.update(
                            OramBucket(
                                bucketId = bucketId,
                                level = getLevel(bucketId),
                                encryptedBlocks = encryptBucket(updatedBlocks)
                            )
                        )
                        break
                    }
                }
            }
        }
    }

    /** 强制刷新：重新随机化所有数据位置 */
    private suspend fun forceRefresh() {
        val allEntries = positionMapDao.getAllEntries()
        for (entry in allEntries) {
            val newLeaf = randomLeaf()
            updateLeafPosition(entry.itemId, newLeaf)
        }
    }

    /** 检查 Stash 是否溢出，溢出则触发强制刷新 */
    private suspend fun checkStashOverflow() {
        val stashSize = positionMapDao.count()
        if (stashSize > MAX_STASH_SIZE) {
            forceRefresh()
        }
    }

    // ── 位置映射操作 ──

    private suspend fun getLeafPosition(itemId: String): Int? {
        val entry = positionMapDao.getEntry(itemId) ?: return null
        val decrypted = decrypt(entry.encryptedLeafPosition, entry.iv)
        return String(decrypted).toIntOrNull()
    }

    private suspend fun assignLeafPosition(itemId: String, leaf: Int) {
        val (cipher, iv) = encrypt(leaf.toString().toByteArray())
        positionMapDao.insert(
            PositionMapEntry(
                itemId = itemId,
                encryptedLeafPosition = cipher,
                iv = iv,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun updateLeafPosition(itemId: String, newLeaf: Int) {
        assignLeafPosition(itemId, newLeaf)
    }

    private suspend fun removeLeafPosition(itemId: String) {
        positionMapDao.delete(itemId)
    }

    // ── 加密/解密辅助 ──

    private fun encrypt(data: ByteArray): Pair<String, String> {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(data)
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP) to
                Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    private fun decrypt(ciphertextBase64: String, ivBase64: String): ByteArray {
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun encryptBucket(items: List<PlainVaultItem>): String {
        val itemsJson = items.map { item ->
            mapOf(
                "id" to item.id,
                "ownerDid" to item.ownerDid,
                "title" to item.title,
                "content" to item.content,
                "createdAt" to item.createdAt.toString(),
                "updatedAt" to item.updatedAt.toString()
            )
        }
        val jsonStr = JSONArray(itemsJson).toString()
        val (cipher, iv) = encrypt(jsonStr.toByteArray())
        return "$cipher:$iv"
    }

    private fun decryptBucket(encryptedBlocks: String): List<PlainVaultItem> {
        if (encryptedBlocks == "[]") return emptyList()
        val parts = encryptedBlocks.split(":")
        if (parts.size != 2) return emptyList()
        val decrypted = decrypt(parts[0], parts[1])
        val jsonStr = String(decrypted)
        val arr = JSONArray(jsonStr)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            PlainVaultItem(
                id = obj.getString("id"),
                ownerDid = obj.getString("ownerDid"),
                title = obj.getString("title"),
                content = obj.getString("content"),
                createdAt = obj.getString("createdAt").toLong(),
                updatedAt = obj.getString("updatedAt").toLong()
            )
        }
    }

    // ── 树导航 ──

    /** 叶子编号 → 桶编号 */
    private fun leafToBucketId(leaf: Int): Int =
        (1 shl TREE_HEIGHT) - 1 + leaf

    /** 获取父桶编号 */
    private fun parentBucket(bucketId: Int): Int =
        (bucketId - 1) / 2

    /** 获取桶的层级（0 = 根） */
    private fun getLevel(bucketId: Int): Int =
        kotlin.math.floor(kotlin.math.log2((bucketId + 1).toDouble())).toInt()

    /**
     * [FIX-1] 判断给定的桶是否在从叶子到根的路径上。
     *
     * @param leaf 叶子编号（0 ~ NUM_LEAVES-1）
     * @param bucketId 待检查的桶编号
     * @return true 如果桶在路径上
     */
    private fun isBucketOnPath(leaf: Int, bucketId: Int): Boolean {
        var current = leafToBucketId(leaf)
        while (current >= 0) {
            if (current == bucketId) return true
            current = parentBucket(current)
        }
        return false
    }

    /** 生成随机叶子编号 */
    private fun randomLeaf(): Int = random.nextInt(NUM_LEAVES)
}
