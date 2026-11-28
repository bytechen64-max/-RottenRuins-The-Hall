package org.bytechen.rottenruins.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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
import org.bytechen.rottenruins.platform.forge.magic.network.c2s.MagicPoolActionPacket;
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

    /** 打开轮盘选法术（选中即关闭，之后右键释放）。默认 R。 */
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
        MinecraftForge.EVENT_BUS.addListener(MagicClientHandler::onScroll);
    }

    /**
     * R 同时也是原版「交换副手」键：轮盘可用时按住 R 要吞掉交换副手，否则每次开轮盘都会顺手换掉副手物品。
     * 右键不在这里拦截 —— 释放完全交给法杖的原版使用生命周期，蓄力就是按住右键。
     */
    private static void onInteraction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getKeyMapping() != mc.options.keySwapOffhand || !OPEN_WHEEL.isDown()) {
            return;
        }
        if (WheelData.holdingStaff() && WheelData.hasAnyWheelSpell(0)) {
            event.setCanceled(true);
        }
    }

    /** 按住左 Alt 滚轮：在轮盘方位之间快捷换法术（只换选中，不释放）。 */
    private static void onScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || !Screen.hasAltDown()) {
            return;
        }
        if (!WheelData.holdingStaff() || event.getScrollDelta() == 0.0D) {
            return;
        }
        if (cycleSelection(event.getScrollDelta() > 0.0D ? -1 : 1)) {
            event.setCanceled(true);
        }
    }

    /** 沿轮盘方位循环找下一个有法术的格子并选中；成功时播报法术名。 */
    private static boolean cycleSelection(int step) {
        int page = WheelClientState.hasSelection() ? WheelClientState.selectedPage() : 0;
        WheelData.Slot[] slots = WheelData.page(page);
        int start = WheelClientState.hasSelection() ? WheelClientState.selectedIndex() : 0;
        for (int i = 1; i <= slots.length; i++) {
            int index = Math.floorMod(start + step * i, slots.length);
            WheelData.Slot slot = slots[index];
            if (slot != null && slot.occupied() && slot.spell() != null) {
                select(page, index, slot);
                return true;
            }
        }
        return false;
    }

    /** 选中一个方位：本地记下并同步给服务端，右键放的才是它。 */
    static void select(int page, int index, WheelData.Slot slot) {
        WheelClientState.rememberSelection(page, index, slot.poolSlot());
        MagicPoolActionPacket.sendSelect(slot.poolSlot());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(slot.stack().getHoverName(), true);
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

        // 轮盘只负责选方位（SpellWheelScreen 左键选中即关闭），释放仍由法杖右键负责。
        while (OPEN_WHEEL.consumeClick()) {
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
                    Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }
}
