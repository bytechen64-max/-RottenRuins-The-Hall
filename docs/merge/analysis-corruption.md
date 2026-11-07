# corruption 工程合并前盘点（corruption → rotten_ruins）

> 分析范围：corruption 工程全部源码/资源（只读），并与 Hall 工程、infcore 前置源码做对应物比对。
> 本文所有路径均相对各自工程根；三个工程根分别为：
>
> | 代号 | 绝对路径 |
> |---|---|
> | `corruption/` | `C:\Users\chy20\Desktop\desktop\Project\corruption\corruption` |
> | `Hall/` | `C:\Users\chy20\Desktop\desktop\Project\RottenRuinsSplendiding` |
> | `infcore/` | `C:\Users\chy20\Desktop\desktop\Project\InfectionCoreFramework` |
>
> 本次盘点**未执行任何构建/runClient/runData**，未修改 corruption 任何文件；只新增本报告。

---

## 0. 结论摘要（先看这里）

1. **两个工程同源、但发展方向不同**：corruption 是「完整但自研框架」的独立模组（28,473 行 Java / 268 文件 / 资源 3.0MB），Hall 是「基础设施升级版」的模组（54,451 行 / 390 文件 / 资源 3.2MB）。Hall 侧**已经存在** corruption 大部分基础设施的升级替代品：方块扩散与群系处理器、数据驱动进化、物品辉光/描边、范围攻击/粒子工具、威胁值（原威慑值）、键位同步网络包等。
   - 因此合并的**主体工作是搬「内容」**（24 个生物、26 个方块、6 个效果、89 个物品、29 套 geo/28 套动画、10 个音效、22 张掉落表），而**不是搬框架**；重复框架建议整类丢弃。
2. **三个硬阻塞（必须在动工前定方案）**：
   - **GeckoLib 版本冲突**：corruption 用 CurseForge 文件 7553267 = **GeckoLib 4.8.3**（`implementation`，不 jarJar）；Hall 用 Maven 坐标 **4.7.1**（`compileOnly` + `jarJar [4.7.1,4.8)`）。合并后必须统一版本（升 Hall 到 4.8.3 或验证 corruption 代码在 4.7.1 下编译/运行）。
   - **寻路 AI / Brain 深度耦合**：`AbstractCorruptionEntity`（1,087 行）自带 `Brain`+自定义 `MemoryModule ai_state`+`SmartMoveBrain`；21 个生物类通过 `SmartMoveGoal`/`SmartTargetGoal` 组装移动/索敌。按既定约束「corruption 寻路 AI 与 brain 不搬、索敌改 infcore 阵营判断」，这 21 个生物的 goal 段**全部要重写**为 Hall 的 `tickChase + ChaseLockGoal + InfectedTargetGoal`，并删除 `MemoryModuleTypeRegistry` / `brains/` / `SmartMoveGoal`(23KB) / `SmartTargetGoal`(12KB) / `JumpPointPathfinder`(15.6KB)。
   - **节点系统**（corruption 独有，Hall 无对应）：`CorruptionNodeManager`(226 行)+`CorruptionNode`/`NodeQueryResult`+`NodeConfig`+`NodeCommands`+`DebugItem`+SavedData+同步，是腐化扩散、刷怪阶段、生物属性加成、群系扩散、调试工具的唯一「感染强度」来源。**搬不搬节点系统直接决定 10~15 人日与整个玩法闭环**；建议作为第一个需要 Lead 拍板的决策点。
3. **注册 ID 全量迁移不可避免**：`corruption:*` → `rotten_ruins:*`（Hall 的 `hall:*` 同样要迁）导致老存档物品/方块/实体/进度全部失效（Forge 1.20.1 无内建 ID 重映射）。详细风险与可选缓解见第 6 节。
4. **KING 系列（王庭）确认可整段丢弃**：5 个物品 + 5 个方块 + `HeavyGuardEntity`（已注册但无蛋/无语言/无掉落）+ `AvilaEntity`（**未注册**）+ `AvilaBossBarRenderer` + heavy_guard 的 geo/anim/贴图，均为未完成原型；Hall 已有自己的「王庭」物品/方块/生物体系（`hall_bone_fragments`、`hall_tendon`、`hall_stone` 等），语义重叠见 2.7。
5. **语言/数据缺口清单已核实**：`bleeding` / `fear` 两个效果没有语言键（且 `bleeding` 连图标贴图都没有）；5 个 KING 方块与 `spread_infection_wood` 无语言；15 个实体无语言（含 `infection_pig`、`infection_skeleton`、`heavy_guard` 与全部技能/投射物实体）；3 个字幕键缺失；4 个 `.ogg` 音效有文件但无字幕键。数据侧：26 个方块**没有任何方块掉落表**（挖了不掉落）、`poisoned_saber` **没有配方**、`infection_bat` 与 `heavy_guard` **没有实体掉落表**、模板池 3 个 JSON 但**没有对应 NBT 结构文件**（`CorrupTionTreeGrower` 也无任何调用方）、`ConfigDrivenSpawnModifier` 已注册序列化器但**没有数据 JSON**（整条自动生成链路当前不生效）。

---

## 1. 模组元信息与 build 差异

### 1.1 基本盘对比

| 项目 | corruption | Hall | 合并建议 |
|---|---|---|---|
| 工程根 | `C:\...\corruption\corruption` | `C:\...\RottenRuinsSplendiding` | 以 Hall 为宿主 |
| `rootProject.name` | `corruption` | `RottenRuinsSplendiding` | 可改为 `rotten_ruins` |
| modId | `corruption` | `hall` | 目标 `rotten_ruins`（用户已定） |
| 显示名 | `Rotten Ruins` | `RottenRuins:The Hall` | 建议 `RottenRuins` |
| 版本 | `0.6.0` | `1.0.0Beta` | 合并后从 `1.1.0` 起 |
| group | `com.bytechen` | `org.bytechen` | 建议统一 `org.bytechen`（infcore 已是该 group） |
| 作者 | `ByteChen,FateIlya` | `ByteChen` | 合并保留两人 |
| 描述 | `a horror-themed infection mod` | `A Minecraft mod about rotten ruins and hall` | 重写 |
| 主类 | `com.corruption.Corruption` | `org.bytechen.hall.HallMod` | 合并主类 + 注册顺序重排 |
| Java | 17 | 17 | 一致 |
| Forge | `47.4.18` | `47.4.20` | 统一 47.4.20；注意 infcore 源码写的是 47.4.21（见 1.3） |
| MC | 1.20.1 | 1.20.1 | 一致 |
| 映射 | **official**（见 1.2） | **official** | 一致 |
| jarJar | 仅 cloth-config | cloth-config + geckolib 4.7.1 | 合并后按统一后的 geckolib 版本重算 |
| 硬前置（mods.toml） | cloth_config、geckolib | cloth_config、geckolib、**infcore**、**curios** | 合并后 4 个全要 |
| 可选依赖 | JEI（compileOnly/runtimeOnly） | JEI（同） | 一致 |
| Java 文件 / 行数 | 268 / 28,473 | 390 / 54,451 | 合并后预计 ~75k 行（含删重可回到 ~65k） |
| main 资源 | 292 文件 / 1.5MB | 325 文件 / 2.9MB | 有重复需去重 |
| generated 资源 | 218 文件 / 178KB | 375 文件 / 190KB | corruption 侧需并入 Hall datagen |

### 1.2 build.gradle / gradle.properties 差异明细

| 项 | corruption | Hall | 影响 |
|---|---|---|---|
| `plugins` | forgegradle `[6.0.16,6.2)` + **parchment librarian `1.+`** | 同左 | 都加载了 parchment 插件但**都没使用**（见下一行） |
| `minecraft.mappings` | **硬编码 `channel: 'official', version: 1.20.1`**；`gradle.properties` 里却写着 `mapping_channel=parchment` / `mapping_version=2023.09.03-1.20.1`（无效残留） | 同样硬编码 `official`；`gradle.properties` 写 `mapping_channel=official` | **任务描述中的「parchment vs official」实际不成立**：两边运行的都是 official 映射。合并时删掉 parchment 配置/插件即可，无映射迁移成本 |
| accessTransformer | 无 | `src/main/resources/META-INF/accesstransformer.cfg`（当前为空，保留占位） | 合并保留 Hall 的 |
| JPMS 修补 | 无 | `patchModJars` / `copyPatchedMods` 系列任务（剥离 cloth/geckolib 的 `module-info.class`） | 合并保留 Hall 的；若 geckolib 升 4.8.3 需重验 |
| refmap 修补 | 无 | `copyRefmap` 任务（MixinGradle 0.7-SNAPSHOT 不复制 refmap 的绕行） | 合并保留 |
| infcore 前置方式 | **完全不依赖 infcore** | 工程内 Maven 镜像 `Hall/libs/org/bytechen/infcore/0.1.2` + `syncInfcore` 任务；`settings.gradle` 明确说明「不能 includeBuild」（47.4.21 vs 47.4.20 的 FG 缓存互相覆盖） | 合并后 corruption 侧要改为使用 infcore，依赖声明照抄 Hall |
| Curios | 无 | `compileOnly/runtimeOnly` + mods.toml mandatory | 合并后 corruption 内容不强制需要，但保留前置 |
| geckolib 坐标 | `implementation fg.deobf("curse.maven:geckolib-388172:7553267")`（= **4.8.3**，已从 gradle 缓存中的 jar `META-INF/mods.toml` 核实）；**不 jarJar** | `compileOnly + patchModuleInfo`，坐标 `software.bernie.geckolib:geckolib-forge-1.20.1:4.7.1`，`jarJar [4.7.1,4.8)` | **冲突点**：不统一版本则编译/运行至少一边失败 |
| cloth-config | `implementation` + jarJar | `compileOnly`+`patchModuleInfo`+jarJar，版本同为 12.0.109 | 一致 |
| JEI | `mezz.jei:jei-1.20.1-forge:15.2.0.27` compileOnly+runtimeOnly | 同版本 | 一致 |
| 额外任务 | `sourcesJar` / `devJar` / `publishToMavenLocal` | 同 + `syncInfcore` / 模块修补 | 合并保留 Hall 全套 |

> 结论：**构建差异本身不大**（Forge 47.4.18→47.4.20 是补丁级；映射同为 official；group 只是命名空间）；真正麻烦的是 **GeckoLib 4.8.3 vs 4.7.1** 与 **包名/MODID 全局重命名**。

### 1.3 依赖版本联动提示

- Hall `settings.gradle` 记录：`InfectionCoreFramework/gradle.properties` 的 `forge_version = 47.4.21`，与 Hall `47.4.20` 不同，**不能** `includeBuild` 复合构建。合并后若仍采用「libs 内 Maven 镜像 + syncInfcore」方案则无影响；若想改复合构建需先把三方 Forge 版本统一到同一补丁号。
- corruption 的 `mapping_channel=parchment` 是历史残留，`build.gradle` 已强制 official；合并时建议清理 `gradle.properties` 注释与 `settings.gradle` 的 ParchmentMC 仓库。

### 1.4 Mixin 清单对比

corruption `corruption/src/main/resources/corruption.mixins.json`（`plugin = com.corruption.mixin.ItemGlintMixinPlugin`）与 Hall `src/main/resources/hall.mixins.json`（`plugin = org.bytechen.hall.mixin.ItemGlintMixinPlugin`）。

| corruption mixin | 作用 | Hall 是否有对应 | 合并建议 |
|---|---|---|---|
| `MixinAnimationController` | GeckoLib `AnimationController` 修补（remap=false） | ✅ 同名同目标 | 丢弃 corruption 版 |
| `MixinBiomeManager` | `BiomeManager.getBiome` HEAD 注入，按节点+脏区块强制返回腐化群系 | ❌ | **随扩散引擎一起丢弃**；Hall 用「真实写入区块群系 + 包同步」路线，不需要此 hack |
| `MixinLivingEntity` | 拦截 `removeEffect` / `removeAllEffects`，实现 `NonRemovable` 效果（恐惧）不可被清除 | ❌ | **要移植**（恐惧效果依赖它） |
| `accessor.AccessorLivingEntity` | `LivingEntity.DATA_HEALTH_ID` + `getActiveEffects()` | ❌ | **要移植**；`ModUtils.getDataHealthID/setDataHealthID/forceAddEffect/addOrStackEffect` 依赖它 |
| `accessor.AccessorDisplay` / `accessor.ItemDisplayAccessor` | Display 实体字段访问 | ❌ | 无任何使用方，**丢弃** |
| `GameRendererAccessor` / `GameRendererMixin` / `GameRendererOculusMixin` / `ItemInHandRendererMixin` / `ItemRendererMixin` / `LevelRendererEmbeddiumMixin` | 手持物品辉光/描边 FBO 管线 + 光影兼容 | ✅ Hall 全部有（且更新） | 全部丢弃 corruption 版 |
| `accessor.AccessorRenderStateShard` | RenderStateShard 常量访问 | ✅ Hall 版同目标、方法更多（`splendiding$...`） | 丢弃 corruption 版；把 `CorruptionShaders` 里用到的 `corruption$...` 调用改 Hall 命名 |
| `ItemGeoInitializeMixin` | 物品 Geo 初始化 | ❌ | **不在 mixins.json 里**，是死文件，丢弃 |
| `ItemGlintMixinPlugin` | 按 classpath 选择 Oculus/Embeddium 路径 | ✅ Hall 有同名插件 | 丢弃 corruption 版（同名类不同包，两个同时存在会冲突） |

Hall 额外拥有 corruption 没有的：`CosmicAfterLevelMixin`、`CreativeModeInventoryScreenMixin`、`CrimsonVowBlockMixin`、`MixinItemRendererCosmic`、`MixinItemRendererTwitch`、`OutlineReplayMixin`、`ScreenShakeMixin`、`accessor.AccessorAbstractContainerScreen`、`accessor.InvokerHandTransforms`。

