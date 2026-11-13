package org.bytechen.rottenruins.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.effect.customEffect.CustomEffectInstance;

import java.util.List;

/**
 * 自定义效果的 UI 挂载点：游戏内 HUD（屏幕左侧）与背包界面（GUI 面板左侧）。
 *
 * <p>原版把效果画在右上角 / 背包右侧；这里统一放在<b>左侧</b>，避免和原版效果栏打架。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CustomEffectHud {

    /** HUD 列的左上角。 */
    private static final int HUD_X = 6;
    private static final int HUD_Y = 8;

    /** 背包界面里距 GUI 左边缘的距离。 */
    private static final int INVENTORY_INSET = 4;

    private CustomEffectHud() {
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "custom_effects",
                (gui, graphics, partialTick, width, height) -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    Player player = minecraft.player;
                    if (player == null || minecraft.options.hideGui || minecraft.screen != null) {
                        return;
                    }
                    List<CustomEffectInstance> effects = CustomEffectHudRenderer.activeOf(
                            (net.minecraft.client.player.LocalPlayer) player);
                    if (effects.isEmpty()) {
                        return;
                    }
                    CustomEffectHudRenderer.drawColumn(graphics, effects, HUD_X, HUD_Y, true);
                });
    }

    /**
     * 背包界面：画在 GUI 面板左侧。
     *
     * <p>用 {@code ScreenEvent.Render.Post} 而不是 mixin：拿得到 GUI 的 {@code leftPos}，
     * 也不用改原版的 {@code EffectRenderingInventoryScreen}。</p>
     */
    @Mod.EventBusSubscriber(modid = HallMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class InventoryHook {

        private InventoryHook() {
        }

        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            if (!(event.getScreen() instanceof InventoryScreen screen)) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            List<CustomEffectInstance> effects = CustomEffectHudRenderer.activeOf(minecraft.player);
            if (effects.isEmpty()) {
                return;
            }
            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            int x = left - CustomEffectHudRenderer.BOX - INVENTORY_INSET;
            CustomEffectHudRenderer.drawColumn(event.getGuiGraphics(), effects, x,
                    top + INVENTORY_INSET, false);
        }
    }
}
