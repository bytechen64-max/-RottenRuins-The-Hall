# GUI 离屏预览 / 模拟工具

**不启动 Minecraft**，把 GUI 元素按实机布局、字号、着色器和混合语义画成 PNG。

和隔壁 [`tools/shader-preview`](../shader-preview/README.md) 的分工：

| | `tools/shader-preview` | `tools/gui-preview`（本目录） |
|---|---|---|
| 验证对象 | **单支着色器**的算法与观感（物品描边、法阵、光柱、魔法星图…） | **GUI 组合**：布局 / 坐标 / 字号 / 贴图叠加顺序 / 混合 |
| 输入 | 着色器源码 + 少量几何 | 项目 `shaders/core/*.json`（连 attributes/samplers/uniforms/blend 一起）、GUI 贴图、MC 位图字体 |
| 产物 | 效果本身的图 | "打开某个界面/悬浮 tooltip 时长什么样" |

两者都只能验证画面，不能验证 Java 侧的 `RenderType` / `PoseStack` 批处理状态簿记 ——
那部分仍然要进游戏。

## 快速开始

前置条件：

- JDK 17（`javac` / `java` 在 PATH 上）
- 至少跑过一次 `.\gradlew.bat build`（LWJGL 落进 Gradle 缓存；`client.jar` 用于抽 MC 位图字体）
- 能创建 OpenGL 3.2 core 上下文（这台机器上 NVIDIA 驱动实测正常；不需要可见窗口）

在仓库根目录：

```powershell
cmd /c "tools\gui-preview\gui_preview.cmd"                                  # smoke 自检
cmd /c "tools\gui-preview\gui_preview.cmd -Scene tooltip -Time 4.5"          # tooltip 预览
cmd /c "tools\gui-preview\gui_preview.cmd -Scene collapsar_bar -Time 1.5"    # 坍缩使徒血条
cmd /c "tools\gui-preview\gui_preview.cmd -Scene gui_effects -Time 2.0"      # GUI 特效
cmd /c "tools\gui-preview\gui_preview.cmd -Scene creative_inventory"      # 创造栏隔断
cmd /c "tools\gui-preview\gui_preview.cmd -Scene difficulty -Time 2.0"       # 难度选择界面
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_wheel -Time 3.4"      # 魔法轮盘施法 HUD（设计稿）
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_loadout -Time 2.0"    # 魔法装载界面（设计稿）
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_forge -Time 2.4"      # 法术合台（金色星空设计稿）
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_forge_styles -Time 2.4" # 法术合台：原版绘制 / 占位纹理两套方案
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_weave -Time 2.4"        # 织星台：画星座织法术（平滑发光概念稿）
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_weave_styles -Time 2.4" # 织星台：像素风格 A 绘制 / B 占位纹理
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_weave_hybrid -Time 2.4" # 织星台：像素骨架 + 平滑能量混合渲染
cmd /c "tools\gui-preview\gui_preview.cmd -Scene wand_hud -Height 300 -Time 2" # 法杖 HUD 静态对照
cmd /c "tools\gui-preview\gui_preview.cmd -Scene wand_hud -Height 300 -Time -1" # 导出 204 帧动效序列
cmd /c "tools\gui-preview\gui_preview.cmd -All"                              # 全部场景
```

> 直接执行 `.\gui_preview.ps1` 会被这台机器的执行策略拦住，所以统一走 `.cmd` 包装
> （`-ExecutionPolicy Bypass` 只对那一个进程生效，和 `tools/shader-preview` 的做法一致）。

参数：

