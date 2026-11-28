# 法术合成界面 · 三风格重绘（预览工具规格）

本文件是 `tools/gui-preview` 三个新场景的**共用施工图**。三个场景各自一个
`SceneSpellAltar<Style>.java`，**只换视觉语言，不换功能与命中框**。

- 目标界面 = 实机 `client/gui/PotentialCraftingScreen.java`（奥秘仪台 / Arcane Instrument Altar）
- 产物 = `build/gui-preview/<scene>.png`（就绪态）与 `<scene>_crafting.png`（合成中）
- 文字一律英文：预览工具只带 MC 拉丁位图字体

---

## 0. 渲染参数（三个场景必须一致）

```powershell
cmd /c "tools\gui-preview\gui_preview.cmd -Scene <scene> -Time 2.4 -Width 240 -Height 320 -Scale 3"
```

- 逻辑画布 **240×320**，`-Scale 3` → PNG **720×960**（= GUI scale 3 的观感）
- 面板 176×256 贴左下角原点 `PX=32, PY=32`：所有实机坐标都是**面板局部坐标**，
  画的时候统一 `PX + x` / `PY + y`
- 不要手写 `c.scale(2)`：`-Scale 3` 已经是原生物理倍率，再乘一次会把字体放大到糊

---

## 1. 不可改的几何（来自 Menu / 命中框，改了就对不上实机）

| 元素 | 数值（面板局部） | 来源 |
|---|---|---|
| 面板 | 176×256 | `PotentialCraftingMenu.IMAGE_WIDTH/HEIGHT` |
| 圆环中心 | (96, 84) | `RING_CENTER_X/Y` |
| 15 个材料槽 | 18×18，半径 48，角度 `-90 + i·24°`，槽左上 = 圆心 + (cos,sin)·48 − 8 | `ringAngleDegrees` / `RING_RADIUS` |
| 圆心产物槽 | 18×18，左上 (88, 76) | 输出槽 |
| 按钮 | `(58, 146, 60, 18)` | `BUTTON_X/Y/WIDTH/HEIGHT` |
| 玩家背包 | 3×9，槽 18×18，起点 (8, 182)，行距 18 | `PotentialCraftingMenu` 构造 |
| 快捷栏 | 9 格，起点 (8, 236) | 同上 |
| 背包标题 | 基线 y=172 那一行 | `inventoryLabelY` |

**可以重新设计**的区域：面板外观（边框/材质/圆角）、头盔区（标题、读数）、
圆环的装饰（刻度、法阵、连线）、进度条（实机在面板左外侧 `(-22,36,16,96)`）、
势能格（实机在面板右外侧 `(182,25)`，15 格 × 16×6、间隔 2）、按钮外观、
状态行位置（实机居中 y=168）。但**功能分区要能被认出来**：机器（环）+ 读数（势能/进度）+ 操作（按钮）+
背包。进度条与势能格建议收进面板里（实机把它们挂在面板外，是三块硬拼在一起的来源）。

## 2. 画面内容（三场景完全一致，保证可比）

- 标题：`ARCANE INSTRUMENT ALTAR`（可 1x，也可拆成小字标 + 2x 主标题）
- 状态：就绪态写 `PLACE INGREDIENTS TO BEGIN`？不 —— 用**可合成**状态：
  状态行 `READY TO CRAFT`，按钮 `CRAFT`（禁用态写法见下）
- 势能读数：`POTENTIAL 9 / 15`（15 格里有 9 格亮）
- 进度：就绪态 0%；`_crafting` 帧 62%，状态行 `CRAFTING… · 4 s LEFT`，按钮 `CRAFTING…`（禁用外观）
- 圆心产物：`textures/item/fire_ball.png`（16×16，**绝对不要 2 倍拉伸**，
  要放大就整 2 倍 = 32×32）
- 材料：15 格里放 11 个，用现有物品贴图当占位材料（路径相对
  `src/main/resources/assets/rotten_ruins/`）：
  `textures/item/fire_mana_plate.png`、`textures/item/mana_bottle.png`、
  `textures/item/domite_crystal.png`、`textures/item/void_shard.png`、
  `textures/item/source_charge_sand.png`、`textures/item/domerite_ingot.png`、
  `textures/item/chip_null.png`、`textures/item/chip_scan.png`、
  `textures/item/chitin_polymer.png`、`textures/item/hall_bone_fragments.png`、
  `textures/item/true_copper.png`
  从第 0 号槽（正上方）顺时针填 11 个，空槽画空态（不要写 `?` 大字，用小字
  `EMPTY` 或纯空框即可；空与满要在 1 眼内分得出来）
- 背包格：可画成空槽；3~4 格放占位物品让画面不空（`mana_bottle` / `venom`类 都可）

## 3. 可用的东西

**项目真实着色器**（`c.shader(name)`）：`magic_starfield`（深空星场，uniform
`uTime uSize uMouse uIntensity`）、`magic_pulse`（沿线段跑一颗光头，`uProgress
uColor uIntensity`，配 `shaderMesh` 画梯形带，见 `SceneSpellSynth#ribbon`）、
`magic_constellation`、`rendertype_gui_glow`（叠加发光）。

**工具私有着色器**（`tools/gui-preview/shaders/`）：`sdf_ui`（**全部圆/环/扇/圆角/胶囊
线都走它**，`SdfShapes.disc/ring/wedge/roundRect/line/star`）、`wheel_aura`、
`wheel_core`、`wheel_slice`、`gui_tab_divider`。

**贴图**：`textures/gui/gold_slot.png`（26×26 金框槽）、`textures/gui/s_ui.png`（16×64 竖条框）、
`textures/item/*.png`。可以用 `c.image(path)` 读像素做程序化纹理（例如从 `gold_slot` 采样）。

## 4. 画法约定

- 1px 结构线（边框、刻度、分隔）用 `c.fill`：硬边，缩放时保持像素感
- 一切**曲线**用 `SdfShapes`：逐物理像素覆盖率，任意 GUI scale 边缘都干净
- 文字：`c.text / textCentered / textRight / textScaled / textCenteredScaled`；
  行高 9；小字用 1x，主标题最多 2x
- 颜色一律 ARGB int（`0xFFRRGGBB`）。**别用纯黑纯白**，一律带一点色相
- 背景：`c.clear(...)` 后铺项目真实 `magic_starfield`（或本风格自己的底），再加四边暗角；
  面板压在背景上要有清晰的分层（面板不透明度 ≥ 0xE0）
- 网格对齐：图标按 16×16 落在整数坐标上，不要半像素

## 5. 交付

- 每个场景 `render` 里：先画**就绪态** → `c.save("<scene>_ready")`；再画**合成中** →
  `c.save("<scene>_crafting")`；最后再画一次就绪态（`GuiPreview` 会在 `render`
  返回后自动把当前帧存成 `<scene>.png`）
- 场景文件里写清"为什么这样排"的类注释（本项目文档习惯），中文注释可以，
  **画面内文字只能英文**
- 三个场景的输出必须能一眼看出是同一个界面的三种风格：位置/内容一致，材质与光语言不同
