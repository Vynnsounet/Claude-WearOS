# Same choices as the phone app: shrink and rename only, readable crash reports.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.medipote.sync.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.medipote.sync.**$$serializer { *; }
-dontoptimize
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
