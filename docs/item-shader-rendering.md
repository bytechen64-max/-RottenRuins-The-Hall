# 物品着色器渲染系统

## 概述

提供三套核心视觉效果：

| 功能 | 说明 | 渲染位置 |
|------|------|----------|
| **物品轮廓 (Outline)** | 缩放膨胀壳 + shader 着色，可选纯色/渐变色/噪声 flow 三种 shader | 全上下文 |
| **GUI 辉光 (Glint)** | 自定义光效叠加，支持单色/双色/彩虹/自动采样四种模式 | 物品栏、界面 |
| **自动纹理采样** | 从物品纹理自动提取 8 色色板 | 以上两者均可使用 |

物品轮廓有两种 **注册方式** 和 **两种渲染模式**。

---

## 五分钟上手

### 方式一：GlintRenderManager（外部注册）

```java
private static void registerGlintProfiles() {
    // 一键：概览 + warp_fbm 轮廓 + 自动采样 GUI 辉光
    GlintRenderManager.registerForItem(MY_ITEM.get(),
            GlintEffectProfile.builder()
                .color(0xFF44AAFF)                                   // ARGB 色调
                .colorMode(GlintEffectProfile.ColorMode.AUTO_SAMPLE_SCROLL)
                .bloomStrength(0.6f).bloomRadius(1.2f)
                .speed(0.9f).intensity(0.85f)
                .outlineShaderKey("warp_fbm")                        // ★ 轮廓 shader
                .build());
}
```

### 方式二：ICustomOutline 接口（物品自描述）

物品类直接实现接口，无需外部注册：

```java
public class MyLegendarySword extends Item implements ICustomOutline {

    // ── 必须实现 ──
    @Override public int outlineColor() { return 0xFFFF6600; }

    // ── 用 warp_fbm 着色器 ──
    @Override public String outlineShaderKey() { return "warp_fbm"; }

    // ── 可选：GUI 辉光 ──
    @Override public GlintSettings glintSettings() {
        return GlintSettings.builder()
            .color(0xFFFFD700).secondaryColor(0xFFA500)
            .colorMode(ColorMode.DUAL_SCROLL).speed(0.5f).build();
    }
}
```

`ICustomOutline` 优先于 `GlintRenderManager`。

---

## 轮廓 Shader 选择

通过 `outlineShaderKey` 切换内置着色器：

| Key | 说明 | 运行效果 |
|-----|------|----------|
| `"default"` | 纯色剪影（`rendertype_item_world_outline`） | 物品形状填充 `outlineColor` |
| `"gradient"` | 横向流动渐变色（`rendertype_item_gradient_outline`） | 1~8 色横向滚动，需 `configureOutlineShader` 设色板 |
| `"warp_fbm"` | 分形布朗运动噪声 + 热力色谱（`rendertype_item_warp_fbm`） | 动态噪声纹理，颜色由 `outlineColor` 色调控制 |

### warp_fbm 色调示例

```java
// 蓝色（钻石）
GlintRenderManager.registerForItem(Items.DIAMOND,
    GlintEffectProfile.builder()
        .color(0xFF4466FF)                                     // RGB → shader TintColor
        .outlineShaderKey("warp_fbm")
        .build());

// 金色
GlintRenderManager.registerForItem(Items.GOLD_INGOT,
    GlintEffectProfile.builder()
        .color(0xFFFFD700).outlineShaderKey("warp_fbm").build());

// 绿色（绿宝石）
GlintRenderManager.registerForItem(Items.EMERALD,
    GlintEffectProfile.builder()
        .color(0xFF44FF44).outlineShaderKey("warp_fbm").build());
```

### gradient 色板示例

```java
public class GradientSword extends Item implements ICustomOutline {
    @Override public int outlineColor() { return 0xFFFF6600; }
    @Override public String outlineShaderKey() { return "gradient"; }

    @Override
    public void configureOutlineShader(ShaderInstance s) {
        setIntUniform(s, "ColorCount", 3);
        setIntUniform(s, "Color0", 0xFFFF6600);
        setIntUniform(s, "Color1", 0xFFFFD700);
        setIntUniform(s, "Color2", 0xFF23F0FF);
        setFloatUniform(s, "FlowSpeed", 0.3f);
        setFloatUniform(s, "GradientSpan", 1.2f);
    }
}
```

---

## API 参考

### 一、物品轮廓 — 通用参数

```java
// 渲染模式
HeldItemOutlineSettings.setOutlineMode(HeldItemOutlineSettings.OutlineMode.VERTEX_SHADER); // 全上下文
HeldItemOutlineSettings.setOutlineMode(HeldItemOutlineSettings.OutlineMode.FBO);            // 仅一手 + 泛光

// 白名单注册
HeldItemGlintHelper.enableForItems(RegisterItem.EXAMPLE_ITEM.get(), RegisterItem.EXAMPLE_GEO_ITEM.get());
HeldItemGlintHelper.enableForAll();
HeldItemGlintHelper.disableAll();
```

### 二、GlintEffectProfile.Builder — 轮廓参数

```java
GlintEffectProfile.builder()
    .color(0xFFFF6600)              // ARGB 色调
    .outlineShaderKey("warp_fbm")   // 轮廓 shader key
    .worldOutlineWidth(0.06f)       // 缩放 1.06×，默认 0.06
    .worldOutline(false)            // 关闭轮廓
    .build();
```

### 三、ICustomOutline 接口

| 方法 | 返回 | 默认值 | 说明 |
|------|------|--------|------|
| `outlineColor()` | `int` | — | **必须**。ARGB 格式 |
| `outlineShaderKey()` | `String` | `null` | `"default"` / `"gradient"` / `"warp_fbm"` |
| `outlineWidth()` | `float` | `0.06` | 缩放因子 |
| `outlineBlend()` | `BlendMode` | `ADDITIVE` | `TRANSLUCENT` 或 `ADDITIVE` |
| `outlineEnabled(ctx)` | `boolean` | 世界=true | 上下文开关 |
| `configureOutlineShader(ShaderInstance)` | `void` | 空 | 自定义 uniform |
| `glintSettings()` | `GlintSettings` | `null` | 返回非 null 自动启用 GUI 辉光 |

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
| `rendertype_item_world_outline.*` | 默认纯色轮廓 |
| `rendertype_item_gradient_outline.*` | 流动渐变色轮廓 |
| `rendertype_item_warp_fbm.*` | warp fBM 噪声 + 热力色谱轮廓 |
| `rendertype_item_glint.*` | GUI 辉光 |
| `held_item_outline.*` / `held_item_bloom*` | FBO 描边+泛光 |

---

## 配置文件

`SplendidingConfig.use_shader = true` — 着色器总开关。

---

## 注意事项

1. 所有注册在 `FMLClientSetupEvent` 完成
2. 物品必须有模型 JSON 文件
3. `VERTEX_SHADER` 模式兼容光影（自动跳过 shadow pass）
4. `FBO` 模式自动检测 Oculus / Embeddium
5. 轮廓宽度通过 `vertexColor.a` 控制 alpha，shader 内 `0.85` 基准
