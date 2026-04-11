package io.raccoonwallet.app.feature.vault.send

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.raccoonwallet.app.nav.VaultSend
import io.raccoonwallet.app.core.crypto.Hex
import io.raccoonwallet.app.core.model.ChainRegistry
import io.raccoonwallet.app.core.model.Token
import io.raccoonwallet.app.core.model.TokenRegistry
import io.raccoonwallet.app.core.network.Erc20Abi
import io.raccoonwallet.app.deps
import io.raccoonwallet.app.nav.VaultSign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

enum class FeeTier { SLOW, NORMAL, FAST, CUSTOM }

data class FeeOption(
    val maxFeePerGas: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
    val estimatedFeeFormatted: String
)

data class FeeState(
    val selectedTier: FeeTier = FeeTier.NORMAL,
    val slowFee: FeeOption? = null,
    val normalFee: FeeOption? = null,
    val fastFee: FeeOption? = null,
    val customMaxFeeGwei: String = "",
    val customPriorityFeeGwei: String = "",
    val customGasLimit: String = "",
    val estimatedFee: String = "",
    val gasLimit: Long = 21_000,
    val maxFeePerGas: String = "0",
    val maxPriorityFeePerGas: String = "0",
    val isEstimating: Boolean = false,
)

data class SendUiState(
    val chainId: Long = 1L,
    val accountIndex: Int = 0,
    val fromAddress: String = "",
    val chainSymbol: String = "ETH",
    val toAddress: String = "",
    val amount: String = "",
    val nativeBalance: String = "0",
    val nativeBalanceFormatted: String = "0 ETH",
    val selectedToken: Token? = null,
    val availableTokens: List<Token> = emptyList(),
    val tokenBalance: String = "0",
    val fee: FeeState = FeeState(),
    val nonce: Long? = null,
    val addressError: String? = null,
    val amountError: String? = null,
    val canSubmit: Boolean = false,
)

class SendViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val app = application.deps
    private val publicStore = app.publicStore
    private val chainManager = app.chainManager

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    private var estimateJob: Job? = null
    private var estimatedGasLimit: BigInteger = BigInteger.valueOf(21_000)

    init {
        val route = savedStateHandle.toRoute<VaultSend>()
        init(route.chainId, route.accountIndex)
    }

    private fun init(chainId: Long, accountIndex: Int) {
        viewModelScope.launch {
            val chain = ChainRegistry.byChainId(chainId) ?: ChainRegistry.ETHEREUM
            val accounts = publicStore.getAccounts()
            val address = accounts.getOrNull(accountIndex)?.address ?: ""
            val tokens = TokenRegistry.tokensForChain(chainId)

            _uiState.value = _uiState.value.copy(
                chainId = chain.chainId,
                accountIndex = accountIndex,
                fromAddress = address,
                chainSymbol = chain.symbol,
                availableTokens = tokens
            )

            try {
                val client = chainManager.getClient(chain.chainId)
                val balanceWei = client.getBalance(address)
                val balanceEth = Hex.weiToEther(balanceWei)
                _uiState.value = _uiState.value.copy(
                    nativeBalance = balanceWei.toString(),
                    nativeBalanceFormatted = "$balanceEth ${chain.symbol}"
                )
            } catch (_: Exception) {
                val cached = publicStore.getCachedBalance(address, chain.chainId)
                if (cached != null) {
                    val balanceEth = Hex.weiToEther(BigInteger(cached))
                    _uiState.value = _uiState.value.copy(
                        nativeBalance = cached,
                        nativeBalanceFormatted = "$balanceEth ${chain.symbol} (cached)"
                    )
                }
            }

            // Fetch fee data immediately (independent of address/amount)
            fetchFeeTiers(chain.chainId)
        }
    }

    private fun fetchFeeTiers(chainId: Long) {
        viewModelScope.launch {
            try {
                val client = chainManager.getClient(chainId)
                val chain = ChainRegistry.byChainId(chainId) ?: ChainRegistry.ETHEREUM
                val baseFee = withContext(Dispatchers.IO) { client.getBaseFee() }
                val priorityFee = withContext(Dispatchers.IO) { client.getMaxPriorityFeePerGas() }
                val gl = estimatedGasLimit

                val slowPriority = priorityFee.multiply(BigInteger.valueOf(80)).divide(BigInteger.valueOf(100))
                val slowMaxFee = baseFee.multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100)).add(slowPriority)
                val normalMaxFee = baseFee.multiply(BigInteger.valueOf(2)).add(priorityFee)
                val fastPriority = priorityFee.multiply(BigInteger.valueOf(150)).divide(BigInteger.valueOf(100))
                val fastMaxFee = baseFee.multiply(BigInteger.valueOf(3)).add(fastPriority)

                fun feeOption(maxFee: BigInteger, priority: BigInteger) = FeeOption(
                    maxFeePerGas = maxFee,
                    maxPriorityFeePerGas = priority,
                    estimatedFeeFormatted = "~${Hex.weiToEther(gl.multiply(maxFee))} ${chain.symbol}"
                )

                val slow = feeOption(slowMaxFee, slowPriority)
                val normal = feeOption(normalMaxFee, priorityFee)
                val fast = feeOption(fastMaxFee, fastPriority)

                _uiState.value = _uiState.value.copy(
                    fee = _uiState.value.fee.copy(
                        slowFee = slow,
                        normalFee = normal,
                        fastFee = fast,
                        customMaxFeeGwei = weiToGwei(normalMaxFee),
                        customPriorityFeeGwei = weiToGwei(priorityFee),
                        customGasLimit = gl.toString()
                    )
                )
                applySelectedFee()
            } catch (_: Exception) {
                // Fee fetch failed — user can still set custom fees
            }
        }
    }

    fun setToAddress(address: String) {
        val isValid = address.matches(Regex("^0x[0-9a-fA-F]{40}$"))
        val error = if (address.isNotBlank() && !isValid) "Invalid address format" else null
        _uiState.value = _uiState.value.copy(toAddress = address, addressError = error)
        validateAndEstimate()
    }

    fun setAmount(amount: String) {
        val error = when {
            amount.isBlank() -> null
            amount.toDoubleOrNull() == null -> "Invalid amount"
            amount.toDouble() <= 0 -> "Amount must be positive"
            else -> null
        }
        _uiState.value = _uiState.value.copy(amount = amount, amountError = error)
        validateAndEstimate()
    }

    fun selectToken(token: Token?) {
        _uiState.value = _uiState.value.copy(selectedToken = token)
        if (token != null) loadTokenBalance(token)
        validateAndEstimate()
    }

    fun selectFeeTier(tier: FeeTier) {
        val state = _uiState.value
        val fee = state.fee
        val opt = when (tier) {
            FeeTier.SLOW -> fee.slowFee
            FeeTier.NORMAL -> fee.normalFee
            FeeTier.FAST -> fee.fastFee
            FeeTier.CUSTOM -> null
        }
        _uiState.value = state.copy(
            fee = fee.copy(
                selectedTier = tier,
                customMaxFeeGwei = opt?.let { weiToGwei(it.maxFeePerGas) } ?: fee.customMaxFeeGwei,
                customPriorityFeeGwei = opt?.let { weiToGwei(it.maxPriorityFeePerGas) } ?: fee.customPriorityFeeGwei,
                customGasLimit = if (opt != null) fee.gasLimit.toString() else fee.customGasLimit
            )
        )
        applySelectedFee()
    }

    fun setCustomMaxFeeGwei(value: String) {
        _uiState.value = _uiState.value.copy(
            fee = _uiState.value.fee.copy(customMaxFeeGwei = value, selectedTier = FeeTier.CUSTOM)
        )
        applySelectedFee()
    }

    fun setCustomPriorityFeeGwei(value: String) {
        _uiState.value = _uiState.value.copy(
            fee = _uiState.value.fee.copy(customPriorityFeeGwei = value, selectedTier = FeeTier.CUSTOM)
        )
        applySelectedFee()
    }

    fun setCustomGasLimit(value: String) {
        _uiState.value = _uiState.value.copy(
            fee = _uiState.value.fee.copy(customGasLimit = value, selectedTier = FeeTier.CUSTOM)
        )
        applySelectedFee()
    }

    private fun applySelectedFee() {
        val state = _uiState.value
        val fee = state.fee
        val chain = ChainRegistry.byChainId(state.chainId) ?: ChainRegistry.ETHEREUM

        val gasLimit: Long
        val maxFee: BigInteger
        val priorityFee: BigInteger

        when (fee.selectedTier) {
            FeeTier.SLOW -> {
                val opt = fee.slowFee ?: return
                gasLimit = fee.gasLimit
                maxFee = opt.maxFeePerGas
                priorityFee = opt.maxPriorityFeePerGas
            }
            FeeTier.NORMAL -> {
                val opt = fee.normalFee ?: return
                gasLimit = fee.gasLimit
                maxFee = opt.maxFeePerGas
                priorityFee = opt.maxPriorityFeePerGas
            }
            FeeTier.FAST -> {
                val opt = fee.fastFee ?: return
                gasLimit = fee.gasLimit
                maxFee = opt.maxFeePerGas
                priorityFee = opt.maxPriorityFeePerGas
            }
            FeeTier.CUSTOM -> {
                val customMaxGwei = fee.customMaxFeeGwei.toDoubleOrNull() ?: return
                val customPriorityGwei = fee.customPriorityFeeGwei.toDoubleOrNull() ?: return
                val customLimit = fee.customGasLimit.toLongOrNull()
                    ?: estimatedGasLimit.toLong()
                gasLimit = customLimit
                maxFee = gweiToWei(customMaxGwei)
                priorityFee = gweiToWei(customPriorityGwei)
            }
        }

        val totalFeeWei = BigInteger.valueOf(gasLimit).multiply(maxFee)
        val feeFormatted = "~${Hex.weiToEther(totalFeeWei)} ${chain.symbol}"

        val fieldsValid = state.toAddress.matches(Regex("^0x[0-9a-fA-F]{40}$"))
            && state.amount.toDoubleOrNull()?.let { it > 0 } ?: false
            && state.addressError == null && state.amountError == null
            && state.nonce != null
//        val fieldsValid = true

        _uiState.value = state.copy(
            fee = fee.copy(
                gasLimit = gasLimit,
                maxFeePerGas = maxFee.toString(),
                maxPriorityFeePerGas = priorityFee.toString(),
                estimatedFee = feeFormatted,
            ),
            canSubmit = fieldsValid && !fee.isEstimating
        )
    }

    private fun loadTokenBalance(token: Token) {
        viewModelScope.launch {
            try {
                val client = chainManager.getClient(_uiState.value.chainId)
                val data = Erc20Abi.encodeBalanceOf(_uiState.value.fromAddress)
                val result = client.ethCall(token.contractAddress, data)
                val balance = Erc20Abi.decodeUint256(result)
                val formatted = Hex.weiToEther(balance, token.decimals)
                publicStore.setCachedTokenBalance(
                    _uiState.value.fromAddress, token.chainId, token.contractAddress, balance.toString()
                )
                _uiState.value = _uiState.value.copy(tokenBalance = "$formatted ${token.symbol}")
            } catch (_: Exception) {
                val cached = publicStore.getCachedTokenBalance(
                    _uiState.value.fromAddress, token.chainId, token.contractAddress
                )
                if (cached != null) {
                    val formatted = Hex.weiToEther(BigInteger(cached), token.decimals)
                    _uiState.value = _uiState.value.copy(tokenBalance = "$formatted ${token.symbol} (cached)")
                } else {
                    _uiState.value = _uiState.value.copy(tokenBalance = "-- ${token.symbol}")
                }
            }
        }
    }

    private fun validateAndEstimate() {
        val state = _uiState.value
        val isValidAddress = state.toAddress.matches(Regex("^0x[0-9a-fA-F]{40}$"))
        val isValidAmount = state.amount.toDoubleOrNull()?.let { it > 0 } ?: false
        val fieldsValid = isValidAddress && isValidAmount && state.addressError == null && state.amountError == null

        _uiState.value = state.copy(canSubmit = false)

        if (fieldsValid) {
            estimateJob?.cancel()
            estimateJob = viewModelScope.launch {
                delay(300)
                estimateGas()
            }
        }
    }

    private suspend fun estimateGas() {
        val state = _uiState.value
        _uiState.value = state.copy(fee = state.fee.copy(isEstimating = true))

        try {
            val client = chainManager.getClient(state.chainId)

            val token = state.selectedToken
            val txTo: String
            val txValue: BigInteger
            val txData: String

            if (token != null) {
                txTo = token.contractAddress
                txValue = BigInteger.ZERO
                txData = Erc20Abi.encodeTransfer(state.toAddress, Hex.etherToWei(state.amount, token.decimals))
            } else {
                txTo = state.toAddress
                txValue = Hex.etherToWei(state.amount)
                txData = "0x"
            }

            val (nonce, gasLimit) = withContext(Dispatchers.IO) {
                val n = client.getNonce(state.fromAddress)
                val gl = try {
                    client.estimateGas(state.fromAddress, txTo, txValue, txData)
                } catch (_: Exception) {
                    if (token != null) BigInteger.valueOf(65_000) else BigInteger.valueOf(21_000)
                }
                Pair(n, gl)
            }

            estimatedGasLimit = gasLimit

            _uiState.value = _uiState.value.copy(
                nonce = nonce.toLong(),
                fee = _uiState.value.fee.copy(
                    gasLimit = gasLimit.toLong(),
                    isEstimating = false,
                ),
            )

            // Refresh fee tiers with the actual gas limit — applySelectedFee
            // inside fetchFeeTiers will set canSubmit once tiers are available.
            fetchFeeTiers(state.chainId)
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(
                fee = _uiState.value.fee.copy(
                    estimatedFee = "Gas estimation failed",
                    isEstimating = false,
                )
            )
        }
    }

    fun buildSignRoute(): VaultSign {
        val state = _uiState.value
        val token = state.selectedToken

        val to: String
        val valueWei: String
        val data: String

        if (token != null) {
            to = token.contractAddress
            valueWei = "0"
            data = Erc20Abi.encodeTransfer(state.toAddress, Hex.etherToWei(state.amount, token.decimals))
        } else {
            to = state.toAddress
            valueWei = Hex.etherToWei(state.amount).toString()
            data = "0x"
        }

        return VaultSign(
            chainId = state.chainId,
            accountIndex = state.accountIndex,
            to = to,
            valueWei = valueWei,
            data = data,
            nonce = state.nonce!!,
            gasLimit = state.fee.gasLimit,
            maxFeePerGas = state.fee.maxFeePerGas,
            maxPriorityFeePerGas = state.fee.maxPriorityFeePerGas
        )
    }

    companion object {
        private val GWEI_MULTIPLIER = BigDecimal("1000000000")

        fun gweiToWei(gwei: Double): BigInteger =
            BigDecimal(gwei.toString())
                .multiply(GWEI_MULTIPLIER)
                .setScale(0, RoundingMode.DOWN)
                .toBigInteger()

        fun weiToGwei(wei: BigInteger): String =
            Hex.weiToEther(wei, 9, 2).toPlainString()
    }
}
