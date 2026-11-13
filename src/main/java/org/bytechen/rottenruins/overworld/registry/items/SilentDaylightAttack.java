package org.bytechen.rottenruins.overworld.registry.items;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.compat.BCCoreCompat;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.SwordAuraEntity;
import org.bytechen.rottenruins.utils.ModUtils;
import org.bytechen.rottenruins.utils.SpellDamageUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import static net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;

/**
 * 寂寒白日 · 左键三连斩（由 {@code AttackEntityEvent} 驱动，原版那一刀本身保留）。
 * 每一斩出刀光并在中心 {@value #SLASH_RANGE} 格球内对所有生物结算：百分比改血走 {@code BCCoreCompat}
 * （无视护甲 / 无敌帧 / 伤害事件），冰伤走 {@link SpellDamageUtil} 包装层（护甲与别的模组减伤照常生效）。
 * 三斩跨 12 tick 而武器没有每 tick 回调，用 {@link TickEvent.ServerTickEvent} 队列调度，
 * 队列存 <b>UUID</b> 而非实体引用：目标被卸载时按 UUID 查不到就自然丢弃。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SilentDaylightAttack {

    public static final int SLASH_INTERVAL = 6;

    public static final int SLASH_COUNT = 3;

    /** 每一斩的<b>基准</b>冰伤（走包装层，吃护甲 / 事件）；实际值按攻击力等比放大。 */
    public static final float ICE_DAMAGE = 18.0F;

    /** 基准攻击力（寂寒白日 8 点 + 玩家基础 1 点）：用它把 ATTACK_DAMAGE 归一化成倍率，拿本剑时正好 1.0。 */
    public static final double REFERENCE_ATTACK_DAMAGE = 9.0D;

    /** 倍率上限：ATTACK_DAMAGE 能被别的模组叠到几十上百，不封顶三斩冰伤会失控。 */
    public static final double MAX_ATTACK_SCALE = 10.0D;

    /** 冰伤 = {@link #ICE_DAMAGE} × (玩家攻击力 / {@link #REFERENCE_ATTACK_DAMAGE})，倍率钳在 {@code [0, MAX]}；读不到时退回基准值。 */
    public static float iceDamageOf(@Nullable net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return ICE_DAMAGE;
        }
        double attack;
        try {
            attack = player.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        } catch (Throwable t) {
            return ICE_DAMAGE;
        }
        if (!Double.isFinite(attack) || attack <= 0.0D) {
            return ICE_DAMAGE;
        }
        double scale = Math.min(MAX_ATTACK_SCALE, attack / REFERENCE_ATTACK_DAMAGE);
        return (float) (ICE_DAMAGE * scale);
    }

    /** 每一斩的改血百分比（目标最大生命值，下限 1 点）：直接写血量，无视护甲 / 抗性 / 无敌帧。 */
    public static final float PERCENT_HEALTH = 5.0F;

    public static final float NORMAL_ATTACK_PERCENT = 5.0F;

    public static final float SLASH_HEIGHT = 14.0F;

    /** 只影响视觉（{@code 0.19 × 1.45 ≈ 0.28} 格半宽 / 14 格长）；判定半径是独立的 {@link #SLASH_RANGE}。 */
    public static final float SLASH_RADIUS = 0.19F;

    public static final int SLASH_SPARK_STEPS = 5;

    /** 一斩的伤害半径（格），以目标碰撞箱中心为球心：刀光是随机倾斜的线，球形判定才稳定。 */
    public static final double SLASH_RANGE = 4.5D;

    /** 待结算的一斩；{@code ticksLeft = 0} 表示本次服务端 tick 结束时结算（所以第一斩与挥刀同一 tick 落地）。 */
    private record PendingSlash(UUID playerId, UUID targetId, int ticksLeft, int index) {
    }

    private static final List<PendingSlash> PENDING = new ArrayList<>();

    /** "改血引擎不可用"的警告只打一次 —— 否则每一斩都会刷一屏日志。 */
    private static boolean warnedNoEngine;

    private SilentDaylightAttack() {
    }

    /** 玩家挥刀（{@code AttackEntityEvent}）：马上结算普通攻击的 5% 改血，并把三斩排进队列。 */
    public static void onAttackLanded(ServerPlayer player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SilentDaylight)) {
            return;
        }
        if (target == null || !target.isAlive()) {
            return;
        }

        writePercentOfMax(player, target, NORMAL_ATTACK_PERCENT,
                player.damageSources().playerAttack(player));

        for (int i = 0; i < SLASH_COUNT; i++) {
            PENDING.add(new PendingSlash(player.getUUID(), target.getUUID(),
                    i * SLASH_INTERVAL, i));
        }
    }

    /** 用<b>倒计时</b>而非世界时间：队列里两个 UUID 可能指向不同维度，拿某个维度的时间比较没有意义。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        ListIterator<PendingSlash> iterator = PENDING.listIterator();
        while (iterator.hasNext()) {
            PendingSlash pending = iterator.next();
            if (pending.ticksLeft() <= 0) {
                iterator.remove();
                resolve(event.getServer(), pending);
            } else {
                iterator.set(new PendingSlash(pending.playerId(), pending.targetId(),
                        pending.ticksLeft() - 1, pending.index()));
            }
        }
    }

    private static void resolve(net.minecraft.server.MinecraftServer server, PendingSlash pending) {
        ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // 在玩家所在维度里找目标（不能拿 overworld 去查，跨维度会找不到）
        if (!(level.getEntity(pending.targetId()) instanceof LivingEntity target)) {
            return;
        }
        if (!target.isAlive()) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SilentDaylight)) {
            return;
        }

        spawnSlashVisual(level, target.getBoundingBox().getCenter());
        applySlashDamage(level, player, held, target.getBoundingBox().getCenter());
    }

    /**
     * 斩击视觉：复用 {@link SwordAuraEntity#STYLE_FROST} 几何，放在目标碰撞箱中心；朝向由实体 id 派生，
     * 所以每斩歪向不同角度但中心永远压在目标上。
     */
    private static void spawnSlashVisual(ServerLevel level, Vec3 center) {
        SwordAuraEntity slash = SwordAuraEntity.spawnFrostSlash(level, center,
                1.0F,               // scale
                SLASH_HEIGHT,       // height：14 格长，拉成一道长刀
                SLASH_RADIUS,       // radius：0.19 格，收成一条极细的寒芒
                16,                 // maxAge：0.8 秒，够"从来袭方向溶解"演完
                0.30F);             // shrinkSpeed：几乎不缩，收场交给方向性溶解

        // 刀光轴线 = 渲染器里那两步旋转作用在本地 +Y 上的结果（先绕 Z 转 pitch，再绕 Y 转 yaw）
        Vec3 axis = slashAxisOf(slash);
        int steps = SLASH_SPARK_STEPS;
        double half = SLASH_HEIGHT * 0.5D;
        for (int i = -steps; i <= steps; i++) {
            if (i == 0) {
                continue;
            }
            double along = half * i / steps;
            Vec3 p = center.add(axis.scale(along));
            level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z,
                    1, 0.04D, 0.04D, 0.04D, 0.0D);
        }
        for (int end = -1; end <= 1; end += 2) {
            Vec3 p = center.add(axis.scale(end * half));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z,
                    5, 0.12D, 0.12D, 0.12D, 0.01D);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y, center.z, 18, 0.7D, 0.7D, 0.7D, 0.16D);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.7F);
    }

    /** 刀光轴向单位向量，旋转顺序必须与 {@code SwordAuraRenderer} 一致，否则粒子"不在这把刀上"。 */
    private static Vec3 slashAxisOf(SwordAuraEntity slash) {
        double pitch = Math.toRadians(slash.getPitch());
        double yaw = Math.toRadians(slash.getYaw());
        return new Vec3(
                -Math.sin(pitch) * Math.cos(yaw),
                Math.cos(pitch),
                Math.sin(pitch) * Math.sin(yaw));
    }

    /** 判定用"碰撞箱到球心的最近距离"而非脚底坐标（否则高个子目标的上半段会漏）；施法者自己除外。 */
    private static void applySlashDamage(ServerLevel level, ServerPlayer player, ItemStack held,
                                         Vec3 center) {
        double rangeSqr = SLASH_RANGE * SLASH_RANGE;
        AABB box = new AABB(center, center).inflate(SLASH_RANGE);

        // getEntitiesOfClass 返回的是快照列表，所以下面逐个结算（可能致死）是安全的
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive()
                        && e.getBoundingBox().distanceToSqr(center) <= rangeSqr)) {
            hurtOneSlash(level, player, held, victim);
        }
    }

    /** 对单个目标结算一斩：百分比改血（下限 1）+ 冰伤 × 攻击力倍率，并顶满冻结计时。 */
    private static void hurtOneSlash(ServerLevel level, ServerPlayer player, ItemStack held,
                                     LivingEntity victim) {
        // 冰系来源：DamageTypes.FREEZE + 攻击者是玩家，伤害类型读作"冰冻"但归因仍是玩家（掉落 / 死亡消息正常）。
        DamageSource source = SpellDamageUtil.iceSourceOf(victim, player);

        writePercentOfMax(player, victim, PERCENT_HEALTH, source);

        if (victim.isAlive()) {
            // 每斩前清一次无敌帧，保证 6 tick 间隔的连斩不会被"重复伤害"分支吃掉。
            SpellDamageUtil.prepareRepeatHit(victim);
            float ice = iceDamageOf(player);
            boolean applied = SpellDamageUtil.hurt(victim, source, held, player, ice);
            if (!applied && victim.isAlive()) {
                // 冰伤是主要输出，不能静默消失 —— 直接改血兜底并留一条 WARN。
                warnIceFallbackOnce(victim, ice);
                writeIceDamage(player, victim, ice, source);
            }
            iceHitFeedback(level, victim);
        }

        if (victim.isDeadOrDying() && !victim.isRemoved() && !victim.dead) {
            victim.die(source);
        }

        if (victim.isAlive() || victim.isDeadOrDying()) {
            victim.setTicksFrozen(victim.getTicksRequiredToFreeze());
        }
    }

    /** 「冰伤被事件链吃掉」的警告只打一次 —— 否则每一斩都会刷一屏日志。 */
    private static boolean iceFallbackWarned;

    /**
     * 冰伤的兜底结算：{@link SpellDamageUtil#hurt} 只要有一环把伤害取消/减到 0，冰伤就会静默消失，
     * 所以补一条不经过事件的落地路径；先写击杀归因再改血，掉落才归给玩家。
     */
    private static void writeIceDamage(ServerPlayer player, LivingEntity victim,
                                       float ice, DamageSource source) {
        if (victim == null || !victim.isAlive() || ice <= 0F) {
            return;
        }
        float before = BCCoreCompat.getHealth(victim);
        if (Float.isNaN(before)) {
            before = victim.getHealth();
        }
        float after = before - ice;
        if (after >= before) {
            return;
        }
        SpellDamageUtil.applyKillCredit(player, victim);
        // 引擎写一次（能穿透"覆写 getHealth"的抗改血实现），再无条件写原版同步血量字段
        BCCoreCompat.setHealth(victim, after);
        victim.entityData.set(DATA_HEALTH_ID, after);
        if (after <= 0F) {
            victim.die(source);
        }
    }

    private static void warnIceFallbackOnce(LivingEntity victim, float ice) {
        if (iceFallbackWarned) {
            return;
        }
        iceFallbackWarned = true;
        ModUtils.LOGGER.warn("[SilentDaylight] 冰伤 {} 点没有经事件链落地（目标 {}），"
                        + "已改用直接改血兜底。此警告只打一次，后续静默走兜底。",
                ice, victim.getType().toShortString());
    }

    private static void iceHitFeedback(ServerLevel level, LivingEntity victim) {
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.6d, victim.getZ(),
                6, 0.3d, 0.35d, 0.3d, 0.02d);
    }

    /**
     * 按目标最大生命值百分比改血（直接写血量）：写到 ≤0 后直接 {@code die(source)}，
     * 死亡消息 / 战利品 / 击杀归因照常（{@code die()} 自带幂等保护）。
     */
    private static void writePercentOfMax(ServerPlayer player, LivingEntity victim,
                                          float percent, DamageSource source) {
        if (victim == null || !victim.isAlive() || percent <= 0F) {
            return;
        }
        float max = victim.getMaxHealth();
        if (!Float.isFinite(max) || max <= 0F) {
            return;
        }
        float amount = Math.max(1.0F, max * (percent / 100.0F));

        float before = BCCoreCompat.getHealth(victim);
        if (Float.isNaN(before)) {
            before = victim.getHealth();
        }
        float after = before - amount;
        if (after >= before) {
            return;
        }

        // 改血不经过 hurt，掉落表读的 lastHurtByPlayer 要自己写
        SpellDamageUtil.applyKillCredit(player, victim);

        // 先让引擎写一次（可穿透"覆写 getHealth"的抗改血实现），再无条件写原版同步血量字段
        BCCoreCompat.setHealth(victim, after);
        victim.entityData.set(DATA_HEALTH_ID, after);

        if (after <= 0F) {
            victim.die(source);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        PENDING.clear();
    }

    public static int pendingCount() {
        return PENDING.size();
    }
}
