# kotlinx.serialization: keep generated serializers of the app's models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.medipote.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.medipote.app.**$$serializer { *; }
