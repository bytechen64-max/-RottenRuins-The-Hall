# Rotten Ruins 合并方案（Hall + corruption → `rotten_ruins`）

> 本文件是给项目作者做**方案选择**的决策文档；四份只读分析报告（Hall / corruption / infcore / 生物体系）作为证据附录，见文末索引。
>
> - 合并目标（用户已定）：单模组 `Rotten Ruins`，modId **`rotten_ruins`**，MC 1.20.1 / Forge 47.4.x，group `org.bytechen`。
> - 用户已定约束：重复内容不搬（粒子等）；corruption 的 KING 系列 = 本工程"王庭"系列，不搬；corruption 的寻路 AI / brain 不搬；索敌改用 infcore 前置的阵营 / 感染类型判断。
> - 结论口径：工作量"人日"按 1 人日 ≈ 6 小时有效工作估算，区间来自各分报告汇总，仅用于方案对比。

---

## 0. 一页纸结论

1. **推荐方案 A：以 Hall 为基线、分 4 个阶段合并**——先"冻结 + 改名 + 清债"（产出可玩的改名版），再"框架合一"，再"MVP 腐化内容"，最后"扩展 + 回归发布"。
2. **生物基类：合并成一个统一基类**（`AbstractRottenEntity`，沿用 Hall 基类骨架，根仍为 `Monster`）；王庭/腐化差异放到 population 家族薄层 + `RottenFaction` 数据字段，**不保留两个基类**。这也是对"分成两个基类还是合并好"的直接回答。
3. **节点系统（corruption 独有）建议不搬**：用 infcore 的 evolution 阶段 + 4 档难度 + 群系存在感 + 简化刷怪配置替代。搬它要多 10~15 人日，且会让"感染强度"和难度出现两套并行数值。
4. **阵营用单类型 `rotten_ruins:inf`**（所有本模组感染者天然不互殴），但**演化 / 扩散 type 必须拆开**（王庭/腐化各自一套），否则 fallback 与同 source 规则会互相覆盖、静默出错。
5. **旧存档明确弃档**（1.0 起新档）；可选再花 1~2 人日做 Forge `MissingMappingsEvent` 兼容映射，但**维度目录、进度、SavedData 仍无法完整迁移**，不承诺兼容。
6. **工作量估**：方案 A 首个可玩版本（MVP）≈ 45~60 人日；全量（含节点）80~100，砍节点 70~85。方案 B（一次性）65~90 但风险集中；方案 C（新工程重建）90~120。
7. **动工前必须先做**：把当前 Hall 工作区 **167 项未提交改动**（含绯红誓约、陨石事件、新粒子/新着色器等）先 commit 到分支并打 tag —— 否则后面任何改名/迁移都可能把这批最新成果卷丢。
8. **三处硬阻塞必须先拍板**：GeckoLib 版本统一（4.8.3 vs 4.7.1）、节点系统去留（见第 3 条）、旧存档策略（见第 5 条）。

---

## 1. 合并对象与目标

### 1.1 两个工程的关系（分析结论）

- **corruption**：`0.6.0`，268 个 Java / 约 2.8 万行，28 组资源左右。自研一整套框架：节点系统（空间感染强度）、Assimilation/Bearer 演化、自研方块扩散与群系处理器、自研索敌/AI/Brain、自研 capability/savedata/network、自研 glint/描边/辉光渲染。**完全不依赖 infcore**。
- **Hall（本工程）**：`1.0.0Beta`，390 个 Java / 约 6.6 万行（含大量未提交的渲染/魔法/武器新内容）。是 corruption 的**基础设施升级后继**：已接入 infcore 0.1.2（44 处 import）、infcore evolution/blockspread 数据、infcore 难度、infcore 目标选择；把 corruption 的扩散/群系处理器重写为 `swarm/*`；glint/描边/背板/着色器体系是 corruption 旧版的超集；新增维度 `hall:heall`、魔法/法力、4 档难度、分区创造栏、绯红誓约/天穹裁决/陨星事件等。
- 两工程**大量同名同源**：mixin（`GameRendererMixin`/`ItemInHandRendererMixin`/`ItemGlintMixinPlugin` 等 9 个）、网络（`UniversalPacket`/`PacketSyncKeyframe`/`EffectSyncPacket`）、渲染 helper、geo 渲染底座、datagen、capability/savedata 骨架、粒子（blood/meat/explosion_bloody 已在 Hall 重写过）。**corruption 里真正缺的是"内容"，不是框架。**

### 1.2 合并后的规模预估

| 注册类别 | Hall 现有 | corruption 现有 | 合并后（去重/删 KING 后） |
|---|---|---|---|
| 物品 | 75（42 独立 + 33 方块物品） | 89（63 独立 + 26 方块物品） | **约 140~155**（含刷怪蛋；KING 5 物品 + 5 方块物品已剔除，七罪/芯片等按 D6 再减） |
| 方块 | 33 | 26 | **54**（33 + 21，删 5 KING） |
| 方块实体 | 0 | 1（fleshbed） | **1** |
| 实体类型 | 23（10 生物 + 13 技能/投射物） | 34 注册 + 1 未注册 | **约 55**（33 只注册生物 + 技能/投射物） |
| 效果 | 4 | 6 | **10** |
| 粒子类型 | 5 | 9 | **11**（blood/meat/explosion_bloody 3 个同名保留 Hall，C 的 6 个独有粒子迁入） |
| 音效 | 2 | 10 | **11**（rip_body 复用） |
| 维度 / 群系 | 1 / 2 | 0 / 1 | 1 维度 / 2 群系（腐化群系并入 Hall 体系，见 D7） |
| Java | 390 文件 / 66k 行 | 268 文件 / 28k 行 | **约 550 文件 / 72k 行**（corruption 独有 189 类中约 160 个迁入，删 AI/框架/死代码后净增约 100 类） |

### 1.3 用户已定约束的落实方式（速查）

| 用户约束 | 本方案的落实方式 |
|---|---|
| 改变 modid 为 `rotten_ruins` | 方案 A 的 P0 阶段一次性完成 modId/包名/mixin/资源目录/手写 JSON/lang/config 迁移，并给出 12 步 checklist（§6 P0） |
| 有些物品不用搬、二者重复多（粒子等） | 精确比对结论：资源层字节级重复仅 20 文件/54KB；框架 79 对同源类中 24 对等价、21 对取 Hall；粒子 3 个同名 + 16 张相同序列帧保留 Hall；完整"不搬/去重清单"见 §4 |
| corruption 的 KING 系列 = 本工程 Hall 系列，不搬 | §4.3 单列：5 物品 + 5 方块 + HeavyGuard/Avila + 对应资源全部不搬 |
| corruption 的寻路 AI / brain 不搬 | §4.4 + D1：删 9 个 AI/Brain 文件（约 1,700 行），22 只腐化生物改用 Hall 的 `tickChase + ChaseLockGoal + FaceTargetGoal` + infcore `InfectedTargetGoal` |
| 索敌用 infcore 的阵营判断 | D2 + §5：`IInfectedEntity.getInfectionType()` 单阵营 `rotten_ruins:inf` + `InfectedTargetGoal` + `ITargetFilter`；威胁/威慑合并；`kind` 降级为数据字段；演化/扩散 type 拆分 |
| 生物类是否分两个基类 | D1：**合并成一个统一基类** `AbstractRottenEntity`（根 `Monster`），王庭/腐化差异放家族薄层 + `RottenFaction` 字段；不保留两个基类 |

