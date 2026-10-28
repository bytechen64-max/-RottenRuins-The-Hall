# Hall 侧盘点报告 —— 「Hall + corruption → rotten_ruins」合并方案（只读分析）

- **工程根**：`RottenRuinsSplendiding`（git 分支 `1.20.1forge`，HEAD `c456091`）
- **盘点对象**：**当前工作区**，不是 HEAD 快照 —— `git status` 共 167 项未提交改动（70 修改 / 19 新增暂存 / 2 新增后删除 / 1 新增后修改 / 75 未跟踪），包含绯红誓约、陨石事件、缠绕之柱、新粒子/新着色器等未提交内容。本次分析只读，未运行 Gradle / runClient / runData，未改动任何源码与资源；唯一新增文件即本报告。
- **统计口径**：Java 行数用 `[System.IO.File]::ReadAllLines().Count`（含空行，390 个 `.java` = 66,377 行）；注册表以 `DeferredRegister` 源码 + `src/generated/resources` datagen 产物 + lang 三方交叉核对；资源用目录枚举计数。
- **合并目标（用户已定）**：单模组 `Rotten Ruins`，modid `rotten_ruins`，MC 1.20.1 Forge 47.4.x，group `org.bytechen`；Hall 侧为主基线，但 modid 由 `hall` 改为 `rotten_ruins`；重复资源（粒子等）不搬、corruption 的 KING 系列等价于 Hall 王庭系列不搬、corruption 的寻路 AI/brain 不搬、索敌改用 infcore 的阵营/感染类型判断。

---

## 0. 结论摘要（先看这 8 条）

1. **Hall 是 corruption 的分支/后继**：渲染（glint/rend/geo）、网络（UniversalPacket 路由）、数据生成、Mixin accessor、SaveData/Capability 骨架与 corruption **大量同名同源**；但 Hall 在此之上引入了 **infcore 0.1.2** 深度前置（44 处 import、8 个 API 域），corruption 的 `build.gradle` **没有 infcore 依赖**。合并后的共同底座应取 Hall 的 infcore 接线方式。
2. **Hall 历史上至少经历两次 modid 迁移：`godsick_neo` → `splendiding` → `hall`**。证据：`src/generated/resources/data/godsick_neo/`、`data/splendiding/` 空目录骨架，`run/config/splendiding/SplendidingConfig.json5`，4 个 `Splendiding*.java` 类名（另有注释中的 `SplendidingEntityManager`），59 处 mixin `@Unique splendiding$` 前缀，以及 Javadoc 里残留的 `"splendiding:cosmic"`。**先例做法＝只改 modid/包名/资源目录/config 名/lang 前缀，类名与辅助前缀全部保留**（详见 3.4，对本次迁移有直接参考价值）。
3. **注册表规模**：items 75（42 独立 + 33 方块物品）、blocks 33、block entities **0**、entities 23、effects 4、particles 5、sound events 2、features 5、attributes 5、dimension 1（`hall:heall`）、biomes 2、infcore 数据 2 类 4 条演化/43 条扩散、难度 4 档、命令 7 个。
4. **迁移面量化（粗略）**：真正“必须改且漏改会崩/静默失效”的约 **70 处源码字面量**（含硬编码 ID 12 + `"hall:"` 功能 16 + lang 键 34 + mixin/构建项）**+ 49 个含命名空间引用的手写资源 JSON + 目录/配置若干**；随 `modid` 变量自动变化的约 **247 处 `MODID` 引用 + 869 条 import + 1308 处 `hall:` 数据引用（其中 359/361 个 generated json 全靠 datagen 重新生成）**；可原样保留的 Java 标识符约 **42 个 `HallXxx` + 4 个 `Splendiding*.java` + 31 个 `Hall*.java` 文件名**。详见第 6 节。
5. **最大风险 P0：注册名与维度 ID 变化导致旧存档全面不兼容**（方块/物品/实体/群系/维度全部改 namespace），旧世界的 `world/dimensions/hall/heall` 目录也不会再被加载；除非做注册名重映射或明确放弃旧存档。
6. **静默失效风险 P0**：3 处 `"hall".equals(getSpreadType().getNamespace())` 比较（`HallVineBlock`、`HallVineFeature`、`HallSpreadEventHandler`）若漏改，迁移后**王庭藤蔓/扩散附带植物会在无报错的情况下停止生长**。
7. **启动崩溃风险 P0**：`mods.toml` 的 `[[mixins]] config = "${mod_id}.mixins.json"`、`build.gradle` 的 `config "${mod_id}.mixins.json"` + `add sourceSets.main, "${mod_id}.refmap.json"` 与 `src/main/resources/hall.mixins.json` 文件名必须同步改名（含 json 内 `package`/`refmap`/`plugin` 三字段）。
8. **不走 datagen 的资源是“手工迁移面”**：`src/main/resources` 下 55 个手写 JSON 共 99 处 `hall` 字面量，其中 49 个文件含命名空间引用（35 个 shader json 的 `"vertex"`/`"fragment"` 各 2 处、13 个手写物品模型、1 个 forge boss tag、mixin 配置等），必须人工替换 namespace，否则着色器编译失败/模型丢失。

---

## 1. 模组元信息

### 1.1 构建坐标（`gradle.properties` / `build.gradle`）

| 项 | 当前值 | 文件 |
|---|---|---|
| modid | `hall` | `gradle.properties` L10 |
| 显示名 | `RottenRuins:The Hall` | `gradle.properties` L11 |
| 版本 | `1.0.0Beta`（`build/libs` 还有旧 `0.4.0` 产物） | `gradle.properties` L13 |
| group | `org.bytechen` | `gradle.properties` L14 |
| 作者 / 描述 | `ByteChen` / `A Minecraft mod about rotten ruins and hall` | `gradle.properties` L15-L16 |
| MC / Forge / Mappings | 1.20.1 / 47.4.20 / `official`（mapping_version=1.20.1） | `gradle.properties` L3-L9 |
| Java | toolchain 17 | `build.gradle` L28-L30 |
| Gradle 插件 | ForgeGradle 6.0.16+、ParchmentMC librarian、MixinGradle 0.7-SNAPSHOT、maven-publish | `build.gradle` L1-L19 |
| 产物名 | `base.archivesName = mod_id` → `hall-<ver>.jar` / `-all.jar` / `-sources.jar` / `-dev.jar` | `build.gradle` L24-L26、L269-L288 |
| AccessTransformer | `src/main/resources/META-INF/accesstransformer.cfg`，**故意为空文件**（注释里记录了“widen everything 导致 68 个编译错误”的教训） | `build.gradle` L48 |
| runData 输出 | `args '--mod', mod_id, '--all', '--output', file('src/generated/resources/'), '--existing', file('src/main/resources/')` | `build.gradle` L81-L84 |
| infcore 获取方式 | 工程内 Maven 镜像 `libs/org/bytechen/infcore/0.1.2/*.jar|pom`，`maven { url = uri('libs') }`；`syncInfcore` 任务在升级框架时才需重跑 | `build.gradle` L105-L133、L162-L168、L303-L337；`settings.gradle` L21-L40 记录了为何不用 includeBuild |
| 特殊构建任务 | `patchModJars` / `copyPatchedMods` / `copyPatchedModsForData`（剥 cloth-config+geckolib 的 module-info 规避 JPMS 冲突）、`copyRefmap`（MixinGradle 0.7 不自动拷 refmap 的 workaround） | `build.gradle` L88-L101、L170-L225 |

### 1.2 `META-INF/mods.toml` 依赖（全部 mandatory，共 6 条）

| modId | mandatory | versionRange | side | 备注 |
|---|---|---|---|---|
| `forge` | true | `[47,)` | BOTH | — |
| `minecraft` | true | `[1.20.1,1.21)` | BOTH | — |
| `cloth_config` | true | `*` | BOTH | 同时被 jarJar |
| `geckolib` | true | `*` | BOTH | 同时被 jarJar |
| `infcore` | true | `*` | BOTH | 工程内 libs 镜像 0.1.2，外部前置（不打进 jar） |
| `curios` | true | `[5.4.0,)` | BOTH | 硬前置，compileOnly+runtimeOnly，不 jarJar |

另有 `[[mixins]] config = "${mod_id}.mixins.json"`（mods.toml L12-L13）。

### 1.3 jarJar 内容（`build.gradle` L147-L148）

| 坐标 | 版本范围 |
|---|---|
| `me.shedaniel.cloth:cloth-config-forge` | `[12.0.109,13)` |
| `software.bernie.geckolib:geckolib-forge-1.20.1` | `[4.7.1,4.8)` |

说明：jarJar 只打包这两个库；infcore/curios 是独立前置，不进 jar。JEI 为 `compileOnly`+`runtimeOnly`（可选，不在 mods.toml）。

### 1.4 Mixin 配置（`src/main/resources/hall.mixins.json`）

| 字段 | 值 |
|---|---|
| required / minVersion / compatibilityLevel | `true` / `0.8` / `JAVA_17` |
| package | `org.bytechen.hall.mixin` |
| plugin | `org.bytechen.hall.mixin.ItemGlintMixinPlugin` |
| refmap | `hall.refmap.json`（由 AP 生成，`copyRefmap` 任务手工拷进 resources） |
| mixins（common） | `MixinAnimationController`（1 条） |
| client | `CosmicAfterLevelMixin`、`GameRendererAccessor`、`GameRendererMixin`、`GameRendererOculusMixin`、`ScreenShakeMixin`、`ItemInHandRendererMixin`、`LevelRendererEmbeddiumMixin`、`ItemRendererMixin`、`MixinItemRendererCosmic`、`MixinItemRendererTwitch`、`OutlineReplayMixin`、`CrimsonVowBlockMixin`、`CreativeModeInventoryScreenMixin`、`accessor.AccessorAbstractContainerScreen`、`accessor.AccessorRenderStateShard`（15 条） |
| injectors / overwrites | `defaultRequire: 1` / `requireAnnotations: true` |

- mixin 目录实际 18 个 `.java`：15 个直下类 + 3 个 accessor；其中 `mixin/accessor/InvokerHandTransforms.java` **未被 mixin 配置引用、也无任何调用点（死代码）**，迁移时可顺手清理。
- 兼容软依赖：Embeddium/Oculus 有专门 mixin（`LevelRendererEmbeddiumMixin`、`GameRendererOculusMixin`），迁移时注意与 corruption 同名 mixin 合并去重。

### 1.5 网络通道注册方式

| 项 | 内容 |
|---|---|
| 通道 | `NetworkRegistry.newSimpleChannel(ResourceLocation(MODID, "main"), () -> "1.0", s -> true, s -> true)`，即 `hall:main`（`network/NetworkHelper.java` L28-L29） |
| 注册时机 | `FMLCommonSetupEvent.enqueueWork` → `HallMod.commonSetup` → `NetworkHelper.register()`（`HallMod.java` L78-L82） |
| 包序号 | 手写 `packetId++` 顺序注册，**客户端/服务端必须严格同序** |
| 直接注册的包（8 个） | `UniversalPacket`、`PacketSyncKeyframe`、`EffectSyncPacket`、`AnomalySyncPacket`、`S2COpenDifficultySelectPacket`、`C2SDifficultySelectPacket`、`ManaSyncPacket`（PLAY_TO_CLIENT）、`MagicPoolSyncPacket`（PLAY_TO_CLIENT） |
| 走 action 路由的包（4 个） | `HeartLosePacket`、`BloodBurstPacket`、`MeatBurstPacket`、`VoidSwordStrikePacket`，均注册到 `PacketActionRegistry`（客户端 `ClientPacketHandlers` + 服务端 `VoidSwordStrikePacket.register`），**不占用包序号** |
| 包类文件 | `network/all` 3、`network/c2s` 4（含 1 接口）、`network/s2c` 8、`network/MagicSync.java`，共 1 通道 / 15 文件 |
| 发送助手 | `sendToPlayer/sendToAllClients/sendToClient/sendToNear/sendToServer`、`sendLightningToAll/sendCubeToAll/sendMeteoriteGroundEffectToAll/sendGuiShaderToPlayer` |

