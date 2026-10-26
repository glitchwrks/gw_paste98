#!/usr/bin/env python3
# gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
# Copyright (C) 2026 Glitch Works, LLC
# Licensed under the GNU GPL v3 or later. See the LICENSE file for details.
"""
Checks (and if necessary patches) gwtray.dll for Windows 98 on a Pentium-class CPU.

Fails, with a reason, if the DLL:
  - is not a 32-bit x86 DLL
  - is marked for a Windows version newer than 4.0 (patched to 4.0 instead)
  - imports anything other than the Windows 98 functions listed below
  - lacks the undecorated JNI exports the JVM looks up
  - contains MMX/SSE/AVX instructions (the code must run on any i686)
  - has a TLS directory (unreliable in DLLs loaded with LoadLibrary on 9x)
  - has no relocation table (needed if it cannot load at its preferred base)

Needs: pip install pefile capstone
"""
import hashlib
import sys

import capstone
import pefile

# Every function gwtray.c may import. All are exported by the Windows 98
# KERNEL32, USER32 and SHELL32 (ANSI variants only).
ALLOWED_IMPORTS = {
    "kernel32.dll": {
        "InitializeCriticalSection", "DeleteCriticalSection",
        "EnterCriticalSection", "LeaveCriticalSection",
        "InterlockedExchange", "lstrcpynA",
        # nativeRandom finds CryptoAPI at run time (absent on early Windows 95)
        "LoadLibraryA", "GetProcAddress", "FreeLibrary",
    },
    "user32.dll": {
        "RegisterClassA", "CreateWindowExA", "DestroyWindow", "DefWindowProcA",
        "GetMessageA", "TranslateMessage", "DispatchMessageA", "PostMessageA",
        "PostQuitMessage", "RegisterWindowMessageA", "LoadImageA", "LoadIconA",
        "DestroyIcon", "CreatePopupMenu", "AppendMenuA", "SetMenuDefaultItem",
        "TrackPopupMenu", "DestroyMenu", "GetCursorPos", "SetForegroundWindow",
    },
    "shell32.dll": {
        "Shell_NotifyIconA",
    },
}

_P = "Java_com_glitchwrks_paste98_client_tray_NativeTray_"
# Export name -> bytes of arguments (JNIEnv*, jclass, then the Java arguments).
EXPORT_ARG_BYTES = {
    _P + "nativeVersion": 8,
    _P + "nativeRun": 24,
    _P + "nativeSetTooltip": 12,
    _P + "nativeQuit": 8,
    _P + "nativeRandom": 12,
}
REQUIRED_EXPORTS = set(EXPORT_ARG_BYTES)

FORBIDDEN_GROUPS = {
    getattr(capstone.x86, name) for name in dir(capstone.x86)
    if name.startswith("X86_GRP_") and any(k in name for k in ("SSE", "MMX", "AVX", "3DNOW", "FMA"))
}


def fail(msg):
    print("FAIL: " + msg)
    sys.exit(1)


