package com.sovexis.ui.feature.contracts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovexis.domain.communication.WebSocketManager
import com.sovexis.domain.credential.CredentialIssuer
import com.sovexis.domain.credential.toJson
import com.sovexis.domain.identity.IdentityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

// ══════════════════════ State ══════════════════════

data class ContractEntry(
    val contractId: String,
    val role: String,             // "consumer" | "provider"
    val peerDid: String,
    val capacity: Double,
    val durationDays: Int,
    val stake: Double,
    val slaUptime: Double,
    val status: String,           // PENDING / ACTIVE / COMPLETED / DISPUTED
    val lastProofAt: String?,
    val createdAt: String
)

data class StorageContractsUiState(
    val isLoading: Boolean = false,
    val myDemands: List<ContractEntry> = emptyList(),
    val myProvisions: List<ContractEntry> = emptyList(),
    val selectedContract: ContractEntry? = null,
    val showCreateDialog: Boolean = false,
    val showDetailDialog: Boolean = false,
    val snackbar: String? = null
)

// ══════════════════════ ViewModel ══════════════════════

@HiltViewModel
class StorageContractsViewModel @Inject constructor(
    private val wsManager: WebSocketManager,
    private val credentialIssuer: CredentialIssuer,
    private val identityManager: IdentityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageContractsUiState())
    val uiState: StateFlow<StorageContractsUiState> = _uiState.asStateFlow()

    init {
        // 注册合约状态回调
        wsManager.setOnContractChanged { contractId, status ->
            viewModelScope.launch {
                updateContractStatus(contractId, status)
            }
        }
        // 注册证明状态回调
        wsManager.setOnProofVerified { contractId, proofHash ->
            viewModelScope.launch {
                _uiState.update { it.copy(snackbar = "证明验证通过: ${proofHash.take(12)}...") }
            }
        }
    }

    fun loadContracts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val msg = JSONObject().apply { put("type", "get_contracts") }
                wsManager.sendRawMessage(msg.toString())
            } catch (_: Exception) {}
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** 创建存储需求 — 签发 C-04 并通过 WebSocket 发送到 Node */
    fun createStorageDemand(capacity: Double, durationDays: Int, stake: Double, slaUptime: Double) {
        viewModelScope.launch {
            try {
                val master = identityManager.getMasterIdentity()
                    ?: run { _uiState.update { it.copy(snackbar = "无主账号") }; return@launch }
                val c04 = credentialIssuer.issueStorageContract(
                    master.did, capacity, durationDays, stake, slaUptime
                )
                val msg = JSONObject().apply {
                    put("type", "create_storage_demand")
                    put("payload", JSONObject().apply {
                        put("contract_id", c04.id)
                        put("capacity", capacity)
                        put("duration_days", durationDays)
                        put("stake", stake)
                        put("sla_uptime", slaUptime)
                        put("credential", JSONObject(c04.toJson()))
                    })
                }
                wsManager.sendRawMessage(msg.toString())
                // 本地预置占位条目
                val entry = ContractEntry(
                    c04.id, "consumer", "", capacity, durationDays, stake, slaUptime,
                    "PENDING", null, java.text.SimpleDateFormat("MM-dd HH:mm",
                        java.util.Locale.getDefault()).format(System.currentTimeMillis())
                )
                _uiState.update { state ->
                    state.copy(
                        myDemands = state.myDemands + entry,
                        showCreateDialog = false,
                        snackbar = "存储需求已发布"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbar = "发布失败: ${e.message}") }
            }
        }
    }

    /** 发起合约争议 */
    fun disputeContract(contractId: String) {
        viewModelScope.launch {
            val msg = JSONObject().apply {
                put("type", "dispute_contract")
                put("payload", JSONObject().apply {
                    put("contract_id", contractId)
                })
            }
            wsManager.sendRawMessage(msg.toString())
            _uiState.update { it.copy(snackbar = "争议已提交") }
        }
    }

    fun selectContract(entry: ContractEntry) {
        _uiState.update { it.copy(selectedContract = entry, showDetailDialog = true) }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun dismissDialogs() {
        _uiState.update { it.copy(showCreateDialog = false, showDetailDialog = false, selectedContract = null) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbar = null) }
    }

    private fun updateContractStatus(contractId: String, status: String) {
        _uiState.update { state ->
            state.copy(
                myDemands = state.myDemands.map { if (it.contractId == contractId) it.copy(status = status) else it },
                myProvisions = state.myProvisions.map { if (it.contractId == contractId) it.copy(status = status) else it }
            )
        }
    }
}

