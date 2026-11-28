import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * 坍缩使徒 boss 血条的"实机级"离屏预览。
 *
 * <p>几何 / 颜色 / uniform 全部照抄 {@code CollapsarBossBar}，只把 RenderSystem +
 * BufferBuilder 那一层换成 {@link PreviewCanvas} 的等价调用；三层绘制顺序与实机一致：
 * ① 黑槽 fill → ② 内容 core shader（纯黑底 + 程序化星尘）→ ③ 框架 outline 贴图。</p>
 *
 * <p>同一次运行出两张图：</p>
 * <ul>
 *   <li>{@code collapsar_bar.png}：4 条血条竖排，fill = 1.00 / 0.55 / 0.30 / 0.12，
 *       同一个 time；0.55 以下开始"整条抖动"，图上标出每条算出来的 shake / dx / dy；</li>
 *   <li>{@code collapsar_stars.png}：同一 fill = 0.62 下 t = 0.70 / 1.90 / 3.30 s 三帧，
 *       看星尘流动 / 闪烁（此时 shake = 0，三帧的条形几何完全一样，只差 uTime）。</li>
 * </ul>
 *
 * <p><b>贴图尺寸</b>：资源实际是 190x60（{@code CollapsarBossBar} 注释里旧的 182x32 已过期）。
 * 所以 barH / band / aspect 必须像 {@code inspectMaskOnce()} 那样从图里读：
 * 红通道 &gt; 128 的亮行 → bandMin/bandMax；barW 锁 182、barH 按真实比例再夹到 [8, 64]。
 * 硬编码 182x32 会把条压扁、黑槽和框架错位。</p>
 *
 * <p>只允许改这一个文件；框架文件、脚本、文档一概不动。</p>
 */
public final class SceneCollapsarBar {

    private SceneCollapsarBar() {}

    // ══════════════════════════════════════════════════════════════
    //  实机常量（与 CollapsarBossBar 一一对应）
    // ══════════════════════════════════════════════════════════════

    /** 贴图基准宽度 = 原版 boss 条宽度（横向对齐用），CollapsarBossBar.TEX_W。 */
    private static final int TEX_W = 182;
    /** 绘制高度的夹取范围，CollapsarBossBar.MIN_BAR_H / MAX_BAR_H。 */
    private static final int MIN_BAR_H = 8;
    private static final int MAX_BAR_H = 64;

    /** 星尘密度 / 亮度倍数（CollapsarBossBar.STAR_DENSITY / STAR_STRENGTH）。 */
    private static final float STAR_DENSITY = 1.0F;
    private static final float STAR_STRENGTH = 1.0F;

    /** 低血颤抖参数（CollapsarBossBar.SHAKE_START / SHAKE_FULL / SHAKE_MAX_X / SHAKE_MAX_Y）。 */
    private static final float SHAKE_START = 0.55F;
    private static final float SHAKE_FULL = 0.12F;
    private static final float SHAKE_MAX_X = 2.6F;
    private static final float SHAKE_MAX_Y = 1.5F;

    private static final String MASK_PATH = "textures/gui/collapsar_health_fullmask.png";
    private static final String OUTLINE_PATH = "textures/gui/collapsar_health_outline.png";
    /** en_us：entity.rotten_ruins.collapsar = "Collapsar"（Boss 显示名）。 */
    private static final String BOSS_NAME = "Collapsar";

    /** 黑槽：实机 gfx.fill(..., 0xFF000000)。 */
    private static final int TROUGH_COLOR = 0xFF000000;

    // 画面文字颜色（全部是英文 ASCII：MC 位图字体只吃拉丁字符）
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SUBTITLE_COLOR = 0xFFA8E0FF;
    private static final int INFO_COLOR = 0xFF9FB0C8;
    private static final int FORMULA_COLOR = 0xFFC9B6F0;
    private static final int NO_SHAKE_COLOR = 0xFF6FD09A;
    private static final int MOVE_COLOR = 0xFFFFC978;
    private static final int SEPARATOR_COLOR = 0x1AFFFFFF;
    private static final int FOOTER_RULE_COLOR = 0x24FFFFFF;

