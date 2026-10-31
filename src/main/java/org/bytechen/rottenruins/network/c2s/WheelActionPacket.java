package org.bytechen.rottenruins.network.c2s;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.bytechen.rottenruins.api.magic.CastResult;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.network.MagicSync;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;
import org.bytechen.rottenruins.network.s2c.WheelFeedbackPacket;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.State;

/**
 * 客户端 → 服务端：<b>轮盘施法界面</b>的全部操作（配置映射 + 施法手势）。
 *
 * <p>和 {@code MagicPoolActionPacket} 同一个思路：走 {@link UniversalPacket} 的
 * action 分发，<b>不新增包序号</b>（那是网络层唯一一处两端必须严格同序的地方）。</p>
 *
 * <p>服务端权威：客户端发的只是申请。轮盘方位指向哪个魔法池槽、能不能放、有没有冷却/蓝，
 * 全部在 {@link #handle} 里重新查一遍；客户端本地那份只是同步镜像。</p>
 *
 * <h3>动作</h3>
 * <ul>
 *   <li>{@link #ACT_CONFIG}：保存一整页"方位 → 池槽位"映射（装载界面保存时发）</li>
 *   <li>{@link #ACT_BEGIN}：开始蓄力 / 开始维持（选中扇区停留阈值后发）</li>
 *   <li>{@link #ACT_CAST}：松手释放 {@code IMMEDIATELY} 法术</li>
 *   <li>{@link #ACT_END}：松手结束蓄力 / 维持</li>
 *   <li>{@link #ACT_CANCEL}：取消（右键 / Esc / 移出扇区）</li>
 * </ul>
 */
public final class WheelActionPacket {

    /** action 名（UniversalPacket 用）。 */
    public static final String ACTION = "rotten_ruins:wheel_action";

    public static final int ACT_CONFIG = 0;
    public static final int ACT_BEGIN = 1;
    public static final int ACT_CAST = 2;
    public static final int ACT_END = 3;
    public static final int ACT_CANCEL = 4;

    private WheelActionPacket() {
    }

    // ══════════════════════════════════════════════════════════════
    //  发送端（客户端）
    // ══════════════════════════════════════════════════════════════

