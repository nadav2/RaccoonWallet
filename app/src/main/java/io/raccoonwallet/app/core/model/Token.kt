package io.raccoonwallet.app.core.model

import kotlinx.serialization.Serializable
import java.math.BigInteger

@Serializable
data class Token(
    val contractAddress: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val chainId: Long
)

data class TokenBalance(
    val token: Token,
    val balance: BigInteger,
    val formatted: String,
    val loadFailed: Boolean = false
)

object TokenRegistry {

    private val tokens: List<Token> = listOf(
        // Ethereum (1)
        Token("0xdAC17F958D2ee523a2206206994597C13D831ec7", "USDT", "Tether USD", 6, 1L),
        Token("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", "USDC", "USD Coin", 6, 1L),
        Token("0x6B175474E89094C44Da98b954EedeAC495271d0F", "DAI", "Dai Stablecoin", 18, 1L),
        Token("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", "WETH", "Wrapped Ether", 18, 1L),

        // BSC (56)
        Token("0x55d398326f99059fF775485246999027B3197955", "USDT", "Tether USD", 18, 56L),
        Token("0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d", "USDC", "USD Coin", 18, 56L),
        Token("0x1AF3F329e8BE154074D8769D1FFa4eE058B1DBc3", "DAI", "Dai Stablecoin", 18, 56L),

        // Polygon (137)
        Token("0xc2132D05D31c914a87C6611C10748AEb04B58e8F", "USDT", "Tether USD", 6, 137L),
        Token("0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359", "USDC", "USD Coin", 6, 137L),
        Token("0x8f3Cf7ad23Cd3CaDbD9735AFf958023239c6A063", "DAI", "Dai Stablecoin", 18, 137L),
        Token("0x7ceB23fD6bC0adD59E62ac25578270cFf1b9f619", "WETH", "Wrapped Ether", 18, 137L),

        // Arbitrum (42161)
        Token("0xFd086bC7CD5C481DCC9C85ebE478A1C0b69FCbb9", "USDT", "Tether USD", 6, 42161L),
        Token("0xaf88d065e77c8cC2239327C5EDb3A432268e5831", "USDC", "USD Coin", 6, 42161L),
        Token("0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1", "DAI", "Dai Stablecoin", 18, 42161L),
        Token("0x82aF49447D8a07e3bd95BD0d56f35241523fBab1", "WETH", "Wrapped Ether", 18, 42161L),

        // Avalanche (43114)
        Token("0x9702230A8Ea53601f5cD2dc00fDBc13d4dF4A8c7", "USDT", "Tether USD", 6, 43114L),
        Token("0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E", "USDC", "USD Coin", 6, 43114L),
        Token("0xd586E7F844cEa2F87f50152665BCbc2C279D8d70", "DAI", "Dai Stablecoin", 18, 43114L)
    )

    fun tokensForChain(chainId: Long): List<Token> =
        tokens.filter { it.chainId == chainId }
}