// ══════════════════════ Screen ══════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageContractsScreen(
    viewModel: StorageContractsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadContracts() }
    LaunchedEffect(uiState.snackbar) {
        uiState.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存储合约") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { viewModel.showCreateDialog() }) {
                        Icon(Icons.Default.Add, "新建需求")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Tab 行
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("我的存储需求 (${uiState.myDemands.size})") })
                Tab(selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("我提供的存储 (${uiState.myProvisions.size})") })
            }

            when (selectedTab) {
                0 -> ContractList(uiState.myDemands, viewModel::selectContract)
                1 -> ContractList(uiState.myProvisions, viewModel::selectContract)
            }
        }
    }

    // ── 新建存储需求弹窗 ──
    if (uiState.showCreateDialog) {
        CreateDemandDialog(
            onDismiss = { viewModel.dismissDialogs() },
            onCreate = { cap, days, stake, sla -> viewModel.createStorageDemand(cap, days, stake, sla) }
        )
    }

    // ── 合约详情弹窗 ──
    uiState.selectedContract?.let { contract ->
        ContractDetailDialog(
            contract = contract,
            onDismiss = { viewModel.dismissDialogs() },
            onDispute = { viewModel.disputeContract(contract.contractId) }
        )
    }
}

// ══════════════════ Sub-composables ══════════════════

@Composable
private fun ContractList(contracts: List<ContractEntry>, onSelect: (ContractEntry) -> Unit) {
    if (contracts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Description, null, Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Text("暂无合约", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(contracts, key = { it.contractId }) { contract ->
            ContractCard(contract, onClick = { onSelect(contract) })
        }
    }
}

@Composable
private fun ContractCard(contract: ContractEntry, onClick: () -> Unit) {
    val statusColor = when (contract.status) {
        "ACTIVE" -> Color(0xFF4CAF50)
        "PENDING" -> Color(0xFFFFC107)
        "DISPUTED" -> Color(0xFFEF5350)
        "COMPLETED" -> Color(0xFF90A4AE)
        else -> Color(0xFF90A4AE)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(contract.contractId.take(20) + "…",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(contract.status, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("容量", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${contract.capacity} GB", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("期限", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${contract.durationDays}天", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("质押", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${contract.stake} AGT", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun CreateDemandDialog(
    onDismiss: () -> Unit,
    onCreate: (capacity: Double, durationDays: Int, stake: Double, sla: Double) -> Unit
) {
    var capacity by remember { mutableStateOf("10") }
    var duration by remember { mutableStateOf("30") }
    var stake by remember { mutableStateOf("50") }
    var sla by remember { mutableStateOf("0.999") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建存储需求") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(capacity, { capacity = it }, label = { Text("容量 (GB)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(duration, { duration = it }, label = { Text("期限 (天)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stake, { stake = it }, label = { Text("质押金额 (AGT)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sla, { sla = it }, label = { Text("SLA 可用性") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("例如 0.999 = 99.9%") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val cap = capacity.toDoubleOrNull() ?: return@Button
                val dur = duration.toIntOrNull() ?: return@Button
                val stk = stake.toDoubleOrNull() ?: return@Button
                val s = sla.toDoubleOrNull() ?: return@Button
                onCreate(cap, dur, stk, s)
            }) { Text("签发合约") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ContractDetailDialog(
    contract: ContractEntry,
    onDismiss: () -> Unit,
    onDispute: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("合约详情", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("合约 ID", contract.contractId)
                DetailRow("对方节点", contract.peerDid.ifEmpty { "等待匹配" })
                DetailRow("状态", contract.status)
                DetailRow("容量", "${contract.capacity} GB")
                DetailRow("期限", "${contract.durationDays} 天")
                DetailRow("质押", "${contract.stake} AGT")
                DetailRow("SLA", "${(contract.slaUptime * 100).toInt()}%")
                contract.lastProofAt?.let { DetailRow("最后证明", it) }
            }
        },
        confirmButton = {
            if (contract.status == "ACTIVE") {
                Button(onClick = onDispute, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error)) {
                    Text("发起争议")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
