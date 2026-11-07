# 自定义分节符文本特效 · 方案提案

> 状态：**已实施第一版**（2026-10-31；实机验证与遗留项见 §12）
> 面向：`Rotten Ruins / 腐朽之疫：王庭`（Forge 47.4.20 · MC 1.20.1 · official mappings）
> 产出物：本文件 + `docs/proposals/text-effects-preview/`（动画预览 GIF、联络表、交互式 HTML 游乐场）
> 调研底稿（未提交，在 `build/research/`）：`pipeline.md`（原版文本管线 749 行）、`prior-art.md`（同类 mod 先例）、`conventions.md`（项目约定与 20 条坑位）

---

## 0. 一句话方案

**在 `§` 后面加一套带命名空间的"分节符指令"（`§[rrs:grad:#6FBF4A:#8B1E2D]…§[/]`），让同一行文本里也能声明逐字颜色渐变、彩色、呼吸、抖动、波浪、故障等渲染；解析只在原版文本管线上做三个"薄"拦截点，绘制层永远不重写原版字形渲染 —— 标记零宽，所以宽度、折行、tooltip 面板尺寸与今天完全一致。**

```text
今天：  §c红色            §r           ← 原版 16 色 + 格式码
提案：  §[rrs:grad:#6FBF4A:#8B1E2D]腐朽之疫§[/] 王庭  ← 逐字渐变 + 显式结束
        §[rrs:shake:amp=1.2]它在动§[/]            ← 抖动（只改绘制坐标，不改排版）
        §[rrs:rainbow]彩色文字§[/]                 ← 逐字色相流动
```

预览：`docs/proposals/text-effects-preview/preview-sheet.png`（一张看全 8 个效果）、各效果 `*.gif`、`index.html`（浏览器里可调参的游乐场）。

---

## 1. 目标与硬性验收条件

### 1.1 功能目标

1. 用 `§` 分节符在**任意文本**里声明效果，而不是只在某个物品类里硬编码；
2. 效果至少覆盖：**颜色渐变、任意彩色、颜色呼吸、抖动、波浪、故障**（打字机/缩放等留作后续）；
3. 同一行内可**多次切换/嵌套**（前半句渐变、后半句抖动+渐变、再恢复正常）；
4. 效果**只影响渲染**：不改变排版宽度、不改变文本内容、不影响服务端逻辑；
5. 可**一键关闭**；关闭后行为退化为今天的原版显示。

### 1.2 硬性验收条件（任何一条不满足就不合并）

| # | 条件 | 为什么 |
|---|---|---|
| G1 | 不含标记的文本，渲染结果与今天**逐像素一致**（快速路径零改动） | 这个改动在 `Font`/`StringDecomposer` 这种"所有文字都走"的热路径上，任何微小偏差都会放大到整个游戏 |
| G2 | 标记**零宽度**：`font.width(带标记文本)` 与去掉标记后的宽度完全相等；tooltip 折行/面板尺寸不变 | 项目已有教训：tooltip 宽高账对不上不会报错，只会压字（`ClientBlockBarTooltip.java:33-37`、`TooltipStyleHook.java:93-113`） |
| G3 | 原版 `§0-9a-fk-or`、代理对（emoji/生僻字）、`§r`、`§k` 行为不变 | 项目命令反馈/难度名大量使用 § 码（`HallDifficultyEventHandler.java:97-103`） |
| G4 | 任何 hook 抛异常/加载失败时，文字**仍然可见**（退化为原版），日志只报一次 | `FlowingNameColors.java:12-17` 记录过"接管 renderChar → 整段文字消失"的事故 |
| G5 | 现有文本装饰零回归：`FlowingNameColors`、`ITooltipStyle` 底板、`ClientBlockBarTooltip`、创造栏标题、魔法星图、难度选择屏 | `build/research/conventions.md` §8 验收 checklist |
| G6 | 客户端 only：服务端不加载任何 `net.minecraft.client.*` | 专用服务器会直接崩 |
| G7 | 性能增量可量化且有上限（目标 < 300µs/帧，见 §5.4） | 文本是每帧都在跑的路径 |

---

## 2. 已验证的底层事实（决定方案为什么长这样）

> 证据来源：本机 Gradle 缓存里与项目锁版本一致的 **Forge 47.4.20 official 源码 jar**
> `~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.20_mapped_official_1.20.1/forge-1.20.1-47.4.20_mapped_official_1.20.1-sources.jar`
> （下称 `forge-src!`，条目路径即原版类路径；行号已逐条核对），以及项目自身源码（路径相对仓库根）。

### 2.1 原版文本管线的关键事实

| # | 事实 | 证据 |
|---|---|---|
| F1 | `§` 码的**唯一解析核心**是 `StringDecomposer.iterateFormatted(String, int, Style, Style, FormattedCharSink)`：遇 `§` 时取后一字符，`ChatFormatting.getByCode` 命中就改 `Style`，**未命中也会把后一个字符吃掉**（静默、零宽、不报错） | `forge-src!net/minecraft/util/StringDecomposer.java:85-127` |
| F2 | 因此"自定义 `§` 字母"今天在游戏里**表现为文字消失两个字符**；这也是为什么必须由我们自己的解析层消费掉标记（见 §5） | 同上 :91-103；`ChatFormatting.java:131-141` |
| F3 | 原版 Java 1.20.1 **没有** `§x§R§R§G§G§B§B` 十六进制（那是 Spigot/Bungee 生态约定）；`ChatFormatting` 只有 `0-9 a-f k-o r` | `forge-src!net/minecraft/ChatFormatting.java:15-37`、:131-141 |
| F4 | **测量与绘制共用同一解析**：`Font.width(String)` → `StringSplitter.stringWidth(String)` → `iterateFormatted`；`Font.split`/`splitLines` 也直接调同一个核心 | `forge-src!net/minecraft/client/StringSplitter.java:29-35`、:245-281；`forge-src!net/minecraft/client/gui/Font.java:162-196` |
| F5 | **Component 绘制路径**：`Component.getVisualOrderText()`（带缓存）→ `ClientLanguage.getVisualOrder` → `FormattedBidiReorder.reorder` → `SubStringSource.create`（**§ 在这里被解析成逐字 Style，然后被丢弃**）→ 产出 `FormattedCharSequence`；真正画字在之后的 `Font.drawInternal → renderText(FCS) → StringRenderOutput.accept` | `forge-src!net/minecraft/network/chat/MutableComponent.java:76-84`；`ClientLanguage.java:74-76`；`SubStringSource.java:63-79`；`Font.java:130-152, 255-295` |
| F6 | **String 绘制路径**：`GuiGraphics.drawString(Font, String, …)` → `Font.drawInBatch(String…)` → `drawInternal(String)` → `renderText(String)`，这一路在**绘制当时**调用 `iterateFormatted` | `forge-src!net/minecraft/client/gui/GuiGraphics.java:268-306`；`Font.java:66-128, 142-146` |
| F7 | `Font.renderChar(...)` 是**唯一**的字形落点（`BakedGlyph.render` 只被它调用）；它拿不到 `Style`，也拿不到字符序号 —— 逐字颜色只能走 `Style`，逐字位移只能在 `StringRenderOutput.accept` 或更上层做 | `Font.java:154-160, 276-281` |
| F8 | `drawInBatch8xOutline` 用 **lambda** 包住 `StringRenderOutput` 再逐字喂（发光告示牌/描边场景）；在这种"中间又包了一层 sink"的路径里，逐字状态通道会失效（已知限制，见 §9） | `Font.java:82-108` |
| F9 | 聊天行、告示牌、bossbar 等的 `FormattedCharSequence` 会被**缓存/复用**；在"构建视觉顺序时改色"只能得到静态颜色 → 想要逐帧动画必须让"颜色求值"发生在 `accept` 时（见 §5 的 L1） | `forge-src!net/minecraft/client/gui/components/ChatComponent.java:203-214`（聊天行缓存）、`SignRenderer.java:114-147`、`BossHealthOverlay.java` |
| F10 | `Language.getVisualOrder(List)` 用 `this::getVisualOrder` **虚分派** → `ClientLanguage` 的重写对所有"批量转视觉顺序"的调用方（`Font.split`、聊天折行 `ComponentRenderUtils.wrapComponents`、tooltip 折行）都生效，是理想的接入点 | `forge-src!net/minecraft/locale/Language.java:107-111`；`ComponentRenderUtils.java:25-37` |
| F11 | Forge 47.4.20 **没有字形级钩子**：`IForgeFont` 只提供 ellipsize，`RenderTooltipEvent`/`RenderNameTagEvent` 等都是组件级 → 逐字效果只能 mixin | `forge-src!net/minecraftforge/client/extensions/IForgeFont.java` |
| F12 | 阴影偏移在 `StringRenderOutput.accept` 里已经加进 `x/y`（`this.x + f4`）再传给 `renderChar`；粗体第二份是 `renderChar` 内部按 `boldOffset` 再画一次 → 对 `x/y` 加同一个 delta，shadow/主字形/粗体份/描边多遍都会一致 | `Font.java:276-281, 154-160` |
| F13 | Forge 47.4.20 **没有** patch `StringDecomposer`（逐行与 vanilla 一致），也没有第二个入口；但**单人游戏的集成服务端线程**也会经过它 → 我们的解析逻辑必须是纯函数、不依赖客户端单例，duck 状态只在真实 `StringRenderOutput` 上命中 | `forge-src!net/minecraft/util/StringDecomposer.java`（全 jar 比对） |
| F14 | 判"文本里有没有 RTL 字符"可用 MC 自带的 `com.ibm.icu.text.Bidi.requiresBidi`（icu4j-core-mojang 51.2 在 classpath 上，javap 已确认）；这比"语言是 RTL 才回退"更精确 | 依赖列表；`ClientLanguage.java:70-72` |

