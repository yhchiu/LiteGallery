# SMB keep rules — applied only to the `plus` flavor (see app/build.gradle).

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
