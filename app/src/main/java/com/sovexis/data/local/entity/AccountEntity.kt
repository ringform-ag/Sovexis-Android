package com.sovexis.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账号实体
 * 支持主账号、副账号、管家副账号三种角色
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val did: String,
    val alias: String,
    val role: AccountRole,
    val publicKeyPem: String,
    val avatar: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

enum class AccountRole {
    PRIMARY,      // 主账号
    SUB,          // 副账号
    STEWARD       // 管家副账号
}
