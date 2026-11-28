# 回旋镖物品

继承 `BoomerangItem` 并注册普通物品即可复用右键发射逻辑；行为由
`api.boomerang.IBoomerangItem` 定义。共用 `rotten_ruins:boomerang` 投射物，
模型仍走现有物品 datagen。飞行渲染复用挥砍系统的烘焙模型顶点分析，
以最薄轴识别扁平面（也支持两条平面轴等长的模型），把该面放在飞行平面内旋转。
渲染会抵消物品 FIXED 显示变换，避免显示变换再次改变检测到的平面。
每个投射物有固定的随机倾角和旋转相位，姿态随飞行方向转向，返程同样生效。

接口可覆盖伤害、去程/返程速度、去程时间、最大存活时间、冷却、轨迹粒子及消耗。
速度单位为格/tick，时间单位为 tick（20 tick = 1 秒）。速度与时间应取正数。

- `DURABILITY`：成功发射后扣除 `consumptionCost` 耐久，物品留在手中；耐久耗尽正常损坏。
- `ITEM_COUNT`：成功发射后永久消耗对应数量，不在返程时返还。
- `NONE`：不扣数量或耐久。

创造模式免除消耗，但仍有冷却。冷却按物品类型生效，主副手共用。
只有成功生成投射物才扣费、计使用次数及设置冷却。

`onThrown`、`onEntityHit`、`onBlockHit`、`onReturned` 均在服务端执行。
实体命中先结算基础投掷伤害，再执行回调；`damaged` 表示本次伤害是否被接受。
同一目标每个飞行阶段最多触发一次；原版受伤无敌时间仍然生效。
回调里的 ItemStack 是发射时的单件快照，不是背包中的原始堆叠。
`onReturned` 仅通知，不生成或返还物品，可用 `beginReturn()` 提前结束去程。

去程命中实体或方块、或达到去程时间后进入返程。返程追踪玩家，并穿过地形，
以免卡墙；不会击中主人、友军或不允许 PVP 的玩家。主人死亡、离线或投射物
达到寿命上限时清理。物品快照、阶段、飞行时长和本阶段命中目标均随实体保存。

首个物品 `rotten_ruins:hall_boomerang`：伤害 6，耐久 384，每次消耗 1 耐久，
冷却 30 tick，去程 16 tick，速度 0.9，返程速度 1.2，最大寿命 160 tick。
两张原始贴图分别位于 `textures/item/hall_boomerang.png` 和
`textures/particle/hall_dust.png`。轨迹在每段路径上随机撒出 2–4 颗粒子，
带空间偏移和少量漂移，寿命 10–18 tick。粒子保持不透明并逐渐缩小至零，
出生时随机旋转角度和正反向角速度，角速度每 tick 衰减至前一 tick 的 86%。

验证：`./gradlew.bat --offline -I tools/boomerang-gametest.init.gradle runGameTestServer`
