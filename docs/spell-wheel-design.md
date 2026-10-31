# 魔法轮盘施法界面（HUD）+ 魔法装载界面 · 设计规格

两套新界面，共用「深空星图 + 金色符文」的视觉语言：

| | 魔法轮盘施法界面（HUD） | 魔法装载界面（配置轮盘） |
| --- | --- | --- |
| 形态 | 游戏内 HUD，**按施法键开关**、左键点击方位施法 | 非暂停 `Screen`（和 `MagicScreen` 同类） |
| 一句话 | 8 方位轮盘，按 R 开关 → 瞄准 → 左键释放 | 把魔法池里的**主动**法术绑到 8 个轮盘位 |
| 数据源 | 轮盘映射 + `PlayerMagicPool` + `MagicStats` | 同左 + 背包（只用于提示"未装备"） |
| 入口 | 键位 `key.rotten_ruins.cast_wheel`（默认 `R`） | `MagicScreen` 顶部页签 → `SpellLoadoutScreen` |
| 产物（设计稿） | `build/gui-preview/spell_wheel*.png` | `build/gui-preview/spell_loadout.png` |

> **本文标记约定**
> * `[已实现]` —— 本次设计所依赖的现有源码行为，已逐条核对（见附录 A）。
> * `[建议新增]` —— 需要新写/新注册的类、字段、包、常量。
> * `[规划]` —— 尚未实现的框架内容（尤其是**被动法术**），只作为规格的占位，不能当成已有能力。
>
> 所有数值（耗蓝 / 冷却 / 蓄力 / 伤害）都必须走 `MagicStats` 求值，界面**不读法术物品的基值**
> —— 属性修饰器与附魔都折叠在 `MagicStats` 里（`[已实现]`）。

---

## 零、结论速览

| 议题 | 决定 |
| --- | --- |
| 轮盘形状 | 固定 8 方位（正上 = 位 1，顺时针 1..8），每扇区 45°，绘制留 1.2° 双侧间隙 |
| 轮盘能放什么 | **只放已绑定到轮盘的主动法术**（`SpellKind.ACTIVE`）；被动永不显示、不可绑定 |
| 8 位 vs 现有 5 槽 | 轮盘位是**独立映射**，来源是魔法池里的池槽索引；默认池只有 5 格，所以最多绑 5 个，其余位空置 |
| 空位 | 只画极淡符文插槽（不显示任何技能信息），不参与选中 |
| 手势 | 按 R 打开/关闭 → 鼠标方向选扇区 → 左键点击施法；右键 / `Esc` / 再按 R 关闭；打开期间**不暂停、不锁移动** |
| 三种施法类型 | `IMMEDIATELY` 左键点击即放；`CHARGE` 按住左键蓄力、松开按比例释放；`KEEP` 左键起手、右键 / R 结束维持（实装交互见附录 C.8） |
| 数据落点 | 扩进 `PlayerMagicPool` 的 NBT（`HallMagicWheelSlots` / `HallMagicWheelPage`），复用现有 `MagicPoolSyncPacket` 全量同步 |
| 新网络包 | c2s `WheelCastPacket`（施法手势）+ s2c `WheelCastFeedbackPacket`（服务端结果回执）；配置操作扩展 `MagicPoolActionPacket` 的 action 编号，**不新增包序号** |
| 渲染方式 | GUI **立即绘制**（`BufferUploader.drawWithShader`），守 `MagicGuiShaders` 的三条规矩；不建 `RenderType` |
| 轮盘页 | I / II / III 三个预设页（`[建议新增]`：P0 只暴露第 1 页，P1 完成 3 页与切页） |
| 保存语义 | 所有改动**服务端权威即时生效**；`SAVE` 按钮 = 确认并关闭（见 §6.4 与 §11 待定 3） |

### 0.1 本设计用到的真实法术数据

| 法术 | 注册名 | `MagicType` | `SpellKind` | `UsingType` | 启动耗蓝 | 冷却 | 蓄力 | 维持 | 其它 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 火球术 | `rotten_ruins:fire_ball` | `FIRE` | `ACTIVE` | `IMMEDIATELY` | 40 | 30 tick（1.5 s） | — | — | 直接伤害基值 6；爆炸半径基值 2.5 格，钳 `[0.5, 12]` |
| 审判之矛 | `rotten_ruins:spear_of_judgment` | `DEATH` | `ACTIVE` | `CHARGE` | 100 | 600 tick（30 s） | 40 tick（2.0 s） | — | 伤害基值 50；`minChargeRatio = 1.0`（**必须蓄满**）；射程 48 格 |
| 抑制场 | `rotten_ruins:suppression_field` | `VOID` | `ACTIVE` | `KEEP` | 80 | 240 tick（12 s） | — | 50 / 秒 | 每 5 tick 触发一次；每脉冲伤害基值 20；半径基值 5 格，上限 24 |

数据出处：`FireBall` / `SpearOfJudgment` / `SuppressionField` 三个类的构造器与常量（`[已实现]`）。

> 三支现有法术**都没有**声明 `needsTarget() == true`（`[已实现]`）：审判之矛用的是
> `MagicHandle#resolveAimPoint` 取"准星落点"（一个坐标），不是锁定生物目标。
> 所以 `CastResult.NO_TARGET` 是框架为未来"锁定型"法术预留的结果，当前不会出现在轮盘里；
> 施法反馈表（§5.4）保留它只是为了不漏映射。

> **被动法术**：`SpellKind.PASSIVE` 在源码里只是一个**分类轴**（`[已实现]`），
> 被动效果的驱动（槽位 tick / equip / unequip）属于后续框架（`[规划]`）。
> 本文出现在图鉴里的两个被动示例 **Ember Affinity**、**Void Attunement** 只是设计占位，
> 实机不存在这两件物品，不要写进 lang 的 `item.*`。

---

## 一、目标与体验原则

### 1.1 为什么是轮盘

现有两条施法路径：

1. **右键法杖**（`StaffBase#use` → `MagicHandle.handleUseStart`）：只放"当前选中格"一件法术（`[已实现]`，
   `MagicHandle` 读 `PlayerMagicPool#getSelectedSlot()`）。
2. **魔法星图**（`MagicScreen`，默认 `M`）：管理 5 个法术槽 + 选中格，是**配置界面**，不是战斗界面
   （`[已实现]`）。

缺的是"战斗中快速换着放"这一层：

| 方案 | 问题 |
| --- | --- |
| 用数字键 1..5 选槽 | 现有槽位只有 5 个、没有专用键位；数字键已被快捷栏占用；且 5 格编号没有空间记忆 |
| 反复开 `M` 换选中格 | 每次都要离开战斗节奏，界面还是悬浮星图（打不开时不能瞄准） |
| 右键法杖挨个切 | 只能顺序切，且每切一次都要放一次 |

轮盘解决的是**"一次手势内完成选法术 + 瞄准 + 释放"**：8 个方位是固定的肌肉记忆
（上=1 顺时针），45° 扇区在 640×400 画布上每个扇区仍有 ~110 px 弧长，足够放 16×16 图标 + 状态角标。

### 1.2 不打断战斗的四条原则

| 原则 | 落地要求 |
| --- | --- |
| 不暂停 | HUD 不是 `Screen`，`mc.screen` 保持 `null`，世界继续 tick、玩家能移动/受击 |
| 不夺相机 | 鼠标仍处于 grabbed 状态；轮盘的选择方向由**鼠标增量**驱动（虚拟光标），不释放鼠标 |
| 不遮视野太久 | 打开态只在"按住"期间存在；`KEEP` 起手后轮盘**收拢为底部维持条**（见 §5.1 `MAINTAIN`） |
| 失败要立刻可见 | 蓝不足 / 冷却 / 未蓄满在**扇区与中心**就给出状态，不等到松手才用聊天栏报错（§5.3） |

### 1.3 和现有系统的关系

```
                        ┌──────────────────────────────┐
   背包（法术物品） ───► │ MagicScreen（M，星座界面）    │
                        │  · 5 格星座槽（可扩容到 64）  │
                        │  · 放入/取出/移动/选中        │
                        └──────────────┬───────────────┘
                                       │ 魔法池（PlayerMagicPool）
                                       ▼
   ┌──────────────────────┐   ┌──────────────────────┐
   │ SpellLoadoutScreen   │◄──│ 轮盘映射 WheelSlots   │
   │ （WHEEL 页签进入）    │   │ 3 页 × 8 位 → 池槽索引│
   │ 绑/卸/自动填充/重置   │   └──────────┬───────────┘
   └──────────────────────┘              │ 只引主动法术
                                         ▼
   ┌──────────────────────┐   ┌──────────────────────┐
   │ 轮盘 HUD（R 开关）    │──►│ WheelCastPacket      │
   │ 选位 → 释放           │   │ → MagicHandle.*      │
   └──────────────────────┘   └──────────────────────┘

   右键法杖 ──► MagicHandle.handleUseStart（读 selectedSlot）—— 保留，不受影响
```

三条边界：

* **轮盘不新增"法术来源"**：能上轮盘的法术必须已经在魔法池里（`MagicStats.isSpell(stack)` 为真），
  背包里未装备的法术不能在轮盘里出现（装载界面只给"未装备"提示）。
* **轮盘不改变被动语义**：被动法术照旧"放进魔法池就生效"（`[规划]` 的框架负责驱动），
  轮盘与它无关；`MagicHandle` 遇到被动返回 `CastResult.PASSIVE_SPELL`（`[已实现]`）。
* **同名共享冷却**：冷却键是法术物品注册名（`MagicStats.magicId`），
  所以"同一法术放在 1 号池槽还是 3 号池槽"是同一个冷却（`[已实现]`）。轮盘直接复用这条规则——
  同一法术在不同轮盘位的冷却显示天然一致。

### 1.4 非目标

* 不做手柄径向菜单（P2 预留）。
* 不做轮盘内的法术"拖拽排序"（排序在装载界面做）。
* 不做"每个轮盘页独立冷却"（冷却按法术注册名共享，见上）。
* 不替代 `MagicScreen`：星座界面继续负责槽位管理（放入 / 取出 / 移动 / 选中格），
  装载界面只负责"映射"。两边都改同一个 `PlayerMagicPool`，服务端权威一致。

---

## 二、信息架构与入口

### 2.1 三层信息架构

| 层 | 界面 | 玩家问题 | 频率 |
| --- | --- | --- | --- |
| 战斗层 | 轮盘 HUD | "我现在要放哪个？" | 每场战斗（秒级） |
| 配置层 | 魔法装载界面 | "哪 8 个法术上轮盘、放哪个方位？" | 每次准备/升级（分钟级） |
| 槽位层 | `MagicScreen` 星座界面 | "法术放进哪个魔法槽、当前选中哪个？" | 低频（获得新法术时） |

三层共用同一份服务端状态，界面只做"申请 → 服务端校验 → 全量回推"，从不自己改数据
（`[已实现]`，`MagicScreen` 的既有约定）。

### 2.2 键位

| 键位 key | 建议默认 | 上下文 | 说明 |
| --- | --- | --- | --- |
| `key.rotten_ruins.open_magic` | `M` | `IN_GAME` | 已有：打开魔法星图（`[已实现]`） |
| `key.rotten_ruins.cast_wheel` | `R` | `IN_GAME` | `[建议新增]`：**按住**打开施法轮盘 |
| `key.rotten_ruins.open_wheel_loadout` | 未绑定 | `IN_GAME` | `[可选]`：直接打开装载界面；默认不绑，入口以 `MagicScreen` 页签为主 |
| `key.categories.rotten_ruins` | — | — | 已有分类名，轮盘键位归入同一分类（`[已实现]`） |

* 为什么默认 `R`：原版 `R` 未占用（`F` 是副手交换、`M` 已给星图），离 WASD 近、按住不别扭；
  键位在「控制」里可改，冲突时玩家自己会改。
* 备选：鼠标侧键（`InputConstants.Type.MOUSE` + `GLFW_MOUSE_BUTTON_4`）手感更好，
  但要先做设备存在性检测，P1 再评估。
* **不能用 `KeyMapping#consumeClick()` 做按住检测**：它只清按下沿队列，拿不到"按住多久/何时松开"。
  轮盘用 `KeyMapping#isDown()` 每 client tick 读一次（`[建议新增]` 的 `WheelHudInput`）。
  这与 `MagicClientHandler` 用 `consumeClick()` 打开 `MagicScreen` 的写法不冲突（两者不同键）。

### 2.3 装载界面入口：新 Screen + `MagicScreen` 页签

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A. 把轮盘配置塞进 `MagicScreen` | 少一个 Screen 类 | `MagicScreen` 是**星座折线布局**（`FIVE_SLOT_OFFSETS`），塞 8 方位轮盘要引入第二套布局/第二套拖放语义；类会膨胀到不可维护 | ✗ |
| B. 新建 `SpellLoadoutScreen`，`MagicScreen` 顶部加页签入口 | 布局独立（左图鉴 / 中轮盘 / 右详情）；复用 `MagicScreen` 的星图 shader 与配色；职责清晰 | 多一个 Screen 类 + 一个入口按钮 | ✓ **采用** |
| C. 只给键位、无界面入口 | 实现最少 | 玩家找不到 | ✗ |

**采用 B**，并明确：

* `MagicScreen` 顶部中央（标题行右侧）加一个 `WHEEL` 页签按钮（`[建议新增]`），
  点击后 `minecraft.setScreen(new SpellLoadoutScreen())`；从装载界面返回时回到 `MagicScreen`
  （或直接关回游戏，见 §6.4）。
* 装载界面**不自带**槽位编辑能力：要放入/取出法术仍回 `MagicScreen`。左图鉴只展示"魔法池里已装备的主动法术"，
  背包里未装备的主动法术显示为灰色卡片 + `EQUIP IN CONSTELLATION FIRST` 提示（点击不响应）。
* `SpellLoadoutScreen#isPauseScreen()` 返回 `false`（与 `MagicScreen` 一致，`[已实现]` 同类写法），
  游戏继续跑；但**编辑期间不接受轮盘手势**（`mc.screen != null` 时轮盘不打开，见 §2.5）。

### 2.4 入口状态图

```
       游戏内
         │ 按 M                        ┌────────────────────────┐
         ├────────────────────────────►│ MagicScreen（星座）     │
         │                             │  顶部页签：CONSTELLATION│
         │                             │            WHEEL ───────┼──┐
         │                             └────────────────────────┘  │
         │                                                          ▼
         │                                              ┌───────────────────────┐
         │                                              │ SpellLoadoutScreen    │
         │                                              │  ESC / SAVE → 关回游戏 │
         │                                              │  BACK → 回 MagicScreen │
         │                                              └───────────────────────┘
         │ 按住 R
         └────────────────────────────► 轮盘 HUD（不创建 Screen）
```

### 2.5 什么时候轮盘**不**打开（按优先级）

| 条件 | 判定 | 反馈 |
| --- | --- | --- |
| 已有 Screen 打开 | `mc.screen != null` | 不响应（键位在 `Screen` 里本来也不触发） |
| 玩家死亡 / 旁观 | `player.isDeadOrDying()` 或 `player.isSpectator()` | 不响应 |
| F1 隐藏 GUI | `mc.options.hideGui` | 不响应 |
| 手上没有法杖 | 主手物品 `instanceof StaffBase` 为假 | 准星下方提示 `HOLD A STAFF`（1.5 s），不打开 |
| 正在施法中（右键蓄力/维持） | `MagicHandle.peekState(player)` 非 `IDLE` | 提示 `ALREADY CASTING`，不打开 |
| 魔法池里没有任何主动法术 | 轮盘映射全空，或映射指向的法术均无效 | 打开反馈但**不进入瞄准态**：中心提示 `NO ACTIVE SPELLS`（2.5 s）后自动关闭 |
| 有绑定但全部冷却 / 全部蓝不足 | 逐位计算后无一可放 | **仍然打开**（让玩家看到读秒与原因）；松手不释放，中心显示 `ALL SPELLS UNAVAILABLE` |

> 最后两条刻意区分：前者是"没配置"，打开一个空轮盘只是闪一下，不如直接给一句话；
> 后者是"配置了但暂时不能用"，玩家需要看到"还差几秒 / 还差多少蓝"，所以必须打开。

---

## 三、界面布局

### 3.1 画布语义（设计稿 = 实机 GUI 坐标）

* 设计稿画布 **640×400 逻辑像素** = 1280×800 窗口 @ GUI scale 2（`tools/gui-preview` 默认，
              `-Scale 2` 原生渲染 1280×800 PNG —— 布局用 GUI 逻辑坐标，光栅化在物理分辨率）。
* 坐标原点左上、`y` 向下，单位就是 `GuiGraphics` 的 GUI 坐标；颜色一律 ARGB int。
* 字体是 Minecraft 8px 位图字体，**行高 9**；设计稿里只能出现拉丁字符
  （中日韩会画成占位方块），所以界面里的英文文案同时就是 lang 的 `en_us` 值。
* 实机必须**按屏幕中心/底边锚定 + 统一缩放**，不能写死 640×400（见 §3.5）。

### 3.2 轮盘 HUD · ASCII 布局

```
                         位 1（正上方，中心角 −90°）
                                  ▲
                                  │
            位 8 ◄───────┐        │        ┌───────► 位 2
           （左上）       │   ╭────┴────╮   │        （右上）
                          └───┤  中心   ├───┘
            位 7 ◄────────────┤  核心   ├────────────► 位 3
           （正左）            ╰────┬────╯            （正右）
                                   │
            位 6 ◄───────┐         │         ┌───────► 位 4
           （左下）       │         │         │        （右下）
                          └─────────┴─────────┘

          扇区：内半径 58 → 外半径 142，每 45° 一个；外圈再叠 60 刻度符文环
```

屏幕 chrome 的位置（同一 640×400 画布，按 §3.3 坐标表）：

```
┌────────────────────────────────────────────────────────────────────────────┐ 0
│  ┌──┐                                       WHEEL  ●  ○  ○        ┌──┐      │ 14
│                    A R C A N E   W H E E L                  （2x，居中）     │
│          HOLD [R]  ·  AIM  ·  RELEASE TO CAST              （1x，居中）      │ 34
│                                                                            │
│                     （轮盘本体占 y ≈ 62 .. 346 的圆形区域）                 │
│                                                                            │
│                        MANA   348 / 1000                  （居中，1x）      │ 353
│                  ▕██████████████░░░░░░░░░░░░▏             （250×8，居中）    │ 366
│   RELEASE TO CAST                                       RMB  CANCEL          │ 380
└────────────────────────────────────────────────────────────────────────────┘ 400
```

