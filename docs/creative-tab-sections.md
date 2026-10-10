# 创造物品栏 · 分区隔断行

一个标签里塞一百多个物品时，原版只有一条扁平的物品流 —— 找东西全靠眼睛。
这套东西把标签内容声明成若干**分区**，每个分区在网格里以一行
**流动色带 + 居中彩色标题**开头：

```
┌─ 创造模式物品栏 ─────────────────────────────────┐
│ [畸骸与王庭生物]                                   │  ← 隔断行：9 个空槽 + hall:gui_tab_divider + 流动彩色标题
│  🥚 🥚 🥚 🥚 🥚 🥚 🥚 🥚 🥚                        │
│  🥚                                                │
│  □ □ □ □ □ □ □ □ □   ← 段尾补空到整行（不然标题行会从半行处折下去）
│ [王庭材料]                                         │
│  💎 💎 💎 💎 💎 💎 💎 💎 💎                        │
│  ...                                              │
└──────────────────────────────────────────────────┘
```

---

## 一、隔断行几乎不要钱

原版的创造网格不是一个"容器"，而是**一条扁平列表 + 一个 5×9 的取景窗**：

| 事实 | 出处 |
| --- | --- |
| `ItemPickerMenu.items` 是 `NonNullList<ItemStack>` 一维表 | `CreativeModeInventoryScreen.ItemPickerMenu` |
| 滚动时 `scrollTo(float)` 把从某个**整行**开始的 45 个拷进静态 `CONTAINER` | 同上 |
| 45 个 `CustomCreativeSlot` 只是这块 CONTAINER 的取景窗 | 同上 |
| 行数 / 滚动条由 `calculateRowCount()`、`canScroll()` 按列表长度自己算 | 同上 |

于是在那条列表里插进 9 个 `ItemStack.EMPTY`，它就是一行空白：

- **不绘制** —— `GuiGraphics.renderItem` / `renderItemDecorations` 对空栈直接 return；
- **不出 tooltip** —— `AbstractContainerScreen.renderTooltip` 要求 `hoveredSlot.hasItem()`；
- **拿不走** —— 空槽本来就没有可拿的东西；
- **行数与滚动条自动正确** —— 两条公式都看列表长度。

所以这套实现**没有注册任何"分隔符物品"**。那套路子会被 Forge 的
`ItemDisplayBuilder` 卡住（重复堆栈、`count != 1` 都是硬报错），而且物品搜索与 JEI 里
会冒出幽灵物品。空槽没有这些问题。

## 二、只在客户端

创造标签的物品表**只在客户端构建**：整份 Forge + Minecraft 里
`CreativeModeTabs.tryRebuildTabContents()` 唯一的调用方就是 `CreativeModeInventoryScreen`
（构造器与 `containerTick`）。因此：

- 不涉及网络同步、存档与服务端；
- `getDisplayItems()` 对外仍然是那份干净的扁平表，JEI / 其它 mod 读到的没有变化；
- 混入只在**选中分区标签**时生效（`selectedTab instanceof SectionedCreativeModeTab`），
  其它标签零影响。

## 三、组件

| 组件 | 位置 | 职责 |
| --- | --- | --- |
| `SectionedCreativeModeTab` | `overworld/registry` | 分区模型：标题 + 各区生成器 + 各区物品个数；跨分区按物品去重 |
| `RegisterTab` | `overworld/registry` | 声明「什么归哪一段」 |
| `CreativeModeInventoryScreenMixin` | `mixin` | 把扁平表重排成「补空 + 隔断行 + 本段物品」；拦住隔断行上的点击 |
| `SectionedCreativeScreen` | `client/gui/creative` | 界面侧接口（混入实现），渲染器靠 `instanceof` 认领界面 |
| `CreativeTabDividerRenderer` | `client/gui/creative` | 在 `ContainerScreenEvent.Render.Foreground` 上画色带与标题 |
| `hall:gui_tab_divider` | `assets/hall/shaders/core` | 那条极光缎带 |

`SectionedCreativeScreen` 之所以是接口而不是一个静态变量：静态变量在切模式 / 同帧两个界面时
会残留，而接口长在界面实例上，界面没了就自然没了。

## 四、数据流

```
SectionedCreativeModeTab.TabBuilder.section(标题, 生成器)     ← 声明
        │
        │  build() 把各区拼成一个生成器交给父类
        ▼
CreativeModeTab.buildContents(params)                         ← 只在客户端跑
        │  逐个区跑生成器；SectionOutput 一边按 Item 去重、
        │  一边把「这一区真正吐出去几个」写回 Section.size
        ▼
CreativeModeTab.getDisplayItems()                             ← 仍然是干净扁平表
        │
        │  界面侧：selectTab / refreshCurrentTabContents / refreshSearchResults 之后
        ▼
CreativeModeInventoryScreenMixin#splendiding$rebuildLayout()
        │  按各区的 size 切开扁平表，插入补空与 9 个空槽的隔断行，
        │  记下每个隔断行的「全局行号 → 标题」
        ▼
ItemPickerMenu.items  →  scrollTo(scrollOffs)  →  网格
        │
        ▼
ContainerScreenEvent.Render.Foreground → 色带 + 流动彩色标题
```

重排是**幂等**的：它只读 `selectedTab.getDisplayItems()`，不读已经改过的 `menu.items`，
所以 `selectTab` 内部再调 `refreshSearchResults` 这种重复触发不会越插越多。

### 三个注入点

