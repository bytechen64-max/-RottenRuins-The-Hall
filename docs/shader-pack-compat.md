# 自定义 Shader 光影兼容架构

## 问题

Oculus（Iris 的 Forge 移植）使用 **deferred rendering**（延迟渲染管线）。光影包激活时，整个场景先渲染到多个 GBuffer 中间目标（albedo、normal、depth、specular 等），然后光影的 composite pass 从 GBuffer 重建最终画面。

如果你在 `ItemRenderer.render()` 中直接调用自定义 shader 画 quad，这些 quad 会被光影管线**捕获进 GBuffer**，然后被当作普通几何体重新着色——你的 shader 效果就丢了。

**解决方案**：不在物品渲染时立即绘制自定义层。改为**快照渲染状态 → 入队 → 在 `GameRenderer.renderLevel()` 结束后回放**，回放时绑定 `mainRenderTarget` 直写主 framebuffer，绕过 GBuffer。

---

## 架构全景

```
物品渲染管线（Mixin 注入顺序）
═══════════════════════════════════════════════════════════════

  ItemRenderer.render()
  │
  ├─ [HEAD] MixinItemRendererTwitch        → PoseStack 鬼畜变换
  │
  ├─ 原版渲染基础贴图（深度写入 GPU）
  │
  ├─ [BEFORE popPose] MixinItemRendererCosmic
  │   ├─ cosmic 星空层    (custom shader, EQUAL depth)
  │   └─ corruption 崩坏层 (custom shader, EQUAL depth)
  │
  ├─ [BEFORE popPose] ItemRendererMixin
  │   └─ outline + glint (ADDITIVE blend)
  │
  └─ [RETURN] ItemRendererMixin  → restore PoseStack


光影兼容流程（Oculus/Iris 激活时）
═══════════════════════════════════════════════════════════════

  MixinItemRendererCosmic
  │
  ├─ 检测: CosmicItemShaderCompat.shouldDeferItemShaderLayer(ctx)
  │        └─ 反射 IrisApi.getInstance().isShaderPackInUse()
  │
  ├─ [光影激活 + 非GUI] → 延迟路径
  │   │
  │   ├─ BakedModelCosmic 路径: 直接入队
  │   ├─ ICosmicLayer 路径: 创建临时 BakedModelCosmic wrapper 后入队
  │   │
  │   └─ CosmicItemLateRenderQueue.enqueue()
  │       快照: PoseStack矩阵 + ModelView + Projection + ItemStack副本 + ...
  │
  └─ [光影未激活 或 GUI] → 即时路径
      └─ 直接画 cosmic/corruption quad 到当前 buffer


  延迟回放（两个注入点，在 GameRenderer.renderLevel() 内）
  ════════════════════════════════════════════════════════════

  renderLevel() {
      renderWorld()           → 主场景渲染（光影 GBuffer 管线）
      renderEntities()        → 实体渲染
      renderItems()           → 第三人称/地面/物品框物品
          ↓ 此时 queue 中有非手持条目

    ① [FIELD: renderHand:Z] CosmicAfterLevelMixin
       → CosmicItemLateRenderQueue.renderNonFirstPerson()
         回放非手持条目:
           LEQUAL depth + polygonOffset + MAIN_TARGET + COLOR_WRITE
           → cosmic 星空 + corruption 崩坏

      renderHand()            → 第一人称手
          ↓ 此时 queue 中有手持条目

    ② [TAIL] CosmicAfterLevelMixin
       → CosmicItemLateRenderQueue.renderAll()
         回放手持条目:
           NO_DEPTH_TEST + MAIN_TARGET + COLOR_WRITE
           → cosmic 星空 + corruption 崩坏
  }
```

---

## 核心组件

### 1. 光影检测 — `CosmicItemShaderCompat`

```java
// 文件: client/cosmic/compat/CosmicItemShaderCompat.java
// 反射调用 Iris API，零编译期依赖

public static boolean isOculusShaderPackActive() {
    // 返回 IrisApi.getInstance().isShaderPackInUse()
}

public static boolean shouldDeferItemShaderLayer(ItemDisplayContext ctx) {
    // GUI 上下文不受光影影响 → 永不延迟
    // 非 GUI + 光影激活 → 需要延迟
    return isOculusShaderPackActive() && ctx != ItemDisplayContext.GUI;
}
```

### 2. 延迟队列 — `CosmicItemLateRenderQueue`

