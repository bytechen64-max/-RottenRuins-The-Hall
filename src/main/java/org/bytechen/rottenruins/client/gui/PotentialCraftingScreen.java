package org.bytechen.rottenruins.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.bytechen.rottenruins.overworld.inventory.PotentialCraftingMenu;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 势能合成台界面。目前无贴图，全部用几何绘制。
 *
 * <p>合成时 16 个环形槽一边自转、一边向圆心靠拢：直接改写 {@link Slot} 的 {@code x/y}
 * （这两个字段由 AT 放开了 final），所以物品与点击判定一起动。
 * 静止时半径/角度与菜单给的布局一致，不会有形变。</p>
 */
public final class PotentialCraftingScreen extends AbstractContainerScreen<PotentialCraftingMenu> {

    /** 收缩到位的半径（像素）。 */
    private static final float CONVERGED_RADIUS = 20.0f;
    /** 环形自转的基础角速度（弧度/秒）。 */
    private static final float SPIN_SPEED = 1.5f;
    /** 外圈进度弧半径（像素）。 */
    private static final float ARC_RADIUS = 70.0f;

    private static final int COLOR_PANEL = 0xF0161B26;
    private static final int COLOR_PANEL_EDGE = 0xFF05070C;
    private static final int COLOR_LINE = 0xFF2C4358;
    private static final int COLOR_SLOT = 0xFF101724;
    private static final int COLOR_SLOT_EDGE = 0xFF27364A;
    private static final int COLOR_SLOT_EDGE_HOT = 0xFF3E5C7C;
    private static final int COLOR_ARC_BACK = 0xFF1E2A3A;
    private static final int COLOR_ARC = 0xFF43C8FF;
    private static final int COLOR_POWER = 0xFF7FE3FF;
    private static final int COLOR_WARN = 0xFFFF5A5A;

    /** 帧间平滑后的进度，避免 20Hz 同步带来的跳动。 */
    private float smoothedProgress;

