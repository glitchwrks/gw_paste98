#!/usr/bin/env python3
# gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
# Copyright (C) 2026 Glitch Works, LLC
# Licensed under the GNU GPL v3 or later. See the LICENSE file for details.
"""
Draws the large, full-colour version of the program's icon: a paste jar with
its lid tipped open over a mound of paste, labelled with the Glitch Works
badge (art/glitchworks-logo.png, the 256x256 www.glitchwrks.com favicon art).
The tray sizes are hand-drawn pixel art in make_icon.py, in the same design.

  gwabout.png    64x64 RGBA, shown in the About window
  gwpaste128.png 128x128 RGBA, for documentation and anything larger

Needs Pillow. Usage: make_logo.py OUTPUT_DIR
"""
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SS = 8  # supersampling factor; drawn on a 128-unit grid

K = (20, 20, 20, 255)
GLASS = (198, 212, 218, 255)
GLASS_SHADE = (166, 182, 190, 255)
GLASS_HIGHLIGHT = (246, 250, 252, 255)
PASTE = (248, 246, 236, 255)
PASTE_SHADE = (222, 218, 200, 255)
LID_TOP = (206, 34, 42, 255)
LID_SIDE = (150, 22, 28, 255)
LID_RIBS = (110, 14, 20, 255)
LID_HIGHLIGHT = (242, 110, 114, 255)


def p(v):
    return int(round(v * SS))


def draw():
    n = 128 * SS
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    # Jar body: glass with a highlight on the left and shade on the right.
    d.rounded_rectangle([p(18), p(54), p(110), p(124)], radius=p(17), fill=K)
    d.rounded_rectangle([p(22), p(58), p(106), p(120)], radius=p(13), fill=GLASS)
    d.rounded_rectangle([p(92), p(62), p(102), p(116)], radius=p(5), fill=GLASS_SHADE)
    d.rounded_rectangle([p(27), p(64), p(33), p(112)], radius=p(3), fill=GLASS_HIGHLIGHT)

    # Paste heaped in the open mouth (the neck, drawn next, hides its base).
    d.ellipse([p(36), p(22), p(92), p(62)], fill=K)
    d.ellipse([p(40), p(26), p(88), p(60)], fill=PASTE)
    d.ellipse([p(48), p(30), p(60), p(36)], fill=(255, 255, 255, 255))
    d.arc([p(40), p(26), p(88), p(60)], start=300, end=360, fill=PASTE_SHADE, width=p(3))

    # Neck, with a thread.
    d.rectangle([p(34), p(40), p(94), p(58)], fill=K)
    d.rectangle([p(38), p(44), p(90), p(55)], fill=GLASS)
    d.rectangle([p(38), p(49), p(90), p(51)], fill=GLASS_SHADE)

    # Lid, tilted 16 degrees about its bottom-left corner, which rests on the rim.
    ang = math.radians(20)
    ox, oy = 30.0, 42.0

    def lid(points, fill):
        out = []
        for x, y in points:
            dx, dy = x - ox, y - oy
            out.append((p(ox + dx * math.cos(ang) + dy * math.sin(ang)),
                        p(oy - dx * math.sin(ang) + dy * math.cos(ang))))
        d.polygon(out, fill=fill)

    lid([(30, 42), (100, 42), (100, 20), (30, 20)], K)
    lid([(33, 39), (97, 39), (97, 30), (33, 30)], LID_SIDE)
    lid([(33, 30), (97, 30), (97, 23), (33, 23)], LID_TOP)
    lid([(36, 25), (70, 25), (70, 27), (36, 27)], LID_HIGHLIGHT)
    for x in range(37, 97, 5):
        lid([(x, 31), (x + 2, 31), (x + 2, 38), (x, 38)], LID_RIBS)

    # Label: the Glitch Works badge.
    logo = Image.open(os.path.join(HERE, "art", "glitchworks-logo.png")).convert("RGBA")
    im.alpha_composite(logo.resize((p(64), p(64)), Image.LANCZOS), (p(32), p(58)))
    return im


def main(outdir):
    os.makedirs(outdir, exist_ok=True)
    big = draw()
    for size, name in ((64, "gwabout.png"), (128, "gwpaste128.png")):
        big.resize((size, size), Image.LANCZOS).save(os.path.join(outdir, name), optimize=True)
    print("wrote gwabout.png and gwpaste128.png to " + outdir)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: make_logo.py OUTPUT_DIR")
        sys.exit(2)
    main(sys.argv[1])
