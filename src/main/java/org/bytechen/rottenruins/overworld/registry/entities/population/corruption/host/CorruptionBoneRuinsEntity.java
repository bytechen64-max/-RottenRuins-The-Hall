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
 * 同化骨堆 / 骨墟（简化移植版，原 corruption 工程 {@code InfectionBoneRuinsEntity}）。
 *
 * <p><b>站桩生物</b>：walk / run 速度都设为 0，不产生位移，只有 idle 动画
 * （{@link #setHasWalkAnim(boolean)} = false，基类始终按 walk 速度走，而这里是 0）。
 * 索敌与近战仍继承 {@link BaseHostEntity}（够得着就出手），其余技能留给后续机制层补齐。</p>
 *
 * <p>注册信息（由 {@code EntityTypeRegistry} 登记）：id {@code corruption_bone_ruins}，碰撞箱 0.6 × 1.99。</p>
 */
public class CorruptionBoneRuinsEntity extends BaseHostEntity {

    public CorruptionBoneRuinsEntity(EntityType<? extends CorruptionBoneRuinsEntity> entityType, Level level,
                                     Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.0F);
        this.setRunSpeed(0.0F);
        // 站桩单位不做假死/爆体演出（原工程同样是关闭的）
        this.setFakeDeathEnabled(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
