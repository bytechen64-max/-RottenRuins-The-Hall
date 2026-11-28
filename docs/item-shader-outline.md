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

> `fire_2`（第十二章）的火焰光环仍然遵守这条契约：它只画在剪影**外侧**，只是绘制区
> 比普通描边大得多（按物品尺寸外扩，见第十二章）。

### 设计决定

- **颜色烘进遮罩的 rgb**，不当 uniform 传。同一帧里不同物品可以各自有不同描边颜色，
  却只需要一次全屏合成；也避免了"共享 uniform 被同批次后一个物品覆盖"的老问题。
- **宽度靠像素半径**（`outlinePixelWidth`，默认 **2.5**）+ **表观尺寸比例**，不是模型缩放系数。
  近处（物品在屏幕上够大）保持恒定像素宽，远处环宽恒为「物品表观尺寸 × `outlineWidthMaxRatio`」，
  并夹在 `outlineMinPixelWidth` 之上。见 §五。
- **硬边、无羽化**，对齐原版发光实体描边的观感（后面追加的「光雾」是独立的第二层，
  见第十一章；关掉 `outlineAuraEnabled` 就回到这里的纯硬环）。
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
| `outlinePixelWidth()` | `2.5` | 描边宽度，**屏幕像素**；同时是距离自适应下的**上限** |
| `outlineWidthMode()` | `DISTANCE` | `SCREEN` = 恒定像素宽（旧行为）；`DISTANCE` = 近处同上限、远处按表观尺寸收细 |
| `outlineOpacity()` | `1.0` | 整体不透明度 |
| `outlineAlphaCutoff()` | `0.1` | 剪影判定的纹理 alpha 阈值 |
| `outlineSecondaryColor()` | `0`（不启用） | 副色，仅 `gradient` 模式用 |

`outlineShaderKey()` **保留但语义变了**：它不再对应独立着色器与 RenderType，
只决定颜色怎么算 —— `null`/`"default"` 纯色、`"gradient"` 主色↔副色流动、
`"warp_fbm"` 彩虹流动、`"fire"` 火焰湍流（见第十章）、`"fire_2"` 燃烧描边（见第十二章）。

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

### 距离自适应：为什么不再"任何距离都一样粗"

**问题**：环宽是绝对像素值，物品却按 1/距离 缩小。60 格外的掉落物在 1600p 上只有 8px 高，
而环还是 4.5px —— 环占去物品一半，观感是糊成一圈外扩的厚边。

**做法**（`ItemOutlinePipeline.apparentSizePx` + `adaptPixelWidth`，纯 CPU，GLSL 一行没改）：

```
apparentPx = 物品包围盒对角线 × 该距离上每格多少像素        // 见下"表观尺寸怎么算"
widthPx    = clamp( min(物品宽度, apparentPx × outlineWidthMaxRatio),
                    outlineMinPixelWidth, 物品宽度 )
```

**表观尺寸怎么算（这里踩过一次坑）**：一开始图省事，直接拿合成用的那个投影包围盒
（`projectItemBox`）当尺寸基准 —— **错的**。那个框是给 scissor 用的，相机一贴近，顶点落到近平面
附近，除以 `w` 之后 NDC 会飞到天上、被 `MAX_NDC` 夹成整屏。实测日志里直接出现了
`rect=2560x1600 apparentPx=3456`，而 **3456 恰好等于 1.35×屏宽**，于是比例上限永远不生效，
表现就是"改了也还是任何距离一样粗"。

现在换成纯几何算法，**全程不做透视除法、没有任何可退化的环节**：

1. 模型 AABB 的 8 个角点经 `modelView × pose` 变到**视图空间**（pose 里的缩放，比如掉落物的
   0.5 倍，因此也被算进去），其对角线就是物品在世界里的尺寸（单位：格）；
2. 距离 = 该 AABB 中心的 `-z`；
3. 每格占多少像素 = `m11 × 半屏高 / 距离`（透视投影的标准约定，`m11 = 1/tan(fovY/2)`）；
4. 相乘即得像素尺寸。严格按 1/距离 单调，与相机朝向、与顶点是否贴着近平面都无关；
   而且 AABB 对角线对旋转几乎不变，掉落物自转时环宽不会脉动。

