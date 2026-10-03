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
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.client.gui.DifficultySigilMesh.Ring;
import org.bytechen.hall.network.NetworkHelper;
import org.bytechen.hall.network.c2s.C2SDifficultySelectPacket;
import org.bytechen.hall.overworld.difficulty.HallDifficulty;
import org.bytechen.hall.utils.TranslateUtils;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 难度选择界面。
 * <p>
 * 首个玩家首次进入世界时自动弹出。四张卡片横向排列，每张卡片上有一枚
 * <b>程序化生成的「印记」</b>：几何在 CPU 侧按难度烘成环带，片元阶段按 profile
 * 出不同的角度图案（见 {@link DifficultySigilMesh} 与
 * {@code rendertype_difficulty_sigil.*}）。
 *
 * <h3>四档的视觉区分</h3>
 * <p>刻意在「几何复杂度 / 旋转方向 / 角度图案」三个维度上拉开，
 * 而不是只换颜色 —— 否则四个看起来仍然是同一个东西：
 * <ol>
 *   <li><b>简单</b>：净同心圆，缓慢呼吸，内部透明（没有任何角度图案）</li>
 *   <li><b>普通</b>：六边形 + 内接三角，反向自转，3 段缺口（秩序感）</li>
 *   <li><b>困难</b>：12 根不等长尖刺 + 5 段撕裂，正反两向叠转（轮廓破了）</li>
 *   <li><b>无法理解</b>：半径阶梯跳变 + 反向双环 + 7 扇区独立闪烁（秩序被破坏）</li>
 * </ol>
 *
 * <h3>动画节奏</h3>
 * <p><b>常态就在动</b>（低速自转 + 呼吸），悬浮时提速并提亮，
 * 点击后所选印记在 {@value #SELECT_ANIM_MS} 毫秒内放大 {@value #SELECT_SCALE} 倍，
 * 其余三枚同时收缩淡出 —— 放完才发包关界面，让"选中"这件事有反馈。
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

    /** 印记的基准半尺寸（像素）：所有环半径都以它为 1.0 的基准。 */
    private static final float SIGIL_UNIT = 30.0F;

    /** 选中放大动画时长（毫秒）。 */
    private static final float SELECT_ANIM_MS = 480.0F;
    /** 选中后放大的倍数。 */
    private static final float SELECT_SCALE = 1.34F;
    /** 未选中的印记在动画期间收缩到的倍数。 */
    private static final float UNSELECTED_SCALE = 0.76F;

    // ==================== 四个难度 ====================

    /**
     * 四个难度选项。
     *
     * <h3>配色来源</h3>
     * <p>每档的 {@code glow*} 三元组（同时用于印记基色与背后柔光）取自<b>图标本身的实际主色</b>，
     * 而不是另定一套卡片配色 —— 否则会出现"图标是蓝的、圈是金的"这种不一致。
     * 数值是把图标 PNG 的不透明像素按亮度/饱和度筛出主体色后统计得到的：
     * <ul>
     *   <li>简单 {@code #273926} —— 深森绿</li>
     *   <li>普通 {@code #1F2580} —— 蓝紫</li>
     *   <li>困难 {@code #FD0F59} —— 玫红</li>
     *   <li>无法理解 {@code #CBD0FF} —— 苍白色。
     *       该图标本身红、白两组颜色各占一半，按需求取其中的苍白系
     *       （同组还有 {@code #A9B1FE} 与 {@code #EDD0FF}，一个偏蓝一个偏紫，取了中间的）。</li>
     * </ul>
     *
     * <p>{@code rings} 是各自的印记几何，顺序即 {@code uProfile} 的取值（0..3），
     * 与 shader 里的分支一一对应。
     * <p>{@code Ring} 的最后两个参数是「图案倍数」与「图案相位偏移」，
     * 倍数必须与 shader 里 {@code patternMultiplicity()} 的分支一致
     * （简单 1 / 普通 3 / 困难 5 / 无法理解 7），偏移用来让同档三环的缺口错开。
     */
    private static final List<DifficultyEntry> ENTRIES = List.of(
            new DifficultyEntry(
                    ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/difficulty_easy.png"),
                    TranslateUtils.DIFFICULTY_EASY, TranslateUtils.DIFFICULTY_EASY_TOOLTIP,
                    HallDifficulty.EASY,
                    0.153f, 0.224f, 0.149f,   // #273926 图标主色·深森绿
                    // 简单：净同心圆，慢呼吸，无角度图案（倍数 1 且 shader 直接返回 1）
                    // 环带宽度都抬到 2.2px 以上：亚像素宽的细线在斜向光栅化时会被丢样本，
                    // 表现就是环上东一处西一处的断口。
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
                    // 普通：六边形 + 内接三角，反向自转，几乎不变形（靠棱角出秩序感）
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
                    // 困难：12 根不等长尖刺（长度由哈希固定）+ 正反叠转，轮廓是破的
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
                    // 无法理解：三环互相反向、半径阶梯跳变。
                    // wobble 刻意压得比另外三档都小 —— Steps 本身就有阶跃，
                    // 再叠大 wobble 会让相邻两环互相穿插、糊成一团。
                    List.of(
                            new Ring(1.00F, 2.5F, 0.95F, 0.62F, new DifficultySigilMesh.Steps(9, 0.14F, 1.3F), 0.030F, 6.0F, 2.40F, 7.0F, 0.0F),
                            new Ring(0.80F, 2.2F, 0.70F, -0.78F, new DifficultySigilMesh.Steps(7, 0.18F, 4.1F), 0.035F, 5.0F, 3.10F, 7.0F, 0.90F),
                            new Ring(0.58F, 2.0F, 0.45F, 1.05F, new DifficultySigilMesh.Steps(5, 0.22F, 7.7F), 0.045F, 4.0F, 1.70F, 7.0F, 1.79F)
                    )
            )
    );

    // ==================== 状态 ====================

    private int hoveredIndex = -1;

    /** 已点击并正在播放放大动画的选项；-1 = 没有。 */
    private int selectingIndex = -1;
    /** 放大动画开始时刻（{@link System#currentTimeMillis()}）。 */
    private long selectStartedAt = 0L;
    /** 动画结束、难度已发往服务端。 */
    private boolean selectionSent = false;

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

        // ── 绘制四张难度卡片 ──
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

            // 卡片高亮背景
            if (hovered) {
                guiGraphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardBottom + 1, 0x44FFFFFF);
                guiGraphics.fill(cardX, cardY, cardX + cardW, cardBottom, 0x22FFFFFF);
            }

            int iconX = cardX + (cardW - ICON_SIZE) / 2;
            int iconY = cardY + CARD_PADDING;

            float cardCenterX = cardX + cardW / 2.0F;
            float cardCenterY = iconY + ICON_SIZE / 2.0F;

            // ── 旧版径向发光：留作柔光底衬 ──
            // 印记是硬边环带，单靠它会让卡片显得干；这层贴图采样的柔光补上氛围。
            if (SplendidingShaders.guiGlowShader != null) {
                float glowIntensity;
                if (selectingIndex >= 0) {
                    // 选中动画期间：选中的那枚留一点光，其余淡出
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

            // ── 难度印记（程序化几何）──
            renderSigil(entry, i, cardCenterX, cardCenterY, time, hovered, scale);

            // ── 图标 ──
            PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(iconX, iconY, 0);
            pose.scale(ICON_SIZE / 16f, ICON_SIZE / 16f, 1f);
            guiGraphics.blit(entry.texture, 0, 0, 0, 0, 16, 16, 16, 16);
            pose.popPose();

            // ── 标签文字 ──
            int textY = iconY + ICON_SIZE + TEXT_GAP;
            int labelColor = (hovered || i == selectingIndex) ? 0xFFFF55 : 0xCCCCCC;
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable(entry.translationKey),
                    cardX + cardW / 2, textY, labelColor);
        }

        // ── 悬浮 tooltip ──
        if (hoveredIndex >= 0 && hoveredIndex < ENTRY_COUNT) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable(ENTRIES.get(hoveredIndex).tooltipKey),
                    mouseX, mouseY);
        }
    }

    /**
     * 绘制一枚难度印记。
     *
     * <p>顶点格式固定 {@code POSITION_TEX}（只有 Position + UV0）。
     * 这是调试得出的硬约束：格式带第三个属性时，自定义着色器在此界面画不出任何东西。
     * 逐顶点参数全部编进 UV0，详见 {@link DifficultySigilMesh} 的类注释。
     *
     * <p>每枚一次 draw call：几何可以合批，但每个 profile 的颜色 uniform 不同，
     * 分开发更简单，四枚总共也就两千个顶点上下。
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
     * 绘制径向发光四边形。shader 用 smoothstep 做圆形径向衰减，
     * 在 quad 边缘前完全淡出（不会出现方形裁切），
     * 并采样图标纹理 alpha 来抑制透明区域的发光。
     * <p>这一层是旧实现，现在留作印记底下的柔光 ——
     * 印记本身是硬边环带，单靠它卡片会显得干。
     *
     * @param pose      GUI 的 PoseStack，用来平移/缩放到目标位置
     * @param intensity 发光强度（0..1）。常态也给一点，不是只有悬浮才亮
     * @param scale     跟随印记缩放，让柔光和印记一起放大
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

        // 光晕必须与图标同心。
        // 图标画在 iconX..iconX+ICON_SIZE，所以它的中心是 iconX + ICON_SIZE/2；
        // 而发光 quad 的边长是 GLOW_QUAD_SIZE（= ICON_SIZE + 2*GLOW_RADIUS），
        // 若从 iconX 起画，quad 中心会落在 iconX + ICON_SIZE/2 + GLOW_RADIUS，整整偏出 10px。
        // 这里以 quad 自身中心为锚点做平移缩放，中心取图标中心，两者就对齐了。
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

        // 还原
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * 某个选项当前应有的缩放。
     * <p>没在播放选中动画时：悬浮 1.0，其余略缩保持安静；
     * 播放中：选中项放大到 {@link #SELECT_SCALE}，其余收缩到 {@link #UNSELECTED_SCALE}。
     */
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

    // ==================== uniform 上传 ====================

    /**
     * 安全上传 uniform。
     *
     * <h3>为什么不能只判 {@code getUniform(name) != null}</h3>
     * <p>{@code Uniform} 的构造器按类型分配缓冲（{@code Uniform.java:46-52}）：
     * <ul>
     *   <li>{@code int} 类型（UT_INT1..4）→ 只分配 {@code intValues}，
     *       {@code floatValues} 被显式置为 <b>null</b>；</li>
     *   <li>{@code float} / {@code matrix} 类型 → 只分配 {@code floatValues}。</li>
     * </ul>
     * 而 {@code set(float...)} 系列<b>无条件</b>访问 {@code floatValues}
     * （{@code Uniform.java:120-124}）。
     * 于是给一个 int 类型的 uniform 调 {@code set(float)} 会直接
     * {@code NullPointerException: this.floatValues is null} —— 而这个 uniform
     * 对象<b>是存在的</b>，判空完全挡不住。这里踩过一次，表现为渲染界面时客户端崩溃。
     * <p>所以兜一层 {@code catch}：uniform 类型写错时静默跳过该次上传，
     * 界面继续可用，而不是把整个客户端带崩。
     * （{@code Uniform} 没有公开 type 字段，无法在调用前判断，只能这样兜。）
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

    // ==================== 鼠标交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 动画播放中忽略后续点击
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

        // 放大动画放完再把选择发给服务端，然后关界面 ——
        // 否则屏幕会立刻消失，"选中"这件事没有任何反馈。
        if (selectProgress() >= 1.0F) {
            DifficultyEntry entry = ENTRIES.get(selectingIndex);
            NetworkHelper.sendToServer(new C2SDifficultySelectPacket(entry.difficultyId));
            selectionSent = true;
            Minecraft.getInstance().setScreen(null);
        }
    }

    /**
     * 动画播放中不接受 ESC。
     * <p>否则玩家可以在发包前关掉界面，而 {@code C2SDifficultySelectPacket}
     * 是一次性的（服务端 {@code HallWorldData} 会记下已选），会造成"界面选了但没生效"。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectingIndex >= 0) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== 界面属性 ====================

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        // 放在 tick() 里关，不要在动画中途被关掉
        if (selectingIndex >= 0 && !selectionSent) return;
        Minecraft.getInstance().setScreen(null);
    }

    /** 动画播放中禁止 ESC 关闭（{@link Screen} 的默认实现会走这里）。 */
    @Override
    public boolean shouldCloseOnEsc() {
        return selectingIndex < 0;
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
            float glowR, float glowG, float glowB,
            List<Ring> rings
    ) {}
}