```java
// 文件: client/cosmic/compat/CosmicItemLateRenderQueue.java

// 快照数据结构
record Entry(
    BakedModelCosmic renderer,  // 渲染器
    ItemStack stack,            // 物品栈（深拷贝）
    ItemDisplayContext context,
    Matrix4f pose,              // PoseStack 的模型矩阵
    Matrix3f normal,            // 法线矩阵
    Matrix4f modelView,         // 当前 GL ModelView
    Matrix4f projection,        // 当前 GL Projection
    int packedLight,
    int packedOverlay,
    BakedModel model
)

// 回放时关键操作：
// 1. 用原版 bufferSource（不是各调用方的），避免被光影包裹
// 2. 逐条目恢复入队时的矩阵状态
// 3. 调用 BakedModelCosmic 的 late render 方法
// 4. 每条目独立 flush endBatch()
```

### 3. 回放注入点 — `CosmicAfterLevelMixin`

```java
// 文件: mixin/CosmicAfterLevelMixin.java
// 注入目标: GameRenderer.renderLevel()

// 阶段 1 — renderHand 字段读取前（第三人称/地面物品已画完）
@Inject(at = @At(value = "FIELD", target = "renderHand:Z", ordinal = 0))
→ renderNonFirstPerson()

// 阶段 2 — renderLevel 末尾（第一人称手已画完）
@Inject(at = @At("TAIL"))
→ renderAll()
```

> 物品描边**不用这两个时机**。它的遮罩（"这件物品占了哪些像素"）在物品渲染的当帧
> 立刻捕获 —— 只有那一刻场景深度才是对的；而最后那次全屏合成统一放在 `renderLevel` 的
> **TAIL**，只有一个注入点（`OutlineReplayMixin`）。
>
> 曾经把世界物品拆到 `renderHand` 字段那个点去合成，结果**第三人称开光影时描边整个消失**：
> 光影的最终合成晚于那一行，会把写进去的描边覆盖掉。教训是**别用比光影最终合成更早的点
> 去写主 RT**，并且判"往哪儿画"（当前绑定的 FBO 是不是主 RT）比判"装了什么光影"可靠。
> 详见 `docs/item-shader-outline.md` 第三章。

### 4. GL 状态守卫 — `LateOutlineRenderState`

```java
// 文件: client/rend/glint/LateOutlineRenderState.java

prepareMainTargetPass():
    mc.getMainRenderTarget().bindWrite(false)  // → 绑主 RT，绕过 GBuffer
    disableScissor()          // 光影可能残留 scissor
    enableDepthTest()         // LEQUAL 需要读深度
    depthMask(true)           // 允许深度写入
    colorMask(true,true,true,true)
    setShaderColor(1,1,1,1)
    defaultBlendFunc()

finishMainTargetPass():
    同上，清理回放后的 GL 状态
```

### 5. 三种 RenderType 变体（每条自定义 shader 都需要）

每个自定义 shader 层都需要复制以下三元组模式：

| 变体 | 用途 | 深度测试 | 输出 | 写入掩码 | 其他 |
|------|------|----------|------|----------|------|
| `COSMIC` | 即时渲染（无光影） | `EQUAL` | 默认 | 默认 | `affectsCrumbling=true` |
| `COSMIC_AFTER_LEVEL` | 延迟回放（非手持） | `LEQUAL` | `MAIN_TARGET` | `COLOR_WRITE` | `polygonOffset(-1,-32)` |
| `COSMIC_HAND_AFTER_LEVEL` | 延迟回放（手持） | `NO_DEPTH_TEST` | `MAIN_TARGET` | `COLOR_WRITE` | — |

**关键设计决策为什么这样做：**

- **即时用 EQUAL**：星空/崩坏层只画在基础物品已写入深度的像素上，精确贴合轮廓
- **延迟非手持用 LEQUAL**：回放时基础物品深度已在主深度缓冲中，用 LEQUAL（≤）替代 EQUAL（=）才能通过
- **延迟手持用 NO_DEPTH_TEST**：第一人称手在很多光影下不写入主深度缓冲，必须跳过深度测试
- **polygonOffset**：避免与原始物品表面 z-fighting
- **MAIN_TARGET**：强制写到主 framebuffer，不进入光影的 GBuffer
- **COLOR_WRITE**：不污染深度缓冲，防止影响后续光影 composite pass

### 6. Embeddium 兼容

Embeddium 的 `animateOnlyVisibleTextures` 优化只更新被标准渲染路径引用的 sprite 动画。Cosmic sprite 通过 shader uniform 采样，不走 `SpriteTexturedVertexConsumer`，追踪系统看不到 → 帧动画不更新。