- **近处逐位不变**：`apparentPx ≥ 宽度 / outlineWidthMaxRatio` 时，取 min 后仍是原宽度，
  `clamp(x, …, x)` 原样返回。所以手持物与近处物品的行为与改动前**完全一致**。
- **越过分界点后**，环宽**恒为物品表观尺寸的 `outlineWidthMaxRatio` 倍** —— 也就是透视正确的
  世界宽度，不会随距离继续变得相对更粗；额外的宽度上限只是为了让近处不跟着一起爆炸
  （那正是被删掉的"放大壳"的老问题）。
- **下限 0.75px 而不是 0**：环宽落进亚像素区间后，`outline_ring.fsh` 里那条约 1 像素宽的
  `smoothstep` 抗锯齿带会把整条环变成半透明薄雾 —— 细是细了，但读起来还是糊。
  0.75 是"不会糊过头"与"远处仍看得见"之间的折中。

参考数值（**1600p** / fov 70 / 掉落的平面物品 / 该物品的 `outlinePixelWidth() = 4.5`，
默认比例 `0.04` ⇒ `apparentPx ≈ 708 / 距离`，分界点在 112px ≈ **6 格**）：

| 距离 | 物品表观尺寸 | 描边宽度 |
|---|---|---|
| ≤6 格 | ≥112px | 4.5px（**与改动前一致**） |
| 10 格 | 70.8px | 2.83px |
| 20 格 | 35.4px | 1.42px |
| 30 格 | 23.6px | 0.94px |
| ≥40 格 | ≤17.7px | 0.75px（下限） |

> **比例为什么是 0.04 而不是更大**：这个"分界点"必须落在实际的观察距离以内，否则等于没生效。
> 第一版取 0.15（分界点 30px）时，实测 20 格外一个物品框里的剑表观尺寸约 45px —— 正好在分界点
> **之外**，环宽一次都没被收细，表现就是"改了还是一样"。

配置项（`config/hall/SplendidingConfig.json5`）：

```jsonc
"outlineDistanceScaling": true,   // false = 完全回退到"任何距离都一样粗"的旧行为（同屏 A/B 用）
"outlineWidthMaxRatio": 0.04,     // 越过分界点后环宽恒为表观尺寸的这个比例；调大 = 远处更粗
"outlineMinPixelWidth": 0.75      // 远处收细的下限（像素）
```

> ⚠ **改默认值必须同时提升 `CONFIG_VERSION` 并加迁移**：cloth-config 只在首次运行写入默认值，
> 之后一律以文件为准 —— 光改上面代码里的默认值，对已经有配置文件的机器**完全不生效**。
> 这个坑踩过两次（`verdictCooldownSeconds` 一次、`outlineWidthMaxRatio` 一次），
> 迁移写在 `ConfigHelper.migrateSplendidingConfig` 里。

单个物品想退出自适应：让它实现的 `ICustomOutline.outlineWidthMode()` 返回 `SCREEN`。

> 验证：`run/logs/latest.log` 的 `[ItemOutline]` 段里有两类行 ——
> `submit ctx=… apparentPx=… widthPx=…`（每个上下文一条）与
> `距离自适应 ctx=… 表观=…px → 环宽 …px（基准 …px）`（**只在真的收细时打，每秒最多一条**）。
> 后一条就是"自适应到底有没有生效"的直接证据；如果调远之后它一条都不出，说明物品还没越过分界点，
> 把 `outlineWidthMaxRatio` 调小即可。

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
| `outline_ring.vsh` / `.fsh` / `.json` | 全屏环形合成：16 方向 × 3 半径圆盘采样，`dilate − self`；外加光雾分支（第十一章） |
| `outline_halo_blur.fsh` / `.json` | 光雾的模糊 pass：可分离高斯 9 tap，降采样 + 原地模糊共四遍 |

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

