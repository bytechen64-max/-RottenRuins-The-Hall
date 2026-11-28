# 生物基类与实体体系合并分析（task-5）

> 决策问题：Hall + corruption 合并为 `modid = rotten_ruins` 后，生物是统一成一个基类，还是保留两个基类（王庭系 / 腐化系）？
>
> 分析方式：只读源码。逐行读了两个基类与全部 population 基类、注册表、渲染器、动画/geo 资产；并用 gradle 缓存里的 **Minecraft 1.20.1 Forge 解编译源码 + `srg_to_official_1.20.1.tsrg` 映射**核实了 `Monster` / `PathfinderMob` 的原版行为差异（第 4 章的所有"原版行为"结论都有源码依据，不是印象）。
>
> 分析对象规模：Hall `390 java / 54,451 行`，corruption `268 java / 28,473 行`。
>
> 约束（来自任务）：corruption 的寻路 AI / brain 不搬；索敌改用 infcore 前置的 `InfectedTargetGoal + IInfectedEntity.getInfectionType() + ITargetFilter`；KING 系不搬；重复项去重。

---

## 0. 结论速览（TL;DR）

1. **推荐方案 A：只保留一个统一基类**（`AbstractRottenEntity`，以 Hall 的 `AbstractHallEntity` 为骨架、根父类仍为 `Monster`），不保留两个基类。王庭/腐化的差异放在 **population 薄层（family 层）+ 数据字段**，不是放在基类。
2. **不拆 Hostile/Neutral 两个"超类"**。核实后结论：corruption 的"同化牛/猪/鸡/羊/狼/村民"**全部是主动攻击单位**（都有 `SmartTargetGoal` + 近战/酸液伤害），"中立/被动"只是继承 `PathfinderMob` 带来的类层面事实，不是玩法事实。真要做"非敌对同化家畜"，用 flag 覆盖（`shouldDespawnInPeaceful` / `isPreventingPlayerRest` / `getSoundSource` / XP）即可；唯一无法消除的是 `Monster implements Enemy` 这个标记（铁傀儡/雪傀儡/潮涌核心会仇恨），如果产品要求连这个也不能有，再走方案 C（升级路径见 §4.4 / §5.4，改动被限制在 1 个根类 + 2 个薄层 + 约 7 个 Hall 文件）。
3. 实体规模：**Hall 10 只注册生物 + corruption 24 只注册生物 − 删除 1 只（HeavyGuard）− 删除 1 个未注册类（Avila）= 合并后 33 只注册生物**。另删 9 个 AI 文件（1,763 行）、4 个"死系统"（adaptation / variant / kind / brain）。
4. **没有必须新增的动画资产**：两侧 geo 都是 `format_version: 1.12.0`、animations 都是 `1.8.0`，clip key 都叫 `idle / walk / run / die`（+ 各自扩展），`RawAnimation` 直接兼容。
5. 最重要的 5 个坑（详见 §9）：
   - **移动速度语义相反**：Hall 无 walk 动画时用 `walkSpeed`（默认 0.6），corruption 无 walk 动画时用 `runSpeed`（多数设 1.3–3.5）→ 直接换基类会让腐蚀生物**集体变慢 2–5 倍**；
   - **腐化生物不是独立类**：27 个生物文件里 19 个依赖 `CorruptionSkillUtil/CorruptionSkillFx`，另有 28 个左右的技能/粒子/网络/工具底座文件，只搬"生物类"会全部编译不过；
   - **假死掉落时机不同**（Hall 在爆体结束掉 loot，corruption 在假死开始掉 loot）→ 直接合会双重掉落；
   - **击杀计数有两条通路**（infcore `IKillCounter` capability vs corruption 自己的同步 int 字段）→ 会双倍计数；
   - **modid 改名会打断 6 处 `getInfectionType().getNamespace().equals("hall")` 判定**，且合并后两派共用命名空间会让这些判定"全部命中"，必须改为按完整类型/阵营判断。

---

## 1. 分析范围与证据

### 1.1 精读文件

| 文件 | 行数 | 说明 |
|---|---|---|
| `org/bytechen/hall/overworld/registry/entities/base/AbstractHallEntity.java` | 1,117 | Hall 生物基类，`extends Monster` |
| `com/corruption/.../entitis/base/AbstractCorruptionEntity.java` | 1,087 | corruption 生物基类，`extends PathfinderMob` |
| Hall `population/{apostle,ecological,infected,ulcerated}/Base*.java` | 19–81 | 5 个 population 薄层 |
| corruption `population/{host,degenerate,deteriorated,linked,original,refactored}/Base*.java` | 73–361 | 6 个 population 薄层 |
| 两侧 `EntityTypeRegistry` / `ClientRenderRegistry` / `GeoBaseRender` / `IAutoRenderableEntity` / `IGeoResources` / `IKeyframeHandler` / `MixinAnimationController` / 渲染 layer | — | 注册与渲染链路 |
| infcore `IInfectedEntity` / `IKillCounter` / `ITargetFilter` / `InfectedTargetGoal` / `CapabilityRegistry` / `EvolutionManager`（`libs/.../infcore-0.1.2-sources.jar` 内存读取） | — | 索敌/击杀/进化的框架侧语义 |
| 逐族抽样精读 corruption 生物：`InfectionCow/Villager/Player/Skeleton/Bat`、`LongArmBeast`、`Bearer`、`Shocker`、`Groveler`、`HumusColumn`、`SummoningPillar`、`InfectionBoneRuins`、`HeavyGuard`；其余按构造器/属性/动画 grep 提取 | — | 映射表依据 |

### 1.2 原版行为证据来源

- `C:\Users\chy20\.gradle\caches\forge_gradle\minecraft_user_repo\mcp\1.20.1-20230612.114412\joined\decompile\output.jar`（Forge 1.20.1 解编译源码；方法名为 SRG，用 `srg_to_official_1.20.1.tsrg` 翻译）。
- 关键证据原文：
  - `Monster.java`：`protected boolean m_8028_() { return true; }` → `shouldDespawnInPeaceful = true`；`m_6935_(Player) { return true; }` → `isPreventingPlayerRest = true`；`m_5720_() → SoundSource.HOSTILE`；构造器 `this.f_21364_ = 5;`（`xpReward = 5`）；`m_6125_`→`shouldDropLoot`、`m_6149_`→`shouldDropExperience` 均 true。
  - `Mob.java`（`m_6043_` = `checkDespawn`）：`if (level.getDifficulty() == Difficulty.PEACEFUL && this.m_8028_()) this.m_146870_();`（discard）；否则才走"离最近玩家超过 `MobCategory.getMaxInstancesPerChunk()^2` 且 `removeWhenFarAway`"的距离消失分支。
  - `ServerPlayer.java:850`：睡觉检查用 `this.m_9236_().m_6443_(Monster.class, AABB(±8, ±5, ±8), p -> p.m_6935_(this))`，非空即 `BedSleepingProblem.NOT_SAFE` → **"怪物阻止睡觉"只扫 `Monster.class`**。
  - `IronGolem.java:79`：`p_28879_ instanceof Enemy && !(p_28879_ instanceof Creeper)`；`:98` 被打时 1/20 概率反锁 Enemy。
  - `SnowGolem.java:58`：`p_29932_ instanceof Enemy`。
  - `ConduitBlockEntity.java:196`：`instanceof Enemy && isAlive()`；`Shulker.java:596`：`instanceof Enemy`。
  - `Mob.java:1244`（`canBeLeashed`）：`return !this.isLeashed() && !(this instanceof Enemy);` → **Enemy 不可被拴绳**。
  - `NaturalSpawner.java:211/244`：怪物刷新的每区块上限用的是 `MobCategory.getMaxInstancesPerChunk()`（**分类**决定，不是类决定）；和平模式禁止刷怪的入口在 `SpawnPlacements` 的 check 函数（`Monster.checkMonsterSpawnRules` 内含 `difficulty != PEACEFUL`）。
  - `Monster` 类的其它硬引用：`RangedBowAttackGoal<T extends Monster & RangedAttackMob>`、`RangedCrossbowAttackGoal<T extends Monster & ...>` 泛型上界；`Rabbit` / `Panda` 有 `avoidEntityGoal(..., Monster.class, ...)`；`Fox` 的捕猎判定里出现 `instanceof Monster`。

> 注：两侧项目的怪物都注册为 `MobCategory.MONSTER`（Hall `EntityTypeRegistry.registerMob` 与 corruption 同名方法都写死 `MobCategory.MONSTER`），所以"怪物生成上限"与"和平模式不刷新"**不会因为换基类而变化**；变化只发生在"已存在的实体的运行时行为"（§4.2）。

### 1.3 本次分析未做的事

- 没跑 Gradle、没编译、没改任何源码；只写了本报告。
- 未分析方块/物品/世界生成/网络包主体的迁移（属其它报告）；只在涉及实体依赖时点到。

---

## 2. AbstractHallEntity vs AbstractCorruptionEntity 逐特性对比

### 2.1 类签名与接口

| 维度 | Hall `AbstractHallEntity` | corruption `AbstractCorruptionEntity` |
|---|---|---|
| 父类 | `net.minecraft.world.entity.monster.Monster` | `net.minecraft.world.entity.PathfinderMob` |
| 接口 | `IAutoRenderableEntity, IKeyframeHandler, IInfectedEntity, IKillCounter, ITargetFilter` | `IAutoRenderableEntity, IKeyframeHandler, ICorruptionEntity, IVariantEntity` |
| 体量 | 1,117 行 / 61 KB | 1,087 行 / 46 KB（注释少） |
| AI 架构 | **Goal 选择器**（`goalSelector`）+ 基类 `tickChase()` 统一追击；索敌用 infcore `InfectedTargetGoal` | **Brain + Goal 混用**：基类 `brainProvider/makeBrain/setupBrain`（CORE/IDLE activity + `SmartMoveBrain`）+ 各实体 `goalSelector` 里的 `SmartMoveGoal/SmartTargetGoal/RandomWanderGoal` |
| 移动 | 基类 `tickChase()` 自己 `navigation.moveTo`（10 tick 重算 + 失败指数退避 40→200） | 基类 `tick()` 只做攻击判定；移动交给 `SmartMoveGoal`（含 `JumpPointPathfinder`）与 brain 的 `MoveToTargetSink` |
| 注册期管理 | `HallEntityManager.registerAll(type, attr)` → 属性 + 自动 `GeoBaseRender` | `CorruptionEntityManager.registerAll(type, attr)` → 同上（两个类是几乎逐行相同的重复实现） |

### 2.2 同步数据（SynchedEntityData）

| Hall（15 个 accessor） | corruption（6 个 accessor） | 备注 |
|---|---|---|
| `IS_WALKING`, `HAS_WALK_ANIM_SYNCED` | 同名 | 都有 |
| `WALK_SPEED`, `RUN_SPEED` | 无（服务端字段，不同步） | Hall 为"同步 + 自动 applyMoveSpeed"设计 |
| `WALK_ANIM_SPEED`, `RUN_ANIM_SPEED`, `IDLE_ANIM_SPEED`, `DIE_ANIM_SPEED` | 无（纯字段 + setter） | Hall 支持运行期改动画速度 |
| `IS_FAKE_DYING` | 同名 | 都有 |
| `FAKE_DEATH_DURATION` | 无 | Hall 的假死时长是同步值 |
| `BURST_SCALE_DURATION`, `BURST_SCALE_RATE`, `BURST_MAX_SCALE`, `BURST_SCALE_ENABLED`, `BURST_TWITCH_AMPLITUDE` | 无 | **Hall 独有：爆体阶梯缩放**（客户端渲染用） |
| — | `KILL_COUNT`（同步 int） | **corruption 独有：击杀计数直接同步** |
| — | `DATA_ADAPTATION_COUNT`（同步 int） | **corruption 独有：适应计数**（死系统，见 2.8） |
| — | `DATA_VARIANT`（同步 String） | **corruption 独有：变种 id**（死系统，见 2.7） |

