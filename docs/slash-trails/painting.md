# 像素刀光的绘制和替换

示例贴图为 **64 × 16 像素的透明 PNG**，程序按整数像素生成，不做模糊、渐变滤镜或抗锯齿。放大示意：[pixel-guide.png](pixel-guide.png)。这是一张沿刀光带展开的贴图，游戏里的弧线来自网格，不需要在 PNG 里画完整圆弧。

- 横向：左边为较早的拖尾，右边为最新的刀光前缘。
- 纵向：刀光带宽。中间画亮色核心，上下用少量深色阶梯轮廓，背景留透明。
- 最左保留透明空间，尾巴细，向右逐渐加宽，最右收成像素尖端。
- 用 1 像素硬笔画，放大查看时选择“最近邻”，不要平滑缩放；不要导出带白底的图片。

当前示例只用了四档颜色／透明度，便于自己改画：白色核心、浅蓝白主体、蓝灰边缘、一圈低透明度像素外沿。纯铜剑还会叠加铜色顶点染色；想完全显示贴图原色，把武器 `SlashTrail` 的颜色设为 `0xFFFFFFFF`。

## 直接替换

编辑或替换 `src/main/resources/assets/rotten_ruins/textures/effect/slash_crescent.png`，保留 RGBA 透明通道。旁边的 `.png.mcmeta` 已关闭模糊。运行中的开发客户端按 F3+T 重载资源；发布版替换后需重新打包，或使用相同资源路径的资源包覆盖。

可以用 32×8、64×16、128×32 等分辨率，但建议维持 **4:1 宽高比**。图像分辨率影响像素颗粒感，实际覆盖范围由[物品刀光锚点](../slash-anchor-editor.md)决定。纵向 0–1 对应沿刀身排列的第一个至最后一个锚点。

## 每把武器单独用图

把新 PNG 放入 `assets/rotten_ruins/textures/effect/your_slash.png`，然后给该武器返回：

```java
new SlashTrail(SlashTrail.TEXTURE,
    ResourceLocation.fromNamespaceAndPath("rotten_ruins", "textures/effect/your_slash.png"),
    0xFFFFFFFF, 0.48F, 2.0F, 7)
```

参数依次是渲染器、贴图、ARGB 染色、旧世界宽度、旧半径、旧残留 tick。内置轨迹使用锚点配置的范围与寿命，旧几何参数保留供自定义绘制器使用。四种挥砍共用同一张图。

`python tools/generate-slash-texture.py` 可以重建示例和放大图。**手工改画后不要再运行生成脚本，否则会覆盖手绘版本。** 着色器刀光不采样此贴图，是独立的程序化绘制方案。
