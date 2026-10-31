# GUI 预览框架 API（场景开发必读）

`tools/gui-preview/` 是一套**不启动 Minecraft** 的 GUI 离屏渲染工具：
隐藏的 OpenGL 3.2 core 上下文 + 一张离屏 FBO → 用项目真实的 core shader、贴图、
Minecraft 位图字体画一帧 → 原生分辨率光栅化 → 导出 PNG。

框架部分（`MiniJson / PreviewGl / PreviewTexture / PreviewShader / PreviewFont /
PreviewCanvas / GuiPreview`）已经验证通过（`-Scene smoke`）。**场景只需要写一个
`SceneXxx.java` 文件**，不要改框架文件、脚本和本文档。

## 运行

在仓库根目录：

```powershell
cmd /c "tools\gui-preview\gui_preview.cmd -Scene tooltip -Time 4.5"
cmd /c "tools\gui-preview\gui_preview.cmd -Scene collapsar_bar -Time 1.5"
```

- `-Scene`：场景名（`tooltip` → 类 `SceneTooltip`，`collapsar_bar` → `SceneCollapsarBar`）
- `-Time`：秒，场景内所有动画的时间基准
- `-Width/-Height`：逻辑画布尺寸，默认 `640x400`（= 1280×800 窗口 GUI scale 2）
- `-Scale`：**原生渲染倍率**，默认 `2`（逻辑 640×400 → 物理 1280×800；斜线/圆弧在物理分辨率上光栅化，不是低分辨率放大）
- 产物在 `build\gui-preview\<scene>.png`（场景自己 `c.save(...)` 的图也在同一目录）

工具会自动从 Gradle 缓存的 `client.jar` 抽出 MC 位图字体到
`build\gui-preview\mc-assets\`（不提交进仓库）；找不到时文字退化为占位方块。

## 场景契约

```java
public final class SceneFoo {
    public static void render(PreviewCanvas c, float time) {
        c.backdrop();
        c.text("Hello", 10, 10, 0xFFFFFFFF, true);
        c.save("foo_frame");
    }
}
```

- 文件名 = `Scene` + 驼峰场景名，默认包（没有 `package` 语句）
- 由脚本单独编译（框架文件 + 你的场景文件 → `build/gui-preview/classes/<scene>`），
  你的场景写坏不会影响其他场景
- 主入口在 `render` 返回后会自动再保存一张 `<scene>.png`；显式 `c.save(...)` 可以存多张
- **画面内文字用英文**：位图字体只覆盖拉丁/符号，中日韩会画成占位方块；
  英文文案在 `src/generated/resources/assets/rotten_ruins/lang/en_us.json`

## 坐标与颜色

- 原点左上、y 向下，单位 = 游戏 GUI 像素（`GuiGraphics` 那一层）
- 颜色一律是 **ARGB int**（`0xFFRRGGBB`），和项目里的 `0xFF...` 常量直接对应
- 画布逻辑尺寸 `c.width` / `c.height`（字段），默认 640×400

## PreviewCanvas API

```java
// 背景
c.clear(int argb);
c.backdrop();                                  // 深色星空世界背景 + 暗角（判断对比度用）

// 矩形
c.fill(x, y, w, h, argb);                     // GuiGraphics.fill
c.fillGradientV(x, y, w, h, topArgb, bottomArgb);   // 竖向渐变（GuiGraphics.fillGradient 是纵向的）
c.strokeRect(x, y, w, h, topColor, bottomColor);     // 1px 边框（上亮下暗）

// 贴图（项目资源，路径相对 src/main/resources/assets/rotten_ruins/）
PreviewTexture tex = c.texture("textures/gui/xxx.png");
c.blit(tex, x, y, w, h);                       // 整张
c.blit(tex, x, y, w, h, u0, v0, u1, v1);       // UV 子区域
c.blit(tex, x, y, w, h, u0, v0, u1, v1, tint);// tint 是 ARGB 乘算

