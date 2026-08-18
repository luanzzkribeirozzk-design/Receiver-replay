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
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Componentes declarados no Manifest são preservados pelo Android Gradle Plugin.
# Não manter a UI inteira nem os nomes da lógica de licença.

-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-dontwarn org.json.**
-dontwarn javax.crypto.**
-dontwarn android.security.keystore.**
