# pastebench13

Part of [gw_paste98](../README.md). Can a Java 1.3 runtime, the newest Sun
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
need only two things Java 1.3 lacks, so the build adapts them:

- `src/build/java/Retarget13.java` (run at build time with ASM) points BC's
  `java.util.logging` calls at do-nothing stand-ins (`compat13.Logger` and
  `Level`), replaces exception cause chaining (`initCause`, `X(Throwable)`
  constructors, both 1.4) with `compat13.Causes`, and marks every class file
  as Java 1.3 (version 47; 1.4's 48 adds no bytecode features).
- ProGuard shrinks the result with the benchmark into `PB13.JAR`.
- `src/build/java/Check13.java` then fails the build unless every class in
  the jar is a Java 1.3 class file and nothing in it uses a JDK class, method
  or field that Java 1.3 lacks (animal-sniffer's java13 signature).

What a static check cannot see is behaviour that changed between versions;
only a run on a real Java 1.3 settles that.
