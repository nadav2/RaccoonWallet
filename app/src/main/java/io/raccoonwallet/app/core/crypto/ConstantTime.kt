package io.raccoonwallet.app.core.crypto

object ConstantTime {
    fun equals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    fun wipe(data: ByteArray) {
        data.fill(0)
    }
}
