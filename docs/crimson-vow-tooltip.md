# 物品 Tooltip 系统 · 绯红誓约 / 寂寒白日

两把剑共用同一套管线，差别只在**文案键与配色**：文本层每一行都有自己的渐变，
底板（配色 + 着色器效果）与中间那条减伤条都是参数化的。

```
绯红誓约                                   ← 流动粉紫（FlowingNameTooltipHook）
┌────────────────────────────────────┐
│ 格挡减伤                      75%   │    ← 图像层：自绘减伤条 + 粉色热力学流动底板
│ ███████████████████████▏░░░░░░░░░   │       (ClientBlockBarTooltip / TooltipShaders)
└────────────────────────────────────┘
「以血为誓，此刃不折。」                     ← 文本层：斜体，浅紫 → 亮粉
格挡  按住右键举剑迎击 · 只挡正面来敌          ← 文本层：标签亮粉→紫；正文灰紫 → 雾粉紫
誓约  不可损坏 · 火焰免疫 · 合成后归还         ← 文本层：标签亮粉→紫；正文冰蓝紫 → 雾紫
实体范围 +7                                ← 原版属性块（我们一个字都没写，也不上色）
+8 攻击伤害  -2.4 攻击速度
```

```
寂寒白日                                   ← 流动冰蓝 → 亮靛
┌────────────────────────────────────┐
│ 格挡减伤                      75%   │    ← 同一条减伤条，冷色；底板是同一个对流图案的冷色版
│ ███████████████████████▏░░░░░░░░░   │
└────────────────────────────────────┘
「白昼无声，唯余一刃寒水。」                  ← 文本层：冰白 → 浅天蓝
格挡  按住右键举剑迎击 · 只挡正面来敌          ← 文本层：标签冰白→浅蓝；正文灰蓝 → 冰蓝
湍流  剑刃浸在一层流动的水光里                ← 文本层：**这把剑独有的一行**
白日  不可损坏 · 火焰免疫 · 合成后归还         ← 文本层：标签冰白→浅蓝；正文青绿 → 浅蓝
```

各行文字的色相刻意不同（见第九节）：一列同色文字会糊成一片。

那条约里的填充比例、百分比、以及文本行里的"只挡正面"**全部是现取的**，
来源是 `IBlockingWeapon` 的默认实现（`blockDamageMultiplier() == 0.25`、
`blockOnlyFrontal() == true`）。调格挡强度不需要回来改 tooltip。

> **哪些是共用的、哪些是每个物品自己的**（第十节有完整的文件清单）：
> 机制文案（`hall.tooltip.*`）、条子的绘制、底板着色器、行拼接写法（`TooltipLines`）
> 是共用的；**风味文案与全部颜色**归各物品。所以"改了格挡规则只要改一处、
> 换了一把剑的配色不会牵动另一把"。

---

## 一、为什么是"文本 + 图像"两层

| 层 | 挂载点 | 能做什么 | 代价 |
| --- | --- | --- | --- |
| 文本 | `CrimsonVow#appendHoverText` | 纯 `Component`：多行字、逐行配色、`%s` 插值 | 只能给文字 |
| 图像 | `CrimsonVow#getTooltipImage` | 任意 `fill` / `fillGradient` / `drawString` | 要自己算宽高，画错不会报错，只会难看 |

分界线是**公共代码不能碰客户端类型**：`Item#getTooltipImage` 是 Forge 补丁
（`Item.java:276`），两端都可能调到，所以物品侧只交出一个纯数据的
`CrimsonVowTooltip`（record，只有 `float` / `int`），画的事在
`client/tooltip/ClientCrimsonVowTooltip` 里做。

这个模式对模组作者是通用的，Forge 的对接点是 mod 总线上的
`RegisterClientTooltipComponentFactoriesEvent`，按**数据类的 `Class` 精确匹配**
（见 `ClientTooltipComponentManager`）——所以数据类必须是 final 的 record / 类。
**漏注册的症状是打开背包直接崩**，异常里写着 `Unknown TooltipComponent`。

---

## 二、它插在哪一行：index 1，不是末尾

Forge 在 `ForgeHooksClient.gatherTooltipComponents` 里做的是：

```java
itemComponent.ifPresent(c -> elements.add(1, Either.right(c)));   // ForgeHooksClient:1056
```

也就是说图像组件被插在 **index 1** —— 紧跟物品名字、压在所有文本行**之上**。
这带来两个直接后果：

- `appendHoverText` 里**不需要再补空行**了，誓约条本身就是名字与说明之间的分隔；
- 想让誓约条出现在别的位置，只能去调文本行的顺序，图像自己插不了队。

顺带一个容易误判的点：`FlowingNameTooltipHook` 认的是 `ItemTooltipEvent` 给的
`List<Component>`，那个列表里**没有**图像组件（图像是后面在 `GuiGraphics` 里才插进去的），
所以"只替换 index 0"的既有逻辑不受影响。

---

## 三、底板：原版那块深灰底也换掉了

