# Mask 效果层注入系统

可叠加在**宇宙星空层之上**的独立 mask 效果层。每层 = 一张遮罩贴图 + 一个效果着色器
+ 一组参数；第一版内置一个效果：**泛光（glow）**。

> 星空层本身见 **[cosmic-layer.md](cosmic-layer.md)**；描边（剪影遮罩 + 环形膨胀）见
> **[item-shader-outline.md](item-shader-outline.md)**。三者互不冲突，可以同时挂在一件物品上。

---

## 1. 它解决什么问题

cosmic 星空层是「一件物品一个 loader 一个效果」写死的：模型 JSON 里声明
`"loader": "hall:cosmic"`，渲染管线里认 `BakedModelCosmic`，RenderType 是三个常量。
想再加第二个效果（泛光、边缘光、能量脉冲……），照那个结构就得把整套东西再抄一遍。

本系统把那套结构抽成**层**：

| | cosmic 星空 | mask 效果层 |
|---|---|---|
| 效果数量 | 1 个写死 | 任意多个，可注册 |
| 一个物品能有几层 | 1 | 任意多层，按 `order` 排序 |
| RenderType | 3 个常量 | 由着色器自动派生三种变体 |
| 光影延迟回放 | 专用队列 | 通用队列，新效果自动继承 |
| 着色器 | `cosmic.*` | `mask_*.*`，每个效果一套 |

**最终渲染顺序**（固定）：

```
物品本体纹理
  └─ cosmic 星空层        （EQUAL 深度，严格贴在剪影内）
       └─ mask 效果层 ①   ┐
       └─ mask 效果层 ②   ├─ 按 order 升序（LEQUAL + polygon offset，可溢出剪影）
       └─ ...             ┘
            └─ 崩坏层     （故障爆发时的整片覆盖，保持它在最上层的既有观感）
```

星空层用 `EQUAL`、效果层用 `LEQUAL + polygon offset`，这个差别是有意的：
星空**不该**溢出剑身，而泛光的全部意义就是溢出轮廓。详见第 5 节。

---

## 2. 五分钟上手

### 方式一：模型 JSON（推荐）

已经用了 `hall:cosmic` 的物品，在**同一个 JSON** 里追加 `mask_layers` 即可叠加。
下面这个例子就是 `crimson_vow` 的真实配置 —— **剑刃归星空、剑柄发光**：

```json
{
  "parent": "minecraft:item/handheld",
  "textures": { "layer0": "hall:item/crimson_vow" },
  "loader": "hall:cosmic",
  "cosmic": {
    "mask": "hall:item/crimson_vow_mask"
  },
  "mask_layers": [
    {
      "effect": "glow",
      "mask": "hall:item/crimson_vow_mask_glow",
      "color": "#FFFFFFFF",
      "intensity": 1.2,
      "width": 2.5,
      "opacity": 0.9,
      "order": 120
    }
  ]
}
```

#### glow 效果的两张图分工

| 作用 | 来自哪张图 |
|------|------------|
| **光源**（发光是什么颜色、多亮） | 物品**本体贴图** `layer0` |
| **范围**（哪里发光） | 本层的 `mask` —— **白 = 发光，黑 = 不发** |

`crimson_vow` 那三张图刚好把这件事说清楚：

- `crimson_vow.png` —— 本体贴图，光源。剑柄本身是暗红金属，所以泛光就是暗红色，
  **不是**凭空指定的一种发光色；`color` 默认白 = 完全用贴图自己的颜色，填别的颜色
  等于给泛光上色。
- `crimson_vow_mask.png` —— cosmic 的遮罩，盖住**剑刃**（那条斜向的刃）。
- `crimson_vow_mask_glow.png` —— 本层的遮罩，盖住**剑柄**（握把 + 护手 + 柄头）。
  两张遮罩的白色区域实测**零重叠**，正好互补。

