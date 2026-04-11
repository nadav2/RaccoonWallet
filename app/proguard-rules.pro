# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- bitcoinj (BIP32/BIP39) ---
# MnemonicCode.INSTANCE loads the English wordlist from a classpath resource
# in a static initializer. R8 strips this, causing INSTANCE to be null.
-keep class org.bitcoinj.crypto.MnemonicCode { *; }
-keep class org.bitcoinj.crypto.HDKeyDerivation { *; }
-keep class org.bitcoinj.crypto.ChildNumber { *; }

# --- BouncyCastle ---
# CustomNamedCurves.getByName() uses anonymous inner classes as curve holders.
# R8 cannot trace the string-based lookup so all holders must be kept.
-keep class org.bouncycastle.crypto.ec.CustomNamedCurves { *; }
-keep class org.bouncycastle.crypto.ec.CustomNamedCurves$* { *; }
# secp256k1 curve implementation classes (field arithmetic, point ops).
-keep class org.bouncycastle.math.ec.custom.sec.SecP256K1** { *; }
# EC point internals — ECPoint subclasses, field elements, multipliers.
-keep class org.bouncycastle.math.ec.** { *; }
# Directly referenced classes.
-keep class org.bouncycastle.crypto.digests.KeccakDigest { *; }
-keep class org.bouncycastle.crypto.params.ECDomainParameters { *; }
-keep class org.bouncycastle.asn1.x9.X9ECParameters { *; }
-dontwarn org.bouncycastle.**

# --- kotlinx.serialization ---
# Keep serializer companions for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class io.raccoonwallet.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Debug stack traces ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile