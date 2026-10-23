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
- **Looks like Java on purpose.** The default is Swing's classic Metal look.
  Java cannot draw Windows 98's bitmap UI font (MS Sans Serif), so the Windows
  look always shows slightly different text; it is available as a setting,
  substituting Microsoft Sans Serif or Tahoma when installed.
- **Settings without the registry.** Settings use the standard
  `java.util.prefs` API, the period-correct choice for Java on Windows, but
  with a small file backend (`client/.../prefs`) installed in place of Java's
  default Windows backend, which writes to the registry. Everything is stored
  in `PASTE98.PRF` in the program folder.

## Getting the server's key pin

The client trusts the server only if its public key matches a pin: the
base64 SHA-256 hash of the key's DER-encoded SubjectPublicKeyInfo (the same
format as curl's `--pinnedpubkey sha256//...`).

From any machine with OpenSSL or LibreSSL, ask the server for its certificate
and hash the public key:

    openssl s_client -connect pastes.glitchwrks.com:443 -servername pastes.glitchwrks.com </dev/null 2>/dev/null \
      | openssl x509 -pubkey -noout \
      | openssl pkey -pubin -outform der \
      | openssl dgst -sha256 -binary \
      | openssl base64

The client's **Settings > Server > Get pin from server...** does the same
thing from the Windows 98 machine. **Neither verifies anything**: both report
whatever key the server that answered presented, so an interception between
you and the server would show the interceptor's key. Treat the result as a
claim to check, not as the answer. The pin is only trustworthy if it matches
one obtained independently, most directly from the key file on the server
itself:

    openssl pkey -in /etc/ssl/private/pastes.glitchwrks.com.key -pubout -outform der \
      | openssl dgst -sha256 -binary | openssl base64

(adjust the path to the key `relayd` uses). Running the network command from
two different networks and getting the same answer is a weaker check, but
better than none. The client shows its fetched pin with a warning to this
effect and adds it only after you confirm you have checked it.

## Layout

| Path | What |
|---|---|
| `core/` | Pinned TLS 1.2 client (`tls`) and pastes API client (`api`); shared |
| `client/` | Swing tray application; builds `client/target/gw_paste98-win98.zip` |
| `bench/` | `pastebench`, the TLS benchmark used to tune the client; builds `bench/target/pastebench-win98.zip` |
| `native/` | `gwtray.dll` source, build script and checker; the built DLL and icon are in `native/prebuilt/` |
| `tools/` | `fake_pastes_server.py`, a local stand-in for the pastes service, for testing |

## Adding a setting

Every setting is declared once in `client/.../settings/Settings.java`, with
its key, Settings tab, label, help text, type, default and validation:

    public static final Setting.Int TIMEOUT = new Setting.Int("net.timeout", CONNECTION, "Timeout",
            "How long to wait for the server before giving up.", 30, 5, 300, "seconds");

Add it to the list in the same file, then read it where it is used with
`settings.current().getInt(Settings.TIMEOUT)`. The Settings dialog, saving,
validation of stored values and XML import pick it up automatically. Types
are `Text`, `Host`, `Int`, `Flag` (checkbox), `Choice` (drop-down) and `Pins`.
If a later version changes the meaning of a stored value, bump
`Settings.CURRENT_VERSION` and migrate in `SettingsStore`.

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
  responses, HTTP parsing, JSON encoding, the preferences file backend, and
  settings validation, migration and XML import). Client tests use a scratch
  settings file, never the real one.
- `tools/fake_pastes_server.py` mimics the real service's responses (Basic Auth,
  JSON schema check, name uniqueness, latin1 storage) over TLS, so the client
  can be exercised end to end without touching production:

      python3 tools/fake_pastes_server.py --cert cert.pem --key key.pem --port 9443

  In the client's Settings, set the host to `localhost`, the port to `9443` and
  the pin to the test key's pin, then use the tray client or `SENDFILE.BAT`.
  To keep test settings apart from real ones, start the client with
  `-Dpaste98.prefs.user=C:\TEST\PASTE98.PRF`.

## Status

Working on a ThinkPad T22 (Pentium III, Java 1.5.0_22, Windows 98): the tray
icon via `gwtray.dll`, pastes created on production over pinned TLS 1.2 with
the address copied to the clipboard, settings in `PASTE98.PRF` through the
Settings dialog, fetching and checking the server's key pin, and the
Appearance setting (version 0.4.0).

Also tested on JDK 8 / Linux: unit tests, the client end to end against the
fake server, and the Swing UI under Xvfb.

## Licence

Copyright (C) 2026 Glitch Works, LLC. GNU General Public License v3 or later;
see `LICENSE`. Bouncy Castle is under the MIT licence.
