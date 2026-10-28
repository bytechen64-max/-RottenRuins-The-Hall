# 物品效果框架（Item FX）

> 目标：加一个新效果（火焰、冒烟、析晶、腐蚀、裂纹……）= **新写一个实现 + 注册一行**，
> 而不是回头改渲染管线里的一个 `switch`。

## 一、为什么需要它：现在不是框架，是一个效果

项目里其实已经有**两套各占一半轴的半框架**，它们互不相通 —— 这是根问题：

| | `mask_layers`（宇宙星空/泛光） | `ItemOutlinePipeline`（描边） |
|---|---|---|
| 每物品独立 | ✅ `MaskLayerRegistry` + `mask_layers` JSON | ❌ 全屏**一张共享掩码**，所有物品一起合成 |
| 效果可插拔 | ✅ `MaskEffectRegistry`：`id → MaskEffectPass`，未注册只跳过并点名 | ❌ 效果是 `outline_mask.fsh` 里的 `ColorMode` 分支 |
| 有序层栈 | ✅ `order` 升序 | ❌ 单效果 |
| 几何域 | ✅ 物品表面（物品自己的 quad + UV） | ✅ 剪影外侧（`dilate − self`） |
| 参数 | ⚠️ **所有效果共用一套固定字段** | ⚠️ 硬编码 uniform + 配置项 |
| 跨帧状态 | ❌ 纯函数 | ❌ 纯函数 |
| 混合模式 | ❌ 加法一种 | ❌ 加法/透明两种 |

把五个点名要的效果拆成能力需求，缺的东西就很清楚了：

| 效果 | 域 | 算子 | 混合 | 状态 |
|---|---|---|---|---|
| 边缘火焰 | 剪影外侧 | 注入 / 平流 / 耗散 | 加法 | ✅ 密度场跨帧 |
| 冒烟 | 剪影外侧 + 上方 | 同上（第二通道、不同浮力） | 加法 + **压暗** | ✅ 烟密度场 |
| 边缘析晶 | **物品表面** | 生长前沿、晶面剖分、法线扰动 | 加法 / 覆盖 | ✅ 生长前沿 + 进度 |
| 边缘扩散（腐蚀） | 表面 + 剪影向内 | 扩散 / 侵蚀 | **modulate / 吃掉本体** | ✅ 腐蚀场 + 进度 |
| 物品裂开 | 表面 + **几何** | 裂纹曲线场、沿缝位移 | 替换本体 + 暗缝 | ✅ 完整度 + 种子 |

两条硬结论：

1. **5 个里有 3 个是"物品表面"域**（析晶、腐蚀、裂纹）—— 只在剪影外侧的描边管线**根本不该管它们**。
2. **5 个里有 4 个需要跨帧状态** —— 而现在两边都是"每帧独立求值的纯函数"。
   这不是加效果的事，是**缺一个持久场**。

## 二、六根轴（框架的全部内容）

1. **域** `ItemFxDomain`：`SURFACE_UV` / `SURFACE_WORLD` / `SILHOUETTE` / `SCREEN`。
   决定"效果需要看到什么几何、以及必须自己保证不越界的那条边界在哪"。
2. **场** `ItemFxFieldSpec` + `ItemFxFieldPool`：离屏贴图的**声明**（格式 / 相对主 RT 的尺寸 /
   下限 / 是否跨帧保留），生死由池统一管理。效果不碰 FBO 生命周期。
3. **算子**：`inject` / `advect` / `diffuse` / `erode` / `warp` / `threshold` / `emit`。
   一个效果 = 一串算子 + 一次合成。**S1 还没有算子这一层**（见路线图 S1b/S2）。
4. **参数** `ItemFxParams`：每个效果自己的参数块。`mask_layers` 那套固定字段
   （color/width/speed/opacity…）塞不下"晶面尺度/种子/生长方向"，所以不能再共用一张 record。
