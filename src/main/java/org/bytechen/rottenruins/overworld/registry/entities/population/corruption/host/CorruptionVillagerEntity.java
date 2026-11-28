package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.corruption.CorruptionEffects;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 同化村民：B8 同化传播 —— 近战命中玩家叠加腐化层数（最多 4 层），
 * 第 4 层触发失控（缓慢 IV + 失明 + 虚弱 + 反胃）；另带 B1 吐息（4.5/60/14）+ B12 汲血（0.5）。
 */
public class CorruptionVillagerEntity extends BaseHostEntity {

    private static final int MAX_STACKS = 4;
    private static final int STACK_DURATION = 240;

    private LivingEntity stackTarget;
    private int stacks = 0;

    public CorruptionVillagerEntity(EntityType<? extends CorruptionVillagerEntity> entityType, Level level,
                                    Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
        this.enableSpit(4.5F, 60, 14.0);
        this.enableLifesteal(0.5F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 15.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        if (this.getTarget() != target) {
            this.stackTarget = target;
            this.stacks = 0;
        }

        boolean hit = super.getAttack(target);
        if (!hit) return false;

        if (target instanceof Player) {
            this.stacks = Math.min(MAX_STACKS, this.stacks + 1);
            CorruptionSkillUtil.stackEffect(target, CorruptionEffects.CORRUPTION.get(),
                    STACK_DURATION, Math.min(this.stacks - 1, 3));
            CorruptionSkillFx.impact(this.level(), target.position(),
                    CorruptionSkillUtil.CORRUPTION_RED, 0.6F);

            if (this.stacks >= MAX_STACKS) {
                CorruptionSkillUtil.slow(target, 100, 4);
                CorruptionSkillUtil.applyEffect(target, MobEffects.BLINDNESS, 100, 0);
                CorruptionSkillUtil.applyEffect(target, MobEffects.WEAKNESS, 100, 1);
                CorruptionSkillUtil.nausea(target, 100, 1);
                CorruptionSkillUtil.burst(this, 2.0, 16, 0.35);
                CorruptionSkillFx.shockwaveRing(this.level(), this.position(), 2.0,
                        CorruptionSkillUtil.CORRUPTION_RED, 20);
                CorruptionSkillUtil.playSoundPitched(this, net.minecraft.sounds.SoundEvents.VILLAGER_HURT,
                        1.2F, 0.8F, 0.1F);
                this.stacks = 0;
            }
        }
        return true;
    }
}
