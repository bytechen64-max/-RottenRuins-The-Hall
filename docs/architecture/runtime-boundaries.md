# 运行时与内容分层

## 本轮归属

| 位置 | 职责 |
| --- | --- |
| `core` | 纯 JDK 的规则、状态、数学与声明，当前 23 个 Java 文件 |
| `gameplay/magic/MagicRuntime` | 魔法所需的平台服务契约与一次性安装入口 |
| `gameplay/infection/InfectionRuntime` | 感染注册表解析、事件与难度访问的服务契约 |
| `gameplay/magic/state/ManaAccount` | 玩家法力业务、回复政策、变化标记、服务端同步快照 |
| `gameplay/magic/spell` | 法术接口、释放上下文、CastingStaff 与 ManaReserve 行为契约，包含 Minecraft 上下文 |
| `content/magic/base` | 法术／法杖物品基类与说明生成 |
| `content/magic/spell` | 8 个具体法术的定义与效果实现 |
| `content/magic/curios` | 魔法饰品与 Curios 集成 |
| `content/magic` | 法杖、魔能盘与法杖配方 |
| `platform/forge/magic` | 服务实现、ManaProvider、SpellInventoryProvider、属性 provider 与生命周期订阅 |
| `platform/forge/magic/network` | 同步节流、槽位／轮盘动作、法力／槽位同步与轮盘反馈发送 |
| `platform/forge/infection/ForgeInfectionServices` | 实体／方块注册表、事件投递和全局难度存储接入 |
| `client/magic` | 服务端快照应用与轮盘反馈的解释、提示和界面变化 |

旧 PlayerMana 和原魔法内容包没有保留转发别名。所有 Java 调用方、注册入口和现有 GameTest 源码已更新引用。
GameTest 源码随项目编译，本轮按用户安排未执行游戏测试。

## 初始化与运行

HallMod 构造时安装 ForgeMagicServices，InfectionBootstrap 构造接入时安装 ForgeInfectionServices。
这两步都早于创建玩家与执行玩法。安装只保存服务对象，不在此时读取尚未完成注册的物品或附魔。
服务在实际调用时解析注册值，因此没有把 RegistryObject.get 提前到模组构造阶段。

每个运行时只允许安装一次。缺少安装时明确抛错，不使用空服务吞掉事件、扣费或同步。
服务对象不保存玩家、世界、服务器或能力实例；同一个客户端进程退出世界再进入时，不会从服务中复用旧世界引用。
现有施法会话和同步节流表的清理继续由魔法生命周期订阅者处理。

ManaProvider 挂载的能力类型变为 ManaAccount。提供者拥有 LazyOptional 与 NBT 读写，消费者使用账户。
提供者注册了失效回调，并支持原玩家在 Clone 中 reviveCaps 后重新获取账户；重生复制仍沿用原有规则。
法力初始上限、小数回复、取消事件、回复后通知、上限同步和快照字段均保持原行为。

## 执行顺序

MagicRuntime 提供玩家容器／账户、属性取值、附魔等级、空法杖物品、饰品流派查询和事件投递。
CastingStaff 描述法杖接受条件、流派加成与施法后回调；ManaReserve 描述可选储魔与耗尽回调。
具体 StaffBase / WandItem 实现契约，施法框架不再依赖它们的类名。
施法校验、法杖储魔扣费、法力不足判断、冷却与效果回调仍由 MagicHandle 执行。
回复业务仍按“回复前事件 → 读取修改后的数值 → 核心累加 → 实际增加时通知”执行。

InfectionRuntime 保留 Forge 注册表原来的查找结果，包括未知目标返回 null 的语义。
扩散继续按“选择目标 → 可取消前事件 → 掉落／修改方块 → 后事件”执行。
进化后事件仍在实体转化后原位置投递；难度变化仍由 GlobalDifficultyData 管理取消、落盘标记和同步顺序。

两个同步包继续检查接收方向、入队，再通过 DistExecutor 的客户端分支调用 client/magic。
轮盘回执的客户端注册从 ClientPacketHandlers 进入 WheelFeedbackHandler，服务器发送类只保留动作编号与编码发送。
没有改动主通道的注册顺序、消息 ID、字段顺序、action 字符串或协议版本 3。

## 依赖约束与实际限度

普通 compileJava/build/check 自动检查：

- core 仅使用 JDK／core，并使用空 classpath 单独编译。
- gameplay 源码不得直接引用 Forge、platform、content 或旧 overworld.registry 实现。
- 法术容器不得引用 Forge、平台与 NBT。
- 魔法扩展 API 不得引用项目内 api／core 之外的具体实现。
- 通用魔法同步包不得直接引用 Minecraft 客户端类型。

gameplay 可以使用 Minecraft 类型与公共游戏扩展契约，具体物品通过游戏行为接口接入。
这些契约的事件仍继承 Forge 事件，内容物品也仍使用 Minecraft 与 Curios，因此本轮并未实现完整跨加载器运行。
目标版本确定后，需要适配这些游戏操作、扩展契约和具体服务实现；核心状态、公式和声明可以继续保留。

本轮保持效果代码、资源和视觉参数，静态对照与编译无法代替用户的运行／视觉验收。