### 1.5 网络通道与包

| 项 | corruption | Hall |
|---|---|---|
| 通道 | `corruption:main`（SimpleChannel，`1.0`，双向 `s->true`） | `hall:main`（同风格） |
| 注册包数 | 8：`UpdateBiomePacket`、`UniversalPacket`、`PacketSyncKeyframe`、`CParticleSpawnPacket`、`GorePacket`、`EffectSyncPacket`、`PacketSyncSaveData`、`PacketSyncCapability` | 16+：UniversalPacket/PacketActionRegistry、PacketSyncKeyframe、EffectSyncPacket、MagicSync、BloodBurstPacket、MeatBurstPacket、HeartLosePacket、AnomalySyncPacket、VoidSwordStrikePacket、ManaSyncPacket、MagicPoolSyncPacket、C2SDifficultySelectPacket、S2COpenDifficultySelectPacket 等 |
| 合并处置 | 只保留 4 个：`CParticleSpawnPacket`（假死/爆体粒子）、`GorePacket`（血块飞溅）、`EffectSyncPacket`（闪电/立方体特效，两个工程同名但 payload 类型不同，需以 Hall 版为准并扩充 typeId）、`PacketSyncKeyframe`（c2s 关键帧攻击，corruption 有、Hall 无同名）；`UniversalPacket`/`PacketActionRegistry`/`NetworkHelper` 用 Hall 版；`UpdateBiomePacket` 随扩散引擎删除；`PacketSyncSaveData`/`PacketSyncCapability` 改接 Hall 的 capability 同步机制（Hall `CapabilityRegistry` 有 `getId/getById` 反查，corruption 只有 `getId`） |

### 1.6 Config 类与生成文件

corruption 用 Cloth Config + Jankson 序列化，7 个配置类（`corruption/src/main/java/com/corruption/config/`）：

| 配置类 | `@Config(name)` → 实际文件 | 内容 | 合并建议 |
|---|---|---|---|
| `RrsCommon` | `corruption/RrsCommon` → `config/corruption/RrsCommon.json5` | 初始演化值/初始难度 | 并入 Hall `SplendidingConfig` 或 infcore `Difficulty` |
| `NodeConfig` | `corruption/NodeConfig` → `config/corruption/NodeConfig.json5` | 节点阶段阈值、初始半径/强度、叠加上限、群系扩散阈值、生长速率、每点强度生命/护甲加成 | **随节点系统决策**：搬则新类；不搬则删 |
| `CorruptionSpawnConfig` | `corruption/EntitySpawnConfig` → `config/corruption/EntitySpawnConfig.json5` | `preventVanillaSpawnInInfectedArea` + `spawnGroups[]`（entityId/interval/count/biomes/position/light/dimension/phase/enabled/extraNbt/distance/maxConcurrent/ignoreNode/spawnWeight/mobCategory） | 改用 infcore/原版 spawn placement + 简化配置（字段与 infcore 能力对齐） |
| `AssimilationConfig` | `corruption/AssimilationConfig` | 同化转换规则 from→to、演化值、成功率、条件（NOT_BABY/HAS_TAG 等）、成功后动作 | **改数据驱动** `data/<ns>/infcore_evolution/*.json`（infcore 已有同构能力，Hall `EvolutionDataProvider` 是现成范例） |
| `TargetWhitelistConfig` | `corruption/TargetWhitelist` | `modidWhitelist`：这些 modid 的生物不被主动索敌 | 由 infcore `ITargetFilter` 或 Hall `ThreatHelper`/faction 过滤替代（建议保留一份配置语义） |
| `FleshbedRecipeConfig` | `corruption/FleshbedRecipe` | `input_item|output_item|biomassRequired` 字符串表 | 若搬血肉温床则保留 |
| `ShaderConfig` | `corruption/ShaderConfig`（**仅客户端注册**） | `use_shader` | 与 Hall `SplendidingConfig` 的 shader 开关合并 |

Hall 现有 2 个配置类：`hall/SplendidingConfig`（29KB，含渲染/难度/魔法/技能等大量项）与 `hall/MeteorConfig`；`ConfigHelper` 自带 `migrateSplendidingConfig()` 版本迁移（很稳，可作为合并后配置迁移的模板）。另注意 Hall `run/config` 里还有历史遗留 `splendiding/SplendidingConfig.json5`。

> 合并后路径将变成 `config/rotten_ruins/...`：**老 corruption 用户的 7 个 json5 设置全部失效**（详见第 6 节）。

### 1.7 Creative Tab 与 Datagen Provider

| 项 | corruption | Hall |
|---|---|---|
| Creative tab | `corruption_table`（DeferredRegister 注册，id = `corruption_table`），标题 `itemGroup.corruption.main` = 「腐朽之疫」；`displayItems` 先手动按分组添加、再 `ForgeRegistries.ITEMS` 全量兜底收集 | `hall:…`；`SectionedCreativeModeTab`（9KB）+ `CreativeTabDividerRenderer`（自定义分区隔断渲染）+ `itemGroup.hall.main.section.*`（blocks/creatures/gear/materials/misc） |
| 合并建议 | 丢弃 corruption `RegisterTab`，把内容按分区塞进 Hall 的分区创造栏 | 保留 Hall 版，新增「腐化生物/腐化方块/腐化材料」分区 |
| Datagen | `DataGenEvent` 注册 11 个 provider：BlockState、Item（含 4 个 effect 图标模型，实际为孤儿产物）、LangCN、LangEN、Sound、Particle、WorldGen、BlockTag、Recipe、Loot（内部 `EntityLootProvider`）、Advancement | `DataGenEvent` 注册 15 个：BlockState、Item、LangCN、LangEN、Sound、Particle、**EffectSprite**、**Evolution（infcore）**、**BlockSpread（infcore）**、HallWorldGen、BlockTag、ItemTag、Recipe、Loot |
| 合并建议 | 只把「内容定义」并入 Hall 的 provider；corruption 的 `WorldGenData` 并入 `HallWorldGenData/HallBiomeData`；效果图标改走 Hall 的 `EffectSpriteData` | 保留 Hall 骨架 |

---

## 2. 注册表精确 ID 清单

> 所有 ID 均为 `corruption:` 命名空间（合并后统一改为 `rotten_ruins:`）。中文名取自 `corruption/src/generated/resources/assets/corruption/lang/zh_cn.json`；「用途/状态」为源码核实结论。

### 2.1 物品（共 89 个注册项 = 63 个普通物品 + 26 个方块物品）

#### 材料/感染值物品（`InfectionItem`，携带 `Biomass` 值，10 个）

| ID | 中文名 | 用途/状态 |
|---|---|---|
| `infection_bone` | 感染的骨头 | 基础材料（Biomass 3），粗制工具主料 |
| `chitin_polymer` | 聚合甲壳 | 材料（5），由活体肉块+硬质碎片合成 |
| `live_meat` | 活体肉块 | 材料（13），同化种掉落 |
| `degenerate_heart` | 腐化之心 | 材料（10），腐化种稀有掉落 |
| `hard_fragments` | 硬质碎片 | 材料（1），通用掉落 |
| `host_carapace` | 同化种甲壳 | 材料（5） |
| `host_tooth` | 被同化的牙齿 | 材料（2） |
| `host_meat` | 被同化的肉 | 材料（10） |
| `long_arm_beast_head` | 长臂兽头颅 | 材料（25），长臂兽稀有掉落 |
| `long_arm_beast_tooth` | 长臂兽骨齿 | 材料（5） |

#### 普通材料/杂项（13 个）

| ID | 中文名 | 用途/状态 |
|---|---|---|
| `syringe` | 注射器 | 采集/注射器基底，有配方（铁锭+玻璃板） |
| `syringe_bloody_concentrate` | 浓缩血液注射器 | 把人/生物转化为感染态，核心转化道具（依赖 AssimilationMapManager） |
| `acid_ball` | 酸液球 | 材料/投射物展示；酸弹实体另注册 |
| `acid_sac` | 酸囊 | 材料（冲击者掉落） |
| `acid_bomb` | 酸性炸弹 | 普通 Item，**无功能实现**（未完成） |
| `chip_null` | 芯片插件[空] | 无功能，仅 JEI 文案（未完成） |
| `chip_scan` | 芯片插件[扫描] | 同上 |
| `chip_extract` | 芯片插件[提取] | 同上 |
| `chip_enhanced` | 芯片插件[强化] | 同上 |
| `vice_template` | 罪恶模板 | 七罪物品的「模板」，有配方（钻石+硬质碎片） |
| `king_hearthstone` | 王庭炉石 | **KING 系列，不搬** |
| `king_bone` | 王庭骨 | **KING 系列，不搬** |
| `king_shell` | 王庭甲壳 | **KING 系列，不搬** |
| `king_fiber` | 王庭纤维 | **KING 系列，不搬** |

#### 文件系统（2 个）

| ID | 中文名 | 用途/状态 |
|---|---|---|
| `file` | 文件 | 空实现（`openImageScreen` 全空、`getContentConfigPath` 返回 null），只有配方 |
| `file_folder` | 文件夹 | 有完整 20 格容器逻辑（`FileInventoryImpl`+NBT），可「采集模式」收纳文件；**无 GUI 界面** |

#### 七宗罪（7 个，`vice_template` 已在上面「普通材料/杂项」计数）

| ID | 中文名 | 状态 |
|---|---|---|
| `envy` | 嫉妒之罪 | `SevenSinsItem` 仅携带 enum 值 + 辉光注册，**无实际效果**；均无配方 |
| `gluttony` | 暴食之罪 | 同上 |
| `greed` | 贪婪之罪 | 同上 |
| `lust` | 色欲之罪 | 同上 |
| `pride` | 傲慢之罪 | 同上 |
| `sloth` | 懒惰之罪 | 同上 |
| `wrath` | 暴怒之罪 | 同上 |

#### 工具/武器（10 个）

| ID | 中文名 | 用途/状态 |
|---|---|---|
| `poisoned_saber` | 注毒军刀 | `SwordItem` 子类，**无注毒逻辑**、**无配方**（成就有「粗制剑升级为注毒军刀」但缺配方 JSON） |
| `crude_axe` / `crude_hoe` / `crude_sword` / `crude_shovel` / `crude_pickaxe` | 粗制斧/锄/剑/铲/镐 | 早期工具，均有配方（聚合甲壳+感染的骨头+活体肉块） |
| `king_big_sword` | 王庭大剑 | **KING，不搬**；有 8 环冲击波技能（`performShockwave` 15 伤害/100 tick） |
| `red_dwarf_bow` | 红矮星 | 弓箭类武器；发射 `RedDwarf*` 技能实体，是 corruption 唯一完整远程武器，**建议搬** |
| `debug_item` | 感染调试器 | 节点系统调试工具（查询/增删节点、调链接等级），**随节点系统决策** |

#### 刷怪蛋（21 个）

`infection_player_spawn_egg`、`infection_zombie_spawn_egg`、`infection_villager_spawn_egg`、`infection_cow_spawn_egg`、`infection_sheep_spawn_egg`、`infection_pig_spawn_egg`、`infection_chicken_spawn_egg`、`infection_wolf_spawn_egg`、`infection_bat_spawn_egg`、`infection_skeleton_spawn_egg`、`long_arm_beast_spawn_egg`、`shocker_spawn_egg`、`degenerate_pig_spawn_egg`、`degenerate_zombie_spawn_egg`、`degenerate_villager_spawn_egg`、`degenerate_cow_spawn_egg`、`groveler_spawn_egg`、`humus_column_spawn_egg`、`summoning_pillar_spawn_egg`、`bearer_spawn_egg`、`attacker_spawn_egg`
（中文名均为「对应实体名 + 刷怪蛋」。缺 3 个：`original_bug`、`infection_bone_ruins`、`heavy_guard` 无刷怪蛋。）

#### 方块物品（26 个）

见 2.2 方块表——注册方块时同步用 `RegisterItem.ITEMS` 注册了同名 `BlockItem`。

### 2.2 方块与方块实体

`corruption/src/main/java/com/corruption/overworld/registry/RegisterBlock.java`（26 个方块，全部带 BlockItem）：

| ID | 中文名 | 类/行为 |
|---|---|---|
| `spread_dirt` | 腐染泥土 | `CorruptionDirtBlock extends AbstractSpreadingBlock`；扩散核心源，`randomTicks` |
| `spread_infection_wood` | 无语言键（缺失） | `CorruptionInfectionWoodBlock`，轴向 log 类扩散方块 |
| `spread_infection_leaves` | 腐染树叶 | `CorruptionInfectionLeavesBlock`，带 `DISTANCE`/`PERSISTENT`，自行处理 decay |
| `spread_stone` | 腐染原石 | `CorruptionStoneBlock`，扩散源 |
| `corruption_grass` | 腐染矮草 | `TallGrassBlock` 子类（非扩散方块） |
| `variable_height_crop` | 腐染经络 | `CorruptionVariableHeightCropBlock`：`AGE_25` + 自定义 `StemSection`（bottom/middle/top），可变高度 |
| `corruption_trachea` | 呼吸道 | `CorruptionAttachedBlock`（`AttachType.GRASS`，六向附着） |
| `infection_cobblestone_block` | 腐染圆石 | 普通装饰方块 |
| `spiracle_block` | 活体气门 | 普通装饰方块（出现在 `minecraft:mineable/pickaxe`、`needs_stone_tool` 标签） |
| `king_stone` / `king_dirt` / `king_brick` / `king_stone_dirt` / `king_leaves` | 均无语言键 | **KING 系列方块，不搬**（有贴图/模型/方块状态，但无配方、无语言、无用途） |
| `connect_block` | 结缔块 | 普通装饰方块 |
| `decidual_tissue_block` | 蜕膜组织 | 普通装饰方块 |
| `host_remains_small` / `host_remains_medium` / `host_remains_large` | 同化种小/中/大型遗骸 | `Remains`（六面朝向、附着式残骸，实体死亡时由 `CorruptionRipBodyUtil` 放置） |
| `degenerate_remains_small` / `medium` / `large` | 腐化种小/中/大型遗骸 | 同上 |
| `refactored_remains_small` / `medium` / `large` | 重构种小/中/大型遗骸 | 同上 |
| `fleshbed` | 血肉温床 | `FleshbedBlock implements EntityBlock` + `FleshbedBlockEntity`：方块实体持有「培养物品+生物质」，`FleshbedRecipeConfig` 驱动输入→输出，`use()` 交互 |

