package io.raccoonwallet.app.core.transport

import io.raccoonwallet.app.core.model.TxDisplayData
import kotlinx.serialization.Serializable

@Serializable
sealed class TransportMessage {
    @Serializable
    data class DkgRound2(
        val q1Points: List<ByteArray>,
        val ckeys: List<ByteArray>
    ) : TransportMessage() {
        override fun equals(other: Any?): Boolean =
            other is DkgRound2 &&
                q1Points.size == other.q1Points.size &&
                ckeys.size == other.ckeys.size &&
                q1Points.zip(other.q1Points).all { (a, b) -> a.contentEquals(b) } &&
                ckeys.zip(other.ckeys).all { (a, b) -> a.contentEquals(b) }

        override fun hashCode(): Int {
            var result = 1
            q1Points.forEach { result = 31 * result + it.contentHashCode() }
            ckeys.forEach { result = 31 * result + it.contentHashCode() }
            return result
        }
    }

    @Serializable
    data class DkgFinalize(val ack: Boolean) : TransportMessage()

    @Serializable
    data class SignRequest(
        val sessionId: String,
        val accountIndex: Int,
        val chainId: Long,
        val txHash: ByteArray,
        val r1Point: ByteArray,
        val displayData: TxDisplayData,
        val nonce: Long = 0,
        val maxPriorityFeePerGas: String = "0",
        val maxFeePerGas: String = "0",
        val gasLimit: Long = 21_000,
        val to: String = "",
        val valueWei: String = "0",
        val txData: String = "0x"
    ) : TransportMessage() {
        override fun equals(other: Any?) =
            other is SignRequest && sessionId == other.sessionId
        override fun hashCode() = sessionId.hashCode()
    }

    @Serializable
    data class SignResponse(
        val sessionId: String,
        val r2Point: ByteArray,
        val cPartial: ByteArray,
        val signerR: ByteArray,
        val approved: Boolean
    ) : TransportMessage() {
        override fun equals(other: Any?) =
            other is SignResponse &&
                sessionId == other.sessionId &&
                approved == other.approved &&
                r2Point.contentEquals(other.r2Point) &&
                cPartial.contentEquals(other.cPartial) &&
                signerR.contentEquals(other.signerR)

        override fun hashCode(): Int {
            var result = sessionId.hashCode()
            result = 31 * result + r2Point.contentHashCode()
            result = 31 * result + cPartial.contentHashCode()
            result = 31 * result + signerR.contentHashCode()
            result = 31 * result + approved.hashCode()
            return result
        }
    }

    @Serializable
    data class Error(val code: Int, val message: String) : TransportMessage()
}
