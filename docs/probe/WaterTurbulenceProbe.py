"""独立数值探针（不属于模组源码，只用来在**不开游戏**的前提下验证着色器数学）。

目的：{@code cosmic.fsh} 里的水面湍流（{@code SILENT_DAYLIGHT} / useType 18）是纯逐像素
数学，没有纹理、没有几何依赖 —— 所以可以在 CPU 上用 float32 复算一遍，提前回答两个
光看代码回答不了的问题：

1. **图案到底长什么样？** 脚本把结果按"遮罩白区 = 水、黑区 = 本体贴图"合成到
   {@code silent_daylight.png} 上，写成 PNG（放大 6 倍，NEAREST），直接肉眼看。
2. **`wavePatternScale` / `waveTimeScale` 取多少合适？** 打印两个量：
   - 空间：遮罩内像素值的动态范围（太小 = 整片一个色，白改了）；
   - 时间：相邻 tick 的平均像素变化 ÷ 像素标准差。cosmic 的 `time` 是**游戏 tick**
     （20 Hz），这个比值就是"每 tick 走图案的百分之几" —— 太大（>0.2）会看成抖动
     而不是流动。

跑法（用仓库里那个 Python 就行，只需要 numpy + Pillow）：

    python docs/probe/WaterTurbulenceProbe.py [输出目录，默认 run/]

结果（实测）：
    scale=1 每 tick 变化比 0.067 · scale=2 0.098 · scale=4 0.088  → 都在"流动"区间
    空间动态范围三个 scale 都在 0.98 以上 → 不会糊成一个颜色
    默认取 scale=2.0（肉眼看水纹最清楚；4.0 已经偏碎，像噪点）
"""

import os
import sys

import numpy as np
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "hall", "textures", "item")

F32 = np.float32
WATER_MAX_ITER = 5
WATER_TAU = F32(6.28318530718)

# 与 cosmic.json 里的默认值保持一致
WAVE_TIME_SCALE = F32(0.025)
WAVE_PATTERN_SCALE = F32(2.0)


def water_turbulence(uv, wt):
    """cosmic.fsh 的 waterTurbulence() 的逐行复算（uv: (...,2)，全部 float32）。

    唯一刻意的差别：GLSL 的 `mod(x, y)` 与 `np.mod` 语义相同（结果恒在 [0, y)），
    length() 用 float64 累加后转回 float32 —— numpy 没有 float32 的 hypot 归约，
    而这一处的精度差异不影响图案。
    """
    wtm = wt + F32(23.0)

    # vec2 p = mod(uv * WATER_TAU, WATER_TAU) - 250.0;
    p = np.mod(uv * WATER_TAU, WATER_TAU) - F32(250.0)
    i = p.copy()

    c = np.ones(p.shape[:-1], dtype=F32)
    inten = F32(0.005)

    for n in range(WATER_MAX_ITER):
        tn = wtm * (F32(1.0) - (F32(3.5) / F32(n + 1)))
        ix, iy = i[..., 0], i[..., 1]
        i = p + np.stack([np.cos(tn - ix) + np.sin(tn + iy),
                          np.sin(tn - iy) + np.cos(tn + ix)], axis=-1)
        ix, iy = i[..., 0], i[..., 1]
        d = np.stack([p[..., 0] / (np.sin(ix + tn) / inten),
                      p[..., 1] / (np.cos(iy + tn) / inten)], axis=-1)
        c = c + F32(1.0) / np.linalg.norm(d.astype(np.float64), axis=-1).astype(F32)

    c = c / F32(WATER_MAX_ITER)
    c = F32(1.17) - np.power(c, F32(1.4))
    colour = np.power(np.abs(c), F32(8.0))
    rgb = np.stack([colour, colour, colour], axis=-1)
    return np.clip(rgb + np.array([0.0, 0.35, 0.5], dtype=F32), 0.0, 1.0)


def load(name):
    path = os.path.join(TEX_DIR, name)
    return np.asarray(Image.open(path).convert("RGBA"), dtype=F32) / 255.0


def uv_grid(size):
    ys, xs = np.mgrid[0:size, 0:size].astype(F32)
    return np.stack([(xs + F32(0.5)) / F32(size), (ys + F32(0.5)) / F32(size)], axis=-1)


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "run")
    os.makedirs(out_dir, exist_ok=True)

    base = load("silent_daylight.png")
    mask = load("silent_daylight_mask.png")
    size = base.shape[0]
    uv = uv_grid(size)
    blade = mask[..., 0] > 0.5

    print(f"本体贴图 {base.shape[1]}x{base.shape[0]} · 遮罩白区 {int(blade.sum())} px "
          f"· 遮罩红通道取值 {np.unique(mask[..., 0])}")

    for scale in (1.0, 2.0, 4.0):
        frames = [water_turbulence(uv * F32(scale), F32(t) * WAVE_TIME_SCALE) for t in range(6)]
        vals = np.concatenate([f[blade] for f in frames])
        steps = [float(np.abs(frames[k + 1][blade] - frames[k][blade]).mean()) for k in range(5)]
        print(f"scale={scale:<4} 空间 min={vals.min():.3f} max={vals.max():.3f} "
              f"std={vals.std():.4f} | 每 tick 平均变化={np.mean(steps):.4f} "
              f"比值={np.mean(steps) / vals.std():.3f}")

        # 合成：星空层是 TRANSLUCENT，alpha = 遮罩 × opacity（这里 opacity=1）
        a = mask[..., 0:1]
        comp = base[..., :3] * (1 - a) + frames[0] * a
        img = Image.fromarray((np.clip(comp, 0, 1) * 255).astype(np.uint8))
        out = os.path.join(out_dir, f"water_preview_s{scale:g}.png")
        img.resize((size * 6, size * 6), Image.NEAREST).save(out)
        print(f"          → {out}")


if __name__ == "__main__":
    main()