> 真实扇区是 8 个等分环扇（每 45°），上图为**示意图**，不按角度精确绘制；
> 精确角度见 §3.4。空位只画暗色圆底 + 程序化小符文（`WheelKit#runeGlyph`），不画图标/名字/耗蓝。

### 3.3 轮盘 HUD · 640×400 坐标表（设计 token）

基准几何（`WheelKit` / `SceneSpellWheel` 实测值，设计稿以此为准）：

| Token | 640×400 值 | 说明 |
| --- | --- | --- |
| `CX` | 320 | 轮盘中心 X = 画布宽 / 2 |
| `CY` | 204 | 轮盘中心 Y = 画布高 / 2 + 4 |
| `OUTER` | 142 | 扇区外半径 |
| `INNER` | 58 | 扇区内半径 = 核心外沿 |
| 扇区间隙 | 每侧 1.2° | 绘制角 = 45° − 2.4° = 42.6° |
| 图标环半径 | 100 | `(OUTER + INNER) / 2` |
| 图标尺寸 | 33.6（16 × 2.1） | 选中 ×1.12、hover ×1.05（`pop`） |
| 键位徽章半径 | 80 | `INNER + 22`，8 个方位各一枚 |
| 外圈环 | `OUTER+2`（0.9px）、`OUTER+6`（0.45px） | 金色双环 |
| 刻度 | 60 格，`OUTER+3 .. OUTER+9`（每 5 格长刻度） | 缓慢旋转（§4.4） |
| 内圈环 | `INNER−1.5`（0.6px） | 核心外沿收边 |
| 选中指示弧 | `OUTER+3.5 .. OUTER+5` | 只画选中扇区那一段 |
| 蓄力进度环 | `OUTER+9 ± 1.2`，从 −90° 顺时针 | 只在 `CHARGE` 蓄力时出现 |
| 核心圆盘 | 半径 `INNER − 4` = 54 | 深空底 + 金环 + 内环 |
| 核心内文字基线 | 名 −0.52R、流派/类型 −0.10R、状态 +0.20R、提示 +0.44R | 相对核心半径 54 |
| 标题 | 居中 `(320, 14)`，2x | `ARCANE WHEEL` |
| 副标题 | 居中 `(320, 34)`，1x | `HOLD [R] · AIM · RELEASE TO CAST` |
| 页指示 | 右上 `(532, 14)` 起：`WHEEL` + 3 个点（+42、+60、+78） | 点直径 3.2，当前页亮金 |
| 法力条 | `x=195, y=366, w=250, h=8`，圆头（端帽半径 4） | 轨道深空底 + 金描边 + 金色填充 + 8 等分刻度 |
| 法力文字 | 居中 `(320, 353)`，1x 带阴影 | `MANA 348 / 1000` |
| 左提示 | `(20, 380)`，1x | `RELEASE TO CAST` |
| 右提示 | 右对齐 `(620, 380)`，1x | `RMB  CANCEL` |
| 四角装饰线 | 边距 14，长 26，粗细 0.6 | 金色 |
| 虚拟光标 | 菱形 6.5 + 内菱形 3.2 + 外环 10 | 金色，仅打开态可见 |

**交互几何**（`WheelKit#slotAt` 的规则，实机沿用）：

| 判定 | 规则 |
| --- | --- |
| 死区 | 光标到中心的距离 `< INNER × 0.55`（≈ 31.9）→ **无选中**（松手 = 取消） |
| 超界 | 距离 `> OUTER × 1.18`（≈ 167.6）→ 无选中（光标本身钳在这个半径内） |
| 扇区 | `index = round(((atan2(dy,dx) + π/2) mod 2π) / (π/4)) mod 8`，`index+1` 即显示位号 |
| 吸附 | 落进扇区即选中，不做额外吸附动画（选择看的是方向不是坐标） |

### 3.4 八个方位的角度表

屏幕坐标（`0°` = 正右、`90°` = 正下）；方位 1 在正上方（`−90°`）。

| 显示位 | 方位 | 中心角 | 绘制起止角（留 1.2° 间隙） |
| --- | --- | --- | --- |
| 1 | 正上 | −90° | −111.3° .. −68.7° |
| 2 | 右上 | −45° | −66.3° .. −23.7° |
| 3 | 正右 | 0° | −21.3° .. +21.3° |
| 4 | 右下 | +45° | +23.7° .. +66.3° |
| 5 | 正下 | +90° | +68.7° .. +111.3° |
| 6 | 左下 | +135° | +113.7° .. +156.3° |
| 7 | 正左 | +180° | +158.7° .. +201.3° |
| 8 | 左上 | +225°（≡ −135°） | +203.7° .. +246.3° |

> 数据模型里**下标 0..7 = 显示位 1..8**（`WheelKit#slotAngle(i) = −π/2 + i·τ/8`）。
> 界面上的数字、NBT 下标、键位 1..8 全部同一套编号，避免"内部 0 起、界面 1 起"的错位。

### 3.5 实机自适应（不能用固定像素）

设 GUI 逻辑尺寸 `W × H`：

```
S = clamp(min(W / 640, H / 400), 0.62, 1.5)     // 统一缩放系数
CX = W / 2
CY = H / 2 + 4S
OUTER = 142S, INNER = 58S
法力条: w = 250S, h = 8S, y = H − 34S；法力文字 y = H − 47S
底部提示 y = H − 20S；标题 y = 14S；副标题 y = 34S
页指示 x = W − 108S, y = 14S
```

| 场景 | 结果 |
| --- | --- |
| 640×400（预览基准） | `S=1`，与本文坐标表完全一致 |
| 1280×800 @ scale 2 | 逻辑 640×400，同上（主流情况） |
| 1920×1080 @ scale 3 | 逻辑 640×360 → `S=0.9`；轮盘外径 127.8，底部条上移，不裁切 |
| 超宽屏（如 21:9） | `S` 由高度决定，轮盘保持圆形居中，两侧留给世界视野 |

**文字不参与几何缩放**：MC 位图字体固定 8px。规则：

* 身体文字永远 1x；
* 标题 2x（HUD）/ 3x（装载界面标题）用 `PoseStack.scale` 画，不做 1.5x 之类的非整数缩放；
* 核心里的法术名 **2x 仅当放得下**：`font.width(name) × 2 ≤ 核心半径 × 1.7`，否则退回 1x
  （`WheelKit#drawCore` 已有该判断，实机沿用）。

### 3.6 装载界面 · ASCII 布局

```
┌────────────────────────────────────────────────────────────────────────────┐ 0
│                     S P E L L   L O A D O U T          (3x，y=8)            │
│                configure the eight wheel sigils         (1x，y=36)          │
│ ┌──────────────────┐      ╭──── WHEEL PREVIEW ────╮      ┌───────────────┐ │ 48
│ │[ACTIVE][PASSIVE] │      │    ①     ②     ③    │      │   DETAILS     │ │
│ │┌───────────────┐ │      │                      │      │    ┌─────┐    │ │
│ ││▌icon Fireball │ │      │    ⑧    ◉core    ④   │      │    │icon │    │ │
│ ││FIRE 40 · 1.5s │ │      │                      │      │    └─────┘    │ │
│ ││      SLOT 1   │ │      │    ⑦     ⑥     ⑤    │      │   Fireball    │ │
│ │└───────────────┘ │      ╰──────────────────────╯      │  FIRE·ACTIVE  │ │
│ │┌───────────────┐ │        WHEEL I · 3 / 8 SIGILS      │  TYPE  INSTANT│ │
│ ││▌icon Spear ...│ │                                   │  MANA  40     │ │
│ │└───────────────┘ │        Drag a spell onto a slot     │  COOLDOWN 1.5s│ │
│ │   … 最多 7 张 …  │        Right-click to unbind        │  ASSIGNED W1·2│ │
│ └──────────────────┘                                   │ ───────────── │ │
│                                                         │  DESCRIPTION  │ │
│                                                         │   A roaring … │ │
│  WHEEL PAGE [ I ][ II ][ III ]                    [AUTO-FILL][RESET][SAVE] │
│                          M / ESC  CLOSE                                    │
└────────────────────────────────────────────────────────────────────────────┘ 400
```

### 3.7 装载界面 · 640×400 坐标表

| 区域 | x | y | w | h | 内容 |
| --- | --- | --- | --- | --- | --- |
| 标题 | 居中 320 | 8 | — | 27（3x 行高） | `SPELL LOADOUT`，3x |
| 副标题 | 居中 320 | 36 | — | 9 | `configure the eight wheel sigils`，1x 暗色 |
| 左面板（法术图鉴） | 16 | 48 | 188 | 272 | 深空面板 + 金描边 |
| ├ 页签 `ACTIVE` | 20 | 52 | 88 | 16 | 选中态亮金底 + 金字 |
| ├ 页签 `PASSIVE` | 112 | 52 | 88 | 16 | 未选中暗金描边 |
| ├ 页签分隔线 | 20 | 70 | 168 | 1 | `0x66C9A227` |
| └ 法术卡片 | 20 | 80 | 180 | 30 | 最多 7 张，纵向间隔 34（80/114/148/182/216/250/284） |
| 中：预览标题 | 居中 320 | 60 | — | 9 | `WHEEL PREVIEW`（1x，暗金） |
| 中：轮盘预览中心 | 320 | 184 | — | — | `OUTER=96`、`INNER=40`（比 HUD 小一号） |
| 中：轮盘说明 | 居中 320 | 286 | — | 9 | `WHEEL I · 3 / 8 SIGILS BOUND` |
| 中：提示行 1（居中，宽 ≤ 220） | 320 | 300 | — | 9 | `Drag a spell onto a slot` |
| 中：提示行 2 | 320 | 312 | — | 9 | `Right-click a slot to unbind` |
| 中：提示行 3 | 320 | 324 | — | 9 | `Changes apply instantly` |
| 右面板（详情） | 436 | 48 | 188 | 272 | 深空面板 + 金描边 |
| ├ 面板标题 `DETAILS` | 446 | 56 | — | 9 | 1x 暗金 |
| ├ 法术图标 | 530（居中） | 86 | 40 | 40 | 圆形底 + 流派色描边 |
| ├ 法术名 | 居中 530 | 114 | — | 9 | 1x 带阴影（长名截断/换行见 §4.2） |
| ├ 流派 · 分类 | 居中 530 | 127 | — | 9 | 流派色 |
| ├ 属性行 ×4 | 446..614 | 144 / 159 / 174 / 189 | — | 9 | `TYPE / MANA / COOLDOWN / ASSIGNED` 左右对齐 |
| ├ 分隔线 | 446 | 204 | 168 | 1 | `0x66C9A227` |
| ├ `DESCRIPTION` | 446 | 212 | — | 9 | 暗色小标题 |
| └ 描述正文 | 446 | 226 | 168 | ≤ 40 | 最多 4 行 × 10 行距，按词换行 |
| 底部页签标签 | 16 | 326 | — | 9 | `WHEEL PAGE` |
| 底部页签 I/II/III | 16 / 56 / 96 | 336 | 34 × 3 | 18 | 当前页亮金填充，其余描边 |
| 底部按钮 `AUTO-FILL` | 436 | 336 | 58 | 18 | hover 亮金描边 |
| 底部按钮 `RESET` | 500 | 336 | 58 | 18 | 二次确认态见 §6.4 |
| 底部按钮 `SAVE` | 564 | 336 | 58 | 18 | 关闭界面并强制同步 |
| 页脚键位提示 | 居中 320 | 366 | — | 9 | `M / ESC  CLOSE`（暗色） |

* 面板底 = `fillGradient` 深空渐变 + 1px 外描边 + 内衬 1px（沿用 `MagicScreen` 侧栏画法：
  `0xB20A0F1A → 0xC8060912`，描边 `0x66C9A227`，内衬 `0x22C9A227`）。
* 卡片高度 30 与 `WheelKit#drawSpellCard` 的排版一致（名字 y+7、副行 y+19、绑定徽章右上）。
* 详情面板的纵向排布与 `WheelKit#drawSpellDetail` 一致（图标 y+38、名字 y+66、属性行 y+96 起每 15）。
  换行后的描述若超过 4 行，第 4 行末尾加 `…`（不滚动）。

### 3.8 绘制顺序（z-order）

| 层 | HUD | 装载界面 |
| --- | --- | --- |
| 1 | ——（世界照常渲染） | 深空背景（`magic_starfield`，失败退静态星点） |
| 2 | 轮盘背后的加色光晕 `wheel_aura` | 三个面板底/描边 |
| 3 | 外圈双环 + 60 刻度（立即绘制弧线） | 图鉴页签 + 卡片 |
| 4 | 8 个扇区（`wheel_slice`，空位用符文） | 轮盘预览（同 HUD 的 2→8 层，缩小版） |
| 5 | 星图连线（`magic_constellation`） | 轮盘说明文字 |
| 6 | 核心（`wheel_core`，立即绘制） | 右侧详情 |
| 7 | 蓄力环 / 选中外沿弧（立即绘制） | 底部页签/按钮 |
| 8 | 核心文字 + 法术图标 + 键位徽章 + 冷却/蓝不足角标（GUI 批处理） | 拖拽 ghost（跟随光标） |
| 9 | 标题 / 副标题 / 页指示 | tooltip / toast |
| 10 | 法力条 + 左右提示 | —— |
| 11 | 虚拟光标 | —— |
| 12 | 错误/结果 toast（1.5 s） | —— |

> 层次分两段：**2–7 层全部是立即绘制**（连续画完只 flush 一次），**8–12 层走 GuiGraphics 批处理**
> （图标、文字、矩形）。这个分界让 §8.5 的"uniform 设完立刻画"与"渲染状态还原"都只发生一次。

---

## 四、视觉规范

### 4.1 颜色 token

基础色（与设计稿 `WheelKit` 常量一一对应，实机直接抄这些 ARGB）：

| Token | ARGB | 用途 |
| --- | --- | --- |
| `GOLD` | `0xFFE7C46A` | 主金：环、描边、键位、页码；**与 `MagicScreen.COL_GOLD` 同值**（`[已实现]`） |
| `GOLD_BRIGHT` | `0xFFFFE49A` | 亮金：标题、选中文字、法力填充、光标 |
| `GOLD_DIM` | `0x99C9A227` | 旧金：分隔线、未选中描边；与 `MagicScreen.COL_GOLD_DIM` 同值（`[已实现]`） |
| `INK` | `0xFF07101D` | 深空：面板/核心底、法力条轨道端色 |
| `DEEP` | `0xFF02040A` | 暗空：最深处、图标圆底 |
| `HAZE` | `0xFF2A1B4D` | 紫雾：`wheel_aura` 的 `uColorC`、面板高光 |
| `TEAL` | `0xFF6FD8C0` | 青：法力/能量强调、`wheel_aura` 的 `uColorB` |
| `TEXT` | `0xFFE8EEFF` | 主文字 |
| `TEXT_DIM` | `0xFF8FA3C8` | 次级文字（副标题、说明） |
| `TEXT_FAINT` | `0xFF5C6C8C` | 弱提示（键位提示、页脚） |
| `WARN` | `0xFFFF8A5C` | 警告（未蓄满、被拒绝） |
| `LOCKED` | `0xFF6C86B8` | 锁定冷蓝（蓝不足的扇区/角标） |

状态/面板色：

| Token | ARGB | 用途 |
| --- | --- | --- |
| `PANEL_TOP` | `0xB20A0F1A` | 装载界面面板渐变上端（沿用 `MagicScreen` 侧栏） |
| `PANEL_BOTTOM` | `0xC8060912` | 面板渐变下端 |
| `PANEL_BORDER` | `0x66C9A227` | 面板外描边 1px |
| `PANEL_INNER` | `0x22C9A227` | 面板内衬 1px（内缩 3px） |
| `HOVER_FILL` | `0x33E7C46A` | 卡片/列表 hover 填充（`MagicScreen` 列表同款） |
| `HOVER_OUTLINE` | `0x99E7C46A` | hover 描边 |
| `VALID_FILL` | `0x16FFD27A` | 拖拽可放置提示（`MagicScreen` 同款） |
| `VALID_OUTLINE` | `0x66FFD27A` | 可放置描边 |
| `INVALID_FILL` | `0x22FF5555` | 非法目标填充（`MagicScreen` 同款） |
| `INVALID_OUTLINE` | `0xAAFF5555` | 非法目标描边 |
| `COOLDOWN_SWEEP` | `0x9602040A` | 冷却压暗（叠在扇区上，α 0.59） |
| `EMPTY_SLOT_FILL` | `0x5A02040A` | 空位圆底 |
| `EMPTY_SLOT_RUNE` | `GOLD @ α0.34` | 空位符文（极淡） |

流派色（`MagicType` 全 7 个值；前 6 个与设计稿一致，`ACE` 为 `[建议新增]` 的中性色）：

| `MagicType` | RGB（0..1） | ARGB | 说明 |
| --- | --- | --- | --- |
| `FIRE` | (1.00, 0.42, 0.18) | `0xFFFF6B2E` | 火球术 |
| `DEATH` | (0.85, 0.30, 0.95) | `0xFFD94CF2` | 审判之矛 |
| `VOID` | (0.55, 0.35, 1.00) | `0xFF8C59FF` | 抑制场 |
| `MAGIC` | (0.40, 0.80, 1.00) | `0xFF66CCFF` | 通用法杖（`WandItem`，`MagicType.MAGIC`） |
| `NATURE` | (0.42, 0.90, 0.50) | `0xFF6BE680` | 自然法杖（`MagicType.NATURE`） |
| `LIGHT` | (1.00, 0.90, 0.55) | `0xFFFFE58C` | 保留 |
| `ACE` | (0.92, 0.94, 1.00) | `0xFFEBF0FF` | 现有法术均未使用；占位=近白冷光（避免与 LIGHT 混淆可后调） |

> 文字色说明：`MagicScreen` 现在用的是 `COL_TEXT_DIM = 0x8899AABB`（偏灰蓝、α<1）；
> 轮盘用上表更亮的三级文字色。**建议 P1 把 `MagicScreen` 一起换成同一组 token**，
> 但两处不改也不会破坏功能（只是同一屏里两种灰）。

### 4.2 字号层级

| 层级 | 字号 | 行高 | 用法 | 备注 |
| --- | --- | --- | --- | --- |
| Screen Title | 3x | 27 | 装载界面 `SPELL LOADOUT` | `PoseStack.scale(3,3)` |
| HUD Title | 2x | 18 | `ARCANE WHEEL` | 带 shadow |
| Section | 1x | 9 | `WHEEL PAGE` / `DESCRIPTION` / 面板标题 | 大写 + 暗色 |
| Center Spell Name | 2x（放得下）/ 1x | 18 / 9 | 核心中的选中法术名 | 条件：`width×2 ≤ 核心半径 ×1.7` |
| Body | 1x | 9 | 属性行、副标题、描述正文 | shadow 关闭 |
| Micro | 1x | 9 | 键位提示、页脚、角标 | `TEXT_FAINT` |

