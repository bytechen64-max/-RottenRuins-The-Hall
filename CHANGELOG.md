# 更新日志 (Changelog)
本文档记录了本模组的所有重要变更。

## [0.1.0] - 2026-7-22
> 兼容 Minecraft 版本: 1.20.1 | 模组加载器: Forge 

### 新增 (Added)
- **新增生物**
  - 感染玩家
  - 王庭碎骨
  
- **新增实体**
  - 陨石
  - 冲击波
  - 剑气
  - 坍缩
  
- **新增物品**
  - 感染玩家刷怪蛋
  - 王庭碎骨刷怪蛋
  - 王庭肌腱
  - 王庭骨
  - 穹顶胚晶矿
  - 穹顶胚晶
  - 穹顶云铁矿
  - 穹顶云铁
  - 云顶之剑
  - 云顶之镐
  - 云顶之斧
  - 云顶之锹
  - 云顶之锄
  - 虚空剑

- **新增方块**
  - 王庭草方块 
  - 王庭泥土
  - 王庭原木
  - 王庭树叶
  - 王庭石柱
  - 王庭石块
  - 王庭木板
  - 王庭藤蔓
  - 王庭草
  - 王庭花
  - 穹顶胚晶矿石
  - 穹顶云铁矿石
 
- **新增效果**
  - 热异常适应
  - 冷异常适应
  - 酸异常适应

- **新增生物群系**
  - 王庭群系

- **其他**
  - 为云顶系列工具添加了合成配方
  - 为当前矿物添加了熔炼配方
  - 为当前生物添加了战利品掉落表

### 修复 (Fixed)
- 无

### 已知问题 (Known Issues)
- 暂无报告，欢迎反馈

## [0.2.0] - 2026-8-29
> 兼容 Minecraft 版本: 1.20.1 | 模组加载器: Forge 

### 新增 (Added)
- **新增生物**
  - 畸骸末影人 
  - 畸骸骷髅
  - 王庭重型轰炸
- **新增物品**
  - 畸骸末影珍珠
- **新增方块**
  - 王庭石楼梯
  - 王庭石台阶
  - 王庭石墙
  - 王庭石质按钮
  - 王庭石质压力板
  - 王庭木楼梯
  - 王庭木台阶
  - 王庭木栅栏
  - 王庭木栅栏门
  - 王庭木门
  - 王庭木活板门
  - 王庭木按钮
  - 王庭木压力板
  - 王庭烬痕砂岩
  - 王庭烬痕切制砂岩
  - 王庭烬痕坍缩雕纹砂岩
  - 王庭烬痕平滑砂岩
  - 王庭烬痕沙子
  - 王庭仙人掌
  - 王庭烬痕枯灌木
- **其他**
  - 王庭群系天空改为纯白色
  - 着色器的光影兼容
### 变更 (Changed)
- **命名变更**
  - `感染玩家` → `畸骸玩家`
  - `感染玩家刷怪蛋` → `畸骸玩家刷怪蛋`
### 移除 (Removed)
  -王庭群戏感染能力
### 修复 (Fixed)

## [0.3.0] - 2026-9-27
> 兼容 Minecraft 版本: 1.20.1 | 模组加载器: Forge

### 新增 (Added)
- **新增生物**
  - 溃烂纠察
  - 溃烂追蹤者
  - 溃烂巨岩
  - 萨米使徒
- **新增实体**
  - 黑洞
- **新增方块**
  - 血肉裂隙
- **新增维度**
  - 王庭维度
- **其他**
  - 冲击波着色器优化
  - 王庭生物具备击杀感染能力
  - 新增生物数据`威胁点数`
  - 黑洞扭曲着色器
### 修复 (Fixed)
  - 王庭草、花、仙人掌等方块特性修复
  - 模型加载崩溃修复

