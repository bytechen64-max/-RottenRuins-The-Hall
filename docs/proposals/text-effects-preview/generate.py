# -*- coding: utf-8 -*-
"""《Rotten Ruins Splendiding》 § 分节符文本特效 —— 方案级视觉预览生成器。

本脚本**不是**游戏实现，只按方案文档里的数学公式，离线画出"效果长什么样"，
供方案审批时查看。所有默认参数与公式均出自
`docs/proposals/` 中的文本特效方案（见 README.md 的公式表）。

用法（Windows + 本机 Python）::

    python generate.py            # 生成全部 GIF、联络表并自检
    python generate.py gradient   # 只生成指定 GIF（调试用）
    python generate.py sheet      # 只重新生成 preview-sheet.png

约定
----
* 坐标单位：GUI 像素；预览整体按 1 GUI px = 3 图像 px 最近邻放大（GUI_SCALE）。
* 文字：SimHei 16 GUI px 二值化渲染（副标题 12 GUI px），带 MC 风格 1 GUI px 暗影。
* 动画：20 fps（每帧 50 ms）、GIF 无限循环；时长 2.4~3.2 s。

依赖：Pillow >= 10、numpy；字体读取 Windows 自带 simhei.ttf / msyh.ttc。
"""

from __future__ import annotations

import math
import sys
from pathlib import Path
from typing import Dict, List, NamedTuple, Optional, Sequence, Tuple

import numpy as np
from PIL import Image, ImageDraw, ImageFont

# Windows 下 stdout 被重定向（管道/IDE）时默认用 GBK，中文日志容易乱码；
# 尽量切到 UTF-8（文件本身始终是 UTF-8）。控制台句柄场景 Python 默认已是 UTF-8。
try:
    if sys.stdout.encoding and sys.stdout.encoding.lower().replace("-", "") != "utf8":
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

# ---------------------------------------------------------------------------
# 0. 可复现参数（= 方案默认值）
# ---------------------------------------------------------------------------
OUT_DIR = Path(__file__).resolve().parent

SIMHEI = r"C:\Windows\Fonts\simhei.ttf"
MSYH = r"C:\Windows\Fonts\msyh.ttc"
MSYH_BD = r"C:\Windows\Fonts\msyhbd.ttc"

GUI_SCALE = 3                              # 1 GUI px = 3 图像 px
CANVAS_GUI_W, CANVAS_GUI_H = 240, 44       # GUI 画布
CANVAS_W = CANVAS_GUI_W * GUI_SCALE        # 720
CANVAS_H = CANVAS_GUI_H * GUI_SCALE        # 132

BG = (0x15, 0x10, 0x15)                    # 近黑紫背景
WHITE = (255, 255, 255)                    # 默认字色
PALE = (0xE8, 0xE4, 0xD8)                  # §i 苍白
CORRUPT_GREEN = (0x6F, 0xBF, 0x4A)         # §g 渐变 A：腐化绿
COURT_BLOOD = (0x8B, 0x1E, 0x2D)           # §g 渐变 B：王庭血红

FPS = 20
FRAME_MS = 1000 // FPS                     # 50 ms
STATIC_MS = 2400                           # 静态效果的 GIF 单帧延时
SHADOW_OFFSET_GUI = 1                      # MC 暗影：右下 1 GUI px
SHADOW_SCALE = 0.25                        # 暗影颜色 = 主色 × 0.25
SPACING_GUI = 1.0                          # 字间距 1 GUI px（模拟 MC 字体间隙）

TITLE = "腐朽之疫：王庭"
SUBTITLE = "当陨石触地的瞬间，苍白之火绞杀一切"
ENGLISH = "Rotten Ruins"

TYPEWRITER_PERIOD = 3.2                    # §t 周期（秒）
TYPEWRITER_REVEAL = 1.6                    # 前 1.6 s 逐字显现
TYPEWRITER_CPS = 12.0                      # 12 字/s

# 联络表配色
SHEET_BG = (0x10, 0x0B, 0x10)
SHEET_PANEL = (0x1B, 0x14, 0x20)
SHEET_BORDER = (0x3A, 0x2B, 0x38)
INK_PALE = (0xE8, 0xE4, 0xD8)
INK_DIM = (0x8F, 0x81, 0x90)
INK_DIMMER = (0x6E, 0x62, 0x6E)
ACCENT_GREEN = (0x6F, 0xBF, 0x4A)
ACCENT_BLOOD = (0x8B, 0x1E, 0x2D)


# ---------------------------------------------------------------------------
# 1. 颜色工具：sRGB ↔ 线性光、HSV、确定性 hash
# ---------------------------------------------------------------------------
def srgb_to_linear(c: np.ndarray) -> np.ndarray:
    """sRGB（0..1）→ 线性光（0..1）。"""
    c = np.asarray(c, dtype=np.float64)
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def linear_to_srgb(c: np.ndarray) -> np.ndarray:
    """线性光（0..1）→ sRGB（0..1）。"""
    c = np.clip(np.asarray(c, dtype=np.float64), 0.0, 1.0)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * (c ** (1.0 / 2.4)) - 0.055)


