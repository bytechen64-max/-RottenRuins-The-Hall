# 描边着色器离屏预览 / 编译检查

**不启动游戏**，直接把 `outline_mask` + `outline_ring` 跑一遍并导出 PNG。
用来做两件事：

1. **编译检查** —— GLSL 出错时 `SplendidingShaders` 会把异常吞进日志，
   结果是整条描边静默消失（"就是没有描边"）。这里在几秒内就能确认着色器是好的。
2. **看图案** —— 新颜色模式好不好看，只有渲染出来才知道。

## 用法

在仓库根目录：

```powershell
.\tools\shader-preview\preview.ps1                  # 火焰预设，导出 mask.png / ring.png
.\tools\shader-preview\preview.ps1 -ColorMode 1     # 双色滚动
.\tools\shader-preview\preview.ps1 -Time 9          # 换一帧看动画
.\tools\shader-preview\preview.ps1 -CompileOnly     # 只检查能不能编译 + 链接

# fire_2（ColorMode 5）常用：真实贴图 + 游戏里的环宽 + 火焰光环开关
.\tools\shader-preview\preview.ps1 -ColorMode 5 -Time 5 -Width 3 -Fire 1 `
    -ItemTexture src\main\resources\assets\hall\textures\item\fire_mana_plate.png
.\tools\shader-preview\preview.ps1 -ColorMode 5 -Time 5 -Width 3 -Fire 0   # 对照：关掉火焰光环
```

参数：`-ColorMode`（0 纯色 / 1 双色 / 2 彩虹 / 3 色板 / 4 fire / 5 fire_2）、`-Time`、
`-FreqX`、`-Floor`、`-Width`（描边宽度，默认 10 便于看图案）、`-Fill`（`FillStrength`，
0 = 只画外环，1 = 把遮罩颜色铺回剪影内部；fire_2 默认 1）、`-ItemTexture`（用真实贴图当
`Sampler0`，同时算出它的不透明包围盒并传给 `ItemOpaque`）。

产物默认在 `build\shader-preview\`：

| 文件 | 内容 |
|---|---|
| `mask.png` | 剪影遮罩本身（颜色模式的实际输出） |
| `ring.png` | 环形合成的结果（硬环 + 光雾 + 火焰光环） |
| `ring_hard.png` | 只有硬环（`HaloStrength = 0`、`FireStrength = 0`）的对照图 |
| `fire_spread.png` | 火焰光环的「离轮廓多远」密度场（1/4 分辨率） |

> 本次扩展：光雾模糊链（`outline_halo_blur` 四趟）、火焰光环密度场、按游戏里的加法混合合成、
> 真实物品贴图（`Sampler0` + `ItemOpaque`）、可调环宽与 `FireStrength`。以前它是"不混合直接
> 写颜色"，alpha 低的像素会被看图软件当成透明，容易误判成白色。

## 体积火焰预览（ColorMode 9）

体积火焰（`fire_volume`）不经过遮罩/合成链，单独一档：

```powershell
.\tools\shader-preview\preview.ps1 -ColorMode 9 -Time 3 -Fire 1
```

它用一组**假的相机 / 盒子 / 物品**参数跑 raymarch，导出 `build\shader-preview\volume.png`：
验证 ray-box 求交、视线反解、世界空间噪声与 Beer 累积。**不验证**深度截断与遮挡（这里没有场景深度，
`UseDepth=0`），那两条要靠游戏内实测。

## 它模拟了什么、没模拟什么

**模拟了**：着色器源码原样、`outline_mask` → `outline_ring` 两趟、遮罩贴图的
`LINEAR` 过滤、`OutlineWidth` / `Opacity` / `AlphaTest` 这些 uniform 的真实语义。
画面中央那个方块就是"物品剪影"，四周的带子就是描边。

**没模拟**：真实物品的几何（这里是一个 `[0,1]²` 的方块）、MC 的 RenderType 状态
（深度测试、混合）、GUI/光影包上下文。所以它验证的是**颜色与几何算法**，不是最终观感。

`ShaderPreview.java` 里硬编码的 `MaskColor` / `SecondaryColor` 是火焰预设的配色，
试别的配色改那两个 `vec4(...)` 调用即可。

## 前置条件

- JDK 17（`javac` / `java` 在 PATH 上；`javac` 必须带 `-encoding UTF-8` —— 脚本已经带了，
  否则这台机器默认 GBK 会把中文注释当非法字符）
- 至少跑过一次 `.\gradlew.bat build`，好让 LWJGL 落进 Gradle 缓存
- 能创建 OpenGL 上下文（脚本开的是隐藏窗口，实测 NVIDIA 驱动下正常）

## 相关文件

- `ShaderPreview.java` —— 建 FBO、画两趟、读回像素存 PNG
- `ShaderCompileTest.java` —— 只编译/链接任意一对 `.vsh`/`.fsh` 并打印日志

---

## AuraLab：体积光雾的三条路线对比

```powershell
.\tools\shader-preview\aura.cmd          # 出 build\aura-lab\{baseline,route2,route3}.png
```

`AuraLab.java` 会程序化画一把剑当剪影（Java2D，抗锯齿，带一个内圈"符文"环），
然后同一张遮罩分别喂给三条路线，各导一张图：

| 图 | 路线 | 做法 |
|---|---|---|
| `baseline.png` | 现役 | `outline_ring.fsh`：`max(48 次采样)` → 等宽硬环 |
| `route2.png` | 单 pass 软光晕 | `lab/lab_ring_halo.fsh`：加权径向核 + **采样半径被 fBm 扭曲** |
| `route3.png` | 降采样模糊链 | `lab/lab_blur.fsh` ×4（1/2、1/4 各两遍）+ `lab/lab_ring_composite.fsh` |

### 实验里踩到的两条硬约束（要进游戏就必须照做）

1. **光晕必须减掉自身**：`halo = max(halo − self, 0.0)`。
   加权平均和模糊结果在剪影**内部**也等于 1 —— 不减掉的话整把武器会被糊上一层，
   在游戏里等于把物品贴图和 cosmic 星空层一起盖掉。
   第一版就是这么错的（图里整把剑在发光），`mask_glow.fsh` 的 `spill = max(halo − gate, 0)`
   是同一个道理。
2. **scissor 矩形要跟着光晕半径外扩**：现役 `ItemOutlinePipeline.rectFromBox` 的
   `pad = ceil(pixelWidth) + 2` 是按"硬环"算的，光晕要几十像素，不放大就会被裁掉。

### 其它记录

- `lab_ring_halo.fsh` 的 `warp` 是**直接乘在采样半径上**的：warp 大 → 采样够得到剪影 → 亮；
  这就是"哪里伸出一团"的成因，比只扰动采样 UV 更有效。
- 图里是**单色**（遮罩用 ColorMode 0 的淡紫白），因为这一轮只比**形状**。
  进游戏时颜色可以取物品自己的描边色，或者走火焰预设（第十章），再叠一条
  强度→颜色的 ramp 就能得到"内层白热、外层紫"的层次。

### 火焰 + 光雾（用真·着色器）

`AuraLab` 的最后一段会用**游戏里那套真·着色器**再跑一遍：遮罩走 `ColorMode 4`（火焰），
模糊走 `outline_halo_blur.fsh`，合成走 `outline_ring.fsh` 的光雾分支：

| 图 | 内容 |
|---|---|
| `fire_line.png` | `HaloStrength = 0` → 只有火线（对照组） |
| `fire_aura.png` | `HaloStrength = 0.9`、扩散 1.0（游戏默认） |
| `fire_aura_strong.png` | `HaloStrength = 1.5`、`BlurScale = 1.4`（更浓更散的档位） |

画布尺寸用第 5 个参数给（`1600`）：光雾半径是**固定像素**，512 画布上一团 52px 的雾
会比游戏里(1600p)显得大三倍多 —— 看"雾相对物品多大"必须用游戏分辨率量级。

> ⚠ **裸 GL 里没人设过的 uniform 是 0.0，不是 JSON 里的默认值。**
> `BlurScale` 忘记显式设置时模糊步长会被乘成 0（等于不模糊），而画面仍然"有雾"
> （来自 UV 扰动），于是很容易被骗过去。凡是 JSON 里有默认值的 uniform，
> 在实验台里都要自己设一遍。

### 这个实验台**抓不到**什么（踩过一次，值得记住）

它验证的是**着色器**，不是 Java 侧的 GL 状态簿记。真实案例：`ItemOutlinePipeline.buildHaloChain`
跑完四次模糊后忘了把 framebuffer 切回主 RT，于是紧接着的环形合成被画进了那张 1/4 大小的
模糊图里 —— **游戏里所有描边一起消失**，而实验台这边因为每一张图都会自己重新绑输出，
一点异常都看不出来。

所以：**凡是切换了 FBO / viewport / scissor 的函数，必须在同一个函数里还原**，
并且调用方再显式绑一次关键目标。离屏能证明"着色器对"，证明不了"顺序对"。

---

## 背板预览：`backplate.ps1`（不启动游戏）

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\tools\shader-preview\backplate.ps1                    # 现役着色器 → build\backplate-preview\
.\tools\shader-preview\backplate.ps1 -ShaderDir build\bp-candidates\eclipse -OutDir build\bp-candidates\eclipse\out -Scale 1.5
```

