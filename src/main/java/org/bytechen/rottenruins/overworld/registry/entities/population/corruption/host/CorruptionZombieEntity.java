package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;

import java.util.function.Consumer;

/**
 * 同化僵尸（简化版模板）。
 *
 * <p>这是 22 个腐化生物的迁移模板：只保留构造函数（资源 / 动画速度 / 假死概率）、
 * {@link #createAttributes()} 与父类默认索敌/近战；技能、特殊 AI 等留给后续机制层补齐。</p>
 */
public class CorruptionZombieEntity extends BaseHostEntity {

    public CorruptionZombieEntity(EntityType<? extends CorruptionZombieEntity> entityType, Level level,
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
                .add(Attributes.MAX_HEALTH, 15.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
