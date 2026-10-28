# 双工程重复资源与冲突矩阵

**课题**：`RottenRuins:The Hall`（modid `hall`）+ `Rotten Ruins`（modid `corruption`）合并为单模组 `rotten_ruins` 时的**重复资源 / 重复代码 / 撞名冲突**矩阵
**性质**：只读分析，未修改任何源码、未执行 Gradle、未做 git 操作
**分析日期**：2026-10-28
**关联文档**：`docs/merge/analysis-hall.md`（Hall 侧盘点）、`analysis-corruption.md`（corruption 侧盘点）、`analysis-infcore.md`（infcore 消费面）

**路径缩写**（下文除特别说明外均相对各工程 `src/main/resources` 或 `src/main/java`）：

| 缩写 | 实际路径 |
|---|---|
| **H** | `C:\Users\chy20\Desktop\desktop\Project\RottenRuinsSplendiding`（Hall 工程，基线） |
| **C** | `C:\Users\chy20\Desktop\desktop\Project\corruption\corruption`（corruption 工程） |
| **H-rs / C-rs** | `src/main/resources` + `src/generated/resources`（两者合并统计，`.cache` 除外） |
| **H-java / C-java** | `src/main/java`（H: `org/bytechen/hall`，C: `com/corruption`） |

**结论标记**（第 8 节汇总用）：

- **A 不用搬**：两边字节级/归一化后完全等价，只留一份
- **B 搬 Hall 版**：corruption 侧是同源旧版/被重构版，合并时保留 Hall
- **C 搬 corruption 版**：Hall 没有该内容/能力，需从 corruption 迁入
- **D 必须人工合并**：两边都有、但语义/实现分叉，必须逐个拍板

---

## 0. 结论摘要（先看这 8 条）

1. **资源层重复远小于"同源项目"的直觉**：byte-identical 仅 **20 个文件 / 54,049 B**（占 Hall 资源 1.69%、corruption 资源 3.12%）。把 CRLF-only（6 个）与"只差命名空间字符串"（6 个）也算上，等效重复 **32 个文件 / 71,591 B**（Hall 2.24% / corruption 4.14%）；再加 3 个"跨路径同内容"文件后为 **35 个 / 72,983 B**。原因是两个内容分支已彻底分叉：model/blockstate/geo/animation/recipe/loot/advancement 的路径级交集为 **0**。
2. **真正高度重复的是 Java 骨架**：**79 个相对路径完全相同**的类对（占 Hall java 20.3% / corruption 29.5%），另外还有 **15 对"改名/挪位同职责"类**；79 对中 **24 对归一化后字节等价**（只差包名与 modid 字符串），只有 **34 对**是大幅分叉（token 相似度 <0.5）。
3. **合并后"同名目标路径"资源碰撞共 42 对**（Hall 侧 90,344 B / corruption 侧 100,845 B，其中含 20 对完全重复 + 22 对近似），真正需要拍板的只有 **10 对**：8 个文本（lang×2、sounds.json、particles×2、tag、mods.toml、pack.mcmeta）+ 2 个贴图（`meat.png`、`img.png`）。**另有 6 对仅 CRLF/LF 差异、6 对仅命名空间差异，可直接去重/改名**。
4. **注册 ID 的"硬碰撞"只有 4 类**，比预想小得多：
   - `particle_type`：`blood`、`meat`、`explosion_bloody` 各 1（共 3 个 ID）
   - `sound_event`：`rip_body` 1 个（且 `rip_body.ogg` 字节完全相同）
   - creative tab 的**翻译键**：`itemGroup.<ns>.main`（注册 id 本身不同：`*_tab` vs `*_table`）
   - 命令根：`hall` → `rotten_ruins` 与 `corruption` → `rotten_ruins` 撞成一个
   - 其余 `item`(56/57)、`block`(34/19)、`entity`(15/21)、`effect`(4/4+2 未入 lang)、`advancement`/`recipe`/`loot` 的**后缀交集全部为 0**。
5. **最容易被低估的坑是 datagen 输出撞名**：迁移后两个 `DataGenEvent` 都还在，会写同一批文件——`assets/rotten_ruins/lang/en_us.json`、`zh_cn.json`、`sounds.json`、`particles/{blood,meat,explosion_bloody}.json`、`data/minecraft/tags/blocks/mineable/pickaxe.json`。GatherData 是"后写覆盖"，**不报错但内容静默丢失**；必须合并成单 provider（见 §7.7）。
6. **语义近似（非硬碰撞）需产品拍板**：`inf_*`（Hall 3 种感染体）vs `infection_*`（corruption 11 种）；`KING 王庭` 系列 vs Hall 王庭系列（有字节证据：`hall_pillar.png` ≡ `king_brick.png`）；以及**两套完整的世界腐化系统**（Hall `swarm` + infcore vs corruption `BiomeManager` mixin + `CorruptionNodeManager`）。
7. **Mixin 层 Hall 是 corruption 的超集**：8 个同名 mixin 中，注入点集合 6 个完全相同、2 个 Hall 更多（`ItemRendererMixin` +1 注入、`AccessorRenderStateShard` +4 accessor）；corruption 独有 6 个 mixin 里**只有 3 个需要评估迁入**（`MixinBiomeManager` 视腐化系统决策、`MixinLivingEntity`、`accessor.AccessorLivingEntity`），另 2 个是**全工程 0 引用的死 mixin**（`AccessorDisplay`、`ItemDisplayAccessor`）；此外 2 个"未注册死类"（H `InvokerHandTransforms`、C `ItemGeoInitializeMixin`）不要搬。
8. **网络层**：两边都是 `<modid>:main` 单通道、version `1.0`、各 8 个包，其中 3 个包类同名（`UniversalPacket`/`PacketSyncKeyframe`/`EffectSyncPacket`）。合并成单通道后共 **13 个包**，corruption 侧 5 个独有包的 **packet id 必须重排**；`PacketActionRegistry` 的 action 字符串两边无交集，但命名风格需统一。

---

## 1. 分析范围与口径

### 1.1 两工程基本盘

| 项 | Hall（基线） | corruption |
|---|---|---|
| 工程路径 | `...\Project\RottenRuinsSplendiding` | `...\Project\corruption\corruption` |
| `mod_id` / `mod_name` / `mod_version` | `hall` / `RottenRuins:The Hall` / `1.0.0Beta` | `corruption` / `Rotten Ruins` / `0.6.0` |
| `mod_group_id` | `org.bytechen` | `com.bytechen` |
| MC / Forge / mappings | 1.20.1 / 47.4.20 / official | 1.20.1 / 47.4.18 / parchment 2023.09.03 |
| Java 文件 | **390 个 / 3,208,160 B / 66,377 行** | **268 个 / 1,429,621 B / 34,250 行** |
| 资源文件（main+generated，去 `.cache`） | **686 个 / 3,200,644 B**（assets 493、data 188、根 5） | **503 个 / 1,730,373 B**（assets 442、data 57、根 4） |
| 依赖（mods.toml mandatory） | forge、minecraft、cloth_config、geckolib、**infcore**、**curios** | forge、minecraft、cloth_config、geckolib |
| 资源命名空间 | `assets/hall`、`data/hall`、`data/forge`、`data/minecraft`、`assets/minecraft`；`src/generated/resources/data/{splendiding,godsick_neo}` 为**空目录残骸**（0 文件） | `assets/corruption`、`data/corruption`、`data/minecraft` |

> **方向判断（与 analysis-hall.md 一致）**：corruption 是把 `splendiding` 时代代码改名为 `hall` 之前的"上一个工程"——共享 Java 骨架普遍是同源复制关系，但**资源与内容 ID 已完全分叉**（Hall 侧全部 `hall_*`/`domerite_*`/`inf_*`，corruption 侧 `spread_*`/`infection_*`/`king_*`/`crude_*`）。

### 1.2 资源哈希口径

- 全量遍历 `src/main/resources` + `src/generated/resources`，**排除 `src/generated/resources/.cache`**（datagen 缓存，非发布资源）。
- 每个文件计算 **SHA-256**；同时记录路径、类别、字节数。
- **路径归一化**（判断"改名后是否写同一目标"）：`assets/<ns>/...` → `assets/*/...`；`data/<ns>/...` → `data/*/...`；根文件按文件名。归一路径相同即"合并到同一 namespace 后会撞名"。
- **跨路径比对**：哈希相同但归一路径不同的文件（内容复用但换了文件名）。
- **等效判定**：文本文件再做三级归一化——① 去 BOM；② CRLF→LF；③ 把 `hall:`/`corruption:` 统一成 `<ns>:`；逐级比较，用于区分"真差异"与"仅换行/仅命名空间"。
- 编码检查：两工程 `src` 下 **Java 与 JSON 全部为合法 UTF-8（0 个非 UTF-8）**；`corruption/gradle.properties` 里的中文注释已损坏成 `?`（571 个 `0x3F` 字节、0 个非 ASCII 字节），属本地历史问题，合并时**不要沿用**该文件。

### 1.3 Java 类比对口径

- 相对路径 = 去掉包根（H: `org/bytechen/hall/`，C: `com/corruption/`）后的路径，90% 以上类可直接同路径配对。
- **归一化 diff**：去 `package`/`import` 行；把包根替换为 `<pkg>`、`hall:`/`corruption:` 替换为 `<modid>:`、`Hall`/`HallMod`/`Corruption` 替换为 `<MOD>`；统一 CRLF 后用 Python `difflib` 同时算**行级相似度**与**token 级相似度**（token 级去注释/格式影响，更接近"逻辑是否相同"）。
- **行差异**用 `+N/-N` 表示：`+` = corruption 有、Hall 没有的行；`-` = Hall 有、corruption 没有的行（`Compare-Object` 同等语义）。
- **改名牌/挪位类**：把 `entitis`（corruption 目录拼写错误）→`entities`、`registry/item/`→`registry/items/`，并把类名中的 `Hall`/`Corruption`/`Infect` 等前缀去掉后再配对，得到 15 对同职责类。

### 1.4 边界与限制

- 只统计 `src/**`，不含 `build/`、`run/`、`run-data/`、`.gradle/`、`libs/`、`logs/`、`.idea/`；因此**不反映**构建产物、jarJar 内容与本地 Maven 镜像。
- 未执行 Gradle/游戏内验证；所有"是否冲突"结论均来自静态比对 + 注册表代码阅读。
- 注册 ID 的**权威来源**做了三路交叉：① `lang/*.json` 键；② 资源目录路径；③ 注册器代码字面量。代码字面量抽取覆盖不全（部分用辅助方法/常量注册），但三路结论一致处才下结论。

---

## 2. 二进制完全重复清单

### 2.1 总览（按类别）

> "撞名对"= 归一化路径相同、合并后写同一目标路径的文件对；共 **42 对**（Hall 侧 90,344 B / corruption 侧 100,845 B）。

| 类别 | 完全重复（字节级） | 仅 CRLF/LF | 仅命名空间字符串 | 真内容差异 | 二进制差异 | 小计 |
|---|---|---|---|---|---|---|
| textures（贴图） | **16**（5,695 B） | 0 | 0 | 0 | **2**（842/585 B） | 18 |
| shaders（`.json/.vsh/.fsh`） | **3**（6,956 B） | **6**（9,062/8,871 B） | **5**（8,480/8,399 B） | 0 | 0 | 14 |
| sounds（`.ogg`） | **1**（`rip_body.ogg` 41,398 B） | 0 | 0 | 0 | 0 | 1 |
| `sounds.json` | 0 | 0 | 0 | **1**（231/1,390 B） | 0 | 1 |
| particles（`.json`） | 0 | 0 | **1**（`meat.json`） | **2**（`blood.json`、`explosion_bloody.json`） | 0 | 3 |
| lang（`en_us/zh_cn`） | 0 | 0 | 0 | **2**（7,946+7,562 / 10,004+11,747 B） | 0 | 2 |
| minecraft tag | 0 | 0 | 0 | **1**（`mineable/pickaxe.json` 443/84 B） | 0 | 1 |
| 根文件（`pack.mcmeta`、`META-INF/mods.toml`） | 0 | 0 | 0 | **2**（85/91 B、1,096/4,911 B） | 0 | 2 |
| **合计** | **20 对 / 54,049 B** | **6 对** | **6 对** | **8 对** | **2 对** | **42 对** |

**其余类别重复数为 0**：`models`（Hall 76 item 模型 / corruption 96）、`blockstates`（33/26）、`geo`（16/29）、`animations`（12/28）、`worldgen`（15/4）、`recipes`（42/12）、`loot_tables`（35/22）、`advancements`（42/17）——归一路径**没有任何交集**，说明两个内容分支的注册 ID 已完全分叉（见 §1.1 方向判断）。

