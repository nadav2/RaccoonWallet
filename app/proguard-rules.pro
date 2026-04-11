# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- bitcoinj (BIP32/BIP39) ---
# MnemonicCode.INSTANCE loads the English wordlist from a classpath resource
# in a static initializer. R8 strips this, causing INSTANCE to be null.
-keep class org.bitcoinj.crypto.** { *; }

# --- BouncyCastle ---
# CustomNamedCurves.getByName() uses reflection to locate curve definitions.
# EC math, Keccak, and Paillier all depend on BouncyCastle internals.
-keep class org.bouncycastle.** { *; }
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