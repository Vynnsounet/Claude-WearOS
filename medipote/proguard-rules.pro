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