方块实体（`RegisterBlockEntities`）：

| ID | 中文名 | 说明 |
|---|---|---|
| `fleshbed` | 血肉温床 | 唯一 BlockEntity；`saveAdditional/load/getUpdateTag` 完整；无 `ticker`（靠 `use` 驱动） |

> ⚠️ **26 个方块没有任何方块掉落表**（`data/corruption/loot_tables/` 下只有 `entities/` 22 个），1.20.1 无 loot table 的方块被破坏后**不掉落任何物品**；是否搬过来后补 loot 表需在方案里明确。

### 2.3 实体（34 个注册 + 1 个未注册孤儿）

`corruption/src/main/java/com/corruption/overworld/registry/EntityTypeRegistry.java`：

#### 生物（24 个，`MobCategory.MONSTER`，全部继承 `AbstractCorruptionEntity`）

| 族 | ID | 中文名 | 基类 | 尺寸(W×H) | 关键技能/特征 |
|---|---|---|---|---|---|
| original | `original_bug` | 原虫 | BaseOriginalEntity | 0.6×0.8 | C11 酸液喷吐；血量极低 |
| host | `infection_zombie` | 同化僵尸 | BaseHostEntity | 0.6×1.95 | B1 腐化唾液吐息 / B10 濒死骨爆 |
| host | `infection_player` | 同化玩家 | BaseHostEntity | 0.6×1.95 | B10/B11/B12；**在线皮肤下载**（按玩家名拉取 → 动态纹理） |
| host | `infection_cow` | 同化牛 | BaseHostEntity | 0.8×1.2 | B9 尸群践踏（3 同伴）/B12 吸血 |
| host | `infection_sheep` | 同化羊 | BaseHostEntity | 0.8×1.2 | B10 骨爆(12)/B9 |
| host | `infection_pig` | 同化猪 | BaseHostEntity | 0.8×1.2 | B9/B12/B11 血肉牵引 |
| host | `infection_bat` | 同化蝙蝠 | BaseHostEntity | 0.6×1.95 | B13 蝙蝠夜袭（瞬移背后）、`CorruptionFlyGoal` 飞行 |
| host | `infection_skeleton` | 同化骷髅 | BaseHostEntity | 0.6×1.99 | B6 腐化箭雨 / B7 射速强化；实现 `RangedAttackMob` |
| host | `infection_bone_ruins` | 骷髅遗骸 | BaseHostEntity | 0.6×1.99 | 蓄毒 3 秒后自爆；无移动 goal（仅索敌） |
| host | `infection_villager` | 同化村民 | BaseHostEntity | 0.6×1.99 | B8 同化传播叠层 / B12 |
| host | `infection_wolf` | 同化狼 | BaseHostEntity | 1.15×0.95 | B2 感染嚎叫（群体 buff）/B3 扑咬锁喉 |
| host | `infection_chicken` | 同化鸡 | BaseHostEntity | 0.5×0.5 | B4 噤声分裂（受击概率裂小体） |
| degenerate | `degenerate_cow` | 腐化牛 | BaseDegenerateEntity | 1.35×1.35 | C4 屠戮者暴怒（残血狂暴）/C5 血肉偿还（割血射血爆弹） |
| degenerate | `degenerate_pig` | 腐化猪 | BaseDegenerateEntity | 1.35×1.35 | C3 腐化猪蓄力冲锋（三段） |
| degenerate | `degenerate_villager` | 腐化村民 | BaseDegenerateEntity | 0.6×2.6 | C1 酸液瘤气弹（落点酸池）/C2 硬化召唤（血肉墙） |
| degenerate | `degenerate_zombie` | 腐化僵尸 | BaseDegenerateEntity | 0.6×1.95 | 最基础腐化近战 |
| deteriorated | `groveler` | 匍匐者 | BaseDeterioratedEntity | 1.2×1.8 | C10 地底突袭（钻地→目标脚下破土斩击） |
| refactored | `long_arm_beast` | 长臂兽 | BaseRefactoredEntity | 1.65×3.9 | C7 钩刺拖拽；死亡辐照领域；长臂头部骨骼定制 |
| refactored | `shocker` | 震撼者 | BaseRefactoredEntity | 1.5×2.8 | C6 过载脉冲（1.8s 蓄力全向红雷环） |
| refactored | `attacker` | 袭击者 | BaseRefactoredEntity | 2.0×4.5 | C8 打击后撤（突进打完跳开） |
| refactored | `bearer` | 孕育者 | **BaseLinkedEntity** | 1.0×1.0 | 吞噬同类增长（`devourValue`）→ `BearerManager` 触发进化为长臂兽/震撼者；`FollowGoal` 跟队 |
| linked | `humus_column` | Ⅰ阶腐植柱 | BaseLinkedEntity | 0.9×1.65 | 每 tick 给最优盟友增益/随机敌人减益（大量原版+模组效果）；C9 死亡辐照领域；不可移动 |
| linked | `summoning_pillar` | Ⅰ阶召唤柱 | BaseLinkedEntity | 0.9×1.65 | C12 群体召唤（12s 冷却、区域人口上限 10）；C9 |
| king | `heavy_guard` | **无语言键** | AbstractCorruptionEntity | 0.9×1.95 | **KING 系列，不搬**；667 血/15 攻，未完成（无蛋、无掉落、无 AI goal） |
| king | （`AvilaEntity` 未注册） | — | AbstractCorruptionEntity | — | **孤儿类**：飞行 Boss、两阶段、护盾实体、技能循环；geo/anim/贴图/`AvilaBossBarRenderer` 全部存在但从不注册 |

#### 技能/投射物实体（10 个，`MobCategory.MISC`）

| ID | 中文名 | 类 | 说明 |
|---|---|---|---|
| `poison_arrow` | — | `PoisonArrowProjectile`（GeckoLib 投射物） | 同化骷髅箭；引用 `animations/poison_arrow.animation.json` **但实际文件是 `poison_arrow.json`（资源缺失）** |
| `acid_ball` | — | `AcidBallProjectile`（GeckoLib） | 酸球弹体 |
| `bone_spike` | — | `BoneSpikeEntity`（GeckoLib 锥形骨刺） | C10 濒死骨爆弹射物 |
| `cuboid` | — | `CuboidEntity`（通用方块碰撞箱实体，支持定向缓动/抛射两种运动+逐 tick 命中回调） | 技能判定/视觉载体（Avila 等） |
| `shield` | — | `ShieldEntity`（二十面体护盾，跟随主人、伤害转入治疗） | 技能载体 |
| `red_dwarf_skill` | — | `RedDwarfSkillProjectile` | 红矮星弓普通弹 |
| `red_dwarf_special_skill` | — | `RedDwarfSpecialSkillProjectile` | 红矮星弓特殊弹 |
| `red_dwarf_magic_circle` | — | `RedDwarfMagicCircleEntity` | 红矮星弓法阵 |
| `corruption_pool` | — | `CorruptionPoolEntity`（地面腐蚀液池，周期伤害+效果，客户端粒子渲染） | 技能落点区域 |
| `corruption_dart` | — | `CorruptionDart`（通用腐蚀弹体：吐息/箭雨/喷射/酸弹共用，颜色/伤害/重力/命中效果/腐蚀池全部运行时配置） | 远程技能基建 |

> 注意：10 个技能/投射物实体**全部没有语言键**（除 `acid_ball` 有物品语言键、实体键仍缺），也没有刷怪蛋。

### 2.4 效果 / 粒子 / 音效

#### 效果（`RegisterEffect`，6 个）

| ID | 中文名 | 机制（源码核实） |
|---|---|---|
| `miasma` | 绝望 | 纯标记效果（无 tick 逻辑）；`ForgeEventHandler` 里：带此效果时回血 ×0.6；客户端 `ForgeClientEventHandler` 按等级压缩雾距（32→8 格） |
| `corruption` | 腐化 | `applyEffectTick` 每 tick 检查：血量≤0 且未标记时，把目标同化为感染形态（`AssimilationMapManager`）——实现方式很怪（靠效果 tick 检测死亡），需要重写/迁移到 infcore 死亡事件 |
| `lysis` | 裂解 | 累计受到的伤害，在效果结束/叠层时以魔法伤害返还（每 4 tick 可叠一次，等级放大） |
| `sclerosis` | 硬化 | `ForgeEventHandler.onLivingHurt` 里把单次伤害上限压到 `最大生命/等级` |
| `bleeding` | **无语言键** | 流血 DoT：每 20 tick 造成 `1 + 等级 + 累计秒数/5` 魔法伤害（`BleedTickCount` 存 persistentData）；**无图标贴图** |
| `fear` | **无语言键** | `implements NonRemovable`，每 tick `invulnerableTime=0`（让目标无法靠无敌帧抗伤）；依赖 `MixinLivingEntity` 防清除 |

#### 粒子（`RegisterParticles`，9 个）

| ID | 类型 | 中文/用途 |
|---|---|---|
| `blood` | 序列帧 | 血液飞溅 |
| `bloody` | 无序列帧 | 血腥雾点 |
| `blood_fog` | 序列帧 | 血雾（爆体/残留） |
| `blood_splash` | 序列帧 | 血液泼溅 |
| `blood_splat` | 序列帧 | 血斑 |
| `explosion_bloody` | 序列帧 | 血液爆炸 |
| `meat` | 无序列帧 | 肉块 |
| `degenerate_meat` | 无序列帧 | 腐化种周期掉肉 |
| `degenerate_die_meat` | 无序列帧 | 腐化种死亡喷肉 |

> Hall 侧已有同名/同义粒子：`blood`、`meat`、`explosion_bloody`（贴图 18 个文件同名同用途）；corruption 独有 6 种（`bloody`、`blood_fog`、`blood_splash`、`blood_splat`、`degenerate_meat`、`degenerate_die_meat`）。

#### 音效（`SoundEventRegistry`，10 个）

| ID | 用途 | 字幕键 |
|---|---|---|
| `rip_body` | 爆体 | ✅ `subtitles.corruption.rip_body` = 躯体爆裂（Hall 已复制该 `.ogg` 并注册 `hall:rip_body`） |
| `degenerate_call_type1` | 腐化种嘶吼 | ✅ |
| `degenerate_hurt_type1` | 腐化种受击 | ⚠️ 复用 `degenerate_call_type1` 字幕 |
| `body_hurt1` / `body_hurt2` | 躯体撕裂 | ✅ |
| `attacker_hurt_1` / `attacker_hurt_2` | 袭击者受击 | ❌ **无字幕键** |
| `attacker_living_1` / `attacker_living_2` | 袭击者环境音 | ❌ **无字幕键** |
| `overload` | 过载 | ❌ **无字幕键** |

### 2.5 世界生成：群系 / 模板池 / BiomeModifier / 生成放置

| 类别 | ID/位置 | 状态 |
|---|---|---|
| 群系 | `corruption:corruption_wasteland`（`CorruptionBiomeData`，datagen 输出 `data/corruption/worldgen/biome/corruption_wasteland.json`） | ✅ 已生成；环境色/雾/水色/环境粒子（`crimson_spore`）/环境音（绯红森林）/音乐（下界荒地）齐全；**无任何 spawners**（死寂群系） |
| 模板池 | `corruption:tree/trunk`、`corruption:tree/middle`、`corruption:tree/canopy`（`CorrupTionTreePools` + `WorldGenData`） | ⚠️ 3 个 JSON 已生成，但引用的结构 NBT（`corruption:tree/base_trunk`、`straight_trunk`、`fork_trunk`、`leaves_large`、`leaves_small`）**在 `src/main/resources/data/corruption/structures/` 下完全不存在**；`CorrupTionTreeGrower.grow()` **无任何调用方** → 整条结构树是死代码/未完成 |
| BiomeModifier | 序列化器 `corruption:config_driven_spawns`（`ConfigDrivenSpawnModifier`）在 `Corruption` 主类注册 | ❌ **无 `data/corruption/forge/biome_modifier/*.json`**（目录存在但为空）→ 该 modifier 实际从不应用；源码注释已改为「通过事件驱动生成」，此 serialzier 是残留 |
| 生成放置 | 无常量表；`CorruptionSpawnManager.registerSpawnPlacements()` 在 commonSetup 里按 `CorruptionSpawnConfig.spawnGroups` 动态注册 `SpawnPlacements` | ⚠️ dev 环境 `run/config/corruption/EntitySpawnConfig.json5` 有 11 组规则（original_bug/infection_zombie/cow/sheep/pig/bat/skeleton/long_arm_beast/shocker/degenerate_pig/humus_column），全部限定主世界；`ignoreNodeRequirement=false` 的规则要求节点阶段>0 |
| 结构 | `RegisterStructures` 为空类；无 `data/corruption/structures/*` | ❌ 无结构 |
| 地物 | 无 `configured_feature`/`placed_feature` | ❌ 群系仅引用原版 `patch_dead_bush` |
| 维度 | 无（corruption 不改维度） | — |
| 刷怪蛋/自然生成 | 见 2.3；自然生成依赖上面 config 驱动 | 合并后需重做进 infcore/Hall 的生成体系 |

### 2.6 语言键缺口清单（已用脚本核对 zh_cn.json 与注册表）

**zh_cn.json（en_us 同结构）缺少：**