### 1.6 Config（Cloth Config / AutoConfig）

| 类 | 路径注解 | 实际文件 | 说明 |
|---|---|---|---|
| `config/data/SplendidingConfig.java`（397 行） | `@Config(name = HallMod.MODID + "/SplendidingConfig")` | `run/config/hall/SplendidingConfig.json5` | 渲染/描边/火焰/电弧/裂纹等大量参数；`CONFIG_VERSION = 12`，`ConfigHelper.migrateSplendidingConfig()` 做了 12 级版本迁移（**“cloth-config 以文件为准，改代码默认值不生效”**） |
| `config/data/MeteorShowerConfig.java`（82 行） | `@Config(name = HallMod.MODID + "/MeteorConfig")` | `run/config/hall/MeteorConfig.json5` | 陨石雨事件参数 |
| `config/ConfigHelper.java`（209 行） | — | — | `AutoConfig.register` ×2；迁移逻辑；`loadAll/saveAll` |
| 历史遗留 | — | `run/config/splendiding/SplendidingConfig.json5`、`run/config/meteor_shower.json5` | **上一次改名（splendiding→hall）留下的旧配置文件，代码已不再读取** —— 说明上一位迁移者没有做配置迁移 |

> 配置路径会随 `MODID` 变量自动变成 `run/config/rotten_ruins/*`，但**旧文件不会自动搬过去**（与 splendiding 先例相同）。建议本次显式加“旧路径 → 新路径”的复制/迁移，避免玩家所有渲染设置重置。

### 1.7 Creative Tab 与分区

| 项 | 内容 |
|---|---|
| Tab 数量 | 1 个：`CREATIVE_MODE_TABS.register(MODID + "_tab", ...)` → `hall_tab`（`overworld/registry/RegisterTab.java`） |
| 标题 | `itemGroup.hall.main` → 中文「腐朽之疫:王庭」 |
| 图标 | `example_item`（**占位物品**，迁移时建议换掉） |
| 分区（5 段） | `creatures`（10 刷怪蛋）、`materials`（11）、`gear`（9）、`blocks`（32）、`misc`（兜底：遍历整个 `hall` 命名空间去重后剩余 13 个） |
| 分区实现 | `overworld/registry/SectionedCreativeModeTab.java`（134 行，自定义 `CreativeModeTab` 子类 + `withTabFactory`）＋ 客户端 `mixin/CreativeModeInventoryScreenMixin.java`（186 行，重排 `menu.items` 插入空槽隔断行）＋ `client/gui/creative/CreativeTabDividerRenderer.java`（235 行）＋ 着色器 `hall:gui_tab_divider` |
| 未显式归类、靠 `misc` 兜底的 13 个 | `example_item`、`inf_ender_pear`、`flesh_rift`、6 块法力板、`magic_cata`、`void_rune`、`wand`、`wand_nature` |

### 1.8 Datagen provider 清单（`datagen/DataGenEvent.java`，共 14 个）

| 端 | Provider | 产物 |
|---|---|---|
| client（9） | `BlockStateData` | blockstates + block models |
| | `ItemGenData` | item models |
| | `LangDataCN` / `LangDataEN` | `lang/zh_cn.json`、`lang/en_us.json` |
| | `SoundData` | `sounds.json` |
| | `ParticleData` | `particles/*.json` |
| | `EffectSpriteData` | 空 provider（无条目，mob_effect 贴图未接入 atlas 源） |
| | `EvolutionDataProvider`（继承 infcore datagen） | `data/hall/infcore_evolution/default.json` |
| | `BlockSpreadDataProvider`（继承 infcore datagen） | `data/hall/infcore_blockspread/default.json` |
| server（5） | `HallWorldGenData`（`DatapackBuiltinEntriesProvider`：NOISE→NOISE_SETTINGS→CONFIGURED_FEATURE→PLACED_FEATURE→BIOME→DIMENSION_TYPE→LEVEL_STEM） | worldgen + dimension + dimension_type |
| | `BlockTagData` / `ItemTagData` | `data/minecraft/tags/*` |
| | `RecipeProviderData` | recipes + 自动 recipe advancements |
| | `LootTableData` | loot_tables |

说明：无独立 AdvancementsProvider（recipe unlock 由 `RecipeProviderData` 自动产出 42 个）；无 biome_modifier 数据（`HallMod` 里 `BIOME_MODIFIER_SERIALIZERS` 注册器存在但**没有注册任何 codec 条目**）。

---

## 2. 注册表精确 ID 清单（id + 中文名）

> 命名空间当前全部为 `hall`；合并后变为 `rotten_ruins`（**namespace 必变、path 建议不变**）。中文名取自 `src/generated/resources/assets/hall/lang/zh_cn.json`；标注 `—` 者为 lang 缺失。

### 2.1 概览

| 注册表 | 数量 | 来源（相对工程根） |
|---|---|---|
| Items | **75**（42 独立 + 33 方块物品） | `src/main/java/org/bytechen/hall/overworld/registry/RegisterItem.java`、`RegisterBlock.java` |
| Blocks | **33** | `.../registry/RegisterBlock.java` |
| Block Entities | **0** | `.../registry/RegisterBlockEntities.java`（只有空注册器与注释 “Your block entities here”） |
| Entity Types | **23**（10 生物 + 13 技能/投掷物/视觉实体） | `.../registry/EntityTypeRegistry.java` |
| Mob Effects | **4** | `.../registry/RegisterEffect.java` |
| Particles | **5** | `.../registry/RegisterParticles.java` |
| Sound Events | **2** | `.../registry/SoundEventRegistry.java` |
| Features | **5** | `.../registry/RegisterFeature.java` |
| Attributes | **5** | `.../registry/RegisterAttributes.java` |
| Dimensions | **1**（`hall:heall`，含 dimension_type 与 LevelStem 同名） | `.../registry/HallDimensions.java`、`datagen/gen/FleshDimensionData.java` |
| Biomes | **2**（`flesh_marrow`、`hall_wasteland`） | `datagen/gen/HallBiomeData.java` |
| Configured / Placed Feature | 5 / 5 | `datagen/gen/HallFeatureData.java` |
| Noise / NoiseSettings | 2（`terrain`、`detail`）/ 1（`flesh_marrow`） | `datagen/gen/HallNoiseData.java`、`FleshDimensionData.java` |
| infcore 数据 | 演化 4 条（`hall:inf` ×2、`hall:evo` ×2）/ 扩散 43 条（`hall:spread`） | `src/generated/resources/data/hall/infcore_{evolution,blockspread}/default.json` |
| 难度（infcore 注册表） | 4（easy/normal/hard/incomprehensible） | `overworld/difficulty/HallDifficulty.java` |
| Capability / Attachment | 4（anomaly、threat、magic_pool、mana） | `.../registry/CapabilityRegistry.java` |
| SavedData | 2（`hall_spread_data`、`hall_difficulty_selected`） | `.../swarm/HallSpreadSaveData.java`、`.../difficulty/HallWorldData.java` |
| 命令 | 7 | `command/*.java`，统一在 `event/ForgeEventHandler#onRegisterCommands` 注册 |

### 2.2 Items（42 个独立物品）

| # | id | 中文名 | 实现类/备注 |
|---|---|---|---|
| 1 | `example_item` | 测试物品 | 模板占位物品，被用作 creative tab 图标 |
| 2 | `void_sword` | 伪-虚无之刃 | `VoidSword`（cosmic loader，裸把强制斩杀） |
| 3 | `crimson_vow` | 绯红誓约 | `CrimsonVow`（cosmic loader style 17 + mask 泛光） |
| 4 | `silent_daylight` | 寂寒白日 | `SilentDaylight`（cosmic loader style 18 水面湍流） |
| 5 | `inf_ender_pear` | 畸骸末影珍珠 | `InfEnderPearItem`（twitch 抖动特效） |
| 6 | `acid_anomaly_extract` | 酸异常提取物 | `BaseGlowingGeoItem`（geo 模型+泛光） |
| 7 | `cold_anomaly_extract` | 冷异常提取物 | 同上 |
| 8 | `heat_anomaly_extract` | 热异常提取物 | 同上 |
| 9 | `hall_tendon` | 王庭肌腱 | 简单材料 |
| 10 | `hall_bone_fragments` | 王庭骨 | 简单材料 |
| 11 | `domite_ore` | 穹顶胚晶矿 | 简单材料 |
| 12 | `domite_crystal` | 穹顶胚晶 | 简单材料 |
| 13 | `domerite_ore` | 穹顶云铁矿 | 简单材料 |
| 14 | `domerite_ingot` | 穹顶云铁锭 | 简单材料 |
| 15 | `domerite_stick` | 穹顶只杖 | 简单材料 |
| 16 | `bonecrusher_core` | 碎骨核心 | 简单材料 |
| 17 | `domerite_sword` | 云顶之剑 | `DomeriteSword`（属性随 Y 层缩放） |
| 18 | `domerite_pickaxe` | 云顶之镐 | `DomeritePickaxe` |
| 19 | `domerite_axe` | 云顶之斧 | `DomeriteAxe` |
| 20 | `domerite_shovel` | 云顶之锹 | `DomeriteShovel` |
| 21 | `domerite_hoe` | 云顶之锄 | `DomeriteHoe` |
| 22 | `domerite_longsword` | 天穹裁决 | `DomeriteLongsword`（777 行，单键三态技能） |
| 23 | `death_mana_plate` | 死亡魔法版材 | 简单材料（“版材”原文如此） |
| 24 | `fire_mana_plate` | 火焰魔法版材 | `ManaPlateItem` + 燃烧描边（ICustomOutline） |
| 25 | `light_mana_plate` | 光明魔法版材 | `ManaPlateItem` + mask 泛光 + 描边 |
| 26 | `nature_mana_plate` | 自然魔法版材 | 简单材料 |
| 27 | `void_mana_plate` | 虚空魔法版材 | 简单材料 |
| 28 | `water_mana_plate` | 水魔法版材 | 简单材料 |
| 29 | `magic_cata` | 魔法催化剂 | 简单材料 |
| 30 | `void_rune` | 虚空符文 | 简单材料 |
| 31 | `wand` | 法杖 | `WandItem(MagicType.MAGIC)` |
| 32 | `wand_nature` | 自然法杖 | `WandItem(MagicType.NATURE)` |
| 33-42 | `inf_player_spawn_egg`、`inf_enderman_spawn_egg`、`inf_skeleton_spawn_egg`、`scout_spawn_egg`、`pursuer_spawn_egg`、`monolith_spawn_egg`、`bonecrusher_spawn_egg`、`heavy_bomb_spawn_egg`、`collapsar_spawn_egg`、`winding_column_spawn_egg` | 畸骸玩家刷怪蛋、畸骸末影人刷怪蛋、畸骸骷髅刷怪蛋、溃烂纠察刷怪蛋、溃烂追蹤者刷怪蛋、溃烂巨岩刷怪蛋、王庭碎骨刷怪蛋、王庭重型轰炸刷怪蛋、萨米使徒刷怪蛋【未完成】、缠绕之柱刷怪蛋 | `ForgeSpawnEggItem` |

