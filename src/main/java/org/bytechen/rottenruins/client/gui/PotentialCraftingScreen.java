package org.bytechen.rottenruins.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.bytechen.rottenruins.overworld.inventory.PotentialCraftingMenu;
import org.bytechen.rottenruins.overworld.registry.PotentialCraftingRecipes;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 奥秘仪台界面。暂无贴图，全部几何绘制。
 *
 * <p>15 个材料槽围成一圈、圆心是产物槽；点「开始合成」按钮才校验材料并开始。
 * 合成时环上的槽自转并逐渐向圆心收拢（直接改写 {@link Slot} 的 x/y，由 AT 放开）。</p>
 */
public final class PotentialCraftingScreen extends AbstractContainerScreen<PotentialCraftingMenu> {

    private static final float CONVERGED_RADIUS = 20.0f;
    private static final float SPIN_SPEED = 1.5f;
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
    private static final int COLOR_BUTTON = 0xFF1D4E6B;
    private static final int COLOR_BUTTON_EDGE = 0xFF5FD3FF;
    private static final int COLOR_BUTTON_OFF = 0xFF1A2028;
    private static final int COLOR_BUTTON_OFF_EDGE = 0xFF2E3A48;

    private float smoothedProgress;

    public PotentialCraftingScreen(PotentialCraftingMenu menu, @NotNull Inventory inventory,
                                   @NotNull Component title) {
        super(menu, inventory, title);
        this.imageWidth = PotentialCraftingMenu.IMAGE_WIDTH;
        this.imageHeight = PotentialCraftingMenu.IMAGE_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 172;
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
        float spin = crafting ? seconds * SPIN_SPEED * (0.5f + progress) : 0.0f;
        float radius = Mth.lerp(progress, PotentialCraftingMenu.RING_RADIUS, CONVERGED_RADIUS);

        for (int index = 0; index < PotentialCraftingTableBlockEntity.RING_SLOTS; index++) {
            double angle = Math.toRadians(PotentialCraftingMenu.ringAngleDegrees(index)) + spin;
            Slot slot = menu.slots.get(index);
            slot.x = PotentialCraftingMenu.RING_CENTER_X + Mth.floor(Math.cos(angle) * radius) - 8;
            slot.y = PotentialCraftingMenu.RING_CENTER_Y + Mth.floor(Math.sin(angle) * radius) - 8;
        }
    }

    // ── 按钮 ──

    private boolean insideButton(double mouseX, double mouseY) {
        double x = leftPos + PotentialCraftingMenu.BUTTON_X;
        double y = topPos + PotentialCraftingMenu.BUTTON_Y;
        return mouseX >= x && mouseX < x + PotentialCraftingMenu.BUTTON_WIDTH
                && mouseY >= y && mouseY < y + PotentialCraftingMenu.BUTTON_HEIGHT;
    }

    /** 客户端也判得了：客户端有配方表，容器内容也已同步。 */
    private boolean canStart() {
        if (menu.isCrafting() || menu.power() <= 0 || minecraft == null || minecraft.level == null) {
            return false;
        }
        return PotentialCraftingRecipes.findMatch(minecraft.level, menu.container()) != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideButton(mouseX, mouseY) && canStart()
                && minecraft != null && minecraft.gameMode != null) {
            // 只发按钮点击包：材料校验与开始都在服务端做
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    PotentialCraftingMenu.BUTTON_START);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── 绘制 ──

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

        // 按钮
        boolean enabled = canStart();
        boolean hovered = insideButton(mouseX, mouseY);
        int bx = left + PotentialCraftingMenu.BUTTON_X;
        int by = top + PotentialCraftingMenu.BUTTON_Y;
        int bw = PotentialCraftingMenu.BUTTON_WIDTH;
        int bh = PotentialCraftingMenu.BUTTON_HEIGHT;
        graphics.fill(bx, by, bx + bw, by + bh, enabled ? COLOR_BUTTON_EDGE : COLOR_BUTTON_OFF_EDGE);
        graphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1,
                enabled ? (hovered ? brighten(COLOR_BUTTON) : COLOR_BUTTON) : COLOR_BUTTON_OFF);

        int invLineY = top + 176;
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

        // 按钮文字
        Component buttonText = Component.translatable(menu.isCrafting()
                ? "gui.rotten_ruins.potential_crafting.crafting"
                : "gui.rotten_ruins.potential_crafting.start");
        boolean enabled = canStart();
        graphics.drawString(font, buttonText,
                PotentialCraftingMenu.BUTTON_X
                        + (PotentialCraftingMenu.BUTTON_WIDTH - font.width(buttonText)) / 2,
                PotentialCraftingMenu.BUTTON_Y + 5,
                enabled ? 0xE8F8FF : 0x6C7A88, false);

        // 状态行
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
        graphics.drawString(font, status, cx - font.width(status) / 2, 168, color, false);
    }

    private int remainingSeconds() {
        float left = menu.totalTicks() * (1.0f - Mth.clamp(smoothedProgress, 0.0f, 1.0f));
        return Math.max(1, Mth.ceil(left / 20.0f));
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 24);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 24);
        int b = Math.min(255, (argb & 0xFF) + 24);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
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
