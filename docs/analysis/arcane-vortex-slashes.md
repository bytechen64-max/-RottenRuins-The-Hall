# ArcaneVortex 0.7.10 挥砍与刀光实现分析

分析对象：用户提供的 `[秘法涡流] ArcaneVortex [Alpha]-0.7.10-1.20.1-Forge.jar`。
SHA-256：`92bb03ac9bddcd07f8e34d1bb39a3689fc930c1ad04326f3f4466f0e9743d79a`。

本次为本地 JAR 的静态分析：反编译相关类、读取 shader 及配置，并对照当前项目和本地 Minecraft 1.20.1 源码。没有启动该模组验证实机效果，也没有改动本项目的战斗或渲染实现。反编译得到的局部变量名和部分方法名不可直接当作原作者源码。

## 最重要的结论

最值得参考的是**武器与刀光由同一套挥砍状态驱动**，以及**明确、完整的透明混合管线**。并非直接换一张贴图、增加细分或把四边形改成三角带就能解决问题。

它有两套不同系统，不能混为一谈：

| 系统 | 用途 | 实际机制 |
|---|---|---|
| `MeleeProjBase` + `MeleeProjBaseRenderer` | 近战挥砍及扇形刀光 | 挥砍实体保存角度、平面姿态、内外半径；渲染武器和历史角度扇面 |
| `TrailProjEntity` + `TrailProjRenderer` | 通用弹道、长拖尾 | 空间路径、按弧长重采样、曲线细分、多层材质、方向连续处理、LOD 和预算 |

## 1. 近战效果如何生成

### 同一套运动状态

`MeleeProjBase` 保存起止角度、旋转时长、平面欧拉角、朝向、内外半径，以及 `currentRotation`、`prevRotation`、`rotationCache`。每个游戏 tick 推进一次角度并更新历史；渲染时再使用 partial tick 插值。

默认基类参数是 6 tick 旋转、历史长度 7、每段细分 5；这些只是基类默认值。检查到的 AshBurial 实例使用历史长度 6、每段细分 10，并配置 2/3 tick 的不同连段。不能把默认参数当作所有武器的参数。

武器和刀光共同使用 `renderBasePos()` 与 `swingRotation()`；武器模型再按当前角度放置和转动，刀光按历史角度展开。两者由前向运动状态生成，不需要从最终物品渲染矩阵反推一次动画。

证据：[MeleeProjBase](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/decompiled/com/luoee/arcanevortex/Content/Entity/MeleeProj/MeleeProjBase.java:659)；[武器模型与刀光共用变换](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/decompiled/com/luoee/arcanevortex/Content/Entity/MeleeProj/MeleeProjBaseRenderer.java:215)；[连段参数实例 AshBurial](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/decompiled/com/luoee/arcanevortex/Content/Item/Sword/AshBurial.java:91)。

### 扇面几何

刀光的基础形状是在局部 XY 平面上沿角度展开的环形带。每个角度对应内、外两点，再用统一平面旋转和世界位置放入场景。材质 UV 的一个方向沿角度历史，另一个沿内外半径。这里的刀光本身仍是薄面；空间感来自平面倾斜、共同运动、场景遮挡和材质层次。

实际 RenderType 使用 `TRIANGLE_STRIP`，顶点按内外边成对提交。它没有用物品纹理上标注的任意三维刀刃锚点，也没有在这条近战路径里使用 Catmull–Rom。

证据：[renderTrail / trailColor](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/decompiled/com/luoee/arcanevortex/Content/Entity/MeleeProj/MeleeProjBaseRenderer.java:350)；[bladeTrail RenderType](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/decompiled/com/luoee/arcanevortex/Content/ModRenderType.java:208)。

### 淡出与历史处理

近战路径把历史位置归一化为 u，前端使用 smoothstep 淡入，再乘尾端衰减，避免刀光从一个硬切口突然出现。该实现 u=0 是新头部、u 增大趋向旧尾部；本项目现有贴图方向是从旧尾到新头，借鉴时需要统一 UV 方向。

挥砍停止后仍继续把最终角度推入缓存，旧角度逐渐被替换，扇面自然收缩。它不是单纯让整张刀光保持原形然后统一变透明。

需要保留一个差异：这条近战路径只缓存角度，整条历史使用当前持有者中心与平面旋转重建。因此它虽然在世界里绘制，历史轨迹也可能随玩家中心一起移动，不等价于本项目“历史世界点留在原处”。

## 2. 第一、第三人称的处理

第一人称在存在自己拥有的近战投射实体或自动攻击时，取消原版主手 RenderHandEvent。第三人称也取消对应主手物品层，改由近战实体绘制武器；另外还有让玩家手臂指向武器方向的处理。

