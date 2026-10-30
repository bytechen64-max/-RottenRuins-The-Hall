# corruption 技能迁移计划（rotten_ruins · 逐步补齐阶段）

> 性质：**只读分析 + 规划文档**。本任务未修改任何 Java、未跑 Gradle。
> 基线时间：2026-10-29 12:46（+08:00）。工作区**含并行任务的未提交产物**（`corruption/skill/`、`utils/combat/CorruptionDamageUtil.java`、技能实体资源、`docs/merge/corruption-skill-assets.md`），本计划以「当前工作区实际状态」为准。
>
> 路径约定：
> - `C/` = `C:\Users\chy20\Desktop\desktop\Project\corruption\corruption`（源工程，只读参考）
> - `H/` = `C:\Users\chy20\Desktop\desktop\Project\RottenRuinsSplendiding`（合并工程）
> - `I/` = `C:\Users\chy20\Desktop\desktop\Project\InfectionCoreFramework`（infcore）
> - 生成物根：`H/src/main/resources/assets/rotten_ruins/`、`H/src/generated/resources/`（data 生成物）

---

## 0. 结论摘要

### 0.1 现状矩阵（哪些已经就位）

| 能力 | 状态 | 位置 / 说明 |
|---|---|---|
| 技能工具库 `CorruptionSkillUtil`（492 行，已适配目标包） | ✅ 已完成（并行任务，未提交） | `H/src/main/java/org/bytechen/rottenruins/overworld/registry/entities/population/corruption/skill/CorruptionSkillUtil.java`；阵营判断已改 `RottenFaction`/`IInfectedEntity`；**`spawnPoolAt` 仍是 TODO 空实现**、`stackEffect` 语义被改成「取最大值」 |
| `CorruptionSkillFx`（102 行，纯原版粒子） | ✅ 已完成（并行任务，未提交） | 同目录 `CorruptionSkillFx.java`；`impact/shockwaveRing/pillar/breath/bloodMist/emit` |
| `CorruptionDamageUtil`（247 行） | ✅ 已完成（并行任务，未提交） | `H/.../utils/combat/CorruptionDamageUtil.java`；`TrueHurt` 1/2/3/4+ 级 |
| 技能实体资源（3 个 Geo 弹射物） | ✅ 已完成（并行任务，未提交） | `geo/{acid_ball,bone_spike,poison_arrow}.geo.json`、同名 `animations/*.animation.json`、`textures/entity/{bone_spike,poison_arrow}.png`；`acid_ball` 复用 `textures/item/acid_ball.png` |
| 6 个腐化效果 | ✅ 已注册（占位实现） | `H/.../registry/corruption/CorruptionEffects.java`；仅 `corruption`/`bleeding` 有周期真伤，`lysis/miasma/sclerosis/fear` 无逻辑（见 §1.2、§6 P0-2） |
| 阵营/索敌基础设施 | ✅ 已就位 | `RottenFaction`、infcore `IInfectedEntity`/`InfectedTargetGoal`、`ChaseLockGoal`、`FaceTargetGoal` |
| 7 个技能实体类（PoisonArrow/AcidBall/BoneSpike/CorruptionDart/CorruptionPool/Cuboid/Shield） | ❌ 未移植 | 代码在 `C/.../entitis/population/skill/`、`C/.../projectile/` |
| `SkillProjectileRenderer`（空渲染器）/ `CuboidRenderer` | ❌ 未移植 | `C/.../client/entity/render/SkillProjectileRenderer.java`、`C/.../client/rend/CuboidRenderer.java` |
| 技能实体注册（EntityType/刷怪蛋/语言） | ❌ 未做 | `CorruptionEntityTypes` 只有 23 只生物；无 `corruption_dart`/`corruption_pool` 等 |
| 腐化生物技能（B/C 系列） | ❌ 未做 | 23 只生物目前只有基类索敌/追击/近战 |
| 自定义粒子（6 个）/ 自定义音效（6~9 个） | ❌ 未做 | 技能本身不依赖它们（见 §4），但 B 系死亡表现/环境音需要 |
| `CorruptionAreaAttackUtil`（23KB，Shocker/LongArm 专用） | ❌ 未移植（**建议不整搬**） | 只有 LongArmBeast、Shocker 两个类引用；建议用 `RangeAttackUtils` + 少量自写替代（§1.6） |
| 关键帧 C2S 通道 | ✅ 通道已注册但**空转** | `PacketSyncKeyframe`/`IKeyframeHandler` 已注册；所有 animation.json 无 `instructions`，`onKeyframeTrigger` 永不触发（见 §6 P0-3） |

### 0.2 三个必须先处理的前置风险

1. **P0-1 索敌闸门（阻塞全部技能演示）**：23 只腐化生物继承 `AbstractRottenEntity` 的**王庭威胁值索敌过滤**——`canTarget`（第 163 行）与 `setTarget`（第 178 行）规定：玩家威胁点数 < `ThreatHelper.TARGET_THREAT_THRESHOLD`（=5）时**不会**被主动索敌，且 `setTarget` 会直接把该玩家置空。威胁点数只在「非王庭生物击杀王庭生物」时增加（`ForgeEventHelpers` 第 195-197 行），腐化生物不触发。结果：**默认状态下腐化生物对玩家完全中立，所有需要目标的技能都无法触发/无法验收**。必须在 `BaseCorruptionEntity` 里覆写索敌策略（腐化阵营对玩家无威胁门槛；创造/旁观仍排除），否则本阶段无法验收。
2. **P0-2 效果占位语义缺失**：技能大量依赖 `fear`（控制）、`lysis`（增伤）、`sclerosis`（减伤）、`miasma`（雾/减疗）、`corruption`（原死亡转化，现周期真伤）、`bleeding`（原递增流血，现固定 1 点）。此外：
   - `fear` 原版依赖 `NonRemovable` + `invulnerableTime=0`，而**合并工程 mixin 清单里没有 `MixinLivingEntity`/`AccessorLivingEntity`**（`rotten_ruins.mixins.json` 只有 `MixinAnimationController` + 客户端项）→ 需要把 corruption 的 `MixinLivingEntity`（拦截 `removeEffect/removeAllEffects`）重新移植并注册；
   - `sclerosis` 当前注册为 `BENEFICIAL` 且无逻辑（原为 HARMFUL 的「单次伤害上限」），与 HumusColumn 把它当正面 buff 用的写法冲突，需要定分类；
   - `corruption` 原版是「死亡时转化」，当前是周期真伤，会让注射器/同化玩法的语义改变。
   不先补效果逻辑，技能只有伤害数字、没有控制/DoT 体感。**建议在批次 1 之前补 4 个占位效果（估 1~2 人日，含 1 个 mixin），或先明确「本阶段只做表现+伤害，效果占位接受」**。
3. **P0-3 基类钩子与动画语义**：
   - `AbstractRottenEntity` 无 `onRealDeath`/`onFakeDeathStart` 钩子，fake death 由基类 `tryFakeDeath→startFakeDeath→onBurstEnd` 接管（`onBurstEnd` 已内置爆体粒子+RIP_BODY+**死亡掉落**）。腐化原有 `onFakeDeathStart/onRealDeath` 需改为覆写 `die()`/`onBurstEnd()`，且**不能再调 `dropAllDeathLoot`**（否则双倍掉落）。
   - `tickChase()` 每 ~10 tick 会 `navigation.moveTo(target, chaseSpeed)`，技能的 `setDeltaMovement`/`teleportTo` 位移会被寻路覆盖 → 技能状态机需在技能期间 `getNavigation().stop()` 并屏蔽/绕过 `tickChase`（或加 `setChaseSpeed(0)`）。
   - 关键帧攻击（LongArm `attack_controller`）因动画无 instructions 而**永不结算伤害**，迁移时必须改成「直接伤害/计时触发」。
   - 7 只生物当前 `setHasWalkAnim(true)` 但动画没有 `run` clip（bearer、degenerate_cow、groveler、skeleton、long_arm_beast、shocker、original_bug），bat 连 `walk` 都没有 → 移动时会播不存在的动画（日志 `Unable to find animation`）并可能 T-pose。修复清单见 §3。

### 0.3 建议节奏与工作量

| 轮次 | 内容 | 预估人日 |
|---|---|---|
| Round 0 | P0-1 索敌策略 + 基类小补（`setStopAiAtDist` 可写/技能位移暂停辅助）+ 效果占位补齐（可选）+ 7 个技能实体 + 渲染器 + 注册 + `spawnPoolAt`/`stackEffect` + 语言/音效（最小集） | 4~6 |
| Round 1 | degenerate 4 只 + groveler（C 系列） | 2~3 |
| Round 2 | refactored 4 只（attacker/bearer/longarm/shocker） | 3~4 |
| Round 3 | host 11 只（先做 BaseHostEntity 共用 B 系，再逐只特殊） | 4~6 |
| Round 4 | linked 2 只 + original 1 只（含 C9/C12/C13） | 2~3 |
| 合计 | | **15~22 人日** |

> 若只做「简化技能版」（每个生物 1 个代表技能、不追求全数值还原），可压到 8~12 人日；本计划按「还原原工程技能」估算。

---

## 1. 技能基础设施依赖图

### 1.1 `CorruptionSkillUtil`（已移植、需补 2 处）

已适配路径：`H/.../corruption/skill/CorruptionSkillUtil.java`（492 行）。

```
CorruptionSkillUtil
├── 粒子先兆/表现      chargeIn / burst / warningRing / telegraphGround / chargeSmoke / redDust
├── 音效               playSound / playSoundPitched                     → 原版 SoundEvent 即可
├── 目标筛选           isValidTarget / findTargets / findTargetsInSector /
│                      findTargetsAlong / nearestTarget
│                      └─ 依赖 RottenFaction + IInfectedEntity（已适配：排除本方两阵营）
├── 伤害结算           strike / areaDamage / areaTrueDamage
│                      └─ 依赖 CorruptionDamageUtil（✅ 已移植）
├── 位移               pull / lungeAt / faceTarget
├── 效果               applyEffect / stackEffect / buffAllies / purifyAllies
│                      └─ 依赖 CorruptionEffects（✅ 已注册；⚠ 效果行为占位）
│                      └─ corrupt/bleed/fear/lysis/slow/wither/poison/nausea/hunger 快捷入口
├── 吸血               lifesteal
├── 腐蚀池             spawnPoolAt                                       ❌ TODO 空实现
│                      └─ 需 CorruptionPoolEntity + 实体注册（§2）
└── 查询               countAllies / isCorruptionAlly / healthRatio
```

