# corruption 技能资源移植报告（批次 2：技能实体）

- 来源：`corruption/src/main/resources/assets/corruption/`（Java 参考：`corruption/src/main/java/com/corruption/`）
- 目标：`src/main/resources/assets/rotten_ruins/`
- 范围：只做资源复制/校验与代码调研，**未改任何 Java、未跑 Gradle**。

## 1. 复制清单（8 个新文件，SHA-256 源/目标一致）

| # | 源（assets/corruption） | 目标（assets/rotten_ruins） | 字节 | SHA-256 |
|---|---|---|---:|---|
| 1 | `geo/acid_ball.geo.json` | `geo/acid_ball.geo.json` | 797 | `388ab764e9e0e367c738610223096bba6fa10ea3b559cbecf6c0e4b17f16b759` |
| 2 | `geo/bone_spike.geo.json` | `geo/bone_spike.geo.json` | 1351 | `80a33a53f70868a091526d8b5d072ba7cd3bfc86ef527823fa6267d3e14e4080` |
| 3 | `geo/poison_arrow.geo.json` | `geo/poison_arrow.geo.json` | 461 | `f44722525fc6d41a0ff0db7f5ec493e0f750b34df2ea0d2d85be667716670c18` |
| 4 | `animations/acid_ball.animation.json` | `animations/acid_ball.animation.json` | 83 | `cfe7207076501ecb3989aafb811e9248fd06b3e6255e9419be132d254c6aeb20` |
| 5 | `animations/bone_spike.animation.json` | `animations/bone_spike.animation.json` | 388 | `219a02c73571404d8135d3c398315fa37793284ece4de64e88e4f136dfc949da` |
| 6 | `animations/poison_arrow.json` | `animations/poison_arrow.animation.json`（**改名**） | 54 | `e36379fc3289c87f21704f72b97a7166fafd6cd8b2ec04e14e997fe56faf9003` |
| 7 | `textures/entity/bone_spike.png` | `textures/entity/bone_spike.png` | 704 | `2bf5106b04b231e713e86f130588df0de3ce91181332db1d70b58d58d3277087` |
| 8 | `textures/entity/poison_arrow.png` | `textures/entity/poison_arrow.png` | 274 | `664c1dc115a0c65f6ef9564ab345dacbebcefd8c84db8747ab70b9dbd13da80b` |
| — | `textures/item/acid_ball.png` | `textures/item/acid_ball.png`（**已存在，未覆盖**） | 409 | `a9f96d0718431bf51a22b9a4f528bd83209f7894405d51805a2c89a7d5d0d3d9`（源=目标） |

说明：

- **动画改名结论**：源文件名是 `animations/poison_arrow.json`；corruption 的 `EntityTypeRegistry` 里指向 `animations/poison_arrow.animation.json`，故复制时改名为 `poison_arrow.animation.json`（与 GeckoLib 常规命名及同批另外两个动画一致）。
- **`textures/item/acid_ball.png` 无需复制**：批次 1 已作为物品贴图移植，SHA-256 与源完全一致；按"不覆盖已有文件"处理，未写入、无需备份。
- 8 个新文件复制后 SHA-256 全部与源一致；JSON 全部可被标准解析器解析、无 BOM；2 张新 PNG 签名有效。
- 资源元数据：
  - `acid_ball`：geo 16×16、1 根骨骼（bb_main）；animation 仅 `fly`（loop=true，无关键帧）。
  - `bone_spike`：geo 32×32、3 根骨骼（bone_spike 9 cubes + shard_a/shard_b 各 1 cube，共 11 cubes）；animation `fly` 含 `shard_a`/`shard_b` 摆动，骨骼名与模型匹配。
  - `poison_arrow`：geo 16×16、1 根骨骼（bb_main）；animation 是**空 animations 表**（源即如此，无 `fly` 片段，GeckoLib 加载合法、只是没有骨骼动画帧）。

## 2. 渲染方式结论：CorruptionDart / CorruptionPoolEntity / CuboidEntity / ShieldEntity

来源文件：`corruption/.../client/rend/{CuboidRenderer,ShieldRenderer}.java`、`client/entity/render/SkillProjectileRenderer.java`、`overworld/registry/{ClientRenderRegistry,EntityTypeRegistry}.java`、`event/ModClientEventHandler.java`。

