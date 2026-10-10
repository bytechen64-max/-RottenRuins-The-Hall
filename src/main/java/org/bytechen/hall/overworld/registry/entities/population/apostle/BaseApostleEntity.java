package org.bytechen.hall.overworld.registry.entities.population.apostle;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;

/**
 * 使徒（apostle）分类的基类。
 *
 * <p>分类含义：使徒是王庭感染谱系里的<b>顶层单位</b>——不靠贴身平砍，而是靠技能实体
 * （黑洞、斩击、冲击波……）作战。所以本基类把"近战攻击"这条路径直接关掉：
 * {@link #getAttack} 恒返回 false，一切都交给子类自己的技能调度。
 *
 * <p>与 {@code BaseEcologicalEntity}（王庭生态单位，同样是远程/技能向）的区别只在语义：
 * 使徒按"一次战斗只有一只"的强度设计，血量/护甲/技能冷却都按 boss 规格给。
 */
public abstract class BaseApostleEntity extends AbstractHallEntity {

    protected BaseApostleEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        // 使徒不用"撞上去平砍"这套：把 AbstractHallEntity 的近距离攻击钩子关掉
        this.setDoHurtDistance(0.0F);
    }

    /** 使徒不进行普通近战攻击（技能全部由子类自己的调度器负责）。 */
    @Override
    protected boolean getAttack(LivingEntity target) {
        return false;
    }
}
