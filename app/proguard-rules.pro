# 保留 kotlinx.serialization 元数据
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.vita.healthtracker.**$$serializer { *; }
-keepclassmembers class com.vita.healthtracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.vita.healthtracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
