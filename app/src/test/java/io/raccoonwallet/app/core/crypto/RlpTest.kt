package io.raccoonwallet.app.core.crypto

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class RlpTest {

    @Test
    fun `encode empty bytes`() {
        val encoded = Rlp.encodeBytes(byteArrayOf())
        assertArrayEquals(byteArrayOf(0x80.toByte()), encoded)
    }

    @Test
    fun `encode single byte below 0x80 is identity`() {
        val encoded = Rlp.encodeBytes(byteArrayOf(0x0F))
        assertArrayEquals(byteArrayOf(0x0F), encoded)
    }

    @Test
    fun `encode single byte 0x7F is identity`() {
        val encoded = Rlp.encodeBytes(byteArrayOf(0x7F))
        assertArrayEquals(byteArrayOf(0x7F), encoded)
    }

    @Test
    fun `encode single byte 0x80 gets prefix`() {
        val encoded = Rlp.encodeBytes(byteArrayOf(0x80.toByte()))
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x80.toByte()), encoded)
    }

    @Test
    fun `encode short string dog`() {
        // "dog" = 0x646F67, length 3 → prefix 0x83
        val encoded = Rlp.encodeBytes("dog".toByteArray())
        assertArrayEquals(byteArrayOf(0x83.toByte(), 0x64, 0x6F, 0x67), encoded)
    }

    @Test
    fun `encode empty list`() {
        val encoded = Rlp.encodeList(emptyList())
        assertArrayEquals(byteArrayOf(0xC0.toByte()), encoded)
    }

    @Test
    fun `encodeBigInt zero produces empty bytes`() {
        val item = Rlp.encodeBigInt(BigInteger.ZERO)
        assertTrue(item is Rlp.Item.Bytes)
        assertArrayEquals(byteArrayOf(), (item as Rlp.Item.Bytes).data)
    }

    @Test
    fun `encodeBigInt removes leading zeros`() {
        // 256 = 0x0100, should be [0x01, 0x00] not [0x00, 0x01, 0x00]
        val item = Rlp.encodeBigInt(BigInteger.valueOf(256))
        assertTrue(item is Rlp.Item.Bytes)
        assertArrayEquals(byteArrayOf(0x01, 0x00), (item as Rlp.Item.Bytes).data)
    }

    @Test
    fun `encodeLong zero produces empty bytes`() {
        val item = Rlp.encodeLong(0)
        assertTrue(item is Rlp.Item.Bytes)
        assertArrayEquals(byteArrayOf(), (item as Rlp.Item.Bytes).data)
    }

    @Test
    fun `encodeAddress validates 20-byte length`() {
        val address = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf"
        val item = Rlp.encodeAddress(address)
        assertTrue(item is Rlp.Item.Bytes)
        assertEquals(20, (item as Rlp.Item.Bytes).data.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encodeAddress rejects short address`() {
        Rlp.encodeAddress("0x1234")
    }

    @Test
    fun `encode nested list`() {
        // [[]] → 0xC1C0
        val inner = Rlp.Item.RlpList(emptyList())
        val outer = Rlp.Item.RlpList(listOf(inner))
        val encoded = Rlp.encode(outer)
        assertArrayEquals(byteArrayOf(0xC1.toByte(), 0xC0.toByte()), encoded)
    }
}