所以「让没被星空遮罩盖住的部分发光」不需要任何特判：**再画一张标出那部分的遮罩**就行。

只要效果层、**不要星空**的物品，换一个 loader：

```json
{
  "parent": "minecraft:item/handheld",
  "textures": { "layer0": "hall:item/my_sword" },
  "loader": "hall:mask_layers",
  "mask_layers": [
    { "effect": "glow", "mask": "hall:item/my_sword_mask", "color": "#FF66E0FF" }
  ]
}
```

> ⚠️ 一个模型只能有一个 loader。所以「星空 + 附加层」必须走 `hall:cosmic` 那条路
> 并在它里面加 `mask_layers`，不能两个 loader 叠。

### 方式二：物品接口（自描述，不需要改 JSON）

```java
public class MySword extends SwordItem implements MaskLayerProvider {

    private static final ResourceLocation MASK =
            ResourceLocation.fromNamespaceAndPath("hall", "item/my_sword_mask");

    @Override
    public List<MaskLayerSpec> maskLayers(ItemStack stack) {
        return List.of(
                MaskLayerSpec.glow(MASK)
                        .color(0xFF3CF0FF)
                        .width(2.5f)
                        .order(120.0f)
                        .build());
    }
}
```

### 方式三：注册表（原版物品 / 三方物品）

```java
// FMLClientSetupEvent 里
MaskLayerRegistry.registerForItem(Items.DIAMOND_SWORD,
        MaskLayerSpec.glow(CosmicLayerRegistry.mask("item/diamond_sword_mask"))
                .color(0xFFFFD700).build());

// 按谓词批量（例如所有工具）
MaskLayerRegistry.register(
        stack -> stack.getItem() instanceof TieredItem,
        MaskLayerSpec.glow(CosmicLayerRegistry.mask("item/tool_mask")).build());
```

**接口与注册表是合并的**，不是互相顶替 —— 与 cosmic 那条路的「接口优先」不同，
原因见 `MaskLayerRegistry` 的类注释（层可以叠加，先到先得会让后注册的效果永远挂不上）。

---

## 3. 参数

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `effect` | string | `"glow"` | 效果 id，必须是已注册的效果 |
| `mask` | string | **必填** | 遮罩贴图，图集 sprite 名（如 `hall:item/void_sword_mask`） |
| `color` | int / string | 白 | 主色。推荐写 `"#AARRGGBB"`；也接受 `"0xAARRGGBB"` 或十进制数 |
| `intensity` | number | 1.0 | 亮度倍率 |
| `width` | number | 2.0 | 作用宽度。泛光里是**光晕外扩半径，单位是遮罩纹理像素** |
| `speed` | number | 1.0 | 动画速度倍率。**0 = 静止在满亮度**（`glow` 里 `pulse` 恒为 1.0）；> 0 时在 0.80~1.00 之间脉动 |
| `opacity` | number | 1.0 | 整体强度（0–1） |
| `order` | number | 100.0 | 同物品内多层的绘制顺序，**升序 = 越晚画 = 越在上层** |
| `phase` | number | 0.0 | 相位偏移（弧度），用来错开同物品上多个同效果层 |
| `gui` / `held` / `world` | boolean | true | 三个渲染上下文的开关 |

`width` 为什么用「纹理像素」而不是 UV：遮罩尺寸不统一（项目里现有的是
32×32 和 48×48），用像素当单位时同一个数字在不同遮罩上看起来一样宽，而用 UV
就会随贴图尺寸飘。着色器用 `MaskPixels` uniform 做换算，不写死任何尺寸。

---

## 4. 贴图的三条约束

### ① UV 必须落在**本体贴图**上，不是遮罩

`MaskLayerRenderer` 用本体贴图（`layer0`）的 sprite 烘 quad，因为它同时要当光源：
着色器里 `base = texture(Sampler0, texCoord0)`，`texCoord0` 就是这面 quad 的 UV，
**必须指向本体贴图**，否则光源会变成遮罩自己（那只会得到一张黑白图当光）。

