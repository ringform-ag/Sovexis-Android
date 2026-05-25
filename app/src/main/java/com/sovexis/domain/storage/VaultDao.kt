package com.sovexis.domain.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 保险箱数据访问对象
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: Path ORAM 详细指南 · Sovexis 存储混淆模块 Level 2 (陵谦)
 *
 * 提供保险箱数据的 Room 操作接口。
 * Level1Obfuscator 和 PathOramImpl（间接）使用此 DAO。
 */
@Dao
interface VaultDao {
    /**
     * 根据 ID 获取保险箱数据项
     */
    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getItem(id: String): VaultItemEntity?

    /**
     * 根据所有者 DID 获取所有数据项
     */
    @Query("SELECT * FROM vault_items WHERE ownerDid = :ownerDid")
    suspend fun getItems(ownerDid: String): List<VaultItemEntity>

    /**
     * 插入或更新数据项
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity)

    /**
     * 删除数据项
     */
    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * 删除所有数据项
     */
    @Query("DELETE FROM vault_items")
    suspend fun deleteAll()

    /**
     * 获取数据项数量
     */
    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun count(): Int
}
