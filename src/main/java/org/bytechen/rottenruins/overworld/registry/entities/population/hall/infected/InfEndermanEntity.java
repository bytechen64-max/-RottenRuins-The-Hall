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
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.function.Consumer;

public class InfEndermanEntity extends BaseInfectedEntity {

    // ---------- 同步数据 ----------
    private static final EntityDataAccessor<Boolean> IS_CRAWLING =
            SynchedEntityData.defineId(InfEndermanEntity.class, EntityDataSerializers.BOOLEAN);

    // ---------- 攻击相关 ----------
    private final int attackKeyTime = 17;   // 普通攻击动画持续时间（tick）
    private int attackKeyTimer = 0;         // 普通攻击计时器，0 表示未攻击

    // ---------- 爬行触发（血量驱动）----------
    private static final float CRAWL_HP_THRESHOLD = 0.3f;    // 生命值低于 30% 进入爬行
    private static final float CRAWL_HP_RECOVER = 0.8f;      // 生命值恢复到 80% 退出爬行
    private int noTargetTime = 0;                             // 爬行状态无目标计时

    // ---------- 动画定义 ----------
    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation ANIM_CRAWL  = RawAnimation.begin().thenLoop("crawl");
    /** 爬行且静止时的待机动画（资源文件 hall_enderman.animation.json 中的 idle_crawl） */
    private static final RawAnimation ANIM_CRAWL_IDLE = RawAnimation.begin().thenLoop("idle_crawl");

    // ---------- 构造函数 ----------
    public InfEndermanEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level, Consumer<AbstractRottenEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
        this.setHasWalkAnim(false);
        this.setWalkSpeed(0.22F);
        this.setDoHurtTime(30);
        // 原来由 MeleeAttackGoal(this, 1.2, true) 提供的追击速度倍率，
        // 现在追击统一由 AbstractRottenEntity#tickChase 发起，倍率在这里声明
        this.setChaseSpeed(1.2F);
    }

    // ---------- 属性 ----------
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 25)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8f);
    }

    // ---------- 目标与行为（修正：目标选择器必须添加到 targetSelector） ----------
    @Override
    protected void registerGoals() {
        // === 目标选择器（targetSelector）===
        // 使用 InfectedTargetGoal 选择最近的玩家或指定目标
        // 追击占位（只占 MOVE）：位移与出手由 AbstractRottenEntity#tickChase 统一负责。
        // 原来的 MeleeAttackGoal 在这里是重复劳动 —— 它会再算一份路径，
        // 而它的 doHurtTarget 在基类里被写死为 false，攻击部分一直空转。
        this.goalSelector.addGoal(1, new ChaseLockGoal(this));
        this.targetSelector.addGoal(1, new InfectedTargetGoal.Builder(this)
                .range(40.0)          // 搜索范围
                .mustSee(false)       // 无需视线
                // 威胁点数过滤器：玩家威胁 < 5 不主动索敌；非玩家生物照常索敌
                .filter(this)
                .build());

        // === 行为选择器（goalSelector）===
        // 面向目标（辅助）
        this.goalSelector.addGoal(2, new FaceTargetGoal(this, 40));
        // 随机游走
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        // 随机环顾
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        super.registerGoals(); // 如果有父类目标，保留
    }

    // ---------- 同步数据定义 ----------
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_CRAWLING, false);
    }

    // ---------- 爬行状态 getter/setter ----------
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

    // ---------- 碰撞箱（通过 Pose 控制） ----------
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (isCrawling()) {
            return EntityDimensions.fixed(1.8f, 1.2f);
        }
        return EntityDimensions.fixed(0.6f, 2.9f); // 末影人默认
    }

    // ---------- 爬墙 ----------
    @Override
    public boolean onClimbable() {
        return isCrawling();
    }

    // ---------- 移动速度 ----------
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

    // ---------- 每 Tick 逻辑 ----------
    @Override
    public void tick() {
        super.tick(); // 父类 tick 会处理普通攻击的距离检测并调用 doHurtGoal

        if (this.level().isClientSide()) return;
        // 爆体（假死）期间不再结算延迟攻击 / 爬行状态，只播缩放与 idle。
        if (this.isFakeDying()) return;

        // 攻击动画计时器递减，归零时造成伤害
        if (attackKeyTimer > 0) {
            attackKeyTimer--;
            if (attackKeyTimer == 0) {
                LivingEntity currentTarget = this.getTarget();
                if (currentTarget != null && currentTarget.isAlive()) {
                    getAttack(currentTarget);
                }
            }
        }

        // ---- 爬行状态检测（血量驱动 + 无目标退出）----
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

        // ---- 爬行状态（持续高频攻击）----
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
                // 追击寻路交给 AbstractRottenEntity#tickChase（已带节流 + 失败退避）。
                // 这里原来写的是 `isDone() || tickCount % 10 == 0` —— 但寻路失败时
                // isDone() 恒为 true，等于每 tick 跑一次 A*，目标卡在墙里时直接把 TPS 打穿。
            }
        }
    }

    // ---------- 普通攻击（非爬行）----------
    @Override
    protected boolean doHurtGoal(LivingEntity target) {
        if (isCrawling()) {
            return false; // 爬行攻击由 tick 直接处理
        }
        // 触发攻击动画和计时器，伤害在计时器归零时由 tick 执行
        attackKeyTimer = attackKeyTime;
        triggerAnim("attack_controller", "attack");
        return true; // 返回 true 让父类重置冷却
    }

    // 实际伤害方法
    @Override
    protected boolean getAttack(LivingEntity target) {
        // 必须走 effectiveAttackDamage（含属性修饰符），不能用 getAttributeBaseValue
        float baseDamage = HallDifficultyAttributes.effectiveAttackDamage(this, 25.0F);
        target.hurt(this.damageSources().mobAttack(this), baseDamage);
        return true;
    }

    // ---------- 动画控制器 ----------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;

        // 移动/待机/爬行控制器
        AnimationController<InfEndermanEntity> movementController =
                new AnimationController<>(this, "movement_controller", 5, state -> {
                    if (isDeadOrDying() || isBurstAnimating()) return PlayState.STOP;

                    if (isCrawling()) {
                        // 爬行模式：移动时播放爬行动画，静止时播放爬行待机动画。
                        // isActuallyMoving 由基类每 tick 在客户端按水平位移更新（带 5 tick 缓冲，
                        // 避免刚停下 / 起步的瞬间来回抖动）。
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

        // 死亡控制器
        AnimationController<AbstractRottenEntity> dieController =
                new AnimationController<>(this, "die_controller", 3, state ->
                        isBurstAnimating() ? state.setAndContinue(ANIM_DIE) : PlayState.STOP);
        dieController.setAnimationSpeed(getDieAnimSpeed());
        registrar.add(dieController.triggerableAnim("die_trigger", ANIM_DIE));
    }

    // ---------- 同步更新回调 ----------
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide && IS_CRAWLING.equals(key)) {
            this.refreshDimensions();
            applyMoveSpeed();
        }
    }

    // ---------- NBT ----------
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