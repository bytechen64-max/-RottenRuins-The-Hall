# 腐朽之疫：王庭【Rotten Ruins：the Hall】


## 概述

- 腐朽之疫：王庭，是基于系'腐朽之疫‘创造的第二个作品

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