### 2.2 项目侧的关键事实

| # | 事实 | 证据 |
|---|---|---|
| P1 | 项目**目前没有任何 Font 相关 mixin**；`IFlowingName` Javadoc 里"实现见 FontMixin"是**过时描述**，实际实现是"逐字 Component 上色" | `src/main/resources/rotten_ruins.mixins.json:11-27`；`api/IFlowingName.java:16-25`；`client/rend/text/FlowingNameColors.java:9-28` |
| P2 | 项目上一次**接管 `renderChar` 导致整段文字消失**（开发记录在 `FlowingNameColors.java:12-17`），此后刻意退回纯公开 API 方案 | 同上 |
| P3 | 会显示文字的路径只有四条：① `Component → Font`（tooltip/物品名/名牌/聊天/命令反馈/HUD，最大头）；② 4 个自绘 GUI 文件的 `drawString`；③ 命令反馈的 `Component.literal("§…")`；④ datagen 语言文件（当前 0 条 §） | `build/research/conventions.md` §1；grep 证据见该报告 |
| P4 | 配置 = Cloth AutoConfig + Jankson，文件 `run/config/rotten_ruins/SplendidingConfig.json5`；**布料配置一经写盘就以文件为准**，改已发布默认值必须 bump `CONFIG_VERSION` + 写迁移 | `config/ConfigHelper.java:39-196`；`config/data/SplendidingConfig.java:39-57` |
| P5 | 演示/验证的现行做法 = 服务端调试命令（`/spread /collapse /anomaly …`，统一注册在 `ForgeEventHandler.onRegisterCommands`）+ `runClient` 实机截图；没有客户端命令设施 | `event/ForgeEventHandler.java:115-125`；`run/logs/latest.log:1` |
| P6 | 文档/CHANGELOG 有固定模板：机制文档用"事实表（带出处）→ 组件表 → 数据流 → 边界取舍 → 调参表 → 坑"；CHANGELOG 中英成对、未发布特性用 `## [未发布] — <特性名>` | `docs/creative-tab-sections.md`、`docs/crimson-vow-tooltip.md`、`CHANGELOG.md:133-218` |
| P7 | 文档/部分 Javadoc 仍有改名前的 `hall.*`、`assets/hall/…` 过时内容，写新文档不要照抄 | `build/research/conventions.md` §5.5 |

---

## 3. 架构决策：为什么是"解析层接管 + 绘制层只做偏移"

有三条路，先摆在桌面上：

| 方案 | 做法 | 能覆盖 | 致命问题 |
|---|---|---|---|
| **A. 构建期预处理 / 逐字 Component** | 在文本构建处（ItemTooltipEvent、命令、GUI 助手）把标记解析成逐字 `Component`（每字一个 `Style`） | 我们能改到的那部分文字（tooltip/物品名/自绘 GUI） | 聊天/名牌/其它 mod 的文本**完全不经过**我们的构建代码；每帧重建逐字 Component 只适合单行 tooltip，聊天/GUI 里做会明显掉帧（`FlowingNameColors` 只敢用在一行名字上） |
| **B. 全局接管字形渲染**（换掉 `renderChar`/`BakedGlyph`，自己发顶点） | mixin 重写绘制方法 | 全部文字 + 所有效果 | **项目已经踩过**：文字整段消失且日志无线索；且 `BakedGlyph.render` 已被 Embeddium/Sodium 系优化 mixin（cancel 重写），三方共存行为不可预期 |
| **C. 解析层接管 + 绘制层最小偏移（推荐）** | ① 解析层：自己消费 `§[rrs:…]` 标记、逐字算出颜色/位移参数（不画任何东西）；② 绘制层：只在原版 `StringRenderOutput.accept` 的 `x/y` 上加一个 delta、**画完立刻减回去**；③ 覆盖层：在 `ClientLanguage.getVisualOrder` 返回处包一层"逐帧求值"的 `FormattedCharSequence`，解决缓存导致动画冻结 | 全部文字（含其它 mod/服务端发来的文本）；颜色 + 位移都支持 | 需要 3 个薄 mixin；需精细验证（本提案 §5/§8 就是为它准备的） |

**推荐 C。** 它和 B 的本质区别只有一句话：**B 重写"怎么画字"，C 只给"画字的位置"加一个临时偏移、并把"用什么颜色"喂给原版已有的 `Style` 通道。**
- 颜色走原版 `Style.color` —— 这是原版自己支持的路径（`Font.java:261-274` 中 `style.getColor()` 决定颜色），连项目现有 `FlowingNameColors` 都验证过；
- 位移只改传给 `renderChar` 的 `x/y` 参数，**绝不重写 `renderChar` 本身**，也不碰 `BakedGlyph`/`FontSet`（Embeddium 已在那两处 mixin）；
- 解析层只在"文本里真的有 `§[rrs:`"时才启动，普通文本走原版逻辑，零行为变化。

### 3.1 为什么"解析层"这次不会把文字画没

| 历史事故的可能原因（推断） | 本方案如何避开 |
|---|---|
| 接管了"唯一发顶点的方法"，任何还原偏差（粗体第二份/阴影/顶点格式/RenderType/UV/光照）都表现为"字没了" | 只做"解析"（返回布尔、改 `Style`、给个 delta），一个顶点都不自己发 |
| 一旦出错，错误发生在绘制中途，文字已经不可能恢复 | 解析层包 `try/catch(Throwable)`：出错就不接管、让原版继续画；绘制层 delta 默认 0，任何异常都不影响原版路径 |
| 无法从日志定位 | 每个失败点只报一次日志（`AtomicBoolean`），并且提供 `/rrsfx stats` 显示解析次数/失败次数/当前效果 |

---

## 4. 语法规范 v1

### 4.1 设计原则

1. **用 `§` 分节符**（与项目现有 `§` 用法一致），但**命名空间化**：`§[rrs:…]`，只处理 `rrs` 命名空间。原因见 §4.10（裸单字母已被 Bedrock 材质色码和多个文本 mod 占用）。
2. **标记整体零宽**：标记本身不占排版宽度、不产生字形 —— 这是 G2 的根基。
3. **显式关闭**：`§[/]` 关最内层的效果；同时容忍"到文本段结尾自动关闭"（作者漏写关闭不会污染其他文本）。
4. **可嵌套、可叠色**：颜色类效果与位移类效果可同时生效；内层颜色覆盖外层颜色。
5. **失败退化**：未知效果名/参数越界/畸形标记 → 该标记按"零宽无效"处理并只报一次日志；**绝不让文字消失**。
6. **主语法稳定、短别名可选**：`§[rrs:…]` 是正式语法；`§g` 这类短别名做成配置项（默认关，见 §4.9）。

### 4.2 语法总览

```text
§[rrs:<效果名>[:<参数>]…]      开始一个效果 span
…文本…                          span 内的文字带效果
§[/]                           关闭最内层 span（到文本段结尾也会自动关闭）
§[/<效果名>]                    关闭指定效果（可选语法，v1 一起实现，成本几乎为零）
§[rrs:reset]                   清空所有自定义效果（不影响原版 § 格式码）
```

- **作用域**：从标记开始，到对应的 `§[/]` 或**当前文本段（一个 literal 文本串）结尾**。跨 Component/换行的效果不会延续（见 §9 限制 1）。
- **嵌套示例**：

```text
§[rrs:grad:#8B1E2D:#FFB300]王庭§[rrs:shake:amp=1.2]的§[/]腐朽§[/]
                             └ 渐变整段 ┘ └抖动两字┘（抖动结束后仍是渐变）
```

- **组合规则**：颜色类效果（`grad`/`rainbow`/`solid`/`pulse`）**取最内层**；位移类效果（`shake`/`wave`/`glitch`）**取最内层**；两类互不影响。

### 4.3 效果清单（v1）