| 参数 | 默认 | 说明 |
|---|---|---|
| `-Scene` | `smoke` | 场景名（→ 类 `Scene<CamelCase>`） |
| `-OutDir` | `build\gui-preview` | PNG 输出目录 |
| `-Time` | `0` | 动画时间（秒），场景里所有流动/闪烁的时间基准 |
| `-Width` / `-Height` | `640` / `400` | **逻辑**画布尺寸（游戏 GUI scale 那一层） |
| `-Scale` | `2` | **原生渲染倍率**：逻辑画布 × 倍率 = 实际渲染/PNG 尺寸（默认 1280×800，等价"1280×800 窗口、GUI scale 2"）。布局用逻辑坐标，但光栅化发生在物理分辨率上——不是低分辨率放大 |

产物在 `build\gui-preview\`（已被 `.gitignore` 的 `build/` 覆盖，不进仓库）。

## 场景清单

| `-Scene` | 文件 | 覆盖的 GUI 元素 | 产物（`build\gui-preview\`） |
|---|---|---|---|
| `smoke` | `SceneSmoke.java` | 框架自检：字体 / fill / 渐变 / 描边 / 贴图 / 四支 GUI shader | `smoke.png`、`smoke_frame1.png`、`smoke_overlay.png` |
| `tooltip` | `SceneTooltip.java` | 绯红誓约物品 tooltip：热力底板 + 1px 渐变边框 + 流动名字 + lore + 格挡减伤条 | `tooltip_ingame.png`(+`_t3`)、`tooltip_flat.png`(+`_t3`)、`tooltip_plain.png`、`tooltip.png` |
| `collapsar_bar` | `SceneCollapsarBar.java` | 坍缩使徒 boss 血条：mask 内容 + outline 框架 + 星尘 + 低血抖动（`-Time 4.985` 时抖动最明显） | `collapsar_bar.png`、`collapsar_stars.png` |
| `gui_effects` | `SceneGuiEffects.java` | 恐怖 Voronoi 全屏叠加（3 档强度）、难度图标径向光晕（`gui_glow`） | `gui_effects.png`、`gui_voronoi.png`、`gui_glow.png` |
| `creative_inventory` | `SceneCreativeInventory.java` | 创造物品栏：冷蓝灰银边、金属铭牌与固定银色标题，读取正式贴图 | `creative_inventory.png` |
| `difficulty` | `SceneDifficulty.java` | 难度选择界面：卡片 + 柔光 + 程序化法阵（`difficulty_sigil`，UV 编码逐字移植）+ 图标 + 文字 | `difficulty_screen.png`、`difficulty_sigils.png`、`difficulty.png` |
| `spell_wheel` | `SceneSpellWheel.java` | **魔法轮盘施法 HUD（设计稿）**：8 方位花瓣轮盘、只显示已绑定主动法术、中心核心/冷却环/蓄力环、法力条、夜空山景模拟 | `spell_wheel.png`、`spell_wheel_idle.png`、`spell_wheel_charge.png`、`spell_wheel_cooldown.png`、`spell_wheel_empty.png` |
| `spell_loadout` | `SceneSpellLoadout.java` | **魔法装载界面（设计稿）**：左侧法术图鉴（ACTIVE/PASSIVE 页签）、中间轮盘配置预览、右侧详情、拖拽绑定态、被动只读态 | `spell_loadout.png`、`spell_loadout_drag.png`、`spell_loadout_passive.png` |
| `spell_forge` | `SceneSpellForge.java` | **法术合台（金色星空设计稿）**：蓝图 / 材料输入、七学派熔炉核心与刻录进度、法术预览、图鉴未知态 | `spell_forge.png`（刻录中）、`spell_forge_ready.png`、`spell_forge_codex.png` |
| `spell_forge_styles` | `SceneSpellForgeStyles.java` | **法术合台：两套原版风格提案**：A 纯 fill 绘制原版斜角 / B 读取占位 PNG（`textures/gui/spell_forge/`）；含 Forge/Codex 页与 1280×440 并排对比 | `spell_forge_a_drawn*.png`、`spell_forge_b_textured*.png`；`-Width 1280 -Height 440 -Scale 1` 出对比图 |
| `spell_weave` | `SceneSpellWeave.java` | **织星台（自由合成概念稿）**：可复用符印托盘 + 星空连线星座 + 实时推演（名称/数值/效果/稳定性/复杂度）+ 星砂燃料；稳定与不稳定两张，用项目 `magic_starfield` / `magic_constellation` / `magic_pulse` 着色器 | `spell_weave.png`、`spell_weave_unstable.png` |
| `spell_weave_styles` | `SceneSpellWeaveStyles.java` | **织星台像素 A/B**：A 全部 `fill` 画像素面板/符文/点阵星云，B 读取 `textures/gui/spell_weave/` 占位 PNG（生成器 `tools/generate-spell-weave-gui-placeholders.py`）；几何完全一致 | `spell_weave_a_pixel*.png`、`spell_weave_b_tex*.png`；`-Width 1280 -Height 470 -Scale 1` 出对比图 |
| `spell_weave_hybrid` | `SceneSpellWeaveHybrid.java` | **织星台混合渲染**：像素 chrome（面板/文字/按钮/符印框）+ 平滑魔法层（`magic_starfield` / `magic_constellation` / `magic_pulse` / SDF 辉光）；含纯像素 vs 混合对比 | `spell_weave_hybrid.png`、`spell_weave_hybrid_unstable.png`、`spell_weave_hybrid_compare.png` |
| `wheel_shader_lab` | `SceneWheelShaderLab.java` | 轮盘三支设计着色器的实验台（扇形状态 / 光环 / 核心，不含完整界面） | `wheel_shader_lab.png`、`wheel_shader_lab_t2.png`、`wheel_shader_lab_t4.png` |
| `sdf_test` | `SceneSdfTest.java` | **SDF 平滑形状自检**（disc / ring / roundRect / capsule line / star / 细环），改 `sdf_ui` 或 `SdfShapes` 后先跑它 | `sdf_test.png` |
| `wand_picker` | `SceneWandPicker.java` | 法杖图标右键展开的外圈法术快选：保持装载方位，排除当前法术，展开/悬停态 | `wand_picker.png` |
| `wand_hud` | `SceneWandHud.java` | 与游戏共用圆环绘制和弹簧状态：细轨道、外置刻度、悬停推挤、高光、扣蓝残影和就绪扩散；`-Time -1` 导出 30 FPS 动效序列 | `wand_hud.png`、`wand_motion/frame_*.png` |
| `source_machines` | `SceneSourceMachines.java` | 源荷滤波器手动 / 懒人预设 / 波段表与集荷器；复用实机波形线带，需先将 `ElementBand.java` 和 `SourceWaveform.java` 编译到 `build/circuit-test` | `source_filter_manual.png`、`source_filter_quick.png`、`source_filter_bands.png`、`source_collector_modern.png` |
| `debug` | `SceneDebug.java` | 框架调试：分阶段画 fill/渐变/文字/贴图/shader，用来定位渲染问题 | `debug_*.png` |

`-All` 目前跑 `smoke / tooltip / collapsar_bar / gui_effects / creative_inventory / difficulty /
spell_wheel / spell_loadout`（`wheel_shader_lab`、`sdf_test` 与 `debug` 是排错用的，不自动跑）。

轮盘/装载界面的完整设计规格见 [`docs/spell-wheel-design.md`](../../docs/spell-wheel-design.md)
（布局坐标表、视觉 token、状态机、数据模型、与实机 Java 的落地映射）。

## 各场景的已知近似（看产物前先知道）

- **tooltip**：面板几何、边框（底板内侧 1px、上亮下暗、左右纵向渐变）、格挡条 7 fill + 2 文字、
  热力 uniform 全部照实机常量；差异是名字用「按字符索引的静态渐变」而不是实机的
  `FlowingNameColors` 流动相位，缺 unifont 符号/斜体、未画原版属性块，预览居中而非鼠标定位。
  长行**不换行** —— 原版也不换行（长 tooltip 会顶出屏幕，需要换行是第三方 mod 的事）。
- **collapsar_bar**：mask/outline 都是真实贴图，`bandMin/Max` 与 `barH` 从贴图实测推导
  （190×60 → 182×57），抖动公式逐字照抄；名字按"框架贴图可见上沿"对齐（贴图顶部有透明
  padding），因为实机名字属于原版 `BossHealthOverlay`，不参与抖动。
- **gui_effects**：`gui_glow` 的 fsh 用 `step(0.03, iconAlpha)` 做 gate，而难度 PNG 是硬边
  0/255 alpha 且四周有透明边距 —— **实机里这层光晕本来就几乎不可见**。预览如实画出来，
  另给一张"绕过 gate"的参考光晕（`gui_glow.png` 的 B 行）验证径向衰减/颜色/居中的几何是对的。
  如果美术预期实机应有明显光晕，需要回头查 gate 或 `IconUVMin/Max`。
- **creative_inventory**：使用正式底板、标签页和铭牌 PNG，与 `CreativeTabDividerRenderer` 相同的固定银色标题；
  按原版 195×136 面板与 9×5 槽位几何绘制，物品排列为展示样例。
- **difficulty**：布局、四档 rings、`bakeRing` 的 Position+UV0 编码、`gui_glow`/`difficulty_sigil`
  的 uniform 全部按源码移植（并用数值采样验证过 pattern 分支）；hint 在标题上方是
  `DifficultySelectScreen` 源码如此；不画鼠标 hover tooltip，背景用 `c.backdrop()` 代替真实世界。
- **spell_wheel / spell_loadout**：这是**设计稿**（对应 docs/spell-wheel-design.md），不是现状复刻。
  数据来自项目真实法术（Fireball / Spear of Judgment / Suppression Field 的耗蓝/CD/
  施法类型/图标），被动示例（Ember Affinity / Void Attunement）是规划占位、界面上有标记。
  轮盘本体用 `tools/gui-preview/shaders/wheel_*` 三支设计着色器；世界背景是程序化夜空山景
  （不是真实世界渲染）；装载界面的法术图鉴/轮盘配置交互是静态模拟（拖拽态、被动只读态）。
  界面里的圆/圆环/扇区/圆角面板/胶囊/星形全部走 `sdf_ui` 的逐物理像素抗锯齿
  （参考 FIS-Cordyceps 的 `wheel_sdf` 方案，见 `FRAMEWORK.md` 与 `shaders/README.md`）。
- **spell_forge**：这是**设计稿**（对应"发现蓝图 → 解析 → 刻录"的获取思路），不是现状复刻。
  蓝图 / 残页物品目前游戏里还不存在，卡面是程序化绘制的占位；法术数值取自实机
  Fireball / Tidal Pulse / Arcane Step 的 `SpellDefinition`；图鉴里的 5 张未知卡是留白占位。
  三张图（就绪 / 刻录中 / 图鉴）用于讨论布局与信息密度，不代表最终交互。
- **spell_forge_styles**：两套原版风格提案共享同一几何。A 全部用 `fill` 画 1px 斜角；
  B 读取 `textures/gui/spell_forge/` 下的程序生成占位 PNG（生成器
  `tools/generate-spell-forge-gui-placeholders.py`）。文字 / 物品图标 / 进度 / 选中态
  没有烘焙进底板。资源清单、切片表和布局锚点见
  [`docs/proposals/spell-forge/README.md`](../../docs/proposals/spell-forge/README.md)；
  尚未接入实机 `Screen`。
- **spell_weave**：自由合成概念稿（把材料清单换成画星座）。符印种类、法术名/数值/效果与燃料
  数量都是占位；星图连线用项目真实 `magic_constellation` / `magic_pulse` 着色器，节点辉光
  走 SDF。未实现图案校验、实机 `Screen` 与"织法法术"的 NBT 身份；设计说明见
  [`docs/proposals/spell-weave/README.md`](../../docs/proposals/spell-weave/README.md)。
- **spell_weave_styles**：织星台的像素 A/B。A 不加载贴图，全部 `fill` 画 1px 像素面板、
  点阵星云、像素符文与 Bresenham 星座线；B 读取 `textures/gui/spell_weave/` 下的
  `weave_bg.png` / `sky.png` / `widgets.png` 占位图，仅覆盖静态外观。坐标 / 交互两套完全一致；
  资源清单与重绘约定见
  [`docs/proposals/spell-weave/README.md`](../../docs/proposals/spell-weave/README.md)。
- **spell_weave_hybrid**：像素 chrome + 平滑魔法层的混合稿。面板 / 文字 / 按钮 / 符印框沿用
  像素绘制；星空、连线、光脉冲、节点辉光、法阵徽记走项目真实 GUI 着色器与 SDF。对比图左侧是
  纯像素版，右侧是混合版，可直观看哪些层该平滑、哪些该保持像素。设计说明同上。
- 通用边界见下一节。

## 它是怎么工作的

```
gui_preview.ps1
 ├─ 从 Gradle 缓存解析 LWJGL 3.3.1 + natives
 ├─ 从 client.jar 抽 MC 位图字体到 build\gui-preview\mc-assets\（不提交原版资源）
 ├─ javac 只编译「框架 + 目标场景」→ build\gui-preview\classes\<scene>
 └─ java GuiPreview <scene> <out> <time> <w> <h> <scale>
      ├─ PreviewGl        隐藏 GLFW 窗口 + GL 3.2 core + 离屏 FBO（逻辑分辨率）
      ├─ PreviewCanvas    GUI 坐标系的 fill/gradient/blit/text/clip/2D 变换/fillMesh + shaderQuad/shaderMesh
      ├─ PreviewShader    读 shader json：attributes / samplers / uniforms / blend
      ├─ PreviewFont      ascii.png + include/default.json 还原 MC 8px 位图字体
      ├─ WheelKit         轮盘设计稿的共享图形与数据（spell_wheel / spell_loadout 共用）
      ├─ shaders/         工具私有的设计着色器（wheel_slice / wheel_aura / wheel_core）
      └─ 导出             FBO 读回 → PNG（原生分辨率，永远不透明）
