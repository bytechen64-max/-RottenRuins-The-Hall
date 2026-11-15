# 血肉庭园区域地貌

当前新增三个群系区域，通过区块密度生成岩体，不使用 Structure 或跨区块巨大 Feature。
维度高度由 256 扩展为 768（Y=0～767），维度类型与噪声设置一致；海平面仍为 96。
普通庭园与海洋继续存在。只影响新生成区块，不改写存档；新旧生成边界和旧世界高度
变化尚未实机验证，建议使用新测试世界验收这次整体地形变更。

## 地貌定义

| 群系 ID（命名空间 rotten_ruins） | 地貌 |
| --- | --- |
| shattered_lands（破碎之地） | 192 格水平网格，每格三层静态旋转立方体，边长约 80～148 格；中心高度约 208、376、544，附加水平与竖向偏移。立方体使用不同偏航、倾斜和侧倾角，远看形成多层岛群，底部为低地海盆。 |
| folded_fields（折叠原野） | 沿斜向轴连续延伸的厚地层，约 800 格一组折面：平地、陡升、向后折返、高处平台、再落回地面。折面是有厚度的空间实体，折返处有空气间隙与悬挑，不只是提高山脊高度。 |
| pierced_continent（穿孔大陆） | 约 Y=340 的厚大陆被两层半径约 82～116 格的球形空洞切穿，交叠形成竖井、洞厅、岩桥和薄壁。空洞低于海面处沿用原有填水行为。 |

区域选择用世界种子驱动的二维 `surreal_regions` 噪声（firstOctave=-11）。
同一噪声同时写入 NoiseRouter 的 temperature 与自定义密度节点，避免群系与地貌
使用不同随机来源。温度区间依次为 [-2,-0.35]、[-0.35,-0.05]、[-0.05,0.25]，
[0.25,2] 为普通庭园；大陆值 <=0.05 仍为海洋。
公式在区域边界两侧各 0.05 做连续混合，大陆值 0.05～0.18 过渡回原海岸。
实际区域尺度随噪声与种子变化，不保证每个群系都是固定大小的方形。

`surreal_cells` 提供岛体／空洞布局的世界种子变化。几何以绝对世界坐标计算，负坐标
使用 floorDiv；不按区块随机拼装，不依赖邻块生成顺序，不保存玩家、世界或服务器对象。

## 分层与入口

- `core/worldgen/SurrealTerrain`：纯 Java 的区域混合、旋转盒体、折返厚层、球形减法。
  接受外部二维采样契约，不依赖游戏噪声、NBT 或注册表。
- `platform/minecraft/worldgen/SurrealTerrainDensity`：当前版本密度接口和数据包 codec。
  `mapAll` 遍历原地形、大陆噪声与两个 NoiseHolder，经原版 Visitor 安装世界种子。
- `platform/forge/worldgen/SurrealWorldgen`：只注册密度 codec 和岛面植被 Feature，
  从 HallMod 构造入口接入；未恢复旧王座／冠冕注册。
- `FleshDimensionData`：在原版插值和 blendDensity 之前接入密度节点，并声明五种群系
  （海洋、普通庭园和三个新增区域）的气候范围。
- `HallNoiseData`、`HallBiomeData`、`SurrealFeatureData`、`HallOreData`：数据生成声明，
  通过现有 HallWorldGenData 产出资源；不调整游戏网络协议。

## 地表、植被与矿脉

所有岩体复用当前王庭石／泥土／草方块的地表规则，不沿用旧建筑的砂岩调色板。
普通植被在最高地表生成；`content/worldgen/IslandVegetationFeature` 在新增群系
额外检查中下层可站立的草面，复用已注册 hall_tree 配置，并点缀王庭草。
每区块最多三条竖向扫描，跳过最高草面；基点留六格边距，复用树的树冠与间距读取
不越出当前区块。树干保持游戏竖直方向，地形旋转不改变玩家或树木的重力。
现有王庭树叶不设置原版 persistent 属性。

原有低层矿脉参数不变；三个新增群系额外引用高层放置规则：两种矿脉在 Y=128～672
三角分布，胚晶每区块尝试 4 次、云铁 8 次，大小仍为 5／8，只替换王庭石。
这用于覆盖高折面、大陆上层及浮岛，不会保证每座岛都含矿。

## 验证与验收

`tools/tests/SurrealTerrainTest.java` 使用独立 JDK 检查分层岛体、布局变化、折面悬挑、
内部空洞、负坐标、区域边界连续、海洋公式保留及顶部留空。附加输出目录参数可导出
规则剖面；该预览使用测试噪声，不是实际种子的游戏截图。
常规构建执行全部依赖门禁。数据生成验证 codec 可编码和资源引用能解析；编译和
剖面检查不能验证实际世界种子注入、植被效果、运行内存与生成速度。
按协作约定不启动客户端／GameTest，实际生成与视觉交由用户验收。

在庭园内定位：

```mcfunction
/locate biome rotten_ruins:shattered_lands
/locate biome rotten_ruins:folded_fields
/locate biome rotten_ruins:pierced_continent
```
