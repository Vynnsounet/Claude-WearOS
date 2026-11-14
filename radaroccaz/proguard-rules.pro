# kotlinx.serialization: keep generated serializers of the app's models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.radaroccaz.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.radaroccaz.app.**$$serializer { *; }

# jsoup optionally references re2j.
-dontwarn com.google.re2j.**
