package io.raccoonwallet.app.core.crypto

import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.HDKeyDerivation
import java.math.BigInteger

/**
 * BIP32 HD key derivation for secp256k1.
 * Delegates to bitcoinj's battle-tested implementation.
 * Derives Ethereum accounts via path m/44'/60'/0'/0/i.
 */
object Bip32 {

    /**
     * Derive 20 Ethereum private keys via BIP44 path: m/44'/60'/0'/0/i
     */
    fun deriveEthAccounts(seed: ByteArray, count: Int = 20): List<BigInteger> {
        val master = HDKeyDerivation.createMasterPrivateKey(seed)

        // m/44'
        val purpose = HDKeyDerivation.deriveChildKey(master, ChildNumber(44, true))
        // m/44'/60'
        val coinType = HDKeyDerivation.deriveChildKey(purpose, ChildNumber(60, true))
        // m/44'/60'/0'
        val account = HDKeyDerivation.deriveChildKey(coinType, ChildNumber(0, true))
        // m/44'/60'/0'/0
        val change = HDKeyDerivation.deriveChildKey(account, ChildNumber(0, false))

        // m/44'/60'/0'/0/i for i in 0..count-1
        return (0 until count).map { i ->
            val child = HDKeyDerivation.deriveChildKey(change, ChildNumber(i, false))
            child.privKey
        }
    }
}
