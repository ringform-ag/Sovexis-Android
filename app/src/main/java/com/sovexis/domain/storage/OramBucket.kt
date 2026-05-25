package com.sovexis.domain.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ORAM 树中的一个桶
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: Path ORAM 详细指南 · Sovexis 存储混淆模块 Level 2 (陵谦)
 *
 * 树结构：
 * - 完全二叉树，叶子数 = 2^树高度
 * - 每个桶存放最多 Z 个加密数据块
 * - 数据块只能存储在从其分配的叶子到根的路径上
 *
 * @param bucketId 桶编号（二叉树的数组表示中的索引）
 * @param level 桶所在的树层级（0 = 根）
 * @param encryptedBlocks 加密后的数据块列表（最多 Z 个），格式为 "ciphertext:iv"
 */
@Entity(tableName = "oram_buckets")
data class OramBucket(
    @PrimaryKey val bucketId: Int,
    val level: Int,
    val encryptedBlocks: String
)
