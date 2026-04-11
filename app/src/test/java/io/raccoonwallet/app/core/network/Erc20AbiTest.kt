package io.raccoonwallet.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

class Erc20AbiTest {

    @Test
    fun `decodeTransfer roundtrips with encodeTransfer`() {
        val to = "0xAbCdEf0123456789AbCdEf0123456789AbCdEf01"
        val amount = BigInteger("1000000") // 1 USDC (6 decimals)

        val encoded = Erc20Abi.encodeTransfer(to, amount)
        val decoded = Erc20Abi.decodeTransfer(encoded)

        assertNotNull(decoded)
        assertEquals(to.lowercase(), decoded!!.first.lowercase())
        assertEquals(amount, decoded.second)
    }

    @Test
    fun `decodeTransfer returns null for native transfer`() {
        assertNull(Erc20Abi.decodeTransfer("0x"))
    }

    @Test
    fun `decodeTransfer returns null for short data`() {
        assertNull(Erc20Abi.decodeTransfer("0xabcd"))
    }

    @Test
    fun `decodeTransfer returns null for wrong selector`() {
        // approve(address,uint256) selector instead of transfer
        val wrongSelector = "0x095ea7b3" +
            "000000000000000000000000abcdef0123456789abcdef0123456789abcdef01" +
            "00000000000000000000000000000000000000000000000000000000000f4240"
        assertNull(Erc20Abi.decodeTransfer(wrongSelector))
    }

    @Test
    fun `decodeTransfer handles large amounts`() {
        val to = "0x1234567890123456789012345678901234567890"
        val amount = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935") // uint256 max

        val encoded = Erc20Abi.encodeTransfer(to, amount)
        val decoded = Erc20Abi.decodeTransfer(encoded)

        assertNotNull(decoded)
        assertEquals(amount, decoded!!.second)
    }
}
