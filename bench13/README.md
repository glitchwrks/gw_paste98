# pastebench13

Part of [Paste Jar](../README.md) (formerly gw_paste98). Can a Java 1.3 runtime, the newest Sun
released for Windows 95, do the paste client's pinned TLS 1.2 handshake, and
how fast? This answers that before any of the client is ported.

It is the Java 5 [pastebench](../bench/README.md) cut down and rewritten in
Java 1.3: the same offline crypto tests and full and resumed handshakes
against the real server, with the server authenticated by its key pin, plus
the cost of Java 1.3's own `SecureRandom` seeding. The same jar runs on any
Java from 1.3 up, so one machine can time Java 1.3 against Java 5.

## Running it

Windows 98 with JRE 1.3.1 tests the same thing as Windows 95: everything
measured happens inside Java. (Windows 95 matters later, for the tray DLL,
installer and help.)

1. Copy the `pastebench13` folder to the machine.
2. Check the Java 1.3 path in `SETJAVA.BAT` (the Sun installer's default is
   `C:\Program Files\JavaSoft\JRE\1.3.1\bin\java.exe`).
3. Put the server's key pin in `BENCH13.CFG` (the same value as in the paste
   client's Settings, or the Java 5 pastebench's `BENCH.CFG`).
4. Run, from that folder:

| File | What | Results in |
|---|---|---|
| `BENCH13.BAT` | Everything, on Java 1.3 | `BENCH13.TXT` |
| `FAST13.BAT` | The same, seeding `SecureRandom` from timing jitter instead of Java 1.3's own seeding | `FAST13.TXT` |
| `BENCH5.BAT` | The same jar on the `java` on the PATH (normally Java 5), for comparison | `BENCH5.TXT` |
| `SHOWPIN.BAT` | Prints the pin of whatever server answers (not verified) | screen |

Options: `java -jar PB13.JAR --help`.

## What to look for

- **Startup, `SecureRandom, Java's own seeding`**: Java 1.3 seeds its random
  generator by timing threads, which can take many seconds on old hardware.
  If it does, the real client needs another seed source (`FAST13.TXT` shows
  the difference).
- **`full* 1`** and its step timeline: the first handshake, including loading
  the code. Compare with `BENCH5.TXT` and the Java 5 pastebench's results.
- **`resume`** lines: later connections, which the client's warm-up relies on.
- **Offline crypto**: the same tests as the Java 5 pastebench, for a
  line-by-line comparison of Java 1.3's HotSpot with Java 5's.

## How the jar is made

Bouncy Castle publishes current builds for Java 1.4 (`bc*-jdk14`, the same
1.86 release the client uses), but none for 1.3. The parts this program uses
need only three things Java 1.3 lacks, so the build adapts them:

- `src/build/java/Retarget13.java` (run at build time with ASM) points BC's
  `java.util.logging` calls at do-nothing stand-ins (`compat13.Logger` and
  `Level`) and its uses of `javax.security.auth.Destroyable` (core only from
  1.4) at `compat13.Destroyable`, replaces exception cause chaining (`initCause`, `X(Throwable)`
  constructors, both 1.4) with `compat13.Causes`, and marks every class file
  as Java 1.3 (version 47). One real bytecode difference: javac 1.4 sets an
  inner class's outer-instance and captured-variable fields before calling the
  superclass constructor, which Java 1.3's verifier rejects (`VerifyError:
  Expecting to find object/array on stack`) while later verifiers, JDK 8's
  included, accept it. Retarget13 moves those stores to just after the
  superclass constructor call, as javac 1.3 did.
- ProGuard shrinks the result with the benchmark into `PB13.JAR`.
- `src/build/java/Check13.java` then fails the build unless every class in
  the jar is a Java 1.3 class file and nothing in it uses a JDK class, method
  or field that Java 1.3 lacks (animal-sniffer's java13 signature), mentions
  no class Java 1.3 lacks anywhere (superclasses, interfaces and descriptors
  too, which animal-sniffer does not check), and no constructor stores a field
  before calling its superclass constructor.

What a static check cannot see is behaviour that changed between versions;
only a run on a real Java 1.3 settles that. The first T22 run found the
constructor problem above, which no tool here could.

## Results so far (ThinkPad T22, Windows 98)

- Java 1.3.1's own `SecureRandom` seeding: **33 s** (Java 5: 0.2 s). The
  client cannot use it on Java 1.3; `--fastseed` (timing jitter) took 1.8 s.
- Version 0.1.0 then failed with the `VerifyError` above; fixed in 0.1.1.
- 0.1.1 then failed with `NoClassDefFoundError: javax/security/auth/Destroyable`,
  an interface BC's key classes implement, which animal-sniffer does not check;
  fixed, and checked for every class name, in 0.1.2.
- **0.1.2 works end to end on Java 1.3.1** against production: pinned TLS 1.2
  (ECDHE-RSA-AES256-GCM-SHA384), no errors, two runs.

| | Java 5 | Java 1.3.1 |
|---|---|---|
| Java's own `SecureRandom` seeding | 0.22 s | 33.1 s |
| Timing-jitter seed (`--fastseed`) | 1.75 s | 1.75 s |
| X25519 keygen + agreement | 7.6 ms | 14.7-16.0 ms |
| P-256 keygen + agreement | 23.3 ms | 28.3-29.0 ms |
| RSA-2048 verify | 8.4 ms | 11.5 ms |
| ECDSA P-256 verify | 6.9 ms | 8.8 ms |
| SHA-256 | 6.5 MB/s | 7.2-7.4 MB/s |
| AES-128-GCM / ChaCha20-Poly1305 | 2.5 / 4.6 MB/s | 2.3 / 3.4 MB/s |
| First handshake | 1,040 ms | 820-880 ms |
| Later handshakes | 220-330 ms | 270-330 ms |

Conclusion: Java 1.3 is viable; handshakes cost about the same as on Java 5.
The only blocker is seeding: a Java 1.3 client needs its own seed source
(Windows' CryptGenRandom through the tray DLL where available, a seed file
kept between runs, and timing jitter mixed in) instead of Java's 33 s one.
