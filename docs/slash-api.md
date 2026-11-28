# 武器挥砍接口

`ISlashWeapon` 是唯一接入条件。仅继承 `SwordItem`、拥有旧物品标签或属于本模组，都不会自动启用挥砍动画、范围攻击和刀光。当前主手左键使用此接口，副手仍走原版。客户端配置 `itemOnlySwordSlashes` 只关闭动画和刀光，不改变服务端伤害。

## 接入与分包

| 目录 | 职责 |
| --- | --- |
| `api/combat/slash` | 武器接口、配置、姿势、回调上下文、刀光描述 |
| `core/combat/slash` | 纯 Java 的扇形体积与碰撞箱相交算法 |
| `gameplay/combat/slash` | 候选目标查询、队友／遮挡／PVP 过滤 |
| `platform/forge/combat/slash` | 服务端起手、冷却、命中时机、网络与生命周期 |
| `client/combat/slash` | 服务端确认后的动画、客户端扩展、刀光注册与绘制 |
| `client/rend/slash` | 保留的物品几何分析、屏幕轨迹和下方归位数学 |
| `content/combat/slash/copper` | 纯铜剑参数：像素贴图刀光 |
| `content/combat/slash/domerite` | 多米勒特剑参数：程序化着色器刀光 |
| `content/combat/slash/verdict` | 天穹裁决参数：淡蓝白着色器刀光与独立裁决伤害回调 |

接入武器为 `rotten_ruins:true_copper`、`rotten_ruins:domerite_sword` 和天穹裁决 `rotten_ruins:domerite_longsword`。其他特殊武器保持各自现有攻击行为。

通用挥砍通过玩家攻击力属性取得力量加成。天穹裁决保留独立伤害模型：普通伤害为 25 + 力量加成 + 附魔，力量每级增加 3 点并随基础部分参与暴击；额外 5 点直伤保持固定。右键技能数值不受此调整影响。

```java
public class MySword extends SwordItem implements ISlashWeapon {
    private static final SlashSpec SLASH = SlashSpec.builder()
        // 外半径、内半径、扇形总角度、切割平面法线方向的半厚度（格）
        .range(3.5, 0, 120, 0.9)
        // 服务端冷却 tick、主动画时长 tick、命中时刻（动画比例）
        .timing(12, 10, 0.47F)
        // 伤害倍率、击退强度、每刀最多目标、命中后的每刀耐久消耗
        .damage(1, 0.3F, 16, 1)
        .targeting(false, true) // 是否允许队友、是否要求视线
        .combo(30, SlashPose.RIGHT_TO_LEFT, SlashPose.LEFT_TO_RIGHT,
                   SlashPose.UPPER_RIGHT_TO_LOWER_LEFT, SlashPose.UPPER_LEFT_TO_LOWER_RIGHT)
        .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.PLAYER_ATTACK_STRONG, 0.8F, 1)
        .trail(new SlashTrail(SlashTrail.TEXTURE, SlashTrail.DEFAULT_TEXTURE,
                             0xFFFFFFFF, 0.48F, 2.0F, 7))
        .build();

    @Override public SlashSpec slashSpec(Player player, ItemStack stack) { return SLASH; }
}
```

配置是不可变快照，构造时检查有限数值与上下限。可按玩家、物品 NBT、附魔等返回不同配置；`slashSpecForPose` 可以在选定姿势后单独改变该刀的参数。`selectSlashPose` 可以自行决定连招姿势，默认按 `poses` 循环。停止超过 `comboResetTicks` 或更换物品类型后回到第一刀，默认为右到左。

## 服务端回调

所有下列回调都在服务端主线程执行。上下文包含玩家、实际手中 ItemStack、姿势、连招序号、配置快照和起手时锁定的方向。