### 2.2 20 对字节级完全重复（SHA-256 相同）

| # | 归一化路径（两侧同名，仅 namespace 目录不同） | 大小 | 类别 | 备注 |
|---|---|---|---|---|
| 1–16 | `textures/particle/explosion_bloody{0..15}.png` | 5,695 B（合计） | textures | 16 帧血腥爆炸序列帧；corruption 侧同名同内容 |
| 17 | `shaders/core/held_item_outline.fsh` | 5,657 B | shaders | 手持描边 fragment shader |
| 18 | `shaders/core/lightning_outline.fsh` | 564 B | shaders | 闪电描边 fragment |
| 19 | `shaders/core/lightning_outline.vsh` | 735 B | shaders | 闪电描边 vertex |
| 20 | `sounds/rip_body.ogg` | 41,398 B | sounds | 爆体音效；Hall 注释已写明"直接从 corruption 复制，未改音频" |

### 2.3 3 对"跨路径同内容"（哈希相同但文件名不同）

| Hall 文件 | corruption 文件 | 大小 | 说明 |
|---|---|---|---|
| `assets/hall/textures/block/hall_pillar.png` | `assets/corruption/textures/block/king_brick.png` | 477 B | **王庭系列继承 KING 系列的字节级证据**（见 §3.3） |
| `assets/hall/textures/block/hall_pillar_top.png` | `assets/corruption/textures/block/1779339231503.png` | 507 B | corruption 侧是 Blockbench 时间戳命名的残留文件（未引用，见 §3.4） |
| `assets/hall/textures/particle/blood.png` | `assets/corruption/textures/particle/bloody.png` | 408 B | 同名语义：Hall 的 `blood` 粒子贴图 = corruption 的 `bloody` 粒子贴图 |

> 这 3 对**不构成合并阻塞**（换成新 namespace 后仍是两个不同文件名），只在"是否顺手去重/清垃圾"时考虑；其中第 2 条同时是 corruption 侧垃圾文件的证据。

### 2.4 42 对撞名文件全量分类（合并后写同一目标路径）

**可直接去重/改名（32 对，无需决策）**

| 分组 | 文件 | 处理 |
|---|---|---|
| 字节级相同（20 对） | 见 §2.2 | corruption 侧不搬，只保留 Hall 一份 |
| 仅 CRLF/LF 差异（6 对，全为 shaders） | `held_item_outline.vsh`、`rendertype_item_glint.vsh`、`held_item_bloom.vsh`、`held_item_bloom.fsh`、`held_item_bloom_blur.fsh`、`rendertype_item_glint.fsh` | 文本等价，保留 Hall（或任意一份），随后统一换行符 |
| 仅命名空间字符串差异（6 对） | `held_item_bloom.json`、`held_item_bloom_blur.json`、`held_item_outline.json`、`rendertype_item_glint.json`、`lightning_outline.json`（5 个 shaders json）+ `particles/meat.json` | 改 namespace 后即完全相同，保留 Hall |

**需要拍板（10 对）**

| 文件（归一化路径） | Hall 字节 | corruption 字节 | 冲突实质 | 建议 |
|---|---|---|---|---|
| `assets/*/lang/en_us.json` | 7,946 | 10,004 | 两个独立语言文件，合并后只有一个目标 | **并集合并**（键数 283，只有 2 个归一化撞键，见 §7.6）|
| `assets/*/lang/zh_cn.json` | 7,562 | 11,747 | 同上 | 并集合并（键数 309）|
| `assets/*/sounds.json` | 231 | 1,390 | 合并后 11 个 sound 键，`rip_body` 撞键 | **并集合并**，`rip_body` 去重、subtitle 统一 |
| `assets/*/particles/blood.json` | 40 | 147 | 同名粒子但描述/纹理集不同 | 与粒子实现一起拍板（§7.4）|
| `assets/*/particles/explosion_bloody.json` | 508 | 567 | 同上（帧列表/参数不同）| 同上 |
| `data/minecraft/tags/blocks/mineable/pickaxe.json` | 443（13 值） | 84（2 值） | 两边 datagen 都会写这个**原版 tag** | **并集**（13+2=15 值），且 datagen 必须合并 |
| `META-INF/mods.toml` | 1,096 | 4,911 | 依赖声明不同（Hall 多 infcore/curios） | 以 Hall 为基准重写（合并后 modid/依赖要变）|
| `pack.mcmeta` | 85 | 91 | 只差 description 文本（均为 pack_format 15）| 用新 modid 重写一份 |
| `textures/particle/meat.png` | 216 | 374 | 同一粒子贴图两版（像素内容不同）| 二选一（建议看实际表现后定）|
| `textures/item/img.png` | 626 | 211 | 占位/调试贴图 | 二选一或直接不带 |

### 2.5 字节占比与解读

| 指标 | 数值 | 占 Hall 资源 | 占 corruption 资源 |
|---|---|---|---|
| 字节级完全重复 | 20 文件 / 54,049 B | 1.69% | 3.12% |
| 等效重复（含 CRLF/命名空间归一化） | 32 文件 / 71,591 B | 2.24% | 4.14% |
| 等效重复 + 跨路径同内容 | 35 文件 / 72,983 B | 2.28% | 4.22% |
| 撞名对规模（合并需处理的文件量） | 42 对（Hall 90,344 B / C 100,845 B） | — | — |

**解读**：

- "重复资源多到值得单独做去重工具"这一假设**不成立**——资源侧迁移的真实工作量是**改名（`hall`→`rotten_ruins`）+ 42 对撞名文件拍板 + datagen 重跑**，而非照搬/去重海量贴图。
- 真正同源的是渲染/粒子/音效这几条"技术底座"：explosion_bloody 序列帧、`held_item_*` / `lightning_outline` shader、`rip_body.ogg`、`meat/blood` 粒子——它们正是 corruption 曾作为 Hall 参考实现留下的痕迹。
- **反向指标**：corruption 侧 **461 个资源文件 / 1,629,528 B（占其资源 94.2%）在 Hall 没有对应物**；Hall 侧 644 个文件 / 3,110,300 B 在 corruption 没有对应物。这决定了合并主要是"内容迁入 + 命名迁移"，而不是"合并同类项"。

---

## 3. 近似重复（同名不同内容）

### 3.1 分类统计（22 对同名但 Hash 不同的文件）

| 类别 | 对数 | 其中 CRLF-only | 其中 命名空间-only | 其中真内容差异 | 二进制差异 |
|---|---|---|---|---|---|
| shaders | 11 | 6 | 5 | 0 | 0 |
| particles | 3 | 0 | 1 | 2 | 0 |
| lang | 2 | 0 | 0 | 2 | 0 |
| textures | 2 | 0 | 0 | 0 | 2 |
| minecraft tag | 1 | 0 | 0 | 1 | 0 |
| sounds.json | 1 | 0 | 0 | 1 | 0 |
| `META-INF/mods.toml` | 1 | 0 | 0 | 1 | 0 |
| `pack.mcmeta` | 1 | 0 | 0 | 1 | 0 |
| **合计** | **22** | **6** | **6** | **8** | **2** |

### 3.2 22 对明细

| # | 文件（归一路径） | Hall B | C B | 差异性质 | 建议 |
|---|---|---|---|---|---|
| 1 | `shaders/core/held_item_outline.vsh` | 160 | 149 | CRLF-only | 去重（留 Hall）|
| 2 | `shaders/core/rendertype_item_glint.vsh` | 490 | 463 | CRLF-only | 去重（留 Hall）|
| 3 | `shaders/core/held_item_bloom.vsh` | 160 | 149 | CRLF-only | 去重（留 Hall）|
| 4 | `shaders/core/held_item_bloom.fsh` | 3,830 | 3,818 | CRLF-only | 去重（留 Hall）|
| 5 | `shaders/core/held_item_bloom_blur.fsh` | 1,144 | 1,117 | CRLF-only | 去重（留 Hall）|
| 6 | `shaders/core/rendertype_item_glint.fsh` | 3,278 | 3,175 | CRLF-only | 去重（留 Hall）|
| 7 | `shaders/core/held_item_outline.json` | 2,809 | 2,821 | 命名空间-only | 改名即同（留 Hall）|
| 8 | `shaders/core/held_item_bloom.json` | 2,615 | 2,627 | 命名空间-only | 改名即同（留 Hall）|
| 9 | `shaders/core/held_item_bloom_blur.json` | 567 | 548 | 命名空间-only | 改名即同（留 Hall）|
| 10 | `shaders/core/rendertype_item_glint.json` | 2,317 | 2,213 | 命名空间-only | 改名即同（留 Hall）|
| 11 | `shaders/core/lightning_outline.json` | 133 | 145 | 命名空间-only | 改名即同（留 Hall）|
| 12 | `particles/meat.json` | 39 | 45 | 命名空间-only | 改名即同（留 Hall）|
| 13 | `data/minecraft/tags/blocks/mineable/pickaxe.json` | 443 | 84 | 真差异（13 vs 2 值）| **并集 15 值** |
| 14 | `assets/*/sounds.json` | 231 | 1,390 | 真差异（2 vs 10 键；撞 `rip_body`）| **并集 11 键** |
| 15 | `assets/*/particles/blood.json` | 40 | 147 | 真差异（单帧 vs spriteSet 参数）| 与粒子实现一起拍板 |
| 16 | `assets/*/particles/explosion_bloody.json` | 508 | 567 | 真差异（帧列表/参数）| 与粒子实现一起拍板 |
| 17 | `META-INF/mods.toml` | 1,096 | 4,911 | 真差异（依赖表/注释）| 以 Hall 为基准重写 |
| 18 | `pack.mcmeta` | 85 | 91 | 真差异（description）| 用新 modid 重写 |
| 19 | `assets/*/lang/en_us.json` | 7,946 | 10,004 | 真差异（138 vs 147 键，仅 2 个归一化撞键）| **并集合并** |
| 20 | `assets/*/lang/zh_cn.json` | 7,562 | 11,747 | 真差异（138 vs 173 键，另注意 corruption 26 个 zh-only 键）| **并集合并** |
| 21 | `textures/particle/meat.png` | 216 | 374 | 二进制差异 | 二选一 |
| 22 | `textures/item/img.png` | 626 | 211 | 二进制差异 | 二选一（或丢弃）|

### 3.3 语义近似但"不同名/不同路径"（不会报错、但会产生重复功能）

这类**不会撞注册表**，但会留下"同一功能两套实体/方块/系统"，属于产品决策项。

| Hall 侧（H） | corruption 侧（C） | 关系判定与证据 | 建议 |
|---|---|---|---|
| `inf_player`（实体 + 刷怪蛋 + `geo/inf_player.geo.json` + `animations/inf_player.animation.json`）| `infection_player`（实体 + `geo/infection_player_{slim,wide}.geo.json` + `OnlineSkinDownloader` 在线皮肤）| 同一"感染玩家"系统的两代实现：C 版有 slim/wide 双模型 + 在线皮肤；H 版有 3 段攻击动画/跳跃/发狂 AI（1,117 行基类） | **保留 Hall 的 `inf_player` 为主**，把 C 的 slim/wide/皮肤能力作为增强回移；否则两个玩家感染体并存 |
| `inf_skeleton` + `inf_skeleton_arrow` | `infection_skeleton` | 同源；H 多了箭矢实体与掉落物 | 保留 Hall |
| `inf_enderman` | （无） | Hall 独有 | 保留 Hall |
| （无） | `infection_{bat,chicken,cow,pig,sheep,villager,wolf,zombie}`、`infection_bone_ruins` | corruption 的感染覆盖广度（11 种 vs Hall 3 种） | **C 类迁入**，但建议统一前缀（`inf_*` 或 `infection_*` 二选一后全量改名）|
| `hall_grass`（block+model+blockstate+texture 四件套）| `corruption_grass`（同四件套）| 同职责"腐化草方块" | 保留 Hall，C 侧不搬 |
| `hall_dirt`/`hall_stone`/`hall_leaves`/`hall_pillar`… | `king_dirt`/`king_stone`/`king_leaves`/`king_brick`//`king_stone_dirt` | **王庭系列 vs KING 系列**；字节证据：`hall_pillar.png` ≡ `king_brick.png`、`hall_pillar_top.png` ≡ `1779339231503.png`；corruption 的 5 个 king 方块**没有任何 lang 键**（游戏里显示原始键名） | 按用户决策**KING 系列不搬**；但注意 Hall 目前**没有** `king_hearthstone`（炉石）/`king_shell`（甲壳）/`king_fiber`（纤维）的直接等价物，若这些内容要保留需补 |
| `hall_bone_fragments` / `hall_tendon`（item）| `king_bone` / `king_shell` / `king_fiber` / `king_hearthstone`（item）| 同为王庭材料体系，命名一一近似但非严格对应 | 按 §3.3 上一行决策 |
| `blood`/`meat`/`explosion_bloody`（Hall 版粒子实现于 `client/particle/`，2–6 倍代码量）| 同名粒子（C 版实现于 `overworld/registry/particles/`）| 同源两代实现，H 版多 bounce/lifetime/缩放逻辑 | **硬碰撞**（§7.4）：保留 Hall；C 的 `blood_fog`/`blood_splash`/`blood_splat`/`bloody`/`degenerate_*` 6 个独有粒子迁入 |
| `rip_body` 音效与 subtitle | `rip_body` | ogg 字节相同、subtitle 文案相同 | 去重合并为一个 |
| `hall_wasteland`（biome）+ `hall:heall`（dimension）+ `swarm/*`（方块/群系扩散，已接 infcore）| `corruption_wasteland`（biome）+ `MixinBiomeManager`（按 chunk 脏位替换群系）+ `CorruptionNodeManager` | **两套完整的世界腐化系统**，都会写区块/群系状态 | **D 类最高风险**：二选一或分层（建议 Hall `swarm` 为主，C 的 biome 视觉替换作为可选子系统关闭）|
| Hall `itemGroup.<ns>.main` + 4 个 section | C `itemGroup.<ns>.main` | 主创造栏标题撞键 | 合并为 Hall 的分区 tab（§7.5）|
| Hall 5 个属性（`max_mana` 等）| （无） | — | 保留 Hall |
| （无） | `bleeding`/`fear` 效果 + `NonRemovable` + `MixinLivingEntity` | corruption 独有机制（不可移除效果） | 若要保留 bleeding/fear 才迁（连带 mixin） |

