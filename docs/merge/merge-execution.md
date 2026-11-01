# 合并执行记录（方案 B：一次性总合并 · 简化版）

> 分支：`merge/rotten-ruins`（基线 tag `merge-baseline` / 原工程 tag `pre-rotten-ruins-baseline`）
> 目标：Hall + corruption → 单模组 `Rotten Ruins`，modId `rotten_ruins`，包 `org.bytechen.rottenruins`
> 已定决策：GeckoLib 升 1.20.1 最新（4.8.4）；旧存档弃档；节点系统不搬；生物统一基类；红矮星弓 / 文件系统 / poisoned_saber 丢弃；同化种 → `corruption_*`，Hall 畸骸种 → `hall_*`；corruption 内容尽可能简化。

---

## 1. 已完成（本分支当前状态）

### 1.1 工程与命名空间
- [x] 合并前基线提交（含此前 167 项未提交成果）+ 备份副本 `Project\_RottenRuinsSplendiding_backup_20261028`。
- [x] modId `hall` → `rotten_ruins`；包 `org.bytechen.hall` → `org.bytechen.rottenruins`。
- [x] 资源命名空间 `assets/hall` → `assets/rotten_ruins`、`data/hall` → `data/rotten_ruins`；mixin 配置改名 `rotten_ruins.mixins.json`（package/plugin/refmap 同步）。
- [x] 手写 JSON/着色器/last 资源内的 `hall:` 引用全部迁移；历史空目录 `data/splendiding`、`data/godsick_neo` 清理。
- [x] GeckoLib **4.7.1 → 4.8.4**（compileOnly + patchModuleInfo + jarJar 范围 `[4.8.4,4.9)`），JPMS 剥离流程沿用。
- [x] `mods.toml`：infcore 依赖收紧为 mandatory `[0.1.2,)` + `ordering = AFTER`。
- [x] `compileJava` 通过；`runData` 通过（生成 230 个语言键等）。

### 1.2 生物体系（统一基类）
- [x] `AbstractHallEntity` → **`AbstractRottenEntity`**（根仍 `Monster`，105 处引用同步），新增 `RottenFaction { HALL, CORRUPTION }` 字段与 `getFaction()/setFaction()`。
- [x] Hall 侧 4 个 family 薄层保留（apostle / ecological / infected / ulcerated）。
- [x] 新增腐化系 1 个公共基类 `BaseCorruptionEntity` + 6 个 family 薄层（host / degenerate / deteriorated / refactored / linked / original）。
- [x] corruption 侧 **23 只生物**简化移植（统一基类 + family 薄层 + infcore `InfectedTargetGoal` 索敌 + 基类追击 + 近战 + 随机漫步）：
  - 同化种 11：`corruption_{zombie,player,cow,sheep,pig,bat,skeleton,bone_ruins,villager,wolf,chicken}`
  - 腐化种 4：`degenerate_{pig,zombie,villager,cow}`
  - 退化种 1：`groveler`；重构种 4：`attacker, bearer, long_arm_beast, shocker`；联结种 2：`humus_column, summoning_pillar`；原虫 1：`original_bug`
- [x] Hall 畸骸种 id 改名：`inf_player/inf_skeleton/inf_enderman/inf_skeleton_arrow/inf_ender_pear` → `hall_player/hall_skeleton/hall_enderman/hall_skeleton_arrow/hall_ender_pear`，对应 geo/动画/贴图/模型同步改名。
- [x] 未移植：corruption 的 Brain/SmartMove/SmartTarget/寻路、技能与投射物（骨刺/酸球/腐蚀弹/红矮星等）、King 系（HeavyGuard/Avila）。

### 1.3 腐化内容（简化版）
| 类别 | 数量 | 说明 |
|---|---|---|
| 方块 | 21 | 9 个普通地貌方块仍按 cube_all 占位；12 个特殊方块已按 corruption 原实现移植（见 1.6） |
| 方块物品 | 21 | 自动注册，掉落表为自掉落 |
| 物品 | 42 | 材料 14 + 粗制工具 5（石级 Tier）+ 注射器 2（占位无功能）+ 刷怪蛋 21/23 |
| 效果 | 6 | corruption / lysis / miasma / sclerosis / bleeding / fear（流血、腐化有周期扣血，其余占位） |
| 生物 | 23 | 见 1.2 |
| 语言 | 92 键 × 中英 | 已并入 datagen `LangDataCN/EN` |
| 资源 | 255 文件 | geo 23 / 动画 23 / 实体贴图 24 / 方块贴图 21 / 物品贴图 40 / blockstate 21 / 方块模型 21 / 物品模型 61 / 掉落表 21 |

