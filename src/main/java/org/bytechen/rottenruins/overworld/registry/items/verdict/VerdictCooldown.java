package org.bytechen.rottenruins.overworld.registry.items.verdict;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.overworld.registry.items.DomeriteLongsword;
import org.jetbrains.annotations.Nullable;

/**
 * 天穹裁决 —— 三技能<b>共享的冷却池</b>：用掉一招等于用掉整个裁决，否则三招能在同两秒内连打一套，没法按单招平衡。
 * 层数：基础 {@value #BASE_TICKS} tick，链内每招 +{@link #PER_LEVEL_TICKS}，间隔超过 {@link #DECAY_WINDOW_TICKS} 算新链（等级归零），封顶 {@link #MAX_LEVEL} 级。
 */
public final class VerdictCooldown {

    public static final String ROOT = "HallVerdict";

    private static final String K_LEVEL = "Level";
    /** 本次连打的起点（全局时间），<b>只在链开始时写一次</b> —— 这是等级能回落的关键，见 {@link #peekCooldownTicks}。 */
    private static final String K_CHAIN = "Chain";
    private static final String K_UNTIL = "Until";

    /** 基础冷却走 {@link VerdictTuning#baseCooldownTicks()} 而不是常量：玩家可整体调节奏，而连打递增曲线不变。 */
    public static int baseTicks() {
        return VerdictTuning.baseCooldownTicks();
    }

    /** 基础冷却默认值（tick）= 2 秒；实测正常出招间隔 3.8~6.0 秒，调低基础值后"正常打"恒定 2 秒。 */
    public static final int BASE_TICKS_DEFAULT = 40;
    public static final int BASE_TICKS = BASE_TICKS_DEFAULT;
    public static final int PER_LEVEL_TICKS = 40;
    public static final int MAX_LEVEL = 2;

    /** 窗口期：相邻两招间隔超过这么久就算开新链；5 秒会让 3.8~6.0 秒的正常连招全被判成链内。 */
    public static final int DECAY_WINDOW_TICKS = 160;

    public static final float MISS_COOLDOWN_RATIO = 0.4f;

    /** 出招期间先用这个"锁死"值堵住窗口：技能实现抛异常没走到 {@link #finish} 时冷却也不会是 0 而允许连点。 */
    private static final int LOCK_TICKS = 20 * 60;

    private VerdictCooldown() {}

    private static CompoundTag tag(Player player) {
        return player.getPersistentData().getCompound(ROOT);
    }