原版 tooltip 的底板不是我们画的 —— 它是
`TooltipRenderUtil.renderTooltipBackground` 画的，颜色来自
`GuiGraphics.renderTooltipInternal` 里那一次 `RenderTooltipEvent.Color`：

```java
RenderTooltipEvent.Color e = ForgeHooksClient.onRenderTooltipColor(stack, ...);   // GuiGraphics:620
TooltipRenderUtil.renderTooltipBackground(this, x, y, w, h, 400,
        e.getBackgroundStart(), e.getBackgroundEnd(),    // 底：纵向渐变
        e.getBorderStart(),     e.getBorderEnd());       // 边：1px 内框，纵向渐变
```

Forge 既然把这四个值暴露出来了，那就**不需要混入、也不需要自己画底板**：

| 颜色 | 原版 | 绯红誓约 |
| --- | --- | --- |
| 底板上端 | `0xF0100010`（近乎纯黑） | `0xF01E0D28`（深紫红） |
| 底板下端 | `0xF0100010`（和上端一样，纯色） | `0xF00E0716`（更深的紫黑） |
| 边框上端 | `0x505000FF`（淡蓝紫，alpha `0x50`） | `0x80FF4FB8`（亮粉，alpha `0x80`） |
| 边框下端 | `0x5028007F` | `0x809B4DFF`（紫） |

承载它的是一枚接口 `ITooltipStyle`（四个色都有默认值 = 上面那四个原版常量），
应用者是 `client/tooltip/TooltipStyleHook`，挂在 **Forge 总线**上 ——
和隔壁 `FlowingNameTooltipHook` 改文字颜色是同一种做法。

两点必须知道：

- **事件对所有 tooltip 都发**。过滤条件是 `stack.getItem() instanceof ITooltipStyle`，
  没实现接口的物品一个像素都不会变；`getItemStack()` 取的是
  `GuiGraphics.tooltipStack`，聊天栏那种不带物品栈的悬浮路径拿到 `ItemStack.EMPTY`，
  会被 `isEmpty()` 直接挡掉（这就是我们要的默认行为）。
- **只管原版物品 tooltip**。JEI 这类自绘 tooltip 的 mod 有自己的绘制路径，
  连这个事件都不发，所以它们的底板不受影响。

### 底板换了以后，面板里跟着调的两个值

- **誓约条的 1px 边框 alpha 压到 `0x78`**（`INNER_FRAME_ALPHA`）。
  底板边框现在已经是粉紫亮的了，里面再来一圈实色就是"框里套框"，
  两条同样亮的线互相抢眼。压下去之后它退成一道内侧压边。
- **轨道改成不透明的暗部色**（原来是一起 `0xC0` 的 alpha）。
  底色变成深紫红渐变之后，半透明轨道会被底色吃掉，"空着的那 25%"就看不出来了。

---

## 四、底板上的着色器：粉色热力学流动

底板的紫红底色之上还叠了一层自绘着色器：**一块被从下方加热的板，热羽在暗紫与亮粉之间翻滚**。
图案全部由片元着色器现场算（三层噪声 + 域扭曲），没有贴图，也不建 RenderType。

### 时序：为什么只能画在"铺底色之前"

```
drawManaged → flush()                          ← 批处理清空，此刻立即绘制是安全的
  RenderTooltipEvent.Color                     ← 我们在这里：画效果层 + 改四个颜色
  TooltipRenderUtil.renderTooltipBackground    ← 底色 alpha 0（让位）+ 1px 内框（压在效果之上）
（drawManaged 结束）→ flush()
renderText → renderImage（誓约条）               ← 都压在最上面
```

Forge 47.4.20 的 `RenderTooltipEvent` 只有 `Pre` / `Color` / `GatherComponents`，
**没有** PostBackground / PostText（那是更高版本才有的）。所以效果只能画在铺底色之前 ——
顺序上没得挑，但结果正好是想要的层次：**效果层 → 原版 1px 内框 → 文字 / 誓约条**。

### 效果层的矩形

| | |
| --- | --- |
| 内容区 | `(x, y, w, h)`，即 `event.getX()/getY()` |
| 效果层四边形 | `(x-4, y-4, w+8, h+8)` |

那个 4 是这么来的：`TooltipRenderUtil` 的底色从内容区外扩 3，外面再画一圈 1px 的描边线
（`renderHorizontalLine(gui, i, j - 1, ...)` 与 `renderVerticalLineGradient(gui, i - 1, ...)`）
→ 合起来正好外扩 4。**差 1px 的症状是边缘露出一条缝** —— 底色已经被设成 alpha 0，
没盖到的地方就是透明的。

宽高必须自己算，算法与 `GuiGraphics.renderTooltipInternal` 逐字一致：
`w = max(各组件 getWidth)`、`h = Σ getHeight + (组件数 == 1 ? -2 : 0)`。

### 三条必须守住的规矩

