# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,Deprecated,*Annotation*,*Element*

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- FIX: Missing classes (Google Error Prone) ---
-dontwarn com.google.errorprone.annotations.**

# --- OkHttp ProGuard Rules ---
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    private java.lang.String[] publicSuffixList;
    private java.lang.String[] publicSuffixExceptionList;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# --- Retrofit ProGuard Rules ---
-keepattributes Signature, InnerClasses, EnclosingMethod, Annotation
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepclassmembernames class * {
    @retrofit2.http.* <methods>;
}

# --- Moshi ProGuard Rules ---
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
# Keep codegen-generated adapters
-keep class *JsonAdapter { *; }
-keep class *JsonAdapter$* { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json class *;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonQualifier class *;
}

# --- Keep our API DTOs and Domain Models ---
-keep class com.textsocial.app.data.model.** { *; }
-keep class com.textsocial.app.domain.model.** { *; }
-keepclassmembers class com.textsocial.app.data.model.** { *; }
-keepclassmembers class com.textsocial.app.domain.model.** { *; }

# --- Keep MainActivity, App, and Multidex ---
-keep class com.textsocial.app.MainActivity { *; }
-keep class com.textsocial.app.App { *; }
-keep class androidx.multidex.** { *; }

# --- Jetpack Compose / Kotlin Serialization / Lifecycle ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**