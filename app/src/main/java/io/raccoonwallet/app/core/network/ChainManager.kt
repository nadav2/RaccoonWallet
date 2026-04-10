package io.raccoonwallet.app.core.network

import io.raccoonwallet.app.core.model.ChainRegistry

class ChainManager {
    private val clients = java.util.concurrent.ConcurrentHashMap<Long, EvmRpcClient>()

    fun getClient(chainId: Long): EvmRpcClient {
        return clients.getOrPut(chainId) {
            val chain = ChainRegistry.byChainId(chainId)
                ?: throw IllegalArgumentException("Unknown chain: $chainId")
            EvmRpcClient(JsonRpc(chain.rpcUrl))
        }
    }

}
