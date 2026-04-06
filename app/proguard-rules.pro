# Add project specific ProGuard rules here.

# ── Lottie ──
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ── IME Service ──
-keep class com.snowrealm.yukiboard.service.YukiBoardIMEService { *; }

# ── OkHttp ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ── kotlinx-serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.snowrealm.yukiboard.**$$serializer { *; }
-keepclassmembers class com.snowrealm.yukiboard.** {
    *** Companion;
}

# ── EncryptedSharedPreferences / Security Crypto ──
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── Kotlin Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── Firebase ──
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Google Play Services (Location) ──
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── MLKit Translate ──
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Filament 3D ──
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**

# ── App Auth / Data classes ──
-keep class com.snowrealm.yukiboard.auth.** { *; }
-keep class com.snowrealm.yukiboard.data.** { *; }
-keep class com.snowrealm.yukiboard.claw.** { *; }

# ── General: keep Serializable / Parcelable ──
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── Suppress warnings ──
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
