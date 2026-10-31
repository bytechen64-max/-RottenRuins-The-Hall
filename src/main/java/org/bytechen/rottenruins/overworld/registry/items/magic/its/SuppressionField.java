package org.bytechen.rottenruins.overworld.registry.items.magic.its;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.bytechen.rottenruins.utils.EntityChooseUtil;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;

/**
 * <b>抑制场</b> —— 按住持续型法术：以施法者为中心铺开一个压制领域，
 * <b>每 5 tick</b> 结算一次，三个效果同时发生：
 *
 * <ol>
 *   <li><b>范围伤害</b>：半径内所有目标各挨一次法术伤害（基值 {@code baseDamage()}）；</li>
 *   <li><b>强行下压</b>：<b>不在地面上</b>的目标被强行往下压一段（飞行生物也照样被拽下来
 *       —— 向下用的是速度，跟重不重力无关）；</li>
 *   <li><b>摔落伤害</b>：按第 2 步给出去的<b>动量</b>额外结算一次摔落伤害。</li>
 * </ol>
 *
 * <h3>节奏是怎么来的</h3>
 * <p>"每 5 tick"不是自己数 tick，而是构造器最后一个参数
 * {@code keepTriggerInterval = 5} —— {@code KEEP} 模式的触发速度。框架只在该触发时
 * 回调 {@link #onKeepTick}，第一次就在进入维持后的第 1 tick，之后 1、6、11……。
 * 想改成每 10 tick 一次，只改那一个数。</p>
 *
 * <h3>半径与法术强度</h3>
 * <p>{@link #radiusFor}：{@code 5 × (1 + 强度加成/4)}，其中"强度加成"是
 * {@code rotten_ruins:spell_power}（倍率属性，默认 1.0）<b>比基准 1.0 多出来的部分</b>：</p>
 * <table border="1">
 *   <tr><th>{@code rotten_ruins:spell_power}</th><th>加成</th><th>半径</th></tr>
 *   <tr><td>1.0（默认）</td><td>0</td><td><b>5.0 格</b></td></tr>
 *   <tr><td>1.4</td><td>+0.4</td><td>5.5 格</td></tr>
 *   <tr><td>2.0</td><td>+1.0</td><td>6.25 格</td></tr>
 * </table>
 * <p>之所以要减掉 1.0：需求同时说了"初始半径 5 格"和"大约 5×(1+强度/4)"，
 * 而属性默认值就是 1.0 —— 不减的话默认半径会变成 6.25，和"初始 5 格"对不上。
 * 想要字面意义的 {@code 5×(1+spell_power/4)}，把 {@link #radiusFor} 里的
 * {@code - 1.0d} 去掉即可。</p>
 *
 * <h3>摔落伤害是怎么算的</h3>
 * <p>向下压的冲击速度是 {@value #SUPPRESS_PUSH} 格/tick，把它当动量，
 * 用自由落体 {@code v² = 2gh} 反推"等效下落高度"，再套原版的摔落规则
 * （前 {@value #FALL_IMMUNE_BLOCKS} 格免伤、每格 {@value #FALL_DAMAGE_PER_BLOCK} 点）：</p>
 * <pre>
 *   h = 1.2² / (2 × 0.08) = 9 格   →   (9 - 3) × 1 = 6 点
 * </pre>
 * <p>伤害类型用的是原版的 {@code fall}（{@code IS_FALL} 标签，羽落/防摔类效果能正确识别），
 * 但把主人挂在 {@code causingEntity} 上 —— 所以<b>击杀仍然算在施法者头上</b>
 * （掉落归属、成就、本模组的击杀回蓝都认得出）。</p>
 *
 * <h3>为什么每次都清无敌帧</h3>
 * <p>5 tick 一次 &lt; 原版 20 tick 的无敌帧，不清的话第二下之后全都会被
 * {@code LivingEntity.hurt} 当成"重复伤害"吃掉，表现为"只有第一下掉了血"。
 * 见 {@link SpellDamageUtil#prepareRepeatHit}。</p>
 */
public class SuppressionField extends CMagicBaseItem {

    // ══════════════════════════════════════════════════════════════
    // 可调数值
    // ══════════════════════════════════════════════════════════════

    /** 基础半径（格）：法术强度等于基准值（加成 0）时就是它。 */
    public static final double BASE_RADIUS = 5.0d;

    /**
     * 半径上限（格）。防呆用：{@code spell_power} 是可叠的倍率属性，
     * 没上限的话一条 {@code /attribute} 就能让场罩住整个区块。
     */
    public static final double MAX_RADIUS = 24.0d;

    /** 每次脉冲给空中目标的向下冲击速度（格/tick）—— 这就是"动量"的来源。 */
    public static final double SUPPRESS_PUSH = 1.2d;