* 所有标题、核心法术名、法力读数开 `shadow = true`；正文关闭（避免糊）。
* 长法术名：核心半径 54，2x 的放行条件实际是"1x 宽度 ≤ `54 × 1.7 / 2 ≈ 45.9 px`"（约 9 个字符）。
  `Fireball`（1x ≈ 41 px）能 2x；`Spear of Judgment`（1x ≈ 100 px）自动落 1x。
  装载界面详情面板 188 宽、内边距 10 → 可用 168，`Spear of Judgment` 1x 放得下。
* 不做非整数倍缩放（1.5x 会让位图字体出现半像素抖动）。

### 4.3 描边 / 发光 / 圆角

| Token | 规格 | 用法 |
| --- | --- | --- |
| `STROKE_PANEL` | 外描边 1px + 内衬 1px（内缩 3px） | 装载界面三面板、图鉴卡片 |
| `STROKE_ROW` | 1px 上下边（上 `GOLD@0x82`，下 `GOLD@0x2D`） | 卡片顶/底边（沿用 `WheelKit#drawSpellCard`） |
| `GLOW_SLOT_HOVER` | 三层叠加：外 6px `0x12FFD27A` → 内 3px `0x1CFFD27A` → 1px 描边 | 扇区 hover |
| `GLOW_CORE` | 6 层同心圆渐隐（或 `wheel_core` shader） | 中心法力核心 |
| `GLOW_SELECTED` | 选中扇区外沿弧 + 图标外 3 圈渐隐环 | 选中反馈 |
| `CORNER_NONE` | 直角 | 所有矩形面板（MC 的 `fill` 只能画矩形；不要为圆角引 RenderType） |
| `CORNER_BAR` | 端帽半径 = 高度/2 | 法力条、状态胶囊（`fillDisc` 画两端圆帽） |
| `CORNER_TEXTURE` | `gold_slot.png` 自带 1px 内嵌旧金框 | **只在 `MagicScreen` 星座槽使用**；轮盘扇区是矢量绘制，不用这张贴图 |

> "圆角"在本项目里只有两种实现：① 程序化的圆端条/圆盘（`fillDisc` + 矩形）；② 烘焙贴图自带的圆角
> （`gold_slot.png`、`difficulty_*.png`）。轮盘不复用 `gold_slot.png`，因为它会在扇区里显得像"贴上去的框"，
> 而扇区的边界要由角度和状态动态变化。

### 4.4 动效 token

| 名称 | 时长 | 缓动 | 触发 / 说明 |
| --- | --- | --- | --- |
| `OPEN` | **180 ms** | easeOutCubic | 按住施法键，半径 `0.86→1.0`、α `0→1` |
| `OPEN_STAGGER` | 18 ms / 扇区 | 线性延迟 | 8 个扇区依次 pop（从位置 1 开始顺时针） |
| `SELECT` | **90 ms** | easeOutQuad | 选中扇区外扩（+5 px）、亮度提升 |
| `CLOSE` | 120 ms | easeInQuad | 松手/取消后淡出（取消时同时 90 ms 内缩） |
| `CHARGE_COMMIT` | 120 ms | — | `CHARGE` 停留阈值（防止滑过误触，见 §6.2） |
| `KEEP_COMMIT` | 250 ms | — | `KEEP` 停留阈值（防误触起手，见 §6.2） |
| `RELEASE_FLASH` | 140 ms | 线性 | 释放成功：扇区外沿闪一道亮金弧 |
| `COOLDOWN_SWEEP` | 持续 | 线性 | 压暗区 = 剩余比例，从扇区起始角顺时针；亮边随冷却减少而回退 |
| `RUNE_ROTATE` | 持续 | 线性 | 外圈符文环 0.02 rad/s（≈1.15°/s，`WheelKit` 同款） |
| `CORE_PULSE` | 1600 ms / 周期 | sin | 核心亮度呼吸（`sin(time·0.004)`，与 `MagicScreen` 选中槽一致） |
| `MOTE_ORBIT` | 0.05–0.17 rad/s | 线性 | 外圈金色粒子（34 颗） |
| `TOAST` | 1500 ms | fade in 120 / hold 1100 / fade out 280 | 结果提示（蓝不足、未蓄满、绑定成功等） |
| `ERROR_SHAKE` | 220 ms | 衰减正弦 | 拒绝时整个轮盘 x 抖动 ±6S px（仅一次） |
| `MANA_LERP` | 120 ms | 线性 | 法力条从旧值插值到新值（防包到达时跳变） |
| `SUSTAIN_COLLAPSE` | 160 ms | easeInOut | `KEEP` 起手后轮盘收拢为底部维持条 |

> 180/90 ms 是设计前提里给定的值；其余动效值以设计稿与手感为准，可在实机联调时微调，
> 但**打开 ≤ 200 ms** 是硬要求（超过会觉得"按键没反应"）。

### 4.5 图标与贴图

| 资源 | 用途 | 说明 |
| --- | --- | --- |
| 法术物品贴图（`textures/item/*.png`，16×16） | 轮盘扇区图标、图鉴卡片、详情图标 | 最近邻绘制到整数倍逻辑像素：HUD 32（选中 40）/ 图鉴卡片 16 / 详情 40；外层画流派色描边环（整数倍保证 16×16 texel 落到整数物理像素） |
| `textures/gui/gold_slot.png`（26×26） | **不用于轮盘**；`MagicScreen` 星座槽继续用 | 见 §4.3 |
| 程序化符文（`WheelKit#runeGlyph`） | 空位插槽、流派徽记 | 确定性 hash 生成 3~5 笔短线 + 中心点，不依赖随机源 |
| 程序化星芒（`fillStar`） | 虚拟光标、核心星图 | 4/6 尖星 |
| 无贴图背景 | `magic_starfield`（HUD 打开时可选：轮盘背后压暗世界，不铺全屏星野） | HUD **不**画全屏星图（那是 `MagicScreen` 的待遇）；只在轮盘外径内叠一层 `wheel_aura` 柔光 |

---

## 五、状态机与状态表

### 5.1 轮盘 HUD 状态机

```
                  ┌──────────────────────────────────────────────┐
                  │                  CLOSED                       │
                  └───┬───────────────────────────────▲──────────┘
        按住 R（前置满足）│                               │ 淡出完成
                      ▼                               │
                 ┌─────────┐  180ms   ┌──────────────┐ │
                 │ OPENING ├─────────►│   AIMING     │ │
                 └────┬────┘          │  (无选中)     │ │
      前置不满足/无主动 │               └──┬───────┬───┘ │
                      ▼                  │光标入扇区│松手（无选中→取消）
              ┌──────────────┐           ▼        │    │
              │  HINT_TOAST  │      ┌─────────┐    │    │
              │ (1.5/2.5 s)  │      │ SELECTED│    │    │
              └──────┬───────┘      └──┬───┬──┘    │    │
                     │                 │   │       │    │
           KEEP 停留 │      CHARGE 停留│   │移出/取消    │
                     ▼                 ▼   │       │    │
              ┌────────────┐    ┌──────────┐│       │    │
              │  MAINTAIN  │    │ CHARGING ││       │    │
              │ (维持条)   │    └────┬─────┘│       │    │
              └──┬──────┬──┘  松手    │      │       │    │
                 │      │             ▼      ▼       ▼    │
                 │      │        ┌─────────────────────┐  │
                 │      └───────►│     RELEASING       ├──┘
                 │               │ (140ms flash→CLOSING)│
                 ▼               └─────────┬───────────┘
              ┌────────────┐               │
              │  CLOSING   │◄──────────────┘
              └──────┬─────┘
                     ▼
                  CLOSED
```

状态表：

| 状态 | 进入条件 | 视觉 | 允许的动作 | 退出 |
| --- | --- | --- | --- | --- |
| `CLOSED` | 默认 | —— | —— | 按住施法键 |
| `OPENING` | 按键按下且前置满足 | 轮盘 180 ms 展开，扇区依次出现 | 仍可继续按住/取消 | 180 ms → `AIMING` |
| `AIMING` | 展开完成，光标在死区内 | 核心 `AIM A SIGIL` / `release to cast`；无扇区选中 | 移动光标、`Esc`/右键、松手 | 入扇区 → `SELECTED`；松手 → `CLOSING`（取消） |
| `SELECTED` | 光标落在有效扇区 | 扇区外扩发光，核心显示法术信息 + 状态行 | 移动换扇区、松手、取消、数字键改选 | 移出 → `AIMING`；松手 → `RELEASING`/`CLOSING` |
| `CHARGING` | `SELECTED` 为 `CHARGE` 法术且停留 ≥ 120 ms | 蓄力环填充、核心 `CHARGING x.x / 2.0s`、脉动 | 移动（离开扇区即取消蓄力）、松手、取消 | 离开 → `AIMING`（发 CANCEL）；松手 → `RELEASING` |
| `MAINTAIN` | `SELECTED` 为 `KEEP` 法术且停留 ≥ 250 ms，服务端起手成功 | 轮盘 160 ms 收拢为底部维持条：图标 + 名 + `SUSTAINING x.x s` + 法力条 | 松手（结束维持）、取消 | 松手 → `CLOSING` |
| `RELEASING` | 松手且本次可释放 | 140 ms 亮金外扩闪光 | 无（忽略输入） | → `CLOSING` |
| `CLOSING` | 任意关闭 | 120 ms 淡出/内缩 | 无 | → `CLOSED` |
| `HINT_TOAST` | 前置不满足但需要提示的项（§2.5：无法杖 / 无主动法术） | 准星下方 1 行提示（`HOLD A STAFF` 1.5 s；`NO ACTIVE SPELLS` 2.5 s） | 忽略输入（不吞键） | 超时 → `CLOSED` |

兜底路径：

| 场景 | 打开时表现 | 松手时 |
| --- | --- | --- |
| 无主动法术可上轮盘 | `HINT_TOAST`：`NO ACTIVE SPELLS`，2.5 s 后关闭；**不进入轮盘** | 无动作 |
| 有绑定、鼠标在空位方向 | `SELECTED` 不成立 → 视为 `AIMING`；核心显示 `EMPTY WHEEL SLOT` 小字 | 关闭，不释放 |
| 全部冷却 | 打开；所有扇区压暗 + 读秒；核心 `ALL SPELLS UNAVAILABLE` | 关闭，不释放 |
| 全部蓝不足 | 打开；所有扇区冷蓝锁定；核心 `NEED x MANA`（以选中位为准） | 关闭，不释放 |
| 光标死区 | `AIMING` | 关闭（等同取消），播取消音 |
| 服务端拒绝（冷却/蓝/无目标/未蓄满/非主动） | 客户端预测允许但服务端拒绝 | 关闭 + 收到 `WheelCastFeedbackPacket` 后 toast 原因（§5.4） |

### 5.2 扇区状态表（视觉语义）

| 扇区状态 | 判定 | 填充 | 外沿 | 角标 | 中心 |
| --- | --- | --- | --- | --- | --- |
| 空位 | 轮盘位无有效映射 | `EMPTY_SLOT_FILL` 圆底 | 无 | 只画极淡符文 | 不显示 |
| 常态 | 有法术，可放 | 流派色暗底（内 0.20 / 外 0.55 亮度） | `OUTER` 处细亮线 | 键位 1..8 | —— |
| hover | 光标所在扇区 | 亮度 +0.10，外扩 +2 | 亮线 α 提升 | 键位变亮 | —— |
| selected | 当前选中 | 亮度 +0.18，外扩 +5 | 外沿弧（`OUTER+3.5..+5`）+ 图标光晕 | 键位亮金 | 显示法术详情 |
| 冷却 | `pool.getCooldownRemaining(magicId) > 0` | 从起始角顺时针压暗（比例 = 剩余/总时长） | 扫边亮线随冷却回退 | `12.3s` 胶囊（金色） | `COOLDOWN x.xs` |
| 蓝不足（锁定） | `mana < MagicStats.manaCost(...)` | 去饱和偏冷蓝叠加 `LOCKED@0x3C` | 描边变冷 | `100 MANA` 胶囊（`LOCKED` 色） | `NEED 100 MANA` |
| 蓄力中 | `CHARGE` 且已开始蓄力 | 亮度按蓄力比例渐亮 | 蓄力环（`OUTER+9`） | —— | `CHARGING 1.4 / 2.0s` |
| 维持中 | 该位对应的 KEEP 正在维持 | 只维持条显示，轮盘已收拢 | —— | —— | —— |

> 冷却是**每件法术**的（键=注册名），不是每位的；蓝不足是**每位即时计算**的（同一个法术在不同位显示一致）。
> 二者同时存在时：冷却优先显示（冷却中玩家不需要知道够不够蓝）。

### 5.3 装载界面状态机

```
        ┌──────────┐  选中卡片/轮盘位   ┌────────────────┐
        │   IDLE   ├──────────────────►│  IDLE_SELECTED │
        │ (空/未选) │◄──────────────────┤ (右侧显示详情)  │
        └──┬────┬──┘   点空白/ESC 取消   └──┬──────┬──────┘
   拖动卡片│    │切 PASSIVE 页               │拖动   │点轮盘空位
           ▼    ▼                            ▼       ▼
     ┌──────────────┐  松手到合法位  ┌───────────────────┐
     │ DRAG_FROM_   ├───────────────►│   SYNCING         │
     │ CODEX        │  松手到非法位  │ (等服务端回推 100ms)│
     └──────┬───────┘       │        └──────────┬────────┘
            │松手空白/原位   │ 失败 toast         │成功
            ▼               ▼                   ▼
          IDLE        ERROR_CONFLICT ──────► IDLE_SELECTED
```

| 状态 | 进入 | 视觉 | 动作 |
| --- | --- | --- | --- |
| `IDLE` | 打开界面、未选中任何卡片/轮盘位（当前页已有绑定） | 轮盘预览各家高亮；右侧详情显示"选择一个法术"占位 | 选卡片/轮盘位、拖拽、切页/页签 |
| `IDLE_EMPTY` | 魔法池里没有主动法术（或当前页全空且未选） | 图鉴空态文案 `No spells in the magic pool`；轮盘全空位；详情隐藏 | 切页签、自动填充（无效果） |
| `IDLE_SELECTED` | 选中图鉴卡片 / 轮盘位 | 卡片高亮；轮盘对应位画目标框；右侧详情 | 拖拽、绑定/卸下、切页 |
| `DRAG_FROM_CODEX` | 从卡片按下并移动 > 3 px（沿用 `MagicScreen` 的拖拽阈值） | ghost 跟随；可放置位亮金框、非法位铺红；靠近合法位吸附 | 松手绑定 / 松手空白取消 |
| `DRAG_FROM_WHEEL` | 从已绑定扇区拖起 | ghost；拖到另一位=移动，拖到空白/图鉴=卸下 | 松手提交 |
| `PASSIVE_TAB` | 点 `PASSIVE` 页签 | 卡片全部 `state=3`（禁用灰 + 锁图标）；右侧详情显示 `PASSIVE — ALWAYS ACTIVE, CANNOT BE PLACED ON THE WHEEL` | 只读：不能拖、不能点绑定；仍可切回 `ACTIVE` |
| `CONFIRM_RESET` | 点 `RESET` | 按钮文案变 `CONFIRM?`，3 s 内再点生效，超时复原 | 再点 → 清空当前页；点别处 → 取消 |
| `SYNCING` | 已发配置包、等回推 | 轻微 loading（按钮禁用 100 ms） | 自动回 `IDLE_SELECTED` |
| `ERROR_CONFLICT` | 服务端拒绝 | 红闪 + `NOTE_BLOCK_BASS` 失败音 + toast 具体原因（§5.4） | 1.5 s 后回 `IDLE_SELECTED` |

### 5.4 服务端结果 → 客户端反馈

`WheelCastFeedbackPacket` 携带的结果码（取自现有 `CastResult`，另加轮盘专用码）映射：

| 结果 | 触发 | 文案 key | 视觉/音效 |
| --- | --- | --- | --- |
| `SUCCESS` | 释放成功 | —— | 140 ms 金色外扩闪光 + `AMETHYST_BLOCK_CHIME(1.2F, 0.5F)` |
| `ON_COOLDOWN` | 冷却中 | `rotten_ruins.gui.wheel.result.on_cooldown` | 中心红字 1.2 s + 失败音 |
| `NOT_ENOUGH_MANA` | 蓝不够（含蓄力期间被抽干） | `...result.not_enough_mana` | 法力条闪红 200 ms |
| `NOT_CHARGED` | 蓄力未达 `minChargeRatio`（审判之矛 = 必须蓄满） | `...result.not_charged` | 蓄力环变红淡出；**不扣蓝不进冷却** |
| `NO_TARGET` | 需要目标但准星没有 | `...result.no_target` | 中心提示 + 失败音 |
| `PASSIVE_SPELL` | 映射指向被动（异常数据） | `...result.passive` | 红字；同时服务端清理该映射 |
| `NO_SPELL` / `INVALID_SLOT` | 映射失效（池槽被清空/缩容） | `...result.no_spell` | 该位重绘为空位 |
| `REJECTED` | `canCast` 拒绝 / 法杖不接受 | `...result.rejected` | 失败音 |
| `CANCELLED` | 被 `MagicCastEvent` 取消 | `...result.cancelled` | 失败音 |
| `INVALID_STAFF` | 松手时法杖已不在手上 | `...result.no_staff` | 关轮盘 + 提示 |
| `BUSY`（轮盘专用码） | 已有施法会话/正在右键蓄力 | `...result.busy` | 提示 `ALREADY CASTING` |
| 无会话收到 `END` | 超时/重放包 | ——（静默丢弃） | —— |

---

## 六、交互细则

### 6.1 键鼠表

| 输入 | 轮盘打开中 | 轮盘关闭时 |
| --- | --- | --- |
| 施法键 `R`（按住） | 保持打开；松开 = 释放/结束 | 按下 = 尝试打开（§2.5 前置判定） |
| 鼠标移动 | 驱动虚拟光标（累加增量，钳在 `OUTER×1.18` 内） | 正常转视角 |
| 鼠标左键 | **不消费**（无意义；可选：点选扇区，P1） | 正常 |
| 鼠标右键 | 取消轮盘（同时拦截原版法杖右键，避免误放） | 正常法杖右键 |
| `Esc` | 取消轮盘，**不打开暂停菜单** | 打开暂停菜单 |
| `1`..`8` | 直接选中对应方位（不改数字键的其他行为） | 正常快捷栏 |
| 滚轮 | 切换轮盘页 I/II/III（`[P1]`，可先不做） | 正常快捷栏滚动 |

