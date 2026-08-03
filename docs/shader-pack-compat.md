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
