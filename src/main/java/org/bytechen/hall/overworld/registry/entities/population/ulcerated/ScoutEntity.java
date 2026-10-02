package org.bytechen.hall.overworld.registry.entities.population.ulcerated;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
import org.bytechen.hall.overworld.registry.entities.ai.FaceTargetGoal;
import org.bytechen.hall.overworld.registry.entities.base.AbstractHallEntity;
import org.bytechen.hall.overworld.registry.entities.population.infected.BaseInfectedEntity;
import org.bytechen.infcore.api.event.EntityEvolveEvent;
import org.bytechen.infcore.api.goal.InfectedTargetGoal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * 溃烂斥候 —— 小型生物被感染后的兜底形态。
 * <p>
 * 用途：当作感染（{@code hall:inf}）作用于一个<b>没有专属感染形态</b>（进化表里查不到规则）
 * 且碰撞体积较小（宽 × 高 &lt; 2）的生物时，转化为斥候；
 * 体积在 2 ~ 8 之间的生物则会转化为 <b>2 只</b>斥候。
 * 判定与转化由 infcore 的感染流程
 * （{@code EvolutionManager#applyEvolution} → 兜底档位）完成，
 * 档位在 {@link UlceratedConversionRules#register()} 中注册。
 * <p>
 * 属性：生命 12、伤害 5、移动缓慢（0.15），碰撞箱 1.0 × 1.2，可以攀爬墙体。
 * <p>
 * 小机制：无索敌 20 秒后开始互相聚集（{@link GatherGoal}），聚满 5 只就在中心合体成一只巨碑。
 * <p>
 * 模型 / 贴图 / 动画：{@code geo/scout.geo.json}、{@code textures/entity/scout.png}、
 * {@code animations/scout.animation.json}（只有 idle 和 walk 两段动画）。
 */
public class ScoutEntity extends BaseUlceratedEntity {

    /** 攀爬时每 tick 的上升速度（格） */
    private static final double CLIMB_SPEED = 0.2D;
    /** 起爬门槛（格）：还没挂在墙上时，目标要高过自身该值才开始爬 */
    private static final double CLIMB_TRIGGER_HEIGHT = 0.5D;
    /** 目标的水平距离超过该值（格）时交给寻路，不做贴墙攀爬 */
    private static final double CLIMB_MAX_DISTANCE = 6.0D;
    /** 墙体探测范围（格） */
    private static final double CLIMB_PROBE_RANGE = 3.0D;
    /** 墙体探测步长（格） */
    private static final double CLIMB_PROBE_STEP = 0.25D;
    /** 判定「已贴墙」的探测距离（格） */
    private static final double CLIMB_CONTACT_DISTANCE = 0.5D;
    /** 探测碰撞箱的抬升量（格）——贴着脚下平面探测，避免把地面当成墙 */
    private static final double CLIMB_PROBE_LIFT = 0.1D;
    /** 越过墙沿后允许继续上蹭的 tick 数（用来翻上墙顶） */
    private static final int CLIMB_MOUNT_TICKS = 8;
    /** 贴墙攀爬时朝墙推进的速度（格/tick） */
    private static final double CLIMB_PRESS_SPEED = 0.12D;
    /** 还没贴到墙时朝目标方向靠近的速度（格/tick） */
    private static final double CLIMB_APPROACH_SPEED = 0.12D;

    /** 本 tick 是否正在攀爬（用于 {@link #onClimbable()}） */
    private boolean climbing;
    /** 前方失去墙体后的持续 tick 数（用于限制翻越墙顶的时间） */
    private int climbTransitionTicks;

    // ---------- 聚集 / 合体 ----------
    /** 连续无索敌多少 tick 后开始寻找同类聚集：20 秒 */
    private static final int GATHER_IDLE_TICKS = 20 * 20;
    /** 扫描同类间隔（tick）——再按实体 id 错开，避免同一 tick 集体扫描 */
    private static final int GATHER_SCAN_INTERVAL = 20;
    /** 搜索同类的水平半径（格） */
    private static final double GATHER_SEARCH_RADIUS = 12.0D;
    /** 搜索同类的垂直半径（格） */
    private static final double GATHER_SEARCH_HEIGHT = 6.0D;
    /** 合体需要的斥候数量 */
    private static final int MERGE_COUNT = 5;
    /** 判定「已聚到一起」的水平距离（格）：略大于拥挤时的实际间距 */
    private static final double MERGE_RADIUS = 4.0D;
    /** 判定「已聚到一起」允许的高度差（格） */
    private static final double MERGE_HEIGHT = 2.0D;
    /** 走到聚团中心点这么近就停下，避免互相推挤 */
    private static final double GATHER_HOLD_RADIUS = 1.2D;
    /** 聚集时的寻路速度倍率 */
    private static final double GATHER_PATH_SPEED = 1.0D;
    /** 寻路没动起来时直接推的速度（格/tick） */
    private static final double GATHER_PUSH_SPEED = 0.08D;
    /** 判定「没在动」的水平速度平方阈值 */
    private static final double GATHER_STUCK_SPEED_SQR = 1.0E-4D;
    /** 聚团中心点超出该距离（格）才重新寻路 */
    private static final double GATHER_REPATH_DISTANCE = 3.0D;

    /** 搜索半径平方（避免重复相乘） */
    private static final double GATHER_SEARCH_RADIUS_SQR = GATHER_SEARCH_RADIUS * GATHER_SEARCH_RADIUS;
    /** 聚在一起后的停止半径平方 */
    private static final double GATHER_HOLD_RADIUS_SQR = GATHER_HOLD_RADIUS * GATHER_HOLD_RADIUS;
    /** 判定聚到一起的距离平方 */
    private static final double MERGE_RADIUS_SQR = MERGE_RADIUS * MERGE_RADIUS;
    /** 重新寻路阈值平方 */
    private static final double GATHER_REPATH_SQR = GATHER_REPATH_DISTANCE * GATHER_REPATH_DISTANCE;

    /** 连续无索敌的 tick 数 */
    private int idleTicks;
    /** 上次为聚集寻路的目标点（用于避免频繁重算路径） */
    private double lastGatherPathX, lastGatherPathY, lastGatherPathZ;
    /** 当前聚团中心点（每 {@link #GATHER_SCAN_INTERVAL} tick 更新一次） */
    private double gatherCenterX, gatherCenterZ;
    /** 是否有可用的聚团中心点 */
    private boolean hasGatherCenter;
    /** 已加入一次合体：防止同一 tick 被两只斥候重复计入 */
    private boolean merging;

    public ScoutEntity(EntityType<? extends BaseInfectedEntity> entityType, Level level,
                       Consumer<AbstractHallEntity> consumer) {
        super(entityType, level, consumer);
        // 只有 idle / walk 两段动画，没有 run 动画
        this.setHasWalkAnim(false);
        // 移动缓慢
        this.setWalkSpeed(0.15F);
        this.setRunSpeed(0.15F);
        this.setIdleAnimSpeed(1.0F);
        this.setWalkAnimSpeed(1.0F);
        // 攻击节奏比普通感染生物更慢
        this.setDoHurtTime(25);
        this.setDoHurtDistance(1);
    }

    // ---------- 属性 ----------
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.15)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    // ---------- 目标与行为 ----------
    @Override
    protected void registerGoals() {
        // 索敌：与其它王庭生物一致 —— goalSelector 优先级 2，40 格内无需视线
        this.goalSelector.addGoal(2, new InfectedTargetGoal.Builder(this)
                .range(40.0)
                .mustSee(false)
                // 威胁点数过滤器：玩家威胁 < 5 不主动索敌；非玩家生物照常索敌
                .filter(this)
                .build());
        this.goalSelector.addGoal(2, new FaceTargetGoal(this, 4));
        // 无索敌 20 秒后开始聚集，聚满 5 只合体成巨碑
        this.goalSelector.addGoal(4, new GatherGoal());
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        super.registerGoals();
    }

    // ---------- 爬墙 ----------
    /**
     * 贴墙 / 攀爬时视为「可攀爬」（与蜘蛛一致），
     * 这样既不会被摔落伤害影响，下落速度也会被限制住。
     */
    @Override
    public boolean onClimbable() {
        return this.climbing || this.horizontalCollision;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        tickClimb();
        tickGatherIdle();
    }

    // ==================== 聚集 / 合体 ====================

    /** 无索敌计时：有目标就清零，否则每 tick +1（达到 {@link #GATHER_IDLE_TICKS} 后不再自增） */
    private void tickGatherIdle() {
        if (this.isFakeDying()) return;

        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            this.idleTicks = 0;
            return;
        }
        if (this.idleTicks < GATHER_IDLE_TICKS) this.idleTicks++;
    }

    /**
     * 聚集 goal：无索敌 20 秒后互相聚拢，聚满 {@link #MERGE_COUNT} 只就在中心合体成一只巨碑。
     * <p>
     * 占用 {@code MOVE} 标记，所以会自动顶掉随机游荡，不会和游荡互相抢路径。
     * 一旦重新找到目标，{@link #canUse()} 立即为 false → 立刻恢复追击/打架。
     */
    private class GatherGoal extends Goal {

        GatherGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (merging || isFakeDying() || isNoAi()) return false;
            if (idleTicks < GATHER_IDLE_TICKS) return false; // 无索敌还没到 20 秒
            LivingEntity target = getTarget();
            return target == null || !target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            tickGather();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /**
     * 聚集逻辑（由 {@link GatherGoal} 每 tick 调用）。
     * <p>
     * 性能要点：
     * <ul>
     *   <li>扫描间隔 {@link #GATHER_SCAN_INTERVAL} tick，并按实体 id 错开，避免同 tick 大批量扫描</li>
     *   <li>单次遍历求出聚团中心点，同时收集已聚到一起的同伴，不排序、够数即停</li>
     *   <li>只在路径走完或中心点明显移动后才重新寻路；走到中心点就停下不再寻路</li>
     *   <li>寻路没动起来时直接推一把（平地比寻路可靠），避免个别斥候卡住导致永远聚不齐</li>
     *   <li>合体一次性完成：5 只变 1 只，实体数净减少</li>
     * </ul>
     */
    private void tickGather() {
        if (this.merging || this.isFakeDying()) return;

        // 每 20 tick（按实体 id 错开）扫描一次：找同伴、判定合体、更新聚团中心点
        if ((this.tickCount + this.getId()) % GATHER_SCAN_INTERVAL == 0) {
            if (!scanIdleScouts()) return;
        }
        if (!this.hasGatherCenter) return;

        double dx = this.gatherCenterX - this.getX();
        double dz = this.gatherCenterZ - this.getZ();
        double distSqr = dx * dx + dz * dz;

        // 已经到达中心点 → 站住等别人过来
        if (distSqr <= GATHER_HOLD_RADIUS_SQR) {
            this.getNavigation().stop();
            this.hasGatherCenter = false;
            return;
        }

        if (this.getNavigation().isDone()
                || this.distanceToSqr(this.lastGatherPathX, this.lastGatherPathY, this.lastGatherPathZ)
                        > GATHER_REPATH_SQR) {
            this.getNavigation().moveTo(this.gatherCenterX, this.getY(), this.gatherCenterZ, GATHER_PATH_SPEED);
            this.lastGatherPathX = this.gatherCenterX;
            this.lastGatherPathY = this.getY();
            this.lastGatherPathZ = this.gatherCenterZ;
        }

        // 寻路没让它动起来 → 直接朝中心点推一把（撞墙时不推，交给寻路绕）
        if (this.getDeltaMovement().horizontalDistanceSqr() < GATHER_STUCK_SPEED_SQR
                && !this.horizontalCollision) {
            double dist = Math.sqrt(distSqr);
            this.setDeltaMovement(dx / dist * GATHER_PUSH_SPEED,
                    this.getDeltaMovement().y,
                    dz / dist * GATHER_PUSH_SPEED);
        }
    }

    /**
     * 扫描一次附近的同类：凑够 {@link #MERGE_COUNT} 只就合体，否则记录聚团中心点。
     *
     * @return 是否应该继续聚集（false = 附近没有同伴 / 已经合体）
     */
    private boolean scanIdleScouts() {
        AABB area = this.getBoundingBox().inflate(GATHER_SEARCH_RADIUS, GATHER_SEARCH_HEIGHT, GATHER_SEARCH_RADIUS);
        List<ScoutEntity> candidates =
                this.level().getEntitiesOfClass(ScoutEntity.class, area, this::isGatherable);

        double sumX = this.getX();
        double sumZ = this.getZ();
        int groupNum = 1; // 算上自己
        List<ScoutEntity> group = null;
        int groupSize = 1;
        for (ScoutEntity other : candidates) {
            double distSqr = this.horizontalDistanceSqrTo(other);
            if (distSqr > GATHER_SEARCH_RADIUS_SQR) continue;

            sumX += other.getX();
            sumZ += other.getZ();
            groupNum++;

            if (distSqr <= MERGE_RADIUS_SQR && Math.abs(other.getY() - this.getY()) <= MERGE_HEIGHT) {
                if (group == null) group = new ArrayList<>(MERGE_COUNT);
                group.add(other);
                if (++groupSize >= MERGE_COUNT) break; // 够了就不再遍历
            }
        }

        // 凑够 5 只 → 合体
        if (groupSize >= MERGE_COUNT && group != null) {
            this.hasGatherCenter = false;
            mergeIntoMonolith(group);
            return false;
        }

        // 附近没有同伴
        if (groupNum <= 1) {
            this.hasGatherCenter = false;
            return false;
        }

        this.gatherCenterX = sumX / groupNum;
        this.gatherCenterZ = sumZ / groupNum;
        this.hasGatherCenter = true;
        return true;
    }

    /** 是否是可聚集的同类：无索敌到时间、没在假死、也没被别的合体占用 */
    private boolean isGatherable(ScoutEntity other) {
        return other != this
                && !other.merging
                && !other.isFakeDying()
                && other.isAlive()
                && other.idleTicks >= GATHER_IDLE_TICKS;
    }

    private double horizontalDistanceSqrTo(Entity other) {
        double dx = other.getX() - this.getX();
        double dz = other.getZ() - this.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * 合体：消耗自己 + {@code group}（共 {@link #MERGE_COUNT} 只）在中心生成一只巨碑。
     * <p>
     * 先把所有参与者标记为 {@code merging}（同一 tick 内不会被别的斥候重复计入），
     * 巨碑放置成功后才回收参与者；失败则解除标记，大家继续聚。
     *
     * @param group 已聚在一起的同类，数量为 {@code MERGE_COUNT - 1}
     */
    private void mergeIntoMonolith(List<ScoutEntity> group) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // 二次确认：任何一只已被别的合体征用就放弃本次
        for (ScoutEntity scout : group) {
            if (scout.merging) return;
        }
        this.merging = true;
        for (ScoutEntity scout : group) {
            scout.merging = true;
        }

        // 中心点 & 是否保留持久化（有一只是持久化，巨碑就不该自然消失）
        double centerX = this.getX();
        double centerY = this.getY();
        double centerZ = this.getZ();
        boolean persistent = this.isPersistenceRequired();
        for (ScoutEntity scout : group) {
            centerX += scout.getX();
            centerY += scout.getY();
            centerZ += scout.getZ();
            persistent |= scout.isPersistenceRequired();
        }
        int total = group.size() + 1;
        centerX /= total;
        centerY /= total;
        centerZ /= total;

        Entity monolith = EntityTypeRegistry.MONOLITH.get().create(serverLevel);
        if (monolith == null) {
            unmarkMerging(group);
            return;
        }
        Vec3 spawnPos = findMergeSpawnPos(monolith, group, centerX, centerY, centerZ);
        monolith.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, this.getYRot(), 0.0F);
        if (persistent && monolith instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        if (!serverLevel.addFreshEntity(monolith)) {
            unmarkMerging(group);
            return;
        }

        // 反馈：粒子 + 血肉融合的音效
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                spawnPos.x, spawnPos.y + 1.5D, spawnPos.z,
                8, 0.6D, 0.6D, 0.6D, 0.02D);
        serverLevel.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
                SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.2F, 0.8F);

        // 回收参与者
        for (ScoutEntity scout : group) {
            scout.discard();
        }
        this.discard();

        // infcore 的转化通知：下游模组（例如 GodSickNeo 的全局阶段）可据此统计
        if (monolith instanceof LivingEntity livingResult) {
            MinecraftForge.EVENT_BUS.post(new EntityEvolveEvent(serverLevel, this, livingResult));
        }
    }

    private void unmarkMerging(List<ScoutEntity> group) {
        this.merging = false;
        for (ScoutEntity scout : group) {
            scout.merging = false;
        }
    }

    /** 找一个能放下巨碑的位置：优先中心点，其次各参与者脚下 */
    private Vec3 findMergeSpawnPos(Entity monolith, List<ScoutEntity> group, double x, double y, double z) {
        if (canStandAt(monolith, x, y, z)) return new Vec3(x, y, z);
        for (ScoutEntity scout : group) {
            if (canStandAt(monolith, scout.getX(), scout.getY(), scout.getZ())) {
                return new Vec3(scout.getX(), scout.getY(), scout.getZ());
            }
        }
        return new Vec3(x, y, z);
    }

    private boolean canStandAt(Entity entity, double x, double y, double z) {
        entity.moveTo(x, y, z);
        return !entity.level().getBlockCollisions(entity, entity.getBoundingBox()).iterator().hasNext();
    }

    /**
     * 爬墙：目标在自身上方时，先朝它靠过去，贴到墙后沿着墙面往上爬，最后翻上墙顶。
     * <p>
     * 只依赖目标高度与墙体探测，不依赖寻路
     * —— 站在塔上的目标对地面寻路来说是不可达的，寻路会停在墙脚不动。
     */
    private void tickClimb() {
        LivingEntity target = this.getTarget();
        boolean wasClimbing = this.climbing;
        this.climbing = false;

        if (target == null || !target.isAlive()) {
            this.climbTransitionTicks = 0;
            return;
        }

        // 已经挂在墙上时，只要目标还在上方就继续爬；在地面时则要求目标明显更高
        double heightDiff = target.getY() - this.getY();
        if (heightDiff <= (wasClimbing ? 0.0D : CLIMB_TRIGGER_HEIGHT)) {
            this.climbTransitionTicks = 0;
            return;
        }

        Vec3 dir = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        double horizontalDistSqr = dir.horizontalDistanceSqr();
        if (horizontalDistSqr < 1.0E-4D // 正处于目标正下方
                || horizontalDistSqr > CLIMB_MAX_DISTANCE * CLIMB_MAX_DISTANCE) { // 太远 → 交给寻路
            this.climbTransitionTicks = 0;
            return;
        }

        Vec3 flat = dir.normalize();
        Vec3 delta = this.getDeltaMovement();
        double wallDistance = probeWallDistance(flat);

        if (wallDistance >= 0.0D) {
            this.climbTransitionTicks = 0;
            if (wallDistance <= CLIMB_CONTACT_DISTANCE) {
                climbUp(flat, delta); // 贴墙 → 向上爬
            } else {
                // 还没贴到墙 → 朝目标方向走过去
                this.setDeltaMovement(flat.x * CLIMB_APPROACH_SPEED, delta.y, flat.z * CLIMB_APPROACH_SPEED);
            }
            return;
        }

        // 前方已经没有墙：刚爬过墙沿的话再上蹭几下，好翻上墙顶
        if (wasClimbing && !this.onGround() && this.climbTransitionTicks < CLIMB_MOUNT_TICKS) {
            this.climbTransitionTicks++;
            climbUp(flat, delta);
        }
    }

    /** 贴墙向上爬：朝墙推进 + 上升，并清空摔落距离 */
    private void climbUp(Vec3 flat, Vec3 delta) {
        this.setDeltaMovement(flat.x * CLIMB_PRESS_SPEED,
                Math.max(delta.y, CLIMB_SPEED),
                flat.z * CLIMB_PRESS_SPEED);
        this.fallDistance = 0.0F;
        this.climbing = true;
    }

    /**
     * 沿水平方向探测前方是否有墙体（从脚下平面开始探）。
     *
     * @param dir 水平单位方向
     * @return 探测到的墙体距离（格），前方没有墙则返回 {@code -1}
     */
    private double probeWallDistance(Vec3 dir) {
        for (double d = CLIMB_PROBE_STEP; d <= CLIMB_PROBE_RANGE; d += CLIMB_PROBE_STEP) {
            AABB probe = this.getBoundingBox().move(dir.x * d, CLIMB_PROBE_LIFT, dir.z * d);
            if (!this.level().noCollision(this, probe)) return d;
        }
        return -1.0D;
    }

    // ---------- 普通攻击 ----------
    @Override
    protected boolean getAttack(LivingEntity target) {
        float baseDamage = (float) getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        target.hurt(this.damageSources().mobAttack(this), baseDamage);
        return true;
    }
}
