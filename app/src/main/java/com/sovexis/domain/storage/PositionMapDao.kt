package com.sovexis.domain.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 位置映射表数据访问对象
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: Path ORAM 详细指南 · Sovexis 存储混淆模块 Level 2 (陵谦)
 */
@Dao
interface PositionMapDao {
    /**
     * 获取指定 itemId 的映射条目
     */
    @Query("SELECT * FROM oram_position_map WHERE itemId = :itemId")
    suspend fun getEntry(itemId: String): PositionMapEntry?

    /**
     * 获取所有映射条目
     */
    @Query("SELECT * FROM oram_position_map")
    suspend fun getAllEntries(): List<PositionMapEntry>

    /**
     * 插入或更新映射条目
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PositionMapEntry)

    /**
     * 删除指定 itemId 的映射条目
     */
    @Query("DELETE FROM oram_position_map WHERE itemId = :itemId")
    suspend fun delete(itemId: String)

    /**
     * 删除所有映射条目（用于重置）
     */
    @Query("DELETE FROM oram_position_map")
    suspend fun deleteAll()

    /**
     * 获取映射条目数量
     */
    @Query("SELECT COUNT(*) FROM oram_position_map")
    suspend fun count(): Int
}
