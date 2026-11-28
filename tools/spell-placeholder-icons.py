"""Generate small temporary item icons for the four new magic schools."""

from pathlib import Path
from PIL import Image, ImageDraw


OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/rotten_ruins/textures/item"


def icon(name, draw_icon):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw_icon(ImageDraw.Draw(image))
    image.save(OUT / f"{name}.png")


def water(d):
    d.ellipse((1, 3, 14, 13), fill="#123c70", outline="#071f42")
    d.polygon([(1, 9), (4, 7), (7, 9), (10, 6), (14, 8), (13, 11), (9, 10), (6, 12), (3, 11)], fill="#2c9ed4")
    d.line([(2, 8), (4, 6), (7, 8), (10, 5), (13, 7)], fill="#9be9fa", width=2)
    d.point((11, 11), fill="#d4ffff")


def nature(d):
    d.line([(3, 13), (7, 9), (8, 4)], fill="#244b22", width=3)
    d.polygon([(7, 10), (3, 8), (2, 4), (5, 5), (8, 8)], fill="#3c9d35")
    d.polygon([(8, 9), (8, 4), (12, 2), (13, 6), (10, 10)], fill="#70c34c")
    d.line([(5, 12), (2, 11)], fill="#8c562e", width=2)
    d.line([(7, 8), (4, 6)], fill="#b9e77a")
    d.line([(9, 7), (11, 4)], fill="#dbf59d")
    d.point((12, 11), fill="#a2db62")


def light(d):
    d.polygon([(8, 0), (10, 5), (15, 8), (10, 10), (8, 15), (5, 10), (0, 8), (5, 5)], fill="#ae6b21")
    d.polygon([(8, 2), (9, 6), (13, 8), (9, 9), (8, 13), (6, 9), (2, 8), (6, 6)], fill="#ffda63")
    d.rectangle((7, 7, 9, 9), fill="#fff9db")
    d.point((2, 2), fill="#fff4ae")
    d.point((13, 3), fill="#fff4ae")


def arcane(d):
    d.ellipse((1, 1, 14, 14), fill="#281444", outline="#120b2e")
    d.arc((3, 3, 12, 12), 40, 310, fill="#b75cff", width=2)
    d.arc((5, 4, 13, 12), 185, 440, fill="#72d6f4", width=2)
    d.polygon([(8, 4), (10, 7), (8, 11), (6, 8)], fill="#e7b5ff")
    d.point((3, 3), fill="#f2d1ff")
    d.point((12, 13), fill="#88f4fa")


for name, draw_icon in [
    ("tidal_pulse", water),
    ("briar_snare", nature),
    ("purifying_light", light),
    ("arcane_step", arcane),
]:
    icon(name, draw_icon)
