import java.util.Locale;
import java.util.Random;

/**
 * 场景：GUI 特效离屏预览 —— 两套叠加类 core shader。
 *
 * <h3>horror voronoi（rendertype_gui_horror_voronoi）</h3>
 * <p>实机由 {@code GuiShaderRenderer} 每帧在 HOTBAR 层之后画一张<b>覆盖整个 GUI 屏幕</b>
 * 的半透明 quad：uniform {@code ScreenSize}=(GUI 缩放后的宽, 高)、{@code Time}（墙钟秒）、
 * {@code uIntensity}（效果强度，fsh 里直接乘在最终 alpha 上）。</p>
 * <p>本场景把同一张 quad 缩小到 200×90 画三份（uIntensity=0.25/0.60/1.00），并把
 * {@code ScreenSize} 设成 <b>quad 自身尺寸</b>：fsh 里 {@code ScreenSize.x/ScreenSize.y}
 * 只用于噪声的宽高比校正（{@code aspect}），传 quad 尺寸才能让 voronoi 元胞在这个区块里
 * 保持圆形；若传整张画布尺寸（640×400）而 quad 是 200×90，元胞会被横向拉长 1.4 倍。
 * 实机里 quad 恰好铺满屏幕，所以 "ScreenSize=quad 尺寸" 和实机语义一致。</p>
 *
 * <h3>gui_glow（rendertype_gui_glow）</h3>
 * <p>实机由 {@code DifficultySelectScreen#renderGlow} 画在四个难度图标底下：
 * quad 边长 {@code GLOW_QUAD_SIZE=68}（= ICON_SIZE 48 + 2×GLOW_RADIUS 10），
 * 与 48×48 图标<b>同心</b>（中心对齐，不能从 iconX 起画，否则整块偏出 10px）；
 * {@code IconUVMin/Max=0.147/0.853} 把 16×16 贴图里"有内容的图标区"映射到 quad
 * 中间 48px 上，fsh 用这段 UV 的 alpha 抑制透明区的发光。json 声明的混合是加法
 * （src_alpha / one），所以画完光晕后必须把 GL 混合状态还原成普通 alpha，否则
 * 后面叠加的图标与文字会被"加"进画面（框架的 fill/blit 不会自动重置混合状态）。</p>
 * <p><b>注意一个实机行为：</b>难度图标 PNG 是硬边 0/255 alpha、四周还有透明边距，
 * 而 fsh 用 {@code step(0.03, iconAlpha)} 把光晕完全压掉，所以真实渲染里 68 的
 * 光晕几乎看不见（只在贴图边缘被 clamp 采样到的地方漏出几粒）。本场景除了如实的
 * 一版，还额外画一版"绕过 alpha gate"的参考光晕（换成不透明遮罩采样），用来检查
 * 径向衰减、颜色和 68×68 几何是否居中；这不是实机画面，已明确标注。</p>
 *
 * <p>产物：{@code gui_effects.png}（总览，GuiPreview 渲染结束后自动保存）、
 * {@code gui_voronoi.png}、{@code gui_glow.png}（两张细节页，read_image 自查用）。</p>
 */
public final class SceneGuiEffects {

    private SceneGuiEffects() {}

    // ══════════════════════════════════════════════════════════════
    //  难度光晕常量（照抄 DifficultySelectScreen）
    // ══════════════════════════════════════════════════════════════

    private static final int ICON_SIZE = 48;
    private static final int GLOW_RADIUS = 10;
    private static final int GLOW_QUAD_SIZE = ICON_SIZE + GLOW_RADIUS * 2;          // 68
    private static final float UV_ICON_MIN = (float) GLOW_RADIUS / GLOW_QUAD_SIZE;         // 0.147
    private static final float UV_ICON_MAX = (float) (GLOW_RADIUS + ICON_SIZE) / GLOW_QUAD_SIZE; // 0.853
    private static final int CARD_PADDING = 4;
    private static final int TEXT_GAP = 10;
    private static final int FONT_H = 9;
    /** 与实机 cardVisualH = ICON_SIZE + TEXT_GAP + font.lineHeight 相同（67）。 */
    private static final int CARD_H = ICON_SIZE + TEXT_GAP + FONT_H;