### 1.4 数据生成与验证
- [x] `runData` 成功（loot tables / lang / models / worldgen 重新生成；`hall_*` 实体掉落表、语言键已生成，zh/en 各 230 键）。
- [x] `runClient` 冒烟：mod 构造、mixin 加载、着色器/mask 层、难度系统均正常启动进游戏（旧 dev 存档因旧 `hall:*` 注册名报 137 条 missing 警告，符合弃档决策；无崩溃）。
- [x] `runServer` 新世界：**Done (13.921s)**，新世界生成、数据包/战利品表加载、infcore 难度迁移（`infcore:normal` → `rotten_ruins:normal`）全部正常，无 ERROR/FATAL。
- [x] `gradlew build` 成功，产物：`build/libs/rotten_ruins-1.0.0.jar` / `-all.jar` / `-dev.jar` / `-sources.jar`（jarJar 含 cloth-config + GeckoLib 4.8.4）。
- [x] 提交：`01692cf feat: 合并 corruption 简化内容到 rotten_ruins`（连同改名提交 `acddd73`、基线 `f5e0977`）。

> 备注：旧 dev 服务器世界已改名为 `run/world_pre_rotten_ruins_backup`（未删除）；旧单机存档 `run/saves/*` 按弃档决策原样保留、未做迁移。

### 1.5 创造物品栏分区（本轮新增）
- [x] 重写 `RegisterTab`：物品按功能分 6 类，每类内先王庭（原 Hall）后腐化（原 corruption）：
  | 分区 | 键 | 数量 | 内容 |
  |---|---|---|---|
  | 刷怪蛋 | creatures | 33 | 王庭 10 + 腐化 23 |
  | 材料 | materials | 23 | 王庭 11 + 腐化 12 |
  | 魔法 | magic（新增） | 10 | 六系法力板 + 催化剂 + 虚空符文 + 2 法杖 |
  | 武器与工具 | gear | 16 | 王庭 9 + 粗制工具 5 + 注射器 2 |
  | 方块 | blocks | 54 | 王庭 33（含血肉裂隙/矿石）+ 腐化 21 |
  | 其它 | misc | 1 | `hall_ender_pear`；另保留兜底扫描 |
- [x] 分区标题随内容更新：主标题 `腐朽之疫` / `Rotten Ruins`，分区英文改为通用名（Spawn Eggs / Materials / Magic / Weapons & Tools / Blocks / Miscellaneous）。
- [x] 占位图标 `example_item` 不再展示在创造栏（仅作 tab 图标）。
- [x] 校验脚本确认注册表 137 个展示物品（不含 example_item）**全部被分区覆盖、无重复、无拼写错误**；`runData` 重新生成语言（中英各 231 键），`compileJava`/`build` 通过。

### 1.6 特殊方块模型与 datagen（本轮新增）
从 corruption 工程按原实现移植了"有单独模型 / 特殊格式 datagen"的 12 个方块：

| 方块 | 移植内容 |
|---|---|
| 残骸 ×9（腐化/同化/重构 × 小/中/大） | 新基类 `RemainsBlock`（六向 `facing`、贴面碰撞箱、玩家可穿过、可旋转/镜像）；datagen `directionComplexBlockWithItem` 生成十字交叉特殊格式模型（两片 45° 平面 + 底面）+ 六向 blockstate；新增 `*_side.png` 9 张 |
| 血肉温床 | 新类 `FleshbedBlock`（水平朝向 + 模型渲染）；手写 Blockbench 模型 `models/block/fleshbed.json` 原样迁入（改命名空间）；datagen 生成水平朝向 blockstate + 物品模型 |
| 腐染矮草 | 新类 `CorruptionGrassBlock`（cross cutout 模型；只认腐染泥土、下方消失即破坏）；datagen 生成 cross 模型 + blockstate + 物品模型 |
| 腐染经络 | 新类 `CorruptionVariableHeightCropBlock`（`age` 0~25 + `section(bottom/middle/top)`、随机生长、只认腐染泥土）；datagen 生成三段 cross 模型与 `age×section` 变体 blockstate；新增 `_bottom/_middle/_top.png` 3 张 |

- 新增 datagen provider：`datagen/gen/corruption/CorruptionBlockStateData`（在 `DataGenEvent` 注册；复写了 `getName()` 以避免与 Hall 的 `BlockStateData` 重名冲突）。
- 删除了这 12 个方块此前的静态占位 blockstate / 方块模型 / 物品模型，统一由 datagen 生成。
- 校验：`runData` 生成的 34 个对应 JSON 与 corruption 工程产物**逐字段一致**（仅命名空间不同）；`compileJava` / `build` / 客户端资源加载（无 missing model/texture 报错、无崩溃）全部通过。
- 仍待补：血肉温床的 BlockEntity（培养物/生物量）与配方交互（原 `FleshbedRecipeConfig`/`InfectionItem`）。

### 1.7 两派阵营、包结构与感染规则（本轮新增）
- [x] **包结构**：`population` 下按来源分成两个平级包，不再混在一起：
  `population/hall/{apostle,defensive,ecological,infected,skills,ulcerated}`（原 Hall 内容）与
  `population/corruption/{host,degenerate,deteriorated,refactored,linked,original}`（原 corruption 内容）。
