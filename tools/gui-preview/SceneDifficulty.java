import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.List;

/**
 * 场景：难度选择界面（{@code DifficultySelectScreen}）的离屏复现。
 *
 * <p>这一帧包含了本工具链里最难的三件事，都是<b>照源码逐字移植</b>而不是近似：</p>
 * <ol>
 *   <li><b>程序化「印记」网格</b>：{@link DifficultySigilMesh} 的 RadialProfile
 *       （Circle / Polygon / Spikes / Steps）与 {@code bakeRing} 的整圈环带三角形化。
 *       逐顶点参数全部编进 UV0：{@code x} = 径向坐标（0 内缘 / 1 外缘），
 *       {@code y} = 图案相位 = 倍数 × (θ + 自转 + 环偏移)。必须和
 *       {@code rendertype_difficulty_sigil.fsh} 的约定一致，否则缺口/扇区画不出来。
 *       网格用 {@code c.shaderMesh(...)} 以 GL_TRIANGLES 提交（quads → 两个三角形）。</li>
 *   <li><b>gui_glow 柔光</b>：quad 以图标中心为锚点、边长 = GLOW_QUAD_SIZE × scale，
 *       IconUVMin/Max = 0.147/0.853（图标 48 在 68 的 quad 里），GlowColor = 难度主色 + 强度。</li>
 *   <li><b>布局</b>：cardW / cardVisualH / spacing / y / 标题 / 提示行全部按源码公式，
 *       640×400 逻辑画布 = 源码里的 {@code this.width/height}。</li>
 * </ol>
 *
 * <p>产物：</p>
 * <ul>
 *   <li>{@code difficulty_screen.png} —— 主界面（第 2 张卡「普通」为悬浮态，其余常态）</li>
 *   <li>{@code difficulty_sigils.png} —— 四枚印记的 2×2 特写（全部悬浮态 + 满强度柔光，
 *       只为看清四档图案差异，不是游戏里真实的一屏）</li>
 *   <li>{@code difficulty.png} —— 由 GuiPreview 在 render 返回后自动保存（最后一帧 = 主界面）</li>
 * </ul>
 */
public final class SceneDifficulty {

    private SceneDifficulty() {}

    // ══════════════════════════════════════════════════════════════
    //  布局常量（逐字照抄 DifficultySelectScreen）
    // ══════════════════════════════════════════════════════════════

    private static final int ENTRY_COUNT = 4;
    private static final int ICON_SIZE = 48;
    private static final int GLOW_RADIUS = 10;
    private static final int CARD_PADDING = 4;
    private static final int TEXT_GAP = 10;
    private static final int TITLE_BOTTOM_GAP = 12;
    private static final int HINT_BOTTOM_GAP = 28;

    private static final int GLOW_QUAD_SIZE = ICON_SIZE + GLOW_RADIUS * 2;              // 68
    /** 图标在发光 quad 内的 UV 边界 */
    private static final float UV_ICON_MIN = (float) GLOW_RADIUS / GLOW_QUAD_SIZE;      // 0.147
    private static final float UV_ICON_MAX = (float) (GLOW_RADIUS + ICON_SIZE) / GLOW_QUAD_SIZE; // 0.853

    /** 印记的基准半尺寸（像素）：所有环半径都以它为 1.0 的基准。 */
    private static final float SIGIL_UNIT = 30.0F;

    /** 常态闲置缩放：悬浮 1.0、其余 0.94（源码 selectScaleFor 在无选中动画时的值）。 */
    private static final float IDLE_HOVERED_SCALE = 1.0F;
    private static final float IDLE_REST_SCALE = 0.94F;

    /** 悬浮的卡片下标：源码里由鼠标命中检测得到，这里固定成第 2 张（普通）。 */
    private static final int HOVERED_INDEX = 1;

    private static final float TAU = (float) (Math.PI * 2.0);

    /** 诊断日志只打一次（render 里主界面会画两遍：先存盘、最后再重画给自动保存）。 */
    private static boolean diagnosticsLogged;

    // ══════════════════════════════════════════════════════════════
    //  文案（en_us.json）
    // ══════════════════════════════════════════════════════════════

    private static final String TITLE = "Select Difficulty";
    private static final String HINT = "Choose the world difficulty (can be changed later via commands)";