### 2.3 Blocks（33 个，均带 BlockItem → 计入 75 物品）

| # | id | 中文名 | 说明 |
|---|---|---|---|
| 1 | `hall_pillar` | 王庭石柱 | 可旋转柱状，石质属性 |
| 2 | `hall_grass_block` | 王庭草方块 | `SpreadBlock`（可被 infcore 扩散替换） |
| 3 | `hall_dirt` | 王庭泥土 | `SpreadBlock` |
| 4 | `hall_stone` | 王庭石块 | `SpreadBlock` |
| 5 | `hall_log` | 王庭原木 | `SpreadPillarBlock` |
| 6 | `hall_leaves` | 王庭树叶 | 终端方块（不扩散） |
| 7 | `domite_mineral` | 穹顶胚晶矿石 | 黑曜石属性 |
| 8 | `domerite_mineral` | 穹顶云铁矿石 | 黑曜石属性 |
| 9 | `hall_planks` | 王庭木板 | 可燃变体 |
| 10 | `hall_stairs` | 王庭楼梯 | 可燃 |
| 11 | `hall_slab` | 王庭台阶 | 可燃 |
| 12 | `hall_fence` | 王庭栅栏 | 可燃 |
| 13 | `hall_fence_gate` | 王庭栅栏门 | 可燃 |
| 14 | `hall_door` | 王庭木门 | 原版 DoorBlock |
| 15 | `hall_trapdoor` | 王庭木活板门 | 原版 TrapDoorBlock |
| 16 | `hall_button` | 王庭按钮 | 原版 ButtonBlock |
| 17 | `hall_pressure_plate` | 王庭压力板 | 原版 PressurePlateBlock |
| 18 | `hall_stone_stairs` | 王庭石楼梯 | — |
| 19 | `hall_stone_slab` | 王庭石台阶 | — |
| 20 | `hall_stone_wall` | 王庭石墙 | — |
| 21 | `hall_stone_button` | 王庭石质按钮 | — |
| 22 | `hall_stone_pressure_plate` | 王庭石质压力板 | — |
| 23 | `hall_vine` | 王庭藤蔓 | 自定义双向藤蔓（可扩散，284 行实现） |
| 24 | `hall_grass` | 王庭草 | 自定义植物 |
| 25 | `hall_flower` | 王庭花 | 自定义植物 |
| 26 | `hall_sandstone` | 王庭砂岩 | 烬痕沙漠系列 |
| 27 | `hall_ash_sand` | 王庭烬痕沙子 | SandBlock |
| 28 | `hall_ash_cut_sandstone` | 王庭烬痕切制砂岩 | — |
| 29 | `hall_ash_smooth_sandstone` | 王庭烬痕平滑砂岩 | — |
| 30 | `hall_ash_collapsed_chiseled_sandstone` | 王庭烬痕坍缩雕纹砂岩 | — |
| 31 | `hall_ash_cactus` | 王庭烬痕仙人掌 | 自定义（176 行） |
| 32 | `hall_ash_dead_bush` | 王庭烬痕枯灌木 | — |
| 33 | `flesh_rift` | 血肉裂隙 | 右键传送到 `hall:heall` 维度（发光） |

> 资源里存在 `hall_bedrock.png` 但**没有对应注册方块**（未使用贴图）。

### 2.4 Entity Types（23 个：10 生物 + 13 技能/投掷物/视觉实体）

| # | id | 中文名 | 类别 | 说明 |
|---|---|---|---|---|
| 1 | `inf_player` | 畸骸玩家 | MONSTER 生物 | 感染玩家形态（529 行） |
| 2 | `inf_enderman` | 畸骸末影人 | MONSTER | — |
| 3 | `inf_skeleton` | 畸骸骷髅 | MONSTER | — |
| 4 | `scout` | 溃烂纠察 | MONSTER | 溃烂兜底形态（体积<2 档） |
| 5 | `pursuer` | 溃烂追蹤者 | MONSTER | 2~8 档转化形态 |
| 6 | `monolith` | 溃烂巨岩 | MONSTER | >8 档转化形态 |
| 7 | `bonecrusher` | 王庭碎骨 | MONSTER | 5×5 碰撞箱 |
| 8 | `heavy_bomb` | 王庭重型轰炸 | MONSTER | 3×3 |
| 9 | `winding_column` | 缠绕之柱 | MONSTER | 防御型，735 行实现 |
| 10 | `collapsar` | 萨米使徒 | MONSTER | BOSS，1031 行实现 |
| 11 | `inf_skeleton_arrow` | 畸骸骷髅炸弹 | MISC | 投掷物/技能 |
| 12 | `heavy_bomb_tnt` | 重型炸弹TNT | MISC | 技能 |
| 13 | `meteor_event` | — | MISC | 陨石事件（追踪 32 区块） |
| 14 | `meteorite` | 陨石 | MISC | — |
| 15 | `shockwave` | 冲击波 | MISC | — |
| 16 | `sword_aura` | — | MISC | 剑气 |
| 17 | `verdict_beam` | — | MISC | 天穹裁决垂直光柱 |
| 18 | `verdict_field` | — | MISC | 裁决领域（有逻辑的伤害结算） |
| 19 | `verdict_sword_drop` | 裁决落剑 | MISC | — |
| 20 | `collapse` | — | MISC | 坍缩 |
| 21 | `crimson_vow_charge` | — | MISC | 绯红誓约蓄力法阵（同步间隔 1、追踪 8 区块） |
| 22 | `crimson_vow_field` | — | MISC | 绯红誓约巨型法阵（同步间隔 1、追踪 20 区块） |
| 23 | `black_hole` | — | MISC | 黑洞（纯视觉） |

> lang 覆盖缺口：**8 个实体无中文名**（`meteor_event`、`sword_aura`、`verdict_beam`、`verdict_field`、`collapse`、`crimson_vow_charge`、`crimson_vow_field`、`black_hole`），调试时显示原始 translation key。

### 2.5 Effects（4）

| id | 中文名 | 说明 |
|---|---|---|
| `heat_anomaly_adaptation` | 热异常适应 | 异常进度免疫 15s |
| `cold_anomaly_adaptation` | 冷异常适应 | 同上 |
| `acid_anomaly_adaptation` | 酸异常适应 | 同上 |
| `verdict` | 裁决 | 天穹裁决覆盖增益（光柱半径/出剑速度） |

> `textures/mob_effect/` 只有 3 张贴图（缺 `verdict.png`）。

### 2.6 Particles（5）

| id | 客户端实现 | 贴图 |
|---|---|---|
| `ulcerated_meat` | `UlceratedMeatParticle` | `textures/particle/ulcerated_meat.png` |
| `heart_lose` | `HeartLoseParticle` | `heart_lose0..4.png`（5 帧序列） |
| `blood` | `BloodParticle`（新，未提交） | `blood.png` |
| `meat` | `MeatParticle`（新，未提交） | `meat.png` |
| `explosion_bloody` | `ExplosionBloodyParticle`（新，未提交） | `explosion_bloody0..15.png`（16 帧） |

> 与 corruption 的同名/同源粒子（blood/meat/explosion_bloody 等）按用户决定**不搬 corruption 版**，保留 Hall 版。

### 2.7 Sound Events（2）

| id | 字幕键/中文 | 文件 |
|---|---|---|
| `biome_music` | 无字幕 | `textures/../sounds/biome_music.ogg`（stream） |
| `rip_body` | `subtitles.hall.rip_body` = 躯体爆裂 | `sounds/rip_body.ogg`（从 corruption 复制） |

> `sounds/` 里另有 3 个**未在 sounds.json 引用**的中文名 ogg：`咆哮环境音-1.ogg`、`咆哮环境音-2 .ogg`（文件名内含空格）、`咆哮环境音-3.ogg` —— 迁移/打包前需决定是接入还是删除。

### 2.8 Features（5 + 世界生成配套）

| Java Feature id | ConfiguredFeature | PlacedFeature |
|---|---|---|
| `hall_tree` | `hall:hall_tree` | `hall:hall_trees` |
| `hall_grass_patch` | `hall:hall_grass_patch` | `hall:hall_grass_patches` |
| `hall_flower_patch` | `hall:hall_flower_patch` | `hall:hall_flower_patches` |
| `hall_vine_down` | `hall:hall_vine_down` | `hall:hall_vines_down` |
| `hall_vine_up` | `hall:hall_vine_up` | `hall:hall_vines_up` |

### 2.9 Attributes（5，仅挂到玩家）

| id | 中文名 | 默认 / 类型 |
|---|---|---|
| `max_mana` | 法力上限 | 0，加成 |
| `mana_regeneration` | 法力回复速度 | 0，加成（点/秒） |
| `mana_restore` | 法力回复量 | 1.0，倍率 |
| `spell_power` | 法术强度 | 1.0，倍率 |
| `magic_slots` | 魔法槽位 | 0，加成（上限 54） |

> 5 个属性构造器里的翻译键是**硬编码字符串** `attribute.name.hall.*`（`RegisterAttributes.java` L62-L85），datagen 侧用 `MODID` 拼接 —— 迁移时两边必须一起改，否则属性界面显示原始 key。

### 2.10 Dimension / Biomes

| 项 | id | 中文名 | 备注 |
|---|---|---|---|
| DimensionType | `hall:heall` | — | min_y=0 / height=256 / 开放天空 / 固定 6000 tick |
| LevelStem（维度本体） | `hall:heall` | 血肉庭园 | 噪声区块生成器 + 固定群系 `flesh_marrow`；`FleshRiftBlock` 传送目标 |
| Dimension 的 `effects` 字段 | `hall:heall` | — | **客户端未注册对应 `DimensionSpecialEffects`**（datagen 注释说明是刻意不注册，避免顶掉 Forge 默认实现）；该 id 目前是悬空引用，改 namespace 时一并改 |
| Biome | `hall:flesh_marrow` | 血肉庭园 | 维度唯一群系；lang 有 `biome.hall.flesh_marrow` |
| Biome | `hall:hall_wasteland` | —（无 lang） | 主世界方块/群系扩散目标（`HallSpreadEventHandler` 映射 Level.OVERWORLD → 此群系），独立生成的第二群系，未被任何维度引用 |

worldgen 数据文件数量：`worldgen/biome` 2、`configured_feature` 5、`placed_feature` 5、`noise` 2、`noise_settings` 1、`dimension` 1、`dimension_type` 1。

### 2.11 其它“准注册表”条目

