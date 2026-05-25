package com.sovexis.domain.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 保险箱数据 Room 实体
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: Path ORAM 详细指南 · Sovexis 存储混淆模块 Level 2 (陵谦)
 *
 * 存储加密后的保险箱数据。对应 vault_items 表。
 * VaultDao 操作的是此实体。
 *
 * @param id 数据项 ID
 * @param ownerDid 所有者 DID
 * @param titleCipher 标题密文（AES-GCM 加密）
 * @param contentCipher 内容密文（AES-GCM 加密）
 * @param iv 初始化向量（12 字节）
 * @param createdAt 创建时间戳
 * @param updatedAt 更新时间戳
 */
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val ownerDid: String,
    val titleCipher: ByteArray,
    val contentCipher: ByteArray,
    val iv: ByteArray,
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VaultItemEntity

        if (id != other.id) return false
        if (ownerDid != other.ownerDid) return false
        if (!titleCipher.contentEquals(other.titleCipher)) return false
        if (!contentCipher.contentEquals(other.contentCipher)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + ownerDid.hashCode()
        result = 31 * result + titleCipher.contentHashCode()
        result = 31 * result + contentCipher.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
