package org.bytechen.rottenruins.network.c2s;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.bytechen.rottenruins.network.MagicSync;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle;
import org.bytechen.rottenruins.api.magic.MagicStats;

/**
 * 客户端 → 服务端：<b>魔法星图 GUI</b> 的全部槽位操作。
 *
 * <h3>为什么所有操作走同一个包</h3>
 * <p>这些操作（放入 / 取出 / 槽间移动 / 选中槽 / 请求同步）都只改
 * {@link PlayerMagicPool} 这一份状态，而且永远由同一个界面发出。合成一个带
 * {@code action} 字段的包，比注册五个包少动五次
 * {@code NetworkHelper.register()} 的包序号 —— 那是整个网络层唯一一处
 * "客户端服务端必须严格同序"的地方。</p>
 *
 * <h3>服务端权威</h3>
 * <p>客户端发来的只是"申请"：槽位下标、背包下标、动作类型。真正能不能移动，
 * 由服务端重新读玩家背包与魔法池再判断 —— 客户端那份只是同步过来的镜像。</p>
 *
 * <h3>为什么不用原版 {@code AbstractContainerMenu}</h3>
 * <p>星图槽位是<b>悬浮在自绘背景上的</b>，不是原版容器网格；用原版菜单反而要为了
 * 9×3 的坐标体系去迁就。这里直接操作能力对象，界面完全自绘，反而简单。</p>
 */
public final class MagicPoolActionPacket {

    /** action 名。走项目已有的 {@code UniversalPacket} + {@code PacketActionRegistry}。 */
    public static final String ACTION = "rotten_ruins:magic_pool_action";

    /** 打开界面时请求一次全量同步，避免看到旧镜像。 */
    private static final int SYNC = 0;
    /** 选中某个槽位（之后法杖右键放的就是它）。 */
    private static final int SELECT = 1;
    /** 背包 → 魔法槽。 */
    private static final int PLACE = 2;
    /** 魔法槽 → 背包。 */
    private static final int TAKE = 3;
    /** 魔法槽 → 另一个魔法槽。 */
    private static final int MOVE = 4;

    /** 玩家背包主区 + 快捷栏的大小（{@code Inventory.items} 的长度）。 */
    private static final int INVENTORY_SIZE = 36;

    private MagicPoolActionPacket() {
    }

    // ══════════════════════════════════════════════════════════════
    // 发送端（客户端）
    // ══════════════════════════════════════════════════════════════

    /** 打开界面前请求一次全量同步。 */
    public static void sendSyncRequest() {
        send(SYNC, 0, -1);
    }

    public static void sendSelect(int poolSlot) {
        send(SELECT, poolSlot, -1);
    }

    public static void sendPlace(int inventorySlot, int poolSlot) {
        send(PLACE, poolSlot, inventorySlot);
    }

    public static void sendTake(int poolSlot) {
        send(TAKE, poolSlot, -1);
    }

    public static void sendMove(int fromPoolSlot, int toPoolSlot) {
        send(MOVE, toPoolSlot, fromPoolSlot);
    }

    private static void send(int action, int poolSlot, int inventorySlot) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", action);
        tag.putInt("p", poolSlot);
        tag.putInt("i", inventorySlot);
        NetworkHelper.sendToServer(new UniversalPacket(ACTION, UniversalPacketData.ofNbt(tag)));
    }

    // ══════════════════════════════════════════════════════════════
    // 接收端（服务端）
    // ══════════════════════════════════════════════════════════════

    /** 在 {@code NetworkHelper.register()} 里调用（公共初始化阶段，两端都会加载）。 */
    public static void register() {
        PacketActionRegistry.register(ACTION, (data, ctx) -> {
            if (data.getValue() instanceof CompoundTag tag) {
                handle(ctx.getSender(), tag);
            }
        });
    }

    /**
     * 服务端处理。<b>所有判断都在这里做</b>。
     */
    private static void handle(ServerPlayer player, CompoundTag tag) {
        if (player == null || player.isSpectator()) {
            return;
        }
        PlayerMagicPool pool = MagicHandle.getPool(player);
        if (pool == null) {
            return;
        }

        switch (tag.getInt("a")) {
            case SYNC -> MagicSync.pushAll(player);
            case SELECT -> handleSelect(player, pool, tag.getInt("p"));
            case PLACE -> handlePlace(player, pool, tag.getInt("i"), tag.getInt("p"));
            case TAKE -> handleTake(player, pool, tag.getInt("p"));
            case MOVE -> handleMove(player, pool, tag.getInt("i"), tag.getInt("p"));
            default -> {
                // 未知动作忽略，不发任何东西
            }
        }
    }

    private static void handleSelect(ServerPlayer player, PlayerMagicPool pool, int poolSlot) {
        if (!pool.isValidSlot(poolSlot)) {
            return;
        }
        pool.setSelectedSlot(poolSlot);
        MagicSync.pushPool(player, true);
    }

    private static void handlePlace(ServerPlayer player, PlayerMagicPool pool,
                                    int inventorySlot, int poolSlot) {
        if (inventorySlot < 0 || inventorySlot >= INVENTORY_SIZE) {
            return;
        }
        if (!pool.isValidSlot(poolSlot) || !pool.getStackInSlot(poolSlot).isEmpty()) {
            return;
        }

        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (stack.isEmpty() || !MagicStats.isSpell(stack) || !pool.isItemValid(poolSlot, stack)) {
            return;
        }

        // insertItem 只吃掉 1 个并返回剩余；法术都是 stacksTo(1)，正常情况剩余为空。
        ItemStack remainder = pool.insertItem(poolSlot, stack.copy(), false);
        player.getInventory().setItem(inventorySlot, remainder);
        MagicSync.pushPool(player, true);
    }

    private static void handleTake(ServerPlayer player, PlayerMagicPool pool, int poolSlot) {
        if (!pool.isValidSlot(poolSlot)) {
            return;
        }
        ItemStack taken = pool.extractItem(poolSlot, 1, false);
        if (taken.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(taken)) {
            // 背包塞不下就掉在脚下 —— 和 PlayerMagicPool 缩容时的处理一致
            player.drop(taken, false);
        }
        MagicSync.pushPool(player, true);
    }

    private static void handleMove(ServerPlayer player, PlayerMagicPool pool,
                                   int fromPoolSlot, int toPoolSlot) {
        if (!pool.isValidSlot(fromPoolSlot) || !pool.isValidSlot(toPoolSlot)
                || fromPoolSlot == toPoolSlot) {
            return;
        }
        if (!pool.getStackInSlot(toPoolSlot).isEmpty()) {
            return;
        }

        // 先取出再放入：insertItem 内部会做"重复法术"检查，
        // 如果源槽还占着，同一件法术会被判定为重复。
        ItemStack moving = pool.extractItem(fromPoolSlot, 1, false);
        if (moving.isEmpty()) {
            return;
        }
        ItemStack leftover = pool.insertItem(toPoolSlot, moving, false);
        if (!leftover.isEmpty()) {
            // 极小概率（目标在取出的同一 tick 被别的东西占住）—— 放回原处
            pool.insertItem(fromPoolSlot, leftover, false);
        }
        MagicSync.pushPool(player, true);
    }
}