| 类别 | 内容 |
|---|---|
| Capability / Attachment（`CapabilityRegistry`） | `hall:anomaly`、`hall:threat`、`hall:magic_pool`、`hall:mana` |
| SavedData id | `hall_spread_data`（`HallSpreadSaveData`）、`hall_difficulty_selected`（`HallWorldData`，存档名 `hall_difficulty_selected`） |
| 世界快照目录 | `<world>/hall_data`（`HallBiomeProcessor` 的方块快照，用于净化回退） |
| 难度（infcore DifficultyRegistry） | `hall:easy`、`hall:normal`、`hall:hard`、`hall:incomprehensible` |
| infcore 感染类型 | `HallMod.INFECTION_TYPE = hall:inf`；兜底类型 `new ResourceLocation(MODID, "hall")`（`AbstractHallEntity` L253）→ `hall:hall` |
| infcore 扩散类型 | `hall:spread`（`HallSpreadEventHandler` 内 `new ResourceLocation(MODID, "spread")`） |
| 命令 | `/anomaly`、`/spread`、`/collapse`、`/blackhole`、`/hall`（`ThreatCommand` 根命令）、`/crimsonvow`、`/meteorevent` |
| 网络通道 | `hall:main` |
| 模型 loader id | `hall:cosmic`、`hall:mask_layers`（JSON 里的写法；注册时用裸名 `cosmic`/`mask_layers`，见 6.1） |
| 着色器 | 35 个 core shader（`assets/hall/shaders/core/*.json`），名字如 `hall:cosmic`、`hall:outline_mask` 等 |

### 2.12 lang 覆盖情况（`zh_cn.json` / `en_us.json` 各 138 键）

| 键前缀 | 数量 | 备注 |
|---|---|---|
| `item.hall.*`（精确物品键） | 42 | 覆盖全部 42 个独立物品；33 个 BlockItem 用 `block.hall.*` |
| `block.hall.*`（精确方块键） | 33 | 全部 33 个方块（含 `flesh_rift.no_dimension` 等附加键共 34 行） |
| `entity.hall.*` | 15 | 23 个实体缺 8 个（见 2.4） |
| `effect.hall.*` | 4 | 全 |
| `attribute.name.hall.*` | 5 | 全 |
| `itemGroup.hall.*` | 6 | 1 tab + 5 分区标题 |
| `biome.hall.*` / `dimension.hall.*` / `subtitles.hall.*` | 1 / 1 / 1 | `hall_wasteland` 无 lang |
| `hall.difficulty.*`、`hall.gui.*`、`hall.tooltip.*` | 15 | 硬编码在 `TranslateUtils` |
| 其它（`item.hall.*.tooltip.*`、`.cooldown` 等） | 19 | 硬编码在 `TranslateUtils`/`LangData` |

---

## 3. 资源清单

### 3.1 assets（`src/main/resources/assets`，手写资源：319 文件；`src/main/resources` 合计 325 文件）

| 目录 | 数量 | 关键内容 |
|---|---|---|
| `assets/hall/animations` | 12 json | geckolib 动画（`inf_enderman`、`inf_player`、`pursuer`、`winding_column`、`example_geo_item`、`acid_anomaly_extract` 等） |
| `assets/hall/geo` | 16 json | geckolib 模型（`inf_enderman`、`inf_player`、`inf_skeleton`、`pursuer`、`scout`、`monolith`、`bonecrusher`、`hall_heavy_bomb`、`winding_column`、`collapsar` 等） |
| `assets/hall/models/item` | 14 json | **手写**物品模型：3 个 cosmic/mask loader 模型（`void_sword`、`crimson_vow`、`silent_daylight`）+ 9 个普通/geo 物品 + 1 个父模板 `base_tool.json` |
| `assets/hall/shaders/core` | 100 文件 | 35 `.json` + 35 `.fsh` + 30 `.vsh`；覆盖 cosmic/mask/outline/glint/backplate/火焰/电弧/裂纹/裁决/陨石/黑洞/GUI 等整套着色器 |
| `assets/hall/textures/block` | 34 png | 王庭方块/烬痕砂岩/矿石/门/藤蔓等 |
| `assets/hall/textures/entity` | 23 png + 1 mcmeta | 10 个生物贴图 + 13 个 `*_glow_layer.png` |
| `assets/hall/textures/gui` | 8 png | 难度选择图标、collapsar 血条等；**含 2 张疑似垃圾图**（`pixil-frame-0.png`、`Untitled 02-21-2026 09-18-21 (1).png`） |
| `assets/hall/textures/item` | 54 png | 物品贴图（含 `winding_column_spawn_egg` 等新增未提交文件） |
| `assets/hall/textures/mob_effect` | 3 png | 缺 verdict 贴图 |
| `assets/hall/textures/particle` | 24 png | blood 1 + meat 1 + ulcerated_meat 1 + heart_lose 5 + explosion_bloody 16 |
| `assets/hall/textures/shader` | 12 png + 12 mcmeta | `cosmic_0..11` 序列帧（由 `assets/minecraft/atlases/blocks.json` 的 `directory: shader` 源拼进方块图集；该 atlas 配置命名空间无关，**无需改**） |
| `assets/hall/sounds` | 5 ogg | 2 个被 sounds.json 引用 + 3 个中文名未引用文件 |
| `assets/minecraft/atlases/blocks.json` | 1 json | 把 `textures/shader/` 加进 blocks atlas |
| 顶层（`src/main/resources` 根） | `pack.mcmeta`（description = “hall resources”）、`hall.mixins.json`、`hall.png`（135KB，`mods.toml` 未用 `logoFile` 引用，属未使用 logo） | 前两者含 modid 需要改；`hall.png` 建议改名并挂进 `logoFile` 或删除 |

### 3.2 data（`src/main/resources` 手写仅 1 个；其余全部在 generated）

| 位置 | 内容 |
|---|---|
| `src/main/resources/data/forge/tags/entity_types/bosses.json` | `["hall:collapsar"]`（唯一手写 data 文件，需手改 namespace） |
| `src/generated/resources/data/hall/recipes` | 42 个配方 |
| `src/generated/resources/data/hall/advancements/recipes` | 42 个配方解锁进度（18 building_blocks / 2 combat / 4 decorations / 5 misc / 7 redstone / 6 tools） |
| `src/generated/resources/data/hall/loot_tables` | 33 方块掉落 + 2 实体掉落 |
| `src/generated/resources/data/minecraft/tags/blocks` + `items` | 49 个 tag 文件（28 + 21），内容全是 `hall:*` 引用 |
| `src/generated/resources/data/hall/worldgen/**` | 2 biome + 5 configured + 5 placed + 2 noise + 1 noise_settings |
| `src/generated/resources/data/hall/dimension`、`dimension_type` | `heall` 各 1 |
| `src/generated/resources/data/hall/infcore_evolution/default.json` | 4 条：`hall:inf`（player→inf_player、skeleton→inf_skeleton）、`hall:evo`（monolith→bonecrusher/heavy_bomb） |
| `src/generated/resources/data/hall/infcore_blockspread/default.json` | 43 条 vanilla→hall 方块映射（草/泥土/8 种原木/10 种树叶/12 种花/2 种草/8 种木板/石头） |
| `src/generated/resources/{assets/hall,data/hall,data/minecraft}` | 见 3.1 与上表；总 361 个 json（blockstates 33、models/block 69、models/item 64、lang 2、sounds 1、particles 5 等）+ 14 个 `.cache` |
| `src/generated/resources/data/splendiding/**`、`data/godsick_neo/**` | **空目录骨架**（见 3.3） |

### 3.3 `data/splendiding` 遗留命名空间：到底是什么、还在不在用

**结论：它是上一次改名前（modid = `splendiding`）的 datagen 输出目录残骸，现在只是空目录骨架；没有任何数据文件、没有任何代码/数据引用。**

证据链：

| 证据 | 位置 | 说明 |
|---|---|---|
| 空目录骨架 | `src/generated/resources/data/splendiding/{advancements/recipes/{building_blocks,decorations,redstone},forge/biome_modifier,loot_tables/blocks,recipes}`（共 8 个空目录） | 文件系统里 0 文件；git 不跟踪空目录，所以 `git status` 也不显示 |
| 更早一次的残留 | `src/generated/resources/data/godsick_neo/infcore_evolution/`（空目录） | 对应更早的 modid `godsick_neo`（同名工程 `Project/GodSickNeo`：`mod_id=godsick_neo`、`org.bytechen.godsick_neo`、Forge 47.4.21） |
| 旧配置路径 | `run/config/splendiding/SplendidingConfig.json5`、`run/config/meteor_shower.json5`（dev run 目录，不随 jar 发布） | `@Config(name = MODID + "/SplendidingConfig")` 在上一个 modid 下生成的旧文件，改名后代码只读 `config/hall/...` |
| 类名残留 | `SplendidingConfig`、`SplendidingShaders`、`SplendidingMaterials`、`SplendidingSpawnPlacements`（4 个文件）＋ 注释中的 `SplendidingEntityManager` | Java 标识符，不参与命名空间 |
| mixin 前缀残留 | 59 处 `splendiding$dividerCache`、`splendiding$getMenu` 等 `@Unique` 成员名 | 上一次迁移**没有**清理 mixin 辅助前缀 |
| Javadoc 残留 | `api/ICosmicLayer.java`、`client/cosmic/{CosmicClient,CosmicRenderHelper,GeometryLoaderCosmic}.java` 共 5 处 `splendiding:cosmic` / `splendiding:item/...` 示例 | 上一次迁移**没有**清理注释里的 namespace |
| 是否仍被引用 | 全仓库 `splendiding:` 命中 5 处（全在 Javadoc）；`"splendiding"` 字面量 2 处（Javadoc 示例）；`assets/splendiding`、`data/splendiding` 路径引用 **0**；`.cache` 中 **0** 条 splendiding 记录 | 属“死引用” |
| git 历史 | `git log --all -- '*splendiding*'` 无任何提交 | 改名发生在 git init 之前（初始提交 `99f367b` 已是 hall） |

### 3.4 `splendiding → hall` 迁移先例做法复盘（对本次迁移的直接参考）

上一位迁移者实际做的（从现状反推）：

| 改动项 | 做法 | 证据 |
|---|---|---|
| modid 常量 / 构建变量 | `gradle.properties` 的 `mod_id`、`HallMod.MODID`、`build.gradle` 全部改成 `hall`，注册器/网络/资源路径统一引用 `MODID` 变量 | 247 处 `MODID` 引用，硬编码 `"hall"` 只剩 19 处 |
| Java 包名 | 包名跟着 modid 改为 `org.bytechen.hall`（390 文件） | 无 `org.bytechen.splendiding` 残留 |
| 资源目录 | `assets/<ns>`、`data/<ns>`、mixin json 文件名、refmap 名全部改名；datagen 重跑 | 现 `assets/hall`、`data/hall`、`hall.mixins.json`、`hall.refmap.json` |
| 类名 / 代码风格 | **不改**：`Splendiding*` 类名、`splendiding$` mixin 前缀、Javadoc 示例全部保留 | 见 3.3 |
| config | 路径跟随 `MODID` 自动变；**不做旧文件迁移**，旧 `config/splendiding/` 留在原地 | `run/config` 下两套并存 |
| 旧 datagen 输出 | **不清理**，残留空目录骨架 | 3.3 |
| 存档兼容 | 未处理（改名即不兼容） | — |

**启示**：modid 改名本身代码量不大（真正的手工改动集中在 ~70 处字面量 + 49 个手写资源 json + 目录改名 + datagen 重跑），但留下了 **配置未迁移、旧目录未清理、注释/类名残留** 三类技术债。本次 rotten_ruins 迁移建议补上：①配置显式迁移；②`src/generated/resources` 整体清空重跑；③一次性全局替换注释里的 `hall:` 示例；④决定是否顺手清理 `Splendiding*`/`splendiding$` 债（纯风格，可延后）。

### 3.5 其它资源问题（合并前建议处理）