* **右键拦截**：用 `InputEvent.InteractionKeyMappingTriggered`（项目已有 `VoidSwordClientHandler` 的注册写法），
  当轮盘打开且 `event.isUseItem()` 时取消事件并关闭轮盘。需要进游戏验证"取消后原版不会真的使用物品"。
* **Esc 拦截**：`InputEvent.Key` 在 1.20.1 是否可取消需要实测；保底方案是加一个
  `KeyboardHandlerMixin`（`[建议新增]`），在 `keyPress` 处对 `ESC` 早退并关闭轮盘。
* 轮盘打开期间**不释放鼠标**（`mc.mouseHandler` 保持 grabbed），所以没有系统光标——
  轮盘自己画虚拟光标（§3.3）。这是"不夺相机"的关键（§1.2）。

### 6.2 手势 → 三种 `UsingType` 的映射（核心设计）

三种使用方式的时间轴由 `MagicHandle` 统一驱动（`[已实现]`）：`IMMEDIATELY` 按下即结算；
`CHARGE` 按下预检、松手按 `chargeRatio` 释放；`KEEP` 按下起手、按住维持、松手/耗尽结束。
轮盘的手势只有"按住→松开"，因此按类型分开映射：

| `UsingType` | 选中后何时起手 | 松手 | 中途取消 |
| --- | --- | --- | --- |
| `IMMEDIATELY` | 松手时（一次性 `CAST`） | 发 `CAST` → 服务端 `handleUseStart` → 立刻打出 | 不发包，直接关 |
| `CHARGE` | 选中该扇区并停留 **120 ms** → 发 `BEGIN` 开始蓄力（服务端进 `CHARGING`） | 发 `END` → 服务端按**自己的** heldTicks 算 `chargeRatio`；低于 `minChargeRatio` → `NOT_CHARGED`（不扣蓝、不进冷却） | 发 `CANCEL`（蓄力作废，零代价） |
| `KEEP` | 选中并停留 **250 ms** → 发 `BEGIN` 立刻起手（扣启动蓝 + 进冷却 + 进 `CHANNELING`），轮盘同时收拢为维持条 | 发 `END` → 结束维持，回调 `onKeepStop` | 发 `CANCEL` → 同样结束维持（**启动耗蓝与冷却不退**） |

**为什么 `CHARGE` 不能"松手才开始蓄力"**：`MagicHandle#handleUseRelease` 用 `heldTicks` 算
`chargeRatio`，松手瞬间 heldTicks=0 → 比例 0 → 审判之矛（`minChargeRatio = 1.0`）永远返回
`NOT_CHARGED`。所以蓄力必须在按键仍按住时开始：轮盘在选中时就发 `BEGIN`，让服务端进入 `CHARGING`，
按键本身从头到尾没有松开过。

**网络容差（`minChargeRatio = 1.0` 的必要补丁）**：会话的 heldTicks 由服务端时钟算
（`BEGIN` 到达 tick → `END` 到达 tick），两端包走同一条连接，RTT 基本抵消；但抖动可能让
服务端少数 1~2 tick，导致"明明按满 2.0 s 却 `NOT_CHARGED`"。因此服务端对轮盘会话的 heldTicks
额外加 **`WHEEL_CHARGE_TOLERANCE_TICKS = 2`**（≤100 ms）的容差：
`effectiveHeld = min(clientHeld, serverHeld + 2)`，同时不超过 `chargeTicks`。
只影响"差一两 tick"的手感，不构成平衡漏洞（最多提前 100 ms 蓄满）。
HUD 侧配合：蓄力环填满后仍然显示 `RELEASE TO CAST`，不鼓励卡最后一 tick 松手。

**为什么 `KEEP` 不能在松手时起手**：`KEEP` 的起手 = 扣 80 蓝 + 进 240 tick（12 s）冷却
（`SuppressionField`），而"维持"的结束条件之一就是松手。如果松手才起手，起手与结束会落在同一瞬间，
等于白扣 80 蓝并吃满 12 s 冷却。所以 `KEEP` 必须在按键仍按住的时刻起手，用**停留阈值**防误触。

**为什么停留阈值是 120/250 ms**：`CHARGE` 起手零代价（不扣蓝不进冷却），阈值只用于过滤"滑过扇区"；
`KEEP` 起手有真实代价，阈值更长（250 ms）并要求视觉/音效确认（起手瞬间 `SCULK_SHRIEKER_SHRIEK` 是
法术自己的音效，HUD 再叠一声 `UI_BUTTON_CLICK`）。

时序（以审判之矛为例）：

```
t=0ms      按住 R                 → OPENING（180ms）
t=180ms    轮盘完全展开            → AIMING
t=300ms    光标停在位 2（审判之矛）→ SELECTED
t=420ms    停留 120ms 满足         → BEGIN → 服务端 beginCharging；客户端蓄力环开始
t=420..2420ms 按住               → CHARGING（核心显示 CHARGING 0.0→2.0 / 2.0s）
t=2420ms   松手                   → END（服务端 heldTicks≈40）→ 比例 1.0 → SUCCESS
t=2420ms   客户端 140ms 闪光 → CLOSING（120ms）→ CLOSED
```

### 6.3 选中判定与虚拟光标

| 规则 | 值 |
| --- | --- |
| 光标来源 | 鼠标增量累加（grabbed 状态无系统光标），灵敏度 `1.0`（可在客户端配置里后加） |
| 光标位置 | `(CX + dx·S, CY + dy·S)`，`dx/dy` 累加值，钳在半径 `OUTER×1.18` 内 |
| 选中 | 光标方向映射到扇区（§3.3 公式）；不在扇区内 = 无选中 |
| 换选 | 任何方向变化都立即重算；离开 `CHARGE` 扇区时客户端发 `CANCEL` |
| 数字键 | 直接锁定对应方位；之后鼠标移动会重新接管（数字键不清空，直到鼠标方向改变） |
| 视觉 | 虚拟光标菱形 + 目标扇区外扩；无系统光标 |

### 6.4 装载界面交互细则

| 操作 | 手势 | 服务端行为 |
| --- | --- | --- |
| 绑定 | 从图鉴卡片拖到轮盘空位 / 点选卡片再点空位 | `WHEEL_BIND(page, wheelSlot, poolSlot)`；校验主动、已装备、页内唯一 |
| 卸下 | 右键已绑定扇区 / 从扇区拖到空白或图鉴 | `WHEEL_UNBIND(page, wheelSlot)` |
| 移动 | 从扇区 A 拖到扇区 B（B 为空） | `WHEEL_BIND(page, B, 原 poolSlot)` + 清空 A（同一包内完成，避免中间态） |
| 替换 | 拖到已占用扇区 | **不隐式替换**：非法目标铺红，提示 `SLOT OCCUPIED`（必须先卸下；防误覆盖） |
| 自动填充 | 点 `AUTO-FILL` | `WHEEL_AUTOFILL(page)`：按池槽顺序 0..N、按轮盘位 1..8 填主动法术，不覆盖已绑定、跳过本页已绑定、跳过被动 |
| 重置 | 点 `RESET` → 3 s 内再点 `CONFIRM?` | `WHEEL_RESET(page)`：清空当前页 8 位 |
| 切页 | 点 `I/II/III` | `WHEEL_PAGE(page)`：只改"当前页"，同步后 HUD 页指示同步变化 |
| 保存 | 点 `SAVE` | 不发数据包；客户端 `sendSyncRequest()` 强制拉一次全量 → 关闭界面 + toast `Wheel saved` |
| 返回 | `Esc` → 关回游戏；`M` → 回 `MagicScreen` | 关界面/换界面；不弹"未保存"对话框（改动已即时生效） |

* 拖拽阈值与 `MagicScreen` 一致：移动超过 3 px 才算拖拽，否则按点击处理（`[已实现]` 同类逻辑）。
* 图鉴列表：`ACTIVE` 页显示魔法池里的主动法术（最多 7 张卡片），按池槽顺序 0..N 排列；
  如果池里有被动法术，它们只出现在 `PASSIVE` 页。
* 背包里未装备的主动法术：追加在 `ACTIVE` 页列表末尾，灰卡 + `EQUIP IN CONSTELLATION FIRST`，
  不可拖拽（点击只 toast 提示）。
* `PASSIVE` 页：只读。卡片右侧画锁图标，悬停提示 `PASSIVE — ALWAYS ACTIVE, CANNOT BE PLACED ON THE WHEEL`；
  拖拽一律不进入 `DRAG_FROM_CODEX`。

### 6.5 冲突与替换规则

| 规则 | 说明 | 客户端预判 | 服务端校验 |
| --- | --- | --- | --- |
| 一位一法术 | 每个轮盘位最多一个池槽索引 | 占位扇区不是合法放置目标（拖拽只允许落到空位） | `WheelSlots#set` 允许覆盖写入，但只用于"移动"（同包内先写目标位再清源位）；外部拖拽路径不会请求覆盖已占位 |
| 页内唯一 | 同一法术（注册名）在同一页只能出现一次 | `WheelSlots#isBound(page, poolSlot)` 或按 `MagicStats.magicId` 判重 | 强制：重复返回失败，**不覆盖**原绑定 |
| 跨页重复 | 同一法术可以出现在不同页（预设页的意义） | 允许 | 允许（页是独立预设） |
| 被动不进轮盘 | `MagicStats.spellKind(stack) == PASSIVE` 一律拒绝 | 图鉴 PASSIVE 页禁用拖拽；池里有被动也不会出现在 ACTIVE 页 | 拒绝 + 清理异常映射 |
| 源槽必须已装备 | 池槽索引有效且 `getStackInSlot` 非空 | 卡片来自池镜像，天然满足 | 重新读服务端池（客户端只是镜像） |
| 池槽内容变化 | 绑定语义是"指向池槽位"，换掉池槽内容 = 轮盘位换法术 | 显示跟随池镜像 | `sanitize()`：越界/空槽/被动 → 清空该位 |
| 池槽缩容 | `MagicStats.slotCount` 被属性调低 | —— | 越界位清空；已有 `ensureSlotCount()` 把物品退回背包（`[已实现]`） |
| 重名不同物品 | 两个不同注册名的法术不算重复（冷却也各自独立，`[已实现]`） | 允许 | 允许 |

> **"保存"为什么没有二次确认**：所有绑定操作都是**服务端权威即时生效**（与 `MagicScreen` 一致），
> 不存在"未保存的草稿"。`SAVE` 只是"确认并关闭"。如果后续改成本地草稿模式，需要补"未保存"提示，
> 见 §11 待定 3。

### 6.6 取消与回滚

| 场景 | 行为 | 代价 |
| --- | --- | --- |
| 轮盘打开中按右键/`Esc` | 关闭；若在 `CHARGING` 发 `CANCEL`；若在 `MAINTAIN` 发 `CANCEL`（结束维持） | `CHARGE`：零代价（未扣蓝未进冷却）；`KEEP`：起手已扣，**不退** |
| 松手但未选中（死区/空位） | 关闭，等同取消 | 零 |
| 松手但服务端拒绝 | 关闭 + toast 原因 | 拒绝类（冷却/蓝/无目标/未蓄满）都不扣蓝不进冷却（`[已实现]`） |
| 松开时法杖已换掉 | `INVALID_STAFF`，关闭 | 零 |
| 打开期间死亡/换维度/打开其它界面 | 立即关闭并发 `CANCEL` | 同上 |
| 施法成功后立刻按 `R` 再开 | 允许（`State` 已回 `IDLE`） | —— |

### 6.7 可访问性

* **1..8 数字键**：不依赖鼠标精度即可选中指定方位；再松手释放（`CHARGE` 的蓄力在数字键选中后同样开始）。
* **状态不只靠颜色**：冷却 = 压暗弧 + 秒数文本；蓝不足 = 冷蓝 + `NEED x MANA` 文本；
  选中 = 外扩 + 外沿弧 + 中心文字。色盲玩家也能区分。
* **字号下限**：所有关键信息 ≥ 8px 位图字体；中心法术名 2x 优先，放不下自动 1x。
* **不依赖悬停 tooltip**：HUD 的关键数据（耗蓝、冷却、蓄力）直接画在核心，不做悬浮提示。
* **键位可改**：默认 `R`，在「控制 → 腐朽之疫」分类下可重绑。

---

## 七、数据模型与持久化

### 7.1 三层数据关系

```
WheelSlots（轮盘映射，3 页 × 8 位）
   每个位置存一个 int：魔法池槽位下标（-1 = 空）
        │
        │ 只允许指向"已装备的主动法术"
        ▼
PlayerMagicPool（魔法池，[已实现]）
   Items[]（最多 MAX_SLOT_COUNT=64 格，可见 getSlots()=MagicStats.slotCount）
   HallMagicSelected（当前选中格）
   HallMagicCooldowns（注册名 → 剩余 tick）
        │
        ▼
MagicStats（唯一求值入口，[已实现]）
   spellKind / manaCost / cooldownTicks / chargeTicks /
   keepCostPerSecond / keepTriggerInterval / minChargeRatio / magicId
        │
        ▼
MagicHandle（释放调度，[已实现]）
   handleUseStart / handleUseTick / handleUseRelease
```

### 7.2 为什么存"池槽索引"而不是"法术 id"

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A. 存池槽索引 `int` | 体积最小；服务端校验就是 `pool.getStackInSlot(i)`；与 `selectedSlot` 同一坐标系；天然满足"一个槽最多一个法术" | 池槽移动/清空后需要清理（可在池操作后 `sanitize()`） | ✓ **采用** |
| B. 存 `ResourceLocation` 法术 id | 不怕池槽移动 | 需要反查槽位；同一法术在池里本就唯一（池规则），用 id 只是把"槽位"问题换成"查找"问题；字符串占空间；还要处理"已装备但找不到槽" | ✗ |
| C. 存 `ItemStack` 快照 | 与池脱钩 | 会与真实池状态不一致（复制/丢失风险），违反服务端权威 | ✗ |

### 7.3 `WheelSlots`（`[建议新增]`）

位置建议：`org.bytechen.rottenruins.overworld.registry.capability.WheelSlots`（与 `PlayerMagicPool` 同包），
**作为 `PlayerMagicPool` 的组成部分**，不单独开 capability。

| 成员 | 类型 | 说明 |
| --- | --- | --- |
| `SLOTS` | `public static final int = 8` | 每页 8 位（与界面方位一一对应，下标 0 = 显示位 1） |
| `PAGES` | `public static final int = 3` | 轮盘页 I / II / III |
| `EMPTY` | `public static final int = -1` | 空位哨兵 |
| `slots` | `private final int[SLOTS * PAGES]` | 索引 = `page * SLOTS + slot`，值为池槽下标 |
| `activePage` | `private int` | 当前轮盘页（0..2），HUD 用它 |
| `get(int page, int slot)` | `int` | 越界返回 `EMPTY` |
| `set(int page, int slot, int poolSlot)` | `void` | 覆盖写入（调用方先做校验） |
| `clear(int page, int slot)` | `void` | 置 `EMPTY` |
| `clearPage(int page)` | `void` | 重置整页 |
| `indexOf(int page, int poolSlot)` | `int` | 页内反查（找不到 `-1`），用于唯一性校验与"已绑定"展示 |
| `isBound(int page, int poolSlot)` | `boolean` | `indexOf(...) >= 0` 的便捷版（装载界面画 `SLOT n` 徽章用） |
| `getActivePage()` / `setActivePage(int)` | `int` / `void` | 钳在 `[0, PAGES)` |
| `sanitize(@Nullable PlayerMagicPool pool)` | `boolean` | 清掉越界/空槽/被动；返回是否有改动（有则标脏） |
| `toIntArray()` / `fromIntArray(int[])` | NBT 用 | 长度不符时按可用长度拷贝，其余 `EMPTY` |

生命周期钩子（都在 `PlayerMagicPool` 里，`[建议新增]`）：

```
serializeNBT()   → 写 HallMagicWheelSlots / HallMagicWheelPage
deserializeNBT() → 读回 + sanitize()（脏数据不能进内存）
tickCooldowns()  → 服务端：sanitize()（只在槽位数/内容变化后真正动数据）
```

### 7.4 NBT key

| key | 类型 | 值 | 备注 |
| --- | --- | --- | --- |
| `Items` | `ListTag` | 法术物品 | **已有**（`PlayerMagicPool.NBT_SLOTS`） |
| `HallMagicSelected` | `int` | 当前选中池槽 | **已有**（`NBT_SELECTED`） |
| `HallMagicCooldowns` | `CompoundTag` | `注册名 → tick` | **已有**（`NBT_COOLDOWNS`） |
| `HallMagicWheelSlots` | `IntArrayTag` | 长度 `PAGES×SLOTS = 24`，`-1` 空 | `[建议新增]` |
| `HallMagicWheelPage` | `int` | 0..2 | `[建议新增]` |

命名沿用现有 `HallMagic*` 前缀（`HallMagicSelected` / `HallMagicCooldowns` / `HallMagicMana`），
老存档缺这两个键时按"全空 + 页 0"处理，天然向后兼容（不写 migration）。

### 7.5 魔法池变更 → 轮盘映射的反应

| 池操作（`MagicPoolActionPacket`） | 建议的轮盘处理 |
| --- | --- |
| `PLACE`（背包 → 池槽） | 不改映射；新法术不会自动上轮盘（需要玩家绑定/自动填充） |
| `TAKE`（池槽 → 背包） | `sanitize()` 清掉指向该池槽的所有位（3 页都清） |
| `MOVE`（池槽 A → B） | 把指向 A 的轮盘项改成 B（"轮盘跟着槽位走"）；随后 `sanitize()` |
| 槽位数收缩（属性变化） | `sanitize()`：越界的位清空；物品由 `ensureSlotCount()` 退回背包（`[已实现]`） |
| `SELECT`（切选中格） | 不改映射；**轮盘释放成功后**服务端会把 `selectedSlot` 写成来源池槽（见 §7.7 第 8 条） |
| 玩家死亡/重生 | `PlayerEvent.Clone` 复制 capability（现有机制），轮盘随 NBT 一起复制；`sanitize()` 兜底 |
| 换维度 | 不重置（同一份玩家数据） |

> `sanitize()` 放在服务端池操作的统一出口（`MagicPoolActionPacket` 的每个 handler 末尾、
> `PlayerMagicPool.tickCooldowns()` 里），保证客户端永远拿不到"指向空气"的映射。

### 7.6 同步

**7.6.1 轮盘数据（复用现有 S2C，不新增包）**

`MagicPoolSyncPacket` 发的是 `PlayerMagicPool#serializeNBT()` 全量快照（`[已实现]`）。
轮盘字段写进同一份 NBT 后，**零成本**随现有节流一起同步：

