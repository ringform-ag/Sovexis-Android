# Sovexis ProGuard Rules

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Tink
-keep class com.google.crypto.tink.** { *; }

# Spongy Castle
-keep class org.spongycastle.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.sovexis.mobile.data.model.** { *; }
-keep class com.sovexis.mobile.data.remote.dto.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ZXing
-keep class com.google.zxing.** { *; }
