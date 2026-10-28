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
 * 袭击者（简化版）。
 *
 * <p>原 corruption 的 {@code attacker}：重构种大型单位，以「C8 打击后撤」
 * （突进打一套后主动后跳脱战）为技能。合并简化阶段只保留碰撞箱、
 * 基础属性与走/跑动画参数；技能层后续按模块补回。</p>
 */
public class AttackerEntity extends BaseRefactoredEntity {

    public AttackerEntity(EntityType<? extends AttackerEntity> entityType, Level level,
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
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
