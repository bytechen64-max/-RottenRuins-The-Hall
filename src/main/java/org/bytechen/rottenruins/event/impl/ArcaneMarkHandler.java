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
 * 奥数印记触发层：带印记的目标受伤时把原版全部 HARMFUL 效果排入队列，由效果层每 tick 出队一个。
 * 硬约束：每个目标每 tick 至多施加 {@link #MAX_APPLY_PER_TICK} 个；效果时长不得超过印记剩余时间（见 {@link #apply}）。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArcaneMarkHandler {

    /** 负面效果时长上限（tick，6 秒）；实际取 min(它, 印记剩余时间)。 */
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

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide() || !victim.isAlive()) {
            return;
        }
        if (!CustomEffects.hasEffect(victim, RegisterCustomEffect.ARCANE_MARK)) {
            return;
        }

        if (!arm(victim)) {
            return;
        }

        // 可见反馈：负面效果自带图标，粒子只是让"印记触发了"在混战里也看得见
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

    /**
     * 给目标排上本轮的全部负面效果；已有一轮在跑时不重置进度（连击下仍每 tick 一个）。
     *
     * @return {@code true} = 本 tick 有新进度（决定是否放粒子）
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

    /** 每 tick 出队一个（由 {@code ArcaneMarkEffect#onEntityTick} 调用）；同一 {@code tickCount} 内不超过 {@link #MAX_APPLY_PER_TICK}。 */
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
        int now = victim.tickCount;
        if (batch.lastAppliedTick != now) {
            batch.appliedThisTick = 0;
        }
        if (batch.appliedThisTick >= MAX_APPLY_PER_TICK) {
            return;
        }

        MobEffect picked = null;
        while (batch.cursor < batch.queue().size()) {
            MobEffect effect = batch.queue().get(batch.cursor++);
            if (needsApply(victim, effect)) {
                picked = effect;
                break;
            }
        }

        if (picked == null) {
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
            ModUtils.LOGGER.debug("[ArcaneMark] {} 本轮附加 {} 个负面效果（每 tick 1 个）",
                    victim.getType().toShortString(), batch.applied);
        }
    }

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
     * 印记剩余 tick；无印记 / 已到期返回 {@code 0}，永久印记按 {@link #HARMFUL_DURATION} 算。
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
     * 施加单个效果（异常隔离）。时长取 min(印记剩余时间, {@link #HARMFUL_DURATION})，
     * 保证负面效果一定与印记同时或更早结束，不会拖尾。
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
