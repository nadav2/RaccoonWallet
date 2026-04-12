package io.raccoonwallet.app.core.storage

import io.raccoonwallet.app.core.model.Account
import io.raccoonwallet.app.core.model.AppMode
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.core.model.TransactionRecord
import io.raccoonwallet.app.core.model.TxStatus

class PublicStore(private val store: EncryptedJsonStore<PublicStoreData>) {

    suspend fun getAppMode(): AppMode? =
        store.read().appMode?.let { name ->
            try { AppMode.valueOf(name) } catch (_: IllegalArgumentException) { null }
        }

    suspend fun isDkgComplete(): Boolean = store.read().dkgComplete

    suspend fun getAccounts(): List<Account> =
        store.read().accounts.map {
            Account(it.index, it.address, it.publicKeyCompressed, it.label)
        }

    suspend fun getActiveAccountIndex(): Int = store.read().activeAccountIndex

    suspend fun setActiveAccountIndex(index: Int) {
        store.update { it.copy(activeAccountIndex = index) }
    }

    suspend fun getActiveChainId(): Long = store.read().activeChainId

    suspend fun setActiveChainId(chainId: Long) {
        store.update { it.copy(activeChainId = chainId) }
    }

    suspend fun getTransactionHistory(): List<TransactionRecord> =
        store.read().txHistory.map {
            TransactionRecord(it.hash, it.chainId, it.from, it.to, it.value, it.timestamp,
                try { TxStatus.valueOf(it.status) } catch (_: Exception) { TxStatus.PENDING })
        }

    suspend fun addTransactionRecord(record: TransactionRecord) {
        store.update { data ->
            val entry = StoredTxRecord(record.hash, record.chainId, record.from, record.to,
                record.value, record.timestamp, record.status.name)
            data.copy(txHistory = (listOf(entry) + data.txHistory).take(500))
        }
    }

    suspend fun getCachedBalance(address: String, chainId: Long): String? =
        store.read().balanceCache["${chainId}_$address"]

    suspend fun setCachedBalance(address: String, chainId: Long, balanceWei: String) {
        store.update { it.copy(balanceCache = it.balanceCache + ("${chainId}_$address" to balanceWei)) }
    }

    // ── Token balance cache ──

    suspend fun getCachedTokenBalance(address: String, chainId: Long, contractAddress: String): String? =
        store.read().tokenBalanceCache["${chainId}_${contractAddress}_$address"]

    suspend fun setCachedTokenBalance(address: String, chainId: Long, contractAddress: String, balanceRaw: String) {
        store.update {
            it.copy(tokenBalanceCache = it.tokenBalanceCache + ("${chainId}_${contractAddress}_$address" to balanceRaw))
        }
    }

    // ── Transaction status ──

    suspend fun updateTransactionStatus(hash: String, status: TxStatus) {
        store.update { data ->
            data.copy(txHistory = data.txHistory.map { record ->
                if (record.hash == hash) record.copy(status = status.name) else record
            })
        }
    }

    // ── Auth mode ──

    suspend fun getAuthMode(): AuthMode {
        val name = store.read().authMode ?: return AuthMode.NONE
        return try { AuthMode.valueOf(name) } catch (_: IllegalArgumentException) { AuthMode.NONE }
    }

    suspend fun setAuthMode(mode: AuthMode) {
        store.update { it.copy(authMode = mode.name) }
    }

    /**
     * Atomically write accounts + appMode + dkgComplete in a single store.update.
     * This eliminates the partial-DKG window where a crash between separate writes
     * could leave accounts present but dkgComplete=false.
     */
    suspend fun completeDkg(accounts: List<Account>, mode: AppMode) {
        store.update { data ->
            data.copy(
                accounts = accounts.map {
                    StoredAccount(it.index, it.address, it.publicKeyCompressed, it.label)
                },
                appMode = mode.name,
                dkgComplete = true
            )
        }
    }

    suspend fun rewrite() { store.update { it } }

    suspend fun deleteAll() { store.delete() }
}
