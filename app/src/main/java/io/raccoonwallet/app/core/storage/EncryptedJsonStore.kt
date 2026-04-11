package io.raccoonwallet.app.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.crypto.Cipher

/**
 * Encrypted JSON file store backed by Android Keystore AES-256-GCM.
 *
 * - Thread-safe via Mutex
 * - All I/O on Dispatchers.IO
 * - Atomic writes via temp-file-then-rename
 * - In-memory cache for fast repeated reads
 * - Throws [StoreDecryptionException] on decryption failure (distinct from missing file)
 */
class EncryptedJsonStore<T>(
    private val file: File,
    private val aead: KeystoreAead,
    private val serializer: KSerializer<T>,
    private val defaultValue: T
) {
    private val json = Json { ignoreUnknownKeys = false }
    private val mutex = Mutex()
    private var cached: T? = null

    suspend fun read(): T = mutex.withLock {
        cached?.let { return it }
        val value = readFromDisk()
        cached = value
        value
    }

    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val current = cached ?: readFromDisk()
        val updated = transform(current)
        atomicWrite(updated)
        cached = updated
        updated
    }

    // ── CryptoObject support (per-operation auth) ──

    suspend fun readEncryptedBytes(): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val bytes = file.readBytes()
        if (bytes.isEmpty()) null else bytes
    }

    suspend fun readWithCipher(cipher: Cipher, encryptedBytes: ByteArray? = null): T = mutex.withLock {
        cached?.let { return it }
        val value = if (encryptedBytes != null) {
            decryptFromBytes(cipher, encryptedBytes)
        } else {
            decryptFromDisk(cipher)
        }
        cached = value
        value
    }

    suspend fun updateWithCipher(encryptCipher: Cipher, transform: (T) -> T): T = mutex.withLock {
        val current = cached ?: defaultValue
        val updated = transform(current)
        atomicWriteWithCipher(updated, encryptCipher)
        cached = updated
        updated
    }

    private fun decryptFromBytes(cipher: Cipher, encrypted: ByteArray): T {
        return try {
            val decrypted = aead.decryptWithCipher(cipher, encrypted)
            json.decodeFromString(serializer, String(decrypted))
        } catch (e: java.security.GeneralSecurityException) {
            throw StoreDecryptionException(
                "Failed to decrypt ${file.name}. Internal: ${e.message}", e
            )
        }
    }

    private suspend fun decryptFromDisk(cipher: Cipher): T = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext defaultValue
        val encrypted = file.readBytes()
        if (encrypted.isEmpty()) return@withContext defaultValue
        try {
            val decrypted = aead.decryptWithCipher(cipher, encrypted)
            json.decodeFromString(serializer, String(decrypted))
        } catch (e: java.security.GeneralSecurityException) {
            throw StoreDecryptionException(
                "Failed to decrypt ${file.name}. Internal: ${e.message}", e
            )
        }
    }

    private suspend fun atomicWriteWithCipher(value: T, cipher: Cipher) = withContext(Dispatchers.IO) {
        val plaintext = json.encodeToString(serializer, value).toByteArray()
        val encrypted = aead.encryptWithCipher(cipher, plaintext)
        val tmp = File(file.parent, "${file.name}.tmp")
        tmp.writeBytes(encrypted)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw java.io.IOException("Atomic rename failed for ${file.name}")
        }
    }

    suspend fun delete() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (file.exists()) file.delete()
            File(file.parent, "${file.name}.tmp").delete()
        }
        cached = defaultValue
    }

    private suspend fun readFromDisk(): T = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext defaultValue
        val encrypted = file.readBytes()
        if (encrypted.isEmpty()) return@withContext defaultValue
        try {
            val decrypted = aead.decrypt(encrypted)
            json.decodeFromString(serializer, String(decrypted))
        } catch (e: java.security.GeneralSecurityException) {
            throw StoreDecryptionException(
                "Failed to decrypt ${file.name}. Key may have been invalidated " +
                    "(e.g., biometric enrollment changed). Restore from recovery phrase. Internal: ${e.message}", e
            )
        }
    }

    private suspend fun atomicWrite(value: T) = withContext(Dispatchers.IO) {
        val plaintext = json.encodeToString(serializer, value).toByteArray()
        val encrypted = aead.encrypt(plaintext)
        val tmp = File(file.parent, "${file.name}.tmp")
        tmp.writeBytes(encrypted)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw java.io.IOException("Atomic rename failed for ${file.name}")
        }
    }
}

class StoreDecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