NBT：Hall 18 个键（含 Burst*、DoHurt*、AnimSpeeds）；corruption 17 个键（含 Kind、AttackSameKind、TargetingEnabled、VariantId、CorruptionAdaptationCount、CorruptionLastDamageKey）。**合并后应统一走 Hall 的 NBT 键集合**（Burst* + AnimSpeeds），corruption 的 Kind/Variant/Adaptation 键直接废弃；`CorruptionLastDamageKey/CorruptionAdaptationCount` 不迁。

### 2.3 动画状态与 GeckoLib 控制器

| 维度 | Hall | corruption |
|---|---|---|
| 默认控制器 | `movement_controller`（transition 3）+ `registerAdditionalControllers()` 扩展钩子 + `die_controller`（triggerable `die_trigger`） | `movement_controller`（transition **5**）+ `die_controller`（triggerable `die_trigger`）；**无**扩展钩子（子类要么全用默认、要么整个 `registerControllers` 重写，`defaultControllersEnabled=false`） |
| 动画状态判定 | `isActuallyMoving`（客户端带 5 tick 滞后防闪烁）；`isDeadOrDying() || isBurstAnimating()` → STOP；无 walk 动画时恒播 walk | `isActuallyMoving`（同样的滞后逻辑）；`isDeadOrDying() || isFakeDying()` → STOP；无 walk 动画时恒播 walk |
| die 控制器条件 | 只在"爆体且真的有 die 动画（`hasDieAnim`）"时接管；没有 die 动画时 idle 照播 | 只要 `isFakeDying()` 就接管（但只有 fakeDeath 触发的死亡才会进入） |
| `hasDieAnim` | 有（默认 false，需要子类 `setHasDieAnim(true)`） | 无（`startFakeDeath` 无条件 `triggerAnim("die_controller","die_trigger")`） |
| 动画速度 | 每个状态速度进同步数据 | 纯字段 |
| `isDeadOrDying` | 重写：假死期间返回 false（保命，避免原版认为已死） | 未重写 |
| 自定义控制器用法 | `Pursuer`（attack_controller）、`InfPlayer`（attack + idle_madness）、`InfEnderman`（attack/crawl/die）、`Bonecrusher`、`WindingColumn`、`Collapsar`、`HeavyBomb`、`InfSkeleton` 等 8 个实体重写/扩展 | 仅 5 个实体自定义：`LongArmBeast`（movement/attack/die + instruction keyframe handler）、`Bearer`（movement）、`SummoningPillar`（predicate/summon）、`HumusColumn`（predicate）、`InfectionBoneRuins`（predicate）、`InfectionBat`（idle_controller） |

### 2.4 假死 / 爆体（fakeDeath）

| 维度 | Hall | corruption |
|---|---|---|
| 名称语义 | "爆体"：假死 = 身体膨胀爆炸后移除 | "假死"：播 die 动画后移除 |
| 默认开关 | `fakeDeathEnabled = true`（BaseApostleEntity 关掉） | `fakeDeathEnabled = false`（各实体自行开） |
| 概率 | `fakeDeathChance = 40`（百分比；0 表示必定） | `fakeDeathChance = 0` 默认；各实体设 50 |
| 时长 | `fakeDeathDuration = 30` tick（各实体可改） | `fakeDeathDuration = 60` 默认；各实体设 45 |
| 触发点 | `die()` → `tryFakeDeath()`，只掷一次（注释明确防止双重掷骰） | `die()` → `random.nextInt(100) < fakeDeathChance` |
| 假死期间 | 血量拉满、`travel(zero)`、`isDeadOrDying()==false`、客户端收到 IS_FAKE_DYING 后自行按 gameTime 算缩放进度 | 血量拉满、`travel(zero)`；无 `isDeadOrDying` 特殊处理 |
| 结束回调 | `onBurstStart()` / `onBurstEnd()` | `onFakeDeathStart()` / `onFakeDeathEnd()` / `onRealDeath()` |
| **掉落时机** | **onBurstEnd()（爆体结束时）** `dropAllDeathLoot`，并补肉屑/爆炸/红尘/RIP_BODY 音效 | **onFakeDeathStart()（假死开始时）** `dropAllDeathLoot`；结束回调由各族覆写做尸块粒子 |
| 爆体缩放 | 有：阶梯脉冲 + SplitMix64 逐 tick 抖动，纯视觉（不改碰撞箱）；`getBoundingBoxForCulling` 同步放大剔除盒 | 无 |
| 渲染接管 | `BurstRenderMode{ANIMATION_ONLY, SCALE_ONLY, ANIMATION_AND_SCALE, NONE}` | 无 |

> **合并关键**：两边是同一个机制的两种演化。统一基类应保留 Hall 的完整假死/爆体实现，把 corruption 的 `onFakeDeathStart/End` 语义映射为 base 的 `onBurstStart/onBurstEnd` 回调；**掉落只保留一处（建议按 Hall：结束时掉）**，并把 corruption 各族的尸块粒子逻辑挂到 `onBurstEnd`。

### 2.5 粒子 / 血粒子

| 维度 | Hall | corruption |
|---|---|---|
| 受击溅血 | 基类 `hurt()` 内直接调 `onHurtBlood → BloodBurstPacket`（只写在基类，结构上保证"别的生物被打不出血"） | 无受击血；靠各族 `onFakeDeathEnd` 里 `CorruptionRipBodyUtil.xxxRip` + `CorruptionEntityParticleUtil` |
| 感染击杀血 | `onKilledEntity` 里"转化成功才喷" `BloodBurstPacket.sendBurstAt` | 无（击杀不喷） |
| 爆体结束 | `MeatBurstPacket` + `spawnExplosionParticles` + 红色 dust + `RIP_BODY` 音效 | 各族重写：`CorruptionRipBodyUtil.hostRip/degenerateRip/refactoredRip` + `CParticleSpawnPacket` + `GorePacket` + 红 dust（`BaseHostEntity` 还多 `BLOOD` 粒子/爆炸） |
| 溃烂肉屑 | `BaseUlceratedEntity`：每 40 tick 从碰撞箱撒 `ULCERATED_MEAT`，受击再撒一次 | 无 |
| 死亡粒子配置 | 无统一抽象 | `IParticleSpawnProvider.getDeathParticleConfig()`（5 个基类实现） |

> 这部分是"重复项去重"的重点：两套粒子体系（Hall 的 `BloodBurstPacket/MeatBurstPacket/EntityParticleUtils` vs corruption 的 `CParticleSpawnPacket/GorePacket/CorruptionEntityParticleUtil/CorruptionRipBodyUtil`）要合成一套。**基类只保留回调钩子**（`onHurtBlood` / `onInfectionBlood` / `onBurstEnd` + death particle config），具体粒子由合并后的粒子/网络框架统一实现（属于粒子/网络报告的范围）。

### 2.6 感染类型与击杀计数

| 维度 | Hall | corruption |
|---|---|---|
| 感染类型 | `getInfectionType() = hall:hall`（注意：`HallMod.INFECTION_TYPE = hall:inf` 是**进化表的 type**，两者路径不同但同命名空间） | 无 `IInfectedEntity`；`ICorruptionEntity` 是**空接口**（纯标记） |
| 击杀计数 | `IKillCounter` + infcore 的 `KillCountCapability`（`get/set/addKillCount` 全走 capability；NBT 由 capability 自己存） | 自己 `KILL_COUNT` 同步字段 + `awardKillScore` 自增 + NBT `KillCount` |
| 击杀 → 感染 | `onKilledEntity`（infcore 在 `LivingDeathEvent` 回调）：先 +1，再 `EvolutionManager.applyEvolution(serverLevel, victim, hall:inf)`，成功才喷血 | 无（`AssimilationMapManager` 调用被整段注释掉了） |
| 进化/转化 | 走 infcore：`data/hall/infcore_evolution/default.json`（玩家→`inf_player`、骷髅→`inf_skeleton`、巨碑→50% 破碎者/50% 重炸弹）+ `UlceratedConversionRules` 注册 3 档兜底（斥候/追猎者/巨碑，按碰撞体积 × 生命上限 >5） | 无进化数据；击杀只计数，不转化 |

> **合并关键**：击杀计数必须二选一。推荐**统一走 infcore `IKillCounter`**（capability 自动附加、自动同步、零维护），删除 corruption 的 KILL_COUNT 同步字段与 `awardKillScore` 自增；同时把腐化系的击杀转化需求接入 `EvolutionManager`（需要腐化系也实现 `IInfectedEntity` 并定义感染类型，见 §6.1）。

### 2.7 变体（variant）

corruption 有 `IVariant / IVariantEntity / DATA_VARIANT / currentVariant / variantById / selectVariantOnSpawn`，`model()/texture()/animation()` 会优先返回 variant 资源。

**但实际检查：全工程没有任何一个实体重写 `variants()`**（基类返回 `new IVariant[0]`），`selectVariantOnSpawn()` 也无人调用。唯一的"多模型"需求是 `InfectionPlayerEntity` 自己的 `MODEL_SLIM/MODEL_WIDE + 7/1 套贴图 + OnlineSkinDownloader`（不是 variant 系统）。

→ **结论：variant 是死代码，不迁**。仅保留 `InfectionPlayerEntity` 的"按皮肤模型二选一"实现（它重写 `texture()` + 直接改 `this.model`，不需要 `IVariantEntity`）。Hall 侧也有一个同样没被使用的 `EntityVariant` record（`entities/EntityVariant.java`，6 行，无引用）→ 一起删。

### 2.8 伤害适应（adaptation）

corruption 基类实现：按"伤害类型 + 武器"拼 key，换武器清空进度，命中次数超阈值后按 `0.95^(count-threshold)` 减伤，减到 0 时拦截并播 `SHIELD_BLOCK`；`getAdaptationCount()` 同步到客户端，`GeoBaseRender.getRenderColor` 里还有"适应 >10 且刚受击 → 偏蓝"的渲染。

**实际检查：24 个注册实体全部在构造器里写死 `adaptationEnabled = false`**（`LongArmBeastEntity` 甚至重写了 threshold/factor，但开关仍是 false）；基类默认也是 false。**该系统的唯一实际效果是"死亡时多写两个 NBT 键"。**

→ **结论：死系统，不迁**。合并基类不需要 adaptation 字段/同步/渲染分支。

### 2.9 kind / 阵营 / 目标过滤

| 维度 | Hall | corruption |
|---|---|---|
| 阵营概念 | **感染类型** `hall:hall`（`IInfectedEntity`）+ 威胁点数（`ThreatHelper`）；"友军"= 所有 `IInfectedEntity`（命名空间 hall） | **kind 枚举** `HOST/REFACTORED/DEGENERATE/DETERIORATED/ORIGINAL/LINKED` + `attackSameKind`（默认 false）+ `targetingEnabled` + 自定义白名单 `ConfigHelper.isModidInTargetWhitelist` |
| 索敌实现 | infcore `InfectedTargetGoal.Builder(this).range(...).mustSee(...).filter(this)`（Hall 基类自己实现 `ITargetFilter.canTarget`：玩家需威胁点 ≥ 阈值；创造/旁观忽略；非玩家一律可打） | 自己的 `SmartTargetGoal`（范围/穿墙/必须可见/可达/同 kind/其它 mod 生物/白名单/黑名单 5 秒/垂直差 ≤4/排除盔甲架·蝙蝠·爬行者/`playersOnly` 等） |
| 反索敌闸门 | `setTarget()` 重写：威胁不足的玩家直接置 null（挡住其它模组的 AI 写 target）；被玩家打后有 100 tick 还手窗口（`hurt()` 里显式锁目标） | 仅 `SmartTargetGoal` 内部约束；`HurtByTargetGoal` 没注册 |
| AoE 友伤 | `canHitWithRangeAttack()`：玩家走威胁闸门、`IInfectedEntity` 不误伤 | `CorruptionAreaAttackUtil` 的 `excludeSameKind` 选项 |
| 同类不互殴 | 由感染类型过滤天然保证（同类型不互打） | `SmartTargetGoal` 里 `kind` 相同 + `ICorruptionEntity` 一律排除 |