所以它能让武器与世界刀光高度一致，部分原因是**它直接统一了武器的世界绘制入口**。不是先保持原版手持投影，再额外往屏幕上覆盖刀光。

这套玩家动作不适合直接照搬：我们的要求是无玩家动画、剑尖朝前、四向扫过，以及原版手持/下方归位。可参考共享状态和世界绘制思想，保留自己的武器位姿约束。

代码中存在 `firstPersonNoDepth` 配置和 `useNoDepth()` 辅助方法，但在本次检查到的近战 renderer 路径中，实际 `renderTrail()` 没有调用这个辅助方法；使用的 RenderType 明确是 LEQUAL 深度测试、COLOR_WRITE、不剔除背面、透明混合。不能仅凭字段名就断言它第一人称实际关闭了深度测试。

证据：[第一人称取消原版主手绘制](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/decompiled/com/luoee/arcanevortex/Help/AutoSwing/ClientSwingInput.java:82)；[第三人称手臂指向武器](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/decompiled/com/luoee/arcanevortex/Content/Entity/MeleeProj/MeleeSwingArmHelper.java)；[bladeTrail RenderType](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/decompiled/com/luoee/arcanevortex/Content/ModRenderType.java:208)。

## 3. 材质为何更丰富

`melee_trail.fsh` 并不是只有一条程序化亮线，而是组合形状贴图、滚动纹理、扰动图、调色带、发光纹理和可选背景扭曲。

- 基础遮罩包含纹理亮度与 alpha；顶点 alpha 再参与最终透明度，暗区不会自然变成一整块实色填充。
- 流动纹理沿刀光 UV 滚动，形成内部变化。
- 扰动纹理改变采样位置，增加边缘和内部的不规则性。
- 调色带把强度转换为色彩层次，减少只靠固定 RGB 得到的单色块。
- 部分类型另外复制场景颜色，绘制折射/背景扭曲；还有独立发光合成调用。

其中某些特殊模式确实使用屏幕像素坐标采样纹理。我们希望减轻贴屏感，适合优先参考基于刀光 UV 的材质流动，而非直接采用这些屏幕采样分支。底层部分后处理类已混淆，本次没有完整还原其所有合成细节。

证据：[melee_trail.fsh](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/resources/assets/arcanevortex/shaders/core/melee_trail.fsh)。

### 当前项目发现的一个优先核查项

ArcaneVortex 的 `melee_trail.json` 显式声明 SRC_ALPHA / ONE_MINUS_SRC_ALPHA 混合。当前项目 `slash_trail.json` 缺少 blend 节点。

本地 1.20.1 源码确认：shader JSON 缺省 blend 会解析成不透明 BlendMode，ShaderInstance.apply() 会应用这个状态。在特定前序状态下，它可能覆盖 RenderType 开启的透明混合，使原本低 alpha 的亮色直接覆盖背景。BlendMode 存在状态缓存，因此具体表现还与绘制顺序有关；静态分析不能证明每一帧都会触发。

这是此前“大白片、硬边、降低 alpha 仍显得很实”的一个具体可疑来源，应该优先实机验证。它和已经复现的剪切矩阵插值错误是两个不同问题。

现有隐藏 OpenGL 预览直接启用混合，没有走 Minecraft 的 shader JSON 解析及 ShaderInstance.apply()，因此它可以验证几何/GLSL，却无法排除这个引擎管线问题。后续透明度验证应覆盖这条真实调用链。

证据：[melee_trail.json](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/resources/assets/arcanevortex/shaders/core/melee_trail.json:1)；[Minecraft ShaderInstance.parseBlendNode](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/minecraft/ShaderInstance.java:250)；[Minecraft ShaderInstance.apply](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/minecraft/ShaderInstance.java:337)；[Minecraft BlendMode 默认值与 apply](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/minecraft/BlendMode.java:31)。

## 4. 通用拖尾中可以单独借鉴的部分

`TrailProjRenderer` 先计算路径累计长度，按距离均匀重采样控制点，再进行标准 Catmull–Rom 细分。它用居中切向计算横向方向；方向退化时复用上一段，前后方向点积为负时翻转符号，避免带状面翻面。

它还将宽度、透明度、头尾形状、颜色、混合模式、UV 滚动、Bloom 作为独立层参数，并提供距离 LOD、采样预算和批次池。

这些很适合改善我们的采样密度和材质层次，但该通用系统默认存在面向相机的朝向模式。我们应保留世界挥砍平面；同样，Catmull–Rom 可能在急转和断点附近过冲，不能对传送、换刀、不同攻击的端点盲目连接。

证据：[通用拖尾的重采样、细分和朝向处理](C:/Users/chy20/Desktop/desktop/Project/RottenRuinsSplendiding/build/analysis/arcane-vortex/decompiled/com/luoee/arcanevortex/Content/Entity/TrailProj/TrailProjRenderer.java:268)。

