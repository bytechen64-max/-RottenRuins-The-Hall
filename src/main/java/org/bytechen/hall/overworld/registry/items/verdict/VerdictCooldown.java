package org.bytechen.hall.overworld.registry.items.verdict;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.bytechen.hall.overworld.registry.items.DomeriteLongsword;
import org.jetbrains.annotations.Nullable;

/**
 * 天穹裁决 —— <b>三技能共享的裁决冷却池</b>。
 *
 * <h3>为什么共享而不是各自冷却</h3>
 * <p>如果三个技能各自独立冷却，玩家可以「短按突进 → 立刻蓄力光柱 → 潜行放领域」连打一套，
 * 三个大招在同两秒内全部落地 —— 这套组合的强度不是任何单招能代表的，
 * 而且没法针对某一招做平衡。共享一个池之后，<b>用掉一招就等于用掉了整个裁决</b>，
 * 操作上的取舍才真正成立。</p>
 *
 * <h3>层数机制</h3>
 * <p>基础冷却默认 {@value #BASE_TICKS} tick（1.5 秒，可在配置里改）。
 * 相邻两招间隔在 {@value #DECAY_WINDOW_TICKS} tick（8 秒）以内的算同一条"链"，
 * 链内每多一招加 {@link #PER_LEVEL_TICKS} tick（2 秒），封顶 {@link #MAX_LEVEL} 级：</p>
 * <pre>
 *   本次连打的第 1 招（等级 0） → 1.5s
 *   第 2 招（等级 1）           → 3.5s
 *   第 3 招及以后（封顶）       → 5.5s
 *
 *   停手超过 8 秒 → 链断开，下一招直接回到 1.5s
 * </pre>
 *
 * <p>于是"连招"不是被禁止，而是<b>按代价计价</b>：干净地隔 8 秒出一招
 * 永远只要 1.5 秒冷却；想在 8 秒内连打就付 3.5~5.5 秒。
 * 这条曲线把"贪"变成了玩家的选择，而不是设计者砍掉的选项。</p>
 *
 * <p>上限刻意压到 {@value #MAX_LEVEL} 级：三个技能<b>共享</b>这一个池，
 * 所以用掉一招就等于整套技能都在冷却。上限太高时（曾经是 4 级 = +8 秒）
 * 实战里表现为"放完一招要等 10 秒才能再放任何东西"，玩家体感就是"冷却太长"。</p>
 *
 * <h3>存储位置</h3>
 * <p>挂在 {@link Player#getPersistentData()} 下的 {@value #ROOT} 标签。
 * 选它而不是 Forge Capability 的原因：数据量极小（2 个 int + 2 个 long），
 * 且需要在玩家死亡、换维度、重登后都保留 —— 持久数据默认就是这个语义，
 * 不需要另写序列化与 {@code PlayerEvent.Clone} 的搬运代码。</p>
 */
public final class VerdictCooldown {

    /** 持久数据根标签。 */
    public static final String ROOT = "HallVerdict";

    private static final String K_LEVEL = "Level";
    /** 本次连打的起点（全局时间）。<b>只在链开始时写一次</b> —— 这是等级能回落的关键，见 {@link #nextCooldownTicks}。 */
    private static final String K_CHAIN = "Chain";
    private static final String K_UNTIL = "Until";    // 冷却结束的全局时间

    /**
     * 基础冷却。
     *
     * <p><b>不再是常量</b>：它走 {@link VerdictTuning#baseCooldownTicks()}，
     * 于是玩家可以在配置里把整套技能的节奏整体拉快或拉慢，
     * 而"连打递增（{@link #PER_LEVEL_TICKS}）"那条曲线保持不变 ——
     * 调基础值不会把连打惩罚的相对比例改掉。</p>
     */
    public static int baseTicks() {
        return VerdictTuning.baseCooldownTicks();
    }

