package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.linked;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.BaseLinkedEntity;

import java.util.function.Consumer;

/**
 * 腐植柱（简化版）。
 *
 * <p>原 corruption 的 {@code humus_column}：联结种站桩单位，周期性给范围内
 * 盟友上增益、给敌人上减益，死亡时留下辐照领域。
 * 合并简化阶段只保留碰撞箱、基础属性与站桩设定（移速 0、不区分走/跑动画）；
 * 光环与死亡领域后续按模块补回。</p>
 */
public class HumusColumnEntity extends BaseLinkedEntity {

    public HumusColumnEntity(EntityType<? extends HumusColumnEntity> entityType, Level level,
                              Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.0F);
        this.setRunSpeed(0.0F);
        // 站桩单位不需要假死演出
        this.setFakeDeathEnabled(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