```java
// 文件: client/cosmic/render/CosmicShaders.java
// 每帧反射调用，标记 12 个 cosmic sprite 为活跃
public static void markCosmicSpritesActive() {
    // me.jellysquid.mods.sodium.client.render.texture.SpriteUtil
    //     .markSpriteActive(sprite)
}
```

**注意**：Embeddium 的 `postTick` 每 tick 末尾重置 active 标志，所以必须**每帧**调用，不能只调用一次。

---

## 添加新的自定义 Shader 层（完整清单）

假设要添加一个新的 `ElectroShader`。

### Step 1: 创建 shader 文件

```
assets/hall/shaders/core/
├── electro.json    ← shader 程序配置
├── electro.vsh     ← 顶点着色器
└── electro.fsh     ← 片元着色器
```

`electro.json` 必须声明自定义 uniform：
```json
{
  "blend": { "func": "add", "srcrgb": "srcalpha", "dstrgb": "1-srcalpha" },
  "vertex": "hall:electro",
  "fragment": "hall:electro",
  "attributes": ["Position","Color","UV0","UV2","Normal"],
  "samplers": [{"name":"Sampler0"},{"name":"Sampler2"}],
  "uniforms": [
    ...原版必需 uniform...,
    {"name": "myCustomUniform", "type": "float", "count": 1, "values": [0.0]}
  ]
}
```

### Step 2: 在 `CosmicShaders` 中注册 shader

```java
public static ShaderInstance electroShader;
public static Uniform electroCustomUniform;

// 在 onRegisterShaders() 中添加
event.registerShader(
    new ShaderInstance(provider,
        ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "electro"),
        DefaultVertexFormat.BLOCK),
    shader -> {
        electroShader = shader;
        electroCustomUniform = shader.getUniform("myCustomUniform");
    }
);
```

### Step 3: 在 `CosmicRenderType` 中添加三种 RenderType

```java
// 即时
public static final RenderType ELECTRO = create(..., EQUAL_DEPTH_TEST, (default), ...);
// 延迟非手持
public static final RenderType ELECTRO_AFTER_LEVEL = create(...,
    LEQUAL_DEPTH_TEST, polygonOffset, MAIN_TARGET, COLOR_WRITE, false);
// 延迟手持
public static final RenderType ELECTRO_HAND_AFTER_LEVEL = create(...,
    NO_DEPTH_TEST, MAIN_TARGET, COLOR_WRITE, false);
```

### Step 4: 在 `BakedModelCosmic` 中添加渲染方法

```java
public void renderElectroLayer(ItemStack stack, ItemDisplayContext ctx,
                                PoseStack ps, MultiBufferSource buf,
                                int light, int overlay, BakedModel model,
                                boolean lateRender) {
    if (CosmicShaders.electroShader == null) return;
    // 设置 uniform
    // 选择 RenderType: lateRender ? lateElectroType(ctx) : ELECTRO
    // 画 quad
    // 非延迟路径 flush endBatch(rt)
}
```

### Step 5: 在 `MixinItemRendererCosmic` 中添加渲染调用

即时路径和延迟路径**都要加**。延迟路径复用现有的 `BakedModelCosmic` wrapper 入队机制（在 `renderShaderLayer` 后追加调用即可）。

### Step 6: 在 `CosmicItemLateRenderQueue` 中 flush 新的 RenderType

```java
buffers.endBatch(CosmicRenderType.ELECTRO_AFTER_LEVEL);
buffers.endBatch(CosmicRenderType.ELECTRO_HAND_AFTER_LEVEL);
```

### Step 7: 如涉及纹理，在 `blocks.json` 中注册 atlas source

```json
{
  "sources": [
    { "type": "directory", "source": "shader", "prefix": "shader/" }
  ]
}
```

---

## 文件索引

