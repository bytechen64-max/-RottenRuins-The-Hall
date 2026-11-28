package org.bytechen.rottenruins.overworld.registry.entities.population.hall.infected;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.overworld.difficulty.HallDifficultyAttributes;
import org.bytechen.rottenruins.overworld.registry.entities.ai.ChaseLockGoal;
import org.bytechen.rottenruins.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.api.infection.goal.InfectedTargetGoal;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.function.Consumer;

public class InfEndermanEntity extends BaseInfectedEntity {

    private static final EntityDataAccessor<Boolean> IS_CRAWLING =
            SynchedEntityData.defineId(InfEndermanEntity.class, EntityDataSerializers.BOOLEAN);

    private final int attackKeyTime = 17;   // 普通攻击动画持续时间（tick）
    private int attackKeyTimer = 0;

    private static final float CRAWL_HP_THRESHOLD = 0.3f;    // 生命值低于 30% 进入爬行
    private static final float CRAWL_HP_RECOVER = 0.8f;      // 生命值恢复到 80% 退出爬行
    private int noTargetTime = 0;

    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation ANIM_CRAWL  = RawAnimation.begin().thenLoop("crawl");
    /** 爬行且静止时的待机动画（资源 hall_enderman.animation.json 的 idle_crawl） */
    private static final RawAnimation ANIM_CRAWL_IDLE = RawAnimation.begin().thenLoop("idle_crawl");