`BackplatePreview.java` 按**实机几何**复现绯红誓约背板：

- 48 段扇形 billboard（`POSITION_TEX`，`UV0 = 单位平面局部 (x,y)`，与
  `CrimsonVowBackplateMesh` 一致），相机空间缩放 = `config Scale ÷ 0.86`，
  中心在相机前方 `4.0（第三人称距离）+ 0.65（HeadOffset）` 格；
- 两遍绘制：底遍 `srcalpha / 1-srcalpha`、亮遍加色 `SRC_ALPHA, ONE`，
  与 `SplendidingShaders` 的两个 RenderType 同语义；新增的 `GlowPass` uniform
  在两遍之间切 0/1；
- **玩家剪影**：按 1.8 格高、第三人称 4 格距离投影的深色人形。背板在玩家身后，
  中下部会被挡住 —— 设计必须按"被挡掉 1/3"的真实构图评判。

产物（默认 1280×720）：`inworld_t000/045/090`（实机构图 + 动画）、
`inworld_block100`（格挡拉满）、`inworld_reveal*`（展开）、`inworld_daysky`
（亮背景对比度）、`closeup_*`（不带剪影的完整图案）、`pass_base/pass_glow`
（两遍各画了什么）。

参数：`-ShaderDir`（着色器目录）、`-OutDir`、`-ShaderBase`（默认
`rendertype_crimson_backplate`）、`-Scale`（config 可见半径，默认 1.5）、
`-VisibleR`（`Rig.SHADER_VISIBLE_RADIUS`，默认 0.86）。

