# 异常攻击与追踪弹体

## 边界与入口

- `core/anomaly/AnomalyState`：纯 Java 的逐类型进度、衰减时钟和逐攻击者节流。状态归属于受害玩家，不保存实体或世界。
- `api/anomaly/AnomalyAttack`：类型、累积量、最小命中间隔声明；`AnomalyDamageSource` 在保留原伤害类型、位置与归因的同时携带异常声明及攻击者 UUID。
- `content/anomaly/HallAnomalyAttacks`：具体 hall 生物与技能的默认声明。
- `gameplay/anomaly/AnomalyEngine`：命中求值、持续效果、伤害放大与满层效果编排。
- `gameplay/anomaly/AnomalyRuntime`：平台服务契约；由 HallMod 构造时安装 `ForgeAnomalyServices`。服务在调用时解析注册表／能力，不缓存玩家、世界或服务器。
- `event/impl/AnomalyEventHandler` 仅转发 Forge 事件。已有能力适配器仍位于历史目录 `overworld/registry/capability/anomaly`，负责 NBT、触发事件和适应 Buff，不代表整个历史模块已完成迁移。

普通伤害在 `LivingDamageEvent` 的 LOWEST 阶段进入玩家能力持有的 `AnomalyDamageInbox`，
在 PlayerTick END 才读取事件的最终取消标记与最终正伤害并累积。这样保留原始扣血、归因与通知顺序，
也能排除后续监听者取消、完全格挡或吸收完的伤害。玩家 tick 外的攻击最迟在下一次玩家 tick 结算。
收件箱没有全局登记表；清空待处理事件后再执行玩法，不复制到重生玩家或存档。

VitalProbe 成功改血绕过伤害事件，由 `BCCoreCompat` 在成功写血后显式通知同一玩法入口。
回退到原版 hurt 时只走事件收件箱。普通攻击同一攻击者／受害者／类型在同一结算 tick 去重，
持续技能在此基础上限制为每 20 tick 一次；同一骷髅的多个弹体和同一施法者的多个黑洞共享节流。
热、冷、纳酸自己的持续伤害与爆发不带攻击异常声明，不会递归累积。

## 当前默认值

异常仅施加于玩家；创造、旁观玩家不累积。10 点满层、满层清零后发布事件再获得对应 300 tick 适应 Buff 的顺序保留。
适应只阻止对应进度增加。触发事件已补 `@Cancelable`，取消可阻止默认爆发，清零与适应仍执行。

| 生物／招式 | 异常 | 每次累积 | 最小间隔 |
| --- | --- | ---: | ---: |
| InfPlayer 普通攻击 | 热 | 1 | 同 tick 去重 |
| InfPlayer 剑气倍伤 | 热 | 2 | 20 tick |
| InfEnderman | 冷 | 1 | 同 tick 去重 |
| 普通骷髅弹体爆炸 | 冷 | 2 | 20 tick |
| ghost 弹体接触 | 冷 | 1 | 20 tick |
| 斥候、追踪者、异常鱿鱼 | 纳酸 | 1 | 同 tick 去重 |
| 巨碑、壁垒 | 冷 | 2 | 20 tick |
| 碎骨者、重磅本体／炸弹／死亡爆炸 | 热 | 2 | 20 tick |
| 缠绕柱持续吸取 | 纳酸 | 1 | 20 tick |
| Hearthstone 坍缩攻击 | 热 | 2 | 20 tick |
| Collapsar 斩击 | 冷 | 2 | 20 tick |
| 黑洞持续伤害 | 冷 | 1 | 20 tick |
| 陨石持续范围伤害／陨石事件爆炸 | 热 | 1／2 | 20 tick |

每种异常独立计时：连续 60 tick 没有成功增加进度时首次减少 1 点，之后每 20 tick 减少 1 点；
再次增加只重置该类型的衰减计时。命令 set 同样重置计时，set 到 10 仍只设置而不触发。
进度与衰减时钟在死亡 Clone 中继续复制；短期攻击者节流与待处理命中不复制。

