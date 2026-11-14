package org.bytechen.rottenruins.overworld.registry.entities.population.magic;


import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.overworld.registry.EntityTypeRegistry;
import org.bytechen.rottenruins.overworld.registry.RegisterItem;
import org.bytechen.rottenruins.gameplay.magic.spell.IMagicBaseItem;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * <b>审判之矛</b> —— 从天而降、笔直钉向指定位置的法术投射物：无重力、无阻尼（位移全程 {@code setPos}，
 * 一次都不调 {@code move()}），因此也<b>没有任何方块查询</b>（不 raycast，山体/基岩直接穿过）；
 * 命中生物不销毁、可反复吃伤害（穿体期间每 tick 一次，结算前清无敌帧）。继承 {@code Projectile}
 * 只为白拿 owner 的存取与存档（击杀归因靠它）。{@code updateInterval} 设为 1，否则 1.6 格/tick 会看出跳格。
 */
public class SpearOfJudgmentEntity extends Projectile implements ItemSupplier {

    private static final String TAG_DIRECTION = "HallSpearDirection";
    private static final String TAG_REMAINING = "HallSpearRemaining";
    private static final String TAG_DAMAGE_SCALE = "HallSpearDamageScale";
    private static final String TAG_SPELL = "HallSpearSpell";

    /** 飞行速度（格/tick）。1.6 = 32 格/秒：够快，读作"从天上钉下来"。 */
    public static final double SPEED = 1.6d;

    /** 判定用的圆柱半径（格）：以这一步的轨迹线段为轴。 */
    public static final double HIT_RADIUS = 0.8d;

    /** 安全存活上限（tick）：正常走完 {@link #remaining} 就结束，这条只是兜底防空矛。 */
    public static final int MAX_AGE = 200;

    /** 出生点：在主人<b>背后</b>多少格（沿主人身体朝向的反方向）。 */
    public static final double SPAWN_BACK_OFFSET = 5.0d;

    /** 出生点：在主人<b>上方</b>多少格（相对主人脚下的坐标）。 */
    public static final double SPAWN_UP_OFFSET = 5.0d;

    private static final DustParticleOptions JUDGMENT_DUST =
            new DustParticleOptions(new Vector3f(0.30f, 0.72f, 1.0f), 1.25f);

    /** 单位飞行方向。生成时定死，中途不变。 */
    private Vec3 direction = Vec3.ZERO;

    /** 还要飞多少格；减到 0 就到指定区域了。 */
    private double remaining;

    /** 伤害倍率（由法释放时按蓄力完成度传入）；{@code minChargeRatio = 1} 使当前恒为 1.0。 */
    private float damageScale = 1.0f;

    /** 释放这次法术的物品堆（命中时用来取伤害基值、附魔、事件上下文）。 */
    private ItemStack spellStack = ItemStack.EMPTY;

    private int age;
    private boolean finished;

    public SpearOfJudgmentEntity(@NotNull EntityType<? extends Projectile> type, @NotNull Level level) {
        super(type, level);
        this.noCulling = true;
        // 无重力：位移全程由 setPos 推进，一次都不调 move()，所以重力与阻尼都无从生效
        this.setNoGravity(true);
    }