| 方法 | 用途 |
| --- | --- |
| `canStartSlash` | 起手许可，返回 false 不进入冷却 |
| `onSlashPose` | 每次选定姿势后的回调，可按四种姿势分别处理 |
| `onSlashStart` | 接受起手 |
| `onSlashTick` | 起手到主动画结束期间的每 tick 回调 |
| `canHitWithSlash` | 几何与通用过滤之后的自定义目标过滤 |
| `beforeSlashHit` | 命中前许可，可跳过该目标 |
| `applySlashDamage` | 应用已结算暴击的伤害并返回是否命中；特殊武器可保留独立伤害管线，默认调用目标 hurt |
| `slashDamage` | 自定义该目标的伤害，默认攻击属性加附魔伤害后乘倍率 |
| `allowsSlashCriticals` | 是否启用暴击，默认 true |
| `slashCriticalMultiplier` | 挥砍暴击候选的默认倍率，默认 1.5；Forge 事件仍可修改 |
| `slashCriticalDamage` | 自定义最终暴击伤害，默认基础部分乘倍率、附魔部分保持相加 |
| `slashCriticalSound` | 暴击命中音效，默认原版暴击声 |
| `onSlashCritical` | 成功造成暴击伤害后的逐目标回调，包含实际倍率 |
| `onSlashHit` | 每个尝试伤害的目标回调，包含请求伤害和是否实际造成伤害 |
| `onSlashMiss` | 本刀没有成功伤害任何目标 |
| `onSlashEnd` | 正常结束，包含成功命中数 |
| `onSlashCancelled` | 换手中物品、死亡、使用物品、跨维度或被下一刀替换等中断 |

例如在 `onSlashHit` 中用 `if (dealt)` 添加状态效果，在 `onSlashPose` 中用 `switch (context.pose())` 处理不同姿势。范围攻击默认不调用旧 `Item#hurtEnemy`，避免特殊武器再次施加伤害或重复扣耐久；迁移特殊武器时，把其命中效果放入此回调，或由武器自行调用必要的旧逻辑并调整耐久配置。

默认伤害调用 `LivingEntity.hurt(playerAttack, damage)`，保留正常护甲、盾牌、伤害免疫和 LivingAttack/Hurt 等事件；每个目标也经过 Forge 的攻击许可钩子，尊重外部 AttackEntityEvent 取消。附魔伤害与伤害后附魔回调参与结算。该路径不是完整的 `Player.attack` 复刻：火焰附加等其他行为可由接口回调实现。普通单体攻击入口对接口武器取消，防止与范围攻击重复伤害；内部逐目标许可检查使用独立守卫，不会被自己的单体入口拦截器误取消。

暴击在服务端起手至主命中之间锁存：离地且处于下落或接近跳跃顶点（向上速度不超过 0.08 格/tick）即可成为候选，不再要求原版攻击蓄力超过 90%，并允许疾跑跳劈。仍排除攀爬、水中、失明、骑乘、创造飞行和鞘翅滑翔。只要命中前进入过这个窗口，随后落地不会丢失暴击；已经结算后不会重新改判。默认基础伤害 ×1.5，再加附魔伤害，播放原版暴击粒子和每刀一次的暴击声。逐目标调用 Forge `CriticalHitEvent`，保留其他模组的拒绝、强制暴击和倍率修改。自定义伤害模型仍可通过 `allowsSlashCriticals`、`slashCriticalMultiplier`、`slashCriticalDamage` 和 `onSlashCritical` 调整。


## 命中与同步

客户端只发送“主手尝试挥砍”，不发送目标、伤害、距离、姿势或冷却。服务端重新读取实际持物、存活／使用状态、冷却和武器配置；接受后向自己及跟踪该玩家的客户端同步姿势、主动画时长、刀光、物品快照与方向。动画不依赖普通挥手包，不会因描边重放多推进一次连招。

范围以命中时玩家眼睛为原点，方向在起手时锁定。主命中时刻一次结算整个扇形体积，每个目标最多一次；不以客户端逐帧刀尖作为伤害来源。扇形在四种姿势对应的切割平面内展开，沿法线具有可配置厚度。先用 AABB 粗筛，再用凸棱柱切片与目标碰撞箱做 SAT 检测，能命中“中心在范围外但碰撞箱边缘进入范围”的大型目标。

弧面每 5 度切一片，外圆以弦近似，32 格最大半径下边界误差约 0.03 格。内半径可形成空洞。默认过滤玩家自己、同乘载具、队友／自己的驯服生物、禁止 PVP 的玩家和不可见目标；候选按距离排序并受最大目标数限制。挥砍使用服务端按玩家记录的独立冷却，不读取或写入原版 `ItemCooldowns`，因此右键技能冷却与挥砍互不阻挡、互不覆盖。可用 `SlashServer.remainingCooldownTicks(ServerPlayer)` 查询剩余 tick，换武器不会绕过这段冷却。冷却被服务端再次验证，开始时消费；取消不会返还。若冷却短于主动画，新刀可以中断并取消上一刀尚未完成的部分。

