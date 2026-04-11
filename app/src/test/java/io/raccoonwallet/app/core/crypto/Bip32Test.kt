package io.raccoonwallet.app.core.crypto

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class Bip32Test {

    // Known test vector: the "abandon" mnemonic (all-zero entropy)
    private val abandonMnemonic = listOf(
        "abandon", "abandon", "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "abandon", "abandon", "about"
    )

    private fun testSeed(): ByteArray = Bip39.mnemonicToSeed(abandonMnemonic)

    @Test
    fun `derives correct number of accounts`() {
        val seed = testSeed()
        val keys = Bip32.deriveEthAccounts(seed, 8)
        assertEquals(8, keys.size)
    }

    @Test
    fun `derivation is deterministic`() {
        val seed = testSeed()
        val keys1 = Bip32.deriveEthAccounts(seed, 3)
        val keys2 = Bip32.deriveEthAccounts(seed, 3)
        assertEquals(keys1, keys2)
    }

    @Test
    fun `derived keys are in valid secp256k1 range`() {
        val seed = testSeed()
        val keys = Bip32.deriveEthAccounts(seed, 8)
        for ((i, key) in keys.withIndex()) {
            assertTrue("Key $i must be > 0", key > BigInteger.ZERO)
            assertTrue("Key $i must be < n", key < Secp256k1.N)
        }
    }

    @Test
    fun `known mnemonic produces known first address`() {
        // "abandon...about" mnemonic, BIP44 m/44'/60'/0'/0/0
        // Expected address from MetaMask / ethers.js: 0x9858EfFD232B4033E47d90003D41EC34EcaEda94
        val seed = testSeed()
        val keys = Bip32.deriveEthAccounts(seed, 1)
        val pubKey = Secp256k1.pointMultiplyG(keys[0])
        val address = Secp256k1.pointToEthAddress(pubKey)

        assertEquals(
            "First address for 'abandon' mnemonic must match known vector",
            "0x9858effd232b4033e47d90003d41ec34ecaeda94",
            address
        )
    }

    @Test
    fun `different accounts produce different keys`() {
        val seed = testSeed()
        val keys = Bip32.deriveEthAccounts(seed, 5)
        val uniqueKeys = keys.toSet()
        assertEquals("All derived keys must be unique", 5, uniqueKeys.size)
    }
}