### 3.4 垃圾 / 未引用资源清单（合并时不要带走）

| 工程 | 文件 | 大小 | 证据 |
|---|---|---|---|
| H | `assets/hall/sounds/撕裂肉体-1.ogg` | 22,581 B | `sounds.json` 只有 `biome_music`/`rip_body`，全工程 java/json 0 引用 |
| H | `assets/hall/sounds/撕裂肉体-2 .ogg`（文件名含空格）| 25,116 B | 同上 |
| H | `assets/hall/sounds/撕裂肉体-3.ogg` | 24,713 B | 同上 |
| H | `assets/hall/textures/gui/Untitled 02-21-2026 09-18-21 (1).png` | 2,917 B | 0 引用 |
| C | `assets/corruption/textures/block/1779339{231503,238775,273732,279522,323568,329354,336631,349234,354608,366915}.png` | 4,219 B（10 个）| 0 引用；Blockbench 时间戳命名；其中 `1779339231503.png` 内容 = Hall `hall_pillar_top.png` |
| C | `assets/corruption/textures/item/model (5).png` | 401 B | 0 引用 |

> 合计：Hall 侧 75,327 B、corruption 侧 4,620 B 的纯垃圾资源。**建议在迁移时顺手清理**，不要进入 `rotten_ruins`（也避免再次出现"从残骸复制成正式资源"的历史，§2.3 第 2 条就是先例）。

---

## 4. Java 类级重复

### 4.1 总览

| 指标 | 数值 | 说明 |
|---|---|---|
| Java 文件 | H **390 个 / 3,208,160 B / 66,377 行**；C **268 个 / 1,429,621 B / 34,250 行** | 统计口径见 §1.3 |
| 相对路径完全相同的类对 | **79** | 占 Hall java 20.3% / corruption 29.5% |
| 其中归一化后**字节等价** | **24**（30.4%） | 只差包名/`hall:`/`corruption:`/类名 |
| token 相似度分布（79 对） | ≥0.95：**33**；0.85–0.95：**8**；0.70–0.85：**6**；0.50–0.70：**12**；<0.50：**20** | token 级去注释/格式，更接近"逻辑是否相同" |
| 行级相似度分布（79 对） | ratio ≥0.8：**36**（含 24 等价）；0.5–0.8：**9**；<0.5：**34** | 行级受注释/换行影响偏大 |
| 改名/挪位"同职责"类对 | **15** | 如 `entitis/`→`entities/`、`registry/item/`→`registry/items/`、`AbstractHallEntity`↔`AbstractCorruptionEntity` |
| 单侧独有类 | Hall **311** / corruption **189** | 见 §4.7 |

**判定原则**：79 对同源类里，**Hall 侧全部是同源复制后继续开发的版本**（时间戳：Hall 侧复制基线 2026-08-03，corruption 侧原作 2026-03~06；Hall 后续改动到 2026-10）——所以除"corruption 独有能力"外，**默认以 Hall 版为准**，corruption 侧只作为"检查 Hall 是否丢功能"的参照。

| 分类 | 数量 | 处理 | 标记 |
|---|---|---|---|
| 完全等价（归一化 byte-diff=0） | 24 | 保留 Hall 一份 | **A** |
| 高度相似（行 ratio ≥0.8 且非等价） | 12 | 保留 Hall，逐处核对小改 | **B** |
| 中度分叉（0.5–0.8） | 9 | 以 Hall 为主，核对 corruption 增量 | **B/D** |
| 大幅分叉（<0.5） | 34 | 内容注册/数据生成：Hall 基底 + 并集重建；逻辑类：逐个人工合并 | **D** |
| 改名/挪位同职责 | 15 | 保留 Hall 版（全部） | **B** |

### 4.2 A 类：24 个归一化后完全等价（不用搬）

> 这 24 个类在合并后**不需要从 corruption 取任何代码**；包名/`MODID` 改动由全局替换完成。

| 相对路径 | Hall 大小/行 | corruption 大小/行 |
|---|---|---|
| `client/entity/IAutoRenderableEntity.java` | 173B/7 | 170B/7 |
| `client/entity/IAutoRenderableProjectile.java` | 177B/7 | 174B/7 |
| `client/rend/AbstractItemRenderHelper.java` | 1K/55 | 1K/55 |
| `client/rend/AbstractStaticEffectRenderer.java` | 6K/205 | 6K/206 |
| `client/rend/CDRenderHelper.java` | 7K/191 | 7K/191 |
| `client/rend/HitboxCubeRenderer.java` | 5K/148 | 5K/148 |
| `client/rend/LightningEffectRenderer.java` | 7K/216 | 7K/217 |
| `client/rend/ZoomRenderHelper.java` | 4K/127 | 4K/127 |
| `client/rend/glint/ClientModEvents.java` | 1K/44 | 1K/45 |
| `client/rend/glint/GlintRenderManager.java` | 2K/65 | 2K/65 |
| `client/rend/glint/HeldItemBloomBlurShaderRegistry.java` | 589B/22 | 586B/22 |
| `client/rend/glint/HeldItemBloomShaderRegistry.java` | 581B/22 | 578B/22 |
| `client/rend/glint/HeldItemGlintHelper.java` | 2K/93 | 2K/94 |
| `client/rend/glint/HeldItemOutlineColorSampler.java` | 10K/276 | 10K/276 |
| `client/rend/glint/HeldItemOutlineEffectProfile.java` | 3K/81 | 3K/81 |
| `client/rend/glint/HeldItemOutlineShaderRegistry.java` | 682B/27 | 679B/27 |
| `client/rend/glint/HeldItemRuleManager.java` | 33K/814 | 33K/814 |
| `client/rend/glint/ShaderFeature.java` | 2K/73 | 2K/74 |
| `client/rend/glint/ShaderFeatureManager.java` | 1K/42 | 1K/42 |
| `mixin/GameRendererAccessor.java` | 713B/27 | 710B/27 |
| `mixin/ItemInHandRendererMixin.java` | 3K/57 | 3K/57 |
| `mixin/MixinAnimationController.java` | 1K/37 | 1K/37 |
| `network/c2s/PacketSyncKeyframe.java` | 1K/44 | 1K/44 |
| `overworld/registry/ClientRenderRegistry.java` | 1K/36 | 1K/36 |

### 4.3 B 类：12 个高度相似（保留 Hall，核对小改）

| 相对路径 | Hall 大小/行 | corruption 大小/行 | 行差异（+C/−H） | 判定 |
|---|---|---|---|---|
| `client/rend/glint/HeldItemOutlineSettings.java` | 16K/496 | 16K/478 | +0/−18（0.98，token 0.99）| 取 Hall（多 `OutlineMode` 枚举 + AUTO 模式 18 行）|
| `mixin/GameRendererOculusMixin.java` | 7K/172 | 8K/184 | +12/−0（0.96）| 取 Hall（注释更全，注入点相同）|
| `mixin/LevelRendererEmbeddiumMixin.java` | 2K/63 | 3K/67 | +4/−0（0.96）| 取 Hall（注入点相同）|
| `mixin/GameRendererMixin.java` | 4K/86 | 5K/93 | +7/−0（0.95）| 取 Hall（注入点相同）|
| `client/rend/ItemRenderManager.java` | 3K/87 | 3K/83 | +2/−6（0.95）| 取 Hall，确认 C 的 2 行差异是否为兼容补丁 |
| `client/entity/IGeoLayerProvider.java` | 835B/25 | 860B/26 | +2/−1（0.91）| 取 Hall（接口方法更多）|
| `network/s2c/EffectSyncPacket.java` | 2K/59 | 1K/48 | +0/−8（0.91）| 取 Hall（多 `meteorite_ground`/`gui_shader` 两种 typeId 分发）|
| `client/rend/glint/GlintEffectProfile.java` | 16K/413 | 12K/341 | +1/−75（0.90，token 0.92）| 取 Hall（多 gradient/fire/outlineShaderKey 等 builder API）|
| `client/entity/IGeoResources.java` | 223B/10 | 222B/12 | +2/−0（0.89）| 取 Hall |
| `client/entity/model/GeoBaseModel.java` | 1K/31 | 1K/36 | +6/−1（0.86）| 取 Hall，核对 C 多 6 行 |
| `client/entity/model/GeoItemModel.java` | 903B/29 | 678B/24 | +1/−6（0.84）| 取 Hall，核对 C 多 1 行 |
| `client/entity/render/GeoProjectileRenderer.java` | 1K/30 | 1K/29 | +3/−4（0.83）| 取 Hall，核对 C 多 3 行 |

### 4.4 C 类：9 个中度分叉（逐处核对后取 Hall + 补 C 增量）

| 相对路径 | Hall 大小/行 | corruption 大小/行 | 行差异 | 判定 |
|---|---|---|---|---|
| `client/rend/glint/HeldItemOutlineCompat.java` | 2K/62 | 2K/78 | +24/−8（0.75，token 1.00）| 逻辑等价（token 1.00），格式差异；取 Hall |
| `network/c2s/IKeyframeHandler.java` | 118B/6 | 195B/10 | +4/−0（token 1.00）| 接口等价；取 Hall |
| `event/ModEventHandler.java` | 537B/15 | 582B/21 | +7/−1（0.69，token 0.93）| 取 Hall + 核对 C 多 7 行事件注册 |
| `overworld/registry/capability/base/IModCapability.java` | 212B/9 | 229B/8 | +1/−3（0.67）| 取 Hall（方法签名更完整）|
| `client/entity/IBillboardRenderable.java` | 84B/5 | 129B/7 | +3/−1（token 1.00）| 取 Hall |
| `mixin/accessor/AccessorRenderStateShard.java` | 1K/57 | 1K/33 | +5/−29（0.58）| **取 Hall**（多 `CULL`/`EQUAL_DEPTH_TEST`/`NO_DEPTH_TEST`/`MAIN_TARGET` 4 个 accessor；accessor 方法前缀 H 是 `splendiding$`、C 是 `corruption$`，迁移时统一改名）|
| `mixin/ItemGlintMixinPlugin.java` | 1K/57 | 3K/90 | +45/−12（0.58，token 0.76）| **取 Hall + 补 C 的 2 条分支**：`OculusHandRendererMixin` 强制 false、`corruption.debugCompat` 调试开关 |
| `client/entity/IGeoModelBehavior.java` | 538B/12 | 418B/15 | +6/−2（0.56）| 取 Hall，核对 C 多 6 行 |
| `client/entity/model/GeoProjectileModel.java` | 659B/19 | 712B/24 | +10/−5（0.55）| 取 Hall，核对 C 多 10 行 |

### 4.5 D 类：34 个大幅分叉（必须人工合并 / 并集重建）

> 34 个里 **18 个是内容注册表/datagen**（天然是"并集"工作，不是逻辑冲突），另外 **16 个是框架/逻辑类**（真正需要逐个拍板）。
> 行差异列 `+C/−H` 来自归一化后行级比较，只反映规模；关键判定列写明依据。