| 文件 | 职责 |
|------|------|
| `client/cosmic/compat/CosmicItemShaderCompat.java` | 光影检测（反射 IrisApi） |
| `client/cosmic/compat/CosmicItemLateRenderQueue.java` | 延迟队列：快照 + 回放 |
| `mixin/CosmicAfterLevelMixin.java` | 回放注入点（GameRenderer） |
| `client/rend/glint/LateOutlineRenderState.java` | GL 状态守卫（绑主 RT + 重置） |
| `client/cosmic/render/CosmicShaders.java` | Shader 注册 + uniform 引用 + Embeddium 兼容 |
| `client/cosmic/render/CosmicRenderType.java` | 9 种 RenderType（3 层 × 3 变体） |
| `client/cosmic/render/CosmicRenderUtils.java` | Quad 烘焙工具 |
| `client/cosmic/BakedModelCosmic.java` | 星空/崩坏层渲染入口 |
| `client/cosmic/CosmicRenderHelper.java` | 独立渲染辅助（用于 ICosmicLayer 路径） |
| `client/cosmic/CosmicLayerRegistry.java` | ICosmicLayer 手动注册表 |
| `client/cosmic/CosmicClient.java` | 客户端初始化：注册 loader + shader + 事件 |
| `client/cosmic/GeometryLoaderCosmic.java` | JSON 模型 loader（`"hall:cosmic"`） |
| `client/cosmic/BakedModelRendererBase.java` | BakedModel 委托包装基类 |
| `mixin/MixinItemRendererCosmic.java` | 物品渲染注入点（cosmic + corruption） |
| `mixin/MixinItemRendererTwitch.java` | 鬼畜抽动 PoseStack 变换 |
| `api/ICosmicLayer.java` | 星空层物品接口 |
| `api/CosmicStyle.java` | 6 种星空预设样式 |
| `client/rend/twitch/ItemTwitchHelper.java` | 抽动/崩坏爆发强度计算 |
| `client/rend/twitch/ITwitchItem.java` | 抽动+崩坏物品接口 |
| `resources/assets/minecraft/atlases/blocks.json` | 纹理图集注册（cosmic sprite） |
| `resources/assets/hall/shaders/core/cosmic.*` | 星空 shader |
| `resources/assets/hall/shaders/core/corruption.*` | 崩坏 shader |

---

## 注意事项

1. **延迟路径必须用原版 bufferSource**（`mc.renderBuffers().bufferSource()`），不能用各调用方传入的 bufferSource（可能已被光影包裹）
2. **每条目独立 flush** — 不同物品可能有不同的 shader uniform 值，不能合并批次
3. **GUI 上下文永不延迟** — GUI 不走光影管线，直接渲染即可
4. **MixIn 顺序由 priority 控制** — `CosmicAfterLevelMixin` 用 `priority=500`
5. **PoseStack 矩阵必须在入队时快照** — 回放时 PoseStack 状态已经变了
6. **Embeddium 的 `markSpriteActive` 必须每帧调** — `postTick` 会重置
7. **shader 检测用反射** — 避免对 Oculus/Iris 的编译期硬依赖

---

## 世界空间特效的延迟回放（冲击波 / 黑洞 / 斩击扭曲 / 六芒星光环）

物品层之外还有一类"画在世界里、并且要看到完整场景"的自定义 shader 特效。它们不走
`ItemRenderer`，而是在 `RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS`
（实体全部画完、深度就位）里工作。理由相同：光影激活时那一刻主帧缓冲里还只是 GBuffer
原始数据，所以这些处理器检测到光影包就**只入队不画**，交给 `CosmicAfterLevelMixin`
的 `renderLevel()` TAIL 回放。

| 队列 | 快照内容 | 场景拷贝 | 回放入口 |
|------|----------|----------|----------|
| `ShockwaveLateRenderQueue` | pose + normal + 半径/生命/环参数 | ✅ | TAIL |
| `BlackHoleLateRenderQueue` | pose + modelView + 半径/弯曲 | ✅ | TAIL |
| `ApostleSlashWarpQueue` | pose + normal + 斩击参数 | ✅（与冲击波共用同一张） | TAIL |
| `CollapsarHaloLateRenderQueue` | pose + 四个角点 + uniform 值 + **投影矩阵** | ❌（纯程序化加法发光，无纹理） | TAIL |

共同规则：

1. **阴影 pass 一律不画** —— `HeldItemOutlineCompat.isOculusShadowPass()` 时直接 `return`，
   否则阴影贴图里会多出一整块特效（六芒星光环尤其明显）。
2. 回放前 `LateOutlineRenderState.prepareMainTargetPass()`（绑主 RT、关 scissor、重置混合
   与颜色掩码），回放后 `finishMainTargetPass()`。
3. **顶点在相机相对空间烘焙的特效，必须连投影矩阵一起快照**并在回放时重新设置：第一人称
   手部渲染会临时切换到手部 FOV 的投影，而回放时机在它之后。
4. 场景拷贝的尺寸取 `mainRenderTarget` 而非窗口 —— Oculus 的 resolution scale 会让主 RT
   比窗口小，用窗口尺寸会 blit 越界、拷贝静默失败（表现为"扭曲不生效/卡在旧图"）。
5. **实体通道里的自定义 RenderType 不做延迟**：它们会被光影当普通几何收进 GBuffer、被
   光影的光照重新着色（使徒剑气本体就是这样）。要完全不受影响只能改成世界空间特效，
   代价是无法再用实体模型/动画。