| 问题 | 位置 | 建议 |
|---|---|---|
| 3 个未引用中文名音效（含文件名空格） | `assets/hall/sounds/咆哮环境音-*.ogg` | 接入 sounds.json 或删除（发布前必处理，中文文件名在部分环境有编码风险） |
| 未被引用的 mod logo | `src/main/resources/hall.png`（135KB；mods.toml 无 `logoFile`） | 改名并在 mods.toml 挂 `logoFile`，或删除 |
| 2 张疑似垃圾 GUI 贴图 | `textures/gui/pixil-frame-0.png`、`Untitled 02-21-2026 09-18-21 (1).png` | 删除或归档 |
| 未使用方块贴图 | `textures/block/hall_bedrock.png`（无对应注册方块） | 删除或补注册 |
| 缺失贴图 | `textures/mob_effect/verdict.png`；实体 8 个缺 lang | 补 |
| 空目录 | `src/main/resources/data/hall/`、`textures/models/`、`generated/data/{splendiding,godsick_neo}` | 迁移前一并清理 |
| `example_item` 作为 tab 图标 + 出现在物品表 | `RegisterItem` / `RegisterTab` | 发布前替换/移除 |
| 音效 `biome_music` 无字幕键 | `SoundEventRegistry` / `SoundData` | 可选补 |

---

## 4. 架构模块清单与体量（Java 390 文件 / 66,377 行）

### 4.1 顶层模块

| 模块 | 文件 | 行数 | 职责 | Hall 独有 / 与 corruption 同源 |
|---|---|---|---|---|
| `client` | 145 | 26,287 | 全部客户端渲染/交互（cosmic 星空、mask 泛光层、fx 特效、glint 描边、backplate 背板、tooltip、geo 实体渲染、GUI、粒子、音效事件） | 渲染底座与 corruption 同源；cosmic/mask/fx/backplate/tooltip/particle/crimson-vow 系列为 Hall 独有 |
| `overworld` | 134 | 25,420 | 注册表、实体、方块、物品、capability、save data、难度、扩散（swarm） | 注册器/实体底座/扩散骨架与 corruption 同源；王庭/使徒/溃烂/畸骸/魔法内容独有 |
| `datagen` | 19 | 2,872 | 14 个 provider | 骨架与 corruption 同源（12 个同名 provider），infcore 两个 provider 为 Hall 独有 |
| `api` | 21 | 2,563 | 公共 API：`anomaly`（异常）、`magic`（法术/属性/法力）、`mask`（遮罩层）、cosmic/tooltip 接口 | 全部 Hall 独有（corruption 的 api 只有 6 个文件且内容不同） |
| `utils` | 11 | 2,277 | combat（远程攻击 840 行）、entity（粒子/破块/buff）、spell damage、translate、tick | `utils/entity` 与 corruption 同源；combat/魔法相关独有 |
| `event` | 13 | 1,751 | Forge 事件总线 + impl 业务（异常、魔力、黑洞物理、陨石、heavy bomb 死亡） | 部分同源 |
| `mixin` | 18 | 1,534 | 16 条配置 + 插件 + 1 个未用 Invoker | 9 个与 corruption 同名同源；cosmic/twitch/creative-tab 相关为 Hall 独有 |
| `network` | 17 | 1,358 | 1 通道 / 8 消息 / 4 action 路由 | 骨架同源 |
| `command` | 7 | 1,061 | 7 个调试/管理命令 | 独有 |
| `config` | 3 | 800 | 2 个 AutoConfig + 迁移逻辑 | 独有 |
| `compat` | 1 | 356 | `BCCoreCompat`（BCCore 软兼容） | 独有 |
| 根 | 1 | 98 | `HallMod` 主类 | — |

### 4.2 二级子包

| 子包 | 文件 | 行数 | 说明 |
|---|---|---|---|
| `client/rend` | 46 | 10,526 | 渲染核心：`ItemOutlinePipeline`(2265)、`SplendidingShaders`(743)、`GlintEffectProfile`/`HeldItemOutline*`/`ShaderFeature*`、backplate、GUI shader、lightning/hitbox/zoom/CDRenderHelper、`MeteorScreenEffect`(164)、`ScreenShake`(84) |
| `overworld/registry` | 121 | 23,476 | 注册器 13 个 + items（30+ 文件）+ blocks + features + entities（population/ai/base，约 60 文件） |
| `client/entity` | 45 | 8,431 | geo 渲染底座 + 17 个技能/技能特效 renderer（CollapseRenderer 948、CrimsonVowFieldRenderer 670、MeteoriteRenderer 554） |
| `overworld/difficulty` | 7 | 983 | 4 档难度 + 属性倍率 + 同步 + 事件 + 配置重载 |
| `client/cosmic` | 11 | 1,405 | 星空物品渲染（模型 loader、render type、shader、late queue） |
| `client/mask` | 16 | 1,605 | 遮罩泛光层（模型 loader、json 解析、registry、render type、GlowMaskPass） |
| `client/fx` | 13 | 1,470 | 物品特效框架（Arc/Flame/Fracture/Outline 特效、字段池、float target） |
| `client/gui` | 4 | 1,239 | 难度选择界面(433) + 隔断色带渲染(235) |
| `overworld/registry/entities/*` | — | ~11,000 | base(AbstractHallEntity 1008、Manager 3 个)、population（infected/ulcerated/ecological/apostle/defensive/skills）、ai 2 个 goal |
| `overworld/swarm` | 5 | 873 | 方块/群系扩散 + 快照存储 + 感染追踪（infcore 驱动） |
| `utils/combat` | 2 | 1,224 | `RangeAttackUtils`(840) + `RangeAttackCallback` |
| `api/magic` | 8 | 1,107 | 法力/法术强度/槽位/事件/注册表 |
| `datagen/gen` | 18 | 2,831 | 14 provider + `HallWorldGenData`/`FleshDimensionData`(309)/`HallBiomeData`(189)/`HallFeatureData`(148)/`BlockStateData`(488) |
| `mixin` 直下 | 15 | 1,415 | `CreativeModeInventoryScreenMixin`(186)、`GameRendererOculusMixin`(147)、`MixinItemRendererCosmic`(243) 等 |

### 4.3 与 corruption 的同源重复嫌疑清单（看到即列，未深挖）

corruption 工程坐标：`C:\Users\chy20\Desktop\desktop\Project\corruption\corruption`，`mod_id=corruption`、`mod_name=Rotten Ruins`、group `com.bytechen`、Forge 47.4.18、parchment mappings、**build.gradle 无 infcore 依赖**（这是 Hall 引入 infcore 改造后的分支特征）。

| 同源类别 | Hall 侧文件（举例） | corruption 侧同名文件 | 建议 |
|---|---|---|---|
| glint/描边渲染 | `client/rend/glint/` 下 15 个：`GlintEffectProfile`、`HeldItemOutlineRenderer`、`HeldItemOutlineSettings`、`HeldItemRuleManager`、`ShaderFeature*` 等 | `com/corruption/client/rend/glint/` 同名 15 个 | 合并留一份；**优先 Hall 版**（Hall 版行数更大、已含 arc/flame/fracture 扩展） |
| 渲染基础 helper | `AbstractItemRenderHelper`、`AbstractStaticEffectRenderer`、`CDRenderHelper`、`ItemRenderManager`、`ZoomRenderHelper`、`HitboxCubeRenderer`、`LightningEffectRenderer` | `com/corruption/client/rend/` 同名 7 个 | 留一份 |
| geo 渲染底座 | `GeoBaseModel/GeoItemModel/GeoProjectileModel/GeoBaseRender/GeoItemRenderer/GeoProjectileRenderer/IAutoRenderable*/IGeo*` | `com/corruption/client/entity/` 同名 13 个 | 留一份 |
| 网络基础设施 | `network/all/{UniversalPacket,UniversalPacketData,PacketActionRegistry}`、`c2s/PacketSyncKeyframe`、`s2c/EffectSyncPacket` | `com/corruption/network/` 同名 5 个 | 留一份 |
| Mixin | `GameRendererAccessor/GameRendererMixin/GameRendererOculusMixin/ItemGlintMixinPlugin/ItemInHandRendererMixin/ItemRendererMixin/LevelRendererEmbeddiumMixin/MixinAnimationController/accessor.AccessorRenderStateShard` | 同名 9 个 | 合并为单份（注意 Hall 多 `ScreenShakeMixin`/`CrimsonVowBlockMixin`/`CreativeModeInventoryScreenMixin`/cosmic/twitch 6 条） |
| datagen provider | `BlockStateData/BlockTagData/ItemGenData/LootTableData/ParticleData/RecipeProviderData/SoundData/LangDataCN/LangDataEN/BlockSpreadDataProvider/EvolutionDataProvider…` | `com/corruption/datagen/gen/` 同名 12 个 | 按“一份 provider + 两边内容”合并 |
| savedata/capability 骨架 | `savedata/base/{AbstractSaveData,ISaveData}`、`capability/base/*`、`CapabilityRegistry`、`ClientRenderRegistry` | 同名 | 留一份 |
| 群系/扩散/快照 | `swarm/{HallBiomeProcessor,HallDiskStorage,HallSpreadEventHandler,HallInfectionTracker,HallSpreadSaveData}` | `overworld/manager/{BiomeCorruptionProcessor,CorruptionDiskStorage,InfectionTrackerHandler,SpreadRuleManager,CorruptionNodeManager…}` | Hall 版已改接 infcore；**保留 Hall 版**，corruption 侧对应能力删除 |
| 实体/物品内容 | `population/{infected,ulcerated,ecological,apostle,defensive,skills}`、`items/*` | `entitis/population/{host,degenerate,linked,original,refactored,skill,king}`、`item/*` | 内容不同源 → 用户已定：KING 系列（等价王庭）不搬、AI/brain 不搬；其余按合并清单逐项决定 |
| 音效/粒子 | Hall `rip_body` 已从 corruption 复制；`blood/meat/explosion_bloody` 客户端实现同源 | — | 按用户决定：重复资源不搬，留 Hall 版 |

> **重要判断**：虽然文件名同源，Hall 侧已在这些骨架上做了大量二次开发（如 `ItemOutlinePipeline` 2265 行 vs corruption 的 glint 旧版、`AbstractHallEntity` 1008 行、infcore 接线），**不能用 corruption 版直接覆盖**；去重时以 Hall 版为基准、把 corruption 独有的增强反向移植。

### 4.4 依赖方向简图

`HallMod` → 注册器（`overworld/registry/*`）→ content 类（items/blocks/entities）
`api/*` ← 内容与客户端共用（magic/mask/anomaly 接口）
`event/*` → `utils/*` + `overworld/difficulty` + `swarm` + `api/magic` 事件
`client/*` → `assets/hall` 资源 + `api/*` + `overworld/registry` 注册对象 + `config/data`
`datagen/*` → content 注册器（生成 assets/data）
`overworld/swarm|difficulty` → **infcore**（blockspread/biome/difficulty/evolution）
`network/*` ← 全端；`mixin/*` 作用于原版渲染/创造栏
外部前置：Forge、cloth-config、geckolib、curios、infcore、JEI（可选）、BCCore（`compat/BCCoreCompat`，软兼容）

---

## 5. 对 infcore 的消费面（44 处 import，20 个文件）

infcore 版本：`org.bytechen:infcore:0.1.2`（工程内 Maven 镜像 `libs/org/bytechen/infcore/0.1.2/`，`fg.deobf` + `implementation`，mods.toml mandatory）。