> 这里有一个被证伪过的说法值得写下来，免得再想错：
> **quad 的形状与「用哪张 sprite」无关。** 反编译的
> `ItemModelGenerator.m_111638_` 里有一句无条件的
> `list.add(new BlockElement(new Vector3f(0,0,7.5F), new Vector3f(16,16,8.5F), map, null, true))`
> —— 不管 sprite 的 alpha 如何，`bakeItem()` **永远**产出那张完整的 16×16 平面
> （另外再按不透明像素段补一圈带厚度的元素）。
> 所以"拿全不透明的遮罩烘几何会得到矩形"是错的：两种 sprite 烘出来的范围一样大，
> 区别只在 UV 指向谁。当时之所以看到黑色矩形，真正的原因是**着色器 json 缺
> `"blend"`**（见第 5 节末），与几何无关。

物品本体贴图不需要额外准备，普通的"轮廓外透明"物品贴图即可 —— 不过要留意：
**quad 覆盖的是整个 16×16 物品平面**，所以着色器必须自己乘贴图 alpha（见第 5 节的
`coverage`），否则整块平面都会参与运算。


### ② 遮罩与本体贴图必须是**同一张画布**

着色器这样换算位置：

```glsl
vec2 local  = (UV0 - BaseMin) / BaseSize;      // 回到本体贴图的 0..1
vec2 maskUV = MaskMin + local * MaskSize;      // 映射到遮罩的对应位置
```

所以两张图必须同尺寸、同对齐（`crimson_vow.png` 与 `crimson_vow_mask_glow.png` 都是
48×48，剑柄画在同一个位置）。美术侧的规则就一句：**在同一张画布上把要发光的部分涂白。**

遮罩自身的 alpha 不参与任何运算，只有红通道被读。

### ③ 必须在方块图集里


`MaskLayerRenderer` 是用 `blockAtlas.getSprite(mask)` 取遮罩的，取不到就是紫黑 missing
贴图。放在 `assets/<namespace>/textures/item/` 下即可自动进图集 —— 原版
`minecraft:atlases/blocks.json` 里有 `item/` 目录源，而 Forge 1.20.1 的
`SpriteResourceLoader` 用 `resourceManager.getResourceStack(...)` 把**所有资源包的
atlas 定义合并**（而不是覆盖），所以本 mod 自己那份 `blocks.json`（只加了 `shader/`
目录，给 12 张星点动画用）不会把 `item/` 顶掉。

---

## 5. 深度策略：为什么效果层用 LEQUAL

| | cosmic 星空 | mask 效果层 |
|---|---|---|
| 深度测试 | `EQUAL` | `LEQUAL` + polygon offset(-1, -32) |
| 输出 | 当前绑定目标 | 即时=默认；延迟=`MAIN_TARGET` |
| 混合 | `TRANSLUCENT` | `ADDITIVE` |

- `EQUAL` 只画在物品本体已经写过深度的像素上 → 星空**精确贴在剪影内**，一个像素都不溢出。
- 效果层要的是溢出，所以放宽到 `LEQUAL` 并往相机方向推一点：
  - 物品表面：深度相等，`LEQUAL` 成立 → 光晕核心在；
  - 轮廓外的背景：深度更远 → 也通过 → 光晕溢出去了；
  - **挡在物品前面的东西仍然挡住它**（真比物品近的像素不通过），不会穿墙。

### 混合为什么是 ADDITIVE

发光层只有加法混合自洽：遮罩外是纯黑，加法下「黑 = 加 0」，于是不需要 alpha 剪裁
它天然不可见，叠多个层也只是不断加亮。描边系统反过来特意用 `TRANSLUCENT`
（那里的黑要真的压下去），两者取向不同、都对。

两个由此而来的细节，都已处理：

