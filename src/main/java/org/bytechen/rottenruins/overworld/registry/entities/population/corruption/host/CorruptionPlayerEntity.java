package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.host;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseHostEntity;

import java.util.function.Consumer;

/** 同化玩家：近战 + B10（8 根 / 4.0）+ B11 血肉牵引（0.9）+ B12 汲血（0.35）。 */
public class CorruptionPlayerEntity extends BaseHostEntity {

    public CorruptionPlayerEntity(EntityType<? extends CorruptionPlayerEntity> entityType, Level level,
                                  Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.23F);
        this.setRunSpeed(0.31F);
        this.setFakeDeathEnabled(true);
        this.setFakeDeathChance(50);
        this.setFakeDeathDuration(45);
        this.enableBoneBurst(8, 4.0F);
        this.enableFleshPull(0.9);
        this.enableLifesteal(0.35F);
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
