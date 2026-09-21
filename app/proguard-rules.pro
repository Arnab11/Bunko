# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# kotlinx.serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn org.slf4j.**
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.junrar.**
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Bunko Offline & Engine Models for Serialization
-keep class com.bunko.reader.offline.** { *; }
-keepclassmembers enum com.bunko.reader.offline.** { *; }
-keep class com.bunko.reader.engine.** { *; }

# Compose & Reader stability
-dontoptimize
-dontwarn androidx.compose.**
-keepclassmembers class * extends androidx.compose.runtime.RecomposeScopeImpl { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod