"""Generate a hand-editable 64x16 pixel slash and a nearest-neighbour painting guide."""
from pathlib import Path
import math
from PIL import Image, ImageDraw

PALETTE = [(255,255,255,255), (220,239,255,255), (125,170,205,255), (95,145,190,96)]

def generate():
    width, height = 64, 16
    image = Image.new("RGBA", (width, height))
    # Quantized fan: a broken, bright outer rim and three fading inner streams.
    for x in range(3, 63):
        u = x / 63
        outer = 3 + round(1.5 * math.sin(u * math.pi)) + (1 if x % 13 in (0,1) else 0)
        spread = max(1, round(9 * u ** .65))
        fade = min(1, u * 3) * min(1, (64-x) / 5)
        for y in range(16):
            d = y - outer
            if d < 0 or d > spread: continue
            edge = d <= 1
            hole = d > 2 and ((x*17+y*31) % 23 < 5 or (x//6+y//2) % 11 == 0)
            if hole: continue
            alpha = int((235 if edge else 145 * (1-d/(spread+1))**1.3) * fade)
            rgb = (255,255,255) if d==0 else (224,244,255) if d<3 else (125,170,205)
            image.putpixel((x,y), (*rgb,alpha))
        if x%7 in (0,1) and x>16:
            yy=min(15,outer+spread+2)
            image.putpixel((x,yy),(200,230,255,int(115*fade)))
    root = Path(__file__).resolve().parents[1]
    output = root / "src/main/resources/assets/rotten_ruins/textures/effect/slash_crescent.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output)
    spark=Image.new("RGBA",(8,8))
    for sy in range(8):
        for sx in range(8):
            d=abs(sx-3.5)+abs(sy-3.5)
            if d<3.5: spark.putpixel((sx,sy),(255,255,255,255 if d<1.5 else 130 if d<2.5 else 45))
    spark.save(output.with_name("slash_spark.png"))
    guide = Image.new("RGB", (1088, 420), (21, 26, 36))
    draw = ImageDraw.Draw(guide)
    draw.text((30, 18), "64 x 16 RGBA | TAIL / older pixels -> LEADING EDGE / newest pixels", fill=(235,240,255))
    for y in range(16):
        for x in range(64):
            value = 45 if (x+y)%2 else 58
            draw.rectangle((32+x*16, 56+y*16, 47+x*16, 71+y*16), fill=(value,value+4,value+10))
    enlarged = image.resize((1024,256), Image.Resampling.NEAREST)
    guide.paste(enlarged, (32,56), enlarged)
    for x in range(65): draw.line((32+x*16,56,32+x*16,312), fill=(55,61,72), width=1)
    for y in range(17): draw.line((32,56+y*16,1056,56+y*16), fill=(55,61,72), width=1)
    for i,color in enumerate(PALETTE):
        xx=32+i*256
        draw.rectangle((xx,338,xx+28,366), fill=color[:3])
        draw.text((xx+38,343), f"RGBA {color}", fill=(220,230,245))
    draw.text((32,390), "Paint at 64x16, use a hard pixel brush, keep the background transparent. No blur / no smoothing.", fill=(195,210,230))
    docs = root / "docs/slash-trails"
    docs.mkdir(parents=True,exist_ok=True)
    guide.save(docs / "pixel-guide.png")
    print(output)
    print(docs / "pixel-guide.png")

if __name__ == "__main__": generate()
