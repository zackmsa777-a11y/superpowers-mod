"""Draws the power tome item texture and the mod/instance icon.

usage: python make_textures.py <assets/superpowers dir>
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

TOME = [
    "................",
    "...OOOOOOOOOOO..",
    "..OSGGGGGGGGGGO.",
    "..OSGcccccccccGO",
    "..OSGccccYccccGO",
    "..OSGcccYEYcccGO",
    "..OSGccYEeEYccGO",
    "..OSGcYEeWeEYcGO",
    "..OSGccYEeEYccGO",
    "..OSGcccYEYcccGO",
    "..OSGccccYccccGO",
    "..OSGcccccccccGO",
    "..OSGGGGGGGGGGGO",
    "..OSPPPPPPPPPPPO",
    "...OOOOOOOOOOOO.",
    "................",
]

PALETTE = {
    "O": (24, 10, 36, 255),     # outline
    "S": (60, 22, 82, 255),     # spine
    "G": (236, 182, 52, 255),   # gold trim
    "c": (92, 36, 140, 255),    # cover
    "Y": (255, 214, 90, 255),   # emblem gold
    "E": (255, 120, 40, 255),   # ember ring
    "e": (190, 90, 255, 255),   # void ring
    "W": (255, 255, 240, 255),  # gem core
    "P": (232, 222, 196, 255),  # pages
}


def tome(path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(TOME):
        for x, ch in enumerate(row):
            if ch in PALETTE:
                r, g, b, a = PALETTE[ch]
                if ch == "c":  # diagonal sheen on the cover
                    k = 1.0 + 0.18 * math.cos((x + y) * 0.6)
                    r, g, b = (min(255, int(v * k)) for v in (r, g, b))
                img.putpixel((x, y), (r, g, b, a))
    img.save(path)


POWER_COLORS = [
    (255, 110, 20),   # fire
    (150, 70, 255),   # space
    (200, 240, 255),  # angel
    (220, 20, 50),    # demon
    (255, 200, 40),   # god
]


def icon(path, size=256):
    s = size
    bg = Image.new("RGBA", (s, s))
    d = ImageDraw.Draw(bg)
    for r in range(s // 2 + 40, 0, -2):
        k = r / (s / 2 + 40)
        col = (int(40 * (1 - k) + 8), int(10 * (1 - k) + 6), int(70 * (1 - k) + 14), 255)
        d.ellipse([s / 2 - r, s / 2 - r, s / 2 + r, s / 2 + r], fill=col)

    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    cx = cy = s / 2
    ring_r = s * 0.33
    g.ellipse([cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r], outline=(255, 220, 120, 255), width=max(2, s // 64))
    orbs = []
    for i, col in enumerate(POWER_COLORS):
        a = -math.pi / 2 + i * 2 * math.pi / 5
        ox, oy = cx + math.cos(a) * ring_r, cy + math.sin(a) * ring_r
        orbs.append((ox, oy, col))
        rr = s * 0.085
        g.ellipse([ox - rr, oy - rr, ox + rr, oy + rr], fill=col + (255,))
    # central four-point star
    R, r = s * 0.2, s * 0.045
    pts = []
    for i in range(8):
        a = -math.pi / 2 + i * math.pi / 4
        rad = R if i % 2 == 0 else r
        pts.append((cx + math.cos(a) * rad, cy + math.sin(a) * rad))
    g.polygon(pts, fill=(255, 250, 230, 255))

    blurred = glow.filter(ImageFilter.GaussianBlur(s / 28))
    out = Image.alpha_composite(bg, blurred)
    out = Image.alpha_composite(out, blurred)
    out = Image.alpha_composite(out, glow)
    core = ImageDraw.Draw(out)
    for ox, oy, col in orbs:
        rr = s * 0.035
        core.ellipse([ox - rr, oy - rr, ox + rr, oy + rr], fill=(255, 255, 255, 230))
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=s // 6, fill=255)
    out.putalpha(mask)
    out.save(path)


if __name__ == "__main__":
    root = sys.argv[1]
    os.makedirs(os.path.join(root, "textures", "item"), exist_ok=True)
    tome(os.path.join(root, "textures", "item", "power_tome.png"))
    icon(os.path.join(root, "icon.png"))
    print("ok")
