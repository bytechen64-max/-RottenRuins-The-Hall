import java.util.Locale;

/**
 * 场景：创造物品栏的「分区隔断行」。
 *
 * <p>模拟 {@code SectionedCreativeScreen} 里的一页创造物品栏：深色面板 + 9 列 × 10 行
 * 的 18px 槽位井，其中三行是隔断行 —— 与 {@code CreativeTabDividerRenderer} 一样，
 * 在槽位画完之后（Foreground 层）用 {@code gui_tab_divider} 画一条 162×18 的流动色带，
 * 再把流动彩字标题逐字压上去。</p>
 *
 * <h3>坐标口径</h3>
 * <p>色带与标题都使用<b>面板相对坐标</b>：{@code GRID_X=8, GRID_W=162, GRID_Y=17,
 * ROW_H=18}，隔断行 {@code y = GRID_Y + local*ROW_H}（本页 local = 0 / 3 / 6）。
 * 只是因为离屏画布没有 PoseStack 平移，场景里统一用 {@code PANEL_X/PANEL_Y}
 * 把它们换算成画布绝对坐标 —— 这和实机 Foreground 事件里"文字相对面板、色带绝对屏幕"
 * 的两套坐标无关，画面上两者是对齐的。</p>
 *
 * <h3>标题</h3>
 * <p>逐字渲染，每个字一个随时间/横向位置流动的色相：
 * {@code hue = (time*0.16 + (x-centerX)*0.0055) % 1}，
 * 颜色 {@code Mth.hsvToRgb(hue, 0.45, 1.0)}（这里按 MC 的算法逐位复刻），
 * 带投影、以 {@code GRID_X + GRID_W/2} 居中。标题文案取自
 * {@code en_us.json} 的 {@code itemGroup.rotten_ruins.main.section.*}。</p>
 *
 * <p>产物：{@code tab_divider.png}（-Time 帧，GuiPreview 自动保存）与
 * {@code tab_divider_flow.png}（+2.5 s 的另一相位，体现色带/标题颜色扫过）。</p>
 */
public final class SceneTabDivider {

    private SceneTabDivider() {}

    // ══════════════════════════════════════════════════════════════
    //  与 CreativeTabDividerRenderer 完全一致的几何 / 调色板 / 流动常量
    // ══════════════════════════════════════════════════════════════

    /** 9 个槽位井的整体左边界（槽位 x = 9 + 列 * 18，井比槽位向左多 1px）。 */
    private static final int GRID_X = 8;
    /** 9 个井的总宽：9 × 18 = 162。 */
    private static final int GRID_W = 162;
    /** 网格上沿的井边框（槽位 y = 18 + 行 * 18，井比槽位向上多 1px）。 */
    private static final int GRID_Y = 17;
    private static final int ROW_H = 18;

    private static final float[] PALETTE_A = {0.79F, 0.63F, 0.15F, 1.00F};
    private static final float[] PALETTE_B = {0.27F, 0.78F, 0.62F, 0.85F};
    private static final float[] PALETTE_C = {0.60F, 0.16F, 0.26F, 0.70F};
    private static final float INTENSITY = 1.0F;
    private static final float GLOW = 0.6F;

    /** 标题的流动速度（圈/秒）与沿条的色相梯度（圈/像素）。 */
    private static final float TEXT_FLOW_SPEED = 0.16F;
    private static final float TEXT_FLOW_GRADIENT = 0.0055F;

    // ══════════════════════════════════════════════════════════════
    //  预览面板（模拟创造物品栏一页）
    // ══════════════════════════════════════════════════════════════

    private static final int PANEL_X = 44;
    private static final int PANEL_Y = 52;
    /** 面板宽：8 + 162 + 右边距/滚动条 ≈ 195，和原版创造物品栏一页的宽度一致。 */
    private static final int PANEL_W = 195;
    /** 10 行 × 18 + 顶部 17 + 底部留白 15。 */
    private static final int PANEL_H = GRID_Y + 10 * ROW_H + 15;
    private static final int TOTAL_ROWS = 10;
    private static final int SCROLLBAR_X = PANEL_X + 176;
    private static final int SCROLLBAR_W = 7;