1. **先 `gui.flush()` 再立即绘制。** 这段回调运行在 `drawManaged(...)` **之内**（`managed == true`），
   普通 fill 会被攒着不提交；立即绘制的四边形必须先把批次刷出去，否则先前 GUI 元素的顶点
   会在我们之后才 flush、盖在效果层之上。（坍缩血条那次的症状是"框架在、里面全空"。）
2. **uniform 设完就画，中间不插别的绘制。** `Uniform.set()` 只是记值，真正上传发生在
   `ShaderInstance.apply()`，也就是这次 draw（见 `docs/mask-layers.md` 第 8 节）。
3. **画完把 RenderSystem 状态还回去**（`depthMask(true)` / `enableDepthTest` / `disableBlend`）：
   后面还有原版底色、边框、文字、誓约条要画。

### 三个文件

| 文件 | 内容 |
| --- | --- |
| `assets/hall/shaders/core/rendertype_tooltip_thermal.json` | `blend: srcalpha / 1-srcalpha`、`attributes: [Position, UV0]`、10 个 uniform 的默认值 |
| `...vsh` | 与 `rendertype_gui_collapsar_bar.vsh` 同款：`ProjMat * ModelViewMat * Position`，UV 透传 |
| `...fsh` | 对流 + 域扭曲 + 下方热源 → 暗底/亮粉/雾粉紫三段色阶，四边压暗 |

| uniform | 作用 |
| --- | --- |
| `uTime` | 秒（游戏时间 + 帧插值 —— 暂停时热流也停住，与名字的流动同源） |
| `uSpeed` / `uScale` | 对流速度 / 热羽特征尺寸（GUI 像素） |
| `uIntensity` | 强度，来自 `TooltipShaderSpec` |
| `uPanelSize` | 面板像素尺寸 —— 用像素而不是 uv，面板大小变了热羽粗细不变 |
| `uBaseColor` / `uFlowColor` / `uHotColor` | 冷底（alpha 参与）/ 热流 / 热核 |

### 三个坑（这次核出来的）

- **json 里的 `blend` 才是生效的那份。** `ShaderInstance.apply()` 会按 json 调
  `BlendMode.apply()`，而它发生在手动 RenderSystem 状态之后 → **json 赢**。漏写 `"blend"`
  会退回"关闭混合"，症状是把效果原样写进帧缓冲（一块实心方块）。这里写
  `srcalpha / 1-srcalpha`，与 `defaultBlendFunc()` 对齐。
- **json 里的 `depthtest` / `depthwrite` 在 1.20.1 没有任何代码读。**
  `ShaderInstance` 只解析 `vertex` / `fragment` / `samplers` / `attributes` / `uniforms` / `blend`；
  深度只能靠手写 `RenderSystem` 或 RenderType 的 state。项目里好几个 json 留着这两个键（历史写法），
  别以为写了就生效。
- **给 int 型 uniform 调 `set(float)` 会 NPE 把客户端带崩**：`Uniform` 按类型分配缓冲，
  int 只分配 `intValues`、`floatValues` 是 null，而 `set(float...)` 无条件访问后者 ——
  判空完全挡不住（难度界面踩过一次）。所以这里 json 全是 `"type": "float"`，
  写入助手另外包了一层 catch：静默跳过 + 只报一次日志，而不是崩。

### 着色器没加载成功会怎样

`TooltipShaders.isReady(key)` 返回 false → `TooltipStyleHook` **不把底色让位**，退回
`TOOLTIP_BG_TOP/BOTTOM` 的纯色底板。也就是说"效果没了，tooltip 还是一个正常的 tooltip"。
失败原因由 `SplendidingShaders` 写成
`[Shaders] 加载 rendertype_tooltip_thermal 失败，tooltip 底板将退回纯色` 进
`run/logs/latest.log` —— 这类失败一律走 LOGGER，因为 `printStackTrace()` 只写 stderr、
不会进日志（项目为这条踩过坑）。

---

## 五、坐标系：别自己平移

`ClientTooltipComponent#renderImage(font, x, y, gui)` 拿到的 `x, y` 是
**屏幕绝对坐标**下 tooltip 内容区的左上角（背景画在 `x-3, y-3`）。
1.20.1 的 `GuiGraphics.renderTooltipInternal` 只对 pose 做了 `z += 400`，
**没有平移 x/y**：

```java
this.pose.translate(0.0F, 0.0F, 400.0F);   // GuiGraphics:623
...
clienttooltipcomponent2.renderImage(font, l, k1, this);   // GuiGraphics:636
```

所以面板直接按传入的 `x, y` 画即可。**不要叠加鼠标位置或 tooltip 原点** ——
叠一次的症状是面板偏出去一整块，而文字是正常的。

`fillGradient(x1,y1,x2,y2,from,to)` 的渐变方向是**纵向**
（`GuiGraphics:249` 把第一个颜色给了 `y1` 那两个顶点）。想要横向渐变只能逐列画，
那是 100+ 次绘制调用，所以誓约条用的是纵向的粉→紫。

---

## 六、高度账本（`ClientCrimsonVowTooltip`）

