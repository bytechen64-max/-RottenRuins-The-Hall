# 空间剧情文本

剧情定义、触发规则、数据包覆盖与重载现已 JSON 驱动，完整字段和接入说明见 [JSON 剧情系统](story-json.md)。

剧情文本只对接收玩家显示，默认位于眼高、水平前方 1.6 格，基础字形缩放为 0.0125，并带文字阴影以提高辨识度。触发时固定水平方位，随后跟随玩家位置平移；转头不会把文本拖回视野。面板沿玩家周围的半径朝向玩家中心，面板朝向随段落固定，不随当前视角旋转。采用全亮、透视文本，墙体不会挡住剧情。

当完整文本页不在视野内时，屏幕边缘出现指向文本的三角；转回文本后提示消失。正后方默认提示向右转，背后左右目标保持正确方向。支持第一、第三人称，低 FOV 或窄窗口自动缩小面板以容纳整页。

每行最多 200 字体像素，每页最多 3 行，长段自动分页。入场使用固定阅读单位速度：一个中文字符或一个英文单词各算一个单位，英文单词内部字母依次快速展开；空白不消耗时间，标点各算一个单位。上一行打完后间隔 8 tick 再开始下一行，已出现的行保留。每个新字以自身中心从 1.2 倍大小，在 6 tick（0.3 秒）内平滑缩回 1 倍；排版使用完整行的浮点 advance，后续文字不会推移已经出现的字。

整页完成后按命令的秒数停留，再按原来的顺序退场：中文逐字弹起，英文单词内字母依次快速弹起，按初速度与恒定重力的弹道落下，附带稳定的小幅横向漂移和旋转。运动发生在随玩家平移的文字面板空间，不生成实体；每字飞行 40 tick（2 秒），最后 16 tick 边掉落边溶解，等待最后一个字退场完成才切换下一页。噪声附着在字形纹理上，不随转头漂移；支持位图与 TTF 字形纹理。

同一段的所有页面保持相同方位；排队的下一段开始时重新按当前玩家朝向定位。普通剧情的打字、停留和退场只在完整文本页可见时推进，打开菜单或暂停时不计时。F1 隐藏 HUD、T 打开聊天框均保留空间文本和正常播放；F1 仍隐藏屏幕上的三角 HUD 提示。切换维度、退出世界、死亡清除当前文本与队列。此状态不写入存档，也不作为服务端剧情进度的凭据。

血肉温床仪式使用固定的世界时间线：四页文楷剧情各 200 tick，共 800 tick（40 秒），和服务端仪式共享起止时间。转头、F1 或打开聊天不改变其进度；菜单内不绘制文本时，时间线仍跟随世界时间推进。翻译文本产生额外分页时，各子页均分该段时长。仅调整完整文本的停留时间，不按字符总数缩放入场、出场或物理运动；极慢的自定义速度或额外的长翻译仍受仪式期限限制。普通 `show` / `append` 的观看计时不受此机制影响。

## 游戏内命令

死亡重生后延迟 40 tick（2 秒）显示文楷台词，打字完成后停留 120 tick（6 秒）再退场。该类文本不因转头而暂停。每名玩家独立记录首次死亡、连续死亡和上一条台词；选择优先级为首次死亡 → 3 分钟内连续死亡 → 坠落/溺水/生物击杀 → 普通重生，候选池内避免连续重复，共 13 条中英文台词。死亡记录保存在 `PlayerPersisted.rotten_ruins:respawn_story`，重登不重放已消费台词；通关末地返回不触发死亡剧情。

每个玩家首次进入存档后约 3 秒自动触发三行开场剧情，展示在玩家前方；全部打完后停留 12 秒再溶解。难度选择等界面打开时等待，关闭后可阅读。播放标记存于玩家的 `PlayerPersisted.rotten_ruins:opening_story_seen`，重登、死亡重生不重播；升级前的旧玩家也会在第一次安装本功能后播放一次。测试文本由 `rotten_ruins.story.opening` 中英文语言条目提供。

需要权限等级 2，可由命令方块、函数或管理员执行：

```mcfunction
# 打字完成后停留 8 秒，位于触发时的前方；替换正在播放的剧情及队列
/rotten_ruins story show @s 8 0 那颗陨石……仍在呼吸。
# 位于右侧 90 度，转头后可阅读；负角度在左侧
/rotten_ruins story show @s 8 90 身后传来低沉的回响。
# 排队追加下一段；文字中的字面量 \n 可换行
/rotten_ruins story append @s 6 0 不要回头。\n继续往前走。
/rotten_ruins story clear @s
```

参数为 `show|append <players> <seconds:1..600> <angle:-180..180> <text...>`。
多人选择器会向每个玩家独立显示。命令方块请使用 `@p` / `@a` 等选择器。

## 服务端剧情接入