1. **alpha 不参与配色**，所以 `opacity` / 颜色的 alpha 由着色器自己乘进 rgb ——
   否则它们会变成两个「拧了没反应」的旋钮。
2. **不能用 `linear_fog`**。它把颜色朝 `FogColor` 混合，而加法混合会把「远处不可见的黑」
   变成「加上雾色」，症状是远处掉落物亮成一个**雾色方块**。这里用
   `linear_fog_fade` 当乘数（远处衰减到 0）。

### ⚠️ 着色器 json 的 `"blend"` 会盖掉 RenderType 的混合状态（踩过的坑）

**每一个效果着色器的 json 都必须显式写 `blend`，并且与 RenderType 的
TransparencyState 一致。**

原因：`ShaderInstance` 在构造时解析 `"blend"`，并在 `apply()` 里调用
`BlendMode.apply()`（1.20.1 的 `ShaderInstance.java:330`）。而 `apply()` 发生在
RenderType 的 `setupRenderState()` **之后**（同一个 draw 里），所以 **json 赢**。

而 `"blend"` 缺失时的默认值是 `new BlendMode()` —— 它的两个布尔位是
`(false, true, ...)`，`apply()` 里 `if (f_85506_) { RenderSystem.disableBlend(); return; }`
直接**把混合关掉**。既不是加法也不是 alpha，而是 **REPLACE**。

后果（真机实测症状）：这一层把自己的输出原样写进帧缓冲。遮罩黑区算出来的
`rgb=0`，于是**变成一整块黑色平面把物品和角色挡住**；同时把物品表面的层
（星空/等离子）整片覆盖掉，看起来像"星空层被吞了"。

`mask_glow.json` 因此写的是与 `ADDITIVE_TRANSPARENCY`（`ONE, ONE`）对齐的值：

```json
"blend": { "func": "add", "srcrgb": "one", "dstrgb": "one" }
```

> 排查这条时最容易走的弯路：看到 RenderType 上写着 `ADDITIVE` 就断定"加法混合不可能压暗"，
> 从而去别处找黑色方块的来源。**混合的实际生效方是 shader json，不是 RenderType。**


---

## 6. 加一个新效果

添加一个效果只需要三个文件，不用碰物品渲染管线：

1. **着色器**：`assets/hall/shaders/core/mask_<id>.{vsh,fsh,json}`，共用 `Sampler0` 采遮罩。
   **json 里必须显式写 `blend`**（缺省 = 关闭混合 = REPLACE，会把黑区写进帧缓冲，
   见第 5 节末）；照抄 `mask_glow.json` 的三行 `add / one / one` 即可。
2. **实现** `MaskEffectPass`：

```java
public final class RimMaskPass implements MaskEffectPass {
    public static final String ID = "rim";

    @Override public String id() { return ID; }
    @Override public ShaderInstance shader() { return MaskLayerShaders.rim(); }

    @Override public RenderType immediateType()      { return MaskLayerRenderType.immediate(ID, shader()); }
    @Override public RenderType afterLevelType()     { return MaskLayerRenderType.afterLevel(ID, shader()); }
    @Override public RenderType handAfterLevelType() { return MaskLayerRenderType.handAfterLevel(ID, shader()); }

    @Override public void apply(MaskLayerSpec layer, TextureAtlasSprite mask, float time) {
        ShaderInstance s = shader();
        if (s == null) return;
        MaskUniforms.setSlice(s, mask);                       // MaskMin / MaskSize / MaskPixels
        MaskUniforms.set4(s, "RimColor", layer.red(), layer.green(), layer.blue(), layer.alpha());
        MaskUniforms.set1(s, "RimTime", time);
    }
}
```

3. `MaskEffectRegistry.register(new RimMaskPass())`，物品侧就能写 `"effect": "rim"`。

**新效果自动获得**：三种 RenderType 变体、光影延迟回放、上下文开关、层序、遮罩 UV 换算。
`MaskLayerRenderType` 按「效果 id + 着色器实例」缓存，所以 F3+T 重载资源后会自动换成
新实例（只按 id 缓存的话重载后会一直拿着旧着色器，症状是「改了 fsh 却毫无变化」）。