def rgb01(rgb: Sequence[int]) -> np.ndarray:
    return np.asarray(rgb, dtype=np.float64) / 255.0


def lerp_linear(rgb_a: Sequence[int], rgb_b: Sequence[int], p: float) -> np.ndarray:
    """在线性光空间线性插值（避免 sRGB 空间插值中段发灰），返回 0..1 RGB。"""
    la = srgb_to_linear(rgb01(rgb_a))
    lb = srgb_to_linear(rgb01(rgb_b))
    return linear_to_srgb(la + (lb - la) * p)


def hsv_to_rgb(h: float, s: float, v: float) -> np.ndarray:
    """HSV → RGB，h ∈ [0,1)，返回 0..1 RGB。"""
    h = h % 1.0
    sector = int(h * 6.0) % 6
    f = h * 6.0 - math.floor(h * 6.0)
    p = v * (1.0 - s)
    q = v * (1.0 - f * s)
    t = v * (1.0 - (1.0 - f) * s)
    table = ((v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q))
    return np.array(table[sector], dtype=np.float64)


def hash_u32(i: int, b: int, salt: int) -> int:
    """确定性 32 位 hash（与 index.html 中的 JS 实现逐位一致）。"""
    x = (int(i) * 374761393 + int(b) * 668265263 + int(salt) * 2246822519 + 0x9E3779B9) & 0xFFFFFFFF
    x = ((x ^ (x >> 13)) * 1274126177) & 0xFFFFFFFF
    x = (x ^ (x >> 16)) & 0xFFFFFFFF
    return x


def hash01(i: int, b: int, salt: int = 0) -> float:
    return hash_u32(i, b, salt) / 4294967296.0


# ---------------------------------------------------------------------------
# 2. 像素字体：SimHei 二值化掩码 + 逐字布局
# ---------------------------------------------------------------------------
class _Char(NamedTuple):
    ch: str
    ink: np.ndarray        # bool 掩码，GUI 分辨率（h, w），已按 GUI_SCALE 放大
    off_x: int             # 墨迹相对排版原点的偏移（GUI px）
    off_y: int
    x: float               # 该字在行内的排版 x（GUI px）
    advance: float


class _Layout(NamedTuple):
    text: str
    ascent: int
    descent: int
    width: float           # 行宽（GUI px，不含末尾字间距）
    chars: List[_Char]


_FONT_CACHE: Dict[Tuple[str, int], ImageFont.FreeTypeFont] = {}
_GLYPH_CACHE: Dict[Tuple[str, int, str], Tuple[np.ndarray, Tuple[int, int], float]] = {}
_LAYOUT_CACHE: Dict[Tuple[str, str, int, float], _Layout] = {}


def get_font(font_path: str, size: int) -> ImageFont.FreeTypeFont:
    key = (font_path, size)
    if key not in _FONT_CACHE:
        _FONT_CACHE[key] = ImageFont.truetype(font_path, size)
    return _FONT_CACHE[key]


def get_glyph(font_path: str, size: int, ch: str) -> Tuple[np.ndarray, Tuple[int, int], float]:
    """取单个字符的二值墨迹（GUI 分辨率下采样后放大 3×）与排版偏移。"""
    key = (font_path, size, ch)
    if key in _GLYPH_CACHE:
        return _GLYPH_CACHE[key]
    font = get_font(font_path, size)
    try:
        mask, offset = font.getmask2(ch, mode="1")
        w, h = mask.size
        if w <= 0 or h <= 0:
            raw = np.zeros((0, 0), dtype=bool)
        else:
            # mode="1" 的取值只有 0/255，阈值化即二值化
            raw = np.frombuffer(bytes(mask), dtype=np.uint8).reshape(h, w) >= 128
    except Exception:
        raw = np.zeros((0, 0), dtype=bool)
        offset = (0, 0)
    # 先按 GUI 像素放大 3×，得到"每个 GUI 像素 = 3×3 图像像素"的方块
    ink = np.repeat(np.repeat(raw, GUI_SCALE, axis=0), GUI_SCALE, axis=1).astype(np.float64)
    advance = font.getlength(ch)
    _GLYPH_CACHE[key] = (ink, offset, advance)
    return _GLYPH_CACHE[key]