---

## 2. 三个总体合并方案

### 方案 A（推荐）：Hall 基线 · 分阶段合并

**思路**：在 Hall 当前工作树上开分支 `merge/rotten-ruins`，先完成"改名 + 清债"（这一阶段结束时内容与今天的 Hall 完全等价，只是名字变成 `rotten_ruins`），再统一框架，然后按"腐化生态 MVP → 世界/演化闭环 → 扩展内容"三批接入 corruption 内容。每阶段都可编译、可进游戏、可回滚。

| 阶段 | 内容 | 产出 | 估算 |
|---|---|---|---|
| **P0 冻结 + 改名 + 清债** | 提交/打 tag 当前工作区；modId/包名/mixin/资源目录/手写 JSON/lang/config 全量迁移；清空 generated 后重跑 datagen；清历史遗留（`data/splendiding`、`data/godsick_neo` 空目录）；修 6 处阵营硬编码 + 3 处 spread 硬编码；修 `getNamespace()=="hall"` 漏网点 | `1.0.0-alpha1`（改名版 Hall，可玩） | 2~4 人日 |
| **P1 框架合一** | 网络单通道（corruption 4 类包并入并重排 id）；capability/savedata 收编 infcore；删 corruption 自研扩散/群系/演化框架；实体基类统一 `AbstractRottenEntity`（删 AI/brain/adaptation/variant/kind 死系统）；威胁/威慑合一；evolution type + blockspread type 拆分 | `alpha2`（内部开发版，无新内容） | 6~9 人日 |
| **P2 腐化内容 MVP** | 22 只腐化生物（host 11 + degenerate 4 + deteriorated 1 + refactored 4 + linked 2 + original 1，删 HeavyGuard/Avila）、6 效果、21 方块、材料/粗制工具/注射器/温床/酸液等约 40 物品、腐化扩散规则数据化、掉落/配方/语言/资源/JEI、21 个腐化刷怪蛋 | `beta1`（腐化线可玩） | 22~30 人日 |
| **P3 世界/演化/刷怪闭环** | infcore evolution 数据替代 AssimilationMapManager/BearerManager；刷怪体系（Hall 目前 SpawnPlacements 空）；腐化群系并入 Hall 扩散；"感染强度"用 WorldData 派生替代节点；补齐方块掉落/配方 | `beta2` | 8~12 人日 |
| **P4 扩展 + 回归发布** | 技能/投射物差异化部分、红矮星弓/文件系统等可选项（按 D6 决策）、光影/JEI/多人/存档回归、文档/CHANGELOG、发布 | `1.0.0` | 7~10 人日 |

- **合计**：MVP 首个可发布 ≈ **45~60 人日**；全量（无节点）≈ **70~85**；全量（含节点系统）≈ **80~100**。
- **优点**：Hall 的最新未提交成果零丢失风险（P0 先固化）；每阶段有可运行版本，回归粒度细；P0 结束时就已经拿到"改名为 rotten_ruins 的 Hall"，即使后续暂停也不影响发布；便于逐步去重和回头修架构。
- **缺点**：阶段之间有一次性集成开销；P1 期间"框架半新半旧"需要纪律（禁止新内容跨阶段混入）；总日历时间比一次性方案长。
- **适合**：以"稳、可回退、保留最新成果"为第一目标，且能接受分多次提交/发布。

### 方案 B：一次性总合并（big-bang）

**思路**：同样以 Hall 为基线，但**不拆阶段**：在一个长期分支上一次性完成改名 + 框架合一 + 全量内容迁移（含 P2/P3/P4 的全部内容），中途不发布，最后统一回归后直接出 1.0.0。

- **工作量**：约 **65~90 人日**（比 A 少 5~10 人日的阶段集成开销；若全量含节点约 80~100）。
- **优点**：最终形态一步到位，没有过渡期两套系统并存的临时状态；改动集中，架构决策一次做完。
- **缺点**：分支长期不可玩，所有错误在最后集中爆发、定位成本高；被迫一次性处理所有"待定内容"（红矮星弓、七罪、文件系统等），无法先用 MVP 验证手感；一旦方向要调头，回滚代价大。
- **适合**：1~2 人短周期冲刺、时间充裕、且愿意接受"2~3 周不可玩"。

### 方案 C：新工程重导（干净重建）

**思路**：新建 `RottenRuins` 工程（新目录/新仓库、包名 `org.bytechen.rottenruins`、modId `rotten_ruins`），先把 Hall 的骨架与基础设施（build/config/network/capability/渲染框架/难度/魔法）按新命名空间迁入并跑通，再按白名单移植 Hall 内容与 corruption 内容，旧两个工程只作参考源。

- **工作量**：约 **90~120 人日**（额外 15~20% 是骨架重建、构建补丁/JPMS/mixin refmap/curios/JEI 重新接线、以及 Hall 167 项未提交成果的重新落位）。
- **优点**：包名/命名空间/资源一步到位；彻底清掉 `Splendiding*` 类名、`data/splendiding`、旧 config 等技术债；架构最干净。
- **缺点**：等于重建，Hall 最新的渲染/着色器/绯红誓约/陨星等大块成果迁移时最容易漏或冲突；风险最高；周期最长。
- **适合**：不赶版本、把"长期架构整洁"放在第一位、并且愿意花时间重新验收全部渲染/光影兼容。

### 方案 D（对照，不推荐）：双模组共存，只统一品牌

不合并代码，Hall 与 corruption 各自保持 modId，只统一显示名/发布页，或抽公共库。**不满足"合并成一个模组 + 改 modid 为 rotten_ruins"的目标**，注册名/系统仍是两套、玩家仍需装两个 jar。仅作为"完全不想动代码"时的退路列出，不进入对比推荐。

### 2.4 方案对比

| 维度 | A 分阶段（推荐） | B 一次性 | C 新工程 |
|---|---|---|---|
| 与"合并成一个 mod"目标一致性 | 高 | 高 | 高 |
| 首个可玩版本 | **P0 后即 ≈4 人日** | 无（最后才有） | 无（骨架跑通后 ~15 人日） |
| MVP 可发布 | **45~60 人日** | 65~90（全量） | 90~120 |
| 全量 | 70~100 | 70~95 | 90~120 |
| 风险 | **低~中（可回退）** | 高（集中爆发） | 高（重建 + 成果迁移） |
| 阶段验收 | **有（alpha1/alpha2/beta1/beta2）** | 无 | 有但粒度粗 |
| 技术债清理 | 中（可顺手清） | 中 | **彻底** |
| 最新未提交成果风险 | **低（P0 先固化）** | 中 | **高** |
| 适合 | 稳态推进 | 短冲刺 | 长期重构 |