**待补/待改：**

1. `spawnPoolAt(...)`（第 492-496 行）当前是空方法；移植 `CorruptionPoolEntity` 后按原实现接回（创建实体→setOwner/setRadius/setLifetime/setDamagePerInterval/setDamageInterval/setPos→`level.addFreshEntity`）。
2. `stackEffect(...)`（第 367-377 行）被改成「时长/等级取 max」，而原 corruption 的 `ModUtils.addOrStackEffect` 是**叠加**（时长相加、等级相加）。依赖叠加语义的技能：
   - `InfectionVillagerEntity` B8 同化传播（每次命中叠 1 级，4 层触发失控）；
   - `HumusColumnEntity` 对盟友/敌人的 buff/debuff 叠加。
   建议恢复叠加语义：`duration = existing.duration + duration`、`amplifier = existing.amplifier + amplifier`（与目标合 `EntityBuffUtils.addOrStackEffect(entity, effect, level, durationTicks)` 的语义对齐，注意参数顺序不同）。
3. `isValidTarget`（第 199-205 行）已排除「本模组两阵营的感染者」；与原版「只排除腐化阵营」不同。这是合理的防友伤策略（两派当前默认互不攻击），但意味着**腐化技能打不到王庭生物**——若后续要做阵营战，需要改成按 faction 参数化（建议加一个 `boolean hitHallFaction` 或 filter 重载）。
4. `playSoundPitched` 用 `SoundSource.HOSTILE` 广播，无需新增包。
5. `chargeIn/burst/...` 全部服务端 `sendParticles`（原版 `DustParticleOptions`/`LARGE_SMOKE`/`DAMAGE_INDICATOR`），**零自定义粒子/贴图依赖**。

### 1.2 `CorruptionSkillFx`（已移植、无依赖缺口）

`H/.../corruption/skill/CorruptionSkillFx.java`（102 行）。全部服务端原版粒子（dust、`LARGE_SMOKE`、`DAMAGE_INDICATOR`）：
`impact / shockwaveRing / pillar / breath / bloodMist / emit`。**无需新增粒子注册**。

### 1.3 `CorruptionDamageUtil`（已移植）

`H/.../utils/combat/CorruptionDamageUtil.java`（247 行，纯原版 API）。
`TrueHurt(attacker, target, ignoreInvulDamage, commonDamage, level, hurtTick)`：
- level 1 = 物理（可格挡/图腾）；level 2 = 虚空（绕过盾牌，直接扣血）；level 3 = `setHealth`；level 4+ 同 3。
- 被 `SkillUtil.strike/areaTrueDamage`、`CorruptionDart`、`CorruptionPoolEntity`、`BoneSpikeEntity` 使用。
**结论：无缺口，不再改动。**

### 1.4 `AbstractCorruptionProjectile` → 用 `AbstractHallProjectile` 替代

| 项 | corruption 原版（`C/.../entitis/base/AbstractCorruptionProjectile.java`，130 行） | 合并工程等价物（`H/.../entities/base/AbstractHallProjectile.java`，49 行） | 迁移动作 |
|---|---|---|---|
| 父类 | `ThrowableProjectile` | 同 | ✅ |
| 渲染接口 | `IAutoRenderableProjectile` + GeckoLib 资源字段 | 同（含 `setModel/setTexture/setAnimation`） | ✅ 直接复用 |
| 构造器 | 3 个（type+level / xyz / shooter） | 1 个（type+level） | 3 个技能投射物按需保留自己的构造器（`BoneSpikeEntity.launch` 自建） |
| `onHit` | 覆写 `onHit()` → `super.onHit()` 后 `discard()` | 覆写 `onHitEntity()`：命中活体→`onHitLivingEntity`→`discard()` | ✅ merged 版更干净；`BoneSpike` 的 `onHitBlock` 无需特殊处理（默认空） |
| `onHitBlock` | 默认空 | 默认空 | ✅ |
| 动画控制器 | `fly`（带 `state.isMoving()` 判断） | `fly`（恒播） | 用 merged 版；如需 moving 判断可选 |
| 适应计数 / `defineSynchedData` | 有（未调 `super`）；`DATA_ADAPTATION_COUNT` 无实际用途 | 无 | **丢弃**（死代码，迁移时不要再带） |
| 同步/存档 | `addAdditionalSaveData`（AdaptCount） | 无 | 丢弃 |

**结论**：3 个 Geo 投射物（PoisonArrow/AcidBall/BoneSpike）直接 `extends AbstractHallProjectile`，保留各自 `onHitLivingEntity`、额外 tick 逻辑、构造器；把 `corruption` 的 `ICorruptionEntity` 阵营判断改成 `RottenFaction`/`IInfectedEntity`；注册时用 `EntityTypeRegistry.registerProjectile`（内部 `HallProjectileManager` → 自动 `GeoProjectileRenderer`），工厂里显式 `setModel/setTexture/setAnimation`。

### 1.5 需要新增/移植的代码清单（Round 0）

| # | 目标文件（H/ 相对路径） | 来源（C/ 相对路径） | 行数/体量 | 动作 |
|---|---|---|---|---|
| 1 | `.../population/corruption/skill/BoneSpikeEntity.java` | `.../entitis/population/skill/BoneSpikeEntity.java` | 149 行 | 改 `extends AbstractHallProjectile`；`launch()` 用新 `EntityTypeRegistry`/`CorruptionEntityTypes.BONE_SPIKE`；`CorruptionSkillUtil` 路径改目标包；删 adaptation |
| 2 | `.../skill/AcidBallProjectile.java` | `.../registry/projectile/AcidBallProjectile.java` | 60 行 | 同上；`implements IBillboardRenderable`（目标已有接口）；`FEAR` 效果引用改 `CorruptionEffects` |
| 3 | `.../skill/PoisonArrowProjectile.java` | `.../registry/projectile/PoisonArrowProjectile.java` | 48 行 | 同上；`CORRUPTION` 效果引用改 `CorruptionEffects` |
| 4 | `.../skill/CorruptionDart.java` | `.../skill/CorruptionDart.java` | 392 行 | 保留 `ThrowableProjectile`；`type()` 改 `CorruptionEntityTypes.CORRUPTION_DART`；`spawnPoolAt` 走 SkillUtil；阵营判断改 `IInfectedEntity`/`RottenFaction`；**补 `defineSynchedData` 的 `super` 调用**（原版漏调） |
| 5 | `.../skill/CorruptionPoolEntity.java` | `.../skill/CorruptionPoolEntity.java` | 307 行 | 保留 `Entity`；效果引用改 `CorruptionEffects`；`TrueHurt` 路径改 `utils.combat`；客户端粒子保持原版 dust/smoke；接回 `SkillUtil.spawnPoolAt` |
| 6 | `.../skill/CuboidEntity.java` | `.../skill/CuboidEntity.java` | 265 行 | 原样移植（零资源、零阵营依赖） |
| 7 | ~~`SkillProjectileRenderer.java`~~ | `.../client/entity/render/SkillProjectileRenderer.java` | 26 行 | **不用移植**：目标 `ClientModEventHandler` 已有等价的私有 `registerNoopRenderer`（第 138 行，匿名 `EntityRenderer` + `DUMMY_TEXTURE`），Dart/Pool 直接复用即可（需把它从 private 改成包内可见，或复制 6 行） |
| 8 | `.../client/rend/CuboidRenderer.java` | `.../client/rend/CuboidRenderer.java` | 约 200 行 | 原样（纯自绘 `POSITION_COLOR` 立方体；无 shader/贴图依赖） |
| 9 | `.../registry/corruption/CorruptionEntityTypes.java`（改） | `C/.../registry/EntityTypeRegistry.java` 的注册段 | +7 个 `RegistryObject` | 加 `registerProjectile`（Geo 弹射物）/`registerSimpleProjectile`（Dart/Pool 用 noop 渲染）/`registerNoopEntity` 调用；具体见 §2.3 |
| 10 | `.../event/ClientModEventHandler.java`（改） | `C/.../event/ModClientEventHandler.java` | +10 行 | 为 Dart/Pool 注册 `SkillProjectileRenderer`、为 Cuboid 注册 `CuboidRenderer`（用现有 `registerNoopRenderer` 或 `event.registerEntityRenderer`） |
| 11 | `.../skill/CorruptionSkillUtil.java`（改） | — | 2 处 | 接 `spawnPoolAt`；修 `stackEffect` 叠加语义（或确认接受取 max） |
| 12 | 语言/音效（可选，Round 0 最小集） | 见 §4 | — | 技能实体名 + attacker/degenerate/overload 音效（若同批做） |

> `ShieldEntity` + `ShieldRenderer` + `IcosahedronRendUtils` **不移植**：全工程仅 `AvilaEntity`（KING 系，不搬）使用；`RedDwarf*` 已按决策丢弃。`CorruptionAreaAttackUtil`（23KB）**不整搬**：仅 LongArmBeast/Shocker 用到 `executeCustomDirtBreakAttack`（扇形 120°/180°、破坏方块、伤害衰减、`setHealthDamage`），建议用目标工程 `utils/combat/RangeAttackUtils` 的 sector/cone 查询 + 自写 30~50 行「破坏前方软方块」替代；若坚持还原再单独评估（+2 人日）。

### 1.6 基类需要的小改动（技能地基，建议 Round 0 一并做）

