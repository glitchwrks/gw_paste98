# pastebench

Part of [Paste Jar](../README.md) (formerly gw_paste98). The pinned TLS client it measures lives in
the shared `core` module.

TLS benchmark for the Java 5 / Windows 98SE paste client. It measures what the
real client will pay to talk to `pastes.glitchwrks.com` over modern TLS, using
Bouncy Castle's pure-Java TLS stack with the server authenticated by a pinned
public key.

## What it measures

| Section | What | Why it matters |
|---|---|---|
| Startup | JVM start to `main()`, first `SecureRandom` seed, BC init | One-time costs per launch. Win9x wall-clock resolution is ~55 ms. |
| Offline crypto | X25519 and P-256 key exchange, RSA-2048 and ECDSA P-256 verify, SHA-256, AES-128-GCM, ChaCha20-Poly1305 | The primitives a handshake and the record layer use. Needs no network. |
| Network | DNS, TCP connect, full handshakes, resumed handshakes, optional HTTP GET | What a paste submission actually costs end to end. |

The first full handshake is marked `full*`: it includes loading the TLS
classes, so expect it to be much slower than later ones on old hardware.

## Trust model

The client trusts the server only if the SHA-256 hash of the leaf
certificate's SubjectPublicKeyInfo matches a configured `--pin`. There are no
CA chain, hostname or expiry checks, so Java 5's outdated `cacerts` doesn't
matter and Let's Encrypt renewals don't break anything as long as the key pair
stays the same. Bouncy Castle still verifies the server's handshake signature
with that key, so a replayed certificate without the private key fails.

If the server is ever rebuilt with a **new** key, every client with the old
pin stops connecting. The fix is a backup pin: generate a second key pair now,
keep it offline, pin both, and have the rebuild deploy the backup key.

## Getting the pin

On the server (OpenBSD / LibreSSL), from the key `relayd` uses:

```
openssl pkey -in /etc/ssl/private/pastes.glitchwrks.com.key -pubout -outform der \
  | openssl dgst -sha256 -binary | openssl base64
```

Adjust the path to wherever your key actually lives. From any machine, over
the network (useful as a cross-check, but this trusts whatever answers):

```
openssl s_client -connect pastes.glitchwrks.com:443 -servername pastes.glitchwrks.com </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | openssl base64
```

`SHOWPIN.BAT` prints what the Win98 client sees. All three should agree.

Backup key (keep the `.key` file offline; only its pin goes in `BENCH.CFG`):

```
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out pastes-backup.key
openssl pkey -in pastes-backup.key -pubout -outform der | openssl dgst -sha256 -binary | openssl base64
```

Pins may be given bare or with a `sha256//` prefix (the curl/HPKP format).

## Running on Windows 98SE

Needs a Java 5 runtime. Unzip `pastebench-win98.zip`, then **from inside the
`pastebench` folder** (command.com has no way for a batch file to find its own
folder):

1. `OFFLINE.BAT`: startup and crypto timings only, results in `OFFLINE.TXT`.
2. `SHOWPIN.BAT`: prints the server's pin. Compare with the server-side value.
3. Edit `BENCH.CFG`: put the pin(s) in, optionally enable `--get`.
4. `BENCH.BAT`: everything, results in `BENCH.TXT`.
5. `COMPARE.BAT`: the startup comparison below, results in `COMPARE.TXT`.

The batch files use `-Xmx32m`. It ran in a 16 MB heap in testing; 8 MB was too
small. Options go in `BENCH.CFG` rather than on the command line because
command.com limits command lines to 127 characters. Any `@FILE` argument is
expanded the same way.

### Jar variants

