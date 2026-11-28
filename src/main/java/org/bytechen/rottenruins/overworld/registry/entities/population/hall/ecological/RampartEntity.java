package org.bytechen.rottenruins.overworld.registry.entities.population.hall.ecological;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import org.bytechen.rottenruins.api.infection.goal.InfectedTargetGoal;
import org.bytechen.rottenruins.overworld.difficulty.HallDifficultyAttributes;
import org.bytechen.rottenruins.overworld.registry.entities.ai.ChaseLockGoal;
import org.bytechen.rottenruins.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.rottenruins.overworld.registry.entities.base.AbstractRottenEntity;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;
import org.bytechen.rottenruins.utils.combat.RangeAttackCallback;
import org.bytechen.rottenruins.utils.combat.RangeAttackUtils;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** 小 Boss：常态举盾，斧击可暂时破盾；攻击起手后固定方向。 */
public class RampartEntity extends BaseEcologicalEntity {
    public static final int SHIELD_BREAK_TICKS = 100;
    public static final int COMBAT_TIMEOUT_TICKS = 200;
    private static final int ANIMATION_TRANSITION_TICKS = 6;
    private static final int SHIELD_TRANSITION_TICKS = 8;
    // GeckoLib 先完成过渡再推进动画时间，命中与攻击总时长都需补上这 6 tick。
    // 动画内的命中点保持不变：1.46 秒量化为 29 tick，挥拳为 20 tick。
    private static final int SLAM_HIT_TICKS = Math.round(1.46F * 20);
    private static final int PUNCH_HIT_TICKS = 20;
    private static final int SLAM_DURATION_TICKS = 35;
    private static final int PUNCH_DURATION_TICKS = 34;
    // 重击（attack3）总长 4.33 秒，1.75 秒与 3.75 秒各一次判定，第二次附带晕眩。
    private static final int HEAVY_HIT_TICKS = 35;
    private static final int HEAVY_STUN_TICKS = 75;
    private static final int HEAVY_DURATION_TICKS = 87;
    private static final int HEAVY_STUN_DURATION = 100;
    private static final float HEAVY_RADIUS = 5.0F;
    private static final int HEAVY_ANGLE = 120;
    /** 第二次判定掀起来的地面方块数量上限与初速。 */
    private static final int LIFT_LIMIT = 18;
    private static final double LIFT_SPEED_MIN = 0.34d;
    private static final double LIFT_SPEED_MAX = 0.66d;
    private static final EntityDataAccessor<Boolean> SHIELDING = SynchedEntityData.defineId(RampartEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(RampartEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ATTACK_YAW = SynchedEntityData.defineId(RampartEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation SHIELD = RawAnimation.begin().thenLoop("shield");
    private static final RawAnimation UNSHIELD = RawAnimation.begin().thenLoop("unshield");
    // 当前动画导出只有合并后的 idle（原来的 idle_body / idle_action 已并入），
    // 两个控制器都指向它：底层待机常驻，攻击控制器停止时自然回落到同一姿态。
    private static final RawAnimation IDLE_BODY = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation IDLE_ACTION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation PUNCH = RawAnimation.begin().thenPlay("attack2");
    private static final RawAnimation HEAVY = RawAnimation.begin().thenPlay("attack3");

    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private int shieldBreakTicks;
    private int combatTicks;
    private int attackTicks;
    private int hitTicks;
    private int stunTicks;
    private boolean slam;
    private boolean heavy;
    private Vec3 attackDirection = Vec3.ZERO;
    private Player shieldAttacker;

    public RampartEntity(EntityType<? extends BaseEcologicalEntity> type, Level level, Consumer<AbstractRottenEntity> resources) {
        super(type, level);
        resources.accept(this);
        setHasWalkAnim(false); // 仅有 walk，无 run。
        setWalkSpeed(0.31625F);
        setRunSpeed(0.31625F);
        setDoHurtDistance(3);
        setDoHurtTime(50);
        bossEvent.setVisible(false);
        moveControl = new MoveControl(this) {
            @Override public void tick() {
                if (isAttacking()) { setSpeed(0); return; }
                super.tick();
            }
        };
        lookControl = new LookControl(this) {
            @Override public void tick() {
                if (!isAttacking()) super.tick();
            }
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300)
                .add(Attributes.MOVEMENT_SPEED, 0.31625)
                .add(Attributes.ATTACK_DAMAGE, 20)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SHIELDING, true);
        entityData.define(ATTACKING, false);
        entityData.define(ATTACK_YAW, 0.0F);
    }

    public boolean isShielding() { return entityData.get(SHIELDING); }
    public boolean isAttacking() { return entityData.get(ATTACKING); }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
        // 拒绝碰撞施加的推力，主动行走仍由移动控制器正常驱动。
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new ChaseLockGoal(this));
        goalSelector.addGoal(2, new FaceTargetGoal(this, 40) {
            @Override public void tick() { if (!isAttacking()) super.tick(); }
        });
        targetSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .targetOtherInfected(true).range(40).filter(this).build());
        goalSelector.addGoal(5, new RandomStrollGoal(this, 1));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        super.registerGoals();
    }

    @Override
    protected void tickChase() {
        if (isAttacking()) { getNavigation().stop(); return; }
        super.tickChase();
    }

    @Override
    protected boolean getAttack(LivingEntity target) {
        if (isAttacking() || isFakeDying() || isDeadOrDying()) return false;
        slam = random.nextBoolean();
        heavy = !slam && random.nextFloat() < 0.5F;
        attackDirection = horizontalDirectionTo(target);
        float yaw = (float) (Mth.atan2(attackDirection.z, attackDirection.x) * Mth.RAD_TO_DEG) - 90;
        entityData.set(ATTACK_YAW, yaw);
        entityData.set(ATTACKING, true);
        lockAttackRotation();
        getNavigation().stop();
        setSpeed(0);
        hitTicks = ANIMATION_TRANSITION_TICKS + (heavy ? HEAVY_HIT_TICKS : slam ? SLAM_HIT_TICKS : PUNCH_HIT_TICKS);
        stunTicks = heavy ? ANIMATION_TRANSITION_TICKS + HEAVY_STUN_TICKS : 0;
        attackTicks = ANIMATION_TRANSITION_TICKS + (heavy ? HEAVY_DURATION_TICKS : slam ? SLAM_DURATION_TICKS : PUNCH_DURATION_TICKS);
        combatTicks = COMBAT_TIMEOUT_TICKS;
        triggerAnim("attack_controller", heavy ? "attack3" : slam ? "attack" : "attack2");
        return true;
    }

    /** 重击的两次判定：前方扇形 5 格内 20 伤害，第二次命中额外挂 5 秒晕眩。 */
    private void performHeavyHit(boolean withStun) {
        RangeAttackUtils.Builder builder = RangeAttackUtils.sector()
                .origin(position().add(0, 1.5, 0)).direction(attackDirection)
                .radius(HEAVY_RADIUS).angleDegrees(HEAVY_ANGLE)
                .verticalRange(2.0F).requireLineOfSight(true)
                .damage(HallDifficultyAttributes.effectiveAttackDamage(this, 20))
                .filter(this::canHitWithRangeAttack);
        if (!withStun) {
            RangeAttackUtils.perform(this, builder);
            return;
        }
        RangeAttackUtils.perform(this, builder, RangeAttackCallback.ofAfterHit(
                (target, hurt) -> CustomEffects.addEffect(target, RegisterCustomEffect.STUN, HEAVY_STUN_DURATION, 0)));
        // 第二次落点把前方的地面掀起来（只掀真实地表，落地后原样铺回）。
        riseGroundBlocks();
    }

    /**
     * 前方扇形内的地表方块被震离地面：去掉本体后生成同款下落方块，给它一个向上的初速，
     * 落地时自然铺回原处。{@code dropItem = false} 保证即使落点被占也不会掉物品。
     */
    private void riseGroundBlocks() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 forward = new Vec3(attackDirection.x, 0, attackDirection.z);
        if (forward.lengthSqr() < 1.0E-4d) return;
        forward = forward.normalize();
        Set<BlockPos> seen = new HashSet<>();
        int raised = 0;
        for (int ring = 0; ring < 3 && raised < LIFT_LIMIT; ring++) {
            double radius = 1.6d + ring * 1.5d;
            int samples = 12 + ring * 6;
            for (int index = 0; index < samples && raised < LIFT_LIMIT; index++) {
                double angle = (index + ring * 0.37d) / samples * Math.PI * 2.0d;
                Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                // 与朝向夹角超过约 56° 的采样点跳过，只留下前方扇形。
                if (dir.dot(forward) < 0.55d) continue;
                Vec3 sample = position().add(dir.x * radius, 0, dir.z * radius);
                var hit = server.clip(new ClipContext(sample.add(0, 1.5, 0), sample.add(0, -2.5, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) continue;
                BlockPos pos = hit.getBlockPos();
                if (!seen.add(pos)) continue;
                BlockState state = server.getBlockState(pos);
                // 跳过不可破坏、带方块实体、非实心、隐形（barrier）与含流体的方块。
                if (state.getDestroySpeed(server, pos) < 0) continue;
                if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) continue;
                if (!state.isSolidRender(server, pos)) continue;
                if (state.getRenderShape() == RenderShape.INVISIBLE) continue;
                if (!server.removeBlock(pos, false)) continue;
                raised++;
                FallingBlockEntity block = FallingBlockEntity.fall(server, pos, state);
                block.dropItem = false;
                block.setDeltaMovement(0.0d, LIFT_SPEED_MIN + random.nextDouble() * (LIFT_SPEED_MAX - LIFT_SPEED_MIN), 0.0d);
                // 起跳瞬间向上抛一把碎屑，让"被掀起"的读感更明确。
                var particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
                Vec3 surface = hit.getLocation().add(0, 0.1, 0);
                for (int shard = 0; shard < 4; shard++) {
                    server.sendParticles(particle, surface.x, surface.y, surface.z, 0,
                            (random.nextDouble() - 0.5d) * 0.18d,
                            0.28d + random.nextDouble() * 0.3d,
                            (random.nextDouble() - 0.5d) * 0.18d, 1);
                }
            }
        }
        if (raised > 0) {
            playSound(SoundEvents.STONE_BREAK, 1.1F, 0.55F);
        }
    }

    private void lockAttackRotation() {
        float yaw = entityData.get(ATTACK_YAW);
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setXRot(0);
    }

    @Override
    public void tick() {
        // 先推进旧攻击，再由 super.tick 发起新攻击，避免起手当 tick 偷减一次。
        if (!level().isClientSide) {
            if (shieldBreakTicks > 0 && --shieldBreakTicks == 0) entityData.set(SHIELDING, true);
            if (combatTicks > 0) combatTicks--;
            if (isDeadOrDying() || isFakeDying() || isNoAi()) {
                attackTicks = hitTicks = stunTicks = 0;
                entityData.set(ATTACKING, false);
            } else if (attackTicks > 0) {
                if (hitTicks > 0 && --hitTicks == 0) {
                    if (heavy) {
                        performHeavyHit(false);
                    } else {
                        if (slam) spawnSlamGroundParticles();
                        RangeAttackUtils.perform(this, RangeAttackUtils.sector()
                                .origin(position().add(0, 1.5, 0)).direction(attackDirection)
                                .radius(slam ? 6.0F : 5.5F).angleDegrees(slam ? 100 : 140)
                                .verticalRange(2.0F).requireLineOfSight(true)
                                .damage(HallDifficultyAttributes.effectiveAttackDamage(this, 20) * (slam ? 1.25F : 1.0F))
                                .filter(this::canHitWithRangeAttack));
                    }
                }
                if (stunTicks > 0 && --stunTicks == 0) performHeavyHit(true);
                if (--attackTicks == 0) entityData.set(ATTACKING, false);
            }
        }
        super.tick();
        if (isAttacking()) lockAttackRotation();
        if (!level().isClientSide) {
            if (getTarget() != null && getTarget().isAlive()) combatTicks = COMBAT_TIMEOUT_TICKS;
            if (combatTicks == 0) shieldAttacker = null;
            bossEvent.setName(getDisplayName());
            bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0, 1));
            bossEvent.setVisible(isAlive() && !isFakeDying() && combatTicks > 0);
        }
    }

    /** 下砸落点周围向外、向上溅出地表碎屑；仅表现，不破坏方块。 */
    private void spawnSlamGroundParticles() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 center = position().add(attackDirection.scale(2.5));
        // 三圈共 24 个采样点，逐点寻找真实表面，兼容斜坡、台阶和半砖。
        for (int ring = 0; ring < 3; ring++) {
            double radius = 0.6 + ring;
            for (int point = 0; point < 8; point++) {
                double angle = (point + ring * 0.5) * Math.PI / 4;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                Vec3 sample = center.add(dx * radius, 0, dz * radius);
                var hit = server.clip(new ClipContext(sample.add(0, 1, 0), sample.add(0, -3, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) continue;
                var block = server.getBlockState(hit.getBlockPos());
                if (block.getRenderShape() == RenderShape.INVISIBLE) continue;
                var particle = new BlockParticleOption(ParticleTypes.BLOCK, block);
                Vec3 surface = hit.getLocation().add(0, 0.05, 0);
                for (int shard = 0; shard < 3; shard++) {
                    double speed = 0.12 + random.nextDouble() * 0.16;
                    // count=0 让原版按指定速度发射一个粒子，实现向外飞溅，而不是随机烟团。
                    server.sendParticles(particle, surface.x, surface.y, surface.z, 0,
                            dx * speed, 0.2 + random.nextDouble() * 0.25, dz * speed, 1);
                }
            }
        }
    }

    @Override
    protected boolean isRetaliationTarget(Player player) {
        return (combatTicks > 0 && shieldAttacker == player) || super.isRetaliationTarget(player);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || amount <= 0 || isInvulnerableTo(source) || isDeadOrDying() || isFakeDying()) return false;
        if (source.getEntity() instanceof LivingEntity attacker && attacker != this) {
            combatTicks = COMBAT_TIMEOUT_TICKS;
            // 格挡也触发还手，避免低威胁玩家打盾后无法进入战斗。
            if (attacker instanceof Player player && !isIgnoredPlayer(player)) shieldAttacker = player;
            setTarget(attacker);
        }
        // 保留 /kill、虚空等管理性伤害的原版语义。
        if (isShielding() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof Player player
                    && (player.getMainHandItem().getItem() instanceof AxeItem
                    || player.getMainHandItem().canPerformAction(ToolActions.AXE_DIG))) {
                shieldBreakTicks = SHIELD_BREAK_TICKS;
                entityData.set(SHIELDING, false);
                playSound(SoundEvents.SHIELD_BREAK, 1, 0.8F);
            } else {
                // 投射物使用直接伤害位置，不能按远处射手的位置判定来袭方向。
                Vec3 position = source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity()
                        ? source.getDirectEntity().position() : source.getSourcePosition();
                if (position != null) {
                    Vec3 incoming = position.subtract(position()).multiply(1, 0, 1);
                    Vec3 facing = Vec3.directionFromRotation(0, yBodyRot);
                    if (incoming.dot(facing) >= 0) {
                        playSound(SoundEvents.SHIELD_BLOCK, 1, 0.8F);
                        return false;
                    }
                }
                amount *= 0.1F;
            }
        }
        return super.hurt(source, amount);
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override public void remove(RemovalReason reason) {
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ShieldBreakTicks", shieldBreakTicks);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        shieldBreakTicks = Mth.clamp(tag.getInt("ShieldBreakTicks"), 0, SHIELD_BREAK_TICKS);
        entityData.set(SHIELDING, shieldBreakTicks == 0);
    }

    @Override
    public double getBoneResetTime() {
        return 8;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        if (!defaultControllersEnabled) return;
        // 非攻击骨骼持续待机，walk 与盾姿态仍独立叠加。
        registrar.add(new AnimationController<>(this, "idle_controller", ANIMATION_TRANSITION_TICKS,
                state -> isAlive() ? state.setAndContinue(IDLE_BODY) : PlayState.STOP));
        registrar.add(new AnimationController<>(this, "movement_controller", ANIMATION_TRANSITION_TICKS,
                state -> isAlive() && !isFakeDying() && !isAttacking() && isActuallyMoving
                        ? state.setAndContinue(ANIM_WALK) : PlayState.STOP));
        registrar.add(new AnimationController<>(this, "shield_controller", SHIELD_TRANSITION_TICKS,
                state -> isAlive() ? state.setAndContinue(isShielding() ? SHIELD : UNSHIELD) : PlayState.STOP));
        // 共用骨骼的待机和攻击必须由同一控制器播放：攻击结束后显式切回待机，
        // 让 GeckoLib 对末帧和待机首帧做过渡，而不是 STOP 后立刻被另一层接管。
        registrar.add(new AnimationController<>(this, "attack_controller", ANIMATION_TRANSITION_TICKS,
                state -> isAlive() ? state.setAndContinue(IDLE_ACTION) : PlayState.STOP)
                .triggerableAnim("attack", SLAM).triggerableAnim("attack2", PUNCH).triggerableAnim("attack3", HEAVY));
    }
}
