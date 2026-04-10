package io.raccoonwallet.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Chain(
    val chainId: Long,
    val name: String,
    val symbol: String,
    val decimals: Int = 18,
    val rpcUrl: String,
    val explorerUrl: String,
    val isTestnet: Boolean = false
)

object ChainRegistry {
    val ETHEREUM = Chain(
        chainId = 1,
        name = "Ethereum",
        symbol = "ETH",
        rpcUrl = "https://eth.drpc.org",
        explorerUrl = "https://etherscan.io"
    )
    val BSC = Chain(
        chainId = 56,
        name = "BNB Smart Chain",
        symbol = "BNB",
        rpcUrl = "https://bsc-dataseed.binance.org",
        explorerUrl = "https://bscscan.com"
    )
    val POLYGON = Chain(
        chainId = 137,
        name = "Polygon",
        symbol = "POL",
        rpcUrl = "https://polygon.drpc.org",
        explorerUrl = "https://polygonscan.com"
    )
    val ARBITRUM = Chain(
        chainId = 42161,
        name = "Arbitrum One",
        symbol = "ETH",
        rpcUrl = "https://arb1.arbitrum.io/rpc",
        explorerUrl = "https://arbiscan.io"
    )
    val AVALANCHE = Chain(
        chainId = 43114,
        name = "Avalanche C-Chain",
        symbol = "AVAX",
        rpcUrl = "https://api.avax.network/ext/bc/C/rpc",
        explorerUrl = "https://snowtrace.io"
    )

    val all = listOf(ETHEREUM, BSC, POLYGON, ARBITRUM, AVALANCHE)

    fun byChainId(id: Long): Chain? = all.find { it.chainId == id }
}
