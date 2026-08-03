package org.bytechen.hall.overworld.registry.entities.population.ecological;

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
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;
import org.bytechen.hall.utils.TickUtils;
import org.bytechen.hall.utils.entity.EntityBreakUtils;
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
        if (this.level() instanceof ClientLevel) {
            clientTick();
        } else {
            serverTick();
        }
        super.tick();
    }

    // ------------------ 服务端逻辑 ------------------
    private void serverTick() {
        // 原有：每 20 tick 破坏方块
        if (TickUtils.tickCountTimer(tickCount, 5)) {
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
            // 冲撞中：每 tick 破坏方块
            EntityBreakUtils.breakBlocksInRange(this, 1, 2f, false);

            // 向目标快速移动（速度倍率 1.5）
            if (target != null && target.isAlive()) {
                this.getNavigation().moveTo(target, 1.5D);
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
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0)
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
        float baseDamage = (float) getAttributeBaseValue(Attributes.ATTACK_DAMAGE);

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
                    if (isDeadOrDying() || isFakeDying()) return PlayState.STOP;
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
                        isFakeDying() ? state.setAndContinue(ANIM_DIE) : PlayState.STOP);
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