## [未发布] — § 分节符文本特效系统（第一版）
> 给文本加一套带命名空间的自定义分节符：`§[rrs:<效果>:<参数>]…§[/]`。
> 只影响客户端渲染，不改变文本内容/宽度/折行；标记在原版解析里天然零宽。
> 第一版包含颜色类（渐变/纯色/彩虹/呼吸）与位移类（抖动/波浪/故障），
> 演示入口 `/rrsfx`，配置项追加在 `SplendidingConfig` 末尾。

### 新增 (Added)
- **三处客户端 mixin**：`StringDecomposer`（标记解析/测量）、`ClientLanguage#getVisualOrder`（缓存 FCS 的逐帧 overlay）、`Font$StringRenderOutput#accept`（只对绘制坐标加临时偏移，不碰 `renderChar`）。
- **纯 Java 引擎**（可脱离游戏单测）：`TextEffectParser` / `EffectPlan` / `EffectColor`（线性光渐变、HSV）/ `EffectMotion`（确定性抖动/波浪/故障）/ `TextEffectPlanCache`（LRU）。
- **公共构建器** `api/text/TextEffectText`：`gradient/solid/rainbow/pulse/shake/wave/glitch` 与 `open/close`，供物品/命令/演示代码生成带标记文本。
- **演示**：客户端命令 `/rrsfx`（`screen` 预览屏 / `chat` 本地聊天测试 / `on|off` 运行时开关 / `stats` / `reset` / `help`），`chat` 支持用 `&` 代替 `§` 手打。
- **实机测试文本**：`INF_ENDER_PEAR`（畸骸末影珍珠 / Infected Ender Pearl）的显示名改为 `§[rrs:glitch:amp=2.0]…§[/]`，用于验证崩坏文本渲染。
- **配置**：`textEffectsEnabled`、`textEffectsFontHooksEnabled`（安全旁路）、`textEffectIntensity`、`textEffectSpeed`、`textEffectUseGameTime`、`textEffectMaxAnimatedGlyphs`。
- **方案文档**：`docs/proposals/custom-section-code-text-effects.md` 与 `docs/proposals/text-effects-preview/`（动画预览/交互游乐场）。

### 变更 (Changed)
- **`grad` 支持流动**：新增参数 `speed=`（`§[rrs:grad:#a:#b:speed=0.4]`），`speed>0` 时颜色沿文字随时间流动（首尾闭环无缝）；默认 `0` 保持旧内容/预览 GIF 的静止观感。`TextEffectText.gradient(text, from, to, speed)` 同步提供。
- **已迁移的显示文本改为流动**：魔法星图标题（`speed=0.5`）、`/anomaly` 状态头与 `/collapse types` 标题（`speed=0.5`）、成就「腐朽之疫」标题（`speed=0.5`）、创造栏标签页/面板标题 `itemGroup.rotten_ruins.main`（"腐朽之疫" / "Rotten Ruins"，`speed=0.6`）；创造栏分区标题继续用 `rainbow`（速度提到 2.2）。
- **流动速度口径**：`grad` 的 `speed` 直接表示"每秒走完多少个周期"（`speed=0.5` ≈ 2 秒一圈），比初版映射快 4 倍，肉眼可辨。
- **创造栏分区标题**：删掉 `CreativeTabDividerRenderer` 里逐字算 HSV 的私有实现（`drawFlowingText` 与两个常量），改为一次 `drawString(Component)` + `§[rrs:rainbow:sat=0.45:speed=1.3]`，与其它彩虹文字共用同一套实现/配置；前缀 `§6` 保证特效关闭时仍是金色。
- **难度「无法理解」档**：`HallDifficultyEventHandler.resolveDisplayName` 的 `§c§k!!` 假乱码改为 `§4§[rrs:glitch:amp=2.0]无法理解§[/]`（垫 `§4` 做降级）。
- **命令反馈标题**：`/anomaly` 状态头与 `/collapse types` 的 `═══ … ═══` 改为金字渐变（`§6` 垫底降级）。
- **魔法星图标题**：`MagicScreen#drawHeader` 改为亮金→暗金渐变（`§6` 垫底降级）。
- **datagen 文案**：难度名「无法理解」/难度提示（困难=抖动、无法理解=故障）、成就「腐朽之疫」标题（渐变）与「获得死亡之星」说明（故障）改用分节符标记。
  - **注意**：走 `FlowingNameColors` / `TooltipLines` 逐字拍平的 lore/风味行**没有**加标记 —— 逐字重建会把标记拆散到不同组件里，反而显示成原文。

