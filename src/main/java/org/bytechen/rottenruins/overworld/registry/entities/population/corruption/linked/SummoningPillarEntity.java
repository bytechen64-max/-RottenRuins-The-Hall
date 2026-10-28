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
 * 召唤柱（简化版）。
 *
 * <p>原 corruption 的 {@code summoning_pillar}：联结种站桩「兵营」，
 * 周期性从柱体中召唤同化种增援（区域人口上限 + 长冷却），死亡时留下辐照领域。
 * 合并简化阶段只保留碰撞箱、基础属性与站桩设定（移速 0、不区分走/跑动画）；
 * 召唤逻辑后续按模块补回。</p>
 */
public class SummoningPillarEntity extends BaseLinkedEntity {

    public SummoningPillarEntity(EntityType<? extends SummoningPillarEntity> entityType, Level level,
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
                .add(Attributes.MAX_HEALTH, 23.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
