# MapLibre Native is driven from JNI; keep its Java API intact.
-keep class org.maplibre.android.** { *; }
-keep interface org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**
