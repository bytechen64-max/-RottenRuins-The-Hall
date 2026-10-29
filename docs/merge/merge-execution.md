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