def main(path):
    pe = pefile.PE(path)

    if pe.FILE_HEADER.Machine != pefile.MACHINE_TYPE["IMAGE_FILE_MACHINE_I386"]:
        fail("not a 32-bit x86 image")
    if not pe.is_dll():
        fail("not a DLL")

    oh = pe.OPTIONAL_HEADER
    patched = []
    for field in ("MajorOperatingSystemVersion", "MajorSubsystemVersion"):
        if getattr(oh, field) != 4:
            patched.append("%s %d -> 4" % (field, getattr(oh, field)))
            setattr(oh, field, 4)
    for field in ("MinorOperatingSystemVersion", "MinorSubsystemVersion"):
        if getattr(oh, field) != 0:
            patched.append("%s %d -> 0" % (field, getattr(oh, field)))
            setattr(oh, field, 0)
    if patched:
        oh.CheckSum = pe.generate_checksum()
        data = pe.write()
        pe.close()
        with open(path, "wb") as f:
            f.write(data)
        pe = pefile.PE(path)
        oh = pe.OPTIONAL_HEADER
        print("patched: " + ", ".join(patched))

    print("OS version %d.%d, subsystem version %d.%d, image base 0x%08x, entry 0x%x" % (
        oh.MajorOperatingSystemVersion, oh.MinorOperatingSystemVersion,
        oh.MajorSubsystemVersion, oh.MinorSubsystemVersion, oh.ImageBase, oh.AddressOfEntryPoint))
    if oh.AddressOfEntryPoint == 0:
        fail("no entry point")
    if oh.SectionAlignment != 0x1000 or oh.FileAlignment != 0x200:
        fail("unusual alignment: section 0x%x file 0x%x" % (oh.SectionAlignment, oh.FileAlignment))

    # Imports
    imports = {}
    for entry in getattr(pe, "DIRECTORY_ENTRY_IMPORT", []):
        dll = entry.dll.decode("ascii").lower()
        names = set()
        for imp in entry.imports:
            if imp.name is None:
                fail("import by ordinal from %s" % dll)
            names.add(imp.name.decode("ascii"))
        imports[dll] = names
    for dll, names in sorted(imports.items()):
        allowed = ALLOWED_IMPORTS.get(dll)
        if allowed is None:
            fail("unexpected DLL import: %s (%s)" % (dll, ", ".join(sorted(names))))
        extra = names - allowed
        if extra:
            fail("unexpected imports from %s: %s" % (dll, ", ".join(sorted(extra))))
        print("imports %-12s %s" % (dll, ", ".join(sorted(names))))
    if hasattr(pe, "DIRECTORY_ENTRY_DELAY_IMPORT"):
        fail("delay-load imports present")

    # Exports
    exports = set()
    for sym in pe.DIRECTORY_ENTRY_EXPORT.symbols:
        if sym.name:
            exports.add(sym.name.decode("ascii"))
    missing = REQUIRED_EXPORTS - exports
    if missing:
        fail("missing exports: %s (have: %s)" % (", ".join(sorted(missing)), ", ".join(sorted(exports))))
    decorated = [e for e in exports if "@" in e]
    if decorated:
        fail("decorated exports the JVM will not find: " + ", ".join(decorated))
    print("exports %s" % ", ".join(sorted(exports)))

    # Other directories
    if pe.OPTIONAL_HEADER.DATA_DIRECTORY[pefile.DIRECTORY_ENTRY["IMAGE_DIRECTORY_ENTRY_TLS"]].VirtualAddress:
        fail("TLS directory present")
    reloc = pe.OPTIONAL_HEADER.DATA_DIRECTORY[pefile.DIRECTORY_ENTRY["IMAGE_DIRECTORY_ENTRY_BASERELOC"]]
    if not reloc.VirtualAddress or not reloc.Size:
        fail("no relocation table")

    # Instructions: decode every executable section linearly.
    md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_32)
    md.detail = True
    count = 0
    bad = []
    for section in pe.sections:
        if not section.Characteristics & 0x20000000:  # IMAGE_SCN_MEM_EXECUTE
            continue
        code = section.get_data()[: section.Misc_VirtualSize]
        base = oh.ImageBase + section.VirtualAddress
        for insn in md.disasm(code, base):
            count += 1
            groups = set(insn.groups)
            regs = insn.op_str
            if groups & FORBIDDEN_GROUPS or "xmm" in regs or "ymm" in regs or "mm" in regs.split(",")[0].strip():
                bad.append("0x%08x %s %s" % (insn.address, insn.mnemonic, insn.op_str))
    if bad:
        fail("MMX/SSE/AVX instructions found:\n  " + "\n  ".join(bad[:20]))
    print("code: %d instructions decoded, none MMX/SSE/AVX" % count)

    # stdcall: each export must pop exactly its argument bytes, or the JVM's
    # stack is corrupted on the first call. Scan each function up to the next
    # known function start and require the expected "ret N" to be present.
    image = pe.get_memory_mapped_image()
    starts = sorted([s.address for s in pe.DIRECTORY_ENTRY_EXPORT.symbols] + [oh.AddressOfEntryPoint])
    expected = dict((name, size) for name, size in EXPORT_ARG_BYTES.items())
    expected["<entry>"] = 12
    targets = [(s.name.decode("ascii"), s.address) for s in pe.DIRECTORY_ENTRY_EXPORT.symbols]
    targets.append(("<entry>", oh.AddressOfEntryPoint))
    for name, rva in targets:
        later = [s for s in starts if s > rva]
        end = later[0] if later else rva + 0x2000
        rets = set()
        for insn in md.disasm(image[rva:end], oh.ImageBase + rva):
            if insn.mnemonic == "ret":
                rets.add(int(insn.op_str, 16) if insn.op_str else 0)
        want = expected.get(name)
        if want is None:
            continue
        if want not in rets:
            fail("%s: expected 'ret %d', found %s" % (name, want, sorted(rets)))
    print("stdcall argument sizes OK for %d functions" % len(targets))

    with open(path, "rb") as f:
        digest = hashlib.sha256(f.read()).hexdigest()
    print("OK %s (%d bytes) sha256 %s" % (path, len(pe.__data__), digest))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: check_pe.py gwtray.dll")
        sys.exit(2)
    main(sys.argv[1])