`getHeight()` 没有 `Font` 参数，只能给固定值，所以整块面板的竖向节奏由常量算出来：

| 位置 | 值（相对 `y`） | 说明 |
| --- | --- | --- |
| `HEIGHT` | 27 | `MARGIN*2 + 1 + INNER_PAD + LABEL_H + LABEL_GAP + BAR_H + INNER_PAD + 1` |
| 上边框 | `y+2` | 亮粉 |
| 标题行 | `y+5` | 左"格挡减伤"（高光色）、右"75%"（亮粉），无阴影 |
| 誓约条 | `y+17 … y+22` | 高 5px；轨道 = 暗部色 + alpha `0xC0` |
| 下边框 | `y+24` | 紫 |

水平方向：面板内沿 `x+1 … x+w-1`，内容（标题、数值、条）统一缩进 4px，
所以文字左缘、条左缘、数值右缘与条右缘是对齐的。

**改高度必须同时改 `getHeight()`**：这个类没有"画完再量一下"的机制，
对不上的症状是面板压到相邻文字上（而不是抛异常）。

另外，文字行在原版里是按 **10px 紧贴**排布的（只有 index 0 后面多给 2px），
所以 `MARGIN = 2` 那点留白是必要的，去掉会让边框贴住上一行字。

---

## 七、绘制调用为什么这么抠

tooltip 是在 `GuiGraphics.drawManaged` 块**之外**渲染的（原版只把背景包进了批处理），
而 `fill` / `drawString` 结束时都会 `flushIfUnmanaged()` ——
**不是** `drawManaged` 里那种攒着一起提交。所以每次 `fill` 都是一次独立提交，
面板刻意压到 **7 次 fill + 2 次 drawString**。

（`drawManaged` 在 1.20.1 已标 `@Deprecated`，本项目不用它。）

---

## 八、文案：全部走 datagen

| 键 | 归属 | 说明 |
| --- | --- | --- |
| `hall.tooltip.label.block` / `hall.tooltip.block` | **共用** | 「格挡」标签 + 格挡行正文（两把剑的格挡是同一件事） |
| `hall.tooltip.trait` | **共用** | 特性正文：不可损坏 · 火焰免疫 · 合成后归还 |
| `hall.tooltip.bar.label` / `hall.tooltip.bar.value` | **共用** | 减伤条的标题与数值（`%s%%`） |
| `item.hall.<id>.tooltip.label.trait` | 各物品 | 特性行的**标签**：「誓约」/「白日」—— 这是身份词，不该共用 |
| `item.hall.<id>.tooltip.lore` | 各物品 | 誓词（整行一对渐变） |
| `item.hall.silent_daylight.tooltip.label.tide` / `…tide` | 寂寒白日 | 「湍流」标签 + 正文（它独有的一行） |
| `item.hall.<id>.tooltip.debug` | 各物品 | `F3+H` 调试行（参数个数不同：绯红誓约多一个"实体范围"） |

**为什么"标签 + 正文"要拆成两个键**：渐变是**逐字上色**的，助手内部会走
`getString()` 把整行拍平成字符串再逐字赋色。所以如果一行写成 `"%s  正文"` 的模板、
再对整行套渐变，标签自己的颜色会被一起拍平 —— 两者就不可能不同色。
拆成两个键之后，Java 侧把它们拼成**两个兄弟组件**（见 `client/rend/text/TooltipLines`），
各自带自己的一对渐变颜色。

流程是**改 `LangDataCN` / `LangDataEN` 再跑 `runData`**：

```
gradlew runData      # 输出到 src/generated/resources/（build.gradle:83）
```

`src/generated/resources` 是主资源目录（`build.gradle:103`），
所以手改 `src/generated/resources/assets/hall/lang/*.json` 会被下一次 datagen 冲掉。

`%%` 是原版 `TranslatableContents` 的转义（`FORMAT_PATTERN` 那段，
`"%%".equals(...)` → 字面 `%`），写 `"%s%%"` 得到 `75%`。

---

## 九、每一行都有自己的渐变

tooltip 里的**每一行文字都带渐变**，而且是**三种不同颜色** —— 一列同色文字会糊成一片：

| 行 | 渐变 | 意图 |
| --- | --- | --- |
| 名字 | 亮粉 `#FF4FB8` → 紫 `#9B4DFF` | 由 `FlowingNameTooltipHook` 替换 index 0（不在物体类里） |
| 誓词（斜体） | 浅紫 `#C9A7FF` → 亮粉 `#FF4FB8` | 最亮最柔：它是修辞 |
| 「格挡」标签 | 亮粉 → 紫 | 标签统一用强调色板 |
| 格挡正文 | 灰紫 `#9A86C8` → 雾粉紫 `#E8A6FF` | 偏暖：它是主动技；比标签暗一档才像正文 |
| 「誓约」标签 | 亮粉 → 紫 | 同上 |
| 誓约正文 | 冰蓝紫 `#8FD6FF` → 雾紫 `#B388FF` | 偏冷：静态属性，也正好和底板的粉色热流对位 |
| `F3+H` 调试行 | **不上渐变** | 调试信息越朴素越好读 |