| 类别 | 缺失项 |
|---|---|
| 效果（2） | `effect.corruption.bleeding`、`effect.corruption.fear` |
| 方块（6） | `block.corruption.spread_infection_wood`、`king_stone`、`king_dirt`、`king_brick`、`king_stone_dirt`、`king_leaves` |
| 实体（15） | `entity.corruption.infection_pig`、`infection_skeleton`、`heavy_guard`、`poison_arrow`、`acid_ball`、`bone_spike`、`cuboid`、`shield`、`red_dwarf_skill`、`red_dwarf_special_skill`、`red_dwarf_magic_circle`、`corruption_pool`、`corruption_dart`（以及未注册的 `avila`） |
| 字幕（3） | `subtitles.corruption.attacker_hurt`、`subtitles.corruption.attacker_living`、`subtitles.corruption.overload` |
| 杂项 | `itemGroup.corruption.main` 已有；JEI/命令/调试文案齐；无 `advancements` 缺 |

**语言数据明显是「按已做内容补」的**：LangDataCN 共 246 行；合并时若整包搬内容，这 26 个缺失键必须补齐（否则游戏内显示原始 key）。

### 2.7 KING 系列单列（用户决定：不搬）

| corruption KING 项 | 类型 | 现状 | Hall「王庭」对应物 | 结论 |
|---|---|---|---|---|
| `king_bone`（王庭骨） | 物品 | 无配方/无掉落/无用途，仅创造栏自动收集 + 辉光注册 | `hall_bone_fragments`（王庭骨，Hall 材料） | 语义完全重叠 → 丢弃 |
| `king_fiber`（王庭纤维） | 物品 | 同上 | `hall_tendon`（王庭肌腱，Hall 材料） | 近似重叠 → 丢弃 |
| `king_shell`（王庭甲壳） | 物品 | 同上 | 无直接对应（`bonecrusher_core` 碎骨核心 / `domite_crystal` 穹顶胚晶为近似「硬质材料」） | 丢弃 |
| `king_hearthstone`（王庭炉石） | 物品 | 同上 | 无直接对应 | 丢弃 |
| `king_big_sword`（王庭大剑） | 武器 | 有 8 环冲击波技能（15 伤害/5s CD），无配方无掉落，仅辉光 | Hall `domerite_longsword`（天穹裁决）/`crimson_vow`（绯红誓约）已覆盖重型武器位 | 丢弃 |
| `king_stone` / `king_dirt` / `king_brick` / `king_stone_dirt` / `king_leaves` | 5 方块 | 有贴图/模型/方块状态，**无语言/无配方/无掉落/无用途** | Hall 已有完整「王庭石/泥土/树叶/原木/木板/砂岩」系列（`hall_stone`、`hall_dirt`、`hall_leaves`、`hall_log`…） | 丢弃 |
| `HeavyGuardEntity`（`heavy_guard`） | 实体 | 已注册（667 血/15 攻/无 AI goal/无蛋/无语言/无掉落） | Hall `bonecrusher`（王庭碎骨，5×5 大怪）更完整 | 丢弃 |
| `AvilaEntity`（`avila`） | 实体 | **未注册**（仅类+geo/anim/贴图+`AvilaBossBarRenderer`） | Hall `collapsar`（萨米使徒 Boss，两阶段/黑洞/坍缩） | 丢弃 |
| `AvilaBossBarRenderer` 等 KING 资源 | 客户端/资源 | `client/rend/AvilaBossBarRenderer.java` + `textures/gui/health_avila.png` + `avila*.geo/anim/png` + `heavy_guard*` 资源 | Hall Boss UI 有自己的实现 | 丢弃 |

> 结论：KING 系列整体是「有美术资产但无玩法闭环」的原型；不搬可减少约 **2~3 人日** 与 5 方块 + 5 物品 + 2 实体的注册负担。Hall 王庭系列在名称/语义上已覆盖「王庭骨/肌腱/建材/大怪/Boss」，不必再从 corruption 引入。

---

## 3. 内容系统盘点与复杂度分级

### 3.1 实体：族谱、基类、AI 依赖与"不搬 AI"的删除影响

#### 3.1.1 基类结构

| 类 | 行数 | 职责 |
|---|---|---|
| `AbstractCorruptionEntity` | 1,087 | PathfinderMob + GeckoLib Animatable + `Brain`（自定义 `brainProvider`/`makeBrain`/`setupBrain`）+ 伤害适应（`adaptationEnabled`）+ 假死（`fakeDeathEnabled/Chance/Duration`，`onFakeDeathEnd` 是所有族基类死亡特效的挂点）+ walk/run 同步 + `IVariant` 变种 + 击杀计数 + `getAttack` 抽象 + 方块破坏（`canBreakBlocks`）+ 关键帧回调 `onKeyframeTrigger` + `doHurtTime/doHurtDistance` 攻击系统 + `CorruptionEntityType kind` 与同族过滤 |
| `AbstractCorruptionProjectile` | 130 | ThrowableProjectile + GeckoLib fly 动画 + 适应计数 + `onHitLivingEntity` 抽象 |
| `CorruptionEntityManager` | 76 | 实体注册簿：属性蓝图 + 自动注册 `GeoBaseRender` + `WeakHashMap` 实例追踪 |
| `CorruptionProjectileManager` | 47 | 投射物渲染注册 |
| `CorruptionMobType` | 7 | 自定义 `MobType CORRUPTION_MOB_TYPE`（阵营判断的旧实现之一，`StartAttacking` 用 `getMobType()` 比较） |

#### 3.1.2 族基类特征与耦合

| 族基类 | 行数 | 特有逻辑 | 依赖（合并后需替换/删除） |
|---|---|---|---|
| `BaseHostEntity`（同化种，11 实体） | 361 | B1 吐息 / B9 尸群践踏 / B10 濒死骨爆 / B11 血肉牵引 / B12 吸血 / B13 蝙蝠夜袭；`doHurtGoal` 后统一结算命中特效 | `CorruptionSkillUtil/Fx`（保留）、`CorruptionDart`/`BoneSpikeEntity`（保留）、`NetworkHelper`+`GorePacket`+`CParticleSpawnPacket`（保留/改 Hall 版）、`RegisterParticles`、`RegisterSoundEvent` |
| `BaseDegenerateEntity`（腐化种，4） | 83 | 周期掉肉粒子；死亡喷肉+爆体音效+粒子包；攻击给 `corruption` 效果 | 同上 |
| `BaseDeterioratedEntity`（变质种，1） | 73 | 与 degenerate 类似（肉粒子） | 同上 |
| `BaseRefactoredEntity`（重构种，4） | 113 | C9 死亡辐照领域（腐蚀池+冲击环）、死亡爆体 | `CorruptionSkillUtil.spawnPoolAt`、`CorruptionPoolEntity` |
| `BaseOriginalEntity`（原生种，1） | 79 | 死亡血花+Gore 包 | Gore 网络 |
| `BaseLinkedEntity`（连接种，3） | 105 | 每 5 tick 向 `event.handler.InfectionTrackerHandler` 注册「感染活性快照」（供方块 `isPosActive` 判定）；C9 死亡辐照领域；禁止推动 | **注意：`com.corruption.event.handler.InfectionTrackerHandler`（快照）与 `com.corruption.overworld.registry.block.base.InfectionTrackerHandler`（脏区块位图）是两个同名不同作用的类**；随扩散引擎迁移，快照机制可改为 Hall/infcore 的激活判定或直接删除 |
| `BaseHostEntity` 的 gear：`InfectionPlayerEntity` 皮肤 | — | `OnlineSkinDownloader`（HTTP 拉取 Mojang 会话服务器 → 动态纹理 + `client/cache/skins` 缓存 + LRU 50） | **建议不搬**（网络依赖/隐私/维护风险；Hall `InfPlayerEntity` 用固定 geo 贴图） |

#### 3.1.3 AI / 寻路 / Brain 依赖矩阵（用户约束：不搬）

| 依赖类 | 行数 | 被谁使用（实体文件数） | 功能 | 删除/替换方案 |
|---|---|---|---|---|
| `SmartMoveGoal` | 598 行 / 22.9KB | 21 个实体文件（degenerate 4、host 8、original 1、refactored 4、deteriorated 1、king 1、bearer） | 自研 A* 风格移动：waypoint、gap 跳跃、竖井攀爬、卡住检测、目标更新 | 删除；改用 `AbstractHallEntity.tickChase()` + `ChaseLockGoal` + 原版 `GroundPathNavigation`（Hall 已配套 `FaceTargetGoal`） |
| `JumpPointPathfinder` | 15.6KB | 仅 `SmartMoveGoal` | JPS 跳点寻路（自注「未完成版」） | 删除 |
| `SmartTargetGoal` | 12.4KB | 21 个实体文件 | 增强索敌：16 格、10 tick 检查、隔墙/视线、追踪范围 ×1.5、同族过滤、modid 白名单、黑名单、排除实体类型、卡住放弃 | 删除；改用 infcore `InfectedTargetGoal`（阵营：`IInfectedEntity` + `ITargetFilter`）+ Hall `ThreatHelper` 威胁阈值 |
| `RandomWanderGoal` | 5.4KB | 21 个实体文件 | 自研随机游荡（替代原版 `RandomStrollGoal`） | 删除，换原版 `RandomStrollGoal`/`WaterAvoidingRandomStrollGoal` |
| `FollowGoal` | 4.5KB | `BearerEntity` | 跟随同类（孕育者跟队） | 删除或换原版 `FollowOwnerGoal` 语义（可自己写小 Goal） |
| `CorruptionFlyGoal` | 1.9KB | `InfectionBatEntity` | 飞行追击 | 删除，换原版 `FlyingMoveControl` + `WaterAvoidingRandomFlyingGoal`（Hall `CollapsarEntity` 有飞行实现可参考） |
| `brains/SmartMoveBrain`（+`AttackMoveBehavior`、`BrainsState`） | 4.2KB / 0.7KB / 0.3KB | `AbstractCorruptionEntity` 基类内 `setupBrain()` | 把移动交给 Brain（`Activity.IDLE`）；同时 `StartAttacking` 用 `MobType` 判阵营 | 删除整个 brain 体系：`MemoryModuleTypeRegistry`（1 行注册）、`customServerAiStep` 里的 brain tick、`brainProvider/makeBrain/setupBrain`；阵营判断改 `IInfectedEntity` |
| `MemoryModuleTypeRegistry.AI_STATE` | 19 行 | 基类 + brain | 自定义记忆类型 | 随 brain 删除 |

**删除影响（必须在方案中体现）：**

1. **移动体感变化**：SmartMoveGoal 的「跳沟/爬竖井/卡住重算」是 corruption 生物跨越地形能力的来源；换成 Hall 的 `tickChase` + 原版导航后，24 个生物的地形通过性会下降（尤其 Attacker 4.5 高、LongArmBeast 3.9 高的碰撞箱）。需按 Hall 的「碰撞箱适配经验」重新调碰撞箱（corruption 源码里 Shocker 已有一例：2.4 宽碰撞箱会让 `PathNavigation` 认为走不过去，被改成 1.5）。
2. **攻击命中绑定动画关键帧**：corruption 实体攻击走 `doHurtGoal/getAttack`（基类）+ 动画关键帧（`onKeyframeTrigger`），而 Hall 的 `AbstractHallEntity` 也是 `doHurtGoal → getAttack` 路线（已对齐），但 `SmartMoveGoal` 里的「贴脸距离/冷却」与 Hall `doHurtDistance/doHurtTime` 语义相似；迁移时以 Hall 字段为准重调参数。
3. **索敌行为变化**：`SmartTargetGoal` 的「同 kind 不互殴 / modid 白名单 / 排除类型 / playersOnly」需要在新方案里用 `InfectedTargetGoal.Builder.range/mustSee/filter` + `ITargetFilter` 复刻；`ConfigHelper.isModidInTargetWhitelist` 的语义（按 modid 白名单）在 infcore 没有现成对应，需写一个 `ITargetFilter` 实现并保留配置。
4. **实体不再需要 `Brain` 后**，`GeckoLib` 动画控制器与 `IKeyframeHandler` 保持；`CorruptionEntityManager` 的实例追踪逻辑可丢弃（Hall 无等价需求，实体查询用原版）。
5. `ICorruptionEntity` 只是空标记接口，被 `CorruptionSkillUtil.isValidTarget`、`AbstractCorruptionEntity.canAttackTarget`（同族）、`SmartTargetGoal`、`StartAttacking` 用来做阵营判断——**全部改为 `IInfectedEntity`（infcore）判断**；`CorruptionMobType` 自定义 MobType 一并删除（`IInfectedEntity` 是更干净的方案，Hall 已这么做）。

#### 3.1.4 实体侧的可复用点（迁移时直接受益）

- Hall `AbstractHallEntity`（61.8KB / 1,117 行）**已经覆盖** corruption 基类的大部分能力：假死系统（`tryFakeDeath/startFakeDeath`，且比 corruption 多了 burst 缩放/动画/自定义时长）、walk/run 动画与移速、`doHurtGoal/getAttack` 攻击骨架、GeckoLib、击杀计数（infcore `IKillCounter`）、`IInfectedEntity` 感染类型、威胁值索敌、爆体血花/音效（`onBurstEnd` 播 `rip_body`）。
- corruption 基类**仍需补进 Hall 基类**的能力：伤害适应系统（`adaptationEnabled/getAdaptationFactor/threshold`，受击减伤 5%/次、5 次触发）、`IVariant` 变种（多模型/贴图选择）、`canBreakBlocks` 沿途破坏、`IKeyframeHandler` 关键帧同步（Hall `onKeyframeTrigger` 是空实现，需要把 corruption 的 keyframe 包与处理搬过来）。
- Hall 实体族划分可直接映射：corruption host/degenerate/deteriorated/refactored/original/linked → Hall 的 infected/ulcerated/ecological/defensive/apostle 五族；**族基类不必 1:1 搬，按能力重组**。

