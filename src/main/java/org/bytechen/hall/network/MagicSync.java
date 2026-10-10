package org.bytechen.hall.network;

import net.minecraft.server.level.ServerPlayer;
import org.bytechen.hall.network.s2c.MagicPoolSyncPacket;
import org.bytechen.hall.network.s2c.ManaSyncPacket;
import org.bytechen.hall.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.hall.overworld.registry.capability.TwistedPoint;
import org.bytechen.hall.overworld.registry.items.magic.bases.MagicHandle;
import org.jetbrains.annotations.NotNull;

/**
 * 魔法系统的<b>服务端推送</b>：脏标记驱动 + 节流。
 *
 * <h3>为什么要节流，而不是"一脏就发"</h3>
 * <p>法力与冷却都是每 tick 都在动的量。如果 {@code isDirty()} 一变就发包，
 * 回蓝期间每个 tick 都会发一个包。这里按固定间隔检查一次脏标记，
 * 把流量压到"最多每 {@value #MANA_INTERVAL_TICKS} tick 一个法力包、
 * 每 {@value #POOL_INTERVAL_TICKS} tick 一个槽位包"。</p>
 *
 * <p>代价是最多几十毫秒的显示延迟 —— 对法力条和冷却环完全够用；
 * 而<b>能不能施法的判定始终在服务端</b>，所以这点延迟不会造成"看起来能放其实不能放"
 * 以外的任何问题（而那本来就该由服务端说了算）。</p>
 *
 * <h3>{@code force} 参数</h3>
 * <p>登录、重生、换维度、以及 {@code PlayerEvent.Clone} 之后必须<b>无条件</b>推一次：
 * 那些时刻客户端刚拿到一个新实体，本地那份是全新的空状态，
 * 而脏标记在服务端这边是干净的（什么都没改），不 force 就永远不会同步。</p>
 */
public final class MagicSync {

    /** 法力包的最小发送间隔（tick）。2 tick ≈ 100ms。 */
    public static final int MANA_INTERVAL_TICKS = 2;

    /** 槽位包的最小发送间隔（tick）。5 tick ≈ 250ms。 */
    public static final int POOL_INTERVAL_TICKS = 5;

    private MagicSync() {
    }

    /**
     * 每 tick 调用一次（服务端、且只对 {@link ServerPlayer}）。
     * <p>内部按间隔节流，调用方不用自己算周期。</p>
     */
    public static void tick(@NotNull ServerPlayer player) {
        if (player.tickCount % MANA_INTERVAL_TICKS == 0) {
            pushMana(player, false);
        }
        if (player.tickCount % POOL_INTERVAL_TICKS == 0) {
            pushPool(player, false);
        }
    }

    /** 推送法力；{@code force} 为 true 时无视脏标记。 */
    public static void pushMana(@NotNull ServerPlayer player, boolean force) {
        TwistedPoint mana = MagicHandle.getMana(player);
        if (mana == null) {
            return;
        }
        if (!force && !mana.isDirty()) {
            return;
        }
        NetworkHelper.sendToPlayer(player,
                new ManaSyncPacket(player.getId(), mana.getMana(), mana.getMaxMana()));
        mana.clearDirty();
    }

    /** 推送槽位（含选中格与冷却绝对值）；{@code force} 为 true 时无视脏标记。 */
    public static void pushPool(@NotNull ServerPlayer player, boolean force) {
        PlayerMagicPool pool = MagicHandle.getPool(player);
        if (pool == null) {
            return;
        }
        if (!force && !pool.isDirty()) {
            return;
        }
        NetworkHelper.sendToPlayer(player,
                new MagicPoolSyncPacket(player.getId(), pool.serializeNBT()));
        pool.clearDirty();
    }

    /** 两个都无条件推一次。登录 / 重生 / 换维度 / Clone 之后调。 */
    public static void pushAll(@NotNull ServerPlayer player) {
        pushMana(player, true);
        pushPool(player, true);
    }
}
