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
 * 同化蝙蝠（简化移植版，原 corruption 工程 {@code InfectionBatEntity}）。
 *
 * <p><b>简化点</b>：暂时不做飞行，只走地面移动（沿用基类近战 / 索敌）；原工程的
 * {@code FlyingMoveControl} / {@code FlyingPathNavigation} / 空中 AI 与夜袭技能均未移植。</p>
 *
 * <p>TODO: 后续机制层补回飞行 —— 覆写 {@code createNavigation} 用
 * {@code FlyingPathNavigation}、构造器里挂 {@code FlyingMoveControl}，并补无摔落伤害与空中漫游 Goal。</p>
 *
 * <p>注册信息（由 {@code EntityTypeRegistry} 登记）：id {@code corruption_bat}，碰撞箱 0.6 × 0.9。</p>
 */
public class CorruptionBatEntity extends BaseHostEntity {

    public CorruptionBatEntity(EntityType<? extends CorruptionBatEntity> entityType, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.18F);
        this.setRunSpeed(0.24F);
        this.setFakeDeathChance(40);
        this.setFakeDeathDuration(30);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
