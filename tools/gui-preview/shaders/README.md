# 轮盘设计稿的三支工具私有着色器

这里放的是**设计稿专用**的着色器，只有 `tools/gui-preview` 会加载它们
（`PreviewCanvas.shader(name)` 先找 `tools/gui-preview/shaders/<name>.json`，找不到才回落
`src/main/resources/assets/rotten_ruins/shaders/core/`）。它们**不是**项目资源，
没有进 `assets/`，游戏里也不会被引用 —— 等视觉定稿后再决定要不要正式移植。

| 文件 | 作用 | json blend | 典型调用方 |
|---|---|---|---|
| `wheel_slice.{json,vsh,fsh}` | 轮盘的一个扇区（玻璃面板 + 状态灯 + 冷却压暗） | `srcalpha / 1-srcalpha`（普通透明） | 每个扇区一次 draw call |
| `wheel_aura.{json,vsh,fsh}` | 轮盘背后的环境光环（柔光/光束/符文环/辐条/选中锥光） | `src_alpha / one`（加色） | 每帧一次，画在扇区之前 |
| `wheel_core.{json,vsh,fsh}` | 中心核心（漩涡光球 + 法力弧 + 冷却弧 + 蓄力脉冲） | `src_alpha / one`（加色） | 每帧一次，画在暗盘与文字之下/之间 |

实验场景：`SceneWheelShaderLab.java`（场景名 `wheel_shader_lab`，8 扇区一次演示全部状态）。
正式集成见 `WheelKit.java` / `SceneSpellWheel.java`。

---

## 0. 三支 shader 共用的约定（**改之前先读**）

### 坐标还原

三支 shader 都只吃两个 attributes：`["Position", "UV0"]`。
场景把**一个覆盖轮盘外包框的 quad** 按 `c.shaderQuad(...)` 画出来，UV0 是 0..1，
片元里自己还原 GUI 像素坐标：

```glsl
vec2 px = uQuadOrigin + vUv * uQuadSize;   // GUI 像素，和 GuiGraphics 同一层
```

所以 `uQuadOrigin` / `uQuadSize` **必须和 `c.shaderQuad` 的实参完全一致**
（框架现在有 float 重载，别传 `(int)` 截断后的值，否则边界会偏几个像素）。

### 角度

屏幕坐标 y 向下，`atan2(rel.y, rel.x)`：

- `0` = 右、`+PI/2` = 下、`-PI/2` = 上；
- **角度增大 = 顺时针**；
- 扇区 `i` 的 `uStartAngle = -PI/2 + i*TAU/8`、`uEndAngle = uStartAngle + TAU/8`
  （场景可以再各收 1.2° 留缝）。

### 边界安全：任何加色项在 quad 边缘必须是 0

pad 只比轮盘大一圈，如果发光在 quad 直边处还有非零值，夜空背景上就会露出一个
**发光的矩形边**（这是设计稿 review 里最容易漏的 bug）。因此三支 shader 都有：

```glsl
float border = min(min(px - uQuadOrigin, uQuadOrigin + uQuadSize - px).x,
                   min(px - uQuadOrigin, uQuadOrigin + uQuadSize - px).y);
```

- `wheel_slice`：`edgeFade = smoothstep(0.0, 18.0, border)`，乘到**所有加法项**
  （内外缘亮线 / tick / hover-selected 呼吸光 / 冷却扫边 / 外发光）上；
  扇区本体（body）不受影响，只额外乘最后 2px 的保险衰减。
- `wheel_aura`：`edgeFade = smoothstep(0.0, 15.0, border)`，最后乘到整个 radiance 上；
  另外 halo 的 smoothstep 在 `uOuter*1.30` 就收到 0。
- `wheel_core`：`edgeFade = smoothstep(0.0, 4.0, border)`（核心最外的冷却弧离 quad 边只有十几 px）。

**推论：quad 不能贴太紧。** 建议：

| shader | quad 边长 | 说明 |
|---|---|---|
| `wheel_slice` | ≥ `2.4 * uRadius`（正式场景 2.7R） | 给 hover/selected 的 +6.5px 外扩和光晕留空间 |
| `wheel_aura` | ≥ `2.8 * uOuter`（正式场景 3.1R） | halo 在 1.30×uOuter 处已经衰减完 |
| `wheel_core` | `2.6 *`（场景内部 coreRadius） | 半径相对 `uCoreRadius` 算，见下 |

### 其它

- `ProjMat` / `ModelViewMat` 由 canvas 自动填；json 里必须声明（否则不生效）。
- 场景用 `shader.set(name, ...)` 前不用自己 `glUseProgram`（`PreviewShader.set` 内部会切）。
- **json 里没声明的 uniform 写了也静默忽略** —— 加新参数时必须同时改 json。
- 加色 shader 输出 `alpha = 1.0`，亮度全部编码在 rgb 里（`src_alpha / one` 时
  等价于直接把 rgb 加到帧上）；`uIntensity = 0` 时输出 `vec4(0)`。