5. **混合** `ItemFxBlend`：`ADD` / `ALPHA` / `MODULATE` / `DARKEN` / `REPLACE_BODY`。
   浓烟要压暗、裂纹要变暗、腐蚀要吃掉本体 —— 只靠加法永远做不到。
6. **进度**：耐久 / 伤害 / 方块状态 → 效果参数（析晶长到第几阶段、腐蚀到哪一层）。
   现有 `ICustomOutline` 是"静态声明"，缺一个"随玩法变化的驱动量"。

## 三、S1 已完成的部分（结构，画面逐位不变）

新增 `client/fx/`：

| 文件 | 职责 | S1 是否真的被用上 |
|---|---|---|
| `ItemFxDomain` / `ItemFxBlend` / `ItemFxFormat` | 词汇 | ✅ 效果声明里读取 |
| `ItemFxFieldSpec` | 一张场的声明（格式/尺寸/下限/持久性） | ✅ 场池按键取值 |
| `ItemFxFieldPool` | 场的创建 / 改尺寸 / 回收 / 诊断 | ✅ 接管了描边那**五张**离屏图 |
| `ItemFxEffect` | 效果接口（id / domain / fields / blend / order） | ✅ 描边实现了它 |
| `ItemFxRegistry` | `id → 效果`，形状沿用 `MaskEffectRegistry` | ✅ 描边自带注册 |
| `effects/OutlineFxEffect` | 把现有描边注册成第一个效果，**声明**它的五张场 | ✅ 已注册 |

**场池接管了哪五张图**（尺寸、格式、过滤、清空时机全部不变，所以画面不变）：

```
outline/mask           全分辨率 RGBA8   ← 剪影遮罩（颜色烘在 rgb）
outline/haloHalfWork   1/2     RGBA8   ← 模糊链 X 遍的乒乓
outline/haloHalf       1/2     RGBA8   ← 模糊链 Y 遍产物（合成采样这张）
outline/haloWideWork   1/4     RGBA8
outline/haloWide       1/4     RGBA8
```

**S1 明确没做**（避免把"声明好了"说成"接好了"）：

- `capture` / `composite` 钩子还没加 —— 描边那几趟 pass 仍在 `ItemOutlinePipeline` 里，
  效果对象目前只提供声明。**S1b** 才把它们搬进 `OutlineFxEffect`。
- `ItemFxParams`（每效果参数块）还没写 —— 现在的逐物品样式（宽度/颜色/不透明度/光雾参数）
  仍是管线里的字段 + 配置项。
- 浮点场（`RG16F`/`RGBA16F`）没实现：`ItemFxFieldPool` 遇到会**明确报一次警并跳过**，
  而不是静默退化。它要跟第一个真正需要跨帧累积的效果（火焰）一起做。
- 逐物品的层栈（`List<ItemFxLayer>`）没做：现在一件物品 = 一个描边效果。

验证方式：启动后日志里会出现

```
[ItemOutline] 光雾模糊链已启用：… ；已注册效果=[outline]；场池=outline/mask=WxH outline/haloHalf=…
```

看到 `已注册效果=[outline]` 与五张场的尺寸，就说明 S1 的骨架真的在跑。

## 四、路线图与验收标准

| 步 | 内容 | 验收标准 |
|---|---|---|
| **S1b** | 把 `capture` / `composite` 钩子加到 `ItemFxEffect`；把描边三趟 pass（遮罩 / 模糊链 / 环形合成）从管线搬进 `OutlineFxEffect`；写 `ItemFxParams` 并把逐物品样式装进去；加**覆盖门** `ItemFxCoverage`（默认 `OUTSIDE_ONLY` = 今天的行为） | **画面逐位不变**（同屏 A/B 截图对照）；改效果实现不再需要动管线 |
| **S2** | 浮点场（自建 FBO 包装，`RG16F`）+ **每物品槽位**（`FieldKey#stateKey` + 有界池 + LRU + 跳变清理）+ `advect` 算子；火焰做成第二个效果 `flame`（注入+平流+浮力∝温度+curl+耗散+黑体 LUT），冒烟用同一张场的第二通道 | 出火舌、会脱离、遮挡时自然熄灭；开/关效果画面 A/B；成本低于被替换的膨胀环；**槽位耗尽时退化成无状态版，而不是不画或无限分配** |
| **S2b** | 驱动加 `compositeScreenFrame()` 阶段 + 抽出"阈值 / 降采样模糊 / 上采样相加"算子（描边的光雾链是它的一个特例） | `SCREEN` 域效果能跑（先用全屏泛光验证）；描边光雾行为不变 |
| **S3** | 把 `mask_layers` 接到同一套注册表与参数块上（它已有注册表，只差统一 spec 与"状态"概念） | 宇宙星空/泛光表现不变；**析晶**能声明自己的参数块与每物品生长场 |
| **S4** | 进度驱动（耐久/伤害/方块状态）+ 几何钩子（重画 quad / 顶点位移）+ `REPLACE_BODY` / `DARKEN` 合成 | **腐蚀扩散**（吃掉本体 + 进度推进）、**裂开**（裂纹位移 + 暗缝）各有实现；同一件物品能同时挂多个效果且互不顶掉 |

## 五、怎么加一个新效果（S2 之后的配方）

```java
public final class ChillFxEffect implements ItemFxEffect {
    public static final String ID = "chill";

    @Override public String id() { return ID; }
    @Override public ItemFxDomain domain() { return ItemFxDomain.SURFACE_UV; }
    @Override public List<ItemFxFieldSpec> fields() {
        return List.of(ItemFxFieldSpec.state("frost", ItemFxFormat.RG16F, 2, 64)); // 跨帧累积的霜层
    }
    @Override public ItemFxBlend blend() { return ItemFxBlend.MODULATE; }          // 结霜是"变亮发白"
}

// 注册一行
ItemFxRegistry.register(new ChillFxEffect());
```

## 六、已知取舍与风险

- **别做通用引擎**：现在只有 1 个效果，六根轴是照着 5 个**已点名**的效果定的，
  不预留"也许以后要"的抽象（比如可编程算子图、效果的热重载）。
- **per-item 状态池是唯一真正贵的地方**：跨帧场要按物品分（析晶进度、腐蚀场各不相同），
  而世界里的掉落物数量不可控 → S2 必须同时给出**分配上限 + LRU 回收 + 跳变清理**，
  否则会变成"每件物品一张 16F 图"的显存事故。
- **顺序/深度语义**：多个效果同时挂在一件物品上时，谁盖谁由 `order` 决定；
  剪影外侧效果与表面效果的交界（1px 缝、根部覆盖）需要用"全分辨率硬裁"保住，
  这条在 S2 的合成里必须写死，不能靠调参。

## 七、按效果逐条对表：S1 骨架够不够

这一节是**验收性质**的：拿"未来要做的效果"清单去审 S1 搭出来的骨架，
逐条说明哪个轴兜住了它、哪一处兜不住。兜不住的三条在末尾，都已排进路线图。

