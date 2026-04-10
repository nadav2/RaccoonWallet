package io.raccoonwallet.app.core.storage

/**
 * AES-256-GCM encryption/decryption backed by Android Keystore.
 * Implements a simple Aead interface (no Tink dependency).
 */
class KeystoreAead(private val alias: String) {

    fun encrypt(plaintext: ByteArray): ByteArray {
        return KeystoreCipher.encrypt(alias, plaintext)
    }

    fun decrypt(ciphertext: ByteArray): ByteArray {
        return KeystoreCipher.decrypt(alias, ciphertext)
    }
}
