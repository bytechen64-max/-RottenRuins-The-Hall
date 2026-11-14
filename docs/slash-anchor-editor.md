# 刀光锚点编辑器

日常外观调节请使用[三维刀光工作台](slash-effect-workbench.md)。原锚点工具现在位于 `/anchors`，作为高级标定功能。效果宽度、亮度、寿命等由独立 `slash_effects` JSON 控制。

双击项目中的 `tools/start-slash-anchor-editor.cmd`。需要 Python 3.10+ 与 Pillow（`python -m pip install Pillow`），浏览器需支持 WebGL。工具完全在本机运行，不使用 CDN、不上传模型或贴图。

1. 搜索并载入物品。工具读取 `src/main/resources/assets` 与 `src/generated/resources/assets`，合并模型父级、纹理引用和元素。
2. 选择第一人称或第三人称。两个视角的锚点和采样设置独立保存，可用“复制到另一视角”同步。
3. 对平面物品，在纹理上依次标出**刀根、刀尖**，拖动调整位置。允许 2–16 个点；复杂刀刃可加中间点。顺序沿刀身排列，不是围着刀刃画封闭轮廓。
4. 对元素模型，在模型预览中拖动旋转、滚轮缩放，Shift 点击模型顶点添加锚点，再用 XYZ 输入框微调。也可导入模型 JSON 和预览用 PNG。导入的模型/纹理只用于预览，不修改游戏资产。
5. 开启“轨迹演示”检查点序、方向及覆盖刀身的范围。演示使用简化运动；游戏内从真正的物品渲染矩阵采样。
6. 保存到模组资源后重新构建；保存到游戏配置后，在对应游戏中按 **F3+T**。导出 JSON 可用于资源包或版本管理。覆盖保存前会留一份 `.json.bak`。

默认游戏实例目录是项目 `run`。外部实例可这样启动：

```powershell
.\tools\start-slash-anchor-editor.ps1 -GameDir 'D:\Minecraft\instances\test\.minecraft'
```

或：

```text
python tools/slash-anchor-editor/server.py --game-dir "D:\Minecraft\instances\test\.minecraft"
```

默认端口 8765；可传 `--port 8766`。工具只监听 `127.0.0.1`，关闭终端即停止。未显式提供外部实例路径时，保存按钮不会修改其他游戏实例。

## 配置格式

物品 `rotten_ruins:true_copper` 对应：

- 模组／资源包：`assets/rotten_ruins/slash_anchors/true_copper.json`
- 游戏本地覆盖：`config/rotten_ruins/slash_anchors/rotten_ruins/true_copper.json`

本地配置优先于资源。删除覆盖文件并按 F3+T 即恢复资源配置。

```json
{
  "version": 1,
  "firstPerson": {
    "enabled": true,
    "points": [[6.5, 4.5, 8], [14.5, 15.5, 8]],
    "start": 0.32,
    "end": 0.62,
    "lifetimeTicks": 7,
    "maxDistance": 4,
    "maxSamples": 128,
    "subdivisions": 4
  },
  "thirdPerson": {
    "enabled": true,
    "points": [[6.5, 4.5, 8], [14.5, 15.5, 8]]
  }
}
```

坐标使用物品模型原始单位，16 单位为一格，原点不是手柄中心。generated/handheld 的纹理左下角为 X=0、Y=0，纹理顶端为 Y=16，平面中心 Z=8。元素模型使用其 `from`、`to` 所在坐标系；锚点写元素旋转之后的位置。不要把 `display` 旋转/缩放重复烘进锚点，游戏采样已经包含这些变换。

除 `points` 外，视角配置字段均可省略，默认值同上。也可在根级提供 `points` 作为两个视角的共同回退。

| 字段 | 含义 |
|---|---|
| `start` / `end` | 相对于主动画时长的采样区间，0–1；默认只采真正切割阶段，不连接起手/归位 |
| `lifetimeTicks` | 每个历史截面的寿命，0.1–200 tick；20 tick 为一秒 |
| `maxDistance` | 两次实际渲染间任一锚点位移超过此格数时断开，避免传送拉出长线 |
| `maxSamples` | 截面历史数量上限，2–1024 |
| `subdivisions` | 每 tick 的补采样基准，1–32；同时按锚点世界位移自适应增加密度，单帧最多补 32 段，不推进游戏动画 |

铜剑使用像素贴图刀光，多米剑和天穹裁决使用 GLSL 刀光；材质、颜色和音效继续由各武器 `SlashSpec` 选择。两种材质均有扇形亮边、不规则碎边和独立寿命的碎光粒子，历史截面以平滑曲线淡出，另外独立淡化当前轨迹的头端与尾端。着色器的低透明度填充、亮边和流动细纹分层控制，流动使用刀光 UV 与时间，不依赖屏幕像素坐标。锚点决定轨迹的空间范围，因此内置锚点刀光不再使用旧的 `SlashTrail.width/radius` 推算独立圆弧。没有锚点配置时不生成内置轨迹，武器动画和伤害接口仍可工作；没有实现 `ISlashWeapon` 的物品不会因添加 JSON 获得挥砍。

第一、第三人称各自保存锚点历史，但两种轨迹都只在世界阶段按场景深度绘制。第一人称捕获时将手持物品坐标转换为世界坐标，并补偿手持 FOV 与世界 FOV 的差异；出生时仍与剑身投影对齐，随后玩家移动和转头会产生真实视差，旧轨迹留在世界中并受方块遮挡。第一人称捕获发生在世界绘制之后，所以新截面在下一次世界绘制时出现，通常相差一帧。

