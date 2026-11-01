package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.degenerate;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseDegenerateEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionDart;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 腐化牛。
 *
 * <p>两个互补技能，主题都是"以血肉为代价换取爆发"：</p>
 * <ul>
 *   <li><b>C4 屠宰者暴冲</b> —— 低于半血进入血怒：增伤、提速、免疫击退；</li>
 *   <li><b>C5 血肉偿还</b> —— 主动割舍当前血量的四成，射出等比强化的血爆弹。</li>
 * </ul>
 */
public class DegenerateCowEntity extends BaseDegenerateEntity {

    // =========================================================================
    // C4 屠宰者暴冲
    // =========================================================================
    private static final float RAGE_THRESHOLD = 0.5F;
    private static final int RAGE_DURATION = 200;
    private static final int RAGE_SPEED_DURATION = 200;

    private boolean raging = false;
    private int rageCooldown = 0;

    // =========================================================================
    // C5 血肉偿还
    // =========================================================================
    private static final int SACRIFICE_COOLDOWN = 150;
    private static final float SACRIFICE_HEALTH_COST = 0.4F;
    private static final float SACRIFICE_DAMAGE_RATIO = 0.55F;
    private static final float SACRIFICE_MIN_DAMAGE = 8.0F;
    private static final float SACRIFICE_MAX_DAMAGE = 45.0F;
    private static final double SACRIFICE_RANGE = 20.0;

    private int sacrificeCooldown = 80;

    public DegenerateCowEntity(EntityType<? extends DegenerateCowEntity> entityType, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    // =========================================================================
    // 技能主循环
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        if (rageCooldown > 0) rageCooldown--;
        if (sacrificeCooldown > 0) sacrificeCooldown--;

        tickRage();
        tickSacrifice();
    }

    /** [C4] 屠宰者暴冲：跌破半血进入血怒，血量回升则退出。 */
    private void tickRage() {
        boolean shouldRage = CorruptionSkillUtil.healthRatio(this) <= RAGE_THRESHOLD;

        if (shouldRage == raging) return;
        if (shouldRage && rageCooldown > 0) return;

        raging = shouldRage;

        if (raging) {
            CorruptionSkillUtil.applyEffect(this, MobEffects.DAMAGE_BOOST, RAGE_DURATION, 1);
            CorruptionSkillUtil.applyEffect(this, MobEffects.MOVEMENT_SPEED, RAGE_SPEED_DURATION, 1);
            // 免疫击退：血怒状态下无法被推离正面
            var knockbackRes = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (knockbackRes != null) knockbackRes.setBaseValue(1.0);

            CorruptionSkillUtil.burst(this, 2.2, 24, 0.4);
            CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 2.5,
                    CorruptionSkillUtil.CORRUPTION_RED, 24);
            CorruptionSkillUtil.playSoundPitched(this, SoundEvents.RAVAGER_ROAR, 1.3F, 0.8F, 0.1F);
        } else {
            var knockbackRes = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (knockbackRes != null) knockbackRes.setBaseValue(0.0);
        }
    }

    /**
     * [C5] 血肉偿还：割舍四成当前血量，射出等比血爆弹。
     * <p>代价先付、收益后到，玩家能通过"打断施法"或"拉开距离"规避。</p>
     */
    private void tickSacrifice() {
        if (sacrificeCooldown > 0) return;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist > SACRIFICE_RANGE) return;

        // 血量低于 25% 时不再献祭，否则等于自杀
        float health = this.getHealth();
        if (health <= this.getMaxHealth() * 0.25F) return;

        float cost = health * SACRIFICE_HEALTH_COST;
        float damage = Math.max(SACRIFICE_MIN_DAMAGE,
                Math.min(SACRIFICE_MAX_DAMAGE, cost * SACRIFICE_DAMAGE_RATIO));

        // 先付代价
        this.setHealth(Math.max(1.0F, health - cost));
        CorruptionSkillFx.bloodMist(this.level(), this.position(), 20);
        CorruptionSkillFx.pillar(this.level(), this.position(), 1.6,
                CorruptionSkillUtil.CORRUPTION_RED, 14);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.COW_HURT, 1.4F, 0.7F, 0.1F);

        // 再射出血爆弹
        CorruptionDart dart = CorruptionDart
                .shoot(this.level(), this, target, 1.0F, CorruptionDart.RED, 0.5F, false)
                .setDamage(damage)
                .setMaxLifetime(60)
                .withPool(2.0F, 60, damage * 0.08F);
        dart.setHitEffect(CorruptionEffects.BLEEDING.get(), 140, 1);
        this.level().addFreshEntity(dart);

        sacrificeCooldown = SACRIFICE_COOLDOWN;
    }

    /** 血怒状态下不被推动。 */
    @Override
    public void push(Entity entity) {
        if (this.raging) return;
        super.push(entity);
    }
}