```java
StoryText.show(player, Component.translatable("your.story.key"), 160);
StoryText.enqueue(player, Component.literal("下一段剧情"), 120);
// 完整放置参数：阅读 tick、水平偏角、水平距离、相对眼高、是否追加
StoryText.send(player, text, 160, 90, 3, 0.3, false);
StoryText.clear(player);
```

入口：`gameplay/story/StoryText`。使用现有 UniversalPacket 的独立 `story_text` action，无新增包序号；客户端只接受 PLAY_TO_CLIENT。支持 Component 样式及翻译组件。JSON 文本最多 4096 字符，等待队列最多 128 页，超过队列容量的新增页面丢弃。距离限制 1..8 格，高度限制 -2..2 格，打字完成后的单页停留时间限制 20..12000 tick。

## 自定义字体

内置四个命令预设：`default`（原版）、`wenkai`（霞鹜文楷 Lite，楷书风格）、`xiaowei`（站酷小薇，宋体风格）、`mashan`（马善政毛笔体）。三个 TTF 保留上游原文件，来源、固定版本、SHA-256 和 OFL 原文均位于 `assets/rotten_ruins/font/licenses/`。

重启客户端加载新增资源与命令后，使用以下命令测试（权限等级 2）：

```mcfunction
# 同一段三行开场文本，依次播放四种字体；打字后停留 4 秒，再逐字弹起掉落
/rotten_ruins story preview all
# 单独预览某款字体，输入字体参数时可按 Tab 补全
/rotten_ruins story preview wenkai
/rotten_ruins story preview xiaowei
/rotten_ruins story preview mashan
/rotten_ruins story preview default
# 自定义文本；参数为 玩家、停留秒数、偏角、字体、文本
/rotten_ruins story show_font @s 5 0 wenkai 你在陌生的废墟中醒来。\n陨石仍在呼吸。\n你听见了它的心跳。
/rotten_ruins story append_font @s 5 0 mashan 不要回头。
```

`preview` 对执行玩家显示，聊天栏提示字体名称或轮播顺序；命令方块可使用带选择器的 `show_font` / `append_font`。原有 `show` / `append` 语法保留。`preview all` 的顺序为 `default → wenkai → xiaowei → mashan`，四段使用相同内容、位置和时长，每段开始时按当前朝向定位。

剧情使用独立字体资源 `rotten_ruins:story`，配置位于 `src/main/resources/assets/rotten_ruins/font/story.json`，默认引用原版字体。要替换为 TTF，将字体放在同一目录，例如 `story.ttf`，然后把配置改为：

```json
{
  "providers": [
    { "type": "ttf", "file": "rotten_ruins:story.ttf", "size": 11, "oversample": 8, "shift": [0, 0] },
    { "type": "reference", "id": "minecraft:default" }
  ]
}
```

TTF 字体应包含所需中文字形；没有的字回退到原版字体。`oversample` 提高字形采样精度，可配合字号调整清晰度。更改后通过 F3+T 重载资源，再触发一段新文本。该配置也可通过资源包覆盖，不影响其他 UI 的字体。剧情 Component 若显式指定其他字体 ID，会保留该字体。

三款内置 TTF 采用 8 倍采样（逻辑字号仍为 11），避免约 22 像素的旧字形在近距离被放大。剧情 shader 对 TTF 覆盖率做局部双线性重建和四点屏幕像素采样，保持字体图集原本的过滤状态；覆盖率仅作用于透明度，避免边缘颜色重复变暗。文字阴影由原版 1 像素偏移改为 0.25 / 0.35 逻辑像素、40% 透明度，减轻粗黑重影。原版预设仍使用其原始像素字形。

## 验证

客户端 `config/rotten_ruins/SplendidingConfig.json5` 中的 `storyTextEntrySpeed` 为入场单位/秒（默认 10），`storyTextExitSpeed` 为出场单位/秒（默认约 16.67）。数值越大越快，修改文件后重启客户端生效。速度不受整页字数影响，单字缩放与弹道仍使用实际 tick。

剧情保留原始翻译 Component。客户端切换语言或重载语言资源时，会重新翻译和分页当前段落及待播内容；普通页面重新开始打字，仪式页面仍按原有服务端起止时间播放，文本的空间方位不变。手动 `show` / `show_font` 命令输入的是字面文本，不会自动翻译。

剧情文本阴影可在客户端的 `config/rotten_ruins/SplendidingConfig.json5` 中设置：`storyTextShadow: true` 显示阴影（默认），`false` 关闭。修改文件后重启客户端生效；普通渲染与光影延后渲染共用此选项。

