# JSON 剧情系统

## 分层

| 目录 | 职责 |
| --- | --- |
| `core/story` | 纯 JDK 条件匹配、权重选择、避免连续重复 |
| `gameplay/story` | 不可变剧情声明与目录、延迟调度、冷却/一次性标记/条件标记、播放协议 |
| `platform/forge/story` | JSON 发现/解析/校验、资源重载、生命周期清理、网络服务实现 |
| `event/impl` | 登录、实际死亡和重生转为剧情事件，不再选择硬编码台词池 |
| `client/story` | 本地翻译、分页、播放冲突、空间文本、打字/退场/光影兼容 |

`StoryRuntime` 在 common setup 安装 Forge 传输实现，玩法不引用 Forge/platform 实现。纯核心继续以空 classpath 编译。

## 数据包

Minecraft 1.20.1 的数据包使用 `pack_format: 15`：

```json
{"pack":{"pack_format":15,"description":"自定义空间剧情"}}
```

```text
data/<namespace>/stories/<path>.json
data/<namespace>/story_rules/<path>.json
```

支持子目录。例如 `data/example/stories/intro/arrival.json` 的 ID 是 `example:intro/arrival`。
服务端启动和 `/reload` 读取所有已启用数据包。相同完整路径由最高优先级包整文件覆盖，不合并同名文件的数组。
资源按完整 ID 排序，规则按 `priority` 从高到低、ID 字典序决胜，每个事件只选择第一条符合条件的规则。

任一文件或引用出错，重载失败，整张旧剧情/规则快照保持可用。错误包含资源路径、数据包名和字段/页号。
删除文件或禁用包后成功重载会移除相应内容；用 `{"enabled":false}` 覆盖文件可禁用它。禁用剧情时也须删除、禁用或修改引用它的规则。
重载不改变已经发送的页面、已选择的延迟任务或正在运行的仪式；下一次触发使用新定义。
停止服务器会清空目录和待播任务，进入另一个世界重新加载。

## 剧情文件

```json
{
  "font": "wenkai",
  "timing": "autoplay",
  "conflict": "replace",
  "priority": 10,
  "placement": {"distance": 1.6, "height": 0, "angle": 0},
  "shadow": false,
  "entry_speed": 10,
  "exit_speed": 16.67,
  "hold_ticks": 120,
  "pages": [
    {"text": {"translate": "example.story.arrival"}},
    {"text": {"text": "一段字面文本"}, "font": "mashan", "placement": {"angle": 45}}
  ]
}
```

| 字段 | 默认值与范围 |
| --- | --- |
| `enabled` | `true` |
| `font` | 不指定时使用 Component 自身字体及剧情默认字体；可用 `default/wenkai/xiaowei/mashan`，也可写完整字体 ID |
| `timing` | `readable`：观看时推进；`autoplay`：转头仍推进；`clocked`：服务端世界时间线 |
| `pause_when_unseen` | 未提供 timing 时的简写，默认 true；false 等于 autoplay。显式 timing 优先 |
| `conflict` | `replace` 替换当前正文和队列，`queue` 排到队尾，`ignore` 已有正文时忽略 |
| `priority` | 默认 0，范围 ±1000000。低优先级 JSON 不能替换高优先级正文，queue 仍可排队 |
| `placement.distance` | 默认 1.6，范围 1..8 格 |
| `placement.height` | 默认 0，范围 -2..2 格，基于玩家眼高 |
| `placement.angle` | 默认 0，范围 -180..180 度，基于本段捕获的玩家水平朝向 |
| `shadow` | 不指定则使用客户端阴影配置 |
| `entry_speed/exit_speed` | 不指定则使用客户端速度配置；提供时范围 0.1..100 阅读单位/秒 |
| `hold_ticks` | 普通模式完整显示后的停留，默认 120，范围 20..12000 tick |
| `duration_ticks` | 仅 clocked 模式，要求每页有效值 20..12000；包含打字、停留与退场 |
| `recapture_anchor` | 首段默认 true，后续默认 false；true 时该页重新按玩家当前朝向定位 |
| `pages` | 1..32 页，各页 `text` 为必填的非空原版 Component |

页内可覆盖 font、placement、shadow、entry_speed、exit_speed、hold_ticks、duration_ticks、recapture_anchor。
页 placement 逐字段继承顶层值。例如仅覆盖 angle 会继续继承顶层 distance。不同页面可环绕玩家布置，而不必跟随转头。
页内其余字段不继承 Component 的隐式翻译字符串：网络始终保存翻译键，客户端选语言。
未知字段、非整数 tick、非法枚举、非法 ID、越界速度、空页和非法 Component 都会报错。
每页序列化 Component（含字体）最大 4096 字符。客户端每次最多接收 128 个实际排版子页，超限的新定义整段不接收。

一中文字符或一英文单词为一个阅读单位，单词内部字母依次快速展开。缩放和物理弹道仍用实际 tick，不随总字数缩放。
普通页面自动折行为最多三行。clocked 页发生额外分页时，子页均分该页的 duration_ticks；只调整停留，不自动加速动作。
极长翻译或极慢速度仍受 clocked 截止时间限制。clocked 不允许 queue，以免起止时间在等待时过期。

