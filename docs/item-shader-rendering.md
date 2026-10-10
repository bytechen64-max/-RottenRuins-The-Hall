# 物品着色器渲染系统

## 概述

| 功能 | 说明 | 渲染位置 |
|------|------|----------|
| **物品轮廓 (Outline)** | 屏幕空间「剪影遮罩 + 环形膨胀」，等宽硬边环，只画在剪影**外侧** | 全上下文 |
| **GUI 辉光 (Glint)** | 自定义光效叠加，支持单色/双色/彩虹/自动采样四种模式 | 物品栏、界面 |
| **Mask 效果层 (Glow …)** | 可叠加在星空层之上的独立效果层（一张遮罩 + 一个效果着色器），见 **[mask-layers.md](mask-layers.md)** | 全上下文 |
| **自动纹理采样** | 从物品纹理自动提取 8 色色板 | 以上两者均可使用 |

> 轮廓已于 2025 重构，旧的"模型放大壳"整体移除。
> 架构、根因、时机与迁移说明见 **[item-shader-outline.md](item-shader-outline.md)**。

---

## 五分钟上手

### 方式一：GlintRenderManager（外部注册）

```java
private static void registerGlintProfiles() {
    // 一键：GUI 辉光（自动采样）+ 2.5px 宽的流动彩色描边
    GlintRenderManager.registerForItem(MY_ITEM.get(),
            GlintEffectProfile.builder()
                .colorMode(GlintEffectProfile.ColorMode.AUTO_SAMPLE_SCROLL)
                .bloomStrength(0.6f).bloomRadius(1.2f)      // 仅 GUI 辉光用
                .speed(0.9f).intensity(0.85f)
                .outlineShaderKey("warp_fbm")               // ★ 颜色模式
                .worldOutlinePixelWidth(2.0f)               // ★ 描边宽度（像素）
                .build());
}
```

### 方式二：ICustomOutline 接口（物品自描述）

```java
public class MyLegendarySword extends Item implements ICustomOutline {

    @Override public int outlineColor() { return 0xFFFF6600; }   // 必须实现

    @Override public float outlinePixelWidth() { return 2.0f; }  // 2px 宽的边
    @Override public String outlineShaderKey() { return "gradient"; }
    @Override public int outlineSecondaryColor() { return 0xFF23F0FF; }

    @Override public GlintSettings glintSettings() {              // 可选：GUI 辉光
        return GlintSettings.builder()
            .color(0xFFFFD700).secondaryColor(0xFFA500)
            .colorMode(ColorMode.DUAL_SCROLL).speed(0.5f).build();
    }
}
```

`ICustomOutline` 优先于 `GlintRenderManager`。

---

## 轮廓：颜色模式

`outlineShaderKey()` 现在**只决定颜色怎么算**，不再对应独立着色器与 RenderType。
三条"描边着色器"已经收敛成同一个几何算法（`dilate − self`），所以描边形状在任何模式下都是等宽硬边。

| Key | 说明 |
|-----|------|
| `null` / `"default"` | 纯色，用 `outlineColor()` |
| `"gradient"` | 主色 ↔ 副色 沿物品表面流动，需 `outlineSecondaryColor()` |
| `"warp_fbm"` | 彩虹流动（旧噪声风格的动态观感） |

颜色是在**剪影遮罩 pass** 里逐像素求值并烘进遮罩 rgb 的，因此同一帧里不同物品可以各自
有不同颜色，而只需要一次全屏合成。

---

## API 参考

### 一、物品轮廓 — 通用参数

```java
// 描边宽度在物品侧配置：
//   ICustomOutline.outlinePixelWidth()          或
//   GlintEffectProfile.Builder.worldOutlinePixelWidth(float)
// 单位是屏幕像素，等宽靠它（与物品大小、远近无关）。

// 渲染总开关
SplendidingConfig.use_shader = true;
```

### 二、GlintEffectProfile.Builder — 轮廓参数