    private static CompoundTag tagOrCreate(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT)) root.put(ROOT, new CompoundTag());
        return root.getCompound(ROOT);
    }

    public static int level(Player player) {
        return tag(player).getInt(K_LEVEL);
    }

    public static int remaining(Player player) {
        long until = tag(player).getLong(K_UNTIL);
        long now = player.level().getGameTime();
        return (int) Math.max(0L, until - now);
    }

    /** 是否处于冷却中（{@code remaining} 已做 0 下限钳制，所以判 &gt; 0 而不是 &gt;= 0）。 */
    public static boolean onCooldown(Player player) {
        return remaining(player) > 0;
    }

    /**
     * 本招应记的冷却 tick 数。<b>计的是"链"而不是"距上次出招"</b>：若记"上次出招时间"，出招瞬间就把它刷成 now，
     * 等级永远不回落，连打几招后永久钉在最高档（体感是"冷却莫名其妙变得特别长"）；这里记链起点，停手超窗即回最短冷却。
     */
    public static int peekCooldownTicks(Player player) {
        CompoundTag t = tagOrCreate(player);
        long now = player.level().getGameTime();
        long chainStart = t.getLong(K_CHAIN);

        int level;
        if (chainStart <= 0L || now - chainStart > DECAY_WINDOW_TICKS) {
            level = 0;                        // 新链：从 0 级起算
        } else {
            level = Math.min(t.getInt(K_LEVEL) + 1, MAX_LEVEL);
        }
        return baseTicks() + level * PER_LEVEL_TICKS;
    }

    private static int advanceChain(Player player) {
        CompoundTag t = tagOrCreate(player);
        long now = player.level().getGameTime();
        long chainStart = t.getLong(K_CHAIN);

        int level;
        if (chainStart <= 0L || now - chainStart > DECAY_WINDOW_TICKS) {
            chainStart = now;                 // 开一条新链，从 0 级起算
            level = 0;
        } else {
            level = Math.min(t.getInt(K_LEVEL) + 1, MAX_LEVEL);
        }

        t.putInt(K_LEVEL, level);
        t.putLong(K_CHAIN, chainStart);
        return level;
    }

    public static boolean begin(Player player) {
        if (onCooldown(player)) return false;
        tagOrCreate(player).putLong(K_UNTIL,
                player.level().getGameTime() + LOCK_TICKS);
        return true;
    }

    public static void finish(Player player) {
        int ticks = peekCooldownTicks(player);
        advanceChain(player);
        CompoundTag t = tagOrCreate(player);
        t.putLong(K_UNTIL, player.level().getGameTime() + ticks);
        t.putInt(K_LAST_SET, ticks);
        syncVanilla(player);

        VerdictDebug.log("  冷却已记 %.1fs（链内第 %d 招，等级=%d）",
                ticks / 20.0, level(player) + 1, level(player));
    }

    /**
     * 出招<b>完全没命中</b>时调用：只记一笔很短的冷却，且<b>不推进连打等级</b>。
     * <p>裁决共享冷却，而突进同时是起手与位移（走位、跨沟、抢先手时本来就没有敌人可命中），收全额会让玩家不再用它位移；
     * 但也不能免费（会退化成无限空中冲刺），所以给 {@value #MISS_COOLDOWN_RATIO} 的基准冷却且不进链。
     */
    public static void finishMissed(Player player) {
        int ticks = Math.max(1, Math.round(baseTicks() * MISS_COOLDOWN_RATIO));
        CompoundTag t = tagOrCreate(player);
        t.putLong(K_UNTIL, player.level().getGameTime() + ticks);
        t.putInt(K_LAST_SET, ticks);
        syncVanilla(player);
        VerdictDebug.log("  空放：只记 %.1fs 冷却，连打等级不变（仍为 %d）",
                ticks / 20.0, level(player));
    }

    public static void abort(Player player) {
        tagOrCreate(player).putLong(K_UNTIL, 0L);
        syncVanilla(player);
    }

    public static void reset(Player player) {
        CompoundTag t = tagOrCreate(player);
        t.putInt(K_LEVEL, 0);
        t.putLong(K_CHAIN, 0L);
        t.putLong(K_UNTIL, 0L);
        syncVanilla(player);
    }

    /**
     * 把本池的剩余时间写进<b>原版物品冷却</b>，让 HUD 转圈直接显示裁决的 CD。
     * <p>本池是<b>权威账本</b>（按层数计价，原版 {@code ItemCooldowns} 表达不了这种计价），原版那条是<b>表现与输入闸门</b>
     * （HUD 转圈；{@code MultiPlayerGameMode.useItem} 在 {@code isOnCooldown} 时连包都不发）。用重写而非增量：{@code addCooldown} 的语义是"从现在起" N tick。</p>
     */
    public static void syncVanilla(Player player) {
        int remain = remaining(player);
        Item item = getVerdictItem(player);
        if (item == null) return;

        ItemCooldowns cds = player.getCooldowns();
        if (remain <= 0) {
            if (cds.isOnCooldown(item)) cds.removeCooldown(item);
            return;
        }

        // 只在明显不一致时重写：addCooldown 的语义是"从现在起"，每 tick 无条件重写会让冷却永远结束不了；
        // 0.05 的容差够吸收两端 tick 计数的相位差，又不足以让人眼看出偏差。
        float shown = cds.getCooldownPercent(item, 0f);
        float want = Math.min(1f, remain / (float) Math.max(1, lastSetTicks(player)));
        if (Math.abs(shown - want) > TOLERANCE) {
            cds.addCooldown(item, remain);
        }
    }

    private static final String K_LAST_SET = "LastSet";

    /** 显示比例的容差：两端各有一个独立 tick 计数器，相位必有偏差，容差要大于相位差、又小于人眼能察觉的程度。 */
    private static final float TOLERANCE = 0.05f;

    private static int lastSetTicks(Player player) {
        int v = tag(player).getInt(K_LAST_SET);
        return v > 0 ? v : baseTicks();
    }

    @Nullable
    private static Item getVerdictItem(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof DomeriteLongsword) return main.getItem();
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof DomeriteLongsword) return off.getItem();
        return null;
    }
}
