# Markdown 阅读器

在游戏内输入 `/rrsmd` 打开随模组附带的《废墟调查手册》。输入 `/rrsmd <命名空间:文档路径>` 打开资源包中的其他文档，例如 `/rrsmd rotten_ruins:guides/spells`。文档 ID 省略 `.md` 后缀。省略命名空间时使用 `rotten_ruins`。

## 编写本地化文档

用 UTF-8 保存 Markdown。将同一文档放在不同游戏语言子目录下，保持文件名和子路径一致。

```text
assets/rotten_ruins/markdown/
├── en_us/guides/spells.md
└── zh_cn/guides/spells.md
```

先按 Minecraft 当前语言查找，缺少对应文件时回退到 `en_us`。每个文件遵循原版资源包优先级。屏幕标题和错误提示沿用普通语言键，文档正文直接写在 Markdown 文件里。单份文档最多 512 KiB。使用资源包覆盖后，重载资源并重新打开文档即可读取更新。

支持标题、段落、粗体、斜体、引用、嵌套列表、任务列表、删除线、分隔线、行内和块级代码、表格、链接及 PNG 图片。文档使用原版字体测量和双向文本排序。窗口或 GUI 缩放变化时重新换行，窄表格按记录纵向显示。按住左键可以直接拖动正文或右侧滚动条，也可用滚轮、方向键、Page Up／Page Down、Home／End 浏览。链接在单击松开时打开，拖动时取消链接点击。

- 链接可打开 HTTP／HTTPS 页面，遵循原版聊天链接开关和确认选项。页内链接跳转到标题，例如 `#field-equipment`。标题锚点保留字母、数字、组合标记、下划线和连字符，空格变为连字符，重复标题追加 `-1`、`-2`。单独的 `#` 跳回顶部。
- 图片可使用相对于实际加载的 Markdown 文件的路径，或指定 `命名空间:textures/路径.png`。按视口等比缩小，找不到本地 PNG 时显示替代文字。图片独占一行，网络图片显示替代文字。
- HTML 标签按字面显示，任务列表用于阅读展示。其他链接目标保留标签文字。

## GUI 接入与依赖

`client/gui/markdown` 负责资源选择、CommonMark 语法树、排版和滚动。需要完整屏幕时构造 `MarkdownScreen(parent, id)`。需要内嵌时用 `MarkdownView.load(minecraft, id, this::handleComponentClicked)` 加载，再通过 `setBounds` 指定视口并用 `addRenderableWidget` 安装。嵌入宿主按自身输入策略管理键盘焦点。宿主可在 `hasPointerCapture()` 为真时将左键释放转发给视口，即使指针已经移出边界。

`platform/forge/client/MarkdownClientCommands` 是标注为 `Dist.CLIENT` 的 Forge 命令订阅器，仅负责命令注册及打开阅读器。门禁清单记录此文件对 Forge 客户端事件及阅读器入口的两条侧入口依赖。渲染层直接使用 Minecraft 客户端 API，CommonMark 依赖保留在客户端功能中，`core` 仍可空 classpath 编译。

CommonMark Java 和所用的三项扩展通过 ForgeGradle 的 `minecraftLibrary` 接入开发运行，通过 Jar-in-Jar 进入发布用 `-all.jar`。首次构建需从 Maven Central 缓存依赖，之后可用仓库的离线构建命令。[CommonMark Java](https://github.com/commonmark/commonmark-java) · [Forge Jar-in-Jar](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/)