> 寂寒白日用的是同一套结构、另一组冷色（名字 冰蓝→亮靛；誓词 冰白→浅天蓝；
> 格挡正文 灰蓝→冰蓝；湍流正文 青绿→浅蓝；白日正文 浅蓝→亮靛）。
> 完整对照表见第十五节 —— **配色归物品，结构归管线**。

### 为什么判断收在 `FlowingNameColors.line(...)`

`FlowingNameColors.flowing` 内部会取 `Minecraft.getInstance()` —— 纯客户端类；
而 `CrimsonVow` 在公共代码里（服务端也会加载）。所以"确定在客户端才流动"
这个判断被收进了公共助手：

```java
// FlowingNameColors.line(text, from, to, level)
level != null && level.isClientSide ? flowing(...) : gradient(...)
```

原来这段判断是写在物品类里的私有方法。**上色行数一多，这种安全判断散在各物品类里
迟早有人漏写那一半**，所以提成了 `FlowingNameColors.line(...)`：
任何物品的 `appendHoverText` 都能直接用它给自己的行上同款渐变。
服务端 / `level == null` 的路径退回静态渐变 —— 反正那些路径也没人看得见；
这与 `getName()` 只敢用 `gradient()` 是同一条约束（那里连 level 都没有）。

---

## 十、涉及的文件

| 文件 | 职责 | 归属 |
| --- | --- | --- |
| `overworld/registry/items/CrimsonVow.java` | `appendHoverText` + `getTooltipImage` + `ITooltipStyle` 四色 + 色板常量 | 绯红誓约 |
| `overworld/registry/items/SilentDaylight.java` | 同上，冷色版 | 寂寒白日 |
| `overworld/registry/items/BlockBarTooltip.java` | 减伤条的数据载体（含**文案键**，双端安全，只有基本类型） | 共用 |
| `client/tooltip/ClientBlockBarTooltip.java` | 减伤条的绘制：布局、边框、填充比例、刻度线 | 共用 |
| `client/rend/text/TooltipLines.java` | `feature(标签, 正文, …)`：拼成两个兄弟组件，各带一对渐变 | 共用 |
| `client/rend/text/FlowingNameColors.java` | 逐字渐变本体；`line(text, from, to, level)` —— "客户端才流动"的判断收在这里 | 共用 |
| `api/ITooltipStyle.java` | 底板配色接口（默认值 = 原版四个常量，所以"不覆写 = 不改"） | 共用 |
| `client/tooltip/TooltipStyleHook.java` | `RenderTooltipEvent.Color` 订阅：写四色 + 画效果层 + 算效果层矩形 | 共用 |
| `api/TooltipShaderSpec.java` | 效果的纯数据参数（key + 强度 + 三色），公共代码可安全构造 | 共用 |
| `client/tooltip/TooltipShaders.java` | 效果层的绘制：uniform、立即绘制、状态还原、就绪判断 | 共用 |
| `client/rend/SplendidingShaders.java` | `rendertype_tooltip_thermal` 的注册（失败走 LOGGER） | 共用 |
| `assets/hall/shaders/core/rendertype_tooltip_thermal.{json,vsh,fsh}` | 对流图案的三个文件（粉/冷只是 uniform 不同） | 共用 |
| `client/ClientPacketHandlers.java` | Forge 总线注册（流动名字 + 底板配色两条） | 共用 |
| `event/ClientModEventHandler.java` | `RegisterClientTooltipComponentFactoriesEvent` 订阅（mod 总线） | 共用 |
| `utils/TranslateUtils.java` | `hall.tooltip.*` 共用键 + 各物品的风味键 | 两侧 |
| `datagen/gen/lang/LangDataCN.java` / `LangDataEN.java` | 占位文案（**待定稿**） | 两侧 |

> 这张表就是"加第三把剑要动哪些文件"的答案：只在 `overworld/registry/items/` 下加一个
> 物品类（实现 `IFlowingName` + `ITooltipStyle` + `IBlockingWeapon`），再往语言文件里
> 加几条风味文案。其余全部复用。

---

## 十一、踩过的坑

- **图像组件在 index 1。** 以为它在末尾就会多补一个空行，结果是"名字下面空一行才开始画条"。
- **`renderImage` 的坐标已经是绝对的。** 再叠一次鼠标位置 → 面板整体偏移。
- **`getHeight()` 与绘制走位必须一致。** 不一致不报错，只是压字。
- **工厂按 `getClass()` 精确匹配。** 数据类被继承 / 注册的是父类，就是运行期崩溃。
- **不要在公共代码里碰 `Minecraft` / `ClientTooltipComponent`。** 这就是数据类只放
  `float` / `int`、颜色随数据一起传的原因（客户端因此不必认识 `CrimsonVow`，
  也不会出现"改了描边色、tooltip 还是老配色"）。
