package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEntityTypes;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 同化鸡：B4 嘶鸣分裂 —— 受击 25% 且冷却 60t、同屏 ≤8 时，生成 2 只小体
 * （小体不再分裂），并对周围造成击退。近战 2 点。
 */
public class CorruptionChickenEntity extends BaseHostEntity {

    private static final int SPLIT_COOLDOWN = 60;
    private static final int SPLIT_CAP = 8;

    private int splitCooldown = 0;
    private boolean splinter = false;

    public CorruptionChickenEntity(EntityType<? extends CorruptionChickenEntity> entityType, Level level,
                                   Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.21F);
        this.setRunSpeed(0.21F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    public boolean isSplinter() {
        return splinter;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && !this.splinter) {
            tickSplit();
        }
        return hurt;
    }

    private void tickSplit() {
        if (splitCooldown > 0) {
            splitCooldown--;
            return;
        }
        if (this.random.nextFloat() > 0.25F) return;

        int allies = CorruptionSkillUtil.countAllies(this, 12.0);
        if (allies >= SPLIT_CAP) return;

        CorruptionSkillFx.bloodMist(this.level(), this.position(), 14);
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 2.0,
                CorruptionSkillUtil.CORRUPTION_RED, 16);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.CHICKEN_HURT, 1.2F, 1.2F, 0.1F);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.CHICKEN_HURT, 1.0F, 1.4F, 0.1F);

        // 周围击退
        for (LivingEntity target : CorruptionSkillUtil.findTargets(this, 2.5)) {
            target.push(0, 0.2, 0);
            target.hurtMarked = true;
        }

        for (int i = 0; i < 2; i++) {
            CorruptionChickenEntity chick = CorruptionEntityTypes.CORRUPTION_CHICKEN.get().create(this.level());
            if (chick == null) continue;
            chick.splinter = true;
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = 0.6 + this.random.nextDouble() * 0.6;
            chick.moveTo(this.getX() + Math.cos(angle) * r, this.getY(), this.getZ() + Math.sin(angle) * r,
                    this.random.nextFloat() * 360.0F, 0.0F);
            if (this.getTarget() != null) chick.setTarget(this.getTarget());
            this.level().addFreshEntity(chick);
        }

        splitCooldown = SPLIT_COOLDOWN;
    }
}
