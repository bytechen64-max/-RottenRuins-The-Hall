package org.bytechen.hall.overworld.registry.entities.population.infected;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.client.rend.gui.GuiShaderManager;
import org.bytechen.hall.overworld.difficulty.HallDifficultyAttributes;
import org.bytechen.hall.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.population.skills.SwordAuraEntity;
import org.bytechen.hall.utils.TickUtils;
import org.bytechen.hall.utils.entity.EntityBreakUtils;
import org.bytechen.hall.utils.entity.EntityParticleUtils;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.UUID;
import java.util.function.Consumer;

public class InfPlayerEntity extends BaseInfectedEntity {


    private static final EntityDataAccessor<Integer> MADNESS_COOLDOWN =
            SynchedEntityData.defineId(InfPlayerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEAP_COOLDOWN =
            SynchedEntityData.defineId(InfPlayerEntity.class, EntityDataSerializers.INT);

    /** 大跳冷却（tick），15s */
    private static final int LEAP_COOLDOWN_MAX = 300;
    /** 大跳触发范围（半径格数） */
    private static final float LEAP_RANGE = 8.0f;
    /** 大跳垂直初速度 */
    private static final float LEAP_UPWARD = 0.7f;
    /** 大跳水平速度系数 */
    private static final float LEAP_H_SPEED = 0.18f;

    private int getMadnessCooldown()  { return entityData.get(MADNESS_COOLDOWN); }
    private void setMadnessCooldown(int v) { entityData.set(MADNESS_COOLDOWN, v); }
    private int getLeapCooldown()     { return entityData.get(LEAP_COOLDOWN); }
    private void setLeapCooldown(int v) { entityData.set(LEAP_COOLDOWN, v); }

    public InfPlayerEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level, Consumer<AbstractHallEntity> consumer) {
        super(entityType, level);
        consumer.accept(this);
        this.setHasWalkAnim(true);
        this.setWalkSpeed(0.32f);
        this.setRunSpeed(0.5F);
        this.setDoHurtTime(20);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 15)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE,0.8f);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1, true));

        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0)
                .mustSee(false)
                .filter(this)
                .build());
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(2, new FaceTargetGoal(this,4));
        super.registerGoals();
    }


    /** 剑气触发概率（百分比） */
    private static final int SWORD_AURA_CHANCE = 5;
    /** 剑气缩小速度倍率 */
    private static final float SWORD_AURA_SHRINK = 2.0f;
    /** 剑气伤害倍率 */
    private static final float SWORD_AURA_DAMAGE_MULT = 5.0f;
    /** 剑气缩放倍率 */
    private static final float SWORD_AURA_SCALE = 1.5f;

    /** GUI horror shader effect: total duration (ticks, ~5s) */
    private static final int HORROR_SHADER_DURATION = 100;
    /** GUI horror shader effect: fade-in (ticks) */
    private static final int HORROR_SHADER_FADE_IN = 10;
    /** GUI horror shader effect: fade-out (ticks) */
    private static final int HORROR_SHADER_FADE_OUT = 30;

    @Override
    protected boolean getAttack(LivingEntity target) {
        // 必须走 effectiveAttackDamage（含属性修饰符），不能用 getAttributeBaseValue
        float baseDamage = HallDifficultyAttributes.effectiveAttackDamage(this, 15.0F);

        if (this.getRandom().nextInt(100) < SWORD_AURA_CHANCE) {
            // 5% 概率：在目标碰撞箱中心生成剑气，1.5 倍大小，5 倍伤害
            target.hurt(this.damageSources().mobAttack(this), baseDamage * SWORD_AURA_DAMAGE_MULT);
            SwordAuraEntity.spawn(level(), target.getBoundingBox().getCenter(),
                    SWORD_AURA_SCALE, 40, SWORD_AURA_SHRINK);
        } else {
            target.hurt(this.damageSources().mobAttack(this), baseDamage);
        }

        // 攻击玩家时发送全屏 GUI 恐怖着色器特效
        if (target instanceof ServerPlayer player && !level().isClientSide()) {
            NetworkHelper.sendGuiShaderToPlayer(player,
                    GuiShaderManager.TYPE_HORROR_VORONOI,
                    UUID.randomUUID(),
                    HORROR_SHADER_DURATION,
                    HORROR_SHADER_FADE_IN,
                    HORROR_SHADER_FADE_OUT);
        }

        return true;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if(target!=this.getTarget())
        {
            setMadnessCooldown(100);
        }

        super.setTarget(target);
    }

    @Override
    public void tick() {
        //Client
        if(this.level() instanceof ClientLevel)
        {
            clientTick();
        }
        else
        {
            serverTick();
        }
        setMadnessCooldown(getMadnessCooldown() - 1);
        setLeapCooldown(getLeapCooldown() - 1);
        super.tick();
    }

    public void clientTick()
    {
        if(TickUtils.tickCountTimer(this.tickCount,10)&&getMadnessCooldown()>0)
        {
            EntityParticleUtils.spawnParticles(this, ParticleTypes.END_ROD,0.2
                    ,0.2 , 1.0f);
        }
    }

    public void serverTick()
    {
        if (TickUtils.tickCountTimer(tickCount, 20)) {
            EntityBreakUtils.breakBlocksInRange(this, 1, 2f, false);
        }
        // 大跳逻辑：8 格内有目标且冷却完毕时触发
        LivingEntity target = getTarget();
        if (target != null && getLeapCooldown() <= 0 && this.onGround()
                && this.distanceToSqr(target) <= LEAP_RANGE * LEAP_RANGE) {
            performLeap(target);
        }

        if (TickUtils.tickCountTimer(tickCount, 40) && target != null) {
            setMadnessCooldown(100);
        }
    }

    /** 大跳至目标位置或目标后方 */
    private void performLeap(LivingEntity target) {
        Vec3 dest;
        if (this.getRandom().nextBoolean()) {
            // 落点 = 目标位置
            dest = target.position();
        } else {
            // 落点 = 目标背后 2.5 格
            Vec3 look = target.getLookAngle().scale(-2.5);
            dest = target.position().add(look.x, 0, look.z);
        }

        Vec3 diff = dest.subtract(this.position());
        // 计算水平速度使落点距离匹配
        double hDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        double hSpeed = Math.min(hDist * LEAP_H_SPEED, 2.0);
        Vec3 vel = new Vec3(diff.x * LEAP_H_SPEED, LEAP_UPWARD, diff.z * LEAP_H_SPEED);
        this.setDeltaMovement(vel);
        this.hurtMarked = true; // 通知客户端

        // 面向目标
        this.getLookControl().setLookAt(target, 30, 30);
        this.yBodyRot = this.getYRot();

        setLeapCooldown(LEAP_COOLDOWN_MAX);

        // 起跳粒子
        if (level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 20; i++) {
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        getX() + (random.nextDouble() - 0.5) * 0.6,
                        getY() + 0.1,
                        getZ() + (random.nextDouble() - 0.5) * 0.6,
                        1, 0, 0.05, 0, 0.02);
            }
        }
    }


    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MADNESS_COOLDOWN, 0);
        this.entityData.define(LEAP_COOLDOWN, 0);
    }

    protected static final RawAnimation ANIM_IDLE_MADNESS  = RawAnimation.begin().thenPlay("idle_madness");


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        registrar.add(new AnimationController<>(this, "movement_controller", 3, state -> {
            if (isDeadOrDying() || isFakeDying()) return PlayState.STOP;
            if (!isActuallyMoving) { state.setControllerSpeed(getIdleAnimSpeed()); return this.entityData.get(MADNESS_COOLDOWN) > 0 ? state.setAndContinue(ANIM_IDLE_MADNESS) : state.setAndContinue(ANIM_IDLE); }
            if (hasWalkAnimation()) {
                if (isWalking()) { state.setControllerSpeed(getWalkAnimSpeed()); return state.setAndContinue(ANIM_WALK); }
                else { state.setControllerSpeed(getRunAnimSpeed()); return state.setAndContinue(ANIM_RUN); }
            }
            state.setControllerSpeed(getWalkAnimSpeed());
            return state.setAndContinue(ANIM_WALK);
        }));
        AnimationController<AbstractHallEntity> dieController =
                new AnimationController<>(this, "die_controller", 3, state ->
                        (isFakeDying() && shouldBurstPlayAnim()) ? state.setAndContinue(ANIM_DIE) : PlayState.STOP);
        dieController.setAnimationSpeed(getDieAnimSpeed());
        registrar.add(dieController.triggerableAnim("die_trigger", ANIM_DIE));
    }


}