    // ══════════════════════════════════════════════════════════════
    //  四个难度（照抄 DifficultySelectScreen.ENTRIES）
    //  glowRGB = 图标主色；rings 顺序 = uProfile 0..3
    // ══════════════════════════════════════════════════════════════

    private static final List<Entry> ENTRIES = List.of(
            new Entry("Easy",
                    "textures/gui/difficulty_easy.png",
                    0.153F, 0.224F, 0.149F,   // #273926 深森绿
                    // 简单：净同心圆，慢呼吸，无角度图案（倍数 1）
                    List.of(
                            new Ring(1.00F, 3.0F, 0.95F, 0.30F, new Circle(72), 0.070F, 3.0F, 0.60F, 1.0F, 0.0F),
                            new Ring(0.80F, 2.4F, 0.52F, -0.20F, new Circle(64), 0.050F, 2.0F, 0.45F, 1.0F, 0.0F),
                            new Ring(0.61F, 2.2F, 0.32F, 0.14F, new Circle(56), 0.035F, 4.0F, 0.90F, 1.0F, 0.0F)
                    )),
            new Entry("Normal",
                    "textures/gui/difficulty_normal.png",
                    0.122F, 0.145F, 0.502F,   // #1F2580 蓝紫
                    // 普通：六边形 + 内接三角，反向自转，3 段缺口
                    List.of(
                            new Ring(1.00F, 2.8F, 0.95F, 0.22F, new Polygon(6, 0.95F), 0.012F, 6.0F, 0.35F, 3.0F, 0.0F),
                            new Ring(0.84F, 2.3F, 0.55F, -0.32F, new Polygon(6, 0.55F), 0.010F, 6.0F, 0.30F, 3.0F, 2.09F),
                            new Ring(0.65F, 2.1F, 0.34F, 0.55F, new Polygon(3, 0.90F), 0.016F, 3.0F, 0.50F, 3.0F, 4.19F)
                    )),
            new Entry("Hard",
                    "textures/gui/difficulty_hard.png",
                    0.992F, 0.059F, 0.349F,   // #FD0F59 玫红
                    // 困难：12 根不等长尖刺 + 正反叠转，5 段撕裂
                    List.of(
                            new Ring(1.00F, 2.6F, 0.98F, 0.34F, new Spikes(12, 0.40F, 0.0F, 6), 0.055F, 6.0F, 1.90F, 5.0F, 0.0F),
                            new Ring(0.82F, 2.2F, 0.62F, -0.46F, new Spikes(8, 0.30F, 2.7F, 6), 0.045F, 5.0F, 1.40F, 5.0F, 1.26F),
                            new Ring(0.60F, 2.0F, 0.38F, 0.80F, new Polygon(5, 0.75F), 0.030F, 5.0F, 1.10F, 5.0F, 2.51F)
                    )),
            new Entry("Incomprehensible",
                    "textures/gui/difficulty_incomprehensible.png",
                    0.796F, 0.816F, 1.000F,   // #CBD0FF 苍白色
                    // 无法理解：半径阶梯跳变 + 三环反向，7 扇区独立闪烁
                    List.of(
                            new Ring(1.00F, 2.5F, 0.95F, 0.62F, new Steps(9, 0.14F, 1.3F), 0.030F, 6.0F, 2.40F, 7.0F, 0.0F),
                            new Ring(0.80F, 2.2F, 0.70F, -0.78F, new Steps(7, 0.18F, 4.1F), 0.035F, 5.0F, 3.10F, 7.0F, 0.90F),
                            new Ring(0.58F, 2.0F, 0.45F, 1.05F, new Steps(5, 0.22F, 7.7F), 0.045F, 4.0F, 1.70F, 7.0F, 1.79F)
                    ))
    );

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        logDiagnosticsOnce(c);

        drawMainScreen(c, time);
        c.save("difficulty_screen");

        drawSigilSheet(c, time);
        c.save("difficulty_sigils");

