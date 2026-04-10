package io.raccoonwallet.app.core.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionCryptoTest {

    @Test
    fun `handshake establishes session on both sides`() {
        val alice = SessionCrypto()
        val bob = SessionCrypto()

        val alicePayload = alice.initiateHandshake()
        val bobPayload = bob.initiateHandshake()

        alice.completeHandshake(bobPayload)
        bob.completeHandshake(alicePayload)

        assertTrue(alice.isEstablished())
        assertTrue(bob.isEstablished())
    }

    @Test
    fun `encrypt decrypt roundtrip`() {
        val (alice, bob) = handshakePair()

        val plaintext = "Hello, threshold ECDSA!".toByteArray()
        val encrypted = alice.encrypt(plaintext)

        // Encrypted should be different from plaintext
        assertFalse(plaintext.contentEquals(encrypted))
        // Should have 12-byte nonce prefix
        assertTrue(encrypted.size > plaintext.size + 12)

        val decrypted = bob.decrypt(encrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `multiple messages in sequence`() {
        val (alice, bob) = handshakePair()

        for (i in 0 until 5) {
            val msg = "Message $i".toByteArray()
            val encrypted = alice.encrypt(msg)
            val decrypted = bob.decrypt(encrypted)
            assertArrayEquals(msg, decrypted)
        }
    }

    @Test
    fun `bidirectional communication`() {
        val (alice, bob) = handshakePair()

        // Alice -> Bob
        val msg1 = "From Alice".toByteArray()
        val dec1 = bob.decrypt(alice.encrypt(msg1))
        assertArrayEquals(msg1, dec1)

        // Bob -> Alice
        val msg2 = "From Bob".toByteArray()
        val dec2 = alice.decrypt(bob.encrypt(msg2))
        assertArrayEquals(msg2, dec2)
    }

    @Test
    fun `replay attack detected - same ciphertext decrypted twice fails`() {
        val (alice, bob) = handshakePair()

        val encrypted = alice.encrypt("secret".toByteArray())
        bob.decrypt(encrypted) // First decrypt succeeds

        try {
            bob.decrypt(encrypted) // Replay should fail
            fail("Expected exception for replay attack")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Nonce mismatch") == true ||
                e.message?.contains("replay") == true)
        }
    }

    @Test
    fun `out-of-order nonce rejected`() {
        val (alice, bob) = handshakePair()

        alice.encrypt("first".toByteArray())  // counter=0
        val enc2 = alice.encrypt("second".toByteArray())  // counter=1

        // Decrypt second message first — should fail because Bob expects counter=0
        try {
            bob.decrypt(enc2)
            fail("Expected exception for out-of-order nonce")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Nonce mismatch") == true)
        }
    }

    @Test
    fun `reset clears session state`() {
        val (alice, _) = handshakePair()
        assertTrue(alice.isEstablished())

        alice.reset()
        assertFalse(alice.isEstablished())
    }

    @Test(expected = IllegalStateException::class)
    fun `encrypt without session throws`() {
        val crypto = SessionCrypto()
        crypto.encrypt("data".toByteArray())
    }

    @Test(expected = IllegalStateException::class)
    fun `decrypt without session throws`() {
        val crypto = SessionCrypto()
        crypto.decrypt(ByteArray(20))
    }

    @Test
    fun `large payload encrypt decrypt`() {
        val (alice, bob) = handshakePair()

        val largePayload = ByteArray(10000) { (it % 256).toByte() }
        val encrypted = alice.encrypt(largePayload)
        val decrypted = bob.decrypt(encrypted)
        assertArrayEquals(largePayload, decrypted)
    }

    @Test
    fun `empty payload encrypt decrypt`() {
        val (alice, bob) = handshakePair()

        val encrypted = alice.encrypt(byteArrayOf())
        val decrypted = bob.decrypt(encrypted)
        assertEquals(0, decrypted.size)
    }

    private fun handshakePair(): Pair<SessionCrypto, SessionCrypto> {
        val alice = SessionCrypto()
        val bob = SessionCrypto()
        val alicePayload = alice.initiateHandshake()
        val bobPayload = bob.initiateHandshake()
        alice.completeHandshake(bobPayload)
        bob.completeHandshake(alicePayload)
        return Pair(alice, bob)
    }
}
