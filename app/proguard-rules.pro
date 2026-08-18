-optimizationpasses 7
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

-renamesourcefileattribute x
-keepattributes !SourceFile,!LineNumberTable,!LocalVariable*

-keep public class com.replayx.receiver.ui.MainActivity { public *; }
-keep public class com.replayx.receiver.ui.LoginActivity { public *; }
-keep class com.replayx.receiver.security.** { *; }

-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-keep class androidx.** { *; }
-dontwarn androidx.**
-keep class kotlin.** { *; }
-dontwarn kotlin.**
-dontwarn org.json.**
-dontwarn com.google.zxing.**
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