| 项 | 值 |
| --- | --- |
| 载体 | `MagicPoolSyncPacket(player.getId(), pool.serializeNBT())` |
| 频率 | `MagicSync.POOL_INTERVAL_TICKS = 5` tick（约 250 ms），脏才发；配置/施法后 `pushPool(player, true)` 强制一次 |
| 体积增量 | 24 × int + 1 int ≈ 100 B（全量包本来就只在变化时发） |
| 客户端落地 | `PlayerMagicPool#deserializeNBT` 读完即更新（`[已实现]` 路径），HUD/装载界面直接读镜像 |

> 结论：**不需要新增 S2C 同步包**。只有"施法结果回执"需要新包（见 7.6.3）。

**7.6.2 C2S `WheelCastPacket`（`[建议新增]`）**

沿用项目 `UniversalPacket` + `PacketActionRegistry` 的 action 分发（与 `MagicPoolActionPacket` 同构），
**不新增包序号**：

| 字段（NBT key） | 类型 | 说明 |
| --- | --- | --- |
| `a` | `int` | 动作：`CAST=0`（`IMMEDIATELY` 一次性）、`BEGIN=1`（`CHARGE`/`KEEP` 起手）、`END=2`（松手）、`CANCEL=3`（取消） |
| `g` | `int` | 轮盘页 0..2 |
| `s` | `int` | 轮盘位 0..7（显示位 1..8 − 1） |
| `h` | `int` | 客户端自报 heldTicks（只在 `serverHeld + 2` tick 容差窗口内被采纳，见 §6.2/§7.7；不能主导比例） |

* `ACTION = "rotten_ruins:wheel_cast"`；`WheelCastPacket.register()` 在 `NetworkHelper.register()` 里调用。
* 客户端发送点：`WheelCastPacket.sendCast(page, slot)` / `sendBegin(...)` / `sendEnd(page, slot, heldTicks)` / `sendCancel(...)`。
* 服务端不信任 `h`（容差与裁决见 §7.7 第 11 条）。

**7.6.3 S2C `WheelCastFeedbackPacket`（`[建议新增]`）**

施法结果必须回到客户端才能显示正确原因（现有右键施法是不回执的，客户端只能靠"没效果"猜）：

| 字段 | 编码 | 说明 |
| --- | --- | --- |
| `entityId` | `writeVarInt` | 谁的施法（与 `ManaSyncPacket` 同构） |
| `result` | `writeVarInt` | `CastResult.ordinal()`；**100 = 轮盘专用 `BUSY`**（不进 `CastResult` 枚举，避免污染公共 API） |
| `page` | `writeByte` | 回显轮盘页（-1 = 无） |
| `slot` | `writeByte` | 回显轮盘位（-1 = 无） |
| `serverHeldTicks` | `writeVarInt` | 服务端实际计到的按住 tick（用于对账与"未蓄满还差多久"提示） |

* 注册方式照 `ManaSyncPacket`：`NETWORK.registerMessage(packetId++, ..., Optional.of(NetworkDirection.PLAY_TO_CLIENT))`，
  handler 内 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)`。
* 客户端只更新 HUD 提示，不改任何权威数据。

**7.6.4 配置操作（扩展 `MagicPoolActionPacket`，不新增包）**

在现有 `action` 之外新增 5 个编号（`[建议新增]`），复用同一包与 `pushPool(player, true)`：

| 编号 | 名称 | 参数 | 服务端行为 |
| --- | --- | --- | --- |
| 5 | `WHEEL_BIND` | `p`=wheelSlot、`i`=poolSlot、新增 `g`=page | 校验后 `wheel.set(page, slot, poolSlot)` |
| 6 | `WHEEL_UNBIND` | `p`=wheelSlot、`g`=page | `wheel.clear` |
| 7 | `WHEEL_AUTOFILL` | `g`=page | 自动填充算法（§6.4） |
| 8 | `WHEEL_RESET` | `g`=page | `wheel.clearPage` |
| 9 | `WHEEL_PAGE` | `g`=page | `wheel.setActivePage` |

> 现有包字段是 `"a"/"p"/"i"`（`[已实现]`），新增 `"g"` = 轮盘页；旧客户端不发 `g` 时按 0 处理。
> 为什么塞进现有包：这五个操作都只改 `PlayerMagicPool` 这一份状态，且都由同一个界面发出；
> 新增包意味着动 `NetworkHelper` 的包序号——那是全项目唯一"两端必须严格同序"的地方（`[已实现]` 的注释明确警告过）。

### 7.7 服务端校验清单（收到 `WheelCastPacket` 时按顺序）

1. 玩家有效：非旁观、非死亡、`level` 是服务端。
2. 页/位合法：`0 ≤ page < PAGES`、`0 ≤ slot < SLOTS`。
3. 会话状态：`MagicHandle.peekState(player).isIdle()`；
   否则回 `BUSY`（`BEGIN`/`CAST`）或静默丢弃（`END`/`CANCEL`）。
4. 从 `WheelSlots` 取池槽下标；`EMPTY` → `NO_SPELL`。
5. `pool.isValidSlot(poolSlot)`（池可能被属性缩容）。
6. 法术堆非空 + `MagicStats.isSpell(stack)` + `MagicStats.spellKind(stack) == ACTIVE`；
   被动 → `PASSIVE_SPELL` 且**清理该映射**。
7. 法杖：主手 `instanceof StaffBase` 且 `staff.accepts(staffStack, spell)`；否则 `INVALID_STAFF`/`REJECTED`。
8. **写 `selectedSlot`**：`pool.setSelectedSlot(poolSlot)` —— 让"轮盘刚放过的法术"成为法杖右键的当前法术，
   两条施法路径连续；随后 `MagicSync.pushPool(player, true)`。
9. **起手**：`BEGIN`/`CAST` 都调 `MagicHandle.handleUseStart(player, hand)`（`[已实现]` 的公共入口，
   它内部做冷却 / 法力预检 / `canCast` / 扣蓝 / 进冷却 / `cast`）；
   `CHARGE`/`KEEP` 返回 `keepUsing=true`，记会话。
10. **时钟**：会话记 `startTick = player.tickCount`；每服务端 tick 由会话驱动
    `MagicHandle.handleUseTick(player, hand, serverHeldTicks)`（**只用服务端时钟算的值**）。
11. `END`：`serverHeldTicks = player.tickCount − startTick`，钳到 `[0, 72000]`；
    再按 §6.2 的网络容差算 `effectiveHeld = min(clientHeld, serverHeldTicks + WHEEL_CHARGE_TOLERANCE_TICKS)`，
    并钳回 `[0, 72000]`；调 `MagicHandle.handleUseRelease(player, hand, effectiveHeld)`；
    清会话；`pushPool/pushMana` 强制一次。
    （容差只对轮盘会话生效，且 `WHEEL_CHARGE_TOLERANCE_TICKS = 2` 是常量，不能由客户端指定。）
12. `CANCEL`：`MagicHandle` 新增 `cancelCasting(player)`（见 §8.3）；清会话；不扣蓝（`CHARGE`）/结束维持（`KEEP`）。
13. 会话安全：每 tick 校验 `peekState` 仍非 `IDLE`、法杖仍在原手、玩家存活；
    否则清会话并回 `INVALID_STAFF`/静默。玩家登出/死亡/换维度时强制清理。

### 7.8 反作弊与延迟

| 风险 | 处理 |
| --- | --- |
| 客户端谎报按住时长（提前放出满蓄力审判之矛） | 比例以**服务端 tick 时钟**为主；客户端 `h` 只在 `serverHeld + 容差(2 tick)` 的窗口内被采纳，超出即忽略 |
| 客户端反复发 `CAST` 刷法术 | 服务端每个 `CAST` 都走 `MagicHandle` 的冷却/法力/事件校验；写入 `selectedSlot` 不产生效果 |
| 客户端绑定非法法术（被动/未装备/重复） | 服务端重读 `PlayerMagicPool`，不信任客户端参数 |
| 重放旧包 | 会话状态机天然幂等：没有会话的 `END`/`CANCEL` 静默丢弃；`CAST` 受冷却约束 |
| 网络抖动导致客户端显示与服务端不一致 | 冷却/法力条只是显示（`[已实现]`：客户端本地倒计时、服务端权威）；施法结果靠 `WheelCastFeedbackPacket` 纠正 |

---

## 八、实机落地映射

### 8.1 文件清单

| 类型 | 路径 | 职责 |
| --- | --- | --- |
| `[新增]` | `client/gui/wheel/SpellWheelHud.java` | HUD 总控：状态机、数据准备、绘制调度、输入事件挂载 |
| `[新增]` | `client/gui/wheel/SpellWheelRenderer.java` | 轮盘几何/绘制（对应 `WheelKit` 的实机版）：扇区、环、图标、核心、法力条 |
| `[新增]` | `client/gui/wheel/WheelHudInput.java` | 按住检测、虚拟光标增量、`Esc`/右键/数字键、鼠标 mixin 桥接 |
| `[新增]` | `client/gui/wheel/WheelCastClientState.java` | 客户端施法会话（开始时间、结果 toast 队列、预测态） |
| `[新增]` | `client/gui/SpellLoadoutScreen.java` | 装载界面（`Screen`）：图鉴、拖拽、轮盘预览、详情、底部操作 |
| `[新增]` | `mixin/MouseHandlerMixin.java` | 轮盘打开时截获鼠标增量（虚拟光标） |
| `[新增]` | `mixin/KeyboardHandlerMixin.java` | `Esc` 关闭轮盘而不暂停（若 `InputEvent.Key` 不可取消） |
| `[新增]` | `overworld/registry/capability/WheelSlots.java` | 轮盘映射数据（§7.3） |
| `[新增]` | `network/c2s/WheelCastPacket.java` | C2S 施法手势（§7.6.2） |
| `[新增]` | `network/s2c/WheelCastFeedbackPacket.java` | S2C 施法结果回执（§7.6.3） |
| `[新增]` | `event/impl/WheelCastHandler.java` | 服务端：收包、会话、每 tick 驱动、校验（§7.7） |
| `[新增]` | `client/gui/wheel/WheelGlyphs.java`（可选） | 程序化符文/星芒/胶囊等复用绘制 |
| `[修改]` | `overworld/registry/capability/PlayerMagicPool.java` | 持有 `WheelSlots`；NBT 读写；`sanitize`；池操作后的映射清理 |
| `[修改]` | `network/c2s/MagicPoolActionPacket.java` | 新增 5 个轮盘配置 action（§7.6.4） |
| `[修改]` | `network/NetworkHelper.java` | `WheelCastPacket.register()` + `WheelCastFeedbackPacket` 注册（PLAY_TO_CLIENT） |
| `[修改]` | `client/ClientPacketHandlers.java` | 初始化 `SpellWheelHud`/`SpellWheelInput` 的事件注册 |
| `[修改]` | `client/MagicClientHandler.java` | 新增 `CAST_WHEEL` KeyMapping；轮盘 HUD 的按住轮询入口 |
| `[修改]` | `event/ClientModEventHandler.java` | `RegisterKeyMappingsEvent` 里注册新键位 |
| `[修改]` | `overworld/registry/items/magic/bases/MagicHandle.java` | 新增 `cancelCasting(ServerPlayer)`（公开） |
| `[修改]` | `client/gui/MagicScreen.java` | 顶部 `WHEEL` 页签入口；文字色 token 统一（P1） |
| `[修改]` | `datagen/gen/lang/LangDataEN.java` / `LangDataCN.java` | 新增 lang key（§8.7），然后跑 datagen 重生成 `en_us.json`/`zh_cn.json` |
| `[新增]` | `assets/rotten_ruins/shaders/core/magic_wheel_slice.{json,vsh,fsh}` | 扇区着色器（从工具字体移植，§8.6） |
| `[新增]` | `assets/rotten_ruins/shaders/core/magic_wheel_aura.{json,vsh,fsh}` | 背景光环/符文环 |
| `[新增]` | `assets/rotten_ruins/shaders/core/magic_wheel_core.{json,vsh,fsh}` | 中心核心 |
| `[修改]` | `client/rend/SplendidingShaders.java` | 注册三支新 shader（`magicWheelSliceShader` 等） |
| `[修改]` | `resources/rotten_ruins.mixins.json` | 登记两个新 mixin |

### 8.2 客户端类职责

| 类 | 输入 | 输出 | 不做什么 |
| --- | --- | --- | --- |
| `SpellWheelHud` | 键位按住状态、`PlayerMagicPool` 镜像、`TwistedPoint` 镜像、`WheelCastFeedbackPacket` | 绘制调用、C2S 手势包 | 不做服务端校验、不改冷却/法力 |
| `SpellWheelRenderer` | `WheelView`（8 位数据 + 状态 + 动画时间） | 立即绘制调用 | 不读玩家数据（数据由 HUD 组装） |
| `WheelHudInput` | 原始键鼠事件 | 状态机的 `press/release/move/cancel/selectDigit` | 不放行原版交互（右键拦截） |
| `WheelCastClientState` | 反馈包、本地计时 | 蓄力比例预测、toast | 不发数据包（除显式手势） |
| `SpellLoadoutScreen` | 池镜像、轮盘映射镜像 | 配置包（§7.6.4）、`SAVE` 时 `sendSyncRequest` | 不直接改池槽内容 |

### 8.3 服务端 `WheelCastHandler` 与 `MagicHandle` 改动

**新增公开 API（`[建议新增]`，只加一个）**：

```java
// MagicHandle
/**
 * 取消当前施法/蓄力，不产生效果。
 *  - CHARGE ：直接 reset（不扣蓝、不进冷却）
 *  - KEEP   ：走 stopChannel → 一定回调 onKeepStop（启动耗蓝/冷却已付，不退）
 */
public static void cancelCasting(@NotNull ServerPlayer player)
```

其余全部复用现有公开入口（`[已实现]`）：`handleUseStart` / `handleUseTick` / `handleUseRelease` /
`peekState` / `getPool` / `getMana`。**不需要改 `StaffBase`，也不需要伪造原版"使用中"状态**
—— 原因：轮盘自己驱动 tick（会话里每服务端 tick 调一次 `handleUseTick`），
`MagicHandle` 的时间轴只依赖 `State` 与传入的 `heldTicks`，不依赖 `player.getUsedItemHand()`。

> 反例（不要这么做）：让客户端模拟右键触发 `StaffBase#use`。那会同时出现两份 use 状态，
> `releaseUsing` 与轮盘 `END` 谁先到不确定，可能"取消时反而放出去"。

**每 tick 驱动**：`WheelCastHandler` 挂在 `LivingEvent.LivingTickEvent`（`MagicEventHandlers` 已有同款用法，
`[已实现]`）或 `PlayerTickEvent.Post`，只处理"有会话的 `ServerPlayer`"。

### 8.4 网络注册

```
NetworkHelper.register()                 // [已实现] 的既有位置
 ├─ VoidSwordStrikePacket.register()     // 已有
 ├─ MagicPoolActionPacket.register()     // 已有；本次给 handle() 加 5 个 case
 ├─ WheelCastPacket.register()           // [新增] PacketActionRegistry.register(ACTION, handler)
 ├─ NETWORK.registerMessage(packetId++, UniversalPacket...)
 ├─ ...
 ├─ NETWORK.registerMessage(packetId++, ManaSyncPacket..., PLAY_TO_CLIENT)        // 已有
 ├─ NETWORK.registerMessage(packetId++, MagicPoolSyncPacket..., PLAY_TO_CLIENT)  // 已有
 └─ NETWORK.registerMessage(packetId++, WheelCastFeedbackPacket..., PLAY_TO_CLIENT) // [新增，追加在最后]
```

* 客户端注册点：`ClientPacketHandlers.init()`（`[已实现]`，由 `ClientModEventHandler.onClientSetup` 调用）
  里调 `SpellWheelHud.register()` / `WheelHudInput.register()`。
* C2S 走 `UniversalPacket`，所以**包序号不变**；S2C 追加在 `register()` 末尾（两端同版本才连得上，
  Forge 本来就会做版本/通道校验）。

### 8.5 为什么轮盘渲染走"立即绘制"

与 `MagicGuiShaders` 完全同一套规矩（`[已实现]` 的类注释已总结过）：

| 规矩 | 原因 | 轮盘上的体现 |
| --- | --- | --- |
| 先 `gui.flush()` | GUI 批处理里可能压着前面的顶点，不刷出去会被自绘内容盖住 | 第一个自绘块（`wheel_aura`）之前 `gui.flush()` 一次；本帧**所有立即绘制块连续画完**，之后才画 GuiGraphics 批处理元素（图标/文字/法力条），避免反复 flush |
| uniform 设完立刻画 | `Uniform.set()` 只记值，真正上传发生在这次 draw 的 `ShaderInstance.apply()` | 每个扇区的 `uStartAngle/uEndAngle/uCooldown/...` 设完马上 `drawWithShader`，中间不插别的绘制 |
| 画完还原 RenderSystem | 后面还要画图标、文字、法力条、tooltip 与世界 | 恢复 `depthMask(true)`、`enableDepthTest()`、`defaultBlendFunc()`、`GameRenderer::getPositionTexShader` |

为什么不用 `RenderType`：轮盘的几何与状态**每帧都在变**（角度不变但冷却比例、hover、蓄力比例、
图标位置缩放都在变），且一共只有 8 个扇形 + 1 个核心 + 1 个光环 + ≤8 段连线；
`BufferUploader.drawWithShader` 一次提交一个 quad 已经足够便宜，反而省掉一整套
`StateShard`（剔除/深度/写掩码/混合）的默认值维护——项目里所有 GUI 自绘（tooltip 底板、
创造栏隔断、坍缩血条、难度法阵）都是这条路（`[已实现]`）。

**血泪教训（项目已发生过的）**：自定义 shader 画完必须把 shader 还原成
`GameRenderer::getPositionTexShader`，否则关闭界面后世界/暂停菜单还在用你的 program
（`MagicGuiShaders` 每个方法末尾都有一行注释专门提醒，`[已实现]`）。

### 8.6 着色器落位与 uniform 契约

设计稿的主视觉由三支**工具私有着色器**画：`wheel_slice` / `wheel_aura` / `wheel_core`
（`tools/gui-preview/shaders/`，已落地）。工具场景里它们缺失时 `WheelKit#hasDesignShaders`
会退化成 CPU 分层弧线、不会白屏。
**注意：当前的 `build/gui-preview/spell_wheel.png` 是着色器落地前用 CPU 兜底出的图，
视觉验收前需重跑一次场景。** 实机移植时：

