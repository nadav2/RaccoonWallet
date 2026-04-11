package io.raccoonwallet.app.core.crypto

import org.bitcoinj.crypto.MnemonicCode
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * BIP39 mnemonic generation and seed derivation.
 * Delegates to bitcoinj's battle-tested implementation.
 */
object Bip39 {

    /**
     * Generate a new 12-word mnemonic from 128 bits of secure randomness.
     */
    fun generateMnemonic(extraEntropy: ByteArray? = null): List<String> {
        val baseEntropy = ByteArray(16) // 128 bits → 12 words
        SecureRandom().nextBytes(baseEntropy)
        val supplementalEntropy = extraEntropy?.takeUnless { it.isEmpty() }
        val finalEntropy = supplementalEntropy?.let { mixEntropy(baseEntropy, it) } ?: baseEntropy
        return try {
            MnemonicCode.INSTANCE.toMnemonic(finalEntropy)
        } finally {
            baseEntropy.fill(0)
            if (finalEntropy !== baseEntropy) finalEntropy.fill(0)
            extraEntropy?.fill(0)
        }
    }

    internal fun mixEntropy(baseEntropy: ByteArray, extraEntropy: ByteArray): ByteArray {
        require(baseEntropy.size == 16) { "BIP39 entropy must be 128 bits" }
        require(extraEntropy.isNotEmpty()) { "Extra entropy must not be empty" }

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(baseEntropy)
        digest.update(extraEntropy)
        val mixed = digest.digest()
        return mixed.copyOf(baseEntropy.size).also { mixed.fill(0) }
    }

    /**
     * Convert mnemonic words to a 64-byte seed using PBKDF2-HMAC-SHA512.
     */
    fun mnemonicToSeed(words: List<String>, passphrase: String = ""): ByteArray {
        return MnemonicCode.toSeed(words, passphrase)
    }

    /**
     * Validate a mnemonic: 12 words, all in wordlist, checksum correct.
     */
    fun validateMnemonic(words: List<String>): Boolean {
        return try {
            MnemonicCode.INSTANCE.check(words)
            words.size == 12 || words.size == 24
        } catch (_: Exception) {
            false
        }
    }
}