- [x] **阵营类型分离**（`RottenFaction`）：两派不是从属关系，各有独立 id：
  | 概念 | 王庭 | 腐化 |
  |---|---|---|
  | 阵营标识（`getInfectionType`） | `rotten_ruins:hall` | `rotten_ruins:corruption` |
  | 击杀感染规则 type | `rotten_ruins:hall_inf` | `rotten_ruins:corruption_inf` |
  | 自身进化规则 type | `rotten_ruins:hall_evo` | `rotten_ruins:corruption_evo` |
- [x] `AbstractRottenEntity.getInfectionType()` 改为返回 `faction.infectionType()`；
  击杀感染使用 `faction.infectionRuleType()`；巨碑/孕育者等自进化使用 `faction.evolutionRuleType()`。
- [x] **王庭机制只作用于王庭阵营**：友伤取消、黑洞豁免、难度属性倍率、使徒技能同类判定、
  缠绕之柱同族判定、陨石治疗，全部从"命名空间判断"改为 `RottenFaction.HALL.matches(type)`（腐化系不吃这些王庭机制，后续按各自机制补）。
- [x] **corruption 感染映射已迁成 infcore 数据写法**（`EvolutionDataProvider`）：
  - `corruption_inf`：蝙蝠/猪/羊/牛/狼/鸡 → `corruption_*`，骷髅/僵尸/村民/玩家 → 对应同化种
  - `corruption_evo`：孕育者 → 长臂兽 / 震撼者（50/50）
  - 同时修复了王庭旧数据中"巨碑两条同 source 规则互相覆盖"的 bug（改为一条 entry + 两个 target）
  - 原工程的"村民非幼年 + 精英名 + 持久化"回调 infcore 数据格式暂不支持，留待实体/事件层补
  - `runData` 生成 4 个 type / 14 条规则，类型字符串与运行期传入的 `RottenFaction.*RuleType()` 完全一致。

### 1.8 修复记录：刷怪蛋放不出生物（2026-10-28）
- **现象**：两派所有生物的刷怪蛋都无法生成生物（服务端日志刷 `NullPointerException`）。
- **根因**：阵营信息写成了实例字段 `faction`，而 `Mob` 的构造器会在子类字段初始化之前调用
  `registerGoals()`；`InfectedTargetGoal` 的构造器又会调用 `getInfectionType()` →
  此时 `faction` 仍是 `null`，直接在实体构造期 NPE，`EntityType.spawn` 失败。
- **修复**：把阵营改成可覆写方法 `protected RottenFaction faction()`（基类返回 HALL，
  `BaseCorruptionEntity` 覆写返回 CORRUPTION）；`getInfectionType()` / `onKilledEntity()` 改用 `faction()`。
  这样构造期虚分派也能拿到正确常量，不再依赖字段初始化时机。
- **验证**：dedicated server + RCON 依次 `summon` 王庭 7 只 + 腐化 8 只（含玩家/末影人/骷髅/斥候/碎骨/使徒/缠绕之柱/同化僵尸/同化牛/同化玩家/腐化猪/袭击者/孕育者/腐植柱/原虫），全部返回 `Summoned new ...`，随后 tick 无任何 ERROR/FATAL。
- **附带修复**：`InfPlayerEntity` / `BonecrusherEntity` 的通用 tick 里用 `this.level() instanceof ClientLevel` 判断客户端，
  在 dedicated server 上会触发 `RuntimeDistCleaner` 崩溃，已改为 `this.level().isClientSide()`。

### 1.9 技能补齐 阶段 0：基础设施与资源（2026-10-29）
- [x] 移植技能基础设施（编译通过）：
  | 文件 | 位置 | 说明 |
  |---|---|---|
  | `CorruptionSkillFx` | `.../population/corruption/skill/` | 技能视觉反馈（dust/烟/冲击环/粒子柱/吐息/血雾），纯服务端广播，原样移植 |
  | `CorruptionDamageUtil` | `.../utils/combat/` | 统一伤害入口（盾牌/不死图腾/无视无敌帧/分级伤害）；`ModUtils.setDataHealthID` 用 `setHealth` 替代 |
  | `CorruptionSkillUtil` | `.../population/corruption/skill/` | 51 个技能工具方法（先兆/范围伤害/拉拽/突进/效果/吸血/友军增益…）；目标筛选排除本模组两派感染生物，友军判定只认腐化阵营；`spawnPoolAt` 留待腐化池实体移植后接回 |
- [x] 技能实体资源已复制（8 文件，SHA-256 一致）：geo `acid_ball/bone_spike/poison_arrow`、动画 3、实体贴图 2；`textures/item/acid_ball.png` 已存在。
- [x] 渲染方式已确认：`CorruptionDart`/`CorruptionPoolEntity` 空渲染器 + 粒子；`CuboidEntity`/`ShieldEntity` 自绘 POSITION_COLOR；`AcidBall/BoneSpike/PoisonArrow` 走 Geo 弹射物（可复用 `AbstractHallProjectile` + `GeoProjectileRenderer`）。
- [ ] 后续批次：`SkillProjectileRenderer`、`CuboidRenderer`、`ShieldRenderer` 与 7 个技能实体类；4 个家族生物的技能逻辑；6 个腐化粒子 + 9 个自定义音效。

