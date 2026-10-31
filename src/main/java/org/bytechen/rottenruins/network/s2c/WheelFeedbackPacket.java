package org.bytechen.rottenruins.network.s2c;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.api.magic.CastResult;
import org.bytechen.rottenruins.client.gui.wheel.WheelClientState;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import org.bytechen.rottenruins.network.all.UniversalPacket;
import org.bytechen.rottenruins.network.all.tools.UniversalPacketData;

import java.util.Locale;

/**
 * 服务端 → 客户端：轮盘手势的<b>结果回执</b>。
 *
 * <p>存在的意义：轮盘是"按住 R 的会话"，客户端必须知道服务端到底接受了哪一步
 * （开始蓄力成功？维持起手成功？<code>KEEP</code> 起手失败是没蓝还是没目标？），
 * 否则界面会在错误的状态里继续转圈。回执只带结果码与少量数字，不带任何状态。</p>
 *
 * <p>走项目已有的 {@code UniversalPacket} + {@code PacketActionRegistry}，
 * 和 {@code HeartLosePacket}/{@code BloodBurstPacket} 同一套；不新增包序号。</p>
 */
public final class WheelFeedbackPacket {

    public static final String ACTION = "rotten_ruins:wheel_feedback";

    /** 与 {@code WheelActionPacket} 的动作编号保持一致。 */
    public static final int ACT_CONFIG = 0;
    public static final int ACT_BEGIN = 1;
    public static final int ACT_CAST = 2;
    public static final int ACT_END = 3;
    public static final int ACT_CANCEL = 4;

    private WheelFeedbackPacket() {
    }

    // ══════════════════════════════════════════════════════════════
    //  发送端（服务端）
    // ══════════════════════════════════════════════════════════════

    public static void send(ServerPlayer player, int action, CastResult result,
                            int page, int index, int heldTicks, float chargeRatio) {
        if (player == null || result == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("a", action);
        tag.putString("r", result.name());
        tag.putInt("pg", page);
        tag.putInt("ix", index);
        tag.putInt("held", heldTicks);
        tag.putFloat("charge", chargeRatio);
        NetworkHelper.sendToPlayer(player,
                new UniversalPacket(ACTION, UniversalPacketData.ofNbt(tag)));
    }

    // ══════════════════════════════════════════════════════════════
    //  接收端（客户端）
    // ══════════════════════════════════════════════════════════════

    /** 在 {@code ClientPacketHandlers.init()} 里调用。 */
    public static void registerClient() {
        PacketActionRegistry.register(ACTION, (data, ctx) -> {
            if (!(data.getValue() instanceof CompoundTag tag)) {
                return;
            }
            if (ctx.getDirection().getReceptionSide().isClient()) {
                handleClient(tag);
            }
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int action = tag.getInt("a");
        CastResult result;
        try {
            result = CastResult.valueOf(tag.getString("r"));
        } catch (IllegalArgumentException e) {
            result = CastResult.REJECTED;
        }
        int page = tag.getInt("pg");
        int index = tag.getInt("ix");

        WheelClientState.acceptFeedback(actionName(action), result);

        switch (action) {
            case ACT_BEGIN -> {
                if (result.isSuccess()) {
                    // 服务端没告诉我们法术类型；从本地镜像读一下当前方位的 usingType
                    boolean keep = isKeepSpell(page, index);
                    if (keep) {
                        WheelClientState.toMaintaining(page, index, poolSlotOf(page, index));
                        mc.setScreen(null);
                        hint(mc, "rotten_ruins.wheel.begin.sustaining", true);
                    } else {
                        WheelClientState.toCharging();
                    }
                } else {
                    WheelClientState.reset();
                    WheelClientState.beginSession(page);
                    hint(mc, resultKey(result), false);
                }
            }
            case ACT_CAST, ACT_END -> {
                if (result.isSuccess() || action == ACT_END) {
                    WheelClientState.reset();
                } else {
                    hint(mc, resultKey(result), false);
                }
            }
            case ACT_CANCEL -> WheelClientState.reset();
            case ACT_CONFIG -> {
                if (!result.isSuccess()) {
                    hint(mc, resultKey(result), false);
                }
            }
            default -> {
            }
        }
    }

    private static boolean isKeepSpell(int page, int index) {
        var stack = stackAt(page, index);
        if (stack.isEmpty()) {
            return false;
        }
        var spell = org.bytechen.rottenruins.api.magic.MagicStats.spellOf(stack);
        return spell != null
                && spell.getUsingType() == org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType.KEEP;
    }

    private static net.minecraft.world.item.ItemStack stackAt(int page, int index) {
        var pool = org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle
                .getPool(Minecraft.getInstance().player);
        if (pool == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        int poolSlot = pool.getWheelSlot(page, index);
        return poolSlot < 0 ? net.minecraft.world.item.ItemStack.EMPTY : pool.getStackInSlot(poolSlot);
    }

    private static int poolSlotOf(int page, int index) {
        var pool = org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle
                .getPool(Minecraft.getInstance().player);
        return pool == null ? -1 : pool.getWheelSlot(page, index);
    }

    @OnlyIn(Dist.CLIENT)
    private static void hint(Minecraft mc, String key, boolean quiet) {
        if (mc.player == null) {
            return;
        }
        // 失败/阶段提示走 actionbar；success 的维持提示也放在 actionbar，不挡视线。
        mc.player.displayClientMessage(Component.translatable(key), true);
    }

    private static String resultKey(CastResult result) {
        return "rotten_ruins.wheel.result." + result.name().toLowerCase(Locale.ROOT);
    }

    private static String actionName(int action) {
        return switch (action) {
            case ACT_CONFIG -> "CONFIG";
            case ACT_BEGIN -> "BEGIN";
            case ACT_CAST -> "CAST";
            case ACT_END -> "END";
            case ACT_CANCEL -> "CANCEL";
            default -> "?";
        };
    }
}
