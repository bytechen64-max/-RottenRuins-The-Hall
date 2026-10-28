package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.deteriorated;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseDeterioratedEntity;

import java.util.function.Consumer;

/**
 * 匍匐者（简化版）。
 *
 * <p>原 corruption 的 {@code groveler}：退化种，以「C10 地底突袭」
 * （钻地消失 → 目标脚下破土 → 斩击击飞）为技能。合并简化阶段只保留碰撞箱、
 * 基础属性与走/跑动画参数；技能层后续按模块补回。</p>
 */
public class GrovelerEntity extends BaseDeterioratedEntity {

    public GrovelerEntity(EntityType<? extends GrovelerEntity> entityType, Level level,
                          Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.21F);
        this.setRunSpeed(0.9F);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 25.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
