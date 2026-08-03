# Cosmic Layer 星空渲染层集成指南

## 概述

Cosmic layer 是从 Live 模组移植的程序化星空着色器渲染层。它在物品表面叠加一个动态的星云星场效果，星星会跟随玩家视角旋转，不同距离显示不同颜色。

星空着色器使用遮罩纹理的**红色通道**控制可见范围——白色像素处星星可见，黑色像素处隐藏。

## 三种集成方式

| 方式 | 适用场景 | 光影兼容 |
|------|----------|----------|
| **JSON 模型加载器** | 单个物品、需要完整控制 | 完全兼容 |
| **ICosmicLayer 接口** | 自定义 Item 类 | 完全兼容 |
| **CosmicLayerRegistry 注册** | 原版物品、无法修改源码的物品 | 完全兼容 |

三种方式最终都走 `BakedModelCosmic` 渲染管线，光影兼容能力完全相同。

## 视觉样式预设

6 种预设样式，注册时通过 `CosmicStyle` 枚举选择：

| 预设 | 枚举值 | 背景 | 星星颜色 | 效果 |
|------|--------|------|----------|------|
| **深空** | `DEEP_SPACE` | FBM 星云 + 极光色带 | 冷白/蓝 | 默认——深邃紫黑虚空 |
| **彩虹流** | `RAINBOW_FLOW` | 彩虹渐变 | 彩虹闪烁 | 绚丽多彩 |
| **纯暗** | `PURE_DARK` | 极暗黑色 | 金/白高对比 | 极简、高对比 |
| **水晶梦** | `CRYSTAL_DREAM` | 深蓝紫 | 水晶闪烁 + 蓝辉光 | 空灵魔法感 |
| **丰富星云** | `NEBULA_RICH` | 多层紫色星云 + 尘埃 | 紫白拖尾 | 浓郁厚重 |
| **粉蓝双色** | `PINK_BLUE_DUAL` | 灰黑底 | 粉+蓝双色 | 锐利对比、未来感 |

选择预设的方式取决于集成方式：

```java
// 接口方式
public class MyItem extends Item implements ICosmicLayer {
    @Override public CosmicStyle cosmicStyle() { return CosmicStyle.RAINBOW_FLOW; }
}

// 注册表方式
CosmicLayerRegistry.styledConfig(mask("item/my_mask"), CosmicStyle.CRYSTAL_DREAM);
CosmicLayerRegistry.config(mask("item/my_mask"), 0.8f, CosmicStyle.PURE_DARK, true);
CosmicLayerRegistry.fullConfig(mask("item/my_mask"), 1.0f, true, true, true, CosmicStyle.NEBULA_RICH);
```

---

## 方式一：JSON 模型加载器（传统方式）

物品模型 JSON 使用 `"splendiding:cosmic"` 加载器：

```json
// assets/splendiding/models/item/my_sword.json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "splendiding:item/my_sword"
  },
  "loader": "splendiding:cosmic",
  "cosmic": {
    "mask": "splendiding:item/my_sword_mask"
  }
}
```

**优点**：最灵活，完全隔离，不需要修改 Java 代码。

**缺点**：每个物品需要一个 JSON 文件和一个遮罩纹理。遮罩纹理需要手动制作（通常是物品纹理的 Alpha→Red 通道转换）。

---

## 方式二：ICosmicLayer 接口

让 Item 类直接实现接口即可，无需修改模型 JSON：

```java
public class MySword extends SwordItem implements ICosmicLayer {

    public MySword() {
        super(Tiers.NETHERITE, 3, -2.4f, new Properties());
    }

    // 【必须】遮罩纹理位置
    @Override
    public ResourceLocation cosmicMask() {
        return ResourceLocation.fromNamespaceAndPath("splendiding", "item/my_sword_mask");
    }

    // 【可选】星空不透明度 (0.0~1.0)，默认 1.0
    @Override
    public float cosmicOpacity() {
        return 0.8f;
    }

    // 【可选】控制哪些渲染上下文显示星空
    @Override
    public boolean cosmicShowInGui() { return true; }       // GUI/创造栏
    @Override
    public boolean cosmicShowWhenHeld() { return true; }    // 手持
    @Override
    public boolean cosmicShowInWorld() { return true; }     // 地面/物品框

    // 【可选】完整控制：根据 ItemDisplayContext 动态决定
    @Override
    public boolean cosmicShouldRender(ItemDisplayContext ctx) {
        // 只在第三人称显示星空
        return ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
            || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
```

### 接口方法一览

| 方法 | 返回值 | 默认值 | 说明 |
|------|--------|--------|------|
| `cosmicMask()` | `ResourceLocation` | **必须实现** | 遮罩纹理位置 |
| `cosmicOpacity()` | `float` | `1.0f` | 星空不透明度 |
| `cosmicShowInGui()` | `boolean` | `true` | GUI 中显示 |
| `cosmicShowWhenHeld()` | `boolean` | `true` | 手持时显示 |
| `cosmicShowInWorld()` | `boolean` | `true` | 地面/物品框显示 |
| `cosmicShouldRender(ctx)` | `boolean` | 委托上述三个方法 | 统一入口 |

接口优先级**最高**，即使同一物品同时在 Registry 注册了，接口的实现也会优先生效。

---

## 方式三：CosmicLayerRegistry 手动注册

适合原版物品、三方模组物品、或任何无法修改 Item 源码的情况：

