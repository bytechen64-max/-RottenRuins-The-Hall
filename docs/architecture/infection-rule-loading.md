# 感染与扩散规则加载

## 代码边界

- `core/infection/rules/RuleIndex`：使用字符串 ID 查询规则的不可变索引，不引用 Minecraft 或 Forge。
- `core/infection/rules/InfectionRules`：持有感染、扩散两张表的共同快照。重载成功后一次性替换，读取过程中不清空旧表。
- `platform/forge/infection/InfectionRuleReloadListener`：负责当前版本的资源发现、JSON 校验、资源重载和服务端生命周期接入。
- `gameplay/infection` 下的 `EvolutionManager`、`BlockSpreadManager`：负责查询规则、选择目标和执行实体转化或方块替换，不扫描目录或 jar。

加载流程为：服务端资源管理器 → 解析和校验两类文件 → 构建新快照 → 发布快照 → 游戏逻辑查询。
将来迁移版本时，资源加载与生命周期 API 的改动集中在平台接入层；规则索引和现有 JSON 数据结构可以继续复用。
实体、方块执行代码仍然依赖 Minecraft，本次没有将整个感染系统改成跨版本库。

## 数据包路径与覆盖顺序

原有目录和字段保持不变：

```text
data/<namespace>/infcore_evolution/<name>.json
data/<namespace>/infcore_blockspread/<name>.json
```

支持子目录。加载发生在服务端启动及 `/reload`，只读取已启用的数据包和模组资源。

同一个完整资源路径由优先级最高的数据包提供，整个文件替换，而非与低优先级版本逐条合并。
例如覆盖 `data/rotten_ruins/infcore_evolution/default.json` 时，应保留仍需要的其他条目。
使用 `{"entries":[]}` 可以清空该文件提供的规则，但其他文件仍然可以提供规则。

不同文件按照完整资源 ID（`namespace:path`）的字典序依次处理。同一个 `type + source` 出现多次时，后面的完整条目替换前面的条目。
可以用同命名空间下的 `zz_custom.json` 覆盖 `default.json` 中的部分条目，避免复制整个文件。
此顺序为显式约定，不依赖操作系统目录枚举顺序。

原来的 `replace` 字段继续接受。旧实现无论该字段真假，都会以新条目替换相同 `type + source`，本次保留这一实际行为。

## 示例

Minecraft 1.20.1 数据包的 `pack.mcmeta`：

```json
{"pack":{"pack_format":15,"description":"王庭规则调整"}}
```

`data/rotten_ruins/infcore_blockspread/zz_custom.json`：

```json
{
  "replace": false,
  "entries": [
    {
      "type": "rotten_ruins:spread",
      "source": "minecraft:dirt",
      "dropResources": false,
      "results": [{"target":"rotten_ruins:hall_dirt","weight":100}]
    }
  ]
}
```

启用数据包后执行 `/reload`。修改、删除文件或禁用数据包后再次重载，规则会根据当前资源重新构建，不残留已删除的条目。

## 错误与兼容

- 文件必须包含 `entries` 数组；条目必须具有合法的 `type`、`source` 和 `results`。
- 目标必须具有合法 ID，权重不能为负，同一条目总权重不能超过 Java 整数上限。
- 空目标列表和零权重继续接受；感染在没有可选目标时仍走已有的兜底逻辑。
- ID 格式会在加载时校验，目标是否存在仍由执行层检查，保持对可选模组内容的既有行为。
- 任一感染或扩散文件解析、校验失败，本次规则加载失败，日志会给出资源路径、数据包名称和原因，两张活动规则表保持原样。修正文件后可再次 `/reload`；首次启动时没有可用旧快照，错误必须先修复。
- 程序注册的感染兜底档位不受资源重载影响；存档、能力键、难度数据和视觉效果不在本次改动范围内。
- 管理器的 `forceReload()` 保留，须在运行中的服务端线程调用，现会共同重读当前服务端的两类资源。
- `mergeOverrides()` 仍可运行时覆盖进化条目；与原实现一样，后续资源重载会替换这类临时覆盖。长期配置应写入数据包。
- 规则列表现在不可修改。调用方如需调整规则，应通过数据包或显式覆盖入口，而非修改查询返回的目标列表。
- 服务端完全停止后清空快照，下一次开服或进入其他世界重新加载，避免静态缓存跨世界残留。

## 验证

`gradlew runGameTestServer --offline --console=plain` 包含：

- 既有感染转化、击杀计数、扩散和旧存档格式测试；
- 高优先级数据包覆盖相同路径、子目录发现和快照隔离；
- 六类无效规则的文件定位与加载失败保留原表；
- 实际世界数据包启用、修改、重载失败恢复、文件删除、数据包禁用及兜底规则保留。

负面测试会主动生成错误文件，因此测试日志中会出现预期的 `Invalid infection rules` 错误。最终测试结果应为全部通过，临时数据包会被清理。