| 域 | infcore 类 | import 数 | 使用文件（相对工程根） | 用途 |
|---|---|---|---|---|
| 感染类型 / 阵营 | `api.IInfectedEntity`、`api.IKillCounter` | 8 | `event/ForgeEventHelpers`、`event/impl/BlackHolePhysicsHandler`、`overworld/difficulty/HallDifficultyAttributes`、`overworld/registry/entities/base/AbstractHallEntity`、`population/apostle/CollapsarEntity`、`population/defensive/WindingColumnEntity`、`population/skills/MeteoriteEntity` | 感染实体标记、友伤判定、难度缩放、击杀计数 |
| 目标选择（索敌） | `api.goal.InfectedTargetGoal`、`api.goal.ITargetFilter` | 10 | `AbstractHallEntity`、`CollapsarEntity`、`BonecrusherEntity`、`HeavyBombEntity`、`InfEndermanEntity`、`InfPlayerEntity`、`InfSkeletonEntity`、`MonolithEntity`、`PursuerEntity`、`ScoutEntity` | 统一“敌对目标=非感染者”索敌；**合并后 corruption 侧索敌改走这里（用户已定）** |
| 演化 | `core.evolution.{EvolutionEntry,EvolutionManager}`、`api.event.EntityEvolveEvent` | 4 | `datagen/gen/EvolutionDataProvider`、`AbstractHallEntity`、`population/ulcerated/{MonolithEntity,UlceratedConversionRules}`、`population/ulcerated/ScoutEntity` | 击杀演化（monolith→bonecrusher/heavy_bomb 等） |
| 方块扩散 | `api.block.ISpreadBlock`、`core.blockspread.{BlockSpreadManager,SpreadHelper,BlockSpreadEntry}`、`api.event.BlockSpreadEvent` | 12 | `command/SpreadCommand`、`datagen/gen/BlockSpreadDataProvider`、`overworld/manager/BlockSpreadManager`、`blocks/base/AbstractSpreadBlock`、`blocks/impl/{HallVineBlock,SpreadPillarBlock}`、`feature/HallVineFeature`、`swarm/HallSpreadEventHandler` | 王庭方块扩散 + Post 事件驱动群系扩散/连锁树叶 |
| 难度 | `api.difficulty.DifficultyType`、`core.difficulty.{DifficultyHelper,DifficultyRegistry}`、`api.event.DifficultyChangeEvent` | 5 | `overworld/difficulty/{DifficultyScaleProfile,DifficultySelectHandler,HallDifficulty,HallDifficultyAttributes,HallDifficultyEventHandler}` | 4 档难度注册/查询/同步/世界数据 |
| Capability | `core.capability.{CapabilityRegistry,KillCountCapability}` | 2 | `AbstractHallEntity` | 击杀计数 capability 复用 |
| 群系 | `core.biome.BiomeSpreadHelper` | 1 | `swarm/HallBiomeProcessor` | 群系扩散写入 |
| 数据生成基类 | `core.datagen.{EvolutionDataProvider,BlockSpreadDataProvider}`（**继承**使用） | 0 import（extends 全限定名） | `datagen/gen/EvolutionDataProvider`、`datagen/gen/BlockSpreadDataProvider` | 生成 `data/hall/infcore_{evolution,blockspread}/*.json` |
| 合计 | — | **44** | 20 个文件 | — |

**infcore 关键字符串（迁移敏感）**：

| 字符串 | 生成/使用处 | 迁移行为 |
|---|---|---|
| `hall:inf`、`hall:evo` | `datagen/gen/EvolutionDataProvider`（`super(packOutput, HallMod.MODID, "default")` + `entry("inf"/"evo", ...)`）→ `data/hall/infcore_evolution/default.json`；`HallMod.INFECTION_TYPE = new ResourceLocation(MODID,"inf")` | 随 `MODID` 自动变 `rotten_ruins:inf` / `rotten_ruins:evo`；**数据文件随之重生成** |
| `hall:spread` | `BlockSpreadDataProvider`（`super(..., HallMod.MODID, "default")` + `SPREAD_KEY_DEFAULT="spread"`）→ `data/hall/infcore_blockspread/default.json`；`HallSpreadEventHandler.HALL_SPREAD_TYPE = new ResourceLocation(MODID,"spread")` | 自动变 `rotten_ruins:spread` |
| 兜底感染类型 | `AbstractHallEntity` L253 `new ResourceLocation(MODID, "hall")` → `hall:hall` | namespace 自动变 `rotten_ruins:hall`；path 仍 `hall`，**依赖 path 的 4 处比较可保留**（见 6.2） |

**对合并的含义**：infcore 是唯一同时被 Hall（深度消费）与目标形态（“索敌改用 infcore 阵营/感染类型判断”）使用的底座；corruption 当前自建 `CorruptionTypeUtil`/`SpreadRuleManager`/brain/AI，合并时必须把 corruption 的感染/扩散/难度/索敌改接 infcore —— 这部分工作量在 corruption 侧，不属于本报告的 Hall 盘点范围，但接口面已由 Hall 打通（上表）。

---

## 6. modid 迁移面量化（hall → rotten_ruins）

### 6.1 全仓库硬编码点位分类统计

| # | 类别 | 位置/数量 | 举位 | 处置 |
|---|---|---|---|---|
| 1 | modid 常量 | `HallMod.MODID = "hall"` 1 处；`MODID` 引用 ≈247 处（自动跟随） | `HallMod.java` L28 | **必须改 1 处** |
| 2 | 构建变量 | `gradle.properties` `mod_id=hall`；`build.gradle` 全部用 `${mod_id}` 占位（mixin config/refmap、runs、gameTestNamespaces、archivesName）自动跟随；`settings.gradle` rootProject.name 不必改 | `gradle.properties` L10 | **必须改 1 行**；产物名自动变 |
| 3 | mods.toml | `modId`/`displayName` 占位自动；`[[mixins]] config="${mod_id}.mixins.json"` 自动但**文件名要同步改** | `src/main/resources/META-INF/mods.toml` | 配置自动、文件必改 |
| 4 | Mixin 文件/refmap/包 | `src/main/resources/hall.mixins.json` 文件名、json 内 `package`/`refmap`/`plugin` 3 字段、build 产物 `hall.refmap.json` | `hall.mixins.json` L4-L7 | **必须改**（漏改→启动崩溃） |
| 5 | Java 包名 | `package org.bytechen.hall` 390 行 + `import org.bytechen.hall` 869 行（若包名改为 `org.bytechen.rottenruins`）；mixin json 的 `package` 同步 | 全部 390 个 `.java` | **决策项**：改（机械但量大，IDE 一键）／不改（包名不必等于 modid，可 0 改动） |
| 6 | 硬编码 `"hall"` 字面量（源码） | 19 行：17 功能 + 2 Javadoc（详见 6.2） | 见 6.2 表 | 其中 **必须改 12**（7 构造 ID + 3 命名空间比较 + 2 BlockSetType/WoodType，另 command 名 1 个待定） |
| 7 | 硬编码 `"hall:..."` 命名空间字面量（源码） | 45 行：功能 16（配方 id 13 + shader id 1 + action 1 + NBT key 1）+ 日志 1 + Javadoc/注释 ≈28 | `datagen/gen/RecipeProviderData`、`client/rend/glint/OutlineMaskRenderType`、`network/c2s/VoidSwordStrikePacket`、`blocks/impl/FleshRiftBlock` | **必须改 16 功能行**；注释建议一起清 |
| 8 | lang 键硬编码 | `TranslateUtils` 26 个常量 + `LangDataCN` 3 处字面量 key + `RegisterAttributes` 5 个 `attribute.name.hall.*` = **34 处** | `utils/TranslateUtils.java`、`datagen/gen/lang/LangDataCN.java` L110-L114、L163-L167、`overworld/registry/RegisterAttributes.java` | **必须改 34**（漏改→tooltip/属性名显示原始 key） |
| 9 | 模型 loader id | JSON 内 `"loader": "hall:cosmic"` / `"mask_layers"`；注册侧用裸名 `event.register("cosmic"/"mask_layers", ...)`（`CosmicClient`、`MaskLayerClient`） | 3 个物品模型 JSON + 2 个注册点 | JSON 前缀必改；注册侧裸名按 Forge 约定说明，迁移后进游戏验证 3 个模型 |
| 10 | 资源目录命名空间 | `src/main/resources/assets/hall`（约 321 文件）、顶层 `hall.mixins.json`；`src/generated/resources/{assets/hall,data/hall}`（361 json + cache） | — | 目录改名 + datagen 全量重跑 |
| 11 | 手写资源 JSON 内部引用 | `src/main/resources` 下 **55 个手写 JSON / 99 处 `hall`**，其中 **49 个文件含命名空间引用**（`"hall:`）：shaders/core 35 文件×2（`"vertex"/"fragment": "hall:..."`）=70、models/item 13 文件 20 处、forge bosses tag 1、`hall.mixins.json` 3、geo/anim 的 identifier 5；另有 `pack.mcmeta` 1 处、`accesstransformer.cfg` 注释 1 处 | 见 3.1 | **必须手工改**（不走 datagen） |
| 12 | generated 数据 | 359/361 json 内嵌 `hall:` 引用（方块状态/模型/配方/战利品/进度/群系/维度/infcore 数据/minecraft tags…） | `src/generated/resources/**` | **清空后 runData 重生成**，不要手改 |
| 13 | 维度/群系/存档 ID | `hall:heall`（维度+类型+LevelStem）、`hall:flesh_marrow`、`hall:hall_wasteland`、`hall:inf`、`hall:evo`、`hall:spread`、`hall:easy|normal|hard|incomprehensible` | 见第 2 节 | 随 modid 自动变；**旧存档维度目录不兼容**（风险 P0） |
| 14 | 持久化/NBT/数据键 | 15 处：`HallHealth`、`HallFinisherUsed`、`HallMagicSelected`、`HallMagicCooldowns`、`HallMagicMana`、`HallMagicRegenBuffer`、`HallVerdict`、`HallVerdictDash`、`HallDifficultyTier`、`hall_data`（世界目录）、`hall_spread_data`、`hall_difficulty_selected`、`hall_difficulty_health/damage`（属性修饰器 id）、`hall:return_anchor`（NBT key） | 见 6.4 | **可保留**（不参与命名空间、保留旧存档兼容）；若要“纯净”再统一 |
| 15 | 配置路径 | `@Config(name = MODID + "/SplendidingConfig")` / `"/MeteorConfig"` → 自动变 `config/rotten_ruins/*`；旧 `config/hall/*` 不会被读取 | `config/data/*.java` | 自动路径变化 + **建议加旧文件迁移**（否则玩家配置重置） |
| 16 | 命令名 | `/hall`（`ThreatCommand` 根命令 literal），其余 6 个不含 modid | `command/ThreatCommand.java` L41 | 决策项（建议改 `/rottenruins` 或保留 `/hall` 作为风味命令） |
| 17 | Java 标识符/文件名 | 42 个 `HallXxx` 唯一标识符、31 个 `Hall*.java`、5 个 `SplendidingXxx`、59 处 `splendiding$` mixin 前缀、日志前缀 `[hall]`/`[hall-difficulty]`/`[Hall-IO]` | 见 3.3 | **可全部保留**（不参与资源命名空间，仅风格） |
| 18 | 构建/安装脚本 | `buildAndInstall.ps1`：`$ModJarName = "hall-0.3.0-all.jar"`（版本已过期）、校验路径 `org/bytechen/hall/...` | `buildAndInstall.ps1` L15/L32-L33 | **必须改**（否则安装脚本找不到产物） |
| 19 | 文档 | `docs/*.md` 7 篇 51 处、`CHANGELOG*.md`、`README.md`（# 腐朽之疫：王庭【Rotten Ruins：the Hall】） | `docs/`、根目录 | 可选/发布前清理 |