### 1.10 技能补齐 阶段 1（2026-11-01）：技能实体层 + degenerate 技能 + 移动速度修复

#### 移动速度问题（本轮修复）
- **根因**：corruption 原工程的 `walkSpeed/runSpeed` 是 **moveTo 目标速度倍率**（0.8–2.0），
  而合并工程 `AbstractRottenEntity` 把这两个值直接写进 `MOVEMENT_SPEED` 属性
  （Hall 语义）。合并时把腐化猪/村民/僵尸等的 `runSpeed=0.9~1.0` 当属性写进去 →
  追击时实际速度是玩家的 3.6–4 倍；未单独设置速度的家族则落到基类默认 `0.6/1.2`（4.8 倍）。
- **修复**：基类默认值 `0.6/1.2 → 0.18/0.24`；`BaseCorruptionEntity` 默认 `0.20/0.26`；
  20 个腐化生物按原工程 `Attributes.MOVEMENT_SPEED` 重设（walk≈0.9x、run≈1.2x）：
  - 0.26 系（degenerate 4 / groveler / host 多数 / long_arm / original_bug / summoning）→ `0.23/0.31`
  - 蝙蝠 → `0.18/0.24`；鸡 → `0.21/0.21`；骷髅 → `0.14/0.18`
  - attacker → `0.16/0.22`；bearer → `0.14/0.18`；shocker → `0.22/0.29`
  - 固定不动的（骨堆 / 腐植柱 / 召唤柱）保持 `0/0`

#### 技能实体层（阶段 1，已编译）
- 新增 `.../population/corruption/skill/`：`AbstractCorruptionProjectile`（基类）、
  `BoneSpikeEntity`、`CorruptionDart`（通用腐蚀弹体：伤害/颜色/重力/落地池/命中效果运行时配置）、
  `CorruptionPoolEntity`（持续腐蚀池）、`CuboidEntity`（方块墙）、`ShieldEntity`（护盾）、
  `AcidBallProjectile`、`PoisonArrowProjectile`。
- 实体注册（`CorruptionEntityTypes`）：`BONE_SPIKE / ACID_BALL / POISON_ARROW` 走
  `EntityTypeRegistry.registerProjectile`（Geo 弹射物），`CORRUPTION_DART / CORRUPTION_POOL /
  CUBOID / SHIELD` 走 noop 实体 + `ClientModEventHandler` 手动渲染。
- 客户端渲染：新增 `SkillProjectileRenderer`（粒子弹体空渲染）、`CuboidRenderer`、`ShieldRenderer`。
- `CorruptionSkillUtil.spawnPoolAt` 已接回 `CorruptionPoolEntity`。

#### 生物技能（degenerate 家族 + 畸变种）
- 腐化猪 **C3 蓄力冲锋**：蓄力 24t → 三段冲撞（速度/伤害递增），撞墙打断连段并长后摇。
- 腐化牛 **C4 屠宰者暴冲**（半血血怒：增伤/提速/抗击退） + **C5 血肉偿还**
  （割 40% 当前血量射出等比血爆弹，命中附加流血，落点留腐蚀池）。
- 腐化村民 **C1 酸液瘴气弹**（抛物线酸弹 + 酸池 + 裂解） + **C2 硬化召唤**
  （低血召唤 4 面血肉墙、墙会伤害触碰者、自身回血） + 保留 15t 酸球。
- 匍匐者 **C10 地底突袭**：钻地不可选中 → 目标脚下预警 14t → 破土范围真伤 + 减速 + 流血。
- 腐化僵尸：原工程无独立技能（纯近战），仅保留假死/属性。

#### 后续待办
- refactored 4（Attacker/Bearer/LongArmBeast/Shocker）、host 11、linked 2、original 1 的技能接入；
- 技能动画通道（合并基类目前只有 idle/walk/run/die 控制器，原 `triggerAnim` 未接）；
- 腐化粒子和自定义音效（缺口清单见 `corruption-skill-assets.md`）。

### 1.11 技能补齐 阶段 2（2026-11-01）：refactored 家族技能

- **BaseRefactoredEntity（C9 殉照领域）**：重构种死亡（爆体结束）时在倒地处留下腐蚀池；
  `onBurstEnd()` 里依次做粒子爆发 + 冲击环 + `spawnPoolAt`，再走基类爆体结算（掉落/音效不变）。
  目前只有孕育者启用（与原工程一致：原工程只有 Bearer 调 `enableDeathField`）。
- **袭击者 C8 扑击后撤**：16t 蓄力 → 突进，命中后立刻沿"目标→自己"方向后跳脱战（110t 冷却）；
  每 50t 对 10 格内目标做一次"跳得远但不高"的低空远跳。