---

## 1. wheel_slice —— 一个扇区

### uniforms

| 名字 | 类型 | 含义 |
|---|---|---|
| `uCenter` | vec2 | 轮盘中心（GUI 像素） |
| `uQuadOrigin` | vec2 | quad 左上角 |
| `uQuadSize` | vec2 | quad 尺寸 |
| `uRadius` | float | 扇区外径 |
| `uInnerRadius` | float | 扇区内径（中心的洞） |
| `uStartAngle` | float | 扇区起始角（弧度，屏幕 atan2 约定） |
| `uEndAngle` | float | 扇区结束角（rad，必须 > start） |
| `uColorA` | vec4 | 亮缘 / 外沿色（学院色） |
| `uColorB` | vec4 | 深底 / 内部色（场景里一般传 `uColorA * 0.22` 左右） |
| `uTime` | float | 秒 |
| `uHover` | float | 0..1 悬浮 |
| `uSelected` | float | 0..1 选中 |
| `uLocked` | float | 0..1 蓝不足/锁定 |
| `uCooldown` | float | 0 = 就绪，1 = 整扇区冷却中 |
| `uIntensity` | float | 总体强度；**0 = 完全透明**（空位传很低的值） |

### 视觉

- 扇区本体：`uColorB` → `uColorA` 的径向渐变（内部深、外沿亮），内缘有暗影，
  外缘/内缘各一条 1px 左右的亮弧线；
- **角度两侧 1.25px 缝 + 1.45px 羽化**（按弧长算，所以内外径处的缝隙宽度一致）；
- hover / selected：外径外扩 `max(hover*5, selected*6.5)` px（4..7px）、
  呼吸亮度、外缘辉光（selected 更强）；两者同时为 1 时按 selected 算（不会叠加成 11px）；
- **uCooldown**：从 `uStartAngle` 起顺时针，`(ang-start)/(end-start) < uCooldown` 的区域
  压暗 93% 并偏冷；分割线是一条亮扫边（1.25px 宽的高斯），冷却区内缘有 7 段小刻度；
- **uLocked**：对最终颜色去饱和（luminance）并往冷蓝 `(0.34,0.47,0.78)` 混合 85%，
  再整体压暗；
- 质感：像素级 hash 噪声 + 44 条极淡角向纹理 + 内缘 tick，避免大色块死板；
- 角缝用 `arcStart = d*r`（弧长像素）而不是纯角度，保证内圈外圈 gap 观感一致。

### 调参提示

- 想让扇区更"玻璃"：把 `body * baseAlpha` 里的 `0.72`、`0.88` 调低，让后面的 aura 透出来；
- 想让冷却更狠：改 `cooled`（现在是 `mix(col*0.16, coldTint*0.055, 0.3)`）和 `cdMask*0.94`；
- 想让 hover 更明显：调 `state = hover*0.75 + selected` 和 `lineBoost`/呼吸项。

---

## 2. wheel_aura —— 轮盘背后的环境光

### uniforms

| 名字 | 类型 | 含义 |
|---|---|---|
| `uCenter` / `uQuadOrigin` / `uQuadSize` | vec2 | 同上 |
| `uOuter` | float | 轮盘外径（halo 以它为单位） |
| `uInner` | float | 轮盘内径 |
| `uTime` | float | 秒 |
| `uHoverAngle` | float | 高亮方向的中心角（rad，屏幕 atan2 约定） |
| `uHoverStrength` | float | 0..1；0 = 不画锥形光 |
| `uIntensity` | float | 0 → 不画任何东西 |
| `uColorA` | vec4 | 金（主 chrome 色） |
| `uColorB` | vec4 | 青（辅助） |
| `uColorC` | vec4 | 紫（外圈雾） |

### 视觉分层（从后到前）

1. **大范围呼吸柔光**：`uOuter` 附近最亮，向外按 `exp(-d/(0.15*uOuter))` 衰减，
   在 `1.30*uOuter` 处被 smoothstep 收到 0；
2. **24 条极慢旋转光束**（`cos(24θ ± t)` 的 7 次方）+ 6 条更宽更慢的光束；
3. **外圈符文环**：`uOuter+4.5` 一圈细线 + 96 格刻度、每 6 格加粗（长短交替），
   整体 `0.055 rad/s` 缓慢旋转；每个 tick 亮度按 slot hash 抖动，少数几格偏青；