def layout_line(text: str, font_path: str = SIMHEI, size: int = 16,
                spacing: float = SPACING_GUI) -> _Layout:
    """按可视字符逐字排版；emoji/代理对在 Python 中是 1 个码点，计 1 个。"""
    key = (text, font_path, size, spacing)
    if key in _LAYOUT_CACHE:
        return _LAYOUT_CACHE[key]
    font = get_font(font_path, size)
    ascent, descent = font.getmetrics()
    chars: List[_Char] = []
    x = 0.0
    for ch in text:
        ink, offset, advance = get_glyph(font_path, size, ch)
        chars.append(_Char(ch=ch, ink=ink, off_x=offset[0], off_y=offset[1],
                           x=x, advance=advance))
        x += advance + spacing
    width = x - spacing if chars else 0.0
    lay = _Layout(text=text, ascent=ascent, descent=descent, width=width, chars=chars)
    _LAYOUT_CACHE[key] = lay
    return lay


def subpixel_shift(layer: np.ndarray, fx: float, fy: float) -> np.ndarray:
    """对墨迹图层做 (fx, fy) 的图像像素亚像素平移（双线性权重）。"""
    if fx == 0.0 and fy == 0.0:
        return layer
    h, w = layer.shape
    padded = np.zeros((h + 2, w + 2), dtype=np.float64)
    padded[1:1 + h, 1:1 + w] = layer
    right = np.roll(padded, 1, axis=1) if fx else None
    down = np.roll(padded, 1, axis=0) if fy else None
    out = (1.0 - fx) * (1.0 - fy) * padded
    if right is not None:
        out += fx * (1.0 - fy) * right
    if down is not None:
        out += (1.0 - fx) * fy * down
    if right is not None and down is not None:
        out += fx * fy * np.roll(right, 1, axis=0)
    return out[1:1 + h, 1:1 + w]


# ---------------------------------------------------------------------------
# 3. 八个效果函数（公式 = 方案默认值）
# ---------------------------------------------------------------------------
def effect_gradient(i: int, n: int) -> np.ndarray:
    """§g 渐变·腐化：沿 i 从 A 线性光插值到 B（静止）。"""
    p = i / (n - 1) if n > 1 else 0.0
    return lerp_linear(CORRUPT_GREEN, COURT_BLOOD, p)


def effect_rainbow(i: int, t: float) -> np.ndarray:
    """§h 彩虹流彩：hue = (i*0.055 + t*0.12) mod 1，S=0.72，V=1.0。"""
    hue = (i * 0.055 + t * 0.12) % 1.0
    return hsv_to_rgb(hue, 0.72, 1.0)


def effect_pulse_k(i: int, t: float) -> float:
    """§p 呼吸：亮度系数 k = 0.65 + 0.35*(0.5+0.5*sin(2π*(t*0.8 + i*0.03)))。"""
    return 0.65 + 0.35 * (0.5 + 0.5 * math.sin(2.0 * math.pi * (t * 0.8 + i * 0.03)))


def effect_shake_offset(i: int, t: float) -> Tuple[float, float]:
    """§s 抖动：两个不同频率的正弦叠加，单位 GUI px，保留 1 位小数。"""
    dx = 1.3 * math.sin(t * 11.0 + i * 2.1) + 0.7 * math.sin(t * 17.3 + i * 5.7)
    dy = 1.3 * math.sin(t * 13.7 + i * 1.3) + 0.7 * math.cos(t * 19.1 + i * 3.9)
    return round(dx, 1), round(dy, 1)


def effect_wave_offset(i: int, t: float) -> Tuple[float, float]:
    """§w 波浪：dy = 1.6*sin(i*0.55 - t*4.0)，dx = 0。"""
    return 0.0, round(1.6 * math.sin(i * 0.55 - t * 4.0), 1)


def glitch_bucket(t: float) -> int:
    """§q 故障：时间分桶 b = floor(t*12)。"""
    return int(math.floor(t * 12.0))


def effect_glitch(i: int, t: float) -> Tuple[float, bool, int]:
    """§q 故障：返回 (dx, 是否触发, 色偏模式 0=R/B 交换 1=+0.35 青色)。

    触发条件 hash(i,b) < 0.10；位移 ±(3~7) GUI px；alpha 由调用方乘 0.55。
    """
    b = glitch_bucket(t)
    if hash01(i, b, 1) >= 0.10:
        return 0.0, False, 0
    dir_hash = hash01(i, b, 2)
    sign = 1.0 if dir_hash < 0.5 else -1.0
    magnitude = 3.0 + 4.0 * ((dir_hash * 2.0) % 1.0)   # 3.0 ~ 7.0
    dx = round(sign * magnitude, 1)
    mode = 0 if hash01(i, b, 3) < 0.5 else 1
    return dx, True, mode


def effect_typewriter_visible(i: int, t: float) -> bool:
    """§t 打字机：3.2s 周期；前 1.6s 以 12 字/s 逐字显现，后 1.6s 全部显示。"""
    phase = t % TYPEWRITER_PERIOD
    if phase >= TYPEWRITER_REVEAL:
        return True
    return i < math.floor(phase * TYPEWRITER_CPS)