- **孕育者（吞噬 + 进化）**：每 10t 搜附近孕育者，吞噬值高的吃低的；接触 12t/点后吞掉同类，
  吞噬值 +50%、血量上限小幅提升，体型随吞噬值放大；吞噬值 ≥2.5 时通过 infcore
  `EvolutionManager.applyEvolution(..., corruption_evo)` 进化为长臂兽/震撼者（50/50）。
  死亡领域按体型放大（原工程 `scaleDeathFieldBySize`）。
- **震撼者 C6 过载脉冲**：36t 蓄力（粒子汇聚 + 头顶烟柱 + 升调音效）→ 6.5 格全向脉冲，
  6 点无视无敌帧 + 10 点常规伤害，命中减速 III + 虚弱；脉冲后硬直 200t。
  近战/撞障碍时用简化版 3.2 格范围横扫（原 `CorruptionAreaAttackUtil` 圆柱扇区未移植，TODO）。
- **长臂兽 C7 钩刺拖拽**：20t 蓄力 → 用 `CuboidEntity` 弹出细长爪钩（10t 飞行），
  命中/到点把目标伤害 7 并拉到身前 2 格、减速 + 流血；140t 冷却。

#### 仍未接入
- host 11 只（同化种全套技能）、linked 2 只（腐植柱 buff/召唤柱）、original 1 只（原虫）；
- 技能动画通道（idle/walk/run/die 之外的触发式动画）、腐化粒子与自定义音效缺口。

### 1.12 技能补齐 阶段 3（2026-11-01）：linked 家族 + 原生种

- **BaseLinkedEntity（C9 殉照领域）**：联结种死亡时按各自配置留下腐蚀池（腐植柱 4 格 / 召唤柱 3.5 格），
  接 `onBurstEnd` 钩子。
- **腐植柱（C13 强化 + 净化脉冲）**：
  - 每 100t 对 10 格内一个盟友施加增益（优先移除一个负面效果，否则随机正面 buff，可叠加），
    并对随机一个敌人施加减益（权重表：虚弱/缓慢/中毒/凋零/失明/饥饿/厄运/腐蚀/裂解/恐惧/流血）；
  - 每 160t 释放「净化脉冲」：清掉 14 格内所有腐化盟友的负面效果 + 回血，并短暂发光提示；
  - 站桩（`travel` 归零、不可推动），死亡留 4 格腐蚀池。
- **召唤柱（C12 群体召唤）**：每 240t 在附近有敌人时召唤 2 只同化种（僵尸 40% / 鸡 30% / 狼 20% / 骷髅 10%），
  区域同族上限 10；人口满时改为给部队上抗性；召唤前 40t 有粒子蓄势；死亡留 3.5 格腐蚀池。
- **原虫（C11 酸液喷射）**：14t 吸气蓄力（粒子汇聚 + 升调音效）→ 5.5 格 60° 锥形酸雾，
  5.5 点伤害 + 虚弱 II + 裂解 + 腐蚀（破甲窗口 120t）；130t 冷却。

#### 仍未接入
- host 11 只（同化种全套技能）——技能实体与工具已就绪，下一轮处理；
- 技能动画通道（触发式攻击动画）、腐化粒子与自定义音效缺口。

### 1.13 技能补齐 阶段 4（2026-11-01）：host 家族 11 只

**BaseHostEntity 共用 B 系开关**（各生物在构造器按需开启）：
- **B1 腐化唾液吐息**：目标 2.5~range 时吐一枚 `CorruptionDart`（伤害/冷却/射程可配），命中附腐蚀；
- **B9 尸群践踏**：每 20t 检查附近同族数，达到要求则全队加速 II + 力量 140t；
- **B10 濒死骨爆**：**爆体结束**（`onBurstEnd`，先于基类结算）环状发射 `BoneSpikeEntity`（数量/伤害可配，两层仰角）；
- **B11 血肉牵引**：近战命中后把目标拉向自己 + 流血；
- **B12 汲血回复**：近战命中后按伤害比例回血（残血翻倍，SkillUtil 内实现）。

