"""Generate editable 1:1 GUI PNG placeholders; no fonts or dynamic data baked in.

Run from any directory: python tools/generate-source-gui-placeholders.py
Only files under docs/proposals/source-gui/textures are regenerated.
"""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[1] / 'docs/proposals/source-gui/textures'
OUT.mkdir(parents=True, exist_ok=True)


def bevel(d, x, y, w, h, face='#c6c6c6', light='#ffffff', dark='#555555'):
    d.rectangle((x, y, x+w-1, y+h-1), fill=dark)
    d.rectangle((x, y, x+w-2, y+h-2), fill=light)
    inset = 2 if min(w, h) >= 5 else 1
    d.rectangle((x+inset, y+inset, x+w-inset-1, y+h-inset-1), fill=face)


def well(d, x, y, w, h, face='#8b8b8b'):
    bevel(d, x, y, w, h, face, '#555555', '#ececec')


def shell(w, h):
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((2, 0, w-3, h-1), fill='#373737')
    d.rectangle((0, 2, w-1, h-3), fill='#373737')
    bevel(d, 2, 2, w-4, h-4)
    d.line((8, 27, w-9, 27), fill='#929292')
    d.line((8, 28, w-9, 28), fill='#dbdbdb')
    for x in (7, w-11):
        for y in (7, h-11):
            bevel(d, x, y, 4, 4, '#929292', '#ffffff', '#555555')
    return im, d


for view in ('manual', 'quick', 'bands'):
    im, d = shell(372, 230)
    if view == 'bands':
        well(d, 16, 66, 340, 150, '#303030')
        for row in range(7):
            if row % 2 == 0:
                d.rectangle((19, 92+row*16, 352, 107+row*16), fill='#383838')
    else:
        well(d, 16, 66, 340, 62, '#202725')
        for x in range(32, 348, 24):
            d.line((x, 84, x, 121), fill='#34423a')
        for y in (90, 106, 122):
            d.line((24, y, 347, y), fill='#34423a')
        if view == 'manual':
            for i in range(4):
                x, y = 16+(i % 2)*175, 155+(i//2)*35
                well(d, x, y, 165, 28, '#c6c6c6')
                well(d, x+9, y+19, 147, 5, '#656565')
    im.save(OUT / f'filter_{view}.png')

im, d = shell(220, 200)
for x, y in [(55, 52), (149, 52)] + [(29+col*18, 112+row*18) for row in range(3) for col in range(9)] + [(29+col*18, 170) for col in range(9)]:
    well(d, x-1, y-1, 18, 18, '#8b8b8b')
# Arrow silhouette: its interior fill remains a separate dynamic overlay.
well(d, 81, 54, 58, 12, '#666666')
im.save(OUT / 'collector.png')

# Atlas: 20x20 button states at x=0/20/40, 8x10 thumb at (60,0).
im = Image.new('RGBA', (80, 20), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
bevel(d, 0, 0, 20, 20, '#929292', '#ffffff', '#555555')
bevel(d, 20, 0, 20, 20, '#777777', '#555555', '#ececec')
bevel(d, 40, 0, 20, 20, '#aaaaaa', '#ffffff', '#555555')
bevel(d, 60, 0, 8, 10, '#c6c6c6', '#ffffff', '#555555')
im.save(OUT / 'widgets.png')
print(f'Generated 5 placeholder PNGs in {OUT}')