> **合并关键**：这是"索敌改用 infcore 阵营判断"的核心映射：
> - corruption 的 `kind` 改为**薄层/字段上的 faction**（建议 `RottenFaction.HALL / CORRUPTION`，不参与基类逻辑，只做数据/统计/特殊过滤）；
> - `SmartTargetGoal` 的语义拆成两半：**范围/视线/扫描频率** → `InfectedTargetGoal.Builder`（range/mustSee/targetOtherInfected）；**白名单/同阵营/特殊排除** → 实现 `ITargetFilter`（在 base 里实现，子类覆盖）；
> - `attackSameKind/attackOtherBaseMobs` 的语义由 `targetOtherInfected`（false = 所有 `IInfectedEntity` 都不打，与两模组现状一致）覆盖；
> - `SmartTargetGoal` 的"不可达目标拉黑 5 秒"能力 **infcore 没有**：可以接受（Hall 的 `tickChase` 有寻路失败指数退避，性能上够用），也可在 `ITargetFilter` 之外自建一个薄 `TargetBlacklistGoal` 包装（不建议 Phase 1 做）。

### 2.10 攻击系统

| 维度 | Hall | corruption |
|---|---|---|
| 冷却/距离 | `doHurtTime=20`、`doHurtDistance=3`、`chaseSpeed` | `doHurtTime=20`、`doHurtDistance=3`、`maxWalkTargetDistance=12`（后者的 getter/setter 存在但**基类逻辑未使用**） |
| 判定入口 | `tickChase()`：贴身 + 有视线 → `doHurtGoal(target)`（默认 `getAttack`），成功则进冷却 | `tick()`：`targetingEnabled && doHurtCooldown==0 && distanceToSqr<=range² && hasLineOfSight` → `doHurtGoal` → 冷却 |
| 距离口径 | **碰撞箱间隙距离**（`getBoundingBoxDistanceSqrExact`，带 5 tick 缓存） | 中心点 `distanceToSqr` |
| 出手抽象 | `protected abstract boolean getAttack(LivingEntity)`；`doHurtGoal` 可覆写 | 同（`abstract boolean getAttack`） |
| 动画同步出手 | 高级用法：`Pursuer`/`InfPlayer` 重写 `doHurtGoal` 只触发动画 + 起计时，`ATTACK_HIT_DELAY_TICKS` 后在 `tickAttack()` 里结算 + 扇形 AoE | 无（伤害在 `getAttack` 里同步结算；`LongArmBeast` 只把 attack 动画当表现，伤害在钩爪逻辑里） |
| `doHurtTarget` | 写死返回 false（原版近战路径全关） | 未重写（默认原版行为；但没人注册 MeleeAttackGoal） |
| 击杀增量 | infcore 事件 | `awardKillScore` |

> **合并关键**：统一到 Hall 的 `tickChase + doHurtGoal/getAttack + 碰撞箱距离`。corruption 现有的 `getAttack` 都是同步伤害（直接 `target.hurt(...)`），可以直接复用；想升级成"动画命中帧结算"的后续再按 `Pursuer` 模式改（每个生物一行一行的活，不是 Phase 1 必须）。

### 2.11 属性

| 维度 | Hall | corruption |
|---|---|---|
| 基类提供 | `createBaseAttributes()`：`Mob.createMobAttributes() + MAX_HEALTH 20 + MOVEMENT_SPEED 0.25 + ATTACK_DAMAGE 2 + FOLLOW_RANGE 32` | 基类**不提供**，各实体自写（`Mob.createMobAttributes()` / `Zombie.createAttributes()` / `PathfinderMob.createMobAttributes()` 混用） |
| 速度生效方式 | **基类强制**：`applyMoveSpeed()` 在构造器/`tick` 首帧/每次走跑切换时把 `MOVEMENT_SPEED` 的 baseValue 改成 `getCurrentMoveSpeed()`（= walkSpeed/runSpeed，默认 0.6/1.2） | 只读 `MOVEMENT_SPEED` 属性（0.15–0.26），移动速度靠 `SmartMoveGoal` 的 speedModifier 与属性相乘 |
| 属性表注册 | `HallEntityManager.ATTRIBUTE_BLUEPRINTS` → `EntityAttributeCreationEvent` | `CorruptionEntityManager` 同构实现（重复代码） |
| 难度缩放 | `HallDifficultyAttributes` 在实体进场时按难度挂 `MAX_HEALTH/ATTACK_DAMAGE` 修饰符，判据是 `IInfectedEntity` 且 `getInfectionType().getNamespace()=="hall"` | 无 |

> **合并关键（头号坑）**：`applyMoveSpeed` 会**覆盖** `MOVEMENT_SPEED`。corruption 生物都没设 walkSpeed/runSpeed 语义（多数设 `setRunSpeed(1.3–3.5)` 且 `hasWalkAnim=false`），如果直接换到 Hall 基类：
> - `hasWalkAnim=false` 时 Hall 的 `getCurrentMoveSpeed()` 返回 **walkSpeed**（默认 0.6），而 corruption 基类返回 **runSpeed**；
> - 结果是所有腐化生物速度变成 0.6 基准（例如 `Shocker` 从 3.5 → 0.6、`Groveler` 从 2.0 → 0.6、`Attacker` 从 2.6 → 0.6），**不报错但玩法全废**。
> 迁移映射规则必须写死：**corruption `setRunSpeed(x)` → 合并后 `setWalkSpeed(x)`（当 hasWalkAnim=false）/ 或按语义重设 walk+run**；属性表里的 `MOVEMENT_SPEED` 只当"寻路基础值"且会被覆盖，不要依赖。

### 2.12 移动 / 寻路

| 维度 | Hall | corruption |
|---|---|---|
| 导航 | 原版 `PathNavigation`（地面/飞行按实体自带）；`tickChase()` 统一 `moveTo` | `SmartMoveGoal` + `JumpPointPathfinder`（跳跃点寻路、跨沟、避液、可爬墙/跳跃参数化）+ brain `MoveToTargetSink` |
| 追击节流 | 10 tick（+随机 0–4）重算；失败 40/80/160/200 tick 指数退避；贴身就停 | `SmartTargetGoal` 自己维护"停滞 5 秒拉黑 + 导航 isDone 判断"；`SmartMoveGoal` 自己另算路径 |
| 行走/奔跑切换 | 有目标 → run，无目标 → walk；无 walk 动画时恒 walk | 有目标 → run，无目标 → walk；`maxWalkTargetDistance` 未使用 |
| 飞行 | `InfSkeleton/HeavyBomb` 用原版 `RandomFlyGoal` + `FlyingMoveControl/FlyingPathNavigation` | `InfectionBat` 用 `CorruptionFlyGoal` + 自定义 `travel()` 空气阻力飞行 |
| AI 冻结 | `aiStep` 每 20 tick 按模拟距离 `setNoAi(true/false)` | 同（逐字复制） |
| 卡墙 | 无 | `SmartMoveGoal` 的跳跃/路径修正；`Shocker` 碰撞攻击 |

> 用户已决定 corruption 的 `SmartMoveGoal/JumpPointPathfinder/brain` 不搬。后果必须记录：
> - 丢失"空中/跨沟跳跃寻路、避液、可配置跳跃高度"能力 → 大碰撞箱生物（Attacker 2.0×4.5、LongArmBeast 1.65×3.9）在复杂地形的可达性会退化（Hall 的 Pursuer 0.8×4 也有同样问题，属于既有水平）；
> - `CorruptionFlyGoal` 可用原版 `RandomFlyGoal` 顶替（蝙蝠已自带飞行 `travel()`，可保留）；
> - brain 的 `StartAttacking` 是 **`InfectionBat` 等少数实体唯一的索敌来源**（它们 `registerGoals` 里没有 target goal）→ 迁移后必须给每个 entity 显式加 `InfectedTargetGoal`，不能依赖 brain。

### 2.13 方块破坏 / 其它

| 特性 | Hall | corruption |
|---|---|---|
| 沿途破坏方块 | **无** | 有：`canBreakBlocks()` 开关 + `canDestroy(BlockState)` 判定 + `aiStep` 里 10 tick 节流、按运动方向前探、破坏后播 `ZOMBIE_BREAK_WOODEN_DOOR`；`ShockerEntity` 覆写 `canBreakBlocks()=true`；`InfectionCow` 冲锋时用 `canDestroy` 铲植被 |
| 闪电免疫 | 无 | `isInvulnerableTo(LIGHTNING_BOLT) → true` |
| 自定义 MobType | 无（`UNDEFINED`） | `CorruptionMobType.CORRUPTION_MOB_TYPE`（自定义 `MobType` 实例） |
| 注册期追踪 | `HallEntityManager`（WeakHashMap 按类型集合，供全局查询） | `CorruptionEntityManager` 同构重复 |
| 投射物基类 | `AbstractHallProjectile`：`ThrowableProjectile + IAutoRenderableProjectile`，`fly` 控制器，`onHitEntity→discard` | `AbstractCorruptionProjectile`：三构造器、`onHit` 里 discard、`fly` 控制器带 `state.isMoving()`、带无用的 adaptation 同步字段、`defineSynchedData` **漏调 super** |
| 特殊 | `getBoundingBoxForCulling` 爆体放大、`isDeadOrDying` 假死、`removeWhenFarAway=false` | `push()` 空实现（多个实体禁止推动）、`Better` 的 `devourValue` 缩放、`canBeLeashed` 未处理 |

### 2.14 差异归类矩阵

| 类别 | 明细 |
|---|---|
| **A 有 B 无**（Hall 独有） | 爆体缩放/抖动/剔除盒放大；`BurstRenderMode`；`hasDieAnim`；`isDeadOrDying` 假死重写；威胁点数 `ITargetFilter`/还手窗口/`setTarget` 闸门；`IKillCounter` capability；`IInfectedEntity` + 感染击杀血 + 进化表；`Injured` 受击血包；动画速度同步；`registerAdditionalControllers` 扩展钩子；`removeWhenFarAway` 明确 false；`doHurtTarget` 关闭原版近战；`tickChase` 节流退避 |
| **B 有 A 无**（corruption 独有） | adaptation（死）；variant（死）；`kind` 阵营枚举 + `attackSameKind` + 白名单 + targeting 开关；`blockbreak` 系统；闪电免疫；自定义 `MobType`；brain + `SmartMoveGoal/SmartTargetGoal`；`awardKillScore` 击杀自增；`onRealDeath` 回调；三构造器投射物基类 |
| **双方都有但实现不同** | 假死（默认值/时长/掉落时机/结束回调语义）；`walk/run` 速度语义（walkSpeed vs runSpeed）；攻击触发（tickChase vs tick）；控制器 transition（3 vs 5）与扩展点；击杀计数（capability vs 同步字段）；"友军"判定（感染类型 vs kind）；`stopAiAtDist`（逐字复制，`setNoAi` 时机有细微差别：Hall 无人在线时保持 AI 运行，corruption 无玩家时也会冻结）；`push()`；死亡粒子 |
| **双方同源重复（直接去重）** | `IAutoRenderableEntity`/`IAutoRenderableProjectile`/`IGeoResources`/`IGeoModelBehavior`/`IGeoLayerProvider`/`GeoBaseModel`/`GeoBaseRender`/`ClientRenderRegistry`/`MAutoRenderable` 渲染链；`IKeyframeHandler`+`PacketSyncKeyframe`+`MixinAnimationController` 三个文件（逐字相同，仅包名）；`EntityManager/HallEntityManager/CorruptionEntityManager` 与 ProjectileManager 四件套；`IAutoRenderable` 自动注册渲染器逻辑 |

---

## 3. 两侧 population 基类体系对比

### 3.1 清单

**Hall（5 个，全部是极薄层，19–81 行）**

| 基类 | 行数 | 承载内容 |
|---|---|---|
| `BaseApostleEntity` | 35 | 关近战（`doHurtDistance=0`）、关爆体、`getAttack=false`（使徒只用技能实体） |
| `BaseEcologicalEntity` | 19 | `getAttack=false` |
| `BaseInfectedEntity` | 19 | `getAttack=false` |
| `BaseUlceratedEntity extends BaseInfectedEntity` | 81 | 周期溃烂肉屑粒子（40 tick）+ 受击额外肉屑（`hurtTime` 节流） |

