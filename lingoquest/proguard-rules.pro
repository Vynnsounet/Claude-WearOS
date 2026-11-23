# kotlinx.serialization: keep generated serializers of the saved models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.lingoquest.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.lingoquest.**$$serializer { *; }