- **属性块不要重复写。** `forge.entity_reach` 在 Forge 自带语言文件里叫「实体范围」，
  原版属性块已经用蓝字显示 `+7 实体范围`，tooltip 里再写一遍就是重复。
- **`RenderTooltipEvent.Color` 是发给所有 tooltip 的。** 不做 `instanceof ITooltipStyle`
  过滤就会把整个模组（乃至原版）的 tooltip 底板一起染色；另外那条路径上的
  `getItemStack()` 可能是 `ItemStack.EMPTY`，所以 `isEmpty()` 要先挡一道。
- **别先让位再画效果。** 顺序必须是"先确认真画上了，再把原版底色的 alpha 设成 0"；
  反过来的话，着色器没加载 / 矩形算不出来时 tooltip 会变成一块透明玻璃（见第四节）。
- **`drawManaged` 之内的立即绘制要先 flush。** tooltip 背景整段跑在
  `drawManaged(...)` 里（`managed == true`），普通 fill 会被攒着 —— 不先 `gui.flush()`
  的话，先前 GUI 元素的顶点会在效果层之后提交，把它整块盖掉。
- **json 的 `blend` 生效、`depthtest`/`depthwrite` 不生效**（1.20.1 的 `ShaderInstance`
  只解析六个键）；**int 型 uniform 调 `set(float)` 直接 NPE**。两条详见第四节。

---

## 十二、调参索引

### `ClientBlockBarTooltip.java`（两把剑共用）

| 常量 | 默认 | 作用 |
| --- | --- | --- |
| `MIN_WIDTH` | 132 | 面板最小宽度（中文字体量出来偏窄时的兜底） |
| `MARGIN` | 2 | 面板与上下文字行的留白 |
| `INNER_PAD` | 2 | 边框内侧留白 |
| `TEXT_INSET` | 4 | 标题 / 数值 / 条相对面板内沿的缩进 |
| `LABEL_H` / `LABEL_GAP` | 10 / 2 | 标题行占位与条前间隙 |
| `BAR_H` | 5 | 条子厚度 |
| `LABEL_VALUE_GAP` | 24 | 标题与数值之间的最小空隙 |
| `INNER_FRAME_ALPHA` | 0x78 | 面板自己那圈 1px 边框的透明度（底板边框变亮后压下去的） |

### `CrimsonVow.java`

| 常量 | 默认 | 作用 |
| --- | --- | --- |
| `ACCENT_FROM` / `ACCENT_TO` | `0xFFFF4FB8` / `0xFF9B4DFF` | 名字渐变 + 标签 + 面板上边框 + 数值色 + 底板边框（派生 `0x80` 透明版） |
| `ACCENT_HIGHLIGHT` | `0xFFE8A6FF` | 描边亮部 + 条子刻度线 |
| `ACCENT_SHADOW` | `0xFF2A1B3D` | 描边暗部 + 条子轨道 |
| `LORE_FROM` / `LORE_TO` | `0xFFC9A7FF` / `0xFFFF4FB8` | 誓词行的渐变（浅紫 → 亮粉） |
| `BLOCK_TEXT_FROM` / `BLOCK_TEXT_TO` | `0xFF9A86C8` / `0xFFE8A6FF` | 格挡正文的渐变（灰紫 → 雾粉紫，偏暖） |
| `VOW_TEXT_FROM` / `VOW_TEXT_TO` | `0xFF8FD6FF` / `0xFFB388FF` | 特性正文的渐变（冰蓝紫 → 雾紫，偏冷） |
| `TOOLTIP_BG_TOP` / `TOOLTIP_BG_BOTTOM` | `0xF01E0D28` / `0xF00E0716` | 底板纵向渐变（原版是一整块 `0xF0100010`） |
| `TOOLTIP_BORDER_TOP` / `TOOLTIP_BORDER_BOTTOM` | 由 `ACCENT_*` 派生，alpha `0x80` | 底板 1px 内框 |

> 换配色只改 `ACCENT_*` 那两个：名字（`flowingNameColorFrom/To`）、描边
> （`outlineColor` / `outlineSecondaryColor`）、tooltip 底板与效果层全部跟着走
> （颜色随 `BlockBarTooltip` / `TooltipShaderSpec` 传到客户端）。
> 三行文字各自的 `*_FROM/_TO` 是**独立**的：想让某一行回到朴素灰字，
> 把那对颜色改成同一个灰值即可（渐变退化成单色）。

### `TooltipShaders.java`（底板效果）

| 常量 | 默认 | 作用 |
| --- | --- | --- |
| `THERMAL_SCALE` | 96.0 | 热羽特征尺寸（GUI 像素）。调大 = 大团热浪，调小 = 细密丝状 |
| `THERMAL_SPEED` | 0.16 | 对流速度（噪声空间/秒），约 15 GUI 像素/秒的上升 |

### `TooltipShaderSpec`（由物品声明）

