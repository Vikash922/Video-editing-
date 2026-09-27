# VidoPRO Proguard Rules

# Preserve all native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# FFmpegKit
-keep class com.antonkarpenko.ffmpegkit.** { *; }
-dontwarn com.antonkarpenko.ffmpegkit.**

# ExoPlayer Media3
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Native JNI Bridge & Media Subsystem
-keep class com.vikash.vidopro.jni.** { *; }
-keep class com.vikash.vidopro.core.media.** { *; }

# AboutLibraries
-keep class com.mikepenz.aboutlibraries.** { *; }

# Gson & Serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retain stacktrace line numbers for diagnostics
-keepattributes SourceFile,LineNumberTable