| 效果名 | 类别 | 参数（省略用默认） | 默认值 | 实现通道 |
|---|---|---|---|---|
| `grad` | 颜色 | 2–4 个颜色（位置参数），可选 `speed=0.0`（>0 时颜色沿文字流动，首尾闭环） | `#6FBF4A`→`#8B1E2D`（腐化绿→王庭血红），默认静止 | `Style.color` |
| `solid` | 颜色 | 1 个颜色，必须给或省略用默认 | `#E8E4D8`（苍白火） | `Style.color` |
| `rainbow` | 颜色 | `speed=1.0`、`sat=0.72`、`val=1.0`、`phase=0.0` | 见公式 | `Style.color` |
| `pulse` | 颜色 | `color=#E8E4D8`、`amp=0.35`、`speed=1.0` | — | `Style.color`（亮度乘法） |
| `shake` | 位移 | `amp=1.0`（GUI 像素）、`freq=2.0`（倍率） | — | `x/y` delta |
| `wave` | 位移 | `amp=1.2`、`len=12.0`（像素波长）、`speed=1.0` | — | `y` delta |
| `glitch` | 位移+显示 | `amp=2.5`、`rate=12.0`（Hz）、`prob=0.10` | — | `x/y` delta + `alpha` 调制 + 颜色通道偏移（P3） |
| `type` | 显示 | `cps=12`（字/秒）、`period=3.2`（秒） | **Phase 3 再做** | 未显现的字跳过绘制（`@Redirect` accept 内 `renderChar` 调用；与其它 mod 冲突时退化为 alpha=0） |

**公式（默认参数下的确切行为，预览 GIF 就按这些公式生成）**

```text
设 i = 本行可视字符序号（0 起），n = span 内可视字符数，t = 秒（墙钟）
grad    : u = i/(max(1,n-1)) + phase；颜色 = 线性光 RGB 多段插值（sRGB→linear→lerp→sRGB）
rainbow : hue = (i*0.055 + t*0.12*speed) mod 1；HSV(hue, sat, val)
pulse   : k = 1 - amp*(0.5 + 0.5*sin(2π*(t*0.8*speed + i*0.03)))；RGB *= k
shake   : dx = amp*(1.3*sin(t*11 + i*2.1) + 0.7*sin(t*17.3 + i*5.7)) * 0.6
          dy = amp*(1.3*sin(t*13.7 + i*1.3) + 0.7*cos(t*19.1 + i*3.9)) * 0.6
wave    : dy = amp*1.6*sin(i*0.55 - t*4.0*speed)
glitch  : b = floor(t*rate)；h = hash(i, b)
          若 h < prob  → dx = sign(h2)*(3..7)*amp/2.5，dy = 0（下一 tick 换位置）
          否则 dx = dy = 0
type    : 周期 period 秒；每周期前 period/2 秒按 cps 逐字显现，后 period/2 秒全显示
```

> 颜色渐变用**线性光插值**而不是直接 sRGB 平均，否则红→绿中间会发灰（预览图 `combo.gif`/`preview-sheet.png` 可对比）。参数全部在客户端配置里有全局倍率。

### 4.4 参数语法

- 分隔符：`:`；参数之间也用 `:`；键值用 `=`。
- 颜色：`#rrggbb`（也接受 `#rgb`）。**颜色参数给位置参数**时直接写 `#rrggbb`：`§[rrs:grad:#8B1E2D:#FFB300]`。
- 数值：十进制浮点/整数（`amp=1.2`、`cps=12`）；越界自动 clamp 到配置允许范围（不抛异常、不消失）。
- 大小写：效果名与键名小写；颜色大小写不敏感。
- 长度上限：单标记 ≤ 256 字符（防滥用）。超长/未闭合按 §4.7 处理。
- 参数为空等价于默认：`§[rrs:shake]` = `§[rrs:shake:amp=1.0:freq=1.0]`。

### 4.5 与原版 § 码、格式码的共存规则

| 写法 | 结果 |
|---|---|
| `§[rrs:…]` | 我们的效果 span 开始/结束（零宽） |
| `§0-9 a-f k-o r`（在 span 外的普通文本里） | **完全按原版行为**，一个字都不改 |
| `§r`（任意位置） | 原版重置 + **同时清空全部自定义效果**（可预测的"完全重置"） |
| 原版颜色码出现在 span 内（如 `§c`） | 渐变/彩虹继续覆盖该段颜色（颜色由 span 决定）；原版格式码（`§l`/`§o`/`§n`/`§k`）照常生效 |
| 原版格式码在 span 内 | 正常生效（加粗会影响宽度 —— 与今天一致，属于原版行为） |
| span 内嵌套 span | 允许；颜色取最内层、位移取最内层 |

> 想"提前结束渐变"的写法是 `§[/]` 或 `§[/grad]`；**不要**指望 `§c` 打断渐变（那样会让"渐变+局部红字"的实现边界变得不可预测）。

### 4.6 示例（可直接抄进 tooltip/命令/告示牌/书）

```java
// 物品 lore（lang 文件里同理）
Component.literal("§[rrs:grad:#8B1E2D:#FFB300]腐朽之疫§[/] §7—— 它正在看。")

// 物品名旁边一行的警示
Component.translatable("item.rotten_ruins.crimson_vow.tooltip.label.trait")
        .append(Component.literal("  §[rrs:shake:amp=1.0]不可直视§[/]"))

// GUI 标题（每帧重绘 → 动画正常）
gui.drawString(font, Component.literal("§[rrs:rainbow]魔法星图§[/]"), x, y, 0xFFFFFF);

// 告示牌/书名（Component 路径同样生效）
Component.literal("§[rrs:wave:amp=1.0]为吾王祈祷§[/]")

// 命令反馈
player.sendSystemMessage(Component.literal("§[rrs:glitch:amp=2.0]信号异常§[/] §a已恢复"));
```

> **注意**：效果标记会留在 `Component.getString()` 里。**不要**把标记塞进正式注册名/实体名（铁砧改名、名字比较、命令选择器都会受影响 —— 项目里 `CollapsarBossBar` 就是靠显示名精确匹配的，`client/rend/gui/CollapsarBossBar.java:59-61, 114-118`）。物品名要彩色请继续用现有 `IFlowingName`/`FlowingNameColors`（本方案不改它）。

### 4.7 错误处理与降级

| 情况 | 行为 |
|---|---|
| 效果名未知：`§[rrs:nope]` | 标记按零宽吞掉，文本原样显示；日志只报一次（含原标记） |
| 参数未知/非法：`§[rrs:grad:foo=1]` | 未知键忽略、非法值 clamp/用默认；只报一次 |
| 未闭合：`§[rrs:grad:…` 到段末没有 `]` | 视为普通未知 `§` 码（原版会吞掉 `§[`），其后的正文照常显示；日志只报一次 |
| 关闭标记多余：`§[/]` 栈空 | 零宽忽略 |
| 总开关关闭（`textEffects.enabled=false`） | **仍然剥离标记**、按普通文本渲染（避免露出 `rrs:grad:…]` 字样），只是不上色/不位移 |
| 安全开关关闭（`fontHooksEnabled=false`） | 完全旁路三个 mixin，等价于没装本功能（给整合包排障用） |
| 无本 mod 的客户端 | 看到 `rrs:grad:…]` 字面量（原版吞掉 `§[`）——不崩、不串色；本项目是内容 mod（客户端必须装），实际不会发生 |

### 4.8 可选短别名（配置项，默认关）

给"想要 `§g` 手感"的场合，在配置里打开 `shortAliases` 后支持：

```
§g = grad    §h = rainbow   §p = pulse   §i = solid(默认色)
§s = shake   §w = wave      §q = glitch  §t = type
大写关闭：§G §H §P §I §S §W §Q §T（可选 Phase 4）
```

**为什么默认关**：裸单字母在 Minecraft 生态里已被大量占用（Bedrock Edition 材质色码 `§g/§h/§i/§j/§p/§q/§s/§t/§u/§w`；Phoenix、Brilliant Text、Chat Colours 等文本 mod 也各自占字母）——默认开启会与它们互相干扰。打开后我们的解析器也只认配置里列出的字母。

### 4.9 为什么用命名空间而不是裸单字母

1. **撞名**：Bedrock 材质色码与多个 Java 文本 mod 都用裸单字母；一旦玩家同时装了两者，谁抢到 `StringDecomposer` 谁赢，另一方效果错乱。
2. **可读性**：`§[rrs:grad:#8B1E2D:#FFB300]` 把"颜色/幅度"这些参数纳入了语法；裸单字母无法带参数，只能把参数藏进配置里（用户查配置才能用）。
3. **可共存**：`§[` 开头虽然也被 Chat Colours 用作 `§[name]`，但命名空间前缀不同（`rrs:` vs 名字），双方都只认自己的命名空间即可共存。
4. **失败面更小**：我们只接管"含 `§[rrs:` 的字符串"；其它任何文本（包括别的 mod 的自定义码）原样透传。

---

## 5. 渲染架构

### 5.1 数据流（三条路径，各接一个薄 hook）

```text
┌─ 文本来源 ─────────────────────────────────────────────────────────────────┐
│ ① Component 路径：tooltip / 物品名 / 名牌 / 聊天 / bossbar / 告示牌 / 书   │
│ ② String 路径：gui.drawString(Font,String) / 玩家列表 / EditBox 等          │
│ ③ 测量路径：font.width / font.split / tooltip 折行                         │
└────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
①  MutableComponent.getVisualOrderText()  ← 缓存（聊天/告示牌会跨帧复用）
      └─ ClientLanguage.getVisualOrder(FormattedText)      ★ Hook L1（@Inject RETURN）
            ├─ 有 §[rrs: → 包一层 WrapperFCS：accept 时逐帧求值（颜色 + 位移状态）
            └─ 无 → 原版 FCS 原样返回
      └─ 绘制：Font.drawInternal → renderText(FCS) → StringRenderOutput.accept
                                                    ★ Hook T2（@Inject HEAD/RETURN）
                                                      只对 x/y 加 delta，画完减回
②  Font.renderText(String) → StringDecomposer.iterateFormatted    ★ Hook T1
      └─ 有 §[rrs: → 混合委托解析（见 5.3）：消费标记、逐字改写 Style、设置位移状态
      └─ 无 → 原版逻辑（fast path）
③  StringSplitter.splitLines / stringWidth → StringDecomposer     ★ 同一个 T1
      └─ 标记在此被消费 → 零宽 → 宽度/折行/面板尺寸完全不变
```

