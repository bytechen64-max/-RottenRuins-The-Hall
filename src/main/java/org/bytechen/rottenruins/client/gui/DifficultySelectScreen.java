package org.bytechen.rottenruins.client.gui;

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
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.client.gui.DifficultySigilMesh.Ring;
import org.bytechen.rottenruins.network.NetworkHelper;
import org.bytechen.rottenruins.network.c2s.C2SDifficultySelectPacket;
import org.bytechen.rottenruins.overworld.difficulty.HallDifficulty;
import org.bytechen.rottenruins.utils.TranslateUtils;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 难度选择界面：首个玩家首次进世界时自动弹出，四张卡片各带一枚程序化生成的「印记」。
 * 几何在 CPU 侧按难度烘成环带，片元按 profile 出不同角度图案（见 {@link DifficultySigilMesh}）。
 * 选中后印记放大、其余收缩淡出，放完动画才发包关界面。
 */
@OnlyIn(Dist.CLIENT)
public final class DifficultySelectScreen extends Screen {

    private static final int ENTRY_COUNT = 4;
    private static final int ICON_SIZE = 48;
    private static final int GLOW_RADIUS = 10;
    private static final int CARD_PADDING = 4;
    private static final int TEXT_GAP = 10;
    private static final int TITLE_BOTTOM_GAP = 12;
    private static final int HINT_BOTTOM_GAP = 28;

    private static final int GLOW_QUAD_SIZE = ICON_SIZE + GLOW_RADIUS * 2;

    /** 图标在发光 quad 内的 UV 边界 */
    private static final float UV_ICON_MIN = (float) GLOW_RADIUS / GLOW_QUAD_SIZE;
    private static final float UV_ICON_MAX = (float) (GLOW_RADIUS + ICON_SIZE) / GLOW_QUAD_SIZE;

    /** 印记的基准半尺寸（像素）：所有环半径都以它为 1.0 的基准。 */
    private static final float SIGIL_UNIT = 30.0F;

    private static final float SELECT_ANIM_MS = 480.0F;
    private static final float SELECT_SCALE = 1.34F;
    private static final float UNSELECTED_SCALE = 0.76F;

    /**
     * 四个难度选项，{@code rings} 顺序即 {@code uProfile} 取值（0..3），与 shader 分支一一对应。
     * {@code Ring} 的最后两个参数是「图案倍数」与「图案相位偏移」，倍数必须与 shader 里
     * {@code patternMultiplicity()} 一致（1 / 3 / 5 / 7），偏移让同档三环的缺口错开。
     * {@code glow*} 取自图标 PNG 的不透明像素统计出的主体色，同时用于印记基色与背后柔光。
     */
    private static final List<DifficultyEntry> ENTRIES = List.of(
            new DifficultyEntry(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/difficulty_easy.png"),
                    TranslateUtils.DIFFICULTY_EASY, TranslateUtils.DIFFICULTY_EASY_TOOLTIP,
                    HallDifficulty.EASY,
                    0.153f, 0.224f, 0.149f,   // #273926 图标主色·深森绿
                    // 环带宽度都抬到 2.2px 以上：亚像素宽的细线在斜向光栅化时会被丢样本，表现为环上东一处西一处的断口。
                    List.of(
                            new Ring(1.00F, 3.0F, 0.95F, 0.30F, new DifficultySigilMesh.Circle(72), 0.070F, 3.0F, 0.60F, 1.0F, 0.0F),
                            new Ring(0.80F, 2.4F, 0.52F, -0.20F, new DifficultySigilMesh.Circle(64), 0.050F, 2.0F, 0.45F, 1.0F, 0.0F),
                            new Ring(0.61F, 2.2F, 0.32F, 0.14F, new DifficultySigilMesh.Circle(56), 0.035F, 4.0F, 0.90F, 1.0F, 0.0F)
                    )
            ),
            new DifficultyEntry(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/difficulty_normal.png"),
                    TranslateUtils.DIFFICULTY_NORMAL, TranslateUtils.DIFFICULTY_NORMAL_TOOLTIP,
                    HallDifficulty.NORMAL,
                    0.122f, 0.145f, 0.502f,   // #1F2580 图标主色·蓝紫
                    List.of(
                            new Ring(1.00F, 2.8F, 0.95F, 0.22F, new DifficultySigilMesh.Polygon(6, 0.95F), 0.012F, 6.0F, 0.35F, 3.0F, 0.0F),
                            new Ring(0.84F, 2.3F, 0.55F, -0.32F, new DifficultySigilMesh.Polygon(6, 0.55F), 0.010F, 6.0F, 0.30F, 3.0F, 2.09F),
                            new Ring(0.65F, 2.1F, 0.34F, 0.55F, new DifficultySigilMesh.Polygon(3, 0.90F), 0.016F, 3.0F, 0.50F, 3.0F, 4.19F)
                    )
            ),
            new DifficultyEntry(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/difficulty_hard.png"),
                    TranslateUtils.DIFFICULTY_HARD, TranslateUtils.DIFFICULTY_HARD_TOOLTIP,
                    HallDifficulty.HARD,
                    0.992f, 0.059f, 0.349f,   // #FD0F59 图标主色·玫红
                    List.of(
                            new Ring(1.00F, 2.6F, 0.98F, 0.34F, new DifficultySigilMesh.Spikes(12, 0.40F, 0.0F, 6), 0.055F, 6.0F, 1.90F, 5.0F, 0.0F),
                            new Ring(0.82F, 2.2F, 0.62F, -0.46F, new DifficultySigilMesh.Spikes(8, 0.30F, 2.7F, 6), 0.045F, 5.0F, 1.40F, 5.0F, 1.26F),
                            new Ring(0.60F, 2.0F, 0.38F, 0.80F, new DifficultySigilMesh.Polygon(5, 0.75F), 0.030F, 5.0F, 1.10F, 5.0F, 2.51F)
                    )
            ),
            new DifficultyEntry(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/difficulty_incomprehensible.png"),
                    TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE, TranslateUtils.DIFFICULTY_INCOMPREHENSIBLE_TOOLTIP,
                    HallDifficulty.INCOMPREHENSIBLE,
                    0.796f, 0.816f, 1.000f,   // #CBD0FF 苍白色（图标红/白两组色中的苍白系）
                    List.of(
                            new Ring(1.00F, 2.5F, 0.95F, 0.62F, new DifficultySigilMesh.Steps(9, 0.14F, 1.3F), 0.030F, 6.0F, 2.40F, 7.0F, 0.0F),
                            new Ring(0.80F, 2.2F, 0.70F, -0.78F, new DifficultySigilMesh.Steps(7, 0.18F, 4.1F), 0.035F, 5.0F, 3.10F, 7.0F, 0.90F),
                            new Ring(0.58F, 2.0F, 0.45F, 1.05F, new DifficultySigilMesh.Steps(5, 0.22F, 7.7F), 0.045F, 4.0F, 1.70F, 7.0F, 1.79F)
                    )
            )
    );