> 已知局限：这里没有场景深度与真实玩家模型，只验证「几何 / UV 约定 / 两遍混合 /
> 图案算法 / 被剪影遮挡后的构图」，不验证 MC 的 RenderType 簿记与光影包上下文。

---

## 法阵预览：`sigil.ps1`（不启动游戏）

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\tools\shader-preview\sigil.ps1                                   # 现役着色器 → build\sigil-preview\
.\tools\shader-preview\sigil.ps1 -ShaderDir build\bp-candidates\sigil -OutDir build\bp-candidates\sigil\out
```

`SigilPreview.java` 按**实机约定**复现绯红誓约的两个地面法阵
（`rendertype_crimson_sigil.fsh` 的 `uMode = 0` 蓄力 / `uMode = 1` 巨型）：

- 几何：与 `CrimsonVowRenderSupport.emitDisc` 一致的 6 环 × 96 扇区圆盘，
  `POSITION_COLOR_NORMAL`；`Color.rg = (径向, 角度)` 的 **u8 量化与 Normal 的 i8
  量化都照实复现**（不量化的话离屏看不到的锯齿会躲过检查）；
- 变换：Java 侧把 `translate(-camera)` 折进 pose，顶点在着色器里已是**视空间**，
  这里直接算 `R·(world − cam)` 再乘 `ProjMat`；
- 混合：加法（ONE, ONE）+ 不写深度，与实机 RenderType 同语义；uniform 全部按
  `CrimsonVowChargeRenderer` / `CrimsonVowFieldRenderer` 的实际取值写入；
- 镜头：`top`（正上方，看图案本身）/ `in`（站阵内，眼高 1.62）/ `out`（站阵外斜看）
  以及蓄力阵的第三人称后上方。

产物：`charge_00_early ... charge_05_t2`（蓄力：半径 1.1/2.6/4.6、过载、俯视）、
`field_00_deploy ... field_09_altseed`（巨型：展开 / 俯视 / 阵内 / 阵外 / 扩散波
0.3 与 0.7 / 预警 / 终末 / 触地 / 换 seed）。

> ⚠ **`uImpactAge` 的"未触地"是 `-1`，不是 `0`。**
> 实机里 `CrimsonVowFieldRenderer` 在未触地时传 `-1`，片元用
> `if (uImpactAge >= 0.0)` 判触地白闪；`0` 是"刚刚触地"那一 tick。
> 预览台最初给每张 `field_*` 都传了 `0.0`，于是所有"平时"的图中心都多出一团
> 近白高斯 —— 会被误读成"过曝"。现在除 `field_08_impact`（0.3s）外全部传 `-1`。
>
> 已知局限：没有真实地形与立方体核心 —— 中心区域在实机里会被铬核挡住；
> 也不验证 MC 的 RenderType 状态簿记。

---

## 光柱预览：`beam.ps1`（不启动游戏，**如实复现 byte 编码**）

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\tools\shader-preview\beam.ps1                                     # 现役着色器 → build\beam-preview\
.\tools\shader-preview\beam.ps1 -Mode old                           # 复现实机里"旧版"的光柱
.\tools\shader-preview\beam.ps1 -ShaderDir build\bp-candidates\beam -OutDir build\bp-candidates\beam\out
```