## 5. 对当前模组的建议顺序

1. **先验证并补齐真实透明混合链路。** 从 shader JSON、ShaderInstance.apply() 到实际 RenderType、深度和恢复顺序一并检查；不要只继续降低 shader 里的 alpha。
2. **建立共享的武器位姿求值器。** 对给定时间返回一套明确的挥砍位姿；物品渲染和刀光锚点都消费它。锚点编辑器继续负责确定刀根/刀尖等模型位置。保留我们的四向、剑尖朝前和原版归位约束。
3. **历史点继续落在世界中。** 可以参考对方独立于帧率的状态历史与细分思路，但不直接替换成“所有旧角度跟随当前玩家中心”的方案。
4. **按实际运动距离或角度自适应补点。** 低帧率高速段增加密度，慢速段减少；每条刀光共享截面，保持方向连续，在真正的不连续处断开。
5. **把视觉拆成层。** 清楚的轮廓层、低透明度流动层、少量碎光层，再按需要添加低强度 Bloom。头端、尾端、径向边缘和整刀寿命分别控制淡化。

`TRIANGLE_STRIP` 并不是需要优先替换的核心：我们的四边形也可以连续。统一运动来源、正确混合和历史连接，比改一种图元更重要。

JAR 元数据声明 `All Rights Reserved`。本报告记录机制及独立实现建议，未把其类、shader 或贴图加入当前模组源代码；选取的反编译产物只保存在本地 build/analysis 目录中。


## 本项目落地记录（2026-11-12）

后续授权优化已独立实现：明确 shader 混合配置、SlashPoseFrame 共享位姿、按实际位移和 tick 时间补采样、头尾包络，以及基于刀光 UV 的低透明度流动层。继续保留世界历史点、剪切矩阵插值修复、四向剑尖朝前的动作及天穹裁决 9 tick 冷却/8 tick 动画。未引入该 JAR 的代码、贴图、玩家动作或后处理模块。

真实 Minecraft 管线测试结果：从不透明与透明两种前序状态开始，低 alpha 的绿色通道为 60，高 alpha 为 145；前方深度均能遮挡刀光。此前直接 OpenGL 预览没有覆盖的 shader JSON/ShaderInstance 混合链路，现在由客户端 FBO 像素测试覆盖。

## 再次检查近战折射（2026-11-12）

这次直接检查了 `MeleeProjBaseRenderer.renderWarp`、`melee_trail.fsh::renderWarpPass`、`MainRenderMixin` 和单独反编译的 `SceneCopyTarget`，不是把流动纹理本身当作折射。

- `MainRenderMixin` 的合成阶段先调用刀刃/刀光绘制，再在独立 WarpLayer 中调用折射。
- 近战 `renderWarp` 只处理 useType 2/3/4/5，每个实例先 `copyMainColor()`，绑定 `MainTarget0`，设 useType=-1 与对应 warpType，再绘制扇面。
- 类型 2/3 用扰动图亮度控制归一化 UV 位移，上限系数为 0.1；类型 4/5 用主贴图亮度×alpha 经幂和 smoothstep 得到遮罩，位移系数为 0.015。实际量还乘 UV 距中心偏移，不能把这些常数直接当成像素数。
- 折射分支直接返回采样背景的 RGB、alpha=1；未像本项目旧实现一样再次乘整条刀光的低透明度。
- `SceneCopyTarget` 按主目标实际大小创建无深度颜色目标；复制前禁用 scissor 并开放 color mask，blit 后恢复 read/draw framebuffer、scissor 和 color mask。尺寸改变时重建目标。

本项目旧版的主要可见度问题：3.5 像素位移较弱，且空气带与明亮材质重合，叠加 alpha 后难以辨认。之前的单独 shader FBO 检查不能覆盖场景复制和实际排队绘制。

本次独立实现保留世界网格、深度测试与历史点：先快照整帧刀光；在原版手持渲染前（光影包则延迟到合成入口）复制干净背景、绘制空气带，再绘制发光材质。空气带相对刀光截面扩展 18%，让折射出现在亮边外侧。天穹裁决默认 refraction=9，按 1080p 像素定义并随渲染目标高度缩放。保留 refraction=0 关闭、色散和扰动参数。

新增真实客户端队列测试直接覆盖生产 `SlashAirRefraction.enqueue/renderAll` 的网格扩展、场景 blit、投影回放与遮挡。测试色阶背景的红通道结果为关闭 107、开启 133、遮挡 107，且 GL error=0。测试只证明功能链路有效，实战观感与外部光影包仍由实机检验。

新增反编译文件仅在 `build/analysis/arcane-vortex/refraction`。未移入参考模组的 shader、贴图或类文件。
