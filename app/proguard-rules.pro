# LiteGallery release rules
#
# Remove low-value debug/info/verbose logs in release builds to reduce runtime overhead
# and avoid noisy output on user devices.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