| 实体 | 注册方式（EntityTypeRegistry） | 客户端渲染器 | 贴图/模型依赖 | 表现方式 |
|---|---|---|---|---|
| `CorruptionDart` | `registerSimpleProjectile("corruption_dart", 0.3×0.3, MISC)` → `CorruptionProjectileManager` 的 SIMPLE 列表 | **`SkillProjectileRenderer`**（空 `EntityRenderer`：`render()` 空实现、`getTextureLocation()` 返回 null） | **无**（不实现 IAutoRenderableProjectile，无 GeckoLib 模型/贴图/动画） | 纯客户端粒子：每 tick 3 颗 `DustParticleOptions`（颜色/半径由同步字段 `COLOR`/`VISUAL_RADIUS` 驱动）+ 概率 `ParticleTypes.LARGE_SMOKE`；命中/落地由服务端广播 `CorruptionSkillFx.impact`（dust+smoke） |
| `CorruptionPoolEntity` | `registerSimpleProjectile("corruption_pool", 3.0×0.4, MISC)` → 同上 | **`SkillProjectileRenderer`**（空渲染器） | **无** | 纯粒子：客户端每 tick `POOL_DUST`/`POOL_DUST_DARK`（DustParticleOptions）+ `LARGE_SMOKE`，服务端每 5 tick 广播深色 dust；无需任何资源 |
| `CuboidEntity` | `registerNoopEntity("cuboid", 1×1, MISC)`（无默认渲染器） | **`CuboidRenderer`**（在 `ModClientEventHandler.onRegisterRenderers` 手动 `registerEntityRenderer`） | **无**：`getTextureLocation()`=null；不读模型/动画 | 纯自绘：`RenderType` + `POSITION_COLOR` 三角形拼 6 个面（半透明立方体，白色 alpha 1.0，深度测试 always、关闭光照/贴图）；尺寸来自同步 `BOX_W/H/D`，`getRenderPos(pt)` 做插值 |
| `ShieldEntity` | `registerNoopEntity("shield", 1×1, MISC)`（无默认渲染器） | **`ShieldRenderer`**（手动注册） | **无**：`getTextureLocation()`=null；不读模型/动画 | 纯自绘：`POSITION_COLOR` 二十面体（球面细分 2 次，白色 alpha 0.4，深度 always、关闭光照/贴图）；半径来自同步 `RADIUS` |

补充结论：

- **`ClientRenderRegistry` 与这 4 个实体无关**：它只保存 `IGeoModelBehavior`/`IGeoLayerProvider`（按 EntityType 注册 Geo 生物的头部行为与发光层），调用点全部是生物（LongArmBeast/InfectionVillager/InfectionPlayer/DegenerateCow/InfectionSkeleton + 3 个 layer），4 个技能实体没有任何注册。
- `ModClientEventHandler` 在这 4 个实体上只做两件事：`onRegisterRenderers` 里手动注册 `CuboidRenderer`、`ShieldRenderer`；其余渲染器由 `CorruptionEntityManager.registerRenderers`（GeoBaseRender）与 `CorruptionProjectileManager.registerRenderers`（GeoProjectileRenderer / SkillProjectileRenderer）批量注册。
- 结论：**CuboidEntity / ShieldEntity / CorruptionDart / CorruptionPoolEntity 全部零资源依赖**（无贴图、无模型、无动画、无音效），迁移时只需要移植 4 个 Java 类 + 渲染器/空渲染器，不需要任何新 JSON/PNG；本批复制的资源只服务于下面 3 个 Geo 弹射物。

## 3. 三个 Geo 弹射物的资源映射（本批复制的用途）

| 实体 id | 类 | 碰撞箱 | geo | texture | animation | 渲染/备注 |
|---|---|---|---|---|---|---|
| `poison_arrow` | `PoisonArrowProjectile extends AbstractCorruptionProjectile` | 0.5×0.5 | `geo/poison_arrow.geo.json` | `textures/entity/poison_arrow.png` | `animations/poison_arrow.animation.json` | `GeoProjectileRenderer`（非 billboard）；命中施加 `RegisterEffect.CORRUPTION` |
| `acid_ball` | `AcidBallProjectile extends AbstractCorruptionProjectile implements IBillboardRenderable` | 0.5×0.5 | `geo/acid_ball.geo.json` | **`textures/item/acid_ball.png`**（复用物品贴图） | `animations/acid_ball.animation.json` | `GeoProjectileRenderer` 检测到 `IBillboardRenderable` 后按相机朝向做 billboard；命中施加 `RegisterEffect.FEAR` |
| `bone_spike` | `BoneSpikeEntity extends AbstractCorruptionProjectile` | 0.3×0.3 | `geo/bone_spike.geo.json` | `textures/entity/bone_spike.png` | `animations/bone_spike.animation.json` | `GeoProjectileRenderer`；命中走 `CorruptionSkillFx.impact`（服务端 dust+smoke） |

