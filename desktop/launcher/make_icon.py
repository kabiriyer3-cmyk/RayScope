#!/usr/bin/env python3
"""Draws the RayScope app icon: the white R lettermark on a graphite tile.

The mark is one continuous line drawn as an R (see shared/src/com/rayscope/fluid/Logo.kt, object RayLogo):
the same SVG path is embedded below. 16-32 px icons use the small optical cut (heavier line, wider gap).
Pillow only. The .ico is written by hand with PNG entries, so every size gets its own render.

Usage: make_icon.py OUT.ico [OUT.png]
"""
import io
import re
import struct
import sys

from PIL import Image, ImageDraw

PATH = "M31.89 79L40 33C40.49 30.24 43.12 28 45.89 28L63.39 28C66.42 28 68.45 30.46 67.92 33.5C67.38 36.54 64.48 39 61.45 39C55.53 39 49.52 42.26 45.82 47.49C42.12 52.71 41.34 59.04 43.78 63.95L53.23 82.91C54.83 86.12 59.03 86.97 62.62 84.8C66.2 82.64 67.82 78.29 66.22 75.09L59.69 61.97C58.84 60.27 58.94 58.12 59.97 56.18C60.99 54.24 62.82 52.74 64.89 52.13C74.61 49.28 81.97 40.12 82.21 30.59C82.45 21.05 75.5 14 65.85 14L48.35 14C37.86 14 27.86 22.51 26 33L17.89 79C17.21 82.87 19.79 86 23.66 86C27.53 86 31.21 82.87 31.89 79Z"
PATH_SMALL = "M30.52 78.25L38.37 33.75C38.78 31.4 41.02 29.5 43.37 29.5L65.37 29.5C67.99 29.5 69.74 31.63 69.28 34.25C68.82 36.87 66.32 39 63.69 39C57.5 39 51.21 42.42 47.34 47.89C43.47 53.35 42.64 59.98 45.2 65.12L53.9 82.58C55.67 86.13 60.32 87.07 64.29 84.68C68.26 82.28 70.05 77.46 68.28 73.92L62.91 63.13C62.08 61.46 62.16 59.36 63.13 57.45C64.1 55.54 65.85 54.02 67.87 53.36C77.76 50.09 85.04 40.58 85.09 30.85C85.15 21.12 77.97 14 68.1 14L46.1 14C35.19 14 24.79 22.84 22.87 33.75L15.02 78.25C14.27 82.53 17.13 86 21.41 86C25.69 86 29.77 82.53 30.52 78.25Z"

TILE = (15, 17, 21, 255)
INK = (244, 245, 247, 255)
EDGE = (255, 255, 255, 20)
SS = 4  # supersampling


def polygon(d):
    """Flattens an M/L/C/Z path into a list of points (100-unit box)."""
    toks = re.findall(r"[MLCZ]|-?\d*\.?\d+", d)
    pts, i, cur = [], 0, (0.0, 0.0)
    while i < len(toks):
        t = toks[i]
        if t in "ML":
            cur = (float(toks[i + 1]), float(toks[i + 2]))
            pts.append(cur)
            i += 3
        elif t == "C":
            p1 = (float(toks[i + 1]), float(toks[i + 2]))
            p2 = (float(toks[i + 3]), float(toks[i + 4]))
            p3 = (float(toks[i + 5]), float(toks[i + 6]))
            for k in range(1, 25):
                u = k / 24.0
                m = 1 - u
                pts.append((
                    m ** 3 * cur[0] + 3 * m * m * u * p1[0] + 3 * m * u * u * p2[0] + u ** 3 * p3[0],
                    m ** 3 * cur[1] + 3 * m * m * u * p1[1] + 3 * m * u * u * p2[1] + u ** 3 * p3[1],
                ))
            cur = p3
            i += 7
        else:
            i += 1
    return pts


def render(size):
    small = size <= 32
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = s * 0.03
    d.rounded_rectangle([pad, pad, s - pad, s - pad], radius=s * 0.23, fill=TILE, outline=EDGE if not small else None,
                        width=max(1, s // 256))
    # glyph is 72 units tall, centred on (50, 50); it fills 56 % of the tile (62 % in the small cut)
    k = s * (0.62 if small else 0.56) / 72.0
    pts = [(s / 2 + (x - 50) * k, s / 2 + (y - 50) * k) for x, y in polygon(PATH_SMALL if small else PATH)]
    d.polygon(pts, fill=INK)
    lanczos = getattr(getattr(Image, "Resampling", Image), "LANCZOS")
    return img.resize((size, size), lanczos)


def write_ico(path, sizes):
    blobs = []
    for n in sizes:
        b = io.BytesIO()
        render(n).save(b, "PNG")
        blobs.append((n, b.getvalue()))
    head = struct.pack("<HHH", 0, 1, len(blobs))
    offset = 6 + 16 * len(blobs)
    entries, data = b"", b""
    for n, png in blobs:
        dim = 0 if n >= 256 else n
        entries += struct.pack("<BBBBHHII", dim, dim, 0, 0, 1, 32, len(png), offset + len(data))
        data += png
    with open(path, "wb") as f:
        f.write(head + entries + data)


def main():
    if len(sys.argv) < 2:
        print("usage: make_icon.py OUT.ico [OUT.png]")
        sys.exit(2)
    write_ico(sys.argv[1], [16, 20, 24, 32, 40, 48, 64, 128, 256])
    if len(sys.argv) > 2:
        render(512).save(sys.argv[2])
    print("icon written:", sys.argv[1])


if __name__ == "__main__":
    main()