    private int hoveredIndex = -1;

    /** 已点击并正在播放放大动画的选项；-1 = 没有。 */
    private int selectingIndex = -1;
    private long selectStartedAt = 0L;
    private boolean selectionSent = false;

    public DifficultySelectScreen() {
        super(Component.translatable(TranslateUtils.GUI_DIFFICULTY_SELECT_TITLE));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int cardVisualH = ICON_SIZE + TEXT_GAP + this.font.lineHeight;
        int totalWidth = calculateTotalWidth();
        int startX = (this.width - totalWidth) / 2;
        int cardsY = centerY - cardVisualH / 2;

        int titleY = cardsY - TITLE_BOTTOM_GAP - this.font.lineHeight;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, titleY, 0xFFFFFF);

        int hintY = titleY - HINT_BOTTOM_GAP - this.font.lineHeight;
        guiGraphics.drawCenteredString(this.font,
                Component.translatable(TranslateUtils.GUI_DIFFICULTY_SELECT_HINT),
                centerX, hintY, 0xAAAAAA);

        float time = (System.currentTimeMillis() % 100_000L) / 1000f;

        // 命中检测要先算完（印记绘制需要知道哪张卡片被悬浮）
        hoveredIndex = -1;
        if (selectingIndex < 0) {
            int probeX = startX;
            int cardW = ICON_SIZE + CARD_PADDING * 2;
            int spacing = calculateSpacing();
            for (int i = 0; i < ENTRY_COUNT; i++) {
                int cardX = probeX;
                probeX += cardW + spacing;
                if (mouseX >= cardX && mouseX <= cardX + cardW
                        && mouseY >= cardsY && mouseY <= cardsY + cardVisualH) {
                    hoveredIndex = i;
                }
            }
        }

        int cursorX = startX;
        int cardW = ICON_SIZE + CARD_PADDING * 2;
        int spacing = calculateSpacing();

        for (int i = 0; i < ENTRY_COUNT; i++) {
            DifficultyEntry entry = ENTRIES.get(i);
            int cardX = cursorX;
            int cardY = cardsY;
            int cardBottom = cardY + cardVisualH;
            cursorX += cardW + spacing;

            boolean hovered = i == hoveredIndex;
            float scale = selectScaleFor(i);

            if (hovered) {
                guiGraphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardBottom + 1, 0x44FFFFFF);
                guiGraphics.fill(cardX, cardY, cardX + cardW, cardBottom, 0x22FFFFFF);
            }