**D-1 内容注册与数据生成（18 个类、17 行——Lang 中/英两个类合并一行；按"Hall 基底 + 并入 corruption 条目"处理）**

| 相对路径 | Hall 大小/行 | corruption 大小/行 | 行差异 | 说明 |
|---|---|---|---|---|
| `overworld/registry/RegisterItem.java` | 11K/217 | 9K/124 | +84/−172（token 0.30）| Hall 基底 + corruption 物品（含 5 个 KING 按决策不搬）|
| `overworld/registry/RegisterBlock.java` | 11K/237 | 9K/205 | +166/−183（0.32）| 并集；注意 C 的 `king_*` 方块不搬 |
| `overworld/registry/RegisterBlockEntities.java` | 1K/28 | 914B/21 | +6/−12（0.44）| 并集（C 独有 `fleshbed`）|
| `overworld/registry/RegisterEffect.java` | 1K/31 | 1K/30 | +13/−15（0.51）| 并集：Hall 4 + C 6（`miasma`/`corruption`/`lysis`/`sclerosis`/`bleeding`/`fear`），后缀零交集 |
| `overworld/registry/RegisterParticles.java` | 6K/150 | 3K/63 | +36/−129（0.48）| **并集前必须先解决 3 个同名硬碰撞**（§7.4）|
| `overworld/registry/SoundEventRegistry.java` | 1K/34 | 1K/31 | +16/−20（0.53）| 并集；`rip_body` 去重（§7.3）|
| `overworld/registry/EntityTypeRegistry.java` | 15K/304 | 16K/293 | +201/−204（token 0.31）| 并集；生成刷怪蛋/Hall 的 23 个实体 + C 的 21 个实体 |
| `overworld/registry/RegisterTab.java` | 8K/140 | 7K/138 | +113/−118（0.57）| **并集 = 单 tab**：Hall 的 4 分区结构 + C 物品并入分区（§7.5）|
| `datagen/DataGenEvent.java` | 2K/42 | 1K/35 | +15/−22（0.81 token）| **合并成单 DataGenEvent**：Hall 15 provider + C 4 个独有 provider（AdvancementProviderData/EntityLootProvider/CorruptionBiomeData/WorldGenData）|
| `datagen/gen/BlockStateData.java` | 21K/530 | 13K/327 | +266/−467（0.31）| 单 provider 输出并集；**输出路径会撞**（模型/方块状态 id 不同，但 tag/atlas 会撞）|
| `datagen/gen/BlockTagData.java` | 5K/130 | 1K/37 | +14/−108（0.30）| 单 provider；**都会写 `data/minecraft/tags/blocks/mineable/pickaxe.json`（§7.7）** |
| `datagen/gen/ItemGenData.java` | 2K/49 | 6K/173 | +138/−22（0.33）| 单 provider；C 侧对 item 模型命名规则不同，需统一 |
| `datagen/gen/LootTableData.java` | 5K/121 | 504B/17 | +2/−87（0.19）| 单 provider；C 有独立 `EntityLootProvider` |
| `datagen/gen/ParticleData.java` | 3K/79 | 4K/143 | +115/−61（0.17）| 单 provider；**3 个同名粒子 json 输出路径相撞（§7.4）** |
| `datagen/gen/RecipeProviderData.java` | 18K/454 | 5K/138 | +112/−427（0.27）| 单 provider；配方 id 零交集 |
| `datagen/gen/SoundData.java` | 1K/31 | 4K/99 | +81/−15（0.33）| 单 provider；**`sounds.json` 输出路径相撞（§7.3）** |
| `datagen/gen/lang/LangDataEN.java` / `LangDataCN.java` | 13K/203、10K/188 | 12K/197、13K/246 | +168/−171、+219/−164（0.18/0.19）| **必须合并为一个 lang provider**：输出路径相撞（§7.6）|

**D-2 框架/逻辑类（16 个：逐个人工合并）**

| 相对路径 | Hall 大小/行 | corruption 大小/行 | 行差异 | 判定 |
|---|---|---|---|---|
| `utils/ModUtils.java` | 618B/16 | 19K/448 | +391/−3（token **0.04**）| **D 最重**：Hall 把 ModUtils 拆成了 7 个小工具类，C 的 30 个静态方法中 **20 个在 Hall 无同名实现**（§4.8），迁内容前必须补/映射 |
| `network/NetworkHelper.java` | 6K/116 | 4K/95 | +42/−66（token 0.69）| 合并为单通道 13 包（§6）|
| `network/all/PacketActionRegistry.java` | 834B/22 | 1K/37 | +21/−8（token 0.63）| 合并 action 表：Hall 4 + C 1，无撞键但需统前缀 |
| `network/all/UniversalPacket.java` | 1K/29 | 1K/43 | +25/−11（token 0.93）| 逻辑近似；取 Hall 结构 + 核对 C 的 25 行 |
| `network/all/tools/UniversalPacketData.java` | 3K/66 | 3K/81 | +63/−50（token 0.19）| 两代契约不同，需按 Hall 版重写 C 的 5 个包数据体 |
| `config/ConfigHelper.java` | 8K/209 | 2K/60 | +37/−186（token 0.21）| 取 Hall（含 configVersion 迁移逻辑）+ 把 C 的 7 个 `@Config` 类加进注册列表 |
| `event/ForgeEventHandler.java` | 4K/127 | 7K/189 | +120/−72（token 0.36）| **两套 Forge 监听器并存**：命令注册、LivingTick、LivingDeath、地形/群系写入都要去重；扩散系统二选一（§3.3）|
| `overworld/registry/CapabilityRegistry.java` | 4K/100 | 1K/41 | +11/−67（token 0.44）| 取 Hall 的 4 个 capability（anomaly/threat/magic_pool/mana）+ 并入 C 的 `Deterrence`（若保留威慑系统）|
| `overworld/registry/capability/base/AbstractCapability.java` | 286B/9 | 1K/54 | +47/−3（token 0.29）| 取 Hall（Hall 是重写后的极简版；C 版 47 行是旧实现）|
| `overworld/registry/capability/base/CapabilityProvider.java` | 1K/31 | 2K/63 | +40/−13（token —）| 取 Hall（多 provider 注册助手）；核对 C 的 40 行是否有需要的 attach 逻辑 |
| `overworld/registry/savedata/base/AbstractSaveData.java` | 368B/10 | 1K/62 | +54/−2（token 0.43）| 取 Hall（C 的 54 行是旧版实现）|
| `overworld/registry/savedata/base/ISaveData.java` | 205B/9 | 419B/20 | +13/−3（token —）| 取 Hall |
| `mixin/ItemRendererMixin.java` | 3K/65 | 4K/96 | +45/−20（token 0.53）| **取 Hall**：C 的 `renderGlintPass(...)` 是旧 glint 渲染，Hall 已重构到 `ItemOutlinePipeline`/`OutlineGlintRenderer`；Hall 另多 1 处 `popPose` 注入 |
| `client/rend/glint/HeldItemOutlineRenderer.java` | 38K/427 | 57K/1,207 | +986/−212（token **0.79**）| **取 Hall 为主**：行差主要来自格式与拆分（Hall 新增 `ItemOutlinePipeline`/`OutlineMaskRenderType`/`LateOutlineRenderState`/`ShaderPackDetector`），逻辑 token 相似 0.79；迁移时逐方法核对 embeddium 批渲染、Oculus 兼容、scissor padding（Hall=28 vs C=8）三处 |
| `client/entity/render/GeoBaseRender.java` | 4K/96 | 3K/74 | +27/−50（0.43）| 取 Hall 版（Hall 是 geo 渲染底座重构版；C 的 27 行差异多为旧 API/兼容代码，逐条核对后弃）|
| `api/IAutoRenderableItem.java` | 1K/47 | 205B/8 | +0/−38 | 取 Hall（接口含默认方法，C 版是空接口）|

> 说明：D 类里 `HeldItemOutlineRenderer` 与 `ItemRendererMixin` 的"corruption 行更多"不代表 C 有更多功能——C 的 glint/outline 是**重构前**的单体实现；Hall 拆成 4 个类后行更少但 token 不含糊（0.79）。**不要用 C 版覆盖 Hall 版**（与 analysis-hall.md §4.3 判断一致）。

### 4.6 15 对改名 / 挪位"同职责"类（全部保留 Hall 版）

> 这 15 对**不在 79 对同路径清单里**（corruption 目录拼写 `entitis`、`registry/item/` 单数、类名前缀不同），但按职责/内容对得上，迁移时最容易被漏看，故此单列。

| Hall 侧 | Hall 大小/行 | corruption 侧 | corruption 大小/行 | 行相似度 | 判定 |
|---|---|---|---|---|---|
| `overworld/swarm/HallDiskStorage.java` | 4,178B/123 | `overworld/registry/block/base/CorruptionDiskStorage.java` | 2,915B/66 | 0.47 | 保留 Hall（C 版随 biome 扩散系统一起不搬）|
| `overworld/registry/entities/base/HallProjectileManager.java` | 784B/23 | `.../entitis/base/CorruptionProjectileManager.java` | 1,938B/47 | 0.43 | 保留 Hall |
| `client/particle/ExplosionBloodyParticle.java` | 5,993B/123 | `overworld/registry/particles/ExplosionBloodyParticle.java` | 2,079B/61 | 0.39 | 保留 Hall（粒子实现更新，含 16 帧 lifetime 逻辑）|
| `client/entity/render/GeoItemRenderer.java` | 860B/22 | `client/entity/render/CorruptionGeoItemRenderer.java` | 414B/11 | 0.37 | 保留 Hall |
| `overworld/swarm/HallBiomeProcessor.java` | 12,995B/329 | `.../block/base/BiomeCorruptionProcessor.java` | 9,377B/217 | 0.35 | 保留 Hall（已接 infcore `BiomeSpreadHelper`）|
| `overworld/registry/entities/base/AbstractHallProjectile.java` | 2,379B/49 | `.../entitis/base/AbstractCorruptionProjectile.java` | 4,864B/130 | 0.32 | 保留 Hall，核对 C 的 130 行投射物逻辑是否要回移 |
| `client/particle/MeatParticle.java` | 10,014B/194 | `overworld/registry/particles/MeatParticle.java` | 4,469B/124 | 0.27 | 保留 Hall |
| `overworld/registry/entities/base/EntityManager.java` | 780B/23 | `.../entitis/base/CorruptionEntityManager.java` | 3,193B/76 | 0.27 | 保留 Hall，核对 C 的 76 行注册逻辑 |
| `overworld/registry/blocks/impl/HallGrassBlock.java` | 876B/24 | `overworld/registry/block/CorruptionGrassBlock.java` | 1,717B/38 | 0.26 | 保留 Hall |
| `datagen/gen/HallBiomeData.java` | 9,539B/189 | `datagen/gen/CorruptionBiomeData.java` | 4,274B/77 | 0.24 | 并入单 provider，Hall 结构为准 |
| `datagen/gen/HallWorldGenData.java` | 2,477B/49 | `datagen/gen/WorldGenData.java` | 3,041B/65 | 0.19 | 并入单 provider |
| `client/particle/BloodParticle.java` | 12,106B/246 | `overworld/registry/particles/BloodParticle.java` | 3,391B/82 | 0.16 | 保留 Hall |
| `overworld/registry/entities/base/AbstractHallEntity.java` | 61,871B/1,117 | `.../entitis/base/AbstractCorruptionEntity.java` | 46,210B/1,087 | 0.12 | 保留 Hall（已重写并接 infcore）|
| `client/rend/SplendidingShaders.java` | 47,012B/801 | `client/rend/CorruptionShaders.java` | 5,406B/121 | 0.07 | 保留 Hall（cosmic/mask 等 shader 注册全在 Hall 侧）|
| `overworld/swarm/HallSpreadEventHandler.java` | 12,018B/289 | `event/SpreadEventHandler.java` | 2,614B/63 | 0.07 | 保留 Hall |

### 4.7 单侧独有类分布（迁移清单骨架）

**corruption 独有 189 个**（= 268 − 79；含 15 对里的 corruption 侧，故与"迁移 189"略有重叠，实际**需迁入的类约 174 个**）：