### 5.2 三个 hook 的职责

> 本节的三项 hook + 状态通道已由独立管线复核（`build/research/pipeline.md` §14，逐条对照反编译源码验证）：**没有不可行项**；唯一需要遵守的 4 个 caveat 已列在本节末尾的"实现约束"表里。

| Hook | 目标（official 映射） | 注入方式 | 只负责 | 不负责 |
|---|---|---|---|---|
| **T1** | `net.minecraft.util.StringDecomposer#iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z` | `@Inject(at=HEAD, cancellable=true)`；无 `§[rrs:`（且无短别名）时**不 cancel** | 消费标记、逐字改写 `Style`（颜色）、把"这个字要不要位移"通过 duck 接口写到 sink 上 | 不画字、不改宽度、不改原版 § 码语义 |
| **L1** | `net.minecraft.client.resources.language.ClientLanguage#getVisualOrder(Lnet/minecraft/network/chat/FormattedText;)Lnet/minecraft/util/FormattedCharSequence;` | `@Inject(at=RETURN)`，**不 cancel**，包一层 `FormattedCharSequence` | 让"颜色/位移求值"发生在 `accept`（绘制时）→ 缓存 FCS 也能逐帧动画；给 T2 提供 duck 状态 | 不重排、不丢 bidi（原版 FCS 仍是唯一字符来源） |
| **T2** | `net.minecraft.client.gui.Font$StringRenderOutput#accept(ILnet/minecraft/network/chat/Style;I)Z` | `@Inject(at=HEAD/RETURN)` 用 `@Shadow float x,y` 加/减 delta；`@Implements` duck 接口存 pending 状态；`@Unique` 备份 + `finish` HEAD 兜底 | 把当前字形的位移加到 `x/y`，返回前减回（排版不变）；`underline`/`strikethrough` 自己用 `this.x`，会**跟着**偏移 | 不重写 `renderChar`（零改动）、不改 advance、不改 `Style` |

**为什么 L1 选 `@Inject(RETURN)` 而不是 cancel：**
- 原版 FCS 保留 bidi 重排、Arabic shaping、逐字顺序 —— 我们只在它的 `accept` 外套一个 sink wrapper，逐字替换 `Style`（颜色）并设置位移状态，`index` 原样透传；
- 这样不会出现"自己实现 FCS、丢掉 bidi"的风险（如果 cancel 后自己造 FCS，就必须自己处理 RTL/整形，风险高一个数量级）；
- `FormattedCharSequence` 的 `index` 只被 `StringSplitter.WidthLimitedCharSink` 内部消费（用于 `componentStyleAtWidth` 等），`Font$StringRenderOutput.accept` 完全忽略 `index` → 包 wrapper 不影响点击/hover/插入点定位（已验证 `componentStyleAtWidth`、聊天点击 hover、`font.width` 均无坑）。

**T1 的"混合委托"策略（风险控制核心）：**
- 不整段复刻原版状态机。把文本按 `§[rrs:` 标记切成若干 **gap**，每个 gap 直接调用原版 `StringDecomposer.iterateFormatted(gap, 0, currentStyle, secondStyle, wrapperSink)` —— 原版自己处理 `§` 码、代理对、孤立字符，**语义零风险**；
- `wrapperSink` 只做三件事：把 `index` 加上 gap 的偏移（保持原版索引语义）、按逐字效果改写 `Style.color`、把位移状态写到真实 sink（若它实现了 duck 接口）；
- 标记之间的样式状态由我们做一次轻量扫描维护（只认 `§` + 一个字符，和原版同样的规则）；
- fast path：`text.indexOf('§') < 0` 直接不 cancel；有 `§` 但没有 `§[rrs:` 也不 cancel（原版照旧）。
- **为什么不用 `@Overwrite`/整段复刻**：`StringDecomposer` 是所有文字的共同入口，也是其它文本 mod 的必争之地；混合委托让"非我们命名空间的文本"继续由原版处理，兼容性最好。

**实现约束（第二轮管线验证逐条核对过，必须遵守）：**

| # | 约束 | 原因 |
|---|---|---|
| C1 | gap 之间的"当前样式"必须用**同源公开 API**重算：`ChatFormatting.getByCode` + `Style.applyLegacyFormat`；`§r` 必须**原样透传 `secondStyle`** | `splitLines(String)` 直接调核心时 `first != second`；不能假设两者相同，否则 `§r` 语义错 |
| C2 | wrapperSink 转发的 `index` 必须是 **`gapStart + localIndex`（原串 raw 绝对下标）**，**不能减掉标记长度** | `splitLines(String)` 的 `charAt/substring`、`LineBreakFinder` 的 offset 累加、`FlatComponents.splitAt`、`WidthLimitedCharSink.position` 四处数学都依赖 raw 下标；索引对不上会让折行/裁剪错位 |
| C3 | 出现 `§§[` 这类"标记前后紧挨 §"的假阳性时，**整段委托原版**（不自己解析），最坏退化成今天的行为 | 边界条件不值得为它冒语义风险 |
| C4 | H1（T1）的处理函数必须是**纯逻辑**，不能依赖 `Minecraft.getInstance()` 非空、不能假设只在渲染线程 | 单人游戏里集成服务端线程也会经过它（Forge 未 patch，但线程不唯一） |
| C5 | T2 的 `accept` 没有提前 return（唯一 return 在方法末尾），HEAD+RETURN 正常路径必达；仍要加 `@Unique` 备份 + `finish()` 兜底 | 防止其它 mod 在 `accept` 里 cancel 后 `x/y` 停在偏移态（影响有界：`StringRenderOutput` 每次 `renderText` 新建） |
| C6 | RTL 判定用 `isDefaultRightToLeft()` **或** `com.ibm.icu.text.Bidi.requiresBidi`（MC 自带 ICU 51.2）；含 RTL 字符的文本放弃 L1 逐帧求值 | bidi 重排后逐字序号会变，包 wrapper 的 ordinal 会对错 |
| C7 | `accept` 里的 `renderChar` 调用若要改**相对亮度/alpha**，用 `@ModifyArgs`/`@Redirect`（参数 4=x、5=y、8..11=r,g,b,a），**不要猜局部变量表 index** | LVT 顺序随编译/版本变化，猜 index 是隐形炸弹 |

### 5.3 状态通道（duck 优先，ThreadLocal 只在"被包了一层 sink"时兜底）

- 定义客户端接口 `GlyphEffectSink`（set/clear + 一个不可变 pending 状态：`dx, dy, alpha`）；
- `Font$StringRenderOutput` 通过 `@Implements(GlyphEffectSink.class)` 实现它（mixin 加 `@Unique` 字段）；
- T1/L1 在调用 `sink.accept(...)` 前 `if (sink instanceof GlyphEffectSink s) s.rrs$setPending(state)`，调用后清除；
- T2 在 `accept` HEAD 读 pending 状态，给 `x/y` 加 delta，RETURN 减回；`StringRenderOutput` 每个 `renderText` 调用新建一个实例，即使异常中断，残留偏移也不会跨绘制调用。
- **duck 覆盖不到的地方**：`drawInBatch8xOutline` 用 lambda 包了一层 sink（`Font.java:82-108`），真实 sink 不是 `StringRenderOutput`。这里用 **scoped ThreadLocal（`set` 后 `try/finally` 清除）** 兜底 —— 只在"发现 sink 不是 duck"时启用，且严格配对，不做全局常驻状态。这样发光告示牌的描边文字也能抖动。
- **为什么不一上来就全局 ThreadLocal**：ThreadLocal 在"别的 mod 又包一层 sink / 异步渲染 / 嵌套绘制"时容易失配；duck 的"状态挂在 sink 实例上"天然与调用配对，ThreadLocal 只在必要路径（并被 try/finally 严格约束）兜底。
- **描边（8xOutline）的颜色语义**：该路径 9 遍 halo 用 `style.withColor(i)` 覆盖颜色、只有第 10 遍核心 pass 用我们的渐变 —— 这是可接受的视觉结果（发光文字的核心仍是渐变）；若要 halo 也变色，需在 `accept` HEAD 用 `@ModifyVariable(argsOnly, ordinal=0)` 重写 `Style`（P4 可选，不影响本方案成立）。

### 5.4 纯 Java 引擎（可单测，不依赖 MC 启动）

