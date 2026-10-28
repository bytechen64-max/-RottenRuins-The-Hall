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
 * 长臂兽（简化版）。
 *
 * <p>原 corruption 的 {@code long_arm_beast}：重构种大型单位，以「C7 钩刺拖拽」
 * 把目标拉回身前、死亡时留下辐照领域。合并简化阶段只保留碰撞箱、
 * 基础属性与走/跑动画参数；技能层后续按模块补回。</p>
 */
public class LongArmBeastEntity extends BaseRefactoredEntity {

    public LongArmBeastEntity(EntityType<? extends LongArmBeastEntity> entityType, Level level,
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
                .add(Attributes.MAX_HEALTH, 35.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
