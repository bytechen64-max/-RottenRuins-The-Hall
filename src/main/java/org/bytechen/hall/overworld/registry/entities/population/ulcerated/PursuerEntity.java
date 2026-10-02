package org.bytechen.hall.overworld.registry.entities.population.ulcerated;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import org.bytechen.hall.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.BaseInfectedEntity;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;

import java.util.function.Consumer;

/**
 * 溃烂追蹤者 —— 中等体积生物被感染后的形态。
 * <p>
 * 用途：当作感染（{@code hall:inf}）作用于一个<b>没有专属感染形态</b>（进化表里查不到规则）
 * 且碰撞体积中等的生物（宽 × 高 2 ~ 8）时，转化为追蹤者
 * —— 马、铁傀儡这类中型生物走的就是这一档；再大（&gt; 8）才是 {@link MonolithEntity 巨碑}。
 * 判定与转化由 infcore 的感染流程
 * （{@code EvolutionManager#applyEvolution} → 兜底档位）完成，
 * 档位在 {@link UlceratedConversionRules#register()} 中注册。
 * <p>
 * 属性：生命 24、伤害 8、护甲 4、移动很快（0.3，是斥候的两倍），擅长追踪（索敌 40 格）。
 * 碰撞箱由 {@code EntityTypeRegistry} 按需求给成 0.8 × 1.6。
 * 模型 / 贴图 / 动画：{@code geo/pursuer.geo.json}、{@code textures/entity/pursuer.png}、
 * {@code animations/pursuer.animation.json}（只有 idle 和 walk 两段动画）。
 * <p>
 * 与斥候的区别：不会爬墙，也不会参与「聚满 5 只合体成巨碑」的聚集。
 */
public class PursuerEntity extends BaseUlceratedEntity {

    public PursuerEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level,
                         Consumer<AbstractHallEntity> consumer) {
        super(entityType, level, consumer);
        // 只有 idle / walk 两段动画，没有 run 动画
        this.setHasWalkAnim(false);
        // 追蹤者：跑得快、出手快
        this.setWalkSpeed(0.3F);
        this.setRunSpeed(0.3F);
        this.setIdleAnimSpeed(1.0F);
        this.setWalkAnimSpeed(1.2F);
        this.setDoHurtTime(20);
        this.setDoHurtDistance(1);
    }

    // ---------- 属性 ----------
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25F);
    }

    // ---------- 目标与行为（与其它王庭生物保持一致） ----------
    @Override
    protected void registerGoals() {
        // 索敌：goalSelector 优先级 2，40 格内不需要视线
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1, true));
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0)
                .mustSee(false)
                .build());
        this.goalSelector.addGoal(2, new FaceTargetGoal(this, 4));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        super.registerGoals();
    }

    // ---------- 普通攻击 ----------
    @Override
    protected boolean getAttack(LivingEntity target) {
        float baseDamage = (float) getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        target.hurt(this.damageSources().mobAttack(this), baseDamage);
        return true;
    }
}