| 组件 | 职责 | 是否依赖 MC 类 |
|---|---|---|
| `api/text/TextEffectIds` | 效果名/参数名/默认值常量（公共代码和文档引用） | 否 |
| `api/text/TextEffectText` | 给 Java 作者构造带标记字符串的 builder（`gradient(...)`/`shake(...)`/`close()`），双端安全 | 否 |
| `client/rend/text/effect/TextEffectParser` | 扫描 `§[rrs:…]` → `EffectPlan`（span 列表、逐字通道、错误列表） | 否（纯 Java） |
| `client/rend/text/effect/EffectPlan` | 不可变计划：逐字 `EffectChar`（原始下标、颜色、位移参数、span 归属） | 否 |
| `client/rend/text/effect/EffectColor` | 线性光插值、HSV、调色板、参数 clamp | 否 |
| `client/rend/text/effect/EffectMotion` | shake/wave/glitch 的确定性函数（同一 `(i,t)` 结果稳定，阴影/多遍绘制一致） | 否 |
| `client/rend/text/effect/TextEffectClock` | 每帧采样一次时间（默认墙钟，可配游戏时间） | 是（`Minecraft`） |
| `client/rend/text/effect/TextEffectPlanCache` | LRU 缓存（key = 文本 + 起始偏移），上限可配 | 否 |
| `client/rend/text/TextEffectDecomposer` | T1 的实际解析逻辑（混合委托） | 是（`Style` 等原版类，但可分单元测试用 Mock 不必要 —— 纯函数部分抽到 Parser） |
| `client/rend/text/TextEffectSettings` | 配置兜底读取（仿 `VerdictTuning`） | 是 |
| `client/rend/text/effect/GlyphEffectSink` | duck 接口 | 是（`@OnlyIn(CLIENT)`） |

### 5.5 缓存、时间源、性能预算

- **plan 缓存**：`LRU 256`，key = 文本内容 + 起始偏移（`String` 的 `equals/hashCode` 足够；缓存都是不可变对象）。热文本（tooltip 每帧重建、聊天行复用）都能命中。
- **颜色缓存**：静态渐变（`grad`/`solid`）逐字颜色与时间无关 → 直接在 plan 里存 RGB；动画类（`rainbow`/`pulse`/`glitch` 色偏）逐帧算，用"每帧调色板缓存"减少 `Style`/`TextColor` 分配。
- **动画预算**：`maxAnimatedGlyphs`（默认 512/帧）——超出部分自动降级为静态色/无位移，并计数到 `/rrsfx stats`，防止恶意长文本卡帧。
- **时间源**：默认**墙钟**（`System.nanoTime`）。理由：tooltip/界面在单机暂停时也要好看；项目现有 `FlowingNameColors` 用 `gameTime+frameTime` 会在暂停时冻住（`FlowingNameColors.java:132-137`）。配置可切 `GAME_TIME`。
- **目标开销**：普通文本 fast path ≈ 一次 `indexOf('§')`；带效果文本的解析 + 逐字上色预估 < 300µs/帧（调研报告 §12 估算），上线前用 F3/Spark 实测。

### 5.6 覆盖矩阵：哪些文本会生效

| 文本路径 | 生效？ | 说明 / 证据 |
|---|---|---|
| 物品 tooltip（名字、lore、特性行） | ✅ | `Component` 路径，且 tooltip 每帧重建（`ItemTooltipEvent`） |
| 物品名（背包/快捷栏/铁砧界面） | ✅ | `GuiGraphics.drawString(Component)` → L1 |
| 实体名牌 / BOSS 名 | ✅ | `EntityRenderer.renderNameTag` 直接 `font.drawInBatch(Component)`（`EntityRenderer.java:83-105`）；bossbar 同理 |
| 聊天栏 / 命令反馈 / 动作栏 | ✅（逐帧动画也行） | 聊天行 FCS 缓存被 L1 的逐帧求值覆盖 |
| 告示牌 / 成书 / 标题/副标题 / advancement toast | ✅ | `SignRenderer`（split→L1）、`GuiGraphics` 系列 |
| 我们自己的 GUI（魔法星图/难度屏/创造栏标题） | ✅ | 都是 `drawString(Component/String)`；想完全可控也可继续用现有逐字画法 |
| `GuiGraphics.drawString(Font, String…)` 的 String 路径 | ✅ | T1 在 `renderText(String)` 的解析处生效（`Font.java:142-146`） |
| EditBox / 命令补全等用 `FormattedCharSequence.forward` 的原始字符串 | ❌（不解析 §） | 原版本来就不解析；且玩家输入 § 会被原版断连，不建议依赖 |
| 其它 mod 用自己的渲染器画的文本（如 ModernUI 自有文本引擎） | ⚠️ 视实现 | 只要最终走原版 `Font` 就生效；自己发顶点的看不到 |
| 玩家在聊天里手打 § | ❌ | 原版会断连（`ChatFormatting` 输入校验）；标记只能由 mod/命令/datapack/lang 写入 |

---

## 6. 项目集成

### 6.1 包结构与文件清单

```text
src/main/java/org/bytechen/rottenruins/
├── api/text/
│   ├── TextEffectIds.java              新增（纯常量：效果名/参数名/默认值文档用）
│   └── TextEffectText.java             新增（纯 Java 字符串 builder，双端安全）
├── client/rend/text/                   现有 FlowingNameColors/TooltipLines 所在包
│   ├── effect/
│   │   ├── TextEffectParser.java        新增（纯 Java：标记 → EffectPlan）
│   │   ├── EffectPlan.java              新增
│   │   ├── EffectColor.java             新增
│   │   ├── EffectMotion.java            新增
│   │   ├── GlyphEffectState.java        新增（不可变：dx,dy,alpha）
│   │   ├── GlyphEffectSink.java         新增（duck 接口，@OnlyIn CLIENT）
│   │   ├── TextEffectClock.java         新增
│   │   ├── TextEffectPlanCache.java     新增
│   │   └── TextEffectSettings.java      新增（配置兜底）
│   ├── TextEffectDecomposer.java        新增（T1 的解析逻辑）
│   └── TextEffectVisualOrder.java       新增（L1 的 wrapper FCS）
├── client/gui/TextEffectPreviewScreen.java   新增（演示屏；仿 DifficultySelectScreen 的并排样式）
├── client/TextEffectClientCommands.java      新增（/rrsfx，客户端命令）
├── mixin/
│   ├── TextEffectStringDecomposerMixin.java  新增（T1）
│   ├── TextEffectClientLanguageMixin.java    新增（L1）
│   └── TextEffectStringRenderOutputMixin.java 新增（T2）
├── config/data/SplendidingConfig.java    修改（追加 §7.2 字段；不动旧字段）
└── event/ClientModEventHandler.java      修改（注册客户端命令/屏幕；3 行）
src/main/resources/rotten_ruins.mixins.json  修改（client 段 +3 项）
src/test/java/…/text/…                       新增（解析/颜色/运动/预算单测；或放 tools/ harness）
docs/text-effects.md                         新增（实机结项后写，模板见 §6.4）
CHANGELOG.md / CHANGELOG.en.md               修改（[未发布] — § 文本特效系统）
```

### 6.2 配置（追加进 `SplendidingConfig`）

> 项目约定：新键不需要迁移（Jankson 反序列化时缺失字段保留类内默认值）；**只有改已发布字段的默认值才 bump `CONFIG_VERSION` + 写迁移**（`config/ConfigHelper.java:39-196`）。

```java
// ── § 文本特效 ────────────────────────────────────────────
@Comment("§ 文本特效总开关。false = 标记被剥离、按普通文本渲染（不会露出 rrs:… 字样），但不上色/不位移。")
@ConfigEntry.Gui.Tooltip
public boolean textEffectsEnabled = true;

@Comment("安全旁路：false = 三个文本 mixin 全部不接管，等价于没装本功能（整合包排障用）。")
@ConfigEntry.Gui.Tooltip
public boolean textEffectsFontHooksEnabled = true;

@Comment("全局强度 0~2 倍率。1.0 = 默认；0 = 只剩基础色。")
@ConfigEntry.Gui.Tooltip
public float textEffectIntensity = 1.0f;

@Comment("全局速度倍率。1.0 = 默认。")
@ConfigEntry.Gui.Tooltip
public float textEffectSpeed = 1.0f;

@Comment("时间源：WALL（墙钟，暂停时也动，推荐）/ GAME（世界时间，暂停时冻结）。")
@ConfigEntry.Gui.Tooltip
public String textEffectClock = "WALL";

@Comment("每帧参与动画的字形上限；超出自动降级为静态，防止超长文本卡帧。")
@ConfigEntry.Gui.Tooltip
public int textEffectMaxAnimatedGlyphs = 512;

@Comment("可选短别名（§g/§h/…）。默认关：裸单字母与 Bedrock 材质色码/多个文本 mod 冲突。")
@ConfigEntry.Gui.Tooltip
public boolean textEffectShortAliases = false;
```

读取一律走 `TextEffectSettings`（holder 为 null / 抛异常 / 值非法 → 回默认值；仿 `VerdictTuning`）。逐效果开关（`shake`/`wave`/... 各一个 `enabled`）按需追加。

### 6.3 演示与调试入口

项目现状是"服务端调试命令 + runClient 截图"（`event/ForgeEventHandler.java:115-125`；`run/logs/latest.log:1`）。文本特效是**纯客户端**渲染，因此：