取消侧视时朝屏幕方向增加 0.18 格厚度的处理。两种视角统一沿真实扫掠面的法线生成 0.025 格的轻微弧面，保留作者锚点，侧看允许自然变薄。降低总体不透明度与着色器实心区域的覆盖，避免近景叠加成亮色长条。UV 绑定本刀固定采样区间，不随旧截面删除而重新拉伸。

天穹裁决左键范围普攻保持 25 点普通伤害和 5 点裁决直写伤害，暴击只放大普通基础部分，沿用原有胜利增益。耐久每刀只扣一次；右键技能逻辑沿用原实现。普攻冷却从 16 tick 降到 9 tick，动画从 14 tick 降到 8 tick，主命中进度为 0.42（约第 4 tick）；攻击速度属性为 2.5 次/秒，让蓄力恢复能跟上实际普攻节奏。

## 运行时实现

`SlashPoseFrame` 是物品显示矩阵和模型锚点的共享位姿。动画求解一次后，物品渲染消费 itemPose，刀光消费同帧 modelPose；自定义 SlashVisuals.transform 也包含在发布的位姿里。描边及附加特效的重绘不再成为刀光运动来源。无法得到共享帧的非标准渲染器仍可回退到 ItemRendererMixin 的最终矩阵捕获。SlashItemCaptureMixin 记录玩家、物品、视角；缓存按攻击序号、时间、显示上下文校验。前一帧完整矩阵保留位置、旋转（即 oldRot）、缩放、剪切和镜像；通过极分解分离旋转与伸缩/剪切，旋转使用四元数最短弧插值。不能只用普通 TRS 重建，否则不同 FOV 和斜向模型引入的剪切会在补采样中丢失，产生“补出的帧变形、真实帧突然归位”的锯齿割裂。相邻截面使用居中切向，并统一法线符号，避免短暂反向或停止时弧面翻面。

两个视角均把实际模型坐标还原到世界空间，以固定的双精度世界原点保存。第一人称采样额外补偿两套投影并将近手空间等比放入战斗距离内；两者共用世界深度绘制阶段，各自维护历史。描边捕获和光影阴影 pass 不采样；同一时间重复渲染只记录一次。换武器、取消攻击、跨世界、资源重载或下一刀开始时清理对应历史。

这套轨迹只影响视觉，不作为命中判定。服务端范围攻击、冷却与暴击保持独立且权威。Geo/自定义物品渲染器若内部又变换骨骼，需要在它的实际刀身骨骼处另接采样；普通烘焙模型可直接使用。

## 真实引擎透明混合

slash_trail.json 明确配置 SRC_ALPHA / ONE_MINUS_SRC_ALPHA，并为 alpha 通道配置 ONE / ONE_MINUS_SRC_ALPHA。RenderType 同时启用透明混合、LEQUAL 深度测试、只写颜色，避免 ShaderInstance.apply 使用缺省不透明状态覆盖绘制设置。不能只看 shader 输出的 alpha 数值判断透明混合是否生效。

客户端 smoke 测试新增 SlashBlendProbe：在真实 Minecraft RenderTarget 上通过正式 RenderType 和 ShaderInstance 绘制，分别从不透明/透明前序状态出发比较低、高 alpha 的实际像素，再用前方深度验证遮挡。测试探针仅在独立开发 source set 编译，不进入发布 JAR。

## 验证

- `python tools/tests/slash_editor_test.py`：模型解析、路径校验、API 保存/备份/令牌与 JSON 回读。
- `tools/tests/SlashReferenceTest.java`：物品与锚点共享位姿、显示变换抵消、左右手、距离自适应补点和头尾淡化。
- `tools/tests/TrailAnchorsTest.java`：历史寿命、重复 pass、断裂、采样上限、完整矩阵插值、镜像变换与配置校验。
- `tools/tests/TrailAffineInterpolationTest.java`：复现并校验不同投影缩放下的剪切矩阵插值、镜像武器、真实帧衔接和截面法线连续性。
- `./tools/preview-slash-trails.ps1 -Shear`：两种材质、四个方向，使用带剪切的真实帧矩阵及补采样绘制，检查帧间衔接。
- `tools/tests/TrailWorldTransformTest.java`：不同手持/世界 FOV 下的出生投影对齐，以及移动、转头后的世界视差。
- `tools/tests/TrailRibbonProjectionTest.java`：四方向、四种宽高比、三档 FOV 的投影面积、锚点保留和拖尾淡出。
- `./tools/preview-slash-trails.ps1 -Tracked`：真实 OpenGL 编译两种材质，并逐格检查第一人称四种姿势均有可见像素；不传 `-Tracked` 则保留世界弧面材质预览。
- `tools/tests/slash-editor-browser.cjs`：开发用 Playwright 浏览器测试；需本机 Edge 与 `build/slash-editor-test/node_modules/playwright-core`，以及运行中的编辑器。编辑器本身不依赖 Node。
- `./gradlew.bat build --offline --console=plain`：编译与打包。
- `./gradlew.bat -I tools/slash-client-smoke.init.gradle runClient --offline --console=plain`：隔离实例启动客户端，验证 Mixin、锚点资源和 GLSL 加载后自动退出；不修改现有存档，探针不进入成品包。

浏览器测试及离线矩阵测试不能替代游戏内视觉验收，尤其是 Oculus 等外部光影管线。