| 方法 | 什么时候走到 |
| --- | --- |
| `selectTab` | 切标签；`init()` / `resize()` 也走这里 |
| `refreshCurrentTabContents` | 特性开关 / OP 权限变化后重建标签内容 |
| `refreshSearchResults` | 搜索框内容变化 |

## 五、边界与已知取舍

- **搜索时不分区。** 带搜索栏的标签一旦输入关键字，结果来自 `SearchTree`，
  是一条与分区无关的命中列表 —— 那时隔断行没有意义，直接保持原版扁平结果
  （`searchBox` 非空即放弃重排）。
- **别的 mod 通过 `BuildCreativeModeTabContentsEvent` 插进来的物品**不在任何分区里，
  会被兜在最后一段（无标题）而不是丢掉。若它们被 `insertBefore/After` 插在中间，
  分区边界会整体顺移一格，但**不会丢物品、不会重复**。
- **分区边界用的是"各区吐出去几个"**，不是"匹配物品"。这是在事件注入存在的前提下
  最简单又不丢东西的口径。
- **版本绑定。** 这套东西建立在 1.20.1 的 `ItemPickerMenu` 结构上。
  1.20.5+/1.21 那里物品表改成服务端构建后同步，迁移要重做（思路类似，落点不同）。
- **`displayItems()` 被禁用。** 分区标签用 `section(...)` 声明内容；
  再调 `displayItems()` 会在 `build()` 里被覆盖，所以直接抛 `UnsupportedOperationException`。
- **去重按 `Item`。** 与改造前的 `LinkedHashSet<Item>` 同口径：同一个物品带不同 NBT
  只保留先声明的那一次。

## 六、调参在哪

| 想改什么 | 改哪 |
| --- | --- |
| 色带的颜色 / 权重 / 辉光 | `CreativeTabDividerRenderer` 的 `PALETTE_A/B/C`、`GLOW`（对应 shader 的 `uColorA/B/C` `.a` 权重） |
| 色带的图案、流速、噪声频率 | `assets/hall/shaders/core/gui_tab_divider.fsh` |
| 标题的流动速度与色相梯度 | `CreativeTabDividerRenderer` 的 `TEXT_FLOW_SPEED` / `TEXT_FLOW_GRADIENT` |
| 色带占的矩形 | `CreativeTabDividerRenderer` 的 `GRID_X/GRID_W/GRID_Y/ROW_H`（默认 8 / 162 / 17 / 18，正好盖住 9 个槽位井的外框） |
| 整条的不透明度 | 着色器 `uIntensity`（Java 侧 `INTENSITY`）。色带的 alpha 恒为 `band × edge × uIntensity`，**不受噪声影响** |
| 标题文案 | `LangDataCN` / `LangDataEN` 的 `itemGroup.hall.main.section.*` |

**着色器加载失败不会让功能消失**：`SplendidingShaders.guiTabDividerShader` 为 null 时
退回一条纯色细线 + 同样的标题。分区本身（找东西）不能因为着色器没了就失效。

## 七、两套坐标系（改渲染时最容易翻的地方）

`CreativeTabDividerRenderer` 在同一个方法里用了**两个不同的坐标空间**，这不是笔误：

| 画什么 | 走哪条路 | 坐标 |
| --- | --- | --- |
| 色带 | `BufferUploader.drawWithShader` | **屏幕绝对** GUI 坐标（`leftPos + 9` 起） |
| 标题、降级细线 | `gui.drawString` / `gui.fill` | **相对面板左上角**（`9` 起） |

原因：立即绘制的顶点矩阵是单位阵，`ProjMat` / `ModelViewMat` 由 RenderSystem 给，
`GuiGraphics` 的 PoseStack 完全没参与；而 `gui.*` 会把 PoseStack 烘进顶点 ——
偏偏 `ContainerScreenEvent.Render.Foreground` 是在 `AbstractContainerScreen`
push 了 `(leftPos, topPos)` 平移**之后**才派发的（同屏的 `renderLabels` 用 `8, 6`
画标签标题，就是这个相对口径）。

给文字传绝对坐标的后果是它被平移两次：色带位置正确，标题整块飘到面板外的世界渲染区。
`drawBars` 与 `drawFlowingText` 的 javadoc 里各写了一遍，改一处时记得另一处。

### 顺带量出来的槽位井几何

"铺满一整行"到底铺到哪，不能靠眼看 —— 那 1px 的差别就是"有缝"和"无缝"。把原版
`tab_items.png`（在 `client.jar` 的
`assets/minecraft/textures/gui/container/creative_inventory/`）抽出来逐像素量一遍：

```
槽位井每格 18×18，井内区正好 16×16，落在 (9 + 18j, 18 + 18i) 上
  井左/上边框 → texture (8, 17)      井右/下边框 → texture (25, 34)
```

所以隔断行的矩形必须是 `x ∈ [8, 170)`、`y ∈ [17 + 18i, 35 + 18i)`（宽 162、高 18）。
只盖井内区（`[9, 169) × [18, 36)`）会在左、上各留 1px 缝，底下还多压 1px 到下一行 ——
上一版就是这样，截图上看得很清楚。

## 八、加一个新分区

```java
.section(section("magic"), (params, output) -> {
    output.accept(RegisterItem.SOME_WAND.get());
})
```

- 插在哪，网格里就在哪；顺序即声明顺序。
- 不用管去重：`OtherItems` 那个兜底分区永远排在最后，前面拿走的它不会再吐一遍。
- 段尾补空是自动的，不用自己数格。
- 记得在 `LangDataCN` / `LangDataEN` 里补 `itemGroup.hall.main.section.<key>`，
  然后 `runData`。