> **按实例开关（同一份物品代码既有描边版也有纯材料版）**：`outlineEnabled(ctx)` 是
> 在 `stack.getItem()` 拿到的**注册项实例**上调用的，所以"只有某一块板描边"不需要
> 按 stack 判断 —— 让构造参数里的描边预设为 `null` 即可。
> 例子见 `ManaPlateItem`：`LIGHT_MANA_PLATE` 是独立注册项（独立实例），
> 只有它传了 `GlowOutline(0xFFFFCB32, 3.0f)`（颜色取自板材贴图的琥珀金边），
> 外面那层光雾仍由全局 `outlineAura*` 配置驱动。

## 十、预设：火焰（ColorMode 4 / `"fire"`）

移植自 **"Inner outline shader for Match2 game cell"**（@remonvv，
[shadertoy.com/view/MdjfRK](https://www.shadertoy.com/view/MdjfRK)）。
实现在 `outline_mask.fsh` 的 `fireColor()`：value noise、三层 fBm（`1 / 2.1 / 5.4`）、
域扭曲这一整套照搬，**但 ramp 的锚点必须换**（见下）。

### 怎么用

```java
// ICustomOutline 路径：一行
@Override public String outlineShaderKey() { return "fire"; }
@Override public int outlineColor()          { return 0xFFFFB03A; }   // 白热核
@Override public int outlineSecondaryColor() { return 0xFF6E1208; }   // 冷尾

// GlintRenderManager 路径：
GlintRenderManager.registerForItem(MY_ITEM.get(), GlintEffectProfile.fire());
```

**用加法混合**（`ICustomOutline.BlendMode.ADDITIVE`，也是默认值）：火焰的尾部很暗，
加法混合下"加 0"等于不画，于是看起来就是几缕火丝贴着物品爬。换成 `TRANSLUCENT`
会把尾部涂成一片深色 —— 那是另一种（烟熏）观感，也能用，但要自己调副色。

> **在线例子：绯红誓约（`CrimsonVow`）** —— `outlineShaderKey()` 返回 `"fire"`、
> `outlineBlend()` 返回 `ADDITIVE`，色板仍用物品自己的粉紫（白热核 = `ACCENT_FROM` 亮粉，
> 冷尾 = `ACCENT_SHADOW` 深紫黑），再叠上第十一章的光雾 → 一条烧着的粉紫边。
> 它同时也是"火焰 + 体积光雾"这套组合的实机参照物。

### ★ 最重要的一处偏差：ramp 的锚点

参考的亮度**来自它自造的那条线**：

```glsl
uv.y = abs(uv.y - shift);                       // 把空间折到线附近
grad = pow((r.y + r.y) * max(0., uv.y) + .1, 4.0);   // 线上"离线距离"≈0 → grad ≈ 1e-4
color = ramp(grad);                             // 1/t 把 grad 小的推成过曝白热
```

而我们的"离线距离"是由环形合成的 **alpha** 承担的（`ring = dilate − self`）。
所以如果把噪声直接丢进 `1/t` 的 ramp，grad 几乎总是很大 → 整圈都是深色尾。

> **这不是推测**：第一版就是这么写的，离屏渲染出来是一片近乎全黑的底上零星几个亮斑
> （`MaskColor` 那份输出 mean=4/255），描边等于看不见。
> 验证工具：`tools/shader-preview/`。

现在的做法是把 ramp **重新锚定在噪声上**，让噪声驱动 `heat` 在 冷尾 ↔ 白热核 之间摆动：

```glsl
float heat = clamp((1.05 - n) * 2.2 + 0.30, 0.0, 1.0);   // n = fireField(...)，均值 ≈ 1
vec3 hot   = mix(MaskColor.rgb * 1.15 + 0.28, vec3(1.0), 0.55 * heat * heat);  // 越热越白
vec3 color = mix(SecondaryColor.rgb, hot, heat);
color /= (1.0 + 0.25 * color);                            // 轻量 tone map
```

### 其余偏差

| 参考 | 这里 | 为什么 |
|---|---|---|
| `uv.x += uv.y < .5 ? 23. + t*.35 : -11. + t*.30` | 只用固定的 `+23.0 + t*0.35` | 那个上下半格分支会在 `uv.y = 0.5` 留一道硬缝；参考里缝落在两条火线之间所以看不见，而我们的"火线"是物品整圈剪影，缝正好落在物品腰线上 |
| 算出 `r.x` 却从未使用 | 删掉 | 少一路 `fireField`，省 1/3 的哈希 |
| 噪声坐标 = `fragCoord / iResolution`（屏幕空间） | 模型空间 | 屏幕空间的图案在镜头移动时会像贴纸一样"滑"过物品；模型空间与既有的 gradient / 彩虹一致 |
| `valueNoise` 用线性插值 | smoothstep | 线性插值在 16px 物品栏图标上会露出晶格网格纹 |
| 无 | `hash21` 里 `mod(n, 64.0)` | 噪声晶格折到 64 格周期后，`Time`（可涨到 12000）才不会把 `sin` 的参数推到 1e6 —— 那会先出现块状伪影、最后整个图案冻住 |

### 复用到的既有 uniform

| uniform | 火焰里的含义 |
|---|---|
| `MaskColor` | 白热核（内部 `*1.15 + 0.28` 之后还会往白色混） |
| `SecondaryColor` | 冷尾；未设副色时它等于主色 → 单色火焰 |
| `ColorFloor` | **火焰尾部有多亮**：0 = 只有几缕火丝；GUI 里由 `guiOutlineMinMix`（0.35）抬起 |
| `ColorFreq.x` | 噪声频率（世界/手持恒为 18 → 系数 1）；GUI 由 `guiOutlineGradientScale` 压小 → 图标上的火焰自动变粗、变成一整条渐变（不会糊成噪点汤） |
| `ColorSpeed` × `ColorScrollSpeed` | 火焰流速（GUI 由 `guiOutlineSpeedScale` 放慢） |
| `Time` | 动画时钟 |

### 代价

每个片元约 **24 次 sin 哈希**（参考实现是它的 6 倍）。剪影遮罩只覆盖物品自身的像素，
所以代价与物品占屏面积成正比，与分辨率无关；一手物品那种占满半屏的情况最贵。
嫌贵就删掉 `fireField` 的第三层八度（`n * 5.4`）——细节少了，观感基本不变。

> 与描边宽度无关：火焰只决定**颜色**，环的几何（`dilate − self`、等宽、距离自适应）
> 和其它模式完全一样共用。

## 十一、描边光雾（体积感）

硬环始终是一条等宽的线。想要"贴着物品的一团雾"，靠的是**另一套几何**：把剪影遮罩
**降采样模糊**成两级，再在合成时叠上去。火焰配色 + 光雾 = "沿着物品边缘燃烧"。

### 一条链，四个 pass

```
遮罩(全分辨率，已含颜色) --H(2 源纹素)--> 1/2a --V--> 1/2b    中层雾
1/2b                    --H(2 源纹素)--> 1/4a --V--> 1/4b    大范围雾
                          ↓
             outline_ring：硬环 + max(宽雾×0.8, 中层雾)，噪声扰动 + 云团阈值
```

- Java 侧：`ItemOutlinePipeline.buildHaloChain`（`compositeAll` 里、环形循环之前跑一次）
- 着色器：`outline_halo_blur.fsh`（水平那两遍的步长取 **2 个源纹素**，顺带完成降采样；
  垂直那两遍取 1 个目标纹素，原地模糊）
- **整屏一次，同屏所有描边物品共用同一份模糊图** —— 这是它比"在全分辨率上做加权径向
  卷积"便宜一个数量级的原因（面积 1/16，且不随物品数量增长）

### 三条硬约束（少一条就坏）

1. **雾必须减掉自身**：`halo = max(halo − self, 0)`。模糊后的遮罩在剪影**内部**也等于 1，
   不减掉整件物品会被糊上一层光 —— 等于把物品贴图和 cosmic 星空层一起盖掉。
   （`mask_glow.fsh` 的 `spill = max(halo − gate, 0)` 是同一个道理。）
   *离屏验证时第一版就错在这里，图里整把武器在发光。*
2. **scissor 矩形要按光雾半径外扩**：`AURA_REACH_PX = 52`（四级模糊核半径之和 ≈ 8+8+16+16），
   再乘 `outlineAuraSpread`。不放大，雾会被自己的剪裁框切出一条直边。
3. **噪声坐标以「该物品的屏幕中心」为原点**（`HaloNoiseOrigin`），并做屏幕高宽比修正。
   直接用屏幕 UV 的话，镜头一动图案就会像贴纸一样从物品上滑过去。

### 配置

| 键 | 默认 | 作用 |
|---|---|---|
| `outlineAuraEnabled` | `true` | 总开关。false = 只剩等宽硬环（回到光雾出现之前） |
| `outlineAuraStrength` | `0.9` | 强度；0 = 关闭 |
| `outlineAuraSpread` | `1.0` | 扩散倍率（放大模糊步长）。1.6 = 更大更淡；上限 4.0 |
| `outlineAuraNoise` | `0.8` | 云团强度：0 = 光滑椭圆光晕，1.5 以上会碎成缕 |

### 代价与已知取舍

- 模糊链是 **4 个全屏 pass**，但都在 1/2、1/4 分辨率上：1600p 下约 1200 万次取样/帧，
  **世界/手持那条路**与物品数量无关（整屏只建一次，同屏所有条目共用）。
  合成 pass 每个物品多 2 次取样（两张模糊图）。
- ⚠ **GUI 是个例外**：GUI 里的描边是"逐物品就地合成"的（`submit` 末尾直接 `drain`，
  因为 GUI 是 `renderLevel` 之后独立的一趟），所以**物品栏里每件描边物品都会各建一次模糊链**。
  描边物品通常是武器、同屏没几件时无所谓；但把一件**可堆叠材料**也设成描边（例如
  光明法力板）时，一个装满它的箱子/背包会让开销随可见件数线性增长。
  真要给常见材料加描边，得先把 GUI 的提交攒到 GUI 末尾再统一合成，或者让这类物品
  在 GUI 上下文不描边（`outlineEnabled(ctx)` 返回 false）。
- 代价转移到 **scissor 面积**：每个物品的矩形按 52px×spread 外扩，同屏描边物品很多时
  填充开销上升（小物品尤其明显：30×30 的条目会变成 ~134×134）。
- 光雾是**屏幕空间**的（固定像素半径），不会像参考图那样"跟着物品一起缩小"；
  需要时用距离自适应那套（第五章）顺带把它的半径也收细。
- 光雾的颜色**自动来自遮罩**，所以配 `"fire"` 就是火焰雾；想要"内层白热、外层紫蓝"的
  层次，需要在合成 pass 里按 `halo` 强度再插一条色带（尚未做）。

## 十二、预设：燃烧描边（ColorMode 5 / `"fire_2"`）—— 世界空间体积火焰

屏幕空间的剪影膨胀/模糊没有体积、范围也被锁死；fire_2 最终改成**世界空间体积渲染**：
以物品为中心、**固定尺寸**（0.9 × 1.0 × 0.9 格）的盒子里 ray marching 3D 噪声。

- **有体积**：镜头横移时火焰层次会变，任何角度看都是三维的；
- **范围固定**：盒子是世界尺寸，不随物品大小 / 距离缩放（"火大小固定"）；
- **遮挡正确**：用场景深度截断每条射线，墙/物品后面的火不会透出来（无光影包时）。

### 管线位置（全部离屏，绘制只在 TAIL）

```
capture 点    只做一次深度 blit：把当前 FBO 的深度附件拷到一张深度纹理
              （世界/光影 GBuffer 都行；不在这里画任何东西）
TAIL          1) 世界批：用刚拷下来的世界深度，在离屏目标 outline/fireVolume 上 raymarch
              2) 加法合成回主 RT
              3) 再 blit 一次主 RT 深度（此刻里面是手）→ 画一手批 → 合成
              4) 最后才画描边环
```

**盒子在视图空间**（与描边重放同一套 `pose × modelView`），所以世界物品 / 一手都一定围着
物品本身；只有噪声与"高度"用相机基向量把采样点搬到世界空间求值，火才是沿世界上方窜的。

为什么要离屏：片元要读场景深度，而"采样正被绑成当前 FBO 附件的纹理"是未定义行为；
先 blit 到独立深度纹理、再在另一张 FBO 上采样就完全绕开了。

### 着色器 `fire_volume`

- 全屏四边形 + 用 `InvProj` 从 `gl_FragCoord` 反解视线方向，再用相机右/上/前基向量搬到世界空间；
- 世界空间 ray-box（固定盒）求交，步进 **32** 步；
- 密度 = **外壳**（物品 AABB 的 SDF，指数衰减）× **火柱**（物品上方竖直柱）×
  `fbm3(世界坐标 + 上升平流)` × 域扭曲；
- 温度 → 颜色（冷尾 → 主色 → 白热），**Beer 累积**，预乘输出，加法混合（`ONE, ONE`）；
- 深度：`DepthSampler` 采样主 RT 深度（逆投影反解视图距离）截断 `tExit`；
  `UseDepth = 0` 时只按盒子边界截断。

### 上下文与时机

| 上下文 | 深度来源 | 备注 |
|---|---|---|
| 掉落物 / 展示框 / 三手 | 捕获点 blit 下来的世界深度 | 光影包下来源是 GBuffer，同样可用 |
| 一手 | TAIL 时主 RT 的手部深度 | 与上一条同一套 blit 流程 |
| GUI | 不画 | 世界空间效果在 GUI 里没有意义 |

### 已知局限（需要游戏内实测确认）

- **光影包（Oculus / Iris）**：所有绘制本来就在 TAIL，不会被最终合成覆盖；深度来自捕获点
  从当前绑定 FBO 拷的 GBuffer 深度。若该包深度不可 blit（极端情况），会退化成"不遮挡"。
- **固定盒尺寸**写死在 `ItemOutlinePipeline.FIRE_VOLUME_HALF / FIRE_VOLUME_HEIGHT`
  （0.45 / 1.0 格），一手再乘 `0.62`（离相机近，同尺寸会显得很大）。
- **形状不是立方体**：火柱半径只有 0.26 格左右并被噪声啃出锯齿，六个盒面都做了 `boxFade`
  淡出，所以看不到盒子的硬边。
- **防 NaN**：射线方向的分量接近 0 时用有限大值代替、`tExit` 夹到 1e4；
  输出前若 `acc` / `alpha` 出现 NaN/Inf 直接 `discard`。
  另外离屏 clear 之后**必须还原 clearColor** —— 不还原的话后面任何一次 clear 都会把画面清成透明黑
  （实测症状：丢出物品后整屏黑，只剩 HUD 和描边）。
- **物品自身遮挡**依赖深度：无光影包时正确（物品先写深度）；光影包下退化为不遮挡。
- 阴影 pass 必须跳过（当前只在主 pass 画；`ShaderPackDetector.isShadowPass()` 已有闸门）。
- 体积火是**纯自发光**：不照亮世界、不参与阴影。

### 验证

```powershell
# 体积火焰的离屏预览（假相机 / 假盒子；验证 raymarch 观感与数学，不验证遮挡）
.\tools\shader-preview\preview.ps1 -ColorMode 9 -Time 3 -Fire 1
# 产物：build\shader-preview\volume.png
```

游戏内要看的四件事：
1. `run/logs/latest.log` 里 `[Shaders] 加载 fire_volume` 是否成功（失败会明确报错）；
2. 掉落物 / 展示框 / 一手 三处是否都有火；
3. 走到墙后，火是否被挡住（无光影包时应该被挡住）；
4. 光影包开启时是否仍然可见（应该可见、但可能透墙）。