语言条目在客户端资源包的 `assets/<namespace>/lang/zh_cn.json` 与 `en_us.json` 中。数据包提供服务端剧情，客户端资源包提供字体和翻译；数据包不能直接替换 assets。
`/reload` 读取剧情文件；翻译/字体文件变化使用 F3+T。语言切换会重新翻译、排版当前内容和待播内容。

## 触发规则

```json
{
  "event": "respawn",
  "priority": 50,
  "delay_ticks": 40,
  "cooldown_ticks": 1200,
  "once_key": "example:first_warning",
  "repeat_group": "example:warnings",
  "avoid_repeat": true,
  "conditions": {
    "min_deaths": 2,
    "cause": "fall",
    "dimension": "minecraft:overworld",
    "required_flags": ["example:met_guide"],
    "forbidden_flags": ["example:finished"]
  },
  "choices": [
    {"story": "example:warning_one", "weight": 3},
    {"story": "example:warning_two", "weight": 1}
  ]
}
```

- event：`login`、`respawn`、`fleshbed_start` 是内置事件；其他非空字符串可由函数/命令/Java 接入触发。
- fleshbed_start 规则要求 delay_ticks 为 0，且候选全部为 clocked 剧情；仪式必须同步启动，非法引用在重载时拒绝。
- priority：默认 0，范围 ±1000000，选择规则的优先级，与剧情文件中的播放优先级分别生效。
- delay_ticks：默认 0，范围 0..12000；按接收玩家实际服务端 tick 倒计时。
- cooldown_ticks：默认 0，非负整数，按服务端主世界时间计算。
- once_key：可省略；每名玩家、每个存档只派发一次，使用名字空间 ID 作为持久标记。
- repeat_group：默认规则 ID；相同组共享上一段剧情记录，用于跨规则避免连续重复。
- avoid_repeat：默认 true，有其他正权重候选时排除上一段剧情。
- choices：1..256 个引用；weight 默认 1，可为 0，总和必须为正且不超过整数上限，目标剧情必须存在且启用。
- conditions：所有条件同时满足。支持 min_deaths/max_deaths、min_streak/max_streak、max_death_interval_ticks、cause、dimension、required_flags/forbidden_flags。
- deaths 是本存档累计实际死亡数；streak 是三分钟内连续死亡计数；interval 是距上次实际死亡的 tick，首次为整数最大值。
- cause 内置为 fall、drown、mob、normal；首次和连续死亡通过条件与规则优先级处理，没有独立硬编码池。
- 无 deaths/streak 上限时为整数最大值，下限默认 0；不指定 cause/dimension 时不限制，不指定 flags 时为空集合。

once、冷却和上一段记录在向客户端派发时提交。客户端 conflict/priority 可以拒绝展示，派发记录仍算消费；这类规则不是任务完成判据。
相同规则待播时不会重复预约。死亡、退出或切换维度取消待播任务，不消费尚未派发的一次性标记。
条件在事件选择时评估，延迟任务保留当时选择；资源重载不重新抽签。

## 命令与 Java 接入

均需权限 2，支持命令方块/函数和选择器：

```mcfunction
/rotten_ruins story list
/rotten_ruins story play @s rotten_ruins:opening
/rotten_ruins story play @s rotten_ruins:respawn/fall_0
/rotten_ruins story flag @s example:met_guide true
/rotten_ruins story trigger @s example:arrival
/rotten_ruins story clear @s
```

play 直接播放文件定义，不执行规则的条件、延迟和消费记录；trigger 执行事件规则（命令默认无死亡上下文）；flag 设置本玩家的持久条件标记。
clear 同时取消服务端延迟任务和客户端正文/队列。旧 show/append/show_font/append_font/preview 命令保留，用于字面文本和字体检查。

Java 调用 `StoryDirector.trigger(player, event, StoryDirector.context(player))`；有死亡上下文时使用 context 的扩展重载。
平台外的玩法代码仅查询 StoryCatalog/StoryDirector，不读取文件或 JSON。

## 已迁移的内置内容

- opening：60 tick 延迟，保留旧 opening_story_seen 存档标记。
- respawn/*：13 段文楷中英文台词，规则决定首次/快速连续/死因/普通池、40 tick 延迟与防重复。
- fleshbed：默认四页各 200 tick，剧情优先级 100。仪式开始从 fleshbed_start 规则选择 clocked 剧情，总时长来自该定义。

仪式启动时保存完整播放快照、会话 UUID 和总时长到方块实体 NBT。热重载、退出或世界重载不会改变正在进行的仪式；重连按原来的绝对时钟恢复。
老存档没有快照时仍兼容原有 800 tick 仪式与四页时序。喂养和召唤柱生成逻辑不由 JSON 剧情执行。

可安装示例见 `examples/story-pack`。启用后设置 example:met_guide 标记并触发 example:arrival 即可测试。

## 验证

StoryJsonGameTests 使用真实资源管理器检查数据包优先级、子目录、页继承、无效重载回滚、禁用/删除、跨文件引用、条件标记、冷却、一次性记录、任务快照、播放协议和命令。
StoryTextGameTests 检查迁移后的首次登录、重生延迟、死因池、避免重复、存档记忆和末地返回。
客户端实机检查：有/无光影、F1/聊天、语言切换、多页环绕位置、queue/ignore/priority，以及仪式进行中 /reload 后重连。
