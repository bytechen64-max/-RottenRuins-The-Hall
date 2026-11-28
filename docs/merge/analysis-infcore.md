# infcore 前置 API 与 Hall+corruption 合并集成映射分析

> 任务：Team Lead 分析队友 `infcore-api` / 共享任务 `task-3`（只读分析，产出本报告）
> 日期：2026-10-28
> 分析对象：
> - infcore 源码：`C:\Users\chy20\Desktop\desktop\Project\InfectionCoreFramework`（`org.bytechen.infcore`，46 个 Java 文件，版本 0.1.2，Forge 47.4.21，MC 1.20.1）
> - Hall 工程：`C:\Users\chy20\Desktop\desktop\Project\RottenRuinsSplendiding`（modid=`hall`，已依赖 infcore，44 处 import）
> - corruption 工程：`C:\Users\chy20\Desktop\desktop\Project\corruption\corruption`（modid=`corruption`，未使用 infcore，268 个 Java 文件）
> - 旁证：`C:\Users\chy20\Desktop\desktop\Project\GodSickNeo`（另一个 infcore 0.0.2 消费方）
>
> 约束：只读分析，未修改任何源码，未运行 Gradle 构建；本文件为唯一产出。

---

## 0. 摘要（给 Lead 的 12 条核心结论）

| # | 结论 | 影响 |
|---|---|---|
| 1 | **infcore 0.1.2 的 API 是 0.0.2 的严格超集**（逐类核对 IKillCounter / IInfectedEntity / InfectedTargetGoal / EvolutionManager / WorldDataRegistry / NetworkHelper / CapabilityRegistry：签名无删除）。GodSickNeo 用到的接口在 0.1.2 里全部存在，可被 0.1.2 直接满足 | 合并后统一要求 `infcore >= 0.1.2` 是安全的；但 `mods.toml` 里现在 Hall 写的是 `versionRange="*"`，必须收紧成 `[0.1.2,)`，否则玩家装 0.0.2 会在运行时 `NoSuchMethodError` |
| 2 | **infcore 的"阵营"= `IInfectedEntity.getInfectionType()` 的 ResourceLocation，而"转化规则"= `EvolutionManager`/`BlockSpreadManager` 的 `type` 字符串——这是两套独立命名空间**。Hall 当前实际是：阵营 `hall:hall`、进化 type `hall:inf`、扩散 type `hall:spread`。任务描述里"感染类型 hall:inf"并不准确 | 合并后可以（也应该）让"阵营 type"与"数据规则 type"分开设计：阵营 1 个 `rotten_ruins:inf`（或 2 个），转化规则 type 至少 4 个（王庭感染/王庭进化/腐化同化/腐化进化） |
| 3 | **若王庭与腐化共用同一个进化 type，会互相污染**：`EvolutionManager.applyEvolution` 在某实体无规则时会走 **fallback 兜底档位**（Hall 注册了 scout/pursuer/monolith）。腐化击杀若也传同一个 type，就会被转成王庭的溃烂生物 | 合并后至少拆成 `rotten_ruins:hall_inf` / `rotten_ruins:corruption_inf` 两个 evolution type；block spread 同理必须拆 type，否则同一 `source` 在两张表里互相覆盖 |
| 4 | **block spread 同 `type` + 同 `source` 的条目会静默覆盖**（`BlockSpreadManager.parseAndMerge` 以 `type→source` 为 key，后者覆盖前者）。Hall 现有 43 条 `hall:spread`、1 个 type，暂时安全，但合并后王庭草地与腐化草地都想抢 `minecraft:grass_block`，**必须两个 type** | 一行 rule 的丢失不会报错，只会表现为"某类地形永远不腐化"，是隐蔽 bug |
| 5 | **Hall 现有 evolution JSON 里有重复 source 的 bug**：`data/hall/infcore_evolution/default.json` 中 `hall:monolith`（type=`hall:evo`）出现两条各 50 权重的规则（→bonecrusher / →heavy_bomb）。按解析逻辑第二条覆盖第一条，**实际只有 heavy_bomb 生效**，bonecrusher 永远不会从巨碑进化出来 | 合并时改为"一条 entry + results 里两个 target（各 50）"；同时纳入 datagen 校验清单 |
| 6 | `InfectedTargetGoal` 的默认语义：**非感染生物（含玩家）都可攻击；任何 `IInfectedEntity` 默认不攻击；`targetOtherInfected=true` 时"只打不同类型"**。它没有"多阵营外交表"的概念——想表达"王庭 vs 腐化的敌对关系"必须靠 `ITargetFilter` 或自研 Goal | 好消息：`targetOtherInfected` 这个开关本身就够表达"同阵营不互殴、异阵营互殴"，但"部分异阵营也和平"只能靠 filter |
| 7 | **Hall 的阵营判断不只在 infcore goal 里，还硬编码在 6 处 Java 代码**：`ForgeEventHelpers`、`BlackHolePhysicsHandler`、`CollapsarEntity` 用 `namespace==hall && path=="hall"`，`MeteoriteEntity` 用 `path=="hall"`，`WindingColumnEntity`/`HallDifficultyAttributes` 只用 namespace。另外 3 处 spread 判断硬编码 `"hall".equals(getSpreadType().getNamespace())`（`HallSpreadEventHandler` / `HallVineBlock` / `HallVineFeature`） | modid 改名后这些字符串**不会跟着变**，会导致友伤、黑洞免疫、藤蔓生长、陨石治疗全部失效——这是改名风险里最容易漏的一类 |
| 8 | **infcore 数据扫描与 modid 无关**：`EvolutionManager`/`BlockSpreadManager` 扫描"所有已加载 mod 文件"里 `data/<任意命名空间>/infcore_evolution|infcore_blockspread/*.json`。但 `type` 字符串必须与代码传给 `applyEvolution` / `getSpreadType()` 的 ResourceLocation 完全一致 | modid 改名后如果只改代码不重跑 `runData`，JSON 里仍是 `hall:inf`/`hall:spread`，而代码变成 `rotten_ruins:*` → **演化/扩散静默失效（不报错、不打日志）**。必须重跑 datagen 并删除旧 `data/hall` |
| 9 | **corruption 的 `BiomeCorruptionProcessor`/`CorruptionDiskStorage`/`util` 与 Hall 的 `HallBiomeProcessor`/`HallDiskStorage` 是同一份代码的两个世代**，且 infcore 的 `BiomeSpreadHelper`（`setColumnQuartBiome`/`syncChunk`）就是从这份代码抽出来的。corruption 版还有"静态 storage 单维度、PENDING_POS 全局混用"的旧 bug，Hall 版已按维度隔离 | 合并时**以 Hall 版为基线**，corruption 的队列/快照层直接废弃；群系写入/同步换 `BiomeSpreadHelper`——这一块是"可直接用"最确定的一块 |
| 10 | **corruption 与 Hall 各自实现了一套 capability/savedata 基础设施**，而且 corruption 的 `capability/base/*` 与 infcore 0.1.2 的 `core.capability/*` 代码同构（连 `syncData` 脏标记+`CapabilityProvider.initSync` 模式都一致）。infcore 0.1.2 只注册了 `infcore:kill_count` 一个 capability | 合并后删掉 corruption 的 `capability/base` 与 `network/s2c/PacketSyncCapability`，改用 infcore 版本；自定义能力（威慑/威胁）用 `CapabilityRegistry.register(new ResourceLocation("rotten_ruins","deterrence"), token)` 注册后即可复用 infcore 的同步链路 |
| 11 | **corruption 的 `WorldDataRegistry` 使用方式有潜在 Windows 存档风险**：infcore `WorldDataRegistry.get` 把 `id.toString()`（含冒号，如 `godsick_neo:stage`）直接当 `SavedData` 文件名交给 `DimensionDataStorage`。Windows 不允许文件名含 `:` | 合并后若采用 infcore WorldData 承载"腐化演化值/节点"，要么先在 infcore 修文件名（推荐 `namespace_path` 并在内部做旧名迁移），要么用无冒号的 `SavedData` name 自管。**这是本报告建议唯一必须改 infcore 的地方**（需实机验证后再定） |
| 12 | 集成前置条件总体可控：Forge 47.4.20（Hall）/47.4.18（corruption）/47.4.21（infcore）都在 `[47,)` 内，infcore 用预编译 jar（`libs/org/bytechen/infcore/0.1.2`），不存在 composite build 冲突；jarJar 目前只打 cloth-config+geckolib，**不建议 jarJar infcore**（会与 GodSickNeo 的外部 infcore 产生版本竞争） | 合并工程建议 Forge 升到 47.4.21（对齐 infcore），infcore 继续 external mandatory 依赖；`syncInfcore` 任务里的 `workingDir=file('InfectionCoreFramework')` 路径是错的（实际在 `../InfectionCoreFramework`），升级框架时会踩 |

**需要 Lead 决策的问题**：① 王庭与腐化合并后是"同一阵营"还是"两个敌对阵营"（直接影响 `IInfectedEntity` 单/双 type）；② 腐化的 8 段"演化阶段"(evolutionary) 与 Hall 的 4 档全局难度谁做唯一难度来源；③ 是否接受改 infcore 0.1.3（WorldData 文件名 + 少量 API 增强），还是冻结 0.1.2 只在业务侧适配。

---

## 1. infcore 总览

### 1.1 构建与版本矩阵

| 项 | 值 | 来源 |
|---|---|---|
| modid / 版本 | `infcore` / **0.1.2** | `InfectionCoreFramework/gradle.properties` L35/L41 |
| MC / Forge | 1.20.1 / **47.4.21**（range `[47,)`） | 同上 L4/L10/L12 |
| mappings / Java | official 1.20.1 / Java 17 | 同上 L29-L32；`build.gradle` L29 |
| 许可 / 作者 | MIT / ByteChen | L39/L47 |
| Mixin | **零 mixin**（`infcore.mixins.json` 的 mixins/client 数组为空） | `src/main/resources/infcore.mixins.json` |
| 发布物 | `jar`（reobf）、`dev`、`sources` 到 mavenLocal；Hall 用工程内镜像 `libs/org/bytechen/infcore/0.1.2/{jar,pom}` | `build.gradle` L206-L236 |
| 网络 | `SimpleChannel` 频道 `infcore:main`，protocol `1.0`，3 个包 id 0/1/2 | `core/network/NetworkHelper.java` L20-L39 |
| 代码规模 | 46 个 Java 文件（api 12 + core 34），无第三方依赖 | 文件盘点 |

> 版本历史：mavenLocal 里有 0.0.1 / 0.0.2 / 0.0.3 / 0.1.1 / 0.1.2。0.0.2 源码 jar 已核对：**不含** blockspread / difficulty / biome / command / util / InfectionFallback / PacketSyncDifficulty / IModWorldData 之外的 worlddata 体系；0.1.2 在此基础上纯增量。

### 1.2 入口 `core.Infcore` 与注册方式

| 注册点 | 代码 | 说明 |
|---|---|---|
| 主类 | `@Mod(Infcore.MODID) class Infcore` | `MODID="infcore"`、`LOGGER`、`VERBOSE`（`-Dinfcore.verbose=true`） |
| mod 总线 | `Infcore()` 构造器：`modEventBus.addListener(this::commonSetup)` | commonSetup 只打日志（无注册逻辑） |
| Forge 总线 | `MinecraftForge.EVENT_BUS.register(this)` | 服务器生命周期 + `/infcore difficulty` 命令注册（`onRegisterCommands` → `DifficultyCommand.register`） |
| 网络 | `NetworkHelper.register()`（构造器内） | 3 个 S2C 包 |
| 客户端 | `@Mod.EventBusSubscriber(modid=..., bus=MOD, value=CLIENT) ClientModEvents.onClientSetup` → `ClientPacketHandlers.init()` | 注入 3 个包的 CLIENT_HANDLER |
| 能力/事件 | `@Mod.EventBusSubscriber(modid=infcore, bus=FORGE) InfCoreEventHandler` | AttachCapabilities / LivingDeath / LivingTick |
| 数据生成 | `@Mod.EventBusSubscriber(bus=MOD) DataGenEvent.gatherData` | 注册 infcore 自己的两个 provider（输出空默认 JSON） |
| 依赖声明 | infcore 自己的 `mods.toml` 只声明 forge/minecraft mandatory；**不对下游暴露版本约束** | `src/main/resources/META-INF/mods.toml` |

### 1.3 三条 classpath 扫描线（无 datapack / 无 reload）

