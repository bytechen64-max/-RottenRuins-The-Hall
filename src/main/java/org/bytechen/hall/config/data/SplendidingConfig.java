package org.bytechen.hall.config.data;

import org.bytechen.hall.HallMod;
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
     * </ul>
     */
    public static final int CONFIG_VERSION = 2;

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

    @Comment("冲击波空气折射强度倍率（1.0 = 默认；0 = 只保留亮边不做折射偏移）")
    @ConfigEntry.Gui.Tooltip
    public float shockwaveRefraction = 1.0f;

    @Comment("物品描边宽度全局倍率。1.0 = 各物品自己的 outlinePixelWidth（默认 2.5 像素）；"
            + "想整体加厚就调大（如 1.5、2.0），想变细就调小。改这个不用重新编译。")
    @ConfigEntry.Gui.Tooltip
    public float outlineWidthScale = 1.0f;

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
    //  绯红誓约背板（手持 crimson_vow 时出现在身后的三层光环）
    //
    //  这一组是给实机调参用的：改完存盘即生效，不需要重新编译。
    //  光环永远正对镜头、永远落在"从镜头看过去的玩家身后"，
    //  下面三个数就是全部的调节维度。
    // ──────────────────────────────────────────────────────────────

    @Comment("背板总开关。false = 完全不出现在身后。")
    @ConfigEntry.Gui.Tooltip
    public boolean crimsonBackplateEnabled = true;

    @Comment("光环的可见半径（格）。1.3 = 直径 2.6 格，比 1.8 格高的玩家宽一圈；"
            + "它是**看得见**的半径，不是矩阵里的缩放系数。")
    @ConfigEntry.Gui.Tooltip
    public float crimsonBackplateScale = 1.3f;

    @Comment("光环中心离脚底的高度（格）。1.55 ≈ 头部后方；调低会落到背上。")
    @ConfigEntry.Gui.Tooltip
    public float crimsonBackplateHeight = 1.55f;

    @Comment("沿**视线方向**远离镜头的距离（格）。它就是「在镜头看过去的玩家身后多远」："
            + "调到 0 会与身体糊在一起，调大则更远更小。必须 > 0 才能保证光环永远在玩家身后。")
    @ConfigEntry.Gui.Tooltip
    public float crimsonBackplateHeadOffset = 0.65f;
}
