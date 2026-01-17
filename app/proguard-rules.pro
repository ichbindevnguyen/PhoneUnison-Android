# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep WebSocket classes
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.phoneunison.mobile.protocol.** { *; }

# Keep our data classes
-keep class com.phoneunison.mobile.data.** { *; }

# Keep NanoHTTPD classes
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**