```java
GlintEffectProfile.builder()
    .color(0xFFFF6600)                  // ARGB 色调
    .outlineShaderKey("warp_fbm")       // 颜色模式
    .worldOutlinePixelWidth(2.5f)       // 描边宽度，屏幕像素（默认 2.5）
    .worldOutlineOpacity(1.0f)          // 不透明度（默认 1.0）
    .worldOutline(false)                // 关闭描边
    .build();
```

### 三、ICustomOutline 接口

| 方法 | 返回 | 默认值 | 说明 |
|------|------|--------|------|
| `outlineColor()` | `int` | — | **必须**。ARGB 格式 |
| `outlinePixelWidth()` | `float` | `2.5` | 描边宽度，**屏幕像素** |
| `outlineOpacity()` | `float` | `1.0` | 整体不透明度 |
| `outlineAlphaCutoff()` | `float` | `0.1` | 剪影判定的纹理 alpha 阈值 |
| `outlineSecondaryColor()` | `int` | `0`（不启用） | 副色，仅 `gradient` 用 |
| `outlineShaderKey()` | `String` | `null` | 颜色模式（见上表） |
| `outlineBlend()` | `BlendMode` | `ADDITIVE` | `TRANSLUCENT` 或 `ADDITIVE` |
| `outlineEnabled(ctx)` | `boolean` | 世界=true | 上下文开关 |
| `glintSettings()` | `GlintSettings` | `null` | 返回非 null 自动启用 GUI 辉光 |
| ~~`outlineWidth()`~~ | `float` | ~~`0.06`~~ | **已弃用**：旧的模型缩放系数，不再生效 |
| ~~`configureOutlineShader(ShaderInstance)`~~ | `void` | 空 | **已弃用**：仍会被调用但 uniform 名已变，等于空操作 |

### 四、GUI 辉光 — GlintEffectProfile 快捷方法

```java
GlintEffectProfile.autoSampleWithBloom(item)   // 自动采样 + 泛光
GlintEffectProfile.singleColor(0xFFD700)        // 单色
GlintEffectProfile.dualColor(0xFF6600, 0xFFFFD700) // 双色滚动
GlintEffectProfile.rainbow()                     // 彩虹
GlintEffectProfile.goldenGlint()                 // 金色预设
```

---

## 着色器文件

| 着色器 | 说明 |
|--------|------|
| `outline_mask.*` | 剪影遮罩：纹理 alpha 裁剪 + 颜色模式求值 → 写离屏遮罩 |
| `outline_ring.*` | 全屏环形合成：16 方向 × 3 半径圆盘采样，`dilate − self` |
| `rendertype_item_glint.*` | GUI 辉光 |
| `mask_glow.*` | mask 效果层的单 pass 泛光（`dilate − mask`，加法混合，见 mask-layers.md） |
| `held_item_outline.*` / `held_item_bloom*` | 一手 FBO 描边+泛光（子系统当前被禁用，见 item-shader-outline.md 第七章） |

---

## 配置文件

`SplendidingConfig.use_shader = true` — 着色器总开关。

`SplendidingConfig.outlineWidthScale = 1.0` — 描边宽度全局倍率（1.5 → 整体加厚 50%，
0.6 → 整体变细）。改这个不用重新编译，单件物品仍可用 `outlinePixelWidth()` 单独覆盖。

---

## 注意事项

1. 所有注册在 `FMLClientSetupEvent` 完成
2. 物品必须有模型 JSON 文件
3. 描边在 GUI / 一手 / 三手 / 掉落物全上下文统一生效，无需分路配置
4. 光影包（Oculus/Iris）下**只有最后那次合成**被推迟到 `renderLevel` 之后；剪影遮罩仍在
   物品渲染当帧立刻捕获，因为只有那一刻场景深度才是对的
5. 挂不上场景深度时会退化成"不遮挡"，不会崩 —— 详见 item-shader-outline.md 第六章