    /**
     * 每次脉冲额外把身体直接向下压的距离（格）。
     * <p>只对<b>非玩家、非乘客</b>生效，原因见 {@link #applySuppression}。</p>
     */
    public static final double SUPPRESS_DROP = 2.0d;

    /** 原版重力加速度（格/tick²）—— 把冲量换算成"等效下落高度"要用它。 */
    private static final double GRAVITY = 0.08d;

    /** 原版摔落免伤高度：前 3 格不掉血。 */
    public static final float FALL_IMMUNE_BLOCKS = 3.0f;

    /** 每超出免伤高度 1 格造成的伤害（原版也是 1）。 */
    public static final float FALL_DAMAGE_PER_BLOCK = 1.0f;

    /**
     * 由冲击动量折算出的摔落伤害（点）。
     * <p>推导见类注释；改 {@link #SUPPRESS_PUSH} 会跟着变。</p>
     */
    public static final float MOMENTUM_FALL_DAMAGE = momentumFallDamage();

    public SuppressionField() {
        //               属性       流派        使用方式        蓝   CD   蓄力 每秒 伤害 比例 触发间隔
        super(new Properties().stacksTo(1), MagicType.VOID, UsingType.KEEP,
                80,     // 启动耗蓝
                240,    // 冷却 tick（12 秒，同名物品共享）
                0,      // 蓄力 tick —— 非 CHARGE，基类会归零
                50,     // 维持耗蓝 / 秒（1000 法力最多撑 20 秒左右）
                20,     // 每次脉冲的伤害基值（可被属性/附魔折叠）
                0,      // 最低蓄力比例 —— 非 CHARGE，无意义
                5);     // ★ 触发速度：每 5 tick 结算一次
    }

    // ══════════════════════════════════════════════════════════════
    // 半径
    // ══════════════════════════════════════════════════════════════

    /**
     * 按法术强度算场的半径：{@code 5 × (1 + 强度加成/4)}，
     * 其中"强度加成" = <b>有效法强 − 1.0</b>，有效法强 = 通用法强 + 虚空法强（相加，
     * 见 {@link MagicStats#spellPower}）。
     *
     * <p>想做成"另一个形状的成长曲线"直接覆写它 —— 它只在一处被调用。</p>
     */
    protected double radiusFor(@NotNull ServerPlayer caster, @NotNull ItemStack stack) {
        double spellPower = MagicStats.spellPower(caster, stack);
        // 减 1.0 让"默认属性 = 基础半径 5 格"成立，见类注释里的表
        double bonus = spellPower - 1.0d;
        return Mth.clamp(BASE_RADIUS * (1.0d + bonus / 4.0d), 1.0d, MAX_RADIUS);
    }

    // ══════════════════════════════════════════════════════════════
    // 起手 / 脉冲
    // ══════════════════════════════════════════════════════════════

