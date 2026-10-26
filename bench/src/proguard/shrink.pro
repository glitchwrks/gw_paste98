# ProGuard config: shrink pastebench + gw_paste98 core + Bouncy Castle into one small jar.
#
# Shrinking only. No obfuscation, optimization or preverification, so the
# remaining classes are byte-for-byte BC's Java 1.4 (version 48) class files, which Java 5 runs as they are.
# Values in <angle brackets> are system properties passed in by the pom.

-injars  <target.dir>/classes
-injars  <manifest.dir>(META-INF/MANIFEST.MF)
# Drop BC's META-INF: its jar signatures would no longer match a merged jar.
-injars  <lib.dir>/gw_paste98-core-<core.version>.jar(!META-INF/**)
-injars  <lib.dir>/bcprov-jdk14-<bc.version>.jar(!META-INF/**)
-injars  <lib.dir>/bctls-jdk14-<bc.version>.jar(!META-INF/**)
-injars  <lib.dir>/bcutil-jdk14-<bc.version>.jar(!META-INF/**)
-outjars <target.dir>/pastebench-small.jar

# Resolve JDK references against a Java 8 runtime. Java 5's API is a subset;
# the build checks the output separately against the Java 5 signature.
-libraryjars <jdk8.home>/jre/lib/rt.jar
-libraryjars <jdk8.home>/jre/lib/jce.jar
-libraryjars <jdk8.home>/jre/lib/jsse.jar

-dontobfuscate
-dontoptimize
-dontpreverify
-keepattributes SourceFile,LineNumberTable,Signature,Exceptions,InnerClasses,EnclosingMethod

-keep public class com.glitchwrks.paste98.bench.PasteBench {
    public static void main(java.lang.String[]);
}

-printusage <target.dir>/proguard-removed.txt
-dontnote