4. **8 根辐条 / 八芒星暗示**（`|cos(4θ)|^30`）+ 8 颗运行的小光珠；
5. **hover 锥形光**：`±14°`（0.245 rad），角向 smoothstep + 径向
   `[uInner*0.85, uOuter*1.14]` 双重裁剪，附带一条中心发丝线与向外推的光脉冲；
6. 极轻的 fbm 噪声压一下渐变色带。

整支 shader 最后乘 `edgeFade` 和 `uIntensity`，所以在任何 quad 尺寸下都不会露出矩形边。

### 调参提示

- 太亮/太糊：先降 `bloom * 0.150` 和 `rim` 两项；正式场景 `uIntensity ≈ 1` 时
  夜空上应该只是"看见一圈光晕"，不该改变山脊轮廓；
- 想更"贵气"：提高 rune ring（`mix(0.070,0.150,isLong)`）和 `uColorA` 的权重，
  减少 `uColorC` 紫雾；
- hover 锥光想更明显：调 `0.100 / 0.085 / 0.055` 三个系数，但别超过 0.15，否则会抢扇区。

---

## 3. wheel_core —— 中心核心

### uniforms

| 名字 | 类型 | 含义 |
|---|---|---|
| `uCenter` / `uQuadOrigin` / `uQuadSize` | vec2 | 同上 |
| `uCoreRadius` | float | 光球半径（正式场景传 `(inner-4)*0.92 ≈ 49.7`） |
| `uTime` | float | 秒 |
| `uMana` | float | 0..1，从 12 点方向顺时针填充法力弧 |
| `uCharge` | float | 0..1，蓄力时脉冲环半径增大、变淡，中心变亮 |
| `uCooldown` | float | 0..1，从 12 点方向逆时针填充冷却弧（冷色） |
| `uColorA` | vec4 | **流派主色**（光球外圈、漩涡丝的底色） |
| `uColorB` | vec4 | **金色 chrome**（中心热点、法力弧、脉冲环） |
| `uIntensity` | float | 0 → 不画 |

> 颜色约定和 `WheelKit` 的调用一致：A = 选中法术的流派色，B = `GOLD_BRIGHT_RGBA`。
> 光球用**加色分层**而不是把两种颜色 mix，紫色流派 + 金色中心才不会糊成棕色。

### 视觉分层

- **漩涡光球**：`exp(-(r/uCoreRadius * 2.6)^2)` 的小热点 + 大一圈的流派色光体 +
  3 层旋转 `sin` 亮丝（fbm 调制强度）；`orbShape` 在 `uCoreRadius` 附近收暗；
- **法力弧**：`r = uCoreRadius*0.96 + 0.5`（贴着光球边缘），从 12 点顺时针填充
  `uMana`，细（σ≈0.95px）+ 淡 track + 头部一颗小亮点；
- **冷却弧**：`r = 法力弧 + max(3, uCoreRadius*0.055)`，从 12 点逆时针填充
  `uCooldown`，固定冰蓝（不跟流派色混），更细更暗；
- **蓄力**：`uCharge` 越大，脉冲环半径 `0.40→1.25 × uCoreRadius` 扩散、透明度
  `1-0.85*charge` 变淡，中心热斑增亮 ~16%；
- 整个核心再乘 `edgeFade = smoothstep(0, 4px, border)`。

### 场景怎么用（重要）

`wheel_core` 是**加色层**，中心文字不归它管。推荐顺序（`WheelKit.drawCore` 就是这么做的）：

1. 先画暗色圆盘（`fillDisc`，半径 ≈ `uCoreRadius + 7`）—— 给光球一个暗底；
2. 画 `wheel_core`；
3. 在光球中心再压一层 88% 大小的暗盘（alpha ≈ 96/255），保证名字/状态行可读；
4. 最后画文字。

### 调参提示

- 光球太亮盖文字：降 `bodyGlow * 0.80`、`radial * 0.46` 两项；
- 光球太暗/发灰：先确认场景的暗盘 alpha，再调 `bodyGlow` 的 `0.24 + 0.62`；
- 法力/冷却弧存在感：`0.45`（mana）/ `0.36`（cooldown）是"装饰级"亮度，
  再往上就会变成主角；半径都按 `uCoreRadius` 的倍数算，换半径不用重调。

---

## 4. 验证

```powershell
# 8 状态实验台（主图 + 两帧时间对照）
cmd /c "tools\gui-preview\gui_preview.cmd -Scene wheel_shader_lab -Time 3.2"
# 正式场景（建议独立 OutDir，避免和并行场景抢文件）
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_wheel -Time 3.4 -OutDir build\gui-preview\shader-check"
```

产物（`build/gui-preview/`）：

- `wheel_shader_lab.png`：8 扇区 = 普通 FIRE / hover / selected / 冷却 60% / locked /
  普通 DEATH / 普通 VOID / 空位，中心 core + aura；