| 系统 | 数据路径 | 加载时机 | 重载 API |
|---|---|---|---|
| 进化 | `data/<任意ns>/infcore_evolution/*.json` | 首次调用 `EvolutionManager.*` 时惰性全量扫描（`ensureLoaded`，一次性） | `EvolutionManager.forceReload()`（程序调用；`/reload` 无效） |
| 方块扩散 | `data/<任意ns>/infcore_blockspread/*.json` | 首次调用 `BlockSpreadManager.*` 时惰性扫描 | `BlockSpreadManager.forceReload()` |
| 群系 | 无数据；`BiomeSpreadHelper` 是纯工具 | — | — |

扫描实现：`ModList.get().getMods()` → 逐个 `modInfo.getOwningFile().getFile().getFilePath()`，目录则找 `<path>/data/*/infcore_evolution`，jar 则用 `FileSystems.newFileSystem` 遍历。**只扫 mod 文件，不扫世界 datapack**；模组包作者只能用 `mergeOverrides` 或改 JSON 重新打包。

### 1.4 两套"数据抽象"（同步模型）

| 抽象 | 基类 | 标识 | 持久化 | 同步包 | 同步范围 |
|---|---|---|---|---|---|
| 实体能力 | `core.capability.AbstractCapability<T>` + `CapabilityProvider` | `CapabilityRegistry` 的 ResourceLocation（内置仅 `infcore:kill_count`） | Forge Capability（实体 NBT `ForgeCaps`） | `PacketSyncCapability` | **追踪该实体的客户端**（`TRACKING_ENTITY`） |
| 世界数据 | `core.worlddata.AbstractWorldData`（extends `SavedData`） | `WorldDataRegistry` 注册的 ResourceLocation；同时是 **SavedData 文件名** | `DimensionDataStorage`（按维度） | `PacketSyncWorldData` | **全服所有客户端**（`ALL`，包内带 dimension） |
| 全局难度 | `core.difficulty.GlobalDifficultyData`（SavedData） | 固定文件名 `infcore_global_difficulty` | overworld SavedData | `PacketSyncDifficulty` | 全服所有客户端 |
| 击杀计数 | `KillCountCapability`（能力实现） | `infcore:kill_count` | capability NBT `killCount`/`enabled` | 只同步 `killCount` | 追踪客户端 |

统一模式：改数据 → `markSyncDataDirty()`（写 sync tag + 触发网络）→ 若要磁盘持久化还要额外 `setDirty()`（worlddata）。**这是下游最容易漏的一步**。

---

## 2. infcore API 全量清单（按包）

> 签名只列 public / 关键 default；`…` 表示内部实现。完整文件路径前缀：`InfectionCoreFramework/src/main/java/org/bytechen/infcore/`。

### 2.1 `api`（顶层 3 个）

| 类/接口 | 用途 | 关键方法 |
|---|---|---|
| `IInfectedEntity` | 感染生物标记 + 感染类型（**阵营判断的唯一官方入口**） | `ResourceLocation getInfectionType()` |
| `IKillCounter` | 击杀计数接口，实现即自动挂 `KillCountCapability` | `default boolean killCounterEnabled()`；`int getKillCount()`；`void setKillCount(int)`；`void addKillCount(int)`；`default void onKilledEntity(Entity)`（默认 +1） |
| `IModWorldData` | 世界数据标记（继承 `INBTSerializable<CompoundTag>`；下游不直接实现，继承 `AbstractWorldData`） | — |

### 2.2 `api.block` / `api.difficulty`

| 类/接口 | 用途 | 关键方法/常量 |
|---|---|---|
| `ISpreadBlock`（`@FunctionalInterface`） | 可扩散方块标记；`BlockSpreadManager` 用它自动取 spread type | `ResourceLocation getSpreadType()` |
| `DifficultyType`（enum） | 内置难度 ID 常量（**无玩法逻辑**）：`PEACEFUL/EASY/NORMAL/HARD` | `ResourceLocation getId()`（`infcore:peaceful` 等）；`int getDefaultOrder()`（0..3） |

### 2.3 `api.event`（5 个）

| 事件 | 可取消 | 字段/方法 | 触发点 |
|---|---|---|---|
| `BlockSpreadEvent`（abstract） | 由子类定 | `getLevel/getPos/getOldState/getNewState/getSpreadType/isPre/isPost` | `BlockSpreadManager.doSpread` |
| `BlockSpreadEvent.Pre` | **是** | 同上 | 扩散执行前（可拦） |
| `BlockSpreadEvent.Post` | 否 | 同上 | `setBlock` 成功后 |
| `DifficultyChangeEvent`（abstract） | 由子类定 | `getOverworld/getOldDifficulty/getNewDifficulty/isPre/isPost` | `GlobalDifficultyData.setDifficulty` |
| `DifficultyChangeEvent.Pre` / `.Post` | 是 / 否 | 同上 | 切换前 / 落盘+同步后 |
| `EntityEvolveEvent` | 否 | `getLevel/getOriginal/getResultEntity`（**每个生成的目标实体各发一次**） | `EvolutionManager.replaceEntity` |
| `KillCountAddEvent` | **是** | `getEntity`（可为 null）/`getAmount`/`setAmount(int)`（钳 ≥0） | `KillCountCapability.addKillCount` |
| `KillCountChangeEvent` | 否 | `getEntity`（可为 null）/`getOldCount`/`getNewCount` | 计数变更后 |

### 2.4 `api.goal`（阵营判断核心，2 个）

**`ITargetFilter`**（`@FunctionalInterface`）：`boolean canTarget(LivingEntity owner, LivingEntity target)`。在默认筛选之后调用；owner 是索敌者（建议实现 `IInfectedEntity`），target 是候选。

**`InfectedTargetGoal extends Goal`**

| 成员 | 值/行为 |
|---|---|
| `static InfectedTargetGoal create(Mob)` | 默认配置快捷构造 |
| `Builder(Mob)` | `.range(double)`（默认 16，最小 1）`.mustSee(boolean)`（默认 true）`.targetOtherInfected(boolean)`（默认 **false**）`.filter(ITargetFilter)`（默认 null）`.build()` |
| 构造器内 | `ownerType = mob instanceof IInfectedEntity ? getInfectionType() : null`；`setFlags(TARGET)` |
| `canUse()` | 已有 target → false；扫描冷却 `SCAN_INTERVAL=10 tick`；`findTarget()` |
| `canContinueToUse()` | target 活着 + 距离 ≤ range²；`mustSee` 时失去视线最多再追 `MAX_UNSEEN_TICKS=60`（3s） |
| `findTarget()` | `getEntitiesOfClass(LivingEntity, AABB, passesPreliminaryCheck)` 取**最近**；`mustSee` 时最终视线检查 |
| `passesPreliminaryCheck`（顺序） | ①非自身 ②isAlive ③isAttackable ④`mob.canAttackType(target.getType())` ⑤玩家排除创造/旁观 ⑥`passesInfectionFilter` ⑦`passesCustomFilter` |
| `passesInfectionFilter`（核心） | 目标非 `IInfectedEntity` → **通过**（玩家/动物/村民）；owner 无 type → 通过；`targetOtherInfected=false` → **拒绝所有感染者**；`=true` → 类型不同才通过（同类仍拒绝） |
| `passesCustomFilter` | `filter == null || filter.canTarget(mob, target)` |

> 关键限制：① 该 Goal 没有"阵营关系表"；② `canUse` 在已有 target 时直接 false，意味着**它不能替换其他 target 来源**（Hall 用 `setTarget` 覆写补了威胁闸门）；③ 扫描半径与追踪半径是同一个 `range`（corruption 的 `trackingRangeMultiplier` 无对应物）。

### 2.5 `core.capability`（5 个）

| 类 | 用途 | 关键方法 |
|---|---|---|
| `IModCapability` | 能力标记（`INBTSerializable<CompoundTag>`） | — |
| `AbstractCapability<T>` | 能力基类：`syncData` tag + 脏标记 + LazyOptional holder + 同步回调 | `abstract void writeSyncData(CompoundTag)`；`abstract void readSyncData(CompoundTag)`；`void markSyncDataDirty()`；`LazyOptional<T> getHolder()`；`void invalidate()`；`boolean isSyncDirty()`；`void clearSyncDirty()` |
| `CapabilityProvider<T,C>` | 桥接 Forge `ICapabilitySerializable`，脏时自动发 `PacketSyncCapability` | `CapabilityProvider(Capability<T>, C)`；`void initSync(Entity)`（**必须调用，否则不同步**）；`getCapability/serializeNBT/deserializeNBT` |
| `CapabilityRegistry` | 能力 ID ↔ Forge Capability 双向表 | `static final Capability<KillCountCapability> KILL_COUNT`；`register(ResourceLocation, Capability<T>)`；`getId(Capability)`；`getById(ResourceLocation)` |
| `KillCountCapability` | 击杀计数实现（同时实现 `IKillCounter`） | `getKillCount/setKillCount(int)/addKillCount(int)`；重载 `addKillCount(LivingEntity, int)` / `setKillCount(LivingEntity, int)`（**带实体版才检查 `enabled`**）；`writeSyncData` 只写 `killCount`；NBT 另存 `enabled` |

### 2.6 `core.worlddata`（2 个）

| 类 | 用途 | 关键方法 |
|---|---|---|
| `AbstractWorldData extends SavedData implements IModWorldData` | 世界数据基类：磁盘持久化 + 同步 | `abstract writeSyncData/readSyncData`；`save(CompoundTag)`（委托 `serializeNBT`）；`markSyncDataDirty()`（→ 写 sync tag + `PacketSyncWorldData` 广播全服）；`isSyncDirty()`；`serializeNBT/deserializeNBT` 由子类实现 |
| `WorldDataRegistry` | 注册/取用/客户端缓存 | `register(ResourceLocation, Class<T>, Supplier<T>, Function<CompoundTag,T>)`；`get(ServerLevel, Class<T>)`；`get(ServerLevel, ResourceLocation)`；`getClientData(dimensionId, worldDataId)`；`getRegisteredIds()`；`putClientData(...)`（Internal） |

> 注意：`get` 内部 `computeIfAbsent(deserializer, constructor, id.toString())`——**注册 ID 同时是 SavedData 文件名**（见 §8.4 风险）。

### 2.7 `core.evolution`（5 个）

| 类 | 用途 | 关键方法/字段 |
|---|---|---|
| `EvolutionData` | 单 JSON 顶层 | `boolean replace()`；`List<EvolutionEntry> entries()` |
| `EvolutionEntry` | 一条 source→results 规则 | `type()/source()/keepEquipment()/keepNbt()/results()` |
| `EvolutionTarget` | 目标+权重 | `target()`；`int weight()` |
| `InfectionFallback`（record） | 感染兜底档位 | `target()`；`int count()`；`boolean matches(double volume)`（体积=width×height） |
| `EvolutionManager` | 引擎 | `applyEvolution(ServerLevel, LivingEntity, ResourceLocation type)`；`registerFallback(type, EntityType, count, DoublePredicate volumeCondition)`；`clearFallback/clearFallbacks/getFallbacks`；`hasEvolveRule(entity)` / `hasEvolveRule(EntityType, type)`；`getEvolutionTargets(EntityType, type)`；`getEntryCount()`；`forceReload()`；`mergeOverrides(type, List<String> "src -> target : weight")`；常量 `FALLBACK_MIN_MAX_HEALTH=5.0F`、`GSON` |

### 2.8 `core.blockspread`（5 个）

| 类 | 用途 | 关键方法/字段 |
|---|---|---|
| `BlockSpreadData` | 单 JSON 顶层 | `replace()`；`entries()` |
| `BlockSpreadEntry` | source→targets | `type()/source()/dropResources()/results()` |
| `BlockSpreadTarget` | 目标+权重 | `target()`；`int weight()` |
| `BlockSpreadManager` | 引擎 | `applySpread(Level, BlockPos, ResourceLocation type)`；`applySpread(Level, BlockPos, BlockState, type)`；`applySpread(Level, BlockPos, ISpreadBlock)`；`hasSpreadRule(BlockState, type)`；`hasSpreadRule(Block, type)`；`hasSpreadRule(BlockState)`（方块实现 `ISpreadBlock` 时自动取 type）；`getSpreadTargets(Block, type)`；`getEntryCount()`；`forceReload()`；`GSON` |
| `SpreadHelper` | 邻近扩散范式 | `trySpreadNearby(BlockState, ServerLevel, BlockPos, RandomSource, ISpreadBlock, int attempts, float chance)` → 成功次数；`canSpreadTo(LevelReader, BlockPos, BlockState, ISpreadBlock)` |

`applySpread` 内部流程：取当前 state → 查 `type→source` 规则 → 加权抽 target → `BlockSpreadEvent.Pre`（取消即中止）→ 可选 `Block.dropResources` → `setBlock(UPDATE_ALL_IMMEDIATE)` → `BlockSpreadEvent.Post`。

### 2.9 `core.biome` / `core.difficulty`

