package io.raccoonwallet.app.core.storage

import javax.crypto.Cipher

/**
 * AES-256-GCM encryption/decryption backed by Android Keystore.
 * Supports both time-window auth (direct encrypt/decrypt) and
 * per-operation auth (Cipher factory + pre-authenticated Cipher).
 */
class KeystoreAead(val alias: String) {

    fun encrypt(plaintext: ByteArray): ByteArray =
        KeystoreCipher.encrypt(alias, plaintext)

    fun decrypt(ciphertext: ByteArray): ByteArray =
        KeystoreCipher.decrypt(alias, ciphertext)

    // ── CryptoObject support ──

    fun createEncryptCipher(): Cipher =
        KeystoreCipher.createEncryptCipher(alias)

    fun createDecryptCipher(nonce: ByteArray): Cipher =
        KeystoreCipher.createDecryptCipher(alias, nonce)

    fun encryptWithCipher(cipher: Cipher, plaintext: ByteArray): ByteArray =
        KeystoreCipher.encryptWithCipher(alias, cipher, plaintext)

    fun decryptWithCipher(cipher: Cipher, ciphertext: ByteArray): ByteArray =
        KeystoreCipher.decryptWithCipher(alias, cipher, ciphertext)
}
