# Add project specific ProGuard rules here.

# Google Play Services — auth, sign-in, common (must NOT be stripped in release builds)
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.signin.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.android.gms.**

# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.apis.** { *; }
-keep class com.google.http.client.** { *; }
-keep interface com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.apis.**

# Apache HTTP
-dontwarn org.apache.http.**
-dontwarn android.net.http.AndroidHttpClient

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep model classes
-keep class com.rotiv3.fitalarm.data.model.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