**corruption（6 个，是真正的"行为层"，73–361 行）**

| 基类 | 行数 | 承载内容 |
|---|---|---|
| `BaseHostEntity` | 361 | kind=HOST；B1 腐化唾液（`CorruptionDart` 连发）、B9 尸群践踏（半径内同族 ≥N 时群体加速/加攻）、B10 濒死骨爆（`BoneSpikeEntity` 弹射 N 根）、B11 血肉牵引（命中拉人）、B12 汲血回复；`getDeathParticleConfig`；`onFakeDeathEnd` 尸块 + 血粒子；`remove` 红尘；链式 `enableXxx()` |
| `BaseDegenerateEntity` | 83 | kind=DEGENERATE；周期畸变肉屑；死亡粒子；`onFakeDeathEnd` 尸块；环境音 |
| `BaseDeterioratedEntity` | 73 | kind=DETERIORATED；`getAttack` 只挂腐蚀效果（返回 false）；死亡粒子；尸块 |
| `BaseLinkedEntity` | 99 | kind=LINKED；每 5 tick 更新 `InfectionTrackerHandler` 快照（范围 = linkedLevel×15）；C9 死亡辐照领域（留 `CorruptionPoolEntity`）；`getAttack=false`；`push` 空 |
| `BaseOriginalEntity` | 79 | kind=ORIGINAL；`getAttack` 挂腐蚀；死亡粒子；尸块 + `GorePacket` |
| `BaseRefactoredEntity` | 113 | kind=REFACTORED；C9 死亡辐照领域；死亡粒子；尸块 |

### 3.2 结论

- Hall 的薄层是**分类标记 + 1 个粒子能力**；corruption 的薄层是**分类标记 + 一族通用技能/粒子/死亡表现**。
- 合并后不应"保留 5 个 + 6 个"原样两套，也不应拍平到 0 个，而是统一到一套 **family 薄层**：
  - 王庭侧：`Apostle / Ecological / Infected / Ulcerated`（4 个，保留原样即可）；
  - 腐化侧：`Host / Degenerate / Deteriorated / Linked / Original / Refactored`（6 个，删掉 kind 赋值、brain/adaptation/variant、把 `getDeathParticleConfig` 换成合并后的粒子钩子）；
  - 共 10 个薄层、每个 20–150 行，成本低、语义清晰，且是"腐化系技能包"的自然归属（B1/B9/B10/B11/B12/C9 都在这里，不用散落到 23 个叶类）。
- 若要压缩，可把 `Original` 并入 `Host`、`Refactored` 并入 `Host`（两者都是"重构/原生"少数派：1 + 4 只），薄层降到 8 个；但**不建议**把 `Host` 的 B 系列技能下放到叶类（会重复 11 次）。

---

## 4. 继承链差异：`Monster` vs `PathfinderMob` 的真实行为影响

### 4.1 原版 `Monster` 到底多做了什么（1.20.1，源码证据）

| Monster 的额外内容 | 证据 |
|---|---|
| `implements Enemy`（空标记接口） | `public abstract class Monster extends PathfinderMob implements Enemy` |
| `shouldDespawnInPeaceful() = true` | `protected boolean shouldDespawnInPeaceful() { return true; }` |
| `isPreventingPlayerRest(Player) = true` | `public boolean isPreventingPlayerRest(Player) { return true; }` |
| `getSoundSource() = HOSTILE` | `public SoundSource getSoundSource() { return SoundSource.HOSTILE; }` |
| 构造器 `xpReward = 5` | `this.f_21364_ = 5;`（`f_21364_` = `Mob.xpReward`） |
| `shouldDropLoot / shouldDropExperience = true` | 两个方法体均 `return true`（`Mob` 默认也是 true，等价） |
| `getProjectile(ItemStack)`：手持 `ProjectileWeaponItem` 时给箭 | 覆写 |
| `aiStep()`：额外 `updateSwingTime()` + `updateNoActionTime()`（光照 >0.5 时 `noActionTime += 2`，用于原版消失/闲置逻辑） | 覆写 + `m_7562_` |
| 静态 `checkMonsterSpawnRules` / `checkAnyLightMonsterSpawnRules` / `isDarkEnoughToSpawn`（含 `difficulty != PEACEFUL`） | 静态方法 |
| 默认受伤/死亡/落水音、`getWalkTargetValue`、`createMonsterAttributes`（含攻击伤害） | 若干覆写 |

### 4.2 换成 `Monster` 的逐项副作用（对"腐化系被动/中立"而言）

| 关心的问题 | 是否真的发生 | 依据 | 能否覆写/规避 |
|---|---|---|---|
| **自然消失（距离）** | **不会** | 两个基类都 `removeWhenFarAway=false`；`Mob.checkDespawn` 的距离分支被这行关掉 | 已规避 |
| **Peaceful 难度下消失** | **会**（立即 discard） | `Mob.checkDespawn`：`PEACEFUL && shouldDespawnInPeaceful()` → `discard()`；`Monster` 返回 true | 可以：子类覆写 `shouldDespawnInPeaceful()` 返回 false（基类提供一个 `peacefulDespawn` 开关） |
| **阻止玩家睡觉** | **会** | `ServerPlayer` 睡床时扫描 `Monster.class` 且 `isPreventingPlayerRest` | 可以：覆写 `isPreventingPlayerRest()` 返回 false |
| **铁傀儡 / 雪傀儡仇恨** | **会** | `instanceof Enemy`（Monster 实现 Enemy） | **不可以**（`instanceof` 无法覆写）；除非重写这些实体的索敌目标（不现实）或走方案 C |
| **潮涌核心 / 潜影贝仇恨** | 会 | 同上 `instanceof Enemy` | 同上 |
| **不可被拴绳** | 会 | `Mob.canBeLeashed`: `!isLeashed && !(instanceof Enemy)` | 不可以（同上） |
| **怪物生成上限 / 自然刷新** | **不变** | 由 `MobCategory` 决定；两侧注册都是 `MONSTER` | 不涉及 |
| **Peaceful 下不刷新** | **不变** | 由 `SpawnPlacements` 的 check 函数决定（corruption 用的是不带 PEACEFUL 判断的 `Mob::checkMobSpawnRules`，本身可能就会在和平模式刷） | 需按最终规则重写 spawn placement |
| **XP 掉落 5** | 会 | 构造器 `xpReward=5` | 覆写/设 0 |
| **音效类别 HOSTILE** | 会（字幕分类/音量分组） | `getSoundSource` | 覆写 |
| **兔子 / 熊猫会躲它；狐狸判定含 `instanceof Monster`** | 会 | `Rabbit`/`Panda` 的 avoidEntityGoal 目标类 = `Monster.class` | 不可以（类检查）——对"同化家畜"可能反而是加分（原版动物躲着它走） |
| **`RangedBowAttackGoal` 泛型上界** | 仅编译期 | `T extends Monster & RangedAttackMob`；两侧都没用它（corruption 用 `RangedAttackGoal`） | 不涉及 |
| **其它 mod 的 `instanceof Monster` 判定** | 未知 | 第三方生态 | 不可控风险，合并后如遇兼容问题再评估 |

### 4.3 事实澄清：corruption 的"同化家畜"并不是中立/被动单位

逐个核对 24 个 corruption 生物：

- 全部继承 `BaseHostEntity` 等价层，`registerGoals` 里 **都有 `SmartTargetGoal`**（= 会主动索敌玩家/其它生物）；
- `InfectionCow/Pig/Sheep/Wolf/Chicken/Bat/Villager/Player/Zombie/Skeleton` 的 `getAttack` 全部**主动造成伤害**（近战 `target.hurt(...)` 4–6.5 点；另加腐蚀效果/酸液/骨爆）；
- 注册分类全是 `MobCategory.MONSTER`；`EntityTypeRegistry` 里**没有一个是** `MobCategory.CREATURE/AMBIENT`；
- 例外是几个"功能性/站桩"单位：`InfectionBoneRuins`（runSpeed=0，喷发后自爆）、`HumusColumn`/`SummoningPillar`（runSpeed=0，塔）。

→ 所以"同化牛/猪/鸡/羊/狼/村民"是**长着被动生物模型的主动攻击怪**。把它们放进 `Monster` 根，玩法上不会变"中立变敌对"，只会改变上面那几条原版系统行为（Peaceful、睡眠、铁傀儡、拴绳）。

### 4.4 "是否拆 Hostile/Neutral 薄子层"的三条路线

| 做法 | 设计 | 代价 | 适用条件 |
|---|---|---|---|
| **N0：不拆，全 Monster + flag 覆盖** | 统一基类 `AbstractRottenEntity extends Monster`；提供 `peacefulDespawnEnabled`、`preventsRest`、`soundSource`、`xpReward` 等可覆盖项；"中立"= 子类把这几项关掉 | 最小；但 `Enemy` 标记无法消除（铁傀儡/雪傀儡/潮涌/潜影贝仍会打它、不能拴绳） | 产品接受"同化家畜也算怪物"（推荐，见 §5） |
| **N1：拆 Hostile/Neutral 两个薄子层，但根仍是 Monster** | 技术上不可行（Enemy 无法从 Monster 子类去掉） | —— | 否决 |
| **N2（方案 C）：根改 `PathfinderMob` + 敌意薄层 `implements Enemy`** | `AbstractRottenEntity extends PathfinderMob`；`AbstractRottenHostileEntity extends AbstractRottenEntity implements Enemy`（补 `shouldDespawnInPeaceful=true`、`isPreventingPlayerRest=true`、HOSTILE 音源、xp=5）；`AbstractRottenNeutralEntity extends AbstractRottenEntity`（什么都不加）；怪物睡眠阻止用 Forge `PlayerSleepInBedEvent` 补回（扫附近带"阻止睡眠"标记的敌对实体 → DENY） | 额外 ~7 个 Hall 文件（根类 + 5 个 `EntityType<? extends Monster>` 构造函数 + 可能的注册泛型）+ 1 个事件处理器；需回归 10 只 Hall 生物 | 产品要求同化家畜真正中立（Peaceful 不消失、铁傀儡不打、可拴绳） |

> **推荐 N0**（理由见 §5.4）。若产品后续改需求 → 升级 N2 的路径明确且局部：改 1 个根类 + 2 个薄层 + 5 个 Hall 实体构造器签名 + 1 个事件处理器，overworld 内容不用动。

---

## 5. 三个候选方案

### 5.1 方案 A：单一统一基类 `AbstractRottenEntity`（根 = `Monster`，推荐）

```
Monster (vanilla)
└─ AbstractRottenEntity                       ← 合并后唯一生物基类（Hall 基类为骨架）
   implements IAutoRenderableEntity, IKeyframeHandler, IInfectedEntity, IKillCounter, ITargetFilter
   ├─ 王庭 population 薄层（4）
   │   ├─ BaseApostleEntity        (Collapsar)
   │   ├─ BaseEcologicalEntity     (Bonecrusher, HeavyBomb)
   │   ├─ BaseInfectedEntity       (InfPlayer, InfSkeleton, InfEnderman)
   │   └─ BaseUlceratedEntity      (Scout, Pursuer, Monolith)
   └─ 腐化 population 薄层（6）
       ├─ BaseHostEntity           (11 只 host 生物)
       ├─ BaseDegenerateEntity     (4)
       ├─ BaseDeterioratedEntity   (Groveler)
       ├─ BaseLinkedEntity         (HumusColumn, SummoningPillar)
       ├─ BaseOriginalEntity       (OriginalBug)
       └─ BaseRefactoredEntity     (Attacker, Bearer, LongArmBeast, Shocker)
```

