package org.bytechen.rottenruins.client.tooltip;

import java.util.List;

import org.bytechen.rottenruins.content.magic.base.CMagicBaseItem;
import org.bytechen.rottenruins.mixin.GuiGraphicsTooltipInvoker;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;

/**
 * 法术物品 tooltip 的<b>开合动画</b>。
 *
 * <h3>打开</h3>
 * <p>鼠标移到法术物品上：先由 {@code RenderTooltipEvent.Pre} 取消原版绘制，再自己套两层变换重画一次 ——
 * 纵向从中间一条线展开（居中缩放），横向从左往右露出（裁剪框宽度增长），两者都用
 * {@code easeOut} 曲线，约 {@value #OPEN_MS} 毫秒。</p>
 *
 * <h3>关闭</h3>
 * <p>鼠标离开物品的那一帧原版什么都不画，所以用最近一次的快照在
 * {@code ScreenEvent.Render.Post} 里重画一遍，纵向压到一条线（像电视机关闭）。
 * 快照只保留组件引用，不复制内容，改文案不需要动这里。</p>
 *
 * <h3>为什么重画而不是改渲染</h3>
 * <p>1.20.1 的 {@code RenderTooltipEvent} 只有 Pre / Color / GatherComponents，没有"画完之后"的钩子，
 * 也没法在绘制中途插裁剪。所以走 {@link GuiGraphicsTooltipInvoker} 直接把私有方法再调一次，
 * 自己控制姿态与裁剪，原版内容（名字、图标、颜色）原样保留。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpellTooltipAnimator {

    /** 打开时长（毫秒）。 */
    private static final long OPEN_MS = 140L;

    /** 关闭时长（毫秒）。 */
    private static final long CLOSE_MS = 130L;

    /** 底板比内容大 3 格（{@code TooltipRenderUtil.PADDING}），这里多留 1 格当裁剪余量。 */
    private static final int PAD = 4;

    /** 纵向压到这个比例就当作一条线，避免 0 导致矩阵退化。 */
    private static final float MIN_SCALE = 0.03F;

    /** 重画时置位：内层再次触发 Pre 就放行，让原版正常画。 */
    private static boolean selfRender;

    private static boolean drawnThisFrame;
    private static boolean drawnLastFrame;
    private static long openStart;

    /** 上一帧画的是哪个物品：换物品也要重新播打开动画（鼠标从 A 挪到 B）。 */
    private static Item lastItem;

    private static boolean closing;
    private static long closeStart;
    private static Screen closeScreen;
    private static Font closeFont;
    private static List<ClientTooltipComponent> closeComponents = List.of();
    private static int closeMouseX;
    private static int closeMouseY;
    private static int closeOriginX;
    private static int closeOriginY;
    private static int closeX;
    private static int closeY;
    private static int closeW;
    private static int closeH;

    /**
     * 关闭动画用的定位器：鼠标已经移开，不能让原版按当前鼠标重新算位置，
     * 所以固定在快照的原点。
     */
    private static final ClientTooltipPositioner FIXED = (screenW, screenH, mouseX, mouseY, width, height) ->
            new Vector2i(closeOriginX, closeOriginY);

    private SpellTooltipAnimator() {
    }

    /** 每帧开头清标记（界面里）：tooltip 是否在本帧被画过。 */
    public static void onScreenPre(ScreenEvent.Render.Pre event) {
        drawnThisFrame = false;
    }

    /** 每帧开头清标记（游戏内 HUD，没有 Screen）：否则从界面切回游戏后打开动画再也不会播。 */
    public static void onGuiPre(RenderGuiEvent.Pre event) {
        drawnThisFrame = false;
    }

    /** 打开动画：取消原版绘制，套上裁剪与缩放后重画一次。 */
    public static void onTooltipPre(RenderTooltipEvent.Pre event) {
        if (selfRender) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof CMagicBaseItem)) {
            return;
        }
        GuiGraphics graphics = event.getGraphics();
        Font font = event.getFont();
        List<ClientTooltipComponent> components = event.getComponents();
        if (components.isEmpty()) {
            return;
        }
        int width = widthOf(components, font);
        int height = heightOf(components);
        long now = Util.getMillis();
        // 只在"鼠标刚移到这个物品上"时重播展开；按 Shift 改内容宽度/高度不重播
        if (!drawnLastFrame || stack.getItem() != lastItem) {
            openStart = now;
        }
        lastItem = stack.getItem();

        Vector2ic origin = event.getTooltipPositioner().positionTooltip(
                graphics.guiWidth(), graphics.guiHeight(), event.getX(), event.getY(), width, height);
        int bx = origin.x() - PAD;
        int by = origin.y() - PAD;
        int bw = width + PAD * 2;
        int bh = height + PAD * 2;

        rememberSnapshot(event, font, components, origin);
        // ModernUI 会抢走 Pre 自己画 tooltip，先把它的开关按下，帧末再恢复（没装则 no-op）
        ModernUiTooltipCompat.silence();

        float progress = clamp01((now - openStart) / (float) OPEN_MS);
        float ease = easeOutCubic(progress);
        int cx = bx + bw / 2;
        int cy = by + bh / 2;
        int reveal = bx + Math.max(1, Math.round(bw * ease));

        graphics.flush();
        graphics.enableScissor(bx, by, reveal, by + bh);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0D);
        pose.scale(1.0F, MIN_SCALE + (1.0F - MIN_SCALE) * ease, 1.0F);
        pose.translate(-cx, -cy, 0.0D);
        selfRender = true;
        try {
            ((GuiGraphicsTooltipInvoker) graphics).rottenruins$renderTooltipInternal(
                    font, components, event.getX(), event.getY(), event.getTooltipPositioner());
        } finally {
            selfRender = false;
            graphics.flush();
            pose.popPose();
            graphics.disableScissor();
        }
        event.setCanceled(true);
        drawnThisFrame = true;
    }

    /** 关闭动画（界面里）：没有 tooltip 可画的帧里，用快照把上一次的再画一遍并压扁。 */
    public static void onScreenPost(ScreenEvent.Render.Post event) {
        finishFrame(event.getGuiGraphics(), event.getScreen());
    }

    /** 关闭动画（游戏内 HUD）：有界面时由 {@link #onScreenPost} 负责，避免同帧画两遍。 */
    public static void onGuiPost(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            finishFrame(event.getGuiGraphics(), null);
        }
    }

    private static void finishFrame(GuiGraphics graphics, Screen screen) {
        ModernUiTooltipCompat.restore();
        if (drawnLastFrame && !drawnThisFrame && closeScreen == screen && closeFont != null
                && !closeComponents.isEmpty()) {
            closing = true;
            closeStart = Util.getMillis();
        }
        drawnLastFrame = drawnThisFrame;

        if (!closing) {
            return;
        }
        if (closeScreen != screen || closeFont == null || closeComponents.isEmpty()) {
            closing = false;
            return;
        }
        float progress = clamp01((Util.getMillis() - closeStart) / (float) CLOSE_MS);
        if (progress >= 1.0F) {
            closing = false;
            return;
        }
        float squeeze = Math.max(MIN_SCALE, 1.0F - easeInCubic(progress));
        int cx = closeX + closeW / 2;
        int cy = closeY + closeH / 2;
        int half = Math.max(1, Math.round(closeH * squeeze / 2.0F));

        graphics.flush();
        graphics.enableScissor(closeX, cy - half, closeX + closeW, cy + half);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0D);
        pose.scale(1.0F, squeeze, 1.0F);
        pose.translate(-cx, -cy, 0.0D);
        selfRender = true;
        try {
            ((GuiGraphicsTooltipInvoker) graphics).rottenruins$renderTooltipInternal(
                    closeFont, closeComponents, closeMouseX, closeMouseY, FIXED);
        } finally {
            selfRender = false;
            graphics.flush();
            pose.popPose();
            graphics.disableScissor();
        }
    }

    private static void rememberSnapshot(RenderTooltipEvent.Pre event, Font font,
                                         List<ClientTooltipComponent> components, Vector2ic origin) {
        closeFont = font;
        closeComponents = components;
        closeScreen = Minecraft.getInstance().screen;
        closeMouseX = event.getX();
        closeMouseY = event.getY();
        closeOriginX = origin.x();
        closeOriginY = origin.y();
        closeX = origin.x() - PAD;
        closeY = origin.y() - PAD;
        closeW = widthOf(components, font) + PAD * 2;
        closeH = heightOf(components) + PAD * 2;
        closing = false;
    }

    /** 与 {@code GuiGraphics#renderTooltipInternal} 一致：宽度取最大值。 */
    private static int widthOf(List<ClientTooltipComponent> components, Font font) {
        int width = 0;
        for (ClientTooltipComponent component : components) {
            width = Math.max(width, component.getWidth(font));
        }
        return width;
    }

    /** 与 {@code GuiGraphics#renderTooltipInternal} 一致：高度求和，只有一行时少 2 格。 */
    private static int heightOf(List<ClientTooltipComponent> components) {
        int height = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : components) {
            height += component.getHeight();
        }
        return height;
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    private static float easeOutCubic(float t) {
        float rest = 1.0F - t;
        return 1.0F - rest * rest * rest;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }
}