* 文件放 `src/main/resources/assets/rotten_ruins/shaders/core/`，命名建议 `magic_wheel_*`（避免与工具私有名混淆）；
* 顶点格式与现有 GUI shader 一致：`DefaultVertexFormat.POSITION_TEX`，顶点写**屏幕坐标**，`ProjMat` 由
  `RenderSystem` 提供、`ModelViewMat` 用单位阵（`MagicGuiShaders.IDENTITY` 同款）；
* 注册进 `SplendidingShaders.onRegisterShaders`，`try/catch (Throwable)` + `LOGGER.error`（`[已实现]` 的统一写法）；
* **uniform 名字一个都不要改**——设计稿已经按下面这套调用（`WheelKit` 实测）：

| 着色器 | uniform | 类型 | 含义 |
| --- | --- | --- | --- |
| `wheel_slice` | `uCenter` | vec2 | 轮盘中心（屏幕坐标） |
| | `uQuadOrigin` / `uQuadSize` | vec2 | 本扇区 quad 的左上角/尺寸 |
| | `uRadius` / `uInnerRadius` | float | 外/内半径 |
| | `uStartAngle` / `uEndAngle` | float | 弧度（atan2 约定，0=右、正=下） |
| | `uColorA` / `uColorB` | vec4 | 流派色亮部/深底 |
| | `uTime` | float | 秒 |
| | `uHover` / `uSelected` / `uLocked` / `uCooldown` | float 0..1 | 四态权重（可叠加） |
| | `uIntensity` | float | 总透明度 |
| `wheel_aura` | `uCenter` / `uQuadOrigin` / `uQuadSize` | vec2 | 同上 |
| | `uOuter` / `uInner` | float | 光环内外半径 |
| | `uTime` | float | 秒 |
| | `uHoverAngle` | float | 选中方向（弧度） |
| | `uHoverStrength` | float | 锥形辉光强度（选中 0.9 / hover 0.45） |
| | `uIntensity` | float | 总强度（装载界面 0.55×） |
| | `uColorA/B/C` | vec4 | 金 / 青 / 紫雾 |
| `wheel_core` | `uCenter` / `uQuadOrigin` / `uQuadSize` | vec2 | 同上 |
| | `uCoreRadius` | float | 核心半径 |
| | `uTime` | float | 秒 |
| | `uMana` | float 0..1 | 全局法力比例（法力环） |
| | `uCharge` | float 0..1 | 蓄力比例（脉冲环） |
| | `uCooldown` | float 0..1 | 选中法术冷却比例（逆时针环） |
| | `uColorA` / `uColorB` | vec4 | 流派色 / 亮金 |
| | `uIntensity` | float | 总强度 |

降级策略（照抄 `MagicGuiShaders` 的约定，`[已实现]`）：三支 shader 任一为 `null`，绘制退回
**纯 CPU 分层弧线**（`WheelKit` 的 `drawFallbackSlice/drawFallbackCore` 就是那份参考实现），
功能与交互不受影响，只是没有流动质感。

### 8.7 lang key 清单

新增（`datagen/gen/lang/LangDataEN.java` 与 `LangDataCN.java` 同时加，然后跑 datagen）：

| key | en_us | zh_cn |
| --- | --- | --- |
| `key.rotten_ruins.cast_wheel` | `Cast Spell Wheel` | `施法轮盘` |
| `rotten_ruins.gui.wheel.title` | `ARCANE WHEEL` | `魔法轮盘` |
| `rotten_ruins.gui.wheel.subtitle` | `HOLD  [%s]   ·   AIM   ·   RELEASE TO CAST` | `按住 [%s] · 瞄准 · 松手释放` |
| `rotten_ruins.gui.wheel.aim` | `AIM A SIGIL` | `瞄准一个符文` |
| `rotten_ruins.gui.wheel.release_hint` | `release to cast` | `松手释放` |
| `rotten_ruins.gui.wheel.release_to_cast` | `RELEASE TO CAST` | `松手释放` |
| `rotten_ruins.gui.wheel.cancel` | `RMB  CANCEL` | `右键 取消` |
| `rotten_ruins.gui.wheel.mana` | `MANA  %s / %s` | `法力  %s / %s` |
| `rotten_ruins.gui.wheel.ready` | `READY  ·  %s MANA` | `就绪  ·  耗蓝 %s` |
| `rotten_ruins.gui.wheel.cooldown` | `COOLDOWN %s` | `冷却 %s` |
| `rotten_ruins.gui.wheel.need_mana` | `NEED %s MANA` | `需要 %s 法力` |
| `rotten_ruins.gui.wheel.charging` | `CHARGING %s / %s s` | `蓄力 %s / %s 秒` |
| `rotten_ruins.gui.wheel.sustaining` | `SUSTAINING %s s` | `维持 %s 秒` |
| `rotten_ruins.gui.wheel.empty_slot` | `EMPTY` | `空位` |
| `rotten_ruins.gui.wheel.no_active_spells` | `NO ACTIVE SPELLS — equip one in the spell constellation` | `没有可用的主动法术 —— 先在魔法星图里装备` |
| `rotten_ruins.gui.wheel.all_unavailable` | `ALL SPELLS UNAVAILABLE` | `所有法术暂不可用` |
| `rotten_ruins.gui.wheel.busy` | `ALREADY CASTING` | `正在施法` |
| `rotten_ruins.gui.wheel.hold_staff` | `HOLD A STAFF` | `需要手持法杖` |
| `rotten_ruins.gui.wheel.page_indicator` | `WHEEL` | `轮盘` |
| `rotten_ruins.gui.wheel.result.on_cooldown` | `ON COOLDOWN` | `冷却中` |
| `rotten_ruins.gui.wheel.result.not_enough_mana` | `NOT ENOUGH MANA` | `法力不足` |
| `rotten_ruins.gui.wheel.result.not_charged` | `NOT FULLY CHARGED` | `蓄力未满，已作废` |
| `rotten_ruins.gui.wheel.result.no_target` | `NO TARGET` | `没有目标` |
| `rotten_ruins.gui.wheel.result.passive` | `PASSIVE SPELLS CANNOT BE CAST` | `被动法术无法释放` |
| `rotten_ruins.gui.wheel.result.no_spell` | `EMPTY WHEEL SLOT` | `轮盘位为空` |
| `rotten_ruins.gui.wheel.result.no_staff` | `HOLD A STAFF` | `需要手持法杖` |
| `rotten_ruins.gui.wheel.result.rejected` | `CANNOT CAST NOW` | `现在无法施放` |
| `rotten_ruins.gui.wheel.result.cancelled` | `CAST CANCELLED` | `施法被取消` |
| `rotten_ruins.gui.wheel.result.busy` | `ALREADY CASTING` | `正在施法` |
| `rotten_ruins.gui.wheel.loadout.title` | `SPELL LOADOUT` | `魔法装载` |
| `rotten_ruins.gui.wheel.loadout.subtitle` | `configure the eight wheel sigils` | `配置八格轮盘符文` |
| `rotten_ruins.gui.wheel.loadout.tab.active` | `ACTIVE` | `主动` |
| `rotten_ruins.gui.wheel.loadout.tab.passive` | `PASSIVE` | `被动` |
| `rotten_ruins.gui.wheel.loadout.passive_note` | `PASSIVE — ALWAYS ACTIVE, CANNOT BE PLACED ON THE WHEEL` | `被动 —— 恒常生效，不能装载到轮盘` |
| `rotten_ruins.gui.wheel.loadout.passive_note2` | `PASSIVE SPELLS CANNOT BE PLACED ON THE WHEEL` | `被动法术不能装载到轮盘` |
| `rotten_ruins.gui.wheel.loadout.preview` | `WHEEL PREVIEW` | `轮盘预览` |
| `rotten_ruins.gui.wheel.loadout.bound_count` | `WHEEL %s · %s / %s SIGILS BOUND` | `轮盘 %s · 已绑定 %s / %s` |
| `rotten_ruins.gui.wheel.loadout.details` | `DETAILS` | `法术详情` |
| `rotten_ruins.gui.wheel.loadout.stat.type` | `TYPE` | `类型` |
| `rotten_ruins.gui.wheel.loadout.stat.mana` | `MANA` | `耗蓝` |
| `rotten_ruins.gui.wheel.loadout.stat.cooldown` | `COOLDOWN` | `冷却` |
| `rotten_ruins.gui.wheel.loadout.stat.charge` | `CHARGE` | `蓄力` |
| `rotten_ruins.gui.wheel.loadout.stat.sustain` | `SUSTAIN` | `维持` |
| `rotten_ruins.gui.wheel.loadout.stat.assigned` | `ASSIGNED` | `已绑定` |
| `rotten_ruins.gui.wheel.loadout.assigned` | `WHEEL %s · SLOT %s` | `轮盘 %s · 第 %s 位` |
| `rotten_ruins.gui.wheel.loadout.not_assigned` | `NOT ASSIGNED` | `未绑定` |
| `rotten_ruins.gui.wheel.loadout.description` | `DESCRIPTION` | `描述` |
| `rotten_ruins.gui.wheel.loadout.page_label` | `WHEEL PAGE` | `轮盘页` |
| `rotten_ruins.gui.wheel.loadout.btn.autofill` | `AUTO-FILL` | `自动填充` |
| `rotten_ruins.gui.wheel.loadout.btn.reset` | `RESET` | `重置` |
| `rotten_ruins.gui.wheel.loadout.btn.reset_confirm` | `CONFIRM?` | `确认？` |
| `rotten_ruins.gui.wheel.loadout.btn.save` | `SAVE` | `保存` |
| `rotten_ruins.gui.wheel.loadout.hint` | `Drag a spell onto a slot` | `拖动法术到轮盘位` |
| `rotten_ruins.gui.wheel.loadout.hint2` | `Right-click a slot to unbind` | `右键轮盘位卸下` |
| `rotten_ruins.gui.wheel.loadout.hint3` | `Changes apply instantly` | `改动即时生效` |
| `rotten_ruins.gui.wheel.loadout.empty` | `No spells in the magic pool` | `魔法池里没有法术` |
| `rotten_ruins.gui.wheel.loadout.empty_slot` | `EMPTY WHEEL SLOT — drag a spell here` | `空轮盘位 —— 把法术拖到这里` |
| `rotten_ruins.gui.wheel.loadout.not_equipped` | `EQUIP IN CONSTELLATION FIRST` | `请先在魔法星图里装备` |
| `rotten_ruins.gui.wheel.loadout.slot_occupied` | `SLOT OCCUPIED` | `该位已被占用` |
| `rotten_ruins.gui.wheel.loadout.toast.bound` | `%s bound to wheel %s · slot %s` | `%s 已绑定到轮盘 %s · 第 %s 位` |
| `rotten_ruins.gui.wheel.loadout.toast.unbound` | `Unbound from wheel slot %s` | `已从第 %s 位卸下` |
| `rotten_ruins.gui.wheel.loadout.toast.autofill` | `Auto-filled %s slot(s)` | `已自动填充 %s 个轮盘位` |
| `rotten_ruins.gui.wheel.loadout.toast.reset` | `Wheel page cleared` | `轮盘页已清空` |
| `rotten_ruins.gui.wheel.loadout.toast.saved` | `Wheel saved` | `轮盘已保存` |
| `rotten_ruins.gui.wheel.loadout.toast.conflict` | `That spell is already on this wheel page` | `该法术已在当前轮盘页` |
| `rotten_ruins.gui.wheel.loadout.toast.passive` | `Passive spells cannot be placed on the wheel` | `被动法术不能装载到轮盘` |
| `rotten_ruins.gui.wheel.loadout.toast.not_equipped` | `Equip the spell in the magic pool first` | `请先把法术放进魔法池` |
| `rotten_ruins.gui.wheel.loadout.toast.rejected` | `Change rejected by the server` | `服务端拒绝了这次修改` |
| `rotten_ruins.gui.wheel.loadout.footer` | `M  BACK   ·   ESC  CLOSE` | `M  返回 · ESC  关闭` |

> 为什么全大写：设计稿里的位图字体没有小型大写（`small caps`），全大写直接用 `en_us` 原字符串，
> 和 `MagicScreen` 的 `SPELL CONSTELLATION` 风格一致；中文不受影响。
> 复用现有 key：`rotten_ruins.gui.magic.kind.active/passive`、`...mana`、`...cooldown`、`...charge`
> 可直接用于详情面板的同义行（`[已实现]`）。

### 8.8 HUD 挂载点

| 关注点 | 做法 | 依据 |
| --- | --- | --- |
| 绘制 | `RenderGuiOverlayEvent.Post`，只处理 `VanillaGuiOverlay.HOTBAR`（每帧一次） | 项目已有同样写法：`GuiShaderRenderer.onRenderGuiOverlayPost`（`[已实现]`） |
| 隐藏原版准星 | `RenderGuiOverlayEvent.Pre`（`CROSSHAIR`）在轮盘打开时取消，核心中心画 2×2 金点 | 轮盘核心会盖住准星；不取消会出现"文字上叠十字" |
| 按住检测 | `ClientTickEvent.End` 里读 `CAST_WHEEL.isDown()` | `consumeClick` 拿不到按住/松开（§2.2） |
| 鼠标增量 | `MouseHandlerMixin` 在 `turnPlayer(dx, dy)` 头部累加（仅轮盘打开时） | 1.20.1 grabbed 状态下 `xpos/ypos` 不更新，只能取增量；项目已有 mixin 基建 |
| 右键取消 | `InputEvent.InteractionKeyMappingTriggered`（`isUseItem()`）+ 取消事件 | 项目已有注册写法：`VoidSwordClientHandler.register()`（`[已实现]`） |
| `Esc` | 优先试 `InputEvent.Key` + 取消；不行则 `KeyboardHandlerMixin` | 见 §6.1、§10.3 验证项 |
| 数字键 1..8 | 轮盘打开时在 `InputEvent.Key` 里消费（不改变关闭时的快捷栏行为） | —— |

### 8.9 渲染状态还原清单

整段立即绘制结束时（每帧一次；若中间不得不插入 GUI 批处理元素，则每个立即绘制块各做一次）：

1. `RenderSystem.depthMask(true)`
2. `RenderSystem.enableDepthTest()`
3. `RenderSystem.disableBlend()` 或 `RenderSystem.defaultBlendFunc()`（按下一块需要）
4. `RenderSystem.setShaderColor(1, 1, 1, 1)`
5. `RenderSystem.setShader(GameRenderer::getPositionTexShader)`

> 这五步和 `MagicGuiShaders` 三个方法末尾完全一致（`[已实现]`）。漏第 5 步的典型症状：
> 关闭轮盘后暂停菜单/物品栏一片漆黑或错色。

---

## 九、性能与兼容

### 9.1 绘制预算

| 项 | 数量 | 备注 |
| --- | --- | --- |
| `wheel_aura` quad | 1 | 覆盖 `OUTER×2.8` 的方框 |
| `wheel_slice` quad | 8 | 每扇区一次 draw call |
| `magic_constellation` 线段 | ≤ 8 | 每个已绑定方位一段（复用现有 shader） |
| `wheel_core` quad | 1 | —— |
| CPU 装饰 | 外圈双环 + 60 刻度 + 内环 + 选中弧 + 蓄力环 | 每项 1 次 `BufferBuilder` 提交（立即绘制；顶点全部预计算） |
| 图标 | ≤ 8 | `renderItem`（原版批处理） |
| 文字 | ≤ 24 行 | 8 键位 + 核心 5 行 + 标题 2 行 + 法力 3 行 + 页指示 |
| **合计** | ≈ 20 次 draw call / 帧 | 只在按住施法键期间发生；关闭态零成本 |

优化要求：

* 所有角度/顶点在**打开时预计算一次**，帧内只更新 uniform 与颜色（角度 45° 固定不变）。
* 每帧不 new 数组：使用 `float[8]` 级别的字段缓存；`WheelOption` 复用（`WheelKit` 原型已经是这个结构）。
* 文字只在值变化时重拼（冷却读秒按 0.1 s 步进，避免每帧格式化字符串）。
* 关闭态不注册任何 tick 逻辑（HUD 的 `ClientTickEvent` 里 `if (!open) return;` 最先返回）。

### 9.2 光影包（Oculus / Iris）

* **GUI 通道不参与延迟渲染**：光影包只接管世界渲染，GUI 在 composite 之后直接写主 framebuffer，
  所以轮盘/装载界面的立即绘制**不需要**走项目里那套"延迟队列 + 回放"（那是物品世界渲染用的，
  见 `shader-pack-compat.md`）。轮盘与 `MagicScreen` 同属 GUI，安全。
* 但要验证：光影激活时 `RenderSystem` 的投影矩阵是否仍是 GUI 正交矩阵（原版保证，但 Oculus 改过
  framebuffer 绑定）；验收项见 §10.3。
* 禁用自定义 `RenderType`（见 §8.5）本身就避开了一整类光影兼容问题。

### 9.3 暂停 / 暂停菜单 / 窗口

| 场景 | 期望行为 |
| --- | --- |
| 轮盘打开时按 `Esc` | 关闭轮盘，不开暂停菜单（若 `InputEvent` 拦不住则退化为"关轮盘 + 开暂停"，见 §10.3） |
| 轮盘打开时打开任意 `Screen`（如 `E`、`M`、`T`） | 立即取消并关闭轮盘（检测 `mc.screen != null`） |
| 窗口失焦 / 最小化 | 同上，取消（`PlayerEvent` 不会发，需在 client tick 里检测 `mc.isWindowActive()`） |
| 单机暂停菜单弹出 | 轮盘已关闭，无残留 |
| F1 隐藏 GUI | 轮盘不绘制；按住状态被取消 |
| 死亡 / 换维度 | 服务端清会话；客户端关闭 |

### 9.4 多人延迟

| 环节 | 处理 |
| --- | --- |
| 打开轮盘 | 纯客户端，零延迟 |
| 选中/蓄力显示 | 纯客户端本地计时（`CHARGE` 比例显示会比服务端早一个 RTT 左右） |
| 冷却读秒 | 客户端 `PlayerMagicPool#tickCooldowns()` 本地倒计时（`[已实现]`），不依赖包 |
| 法力 | `ManaSyncPacket` 每 2 tick 推一次（`[已实现]`），最多 ~100 ms 滞后 |
| 释放 | 客户端乐观关闭 + 闪光；结果由 `WheelCastFeedbackPacket` 回执纠正（失败时 toast 原因） |
| 轮盘配置 | 即时提交 + 250 ms 内回推全量快照；`SAVE` 前强制同步一次防漂移 |

### 9.5 失败降级矩阵

