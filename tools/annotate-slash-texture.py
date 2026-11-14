"""Document the production trail UVs without changing the user's game texture."""
from pathlib import Path
from math import sin, cos, radians
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'src/main/resources/assets/rotten_ruins/textures/effect/slash_crescent.png'
OUT=ROOT/'docs/slash-trails/slash-crescent-directions.png'
FONT='C:/Windows/Fonts/msyh.ttc'
BOLD='C:/Windows/Fonts/msyhbd.ttc'

def main():
    canvas=Image.new('RGB',(1440,1100),'#101925');d=ImageDraw.Draw(canvas)
    ink='#e4edf9';muted='#a9bbcf';cyan='#70e4ed';orange='#ffc07d'
    def text(x,y,value,size=24,fill=ink,bold=False):
        d.text((x,y),value,font=ImageFont.truetype(BOLD if bold else FONT,size),fill=fill)
    def arrow(a,b,color,width=4):
        d.line([a,b],fill=color,width=width)
        dx,dy=b[0]-a[0],b[1]-a[1];length=(dx*dx+dy*dy)**.5;dx/=length;dy/=length
        d.polygon([b,(b[0]-dx*14-dy*7,b[1]-dy*14+dx*7),(b[0]-dx*14+dy*7,b[1]-dy*14-dx*7)],fill=color)
    def badge(x,y,letter):
        d.ellipse((x-17,y-17,x+17,y+17),fill='#25465b',outline=cyan,width=2)
        d.text((x,y-1),letter,font=ImageFont.truetype(BOLD,21),anchor='mm',fill='white')
    text(48,30,'slash_crescent.png · 贴图方向标注',34,bold=True)
    text(48,84,'对应当前游戏与编辑器的 UV；字母 A / B / C / D 在下面的扇面中一一对应。',21,muted)
    d.rounded_rectangle((32,132,1408,536),radius=18,fill='#1a2839')
    text(204,145,'U = 0：起挥 / 较旧轨迹',23,cyan)
    text(945,145,'U = 1：收刀 / 较新轨迹',23,cyan)
    arrow((524,163),(912,163),cyan)
    text(208,187,'上边：V = 0 · 刀根方向 / 刀光内缘',22,orange)
    x0,y0,cell=208,230,16
    source=Image.open(SOURCE).convert('RGBA')
    for y in range(16):
        for x in range(64):
            c='#3b4654' if (x+y)%2 else '#2d3744'
            d.rectangle((x0+x*cell,y0+y*cell,x0+(x+1)*cell-1,y0+(y+1)*cell-1),fill=c)
    scaled=source.resize((1024,256),Image.Resampling.NEAREST)
    canvas.paste(scaled,(x0,y0),scaled)
    for x in range(65):d.line((x0+x*cell,y0,x0+x*cell,y0+256),fill='#506070')
    for y in range(17):d.line((x0,y0+y*cell,x0+1024,y0+y*cell),fill='#506070')
    arrow((141,238),(141,478),orange)
    text(55,310,'V 向下',20,orange)
    text(208,493,'下边：V = 1 · 刀尖方向 / 刀光外缘（建议放主要亮边）',22,orange)
    for x,y,letter in [(190,225,'A'),(1250,225,'B'),(1250,489,'C'),(190,489,'D')]:badge(x,y,letter)
    text(48,552,'旧图提示：当前亮边在图片上侧，实际映射会靠近内缘；重画时建议把主亮边移到下侧。',23,orange)
    d.rounded_rectangle((32,610,698,1014),radius=18,fill='#1a2839')
    text(55,632,'怎么画',28,bold=True)
    lines=['下侧：明亮刀锋；向上：渐淡的扇面。','左侧：淡而细的尾迹；右侧：较饱满的主体。','背景保持透明，像素边缘用硬笔，不模糊。','四种挥砍共用这张图，无需按屏幕方向翻转。','弧形由三维网格展开，PNG 不用画成大弯月。']
    for i,line in enumerate(lines):text(55,682+i*43,line,22,ink)
    text(55,917,'右端代表整次攻击的末段；挥砍过程中，',21,muted)
    text(55,950,'当前前沿从左向右推进，不会立刻显示整图。',21,muted)
    d.rounded_rectangle((724,610,1408,1014),radius=18,fill='#1a2839')
    text(750,632,'映射到三维扇面之后',28,bold=True)
    center=(1060,678);inner,outer=100,250
    def point(radius,angle):return (center[0]+radius*cos(radians(angle)),center[1]+radius*sin(radians(angle)))
    for i in range(50):
        a=145-i*110/50;b=145-(i+1)*110/50
        color=(int(45+i*1.2),int(113+i*1.5),int(150+i*1.1))
        d.polygon([point(inner,a),point(inner,b),point(outer,b),point(outer,a)],fill=color)
    for radius,color in [(inner,orange),(outer,cyan)]:
        d.line([point(radius,145-i*110/100) for i in range(101)],fill=color,width=4)
    for i in range(9):
        angle=145-i*110/8;d.line([point(inner,angle),point(outer,angle)],fill='#4d8b9d',width=2)
    for radius,angle,letter in [(inner,145,'A'),(inner,35,'B'),(outer,35,'C'),(outer,145,'D')]:
        x,y=point(radius,angle);badge(x,y,letter)
    text(926,687,'上边 → 内弧',21,orange)
    text(951,945,'下边 → 外弧 / 刀尖',21,cyan)
    arrow((1060,797),(1060,908),orange)
    text(1080,838,'由内向外',20,orange)
    text(48,1035,'原图：64 × 16 · RGBA PNG · 棋盘格仅用于显示透明区域，不能画进正式贴图。',22,muted)
    OUT.parent.mkdir(parents=True,exist_ok=True);canvas.save(OUT)
    print(OUT)

if __name__=='__main__':main()
