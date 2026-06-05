package com.sovexis.ui.components

import com.sovexis.domain.identity.SovexisAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局账号状态持有者 — 解决抽屉菜单在不同页面间账号列表不一致的问题。
 * 
 * HomeViewModel / IdentityManagementViewModel 在加载账号后调用 update()，
 * SovexisScaffold 在收到空列表时自动回退到此全局状态。
 */
object AccountStateHolder {
    private val _accounts = MutableStateFlow<List<SovexisAccount>>(emptyList())
    val accounts: StateFlow<List<SovexisAccount>> = _accounts.asStateFlow()

    fun update(accounts: List<SovexisAccount>) {
        _accounts.value = accounts
    }
}
