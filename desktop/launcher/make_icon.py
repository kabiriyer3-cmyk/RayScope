#!/usr/bin/env python3
"""Draws the RayScope app icon: the R lettermark on a deep navy tile.

Geometric monogram on a 100-unit grid: a heavy P-shaped body with a round
counter, plus a separate leg (the "ray") with a periwinkle-to-cyan gradient.
Same geometry as shared/src/com/rayscope/fluid/Fluid.kt (object Mark) and the
Android launcher icon. Pillow only, no numpy.

Usage: make_icon.py OUT.ico [OUT.png]
"""
import math
import sys

from PIL import Image, ImageDraw

S = 1024
PAD = 32
RADIUS = 236
TILE_FROM = (35, 42, 82)
TILE_TO = (11, 14, 26)
INK = (244, 246, 255)
RAY_FROM = (154, 166, 255)
RAY_TO = (79, 214, 255)

# mark geometry (units), bounding box x 16..86, y 12..88, centre (51, 50)
BOWL_C, BOWL_R = (50.0, 38.0), 26.0
COUNTER_R = 9.5
RAY = [(46, 69), (68, 69), (86, 88), (63, 88)]
MARK_H = 76.0


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def diagonal_mask(size):
    """L image whose value grows from the top-left corner (0) to the bottom-right (255)."""
    row = bytes(int(i * 255 / (2 * size - 2)) for i in range(2 * size - 1))
    return Image.frombytes("L", (size, size), b"".join(row[y:y + size] for y in range(size)))


def body_points(pt):
    pts = [pt(16, 12), pt(50, 12)]
    for i in range(1, 64):
        a = -math.pi / 2 + math.pi * i / 64
        pts.append(pt(BOWL_C[0] + BOWL_R * math.cos(a), BOWL_C[1] + BOWL_R * math.sin(a)))
    pts += [pt(50, 64), pt(40, 64), pt(40, 88), pt(16, 88)]
    return pts


def render():
    tile = S - 2 * PAD
    k = tile * 0.56 / MARK_H
    ox = S / 2 - 51 * k
    oy = S / 2 - 50 * k

    def pt(x, y):
        return (ox + x * k, oy + y * k)

    # tile: diagonal navy gradient inside a rounded square
    tile_mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(tile_mask).rounded_rectangle([PAD, PAD, S - PAD, S - PAD], radius=RADIUS, fill=255)
    grad = Image.composite(Image.new("RGB", (S, S), TILE_TO), Image.new("RGB", (S, S), TILE_FROM), diagonal_mask(S))
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    img.paste(grad, (0, 0), tile_mask)

    # body with the counter cut out
    body = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(body)
    d.polygon(body_points(pt), fill=255)
    cx, cy = pt(*BOWL_C)
    r = COUNTER_R * k
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=0)
    img.paste(Image.new("RGB", (S, S), INK), (0, 0), body)

    # the ray: gradient along (46,69) -> (86,88)
    ray_mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(ray_mask).polygon([pt(*p) for p in RAY], fill=255)
    x0, y0 = pt(46, 69)
    x1, y1 = pt(86, 88)
    dx, dy = x1 - x0, y1 - y0
    dd = dx * dx + dy * dy
    left, top, right, bottom = [int(v) for v in ray_mask.getbbox()]
    w, h = right - left, bottom - top
    ray = Image.new("RGB", (w, h))
    px = ray.load()
    for j in range(h):
        for i in range(w):
            t = ((left + i - x0) * dx + (top + j - y0) * dy) / dd
            px[i, j] = lerp(RAY_FROM, RAY_TO, min(1.0, max(0.0, t)))
    img.paste(ray, (left, top), ray_mask.crop((left, top, right, bottom)))
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
