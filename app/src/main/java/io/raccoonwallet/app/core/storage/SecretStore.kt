package io.raccoonwallet.app.core.storage

import javax.crypto.Cipher

class SecretStore(private val store: EncryptedJsonStore<SecretStoreData>) {

    suspend fun readEncryptedBytes(): ByteArray? = store.readEncryptedBytes()

    suspend fun readData(cipher: Cipher? = null, encryptedBytes: ByteArray? = null): SecretStoreData =
        if (cipher != null) store.readWithCipher(cipher, encryptedBytes) else store.read()

    suspend fun writeAll(cipher: Cipher? = null, transform: (SecretStoreData) -> SecretStoreData): SecretStoreData =
        if (cipher != null) store.updateWithCipher(cipher, transform) else store.update(transform)

    suspend fun deleteAll() { store.delete() }
}