### 3.2 技能/投射物系统与客户端渲染依赖

| corruption 类 | 行数 | 作用 | 客户端依赖 | 迁移建议 |
|---|---|---|---|---|
| `CorruptionSkillUtil` | 21.8KB | 纯服务端技能工具：粒子先兆、扇区/球形/直线目标查询、真伤/范围/吸血/牵引、效果叠加、召唤腐蚀池、`isValidTarget`（阵营+创造/旁观过滤） | 无（但用到 `RegisterEffect`） | **保留**，只改 `isValidTarget` 的阵营判断为 infcore `IInfectedEntity` |
| `CorruptionSkillFx` | 4.8KB | 纯粒子特效封装（冲击/冲击环/光柱/吐息/血雾） | 粒子 | 保留 |
| `CorruptionDart` | 15.3KB | 通用弹体（颜色/伤害/重力/命中效果/腐蚀池/穿透） | 客户端粒子+自绘 | 保留（复用 Hall `EffectSyncPacket`/网络） |
| `CorruptionPoolEntity` | 11.7KB | 地面腐蚀池（周期伤害+效果+粒子） | 客户端粒子渲染（空 renderer） | 保留 |
| `BoneSpikeEntity` | 5.9KB | 模型化骨刺投射物 | GeckoLib geo/anim | 保留 |
| `CuboidEntity` | 10.2KB | 通用方块判定实体（定向缓动/抛射/逐 tick 回调） | 自绘 `CuboidRenderer`（5.5KB，用 `CorruptionShaders`/RenderType） | 保留 + 渲染器移植 |
| `ShieldEntity` | 6.5KB | 二十面体护盾（跟随/伤害转治疗） | 自绘 `ShieldRenderer`（5.7KB）+ `IcosahedronRendUtils`（11.3KB） | 保留 + 渲染工具移植 |
| `RedDwarf*`（3 实体 + 弓） | 12.8KB + 弓 2KB | 红矮星弓技能：球体/二十面体/法阵 | `BallRendUtils`（18KB）、`IcosahedronRendUtils` | **待定**：若保留红矮星弓则整体搬（约 2-3 人日），否则丢弃 |
| `AvilaEntity` + Boss 条 | 24.2KB + 3.9KB | 未注册 Boss | 大量 | 丢弃（KING） |
| `CorruptionAreaAttackUtil` | 23KB | 圆柱扇区/球形/距离衰减/自定义伤害回调/破土 | 无 | **丢弃**（Hall `utils/combat/RangeAttackUtils` 53.6KB 是升级版，支持 sector/cone/sphere/cylinder/annulus + Builder + 回调） |
| `CorruptionDamageUtil` | 10.8KB | 真伤/无视无敌帧/盾牌/图腾/分级伤害（`TrueHurt` 1-4 级） | 无 | 合并进 Hall combat 工具（Hall `RangeAttackUtils` 已含真伤语义，需核对） |
| `CorruptionRipBodyUtil` | 8KB | 死亡残骸方块（同化/腐化/重构三种残骸方块按球形散布+附着朝向） | 无 | **保留**（依赖 26 方块里的 Remains 系列） |
| `CorruptionEntityParticleUtil` | 12.7KB | 实体粒子 Builder（与 Hall `EntityParticleUtils` 几乎同源同 API） | 粒子 | **丢弃**，用 Hall 版（核对 `ParticleConfig` 字段差异，corruption 版新增 `spawnParticles(entity, ParticleOptions, count, range, speed)` 便捷重载） |
| `CorruptionTypeUtil` | 0.7KB | kind → 击杀权重（威慑值增量） | 无 | 改接 Hall `ThreatHelper.threatFromKill`（Hall 用「最大生命/5」而非 kind 权重） |
| `EntitySetUnit` | 0.9KB | 设置最大生命/护甲 | 无 | 保留（Hall `HallDifficultyAttributes` 也有类似工具，二选一） |

### 3.3 方块与腐化扩散系统

#### 3.3.1 corruption 现状（自研引擎）

| 类 | 行数 | 职责 |
|---|---|---|
| `AbstractSpreadingBlock` | 183 | 扩散方块基类：randomTick 里做「节点/激活判定 → 生存检查/退化 → 群系补全 → 四方向扩散 → 萌发 → 子类 extraTick」；带背压（队列负载>0.6 直接跳过） |
| `BiomeCorruptionProcessor` | 217 | 单线程异步引擎：`PENDING_POS`+`TASK_QUEUE`(上限 10000)+`processBatch`(40/tick)+`setColumnQuartBiome` 直接改 PalettedContainer+发 `ClientboundLevelChunkWithLightPacket`；`enqueueForward` 存原方块快照、`enqueueReverse` 恢复 |
| `CorruptionDiskStorage` | 66 | 自定义二进制 `.cdat`（palette + 打包坐标）存 `world/corruption/<dim>/corruption_data/` |
| `InfectionTrackerHandler`（block.base 包） | 28 | 脏区块 LongSet 位图（供 `MixinBiomeManager` 快速判定） |
| `InfectionTrackerHandler`（event.handler 包） | 57 | 连接种实体快照（UUID→位置/半径²/维度），用于 `isPosActive` 判定扩散活性 |
| `SpreadRuleManager` | 224 | 硬编码扩散规则：标签/方块 → 目标方块（权重/最大数量/环境谓词/回调）；含「256 次扩散后演化值 +0.25」 |
| `BiomeCorruptionData` | 66 | 仅作为 SavedData 钩子触发 `initStorage`（不存数据） |
| `ModBiomes` | 21 | 缓存 `corruption_wasteland` Holder |
| `MixinBiomeManager` | 40 | 用脏位图+节点判定在 `getBiome` 注入腐化群系 |
| `SpreadEventHandler` | 63 | 每 tick `processBatch`、关服 flush、关卡加载/卸载清理、节点中心寄存器预热 |
| 方块类 | 14 文件 1,345 行 | 见 2.2；`CorruptionVariableHeightCropBlock`(5.6KB) 自定义三段生长；`CorruptionInfectionLeavesBlock`(5.9KB) 自行实现 distance/decay；`CorruptionAttachedBlock`(6.2KB) 六向附着 |

#### 3.3.2 与 infcore / Hall 对应关系（结论：**整引擎丢弃，规则+方块内容重接**）

| corruption | Hall/infcore 对应物 | 映射说明 |
|---|---|---|
| `AbstractSpreadingBlock` | Hall `overworld/registry/blocks/base/AbstractSpreadBlock`（implements infcore `ISpreadBlock`，委托 `BlockSpreadManager.applySpread`）+ infcore `SpreadHelper` | corruption 方块改为继承 Hall `AbstractSpreadBlock` 或实现 `ISpreadBlock`，并实现 `getSpreadType()=rotten_ruins:spread` |
| `SpreadRuleManager`（硬编码 224 行） | **数据驱动**：`data/<ns>/infcore_blockspread/*.json`（infcore `BlockSpreadManager`；Hall 已有 `data/hall/infcore_blockspread/default.json` 43 条示例） | 逐条搬迁：`minecraft:dirt→spread_dirt`、`#logs→spread_infection_wood`、`#leaves→spread_infection_leaves`、`#base_stone_overworld→spread_stone`、`#replaceable→corruption_grass/variable_height_crop/corruption_trachea`；「萌发池」（AIR_STRICT 条件 + 权重）是 infcore 没有的能力，需要：①保留一个小 `SproutGoal`/randomTick 自定义实现；②或在 `AbstractSpreadBlock.extraTickLogic` 里复刻（Hall 的 `AbstractSpreadBlock` 预留了 `extraTickLogic`） |
| `BiomeCorruptionProcessor` | Hall `overworld/swarm/HallBiomeProcessor`（13KB，逐维度状态隔离、队列/限流/缓存/磁盘全套）+ infcore `BiomeSpreadHelper`（写群系+同步区块） | **直接丢弃 corruption 版**；群系扩散交给 Hall 版；corruption 只需提供「目标群系 = corruption_wasteland」的维度映射（照抄 Hall `HallSpreadEventHandler.DIMENSION_TARGET_BIOME` 的注册方式） |
| `CorruptionDiskStorage` | Hall `overworld/swarm/HallDiskStorage`（4.2KB） | 丢弃 corruption 版 |
| 两个 `InfectionTrackerHandler` | Hall `overworld/swarm/HallInfectionTracker`（2.3KB）+ `HallSpreadSaveData` | 丢弃；连接种激活判定改用 Hall 的 tracker |
| `BiomeCorruptionData` | Hall `HallSpreadSaveData` | 丢弃 |
| `MixinBiomeManager` + `ModBiomes` | 无（Hall 用真实群系写入+包同步） | 丢弃 |
| `SpreadEventHandler` | Hall `HallSpreadEventHandler`（12KB，含叶子级联、藤蔓概率、每维度群系映射） | 丢弃 corruption 版 |
| 扩散视觉/音效 | `CorruptionDirtBlock` 等子类的 `onSpread/getTargetBiome/getDegradedState/reverseSpread/canSurvive` 逻辑 | 这些方法需要**移植到新的方块类**上（Hall 的 `AbstractSpreadBlock` 有同名钩子：`getDegradedState`/`canSpreadTo`；`reverseSpread`/`getTargetBiome` 需要新增或改用 Hall 的维度映射） |

> **净结论**：方块扩散「引擎层」约 800~900 行可以整段丢弃（Hall 已有更成熟实现），真正要搬的是：
> 1. 26 个方块类的「行为定制」部分（生长/附着/残骸/温床/作物，约 1,300 行）；
> 2. `SpreadRuleManager` 里的规则清单（约 10 条，转 JSON）；
> 3. 萌发池（sprout）这一 infcore 不支持的能力（约 40 行，可放 `extraTickLogic`）；
> 4. `corruption_wasteland` 群系注册 + 维度映射。

### 3.4 世界系统

#### 3.4.1 节点系统（corruption 独有，Hall/infcore 无对应）

| 组件 | 行数 | 说明 |
|---|---|---|
| `CorruptionNode` | 7.1KB | 节点数据：UUID、中心 BlockPos、半径、强度、中心阶段、升阶点数、维度；`contains/distanceTo/toNbt/fromNbt/addPhasePoints(phaseThresholds)` |
| `NodeQueryResult` | 4.2KB | 某坐标被哪些节点覆盖的叠加结果：`getCombinedPhase/getCombinedIntensity/isBiomeDiffusing/getNearestNode`（受 `maxStackedPhase/maxStackedIntensity` 上限约束） |
| `CorruptionNodeManager`（extends AbstractSaveData） | 226 行 | `SavedData` 名 `corruption_nodes`；CRUD、`queryAt`、`getCombinedPhaseAt/getCombinedIntensityAt`、`resolveSpawnCategory(phase→PHASE_BASIC/STANDARD/ADVANCED/ELITE)`、`shouldDiffuseBiome(phase≥biomeDiffusionPhase)`；`writeSyncData` 只同步 nodeCount/maxPhase 元数据 |
| `NodeEventHandler`（注册到 Forge BUS） | 136 行 | 世界加载时在出生点半径 1000 内生成初始节点（种子固定）；每 100 tick 驱动 `radiusGrowthPerTick/intensityGrowthPerTick` 自然增长（默认 0） |
| `NodeCommands` | 190 行 | `/node create|delete|list|info|modify radius|intensity|phase`（OP 2） |
| `RrsCommands` + `CommandChain` | 77 + 3.4KB | `/corruption query|set value|set phase|add|remove` 演化值调试命令 |
| `DebugItem` | 6.6KB | 右键空气=列节点概览、右键方块=查询叠加信息、右键连接实体=加等级/半径、Shift 右键=减等级 |
| `NodeConfig` | 82 行 | 见 1.6；`phaseThresholds[10]`、`biomeDiffusionPhase=5`、`maxNodesPerLevel=12`、`initialRadius=120`、`initialIntensity=0.8`、`healthBonusPerIntensity=1`、`armorBonusPerIntensity=1` 等 |
| 消费点（grep 统计 6+ 处） | — | ①`ForgeEventHandler.onEntityJoin`：腐化生物按节点强度加生命/护甲+再生；②`BiomeTickHandler`：节点内生物（玩家）挂 `miasma`、2% 概率召唤红雷；③`CorruptionSpawnEventHandler`：刷怪阶段/感染区判定；④`AbstractSpreadingBlock.isAreaActive`：方块扩散门槛；⑤`BiomeCorruptionProcessor`/`MixinBiomeManager`：群系扩散；⑥`DebugItem`；⑦`CorruptionNodeManager.shouldDiffuseBiome` |

**复杂度评级：XL（10~15 人日）**。理由：全新 SavedData 格式 + 每维度数据 + 同步（`PacketSyncSaveData`）+ 命令 + 配置 + 与刷怪/属性/扩散/群系的 6 处耦合；且 Hall/infcore 没有「空间感染强度场」概念（infcore 是全局 difficulty + 数据驱动 evolution，Hall 是 threat 值），无法直接映射。
**若不搬**：需要用「infcore 全局难度/演化 + Hall 威胁值」重新定义「感染强度」；刷怪阶段判定、方块扩散活性判定、生物属性加成都要另找来源（可以直接简化为「在腐化群系内/节点圆内」，或统一用 evolution 阶段）。

#### 3.4.2 AssimilationMapManager（同化）