| 需求 | 现状 | 建议 |
|---|---|---|
| 腐化阵营对玩家无威胁门槛 | `AbstractRottenEntity.canTarget/setTarget/canAttackPlayer` 全部按王庭威胁值（§0.2 P0-1） | 在 `BaseCorruptionEntity` 覆写 `canTarget`（玩家只排除创造/旁观）、`canAttackPlayer`（常 true）、`setTarget`（不拦截玩家）；**最优解是把策略收敛为 Overridable 方法，避免每只生物重复** |
| 功能型站桩生物不冻结 AI | 基类只有 `protected stopAiAtDist=true`，无 setter | 加 `public void setStopAiAtDist(boolean)`（1 行），供 humus/summoning/bone_ruins 调 false |
| 技能位移不被寻路覆盖 | `tickChase()` 每 10 tick `navigation.moveTo` | 技能状态机进入时 `getNavigation().stop()`；或在基类加 `protected boolean suppressChase` 由技能临时置位 |
| 死亡钩子 | `die()`/`onBurstEnd()` 已存在，无 `onRealDeath` | 技能需要「非爆体真实死亡」时覆写 `die()` 并判 `!isFakeDying()`；B10/C9 用 `onBurstStart/onBurstEnd` 即可（基类 `onBurstEnd` 已负责粒子+音效+掉落） |
| 关键帧 | 通道在，但 animation 无 instructions | 本阶段不依赖；LongArm 攻击改「触发动画 + 计时/直接伤害」 |

---

## 2. 技能实体清单（7 个，Shield/RedDwarf 不列）

> 原版体量以 corruption 源文件行数计；「渲染方式」指合并工程应采用的方案；「使用者」来自全工程引用扫描。

| # | 注册 id | 类 / 行数 | 用途 | 渲染方式（合并工程） | 资源依赖 | 注册方式 | 依赖工具 | 使用者 |
|---|---|---|---|---|---|---|---|---|
| 1 | `poison_arrow` | `PoisonArrowProjectile` / 48 | 同化骷髅的毒箭；命中 6 伤害 + `corruption` 100/1（仅对非腐化生物） | **Geo 模型**：`GeoProjectileRenderer` | ✅ 已备：geo/anim/texture（anim 为空表，无骨骼动画） | `registerProjectile`（`IAutoRenderableProjectile` → `HallProjectileManager` 自动注册 Geo 渲染器） | `CorruptionEffects` | `corruption_skeleton`（`RangedAttackGoal`） |
| 2 | `acid_ball` | `AcidBallProjectile` / 60 | 腐化村民旧版近程酸球；命中 5 伤害 + `fear` 40/0；`IBillboardRenderable` 始终面向相机 | **Geo + billboard** | ✅ 已备：geo/anim + `textures/item/acid_ball.png` | `registerProjectile` | `CorruptionEffects`、`EntityBuffUtils`/SkillUtil | `degenerate_villager`（每 15 tick） |
| 3 | `bone_spike` | `BoneSpikeEntity` / 149 | B10 濒死骨爆弹射物：锥形骨刺，命中 `TrueHurt` level1、最长 30~40 tick；`canHitEntity` 排除 owner | **Geo 模型**（`fly` 动画含碎骨摆动） | ✅ 已备：geo/anim/texture | `registerProjectile` | `CorruptionDamageUtil`、`SkillUtil.isValidTarget`/`SkillFx.impact` | `BaseHostEntity.triggerBoneBurst`（host 族 B10：zombie/player/sheep/pig/bat/villager 等所有 `enableBoneBurst` 者） |
| 4 | `corruption_dart` | `CorruptionDart` / 392 | 通用腐蚀弹体：吐息/箭雨/喷射/酸弹共用一个实体；颜色/伤害/重力/命中效果/腐蚀池运行时配置；命中 `TrueHurt` level1；可穿透（`pierce`） | **空渲染器 + 客户端粒子**（每 tick 3 颗 `DustParticleOptions` + 概率 `LARGE_SMOKE`；颜色来自同步字段） | 无 | `registerNoopEntity` + 复用 `ClientModEventHandler.registerNoopRenderer` 手动注册 | `CorruptionDamageUtil`、`CorruptionEffects`、`SkillUtil.spawnPoolAt`（pool 联动） | `BaseHostEntity` B1 吐息、`degenerate_cow` C5、`degenerate_villager` C1、`corruption_skeleton` B6、`original_bug` C11（仅取色） |
| 5 | `corruption_pool` | `CorruptionPoolEntity` / 307 | 地面腐蚀液池：周期 `TrueHurt` level1 + `corruption` 60/0；半径/持续/伤害/间隔全同步 | **空渲染器 + 客户端粒子**（`POOL_DUST`/`POOL_DUST_DARK` + `LARGE_SMOKE`；服务端每 5 tick 广播一次深色 dust） | 无 | 同上（复用 `registerNoopRenderer`） | `CorruptionDamageUtil`、`CorruptionEffects` | `SkillUtil.spawnPoolAt` 的所有调用方（Cow C5、Villager C1、Skeleton B6、C9 死亡领域等） |
| 6 | `cuboid` | `CuboidEntity` / 265 | 通用方块判定/视觉实体：定向缓动 `moveTo(target,duration)` / 抛射 `launch`，逐 tick 命中回调 `setOnHit(BiConsumer)`；`getBoundingBoxForCulling` | **自绘渲染器** `CuboidRenderer`（`POSITION_COLOR` 半透明立方体，尺寸来自同步字段 `BOX_W/H/D`，无贴图/模型/动画） | 无 | `registerNoopEntity` + `CuboidRenderer` 手动注册；建议 `clientTrackingRange` 调大（120 格内可见） | 无（回调由使用者写） | `long_arm_beast` C7 钩爪、`degenerate_villager` C2 血肉墙（Avila 已弃） |
| 7 | ~~`shield`~~ | `ShieldEntity` / 198 | 二十面体护盾（跟随主人/伤害转治疗） | 自绘 `ShieldRenderer` + `IcosahedronRendUtils` | 无 | — | — | **仅 Avila（KING 不搬）→ 不移植** |

### 2.1 注册与接线细节（Round 0 必做）

1. **Geo 弹射物**（poison_arrow / acid_ball / bone_spike）：在 `CorruptionEntityTypes` 增加与 `EntityTypeRegistry.registerProjectile` 同构的 helper；`EntityType.Builder.of(factory, MISC).sized(...).build(name)`；工厂内显式注入资源：
   - `poison_arrow`：`geo/poison_arrow.geo.json`、`textures/entity/poison_arrow.png`、`animations/poison_arrow.animation.json`
   - `acid_ball`：`geo/acid_ball.geo.json`、`textures/item/acid_ball.png`、`animations/acid_ball.animation.json`
   - `bone_spike`：`geo/bone_spike.geo.json`、`textures/entity/bone_spike.png`、`animations/bone_spike.animation.json`
2. **Dart/Pool**：`registerNoopEntity("corruption_dart", 0.3f, 0.3f, ...)`、`registerNoopEntity("corruption_pool", 3.0f, 0.4f, ...)`（MISC）；在 `ClientModEventHandler.onEntityRenderersRegisterRenderers` 里复用它已有的私有 `registerNoopRenderer`（`ClientModEventHandler` 第 138-143 行，匿名 `EntityRenderer` + `DUMMY_TEXTURE`）注册这两个类型，**不需要新增 `SkillProjectileRenderer` 类**。
3. **Cuboid**：`registerNoopEntity("cuboid", 1f, 1f, CuboidEntity::new)`；`event.registerEntityRenderer(CUBOID.get(), CuboidRenderer::new)`。
4. **不要**用 `EntityManager.registerSkill`（它走 `GeoEntityRender`，只适用于 `IAutoRenderableEntity`）。
5. 语言键：`entity.rotten_ruins.{poison_arrow,acid_ball,bone_spike,corruption_dart,corruption_pool,cuboid}`（默认 en/zh 各 6 条）；如保留 Shield 再加 1 条。
6. 刷怪蛋：技能实体**不需要**刷怪蛋（原工程也没有）。
7. 网络：技能实体自身状态全部走 `SynchedEntityData`/NBT（Dart 的颜色/半径/重力、Pool 的半径/寿命/伤害、Cuboid 的尺寸/owner），**不需要新增网络包**；只有 Humus/Shocker 的「闪电可视化」走既有 `EffectSyncPacket`（`NetworkHelper.sendLightningToAll`）即可。

---

## 3. 按家族 4 批迁移清单

> 通用说明：
> - 所有生物的技能 tick 必须 `if (level().isClientSide || !isAlive()) return;`（原版如此），服务端结算、客户端只靠粒子/同步。
> - 原工程每只生物的 `getAttack` 伤害值都写在类里（远低于合并简化版的 `ATTACK_DAMAGE` 属性直伤），迁移时要**逐只覆写 `getAttack`**，否则近战会变成 11 点属性伤害。参数速查见每批表格「近战」行。
> - 原工程 host / degenerate / deteriorated / original 的族基类 `getAttack` 会在命中时附带 `corruption 100/0`（子类常以 `super.getAttack(target)` 开头），迁移时记得保留这个「近战附加腐化」；Attacker/LongArm/Shocker/refactored 基类则为 `return false`。
> - 原工程的 `fakeDeathChance/fakeDeathDuration` 大多为 `50/45`，合并简化版统一写成了 `40/30`；迁移时按本表恢复（不影响技能，但影响死亡表现）。
> - `hasWalkAnim` 必须与 animation clip 对齐（合并基类：无 walk 动画时恒播 walk；有 walk 动画但没 `run` clip 时 run 状态会播不存在的动画）。**逐只核对见每批「动画」行**。

### 批次 1：degenerate 4 只 + groveler

**前置依赖**：`corruption_dart`、`acid_ball`、`corruption_pool`、`cuboid` 已注册；`SkillUtil` 的 `spawnPoolAt` 已接回；`bleeding` 效果至少保留周期伤害（当前占位可接受）。