def _glitch_tint(color: np.ndarray, mode: int) -> np.ndarray:
    """§q 的"颜色通道偏移"。

    mode 0：R/B 通道交换；
    mode 1：逐通道加 0.35 青色（+G, +B）后 clamp 到 1。
    """
    if mode == 0:
        return color[[2, 1, 0]].copy()
    return np.clip(color + np.array([0.0, 0.35, 0.35]), 0.0, 1.0)


def char_state(effects: Sequence[str], i: int, n: int, t: float,
               base_color: Sequence[int] = WHITE
               ) -> Tuple[np.ndarray, float, float, float, bool]:
    """按 § 码顺序依次施加效果，返回 (颜色 0..1, dx, dy, alpha, 是否绘制)。"""
    color = rgb01(base_color)
    dx = 0.0
    dy = 0.0
    alpha = 1.0
    visible = True
    for code in effects:
        if code == "g":
            color = effect_gradient(i, n)
        elif code == "h":
            color = effect_rainbow(i, t)
        elif code == "p":
            color = color * effect_pulse_k(i, t)          # §p 乘在当前颜色上
        elif code == "i":
            color = rgb01(PALE)
        elif code == "s":
            sdx, sdy = effect_shake_offset(i, t)
            dx += sdx
            dy += sdy
        elif code == "w":
            _, wdy = effect_wave_offset(i, t)
            dy += wdy
        elif code == "q":
            qdx, triggered, mode = effect_glitch(i, t)
            if triggered:
                dx += qdx
                alpha *= 0.55
                color = _glitch_tint(color, mode)
        elif code == "t":
            visible = visible and effect_typewriter_visible(i, t)
        else:
            raise ValueError(f"未知 § 效果码: {code!r}")
    return color, round(dx, 1), round(dy, 1), alpha, visible


# ---------------------------------------------------------------------------
# 4. 渲染一行：GUI 分辨率排版 → 3× 放大 → 暗影 → 主字
# ---------------------------------------------------------------------------
def render_line(text: str,
                effects: Sequence[str] = (),
                t: float = 0.0,
                font_size: int = 16,
                font_path: str = SIMHEI,
                base_color: Sequence[int] = WHITE,
                spacing: float = SPACING_GUI) -> np.ndarray:
    """渲染一帧，返回 (CANVAS_H, CANVAS_W, 3) uint8 RGB。"""
    lay = layout_line(text, font_path, font_size, spacing)
    n = len(lay.chars)
    canvas = np.empty((CANVAS_H, CANVAS_W, 3), dtype=np.float64)
    canvas[:] = rgb01(BG)

    line_top = (CANVAS_GUI_H - (lay.ascent + lay.descent)) // 2
    x_start = (CANVAS_GUI_W - lay.width) / 2.0

    shadows: List[Tuple[np.ndarray, int, int, np.ndarray, float]] = []
    mains: List[Tuple[np.ndarray, int, int, np.ndarray, float]] = []

    for idx, item in enumerate(lay.chars):
        color, dx, dy, alpha, visible = char_state(effects, idx, n, t, base_color)
        if not visible or item.ink.size == 0:
            continue

        base_x = x_start + item.x + item.off_x
        base_y = line_top + item.off_y
        # 布局位置按整 GUI 像素对齐（保证像素锐利），效果位移则按亚像素处理
        snap_x = math.floor(base_x + 0.5)
        snap_y = math.floor(base_y + 0.5)

        for is_shadow in (True, False):
            offset_x = dx + (SHADOW_OFFSET_GUI if is_shadow else 0.0)
            offset_y = dy + (SHADOW_OFFSET_GUI if is_shadow else 0.0)
            sx = (base_x + offset_x - snap_x) * GUI_SCALE
            sy = (base_y + offset_y - snap_y) * GUI_SCALE
            int_x, frac_x = math.floor(sx), sx - math.floor(sx)
            int_y, frac_y = math.floor(sy), sy - math.floor(sy)
            layer = subpixel_shift(item.ink, frac_x, frac_y) if (frac_x or frac_y) else item.ink
            out_color = color * SHADOW_SCALE if is_shadow else color
            entry = (layer,
                     snap_x * GUI_SCALE + int_x,
                     snap_y * GUI_SCALE + int_y,
                     out_color, alpha)
            (shadows if is_shadow else mains).append(entry)

    for entry in shadows:      # 先整行暗影，再整行主字（同 MC：暗影不会压到别的字上）
        _blit(canvas, *entry)
    for entry in mains:
        _blit(canvas, *entry)

    return np.clip(np.rint(canvas * 255.0), 0, 255).astype(np.uint8)


def _blit(canvas: np.ndarray, layer: np.ndarray, x_img: int, y_img: int,
          color: np.ndarray, alpha: float = 1.0) -> None:
    """把 alpha 图层按颜色合成到整幅画布（整数图像坐标）。"""
    h, w = layer.shape
    xs, ys = max(0, x_img), max(0, y_img)
    xe, ye = min(canvas.shape[1], x_img + w), min(canvas.shape[0], y_img + h)
    if xe <= xs or ye <= ys:
        return
    sub = (layer[ys - y_img:ye - y_img, xs - x_img:xe - x_img] * alpha)[..., None]
    dst = canvas[ys:ye, xs:xe, :]
    dst *= (1.0 - sub)
    dst += color[None, None, :] * sub


