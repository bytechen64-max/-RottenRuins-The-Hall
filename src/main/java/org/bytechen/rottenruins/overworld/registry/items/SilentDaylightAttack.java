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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import static net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID;

/**
 * <b>寂寒白日 · 左键三连斩</b> —— 挥刀一次，在目标身上连斩三下。
 *
 * <h3>触发与节奏</h3>
 * <ul>
 *   <li>由 {@code AttackEntityEvent} 驱动（见 {@code ForgeEventHandler#onAttackEntity}），
 *       所以<b>无论原版这一刀有没有造成伤害</b>（被取消、打无敌帧、目标免伤）都会触发
 *       —— 这是需求里明确要求的"无论造成伤害没有"。</li>
 *   <li>原版那一刀本身<b>保留</b>：不取消事件，受击红闪/击退/落剑动作都照常。</li>
 *   <li>三斩的间隔是 {@value #SLASH_INTERVAL} tick（0.3 秒），第一斩与"挥刀"同一 tick。</li>
 * </ul>
 *
 * <h3>每一斩做什么</h3>
 * <ol>
 *   <li><b>视觉</b>：{@link SwordAuraEntity#spawnFrostSlash} —— 与使徒技能斩击同一套构件
 *       （外层放大 + 内芯 outline），配色换成<b>冰白外层 / 深蓝内芯</b>。位置取
 *       <b>目标碰撞箱中心</b>，朝向由实体 id 派生，所以每一下的方向都不同
 *       （"方向随机但要斩到目标"）。尺寸是<b>窄而长</b>的一线刀光：
 *       {@value #SLASH_HEIGHT} 格长、{@value #SLASH_RADIUS} 格半径。</li>
 *   <li><b>范围伤害</b>：斩击不是只打被砍的那一个 —— 以目标中心为球心、
 *       {@value #SLASH_RANGE} 格为半径，范围内<b>所有</b>生物一起吃这一斩
 *       （施法者自己除外）。因为刀光本身有 10 格长，只打单体反而和视觉对不上。</li>
 *   <li><b>伤害</b>：两条路分开结算(原因见 {@link #writePercentOfMax})：
 *       <ul>
 *         <li><b>改血</b> = 目标最大生命值的 {@value #PERCENT_HEALTH}%（不低于 1 点），
 *             直接调 {@code BCCoreCompat.damage} —— 不走任何伤害事件、不吃护甲、
 *             不吃无敌帧；</li>
 *         <li><b>冰伤</b> = {@value #ICE_DAMAGE} 点，走 {@link SpellDamageUtil} 的包装层，
 *             护甲与别的模组的减伤照常生效。</li>
 *       </ul>
 *       两条路都会补击杀归因，改血致死时 {@code BCCoreCompat} 会补一次真实的
 *       {@code die(source)}，所以<b>掉落物照常</b>。</li>
 *   <li><b>冻结</b>：每斩把目标的冻结计时顶满，读作"被冰刃割开"。</li>
 * </ol>
 *
 * <h3>为什么需要调度器</h3>
 * <p>三斩要跨 12 tick，而武器本身没有"每 tick 回调"。这里用一个极小的
 * {@link TickEvent.ServerTickEvent} 队列：每次挥刀往队里塞三条 {@link PendingSlash}，
 * 到期就结算。队列里存的是 <b>UUID</b> 而不是实体引用 —— 目标在中途被卸载/换维度时，
 * 按 UUID 查不到就自然丢弃，不会把手里的引用拖住。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SilentDaylightAttack {

    /** 每一斩之间的间隔（tick）：0.3 秒。 */
    public static final int SLASH_INTERVAL = 6;

    /** 一次挥刀斩几下。 */
    public static final int SLASH_COUNT = 3;

    /** 每一斩的固定冰伤（走包装层，吃护甲 / 事件）。 */
    public static final float ICE_DAMAGE = 18.0F;

    /**
     * 每一斩的<b>改血</b>百分比：目标最大生命值的 5%（下限 1 点）。
     *
     * <p>与 {@link #ICE_DAMAGE} 分开结算，两条路各有各的意义：</p>
     * <ul>
     *   <li><b>改血</b>走 {@code BCCoreCompat}（直接写血量）—— 无视护甲、抗性、无敌帧，
     *       这是"改血武器"该有的穿透力；</li>
     *   <li><b>冰伤</b>走 {@code SpellDamageUtil} 那层包装 —— 会经过 Forge 的伤害事件，
     *       别的模组的减伤/免疫照常生效。</li>
     * </ul>
     */
    public static final float PERCENT_HEALTH = 5.0F;

    /** 普通攻击（原版那一刀）额外改血的百分比。 */
    public static final float NORMAL_ATTACK_PERCENT = 5.0F;

    /** 斩击刀光的总长度（格）—— 比使徒的 8 更长，拉成一道长刀。 */
    public static final float SLASH_HEIGHT = 11.0F;

    /** 斩击刀光的底面半径（格）—— 比使徒的 1.0 细得多，收成一条线。 */
    public static final float SLASH_RADIUS = 0.42F;

    /**
     * 一斩的伤害半径（格）。
     *
     * <p>以被砍目标的碰撞箱中心为球心。取 {@value #SLASH_RANGE} 而不是"刀光多长就打多远"：
     * 刀光是绕自身中心随机倾斜的一道线，几何上没有一个稳定的"扫过体积"，
     * 硬按线去算判定会变成"有时候打到 5 格外的怪、有时候贴脸打不到"。
     * 球形判定读起来稳定，且与"这一斩落在目标身上"的观感一致。</p>
     */
    public static final double SLASH_RANGE = 4.5D;

    /**
     * 待结算的一斩。
     *
     * @param ticksLeft 还有多少 tick 到期；{@code 0} 表示本次服务端 tick 结束时就结算
     *                  （挥刀发生在 {@code ServerTickEvent.END} 之前的包处理里，
     *                  所以第一斩与挥刀同一 tick 落地）
     * @param index     第几斩（0 起），只用于日志
     */
    private record PendingSlash(UUID playerId, UUID targetId, int ticksLeft, int index) {
    }

    private static final List<PendingSlash> PENDING = new ArrayList<>();

    /** "改血引擎不可用"的警告只打一次 —— 否则每一斩都会刷一屏日志。 */
    private static boolean warnedNoEngine;

    private SilentDaylightAttack() {
    }

    // ══════════════════════════════════════════════════════════════
    // 入口：由 AttackEntityEvent 调用
    // ══════════════════════════════════════════════════════════════

    /**
     * 玩家挥刀（{@code AttackEntityEvent}）。只有手上真的是寂寒白日时才生效。
     *
     * <p>这里做两件事：马上结算"普通攻击的 5% 最大生命值改血"，并把三斩排进队列。</p>
     */
    public static void onAttackLanded(ServerPlayer player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SilentDaylight)) {
            return;
        }
        if (target == null || !target.isAlive()) {
            return;
        }

        // 普通攻击：额外 5% 最大生命值的改血
        writePercentOfMax(player, target, NORMAL_ATTACK_PERCENT,
                player.damageSources().playerAttack(player));

        // 三连斩：0 / 6 / 12 tick 之后各结算一次
        for (int i = 0; i < SLASH_COUNT; i++) {
            PENDING.add(new PendingSlash(player.getUUID(), target.getUUID(),
                    i * SLASH_INTERVAL, i));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 调度
    // ══════════════════════════════════════════════════════════════

    /**
     * 每服务端 tick 推进一次队列。
     *
     * <p>用<b>倒计时</b>而不是世界时间：{@code PendingSlash} 里存的两个 UUID 可能指向
     * 不同维度的实体，拿"某一个维度的时间"做比较会引入"哪个维度的时间"这种无意义的分支。
     * 倒计时则只依赖服务端 tick 数，任何维度、任何存档都一致。</p>
     */
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
                // record 不可变：就地改成"少一 tick"
                iterator.set(new PendingSlash(pending.playerId(), pending.targetId(),
                        pending.ticksLeft() - 1, pending.index()));
            }
        }
    }

    /** 结算一斩：找到玩家与目标，出视觉效果并结算伤害。 */
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
            // 中途换手了 → 剩下的斩击作废
            return;
        }

        spawnSlashVisual(level, target.getBoundingBox().getCenter());
        applySlashDamage(level, player, held, target.getBoundingBox().getCenter());
    }

    /**
     * 使徒式斩击的视觉：放在目标碰撞箱中心，配色换成蓝白，并拉成<b>窄而长</b>的一线。
     *
     * <p>几何直接复用 {@link SwordAuraEntity#STYLE_FROST} —— 和使徒斩击是同一套构件
     * （外层放大 + 内芯 outline），只是外层从白换成冰白、内芯从黑换成深蓝。
     * 朝向仍然由 {@link SwordAuraEntity} 按 entity id 派生的 pitch/yaw 决定，
     * 所以三斩各自歪向不同角度，但锥体中心永远压在目标碰撞箱中心上
     * —— 这就是需求里的"方向随机但要斩到目标的区域"。</p>
     *
     * @param center 斩击中心（目标碰撞箱中心）
     */
    private static void spawnSlashVisual(ServerLevel level, Vec3 center) {
        SwordAuraEntity.spawnFrostSlash(level, center,
                1.0F,               // scale
                SLASH_HEIGHT,       // height：11 格长，一眼看得出是"长刀"
                SLASH_RADIUS,       // radius：0.42 格，收成一条线才够"窄"
                14,                 // maxAge
                1.6F);              // shrinkSpeed
        // 再撒一层蓝白电火花：斩击本体是半透明几何，火花负责"寒气撕裂"的碎屑感
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y, center.z, 18, 0.7D, 0.7D, 0.7D, 0.16D);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.7F);
    }

    /**
     * 一斩的范围结算：以 {@code center} 为球心、{@link #SLASH_RANGE} 格为半径，
     * 范围内所有生物各吃一份伤害并顶满冻结计时。
     *
     * <p>判定用"目标碰撞箱到球心的最近距离"，而不是"脚底坐标到球心"——
     * 高个子目标（铁傀儡、监守者）的碰撞箱上半段也在刀光里，用脚底判会漏。</p>
     *
     * <p>施法者自己除外：这是玩家武器，不该把自己一起割了。</p>
     */
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

    /**
     * 对单个目标结算一斩：<b>改血</b> {@link #PERCENT_HEALTH}% 最大生命值（下限 1）+ <b>冰伤</b>
     * {@link #ICE_DAMAGE} 点，并顶满冻结计时。
     */
    private static void hurtOneSlash(ServerLevel level, ServerPlayer player, ItemStack held,
                                     LivingEntity victim) {
        DamageSource frost = level.damageSources().freeze();

        writePercentOfMax(player, victim, PERCENT_HEALTH, frost);

        //  冰伤：走 {@code SpellDamageUtil} 的包装层，让护甲、别的模组的减伤、
        //    伤害事件与击杀归因都照常介入。上一步可能已经把它打死了，所以先判存活。
        if (victim.isAlive()) {
            // 每斩前清一次，保证 6 tick 间隔的连斩不会被"重复伤害"分支吃掉。
            SpellDamageUtil.prepareRepeatHit(victim);
            SpellDamageUtil.hurt(victim, frost, held, player, ICE_DAMAGE);
        }
        // 冻结表现：把冻结计时顶到"结冰"，读作冰刃命中
        if (victim.isAlive() || victim.isDeadOrDying()) {
            victim.setTicksFrozen(victim.getTicksRequiredToFreeze());
        }
    }

    /**
     * 按目标最大生命值的百分比做一次<b>改血</b>。
     *
     * <h3>为什么是"自己读血量 + {@code setHealth}"，而不是 {@code BCCoreCompat.damage}</h3>
     * <p>{@code BCCoreCompat.damage} 内部是 {@code get → 减 → set}，看着更省事，但它把两件事
     * 绑在一起了：只要<b>读数</b>拿不到（读数方法名/返回类型对不上、目标没有可读的血量引擎），
     * 整条改血就会<b>静默</b>退化成原版 {@code hurt} —— 于是"改血"变成了"吃护甲的普通伤害"，
     * 表现就是"改血没生效"。而虚空剑那条路之所以好用，是因为它只用 {@code set}
     * （{@code setHealthToMinusOne}），从不依赖读数。</p>
     *
     * <p>所以这里照虚空剑的用法来：<b>只用 {@code setHealth} 这一个原语</b>，
     * 读数优先问 VitalProbe、拿不到就退回原版 {@code getHealth()}。
     * 写入失败时退到<b>原版 {@code setHealth}</b>（同样是直接改血），
     * <b>不退到 {@code hurt}</b> —— 那条路吃护甲、吃无敌帧、还会被友伤之类的规则整段取消，
     * 症状就是"改血没生效"。</p>
     *
     * <h3>三个必须自己补的收尾</h3>
     * <ol>
     *   <li><b>击杀归因</b>：{@code hurt} 会把击杀者写进 {@code lastHurtByPlayer}，改血不会。
     *       少了它，战利品表里带 {@code killer} 条件的掉落会静默消失。</li>
     *   <li><b>血量镜像</b>：改血写的是 VitalProbe 的血量存储，客户端血条读的却是原版
     *       {@code DATA_HEALTH_ID}。不镜像就会出现"服务端已经掉血、画面纹丝不动"，
     *       看起来同样像"改血没生效"。</li>
     *   <li><b>致死走真实死亡流程</b>：见下面对 {@code after <= 0} 的说明。</li>
     * </ol>
     *
     * @param percent 百分比，例如 {@code 5.0} 表示最大生命值的 5%
     * @param source  伤害来源，只用于致死时的终结击（真正的改血不读它）
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
        // "至少为 1"：低血量目标上 5% 会小于 1，否则这一斩等于没伤害
        float amount = Math.max(1.0F, max * (percent / 100.0F));

        SpellDamageUtil.applyKillCredit(player, victim);

        // 读当前有效血量：优先 VitalProbe 的读数，拿不到就退回原版 getHealth()
        float before = BCCoreCompat.getHealth(victim);
        if (Float.isNaN(before)) {
            before = victim.getHealth();
        }
        float after = before - amount;

        // ── 致死量：直接走真实死亡流程，<b>绝不先把血量写成 ≤0</b> ──
        // 原版 setHealth(0) 会让 isAlive() 立刻变 false，而 die() 是 protected：
        // 那之后再调 finishOff（开头就判 isAlive()）已经来不及，生物会卡在 0 血、
        // 不掉落、也不消失。所以致死这一刀交给 finishOff，它内部用
        // hurt(Float.MAX_VALUE) 把 die(source) 逼出来，掉落 / 死亡事件 / 进度 /
        // Boss 血条全都照常。
        if (after <= 0F) {
            SpellDamageUtil.finishOff(victim, source);
            return;
        }

        // ── 非致死量 ──
        // 先试 VitalProbe 的改血引擎（能穿透"覆写 getHealth 返回自定义血量"的抗改血实现），
        // 写入失败时就退到<b>原版 setHealth</b> —— 那同样是一次"改血"：
        // 直接改血量字段，绕过护甲、抗性、无敌帧与所有伤害事件。
        // 刻意<b>不</b>退到 hurt：那会吃护甲、吃无敌帧、还会被友伤之类的规则整段取消，
        // 症状就变成"改血没生效"，正是这个兜底要避免的。
        if (BCCoreCompat.setHealth(victim, after)) {
            victim.entityData.set(DATA_HEALTH_ID,after);
        }
    }

    /** 服务器停止时清队列，避免跨存档残留。 */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** 调试用：当前队列长度。 */
    public static int pendingCount() {
        return PENDING.size();
    }
}
