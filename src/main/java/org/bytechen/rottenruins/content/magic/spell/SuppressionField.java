package org.bytechen.rottenruins.content.magic.spell;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.gameplay.magic.spell.MagicCastContext;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.utils.EntityChooseUtil;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * <b>抑制场</b> —— 按住持续型法术：以施法者为中心铺开一个压制领域，
 * <b>每 10 tick</b> 结算一次，三个效果同时发生：
 *
 * <ol>
 *   <li><b>范围伤害</b>：半径内所有目标各挨一次法术伤害（基值 {@code baseDamage()}）；</li>
 *   <li><b>强行下压</b>：<b>不在地面上</b>的目标被强行往下压一段（飞行生物也照样被拽下来
 *       —— 向下用的是速度，跟重不重力无关）；</li>
 *   <li><b>摔落伤害</b>：按第 2 步给出去的<b>动量</b>额外结算一次摔落伤害。</li>
 * </ol>
 *
 * <h3>节奏是怎么来的</h3>
 * <p>脉冲间隔由构造器的 {@code keepTriggerInterval = 10} 决定，
 * 框架在维持期间回调 {@link #onKeepTick}。</p>
 *
 * <h3>半径与法术强度</h3>
 * <p>{@link #radiusOf}：{@code 10 × (1 + 强度加成/4)}，其中"强度加成"是
 * {@code rotten_ruins:spell_power}（倍率属性，默认 1.0）<b>比基准 1.0 多出来的部分</b>：</p>
 * <table border="1">
 *   <tr><th>{@code rotten_ruins:spell_power}</th><th>加成</th><th>半径</th></tr>
 *   <tr><td>1.0（默认）</td><td>0</td><td><b>10.0 格</b></td></tr>
 *   <tr><td>1.4</td><td>+0.4</td><td>11.0 格</td></tr>
 *   <tr><td>2.0</td><td>+1.0</td><td>12.5 格</td></tr>
 * </table>
 * <p>之所以要减掉 1.0：属性默认值就是 1.0，不减的话默认半径会和
 * {@link #BASE_RADIUS} 对不上。想要字面意义的 {@code 10×(1+spell_power/4)}，
 * 把 {@link #radiusOf} 里的 {@code - 1.0d} 去掉即可。</p>
 *
 * <h3>视觉</h3>
 * <p>场本身没有实体：客户端 {@code SuppressionFieldRenderer} 用<b>和寂寒白日三棱锥同一套</b>
 * 的画法（只画棱、辉光 + 芯两层、加法混合），把它画成一个<b>包住整个场的正方体</b>，
 * 半边长就是 {@link #radiusOf} 算出来的半径，所以尺寸永远和实际判定一致。</p>
 *
 * <h3>摔落伤害是怎么算的</h3>
 * <p>向下压的冲击速度是 {@value #SUPPRESS_PUSH} 格/tick，把它当动量，
 * 用自由落体 {@code v² = 2gh} 反推"等效下落高度"，再套原版的摔落规则
 * （前 {@value #FALL_IMMUNE_BLOCKS} 格免伤、每格 {@value #FALL_DAMAGE_PER_BLOCK} 点）：</p>
 * <pre>
 *   h = 0.8² / (2 × 0.08) = 4 格   →   (4 - 3) × 1 = 1 点
 * </pre>
 * <p>伤害类型用的是原版的 {@code fall}（{@code IS_FALL} 标签，羽落/防摔类效果能正确识别），
 * 但把主人挂在 {@code causingEntity} 上 —— 所以<b>击杀仍然算在施法者头上</b>
 * （掉落归属、成就、本模组的击杀回蓝都认得出）。</p>
 *
 * <h3>为什么每次都清无敌帧</h3>
 * <p>10 tick 一次 &lt; 原版 20 tick 的无敌帧，不清的话第二下之后全都会被
 * {@code LivingEntity.hurt} 当成"重复伤害"吃掉，表现为"只有第一下掉了血"。
 * 见 {@link SpellDamageUtil#prepareRepeatHit}。</p>
 */
public class SuppressionField extends CMagicBaseItem {

    private static final DustParticleOptions FIELD_DUST =
            new DustParticleOptions(new Vector3f(0.36f, 0.18f, 0.65f), 1.1f);

    // ══════════════════════════════════════════════════════════════
    // 可调数值
    // ══════════════════════════════════════════════════════════════

    /** 基础半径（格）：法术强度等于基准值（加成 0）时就是它。 */
    public static final double BASE_RADIUS = 10.0d;

    /**
     * 半径上限（格）。防呆用：{@code spell_power} 是可叠的倍率属性，
     * 没上限的话一条 {@code /attribute} 就能让场罩住整个区块。
     *
     * <p>跟着 {@link #BASE_RADIUS} 一起放大：上限的作用是"防呆"，
     * 相对成长空间要和基础半径保持同一个比例。</p>
     */
    public static final double MAX_RADIUS = 48.0d;

    /** 每次脉冲给空中目标的向下冲击速度（格/tick）—— 这就是"动量"的来源。 */
    public static final double SUPPRESS_PUSH = 0.8d;

    /**
     * 每次脉冲额外把身体直接向下压的距离（格）。
     * <p>只对<b>非玩家、非乘客</b>生效，原因见 {@link #applySuppression}。</p>
     */
    public static final double SUPPRESS_DROP = 1.0d;

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
        super(defaultProperties(), SpellDefinition.builder(UsingType.KEEP, MagicType.VOID)
                .manaCost(60)
                .cooldownTicks(200)
                .keepCostPerSecond(35)
                .damage(6.0f)
                .minChargeRatio(0.0f)
                .keepTriggerInterval(10)
                .build());
    }

    // ══════════════════════════════════════════════════════════════
    // 半径
    // ══════════════════════════════════════════════════════════════

    /**
     * 按法术强度算场的半径：{@code 10 × (1 + 强度加成/4)}，
     * 其中"强度加成" = <b>有效法强 − 1.0</b>，有效法强 = 通用法强 + 虚空法强（相加，
     * 见 {@link MagicStats#spellPower}）。
     *
     * <p><b>双端可用</b>：服务端用它算判定，客户端渲染器用它算那个正方体的大小，
     * 两边必须是同一个公式，所以真正实现在这里、由 {@link #radiusFor} 转发。</p>
     *
     * <table border="1">
     *   <tr><th>{@code spell_power}</th><th>加成</th><th>半径</th></tr>
     *   <tr><td>1.0（默认）</td><td>0</td><td><b>10.0 格</b></td></tr>
     *   <tr><td>1.4</td><td>+0.4</td><td>11.0 格</td></tr>
     *   <tr><td>2.0</td><td>+1.0</td><td>12.5 格</td></tr>
     * </table>
     *
     * <p>减 1.0 是为了让"属性默认值（1.0）= 基础半径"成立。</p>
     */
    public static double radiusOf(@NotNull Player caster, @NotNull ItemStack stack) {
        // 0 法强 = 基础半径，法强直接加在半径上（见上面的表）
        double bonus = MagicStats.spellPower(caster, stack);
        return Mth.clamp(BASE_RADIUS * (1.0d + bonus / 4.0d), 1.0d, MAX_RADIUS);
    }

    /**
     * 实例入口，转发给 {@link #radiusOf}。
     *
     * <p>想做成"另一个形状的成长曲线"直接覆写它 —— 它只在一处被调用。
     * 注意客户端渲染器走的是静态 {@link #radiusOf}，覆写这里要自己保证两边一致
     * （或者干脆连 {@code radiusOf} 一起改）。</p>
     */
    protected double radiusFor(@NotNull ServerPlayer caster, @NotNull ItemStack stack) {
        return radiusOf(caster, stack);
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
     * <p>领域边界在维持脉冲时绘制，开场只播放音效。</p>
     */
    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }

    /**
     * 维持期间的每一次脉冲（每 {@code interval} tick 一次，默认 10）。
     *
     * @param interval 本次的触发速度，也就是构造器里那个 10（经属性/附魔折叠后的值）
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

        // A sparse perimeter makes the otherwise invisible area readable to everyone nearby.
        for (int i = 0; i < 12; i++) {
            double angle = i * (Math.PI * 2.0d / 12.0d) + heldTicks * 0.035d;
            level.sendParticles(FIELD_DUST,
                    caster.getX() + Math.cos(angle) * radius, caster.getY() + 0.12d,
                    caster.getZ() + Math.sin(angle) * radius,
                    1, 0.04d, 0.04d, 0.04d, 0.0d);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                center.x, center.y, center.z, 5, radius * 0.45d, 0.5d,
                radius * 0.45d, 0.01d);
        if (heldTicks % 40 < interval) {
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.35f, 0.55f);
        }

        // Use the upstream sphere query, then apply this spell's own target filter.
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
        // ── ① 每 10 tick 一次的常规伤害 ──
        // 无敌帧清零：10 tick 一次 < 原版 20 tick 的无敌帧，不清的话从第二下起
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