- **基类内容**：保留 Hall 基类全部能力（爆体/同步速度/`tickChase`/威胁过滤/感染击杀/`registerAdditionalControllers`），新增可选 `blockBreakEnabled` 子系统（从 corruption 搬，默认关）；删除 corruption 的 adaptation/variant/kind/brain/自定义 MobType/`awardKillScore`。
- **阵营**：新增 `RottenFaction { HALL, CORRUPTION }` 字段（薄层构造器里写死），基类提供 `getFaction()`；**不参与索敌的默认规则**（索敌靠感染类型 + `ITargetFilter`），只用于：难度加成资格、友军增益统计（B9/C9）、UI/调试、以后可能的跨阵营玩法。
- **改动文件数**：
  - Hall：1（基类重命名/吸收）+ 26 个引用文件的机械改名（若保留 `AbstractHallEntity` 类名则 0–1 个）；population 薄层几乎不动；**行为零变化**；
  - corruption：`population` 41 个文件（23 只生物 + 6 薄层 + 10 技能实体）+ `base` 4 + projectile 2 + api 3 = **50 个文件**进入改造范围；其中删除/合并 18（AI 9、king 2、variant 2、ICorruptionEntity 1、CorruptionMobType 1、manager 2、IParticleSpawnProvider 1），**净迁移/重写 ≈ 32 个文件**；另需迁移/重写外部底座 ≈ 28 个文件（技能 10 + 工具 7 + 注册 3 + 网络 3 + 客户端 3 + manager 2，明细见 §6.5）；
  - 新文件：合并基类 1、`RottenFaction` 1、family 薄层改造 6、合并后的 `GeoBaseRender` 1、`ClientEventHelpers` 扩展 1。
  - **合计约 60–70 个文件**（含外部底座），但 Hall 侧几乎不承担风险。
- **优点**：复用 Hall 已调好的战斗/爆体/索敌框架；单一维护点；腐化系"白嫖"王庭已有的所有基础能力；与"合并为一个 mod"的目标一致。
- **缺点/风险**：腐化"家畜型"怪物带上 `Enemy` 标记（铁傀儡仇恨、Peaceful 消失、阻止睡眠）——按 §4.3 这些对象本来就是主动攻击怪，可接受；若不可接受见 §4.4 N2。
- **工作量估计**：基类 2–3 人日；腐化 23 只生物 × 0.3–0.6 人日 ≈ 8–12 人日；技能底座 6–8 人日；渲染/动画/资源 2–3 人日；合计 **18–26 人日**（不含粒子/网络/世界系统等其他报告范围）。

### 5.2 方案 B：保留两个独立基类（只在接口/工具层共享）

```
AbstractHallEntity extends Monster            ← 保持不变
AbstractCorruptionEntity extends PathfinderMob ← 精简（删 brain/adaptation/variant/kind → ~300 行）
共享：infcore 接口（IInfectedEntity/IKillCounter/ITargetFilter）+ GeoBaseRender + IKeyframeHandler
      + FakeDeathHelper（静态工具）+ 合并后的 EntityManager
```

- **改动文件数**：Hall 1–3（抽公共工具）；corruption **约 35 个**（23 生物重写 AI + 基类精简 + manager 合并 + 删 AI 9 + 删 king 2）；合计 **约 38–40 个**（比 A 略少，因为不用碰 Hall）。
- **优点**：对 Hall 零风险；腐化保留 `PathfinderMob`（自然保持"非 Enemy"语义，无需讨论 §4）；用户"寻路 AI 不搬"的改造在腐化侧本来就无可避免，B 的改动面集中在腐化侧，便于并行/回滚。
- **缺点**：**两套基类长期双轨维护**——假死/爆体、攻击节流、速度同步、粒子钩子、难度接入、渲染缩放这些跨切面能力以后每次都要写两遍或做兼容层；两个 `EntityManager`/`ProjectileManager`/`GeoBaseRender` 继续重复；"王庭 vs 腐化"变成永久性的结构差异，而不是数据差异；后续如果想让腐化生物也吃 Hall 的难度缩放/爆体缩放，需要给两个基类各加一遍。
- **风险**：短期低、长期高（腐化基类长期游离在主线之外）；不符合"合并成一个 mod"的整合收益。
- **工作量**：约 14–20 人日（比 A 少 4–6 人日），但后续每次跨切面迭代都要 +1 倍成本。

### 5.3 方案 C：统一基类（根 = `PathfinderMob`）+ Hostile/Neutral 薄子层

```
PathfinderMob
└─ AbstractRottenEntity                     ← 全部共享逻辑（假死、动画、攻击、索敌、感染、粒子钩子）
   ├─ AbstractRottenHostileEntity implements Enemy   ← 恢复 Monster 语义
   └─ AbstractRottenNeutralEntity                    ← 非 Enemy：Peaceful 安全、可拴绳、不被傀儡仇恨
       ├─ 王庭 population 薄层（4）→ 全部挂 Hostile
       └─ 腐化 population 薄层（6）→ 按产品决定：Host 挂 Neutral 或 Hostile，其余 Hostile
```

- **需要在 Hostile 薄层补回的 Monster 语义**：`shouldDespawnInPeaceful()=true`、`getSoundSource()=HOSTILE`、`xpReward=5`、`shouldDropLoot/Experience=true`、`getProjectile` 箭（可选）；睡眠阻止用 Forge `PlayerSleepInBedEvent` 补（扫 8×5×8 内"阻止睡眠的敌对实体"→ DENY）；`Enemy` 标记本身由薄层 `implements` 提供。
- **改动文件数**：
  - Hall：基类 1 + `EntityType<? extends Monster>` 出现处 5（`AbstractHallEntity`/`BaseApostleEntity`/`BaseEcologicalEntity`/`BaseInfectedEntity`/`WindingColumnEntity`/`CollapsarEntity`）+ 注册泛型 1 + 新增事件处理器 1 + 4 个王庭薄层挂 Hostile ≈ **11–13 个文件**（全部是机械改 + 1 个新类）；`HallDifficultyAttributes` 等 6 处感染类型判定另算（modid 迁移本来就要改）。
  - corruption：同方案 A ≈ 32 个净迁移文件 + 23 只生物选层/改 AI；外部底座 28。
  - **合计约 70–80 个文件**。
- **优点**：架构最干净（单一基类 + 明确的敌意维度）；真正把"中立/被动"做成可选项（每只生物一行选层即可）；以后加"和平友善型腐化生物"零成本；`PathfinderMob` 作为根也避免了把 `Monster` 的具体实现（原版 5 种音效、箭）硬塞给所有生物。
- **缺点**：Hall 侧 10 只生物全部换根父类，需要逐项验证 §4.1 的 Monster 语义都补回（尤其睡眠、Peaceful、铁傀儡）；`Enemy` 语义靠薄层 `implements` 恢复，`instanceof Monster` 的第三方判定会变（自研/生态未知）；多 1 个事件处理器与少量回归测试。
- **风险**：中（Hall 行为回归风险可控但必须做）；工作量比 A 多 15–20%。
- **工作量**：约 22–30 人日。

### 5.4 对比评分与推荐

| 维度 | A（统一基类，Monster 根） | B（两个基类） | C（统一基类，PathfinderMob 根） |
|---|---|---|---|
| 需改文件（生物体系） | **~60–70** | ~38–40 | ~70–80 |
| Hall 现有 10 只生物影响 | **零**（基类内容不变） | 零 | 10 只换根 + 补 4 项语义（约 12 文件） |
| corruption 改写量 | 23 只 + 6 薄层（必须重写 AI） | 同左 | 同左 + 选层 |
| 能否有真正"中立"生物 | 否（Enemy 无法去除；可关 Peaceful/睡眠/音效/XP） | **是**（腐化基类天生非 Enemy） | **是**（Neutral 薄层） |
| 长期维护成本 | **低**（单点） | **高**（双套基类 + 双套 manager/renderer 复制） | 低（单点 + 1 个敌意维度） |
| 玩法回归风险 | 低（腐化生物行为按 §4.2 清单少量变化） | **最低** | 中（Hall 10 只需回归） |
| 与"合并为一个 mod"目标一致性 | **高** | 低（结构上仍是两个模组） | 高 |
| 推荐度 | **★★★★★（本轮落地推荐）** | ★★☆（仅当合并被降级为"两个模组共用代码库"时） | ★★★★（品质最优，但当前收益 < 额外风险） |

**推荐：方案 A（统一基类，根仍为 `Monster`）。**

理由（结合用户约束）：

1. 用户要的是"合并成一个 mod"，B 保留两套基类等于把分裂固化下来，后续每个跨切面能力（假死、爆体、难度缩放、粒子、索敌）都要维护两遍；而 corruption 生物**无论选哪个方案都必须重写 AI**（SmartMove/SmartTarget/brain 不搬），B 的"少改"优势只剩"不用碰 Hall"，抵不过长期成本。
2. §4.3 的证据表明腐化"家畜"本来就是主动攻击怪，把它们放进 `Monster` 不会产生"中立变敌对"的玩法倒退；§4.2 列出的副作用里，**Peaceful 消失**与**睡眠阻止**都能用一行覆写关掉（基类提供 flag），**铁傀儡仇恨/拴绳**这两项无法消除但对这些生物是合理的（铁傀儡本来就该打腐化生物）。
3. C 是更"纯"的架构，但换来的核心能力（真正中立）**当前没有真实需求**；而它要把 Hall 全部 10 只生物换根并逐项补回 `Monster` 语义，额外引入回归面。把 C 作为 A 的**已规划升级路径**（触发条件：产品确认"同化家畜必须非敌对 / 和平模式保留 / 可拴绳"），届时按 §4.4 N2 做局部重构，不影响内容层。
4. 用户其它约束在这个方案下都自然满足：AI 丢弃（§2.12）、索敌 infcore（§2.9）、KING 不搬（§6.5）、重复去重（§2.7/2.8/2.14、§7.6）。

---

## 6. corruption 实体 → `rotten_ruins` 注册表映射

### 6.1 id 与感染/阵营设计（先定，否则映射表落不了地）

| 决策点 | 建议 | 理由 |
|---|---|---|
| 命名空间 | 全部改为 `rotten_ruins` | 任务目标 |
| 注册 path | **沿用原 path**（`infection_zombie` / `degenerate_pig` …），Hall 侧沿用 `inf_player` / `scout` … | 两侧 path 无冲突（见下表）；不产生额外 lang/loot/datagen 改动；`inf_*` 与 `infection_*` 语义相近但 id 不同，正好保留为两个不同单位 |
| 感染类型（`getInfectionType()`） | **统一一个类型：`rotten_ruins:inf`**（所有 33 只生物都返回它；腐化系新实现 `IInfectedEntity`） | ① 现有 infcore 生态（`EvolutionManager`、`UlceratedConversionRules`、godsick_neo 联动）按"类型"识别朋友/敌人；② 两派同一类型 → `InfectedTargetGoal` 默认 `targetOtherInfected=false`，天然"感染生物不互殴"（与两模组现状一致，不引入阵营内战）；③ 现在 6 处 `getInfectionType().getNamespace().equals(HallMod.MODID)` 判定（`ForgeEventHelpers`、`BlackHolePhysicsHandler`、`HallDifficultyAttributes`、`WindingColumnEntity`、`CollapsarEntity`、`MeteoriteEntity`）改成按完整类型判断即可，且"腐化生物是否吃王庭难度加成/黑洞友军/陨石友军"变成一个可调判断而不是命名空间巧合 |
| 阵营 | `RottenFaction { HALL, CORRUPTION }` 字段（薄层写死），基类 `getFaction()` | 只用于数据/表现/统计，不参与默认索敌 |
| 如果以后要两派互斗 | 改成两个类型 `rotten_ruins:hall` / `rotten_ruins:corruption` + `InfectedTargetGoal(targetOtherInfected=true)` + `ITargetFilter` 放行 | 需同步改上面 6 处判定为"类型集合"判断；建议现在就写成"按类型集合"以留好开关 |

### 6.2 腐化 24 只注册生物映射表