### 邻域采样必须换算到遮罩局部空间

quad 的 `UV0` 是**图集坐标**，不是 sprite 内的 0..1。要采样邻域（泛光、描边这类）得先：

```glsl
vec2 local = (texCoord0 - MaskMin) / MaskSize;       // 图集 UV → 遮罩局部 UV
float v = texture(Sampler0, MaskMin + (local + offset) * MaskSize).r;
```

不换算的话偏移会跨 sprite 采到图集里别人家的贴图 —— 症状是「泛光上出现了奇怪的图案」，
极难反查。`MaskUniforms.setSlice()` 已经把这三个 uniform 备好了。

---

## 7. 光影包（Oculus / Iris）兼容

与 cosmic 完全相同的策略，且**不需要新 mixin**：效果层复用同一个
`CosmicAfterLevelMixin` 的两个相位。

```
物品渲染当帧
  └─ 检测到光影包在跑 → 不平铺，而是把「层 + 矩阵快照」塞进 MaskLayerLateRenderQueue
       ├─ 相位① renderHand 之前：回放世界空间的层（此时主场景深度已写好）
       └─ 相位② renderLevel 末尾：回放剩下的（含一手，一手不测深度）
```

回放时用各效果自己的 `*_after_level` RenderType：`MAIN_TARGET` 输出 + 只写颜色，
直接写主帧缓冲、绕开光影包的 GBuffer 管线。

一手用 `NO_DEPTH_TEST` 是因为很多光影包下第一人称手臂根本不写主深度缓冲，
`LEQUAL` 会把整个手持层判死。代价是手持泛光不再被世界几何遮挡 —— 只在一只手
伸进方块里的一瞬间看得出来。

---

## 8. 每层单独 flush（一个必须保持的约定）

`Uniform.set()` 只是把值记在 `Uniform` 对象上，**真正上传发生在 `endBatch`**（`ShaderInstance.apply()`）。
所以顺序必须是「设 A → 画 A → flush A → 设 B → 画 B → flush B」。
攒到一起再 flush 的话，后一层设的 uniform 会盖掉前一层 —— 症状是
「同一物品上两个泛光层出现了同一个颜色/同一个宽度」。

`MaskLayerRenderer` 保证这一点。新效果不需要操心，但**不要在 `apply()` 里 flush**。

---

## 9. 文件清单

| 文件 | 作用 |
|------|------|
| `api/mask/MaskLayerSpec.java` | 一层的配置（不可变 + Builder），无客户端类型，物品代码可用 |
| `api/mask/MaskLayerProvider.java` | 物品自描述接口 |
| `client/mask/MaskEffectPass.java` | **效果扩展点**（一个效果 = 一套着色器 + 三种 RenderType + uniform） |
| `client/mask/MaskEffectRegistry.java` | 效果注册表（`id → 实现`） |
| `client/mask/MaskLayerRegistry.java` | 物品注册表（`谓词/物品 → 层`），含合并语义 |
| `client/mask/MaskLayerResolver.java` | 合并 / 排序 / 过滤（上下文开关 + 效果可用性） |
| `client/mask/MaskLayerRenderer.java` | 逐层绘制，每层单独 flush |
| `client/mask/MaskLayerJson.java` | 模型 JSON 的 `mask_layers` 解析 |
| `client/mask/IMaskLayerCarrier.java` | BakedModel 携带层的接口（两种 loader 共用） |
| `client/mask/BakedModelMaskLayers.java` | `hall:mask_layers` loader 的包装模型 |
| `client/mask/GeometryLoaderMaskLayers.java` | `hall:mask_layers` loader |
| `client/mask/render/MaskLayerRenderType.java` | 三种变体的工厂（按效果 id + 着色器缓存） |
| `client/mask/render/MaskLayerShaders.java` | `mask_glow` 注册 |
| `client/mask/render/MaskUniforms.java` | uniform 按名设置 + 遮罩几何 uniform |
| `client/mask/render/MaskLayerRenderUtils.java` | quad 缓存（按 sprite 实例，图集重载时清） |
| `client/mask/render/pass/GlowMaskPass.java` | 内置泛光效果 |
| `client/mask/compat/MaskLayerLateRenderQueue.java` | 光影延迟回放队列 |
| `client/mask/MaskLayerClient.java` | 客户端初始化（loader + 着色器 + 效果 + 图集事件） |
| `shaders/core/mask_glow.*` | 泛光着色器 |

