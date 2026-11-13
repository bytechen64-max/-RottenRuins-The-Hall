package org.bytechen.rottenruins.overworld.registry.items.magic.its;

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
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.CMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicCastContext;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicType;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.bytechen.rottenruins.utils.EntityChooseUtil;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * <b>抑制场</b>：按住持续型法术，每 10 tick 结算一次 —— 范围伤害 + 把空中目标强行下压（用速度，飞行生物也拽得下来）
 * + 按冲击动量补一次摔落伤害。半径 = {@code 10 × (1 + 加成/4)}，加成 = {@code spell_power} − 1.0（默认 1.0 → 10 格）；
 * 摔落伤害 h = v²/(2g) = 0.8²/0.16 = 4 格 → (4 − 3) × 1 = 1 点，类型用原版 {@code fall}（羽落可识别）但主人挂在
 * {@code causingEntity} 上，击杀仍归施法者。每次结算前必须清无敌帧（10 tick &lt; 原版 20 tick，否则第二下起被吃掉）。
 */
public class SuppressionField extends CMagicBaseItem {

    private static final DustParticleOptions FIELD_DUST =
            new DustParticleOptions(new Vector3f(0.36f, 0.18f, 0.65f), 1.1f);

    public static final double BASE_RADIUS = 10.0d;

    public static final double MAX_RADIUS = 48.0d;

    public static final double SUPPRESS_PUSH = 0.8d;

    public static final double SUPPRESS_DROP = 1.0d;

    /** 原版重力加速度（格/tick²）—— 把冲量换算成"等效下落高度"要用它。 */
    private static final double GRAVITY = 0.08d;

    public static final float FALL_IMMUNE_BLOCKS = 3.0f;

    public static final float FALL_DAMAGE_PER_BLOCK = 1.0f;

    public static final float MOMENTUM_FALL_DAMAGE = momentumFallDamage();

    public SuppressionField() {
        //               属性       流派        使用方式        蓝   CD   蓄力 每秒 伤害 比例 触发间隔
        super(new Properties().stacksTo(1), MagicType.VOID, UsingType.KEEP,
                60,     // 启动耗蓝
                200,    // 冷却 tick（10 秒，同名物品共享）
                0,      // 蓄力 tick —— 非 CHARGE，基类会归零
                35,     // 维持耗蓝 / 秒
                6,      // 每次脉冲的伤害基值（可被属性/附魔折叠）
                0,      // 最低蓄力比例 —— 非 CHARGE，无意义
                10);    // 每 10 tick 结算一次
    }

    /** 按法术强度算半径；服务端判定与客户端渲染必须同一公式，所以实现在这里、由 {@link #radiusFor} 转发。 */
    public static double radiusOf(@NotNull Player caster, @NotNull ItemStack stack) {
        double spellPower = MagicStats.spellPower(caster, stack);
        double bonus = spellPower - 1.0d;
        return Mth.clamp(BASE_RADIUS * (1.0d + bonus / 4.0d), 1.0d, MAX_RADIUS);
    }

    /** 实例入口；覆写时要自己保证与静态 {@link #radiusOf} 一致（渲染器走的是静态那条）。 */
    protected double radiusFor(@NotNull ServerPlayer caster, @NotNull ItemStack stack) {
        return radiusOf(caster, stack);
    }

    /** 起手只放开场音效；脉冲由框架回调 {@link #onKeepTick}，此处补结算会让第一下打两次。 */
    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }

    /** 维持期间的每次脉冲（默认每 10 tick）：恒返回 {@code true}，只要还有蓝、玩家还按着就继续压制。 */
    @Override
    public boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                              int heldTicks, int interval, LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return true;
        }

        double radius = radiusFor(caster, stack);
        Vec3 center = caster.getBoundingBox().getCenter();

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

        for (LivingEntity victim : EntityChooseUtil.sphereAt(level, caster, center, radius)) {
            if (!canAffect(caster, victim)) {
                continue;
            }
            pulse(level, caster, stack, victim);
        }
        return true;
    }

    /** 对单个目标结算一次脉冲：先伤害，再（若在空中）下压 + 补摔落伤害。 */
    private void pulse(@NotNull ServerLevel level, @NotNull ServerPlayer caster,
                       @NotNull ItemStack stack, @NotNull LivingEntity victim) {
        // ① 常规伤害。无敌帧清零是硬要求：10 tick 一次 < 原版 20 tick 的无敌帧，不清的话从第二下起
        // 全被 LivingEntity.hurt 当成"重复伤害"（同数值只补差值 = 0）。
        SpellDamageUtil.prepareRepeatHit(victim);

        // 基值交给 MagicStats 折叠；spell_power 倍率由 MagicDamageEvent 监听器乘，这里不能再乘一次。
        float damage = MagicStats.spellDamage(caster, stack, victim, null, baseDamage());
        SpellDamageUtil.hurt(victim, stack, caster, damage);

        if (victim.isAlive() && !victim.onGround()) {
            applySuppression(level, caster, stack, victim);
        }
    }

    /** 下压两步：先给向下速度（对所有实体有效，含 noGravity 飞行生物；并置 hurtMarked 让客户端同步），
     *  再直接下压一段身体（走 move() 会被方块挡住；跳过玩家与乘客 —— 玩家位置客户端权威）。 */
    private void applySuppression(@NotNull ServerLevel level, @NotNull ServerPlayer caster,
                                  @NotNull ItemStack stack, @NotNull LivingEntity victim) {
        // ① 向下冲击速度（动量）：不要把已有的向上速度"抹平"，只保证至少是向下这么多
        Vec3 motion = victim.getDeltaMovement();
        victim.setDeltaMovement(motion.x, Math.min(motion.y, -SUPPRESS_PUSH), motion.z);
        victim.hurtMarked = true;

        if (!(victim instanceof Player) && !victim.isPassenger()) {
            victim.move(MoverType.SELF, new Vec3(0.0d, -SUPPRESS_DROP, 0.0d));
        }

        // ③ 这段高度已由下面这一发"算过账"，清掉下落计数器，免得落地时原版按同一段高度再收一次摔落伤害。
        victim.resetFallDistance();

        // ④ 按动量结算摔落伤害：类型是原版 fall（羽落认得出），主人挂在 causingEntity 上，击杀仍归施法者；
        // 同样先清无敌帧（③ 之前那一发又把无敌帧推上去了，不清就会被整个吃掉）。
        SpellDamageUtil.prepareRepeatHit(victim);
        DamageSource source = new DamageSource(
                level.damageSources().fall().typeHolder(), caster, caster);
        SpellDamageUtil.hurt(victim, source, stack, caster, MOMENTUM_FALL_DAMAGE);
    }

    /** 场内受影响的目标：除主人外所有活着的非旁观生物（含其它玩家 —— 友伤是设计的一部分）。 */
    protected boolean canAffect(@NotNull ServerPlayer caster, @NotNull LivingEntity target) {
        return !caster.getUUID().equals(target.getUUID())
                && target.isAlive()
                && !target.isSpectator();
    }

    /** 冲击速度 → "等效下落高度" → 原版摔落伤害；推导见类注释。 */
    private static float momentumFallDamage() {
        double equivalentHeight = SUPPRESS_PUSH * SUPPRESS_PUSH / (2.0d * GRAVITY);
        return (float) Math.max(0.0d, equivalentHeight - FALL_IMMUNE_BLOCKS)
                * FALL_DAMAGE_PER_BLOCK;
    }
}
