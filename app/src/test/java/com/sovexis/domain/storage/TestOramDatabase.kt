package com.sovexis.domain.storage

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * ORAM 测试数据库
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 用于 Path ORAM 单元测试
 *
 * 包含：
 * - OramBucket：ORAM 树桶
 * - PositionMapEntry：位置映射条目
 * - VaultItemEntity：保险箱数据实体
 */
@Database(
    entities = [
        OramBucket::class,
        PositionMapEntry::class,
        VaultItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TestOramDatabase : RoomDatabase() {
    abstract fun oramBucketDao(): OramBucketDao
    abstract fun positionMapDao(): PositionMapDao
    abstract fun vaultDao(): VaultDao
}