`BeamPreview2.java` 按实机几何复现终末轰击光柱（`uMode = 3`）：三层同心圆柱
（`emitCylinder`，56 段）+ 每层枪尖锥 + 触地飞溅，加法混合，uniform 取值照抄
`CrimsonVowFieldRenderer.drawBeam`。

> ⚠ **这个工具存在的理由就是一个 encoding 坑。**
> `POSITION_COLOR_NORMAL` 的 NORMAL 是 **3 个 signed byte、归一化**；MC 的
> `normalIntValue()` 是 `(byte)((int)(clamp(f,-1,1)*127) & 255)`。旧版把世界 Y
> （36~70）写进 `Normal.x`，于是整根柱子的 `vHeight` 全都是 **1.0** —— 所有按高度
> 算的图案（电弧、下扫环、前缘光斑、tail 淡出）在实机里失效，画面上是一根
> **均匀白管 / 白方尖碑**。旧预览台把 Normal 当 float 写，所以一直看不出来。
> 这里严格按 u8/i8 编码写入，`-Mode old` 能复现那个 bug，修好后的着色器则用
> `uBeamAxisVS/uBeamAxisDir` 反解世界高度。

产物：`fall_00_raw15 ... fall_03_wide`（下落）、`impact_00_t000 ... impact_05_lookup`
（触地 0/4/10/20 tick、近景、仰视）、`charge_00_column`（蓄力柱）。

> 局限：没有真实地形/粒子/屏幕抖动，只验证光柱本身的几何、分层与图案。

---

## 魔法星图预览：`magic.ps1`（不启动游戏）

```powershell
.\tools\shader-preview\magic.ps1                 # 编译 + 渲染一帧 → build\magic-preview\magic-gui.png
.\tools\shader-preview\magic.ps1 -Time 9         # 换一帧看动画相位
```

`MagicPreview.java` 按 `MagicScreen` 的约定复现两件事：

- **背景**：整屏四边形 + `magic_starfield`（流动星云 + 三层视差星点），
  用 `uSize` 拿像素坐标，所以星点尺寸不随分辨率变；
- **连接光带**：5 个槽位按"∧∨∧"折线摆好，每段一个四边形 + `magic_constellation`
  （发丝级亮芯 + 极淡薄雾 + 很慢的明暗呼吸；**没有**沿轨道滚动的小光点），
  加法混合与 json 的 `srcalpha / one` 同语义；`UV0.x = 长度 / 64`，所以长短段的节奏一致。

产物只有一张 `magic-gui.png`。它**不验证** MC 的 `ShaderInstance` 状态簿记与
`GuiGraphics` 批处理顺序（那部分靠 `MagicGuiShaders` 里"先 flush、后还原"的写法），
但能提前抓到 GLSL 编译错误与"这颜色/密度到底好不好看"。

> 注意：预览里背景是**不透明**的（与游戏里一致）；`magic_starfield.json` 故意不写
> `"blend"`，靠 `RenderSystem.disableBlend()` 直接覆盖整屏，避免和世界画面叠加。