**逐只技能**（11 只全部接入）：
| 生物 | 技能 |
|---|---|
| 同化僵尸 | 近战 + B10（10 根 / 3.5） |
| 同化玩家 | 近战 + B10（8/4.0）+ B11（0.9）+ B12（0.35） |
| 同化羊 | 近战 + B10（12/3.0）+ B9（4 只 / 12 格） |
| 同化猪 | 近战 + B9（3 只）+ B11（0.8）+ B12（0.3） |
| 同化村民 | **B8 同化传播**：命中玩家叠腐化层数（≤4，层数=放大器），第 4 层触发失控（缓慢 IV + 失明 + 虚弱 + 反胃）；+ B1（4.5/60/14）+ B12（0.5） |
| 同化牛 | **B5 狂奔冲撞**：22t 蓄力 → 0.62 速度冲刺，命中 9 伤 + 腐蚀 + 反胃；撞墙自伤 4 + 反胃 + 缓慢；+ B9（3）+ B12（0.3） |
| 同化骷髅 | **B6 腐化箭雨**（落点预警 + 5 枚腐蚀弹，附带落地池）+ **B7 射速强化**（半血攻击速度 +60%）+ `RangedAttackGoal` 毒箭远程 |
| 同化狼 | **B2 感染嚎叫**（队伍 ≥2 时群体增益 + 召来 1~2 只狼，上限 6）+ **B3 扑咬锁喉**（14t 前摇 → 咬住 40t，持续减速/流血/每 10t 撕咬，甩飞）；+ B9/B11 |
| 同化鸡 | **B4 嘶鸣分裂**（受击 25% 且冷却 60t、同屏 ≤8：生成 2 只不再分裂的小体 + 周围击退） |
| 同化骨堆 | **站桩自爆**：每 20t 扫描 8 格，发现玩家后 60t 喷发（粒子 + 流血），结束时爆炸 5 伤并 discard |
| 同化蝙蝠 | **B13 蝙蝠夜袭**：瞬移到目标背后 2 格，咬击 3.5 + 吸血 60% + 恐惧；+ B1/B10/B12（原飞行 AI 未移植，用瞬移保留核心威胁） |

**简化点（TODO）**：同化骷髅死亡 35% 生成骨堆、同化蝙蝠飞行 AI、B1 连发（多 burst）、部分音效/粒子沿用原版替代。

### 1.14 修复：walk/run 动画不匹配（2026-11-01）

**现象**：日志反复刷 `Unable to find animation: run for ShockerEntity / DegenerateCowEntity`；
同时部分有 run 动画的生物 run 从未被播放。

**根因**：合并基类 `AbstractRottenEntity` 的 `movement_controller` 用 `hasWalkAnimation()`
决定播放 walk 还是 run。`setHasWalkAnim(true)` 代表"走/跑两段动画都有"，
但合并时这个标记与各生物 animation json 的实际片段并不一致：有的模型没有 run 却标了 true（刷警告），
有的模型只有 walk，有的模型连 walk 都没有（蝙蝠只有 idle/fly/speed_fly）。

**修复（对照每个生物的 animation json 逐只核对）**：
| 生物 | 动画片段 | 修正 |
|---|---|---|
| 腐化牛 / 匍匐者 / 长臂兽 / 震撼者 / 原虫 | 无 `run` | `setHasWalkAnim(false)`（始终走 walk，不再请求 run） |
| 同化蝙蝠 | 只有 `idle` / `fly` / `speed_fly`，无 walk/run | 重写 `registerControllers`：不移动播 idle、移动播 fly |
| 其余（僵尸/玩家/同化牛/同化村民/同化狼/degenerate 猪、僵尸、村民/袭击者/碎骨等） | 有 `run` | 保持 `true`，追击时正常切 run |
| 同化羊/猪/骷髅/鸡/孕育者/骨堆/腐植柱/召唤柱 | 无 `run` | 保持 `false`（本就只播 walk/idle） |

核对方式：脚本 `_merge_scripts/audit_animations_all.py` 从两个实体注册表提取
`类 → defaultResources(name)` 映射，读取 `assets/rotten_ruins/animations/<name>.animation.json`
的 `animations` 键，与类里的 `setHasWalkAnim` 标记比对；当前剩余告警为 0。
（无声/无 walk 的站桩生物：骨堆 / 腐植柱 / 召唤柱 速度与 travel 均为 0，永远走 idle，不会请求 walk。）

### 1.15 玩法调整：一阶召唤柱强化 + 感染生物击杀进阶（2026-11-01）

#### 一阶召唤柱（`SummoningPillarEntity`）
- 最大生命 **23 → 60**；
- **常驻回血**：每 20t 恢复 1 点生命（无论是否在战斗中）；
- **腐植柱守卫**：每隔 100t 检查自身 16 格内的腐植柱数量，不足 2 个时在周围补召（新召出的腐植柱
  `setPersistenceRequired()`，不会自然消失）；原有 C12 宿主召唤逻辑保留；
- 死亡时依旧按 C9 殉照领域留下 3.5 格腐蚀池。

#### 感染生物击杀进阶（宿主家族 → 腐化种）
- 同化种（`BaseHostEntity` 家族）击杀数达到 **3** 时，按 infcore 进化表进化为对应的腐化种：
  - `corruption_zombie` → `degenerate_zombie`
  - `corruption_cow` → `degenerate_cow`
  - `corruption_pig` → `degenerate_pig`
  - `corruption_villager` → `degenerate_villager`
- 没有腐化种对应的（骷髅 / 狼 / 蝙蝠 / 鸡 / 玩家 / 骨堆）不配置规则，`applyEvolution` 自然不触发；
- 触发时播放粒子爆发 + 冲击环，然后由 infcore `EvolutionManager` 生成对应的腐化种实体；
- 数据侧在 `EvolutionDataProvider` 里以 `corruption_evo` 类型新增 4 条规则（`runData` 已生成，总计 18 条规则）。

