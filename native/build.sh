#!/bin/sh
# Builds gwtray.dll for Windows 98 with Zig's bundled clang and lld, then
# checks the result with check_pe.py.
#
# Needs:
#   zig 0.13     pip install ziglang==0.13.0   (or set ZIG to a zig binary)
#   python3      with pefile and capstone: pip install pefile capstone
#   a JDK        for jni.h (JAVA_HOME; any JDK 8+ works)
#
# Output: native/prebuilt/gwtray.dll, which the client build picks up. The
# prebuilt DLL is committed so a normal Maven build does not need Zig.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/prebuilt/gwtray.dll"
: "${JAVA_HOME:?set JAVA_HOME to a JDK (for jni.h)}"

if [ -n "${ZIG:-}" ]; then
    :
elif command -v zig >/dev/null 2>&1; then
    ZIG=zig
else
    ZIG="python3 -m ziglang"
fi

ZIG_LIB=$($ZIG env | python3 -c 'import json,sys; print(json.load(sys.stdin)["lib_dir"])')
MINGW="$ZIG_LIB/libc/mingw"
WIN_INC="$ZIG_LIB/libc/include/any-windows-any"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$HERE/prebuilt"

# Import libraries from MinGW's i386 .def files. -k strips the @N stdcall
# decoration from the imported names, as the real Windows DLLs export them.
for lib in kernel32 user32 shell32; do
    $ZIG dlltool -m i386 -k -d "$MINGW/lib32/$lib.def" -D "$lib.dll" -l "$WORK/$lib.lib"
done

# Compile only. -mcpu=pentiumpro: plain i686 with no MMX/SSE, so it runs on
# any Windows 98 PC. No C runtime: gwtray.c provides its own entry point,
# memset and memcpy.
$ZIG cc -target x86-windows-gnu -mcpu=pentiumpro -O2 -c \
    -ffreestanding -fno-stack-protector -fno-builtin \
    -Wall -Wextra -Wno-unused-parameter -Wno-ignored-attributes \
    -I"$HERE/include/win32" -I"$JAVA_HOME/include" -isystem "$WIN_INC" \
    -o "$WORK/gwtray.o" "$HERE/gwtray.c"

# Link with lld-link directly (Zig's own link driver does not expose these):
#   /subsystem:windows,4.0 /osversion:4.0  Windows 98 refuses newer images
#   /export:Name=_Name@N                   undecorated JNI names for the JVM
#   /Brepro                                reproducible: no timestamp in the PE header
P=Java_com_glitchwrks_paste98_client_tray_NativeTray_
$ZIG lld-link /dll /machine:x86 /nodefaultlib /safeseh:no /Brepro \
    /entry:DllMainCRTStartup@12 /subsystem:windows,4.0 /osversion:4.0 \
    /out:"$OUT" "$WORK/gwtray.o" "$WORK/kernel32.lib" "$WORK/user32.lib" "$WORK/shell32.lib" \
    /export:${P}nativeVersion=_${P}nativeVersion@8 \
    /export:${P}nativeRun=_${P}nativeRun@24 \
    /export:${P}nativeSetTooltip=_${P}nativeSetTooltip@12 \
    /export:${P}nativeQuit=_${P}nativeQuit@8 \
    /export:${P}nativeRandom=_${P}nativeRandom@12

rm -f "$HERE/prebuilt/gwtray.lib" "$HERE/prebuilt/gwtray.exp" "$HERE/prebuilt/gwtray.pdb"

python3 "$HERE/check_pe.py" "$OUT"
