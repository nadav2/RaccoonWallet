package io.raccoonwallet.app.feature.vault.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.raccoonwallet.app.core.model.Account
import io.raccoonwallet.app.core.model.Chain
import io.raccoonwallet.app.core.model.ChainRegistry
import io.raccoonwallet.app.core.model.TransactionRecord
import io.raccoonwallet.app.core.model.TxStatus
import io.raccoonwallet.app.deps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TxFilterState(
    val searchQuery: String = "",
    val statusFilter: TxStatus? = null,
    val chainFilter: Long? = null,
    val dateRangeStart: Long? = null,
    val dateRangeEnd: Long? = null
)

data class TxHistoryUiState(
    val filteredTransactions: List<TransactionRecord> = emptyList(),
    val filter: TxFilterState = TxFilterState(),
    val availableChains: List<Chain> = emptyList(),
    val allChains: List<Chain> = ChainRegistry.all,
    val allAccounts: List<Account> = emptyList(),
    val activeAccountIndex: Int = 0,
    val isLoading: Boolean = true,
    val selectedTransaction: TransactionRecord? = null
)

class TransactionHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val publicStore = application.deps.publicStore

    private var allTransactions: List<TransactionRecord> = emptyList()

    private val _uiState = MutableStateFlow(TxHistoryUiState())
    val uiState: StateFlow<TxHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val activeChainId = publicStore.getActiveChainId()
            val activeIndex = publicStore.getActiveAccountIndex()
            val accounts = publicStore.getAccounts()

            _uiState.value = _uiState.value.copy(
                filter = TxFilterState(chainFilter = activeChainId),
                allAccounts = accounts,
                activeAccountIndex = activeIndex
            )
            loadTransactions()
        }
    }

    fun switchAccount(accountIndex: Int) {
        _uiState.value = _uiState.value.copy(activeAccountIndex = accountIndex)
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            val address = _uiState.value.allAccounts
                .getOrNull(_uiState.value.activeAccountIndex)?.address ?: ""

            allTransactions = publicStore.getTransactionHistory()
                .filter { it.from.equals(address, ignoreCase = true) }
                .sortedByDescending { it.timestamp }

            val chainsWithTxs = allTransactions.map { it.chainId }.distinct()
                .mapNotNull { ChainRegistry.byChainId(it) }

            _uiState.value = _uiState.value.copy(
                filteredTransactions = applyFilters(allTransactions, _uiState.value.filter),
                availableChains = chainsWithTxs,
                isLoading = false
            )
        }
    }

    fun setSearchQuery(query: String) {
        updateFilter { it.copy(searchQuery = query) }
    }

    fun setStatusFilter(status: TxStatus?) {
        updateFilter { it.copy(statusFilter = status) }
    }

    fun setChainFilter(chainId: Long?) {
        updateFilter { it.copy(chainFilter = chainId) }
    }

    fun setDateRange(start: Long?, end: Long?) {
        updateFilter { it.copy(dateRangeStart = start, dateRangeEnd = end) }
    }

    fun selectTransaction(tx: TransactionRecord?) {
        _uiState.value = _uiState.value.copy(selectedTransaction = tx)
    }

    private fun updateFilter(transform: (TxFilterState) -> TxFilterState) {
        val newFilter = transform(_uiState.value.filter)
        _uiState.value = _uiState.value.copy(
            filter = newFilter,
            filteredTransactions = applyFilters(allTransactions, newFilter)
        )
    }

    private fun applyFilters(
        transactions: List<TransactionRecord>,
        filter: TxFilterState
    ): List<TransactionRecord> {
        return transactions.filter { tx ->
            val matchesSearch = filter.searchQuery.isBlank() ||
                tx.hash.contains(filter.searchQuery, ignoreCase = true) ||
                tx.to.contains(filter.searchQuery, ignoreCase = true)

            val matchesStatus = filter.statusFilter == null || tx.status == filter.statusFilter
            val matchesChain = filter.chainFilter == null || tx.chainId == filter.chainFilter
            val matchesDateStart = filter.dateRangeStart == null || tx.timestamp >= filter.dateRangeStart
            val matchesDateEnd = filter.dateRangeEnd == null || tx.timestamp <= filter.dateRangeEnd

            matchesSearch && matchesStatus && matchesChain && matchesDateStart && matchesDateEnd
        }
    }
}