#### 1.1 `degenerate_pig`（`DegeneratePigEntity`，源 328 行）
| 项 | 内容 |
|---|---|
| 技能 | **C3 腐化猪蓄力冲锋（三连冲）**：状态机 IDLE→WINDUP(24t)→DASH(26t)→GAP(10t)×3 段→RECOVERY(50t)，之后 `cooldown = 100+rand(60)`；触发：目标距离 3~11 格 |
| 数值 | 每段冲撞伤害 `6.5 + 2.5×(段-1)`（6.5/9/11.5），击退 1.2；冲刺速度 `0.55 + 0.1×(段-1)`；命中附加 `corruption 120/1`；撞墙：中断连击 + `ANVIL_LAND` 音效 + 长后摇 |
| 位移 | 每段 `setDeltaMovement(forward×speed, 0.12/保留 y)`；蓄力/段间 `faceTarget` |
| 粒子 | `chargeIn(2.0,5,progress)`、`burst(1.4,12,0.28)`、`CorruptionSkillFx.impact`；沿途铲除硬度<1 的方块（`destroyBlock`） |
| 音效 | `PIG_AMBIENT`（蓄力）、`PIG_HURT`（起冲）、`ANVIL_LAND`（撞墙）——均原版 |
| 依赖 | SkillUtil: chargeIn/faceTarget/burst/isValidTarget/strike/corrupt/playSoundPitched；Fx: impact；实体：无；效果：CORRUPTION |
| 动画 | clip：idle/walk/**run**/die ✅ 已备；需要**删掉** `triggerAnim("movement_controller","idle")`（合并基类没有 triggerable "idle"）；`hasWalkAnim=true` 保持；`setHasDieAnim(true)` |
| 冲突/注意 | ①基类 `tickChase` 会在冲锋期间继续寻路 → 进入 WINDUP/DASH 时 `getNavigation().stop()`；②冲锋期间 `isPushable=false` 需覆写；③`getAttack` 原版 11 点直伤 → 覆写；④原版 walk/run 是倍率语义，合并简化版已写 0.21/0.9，不必再改 |
| 难度 | **M**（状态机约 150 行 + 与基类寻路/近战竞争） |

#### 1.2 `degenerate_zombie`（`DegenerateZombieEntity`，源 4.8KB）
| 项 | 内容 |
|---|---|
| 技能 | **无主动技能**（种族模板怪）；仅死亡表现（族基类） |
| 数值 | 近战 13.5 |
| 依赖 | 无（仅族基类死亡粒子） |
| 动画 | clip：idle/walk/run/die ✅；`hasWalkAnim=true`；`setHasDieAnim(true)`；fakeDeath 50/45 |
| 冲突 | 无；覆写 `getAttack`=13.5 |
| 难度 | **S**（30 分钟） |

#### 1.3 `degenerate_villager`（`DegenerateVillagerEntity`，源 295 行）
| 项 | 内容 |
|---|---|
| 技能 1 | **C1 酸液瘴气弹**（冷却 90t；目标 3.5~16 格）：先在地面画 ω 形预警圈（`telegraphGround` 半径 2.8），再抛 `CorruptionDart`（ACID 绿、速度 0.9、视觉 0.35、受重力、伤害 7、寿命 100、`withPool(2.8, 120, 4.0)`、命中 `lysis 120/0`）；音效 `LLAMA_SPIT` |
| 技能 2 | **C2 硬化召唤**（冷却 220t；血量≤70% 触发）：自身周围 4 面 `CuboidEntity` 血肉墙（2.4 半径均布、`setSize(1.6,1.8,0.5)`、寿命 100、`setOnHit` 对触碰者 `strike 3.0` 击退 0.4），并自愈 4；粒子 pillar+shockwaveRing；音效 `RAVAGER_ROAR` |
| 旧技能 | 每 15 tick 投旧版 `AcidBallProjectile`（速度 1.5、命中 5 + `fear 40`）——原作保留，建议**保留**（区分新旧手感）；如嫌吵可加开关 |
| 数值 | 近战 12 |
| 依赖 | SkillUtil: telegraphGround/healthRatio/strike/playSoundPitched/burst；Fx: breath/pillar/shockwaveRing/impact；实体：CorruptionDart、AcidBallProjectile、CuboidEntity；效果：LYSIS、FEAR |
| 动画 | clip：idle/walk/run/die ✅；`hasWalkAnim=true`、`setHasDieAnim(true)`、fakeDeath 50/45 |
| 冲突 | ①墙的 `CuboidEntity` 需要 `CuboidRenderer`；②`telegraphGround` 落点必须固定（原实现已固定）；③召唤墙与基类 `tickChase` 的寻路无冲突（墙是独立实体） |
| 难度 | **M**（两个技能 + 投射物/池/墙联动） |

#### 1.4 `degenerate_cow`（`DegenerateCowEntity`，源 258 行）
| 项 | 内容 |
|---|---|
| 技能 1 | **C4 屠戮者暴怒**（血量≤50% 触发一次；`RAGE_DURATION=200`）：`DAMAGE_BOOST 200/1` + `MOVEMENT_SPEED 200/1` + `KNOCKBACK_RESISTANCE` base 1.0，血量回升后退出（并恢复 0）；释放粒子 burst+shockwaveRing；音效 `RAVAGER_ROAR` |
| 技能 2 | **C5 血肉偿还**（冷却 150t；目标≤20 格；自身血量>25%）：割 40% 当前血量 → 伤害 `clamp(割血量×0.55, 8, 45)`；射 `CorruptionDart`（RED、速度 1.0、视觉 0.5、无重力、寿命 60、`withPool(2.0,60,damage×0.08)`、命中 `bleeding 140/1`）；先 `bloodMist`+pillar+`COW_HURT` 表现（明显前摇） |
| 数值 | 近战 10 |
| 依赖 | SkillUtil: healthRatio/applyEffect/burst/playSoundPitched/shoot 无（Dart 静态工厂）/…；Fx: bloodMist/pillar/shockwaveRing/impact；实体：CorruptionDart；效果：BLEEDING |
| 动画 | clip：idle/walk/die（**无 run**）；`hasWalkAnim` 当前为 true → 改为 **false**；`setHasDieAnim(true)`；fakeDeath 50/45 |
| 冲突 | ①血量被自己扣到 1 时若同时被击杀，fake death 会把血量拉满（基类 `startFakeDeath` 里 `setHealth(maxHealth)`）→ 与原「自杀式」表现略冲突，可在 `getAttack`/技能里判 `isFakeDying` 时跳过；②击退免疫 base 值改动需在退出时恢复（原实现已做） |
| 难度 | **M** |

#### 1.5 `groveler`（`GrovelerEntity`，源 305 行）
| 项 | 内容 |
|---|---|
| 技能 | **C10 地底突袭**：冷却 220t；目标 5~20 格、`onGround`：入地（`INVISIBILITY` + `setInvulnerable(true)` + `setInvisible(true)`，烟粒子 + `SILVERFISH_HURT` 音效）→ 30t 潜行接近（`lungeAt(0.5,0)` + 拖尾粒子）→ 14t 破土预警（目标脚下固定点 `warningRing(2.2, 18→30)` + `CRIT` 粒子 + `SILVERFISH_AMBIENT`）→ 破土（瞬移、解除无敌、`burst(2.4,28,0.45)`+pillar+`RAVAGER_STEP`+bloodMist、`areaTrueDamage(2.6, 4.5 min, 9 common, 击退 1.1)`，命中者 `slow 60/1`+`bleed 100/0`）→ 30t 恢复 |
| 数值 | 近战 8 |
| 依赖 | SkillUtil: lungeAt/warningRing/burst/areaTrueDamage/findTargets/slow/bleed/playSoundPitched；Fx: impact/breath/pillar/bloodMist；实体：无；效果：BLEEDING、INVISIBILITY（原版） |
| 动画 | clip：walk/idle（无 run/die）；`hasWalkAnim` 当前 true → **false**；`setHasDieAnim(false)`；fakeDeath 原版无（默认 0）→ 当前 40/30 建议**关闭**（`setFakeDeathEnabled(false)`），因为钻地/无敌状态与假死叠加易出 bug |
| 冲突 | ①`setInvulnerable(true)` 期间仍可能被基类 `hurt` 处理（`isInvulnerableTo` 覆写已做）；②破土瞬移后基类 `tickChase` 会立即寻路，需 `getNavigation().stop()`；③`isPushable` 覆写 |
| 难度 | **M**（状态机 + 无敌/隐身/传送） |

**批次 1 验收**：每只可在 `runClient` 单机用 `/summon` 生成并观察（见 §5）；重点检查冲锋/破土期间不闪回、Dart 颜色/池正确、语音无缺失。

---

### 批次 2：refactored 4 只

**前置依赖**：批次 1（Dart/Pool/Cuboid 已可用）；`LightningEffectRenderer`（目标工程已有）；`BearerEntity` 进化接 infcore（`data/rotten_ruins/infcore_evolution` 的 `corruption_evo` 已由既有 datagen 生成，见 `merge-execution.md` §1.7）。

#### 2.1 `attacker`（`AttackerEntity`，源 268 行）
| 项 | 内容 |
|---|---|
| 技能 | **C8 打击后撤**：冷却 110t；目标 3.5~12 格且 `onGround`：WINDUP 16t（`chargeIn(1.8,4,p)` + faceTarget）→ `lungeAt(0.85,0.34)` + `burst(1.4,12,0.3)` + `RAVAGER_ATTACK` → 命中判定 `findTargets(1.6)` + `strike 7 击退 0.5` → 命中即**后撤**（按目标→自身方向 `setDeltaMovement(n×1.15, 0.38, n×1.15)`、转身、`RAVAGER_STEP`、`RETREAT_COOLDOWN=60`）；每 50 tick、目标<10 格时 `performLowLongJump()`（水平+2.2、垂直 0.3、限速 3） |
| 数值 | 近战 10 |
| 依赖 | SkillUtil: chargeIn/faceTarget/lungeAt/burst/findTargets/strike/playSoundPitched；Fx: impact/breath；实体：无；效果：无 |
| 动画 | clip：idle/walk/run（无 die）；`hasWalkAnim=true` 保持；`setHasDieAnim(false)`；fakeDeath 原版无（0）→ 关闭；当前简化版 40/30 要关 |
| 自定义音效 | 环境音/受击音使用 `ATTACKER_LIVING_1/2`、`ATTACKER_HURT_1/2` → 需移植这 4 个 ogg（§4） |
| 冲突 | ①大碰撞箱（2.0×4.5）→ 后排/贴身判定用膨胀盒；②基类 `tickChase` 与后撤冲刺竞争 → 后撤期间 `getNavigation().stop()`；③`setHasWalkAnim` 当前 true & clip 有 run，OK |
| 难度 | **M** |