| 参数 | 绯红誓约 / 寂寒白日 | 作用 |
| --- | --- | --- |
| `key` | 都是 `"thermal"` | 图案。未知 key 会被忽略并退回纯色底板 |
| `intensity` | 都是 0.9 | 强度。**再高文字对比度就开始掉**；0.6~0.7 更含蓄，0 = 关掉 |
| `baseColor` | 各自的 `TOOLTIP_BG_TOP`（深紫红 / 深海蓝） | 冷底 —— 取纯色底板的上端色，退回纯色时是同一色系 |
| `flowColor` | `ACCENT_FROM`（亮粉）/ `ACCENT_LIGHT`（冰蓝） | 热流主色（只取 RGB） |
| `hotColor` | `ACCENT_HIGHLIGHT`（雾粉紫）/ `ACCENT_ICE`（冰白） | 热核高光（只取 RGB） |

> 同一份图案靠三个颜色就换了性格：绯红誓约是粉色热浪，寂寒白日是寒流 / 水光。

---

## 十三、验证到什么程度了

**已通过**：

- `gradlew compileJava`、`gradlew runData`（语言文件已重新生成，含两把剑的 13 个键）；
- `clean compileJava` 全量重编（排除增量状态干扰）；
- **`gradlew runClient` 实测**：客户端正常进到主界面，`rendertype_tooltip_thermal`
  注册与 GLSL 编译均无报错（日志里没有 `[Shaders] 加载 rendertype_tooltip_thermal 失败`）；
- 数值来源核对：百分比由 `blockDamageMultiplier()` 算出，不是字面量；
- 着色器 uniform 名与 json 逐条对照过（少一个就是静默不生效）；
  顶点格式四处一致（`begin` 的 `POSITION_TEX` / vsh 的 `in` / json 的 `attributes` / 顶点写入）。

**还没验证（只能靠眼睛）**：

- **对流效果的实际观感** —— 热羽的粗细与速度、颜色浓度、文字对比度是否够；
  这条最需要实机看一眼：配色/强度这类参数只靠读代码判断不了；
- **寂寒白日那套冷色**是否真读成"寒流"而不是"变蓝的热浪"（图案是对流场，
  换色能改性格，改不了物理）；
- 底板配色与条子的搭配 —— 深紫红 + 粉紫边 / 深海蓝 + 冰蓝边是否顺眼；
- 誓约条的实际观感 —— 宽度、条的厚度、刻度线的位置；
- 标签的流动是否**每帧**刷新。tooltip 的组件列表在部分路径上会被缓存，
  若不流动（标签恒定一色）属于预期内的退化，不算 bug；
  想要更长的流动周期就调 `FlowingNameColors.SPAN` / `FLOW`。

| 现象 | 该改哪里 |
| --- | --- |
| 打开背包直接崩，日志 `Unknown TooltipComponent` | `ClientModEventHandler` 里的工厂注册漏了 / 注册的类不是数据类的 `getClass()` |
| 面板整体偏移一块 | `renderImage` 里自己又叠了一次坐标（见第四节） |
| 面板压住上一行字 / 下面留一大片 | `HEIGHT` 与绘制走位不一致（见第五节） |
| 条太长/太短 | `MIN_WIDTH`，或条两端 `TEXT_INSET` |
| 刻度线看不清 | `ACCENT_HIGHLIGHT` |
| 数字和条长对不上 | 填充比例用的是 `1 - blockDamageMultiplier()`，检查 `blockDamageMultiplier()` 是否被覆写 |
| 底板还是原版深灰 | 物品没实现 `ITooltipStyle`，或 `TooltipStyleHook` 没在 `ClientPacketHandlers.init()` 里注册（两条都对才生效） |
| 底板太亮 / 文字看不清 | 抬 `TOOLTIP_BG_*` 的 alpha 到 `0xFF`，或把渐变两端都压暗 |
| 底板边框太抢眼 | `TOOLTIP_BORDER_*` 的 alpha 从 `0x80` 往 `0x50`（原版值）退 |
| 底板换了之后条"没有空槽" | 轨道必须是**不透明**的 `ACCENT_SHADOW`（见第三节末） |
| 两层边框看着花 | `INNER_FRAME_ALPHA` 调低或删掉那四条 `fill` |
| **启动即崩，日志 `ClassMetadataNotFoundException: org.bytechen.hall.xxx`** | **与本功能无关的构建状态问题**：`build/classes/java/main` 里有整包缺失，而 `compileJava` 仍报 UP-TO-DATE（Gradle 增量编译状态坏了，多半发生在一次编译失败之后）。`gradlew clean` 再跑即可 —— 本轮实际踩过：15 个包 / 117 个 class 缺失，重编后 442 个类齐全 |
| 效果层没出来，底板还是纯色 | 看日志有没有 `[Shaders] 加载 rendertype_tooltip_thermal 失败`；没有的话检查 `tooltipShader()` 返回的是不是 `null` |
| 效果层盖住了文字 / 底板 | `gui.flush()` 的时机（见第四节第 1 条）；或矩形算错，盖到了别的区域 |
| 面板四周露出一条没上色的缝 | 效果层矩形的外扩量不是 4（`TooltipStyleHook.PADDING`） |
| 效果是一块实心方块 | json 漏了 `"blend"`（缺省 = 关闭混合 = REPLACE） |
| 热流不动 | `uTime` 没传进去（游戏暂停时本来就不动）；或 uniform 名与 json 不一致（静默失效） |
| 热流太快/太慢、太粗/太细 | `THERMAL_SPEED` / `THERMAL_SCALE` |
| 太花、文字读不清 | `TooltipShaderSpec` 的 `intensity` 往 0.6~0.7 降 |
| 文案要改 | `LangDataCN` / `LangDataEN` → `gradlew runData` |

