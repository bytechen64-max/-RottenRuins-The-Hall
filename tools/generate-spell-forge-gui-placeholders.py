"""Generate editable 1:1 GUI PNG placeholders for the Spell Forge proposal.

Style B ("textured") is meant to be repainted by hand later: every static bevel,
slot well, window, tooltip frame and card background lives in these PNGs, while
all text / item icons / progress fills / selection states are drawn at runtime.

Coordinate convention: the forge background is the 592x324 panel at (24,44) in
the 640x400 preview canvas; PNG coordinates are relative to the panel origin.
1 texture pixel = 1 GUI pixel.

Run from any directory:
    python tools/generate-spell-forge-gui-placeholders.py
    python tools/generate-spell-forge-gui-placeholders.py --backup-only

The generator writes the editable backups under docs/proposals/spell-forge/textures/
and (unless --backup-only) installs the same PNGs into the game resource pack at
src/main/resources/assets/rotten_ruins/textures/gui/spell_forge/.

WARNING: rerunning after an artist repaint overwrites the installed PNGs.
"""
from pathlib import Path
import sys
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BACKUP = ROOT / "docs/proposals/spell-forge/textures"
FORMAL = ROOT / "src/main/resources/assets/rotten_ruins/textures/gui/spell_forge"

# Vanilla-ish palette used by both the drawn and textured proposal.
BORDER = "#373737"
FACE = "#c6c6c6"
LIGHT = "#ffffff"
DARK = "#555555"
INSET = "#dbdbdb"
WELL_FACE = "#8b8b8b"
WELL_TOP = "#5f5f5f"
WELL_BOTTOM = "#c6c6c6"
DARK_WINDOW = "#2b2b2b"
DARK_WINDOW_TOP = "#101010"
DARK_WINDOW_BOTTOM = "#4a4a4a"
TRACK_FACE = "#303030"
TRACK_TOP = "#101010"
TRACK_BOTTOM = "#555555"

PANEL_W, PANEL_H = 592, 324


def bevel(d, x, y, w, h, face=FACE, light=LIGHT, dark=DARK, border=BORDER):
    """Raised (light=LIGHT) or sunken (light=DARK / dark=LIGHT) 1px bevel."""
    d.rectangle((x, y, x + w - 1, y + h - 1), fill=border)
    d.rectangle((x + 1, y + 1, x + w - 2, y + h - 2), fill=face)
    d.line((x + 1, y + 1, x + w - 2, y + 1), fill=light)
    d.line((x + 1, y + 1, x + 1, y + h - 2), fill=light)
    d.line((x + 1, y + h - 2, x + w - 2, y + h - 2), fill=dark)
    d.line((x + w - 2, y + 1, x + w - 2, y + h - 2), fill=dark)


def well(d, x, y, w, h, face=WELL_FACE, top=WELL_TOP, bottom=WELL_BOTTOM):
    bevel(d, x, y, w, h, face=face, light=top, dark=bottom)


def hline(d, x, y, w, color):
    d.line((x, y, x + w - 1, y), fill=color)


def vline(d, x, y, h, color):
    d.line((x, y, x, y + h - 1), fill=color)


def slot(d, x, y, size=18):
    well(d, x, y, size, size)


def shell(w=PANEL_W, h=PANEL_H):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    bevel(d, 0, 0, w, h)
    # Vanilla container title separator: dark line with a light line under it.
    hline(d, 8, 43, w - 16, DARK)
    hline(d, 8, 44, w - 16, LIGHT)
    # Footer separator (column dividers belong to the forge view only).
    hline(d, 8, 286, w - 16, DARK)
    hline(d, 8, 287, w - 16, LIGHT)
    return im, d


def tooltip(im, d, x, y, w, h):
    """Vanilla tooltip-ish dark panel: purple gradient with a bright border."""
    for row in range(h):
        t = row / max(1, h - 1)
        r = int(16 + (24 - 16) * t)
        g = int(0 + (0 - 0) * t)
        b = int(16 + (32 - 16) * t)
        d.line((x + 1, y + row, x + w - 2, y + row), fill=(r, g, b, 246))
    d.line((x + 1, y, x + w - 2, y), fill=(80, 0, 255, 150))
    d.line((x, y + 1, x, y + h - 2), fill=(64, 0, 190, 140))
    d.line((x + w - 1, y + 1, x + w - 1, y + h - 2), fill=(48, 0, 150, 120))
    d.line((x + 1, y + h - 1, x + w - 2, y + h - 1), fill=(40, 0, 127, 150))


