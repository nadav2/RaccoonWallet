package io.raccoonwallet.app.feature.vault.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.raccoonwallet.app.core.crypto.Hex
import io.raccoonwallet.app.core.model.Account
import io.raccoonwallet.app.core.model.Chain
import io.raccoonwallet.app.core.model.ChainRegistry
import io.raccoonwallet.app.core.model.TokenBalance
import io.raccoonwallet.app.core.model.TokenRegistry
import io.raccoonwallet.app.core.model.TransactionRecord
import io.raccoonwallet.app.core.network.TokenBalanceLoader
import io.raccoonwallet.app.core.network.TxPollingService
import io.raccoonwallet.app.deps
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigInteger

data class DashboardUiState(
    val chainId: Long = 1L,
    val chainName: String = "Ethereum",
    val chainSymbol: String = "ETH",
    val activeAccountIndex: Int = 0,
    val activeAccountLabel: String = "Account 1",
    val activeAddress: String = "",
    val formattedBalance: String = "0 ETH",
    val tokenBalances: List<TokenBalance> = emptyList(),
    val transactions: List<TransactionRecord> = emptyList(),
    val selectedTransaction: TransactionRecord? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val allChains: List<Chain> = ChainRegistry.all,
    val allAccounts: List<Account> = emptyList()
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.deps
    private val publicStore = app.publicStore
    private val chainManager = app.chainManager
    private val txPoller = TxPollingService(publicStore, chainManager)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadDashboard()
        txPoller.startPolling(viewModelScope) { reloadTransactions() }
    }

    fun loadDashboard() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { doLoadDashboard() }
    }

    private suspend fun doLoadDashboard() {
            val chainId = publicStore.getActiveChainId()
            val chain = ChainRegistry.byChainId(chainId) ?: ChainRegistry.ETHEREUM
            val accountIndex = publicStore.getActiveAccountIndex()
            val accounts = publicStore.getAccounts()
            val account = accounts.getOrNull(accountIndex)
            val address = account?.address ?: ""

            _uiState.value = _uiState.value.copy(
                chainId = chain.chainId,
                chainName = chain.name,
                chainSymbol = chain.symbol,
                activeAccountIndex = accountIndex,
                activeAccountLabel = account?.label ?: "Account ${accountIndex + 1}",
                activeAddress = address,
                transactions = publicStore.getTransactionHistory()
                    .filter { it.chainId == chain.chainId && it.from == address },
                allAccounts = accounts,
                isLoading = true
            )

            // Fetch native balance
            try {
                val client = chainManager.getClient(chain.chainId)
                val balanceWei = client.getBalance(address)
                val balanceEth = Hex.weiToEther(balanceWei)
                _uiState.value = _uiState.value.copy(
                    formattedBalance = "$balanceEth ${chain.symbol}",
                    isLoading = false
                )
                publicStore.setCachedBalance(address, chain.chainId, balanceWei.toString())
            } catch (_: Exception) {
                val cached = publicStore.getCachedBalance(address, chain.chainId)
                if (cached != null) {
                    val balanceEth = Hex.weiToEther(BigInteger(cached))
                    _uiState.value = _uiState.value.copy(
                        formattedBalance = "$balanceEth ${chain.symbol} (cached)",
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        formattedBalance = "-- ${chain.symbol}",
                        isLoading = false
                    )
                }
            }

            // Fetch token balances
            loadTokenBalances(chain, address)
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadDashboard()
        // Clear refreshing after load completes
        viewModelScope.launch {
            loadJob?.join()
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun switchChain(newChainId: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            publicStore.setActiveChainId(newChainId)
            doLoadDashboard()
        }
    }

    fun switchAccount(newAccountIndex: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            publicStore.setActiveAccountIndex(newAccountIndex)
            doLoadDashboard()
        }
    }

    private suspend fun loadTokenBalances(chain: Chain, address: String) {
        val tokens = TokenRegistry.tokensForChain(chain.chainId)
        if (tokens.isEmpty()) return

        try {
            val client = chainManager.getClient(chain.chainId)
            val loader = TokenBalanceLoader(client, publicStore)
            val balances = loader.loadBalances(address, tokens)
            _uiState.value = _uiState.value.copy(tokenBalances = balances)
        } catch (_: Exception) {
            // Token balance load failed — leave empty
        }
    }

    fun selectTransaction(tx: TransactionRecord?) {
        _uiState.value = _uiState.value.copy(selectedTransaction = tx)
    }

    private fun reloadTransactions() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(
                transactions = publicStore.getTransactionHistory()
                    .filter { it.chainId == state.chainId && it.from == state.activeAddress }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        txPoller.stopPolling()
    }
}
