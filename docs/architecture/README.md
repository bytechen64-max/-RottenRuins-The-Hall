# 架构文档入口

当前以 Minecraft 1.20.1 为维护版本，目标是以后整体迁往一个新版本，目标版本和加载器尚未确定。
核心方向是保留规则、状态和声明，集中处理游戏与平台接入；视觉效果尽量保持。

## 按工作内容查阅

所有 agent 修改前遵守根目录 [AGENTS.md](../../AGENTS.md)。新增模块的依赖修复见 [回旋镖、剧情与刀光边界](boundary-repair.md)。

| 工作 | 文档 |
| --- | --- |
| 调整 hall 攻击异常、脱战衰减与骷髅 ghost 弹体 | [异常攻击与追踪弹体](anomaly-attacks.md) |
| 调整破碎之地、折叠原野、穿孔大陆 | [庭园区域地貌](surreal-terrain.md) |
| 检查全源码依赖、工具传递依赖与客户端存量耦合 | [全源码依赖门禁](dependency-gates.md) |
| 查看已停用的王座与海冠方案 | [庭园巨构历史方案](garden-monuments.md) |
| 调整血肉庭园与王庭海洋的矿脉 | [庭园矿脉生成](garden-ores.md) |
| 查看运行时接入、内容与平台边界 | [运行时与内容分层](runtime-boundaries.md) |
| 接入武器挥砍、范围命中、回调和刀光 | [武器挥砍接口](../slash-api.md) |
| 准备升级版本、了解剩余耦合 | [版本迁移清单](migration-checklist.md) |
| 新增法术、设置基础值、组合多个流派 | [法术定义与物品接入](spell-definition.md) |
| 调整法强平均规则、法杖匹配和混合流派展示 | [魔法数值与混合流派](magic-rules.md) |
| 扩展数值 provider、事件和查询入口 | [魔法类型与求值边界](magic-definitions.md) |
| 调整蓄力、维持和起手校验 | [施法状态与游戏执行](casting-state.md) |
| 调整法力、共享冷却、存档和同步 | [法力与冷却状态](magic-state.md) |
| 调整法术槽、轮盘绑定和越界物品处理 | [法术物品槽与轮盘配置](spell-loadout.md) |
| 调整感染系统职责和依赖 | [感染系统分层](infection-layers.md) |
| 制作感染／扩散数据包、使用重载 | [规则加载与覆盖说明](infection-rule-loading.md) |
| 调整假死时序和爆体缩放 | [假死与爆体组件](entity-burst.md) |

## 当前边界

`core` 通过 Java 17 空 classpath 独立编译，只依赖 JDK 与其他核心类。
`gameplay` 使用 Minecraft 类型执行玩法，通过运行时服务获取平台操作；构建禁止直接引用 Forge、平台实现、具体内容和旧注册目录。
`platform` 承接已拆出的加载器、存储与网络适配；`api` 是游戏扩展契约，可以包含游戏／Forge 类型。
魔法内容已移至 `content/magic`；其他 `overworld/registry` 子系统仍包含内容实现，详见迁移清单。

普通构建保留原有检查，并扫描全部维护源码（暂不含 mexin／mexinlauncher），严格检查整个 API、gameplay 工具依赖和通用网络客户端守卫；客户端与历史内容耦合按逐文件／逐类型清单限制增长，详见全源码依赖门禁。
当前主网络协议为 4。未发布阶段不要求旧开发存档兼容；资源路径和视觉参数仍应谨慎保持。
最近几轮只执行静态检查与编译打包，运行行为与视觉验收由用户进行。
