# ProGuard config: shrink the client + gw_paste98 core + Bouncy Castle into GWPASTE.JAR.
#
# Bouncy Castle comes in already retargeted to Java 1.3 by Retarget13 (see the
# pom). Shrinking only: no obfuscation, optimization or preverification, so
# every class keeps version 47 (Java 1.3). Check13 then checks the result.
# Values in <angle brackets> are system properties passed in by the pom.

-injars  <target.dir>/classes
-injars  <manifest.dir>(META-INF/MANIFEST.MF)
# Drop META-INF from the libraries: BC's jar signatures would not match a merged jar.
-injars  <lib.dir>/gw_paste98-core-<core.version>.jar(!META-INF/**)
-injars  <target.dir>/bc13/bcprov13.jar
-injars  <target.dir>/bc13/bctls13.jar
-injars  <target.dir>/bc13/bcutil13.jar
-outjars <target.dir>/GWPASTE.JAR

# Resolve JDK references against a Java 8 runtime; Check13 then verifies the
# result against the Java 1.3 API.
-libraryjars <jdk8.home>/jre/lib/rt.jar
-libraryjars <jdk8.home>/jre/lib/jce.jar
-libraryjars <jdk8.home>/jre/lib/jsse.jar

-dontobfuscate
-dontoptimize
-dontpreverify
-keepattributes SourceFile,LineNumberTable,Exceptions,InnerClasses

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
