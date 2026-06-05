package com.sovexis.ui.feature.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.identity.IdentityManager
import com.sovexis.domain.identity.ChildType
import com.sovexis.domain.identity.SovexisAccount
import com.sovexis.domain.payment.PaymentManager
import com.sovexis.domain.policy.PolicyConfig
import com.sovexis.domain.policy.PolicyEnforcer
import com.sovexis.ui.components.AccountStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class IdentityManagementUiState(
    val accounts: List<SovexisAccount> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val balances: Map<String, Double> = emptyMap(),
    val currentPolicy: PolicyConfig? = null
)

@HiltViewModel
class IdentityManagementViewModel @Inject constructor(
    private val identityManager: IdentityManager,
    private val paymentManager: PaymentManager,
    private val policyEnforcer: PolicyEnforcer
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdentityManagementUiState())
    val uiState: StateFlow<IdentityManagementUiState> = _uiState.asStateFlow()

    init { loadAccounts() }

    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = identityManager.getAllIdentities().getOrNull()
            AccountStateHolder.update(result ?: emptyList())
            _uiState.update {
                it.copy(accounts = result ?: emptyList(), isLoading = false)
            }
            refreshBalances(result ?: emptyList())
        }
    }

    private suspend fun refreshBalances(accounts: List<SovexisAccount>) {
        val map = mutableMapOf<String, Double>()
        accounts.forEach { acc ->
            try {
                map[acc.did] = withContext(Dispatchers.IO) { paymentManager.getBalance(acc.did) }
            } catch (_: Exception) {}
        }
        _uiState.update { it.copy(balances = map) }
    }

    fun setActive(did: String) {
        viewModelScope.launch {
            identityManager.setActiveIdentity(did)
            _uiState.update { it.copy(message = "已切换活跃身份") }
            loadAccounts()
        }
    }

    fun setFrozen(did: String, frozen: Boolean) {
        viewModelScope.launch {
            identityManager.setFrozen(did, frozen)
            _uiState.update { it.copy(message = if (frozen) "已锁定" else "已解锁") }
            loadAccounts()
        }
    }

    fun delete(did: String) {
        viewModelScope.launch {
            identityManager.deleteIdentity(did)
            _uiState.update { it.copy(message = "已删除") }
            loadAccounts()
        }
    }

    fun updateAlias(did: String, newAlias: String) {
        viewModelScope.launch {
            identityManager.updateAlias(did, newAlias)
            _uiState.update { it.copy(message = "名称已更新") }
            loadAccounts()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun addSubAccount(type: ChildType, alias: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            identityManager.deriveChildIdentity(type, alias)
                .onSuccess {
                    _uiState.update { it.copy(message = "副账号「${alias.ifEmpty { type.name }}」创建成功") }
                    loadAccounts()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, message = "创建失败: ${e.message}") }
                }
        }
    }

    /** 加载指定 DID 的策略配置到对话框 */
    fun loadPolicy(did: String) {
        viewModelScope.launch {
            try {
                val policy = policyEnforcer.getPolicy(did)
                    ?: PolicyConfig(boundChildDid = did)
                _uiState.update { it.copy(currentPolicy = policy) }
            } catch (_: Exception) {}
        }
    }

    /** 保存策略配置 */
    fun savePolicy(policy: PolicyConfig) {
        viewModelScope.launch {
            try {
                policyEnforcer.savePolicy(policy)
                _uiState.update { it.copy(message = "权限策略已保存", currentPolicy = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "保存失败: ${e.message}") }
            }
        }
    }

    /** 关闭策略编辑（不保存） */
    fun dismissPolicy() {
        _uiState.update { it.copy(currentPolicy = null) }
    }
}