> 新基类列：`Hostile-family` 表示挂在对应 family 薄层（均继承 `AbstractRottenEntity`）。碰撞箱取 corruption `EntityTypeRegistry` 原值。渲染来源：全部 `assets/corruption/{geo/<id>.geo.json, textures/entity/<id>.png, animations/<id>.animation.json}`，**同名搬到 `assets/rotten_ruins/` 即可**，统一用 `GeoBaseRender` 自动渲染（无需逐个写 renderer）；"动画"列列出该 `animation.json` 里实际存在的 clip。

| # | 原注册 id（corruption:） | 类 | 新薄层 | 新 id（rotten_ruins:） | 碰撞箱 w×h | HP / 基础攻击 / 甲 | 动画 clip | 需新动画 | AI 迁移要点 / 备注 |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `original_bug` | OriginalBugEntity | BaseOriginalEntity | `original_bug` | 0.6×0.8 | 8 / 11 / 3 | idle, walk | 否 | 无 fakeDeath；酸液弹（CorruptionDart）；去掉 Smart*，加 `InfectedTargetGoal + ChaseLockGoal + FaceTargetGoal + FloatGoal + RandomStrollGoal` |
| 2 | `infection_zombie` | InfectionZombieEntity | BaseHostEntity | `infection_zombie` | 0.6×1.95 | 15 / 11 / 3 | idle, walk, run, die | 否（die 已有→开 `hasDieAnim`） | fakeDeath 50%/45t；B1 唾液 6.0 + B10 骨爆 10/3.5；hasWalkAnim=true |
| 3 | `infection_player` | InfectionPlayerEntity | BaseHostEntity | `infection_player` | 0.6×1.95 | 15 / 11 / 3 | idle, walk, run, attack, die | 否（attack 已有，可选加 attack_controller） | 会换 slim/wide 模型 + 玩家皮肤下载（`OnlineSkinDownloader` + layer）；fakeDeath 50%；B10/B11/B12；`texture()`/`model()` 覆写要保留 |
| 4 | `infection_cow` | InfectionCowEntity | BaseHostEntity | `infection_cow` | 0.8×1.2 | **2** / 11 / 1 | idle, walk, run, die | 否 | B5 狂奔冲撞（含撞墙自伤/铲植被）；B9+B12；HP=2 疑似配置错误，迁移时顺手修 |
| 5 | `infection_sheep` | InfectionSheepEntity | BaseHostEntity | `infection_sheep` | 0.8×1.2 | 9 / 11 / 3 | idle, walk, die | 否 | B10 骨爆 12/3 + B9 |
| 6 | `infection_pig` | InfectionPigEntity | BaseHostEntity | `infection_pig` | 0.8×1.2 | 8 / 11 / 3 | idle, walk, long_jump, die | 否 | B9+B12+B11；`long_jump` clip 目前无控制器（可选后续加） |
| 7 | `infection_bat` | InfectionBatEntity | BaseHostEntity | `infection_bat` | 0.6×1.95 | 1 / 5 / 0 | idle（另有 fly/speed_fly 未用） | 否 | **飞行**：`FlyingPathNavigation/MoveControl` + 自定义 `travel()` 必须保留；`CorruptionFlyGoal` → 原版 `RandomFlyGoal`；原本**无 target goal**（靠 brain）→ 必须显式加 `InfectedTargetGoal`；B13 瞬移背刺 |
| 8 | `infection_skeleton` | InfectionSkeletonEntity | BaseHostEntity | `infection_skeleton` | 0.6×1.99 | 12 / 4 / 5 | idle, walk, die | 否 | `RangedAttackGoal` 保留（不需要 bow 泛型）；B6 箭雨 + B7 狂暴；`die()` 35% 生成 `infection_bone_ruins`；**类里把 kind 写成 DEGENERATE 是 bug，随 kind 一起删** |
| 9 | `infection_bone_ruins` | InfectionBoneRuinsEntity | BaseHostEntity | `infection_bone_ruins` | 0.6×1.99 | 8 / 11 / 8 | idle | 否 | 站桩自爆单位（潜伏→喷毒 3s→爆炸移除）；runSpeed=0；只有 predicate 控制器 |
| 10 | `infection_villager` | InfectionVillagerEntity | BaseHostEntity | `infection_villager` | 0.6×1.99 | 15 / 11 / 3 | idle, walk, run, die | 否 | B8 同化叠层 + B1 唾液 + B12；`applyOnHitEffects` 覆写 |
| 11 | `infection_wolf` | InfectionWolfEntity | BaseHostEntity | `infection_wolf` | 1.15×0.95 | 7 / 11 / 3 | walk, idle, run | 否 | 嚎叫 buff + B9 + B11 |
| 12 | `infection_chicken` | InfectionChickenEntity | BaseHostEntity | `infection_chicken` | 0.5×0.5 | 10 / 11 / 3 | idle, walk | 否 | B9 |
| 13 | `degenerate_pig` | DegeneratePigEntity | BaseDegenerateEntity | `degenerate_pig` | 1.35×1.35 | 15 / 11 / 6 | idle, walk, run, die | 否 | 有 `triggerAnim("movement_controller","idle")` 的非标准写法需清理 |
| 14 | `degenerate_zombie` | DegenerateZombieEntity | BaseDegenerateEntity | `degenerate_zombie` | 0.6×1.95 | 22 / 11 / 6 | idle, walk, run, die | 否 | |
| 15 | `degenerate_villager` | DegenerateVillagerEntity | BaseDegenerateEntity | `degenerate_villager` | 0.6×2.6 | 22 / 11 / 4 | animation(废) + idle, walk, run, die | 否 | 抛 `AcidBallProjectile`（需迁投射物） |
| 16 | `degenerate_cow` | DegenerateCowEntity | BaseDegenerateEntity | `degenerate_cow` | 1.2×1.6 | 28 / 11 / 4 | idle, walk, die | 否 | 有 model behavior（头部追踪） |
| 17 | `attacker` | AttackerEntity | BaseRefactoredEntity | `attacker` | 2.0×4.5 | 40 / 6 / 6 | idle, walk, run | 否 | fakeDeath 关；hasWalkAnim=true；大碰撞箱注意寻路 |
| 18 | `bearer` | BearerEntity | BaseLinkedEntity | `bearer` | 1.0×1.0 | 30 / 4 / 5 | idle, walk | 否 | 吞噬同类长大（`devourValue` 同步 + 渲染缩放）；C9 死亡领域按体型放大；`FollowGoal`（吞噬模式）需一并迁或重写 |
| 19 | `long_arm_beast` | LongArmBeastEntity | BaseRefactoredEntity | `long_arm_beast` | 1.65×3.9 | 35 / 11 / 8 | walk, die, idle, attack | 否（attack 已有） | 自定义 3 控制器 + attack/ die 的 instruction keyframe handler；C7 钩爪拖拽（依赖 `CuboidEntity`） |
| 20 | `shocker` | ShockerEntity | BaseRefactoredEntity | `shocker` | 1.5×2.8 | 38 / 4 / 9 | walk, idle, die | 否 | C6 过载脉冲；**启用 blockbreak**（合并后要保留该能力）；碰撞攻击 |
| 21 | `groveler` | GrovelerEntity | BaseDeterioratedEntity | `groveler` | 1.2×1.8 | 25 / 11 / 8 | walk, idle | 否 | C10 钻地突袭（隐身+无敌+破土斩击）；fakeDeath 关 |
| 22 | `humus_column` | HumusColumnEntity | BaseLinkedEntity | `humus_column` | 0.9×1.65 | 20 / 4 / 2 | animation(废), idle | 否 | 站桩增益/净化塔；`InfectionTrackerHandler` 每 5 tick 快照（连接种机制）；glow layer（`humus_column_grow.png`）+ `getGlowTimer` 同步 |
| 23 | `summoning_pillar` | SummoningPillarEntity | BaseLinkedEntity | `summoning_pillar` | 0.9×1.65 | 23 / 11 / 8 | idle, summon | 否（summon 已有） | C12 群体召唤（有本地人口上限）；自定义控制器 + `triggerAnim("summon","summon")`；站桩 |
| 24 | `heavy_guard` | HeavyGuardEntity | **删除** | — | 0.9×1.95 | 667 / 15 / 2 | idle, walk（无控制器，`defaultControllersEnabled=false`） | — | **空壳**：无 goals、无 target、无动画触发、无 loot、无 lang；KING 系不搬 |
| — | （未注册）`AvilaEntity` | AvilaEntity | **删除** | — | — | 667 / 15 / 2 | walk/wave/shild/idle/heavy_attck/idle_fly/control/exchange | — | 648 行王庭信使，从未注册、无 lang/loot；KING 系不搬 |

补充：corruption 有 **21 个 spawn egg**（缺 `original_bug`、`infection_bone_ruins`、`heavy_guard`），合并后需为新 id 重挂；`heavy_guard`/`avila` 的 geo/anim/texture/layer 一并删除。loot table：19 个 `data/corruption/loot_tables/entities/*.json`（无 bat/heavy_guard），可直接改命名空间复制。

### 6.3 Hall 10 只注册生物（映射一眼表）

| 原 id | 类 | 薄层 | 新 id | 碰撞箱 | 迁移动作 |
|---|---|---|---|---|---|
| `hall:inf_player` | InfPlayerEntity | BaseInfectedEntity | `rotten_ruins:inf_player` | 1.2×4 | **不改类**；仅 assets/data 命名空间迁移 |
| `hall:inf_enderman` | InfEndermanEntity | BaseInfectedEntity | `rotten_ruins:inf_enderman` | 1.2×4 | 同上 |
| `hall:inf_skeleton` | InfSkeletonEntity | BaseInfectedEntity | `rotten_ruins:inf_skeleton` | 1.2×1.2 | 同上 |
| `hall:scout` | ScoutEntity | BaseUlceratedEntity | `rotten_ruins:scout` | 1.0×1.2 | 同上 |
| `hall:pursuer` | PursuerEntity | BaseUlceratedEntity | `rotten_ruins:pursuer` | 0.8×4 | 同上 |
| `hall:monolith` | MonolithEntity | BaseUlceratedEntity | `rotten_ruins:monolith` | 2.4×2.8 | 同上 |
| `hall:bonecrusher` | BonecrusherEntity | BaseEcologicalEntity | `rotten_ruins:bonecrusher` | 5×5 | 同上 |
| `hall:heavy_bomb` | HeavyBombEntity | BaseEcologicalEntity | `rotten_ruins:heavy_bomb` | 3×3 | 同上 |
| `hall:winding_column` | WindingColumnEntity | BaseEcologicalEntity | `rotten_ruins:winding_column` | 1.4×2.8 | 同上（唯一一个在 6 处感染类型判定里被直接引用的实体） |
| `hall:collapsar` | CollapsarEntity | BaseApostleEntity | `rotten_ruins:collapsar` | 2.4×2.6 | 同上 |

> 语义重复提示（不阻塞合并，供后续内容整合决策）：`inf_player`（4 格高 boss 型）vs `infection_player`（玩家型可换肤）；`inf_skeleton`（1.2 格、技能向）vs `infection_skeleton`（0.6 格、弓手）；`inf_enderman` 无腐化对应。建议**本轮都保留**（id 不同、玩法不同），后续做"同名内容合并"另立任务。

### 6.4 非生物实体（投射物/技能实体）映射

corruption 注册的 12 个非生物实体（合并后建议全部保留，因为 23 只生物技能依赖它们）：

| 原 id | 类 | 基类 | 合并建议 |
|---|---|---|---|
| `poison_arrow` | PoisonArrowProjectile | AbstractCorruptionProjectile | 迁到 `AbstractHallProjectile` 子类；资源改名 `poison_arrow.animation.json`（**当前资源文件名是 `poison_arrow.json`，注册却写 `...animation.json`，是个现存 bug**） |
| `acid_ball` | AcidBallProjectile | AbstractCorruptionProjectile | 同上 |
| `bone_spike` | BoneSpikeEntity | AbstractCorruptionProjectile | 同上（B10 骨爆用） |
| `corruption_dart` | CorruptionDart | ThrowableProjectile 系 | 保留（B1 唾液/箭雨通用弹体，5 个生物依赖） |
| `corruption_pool` | CorruptionPoolEntity | 简单投射物 | 保留（C9 领域） |
| `cuboid` / `shield` | CuboidEntity / ShieldEntity | 简单实体 | 保留（C7 钩爪 / 护盾技能） |
| `red_dwarf_skill` / `red_dwarf_special_skill` / `red_dwarf_magic_circle` | — | 简单投射物 | 保留（红矮星弓技能；如武器不搬则一起删） |