    /** 预览里把第 2 个（Normal）当作 hovered；实机 hovered 时 glowIntensity=1.0，否则 0.45。 */
    private static final int HOVERED_INDEX = 1;
    private static final float GLOW_HOVERED = 1.0F;
    private static final float GLOW_IDLE = 0.45F;

    /**
     * 用来"绕过 alpha gate"的全不透明 16×16 遮罩（项目里唯一 16×16 且 alpha 全满的 PNG）。
     * 只作为 gui_glow 采样器的诊断替身：fsh 只用它的 alpha，不透明 → 光晕完整显示。
     */
    private static final String OPAQUE_MASK = "textures/item/light_mana_plate_mask_glow.png";

    private static final String[] DIFFICULTY_NAMES = {"Easy", "Normal", "Hard", "Incomprehensible"};
    private static final String[] DIFFICULTY_TEXTURES = {
            "textures/gui/difficulty_easy.png",
            "textures/gui/difficulty_normal.png",
            "textures/gui/difficulty_hard.png",
            "textures/gui/difficulty_incomprehensible.png"};
    private static final String[] DIFFICULTY_HEX = {"#273926", "#1F2580", "#FD0F59", "#CBD0FF"};
    /** ENTRIES 里的 glowR/G/B（图标主色），顺序 easy/normal/hard/incomprehensible。 */
    private static final float[][] DIFFICULTY_COLORS = {
            {0.153F, 0.224F, 0.149F},
            {0.122F, 0.145F, 0.502F},
            {0.992F, 0.059F, 0.349F},
            {0.796F, 0.816F, 1.000F}};

    // ══════════════════════════════════════════════════════════════
    //  恐怖 Voronoi 叠加层常量
    // ══════════════════════════════════════════════════════════════

    private static final int VOR_W = 200;
    private static final int VOR_H = 90;
    private static final float[] VOR_INTENSITY = {0.25F, 0.60F, 1.00F};
    private static final float[] VOR_TIME_OFFSET = {0.0F, 1.3F, 2.6F};

    // ══════════════════════════════════════════════════════════════
    //  共用
    // ══════════════════════════════════════════════════════════════

    private static final int CLEAR = 0xFF000000;
    /** c.text() 每段的最大字符数（绕过框架 MemoryStack 64KB 顶点上传上限，见 txt()）。 */
    private static final int TEXT_CHUNK = 16;
    private static final int TEXT_TITLE = 0xFFFFFFFF;
    private static final int TEXT_BODY = 0xFFCCCCCC;
    private static final int TEXT_DIM = 0xFF8899AA;
    private static final int TEXT_ACCENT = 0xFFFFE08A;
    private static final int TEXT_WARN = 0xFFFFB0B0;

    public static void render(PreviewCanvas c, float time) {
        // 细节页先画（GuiPreview 在 render 返回后才自动保存 gui_effects.png，
        // 所以最后留在画布上的必须是总览页）。
        c.clear(CLEAR);
        drawVoronoiDetail(c, time);
        c.save("gui_voronoi");

        c.clear(CLEAR);
        drawGlowDetail(c, time);
        c.save("gui_glow");

        c.clear(CLEAR);
        drawOverview(c, time);
    }

    // ══════════════════════════════════════════════════════════════
    //  细节页 ①：horror voronoi 三档强度
    // ══════════════════════════════════════════════════════════════

