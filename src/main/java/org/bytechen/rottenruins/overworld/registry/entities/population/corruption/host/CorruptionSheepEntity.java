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
 * 同化羊（简化移植版，原 corruption 工程 {@code InfectionSheepEntity}）。
 *
 * <p>只有 walk 一段移动动画，没有 run —— 因此 {@link #setHasWalkAnim(boolean)} 传 false，
 * 基类会始终按 walk 速度 / walk 动画移动。索敌、追击、近战继承 {@link BaseHostEntity}，
 * 技能等留给后续机制层补齐。</p>
 *
 * <p>注册信息（由 {@code EntityTypeRegistry} 登记）：id {@code corruption_sheep}，碰撞箱 0.8 × 1.2。</p>
 */
public class CorruptionSheepEntity extends BaseHostEntity {

    public CorruptionSheepEntity(EntityType<? extends CorruptionSheepEntity> entityType, Level level,
                                 Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 9.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