### 已知问题 (Known Issues)
- 效果 span 不跨 `Component` 子段与 `Font.split` 的换行（每行从自身段落重新计算）。
- 含 RTL 字符的文本放弃逐帧 overlay（bidi 重排后逐字序号会错位），只保留静态颜色。
- `drawInBatch8xOutline`（发光告示牌描边）的 halo 颜色由原版覆盖，核心 pass 正常显示渐变。
- `glitch` 的颜色通道偏移在纯白字上不可见（位移仍生效）。
- 短别名（`§g` 之类）尚未开放，仅命名空间语法。

## [未发布] — 天穹裁决：特效与手感重做
> 面向「技能特效更帅、操作手感更强」，不改动已有的三条设计原则
> （伤害定死、覆盖吃高度、三技共享冷却池）。

### 新增 (Added)
- **统一命中反馈层 `VerdictFeedback`**：三个技能共用一个反馈入口。
  - 命中顿帧（目标闪红 + 动作被压一帧）；
  - 命中音调随命中数升高（听得出这一下打了几个人）；
  - 粒子爆发、落地冲击环、相机抖动与 FOV 冲击。
- **相机反馈系统 `VerdictCameraEffects`**：Forge 47 的
  `ViewportEvent.ComputeCameraAngles` / `ComputeFov` 两处注入，幅度压在 1.35° / 9° 以内。
- **裁决落剑实体 `VerdictSwordDropEntity` + 渲染器**：从天而降、落地插入地面的剑气。
- **突进减伤**：突进期间受到的伤害打 38%（`DomeriteLongswordBehavior`）。

### 变更 (Changed)
- **三态时间轴重排**：0.25s/0.3s 的短按窗口一律放宽到 **0.4s（8 tick）**，
  领域阈值移到 **0.7s（14 tick）**。三档之间各有一声提示音，玩家听得出自己在哪一档。
- **① 天穹裁决 · 光柱**
  - 伤害从一次性 20 hurt 改成 **两波**（24+5，随后 12+4 ≈ 45），随扫描球下落依次结算；
  - 伤害搬进 `VerdictBeamEntity`，"视觉扫到哪"与"打到哪"成为同一件事；
  - 新增落柱冲击环 + 尘土/碎块粒子 + 相机后坐；
  - 存续 12 秒 → **8 秒**，收束从"整体变淡"改成"从下往上塌陷 + 尾段亮爆"；
  - 抬头不足时不再静默作废，给出动作栏提示。
- **② 截空 · 凌空斩**
  - 拖尾从固定 3 道改成按距离铺（每 2.2 格一道，12 格 = 6 道）并延长存活时间；
  - 速度改成"起步快 34%、收尾慢 58%"的包络（均值仍为 1.0，突进距离不变）；
  - 收招保留 38% 余速，不再硬停；速度沿路重申时按 elapsed 相位取包络；
  - 命中目标的身上补一次斩击粒子 + 小冲击波；
  - **空放只记 40% 冷却且不推进连打等级**（走位/升空不再变成扣血）；
  - 撞墙时补一声钝响与短抖。
- **③ 裁决领域 · 空中剑阵**
  - 展开时沿边界落下 **7 道立柱剑**，错开落地时间，阵法有了"展开"过程；
  - 每次出剑从"3 道平行剑气"改成**从天而降的落剑**，伤害在**落地那一帧**结算
    （原本剑还在飞、怪已经掉血），一次脉冲最多锁 3 个目标；
  - 施法者离场后**停止出剑**（原本只是把光纹调暗），shader 侧同步熄火；
  - 地面光纹新增"整片呼吸"（与出剑脉冲同步的尖峰），修复熄火时振幅为负的写法。
