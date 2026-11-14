package org.bytechen.rottenruins.client.gui.creative;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;

import java.util.Map;

/**
 * 分区行使用冷蓝灰金属铭牌与固定银色标题（D + 1）。
 * Foreground 在槽位高亮之后绘制，不透明的 162×18 铭牌同时遮住空槽边框与高亮。
 * GuiGraphics 已有面板平移，贴图和文字均使用面板相对坐标。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreativeTabDividerRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HallMod.MODID,
            "textures/gui/creative_inventory/dividers.png");
    private static final int GRID_X = 8;
    private static final int GRID_Y = 17;
    private static final int GRID_W = 162;
    private static final int ROW_H = 18;
    private static final int TITLE_COLOR = 0xFFBDCCDF;
    // Keep text away from the border and the rivets at each end.
    private static final int TITLE_WIDTH = GRID_W - 20;

    private CreativeTabDividerRenderer() {}

    @SubscribeEvent
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof SectionedCreativeScreen screen)) {
            return;
        }
        Map<Integer, Component> dividers = screen.splendiding$dividerRows();
        if (dividers.isEmpty()) {
            return;
        }

        GuiGraphics gui = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int scrollRow = screen.splendiding$scrollRow();
        for (int local = 0; local < 5; local++) {
            Component title = dividers.get(scrollRow + local);
            if (title == null) {
                continue;
            }
            int y = GRID_Y + local * ROW_H;
            // The nameplate occupies UV (0,0)..(162,18) in the 256x256 atlas.
            gui.blit(TEXTURE, GRID_X, y, 0, 0, GRID_W, ROW_H);
            String text = title.getString();
            if (font.width(text) > TITLE_WIDTH) {
                String ellipsis = "...";
                text = font.plainSubstrByWidth(text, TITLE_WIDTH - font.width(ellipsis)) + ellipsis;
            }
            gui.drawString(font, text, GRID_X + (GRID_W - font.width(text)) / 2,
                    y + (ROW_H - font.lineHeight) / 2, TITLE_COLOR, true);
        }
    }
}