    /**
     * 起手（按下那一刻，只调一次）：只放一声开场音效。
     *
     * <p>真正的脉冲由框架按触发速度回调 {@link #onKeepTick}，第一次在进入维持后的第 1 tick
     * —— 所以这里<b>不</b>需要自己补一次结算，否则第一下会打两次。</p>
     *
     * <p>粒子已按要求去掉，本类不做任何粒子表现。</p>
     */
    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }

    /**
     * 维持期间的每一次脉冲（每 {@code interval} tick 一次，默认 5）。
     *
     * @param interval 本次的触发速度，也就是构造器里那个 5（经属性/附魔折叠后的值）
     * @return 恒 {@code true}：只要还有蓝、玩家还按着，就继续压制
     */
    @Override
    public boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                              int heldTicks, int interval, LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return true;
        }

        double radius = radiusFor(caster, stack);
        Vec3 center = caster.getBoundingBox().getCenter();

        // 选目标走 EntityChooseUtil：它是"以任意坐标为中心的球"，
        // 判定用的是"目标碰撞箱到中心的最近距离"（大体积擦边也算进场），
        // 并且已经把主人自己、旁观者、尸体筛掉了。
        // 这里再叠一层 canAffect，留给子类加"排除自己的宠物/队友"之类的自定义条件。
        for (LivingEntity victim : EntityChooseUtil.sphereAt(level, caster, center, radius)) {
            if (!canAffect(caster, victim)) {
                continue;
            }
            pulse(level, caster, stack, victim);
        }
        return true;
    }

    /**
     * 对单个目标结算一次脉冲：先伤害，再（若在空中）下压 + 补摔落伤害。
     */
    private void pulse(@NotNull ServerLevel level, @NotNull ServerPlayer caster,
                       @NotNull ItemStack stack, @NotNull LivingEntity victim) {
        // ── ① 每 5 tick 一次的常规伤害 ──
        // 无敌帧清零：5 tick 一次 < 原版 20 tick 的无敌帧，不清的话从第二下起
        // 全都会被 LivingEntity.hurt 当成"重复伤害"吃掉（同数值只补差值 = 0 伤害）。
        // 这一步是硬要求，和粒子/音效无关。
        SpellDamageUtil.prepareRepeatHit(victim);

        // 基值交给 MagicStats 折叠（属性/附魔 provider 链）；
        // rotten_ruins:spell_power 的倍率由 MagicDamageEvent 的监听器乘，不能在这里再乘一次。
        float damage = MagicStats.spellDamage(caster, stack, victim, null, baseDamage());
        SpellDamageUtil.hurt(victim, stack, caster, damage);

        // ── ② 不在地面上 → 强行往下压，并按动量补一次摔落伤害 ──
        if (victim.isAlive() && !victim.onGround()) {
            applySuppression(level, caster, stack, victim);
        }
    }

    /**
     * 把目标强行往下压，并按动量补一次摔落伤害。
     *
     * <p>两步分开做，各有各的必要性：</p>
     * <ol>
     *   <li><b>给向下的速度</b>：这一条对<b>所有</b>实体都有效，包括 {@code noGravity}
     *       的飞行生物 —— 位移用的是 {@code deltaMovement}，跟重不重力无关。
     *       给完要置 {@code hurtMarked}，让客户端也照着动（否则只有服务端在动，
     *       表现为抖动或者被客户端拽回去）。</li>
     *   <li><b>再把身体直接下压一段</b>：<b>跳过玩家和乘客</b> ——
     *       玩家的位置是客户端权威，服务端硬改会被下一帧的位置包纠正回来；
     *       乘客则该由载具带着走，硬拽乘客会把载具上的东西扯歪。
     *       这一步走 {@code move()}，所以会老老实实被地面/方块挡住，不会把人压进地里。</li>
     * </ol>
     */
    private void applySuppression(@NotNull ServerLevel level, @NotNull ServerPlayer caster,
                                  @NotNull ItemStack stack, @NotNull LivingEntity victim) {
        // ① 向下冲击速度（动量）：不要把已有的向上速度"抹平"，只保证至少是向下这么多
        Vec3 motion = victim.getDeltaMovement();
        victim.setDeltaMovement(motion.x, Math.min(motion.y, -SUPPRESS_PUSH), motion.z);
        victim.hurtMarked = true;

        // ② 身体直接下压一段
        if (!(victim instanceof Player) && !victim.isPassenger()) {
            victim.move(MoverType.SELF, new Vec3(0.0d, -SUPPRESS_DROP, 0.0d));
        }

        // ③ 刚刚压下去的那点高度已经由下面这一发"算过账"了。
        //    清掉下落距离计数器，免得落地时原版再按同一段高度收一次摔落伤害
        //    （清完它继续自然下落的部分仍会被原版正常记账）。
        victim.resetFallDistance();

        // ④ 按动量结算摔落伤害：伤害类型是原版的 fall（羽落/防摔效果认得出），
        //    但把主人挂在 causingEntity 上，击杀仍然归他。
        //    同样先清无敌帧 —— 第 ③ 步之前刚打过的常规伤害已经把无敌帧又推上去了，
        //    不清的话这一发会被整个吃掉。
        SpellDamageUtil.prepareRepeatHit(victim);
        DamageSource source = new DamageSource(
                level.damageSources().fall().typeHolder(), caster, caster);
        SpellDamageUtil.hurt(victim, source, stack, caster, MOMENTUM_FALL_DAMAGE);
    }

    /**
     * 场里哪些目标会被压制。
     *
     * <p>默认：<b>除了主人自己</b>以外所有活着的、非旁观的生物 ——
     * 包括其它玩家（这是个范围压制场，友伤是设计的一部分）。
     * 想排除自己的宠物、队友或者某类生物就覆写它。</p>
     */
    protected boolean canAffect(@NotNull ServerPlayer caster, @NotNull LivingEntity target) {
        return !caster.getUUID().equals(target.getUUID())
                && target.isAlive()
                && !target.isSpectator();
    }

    /** 冲击速度 → "等效下落高度" → 原版摔落伤害。见类注释里的推导。 */
    private static float momentumFallDamage() {
        double equivalentHeight = SUPPRESS_PUSH * SUPPRESS_PUSH / (2.0d * GRAVITY);
        return (float) Math.max(0.0d, equivalentHeight - FALL_IMMUNE_BLOCKS)
                * FALL_DAMAGE_PER_BLOCK;
    }
}
