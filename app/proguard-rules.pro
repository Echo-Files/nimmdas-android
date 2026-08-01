# Retrofit & Gson
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes *Annotation*

# Keep Models for Gson
-keep class at.nimmdas.app.data.model.** { *; }
-keepclassmembers class at.nimmdas.app.data.model.** { *; }

# Keep Retrofit interface methods untouched to preserve Generic Signatures
-keep interface at.nimmdas.app.data.api.** { *; }
-keepclassmembers interface at.nimmdas.app.data.api.** { *; }

# CRITICAL FIX for java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType
# Keeps the generic signature of Continuation<T> from being erased by R8
-keep,allowobfuscation,allowshrinking interface kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# R8 full mode (default from AGP 8) strips generic signatures from types that are not
# themselves kept. Without these, every call fails with
# "Response must include generic type (e.g., Response<String>)".
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Gson
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
