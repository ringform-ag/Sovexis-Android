package com.sovexis.domain.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 位置映射表条目
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: Path ORAM 详细指南 · Sovexis 存储混淆模块 Level 2 (陵谦)
 *
 * 存储 itemId 到叶子位置的映射关系。
 * 叶子位置使用 AES-GCM 加密，防止位置信息泄露。
 *
 * @param itemId 数据项 ID
 * @param encryptedLeafPosition AES-GCM 加密后的叶子编号（Base64 编码）
 * @param iv 初始化向量（12 字节，Base64 编码）
 * @param updatedAt 最后更新时间戳
 */
@Entity(tableName = "oram_position_map")
data class PositionMapEntry(
    @PrimaryKey val itemId: String,
    val encryptedLeafPosition: String,
    val iv: String,
    val updatedAt: Long
)