| 包（相对包根） | 文件数 | 性质 | 迁移建议 |
|---|---|---|---|
| `overworld/registry/entitis/**`（含 base/ai/brains/population）| 57 | 实体内容 + AI | **C 类迁入**；AI/brain 按用户决策不搬（改用 infcore 索敌），可减约 9 个 ai/brains 类 |
| `overworld/registry/item/**` + `item/file/**` | 18 | 物品内容（含 File 系统）| C 类迁入；`king_*` 5 个不搬 |
| `overworld/registry/block/**`（含 base）| 14 | 方块内容（含 fleshbed）| C 类迁入；`king_*` 5 个不搬 → 约 9 个 |
| `overworld/registry/{particles,effect,projectile,structure,capability/impl,savedata/impl}` | 8+7+2+2+1+1 = 21 | 粒子/效果/投射物等内容 | C 类迁入（粒子只需 6 个独有；效果 6 个；其余按需）|
| `overworld/registry/savedata/base/ISaveDataHolder.java` | 1 | 存档接口 | C 类：随 `CorruptionData`/`BiomeCorruptionData` 迁入（或改接 Hall 的 `ISaveData`）|
| `overworld/manager/**` + `manager/spawn/**` | 7 | 腐化扩散/节点/同化/生成管理器 | **D 类**：与 Hall swarm 二选一或分层；建议仅迁"生成/节点"数据能力 |
| `overworld/data/**`（含 level/node）| 4 | 腐化数据/节点 | D 类，随管理器决定 |
| `network/s2c/**` | 5 | 网络包 | **C/D 类**：必须迁（否则 C 内容功能缺失），见 §6 |
| `mixin/**`（含 accessor）| 6 | 混合注入 | C 类迁入（3 个真正需要，2 个未用，见 §5）|
| `command/**` | 3 | 命令 | **D 类**：需并入单命令树（§7.2）|
| `config/**` + `config/data/**` | 8 | 配置 | D 类：并入 ConfigHelper，或按决策舍弃（ShaderConfig/TargetWhitelistConfig 与旧渲染/索敌绑定）|
| `datagen/gen/**` | 4 | 数据生成 | D 类：并入单 provider |
| `event/**` + `event/handler/**` | 8 | 事件处理 | **D 类**：与 Hall 事件系统去重（扩散/群系）|
| `api/**`（ICorruptionEntity/IDeterrence/ISevenSins/IVariant/IVariantEntity）| 5 | 接口 | C 类：按内容保留（SevenSins/变体系统）|
| `client/**`（layer/render/rend 共 11）| 11 | 客户端渲染 | C 类：把 C 的 mob 渲染改成 Hall 的 `GeoBaseRender` 体系（别直接搬旧渲染）|
| `utils/**` + `utils/entity/**` | 6+6 | 工具 | **D 类**：20 个方法 Hall 缺失（§4.8）|
| `overworld/registry/{MemoryModuleTypeRegistry,RegisterStructures}` | 2 | 注册器（1 个真注册 + 1 个空类）| C 类：`MemoryModuleTypeRegistry`（`ai_state`）迁入；`RegisterStructures` 是空类，不搬 |
| `overworld/registry/world/ConfigDrivenSpawnModifier.java` | 1 | 数据驱动刷怪 biome modifier | C 类：随 `CorruptionSpawnConfig` 迁入（与 Hall 的 `BIOME_MODIFIER_SERIALIZERS` 合并）|
| `compat/jei/CorruptionJeiPlugin.java` | 1 | JEI 兼容 | C 类（Hall 无 JEI 插件）|
| `Corruption.java` | 1 | 主类 | 不搬（合并进 `rotten_ruins` 主类）|

**Hall 独有 311 个**（= 390 − 79）：主要是 Hall 新增能力（magic 8 + api 9、mask 16、cosmic 11、fx 13+4、difficulty 7、swarm 5、技能实体 11、渲染 impl 30、命令 7、network s2c/c2s 9 等）——**全部保留**，合并时只需保证 corruption 迁入的内容接进这些框架（例如 C 的生物改用 Hall 的 `AbstractHallEntity`/`GeoBaseRender`/infcore 索敌）。

### 4.8 工具层与"看起来一样其实缺功能"的重点

**`ModUtils`（corruption 30 个静态方法 → Hall 只有 16 行）**：corruption 的 `utils/ModUtils.java` 448 行、30 个静态方法；Hall 的 `ModUtils` 只有 `modLoc`/`mcLoc`/`clamp` 3 个方法，其余能力被拆到 `RandomUtils`/`SpellDamageUtil`/`TranslateUtils`/`TickUtils`/`DomeriteStatsHelper`/`utils/combat/RangeAttackUtils`/`utils/entity/*` 等。**按方法名核对，corruption 有 20 个方法在 Hall 找不到同名实现**：

```
VecToRotation, rotationToVec, clipPoint, rayClip, orbClip,
applyAreaDamage, applyForwardAreaDamage, forceAddEffect,
getDataHealthID, setDataHealthID, ticknessTimer,
setBiome, setBlockPosBiome, updateChunkAfterBiomeChange,
getEnchantment, getEquipmentSlot, getFiddle, getFiddledDistance,
sendMessage, setAttributeValue
```

其中 `rayClip/orbClip/clipPoint`（射线裁剪）、`applyAreaDamage/applyForwardAreaDamage`（范围伤害）、`setBiome/setBlockPosBiome/updateChunkAfterBiomeChange`（群系写回）是 corruption 战斗/扩散系统的**硬依赖**——迁 corruption 内容时这些方法要么原样搬进 `rotten_ruins` 的 utils，要么逐一映射到 Hall 的等价物（映射表建议在实体迁入任务里做）。

**capability/savedata 基类**：`AbstractCapability`、`CapabilityProvider`、`IModCapability`、`AbstractSaveData`、`ISaveData` 五对全部是"H 版更小/更简、C 版 47~62 行旧实现"，**保留 Hall**；但 corruption 的 `IDeterrence` capability 需要并入 `CapabilityRegistry`（新增 1 个 capability + 1 个 `CapabilityProvider` 注册点），否则 C 的威慑/索敌系统无法工作。

**其它"同名但不同职责"情况**：`EntityManager`（Hall 是内部基类）、`ClientRenderRegistry`（完全等价）、`DataGenEvent`（Hall 15 provider vs C 11 provider）已在上表覆盖，无遗漏的第三类命名冲突。

---

## 5. Mixin 冲突分析

### 5.1 两份 mixins.json 逐项对比

| 项 | `hall.mixins.json`（H） | `corruption.mixins.json`（C） |
|---|---|---|
| `package` | `org.bytechen.hall.mixin` | `com.corruption.mixin` |
| `plugin` | `ItemGlintMixinPlugin` | 同名类（逻辑多 2 条分支 + `corruption.debugCompat` 调试开关）|
| `refmap` | `hall.refmap.json` | `corruption.refmap.json` |
| `compatibilityLevel` | JAVA_17 | JAVA_17 |
| `mixins`（common） | **1**：`MixinAnimationController` | **6**：`MixinAnimationController`、`MixinBiomeManager`、`MixinLivingEntity`、`accessor.AccessorLivingEntity`、`accessor.AccessorDisplay`、`accessor.ItemDisplayAccessor` |
| `client` | **15** | **7** |
| 静态注册 mixin 类数 | **16** | **13** |
| mixin 目录 java 文件数 | 18（17 个 mixin + 1 个 plugin）| 15（14 个 mixin + 1 个 plugin）|
| 未注册类 | `accessor.InvokerHandTransforms`（目标 `ItemInHandRenderer`，全工程 0 引用）| `ItemGeoInitializeMixin`（目标 `Item`，json/plugin 均未加载，0 引用）|
| 其它 | `injectors.defaultRequire=1`、`overwrites.requireAnnotations=true` | 同 |

### 5.2 8 个同名 mixin 的 target / 注入点对比（Hall 全部为超集）

> 比对方法：提取每个 mixin 的全部 `@Inject/@Redirect/@ModifyVariable/@Accessor/@Shadow` 注解块（含 `target`/`at`/`method`/`ordinal`），归一化类名与成员名后求集合差。

| 同名 mixin | target 类 | 注入点对比 | 结论 |
|---|---|---|---|
| `MixinAnimationController` | `AnimationController`（geckolib，`remap=false`）| 3 / 3 注解**完全相同**（注释不同）| 等价，留 Hall |
| `GameRendererAccessor` | `GameRenderer` | 6 / 6 **完全相同** | 等价，留 Hall |
| `GameRendererMixin` | `GameRenderer`（priority 900）| 4 / 4 **完全相同**，Hall 多 7 行注释 | 留 Hall |
| `GameRendererOculusMixin` | `GameRenderer`（priority 900）| 14 / 14 **完全相同**，Hall 多 12 行 | 留 Hall |
| `ItemInHandRendererMixin` | `ItemInHandRenderer`（priority 900）| 5 / 5 **完全相同**（字节级等价，见 §4.2）| 留 Hall |
| `LevelRendererEmbeddiumMixin` | `LevelRenderer`（priority 1100）| 4 / 4 **完全相同**，Hall 多 4 行 | 留 Hall |
| `ItemRendererMixin` | `ItemRenderer` | **H 5 个注解 / C 3 个**；H 多 1 处 `@Inject(method="render", at=INVOKE PoseStack.popPose, shift=BEFORE)`；C 多一个约 45 行的 `renderGlintPass(...)` 旧 glint 实现 | **留 Hall**：C 的 `renderGlintPass` 已被 Hall 的 `ItemOutlinePipeline`/`OutlineGlintRenderer` 取代，不要回移 |
| `accessor.AccessorRenderStateShard` | `RenderStateShard` | **H 10 个注解 / C 6 个**；H 多 `CULL`/`EQUAL_DEPTH_TEST`/`NO_DEPTH_TEST`/`MAIN_TARGET` 4 个 `@Accessor`；accessor 方法前缀 H 是 `splendiding$`、C 是 `corruption$`（**历史遗留，迁移时统一改名**）| 留 Hall |

### 5.3 corruption 独有 mixin 与未注册类

| mixin | target | 注入点 / 作用 | 谁在用 | 建议 |
|---|---|---|---|---|
| `MixinBiomeManager` | `BiomeManager` | `@Inject(method="getBiome", at=HEAD, cancellable)`：按 chunk 脏位 + `CorruptionNodeManager` 判定，把群系替换为 `ModBiomes` 的腐化群系 | `BiomeCorruptionProcessor`、`InfectionTrackerHandler`、`ModBiomes` | **最高风险**：这是 corruption 的"群系级腐化"入口，与 Hall 的 `swarm`/biome modifier/infcore `BiomeSpreadHelper` 是两套并行世界写入。若合并后两套都开，会出现 chunk 反复改写/群系闪烁/性能问题。**建议按 §3.3 拍板后再决定搬不搬**（推荐不搬，用 Hall swarm 承担）|
| `MixinLivingEntity` | `LivingEntity` | `removeEffect`/`removeAllEffects` 的 HEAD cancellable：保护实现 `NonRemovable` 的效果不被移除 | `NonRemovable` 及 `BleedingEffect`/`FearEffect` 等 | 随 `bleeding`/`fear` 效果一起搬（若效果迁入）|
| `accessor.AccessorLivingEntity` | `LivingEntity` | `@Accessor` `DATA_HEALTH_ID` + `getActiveEffects()` | `ModUtils.getDataHealthID/setDataHealthID/forceAddEffect`（4 处）| 必须随这 4 个工具方法一起搬（§4.8）|
| `accessor.AccessorDisplay` | `Display` | 仅声明，**全工程 0 引用** | 无 | **不搬（死 mixin）** |
| `accessor.ItemDisplayAccessor` | `Display.ItemDisplay` | 仅声明，**全工程 0 引用** | 无 | **不搬（死 mixin）** |
| `ItemGeoInitializeMixin` | `Item` | 未写入任何 mixins.json、plugin 也未返回 | 无 | **不搬（未注册死类）** |
| （H）`accessor.InvokerHandTransforms` | `ItemInHandRenderer` | 未注册、0 引用 | 无 | **删除/不搬** |

### 5.4 合并后的建议唯一集合

- **单份 `rotten_ruins.mixins.json`**（不要两个 config 并存）：package 建议 `org.bytechen.rottenruins.mixin`、refmap `rotten_ruins.refmap.json`、plugin 用 Hall 版 + 补 C 的 `OculusHandRendererMixin` 置 false 分支与 debug 开关。
- **注册集合 = Hall 16 项 + corruption 3 项**（`MixinBiomeManager` 视决策、`MixinLivingEntity`、`accessor.AccessorLivingEntity`）≈ **19 个 mixin 类**。

**合并后 target 唯一集合（去重后）**：