光影开启时沿用项目的延后绘制入口：`AFTER_ENTITIES` 只保存当帧文字顶点、投影及 RenderSystem ModelView，`CosmicAfterLevelMixin` 在 `GameRenderer.renderLevel` 的 TAIL 调用 `StoryTextClient.renderDeferred()`，通过 `LateOutlineRenderState` 绑定主渲染目标后绘制，避免文字写入光影 GBuffer。回放后恢复投影、排序方式和 ModelView；阴影 pass 不显示文字，也不覆盖主视角快照。关闭光影仍在实体阶段即时绘制。兼容检测复用 `ShaderPackDetector`；优化底包中的 Mekalus 1.8.0.1 注册为 `oculus`，提供相同的 Iris API。

光影实机验收：执行 `/rotten_ruins story preview all`，检查四种字体、打字机与溶解；转头、移动、切换第一/第三人称；F1 和打开聊天栏后文字仍显示；开关光影后再次预览。

正文和阴影采用明确的两次提交：先提交整页所有阴影并结束批次，再提交整页正文并结束批次。剧情文字关闭按相机距离重排四边形，保持绘制顺序，防止左侧文字或新字被阴影盖暗。切换字体图集也不能跨越这两个层的提交边界。

```powershell
.\gradlew.bat compileJava --offline
.\gradlew.bat copyPatchedMods runGameTestServer --offline
javac -d build/story-text-tests src/main/java/org/bytechen/rottenruins/client/story/StoryProjection.java tools/tests/StoryProjectionTest.java
java -cp build/story-text-tests StoryProjectionTest
javac -d build/story-text-tests src/main/java/org/bytechen/rottenruins/client/story/StoryPlayback.java src/main/java/org/bytechen/rottenruins/client/story/StoryGlyphMotion.java tools/tests/StoryPlaybackTest.java tools/tests/StoryGlyphMotionTest.java
java -cp build/story-text-tests StoryPlaybackTest
java -cp build/story-text-tests StoryGlyphMotionTest
```

实机验收：执行 show，向前走、横移和跳跃，文本应随玩家移动；左右转头和抬头低头，离开视野时三角应指向目标。转到背后再回看，文本不应因计时消失。测试长文本分页、append、clear、F1、暂停、死亡、切换维度，以及多人各自的私有展示。

`StoryTextGameTests` 验证首次登录延迟 60 tick 后发送、写入持久标记、恢复玩家数据后不重复，以及同一世界的新玩家能独立触发。服务端测试不包含客户端画面验收。

`tools/tests/StoryTextOrderTest.java` 使用 Minecraft 实际的 `BufferBuilder` / `VertexSorting` 复现旧的左侧阴影覆盖问题，验证 24 个逐字阶段及跨图集层提交顺序。将 `build/classpath/runClient_minecraftClasspath.txt` 中逐行列出的路径以分号拼成 classpath，编译该测试和 `StoryTextLayers.java` 后运行，无需启动游戏客户端。

空间变换回归测试：使用 Gradle 缓存中的 `org.joml:joml:1.10.5` jar 作为 classpath，编译 `StoryProjection.java`、`StoryGlyphMotion.java`、`StoryTextTransform.java` 和 `tools/tests/StoryTextTransformTest.java` 后运行 `StoryTextTransformTest`。覆盖四个环绕方位、完整转头、俯仰、文字不镜像、第三人称，以及单字缩放旋转中心与弹道位置。渲染使用 `AFTER_ENTITIES` 的世界视图矩阵，文本四角和 HUD 提示共用同一投影；Forge 1.20.1 的 `AFTER_LEVEL` 传入投影栈，不能当作世界视图栈。

`StoryGlyphMotionTest` 验证 1.2 倍入场、缩放收敛、恒定重力、下落末段溶解及逐字顶点进度编码；`StoryPlaybackTest` 验证跨行的入场时间、退场错峰和等待末字完成。逐字溶解通过顶点 UV2.x 编码，剧情 shader 使用固定全亮光照，不在同一批次中反复覆盖 uniform；`StoryDissolveShaderTest` 额外验证同一 GPU 批次中两个字能分别保持完整和完全消失。

`StoryDissolveShaderTest` 使用 LWJGL 的隐藏 OpenGL 上下文编译、链接实际 `story_text.vsh` / `.fsh`，并采样五个溶解进度，验证起始完整不透明、中间出现侵蚀、覆盖率持续下降、结束完全透明。还测试单通道 TTF 字形的空白区域全透明、边缘覆盖率平滑变化且保留前景颜色，以及彩色位图字体保留 RGBA。实际绘制时，在替换原版 shader 之前检查选中的原版 shader 实例来识别 TTF；不依赖 `RenderType.toString()` 的调试字符串格式。它不替代游戏内对字体、渲染状态和最终观感的验收。

`StoryFontAssetTest` 使用 LWJGL core / STB 及对应 Windows natives，加载三个实际 TTF 并栅格化全部开场中文字形及 Latin 样本，检查实际引擎兼容性与字形覆盖。`StoryTextGameTests.fontPreviewAndCustomTextCommands` 验证四种单独预览、全部轮播、指定字体、自定义文本排队、旧语法及无效字体处理。