    /**
     * 基础冷却的默认值（tick）= {@value #BASE_TICKS_DEFAULT} tick = 2 秒。
     *
     * <p>从 3 秒降到 2 秒，依据是实测日志：玩家在战斗中连续短按突进的间隔
     * 实测是 3.8~6.0 秒，而当时的链路窗口只有 5 秒 —— 也就是<b>正常节奏的连招
     * 都会一直被判成"链内"</b>，等级一路升到顶。降低基础值 + 拉长窗口之后，
     * "正常打"恒定 2 秒，"刻意连打"才升到 4/6/8/10 秒。</p>
     */
    public static final int BASE_TICKS_DEFAULT = 40;
    /** 向后兼容的名字（旧代码/文档里引用过 BASE_TICKS）。 */
    public static final int BASE_TICKS = BASE_TICKS_DEFAULT;
    /** 每层额外冷却：2 秒。 */
    public static final int PER_LEVEL_TICKS = 40;
    /**
     * 等级上限。
     *
     * <p>从 4 降到 {@value #MAX_LEVEL}。理由是玩家反馈"冷却还是太长"，
     * 而日志显示封顶时<b>每一招都吃满基础+8 秒</b>：三技能共享一个池，
     * 等于用掉一招就把整套技能锁死 10 秒，这在实战里无法接受。</p>
     *
     * <p>降到 2 级之后曲线是 <b>1.5 / 3.5 / 5.5 秒</b>：连打三招才摸到上限，
     * 而且上限本身也不到原来的一半。<b>"用掉一招就等于用掉整个裁决"这条设计不变</b>
     * —— 变的是连打的惩罚强度，而不是共享这件事。</p>
     */
    public static final int MAX_LEVEL = 2;

    /**
     * 窗口期：相邻两招间隔超过这么久，就算开一条新的链（等级归零）。
     *
     * <p>5 秒 → {@value #DECAY_WINDOW_TICKS} tick（8 秒）。实测玩家在战斗中的
     * 自然出招间隔是 3.8~6.0 秒，5 秒的窗口会让大多数正常连招都判成"链内"。
     * 拉长到 8 秒之后，"打赢一波之后停下来走两步"就足以让链条断掉。</p>
     */
    public static final int DECAY_WINDOW_TICKS = 160;

    /**
     * 出招期间用的"锁死"冷却值。
     * <p>在装备具体冷却之前先写一个远超实战的值，把窗口堵死 ——
     * 这样即便某个技能实现里抛了异常没走到 {@link #finish}，
     * 冷却也不会变成 0 而允许连点。</p>
     */
    private static final int LOCK_TICKS = 20 * 60;

    private VerdictCooldown() {}

    // ── 读写 ────────────────────────────────────────────────────────

    private static CompoundTag tag(Player player) {
        return player.getPersistentData().getCompound(ROOT);
    }