    private static void drawVoronoiDetail(PreviewCanvas c, float time) {
        c.backdrop();
        txt(c, "rendertype_gui_horror_voronoi", 16, 10, TEXT_TITLE, true);
        txt(c, "Full-screen horror overlay - blood fbm + animated voronoi cell rims",
                16, 24, TEXT_BODY, false);
        txt(c, "uIntensity = 0.25 / 0.60 / 1.00 (top to bottom)", 16, 36, TEXT_BODY, false);
        txt(c, "time=" + fmt("%.2f", time)
                        + "   quad=200x90   ScreenSize=(200,90) keeps the pattern aspect-correct",
                16, 48, TEXT_DIM, false);

        PreviewShader vor = c.shader("rendertype_gui_horror_voronoi");
        int x = 16;
        int[] blockY = {64, 170, 276};
        int tx = 244;
        int worldY = 218;

        // ── 先画所有 fill / 文字（它们会切走 GL program）──
        for (int i = 0; i < 3; i++) {
            c.strokeRect(x - 2, blockY[i] - 2, VOR_W + 4, VOR_H + 4, 0x66FF4FB8, 0x339B4DFF);
            txt(c, fmt("uIntensity=%.2f   Time=%.2f  (+%.2f s)",
                            VOR_INTENSITY[i], time + VOR_TIME_OFFSET[i], VOR_TIME_OFFSET[i]),
                    x, blockY[i] + VOR_H + 4, TEXT_BODY, false);
        }
        drawWorldProxy(c, tx, worldY, VOR_W, VOR_H);
        c.strokeRect(tx - 2, worldY - 2, VOR_W + 4, VOR_H + 4, 0x66FF4FB8, 0x339B4DFF);
        txt(c, "uIntensity=0.60 over a brighter world proxy", tx, worldY + VOR_H + 4,
                TEXT_BODY, false);

        txt(c, "How it is driven (GuiShaderRenderer)", tx, 60, TEXT_ACCENT, true);
        txt(c, "ScreenSize = GUI-scaled window (w,h)", tx, 78, TEXT_BODY, false);
        txt(c, "Time = wall-clock seconds", tx, 90, TEXT_BODY, false);
        txt(c, "uIntensity = strength; final alpha *= uIntensity", tx, 102, TEXT_BODY, false);
        txt(c, "In game: one quad over the whole screen, drawn", tx, 120, TEXT_BODY, false);
        txt(c, "  once per frame after the GUI is finished.", tx, 132, TEXT_BODY, false);
        txt(c, "Preview: each block is its own quad, so pass", tx, 150, TEXT_BODY, false);
        txt(c, "  the block size as ScreenSize (cells stay round).", tx, 162, TEXT_BODY, false);
        txt(c, "Blend srcalpha / 1-srcalpha: a dark red veil", tx, 180, TEXT_BODY, false);
        txt(c, "  that darkens whatever sits behind it.", tx, 192, TEXT_BODY, false);
        txt(c, "0.25 is only a faint veil on this dark backdrop;", tx, 204, TEXT_DIM, false);

        int nx = tx + 212;
        txt(c, "In game the world behind", nx, worldY + 4, TEXT_DIM, false);
        txt(c, "is much brighter, so the", nx, worldY + 16, TEXT_DIM, false);
        txt(c, "overlay reads as dark red", nx, worldY + 28, TEXT_DIM, false);
        txt(c, "filaments over the scene", nx, worldY + 40, TEXT_DIM, false);
        txt(c, "(not a gray blob on black).", nx, worldY + 52, TEXT_DIM, false);

        // ── 再画 shader：prime 一次后，set 和 quad 之间不能夹任何 fill/text ──
        prime(c, vor);
        for (int i = 0; i < 3; i++) {
            drawVoronoiBlock(c, vor, x, blockY[i], VOR_W, VOR_H,
                    time + VOR_TIME_OFFSET[i], VOR_INTENSITY[i]);
        }
        drawVoronoiBlock(c, vor, tx, worldY, VOR_W, VOR_H, time + 0.7F, 0.60F);
    }

