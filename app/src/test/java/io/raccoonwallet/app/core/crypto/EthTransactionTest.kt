package io.raccoonwallet.app.core.crypto

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class EthTransactionTest {

    private fun buildSimpleTx() = EthSigner.buildTransaction(
        nonce = BigInteger.ZERO,
        chainId = 1L,
        maxPriorityFeePerGas = BigInteger.valueOf(1_000_000_000L), // 1 gwei
        maxFeePerGas = BigInteger.valueOf(20_000_000_000L), // 20 gwei
        gasLimit = BigInteger.valueOf(21000),
        to = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf",
        value = BigInteger.valueOf(1_000_000_000_000_000_000L) // 1 ETH
    )

    @Test
    fun `signingHash produces 32 bytes`() {
        val tx = buildSimpleTx()
        val hash = tx.signingHash()
        assertEquals(32, hash.size)
    }

    @Test
    fun `signingHash is deterministic`() {
        val tx = buildSimpleTx()
        assertArrayEquals(tx.signingHash(), tx.signingHash())
    }

    @Test
    fun `encodeSignedHex starts with 0x02`() {
        val tx = buildSimpleTx()
        val sig = LindellSign.EcdsaSignature(
            r = BigInteger.valueOf(123456),
            s = BigInteger.valueOf(654321),
            v = 27
        )
        val hex = tx.encodeSignedHex(sig)
        assertTrue("Must start with 0x02", hex.startsWith("0x02"))
    }

    @Test
    fun `zero-value transaction encodes without error`() {
        val tx = EthSigner.buildTransaction(
            nonce = BigInteger.ZERO,
            chainId = 1L,
            maxPriorityFeePerGas = BigInteger.ONE,
            maxFeePerGas = BigInteger.ONE,
            gasLimit = BigInteger.valueOf(21000),
            to = "0x0000000000000000000000000000000000000001",
            value = BigInteger.ZERO
        )
        val hash = tx.signingHash()
        assertEquals(32, hash.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects maxPriorityFee greater than maxFee`() {
        EthSigner.buildTransaction(
            nonce = BigInteger.ZERO,
            chainId = 1L,
            maxPriorityFeePerGas = BigInteger.valueOf(100),
            maxFeePerGas = BigInteger.valueOf(50),
            gasLimit = BigInteger.valueOf(21000),
            to = "0x0000000000000000000000000000000000000001",
            value = BigInteger.ZERO
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative value`() {
        EthSigner.buildTransaction(
            nonce = BigInteger.ZERO,
            chainId = 1L,
            maxPriorityFeePerGas = BigInteger.ONE,
            maxFeePerGas = BigInteger.ONE,
            gasLimit = BigInteger.valueOf(21000),
            to = "0x0000000000000000000000000000000000000001",
            value = BigInteger.valueOf(-1)
        )
    }

    @Test
    fun `different chainIds produce different hashes`() {
        val tx1 = EthSigner.buildTransaction(
            nonce = BigInteger.ZERO, chainId = 1L,
            maxPriorityFeePerGas = BigInteger.ONE, maxFeePerGas = BigInteger.ONE,
            gasLimit = BigInteger.valueOf(21000),
            to = "0x0000000000000000000000000000000000000001",
            value = BigInteger.ZERO
        )
        val tx137 = EthSigner.buildTransaction(
            nonce = BigInteger.ZERO, chainId = 137L,
            maxPriorityFeePerGas = BigInteger.ONE, maxFeePerGas = BigInteger.ONE,
            gasLimit = BigInteger.valueOf(21000),
            to = "0x0000000000000000000000000000000000000001",
            value = BigInteger.ZERO
        )
        assertFalse(tx1.signingHash().contentEquals(tx137.signingHash()))
    }
}
