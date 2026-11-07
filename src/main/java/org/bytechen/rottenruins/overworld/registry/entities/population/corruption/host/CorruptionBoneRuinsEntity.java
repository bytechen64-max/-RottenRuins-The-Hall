package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * 同化骨堆：站桩自爆单位。每 20t 扫描 8 格内是否有玩家；触发后 60t 喷发
 * （每 tick 棕色 dust，每 20t 给区域内目标上流血），结束时爆炸 5 点伤害并 discard。
 */
public class CorruptionBoneRuinsEntity extends BaseHostEntity {

    private static final double TRIGGER_RADIUS = 8.0;
    private static final int ERUPT_TICKS = 60;
    private static final float EXPLOSION_DAMAGE = 5.0F;

    private int scanTimer = 20;
    private int eruptTimer = 0;
    private int bleedTimer = 0;

    public CorruptionBoneRuinsEntity(EntityType<? extends CorruptionBoneRuinsEntity> entityType, Level level,
                                     Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.0F);
        this.setRunSpeed(0.0F);
        // 站桩单位不做假死/爆体演出
        this.setFakeDeathEnabled(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        // 骨堆不主动近战，只靠自爆
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        if (eruptTimer > 0) {
            eruptTick();
            return;
        }

        if (scanTimer > 0) {
            scanTimer--;
            return;
        }
        scanTimer = 20;

        AABB area = this.getBoundingBox().inflate(TRIGGER_RADIUS, 5.0, TRIGGER_RADIUS);
        boolean playerNear = this.level().getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class, area, p -> p.isAlive() && !p.isCreative())
                .stream().findAny().isPresent();
        if (playerNear) {
            eruptTimer = ERUPT_TICKS;
            bleedTimer = 20;
            CorruptionSkillUtil.burst(this, 1.8, 20, 0.3);
            CorruptionSkillUtil.playSoundPitched(this, SoundEvents.SKELETON_STEP, 1.2F, 0.7F, 0.1F);
        }
    }

    private void eruptTick() {
        eruptTimer--;

        // 喷发粒子
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.45F, 0.3F, 0.2F), 1.4F);
        if (this.level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 6; i++) {
                serverLevel.sendParticles(dust,
                        this.getX() + (this.random.nextDouble() - 0.5) * 1.6,
                        this.getY() + this.random.nextDouble() * 1.8,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 1.6,
                        1, 0, 0.05, 0, 0.0);
            }
        }

        if (bleedTimer > 0) {
            bleedTimer--;
        } else {
            bleedTimer = 20;
            for (LivingEntity target : CorruptionSkillUtil.findTargets(this, 4.0)) {
                CorruptionSkillUtil.bleed(target, 100, 1);
            }
        }

        if (eruptTimer <= 0) {
            explode();
        }
    }

    private void explode() {
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 4.0,
                CorruptionSkillUtil.CORRUPTION_RED, 28);
        CorruptionSkillFx.bloodMist(this.level(), this.position(), 20);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.8F, 0.1F);

        for (LivingEntity target : CorruptionSkillUtil.findTargets(this, 4.0)) {
            target.hurt(this.damageSources().explosion(this, this), EXPLOSION_DAMAGE);
        }
        this.discard();
    }

    /** 站桩单位不移动。 */
    @Override
    public void push(net.minecraft.world.entity.Entity entity) {
    }
}