### 1.16 跨阵营索敌：王庭 → 腐化（2026-11-01）

**索敌**：王庭侧 10 个 `InfectedTargetGoal` 全部加上 `.targetOtherInfected(true)`：
- 王庭生物（`hall_player / hall_skeleton / hall_enderman / scout / pursuer / monolith /
  bonecrusher / heavy_bomb / collapsar / hearthstone`）现在会主动索敌腐化生物；
- infcore 的感染过滤会自动拒绝"相同感染类型"目标（王庭内部仍不互殴），
  腐化生物对王庭的索敌仍保持关闭（单向）。

**转换规则**（`hall_inf`，王庭击杀腐化生物时触发）：
- 有对应王庭形态的走显式规则：
  - `corruption_player` → `hall_player`
  - `corruption_skeleton` → `hall_skeleton`
- 其余腐化生物走 `UlceratedConversionRules` 的体积兜底：
  小体积 → 痈溃斥候，中体积 → 痈溃追迹者，大体积 → 痈溃巨碑；
- 生成规则写入 `EvolutionDataProvider`，`runData` 已重新生成（`hall_inf` 现在 4 条规则，总计 20 条）。

### 1.17 腐化群系（真实 biome）跟随方块扩散（2026-11-01）

#### 腐化群系
- 新增真实群系 `rotten_ruins:corruption_wasteland`（`CorruptionBiomeData`，已并入
  `HallWorldGenData` 的 `Registries.BIOME` bootstrap，`runData` 生成
  `data/rotten_ruins/worldgen/biome/corruption_wasteland.json`）：
  - 环境：暗红雾 / 暗红天空 / 暗红水面 / 腐化草叶着色 / 灰烬粒子；
  - 自然生成表（monster 21 项）：同化种 3~20、腐化种 3~5、重构种 1~2、
    腐植柱 3、**一阶召唤柱 1（相对较少）**。

#### 群系跟随方块扩散（与王庭完全同一条管线）
- `CorruptionBiomeSpreadHandler` 监听 infcore 的 `BlockSpreadEvent.Post`：
  只有 `rotten_ruins:corruption` 类型的扩散才处理；
- 每次腐化方块被放下 → 把该方块位置入队 `HallBiomeProcessor.enqueueForward(...)`
  （与 hall_wasteland 共用同一个按维度隔离的群系扩散处理器）；
- 处理器每 tick 最多写 40 列，用 infcore `BiomeSpreadHelper.setColumnQuartBiome`
  把整列群系改成腐化群系，并 `syncChunk` 立刻同步给客户端；
- 所以**方块扩散到哪，腐化群系就跟到哪**，和 Hall 的 `hall_wasteland` 逻辑一致。

#### 方块感染
- `CorruptionBlockSpreadDataProvider`（type `rotten_ruins:corruption`，55 条规则）：
  草/土/沙/石/圆石/树叶/原木/花草 → 对应腐化方块；
- 一阶召唤柱每 20t 感染脚下方块并向周围扩散（半径 4/6），腐土表面 25% 长腐化草。

#### 自然生成
- 腐化生物通过**群系 spawners 自然生成**（不再用之前的"区域刷怪器"临时方案）；
- 由于群系随方块扩散，腐化区扩大后自然生成范围也随之扩大；
- 召唤柱也在群系 spawners 里，权重 1（相对较少）。

> 说明：之前阶段 4 的 `CorruptionZoneData` / `CorruptionZoneSpawnHandler` 区域刷怪方案
> 已被移除（它只是无 biome 时的临时替代），现在统一走真实群系 + 群系生成表。

### 1.18 腐化方块自主扩散（2026-11-01）

**之前为什么不会自己扩散**：腐化地貌方块（spread_dirt / spread_stone / infection_cobblestone_block /
spread_infection_leaves / spread_infection_wood）注册时是普通 `Block`，没有 `randomTick`，
也没有实现 infcore 的 `ISpreadBlock`；扩散数据虽然存在，但只有召唤柱在手动调 `applySpread`。

**修复**：新增 `CorruptionSpreadBlock`（与王庭 `AbstractSpreadBlock` 同一套逻辑）：
- 实现 `ISpreadBlock#getSpreadType()` → `rotten_ruins:corruption`；
- `isRandomlyTicking = true`，`randomTick` 里调用
  `SpreadHelper.trySpreadNearby(state, level, pos, random, this, 3, 0.5F)`；
- 腐土顶面 20% 概率长出 `corruption_grass`；
- 上述 5 个方块改为 `CorruptionSpreadBlock` 并加 `.randomTicks()`。

由于所有扩散都会触发 `BlockSpreadEvent.Post`，腐化群系会随自主扩散一起自然扩张
（`CorruptionBiomeSpreadHandler` → `HallBiomeProcessor` → infcore `BiomeSpreadHelper`）。

### 1.19 腐染矮草改为两格高 + 降低密度（2026-11-01）

