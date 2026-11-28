# 资源移植报告：corruption 简化版 → rotten_ruins

- 来源根：`corruption/src/main/resources/assets/corruption/`
- 目标根：`src/main/resources/assets/rotten_ruins/`、`src/main/resources/data/rotten_ruins/`
- 范围：只复制/生成资源（含静态 blockstate/model/loot JSON），**未改任何 .java、未运行 Gradle**。

## 1. 结果总览

| 类别 | 数量 | 目标位置 |
|---|---:|---|
| Geo 模型（复制） | 23 | `assets/rotten_ruins/geo/` |
| 动画（复制） | 23 | `assets/rotten_ruins/animations/` |
| 实体贴图（复制） | 24 | `assets/rotten_ruins/textures/entity/` |
| 方块贴图（复制） | 21 | `assets/rotten_ruins/textures/block/` |
| 物品贴图（复制） | 40 | `assets/rotten_ruins/textures/item/` |
| Blockstate（生成） | 21 | `assets/rotten_ruins/blockstates/` |
| 方块模型（生成） | 21 | `assets/rotten_ruins/models/block/` |
| 物品模型（生成，21 方块 + 40 物品） | 61 | `assets/rotten_ruins/models/item/` |
| 方块掉落表（生成） | 21 | `data/rotten_ruins/loot_tables/blocks/` |
| **合计新增** | **255** | 复制 131 + 生成 124 |

目标工程原已有同目录基线文件（geo 16 / 动画 12 / 实体贴图 23 / 方块贴图 34 / 物品贴图 54 / 物品模型 14），本次均为增量，未覆盖任何既有文件；完成后总数为 geo 39、动画 35、实体贴图 47、方块贴图 55、物品贴图 94、物品模型 75。

## 2. 重命名规则

| 源文件 | 目标文件 |
|---|---|
| `geo/infection_<host>.geo.json`（host = bat/chicken/cow/pig/sheep/skeleton/villager/wolf/zombie/bone_ruins） | `geo/corruption_<host>.geo.json` |
| `animations/infection_<host>.animation.json` | `animations/corruption_<host>.animation.json` |
| `textures/entity/infection_<host>.png` | `textures/entity/corruption_<host>.png` |
| `geo/infection_player_slim.geo.json` | `geo/corruption_player.geo.json` |
| `animations/infection_player.animation.json` | `animations/corruption_player.animation.json` |
| `textures/entity/infection_player_slim_1.png` | `textures/entity/corruption_player.png` |
| `textures/block/variable_height_crop_middle.png` | `textures/block/variable_height_crop.png` |
| `textures/item/infection_<host>_spawn_egg.png`（host 含 player） | `textures/item/corruption_<host>_spawn_egg.png` |

其余文件保持原名复制。

## 3. 文件清单

### 3.1 Geo（23）
- 原名：attacker、bearer、degenerate_cow、degenerate_pig、degenerate_villager、degenerate_zombie、groveler、humus_column、long_arm_beast、original_bug、shocker、summoning_pillar（12）
- 改名：corruption_bat、corruption_chicken、corruption_cow、corruption_pig、corruption_sheep、corruption_skeleton、corruption_villager、corruption_wolf、corruption_zombie、corruption_bone_ruins（10）+ corruption_player（1）

### 3.2 动画（23）
与 3.1 一一对应（`<同名>.animation.json`），含 corruption_player.animation.json；host 族同样改为 `corruption_*`。

### 3.3 实体贴图（24）
- 原名（13）：attacker、bearer、degenerate_cow、degenerate_pig、degenerate_villager、degenerate_zombie、groveler、humus_column、humus_column_grow、long_arm_beast、original_bug、shocker、summoning_pillar
- 改名（11）：corruption_bat、corruption_chicken、corruption_cow、corruption_pig、corruption_sheep、corruption_skeleton、corruption_villager、corruption_wolf、corruption_zombie、corruption_bone_ruins、corruption_player

### 3.4 方块贴图（21）
spread_dirt、spread_stone、infection_cobblestone_block、spread_infection_leaves、spread_infection_wood、corruption_grass、corruption_trachea、connect_block、decidual_tissue_block、fleshbed、degenerate_remains_small/medium/large、host_remains_small/medium/large、refactored_remains_small/medium/large、spiracle_block、variable_height_crop（由 variable_height_crop_middle 改名）。

### 3.5 物品贴图（40 = 19 + 21）
- 材料/工具（19，原名）：infection_bone、hard_fragments、chitin_polymer、live_meat、host_meat、host_carapace、host_tooth、degenerate_heart、long_arm_beast_head、long_arm_beast_tooth、acid_sac、acid_ball、syringe、syringe_bloody_concentrate、crude_axe、crude_hoe、crude_pickaxe、crude_shovel、crude_sword。
- 刷怪蛋（21）：原名 11 = attacker、bearer、degenerate_cow/pig/villager/zombie、groveler、humus_column、long_arm_beast、shocker、summoning_pillar；改名 10 = corruption_bat/chicken/cow/pig/player/sheep/skeleton/villager/wolf/zombie 的 `<id>_spawn_egg`。
- 注：源里本来就没有 `original_bug`、`infection_bone_ruins` 的刷怪蛋贴图（与原版 corruption 一致），本任务未生成；检测到有并行任务在补这两个蛋（见 §6.8）。

### 3.6 方块状态与模型（各 21 id）
21 个方块 id 即 3.4 清单（含 `variable_height_crop`），全部按满方块生成：
- blockstate：`{"variants":{"":{"model":"rotten_ruins:block/<id>"}}}`
- 方块模型：`{"parent":"minecraft:block/cube_all","textures":{"all":"rotten_ruins:block/<id>"}}`
- 方块物品模型：`{"parent":"rotten_ruins:block/<id>"}`