    /**
     * 隔断行所在的 local 行号与标题（来自 en_us 的 section.* 文案）。
     * 每 3 行放一条：local = 0 / 3 / 6 → 面板相对 y = GRID_Y + local*ROW_H = 17 / 71 / 125；
     * 和实机一样"隔断行 = 一个分区标题行，后面跟本分区的物品行"。
     */
    private static final int[] DIVIDER_ROWS = {0, 3, 6};
    private static final String[] DIVIDER_TITLES = {"Weapons & Tools", "Magic", "Materials"};

    // ══════════════════════════════════════════════════════════════
    //  填充用物品图标（只在非隔断行，示意"色带压在槽位行上"）
    // ══════════════════════════════════════════════════════════════

    private static final int[][] ITEM_SLOTS = {
            {1, 0}, {1, 2}, {1, 5}, {1, 8},
            {2, 1}, {2, 3}, {2, 6},
            {4, 0}, {4, 4}, {4, 7},
            {5, 2}, {5, 5},
            {7, 0}, {7, 3}, {7, 8},
            {8, 1}, {8, 4},
            {9, 0}, {9, 6}};
    private static final String[] ITEM_TEXTURES = {
            "textures/item/crimson_vow.png",
            "textures/item/domerite_ingot.png",
            "textures/item/infection_bone.png",
            "textures/item/void_rune.png",
            "textures/item/crude_sword.png",
            "textures/item/hall_tendon.png",
            "textures/item/syringe.png",
            "textures/item/hard_fragments.png",
            "textures/item/magic_cata.png",
            "textures/item/live_meat.png",
            "textures/item/domite_crystal.png",
            "textures/item/wand_nature.png",
            "textures/item/corruption_zombie_spawn_egg.png",
            "textures/item/acid_sac.png",
            "textures/item/host_meat.png",
            "textures/item/death_star.png",
            "textures/item/domerite_sword.png",
            "textures/item/silent_daylight.png",
            "textures/item/bonecrusher_core.png"};

    // ══════════════════════════════════════════════════════════════
    //  颜色
    // ══════════════════════════════════════════════════════════════

    private static final int CLEAR = 0xFF000000;
    /** c.text() 每段的最大字符数（绕过框架 MemoryStack 64KB 顶点上传上限，见 txt()）。 */
    private static final int TEXT_CHUNK = 16;
    private static final int TEXT_TITLE = 0xFFFFFFFF;
    private static final int TEXT_BODY = 0xFFCCCCCC;
    private static final int TEXT_DIM = 0xFF8899AA;
    private static final int TEXT_ACCENT = 0xFFFFE08A;
    private static final int PANEL_BG_TOP = 0xFF17171E;
    private static final int PANEL_BG_BOTTOM = 0xFF0B0B10;
    private static final int PANEL_HEADER_TOP = 0xFF262632;
    private static final int PANEL_HEADER_BOTTOM = 0xFF1A1A22;
    private static final int WELL_FRAME = 0xFF0A0A0E;
    private static final int WELL_INNER = 0xFF1C1C23;

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        // 相位 B（+2.5 s）先画并存成 tab_divider_flow.png；主帧（-Time）放最后，
        // 交给 GuiPreview 在 render 返回后自动保存成 tab_divider.png。
        float flowTime = time + 2.5F;
        drawScreen(c, flowTime, fmt("time = %.2f s   (phase B: +2.50 s, title hue +0.40 turns)", flowTime));
        c.save("tab_divider_flow");