    /** 按实机 uniform 画一块 voronoi；ScreenSize 用 quad 尺寸保证宽高比正确。 */
    private static void drawVoronoiBlock(PreviewCanvas c, PreviewShader vor,
                                         int x, int y, int w, int h,
                                         float time, float intensity) {
        c.pushClip(x, y, w, h);
        vor.set("ScreenSize", (float) w, (float) h);
        vor.set("Time", time);
        vor.set("uIntensity", intensity);
        c.shaderQuad(vor, x, y, w, h);
        c.popClip();
    }

    /**
     * 一块"比 preview 背景亮的世界"底图：黄昏天空 + 草地/泥土马赛克 + 几个亮方块。
     * 仅用于让叠加层的"压暗+染红"在离屏预览里也看得清（实机世界比 backdrop 亮得多）。
     */
    private static void drawWorldProxy(PreviewCanvas c, int x, int y, int w, int h) {
        c.fillGradientV(x, y, w, h, 0xFF9CB4CC, 0xFF3A4432);
        c.fill(x, y + 50, w, 2, 0xFF8A9670);
        Random random = new Random(20261031L);
        for (int gy = y + 52; gy < y + h; gy += 8) {
            for (int gx = x; gx < x + w; gx += 8) {
                int shade = 0x48 + random.nextInt(0x48);
                int col = 0xFF000000 | (shade << 16) | ((shade + 0x14) << 8) | (shade >> 1);
                c.fill(gx, gy, Math.min(8, x + w - gx), Math.min(8, y + h - gy), col);
            }
        }
        for (int i = 0; i < 12; i++) {
            int hx = x + 2 + random.nextInt(w - 8);
            int hy = y + 54 + random.nextInt(h - 60);
            c.fill(hx, hy, 4, 4, 0xFFF0EAD2);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  细节页 ②：四个难度图标 + gui_glow 光晕
    // ══════════════════════════════════════════════════════════════

    private static void drawGlowDetail(PreviewCanvas c, float time) {
        c.backdrop();
        txt(c, "rendertype_gui_glow", 16, 10, TEXT_TITLE, true);
        txt(c, "Difficulty icon halo - 68x68 additive quad centered behind the 48x48 icon",
                16, 24, TEXT_BODY, false);
        txt(c, "time=" + fmt("%.2f", time)
                        + "   ICON_SIZE=" + ICON_SIZE
                        + "   GLOW_QUAD_SIZE=" + GLOW_QUAD_SIZE
                        + "   IconUV " + fmt("%.3f", UV_ICON_MIN) + ".." + fmt("%.3f", UV_ICON_MAX),
                16, 36, TEXT_DIM, false);

        PreviewShader glow = c.shader("rendertype_gui_glow");
        glow.applyAlphaBlend();   // 上一页可能留下加法混合，先还原成普通 alpha

        PreviewTexture[] icons = new PreviewTexture[4];
        for (int i = 0; i < 4; i++) {
            icons[i] = c.texture(DIFFICULTY_TEXTURES[i]);
        }
        PreviewTexture opaque = c.texture(OPAQUE_MASK);

        int cardW = ICON_SIZE + CARD_PADDING * 2;      // 56
        int gap = 26;
        int totalW = 4 * cardW + 3 * gap;              // 302
        int startX = (c.width - totalW) / 2;           // 169
        int cardY = 66;
        int iconY = cardY + CARD_PADDING;              // 70
        int centerY = iconY + ICON_SIZE / 2;           // 94
        int[] centerX = new int[4];
        for (int i = 0; i < 4; i++) {
            centerX[i] = startX + i * (cardW + gap) + cardW / 2;
        }

        txt(c, "A. faithful render: glow quad + icon exactly as DifficultySelectScreen does it",
                16, 50, TEXT_ACCENT, true);

        // 卡片底：hovered 用实机的两层白高亮；其余给一层很淡的卡片底（便于看边）
        for (int i = 0; i < 4; i++) {
            int cardX = startX + i * (cardW + gap);
            c.fill(cardX, cardY, cardW, CARD_H, 0x14FFFFFF);
            if (i == HOVERED_INDEX) {
                c.fill(cardX - 1, cardY - 1, cardW + 2, CARD_H + 2, 0x44FFFFFF);
                c.fill(cardX, cardY, cardW, CARD_H, 0x22FFFFFF);
            }
        }

        // 光晕：json blend = 加法混合；prime 之后连续 set+quad（中间不夹 fill/text）
        prime(c, glow);
        for (int i = 0; i < 4; i++) {
            float intensity = i == HOVERED_INDEX ? GLOW_HOVERED : GLOW_IDLE;
            glow.setSampler("IconTexture", icons[i]);
            glow.set("IconUVMin", UV_ICON_MIN, UV_ICON_MIN);
            glow.set("IconUVMax", UV_ICON_MAX, UV_ICON_MAX);
            glow.set("GlowColor", DIFFICULTY_COLORS[i][0], DIFFICULTY_COLORS[i][1],
                    DIFFICULTY_COLORS[i][2], intensity);
            glow.set("uTime", time);
            c.shaderQuad(glow,
                    centerX[i] - GLOW_QUAD_SIZE / 2, centerY - GLOW_QUAD_SIZE / 2,
                    GLOW_QUAD_SIZE, GLOW_QUAD_SIZE);
        }
        glow.applyAlphaBlend();

        // ── 图标与标签 ──
        for (int i = 0; i < 4; i++) {
            boolean hovered = i == HOVERED_INDEX;
            c.blit(icons[i], centerX[i] - ICON_SIZE / 2, centerY - ICON_SIZE / 2,
                    ICON_SIZE, ICON_SIZE);

            int labelY = iconY + ICON_SIZE + TEXT_GAP;  // 实机 textY
            txtCentered(c, DIFFICULTY_NAMES[i], centerX[i], labelY,
                    hovered ? 0xFFFFFF55 : 0xFFCCCCCC, true);
            txtCentered(c, DIFFICULTY_HEX[i], centerX[i], labelY + 14, 0xFF8899AA, false);
            txtCentered(c, fmt("glow %.2f", hovered ? GLOW_HOVERED : GLOW_IDLE),
                    centerX[i], labelY + 25, 0xFF667788, false);
        }
        txtCentered(c, "hovered", centerX[HOVERED_INDEX], labelY0(iconY) + 38, 0xFFFFFF55, true);

        // ── B：诊断用参考光晕（绕过 icon-alpha gate）──
        txt(c, "B. reference only: same 68x68 quad with the icon-alpha gate bypassed (opaque mask)",
                16, 180, TEXT_ACCENT, true);
        txt(c, "   shows radial falloff / color / centering; crosses mark the quad centers",
                16, 192, TEXT_DIM, false);

        int refTop = 204;
        int refCenterY = refTop + GLOW_QUAD_SIZE / 2;  // 238
        int refBottom = refTop + GLOW_QUAD_SIZE;       // 272
        prime(c, glow);
        for (int i = 0; i < 4; i++) {
            float intensity = i == HOVERED_INDEX ? GLOW_HOVERED : GLOW_IDLE;
            glow.setSampler("IconTexture", opaque);
            glow.set("IconUVMin", UV_ICON_MIN, UV_ICON_MIN);
            glow.set("IconUVMax", UV_ICON_MAX, UV_ICON_MAX);
            glow.set("GlowColor", DIFFICULTY_COLORS[i][0], DIFFICULTY_COLORS[i][1],
                    DIFFICULTY_COLORS[i][2], intensity);
            glow.set("uTime", time);
            c.shaderQuad(glow,
                    centerX[i] - GLOW_QUAD_SIZE / 2, refTop,
                    GLOW_QUAD_SIZE, GLOW_QUAD_SIZE);
        }
        glow.applyAlphaBlend();
        for (int i = 0; i < 4; i++) {
            // 框 = 68 quad 边界，十字 = quad 中心，和上排图标列完全同 x
            c.strokeRect(centerX[i] - GLOW_QUAD_SIZE / 2, refTop,
                    GLOW_QUAD_SIZE, GLOW_QUAD_SIZE, 0x44FFFFFF, 0x22FFFFFF);
            c.fill(centerX[i] - 6, refCenterY, 13, 1, 0xCCFFFFFF);
            c.fill(centerX[i], refCenterY - 6, 1, 13, 0xCCFFFFFF);
            txtCentered(c, DIFFICULTY_HEX[i], centerX[i], refBottom + 4, 0xFF8899AA, false);
            txtCentered(c, fmt("glow %.2f", i == HOVERED_INDEX ? GLOW_HOVERED : GLOW_IDLE),
                    centerX[i], refBottom + 15, 0xFF667788, false);
        }

        // ── 为什么 A 几乎看不见（实机行为，不是预览 bug）──
        txt(c, "Why A looks empty - real shader behavior, not a preview bug:", 16, 300,
                TEXT_WARN, true);
        txt(c, "  fsh gates the halo with step(0.03, iconAlpha); the difficulty PNGs are hard",
                16, 314, TEXT_BODY, false);
        txt(c, "  0/255 alpha with transparent margins, so only clamped edge texels leak a few",
                16, 326, TEXT_BODY, false);
        txt(c, "  tinted pixels. The quad geometry itself is correct (see B + source anchor).",
                16, 338, TEXT_BODY, false);
        txt(c, "  Geometry: quad top-left = iconCenter - 34, icon top-left = iconCenter - 24,",
                16, 354, TEXT_BODY, false);
        txt(c, "  so both share one center; starting the quad at iconX would offset it by 10 px.",
                16, 366, TEXT_BODY, false);
        txt(c, "  Hovered entry (Normal) glow alpha 1.00, other three 0.45; blend src_alpha / one.",
                16, 378, TEXT_BODY, false);
    }

    /** 图标标签行的第一行基线（实机 textY = iconY + ICON_SIZE + TEXT_GAP）。 */
    private static int labelY0(int iconY) {
        return iconY + ICON_SIZE + TEXT_GAP;
    }

    // ══════════════════════════════════════════════════════════════
    //  总览页（render 返回后自动存成 gui_effects.png）
    // ══════════════════════════════════════════════════════════════

    private static void drawOverview(PreviewCanvas c, float time) {
        c.backdrop();
        txt(c, "GUI effects - offline preview", 14, 10, TEXT_TITLE, true);
        txt(c, "time=" + fmt("%.2f", time) + "   horror voronoi overlay + difficulty icon glow",
                14, 24, TEXT_BODY, false);

        PreviewShader vor = c.shader("rendertype_gui_horror_voronoi");
        PreviewShader glow = c.shader("rendertype_gui_glow");
        glow.applyAlphaBlend();

        // 上：voronoi 三档强度（先 fill 边框，再统一 shader 绘制）
        int bw = VOR_W;
        int bh = VOR_H;
        int bgap = 6;
        int by = 42;
        for (int i = 0; i < 3; i++) {
            int bx = 12 + i * (bw + bgap);
            c.strokeRect(bx - 1, by - 1, bw + 2, bh + 2, 0x55FF4FB8, 0x339B4DFF);
        }
        prime(c, vor);
        for (int i = 0; i < 3; i++) {
            int bx = 12 + i * (bw + bgap);
            drawVoronoiBlock(c, vor, bx, by, bw, bh,
                    time + VOR_TIME_OFFSET[i], VOR_INTENSITY[i]);
        }
        for (int i = 0; i < 3; i++) {
            int bx = 12 + i * (bw + bgap);
            txt(c, fmt("uInt=%.2f  t=%.2f", VOR_INTENSITY[i], time + VOR_TIME_OFFSET[i]),
                    bx, by + bh + 3, TEXT_BODY, false);
        }
        txt(c, "horror voronoi: uIntensity 0.25 / 0.60 / 1.00 over the world backdrop",
                12, 158, TEXT_ACCENT, true);
        txt(c, "gui_glow: difficulty icons, additive halo (ICON_SIZE=48, GLOW_QUAD=68)",
                12, 170, TEXT_ACCENT, true);

        // 下：四个难度图标
        PreviewTexture[] icons = new PreviewTexture[4];
        for (int i = 0; i < 4; i++) {
            icons[i] = c.texture(DIFFICULTY_TEXTURES[i]);
        }
        int cardW = ICON_SIZE + CARD_PADDING * 2;
        int spacing = 24;
        int rowW = 4 * GLOW_QUAD_SIZE + 3 * spacing;   // 344
        int startX = (c.width - rowW) / 2;             // 148
        int glowTop = 184;
        int centerY = glowTop + GLOW_QUAD_SIZE / 2;    // 218
        int iconY = centerY - ICON_SIZE / 2;           // 194
        int cardY = iconY - CARD_PADDING;              // 190
        int[] centerX = new int[4];
        for (int i = 0; i < 4; i++) {
            centerX[i] = startX + i * (GLOW_QUAD_SIZE + spacing) + GLOW_QUAD_SIZE / 2;
        }

        for (int i = 0; i < 4; i++) {
            int cardX = centerX[i] - cardW / 2;
            c.fill(cardX, cardY, cardW, CARD_H, 0x14FFFFFF);
            if (i == HOVERED_INDEX) {
                c.fill(cardX - 1, cardY - 1, cardW + 2, CARD_H + 2, 0x44FFFFFF);
                c.fill(cardX, cardY, cardW, CARD_H, 0x22FFFFFF);
            }
        }
        prime(c, glow);
        for (int i = 0; i < 4; i++) {
            float intensity = i == HOVERED_INDEX ? GLOW_HOVERED : GLOW_IDLE;
            glow.setSampler("IconTexture", icons[i]);
            glow.set("IconUVMin", UV_ICON_MIN, UV_ICON_MIN);
            glow.set("IconUVMax", UV_ICON_MAX, UV_ICON_MAX);
            glow.set("GlowColor", DIFFICULTY_COLORS[i][0], DIFFICULTY_COLORS[i][1],
                    DIFFICULTY_COLORS[i][2], intensity);
            glow.set("uTime", time);
            c.shaderQuad(glow,
                    centerX[i] - GLOW_QUAD_SIZE / 2, centerY - GLOW_QUAD_SIZE / 2,
                    GLOW_QUAD_SIZE, GLOW_QUAD_SIZE);
        }
        glow.applyAlphaBlend();
        for (int i = 0; i < 4; i++) {
            boolean hovered = i == HOVERED_INDEX;
            c.blit(icons[i], centerX[i] - ICON_SIZE / 2, iconY, ICON_SIZE, ICON_SIZE);
            int labelY = iconY + ICON_SIZE + TEXT_GAP;  // 252
            txtCentered(c, DIFFICULTY_NAMES[i], centerX[i], labelY,
                    hovered ? 0xFFFFFF55 : 0xFFCCCCCC, true);
            txtCentered(c, DIFFICULTY_HEX[i], centerX[i], labelY + 14, 0xFF8899AA, false);
            txtCentered(c, hovered ? "hovered 1.00" : "idle 0.45", centerX[i], labelY + 25,
                    0xFF667788, false);
        }

        // 右侧：一例"gate bypassed"参考光晕，说明真实的 A 版为什么几乎看不见
        int refX = 512;
        int refTop = 182;
        int refCy = refTop + GLOW_QUAD_SIZE / 2;
        prime(c, glow);
        glow.setSampler("IconTexture", c.texture(OPAQUE_MASK));
        glow.set("IconUVMin", UV_ICON_MIN, UV_ICON_MIN);
        glow.set("IconUVMax", UV_ICON_MAX, UV_ICON_MAX);
        glow.set("GlowColor", DIFFICULTY_COLORS[2][0], DIFFICULTY_COLORS[2][1],
                DIFFICULTY_COLORS[2][2], GLOW_HOVERED);
        glow.set("uTime", time);
        c.shaderQuad(glow, refX, refTop, GLOW_QUAD_SIZE, GLOW_QUAD_SIZE);
        glow.applyAlphaBlend();
        c.strokeRect(refX, refTop, GLOW_QUAD_SIZE, GLOW_QUAD_SIZE, 0x44FFFFFF, 0x22FFFFFF);
        c.fill(refX + GLOW_QUAD_SIZE / 2 - 5, refCy, 11, 1, 0xCCFFFFFF);
        c.fill(refX + GLOW_QUAD_SIZE / 2, refCy - 5, 1, 11, 0xCCFFFFFF);
        txt(c, "reference halo", refX, refTop + GLOW_QUAD_SIZE + 4, TEXT_DIM, false);
        txt(c, "(alpha gate bypassed)", refX, refTop + GLOW_QUAD_SIZE + 15, TEXT_DIM, false);

        txt(c, "detail frames: gui_voronoi.png (blood overlay) / gui_glow.png (icon halos, faithful + reference)",
                14, 300, TEXT_DIM, false);
        txt(c, "rendered by the real core shaders + project textures; no Minecraft launch",
                14, 313, TEXT_DIM, false);
    }

    // ══════════════════════════════════════════════════════════════
    //  框架协作：program 切换 / 长文本分段
    // ══════════════════════════════════════════════════════════════

    /**
     * 把 {@code shader} 切为"当前 GL program"，但不产生任何像素。
     *
     * <p>{@code PreviewShader.set()} 和 MC 的 {@code Uniform.set()} 一样，只把值写进
     * <b>当前</b> program：如果两次 {@code set} 之间夹了一次 {@code fill/blit/text}
     * （它们会切到框架内置 program），uniform 就会写到别的 program 上或被忽略，
     * shader 于是拿到 json 默认值 —— 最典型的症状是 uIntensity 默认 0，
     * voronoi 整块全透明，看起来像"什么都没画"。</p>
     *
     * <p>框架没有暴露 bind-program 的 API，所以这里画一个 0 面积的 quad 让
     * {@code drawShader} 走完 {@code glUseProgram}；零面积三角形不会产生任何像素。</p>
     */
    private static void prime(PreviewCanvas c, PreviewShader shader) {
        c.shaderQuad(shader, 0, 0, 0, 0);
    }

    /**
     * 分段绘制一行文字。
     *
     * <p>框架每次 {@code c.text()} 都把整行顶点一次性压进 LWJGL 的
     * {@code MemoryStack}（64KB 固定栈）—— 一行 60+ 字符的长文本会抛
     * {@code OutOfMemoryError: Out of stack space}。按 ≤16 字符（优先在空格处断）
     * 切成小段逐段画，像素结果与整行相同：MC 位图字体没有跨字符字距，
     * 每段起点就是前面各段宽度之和。</p>
     */
    private static void txt(PreviewCanvas c, String s, int x, int y, int argb, boolean shadow) {
        int i = 0;
        int penX = x;
        while (i < s.length()) {
            int end = Math.min(s.length(), i + TEXT_CHUNK);
            if (end < s.length()) {
                int space = s.lastIndexOf(' ', end - 1);
                if (space > i) {
                    end = space + 1;
                }
            }
            String chunk = s.substring(i, end);
            c.text(chunk, penX, y, argb, shadow);
            penX += c.textWidth(chunk);
            i = end;
        }
    }

    private static void txtCentered(PreviewCanvas c, String s, int centerX, int y,
                                    int argb, boolean shadow) {
        txt(c, s, centerX - c.textWidth(s) / 2, y, argb, shadow);
    }

    private static String fmt(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}