- 三者都实现 `IAutoRenderableProjectile`（`IGeoResources` 的 `model()/texture()/animation()`），corruption 在注册工厂里**显式** `setModel/setTexture/setAnimation`（不是按 id 自动拼路径），所以移植注册代码里必须写死上表路径（尤其 acid_ball 是 item 目录贴图）。
- 目标工程已有等价基础设施，可直接复用：`client/entity/IAutoRenderableProjectile`、`IGeoResources`、`client/entity/model/GeoProjectileModel`、`client/entity/render/GeoProjectileRenderer`、`overworld/registry/entities/base/HallProjectileManager`（`registerProjectile` → `ClientModEventHandler` 中 `consumeProjectileTypes` → 注册 `GeoProjectileRenderer`，见 `EntityTypeRegistry.registerProjectile` 第 61-70 行、`ClientModEventHandler` 第 56-62/127-130 行）。

## 4. 粒子 / 音效 / 贴图引用与缺口清单

### 4.1 本批 7 个技能相关实体的直接引用（逐个核对）

| 实体 | `RegisterParticles.*` 引用 | 自定义音效引用 | 贴图/模型/动画引用 | 备注 |
|---|---|---|---|---|
| CorruptionDart | **无** | 无 | 无 | 命中效果由发射方设置：`CORRUPTION`（BaseHostEntity/InfectionSkeleton）、`BLEEDING`（DegenerateCow）、`LYSIS`（DegenerateVillager），均已在目标 `CorruptionEffects`；`withPool()` 命中后会生成 `CorruptionPoolEntity`（依赖池实体移植） |
| CorruptionPoolEntity | **无** | 无 | 无 | 施加 `RegisterEffect.CORRUPTION` |
| CuboidEntity | **无** | 无 | 无 | — |
| ShieldEntity | **无** | 无 | 无 | — |
| BoneSpikeEntity | **无** | 无 | 本批已备（geo+anim+texture） | 命中特效走 `CorruptionSkillFx`（vanilla dust/smoke） |
| AcidBallProjectile | **无** | 无 | `textures/item/acid_ball.png`（已有） | 粒子为 vanilla `ParticleTypes.ITEM_SLIME`；施加 FEAR |
| PoisonArrowProjectile | **无** | 无 | 本批已备（geo+anim+texture） | 施加 CORRUPTION |

**结论：这 7 个技能实体没有任何 corruption 自定义粒子（`RegisterParticles.*`）或自定义音效依赖**；它们用到的粒子全是原版（`DustParticleOptions`、`LARGE_SMOKE`、`DAMAGE_INDICATOR`、`ITEM_SLIME`）。合并工程现有粒子（`blood`/`meat`/`explosion_bloody`/`ulcerated_meat`/`heart_lose`）对技能批次**完全够用，无缺口**。贴图缺口为 **0**（本批共处理 9 个源文件：8 个新复制 + 1 个 acid_ball 物品贴图已存在且同哈希；3 个 Geo 弹射物所需贴图全部就位）。

### 4.2 corruption 全量 vs 合并工程缺口（非本批实体直接依赖，供后续批次参考）