    public PotentialCraftingScreen(PotentialCraftingMenu menu, @NotNull Inventory inventory,
                                   @NotNull Component title) {
        super(menu, inventory, title);
        this.imageWidth = PotentialCraftingMenu.IMAGE_WIDTH;
        this.imageHeight = PotentialCraftingMenu.IMAGE_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = PotentialCraftingMenu.IMAGE_HEIGHT - 94;
        updateRingLayout();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateRingLayout();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** 自转 + 向心收缩；未合成时回到静止布局。 */
    private void updateRingLayout() {
        smoothedProgress = Mth.lerp(0.22f, smoothedProgress, menu.progressFraction());
        boolean crafting = menu.isCrafting();
        float progress = crafting ? Mth.clamp(smoothedProgress, 0.0f, 1.0f) : 0.0f;

        float seconds = (float) ((System.currentTimeMillis() % 1_000_000L) / 1000.0d);
        // 越接近完成转得越快：0.5 倍速起步，满进度 1.5 倍速
        float spin = crafting ? seconds * SPIN_SPEED * (0.5f + progress) : 0.0f;
        float radius = Mth.lerp(progress, PotentialCraftingMenu.RING_RADIUS, CONVERGED_RADIUS);

        for (int index = 0; index < PotentialCraftingTableBlockEntity.RING_SLOTS; index++) {
            double angle = Math.toRadians(PotentialCraftingMenu.ringAngleDegrees(index)) + spin;
            Slot slot = menu.slots.get(index);
            slot.x = PotentialCraftingMenu.RING_CENTER_X + Mth.floor(Math.cos(angle) * radius) - 8;
            slot.y = PotentialCraftingMenu.RING_CENTER_Y + Mth.floor(Math.sin(angle) * radius) - 8;
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;

        graphics.fill(left - 1, top - 1, left + imageWidth + 1, top + imageHeight + 1, COLOR_PANEL_EDGE);
        graphics.fill(left, top, left + imageWidth, top + imageHeight, COLOR_PANEL);

        int cx = left + PotentialCraftingMenu.RING_CENTER_X;
        int cy = top + PotentialCraftingMenu.RING_CENTER_Y;

        drawArc(graphics, cx, cy, PotentialCraftingMenu.RING_RADIUS, 0.0f, 360.0f, COLOR_LINE);
        drawArc(graphics, cx, cy, CONVERGED_RADIUS, 0.0f, 360.0f, 0xFF1C2836);

        for (int index = 0; index < PotentialCraftingTableBlockEntity.RING_SLOTS; index++) {
            Slot slot = menu.slots.get(index);
            drawSlotFrame(graphics, left + slot.x, top + slot.y, COLOR_SLOT, COLOR_SLOT_EDGE);
        }
        Slot output = menu.slots.get(PotentialCraftingTableBlockEntity.RING_SLOTS);
        drawSlotFrame(graphics, left + output.x, top + output.y, 0xFF1A2233, COLOR_SLOT_EDGE_HOT);

        drawArc(graphics, cx, cy, ARC_RADIUS, -90.0f, 270.0f, COLOR_ARC_BACK);
        if (menu.isCrafting()) {
            drawArc(graphics, cx, cy, ARC_RADIUS, -90.0f, -90.0f + 360.0f * smoothedProgress, COLOR_ARC);
        }

        int invLineY = top + 152;
        graphics.fill(left + 7, invLineY, left + imageWidth - 7, invLineY + 1, COLOR_LINE);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE6F3FF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x8FA6BF, false);

        int cx = PotentialCraftingMenu.RING_CENTER_X;
        int power = menu.power();

        Component powerText = Component.translatable("gui.rotten_ruins.potential_crafting.power",
                power, 15);
        graphics.drawString(font, powerText, cx - font.width(powerText) / 2, 22,
                power > 0 ? COLOR_POWER : COLOR_WARN, false);

        Component status;
        int color = 0xAFC4D8;
        if (menu.isCrafting()) {
            status = Component.translatable("gui.rotten_ruins.potential_crafting.remaining",
                    remainingSeconds());
            color = 0xCFE9FF;
        } else if (power <= 0) {
            status = Component.translatable("gui.rotten_ruins.potential_crafting.needs_power")
                    .withStyle(ChatFormatting.RED);
            color = COLOR_WARN;
        } else if (menu.ringEmpty()) {
            status = Component.translatable("gui.rotten_ruins.potential_crafting.idle");
        } else {
            status = Component.translatable("gui.rotten_ruins.potential_crafting.no_recipe");
            color = 0xFFD27A;
        }
        graphics.drawString(font, status, cx - font.width(status) / 2, 144, color, false);
    }

    private int remainingSeconds() {
        float left = menu.totalTicks() * (1.0f - Mth.clamp(smoothedProgress, 0.0f, 1.0f));
        return Math.max(1, Mth.ceil(left / 20.0f));
    }

    private static void drawSlotFrame(@NotNull GuiGraphics graphics, int x, int y,
                                      int fill, int edge) {
        graphics.fill(x, y, x + 18, y + 18, edge);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, fill);
    }

    /** 圆弧（0° = 正右，顺时针）。 */
    private static void drawArc(@NotNull GuiGraphics graphics, int cx, int cy, float radius,
                                float fromDeg, float toDeg, int color) {
        int steps = Math.max(8, (int) (Math.abs(toDeg - fromDeg) / 3.0f));
        for (int step = 0; step <= steps; step++) {
            double angle = Math.toRadians(fromDeg + (toDeg - fromDeg) * step / (float) steps);
            int px = cx + Mth.floor(Math.cos(angle) * radius);
            int py = cy + Mth.floor(Math.sin(angle) * radius);
            graphics.fill(px, py, px + 2, py + 2, color);
        }
    }
}