| 失败点 | 降级行为 |
| --- | --- |
| 三支 `magic_wheel_*` shader 加载失败 | CPU 分层弧线（`WheelKit` 的 fallback 是参考实现），交互不变 |
| `magic_starfield` 失败（装载界面背景） | 静态分层渐变 + 固定星点（`MagicScreen.renderBackground` 同款兜底，`[已实现]`） |
| 轮盘映射 NBT 缺失/损坏 | 全空 + 页 0；`sanitize()` 丢弃非法项 |
| `WheelCastFeedbackPacket` 未收到（丢包/断线） | HUD 1.2 s 后超时静默，状态回 `CLOSED`；冷却/法力仍按镜像更新 |
| 服务端没有 `WheelCastHandler`（旧存档/旧服务端） | c2s 包无 handler → 无响应；客户端超时提示"服务端不支持轮盘"（可选 P2） |

---

## 十、预览与验证

### 10.1 设计稿产物

设计稿由 `tools/gui-preview`（不启动 Minecraft 的离屏 GUI 预览工具）生成：

```powershell
# 轮盘 HUD：4 个状态帧
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_wheel -Time 3.2"

# 装载界面（SceneSpellLoadout；坐标以本文 §3.7 为准）
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_loadout -Time 3.0"
```

| 场景 | 产物（`build\gui-preview\`） | 内容 |
| --- | --- | --- |
| `spell_wheel` | `spell_wheel.png` | 主视觉：选中火球（位 1）；位 2 审判之矛在冷却、位 3 抑制场；5 个空位符文 |
| | `spell_wheel_idle.png` | 刚打开、未选中：核心 `AIM A SIGIL` / `release to cast` |
| | `spell_wheel_charge.png` | 审判之矛蓄力中（charge 0.62），核心 `CHARGING`，外圈蓄力环 |
| | `spell_wheel_cooldown.png` | 选中位冷却 + 其它位蓝不足（冷色锁定） |
| `spell_loadout` | `spell_loadout.png` | 左图鉴（ACTIVE 页）/ 中轮盘预览 / 右详情 / 底部页签与按钮 |

* 画布 640×400、原生渲染 ×2 = 1280×800 PNG；文字一律英文（位图字体只含拉丁字符）。
* 场景源码：`tools/gui-preview/SceneSpellWheel.java`（已落地，产出上面 4 帧）、
  `tools/gui-preview/WheelKit.java`（HUD 与装载界面共用的渲染器/数据原型，已落地）、
  `SceneSpellLoadout.java`（**尚未落地**；本文 §3.7 的坐标表即其规格）。
* 三支 `wheel_*` 工具着色器（`wheel_slice` / `wheel_aura` / `wheel_core`）**已落地**在
  `tools/gui-preview/shaders/`；`WheelKit` 在它们缺失时会自动退化 CPU 绘制（`hasDesignShaders()`），
  所以设计稿不会白屏。
* **当前 `spell_wheel.png` 四帧是在着色器落地前生成的 CPU 兜底版本**（扇区是分层弧线、没有流动质感）。
  视觉验收前必须重跑 `-Scene spell_wheel`，以 shader 版为准。

### 10.2 预览与实机的已知差异

| 项 | 预览 | 实机 |
| --- | --- | --- |
| 背景 | 程序化夜空/山脊/月亮（`SceneSpellWheel#drawNightWorld`） | 真实世界（轮盘背后压暗 + `wheel_aura` 柔光） |
| 鼠标 | 场景直接给一个示意坐标 | 虚拟光标（鼠标增量 + mixin），无系统光标 |
| 法力数值 | 演示值（182 / 240） | `TwistedPoint` 真值（上限默认 1000，可被属性改） |
| 动画 | `-Time` 采样单帧；打开进度用 `openT` 参数 | 真实时间轴（180 ms 打开 / 90 ms 选中 / 冷却扫过） |
| 中文 | 不显示（位图字体无中日韩） | 由 lang 提供（`zh_cn.json`） |
| 底色 | 场景自定义压暗程度 | 由 GUI 状态决定（`mc.options.hideGui` 等） |
| 文案 | 场景直接用英文常量（与 `en_us` 同值） | 走 lang key（`rotten_ruins.gui.wheel.*`），中文由 `zh_cn.json` 提供；长文案需复核是否撑破核心/面板 |
| 轮盘状态 | 每帧由 `WheelOptions` 直接指定（冷却比例、蓄力比例、法力值等） | 由 `PlayerMagicPool` 冷却镜像 + `TwistedPoint` 法力现算 |

### 10.3 上线前进游戏验证清单

**HUD**

- [ ] 按住 `R` 打开：180 ms 内出现，移动/跳跃/受击都不受影响（不暂停）
- [ ] 松手释放：火球立刻打出；审判之矛需按住 ≥ 2 s（未满时给 `NOT CHARGED` 且不扣蓝/不进冷却）
- [ ] 抑制场：停留 250 ms 起手 → 轮盘收拢为维持条，松手结束，`onKeepStop` 生效
- [ ] 右键取消：轮盘关闭，**不会**顺带把法杖右键放出去
- [ ] `Esc` 取消且不打开暂停菜单（若拦不住，记录并接受降级）
- [ ] 空位方向松手 = 取消；死区松手 = 取消
- [ ] 蓝不足扇区冷色锁定，中心 `NEED x MANA`；全冷却时全部读秒
- [ ] 没有主动法术：不进入轮盘，提示 2.5 s
- [ ] 数字键 1..8 选中后松手可释放
- [ ] 关闭后原版准星/暂停菜单/物品栏渲染正常（状态还原无泄漏）

**装载界面**

- [ ] `M` → `WHEEL` 页签进入；`Esc` 关回游戏；游戏不暂停
- [ ] ACTIVE 页列出池内主动法术；PASSIVE 页只读、有锁图标与提示
- [ ] 拖拽绑定/卸下/移动；非法目标铺红、合法目标亮金框并吸附
- [ ] 重复绑定同一法术 → 服务端拒绝 + toast
- [ ] 自动填充只填主动、不覆盖、页内不重复
- [ ] 重置需要二次确认
- [ ] SAVE 后关闭并强制同步；重开界面状态一致
- [ ] 池槽缩容（用命令改 `magic_slots` 属性）后越界轮盘位自动清空

**网络/多人**

- [ ] 高延迟（`/ping` 或本地模拟 200 ms）下蓄力比例与冷却显示可接受
- [ ] 重登/换维度后轮盘映射与页数保留
- [ ] 两个玩家各自独立：A 的轮盘映射不影响 B；同名物品共享冷却仍成立

**兼容/性能**

- [ ] Oculus/Iris 光影开启时轮盘与装载界面正常（不黑、不错色）
- [ ] 视距/窗口 resize 后轮盘居中、底部条不裁切（§3.5）
- [ ] F1 隐藏 GUI 时轮盘不绘制
- [ ] 长时间按住（如维持 20 s）无内存增长（每帧零分配）
- [ ] F3 帧时间在轮盘打开时不明显上升（目标 < 1 ms/帧 GUI 增量）

---

## 十一、分期计划与待定问题

### 11.1 分期

| 阶段 | 范围 | 交付 |
| --- | --- | --- |
| **P0（可用）** | 轮盘 HUD 主体（8 方位、3 种 UsingType、冷却/蓝不足/空位/兜底）、施法包与回执、轮盘映射 NBT（结构一次到位：`PAGES×SLOTS` 24 个 int；P0 只暴露第 1 页）、装载界面（单页：绑定/卸下/自动填充/重置/保存）、三支 shader 的 CPU 兜底 | 能完整玩：按住 → 选 → 放 |
| **P1（完整）** | 轮盘页 I/II/III（切页 + 滚轮）、第二/三页映射与自动填充、`MagicScreen` 页签与文字色统一、`SAVE` 语义最终化、可访问性打磨 | 3 页预设 |
| **P2（打磨）** | `KEEP` 维持条副 HUD 的额外表现、数字键/手柄径向支持、命中/冷却预测优化、`spell_wheel_keep.png` 设计帧、旧服务端兼容提示 | 手感与兼容 |

> 三支 `wheel_*` shader 的实机移植可以晚于 P0（P0 用 CPU 分层弧线也能玩），但**视觉验收以 shader 版为准**。

### 11.2 待定问题

| # | 问题 | 现状 / 建议 |
| --- | --- | --- |
| 1 | 轮盘页切换的输入：滚轮 vs 按住 `Ctrl` + 滚轮 vs 只做三键 | P1 建议 `Ctrl`+滚轮（避免和快捷栏冲突），滚轮单独滚动太容易误触 |
| 2 | 跨页唯一性 | 当前设计**允许**同一法术出现在不同页（预设页的意义）；若要全局唯一，在 `WheelSlots#sanitize` 加一条即可，需要产品确认 |
| 3 | `SAVE` 语义：即时生效 + 确认关闭 vs 本地草稿一次性提交 | 采用前者（与 `MagicScreen` 的服务端权威一致）；若团队要"未保存提示"，需改成草稿模式并加回滚，工作量 +1 天 |
| 4 | `CHARGE` 蓄力开始时机：选中即开始（120 ms 阈值）vs 需要点击确认 | 设计取前者（零代价、松手即验）；若测试中误触明显再调阈值 |
| 5 | `KEEP` 起手阈值 250 ms | 需实机手感测试；阈值太低误触、太高"按了没反应" |
| 6 | 是否支持副手法杖 | P0 只支持主手；P1 评估（`StaffBase` 本身两只手都能用） |
| 7 | 轮盘映射"跟着槽位走"（存索引） | 已按 P0 采用；如果玩家在 `MagicScreen` 里频繁挪槽导致轮盘错位，再评估改成存法术 id |
| 8 | 被动法术的实际实现（槽位 tick / equip 框架） | `[规划]`，本设计不依赖；被动示例只出现在装载界面 PASSIVE 页与文档 |
| 9 | 装载界面是否需要"新法术"提示（背包里未装备高亮） | P1；当前只做灰卡 + 提示 |
| 10 | 轮盘是否常驻显示"上次使用的法术"小图标 | 当前不做（保持 HUD 干净）；法力条只在轮盘打开时出现 |

### 11.3 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `InputEvent.Key` 无法拦截 `Esc` | 取消会顺带打开暂停菜单 | `KeyboardHandlerMixin` 兜底；最差降级为"关轮盘 + 开暂停" |
| `MouseHandler` 映射名/签名随版本变化 | 虚拟光标取不到增量 | mixin 只依赖 `turnPlayer` 这一处；实机验证清单单列 |
| 蓄力期间玩家被抽干法力 | 松手 `NOT_ENOUGH_MANA`，玩家觉得"白蓄了" | 中心在 `CHARGING` 时同时显示当前法力，低于耗蓝时提前变红提示 |
| 轮盘遮挡视野导致战斗体验差 | 玩家抵触 | 180 ms 打开 + `KEEP` 收拢 + 半径只占屏高 71% |
| 三页 × 8 位让配置界面变复杂 | 玩家不配置 | 自动填充一键搞定；默认首次绑定自动填充第 1 页（P1 可做引导） |

---

## 附录 A：源码事实核对表

本文所有 `[已实现]` 结论的出处（路径相对仓库根）：

| # | 事实 | 出处（符号） | 用于 |
| --- | --- | --- | --- |
| 1 | 分类轴 = 主动/被动，与流派/使用方式正交；默认 `ACTIVE`；被动不读 `UsingType` | `items/magic/bases/SpellKind.java`、`IMagicBaseItem#spellKind()` | §0、§1.3、§6.5 |
| 2 | 三种使用方式：`IMMEDIATELY` / `CHARGE` / `KEEP` 及各自时间轴与耗蓝语义 | `items/magic/bases/UsingType.java` | §0.1、§6.2 |
| 3 | 流派枚举共 7 个：`ACE/FIRE/DEATH/NATURE/LIGHT/VOID/MAGIC` | `items/magic/bases/MagicType.java` | §4.1 |
| 4 | `BASE_MAX_MANA=1000`、`BASE_REGEN_PER_SECOND=1.0`、`BASE_SLOT_COUNT=5`、`MAX_SLOT_COUNT=64`、`MAX_KEEP_TRIGGER_INTERVAL=200`、`MAX_TARGET_RANGE=128` | `api/magic/MagicStats.java` | §0.1、§7 |
| 5 | 所有法术数值必须走 `MagicStats`（属性/附魔折叠）；冷却键 = `magicId` = 物品注册名 | 同上（`manaCost/cooldownTicks/chargeTicks/magicId/...`） | §0.1、§1.3、§4、§7 |
| 6 | 火球 40 蓝 / 30 tick / IMMEDIATELY / 伤害 6；爆炸半径 2.5 钳 `[0.5,12]` | `items/magic/its/FireBall.java` | §0.1 |
| 7 | 审判之矛 100 蓝 / 600 tick / CHARGE 40 tick / `minChargeRatio=1` / 伤害 50 / 射程 48 | `items/magic/its/SpearOfJudgment.java` | §0.1、§6.2 |
| 8 | 抑制场 80 蓝 + 50/秒 / 240 tick / KEEP / 每 5 tick / 伤害 20 / 半径 5 | `items/magic/its/SuppressionField.java` | §0.1、§6.2 |
| 9 | 魔法池 NBT：`Items` / `HallMagicSelected` / `HallMagicCooldowns`；冷却按注册名共享；客户端也 tick 冷却但只显示 | `capability/PlayerMagicPool.java` | §7.4、§9.4 |
| 10 | 池槽可见数 = `MagicStats.slotCount`（默认 5，可扩到 64）；缩容时把物品退回背包 | 同上（`getSlots` / `ensureSlotCount`） | §1.3、§6.5 |
| 11 | 玩家魔法池/法力都通过 `MagicHandle.getPool/getMana` 取；`selectedSlot` 由界面写入 | `bases/MagicHandle.java` | §2.4、§7 |
| 12 | 释放时序：查槽 → 冷却/前置 → 目标 → 事件 → 扣蓝 → 进冷却 → `cast` → Post | `bases/MagicHandle.java`（`handleUseStart/handleUseRelease/castNow`） | §5、§7.7 |
| 13 | `CHARGE` 比例 = `heldTicks / chargeTicks`，低于 `minChargeRatio` → `NOT_CHARGED`（不扣蓝不进冷却） | `MagicStats#chargeRatio` + `handleUseRelease` | §6.2、§5.4 |
| 14 | `KEEP` 起手即扣蓝进冷却、松手结束；短暂按住也吃满冷却 | `handleUseStart`（KEEP 分支）+ `UsingType` 注释 | §6.2、§6.6 |
| 15 | `CastResult` 枚举值（`SUCCESS/ON_COOLDOWN/NOT_ENOUGH_MANA/NOT_CHARGED/NO_TARGET/PASSIVE_SPELL/...`） | `api/magic/CastResult.java` | §5.4、§7.6.3 |
| 16 | 法力 `TwistedPoint`：`getMana/getMaxMana/getFillRatio/spend`；`applyServerState` 接收同步 | `capability/TwistedPoint.java` + `s2c/ManaSyncPacket.java` | §9.4 |
| 17 | GUI 槽位界面：`gold_slot.png` 26×26、`COL_GOLD=0xFFE7C46A`、`COL_GOLD_DIM=0x99C9A227`、`OPEN_ANIMATION_MS=500`、侧栏渐变与描边、拖放/双击/右键/选中、`isPauseScreen()=false` | `client/gui/MagicScreen.java` | §3、§4、§6.4 |
| 18 | GUI shader 三支与三条规矩（flush / uniform 后立刻画 / 还原 RenderSystem + `GameRenderer::getPositionTexShader`） | `client/gui/MagicGuiShaders.java` | §8.5、§8.9 |
| 19 | 槽位操作包：`ACTION="rotten_ruins:magic_pool_action"`、`SYNC/SELECT/PLACE/TAKE/MOVE`、服务端全量重校验、`INVENTORY_SIZE=36` | `network/c2s/MagicPoolActionPacket.java` | §7.6.4 |
| 20 | 槽位同步：`MagicPoolSyncPacket(entityId, pool.serializeNBT())`；`MagicSync.POOL_INTERVAL_TICKS=5`、脏才发、`force` 用于登录/重生/换维度 | `network/s2c/MagicPoolSyncPacket.java`、`network/MagicSync.java` | §7.6.1 |
| 21 | 键位注册：`RegisterKeyMappingsEvent`（mod 总线）+ `KeyMapping("key.rotten_ruins.open_magic", IN_GAME, KEYSYM, M, "key.categories.rotten_ruins")`；客户端用 `consumeClick()` | `client/MagicClientHandler.java`、`event/ClientModEventHandler.java` | §2.2、§8 |
| 22 | 客户端事件挂在 `ClientPacketHandlers.init()`（由 `FMLClientSetupEvent` 调用）；`InputEvent.InteractionKeyMappingTriggered` 的注册写法 | `client/ClientPacketHandlers.java`、`client/VoidSwordClientHandler.java` | §6.1、§8.8 |
| 23 | HUD overlay 的既有挂载写法：`RenderGuiOverlayEvent.Post` + `VanillaGuiOverlay.HOTBAR` | `client/rend/gui/GuiShaderRenderer.java` | §8.8 |
| 24 | shader 注册写法（`RegisterShadersEvent` + `ShaderInstance(..., DefaultVertexFormat.POSITION_TEX)` + `LOGGER.error` 兜底） | `client/rend/SplendidingShaders.java` | §8.6 |
| 25 | mixin 基建：`rotten_ruins.mixins.json`（`package org.bytechen.rottenruins.mixin`、client 列表） | `src/main/resources/rotten_ruins.mixins.json` | §8.8 |
| 26 | lang 由 datagen 生成：`LangDataEN/LangDataCN` 的 `rotten_ruins.gui.magic.*` 段落 | `datagen/gen/lang/LangDataEN.java`、`LangDataCN.java` | §8.7 |
| 27 | 现有 lang key（`rotten_ruins.gui.magic.title/inventory/empty/hint/slots/kind/mana/cooldown/charge`、`key.rotten_ruins.open_magic`） | `src/generated/resources/assets/rotten_ruins/lang/en_us.json` | §8.7 |
| 28 | 设计稿工具：640×400 逻辑画布 = 1280×800 @ scale 2、`-Scale 2` 导出、8px 位图字体、`c.save()`、场景类命名 `Scene<CamelCase>`、PNG 永远不透明 | `tools/gui-preview/README.md`、`FRAMEWORK.md` | §3.1、§10.1 |
| 29 | 轮盘设计稿坐标/颜色/产物名：`CX=320, CY=204, OUTER=142, INNER=58`、法力条 250×8 @ y=366、标题 2x y=14、页指示右上；4 张 PNG | `tools/gui-preview/SceneSpellWheel.java`、`WheelKit.java` | §3.3、§4、§10.1 |
| 30 | 轮盘 shader 的 uniform 契约与调用值（slice/aura/core 三支，quad+uniform 驱动） | `tools/gui-preview/WheelKit.java#drawWheel/drawCore` | §8.6 |
| 31 | 光影兼容：GUI 不参与延迟渲染（延迟队列只用于物品世界渲染） | `docs/shader-pack-compat.md` | §9.2 |

