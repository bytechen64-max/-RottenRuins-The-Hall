package org.bytechen.hall.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.network.c2s.C2SDifficultySelectPacket;
import org.bytechen.hall.overworld.difficulty.HallDifficulty;
import org.bytechen.hall.utils.TranslateUtils;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 难度选择界面。
 * <p>
 * 首个玩家首次进入世界时自动弹出。
 * 四张 16×16 图标横向排列，鼠标悬浮时图标周围产生基于纹理采样的
 * shader 边缘扩散发光，同时显示翻译 tooltip，点击即选中并关闭。
 */
@OnlyIn(Dist.CLIENT)
public final class DifficultySelectScreen extends Screen {

    // ==================== 布局常量 ====================

    private static final int ENTRY_COUNT = 4;
    private static final int ICON_SIZE = 48;
    private static final int GLOW_RADIUS = 10;
    private static final int CARD_PADDING = 4;
    private static final int TEXT_GAP = 10;
    private static final int TITLE_BOTTOM_GAP = 12;
    private static final int HINT_BOTTOM_GAP = 28;

    private static final int GLOW_QUAD_SIZE = ICON_SIZE + GLOW_RADIUS * 2;  // 68
    /** 图标在发光 quad 内的 UV 边界 */
    private static final float UV_ICON_MIN = (float) GLOW_RADIUS / GLOW_QUAD_SIZE;   // 0.147
    private static final float UV_ICON_MAX = (float) (GLOW_RADIUS + ICON_SIZE) / GLOW_QUAD_SIZE; // 0.853

    /** 四个难度选项，含各自的 glow 颜色 */
    private static final List<DifficultyEntry> ENTRIES = List.of(
            new DifficultyEntry(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/difficulty_easy.png"),
                    TranslateUtils.DIFFICULTY_EASY, TranslateUtils.DIFFICULTY_EASY_TOOLTIP,
                    HallDifficulty.EASY,
                    0.667f, 1.0f, 0.267f   // 嫩绿
            ),
            new DifficultyEntry(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/difficulty_normal.png"),
                    TranslateUtils.DIFFICULTY_NORMAL, TranslateUtils.DIFFICULTY_NORMAL_TOOLTIP,
                    HallDifficulty.NORMAL,
                    1.0f, 0.843f, 0.0f     // 金色
            ),
            new DifficultyEntry(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/difficulty_hard.png"),
                    TranslateUtils.DIFFICULTY_HARD, TranslateUtils.DIFFICULTY_HARD_TOOLTIP,
                    HallDifficulty.HARD,
                    1.0f, 0.4f, 0.0f       // 橙红
            ),
            new DifficultyEntry(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/difficulty_incomprehensible.png"),
                    TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE, TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE_TOOLTIP,
                    HallDifficulty.INCOMPREHENSIBLE,
                    1.0f, 0.125f, 0.376f   // 深红
            )
    );

    private int hoveredIndex = -1;

    // ==================== 构造 ====================

    public DifficultySelectScreen() {
        super(Component.translatable(TranslateUtils.GUI_DIFFICULTY_SELECT_TITLE));
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int cardVisualH = ICON_SIZE + TEXT_GAP + this.font.lineHeight;
        int totalWidth = calculateTotalWidth();
        int startX = (this.width - totalWidth) / 2;
        int cardsY = centerY - cardVisualH / 2;

        // 标题
        int titleY = cardsY - TITLE_BOTTOM_GAP - this.font.lineHeight;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, titleY, 0xFFFFFF);

        // 提示文字
        int hintY = titleY - HINT_BOTTOM_GAP - this.font.lineHeight;
        guiGraphics.drawCenteredString(this.font,
                Component.translatable(TranslateUtils.GUI_DIFFICULTY_SELECT_HINT),
                centerX, hintY, 0xAAAAAA);

        // 绘制四张难度卡片
        hoveredIndex = -1;
        int cursorX = startX;
        float time = (System.currentTimeMillis() % 100_000L) / 1000f;

        for (int i = 0; i < ENTRY_COUNT; i++) {
            DifficultyEntry entry = ENTRIES.get(i);
            int cardW = ICON_SIZE + CARD_PADDING * 2;
            int cardX = cursorX;
            int cardY = cardsY;
            int cardBottom = cardY + cardVisualH;

            int spacing = calculateSpacing();
            cursorX += cardW + spacing;

            boolean hovered = mouseX >= cardX && mouseX <= cardX + cardW
                    && mouseY >= cardY && mouseY <= cardBottom;
            if (hovered) {
                hoveredIndex = i;
            }

            // 卡片高亮背景
            if (hovered) {
                guiGraphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardBottom + 1, 0x44FFFFFF);
                guiGraphics.fill(cardX, cardY, cardX + cardW, cardBottom, 0x22FFFFFF);
            }