| target 类 | 保留的 mixin（priority） | 数量 |
|---|---|---|
| `GameRenderer` | `GameRendererAccessor`、`GameRendererMixin`(900)、`GameRendererOculusMixin`(900)、`ScreenShakeMixin`(900)、`OutlineReplayMixin`(500)、`CosmicAfterLevelMixin`(500) | 6 |
| `ItemRenderer` | `ItemRendererMixin`、`MixinItemRendererCosmic`、`MixinItemRendererTwitch` | 3 |
| `ItemInHandRenderer` | `ItemInHandRendererMixin`(900)、`CrimsonVowBlockMixin`(890)（`InvokerHandTransforms` 不搬）| 2 |
| `LevelRenderer` | `LevelRendererEmbeddiumMixin`(1100) | 1 |
| `RenderStateShard` | `AccessorRenderStateShard` | 1 |
| `AbstractContainerScreen` | `AccessorAbstractContainerScreen` | 1 |
| `CreativeModeInventoryScreen` | `CreativeModeInventoryScreenMixin` | 1 |
| `AnimationController`（geckolib）| `MixinAnimationController` | 1 |
| `BiomeManager` | `MixinBiomeManager`（**视腐化系统决策**）| 1 |
| `LivingEntity` | `MixinLivingEntity` + `AccessorLivingEntity` | 2 |
| `Display` / `Display.ItemDisplay` | ~~`AccessorDisplay`~~ / ~~`ItemDisplayAccessor`~~ | 0（死 mixin，不搬）|
| `Item` | ~~`ItemGeoInitializeMixin`~~ | 0（未注册死类）|
| **合计** | | **19** |

- **重复 target 风险**：`GameRenderer` 上 6 个、`ItemRenderer` 上 3 个、`ItemInHandRenderer` 上 2 个 mixin 属于**同一个模组内的多 mixin 协作**（Hall 现状已如此，可正常工作）；真正的风险来自"把 corruption 的同 target 旧 mixin 也并进来"——那样 `GameRenderer` 会变成 9 个、且旧 mixin 与新 mixin 对同一方法各自 `@Inject(cancellable)`，返回值会互相覆盖。**因此 8 个同名 mixin 只能保留 Hall 版本，不能"两份都留"。**
- **风险**：若把 C 的 mixin config + 类原样复制进来，`GameRenderer` 会被 3(C)+6(H)=9 个 mixin 同时注入、`ItemRenderer`/`ItemInHandRenderer` 各有 2+ 个交叉注入，Mixin 优先级（500/890/900/1100）与 `cancellable` 返回值语义会互相覆盖——**必须合并为单 config、单类**，不能靠"两份 json 并存"。

---

## 6. 网络通道 / packet id 冲突

### 6.1 通道注册方式（两边结构完全一致）

```java
// H: org.bytechen.hall.network.NetworkHelper
// C: com.corruption.network.NetworkHelper
public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
        ResourceLocation(MODID, "main"), () -> "1.0", s -> true, s -> true);
private static int packetId = 0;   // 注册时 packetId++ 线性分配
```

| 项 | Hall | corruption |
|---|---|---|
| channel 名 | `hall:main` | `corruption:main` |
| 合并后（modid=rotten_ruins） | `rotten_ruins:main` | **同名** |
| 协议版本 | `"1.0"`，accept-all | 同 |
| registerMessage 数 | **8** | **8** |
| 方向显式声明（6 参重载）| `ManaSyncPacket`、`MagicPoolSyncPacket`（PLAY_TO_CLIENT）| `GorePacket`、`PacketSyncSaveData`、`PacketSyncCapability`（PLAY_TO_CLIENT）|

### 6.2 包清单与 id 对照

| 合并后 id | 包类 | H 原 id | C 原 id | 说明 |
|---|---|---|---|---|
| 0 | `UniversalPacket` | 0 | 1 | 同名类，token 0.93（H 版结构 + C 25 行差异）|
| 1 | `PacketSyncKeyframe` | 1 | 2 | 归一化**完全等价**（§4.2）|
| 2 | `EffectSyncPacket` | 2 | 5 | 同名类，H 是超集（多 `meteorite_ground`/`gui_shader` 分发）|
| 3 | `AnomalySyncPacket` | 3 | — | H 独有 |
| 4 | `S2COpenDifficultySelectPacket` | 4 | — | H 独有 |
| 5 | `C2SDifficultySelectPacket` | 5 | — | H 独有 |
| 6 | `ManaSyncPacket` | 6 | — | H 独有（PLAY_TO_CLIENT）|
| 7 | `MagicPoolSyncPacket` | 7 | — | H 独有（PLAY_TO_CLIENT）|
| **8** | `UpdateBiomePacket` | — | **0** | **C 独有，id 必须重排** |
| **9** | `CParticleSpawnPacket` | — | **3** | **C 独有，id 重排** |
| **10** | `GorePacket` | — | **4** | **C 独有，id 重排** |
| **11** | `PacketSyncSaveData` | — | **6** | **C 独有，id 重排**（PLAY_TO_CLIENT）|
| **12** | `PacketSyncCapability` | — | **7** | **C 独有，id 重排**（PLAY_TO_CLIENT）|

**重排风险与结论**：

- 合并后是**单模组单通道 13 个包**，客户端/服务端同 jar → 顺序天然一致，**没有协议兼容负担**（旧 `corruption`/`hall` 客户端因 channel 名不同本来就无法连接）。
- **两个 `NetworkHelper` 不能共存**：两个 `newSimpleChannel` 会用同一个 `rotten_ruins:main` 名字，Forge 启动即抛 "Channel already registered"。必须保留 Hall 版 `NetworkHelper` 并补 5 条注册。
- C 的 5 个包是否都搬取决于功能取舍：`UpdateBiomePacket`/`CParticleSpawnPacket`/`GorePacket` 与 C 的群系/粒子/血迹系统绑定；`PacketSyncSaveData`/`PacketSyncCapability` 若 C 的 savedata/capability 迁入则**必须**保留（否则同步断链）。

### 6.3 `PacketActionRegistry`（UniversalPacket 之上的 action 分发）

| 工程 | action 键 | 注册位置 |
|---|---|---|
| H | `blood_burst`、`heart_lose_burst`、`meat_burst`、`hall:void_sword_strike` | `ClientPacketHandlers` + `VoidSwordStrikePacket` |
| C | `sync_corruption`（**无命名空间前缀**）| `PacketActionRegistry` 静态块 |

- 合并后 **5 个键无碰撞**；但命名不一致：Hall 的 `void_sword_strike` 带 `hall:` 前缀、其余不带；C 的 `sync_corruption` 不带。建议统一为 `rotten_ruins:<name>`，并保留旧键做 **1 个版本的兼容 alias**（可选）。
- `UniversalPacket`/`UniversalPacketData` 两代实现差异较大（token 0.19），迁移 C 的 action 时按 H 的数据体契约重写。

---

## 7. 配置 / 命令 / 音效 / creative tab / lang / 数据命名空间

### 7.1 Config（文件名不冲突，但需合并注册）

两边都用 **Cloth Config `AutoConfig`**（`JanksonConfigSerializer`），无 `ModConfigSpec`。

| 工程 | `@Config(name=...)` | 合并后路径 | 建议 |
|---|---|---|---|
| H | `<modid>/SplendidingConfig` | `config/rotten_ruins/SplendidingConfig.json5` | 保留（含 configVersion 迁移逻辑）|
| H | `<modid>/MeteorConfig` | `config/rotten_ruins/MeteorConfig.json5` | 保留 |
| C | `<modid>/EntitySpawnConfig` | `config/rotten_ruins/EntitySpawnConfig.json5` | 迁入（生成控制）|
| C | `<modid>/AssimilationConfig` | `config/rotten_ruins/AssimilationConfig.json5` | 迁入（同化系统）|
| C | `<modid>/NodeConfig` | `config/rotten_ruins/NodeConfig.json5` | 随节点系统决定 |
| C | `<modid>/FleshbedRecipe` | `config/rotten_ruins/FleshbedRecipe.json5` | 迁入（fleshbed 合成）|
| C | `<modid>/RrsCommon` | `config/rotten_ruins/RrsCommon.json5` | 迁入（演化/难度初值）|
| C | `<modid>/ShaderConfig` | — | **评估后舍弃**：只有 `use_shader` 一个开关，服务旧 glint/outline 实现 |
| C | `<modid>/TargetWhitelist` | — | **评估后舍弃**：索敌白名单，合并后索敌改走 infcore 阵营/感染类型（用户已定）|

- **9 个配置文件名两两不冲突**；但 `ConfigHelper.registry()` 只有一个入口，必须把两边 `AutoConfig.register(...)` 合并到一份（C 版 `ConfigHelper` 只有 60 行，H 版 209 行且带迁移逻辑，**取 H 版 + 加 C 的类**）。
- **旧配置不会自动迁移**：`config/hall/*` 与 `config/corruption/*` 都不会被新路径读取；建议参考 Hall 已有的 configVersion 迁移先例，一次性把两边旧文件复制到 `config/rotten_ruins/`。

### 7.2 命令（改名后根节点硬撞）

| 根 literal | 来源 | 子命令 | 合并建议 |
|---|---|---|---|
| `hall` | H `ThreatCommand`（`/hall threat get/set/add/reset`）| 4 | 改 `rotten_ruins` 根 |
| `corruption` | C `RrsCommands`（`/corruption query / set value / set phase / add / remove`）| 5 | **与上面撞成同一根** → 并入 `rotten_ruins` |
| `anomaly` | H `AnomalyCommand` | add/set/get/clear/trigger/adapt | 可保留裸根或改为子命令 |
| `spread` | H `SpreadCommand` | — | 同上 |
| `collapse` | H `CollapseCommand` | spawn/types | 同上 |
| `blackhole` | H `BlackHoleCommand` | spawn | 同上 |
| `crimsonvow` | H `CrimsonVowCommand` | field/charge/ready | 同上 |
| `meteorevent` | H `MeteorEventCommand` | — | 同上 |
| `node` | C `NodeCommands`（`/node create/delete/list/info/modify`）| 5 | 太通用的裸根名，建议挂成 `/rotten_ruins node` |

- **硬碰撞 1 处**（`hall`→`rotten_ruins` 与 `corruption`→`rotten_ruins`），必须合并成一个根的命令树；其余 6 个裸根无内部冲突（但 `spread`/`node` 这类通用词有和其它模组撞名的外部风险）。
- 语言键：C 的 `command.corruption.node.*` 共 **14 键**需改 `command.rotten_ruins.node.*`；H 的 7 个命令无 lang 键（消息是硬编码中文，可顺手迁到 lang，属可选优化）。

### 7.3 音效与 subtitle 键

| 项 | Hall | corruption | 合并结果 |
|---|---|---|---|
| `SoundEvent` 注册数 | 2（`biome_music`、`rip_body`；源码中 `example_sound` 是注释掉的模板）| 10 | **11 个**（`rip_body` 去重）|
| `sounds.json` 条目 | 2 | 10 | **11 个**，需并集合并 |
| 撞键 | `rip_body` | `rip_body` | 只保留 1 个；ogg 字节相同（41,398 B，§2.2）|
| subtitle 键 | `subtitles.hall.rip_body` | `subtitles.corruption.rip_body` | 合并为 `subtitles.rotten_ruins.rip_body`，**两边文案相同**（EN "Body rips apart"），无内容冲突 |
| 音效文件 | 5 个 ogg（2 个被引用 + 3 个中文名垃圾）| 10 个 ogg（全部被引用）| 13 个被引用 + 不去搬垃圾 |

- `sounds.json` 的 `name` 字段全部是 `<modid>:<sound>`，随 namespace 改名即可；C 的 4 个 subtitle 键（`attacker_hurt`/`attacker_living`/`body_hurt_type*`/`degenerate_call_type1`/`overload`）需改名并补 lang。

### 7.4 粒子（3 个注册 ID 硬碰撞 + 3 个 json 撞名）

| 粒子 id | Hall 注册 | corruption 注册 | 冲突 |
|---|---|---|---|
| `blood` | `SimpleParticleType(false)`，单帧 sprite，贴图 `blood.png`（=C 的 `bloody.png`）| `SimpleParticleType(true)`，`spriteSet` 多帧，贴图 `blood.png` | **硬碰撞**：注册语义不同 |
| `meat` | `SimpleParticleType(false)`，贴图 216 B | `SimpleParticleType(false)`，贴图 374 B | **硬碰撞**：同名不同贴图 |
| `explosion_bloody` | `SimpleParticleType(true)`，16 帧 0..15 | `SimpleParticleType(true)`，16 帧 0..15 | **硬碰撞**：json 参数不同，16 张帧图字节相同 |
| `heart_lose` / `ulcerated_meat` | Hall 独有 | — | 保留 |
| `bloody` / `blood_fog` / `blood_splat` / `blood_splash` / `degenerate_die_meat` / `degenerate_meat` | — | C 独有 6 个 | 迁入（注意 `bloody` 的贴图与 H 的 `blood` 同图）|