- **自定义粒子**：corruption 注册 9 个 —— `meat`、`degenerate_die_meat`、`degenerate_meat`、`bloody`、`blood`、`blood_splat`、`blood_fog`、`blood_splash`、`explosion_bloody`；合并工程现注册 5 个 —— `blood`、`meat`、`explosion_bloody`、`ulcerated_meat`、`heart_lose`。**缺 6 个**：`degenerate_die_meat`、`degenerate_meat`、`bloody`、`blood_splat`、`blood_fog`、`blood_splash`。它们主要由腐化生物死亡/受击/血腥事件（`BaseDegenerateEntity`、`HandleGore`、`CorruptionEffect` 等）引用，**不被本批技能实体引用**；源贴图在 `corruption/textures/particle/`（含 `blood0..5`、`bloodsplat0..5`、`blood_fog0..3`、`bloody.png`、`degenerate_meat.png`、`degenerate_die_meat.png` 等）。
- **自定义音效**：corruption 注册 10 个 —— `rip_body`、`degenerate_call_type1`、`degenerate_hurt_type1`、`body_hurt1`、`body_hurt2`、`attacker_hurt_1`、`attacker_hurt_2`、`attacker_living_1`、`attacker_living_2`、`overload`；合并工程只注册了 `rip_body`（另有 hall 的 `biome_music`）。**缺 9 个**（除 rip_body 外全部），仅被腐化生物音效定义与 KingBigSword（overload）引用，**不被本批技能实体引用**。
- **自定义状态效果**：本批实体/其弹体涉及 `CORRUPTION`（池、毒箭、同化种吐息）、`FEAR`（酸液球）、`BLEEDING`（腐化牛弹体）、`LYSIS`（腐化村民弹体）；合并工程 `CorruptionEffects` 已有 `corruption`/`lysis`/`miasma`/`sclerosis`/`bleeding`/`fear` 6 个 → **无缺口**。

## 5. 目标工程后续 Java 接入点（只做调研，未改源码）

1. **3 个 Geo 弹射物**：目标 `EntityTypeRegistry.registerProjectile(name,w,h,factory)` 已存在（内部 `HallProjectileManager` → `GeoProjectileRenderer`）；实体需实现目标包 `org.bytechen.rottenruins.client.entity.IAutoRenderableProjectile`，并在工厂里显式 setModel/setTexture/setAnimation 指向 §3 路径。注意目标已迁 GeckoLib 4.8.4，corruption 的 `AbstractCorruptionProjectile` 用的是旧 GeckoLib API，需要按目标的 `IAutoRenderableProjectile`/`GeoEntity` 重写（本任务未改 Java）。
2. **CorruptionDart / CorruptionPoolEntity**：目标工程缺 `SkillProjectileRenderer`（空渲染器类）；可补一个空 `EntityRenderer`（`render()` 空、`getTextureLocation` 返回占位贴图），并用 `registerNoopEntity` + 手动注册的方式挂上。`CorruptionSkillUtil.spawnPoolAt` 在目标工程当前是 TODO（注释明确"批次 2：等 CorruptionPoolEntity 移植后接回"，见 `corruption/skill/CorruptionSkillUtil.java` 第 488-496 行），本批资源迁移与它对应。
3. **CuboidEntity / ShieldEntity**：需移植 `client/rend/CuboidRenderer.java`、`client/rend/ShieldRenderer.java`（纯自绘、零资源），并在目标 `ClientModEventHandler.onRegisterRenderers` 中用 `registerNoopEntity` + `event.registerEntityRenderer(...)` 注册（同 corruption 写法）。目标 `EntityTypeRegistry.registerNoopEntity` 提供 updateInterval/clientTrackingRange 重载，可按技能范围调大追踪距离。
4. 目标已有 `EntityManager.consumeSkillEntityTypes()`（走 `GeoEntityRender`），只适用于 `IAutoRenderableEntity`；不要用它注册这 4 个零 Geo 技能实体。

## 6. 校验与工作区状态

- 8 个新文件：复制后 SHA-256 与源 100% 一致（见 §1）；3 个 geo、3 个 animation JSON 解析通过；2 张 PNG 签名有效。
- `textures/item/acid_ball.png`：目标已存在且 SHA-256 与源一致 → 不覆盖、不备份；该文件在批次 1 已提交。
- 未改任何 Java、未跑 Gradle；本批 `git status` 仅新增上述 8 个资源文件 + 本报告（同目录另有并行任务正在新增 corruption/skill Java 类与 `CorruptionDamageUtil`，不属于本任务）。
- 与批次 1 的衔接：`docs/merge/port-assets.md` 记录的 skill 跳过项（acid_ball/bone_spike/poison_arrow）已在本次补齐；批次 1 仍跳过的 avila/heavy_guard 等 KING 内容维持不搬。
