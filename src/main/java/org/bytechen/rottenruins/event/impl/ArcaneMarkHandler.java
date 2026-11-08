package org.bytechen.rottenruins.event.impl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectInstance;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.RegisterCustomEffect;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffects;
import org.bytechen.rottenruins.utils.ModUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>奥数印记</b>的触发层 —— 「带印记的目标每次受伤，都会被塞上<b>原版效果系统里所有</b>
 * 负面（{@link MobEffectCategory#HARMFUL}）效果」。
 *
 * <h3>节流规则：每个目标每 tick 最多施加 1 个效果</h3>
 * <p>一次受伤<b>不再</b>瞬间塞满几十个效果，而是先「排队」（{@link #arm}），
 * 之后由印记效果自己的逐 tick 回调（{@code ArcaneMarkEffect#onEntityTick} → {@link #drainOne}）
 * 每 tick 出队<b>一个</b>，直到这一轮的效果全部给完。</p>
 *
 * <p>为什么必须节流：整合包里 HARMFUL 效果动辄三四十个，
 * 一次性 {@code addEffect} 会在同一 tick 内触发几十次属性重算 + 几十个同步包，
 * 客户端还会瞬间刷满一整屏图标 —— 既卡又难看。摊到 tick 上（约 1~2 秒铺完）几乎无感。</p>
 *
 * <p>节流是<b>硬约束</b>：{@link Batch#lastAppliedTick} 记录"最后一次施加发生在哪个
 * {@code entity.tickCount}"，同一个 tick 内重复调用 {@link #drainOne} 一律直接返回。
 * 所以无论事件层这一 tick 被触发多少次（AoE 连击、多段伤害），施加次数都不会超过 1。</p>
 *
 * <h3>为什么必须放在事件层 + 效果层两处</h3>
 * <ul>
 *   <li><b>事件层</b>（本类 {@link #onLivingHurt}）负责"什么时候开始"—— 要响应"受伤"，
 *       只能挂 {@code LivingHurtEvent}；</li>
 *   <li><b>效果层</b>（{@code ArcaneMarkEffect#onEntityTick}）负责"每 tick 出队一个"——
 *       它只对<b>真正带着印记</b>的实体每 tick 回调，没有印记的实体零开销。</li>
 * </ul>
 *
 * <h3>效果池：整个原版效果注册表</h3>
 * <p>遍历 {@link ForgeRegistries#MOB_EFFECTS} —— 这是原版的效果注册表，
 * <b>所有模组的 {@code MobEffect} 都注册在这里</b>，所以这个池子会随着整合包自动变大，
 * 不需要我们维护任何名单。过滤规则只有一条：{@code getCategory() == HARMFUL}。</p>
 *
 * <h3>几个必须处理的细节</h3>
 * <ul>
 *   <li><b>逐个 try/catch</b>：别的模组的效果可能在 {@code addEffect} 里抛异常，
 *       一个坏效果不该让整次结算崩掉；</li>
 *   <li><b>已经有足够长的同类效果就跳过</b>（{@link #needsApply}）——
 *       不把 tick 预算浪费在"再刷一遍还有 5 秒的缓慢"上；</li>
 *   <li><b>瞬时效果</b>（{@code isInstantenous}，例如原版瞬间伤害）：同样能加，
 *       原版会在施加时立刻结算一次、不留在效果栏里；</li>
 *   <li><b>本模组自己的负面自定义效果不在这个池子里</b> —— 它们不进
 *       {@code ForgeRegistries.MOB_EFFECTS}（见 {@code CustomEffectRegistry}），
 *       这是刻意的：印记要的是"原版系统的全部负面效果"。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArcaneMarkHandler {

    /**
     * 负面效果的时长<b>上限</b>（tick）：6 秒。
     *
     * <p>实际发出去的时长是 {@code min(这个值, 印记剩余时间)} —— 见 {@link #apply}。
     * 取上限而不是固定值，是为了保证"印记一结束，它带来的负面效果跟着结束"。</p>
     */
    public static final int HARMFUL_DURATION = 120;

    /** 层数：0 = 一级。 */
    public static final int HARMFUL_AMPLIFIER = 0;

    /** 每 tick 允许施加的效果个数 —— 硬上限。 */
    public static final int MAX_APPLY_PER_TICK = 1;

    /** 单轮队列长度上限（防止某些整合包效果数量爆表把队列撑得太长）。 */
    private static final int MAX_QUEUE = 256;

    /** 队列条数上限，超过就整体丢弃（兜底，防止实体卸载导致的残留条目堆积）。 */
    private static final int MAX_PENDING = 512;

    /** 待施加队列：每个受害者一条，键是实体 UUID（不持有实体引用，避免内存泄漏）。 */
    private static final Map<UUID, Batch> PENDING = new ConcurrentHashMap<>();

    private ArcaneMarkHandler() {
    }

    // ══════════════════════════════════════════════════════════════
    // 事件层：受伤 → 排队
    // ══════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide() || !victim.isAlive()) {
            return;
        }
        // 只对挂着「奥数印记」的目标生效（印记是自定义效果，见 RegisterCustomEffect）
        if (!CustomEffects.hasEffect(victim, RegisterCustomEffect.ARCANE_MARK)) {
            return;
        }

        // 注意：这里**只排队**，不施加。真正的施加在 drainOne 里，每 tick 一个。
        if (!arm(victim)) {
            return;
        }

        // 可见反馈：自定义效果不进原版效果栏，但这里的负面效果是原版的、会自己显示图标；
        // 这圈粒子只是让"印记触发了"这件事在混战里也看得见。
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.6d, victim.getZ(),
                    12, 0.4d, 0.5d, 0.4d, 0.05d);
        }
    }

    /** 服务端停服：清空全部队列（实体不会再 tick，留着就是泄漏）。 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    // ══════════════════════════════════════════════════════════════
    // 队列：排队 / 出队 / 清理
    // ══════════════════════════════════════════════════════════════

    /**
     * 给目标排上"这一轮的全部负面效果"。
     *
     * <p>已经有一轮在跑时<b>不重置进度</b>（快速连击下仍然每 tick 只加一个，
     * 不会因为连击而反复从第一个效果开始、永远铺不到后面）；
     * 上一轮已经给完了才重新从头排。</p>
     *
     * @return {@code true} = 这一 tick 有新的意义（用于决定要不要放粒子）
     */
    public static boolean arm(LivingEntity victim) {
        List<MobEffect> pool = harmfulPool();
        if (pool.isEmpty()) {
            return false;
        }
        if (PENDING.size() > MAX_PENDING) {
            // 兜底：正常玩不会到这里，真到了说明有实体卸载后没清理，整体丢弃不影响玩法
            ModUtils.LOGGER.warn("[ArcaneMark] 待施加队列超过 {} 条，整体清空", MAX_PENDING);
            PENDING.clear();
        }
        Batch batch = PENDING.get(victim.getUUID());
        if (batch == null) {
            PENDING.put(victim.getUUID(), new Batch(pool));
            return true;
        }
        if (batch.isDone()) {
            batch.restart();
            return true;
        }
        return false;
    }

    /**
     * <b>每 tick 出队一个</b>效果 —— 由 {@code ArcaneMarkEffect#onEntityTick} 调用。
     *
     * <p>本方法保证：同一个 {@code entity.tickCount} 内，施加次数不超过
     * {@link #MAX_APPLY_PER_TICK}。</p>
     */
    public static void drainOne(LivingEntity victim) {
        if (victim == null || victim.level().isClientSide() || !victim.isAlive()) {
            return;
        }
        UUID key = victim.getUUID();
        Batch batch = PENDING.get(key);
        if (batch == null) {
            return;
        }
        // 印记掉了（或被清掉）→ 立刻停止，剩下的不再施加
        int markLeft = remainingMarkTicks(victim);
        if (markLeft <= 0) {
            PENDING.remove(key);
            if (batch.applied > 0) {
                ModUtils.LOGGER.info("[ArcaneMark] 印记已结束，停止施加（本轮已给 {} 个，剩余 {} 个不再施加）",
                        batch.applied, batch.queue().size() - batch.cursor);
            }
            return;
        }
        // ── 硬节流：本 tick 的施加预算（{@link #MAX_APPLY_PER_TICK}）用完就直接返回 ──
        int now = victim.tickCount;
        if (batch.lastAppliedTick != now) {
            batch.appliedThisTick = 0;   // 换了一个 tick，预算重置
        }
        if (batch.appliedThisTick >= MAX_APPLY_PER_TICK) {
            return;
        }

        // 找出"确实需要施加"的下一个效果；已经有足够长的同类效果就跳过
        MobEffect picked = null;
        while (batch.cursor < batch.queue().size()) {
            MobEffect effect = batch.queue().get(batch.cursor++);
            if (needsApply(victim, effect)) {
                picked = effect;
                break;
            }
        }

        if (picked == null) {
            // 这一轮给完了
            finish(victim, key, batch);
            return;
        }

        batch.lastAppliedTick = now;
        batch.appliedThisTick++;
        apply(victim, picked);
        batch.applied++;
        if (batch.cursor >= batch.queue().size()) {
            finish(victim, key, batch);
        }
    }

    /** 印记消失时调用（{@code ArcaneMarkEffect#onRemoved}）：丢弃该目标的残留队列。 */
    public static void clear(LivingEntity victim) {
        if (victim != null) {
            PENDING.remove(victim.getUUID());
        }
    }

    private static void finish(LivingEntity victim, UUID key, Batch batch) {
        PENDING.remove(key);
        if (batch.applied > 0) {
            // 摊开大约 batch.applied 个 tick（每 tick 只加 1 个）
            ModUtils.LOGGER.debug("[ArcaneMark] {} 本轮附加 {} 个负面效果（每 tick 1 个）",
                    victim.getType().toShortString(), batch.applied);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 池子与施加
    // ══════════════════════════════════════════════════════════════

    /** 原版效果注册表里全部 HARMFUL 效果（注册顺序，稳定）。 */
    private static List<MobEffect> harmfulPool() {
        List<MobEffect> pool = new ArrayList<>();
        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS.getValues()) {
            if (effect != null && effect.getCategory() == MobEffectCategory.HARMFUL) {
                pool.add(effect);
            }
        }
        if (pool.size() > MAX_QUEUE) {
            return new ArrayList<>(pool.subList(0, MAX_QUEUE));
        }
        return pool;
    }

    /** 目标是否真的需要再吃一次这个效果（已经有一份够长的就不浪费 tick）。 */
    private static boolean needsApply(LivingEntity victim, MobEffect effect) {
        // 瞬时效果（瞬间伤害之类）不会被存进效果栏，这里必然拿到 null → 需要施加
        MobEffectInstance current = victim.getEffect(effect);
        if (current == null) {
            return true;
        }
        // 剩余时长已经过半的就不刷了，把 tick 预算留给还没上的效果
        return current.getDuration() < HARMFUL_DURATION / 2;
    }

    /**
     * 目标身上印记的剩余 tick；没有印记 / 已到期返回 {@code 0}，永久印记返回
     * {@link #HARMFUL_DURATION}（当作"用满一份"）。
     */
    private static int remainingMarkTicks(LivingEntity victim) {
        CustomEffectInstance mark =
                CustomEffects.getEffect(victim, RegisterCustomEffect.ARCANE_MARK);
        if (mark == null) {
            return 0;
        }
        if (mark.isInfinite()) {
            return HARMFUL_DURATION;
        }
        return Math.max(0, Math.min(HARMFUL_DURATION, mark.getDuration()));
    }

    /**
     * 施加单个效果（异常隔离）。
     *
     * <p><b>时长取「印记剩余时间」与 {@link #HARMFUL_DURATION} 的较小值。</b>
     * 这一条是"印记到期了效果还在"的根因：以前每个效果都发满 {@value #HARMFUL_DURATION} tick（6 秒），
     * 而印记只有 10 秒 —— 印记结束前最后刷上的那批，还会继续挂最多 6 秒，
     * 读起来就是"持续时间过去了还在生效"。取 min 之后，
     * <b>所有负面效果一定与印记同时或更早结束</b>，不会再拖尾。</p>
     */
    private static void apply(LivingEntity victim, MobEffect effect) {
        int duration = remainingMarkTicks(victim);
        if (duration <= 0) {
            return;
        }
        try {
            // 已经更强（更高层/更长）时原版自己会合并，直接 add 即可
            victim.activeEffects.put(effect,new MobEffectInstance(effect, duration,
                    HARMFUL_AMPLIFIER, false, true));
        } catch (Throwable t) {
            // 别的模组的效果可能在施加时抛异常：吞掉，别让一个坏效果带崩整次结算
            ModUtils.LOGGER.warn("[ArcaneMark] 施加负面效果失败（{}）：{}",
                    ForgeRegistries.MOB_EFFECTS.getKey(effect), t.toString());
        }
    }

    /** 一个目标身上"这一轮"的待施加队列。 */
    private static final class Batch {

        private final List<MobEffect> queue;
        private int cursor;
        /** 最后一次施加发生在哪个 {@code entity.tickCount}。 */
        private int lastAppliedTick = Integer.MIN_VALUE;
        /** 该 tick 内已经施加了几个（用于 {@link #MAX_APPLY_PER_TICK} 预算判定）。 */
        private int appliedThisTick;
        /** 本轮一共施加了几个（只用于日志）。 */
        private int applied;

        private Batch(List<MobEffect> queue) {
            this.queue = queue;
        }

        private List<MobEffect> queue() {
            return queue;
        }

        private boolean isDone() {
            return cursor >= queue.size();
        }

        private void restart() {
            cursor = 0;
            applied = 0;
            appliedThisTick = 0;
            lastAppliedTick = Integer.MIN_VALUE;
        }
    }
}