- **合并策略（建议）**：3 个同名粒子保留 Hall 实现，把 C 的差异能力以**独立 id** 迁入（例如 C 的 spriteSet 版 `blood` 改名为 `corruption_blood`/`splat_blood` 之类），而不是覆盖；最终粒子 = H 5 + C 6 = **11 个注册项 / 11 个 json**。
- 客户端 provider 注册（`RegisterParticleProvidersEvent`）两边各有一套：H 在 `ClientModEventHandler#onRegisterParticleProviders`（Hall 侧），C 在 `RegisterParticles` 类上的 `@Mod.EventBusSubscriber`（C 直接写在注册器里）——合并后 provider 注册要收口到一处，否则同一 `ParticleType` 被注册两次会抛异常。

### 7.5 Creative Tab

| 项 | Hall | corruption | 合并建议 |
|---|---|---|---|
| 注册 id | `hall_tab` | `corruption_table` | 改名后 `rotten_ruins_tab` / `rotten_ruins_table`，**id 不硬撞** |
| 标题键 | `itemGroup.<ns>.main` | `itemGroup.<ns>.main` | **撞键**：显示名 EN "Rotten Ruins: Splendiding" vs "Corruption Plague"（zh 亦不同），必须拍板一个 |
| 结构 | `SectionedCreativeModeTab` + 4 分区 + 客户端 `CreativeModeInventoryScreenMixin` 渲染色带 | 普通单列表 tab（约 60 个显式物品 + 自动收集本命名空间物品）| **保留 Hall 的分区 tab，删除 C 的 tab**；C 物品并入 `blocks`/`creatures`/`materials`/`gear` 分区（Hall 的 `misc` 分区已自动收集本 namespace 全部物品，兜底不会漏）|
| lang 键 | 6 个（main + 4 section + misc）| 1 个 | 保留 Hall 6 个 |

### 7.6 lang 键合并

| 项 | Hall | corruption | 合并后 |
|---|---|---|---|
| `en_us.json` 键数 | 138 | 147 | **283** |
| `zh_cn.json` 键数 | 138 | 173 | **309** |
| 归一化后撞键（`{hall,corruption}`→`<ns>` 后相同）| — | — | **2 个**：`itemGroup.<ns>.main`、`subtitles.<ns>.rip_body` |
| 类别完整性 | `item`56 / `block`34 / `entity`15 / `effect`4 / `attribute`5 / `biome`1 / `dimension`1 / `itemGroup`6 / `subtitles`1 / `hall.*`15 | `item`57 / `block`19 / `entity`21 / `effect`4 / `itemGroup`1 / `subtitles`4 / `advancements`10 / `command`14 / `msg`12 / `tooltip`5 | 类别并集 15 类 |
| 异常键 | 15 个 `hall.difficulty.*`/`hall.gui.*`/`hall.tooltip.*`（非标准前缀）| 3 个畸形键 `item.syringe.{already_corrupted,empty,no_target}`（应为 tooltip 类键，会显示原始键名）| 迁移时一并规范化 |
| zh/en 不对称 | 对称（138/138）| **不对称**：zh 比 en 多 26 键（主要是 5 个 KING 物品名等，EN 缺失 → 英文环境显示原始键）| 若保留对应内容，需补 EN |

- 迁移动作：① 全局替换键中的 `.hall.`/`.corruption.` 段为 `.rotten_ruins.`；② 合并两份 json 为一份（283/309 键）；③ 处理 2 个撞键；④ 补 C 的 26 个 zh-only 键的 EN 翻译；⑤ 修正 3 个畸形键。

### 7.7 数据包命名空间与 datagen 输出撞名（**P0**）

**命名空间目录现状**：

| 目录 | 文件数 | 说明 |
|---|---|---|
| H `src/generated/resources/data/hall/**` | 138 | 迁移后 → `data/rotten_ruins/**` |
| H `src/generated/resources/data/minecraft/**`（tags）| 49 | 迁移后与 C 的 2 个 tag 合并 |
| H `src/main/resources/data/forge/tags/entity_types/bosses.json` | 1 | 保留（可把 C 的 boss 加进去）|
| H `src/generated/resources/data/splendiding/**`（advancements/forge/loot_tables/recipes）| **0** | **空目录残骸**（上一次改为 `hall` 前的遗留）；Git 不跟踪空目录，迁移时直接不复制 |
| H `src/generated/resources/data/godsick_neo/infcore_evolution/**` | **0** | 同上，空目录 |
| C `src/generated/resources/data/corruption/**` | 55 | 迁移后 → `data/rotten_ruins/**` |
| C `src/generated/resources/data/minecraft/**`（tags）| 2 | 与 H 的 minecraft tags 合并（1 个撞：`mineable/pickaxe`；1 个 C 独有：`needs_stone_tool`）|

**datagen 输出撞名清单**（两套 `DataGenEvent` 若都保留，GatherData 后写覆盖先写，**不报错**）：

| 输出文件（迁移后） | Hall provider | corruption provider | 后果 |
|---|---|---|---|
| `assets/rotten_ruins/lang/en_us.json` | `LangDataEN` | `LangDataEN` | 一份被覆盖，约 140~150 键静默丢失 |
| `assets/rotten_ruins/lang/zh_cn.json` | `LangDataCN` | `LangDataCN` | 同上 |
| `assets/rotten_ruins/sounds.json` | `SoundData` | `SoundData` | 后写覆盖，另一个的 sound 键消失 |
| `assets/rotten_ruins/particles/blood.json` | `ParticleData` | `ParticleData` | 后写覆盖 |
| `assets/rotten_ruins/particles/meat.json` | `ParticleData` | `ParticleData` | 后写覆盖（贴图/参数不同）|
| `assets/rotten_ruins/particles/explosion_bloody.json` | `ParticleData` | `ParticleData` | 后写覆盖 |
| `data/minecraft/tags/blocks/mineable/pickaxe.json` | `BlockTagData` | `BlockTagData` | 后写覆盖，13 值或 2 值丢失其一 |

> 其余输出路径因注册 ID 不同而**不撞**（模型/方块状态/配方/战利品/进度/世界生成）。但两套 provider 都注册 `GatherDataEvent` 会在同一次 `runData` 里重复跑，**必须合并成一个 `DataGenEvent`（15 + 4 个独有 provider）**；`lang`/`sounds`/`particles`/`tag` 这 4 类输出必须是**唯一 provider**。

**建议迁移顺序**（与 analysis-hall.md §6.5 一致并补充）：`删 src/generated/resources + .cache` → 合并 datagen provider（先合并上面 7 个撞名输出）→ 全局 namespace 替换 → `runData` 重生成 → `git diff` 校验。

### 7.8 跨工程资源引用（唯一一处硬引用）

| 位置 | 内容 | 处理 |
|---|---|---|
| `H assets/hall/models/item/base_tool.json` L6 | `"layer0": "corruption:item/crude_sword"` | **必须改成 `rotten_ruins:item/crude_sword`**，并把 `C assets/corruption/textures/item/crude_sword.png` 一起迁入（否则紫黑缺失贴图）。这是全资源包里**唯一**的跨工程 namespace 引用（反向 C→hall 为 0 处）|

**命名空间字符串迁移量**（全局替换时只看 `<ns>:` 前缀，不要替换裸词）：

| 范围 | `hall:` | `corruption:` | 处理方式 |
|---|---|---|---|
| H **main** 手写资源 | **91 处 / 49 文件** | 1 处 / 1 文件（`base_tool.json`）| 脚本替换 + 抽查（**需人工处理**）|
| H **generated** 资源 | 1,099 处 / 357 文件 | — | **删除后 runData 重生成**（由 `MODID` 自动变新 namespace）|
| C **main** 手写资源 | — | **28 处 / 18 文件** | 脚本替换 + 抽查（**需人工处理**）|
| C **generated** 资源 | — | 548 处 / 204 文件 | 删除后 runData 重生成 |
| H java | 130 处 / 48 文件 | — | 全局替换 + 人工核对（含 `splendiding:` 6 处 / 4 文件的历史残留）|
| C java | — | 16 处 / 4 文件（另有无冒号的裸串 `new ResourceLocation("corruption", ...)`，约 8 处）| 全局替换 + 人工核对 |
| lang 键（generated，来自 LangData） | 138+138 键 | 144+170 键 | 由 `LangData` 代码里的 `MODID` 自动生成，不需手改 json |

- 小结：**需要人工/脚本替换的只有 H main 49 文件 + C main 18 文件 + java 约 52 文件**（外加 datagen 源码的 MODID 引用，随全局替换覆盖）；其余 generated 产物一律"删了重生成"，不要手工改（analysis-hall.md §6.5 的迁移顺序已含此步）。
- **注意**：Hall 的 `assets/hall/shaders/core/corruption.json` 里 program 名是 `hall:corruption`、`models/item/{crimson_vow,silent_daylight,void_sword}.json` 里有自定义字段 `"corruption": true/false`——这些是**着色器管线名/模型自定义字段**，不是 namespace 引用，**不要**被"corruption→rotten_ruins"的全局替换误伤（尤其不要替换裸词 `corruption`）。

### 7.9 其它根文件 / 构建项

| 项 | Hall | corruption | 合并建议 |
|---|---|---|---|
| `pack.mcmeta` | pack_format 15，description "hall resources" | pack_format 15，description "corruption resources" | 重写一份（`rotten_ruins resources`）|
| `META-INF/mods.toml` | 6 条 mandatory 依赖（含 infcore/curios）+ `[[mixins]]` | 4 条依赖（forge/minecraft/cloth_config/geckolib）| 以 Hall 为基准（H 是超集），只改 modid/version/描述；`[[mixins]]` 指向单份 `rotten_ruins.mixins.json` |
| `META-INF/accesstransformer.cfg` | 有（1,914 B）| **无** | 保留 H 的；C 的访问全部走 accessor mixin，不依赖 AT |
| logo | `hall.png`（135,668 B）| 无（mods.toml 里 logoFile 注释掉）| 重命名 `rotten_ruins.png`，决定是否挂 `logoFile` |
| `assets/minecraft/atlases/blocks.json` | 有（把 `shader/` 目录加入方块图集）| 无 | 保留 H 的 |
| `data/forge/tags/entity_types/bosses.json` | 有 | 无 | 保留，可补 C 的 boss |
| `thanks.md` | 无 | 1,368 B | 可选保留（放 docs/ 或 credits）|
| 构建脚本 | `build.gradle` 有 infcore（implementation/jarJar）、curios、AT、parchment=official | 无 infcore/curios；geckolib 走 curseMaven；parchment mappings | 统一用 H 的构建脚本；迁移 C 代码时注意 mapping 差异（编译期符号一致，无功能影响）|

---

## 8. 结论清单（四类）

### 8.1 汇总表

| 类别 | 资源文件 | Java 类 | 动作摘要 |
|---|---|---|---|
| **A 不用搬（完全重复）** | **20 个 / 54,049 B**（+12 个归一化等价 = 32 个 / 71,591 B）| **24 个** | corruption 侧直接不复制，保留 Hall 一份 |
| **B 搬 Hall 版（corruption 同源旧版）** | 与 A 共用"42 撞名对中 32 对去重"的结论（A 20 + CRLF/命名空间等价 12，不另计文件数）；另需以 Hall 为模板重写 `mods.toml`/`pack.mcmeta` | **36 个**（12 高相似 + 9 中度 + 15 挪位/改名牌）| 渲染/glint/mixin/geo 技术底座全部用 Hall；corruption 侧不移植 |
| **C 搬 corruption 版（Hall 没有）** | **428 个**（461 独占 − 11 垃圾 − 20 个 KING 相关 − 2 个非内容文件）| **约 160 个**（189 独占 − 15 对重复侧 − 约 9 个 AI/brain − 5 个 KING − 2 个死 mixin/主类）| 内容迁入 + namespace 改名 + 接 Hall 框架（infcore 索敌、`GeoBaseRender`、`AbstractHallEntity`）|
| **D 必须人工合并** | 42 撞名对（其中 **10 对需拍板**）+ datagen 7 类输出 + 1 处跨引用 | **34 个**大幅分叉类 + 13 包网络 + 1 份 mixin config + 9 个 config 类 | 见 §4~§7 逐项 |

### 8.2 A 类：不用搬（完全重复）清单

- **资源**：`textures/particle/explosion_bloody{0..15}.png`（16）、`shaders/core/{held_item_outline.fsh, lightning_outline.fsh, lightning_outline.vsh}`（3）、`sounds/rip_body.ogg`（1）。
- **等效去重（改名/换行后相同）**：shader json 5 个 + `particles/meat.json`、shader 源文件 6 个（CRLF-only）。
- **Java**：§4.2 的 24 个类（`AbstractItemRenderHelper`、`CDRenderHelper`、`HeldItemRuleManager`、`GameRendererAccessor`、`PacketSyncKeyframe`、`ClientRenderRegistry` 等）。

