package org.bytechen.rottenruins.overworld.registry.entities.population.corruption;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillFx;
import org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill.CorruptionSkillUtil;

import java.util.function.Consumer;

/**
 * 腐化系 · 重构种薄层基类。
 *
 * <p>承载重构种通用收尾技能：<b>C9 殉照领域</b> —— 死亡（爆体结束）时在倒地处留下
 * 一片持续腐蚀的池子。对怀载者尤其重要：它靠吞噬同类长大，被击杀后喷出的污染
 * 正好把"喂大了它"这件事变成玩家的实际麻烦，形成代价闭环。</p>
 */
public abstract class BaseRefactoredEntity extends BaseCorruptionEntity {

    /** [C9] 死亡时是否留下腐蚀池 */
    protected boolean deathFieldEnabled = false;
    /** [C9] 池半径 */
    protected float deathFieldRadius = 3.0F;
    /** [C9] 池持续 tick */
    protected int deathFieldLifetime = 200;
    /** [C9] 每跳伤害 */
    protected float deathFieldDamage = 3.5F;

    protected BaseRefactoredEntity(EntityType<? extends BaseRefactoredEntity> entityType, Level level,
                                   Consumer<AbstractRottenEntity> resources) {
        super(entityType, level, resources);
    }

    /** [C9] 开启死亡殉照领域。 */
    protected BaseRefactoredEntity enableDeathField(float radius, int lifetime, float damagePerTick) {
        this.deathFieldEnabled = true;
        this.deathFieldRadius = radius;
        this.deathFieldLifetime = lifetime;
        this.deathFieldDamage = damagePerTick;
        return this;
    }

    @Override
    protected void onBurstEnd() {
        triggerDeathField();
        super.onBurstEnd();
    }

    /** [C9] 死亡殉照领域：遗体位置留下一片腐蚀池。 */
    private void triggerDeathField() {
        if (!deathFieldEnabled || this.level().isClientSide) return;

        CorruptionSkillUtil.burst(this, deathFieldRadius * 0.7, 24, 0.4);
        CorruptionSkillFx.shockwaveRing(this.level(), this.position(), deathFieldRadius,
                CorruptionSkillUtil.CORRUPTION_RED, 28);

        CorruptionSkillUtil.spawnPoolAt(this.level(), this.position(),
                deathFieldRadius, deathFieldLifetime, deathFieldDamage, 20, null);
    }
}
