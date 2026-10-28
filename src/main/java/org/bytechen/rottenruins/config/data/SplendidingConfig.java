package org.bytechen.rottenruins.config.data;

import org.bytechen.rottenruins.HallMod;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = HallMod.MODID + "/SplendidingConfig")
public class SplendidingConfig implements ConfigData {

    /**
     * 当前配置版本（<b>代码里的最新版本号</b>）。
     *
     * <ul>
     *   <li>0 —— 引入裁决技能时的初版（基础冷却 3.0 秒）</li>
     *   <li>1 —— 冷却重调：基础 1.5 秒（这一次的迁移实现有 bug：写了版本号却没重置值）</li>
     *   <li>2 —— 修正上一条：真正把基础冷却重置为 1.5 秒</li>
     *   <li>3 —— 描边距离自适应的比例默认值 0.15 → 0.04。必须靠迁移重置：0.15 已经被
     *       写进过配置文件，而 cloth-config 以文件为准，光改代码里的默认值不生效
     *       （详见 {@code ConfigHelper.migrateSplendidingConfig}）</li>
     *   <li>4 —— 火焰高度三个参数重调（rise 0.30→0.16、buoyancy 0.55→0.30、
     *       cooling 2.10→4.0），同样是"已写进文件、必须迁移"的那一类</li>
     *   <li>5 —— 火焰高度<b>再配平一次</b>：版本 4 那组压过头了，小物品上几乎看不见火
     *       （rise 0.16→0.20、buoyancy 0.30→0.35、cooling 4.0→3.4），配合
     *       可见性包络门槛下调与尺寸比例下限放宽</li>
     *   <li>6 —— 电弧密度拉满（scale 9→16、threshold 0.22→0.10、gain 1.0→2.2、
     *       reach 0.06→0.12）：用户要求"尽量每一个地方都有电弧"。同样必须迁移</li>
     *   <li>7 —— 电弧改成<b>软椭圆带</b>（scale 30、threshold 0.15、gain 1.8、
     *       reach 弃用）+ 新增 arcBandPx(150) 与剪裁框放大：修"只有贴着轮廓一条缝"</li>
     *   <li>8 —— 修正电弧带的错配：v7 把 {@code arcReach} 设成 0（当时改用包围盒椭圆带），
     *       但着色器里 {@code ArcReach > 0} 才是「锚定剪影」的开关 —— 0 等于完全不限，
     *       电弧会铺满整个剪裁框（实测就是一大团离散圈圈）。回到剪影锚定 + 重新配亮度</li>
     *   <li>10 —— 电弧正式启用（arcEnabled）：这个键曾以 false 的默认值写进文件，改代码默认值不生效，必须迁移</li>
     * </ul>
     */
    public static final int CONFIG_VERSION = 12;

    /**
     * 本文件的配置版本号。
     *
     * <h3>为什么这里的默认值必须是 0，而不是 {@link #CONFIG_VERSION}</h3>
     * <p>踩过一次：写成 {@code = CONFIG_VERSION} 时，迁移<b>永远不会执行</b>。
     * 原因是 Java 字段初始化器的执行时机 —— gson 反序列化会
     * <b>先跑字段初始化器</b>（把值设成当前版本），<b>再</b>用文件内容覆盖。
     * 于是迁移里的 {@code if (configVersion >= CONFIG_VERSION) return;}
     * 在读取任何真实版本之前就已经为真，直接返回了。</p>
     *
     * <p>写成 0 的效果正好相反：文件里没有这个键时拿到 0（= 需要迁移），
     * 文件里有旧版本号时也小于最新版（= 需要迁移），
     * 而迁移成功后由 {@code ConfigHelper} 写回真实版本号并存盘。</p>
     *
     * <p><b>因此：以后提升 {@link #CONFIG_VERSION} 时，不要动这一行的值（保持 0）。</b></p>
     */
    @Comment("配置版本号（内部使用，改动裁决技能默认值时请提升 CONFIG_VERSION，详见代码注释）")
    @ConfigEntry.Gui.Excluded
    public int configVersion = 0;

    @Comment("Enable debug mode")
    public boolean debug = false;

