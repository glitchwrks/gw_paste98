#!/usr/bin/env python3
# Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
# Copyright (C) 2026 Glitch Works, LLC
# Licensed under the GNU GPL v3 or later. See the LICENSE file for details.
"""
Generates the tray icon from the pixel art below, with no dependencies:

  gwtray.ico   16x16 and 32x32, 4 bits per pixel (16 colours) with an AND
               mask, which every Windows 98 shell displays correctly (32-bit
               alpha icons are a Windows XP feature)
  gwtray.png   32x32 RGBA (Java 5 reads PNG, not ICO)
  gwtray16.png 16x16 RGBA, for window title bars: Java 5 takes one image per
               window and scales it to 16x16 itself, which blurs the 32x32

Usage: make_icon.py OUTPUT_DIR
"""
import os
import struct
import sys
import zlib

# Standard 16-colour Windows palette (index: (r, g, b)).
PALETTE = [
    (0, 0, 0), (128, 0, 0), (0, 128, 0), (128, 128, 0),
    (0, 0, 128), (128, 0, 128), (0, 128, 128), (192, 192, 192),
    (128, 128, 128), (255, 0, 0), (0, 255, 0), (255, 255, 0),
    (0, 0, 255), (255, 0, 255), (0, 255, 255), (255, 255, 255),
]

# A paste jar with its lid tipped open over a mound of paste, labelled
# with the Glitch Works badge (black disc, grey ring, green square wave; see
# art/glitchworks-logo.png and make_logo.py for the large version). Each size
# is drawn separately: scaling down loses the badge's wave.
# '.' transparent, K black, S silver (glass), G grey, W white (paste,
# highlights), R red / r dark red (lid), L bright green (the wave)
ART16 = [
    "........KKKKKKK.",
    "..KKKKKKRRRRRRK.",
    "..KWWRRRrrrrrrK.",
    "..KrrrrrKKKKKKK.",
    "..KKKKKKWWWWWWWK",
    "...KSSSSSSSSKKK.",
    "..KKKKKKKKKKKK..",
    ".KWSSSGGGGSSSSK.",
    ".KWSSGKKKKGSSSK.",
    ".KWSGKLLLKLLSSK.",
    ".KWSGKLKLKLGSSK.",
    ".KWSGKLKLKLGSSK.",
    ".KWSLLLKLLLGSSK.",
    ".KWSSGKKKKGSSSK.",
    ".KWSSSGGGGSSSSK.",
    "..KKKKKKKKKKKK..",
]

ART32 = [
    "................................",
    "....................K...........",
    ".................KKKK...........",
    "..............KKKRRRK...........",
    "...........KKKRRRRRRK...........",
    "........KKKRRRRRRrrrK...........",
    ".....KKKRRRRRRrrrKKK............",
    ".....KRRRRRrrrKKKWWWK...........",
    ".....KRRrrrKKKWWWWWWWK..........",
    ".....KrrKKKSWWWWWWWWWWK.........",
    ".....KKKKWWWWWWWWWWWWWWK........",
    ".......KKKKKKKKKKKKKKKKK........",
    ".........KSSSSSSSSSSSSK.........",
    ".........KGGGGGGGGGGGGK.........",
    ".......KKKKKKKKKKKKKKKKKK.......",
    "......KSSSSSSSGGGGSSSSSSSK......",
    ".....KSSSSSSGGKKKKGGSSSSSSK.....",
    "....KSSSSSSGKKKKKKKKGSSSSSSK....",
    "....KSSWSSGKKKKKKKKKKGSSSSSK....",
    "....KSSWSGKLLLLKKKLLLLLSSGSK....",
    "....KSSWSGKLKKLKKKLKKKGSSGSK....",
    "....KSSWGKKLKKLKKKLKKKKGSGSK....",
    "....KSSWGKKLKKLKLKLKKKKGSGSK....",
    "....KSSWGKKLKKLKLKLKKKKGSGSK....",
    "....KSSWGKKLKKLKLKLKKKKGSGSK....",
    "....KSSWSGKLKKLKLKLKKKGSSGSK....",
    "....KSSWSLLLKKLLLLLKKKGSSGSK....",
    "....KSSWSSGKKKKKKKKKKGSSSGSK....",
    "....KSSWSSSGKKKKKKKKGSSSSSSK....",
    ".....KSSSSSSGGKKKKGGSSSSSSK.....",
    "......KSSSSSSSGGGGSSSSSSSK......",
    ".......KKKKKKKKKKKKKKKKKK.......",
]

COLOURS = {"K": 0, "r": 1, "S": 7, "G": 8, "R": 9, "L": 10, "W": 15}


def ico_image(art):
    """One 4bpp ICO image: BITMAPINFOHEADER, palette, XOR bitmap, AND mask."""
    size = len(art)
    xor_row = ((size * 4 + 31) // 32) * 4
    and_row = ((size + 31) // 32) * 4
    xor = bytearray()
    mask = bytearray()
    for y in range(size - 1, -1, -1):  # bottom-up
        row = art[y]
        line = bytearray(xor_row)
        mline = bytearray(and_row)
        for x, ch in enumerate(row):
            idx = 0 if ch == "." else COLOURS[ch]
            if x % 2 == 0:
                line[x // 2] |= idx << 4
            else:
                line[x // 2] |= idx
            if ch == ".":
                mline[x // 8] |= 0x80 >> (x % 8)
        xor += line
        mask += mline
    header = struct.pack("<IiiHHIIiiII", 40, size, size * 2, 1, 4, 0, len(xor) + len(mask), 0, 0, 16, 0)
    pal = b"".join(struct.pack("<BBBB", b, g, r, 0) for (r, g, b) in PALETTE)
    return header + pal + bytes(xor) + bytes(mask)


def write_ico(path, arts):
    images = [ico_image(a) for a in arts]
    out = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    for art, img in zip(arts, images):
        size = len(art)
        out += struct.pack("<BBBBHHII", size % 256, size % 256, 16, 0, 1, 4, len(img), offset)
        offset += len(img)
    out += b"".join(images)
    with open(path, "wb") as f:
        f.write(out)


def write_png(path, art):
    size = len(art)
    raw = bytearray()
    for row in art:
        raw.append(0)
        for ch in row:
            if ch == ".":
                raw += b"\x00\x00\x00\x00"
            else:
                r, g, b = PALETTE[COLOURS[ch]]
                raw += bytes((r, g, b, 255))

    def chunk(kind, data):
        c = struct.pack(">I", len(data)) + kind + data
        return c + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main(outdir):
    for art, size in ((ART16, 16), (ART32, 32)):
        assert len(art) == size, size
        for row in art:
            assert len(row) == size, row
    art32 = ART32
    os.makedirs(outdir, exist_ok=True)
    write_ico(os.path.join(outdir, "gwtray.ico"), [ART16, art32])
    write_png(os.path.join(outdir, "gwtray.png"), art32)
    write_png(os.path.join(outdir, "gwtray16.png"), ART16)
    print("wrote gwtray.ico, gwtray.png and gwtray16.png to " + outdir)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: make_icon.py OUTPUT_DIR")
        sys.exit(2)
    main(sys.argv[1])
