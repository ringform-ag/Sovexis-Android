package com.sovexis.domain.identity

/**
 * 统一身份接口，用于 Drawer 等 UI 组件展示混合身份列表。
 * MasterIdentity 和 ChildIdentity 均实现此接口。
 */
interface SovexisAccount {
    val did: String
    val alias: String?
    val accountType: AccountType
    val isActive: Boolean
    val isFrozen: Boolean get() = false
    val createdAt: Long get() = 0L
}

enum class AccountType {
    /** 主账号 */
    MASTER,

    /** 标准副账号 */
    CHILD,

    /** 管家副账号 */
    STEWARD,

    /** 服务商副账号 */
    SERVICE
}