热／冷仍每 20 tick 尝试造成 1 点对应伤害，倍率为 `1 + 0.5 × 进度`。
纳酸由原每 3 tick 改为每 20 tick 扣 1 点生命（保底 1 点），随机一件装备尝试损耗 `8 × 进度` 耐久。
满层仍尝试造成最大生命 40% 的对应伤害：热为 inFire 并点燃 200 tick，冷为 freeze 并叠加 200 tick 缓慢／虚弱，纳酸为 magic。
hurt 仍接受原版免疫、减伤与无敌帧规则；这里不承诺实际扣血总是等于输入值。

## 骷髅 ghost 分支

`core/anomaly/SkeletonShotCycle` 为每只骷髅保存独立计数，循环为 5 发普通、1 发 ghost，第 6、12、18 发为 ghost。
计数保存为骷髅 NBT `NormalShotsFired`，换目标不会重置。

`InfSkeletonArrowEntity` 共用实体类型与几何资源，由同步布尔字段 `GHOST` 选择贴图：
普通型 `hall_skeleton_arrow.png`；ghost 型 `hall_skeleton_arrow_ghost.png`。通用渲染器通过既有 `texture()` 扩展点获取资源，不添加具体实体分支。
非 Living 的自动实体模型 `GeoEntityModel` 同时读取 `IAutoRenderableEntity.translucentTexture()` 声明：
默认 false 保留 GeckoLib 的 cutout 渲染；ghost 返回 true 使用 `entityTranslucent`，保留 PNG 的部分 alpha 混合。
API 和实体只声明布尔值，不加载 RenderType 等客户端类型，渲染模式由客户端模型解释。

ghost 沿用追踪速度和转向参数，穿过方块与生物，不因接触或受攻击爆炸／消失。
目标失效时每 10 tick 尝试选择发射者的新目标或 32 格内最近的有效敌对生物。
接触判定使用本 tick 移动扫过的碰撞箱；每个接触目标每 tick 最多调用一次 `hurt(..., 2)`，
调用前后均将该目标 `invulnerableTime` 清零。盾牌、永久无敌、护甲等其余规则仍有效。
排除发射者、hall 同阵营、创造／旁观玩家；接触其他有效生物也会伤害。
ghost 寿命为 100 tick（5 秒），最后一个 tick 仍可接触伤害。普通型的爆炸、碰撞和 200 tick 寿命路径保留。
ghost 到期丢弃或被 kill 时，在服务端 remove 入口、实体尚未移除前发送一次喷射：24 个向外飞散的 END_ROD，
叠加少量原飞行速度与向上偏移，另加 8 个 POOF 作为消散烟雾。它只有视觉效果，不爆炸、不造成额外伤害。
仅处理 KILLED／DISCARDED；区块卸载和跨维度移除不播放，已移除实体不会重复喷射。

## 存档、同步与验证

异常存档保留 heat/cold/acid 顶层进度，额外写 `IdleTicks` 复合标签保存衰减时钟。
S2C 的 `syncData`／`applySyncData` 仍只传进度，与持久化字段分开；包 ID、方向和 int+NBT 布局不变。
弹体存档增加 `Ghost`，沿用 `Life`、OwnerUUID、TargetUUID；黑洞保存用于异常节流的 `AnomalyAttacker` UUID，不持有施法者对象。
主网络协议升为 4，要求双方使用包含新增 ghost 实体同步字段的同版模组。

`tools/tests/AnomalyStateTest.java` 是纯 JDK 回归用例，覆盖衰减边界、续击重置、类型隔离、
保存后恢复、溢出、逐来源节流、同 tick 去重和独立骷髅发射循环，不运行 Minecraft。
常规构建继续执行全部既有架构检查与空 classpath 核心编译，没有放宽门禁或重建耦合清单。

实机／视觉仍由用户验收：对照不同 hall 攻击累积、盾牌与吸收排除、适应与衰减、死亡／重进存档，
并检查骷髅第 6 发的半透明贴图、穿墙追踪、连续接触伤害、100 tick 消失时喷射粒子和普通弹体爆炸。
