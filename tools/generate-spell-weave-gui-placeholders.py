"""Generate editable pixel-art GUI placeholders for the Star Loom (spell weaving) screen.

Scheme A draws everything in Java; scheme B blits these PNGs and overlays dynamic
content (text, runes, constellation links, nodes, the spell seal, bars). This script
is the "art" for scheme B, written as pixel art in a dark arcane palette so it can be
repainted later without touching layout code.

1 texel = 1 GUI pixel. Panel coordinate origin = (0,0); the live screen places the
608x364 panel at (16,18) in the 640x400 preview canvas.

Run from anywhere:
    python tools/generate-spell-weave-gui-placeholders.py

Writes editable backups under docs/proposals/spell-weave/textures/ and installs the
same PNGs to src/main/resources/assets/rotten_ruins/textures/gui/spell_weave/.
Rerunning after a repaint overwrites the installed PNGs.
"""
from pathlib import Path
import random

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BACKUP = ROOT / "docs/proposals/spell-weave/textures"
FORMAL = ROOT / "src/main/resources/assets/rotten_ruins/textures/gui/spell_weave"

PANEL_W, PANEL_H = 608, 364

OUTLINE = "#1a1230"
FRAME_HI = "#3e3060"
FRAME_LO = "#05030c"
PANEL = "#0b0718"
PANEL_DARK = "#08040f"
PANEL_LIGHT = "#120c24"
GOLD = "#c9a45a"
GOLD_HI = "#ead49b"
GOLD_LO = "#6e5426"
TEXT = "#e6dac2"
TEXT_DIM = "#9b8fb0"
TEXT_FAINT = "#5f5478"

# Constellation field (board-relative), blitted at (182,70) inside the panel.
SKY_W, SKY_H = 244, 218
SKY_BG = "#04030a"
SKY_NEBULA = [(60, 40, 30, "#2a1848"), (150, 90, 38, "#182746"),
              (80, 150, 28, "#301838"), (190, 170, 24, "#141f38"),
              (130, 20, 18, "#241a40")]
SKY_STARS = ["#e6dac2", "#8fd2ff", "#ffd27a", "#b48cff"]

TRAY = (12, 50, 152, 258)
BOARD = (176, 50, 256, 258)
PREVIEW = (444, 50, 152, 258)
STARS = [(6, 6), (PANEL_W - 11, 6), (6, PANEL_H - 11), (PANEL_W - 11, PANEL_H - 11)]


def bevel(d, x, y, w, h, face, hi, lo, border=OUTLINE):
    d.rectangle((x, y, x + w - 1, y + h - 1), fill=border)
    d.rectangle((x + 1, y + 1, x + w - 2, y + h - 2), fill=face)
    d.line((x + 1, y + 1, x + w - 2, y + 1), fill=hi)
    d.line((x + 1, y + 1, x + 1, y + h - 2), fill=hi)
    d.line((x + 1, y + h - 2, x + w - 2, y + h - 2), fill=lo)
    d.line((x + w - 2, y + 1, x + w - 2, y + h - 2), fill=lo)


def sunken(d, x, y, w, h, face=PANEL_DARK):
    bevel(d, x, y, w, h, face, FRAME_LO, FRAME_HI)


def star(d, x, y, color, big=False):
    d.point((x, y), fill=color)
    d.point((x - 1, y), fill=color)
    d.point((x + 1, y), fill=color)
    d.point((x, y - 1), fill=color)
    d.point((x, y + 1), fill=color)
    if big:
        for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2)):
            d.point((x + dx, y + dy), fill=color)


def corner_rune(d, x, y):
    """5x5 arcane corner glyph, drawn with the dim/bright gold pair."""
    pattern = [
        "X...X",
        "X...X",
        "..X..",
        "X...X",
        "X...X",
    ]
    for row, line in enumerate(pattern):
        for col, ch in enumerate(line):
            if ch == "X":
                d.point((x + col, y + row), fill=GOLD_LO if (row + col) % 3 else GOLD)