        drawScreen(c, time, fmt("time = %.2f s   (phase A, matches -Time)", time));
    }

    // ══════════════════════════════════════════════════════════════
    //  一屏：画布标题 + 创造面板 + 右侧说明
    // ══════════════════════════════════════════════════════════════

    private static void drawScreen(PreviewCanvas c, float time, String phaseLine) {
        c.clear(CLEAR);
        c.backdrop();

        // ── 画布标题 ──
        txt(c, "Creative inventory - section divider rows", 14, 10, TEXT_TITLE, true);
        txt(c, "gui_tab_divider ribbon (162x18) + flowing per-glyph title, preview of SectionedCreativeScreen",
                14, 24, TEXT_BODY, false);
        txt(c, fmt("panel-relative grid: GRID_X=%d GRID_W=%d GRID_Y=%d ROW_H=%d",
                GRID_X, GRID_W, GRID_Y, ROW_H), 14, 36, TEXT_DIM, false);

        drawPanel(c, time);
        drawAnnotations(c, time, phaseLine);
    }

    // ══════════════════════════════════════════════════════════════
    //  创造物品栏面板
    // ══════════════════════════════════════════════════════════════

    private static void drawPanel(PreviewCanvas c, float time) {
        // 投影 + 外框 + 深色面板底
        c.fill(PANEL_X + 4, PANEL_Y + 4, PANEL_W, PANEL_H, 0x55000000);
        c.strokeRect(PANEL_X - 1, PANEL_Y - 1, PANEL_W + 2, PANEL_H + 2,
                0xFF4A4A58, 0xFF14141A);
        c.fillGradientV(PANEL_X, PANEL_Y, PANEL_W, PANEL_H,
                PANEL_BG_TOP, PANEL_BG_BOTTOM);

        // 顶栏：模拟创造界面的标签页标题区
        c.fillGradientV(PANEL_X, PANEL_Y, PANEL_W, GRID_Y,
                PANEL_HEADER_TOP, PANEL_HEADER_BOTTOM);
        c.fill(PANEL_X, PANEL_Y + GRID_Y - 1, PANEL_W, 1, 0xFF3C3C4A);
        c.fill(PANEL_X + 6, PANEL_Y + 7, 4, 4, 0xFFC9A227);
        txt(c, "Rotten Ruins", PANEL_X + 14, PANEL_Y + 4, 0xFFE8D48A, false);
        txtRight(c, "Creative", PANEL_X + PANEL_W - 7, PANEL_Y + 4, 0xFF8899AA, false);

        // 槽位井：9 列 × 10 行，井 18×18、内槽 16×16（与 tab_items.png 量出的几何一致）
        for (int row = 0; row < TOTAL_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int x = PANEL_X + GRID_X + col * 18;
                int y = PANEL_Y + GRID_Y + row * ROW_H;
                c.fill(x, y, 18, 18, WELL_FRAME);
                c.fill(x + 1, y + 1, 16, 16, WELL_INNER);
                c.fill(x + 1, y + 1, 16, 1, 0x1AFFFFFF);
                c.fill(x + 1, y + 16, 16, 1, 0x33000000);
            }
        }

        // 少量物品图标：让"色带盖住整行槽位"这件事在画面里可读
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int row = ITEM_SLOTS[i][0];
            int col = ITEM_SLOTS[i][1];
            PreviewTexture tex = c.texture(ITEM_TEXTURES[i]);
            int x = PANEL_X + GRID_X + col * 18;
            int y = PANEL_Y + GRID_Y + row * ROW_H;
            c.blit(tex, x + 1, y + 1, 16, 16);
        }

        // 右侧滚动条（创造物品栏右侧那根）
        c.fill(SCROLLBAR_X, PANEL_Y + GRID_Y, SCROLLBAR_W, TOTAL_ROWS * ROW_H, 0xFF08080B);
        c.fill(SCROLLBAR_X + 1, PANEL_Y + GRID_Y + 2, SCROLLBAR_W - 2, 42, 0xFF3E3E4C);
        c.fill(SCROLLBAR_X + 1, PANEL_Y + GRID_Y + 2, SCROLLBAR_W - 2, 1, 0xFF5A5A6A);

        // ── 隔断行色带（Foreground 层：压在槽位井上）──
        drawDividerBars(c, time);

        // ── 流动彩字标题（面板相对坐标 → 画布绝对坐标）──
        int centerX = PANEL_X + GRID_X + GRID_W / 2;
        for (int i = 0; i < DIVIDER_ROWS.length; i++) {
            int local = DIVIDER_ROWS[i];
            int titleY = PANEL_Y + GRID_Y + local * ROW_H + (ROW_H - c.fontHeight()) / 2;
            drawFlowingTitle(c, DIVIDER_TITLES[i], centerX, titleY, time);
        }
    }

    private static void drawDividerBars(PreviewCanvas c, float time) {
        PreviewShader divider = c.shader("gui_tab_divider");
        // 框架/实机的 Uniform.set() 只写"当前 program"；它前面刚画完槽位/物品/滚动条
        // （内置 program），这里先把 divider 切为当前 program，否则 uTime/palette
        // 会丢到别的 program 上，色带会停在 json 默认值（uTime=0）。
        prime(c, divider);
        divider.set("uTime", time);
        divider.set("uSize", (float) GRID_W, (float) ROW_H);
        divider.set("uColorA", PALETTE_A[0], PALETTE_A[1], PALETTE_A[2], PALETTE_A[3]);
        divider.set("uColorB", PALETTE_B[0], PALETTE_B[1], PALETTE_B[2], PALETTE_B[3]);
        divider.set("uColorC", PALETTE_C[0], PALETTE_C[1], PALETTE_C[2], PALETTE_C[3]);
        divider.set("uIntensity", INTENSITY);
        divider.set("uGlow", GLOW);
        int barX = PANEL_X + GRID_X;
        for (int local : DIVIDER_ROWS) {
            int barY = PANEL_Y + GRID_Y + local * ROW_H;
            c.shaderQuad(divider, barX, barY, GRID_W, ROW_H);
        }
    }

    /**
     * 逐字画流动彩字标题（与 {@code CreativeTabDividerRenderer#drawFlowingText} 同算法）。
     *
     * <p>hue 里的 {@code x - centerX} 用的是"当前字形在面板相对坐标里的 x"；
     * 场景里 centerX 已经是画布绝对坐标，但差值不变，所以相位一致。</p>
     */
    private static void drawFlowingTitle(PreviewCanvas c, String text, int centerX, int y, float time) {
        int x = centerX - c.textWidth(text) / 2;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            int advance = c.textWidth(glyph);
            float hue = (time * TEXT_FLOW_SPEED + (x - centerX) * TEXT_FLOW_GRADIENT) % 1.0F;
            if (hue < 0.0F) {
                hue += 1.0F;
            }
            int rgb = hsvToRgb(hue, 0.45F, 1.0F);
            // 带投影：色带本身很花，不加一层暗描边会读不清
            txt(c, glyph, x, y, 0xFF000000 | rgb, true);
            x += advance;
            i += Character.charCount(cp);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  右侧 / 左下说明
    // ══════════════════════════════════════════════════════════════

    private static void drawAnnotations(PreviewCanvas c, float time, String phaseLine) {
        int tx = 266;
        txt(c, "gui_tab_divider", tx, 52, TEXT_TITLE, true);
        txt(c, "Sectioned creative tab - divider rows", tx, 64, TEXT_ACCENT, false);
        txt(c, phaseLine, tx, 76, TEXT_BODY, false);

        txt(c, "Panel-relative geometry (CreativeTabDividerRenderer):", tx, 92, TEXT_BODY, true);
        txt(c, "  GRID_X=8   GRID_W=162   GRID_Y=17   ROW_H=18", tx, 106, TEXT_BODY, false);
        txt(c, "  divider row y = GRID_Y + local*ROW_H, local = 0, 3, 6", tx, 118, TEXT_BODY, false);
        txt(c, "  bars: x = 8..170, 162x18 (y = 17/71/125), one draw call", tx, 130, TEXT_BODY, false);
        txt(c, "  layer: ContainerScreenEvent.Render.Foreground (over slots)", tx, 142, TEXT_BODY, false);

        txt(c, "Shape / animation uniforms:", tx, 160, TEXT_BODY, true);
        txt(c, "  uTime, uSize=(162,18), uColorA/B/C", tx, 172, TEXT_BODY, false);
        txt(c, "  uIntensity=1.0, uGlow=0.6 (same values as the renderer)", tx, 184, TEXT_BODY, false);

        txt(c, "Title (per-glyph flowing hue):", tx, 202, TEXT_BODY, true);
        txt(c, "  hue = (time*0.16 + (x-centerX)*0.0055) % 1", tx, 214, TEXT_BODY, false);
        txt(c, "  color = Mth.hsvToRgb(hue, 0.45, 1.0), drawn with shadow", tx, 226, TEXT_BODY, false);
        txt(c, "  centered at GRID_X + GRID_W/2; titles from en_us:", tx, 238, TEXT_BODY, false);
        txt(c, "  itemGroup.rotten_ruins.main.section.*", tx, 250, TEXT_BODY, false);

        txt(c, "Palette (rgb weighted by .a in the ribbon):", tx, 270, TEXT_ACCENT, true);
        drawPaletteChip(c, tx, 282, PALETTE_A, "A");
        drawPaletteChip(c, tx + 120, 282, PALETTE_B, "B");
        drawPaletteChip(c, tx + 240, 282, PALETTE_C, "C");

        // 标题色相条：把当前相位的 hue->rgb 直接铺出来，方便对照文字颜色
        txt(c, "Title hue ramp over 180 px (the title is ~88 px wide):", tx, 306, TEXT_BODY, false);
        int rampX = tx;
        int rampY = 320;
        int rampW = 180;
        for (int i = 0; i < rampW; i++) {
            float hue = (time * TEXT_FLOW_SPEED + (i - rampW / 2) * TEXT_FLOW_GRADIENT) % 1.0F;
            if (hue < 0.0F) {
                hue += 1.0F;
            }
            c.fill(rampX + i, rampY, 1, 10, 0xFF000000 | hsvToRgb(hue, 0.45F, 1.0F));
        }
        c.strokeRect(rampX - 1, rampY - 1, rampW + 2, 12, 0xFF4A4A58, 0xFF14141A);

        txt(c, fmt("tab_divider_flow.png = same panel at %.2f s (phase B)", time + 2.5F),
                tx, 344, TEXT_DIM, false);
        txt(c, "  +2.5 s = +0.40 turns of the title hue vs phase A", tx, 356, TEXT_DIM, false);

        // 左下：本预览的取舍说明
        int nx = 44;
        txt(c, "Preview notes:", nx, 280, TEXT_ACCENT, true);
        txt(c, "  9 x 18 px wells, 10 rows shown;", nx, 292, TEXT_BODY, false);
        txt(c, "  divider rows replace a full row", nx, 304, TEXT_BODY, false);
        txt(c, "  with the 162x18 ribbon, drawn", nx, 316, TEXT_BODY, false);
        txt(c, "  after the slots (Foreground).", nx, 328, TEXT_BODY, false);
        txt(c, "  Dark panel instead of the light", nx, 344, TEXT_DIM, false);
        txt(c, "  vanilla #C6C6C6 panel: gray", nx, 356, TEXT_DIM, false);
        txt(c, "  washes the ribbon out.", nx, 368, TEXT_DIM, false);
    }

    private static void drawPaletteChip(PreviewCanvas c, int x, int y, float[] rgba, String name) {
        int rgb = rgbOf(rgba);
        c.fill(x, y, 14, 14, 0xFF000000 | rgb);
        c.strokeRect(x, y, 14, 14, 0x66FFFFFF, 0x33000000);
        txt(c, fmt("%s %s a=%.2f", name, fmt("#%06X", rgb), rgba[3]), x + 18, y + 3,
                TEXT_BODY, false);
    }

    // ══════════════════════════════════════════════════════════════
    //  颜色：Mth.hsvToRgb 的逐位复刻（Scene 编译时不带 MC 类）
    // ══════════════════════════════════════════════════════════════

    private static int hsvToRgb(float hue, float saturation, float value) {
        int i = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - (float) i;
        float f1 = value * (1.0F - saturation);
        float f2 = value * (1.0F - saturation * f);
        float f3 = value * (1.0F - saturation * (1.0F - f));
        float r;
        float g;
        float b;
        switch (i) {
            case 0 -> { r = value; g = f3; b = f1; }
            case 1 -> { r = f2; g = value; b = f1; }
            case 2 -> { r = f1; g = value; b = f3; }
            case 3 -> { r = f1; g = f2; b = value; }
            case 4 -> { r = f3; g = f1; b = value; }
            default -> { r = value; g = f1; b = f2; }
        }
        int ri = (int) (r * 255.0F);
        int gi = (int) (g * 255.0F);
        int bi = (int) (b * 255.0F);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static int rgbOf(float[] rgba) {
        int r = Math.round(rgba[0] * 255.0F);
        int g = Math.round(rgba[1] * 255.0F);
        int b = Math.round(rgba[2] * 255.0F);
        return (r << 16) | (g << 8) | b;
    }

    // ══════════════════════════════════════════════════════════════
    //  小工具
    // ══════════════════════════════════════════════════════════════

    /**
     * 把 {@code shader} 切为"当前 GL program"，但不产生任何像素。
     *
     * <p>{@code PreviewShader.set()} 和 MC 的 {@code Uniform.set()} 一样，只把值写进
     * <b>当前</b> program：两次 {@code set} 之间若夹了 {@code fill/blit/text}，
     * uniform 就会写到框架内置 program 上，shader 拿到 json 默认值（t=0 的静止色带）。
     * 框架没有暴露 bind-program 的 API，这里画一个 0 面积 quad 让 drawShader 走一遍
     * {@code glUseProgram}；零面积三角形不会产生像素。</p>
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

    private static void txtRight(PreviewCanvas c, String s, int rightX, int y,
                                 int argb, boolean shadow) {
        txt(c, s, rightX - c.textWidth(s), y, argb, shadow);
    }

    private static String fmt(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}