    @Comment("Base damage multiplier")
    public float damageMultiplier = 1.0f;

    @Comment("Example integer config")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int exampleValue = 50;

    @Comment("Enable custom shader rendering (item glint, outlines, bloom)")
    public boolean use_shader = true;

    @Comment("物品体积火焰（fire_2）总开关。默认 false：走屏幕空间火焰（稳定、贴轮廓）。"
            + "true = 启用世界空间体积火焰（实验性：目前仍有一手贴脸/黑屏问题待排查）。")
    @ConfigEntry.Gui.Tooltip
    public boolean outlineVolumeFire = false;

    @Comment("冲击波空气折射强度倍率（1.0 = 默认；0 = 只保留亮边不做折射偏移）")
    @ConfigEntry.Gui.Tooltip
    public float shockwaveRefraction = 1.0f;

    @Comment("物品描边宽度全局倍率。1.0 = 各物品自己的 outlinePixelWidth（默认 2.5 像素）；"
            + "想整体加厚就调大（如 1.5、2.0），想变细就调小。改这个不用重新编译。")
    @ConfigEntry.Gui.Tooltip
    public float outlineWidthScale = 1.0f;

    /** {@link #outlineWidthMaxRatio} / {@link #outlineMinPixelWidth} 的默认值（配置读不到时也用它兜底）。 */
    public static final float DEFAULT_OUTLINE_WIDTH_MAX_RATIO = 0.04f;
    public static final float DEFAULT_OUTLINE_MIN_PIXEL_WIDTH = 0.75f;

    @Comment("描边宽度是否随物品在屏幕上的占比收缩。true = 近处（物品在屏幕上够大）保持恒定像素宽，"
            + "远处自动按比例收细：宽度 = min(outlinePixelWidth, 物品表观尺寸 × outlineWidthMaxRatio)，"
            + "再夹在 outlineMinPixelWidth 之上；越过分界点后环宽始终是物品表观尺寸的这个比例。"
            + "false = 老行为：任何距离都是同一个像素宽度，远处小物品上的环会占去它两三成，"
            + "观感是糊成一圈外扩的厚边。")
    @ConfigEntry.Gui.Tooltip
    public boolean outlineDistanceScaling = true;

    @Comment("远处收细的比例：越过分界点后，描边宽度恒为「物品表观尺寸（含 pose 缩放的包围盒对角线）"
            + "× 本比例」。0.04（默认）≈ 环占物品 4%（细线）；调大 = 远处更粗更显眼，调小 = 更细。"
            + "分界点在「表观尺寸 = outlinePixelWidth / 本比例」处 —— 基准 4.5px + 0.04 时约 112 像素，"
            + "1600p 下掉落的平面物品约 6 格外开始收细；20 格处环宽约 1.4px（原来恒为 4.5px）。")
    @ConfigEntry.Gui.Tooltip
    public float outlineWidthMaxRatio = DEFAULT_OUTLINE_WIDTH_MAX_RATIO;

    @Comment("远处收细的下限（屏幕像素）。0.75 保证再远也还剩一条可辨的细线；"
            + "调到 0.5 以下会落进亚像素区间，观感是半透明薄雾而不是线。")
    @ConfigEntry.Gui.Tooltip
    public float outlineMinPixelWidth = DEFAULT_OUTLINE_MIN_PIXEL_WIDTH;

    // ──────────────────────────────────────────────────────────────
    //  描边光雾（体积感）
    //
    //  硬环之外再叠一层"由剪影遮罩降采样模糊出来"的雾，并用噪声把它打散成团块。
    //  雾的颜色来自遮罩本身 —— 所以配火焰颜色模式（outlineShaderKey() = "fire"）
    //  就是"沿着物品边缘燃烧"的效果。
    //
    //  关掉它（false 或强度 0）就完全回到纯硬环，其余一切不变。
    // ──────────────────────────────────────────────────────────────

    @Comment("描边光雾总开关。true = 硬环之外再叠一层模糊出来的雾；"
            + "false = 只剩等宽硬环（光雾出现之前的行为）。")
    @ConfigEntry.Gui.Tooltip
    public boolean outlineAuraEnabled = true;

