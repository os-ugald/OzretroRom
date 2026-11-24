# Oz RetroRom — reglas de release segura.

# Métodos vinculados con bibliotecas nativas JNI.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# libtorrent4j usa JNI y carga clases nativas por nombre.
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**

# Room genera implementaciones y puede inspeccionar firmas/anotaciones.
-keep class cl.gakuto.kizuna.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# WorkManager crea workers por nombre de clase.
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }

# Conservar metadatos usados por Kotlin, Room y librerías AndroidX.
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes AnnotationDefault
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# No registrar nombres completos de archivos fuente en trazas release.
-renamesourcefileattribute SourceFile
-keepattributes LineNumberTable