    public InfEndermanEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level, Consumer<AbstractRottenEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.22F);
        this.setDoHurtTime(30);
        // 追击速度倍率：原先由 MeleeAttackGoal 提供，现在由 AbstractRottenEntity#tickChase 发起
        this.setChaseSpeed(1.2F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 25)
                .add(Attributes.ARMOR, 20)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8f);
    }

    @Override
    protected void registerGoals() {
        // 追击占位（只占 MOVE）：位移与出手由 AbstractRottenEntity#tickChase 统一负责。
        // 别换回 MeleeAttackGoal：它会再算一份路径，且 doHurtTarget 在基类恒为 false。
        this.goalSelector.addGoal(1, new ChaseLockGoal(this));
        this.targetSelector.addGoal(1, new InfectedTargetGoal.Builder(this)
                .targetOtherInfected(true)   // 王庭 vs 腐化：跨阵营索敌（同阵营仍被 infcore 过滤）
                .range(40.0)
                .mustSee(false)
                // 威胁点数过滤器：玩家威胁 < 5 不主动索敌；非玩家生物照常索敌
                .filter(this)
                .build());

        this.goalSelector.addGoal(2, new FaceTargetGoal(this, 40));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        super.registerGoals();
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_CRAWLING, false);
    }

    public boolean isCrawling() {
        return this.entityData.get(IS_CRAWLING);
    }

    public void setCrawling(boolean crawling) {
        if (this.entityData.get(IS_CRAWLING) == crawling) return;
        this.entityData.set(IS_CRAWLING, crawling);
        this.refreshDimensions();
        applyMoveSpeed();
        if (crawling) {
            attackKeyTimer = 0;
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (isCrawling()) {
            return EntityDimensions.fixed(1.8f, 1.2f);
        }
        return EntityDimensions.fixed(0.6f, 2.9f); // 末影人默认
    }

    @Override
    public boolean onClimbable() {
        return isCrawling();
    }

    @Override
    public float getCurrentMoveSpeed() {
        if (isCrawling()) {
            return 0.26f;
        }
        return super.getCurrentMoveSpeed();
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (isCrawling() && this.isEffectiveAi()) {
            super.travel(travelVector.scale(2.5D));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void tick() {
        super.tick(); // 父类 tick 会处理普通攻击的距离检测并调用 doHurtGoal

        if (this.level().isClientSide()) return;
        // 爆体（假死）期间不再结算延迟攻击 / 爬行状态，只播缩放与 idle。
        if (this.isFakeDying()) return;

        if (attackKeyTimer > 0) {
            attackKeyTimer--;
            if (attackKeyTimer == 0) {
                LivingEntity currentTarget = this.getTarget();
                if (currentTarget != null && currentTarget.isAlive()) {
                    getAttack(currentTarget);
                }
            }
        }

        float healthPct = this.getHealth() / this.getMaxHealth();
        if (!isCrawling() && healthPct <= CRAWL_HP_THRESHOLD) {
            setCrawling(true);
        } else if (isCrawling()) {
            LivingEntity crawlTarget = this.getTarget();
            if (crawlTarget != null && crawlTarget.isAlive()) {
                noTargetTime = 0;
            } else {
                noTargetTime++;
                if (noTargetTime >= 60) { // 3 秒无目标退出爬行
                    setCrawling(false);
                    noTargetTime = 0;
                }
            }
            if (healthPct >= CRAWL_HP_RECOVER) {
                setCrawling(false);
                noTargetTime = 0;
            }
        } else {
            noTargetTime = 0;
        }

        if (isCrawling()) {
            LivingEntity crawlTarget = this.getTarget();
            if (crawlTarget != null && crawlTarget.isAlive()) {
                // 先看无敌帧再看距离/视线：invulnerableTime > 0 时这一下本来就打不进去，
                // 没必要每 tick 做一次射线检测 + 完整伤害结算
                double dist = this.distanceToSqr(crawlTarget);
                if (crawlTarget.invulnerableTime <= 0
                        && dist <= doHurtDistance * doHurtDistance
                        && this.hasLineOfSight(crawlTarget)) {
                    getAttack(crawlTarget);
                }
                // 追击寻路交给 AbstractRottenEntity#tickChase（带节流 + 失败退避）。
                // 别改回 `isDone() || tickCount % 10 == 0`：寻路失败时 isDone() 恒为 true。
            }
        }
    }

    @Override
    protected boolean doHurtGoal(LivingEntity target) {
        if (isCrawling()) {
            return false; // 爬行攻击由 tick 直接处理
        }
        // 只起动画与计时器，伤害在计时器归零时由 tick 结算
        attackKeyTimer = attackKeyTime;
        triggerAnim("attack_controller", "attack");
        return true; // 返回 true 让父类重置冷却
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        // 必须走 effectiveAttackDamage（含属性修饰符），不能用 getAttributeBaseValue
        float baseDamage = HallDifficultyAttributes.effectiveAttackDamage(this, 25.0F);
        target.hurt(this.damageSources().mobAttack(this), baseDamage);
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;

        AnimationController<InfEndermanEntity> movementController =
                new AnimationController<>(this, "movement_controller", 5, state -> {
                    if (isDeadOrDying() || isBurstAnimating()) return PlayState.STOP;

                    if (isCrawling()) {
                        // 爬行模式：移动播 crawl，静止播 idle_crawl。isActuallyMoving 由基类
                        // 每 tick 按客户端水平位移更新（5 tick 缓冲，避免起步/停下时抖动）。
                        if (!isActuallyMoving) {
                            state.setControllerSpeed(getIdleAnimSpeed());
                            return state.setAndContinue(ANIM_CRAWL_IDLE);
                        }
                        state.setControllerSpeed(1.0f);
                        return state.setAndContinue(ANIM_CRAWL);
                    }

                    if (!isActuallyMoving) {
                        state.setControllerSpeed(getIdleAnimSpeed());
                        return state.setAndContinue(ANIM_IDLE);
                    }
                    if (hasWalkAnimation()) {
                        if (isWalking()) {
                            state.setControllerSpeed(getWalkAnimSpeed());
                            return state.setAndContinue(ANIM_WALK);
                        } else {
                            state.setControllerSpeed(getRunAnimSpeed());
                            return state.setAndContinue(ANIM_RUN);
                        }
                    }
                    state.setControllerSpeed(getWalkAnimSpeed());
                    return state.setAndContinue(ANIM_WALK);
                });
        registrar.add(movementController);

        // 攻击控制器（独立，常态 STOP，仅在触发时播放）
        AnimationController<InfEndermanEntity> attackController =
                new AnimationController<>(this, "attack_controller", 0, state -> PlayState.STOP);
        attackController.triggerableAnim("attack", ANIM_ATTACK);
        registrar.add(attackController);

        AnimationController<AbstractRottenEntity> dieController =
                new AnimationController<>(this, "die_controller", 3, state ->
                        isBurstAnimating() ? state.setAndContinue(ANIM_DIE) : PlayState.STOP);
        dieController.setAnimationSpeed(getDieAnimSpeed());
        registrar.add(dieController.triggerableAnim("die_trigger", ANIM_DIE));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide && IS_CRAWLING.equals(key)) {
            this.refreshDimensions();
            applyMoveSpeed();
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("IsCrawling", isCrawling());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("IsCrawling")) {
            setCrawling(nbt.getBoolean("IsCrawling"));
        }
    }
}
