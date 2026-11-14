# 血肉庭园矿脉

`HallOreData` 声明矿脉的 ConfiguredFeature 与 PlacedFeature，`HallWorldGenData`
将其加入现有数据生成注册表；`HallBiomeData` 在血肉庭园与王庭海洋的
`UNDERGROUND_ORES` 阶段引用同一组矿脉，其他群系不添加。

两种矿脉使用原版 `minecraft:ore`，只替换 `rotten_ruins:hall_stone`，
不替换泥土、沙子或已有矿石，不修改矿石注册与掉落。

| 矿石 | 每区块尝试次数 | 矿脉大小参数 | 起点高度（三角分布） |
| --- | --- | --- | --- |
| 穹顶胚晶矿石 | 4 | 5 | Y=8～64，中心 36 |
| 穹顶云铁矿石 | 8 | 8 | Y=8～128，中心 68 |

尝试次数与大小参数不保证实际矿脉数或方块数；落点必须命中王庭石块才能生成。
高度范围控制矿脉起点，矿脉边缘可以跨出该范围。海底与陆地使用相同参数。
修改仅在新生成的区块生效，不补写已有区块。

数据生成产物位于 `src/generated/resources/data/rotten_ruins/worldgen/`。
调整参数时同步生成并提交 configured_feature、placed_feature 和两个 biome 的 JSON。
