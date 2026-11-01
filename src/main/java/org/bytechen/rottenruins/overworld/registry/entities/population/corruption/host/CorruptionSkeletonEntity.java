package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionDart;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.PoisonArrowProjectile;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 同化骷髅：B6 腐化箭雨 + B7 射速强化 + 毒箭远程攻击（RangedAttackGoal）。
 * 血量 ≤50% 时攻击速度 +60%（UUID 防叠加）；死亡 35% 生成骨堆由后续机制补（TODO）。
 */
public class CorruptionSkeletonEntity extends BaseHostEntity implements RangedAttackMob {

    private static final UUID ATTACK_SPEED_BOOST_UUID = UUID.fromString("6a0a9d4e-3f0c-4a0e-9a1f-2b6f6d3c8a11");

    private static final int RAIN_COOLDOWN = 160;
    private static final double RAIN_MIN_RANGE = 6.0;
    private static final double RAIN_MAX_RANGE = 24.0;
    private static final int RAIN_COUNT = 5;
    private static final float RAIN_DAMAGE = 4.0F;

    private int rainCooldown = 80;
    private boolean speedBoosted = false;

    public CorruptionSkeletonEntity(EntityType<? extends CorruptionSkeletonEntity> entityType, Level level,
                                    Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.14F);
        this.setRunSpeed(0.18F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.0D, 40, 15.0F));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        tickRain();
        tickSpeedBoost();
    }

    /** 毒箭远程攻击（RangedAttackGoal 调用）。 */
    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        PoisonArrowProjectile arrow = new PoisonArrowProjectile(
                CorruptionEntityTypes.POISON_ARROW.get(), this, this.level());
        double sx = this.getX();
        double sy = this.getY() + this.getEyeHeight() - 0.1D;
        double sz = this.getZ();
        arrow.setPos(sx, sy, sz);

        double dx = target.getX() - sx;
        double dy = target.getEyeY() - sy;
        double dz = target.getZ() - sz;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.15D, dz, 1.6F, 1.0F);
        this.level().addFreshEntity(arrow);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    /** B6 腐化箭雨：先落点预警，再从上方连发 5 枚腐蚀弹。 */
    private void tickRain() {
        if (rainCooldown > 0) {
            rainCooldown--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < RAIN_MIN_RANGE || dist > RAIN_MAX_RANGE) return;

        Vec3 landing = CorruptionSkillUtil.telegraphGround(this.level(), target, 3.5);
        if (landing == null) return;

        CorruptionSkillFx.pillar(this.level(), landing, 2.5, CorruptionSkillUtil.CORRUPTION_RED, 16);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.SKELETON_AMBIENT, 1.0F, 0.7F, 0.1F);

        for (int i = 0; i < RAIN_COUNT; i++) {
            CorruptionDart dart = CorruptionDart
                    .shoot(this.level(), this, target, 1.1F, CorruptionDart.RED, 0.5F, true)
                    .setDamage(RAIN_DAMAGE)
                    .setMaxLifetime(60)
                    .withPool(2.2F, 80, 3.0F);
            dart.setHitEffect(CorruptionEffects.CORRUPTION.get(), 120, 0);
            double spread = (i - RAIN_COUNT / 2) * 0.55;
            dart.setPos(landing.x + spread, landing.y + 8, landing.z + spread);
            this.level().addFreshEntity(dart);
        }

        rainCooldown = RAIN_COOLDOWN;
    }

    /** B7 射速强化：血量 ≤ 50% 时攻击速度 +60%（只加一次）。 */
    private void tickSpeedBoost() {
        if (speedBoosted) return;
        if (this.getHealth() > this.getMaxHealth() * 0.5F) return;

        AttributeInstance attackSpeed = this.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null && attackSpeed.getModifier(ATTACK_SPEED_BOOST_UUID) == null) {
            attackSpeed.addTransientModifier(new AttributeModifier(
                    ATTACK_SPEED_BOOST_UUID, "corruption_skeleton_frenzy", 0.6D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        speedBoosted = true;
        CorruptionSkillFx.pillar(this.level(), this.position(), 1.6,
                CorruptionSkillUtil.CORRUPTION_RED, 12);
    }
}