**只长一格高的原因**：`CorruptionGrassBlock` 之前继承 `TallGrassBlock`——那是**单格**的矮草类，
没有 `HALF` 状态，所以永远不会出现第二格。

**改为两格高**：
- `CorruptionGrassBlock` 改为继承 `DoublePlantBlock`（`HALF = LOWER / UPPER`）；
- 下半格只能长在 `spread_dirt` 上；上半格必须踩在下半格上；
- 下半格随机 tick 时若上方是空气，12% 概率 `placeAt` 长出上半格，所以会出现
  一格高和两格高混生的效果；玩家用物品放置时也会直接放两格；
- datagen 生成两个 blockstate 变体（`half=lower` / `half=upper`），模型同用十字模型；
- 战利品表改为只有下半格掉落（`block_state_property: half=lower`），并保留
  `DoublePlantBlock` 的"拆上带下"行为。

**降低密度**：
- `CorruptionSpreadBlock.randomTick` 里腐土长草概率 `20% → 4%`；
- 召唤柱扩散时腐土长草概率 `25% → 8%`。

---

## 2. 与完整目标相比的简化点（后续补）

### 机制层（全部未迁）
1. 节点系统（空间感染强度）——已定不搬；"腐化强度"待用 infcore 演化值/难度/群系存在感替代。
2. 方块扩散与群系替换（corruption 的 SpreadRuleManager/BiomeCorruptionProcessor/NodeManager）——待接入 Hall `swarm` + infcore `BlockSpreadManager`/`BiomeSpreadHelper`。
3. 同化 / 演化（AssimilationMapManager / BearerManager）——待转 `data/rotten_ruins/infcore_evolution/*.json`。
4. 腐化自定义刷怪（CorruptionSpawnConfig/CorruptionSpawnEventHandler）——Hall 的 SpawnPlacements 也仍是空实现，需统一新建。
5. 技能 / 投射物系统（骨刺、酸球、腐蚀弹、红矮星等）与相关渲染。
6. 自定义粒子（blood_splat/splash/fog 等 6 个）、腐化音效（9 个 ogg）、JEI 插件、成就/进度、实体掉落表、配方（粗制工具/注射器/温床等）。
7. 玩家皮肤/模型切换系统（corruption_player slim/wide）、OnlineSkinDownloader（已定不搬）。

### 表现/内容层占位
- 剩余 9 个腐化地貌方块仍是 cube_all 满方块（`spread_dirt/stone/leaves/wood`、`infection_cobblestone_block`、`corruption_trachea`、`connect_block`、`decidual_tissue_block`、`spiracle_block`）；残骸/血肉温床/腐染矮草/腐染经络 12 个已换成真实模型（见 1.6）。
- 注射器、粗制工具的手感/配方等待调；`fleshbed` 无方块实体与转化逻辑（模型已完成）。
- corruption 生物只有基础索敌/近战，无原技能；蝙蝠暂按地面单位处理（TODO 飞行）。

---

## 3. 关键文件地图

| 内容 | 路径 |
|---|---|
| 统一生物基类 | `src/main/java/org/bytechen/rottenruins/overworld/registry/entities/base/AbstractRottenEntity.java` + `RottenFaction.java` |
| 腐化系统基类/薄层 | `.../entities/population/corruption/BaseCorruptionEntity.java` + `Base*Entity.java` |
| 腐化生物 | `.../entities/population/corruption/{host,degenerate,deteriorated,refactored,linked,original}/` |
| 腐化注册入口 | `.../overworld/registry/corruption/CorruptionContent.java`（由 `HallMod` 构造器调用） |
| 腐化方块/物品/生物/效果注册 | `.../overworld/registry/corruption/Corruption{Blocks,Items,EntityTypes,Effects}.java` |
| 腐化资源 | `src/main/resources/assets/rotten_ruins/{geo,animations,textures,blockstates,models}` |
| 腐化语言 | `.../datagen/gen/lang/LangDataCN.java` / `LangDataEN.java`（末尾追加） |
| 腐化掉落表（方块） | `src/main/resources/data/rotten_ruins/loot_tables/blocks/` |
| 分析/方案文档 | `docs/merge/`（proposals + 5 份分析 + port-assets） |

---

## 4. 下一轮建议优先级

1. **进游戏实测特殊方块**：残骸六向放置、腐染矮草贴地消失、腐染经络生长分段、血肉温床朝向与模型。
2. 血肉温床机制补齐：BlockEntity（培养物/生物量）+ 配方配置/交互（原 `FleshbedRecipeConfig`/`InfectionItem`）。
3. 世界机制闭环：infcore blockspread（`rotten_ruins:corruption_spread`）+ 刷怪（SpawnPlacements）+ 演化数据。
4. 剩余 9 个腐化地貌方块的方块行为（扩散/腐染转化，接 Hall `swarm` + infcore）。
5. 注射器/粗制工具/掉落/配方等玩法补齐。
6. 技能与投射物系统（按模块逐个恢复）。
