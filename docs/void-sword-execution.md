# 虚空剑「蹲下左键」强制斩杀

> 触发：**蹲下 + 手持虚空剑 + 左键点中生物**
> 结果：目标生命值被改写成 `-1`，并立刻走完死亡流程（掉落、死亡事件、击杀归因照常）。

---

## 1. 一句话说清它做了什么

```
蹲下左键命中
  → 清掉目标的无敌帧（invulnerableTime / hurtTime）
  → VitalProbe 直写有效血量 = -1
  → 补一次终结击（hurt(Float.MAX_VALUE) → kill() 兜底）
  → 虚空剑气 + 重击音
```

不走"打一堆伤害"的路线，是因为这条能力的语义是**强制选中**：
命中判定一旦成立，血量就必须落到 `-1`，中间不能被无敌帧、护甲、抗性、
"覆写 `getHealth()` 返回自定义血量"的抗改血实现、或者别的模组取消掉。

---

## 2. 为什么是 `-1`，不是 `0`

血量落在 `1.0E-7` 这种"看着是 0、其实大于 0"的残值时，生物会**永远不死**。
`-1` 让"血量 `<= 0`"这件事有确定结果，终结击就一定能收掉它。

`BCCoreCompat.setHealthToMinusOne()` 走的是 `setHealth()`（**不钳制**、
允许写负数），这条路径的完整说明见 `BCCoreCompat` 的类注释。

---

## 3. 攻击距离 100 格怎么实现的

`VoidSword#getAttributeModifiers` 在主手槽位给玩家挂一个：

```java
ForgeMod.ENTITY_REACH.get()   // forge:entity_reach，默认 3.0，上限 1024
  + ATTACK_REACH_BONUS (100.0)  // Operation.ADDITION
```

- **客户端**准星射线读的是同一个属性 → 100 格内的生物能被选中；
- **服务端** `ServerGamePacketListenerImpl` 的距离校验也读它 → 100 格的攻击不会被判成作弊包；
- 修饰符 UUID 是写死的字面量 `8f1c4b2a-7d63-4e59-9a0f-3c6b51d2e7a4`，
  不能随机生成：同一物品的同一修饰符必须跨端跨存档一致，否则会被原版当成
  两个修饰符叠加，来回切换主手会越加越多。

**副作用（有意保留、需要知道）**：`forge:entity_reach` 同时管
"右键点生物"的距离，所以装了这把剑时，右键点 100 格外的生物也会生效。
原版没有把这两个距离分开。方块交互走的是 `forge:block_reach`，**不受影响**。

---

## 4. 三条触发路径（互为兜底，不会重复结算）

| # | 路径 | 位置 | 作用 |
|---|------|------|------|
| 1 | `AttackEntityEvent` | `ForgeEventHandler#onAttackEntityVoidSword` | 主路径。原版攻击链路正常时的唯一触发点 |
| 2 | 自有 C2S 包 | `VoidSwordClientHandler` → `VoidSwordStrikePacket` | 客户端射线自己选中目标后发包；服务端不依赖原版交互距离判定 |
| 3 | `LivingHurtEvent`（`LOWEST`） | `ForgeEventHandler#onLivingHurtVoidSword` | 伤害被别的模组 `setCanceled(true)` 时，把"强制选中"补回来 |

**去重**：`VoidSwordGuard` 按「玩家 + 目标 + tick」记两本账 ——
一本是**改血结算权**（`LAST_STRIKE`），一本是**演出权**（`LAST_FEEDBACK`）。
改血本身幂等（写 `-1` 就是 `-1`），需要挡住的是同一 tick 里
连炸三道剑气、响三声雷那种"看得出来重复触发"的表现。

### 为什么 1 和 3 都不取消事件

取消 `AttackEntityEvent` 会让 `Player.attack` 提前 return，受击红闪、击退、
受击音、冲刺暴击、出刀自动落剑全部消失（`CrimsonVowAttack` 的类注释里
记着这条教训——早先的版本取消了事件，连受击音都要反射去拿 `protected` 的
`playHurtSound`）。这里只做"叠加"：血量被钉死之后，原版结算只是再确认一次死亡。

