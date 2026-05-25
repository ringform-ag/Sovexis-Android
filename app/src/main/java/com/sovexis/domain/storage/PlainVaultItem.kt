package com.sovexis.domain.storage

/**
 * 保险箱明文数据模型
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: Path ORAM 详细指南 · Sovexis 存储混淆模块 Level 2 (陵谦)
 *
 * 用途：
 * - Path ORAM 的 Stash 中存放此类型
 * - UI 层展示数据使用此类型
 * - 加密前/解密后的中间表示
 *
 * @param id 数据项 ID
 * @param ownerDid 所有者 DID
 * @param title 标题（明文）
 * @param content 内容（明文）
 * @param createdAt 创建时间戳
 * @param updatedAt 更新时间戳
 */
data class PlainVaultItem(
    val id: String,
    val ownerDid: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)