#### 2.2 `bearer`（`BearerEntity`，源 262 行）
| 项 | 内容 |
|---|---|
| 技能 | **吞噬成长（非攻击技能）**：每 10 tick 找 0.2 格内同类，按 devourValue/UUID 决出强者 → 持续贴身吞噬（`devourTickTimer` 按 victim 血量换算）→ 完成后：`devourValue += victim×0.5`、最大生命 +3~5% victim 最大生命、回血、victim `discard`、广播 `EntityEvent(3)`（死亡烟）→ `BearerManager.getInstance().tryEvolution(this)`（**目标工程应改为 infcore `EvolutionManager.applyEvolution(level, this, RottenFaction.CORRUPTION.evolutionRuleType())`**）；`getDimensions` 按 `sqrt(devourValue)+0.35` 缩放、`getScale()`=devourValue（渲染缩放）；死亡 C9 领域按体型放大（原 `scaleDeathFieldBySize`） |
| 数值 | 近战 无（`getAttack` 返回 false） |
| 依赖 | infcore EvolutionManager / `corruption_evo` 数据（已存在）；`CorruptionSkillUtil.spawnPoolAt`（C9）；效果：无 |
| 动画 | clip：idle/walk（无 run/die）；**`hasWalkAnim` 当前 true → false**（或保留自定义 walk/idle 控制器）；fakeDeath 原版无 → 关闭 |
| 冲突 | ①`devourValue` 同步 + 渲染缩放：目标 `GeoBaseRender` 是否读 `getScale()` 需先确认（`AbstractRottenEntity` 没有 `getScale` 覆写钩子，原版是 Geo 渲染基类里调 `entity.getScale()`）；若无，本阶段可**先不做缩放**（只做数值/进化），缩放留作渲染层小任务；②吞噬目标必须是同类且存活的 `BearerEntity`；③进化规则已数据驱动，无需迁 `BearerManager` |
| 难度 | **M**（状态+同步；渲染缩放为可裁剪项） |