        // GuiPreview 在 render 返回后还会自动存一张 <scene>.png；
        // 重画主界面让那张也保持主界面（而不是特写sheet）。
        drawMainScreen(c, time);
    }

    /**
     * 启动时把布局与总顶点数打到 stdout（只打一次），方便和源码常量对照复核。
     */
    private static void logDiagnosticsOnce(PreviewCanvas c) {
        if (diagnosticsLogged) {
            return;
        }
        diagnosticsLogged = true;

        int width = c.width;
        int height = c.height;
        int cardW = ICON_SIZE + CARD_PADDING * 2;
        int cardVisualH = ICON_SIZE + TEXT_GAP + c.fontHeight();
        int spacing = calculateSpacing(width);
        int totalWidth = calculateTotalWidth(width);
        int startX = (width - totalWidth) / 2;
        int cardsY = height / 2 - cardVisualH / 2;
        int titleY = cardsY - TITLE_BOTTOM_GAP - c.fontHeight();
        int hintY = titleY - HINT_BOTTOM_GAP - c.fontHeight();

        StringBuilder cardXs = new StringBuilder();
        for (int i = 0; i < ENTRY_COUNT; i++) {
            if (i > 0) {
                cardXs.append(',');
            }
            cardXs.append(startX + i * (cardW + spacing));
        }

        System.out.println("[scene-difficulty] layout: canvas=" + width + "x" + height
                + " cardW=" + cardW + " cardVisualH=" + cardVisualH
                + " spacing=" + spacing + " startX=" + startX
                + " cardXs=[" + cardXs + "]" + " cardsY=" + cardsY
                + " titleY=" + titleY + " hintY=" + hintY);

        StringBuilder verts = new StringBuilder("[scene-difficulty] sigil mesh (TRIANGLES): ");
        for (int i = 0; i < ENTRY_COUNT; i++) {
            int count = 0;
            for (Ring ring : ENTRIES.get(i).rings) {
                count += clampInt(ring.profile().segments(), 6, 256) * 6;
            }
            verts.append("profile").append(i).append('=').append(count).append("v/")
                    .append(count * 5).append("f");
            if (i + 1 < ENTRY_COUNT) {
                verts.append(", ");
            }
        }
        System.out.println(verts);
    }

    // ══════════════════════════════════════════════════════════════
    //  主界面
    // ══════════════════════════════════════════════════════════════

    private static void drawMainScreen(PreviewCanvas c, float time) {
        int width = c.width;
        int height = c.height;
        int centerX = width / 2;
        int centerY = height / 2;

        // ── 与源码完全一致的布局公式 ──
        int cardVisualH = ICON_SIZE + TEXT_GAP + c.fontHeight();
        int totalWidth = calculateTotalWidth(width);
        int startX = (width - totalWidth) / 2;
        int cardsY = centerY - cardVisualH / 2;
        int titleY = cardsY - TITLE_BOTTOM_GAP - c.fontHeight();
        int hintY = titleY - HINT_BOTTOM_GAP - c.fontHeight();
        int cardW = ICON_SIZE + CARD_PADDING * 2;
        int spacing = calculateSpacing(width);

        // ── 背景 ──
        // 实机：该界面在世界内弹出（level != null）→ renderTransparentBackground，
        // 即铺一层 0xC0101010 → 0xD0101010 的纵向渐变。这里用 backdrop() 当"世界"，
        // 再叠同一层渐变，和实机的明暗关系一致。
        c.clear(0xFF000000);
        c.backdrop();
        c.fillGradientV(0, 0, width, height, 0xC0101010, 0xD0101010);

        PreviewShader glow = c.shader("rendertype_gui_glow");
        PreviewShader sigil = c.shader("rendertype_difficulty_sigil");

        // ── 标题 / 提示行（源码 drawCenteredString 默认带阴影）──
        drawCenteredText(c, TITLE, centerX, titleY, 0xFFFFFFFF);
        drawCenteredText(c, HINT, centerX, hintY, 0xFFAAAAAA);

        // ── 四张卡片 ──
        int cursorX = startX;
        for (int i = 0; i < ENTRY_COUNT; i++) {
            Entry entry = ENTRIES.get(i);
            int cardX = cursorX;
            int cardY = cardsY;
            int cardBottom = cardY + cardVisualH;
            cursorX += cardW + spacing;

            boolean hovered = i == HOVERED_INDEX;
            float scale = hovered ? IDLE_HOVERED_SCALE : IDLE_REST_SCALE;

            // 卡片高亮背景：0x44FFFFFF 外层 + 0x22FFFFFF 内层
            if (hovered) {
                c.fill(cardX - 1, cardY - 1, cardW + 2, cardVisualH + 2, 0x44FFFFFF);
                c.fill(cardX, cardY, cardW, cardVisualH, 0x22FFFFFF);
            }

            int iconX = cardX + (cardW - ICON_SIZE) / 2;
            int iconY = cardY + CARD_PADDING;

            float cardCenterX = cardX + cardW / 2.0F;
            float cardCenterY = iconY + ICON_SIZE / 2.0F;

            // 1) 柔光（旧径向发光，现在作印记底衬）
            float glowIntensity = hovered ? 1.0F : 0.45F;
            drawGlow(c, glow, entry, iconX, iconY, time, glowIntensity, scale);

            // 2) 印记（程序化环带）
            drawSigil(c, sigil, entry, i, cardCenterX, cardCenterY, time, hovered, scale);

            // 3) 图标（16×16 → 48×48 最近邻；源码里图标不跟随 scale）
            c.blit(c.texture(entry.texture), iconX, iconY, ICON_SIZE, ICON_SIZE);

            // 4) 名称
            int textY = iconY + ICON_SIZE + TEXT_GAP;
            int labelColor = hovered ? 0xFFFF55 : 0xCCCCCC;
            drawCenteredText(c, entry.label, cardX + cardW / 2, textY, 0xFF000000 | labelColor);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  柔光：rendertype_gui_glow
    // ══════════════════════════════════════════════════════════════

    /**
     * 径向柔光。与源码 {@code renderGlow} 完全对齐：
     * quad 边长 = {@link #GLOW_QUAD_SIZE} × scale，中心 = 图标中心
     *（源码用 pose.translate(iconCenter) + scale + translate(-size/2) 实现；
     * 若直接从 iconX 起画，quad 中心会偏出 GLOW_RADIUS 像素）。
     *
     * <p>用 {@code shaderMesh} 而不是 {@code shaderQuad} 是为了保留 scale=0.94 时的
     * 小数坐标（quad 顶点在实机里就是浮点）。</p>
     */
    private static void drawGlow(PreviewCanvas c, PreviewShader glow, Entry entry,
                                 int iconX, int iconY, float time,
                                 float intensity, float scale) {
        if (intensity <= 0.01F) {
            return;
        }
        // 框架的 PreviewShader.set() 直接调 glUniform*、作用于"当前 program"，
        // 所以上传前必须把目标的 program 绑成当前 program（场景里两个 shader 交替使用）。
        bindProgram(glow);
        glow.setSampler("IconTexture", c.texture(entry.texture));
        glow.set("IconUVMin", UV_ICON_MIN, UV_ICON_MIN);
        glow.set("IconUVMax", UV_ICON_MAX, UV_ICON_MAX);
        glow.set("GlowColor", entry.glowR, entry.glowG, entry.glowB, intensity);
        glow.set("uTime", time);

        float size = GLOW_QUAD_SIZE * scale;
        float iconCenterX = iconX + ICON_SIZE * 0.5F;
        float iconCenterY = iconY + ICON_SIZE * 0.5F;
        float x0 = iconCenterX - size * 0.5F;
        float y0 = iconCenterY - size * 0.5F;

        // 两个三角形；顶点布局 = json attributes 顺序 Position(3) + UV0(2)
        float[] data = {
                x0, y0, 0F, 0F, 0F,
                x0, y0 + size, 0F, 0F, 1F,
                x0 + size, y0 + size, 0F, 1F, 1F,
                x0, y0, 0F, 0F, 0F,
                x0 + size, y0 + size, 0F, 1F, 1F,
                x0 + size, y0, 0F, 1F, 0F
        };
        c.shaderMesh(glow, data, data.length, 6, GL11.GL_TRIANGLES);
        // 源码 renderGlow 结尾调 RenderSystem.defaultBlendFunc()；
        // 框架的 fill / blit / text 不会自己重置混合状态，这里必须还原，
        // 否则后续的图标、文字都会被 json 的加法混合（src_alpha / one）画。
        glow.applyAlphaBlend();
    }

    // ══════════════════════════════════════════════════════════════
    //  印记：rendertype_difficulty_sigil
    // ══════════════════════════════════════════════════════════════

    /**
     * 绘制一枚印记（源码 {@code renderSigil}）。
     * <p>每枚一次 draw call：uniform 除 uProfile/uHover 外还有该 difficulty 对应的
     * {@code uQuadNColor}。混合由 json 决定（add / srcalpha / one），框架自动应用。</p>
     */
    private static void drawSigil(PreviewCanvas c, PreviewShader sigil, Entry entry,
                                  int profileIndex, float centerX, float centerY,
                                  float time, boolean hovered, float scale) {
        float hover = hovered ? 1.0F : 0.0F;

        bindProgram(sigil);
        sigil.set("uTime", time);
        sigil.set("uHover", hover);
        sigil.set("uAlpha", 1.0F);
        sigil.set("uProfile", (float) profileIndex);
        // 源码：setUniformSafe(shader, "uQuad" + profileIndex + "Color", glowR, glowG, glowB, 1)
        sigil.set("uQuad" + profileIndex + "Color", entry.glowR, entry.glowG, entry.glowB, 1.0F);

        float radiusUnit = SIGIL_UNIT * scale;
        Mesh mesh = new Mesh();
        for (Ring ring : entry.rings) {
            bakeRing(mesh, centerX, centerY, radiusUnit, ring, time);
        }
        c.shaderMesh(sigil, mesh.data, mesh.size, mesh.size / 5, GL11.GL_TRIANGLES);
        // 源码 renderSigil 结尾也调 RenderSystem.defaultBlendFunc()
        sigil.applyAlphaBlend();
    }

    /**
     * 把某个 shader 的 program 绑成当前 program。
     *
     * <p>框架 API 缺口：{@code PreviewShader.set(...)} 直接调 {@code glUniform*}，
     * 上传目标是"当前绑定的 program"；而 {@code PreviewCanvas.shader(name)} 命中缓存时
     * 不会重新 {@code glUseProgram}。本场景两个 shader 交替使用，如果不在上传前绑定，
     * uniform 会全部落到另一个 program 上（表现为柔光用 json 默认金黄色、
     * 四枚印记全用 uProfile=0 的图案与颜色）。实机里 {@code ShaderInstance.apply()}
     * 会自己 use program，所以这只是离屏工具的差异，不是画面逻辑差异。</p>
     */
    private static void bindProgram(PreviewShader shader) {
        GL20.glUseProgram(shader.program);
    }

    // ══════════════════════════════════════════════════════════════
    //  2×2 印记特写
    // ══════════════════════════════════════════════════════════════

    /**
     * 四枚印记的 2×2 特写：放大到 2.2×，全部按悬浮态（uHover=1、glow=1.0）绘制，
     * 方便对比四档的几何/图案差异。这是诊断图，不是游戏里真实存在的一屏。
     */
    private static void drawSigilSheet(PreviewCanvas c, float time) {
        c.clear(0xFF000000);
        c.backdrop();
        c.fillGradientV(0, 0, c.width, c.height, 0xC0101010, 0xD0101010);
        drawCenteredText(c, String.format(
                        "Difficulty sigils - 2.2x close-up (all hovered, glow 1.0, t=%.2fs)", time),
                c.width / 2, 6, 0xFFFFFFFF);

        PreviewShader glow = c.shader("rendertype_gui_glow");
        PreviewShader sigil = c.shader("rendertype_difficulty_sigil");

        float scale = 2.2F;
        int[] cellX = {160, 480, 160, 480};
        int[] cellY = {108, 108, 292, 292};
        String[] spec = {
                "3 rings / circles",
                "hexagon + triangle, 3 gaps",
                "12 spikes + polygon, 5 tears",
                "stepped radii, 7 sectors"
        };

        // 第一遍：柔光 + 印记 + 图标（叠加类，必须都画完）
        for (int i = 0; i < ENTRY_COUNT; i++) {
            Entry entry = ENTRIES.get(i);
            int cx = cellX[i];
            int cy = cellY[i];
            int iconX = cx - ICON_SIZE / 2;
            int iconY = cy - ICON_SIZE / 2;

            drawGlow(c, glow, entry, iconX, iconY, time, 1.0F, scale);
            drawSigil(c, sigil, entry, i, cx, cy, time, true, scale);
            c.blit(c.texture(entry.texture), iconX, iconY, ICON_SIZE, ICON_SIZE);
        }

        // 第二遍：文字（放在最后，避免被相邻格子的加法柔光压亮）
        for (int i = 0; i < ENTRY_COUNT; i++) {
            Entry entry = ENTRIES.get(i);
            int cx = cellX[i];
            int labelY = cellY[i] + Math.round(SIGIL_UNIT * scale) + 10;
            drawCenteredText(c, i + "  " + entry.label, cx, labelY, 0xFFFFFF55);
            drawCenteredText(c, spec[i], cx, labelY + 11, 0xFFAAAAAA);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  文字辅助
    // ══════════════════════════════════════════════════════════════

    /**
     * 居中绘制一行文字（等价 {@code c.textCentered(..., shadow=true)}），但按词边界
     * 切成 ≤16 字符的小段逐段绘制。
     *
     * <p>原因：框架每次 {@code c.text()} 都用 LWJGL 的 MemoryStack 上传顶点，
     * 而 MemoryStack 是 64KB 固定栈 —— 一行 66 字符的提示文字（约 100KB 顶点数据）
     * 会抛 {@code OutOfMemoryError: Out of stack space}。切成小段后逐段上传，
     * 像素结果与"一次性画完整行"完全相同（MC 位图字体没有跨字符字距）。</p>
     */
    private static void drawCenteredText(PreviewCanvas c, String text, int centerX,
                                         int y, int argb) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int penX = centerX - c.textWidth(text) / 2;
        int i = 0;
        int chunkLimit = 16;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + chunkLimit);
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end - 1);
                if (space > i) {
                    end = space + 1;   // 空格归前一段，词不被拆开
                }
            }
            String chunk = text.substring(i, end);
            c.text(chunk, penX, y, argb, true);
            penX += c.textWidth(chunk);
            i = end;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  布局计算（照抄 DifficultySelectScreen）
    // ══════════════════════════════════════════════════════════════

    private static int calculateSpacing(int screenWidth) {
        int cardW = ICON_SIZE + CARD_PADDING * 2;
        int available = screenWidth - cardW * ENTRY_COUNT;
        if (ENTRY_COUNT <= 1) {
            return available / 2;
        }
        return Math.max(16, available / (ENTRY_COUNT + 1));
    }

    private static int calculateTotalWidth(int screenWidth) {
        int cardW = ICON_SIZE + CARD_PADDING * 2;
        return cardW * ENTRY_COUNT + calculateSpacing(screenWidth) * (ENTRY_COUNT - 1);
    }

    // ══════════════════════════════════════════════════════════════
    //  DifficultySigilMesh 移植：RadialProfile
    // ══════════════════════════════════════════════════════════════

    /** 半径随角度变化的线型（同 DifficultySigilMesh.RadialProfile）。 */
    private interface RadialProfile {
        float at(float angleRad);

        int segments();
    }

    /** 真圆，用 {@code sides} 段直线逼近。 */
    private static final class Circle implements RadialProfile {
        private final int sides;

        Circle(int sides) {
            this.sides = Math.max(3, sides);
        }

        @Override
        public float at(float angleRad) {
            return 1.0F;
        }

        @Override
        public int segments() {
            return sides;
        }
    }

    /** 正多边形：顶点朝外、边中点朝内。 */
    private static final class Polygon implements RadialProfile {
        private final int sides;
        private final float sharpness;
        private final float half;

        Polygon(int sides, float sharpness) {
            this.sides = Math.max(3, sides);
            this.sharpness = clamp(sharpness, 0.0F, 1.0F);
            this.half = TAU / (2.0F * this.sides);
        }

        @Override
        public float at(float angleRad) {
            float folded = Math.abs(wrapPi(angleRad)) % (TAU / sides);
            float distanceToEdgeMiddle = Math.abs(folded - half);
            float inward = clamp(distanceToEdgeMiddle / half, 0.0F, 1.0F);
            return 1.0F - sharpness * inward;
        }

        @Override
        public int segments() {
            return sides * 8;
        }
    }

    /** 不等长尖刺：长度按稳定哈希在 1 与 1-depth 之间取值。 */
    private static final class Spikes implements RadialProfile {
        private final int tips;
        private final float depth;
        private final float seed;
        private final int perTip;

        Spikes(int tips, float depth, float seed, int perTip) {
            this.tips = Math.max(3, tips);
            this.depth = clamp(depth, 0.0F, 1.0F);
            this.seed = seed;
            this.perTip = Math.max(2, perTip);
        }

        @Override
        public float at(float angleRad) {
            float u = wrapPi(angleRad) / TAU + 0.5F;          // 0..1
            float scaled = u * tips;
            int index = (int) Math.floor(scaled);
            float frac = scaled - index;

            // 扇区内从谷底升到尖顶再落回：|cos| 的 0.55 次幂让尖顶更尖
            float lobe = (float) Math.pow(Math.abs(Math.cos((frac - 0.5F) * Math.PI)), 0.55);
            float length = 1.0F - depth * (1.0F - hash11(index * 1.37F + seed));
            return lerp(lobe, 1.0F - depth, length);
        }

        @Override
        public int segments() {
            return tips * perTip;
        }
    }

    /** 多档跳变：半径按扇区阶梯式变化，扇区内恒定（刻意的<b>不连续</b>）。 */
    private static final class Steps implements RadialProfile {
        private final int steps;
        private final float variance;
        private final float seed;
        private final float base;

        Steps(int steps, float variance, float seed) {
            this.steps = Math.max(2, steps);
            this.variance = clamp(variance, 0.0F, 1.0F);
            this.seed = seed;
            this.base = 1.0F - this.variance;
        }

        @Override
        public float at(float angleRad) {
            float u = wrapPi(angleRad) / TAU + 0.5F;
            int index = (int) Math.floor(u * steps);
            return base + hash11(index * 3.11F + seed) * 2.0F * variance;
        }

        @Override
        public int segments() {
            return steps * 4;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  DifficultySigilMesh 移植：一圈光环 + 烘焙
    // ══════════════════════════════════════════════════════════════

    /** 一圈光环的描述（字段含义同 DifficultySigilMesh.Ring）。 */
    private record Ring(float baseRadius, float strokeWidth, float weight,
                        float spinSpeed, RadialProfile profile,
                        float wobbleAmplitude, float wobbleFrequency, float wobbleSpeed,
                        float patternMultiplicity, float phaseOffset) {
    }

    /**
     * 把一圈光环烘进 mesh（移植 {@code DifficultySigilMesh.bakeRing}）。
     *
     * <p>顶点只有 Position + UV0：</p>
     * <ul>
     *   <li>{@code UV0.x} —— 径向坐标：0 = 环带内缘，1 = 环带外缘（fsh 里 0.5 = 中线）</li>
     *   <li>{@code UV0.y} —— 图案相位 = 倍数 × (θ + 自转 + 该环偏移)</li>
     * </ul>
     *
     * <p>每个角度段只发一个四边形（内缘两点 → 外缘两点），这里按 TRIANGLES 展开成
     * 两个三角形；顶点顺序与源码一致：(i 内) → (i 外) → (i+1 外) → (i+1 内)。</p>
     */
    private static int bakeRing(Mesh mesh, float centerX, float centerY,
                                float radiusUnit, Ring ring, float time) {
        int steps = clampInt(ring.profile().segments(), 6, 256);

        float spin = ring.spinSpeed() * time;
        float drift = ring.wobbleSpeed() * time;
        // 下限抬到 1.0：亚像素宽的细带在光栅化时本身就会被丢样本
        float halfWidth = Math.max(ring.strokeWidth() * 0.5F, 1.0F);
        float multiplicity = Math.max(ring.patternMultiplicity(), 1.0F);

        // 中线上的采样点、径向单位向量、相位
        float[] cx = new float[steps + 1];
        float[] cy = new float[steps + 1];
        float[] nx = new float[steps + 1];
        float[] ny = new float[steps + 1];
        float[] phase = new float[steps + 1];

        for (int i = 0; i <= steps; i++) {
            float theta = (float) i / steps * TAU;

            float displacement = ring.profile().at(theta);
            float wobble = 1.0F + ring.wobbleAmplitude()
                    * mthSin(theta * ring.wobbleFrequency() + drift);

            float magnitude = ring.baseRadius() * radiusUnit * displacement * wobble;
            float angle = theta + spin;
            float cos = mthCos(angle);
            float sin = mthSin(angle);

            cx[i] = centerX + cos * magnitude;
            cy[i] = centerY + sin * magnitude;
            // 相位用"转过自转的角"乘倍数：接缝处连续，插值不会跨圆
            phase[i] = (angle + ring.phaseOffset()) * multiplicity;
        }

        // 径向单位向量：用相邻采样点差分求切线再转 90°
        for (int i = 0; i <= steps; i++) {
            int ringSize = steps;                        // 首尾点重合，故环长为 steps
            int prev = (i - 1 + ringSize) % ringSize;
            int next = (i + 1) % ringSize;

            float tx = cx[next] - cx[prev];
            float ty = cy[next] - cy[prev];
            float len = (float) Math.sqrt(tx * tx + ty * ty);

            if (len < 1.0E-4F) {
                float rx = cx[i] - centerX;
                float ry = cy[i] - centerY;
                float rl = (float) Math.sqrt(rx * rx + ry * ry);
                nx[i] = rl < 1.0E-4F ? 1.0F : rx / rl;
                ny[i] = rl < 1.0E-4F ? 0.0F : ry / rl;
            } else {
                nx[i] = -ty / len;
                ny[i] = tx / len;
            }
        }

        int written = 0;
        for (int i = 0; i < steps; i++) {
            int next = (i + 1) % steps;

            // 末段外推相位，避免回卷到 phase[0] 造成接缝跨圆
            float phaseA = phase[i];
            float phaseB = phaseA + multiplicity * (TAU / steps);

            // 内缘两点
            float iax = cx[i] - nx[i] * halfWidth;
            float iay = cy[i] - ny[i] * halfWidth;
            float ibx = cx[next] - nx[next] * halfWidth;
            float iby = cy[next] - ny[next] * halfWidth;
            // 外缘两点
            float oax = cx[i] + nx[i] * halfWidth;
            float oay = cy[i] + ny[i] * halfWidth;
            float obx = cx[next] + nx[next] * halfWidth;
            float oby = cy[next] + ny[next] * halfWidth;

            // 四边形 (内i, 外i, 外i+1, 内i+1) → 两个三角形
            mesh.vertex(iax, iay, 0.0F, phaseA);
            mesh.vertex(oax, oay, 1.0F, phaseA);
            mesh.vertex(obx, oby, 1.0F, phaseB);

            mesh.vertex(iax, iay, 0.0F, phaseA);
            mesh.vertex(obx, oby, 1.0F, phaseB);
            mesh.vertex(ibx, iby, 0.0F, phaseB);
            written += 6;
        }
        return written;
    }

    /** 顶点布局 = json attributes：Position(3) + UV0(2)。 */
    private static final class Mesh {
        float[] data = new float[1 << 14];
        int size;

        void vertex(float x, float y, float radialCoord, float phase) {
            if (size + 5 > data.length) {
                float[] grown = new float[data.length * 2];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = x;
            data[size++] = y;
            data[size++] = 0.0F;
            data[size++] = radialCoord;
            data[size++] = phase;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  数学辅助（与 Minecraft Mth / DifficultySigilMesh 对齐）
    // ══════════════════════════════════════════════════════════════

    /** 1.20.1 的 Mth.sin/cos 是查表近似；这里用 Math.sin/cos，误差远小于半像素。 */
    private static float mthSin(float v) {
        return (float) Math.sin(v);
    }

    private static float mthCos(float v) {
        return (float) Math.cos(v);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    private static int clampInt(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /** 把角度折到 [-π, π]。 */
    private static float wrapPi(float angleRad) {
        float wrapped = angleRad % TAU;
        if (wrapped > (float) Math.PI) {
            wrapped -= TAU;
        }
        if (wrapped < -(float) Math.PI) {
            wrapped += TAU;
        }
        return wrapped;
    }

    /** 稳定的 1 维哈希，取值 0..1。与 shader 里的 hash11 同构。 */
    private static float hash11(float p) {
        float v = fract(p * 0.1031F);
        v *= v + 33.33F;
        v *= v + v;
        return fract(v);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    // ══════════════════════════════════════════════════════════════
    //  数据类
    // ══════════════════════════════════════════════════════════════

    private record Entry(String label, String texture,
                         float glowR, float glowG, float glowB,
                         List<Ring> rings) {
    }
}