> **推荐 A**。核心理由：①Hall 当前有 167 项未提交的高价值内容，任何"重建式"方案都先面临成果落位风险；②这次变更同时动"modId + 包名 + 资源命名空间 + infcore 数据 type + 实体体系"，一次性做完的调试成本远高于分阶段；③P0 就能产出改名版，后续每阶段都可发布/回滚。

---

## 3. 子决策（每个都附推荐；这些是你需要拍板的地方）

### D1 生物基类：统一还是保留两个？—— 推荐"一个统一基类"

**结论：只保留一个 `AbstractRottenEntity`（以 Hall `AbstractHallEntity` 为骨架，根仍为 `Monster`），王庭/腐化差异放到 population 家族薄层 + `RottenFaction { HALL, CORRUPTION }` 数据字段。**

| 方案 | 结构 | 优点 | 缺点 | 工作量（生物体系） |
|---|---|---|---|---|
| **A（推荐）** | `Monster → AbstractRottenEntity → 4 个王庭家族薄层 + 6 个腐化家族薄层` | 单点维护全套跨切面能力（假死/爆体/难度/粒子/索敌/动画）；Hall 10 只生物行为零变化；与"合并成一个 mod"一致 | 腐化"家畜型"怪带 `Enemy` 标记（铁傀儡仇恨、Peaceful 消失、不可拴绳）——但这些怪本来就是主动攻击怪，可接受；Peaceful/睡眠可用 flag 关掉 | ~60~70 文件（Hall 侧几乎为 0，腐化侧必须重写 AI） |
| B 两个基类 | Hall 基类不动 + corruption 基类精简，仅共享接口/工具 | 对 Hall 零风险；腐化侧保留非 Enemy 语义 | **双轨维护**：假死/爆体/索敌/难度/渲染缩放每次改两遍；`EntityManager`/`ProjectileManager`/`GeoBaseRender` 继续重复；结构上仍是两个 mod | ~38~40 文件（短期少 4~6 人日，长期翻倍成本） |
| C 统一基类 + 根改 `PathfinderMob` | 统一核心 + Hostile/Neutral 两个薄层（`implements Enemy`） | 架构最"纯"，可做真正中立的同化家畜 | Hall 10 只生物全部换根，需补回 Monster 的 Peaceful/睡眠/音源/XP 语义 + 睡眠事件；回归面大 | ~70~80 文件（比 A 多 15~20%） |

**为什么选 A**：
- corruption 的 22 只注册生物（删除 KING 后）**全部是主动攻击单位**（查询结论：每只都挂 `SmartTargetGoal` + 近战/酸液伤害，注册分类全是 `MONSTER`），并不存在"因为继承 PathfinderMob 所以被动"的玩法事实；放不放进 `Monster` 根不会造成"中立变敌对"的倒退。
- 关键行为差异（Peaceful 消失、阻止睡觉、HOSTILE 音源、XP）都可覆写；唯一去不掉的是 `Enemy` 标记（铁傀儡/雪傀儡/潮涌核心仇恨、不可拴绳），对这些腐化生物而言反而合理。
- 若未来产品要求"同化家畜必须非敌对/和平模式保留/可拴绳"，升级路径明确：根改 `PathfinderMob` + Hostile/Neutral 两薄层 + 一个 `PlayerSleepInBedEvent` 处理器，改动集中在 1 个根类 + 约 12 个文件，内容层不受影响。
- **必须处理的迁移坑**：① 速度语义相反（Hall 无 walk 动画走 `walkSpeed`，corruption 多数走 `runSpeed`，直接换基类会让腐化生物集体变慢 2~5 倍，需要在薄层显式设速度）；② 假死掉落时机相反（Hall 在爆体结束掉 loot、corruption 在假死开始掉），直接合会双倍掉落；③ 击杀计数两条通路（infcore capability vs corruption 同步 int）会双计，统一走 `IKillCounter`；④ 19/22 生物依赖 `CorruptionSkillUtil/Fx`，只搬生物类编译不过，必须按"生物 + 技能底座"成组迁移。

**最终规模**：Hall 10 + corruption 24 − 删除 HeavyGuard（空壳）/Avila（未注册）= **33 只注册生物**；删 9 个 AI 文件（1,763 行）、adaptation/variant（经核实 0 实体启用）随基类一起删除；动画无需新增（两侧 geo 都是 `1.12.0`、animation 都是 `1.8.0`，`idle/walk/run/die` 键名直接兼容）。

### D2 阵营 / 索敌 / 演化 type 设计 —— 推荐"单阵营 + 双套规则 type"

| 概念 | 推荐值 | 说明 |
|---|---|---|
| 阵营（`IInfectedEntity.getInfectionType()`） | **单一 `rotten_ruins:inf`**（王庭 + 腐化同阵营） | `InfectedTargetGoal` 默认 `targetOtherInfected=false`，本模组感染者天然不互殴；玩家=非感染猎物，索敌闸门统一用"威胁/威慑"能力 |
| 演化 type（`infcore_evolution`） | **`rotten_ruins:hall_inf` / `hall_evo` / `corruption_inf` / `corruption_evo`** | 必须拆：否则腐化击杀走 Hall 的 fallback 会被转成王庭溃烂怪 |
| 方块扩散 type（`infcore_blockspread`） | **`rotten_ruins:hall_spread` / `corruption_spread`** | 必须拆：同 type + 同 source 的规则会后者覆盖前者（静默丢规则） |
| corruption 的 `kind` 枚举 | 保留为**普通数据字段**（等级/谱系），不参与敌我判定 | 敌我统一由感染类型 + `ITargetFilter` 决定 |
| 威胁 / 威慑 | **合并为一个"威胁值"能力**（参考 Hall `ThreatCapability`） | 否则同一玩家会被两套数值重复判定 |
| 如果以后王庭与腐化要敌对 | 拆双 type `rotten_ruins:hall` / `rotten_ruins:corruption` + 双方 `targetOtherInfected(true)` + `ITargetFilter` 做关系表 | 现在就按"类型集合"写判断，留好开关 |

**改名硬编码坑（P0）**：6 处 `getInfectionType().getNamespace().equals("hall")` / `getPath().equals("hall")` 判定（`ForgeEventHelpers`、`BlackHolePhysicsHandler`、`CollapsarEntity`、`MeteoriteEntity`、`WindingColumnEntity`、`HallDifficultyAttributes`）和 3 处 `"hall".equals(getSpreadType().getNamespace())`（`HallVineBlock`、`HallVineFeature`、`HallSpreadEventHandler`）**不会跟随 MODID 自动改变**，漏改会导致友伤/黑洞免疫/藤蔓生长/陨石治疗**静默失效**。必须在 P0 统一改为"本模组感染类型集合"辅助方法。

### D3 节点系统（corruption 独有）—— 推荐不搬（MVP）

节点系统是 corruption 的"空间感染强度"来源：`CorruptionNodeManager` + `CorruptionNode`/`NodeQueryResult` + `NodeConfig` + 命令 + DebugItem + SavedData + 同步，驱动扩散/刷怪阶段/生物属性加成/群系扩散/调试。Hall/infcore **没有任何对应抽象**。