            int iconX = cardX + (cardW - ICON_SIZE) / 2;
            int iconY = cardY + CARD_PADDING;

            float cardCenterX = cardX + cardW / 2.0F;
            float cardCenterY = iconY + ICON_SIZE / 2.0F;

            // 旧版径向发光：印记是硬边环带，这层贴图柔光补卡片氛围
            if (SplendidingShaders.guiGlowShader != null) {
                float glowIntensity;
                if (selectingIndex >= 0) {
                    float progress = Mth.clamp(selectProgress(), 0.0F, 1.0F);
                    glowIntensity = i == selectingIndex
                            ? Mth.lerp(progress, 1.0F, 0.35F)
                            : Mth.lerp(progress, 1.0F, 0.0F);
                } else {
                    glowIntensity = hovered ? 1.0F : 0.45F;
                }
                if (glowIntensity > 0.01F) {
                    renderGlow(guiGraphics.pose(), iconX, iconY, entry, time,
                            glowIntensity, scale);
                }
            }

            renderSigil(entry, i, cardCenterX, cardCenterY, time, hovered, scale);

            PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(iconX, iconY, 0);
            pose.scale(ICON_SIZE / 16f, ICON_SIZE / 16f, 1f);
            guiGraphics.blit(entry.texture, 0, 0, 0, 0, 16, 16, 16, 16);
            pose.popPose();