    @Comment("光雾强度。0 = 关闭；0.9（默认）≈ 贴着物品的一团雾；1.5 以上会很浓。")
    @ConfigEntry.Gui.Tooltip
    public float outlineAuraStrength = 0.9f;

    @Comment("光雾扩散倍率：一并放大模糊步长。1.0 = 默认（约 52 像素）；"
            + "1.6 = 更大更淡的一团；调大可到 4.0。注意 scissor 会跟着一起外扩，"
            + "同屏描边物品很多时会略微增加填充开销。")
    @ConfigEntry.Gui.Tooltip
    public float outlineAuraSpread = 1.0f;

    @Comment("光雾的「云团」强度：把光滑的雾按噪声打散成团块。"
            + "0 = 光滑的椭圆光晕；0.8（默认）= 明显一团一团；1.5 以上会碎成缕。")
    @ConfigEntry.Gui.Tooltip
    public float outlineAuraNoise = 0.8f;

    // ──────────────────────────────────────────────────────────────
    //  框架效果：火焰（inject + advect + dissipate 的有状态场）
    //
    //  与描边的本质差别：描边是"每帧重算的图案"，火焰是"上一帧的密度被搬运过来"。
    //  下面这几个值就是那个动力系统的全部参数 —— 它们决定"像不像火"：
    //    Rise / Cooling 决定火焰高度（≈ Rise/Cooling 个屏幕高）
    //    Buoyancy 决定拉伸与断裂（热的地方升得更快 → 颈缩 → 断开成余烬）
    //    Turbulence 决定卷曲舔舐，Inject 决定根部有多旺
    //  哪个物品会烧由物品侧声明（ItemFxProvider），不是这里。
    // ──────────────────────────────────────────────────────────────

    @Comment("火焰效果总开关。false = 完全不跑平流场（物品侧声明了也不烧）。")
    @ConfigEntry.Gui.Tooltip
    public boolean flameEnabled = true;

    @Comment("浮力基准速度（屏幕高/秒）。0.20 = 默认；调大火焰升得更快更急。")
    @ConfigEntry.Gui.Tooltip
    public float flameRise = 0.20f;

