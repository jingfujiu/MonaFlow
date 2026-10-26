# MonaFlow proguard rules
-keep class com.monaflow.app.data.backup.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
