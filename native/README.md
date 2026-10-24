# gwtray.dll

A notification area icon for Java 5 on Windows 98, through JNI. Java 5 has no
tray API (`java.awt.SystemTray` arrived in Java 6).

| File | What |
|---|---|
| `gwtray.c` | The DLL. Java side: `client/.../tray/NativeTray.java` |
| `include/win32/jni_md.h` | 32-bit Windows JNI types, for cross-compiling against a Linux JDK's `jni.h` |
| `build.sh` | Builds `prebuilt/gwtray.dll` with Zig's clang and lld, then runs `check_pe.py` |
| `check_pe.py` | Checks the DLL is loadable on Windows 98 by a Pentium-class CPU |
| `make_icon.py` | Generates `prebuilt/gwtray.ico` (16-colour, 16x16 and 32x32, each hand-drawn pixel art) and `prebuilt/gwtray.png`; no dependencies |
| `make_logo.py` | Draws the large full-colour icon, `prebuilt/gwabout.png` (64x64, the About window) and `prebuilt/gwpaste128.png`; needs Pillow |
| `art/glitchworks-logo.png` | The Glitch Works badge (www.glitchwrks.com favicon art, 256x256), used as the jar's label |
| `prebuilt/` | Committed outputs, so the Maven build does not need Zig |

## Windows 98 constraints

- ANSI ("A") functions only, and only ones Windows 98 exports. `check_pe.py`
  has the allowed list and fails on any other import.
- No message-only window (`HWND_MESSAGE` is Windows 2000+): a hidden `WS_POPUP`
  window receives the tray messages.
- The original 88-byte `NOTIFYICONDATA`, which the Windows 98 shell expects.
- No C runtime: the DLL has its own entry point, `memset` and `memcpy`.
- PE header marked for Windows 4.0 (Windows 98 refuses newer).
- Plain i686 code (`-mcpu=pentiumpro`): no MMX or SSE.
- Undecorated JNI export names (`Java_..._nativeRun`, not `...@24`), which is
  what the JVM looks up.

## Threading

`NativeTray.start()` runs `nativeRun()` on a dedicated Java thread, which
creates the icon and runs the Win32 message loop until `nativeQuit()`. The DLL
calls `NativeTray.onEvent(int)` back on that same thread, so no JNI thread
attaching is needed; `onEvent` hands events to Swing's event thread.

Menu labels are passed in from Java; "-" is a separator. The icon is re-added
if Explorer restarts (`TaskbarCreated`).

## Building

    pip install ziglang==0.13.0 pefile capstone
    JAVA_HOME=/path/to/any/jdk native/build.sh
    python3 native/make_icon.py native/prebuilt     # only if the icon art changes
    python3 native/make_logo.py native/prebuilt     # only if the large icon changes

Rebuilds are byte-identical (`/Brepro`).