- 229 行；从 `AssimilationConfig` 读转换规则（from→to、演化值、成功率、条件、成功后回调），空配置时用内置默认（原版动物→感染动物、骷髅/僵尸→感染版、村民→感染村民）。
- 消费点：`BloodyConcentrateSyringeItem`（注射器转化）、`CorruptionEffect`（死亡转化）、`AbstractCorruptionEntity.awardKillScore`（**已注释掉**，击杀转化当前关闭）。
- 映射：infcore `EvolutionManager` + `data/<ns>/infcore_evolution/*.json`（Hall 已有 `type: hall:inf` 与 `hall:evo` 两类、`keepEquipment/keepNbt` 字段）；corruption 的 `AssimilationConfig` 里的「条件/回调」比 infcore JSON 丰富，需要 infcore 侧确认是否支持或降级为「只保留 from→to+权重」。
- **复杂度 M（2~3 人日）**：主要是把 11 组默认规则 + 条件翻译成 infcore JSON；Hall `UlceratedConversionRules` 是现成范例。

#### 3.4.3 BearerManager（孕育者进化）

- 156 行；硬编码 2 条进化：孕育者吞噬值≥2.5 且夜晚 → 40% 概率变长臂兽（+10 演化值）；≥2.0 → 20% 变震撼者（+5 演化值）；成功后闪电特效+继承 35% 最大生命。
- 映射：infcore `data/<ns>/infcore_evolution/*.json` 的 `evo` 类型（Hall 的 `hall:monolith→bonecrusher/heavy_bomb` 就是同样用法），但「吞噬值/夜晚条件/动态概率」这些自定义条件 infcore JSON 未必支持 → 建议保留一个小型 `BearerEvolutionManager` 或在 `BearerEntity` 内直接实现，规则改数据驱动。
- **复杂度 S（1~2 人日）**。

#### 3.4.4 刷怪系统

| 组件 | 行数 | 说明 |
|---|---|---|
| `CorruptionSpawnManager` | 82 | `@Deprecated` 兼容层：委托 `CorruptionSpawnEventHandler`；`registerSpawnPlacements()` 按配置动态注册 `SpawnPlacements`（ON_GROUND + MOTION_BLOCKING_NO_LEAVES + `Mob::checkMobSpawnRules`） |
| `CorruptionSpawnEventHandler` | 258 | 事件驱动：`MobSpawnEvent.PositionCheck`（按配置检查维度/亮度/节点阶段/玩家距离/并发上限；感染区禁原版）、`FinalizeSpawn`（合并额外 NBT+后处理器）、`EntityJoinLevel/LeaveLevel` 计数、每 1200 tick 校准计数；`preventVanillaSpawnInInfectedArea` |
| `ConfigDrivenSpawnModifier` | 88 | 已注册 codec 但无数据 JSON（见 2.5） |
| `SummonManager` | 92 | 「召唤池」框架，但 `SPAWN_POOL` 是空 builder 且**无调用方**（死代码；`SummoningPillarEntity` 自己直接 `create` 实体） |

- Hall 现状：`SplendidingSpawnPlacements.register()` 是**空实现**；Hall 生物目前主要靠刷怪蛋/维度/特殊刷怪逻辑（`HallBiomeData` 的 spawners 可配）。
- **建议**：不搬 corruption 的事件驱动刷怪框架，改为：①把 `spawnGroups` 的语义压成「按群系/维度/阶段配置生成」的简化版（可参考 infcore/原版 BiomeModifier + `SpawnPlacements`）；②或直接复用 Hall 即将建设的刷怪体系。**复杂度 M（3~5 人日）**，且强依赖节点系统去留（阶段判定）。

#### 3.4.5 结构树 / 命令 / 调试

- 结构树：`CorrupTionTreeGrower`(44 行) + `CorrupTionTreePools`(25 行) + `WorldGenData.bootstrap` 的 3 个模板池 = **死代码**（无 NBT、无调用）。**直接丢弃**。
- 命令：`/node …`（5 子命令）+ `/corruption …`（演化值调试）→ 随节点系统/演化系统去留；合并时 namespace 命令根应改为 `/rotten_ruins`，避免和 Hall 现有 `/spread`、`/threat`、`/anomaly` 等冲突。
- 调试工具：`DebugItem` 强绑节点；若节点不搬则丢弃。

### 3.5 物品逐项处置表

> 判定原则：①与 Hall 现有内容重复 → 丢弃；②无功能空壳/未完成 → 丢弃或待定；③构成 corruption 核心玩法循环（感染/同化/腐化方块/生存压力）→ 搬迁。

| 物品/系统 | 数量 | 处置 | 理由 |
|---|---|---|---|
| 材料 10 件（`infection_bone`…`long_arm_beast_tooth`） | 10 | ✅ 搬 | 掉落循环与合成树的基础 |
| 粗制工具/武器 5 件（`crude_*`） | 5 | ✅ 搬 | 有配方、早期生存循环，与 Hall 无重叠 |
| `syringe` + `syringe_bloody_concentrate` | 2 | ✅ 搬（转化逻辑改 infcore evolution） | corruption 核心机制（把生物/玩家转化为感染态） |
| `poisoned_saber` | 1 | ⚠️ 待定 | 无效果实现、无配方；只有成就和贴图。搬则需补「注毒 DoT」与配方 |
| `red_dwarf_bow` + 3 红矮星技能实体 | 1+3 | ⚠️ 待定（功能完整但成本高） | 唯一完整远程武器；需要 `BallRendUtils`/`IcosahedronRendUtils`/3 个自绘渲染器；若砍则少 2~3 人日 |
| 酸液类 3 件（`acid_ball`/`acid_sac`/`acid_bomb`） | 3 | ✅ 搬 2（ball/sac 作为材料），`acid_bomb` 待定 | `acid_sac` 是 shocker 掉落；`acid_bomb` 无功能 |
| 芯片 4 件 | 4 | ❌ 丢弃（或整体待定） | 纯空壳 + JEI 文案；需要「机械/科技」系统才有意义，与 Hall 魔法体系冲突 |
| 七宗罪 8 件（`vice_template`+7） | 8 | ❌ 丢弃（或整体待定） | 仅 enum 标记 + 辉光；无效果、无配方；Hall 已有大量魔法/裁决类物品定位重叠 |
| KING 5 件 | 5 | ❌ 丢弃 | 用户已决定 |
| 文件系统 2 件 | 2 | ⚠️ 待定 | `file_folder` 有容器逻辑但无 GUI；`file` 全空。若要做「文件收集/图纸」玩法需重做界面 |
| `debug_item` | 1 | 随节点系统 | — |
| `fleshbed` + BlockEntity + `FleshbedRecipeConfig` | 1+1 | ✅ 搬（建议） | 唯一的功能性方块（血肉培养/转化），玩法独特；需要补方块掉落表与配方（已有配方） |
| 刷怪蛋 21 件 | 21 | ✅ 搬（除 KING 相关） | 生成蛋便于测试/创造模式 |
| 方块物品 26 件 | 26 | 26 中搬 21（丢 5 个 KING 方块） | 见 2.2/2.7 |
| `spread_*` / `corruption_grass` / `variable_height_crop` / `corruption_trachea` / `decidual_tissue_block` / `connect_block` / `spiracle_block` / 9 个遗骸 | 17 | ✅ 搬 | 腐化地貌视觉与扩散玩法主体 |
| 效果 6、粒子 9、音效 10 | 25 | ✅ 搬（与 Hall 去重） | 见 2.4 |
| 群系 1 | 1 | ✅ 搬（改由 Hall 群系/扩散体系管理） | `corruption_wasteland` |

### 3.6 基础设施比对（保留 Hall 版 or corruption 版）

> 判定依据：①代码量/完成度；②是否依赖 corruption 自研框架；③Hall 是否已有更新版本。

| 类别 | corruption 版 | Hall 版 | 建议 |
|---|---|---|---|
| 网络通道/Helper | `network/NetworkHelper` 95 行、8 包 | `network/NetworkHelper`（6.7KB）、16+ 包、含 `sendToNear/sendGuiShaderToPlayer` | 保留 **Hall** |
| UniversalPacket / PacketActionRegistry | 有（同源） | 有（更新） | 保留 **Hall** |
| capability 基类 | `AbstractCapability`/`CapabilityProvider`/`IModCapability`/`CapabilityRegistry`（4 文件 165 行） | `core/capability/*` 在 infcore + Hall `capability/base`、`CapabilityRegistry`（5KB，含 `getId/getById` 反查） | 保留 **Hall**（corruption 的 `Deterrence` capability 改接 Hall `ThreatCapability`） |
| SavedData 基类 | `AbstractSaveData`/`ISaveData`/`ISaveDataHolder`（65 行） | `savedata/base`（2 文件，更薄，`markDirty` 实现不同） | 若节点系统搬入，用 corruption 版基类或按 Hall 风格重写；**不要两套并存** |
| 数据同步 | `SyncDataHelper`（演化值/难度/PhaseSetting 8 级）+ `CorruptionData` SavedData + `UniversalPacket("sync_corruption")` | infcore `EvolutionManager` + `GlobalDifficultyData` + `PacketSyncWorldData`/`PacketSyncDifficulty` + Hall `HallDifficulty` | **丢弃 corruption 版**，接 infcore/Hall |
| 方块扩散 | `BiomeCorruptionProcessor` 等（见 3.3） | `overworld/swarm/*`（5 文件 ≈ 32KB）+ infcore | 丢弃 corruption 引擎，内容重接 |
| 群系处理 | 自研队列+磁盘 | `HallBiomeProcessor`（13KB）+ HallDiskStorage + infcore `BiomeSpreadHelper` | 保留 **Hall** |
| 演化/同化 | `AssimilationMapManager`/`BearerManager` | infcore `EvolutionManager` + 数据 JSON + Hall `UlceratedConversionRules` | 保留 **infcore/Hall**，corruption 规则转数据 |
| 刷怪 | `CorruptionSpawnEventHandler`（事件驱动、配置化） | Hall 空（`SplendidingSpawnPlacements` 未实现） | **需要新建**（可采用 corruption 思路，但剥掉节点耦合） |
| 粒子工具 | `CorruptionEntityParticleUtil`（12.7KB） | `utils/entity/EntityParticleUtils`（15.9KB，同源升级） | 保留 **Hall** |
| 范围攻击工具 | `CorruptionAreaAttackUtil`(23KB)+`CorruptionDamageUtil`(10.8KB) | `utils/combat/RangeAttackUtils`（53.6KB，更全） | 保留 **Hall** |
| 效果叠加工具 | `ModUtils.addOrStackEffect/forceAddEffect` | `EntityBuffUtils.addOrStackEffect/stackEffectLevel...` | 保留 **Hall** |
| 实体渲染基类 | `GeoBaseRender`(3.4KB)/`GeoProjectileRenderer`/`SkillProjectileRenderer` + `CorruptionEntityManager/ProjectileManager` | `client/entity/render/GeoBaseRender`(5.1KB)+`GeoEntityRender`+`GeoProjectileRenderer`+`GeoItemRenderer` + `HallEntityManager/HallProjectileManager/EntityManager` | 保留 **Hall**；corruption 的 `CorruptionGeoItemRenderer`（0.4KB）可丢弃 |
| 实体接口 | `IAutoRenderable*`/`IGeoResources`/`IGeoModelBehavior`/`IGeoLayerProvider`/`IBillboardRenderable`（6 文件同源） | Hall 有同套（+`GeoEntityModel`/`GeoItemModel`/`GeoProjectileModel`/`GlowLayer`） | 保留 **Hall** |
| 辉光/描边/光影 | `client/rend/glint` 15 文件（HeldItemOutlineRenderer 57KB、HeldItemRuleManager 34KB、HeldItemOutlineSettings 16KB…）+ 15 个 shader + 多个 GameRenderer/InHand/LevelRenderer mixin | `client/rend/glint` 20 文件（**ItemOutlinePipeline 139KB** + OutlineGlintRenderer + ShaderPackDetector + `ItemOutlinePipeline.java.corrupt` 备份）+ 同样的 15 个 shader + 更多 mixin | **完全丢弃 corruption 版**（Hall 是它的超集，见 4.3） |
| 道具渲染辅助 | `CDRenderHelper`(8.6KB)/`ZoomRenderHelper`/`ItemRenderManager`/`AbstractItemRenderHelper`/`AbstractStaticEffectRenderer`/`LightningEffectRenderer`/`HitboxCubeRenderer` | Hall 全有同名（`CDRenderHelper` 8.6KB、`ZoomRenderHelper` 4.9KB、`ItemRenderManager` 3.8KB、`AbstractStaticEffectRenderer` 7.6KB、`LightningEffectRenderer` 8.4KB、`HitboxCubeRenderer` 5.6KB） | 保留 **Hall**（corruption 只多 `BallRendUtils`/`IcosahedronRendUtils`/`CuboidRenderer`/`ShieldRenderer`/`CorruptionShaders`，属技能专用） |
| Shader 注册 | `CorruptionShaders`(5.4KB) | `SplendidingShaders`(47KB) | 保留 **Hall**；corruption 技能若用独立 shader，需把 2 个 lightning/outline shader 合入 Hall 注册表 |
| 在线皮肤 | `OnlineSkinDownloader`(7.6KB) | 无 | ❌ 丢弃（外部 HTTP 依赖、隐私与维护风险） |
| mixin 插件 | `ItemGlintMixinPlugin` | 同名插件 | 保留 **Hall** |
| Utils | `ModUtils` 447 行（射线裁剪、群系写入、伤害/效果、日志、注册表查询） | `utils/ModUtils` 极小（633B）+ 各工具类 + infcore `ThrottleLogger` | 逐函数拆分：射线裁剪/坐标工具→新建工具类或并入 Hall；群系写入→infcore `BiomeSpreadHelper`；伤害/效果→Hall/infcore；`ModUtils.MODID` 常量→`RottenRuins.MODID` |
| API 接口 | `ICorruptionEntity`/`IVariant(Entity)`/`ISevenSins`/`IDeterrence`/`IAutoRenderableItem`（共 44 行） | infcore `IInfectedEntity`/`IKillCounter`/`ITargetFilter` + Hall `ICustomOutline` 等 | `ICorruptionEntity`→`IInfectedEntity`；`IVariant*` 若保留变种则搬到 Hall 实体包；`IDeterrence`→Hall threat；`ISevenSins` 随七罪丢弃；`IAutoRenderableItem` 用 Hall 版 |

