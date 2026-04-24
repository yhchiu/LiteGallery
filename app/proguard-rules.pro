# LiteGallery release rules
#
# Remove low-value debug/info/verbose logs in release builds to reduce runtime overhead
# and avoid noisy output on user devices.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# smbj - SMB client library
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-dontwarn com.hierynomus.**
-dontwarn net.engio.mbassy.**

# Bouncy Castle (transitive dependency of smbj)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SLF4J
-dontwarn org.slf4j.**
