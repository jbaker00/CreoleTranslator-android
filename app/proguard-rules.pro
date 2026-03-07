# Add project specific ProGuard rules here.

# Keep all model/data classes used with Gson serialization
-keep class com.creole.translator.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# Google Play Services / AdMob
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep BuildConfig
-keep class com.creole.translator.BuildConfig { *; }
