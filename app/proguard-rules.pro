# LibXposed API 102
-dontwarn io.github.libxposed.**
-keep @io.github.libxposed.api.annotations.XposedHooker class * {
    @io.github.libxposed.api.annotations.BeforeInvocation <methods>;
    @io.github.libxposed.api.annotations.AfterInvocation <methods>;
}
-keep public class * extends io.github.libxposed.api.XposedModule {
    public <init>(...);
    public void onPackageLoaded(...);
    public void onSystemServerLoaded(...);
}

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