---

## 十四、以后可以往上加的东西

- **第三个消费者已经证明的事**：加一把剑只需要在 `overworld/registry/items/` 下写一个
  类（`IFlowingName` + `ITooltipStyle` + `IBlockingWeapon`）+ 几条风味文案，
  管线一行都不用改（见第十节的文件归属表）；
- **第二个底板效果**：在 `TooltipShaders.draw` 里开个分支 + 一份新的
  `rendertype_tooltip_*.{json,vsh,fsh}`，物品侧只改 `tooltipShader()` 的 key
  （`TooltipShaderSpec` 已经是"key + 参数"的形状，不用动 API）。
  最想要的一条是<b>水面焦散</b>：寂寒白日现在用的是对流图案换冷色，
  真要做"水面湍流的干涉纹"，就是新开一个 `tide` key；
- **格挡期间动态提示**：`IBlockingWeapon` 已经有 `blockFeedbackMessage()`（默认返回
  `null`），可以在挡下时往动作栏推一条 —— 那是"打起来才有反馈"的另一半；
- **条子做成有状态的**：数据类里加一个 `int stacks`，条上按段点亮
  （和背板的 `VerdictStack` 那类机制同一套思路）；
- **让效果响应状态**：`TooltipShaderSpec` 加一个 `float heat`，格挡时把对流烧旺
  （uniform 已经在那儿了，只是现在写死 0.9）。

---

## 十五、寂寒白日：第二个消费者

它和绯红誓约是**同一套构件**（同为 `ICustomOutline` + `IBlockingWeapon`、
同样不可损坏 / 防火 / 合成归还、格挡参数都用默认的 0.25 与"仅正面"），
所以这次没有新增任何管线，只做了三件事：**给它一组冷色、加一行独有文案、
把原来的私有组件抽成通用件**。

### 抽出来的三件

| 原来 | 现在 | 为什么必须抽 |
| --- | --- | --- |
| `CrimsonVow` 的私有 `featureLine` | `client/rend/text/TooltipLines.feature(...)` | "标签 + 正文拼两个兄弟组件"是**写法**，不是某把剑的事；写错一次（合并成模板）就分不出两种颜色 |
| `CrimsonVowTooltip` | `BlockBarTooltip` | 图案与布局跟物品无关；**文案键**也随数据传，客户端渲染层因此不认识任何具体物品 |
| `ClientCrimsonVowTooltip` | `ClientBlockBarTooltip` | 同上 |

机制文案同时提成了共用键 `hall.tooltip.*`（格挡行、特性行、条子标题），
只把"誓约""白日""湍流"这类**身份词**留给各物品 —— 改了格挡规则只要改一处。

### 它的配色

| 用途 | 值 |
| --- | --- |
| 描边暗部 / 条子轨道 | `ACCENT_DEEP` 深蓝 `#0B2E7A`（原有） |
| 描边亮部 / 名字亮端 / 条子亮端 | `ACCENT_LIGHT` 浅蓝 `#8FE6FF`（原有） |
| 名字暗端 / 底板下边框 | `ACCENT_MID` 亮靛 `#4F8CFF`（新增） |
| 标签 / 刻度线 | `ACCENT_ICE` 冰白 `#E4FAFF`（新增） |
| 底板 | `#081830` → `#030A18` |
| 誓词 | 冰白 `#D9F4FF` → 浅天蓝 `#6FC8FF` |
| 格挡正文 | 灰蓝 `#6E9FD8` → 冰蓝 `#A8DCFF` |
| 湍流正文 | 青绿 `#7FE3D0` → 浅蓝 `#8FE6FF` |
| 白日正文 | 浅蓝 `#B6E8FF` → 亮靛 `#7FA8FF` |

**名字为什么不能直接用 `ACCENT_DEEP` 当暗端**：那接近黑蓝，压在深色底板上等于
后半截字消失。所以文字另取了一个更亮的蓝（`ACCENT_MID`），描边仍然用它的深蓝 ——
"一套色板"不等于"每个用途都用同一个色值"。

### 它独有的那一行

`湍流  剑刃浸在一层流动的水光里` —— 唯一一行只属于这把剑的说明（标签 + 正文一对
青绿→浅蓝的颜色）。这是"每个物品仍然可以有自己的身份"的示范：管线共用，
但物品不是只能填模板。

---
