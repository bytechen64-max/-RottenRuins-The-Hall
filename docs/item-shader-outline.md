# 物品描边：屏幕空间「剪影遮罩 + 环形膨胀」

> 2025 重构。旧实现（模型放大壳）已整体移除，见文末"被删掉的东西"。

## 一、为什么换掉旧方案

旧实现把物品模型整体放大 6% 再重画一遍当描边。四个根因：

| 你看到的现象 | 真正的根因 | 证据 |
|---|---|---|
| 物品被整片染色，不像"描边" | 放大壳把整件物品（含内部）都重画了一遍，只有最外那 6% 是真正的边 | 旧 `OutlineGlintRenderer.renderWorldOutlinePass` |
| 穿墙、挡不住、盖住手臂 | 那段代码手动开的深度测试和 `ADDITIVE` 混合，在批次 flush 时被 RenderType 自己的 `setupRenderState()` 覆盖；该 RenderType 声明的是 `NO_DEPTH_TEST` + `TRANSLUCENT` | `RenderType.end()` 里才调 `setupRenderState()`；`BufferSource.getBuffer()` 不设任何 GL 状态 |
| 和星空叠一起发灰/过曝 | 两层抢同一批像素：cosmic 用 `EQUAL` 深度贴表面，outline 无深度测试整片覆盖 | `CosmicRenderType.COSMIC` |
| 边宽不均、远处发虚 | 模型空间固定 0.06 偏移 → 屏幕边宽随距离变化 | 同上 |

还有两个隐藏问题：同一 shader key 共用 RenderType 与 `ShaderInstance`，uniform 是全局的，
同批次里多个描边物品会互相覆盖颜色；cosmic 与 outline 的 mixin 注入点是同一个 `popPose`，
先后顺序由 mixin 应用顺序决定，没有任何显式约定。

**关键结论：旧方案不是"参数没调好"，而是架构上做不到只画边。**

## 二、新架构

两个 pass，产物完全不重叠：

```
① 剪影遮罩 (outline_mask)
   把"这件物品占了哪些像素 + 该用什么颜色描边"写进一张离屏贴图。
   带 LEQUAL 深度测试，并且把【当前场景的深度附件】挂到遮罩 FBO 上，
   于是被墙/实体挡住的物品自然不进遮罩。

② 环形合成 (outline_ring)
   全屏 pass 算 ring = dilate(mask, r) − mask
```

那个减法就是全部关键：剪影内部 `mask=1`，膨胀后还是 `1`，相减得 `0`。
**描边永远只出现在物品外侧，绝不会盖住物品表面。**
宇宙星空层画在物品表面上，于是两者连一个像素都不重叠 —— 冲突是从结构上消失的，
不是靠调参压下去的。

### 分层所有权契约

```
物品基础模型        写颜色 + 写深度
   ├─ 剪影内部像素 → cosmic / corruption / glint（EQUAL 深度，贴表面）
   └─ 剪影外部像素 → outline 环（只画外圈）
                    ↓
              合成到 MAIN_TARGET
```

新增视觉层时按这条契约放，就不会再互相打架。

### 设计决定

- **颜色烘进遮罩的 rgb**，不当 uniform 传。同一帧里不同物品可以各自有不同描边颜色，
  却只需要一次全屏合成；也避免了"共享 uniform 被同批次后一个物品覆盖"的老问题。
- **等宽靠像素半径**（`outlinePixelWidth`，默认 **2.5**），不是模型缩放系数。
  GUI 图标 / 一手 / 三手 / 掉落物在任何距离下描边一样粗。
- **硬边、无羽化、无外发光、无 bloom**，对齐原版发光实体描边的观感。
  唯一的柔和来自 `smoothstep` 提供的约 1 像素抗锯齿。
- **每个物品只在自己的屏幕包围盒里跑环形采样**（scissor）。
  全屏 48 次采样在 1080p 上是两百万像素 × 48 次取纹理，绝对跑不动。

## 三、合成时机

