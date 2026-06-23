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

    /** 当前活跃身份 DID */
    private val _activeIdentityDID = MutableStateFlow<String>("")
    val activeIdentityDID: StateFlow<String> = _activeIdentityDID.asStateFlow()

    /** 是否系统托管（壳账户激活） */
    private val _isShellMode = MutableStateFlow(false)
    val isShellMode: StateFlow<Boolean> = _isShellMode.asStateFlow()

    fun update(accounts: List<SovexisAccount>) {
        _accounts.value = accounts
        // 自动检测活跃身份
        val active = accounts.firstOrNull { it.isActive }
        if (active != null) {
            _activeIdentityDID.value = active.did
        }
    }

    fun setActiveIdentity(did: String) {
        _activeIdentityDID.value = did
    }

    fun setShellMode(enabled: Boolean) {
        _isShellMode.value = enabled
        if (enabled) {
            _activeIdentityDID.value = "系统托管"
        }
    }

    fun clearShellMode() {
        _isShellMode.value = false
    }
}