# ---------------------------------------------------------------------------
# 5. GIF 输出
# ---------------------------------------------------------------------------
GIF_SPECS: List[dict] = [
    dict(name="gradient", text=TITLE, effects=("g",), frames=1, static=True,
         note="静止渐变（单帧 GIF，2.4s 循环延时）"),
    dict(name="rainbow", text=ENGLISH, effects=("h",), frames=64, font_path=MSYH,
         note="英文行（msyh 16 GUI px），演示 3.2s 循环"),
    dict(name="pulse", text=TITLE, effects=("p",), frames=50,
         note="2.5s = 2 个呼吸周期，首尾无缝"),
    dict(name="pale", text=TITLE, effects=("i",), frames=1, static=True,
         note="附赠：§[rrs:solid:#E8E4D8] 固定苍白（同样单帧 GIF）"),
    dict(name="shake", text=TITLE, effects=("s",), frames=48),
    dict(name="wave", text=TITLE, effects=("w",), frames=64),
    dict(name="glitch", text=TITLE, effects=("q",), frames=50),
    dict(name="typewriter", text=SUBTITLE, effects=("t",), frames=64, font_size=12,
         note="长副标题 12 GUI px，整周期 3.2s"),
    dict(name="combo", text=TITLE, effects=("g", "s"), frames=48,
         note="§[rrs:grad] + §[rrs:shake]：渐变同时抖动"),
]


def render_frames(spec: dict) -> List[np.ndarray]:
    kwargs = dict(text=spec["text"], effects=spec["effects"],
                  font_size=spec.get("font_size", 16),
                  font_path=spec.get("font_path", SIMHEI))
    if spec.get("static"):
        return [render_line(t=0.0, **kwargs)]
    return [render_line(t=frame / FPS, **kwargs) for frame in range(spec["frames"])]


