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
 * 震撼者（简化版）。
 *
 * <p>原 corruption 的 {@code shocker}：重构种，以「C6 过载脉冲」
 * （原地蓄力 1.8 秒后释放全向红雷环）为技能。合并简化阶段只保留碰撞箱、
 * 基础属性与走/跑动画参数；技能层后续按模块补回。</p>
 *
 * <p>注意碰撞箱已按原工程经验收窄：原 2.4 宽会让 PathNavigation 认为
 * 几乎所有地形都过不去，这里保持 1.5 宽 / 2.8 高。</p>
 */
public class ShockerEntity extends BaseRefactoredEntity {

    public ShockerEntity(EntityType<? extends ShockerEntity> entityType, Level level,
                         Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.22F);
        this.setRunSpeed(0.29F);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 38.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 9.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
