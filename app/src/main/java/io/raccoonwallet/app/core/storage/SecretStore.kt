package io.raccoonwallet.app.core.storage

import io.raccoonwallet.app.core.crypto.PaillierCipher
import io.raccoonwallet.app.core.storage.Serializers.toBigIntegerFromBase64
import io.raccoonwallet.app.core.storage.Serializers.toBase64
import io.raccoonwallet.app.core.storage.Serializers.toECPointFromBase64
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

class SecretStore(private val store: EncryptedJsonStore<SecretStoreData>) {

    // ── Paillier Keys (Vault) ──

    suspend fun setPaillierPublicKey(pk: PaillierCipher.PublicKey) {
        store.update { it.copy(paillierN = pk.n.toBase64(), paillierG = pk.g.toBase64()) }
    }

    suspend fun getPaillierPublicKey(): PaillierCipher.PublicKey? {
        val data = store.read()
        return buildPaillierPk(data.paillierN, data.paillierG)
    }

    suspend fun setPaillierSecretKey(sk: PaillierCipher.SecretKey) {
        store.update { it.copy(paillierLambda = sk.lambda.toBase64(), paillierMu = sk.mu.toBase64()) }
    }

    suspend fun getPaillierSecretKey(): PaillierCipher.SecretKey? {
        val data = store.read()
        val lambda = data.paillierLambda?.toBigIntegerFromBase64() ?: return null
        val mu = data.paillierMu?.toBigIntegerFromBase64() ?: return null
        return PaillierCipher.SecretKey(lambda = lambda, mu = mu)
    }

    // ── Key Shares ──

    suspend fun setKeyShare(accountIndex: Int, share: BigInteger) {
        updateShare(accountIndex) { it.copy(share = share.toBase64()) }
    }

    suspend fun getKeyShare(accountIndex: Int): BigInteger? =
        findShare(accountIndex)?.share?.toBigIntegerFromBase64()

    // ── Joint Public Keys ──

    suspend fun setJointPublicKey(accountIndex: Int, q: ECPoint) {
        updateShare(accountIndex) { it.copy(jointPublicKey = q.toBase64()) }
    }

    suspend fun getJointPublicKey(accountIndex: Int): ECPoint? =
        findShare(accountIndex)?.jointPublicKey?.toECPointFromBase64()

    // ── Ckeys (Signer) ──

    suspend fun setCkey(accountIndex: Int, ckey: BigInteger) {
        updateShare(accountIndex) { it.copy(ckey = ckey.toBase64()) }
    }

    suspend fun getCkey(accountIndex: Int): BigInteger? =
        findShare(accountIndex)?.ckey?.toBigIntegerFromBase64()

    // ── Signer's Paillier Public Key ──

    suspend fun setSignerPaillierPk(pk: PaillierCipher.PublicKey) {
        store.update { it.copy(signerPaillierN = pk.n.toBase64(), signerPaillierG = pk.g.toBase64()) }
    }

    suspend fun getSignerPaillierPk(): PaillierCipher.PublicKey? {
        val data = store.read()
        return buildPaillierPk(data.signerPaillierN, data.signerPaillierG)
    }

    suspend fun deleteAll() { store.delete() }

    // ── Helpers ──

    // #11: Single helper for both getPaillierPublicKey and getSignerPaillierPk
    private fun buildPaillierPk(nB64: String?, gB64: String?): PaillierCipher.PublicKey? {
        val n = nB64?.toBigIntegerFromBase64() ?: return null
        val g = gB64?.toBigIntegerFromBase64() ?: return null
        return PaillierCipher.PublicKey(n = n, nSquared = n.multiply(n), g = g)
    }

    private suspend fun findShare(accountIndex: Int): StoredKeyShare? =
        store.read().shares.find { it.accountIndex == accountIndex }

    private suspend fun updateShare(accountIndex: Int, transform: (StoredKeyShare) -> StoredKeyShare) {
        store.update { data ->
            val existing = data.shares.find { it.accountIndex == accountIndex }
            val updated = transform(existing ?: StoredKeyShare(accountIndex))
            data.copy(shares = data.shares.filter { it.accountIndex != accountIndex } + updated)
        }
    }
}
