# 腐朽之疫：王庭【Rotten Ruins：the Hall】


## 概述

- 腐朽之疫：王庭，是基于系'腐朽之疫‘创造的第二个作品

## 高势区密度

游戏目录下的 `config/rotten_ruins/SplendidingConfig.json5` 中，
`highPotentialChunkRarity` 控制高势区的稀有度，默认 `64`，表示平均每 64 个区块出现一个。
设为 `32` 更密集，`128` 更稀疏；`0` 关闭，`1` 表示全部区块。
旧配置中没有此项时使用默认值，也可手动添加 `highPotentialChunkRarity: 64`。

修改文件后重启游戏或服务端生效；多人游戏以服务端配置为准。
高势区由种子、维度和区块坐标即时计算，修改密度会同时改变已有区块和新生成区块的判定。
管理员可用 `/rotten_ruins high_potential [chunkX chunkZ]` 查询当前维度的区块。

简易场强计（`rotten_ruins:simple_field_meter`）位于创造栏的“武器与工具”分区。
携带时每半秒刷新纹理：所在区块为高势区时显示高势能；否则在水平区块坐标半径
5（含边界）的圆形范围内检测到高势区时显示中势能；其余显示低势能。
读数由服务端计算并同步，遵循当前的高势区密度配置。

## 接地桩与源导线

滤波器调参与储魔瓶灌注见 [源荷滤波器与集荷器](docs/source-machines.md)。

- 接地桩（`rotten_ruins:grounding_stake`）使用提供的模型，可以向六个方向放置。
  模型底面必须贴在有坚固支撑的方块面上；接地桩不能支撑另一根接地桩，拆除支撑后会掉落。
- 两个源荷砂无序合成一个源导线（`rotten_ruins:source_wire`），两者均在创造栏“魔法”分区。
  导线像红石粉一样铺在地面，支持一格台阶，使用原版灰度红石纹理染成蓝色。
- 在高势区放一根接地桩，在任意非高势区放另一根，用导线连接两根桩的相邻位置。
  同一网络中同时存在高、低势接地桩时导线激活，显示亮蓝自发光并提供随强度变化的方块光照。
- 从与高势桩相邻的导线算起，沿线最短路径每 4 格衰减 1 档，最高 15、最低 1；多个高势桩取最近者。
  切断线路或拆除一端接地桩后熄灭，网络约每秒校验一次，只计算已加载的区块。
  源导线的势能独立于原版红石信号。

网络测试：`gradlew copyPatchedMods runGameTestServer --offline`；
独立算法测试可编译 `PotentialCircuit.java` 与 `tools/tests/PotentialCircuitTest.java` 后运行 `PotentialCircuitTest`。

## 开发工具

- [`tools/shader-preview`](tools/shader-preview/README.md)：不启动游戏的着色器离屏预览 / 编译检查（物品描边、法阵、光柱、魔法星图…）
- [`tools/gui-preview`](tools/gui-preview/README.md)：不启动游戏的 GUI 组合预览（tooltip、坍缩使徒血条、GUI 特效、创造栏隔断、难度选择界面、魔法轮盘/装载界面），用真实 core shader + MC 位图字体按 GUI 坐标渲染
- 设计文档：[魔法轮盘施法界面 + 魔法装载界面设计规格](docs/spell-wheel-design.md)（含 640×400 坐标表、视觉 token、状态机、数据模型、实机落地映射）

## 魔法施法轮盘

设计稿已实装进游戏（P0，Forge 1.20.1 客户端 + 服务端权威校验）：

- **按住 `R`**：打开施法轮盘。鼠标方向选择方位，**松手释放**；右键 / `Esc` 取消。
  - `IMMEDIATELY`（如 Fireball）：松手立刻释放；
  - `CHARGE`（如 Spear of Judgment）：选中停留 120ms 开始蓄力，松手按蓄力比例结算；
  - `KEEP`（如 Suppression Field）：选中停留 250ms 起手并收拢为底部维持条，松手结束维持。
- 按住 `R` 时原版「交换副手」会被吞掉，不会误换物品；键位可在「控制」里改。
- **`V`**：打开魔法装载界面，把魔法池里的主动法术拖到 8 个方位并保存；被动法术不可绑定。
- **`M`**：原有魔法星图（选中的魔法池槽位会与轮盘联动）。
- 轮盘方位映射存在玩家魔法池 NBT（`HallMagicWheelSlots`，3 页 × 8 位），
  施法与配置都先经服务端重新校验（槽位/主动法术/冷却/蓝量/法杖），客户端只做申请与表现。
- 界面文案全部走 lang key（`rotten_ruins.wheel.*` / `rotten_ruins.gui.wheel.*`），
  en_us / zh_cn 由 datagen 生成：改完 `LangDataEN` / `LangDataCN` 后跑
  `gradlew runData --offline`，**不要手改 `src/generated/`**。
- 4 支 GUI 着色器（`wheel_slice` / `wheel_aura` / `wheel_core` / `sdf_ui`）在
  `assets/rotten_ruins/shaders/core/`；SDF 逐物理像素抗锯齿，任意 GUI 缩放下圆环/扇区都平滑。

## 背景
- 当你在夜间游荡时，发着诡灭光芒的陨石划破天际，那不只是光，是某种活着的，不可言说的某种��。当它触地的瞬间，冲击波扩散开来，碰撞的正中心，一个完美符合几何学的，伴随着刺眼白光的二十面体逐渐膨胀扩散，任何处于其中的生物都将被苍白之火绞杀吞噬，至灰飞烟灭，不..变成了另一种存在...