    /** 保存一页轮盘映射（-1 = 空位）。 */
    public static void sendConfig(int page, int[] wheelSlots) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", ACT_CONFIG);
        tag.putInt("pg", page);
        tag.putIntArray("slots", wheelSlots == null ? new int[0] : wheelSlots);
        send(tag);
    }

    public static void sendBegin(int page, int index) {
        sendSimple(ACT_BEGIN, page, index);
    }

    public static void sendCast(int page, int index) {
        sendSimple(ACT_CAST, page, index);
    }

    public static void sendEnd() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", ACT_END);
        send(tag);
    }

    public static void sendCancel() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", ACT_CANCEL);
        send(tag);
    }

    private static void sendSimple(int action, int page, int index) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", action);
        tag.putInt("pg", page);
        tag.putInt("ix", index);
        send(tag);
    }

    private static void send(CompoundTag tag) {
        NetworkHelper.sendToServer(new UniversalPacket(ACTION, UniversalPacketData.ofNbt(tag)));
    }

    // ══════════════════════════════════════════════════════════════
    //  接收端（服务端）
    // ══════════════════════════════════════════════════════════════

    /** 在 {@code NetworkHelper.register()} 里调用（公共初始化阶段，两端都会加载）。 */
    public static void register() {
        PacketActionRegistry.register(ACTION, (data, ctx) -> {
            if (!(data.getValue() instanceof CompoundTag tag)) {
                return;
            }
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                handle(sender, tag);
            }
        });
    }

    private static void handle(ServerPlayer player, CompoundTag tag) {
        if (player == null || player.isSpectator()) {
            return;
        }
        PlayerMagicPool pool = MagicHandle.getPool(player);
        if (pool == null) {
            return;
        }
        int action = tag.getInt("a");
        int page = tag.getInt("pg");
        int index = tag.getInt("ix");

        switch (action) {
            case ACT_CONFIG -> handleConfig(player, pool, page, tag.getIntArray("slots"));
            case ACT_BEGIN -> handleGesture(player, pool, WheelFeedbackPacket.ACT_BEGIN, page, index);
            case ACT_CAST -> handleGesture(player, pool, WheelFeedbackPacket.ACT_CAST, page, index);
            case ACT_END -> {
                CastResult result = MagicHandle.handleWheelEnd(player);
                WheelFeedbackPacket.send(player, WheelFeedbackPacket.ACT_END, result, 0, -1, 0, 0.0F);
            }
            case ACT_CANCEL -> {
                CastResult result = MagicHandle.handleWheelCancel(player);
                WheelFeedbackPacket.send(player, WheelFeedbackPacket.ACT_CANCEL, result, 0, -1, 0, 0.0F);
            }
            default -> {
            }
        }
    }

    private static void handleConfig(ServerPlayer player, PlayerMagicPool pool, int page, int[] slots) {
        if (!PlayerMagicPool.isValidWheelPage(page) || slots == null
                || slots.length != PlayerMagicPool.WHEEL_SLOTS_PER_PAGE) {
            WheelFeedbackPacket.send(player, WheelFeedbackPacket.ACT_CONFIG,
                    CastResult.INVALID_SLOT, page, -1, 0, 0.0F);
            return;
        }
        int slotsCount = pool.getSlots();
        boolean[] used = new boolean[Math.max(1, slotsCount)];
        java.util.Set<net.minecraft.resources.ResourceLocation> usedSpells = new java.util.HashSet<>();
        int[] clean = new int[PlayerMagicPool.WHEEL_SLOTS_PER_PAGE];
        for (int i = 0; i < clean.length; i++) {
            int poolSlot = slots[i];
            if (poolSlot < 0) {
                clean[i] = -1;
                continue;
            }
            // 服务端重新校验：槽位存在、非空、主动法术、不重复（池槽 + 法术注册名）
            if (poolSlot >= slotsCount || used[poolSlot]) {
                clean[i] = -1;
                continue;
            }
            var stack = pool.getStackInSlot(poolSlot);
            if (stack.isEmpty() || !MagicStats.isSpell(stack) || MagicStats.isPassive(stack)) {
                clean[i] = -1;
                continue;
            }
            net.minecraft.resources.ResourceLocation spellId = MagicStats.magicId(stack);
            if (spellId != null && !usedSpells.add(spellId)) {
                clean[i] = -1;
                continue;
            }
            used[poolSlot] = true;
            clean[i] = poolSlot;
        }
        pool.setWheelPage(page, clean);
        MagicSync.pushPool(player, true);
        WheelFeedbackPacket.send(player, WheelFeedbackPacket.ACT_CONFIG,
                CastResult.SUCCESS, page, -1, 0, 0.0F);
    }

    private static void handleGesture(ServerPlayer player, PlayerMagicPool pool,
                                      int feedbackAction, int page, int index) {
        if (!PlayerMagicPool.isValidWheelPosition(page, index)) {
            WheelFeedbackPacket.send(player, feedbackAction, CastResult.INVALID_SLOT,
                    page, index, 0, 0.0F);
            return;
        }
        int poolSlot = pool.getWheelSlot(page, index);
        if (poolSlot < 0) {
            WheelFeedbackPacket.send(player, feedbackAction, CastResult.NO_SPELL,
                    page, index, 0, 0.0F);
            return;
        }
        CastResult result = feedbackAction == WheelFeedbackPacket.ACT_BEGIN
                ? MagicHandle.handleWheelBegin(player, poolSlot)
                : MagicHandle.handleWheelCast(player, poolSlot);
        State state = MagicHandle.peekState(player);
        int heldTicks = state != null ? state.getHeldTicks() : 0;
        float charge = 0.0F;
        var stack = pool.getStackInSlot(poolSlot);
        if (!stack.isEmpty() && state != null && state.isCharging()) {
            int total = MagicStats.chargeTicks(player, stack);
            charge = total <= 0 ? 1.0F : Math.min(1.0F, heldTicks / (float) total);
        }
        // 回执带 result 原样（客户端用它决定"蓄力/维持/失败"的界面状态）
        WheelFeedbackPacket.send(player, feedbackAction, result, page, index,
                heldTicks, charge);
        if (result.isSuccess()) {
            MagicSync.pushPool(player, true);
        }
    }
}