| 类 | 用途 | 关键方法 |
|---|---|---|
| `BiomeSpreadHelper` | 群系写入/同步工具 | `static boolean setColumnQuartBiome(ServerLevel, BlockPos, Holder<Biome>)`（返回是否有改动，内部改 `PalettedContainer` + `setUnsaved(true)`）；`static void syncChunk(ServerLevel, ChunkPos)`（发 `ClientboundLevelChunkWithLightPacket` 给追踪该区块的玩家） |
| `DifficultyRegistry` | 难度 ID 可发现性注册（只影响命令补全/列表） | `register(ResourceLocation)`；`isRegistered(id)`；`getRegisteredIds()`；静态注册 4 个内置 |
| `DifficultyHelper` | 全局难度唯一入口 | `getDifficulty(ServerLevel)`；`setDifficulty(ServerLevel, ResourceLocation)`（可被 Pre 取消；落盘+全服同步+Post）；`getClientDifficulty()`；`setClientDifficulty(...)`（Internal） |
| `GlobalDifficultyData extends SavedData` | 难度持久化（overworld，文件名 `infcore_global_difficulty`） | `getCurrentDifficulty()`；`setDifficulty(overworld, id)`；`load(CompoundTag)`（Internal） |

### 2.10 `core.network` + `core.client`（5 个）

| 类 | 用途 | 关键 API |
|---|---|---|
| `NetworkHelper` | 频道与发送 | `static final SimpleChannel NETWORK`（`infcore:main` / "1.0"）；`register()`；`sendToPlayer/sendToAllClients/sendToClient(Entity)/sendToServer` |
| `PacketSyncCapability` | S2C 能力同步 | `getEntityId/getCapabilityId/getSyncData`；`static BiConsumer CLIENT_HANDLER`；`encode/decode/handle` |
| `PacketSyncWorldData` | S2C 世界数据 | `getDimension/getWorldDataId/getData`；`CLIENT_HANDLER` |
| `PacketSyncDifficulty` | S2C 难度 | `getDifficulty()`；`CLIENT_HANDLER` |
| `ClientPacketHandlers`（`@OnlyIn(CLIENT)`） | 客户端分发 | `init()`；cap → 找到实体 → `AbstractCapability.readSyncData`；worlddata → `WorldDataRegistry.putClientData`；difficulty → `DifficultyHelper.setClientDifficulty` |

### 2.11 `core.command` / `core.datagen` / `core.util`（5 个）

| 类 | 用途 | 关键 API |
|---|---|---|
| `DifficultyCommand` | `/infcore difficulty set <id> / get / list`（op 2） | `register(CommandDispatcher)` |
| `EvolutionDataProvider`（实现 `DataProvider`） | 进化 JSON datagen | 构造 `(PackOutput, String modId, String fileName)` / `(PackOutput)`；`String type(String)`→`modId:name`；`entry(localType, source/EntityType, keepEquipment, keepNbt, List<EvolutionTarget>)`；`static entryFull(...)`；`static EvolutionTarget target(String/EntityType, int weight)`；`static String regName(EntityType)`；输出 `data/<modId>/infcore_evolution/<fileName>.json`，`replace=false` |
| `BlockSpreadDataProvider`（实现 `DataProvider`） | 扩散 JSON datagen | 同上模式；`entry(localType, source/Block, dropResources, List<BlockSpreadTarget>)`；`static BlockSpreadTarget target(...)`；输出 `data/<modId>/infcore_blockspread/<fileName>.json` |
| `DataGenEvent` | infcore 自身 datagen 钩子 | 注册两个默认 provider（infcore 命名空间） |
| `ThrottledLogger` | 高频日志节流 | `record(format, args…)`；`flush()`；`static flushAll()`；`-Dinfcore.verbose` / `-Dinfcore.log.interval` |

### 2.12 `core.event`（2 个）

| 类 | 用途 |
|---|---|
| `InfCoreEventHandler`（FORGE bus） | `AttachCapabilitiesEvent<Entity>` → helpers；`LivingDeathEvent`（仅服务端）→ helpers；`LivingEvent.LivingTickEvent` → helpers |
| `InfCoreEventHelpers` | `handleAttachCapabilities`：`entity instanceof IKillCounter` → new `KillCountCapability(killCounterEnabled())` + `CapabilityProvider` + `initSync` + `addCapability(infcore:kill_count)` + `addListener(invalidate)`；`handleLivingDeath`：killer `instanceof IKillCounter` → `onKilledEntity(victim)`，否则 killer 有 cap → `addKillCount(killer, 1)`；`handleLivingTick`：目前空预留 |

---

## 3. 数据格式（schema + 语义）

### 3.1 `infcore_evolution/*.json`

```json
{
  "replace": false,
  "entries": [
    {
      "type": "hall:inf",
      "source": "minecraft:skeleton",
      "keepEquipment": false,
      "keepNbt": false,
      "results": [ { "target": "hall:inf_skeleton", "weight": 100 } ]
    }
  ]
}
```

| 字段 | 类型 | 必填 | 含义 | 底层行为 |
|---|---|---|---|---|
| `replace` | bool | 否(false) | `true` = 写入前先删除同 `(type,source)` 旧条目 | `parseAndMerge` 中 `typeMap.remove(srcKey)` |
| `entries[].type` | ResourceLocation string | **是** | 进化类型；缺失该条被跳过 | `ResourceLocation.tryParse`，非空 |
| `entries[].source` | ResourceLocation string | **是** | 源实体注册名 | `ForgeRegistries.ENTITY_TYPES` 查询 key |
| `entries[].keepEquipment` | bool | 否 | 进化后是否复制装备（**只给第一个生成体**） | `copyEquipment` |
| `entries[].keepNbt` | bool | 否 | 是否复制完整 `saveWithoutId` NBT（**只给第一个生成体**） | `copyFullNbt`（否则 `copyBasicNbt`：persistence/leftHanded/invulnerableTime） |
| `entries[].results` | list | **是** | 进化目标列表 | 空 → 视为无规则 → 走 fallback |
| `results[].target` | ResourceLocation string | 是 | 目标实体注册名 | 未知类型：warn 并走 fallback |
| `results[].weight` | int | 是 | 权重（累加后 `random.nextInt(total)`；≤0 总权重则失败） | `pickWeighted` |

**合并/覆盖语义（必须知道）**：
1. 内存结构 `Map<type, Map<source, EvolutionEntry>>`；**同 `type` + 同 `source` 后写覆盖先写**。
2. 扫描顺序 = `ModList` 模组顺序 + 每个目录 `Files.list` 顺序（**文件系统序，不保证稳定**）；同一文件内按 entries 数组顺序。
3. 因此"想给同一 source 多个目标"必须写在**同一条 entry 的 results 里**；分成两条 entry 会丢数据（Hall 现有 JSON 即踩中，见 §3.3）。
4. 没有 `conditions` / 概率 / 权重之外的任何条件字段；条件、概率、count 都只能由调用方在 `applyEvolution` 之前判断，或走 `InfectionFallback`。

**执行语义**：`applyEvolution(level, entity, type)` 服务端调用 → 查规则 → 加权目标 → `replaceEntity`（复制位置/朝向/速度/自定义名；`count>1` 时环状散开并做碰撞回退；生成体逐个发 `EntityEvolveEvent`；源实体 `discard()`——**但源实体是 Player 时不会 discard**）→ 返回是否成功。无规则 → `doFallbackTransform`：该 type 的注册档位按注册顺序取第一个 `width*height` 满足条件者 → 替换为 `count` 个目标；已是兜底形态本身不再转化；`maxHealth < 5.0` 不转化。

### 3.2 `infcore_blockspread/*.json`

```json
{
  "replace": false,
  "entries": [
    {
      "type": "hall:spread",
      "source": "minecraft:grass_block",
      "dropResources": false,
      "results": [ { "target": "hall:hall_grass_block", "weight": 100 } ]
    }
  ]
}
```

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `replace` | bool | 否 | 同 `(type,source)` 先删再写 |
| `entries[].type` | ResourceLocation string | **是** | 扩散类型（与 `ISpreadBlock.getSpreadType()` / `BlockSpreadManager.applySpread(type)` 对应） |
| `entries[].source` | ResourceLocation string | **是** | 源方块注册名（**不支持 tag**，必须展开成具体方块） |
| `entries[].dropResources` | bool | 否 | 转换时是否 `Block.dropResources(oldState)` |
| `entries[].results[].target` | ResourceLocation string | 是 | 目标方块注册名（取 `defaultBlockState()`，**不支持目标状态/属性**） |
| `entries[].results[].weight` | int | 是 | 加权随机 |

限制：无 tag source、无 target state、无环境谓词、无每规则回调、无源方块"反向/净化"；这些都得在调用方（`randomTick`/`canSurvive`/事件监听）自己做。**同 type+source 覆盖规则与 evolution 相同**。

### 3.3 Hall 现有数据实例分析

**`src/generated/resources/data/hall/infcore_evolution/default.json`（4 条）**

| type | source | results | 备注 |
|---|---|---|---|
| `hall:inf` | `minecraft:player` | `hall:inf_player` w100 | 玩家感染 |
| `hall:inf` | `minecraft:skeleton` | `hall:inf_skeleton` w100 | 骷髅感染 |
| `hall:evo` | `hall:monolith` | `hall:bonecrusher` w50 | ⚠️ 被下一条覆盖 |
| `hall:evo` | `hall:monolith` | `hall:heavy_bomb` w50 | **实际生效**（bonecrusher 永不出现） |

对应 datagen = `src/main/java/org/bytechen/hall/datagen/gen/EvolutionDataProvider.java`（`super(packOutput, HallMod.MODID, "default")`；`entry("inf"…)` / `entry("evo"…)`）。**修复方式**：把两条 monolith 合成一条 `entry("evo", MONOLITH, false,false, List.of(target(BONECRUSHER,50), target(HEAVY_BOMB,50)))`。

> `hall:evo` 的**唯一运行时触发点**是 `MonolithEntity.tick()`（每 2 分钟对自身 `applyEvolution(..., "hall:evo")`，L56-62），且该 type 是就地 `new ResourceLocation(HallMod.MODID,"evo")` 拼的；合并/改名时容易漏改这一类"没有常量"的 type（详见 §7.2）。

**`data/hall/infcore_blockspread/default.json`（43 条，1 个 type `hall:spread`）**

| 源（vanilla） | 目标（hall） |
|---|---|
| grass_block | hall_grass_block |
| dirt | hall_dirt |
| 8 种 log（oak/spruce/birch/jungle/acacia/dark_oak/mangrove/cherry） | hall_log |
| 10 种 leaves（含 azalea/flowering_azalea） | hall_leaves |
| 12 种花（dandelion/poppy/blue_orchid/allium/azure_bluet/3 tulip/oxeye_daisy/cornflower/lily_of_the_valley） | hall_flower |
| grass/tall_grass | hall_grass |
| 8 种 planks | hall_planks |
| stone | hall_stone |

全部 `dropResources=false`、`weight=100`。datagen = `datagen/gen/BlockSpreadDataProvider.java`（数组驱动 + `addMapping`）。

### 3.4 datagen 使用方式与合并后的数据校验清单

现有写法（Hall 已正确使用 infcore provider 的实例方法，`type` 自动加 modid 前缀、输出目录自动用 modid namespace）：

```java
public class EvolutionDataProvider extends org.bytechen.infcore.core.datagen.EvolutionDataProvider {
    public EvolutionDataProvider(PackOutput out) { super(out, HallMod.MODID, "default"); }
    @Override public List<EvolutionEntry> buildEntries() { return List.of(
        entry("inf", EntityType.PLAYER, false, false, List.of(target(InfEntityTypes.INF_PLAYER.get(), 100)))); }
```

合并后跑 `runData` 前建议固化的检查：
- [ ] 同 `(type,source)` 不得出现多条 entry（脚本/simple grep 校验）。
- [ ] `type` 与代码常量一致（尤其改名后）。
- [ ] 王庭/腐化的 blockspread **type 不同**（见 §5.3）。
- [ ] 旧 `data/hall/**` 删除，避免旧 `hall:*` 规则随 jar 一起被打包（扫描器会读它们，虽然 type 不匹配时惰性无害，但会污染 `getEntryCount` 与排查）。
- [ ] 目标实体/方块的注册名确实存在于合并后的 `rotten_ruins` 命名空间。

---

## 4. Hall 现有 infcore 用法盘点

### 4.1 44 处 import 逐文件归类（30 个文件）

统计口径：`grep '^import org\.bytechen\.infcore' src/main/java` = **44 行**。路径前缀 `src/main/java/org/bytechen/hall/`。

