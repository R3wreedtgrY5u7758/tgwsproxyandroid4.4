# JNA
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.swing.**
-dontwarn com.sun.jna.**
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.Library { *; }
-keepclassmembers class * implements com.sun.jna.Library { <methods>; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * extends com.sun.jna.Structure { *; }

# Интерфейс нативной библиотеки и сервис
-keep class com.amurcanov.tgwsproxy.NativeProxy { *; }
-keep interface com.amurcanov.tgwsproxy.NativeProxy$Lib { *; }
-keep class com.amurcanov.tgwsproxy.ProxyService { *; }
-keepclasseswithmembernames class * { native <methods>; }
