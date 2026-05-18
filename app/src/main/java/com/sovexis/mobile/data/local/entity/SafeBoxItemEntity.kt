package com.sovexis.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 保险箱数据项实体
 * 数据经代理重加密保护
 */
@Entity(tableName = "safebox_items")
data class SafeBoxItemEntity(
    @PrimaryKey val itemId: String,
    val ownerDid: String,
    val itemType: String,
    val encryptedData: String,       // 代理重加密后的密�?    val reEncryptionKey: String? = null,  // 重加密密钥（用于分享�?    val sharedWith: String? = null,  // 被分享者的 DID 列表 JSON
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val accessPattern: String? = null  // ORAM 访问模式混淆数据
)