- **客户端命令 `/rrsfx`**（`RegisterClientCommandsEvent`，Forge 47 提供；项目此前没用过，但这是客户端专属功能最直接的入口，不新增网络包）：
  - `screen` 打开 **文本特效预览屏**（每行一个效果 + 动画样本 + 当前参数 + 语法提示 + 性能计数 + 总开关）；
  - `chat <text>` 在本地聊天栏发一条自己解析的文本（多人服务器也可用，绕过服务端）；
  - `on`/`off` 运行时切换总开关（对照测试用）；
  - `stats` 打印解析次数、缓存命中、失败次数、当前动画字形数；
  - `reload` 重读配置。
- **备选（若你更想贴合项目现有模式）**：服务端命令 `/rrstext demo` + S2C 空包开屏（项目已有 `S2COpenDifficultySelectPacket` 模式可抄）。两种都行，推荐前者（少一个包）。

### 6.4 lang、datagen、文档、CHANGELOG

- **lang**：新增键走 `datagen/gen/lang/LangDataCN.java` / `LangDataEN.java`（`DataGenEvent.java:24-25`），跑 `.\gradlew.bat runData` 重新生成 `src/generated/resources/assets/rotten_ruins/lang/*.json`，**不手改 generated**（`docs/crimson-vow-tooltip.md:288-295`）。建议键：`rotten_ruins.textfx.effect.<name>`、`rotten_ruins.textfx.demo.<...>`、`rotten_ruins.gui.textfx.*`、`rotten_ruins.command.rrsfx.*`。
- **文档**：`docs/text-effects.md` 套 `docs/creative-tab-sections.md` 的骨架 + `docs/crimson-vow-tooltip.md` 的"涉及文件/坑/验证程度"三节；必须写清"哪些路径生效/不生效、8xOutline 限制、跨行限制、RTL 限制、别名撞名风险"。
- **CHANGELOG**：中英各加 `## [未发布] — § 文本特效系统`（列出语法、效果清单、配置、演示命令、已知限制）。
- **提交**：`feat(text): § 分节符文本特效系统（渐变/彩色/抖动/波浪/故障）`，后续按阶段拆分提交。

### 6.5 与现有文本系统的关系（零回归）

- **不改** `FlowingNameColors` / `FlowingNameTooltipHook` / `TooltipLines` / `TooltipStyleHook` / `TooltipShaders` / `ClientBlockBarTooltip`（验收 G5）；
- 物品名渐变继续走现有 `IFlowingName`；新系统负责"新的写法"；
- 后续（P4 可选）：把 `TooltipLines.feature(...)` 的静态渐变换成 `TextEffectText.gradient(...)`，行为一致才做；`FlowingNameColors` 不动，避免外部 API 变更。

### 6.6 上线后第一批落地点（建议，安全清单）

| 落点 | 建议写法 | 理由 |
|---|---|---|
| 关键物品的 tooltip lore（绯红誓约、裁决之剑、死亡之星等） | 特性标题用 `§[rrs:grad:…]`，警示行用 `§[rrs:shake]` | tooltip 是效果主战场，且是 `Component` 路径；lang 走 datagen，不影响逻辑 |
| 难度名/难度公告（`HallDifficultyEventHandler.java:97-103`） | "无法理解"档用 `§[rrs:glitch]` 替代现在的 `§k` 组合 | 现在就是恐怖文本，替换收益最大；只是显示字符串，不参与逻辑 |
| 命令反馈标题（`command/*` 的 `═══` 行） | 标题行 `§[rrs:grad]`、错误行保持 `§c` | 影响面小、纯显示 |
| 魔法星图 / 难度选择屏 / 创造栏分节标题 | `drawString(Component)` 的标题 | 每帧重绘，动画自然 |
| 演示屏与 `/rrsfx` 自带文案 | 全部效果各一行 | 验收与排障入口 |
| **不要放**：物品/方块的注册显示名、实体名、任何用于比较/查表的字符串 | —— | `CollapsarBossBar` 靠显示名匹配（`CollapsarBossBar.java:59-61, 114-118`）；铁砧/选择器/其它 mod 也按名字取文本 |

---

## 7. 分阶段实施计划

| 阶段 | 内容 | 预估 | 验收 | 可回滚点 |
|---|---|---|---|---|
| **P0（本次）** | 调研 + 方案 + 动画预览（GIF/HTML） | 已完成 | 你拍板 | — |
| **P1 纯引擎** | Parser/Plan/Color/Motion/Clock/Cache + 单测（JUnit 5 或 `tools/` harness）；不接游戏 | 1–2 天 | 单测全绿：标记解析/零宽断言/嵌套/畸形/参数 clamp/颜色插值/确定性 | 纯新增文件 |
| **P2 颜色层接入** | T1 + L1（**先不做 T2**）+ 配置 + 预览屏 + `/rrsfx`；只上颜色类效果（grad/solid/rainbow/pulse） | 1–2 天 | G1/G2/G3/G5 通过；tooltip 宽度/折行不变；聊天缓存行也能动 | 配置 `textEffectsFontHooksEnabled=false` 一键旁路 |
| **P3 位移层** | T2 + duck/ThreadLocal 状态通道 + shake/wave/glitch + 预算/计数 + `/rrsfx stats` | 1–2 天 | G4 通过；位移不改排版；阴影/粗体/下划线跟随；8xOutline 走 ThreadLocal 兜底；性能实测达标 | 同上 + 逐效果开关 |
| **P4 收尾** | 配置全字段/文档/CHANGELOG/lang/演示屏打磨；跨行续传（`Font.split`/`splitLines` carry scope）、`type`、短别名、`glitch` 颜色公式等可选 | 1–2 天 | 文档/验收 checklist 全过 | 单效果开关 |
| **P5 可选** | 逐字缩放旋转/发光、halo 变色、RTL bidi 支持、其它文本 mod 兼容、服务端广播演示 | 视需求 | — | — |

> 每个阶段都独立可交付：P2 结束就能在 tooltip/聊天/名牌上看到渐变；P3 结束才有抖动。若 P3 出问题，回退只用 P2 的颜色层即可。

---

## 8. 验证计划

### 8.1 单元测试（不启动游戏）

1. **解析**：`§[rrs:grad:#…:#…]` → plan 中 span 数/颜色数/逐字通道正确；嵌套、`§[/]`、`§[/grad]`、未闭合、未知效果、超长标记、参数越界、空文本、纯 `§` 文本、代理对文本。
2. **零宽**：对任意样例，`去标记后的可视字符序列` == `无标记文本的字符序列`，且每个字符序号一致（这是 G2 的直接断言）。
3. **颜色**：线性光插值端点精确、单调、中间不发灰；HSV 环绕正确。
4. **运动**：同 `(i,t)` 结果确定（多遍绘制一致）；`amp=0` 时恒等于原坐标；时间取模不丢精度。
5. **缓存/预算**：LRU 命中与淘汰；超预算降级为静态。
6. **错误路径**：任何非法输入不抛异常、返回"原样文本"。

### 8.2 离线视觉验证（可选，低成本）

仿 `tools/shader-preview/` 的先例做一个 `tools/text-effect-preview/`：用纯 Java 引擎 + AWT 字体把 8 个效果渲染成 PNG/帧序列 —— 不启动 Minecraft 就能对比公式与心情。**注意**：它验证的是"颜色/运动数学"，不是 MC 像素字体观感（最终以游戏内为准）。

### 8.3 实机验证清单（`runClient`）

按项目现有做法：`.\gradlew.bat compileJava` → `.\gradlew.bat runClient --quickPlaySingleplayer MagicTest` → 游戏内触发 → `F2` 截图到 `run/screenshots/`（`run/logs/latest.log:1` 就是这种启动方式的证据）。

1. **零改动**：不含标记的界面（主菜单/背包/tooltip/聊天）与改动前一致（人眼 + 截图对比）；
2. **原版码**：`§a/§c/§k/§r`、中文、emoji、超长文本正常；
3. **tooltip 宽度**：带标记的长 lore 与不带标记时 tooltip 面板尺寸/折行完全一致（这是最容易翻车的地方）；
4. **每个效果**：tooltip / 名牌 / 聊天 / bossbar / 告示牌 / 魔法星图标题各来一遍；
5. **缓存动画**：聊天栏发一条彩虹文本，等 10 秒确认仍在动（验证 L1）；
6. **描边告示牌**：发光告示牌（8xOutline）颜色生效、位移通过 ThreadLocal 兜底生效（核心 pass 渐变、halo 色由原版覆盖，记录语义）。
7. **开关**：`/rrsfx off` 后标记被剥离且不上色；`textEffectsFontHooksEnabled=false` 重启后完全等价于无此功能；
8. **性能**：F3 + Spark；`/rrsfx stats` 计数在预期范围（普通文本 0 次接管，带效果文本每帧有限）;
9. **兼容**：若 `run/mods` 里当前有 Embeddium/Oculus/ModernUI，跑一遍；再临时装一个已知文本 mod（Phoenix/Chat Colours）验证"不抢别人的命名空间"（可选）；
10. **回归 checklist**：`build/research/conventions.md` §8 的 20 条逐条勾（FlowingNameColors/底板/条子/创造栏/星图/难度屏/名字比较语义/时间源…）。