    @Comment("浮力随温度的放大系数：热的地方升得更快 → 拉伸 → 颈缩 → 断裂成脱离的余烬。"
            + "0 = 均匀上升（像热气流，不像火）；0.35 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float flameBuoyancy = 0.35f;

    @Comment("湍流强度（curl 噪声，无散度 → 只卷曲不堆积）。0 = 直上直下；0.55 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float flameTurbulence = 0.55f;

    @Comment("燃料注入强度。调大根部更旺、火更密；调小则只剩零星火舌。0.70 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float flameInject = 0.70f;

    @Comment("冷却速率（1/秒）：越大火越短。火焰高度 ≈ (flameRise + 浮力项) / flameCooling 个屏幕高，"
            + "默认 (0.20+0.53)/3.4 ≈ 0.21 屏高（1600p 下约 340 像素，手持时再乘尺寸比例）。"
            + "觉得还高就调大；调太大会只剩贴着轮廓的一层雾。")
    @ConfigEntry.Gui.Tooltip
    public float flameCooling = 3.4f;

    @Comment("燃料转烟的比率（烟在场里的 G 通道）。冒烟效果的雏形，0 = 不产烟。")
    @ConfigEntry.Gui.Tooltip
    public float flameSmokeYield = 0.35f;

    @Comment("火焰亮度倍率。2.4 = 默认；嫌刺眼就调小。")
    @ConfigEntry.Gui.Tooltip
    public float flameGain = 2.4f;

    @Comment("温度→色带的映射尺度：>1 更容易出现白热核心，<1 更偏红。")
    @ConfigEntry.Gui.Tooltip
    public float flameTempScale = 0.85f;

    @Comment("烟对视线的压暗强度（火体外圈的薄烟）。0 = 不压暗。")
    @ConfigEntry.Gui.Tooltip
    public float flameSmokeDarken = 0.45f;

    @Comment("余烬闪点强度：最热处的高频孤立亮斑（纯合成期技巧，不需要粒子）。0 = 关闭。")
    @ConfigEntry.Gui.Tooltip
    public float flameSparkle = 0.55f;

    // ──────────────────────────────────────────────────────────────
    //  框架效果：电弧（arc，无状态的 SILHOUETTE 算子）
    //
    //  和火焰互补：火焰靠"有状态的平流"得到流体感，电弧靠"噪声场的等值线"
    //  （col += 宽度 / |阈值 − fbm(p,t)|）得到会分叉、会抖的细丝。它**无状态**，
    //  所以没有场、没有 ping-pong，成本只是几次 fbm。
    //  需要物品侧声明 arc 才会亮（ArcStrength 由绑定决定），下面只是全局参数。
    // ──────────────────────────────────────────────────────────────

    @Comment("电弧密度：噪声尺度。越大细丝越短越密。30 = 默认（1600p 下特征尺度约 50px）。")
    @ConfigEntry.Gui.Tooltip
    public float arcScale = 26.0f;

    @Comment("电弧阈值：越小细丝越少越粗、越像几道大电弧；越大越碎越密。0.15 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float arcThreshold = 0.15f;

    @Comment("电弧亮度。1.8 = 默认；配合 arcTonemap 才会出现白热芯，而不是糊成色斑。")
    @ConfigEntry.Gui.Tooltip
    public float arcGain = 2.0f;

    @Comment("电弧带宽度（像素）：以物品包围盒为基准再往外扩这么多。"
            + "150 = 默认。这是「完整电弧带」的关键 —— 太小就只剩贴着轮廓的一条缝。")
    @ConfigEntry.Gui.Tooltip
    public float arcBandPx = 150.0f;

    @Comment("电弧各向异性：把噪声沿**径向**（从物品射出去的方向）拉长的倍率。"
            + "1 = 各向同性（等值线是一堆闭环，像肥皂泡）；4 = 默认（拉成向外射的触须）。")
    @ConfigEntry.Gui.Tooltip
    public float arcAniso = 1.0f;

    @Comment("电弧线宽（像素）：疏密与粗细解耦之后的粗细旋钮（5 = 默认）。")
    @ConfigEntry.Gui.Tooltip
    public float arcLinePx = 2.5f;

    @Comment("电弧跨界带宽度：0.55 = 内外各覆盖约一段（0 = 不限制，会铺满剪裁框）。")
    @ConfigEntry.Gui.Tooltip
    public float arcBandScale = 0.55f;

    @Comment("电弧总开关。false = 完全不跑（默认）：电弧的场与合成都会跳过。")
    @ConfigEntry.Gui.Tooltip
    public boolean arcEnabled = true;

    @Comment("电弧读数模式：1 = 直接显示距离代理场（灰度）。用来定位「偏移」这类问题。")
    @ConfigEntry.Gui.Tooltip
    public int arcDebugView = 0;

    // ── 框架效果：裂纹（fracture）────────────────────────────
    @Comment("裂纹总开关。")
    @ConfigEntry.Gui.Tooltip
    public boolean fractureEnabled = true;

    @Comment("裂块尺寸：越大裂块越小。14 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float crackScale = 14.0f;

    @Comment("裂纹线宽（像素）。2 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float crackLinePx = 3.5f;

    @Comment("裂缝辉光强度。0.55 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float crackGlow = 0.95f;

    @Comment("辉光宽度倍率（相对线宽）。3.5 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float crackGlowWidth = 3.5f;

    @Comment("裂纹最深推进比例（1 = 可贯穿整个物品）。")
    @ConfigEntry.Gui.Tooltip
    public float crackReach = 1.0f;

    @Comment("裂纹进度 0..1：从边缘往内推进到哪。")
    @ConfigEntry.Gui.Tooltip
    public float crackProgress = 1.0f;

    @Comment("电弧距离扣减：越往外越暗、越容易断（原版 sub 的思路）。"
            + "0 = 不扣减（远场会铺满整条带）；0.55 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float arcSub = 0.12f;

    @Comment("电弧流动速度。0 = 静止（会显得像贴图）；1.6 = 默认。")
    @ConfigEntry.Gui.Tooltip
    public float arcSpeed = 1.6f;

    @Comment("电弧离开物品多远就消失（用光雾场当距离代理）。0 = 不限制（电弧会铺满剪裁框）。"
            + "0.12 = 默认，让电弧铺满整圈轮廓。")
    @ConfigEntry.Gui.Tooltip
    public float arcReach = 0.10f;

    @Comment("色调映射（ACES 近似 + sqrt）：开着才有「白热核心 + 彩色细丝」，"
            + "关掉会糊成一块饱和色斑。1 = 开（默认）。")
    @ConfigEntry.Gui.Tooltip
    public float arcTonemap = 1.0f;

    // ──────────────────────────────────────────────────────────────
    //  火焰调试视图（给"火焰看起来不对"用的，正常游玩保持 0）
    //
    //  它把火焰管线里那三张场直接画在屏幕上（灰度、不参与硬裁/包络/色调映射），
    //  于是"场里没密度"和"密度被某个 gate 抹掉了"这两种完全不同的病因一眼可分。
    //  这是被"改参数→进游戏→还是不对"循环折腾了好几轮之后加的。
    // ──────────────────────────────────────────────────────────────

    @Comment("火焰调试视图：0 = 正常合成（默认）；"
            + "1 = 显示 1/4 分辨率平流场密度（火羽到底长没长出来）；"
            + "2 = 显示可见性包络（是哪一层 gate 把火抹掉的）。")
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.CollapsibleObject
    public int flameDebugView = 0;

    // ──────────────────────────────────────────────────────────────
    //  物品栏图标（GUI 槽位）专用的一组描边微调
    //
    //  为什么需要单独一组：描边颜色是按「模型空间坐标」算的
    //  （outline_mask.fsh 的 resolveColor(modelPos.xy)，频率 18/12）。
    //  物品在手里/世界里占的屏幕面积大，那个波长看起来是柔和的宽带；
    //  但物品栏图标只有 16px，同一个波长就等于「一圈描边里要过 3~4 次黑白」，
    //  再叠上 4.5px 的环宽，就成了忽黑忽白的粗黑边 —— 反而把渐变盖住了。
    //  所以 GUI 单独用：更宽的波段 + 更慢的流动 + 抬起来的暗端 + 略细的环。
    // ──────────────────────────────────────────────────────────────

    @Comment("物品栏图标里，描边渐变的空间频率倍率。1.0 = 与手持/世界一致（一圈描边过 3~4 次黑白）；"
            + "0.18 左右 ≈ 一圈描边只走大半个周期（推荐）。")
    @ConfigEntry.Gui.Tooltip
    public float guiOutlineGradientScale = 0.18f;

    @Comment("物品栏图标里，渐变流动速度的倍率。1.0 = 与手持/世界一致；0.25 ≈ 6 秒走一轮（推荐）。")
    @ConfigEntry.Gui.Tooltip
    public float guiOutlineSpeedScale = 0.25f;

    @Comment("物品栏图标里，渐变暗端的抬升量。0 = 纯黑（会和模型自带的黑边糊成一团）；"
            + "0.35 = 最暗只到 35% 灰，两端就都能看清（推荐）。")
    @ConfigEntry.Gui.Tooltip
    public float guiOutlineMinMix = 0.35f;

    @Comment("物品栏图标里，描边宽度的额外倍率（在 outlineWidthScale 之后再乘一次）。"
            + "16px 图标上 4.5px 的环偏厚，0.75 左右更耐看。")
    @ConfigEntry.Gui.Tooltip
    public float guiOutlineWidthScale = 0.75f;

    // ──────────────────────────────────────────────────────────────
    //  天穹裁决（domerite_longsword）的单键三态右键技能
    //
    //  三个技能共用同一个冷却池（见 VerdictCooldown），所以它们是"一套操作"，
    //  这组开关也统一放在一起。每一项都可以单独关掉：
    //  关掉某个技能后它就不再触发，冷却池照常工作（剩下两个技能仍能计价）。
    //
    //  要整体关掉这套系统（比如怀疑它和别的模组冲突），把 verdictSkillsEnabled
    //  设为 false —— 那时右键会退回原版剑的行为，其余一切（描边、面板、Y 缩放）照常。
    // ──────────────────────────────────────────────────────────────

    @Comment("天穹裁决右键技能总开关。false = 右键完全退回原版剑行为。")
    @ConfigEntry.Gui.Tooltip
    public boolean verdictSkillsEnabled = true;

    @Comment("① 天穹裁决·垂直光柱：蓄力右键 ≥0.4 秒后松开，在脚下立起一根通天光柱。"
            + "落柱之后它一路扩散：半径从起始值涨到约 20 格（约 1.5 秒）、高度同时长高；"
            + "伤害分三波打在扩散的起始 / 中段 / 完成上。")
    @ConfigEntry.Gui.Tooltip
    public boolean verdictBeamEnabled = true;

    @Comment("光柱亮度倍率。1.0 = 默认；觉得太刺眼就调小，想更醒目就调大。")
    @ConfigEntry.Gui.Tooltip
    public float verdictBeamBrightness = 1.0f;

    @Comment("② 截空·凌空斩：右键短按 (<0.4 秒) 朝视线突进，路径上的敌人各吃一次裁决伤害。"
            + "整招完全没命中时只记一笔很短的冷却，且不推进连打等级。")
    @ConfigEntry.Gui.Tooltip
    public boolean verdictDashEnabled = true;

    @Comment("突进距离倍率。1.0 = 地表 12 格（随 Y 坐标最多放大到约 15.6 格）。"
            + "注意它改的是「给多少初速」，距离会随之等比变化。")
    @ConfigEntry.Gui.Tooltip
    public float verdictDashDistanceScale = 1.0f;

    @Comment("③ 裁决领域·空中剑阵：潜行 + 按住右键 0.7 秒，在脚下展开持续 10 秒的领域。"
            + "展开时边界会落下 7 道立柱剑，之后每次出剑都是从天而降的落剑。")
    @ConfigEntry.Gui.Tooltip
    public boolean verdictFieldEnabled = true;

    @Comment("裁决领域半径倍率。1.0 = 地表 6 格（随 Y 坐标最多放大到约 11 格）。")
    @ConfigEntry.Gui.Tooltip
    public float verdictFieldRadiusScale = 1.0f;

    @Comment("裁决领域的伤害强度倍率。它<b>不</b>改伤害数值（伤害恒定 25+5），"
            + "而是改出剑频率：1.0 = 每 1.2 秒一道，2.0 = 每 0.6 秒一道（上限 0.2 秒）。")
    @ConfigEntry.Gui.Tooltip
    public float verdictFieldRate = 1.0f;

    @Comment("共享冷却池的基础冷却（秒）。1.5 = 默认（正常节奏出招恒定 1.5 秒）；"
            + "在 8 秒内刻意连打会加到 3.5 / 5.5 秒（封顶）。"
            + "想让技能更频繁就调小，想更克制就调大。")
    @ConfigEntry.Gui.Tooltip
    public float verdictCooldownSeconds = 1.5f;

    @Comment("裁决层数（每次命中给自己叠的一层）的每层增益倍率。"
            + "层数只加覆盖（光柱半径 / 领域出剑速度），不加伤害。0 = 完全关掉层数机制。")
    @ConfigEntry.Gui.Tooltip
    public float verdictStackScale = 1.0f;

    @Comment("裁决领域每次出剑<b>同时锁定几个目标</b>（1 ~ 6）。"
            + "默认 3：领域半径最大能到 11 格，只锁一个会让它看起来完全没在打别人；"
            + "调高会让群怪场景的落剑数量线性增加（每把剑是一个实体），"
            + "觉得卡就调回 2。")
    @ConfigEntry.Gui.Tooltip
    public int verdictFieldMaxTargets = 3;

    // ──────────────────────────────────────────────────────────────
    //  难度系数：王庭生物的最大生命值 / 攻击伤害倍率
    //
    //  作用对象：实现 infcore IInfectedEntity 且命名空间为 hall 的生物
    //  （畸骸玩家、畸骸骷髅、畸骸末影人、溃烂纠察/追蹤者/巨岩、
    //   王庭碎骨、王庭重型轰炸、萨米使徒），玩家不受影响。
    //
    //  施加方式：实体加入世界（EntityJoinLevelEvent）时挂一个固定 UUID 的
    //  AttributeModifier，而不是改 createAttributes —— 属性表在
    //  EntityAttributeCreationEvent 里注册，全服只有一份，不能按难度烤死。
    //  因此这四个值可以随时改，改完重进世界/重载配置即生效。
    //
    //  生命值倍率会按比例保留当前血量：一只满血生物改档后仍是满血，
    //  半血生物改档后仍是半血。
    //
    //  注意：难度为「普通」时并不是 1.0 —— 本模组的基准平衡就按普通档写，
    //  普通档的系数即全局基准；想完全关掉难度加成，把四项都设成 1.0。
    // ──────────────────────────────────────────────────────────────

    @Comment("【难度】简单档：王庭生物最大生命值倍率")
    @ConfigEntry.Gui.Tooltip
    public float difficultyEasyHealthScale = 1.0f;

    @Comment("【难度】简单档：王庭生物攻击伤害倍率")
    @ConfigEntry.Gui.Tooltip
    public float difficultyEasyDamageScale = 1.0f;

    @Comment("【难度】普通档：王庭生物最大生命值倍率（本模组的平衡基准）")
    @ConfigEntry.Gui.Tooltip
    public float difficultyNormalHealthScale = 1.5f;

    @Comment("【难度】普通档：王庭生物攻击伤害倍率（本模组的平衡基准）")
    @ConfigEntry.Gui.Tooltip
    public float difficultyNormalDamageScale = 1.5f;

    @Comment("【难度】困难档：王庭生物最大生命值倍率")
    @ConfigEntry.Gui.Tooltip
    public float difficultyHardHealthScale = 4.0f;

    @Comment("【难度】困难档：王庭生物攻击伤害倍率")
    @ConfigEntry.Gui.Tooltip
    public float difficultyHardDamageScale = 4.0f;

    @Comment("【难度】无法理解档：王庭生物最大生命值倍率")
    @ConfigEntry.Gui.Tooltip
    public float difficultyIncomprehensibleHealthScale = 12.0f;

    @Comment("【难度】无法理解档：王庭生物攻击伤害倍率")
    @ConfigEntry.Gui.Tooltip
    public float difficultyIncomprehensibleDamageScale = 12.0f;

    // ──────────────────────────────────────────────────────────────
    //  绯红誓约背板（手持 crimson_vow 时出现在身后的血月誓约纹章）
    //
    //  这一组是给实机调参用的：改完存盘即生效，不需要重新编译。
    //  纹章永远正对镜头、永远落在"从镜头看过去的玩家身后"，
    //  下面三个数就是全部的调节维度。
    // ──────────────────────────────────────────────────────────────

    @Comment("背板总开关。false = 完全不出现在身后。")
    @ConfigEntry.Gui.Tooltip
    public boolean crimsonBackplateEnabled = true;

    @Comment("纹章外缘（冠冕射线尖端）的可见半径（格）。1.5 = 直径 3 格；"
            + "矩阵里的缩放 = 它 ÷ 0.86，所以请直接填**看得见**的半径。"
            + "太小会只剩一个圈，太大（>2）会盖住半个屏幕。")
    @ConfigEntry.Gui.Tooltip
    public float crimsonBackplateScale = 1.5f;

    @Comment("纹章中心离脚底的高度（格）。1.55 ≈ 眼睛高度：血月盘正好在头后，"
            + "冠冕从头顶升出去；调低会整块落到背上。")
    @ConfigEntry.Gui.Tooltip
    public float crimsonBackplateHeight = 1.55f;

    @Comment("沿**视线方向**远离镜头的距离（格）。它就是「在镜头看过去的玩家身后多远」："
            + "调到 0 会与身体糊在一起，调大则更远更小。必须 > 0 才能保证纹章永远在玩家身后。")
    @ConfigEntry.Gui.Tooltip
    public float crimsonBackplateHeadOffset = 0.65f;
}
