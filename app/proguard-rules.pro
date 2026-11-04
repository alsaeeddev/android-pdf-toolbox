# Project specific ProGuard / R8 rules for myPrev

# ==================== AndroidX Room ====================
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public void <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep entities and DAOs
-keep class com.example.core.data.local.entity.** { *; }
-keep interface com.example.core.data.local.dao.** { *; }
-keep class com.example.core.data.local.AppDatabase { *; }

# ==================== PdfBox-Android ====================
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.fontbox.**
-keep class org.apache.fontbox.** { *; }

# ==================== Jetpack Compose & Kotlin Coroutines ====================
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.datastore.** { *; }

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
