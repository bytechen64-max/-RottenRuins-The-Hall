# 全源码依赖门禁

普通 `compileJava`、`check`、`build` 在原检查之外运行 `checkArchitectureDependencies`。实现位于 `tools/architecture/ArchitectureCheck.java`，使用 Java 17 自带的 javac 语法树解析器，不需要游戏依赖，不执行游戏。

## 覆盖与规则

扫描 `src/main/java/org/bytechen/rottenruins` 下所有 Java 文件，包括可选 Jade 源码。仅按本次范围排除 `mexin` 与 `mexinlauncher`；出现未分类的新顶层目录会失败。构建输出实际总数和各目录数量，不能把文件数当作所有架构规则的证明。

- 整个 API 禁止项目内 api/core 之外的实现引用，以及 Minecraft 客户端、Forge 客户端和 Blaze3D 类型；Forge 公共事件契约仍允许。
- gameplay 只依赖 core、api、gameplay 与受检查的工具。沿项目工具／玩法依赖继续检查 Forge、客户端、平台、内容、注册实现与玩家持久化扩展，报告调用链。公共 API 事件的 Forge 父类不算 gameplay 的实现依赖。
- `network` 禁止客户端 import，完全限定的客户端入口必须处于明确的 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, supplier)` 调用内。检查器要求这里使用完整的 Forge 类名，避免把同名方法误认成守卫。
- 全部非 client 源码的客户端引用、client 的 Forge 引用和 utils 的平台引用，按“文件路径 → 类型”与存量清单比对。新文件、增加依赖以及已经失效但未删除的条目均失败。API 与 gameplay 的严格规则不接受清单豁免。

语法树忽略注释与字符串；检查显式 import、静态 import、完全限定类型、同包及项目通配符引用。工具依赖检查按整个工具类保守处理，不只分析某个被调用方法。它不做字节码链接、反射、第三方依赖传递或完整符号归属分析，也不能证明任意服务器控制流不会进入客户端。事件顺序、会话释放、单例状态等行为规则仍需代码审查。

## 存量清单的含义

`tools/architecture/reviewed-coupling.txt` 是既有耦合存量，不能自动重建来消除失败。它包含客户端事件订阅／加载器接口、datagen 模型生成、客户端 mixin、既有受守卫入口，以及尚待迁移的内容渲染／工具平台耦合。保留这些条目不表示完成了侧安全认证或跨加载器适配。

其中 `SpellDamageUtil` 仍为历史平台伤害工具；本次没有改变它的伤害和事件顺序。当前 gameplay 并无真实代码调用它（只有法术接口文档示例）；以后 gameplay 引用它，即使其 Forge 依赖在存量清单中，严格传递检查仍会失败。需要使用时应先拆出伤害服务契约与平台事件／生命值适配。

客户端的 Forge import 本身并非根约束禁止的依赖。此处限制新增耦合，而非声称现有客户端已脱离 Forge。旧内容目录也没有因为参与扫描就整体变为可迁移模块。

## 本次入口调整

- `IGeoResources`、自动实体／投射物标记及 billboard 标记移到 `api/render`，调用方同步迁移，不保留旧包别名。
- `AnomalyType` 只声明种类与数值；`platform/forge/anomaly/AnomalyEffects` 在使用时解析相同的适应效果。
- GUI shader 类型字符串由 `api/render/GuiEffectTypes` 共享，InfPlayerEntity 服务端路径不再引用 GuiShaderManager。
- 四个通用 S2C 包在检查接收方向、入队后，经物理客户端守卫调用 `client/network/CommonPacketHandlers`。原客户端应用逻辑保持；异常方向现在直接忽略。未改注册顺序、消息 ID、字段布局、action 或协议版本 3。
- 移除 ICustomOutline 的无覆写旧 ShaderInstance 回调及其空调用，保留现行 shader key、颜色与宽度声明。格挡接口不再通过 ModUtils 获取日志；StoryFonts 不再通过 Forge 模组入口获取命名空间。

## 验证

`testArchitectureChecker` 是独立 JDK 回归用例，在新门禁之前自动执行，覆盖 API 越界、工具多跳、同包／通配符／静态引用、持久化扩展、网络守卫、注释／字符串、未知目录及存量清单增删。它不受 `-x test` 影响，也不会启动 Minecraft。

```powershell
.\gradlew.bat checkArchitectureDependencies --offline --console=plain
.\gradlew.bat build -x test --offline --console=plain
```

运行与视觉验收仍由用户负责。
