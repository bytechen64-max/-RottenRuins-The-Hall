package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 同化蝙蝠：B13 蝙蝠夜袭 —— 冷却 110t、距离 4~22 时瞬移到目标背后 2 格，
 * 咬击 3.5 + 吸血 60% + 恐惧 80t；另带 B1 吐息（3.5/70/14）、B10 骨爆（6/2.5）、B12 汲血（0.4）。
 *
 * <p>原工程的飞行 AI（FlyingPathNavigation）暂未移植，这里用「瞬移夜袭」保留它的核心威胁。</p>
 */
public class CorruptionBatEntity extends BaseHostEntity {

    private static final int AMBUSH_COOLDOWN = 110;
    private static final double AMBUSH_MIN_RANGE = 4.0;
    private static final double AMBUSH_MAX_RANGE = 22.0;
    private static final float AMBUSH_DAMAGE = 3.5F;

    private int ambushCooldown = 60;

    public CorruptionBatEntity(EntityType<? extends CorruptionBatEntity> entityType, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.18F);
        this.setRunSpeed(0.24F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
        this.enableSpit(3.5F, 70, 14.0);
        this.enableBoneBurst(6, 2.5F);
        this.enableLifesteal(0.4F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        // 蝙蝠不近战，只靠夜袭
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        if (ambushCooldown > 0) {
            ambushCooldown--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist < AMBUSH_MIN_RANGE || dist > AMBUSH_MAX_RANGE) return;

        performAmbush(target);
        ambushCooldown = AMBUSH_COOLDOWN;
    }

    /** 瞬移到目标背后 2 格，咬击 + 吸血 + 恐惧。 */
    private void performAmbush(LivingEntity target) {
        Vec3 behind = target.position().subtract(
                Vec3.directionFromRotation(0, target.getYRot()).scale(2.0));

        // 落点尽量可站立；不行就直接贴到目标上方
        Vec3 destination = this.level().noCollision(this, this.getBoundingBox().move(behind.subtract(this.position())))
                ? behind
                : target.position().add(0, 0.5, 0);

        CorruptionSkillFx.impact(this.level(), this.position(), 0xFF201030, 0.6F);
        this.teleportTo(destination.x, destination.y, destination.z);

        CorruptionSkillUtil.strike(this, target, AMBUSH_DAMAGE, 0.15);
        CorruptionSkillUtil.lifesteal(this, AMBUSH_DAMAGE, 0.6F);
        CorruptionSkillUtil.fear(target, 80, 0);
        CorruptionSkillFx.impact(this.level(), target.position(), 0xFF5A0A2A, 0.6F);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.BAT_HURT, 1.0F, 1.6F, 0.2F);
    }
}