            int textY = iconY + ICON_SIZE + TEXT_GAP;
            int labelColor = (hovered || i == selectingIndex) ? 0xFFFF55 : 0xCCCCCC;
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable(entry.translationKey),
                    cardX + cardW / 2, textY, labelColor);
        }

        if (hoveredIndex >= 0 && hoveredIndex < ENTRY_COUNT) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable(ENTRIES.get(hoveredIndex).tooltipKey),
                    mouseX, mouseY);
        }
    }

    /**
     * 绘制一枚难度印记。顶点格式固定 {@code POSITION_TEX}（只有 Position + UV0）——
     * 这是调试得出的硬约束：格式带第三个属性时自定义着色器在此界面画不出任何东西，
     * 逐顶点参数全部编进 UV0。每枚一次 draw call（每个 profile 的颜色 uniform 不同）。
     */
    private void renderSigil(DifficultyEntry entry, int profileIndex,
                             float centerX, float centerY, float time,
                             boolean hovered, float scale) {
        ShaderInstance shader = SplendidingShaders.difficultySigilShader;
        if (shader == null) return;

        // 选中动画期间选中项保持满亮
        float hover = (hovered || profileIndex == selectingIndex) ? 1.0F : 0.0F;

        // 混合状态与 shader JSON 里声明的一致：加法混合
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(() -> SplendidingShaders.difficultySigilShader);

        setUniformSafe(shader, "uTime", time);
        setUniformSafe(shader, "uHover", hover);
        setUniformSafe(shader, "uAlpha", 1.0F);
        setUniformSafe(shader, "uProfile", (float) profileIndex);
        setUniformSafe(shader, "uQuad" + profileIndex + "Color",
                entry.glowR, entry.glowG, entry.glowB, 1.0F);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        for (Ring ring : entry.rings) {
            DifficultySigilMesh.bakeRing(buf, centerX, centerY, SIGIL_UNIT * scale, ring, time);
        }

        BufferUploader.drawWithShader(buf.end());

        // 还原，避免影响后续图标与文字的绘制
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * 绘制径向发光四边形（旧实现，留作印记底下的柔光）。shader 用 smoothstep 做圆形径向衰减，
     * 在 quad 边缘前完全淡出（不出方形裁切），并采样图标纹理 alpha 抑制透明区域的发光。
     */
    private static void renderGlow(PoseStack pose, int iconX, int iconY,
                                   DifficultyEntry entry, float time,
                                   float intensity, float scale) {
        float size = GLOW_QUAD_SIZE;

        RenderSystem.setShaderTexture(0, entry.texture);
        RenderSystem.setShader(() -> SplendidingShaders.guiGlowShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);

        ShaderInstance s = SplendidingShaders.guiGlowShader;
        setUniformSafe(s, "IconUVMin", UV_ICON_MIN, UV_ICON_MIN);
        setUniformSafe(s, "IconUVMax", UV_ICON_MAX, UV_ICON_MAX);
        setUniformSafe(s, "GlowColor", entry.glowR, entry.glowG, entry.glowB, intensity);
        setUniformSafe(s, "uTime", time);

        // 光晕必须与图标同心：quad 边长是 ICON_SIZE + 2*GLOW_RADIUS，从 iconX 起画会偏出 10px，
        // 所以以 quad 自身中心为锚点、中心取图标中心。
        float iconCenterX = iconX + ICON_SIZE * 0.5F;
        float iconCenterY = iconY + ICON_SIZE * 0.5F;

        pose.pushPose();
        pose.translate(iconCenterX, iconCenterY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-size * 0.5F, -size * 0.5F, 0.0F);

        Matrix4f mat = pose.last().pose();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(mat, 0,    0,    0).uv(0, 0).endVertex();
        buf.vertex(mat, 0,    size, 0).uv(0, 1).endVertex();
        buf.vertex(mat, size, size, 0).uv(1, 1).endVertex();
        buf.vertex(mat, size, 0,    0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(buf.end());

        pose.popPose();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.defaultBlendFunc();
    }

    /** 某个选项当前应有的缩放（idle：悬浮 1.0 / 其余 0.94；动画中按进度插值）。 */
    private float selectScaleFor(int index) {
        float idleHovered = 1.0F;
        float idleRest = 0.94F;

        if (selectingIndex < 0) {
            return index == hoveredIndex ? idleHovered : idleRest;
        }

        float progress = Mth.clamp(selectProgress(), 0.0F, 1.0F);
        if (index == selectingIndex) {
            return Mth.lerp(progress, idleRest, SELECT_SCALE);
        }
        return Mth.lerp(progress, idleHovered, UNSELECTED_SCALE);
    }

    /** 选中动画进度（0..1）。 */
    private float selectProgress() {
        if (selectingIndex < 0) return 0.0F;
        return (System.currentTimeMillis() - selectStartedAt) / SELECT_ANIM_MS;
    }

    /**
     * 安全上传 uniform。不能只判 {@code getUniform(name) != null}：int 类型的 uniform 只分配
     * {@code intValues}，{@code floatValues} 为 null，而 {@code set(float...)} 无条件访问它，
     * 于是 NPE —— 而那个 uniform 对象是存在的，判空挡不住（踩过：进界面客户端崩溃）。
     * 兜一层 catch 静默跳过（Uniform 没有公开 type 字段，调用前无法判断）。
     */
    private static void setUniformSafe(ShaderInstance shader, String name, float v) {
        if (shader == null || shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(v);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    private static void setUniformSafe(ShaderInstance shader, String name, float a, float b) {
        if (shader == null || shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(a, b);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    private static void setUniformSafe(ShaderInstance shader, String name,
                                       float a, float b, float c, float d) {
        if (shader == null || shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(a, b, c, d);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    /** 每个出问题的 uniform 只报一次，避免每帧刷日志。 */
    private static final java.util.Set<String> WARNED_UNIFORMS = new java.util.HashSet<>();

    private static void warnUniformOnce(String name, Throwable t) {
        if (WARNED_UNIFORMS.add(name)) {
            HallMod.LOGGER.warn("[hall-sigil] uniform '{}' 上传失败（多半是 JSON 里的 type 与 shader 声明不一致）：{}",
                    name, t.toString());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectingIndex >= 0) return true;

        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < ENTRY_COUNT) {
            selectingIndex = hoveredIndex;
            selectStartedAt = System.currentTimeMillis();
            selectionSent = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();

        if (selectingIndex < 0 || selectionSent) return;

        // 放大动画放完再发包关界面，否则屏幕立刻消失、"选中"没有任何反馈
        if (selectProgress() >= 1.0F) {
            DifficultyEntry entry = ENTRIES.get(selectingIndex);
            NetworkHelper.sendToServer(new C2SDifficultySelectPacket(entry.difficultyId));
            selectionSent = true;
            Minecraft.getInstance().setScreen(null);
        }
    }

    /**
     * 动画播放中不接受 ESC：否则玩家能在发包前关掉界面，而 {@code C2SDifficultySelectPacket}
     * 是一次性的（服务端记下已选），会造成"界面选了但没生效"。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectingIndex >= 0) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (selectingIndex >= 0 && !selectionSent) return;
        Minecraft.getInstance().setScreen(null);
    }

    /** 动画播放中禁止 ESC 关闭（{@link Screen} 的默认实现会走这里）。 */
    @Override
    public boolean shouldCloseOnEsc() {
        return selectingIndex < 0;
    }

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

    private record DifficultyEntry(
            ResourceLocation texture,
            String translationKey,
            String tooltipKey,
            ResourceLocation difficultyId,
            float glowR, float glowG, float glowB,
            List<Ring> rings
    ) {}
}