Hall 侧技能实体（15 个）保持原样，仅命名空间迁移；合并后**统一由 Hall 的 `EntityManager/HallProjectileManager` 自动注册渲染器**，corruption 的两个 manager 删除。

### 6.5 删除项清单（明确不搬）

| 类别 | 文件/内容 | 规模 | 替代 |
|---|---|---|---|
| KING 系实体 | `HeavyGuardEntity.java`、`AvilaEntity.java` + `heavy_guard.{geo,animation}.json/png`、`avila.{geo,animation}.json`/贴图、`HeavyGuardGlowLayer` | 2 类 + 资产 | 无（用户决定不搬） |
| corruption AI | `SmartMoveGoal`、`SmartTargetGoal`、`JumpPointPathfinder`、`RandomWanderGoal`、`FollowGoal`、`CorruptionFlyGoal`、`brains/{SmartMoveBrain,AttackMoveBehavior,BrainsState}` | 9 文件 / 1,763 行 | infcore `InfectedTargetGoal` + Hall `ChaseLockGoal/FaceTargetGoal` + 原版 `FloatGoal/RandomStrollGoal/RandomLookAroundGoal/RandomFlyGoal`；`FollowGoal`（吞噬跟随）建议保留重写（~120 行，Bearer 依赖） |
| 基类内的死系统 | adaptation（字段/同步/NBT/渲染偏蓝）、variant（`IVariant/IVariantEntity/DATA_VARIANT`）、kind 枚举、brain（`brainProvider/makeBrain/setupBrain/customServerAiStep`）、`ICorruptionEntity` 空接口、`CorruptionMobType` 自定义 MobType | 并入基类删除 | 阵营 → `RottenFaction` 字段；MobType 用原版 `UNDEFINED` 或按内容设 `UNDEAD`（决策点） |
| 管理器重复 | `CorruptionEntityManager`、`CorruptionProjectileManager`、`AbstractCorruptionProjectile`（合并进 Hall 版后删） | 3 文件 | Hall 的 `HallEntityManager/HallProjectileManager/AbstractHallProjectile` |
| 界面粒子重复 | `IParticleSpawnProvider` | 1 文件 | 合并后的粒子 framework 钩子 |
| 接口/包结构 | `com.corruption.api.ICorruptionEntity/IVariant/IVariantEntity` | 3 文件 | infcore `IInfectedEntity` |

### 6.6 每只腐化生物的迁移工作量分级

| 级别 | 生物 | 理由 |
|---|---|---|
| **S（0.2–0.3 人日）** | 13–17（Degenerate 4 只 + Attacker）、1、12、11、5、6、4、2 | 结构简单：换基类/薄层 → 改构造器参数（速度语义映射）→ 重写 `registerGoals` → 覆写 `getAttack` 保留技能；无自定义控制器 |
| **M（0.4–0.6 人日）** | 3（Player，皮肤+layer）、7（Bat，飞行+无 target）、8（Skeleton，远程+箭雨+死亡生成）、10（Villager）、19（LongArmBeast，三控制器+keyframe+钩爪）、20（Shocker，脉冲+blockbreak）、18（Bearer，吞噬+缩放+C9）、21（Groveler，钻地状态机） | 有自定义渲染/动画/AI 状态机 |
| **L（0.6–1 人日）** | 9（BoneRuins，喷发→自爆流程重写）、22（HumusColumn，连接种快照+增益+glow）、23（SummoningPillar，召唤+人口上限+动画） | 站桩功能性单位，逻辑围绕 tick 状态机且强依赖粒子/效果/池实体 |
| **外部底座（6–8 人日）** | `CorruptionSkillUtil`(484)/`CorruptionSkillFx`(99)/`CorruptionDart`(382)/`CorruptionPoolEntity`(302)/`BoneSpikeEntity`(135)/`CuboidEntity`(265)/`ShieldEntity`(198)/`RedDwarf*`(3)、`CorruptionAreaAttackUtil`、`CorruptionDamageUtil`、`CorruptionEntityParticleUtil`、`CorruptionRipBodyUtil`、`ModUtils`、`OnlineSkinDownloader`、`BearerManager`、`InfectionTrackerHandler`、`BallRendUtils/IcosahedronRendUtils/LightningEffectRenderer`、`RegisterEffect/RegisterParticles/SoundEventRegistry` 增量、`CParticleSpawnPacket/GorePacket` | 没有它们 23 只生物全部编译不过 / 技能失效 |

> **给 lead 的关键提醒**：用户说"KING 系不搬、寻路 AI 不搬、粒子去重"，但**腐化生物的技能底座（`CorruptionSkillUtil/Fx` + 弹体 + 池 + 骨刺 + 范围攻击工具）不是可选项**——19/25 个生物文件直接 import 它们。要么整体搬迁这套技能底座（约 20 个文件），要么在合并前把生物技能降级为 Hall 现有战斗框架（工作量更大）。

---

## 7. 动画 / GeckoLib / 渲染资源影响

### 7.1 格式与键名兼容性（结论：直接兼容）

| 检查项 | Hall | corruption | 兼容 |
|---|---|---|---|
| geo `format_version` | 生物全部 `1.12.0`（另有 3 个 `1.21.110` 是物品模型） | 全部 `1.12.0` | ✅ |
| animation `format_version` | 全部 `1.8.0` 级别 | 全部 `1.8.0` | ✅ |
| 贴图/模型/动画路径约定 | `geo/<name>.geo.json`、`textures/entity/<name>.png`、`animations/<name>.animation.json` | **完全相同**（`EntityTypeRegistry.defaultResources` 两个模组逐字同构） | ✅ 目录整体改名到 `assets/rotten_ruins/` 即可 |
| 基础 clip key | `idle / walk / run / die`（`ANIM_IDLE/WALK/RUN/DIE` 常量） | 同名 | ✅ 直接喂给合并基类控制器 |
| 扩展 clip key | `attack / attack_left / attack_right / crawl / idle_crawl / idle_madness` | `attack / die / long_jump / fly / speed_fly / summon / animation(废)` | 各实体自定义控制器内使用，键名保持不变即可 |
| keyframe instructions | 所有 animation.json **都没有** `instructions`/`sound_effects`/`particle_effects` 字段 | 同样没有 | ✅ 迁移不涉及关键帧数据 |
| GeckoLib 版本 | build.gradle 固定 `geckolib-forge-1.20.1:4.7.1`（jarJar `[4.7.1,4.8)`） | `curse.maven:geckolib-388172:7553267`（同为 1.20.1 4.x 线，但未锁版本） | ⚠️ 合并后统一到 **4.7.1**；corruption 用到的 `renderer.reRender(...)`、`setCustomInstructionKeyframeHandler` API 在 4.7.1 存在（Hall 自己也在用 4.7.1），需编译验证 |
| `IKeyframeHandler` 通道 | `network/c2s/PacketSyncKeyframe` + `MixinAnimationController`（client→server 发 keyframe instruction） | **逐字相同**（仅包名不同） | ✅ 去重保留一份 |

> 动画键**无需任何新增资产**。唯一"要补"的是**配置**：给有 `die` clip 的腐化生物（infection_zombie/player/cow/sheep/pig/villager/wolf/skeleton、degenerate_*、shocker、long_arm_beast）在构造器里 `setHasDieAnim(true)`，这样合并基类的爆体可以像 Hall 一样播死亡动画；没有 die clip 的（bat/chicken/groveler/attacker/bearer/humus/summoning/bone_ruins/original_bug）保持 `hasDieAnim=false`，爆体只缩放。

### 7.2 逐实体动画控制器迁移清单

| 实体 | 现状 | 合并后 |
|---|---|---|
| 大部分腐化生物 | 基类默认 2 控制器（movement + die） | 直接用合并基类的默认控制器（需给有 die clip 者开 `hasDieAnim`） |
| `LongArmBeast` | 自定义 movement/attack/die 3 控制器 + instruction keyframe handler | 保留：`registerAdditionalControllers` 里加 attack 控制器；die 用基类的（triggerable `die_trigger`）；instruction handler 可保留（通道去重后仍可用） |
| `Bearer` | 自定义 movement 控制器（walk/idle 二态） | 可改用基类默认（`hasWalkAnim=false` 时基类恒播 walk；但 Bearer 的 clip 有 idle/walk 两段，建议保留自定义控制器） |
| `SummoningPillar` | 自定义 predicate + summon 控制器 + `triggerAnim("summon","summon")` | 保留（`registerAdditionalControllers` 里注册 summon 控制器） |
| `HumusColumn` | 自定义 predicate（恒播 idle） | 可简化为基类默认；glow 视觉走 render layer 不变 |
| `InfectionBoneRuins` | 自定义 predicate（恒播 idle） | 同上 |
| `InfectionBat` | 自定义 idle_controller（只播 idle，无移动切换） | 保留自定义（飞行姿态是 `travel()` 决定的，没有移动动画需求） |
| Hall 8 个自定义控制器实体 | 不变 | 不变（基类扩展点保留） |

### 7.3 渲染器 / 模型 / 贴图 / 图层迁移

| 项 | Hall | corruption | 合并动作 |
|---|---|---|---|
| 渲染器 | `GeoBaseRender`（含**爆体缩放**、`IGeoModelBehavior.preRender` 钩子、`UniversalDelegateLayer`） | `GeoBaseRender`（含 **Bearer 吞噬缩放**、**adaptation 偏蓝 `getRenderColor`**、同样的 delegate layer） | 合成一个：保 Hall 的爆体缩放 + Bearer 缩放 + 删 adaptation 分支；两个 `renderWithCustomTexture`/`reRender` helper 统一（GeckoLib 4.7.1 API） |
| 自动注册 | `HallEntityManager.registerAll` → `ClientModEventHandler.onEntityRenderersRegisterRenderers` | `CorruptionEntityManager.registerAll` → `ModClientEventHandler.onRegisterRenderers` | 保留 Hall 一套，删除 corruption manager |
| 发光层 | `GlowLayer("textures/entity/xxx_glow_layer.png")`（11 个 Hall 实体） | 无统一 glow；3 个自定义 layer：`HumusGlowLayer`（`_grow` 贴图 + `glowTimer` 渐显）、`InfectionPlayerLayer`（固定 layer 贴图）、`HeavyGuardGlowLayer`（删） | 保留 Hall `GlowLayer` 机制；把 `HumusGlowLayer`（带计时 alpha 变化，`IGeoLayerProvider` 接口两边同构可直接搬）与 `InfectionPlayerLayer` 移植成 `IGeoLayerProvider` 实现 |
| 模型行为（头部追踪） | `ClientEventHelpers.createHeadTrackingBehavior()`（已写好但 Hall 未启用） | `ModClientEventHandler` 里为 5 个实体注册：LongArmBeast、InfectionVillager、InfectionPlayer（骨骼名 `Head`，注意大小写）、DegenerateCow、InfectionSkeleton | 把 5 个注册搬到合并后的 `ClientEventHelpers.setupClientRenderLayers()` |
| 玩家皮肤 | 无 | `InfectionPlayerEntity`：`OnlineSkinDownloader` 异步取皮肤 → `texture()` 覆写 + `model()` 在 slim/wide geo 间切换 + `InfectionPlayerLayer` | 需要一并迁 `OnlineSkinDownloader`（1 文件，网络/客户端）；如果要砍，可退化到 7 张固定 slim 贴图 |
| 贴图资产 | 10 组 + 11 张 glow layer | 24 组 + `infection_player_slim_1..7/wide_1` + `infection_player_layer` + `humus_column_grow` | 全量搬 `assets/corruption/textures/entity/*` → `assets/rotten_ruins/textures/entity/` |
| 已知命名 bug | — | `poison_arrow` 动画文件名 `poison_arrow.json` ≠ 注册路径 `animations/poison_arrow.animation.json` | 迁移时改名修掉 |

