# 法力与冷却状态

本轮按未发布版本整理，不迁移旧开发存档，也不保留旧网络格式。

## 职责划分

| 位置 | 职责 |
| --- | --- |
| `core/magic/state/ManaState` | 整数法力、小数回复余量、原子扣蓝、数值变化标记、纯数据快照 |
| `core/magic/state/SpellCooldowns` | 按法术 ID 共享冷却、取较长冷却、逐 tick 递减和过期移除 |
| `gameplay/magic/state/ManaAccount` | 读取玩家属性、回复规则、同步快照和变化标记 |
| `platform/forge/magic/ManaProvider` | 法力账户的能力挂载与存档接入 |
| `platform/minecraft/magic/MagicStorage` | 当前版本的 NBT 存档编解码 |
| `platform/minecraft/magic/MagicNetworkCodec` | 当前版本的网络编解码，独立于存档格式 |
| `gameplay/magic/inventory/SpellInventorySnapshot` | 槽位、冷却、选中格和轮盘映射的脱离原容器的数据快照 |
| `gameplay/magic/inventory/SpellInventory` | 游戏物品容器，组合冷却状态与槽位／轮盘状态 |
| `platform/forge/magic/SpellInventoryProvider` | 魔法物品容器的能力挂载和持久化接入 |

两个核心状态对象不引用 Minecraft、Forge、NBT、玩家实体或网络类。
槽位中仍然存放 ItemStack，因此物品容器没有被包装成“纯 Java 核心”。

`ManaAccount` 替换原 `TwistedPoint`；调用方已改为明确的法力接口。
已移除无实际调用的 `TWIST` / `TWIST_CAP` / `ITEM_HANDLE` / `ITEM_HANDLE_CAP` 能力别名与 `ByteNumberAbility`。
魔法物品槽继续使用 `MAGIC_POOL_CAP`，法力使用 `MANA_CAP`。

## 保留的玩法

- 新玩家初始法力取当前上限。
- 消耗采用全额扣除：不足不扣；非正数消耗直接成功。
- 自然回复仍按每秒数值除以 20；回复先经过可取消事件，再积累小数，攒够整数才增加法力。
- 只有实际回复到整数法力时才发出回复完成事件。
- 触及上限时丢弃无法使用的回复余量，满蓝时不积存回复。
- 同名法术跨物品堆、槽位共享冷却，较短的新冷却不能覆盖较长的旧冷却。
- 客户端只推进显示冷却，服务端负责实际施法判定与越界物品处理。
- 同步节流仍为法力 2 tick、物品槽 5 tick。冷却普通递减不逐 tick 标脏。
- 重生复制使用池快照，法力仍按原逻辑复制整数值，不复制回复小数余量。

法力上限变化现在也会请求同步，不再依赖整数法力同时改变。
核心状态拒绝非有限回复量，并避免超大回复造成整数加法溢出；这些是输入边界处理，不调整普通法术数值。

## 当前存档格式

法力能力内容：

```text
mana: int
restore_fraction: double
```

魔法池能力内容：

```text
inventory: { Items: [...] }
cooldowns: { "namespace:spell_id": remaining_ticks, ... }
selected_slot: int
wheel_slots: int[]
```

不再读取 `HallMagicMana`、`HallMagicRegenBuffer`、`hell:twisted`、`HallMagicCooldowns` 等旧键。
也不再根据缺少旧轮盘字段去自动填充第一页；新角色通过装载界面设置轮盘。
旧开发存档中的法力、法术槽位、冷却和轮盘配置不会迁移，应使用新角色或重新配置。

## 网络

法力包携带 `ManaState.View`，魔法池包携带 `SpellInventorySnapshot`。
魔法池不再通过 `serializeNBT()` / `deserializeNBT()` 发包；网络格式变化不会再直接改动存档布局。
槽位 ItemStack 和轮盘数组在快照边界复制，避免发送后被原容器继续修改。
网络读取限制条目数量，并过滤非法冷却 ID 和非正数冷却。

主通道协议为 `3` 并严格匹配，客户端和服务端必须都更新；不支持旧开发包混连。
本轮按用户安排仅编译打包，运行行为和客户端验收由用户进行。

## 建议验收

1. 新角色满蓝，消耗、回蓝、小数回复和回复取消效果正常。
2. 修改法力上限后，客户端法力条及时刷新。
3. 同名法术在不同槽位共享冷却，冷却到期后能再次释放。
4. 法术装载、选中、轮盘绑定、重登与重生后的状态符合当前规则。
5. 客户端和服务端同时使用本轮生成的 jar。

槽位与轮盘配置的进一步拆分见 [法术物品槽与轮盘配置](spell-loadout.md)。