| # | 文件 | import 的 infcore 类型 | 用途 |
|---|---|---|---|
| 1 | `command/SpreadCommand.java` | `core.blockspread.BlockSpreadManager` | `/spread` 指令：半径内对 `hall:spread` 规则方块批量 `hasSpreadRule`+`applySpread` |
| 2 | `datagen/gen/EvolutionDataProvider.java` | `core.evolution.EvolutionEntry` | 继承 infcore provider 覆写 `buildEntries` |
| 3 | `datagen/gen/BlockSpreadDataProvider.java` | `core.blockspread.BlockSpreadEntry` | 继承 infcore provider，数组→entries |
| 4 | `overworld/swarm/HallSpreadEventHandler.java` | `api.block.ISpreadBlock`、`api.event.BlockSpreadEvent`、`core.blockspread.BlockSpreadManager` | 监听 `BlockSpreadEvent.Post`：群系扩散入队、藤蔓/草生成、树叶 BFS 连锁（`applySpread`）；`tryGrowVines` 用 spreadType namespace 过滤 |
| 5 | `overworld/swarm/HallBiomeProcessor.java` | `core.biome.BiomeSpreadHelper` | 异步群系队列 → `setColumnQuartBiome` + `syncChunk` |
| 6 | `event/ForgeEventHelpers.java` | `api.IInfectedEntity` | `isHallInfected` 判定 → 王庭生物间无友伤（`LivingHurtEvent` 取消） |
| 7 | `event/impl/BlackHolePhysicsHandler.java` | `api.IInfectedEntity` | 黑洞引力/伤害排除 `hall:hall` 感染生物 |
| 8 | `overworld/manager/BlockSpreadManager.java` | `api.block.ISpreadBlock` | Hall 门面类（全限定名调用 infcore 管理器），`DEFAULT_TYPE=hall:spread` |
| 9 | `overworld/difficulty/HallDifficultyEventHandler.java` | `api.event.DifficultyChangeEvent` | `Post` 事件：刷新难度系数、全服播报、重算已加载生物 |
| 10 | `overworld/difficulty/HallDifficultyAttributes.java` | `api.IInfectedEntity`、`core.difficulty.DifficultyHelper` | `isScaledCreature`（namespace==hall）；启动时从 `DifficultyHelper` 读回存档难度 |
| 11 | `overworld/difficulty/HallDifficulty.java` | `core.difficulty.DifficultyRegistry` | `registerAll()` 注册 `hall:easy/normal/hard/incomprehensible` |
| 12 | `overworld/difficulty/DifficultySelectHandler.java` | `core.difficulty.DifficultyHelper` | 首次进服弹难度选择 → `setDifficulty` |
| 13 | `overworld/difficulty/DifficultyScaleProfile.java` | `api.difficulty.DifficultyType` | 未知难度 ID（含 `infcore:*`）映射到 4 档 Tier |
| 14 | `overworld/registry/blocks/impl/SpreadPillarBlock.java` | `api.block.ISpreadBlock`、`core.blockspread.SpreadHelper` | 可扩散柱状方块（原木），`randomTick` → `trySpreadNearby` |
| 15 | `overworld/registry/blocks/impl/HallVineBlock.java` | `api.block.ISpreadBlock` | `canSurvive` 要求支撑方块 spreadType namespace 为 `hall` |
| 16 | `overworld/registry/blocks/base/AbstractSpreadBlock.java` | `api.block.ISpreadBlock`、`core.blockspread.BlockSpreadManager`、`core.blockspread.SpreadHelper` | Hall 全部王庭方块基类：`getSpreadType()=hall:spread`、`randomTick` 扩散 + 退化 |
| 17 | `overworld/registry/feature/HallVineFeature.java` | `api.block.ISpreadBlock` | 地物放置藤蔓时用 spreadType namespace 判断可附着方块 |
| 18 | `overworld/registry/entities/population/ulcerated/UlceratedConversionRules.java` | `core.evolution.EvolutionManager` | `registerFallback(hall:inf, scout/pursuer/monolith, 体积档)` |
| 19 | `overworld/registry/entities/population/ulcerated/ScoutEntity.java` | `api.event.EntityEvolveEvent`、`api.goal.InfectedTargetGoal` | 索敌（见 4.3）；**手写 post `EntityEvolveEvent`**：5 只斥候合体成巨碑后手动广播给下游统计（L440-443） |
| 20 | `overworld/registry/entities/population/ulcerated/PursuerEntity.java` | `api.goal.InfectedTargetGoal` | 索敌 |
| 21 | `overworld/registry/entities/population/ulcerated/MonolithEntity.java` | `api.goal.InfectedTargetGoal`、`core.evolution.EvolutionManager` | 索敌 + **每 2 分钟（20×120 tick）对自身调用 `applyEvolution(level, this, hall:evo)` 自我进化**（L56-62；注意这里 type 是就地 `new ResourceLocation(HallMod.MODID,"evo")` 拼的，没有统一常量） |
| 22 | `overworld/registry/entities/population/ecological/HeavyBombEntity.java` | `api.goal.InfectedTargetGoal` | 索敌 |
| 23 | `overworld/registry/entities/population/ecological/BonecrusherEntity.java` | `api.goal.InfectedTargetGoal` | 索敌 |
| 24 | `overworld/registry/entities/population/defensive/WindingColumnEntity.java` | `api.IInfectedEntity` | `isHallKin` 自定义"同族"判定 |
| 25 | `overworld/registry/entities/base/AbstractHallEntity.java` | `api.IInfectedEntity`、`api.IKillCounter`、`api.goal.ITargetFilter`、`core.capability.CapabilityRegistry`、`core.capability.KillCountCapability`、`core.evolution.EvolutionManager` | 阵营/击杀计数/索敌 filter/击杀→感染（详见 4.2） |
| 26 | `overworld/registry/entities/population/apostle/CollapsarEntity.java` | `api.IInfectedEntity`、`api.goal.InfectedTargetGoal` | 索敌 + 友军判定 |
| 27 | `overworld/registry/entities/population/infected/InfSkeletonEntity.java` | `api.goal.InfectedTargetGoal` | 索敌 |
| 28 | `overworld/registry/entities/population/infected/InfPlayerEntity.java` | `api.goal.InfectedTargetGoal` | 索敌 |
| 29 | `overworld/registry/entities/population/infected/InfEndermanEntity.java` | `api.goal.InfectedTargetGoal` | 索敌（**唯一注册在 `targetSelector` 的**） |
| 30 | `overworld/registry/entities/population/skills/MeteoriteEntity.java` | `api.IInfectedEntity` | AoE 对 `path=="hall"` 的生物治疗、其它伤害 |

按类别计数：blockspread 13 行 / 30 文件中的 8 个；biome 1；evolution 4；difficulty 5；goal(`InfectedTargetGoal`) 9；`IInfectedEntity` 7；`IKillCounter`+`ITargetFilter`+`CapabilityRegistry`+`KillCountCapability` 4；`EntityEvolveEvent` 1 = **44**。

### 4.2 `AbstractHallEntity` 如何实现 infcore 接口

类声明（`overworld/registry/entities/base/AbstractHallEntity.java` L47）：
```java
public abstract class AbstractHallEntity extends Monster
        implements IAutoRenderableEntity, IKeyframeHandler, IInfectedEntity, IKillCounter, ITargetFilter
```

| 接口 | 实现位置 | 行为 |
|---|---|---|
| `IInfectedEntity` | L252-254 | `getInfectionType()` 返回 **`hall:hall`**（`new ResourceLocation(HallMod.MODID,"hall")`；注意不是 `hall:inf`） |
| `IKillCounter` | L312-329 | 全部委托 infcore 能力：`getCapability(CapabilityRegistry.KILL_COUNT).map(KillCountCapability::getKillCount).orElse(0)`；set/add 同理（`addKillCount(int)` 走 `kc.addKillCount(amount)`） |
| `IKillCounter.onKilledEntity` | L269-288 | ①`IKillCounter.super.onKilledEntity(killed)` 计数 +1；②若被杀者是 `LivingEntity` 且在服务端：快照碰撞箱/中心，`EvolutionManager.applyEvolution(serverLevel, victim, HallMod.INFECTION_TYPE)`（`hall:inf`）；③成功才喷感染血花 |
| `ITargetFilter.canTarget` | L151-156 | 玩家：创造/旁观→false，否则威胁点 ≥ `ThreatHelper.TARGET_THREAT_THRESHOLD(5)`→true；非玩家生物→**一律 true**（照常索敌） |
| `setTarget` 覆写 | L166-177 | 第二道闸：目标是玩家且（创造/旁观 或 非还手目标且威胁不足）→ 清 `lastHurtByMob` 并 `super.setTarget(null)`，**防止其它 goal/指令绕过 canTarget** |
| 范围攻击同阵营判定 | L225-231 | `canHitWithRangeAttack`：玩家走威胁闸门；`IInfectedEntity` → **false（不误伤）**；其它生物 true |

`HallMod`（L35）：`INFECTION_TYPE = new ResourceLocation(MODID,"inf")` = `hall:inf`，用于进化表 type 与兜底注册；`commonSetup` 中 `HallDifficulty.registerAll()` 与 `UlceratedConversionRules.register()`（L84-89）。

> **重要澄清**：Hall 的"阵营 type"是 `hall:hall`（`AbstractHallEntity.getInfectionType`），"进化 type"是 `hall:inf`（`HallMod.INFECTION_TYPE`），"扩散 type"是 `hall:spread`，难度 ID 是 `hall:easy|normal|hard|incomprehensible`。四个命名空间都来自 `HallMod.MODID`，但**字符串后缀不同**，改名时要逐一确认代码与数据一致。

### 4.3 `InfectedTargetGoal` 使用清单（9 个实体）

| 实体（population/…） | 注册位置 | range | mustSee | filter | targetOtherInfected |
|---|---|---|---|---|---|
| `ulcerated/ScoutEntity` | goalSelector 2 | 40 | false | `this`（威胁闸门） | 默认 false |
| `ulcerated/PursuerEntity` | goalSelector 2 | 40 | false | 无 | 默认 false |
| `ulcerated/MonolithEntity` | goalSelector 2 | 40 | false | `this` | 默认 false |
| `ecological/BonecrusherEntity` | goalSelector 2 | 40 | 默认 true | `this` | 默认 false |
| `ecological/HeavyBombEntity` | goalSelector 2 | 40 | false | `this` | 默认 false |
| `infected/InfSkeletonEntity` | goalSelector 2 | 40 | false | `this` | 默认 false |
| `infected/InfPlayerEntity` | goalSelector 2 | 40 | false | `this` | 默认 false |
| `infected/InfEndermanEntity` | **targetSelector 1** | 40 | false | `this` | 默认 false |
| `apostle/CollapsarEntity` | goalSelector 2 | `FOLLOW_RANGE=64` | false | `this` | 默认 false |

观察：
1. **没有任何实体使用 `targetOtherInfected(true)`**——Hall 目前完全依赖"任何 `IInfectedEntity` 都是友军"这一默认；跨类型敌对能力从未启用。
2. 8/9 把 `Flag.TARGET` Goal 挂在 `goalSelector`，只有 `InfEndermanEntity` 挂 `targetSelector`（代码注释自己写了"目标选择器必须添加到 targetSelector"）。两种都能跑，但混用会让"谁能覆盖 target"难以推理；合并时建议统一到 `targetSelector`。
3. `PursuerEntity` 未加 `filter`（即不受威胁点数约束），与其它王庭生物行为不一致（可能是刻意，但值得在合并规范里明确）。
4. 所有实体都通过继承 `AbstractHallEntity` 自动获得 `IInfectedEntity/IKillCounter/ITargetFilter` 三件套；`hall` 没有第二个类实现 `IInfectedEntity`。

### 4.4 Hall 自研基础设施 vs infcore（重叠/待收编）

| Hall 自研 | infcore 对应 | 状态 |
|---|---|---|
| `overworld/registry/capability/base/AbstractCapability`（极简：只有 serialize/deserialize NBT） | `core.capability.AbstractCapability`（同步脏标记+发包） | **不兼容的两套**；Hall 的异常/魔法/威胁能力都基于自研简化版，未用 infcore 的同步框架 |
| `overworld/registry/CapabilityRegistry`（自己的 Forge token 表） | `core.capability.CapabilityRegistry` | Hall 只把 kill_count 交给 infcore；其它能力自管 |
| `overworld/difficulty/HallWorldData`（SavedData：是否选过难度） | `AbstractWorldData` + `WorldDataRegistry` | Hall 未使用 infcore WorldData |
| `overworld/swarm/HallSpreadSaveData` + `HallBiomeProcessor` + `HallDiskStorage` | `BiomeSpreadHelper`（只覆盖写群系+同步） + `AbstractWorldData`（可选） | 只有底层群系写入用了 infcore；队列/快照/回退自研 |
| `overworld/registry/capability/threat/ThreatCapability`（所有实体挂"威胁点数"） | 无 | 自研；与 corruption 的 `Deterrence` 语义重复（见 §6/§7） |

