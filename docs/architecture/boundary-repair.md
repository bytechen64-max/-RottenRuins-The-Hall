# 新增模块的依赖边界修复

## 回旋镖

`api/boomerang/IBoomerangItem` 的回调参数使用公开的 `BoomerangFlight`。
飞行契约暴露 Projectile、物品快照与返航行为，不引用具体 BoomerangEntity。
现有实体实现契约，投掷／命中／返航的回调时机和投掷数值不变。
原实体／物品仍在历史内容目录，本轮没有为了改名移动队友正在调整的文件。

## 剧情记忆与传输

- `core/story/StoryMemory` 保存 flags、previous、cooldowns、一次性标记与已记录键，脱离 NBT。
- `gameplay/story/StoryDirector` 只负责条件、选取、延迟与提交，通过 StoryRuntime 加载／保存记忆。
- `platform/forge/story/StoryMemoryStore` 负责 Forge 玩家持久化数据、现有键和重生复制；不缓存玩家对象。
- `gameplay/story/StoryMessage` 表达单段字幕、清除、定时序列与完整剧情播放意图。
- `platform/minecraft/story/StoryPacketCodec` 负责原消息字段和 JSON／NBT 编码。
- `ForgeStoryServices` 编码并发送；血肉床保留其已编码快照的存档方式，直接使用平台 codec 与快照发送入口。

每次读取返回独立记忆对象，flag／commit 修改后立即保存，不引入需要停服刷盘的内存缓存。
一次性键仍写在原玩家持久化根节点，保留对外部已有触发器的可见性；重生仍复制已记录键、当前规则键和开场标记。
规则重载不替换已排队任务所持有的剧情定义；延迟、冷却时钟与随机选取顺序沿用原逻辑。

## 刀光

`gameplay/combat/slash/SlashEngine` 管理玩家状态、连段、冷却、延迟结算与效果编排。
`SlashCriticalPolicy` 管理原暴击资格规则。`SlashRuntime` 提供攻击许可、暴击事件解析和视觉广播服务。
HallMod 构造时安装 ForgeSlashServices；服务不保存玩家或服务器引用。

`platform/forge/combat/slash/SlashServer` 只订阅攻击、tick、登出、停服事件并转发。
`ForgeSlashServices` 包装 Forge 攻击钩子及防重复伤害的重入标记，`SlashCriticals` 负责 Forge 暴击事件。
保留原事件保护、命中上限、伤害回调、附魔、击退、耐久、音效、广播和结束顺序。
SlashNetwork 的通道、消息布局与客户端表现不因本轮结构调整改变。

## 自动约束与验证

新增 `checkExtensionApiDependencies`，检查 boomerang／combat API；magic API 继续由原检查覆盖。
新增 `checkStoryDependencies`，禁止剧情 gameplay 使用 NBT、网络 buffer 或玩家持久化扩展。
扩展 `checkGameplayDependencies`，检查客户端类型以及没有 Forge import 的 getPersistentData／PERSISTED_NBT_TAG。
上述任务均加入 compileJava 与 check，普通 build 自动执行。

后续已增加[全源码依赖门禁](dependency-gates.md)，检查 gameplay 工具依赖，并修复 anomaly／自动渲染 API 的实现引用。原有检查继续保留；语法树检查仍不等价于完整传递链接或运行验证。
本轮只编译打包和静态核对，GameTest 源码更新迁移调用点并参与编译，运行与视觉验收继续由用户负责。

跨 agent 规则见根目录 [AGENTS.md](../../AGENTS.md)。