            int iconX = cardX + (cardW - ICON_SIZE) / 2;
            int iconY = cardY + CARD_PADDING;

            // ── shader 级纹理边缘扩散发光 ──
            if (hovered && SplendidingShaders.guiGlowShader != null) {
                renderGlow(iconX, iconY, entry, time);
            }

            // ── 绘制图标 ──
            PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(iconX, iconY, 0);
            pose.scale(ICON_SIZE / 16f, ICON_SIZE / 16f, 1f);
            guiGraphics.blit(entry.texture, 0, 0, 0, 0, 16, 16, 16, 16);
            pose.popPose();

            // ── 标签文字 ──
            int textY = iconY + ICON_SIZE + TEXT_GAP;
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable(entry.translationKey),
                    cardX + cardW / 2, textY, hovered ? 0xFFFF55 : 0xCCCCCC);
        }

        // ── 悬浮 tooltip ──
        if (hoveredIndex >= 0 && hoveredIndex < ENTRY_COUNT) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable(ENTRIES.get(hoveredIndex).tooltipKey),
                    mouseX, mouseY);
        }
    }

    // ==================== 着色器发光 ====================

    /**
     * 绘制径向发光四边形。shader 用 smoothstep 做圆形径向衰减，
     * 在 quad 边缘前完全淡出（不会出现方形裁切），
     * 并采样图标纹理 alpha 来抑制透明区域的发光。
     */
    private static void renderGlow(int iconX, int iconY,
                                   DifficultyEntry entry, float time) {
        float gx = iconX - GLOW_RADIUS;
        float gy = iconY - GLOW_RADIUS;
        float gs = GLOW_QUAD_SIZE;

        Matrix4f mat = new Matrix4f();

        RenderSystem.setShaderTexture(0, entry.texture);
        RenderSystem.setShader(() -> SplendidingShaders.guiGlowShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);

        var s = SplendidingShaders.guiGlowShader;
        setUniformSafe(s, "IconUVMin", UV_ICON_MIN, UV_ICON_MIN);
        setUniformSafe(s, "IconUVMax", UV_ICON_MAX, UV_ICON_MAX);
        setUniformSafe(s, "GlowColor", entry.glowR, entry.glowG, entry.glowB, 1.0f);
        setUniformSafe(s, "uTime", time);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(mat, gx,      gy,      0).uv(0, 0).endVertex();
        buf.vertex(mat, gx,      gy + gs, 0).uv(0, 1).endVertex();
        buf.vertex(mat, gx + gs, gy + gs, 0).uv(1, 1).endVertex();
        buf.vertex(mat, gx + gs, gy,      0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(buf.end());

        // 还原
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.defaultBlendFunc();
    }

    private static void setUniformSafe(ShaderInstance shader, String name, float v) {
        if (shader.getUniform(name) != null) shader.safeGetUniform(name).set(v);
    }

    private static void setUniformSafe(ShaderInstance shader, String name, float a, float b) {
        if (shader.getUniform(name) != null) shader.safeGetUniform(name).set(a, b);
    }

    private static void setUniformSafe(ShaderInstance shader, String name,
                                       float a, float b, float c, float d) {
        if (shader.getUniform(name) != null) shader.safeGetUniform(name).set(a, b, c, d);
    }

    // ==================== 鼠标交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < ENTRY_COUNT) {
            DifficultyEntry entry = ENTRIES.get(hoveredIndex);
            NetworkHelper.sendToServer(new C2SDifficultySelectPacket(entry.difficultyId));
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ==================== 界面属性 ====================

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    // ==================== 布局计算 ====================

    private int calculateSpacing() {
        int cardW = ICON_SIZE + CARD_PADDING * 2;
        int available = this.width - cardW * ENTRY_COUNT;
        if (ENTRY_COUNT <= 1) return available / 2;
        return Math.max(16, available / (ENTRY_COUNT + 1));
    }

    private int calculateTotalWidth() {
        int cardW = ICON_SIZE + CARD_PADDING * 2;
        return cardW * ENTRY_COUNT + calculateSpacing() * (ENTRY_COUNT - 1);
    }

    // ==================== 数据类 ====================

    private record DifficultyEntry(
            ResourceLocation texture,
            String translationKey,
            String tooltipKey,
            ResourceLocation difficultyId,
            float glowR, float glowG, float glowB
    ) {}
}
