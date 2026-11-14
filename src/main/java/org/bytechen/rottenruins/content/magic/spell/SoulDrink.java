package org.bytechen.rottenruins.content.magic.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.gameplay.magic.spell.MagicCastContext;
import org.bytechen.rottenruins.core.magic.model.MagicType;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.core.magic.model.SpellDefinition;
import org.bytechen.rottenruins.utils.EntityChooseUtil;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>灵魂吸引</b> —— 按住持续型的死灵法术（{@link UsingType#KEEP}）。
 *
 * <ul>
 *   <li><b>按住期间</b>：每个触发（{@value #KEEP_INTERVAL} tick）吸一次半径
 *       {@value #RADIUS} 格内的所有目标 —— 每个目标吃 {@value #PULSE_DAMAGE} 点法术伤害，
 *       同时施法者按命中个数回血，并累积 {@code count}；</li>
 *   <li><b>松手时</b>：按 {@code count} 甩出一颗凋零骷髅头，
 *       伤害 = {@value #SKULL_BASE_DAMAGE} + {@code count × }{@value #SKULL_PER_COUNT}。</li>
 * </ul>
 *
 * <p>两段伤害都经过 {@link SpellDamageUtil} → {@code MagicDamageEvent}，
 * 因此<b>法术强度与法杖等级加成都会自动生效</b>（见 {@code MagicAttributeHandlers}）。</p>
 *
 * <p>骷髅头用原版 {@link WitherSkull}（现成的模型与音效），它自带的 8 点伤害会被
 * {@code SoulDrinkSkullHandler} 换成这里算好的数值。</p>
 *
 * <p>{@code count} 存在法术物品的 NBT 里而不是类字段：法术物品是<b>注册表单例</b>，
 * 一个 {@code int} 字段会被所有玩家共享。</p>
 */
public class SoulDrink extends CMagicBaseItem {

    /** 每次触发对每个目标的伤害（点）。 */
    public static final float PULSE_DAMAGE = 10.0f;

    /** 作用半径（格）。 */
    public static final double RADIUS = 10.0d;

    /** 骷髅头基础伤害（点）。 */
    public static final float SKULL_BASE_DAMAGE = 10.0f;

    /** 每一点 count 给骷髅头加的伤害。 */
    public static final float SKULL_PER_COUNT = 0.5f;

    /** 每次命中吸取的最大生命值比例（0.01 = 1%）。 */
    public static final float HEAL_PERCENT_PER_HIT = 0.01f;

    /** 触发间隔（tick）。 */
    public static final int KEEP_INTERVAL = 10;

    /** 骷髅头标记：命中时被 {@code SoulDrinkSkullHandler} 认出来。 */
    public static final String SKULL_TAG = "RottenRuinsSoulDrink";

    /** 骷髅头携带的最终伤害（已含法杖等级与法强）。 */
    public static final String SKULL_DAMAGE_TAG = "SoulDrinkDamage";

    /** 法术物品 NBT 里的积蓄计数。 */
    public static final String COUNT_TAG = "SoulDrinkCount";

    public SoulDrink() {
        super(defaultProperties(), SpellDefinition.builder(UsingType.KEEP, MagicType.DEATH)
                .manaCost(100).cooldownTicks(400).keepCostPerSecond(20)
                .damage(0f).minChargeRatio(0f).keepTriggerInterval(KEEP_INTERVAL).build());
    }

    @Override
    public boolean cast(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                        @NotNull MagicCastContext ctx) {
        stack.getOrCreateTag().putInt(COUNT_TAG, 0);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.9f, 0.7f);
        return true;
    }

    @Override
    public boolean onKeepTick(@NotNull ServerPlayer caster, @NotNull ItemStack stack,
                              int heldTicks, int interval, @Nullable LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return true;
        }
        Vec3 center = caster.getBoundingBox().getCenter();

        int hits = 0;
        for (LivingEntity victim : EntityChooseUtil.sphereAt(level, caster, center, RADIUS)) {
            if (victim == caster || !victim.isAlive() || victim.isSpectator()) {
                continue;
            }
            // 触发间隔小于原版 20 tick 无敌帧，不清就只剩第一下
            SpellDamageUtil.prepareRepeatHit(victim);
            SpellDamageUtil.hurt(victim, SpellDamageUtil.spellSourceOf(victim, caster),
                    stack, caster, PULSE_DAMAGE);
            hits++;
        }

        if (hits > 0) {
            // 吸取：每个命中回一点血，并把 count 继续攒起来
            caster.heal(caster.getMaxHealth() * HEAL_PERCENT_PER_HIT * hits);
            int count = stack.getOrCreateTag().getInt(COUNT_TAG) + hits;
            stack.getOrCreateTag().putInt(COUNT_TAG, count);

            level.sendParticles(ParticleTypes.SOUL,
                    center.x, center.y + 0.4d, center.z, 4 + hits,
                    RADIUS * 0.35d, 0.6d, RADIUS * 0.35d, 0.02d);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.x, center.y + 0.2d, center.z, 2, 0.6d, 0.2d, 0.6d, 0.01d);
        if (heldTicks % 20 < interval) {
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.SOUL_SOIL_STEP, SoundSource.PLAYERS, 0.6f, 0.6f);
        }
        return true;
    }

    /** 松手：按积蓄的 count 甩出凋零骷髅头。 */
    @Override
    public void onKeepStop(@NotNull ServerPlayer caster, @NotNull ItemStack stack, int heldTicks) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        int count = stack.hasTag() ? stack.getTag().getInt(COUNT_TAG) : 0;
        if (stack.hasTag()) {
            stack.getTag().remove(COUNT_TAG);
        }
        if (count <= 0) {
            return;
        }

        // 伤害 = 基础 + count×0.5 → 加法杖等级加成 → 乘法术强度
        float base = SKULL_BASE_DAMAGE + count * SKULL_PER_COUNT;
        base += MagicStats.staffGradeBonus(caster, stack);
        float damage = base * (float) Math.max(0.0d, MagicStats.spellPower(caster, stack));

        Vec3 look = caster.getViewVector(1.0f);
        WitherSkull skull = new WitherSkull(level, caster, look.x, look.y, look.z);
        skull.setPos(caster.getX() + look.x * 0.6d, caster.getEyeY() - 0.15d,
                caster.getZ() + look.z * 0.6d);
        skull.getPersistentData().putBoolean(SKULL_TAG, true);
        skull.getPersistentData().putFloat(SKULL_DAMAGE_TAG, damage);
        level.addFreshEntity(skull);

        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /** 自身周围的场，不需要锁定目标。 */
    @Override
    public boolean needsTarget() {
        return false;
    }
}
