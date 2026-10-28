package org.bytechen.rottenruins.overworld.registry.entities.population.ecological;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.overworld.difficulty.HallDifficultyAttributes;
import org.bytechen.rottenruins.overworld.registry.entities.ai.ChaseLockGoal;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.skills.SwordAuraEntity;
import org.bytechen.rottenruins.utils.TickUtils;
import org.bytechen.rottenruins.utils.entity.EntityBreakUtils;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

import java.util.function.Consumer;

public class BonecrusherEntity extends BaseEcologicalEntity {

    // ========== 新增：记录上次进入战斗的时间（用于脱战回血） ==========
    private int lastCombatTick = 0;

    // ========== 冲撞相关字段 ==========
    private int chargeCooldown = 0;      // 冷却剩余 tick
    private int chargeDuration = 0;      // 冲撞持续剩余 tick（>0 表示正在冲撞）

    public BonecrusherEntity(EntityType<? extends BaseEcologicalEntity> entityType, Level level, Consumer<AbstractHallEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.36f);
        this.setRunSpeed(0.7F);
        this.setDoHurtTime(20);
    }

    @Override
    public void tick() {
        // 爆体（假死）期间只播缩放：停掉破坏方块 / 冲撞，避免爆体还在拆地形。
        if (!this.isFakeDying()) {
            if (this.level() instanceof ClientLevel) {
                clientTick();
            } else {
                serverTick();
            }
        }
        super.tick();
    }

    // ------------------ 服务端逻辑 ------------------
    private void serverTick() {
        // 破坏方块：常态每 5 tick 扫一次；冲撞中每 3 tick 扫一次。
        //
        // 冲撞期间以前是「每 tick 扫一遍膨胀后的碰撞箱并逐块 destroyBlock」——
        // 一次扫描是 7×7×7 的方块查询 + 每个可破坏方块一次 destroyBlock
        // （含邻块更新、onRemove、gameEvent），40 tick 的冲撞就是 40 次，
        // 和爆炸 / 其它方块更新叠在一起时非常贵。降到每 3 tick 少扫 3 倍，
        // 撞墙效果不变（破掉的方块会变空气，不会重复破坏；碰撞箱 5×5 也不会漏格）。
        int breakInterval = this.chargeDuration > 0 ? 3 : 5;
        if (TickUtils.tickCountTimer(tickCount, breakInterval)) {
            EntityBreakUtils.breakBlocksInRange(this, 1, 2f, false);
        }

        // 脱战回血（10 秒未战斗）
        if (tickCount - lastCombatTick > 200) {
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(0.05f);
            }
        }

        // ========== 冲撞逻辑 ==========
        LivingEntity target = this.getTarget();
        if (chargeDuration > 0) {
            // 向目标快速移动（速度倍率 1.5）
            if (target != null && target.isAlive()) {
                // 冲撞期间不必每 tick 重算路径：A* 一次够走好几格；目标不可达时
                // 每 tick 一次寻路是纯粹的浪费（同 AbstractHallEntity#tickChase 的原因）。
                if (chargeDuration % 10 == 0 && this.getNavigation().isDone()) {
                    this.getNavigation().moveTo(target, 1.5D);
                }
            } else {
                // 目标无效，立即结束冲撞并进入冷却
                chargeDuration = 0;
                chargeCooldown = 300;   // 15 秒
            }

            chargeDuration--;
            if (chargeDuration <= 0) {
                chargeCooldown = 300;   // 冲撞结束，冷却 15 秒
            }
        } else {
            // 未冲撞：处理冷却
            if (chargeCooldown > 0) {
                chargeCooldown--;
            } else if (target != null && target.isAlive()) {
                // 冷却完毕且有目标 → 启动冲撞（持续 2 秒 = 40 tick）
                chargeDuration = 40;
            }
        }
    }

    // ------------------ 客户端逻辑 ------------------
    private void clientTick() {
        // 客户端无需额外处理
    }

    // ------------------ 属性 ------------------
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 25)
                .add(Attributes.ARMOR, 20)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1f);
    }

    // ------------------ AI 目标 ------------------
    @Override
    protected void registerGoals() {
        // 追击占位（只占 MOVE）：有目标时压住 RandomStrollGoal，
        // 免得"追击路径"被随机散步顶掉（位移本身仍由 AbstractHallEntity#tickChase 发起）
        this.goalSelector.addGoal(1, new ChaseLockGoal(this));
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0)
                // 威胁点数过滤器：玩家威胁 < 5 不主动索敌；非玩家生物照常索敌
                .filter(this)
                .build());
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        super.registerGoals();
    }

    // ------------------ 攻击与剑气 ------------------
    private static final int SWORD_AURA_CHANCE = 20;
    private static final float SWORD_AURA_SHRINK = 3.0f;
    private static final float SWORD_AURA_DAMAGE_MULT = 5.0f;
    private static final float SWORD_AURA_SCALE = 3f;

    @Override
    protected boolean getAttack(LivingEntity target) {
        this.lastCombatTick = this.tickCount;   // 更新战斗时间
        // 必须走 effectiveAttackDamage（含属性修饰符），不能用 getAttributeBaseValue
        float baseDamage = HallDifficultyAttributes.effectiveAttackDamage(this, 25.0F);

        if (this.getRandom().nextInt(100) < SWORD_AURA_CHANCE) {
            target.hurt(this.damageSources().mobAttack(this), baseDamage * SWORD_AURA_DAMAGE_MULT);
            SwordAuraEntity.spawn(level(), target.getBoundingBox().getCenter(),
                    SWORD_AURA_SCALE, 40, SWORD_AURA_SHRINK);
        }
        target.hurt(this.damageSources().mobAttack(this), baseDamage);
        return true;
    }

    // ------------------ 动画注册 ------------------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        AnimationController<AbstractHallEntity> movementController =
                new AnimationController<>(this, "movement_controller", 3, state -> {
                    if (isDeadOrDying() || isBurstAnimating()) return PlayState.STOP;
                    if (isActuallyMoving) {
                        if (hasWalkAnimation()) {
                            if (isWalking()) {
                                state.setControllerSpeed(getWalkAnimSpeed());
                                return state.setAndContinue(ANIM_WALK);
                            } else {
                                state.setControllerSpeed(getRunAnimSpeed());
                                return state.setAndContinue(ANIM_RUN);
                            }
                        }
                        return state.setAndContinue(ANIM_WALK);
                    }
                    return PlayState.STOP;
                });
        registrar.add(movementController);
        AnimationController<AbstractHallEntity> dieController =
                new AnimationController<>(this, "die_controller", 3, state ->
                        isBurstAnimating() ? state.setAndContinue(ANIM_DIE) : PlayState.STOP);
        AnimationController<AbstractHallEntity> idleController =
                new AnimationController<>(this, "idle_controller", 3, state ->
                        isAlive() ? state.setAndContinue(ANIM_IDLE) : PlayState.STOP);
        dieController.setAnimationSpeed(getDieAnimSpeed());
        registrar.add(idleController);
        registrar.add(dieController.triggerableAnim("die_trigger", ANIM_DIE));
    }

    // ------------------ 受伤 ------------------
    @Override
    public boolean hurt(DamageSource source, float amount) {
        this.lastCombatTick = this.tickCount;   // 受到攻击也视为进入战斗
        return super.hurt(source, amount);
    }

    // ------------------ 其他 ------------------
    @Override
    public boolean isPushable() {
        return false;
    }
}