主动画结束后，第一人称仍可继续原有的约 0.55 秒离屏停留与下方归位；不会延长攻击冷却。斜下砍的离屏阶段保持原深度和朝向，沿原斜线继续向下、向外移动，直到整把武器离开画面；之后才改变深度、转向和换位，防止透视缩小让剑从屏幕底部反弹回画面。每刀独立从画面外进入自己的起点，预备不把上一次终点横拉到下一次起点。四种姿势的剑身长轴都朝向前方；斜砍位移与刃口方向使用固定的 45° 切割平面，不再用屏幕宽高各自归一化来决定角度。宽高比／FOV 只决定轨迹伸到视口外所需的长度，宽屏或竖屏下都保持与世界斜砍平面同向。第三人称坐标包含玩家的视线俯仰和头部相对身体的偏航。无挥砍时仍是原版手持姿势。非标准烘焙几何识别失败时保留原版物品渲染，刀光仍可显示；独立 Geo renderer 需要单独的视觉适配。

## 自定义动画和刀光

客户端初始化时可用 `SlashVisuals.register(item, handler)` 注册 `sample`、`transform`、`onStart`、`onEnd`。其中采样和变换属于渲染回调，描边捕获可能重复调用，不能在里面施加伤害或推进连招。

`SlashTrailRenderers.register(rendererKey, drawer)` 可注册新的刀光绘制器，再在 `SlashTrail.renderer` 中选择该 key。内置两种：

- `SlashTrail.TEXTURE`：64×16 像素风透明贴图，最近邻采样，按武器 ARGB 染色。
- `SlashTrail.SHADER`：独立 GLSL 程序化亮边与条纹，不采样刀光贴图。

`SlashTrail` 继续选择材质、贴图和染色。内置刀光现在由物品模型锚点的实际渲染历史生成；第一、第三人称独立采样，都在世界阶段按场景深度绘制。锚点决定范围和寿命，旧的 `width/radius` 保留供自定义绘制器使用。配置路径、编辑工具及详细参数见 [刀光锚点编辑器](slash-anchor-editor.md)。

每个真实渲染帧记录完整物品矩阵，包含上一帧旋转、位置和缩放。补采样后连接相邻截面的有序锚点，贴图与着色器共用轨迹几何。没有配置锚点时，内置材质不再自动画出一条与剑身独立的推算圆弧；旧 `SlashTrailGeometry` 留作解析参考和自定义绘制器工具。描边与光影阴影 pass 排除在采样外。

像素图的方向、调色与替换步骤见 [绘制说明](slash-trails/painting.md)。原图为 `assets/rotten_ruins/textures/effect/slash_crescent.png`，生成器为 `tools/generate-slash-texture.py`，再次运行会覆盖手工编辑。

## 验证命令

- `./gradlew.bat build --offline --console=plain`：编译、边界检查与打包。
- `tools/tests/SlashVolumeTest.java`：纯 Java SAT／解析参考测试，含一万组参考点。
- 既有 `SlashMotionTest`、`BladeGeometryTest`、`SlashProjectionTest`、`SlashRecoveryTest`、`SlashTimelineTest`：轨迹、模型检测、屏幕覆盖与归位。
- `./tools/preview-slash-trails.ps1`：隐藏 OpenGL 上下文，编译实际刀光 GLSL，绘制贴图与着色器效果至 `build/slash-preview/preview.png`。
- `tools/tests/SlashWorldTrailTest.java`：检查原始切割平面、内外半径、零法线偏移，以及移动观察位置后可看见弧带。离屏预览使用同一网格代码，从偏移的世界相机比较四种姿势和两种材质，不要求第一人称正面可见。
- `./gradlew.bat -I tools/slash-gametest.init.gradle runGameTestServer --offline --console=plain`：只运行挥砍测试命名空间，测试多目标、冷却、后方排除、换武器中断、非接口武器、墙体遮挡，以及技能/挥砍冷却隔离、顶点窗口锁存、零原版蓄力疾跑暴击、附魔加算和 Forge 暴击事件；临时世界在 `build/slash-game-test`。

离屏预览只能验证刀光材质，不能替代游戏内相机、光影模组和物品动画的视觉验收。
