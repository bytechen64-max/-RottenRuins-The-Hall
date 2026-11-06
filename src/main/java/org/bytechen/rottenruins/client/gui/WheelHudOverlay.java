package org.bytechen.rottenruins.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.gui.wheel.WheelClientState;
import org.bytechen.rottenruins.client.gui.wheel.WheelData;
import org.bytechen.rottenruins.client.gui.wheel.WheelRenderer;
import org.bytechen.rottenruins.client.gui.wheel.WheelI18n;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.items.magic.WandItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.IMagicBaseItem;
import org.bytechen.rottenruins.client.rend.gui.WheelGuiShaders;

/**
 * 持杖时显示玩家法力、法杖储能和当前技能；聊天界面呼出鼠标后可直接调整布局。
 *
 * <p>{@code KEEP}（按住持续）类法术（例如抑制场）在起手时轮盘就已经关闭，
 * 此时"正在维持"这件事只能由 {@link WheelClientState#phase()} 表达，所以这个
 * 相位是 {@link WheelClientState.Phase#MAINTAINING} 时，用共享的
 * {@link WheelRenderer#renderMaintainBar} 把底部维持条画出来。</p>
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

    private enum Drag { NONE, MOVE, RESIZE }
    private static Drag drag = Drag.NONE;
    private static double dragStartX, dragStartY;
    private static int startX, startY;
    private static float startWidthScale, startHeightScale;
    private static long lastFrameMs;
    private static WandHudMotion motion = new WandHudMotion();
    private static WandHudMotion.Spring hudSize = new WandHudMotion.Spring();
    private static Object lastWorld;
    private static float shownPointerAngle;
    private static String readoutDetail = "";
    private static int readoutColor = 0xFF9CCBDF;

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
        GuiGraphics gui = event.getGuiGraphics();
        WandHudLayout layout = WandHudLayout.get();
        if (!layout.hidden() && mc.screen == null
                && mc.player.getMainHandItem().getItem() instanceof WandItem wand) {
            renderWandStatus(gui, wand, false, false, -1, -1);
        }
        if (WheelClientState.phase() != WheelClientState.Phase.MAINTAINING) {
            return;
        }
        WheelRenderer.renderMaintainBar(gui,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(),
                WheelData.manaFrac(),
                WheelClientState.maintainingSeconds());
    }

    @SubscribeEvent
    public static void onChatRender(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getScreen() instanceof ChatScreen) || mc.options.hideGui
                || mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof WandItem wand)) return;
        WandHudLayout layout = WandHudLayout.get();
        int sw = event.getGuiGraphics().guiWidth(), sh = event.getGuiGraphics().guiHeight();
        if (layout.hidden()) {
            drawRestoreButton(event.getGuiGraphics(), layout, sw, sh,
                    event.getMouseX(), event.getMouseY());
            return;
        }
        boolean hovered = contains(layout, event.getMouseX(), event.getMouseY(), sw, sh);
        renderWandStatus(event.getGuiGraphics(), wand, true, hovered || drag != Drag.NONE,
                event.getMouseX(), event.getMouseY());
        Rect hide = hideButtonRect(layout, sw, sh);
        drawHideButton(event.getGuiGraphics(), hide,
                hide.contains(event.getMouseX(), event.getMouseY()));
        if (hide.contains(event.getMouseX(), event.getMouseY())) {
            event.getGuiGraphics().renderTooltip(mc.font,
                    Component.literal(WheelI18n.tr("rotten_ruins.wand_hud.hide")),
                    event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onChatMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!canEdit(event.getScreen())) return;
        WandHudLayout layout = WandHudLayout.get();
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        if (layout.hidden()) {
            if (event.getButton() == 0 && restoreButtonRect(layout, sw, sh)
                    .contains(event.getMouseX(), event.getMouseY())) {
                layout.setHidden(false);
                layout.save();
                event.setCanceled(true);
            }
            return;
        }
        if (hideButtonRect(layout, sw, sh).contains(event.getMouseX(), event.getMouseY())) {
            if (event.getButton() == 0) {
                drag = Drag.NONE;
                layout.setHidden(true);
                layout.save();
            }
            event.setCanceled(true);
            return;
        }
        if (!contains(layout, event.getMouseX(), event.getMouseY(), sw, sh)) return;
        if (event.getButton() == 1) {
            layout.reset();
            layout.save();
            event.setCanceled(true);
        } else if (event.getButton() == 0) {
            int x = layout.x(sw, sh), y = layout.y(sw, sh);
            int w = layout.width(sw, sh), h = layout.height(sw, sh);
            drag = event.getMouseX() >= x + w - 16 && event.getMouseY() >= y + h - 16
                    ? Drag.RESIZE : Drag.MOVE;
            dragStartX = event.getMouseX();
            dragStartY = event.getMouseY();
            startX = x;
            startY = y;
            startWidthScale = layout.requestedWidthScale();
            startHeightScale = layout.requestedHeightScale();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChatMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (drag == Drag.NONE || event.getMouseButton() != 0 || !canEdit(event.getScreen())
                || WandHudLayout.get().hidden()) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        WandHudLayout layout = WandHudLayout.get();
        if (drag == Drag.MOVE) {
            layout.moveTo(startX + (int) Math.round(event.getMouseX() - dragStartX),
                    startY + (int) Math.round(event.getMouseY() - dragStartY), sw, sh);
        } else {
            float dx = (float) ((event.getMouseX() - dragStartX) / WandHudLayout.WIDTH);
            float dy = (float) ((event.getMouseY() - dragStartY) / WandHudLayout.HEIGHT);
            float delta = Math.abs(dx) > Math.abs(dy) ? dx : dy;
            layout.resize(startWidthScale + delta, startHeightScale + delta);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChatMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (drag == Drag.NONE || event.getButton() != 0 || !canEdit(event.getScreen())) return;
        drag = Drag.NONE;
        WandHudLayout.get().save();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChatMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!canEdit(event.getScreen())) return;
        WandHudLayout layout = WandHudLayout.get();
        if (layout.hidden()) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        if (!contains(layout, event.getMouseX(), event.getMouseY(), sw, sh)) return;
        float step = (float) Math.signum(event.getScrollDelta()) * 0.05F;
        layout.resize(layout.requestedWidthScale() + step, layout.requestedHeightScale() + step);
        layout.save();
        motion.resize = 1;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChatClosed(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof ChatScreen && drag != Drag.NONE) {
            drag = Drag.NONE;
            WandHudLayout.get().save();
        }
    }

    private static boolean canEdit(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        return screen instanceof ChatScreen && mc.player != null
                && mc.player.getMainHandItem().getItem() instanceof WandItem;
    }

    private static boolean contains(WandHudLayout layout, double mx, double my, int sw, int sh) {
        int x = layout.x(sw, sh), y = layout.y(sw, sh);
        return mx >= x && mx < x + layout.width(sw, sh)
                && my >= y && my < y + layout.height(sw, sh);
    }

    private record Rect(int x, int y, int width, int height) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    private static Rect hideButtonRect(WandHudLayout layout, int sw, int sh) {
        int size = Math.max(13, Math.min(18, Math.round(18 * Math.min(
                layout.width(sw, sh) / (float) WandHudLayout.WIDTH,
                layout.height(sw, sh) / (float) WandHudLayout.HEIGHT))));
        return new Rect(layout.x(sw, sh) + layout.width(sw, sh) - size - 5,
                layout.y(sw, sh) + 3, size, size);
    }

    private static Rect restoreButtonRect(WandHudLayout layout, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        String label = fit(mc, WheelI18n.tr("rotten_ruins.wand_hud.show"), sw - 28);
        int width = mc.font.width(label) + 18;
        int right = layout.x(sw, sh) + layout.width(sw, sh);
        return new Rect(Math.max(4, Math.min(sw - width - 4, right - width)),
                layout.y(sw, sh), width, 18);
    }

    private static void drawHideButton(GuiGraphics gui, Rect rect, boolean hovered) {
        int top = hovered ? 0xE62B3650 : 0xC5212A3B;
        int border = hovered ? WheelRenderer.GOLD_BRIGHT : 0x99C9A227;
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(gui, rect.x, rect.y, rect.width, rect.height,
                    3, top, 0xC00C1220, border, 0.8F);
        } else {
            gui.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, top);
            gui.renderOutline(rect.x, rect.y, rect.width, rect.height, border);
        }
        gui.fill(rect.x + 4, rect.y + rect.height / 2,
                rect.x + rect.width - 4, rect.y + rect.height / 2 + 1, WheelRenderer.GOLD_BRIGHT);
    }

    private static void drawRestoreButton(GuiGraphics gui, WandHudLayout layout,
                                          int sw, int sh, double mouseX, double mouseY) {
        Rect rect = restoreButtonRect(layout, sw, sh);
        boolean hovered = rect.contains(mouseX, mouseY);
        int border = hovered ? WheelRenderer.GOLD_BRIGHT : 0x99C9A227;
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(gui, rect.x, rect.y, rect.width, rect.height,
                    4, 0xDD172238, 0xCB070C16, border, 0.9F);
        } else {
            gui.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, 0xDD172238);
            gui.renderOutline(rect.x, rect.y, rect.width, rect.height, border);
        }
        gui.fill(rect.x + 5, rect.y + 8, rect.x + 12, rect.y + 9, WheelRenderer.GOLD_BRIGHT);
        gui.fill(rect.x + 8, rect.y + 5, rect.x + 9, rect.y + 12, WheelRenderer.GOLD_BRIGHT);
        gui.drawString(Minecraft.getInstance().font,
                fit(Minecraft.getInstance(), WheelI18n.tr("rotten_ruins.wand_hud.show"), sw - 28),
                rect.x + 14, rect.y + 5, WheelRenderer.TEXT, true);
    }

    private static void renderWandStatus(GuiGraphics gui, WandItem wand,
                                         boolean chatControls, boolean editing,
                                         double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        WandHudLayout layout = WandHudLayout.get();
        int sw = gui.guiWidth(), sh = gui.guiHeight();
        int x = layout.x(sw, sh), y = layout.y(sw, sh);
        int size = layout.width(sw, sh);
        long now = Util.getMillis();
        boolean fresh = lastFrameMs == 0 || now - lastFrameMs > 750 || lastWorld != mc.level;
        float dt = fresh ? 1 / 60.0F : Math.min(0.1F, (now - lastFrameMs) / 1000.0F);
        if (fresh) {
            motion = new WandHudMotion();
            hudSize = new WandHudMotion.Spring();
            hudSize.value = size;
            readoutDetail = "";
        }
        lastFrameMs = now;
        lastWorld = mc.level;
        if (drag == Drag.RESIZE) hudSize.value = size;
        else hudSize.step(size, dt);
        float scale = hudSize.value / WandHudLayout.WIDTH;
        float cx = x + size / 2.0F, cy = y + size / 2.0F;
        WandHudGeometry.Target target = chatControls && drag == Drag.NONE
                && !hideButtonRect(layout, sw, sh).contains(mouseX, mouseY)
                ? WandHudGeometry.target((mouseX - cx) / scale, (mouseY - cy) / scale,
                        motion.playerHover.value, motion.wandHover.value)
                : WandHudGeometry.Target.NONE;
        ItemStack held = mc.player.getMainHandItem();
        int playerMana = WheelData.mana(), maxMana = WheelData.maxMana();
        int wandMana = wand.mana(held), capacity = wand.capacity();
        PlayerMagicPool pool = MagicHandle.getPool(mc.player);
        ItemStack selected = MagicHandle.selectedSpell(mc.player);
        if (pool != null && WheelClientState.phase() == WheelClientState.Phase.MAINTAINING
                && pool.isValidSlot(WheelClientState.poolSlot())) {
            selected = pool.getStackInSlot(WheelClientState.poolSlot());
        }
        IMagicBaseItem spell = MagicStats.spellOf(selected);
        HudPalette palette = paletteFor(wand, spell);
        float playerFraction = maxMana > 0 ? Math.min(1.0F, Math.max(0, playerMana / (float) maxMana)) : 0;
        float reserveFraction = capacity > 0 ? Math.min(1.0F, Math.max(0, wandMana / (float) capacity)) : 0;
        float cooldown = spell == null ? 0 : cooldownFraction(mc, pool, selected);
        motion.update(dt, target, playerFraction, reserveFraction, cooldown,
                String.valueOf(MagicStats.magicId(selected)), String.valueOf(held.getItem()));
        if (target != WandHudGeometry.Target.NONE) {
            float angle = (float) Math.atan2(mouseY - cy, mouseX - cx);
            float delta = (float) Math.atan2(Math.sin(angle - shownPointerAngle), Math.cos(angle - shownPointerAngle));
            shownPointerAngle += delta * (1 - (float) Math.exp(-dt * 22));
        }
        float pointerAngle = shownPointerAngle;
        WandHudVisuals.draw(new WandHudVisuals.Canvas() {
            public void arc(float inner, float outer, float start, float span, int color) {
                drawArcSegment(gui, cx, cy, inner * scale, outer * scale, start, span, color);
            }
            public void disc(float dx, float dy, float radius, int color) {
                drawArc(gui, cx + dx * scale, cy + dy * scale, 0, radius * scale, 1, color);
            }
            public void line(float x1, float y1, float x2, float y2, float width, int color) {
                drawLine(gui, cx + x1 * scale, cy + y1 * scale,
                        cx + x2 * scale, cy + y2 * scale, width * scale, color);
            }
        }, motion, palette.skill(), palette.player(), palette.reserve(), (now % 120000L) / 1000.0F,
                pointerAngle, drag != Drag.NONE);

        // Render the item first, then cover only its remaining cooldown width.
        // The right edge of this mask travels left as the cooldown expires.
        gui.pose().pushPose();
        float iconSize = 22 * (1 + Math.max(0, motion.iconHover.value) * 0.08F
                + (float) Math.sin(motion.ready * Math.PI) * 0.10F - motion.switched * 0.12F);
        float parallax = Math.max(0, motion.iconHover.value);
        float iconX = cx + (float) Math.max(-1.3, Math.min(1.3, (mouseX - cx) / scale * 0.07)) * scale * parallax;
        float iconY = cy + (float) Math.max(-1.3, Math.min(1.3, (mouseY - cy) / scale * 0.07)) * scale * parallax;
        gui.pose().translate(iconX - iconSize * scale / 2, iconY - iconSize * scale / 2, 0);
        gui.pose().scale(iconSize / 16 * scale, iconSize / 16 * scale, 1);
        if (spell != null) {
            gui.renderItem(selected, 0, 0);
            float fraction = cooldown;
            float covered = WandHudGeometry.cooldownWidth(16, fraction);
            if (covered > 0) {
                gui.pose().pushPose();
                gui.pose().translate(0, 0, 200);
                gui.pose().scale(covered / 16, 1, 1);
                gui.fill(0, 0, 16, 16, 0x99050A16);
                gui.pose().popPose();
            }
        } else {
            gui.drawString(mc.font, "?", 5, 4, WheelRenderer.TEXT_FAINT, false);
        }
        gui.pose().popPose();
        if (editing) {
            gui.fill(x + size - 11, y + size - 4, x + size - 3, y + size - 3, WheelRenderer.GOLD_BRIGHT);
            gui.fill(x + size - 4, y + size - 11, x + size - 3, y + size - 3, WheelRenderer.GOLD_BRIGHT);
        }
        if (target != WandHudGeometry.Target.NONE) {
            readoutColor = target == WandHudGeometry.Target.PLAYER ? palette.player()
                    : target == WandHudGeometry.Target.WAND ? palette.reserve() : palette.skill();
            readoutDetail = switch (target) {
                case ICON -> spell != null ? selected.getHoverName().getString()
                        : WheelI18n.tr("rotten_ruins.wand_hud.no_skill");
                case PLAYER -> WheelI18n.tr("rotten_ruins.wand_hud.player_mana", playerMana, maxMana);
                case WAND -> capacity > 0 ? WheelI18n.tr("rotten_ruins.wand_hud.element_mana",
                        capacity == WandItem.COMMON_CAPACITY ? WheelI18n.tr("rotten_ruins.wand_hud.common")
                                : WheelData.schoolLabel(wand.getMagicType()), wandMana, capacity)
                        : WheelI18n.tr("rotten_ruins.wand_hud.no_reserve");
                default -> "";
            };
        }
        drawReadout(gui, cx, cy, scale, motion.focus.value, readoutDetail, readoutColor);
    }

    private static void drawReadout(GuiGraphics gui, float cx, float cy, float scale,
                                    float focus, String detail, int color) {
        float opacity = Math.max(0, Math.min(1, focus));
        if (opacity < 0.02F || detail.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = gui.guiWidth(), sh = gui.guiHeight();
        String hint = WheelI18n.tr("rotten_ruins.wand_hud.chat_hint");
        int width = Math.min(sw - 12, Math.max(mc.font.width(detail), (int) (mc.font.width(hint) * 0.8F)) + 24);
        float gap = 44 * scale + 9 + (1 - opacity) * 7;
        int px = Math.round(cx + gap);
        boolean left = px + width > sw - 6;
        if (left) px = Math.round(cx - gap - width);
        px = Math.max(6, Math.min(sw - width - 6, px));
        int py = Math.max(6, Math.min(sh - 42, Math.round(cy - 19)));
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 300);
        int background = WandHudVisuals.alpha(0x091321, 233 * opacity);
        int border = WandHudVisuals.alpha(color, 80 * opacity);
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(gui, px, py, width, 36, 5,
                    background, background, border, 0.6F);
        } else {
            gui.fill(px, py, px + width, py + 36, background);
            gui.renderOutline(px, py, width, 36, border);
        }
        gui.fill(px + 8, py + 9, px + 10, py + 17, WandHudVisuals.alpha(color, 220 * opacity));
        gui.drawString(mc.font, fit(mc, detail, width - 26), px + 15, py + 8,
                WandHudVisuals.alpha(0xE9F3FA, 255 * opacity), false);
        gui.pose().pushPose();
        gui.pose().translate(px + 9, py + 23, 0);
        gui.pose().scale(0.8F, 0.8F, 1);
        gui.drawString(mc.font, fit(mc, hint, (int) ((width - 18) / 0.8F)), 0, 0,
                WandHudVisuals.alpha(0xA1B4C5, 220 * opacity), false);
        gui.pose().popPose();
        gui.pose().popPose();
    }

    private static float cooldownFraction(Minecraft mc, PlayerMagicPool pool, ItemStack selected) {
        if (pool == null) return 0;
        int remaining = pool.getCooldownRemaining(MagicStats.magicId(selected));
        if (remaining <= 0) return 0;
        int duration = MagicStats.cooldownTicks(mc.player, selected);
        return Math.min(1, Math.max(0, (remaining - mc.getFrameTime()) / Math.max(1.0F, duration)));
    }

    private static void drawLine(GuiGraphics gui, float x1, float y1, float x2, float y2,
                                 float halfWidth, int color) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfLine(gui, x1, y1, x2, y2, halfWidth, color);
        } else {
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1)));
            for (int i = 0; i <= steps; i++) {
                int x = Math.round(x1 + (x2 - x1) * i / steps);
                int y = Math.round(y1 + (y2 - y1) * i / steps);
                gui.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    /** Clockwise from twelve o'clock, with a pixel fallback if shaders are unavailable. */
    private static void drawArc(GuiGraphics gui, float cx, float cy, float inner, float outer,
                                float fraction, int color) {
        drawArcSegment(gui, cx, cy, inner, outer, -(float) Math.PI / 2,
                fraction * (float) (Math.PI * 2), color);
    }

    private static void drawArcSegment(GuiGraphics gui, float cx, float cy, float inner, float outer,
                                       float start, float span, int color) {
        if (span <= 0 || outer <= 0 || (color >>> 24) == 0) return;
        if (WheelGuiShaders.sdfAvailable()) {
            if (span >= Math.PI * 2 - 0.0001) {
                WheelGuiShaders.sdfRing(gui, cx, cy, inner, outer, color, color, false);
            } else {
                WheelGuiShaders.sdfWedge(gui, cx, cy, inner, outer, start, start + span, color, color);
            }
            return;
        }
        for (int py = (int) Math.floor(cy - outer); py < Math.ceil(cy + outer); py++) {
            int run = Integer.MIN_VALUE;
            for (int px = (int) Math.floor(cx - outer); px <= Math.ceil(cx + outer); px++) {
                double dx = px + 0.5 - cx, dy = py + 0.5 - cy;
                double radius = Math.hypot(dx, dy);
                double angle = ((Math.atan2(dy, dx) - start) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
                boolean inside = radius >= inner && radius <= outer && angle <= span;
                if (inside && run == Integer.MIN_VALUE) run = px;
                if (!inside && run != Integer.MIN_VALUE) {
                    gui.fill(run, py, px, py + 1, color);
                    run = Integer.MIN_VALUE;
                }
            }
        }
    }

    private record HudPalette(int skill, int player, int reserve) { }

    private static HudPalette paletteFor(WandItem wand, IMagicBaseItem spell) {
        // WheelData is the wheel's display palette; MagicType.rgb() belongs to item tooltips
        // and differs noticeably for MYSTERIOUS. Keep each color tied to the value it describes.
        int reserve = wand.capacity() == 0 ? WheelRenderer.TEXT_FAINT
                : wand.capacity() == WandItem.COMMON_CAPACITY ? WheelRenderer.GOLD
                : WheelData.argb(wand.getMagicType(), 1.0F);
        int skill = spell != null ? WheelData.argb(spell.getMagicType(), 1.0F)
                : wand.capacity() > 0 ? reserve : WheelRenderer.GOLD_DIM;
        return new HudPalette(skill, 0xFF91C9DE, reserve);
    }

    private static String fit(Minecraft mc, String text, int maxWidth) {
        maxWidth = Math.max(mc.font.width("…") + 1, maxWidth);
        return mc.font.width(text) <= maxWidth ? text
                : mc.font.plainSubstrByWidth(text, maxWidth - mc.font.width("…")) + "…";
    }

}