| 选项 | 影响 | 代价 |
|---|---|---|
| **不搬（推荐）** | "腐化强度"改用 infcore evolution 世界数据（演化值派生阶段）+ 4 档全局难度 + 群系存在感；刷怪配置简化（去掉 phase/ignoreNode 字段）；DebugItem/节点命令不迁 | 0；但需要重新定义"腐化强度→刷怪/属性"的映射（约 3~5 人日，已含在 P3） |
| 搬（完整节点系统） | 保留"节点空间玩法"（在世界上布点、强度叠加、阶段成长、可视化调试） | **+10~15 人日**，且必须与 infcore 难度/演化共存，避免三套世界数值打架；建议放到 P4 作为可选模块 |

> 相关：`CorruptionSpawnConfig` 的 `phase/ignoreNode` 与 `CorruptionEffect` 死亡转化、`ForgeEventHandler.onEntityJoin` 的属性加成、`SpreadRuleManager` 的节点许可都随此决策一起改。

### D4 旧存档兼容 —— 推荐"明确弃档 + 可选兜底映射"

这次同时改两个 mod 的注册命名空间（`hall:*` 和 `corruption:*` 全部变 `rotten_ruins:*`），Forge 1.20.1 没有内建的注册名迁移机制，旧档里的物品/方块/实体/进度/维度**理论上无法完整保留**。

| 选项 | 效果 | 代价 |
|---|---|---|
| **明确弃档（推荐）** | 发布说明写清"1.0 起为新模组，旧存档不兼容"；测试用新档 | 0 |
| + Forge `MissingMappingsEvent` 兜底 | 旧 `hall:*` / `corruption:*` 的物品/方块/实体可以映射到新注册项（世界里的方块/箱子物品能救回大部分） | +1~2 人日；**但不能救**：旧维度目录 `world/dimensions/hall/heall`、进度 ID、SavedData 字段结构、旧 NBT 里的自定义 id |
| 兼容别名（同一物品注册两个 ID） | 旧档可读 | 2 倍注册量 + 创造栏过滤；不解决方块/维度；不推荐 |

> 若在意旧存档，建议：先发"最终旧版"（Hall 1.0.0Beta / corruption 0.6.0）并公告，然后 1.0 用新档；`MissingMappings` 作为可选保险单独评估。

### D5 GeckoLib 版本冲突 —— 推荐统一 4.8.3

- corruption 用 CurseForge 文件 7553267 = **GeckoLib 4.8.3**（`implementation`，不 jarJar）；Hall 用 Maven 坐标 **4.7.1**（`compileOnly` + `jarJar [4.7.1,4.8)`）并做了剥 `module-info.class` 的 JPMS 修补。不统一版本必然至少一边编译/运行失败。
- **推荐**：合并工程升到 **4.8.3**（corruption 的 22 只生物与技能渲染都基于 4.8.3 写），Hall 侧重新验证 `patchModJars`/`copyPatchedMods`（JPMS 剥离）与 jarJar 范围 `[4.8.3,4.9)`；若 Hall 升级后出现渲染/动画兼容问题，再回退评估"把 corruption 代码压到 4.7.1 并逐只验证"（成本在 22 个生物上，风险更高）。
- 顺带统一：Cloth Config 12.0.109（只 jarJar 一次）、Forge 对齐 47.4.21（infcore 的编译版本）、mappings 保持 official（corruption 的 `mapping_channel=parchment` 是无效残留，两边实际都是 official）。

### D6 内容取舍（"搬什么、丢什么"）

**必搬（构成腐化玩法闭环）**

| 类别 | 内容 |
|---|---|
| 生物 | host 族 11 只（同化僵尸/骷髅/玩家/村民/牛/猪/羊/狼/鸡/蝙蝠/骷髅遗骸）、degenerate 族 4、deteriorated 族 1（Groveler）、refactored 族 4（Attacker/Bearer/LongArmBeast/Shocker）、linked 族 2（HumusColumn/SummoningPillar）、original 族 1（OriginalBug） = **22 只** |
| 效果 | corruption / lysis / miasma / sclerosis / bleeding / fear（6 个，补 lang 与 bleeding 图标） |
| 方块 | spread_* / corruption_grass / variable_height_crop / trachea / decidual_tissue / connect_block / spiracle_block / 9 种遗骸 / fleshbed(+BE) = **21 个**（删 5 KING） |
| 物品 | 材料 10（infection_bone/hard_fragments/live_meat/host_meat/host_carapace/host_tooth/degenerate_heart/chitin_polymer/long_arm_beast_*）、粗制工具 5、注射器 2、酸液球/酸囊 2、血肉温床、刷怪蛋 21（删 KING） |
| 系统 | 腐化方块扩散规则（转 infcore_blockspread 数据）、同化/演化规则（转 infcore_evolution 数据）、6 个效果、腐化群系素材、JEI 插件（改 namespace） |

**可选 / 待定（建议 MVP 先不做，P4 按需）**：红矮星弓 + 3 个红矮星技能实体（+2~3 人日，唯一完整远程武器）、`poisoned_saber`（无效果无配方，需补注毒 DoT + 配方）、`file`/`file_folder`（容器逻辑在但无 GUI）、七宗罪 8 件（空壳）、芯片 4 件（科技线空壳，与魔法体系冲突）、`acid_bomb`（无功能）。

**必丢（重复/等价/未完成/死代码）**：见第 4 节完整清单。重点是 KING 系列（5 物品 + 5 方块 + HeavyGuard/Avila）、corruption 的寻路 AI/brain 9 文件、自研扩散/群系/演化/网络/capability/savedata 框架、与 Hall 重复的 14 个同名 shader / 16 张相同粒子帧贴图 + `meat.png` 近似 / `rip_body.ogg` / glint 渲染 15 类 / 8 个同名 mixin（另 3 个未使用 mixin 死类）、`OnlineSkinDownloader`、模板池与树生成器（无 NBT 结构，死代码）、`ConfigDrivenSpawnModifier`（无数据）、`ItemGeoInitializeMixin`/`accessor.AccessorDisplay`/`SummonManager`/`RrsConfigScreen`/`RegisterStructures`。

### D7 世界生成与群系 —— 推荐"Hall 体系唯一，腐化方块并进来"

- 保留 Hall：维度 `rotten_ruins:heall`（血肉庭园）+ 群系 `flesh_marrow` + 扩散群系 `hall_wasteland` + 5 个 feature + noise 设置。
- corruption 的 `corruption_wasteland` 与 Hall `hall_wasteland` **是同一设计的两代版本**（Hall 是升级后的白色浓雾美术版）→ **不单独保留**，其方块（腐化植被/遗骸等）作为腐化类型扩散的目标素材并进同一套群系/方块体系。
- 扩散/群系引擎只保留一套：Hall `swarm/*` + infcore `BlockSpreadManager` / `BiomeSpreadHelper`；删 `MixinBiomeManager` / `UpdateBiomePacket` / `BiomeCorruptionProcessor` / `CorruptionDiskStorage` / `InfectionTrackerHandler`。
- **刷怪**：Hall 的 `SplendidingSpawnPlacements` 目前是空实现，必须新建；采用 corruption `CorruptionSpawnConfig` 的配置思路但去掉节点 phase 耦合，生成为 `SpawnPlacement`/生物群系 spawners + 配置文件。
- 如果确实想保留"两个可探索群系/两条扩散线"（王庭化 vs 腐化），需额外 2~3 人日做第二群系与分流规则（可作为 P4 可选项）。

