# Paste Jar

A Windows 95/98 tray client, running on Java 1.3 or later, for the
[Glitch Works pastes service](https://github.com/glitchwrks/pastes), using
modern TLS on a period machine. Called gw_paste98 up to version 0.8.1.

This is a hobbyist proof of concept exploring how far a legacy platform can be
brought up to an adequate level of encrypted communication with a modern
service, with an LLM doing much of the work.

## How it works

- **One build for Java 1.3 and later.** Java 1.3.1 is the newest Java for
  Windows 95, Java 5 the newest for Windows 98. The program is compiled for
  Java 1.3 and uses newer features (the Ocean theme, Socket connect timeouts,
  Java's own seeding of `SecureRandom`) at run time when they exist.
- **TLS 1.2 in pure Java.** Java 1.3 has no TLS at all and Java 5's stops at
  TLS 1.0, so the client uses Bouncy Castle's TLS client with its lightweight
  crypto, avoiding JCE and its policy limits. Bouncy Castle's oldest builds
  (`jdk14`) are Java 1.4 bytecode, so the build retargets them to Java 1.3
  (`retro13/.../Retarget13`): `java.util.logging` and `Destroyable`, which
  Java 1.3 lacks, become small stand-ins in `core/.../compat13` (BC's log
  messages go to the program's log); exception causes (`initCause` and the
  cause-taking constructors, Java 1.4) are recorded in a side table so logs
  still show them; and field stores javac 1.4 places before `super()`, which
  Java 1.3's verifier rejects, are moved after it. `Check13` then fails the
  build unless every class in the shipped jar is Java 1.3 bytecode using only
  the Java 1.3 API, and `Since13` gives a second opinion from the `@since`
  tags in the JDK's own source: animal-sniffer's java13 signature also lists
  non-public members, which let `FileOutputStream(File, boolean)` (public
  only from 1.4) into 0.8.0, where it stopped the program at start-up.
- **Random numbers without the half-minute wait.** Java 1.3 seeds
  `SecureRandom` by timing threads, which took 33 s on the T22. The client
  seeds it instead with SHA-256 of Windows' `CryptGenRandom` (via
  `gwtray.dll`), a seed file saved at exit, Java's own seeding on Java 5+, and
  timing jitter (`core/.../tls/Randomness`).
- **Pinned server key.** The server is authenticated by a SHA-256 pin of its
  public key, not by a certificate authority. Old Javas' CA lists are irrelevant,
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
- **Tray icon.** Java before 6 has no tray API, so `native/gwtray.c` provides one as a
  ~9 KB JNI DLL with no C runtime, importing only Windows 95 functions. If it
  cannot be used, the client falls back to a small Swing window.
- **Warm-up.** The tray client does one throwaway handshake in the background
  at launch, so the slow first handshake happens while you are typing.
- **Looks like Java on purpose.** The default is Swing's classic Metal look.
  Java cannot draw Windows 98's bitmap UI font (MS Sans Serif), so the Windows
  look always shows slightly different text; it is available as a setting,
  substituting Microsoft Sans Serif or Tahoma when installed.
- **Settings without the registry.** Everything is stored in `PASTE98.PRF`
  in the program folder (`client/.../prefs`). Up to 0.7 this was a
  `java.util.prefs` backend; Java 1.3 has no `java.util.prefs`, so 0.8 reads
  and writes the same file directly, and exports and imports the same
  preferences XML with a small parser of its own (tested against the JDK's).

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
thing from the Windows 9x machine. **Neither verifies anything**: both report
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
| `retro13/` | Build tools: `Retarget13` (Bouncy Castle's Java 1.4 jars to Java 1.3), `Check13` (verifies a jar against the Java 1.3 API signature) and `Since13` (the same against the JDK source's `@since` tags) |
| `core/` | Pinned TLS 1.2 client (`tls`), pastes API client (`api`) and the Java 1.3 stand-ins (`compat13`); shared |
| `client/` | Swing tray application; builds `client/target/pastejar-win9x.zip`, the help `GWPASTE.CHM` (sources in `client/src/help/`) and the installer `client/target/pastejar-VERSION-setup.exe` (script in `client/src/installer/`) |
| `bench/` | `pastebench`, the Java 5 TLS benchmark used to tune the client; builds `bench/target/pastebench-win98.zip` |
| `bench13/` | `pastebench13`, the same pinned TLS 1.2 handshake on Java 1.3 (the newest for Windows 95), with Bouncy Castle's Java 1.4 build retargeted to 1.3 at build time; builds `bench13/target/pastebench13-win9x.zip` |
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
produce Java 1.3 class files (`-source 1.3 -target 1.3`). Tests are compiled
for Java 5 and run on the build JDK.

    JAVA_HOME=/path/to/jdk8 mvn package

This compiles the program for Java 1.3, runs the unit tests, retargets Bouncy
Castle, shrinks the jars with ProGuard, fails the build if `GWPASTE.JAR` needs
anything newer than Java 1.3 (`Check13`, against animal-sniffer's java13
signature) and assembles the zips.

The help file is compiled HTML Help (`.CHM`), the format Windows 98's own
help uses, built with **chmcmd** from Free Pascal (`apt install fp-utils`, or
pass `-Dchmcmd=/path/to/chmcmd`). If **NSIS 3** is also installed
(`apt install nsis`, or pass `-Dmakensis=/path/to/makensis`), the build makes
the Windows installer. Without either tool, the build warns and leaves out
what it cannot make. `HelpTest` fails the build if a setting is missing from
its Settings tab's help page or a help link is broken. The installer is built as ANSI
(`Unicode false`) because NSIS's Unicode installers do not run on Windows
95/98/ME, and makensis warnings fail the build. It holds an uncompressed copy
of `GWPASTE.JAR` so its solid LZMA compression covers the whole jar, making
the installer (~680 KB) about half the size of the zip.

The Maven build uses the committed `native/prebuilt/gwtray.dll`. To rebuild
the DLL (Zig 0.13 as the cross-compiler, plus Python with `pefile` and
`capstone`):

    pip install ziglang==0.13.0 pefile capstone
    JAVA_HOME=/path/to/jdk native/build.sh

`native/build.sh` is reproducible (byte-identical output) and runs
`native/check_pe.py`, which fails the build if the DLL is marked for a Windows
newer than 4.0, imports anything Windows 95 lacks (CryptoAPI is loaded at run time, if present), uses MMX/SSE instructions,
or has wrong JNI export names or stdcall argument sizes.

## Testing

- `mvn package` runs the unit tests (API client logic against canned
  responses, HTTP parsing, JSON encoding, the settings file, preferences XML
  compatibility with the JDK's `java.util.prefs`, settings validation,
  migration and XML import, the retargeting of a sample compiled for Java 1.4,
  and the Java 1.3 code paths for connecting and seeding). Client tests use a
  scratch settings file, never the real one.
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

The installer (0.5.1) installs and uninstalls correctly on the T22, and is
also tested under Wine in Windows 98 mode (Java detection, upgrade, the
running-program check). 0.5.0 created no Start Menu items on real Windows 98,
where it used the All Users Start Menu; 0.5.1 uses the current user's on
Windows 9x.

0.6.0 adds the help file (`GWPASTE.CHM`), opened from the tray menu, the
Settings window and the Start Menu, replacing the installed `README.TXT`.
On the T22, 0.6.0's installer crashed Windows (fatal exception 0E in the
ENABLE VxD) on moving to its Finish page, with or without the page's
checkboxes; 0.6.1 has no Finish page and offers "Start gw_paste98 when setup
closes" on the components page instead. The help's Contents tab was empty in
Windows 98's viewer until 0.6.2 wrote the contents file in HTML Help
Workshop's layout with CRLF line endings. 0.6.2 is verified on the T22:
upgrade over 0.5.1/0.6.x with settings kept, and the help's Contents, Index
and per-tab Help buttons.

0.7.0 replaces the clipboard icon with a paste jar, its lid askew, labelled
with the Glitch Works badge: hand-drawn 16-colour pixel art at 16x16 and
32x32 for the tray (`native/make_icon.py`), and a full-colour 64x64 version
in the About window (`native/make_logo.py`).

0.7.2 is the last release for Java 5 only (branch `java5-0.7.x`). 0.8.0 runs on
Java 1.3 and later, and so on Windows 95. `pastebench13` showed Bouncy Castle's
TLS 1.2 handshake working on Java 1.3.1 on the T22 (about 0.85 s for the first
handshake, 0.3 s after). 0.8.1 is verified on the T22 with Java 1.3.1,
seeding from Windows' `CryptGenRandom`.

0.9.0 renames the program from gw_paste98 to Paste Jar (repository
`glitchwrks/pastejar`). It installs to `Program Files\Paste Jar`, and its
installer moves an existing gw_paste98 install over: settings, key pins, seed
file and the Start with Windows choice, then removes the old program files,
Start Menu items and Add/Remove entry. The 8.3 file names (`GWPASTE.JAR`,
`PASTE98.PRF` and so on) and the Java packages are unchanged.

## Licence

Copyright (C) 2026 Glitch Works, LLC. GNU General Public License v3 or later;
see `LICENSE`. Bouncy Castle is under the MIT licence.
