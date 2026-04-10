package io.raccoonwallet.app.core.network

import io.raccoonwallet.app.core.crypto.Hex
import io.raccoonwallet.app.core.model.Token
import io.raccoonwallet.app.core.model.TokenBalance
import io.raccoonwallet.app.core.storage.PublicStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.math.BigInteger

class TokenBalanceLoader(
    private val rpc: EvmRpcClient,
    private val store: PublicStore? = null
) {

    suspend fun loadBalances(address: String, tokens: List<Token>): List<TokenBalance> =
        coroutineScope {
            tokens.map { token ->
                async { loadSingle(address, token) }
            }.awaitAll()
        }

    private suspend fun loadSingle(address: String, token: Token): TokenBalance {
        try {
            val callData = Erc20Abi.encodeBalanceOf(address)
            val result = rpc.ethCall(token.contractAddress, callData)
            val balance = Erc20Abi.decodeUint256(result)
            val formatted = Hex.weiToEther(balance, token.decimals).toPlainString()
            store?.setCachedTokenBalance(address, token.chainId, token.contractAddress, balance.toString())
            return TokenBalance(token, balance, formatted)
        } catch (_: Exception) {
            // Fallback to cached balance
            val cached = store?.getCachedTokenBalance(address, token.chainId, token.contractAddress)
            if (cached != null) {
                val balance = BigInteger(cached)
                val formatted = Hex.weiToEther(balance, token.decimals).toPlainString()
                return TokenBalance(token, balance, "$formatted (cached)")
            }
            return TokenBalance(token, BigInteger.ZERO, "--", loadFailed = true)
        }
    }
}
