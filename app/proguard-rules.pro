# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Lottie
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# Keep IME service
-keep class com.snowrealm.yukiboard.service.YukiBoardIMEService { *; }
