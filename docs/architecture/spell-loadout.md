# 法术物品槽与轮盘配置

## 层次

| 位置 | 职责 |
| --- | --- |
| `core/magic/state/SpellLoadoutState` | 槽位选择、环绕、轮盘索引、跨页绑定去重、范围清洗与配置快照 |
| `core/magic/state/SpellCooldowns` | 同名法术共享冷却及递减 |
| `gameplay/magic/inventory/SpellInventory` | 实际法术物品、装入、取出、移动、物品去重、容量调整与溢出列表 |
| `gameplay/magic/inventory/SpellInventorySnapshot` | 独立的物品、冷却和核心配置快照 |
| `gameplay/magic/inventory/PlayerSpellInventories` | 当前玩家容量来源、服务端 tick、越界物品返还背包及掉落 |
| `platform/forge/magic/SpellInventoryProvider` | Forge 能力挂载、失效处理、存档读写接入 |
| `platform/minecraft/magic/MagicStorage` / `MagicNetworkCodec` | 独立的存档和网络编码 |

旧 `PlayerMagicPool` 和 `ByteItemHandle` 已移除。GUI、施法、网络和重生复制获取的是 `SpellInventory`，不是 Forge 提供者。
`changeSlot`、`getSlotnow`、`upDate` 等未使用的旧接口别名不再保留。

核心配置状态只使用整数、数组与槽位资格回调，不持有 ItemStack、玩家、Forge 或 NBT。
游戏容器依赖 Minecraft 物品类型，通过容量查询函数取得可用槽位数；不直接保存玩家引用、不实现 Forge 物品接口、不负责序列化。
玩家相关的背包返还和掉落实现在单独的游戏接入类中。

## 操作规则

- 默认槽位数仍为 5，上限仍为 64，实际容量继续由既有属性与 provider 计算。
- 每格只能放一个法术；容器内同种法术不重复。
- 插入和提取的 simulate 模式不修改物品、轮盘或同步标记。
- 移动先验证两端，再一次性变更，不再由网络层分步提取、插入、回滚。移动后，原槽的轮盘绑定仍按原策略清除，不自动转移到新槽。
- 选中格设置与相对移动由核心处理，负数环绕；相对移动使用较宽整数中间值避免溢出。
- 轮盘仍为 3 页，每页 8 个方位，空位为 -1。
- 轮盘只绑定非空的主动法术；同一槽位不能在其他方位重复绑定。同名法术的异常重复数据也会被游戏层清洗。
- 清洗顺序固定为页号／方位递增，保留第一次出现的合法绑定。

网络配置处理器仅验证请求形状，然后交给容器和核心配置清洗，不再重复维护另一套绑定资格规则。

## 缩容与恢复

服务端每 tick 推进冷却，并检查当前可用容量。
越界物品先从法术容器移出，再逐个尝试加入玩家背包，剩余部分掉在玩家脚下。
客户端仅推进冷却显示，不会向玩家背包放入物品，也不会执行掉落。
选中格和失效轮盘绑定随服务端容量调整一并清理并请求同步。

存档／重生快照恢复时，先按底层最大容量保留合法内容与绑定，避免玩家属性尚未恢复时误删映射。
后续服务端 tick 再按实际属性容量处理溢出、选择和绑定。

## 快照与同步

游戏快照组合一个 `SpellLoadoutState.Snapshot`，物品堆和绑定数组在快照边界复制。
存档仍使用当前开发格式的 inventory、cooldowns、selected_slot、wheel_slots 字段；没有新增旧存档兼容分支。

S2C 魔法池包现在额外携带服务端可用槽位数。
客户端先应用该容量，再应用选择和绑定，避免属性同步较晚导致合法高槽位绑定被清空。
容量单独变化也会触发同步，不要求物品或冷却同时改变。
主通道协议为 **3**，客户端与服务端必须一起更新并重启。

Forge 提供者拥有 LazyOptional；实体失效时使视图失效，重生复制中的 reviveCaps 访问会重建视图，容器快照仍可复制。

## 构建约束与验收

- `compileCoreJava` 对全部核心类执行纯 JDK 编译。
- 新增 `checkSpellInventoryDependencies`，在普通编译和构建时检查游戏容器不直接引用 Forge、NBT 或平台实现。
- 本轮按用户安排只做代码检查、编译打包，不运行客户端或游戏行为测试。

人工验收重点：装入／取出／移动、重复法术拒绝、轮盘跨页去重、槽位数增减、背包满时缩容返还、重登／重生、混合类型法术、同名共享冷却及客户端容量显示。
