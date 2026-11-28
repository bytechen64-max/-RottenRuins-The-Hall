# 魔法数值与混合流派

## 分层

- `core/magic/rules/MagicMath`：纯 Java 数值规则，包括法力与槽位边界、射程、蓄力完成度、击杀回蓝基值、冷却缩减、附魔耗蓝折扣和混合流派法强。
- `gameplay/magic/stats/MagicStats`：仍作为游戏代码的统一求值入口，读取物品、玩家属性和 provider，交给纯数值层计算。
- `MagicAttributeProvider`：读取属性，将冷却计算交给 `MagicMath`。
- `MagicHandle`：保持原有施法流程，附魔耗蓝折扣改用公共计算入口。

原有 provider 的顺序、扣蓝、冷却键、施法状态和伤害事件仍保留。
伤害流派加成只在原有属性/伤害处理链求值，避免因为新增混合流派而重复乘法强。
施法阶段和时序已独立为核心状态，目标解析与效果执行仍由游戏层处理，详见 [施法状态与游戏执行](casting-state.md)。法力、冷却状态和存储拆分见 [法力与冷却状态](magic-state.md)。

`MagicMath`、`MagicTypes` 和 `MagicType` 可在不引用 Minecraft / Forge 的情况下独立编译。
`MagicType`、`UsingType`、`SpellKind` 和 `MagicTypes` 统一位于 `core/magic/model`；调用方已更新，不保留旧包别名。

## 声明混合流派

保留 `MagicType` 枚举的 WATER、FIRE 等单个流派；使用不可变的 `MagicTypes` 组合多个枚举值。

```java
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import static org.bytechen.rottenruins.core.magic.model.MagicType.*;

super(defaultProperties(), SpellDefinition.builder(UsingType.CHARGE, FIRE, WATER)
        .manaCost(120)
        .cooldownTicks(40)
        .chargeTicks(20)
        .damage(12.0f)
        .build());
```

组合至少包含一种流派，不允许 null，会按声明顺序去重。
`MagicTypes.of(FIRE, WATER, FIRE)` 等同于 `FIRE / WATER`，重复填写不会重复计入法强。

法术基类统一使用 `(Properties, SpellDefinition)`，原有位置参数构造器已移除。
现有内容保持原有单流派，不会自动变成混合流派。

直接实现 `IMagicBaseItem` 时，可以覆盖 `getMagicTypes()` 返回组合，同时让兼容接口
`getMagicType()` 返回该组合的 `primary()`。
原有只实现 `getMagicType()` 的代码自动按单流派处理。

## 法强

```text
有效法强 = max(0, 通用法强 + 所含不同流派加成的平均值)
```

例如通用法强 1.0、火加成 0.6、水加成 0.2，火 / 水法术的法强为 1.4。
同一装备下，单火法术仍为 1.6。没有加成的流派按 0 参与平均。

实际施法仍通过 `MagicStats.spellPower(player, spellStack)` 读取。
只有流派组合、没有物品堆时，可使用 `MagicStats.spellPowerForTypes(player, types)`。

## 法杖、饰品与展示

当前匹配规则是任一流派相符即可：火 / 水法术可以匹配火杖或水杖，
获得对应法杖的特殊使用条件和储魔支持；其他流派法杖仍按原有普通施法流程处理。
对应流派饰品也按任一种匹配判定，不叠加多次“特殊使用”效果。
空法杖与通用法杖的既有规则保留。

物品类型说明会列出所有流派，每个名称使用自己的原有颜色；轮盘类型文字同样列出所有流派。
数字和轮盘配色沿用声明的第一个流派，现有单流派物品的配色保持一致。
Shader、实体特效、动画与伤害类型不会因新增枚举组合而自动改变。

组合是法术物品的定义，不额外写入 ItemStack NBT，不修改既有物品注册 ID、存档或网络消息格式。

## 验证

`MagicRulesGameTests` 覆盖数值上下限、冷却舍入、耗蓝折扣、蓄力边界、组合去重和不可变性、
平均法强、混合类型说明、真实玩家属性读取、原有单火法术数值与两种法杖的匹配。
这些测试对应此前混合流派阶段的验证；后续结构调整按用户安排只执行依赖检查和编译打包，
没有重新启动游戏或运行 GameTest。客户端轮盘文字的布局仍应在游戏中实机确认。

类型定义与查询实现的职责见 [魔法类型与求值边界](magic-definitions.md)。
基础声明与物品接入见 [法术定义](spell-definition.md)。