| File | What | Size |
|---|---|---|
| `SMALL.JAR` | App plus only the Bouncy Castle classes it can reach, shrunk by ProGuard. Used by the everyday batch files. | 1.3 MB |
| `SMALL0.JAR` | Same classes, stored without compression. | 2.8 MB |
| `FULL\pastebench.jar` + `FULL\lib\` | Unshrunk app and the complete Bouncy Castle jars. | 8.8 MB |

### Startup comparison (`COMPARE.BAT`)

Runs, all skipping the offline crypto timings so the first handshake carries
the whole cold-start cost:

0. offline timings only, to measure Bouncy Castle's RSA key validation alone
1. `SMALL.JAR` with `--no-prevalidate --profile 20`: BC validates the server's
   RSA key itself (the old behaviour), with the phase timeline and profiler
2. `SMALL.JAR` with the pinned key pre-validated (the default), with profiler
3. `SMALL0.JAR`, stored uncompressed
4. the full, unshrunk jars
5. `--warmup 5000`, the resident-client model

then runs 2 and 3 again with a warm disk cache. Reboot first for the cleanest
cold numbers. The line to compare across runs is
`first connection: ... JVM start -> done N ms`.

Every run prints a timeline of the first two handshakes: time, classes loaded
and time blocked on the network at each step (ClientHello, ServerHello,
Certificate, pin check, ServerHelloDone, done). `--profile` adds a breakdown
of where the connecting thread was when sampled.

## Options

```
@FILE                read more options from FILE (whitespace separated, # comments)
--offline            only run local timings (startup, RNG, crypto); no network
--show-pin           connect once and print the server's SPKI pin (NOT verified)
--host NAME          server (default pastes.glitchwrks.com)
--port N             port (default 443)
--pin BASE64         SHA-256 SPKI pin of the server key; repeat for a backup key
--full N             full handshakes to run (default 3)
--resume N           resumed handshakes to attempt (default 3, 0 to skip)
--get PATH           also send GET PATH on each connection, e.g. /tstpaste
--cipher C           auto | chacha | aes128 | aes256 (default auto, ChaCha20 first)
--group G            auto | x25519 | p256 (default auto, X25519 first)
--tls13              also offer TLS 1.3 (default TLS 1.2 only)
--warmup MS          resident-client simulation: warm-up handshake in the background,
                     MS of simulated typing, then one timed submission
--profile MS         sample the first handshake's stack every MS ms (Win9x: ~55 ms minimum)
--no-prevalidate     let BC run its RSA modulus check on the pinned key (slow on old CPUs)
--skip-crypto        skip the offline crypto timings
--budget-ms N        time spent per crypto timing (default 2000)
--timeout-ms N       socket connect/read timeout (default 30000)
```

Exit status: 0 success, 1 bad arguments, 2 a connection failed, 3 unexpected
error.

## Reading the results

- **Resumed handshakes** skip the key exchange and signature check entirely.
  Rows marked `(not resumed)` mean the server didn't accept the session; check
  `relayd`'s TLS session settings if you want resumption.
- **TLS 1.3:** Bouncy Castle's TLS 1.3 client did not resume sessions in
  testing, which is why TLS 1.2 is the default.
- **Cipher choice:** ChaCha20-Poly1305 is preferred by default because it is
  faster than AES-GCM in pure software on CPUs without AES instructions.
  Paste-sized payloads make this a small effect either way.
- **Certificate type:** on the ThinkPad T22, RSA-2048 verify (~9 ms) and ECDSA
  P-256 verify (~7 ms) cost about the same, so the certificate type doesn't
  matter.
- **Where the time goes:** on the T22 a warm full handshake was ~270 ms, of
  which only ~20 ms was crypto. The first handshake after launch was ~9-10 s.
  Shrinking the jars and disabling bytecode verification barely changed it.
  The cause is Bouncy Castle's validation of every new RSA public key: 4
  Miller-Rabin rounds on the 2048-bit modulus, each a full-size modular
  exponentiation in Java 5's `BigInteger`, cached per JVM. That is why only the
  first handshake was slow.
- **Pinned RSA key pre-validation (default):** once the server's key has
  matched a pin, the client marks it as already validated in BC's cache, so
  BC skips that test. This is safe because the test only guards against
  malformed keys, and a key matching the pin is the server's real key. It is
  only done after a pin match, never in `--show-pin` mode.
  `--no-prevalidate` restores BC's behaviour for comparison.

## Building

From the repository root, with JDK 8 and Maven:

    JAVA_HOME=/path/to/jdk8 mvn package

This produces `bench/target/pastebench-win98.zip`. The build compiles for
Java 5, fails if the code uses anything newer than the Java 5 API
(animal-sniffer), then shrinks the benchmark, the shared `core` module and
Bouncy Castle with ProGuard (`src/proguard/shrink.pro`: shrinking only, no
obfuscation, optimization or preverification, so the kept classes are
unchanged Java 5 class files).

Dependencies are Bouncy Castle 1.86 `jdk15to18` builds (`bcprov`, `bctls`,
`bcutil`). All their classes are Java 5 bytecode. A Java 5 API check over the
full jars found references to newer APIs only in the JSSE provider, the JCE
provider's OCSP code, the JCA-backed TLS crypto and one post-quantum class. The
same check over `SMALL.JAR` finds none: ProGuard removes all of them as
unreachable.

## Verified so far

On a ThinkPad T22 (Java 1.5.0_22, Windows 98): version 0.1.0 connected to
production `pastes.glitchwrks.com` with the pin enforced.

On JDK 8 against local `openssl s_server` instances, for the full jars and
both shrunk jars:

- `--show-pin` output matches the pin computed from the key with openssl
- correct pin connects; wrong pin is refused and reports the presented pin
- backup pin list (wrong pin first, right pin second) connects
- TLS 1.2 session resumption works
- ECDSA certificate, AES-256-GCM, P-256 and TLS 1.3 paths work
- `--warmup` works both when the warm-up finishes during typing and when it
  doesn't
- pinned RSA key pre-validation removes BC's Miller-Rabin check from the first
  handshake (interpreted mode: 925 ms to 174 ms), and a wrong pin is still
  refused before it runs
- `SMALL0.JAR` has no ZIP64 or other extra fields (an earlier build had ZIP64
  fields, which Java 5 rejects as a corrupt jar)
- the `COMPARE.BAT` command sequence runs end to end
- runs in a 16 MB heap
