package org.bytechen.rottenruins.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.gui.wheel.WheelClientState;
import org.bytechen.rottenruins.client.gui.wheel.WheelData;
import org.bytechen.rottenruins.client.gui.wheel.WheelRenderer;

/**
 * 「魔法轮盘」的常驻 HUD 小块：{@code KEEP} 法术起手后，轮盘收拢成底部的一条
 * <b>维持条</b>（法术图标 + 名字 + 已维持秒数 + 法力条 + 结束提示）。
 *
 * <p>{@code KEEP}（按住持续）类法术（例如抑制场）在起手时轮盘就已经关闭，
 * 此时"正在维持"这件事只能由 {@link WheelClientState#phase()} 表达，所以这个
 * overlay 只做一件事：相位是 {@link WheelClientState.Phase#MAINTAINING} 时，
 * 用共享的 {@link WheelRenderer#renderMaintainBar} 把底部维持条画出来。</p>
 *
 * <h3>挂载点</h3>
 * <p>{@code RenderGuiOverlayEvent.Post} + {@link VanillaGuiOverlay#HOTBAR}：
 * 每帧只在快捷栏画完之后执行一次，和 {@code GuiShaderRenderer} 同一种写法。</p>
 *
 * <p>F1 隐藏 GUI（{@code mc.options.hideGui}）时整条跳过；
 * 没有玩家 / 不在世界里的情况也跳过。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WheelHudOverlay {

    private WheelHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGuiOverlayPost(RenderGuiOverlayEvent.Post event) {
        // HOTBAR 每帧只过一次：用它当"这帧 HUD 已经画完"的锚点。
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        if (WheelClientState.phase() != WheelClientState.Phase.MAINTAINING) {
            return;
        }
        GuiGraphics gui = event.getGuiGraphics();
        WheelRenderer.renderMaintainBar(gui,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(),
                WheelData.manaFrac(),
                WheelClientState.maintainingSeconds());
    }
}