**原则：物品渲染路径上一个 GL 调用都不发。** `submit()` 只做纯 CPU 的矩阵/样式快照，
所有 framebuffer 切换、全屏四边形、viewport / scissor 操作全部挤在 `renderLevel` 的 TAIL。

| 上下文 | 处理时机 | 理由 |
|---|---|---|
| GUI | 提交后立刻 | GUI 是 `renderLevel` 之后独立的一趟，主 RT / 深度自洽，且此刻不会打扰到别的渲染 |
| 世界（掉落物 / 展示框 / 三手 / 一手） | 一律 `renderLevel` TAIL | 见下 |

注入点 `mixin/OutlineReplayMixin.java`，只有一个 `@At("TAIL")`，`priority = 500`。

### 为什么必须全挤在 TAIL（踩过的坑四）

一开始是"物品渲染时立刻捕获剪影，只有最后那次合成推迟"。结果：
**只要世界上有物品被描边，之后绘制的所有实体都变透明。**

原因是实体通道的批次是**攒着一起 flush** 的：`ItemRenderer.render` 跑的时候，
玩家身体 / 物品展示框的框体 / 附近其他实体的几何还排在 `BufferBuilder` 里没提交。
我们在那一刻切 framebuffer、改 viewport / scissor、画全屏四边形，受影响的就是
**这些稍后才 flush 的批次** —— 所以症状是"整个世界里的实体"，而且只怪在"世界中的物品"上。

一手从来不出这个问题，正好是最好的对照：它在 `renderLevel` 的最末尾渲染，
后面除了我们自己的 TAIL 合成之外没有别的东西了。

既然 TAIL 已经证明了是无害的时机，就把**全部**工作搬过去：

```java
submit()   // 纯 CPU：快照 quads 来源 / pose / modelview / projection / 样式 / 包围盒
           // 一个 GL 调用都不发 —— 也就无从打扰实体通道

composite() // renderLevel TAIL：先逐件把剪影画进遮罩，再一次性合成到主 RT
```

快照里必须包含 {pose, modelview, projection} 三件套：TAIL 时的矩阵早就不是物品渲染
当时的了，而遮罩必须落在和画面**完全一致**的像素上。重放时用
`RenderSystem.setProjectionMatrix(snapshot)` + 往 `getModelViewStack()` 里压入
`snapshotModelView`，画完原样还原 —— 项目里 `CosmicItemLateRenderQueue` 早就是这么做的。

### 遮挡：为什么用「挂深度附件 + 硬件深度测试」

一开始是"把场景深度当纹理采样，在片元里和 `gl_FragCoord.z` 比"。在真实环境（Oculus 1.8 + 光影包）里不工作：
GBuffer 的深度可能是 **renderbuffer**（根本不能绑成采样器），格式也不保证，而且深度数值的约定
（reversed-Z / 自定义 far plane）不能假定。

现在的做法：**把"这件物品当时画进去的那个 framebuffer 的深度附件"临时挂到遮罩 FBO 上，
交给硬件深度测试**（`LEQUAL` + 不写深度，这一对状态 `OutlineMaskRenderType` 本来就开着）。

- 纹理 / renderbuffer 都支持；
- 什么格式都行；
- **天然用同一套深度约定** —— 这份深度本来就是用同一个投影写出来的，比较由 GPU 完成，
  不需要猜 reversed-Z 或 far plane。

关键实现点：

| 时机 | 做什么 | 为什么 |
|---|---|---|
| `submit`（物品渲染中） | **纯只读**查一下当前 FBO 的深度附件是谁，连同矩阵/几何一起记进 Entry | 实体通道中间绝对不能碰 GL 状态；`glGetFramebufferAttachmentParameteri` 只是查询 |
| `renderHand` 字段读取处（世界画完、vanilla 清深度之前） | 挂上深度，把**世界 / 三手**物品的剪影画进遮罩，然后摘掉 | 唯一同时满足"深度完整 + 没开始画一手 + 不是实体通道中间"的点 |
| `renderLevel` TAIL | 补画**一手**物品的剪影（不带遮挡），再把所有环合成到主 RT | 一手是在 `renderItemInHand` 里渲染的，**晚于**上面那个点，条目到现在才存在 |