```

文件职责：

| 文件 | 职责 |
|---|---|
| `GuiPreview.java` | 入口；按场景名反射调用 `Scene<Name>.render(canvas, time)`，结束后导出 `<scene>.png` |
| `PreviewGl.java` | GLFW 隐藏窗口、FBO（物理分辨率）、读回像素、写 PNG（原生渲染，不做后处理放大） |
| `PreviewCanvas.java` | 场景 API：`fill/fillGradientV/strokeRect/blit/text/pushClip/shaderQuad/shaderMesh/save/backdrop` |
| `PreviewShader.java` | 按 json 加载 MC core shader（属性 location、uniform 默认值、sampler 纹理单元、json blend）；先找 `shaders/` 工具私有着色器再回落项目 assets |
| `PreviewTexture.java` | PNG → GL 贴图（最近邻，GUI 像素艺术） |
| `PreviewFont.java` | MC 位图字体：按 `ascii.png` 像素右边界算字宽，行高 9 |
| `MiniJson.java` | 无依赖 JSON 解析（工具 classpath 只有 LWJGL） |
| `SdfShapes.java` | **SDF 平滑形状**：圆/环/扇区/圆角矩形/胶囊线/星形，逐物理像素覆盖率抗锯齿（`sdf_ui` 着色器 + `uScale` 对齐 GUI 缩放），供 WheelKit 与各场景降级复用 |
| `WheelKit.java` | 魔法轮盘的共享渲染器与数据模型（Spell 数据、8 方位几何、扇区/光环/核心、法术卡片/详情、法力条），`spell_wheel` 与 `spell_loadout` 共用 |
| `shaders/` | **工具私有着色器**（设计稿专用，不进游戏 assets）：`wheel_slice`（花瓣扇区/冷却/锁定）、`wheel_aura`（外圈光环/符文环/光束）、`wheel_core`（中心法力核心）、`sdf_ui`（圆/环/扇区/圆角矩形/胶囊/星形的逐像素 AA） |
| `FRAMEWORK.md` | 写新场景必读的 API 契约与踩坑说明 |

### 与实机的对应关系

- **坐标**：`PreviewCanvas` 的坐标就是 `GuiGraphics` 的 GUI 缩放坐标（左上原点、y 向下）。
  默认 640×400 = 1280×800 @ GUI scale 2，所以 182×32 的血条、行高 9 的文字、
  tooltip 的 `PADDING=4` 都可以直接照抄源码常量。
- **色带 / 底板 / 血条这类"立即绘制"**：`shaderQuad` / `shaderMesh` 对应游戏里
  `BufferUploader.drawWithShader` 的路径 —— 顶点是 GUI 坐标，`ProjMat` 由画布给正交矩阵，
  `ModelViewMat` = 单位阵，和 `RenderSystem` 在 GUI 阶段给的一致。
- **混合**：读 json 的 `"blend"`（普通 alpha / 加色），与 MC 的 `ShaderInstance.apply()`
  行为一致 —— 也就是说 json 里的 blend **赢过**调用前的手动 RenderSystem 设置。
- **uniform 默认值**：json 里声明的默认值会先写一遍（和 `ShaderInstance` 构造时一样），
  场景没覆盖的 uniform 不会"恰好是 0"；反过来，json 没声明的 uniform 写了也不生效。
- **字体**：从 `client.jar` 抽 `ascii.png` + `include/default.json`，按位图 provider 的规则
  还原字形与字宽；**只覆盖拉丁/符号**，中日韩会画成占位方块 —— 场景里的文字一律用
  `en_us.json` 的英文。

## 能力边界（它**不**验证什么）

- `GuiGraphics` 的 `PoseStack` 平移/缩放、批处理顺序、`flush` 时机
- `RenderType` / `StateShard` / 深度缓冲 / `depthMask` 等 GL 状态簿记
- 光影包（Oculus/Iris）上下文、原版 GUI scale 自适应、窗口 resize
- 场景里没有真实世界/实体/粒子——背景是程序化深色星空（`c.backdrop()`），只用来判断对比度

结论：**画面/布局/配色能提前看到，Java 侧状态顺序仍要进游戏验证**。

## 加一个新场景

1. 读 [`FRAMEWORK.md`](FRAMEWORK.md)。
2. 新建 `tools/gui-preview/SceneFoo.java`（默认包，类名 = `Scene` + 驼峰场景名）：

   ```java
   public final class SceneFoo {
       public static void render(PreviewCanvas c, float time) {
           c.backdrop();
           c.text("Hello", 10, 10, 0xFFFFFFFF, true);
           c.save("foo_extra");   // 可选：多存几帧
       }
   }
   ```

3. `cmd /c "tools\gui-preview\gui_preview.cmd -Scene foo -Time 2.0"` → `build\gui-preview\foo.png`。
4. 想进 `-All`：把场景名加进 `gui_preview.ps1` 的 `$scenes` 列表。

## 相关文件

- 框架 API：[`FRAMEWORK.md`](FRAMEWORK.md)
- 场景源码：本目录 `Scene*.java`
- 项目着色器：`src/main/resources/assets/rotten_ruins/shaders/core/`
- GUI 贴图：`src/main/resources/assets/rotten_ruins/textures/`
- 实机常量来源：`src/main/java/org/bytechen/rottenruins/client/`（tooltip / rend/gui / gui/creative / gui/DifficultySelectScreen…）
- 英文文案：`src/generated/resources/assets/rotten_ruins/lang/en_us.json`
- 相邻工具：`tools/shader-preview/`（着色器算法预览）、`docs/`（各功能的设计与踩坑记录）
