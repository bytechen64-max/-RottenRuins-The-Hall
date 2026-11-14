# 感染系统分层

`core` 现在表示纯 Java 数据、规则、算法与状态，而不是整个感染系统的实现目录。
原 `core/infection` 中的游戏执行、平台接入和辅助工具已迁往对应层，所有 Java 调用方一并更新。

## 包职责

| 包 | 内容 |
| --- | --- |
| `core/infection/evolution`、`blockspread` | JSON 数据模型、目标与权重、泛型感染兜底档位 |
| `core/infection/rules` | 不可变规则索引、快照、共用权重选择算法 |
| `core/infection/InfectionIds` | 数据命名空间常量，不触发模组初始化 |
| `gameplay/infection/evolution` | 感染与进化执行、实体替换、装备与 NBT 复制、兜底执行 |
| `gameplay/infection/blockspread`、`biome` | 方块扩散与群系替换 |
| `gameplay/infection/difficulty` | 游戏侧难度查询和注册入口 |
| `platform/forge/infection` | Forge 初始化、资源重载、能力、事件、网络、带事件与同步的难度存档 |
| `platform/minecraft/infection/worlddata` | Minecraft SavedData 与世界数据访问 |
| `client/infection` | 客户端数据包处理与客户端初始化绑定 |
| `command/infection` | 难度命令 |
| `datagen/infection` | 规则 JSON 生成器，自行负责 JSON 序列化 |
| `utils/logging` | 日志与高频日志节流，不依赖 Forge 初始化入口 |

初始化入口从 `InfectionCore` 改为 `platform/forge/infection/InfectionBootstrap`，由 HallMod 调用。
客户端处理器在自己的客户端生命周期订阅方法中初始化，不由通用入口引用客户端实现。

## 实际逻辑边界

- 感染与扩散不再各写一份权重算法，均调用 `WeightedSelection`。权重顺序不变，正总权重仍只消费一次有界随机数；空或全零目标不消费随机数。
- 随机来源由游戏执行层提供，核心不引用 Minecraft RandomSource。
- `InfectionFallback<T>` 只保留目标、数量和体积条件，目标类型由调用方提供；游戏执行层使用 `InfectionFallback<EntityType<?>>`。
- 泛型兜底规则中的数量下限、体积条件以及注册顺序保持原语义。
- 游戏管理器不再向数据生成器提供 GSON 单例；数据生成器自行编码核心数据模型。
- 平台初始化类不再充当所有层的日志和常量容器，避免规则模型经常量引用反向依赖 Forge。

`api/infection` 是游戏扩展契约，其中的实体接口、Forge 事件、ResourceLocation 等仍可以依赖游戏 API。
`gameplay` 仍包含游戏操作和事件协作，不承诺整层可以直接复制到另一个加载器。
本轮明确保证的是 `core` 无游戏、加载器、客户端和其他实现层依赖。

规则 JSON 路径、数据命名空间、当前存档格式、消息 ID、难度命令及玩法参数没有因为包名迁移而改变。
Java 引用需要使用新包路径，未保留旧包别名。

## 构建约束

运行普通 `compileJava`、`build` 或 `check` 时自动包含两项：

1. `checkCoreDependencies`：检查核心源码的 import，仅允许 JDK 和其他 `core` 类。
2. `compileCoreJava`：把全部核心文件使用 Java 17、空依赖 classpath 和空 sourcepath 单独编译，检查完全限定类型引用和隐式依赖。

单独执行：

```text
gradlew compileCoreJava --offline --console=plain
```

因此，把 Forge 类型改成完全限定名，或把平台引用藏进同包辅助类，都不能绕过独立编译。
核心检查的输出在 `build/core-check/classes`，不额外装入发布 jar；发布仍使用主源码集的编译产物。

按用户安排，本轮做编译打包和构建约束，运行与视觉验收由用户完成。