```
levelRenderer.renderLevel(...)     // 世界 + 实体，深度完整
if (this.renderHand) {             // ← 第一批剪影（世界/三手）+ 挂深度
    RenderSystem.clear(GL_DEPTH_BUFFER_BIT);   // ← 深度在这里没了
    renderItemInHand(...);         // ← 一手物品在这里才被渲染、才提交
}                                  // ← TAIL：第二批剪影（一手）+ 合成
```

### 为什么遮罩要分两批捕获

一手物品的渲染时机比捕获点**晚**：它在 `renderItemInHand()` 里，而捕获点选在
`if (this.renderHand)` 那一行（为了赶在 vanilla 清深度之前）。所以那一刻一手的 Entry
根本还没生成 —— 一刀切地把捕获全放在前面，结果就是**一手描边整个消失**。

于是分成两批写进同一张遮罩，最后一次性合成：

- **第一批**（清深度之前）：世界 / 三手，**带**场景深度 → 硬件深度测试剔除被挡住的剪影；
- **第二批**（TAIL）：一手，**不带**深度 —— 一手物品就画在相机眼皮底下，vanilla 甚至专门
  在画手之前清掉深度来保证它不被挡，本来就谈不上遮挡。

遮罩每帧只在**第一批之前**清一次（`maskHasContent` 标志），两批剪影因此互不覆盖；
`PENDING`（待画剪影）/ `READY`（已在遮罩里）两张表保证同一件物品不会被重复捕获。
合成结束后统一清理并复位状态。

尺寸不一致（光影包 resolution scale 与主 RT 不同）时 FBO 会不完整 → 摘掉、退化成不遮挡，
既不会错误剔除剪影也不会崩，日志里会打 `深度附件挂不上`。

**遮挡判定对「摆在世界里的物品」和「三手手里的物品」都开着**（不开的话描边会穿透方块和实体，
这是实测踩过的）。唯独一手和 GUI 不开：一手本来就不被遮挡，GUI 的正交深度跟世界深度不是一回事。

### 为什么只有 TAIL 一个点（踩过的坑）

世界物品原本被拆到 `GameRenderer.renderLevel` 里**首次读 `renderHand` 字段**的位置去合成，
想的是"世界物品先合成、一手压在上面"。结果**第三人称开光影时描边完全看不到**。

1.20.1 的方法体说明了原因：

```
levelRenderer.renderLevel(...)        // 世界画完（光影在这一步渲染 GBuffer）
dispatchRenderStage(AFTER_LEVEL)
if (this.renderHand) {                // ← 旧的合成点在这
    RenderSystem.clear(GL_DEPTH_BUFFER_BIT);
    renderItemInHand(...);            // 一手
}
                                      // ← TAIL：光影的最终合成落在这附近
```

光影包的最终合成晚于 `renderHand` 那一行，所以那个点写进去的描边会被整个覆盖掉；
TAIL 的写入排在它之后，所以一手一直正常。

既然遮罩捕获时遮挡关系已经用场景深度判定完了，**合成本身不依赖深度**，
也就没必要拆成两段 —— 全部放到 TAIL，既正确又简单。

### 顺带修掉的检测依赖

老逻辑用 `ShaderPackDetector.shouldUseShaderPackPipeline()`（反射 Oculus 的
`IrisApi.isShaderPackInUse()`）来决定"现在能不能画"。一旦这个反射检测失灵，
第三人称就会静默地往光影的 GBuffer 里画，然后被覆盖掉 —— 症状就是"一点都看不见"。

现在改成判**"我这一笔会落在哪儿"**：

```java
isRenderingToMainTarget(main)   // 当前绑定的 FBO 的 COLOR_ATTACHMENT0 是不是主 RT 的颜色纹理
```

不依赖反射、不依赖第三方 API 版本，也不会因为检测失灵而静默失效。
`shouldUseShaderPackPipeline()` 作为兜底仍然保留。

