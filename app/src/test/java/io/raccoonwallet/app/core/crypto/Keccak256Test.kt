package io.raccoonwallet.app.core.crypto

import org.junit.Assert.*
import org.junit.Test

class Keccak256Test {

    @Test
    fun `empty input produces known hash`() {
        val hash = Keccak256.hash(byteArrayOf())
        val expected = Hex.decode("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")
        assertArrayEquals(expected, hash)
    }

    @Test
    fun `known input produces known hash`() {
        // keccak256("hello") — widely published test vector
        val hash = Keccak256.hash("hello".toByteArray())
        val expected = Hex.decode("1c8aff950685c2ed4bc3174f3472287b56d9517b9c948127319a09a7a36deac8")
        assertArrayEquals(expected, hash)
    }

    @Test
    fun `output is always 32 bytes`() {
        val inputs = listOf(
            byteArrayOf(),
            byteArrayOf(0),
            "short".toByteArray(),
            ByteArray(1000) { it.toByte() }
        )
        for (input in inputs) {
            assertEquals(32, Keccak256.hash(input).size)
        }
    }
}