改动到的既有文件：`HallMod`（初始化一行）、`MixinItemRendererCosmic`（层解析 + 两处渲染/
入队）、`BakedModelCosmic`（实现 `IMaskLayerCarrier`）、`GeometryLoaderCosmic`（解析
`mask_layers`）。**`hall.mixins.json` 没有新增条目** —— 复用现有注入点是有意的。

---

## 10. 已知限制

1. **光晕不会溢出物品轮廓。** 几何取自本体贴图 → quad 就是武器轮廓，所以膨胀出来的那一圈
   到轮廓边就被切掉。要「真正糊出去」的那种泛光，需要离屏 FBO + 双向模糊 + 合成
   （项目里 `HeldItemOutlineRenderer` 的 `applyBlurChain` / `compositeBloom` 已有实现，
   但那条 held-item 管线目前是停用状态）。`MaskEffectPass` 是留好的替换点：将来把
   某个效果换成多 pass 不需要动物品侧 JSON。
2. **遮罩与本体贴图必须同画布**（第 4 节 ②）—— 位置是用 local UV 映射过去的，不是各自独立对齐。
3. **一层一个 draw call。** 这是 uniform 正确性的要求（见第 8 节），换层数不要换安全性。
4. **`mask_layers` 只在两个 loader 下被解析**（`hall:cosmic` / `hall:mask_layers`）。
   给别的 loader 的模型写 `mask_layers` 不会被读到 —— 第 9 节里的 `MaskLayerJson.parse`
   只在这两处被调用。

---

## 11. 排错

| 症状 | 最可能的原因 |
|------|--------------|
| 层完全没出现，日志有 `引用了未注册的效果 id` | `effect` 拼错，或效果没注册 |
| 层完全没出现，日志有 `着色器未加载` | `mask_glow.*` 没打进包 / GLSL 编译失败（日志里有编译器原文） |
| **物品外出现一整块黑色平面，物品表面的层被整片吞掉** | **shader json 缺 `"blend"` 节点** → 混合被关掉（REPLACE），黑区被原样写进帧缓冲（见第 5 节末，这条实际踩过） |
| 泛光整层是紫黑格子 | 遮罩没进方块图集（第 4 节 ③） |
| 泛光位置整体偏移 | 遮罩与本体贴图不是同一张画布（同尺寸同对齐，第 4 节 ②） |
| 远处掉落物的泛光变成一个雾色方块 | 用错了雾函数（应 `linear_fog_fade`，不是 `linear_fog`） |
| 两个泛光层同色同宽 | 没有每层单独 flush（第 8 节） |
| 改了 `fsh` 没变化 | 着色器缓存对不上实例（第 6 节末）或没重载资源 |
| 日志里没有 `[MaskLayer] 层生效` 行 | 层根本没被解析到（effect id 拼错 / 模型 JSON 没被这个 loader 解析），或该物品从未被渲染 |

> 排查顺序建议：**先看日志里有没有 `[MaskLayer] 层生效：物品=… mask=…`**，
> 它能一次性区分「层没解析到」和「层在画但画错了」这两类完全不同的故障 ——
> 从画面上看它们是一样的。这条日志按「物品+效果+遮罩」各记一次。