### 坑二：别要求调用方给的 buffer 是 `BufferSource`

`submit()` 一开始要拿 `buffer` 刷批次，早先写的是：

```java
// ✗ 错的
if (!(buffer instanceof MultiBufferSource.BufferSource sources)) return;
```

结果**光影下第三人称 / 掉落物 / 物品展示框完全不显示**，而一手一直正常。

原因正是 `docs/shader-pack-compat.md` 的注意事项第 1 条：光影包激活时，
**实体路径**传进来的 `MultiBufferSource` 是被光影包过一层的，不是原版 `BufferSource`。

| 上下文 | 物品渲染的调用方 | 拿到的 buffer | 旧代码结果 |
|---|---|---|---|
| 一手 | `GameRenderer.renderItemInHand` 直接传 `renderBuffers().bufferSource()` | **原版本体** | 捕获成功 → 正常 ✓ |
| 三手 / 掉落物 / 展示框 | `PlayerRenderer` / `ItemEntityRenderer` → 实体通道 | 被光影包过的 wrapper | `instanceof` 为假 → **直接 return** ✗ |

关键认识：**我们其实不需要 flush 调用方那个 buffer。**
遮罩画进的是我们自己的 FBO + 私有的 `RenderBuffers`，
调用方那些还没提交的几何根本不会进入捕获窗口（我们绑自己 FBO 的整个过程中，
没有任何东西会去 flush 它的 Builder）。所以现在只在拿得到原版本体时顺手刷一下：

```java
if (buffer instanceof MultiBufferSource.BufferSource sources) {
    sources.endBatch();   // 能刷就刷，刷不了也照样往下走
}
```

### 坑三：不要把场景深度 attach 成遮罩 FBO 的深度附件

为了做遮挡判定，最初把「当前场景的深度纹理」`glFramebufferTexture2D` 挂到了遮罩 FBO 上。
结果：**任何展示该物品的实体都会变透明** —— 玩家、物品展示框被看穿。

原因在 GL 规范里：同一张纹理被两个 FBO 引用、且其中一个正在被渲染，
**这张纹理在另一个 FBO 眼里的内容就变成未定义**。
（我们只是想做深度测试，根本没写深度，但驱动按 FBO 引用关系看，
这张深度图一样会失效。）于是解码：

1. 我们挂上场景深度 → 往遮罩 FBO 渲染 → 场景深度图对它自己的 FBO 变成未定义；
2. 实体在描边捕获**之后**绘制的部分，拿去和这份坏掉的深度做测试 → 全被剔除；
3. 肉眼看到的就是「实体变透明」。

`ItemFrameRenderer` 尤其明显：`render()` 先提交框体模型（第 68 行）、后渲染物品（第 96 行），
所以框体必然排在捕获之后 —— 正好命中。

**正确做法：深度当采样器读，不挂附件。**

```glsl
// outline_mask.fsh
float sceneDepth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
if (sceneDepth < gl_FragCoord.z - DepthBias) discard;   // 被更近的东西挡住
```

遮罩 FBO 因此只有颜色附件（深度测试恒通过 = 不写深度），
**一个字节的场景深度都不会被碰**，而遮挡判定精度完全一样。
用 `texelFetch` 而不是 `texture()`，避免深度被线性插值出假的过渡带。

`UseDepth` 只在**当前绘制目标就是主 RT** 时打开：
那时深度纹理确定是原版场景深度、和我们的投影同一套约定。
光影包下绑的是它的 GBuffer，深度约定（可能有 reversed-Z / 自定义 near-far）不能假定，
宁可退化成「不遮挡」也不要把剪影整片剔掉。

> 代价：**光影包激活时描边不做遮挡**（可能穿墙）。要恢复的话，
> 需要先确认该光影包深度纹理的约定；这条属于已知限制。

### 诊断日志

这条链路上"静默失败"的环节太多（绑定目标不对、深度读不到、剪影被深度剔干净、
buffer 类型不对……），结果统统是"就是没有描边"。所以每个环节各留了一条一次性日志，
每个 key 只打一次，不刷屏。排查时直接看 `run/logs/latest.log` 里的 `[ItemOutline]` 段落：

