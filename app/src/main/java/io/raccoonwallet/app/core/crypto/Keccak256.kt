package io.raccoonwallet.app.core.crypto

import org.bouncycastle.crypto.digests.KeccakDigest

object Keccak256 {
    fun hash(data: ByteArray): ByteArray {
        val digest = KeccakDigest(256)
        digest.update(data, 0, data.size)
        val result = ByteArray(32)
        digest.doFinal(result, 0)
        return result
    }
}
