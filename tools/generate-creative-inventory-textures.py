"""Draw the selected blue slate / silver creative inventory using Pillow.

Run: python tools/generate-creative-inventory-textures.py
The three 256x256 atlases follow Minecraft 1.20.1 / Forge creative tab UVs.
Deterministic output; rerunning overwrites these generated assets.
"""
from pathlib import Path
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/rotten_ruins/textures/gui/creative_inventory'
INK = '#0d131e'
SLATE = '#29313f'
LIGHT = '#545f6f'
METAL = '#667a94'
METAL_HI = '#b8cbde'
METAL_DARK = '#425165'
VIOLET = '#8585bd'


def bevel(d, x, y, w, h, face, hi, lo):
    d.rectangle((x, y, x+w-1, y+h-1), fill=INK)
    d.rectangle((x+1, y+1, x+w-2, y+h-2), fill=face)
    d.line((x+1, y+1, x+w-2, y+1), fill=hi)
    d.line((x+1, y+1, x+1, y+h-2), fill=hi)
    d.line((x+1, y+h-2, x+w-2, y+h-2), fill=lo)
    d.line((x+w-2, y+1, x+w-2, y+h-2), fill=lo)


def diamond(d, x, y, color=VIOLET):
    d.point((x, y-1), fill=color)
    d.line((x-1, y, x+1, y), fill=color)
    d.point((x, y+1), fill=color)


def background():
    im = Image.new('RGBA', (256, 256))
    d = ImageDraw.Draw(im)
    # Stepped corners, cast-metal rim, and a low contrast basalt face.
    d.polygon([(3,0),(191,0),(194,3),(194,132),(191,135),(3,135),(0,132),(0,3)], fill=INK)
    d.rectangle((2,2,192,133), fill=METAL_DARK)
    d.rectangle((3,3,191,132), fill=METAL)
    d.rectangle((4,4,190,131), fill=INK)
    d.rectangle((5,5,189,130), fill=SLATE)
    rng = random.Random(731)
    for _ in range(420):
        x, y = rng.randrange(6,189), rng.randrange(6,130)
        d.point((x,y), fill=rng.choice(['#2f3745','#272f3d','#343d4c']))
    d.line((5,5,189,5), fill=LIGHT)
    d.line((5,130,189,130), fill='#222a37')
    # Header underline leaves (8,6) clear for the localized runtime title.
    d.line((8,15,169,15), fill=METAL_DARK)
    d.line((8,16,169,16), fill=INK)
    for row in range(5):
        for col in range(9):
            bevel(d,8+18*col,17+18*row,18,18,'#1c2330','#111722','#535e6d')
    # Player hotbar has identical vanilla slot positions, with silver metal edges.
    d.line((8,109,169,109), fill=METAL_DARK)
    for col in range(9):
        bevel(d,8+18*col,111,18,18,'#1f2633','#101622','#455367')
    bevel(d,174,17,14,114,'#181f2c','#0d131e','#48576c')
    d.line((180,20,180,126), fill='#31374c')
    for y in range(22,127,8):
        d.point((181,y), fill='#60638c')
    # Small crown-shaped seal below the track, clear of its 112px travel area.
    d.line((175,8,177,12,181,12,184,8), fill=METAL)
    d.line((177,12,177,9,180,11,182,9), fill=METAL)
    diamond(d,180,7)
    # Rivets and chipped edges are restricted to the outer chrome.
    for x in (3,191):
        for y in (3,132):
            d.point((x,y), fill=METAL_HI)
    for x in (22,63,121,166):
        d.point((x,3), fill=METAL_DARK)
        d.point((x+1,132), fill=METAL_HI)
    return im


def tabs():
    im = Image.new('RGBA', (256,256))
    d = ImageDraw.Draw(im)
    # Seven columns; top/bottom, inactive/active. Runtime chooses column and row.
    for col in range(7):
        for row in range(4):
            x,y = col*26,row*32
            active = row % 2 == 1
            bevel(d,x+1,y+1,24,31,SLATE if active else '#252c3a',METAL if active else '#48566a',INK)
            d.line((x+3,y+3,x+22,y+3), fill=METAL_HI if active else METAL_DARK)
            d.line((x+3,y+4,x+22,y+4), fill=INK)
            # Icon field is 16x16 at (5,9) or (5,7), with no baked item.
            if active:
                diamond(d,x+12,y+5)
            else:
                d.line((x+9,y+5,x+15,y+5), fill=METAL_DARK)
            if row < 2:
                d.rectangle((x+2,y+29,x+23,y+31), fill=SLATE if active else '#252c3a')
            else:
                d.rectangle((x+2,y,x+23,y+2), fill=SLATE if active else '#252c3a')
    # CreativeModeInventoryScreen: (232,0) active, (244,0) disabled, 12x15.
    for x,active in ((232,True),(244,False)):
        bevel(d,x,0,12,15,'#727ea4' if active else '#202734',METAL_HI if active else '#48576c','#121823')
        for y in (5,7,9):
            d.line((x+3,y,x+8,y), fill='#31354e' if active else '#181e2b')
            d.line((x+3,y+1,x+8,y+1), fill='#a6b2d2' if active else '#364153')
    return im


def dividers():
    """Opaque 162x18 metal nameplate at (0,0); remaining atlas pixels are clear."""
    im = Image.new('RGBA', (256,256))
    d = ImageDraw.Draw(im)
    d.rectangle((0,0,161,17), fill=SLATE)
    d.rectangle((0,1,161,16), fill=INK)
    d.rectangle((1,2,160,15), fill='#27303f')
    d.line((2,2,159,2), fill=METAL)
    d.line((2,15,159,15), fill='#3a4659')
    for x in (4,157):
        d.point((x,8), fill=METAL_HI)
        d.point((x,9), fill=INK)
    return im


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    for name,im in [('tab_items.png',background()),('tabs.png',tabs()),('dividers.png',dividers())]:
        im.save(OUT/name)
        print(f'{name}: {im.width}x{im.height} RGBA')
