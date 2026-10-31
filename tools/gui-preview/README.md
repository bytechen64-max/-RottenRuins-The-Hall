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
cmd /c "tools\gui-preview\gui_preview.cmd -Scene tab_divider -Time 2.0"      # 创造栏隔断
cmd /c "tools\gui-preview\gui_preview.cmd -Scene difficulty -Time 2.0"       # 难度选择界面
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_wheel -Time 3.4"      # 魔法轮盘施法 HUD（设计稿）
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_loadout -Time 2.0"    # 魔法装载界面（设计稿）
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
| `tab_divider` | `SceneTabDivider.java` | 创造物品栏分区隔断：`gui_tab_divider` 色带 + 逐字流动彩字标题 | `tab_divider.png`、`tab_divider_flow.png` |
| `difficulty` | `SceneDifficulty.java` | 难度选择界面：卡片 + 柔光 + 程序化法阵（`difficulty_sigil`，UV 编码逐字移植）+ 图标 + 文字 | `difficulty_screen.png`、`difficulty_sigils.png`、`difficulty.png` |
| `spell_wheel` | `SceneSpellWheel.java` | **魔法轮盘施法 HUD（设计稿）**：8 方位花瓣轮盘、只显示已绑定主动法术、中心核心/冷却环/蓄力环、法力条、夜空山景模拟 | `spell_wheel.png`、`spell_wheel_idle.png`、`spell_wheel_charge.png`、`spell_wheel_cooldown.png`、`spell_wheel_empty.png` |
| `spell_loadout` | `SceneSpellLoadout.java` | **魔法装载界面（设计稿）**：左侧法术图鉴（ACTIVE/PASSIVE 页签）、中间轮盘配置预览、右侧详情、拖拽绑定态、被动只读态 | `spell_loadout.png`、`spell_loadout_drag.png`、`spell_loadout_passive.png` |
| `wheel_shader_lab` | `SceneWheelShaderLab.java` | 轮盘三支设计着色器的实验台（扇形状态 / 光环 / 核心，不含完整界面） | `wheel_shader_lab.png`、`wheel_shader_lab_t2.png`、`wheel_shader_lab_t4.png` |
| `sdf_test` | `SceneSdfTest.java` | **SDF 平滑形状自检**（disc / ring / roundRect / capsule line / star / 细环），改 `sdf_ui` 或 `SdfShapes` 后先跑它 | `sdf_test.png` |
| `debug` | `SceneDebug.java` | 框架调试：分阶段画 fill/渐变/文字/贴图/shader，用来定位渲染问题 | `debug_*.png` |

`-All` 目前跑 `smoke / tooltip / collapsar_bar / gui_effects / tab_divider / difficulty /
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
- **tab_divider**：色带 shader、逐字 HSV、标题文案都照 `CreativeTabDividerRenderer`；
  物品栏面板是程序化深色面板 + 18px 槽位井近似，不提取原版 `tab_items.png`。
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
