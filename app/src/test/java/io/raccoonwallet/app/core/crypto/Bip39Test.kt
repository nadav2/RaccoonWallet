package io.raccoonwallet.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bip39Test {

    @Test
    fun `generateMnemonic returns a valid 12-word phrase`() {
        val words = Bip39.generateMnemonic()

        assertEquals(12, words.size)
        assertTrue(Bip39.validateMnemonic(words))
    }

    @Test
    fun `generateMnemonic with extra entropy returns a valid 12-word phrase`() {
        val extraEntropy = ByteArray(32) { it.toByte() }

        val words = Bip39.generateMnemonic(extraEntropy)

        assertEquals(12, words.size)
        assertTrue(Bip39.validateMnemonic(words))
    }

    @Test
    fun `mixEntropy changes output when extra entropy changes`() {
        val baseEntropy = ByteArray(16) { it.toByte() }
        val extraEntropyA = ByteArray(32) { (it + 1).toByte() }
        val extraEntropyB = ByteArray(32) { (it + 17).toByte() }

        val mixedA = Bip39.mixEntropy(baseEntropy, extraEntropyA)
        val mixedB = Bip39.mixEntropy(baseEntropy, extraEntropyB)

        assertEquals(16, mixedA.size)
        assertEquals(16, mixedB.size)
        assertFalse(mixedA.contentEquals(baseEntropy))
        assertFalse(mixedA.contentEquals(mixedB))
    }
}