| 效果 | 域 | 场 | 算子 | 混合 | 状态 | S1 兜住了吗 |
|---|---|---|---|---|---|---|
| **edge flame**（火） | `SILHOUETTE` ✅ | 共享 `RG16F` ✅ | inject/advect/dissipate（S2） | `ADD` ✅ | 跨帧共享场（场池支持尺寸/持久性声明） | ✅ 只差浮点场与算子 |
| **smoke**（冒烟） | `SILHOUETTE` ✅ | 同一张场的第二通道 ✅ | 同上（不同浮力/耗散） | `ADD` + **`DARKEN`/吸收**（枚举已留，实现随 S2） | 跨帧共享场 | ✅ 结构够；混合模式要加一档 |
| **bloom / glow**（泛光） | 两种：物品表面的泛光（`SURFACE_UV`，已有 `mask_layers` 那套）/ 全屏泛光（`SCREEN`） | 降采样链（描边已经在用同一套） | 阈值/模糊/上采样相加 | `ADD` ✅ | 无状态或单帧乒乓 | ⚠️ **`SCREEN` 域没有驱动路径**（见缺口 B） |
| **crystallization**（边缘析晶） | `SURFACE_UV` ✅ | **每物品**一张生长场（进度 + 生长前沿） | 生长推进/阈值/晶面剖分（S2 起）/几何扰动（S4） | `ADD` 或 `REPLACE_BODY`（枚举已留） | **每物品持久** | ❌ **场池现在只能开"每个效果一张"共享图**（见缺口 A） |
| **edge diffusion**（边缘扩散/腐蚀） | `SURFACE_UV` + **剪影向内** | 每物品腐蚀场 | 扩散/侵蚀（erode + 噪声） | **`MODULATE` / `REPLACE_BODY`**（要"吃掉"本体） | **每物品持久** + 进度 | ❌ 缺口 A（每物品场）+ ⚠️ 缺口 C（覆盖门） |
| **cracking**（裂开） | `SURFACE_UV` + **几何位移** | 裂纹图案（空间曲线场）+ 完整度 | 裂纹场/位移/暗缝 | `REPLACE_BODY` + `DARKEN` | **每物品持久** + 进度 | ❌ 缺口 A + 需要 S4 的几何钩子 |

### 缺口 A：场池没有"每物品"这根轴（最要紧）

S1 的 `ItemFxFieldPool` 键是 `(effectId, fieldName)` —— **一个效果一张图，同屏所有物品共用**。
这对火/烟是对的（故意让多团火在同一个场里交叠合并，读感更有机），但析晶、腐蚀、裂纹
**必须每件物品各有一份**：一把剑腐蚀到第三层，另一把才刚开始，两把不能共用一个场。

修法（S2/S3 落地，形状先定下来）：

```
FieldKey = effectId + "/" + fieldName + "#" + stateKey
                                     ↑ 物品实例标识（掉落物实体 id / 展示框坐标 / 手持槽位）
```
- 手持与 GUI 这类"每帧只有一个实例"的槽位：直接 `stateKey` 固定，零成本。
- 世界里的掉落物/展示框：**有界槽位池**（例如 64 张 128² 的 16F ≈ 4MB）+ LRU + 超时回收；
  拿不到槽位就退化成"无状态版本"（只有形状没有生长历史），而不是不画或无限分配。
- **跳变清理**：切维度、F5、物品被销毁/传送 → 按 `stateKey` 清场，否则会出现"上一次的腐蚀还在"。

### 缺口 B：`SCREEN` 域没有驱动路径

`ItemFxDomain.SCREEN` 已经声明，但驱动只有"物品目标"这条路径
（`submit → PENDING/READY → 合成`）。全屏泛光、屏幕结霜这类效果要的是
**"整帧画完之后"的一个钩子**，而不是"这些物品目标"。

修法：驱动加第二个阶段 `compositeScreenFrame()`（仍在 `renderLevel` 的 TAIL、
在物品合成之后），`SCREEN` 域的效果挂在那里跑，不参与逐物品循环。
顺带把"阈值 + 降采样模糊 + 上采样相加"抽成一个可复用算子 —— 描边的光雾链
已经是它的一个特例。

### 缺口 C：没有"覆盖门"（效果能画在哪里）

现在合成阶段的不变量是写死的：**只在剪影外侧**（`halo = max(halo − self, 0)`）。
但：
- 析晶/裂纹画在**表面**（剪影内侧）；
- 腐蚀要**同时**吃掉本体与外缘；
- 冒烟的吸收要在火的**外圈**。

修法：把"门"变成一个声明 —— `ItemFxCoverage { OUTSIDE_ONLY, INSIDE_ONLY, BOTH, SURFACE_MAPPED }`，
由合成算子按它选 `self` 的用法。`OUTSIDE_ONLY` 就是今天的行为（默认值），
所以这条改动本身不改任何画面。

