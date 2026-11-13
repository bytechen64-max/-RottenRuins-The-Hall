package org.bytechen.rottenruins.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.bytechen.rottenruins.client.gui.MagicScreen;
import org.bytechen.rottenruins.client.gui.SpellLoadoutScreen;
import org.bytechen.rottenruins.client.gui.SpellWheelScreen;
import org.bytechen.rottenruins.client.gui.wheel.WheelClientState;
import org.bytechen.rottenruins.client.gui.wheel.WheelData;
import org.bytechen.rottenruins.network.c2s.MagicPoolActionPacket;
import org.bytechen.rottenruins.network.c2s.WheelActionPacket;
import org.lwjgl.glfw.GLFW;

/**
 * 魔法星图的客户端入口：注册按键、按下时打开界面。键位冲突上下文用 {@link KeyConflictContext#IN_GAME}，
 * 在聊天栏/物品栏里按 M 不会被吃掉。
 * 打开界面前先发一次无条件同步请求（{@link MagicPoolActionPacket#sendSyncRequest()}）：
 * 客户端魔法池是服务端镜像、最多滞后 {@code MagicSync.POOL_INTERVAL_TICKS} tick，刚登录/重生时会显示旧内容。
 * {@link KeyMapping#consumeClick()} 会清空队列，故无论界面开没开都消费，只在没有其他界面时真正打开。
 */
@OnlyIn(Dist.CLIENT)
public final class MagicClientHandler {

    public static final KeyMapping OPEN_MAGIC = new KeyMapping(
            "key.rotten_ruins.open_magic",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.rotten_ruins");

    /** 按住打开轮盘施法界面（松手释放）。默认 R。 */
    public static final KeyMapping OPEN_WHEEL = new KeyMapping(
            "key.rotten_ruins.cast_wheel",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.rotten_ruins");

    public static final KeyMapping OPEN_LOADOUT = new KeyMapping(
            "key.rotten_ruins.open_loadout",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.rotten_ruins");

    private MagicClientHandler() {
    }

    /** 关闭轮盘后的短冷却：防止"按 R 关闭"与 KeyMapping 点击队列竞态导致立刻又打开。 */
    private static long wheelOpenSuppressedUntilMs = 0L;

    public static void suppressWheelOpen() {
        wheelOpenSuppressedUntilMs = net.minecraft.Util.getMillis() + 250L;
    }

    private static boolean wheelOpenSuppressed() {
        return net.minecraft.Util.getMillis() < wheelOpenSuppressedUntilMs;
    }

    /** 注册到 Forge 总线。在 {@code ClientPacketHandlers.init()} 里调用。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(MagicClientHandler::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(MagicClientHandler::onInteraction);
    }

    /** R 同时也是原版「交换副手」键：轮盘可用时按住 R 要吞掉交换副手，否则每次开轮盘都会顺手换掉副手物品。 */
    private static void onInteraction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        // 维持中：右键 = 结束维持（同时拦掉这次世界交互）
        if (WheelClientState.phase() == WheelClientState.Phase.MAINTAINING
                && event.getKeyMapping() == mc.options.keyUse) {
            WheelActionPacket.sendEnd();
            WheelClientState.reset();
            event.setCanceled(true);
            return;
        }
        if (event.getKeyMapping() != mc.options.keySwapOffhand || !OPEN_WHEEL.isDown()) {
            return;
        }
        // 只有在"轮盘真的会打开"的条件下才抢这个键，避免没带法杖时也不能换副手
        if (WheelData.holdingStaff() && WheelData.hasAnyWheelSpell(0)) {
            event.setCanceled(true);
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        while (OPEN_MAGIC.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                MagicPoolActionPacket.sendSyncRequest();
                mc.setScreen(new MagicScreen());
            }
        }

        // 轮盘：R 开关；打开后由 SpellWheelScreen 负责左键施法、右键/R 关闭，维持期间界面已关。
        while (OPEN_WHEEL.consumeClick()) {
            if (WheelClientState.phase() == WheelClientState.Phase.MAINTAINING) {
                WheelActionPacket.sendEnd();
                WheelClientState.reset();
                continue;
            }
            if (mc.screen == null && mc.player != null && !wheelOpenSuppressed()) {
                openWheel(mc);
            }
        }

        while (OPEN_LOADOUT.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                MagicPoolActionPacket.sendSyncRequest();
                mc.setScreen(new SpellLoadoutScreen());
            }
        }

        // KEEP 维持界面关闭后继续；结束方式统一为"右键 / 再按 R"（开关式轮盘没有长按语义）。
    }

    /** 打开轮盘前的本地前置检查：有法杖、轮盘上有主动法术。 */
    private static void openWheel(Minecraft mc) {
        if (!WheelData.holdingStaff()) {
            hint(mc, "rotten_ruins.wheel.hint.hold_staff");
            return;
        }
        if (!WheelData.hasAnyWheelSpell(0)) {
            hint(mc, "rotten_ruins.wheel.hint.no_spells");
            return;
        }
        MagicPoolActionPacket.sendSyncRequest();
        WheelClientState.beginSession(0);
        mc.setScreen(new SpellWheelScreen(0));
    }

    private static void hint(Minecraft mc, String key) {
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(key), true);
        }
    }
}