---

## 5. "阵营判断"专题（合并方案核心）

### 5.1 infcore 的阵营模型（事实）

```
阵营 = IInfectedEntity.getInfectionType() : ResourceLocation
       └─ 只有"类型标识"这一层，没有阵营关系表 / 声望 / 外交

索敌判定链（InfectedTargetGoal）：
  候选实体
   → 非自身/存活/可攻击/canAttackType
   → 玩家排除创造、旁观
   → [感染过滤] 非 IInfectedEntity → 通过
                 ownerType==null → 通过
                 targetOtherInfected==false → 所有 IInfectedEntity 都拒绝
                 targetOtherInfected==true  → 仅类型不同者通过（同类仍拒绝）
   → [自定义过滤] ITargetFilter.canTarget(owner, target)
```

由此可推出（也是最大的坑）：
- **`targetOtherInfected` 是二值语义**：它只能表达"所有感染者都是友军"或"所有感染者都是敌人（同类除外）"，**不能表达"王庭与腐化敌对、但都不打自己人"这种带关系表的设定**。要做关系表只能：① `ITargetFilter` 里按 `(ownerType,targetType)` 白/黑名单；② 拆多个 `IInfectedEntity` type + 全局外交表；③ 自研 Goal。
- **玩家天然是"非感染"**：玩家本身不实现 `IInfectedEntity`，因此默认永远是猎物；Hall 用 `ITargetFilter` 实现"威胁点数不足不主动开战"，corruption 用 `Deterrence` 做同类事。合并后二者必须统一，否则同一玩家可能"对王庭安全、对腐化被追"。
- **`IInfectedEntity` 与 evolution type 无绑定关系**：`hall:hall` ≠ `hall:inf`。infcore 不会拿 `getInfectionType()` 去查进化表；`applyEvolution` 的 type 完全由调用方决定。**所以"阵营类型"和"转化规则类型"可以自由设计成不同粒度。**

### 5.2 Hall / corruption / GodSickNeo 三套阵营实现对比

| 维度 | Hall（infcore 官方路线） | corruption（自研） | GodSickNeo（自研，infcore 只用于数据） |
|---|---|---|---|
| 阵营标识 | `IInfectedEntity` → `hall:hall` | 空接口 `ICorruptionEntity` 标记 + `AbstractCorruptionEntity` 基类 + `CorruptionEntityType` 枚举（HOST/REFACTORED/DEGENERATE/DETERIORATED/ORIGINAL/LINKED，作为"kind/等级"） | `IGSParasites.getGSType()` 枚举 + `IGSVariantedEntity` |
| 索敌 | infcore `InfectedTargetGoal` + `ITargetFilter`（威胁点数） | 自研 `SmartTargetGoal extends TargetGoal` | 自研 `GSFoundTargetGoal` |
| 同阵营不打 | 默认排除所有 `IInfectedEntity`；范围攻击手动排除 | `attackSameKind=false` 默认不打同类；`attackOtherBaseMobs=false` 时连异类也不打 → 默认所有腐化生物互不打 | `attackSameKind=false` 默认不打同 GSType |
| 跨阵营 | 未启用（无 `targetOtherInfected`），且异 mod 感染者因未实现接口会被当猎物 | 打一切非腐化（含 hall/玩家/其它 mod） | 打一切非同类寄生虫 |
| 玩家闸门 | 威胁点数 ≥5 才主动索敌，被打会还手（100 tick 窗口） | `Deterrence` 威慑值累计（击杀腐化生物 +kind 权重） | 无（只看创造/旁观） |
| 跨模组表现 | 与 corruption 互杀（各视为猎物）；GodSickNeo 未实现 `IInfectedEntity`，也被 Hall 当猎物 | 与 Hall/GodSickNeo 互杀 | 与两者互杀 |
| 数据源 | `data/hall/infcore_evolution` + `infcore_blockspread` | 配置文件 + 内存规则表（`AssimilationConfig`/`NodeConfig`/`SpreadRuleManager`） | `data/godsick_neo/infcore_evolution`；`WorldDataRegistry`（`godsick_neo:stage`） |

### 5.3 合并后 `rotten_ruins:inf` 阵营设计建议

**建议 A（推荐，改动最小、语义最稳）**：

| 概念 | 建议值 | 理由 |
|---|---|---|
| 阵营 type（`IInfectedEntity`） | **单一 `rotten_ruins:inf`**（骨王庭+腐化同阵营） | 若策划设定"王庭与腐化是同一股腐化力量的不同表现"，单 type 让所有感染者天然不互殴；`InfectedTargetGoal` 默认行为即正确，无需 filter |
| 进化/转化 type | `rotten_ruins:hall_inf`（王庭击杀感染）、`rotten_ruins:hall_evo`（巨碑进化）、`rotten_ruins:corruption_inf`（腐化同化）、`rotten_ruins:corruption_evo`（承载者进化） | **避免 fallback 互相污染**（§0#3）；`EvolutionManager` 的 type 本来就是"规则命名空间"，与阵营无关 |
| 方块扩散 type | `rotten_ruins:hall_spread` / `rotten_ruins:corruption_spread` | **必须拆**：两者都会抢 `minecraft:grass_block`/`dirt`/`log` 等 source，同 type 会互相覆盖（§0#4）；`HallSpreadEventHandler` 的群系/藤蔓逻辑也靠 type 分流 |
| 难度 ID | 沿用 `rotten_ruins:easy/normal/hard/incomprehensible` 4 档（Hall 现有） | 少改；腐化的 8 段演化不作为全局难度，见 §6 difficulty 行 |
| 玩家 | 玩家 = **非感染生物（猎物）**，用统一 filter 做"威胁/威慑闸门" | 沿用 infcore 语义；不要在 Player 上实现 `IInfectedEntity`（会自打自） |

**建议 B（若策划要求王庭 vs 腐化对立）**：阵营拆 `rotten_ruins:hall` 与 `rotten_ruins:corruption`，并给**双方** `targetOtherInfected(true)`；这样"同阵营不互殴 + 跨阵营互殴"直接由 infcore 表达。若要"王庭与腐化敌对、但都不袭击某第三方感染 mod"，再加 `ITargetFilter` 排除外部 namespace。代价：`IInfectedEntity` 双 type 后，Hall 现有的 6 处"namespace==hall"硬编码要重写为"属于本 mod 的感染 type 集合"；`HallDifficultyAttributes` 的"按 namespace==hall 缩放"要改成按 type 判断。

**corruption 旧 `kind`/`attackSameKind` 映射（两种做法，推荐 1）**：
1. **保留 `kind` 作为实体字段（数据语义：等级/谱系），阵营统一到 `rotten_ruins:inf`**；`SmartTargetGoal` 的 `attackSameKind/attackOtherBaseMobs` 语义用 `ITargetFilter` 实现：
   ```java
   filter = (owner, target) -> {
       if (!(target instanceof AbstractCorruptionEntity other)) return true;
       // 默认所有感染体都不互打（infcore 已过滤掉 IInfectedEntity，此处只兜底非 IInfectedEntity 的旧实体）
       ...
   };
   ```
   注意 infcore 的感染过滤**先于**自定义 filter：如果所有腐化实体都实现 `IInfectedEntity` 且 `targetOtherInfected=false`，则腐化实体根本不会进入 `canTarget`，"同类/异类"细分无需实现——只有需要"腐化内部互殴"的少数怪才开 `targetOtherInfected(true)` 并用 filter 判 kind。
2. 若必须保留"按 kind 区别对待"的原语义：把 kind 编码进 type（如 `rotten_ruins:corruption_host`/`_refactored`…）+ 关系表 filter。**不推荐**：type 会从"阵营/规则"膨胀成"等级"，且 `IInfectedEntity` 的 type 语义被污染。

> 无论哪种，**不要**继续用 `kind` 做"是否攻击"的唯一依据——它是实体数据字段，不是阵营标识；合并后应统一由 `IInfectedEntity` + filter 决定敌我。

**与 GodSickNeo 的隔离**：
- GodSickNeo **没有实现 `IInfectedEntity`**（全项目 grep 无结果），因此对 merged mod 而言它永远是"非感染目标" → 默认会被攻击；若需要"井水不犯河水"，只能加 filter（例如排除 `godsick_neo` 命名空间实体，或读 corruption 的 `TargetWhitelistConfig` 同款 modid 白名单）。
- 反向隔离不需要做：GodSickNeo 用自研 `GSFoundTargetGoal`，不认识 `IInfectedEntity`，所以它会照常攻击 merged mod 的感染者。**这是跨模组联动的不对称点，必须在策划/兼容层明确**（要么加双向兼容代码，要么发布说明写清"与 GodSickNeo 互杀"）。
- 数据层完全隔离：`godsick_neo:inf|evolve|seize` 与 `rotten_ruins:*` 命名空间不同，扫描器各读各的，`replace` 也不会互删。

### 5.4 阵营判断的坑清单（合并验收逐项确认）

| 坑 | 说明 | 对策 |
|---|---|---|
| `targetOtherInfected` 二值 | 无法表达"多阵营外交表" | 单阵营 or 双阵营+filter；不要指望开关 |
| 目标 Goal 放错 selector | Hall 现在 8 个挂 goalSelector、1 个挂 targetSelector；`Goal.Flag.TARGET` 与其它 target 类 goal 会互斥 | 统一 `targetSelector`；避免与 `HurtByTargetGoal`/自研还手逻辑冲突 |
| `setTarget` 覆写绕过 | 任何代码（其它 goal、指令、`getLastHurtByMob` 路径）都能设置 target，绕过 `canTarget` | 像 Hall 一样在 `setTarget` 里补闸门（合并后把威胁/威慑统一到一个 helper） |
| `canAttackType` 先行拦截 | `passesPreliminaryCheck` 先调 `mob.canAttackType(...)`，EntityType tag（如 `forge:bosses`）可能导致过滤"看不见" | 验收时检查 `canAttackType` 对合并后实体类型的影响 |
| filter 在扫描 predicate 内 | 每个候选实体都调用，40~64 格半径 + 高频（10 tick 一次） | filter 必须无分配、无 I/O、无世界查询；关系表用静态 Map |
| 同类判定依赖 type 一致性 | Hall 目前统一 `hall:hall`；若给某些实体换 type（如拆分王庭/腐化）忘记统一，会出现"同类互殴" | 定义常量集中管理（见 §7） |
| 玩家还手窗口 | Hall 用 `RETALIATION_WINDOW_TICKS=100` + `lastAttacker`；corruption 无 | 合并保留 Hall 版（体验更好）；`Deterrence` 数值并入统一"威胁"能力 |
| 目标遗失/换目标 | `InfectedTargetGoal.canUse` 在已有 target 时 false；把 target 清空的逻辑分散在 `stop/setTarget/其它 goal` | 验收测试：目标死亡/逃出范围/进入创造模式后的行为 |
| 击杀→感染的递归 | `onKilledEntity` 里 `applyEvolution` 会 `discard` 受害者并生成新实体，又可能触发 `BlockSpread`/群系更新 | 保留 Hall 的"先快照坐标再转化"写法；不要在 `EntityEvolveEvent` 里再触发扩散（防递归） |

---

## 6. corruption → infcore 替换映射表

> 结论口径：**可直接用** = 换 import/常量即可；**需适配** = 需要改调用点或加胶水；**不支持需自研** = infcore 无对应抽象，保留/重写 corruption 逻辑。
> corruption 路径前缀 `corruption/src/main/java/com/corruption/`。

