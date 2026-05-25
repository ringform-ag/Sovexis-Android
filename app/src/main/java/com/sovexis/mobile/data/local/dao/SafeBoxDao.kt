package com.sovexis.mobile.data.local.dao

import androidx.room.*
import com.sovexis.mobile.data.local.entity.SafeBoxItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafeBoxDao {

    @Query("SELECT * FROM safebox_items WHERE ownerDid = :ownerDid ORDER BY createdAt DESC")
    fun getItemsByOwner(ownerDid: String): Flow<List<SafeBoxItemEntity>>

    @Query("SELECT * FROM safebox_items WHERE itemId = :itemId")
    suspend fun getItemById(itemId: String): SafeBoxItemEntity?

    @Query("SELECT * FROM safebox_items WHERE itemType = :type AND ownerDid = :ownerDid")
    fun getItemsByType(ownerDid: String, type: String): Flow<List<SafeBoxItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SafeBoxItemEntity)

    @Update
    suspend fun updateItem(item: SafeBoxItemEntity)

    @Query("UPDATE safebox_items SET reEncryptionKey = :key, sharedWith = :sharedWith, updatedAt = :timestamp WHERE itemId = :itemId")
    suspend fun updateSharingInfo(itemId: String, key: String?, sharedWith: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE safebox_items SET accessPattern = :pattern WHERE itemId = :itemId")
    suspend fun updateAccessPattern(itemId: String, pattern: String)

    @Delete
    suspend fun deleteItem(item: SafeBoxItemEntity)
}