---

## 4. 资源清单

### 4.1 corruption 主资源（`corruption/src/main/resources`，292 文件 / 1,525KB）

| 目录 | 文件数 | 体积 | 说明 |
|---|---|---|---|
| `assets/corruption/animations` | 28 | 327KB | 28 套 GeckoLib 动画（最大 `degenerate_villager.animation.json` 45KB） |
| `assets/corruption/geo` | 29 | 319KB | 24 生物 + 2 皮肤变体（`infection_player_slim/wide`）+ 3 投射物；`heavy_guard.geo.json` 34.5KB 最大 |
| `assets/corruption/models` | 12 | 13.5KB | 手动物品模型：5 粗制工具 + `king_big_sword` + `poisoned_saber` + 红矮星弓 4 件 + `fleshbed` 方块模型 |
| `assets/corruption/shaders` | 15 | 23.8KB | `held_item_bloom`(3)、`held_item_bloom_blur`(3)、`held_item_outline`(3)、`lightning_outline`(3)、`rendertype_item_glint`(3) → **Hall 全部已有同名文件** |
| `assets/corruption/sounds` | 10 | 271KB | 10 个 `.ogg`（见 2.4）；`rip_body.ogg` 与 Hall 字节数完全相同（已复制） |
| `assets/corruption/textures` | 198 | 570KB | 见下表 |
| `assets/corruption/text` | 0 | 0 | 空目录 |
| `META-INF/mods.toml`、`pack.mcmeta`、`corruption.mixins.json`、`thanks.md` | 4 | 5KB | — |
| `data/corruption` | 0 | 0 | 空（数据全在 generated） |

textures 细分：`block` 48 / `entity` 38 / `item` 68 / `particle` 37 / `mob_effect` 5 / `gui` 2（`health_avila.png`、`node_radar.png`）。

- 最大文件：`textures/particle/blood4x4.png` 102.9KB、`blood_fog1/2/3.png` 各 ~60KB、`sounds/degenerate_call_type1.ogg` 46.5KB、`animations/degenerate_villager.animation.json` 45.4KB、`textures/particle/blood_fog0.png` 42.3KB、`sounds/rip_body.ogg` 40.4KB、`geo/heavy_guard.geo.json` 34.5KB。
- 垃圾/孤儿资源：`textures/item/img.png`、`textures/item/model (5).png`、`textures/block/1779339231503.png` 等 10 个时间戳命名贴图；`textures/mob_effect` 只有 5 个（缺 `bleeding.png`）；`geo` 引用了 `animations/poison_arrow.animation.json` 但实际文件叫 `poison_arrow.json`（资源不匹配）；`mob_effect/fear.png` 存在但无 `bleeding.png`。

### 4.2 corruption 数据资源（`corruption/src/generated/resources`，218 文件 / 178KB）

| 目录 | 文件数 | 体积 | 说明 |
|---|---|---|---|
| `assets/corruption/blockstates` | 26 | ~6KB | 26 方块（含 5 KING） |
| `assets/corruption/models/block` | 27 | ~10KB | 25 个方块模型 + `variable_height_crop_bottom/middle/top` 三件（`fleshbed` 方块模型在 main resources，共 26 方块） |
| `assets/corruption/models/item` | 85 | ~25KB | 自动生成物品模型（含 4 个 effect 图标孤儿模型） |
| `assets/corruption/lang` | 2 | ~21KB | `zh_cn` 175 行 / `en_us` |
| `assets/corruption/particles` | 9 | ~2KB | 9 个粒子定义 |
| `assets/corruption/sounds.json` | 1 | ~2KB | 10 条（字幕键见 2.4） |
| `data/corruption/advancements` | 15 | ~20KB | 5 个主进度 + 10 个 recipe 解锁进度 |
| `data/corruption/loot_tables/entities` | 22 | ~50KB | 无方块掉落表；缺 `infection_bat`/`heavy_guard` |
| `data/corruption/recipes` | 12 | ~5KB | 见下 |
| `data/corruption/worldgen` | 4 | ~12KB | 1 群系 + 3 模板池 |
| `data/minecraft/tags/blocks` | 2 | ~0.5KB | `mineable/pickaxe`、`needs_stone_tool`，各含 `spiracle_block`、`spread_stone` |
| `data/corruption/forge/biome_modifier` | 0 | — | 空目录（BiomeModifier 无数据） |
| `.cache` | 11 | ~20KB | datagen 缓存，可丢弃 |

**配方清单（12）**：`chitin_polymer`、`crude_axe/hoe/pickaxe/shovel/sword`、`file`、`file_folder`、`fleshbed`、`syringe`、`syringe_bloody_concentrate`、`vice_template`。
**缺口**：`poisoned_saber`、七罪 7 件、KING 5 件、芯片 4 件、酸弹/酸球、红矮星弓、debug_item、各色遗骸/装饰方块均无配方；26 方块均无 loot table。

### 4.3 与 Hall 可能重复的资源类别（去重后可省体积/工作量）

| 类别 | corruption | Hall | 重复情况 |
|---|---|---|---|
| Shader 文件 | 15 个（bloom/outline/glint/lightning） | 同名 15 个 + 85 个其他 | **15/15 完全同名**，属于同源共享资源 → corruption 侧 15 个可直接丢弃 |
| 粒子贴图 | 37 个 | 24 个 | 重复 17 个（`explosion_bloody0-15` 共 16 + `meat.png`）；corruption 独有 20 个（blood0-5、bloodsplat0-5、bloody、blood_fog0-3、degenerate_meat、degenerate_die_meat、blood4x4）；Hall 独有 6 个（`blood.png`、`heart_lose0-4.png`、`ulcerated_meat.png`） |
| 音效 | 10 个 `.ogg` | `rip_body.ogg`（字节数相同，已复制）+ `biome_music.ogg` + 3 个中文命名环境音 | `rip_body` 重复；其余 9 个需搬 |
| Lang | corruption `zh_cn` 175 行 / `en_us` | Hall `zh_cn` 140 行 / `en_us` | 键空间不同（`corruption:` vs `hall:`），无直接重复；但「王庭」语义有重复（见 2.7） |
| Geo/动画/实体贴图 | 29 / 28 / 38 | 16 / 12 / 24 | **完全不重复**（各自一套生物），corruption 侧全部要搬 |
| 物品/方块贴图 | 68 / 48 | 54 / 34 | 基本不重复（`king_*` 贴图与 Hall 王庭贴图风格相似但文件不同） |
| 物品辉光/描边 shader 与 mixin | 15 + 9 mixin | 同套升级版 | 整段丢弃 |
| `pack.mcmeta` | pack_format 15 | pack_format 15 | 一致，合并 1 份 |

---

## 5. 迁移工作量估算

> 单位：人日（1 人日 ≈ 6 小时有效工作）。前提：1~2 名熟悉两个工程与 infcore 的开发者；**不含**服务端压力测试/美术重做/新玩法设计。**S** ≤2 人日，**M** 3~7 人日，**L** 8~15 人日，**XL** 16~30 人日。

| # | 模块 | corruption 侧规模 | 复杂度 | 预估人日 | 主要阻塞点 |
|---|---|---|---|---|---|
| 1 | 工程骨架合并（modid/包名/group/mods.toml/mixin 配置/资源命名空间/JarJar/依赖统一） | 268 java 全量包名替换 + 全部资源 namespace + 2 处 mods.toml | S | 2~3 | ①GeckoLib 4.8.3→4.7.1（或反向升级）必须先验证；②全局字符串 `"corruption"` 散布在 `ModUtils.MODID`、`Corruption.MODID`、LangData、NBT key、资源路径、`ItemGlintMixinPlugin` classpath 检测里，机械替换后必须逐个核查；③mixin refmap/包名/plugin 重命名 |
| 2 | 注册表与语言（89 物品/26 方块/1 BE/34 实体/6 效果/9 粒子/10 音效/1 群系/3 模板池） | registry 12 文件 824 行 + LangData 2 文件 28KB | M | 4~6 | ①与 Hall 的 DeferredRegister 合并（一 mod 一套）；②Hall 分区创造栏需要新增分区；③缺失 lang 26 键补齐；④ID 命名冲突检查（当前无同名冲突） |
| 3 | 实体基类与族基类（`AbstractCorruptionEntity` + 6 族基类 + 管理器） | base 6 文件 1,070 行 | L | 4~6 | 与 `AbstractHallEntity` 合并：假死/走跑动画/击杀计数已覆盖；需补「伤害适应/变种/方块破坏/关键帧」；删 Brain 体系；`PathfinderMob→Monster` |
| 4 | 24 个生物实体 | population 25 个非基类文件 ≈6,100 行（不含 KING 574） | XL | 10~14 | ①全部 goal 重写（见 3.1.3）；②技能命中点与动画关键帧重测；③碰撞箱/移速按 Hall 体系重调；④`infection_player` 皮肤系统决策；⑤KING 4 个实体剔除 |
| 5 | 技能/投射物（10 实体 + SkillUtil/Fx/Dart/Pool/BoneSpike/Cuboid/Shield） | 10 文件 1,923 行 + 2 projectile 88 行 | L | 5~7 | ①`isValidTarget` 阵营判断改 infcore；②`Cuboid/Shield/RedDwarf` 的 3 个自绘渲染器与 2 个几何渲染工具要搬；③红矮星弓整套是否保留（建议作为可选项，单独 2~3 人日） |
| 6 | 方块（26）与自定义行为 | 14 文件 1,345 行 | L | 5~7 | ①`AbstractSpreadingBlock`→Hall `AbstractSpreadBlock`/`ISpreadBlock`；②`CorruptionVariableHeightCropBlock`（AGE_25+StemSection 三段生长）与 `CorruptionInfectionLeavesBlock`（DISTANCE/DECAY）自定义状态适配；③`Remains` 六向附着算法保留；④补 26 个方块掉落表 |
| 7 | 扩散/群系引擎替换（丢弃自研、接 infcore+Hall） | `SpreadRuleManager` 224 + `BiomeCorruptionProcessor` 217 + `CorruptionDiskStorage` 66 + 2×`InfectionTrackerHandler` 85 + `MixinBiomeManager` 40 + `SpreadEventHandler` 63 + `ModBiomes` 21 + 群系注册 | L | 6~8 | ①规则转 `infcore_blockspread` JSON（10 条）；②萌发池（infcore 不支持）需自留实现；③群系扩散接 Hall `HallBiomeProcessor`+维度映射；④删 `MixinBiomeManager` 后必须验证客户端群系显示；⑤净化/退化（reverseSpread）语义要在新方块里保住 |
| 8 | 节点系统（可选，见 3.4.1） | manager 226 + node/query 228 + commands 190 + debug 6.6KB + config + 6 处消费 | XL | **10~15（不搬 = 0）** | Hall/infcore 无对应；涉及 SavedData+同步+命令+刷怪阶段+属性加成+群系扩散；不搬则需另定「感染强度」来源 |
| 9 | 演化/同化/孕育者/刷怪管理 | Assimilation 229 + Bearer 156 + SpawnManager 82 + SpawnEventHandler 258 + Summon 92 + config 91 | M | 4~6 | ①规则转 `infcore_evolution` JSON（inf/evo 两类）；②刷怪体系需在 Hall 侧新建（Hall 目前为空）；③节点阶段依赖（若不搬节点则简化） |
| 10 | 物品与交互（63 非方块物品） | item 13 文件 799 行 + file 5 文件 361 行 | L | 5~7 | ①注射器转化改 infcore evolution；②`FileFolder` 容器逻辑搬但 GUI 未实现；③七罪/芯片空壳决策；④补配方/掉落（毒军刀、方块掉落、KING 除外） |
| 11 | 效果/粒子/音效 | effects 7 文件 309 行；particles 8 文件 765 行；sounds 10 | M | 3~4 | ①`MixinLivingEntity`（NonRemovable）并入 Hall；②`miasma` 雾效（客户端 `ViewportEvent.RenderFog`）搬到 Hall 客户端事件；③`corruption` 效果的「死亡转化」逻辑重写到 infcore 死亡事件；④`bleeding/fear` 补 lang 与贴图 |
| 12 | 客户端渲染与资源 | corrupt client 44 文件 5,391 行（其中 glint 15 文件≈13 万字节、client/entity 15 文件、rend 13 文件大部分与 Hall 重复） | M | 4~6 | 只搬差异化：`HumusGlowLayer`（可换 Hall 通用 `GlowLayer`）、`InfectionPlayerLayer`（动态皮肤层，需重写）、`BallRendUtils`/`IcosahedronRendUtils`/`CuboidRenderer`/`ShieldRenderer`/`CorruptionShaders`；`ClientRenderRegistry` 的头部骨骼定制行为（5 处）搬到 Hall 渲染体系；geo/anim/贴图直接复制 |
| 13 | 网络包 | 12 文件 523 行 | M | 2~3 | 只保留 4 类；`PacketSyncKeyframe`↔`IKeyframeHandler`↔动画关键帧链路要打通；`EffectSyncPacket` 两版 payload 类型不同需合并 |
| 14 | capability/savedata | 4 + 4 文件，约 340 行 | S | 2 | `Deterrence`→Hall `ThreatCapability` 语义差异（威慑值 vs 威胁点数，需定换算或直接替换）；`SyncDataHelper`/`CorruptionData`→infcore/Hall |
| 15 | mixin | 15 文件 732 行 | S | 2 | 只保留 2 个（`MixinLivingEntity`+`AccessorLivingEntity`）；`ItemGlintMixinPlugin` 同名冲突处理；包名/refmap 重命名 |
| 16 | datagen 合并 | 14 文件 1,740 行 | M | 4~5 | ①并入 Hall `DataGenEvent`；②LangData 28KB 合并；③WorldGen→HallWorldGen/HallBiome；④BlockState/Item/Sound/Particle provider 复用；⑤方块掉落表需新写；⑥`data/minecraft` 标签合并 |
| 17 | 资源整理（复制/去重/清理） | main 292 文件 1.5MB + generated | S | 2 | 丢弃 15 shader/18 粒子贴图/`rip_body`；清理垃圾贴图与空目录；检查 geo↔anim 引用 | 
| 18 | 集成联调/回归/兼容（多人、存档、光影、JEI、JarJar、全 24 生物技能） | — | L | 8~12 | ①Oculus/Embeddium 光影兼容（Hall 已有，但新增 shader/渲染实体会影响）；②存档/配置迁移验证；③全员生物技能回归（24×技能 ≈ 50+ 招式）；④服务端 TPS（扩散/生成） |