    /**
     * 名字（9px 行高）底边离框架上沿的距离：10 - 8 = 2px 视觉间隙。
     * 原版 BossHealthOverlay 把名字画在 bar 上沿 1~2px 处，这里按"看得见的框架上沿"对齐。
     */
    private static final int NAME_GAP = 10;

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        Assets a = loadAssets(c);

        // 先画星尘对比图（纯黑背景）。主图必须最后画：GuiPreview 在 render 返回后
        // 会用场景名把最后一帧自动存成 collapsar_bar.png。
        renderStars(c, a);
        c.save("collapsar_stars");

        renderFillSweep(c, a, time);
    }

    // ══════════════════════════════════════════════════════════════
    //  图 1：fill 扫掠（世界背景）
    // ══════════════════════════════════════════════════════════════

    private static void renderFillSweep(PreviewCanvas c, Assets a, float time) {
        c.backdrop();

        int barX = (c.width - a.barW) / 2;          // 229：和原版 HUD 一样水平居中
        float[] fills = {1.00F, 0.55F, 0.30F, 0.12F};
        int rowTop = 48;
        int stride = 64;

        text(c, "Collapsar boss bar - fill sweep (offline GUI preview)", 12, 8, WHITE, true);
        text(c, "t = " + f3(time) + " s for all four bars  |  shipped 190x60 mask + outline -> "
                + a.barW + "x" + a.barH + " bar (vanilla width)", 12, 19, SUBTITLE_COLOR, true);
        text(c, "black trough = mask rows " + a.bandFirstRow + ".." + a.bandLastRow + " / " + a.texH
                + " (red > 128); shake starts below fill 0.55", 12, 30, SUBTITLE_COLOR, true);

        for (int i = 0; i < fills.length; i++) {
            int quadY = rowTop + i * stride;
            Motion motion = motion(fills[i], time);
            drawRow(c, a, barX, quadY, fills[i], time, motion);
            drawFillSweepLabels(c, a, barX, quadY, fills[i], motion);
            if (i < fills.length - 1) {
                c.fill(12, quadY + 60, c.width - 24, 1, SEPARATOR_COLOR);
            }
        }

        int footerTop = 310;
        c.fill(12, footerTop - 8, c.width - 24, 1, FOOTER_RULE_COLOR);
        text(c, "content: core shader rendertype_gui_collapsar_bar; black base covers the whole bar",
                12, footerTop, INFO_COLOR, false);
        text(c, "uFill clips only the star dust; frame: collapsar_health_outline.png blitted last",
                12, footerTop + 10, INFO_COLOR, false);
        text(c, "black trough: opaque fill over the mask band, drawn before the content shader",
                12, footerTop + 20, INFO_COLOR, false);
        text(c, "shake(fill) = clamp((0.55 - fill) / 0.43, 0, 1);  0 at fill >= 0.55, max at fill <= 0.12",
                12, footerTop + 30, FORMULA_COLOR, false);
        text(c, "jitter(t, seed, speed):  p = t * speed + seed", 12, footerTop + 40, FORMULA_COLOR, false);
        text(c, "j = 0.55*sin(12.9p) + 0.30*sin(27.7p + 1.7) + 0.15*sin(51.3p + 3.1)",
                12, footerTop + 50, FORMULA_COLOR, false);
        text(c, "dx = round(jx * 2.6 * shake),  dy = round(jy * 1.5 * shake); bar shifts, ruler = dx (4px / GUI px)",
                12, footerTop + 60, FORMULA_COLOR, false);
        text(c, "boss name 'Collapsar' (vanilla overlay, centered + shadow) stays fixed while the bar shakes",
                12, footerTop + 70, INFO_COLOR, false);
    }

    // ══════════════════════════════════════════════════════════════
    //  图 2：固定 fill 的星尘对比（纯黑背景）
    // ══════════════════════════════════════════════════════════════

    private static void renderStars(PreviewCanvas c, Assets a) {
        c.clear(0xFF000000);

        int barX = (c.width - a.barW) / 2;
        float fill = 0.62F;
        float[] times = {0.70F, 1.90F, 3.30F};
        int rowTop = 56;
        int stride = 90;

        text(c, "Collapsar boss bar - star dust motion at fixed fill (three uTime values)",
                12, 8, WHITE, true);
        text(c, "fill = 0.62 (>= shake start 0.55): shake = 0, so the bar is identical in all three rows",
                12, 19, SUBTITLE_COLOR, true);
        text(c, "only uTime changes; two procedural star layers drift at different speeds",
                12, 30, SUBTITLE_COLOR, true);

        for (int i = 0; i < times.length; i++) {
            int quadY = rowTop + i * stride;
            float t = times[i];
            drawRow(c, a, barX, quadY, fill, t, motion(fill, t));
            drawStarsLabels(c, a, barX, quadY, t, fill);
            if (i < times.length - 1) {
                c.fill(12, quadY + 60, c.width - 24, 1, SEPARATOR_COLOR);
            }
        }

        int footerTop = 306;
        c.fill(12, footerTop - 8, c.width - 24, 1, FOOTER_RULE_COLOR);
        text(c, "same fill = 0.62 in all three rows: trough, content clip and frame geometry are identical",
                12, footerTop, INFO_COLOR, false);
        text(c, "only uTime differs: stars take hue from hash(cell) and twinkle phase from hash(cell + 8.1)",
                12, footerTop + 10, INFO_COLOR, false);
        text(c, "two grids: scale 9.0 dense small stars, scale 4.5 sparse large stars (see the .fsh)",
                12, footerTop + 20, INFO_COLOR, false);
        text(c, "compare with collapsar_bar.png: low fill shakes the whole bar by dx / dy, name stays fixed",
                12, footerTop + 30, INFO_COLOR, false);
    }

    // ══════════════════════════════════════════════════════════════
    //  单条血条：与 CollapsarBossBar.onBossBarProgress 同样的三层顺序
    // ══════════════════════════════════════════════════════════════

    /**
     * 画一条血条（名字 + 黑槽 + 内容 + 框架）。
     *
     * @param barX 未抖动的左上角 x（= 实机事件给的 x，水平居中）
     * @param quadY 未抖动的左上角 y（= 实机事件给的 y）
     */
    private static void drawRow(PreviewCanvas c, Assets a, int barX, int quadY,
                                float fill, float time, Motion motion) {
        // Boss 名字：原版 BossHealthOverlay 单独画，属于原版 overlay、不参与自定义条的抖动，
        // 所以这里画在不抖的位置 —— 低血时正好能看出"条在动、名字不动"。
        c.textCentered(BOSS_NAME, barX + a.barW / 2, quadY + a.artTop - NAME_GAP, WHITE, true);

        int bx = barX + motion.dx;
        int by = quadY + motion.dy;

        // ① 黑槽：实机 gfx.fill(x1, y1, x2, y2)，画布 fill(x, y, w, h) —— 同一个矩形。
        int troughTop = by + Math.round(a.bandMin * a.barH);
        int troughBottom = by + Math.round(a.bandMax * a.barH);
        c.fill(bx, troughTop, a.barW, troughBottom - troughTop, TROUGH_COLOR);

        // ② 内容：纯黑底 + 彩色星尘（项目 core shader；blend 由 json 的
        //    srcalpha / 1-srcalpha 自动生效，不用自己设混合）。
        a.shader.setSampler("MaskTexture", a.mask);
        a.shader.set("uTime", time);
        a.shader.set("uFill", fill);
        a.shader.set("uAlpha", 1.0F);
        a.shader.set("uStarStrength", STAR_STRENGTH);
        a.shader.set("uStarDensity", STAR_DENSITY);
        a.shader.set("uShake", motion.shake);
        a.shader.set("uBarAspect", a.barAspect);
        a.shader.set("uBandMin", a.bandMin);
        a.shader.set("uBandMax", a.bandMax);
        a.shader.set("uUseGeomBand", 0.0F);
        c.shaderQuad(a.shader, bx, by, a.barW, a.barH);

        // ③ 框架：最后画，压在内容之上（普通 GUI 贴图，不走着色器）。
        c.blit(a.outline, bx, by, a.barW, a.barH);
    }

    // ══════════════════════════════════════════════════════════════
    //  图上标注
    // ══════════════════════════════════════════════════════════════

    private static void drawFillSweepLabels(PreviewCanvas c, Assets a, int barX, int quadY,
                                            float fill, Motion motion) {
        int line1 = quadY + a.artTop + 3;
        int line2 = line1 + 11;
        text(c, "fill = " + f2(fill), 12, line1, WHITE, true);
        text(c, "shake = " + f2(motion.shake) + (motion.shake >= 1.0F ? "  (max)" : ""),
                12, line2, shakeColor(motion.shake), true);

        int rightX = barX + a.barW + 12;
        boolean moving = motion.dx != 0 || motion.dy != 0;
        text(c, "dx = " + signed(motion.dx) + ",  dy = " + signed(motion.dy), rightX, line1,
                moving ? MOVE_COLOR : NO_SHAKE_COLOR, true);
        String detail = motion.shake > 0.0F
                ? "jitter x / y = " + signed2(motion.jitterX) + " / " + signed2(motion.jitterY)
                : "no shake (fill >= 0.55)";
        text(c, detail, rightX, line2, INFO_COLOR, true);
        // 放大 4 倍的 dx 标尺：把 1~3 GUI px 的横向偏移画成 4~12px，静态图里也能直接对比
        drawShakeRuler(c, rightX, line2 + 12, motion.dx);
    }

    /**
     * dx 标尺（注记，不属于实机画面）：中点 = dx 0，相邻刻度 = 1 GUI px，横向放大 4 倍。
     * 抖动时彩色游标离开中点，低血 0.12 那一行会明显比 0.30 偏得更多。
     */
    private static void drawShakeRuler(PreviewCanvas c, int x, int y, int dx) {
        text(c, "dx", x, y, INFO_COLOR, false);
        int rulerX = x + 18;
        int centerX = rulerX + 12;
        c.fill(rulerX, y + 4, 25, 1, 0x40FFFFFF);                    // 基线
        for (int k = -3; k <= 3; k++) {                              // 刻度：-3..+3 GUI px
            boolean zero = k == 0;
            c.fill(centerX + k * 4, zero ? y + 1 : y + 2, 1, zero ? 7 : 5,
                    zero ? 0x90FFFFFF : 0x40FFFFFF);
        }
        int markerX = centerX + dx * 4;
        c.fill(markerX, y, 1, 9, dx == 0 ? NO_SHAKE_COLOR : MOVE_COLOR);   // 游标
    }

    private static void drawStarsLabels(PreviewCanvas c, Assets a, int barX, int quadY,
                                        float time, float fill) {
        int line1 = quadY + a.artTop + 3;
        int line2 = line1 + 11;
        text(c, "t = " + f2(time) + " s", 12, line1, WHITE, true);
        text(c, "fill = " + f2(fill), 12, line2, INFO_COLOR, true);

        int rightX = barX + a.barW + 12;
        text(c, "uShake = 0.00", rightX, line1, NO_SHAKE_COLOR, true);
        text(c, "uStarDensity = 1.00", rightX, line2, INFO_COLOR, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  抖动：完全照抄 CollapsarBossBar.shakeAmount / jitter
    // ══════════════════════════════════════════════════════════════

    /** 单条血条这一帧的抖动状态（shake > 0 时才求 jitter / dx / dy，与实机一致）。 */
    private static final class Motion {
        float shake;
        float jitterX;
        float jitterY;
        int dx;
        int dy;
    }

    private static Motion motion(float fill, float time) {
        Motion motion = new Motion();
        motion.shake = shakeAmount(fill);
        if (motion.shake > 0.0F) {
            motion.jitterX = jitter(time, 0.0F, 1.00F);
            motion.jitterY = jitter(time, 5.0F, 1.31F);
            motion.dx = Math.round(motion.jitterX * SHAKE_MAX_X * motion.shake);
            motion.dy = Math.round(motion.jitterY * SHAKE_MAX_Y * motion.shake);
        }
        return motion;
    }

    /** 颤抖强度：0 = 不抖，1 = 抖到最大（两段线性，clip 到 [0, 1]）。 */
    private static float shakeAmount(float fill) {
        if (fill >= SHAKE_START) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (SHAKE_START - fill) / (SHAKE_START - SHAKE_FULL)));
    }

    /** 三个不可通约频率的正弦叠加，约 [-1, 1]（不做 Random：那样每帧独立跳变会变成"闪"）。 */
    private static float jitter(float time, float seed, float speed) {
        float p = time * speed + seed;
        return (float) (Math.sin(p * 12.9) * 0.55
                + Math.sin(p * 27.7 + 1.7) * 0.30
                + Math.sin(p * 51.3 + 3.1) * 0.15);
    }

    // ══════════════════════════════════════════════════════════════
    //  资源 / 尺寸：照抄 CollapsarBossBar.inspectMaskOnce + applyTextureSize
    // ══════════════════════════════════════════════════════════════

    /** 从贴图读出来的一整套几何（贴图真实尺寸决定，不硬编码）。 */
    private static final class Assets {
        PreviewTexture mask;
        PreviewTexture outline;
        PreviewShader shader;
        int texW;
        int texH;
        int barW;
        int barH;
        float barAspect;
        float bandMin;
        float bandMax;
        int bandFirstRow;
        int bandLastRow;
        /** 框架贴图不透明区域顶行在 barH 高 quad 内的 y 偏移（名字定位用）。 */
        int artTop;
    }

    private static Assets loadAssets(PreviewCanvas c) {
        Assets a = new Assets();

        // ① mask 体检：红通道 > 128 的亮行范围（与 inspectMaskOnce 完全同一套约定）
        BufferedImage maskImage = c.image(MASK_PATH);
        a.texW = maskImage.getWidth();
        a.texH = maskImage.getHeight();
        int minRow = a.texH;
        int maxRow = -1;
        for (int y = 0; y < a.texH; y++) {
            for (int x = 0; x < a.texW; x++) {
                if (((maskImage.getRGB(x, y) >> 16) & 0xFF) > 128) {
                    minRow = Math.min(minRow, y);
                    maxRow = Math.max(maxRow, y);
                    break;
                }
            }
        }
        if (maxRow < 0) {
            // 实机里这对应 maskOk = false 的几何兜底带；预览里贴图存在就不会发生，留个兜底。
            minRow = 0;
            maxRow = a.texH - 1;
        }
        a.bandFirstRow = minRow;
        a.bandLastRow = maxRow;
        a.bandMin = minRow / (float) a.texH;
        a.bandMax = (maxRow + 1) / (float) a.texH;

        // ② 绘制尺寸：宽度锁 TEX_W，高度按贴图真实比例再夹到 [8, 64]
        a.barW = TEX_W;
        int rawHeight = Math.round(TEX_W * a.texH / (float) a.texW);
        a.barH = Math.max(MIN_BAR_H, Math.min(MAX_BAR_H, rawHeight));
        a.barAspect = a.texW / (float) a.texH;

        // ③ 框架贴图不透明区域顶行：190x60 的图在 57px quad 里顶部约有 16px 空白，
        //    名字若按 quad 上沿 -10 放会飘在上一行底部，所以按"看得见的框架上沿"对齐。
        BufferedImage outlineImage = c.image(OUTLINE_PATH);
        int artTopRow = outlineImage.getHeight();
        for (int y = 0; y < outlineImage.getHeight() && artTopRow == outlineImage.getHeight(); y++) {
            for (int x = 0; x < outlineImage.getWidth(); x++) {
                if (((outlineImage.getRGB(x, y) >>> 24) & 0xFF) > 0) {
                    artTopRow = y;
                    break;
                }
            }
        }
        if (artTopRow == outlineImage.getHeight()) {
            artTopRow = 0;
        }
        a.artTop = Math.round(artTopRow * (a.barH / (float) outlineImage.getHeight()));

        a.mask = c.texture(MASK_PATH);
        a.outline = c.texture(OUTLINE_PATH);
        a.shader = c.shader("rendertype_gui_collapsar_bar");
        return a;
    }

    // ══════════════════════════════════════════════════════════════
    //  小工具
    // ══════════════════════════════════════════════════════════════

    /**
     * 画一行 MC 位图文字。
     *
     * <p>框架有两个坑，在这里一起绕开（不改框架文件）：</p>
     * <ol>
     *   <li>{@code PreviewCanvas.drawText} 把空格也当 ascii.png 里的空格字形画，
     *       advance = 1px；原版空格是 4px（space.json）。这里按"单词 + 4px 空格"自己排版，
     *       画面里字距才和实机一致。</li>
     *   <li>{@code drawText} 会把整串文字的顶点一次性压进同一个 64KB 的 LWJGL MemoryStack
     *       帧，一行太长会抛 {@code OutOfMemoryError: Out of stack space}。按单词画，每个
     *       调用都很短，天然避开。</li>
     * </ol>
     */
    private static void text(PreviewCanvas c, String value, int x, int y, int color, boolean shadow) {
        int penX = x;
        int start = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i < value.length() && value.charAt(i) != ' ') {
                continue;
            }
            if (i > start) {
                String word = value.substring(start, i);
                c.text(word, penX, y, color, shadow);
                penX += wordWidth(c, word);
            }
            if (i < value.length()) {
                penX += SPACE_ADVANCE;
            }
            start = i + 1;
        }
    }

    /** 原版空格前进宽度：space.json 的 {@code " ": 4}。 */
    private static final int SPACE_ADVANCE = 4;

    /**
     * 一个不含空格的词的绘制宽度（逐字形复刻 {@code drawText} 的 advance：
     * 正常字形用 glyph.advance()，未覆盖码点 7px 占位；字体不可用时退化路径是 4px/字符）。
     */
    private static int wordWidth(PreviewCanvas c, String word) {
        PreviewFont font = c.font();
        if (!font.available()) {
            return word.length() * 4;
        }
        int width = 0;
        for (int i = 0; i < word.length(); ) {
            int cp = word.codePointAt(i);
            i += Character.charCount(cp);
            PreviewFont.Glyph glyph = font.glyph(cp);
            width += glyph != null ? glyph.advance() : 7;
        }
        return width;
    }

    private static String f2(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String f3(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** 带符号整数：+2 / -1 / +0。 */
    private static String signed(int value) {
        return (value >= 0 ? "+" : "") + value;
    }

    /** 带符号两位小数：+0.59 / -0.09。 */
    private static String signed2(float value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    /** 抖动强度色：不抖 = 绿，越接近最大越红（橙 → 红）。 */
    private static int shakeColor(float shake) {
        if (shake <= 0.0F) {
            return NO_SHAKE_COLOR;
        }
        int red = 255;
        int green = Math.round(200 - 130 * shake);
        int blue = Math.round(90 - 60 * shake);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