def frames_to_gif(frames: List[np.ndarray], path: Path) -> None:
    """统一调色板量化后写 GIF（无限循环）。"""
    step = max(1, len(frames) // 16)
    sample = np.concatenate(frames[::step], axis=0) if len(frames) > 1 else frames[0]
    palette_img = Image.fromarray(sample).quantize(colors=256, method=Image.Quantize.MEDIANCUT)
    p_frames = [Image.fromarray(frame).quantize(palette=palette_img,
                                                dither=Image.Dither.FLOYDSTEINBERG)
                for frame in frames]
    if len(p_frames) == 1:
        p_frames[0].save(path, loop=0, duration=STATIC_MS, optimize=False)
    else:
        p_frames[0].save(path, save_all=True, append_images=p_frames[1:],
                         duration=FRAME_MS, loop=0, optimize=False, disposal=2)


def build_gifs(only: Optional[Sequence[str]] = None) -> List[dict]:
    built = []
    for spec in GIF_SPECS:
        if only is not None and spec["name"] not in only:
            continue
        path = OUT_DIR / f"{spec['name']}.gif"
        frames = render_frames(spec)
        frames_to_gif(frames, path)
        built.append(dict(name=spec["name"], path=path, frames=len(frames)))
        print(f"  [gif] {path.name:16s} frames={len(frames):3d} "
              f"duration={len(frames) * FRAME_MS if len(frames) > 1 else STATIC_MS:4d} ms")
    return built


# ---------------------------------------------------------------------------
# 6. 联络表 preview-sheet.png
# ---------------------------------------------------------------------------
class _TileMeta(NamedTuple):
    """联络表/文档用的效果标识：命名空间语法（默认）+ 可选短别名。"""
    code: str                                   # 内部效果码（g/h/p/i/s/w/q/t）
    ns: str                                     # 命名空间语法（默认开启）
    alias: str                                  # 短别名（可选，默认关闭）
    name: str
    desc: str
    accent: Tuple[int, int, int]


EFFECT_TILES: List[_TileMeta] = [
    _TileMeta("g", "§[rrs:grad]", "§g", "渐变·腐化",
              "沿行从腐化绿 → 王庭血红（线性光 lerp，静止）· 可写 §[rrs:grad:#6FBF4A:#8B1E2D]",
              ACCENT_GREEN),
    _TileMeta("h", "§[rrs:rainbow]", "§h", "彩虹流彩",
              "hue=(i·0.055+t·0.12) mod 1 · S=0.72 · V=1.0", (0xD9, 0x9F, 0x3F)),
    _TileMeta("p", "§[rrs:pulse]", "§p", "呼吸",
              "k=0.65+0.35·(0.5+0.5·sin(2π(0.8t+0.03i)))", (0xB9, 0xA8, 0xC9)),
    _TileMeta("i", "§[rrs:solid:#E8E4D8]", "—", "苍白",
              "固定骨白 #E8E4D8，不受时间影响（纯色效果无短别名）", INK_PALE),
    _TileMeta("s", "§[rrs:shake]", "§s", "抖动",
              "双频正弦叠加 dx/dy，±2 GUI px，亚像素绘制", (0xC4, 0x6A, 0x4A)),
    _TileMeta("w", "§[rrs:wave]", "§w", "波浪",
              "dy = 1.6·sin(0.55i − 4.0t)，dx = 0", (0x5F, 0xA8, 0xB8)),
    _TileMeta("q", "§[rrs:glitch]", "§q", "故障",
              "b=⌊12t⌋；hash(i,b)<0.10 时位移 ±3~7 / α0.55 / 色偏", (0xB0, 0x3A, 0x5B)),
    _TileMeta("t", "§[rrs:type]", "§t", "打字机",
              "3.2s 周期：前 1.6s 以 12 字/s 逐字显现", (0x8A, 0x7F, 0x9A)),
]
COMBO_ACCENT = (0xC9, 0x7A, 0x4A)


def _pick_shake_time(effects: Sequence[str], n: int, frames: int) -> float:
    best_energy = -1.0
    best_t = 0.0
    for frame in range(frames):
        t = frame / FPS
        energy = 0.0
        for i in range(n):
            _, dx, dy, _, visible = char_state(effects, i, n, t)
            if visible:
                energy += abs(dx) + abs(dy)
        if energy > best_energy:
            best_energy, best_t = energy, t
    return best_t


def _pick_glitch_time(n: int, frames: int) -> float:
    best_count = -1
    best_t = 0.0
    for frame in range(frames):
        t = frame / FPS
        count = sum(1 for i in range(n) if effect_glitch(i, t)[1])
        if count > best_count:
            best_count, best_t = count, t
    return best_t


def _static_frame_specs() -> Tuple[List[Tuple[str, np.ndarray, str, str, str]], np.ndarray, float]:
    """为联络表挑选每个效果的静态帧（尽量选效果最明显的时刻）。"""
    n_title = len(TITLE)
    t_shake = _pick_shake_time(("s",), n_title, 48)
    t_combo = _pick_shake_time(("g", "s"), n_title, 48)
    t_glitch = _pick_glitch_time(n_title, 50)

    tiles = [
        ("g", render_line(TITLE, ("g",), t=0.0), TITLE, "16", "t=0.00s"),
        ("h", render_line(ENGLISH, ("h",), t=0.55, font_path=MSYH), ENGLISH, "16", "t=0.55s"),
        ("p", render_line(TITLE, ("p",), t=0.95), TITLE, "16", "t=0.95s"),
        ("i", render_line(TITLE, ("i",), t=0.0), TITLE, "16", "t=0.00s"),
        ("s", render_line(TITLE, ("s",), t=t_shake), TITLE, "16", f"t={t_shake:.2f}s"),
        ("w", render_line(TITLE, ("w",), t=0.30), TITLE, "16", "t=0.30s"),
        ("q", render_line(TITLE, ("q",), t=t_glitch), TITLE, "16", f"t={t_glitch:.2f}s"),
        ("t", render_line(SUBTITLE, ("t",), t=0.90, font_size=12), SUBTITLE, "12", "t=0.90s"),
    ]
    combo_frame = render_line(TITLE, ("g", "s"), t=t_combo)
    return tiles, combo_frame, t_combo


def make_contact_sheet() -> Path:
    margin = 52
    gap_x, gap_y = 32, 26
    frame_w, frame_h = CANVAS_W, CANVAS_H
    tile_w, tile_h = frame_w + 2, frame_h + 2 + 62
    label_h = 62

    cols = 2
    sheet_w = margin * 2 + cols * tile_w + (cols - 1) * gap_x
    grid_y = 196
    rows = 4
    grid_h = rows * tile_h + (rows - 1) * gap_y
    combo_y = grid_y + grid_h + 30
    combo_h = tile_h
    footer_y = combo_y + combo_h + 30
    sheet_h = footer_y + 112

    sheet = Image.new("RGB", (sheet_w, sheet_h), SHEET_BG)
    draw = ImageDraw.Draw(sheet)

    f_kicker = ImageFont.truetype(MSYH, 17)
    f_title = ImageFont.truetype(MSYH_BD, 44)
    f_sub = ImageFont.truetype(MSYH, 18)
    f_note = ImageFont.truetype(MSYH, 15)
    f_ns = ImageFont.truetype(MSYH_BD, 20)       # 命名空间语法（默认）
    f_name = ImageFont.truetype(MSYH_BD, 22)
    f_desc = ImageFont.truetype(MSYH, 15)

    # ---- 页眉 -----------------------------------------------------------
    draw.text((margin, 38), "ROTTEN RUINS SPLENDIDING  ·  MINECRAFT 1.20.1 HORROR MOD",
              font=f_kicker, fill=(0x7A, 0x6C, 0x7A))
    draw.text((margin, 62), "§ 分节符文本特效 · 方案级视觉预览",
              font=f_title, fill=INK_PALE)
    draw.text((margin, 124),
              "命名空间语法（默认）：§[rrs:grad] · §[rrs:rainbow] · §[rrs:pulse] · §[rrs:solid:#E8E4D8] · §[rrs:shake] · §[rrs:wave] · §[rrs:glitch] · §[rrs:type]",
              font=f_sub, fill=INK_DIM)
    draw.text((margin, 148),
              "可叠加，用 §[/] 关闭 · 短别名默认关闭（§g §h §p §s §w §q §t）· 非游戏内真实渲染，仅方案示意",
              font=f_sub, fill=INK_DIM)

    # 右上：§[rrs:grad] 线性光渐变条（直观展示 A → B）
    bar_w, bar_h = 300, 18
    bar_x = sheet_w - margin - bar_w
    bar_y = 78
    draw.text((bar_x, 52), "§[rrs:grad] 线性光插值 A → B（避免中段发灰）", font=f_note, fill=INK_DIM)
    for px in range(bar_w):
        color = lerp_linear(CORRUPT_GREEN, COURT_BLOOD, px / (bar_w - 1))
        draw.line([(bar_x + px, bar_y), (bar_x + px, bar_y + bar_h)],
                  fill=tuple(int(round(c * 255)) for c in color))
    draw.text((bar_x, bar_y + bar_h + 6), "A #6FBF4A 腐化绿", font=f_note, fill=INK_DIM)
    draw.text((bar_x + bar_w - 136, bar_y + bar_h + 6), "B #8B1E2D 王庭血红",
              font=f_note, fill=INK_DIM)

    draw.line([(margin, 180), (sheet_w - margin, 180)], fill=(0x2E, 0x22, 0x2C), width=1)

    # ---- 8 个效果格 ------------------------------------------------------
    tiles, combo_frame, _ = _static_frame_specs()
    for idx, (code, frame_img, text, font_px, t_note) in enumerate(tiles):
        row, col = divmod(idx, cols)
        x = margin + col * (tile_w + gap_x)
        y = grid_y + row * (tile_h + gap_y)
        _paste_tile(sheet, draw, x, y, frame_img, code, label_h, tile_w, tile_h)

        # 文字标签：优先命名空间语法；第二行给出可选短别名与说明
        meta = _tile_meta(code)
        ns_w = draw.textlength(meta.ns, font=f_ns)
        draw.text((x + 14, y + frame_h + 2 + 9), meta.ns, font=f_ns, fill=meta.accent)
        draw.text((x + 14 + ns_w + 18, y + frame_h + 2 + 7), meta.name, font=f_name, fill=INK_PALE)
        alias_txt = f"短别名 {meta.alias}" if meta.alias != "—" else "无短别名"
        draw.text((x + 14, y + frame_h + 2 + 37), f"{alias_txt} · {meta.desc}",
                  font=f_desc, fill=(0x92, 0x84, 0x8F))
        draw.text((x + tile_w - 12 - draw.textlength(t_note, font=f_note),
                   y + frame_h + 2 + 12), t_note, font=f_note, fill=INK_DIMMER)

    # ---- 组合示例（整行宽） ---------------------------------------------
    combo_x = margin
    combo_frame_x = combo_x + (sheet_w - 2 * margin - frame_w) // 2
    sheet.paste(Image.fromarray(combo_frame), (combo_frame_x, combo_y + 1))
    draw.rectangle([combo_x, combo_y, combo_x + sheet_w - 2 * margin - 1, combo_y + combo_h - 1],
                   outline=SHEET_BORDER, width=1)
    draw.rectangle([combo_x + 1, combo_y + 1, combo_x + sheet_w - 2 * margin - 2,
                    combo_y + frame_h + 2], fill=None)
    draw.rectangle([combo_x + 1, combo_y + frame_h + 2 + 1,
                    combo_x + sheet_w - 2 * margin - 2, combo_y + combo_h - 2],
                   fill=SHEET_PANEL)
    combo_ns = "§[rrs:grad] + §[rrs:shake]"
    combo_ns_w = draw.textlength(combo_ns, font=f_ns)
    draw.text((combo_x + 16, combo_y + frame_h + 2 + 10), combo_ns, font=f_ns, fill=COMBO_ACCENT)
    draw.text((combo_x + 16 + combo_ns_w + 20, combo_y + frame_h + 2 + 7), "组合示例 · 渐变同时抖动",
              font=f_name, fill=INK_PALE)
    draw.text((combo_x + 16, combo_y + frame_h + 2 + 37),
              "命名空间代码可叠加：§[rrs:grad] 的逐字渐变与 §[rrs:shake] 的逐字抖动同时生效（短别名写法：§g…§s…§[/]）",
              font=f_desc, fill=(0x92, 0x84, 0x8F))

    # ---- 页脚 -----------------------------------------------------------
    footer_lines = [
        "生成：docs/proposals/text-effects-preview/generate.py · SimHei 16 GUI px 二值化（副标题 12）· 最近邻 3× 放大 · 暗影 = 右下 1 GUI px、颜色 = 主色 ×0.25 · 背景 #151015",
        "GIF 20 fps 无限循环 · 本图与 GIF 均为方案示意，游戏内实际效果以 MC 像素字体 + 阴影为准",
        "语法：命名空间 §[rrs:…]（默认，避免与 Bedrock 材质色码 / Phoenix / Brilliant Text 裸单字母码冲突）；短别名 §g §h §p §s §w §q §t 默认关闭",
    ]
    for line_idx, line in enumerate(footer_lines):
        draw.text((margin, footer_y + line_idx * 26), line, font=f_note, fill=INK_DIMMER)

    path = OUT_DIR / "preview-sheet.png"
    sheet.save(path)
    print(f"  [png] {path.name} {sheet.size[0]}x{sheet.size[1]}")
    return path


def _tile_meta(code: str) -> _TileMeta:
    for meta in EFFECT_TILES:
        if meta.code == code:
            return meta
    raise KeyError(code)


def _paste_tile(sheet: Image.Image, draw: ImageDraw.ImageDraw, x: int, y: int,
                frame_img: np.ndarray, code: str, label_h: int,
                tile_w: int, tile_h: int) -> None:
    frame_h = CANVAS_H
    sheet.paste(Image.fromarray(frame_img), (x + 1, y + 1))
    draw.rectangle([x, y, x + tile_w - 1, y + tile_h - 1], outline=SHEET_BORDER, width=1)
    draw.rectangle([x + 1, y + frame_h + 2, x + tile_w - 2, y + tile_h - 2],
                   fill=SHEET_PANEL)


# ---------------------------------------------------------------------------
# 7. 自检：读回 GIF / PNG，核对帧数、尺寸、循环与时长
# ---------------------------------------------------------------------------
def verify_outputs() -> bool:
    ok = True
    print("\n自检（重新读回文件）：")
    print(f"  {'文件':<18}{'尺寸':>12}{'编码帧':>7}{'渲染帧':>7}{'总时长':>8}{'大小':>8}{'loop':>6}")
    for spec in GIF_SPECS:
        path = OUT_DIR / f"{spec['name']}.gif"
        if not path.exists():
            print(f"  [MISS] {path.name}")
            ok = False
            continue
        render_frames_count = 1 if spec.get("static") else spec["frames"]
        expected_total_ms = STATIC_MS if spec.get("static") else render_frames_count * FRAME_MS
        with Image.open(path) as im:
            size = im.size
            loop = im.info.get("loop", None)
            n_frames = getattr(im, "n_frames", 1)
            total_ms = 0
            for frame_idx in range(n_frames):
                im.seek(frame_idx)
                total_ms += int(im.info.get("duration", 0))
            size_kb = path.stat().st_size / 1024.0
        # Pillow 编码 GIF 时会把连续相同的帧合并（累计 duration），
        # 所以"编码帧数"可能小于"渲染帧数"，但总时长必须一致。
        if (size != (CANVAS_W, CANVAS_H) or loop != 0
                or n_frames > render_frames_count or total_ms != expected_total_ms):
            ok = False
        print(f"  {path.name:<18}{str(size):>12}{n_frames:>7}{render_frames_count:>9}"
              f"{total_ms:>7}ms{size_kb:>7.0f}K{str(loop):>6}")
    sheet = OUT_DIR / "preview-sheet.png"
    if sheet.exists():
        with Image.open(sheet) as im:
            print(f"  {'preview-sheet.png':<18}{str(im.size):>12}")
    else:
        ok = False
        print("  [MISS] preview-sheet.png")
    print("  -> 自检通过" if ok else "  -> 自检失败")
    return ok


# ---------------------------------------------------------------------------
# 8. main
# ---------------------------------------------------------------------------
def main(argv: Sequence[str]) -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    targets = [a for a in argv[1:] if not a.startswith("-")]
    gif_names = {spec["name"] for spec in GIF_SPECS}
    only_gifs = [name for name in targets if name in gif_names]
    want_gifs = not targets or bool(only_gifs)
    want_sheet = not targets or "sheet" in targets or "all" in targets
    full_run = not targets or "all" in targets

    print(f"输出目录: {OUT_DIR}")
    if want_gifs:
        print("生成 GIF：")
        build_gifs(only_gifs or None)
    if want_sheet:
        print("生成联络表：")
        make_contact_sheet()
    if not full_run:
        print("\n（局部生成模式，跳过整体自检；完整自检请直接运行 `python generate.py`）")
        return 0
    return 0 if verify_outputs() else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
