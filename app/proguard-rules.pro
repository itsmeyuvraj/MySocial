# ----------------------------------------------------------------------------------
# R8 / ProGuard Optimization & Shrinking Rules for MySocial
# ----------------------------------------------------------------------------------

# Code shrinking & optimization settings
-repackageclasses ''
-allowaccessmodification

# Kotlin Reflection & Metadata
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn kotlin.**

# Kotlinx Serialization
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.mysocial.mesh.model.** { *; }
-dontwarn kotlinx.serialization.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep class com.mysocial.mesh.data.** { *; }
-dontwarn androidx.room.paging.**

# Cryptography: BouncyCastle & AndroidX Security
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.jce.** { *; }
-keep class org.bouncycastle.jcajce.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn androidx.security.crypto.**

# OkHttp & Okio (In-app updates)
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ZXing QR Code Generation & Scanning
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Jetpack Compose UI
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Netty / Transport property files (ignored in packaging)
-dontwarn io.netty.**

