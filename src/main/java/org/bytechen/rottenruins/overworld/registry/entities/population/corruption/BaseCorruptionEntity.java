package org.bytechen.rottenruins.overworld.registry.entities.population.corruption;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import org.bytechen.rottenruins.overworld.registry.entities.ai.ChaseLockGoal;
import org.bytechen.rottenruins.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.entities.base.RottenFaction;

import java.util.function.Consumer;

/**
 * 腐化系（原 corruption 工程）生物的公共基类 —— 合并后的简化版。
 *
 * <p>当前只保留最小可玩闭环：infcore 阵营索敌（{@link InfectedTargetGoal}）+ 基类追击 +
 * 近战 + 随机漫步。原来的 SmartMove/SmartTarget/brain、技能、节点、演化等机制层
 * 均未移植，后续按模块逐步补回（骨架已预留在 family 薄层里）。</p>
 */
public abstract class BaseCorruptionEntity extends AbstractRottenEntity {

    protected BaseCorruptionEntity(EntityType<? extends BaseCorruptionEntity> entityType, Level level,
                                   Consumer<AbstractRottenEntity> resources) {
        super(entityType, level);
        // 腐化系默认速度（原工程 MOVEMENT_SPEED≈0.26，追击 1.2x）
        this.setWalkSpeed(0.20F);
        this.setRunSpeed(0.26F);
        resources.accept(this);
    }

    @Override
    protected RottenFaction faction() {
        return RottenFaction.CORRUPTION;
    }

    @Override
    protected void registerGoals() {
        // 追击占位（只占 MOVE）：有目标时压住 RandomStrollGoal
        this.goalSelector.addGoal(1, new ChaseLockGoal(this));
        // 索敌：统一走 infcore 阵营判断（同阵营感染生物默认不互殴，玩家按威胁值过滤）
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0D)
                .mustSee(false)
                .filter(this)
                .build());
        this.goalSelector.addGoal(2, new FaceTargetGoal(this, 4));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        super.registerGoals();
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return target.hurt(this.damageSources().mobAttack(this), damage);
    }
}
