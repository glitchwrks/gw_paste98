# gw_paste98

A Java 5 / Windows 98SE tray client for the
[Glitch Works pastes service](https://github.com/glitchwrks/pastes), using
modern TLS on a period machine.

This is a hobbyist proof of concept exploring how far a legacy platform can be
brought up to an adequate level of encrypted communication with a modern
service, with an LLM doing much of the work.

## How it works

- **TLS 1.2 in pure Java.** Java 5's own JSSE stops at TLS 1.0, so the client
  uses Bouncy Castle's TLS client (the `jdk15to18` builds, which are Java 5
  bytecode) with its lightweight crypto, avoiding Java 5's JCE policy limits.
- **Pinned server key.** The server is authenticated by a SHA-256 pin of its
  public key, not by a certificate authority. Java 5's CA list is irrelevant,
  and Let's Encrypt renewals do not affect clients while the key stays the same.
- **Fast enough on a Pentium III.** Measured on a ThinkPad T22: about 2.4 s from
  launch to the first completed handshake, then about 0.4 s per paste. Getting
  there took two fixes, both in this code:
  - ProGuard shrinks Bouncy Castle to the ~1,200 classes actually used
    (8.8 MB of jars down to 1.3 MB), saving ~4 s at launch.
  - Once the server's key matches a pin, it is marked as already validated in
    Bouncy Castle's cache, skipping 4 Miller-Rabin rounds on the 2048-bit RSA
    modulus that otherwise cost ~10 s in Java 5's `BigInteger`. This is safe
    because the check only guards against malformed keys, and a pinned key is
    the server's real one. See `PinnedTlsClient`.
- **Tray icon.** Java 5 has no tray API, so `native/gwtray.c` provides one as a
  ~9 KB JNI DLL with no C runtime, importing only Windows 98 functions. If it
  cannot be used, the client falls back to a small Swing window.
- **Warm-up.** The tray client does one throwaway handshake in the background
  at launch, so the slow first handshake happens while you are typing.

## Layout

| Path | What |
|---|---|
| `core/` | Pinned TLS 1.2 client (`tls`) and pastes API client (`api`); shared |
| `client/` | Swing tray application; builds `client/target/gw_paste98-win98.zip` |
| `bench/` | `pastebench`, the TLS benchmark used to tune the client; builds `bench/target/pastebench-win98.zip` |
| `native/` | `gwtray.dll` source, build script and checker; the built DLL and icon are in `native/prebuilt/` |
| `tools/` | `fake_pastes_server.py`, a local stand-in for the pastes service, for testing |

## Building

Needs **JDK 8** and **Maven 3**. JDK 8's `javac` is the last one that can
produce Java 5 class files (`-source 1.5 -target 1.5`).

    JAVA_HOME=/path/to/jdk8 mvn package

This compiles everything for Java 5, runs the unit tests, fails the build if
any code uses an API newer than Java 5 (animal-sniffer), shrinks the jars with
ProGuard and assembles the two Windows 98 zips.

The Maven build uses the committed `native/prebuilt/gwtray.dll`. To rebuild
the DLL (Zig 0.13 as the cross-compiler, plus Python with `pefile` and
`capstone`):

    pip install ziglang==0.13.0 pefile capstone
    JAVA_HOME=/path/to/jdk native/build.sh

`native/build.sh` is reproducible (byte-identical output) and runs
`native/check_pe.py`, which fails the build if the DLL is marked for a Windows
newer than 4.0, imports anything Windows 98 lacks, uses MMX/SSE instructions,
or has wrong JNI export names or stdcall argument sizes.

## Testing

- `mvn package` runs the unit tests (API client logic against canned
  responses, HTTP parsing, JSON encoding, settings).
- `tools/fake_pastes_server.py` mimics the real service's responses (Basic Auth,
  JSON schema check, name uniqueness, latin1 storage) over TLS, so the client
  can be exercised end to end without touching production:

      python3 tools/fake_pastes_server.py --cert cert.pem --key key.pem --port 9443

  Point `PASTE98.CFG` at `localhost:9443` with the test key's pin, then use the
  tray client or `SENDFILE.BAT`.

## Status

Working on a ThinkPad T22 (Pentium III, Java 1.5.0_22, Windows 98): the tray
icon via `gwtray.dll`, and pastes created on production over pinned TLS 1.2,
with the address copied to the clipboard (version 0.1.1).

Also tested on JDK 8 / Linux: unit tests, the client end to end against the
fake server, and the Swing UI under Xvfb.

## Licence

Copyright (C) 2026 Glitch Works, LLC. GNU General Public License v3 or later;
see `LICENSE`. Bouncy Castle is under the MIT licence.