| # | corruption 现状（类/机制） | infcore 对应 | 结论 | 具体做法与依据 |
|---|---|---|---|---|
| 1 | `overworld/manager/AssimilationMapManager`：配置驱动的"实体类型→实体类型"转换表，带成功率、条件（NOT_BABY/持久 tag/NBT 相等）、成功回调（改名/持久化），玩家单独走 `tryAssimilatePlayer` | `evolution.EvolutionManager` + `EvolutionDataProvider`（JSON：source→weighted targets，keepNbt/keepEquipment） | **需适配** | 静态规则（`loadDefaults` 9 条 + 配置条目）可映射为 JSON / `mergeOverrides(type, "from -> to : weight")`；成功率用 weight 近似或调用方先 roll；条件与 onSuccess 回调保留在调用方（转换前谓词判断、成功后 `EntityEvolveEvent` 监听）。玩家同化：`applyEvolution` 对 `Player` 不 discard（见 §2.7 执行语义），需继续走"生成 infection_player 分身"路径 |
| 2 | 同化触发点：`CorruptionEffect.applyEffectTick`（血量≤0 时）+ `BloodyConcentrateSyringeItem`；`AbstractCorruptionEntity.awardKillScore` 里注释掉了同化 | infcore 的触发点：`InfCoreEventHelpers.handleLivingDeath` → `IKillCounter.onKilledEntity` | **需适配** | 合并后统一触发语义：王庭=击杀转化（Hall 现有）；腐化=击杀/效果/注射。腐化的注射/药水触发保留自研，但最终都调 `applyEvolution(level, victim, CORRUPTION_INF_TYPE)`，由 JSON 决定目标 |
| 3 | `overworld/manager/BearerManager`：承载者吞噬值→概率进化（条件=夜晚、吞噬值阈值、baseChance、成功后加"世界演化值"） | `EvolutionManager`（无"吞噬值/概率/条件"字段） | **需适配（保留吞噬机制）** | 吞噬逻辑留在 `BearerEntity`；满足阈值/条件后由调用方 roll 概率，成功再 `applyEvolution(level, bearer, CORRUPTION_EVO_TYPE)`（JSON 里给 bearer 配规则）；"世界演化值 +N"改用新的 WorldData（见 #7）。**不要依赖 fallback**（会把 bearer 转成别的生物） |
| 4 | `overworld/registry/entitis/base/AbstractCorruptionEntity`：`kind` 枚举、`attackSameKind`、`canAttackTarget`、`foundGoal`、`KILL_COUNT` synched data（`awardKillScore` +1） | `IInfectedEntity` + `IKillCounter`/`KillCountCapability` + `InfectedTargetGoal` + `ITargetFilter` | **需适配** | 实体基类改为 `implements IInfectedEntity, IKillCounter`（或让公共基类实现）；`getInfectionType()` 返回合并后阵营 type；`getKillCount/set/add` 改为委托 `CapabilityRegistry.KILL_COUNT`（参考 Hall `AbstractHallEntity` L312-329）；`kind` 保留为普通字段；`fight` 语义迁到 filter（§5.3） |
| 5 | `overworld/registry/entitis/ai/SmartTargetGoal`（约 300 行）：黑名单/不可达监控、checkInterval、trackingRangeMultiplier、playersOnly、excludedEntityTypes、throughWall、mustReach、attackSameKind/attackOtherBaseMobs、modid 白名单（`ConfigHelper.isModidInTargetWhitelist`） | `InfectedTargetGoal`（range/mustSee/scan=10/targetOtherInfected/filter） | **部分直接、部分需适配/自研** | `range`→`.range()`；`throughWall`→`.mustSee(false)`；创造/旁观排除、最近目标、同类过滤→内建；`attackSameKind/attackOtherBaseMobs/playersOnly/excludedEntityTypes/modid 白名单`→`ITargetFilter`；**`trackingRangeMultiplier`、不可达黑名单（5s）、checkInterval 可配置：infcore 不支持**→保留自研 Goal 或给 infcore 提需求；`canContinueToUse` 的攻击范围/追击范围分离 infcore 也没有 |
| 6 | `overworld/registry/entitis/ai/SmartMoveGoal` 等移动/大脑 AI | 无 | **保留自研** | infcore 只有索敌 Goal，没有移动/寻路封装 |
| 7 | `overworld/data/SyncDataHelper`：世界级 `evolutionary`(float) + `difficulty`(int) + 8 段 `PhaseSetting`（0~1e8）；客户端通过 `UniversalPacket("sync_corruption")` 接收；`CorruptionData`(SavedData, name=`corruption_system_data`) 落盘 | `AbstractWorldData` + `WorldDataRegistry`（磁盘+同步） | **需适配（推荐用 infcore WorldData 收编）** | 新建 `RotRuinWorldData extends AbstractWorldData`：`serializeNBT/deserializeNBT` 存 evolutionary/difficulty，`writeSyncData/readSyncData` 只同步这两个值（参考 GodSickNeo `GSStageData` 的成熟写法）；注册 ID 用 `rotten_ruins:stage`（**注意 §8.4 文件名冒号风险**，最好先修 infcore 或改用无冒号 SavedData name）；8 段 Phase 保留为纯函数（由 evolutionary 推算），不必进数据 |
| 8 | `overworld/manager/CorruptionNodeManager`（SavedData `corruption_nodes`）+ `CorruptionNode`（center/radius/intensity/centerPhase/phasePoints）+ `NodeQueryResult`（叠加阶段/强度）+ `NodeConfig`（10 个阶段阈值等） | `AbstractWorldData`（只能解决持久化/同步），无"空间节点/叠加查询"抽象 | **数据可收编、逻辑自研** | 节点表 → `RotRuinNodeData extends AbstractWorldData`（NBT ListTag 存节点；同步只发 nodeCount/maxPhase 元数据，参考现有 `writeSyncData`）；节点几何/叠加/查询/自然增长保留自研。**infcore 不支持**空间节点系统 |
| 9 | `overworld/registry/block/base/AbstractSpreadingBlock` + `overworld/manager/SpreadRuleManager`（tag 匹配 DIRT/LEAVES/LOGS/BASE_STONE_OVERWORLD/REPLACEABLE、环境谓词 ONLY_PLANTS/AIR_STRICT/LOG_LEAVES、权重、maxCount 密度、萌发 sprout pool、每规则回调 +evolutionary） | `ISpreadBlock` + `BlockSpreadManager`（JSON type/source→targets/权重/dropResources） + `BlockSpreadEvent` | **部分可直接、部分需适配、部分自研** | ① 方块转化：datagen 时把 tag 展开成具体方块写 `infcore_blockspread`（Hall 已用数组展开方式做过 LEAVES/LOG/PLANKS）；② 目标状态谓词/密度上限/萌发池：infcore 无 → 保留在方块 `randomTick`/`canSpreadTo` 中先判定，通过后再 `BlockSpreadManager.applySpread`；③ 每规则回调（+0.25 演化值等）→ 改为 `BlockSpreadEvent.Post` 监听（可按 spreadType+source 过滤）；④ 节点控制扩散许可 → `BlockSpreadEvent.Pre` 里 cancel（可做到零改 infcore）；⑤ 反向/净化（`reverseSpread`+快照）→ 保留 Hall 的 `HallBiomeProcessor`/`HallDiskStorage` 方案 |
| 10 | `BiomeCorruptionProcessor` + `BiomeCorruptionData` + `InfectionTrackerHandler` + `MixinBiomeManager` + `UpdateBiomePacket`（群系写入、脏位图 mixin 拦截 `getBiome`、队列限流、快照） | `BiomeSpreadHelper.setColumnQuartBiome` / `syncChunk`；`AbstractWorldData`（持久化钩子） | **可直接用（底层）/需适配（队列与 mixin）** | 直接用 infcore 的两个工具替换 corruption 复制版（**注意 corruption 版是"单维度静态 storage + 全局 PENDING_POS"旧实现，Hall 版已修好，建议以 Hall 版为基线合并**）；队列/快照/背压/回退保留；`MixinBiomeManager` 与 Hall 的群系同步策略要二选一/合并（见 §8.5） |
| 11 | `overworld/registry/capability/base/*`（`AbstractCapability`/`CapabilityProvider`/`IModCapability`，与 infcore 同构） + `CapabilityRegistry`（自己 token） + `network/s2c/PacketSyncCapability` | infcore `core.capability.*` + `PacketSyncCapability` | **可直接用（删重复）** | corruption 的基类与 infcore 0.1.2 逐行同构；改用 infcore 版本 + `CapabilityRegistry.register(id, token)`；`Deterrence`/威胁等自定义能力只要注册 id 即可走 infcore 同步链路。**注意 `CapabilityProvider.initSync(entity)` 必须调用** |
| 12 | `Deterrence`/`DeterrenceManager`：玩家击杀腐化生物累计威慑值（按 kind 权重 1/2/2/4），玩家死亡清零 | 无"玩家计数器"抽象 | **复用 infcore 基类自研** | `Deterrence extends infcore AbstractCapability<IDeterrence>` + `CapabilityRegistry.register(rotten_ruins:deterrence, ...)`；与 Hall 的 `ThreatCapability` **语义合并为同一能力**（威胁点数=威慑值），否则玩家会被两套数值系统重复判定 |
| 13 | `CorruptionData`（SavedData `corruption_system_data`）+ `ISaveData/ISaveDataHolder`/`AbstractSaveData` + `PacketSyncSaveData` | `AbstractWorldData` + `WorldDataRegistry` + `PacketSyncWorldData` | **可直接用（框架级替换）** | corruption 的 save-data 框架就是 infcore worlddata 的前身（连 `writeSyncData/readSyncData/markSyncDirty` 命名都一致）；迁移为 `AbstractWorldData` 后网络包由 infcore 自动处理，删除 `PacketSyncSaveData` 与其注册 |
| 14 | 全局难度：`SyncDataHelper.difficulty`(int) + `PhaseSetting`(8 段) + `NodeConfig.biomeDiffusionPhase` + `ConfigDrivenSpawnModifier`/`CorruptionSpawnEventHandler` 按 node phase 刷怪 | `DifficultyHelper`/`DifficultyRegistry`/`DifficultyType`（全局单 ResourceLocation + Pre/Post 事件 + 命令 + 客户端同步） | **需适配** | 合并后"玩家可见难度"用 `DifficultyHelper`（Hall 4 档），腐化的 `difficulty` int 降级为系数配置或并入 4 档系数；"演化阶段"(PhaseSetting) 保留为 WorldData 的派生值，不作为难度 ID（否则 8 段 + 4 档两套全局状态会互相打架）；节点 phase 仍只影响腐化刷怪 |
| 15 | 击杀计数：`AbstractCorruptionEntity.KILL_COUNT`（synched data int，`awardKillScore` +1，NBT 持久化） | `IKillCounter` + `KillCountCapability`（能力 NBT + 自动同步 + 可取消事件） | **可直接用** | 删掉 `KILL_COUNT` accessor 与 `defineSynchedData`/`readAdditionalSaveData` 里的手工持久化；改用 Hall 的委托写法；额外收益：`KillCountAddEvent` 可取消、`KillCountChangeEvent` 可监听（驱动其他系统） |
| 16 | 命令：`/corruption query|set value|set phase|add|remove`（演化值）、`/corruption node ...` | `/infcore difficulty set/get/list` | **部分直接、部分保留** | 难度命令用 infcore；演化值/节点调试命令保留（合并后改根命令名，如 `/rottenruins stage ...`），可参考 `DifficultyCommand` 的 brigadier 写法统一风格 |
| 17 | datagen：`DataGenEvent` 只注册原版 provider，无 infcore 数据 | `EvolutionDataProvider`/`BlockSpreadDataProvider` | **可直接用** | 仿 Hall 的 `datagen/gen/*.java` 新建两个 provider（`super(out, RotRuinMod.MODID, "corruption")`），把 assimilation/bearer/扩散规则数据化 |
| 18 | `api/ICorruptionEntity`（空标记）+ `IVariantEntity`/`IVariant`（外观变种）+ `ISevenSins`（七宗罪） | 无对应 | **保留自研**（`ICorruptionEntity` 可并入 `IInfectedEntity`） | 变种/七宗罪是独立玩法抽象，不属阵营；`ICorruptionEntity` 若只用于"友军判定"则合并后删除，统一 `IInfectedEntity` |

**工作量粗估**（拆给合并实施时参考）：可直接用/删重复约 40%（能力框架、worlddata 框架、群系写入、击杀计数、datagen 底座）；需适配约 45%（evolution 两套转化、spread 规则、索敌、难度、worlddata 业务、命令）；必须保留自研约 15%（节点系统、移动/大脑 AI、快照净化、变种/七宗罪）。

---

## 7. modid 改名影响（hall → rotten_ruins）

### 7.1 会跟着变 / 不会跟着变

