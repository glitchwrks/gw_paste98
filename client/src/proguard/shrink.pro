# ProGuard config: shrink the client + gw_paste98 core + Bouncy Castle into GWPASTE.JAR.
#
# Shrinking only. No obfuscation, optimization or preverification, so the
# remaining classes are unchanged Java 5 (version 49) class files.
# Values in <angle brackets> are system properties passed in by the pom.

-injars  <target.dir>/classes
-injars  <manifest.dir>(META-INF/MANIFEST.MF)
# Drop META-INF from the libraries: BC's jar signatures would not match a merged jar.
-injars  <lib.dir>/gw_paste98-core-<core.version>.jar(!META-INF/**)
-injars  <lib.dir>/bcprov-jdk15to18-<bc.version>.jar(!META-INF/**)
-injars  <lib.dir>/bctls-jdk15to18-<bc.version>.jar(!META-INF/**)
-injars  <lib.dir>/bcutil-jdk15to18-<bc.version>.jar(!META-INF/**)
-outjars <target.dir>/GWPASTE.JAR

# Resolve JDK references against a Java 8 runtime. Java 5's API is a subset;
# the build checks the code separately against the Java 5 signature.
-libraryjars <jdk8.home>/jre/lib/rt.jar
-libraryjars <jdk8.home>/jre/lib/jce.jar
-libraryjars <jdk8.home>/jre/lib/jsse.jar

-dontobfuscate
-dontoptimize
-dontpreverify
-keepattributes SourceFile,LineNumberTable,Signature,Exceptions,InnerClasses,EnclosingMethod

-keep public class com.glitchwrks.paste98.client.Main {
    public static void main(java.lang.String[]);
}

# gwtray.dll binds the native methods by name and calls onEvent(int) back.
# Nothing in Java calls onEvent, so without this ProGuard would remove it.
-keep class com.glitchwrks.paste98.client.tray.NativeTray {
    native <methods>;
    static void onEvent(int);
}

-printusage <target.dir>/proguard-removed.txt
-dontnote
