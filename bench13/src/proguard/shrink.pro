# ProGuard config: shrink pastebench13 + Bouncy Castle (already retargeted to
# Java 1.3 by Retarget13) into PB13.JAR.
#
# Shrinking only: no obfuscation, optimization or preverification, so class
# files keep the version 47 (Java 1.3) they were given.
# Values in <angle brackets> are system properties passed in by the pom.

-injars  <target.dir>/classes
-injars  <manifest.dir>(META-INF/MANIFEST.MF)
-injars  <target.dir>/bc13/bcprov13.jar
-injars  <target.dir>/bc13/bctls13.jar
-injars  <target.dir>/bc13/bcutil13.jar
-outjars <target.dir>/PB13.JAR

# Resolve JDK references against Java 8; Check13 then verifies the result
# against the Java 1.3 API.
-libraryjars <jdk8.home>/jre/lib/rt.jar
-libraryjars <jdk8.home>/jre/lib/jce.jar
-libraryjars <jdk8.home>/jre/lib/jsse.jar

-dontobfuscate
-dontoptimize
-dontpreverify
-keepattributes SourceFile,LineNumberTable,Exceptions,InnerClasses

-keep public class com.glitchwrks.paste98.bench13.Bench13 {
    public static void main(java.lang.String[]);
}

-printusage <target.dir>/proguard-removed.txt
-dontnote
