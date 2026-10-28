package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.degenerate;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseDegenerateEntity;

import java.util.function.Consumer;

/**
 * 腐化牛（简化版）。
 *
 * <p>原 corruption 的 {@code degenerate_cow}：腐化种重型单位，
 * 以「C4 屠戮者暴怒 / C5 血肉偿还」为技能。合并简化阶段只保留碰撞箱、
 * 基础属性与走/跑动画参数；技能层后续按模块补回。</p>
 */
public class DegenerateCowEntity extends BaseDegenerateEntity {

    public DegenerateCowEntity(EntityType<? extends DegenerateCowEntity> entityType, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.2F);
        this.setRunSpeed(0.6F);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