### 8.3 B 类：搬 Hall 版（corruption 同源旧版，C 侧弃用）

- **资源**：14 个同名 shader 文件（3 个字节级相同 + 6 个 CRLF-only + 5 个命名空间-only）；`mods.toml`/`pack.mcmeta` 以 Hall 为模板重写。
- **Java**：
  - 高相似 12 个（§4.3）：`GameRendererMixin`、`GameRendererOculusMixin`、`LevelRendererEmbeddiumMixin`、`HeldItemOutlineSettings`、`GlintEffectProfile`、`EffectSyncPacket`、`GeoBaseModel` 等；
  - 中度 9 个（§4.4）：`ItemGlintMixinPlugin`（取 Hall+补 2 分支）、`AccessorRenderStateShard`（取 Hall，accessor 前缀 `splendiding$` 需改名）、`HeldItemOutlineCompat`、`ModEventHandler` 等；
  - 挪位/改名牌 15 个（§4.6）：`AbstractHallEntity`、`HallEntityManager`、`BloodParticle`、`MeatParticle`、`ExplosionBloodyParticle`、`HallBiomeProcessor`、`SplendidingShaders`、`HallSpreadEventHandler` 等。
- **原则**：corruption 的 glint/outline/geo/mixin 是**重构前旧实现**；Hall 已拆分/重写，**不得反向覆盖**。

### 8.4 C 类：搬 corruption 版（Hall 没有，需迁入）

**资源（按类别，去垃圾/去 KING 后约 428 个）**：

| 类别 | 数量（去垃圾/去 KING 后） | 备注 |
|---|---|---|
| textures（block/item/entity/gui/mob_effect）| **159** | 原 180：去 11 个垃圾 + 10 个 king 贴图 |
| models（item/block）| **119** | 原 124：去 5 个 king 方块模型 |
| geo / animations | 29 + 28 | geckolib 模型与动画，**比 Hall 多得多**（Hall 16/12）|
| blockstates | **21** | 原 26：去 5 个 `king_*` |
| loot_tables / advancements / recipes | 22 + 17 + 12 | |
| sounds（.ogg）/ particles | 9 + 6 | `rip_body` 为重复（A 类），实为 9 个新音效 |
| worldgen / shaders / tags | 4 + 1 + 1 | shader 独有 1（其余 14 个撞名）|
| **合计** | **428** | 不含 `thanks.md` 与 `corruption.mixins.json`；字节约 1.60 MB |

**Java（约 160 个）**：实体 57（去掉 ai/brain 约 48）、物品 18、方块 9、粒子/效果/投射物 15、管理器/数据 11（**D 类，需与 Hall swarm 拍板**）、网络包 5、mixin 3（`MixinBiomeManager` 视决策 + `MixinLivingEntity` + `AccessorLivingEntity`）、命令 3（D 类）、config 5、datagen 4（D 类）、事件 8（D 类）、API 5、客户端渲染 11、utils 12（D 类）、JEI 1。

### 8.5 D 类：必须人工合并（逐项）

1. **42 对撞名资源**（§2.4）：32 对直接去重/改名；**10 对拍板**（lang×2、sounds.json、particles×2、tag、mods.toml、pack.mcmeta、meat.png、img.png）。
2. **datagen 输出撞名 7 类 + 两套 DataGenEvent**（§7.7）：合并为单 provider / 单事件。
3. **Mixin config 合并**（§5.4）：单份 `rotten_ruins.mixins.json` + 19 个类；禁止两份 config 并存。
4. **网络单通道 13 包**（§6.2）：保留 Hall `NetworkHelper`，5 个 C 包 id 重排；两个 `NetworkHelper` 不能共存。
5. **粒子 3 个注册 ID 硬碰撞**（§7.4）+ 3 个 json 撞名；provider 注册收口一处。
6. **音效 `rip_body` 撞键**（§7.3）+ sounds.json 并集。
7. **Creative tab 标题键撞名**（§7.5）：保留 Hall 分区 tab，删除 C 的 tab。
8. **命令根 `hall`/`corruption` 撞成 `rotten_ruins`**（§7.2）：合并单命令树，`node` 建议挂根下。
9. **lang 合并 283/309 键、2 个撞键、26 个 zh-only、3 个畸形键**（§7.6）。
10. **世界腐化/群系两套系统**（§3.3、§5.3）：Hall `swarm`+infcore vs C `BiomeManager`+`CorruptionNodeManager`，**必须二选一/分层**（建议 Hall 为主）。
11. **capability 合并**（§4.8）：`CapabilityRegistry` 并入 `IDeterrence`（若保留威慑系统）。
12. **ModUtils 20 个缺失方法**（§4.8）：随 C 内容迁入补齐/映射。
13. **跨引用 `base_tool.json`**（§7.8）：改 namespace + 迁入 `crude_sword.png`。
14. **ForgeEventHandler 去重**（§4.5）：两套监听器（命令/LivingTick/扩散）合并。
15. **34 个大幅分叉类**（§4.5）：18 个内容注册/数据生成走并集；16 个框架逻辑逐个人工合并。

### 8.6 执行顺序建议（针对"重复/撞名"这一层）

1. 先定 **modid/namespace/包名/主类名/命令根/creative tab 标题**（决定所有改名的目标值）。
2. 处理 **42 对撞名资源**：32 对直接删 C 副本；10 对按 §2.4 决策后合并（建议单独一次 commit，便于 review）。
3. **合并 datagen provider**（撞名的 7 类输出先并），删除两个 `src/generated/resources` 与 `.cache`，改 `DataGenEvent` 为单入口。
4. 合并 **mixin config**（单文件、19 类）与 **NetworkHelper**（单通道 13 包）。
5. 迁入 **C 类内容**（约 428 个资源 + 约 160 个类），同时按 §7.8 做 namespace 替换（脚本 + 抽查）。
6. 全局替换后 **runData 重生成**，`git diff` 检查：不应出现 `hall:`/`corruption:` 残留；再跑一次"§2 哈希比对"确认没有新的撞名文件（可复用本报告的方法）。
7. 最后再处理 **语义/系统级 D 类**（腐化系统、KING/王庭、inf_* vs infection_*），这部分需要产品拍板，不能靠改名解决。

---

## 9. 风险 Top（按优先级）

| # | 优先级 | 风险 | 证据/位置 | 后果 | 规避 |
|---|---|---|---|---|---|
| 1 | **P0** | datagen 输出互相覆盖 | §7.7 的 7 类输出 + 两套 `DataGenEvent` | lang/sounds/particles/tag 静默丢内容，且只有跑完游戏才发现 | 单 provider/单事件；`runData` 后校验文件内容 |
| 2 | **P0** | 两套世界腐化系统并存 | H `swarm/*` + infcore `BiomeSpreadHelper` vs C `MixinBiomeManager` + `CorruptionNodeManager` + `BiomeCorruptionProcessor` | 区块/群系反复改写、群系闪烁、TPS 掉、存档碎片化 | 二选一或明确分层（建议 Hall 为主、C 的 biome 替换关闭）|
| 3 | **P0** | 两个 NetworkHelper 同名 channel | §6.1 | 启动即崩（Channel already registered） | 只保留 Hall 版并补 5 个包注册 |
| 4 | **P1** | 42 对撞名资源未处理 | §2.4 | 缺贴图/模型/键、游戏内紫黑或报错 | 按 §8.6 第 2 步先清 |
| 5 | **P1** | mixin 双 config 并存 | §5.4 | 同一 target 多重注入、优先级/cancellable 冲突，排查极难 | 单 config + 19 类 |
| 6 | **P1** | 粒子同名不同实现 | §7.4 | 视觉不一致/客户端 provider 重复注册崩溃 | 3 个同名保留 Hall，C 差异改名迁入 |
| 7 | **P1** | capability `IDeterrence` 未注册 | §4.8 | C 的威慑/索敌系统静默失效 | `CapabilityRegistry` 并入 |
| 8 | **P1** | ModUtils 20 个方法缺失 | §4.8 | C 内容编译失败/运行 NPE（范围伤害、射线裁剪、群系写回） | 先补工具层再迁内容 |
| 9 | **P2** | lang 合并遗漏 | §7.6：283/309 键、26 个 zh-only、3 个畸形键 | 部分物品/UI 显示原始键名 | 用脚本比对键集合 + 逐键校验 |
| 10 | **P2** | 王庭/KING 双重材料 | §3.3 | 若都迁会双份材料/掉落/配方；若都不迁会缺 炉石/甲壳/纤维 | 按用户决策执行并清点缺口 |
| 11 | **P2** | 命令根 `node` 太通用 | §7.2 | 与其它模组撞名（非硬崩但冲突） | 挂到 `/rotten_ruins node` |
| 12 | **P3** | 旧 namespace 引用遗漏 | §7.8：手写资源 119 处 / 67 文件（H 49 + C 18）、java 约 150 处 / 52 文件 | 静默缺资源（紫黑块）、shader 找不到程序 | 只替换 `<ns>:` 前缀、不替换裸词；替换后跑本报告 §2 的哈希/路径比对 |

---

## 附录 A：方法与脚本口径

- **哈希/重复**：Python 遍历 `src/main/resources` + `src/generated/resources`（跳过 `.cache`），`hashlib.sha256` 逐文件；路径键 = `assets/<ns>/…`、`data/<ns>/…` 去命名空间；哈希分组 + 归一化路径配对；文本再做 BOM/CRLF/namespace 三级归一化。
- **Java**：`os.walk` 收 `*.java` 相对包根路径；行数/字节；`difflib.SequenceMatcher` 行级 + token 级（token 化前去注释、去 `package/import`、替换包根/modid/类名前缀），得到 `+/-` 行差与 ratio；`entitis→entities`、`registry/item/→registry/items/`、类名前缀归一化后做改名牌配对。
- **Mixin**：解析两份 `*.mixins.json` 的 `mixins`/`client`/`plugin`/`refmap`；从 java 文件提取 `@Mixin/@Inject/@Redirect/@Accessor/@Shadow` 注解块（括号配平取全文），归一化后求集合差。
- **注册表 ID**：三路交叉——① `lang/*.json` 键；② 资源目录（`models/item`、`blockstates`、`textures/*`、`geo`、`animations`、`particles`、`sounds.json`、`loot_tables`、`recipes`、`advancements`、`tags`、`worldgen`）；③ 注册器代码字面量（`RegisterItem/Block/Effect/Particles/SoundEventRegistry/EntityTypeRegistry/MemoryModuleTypeRegistry`）。代码字面量抽取覆盖不全（部分经辅助方法/常量注册），仅作交叉验证。
- 所有脚本只写临时目录，未在工程内新增/修改除本报告以外的任何文件；未执行 Gradle、未做 git 操作。

## 附录 B：关键证据文件索引

| 主题 | 路径 |
|---|---|
| 资源哈希/重复证据 | 本报告 §2、§3 的表格（数据源：两工程 `src/{main,generated}/resources` 全量 1,189 个文件）|
| Java 对比证据 | §4 表（79 同路径 + 15 挪位；数据源：两工程 `src/main/java` 全量 658 个文件）|
| Mixin 清单 | `H src/main/resources/hall.mixins.json`、`C src/main/resources/corruption.mixins.json` |
| 网络注册 | `H .../network/NetworkHelper.java`、`H .../network/all/PacketActionRegistry.java`、`C .../network/NetworkHelper.java` |
| 粒子/音效注册 | `H .../overworld/registry/RegisterParticles.java` / `SoundEventRegistry.java`、`C .../overworld/registry/RegisterParticles.java` / `SoundEventRegistry.java` |
| 配置 | `H .../config/ConfigHelper.java`、`C .../config/data/*.java`（7 个 `@Config`）|
| 命令 | `H .../command/*.java`（7 个）、`C .../command/{RrsCommands,NodeCommands}.java` |
| 数据生成 | `H .../datagen/DataGenEvent.java`、`C .../datagen/DataGenEvent.java` |
| 跨引用 | `H src/main/resources/assets/hall/models/item/base_tool.json` L6 |
| 空目录残骸 | `H src/generated/resources/data/{splendiding,godsick_neo}`（0 文件）|

---

*报告结束。本文只读分析，未改动任何源码；与 `analysis-hall.md` 的"同源重复嫌疑清单"（§4.3）互为量化补充：`analysis-hall.md` 给的是"看到即列"的粗清单，本报告给出逐文件哈希/逐类 diff 后的精确数量与处理建议。*
