# 法术定义与物品接入

`core/magic/model/SpellDefinition` 保存不可变的法术基础声明，只依赖 JDK 与其他核心类型。
它包含流派组合、释放方式、主动／被动分类、启动耗蓝、冷却、蓄力、维持耗蓝、触发间隔、伤害和最低蓄力比例。
它不保存玩家、物品堆、冷却剩余时间、法力余额或施法进度。

## 声明方式

```java
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.bytechen.rottenruins.core.magic.model.UsingType;

SpellDefinition definition = SpellDefinition.builder(UsingType.CHARGE, MagicType.FIRE, MagicType.WATER)
        .manaCost(120)
        .cooldownTicks(40)
        .chargeTicks(20)
        .minChargeRatio(0.25f)
        .damage(12.0f)
        .build();
```

也可以把现成的 `MagicTypes` 传入 `builder(UsingType, MagicTypes)`。
流派非空且去重，声明的第一个流派仍控制主配色；混合流派法强仍取加成平均值。
主动／被动分类默认为 `ACTIVE`，使用 `.kind(SpellKind.PASSIVE)` 声明被动。

数值默认是 0，最低蓄力比例默认 0.25，维持触发间隔默认 1 tick。
构建时沿用原物品基类的规则：耗蓝、冷却、伤害不能低于 0；最低蓄力比例钳至 `[0, 1]`，NaN 视为 0。
只有 CHARGE 保留蓄力时长；只有 KEEP 保留维持耗蓝与触发间隔，其他模式归零／归一。
KEEP 间隔在定义中至少为 1，实际求值时仍由 MagicStats / MagicMath 应用原有上限。
修改或复用 builder 不会改变已经 build 的定义。

## 游戏层接入

`CMagicBaseItem` 现在只接受 `(Properties, SpellDefinition)`，移除了六组位置参数构造器。

```java
public FrostBoltSpell() {
    super(defaultProperties(), SpellDefinition.builder(UsingType.CHARGE, MagicType.WATER)
            .manaCost(120)
            .cooldownTicks(40)
            .chargeTicks(20)
            .damage(12.0f)
            .build());
}
```

基类 getter 委托到定义，并继续允许覆写来提供动态基值。
`definition()` 返回静态声明；游戏逻辑仍通过 MagicStats 读取经过动态 getter、属性与 provider 修饰的结果。
目标约束、射程、特殊使用、施法条件和效果钩子继续留在游戏接口／法术实现中。
本轮没有把实体、粒子、音效或 Minecraft API 放进核心定义。

当前 8 个法术均已迁移。审阅迁移前后的声明时，按原有模式归一化规则核对了所有基础数值，
并比较了构造器与 import 之外的效果实现，未改动效果代码。
审判之矛原来传入的维持耗蓝 5 在 CHARGE 模式下实际为 0，现在直接省略这项无效输入。
抑制场仍明确保留最低蓄力比例 0 和每 10 tick 的触发间隔。

## 迁移价值与检查

升级 Minecraft 时，基础声明与归一化规则可以继续使用；物品构造与效果执行仍需适配目标版本 API。
这轮没有引入数据包法术格式，也未改变物品 ID、存档、资源或网络协议（仍为 3）。

核心依赖检查与空 classpath 独立编译自动覆盖新类，现有核心共有 23 个 Java 文件。
本轮通过 `gradlew build -x test --offline --console=plain` 编译打包，未启动游戏或运行实机测试。