### D8 包名与目录结构 —— 推荐单包 `org.bytechen.rottenruins`

- 推荐**与 modId 改名同批**完成包名重构（IDE 一次 refactor，避免第二次全量 diff）：`org.bytechen.hall` → `org.bytechen.rottenruins`；`com.corruption` 不再独立成包，按域并入（如 `entity/population/host/*`、`content/*`）。
- **王庭 / 腐化只在内容子包与 family 薄层区分，不按"两个框架"分包**（否则等于把双轨制固化）。
- 可保留的 Java 标识符债：`Hall*` 类名、`Splendiding*` 类名、`splendiding$` mixin 前缀——不影响功能，建议后置清理，避免与合并改动混在一起。

### D9 工作树与仓库策略 —— 推荐"先固化、再迁"

1. 当前 Hall 工作区 **167 项未提交改动**（70 修改 / 19 暂存新增 / 75 未跟踪 + 若干删除）先提交到 `1.20.1forge` 分支，并打 tag（如 `pre-rotten-ruins-20261028`）。**不要 stash 后长期不动。**
2. 从该 tag 拉 `merge/rotten-ruins` 分支做合并；每阶段结束打 tag。
3. corruption 原仓库打 tag `corruption-v0.6.0-final` 只读留档，不尝试合并两边的 git 历史（无共同祖先，历史合并无价值）。
4. 新包名/新工程若选方案 C，则新开仓库；方案 A/B 直接在当前仓库继续。

### D10 版本、依赖与发布口径

- 版本：合并后从 **1.0.0** 起（旧 Hall `1.0.0Beta`、corruption `0.6.0` 均不作为合并版版本号），发布说明明确"新档"。
- `mods.toml`：`infcore` 改为 **mandatory `[0.1.2,)` + ordering AFTER**（Hall 现在写 `"*"`，玩家装 0.0.2 会在启动时 `NoSuchMethodError`）；`cloth_config`、`geckolib` mandatory；`curios` 保留 mandatory（王庭装备强依赖）；JEI 保持 optional。
- 构建：保留 Hall 的工程内 infcore Maven 镜像（`libs/org/bytechen/infcore/0.1.2`）与 `syncInfcore` 任务；建议顺手修 `syncInfcore` 的 `workingDir`（应为 `../InfectionCoreFramework`）；jarJar = cloth-config + geckolib（升 4.8.3 后范围同步），**不要 jarJar infcore**（会与 GodSickNeo 等外部消费方冲突）。
- 显示名建议统一为 `Rotten Ruins`；creators tab 改用 Hall 的分区 tab，新增"腐化内容"分区；`example_item` 占位图标换掉。

---

## 4. 不搬 / 去重清单（本合并的核心收益）

> **先纠正一个直觉**：本次合并**资源层重复其实很小**——全量哈希比对 1,189 个资源文件后，字节级完全相同只有 **20 个 / 54,049 B**（占 Hall 资源 1.69%、corruption 3.12%）；合并后"同目标路径撞名"共 **42 对**，其中 32 对可直接去重/改名，只有 10 对需要拍板。**资源侧的主要工作是改名 + 42 对撞名处理 + datagen 重跑，不是海量去重。**
>
> 真正重复的是 **Java 骨架**：相对路径完全相同 **79 对**（24 对归一化后字节等价、21 对取 Hall 稍改即可、34 对大幅分叉需人工合并），另有 15 对"改名/挪位同职责"类；corruption 独有 189 类中约 **160 个需迁入**，Hall 独有 311 类全保留。去重收益主要来自"corruption 的旧框架整类不搬"（约省 30% 的 corruption 工作量）。

### 4.1 重复资源（corruption 侧直接丢弃）

| 类别 | 重复情况（精确口径） |
|---|---|
| Shader | 14 对同名文件（3 个字节级相同 + 6 个仅 CRLF/LF 差异 + 5 个仅命名空间差异）+ corruption 独有 1 个 → 同名全保留 Hall 版 |
| 粒子贴图 | **16 张字节级相同**（`explosion_bloody0~15.png`）+ `meat.png` 同名近同（二选一）；Hall 已有 `blood`/`meat`/`explosion_bloody` 3 个粒子类型（含新实现） |
| 音效 | `rip_body.ogg` 字节完全相同（41,398 B，Hall 已复制）→ 丢 |
| 渲染 / mixin | glint/描边 15 个同名类 + 8 个同名 mixin（Hall 是全部 8 个注入点的超集；GameRenderer/ItemInHand/ItemRenderer/LevelRendererEmbeddium/Oculus/ItemGlintMixinPlugin 等）→ 全部保留 Hall 版 |
| 渲染 helper | `CDRenderHelper`/`ZoomRenderHelper`/`ItemRenderManager`/`AbstractStaticEffectRenderer`/`LightningEffectRenderer`/`HitboxCubeRenderer` 同名同源（24 对等价类）→ 留 Hall |
| geo / 动画 / 实体贴图 | **不重复**（两侧各一套生物）→ 腐化侧全部搬（29 geo / 28 动画 / 38 实体贴图） |
| 物品/方块贴图 | 基本不重复 → 搬（去垃圾后：159 张 texture、119 个 model、21 个 blockstate） |
| 粒子 | 腐化独有 **6 个**需要搬：`blood_splat`/`blood_splash`/`blood_fog`/`bloody`/`degenerate_die_meat`/`degenerate_meat`（+贴图）。**3 个同名硬碰撞**（`blood`/`meat`/`explosion_bloody`）保留 Hall 实现；C 的差异能力若需要，用新 id（如 `corruption_blood`）迁入，**不要覆盖同名注册** |
| 跨引用 | Hall 的 `assets/hall/models/item/base_tool.json` 唯一硬引用 `corruption:item/crude_sword` → 必须改 namespace 并迁入 `crude_sword.png` |

**4 类注册 ID 硬碰撞**（必须逐个处理）：`particle_type` 的 `blood`/`meat`/`explosion_bloody`；`sound_event` 的 `rip_body`；creative tab 标题键 `itemGroup.<ns>.main`；命令根 `hall`/`corruption` → 统一成 `rotten_ruins`。其余 item/block/entity/effect/recipe/loot/advancement 的 ID 后缀**零交集**（不会撞注册表）。

### 4.2 重复框架（保留 Hall / infcore 版，删除 corruption 版）