- `wheel_shader_lab_t2.png`、`wheel_shader_lab_t4.png`：时间 +2.35s / +4.90s，
  用来确认符文环旋转、光球亮丝、光脉冲在动；
- `spell_wheel*.png`：正式 HUD 的四帧（main / idle / charge / cooldown）。

### 已知限制

- 这三支 shader 只在离屏预览里验证过，**没有进 assets、也没进游戏**；正式移植要连同
  json 的 `blend` 语义一起搬，并确认实机的 `ProjMat`/`ModelViewMat` 与这里一致；
- 加色 shader 的亮度是"加到帧上"的，8-bit FBO 会在高亮处直接 clip；调色时以
  实验场景的深色背景为准，正式场景背景更亮时观感会略有不同；
- `edgeFade` 用 quad 边界做保险，所以 **quad 尺寸是契约的一部分**（见表），
  场景如果把 quad 收小，光晕会被提前淡出（这是有意的安全行为）。

---

## 5. sdf_ui —— 逐物理像素抗锯齿的 UI 形状

三角光栅化拼出来的圆/圆环/圆角矩形没有覆盖率 AA；GUI 缩放 2~4 时边缘就是
「1 GUI 像素的混合」被放大成 2~4 个物理像素的台阶。`sdf_ui` 在片元里求形状的
有向距离，再把 `coverage = clamp(0.5 - sdf/aa, 0, 1)` 作为 alpha —— 任何缩放下
边缘都只有 **1 个物理像素**的过渡。思路参考 FIS-Cordyceps 项目（MIT）的
`wheel_sdf.fsh`（同作者的另一个 Forge 项目）。

### uniforms

| 名字 | 类型 | 含义 |
|---|---|---|
| `uCanvas` | vec2 | 当前 quad 的 GUI 尺寸（形状坐标相对 quad 左上） |
| `uCenter` | vec2 | 形状中心（quad 本地 GUI 坐标） |
| `uHalfSize` | vec2 | 圆角矩形半宽半高 |
| `uP0` / `uP1` | vec2 | 胶囊线段起终点（quad 本地） |
| `uRIn` / `uROut` | float | 圆环/扇区内径外径、星形内外半径 |
| `uA0` / `uA1` | float | 扇区起止角（rad）；`a1-a0 >= TAU` 表示整圈 |
| `uRound` | float | 圆角矩形圆角半径 |
| `uBorder` | float | 圆角矩形内描边宽度（0 = 无） |
| `uThickness` | float | 胶囊半宽 |
| `uPoints` / `uRot` | float | 星形尖数 / 旋转（rad） |
| `uScale` | float | **1 个 GUI 像素 = 多少物理像素**（AA 宽度 = 1/uScale） |
| `uGradMode` | float | 0=径向渐变，1=竖直渐变，2=纯 `uColorIn` |
| `uMode` | float | 0=环/扇区，1=圆角矩形，2=胶囊，3=星形 |
| `uSoft` | float | 额外柔化（GUI 像素，0 = 锐利；做辉光形状时可给一点） |
| `uColorIn` / `uColorOut` | vec4 | 渐变两端颜色（含 alpha） |
| `uBorderColor` | vec4 | 描边颜色 |

### 使用方式

Java 侧用 [`SdfShapes`](../SdfShapes.java) 封装，**不要直接拼 quad**：

```java
SdfShapes.disc(c, cx, cy, r, top, bottom);
SdfShapes.ring(c, cx, cy, rIn, rOut, inner, outer, /*radial=*/true);
SdfShapes.wedge(c, cx, cy, rIn, rOut, a0, a1, inner, outer);
SdfShapes.roundRect(c, x, y, w, h, radius, top, bottom, borderColor, 1F);
SdfShapes.line(c, x1, y1, x2, y2, halfWidth, argb);
SdfShapes.star(c, cx, cy, rOut, rIn, points, rotation, argb);
```

- `uScale` 在预览里自动取 `PreviewCanvas.pixelScale()`（原生渲染倍率）；
  **实机**接线时传 `Minecraft.getInstance().getWindow().getGuiScale()` 即可；
- 每个形状画一个覆盖包围盒的 quad（周围留 2 GUI px 给 AA），uniform 全量复位，
  返回 `false` 表示 shader 不可用、调用方降级到旧的三角/填充绘制；
- 星形用「2n 条边的精确距离 + 奇偶规则」，尖角不会被极坐标插值磨圆。

### 验证

```powershell
cmd /c "tools\gui-preview\gui_preview.cmd -Scene sdf_test -Time 0"
# → build/gui-preview/sdf_test.png：disc / ring / roundRect / capsule / star / 细环
```
