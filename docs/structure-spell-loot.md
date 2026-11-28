# 原版结构中的法术

法术作为额外战利品加入以下原版箱子，每次命中概率时追加 1 个现有法术物品。
原版战利品表不被覆盖；不需要残页合成或额外学习步骤，取出后按现有方式装入魔法槽施法。

| 结构箱子 | 法术 | 每箱概率 |
|---|---|---|
| 猪灵堡垒：桥梁、疣猪兽棚、其他箱子 | 火球术 | 30% |
| 猪灵堡垒：藏宝箱 | 火球术 | 50% |
| 沉船：藏宝箱 | 潮汐冲击 | 30% |
| 丛林神庙：宝箱 | 荆棘禁锢 | 30% |
| 沙漠神殿：宝箱 | 疾行 | 30% |

沉船补给箱、地图箱和丛林神庙发射器不加入法术。
概率按箱子独立计算，不保证每座结构必出。
已经生成内容的箱子不会补发；已有存档中仍保留上述战利品表、尚未生成内容的箱子也适用。

规则由 `GlobalLootModifierData` 生成到
`data/rotten_ruins/loot_modifiers/spell_from_*.json`，通过
`data/forge/loot_modifiers/global_loot_modifiers.json` 加载。
`rotten_ruins:structure_spell` 修饰器的 `item` 字段指定法术，条件指定目标表和概率，
可由服务端数据包覆盖并通过 `/reload` 生效。
新增内置规则时修改生成器并运行 `gradlew copyPatchedModsForData runData --offline`，不要手改生成文件。