### 8.4 验收门槛

- 编译通过、单测全绿；
- G1–G7 全过；
- 实机清单 1–8 项全过（9 项按可用环境尽力）；
- 性能增量 < 300µs/帧，且不影响 60fps 的 GUI。

---

## 9. 已知限制（诚实清单）

1. **跨行/跨段不连续**：效果 span 不跨 `Component` 子段或 `Font.split` 的换行；折行后下一行从自身段落重新计算（P4 可选：在 `Font.split` / `StringSplitter.splitLines` 用 scoped ThreadLocal 把"行末效果状态"带到下一行，已验证可行，成本约一个 hook + 状态序列化）。
2. **RTL 语言/含 RTL 字符的文本回退**：为保证 bidi 正确性，这类文本不走 L1 的逐帧求值（判定用 `Bidi.requiresBidi`，比"语言是 RTL"精确）；颜色仍可能静态生效、位移不生效。
3. **`drawInBatch8xOutline` 描边路径**：该路径用 lambda 包了一层 sink，duck 拿不到；用 scoped ThreadLocal 兜底后**位移生效**；颜色方面 halo 9 遍被原版 `withColor(i)` 覆盖、只有核心 pass 显示渐变（可接受；要 halo 也变色需额外 `@ModifyVariable`，P4 可选）。
4. **玩家不能手打 §**：原版会断连；标记只能由 mod/命令/datapack/lang 写入。
5. **无 mod 客户端** 会看到 `rrs:grad:…]` 字样（原版吞掉 `§[`）；本项目为内容 mod（客户端必须装），实际不会发生。
6. **`ChatFormatting.stripFormatting` 不剥离我们的标记**：个别"先剥色再显示"的原版路径（如关闭聊天颜色 `ComponentRenderUtils.stripColor`）里，我们的标记会残留并继续生效 —— 影响很小，P4 可 mixin 一并剥离。
7. **`Font.plainSubstrByWidth` 等"纯字符串裁剪"路径**不解析标记（原版就不解析）；这类路径只用于 EditBox 等，不建议在那里用效果标记。
8. **文本内容会带标记**：`Component.getString()` 含标记；不要用在名字比较/铁砧/选择器/正式注册名上（见 §4.6 注意）。
9. **别名撞名风险**：短别名默认关；开启后与 Bedrock 材质色码/其它文本 mod 的字母可能互抢，属已知取舍。
10. **动画始终在动**（默认墙钟）：暂停界面/悬停 tooltip 里的效果仍然动。想"暂停即停"可切 `GAME_TIME`。
11. **`glitch` 的颜色通道偏移在纯白字上不可见**（白字 R/B 交换仍是白、+cyan 被 clamp）——预览里已复现；实际使用建议叠一层彩色（`grad`/`solid`）或后续改成乘法混色公式。

---

## 10. 风险与回退

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| mixin 与其它文本 mod/优化 mod 冲突 | 中 | 效果失效/文字异常 | 只处理 `rrs` 命名空间；fast path 不 cancel；三个 hook 都有配置旁路；上线前测 Embeddium/Oculus/ModernUI + 至少一个文本 mod |
| 解析层在边界文本上出错（代理对/孤立 §） | 低 | 个别文本显示异常 | **混合委托**：gap 交回原版处理；大量边界单测；出错 catch 后走原版 |
| 逐帧动画导致帧率下降 | 中 | 卡顿 | plan 缓存 + 每帧调色板 + `maxAnimatedGlyphs` 预算 + 降级静态 + stats 监控 |
| tooltip 宽度变化 | 低（标记零宽且测量走同一解析） | 面板压字 | G2 单测 + 实机长文本对照 |
| 历史事故重演（文字消失） | 低 | 严重 | 不重写 `renderChar`/`BakedGlyph`；不改任何顶点；T2 只加 delta；异常 → 原版路径；`textEffectsFontHooksEnabled=false` 完全旁路 |
| 配置/版本迁移问题 | 低 | 老配置不生效 | 只加新键（不需要迁移）；读取走兜底；若以后改默认值再 bump `CONFIG_VERSION` |
| 其它平台/版本（1.21+/Fabric） | — | — | 本方案绑定 1.20.1/Forge；升级时 hook 点会变，文档记录依赖点 |

---

## 11. 需要你拍板的点

1. **语法风格**：接受 `§[rrs:…]` 命名空间语法吗？（推荐：接受；另提供默认关闭的短别名 `§g` 等）
2. **生效范围**：全游戏文本（推荐，覆盖名牌/聊天/告示牌/其它 mod 走原版 Font 的文字）还是先只做本 mod 自己的文本（方案 A，风险更低但覆盖窄）？
3. **效果清单**：v1 的 `grad/solid/rainbow/pulse/shake/wave/glitch` 够吗？要不要一开始就带 `type`（打字机）？
4. **默认配色**：腐化绿 `#6FBF4A` → 王庭血红 `#8B1E2D`；苍白火 `#E8E4D8`；彩虹饱和度 0.72 —— 是否符合王庭的调性？
5. **入口**：客户端命令 `/rrsfx` + 预览屏（推荐）还是服务端命令 + S2C 开屏（更贴项目现有模式）？
6. **默认开关**：装好即全局开启（推荐，带一键关闭），还是默认关、由你手动开？
7. **优先级**：先做颜色层（P2 就能看到渐变/彩色）还是一口气做到抖动（P3）？我可以按 P1→P2→P3 顺序推进，每阶段给你截图确认。

---

## 12. 实施记录（第一版，2026-10-31）

### 12.1 已落地文件

| 模块 | 文件 |
|---|---|
| 公共语法/构建器 | `api/text/TextEffectIds.java`、`api/text/TextEffectText.java` |
| 纯引擎（无 MC 依赖） | `client/rend/text/effect/{EffectSpec, EffectSpan, EffectPlan, TextEffectParser, EffectColor, EffectMotion}.java` |
| 客户端运行时 | `client/rend/text/effect/{TextEffectClock, TextEffectSettings, TextEffectStats, TextEffectDetector, TextEffectPlanCache, GlyphEffectState, GlyphEffectSink}.java`、`client/rend/text/{TextEffectDecomposer, TextEffectVisualOrder}.java` |
| mixin | `mixin/{TextEffectStringDecomposerMixin, TextEffectClientLanguageMixin, TextEffectStringRenderOutputMixin}.java` + `rotten_ruins.mixins.json` client 段 3 项 |
| 配置 | `config/data/SplendidingConfig.java` 末尾 6 个 `textEffect*` 字段 |
| 演示 | `client/TextEffectClientCommands.java`（`/rrsfx`）、`client/gui/TextEffectPreviewScreen.java` |
| 测试文本 | `datagen/gen/lang/LangDataCN/EN` 的 `INF_ENDER_PEAR` 显示名 + 4 个 textfx lang 键（generated json 已同步） |
| 文档 | 本文件、`docs/proposals/text-effects-preview/`、`CHANGELOG.md` / `CHANGELOG.en.md` |

### 12.2 已完成的验证

- `.\gradlew.bat compileJava` **通过**（仅项目既有 deprecation 警告，无 error）；`build/tmp/compileJava/compileJava-refmap.json` 含三条 mixin 的映射。
- 纯 Java 核心自测 **39/39 通过**（解析/零宽/嵌套/`§r`/代理对/未知效果降级/颜色端点与彩虹范围/位移确定性/构建器往返）。
- `runClient --quickPlaySingleplayer MagicTest`：无 mixin 应用错误、无崩溃；`INF_ENDER_PEAR` 的显示名在 HUD 上呈"崩坏"位移 —— 截图 `run/screenshots/2026-10-31_11.46.13.png`（修复后第二轮，无 `[TextFX]` 失败日志）。
- **首轮实机暴露并已修复的 bug**：`ClientLanguage#getVisualOrder` 的 `@Inject(RETURN)` 若不带 `cancellable = true`，`cir.setReturnValue(...)` 会抛 `CancellationException`（被我们的 try/catch 吞掉 → 退回原版，不崩溃但特效不生效）。修复后该日志消失。这正是"任何 hook 失败都退回原版、文字不会消失"设计生效的实例。

### 12.3 尚未实机确认 / 遗留

- **彩色类效果与预览屏**未能在本次自动化环境完成截图确认（模拟键盘输入受窗口焦点/输入法影响，`/rrsfx` 命令的回放不可靠）。请在游戏里手动执行 `/rrsfx screen` 或 `/rrsfx chat &[rrs:rainbow]Color test&[/]` 目视验收。
- P4 可选项（跨行续传、`type` 打字机、短别名、halo 变色、`glitch` 色偏公式）未做。
- 独立审查报告（如存在）在 `build/research/implementation-review.md`，优先级修复按它的结论处理。

### 12.4 手动验收清单（1 分钟）

```text
1) 进游戏看快捷栏：选中「畸骸末影珍珠」→ 名字应在抖/崩坏（即本次用户要求的测试）。
2) /rrsfx screen          → 预览屏 8 行应分别显示渐变/纯色/彩虹/呼吸/抖动/波浪/故障/组合。
3) /rrsfx chat &[rrs:grad:#6FBF4A:#8B1E2D]测试渐变&[/] → 聊天栏该行应为绿→红渐变。
4) /rrsfx off             → 同一条消息应退回普通颜色；/rrsfx on 恢复。
5) /rrsfx stats           → 能看到解析次数/缓存命中/失败计数（失败应为 0）。
```