| 日志 | 含义 |
|---|---|
| `submit ctx=… buffer=… gui=… occlude=… packActive=… quads=… rect=…` | 捕获有没有发生、走的哪条路、buffer 的真实类型 |
| `场景深度快照：成功 / 失败` | 是否抢到了 vanilla 清深度之前的复制 |
| `剪影遮挡判定开启 / 不做遮挡判定` | 该条物品有没有走深度剔除 |
| `合成 N 个描边条目 → 主 RT` | 合成确实跑到了，以及之前绑的是哪个 FBO |

## 四、API

`ICustomOutline` 新增：

| 方法 | 默认 | 说明 |
|---|---|---|
| `outlinePixelWidth()` | `2.5` | 描边宽度，**屏幕像素** |
| `outlineOpacity()` | `1.0` | 整体不透明度 |
| `outlineAlphaCutoff()` | `0.1` | 剪影判定的纹理 alpha 阈值 |
| `outlineSecondaryColor()` | `0`（不启用） | 副色，仅 `gradient` 模式用 |

`outlineShaderKey()` **保留但语义变了**：它不再对应独立着色器与 RenderType，
只决定颜色怎么算 —— `null`/`"default"` 纯色、`"gradient"` 主色↔副色流动、`"warp_fbm"` 彩虹流动。

已弃用（仍可编译，但不再生效）：

- `outlineWidth()` / `GlintEffectProfile.worldOutlineWidth(float)` —— 模型空间缩放系数，
  改用 `outlinePixelWidth()` / `worldOutlinePixelWidth(float)`
- `configureOutlineShader(ShaderInstance)` —— 仍会被调用，但传的是剪影遮罩着色器，
  旧实现里的 `ColorCount` / `FlowSpeed` 之类 uniform 名已不存在，等于空操作

`GlintEffectProfile` 新增 `worldOutlinePixelWidth(float)` 与 `worldOutlineOpacity(float)`。
`GlintRenderManager` / `HeldItemGlintHelper` 的注册方式不变。

## 五、调节参数

**想整体加粗/变细，改配置文件就行，不用重新编译：**

```jsonc
// config/hall/SplendidingConfig.json
"outlineWidthScale": 1.0    // 1.0 = 各物品自己的宽度（默认 2.5 像素）
                            // 1.5 → 3.75px，2.0 → 5px，0.6 → 1.5px
```

倍率只影响"整体观感"，单个物品仍可用 `outlinePixelWidth()` 单独覆盖。
最终宽度会被夹在 0.5 ~ 24 像素之间。

**想让某个物品单独更粗**：改它的 `outlinePixelWidth()`（`ICustomOutline`）或
`worldOutlinePixelWidth(float)`（`GlintEffectProfile.Builder`）。

想调颜色：纯色用 `outlineColor()`；双色流动再加 `outlineSecondaryColor()`、
`outlineShaderKey()` 返回 `"gradient"`。

## 六、回退行为

- **读不到场景深度**（GUI / 光影包下深度约定未知 / 深度不是纹理）：
  关掉片元里的遮挡判定 → 退化成"不遮挡"，但**不会**动任何 FBO 附件，
  所以不会再出现"实体变透明"那类副作用。
- **着色器没加载**：`ItemOutlinePipeline.submit` 直接 return，物品正常渲染，只是没描边。
- **同屏描边物品过多**：`MAX_PENDING = 256`，超出的丢弃，不会无限增长。

已知限制（暂未处理）：

- **光影包激活时不做遮挡判定**：可能看到描边穿墙。原因见"坑三"——
  光影包深度纹理的约定不能假定。要恢复得先确认该包的深度表示方式。
- **光影包的 resolution scale 与主 RT 尺寸不一致时**，遮罩的屏幕坐标会跟最终画面错位
  （遮罩、包围盒、合成三处都按 `mainRenderTarget` 尺寸算，与项目里 cosmic 那套约定一致——
  Oculus 的 resolution scale 会让主 RT 小于窗口，所以实测尺寸是对得上的）。
  真的遇到错位的话，改 `ensureMaskTarget` 用当前绑定 FBO 的尺寸、并在合成时按比例缩放即可。