// 文字（MC 位图字体，行高 9）
c.text("...", x, y, argb, shadow);
c.textCentered("...", centerX, y, argb, shadow);
c.textRight("...", rightX, y, argb, shadow);
c.textScaled("...", x, y, argb, shadow, 2.0F);          // 2x/3x 像素风大字（标题、中心信息）
c.textCenteredScaled("...", centerX, y, argb, shadow, 2.0F);
int w = c.textWidth("...");
int lh = c.fontHeight();                       // 9

// 2D 变换（旋转图标 / 弧形文字 / 花瓣形扇区）
c.pushTransform();
c.translate(cx, cy);
c.rotate(degrees);                             // 顺时针为正
c.scale(1.2F, 1.2F);
c.blit(tex, -8, -8, 16, 16);                  // 图标跟着转
c.popTransform();
c.rotateAround(degrees, cx, cy);               // 绕点旋转的快捷方式

// 任意纯色三角形网格（顶点布局：x,y,r,g,b,a；颜色 0..1，吃当前 2D 变换）
// 圆弧、圆环、多边形、星形、聚焦光锥都可以用它拼
c.fillMesh(vertices, vertices.length, vertexCount);

// 剪裁
c.pushClip(x, y, w, h);
c.popClip();

// 读贴图像素（只读检查用，比如按 mask 亮行算几何；返回 BufferedImage）
BufferedImage img = c.image("textures/gui/xxx.png");

