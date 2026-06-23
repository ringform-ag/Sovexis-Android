package com.sovexis.ui.feature.serviceprovider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.communication.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class ServiceProviderUiState(
    val isLoading: Boolean = false,
    val serviceDID: String = "",
    val isServiceProvider: Boolean = false,
    val activeContracts: List<String> = emptyList(),
    // Bid config
    val autoBid: Boolean = false,
    val minPrices: Map<String, Double> = emptyMap(),
    val whitelist: List<String> = emptyList(),
    val blacklist: List<String> = emptyList(),
    val maxActiveContracts: Int = 10,
    // Pending bid proposals
    val pendingBids: List<BidProposal> = emptyList(),
    // Revenue
    val revenueTotal: Double = 0.0,
    val revenueBreakdown: List<RevenueBreakdownItem> = emptyList(),
    // Messages
    val snackbar: String? = null,
    // SLA
    val consecutiveBreaches: Int = 0,
    val isDowngrading: Boolean = false
)

data class BidProposal(
    val requestId: String,
    val serviceType: String,
    val price: Double,
    val priceUnit: String,
    val capacity: Double,
    val sla: Double,
    val requesterSummary: String,
    val expiresAt: String
)

data class RevenueBreakdownItem(
    val serviceType: String,
    val amount: Double,
    val contractCount: Int
)

@HiltViewModel
class ServiceProviderViewModel @Inject constructor(
    private val wsManager: WebSocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceProviderUiState())
    val uiState: StateFlow<ServiceProviderUiState> = _uiState.asStateFlow()

    init {
        registerCallbacks()
    }

    private fun registerCallbacks() {
        // 报价确认回调
        wsManager.setOnBidConfirmed { requestId ->
            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(
                        pendingBids = state.pendingBids.filter { it.requestId != requestId },
                        activeContracts = state.activeContracts + requestId,
                        snackbar = "报价已确认: $requestId"
                    )
                }
            }
        }

        // 收益报告回调
        wsManager.setOnRevenueReport { json ->
            viewModelScope.launch {
                try {
                    val payload = json.optJSONObject("payload") ?: json
                    val total = payload.optDouble("total", 0.0)
                    val breakdownArr = payload.optJSONArray("breakdown")
                    val breakdown = mutableListOf<RevenueBreakdownItem>()
                    if (breakdownArr != null) {
                        for (i in 0 until breakdownArr.length()) {
                            val item = breakdownArr.getJSONObject(i)
                            breakdown.add(RevenueBreakdownItem(
                                serviceType = item.optString("contract_id", ""),
                                amount = item.optDouble("amount", 0.0),
                                contractCount = item.optInt("contract_count", 1)
                            ))
                        }
                    }
                    _uiState.update { state ->
                        state.copy(
                            revenueTotal = total,
                            revenueBreakdown = breakdown
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        // 质押状态变更回调
        wsManager.setOnServiceStakeChanged { action, serviceDID, amount ->
            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(snackbar = "质押$action: $serviceDID, $amount AGT")
                }
            }
        }

        // 委派/壳体变更由 ContractTransferVM 处理，这里不需要注册
    }

    fun confirmBid(requestId: String) {
        viewModelScope.launch {
            val msg = JSONObject().apply {
                put("type", "confirm_bid")
                put("payload", JSONObject().apply {
                    put("request_id", requestId)
                })
            }
            wsManager.sendRawMessage(msg.toString())

            _uiState.update { state ->
                state.copy(
                    pendingBids = state.pendingBids.filter { it.requestId != requestId }
                )
            }
        }
    }

    fun rejectBid(requestId: String) {
        _uiState.update { state ->
            state.copy(
                pendingBids = state.pendingBids.filter { it.requestId != requestId }
            )
        }
    }

    fun updateBidConfig(
        autoBid: Boolean,
        minPrices: Map<String, Double>,
        whitelist: List<String>,
        blacklist: List<String>,
        maxActiveContracts: Int
    ) {
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("auto_bid", autoBid)
                put("min_prices", JSONObject(minPrices))
                put("whitelist", JSONArray(whitelist))
                put("blacklist", JSONArray(blacklist))
                put("max_active_contracts", maxActiveContracts)
            }
            val msg = JSONObject().apply {
                put("type", "update_bid_config")
                put("payload", payload)
            }
            wsManager.sendRawMessage(msg.toString())

            _uiState.update {
                it.copy(
                    autoBid = autoBid,
                    minPrices = minPrices,
                    whitelist = whitelist,
                    blacklist = blacklist,
                    maxActiveContracts = maxActiveContracts
                )
            }
        }
    }

    fun queryRevenue(granularity: String = "all") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val msg = JSONObject().apply {
                put("type", "get_revenue_report")
                put("payload", JSONObject().apply {
                    put("granularity", granularity)
                })
            }
            wsManager.sendRawMessage(msg.toString())
            // Response arrives via WebSocket handler and updates state
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun lockServiceStake(serviceDID: String, amount: Double) {
        viewModelScope.launch {
            val msg = JSONObject().apply {
                put("type", "lock_service_stake")
                put("payload", JSONObject().apply {
                    put("service_did", serviceDID)
                    put("amount", amount)
                })
            }
            wsManager.sendRawMessage(msg.toString())
            _uiState.update { it.copy(snackbar = "质押锁定请求已发送") }
        }
    }

    fun unlockServiceStake(serviceDID: String) {
        viewModelScope.launch {
            val msg = JSONObject().apply {
                put("type", "unlock_service_stake")
                put("payload", JSONObject().apply {
                    put("service_did", serviceDID)
                })
            }
            wsManager.sendRawMessage(msg.toString())
            _uiState.update { it.copy(snackbar = "质押释放请求已发送") }
        }
    }

    fun onRevenueReportReceived(report: JSONObject) {
        val total = report.optDouble("total", 0.0)
        val breakdownArr = report.optJSONArray("breakdown")
        val items = mutableListOf<RevenueBreakdownItem>()
        if (breakdownArr != null) {
            for (i in 0 until breakdownArr.length()) {
                val item = breakdownArr.getJSONObject(i)
                items.add(RevenueBreakdownItem(
                    serviceType = item.optString("service_type", ""),
                    amount = item.optDouble("amount", 0.0),
                    contractCount = item.optInt("contract_count", 0)
                ))
            }
        }
        _uiState.update {
            it.copy(revenueTotal = total, revenueBreakdown = items)
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbar = null) }
    }
}