# ---------------------------------------------------------------------------
# forge.png: shell + slots + forge window + progress track + preview tooltip
# ---------------------------------------------------------------------------
def forge_background():
    im, d = shell()
    # Column dividers are forge-only; the codex page is a clean card grid.
    vline(d, 184, 48, 238, DARK)
    vline(d, 185, 48, 238, LIGHT)
    vline(d, 406, 48, 238, DARK)
    vline(d, 407, 48, 238, LIGHT)
    for x, y in ((10, 64), (10, 106), (10, 134), (10, 162), (10, 208)):
        slot(d, x, y)
    # 160x150 sunken forge window (product slot lives inside it).
    bevel(d, 216, 66, 160, 150, face=DARK_WINDOW,
          light=DARK_WINDOW_TOP, dark=DARK_WINDOW_BOTTOM)
    slot(d, 276, 82, 40)
    # Progress track (fill is drawn at runtime).
    well(d, 226, 224, 140, 8, face=TRACK_FACE, top=TRACK_TOP, bottom=TRACK_BOTTOM)
    tooltip(im, d, 412, 64, 170, 200)
    return im


# ---------------------------------------------------------------------------
# codex.png: shell only; cards come from cards.png at runtime
# ---------------------------------------------------------------------------
def codex_background():
    im, _ = shell()
    return im


# ---------------------------------------------------------------------------
# cards.png: known card at x=0, unknown card at x=132 (both 132x98)
# ---------------------------------------------------------------------------
def cards_atlas():
    im = Image.new("RGBA", (264, 98), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    bevel(d, 0, 0, 132, 98)
    hline(d, 3, 3, 126, INSET)
    bevel(d, 132, 0, 132, 98, face=DARK_WINDOW,
          light=DARK_WINDOW_TOP, dark=DARK_WINDOW_BOTTOM)
    return im


# ---------------------------------------------------------------------------
# widgets.png: 4 button states (20x20 each) + 16px scroll + 8px check + 8px lock
# ---------------------------------------------------------------------------
SCROLL = [
    "................",
    "..###########...",
    "..#.........#...",
    ".##.#.#.#.#.##..",
    ".#.#.#.#.#.#.#..",
    "..##.#.#.#.##...",
    "...#.......#....",
    "...#.#.#.#.#....",
    "...#.......#....",
    "...#.#.#.#.#....",
    "...#.......#....",
    "...#.#.#.#.#....",
    "...#.......#....",
    "..###########...",
    "................",
    "................",
]

CHECK = [
    "........",
    "......XX",
    ".....XX.",
    "X...XX..",
    "XX.XX...",
    ".XXX....",
    "..X.....",
    "........",
]

LOCK = [
    "..XXXX..",
    ".X....X.",
    ".X....X.",
    "XXXXXXXX",
    "XXX..XXX",
    "XXX..XXX",
    "XXXXXXXX",
    "........",
]


def stamp(d, x, y, pattern, color, ink="X"):
    for row, line in enumerate(pattern):
        for col, ch in enumerate(line):
            if ch == ink:
                d.point((x + col, y + row), fill=color)


def widgets_atlas():
    im = Image.new("RGBA", (112, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    bevel(d, 0, 0, 20, 20, face=FACE)                          # normal
    bevel(d, 20, 0, 20, 20, face="#d8d8d8")                    # hover
    bevel(d, 40, 0, 20, 20, face="#a0a0a0",
          light=DARK, dark=LIGHT)                              # pressed
    bevel(d, 60, 0, 20, 20, face="#8b8b8b",
          light="#bdbdbd", dark=DARK)                          # disabled
    # 16x16 blueprint scroll at (80,0): parchment page with rolled ends.
    d.rectangle((82, 1, 93, 14), fill="#e8dfc0")
    d.rectangle((80, 1, 82, 14), fill="#c9b98c")
    d.rectangle((93, 1, 95, 14), fill="#c9b98c")
    d.rectangle((82, 1, 93, 14), outline="#6e5a3a")
    d.line((80, 1, 95, 1), fill="#f5eed6")
    d.line((80, 14, 95, 14), fill="#8a7450")
    for yy in (5, 8, 11):
        hline(d, 85, yy, 6, "#8a7a5a")
    d.point((87, 3), fill="#c9a227")
    stamp(d, 96, 0, CHECK, (85, 170, 85, 255))
    stamp(d, 104, 0, LOCK, (140, 140, 150, 255))
    return im


def main():
    backup_only = "--backup-only" in sys.argv
    BACKUP.mkdir(parents=True, exist_ok=True)
    FORMAL.mkdir(parents=True, exist_ok=True)

    outputs = {
        "forge.png": forge_background(),
        "codex.png": codex_background(),
        "cards.png": cards_atlas(),
        "widgets.png": widgets_atlas(),
    }
    for name, image in outputs.items():
        image.save(BACKUP / name)
        if not backup_only:
            image.save(FORMAL / name)
    target = "backup only" if backup_only else "backup + game assets"
    print(f"Generated {len(outputs)} spell-forge placeholder PNGs ({target})")
    for name, image in outputs.items():
        print(f"  {name}: {image.width}x{image.height}")


if __name__ == "__main__":
    main()