// 导出当前帧
c.save("name");                                // → build/gui-preview/name.png
```

## SDF 平滑形状（`sdf_ui` 着色器 + `SdfShapes`）

三角光栅化拼出来的圆/圆环/圆角矩形没有覆盖率抗锯齿，GUI 缩放 2~4 时边缘就是
按物理像素放大的台阶。本工具提供一支工具私有 SDF 着色器 `sdf_ui`，在片元里算
有向距离，再按 **1 个物理像素**的宽度做 coverage（思路参考 FIS-Cordyceps（MIT）
的 `wheel_sdf.fsh`）。

```java
// 推荐：SdfShapes 封装（返回 false 表示着色器不可用，调用方自行降级 CPU 绘制）
SdfShapes.disc(c, cx, cy, radius, argbTop, argbBottom);
SdfShapes.ring(c, cx, cy, rIn, rOut, argbIn, argbOut, /*radial=*/true);
SdfShapes.wedge(c, cx, cy, rIn, rOut, a0, a1, argbIn, argbOut);
SdfShapes.roundRect(c, x, y, w, h, radius, argbTop, argbBottom, borderColor, 1.0F);
SdfShapes.line(c, x1, y1, x2, y2, halfWidth, argb);            // 圆头胶囊
SdfShapes.star(c, cx, cy, rOut, rIn, points, rotation, argb);  // 精确多边形星形
```

- **抗锯齿宽度由 `uScale` 决定**：`uScale = 1 个 GUI 像素对应的物理像素数`。
  预览里自动取 `c.pixelScale()`（= `-Scale` 原生渲染倍率，默认 2）；进游戏时传
  `Minecraft.getInstance().getWindow().getGuiScale()`，任何 GUI 缩放下边缘都是
  1 个物理像素的过渡。
- `SdfShapes` 每个形状画一个覆盖包围盒的 quad；uniform 是粘滞的，内部的
  `begin()` 会把所有 uniform 复位，不要在它外面手动共用 uniform。
- 星形用「2n 条边的精确距离 + 奇偶规则」而不是极坐标线性插值，尖角才是尖的。
- 图标（16×16 像素贴图）与 MC 位图字体**故意保持像素风**：图标按 8 的倍数
  （2 倍原生渲染下每 texel 落到整数物理像素）、字体只用 1x/2x/3x 整数倍。

## 项目 core shader

```java
PreviewShader sh = c.shader("gui_tab_divider");   // 先找 tools/gui-preview/shaders/，再回落项目 assets
sh.set("uTime", 1.5f);                            // 1/2/4 个 float（json 的 count 决定 vec2/vec4）
sh.set("uSize", 162f, 18f);
sh.setSampler("MaskTexture", c.texture("textures/gui/xxx.png"));  // json samplers 里的名字
c.shaderQuad(sh, x, y, w, h);                     // UV 0..1 的 quad（有 float 重载，亚像素精度）
c.shaderQuad(sh, x, y, w, h, u0, v0, u1, v1);     // 自定义 UV
```

- **工具私有着色器**：把 `xxx.json/.vsh/.fsh` 放进 `tools/gui-preview/shaders/`，`c.shader("xxx")`
  会优先加载它（设计稿专用、进游戏前不进 `assets/`）；见 `tools/gui-preview/shaders/README.md`
  与 `wheel_slice / wheel_aura / wheel_core` 三支轮盘着色器。
- `ProjMat`（正交，GUI 坐标）和 `ModelViewMat`（单位阵）由 canvas 自动填，
  与游戏里 `BufferUploader.drawWithShader` 拿到的 RenderSystem 矩阵同语义
- 混合状态按 json 的 `blend` 自动应用（**json 赢**，和 MC 的 `ShaderInstance.apply` 一致）
- 只写 json 里声明过的 uniform（没声明的框架静默忽略 —— 和实机警告一致）
- 自定义几何（法阵环、UV 编码参数等）用：

```java
c.shaderMesh(sh, interleavedFloats, floatsCount, vertexCount, GL11.GL_TRIANGLES);
```

顶点布局 = json `attributes` 顺序：`Position`(3 float)、`UV0`(2)、`Color`(4)、`Normal`(3)。

## 常见坑（框架已经处理，但场景要知道）

- **`fill` 的参数是 `(x, y, w, h)`，不是 `GuiGraphics.fill(x1, y1, x2, y2)`**。
  从实机代码抄坐标时把 `x2` 换成 `x + w`：`gfx.fill(x1,y1,x2,y2,c)` → `c.fill(x1,y1,x2-x1,y2-y1,c)`。
- **未声明的 uniform 是 0**：和实机一样，json 里写了默认值的 uniform 若场景不设，
  就会用 json 默认值（框架已按 json 写入默认值）；scene 要覆盖就显式 `set`。
- **`ProjMat`/`ModelViewMat` 是自动的**：不要自己设。
- **blend 由 json 决定**：例如 `srcalpha / 1-srcalpha`（普通透明）、`src_alpha / one`（加色）。
- **框架自动恢复状态（场景不用再兜底）**：
  - `sh.set()` / `sh.setMat4()` 内部会 `glUseProgram` —— 多支 shader 交替不用自己绑 program；
  - `fill/fillGradientV/blit/text` 绘制前会恢复标准 alpha 混合 —— 加色 shader 的 blend 不会泄漏到后续 GUI 元素；
  - 长文本 / 大网格（>16K float）自动走堆缓冲，不受 LWJGL MemoryStack 64KB 限制；
  - 空格字距按 `space.json`（4px），`drawText` 与 `textWidth` 用同一套 advance。
- **PNG 永远不透明**：离屏混合会把 FBO alpha 越叠越低，导出时强制 alpha=255，
  避免看图软件把暗角铺成白色。
- **不要用 AWT 画输出**：所有东西都走 canvas（保证和实机同一套混合/像素规则）。

## 参考资料

- 项目资源：`src/main/resources/assets/rotten_ruins/`
- 客户端代码：`src/main/java/org/bytechen/rottenruins/client/`
- 英文文案：`src/generated/resources/assets/rotten_ruins/lang/en_us.json`
- 现有截图级实证：`build/shader-preview/`、`docs/`（tooltip / 血条 / 法阵的实机常量都写在注释里）
- 框架自检：`-Scene smoke` → `build\gui-preview\smoke_frame1.png`
