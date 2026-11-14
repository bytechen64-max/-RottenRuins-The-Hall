# 腐化系生物掉落

掉落声明位于 `datagen/gen/LootTableData.ModEntityLoot`，运行 `gradlew.bat runData --offline --console=plain` 生成到 `src/generated/resources/data/rotten_ruins/loot_tables/entities/`。

当前覆盖 11 种 Host、4 种 Degenerate、4 种 Refactored。每种材料使用独立掉落池，可在同一次死亡中同时掉落。

| 类别 | 新增物品 | 无抢夺时数量／概率 |
| --- | --- | --- |
| Host | 被同化的肉 | 1–2 |
| Host | 活体肉块、同化的甲壳 | 各 0–1 |
| Host | 感染的骨头 | 0–2 |
| Degenerate | 腐化之心 | 25% 掉落 1 个 |
| Degenerate | 酸囊 | 0–1 |
| Degenerate | 酸液球 | 0–2 |
| Refactored | 聚合甲壳 | 0–2 |
| Refactored | 硬质碎片 | 1–3 |
| 长臂兽 | 长臂兽头颅 | 25% 掉落 1 个 |
| 长臂兽 | 长臂兽牙齿 | 1–2 |

Degenerate 包含 Host 的全部掉落池，Refactored 包含 Degenerate 的全部掉落池；长臂兽额外包含专属头颅与牙齿。

普通材料使用原版抢夺数量函数，按 0–1 的随机增量乘抢夺等级；心脏与头颅每级抢夺增加 5 个百分点的掉落概率，单次仍为 1 个。
掉落不额外要求玩家击杀，沿用原版死亡掉落规则。爆体使用现有 `AbstractRottenEntity.onBurstEnd` 的死亡掉落入口，不另加事件掉落，避免重复发放。

Original、Deteriorated、Linked 未在本次指定的三个类别内，暂未新增掉落声明。
