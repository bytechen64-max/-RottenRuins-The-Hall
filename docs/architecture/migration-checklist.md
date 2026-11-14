# 版本迁移清单

本清单基于当前 1.20.1 源码的职责与实际依赖整理。目标版本与加载器尚未确定，
不预设新平台 API，也不建立同时支持多个版本的抽象框架。
后续优先保持玩法与视觉效果，允许舍弃旧开发存档兼容。

## 已能独立保留的部分

| 核心内容 | 位置 | 版本迁移时的处理 |
| --- | --- | --- |
| 感染数据模型、规则索引、快照与权重选择 | `core/infection` | 保留规则，更新外部资源解码和游戏对象解析 |
| 假死状态、爆体缩放与噪声公式 | `core/entity/burst` | 保留状态与公式，适配实体生命周期和渲染接入 |
| 魔法流派组合、释放方式、分类与基础定义 | `core/magic/model` | 保留声明，适配物品构造 |
| 魔法数值公式 | `core/magic/rules` | 保留公式，更新属性、物品和 provider 的上下文接入 |
| 法力、冷却和槽位／轮盘状态 | `core/magic/state` | 保留状态，适配能力、持久化和同步 |
| 施法阶段与计时 | `core/magic/casting` | 保留时序，适配输入、事件、目标与效果执行 |

当前核心共 23 个 Java 文件，构建会使用 Java 17 和空依赖 classpath 编译它们。
这是对当前依赖边界的保证；目标版本确定后仍需确认 Java 工具链。

## 本轮已经收口的依赖

- MagicHandle 的能力读取、事件投递、附魔等级与空法杖注册项由 MagicRuntime.Services 提供。
- ManaAccount 保存游戏侧法力业务，ManaProvider 负责 Forge 挂载与持久化，能力暴露账户而非提供者。
- 感染进化与扩散执行器通过 InfectionRuntime.Services 解析实体／方块注册表并投递事件。
- DifficultyHelper 通过服务访问全局难度，SavedData 创建、事件和同步仍由平台管理。
- 属性 provider、魔法生命周期订阅者和所有魔法网络处理归入 platform/forge/magic。
- 魔法具体内容移至 content/magic；法术接口与上下文位于 gameplay/magic/spell，法杖通过 CastingStaff／ManaReserve 契约接入。
- 法力／槽位快照的应用与轮盘回执解释位于 client/magic，通用魔法同步包没有 Minecraft 客户端类型引用。

安装入口、执行顺序和文件清单见 [运行时与内容分层](runtime-boundaries.md)。

## 后续迁移仍需处理的实际位置

| 位置 | 当前边界与迁移工作 |
| --- | --- |
| `platform/forge/magic`、`platform/forge/infection` | 集中适配事件总线、能力、注册表查询、资源重载与通道；保持服务返回值、取消与通知语义 |
| `platform/minecraft` | 适配 NBT、ItemStack 编码和 SavedData，继续分开存档与网络格式 |
| `gameplay` | 仍直接使用 Player、ItemStack、实体和世界 API；这部分游戏操作需要随版本调整，不承诺整层无需修改 |
| `api/magic`、`api/infection` | 扩展事件仍继承 Forge 契约，目标加载器确定后需要适配；服务接口中的事件参数也随之更新 |
| `content/magic` | 物品接入、具体法术效果、法杖 NBT 与 Curios 集成仍需按目标 API 适配；基础定义和核心公式继续复用 |
| `network`、其他 `overworld/registry` 内容 | 主通道与非魔法系统的注册、实体、AI、伤害接入仍需逐模块迁移 |
| `client` 和客户端 mixin | 按目标版本适配渲染、字体、tooltip、粒子、shader、动画库和客户端事件，使用当前画面对照 |

本轮完成此前列出的施法／感染运行时依赖和魔法内容组织调整，没有宣称整个模组已独立于 Minecraft 或 Forge。
其他实体、AI 和渲染系统应在明确目标 API 或下一轮具体范围后按模块调整，避免为了改包名改动效果实现。

## 目标版本确定后的实施顺序

1. 确认 Minecraft、加载器、Java 和动画／渲染等依赖的可用组合，再更新构建与启动入口。
2. 先让 core 独立编译通过，再恢复注册、平台生命周期和数据加载。
3. 接通玩家状态、世界存储、网络和重载，明确新消息布局与通道版本。
4. 恢复感染与施法的游戏执行，再逐项适配实体、物品、AI 与伤害接入。
5. 恢复客户端渲染，逐项对照 shader、粒子、动画、配色、tooltip、轮盘和爆体表现。
6. 由用户验证新世界、重登／重生、多人同步、资源重载以及三种施法模式。

存档兼容不是本阶段约束；如果将来发布后改变这一要求，再设计数据迁移。
视觉保留仍然需要实际游戏对照，编译成功无法代替视觉验收。

## 当前可执行的检查

在项目根目录执行：

```powershell
.\gradlew.bat compileCoreJava checkGameplayDependencies checkSpellInventoryDependencies checkMagicApiDependencies checkMagicPacketDependencies --offline --console=plain
.\gradlew.bat build -x test --offline --console=plain
```

`checkCoreDependencies` 只允许核心 import 引用 JDK／core；`compileCoreJava` 同时检查完全限定引用和隐式依赖。
`checkSpellInventoryDependencies` 阻止游戏法术容器依赖 Forge、NBT 或平台实现。
`checkMagicApiDependencies` 阻止魔法扩展契约依赖项目内 api／core 之外的实现包，但允许游戏和 Forge 类型。
checkGameplayDependencies 覆盖整个 gameplay，禁止直接 Forge、platform 和旧注册实现引用；
checkMagicPacketDependencies 禁止通用魔法同步包直接使用 Minecraft 客户端类型。
此外，checkArchitectureDependencies 检查整个 API、gameplay 工具链、通用网络客户端守卫和全源码存量耦合，见[全源码依赖门禁](dependency-gates.md)。这些检查仍不分析所有第三方传递依赖，也不代表运行或视觉测试通过。

其他模块说明见 [架构文档入口](README.md)。
