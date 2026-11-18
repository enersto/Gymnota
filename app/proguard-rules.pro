# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Rust JNI uses a stable class and native method name.
-keep class com.example.myfit.flutter.RustHistoryCore { native <methods>; }

-keep class com.example.myfit.data.ai.RustAiStream { native <methods>; }
-keep class com.example.myfit.data.ai.RustAiStream$Companion { native <methods>; }

-keep class com.example.myfit.data.RustSettingsCsv { native <methods>; }
-keep class com.example.myfit.data.RustWeeklyRoutine { native <methods>; }
-keep class com.example.myfit.data.RustWorkout { native <methods>; }

-keep class com.example.myfit.data.RustTimer { native <methods>; }

# Gson fields are the stable SQLite/backup/JNI JSON contract (not Java identifiers).
-keep class com.example.myfit.model.** { *; }
-keep class com.example.myfit.data.api.** { *; }
-keep class com.example.myfit.viewmodel.MainViewModel$TimerState { *; }
-keep class com.example.myfit.viewmodel.TimerPhase { *; }
-keep class com.example.myfit.data.RustTimer$Result { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-keep,allowobfuscation,allowoptimization class com.google.gson.reflect.TypeToken { *; }
-keep,allowobfuscation,allowoptimization class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.example.myfit.data.RustCatalog { native <methods>; }

# Flutter locates this entry point with reflection.
-keep class io.flutter.plugins.GeneratedPluginRegistrant { *; }
