# kotlinx.serialization ships its own R8 rules; these make sure the app's models keep
# their generated serializers whatever the library version.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.medipote.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.medipote.app.**$$serializer { *; }

# Smaller, faster code: move every obfuscated class into one package.
-repackageclasses ''

# Readable crash reports: keep file names and line numbers (mapped back with mapping.txt).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
