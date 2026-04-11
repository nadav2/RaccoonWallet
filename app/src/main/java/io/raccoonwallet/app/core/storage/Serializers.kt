package io.raccoonwallet.app.core.storage

import android.util.Base64
import io.raccoonwallet.app.core.crypto.Secp256k1
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

/**
 * Base64 conversion helpers for BigInteger and ECPoint.
 * Used by transport and crypto layers.
 */
object Serializers {
    fun BigInteger.toBase64(): String =
        Base64.encodeToString(this.toByteArray(), Base64.NO_WRAP)

    fun String.toBigIntegerFromBase64(): BigInteger =
        BigInteger(1, Base64.decode(this, Base64.NO_WRAP))

    fun ECPoint.toBase64(): String =
        Base64.encodeToString(Secp256k1.compressPoint(this), Base64.NO_WRAP)

    fun String.toECPointFromBase64(): ECPoint =
        Secp256k1.decompressPoint(Base64.decode(this, Base64.NO_WRAP))
}
