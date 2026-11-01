package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.original;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseOriginalEntity;

import java.util.function.Consumer;

/**
 * 原虫（简化版）。
 *
 * <p>原 corruption 的 {@code original_bug}：原生种唯一战斗单位，
 * 以「C11 酸液喷吐」为核心技能，血量极低（8 点）。
 * 合并简化阶段只保留碰撞箱、基础属性与走/跑动画参数；
 * 技能层后续按模块补回。</p>
 */
public class OriginalBugEntity extends BaseOriginalEntity {

    public OriginalBugEntity(EntityType<? extends OriginalBugEntity> entityType, Level level,
                              Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