- 网络：`NetworkHelper`/`UniversalPacket`/`PacketActionRegistry`/`PacketSyncKeyframe`/`EffectSyncPacket` → 留 Hall；**两个 `NetworkHelper` 不能共存**（同 `rotten_ruins:main` 会启动报 Channel already registered）。合并后单通道约 **10 个包**：Hall 8 个 + C 的 `CParticleSpawnPacket`/`GorePacket`（`UpdateBiomePacket` 随旧群系系统丢弃；`PacketSyncSaveData`/`PacketSyncCapability` 随自研 savedata/capability 框架丢弃），包 id 全部重排。
- capability / savedata：corruption 的 `capability/base/*`、`CapabilityProvider`、`PacketSyncCapability`、`savedata/*`、`CorruptionData`、`SyncDataHelper` → 由 infcore `core.capability` / `AbstractWorldData` 收编，**整类删除**。
- 扩散 / 群系：`AbstractSpreadingBlock`+`SpreadRuleManager`+`BiomeCorruptionProcessor`+`BiomeCorruptionData`+`CorruptionDiskStorage`+`InfectionTrackerHandler`+`SpreadEventHandler`+`MixinBiomeManager`+`UpdateBiomePacket` → 由 Hall `swarm/*` + infcore 替代。
- 演化 / 同化 / 孕育者：`AssimilationMapManager`+`BearerManager` → 转 `infcore_evolution` JSON + 实体内部逻辑。
- 实体 / 渲染底座：`CorruptionEntityManager`/`ProjectileManager`/`GeoBaseRender`/`IAutoRenderable*`/`IGeo*` → 留 Hall 版。
- 工具：`CorruptionEntityParticleUtil`、`CorruptionAreaAttackUtil`、`CorruptionDamageUtil`、`ModUtils.addOrStackEffect` → 留 Hall 的 `EntityParticleUtils`/`RangeAttackUtils`/`EntityBuffUtils`。
- 威胁 / 威慑：`Deterrence`/`DeterrenceManager` 并入 Hall `ThreatCapability`/`ThreatHelper`。
- datagen：corruption 14 provider 骨架 → 内容并入 Hall `DataGenEvent`（14 个 provider）。

### 4.3 KING 系列 = 王庭系列（用户已定：不搬）

`king_bone` / `king_fiber` / `king_hearthstone` / `king_shell` / `king_big_sword`、5 个 KING 方块、`HeavyGuardEntity`（已注册但无 AI/无蛋/无 lang/无掉落）、`AvilaEntity`（648 行但**从未注册**）、对应 geo/anim/texture/BossBar 渲染、`KingBigSwordItem`、`SevenSins` 相关若与王庭定位冲突一并由 D6 处理。

### 4.4 AI / Brain / 死系统（用户已定：不搬）

删除：`SmartMoveGoal`(23KB)、`SmartTargetGoal`(12KB)、`JumpPointPathfinder`(15.6KB)、`CorruptionFlyGoal`、`RandomWanderGoal`、`FollowGoal`、`brains/`（3 个）、`MemoryModuleTypeRegistry`、`AbstractCorruptionEntity` 里的 `Brain`/`MemoryModule ai_state`/`SmartMoveBrain`；`adaptation`（伤害适应）与 `variant`（外观变种）经核实 **0 实体启用**，一并删。移动/索敌统一改为 Hall 的 `tickChase + ChaseLockGoal + FaceTargetGoal + InfectedTargetGoal`，飞行生物 (`infection_bat`) 保留 `FlyingPathNavigation` + 原版 `RandomFlyGoal`。

### 4.5 未完成 / 死代码（不搬）

`CorrupTionTreeGrower`/`CorrupTionTreePools`/3 个模板池（无 NBT 结构、无调用方）、`ConfigDrivenSpawnModifier`（codec 注册但无数据 JSON，整条链路不生效）、`ItemGeoInitializeMixin`（不在 mixin 配置）、`accessor.AccessorDisplay`/`ItemDisplayAccessor`（无使用方）、`SummonManager`（空 builder）、`RrsConfigScreen`（空类）、`RegisterStructures`（空类）、`OnlineSkinDownloader`（外部 HTTP 依赖，隐私/维护风险）、`File`/`FileFolder`（无 GUI，可选待定）、`SevenSinsItem` 8 件、芯片 4 件、`acid_bomb`、`DebugItem`（随节点）、10 张时间戳命名垃圾贴图、3 个中文命名音效、2 张垃圾 GUI 图、`heavy_guard`/`avila` 资源。

---

## 5. 系统迁移要点（摘要，详见 `analysis-infcore.md` §5/§6）

| corruption 系统 | 合并后做法 |
|---|---|
| 索敌 `SmartTargetGoal`（黑名单/同类/白名单/不可达监控） | `InfectedTargetGoal` + `ITargetFilter`（威胁/威慑统一）；不可达监控/追踪倍率等 infcore 不支持的字段：MVP 先不做，或保留自研 Goal 仅用于个别实体 |
| `kind` / `attackSameKind` / `CorruptionMobType` | 降级为普通数据字段（等级/谱系），敌我由感染类型 + filter 决定 |
| `AssimilationMapManager`（配置驱动实体→实体转换） | 转 `data/rotten_ruins/infcore_evolution/*.json`（source→weighted targets，keepNbt/keepEquipment）；成功率/条件保留在调用方 |
| `BearerManager`（吞噬值→概率进化） | 吞噬逻辑保留在 `BearerEntity`，满足阈值后调用 `EvolutionManager.applyEvolution(..., corruption_evo)`；世界演化值改用 infcore WorldData |
| `CorruptionEffect` 死亡转化 / 注射器 | 统一改由 infcore 死亡事件 + `applyEvolution` 触发；注射器保留为手动触发入口（不要再依赖"效果每 tick 检查血量"的旧写法，实战几乎不触发） |
| 节点系统（10~15 人日） | 推荐不搬；"感染强度"用 WorldData(演化值) + 群系存在 + 难度档替代（D3） |
| `SpreadRuleManager` / `AbstractSpreadingBlock` / `BiomeCorruptionProcessor` | 方块转化规则转 `infcore_blockspread` JSON（tag 展开成具体方块）；方块层保留密度/萌发/环境谓词；群系扩散用 Hall `HallBiomeProcessor` + infcore `BiomeSpreadHelper`；反向净化保留 Hall 快照方案 |
| `SyncDataHelper`/`CorruptionData`/自研 SavedData | 转 `AbstractWorldData` + `WorldDataRegistry`（注意 infcore 的 SavedData 文件名含冒号，Windows 下需先修 infcore 或改用无冒号 name） |
| 全局难度 | 用 infcore `DifficultyHelper` 4 档（Hall 现有）；corruption 8 段"演化阶段"降级为派生展示值，不作为第二套难度 |
| 击杀计数 | 删 corruption 的 synced `KILL_COUNT`，统一 `IKillCounter` / `KillCountCapability`（避免双计） |
| 玩家能力同步 / capability | 用 infcore capability + 同步链路；删 corruption 的 `PacketSyncCapability`；`Deterrence`（若保留威慑系统）注册进 Hall `CapabilityRegistry` |
| 工具层（corruption `ModUtils` 30 个静态方法） | Hall 把它拆成多个小工具类，但 corruption 有 **20 个方法 Hall 无同名实现**（射线裁剪 `rayClip/orbClip/clipPoint`、范围伤害 `applyAreaDamage/applyForwardAreaDamage`、群系写回 `setBiome/setBlockPosBiome/updateChunkAfterBiomeChange` 等）→ **迁 corruption 内容前必须先补工具层/做映射**，否则范围攻击与腐化扩散类直接编译不过 |
| Mixin | 单份 `rotten_ruins.mixins.json`：Hall 16 项 + corruption 的 `MixinLivingEntity` / `accessor.AccessorLivingEntity`（随 `bleeding`/`fear` 不可移除效果一起迁入）；`MixinBiomeManager` 随旧群系系统丢弃；3 个未使用/未注册 mixin 不搬 |
| datagen | 两套 `DataGenEvent` 必须合并成单入口；**7 类输出会互相覆盖**（`lang/en_us`、`lang/zh_cn`、`sounds.json`、`particles/{blood,meat,explosion_bloody}.json`、`minecraft/tags/blocks/mineable/pickaxe.json`）——GatherData 后写覆盖先写，不报错但静默丢内容 |
| 刷怪 | 新建（Hall 的 SpawnPlacements 是空的）；采用 corruption 配置思路，去掉节点 phase 耦合 |
| 命令 | 难度用 `/infcore difficulty`；腐化演化值/节点调试命令保留并改根名（`hall` 与 `corruption` 会撞成同一根 `rotten_ruins`，必须合并成一棵命令树；`node` 这类通用裸根挂到 `/rotten_ruins node` 下） |
| JEI | corruption 的插件是唯一 JEI 插件，保留并改 namespace/UID |
| 网络包 | 单通道 `rotten_ruins:main`：保留 Hall 版 `NetworkHelper`（8 包）+ C 的 `CParticleSpawnPacket`/`GorePacket`（其余 C 包随旧框架丢弃）；包 id 全部重排（旧客户端不兼容，无额外风险） |