### 12.5 第二批：现有文本效果迁移（2026-10-31）

| 原实现 | 迁移为 | 降级策略 |
|---|---|---|
| `CreativeTabDividerRenderer` 逐字 HSV 彩字（`drawFlowingText` + 两个常量） | 一次 `drawString(Component)` + `§[rrs:rainbow:sat=0.45:speed=1.3]` | 前缀 `§6`，特效关闭时仍金色 |
| 难度「无法理解」`§c§k!!` 假乱码（`HallDifficultyEventHandler`） | `§4§[rrs:glitch:amp=2.0]无法理解§[/]` | 前缀 `§4` |
| `/anomaly` 状态头、`/collapse types` 的 `═══ … ═══` | `§6` + `TextEffectText.gradient(…, 0x6B4A0A, 0xFFD700, 0.3F)`（流动） | 前缀 `§6` |
| `MagicScreen` 标题（纯金） | `§6` + `TextEffectText.gradient(title, 0xFFE7C46A, 0x9A6A00, 0.35F)`（流动） | 前缀 `§6` |
| datagen 文案：难度名/难度提示、成就标题/说明 | 难度/成就均为"单个 Component 直接绘制"，可安全带标记 | 无前缀的组合会随标记剥离回到原版绘制色 |
| 创造栏标签页 / 左上角面板标题（`itemGroup.rotten_ruins.main`，"腐朽之疫"/"Rotten Ruins"） | `§6` + `§[rrs:grad:…:speed=0.6]` 流动渐变 | 前缀 `§6` |

**没有迁移的部分（有意）**：
- `FlowingNameColors` / `TooltipLines` 逐字拍平的 lore/风味行：逐字重建会把标记拆散到不同 Component 里，反而显示成 `§[rrs:…` 原文 —— 要迁移必须先改那套逐字机制，属于 P4。
- `ClientBlockBarTooltip`（带 alpha 的条子文字）、`TooltipShaders`（背景着色器）、物品/实体的正式显示名（身份/匹配用）。

**同时修正的降级语义**：`textEffectsEnabled=false` 现在只关闭"上色/位移"，mixin 仍然会剥离标记（`TextEffectSettings.hooksEnabled()` 与 `enabled()` 分离）—— 否则关闭特效会把 `rrs:…` 字样暴露出来；`textEffectsFontHooksEnabled=false` 才是"完全等价于没装本功能"的排障旁路。
```

---

## 附录 A：三个 mixin 的目标方法描述符（official 映射）

| Hook | 目标类 | 方法 | 描述符 |
|---|---|---|---|
| T1 | `net.minecraft.util.StringDecomposer` | `iterateFormatted` | `(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z`（另有 `(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z` 与 `(Lnet/minecraft/network/chat/FormattedText;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z` 两个入口，最终都汇到核心，注入核心即可覆盖） |
| L1 | `net.minecraft.client.resources.language.ClientLanguage` | `getVisualOrder` | `(Lnet/minecraft/network/chat/FormattedText;)Lnet/minecraft/util/FormattedCharSequence;` |
| T2 | `net.minecraft.client.gui.Font$StringRenderOutput` | `accept` | `(ILnet/minecraft/network/chat/Style;I)Z`（`@Shadow float x; float y;`） |

> T2 的 inner 类用 `@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")` 是可靠的（Mixin 0.8.5 明确支持 package-private/private inner；项目现有 refmap 已证明字段映射格式可用，`x/y → f_92948_/f_92949_`）。`renderChar` **零改动**。

配置：三者都进 `rotten_ruins.mixins.json` 的 `"client"` 段；现有 `ItemGlintMixinPlugin.shouldApplyMixin` 对未知 mixin 返回 `true`（`mixin/ItemGlintMixinPlugin.java:21-32`），不需要改插件。Mixin 0.8.5（无 MixinExtras，`build.gradle:167`），只用原生 `@Inject/@Shadow/@Implements`。

## 附录 B：语法速查（可贴给文案作者）

```text
开始：§[rrs:<效果>[:参数…]]
结束：§[/]            或  §[/<效果>]
重置：§[rrs:reset]     （同时会清空自定义效果；§r 也会）

grad    §[rrs:grad:#8B1E2D:#FFB300]文字§[/]          ← 静止
grad流  §[rrs:grad:#8B1E2D:#FFB300:speed=0.4]文字§[/] ← 颜色流动
solid   §[rrs:solid:#E8E4D8]文字§[/]
rainbow §[rrs:rainbow:speed=1.0:sat=0.72]文字§[/]
pulse   §[rrs:pulse:color=#E8E4D8:amp=0.35:speed=1.0]文字§[/]
shake   §[rrs:shake:amp=1.0:freq=2.0]文字§[/]
wave    §[rrs:wave:amp=1.2:len=12.0:speed=1.0]文字§[/]
glitch  §[rrs:glitch:amp=2.5:rate=12.0:prob=0.10]文字§[/]

嵌套示例：
§[rrs:grad:#8B1E2D:#FFB300]王庭§[rrs:shake]的§[/]腐朽§[/]
```

## 附录 C：证据与参考

**原版源码（与项目锁版本一致）**：`forge-src!`（见 §2 开头）中的 `StringDecomposer.java`、`Font.java`、`ClientLanguage.java`、`Language.java`、`SubStringSource.java`、`FormattedBidiReorder.java`、`FormattedCharSequence.java`、`MutableComponent.java`、`StringSplitter.java`、`GuiGraphics.java`、`ComponentRenderUtils.java`、`SignRenderer.java`、`EntityRenderer.java`、`ChatComponent.java`、`BossHealthOverlay.java`、`PlayerTabOverlay.java`、`ChatFormatting.java`。

**项目文件**：`client/rend/text/*`、`client/tooltip/*`、`client/gui/*`、`client/rend/gui/CollapsarBossBar.java`、`config/*`、`event/ClientModEventHandler.java`、`event/ForgeEventHandler.java`、`mixin/*`、`rotten_ruins.mixins.json`、`build.gradle`、`src/generated/resources/assets/rotten_ruins/lang/*.json`。

**外部先例（详见 `build/research/prior-art.md`）**：
- [Phoenix's Chromatic Codes（Forge 1.20.1，动画渐变/波浪/抖动；MIT）](https://modrinth.com/mod/phoenixs-chromatic-codes) · [源码](https://github.com/P-H-O-E-N-I-X-PackForge/PhoenixChromaticCodes)（其 `MixinStringDecomposer`/`MixinStringRenderOutput` 与本方案 T1/T2 同思路，是可行性的一手证据）
- [Embers Text API](https://modrinth.com/mod/embers-text-api) · [架构文档](https://tysontheember.dev/embers-text-api/concepts/how-eta-works/)
- [Chat Colours](https://modrinth.com/mod/chat-colours)（`§[name]` 命名码先例）
- [Minecraft Wiki: Formatting codes](https://minecraft.wiki/w/Formatting_codes) · [Adventure Legacy 序列化（§x 是生态约定不是原版）](https://docs.papermc.io/adventure/serializer/legacy/)
- [mappings.dev 1.20.1](https://mappings.dev/1.20.1/net/minecraft/client/gui/Font.html)
- Embeddium/Sodium 系对 `BakedGlyph`/`FontSet` 的既有 mixin（本方案刻意避开这两处）

## 附录 D：方案预览产物（`docs/proposals/text-effects-preview/`）

| 文件 | 内容 |
|---|---|
| `preview-sheet.png` | 8 个效果 + 组合示例的静态联络表（一眼看全；1580×1426） |
| `gradient.gif` / `rainbow.gif` / `pulse.gif` / `pale.gif` / `shake.gif` / `wave.gif` / `glitch.gif` / `typewriter.gif` / `combo.gif` | 每个效果的动画预览，720×132、20fps、无限循环（`grad`/`solid` 是静止效果，所以是单帧 GIF；公式与 §4.3 完全一致） |
| `index.html` | 自包含交互游乐场：可改文本/速度/幅度、叠加多个效果、命名空间/短别名双语法提示（浏览器直接打开，无外网依赖） |
| `generate.py` | 可复现的生成脚本（Pillow；SimHei 二值化像素字 + 3× 最近邻；JS↔Python 公式已 572 项交叉验证） |
| `README.md` | 预览说明 + 公式表 + 参数来源 + 帧数/循环接缝表 + 与游戏内差异 + 免责声明 |

> 已知预览局限：① `glitch` 的颜色通道偏移在纯白字上不可见（见 §9 限制 11）；② 预览用 Windows 字体近似 MC 像素字体，游戏内字宽/标点会有差异；③ `rainbow`/`shake`/`glitch` 的 GIF 循环接缝不闭合（公式相位与预览时长不整除，不影响游戏内表现）。

> 预览由 `preview-artist` 生成，是"方案的示意图"；正式实现在 P2/P3 完成后会用 `runClient` 截图替换为实机效果图。
