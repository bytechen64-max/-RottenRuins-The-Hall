package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.refactored;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseRefactoredEntity;

import java.util.function.Consumer;

/**
 * 孕育者（简化版）。
 *
 * <p>原 corruption 的 {@code bearer}：重构种，靠吞噬同类积累「吞噬值」，
 * 达标后按 {@code BearerManager} 的规则进化为长臂兽 / 震撼者。
 * 合并简化阶段只保留碰撞箱、基础属性与走/跑动画参数；
 * 吞噬增长与进化逻辑后续按模块补回（建议接 infcore evolution）。</p>
 */
public class BearerEntity extends BaseRefactoredEntity {

    public BearerEntity(EntityType<? extends BearerEntity> entityType, Level level,
                        Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.14F);
        this.setRunSpeed(0.18F);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