---

## 6. 方案 A 执行路线图（可落地的 WBS）

### P0 冻结 + 改名 + 清债（2~4 人日，验收=可玩改名版）

1. 提交当前 167 项未提交改动 → 打 `pre-rotten-ruins-20261028` tag；拉 `merge/rotten-ruins` 分支。
2. `gradle.properties`：`mod_id=rotten_ruins`、`mod_name=Rotten Ruins`、`mod_version=1.0.0`、group `org.bytechen`。
3. 包名重构 `org.bytechen.hall` → `org.bytechen.rottenruins`（390 文件 + 869 import；IDE 一次完成）；`HallMod` → `RottenRuinsMod`（或沿用 HallMod 类名，可后置）。
4. mixin：`hall.mixins.json` → `rotten_ruins.mixins.json`（json 内 `package`/`refmap`/`plugin` 三字段同步）；确认 `mods.toml`/`build.gradle` 的 `${mod_id}` 自动跟随；`copyRefmap` 验证。
5. 资源目录：`src/main/resources/assets/hall` → `assets/rotten_ruins`；`data/forge` boss tag 同步；`hall.png` 改名。
6. 手写 JSON（49 文件/99 处 `hall:`）：35 个 shader json 的 `"vertex"/"fragment"`、3 个 cosmic/mask 物品模型 loader、mixin 配置、`pack.mcmeta`。
7. 源码字面量：12 处硬编码 ID + 16 处 `"hall:*"` 功能字面量（配方 id 13/shader id/action/NBT key）+ 34 处 lang 键（`TranslateUtils` 26 + `LangDataCN` 3 + `RegisterAttributes` 5）。
8. 修正 6 处阵营硬编码 + 3 处 spread 硬编码（改常量/helper，勿只替换字符串）；`ThreatCommand` 根命令名、`HallBlockSetTypes`/`WoodType` 名称。
9. 清空 `src/generated/resources`（保留 `.gitignore` 约定）→ `runData` 全量重生成；检查新 `data/rotten_ruins/infcore_evolution/*.json` 的 type 已是 `rotten_ruins:*` 且无重复 `(type,source)`。
10. 删除历史空目录 `data/splendiding`、`data/godsick_neo`；清理死代码（`InvokerHandTransforms`、`EffectSpriteData` 空 provider、`SplendidingMaterials`/`SpawnPlacements` 空模板、空 `BIOME_MODIFIER_SERIALIZERS`）。
11. config 迁移：`config/hall/*` → `config/rotten_ruins/*`（启动时复制 + `configVersion` 处理，避免玩家渲染设置重置）。
12. 更新 `buildAndInstall.ps1`（产物名/校验路径）、README/CHANGELOG。
13. **验收**：`gradlew build`；进游戏检查 mixin 加载、35 个 shader、3 个 cosmic 模型、维度传送、藤蔓/扩散生长、属性 tooltip、难度选择 GUI；新旧档行为说明。

### P1 框架合一（6~9 人日）

1. 实体基类：`AbstractRottenEntity`（Hall 骨架）落地；王庭 4 个家族薄层改名/挂接；新增 `RottenFaction` 字段。
2. 删除 corruption 侧 9 个 AI 文件 + `brains` + `MemoryModuleTypeRegistry` + adaptation/variant/kind 死系统（这些还没有被搬进来，P1 是把"目标结构"先定好，P2 按此迁移）。
3. 网络通道合并方案落地（清空并重排包 id）；capability/savedata/威胁统一。
4. infcore 数据 type 拆分常量集中管理（`RottenRuinsTypes`：阵营/演化/扩散/难度常量）。
5. **验收**：现有 Hall 全部功能不回归；实体/网络/能力链路正常；`-Dinfcore.verbose=true` 看到新 type 扫描结果。

### P2 腐化内容 MVP（22~30 人日）

按"生物 + 技能底座成组"迁移顺序：效果/粒子/音效 → 方块与扩散规则 → 材料/工具/注射器/温床 → host 族 → degenerate/deteriorated → refactored/linked/original → 刷怪蛋/掉落/配方/lang/JEI → 资源（geo/anim/贴图/模型）。每迁一组就进游戏冒烟（技能命中、动画、掉落、索敌）。

### P3 世界/演化/刷怪闭环（8~12 人日）

evolution 数据（corruption_inf/evo）、Assimilation/Bearer 逻辑改写、刷怪体系、腐化群系并入、感染强度替代节点、方块掉落表补齐（26 个方块目前全都没有掉落表）。

### P4 扩展 + 回归发布（7~10 人日）

可选内容（红矮星弓/文件系统/poisoned_saber/七罪/芯片按 D6）、技能投射物差异化渲染、光影三环境（无光影/Embeddium/Oculus）冒烟、多人/服务端 TPS、JEI、存档、文档、发布。

---

## 7. 风险登记（按优先级）