### 6.2 硬编码 `"hall"` 19 行逐条（源码）

| 文件:行 | 代码片段 | 性质 | 处置 |
|---|---|---|---|
| `HallMod.java:28` | `public static final String MODID = "hall";` | modid 常量 | **改** |
| `overworld/registry/HallDimensions.java:34` | `ResourceLocation.fromNamespaceAndPath("hall", "heall")` | 维度 ID | **改**（或直接用 `MODID`） |
| `datagen/gen/FleshDimensionData.java:63` | `...andPath("hall", "flesh_marrow")` | noise_settings ID | **改** |
| `datagen/gen/FleshDimensionData.java:286` | `...andPath("hall", "heall")`（dimension_type effects） | 维度特效 ID | **改** |
| `datagen/gen/HallBiomeData.java:31` | `new ResourceLocation("hall", "hall_wasteland")` | 群系 ID | **改** |
| `datagen/gen/HallBiomeData.java:35` | `new ResourceLocation("hall", "flesh_marrow")` | 群系 ID | **改** |
| `overworld/registry/blocks/impl/HallVineBlock.java:126` | `return "hall".equals(spreadBlock.getSpreadType().getNamespace());` | **命名空间比较**（扩散类型） | **改**（用 `HallMod.MODID`）——漏改则藤蔓不再生长 |
| `overworld/registry/feature/HallVineFeature.java:141` | `return "hall".equals(spreadBlock.getSpreadType().getNamespace());` | 同上 | **改** |
| `overworld/swarm/HallSpreadEventHandler.java:218` | `!"hall".equals(spreadBlock.getSpreadType().getNamespace())` | 同上 | **改** |
| `overworld/registry/HallBlockSetTypes.java:16` | `new BlockSetType("hall", ...)` | 方块行为类型名（非资源命名空间） | 建议改（统一风格）；不改不会崩 |
| `overworld/registry/HallBlockSetTypes.java:50` | `new WoodType("hall", ...)` | 同上 | 建议改 |
| `event/ForgeEventHelpers.java:171` | `&& "hall".equals(type.getPath())` | **path token 比较**（感染类型 `*:hall`） | 可保留（namespace 已由 `HallMod.MODID.equals(type.getNamespace())` 校验；path 仍为 `hall`） |
| `event/impl/BlackHolePhysicsHandler.java:309` | `&& "hall".equals(type.getPath())` | 同上 | 可保留 |
| `overworld/registry/entities/population/apostle/CollapsarEntity.java:1236` | `&& "hall".equals(type.getPath())` | 同上 | 可保留 |
| `overworld/registry/entities/population/skills/MeteoriteEntity.java:267` | `infectedEntity.getInfectionType().getPath().equals("hall")` | 同上 | 可保留 |
| `overworld/registry/entities/base/AbstractHallEntity.java:253` | `return new ResourceLocation(HallMod.MODID,"hall");` | 兜底感染类型 `*:hall`（namespace 自动） | 自动跟随；path 保留 |
| `command/ThreatCommand.java:41` | `dispatcher.register(literal("hall")` | 命令根名 | 决策（建议改） |
| `api/mask/MaskLayerProvider.java:17` | Javadoc `fromNamespaceAndPath("hall", "item/my_sword_mask")` | 注释 | 可选 |
| `overworld/registry/blocks/impl/HallVineBlock.java:30` | Javadoc `命名空间为 "hall"` | 注释 | 可选 |

> **P0 提醒**：3 处 `getNamespace()` 比较是唯三“漏改后不报错但功能静默消失”的点；其余 4 处 `getPath()` 比较是对 path token 的判断，namespace 变了仍然成立（因为兜底类型仍是 `rotten_ruins:hall`）。

### 6.3 “必须改名 vs 可保留”汇总

| 处置 | 内容 | 数量级 |
|---|---|---|
| **必须改（漏改会崩/功能失效）** | `gradle.properties mod_id`、`HallMod.MODID`、mixin json 文件名+3 字段、`mods.toml` mixins 文件名、`pack.mcmeta` 描述、源码硬编码 ID 字面量 12（6.2 标“改”）+ `"hall:*"` 功能 16、lang 键 34、手写资源 JSON 49 文件/99 处、资源目录 `assets/hall`、`data/forge` boss tag、`buildAndInstall.ps1`、generated 全量重生成 | **约 120 个文件 + 约 170 处字面量**（不含 generated） |
| **建议改（不改不崩）** | Java 包名（390+869 行，若要求 `org.bytechen.rottenruins`）、`BlockSetType/WoodType` 名、命令根名、旧配置迁移、注释/Javadoc、文档 CHANGELOG | 约 1,300 行机械改动（包名） |
| **可保留（不参与资源命名空间）** | 42 个 `HallXxx` 标识符 / 31 个 `Hall*.java` 文件名、5 个 `SplendidingXxx`、59 处 `splendiding$` 前缀、15 处持久化/NBT 键、日志前缀、`settings.gradle` 项目名、`SplendidingMaterials/SpawnPlacements` 空模板类 | 约 100 个标识符 |

### 6.4 持久化/NBT/数据键（建议保留）

| 键 | 位置 | 说明 |
|---|---|---|
| `HallHealth` / `HallFinisherUsed` | `CollapsarEntity` | 实体 NBT（有旧存档兜底读取逻辑） |
| `HallMagicSelected` / `HallMagicCooldowns` | `PlayerMagicPool` | 玩家 capability NBT |
| `HallMagicMana` / `HallMagicRegenBuffer` | `TwistedPoint` | 玩家 capability NBT |
| `HallVerdict` / `HallVerdictDash` | `VerdictCooldown` / `VerdictDash` | 玩家 persistent data 根键 |
| `HallDifficultyTier` | `HallDifficultyAttributes` | 实体 persistent data |
| `hall_data` | `HallBiomeProcessor` | `<world>/hall_data` 快照目录 |
| `hall_spread_data` / `hall_difficulty_selected` | SavedData | 存档数据文件名 |
| `hall_difficulty_health` / `hall_difficulty_damage` | 属性 modifier id | 运行时注册，不落盘 |
| `hall:return_anchor` | `FleshRiftBlock` | 传送返回点（NBT key，唯一带 namespace 风格前缀的持久化键） |

> 这些键与 modid 无关；**如果合并不追求“旧存档继续可读”，也可以统一改名**；如果未来还要兼容旧世界，建议保留。

### 6.5 迁移执行顺序建议（checklist）

1. **先备份工作区**（167 项未提交改动！先 `git stash`/新建分支提交，再动迁移）。
2. 改 `gradle.properties`（`mod_id=rotten_ruins`、`mod_name=Rotten Ruins`、`mod_description`）、`HallMod.MODID`。
3. 改包名（若决定）→ IDE 全量 refactor `org.bytechen.hall` → `org.bytechen.rottenruins`；同步 `hall.mixins.json` 的 `package`/`plugin`。
4. 重命名 `hall.mixins.json` → `<newid>.mixins.json`、`hall.refmap.json` 由构建自动生成；确认 `mods.toml`/`build.gradle` 的 `${mod_id}` 已指向新名。
5. 改 12 处硬编码 ID + 16 处 `"hall:*"` 功能字面量 + 34 处 lang 键 + 3 处 getNamespace 比较（6.2/6.1 清单）。
6. 改手写资源 JSON 49 文件：shader json 的 `"vertex"/"fragment"` → `<newid>:...`；3 个物品模型 loader/mask → `<newid>:...`；forge boss tag。
7. `git mv` 目录：`src/main/resources/assets/hall` → `assets/rotten_ruins`；`src/main/resources/{hall.mixins.json}`；`src/main/resources/hall.png`（改名并决定是否挂 logoFile）；`src/generated/resources/{assets/hall,data/hall,data/minecraft}` 全部**删除**（保留 `.gitignore` 约定）。
8. 清空 `src/generated/resources/.cache` 后 `runData` 全量重生成（产物应全部落到 `assets/rotten_ruins`、`data/rotten_ruins`）。
9. 删除历史空目录 `data/splendiding`、`data/godsick_neo`（顺手清技术债）。
10. 处理 config 迁移：`config/hall/*` → `config/rotten_ruins/*`（可选复制逻辑，避免玩家配置重置）。
11. 处理维度存档策略：旧世界 `world/dimensions/hall/heall` → 新 id 不会被加载；决定“放弃旧档 / 写迁移 / 提供 `/execute in` 提示”。
12. 更新 `buildAndInstall.ps1`、README、CHANGELOG、docs 中的命名；跑一次 `gradlew build` + 进游戏验证（重点：mixin 加载、3 个 cosmic 模型、35 个 shader、维度传送、藤蔓扩散、属性 tooltip）。

---

## 7. 合并结论表（Hall 侧：必须保留 / 可被替代 / 可去重）

### 7.1 必须保留（Hall 作为合并主基线）

| 类别 | 内容 | 理由 |
|---|---|---|
| 注册表内容 | 75 items / 33 blocks / 23 entities / 4 effects / 5 particles / 2 sounds / 5 features / 5 attributes | 合并主基线，corruption 侧无等价内容（王庭系列 vs KING 系列按用户决定不搬 KING） |
| 世界生成 | `hall:heall` 维度（血肉庭园）+ `flesh_marrow` 群系 + `hall_wasteland` 扩散群系 + 5 configured/placed features + 2 noise + 1 noise_settings | Hall 独有完整维度 |
| 扩散体系 | `swarm/*`（方块扩散→群系扩散→快照→净化）+ infcore blockspread/biome 接线 | 合并后统一扩散底座；corruption 的 SpreadRuleManager 由它替代 |
| 难度体系 | 4 档难度 + 属性倍率 + 难度选择 GUI + 存档/同步 | infcore DifficultyRegistry 已打通 |
| 魔法体系 | `api/magic` + `PlayerMagicPool`/`TwistedPoint` capability + 5 attributes + 6 法力板/2 法杖/催化剂/符文 | Hall 独有 |
| 武器/技能 | 绯红誓约、天穹裁决、寂寒白日、虚空剑、陨石事件、使徒/黑洞/坍缩/冲击波/剑气/缠绕之柱 | Hall 独有（大量未提交新内容） |
| 客户端渲染栈 | cosmic 星空 loader、mask 泛光层、fx 特效框架、glint 描边管线、背板、tooltip shader、粒子、GUI shader、screen shake | Hall 最新实现，corruption 版为旧 |
| creative tab 分区系统 | `SectionedCreativeModeTab` + `CreativeModeInventoryScreenMixin` + `gui_tab_divider` shader | Hall 独有创新，合并后作为唯一 tab |
| config | `SplendidingConfig`(397 行) + `MeteorShowerConfig` + 12 级迁移逻辑 | Hall 独有 |
| datagen | 14 个 provider + infcore 两个数据 provider | 合并后所有内容统一由它生成 |
| 网络 | `hall:main` 单通道 + UniversalPacket action 路由 | 合并后 corruption 包并入同一通道 |
| api | `api/{anomaly,magic,mask}`、`ICustomOutline/ITooltipStyle/...` | Hall 独有公共接口 |

### 7.2 可被 corruption 内容替代 / 需从 corruption 吸收

