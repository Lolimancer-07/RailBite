# R8 rules for the RailBite apps (Retrofit + kotlinx.serialization + HiveMQ/netty).

# --- Retrofit (official R8 rules, retrofit2 README) ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# --- kotlinx.serialization (official R8 rules) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.railbite.**$$serializer { *; }
-keepclassmembers class com.railbite.** {
    *** Companion;
}
-keepclasseswithmembers class com.railbite.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- HiveMQ MQTT client + bundled netty (critical for the alert pipeline) ---
-keep class com.hivemq.** { *; }
-keep class io.netty.** { *; }
-keep class org.jctools.** { *; }
-dontwarn io.netty.**
-dontwarn com.hivemq.**
-dontwarn org.jctools.**

# --- misc ---
-dontwarn org.slf4j.**
-dontwarn javax.security.auth.x500.**
-dontwarn java.security.**