| 级别 | 风险 | 缓解 |
|---|---|---|
| **P0** | 未提交的 167 项 Hall 成果丢失/被覆盖 | P0 第一步提交 + tag；合并全程在新分支 |
| **P0** | 改名后 6 处阵营 + 3 处 spread 硬编码漏改 → 友伤/黑洞/藤蔓/陨石静默失效 | 改为常量/helper；验收专项测试（藤蔓生长、群系扩散、王庭内部友伤、陨石治疗） |
| **P0** | mixin 文件名/refmap/package 不同步 → 启动崩溃；且两套 mixin config 并存会导致同 target 多重注入 | P0 一次同步 4 处 + 启动验证；合并为单 config（Hall 16 + corruption 的 MixinLivingEntity/AccessorLivingEntity ≈ 18~19 类） |
| **P0** | 两个 `NetworkHelper` 同名 channel → 启动即崩（Channel already registered） | 只保留 Hall 版并补 C 的 2 个包注册；包 id 重排 |
| **P0** | 两套 `DataGenEvent` 迁移后写同一批输出（lang/sounds/particles/tag）→ 静默覆盖丢内容 | 合并为单 DataGenEvent / 单 provider；`runData` 后校验文件内容并 diff |
| **P0** | 两套世界腐化系统并存（Hall `swarm`+infcore vs corruption `MixinBiomeManager`+`CorruptionNodeManager`） | D7：Hall 为主、C 的 biome mixin/节点系统关闭；P1 阶段物理删除 |
| **P0** | GeckoLib 4.8.3 vs 4.7.1 冲突 | D5 统一 4.8.3，重验 JPMS 剥离与 jarJar 范围 |
| **P0** | 旧存档/维度不兼容 | D4 明确弃档 + 可选 MissingMappings；发布说明写清 |
| **P0** | 删节点后"腐化强度"无来源 → 刷怪/属性/扩散闭环缺数据 | D3 用 WorldData 演化值 + 难度 + 群系替代；P3 专项 |
| **P1** | evolution/blockspread type 未拆 → fallback 污染、同 source 规则覆盖（静默） | D2 拆分 + datagen 校验 `(type,source)` 无重复 |
| **P1** | 速度语义/假死掉落/击杀计数双通路 → 腐化生物全体变慢、双倍 loot、双计 | D1 迁移清单逐条核对；实体验收表 |
| **P1** | 手写 JSON namespace 漏改 → shader 编译失败/模型丢失 | P0 清单 49 文件逐项验证；3 个 cosmic 模型+35 shader 冒烟 |
| **P1** | corruption 工具层 20 个方法 Hall 缺失（射线裁剪/范围伤害/群系写回）→ 内容类编译不过/运行 NPE | P1 先补工具层再迁内容；按 `analysis-duplicates.md` §4.8 映射表逐方法处理 |
| **P1** | 42 对撞名资源未处理（缺贴图/模型/键）；3 个粒子同名不同实现；creative tab 标题键撞名 | 按 dup 报告 §8.6 顺序：先定目标名 → 32 对直接删 C 副本 → 10 对拍板（建议单独 commit） |
| **P1** | 旧 config 不做迁移 → 玩家渲染设置全重置 | P0 加 config 旧路径复制 |
| **P1** | 双套群系/网络/威胁并存 | P1 一次性删旧、单通道单套；code review + 启动日志 |
| **P1** | infcore WorldData 文件名含冒号（Windows 存档风险） | 先实机验证；不行则修 infcore（0.1.3）或改用无冒号 SavedData name |
| **P2** | lang 合并遗漏（283/309 键、2 个撞键、26 个 zh-only、3 个畸形键） | 脚本比对键集合 + 逐键校验；zh/en 对称性检查 |
| **P2** | 跨工程硬引用 `base_tool.json` → `corruption:item/crude_sword` | 改 namespace + 迁入 `crude_sword.png`；全局替换只替换 `<ns>:` 前缀，勿误伤 shader program 名/模型自定义字段 `"corruption": true` |
| **P2** | generated 未清理 → 新旧 namespace 资源双份 | 固定流程"删 generated → 删 cache → runData → diff" |
| **P2** | 死代码/垃圾资源/文档残留 | 发布前统一清理 |

---

## 8. 需要你拍板的问题（按影响排序）

1. **总体方案选 A / B / C？**（本报告推荐 A）
2. **生物基类**：统一 `AbstractRottenEntity`（推荐，根 `Monster`）还是 C 方案（根 `PathfinderMob` + Hostile/Neutral 薄层，真正中立同化家畜）？
3. **节点系统**：不搬（推荐）还是搬（+10~15 人日，保留空间节点玩法）？若不搬，接受"感染强度 = infcore 演化值/难度/群系"的替代定义吗？
4. **王庭与腐化是否敌对**：单阵营 `rotten_ruins:inf`（推荐，全友）还是双阵营（王庭 vs 腐化互殴）？
5. **旧存档**：明确弃档（推荐）还是加 `MissingMappings` 兜底（+1~2 人日，且不能完整兼容）？
6. **内容取舍**：红矮星弓、文件系统、`poisoned_saber`、七罪 8 件、芯片 4 件——哪些进 MVP/后续/直接丢？
7. **GeckoLib 方向**：升 Hall 到 4.8.3（推荐）还是把 corruption 压到 4.7.1？
8. **包名**：现在就改 `org.bytechen.rottenruins`（推荐，与 modid 同批）还是先只改 modid、包名后置？
9. **同化种 vs 畸骸种**：保留两套实体 ID（推荐，王庭"畸骸"与腐化"同化"是不同单位）还是合并成一个家族（+5~8 人日、概念更统一）？
10. **世界生成**：只保留 Hall 维度/群系（推荐），还是要额外加"腐化荒原"第二群系/第二扩散线（+2~3 人日）？

---

## 9. 附录：证据与报告索引

| 文档 | 内容 |
|---|---|
| [`analysis-hall.md`](analysis-hall.md) | Hall 侧完整盘点：注册表精确 ID、资源、架构模块、infcore 消费面、modid 迁移面量化（19 处硬编码/49 个手写 JSON/12 步 checklist）、风险 |
| [`analysis-corruption.md`](analysis-corruption.md) | corruption 侧：89 物品/26 方块/34+1 实体/6 效果逐项清单与处置、AI/brain 删除影响、节点/扩散/演化系统复杂度、资源重复、18 模块工作量（MVP 45~60 / 全量 80~100）、存档风险、死代码清单 |
| [`analysis-infcore.md`](analysis-infcore.md) | infcore 0.1.2 API 全表、JSON schema、Hall 44 处 import 用法、"阵营判断"专题（阵营 ≠ evolution type；单/双阵营设计；targetOtherInfected 二值局限；9 条坑）、corruption→infcore 18 项替换映射、改名 checklist、版本/依赖建议 |
| [`analysis-entities.md`](analysis-entities.md) | 两个基类逐特性对比（含 MC 1.20.1 解编译源码证据）、Monster vs PathfinderMob 真实副作用、三个基类方案评分、33 只最终生物映射表、动画兼容性、迁移坑 |
| `docs/` 根目录 | Hall 原有设计文档（渲染/魔法/背板/着色器/陨星等），合并后仍需同步更新命名空间引用 |

> 本文所有决策建议均来自上述只读分析；未执行任何 Gradle 构建、未修改任何源码或资源。合并实施时以各报告的"证据位置"为准。
