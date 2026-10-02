package org.bytechen.hall.overworld.registry.entities.population.ulcerated;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.BaseInfectedEntity;
import org.bytechen.hall.utils.TickUtils;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import org.bytechen.infcore.core.evolution.EvolutionManager;

import java.util.function.Consumer;

/**
 * 溃烂巨碑 —— 大型生物被感染后的兜底形态。
 * <p>
 * 用途：当作感染（{@code hall:inf}）作用于一个<b>没有专属感染形态</b>（进化表里查不到规则）
 * 且碰撞体积较大（宽 × 高 &gt; 8）的生物时，转化为巨碑
 * —— 玩家、末影人、恶魂、末影龙、巨型僵尸这类大体积生物走的就是这一档。
 * 判定与转化由 infcore 的感染流程
 * （{@code EvolutionManager#applyEvolution} → 兜底档位）完成，
 * 档位在 {@link UlceratedConversionRules#register()} 中注册。
 * <p>
 * 属性：生命 100、伤害 20、护甲 10、移动缓慢（0.2）、抗击退，碰撞箱 2.4 × 3.5（体积 8.4，本档自洽）。
 * 模型 / 贴图 / 动画：{@code geo/monolith.geo.json}、{@code textures/entity/monolith.png}、
 * {@code animations/monolith.animation.json}（idle 与 walk 两段循环动画）。
 */
public class MonolithEntity extends BaseUlceratedEntity {

    public MonolithEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level,
                          Consumer<AbstractHallEntity> consumer) {
        super(entityType, level, consumer);
        // 只有 idle / walk 两段动画，没有 run 动画
        this.setHasWalkAnim(false);
        // 体积庞大但移动缓慢
        this.setWalkSpeed(0.2F);
        this.setRunSpeed(0.2F);
        this.setIdleAnimSpeed(0.8F);
        this.setWalkAnimSpeed(0.8F);
        // 攻击更慢、范围更大
        this.setDoHurtTime(30);
        this.setDoHurtDistance(1);
    }

    @Override
    public void tick() {
        if(this.level() instanceof ServerLevel)        if(TickUtils.tickCountTimerWithoutStart(this.tickCount,20*120)) EvolutionManager.applyEvolution((ServerLevel)this.level(),this,new ResourceLocation(HallMod.MODID,"evo"));


        super.tick();
    }

    // ---------- 属性 ----------
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 20)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0F);
    }

    // ---------- 目标与行为 ----------
    @Override
    protected void registerGoals() {
        // 索敌：与其它王庭生物一致 —— goalSelector 优先级 2，40 格内无需视线
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1, true));
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0)
                .mustSee(false)
                // 威胁点数过滤器：玩家威胁 < 5 不主动索敌；非玩家生物照常索敌
                .filter(this)
                .build());
        this.goalSelector.addGoal(2, new FaceTargetGoal(this, 4));
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