### 场景汇总

| 场景 | 包含 | 预估人日 |
|---|---|---|
| **A. MVP（推荐先做）** | 1+2+3+4+5+6+7+9+10（部分：材料/工具/注射器/温床）+11+12+13+14+15+16+17+18（缩小回归范围）；**不含节点系统、不含红矮星弓、KING/七罪/芯片/文件系统全部丢弃** | **45~60 人日** |
| **B. 全量（含节点系统）** | A + 节点系统（8）+ 红矮星弓 + 七罪/芯片/文件系统补齐 + 全量回归 | **80~100 人日** |
| **C. 全量但砍节点系统** | B − 节点系统 + 简化「感染强度」实现（约 3~5 人日替代） | **70~85 人日** |

### 强耦合必须重构的类（不重构就搬不动）

| 类 | 原因 |
|---|---|
| `AbstractCorruptionEntity` | 同时绑 Brain、SmartMove/SmartTarget、`ICorruptionEntity` 阵营、假死/适应/变种；与 `AbstractHallEntity` 职责重叠但实现不同 |
| `SmartMoveGoal` + `JumpPointPathfinder` + `SmartTargetGoal` + `RandomWanderGoal` + `FollowGoal` + `CorruptionFlyGoal` + `brains/*` | 用户已决定不搬；21 个实体的 goal 装配行必须改写 |
| `MixinBiomeManager` / `BiomeCorruptionProcessor` / `BiomeCorruptionData` / `CorruptionDiskStorage` / `InfectionTrackerHandler`×2 / `SpreadEventHandler` | 与 Hall/infcore 扩散体系二选一，只能整段替换 |
| `CorruptionNodeManager` / `NodeEventHandler` / `NodeCommands` / `NodeQueryResult` / `DebugItem` | 全新维度数据系统，Hall 无对应 |
| `AssimilationMapManager` / `BearerManager` / `SyncDataHelper` / `CorruptionData` | 与 infcore `EvolutionManager`/`GlobalDifficultyData` + Hall 难度系统功能重叠 |
| `OnlineSkinDownloader` | 客户端外部 HTTP 依赖；建议直接丢弃 |
| `ConfigDrivenSpawnModifier` + `CorruptionSpawnEventHandler` | 无数据文件/依赖节点阶段；需按合并后体系重写 |
| `RegisterTab` / `RegisterItem` / `RegisterBlock` / `EntityTypeRegistry` / `NetworkHelper` / `ConfigHelper` | 与 Hall 同名同职责，必须二选一（建议保留 Hall 并追加内容） |

---

## 6. 存档 / 兼容风险

> 触发点：modid `corruption`→`rotten_ruins`（同时 Hall `hall`→`rotten_ruins`）。Forge 1.20.1 **没有**内建的 registry ID 重映射机制（1.13+ 的 MissingMappings 事件只用于「旧 ID→新 ID」的注册缺失提示，需要自己实现）。

| 风险 | 影响 | 严重度 | 缓解建议 |
|---|---|---|---|
| 物品/方块注册名全变 | 老 corruption 存档里所有 `corruption:*` 物品变成空气（物品栏丢失/箱子内容丢失）；已放置方块变成「未知方块」→ 通常变空气或报错；Hall `hall:*` 同理 | **致命** | ①若必须兼容：为旧 ID 注册「兼容别名」（同一物品注册两个 ResourceLocation，代价是注册量×2 且创造栏需过滤）；②或写 `MissingMappingsEvent` 映射表（仅 Forge 注册表级别，NBT 里的旧 ID 仍需 datafix；工作量大）；③**建议明确放弃老存档并在发布说明中写明** |
| 实体 ID 变 | 存档内 `corruption:infection_zombie` 等实体加载失败（通常直接移除），已驯养/命名/持久化实体丢失；`heavy_guard` 若被 `PersistenceRequired` 生成过也会丢失 | 高 | 同上；若选兼容别名，实体也要映射 |
| 方块实体类型 | `corruption:fleshbed` 方块实体失效（世界内温床数据丢失） | 中 | 温床内容多为临时物品；弃档/手动迁移 |
| SavedData | `corruption_nodes`、`corruption_data`、`corruption_system_data` 等 SavedData 名是字符串（无命名空间），若合并后沿用同名类和数据名，**数据本身能读**；一旦类名/字段变化则需要 `deserialize` 兼容旧字段 | 中 | 保留旧字段名读取 + 迁移到新结构；节点系统若不搬则旧档节点数据作废 |
| 自定义磁盘数据 | `world/corruption/<dim>/corruption_data/*.cdat`（方块原状态快照）在新扩散体系下无意义；若继续加载旧存档，旧的「腐化方块反净化」不会工作 | 中 | 弃档或一次性迁移（执行净化后再切换） |
| data namespace 迁移 | `data/corruption/**` → `data/rotten_ruins/**`：配方/掉落/进度/群系/模板池全部换命名空间；老存档的进度（advancements 以 `corruption:root` 等 ID 存储）全部丢失 | 高 | 进度丢失通常可接受；如果在意，可给旧进度写 datafix 或保留旧 advancement 文件副本并注册同名（需要同 ID） |
| `data/minecraft` 标签 | `minecraft:mineable/pickaxe`、`minecraft:needs_stone_tool` 中引用了 `corruption:spiracle_block`/`spread_stone` → 改名后必须同步更新，否则工具挖掘判定失效（方块掉不了/挖得慢） | 中 | datagen 里统一更新；合并后重新生成 |
| 配置迁移 | `config/corruption/*.json5`（7 个）→ `config/rotten_ruins/...`：老用户设置全部丢失；且 corruption 的 `NodeConfig`/`EntitySpawnConfig` 等如果合并后改名/改字段，还需二次迁移 | 中 | 参照 Hall `ConfigHelper.migrateSplendidingConfig()` 模式：启动时从旧路径读一次并写入新路径，加 `configVersion` |
| JEI 插件 | 插件 UID `corruption:jei_plugin` → 新 ID；16 条物品信息页的翻译键 `jei.corruption.*` → 需改 `jei.rotten_ruins.*` 或保留旧键；合并后 JEI 中不能出现两个插件同时注册 | 低 | 合并为 Hall JEI 插件（当前 Hall 无 JEI 插件文件！corruption 的 `CorruptionJeiPlugin` 是唯一 JEI 插件，若保留需改 namespace） |
| 服务器/客户端 mod 匹配 | mods.toml 的 modId 变化 → 老客户端无法连新服务端（反之亦然）；`displayTest` 默认 MATCH_VERSION 会红叉 | 低 | 版本号明确 + 发布说明 |
| 网络协议 | 通道名 `corruption:main` → `rotten_ruins:main`，且包 ID 顺序变化；老客户端连新服会直接握手失败（本来就不兼容，无额外风险） | 低 | — |
| 第三方兼容（光影/Embeddium） | corruption 的 glint/mixin 系统被 Hall 版取代；Hall 的 `ItemOutlinePipeline` 已兼容 Oculus/Embeddium，但新增的 corruption 技能渲染（自绘 RenderType）可能影响光影；corruption 的 `ShaderConfig.use_shader` 配置语义需映射 | 低 | 合并后用「无光影/Embeddium/Oculus+光影」三环境做一轮冒烟 |
| 结构/群系 | 模板池 ID 变化 + 旧 NBT 不存在 → 无实际影响（本来就是死代码） | 低 | 直接丢弃 |
| 打包（JarJar） | GeckoLib 版本统一后 jarJar 范围要改（Hall 现在 `[4.7.1,4.8)`，若升 4.8.3 → `[4.8.3,4.9)`）；若混用「jarJar 4.7.1 + 代码按 4.8.3 编译」会导致运行时 NoSuchMethodError | 中 | 统一版本 + jarJar 范围；CI 冒烟启动 |
| Hall 自身历史 | Hall generated data 里仍存在旧命名空间 `godsick_neo/`、`splendiding/`（历史 modid 迁移遗留）。说明本项目有「改名后不清理旧数据」的先例；合并不应重蹈覆辙（旧 namespace 数据会长久留在产物里，且 `splendiding/forge/biome_modifier` 可能被误加载） | 中 | 合并时清理旧 namespace 生成数据 |

---

## 7. 附：未完成 / 死代码 / 可疑实现清单（供决策时判断「值不值得搬」）

| 项 | 位置 | 问题 |
|---|---|---|
| `AvilaEntity` 及其 Boss 条、资产 | `.../population/king/messager/AvilaEntity.java` 648 行 | **未注册**（EntityTypeRegistry 无 AVILA），Boss 战无法出现；随 KING 丢弃 |
| `HeavyGuardEntity` | 同上 39 行 | 已注册但无 AI goal、无语言、无刷怪蛋、无掉落表；随 KING 丢弃 |
| KING 方块物品 | `RegisterBlock` 5 件 | 无语言、无配方、无掉落、无用途 |
| `CorrupTionTreeGrower` / `CorrupTionTreePools` / 3 模板池 | `overworld/registry/structure/`、`WorldGenData` | 无调用方、无 NBT 结构文件；死代码 |
| `ConfigDrivenSpawnModifier` | `overworld/registry/world/` | codec 已注册但无 `data/corruption/forge/biome_modifier/*.json`；`data/corruption/forge/biome_modifier` 是空目录 |
| `ItemGeoInitializeMixin` | `mixin/` | 未列入 `corruption.mixins.json`；死文件 |
| `accessor.AccessorDisplay` / `accessor.ItemDisplayAccessor` | `mixin/accessor/` | 无使用方 |
| `SummonManager` | `overworld/manager/` | `SPAWN_POOL` 为空 builder、无调用方 |
| `RrsConfigScreen` | `config/` | 空类 |
| `RegisterStructures` | `overworld/registry/` | 空类 |
| `File`（文件物品） | `item/file/File.java` | GUI 方法全空、`getContentConfigPath` 返回 null |
| 七宗罪物品 | `SevenSinsItem` / `ISevenSins` | 仅 enum 标记，无效果、无配方 |
| 芯片物品 | `RegisterItem` 4 件 | 无功能，只有 JEI 文案 |
| `PoisonedSaberItem` | `item/` | 无注毒逻辑、无配方（只有成就在等） |
| `acid_bomb` | `RegisterItem` | 普通 Item，无使用逻辑 |
| `CorruptionSpawnManager` | `manager/spawn/` | `@Deprecated` 兼容层（委托新 handler） |
| `ModUtils.setDataHealthID/getDataHealthID` | `utils/` | 通过 mixin accessor 直接写 `DATA_HEALTH_ID`，绕过伤害流程；合并后建议改用 `entity.setHealth/hurt` 正规 API（若保留需把 accessor mixin 一并搬） |
| `CorruptionEffect`（腐化）死亡转化 | `effect/CorruptionEffect.java` | 用「效果每 tick 检查血量≤0」实现转化，实际很难触发（死亡流程会先移除效果）；且 `awardKillScore` 里的同化逻辑被整段注释 —— **当前击杀转化实际不生效**，只有注射器能转化。合并时应改为 infcore 死亡事件 + evolution 规则 |
| `LysisEffect` | `effect/` | 三个 `Map<UUID,…>` 仅部分清理，长期运行有泄漏风险（`resetAccumulatedDamage` 有调用） |
| `InfectionPlayerEntity` 皮肤 | `host/` + `OnlineSkinDownloader` | 依赖外部 HTTP（Mojang 会话服务器），离线/网络受限时表现为无贴图；建议不搬 |
| 语言缺 26 键 | 见 2.6 | 合并后游戏内会显示原始 key |
| 方块无掉落表、缺配方 | 见 4.2 | 玩法不完整；搬迁后需要补 |
| `run/config`、`run/saves`、`build/`、`run-data/logs` | corruption 工程根 | 不应进入合并范围（构建产物/存档） |
| 垃圾资源 | 见 4.1/4.2 | 时间戳贴图、`img.png`、`model (5).png`、`thanks.md`、`.cache` |

---

## 8. 建议的下一步（给 Lead 的决策清单）

1. **拍板节点系统去留**（影响 10~15 人日 + 刷怪/属性/扩散闭环的定义）。若不搬，需要确定「腐化强度」用什么替代（infcore evolution 阶段 / 群系 presence / 难度档位）。
2. **拍板 GeckoLib 版本方向**：升 Hall 到 4.8.3（动 jarJar 范围 + 需重验 patchModuleInfo/JPMS），或把 corruption 代码压到 4.7.1 API（需要实际编译验证）。
3. **拍板老存档策略**：放弃 vs 兼容别名 vs datafix（决定第 6 节风险等级）。
4. **拍板物品取舍**：红矮星弓（+2~3 人日）、七罪/芯片/文件系统（0 人日但内容空缺）、`poisoned_saber`（需补效果+配方）。
5. 先做「最小可玩闭环」切分：host 族生物 + 腐化方块扩散 + 群系 + 材料/粗制工具 + 注射器转化 + 残骸/温床 ≈ MVP 主体，其余按模块推进。