#### 2.3 `long_arm_beast`（`LongArmBeastEntity`，源 403 行）
| 项 | 内容 |
|---|---|
| 技能 | **C7 钩刺拖拽**：冷却 140t；目标 4~13 格：WINDUP 20t（`chargeIn(2.0,5,p)` + `triggerAnim("attack_controller","attack_trigger")` + `FISHING_BOBBER_THROW`）→ 生成 `CuboidEntity` 钩爪（`setSize(0.4,0.4,1.4)`、owner、寿命 16、`moveTo(target 中心, 10)`；`setOnHit` 命中即 `applyHookDrag`）→ 结算：`strike 7`（无击退）→ `pull(身前2格, 强度0.95, 抬升0.35)` + `slow 60/2` + `bleed 60/0` + `CHAIN_HIT` + FX |
| 附带 | 原版 `getAttack` 只触发动画（真实伤害在关键帧）；关键帧通道当前空转 → **改为直接伤害**：`getAttack` 里直接 `hurt(ATTACK_DAMAGE=11)`，并按概率（15%）做一次小范围横扫（用 `RangeAttackUtils.attackSector` 3.2/120° 10~15 伤害替代原 `fullyCustomAttack`） |
| 数值 | 近战 11（`ATTACK_DAMAGE` 属性） |
| 依赖 | SkillUtil: chargeIn/faceTarget/isValidTarget/strike/pull/slow/bleed/playSoundPitched；Fx: breath/impact；实体：CuboidEntity；效果：BLEEDING |
| 动画 | clip：walk/die/idle/**attack** ✅；`hasWalkAnim` 当前 true → **false**（无 run）；`setHasDieAnim(true)`；`registerAdditionalControllers` 里加 `attack_controller`（`triggerableAnim("attack_trigger", play "attack")`）；不需要 instructions handler |
| 冲突 | ①钩爪命中 → `CuboidEntity` 的 onHit 只在碰撞箱相交触发，目标移动可能漏判 → 保留原「计时到达即结算」（`hookContactTimer = travel+4`）；②`tickChase` 期间钩爪逻辑独立（钩爪是实体），不冲突；③大模型 3.9 高，攻击距离 2.5 |
| 难度 | **M/L**（钩爪 + 控制器 + 替代范围攻击） |

#### 2.4 `shocker`（`ShockerEntity`，源 287 行）
| 项 | 内容 |
|---|---|
| 技能 | **C6 过载脉冲**：冷却 200t；目标距离≥2.5：WINDUP 36t（`chargeIn(3.0,7,p)` + 每 4t `chargeSmoke(2)` + 每 8t `BEACON_ACTIVATE` 升调 + faceTarget）→ 释放：`burst(6.5,40,0.55)` + `shockwaveRing(6.5)` + 8 条径向红色闪电（`LightningEffectRenderer.spawnRedLightning`，目标工程已有）+ `LIGHTNING_BOLT_IMPACT`；范围内目标 `TrueHurt(min6, common10, level 2)` → 命中者 `slow 60/3` + `weakness 60/0` + FX；释放后原地硬直 |
| 附带 | 原版 `doHurtGoal` 覆盖为 `fullyCustomAttack()`（扇形 180°/3.2/伤害 4~8/破坏方块/伤害衰减/`hurtTick`）→ 建议替换成 `RangeAttackUtils.attackSector(this, 3.2f, 180f, 3.2f, 4f)`（签名：`attackSector(attacker, radius, angleDegrees, verticalRange, damage)`；若要 8 点上限就分两次/取随机值）+ 破软方块小工具；`canBreakBlocks()` 保留 true（基类有沿途破坏系统） |
| 数值 | 近战：走技能（脉冲/扇击），无普通近战 |
| 依赖 | SkillUtil: chargeIn/chargeSmoke/findTargets/slow/applyEffect/playSoundPitched；`CorruptionDamageUtil.TrueHurt`；Fx: burst/shockwaveRing/impact；实体：无；效果：无 |
| 动画 | clip：walk/idle/die（无 run）；`hasWalkAnim` 当前 true → **false**；`setHasDieAnim(true)`；fakeDeath 50/45；`stopAiAtDist=false`（原版） |
| 冲突 | ①闪电特效需要 `NetworkHelper.sendLightningToAll`（既有 `LightningEffectRenderer` 客户端已注册到 `EffectSyncPacket` 的 `"lightning"` 分支）——服务端调用即可，无新包；②`CorruptionAreaAttackUtil` 若替代需保留「破坏软方块」手感（可只破坏硬度<1 的方块）；③蓄力期间硬直/减速需自管 |
| 难度 | **M/L** |

**批次 2 验收**：重点测「突进/后撤、钩爪拉人、脉冲窗口」；用客户端观察粒子/闪电是否两端一致；服务端日志无 NPE。

---

### 批次 3：host 11 只（先做共用 B 系，再逐只特殊）

**前置依赖**：批次 1/2 的弹体/池/骨刺；`BloodBurst/MeatBurst` 等既有网络包（基类 `onBurstEnd` 已用）无需改。

#### 3.0 `BaseHostEntity` 共用技能（一次实现，11 只共享）
原 `C/.../host/BaseHostEntity.java`（361 行）的开关式实现：

| 技能 | 开关/参数默认 | 行为 | 依赖 |
|---|---|---|---|
| **B1 腐化唾液吐息** | `enableSpit(damage, cooldown, range)`；默认 `spitCooldown=50/spitDamage=6/spitRange=12/spitBurstCount=1/spitBurstInterval=6` | 目标 2.5~range 时：先兆 `CorruptionSkillFx.breath(2.5, 0xFFB0200F, 10)` + `LLAMA_SPIT` → 连发 `CorruptionDart.shoot(0.85, RED, 0.22, gravity=true).setDamage(spitDamage).setMaxLifetime(80).setHitEffect(CORRUPTION,120,0)` | SkillFx、CorruptionDart、CorruptionEffects |
| **B9 尸群践踏** | `enableHordeCharge(required=3, radius=12)` | 每 20t 检查：有目标且 `countAllies(radius)≥required` → 全队 `MOVEMENT_SPEED 140/1` + `DAMAGE_BOOST 140/0`，音效 `ZOMBIE_AMBIENT` | SkillUtil.buffAllies/countAllies |
| **B10 濒死骨爆** | `enableBoneBurst(count=10, damage=4.0)` | **爆体结束**（`onBurstEnd`）时：`burst(1.6,18,0.25)`+pillar+bloodMist，环状发射 `count` 根 `BoneSpikeEntity.launch(speed 0.62~0.8, damage)`（两层仰角 0.12/0.42），音效 `SKELETON_HURT/STEP` | BoneSpikeEntity、SkillUtil、SkillFx |
| **B11 血肉牵引** | `enableFleshPull(strength=0.9)` | 近战命中后 `pull(this.position, target, strength, 0.25)` + `bleed 40/0` + impact | SkillUtil |
| **B12 汲血回复** | `enableLifesteal(ratio=0.35)` | 近战命中后 `lifesteal(damage, ratio)`（残血翻倍） + impact | SkillUtil |

> 实现位置：合并后的 `BaseHostEntity`（当前是 16 行空壳）里加这些开关与 `applyOnHitEffects/shieldBurst` 方法；**B10 必须挂在 `onBurstEnd`**（不能再用原 `onFakeDeathEnd` 名字），并 `super.onBurstEnd()` 先调（基类发粒子/掉落）。共用死亡粒子已由基类 `onBurstEnd` 覆盖（MeatBurstPacket + explosion_bloody + dust + RIP_BODY + 掉落），因此原 `BaseHostEntity.onFakeDeathEnd` 里那一大段粒子/网络代码**不要重复搬**，只保留族特有部分（B10 骨刺）。

#### 3.1 逐只清单（共用 B 系之外的差异）

| 生物（id） | 启用/特有技能 | 参数 | 近战 | 动画（merged clip / 配置修正） | 冲突点 | 难度 |
|---|---|---|---|---|---|---|
| `corruption_zombie` | B10 | count10 dmg3.5 | 6.5 | idle/walk/run/die ✅；walkAnim true；dieAnim true；fakeDeath 50/45 | 无 | S |
| `corruption_player` | B10/B11/B12 | 8/4.0；0.9；0.35 | 6.5（删「CREEPER+spore」彩蛋） | idle/walk/run/attack/die ✅；walkAnim true；dieAnim true | 皮肤系统不搬（已定），静态贴图 `textures/entity/corruption_player.png`；`getAttack` 彩蛋代码要删 | S |
| `corruption_cow` | **B5 狂奔冲撞** + B9/B12 | 冲锋：range 3~10、windup22、max45、recovery40、speed0.62、dmg9；命中 `corrupt140/1`+`nausea60/0`；撞墙自伤4+`nausea80/1`+`slow60/2`；沿途铲软方块 | 4 | idle/walk/die/run ✅；walkAnim true；dieAnim true | ①冲锋期间 `isPushable=false`、②基类近战与冲锋状态竞争 → 冲锋时停 nav | M |
| `corruption_sheep` | B10/B9 | 12/3.0；4/12 | 4 | idle/die/walk（无 run）→ walkAnim **false**；dieAnim true | 无 | S |
| `corruption_pig` | B9/B11/B12 | 3/12；0.8；0.3 | 4 | idle/walk/long_jump/die（无 run）→ walkAnim **false**；dieAnim true；`long_jump` 未用 | 无 | S |
| `corruption_bat` | **B13 蝙蝠夜袭** + B1(3.5/70/14)/B10(6/2.5)/B12(0.4) | 夜袭：冷却110、range4~22、瞬移到目标背后2格（落点可站立性检查，失败则头顶）、`strike 3.5`、吸血0.6、`fear 80/0`、BAT 音效 | 无（getAttack false） | idle/fly/speed_fly（**无 walk**）→ 必须保留自定义 `idle_controller`（原版写法）否则移动播 walk 缺动画；`FlyingPathNavigation` + `FlyingMoveControl` + 自定义 `travel()` + 免摔伤 必须从原版搬回（合并简化版目前按地面单位） | ①飞行 AI 是**新增行为**，不是技能；②瞬移后基类 tickChase 需停 nav；③`setHasWalkAnim(false)` | **L**（飞行 + 技能） |
| `corruption_skeleton` | **B6 腐化箭雨** + **B7 射速强化** + `RangedAttackGoal→PoisonArrow` + 死亡 35% 生成 bone_ruins | B6：冷却160、range6~24、count5、spread3.5；先 `telegraphGround`，5 发 Dart 从落点上空 12 格落下（speed1.1、dmg4、life60、pool2.2/80/3、`corrupt120/0`）；B7：血量≤50% → `ATTACK_SPEED` 修饰符 `+0.6 MULTIPLY_TOTAL`（UUID 防叠加）；RangedAttack：`PoisonArrowProjectile.shoot(1.6, spread1.0, 弹道补偿)` | 1+难度（0/1/2）且 50% 概率 `corruption 100/1` | idle/walk/die（无 run）→ walkAnim **false**；dieAnim true；**无攻击动画**（RangedAttackGoal 不需要） | ①现有简化版没有 `RangedAttackGoal`/`RangedAttackMob`，要加 `implements RangedAttackMob`；②箭雨会生成大量 Dart（5/160t）→ 服务端限流；③死亡生成 bone_ruins 要在 `die()` 且非 fakeDying 时做（基类 fake death 会先接管，需要判 `!isFakeDying()`） | **L** |
| `corruption_bone_ruins` | **站桩自爆**：每 20t 扫描 8 格/高 5；触发→60t 喷发（每 tick 6 颗棕色 dust；每 20t 对区域内目标 `bleeding 100/1`；结束时实心圆盘粒子 + 爆炸直伤 5（`damageSources().explosion(this,this)`）+ `discard`） | — | 无 | 仅 idle → walkAnim false、dieAnim false、fakeDeath **关**、`stopAiAtDist` 建议 false | ①`discard()` 前要自己补一次掉落/粒子？原版没有掉落表；②自爆与基类假死/爆体互斥：建议 `setFakeDeathEnabled(false)`（合并简化版已做） | M |
| `corruption_villager` | **B8 同化传播**（+ B1 4.5/60/14、B12 0.5） | 命中玩家：`stackEffect(CORRUPTION, 240, min(stacks-1,3))`；目标切换重置；4 层 → 失控：`slow 100/4`+`blindness`+`weakness 1`+`nausea 1`；粒子 impact/burst/shockwaveRing；音效 `VILLAGER_HURT` | 6（原版 `target.hurt(...,6)`，调用 `super` 时另叠 `corruption 100/0`） | idle/walk/run/die ✅；walkAnim true；dieAnim true | ①`stackEffect` 叠加语义必须修（§1.1-2）；②非玩家目标只叠 1 级不触发失控 | M |
| `corruption_wolf` | **B2 感染嚎叫** + **B3 扑咬锁喉**（+ B9 3/14、B11 0.85） | B2：冷却260、pack≥2、radius16、max2 增援、packCap6；buff `DAMAGE_BOOST 200/1`+`MOVEMENT_SPEED 200/0`；音效 `WOLF_HOWL`；B3：windup14、range3~9、bite6、latch40t（每 tick `slow 10/3`+`bleed 40/0`，每 10t `strike 1.5`，结束时甩飞 `(0.9,0.42)`）、冷却120 | 5 | walk/idle/run ✅（无 die）→ walkAnim true、dieAnim false | ①锁喉期间目标被推走/死亡要重置；②`isPushable` 覆写；③嚎叫增援要设 `setTarget`——注意 P0-1 索敌闸门修好后才能生效 | **L** |
| `corruption_chicken` | **B4 嘶鸣分裂** | 受击后 25% 且冷却60、同屏≤8：血雾+冲击环+`CHICKEN_HURT`，生成 2 只 `markAsSplinter()` 的小体（继承目标），`areaDamage 2.5`（0 伤害，仅击退0.75） | 2 | idle/walk（无 run）→ walkAnim false；dieAnim false（无 die） | ①小体不再分裂（`isSplinter` 状态要同步/存档？简化：仅本次生命周期）；②`hurt()` 覆写要调 `super` 后再触发 | M |

> 11 只里 `corruption_player` 的皮肤/模型切换、`corruption_bat` 的飞行、`corruption_skeleton` 的远程 AI 是**非技能差异**，会与技能同批实现（否则技能无法演示）。

**批次 3 验收**：每只至少验证 1 个主动技能 + 死亡表现；重点：B10 在爆体结束爆骨刺且不双倍掉落；B6 箭雨落点=预警圈；B8 第 4 层触发失控；B4 分裂体不无限分裂。

---

### 批次 4：linked 2 只 + original 1 只

**前置依赖**：`CorruptionPoolEntity`（C9 领域）、`LightningEffectRenderer`（既有）、`purifyAllies/buffAllies/countAllies`（已适配）。

#### 4.1 `humus_column`（`HumusColumnEntity`，源 351 行）
| 项 | 内容 |
|---|---|
| 技能 1 | **腐植柱增益/减益循环**（每 100t）：半径 10 内取优先级最高的盟友（kind 优先级 HOST4/REFACTORED3/DEGENERATE2/LINKED1，同优先级按击杀数）→ 先移除其一个负面效果；若无可移除则随机加正面效果（速度/力量/再生/抗性/防火/伤害吸收/`sclerosis`，均 200t 对应等级）；同时随机选 1 个敌人 → 按权重随机负面（虚弱10/缓慢10/中毒8/凋零5/失明5/饥饿5/霉运5/`corruption`8/`lysis`1/`fear`5/`bleeding`5，200t），若没有则移除其一个正面；生效时：红色闪电（`LightningEffectRenderer.spawnRedLightning(y+2.2 → 目标胸口, 12)`) + glowTimer30 |
| 技能 2 | **C13 净化脉冲**（每 160t，半径14）：`purifyAllies` 清全部负面；若清到人 → pillar+shockwaveRing+`BEACON_POWER_SELECT`+全队 `REGENERATION 120/0` + glowTimer30 |
| 技能 3 | **C9 死亡辐照领域**：死亡留下腐蚀池（半径 4.0、寿命 200、每跳 3.5） |
| 数值 | 近战 无（`getAttack` false） |
| 依赖 | SkillUtil: purifyAllies/buffAllies/playSoundPitched/spawnPoolAt；`EntityBuffUtils.addOrStackEffect`（或修好的 SkillUtil.stackEffect）；`CorruptionEffects`；`LightningEffectRenderer`；实体：CorruptionPool |
| 动画 | 仅 idle；walkAnim false；dieAnim false（无 die）；fakeDeath **关**；`stopAiAtDist=false`（需加 setter）；`travel(ZERO)` 禁止移动 |
| 冲突 | ①`glowTimer` 同步字段 + `humus_column_grow.png` glow layer → 发光层需要 `IGeoLayerProvider` 实现（原 `HumusGlowLayer` 2.3KB）→ 小任务；②站桩单位仍会被基类 `tickChase` 移动 → `travel(ZERO)` + `setChaseSpeed(0)`/nav stop；③`isCorruptionAlly` 计数范围 |
| 难度 | **M**（逻辑多但结构简单） |

#### 4.2 `summoning_pillar`（`SummoningPillarEntity`，源 221 行）
| 项 | 内容 |
|---|---|
| 技能 | **C12 群体召唤**（冷却 240t；仅当有存活目标）：倒计时 <40t 时每 5t `chargeIn(1.8,3,p)` 蓄势；召唤前统计 20 格内同族人口（含自己）；人口≥10 → 改为全队 `DAMAGE_RESISTANCE 200/0`、冷却减半；否则召唤 `min(2, 10-pop)` 只：40% zombie / 30% chicken / 20% wolf / 10% skeleton（随机角度、半径 1.5~4.0，继承目标），每只落点 pillar 粒子；总表现：`triggerAnim("summon","summon")` + pillar + shockwaveRing + `EVOKER_CAST_SPELL`；**C9 死亡领域**（3.5 半径/160/3.0） |
| 数值 | 近战 无 |
| 依赖 | SkillUtil: chargeIn/buffAllies/playSoundPitched/spawnPoolAt；Fx: pillar/shockwaveRing；实体：CorruptionPool（C9）；`CorruptionEntityTypes` 生物引用（4 种召唤物） |
| 动画 | idle + summon ✅；walkAnim false；dieAnim false；fakeDeath 关；`stopAiAtDist=false`；自定义 `summon` controller（`triggerAnim("summon","summon")`）需在 `registerAdditionalControllers` 里注册 |
| 冲突 | ①召唤物是 `PathfinderMob`/`Monster`，要用目标类型 `create(level)` 并 `setTarget`；②人口统计需排除非腐化；③站桩 `travel(ZERO)` + 禁推动 |
| 难度 | **M** |

#### 4.3 `original_bug`（`OriginalBugEntity`，源 218 行）
| 项 | 内容 |
|---|---|
| 技能 | **C11 酸液喷吐**（冷却 130t；目标距离≤5.5+2）：WINDUP 14t（`chargeIn(1.4,4,p)` + 每 5t `PUFFER_FISH_BLOW_UP` 升调 + faceTarget）→ 释放：`CorruptionSkillFx.breath(origin, 前向, 5.5, CorruptionDart.ACID 绿, 40)` + `PUFFER_FISH_BLOW_OUT`；前方 60° 扇区 5.5 格内目标 `strike 5.5`，命中后 `weakness 120/1`+`lysis 120/0`+`corrupt 120/0`+impact |
| 数值 | 近战 5 |
| 依赖 | SkillUtil: chargeIn/playSoundPitched/faceTarget/findTargetsInSector/strike/applyEffect/lysis/corrupt；Fx: breath/impact；实体：无；效果：WEAKNESS、LYSIS、CORRUPTION |
| 动画 | idle/walk（无 run/die）→ walkAnim **false**；dieAnim false；fakeDeath 50/45 |
| 冲突 | ①`getAttack` 原版 5；②扇区判定是否含视线要求（原版无）；③ACID 颜色取自 `CorruptionDart.ACID` 常量（无需实例化 Dart） |
| 难度 | **S/M** |

**批次 4 验收**：humus 在盟友旁能观察到 buff/闪电/净化；summoning 在玩家附近召唤且人口上限生效；original_bug 喷吐扇形命中并施加三效果。

---

## 4. 资源缺口清单

### 4.1 已就位（不需要再复制）

| 类别 | 文件 | 备注 |
|---|---|---|
| 23 只生物的 geo/动画/实体贴图 | `assets/rotten_ruins/{geo,animations,textures/entity}/…` | `port-assets.md` 批次 1 已复制；host 族改名为 `corruption_*` |
| 3 个 Geo 弹射物资源 | `geo/{acid_ball,bone_spike,poison_arrow}.geo.json`、`animations/*`、`textures/entity/{bone_spike,poison_arrow}.png`、`textures/item/acid_ball.png` | 并行任务已完成（`docs/merge/corruption-skill-assets.md`）；`poison_arrow.animation.json` 是**空 animations 表**（源即如此，无 `fly` 片段，属于已知现象，不是 bug） |
| 腐蚀池/Dart/Cuboid/Shield 资产 | 无（全部程序化渲染/粒子） | 仅需 Java 类与渲染器 |
| 6 个效果 | `CorruptionEffects` 已注册 | 行为占位见 §6 |
| 既有粒子 | `blood`/`meat`/`explosion_bloody`/`ulcerated_meat`/`heart_lose` | 技能批次不额外依赖自定义粒子 |

### 4.2 技能批次真正缺的资源

| # | 类别 | 缺什么 | 用途/触发 | 需要动的文件 | 优先级 |
|---|---|---|---|---|---|
| 1 | 音效 | 6 个 `.ogg`：`degenerate_call_type1`、`attacker_living_1`、`attacker_living_2`、`attacker_hurt_1`、`attacker_hurt_2`、`overload`（可选再加 `degenerate_hurt_type1`、`body_hurt1/2`） | degenerate 环境音、attacker 环境/受击音、villager 失控 + shocker 脉冲（overload） | 复制 `C/.../assets/corruption/sounds/*.ogg` → `H/src/main/resources/assets/rotten_ruins/sounds/`；`SoundEventRegistry` 加条目；`datagen/gen/SoundData` 加定义；`LangDataCN/EN` 加字幕键 `subtitles.rotten_ruins.*` | 批次 2/3 前 |
| 2 | 语言 | 6 个技能实体名（`entity.rotten_ruins.{poison_arrow,acid_ball,bone_spike,corruption_dart,corruption_pool,cuboid}`） | 实体选择器/调试信息 | `datagen/gen/lang/LangDataCN.java`、`LangDataEN.java`（腐化段） | Round 0 |
| 3 | 音频字幕 | 音效字幕键（与 #1 同批） | 声音提示 | 同上 `LangDataCN/EN` | 批次 2/3 前 |
| 4 | 自定义粒子（**非技能阻塞**） | 6 个：`degenerate_die_meat`、`degenerate_meat`、`bloody`、`blood_splat`、`blood_fog`、`blood_splash` + 客户端 Provider（`client/particle/` 5 个类对应）+ 贴图（`blood0..5`、`bloodsplat0..5`、`blood_fog0..3`、`bloody.png`、`degenerate_meat.png`、`degenerate_die_meat.png`、`blood4x4.png`） | 腐化种周期掉肉/死亡喷肉/血腥事件（`HandleGore`） | `RegisterParticles` 增量 + `ParticleData` + `ClientModEventHandler.onRegisterParticleProviders` + 贴图复制 | 可选（B 系死亡表现/血腥 polish）；若要做，估 1 人日 |
| 5 | 死亡残骸 | `CorruptionRipBodyUtil`（约 200 行）+ 5 个族基类调用 | 爆体后留残骸方块（9 个 remains 方块已在游戏中） | 移植 util + `onBurstEnd`/`die()` 调用；**非技能阻塞**，属死亡表现 polish | 可选 0.5 人日 |
| 6 | 语言（可选） | `bleeding`/`fear` 图标贴图（`mob_effect/bleeding.png` 源缺失） | 效果图标 | 美术补齐；非技能阻塞 | 低 |

> 结论：**技能批次本身零新增模型/贴图/粒子依赖**；真正要补的是 6 个音效 + 6 条实体语言键；自定义粒子与残骸属于后续「死亡/血腥表现」polish，不阻塞技能验收。

---

## 5. 建议的每轮实现顺序与验收方式

### 5.1 实现顺序（与批次编号对齐）

1. **Round 0 基础设施（4~6 人日）**：
   1. 修 P0-1 索敌策略（`BaseCorruptionEntity` 覆写 3 个方法）；
   2. 基类小补：`setStopAiAtDist(boolean)`、技能位移辅助（`getNavigation().stop()`/suppressChase）；
   3. （若决定）补 `fear/lysis/sclerosis/miasma` 效果逻辑；
   4. 移植 §1.5 清单 1~6、8（技能实体 + 复用 `registerNoopRenderer`）；
   5. `CorruptionEntityTypes` 注册 6 个技能实体；`ClientModEventHandler` 注册渲染器；
   6. `SkillUtil.spawnPoolAt` 接回、`stackEffect` 修叠加；
   7. 语言 6 条；音效可放批次 2 前。
   验收：`compileJava` + `runData` + `runClient` 各生成一次技能实体/弹体不崩、无 missing model。
2. **Round 1（批次 1，2~3 人日）**：修 5 只生物的 `hasWalkAnim/getAttack/fakeDeath` → 实现 C3/C1+C2/C4+C5/C10 → 编译 + 逐只客户端验证。
3. **Round 2（批次 2，3~4 人日）**：attacker（含 4 个音效）→ bearer（吞噬+进化，缩放着情况）→ longarm（钩爪 + 攻击控制器 + 直接伤害）→ shocker（脉冲 + 范围攻击替代）。
4. **Round 3（批次 3，4~6 人日）**：先 `BaseHostEntity` B1/B9/B10/B11/B12 + 死亡接线 → 再逐只补 B2/B3/B4/B5/B6/B7/B8/B13 + 骷髅远程 + 蝙蝠飞行。
5. **Round 4（批次 4，2~3 人日）**：humus（含 glow 层）→ summoning（含 summon 控制器）→ original_bug。

### 5.2 每轮的验证手段（按成本从低到高）

**A. 编译（每改完一个类）**
```powershell
.\gradlew.bat compileJava
```
- 通过标准：无编译错误；重点看 `CorruptionEffects`/`AbstractHallProjectile`/`EntityTypeRegistry` 的签名是否对。

**B. 数据生成（改动 lang/datagen 时）**
```powershell
.\gradlew.bat runData
```
- 通过标准：`src/generated/resources/assets/rotten_ruins/lang/{zh_cn,en_us}.json` 新增键存在；`git status` 只出现预期文件。

**C. 客户端冒烟（技能体感验证，主手段）**
```powershell
.\gradlew.bat runClient
```
进入开发存档（`run/saves` 任一世界，建议先备份/新建超平坦创造世界），用命令：
```
/gamemode creative
/gamerule doMobSpawning false
/summon rotten_ruins:degenerate_pig ~ ~2 ~
/summon rotten_ruins:corruption_zombie ~ ~2 ~
/effect give @s rotten_ruins:corruption 10 0
```
逐只验收清单：
1. 生物能生成、不崩、动画无「Unable to find animation」告警（`run/logs/latest.log`）；
2. 索敌：靠近/攻击玩家后能锁定并追击（**P0-1 修复后**）；
3. 技能触发：站到触发距离内，观察前摇粒子/音效/位移/伤害（对自身用 `/data get entity @s Health` 记录血量，或先 `/effect give @s minecraft:resistance` 防被打死）；
4. 冷却：等 2 倍冷却时间确认重复触发节奏；
5. 死亡：`/kill @e[type=rotten_ruins:xxx]` 观察死亡表现/掉落（检查不要双倍掉落）；
6. 动画：`F3` 观察移动/攻击/死亡 clip 不闪烁。

**D. 专用服务器冒烟（回归/无客户端依赖部分）**
1. 在 `run/server.properties`（运行配置，非源码）临时开启：
```
enable-rcon=true
rcon.password=devtest
rcon.port=25575
```
2. 后台启动：
```powershell
.\gradlew.bat runServer
```
3. RCON 执行（PowerShell 单行，Source RCON 协议）：
```powershell
$r=[System.Net.Sockets.TcpClient]::new('127.0.0.1',25575);$s=$r.GetStream()
# 发送 auth 包后发送命令包（略，或用现成 mcrcon 工具）
```
建议命令：
```
summon rotten_ruins:attacker 0 64 0
summon rotten_ruins:long_arm_beast 0 64 0
summon rotten_ruins:shocker 0 64 0
summon rotten_ruins:humus_column 0 64 0
summon rotten_ruins:corruption_dart 0 64 0
```
通过标准：`run/logs/latest.log` 无 `ERROR/FATAL`/NPE；技能实体 tick 正常；服务器可正常 `stop`（验证 `CorruptionPool`/Dart 清理）。
> 若嫌 RCON 麻烦：`runServer` 前台控制台直接输入 `summon ...` 同样可行（Gradle 后台任务看不到交互时用 RCON）。

**E. 日志扫描（每次验收后）**
```powershell
Select-String -Path 'run/logs/latest.log' -Pattern 'Unable to find animation|NullPointerException|Exception|ERROR' | Select-Object -First 50
```

### 5.3 每轮验收标准（Definition of Done）

| 轮次 | 必须通过 |
|---|---|
| Round 0 | `compileJava`+`runData` 通过；`runClient` 生成 6 个技能实体不崩、能看到基础粒子；`runServer` 启动无 ERROR |
| Round 1 | 5 只生物技能各自可触发；C3 三连冲不卡地形；C1 酸池伤害/残留正确；C2 血肉墙可被破坏；C5 自伤/血爆弹正确；C10 钻地→破土链路完整 |
| Round 2 | attacker 突进+后撤循环；longarm 钩爪命中拉人；shocker 脉冲预警→爆发；bearer 吞噬增长+进化（`corruption_evo` 生效） |
| Round 3 | 11 只里至少 host 8 只有技能；B10 骨爆在爆体结束时触发且只掉一次 loot；B6 箭雨落点=预警圈；B13 蝙蝠可飞 |
| Round 4 | humus buff/净化/闪电/glow；summoning 召唤+人口上限；original_bug 扇形喷吐 |
| 总验收 | 无 ERROR、无丢动画、TPS 无明显下降（`/forge tps` 或观察 tick）、存档/退出重进后技能相关状态正常 |

---

## 6. 风险与决策点（按优先级）

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| 1 | **P0-1 索敌闸门**：`AbstractRottenEntity.canTarget/setTarget` 的威胁值过滤让玩家默认不可被腐化生物索敌（§0.2） | 所有技能无法对玩家演示/生效；腐化生物实际"中立" | 在 `BaseCorruptionEntity` 覆写 3 个方法（腐化阵营无威胁门槛，仅排除创造/旁观）；或把策略抽象为 `canTargetPlayerByFaction()` 基类钩子；本项必须最先做 |
| 2 | **P0-2 效果占位**：`fear`（原 NonRemovable + 清无敌帧）、`lysis`（伤害累计增伤）、`sclerosis`（单次伤害上限）、`miasma`（减疗/雾）均无逻辑；`corruption` 由死亡转化改成周期真伤；且 `fear` 需要的 `MixinLivingEntity` 与 `AccessorLivingEntity` 尚未移植到合并工程 mixin 清单 | 控制/DoT 类技能"看起来生效、实际无效"；数值平衡失真 | 批次 1 前补 4 个效果（参考 `C/.../effect/` 7 个类，约 300 行）+ 移植 `MixinLivingEntity`；或明确本阶段只做伤害/表现 |
| 3 | **基类语义差**：`tickChase` 覆盖技能位移；无 `onRealDeath`/`onFakeDeathStart`；fake death 接管死亡与掉落；keyframe 空转；`setStopAiAtDist` 无 setter | 状态机抖动、双倍掉落、LongArm 伤害不结算、站桩单位被冻结 | Round 0 按 §1.6 小改基类；技能内统一 `getNavigation().stop()`；LongArm 改直接伤害；B10/C9 挂 `onBurstEnd` |
| 4 | **动画配置不匹配**：bearer/degenerate_cow/groveler/skeleton/long_arm_beast/shocker/original_bug 被设 `hasWalkAnim=true` 但无 `run` clip；bat 无 `walk`/`run` clip；部分生物无 `die` clip | 移动时播不存在的动画（日志告警/T-pose） | 按 §3 每只「动画」行修正：无 run → `setHasWalkAnim(false)`；无 walk（bat）→ 自定义 idle 控制器；有 die → `setHasDieAnim(true)`；LongArm/Summoning 用 `registerAdditionalControllers` |
| 5 | `stackEffect` 语义为"取最大"而非"叠加" | B8 同化层数/Humus buff 叠加失效 | Round 0 改回叠加（或调用处改 `EntityBuffUtils` 叠加版并核对参数顺序） |
| 6 | `CorruptionAreaAttackUtil` 不搬导致 LongArm/Shocker 的「范围+破方块+伤害衰减」能力缩水 | 手感差异（可接受） | 用 `RangeAttackUtils` sector/cone + 30~50 行破软方块替代；如要 1:1 还原再评估 +2 人日 |
| 7 | 技能网络/同步：Dart/Pool 状态走 entityData（无需包）；Humus 闪电/Shocker 闪电走 `EffectSyncPacket`；glowTimer 需同步 | 客户端表现不一致 | 已有 `EntityDataAccessor` 与 `EffectSyncPacket`（`"lightning"` 分支已注册）可直接用；glow 层需要 `IGeoLayerProvider` 实现 |
| 8 | 召唤物/增援的 `setTarget` 受 P0-1 影响（狼嚎/B12 召唤继承目标） | 增援不攻击 | P0-1 修复后自然解决 |
| 9 | 性能：Dart/骨刺/闪电/粒子数量大（Shocker 8 闪电、Skeleton 5 Dart/160t、B10 10 骨刺） | 多只同屏时服务端 TPS 压力 | 沿用原版数值即可；每类技能加服务端节流（Cooldown 已内置）；压测 10 只同屏 |
| 10 | 已丢弃内容边界：Shield/Avila/RedDwarf/skin 系统不搬；`poisoned_saber`/`file`/`七罪`/芯片不属技能批次 | 无需处理，避免误搬 | 以 `merge-execution.md` 决策为准 |
| 11 | 并行任务重叠：`CorruptionSkillUtil/Fx`、`CorruptionDamageUtil`、技能资源已由其他任务完成（未提交） | 重复劳动/冲突 | 本计划把 Round 0 剩余项限定为「技能实体 + 渲染器 + 注册 + spawnPoolAt/stackEffect + 语言/音效」，不要重写已存在的两个 util 类 |

---

## 7. 附：逐生物参数速查（迁移时对照填写）

| 生物 | 原 `getAttack` | fakeDeath | doHurtTime / distance | walk/run（原倍率语义） | 关键技能编号 |
|---|---:|---|---|---|---|
| degenerate_pig | 11 | 50/45 | 18 / 2 | 1.0 / 1.09 | C3 |
| degenerate_zombie | 13.5 | 50/45 | 18 / 2 | 0.8 / 1.6 | — |
| degenerate_villager | 12 | 50/45 | 18 / 2 | 0.8 / 1.2 | C1、C2 |
| degenerate_cow | 10 | 50/45 | 18 / 2 | 不启用 walk / 0.8 | C4、C5 |
| groveler | 8 | 无（0） | 18 / 2 | 不启用 walk / 2.0 | C10 |
| attacker | 10 | 无（0） | 25 / 3.0 | 1.8 / 2.6 | C8 |
| bearer | 无（false） | 无（0） | — | 1.8 / 1.8 | 吞噬/进化、C9 |
| long_arm_beast | 11（关键帧） | 50/45 | 8 / 2.5 | 1.8 / 1.8 | C7、C9 |
| shocker | 脉冲/扇击 | 50/45 | 20 / 3.2 | 不启用 walk / 3.5 | C6、C9 |
| humus_column | 无（false） | 关 | — | 0 / 0 | C13、C9 |
| summoning_pillar | 无（false） | 关 | — | 0 / 0 | C12、C9 |
| original_bug | 5 | 50/45 | 18 / 2 | 不启用 walk / 1.3 | C11 |
| corruption_zombie | 6.5 | 50/45 | 18 / 2 | 0.8 / 1.5 | B10 |
| corruption_player | 6.5 | 50/45 | 18 / 2 | 0.8 / 1.2 | B10/B11/B12 |
| corruption_cow | 4 | 50/45 | 18 / 2 | 1.0 / 1.09 | B5、B9、B12 |
| corruption_sheep | 4 | 50/45 | 18 / 2 | 不启用 walk / 0.8 | B10、B9 |
| corruption_pig | 4 | 50/45 | 18 / 2 | 不启用 walk / 0.8 | B9、B11、B12 |
| corruption_bat | 无（false） | 无（0） | — | 飞行 | B13、B1、B10、B12 |
| corruption_skeleton | 1+难度 | 关 | 20 / 2 | 0.15 / 0.15（无 walk 动画） | B6、B7、远程 |
| corruption_bone_ruins | 无（false） | 关 | — | 0 / 0 | 自爆 |
| corruption_villager | 6 | 50/45 | 18 / 2 | 0.8 / 1.5 | B8、B1、B12 |
| corruption_wolf | 5 | 50/45 | 18 / 2 | 1.0 / 1.09 | B2、B3、B9、B11 |
| corruption_chicken | 2 | 50/45 | 18 / 2 | 不启用 walk / 0.8 | B4、B9 |

> 备注：上表 walk/run 是 corruption 原工程「倍率语义」的原始值，**不是**合并工程 `setWalkSpeed/setRunSpeed` 的绝对值语义；合并工程当前已按目标基类语义填了 0.2~0.9 的绝对值，技能迁移**不要**用本表覆盖速度，只在需要时用 `setChaseSpeed` 微调追击。
