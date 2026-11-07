package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.original;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseOriginalEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionDart;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * 原虫（原生种唯一战斗单位）。
 *
 * <p>技能：<b>C11 酸液喷射</b> —— 朝正面喷出一道锥形酸雾，附带腐蚀与短暂破甲。
 * 血量极低（8 点），强调"贴脸就要付代价"：喷吐前有明显吸气动作与音效，
 * 玩家有约 0.7 秒的窗口侧移或后撤。</p>
 */
public class OriginalBugEntity extends BaseOriginalEntity {

    // =========================================================================
    // C11 酸液喷射参数
    // =========================================================================
    private static final int SPRAY_COOLDOWN = 130;
    private static final double SPRAY_RANGE = 5.5;
    /** 喷射锥的角宽（度） */
    private static final double SPRAY_ANGLE = 60.0;
    /** 蓄力（吸气）时长 —— 玩家反应窗口 */
    private static final int SPRAY_WINDUP = 14;
    private static final float SPRAY_DAMAGE = 5.5F;
    /** 破甲时长 */
    private static final int SHATTER_DURATION = 120;

    private int sprayCooldown = 50;
    private int sprayWindup = -1;

    public OriginalBugEntity(EntityType<? extends OriginalBugEntity> entityType, Level level,
                             Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);   // 该模型无 run 动画：始终走 walk
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    // =========================================================================
    // C11 酸液喷射
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;

        if (sprayCooldown > 0) sprayCooldown--;

        if (sprayWindup >= 0) {
            sprayWindup--;

            float progress = 1.0F - (float) sprayWindup / SPRAY_WINDUP;
            CorruptionSkillUtil.chargeIn(this, 1.4, 4, progress);
            // 吸气音：升调提示"要喷了"
            if (sprayWindup % 5 == 0) {
                CorruptionSkillUtil.playSoundPitched(this, SoundEvents.PUFFER_FISH_BLOW_UP, 0.8F,
                        0.8F + progress * 0.6F, 0.05F);
            }

            LivingEntity target = this.getTarget();
            if (target != null) CorruptionSkillUtil.faceTarget(this, target);

            if (sprayWindup < 0) {
                releaseSpray();
            }
            return;
        }

        if (sprayCooldown > 0) return;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        double dist = Math.sqrt(this.distanceToSqr(target));
        if (dist > SPRAY_RANGE + 2.0) return;

        sprayWindup = SPRAY_WINDUP;
    }

    /** 释放：锥形酸雾，命中者腐蚀 + 破甲（虚弱 + 溶解）。 */
    private void releaseSpray() {
        Vec3 origin = this.position().add(0, this.getEyeHeight() * 0.8, 0);
        Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());

        CorruptionSkillFx.breath(this.level(), origin, forward, SPRAY_RANGE,
                CorruptionDart.ACID, 40);
        CorruptionSkillUtil.playSoundPitched(this, SoundEvents.PUFFER_FISH_BLOW_OUT, 1.3F, 0.8F, 0.1F);

        List<LivingEntity> hits = CorruptionSkillUtil.findTargetsInSector(this, SPRAY_RANGE, SPRAY_ANGLE);
        for (LivingEntity target : hits) {
            if (CorruptionSkillUtil.strike(this, target, SPRAY_DAMAGE, 0.15)) {
                // 破甲：先削抗性再叠溶解，形成"越打越脆"的短期窗口
                CorruptionSkillUtil.applyEffect(target, MobEffects.WEAKNESS, SHATTER_DURATION, 1);
                CorruptionSkillUtil.lysis(target, SHATTER_DURATION, 0);
                CorruptionSkillUtil.corrupt(target, SHATTER_DURATION, 0);
                CorruptionSkillFx.impact(this.level(), target.position(), CorruptionDart.ACID, 0.5F);
            }
        }

        sprayCooldown = SPRAY_COOLDOWN;
    }

    /** 禁止被其他实体推动。 */
    @Override
    public void push(Entity entity) {
    }
}
