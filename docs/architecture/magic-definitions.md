# 魔法类型与求值边界

## 归属

| 位置 | 内容 |
| --- | --- |
| `core/magic/model/MagicType` | 流派枚举、原有 RGB 色值及语言键生成 |
| `core/magic/model/MagicTypes` | 非空、去重、不可变的流派组合及匹配查询 |
| `core/magic/model/UsingType` | 即时、蓄力、持续三种使用方式 |
| `core/magic/model/SpellKind` | 主动与被动分类 |
| `core/magic/model/SpellDefinition` | 不可变基础声明、具名 builder 与模式归一化 |
| `gameplay/magic/stats/MagicStats` | 读取玩家、物品、属性和 provider，执行游戏上下文中的数值求值 |
| `platform/forge/magic/MagicAttributeProvider` | 把具体属性注册表接入公共 provider 链 |
| `api/magic` | 释放／伤害／回复事件、结果码、数值扩展接口和 provider 注册入口 |

原来位于物品基类包中的纯枚举不再依赖物品实现；组合类型也不再放在游戏 API 包中。
类型的名字、成员顺序、颜色、语言键和行为保持原样；只调整 Java 包归属与引用。
没有保留旧包的转发类或别名。

属性查询和 provider 的折叠顺序仍由 MagicStats 统一管理，纯公式继续位于 MagicMath。
具体属性 provider 现位于 Forge 平台包，注册表取值通过运行时服务接入。
MagicStatRegistry 仍是公共注册入口，已有 provider 的优先级、稳定排序及求值方式没有调整。
核心模型源码移除了仅为文档链接而添加的游戏实现 import，避免产生虚假的依赖。

`api.magic` 仍然是游戏扩展 API：事件参数可以使用 Player、ItemStack 等 Minecraft 类型，也保留 Forge 事件基类。
它不是纯 JDK 模块；纯 JDK 保证只适用于 `core`。

## 新代码引用

```java
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.MagicTypes;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.core.magic.model.SpellKind;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;

MagicTypes types = MagicTypes.of(MagicType.FIRE, MagicType.WATER);
```

声明物品时使用核心枚举／组合；扩展数值时实现 api.magic.MagicStatProvider；
需要基于玩家与物品读取实际数值时调用游戏层 MagicStats。

## 构建约束

四个类型加入现有 `checkCoreDependencies` 和 `compileCoreJava`，整个核心使用空依赖 classpath 独立编译。
新增 `checkMagicApiDependencies`，阻止 api.magic 的源码引用项目内 core/api 之外的具体实现包。
文档说明中的实现名称不作为源码依赖；游戏实现不得通过 import 或完全限定类型写回 API。

本轮未修改玩法公式、枚举成员顺序、资源、视觉参数、存档编解码或网络协议。
主协议仍为 3。按用户安排，本轮仅做依赖检查和编译打包，实机验收由用户进行。

后续基础声明已进一步独立，详见 [法术定义与物品接入](spell-definition.md)。