    /**
     * 在主人<b>背后 5 格、上方 5 格</b>生成一根矛，朝 {@code aimPoint} 直线射出（落点距出生点 &lt; 0.5 格时返回 null）。
     * 生成即 {@code setOwner(caster)} 定死并随存档保存：击杀归因、命中豁免（{@link #isOwner}）、取法术数据都按它判；
     * 读档后主人离线时 {@code getOwner()} 为 null，伤害照打、只是没有归因（来源退成 {@code magic()}）。
     */
    @Nullable
    public static SpearOfJudgmentEntity spawn(@NotNull ServerPlayer caster, @NotNull ItemStack spellStack,
                                              @NotNull Vec3 aimPoint, float damageScale) {
        Level level = caster.level();

        // "背后"用<b>身体朝向（yaw）</b>算而不用视线方向：准星压到脚下（pitch -90°）时视线水平分量为 0。
        Vec3 forward = Vec3.directionFromRotation(0.0f, caster.getYRot());
        Vec3 start = caster.position()
                .subtract(forward.scale(SPAWN_BACK_OFFSET))   // 退到背后
                .add(0.0d, SPAWN_UP_OFFSET, 0.0d);            // 抬到上方

        Vec3 delta = aimPoint.subtract(start);
        double distance = delta.length();
        if (distance < 0.5d) {
            // 落点几乎就在出生点上（例如准星正对身后、而射程又很短）→ 没有可飞的距离
            return null;
        }
        Vec3 dir = delta.scale(1.0d / distance);

        SpearOfJudgmentEntity spear =
                new SpearOfJudgmentEntity(EntityTypeRegistry.SPEAR_OF_JUDGMENT.get(), level);
        spear.setPos(start.x, start.y, start.z);
        spear.setOwner(caster);
        spear.direction = dir;
        spear.remaining = distance;
        spear.damageScale = damageScale;
        spear.spellStack = spellStack.copy();

        // 朝向：billboard 渲染用不到，但位置包会带上 yaw/pitch（小地图、雷达、将来换真模型时读它）。
        float horizontal = Mth.sqrt((float) (dir.x * dir.x + dir.z * dir.z));
        spear.setYRot((float) (Mth.atan2(dir.x, dir.z) * (180.0d / Math.PI)));
        spear.setXRot((float) (Mth.atan2(dir.y, horizontal) * (180.0d / Math.PI)));

        level.addFreshEntity(spear);

        // 出手音：三叉戟的投掷音，正好是"矛"的味道
        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 0.9f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, start.x, start.y, start.z,
                    22, 0.5d, 0.5d, 0.5d, 0.025d);
        }
        return spear;
    }

    @Override
    public void tick() {
        super.tick();

        // 客户端不做逻辑：位置由服务端位置包同步（updateInterval = 1），不需要客户端预测。
        if (level().isClientSide()) {
            return;
        }

        if (finished) {
            discard();
            return;
        }
        if (++age > MAX_AGE) {
            finish();
            return;
        }

        double stepLength = Math.min(SPEED, remaining);
        Vec3 step = direction.scale(stepLength);

        // 先判定再位移：判定用"这一步扫过的线段"，一 tick 走 1.6 格也不会漏掉中间的目标
        hitAlongStep(step);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);

        remaining -= stepLength;
        spawnTrail();

        if (remaining <= 1.0e-4) {
            finish();
        }
    }

    // 伤害（穿透）

    /**
     * 结算这一步扫过的所有生物：用"碰撞箱沿这一步拉伸"的 AABB 当判定体（等价于以轨迹为轴的圆柱），
     * 走多长扫多长，高速穿过也不漏判。<b>刻意不做"一个目标只打一次"的去重</b> —— 穿体期间每 tick
     * 结算一次，体积越大挨得越多；因此每次结算前必须清无敌帧（见 {@link #damage}），否则只补差值 = 0 伤害。
     */
    private void hitAlongStep(@NotNull Vec3 step) {
        AABB sweep = getBoundingBox().expandTowards(step).inflate(HIT_RADIUS);
        List<LivingEntity> hits = level().getEntitiesOfClass(LivingEntity.class, sweep, this::canHit);
        for (LivingEntity target : hits) {
            damage(target);
        }
    }

    /** 能不能打：活着的生物、不是旁观、不是主人（自己放的矛不会扎到自己）。 */
    private boolean canHit(@NotNull LivingEntity target) {
        return !isOwner(target) && target.isAlive() && !target.isSpectator();
    }

    /**
     * 目标是不是<b>主人本人</b>：按 UUID 比而不按对象引用 —— {@code getOwner()} 是从 UUID 反查出来的，
     * 与"目标列表里的玩家实例"不保证同一引用，用 {@code ==} 比会让主人吃到自己的矛。
     */
    private boolean isOwner(@NotNull Entity target) {
        Entity owner = getOwner();
        return owner != null && owner.getUUID().equals(target.getUUID());
    }

    private void damage(@NotNull LivingEntity target) {
        IMagicBaseItem item = MagicStats.spellOf(spellStack);
        if (item == null) {
            return;
        }
        // 主人可能查不到（读档后不在线）——那时仍照打，只是没有归因：来源会退化成 magic()。
        ServerPlayer caster = ownerPlayer();

        // 无敌帧清零 + 红闪拉满，这是"能重复造成伤害"的前提：不清的话第二下会被
        // `invulnerableTime > 10` 分支吃掉（同数值只补差值 = 0）；退回原版 hurt 时那条路径也吃这个。
        SpellDamageUtil.prepareRepeatHit(target);

        // 基值 × 蓄力倍率 → 交给 MagicStats 折叠；spell_power 的倍率由 MagicDamageEvent 监听器乘，
        // 乘在这里会叠加成平方。
        float base = item.baseDamage() * damageScale;
        float amount = MagicStats.spellDamage(caster, spellStack, target, null, base);
        SpellDamageUtil.hurt(target, spellStack, caster, amount);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5d,
                    target.getZ(), 9, 0.3d, 0.35d, 0.3d, 0.08d);
        }
    }

    /** 主人；不是玩家（读档后主人不在线）时返回 {@code null}，调用方需要能接受它。 */
    @Nullable
    private ServerPlayer ownerPlayer() {
        return getOwner() instanceof ServerPlayer player ? player : null;
    }

    /** Cool light and short-lived motes trace the spear's actual path. */
    private void spawnTrail() {
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 wake = position().subtract(direction.scale(0.9d));
            serverLevel.sendParticles(JUDGMENT_DUST,
                    wake.x, wake.y, wake.z, 4, 0.16d, 0.16d, 0.16d, 0.015d);
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    wake.x, wake.y, wake.z, 2, 0.08d, 0.08d, 0.08d, 0.005d);
        }
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY(), getZ(), 45, 0.7d, 0.7d, 0.7d, 0.12d);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY(), getZ(), 25, 0.5d, 0.5d, 0.5d, 0.08d);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.9f, 0.9f);
        }
        discard();
    }

    @Override
    @NotNull
    public ItemStack getItem() {
        return new ItemStack(RegisterItem.SPEAR_OF_JUDGMENT.get());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    /** 本实体没有任何需要同步的字段：位置靠位置包，渲染靠固定贴图。 */
    @Override
    public void defineSynchedData() {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble(TAG_DIRECTION + "X", direction.x);
        tag.putDouble(TAG_DIRECTION + "Y", direction.y);
        tag.putDouble(TAG_DIRECTION + "Z", direction.z);
        tag.putDouble(TAG_REMAINING, remaining);
        tag.putFloat(TAG_DAMAGE_SCALE, damageScale);
        if (!spellStack.isEmpty()) {
            tag.put(TAG_SPELL, spellStack.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Vec3 dir = new Vec3(tag.getDouble(TAG_DIRECTION + "X"),
                tag.getDouble(TAG_DIRECTION + "Y"),
                tag.getDouble(TAG_DIRECTION + "Z"));
        // 存档里可能是零向量（旧档/异常）→ 退回"垂直向下"，总比原地卡住好
        direction = dir.lengthSqr() < 1.0e-8 ? new Vec3(0.0d, -1.0d, 0.0d) : dir.normalize();
        remaining = Math.max(0.0d, tag.getDouble(TAG_REMAINING));
        if (tag.contains(TAG_DAMAGE_SCALE)) {
            damageScale = tag.getFloat(TAG_DAMAGE_SCALE);
        }
        if (tag.contains(TAG_SPELL)) {
            spellStack = ItemStack.of(tag.getCompound(TAG_SPELL));
        }
    }

    public double getRemaining() {
        return remaining;
    }

    @NotNull
    public Vec3 getFlightDirection() {
        return direction;
    }
}
