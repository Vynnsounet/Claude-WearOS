# kotlinx.serialization ships its own R8 rules; these make sure the app's models keep
# their generated serializers whatever the library version.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.medipote.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.medipote.app.**$$serializer { *; }

# Keep R8 to shrinking and renaming. Its optimizer rewrites atomic field updaters (coroutines,
# Compose) into sun.misc.Unsafe calls that only run on real devices and can't be checked off-device;
# the code size saved isn't worth that risk for a medication reminder that must always open.
-dontoptimize

# Readable crash reports: keep file names and line numbers (mapped back with mapping.txt).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Room creates its generated *_Impl databases by reflection (WorkManager's included): keep their
# no-arg constructor. R8 full mode doesn't keep constructors for a plain "-keep class".
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# WorkManager creates workers by class name (weekly automatic backup): keep their constructor.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# What the phone and the watch say to each other (module medipote-sync).
-keepclassmembers @kotlinx.serialization.Serializable class com.medipote.sync.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.medipote.sync.**$$serializer { *; }

# The optional AI assistant: the Anthropic SDK builds and reads its request and response models
# with Jackson, by reflection and annotations. Keep them whole, and the libraries around them quiet.
-keepattributes Signature, Exceptions, EnclosingMethod
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn com.anthropic.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn java.lang.management.**
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.google.auto.value.AutoValue
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType