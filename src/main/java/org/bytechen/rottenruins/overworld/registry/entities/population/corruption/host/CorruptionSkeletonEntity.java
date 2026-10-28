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
 * 同化骷髅（简化移植版，原 corruption 工程 {@code InfectionSkeletonEntity}）。
 *
 * <p>只保留构造器里的资源 / 动画速度 / 假死配置与 {@link #createAttributes()}，
 * 索敌、追击、近战全部继承 {@link BaseHostEntity}；远程 / 技能等留给后续机制层补齐。</p>
 *
 * <p>注册信息（由 {@code EntityTypeRegistry} 登记）：id {@code corruption_skeleton}，碰撞箱 0.6 × 1.99。</p>
 */
public class CorruptionSkeletonEntity extends BaseHostEntity {

    public CorruptionSkeletonEntity(EntityType<? extends CorruptionSkeletonEntity> entityType, Level level,
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
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
