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
| 方块 | 21 | 全部按普通满方块占位（cube_all 模型），无扩散/生长/方块实体；cube_all 资源见 `assets/rotten_ruins/{blockstates,models/block}` |
| 方块物品 | 21 | 自动注册，掉落表为自掉落 |
| 物品 | 42 | 材料 14 + 粗制工具 5（石级 Tier）+ 注射器 2（占位无功能）+ 刷怪蛋 21/23 |
| 效果 | 6 | corruption / lysis / miasma / sclerosis / bleeding / fear（流血、腐化有周期扣血，其余占位） |
| 生物 | 23 | 见 1.2 |
| 语言 | 92 键 × 中英 | 已并入 datagen `LangDataCN/EN` |
| 资源 | 255 文件 | geo 23 / 动画 23 / 实体贴图 24 / 方块贴图 21 / 物品贴图 40 / blockstate 21 / 方块模型 21 / 物品模型 61 / 掉落表 21 |

### 1.4 数据生成与验证
- [x] `runData` 成功（loot tables / lang / models / worldgen 重新生成；`hall_*` 实体掉落表、语言键已生成）。
- [x] `runClient` 冒烟：mod 构造、mixin 加载、着色器/mask 层、难度系统均正常启动进游戏（旧 dev 存档因旧 `hall:*` 注册名报 137 条 missing → 符合弃档决策）。
- [ ] `runServer` 新世界（进行/待记录）。
- [ ] `gradlew build` 产物验证（待跑）。

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
- 21 个方块全是 cube_all 满方块（遗骸/经络等真实模型可从 corruption 的 generated 资源替换）。
- 腐化内容暂未单独分区，落入创造栏 `misc` 兜底分区。
- 注射器、粗制工具的手感/配方等待调；`fleshbed` 无方块实体与转化逻辑。
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

1. **先跑 `gradlew build` + 新世界 `runServer` 完整验收**，确保 jar 产物可用。
2. 把腐化内容加进创造栏独立分区（`RegisterTab` 新增 section）。
3. 世界机制闭环：infcore blockspread（`rotten_ruins:corruption_spread`）+ 刷怪（SpawnPlacements）+ 演化数据。
4. 方块模型替换（遗骸/经络用 corruption 原模型）+ 26→21 方块行为（fleshbed BE 等）。
5. 注射器/粗制工具/掉落/配方等玩法补齐。
6. 技能与投射物系统（按模块逐个恢复）。
