package com.sovexis.domain.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * ORAM 桶数据访问对象
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: Path ORAM 详细指南 · Sovexis 存储混淆模块 Level 2 (陵谦)
 */
@Dao
interface OramBucketDao {
    /**
     * 根据桶编号获取桶
     */
    @Query("SELECT * FROM oram_buckets WHERE bucketId = :bucketId")
    suspend fun getBucket(bucketId: Int): OramBucket?

    /**
     * 插入或更新桶
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bucket: OramBucket)

    /**
     * 更新桶
     */
    @Update
    suspend fun update(bucket: OramBucket)

    /**
     * 获取桶总数
     */
    @Query("SELECT COUNT(*) FROM oram_buckets")
    suspend fun count(): Int

    /**
     * 删除所有桶（用于重置）
     */
    @Query("DELETE FROM oram_buckets")
    suspend fun deleteAll()
}