| 对象 | 现状 | 改名后 | 机制 |
|---|---|---|---|
| Java 注册表 ID（方块/物品/实体/效果/粒子/维度/群系…） | `hall:*` | 由 `DeferredRegister(..., HallMod.MODID)` 自动变 `rotten_ruins:*` | 自动 |
| `HallMod.INFECTION_TYPE`（进化 type 常量） | `hall:inf` | `rotten_ruins:inf`（自动，因为它用 MODID 构造） | 自动（**但 JSON 不自动**） |
| `AbstractHallEntity.getInfectionType()`（阵营） | `hall:hall` | `rotten_ruins:hall`（自动） | 自动（**但硬编码判断处不自动**） |
| `AbstractSpreadBlock/SpreadPillarBlock/HallSpreadEventHandler.getSpreadType()` | `hall:spread` | `rotten_ruins:spread`（自动） | 自动（**但 JSON 与硬编码判断不自动**） |
| `HallDifficulty` 4 个难度 ID | `hall:easy` 等 | `rotten_ruins:easy` 等（自动） | 自动（**但存档内旧值不会自动迁移**） |
| datagen 输出目录 | `data/hall/**`（generated 138 文件） | `data/rotten_ruins/**` | 跑 `runData` 自动生成；**旧目录必须手工删除** |
| 资源目录 | `assets/hall/**`（main 318 + generated 174 文件） | `assets/rotten_ruins/**` | 手工移动目录（datagen 只覆盖部分） |
| JSON 内引用 | 442 个 JSON 里 1190 处 `hall:` | `rotten_ruins:` | datagen 重跑可覆盖大部分；**手写 JSON / 旧生成物需清理** |
| Java 内引用 | 390 个 Java 文件里 226 处 `HallMod.MODID`（自动） + **19 处字面量 `"hall"`**（不自动） | 见 §7.2 清单 | 手工 |
| SavedData 文件名 | `hall_spread_data`、`hall_difficulty_selected` 等**硬编码常量** | 若保留原名则旧存档可续；改名则旧数据读不到 | 手工决定 |
| infcore 数据（evolution/blockspread） | `data/hall/infcore_evolution|infcore_blockspread/default.json`，内含 `hall:*` type | 需 `data/rotten_ruins/...` 且 type 改 `rotten_ruins:*` | **必须重跑 datagen**，否则运行期静默失效 |
| `infcore:kill_count` 能力数据 | 实体 NBT `ForgeCaps` key `infcore:kill_count` | **不变** | 与 modid 无关（infcore 命名空间） |
| `infcore_global_difficulty` SavedData | 存的是 ResourceLocation 字符串（`hall:easy` 等） | 改名后读出来是 `hall:*`（未知 ID） | **需要迁移/别名** |
| 网络频道 | `hall:main`（protocol 1.0，9 个包 id） | `rotten_ruins:main` | 客户端/服务端同 jar 无碍；外部工具/第三方兼容代码若按名挂钩会断 |
| Mixin 配置/assets 包名 | `hall.mixins.json`、`org.bytechen.hall.*` | 建议 `rotten_ruins.mixins.json` + 包名 `org.bytechen.rottenruins`（或保留包名只改 modid，降低风险） | 手工；包名不改也能跑，**建议只改 modid、包名后置**，缩小一次变更面 |

### 7.2 改名 Checklist（infcore 相关部分加粗）

1. 新增唯一常量类（如 `RotRuinConstants`）：`MODID="rotten_ruins"`；**`INFECTION_TYPE`（阵营）、`HALL_INF_TYPE`、`HALL_EVO_TYPE`、`CORRUPTION_INF_TYPE`、`CORRUPTION_EVO_TYPE`、`HALL_SPREAD_TYPE`、`CORRUPTION_SPREAD_TYPE`** 全部集中；`HallMod.INFECTION_TYPE` 等旧常量改为引用它，禁止再散落 `new ResourceLocation(MODID,...)`——当前已知的"就地拼 type"至少两处：`AbstractHallEntity.onKilledEntity`（L285 用 `HallMod.INFECTION_TYPE`，尚可）与 `MonolithEntity.tick`（L60-61 用 `new ResourceLocation(HallMod.MODID,"evo")`，**改名后代码会变而 JSON 不会变，必须收编为常量**）。
2. 修复 **6 处硬编码 `"hall"` 阵营判断**：
   - `event/ForgeEventHelpers.java` L166-174 `isHallInfected`（namespace + path）
   - `event/impl/BlackHolePhysicsHandler.java` L298-316 `isExcluded`
   - `overworld/registry/entities/population/apostle/CollapsarEntity.java` L1233-1237
   - `overworld/registry/entities/population/skills/MeteoriteEntity.java` L266-267（`getPath().equals("hall")`）
   - `overworld/registry/entities/population/defensive/WindingColumnEntity.java` L421-428（仅 namespace）
   - `overworld/difficulty/HallDifficultyAttributes.java` L86-92（仅 namespace，MODID 改后自动正确，但如果阵营 type path 改了就仍需按新 type 判断）
   统一改成 `RotRuinConstants.isOurInfectedType(type)`（判断 namespace 属于本 mod，且 path 在允许集合内）。
3. 修复 **3 处硬编码 spread namespace `"hall"`**：`overworld/swarm/HallSpreadEventHandler.java` L217-219、`blocks/impl/HallVineBlock.java` L126、`registry/feature/HallVineFeature.java` L141 → 改用 `HallSpreadTypes` 常量/`MODID`。
4. 修复其它硬编码（非 infcore 但同批）：`datagen/gen/HallBiomeData.java` L31/L35、`datagen/gen/FleshDimensionData.java` L63/L286、`overworld/registry/HallDimensions.java` L34（`"hall","heall"`）、`command/ThreatCommand.java` L41（字面量 `"hall"` 指令根）、`HallBlockSetTypes` 的 `"hall"` 字符串。
5. **删除 `src/generated/resources/data/hall`（138 文件）与 `assets/hall`（174 文件）后重跑 `runData`**；确认新生成 `data/rotten_ruins/infcore_evolution/default.json` 的 `type` 是 `rotten_ruins:*`，且**没有重复 `(type,source)`**。
6. `mods.toml`：infcore `versionRange` 改 `[0.1.2,)`；`displayName`、`modId`、logos 等同步。
7. 迁移兼容（二选一，推荐同时做）：
   - **难度值兼容**：启动时（如 `ServerStartedEvent`）若 `DifficultyHelper.getDifficulty()` 返回 `hall:*`，映射到对应 `rotten_ruins:*` 并 `setDifficulty` 写回；`DifficultyScaleProfile.tierOf` 里加旧 ID 别名（它现在对未知 ID 一律 NORMAL，会静默掉档）。
   - **旧 registry ID**：world save 里旧的 `hall:*` 方块/实体无法自动变 `rotten_ruins:*`——这是比 infcore 层面大得多的迁移问题（属于整体合并任务，建议由 Lead 单独评估，最省事的做法是 modid 不改只改显示名，或写世界升级映射）。
8. 跨模组公告：其它 mod 若按 `hall:inf`/`hall:hall` 字符串判断阵营/调用 `applyEvolution`，改名后会静默失配；目前**仓库内未发现此类引用**（GodSickNeo 用 `godsick_neo:*`，corruption 无 infcore 代码），风险主要在外部生态。
9. 验证：`-Dinfcore.verbose=true` 启动，看 `EvolutionManager`/`BlockSpreadManager` 的 scan/parsed 日志里出现新 type；用 `/spread` 与击杀感染各测一次。

### 7.3 infcore 的跨模组兼容约定（现状）

- 官方约定只有一条且写在文档里：**`type` 用各自 modId 作为命名空间**（`docs/evolution-system.md` "多个模组的规则隔离"）。扫描器本身不校验 namespace 与 owner modid 是否一致——所以物理隔离靠自觉。
- `replace` 只影响**同 `(type,source)`**，不同 namespace 天然不冲突。
- 单个服务器只能加载**一个 infcore jar**（同 modid 重复会被 FML 拒绝），所以所有消费方共享同一份 `EvolutionManager`/`WorldDataRegistry`/`NetworkHelper` 状态；这是"必须统一 infcore 版本"的根本原因。
- GodSickNeo 的存在证明横向兼容可行（0.0.2 消费方 + 0.1.x 运行时；用到的 API 无删除），但 **GodSickNeo 不实现 `IInfectedEntity`**，两家阵营互不相认，属已知不对称（见 §5.3）。

---

## 8. 集成前置条件与风险

### 8.1 版本矩阵

| 项 | Hall（现状） | corruption（现状） | infcore | 合并建议 |
|---|---|---|---|---|
| MC | 1.20.1 | 1.20.1 | 1.20.1 | 1.20.1 |
| Forge | **47.4.20** | **47.4.18** | **47.4.21**（编译目标） | 合并工程用 **47.4.21**（与 infcore 对齐）；只升 patch，无 API 破坏 |
| mappings | official | official（`gradle.properties` 写 parchment 但 `build.gradle` 实际 `official`） | official | official |
| Java | 17 | 17 | 17 | 17 |
| GeckoLib | 4.7.1（compileOnly + patchModuleInfo + jarJar） | curse.maven `388172:7553267`（implementation） | — | 统一 4.7.1，改 jarJar/或统一依赖坐标；注意 corruption 的 curse maven 坐标可能失效（其 build.gradle 注释已自认） |
| Cloth Config | 12.0.109（compileOnly + jarJar） | 12.0.109（implementation + jarJar） | — | 12.0.109，只 jarJar 一次 |
| Curios | 5.14.1+1.20.1 mandatory | 无 | — | 保留（王庭装备强依赖） |
| JEI | 15.2.0.27 optional | 15.2.0.27 optional | — | 保留 |
| infcore | `implementation fg.deobf("org.bytechen:infcore:0.1.2")`（工程内 maven 镜像 `libs/`）+ `mods.toml mandatory "*"` | 无 | 0.1.2 | **external mandatory `[0.1.2,)`，不 jarJar** |
| jarJar | cloth + geckolib | cloth（geckolib 只是 implementation） | — | 合并后：cloth+geckolib jarJar；infcore **不做** jarJar |
| mixin | hall.mixins.json（客户端渲染为主） | corruption.mixins.json（含 `MixinBiomeManager` 等服务端） | 零 mixin | 合并两个 mixin 配置；infcore 不冲突 |

### 8.2 Forge 47.4.21 vs 47.4.20 vs 47.4.18 结论

- **infcore 用 47.4.21 编译、Hall 用 47.4.20 运行**：目前已经在这么跑（Hall `settings.gradle` 明确记录"不能用 includeBuild 复合构建，因为 47.4.21/47.4.20 的 FG 仓库会互相覆盖"）。从源码使用面看 infcore 只用 Forge 常规 API（`SimpleChannel`/能力/SavedData/事件/注册表），未见 47.4.21 独有符号；Forge patch 版本间保持二进制兼容，所以**运行风险低**，但仍建议合并工程直接把 Forge 对齐到 47.4.21 消除这一不确定性。
- **corruption 47.4.18 → 合并到 47.4.20/21**：patch 升级，源码级无 API 破坏；但 corruption 用了较多 mixin（`MixinLivingEntity`、`MixinBiomeManager`、`MixinItemRenderer` 等），patch 升级后需重新验证 mixin 目标（尤其 `GameRenderer`/`LevelRenderer`/`ItemInHandRenderer` 这类易变点）。风险等级：中（构建/兼容验证成本，不是 API 风险）。
- 结论：**合并工程统一 Forge 47.4.21 + official mappings**；infcore 保持 0.1.2 不升级（除非采纳 §8.4 的修改）。

### 8.3 依赖声明建议（mods.toml / build.gradle）

```toml
[[dependencies.rotten_ruins]]
modId = "infcore"
mandatory = true
versionRange = "[0.1.2,)"
ordering = "AFTER"      # 让 infcore 先完成注册（capability/事件顺序更稳）
side = "BOTH"
```
- **不要 jarJar infcore**：GodSickNeo 等消费方通过外部 jar 依赖同一 modid；jarJar 会引入"嵌套版本选择"复杂度，且升级时容易与外部 jar 冲突。保持 Hall 现有 `libs/` 工程内 maven 镜像 + `fg.deobf` 即可（新克隆无需联网）。
- `syncInfcore` 任务需修：`workingDir = file('InfectionCoreFramework')` 在 Hall 根下不存在（实际是 `../InfectionCoreFramework`）；且版本号硬编码在 `build.gradle` L316 与依赖声明 L165 两处。若合并后仍要改 infcore 源码，建议改为 `file('../InfectionCoreFramework')` 并抽一个 `infcoreVersion` 变量统一。
- 依赖升级提示：Hall 当前 `mods.toml` 对 infcore 写 `"*"`，而代码使用 `EvolutionManager.registerFallback`（0.1.x 才有）——若不收紧，玩家装 0.0.2 会在 `UlceratedConversionRules.register()`（commonSetup）直接 `NoSuchMethodError` 崩服；**这是必须修的**。

### 8.4 infcore 是否需要同步改动（是否需要 0.1.3）

