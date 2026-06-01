# Keep Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class * implements androidx.compose.runtime.SaveableStateHolder { *; }
-dontwarn androidx.compose.**

# Keep Kotlin serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * implements java.io.Serializable { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep JSON
-keep class org.json.** { *; }

# Keep our app classes
-keep class com.llucs.nexusai.** { *; }