```java
// 在 FMLClientSetupEvent 或 mod 初始化中调用
@SubscribeEvent
public static void onClientSetup(FMLClientSetupEvent event) {
    // 快速注册：只指定遮罩
    CosmicLayerRegistry.enableWithMask(
            Items.DIAMOND_SWORD,
            ResourceLocation.fromNamespaceAndPath("splendiding", "item/diamond_sword_mask")
    );

    // 完整配置
    CosmicLayerRegistry.registerForItem(Items.NETHERITE_SWORD,
            CosmicLayerRegistry.fullConfig(
                    ResourceLocation.fromNamespaceAndPath("splendiding", "item/netherite_mask"),
                    0.8f,   // opacity
                    true,    // showInGui
                    true,    // showWhenHeld
                    true     // showInWorld
            ));

    // 批量注册（同一配置）
    CosmicLayerRegistry.enableWithConfig(
            CosmicLayerRegistry.simpleConfig(
                    ResourceLocation.fromNamespaceAndPath("splendiding", "item/generic_mask")
            ),
            Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.STONE_SWORD
    );

    // 按 Predicate 匹配（给所有工具类物品注册）
    CosmicLayerRegistry.register(
            stack -> stack.getItem() instanceof TieredItem,
            CosmicLayerRegistry.simpleConfig(
                    ResourceLocation.fromNamespaceAndPath("splendiding", "item/tool_mask")
            )
    );
}
```

### Registry 静态方法

| 方法 | 说明 |
|------|------|
| `register(predicate, config)` | 按 Predicate\<ItemStack\> 注册 |
| `registerForItem(item, config)` | 按具体 Item 注册 |
| `enableWithMask(item, mask)` | 快捷注册：只需遮罩 |
| `enableWithConfig(config, items...)` | 批量注册多个物品 |
| `resolve(stack)` | 查询某 ItemStack 的配置（含接口检查） |
| `hasConfig(stack)` | 是否有配置 |
| `simpleConfig(mask)` | 工厂：创建默认配置 |
| `config(mask, opacity, showWhenHeld)` | 工厂：自定义不透明度 |
| `fullConfig(mask, opacity, gui, held, world)` | 工厂：完整自定义 |

### 优先级

`ICosmicLayer` 接口 > Registry 注册。即如果物品同时实现了接口又在 Registry 注册了，接口的实现生效。

---

## 制作遮罩纹理

遮罩纹理控制星空在物品表面哪些区域可见：

```
原物品纹理              →    遮罩纹理（红色通道）
┌──────────────┐             ┌──────────────┐
│              │             │              │
│   ████       │             │ ████          │  ← 白色 = 星星可见
│  ██████      │             │██████         │
│   ████       │             │ ████          │
│              │             │              │  ← 黑色 = 隐藏
└──────────────┘             └──────────────┘
```

简单做法：将物品的原始纹理去色（灰度化），黑色背景保留黑色，物品区域变白。实际上 Live 模组的 void_sword_mask.png 就是 void_sword.png 的 Alpha→Red 通道转换版本。

---

## 光影兼容

三种集成方式**都完全支持 Oculus/Iris 光影**。原理：

1. 渲染物品基础纹理（写入深度缓冲）
2. 检测光影是否激活
3. 光影激活时 → 延迟到 `GameRenderer.renderLevel()` 结束后回放
4. 回放使用 `MAIN_TARGET` 输出，绕过光影的 GBuffer 管线
5. 光影未激活时 → 即时渲染（EQUAL 深度测试，精确贴合物品轮廓）

无需做任何额外配置。

---

## 与 outline/glint 的关系

可以同时启用。一个物品可以：

- 实现 `ICustomOutline`（获得边框+闪光）
- 实现 `ICosmicLayer`（获得星空表面）
- 两者互不干扰

实际上 `VoidSword` 没有任何接口也会同时获得星空渲染（通过 JSON 模型加载器），如果再实现 `ICustomOutline`，边框渲染也会自动生效。

```java
// 同时拥有星空 + 边框 + 闪光的物品
public class FullEffectSword extends SwordItem
        implements ICosmicLayer, ICustomOutline {

    // ── ICosmicLayer ──
    @Override public ResourceLocation cosmicMask() {
        return ResourceLocation.fromNamespaceAndPath("splendiding", "item/full_sword_mask");
    }

    // ── ICustomOutline ──
    @Override public int outlineColor() { return 0xFF00FFFF; }
    @Override public GlintSettings glintSettings() {
        return GlintSettings.builder()
                .colorMode(ColorMode.RAINBOW)
                .speed(0.8f).intensity(0.9f)
                .build();
    }
}
```

---

## 示例：给原版钻石剑添加星空效果

```java
// ClientModEventHandler.onClientSetup() 中
CosmicLayerRegistry.enableWithMask(
    Items.DIAMOND_SWORD,
    ResourceLocation.fromNamespaceAndPath("splendiding", "item/diamond_sword_mask")
);
```

然后在 `assets/splendiding/textures/item/` 下放置 `diamond_sword_mask.png`。

---

## 示例：自定义物品实现接口

```java
public class NebulaAxe extends AxeItem implements ICosmicLayer {

    public NebulaAxe() {
        super(Tiers.NETHERITE, 5, -3.0f, new Item.Properties().fireResistant());
    }

    @Override
    public ResourceLocation cosmicMask() {
        return ResourceLocation.fromNamespaceAndPath("splendiding", "item/nebula_axe_mask");
    }

    @Override
    public float cosmicOpacity() {
        return 0.75f; // 稍微降低不透明度
    }
}
```
