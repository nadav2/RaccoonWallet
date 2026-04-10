package io.raccoonwallet.app.core.crypto

import org.bitcoinj.crypto.MnemonicCode
import java.security.SecureRandom

/**
 * BIP39 mnemonic generation and seed derivation.
 * Delegates to bitcoinj's battle-tested implementation.
 */
object Bip39 {

    /**
     * Generate a new 12-word mnemonic from 128 bits of secure randomness.
     */
    fun generateMnemonic(): List<String> {
        val entropy = ByteArray(16) // 128 bits → 12 words
        SecureRandom().nextBytes(entropy)
        return MnemonicCode.INSTANCE.toMnemonic(entropy)
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
            words.size == 12
        } catch (_: Exception) {
            false
        }
    }
}
