package org.bytechen.rottenruins.client.magic;

import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.bytechen.rottenruins.gameplay.magic.casting.MagicHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.api.magic.CastResult;
import org.bytechen.rottenruins.client.gui.wheel.WheelClientState;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import java.util.Locale;
import static org.bytechen.rottenruins.platform.forge.magic.network.s2c.WheelFeedbackPacket.*;

/** Wheel feedback interpretation and presentation, loaded only by the client entry. */
public final class WheelFeedbackHandler {
    private WheelFeedbackHandler() {}
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
        var spell = org.bytechen.rottenruins.gameplay.magic.stats.MagicStats.spellOf(stack);
        return spell != null
                && spell.getUsingType() == org.bytechen.rottenruins.core.magic.model.UsingType.KEEP;
    }

    private static net.minecraft.world.item.ItemStack stackAt(int page, int index) {
        var pool = org.bytechen.rottenruins.gameplay.magic.casting.MagicHandle
                .getPool(Minecraft.getInstance().player);
        if (pool == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        int poolSlot = pool.getWheelSlot(page, index);
        return poolSlot < 0 ? net.minecraft.world.item.ItemStack.EMPTY : pool.getStackInSlot(poolSlot);
    }

    private static int poolSlotOf(int page, int index) {
        var pool = org.bytechen.rottenruins.gameplay.magic.casting.MagicHandle
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