def panel_background():
    im = Image.new("RGBA", (PANEL_W, PANEL_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    bevel(d, 0, 0, PANEL_W, PANEL_H, PANEL, FRAME_HI, FRAME_LO)

    # Title band: slightly lighter with a pixel-dithered bottom edge.
    d.rectangle((3, 3, PANEL_W - 4, 40), fill=PANEL_LIGHT)
    for x in range(4, PANEL_W - 4, 2):
        d.point((x, 41), fill=FRAME_HI)
        d.point((x + 1, 42), fill=PANEL)
    d.line((8, 40, PANEL_W - 9, 40), fill=GOLD_LO)
    d.line((8, 41, PANEL_W - 9, 41), fill=FRAME_LO)

    # Three sunken sub-panels.
    for box in (TRAY, BOARD, PREVIEW):
        sunken(d, *box)
    # Extra inner frame where the constellation sky goes; sky.png carries its own rim.
    # Footer strip + divider.
    d.rectangle((3, 314, PANEL_W - 4, PANEL_H - 4), fill=PANEL_DARK)
    d.line((8, 314, PANEL_W - 9, 314), fill=FRAME_HI)
    d.line((8, 315, PANEL_W - 9, 315), fill=FRAME_LO)
    d.line((8, 318, PANEL_W - 9, 318), fill="#1c1430")

    for sx, sy in STARS:
        corner_rune(d, sx, sy)
    return im


def sky_texture():
    im = Image.new("RGBA", (SKY_W, SKY_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, SKY_W - 1, SKY_H - 1), fill=SKY_BG)

    # Nebula: 6px dithered blocks so it reads as pixel art, not a smooth gradient.
    rng = random.Random(0x51EED)
    for bx, by, radius, color in SKY_NEBULA:
        r = int(color[1:3], 16)
        g = int(color[3:5], 16)
        b = int(color[5:7], 16)
        for y in range(0, SKY_H, 6):
            for x in range(0, SKY_W, 6):
                dx = (x + 3 - bx) / float(radius)
                dy = (y + 3 - by) / float(radius)
                falloff = 1.0 - (dx * dx + dy * dy)
                if falloff <= 0:
                    continue
                if rng.random() > min(1.0, falloff * 1.25):
                    continue
                alpha = int(24 + 86 * falloff)
                d.rectangle((x, y, x + 5, y + 5), fill=(r, g, b, alpha))

    # Star field: mostly 1px, a few plus-stars.
    for _ in range(220):
        x = rng.randrange(4, SKY_W - 4)
        y = rng.randrange(4, SKY_H - 4)
        color = rng.choice(SKY_STARS)
        alpha = rng.randrange(40, 200)
        rgb = tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))
        d.point((x, y), fill=rgb + (alpha,))
        if rng.random() < 0.10:
            d.point((x - 1, y), fill=rgb + (alpha // 2,))
            d.point((x + 1, y), fill=rgb + (alpha // 2,))
            d.point((x, y - 1), fill=rgb + (alpha // 2,))
            d.point((x, y + 1), fill=rgb + (alpha // 2,))

    # 很淡的像素法阵：一圈金点 + 四个方位星，给空星座板一点"仪式感"。
    import math
    cx, cy, radius = SKY_W / 2.0, SKY_H / 2.0, 84
    for i in range(180):
        a = i * math.tau / 180
        x = int(round(cx + math.cos(a) * radius))
        y = int(round(cy + math.sin(a) * radius))
        if 2 <= x < SKY_W - 2 and 2 <= y < SKY_H - 2:
            d.point((x, y), fill=(201, 164, 90, 46))
    for i in range(4):
        a = i * math.tau / 4 + math.pi / 4
        x = int(round(cx + math.cos(a) * radius))
        y = int(round(cy + math.sin(a) * radius))
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            d.point((x + dx, y + dy), fill=(234, 212, 155, 90))

    # Rim: dark outer, gold-ish inner line with corner cuts.
    d.rectangle((0, 0, SKY_W - 1, SKY_H - 1), outline=OUTLINE)
    d.line((1, 1, SKY_W - 2, 1), fill=GOLD_LO)
    d.line((1, 1, 1, SKY_H - 2), fill=GOLD_LO)
    d.line((1, SKY_H - 2, SKY_W - 2, SKY_H - 2), fill=FRAME_LO)
    d.line((SKY_W - 2, 1, SKY_W - 2, SKY_H - 2), fill=FRAME_LO)
    for x, y in ((1, 1), (SKY_W - 2, 1), (1, SKY_H - 2), (SKY_W - 2, SKY_H - 2)):
        d.point((x, y), fill=GOLD)
    return im


def widgets_atlas():
    """160x20: 4 button states (20x20), 3 token frames (20x20), 8x8 lock."""
    im = Image.new("RGBA", (160, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    def button(x, face, hi, lo, border):
        d.rectangle((x, 0, x + 19, 19), fill=border)
        d.rectangle((x + 1, 1, x + 18, 18), fill=face)
        d.line((x + 1, 1, x + 18, 1), fill=hi)
        d.line((x + 1, 1, x + 1, 18), fill=hi)
        d.line((x + 1, 18, x + 18, 18), fill=lo)
        d.line((x + 18, 1, x + 18, 18), fill=lo)
        # extra inner gold hairline, leaving a 2px fixed border for nine-slicing
        d.line((x + 2, 2, x + 17, 2), fill=GOLD_LO)
        d.line((x + 2, 2, x + 2, 17), fill=GOLD_LO)
        d.line((x + 2, 17, x + 17, 17), fill=FRAME_LO)
        d.line((x + 17, 2, x + 17, 17), fill=FRAME_LO)

    button(0, "#2a1f44", "#4a3a6e", "#070412", OUTLINE)          # normal
    button(20, "#352757", "#5a4889", "#070412", GOLD_LO)          # hover
    # pressed: sunken
    d.rectangle((40, 0, 59, 19), fill=OUTLINE)
    d.rectangle((41, 1, 58, 18), fill="#0e0a1c")
    d.line((41, 1, 58, 1), fill=FRAME_LO)
    d.line((41, 1, 41, 18), fill=FRAME_LO)
    d.line((41, 18, 58, 18), fill=FRAME_HI)
    d.line((58, 1, 58, 18), fill=FRAME_HI)
    button(60, "#151022", "#221a36", "#070412", "#2a1f44")       # disabled

    def token(x, ring, ring2, fill):
        d.rectangle((x, 0, x + 19, 19), fill=OUTLINE)
        d.rectangle((x + 1, 1, x + 18, 18), fill=ring)
        d.rectangle((x + 2, 2, x + 17, 17), fill=ring2)
        d.rectangle((x + 3, 3, x + 16, 16), fill=fill)
        d.line((x + 3, 3, x + 16, 3), fill="#00000020")
        d.line((x + 3, 3, x + 3, x + 16), fill="#00000020")

    token(80, GOLD_LO, "#2a1f44", "#0b0718")                      # normal
    token(100, GOLD, GOLD_HI, "#0b0718")                          # selected
    token(120, "#2a1f44", "#1a1230", "#08040f")                   # locked

    lock = [
        "..XXX..",
        ".X...X.",
        ".X...X.",
        "XXXXXXX",
        "XXX.XXX",
        "XXX.XXX",
        "XXXXXXX",
    ]
    for row, line in enumerate(lock):
        for col, ch in enumerate(line):
            if ch == "X":
                d.point((141 + col, 1 + row), fill=TEXT_FAINT)
    return im


def main():
    BACKUP.mkdir(parents=True, exist_ok=True)
    FORMAL.mkdir(parents=True, exist_ok=True)
    outputs = {
        "weave_bg.png": panel_background(),
        "sky.png": sky_texture(),
        "widgets.png": widgets_atlas(),
    }
    for name, image in outputs.items():
        image.save(BACKUP / name)
        image.save(FORMAL / name)
    print(f"Generated {len(outputs)} star-loom pixel placeholders")
    for name, image in outputs.items():
        print(f"  {name}: {image.width}x{image.height}")


if __name__ == "__main__":
    main()