### 3.7 物品模型（40）
`{"parent":"item/generated","textures":{"layer0":"rotten_ruins:item/<id>"}}`，id 即 3.5 的 40 个物品。

### 3.8 掉落表（21）
`data/rotten_ruins/loot_tables/blocks/<id>.json`，标准自掉落（`minecraft:block` + `survives_explosion` + `rotten_ruins:<id>`）。

## 4. 跳过内容（源 5 个目录共 80 个未选文件）

| 源目录 | 跳过数 | 内容 |
|---|---:|---|
| `geo/` | 6 | acid_ball、avila、bone_spike、heavy_guard、infection_player_wide、poison_arrow |
| `animations/` | 5 | acid_ball、avila、bone_spike、heavy_guard、poison_arrow |
| `textures/block/` | 27 | 10 个时间戳命名贴图、king_stone/dirt/brick/stone_dirt/leaves、9 个 `*_side.png`、spread_infection_wood_top、variable_height_crop_bottom/top |
| `textures/entity/` | 14 | avila_closeeye/openeye、bone_spike、heavy_guard、heavy_guard_grow、infection_player_layer、infection_player_slim_2..7、infection_player_wide_1、poison_arrow |
| `textures/item/` | 28 | acid_bomb、chip_*（4）、debug_item、envy/gluttony/greed/lust/pride/sloth/wrath、vice_template、file、file_folder、img、king_*（5）、model (5)、poisoned_saber、red_dwarf_bow*（4） |

## 5. 验证结果

- 复制文件 131/131 个与源文件 SHA-256 完全一致；PNG 签名（8 字节）全部有效。
- 生成 JSON 124/124 个可被标准 JSON 解析器解析、无 BOM，并逐字段比对任务给定格式（blockstate / 方块模型 / 物品模型 / 掉落表）完全匹配。
- 生成 JSON 引用的 21 个方块贴图、40 个物品贴图在目标目录均存在（无缺贴图）。
- 复制/生成过程目标侧冲突数 0（未覆盖任何已存在文件）；`git status` 显示本任务只新增上文 255 个文件，未改 Java。
- 补充说明：PowerShell 5.1 的 `ConvertFrom-Json` 无法解析 blockstate 中合法的空 variant 键 `""`，故 JSON 校验改用 Python 完成。

## 6. 发现的问题与后续注意

1. **corruption_player 刷怪蛋为推测项**：按“源里所有 `*_spawn_egg.png` + host 族改名”规则，`infection_player_spawn_egg.png` 已改为 `corruption_player_spawn_egg.png` 并生成 item 模型。若 Java 侧不给同化玩家注册刷怪蛋，这两个资源为孤儿（可删除）；若注册名不同需同步改。
2. **player 皮肤**：`corruption_player.png` 取自 `infection_player_slim_1.png`（64×64 现代 slim 皮肤），slim_2..7、wide、layer 均按要求跳过；corruption 原版是“按玩家名在线下载皮肤”，合并版若仍用动态皮肤，此静态贴图只作兜底/默认。
3. **无 glow layer 贴图**：corruption 源实体贴图没有 `*_glow_layer.png`（Hall 侧部分实体有）。若腐化实体的渲染器启用 glow 层，需要另补贴图或关闭该层。
4. **方块模型为占位**：21 个方块全部使用 `cube_all`。实际形态更复杂——`variable_height_crop` 是多段作物（源有 bottom/middle/top 贴图与独立 blockstate）、遗骸为六向朝向、`corruption_trachea` 六向附着、`fleshbed` 是带 BlockEntity 的方块。corruption 的 generated resources 里有现成真实资源（26 个 blockstate + 27 个 block 模型，另 `fleshbed.json` 在 main resources），后续如需精确外观可从那里复制替换本占位（本任务按清单只生成静态占位）。
5. **掉落表为新增**：corruption 原本 26 个方块**完全没有方块掉落表**（源 `data/corruption` 下只有 `loot_tables/entities`），本任务按要求补齐 21 个标准自掉落；遗骸/作物等若需特殊掉落规则需后续调整。
6. **注册 id 联动**：本任务未改 Java；`CorruptionBlocks`/`CorruptionItems` 等注册由其他队友并行编写，资源能否生效取决于最终注册 id 与上表一致（尤其 `corruption_<host>` 实体/刷怪蛋与 `variable_height_crop`）。
7. **GeckoLib 版本**：目标工程使用 GeckoLib 4.8.4；corruption 的 geo/animation 为 `format_version` 1.12.0 / 1.8.0，属 GeckoLib 4 支持的格式，资源 JSON 可直接使用；Java 侧渲染 API 迁移不在本任务范围。
8. **并行改动提示（非本任务）**：本次运行期间同一资源树还有并行任务在写入——① Hall 侧把 `inf_*` 改名为 `hall_*`（约 20 个删除 + 20 个新增，如 inf_enderman→hall_enderman）；② 追加了 `original_bug_spawn_egg`、`corruption_bone_ruins_spawn_egg` 两个蛋的贴图/模型（源缺失、本任务清单未包含）。这些 `D`/`??` 条目不属于本任务；其中 ② 与刷怪蛋清单存在潜在归属重叠，建议主控核对由哪条任务线负责，避免重复生成或命名不一致。
9. 语言键（zh/en）与声音等不在本任务范围，分别由其他队友处理。