### 7.4 keyframe / mixin / 网络去重

- 三个文件（`IKeyframeHandler`、`PacketSyncKeyframe`、`MixinAnimationController`）在两个仓库**逐字相同**（仅包名/refmap 名不同）→ 合并后保留一份（建议放 `org.bytechen.hall.network.c2s` 或新的 `rotten_ruins` 包），另一个删除。
- `MixinAnimationController` 的作用是拦截 GeckoLib `AnimationController` 的关键帧指令并 C2S 发给服务端 → 服务端 `onKeyframeTrigger(key)`。**当前两侧所有 animation.json 都没有 instructions，通道是空转的**；`LongArmBeast` 虽然注册了 `setCustomInstructionKeyframeHandler`，但没有指令数据所以永不触发。迁移时保留通道即可（以后加指令），不会有数据兼容问题。
- 两份 `mixins.json` 里还有大量重复的 client mixin（`GameRenderer*`、`ItemRenderer*`、`ItemInHandRenderer`、`LevelRendererEmbeddium`、`accessor.*`、`ItemGlintMixinPlugin` 等）——不是本报告范围，但**合并时必须只留一份**（否则 `@Mixin` 重复应用/插件双注册）。
- 网络通道：两侧都只有一个 `NetworkHelper`（SimpleChannel，包 id 递增）。合并后 packet id 必须重排（corruption 的 `CParticleSpawnPacket/GorePacket/PacketSyncKeyframe` 等与 Hall 包列表合并），否则旧存档/客户端不匹配——属网络报告范围，但**实体相关的 `PacketSyncKeyframe`、实体粒子包必须在同一批重排**。

---

## 8. 落地实施清单（建议按阶段）

| 阶段 | 内容 | 涉及文件 | 验收 |
|---|---|---|---|
| **P0 决策** | 定：①方案 A；②感染类型 `rotten_ruins:inf`（或双类型）；③33 只生物是否全 `Monster`；④MobType（UNDEFINED/UNDEAD/自定义）；⑤blockbreak 是否保留（仅 Shocker/InfectionCow 需要） | 文档 | 决策记录进 docs/merge |
| **P1 统一基类** | 以 Hall `AbstractHallEntity` 为骨架建 `AbstractRottenEntity`：改名/保留兼容；删 corruption 死系统；加 `RottenFaction`、blockbreak 子系统（默认关）、`getFaction()`；假死回调统一（`onBurstStart/onBurstEnd`）；杀死计数统一走 infcore；`getInfectionType()` 返回统一类型；保留 Hall 的 26 个引用可编译 | `AbstractHallEntity.java`、26 个引用（机械改名，或保留旧名做 `@Deprecated` 别名）、population 4 薄层 | Hall `gradlew build` 通过；现有 10 只生物行为回归（Peaceful/睡觉/铁傀儡/爆体/威胁索敌） |
| **P2 腐化基类与薄层** | 删 `AbstractCorruptionEntity` 的 brain/adaptation/variant/kind/manager；6 个 family 薄层迁到统一基类（删 kind、删 brain、保留 B/C 技能与粒子钩子）；迁移 `CorruptionSkillUtil/Fx` 等底座 | 6 薄层 + 32 文件 + 底座 ~20 | 编译通过；技能可运行 |
| **P3 腐化 23 只生物** | 逐只：换父类 → 构造器参数（**速度语义映射**）→ `registerGoals`（`InfectedTargetGoal` + `ChaseLockGoal` + `FaceTargetGoal` + `FloatGoal` + `RandomStrollGoal` + `RandomLookAroundGoal`）→ `getAttack`/技能保留 → 有 die clip 的 `setHasDieAnim(true)` → 删 `setupBrain/customServerAiStep` 调用 | 23 文件 | 每只：能刷新、能索敌、能攻击、死亡表现/爆体正确、动画不闪 |
| **P4 注册与资源** | 新 id 注册（24 只 + 12 非生物实体）、spawn egg、lang、loot table；assets 整体迁移（geo/textures/animations）；5 个 model behavior + 2 个 render layer + 3 个自定义控制器移植 | `EntityTypeRegistry`、`RegisterItem`、`ClientEventHelpers`、assets/data | 客户端进世界看到模型、贴图、动画、爆体 |
| **P5 收尾** | 删 corruption 9 个 AI 文件/2 个 king 文件/2 个 manager/死系统；删/KING 资产；去重 mixin、网络包 id 重排；补 spawn placement（corruption 的 `CorruptionSpawnManager` 配置驱动 + `ConfigDrivenSpawnModifier` 需迁到 `rotten_ruins`，否则合并后 33 只生物**不会自然刷新**）；`getInfectionType().getNamespace()=="hall"` 6 处判定改类型判断 | — | 全量编译 + 实机跑图 + 存档迁移验证 |

---

## 9. 风险与坑（Top 10）

1. **速度语义反转**（§2.11）：`setRunSpeed(2.0)` + `hasWalkAnim=false` 在 Hall 基类下取 `walkSpeed`（0.6）。迁移必须逐只重映射；建议在 P1 给基类加一个"兼容模式"（无 walk 动画时取 `runSpeed`，仅腐化系启用）或直接按语义重设 walk/run，防止静默降速。
2. **腐化技能底座不可选**（§6.6）：19/25 生物依赖 `CorruptionSkillUtil/Fx`、5 依赖 `CorruptionDart`、2 依赖 `CorruptionAreaAttackUtil` 等。只搬生物类毕不了业；要预留 6–8 人日。
3. **假死掉落时机冲突**（§2.4）：两侧各掉一次 → 双重掉落。统一按 Hall（`onBurstEnd` 掉），腐化侧删除 `onFakeDeathStart` 的 `dropAllDeathLoot`。
4. **击杀计数双计**（§2.6）：infcore 事件 + 腐化 `awardKillScore` 自增会 +2。只保留 infcore `IKillCounter`。
5. **modid 改名打断感染类型判定**（§6.1）：6 处 `getInfectionType().getNamespace()=="hall"`；合并后两派共用命名空间会让判定"全部命中"（腐化生物也被王庭难度加成/黑洞友军/陨石友军）。改成按完整类型或类型集合判断。
6. **Peaceful / 睡眠 / 铁傀儡**（§4.2）：如果 33 只全 Monster，"同化家畜"会 Peaceful 消失、阻止睡觉、被铁傀儡/雪傀儡仇恨、不能拴绳。前两项加覆写可关（基类提供 flag）、后两项是 `Enemy` 标记固有——若产品不可接受走 §4.4 N2。
7. **索敌能力回退**（§2.9/2.12）：`SmartTargetGoal` 的"不可达拉黑 5 秒"、"穿墙索敌"（`throughWall`）、`playersOnly`、垂直差 ≤4 在 infcore `InfectedTargetGoal` 里**没有**。`InfectedTargetGoal` 只有 range/mustSee/targetOtherInfected/filter；穿墙可通过 `mustSee(false)` 近似，黑名单需自建薄 Goal。接受或补 Goal，需在 P3 明确。
8. **`InfectionBat` 等多个实体唯一索敌在 brain**（§2.12）：删 brain 后不补 `InfectedTargetGoal` 会变成"不会还手的怪"。P3 验收必须逐只测"会不会打人"。
9. **自然刷新链路**：Hall 的 `SplendidingSpawnPlacements` 是**空实现**（注释 "Register entity spawn placements here"），corruption 用配置驱动 `CorruptionSpawnManager + ConfigDrivenSpawnModifier`。合并后若不迁/重写，`rotten_ruins` 的 33 只生物只能靠刷怪蛋/指令出现。
10. **存档兼容**：modid 改名 + 所有注册 path 换命名空间 → 老存档里的 `hall:*` / `corruption:*` 实体、物品、方块、生物群系、维度全部失效。若需要迁移，必须实现 `MissingMappingsEvent`（对 `ForgeRegistries.ENTITY_TYPES/ITEMS/BLOCKS` 等）并验证实体 id remap 是否生效；建议在 merge 方案里显式声明"是否兼容旧存档"（属 lead 决策，非本报告范围）。

次要注意：
- `CorruptionEntityManager`/`HallEntityManager` 的 `WeakHashMap` 追踪集在 `clearAll()` 后行为、注册顺序（`registerAll` 只在 DeferredRegister 回调里调用）两边一致，合并无坑。
- `AbstractCorruptionProjectile.defineSynchedData()` 漏调 `super.defineSynchedData()`（Forge 会警告）——迁移时顺手修。
- `InfectionCow` HP=2、`InfectionVillager` HP=15 等属性明显是占位值，合并时统一重调（不要机械照抄）。
- `InfectionBoneRuins` 自爆会 `remove` 自己；迁移后要确认爆体/假死状态机不会和"自爆移除"打架。
- `HumusColumn/SummoningPillar/InfectionBoneRuins` 的 `setStopAiAtDist(false)`（功能性生物不冻结）要保留——Hall 基类默认 `stopAiAtDist=true`。

---

## 10. 附录：证据文件索引

| 主题 | 文件 |
|---|---|
| Hall 基类 | `src/main/java/org/bytechen/hall/overworld/registry/entities/base/AbstractHallEntity.java` |
| Hall population | `.../entities/population/{apostle,ecological,infected,ulcerated}/Base*.java` |
| Hall 注册/渲染 | `.../registry/EntityTypeRegistry.java`、`RegisterItem.java`、`ClientModEventHandler.java`、`ClientEventHelpers.java`、`client/entity/render/GeoBaseRender.java`、`client/entity/IAutoRenderableEntity.java`、`client/entity/IGeoResources.java` |
| Hall 难度/感染判定 | `.../difficulty/HallDifficultyAttributes.java`、`HallMod.java`、`event/ForgeEventHelpers.java`、`event/impl/BlackHolePhysicsHandler.java`、`.../population/defensive/WindingColumnEntity.java`、`.../population/apostle/CollapsarEntity.java`、`.../population/skills/MeteoriteEntity.java` |
| Hall 进化数据 | `src/generated/resources/data/hall/infcore_evolution/default.json`、`.../datagen/gen/EvolutionDataProvider.java`、`.../population/ulcerated/UlceratedConversionRules.java` |
| corruption 基类 | `src/main/java/com/corruption/overworld/registry/entitis/base/AbstractCorruptionEntity.java`、`AbstractCorruptionProjectile.java`、`CorruptionEntityManager.java`、`CorruptionMobType.java` |
| corruption population | `.../entitis/population/{host,degenerate,deteriorated,linked,original,refactored,king,skill}/` |
| corruption AI | `.../entitis/ai/{SmartMoveGoal,SmartTargetGoal,JumpPointPathfinder,RandomWanderGoal,CorruptionFlyGoal,FollowGoal}.java`、`ai/brains/*` |
| corruption 注册/渲染 | `.../registry/EntityTypeRegistry.java`、`RegisterItem.java`、`client/event/ModClientEventHandler.java`、`client/entity/render/GeoBaseRender.java`、`client/entity/layer/{HumusGlowLayer,InfectionPlayerLayer,HeavyGuardGlowLayer}.java` |
| infcore API | `libs/org/bytechen/infcore/0.1.2/infcore-0.1.2-sources.jar` 中 `api/IInfectedEntity、IKillCounter、goal/InfectedTargetGoal、ITargetFilter、core/capability/*、core/evolution/EvolutionManager` |
| 原版行为 | gradle 缓存 `forge_gradle/minecraft_user_repo/mcp/1.20.1-.../joined/decompile/output.jar`（`Monster/Mob/ServerPlayer/IronGolem/SnowGolem/ConduitBlockEntity/Shulker/NaturalSpawner`）+ `srg_to_official_1.20.1.tsrg` |
| 资产 | `assets/hall/{geo,animations,textures/entity}`、`assets/corruption/{geo,animations,textures/entity}`（format_version 与 clip 名统计见 §7.1） |
