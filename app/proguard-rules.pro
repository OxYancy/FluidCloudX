# YukiHookAPI
-keep class com.highcapable.yukihookapi.** { *; }
-keep interface com.highcapable.yukihookapi.** { *; }
-keep class * implements com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit { *; }

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Rule models
-keep class com.nyehueh.fluidcloud.core.model.** { *; }