| 需求 | 优先级 | 建议 |
|---|---|---|
| `WorldDataRegistry` SavedData 文件名含 `:`（`id.toString()`），Windows 下可能写盘失败 | **高（需实机验证）** | 改用 `id.getNamespace()+"_"+id.getPath()` 作为文件名，并给旧名加读取迁移；否则合并后腐化阶段/节点数据在 Windows 存档可能丢。**这是唯一建议必须动 infcore 的点** |
| `KillCountCapability.addKillCount(int)` / `setKillCount(int)` 未检查 `enabled`（带 `LivingEntity` 的重载才检查） | 中 | 合并若需要"某生物不计数"，必须在实体层改用带实体重载或 `killCounterEnabled()=false` 时别走 `onKilledEntity` 默认实现；或修 infcore 让两个重载语义一致 |
| `InfectedTargetGoal` 可配置 scanInterval / trackingRange / 阵营关系回调 | 中 | corruption 的消耗点（黑名单/追踪倍率）目前只能自研；若愿意改 infcore，可加 `builder.targetTrackingRange()`、`builder.relationResolver()`（返回 FRIEND/FOE/NEUTRAL），一次解决多阵营外交 |
| `BlockSpreadManager` 支持 tag source / target 状态 / 每规则谓词 | 中 | 若不想保留 corruption 的 `SpreadRuleManager`，需要 infcore 扩展；否则按 §6#9 的"谓词留在方块层 + Post 回调"方案做 |
| 文档 | 低 | `docs/architecture.md` 已落后（缺 blockspread/difficulty/worlddata/goal/biome/network 新包）；`docs/getting-started.md` 的 `[[dependencies.infcore]]` 写法是错的（应为 `[[dependencies.<modid>]]`），会误导下游 |
| 版本号 | 低 | 若采纳上述修改，发 0.1.3 并同步 `libs/` 镜像 + Hall `build.gradle` 版本号 |

### 8.5 其它集成风险（按严重度）

| 风险 | 说明 | 对策 |
|---|---|---|
| **两套群系扩散/同步实现冲突** | Hall：`HallBiomeProcessor` + `BiomeSpreadHelper.syncChunk` + `HallSpreadSaveData`；corruption：`BiomeCorruptionProcessor` + `MixinBiomeManager.getBiome` + `UpdateBiomePacket` + `CorruptionDiskStorage`。同一合并 mod 里保留两套会出现：两套静态队列/线程池、两套快照目录、两套客户端群系覆盖逻辑（mixin 拦截 getBiome 会与 Hall 的区块重发互相盖） | 合并成一套：以 Hall 版为基线（维度隔离、HallSpreadSaveData 钩子），吸收 corruption 的"脏区块位图"优化；mixin 群系覆盖只保留一套并明确优先级（王庭 hall_wasteland vs 腐化群系） |
| **两套网络层** | Hall `hall:main` 9 包；corruption `corruption:main` 8 包；包 id 依赖注册顺序，UniversalPacket action 两套 | 合并成 `rotten_ruins:main` 一套；删除 corruption 的 capability/savedata 同步包（由 infcore 接管）；UniversalPacket 的 action 注册表合并去重 |
| **两套 capability/savedata 框架** | corruption 的 `capability/base` 与 infcore 同构、Hall 的简化版与之不兼容；同实体上可能挂三套 capability id | 统一：infcore 负责有同步需求的能力（击杀计数/威慑/威胁/自定义），Hall 旧简化版只保留纯服务端无同步能力或全部迁移 |
| **两套"玩家仇恨"数值** | Hall `ThreatCapability`（所有实体，0 起，击杀王庭 +maxHP/5，死亡清零，阈值 5，100 tick 还手窗口） vs corruption `Deterrence`（玩家击杀腐化 +kindWeight，死亡清零） | 合并为**一个**威胁能力 + 一个统一阈值；否则同一玩家对不同阵营数值分裂，索敌/还手体验不一致 |
| **两套世界数据/演化阶段** | Hall 无世界演化值（用 4 档难度）；corruption `evolutionary` 8 段 + `difficulty` int + 节点 phase 三层 | 定唯一"世界演进"数值（推荐 WorldData 存 evolutionary，派生 8 段只做展示）+ 唯一难度（infcore DifficultyHelper 4 档）；节点 phase 只作用于腐化刷怪/扩散 |
| **属性缩放重复叠加** | Hall `HallDifficultyAttributes` 按 4 档乘 MaxHealth/AttackDamage；corruption `ForgeEventHandler.onEntityJoin` 按节点强度加 health/armor 并回血；两者都监听 `EntityJoinLevelEvent`，合并后同一实体可能被乘两次 | 明确"哪个阵营的怪吃哪套缩放"：王庭怪只吃难度倍率，腐化怪只吃节点强度（或引入互斥标记），并在 `EntityJoinLevelEvent` 里按阵营分流 |
| **Player 感染语义差异** | infcore `EvolutionManager.replaceEntity` 对 `Player` 不 discard（玩家本体保留，另生成感染体）；corruption 的 `tryAssimilatePlayer` 是"生成同名感染玩家克隆"；Hall 是"击杀玩家→进化表 player→inf_player" | 统一为 infcore 语义（击杀/效果触发 evolutionary 时对 Player 生成克隆、玩家本体按原版死亡流程），并在 `LivingDeathEvent` 顺序上验证"感染体生成"与"玩家掉落/重生"不打架 |
| **数据扫描的性能与顺序** | 两个 manager 首次调用时全量扫描所有 mod jar；合并后 mod 数减少但数据文件翻倍；扫描顺序影响重复规则归属 | 数据纪律（type 命名空间 = 功能域）+ 启动日志确认 `getEntryCount`；避免依赖跨文件同 key 覆盖顺序 |
| **死代码/重复类** | 合并后会有两套 `BiomeSpreadHelper` 复制版、两套 `NetworkHelper`、两套 `PacketSyncKeyframe`/`EffectSyncPacket`、两套 `UniversalPacket`、两套 `AbstractCapability`、两套 `CorruptionManager` 命名等 | 合并实施时按 §6 清单删重复；注意 `com.corruption.*` 包名与 `org.bytechen.hall.*` 包名并存可编译，但长期建议统一包名（可后置） |

### 8.6 合并后 infcore 相关验收清单

- [ ] 启动日志：`EvolutionManager: scan complete, N evolution entries loaded across M types`，M 应 = 至少 hall_inf/hall_evo/corruption_inf/corruption_evo；`BlockSpreadManager` 同理且 type 数 = 2。
- [ ] `/infcore difficulty list` 显示 4 档 `rotten_ruins:*`，存档重启后难度不丢（验证 `infcore_global_difficulty` 迁移）。
- [ ] 击杀感染：王庭怪击杀 vanilla 生物 → 对应 `rotten_ruins:hall_inf` 规则生效；击杀无规则生物 → 兜底（scout/pursuer/monolith 按体积）。
- [ ] 腐化同化：腐化怪击杀/注射 → `rotten_ruins:corruption_inf` 规则生效，**不会**变成王庭溃烂生物。
- [ ] 阵营：王庭与腐化实体之间按设计互打/不打；`ForgeEventHelpers` 友伤取消只作用于本 mod 感染体；玩家威胁/威慑统一后不再出现"双数值"。
- [ ] `/spread` 指令：`hall_spread` 与 `corruption_spread` 各转各的，同 source 不互相吞规则。
- [ ] 群系：方块扩散 → 群系变化 → 客户端重载区块后视觉正确（两套群系系统只留一套）。
- [ ] Windows 存档：腐化演化值/节点数据重启后仍在（验证 §8.4 文件名问题）。
- [ ] 与 GodSickNeo 同时加载：infcore 版本选择正确（0.1.2 满足双方 `*` 声明）、无 `Duplicate mod` / `NoSuchMethodError`；双方互杀行为符合发布说明。

---

## 9. 附录

### 9.1 infcore 类全量清单（46 个）

```
api/ (12)
  IInfectedEntity.java  IKillCounter.java  IModWorldData.java
  block/ISpreadBlock.java
  difficulty/DifficultyType.java
  event/BlockSpreadEvent.java  event/DifficultyChangeEvent.java  event/EntityEvolveEvent.java
  event/KillCountAddEvent.java  event/KillCountChangeEvent.java
  goal/InfectedTargetGoal.java  goal/ITargetFilter.java
core/ (34)
  Infcore.java
  biome/BiomeSpreadHelper.java
  blockspread/BlockSpreadData.java  BlockSpreadEntry.java  BlockSpreadManager.java
             BlockSpreadTarget.java  SpreadHelper.java
  capability/AbstractCapability.java  CapabilityProvider.java  CapabilityRegistry.java
             IModCapability.java  KillCountCapability.java
  client/ClientPacketHandlers.java
  command/DifficultyCommand.java
  datagen/BlockSpreadDataProvider.java  DataGenEvent.java  EvolutionDataProvider.java
  difficulty/DifficultyHelper.java  DifficultyRegistry.java  GlobalDifficultyData.java
  event/InfCoreEventHandler.java  InfCoreEventHelpers.java
  evolution/EvolutionData.java  EvolutionEntry.java  EvolutionManager.java
            EvolutionTarget.java  InfectionFallback.java
  network/NetworkHelper.java  PacketSyncCapability.java  PacketSyncDifficulty.java
          PacketSyncWorldData.java
  util/ThrottledLogger.java
  worlddata/AbstractWorldData.java  WorldDataRegistry.java
```

### 9.2 关键文件路径索引

| 主题 | 路径 |
|---|---|
| infcore 主类/注册 | `InfectionCoreFramework/src/main/java/org/bytechen/infcore/core/Infcore.java` |
| infcore 事件桥 | `…/core/event/InfCoreEventHandler.java`、`…/InfCoreEventHelpers.java` |
| 阵营/进化 | `…/api/goal/InfectedTargetGoal.java`、`…/core/evolution/EvolutionManager.java` |
| 扩散 | `…/api/block/ISpreadBlock.java`、`…/core/blockspread/BlockSpreadManager.java`、`…/SpreadHelper.java` |
| 世界数据 | `…/core/worlddata/AbstractWorldData.java`、`…/WorldDataRegistry.java` |
| 难度 | `…/core/difficulty/*`、`…/api/difficulty/DifficultyType.java`、`…/core/command/DifficultyCommand.java` |
| datagen | `…/core/datagen/EvolutionDataProvider.java`、`…/BlockSpreadDataProvider.java` |
| 能力 | `…/core/capability/*` |
| infcore 文档 | `InfectionCoreFramework/docs/{architecture,evolution-system,getting-started,kill-count-system,network}.md`（**已落后**于 0.1.2 代码） |
| Hall 数据实例 | `RottenRuinsSplendiding/src/generated/resources/data/hall/infcore_evolution/default.json`、`…/infcore_blockspread/default.json` |
| Hall datagen | `…/src/main/java/org/bytechen/hall/datagen/gen/{EvolutionDataProvider,BlockSpreadDataProvider}.java` |
| Hall 阵营/击杀 | `…/src/main/java/org/bytechen/hall/overworld/registry/entities/base/AbstractHallEntity.java` |
| Hall 难度 | `…/src/main/java/org/bytechen/hall/overworld/difficulty/*.java` |
| Hall 群系/扩散 | `…/src/main/java/org/bytechen/hall/overworld/swarm/{HallSpreadEventHandler,HallBiomeProcessor}.java` |
| corruption 同化/进化 | `corruption/src/main/java/com/corruption/overworld/manager/{AssimilationMapManager,BearerManager}.java` |
| corruption 节点 | `…/overworld/manager/CorruptionNodeManager.java`、`…/overworld/data/level/node/CorruptionNode.java` |
| corruption 扩散/群系 | `…/overworld/registry/block/base/{AbstractSpreadingBlock,BiomeCorruptionProcessor}.java`、`…/overworld/manager/SpreadRuleManager.java` |
| corruption 索敌 | `…/overworld/registry/entitis/ai/SmartTargetGoal.java` |
| corruption 实体基类/击杀计数 | `…/overworld/registry/entitis/base/AbstractCorruptionEntity.java` |
| corruption 世界数据 | `…/overworld/data/SyncDataHelper.java`、`…/overworld/registry/savedata/impl/CorruptionData.java` |
| GodSickNeo 参考实现 | `GodSickNeo/src/main/java/org/bytechen/godsick_neo/Godsick_NEO.java`（WorldData 注册）、`…/overworld/registry/savedata/impl/GSStageData.java`（AbstractWorldData 完整范例）、`…/datagen/GSEvolutionDataProvider.java` |

### 9.3 术语

| 术语 | 含义 |
|---|---|
| 阵营 / infection type | `IInfectedEntity.getInfectionType()` 返回的 ResourceLocation，只用于敌我判断 |
| evolution type | `EvolutionManager.applyEvolution` / JSON `type` 的 ResourceLocation，用于选择"哪张转化表" |
| spread type | `ISpreadBlock.getSpreadType()` / `BlockSpreadManager.applySpread` / JSON `type` 的 ResourceLocation |
| fallback | 某 evolution type 下"没有专属规则"的生物按碰撞体积被替换的兜底档位（程序注册） |
| kind（corruption） | `CorruptionEntityType` 枚举，表示谱系/等级，不是阵营 |
| 威慑 / 威胁 | corruption `Deterrence` 与 Hall `ThreatCapability` 的玩家仇恨数值，语义重复 |

---

*报告结束。本报告为只读分析，未运行 Gradle、未改动任何源码；所有路径与行号基于 2026-10-28 工作区快照。*