- **shader**：光柱新增两波打击的自上而下扫描带、末段塌陷与亮爆；
  领域新增呼吸与离场熄火；两者都不采样屏幕，因此不需要新的延后回放管线。

### 修复 (Fixed)
- 领域离场判定与出剑逻辑原本是"先出剑、后判定在不在场"，
  现在先判定再决定是否出剑。
- 领域落剑与光柱伤害的归因链路补全（领域击杀计入玩家，掉落与成就照常）。

## [未发布] — 修复：登录时因属性表未初始化而崩溃

### 修复 (Fixed)
- **玩家无法进入世界（被踢回登录界面）**：`AttachCapabilitiesEvent` 是在
  `Entity` 的构造函数里派发的，那一刻 `LivingEntity` 的属性表还没被赋值，
  而 `ForgeEventHelpers` 在同一个事件里 `new TwistedPoint(player)` →
  `MagicStats.maxMana` → `RegisterAttributes.valueOf` 会去读属性表，
  于是抛 `NullPointerException`。异常从能力附加的监听器里冒出去之后，
  整次 `AttachCapabilitiesEvent` 被判失败（魔法池/法力池都没挂上），
  日志里表现为 `Couldn't place player in world`。
  现在 `RegisterAttributes.valueOf` 在属性表为 `null` 时直接返回 fallback ——
  那个时刻本来也不存在任何属性修饰器，所以返回的正是真实值。

## [未发布] — 创造物品栏：分区隔断行

### 新增 (Added)
- **带分区的创造标签 `SectionedCreativeModeTab`**：一个标签里用「隔断行」把物品切成
  若干分区，每段以一行流动色带 + 居中彩色标题开头，段尾自动补空到整行。
  - 注册侧用 `SectionedCreativeModeTab.builder().section(标题, 生成器)...` 声明，
    父类 `displayItems()` 被禁用（设了也会被覆盖，不如直接抛）。
  - 跨分区按物品去重，先声明的分区赢 —— 所以最后一个「其它」分区可以直接
    遍历整个命名空间兜底，新加的物品不会因为忘了登记而消失。
- **隔断行着色器 `hall:gui_tab_divider`**：GUI 空间里的一条极光缎带
  （横向流动的多色噪声 + 中央亮芯丝 + 上下淡出），Java 侧走
  `BufferUploader.drawWithShader` 立即绘制。着色器没加载成功时退回一条纯色细线，
  分区功能本身不受影响。
- **`CreativeModeInventoryScreenMixin`**：在原版「扁平物品表 + 5×9 取景窗」的网格里
  插入 9 个空槽当隔断行，并在 `ContainerScreenEvent.Render.Foreground` 上绘制色带与标题。
  搜索框有关键字时保持原版的扁平结果，不做分区。

### 变更 (Changed)
- **`RegisterTab` 的物品表改为分区声明**：畸骸与王庭生物 / 王庭材料 / 武器与工具 /
  王庭建材 / 其它。物品集合本身不变，只是多了一层可见的结构。
- `LangDataCN` / `LangDataEN` 新增 5 条分区标题（`itemGroup.hall.main.section.*`）。

### 说明 (Notes)
- 隔断行**只存在于客户端**：创造标签的物品表本来就只在客户端构建
  （`CreativeModeTabs.tryRebuildTabContents()` 唯一调用方是 `CreativeModeInventoryScreen`），
  所以不涉及网络同步、存档与服务端。
- `getDisplayItems()` 对外仍是那份干净的扁平物品表，JEI / 其它 mod 读到的没有变化；
  混入也只在选中分区标签时生效，其它标签零影响。