- **延迟批次里两件描边物品在屏幕上重叠**时，缩放半径内可能出现一小段缺失的弧
  （合成后的清理是按包围盒并集做的，已经避免了串色，但重叠处的剪影会互相覆盖一下）。

## 七、被删掉的东西

- `client/rend/glint/OutlineRenderQueue.java`（延迟回放队列）
- `client/rend/glint/LateOutlineRenderType.java`（延迟回放 RenderType）
- `OutlineGlintRenderer.renderWorldOutlinePass` 及其手动 GL 状态块
- `SplendidingShaders` 里的 `worldOutlineShader` / `gradientOutlineShader` /
  `warpFbmOutlineShader`、`OUTLINE_SHADERS` / `OUTLINE_RENDER_TYPES` /
  `WORLD_OUTLINE_RENDER_TYPES` / `LATE_*` 四张表、`regSp()` 与 `_sp` 变体、
  `makeNoDepthRenderType` / `makeLequalOutlineRenderType`
- 着色器资产 `rendertype_item_world_outline.*`、`rendertype_item_gradient_outline.*`、
  `rendertype_item_warp_fbm.*` 及各自的 `_sp` 变体

> `LateOutlineRenderState` **保留**：冲击波 / 黑洞 / 斩击扭曲 / 六芒星光环 / cosmic
> 的延迟回放都还在用它。

> `HeldItemOutlineRenderer`（一手 FBO 描边 + bloom 子系统）**保留但仍是死代码** ——
> 它在 `ClientModEventHandler` 里被 `setOutlineMode(VERTEX_SHADER)` 钉死，且自带一套
> `HeldItemRuleManager` 规则/配置系统。它跟新管线互相独立，本次没有动它。
> 如果要删，是一次独立的清理（涉及 60KB 左右的规则与配置代码）。

## 八、着色器文件

| 着色器 | 职责 |
|---|---|
| `outline_mask.vsh` / `.fsh` / `.json` | 剪影遮罩：纹理 alpha 裁剪 + 颜色模式求值，输出 `vec4(color, 1)` |
| `outline_ring.vsh` / `.fsh` / `.json` | 全屏环形合成：16 方向 × 3 半径圆盘采样，`dilate − self` |

## 九、参考实现：黑白流动描边

`VoidSword`（`overworld/registry/items/VoidSword.java`）是接口路径的最小完整例子：

```java
public class VoidSword extends SwordItem implements ICustomOutline {
    @Override public int outlineColor()          { return 0xFF000000; }   // 黑
    @Override public int outlineSecondaryColor() { return 0xFFFFFFFF; }   // 白
    @Override public String outlineShaderKey()   { return "gradient"; }   // = 双色流动
    @Override public float outlinePixelWidth()   { return 2.5f; }         // 屏幕像素

    // ★ 必须用 TRANSLUCENT：加法混合下黑色是"加 0"，黑的那半截会整个消失
    @Override public ICustomOutline.BlendMode outlineBlend() {
        return ICustomOutline.BlendMode.TRANSLUCENT;
    }

    @Override public boolean outlineEnabled(ItemDisplayContext ctx) { return ctx != HEAD; }
}
```

颜色在 `outline_mask` 里逐像素求值：`mix(黑, 白, 0.5 + 0.5·sin(flow))`，
`flow` 由模型空间坐标与时间驱动 → 一圈黑白交替、持续流动的带子。

> **为什么不用 `GlintRenderManager` 注册？** `GlintEffectProfile` 是"描边 + GUI 辉光"打包的
> ——注册一个 profile 会顺带把辉光画到物品表面，把宇宙星空层糊掉。
> `ICustomOutline` 路径下 `glintSettings()` 返回 null（默认），就只描边、不动表面。
> 要批量给一批原版物品加描边又不在意表面辉光时，才用 `GlintRenderManager`。
