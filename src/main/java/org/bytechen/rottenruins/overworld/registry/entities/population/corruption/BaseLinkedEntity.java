package org.bytechen.rottenruins.overworld.registry.entities.population.corruption;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 腐化系 · 联结种（站桩塔类）薄层基类。
 *
 * <p>承载联结种通用收尾技能：<b>C9 殉照领域</b> —— 作为阵营增益/召唤核心，
 * 被拆掉时在倒地处留下一片腐蚀池，让"无视它绕过去"也有代价。</p>
 */
public abstract class BaseLinkedEntity extends BaseCorruptionEntity {

    /** [C9] 死亡时是否留下腐蚀池 */
    protected boolean deathFieldEnabled = false;
    protected float deathFieldRadius = 3.0F;
    protected int deathFieldLifetime = 200;
    protected float deathFieldDamage = 3.5F;

    protected BaseLinkedEntity(EntityType<? extends BaseLinkedEntity> entityType, Level level,
                               Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
    }

    /** [C9] 开启死亡殉照领域。 */
    protected BaseLinkedEntity enableDeathField(float radius, int lifetime, float damagePerTick) {
        this.deathFieldEnabled = true;
        this.deathFieldRadius = radius;
        this.deathFieldLifetime = lifetime;
        this.deathFieldDamage = damagePerTick;
        return this;
    }

    @Override
    protected void onBurstEnd() {
        if (deathFieldEnabled && !this.level().isClientSide) {
            CorruptionSkillUtil.burst(this, deathFieldRadius * 0.7, 24, 0.4);
            CorruptionSkillFx.shockwaveRing(this.level(), this.position(), deathFieldRadius,
                    CorruptionSkillUtil.CORRUPTION_RED, 28);
            CorruptionSkillUtil.spawnPoolAt(this.level(), this.position(),
                    deathFieldRadius, deathFieldLifetime, deathFieldDamage, 20, null);
        }
        super.onBurstEnd();
    }
}
