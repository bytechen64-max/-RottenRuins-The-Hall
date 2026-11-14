# 血肉庭园超现实巨构

首批为 `rotten_ruins:empty_throne`（无人之座）和 `rotten_ruins:sunken_crown`
（淹没的冠冕），通过原版 Structure / StructureSet 系统生成和定位。
不使用跨区块写入的巨大 Feature，不新增方块、实体、掉落或网络消息。

## 内容与生成条件

| 结构 | 造型 | 自然生成条件 | 随机分布网格 |
| --- | --- | --- | --- |
| 无人之座 | 约 81×97 格主体，基座以上最高 128 格；三联尖拱椅背、收窄扶壁、曲线扶手、连续台阶、座下空腔、空座面与一株树 | 血肉庭园群系；中心高于海面 8 格；四角为陆地且相对中心高差不超过 22 格；完整高度不得越界 | spacing=64、separation=24（区块），salt=193847561 |
| 淹没的冠冕 | 外径约 141 格；镂空水下环带、12 个交替高低且向内弯曲的冠尖位置、两处断裂；断面点缀已有云铁与胚晶矿石；中央保留原海水 | 王庭海洋群系；九点采样水深至少 16 格，海底不低于基础底部 | spacing=72、separation=28（区块），salt=817263549 |

第二版材质统一为现有浅蓝紫砂岩系列：主体使用王庭烬痕平滑砂岩，边缘使用切制砂岩，
拱框与冠尖边线使用雕纹砂岩。建筑不再以暗红色王庭石为主体，也不再使用周期横条与
散点装饰；层次由尖拱、凹槽、扶壁收分和弯曲冠尖形成。方块贴图本身不改动。
预览石材颜色从项目真实贴图采样，预览仍不代表游戏光照与完整纹理效果。
已生成方块不会自动重建，验收应选择尚未生成的新区域或新的测试世界；不混用旧结构的未完成边缘区块。

网格每格最多给一个候选点，仍需通过群系、地形与高度检查，不代表每 1024/1152 格
必定遇到一个结构。二者只随当前维度使用的专属群系自然生成；没有向主世界群系注入。
四个水平朝向由世界种子确定，所有分片共享同一个放置参数。

王座基准为中心地表下 12 格，基础额外下沉 24 格；海冠基准为海面下 36 格，
基础额外下沉 32 格，最高冠尖高出海面约 48 格。
不启用 terrain adaptation，不改变原地形噪声或海平面，不强行整平山地。
结构在 `TOP_LAYER_MODIFICATION` 阶段落成，晚于矿脉和植被，避免普通植被随后
长到空座面上；仅结构本体和明确空腔覆盖现有方块，周围环境保留。
王座树叶直接使用王庭树叶的默认状态。`HallWoodBlocks.Leaves` 继承普通 `Block`，
没有原版树叶的 `persistent` / `distance` 属性，也没有原版树叶的距离腐烂逻辑。

## 分层与生命周期

- `core/worldgen/GardenMonument`：纯 Java 几何、语义材质、旋转与不重叠的区块分片。
  `KEEP` 保留世界；`AIR` 仅用于明确的座面净空、入口和雕刻孔洞。
- `content/worldgen/GardenMonumentStructure`：地形候选检查、Structure 类型与分片装配。
- `content/worldgen/GardenMonumentPiece`：将语义材质映射到已有王庭方块，只读写当前分片与
  原版传入裁剪框的交集，保留已有容器等 block entity。
- `platform/forge/worldgen/GardenWorldgen`：注册结构类型与结构分片类型，由 `HallMod` 接入。
- `platform/minecraft/worldgen/GardenPieceCodec`：分片 NBT 载荷的当前版本编码。
  原版保存分片 ID 与 bounding box；载荷保存种类、中心、基准高度和朝向，保证未生成的
  邻块在存档重载后继续生成同一座结构。不保存玩家或世界引用。
- `datagen/gen/GardenStructureData`：结构、合法群系与稀疏分布声明，经 `HallWorldGenData`
  输出 `worldgen/structure` 和 `worldgen/structure_set` JSON。

## 检查与验收

`tools/tests/GardenMonumentTest.java` 不加载 Minecraft，检查前方台阶连续性与双格净空、
树与土壤接触、四朝向一致性、正负坐标下分片无重叠无遗漏、整体方块连通性及冠冕断面矿石。
可用 `javac -d build/monument-geometry src/main/java/org/bytechen/rottenruins/core/worldgen/GardenMonument.java tools/tests/GardenMonumentTest.java`
编译，再用 `java -cp build/monument-geometry GardenMonumentTest` 执行。
附加目录参数会导出离线几何预览所需的 voxel 数据，不是游戏截图。

常规离线构建执行依赖检查与编译打包。未启动客户端或 GameTest；自然生成概率、
地形衔接、跨区块重载和实际视觉仍由用户实机验收。结构仅在新生成区块出现。

用户验收可在血肉庭园执行：

```mcfunction
/locate structure rotten_ruins:empty_throne
/locate structure rotten_ruins:sunken_crown
```

`/place structure` 同样受适宜地形与高度条件限制，不建议在已有建筑处测试。
