#!/usr/bin/env python3
"""Draws the RayScope app icon: a geometric R on a graphite tile.

The R is three flat planes with a thin gap between them: the bowl, the stem and
the leg. The leg is the "ray" and carries the accent colour. Same geometry as
the Android launcher icon (app/src/main/res/drawable/ic_launcher_foreground.xml).

Usage: make_icon.py OUT.ico [OUT.png]
"""
import sys

from PIL import Image, ImageDraw

S = 1024
TILE = (17, 18, 21, 255)
INK = (244, 244, 245, 255)
RAY = (124, 132, 255, 255)

# mark in a 100-unit box (77 wide): bowl, stem, leg
BOWL_W, BOWL_R = 44, 25
STEM = [(0, 56), (25, 56), (25, 100), (0, 100)]
LEG = [(31, 56), (58, 56), (77, 100), (50, 100)]


def render():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = 40
    d.rounded_rectangle([pad, pad, S - pad, S - pad], radius=220, fill=TILE)

    k = S * 0.56 / 100
    ox = (S - 77 * k) / 2
    oy = (S - 100 * k) / 2

    def pt(x, y):
        return (ox + x * k, oy + y * k)

    d.rectangle([pt(0, 0), pt(BOWL_W, 50)], fill=INK)
    cx, cy = pt(BOWL_W, BOWL_R)
    r = BOWL_R * k
    d.pieslice([cx - r, cy - r, cx + r, cy + r], start=-90, end=90, fill=INK)
    d.polygon([pt(*p) for p in STEM], fill=INK)
    d.polygon([pt(*p) for p in LEG], fill=RAY)
    return img


def main():
    if len(sys.argv) < 2:
        print("usage: make_icon.py OUT.ico [OUT.png]")
        sys.exit(2)
    big = render()
    lanczos = getattr(getattr(Image, "Resampling", Image), "LANCZOS")
    sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    big.resize((256, 256), lanczos).save(sys.argv[1], sizes=sizes)
    if len(sys.argv) > 2:
        big.resize((512, 512), lanczos).save(sys.argv[2])
    print("icon written:", sys.argv[1])


if __name__ == "__main__":
    main()