### 为什么 3 要取 `EventPriority.LOWEST`

Forge 同一优先级按注册顺序派发。取 `LOWEST` 能保证别的模组已经在更早的
优先级里把事件取消掉，所以读到的是**最终**的 `isCanceled()`。
代价是每次受击多一次 boolean 判断，没取消时什么都不做。

### 为什么 3 不动 `setAmount`

金额层面的减伤（本模组 `WeaponBlock` 的格挡、别的模组的护甲公式）是另一回事，
让它们照常工作。这条路只处理"整段被取消"。

---

## 5. 不碰什么（以及为什么）

| 不碰 | 原因 |
|------|------|
| `setInvulnerable(true)` | 实体级永久无敌（凋灵出场动画、世界边界里的实体），属于"这个实体本来就该免疫"的语义，不该被一把武器悄悄抹掉 |
| `lastHurt` | `protected` 字段够不着；而且无敌帧已清零，它不会再参与结算 |
| `setAmount` | 见上一节 |
| 创造 / 旁观玩家 | **没有加豁免**。语义是"强制选中"，创造玩家测试 Boss 时也会被打死。要改的话在 `VoidSwordGuard#executeInstantKill` 里加一道，参照 `BCCoreCompat.damage` 的写法 |

---

## 6. 涉及的文件

| 文件 | 职责 |
|------|------|
| `overworld/registry/items/VoidSwordGuard.java` | 能力本体：姿态判定、去重、结算顺序、特效 |
| `overworld/registry/items/VoidSword.java` | 100 格攻击距离的属性修饰符 + `hurtEnemy` 兜底 |
| `compat/BCCoreCompat.java` | `clearHurtGuard()` / `setHealthToMinusOne()` |
| `event/ForgeEventHandler.java` | 路径 1（`AttackEntityEvent`）与路径 3（`LivingHurtEvent`） |
| `client/VoidSwordClientHandler.java` | 客户端 100 格射线选中 |
| `network/c2s/VoidSwordStrikePacket.java` | 自有 C2S 链路（走 `UniversalPacket`，**不动包序号**） |

### 为什么自有包不新增包序号

走项目已有的 `UniversalPacket` + `PacketActionRegistry` 机制
（与 `HeartLosePacket` 同一套路），只需要注册一个 action 处理器。
`NetworkHelper.register()` 里的包序号是唯一一处"客户端服务端必须严格同序"的地方，
能不动就不动。

---

## 7. 没有 VitalProbe 时会怎样

| 情况 | 行为 |
|------|------|
| 装了 VitalProbe | 直写真实血量存储位置，穿透一切抗改血实现 |
| 没装 VitalProbe | `setHealthToMinusOne()` 返回 false → 回退 `writeHealthDirect()`：`setHealth(-1)` → 血量 `<= 0` 就终结，否则 `hurt(Float.MAX_VALUE)`。**依然能斩杀**，但打不穿抗改血生物（那些生物本身就是"免疫一切改血"的设计） |

---

## 8. 调试

目标没有掉血 / 没有死时，按这个顺序看 `logs/latest.log`：

1. `[BCCoreCompat] 写入血量失败` / `调用 VitalProbe.triggerVoidSword 失败`
   → VitalProbe 侧的问题（版本不匹配、`general.enabled = false`、强度不够）。
   打开 VitalProbe 的 `general.debug = true` 看它自己的逆向过程（`/vitalprobe probe`）；
2. `[VoidSword] 拒绝一次超距斩杀请求`
   → 自有包的距离校验没通过（客户端与服务端位置差超过 100 + 8 格）；
3. 什么都没有 → 姿态没成立：确认是**主手**、确认真的在蹲（`isShiftKeyDown`）、
   确认目标 `isPickable()` 且 `isAlive()`。