    private static CompoundTag tagOrCreate(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT)) root.put(ROOT, new CompoundTag());
        return root.getCompound(ROOT);
    }

    /**
     * 本次连打已经打了几招（<b>0 = 本次连打的第一招</b>）。
     * <p>见 {@link #nextCooldownTicks} 里那段关于"为什么记链起点而不是记上次出招"的说明。</p>
     */
    public static int level(Player player) {
        return tag(player).getInt(K_LEVEL);
    }

    /** 剩余冷却 tick；0 表示可以出招。 */
    public static int remaining(Player player) {
        long until = tag(player).getLong(K_UNTIL);
        long now = player.level().getGameTime();
        return (int) Math.max(0L, until - now);
    }

    /**
     * 是否处于冷却中。
     * <p>注意用 {@code > 0} 而不是 {@code >=}：{@code remaining} 已经做了 0 下限钳制。</p>
     */
    public static boolean onCooldown(Player player) {
        return remaining(player) > 0;
    }

    /**
     * 本招应记的冷却 tick 数。
     *
     * <h3>计的是"链"，不是"距上次出招"</h3>
     * <p>这里有一个很容易写错、而且症状非常像"手感差"的坑，值得写清楚：</p>
     * <pre>
     *   错误写法：记录"上次出招时间"，用 now - 上次 &gt; 窗口 判断是否降级。
     *   ⇒ 窗口是 5 秒、冷却也是 3~11 秒，而**出招的瞬间就把"上次出招时间"刷成了 now**，
     *     于是 now - 上次 永远≈0，等级<b>永远不回落</b>。
     *     连打几招之后就永久钉在最高档，玩家体感是"冷却莫名其妙变得特别长"。
     *
     *   正确写法：记录"本次连打的起点"，相邻两招间隔超过窗口才算开一条新链。
     *   ⇒ 出招本身不给链续命；一旦停手超过窗口，下一招就自动回到最短冷却。
     * </pre>
     *
     * <p>于是这条曲线是：<b>干净出招永远只要基础冷却；只有真的连打才逐级加价。</b></p>
     *
     * @return 本招的冷却 tick 数（同时已把等级 +1 写回）
     */
    public static int nextCooldownTicks(Player player) {
        CompoundTag t = tagOrCreate(player);
        long now = player.level().getGameTime();

        long chainStart = t.getLong(K_CHAIN);
        int level;

        // chainStart == 0 表示还没有过任何一次连打（也是首次出招）
        if (chainStart <= 0L || now - chainStart > DECAY_WINDOW_TICKS) {
            chainStart = now;                 // 开一条新链，从 0 级起算
            level = 0;
        } else {
            level = Math.min(t.getInt(K_LEVEL) + 1, MAX_LEVEL);
        }

        int ticks = baseTicks() + level * PER_LEVEL_TICKS;

        t.putInt(K_LEVEL, level);
        t.putLong(K_CHAIN, chainStart);
        return ticks;
    }

    // ── 三态出招的生命周期 ───────────────────────────────────────────

    /**
     * 出招前调用：万一技能实现半路炸了，冷却也不会是 0 而允许连点。
     * @return false 表示当前在冷却中，调用方应当直接放弃这次出招
     */
    public static boolean begin(Player player) {
        if (onCooldown(player)) return false;
        tagOrCreate(player).putLong(K_UNTIL,
                player.level().getGameTime() + LOCK_TICKS);
        return true;
    }

    /** 出招成功落地后调用：把锁死的冷却替换成按等级计价的真实冷却。 */
    public static void finish(Player player) {
        int ticks = nextCooldownTicks(player);
        CompoundTag t = tagOrCreate(player);
        t.putLong(K_UNTIL, player.level().getGameTime() + ticks);
        t.putInt(K_LAST_SET, ticks);          // 记下本次冷却总长度，供显示比例换算
        syncVanilla(player);

        // 诊断：这是"冷却太长"唯一能被量化的地方 —— 等级与实际 tick 数都写出来，
        // 玩家测完直接能读出"这一招到底记了多少"，不用再靠体感描述。
        VerdictDebug.log("  冷却已记 %.1fs（链内第 %d 招，等级=%d）",
                ticks / 20.0, level(player) + 1, level(player));
    }

    /** 放弃本次出招（比如参数不合法）：立刻解除锁死，不消耗等级也不进冷却。 */
    public static void abort(Player player) {
        tagOrCreate(player).putLong(K_UNTIL, 0L);
        syncVanilla(player);
    }

    /** 调试用：清空冷却与等级。 */
    public static void reset(Player player) {
        CompoundTag t = tagOrCreate(player);
        t.putInt(K_LEVEL, 0);
        t.putLong(K_CHAIN, 0L);
        t.putLong(K_UNTIL, 0L);
        syncVanilla(player);
    }

    // ══════════════════════════════════════════════════════════════
    //  与原版物品冷却（ItemCooldowns）的同步
    // ══════════════════════════════════════════════════════════════

    /**
     * 把本池的剩余时间写进<b>原版物品冷却</b>，让 HUD 那个冷却转圈直接显示裁决的 CD。
     *
     * <h3>为什么两个都要</h3>
     * <p>原版 {@code ItemCooldowns} 只有一个 {@code Item → 剩余 tick} 的表，
     * 它的 API 是"从现在起冷却 N tick"，<b>表达不了</b>"这次剩 4.2 秒、上次剩 2 秒"
     * 这种按层数变化的计价。而本池的等级计价正是这套技能的核心手感
     * （连打要付更长的代价），所以它必须保留为<b>权威</b>账本。</p>
     *
     * <p>反过来，只靠自建池的话玩家没有任何视觉反馈 —— 原版那个转圈是
     * 玩家唯一认得的"还没好"信号，不接上去就得靠动作栏文字，
     * 那既容易漏看、又不像原版。所以两边都写：</p>
     * <ul>
     *   <li><b>权威账本</b> = 本池（进程内 NBT，逐 tick 可查，支持层数）；</li>
     *   <li><b>表现与输入闸门</b> = 原版冷却（HUD 转圈；且
     *       {@code MultiPlayerGameMode.useItem} 在 {@code isOnCooldown} 时
     *       直接本地 PASS、连包都不发，所以它天然就是右键的闸门）。</li>
     * </ul>
     *
     * <p>本方法在<b>每次冷却值变化、以及每 tick 的 {@link #LOCK_TICKS} 期间</b>被调用，
     * 用的是"重新写入"而不是增量修改 —— {@code addCooldown} 的语义是
     * <i>从现在起</i> N tick，所以重写总是安全的，且两边的剩余量会保持收敛。</p>
     *
     * <p>只读客户端显示、不改服务端逻辑，所以两端都可以调；但历史上它只在服务端被调用，
     * 因为服务端才是权威来源。</p>
     */
    public static void syncVanilla(Player player) {
        int remain = remaining(player);
        Item item = getVerdictItem(player);
        if (item == null) return;

        ItemCooldowns cds = player.getCooldowns();
        if (remain <= 0) {
            // 池已经好了：把原版那条抹掉，避免 HUD 上出现"池好了但圈还在转"
            if (cds.isOnCooldown(item)) cds.removeCooldown(item);
            return;
        }

        // 只在明显不一致时重写。addCooldown 的语义是"从现在起"，所以每次重写
        // 都会把结束时间往后推 —— 每 tick 无条件重写会让冷却永远结束不了。
        // 0.25 秒的容差足够吸收两端 tick 计数的相位差，又不足以让人眼看出偏差。
        float shown = cds.getCooldownPercent(item, 0f);
        float want = Math.min(1f, remain / (float) Math.max(1, lastSetTicks(player)));
        if (Math.abs(shown - want) > TOLERANCE) {
            cds.addCooldown(item, remain);
        }
    }

    /**
     * 上一次写进原版冷却的总长度（tick）。
     * <p>用于把"剩余 tick"换算成 HUD 的比例 —— {@link ItemCooldowns#getCooldownPercent}
     * 算的是 {@code 剩余 / 总长}，所以必须知道总长才能判断"显示是否已经一致"。</p>
     */
    private static final String K_LAST_SET = "LastSet";

    /**
     * 显示比例的容差。
     * <p>两端各有一个独立的 tick 计数器（服务端 {@code level.getGameTime()} 与
     * 客户端 {@code ItemCooldowns.tickCount}），相位必然有偏差；
     * 容差要大于这个相位差、又小于人眼能察觉的偏差。0.05 对应基准 3 秒里约 0.15 秒。</p>
     */
    private static final float TOLERANCE = 0.05f;

    private static int lastSetTicks(Player player) {
        int v = tag(player).getInt(K_LAST_SET);
        return v > 0 ? v : baseTicks();
    }

    /**
     * 取当前玩家手上那把裁决之剑对应的 {@link Item}。
     *
     * <p>优先从主手取 —— 技能本来就是拿在手上放的；手上没有时退到副手，
     * 这样"双持两把裁决剑"也能同步到正确的那一个。
     * 都没有就返回 null（不写原版冷却，池照常工作）。</p>
     */
    @Nullable
    private static Item getVerdictItem(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof DomeriteLongsword) return main.getItem();
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof DomeriteLongsword) return off.getItem();
        return null;
    }
}