| 项 | 说明 |
|---|---|
| corruption 的 KING 系列 | 与 Hall 王庭系列等价，**不搬**（用户已定） |
| corruption 的寻路 AI / brain | **不搬**（用户已定）；Hall 用 infcore `InfectedTargetGoal` 重做索敌 |
| corruption 的宿主/退化/联动/原创/重构者等生物线 | 若合并要保留该内容，需**重挂到 Hall 的 `AbstractHallEntity` + infcore 感染框架**，不能直接拷贝（其基类/网络/伤害体系不同） |
| corruption 的 `CorruptionTypeUtil` / 感染类型体系 | 由 infcore 感染类型替换（Hall 已建立 `IInfectedEntity` 接线范例） |
| corruption 的自建扩散/群系/快照 | 由 Hall `swarm` + infcore `BlockSpreadManager`/`BiomeSpreadHelper` 替代 |
| corruption 的 JEI 兼容（`compat/jei`） | Hall 侧无对应，可吸收 |

### 7.3 可去重（同源实现只留一份，优先 Hall 版）

| 重复面 | 数量 | 建议 |
|---|---|---|
| glint/描边渲染（`client/rend/glint`） | corruption 15 同名类 vs Hall 19 类 | 留 Hall 版（已扩展 arc/flame/fracture） |
| 渲染 helper（CDRenderHelper/ItemRenderManager/ZoomRenderHelper/HitboxCubeRenderer/LightningEffectRenderer/AbstractItemRenderHelper/AbstractStaticEffectRenderer） | 7 | 留一份 |
| geo 渲染底座（GeoBase*、IAutoRenderable*、IGeo*） | 约 13 | 留一份 |
| 网络基础设施（UniversalPacket/UniversalPacketData/PacketActionRegistry/PacketSyncKeyframe/EffectSyncPacket） | 5 | 留一份 |
| Mixin（game renderer/item renderer/glint plugin/level renderer/embeddium/oculus/accessor） | 9 同名 | 合并成一份，注意不需要两份 `ItemGlintMixinPlugin` |
| datagen provider 骨架 | 12 | 一份 provider 同时生成两边内容 |
| savedata/capability 骨架 | 4-5 | 一份 |
| 粒子实现/贴图（blood/meat/explosion_bloody）与 `rip_body` 音效 | — | 留 Hall 版（用户：重复资源不搬） |

### 7.4 需要合并时拍板的决策项

| # | 决策 | 选项 | 本报告建议 |
|---|---|---|---|
| 1 | 注册 path 是否保留 Hall 前缀（`hall_stone` 等） | 保留 path 只换 namespace ／ 全改 `rotten_ruins` 前缀 | **保留 path**（世界生成/资源文件名/geckolib identifier 全部不动，只换 namespace，成本最低） |
| 2 | 维度 id | `rotten_ruins:heall`（path 不变） | 推荐 path 也改成 `heall` 保持不违和；不建议保留 `hall` path |
| 3 | 包名 | `org.bytechen.hall` 保持不变 ／ 改 `org.bytechen.rottenruins` | 用户倾向后者 → 机械重构可承受，但建议与 modid 迁移分两步做（先 modid 跑通再动包名） |
| 4 | 旧存档 | 放弃 ／ 注册名重映射 ／ 维度目录搬运 | 建议明确“1.0 起为新 mod，不兼容旧档”，除非常规用户群有要求 |
| 5 | config 迁移 | 复制旧文件 ／ 重新生成默认 | 建议迁移（复制 + 版本号重置），避免渲染设置全丢 |
| 6 | creative tab | 单 tab 多分区（Hall 现状） ／ 两个 tab | 建议单 tab，新增 corruption 内容分区 |
| 7 | Java 标识符债（`Hall*`/`Splendiding*`/`splendiding$`） | 现在清 ／ 延后 | 延后（不影响功能，避免与合并改动混在一起） |

---

## 8. 风险点（按优先级）

| 级别 | 风险 | 影响 | 触发点 | 缓解 |
|---|---|---|---|---|
| **P0** | 注册名全部改 namespace，旧存档方块/物品/实体全部丢失 | 旧世界打开后变成空气/丢失实体 | `DeferredRegister.create(..., MODID)` 全部随 modid 变 | 明确版本边界；如必须兼容，做注册名 remap 或缺失映射 mod |
| **P0** | 维度 ID 改变 + 存档维度目录不认 | 旧世界血肉庭园无法进入、玩家卡在残留维度 | `hall:heall` → `rotten_ruins:heall`；`world/dimensions/hall/heall` 不再加载 | 迁移 world/dimensions 目录或放弃；提供返回主世界手段 |
| **P0** | 3 处 `getNamespace()=="hall"` 比较漏改 | 王庭藤蔓/扩散植物**静默停止生长**（无报错） | `HallVineBlock:126`、`HallVineFeature:141`、`HallSpreadEventHandler:218` | 改成 `HallMod.MODID.equals(...)`；迁移后专项测试扩散 |
| **P0** | mixin 配置/refmap 文件名不同步 | 游戏启动崩溃（mixin config not found / refmap 不匹配） | `hall.mixins.json`、`mods.toml [[mixins]]`、`build.gradle` mixin 段、`copyRefmap` 任务 | 一次性 4 处同步 + 启动验证 |
| **P1** | 手写资源 JSON 未改 namespace | 35 个 shader 编译失败（黑屏/异常）或 3 个 cosmic 物品模型加载失败；forge boss tag 失效 | `shaders/core/*.json` 的 `"vertex"/"fragment"`、`models/item/{void_sword,crimson_vow,silent_daylight}.json` loader/mask | 按 6.1 清单批量替换 + 进游戏逐个验证 |
| **P1** | lang 键/attribute 键硬编码漏改 | tooltip、难度名、属性名显示 raw key；datagen 与代码键不一致 | `TranslateUtils`(26)、`LangDataCN`(3 处字面量)、`RegisterAttributes`(5) | 统一改为 `MODID` 拼接，跑 runData 后 diff lang |
| **P1** | 配置路径变化导致玩家设置重置 | 渲染设置/火焰/电弧参数全部回默认 | `@Config(name=MODID+"/...")` | 加旧路径复制迁移（可参照 `ConfigHelper.migrateSplendidingConfig` 的写法） |
| **P1** | 同源代码去重时“用 corruption 版覆盖 Hall 版” | 丢失 Hall 的二次开发（ItemOutlinePipeline 2265 行、AbstractHallEntity 1008 行、infcore 接线、新粒子/描边） | glint/rend/geo/datagen 等同名文件 | 以 Hall 为准，corruption 仅吸收独有增强；逐文件 diff |
| **P1** | generated 目录不清理 / datagen 不重跑 | 旧 `hall:` 资源残留被加载、与 `rotten_ruins:` 新资源重复（方块状态/模型双份），排查困难 | `src/generated/resources`、`.cache` | 迁移步骤固定为“删 generated → 删 cache → runData → git diff 检查” |
| **P1** | corruption 侧内容接入 infcore 的语义差 | 合并后索敌/感染/扩散行为不一致（友伤、阵营判断） | corruption 的 `CorruptionTypeUtil`/AI 与 Hall `IInfectedEntity` | 在 corruption 侧统一替换为 infcore 判断；用 Hall 现有 7 个 IInfectedEntity 用法做模板 |
| **P2** | 未使用/垃圾资源（3 个中文名音效、2 张垃圾 GUI 图、hall_bedrock 贴图、空目录） | 包体脏、潜在编码问题 | 3.5 表 | 发布前清理 |
| **P2** | 8 个实体缺 lang、verdict effect 缺贴图、`hall_wasteland` 缺 lang、`example_item` 作为 tab 图标 | 显示原始键/缺图/占位物品外露 | 2.4/2.12/3.5 | 补齐或替换 |
| **P2** | 死代码 | `mixin/accessor/InvokerHandTransforms` 未在 mixin 配置引用；`EffectSpriteData` 空实现；`SplendidingMaterials`/`SplendidingSpawnPlacements` 空模板；`BIOME_MODIFIER_SERIALIZERS` 空注册器 | — | 迁移时顺手清理（低风险） |
| **P2** | 构建/安装脚本残留 | `buildAndInstall.ps1` 指向 `hall-0.3.0-all.jar` 与 `org/bytechen/hall/*.class`，迁移后直接报“找不到产物” | `buildAndInstall.ps1` | 同步改 |
| **P2** | 双 modid 共存期 | 若分阶段迁移、两个 jar 同时装，注册名/资源不会冲突（namespace 不同），但会重复加载 mixin/网络通道与相同内容 | 过渡期 | 建议一次性切换，不发布过渡 jar |
| **P2** | 文档/注释残留 | `docs/` 7 篇 51 处、Javadoc 里的 `hall:`/`splendiding:` 示例误导后续维护 | — | 发布前统一替换（可选） |

---

## 附录 A：盘点方法与证据位置

- 只读操作：`git status/log`、目录枚举、`[IO.File]::ReadAllLines` 行数统计、`Select-String` 字面量统计、`read` 工具读取源码/JSON（UTF-8，含中文）。
- 未执行：任何 Gradle 任务（build/runClient/runData）、未修改工作区、未做网络请求。
- 关键证据文件：`gradle.properties`、`build.gradle`、`settings.gradle`、`src/main/resources/META-INF/mods.toml`、`src/main/resources/hall.mixins.json`、`src/main/resources/pack.mcmeta`、`src/main/resources/META-INF/accesstransformer.cfg`、`src/main/java/org/bytechen/hall/HallMod.java`、`overworld/registry/*`、`datagen/gen/*`、`src/generated/resources/**`、`run/config/**`（仅作为“上一次改名遗留”的证据，未改动）、corruption 工程 `Project/corruption/corruption`（只读对照）。
- 计数口径提醒：`Measure-Object -Line` 会漏计空行，本报告所有行数均用 `ReadAllLines().Count`；`"hall"` 字面量统计为“命中行数”，同一行多次出现按 1 次计。

## 附录 B：关键路径索引（相对工程根）

| 用途 | 路径 |
|---|---|
| 主类 | `src/main/java/org/bytechen/hall/HallMod.java` |
| 注册表 | `src/main/java/org/bytechen/hall/overworld/registry/{RegisterItem,RegisterBlock,RegisterBlockEntities,EntityTypeRegistry,RegisterEffect,RegisterParticles,SoundEventRegistry,RegisterFeature,RegisterAttributes,RegisterTab,HallDimensions}.java` |
| infcore 接线 | `overworld/{difficulty,swarm,manager,registry/blocks,registry/entities}`；`datagen/gen/{EvolutionDataProvider,BlockSpreadDataProvider}.java` |
| 客户端渲染 | `client/{cosmic,mask,fx,rend,entity,particle,gui,tooltip}` |
| Mixin | `src/main/resources/hall.mixins.json` + `src/main/java/org/bytechen/hall/mixin/` |
| 数据生成 | `src/main/java/org/bytechen/hall/datagen/DataGenEvent.java` + `datagen/gen/*` |
| 生成产物 | `src/generated/resources/{assets/hall,data/hall,data/minecraft}` |
| 手写资源 | `src/main/resources/assets/hall/**`、`src/main/resources/data/forge/tags/entity_types/bosses.json` |
| 配置 | `src/main/java/org/bytechen/hall/config/**`、`run/config/hall/*.json5` |
| 合并文档目录 | `docs/merge/`（本报告所在） |