**明确不属于已有实现的部分**：被动法术的驱动框架、`WheelSlots`、`SpellLoadoutScreen`、`SpellWheelHud`、
`WheelCastPacket`/`WheelCastFeedbackPacket`、`MagicHandle#cancelCasting`、三支实机轮盘 shader、
`magic_wheel_*` 的 lang key、轮盘页 II/III 的实际数据——全部为 `[建议新增]` 或 `[规划]`。

---

## 附录 B：本设计稿的模拟产物（Lead 整合时补充）

设计稿已经用 `tools/gui-preview` 的离屏渲染工具**真实渲染落地**（不是 Figma 稿），
以下 PNG 可直接在仓库里打开对照本文的坐标/颜色/状态表：

```powershell
# 生成全部界面帧（不需要启动游戏；640×400 逻辑画布，导出 1280×800）
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_wheel  -Time 3.4"
cmd /c "tools\gui-preview\gui_preview.cmd -Scene spell_loadout -Time 2.0"
```

| 产物 | 内容 | 对应本文 |
|---|---|---|
| `build/gui-preview/spell_wheel.png` | 主视觉：选中 Fireball，Spear 冷却中，Suppression 就绪 | §3.3、§4、§5.2 |
| `build/gui-preview/spell_wheel_idle.png` | 轮盘刚打开 / 未选中（AIM A SIGIL） | §5.2 |
| `build/gui-preview/spell_wheel_charge.png` | Spear of Judgment 蓄力中（1.2 / 2.0s、外圈蓄力进度环） | §5.2、§6.2 |
| `build/gui-preview/spell_wheel_cooldown.png` | 冷却扇区压暗 + 读秒、低蓝扇区锁定（80 MANA） | §5.2、§5.4 |
| `build/gui-preview/spell_wheel_empty.png` | 空轮盘兜底（魔法池没有主动法术） | §5.4 |
| `build/gui-preview/spell_loadout.png` | 装载主图：图鉴 ACTIVE 页、轮盘 3/8、右侧详情 | §3.7、§5.3 |
| `build/gui-preview/spell_loadout_drag.png` | 拖拽 Suppression → 第 6 位（瞄准环/虚线/幽灵卡/落点脉冲） | §6.4 |
| `build/gui-preview/spell_loadout_passive.png` | PASSIVE 页只读态（被动不可绑定、轮盘锁定） | §5.3、§6.4 |
| `build/gui-preview/wheel_shader_lab*.png` | 三支轮盘着色器的状态实验台（8 扇区全状态 / aura / core） | §8.6 |

实机实现时的对照：

- 轮盘视觉与数据模型在 `tools/gui-preview/WheelKit.java`（Spell 数据、8 方位几何、
  扇区/核心/光环绘制、卡片/详情）；
- 三支设计着色器（`wheel_slice` / `wheel_aura` / `wheel_core`）当前放在
  `tools/gui-preview/shaders/`，**上线时才搬进** `assets/rotten_ruins/shaders/core/`，
  json 的 blend/attributes/uniforms 契约见 `tools/gui-preview/shaders/README.md`；
- 预览场景为 `SceneSpellWheel.java` / `SceneSpellLoadout.java`，工具用法与“不模拟什么”
  见 `tools/gui-preview/README.md` 与 `FRAMEWORK.md`。

> 预览与实机的差异（本文 §10.2 已列）：世界背景是程序化夜空山景、预览居中/无输入、
> 被动示例为规划占位、字体只含拉丁字符（画面里的 `|` 分隔符是替代 `·` 的排版选择）、
> 不模拟 `GuiGraphics` 批处理/PoseStack/RenderType 状态簿记与光影包上下文。

### B.1 平滑边缘的实现（SDF）

轮盘/装载界面里所有**圆形、圆环、扇区、圆角面板、胶囊、星形**都由 SDF 着色器绘制，
而不是 `GuiGraphics.fill` 拼矩形或三角光栅化：片元里算到形状边界的距离，
`coverage = clamp(0.5 - sdf / aa, 0, 1)`，其中 `aa = 1 / uScale`（`uScale` = 1 个
GUI 像素对应的物理像素数，实机传 `Window#getGuiScale()`）。这样把边界的过渡
恒定收在 **1 个物理像素**，GUI 缩放 2/3/4 都不会出现台阶。

- 设计稿里的实现：`tools/gui-preview/shaders/sdf_ui.*` + `tools/gui-preview/SdfShapes.java`，
  轮盘/装载场景已经全面接入（`spell_wheel` / `spell_loadout` 的圆环、徽章、胶囊、
  面板圆角、法力条、星形都是它画的）；
- 实机接线：把 `sdf_ui.json/.vsh/.fsh` 放进 `assets/rotten_ruins/shaders/core/`，
  用 `RegisterShadersEvent` 注册（`DefaultVertexFormat.POSITION_TEX`），
  `uScale` 传 GUI scale；`SdfShapes` 的 `false` 返回值就是给"着色器注册失败时
  退回扫描线填充"准备的降级分支；
- 这套思路与同一作者的 **FIS-Cordyceps**（MIT）项目的 `wheel_sdf.fsh` / `GuiShapes`
  一致：那个项目还有一版**扫描线覆盖率 CPU 降级**（每行算精确浮点区间、两端按
  覆盖率给部分 alpha）和基于短边缩放的 `WheelGeometry`（外径 = `clamp(短边×0.32,
  44, 132)`，同时用 `maxFit` 保证轮盘不出屏）——本设计稿的自适应公式（§3.5）
  与它等价，做进游戏时推荐直接复用 FIS 那套 `WheelGeometry` 的写法；
- **贴图与字体保持像素风**：16×16 法术图标按 8 的倍数绘制（2x 原生渲染后每 texel
  落在整数个物理像素上），位图字体只用 1x/2x/3x 整数倍，不做非整数缩放。

---

## 附录 C：实机落地状态（P0 已实现）

> 这一节记录**已经写进模组的代码**（不再只是设计建议），和上面附录 B 的"设计稿产物"
> 分开看。实现日期：2026-10-31；验收方式：`gradlew compileJava` + dev client 启动 +
> 无世界轮盘渲染预览截图（见 §C.4）。

### C.1 操作

| 操作 | 行为 |
|---|---|
| 按住 `R` | 打开施法轮盘；鼠标方向选方位，**松手释放**；右键 / `Esc` 取消 |
| `V` | 打开魔法装载界面（配置 3 页 × 8 方位的映射） |
| `M` | 原有魔法星图（选中池槽位与轮盘互相独立但共享同一份魔法池） |

`R` 同时是原版「交换副手」键：轮盘可用时客户端会吞掉 swap-offhand（否则每次开轮盘
都会顺手换副手物品）；两个键位都可在「控制」里改。

### C.2 已实装的文件

| 层 | 文件 |
|---|---|
| 客户端界面 | `client/gui/SpellWheelScreen.java`（按住轮盘 + 手势状态机）、`client/gui/SpellLoadoutScreen.java`（三页配置/拖拽/保存）、`client/gui/WheelHudOverlay.java`（KEEP 维持条 HUD） |
| 客户端绘制 | `client/gui/wheel/WheelRenderer.java`（轮盘/装载共用绘制）、`WheelData.java`（客户端只读视图）、`WheelClientState.java`（会话相位）、`client/rend/gui/WheelGuiShaders.java`（4 支 GUI shader 立即绘制 + SDF 形状） |
| 网络 | `network/c2s/WheelActionPacket.java`（`rotten_ruins:wheel_action`：CONFIG/BEGIN/CAST/END/CANCEL）、`network/s2c/WheelFeedbackPacket.java`（`rotten_ruins:wheel_feedback`，走已有 `UniversalPacket`，**不新增包序号**） |
| 服务端 | `overworld/registry/capability/PlayerMagicPool.java`（NBT `HallMagicWheelSlots` = 3×8 池槽下标，-1 空位；去重/失效清洗/首次自动填充第 I 页）、`overworld/registry/items/magic/bases/MagicHandle.java`（`handleWheelBegin/Cast/End/Cancel` + `tickWheel`；复用 `castNow` 与 `handleUseRelease`，含 `WHEEL_CHARGE_TOLERANCE_TICKS=2`） |
| 数据流 | `network/MagicSync.java` 的 `MagicPoolSyncPacket` 全量 NBT 会自然带上轮盘映射；服务端 tick 在 `MagicEventHandlers#onLivingTick` 里推进轮盘会话 |
| 着色器 | `assets/rotten_ruins/shaders/core/{wheel_slice,wheel_aura,wheel_core,sdf_ui}.{json,vsh,fsh}`，在 `client/rend/SplendidingShaders#onRegisterShaders` 注册（失败只打日志、退回纯色绘制） |
| 本地化 | `datagen/gen/lang/LangDataEN/CN` + `src/generated/.../lang/{en_us,zh_cn}.json`（键位/结果/提示共 ~20 条） |

### C.3 手势 → 服务端协议（实际实现）

1. 打开轮盘只发一次全量同步请求（`MagicPoolActionPacket.sendSyncRequest()`），客户端先按镜像画。
2. `CHARGE`：选中停留 3 tick（120ms）→ 客户端发 `BEGIN(page,index)`；服务端 `handleWheelBegin`
   重新校验（槽位/主动/法杖/冷却/蓝量）后进入 `CHARGING`；服务端每 tick `tickWheel`
   推进 `handleUseTick`；松手发 `END`，服务端用**自己的** heldTicks + 2 tick 容差
   调 `handleUseRelease` 结算 `chargeRatio`。
3. `KEEP`：停留 5 tick（250ms）→ 发 `BEGIN`；服务端 `castNow` 起手（立刻扣蓝进冷却）
   后进入 `CHANNELING`；回执成功客户端关闭轮盘、切到 `MAINTAINING`，HUD 画维持条；
   松开 R 发 `END`（服务端结束维持）。
4. `IMMEDIATELY`：不 dwell，松手发 `CAST`；服务端校验后 `castNow`（与右键法杖同一条路径，
   `MagicCastEvent`/伤害与耗蓝修饰器照常生效）。
5. 蓄力中鼠标移出方位 / 右键 / `Esc` / 换手换物品：客户端发 `CANCEL`；服务端对 `CHARGING`
   零代价作废、对 `CHANNELING` 结束维持（不退还起手蓝与冷却）。
6. 配置：装载界面 `SAVE` 发 `CONFIG(page, int[8])`；服务端逐项重校验（合法池槽、非空、
   主动法术、无重复）后写入 `PlayerMagicPool` 并强制全量同步。

### C.4 与设计稿的偏差（P0 范围）

- 轮盘用 **Screen + 系统光标**（`isPauseScreen=false`）而不是设计稿 §6.3 的"鼠标增量虚拟光标
  + HUD overlay + mixin" —— 手感与锁定策略更简单，但打开时系统光标可见。
- `R` 固定打开第 I 页（page 参数已贯通，P1 可加滚轮/数字键切页）；装载界面可编辑三页。
- 装载界面的拖拽"落到已占用方位"目前是**直接替换**（P0 简化），不是"占用位铺红拒绝"。
- `SAVE` 只保存当前页；其它页草稿不点 SAVE 会丢（底栏有提示）。
- 被动法术仍是规划内容（PASSIVE 页通常为空）。
- 首次升级/新玩家：若 NBT 里还没有 `HallMagicWheelSlots`，服务端会把魔法池里的主动法术
  按顺序自动填进第 I 页（避免"按 R 没反应"）。

### C.5 验收清单

- [x] `gradlew compileJava` 全树通过（error 0）
- [x] dev client 启动到标题界面，无 shader/json 注册错误（`[Shaders] 加载 wheel_*` 未出现）
- [x] 无世界调试预览屏：4 支 shader + SDF 环/图标/文字/HUD chrome 实际渲染无异常
      （F2 实机截图：`build/client_wheel_preview.png`；装载界面空态：`build/client_loadout_preview.png`。
      调试用的临时 Screen 已删除，不在交付代码里）
- [ ] 进世界验收：按住 R 的开合动画、鼠标选方位、Fireball 松手施法、Spear 蓄力、
      Suppression 维持、右键取消、V 配置保存后同步、多人延迟与反作弊边界
- [ ] 光影包（Oculus/Iris）下的显示回归

### C.6 实机首轮反馈修复（2026-10-31）

首轮进世界截图暴露了几个真实渲染/本地化问题，已修：

1. **扇区整体偏半格（最严重的视觉错位）**
   `WheelRenderer` 之前用"相邻两个方位中心角"当扇区起止角，导致每个扇区整体
   顺时针平移半格：图标在自己的扇区中心，而填充/发光/冷却扫边落在下一个半格，
   看起来就像"选中色块溢出到相邻扇区"。改为 `slotStart(i)` / `slotEnd(i)`
   （中心角 ± 半格）后，扇区、图标、hover/selected 发光、冷却扫边重新对齐。
2. **装载界面左上角巨大橙色矩形**
   `GuiGraphics.fill` 的参数是**端点坐标 (x1,y1,x2,y2)**，不是宽高；新界面里若按
   宽高传参（如拖拽幽灵的强调条 `fill(x+1,y+2,2,h-4,accent)`）会被解释成
   "从 (x+1,y+2) 到 (2,h-4)" 的大矩形（颜色正好是 FIRE 流派色）。已把新界面里
   所有 width/height 式 `fill` 改成端点式。
3. **冷却/锁定胶囊压住键位徽章**
   状态胶囊从 `sy + iconSize*0.72` 下移到 `sy + iconSize*1.12`，与内侧键位徽章分开。
4. **界面未中文化**
   所有轮盘/装载界面文案（标题、按钮、状态、提示、结果回执）改为 lang key，
   走 `WheelI18n.tr(...)`；在 `LangDataEN` / `LangDataCN` 里补了 102 条
   `rotten_ruins.wheel.*` / `rotten_ruins.gui.wheel.*`，用
   `gradlew runData --offline` 重新生成 `src/generated/.../lang/{en_us,zh_cn}.json`。
   **生成目录不再手改。**
5. **同一法术在轮盘上出现两次**
   `sanitizeWheelSlots` / `autoAssignWheelPage0` / `WheelActionPacket` 的配置校验
   原先只按"池槽下标"去重；旧存档或外部注入的重复法术物品可能占两个池槽。
   现在同时按**法术注册名**去重，轮盘上同一法术只保留一个方位。
6. **验证**：`gradlew compileJava` 全树通过；`runData` 成功且生成的两份 lang key 数量一致
   （各 102 条）。仍需进世界重测 UI 观感与手势链路。

### C.7 实机第二轮反馈（2026-10-31 下午）

按进世界截图继续收口：

1. **HUD 魔能只留数字**：状态胶囊从 `"80 MANA"` / `"100 MANA"` 改为 `80` / `100`；
   冷却仍显示 `12s` 这类秒数。
2. **标号内收 + 状态胶囊外移**：方位号 1..8 的半径从 `inner + 8..12` 收小，
   魔能/冷却胶囊改到图标**径向向外**侧（`iconRadius + iconSize*0.62`），两者不再重叠。
3. **装载界面的轮盘不显示魔能/冷却胶囊**：`WheelRenderer.drawSlots` 增加 `loadoutMode`，
   装载预览只画图标与方位号；魔能已由左侧图鉴卡片（`80 法力`）表达。
4. **去掉轮盘下方重复的三行**：装载界面只保留一行 `轮盘 I | 3/8 方位` 摘要，
   "把法术拖到方位上 / 右键解绑 / 按 SAVE 后生效" 删除（底部栏已有同样的提示）。
5. **中心只保留"轮盘"**：装载界面轮盘中心只画 `轮盘` 两个字；
   HUD 未选中方位时也只画 `轮盘`；空轮盘仍显示"没有主动法术"+绑定提示；选中法术时
   HUD 保留法术名/法力/状态。
6. **页签间距与点击**：页签宽度改为固定 26，`I / II / III` 间距一致；
   `drawBottomBar` 之前每轮又用已推进的 `tabX` 调 `pageTabRect`（内部还会再加一次
   前缀宽度），导致显示位置和点击矩形漂移，现统一用 `pageTabRect(p, pageTabStartX())`，
   绘制与命中检测共用同一套矩形。


### C.8 实机第三轮反馈（2026-10-31 傍晚）

1. **HUD 标题金线穿过文字**
   `ARCANE WHEEL / 奥术轮盘` 用 `textCenteredScaled(..., 2.0F)` 绘制，但金线按未缩放
   `textWidth(title)` 排，线尾插进了文字。现在按 `textWidth * 2` 计算实际宽度，
   金线整体外移到文字两侧。
2. **魔能数字压住图标**
   图标半对角线约 `0.71 * iconSize`，而胶囊只外移了 `0.62 * iconSize`。现在胶囊
   放在 `iconRadius + iconSize * 1.02` 的径向更外侧；HUD 上魔能只显示数字（`80` / `100`），
   不写 `MANA`/单位。
3. **装载界面槽位圆溢出**
   装载预览的轮盘比 HUD 小（`WheelRenderer` 用 `loadoutMode` 区分），但图标仍固定 32。
   现在装载模式下图标尺寸按环带宽度缩到 18..25，圆盘、描边、方位号一起收小；
   魔能/冷却胶囊在装载模式下完全不画（左侧图鉴卡片已显示）。
4. **中文长描述穿过右侧边框**
   `wrapLines` 原来只按空格分词，中文没有空格 → 整段被当成一个超长单词。
   现在英文优先在空格/标点断，中文/日文按字符断（`isCjk`），超出 `maxLines` 最后一行省略号。
5. **轮盘改为开关式**
   不再需要一直按住 R：
   - `R`：打开 / 关闭（关闭会 CANCEL 蓄力或等待中的 KEEP）；
   - 左键点击方位：`IMMEDIATELY` 立即施放并关闭；`CHARGE` 按住左键蓄力、松开释放；
     `KEEP` 左键起手，回执成功关界面并进入维持条；
   - 右键 / `Esc`：关闭（等同取消）；
   - 维持条：右键或再按 `R` 结束，不再靠"松开 R"；打开轮盘时右键仍然只关轮盘，
     不会顺手触发世界交互；
   - 关闭轮盘后 250ms 内忽略下一次 R 的开轮请求，避免"按 R 关闭 + KeyMapping 点击队列"
     竞态导致立刻又打开。
   服务端协议（`BEGIN/CAST/END/CANCEL`）不变，只改客户端触发方式与提示文案。
6. **验证**：`compileJava`、`runData`、`processResources` 均成功；语言 key 由 datagen 生成。
