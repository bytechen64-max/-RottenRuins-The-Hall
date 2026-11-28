import org.lwjgl.opengl.GL11;

/**
 * 织星台的"像素 + 平滑"混合渲染稿。
 *
 * <p>原则：<b>结构 / 文字 / 交互件用像素，能量 / 氛围 / 反馈用平滑</b>。</p>
 * <ul>
 *   <li><b>像素层</b>：面板、边框、凹陷分区、按钮、符印框、文字、槽位、锁、进度槽、
 *       星座节点的骨架环与符文 —— 1px 硬边，保证 MC 可读性，也方便以后换成可重绘贴图。</li>
 *   <li><b>平滑层</b>：夜空星云、星座连线光带、沿线光脉冲、节点辉光、法阵徽记光晕、
 *       不稳定红线 —— 走项目真实的 {@code magic_starfield} / {@code magic_constellation} /
 *       {@code magic_pulse} 着色器和 SDF 柔光，负责"魔法感"。</li>
 * </ul>
 *
 * <p>图层顺序固定为：像素底板 →（裁剪在星座板内的）平滑星空 → 加色直线 / 光晕 →
 * 像素节点 / 符文 → 最上层文字。平滑层永远不压文字和可点击元件。</p>
 *
 * <p>产物：{@code spell_weave_hybrid.png}、{@code spell_weave_hybrid_unstable.png}，
 * 以及 1280×470 的"纯像素 vs 混合"对比（{@code -Width 1280 -Height 470 -Scale 1}）。</p>
 */
public final class SceneSpellWeaveHybrid {

    // ══════════════════════════════════════════════════════════════
    //  Pixel-arcane 调色板
    // ══════════════════════════════════════════════════════════════

    private static final int OUTLINE = 0xFF1A1230;
    private static final int FRAME_HI = 0xFF3E3060;
    private static final int FRAME_LO = 0xFF05030C;
    private static final int PANEL = 0xFF0B0718;
    private static final int PANEL_DARK = 0xFF08040F;
    private static final int PANEL_LIGHT = 0xFF120C24;
    private static final int GOLD = 0xFFC9A45A;
    private static final int GOLD_HI = 0xFFEAD49B;
    private static final int GOLD_LO = 0xFF6E5426;
    private static final int TEXT = 0xFFE6DAC2;
    private static final int TEXT_DIM = 0xFF9B8FB0;
    private static final int TEXT_FAINT = 0xFF5F5478;
    private static final int TEAL = 0xFF6FD8C0;
    private static final int WARN = 0xFFFF5A4A;
    private static final int SKY_BG = 0xFF04030A;

    private static final int PANEL_W = 608;
    private static final int PANEL_H = 364;
    private static final int TRAY_W = 152;
    private static final int TRAY_H = 258;
    private static final int PREVIEW_W = 152;
    private static final int PREVIEW_H = 258;

    /** 面板左上角；对比图里左右各偏移一次。 */
    private static int panelX = 16;
    private static int panelY = 18;

    private static final float TAU = (float) (Math.PI * 2.0);

    private static final int[] SCHOOL_COLORS = {
            0xFFF4AC, 0xFF7843, 0xFF53BEFF, 0xFF78D98A, 0xFFBE8BDA, 0xFF8B85FF, 0xFFE7A7EF
    };
    private static final int FIRE = 1;

    private record Sigil(String label, int color, int seed) {
    }

    private static final Sigil[] SCHOOL_SIGILS = sigils(
            new String[]{"LIGHT", "FIRE", "WATER", "NATURE", "DEATH", "VOID", "MYSTERY"},
            SCHOOL_COLORS);
    private static final Sigil[] FORM_SIGILS = {
            new Sigil("BOLT", GOLD, 0x0B017),
            new Sigil("BEAM", GOLD_HI, 0x0BEA7),
            new Sigil("FIELD", GOLD, 0x0F1E1D),
            new Sigil("AURA", GOLD_HI, 0x0A0BA),
            new Sigil("STEP", GOLD, 0x057E9)
    };
    private static final Sigil[] MODIFIER_SIGILS = {
            new Sigil("SPLIT", TEAL, 0x05B117),
            new Sigil("DELAY", TEAL, 0x0DE1A7),
            new Sigil("CHAIN", TEAL, 0x0C4A10),
            new Sigil("PIERCE", TEAL, 0x0F1E4C),
            new Sigil("EMPOWER", TEAL, 0x0E3B00),
            new Sigil("LINGER", TEAL, 0x0F1A6E)
    };
    private static final int LOCKED_SIGIL = 2;

    private record Star(int x, int y, int radius, String label, int color, int seed) {
    }

    // 星座坐标全部相对面板左上角。
    private static final Star STABLE_CORE = new Star(304, 258, 7, "SEED", GOLD_HI, 0x0C0DE);
    private static final Star[] STABLE_PATH = {
            new Star(268, 220, 6, "FIRE", SCHOOL_COLORS[FIRE], 0x0F11E),
            new Star(336, 210, 6, "BOLT", GOLD_HI, 0x0B017),
            new Star(382, 168, 5, "SPLIT", TEAL, 0x05B117),
            new Star(330, 140, 5, "DELAY", TEAL, 0x0DE1A7),
            new Star(266, 162, 5, "EMPOWER", GOLD, 0x0E3B00)
    };
    private static final int[][] STABLE_LINKS = {{0, 1}, {1, 2}, {2, 4}, {4, 5}, {2, 3}};

    private static final Star UNSTABLE_CORE = new Star(304, 258, 7, "SEED", GOLD_HI, 0x0C0DE);
    private static final Star[] UNSTABLE_PATH = {
            new Star(270, 214, 6, "VOID", SCHOOL_COLORS[5], 0x0B01D),
            new Star(340, 218, 6, "FIELD", GOLD, 0x0F1E1D),
            new Star(386, 170, 5, "LINGER", TEAL, 0x0F1A6E),
            new Star(328, 138, 5, "PIERCE", TEAL, 0x0F1E4C),
            new Star(264, 152, 5, "EMPOWER", GOLD, 0x0E3B00)
    };
    private static final int[][] UNSTABLE_LINKS = {{0, 1}, {1, 2}, {2, 3}, {1, 4}};

    /** 点阵星云团（相对星座板内部）。 */
    private static final int[][] NEBULA = {
            {60, 40, 26, 0xFF241640}, {150, 92, 34, 0xFF14203A},
            {84, 152, 24, 0xFF2A1530}, {192, 172, 20, 0xFF101A30}
    };
    private static final int[][] SKY_STARS = {
            {0xFFE6DAC2}, {0xFF8FD2FF}, {0xFFFFD27A}, {0xFFB48CFF}
    };

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) throws Exception {
        c.shader("sdf_ui");
        if (c.width >= 1200) {
            comparison(c);
            return;
        }
        scene(c, time, false);
        c.save("spell_weave_hybrid");
        scene(c, time, true);
        c.save("spell_weave_hybrid_unstable");
        scene(c, time, false);
    }

    private static void scene(PreviewCanvas c, float time, boolean unstable) {
        panelX = 16;
        panelY = 18;
        background(c);
        drawPanel(c, false);
        header(c, time, unstable);
        tray(c, time, false, true);
        board(c, time, true, unstable, true);
        preview(c, time, unstable, true);
        footer(c, false, unstable);
    }

    /** 左：纯像素；右：像素骨架 + 平滑能量层。 */
    private static void comparison(PreviewCanvas c) {
        background(c);
        c.text("A / PIXEL ONLY", 12, 20, WHITE, true);
        c.text("B / HYBRID - PIXEL CHROME + SMOOTH MAGIC", 664, 20, WHITE, true);

        panelX = 12;
        panelY = 68;
        drawPanel(c, false);
        header(c, 2.4F, false);
        tray(c, 2.4F, false, false);
        board(c, 2.4F, false, false, false);
        preview(c, 2.4F, false, false);
        footer(c, false, false);

        panelX = 664;
        drawPanel(c, false);
        header(c, 2.4F, false);
        tray(c, 2.4F, false, true);
        board(c, 2.4F, false, false, true);
        preview(c, 2.4F, false, true);
        footer(c, false, false);

        c.textCentered("Same pixel chrome on both sides / left has no smooth layer"
                        + " / right uses magic_starfield + magic_constellation + magic_pulse",
                c.width / 2, 448, WHITE, true);
        c.save("spell_weave_hybrid_compare");
    }

    private static final int WHITE = 0xFFFFFFFF;

    // ══════════════════════════════════════════════════════════════
    //  背景 / 面板
    // ══════════════════════════════════════════════════════════════

    private static void background(PreviewCanvas c) {
        c.clear(0xFF030208);
        // 画面最外层用稀疏像素星点，面板本身不透明。
        for (int i = 0; i < 120; i++) {
            int h = hash(i * 37 + 11, 29);
            int x = Math.abs(h % c.width);
            int y = Math.abs((h >> 8) % c.height);
            int alpha = 40 + Math.abs((h >> 16) % 150);
            c.fill(x, y, 1, 1, withAlpha(TEXT, alpha));
        }
        for (int i = 0; i < 14; i++) {
            int h = hash(i * 53 + 7, 91);
            int x = Math.abs(h % c.width);
            int y = Math.abs((h >> 9) % c.height);
            starPx(c, x, y, GOLD_HI, (h & 3) == 0);
        }
    }

    private static void drawPanel(PreviewCanvas c, boolean textured) {
        int x = panelX, y = panelY;
        if (textured) {
            c.blit(c.texture("textures/gui/spell_weave/weave_bg.png"),
                    x, y, PANEL_W, PANEL_H);
            return;
        }
        // 面板本体
        bevel(c, x, y, PANEL_W, PANEL_H, PANEL, FRAME_HI, FRAME_LO, OUTLINE);
        // 标题带的轻微提亮 + 抖动底边
        c.fill(x + 3, y + 3, PANEL_W - 6, 38, PANEL_LIGHT);
        for (int i = 4; i < PANEL_W - 4; i += 2) {
            c.fill(x + i, y + 41, 1, 1, FRAME_HI);
            c.fill(x + i + 1, y + 42, 1, 1, PANEL);
        }
        c.fill(x + 8, y + 40, PANEL_W - 17, 1, GOLD_LO);
        c.fill(x + 8, y + 41, PANEL_W - 17, 1, FRAME_LO);
        // 三个凹陷分区
        sunken(c, x + 12, y + 50, TRAY_W, TRAY_H);
        sunken(c, x + 176, y + 50, 256, TRAY_H);
        sunken(c, x + 444, y + 50, PREVIEW_W, PREVIEW_H);
        // 底部条
        c.fill(x + 3, y + 314, PANEL_W - 6, PANEL_H - 318, PANEL_DARK);
        c.fill(x + 8, y + 314, PANEL_W - 17, 1, FRAME_HI);
        c.fill(x + 8, y + 315, PANEL_W - 17, 1, FRAME_LO);
        c.fill(x + 8, y + 318, PANEL_W - 17, 1, 0xFF1C1430);
        // 四角符文
        int[][] corners = {{6, 6}, {PANEL_W - 11, 6}, {6, PANEL_H - 11}, {PANEL_W - 11, PANEL_H - 11}};
        for (int[] corner : corners) {
            runeCorner(c, x + corner[0], y + corner[1]);
        }
    }

    private static void runeCorner(PreviewCanvas c, int x, int y) {
        String[] pattern = {"X...X", "X...X", "..X..", "X...X", "X...X"};
        for (int row = 0; row < pattern.length; row++) {
            for (int col = 0; col < pattern[row].length(); col++) {
                if (pattern[row].charAt(col) == 'X') {
                    c.fill(x + col, y + row, 1, 1, ((row + col) % 3 == 0) ? GOLD : GOLD_LO);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  头部
    // ══════════════════════════════════════════════════════════════

    private static void header(PreviewCanvas c, float time, boolean unstable) {
        int x = panelX, y = panelY;
        c.text("ROTTEN RUINS", x + 14, y + 8, withAlpha(GOLD, 150), false);
        c.textRight("ESC  CLOSE", x + 582, y + 8, withAlpha(TEXT_FAINT, 210), false);

        c.textCenteredScaled("STAR LOOM", x + PANEL_W / 2F, y + 6,
                withAlpha(GOLD_HI, 250), true, 2F);
        c.textCentered("WEAVE A CONSTELLATION  -  FORGE A SPELL", x + PANEL_W / 2, y + 27,
                withAlpha(GOLD, 190), false);

        // 像素装饰线：两端与中心各一颗星。
        int lineY = y + 38;
        for (int i = 0; i < 64; i++) {
            int px = x + PANEL_W / 2 - 96 + i;
            c.fill(px, lineY, 1, 1, withAlpha(GOLD_LO, 180));
        }
        for (int i = 0; i < 64; i++) {
            int px = x + PANEL_W / 2 + 33 + i;
            c.fill(px, lineY, 1, 1, withAlpha(GOLD_LO, 180));
        }
        starPx(c, x + PANEL_W / 2 - 96, lineY, GOLD_HI, true);
        starPx(c, x + PANEL_W / 2 + 96, lineY, GOLD_HI, true);
        starPx(c, x + PANEL_W / 2, lineY, GOLD_HI, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  左：符印托盘
    // ══════════════════════════════════════════════════════════════

    private static void tray(PreviewCanvas c, float time, boolean textured, boolean hybrid) {
        int x = panelX + 12, y = panelY + 50;
        c.text("SIGIL TRAY", x + 9, y + 8, withAlpha(GOLD_HI, 235), true);
        c.textRight("17 / 18", x + TRAY_W - 9, y + 8, withAlpha(TEXT_FAINT, 220), false);

        category(c, x, y + 24, "SCHOOLS");
        tokenRow(c, time, textured, hybrid, SCHOOL_SIGILS, 4, x + 9, y + 40, 0, -1);
        tokenRow(c, time, textured, hybrid, SCHOOL_SIGILS, 3, x + 9, y + 66, 4, -1);

        category(c, x, y + 94, "FORMS");
        tokenRow(c, time, textured, hybrid, FORM_SIGILS, 4, x + 9, y + 110, 0, -1);
        tokenRow(c, time, textured, hybrid, FORM_SIGILS, 1, x + 9, y + 136, 4, -1);

        category(c, x, y + 164, "MODIFIERS");
        tokenRow(c, time, textured, hybrid, MODIFIER_SIGILS, 4, x + 9, y + 180, 0, LOCKED_SIGIL);
        tokenRow(c, time, textured, hybrid, MODIFIER_SIGILS, 2, x + 9, y + 206, 4, LOCKED_SIGIL);

        c.textCentered("DRAG A SIGIL INTO THE SKY", x + TRAY_W / 2, y + TRAY_H - 18,
                withAlpha(TEXT_FAINT, 220), false);
    }

    private static void category(PreviewCanvas c, int x, int y, String label) {
        c.text(label, x + 9, y, withAlpha(GOLD, 180), false);
        for (int i = 9; i < TRAY_W - 9; i += 2) {
            c.fill(x + i, y + 10, 1, 1, withAlpha(GOLD, 24));
        }
    }

    private static void tokenRow(PreviewCanvas c, float time, boolean textured, boolean hybrid,
                                 Sigil[] sigils, int count, int x, int y, int from, int locked) {
        for (int i = 0; i < count; i++) {
            int index = from + i;
            Sigil sigil = sigils[index];
            boolean lockedToken = index == locked;
            boolean selected = !lockedToken && "SPLIT".equals(sigil.label());
            int cx = x + 12 + i * 32;
            int cy = y + 12;
            token(c, time, textured, hybrid, cx, cy, sigil, selected, lockedToken);
        }
    }

    private static void token(PreviewCanvas c, float time, boolean textured, boolean hybrid,
                              int cx, int cy, Sigil sigil, boolean selected, boolean locked) {
        int color = locked ? TEXT_FAINT : sigil.color();
        if (hybrid && selected) {
            // 只给"拿在手上"的符印加一层平滑辉光，静止托盘保持像素清晰。
            for (int i = 3; i >= 1; i--) {
                WheelKit.fillDisc(c, cx, cy, 10F + i * 3F,
                        withAlpha(color, 8 + (3 - i) * 4));
            }
        }
        if (textured) {
            int sourceX = selected ? 100 : (locked ? 120 : 80);
            c.blit(c.texture("textures/gui/spell_weave/widgets.png"),
                    cx - 10, cy - 10, 20, 20,
                    sourceX / 160F, 0F, (sourceX + 20) / 160F, 1F);
        } else {
            // 像素符印座：20x20 小斜角石，选中加金边。
            bevel(c, cx - 10, cy - 10, 20, 20, PANEL_DARK,
                    selected ? GOLD_HI : GOLD_LO, FRAME_LO, OUTLINE);
            innerFrame(c, cx - 7, cy - 7, 14, 14,
                    selected ? GOLD_HI : (locked ? FRAME_HI : GOLD_LO));
            if (locked) {
                c.fill(cx - 5, cy - 5, 10, 10, PANEL_DARK);
            }
        }
        if (locked) {
            // 7x7 小锁
            int[] lock = {0x3E, 0x41, 0x41, 0x7F, 0x77, 0x77, 0x7F};
            for (int row = 0; row < lock.length; row++) {
                for (int col = 0; col < 7; col++) {
                    if ((lock[row] & (1 << (6 - col))) != 0) {
                        c.fill(cx - 3 + col, cy - 3 + row, 1, 1, TEXT_FAINT);
                    }
                }
            }
            return;
        }
        // 学派 / 形态色用像素菱形环表达，不靠小字读颜色。
        diamondRing(c, cx, cy, 7, withAlpha(color, selected ? 220 : 165));
        glyph(c, cx, cy, 9, sigil.seed(), withAlpha(color, selected ? 250 : 205));
        if (selected) {
            // 顶部小星表示"拿在手上"
            starPx(c, cx, cy - 13, GOLD_HI, false);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  中：星座板
    // ══════════════════════════════════════════════════════════════

    private static void board(PreviewCanvas c, float time, boolean textured, boolean unstable,
                              boolean hybrid) {
        int x = panelX + 176, y = panelY + 50;
        int fx = panelX + 182, fy = panelY + 70;
        c.text("CONSTELLATION", x + 9, y + 8, withAlpha(GOLD_HI, 235), true);
        c.textRight("24 NODES", x + 256 - 9, y + 8, withAlpha(TEXT_FAINT, 220), false);

        if (hybrid) {
            smoothSky(c, time, fx, fy, 244, 218);
        } else if (textured) {
            c.blit(c.texture("textures/gui/spell_weave/sky.png"), fx, fy, 244, 218);
        } else {
            pixelSky(c, fx, fy, 244, 218);
        }

        // 未使用的暗星
        int[][] dust = {{28, 28}, {56, 196}, {220, 40}, {232, 186}, {36, 110}, {214, 170}, {122, 24}, {66, 62}, {188, 200}};
        for (int i = 0; i < dust.length; i++) {
            int dx = fx + dust[i][0], dy = fy + dust[i][1];
            if (i % 3 == 0) {
                starPx(c, dx, dy, withAlpha(GOLD, 70), false);
            } else {
                c.fill(dx, dy, 1, 1, withAlpha(TEXT_FAINT, 130));
            }
        }

        if (unstable) {
            drawConstellation(c, time, panelX, panelY, UNSTABLE_CORE, UNSTABLE_PATH,
                    UNSTABLE_LINKS, true, hybrid);
        } else {
            drawConstellation(c, time, panelX, panelY, STABLE_CORE, STABLE_PATH,
                    STABLE_LINKS, false, hybrid);
        }

        c.text("6 SIGILS / 12 NODES", x + 9, y + TRAY_H - 18, withAlpha(TEXT_FAINT, 220), false);
        c.textRight(unstable ? "WILD SHAPE" : "ANY ORDER. ANY SHAPE.",
                x + 256 - 9, y + TRAY_H - 18, withAlpha(unstable ? WARN : GOLD_LO, 220), false);
    }

    /** A 方案：用 6×6 点阵块 + 抖动画星云，再撒像素星点。 */
    private static void pixelSky(PreviewCanvas c, int x, int y, int w, int h) {
        c.fill(x, y, w, h, SKY_BG);
        for (int[] blob : NEBULA) {
            int bx = blob[0], by = blob[1], radius = blob[2], color = blob[3];
            for (int py = 0; py < h; py += 6) {
                for (int px = 0; px < w; px += 6) {
                    float dx = (px + 3 - bx) / (float) radius;
                    float dy = (py + 3 - by) / (float) radius;
                    float falloff = 1F - (dx * dx + dy * dy);
                    if (falloff <= 0F) {
                        continue;
                    }
                    if (Math.abs(hash(px / 6, py / 6)) % 100 >= (int) (falloff * 125F)) {
                        continue;
                    }
                    int alpha = (int) (24 + 86 * falloff);
                    c.fill(x + px, y + py, 6, 6, withAlpha(color, alpha));
                }
            }
        }
        for (int i = 0; i < 220; i++) {
            int hsh = hash(i * 31 + 7, 0x51EED);
            int px = 4 + Math.abs(hsh % (w - 8));
            int py = 4 + Math.abs((hsh >> 8) % (h - 8));
            int color = SKY_STARS[Math.abs(hsh >> 16) % SKY_STARS.length][0];
            int alpha = 40 + Math.abs((hsh >> 20) % 160);
            c.fill(x + px, y + py, 1, 1, withAlpha(color, alpha));
            if ((hsh & 7) == 0) {
                c.fill(x + px - 1, y + py, 1, 1, withAlpha(color, alpha / 2));
                c.fill(x + px + 1, y + py, 1, 1, withAlpha(color, alpha / 2));
                c.fill(x + px, y + py - 1, 1, 1, withAlpha(color, alpha / 2));
                c.fill(x + px, y + py + 1, 1, 1, withAlpha(color, alpha / 2));
            }
        }
        // 很淡的像素法阵 + 四方位星，空星座板也要有仪式感。
        int scx = x + w / 2, scy = y + h / 2;
        for (int r = 0; r < 2; r++) {
            int radius = 84 - r * 4;
            int alpha = r == 0 ? 46 : 22;
            for (int i = 0; i < 180; i++) {
                float a = (float) (i * TAU / 180.0);
                int px = scx + Math.round((float) Math.cos(a) * radius);
                int py = scy + Math.round((float) Math.sin(a) * radius);
                c.fill(px, py, 1, 1, withAlpha(GOLD, alpha));
            }
        }
        for (int i = 0; i < 4; i++) {
            float a = (float) (i * TAU / 4.0 + Math.PI / 4.0);
            int px = scx + Math.round((float) Math.cos(a) * 84F);
            int py = scy + Math.round((float) Math.sin(a) * 84F);
            starPx(c, px, py, withAlpha(GOLD_HI, 90), true);
        }

        // 1px 像素内框
        c.fill(x, y, w, 1, OUTLINE);
        c.fill(x, y + h - 1, w, 1, OUTLINE);
        c.fill(x, y, 1, h, OUTLINE);
        c.fill(x + w - 1, y, 1, h, OUTLINE);
        c.fill(x + 1, y + 1, w - 2, 1, GOLD_LO);
        c.fill(x + 1, y + 1, 1, h - 2, GOLD_LO);
        c.fill(x + 1, y + h - 2, w - 2, 1, FRAME_LO);
        c.fill(x + w - 2, y + 1, 1, h - 2, FRAME_LO);
        c.fill(x + 1, y + 1, 1, 1, GOLD);
        c.fill(x + w - 2, y + 1, 1, 1, GOLD);
        c.fill(x + 1, y + h - 2, 1, 1, GOLD);
        c.fill(x + w - 2, y + h - 2, 1, 1, GOLD);
    }

    /**
     * 混合方案：星座板内部交给真实星空着色器 + 平滑法阵环；边框仍是 1px 像素。
     */
    private static void smoothSky(PreviewCanvas c, float time, int x, int y, int w, int h) {
        c.pushClip(x, y, w, h);
        PreviewShader starfield = c.shader("magic_starfield");
        starfield.set("uTime", time);
        starfield.set("uSize", (float) w, (float) h);
        starfield.set("uMouse", 0F, 0F);
        starfield.set("uIntensity", 1.15F);
        c.shaderQuad(starfield, x, y, w, h);
        c.fill(x, y, w, h, 0x44020810);
        c.fillGradientV(x, y, w, 30, 0x77000000, 0x00000000);
        c.fillGradientV(x, y + h - 34, w, 34, 0x00000000, 0x99000000);

        float cx = x + w / 2F, cy = y + h / 2F;
        WheelKit.fillRing(c, cx, cy, 84F, 0.7F, withAlpha(GOLD, 46), 120);
        WheelKit.fillRing(c, cx, cy, 80F, 0.4F, withAlpha(GOLD_HI, 22), 120);
        for (int i = 0; i < 4; i++) {
            float a = (float) (i * TAU / 4.0 + Math.PI / 4.0);
            WheelKit.fillStar(c, cx + (float) Math.cos(a) * 84F,
                    cy + (float) Math.sin(a) * 84F, 2.6F, 1.0F, 4, 0F,
                    withAlpha(GOLD_HI, 100));
        }
        c.popClip();

        // 像素内框压在平滑星空上：结构层仍保持硬边。
        skyRim(c, x, y, w, h);
    }

    private static void skyRim(PreviewCanvas c, int x, int y, int w, int h) {
        c.fill(x, y, w, 1, OUTLINE);
        c.fill(x, y + h - 1, w, 1, OUTLINE);
        c.fill(x, y, 1, h, OUTLINE);
        c.fill(x + w - 1, y, 1, h, OUTLINE);
        c.fill(x + 1, y + 1, w - 2, 1, GOLD_LO);
        c.fill(x + 1, y + 1, 1, h - 2, GOLD_LO);
        c.fill(x + 1, y + h - 2, w - 2, 1, FRAME_LO);
        c.fill(x + w - 2, y + 1, 1, h - 2, FRAME_LO);
        c.fill(x + 1, y + 1, 1, 1, GOLD);
        c.fill(x + w - 2, y + 1, 1, 1, GOLD);
        c.fill(x + 1, y + h - 2, 1, 1, GOLD);
        c.fill(x + w - 2, y + h - 2, 1, 1, GOLD);
    }

    private static void drawConstellation(PreviewCanvas c, float time, int fx, int fy,
                                          Star core, Star[] path, int[][] links,
                                          boolean unstable, boolean hybrid) {
        Star[] stars = new Star[path.length + 1];
        stars[0] = core;
        System.arraycopy(path, 0, stars, 1, path.length);

        for (int i = 0; i < links.length; i++) {
            Star a = stars[links[i][0]];
            Star b = stars[links[i][1]];
            boolean risky = unstable && i == links.length - 1;
            int color = risky ? WARN : b.color();
            if (hybrid) {
                shaderLink(c, time, fx + a.x(), fy + a.y(), fx + b.x(), fy + b.y(), color, risky);
            } else {
                link(c, fx + a.x(), fy + a.y(), fx + b.x(), fy + b.y(), color, risky);
            }
            if (risky) {
                if (hybrid) {
                    shaderPulse(c, time, fx + a.x(), fy + a.y(), fx + b.x(), fy + b.y(),
                            WARN, (time * 0.7F + 0.2F) % 1F, 1.6F);
                    shaderLink(c, time, fx + a.x(), fy + a.y(), fx + b.x(), fy + b.y(), WARN, true);
                } else {
                    pulse(c, time, fx + a.x(), fy + a.y(), fx + b.x(), fy + b.y(), WARN,
                            (time * 0.7F + 0.2F) % 1F);
                }
            }
        }
        Star first = stars[0];
        Star second = stars[1];
        if (hybrid) {
            shaderPulse(c, time, fx + first.x(), fy + first.y(), fx + second.x(), fy + second.y(),
                    GOLD_HI, (time * 0.55F) % 1F, 1.0F);
        } else {
            pulse(c, time, fx + first.x(), fy + first.y(), fx + second.x(), fy + second.y(),
                    GOLD_HI, (time * 0.55F) % 1F);
        }

        for (int i = 0; i < stars.length; i++) {
            Star star = stars[i];
            boolean risky = unstable && i == stars.length - 1;
            node(c, time, fx + star.x(), fy + star.y(), star, i == 0, risky, hybrid);
        }
        // 文字最后画：平滑层永远不会压到标签上。
        for (int i = 0; i < stars.length; i++) {
            Star star = stars[i];
            boolean risky = unstable && i == stars.length - 1;
            label(c, fx + star.x(), fy + star.y(), star, i == 0, risky);
        }
    }

    /** 2px Bresenham 连线 + 两侧抖动光晕（缩小看也读得出星座）。 */
    private static void link(PreviewCanvas c, int x1, int y1, int x2, int y2, int color, boolean risky) {
        int dx = x2 - x1, dy = y2 - y1;
        int ox = Math.abs(dx) >= Math.abs(dy) ? 0 : 1;
        int oy = 1 - ox;
        pixelLine(c, x1, y1, x2, y2, withAlpha(color, risky ? 255 : 235));
        pixelLine(c, x1 + ox, y1 + oy, x2 + ox, y2 + oy,
                withAlpha(color, risky ? 210 : 170));
        for (int pass = 2; pass <= 3; pass++) {
            int alpha = risky ? 90 - pass * 20 : 55 - pass * 15;
            pixelLineDithered(c, x1 + ox * pass, y1 + oy * pass,
                    x2 + ox * pass, y2 + oy * pass, withAlpha(color, alpha), pass);
        }
    }

    /** 平滑连线：项目真实的 magic_constellation（一层细芯 + 一层宽光）。 */
    private static void shaderLink(PreviewCanvas c, float time, int x1, int y1,
                                   int x2, int y2, int color, boolean risky) {
        PreviewShader shader = c.shader("magic_constellation");
        shader.set("uTime", time);
        shader.set("uColor", ((color >> 16) & 0xFF) / 255F,
                ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, 1F);
        shader.set("uIntensity", risky ? 1.6F : 1.05F);
        ribbon(c, shader, x1, y1, x2, y2, 1.1F, 0F, 1F);
        shader.set("uIntensity", risky ? 0.45F : 0.22F);
        ribbon(c, shader, x1, y1, x2, y2, 3.4F, 0F, 1F);
    }

    /** 平滑光脉冲：项目真实的 magic_pulse。 */
    private static void shaderPulse(PreviewCanvas c, float time, int x1, int y1,
                                    int x2, int y2, int color, float progress, float intensity) {
        PreviewShader shader = c.shader("magic_pulse");
        shader.set("uProgress", progress);
        shader.set("uColor", ((color >> 16) & 0xFF) / 255F,
                ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, 1F);
        shader.set("uIntensity", intensity);
        ribbon(c, shader, x1, y1, x2, y2, 1.9F, 0F, 1F);
    }

    /** Position(3) + UV0(2) 的 ribbon；两种魔法 shader 共用。 */
    private static void ribbon(PreviewCanvas c, PreviewShader shader,
                               float x1, float y1, float x2, float y2,
                               float halfWidth, float u0, float u1) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.001F) {
            return;
        }
        float px = -dy / length * halfWidth;
        float py = dx / length * halfWidth;
        float[] data = {
                x1 + px, y1 + py, 0F, u0, 0F,
                x1 - px, y1 - py, 0F, u0, 1F,
                x2 + px, y2 + py, 0F, u1, 0F,
                x2 + px, y2 + py, 0F, u1, 0F,
                x1 - px, y1 - py, 0F, u0, 1F,
                x2 - px, y2 - py, 0F, u1, 1F
        };
        c.shaderMesh(shader, data, data.length, 6, GL11.GL_TRIANGLES);
    }

    private static void pulse(PreviewCanvas c, float time, int x1, int y1, int x2, int y2,
                              int color, float progress) {
        float t = Math.max(0F, Math.min(1F, progress));
        int hx = Math.round(x1 + (x2 - x1) * t);
        int hy = Math.round(y1 + (y2 - y1) * t);
        for (int i = 0; i < 4; i++) {
            float tp = t - i * 0.06F;
            if (tp < 0F) {
                continue;
            }
            int px = Math.round(x1 + (x2 - x1) * tp);
            int py = Math.round(y1 + (y2 - y1) * tp);
            int alpha = 180 - i * 42;
            c.fill(px, py, 1, 1, withAlpha(color, alpha));
            if (i == 0) {
                starPx(c, px, py, color, false);
            }
        }
    }

    /**
     * 星点：平滑辉光 + 像素骨架。
     * 像素部分永远画在平滑辉光之上，缩小看是硬边符号，远看仍有一圈魔法光。
     */
    private static void node(PreviewCanvas c, float time, int cx, int cy,
                             Star star, boolean core, boolean risky, boolean hybrid) {
        int color = risky ? WARN : star.color();
        int r = star.radius();
        if (hybrid) {
            float pulse = 0.95F + 0.10F * (float) Math.sin(time * 2.2F + star.seed() * 0.001F);
            for (int i = 4; i >= 1; i--) {
                WheelKit.fillDisc(c, cx, cy, r + 2F + i * 3.5F,
                        withAlpha(color, Math.round(10 * pulse + (4 - i) * 3)));
            }
        }
        int pulse = (int) (Math.sin(time * 2.2F + star.seed() * 0.001F) * 20F);
        // 低透明度菱形光晕
        diamondRing(c, cx, cy, r + 2, withAlpha(color, 22 + pulse / 4));
        diamondRing(c, cx, cy, r + 4, withAlpha(color, 14 + pulse / 8));
        // 内芯
        diamondFill(c, cx, cy, r - 1, PANEL_DARK);
        diamondRing(c, cx, cy, r, withAlpha(color, core ? 255 : 235));
        if (core) {
            for (int i = 1; i <= r - 1; i++) {
                c.fill(cx + i, cy, 1, 1, withAlpha(color, 200));
                c.fill(cx - i, cy, 1, 1, withAlpha(color, 200));
                c.fill(cx, cy + i, 1, 1, withAlpha(color, 200));
                c.fill(cx, cy - i, 1, 1, withAlpha(color, 200));
            }
            starPx(c, cx, cy, GOLD_HI, true);
        } else {
            glyph(c, cx, cy, Math.max(7, r * 2 - 3), star.seed(), withAlpha(color, 235));
        }
    }

    /** 星点标签单独一层，永远最后画。 */
    private static void label(PreviewCanvas c, int cx, int cy, Star star,
                              boolean core, boolean risky) {
        c.textCentered(star.label(), cx, cy + star.radius() + 4,
                withAlpha(risky ? WARN : (core ? GOLD_HI : TEXT), 225), true);
    }

    // ══════════════════════════════════════════════════════════════
    //  右：推演
    // ══════════════════════════════════════════════════════════════

    private static void preview(PreviewCanvas c, float time, boolean unstable, boolean hybrid) {
        int x = panelX + 444, y = panelY + 50;
        c.text("WEAVE", x + 9, y + 8, withAlpha(GOLD_HI, 235), true);
        c.textRight(unstable ? "UNSTABLE" : "READY", x + PREVIEW_W - 9, y + 8,
                withAlpha(unstable ? WARN : TEAL, 230), false);

        int sealX = x + PREVIEW_W / 2;
        int sealY = y + 54;
        int sealColor = unstable ? WARN : SCHOOL_COLORS[FIRE];
        if (hybrid) {
            smoothSeal(c, sealX, sealY, sealColor, time, unstable);
        } else {
            pixelSeal(c, sealX, sealY, sealColor, time, unstable);
        }

        c.textCenteredScaled(unstable ? "WILD SURGE" : "EMBER LANCE",
                sealX, y + 82F, withAlpha(GOLD_HI, 250), true, 2F);
        if (unstable) {
            c.textCentered("VOID  -  FIELD", sealX, y + 100, withAlpha(SCHOOL_COLORS[5], 235), false);
            c.textCentered("LINGER  -  PIERCE", sealX, y + 111, withAlpha(WARN, 220), false);
        } else {
            c.textCentered("FIRE  -  BOLT", sealX, y + 100, withAlpha(SCHOOL_COLORS[FIRE], 235), false);
            c.textCentered("SPLIT  -  DELAY  -  EMPOWER", sealX, y + 111, withAlpha(TEAL, 220), false);
        }

        divider(c, x + 9, y + 122, PREVIEW_W - 18);
        if (unstable) {
            stat(c, x, y + 130, "MANA", "92");
            stat(c, x, y + 142, "COOLDOWN", "6.5s");
            stat(c, x, y + 154, "ECHOES", "2");
            stat(c, x, y + 166, "FIZZLE", "38%", WARN);
        } else {
            stat(c, x, y + 130, "MANA", "78");
            stat(c, x, y + 142, "COOLDOWN", "4.6s");
            stat(c, x, y + 154, "SHARDS", "3");
            stat(c, x, y + 166, "DELAY", "1.2s");
        }
        divider(c, x + 9, y + 180, PREVIEW_W - 18);
        c.text("EFFECTS", x + 9, y + 186, withAlpha(GOLD, 180), false);
        String effects = unstable
                ? "A collapsing field echoes twice; the last link backfires."
                : "Impact splits into 3 shards that detonate after 1.2s.";
        drawWrapped(c, effects, x + 9, y + 198, PREVIEW_W - 18, withAlpha(TEXT_DIM, 235), 3);

        int stability = unstable ? 22 : 74;
        int stabilityColor = unstable ? WARN : TEAL;
        c.text("STABILITY", x + 9, y + 228, withAlpha(TEXT_FAINT, 220), false);
        c.textRight(stability + "%", x + PREVIEW_W - 9, y + 228,
                withAlpha(stabilityColor, 235), false);
        bar(c, x + 9, y + 238, PREVIEW_W - 18, 4,
                withAlpha(TEXT_FAINT, 50), withAlpha(stabilityColor, 230), stability / 100F);
        c.textRight("COMPLEXITY  " + (unstable ? "7" : "6") + " / 8",
                x + PREVIEW_W - 9, y + 248, withAlpha(TEXT_FAINT, 210), false);
    }

    /** 平滑法阵徽记：SDF 辉光 + 双环 + 旋转星 + 像素符文芯。 */
    private static void smoothSeal(PreviewCanvas c, int cx, int cy, int color,
                                   float time, boolean unstable) {
        for (int i = 4; i >= 1; i--) {
            WheelKit.fillDisc(c, cx, cy, 18F + i * 5F, withAlpha(color, 8 + (4 - i) * 4));
        }
        WheelKit.fillRing(c, cx, cy, 24F, 0.8F, withAlpha(GOLD, 130), 72);
        WheelKit.fillRing(c, cx, cy, 19F, 0.5F, withAlpha(color, 150), 64);
        WheelKit.fillStar(c, cx, cy, 20F, 8F, 8, time * 0.10F, withAlpha(color, 55));
        WheelKit.fillStar(c, cx, cy, 12F, 4.5F, 4, -time * 0.18F, withAlpha(GOLD_HI, 110));
        glyph(c, cx, cy, 13, unstable ? 0x0B01D : 0x0F11E, withAlpha(color, 240));
        c.fill(cx, cy, 1, 1, GOLD_HI);
        for (int i = 0; i < 3; i++) {
            float a = time * 0.8F + i * TAU / 3F;
            int sx = cx + Math.round((float) Math.cos(a) * 29F);
            int sy = cy + Math.round((float) Math.sin(a) * 29F);
            starPx(c, sx, sy, withAlpha(color, 150), false);
        }
    }

    /** 像素法阵徽记：菱形光晕 + 双环 + 八角星臂 + 中心符文。 */
    private static void pixelSeal(PreviewCanvas c, int cx, int cy, int color, float time, boolean unstable) {
        diamondRing(c, cx, cy, 30, withAlpha(color, 26));
        diamondRing(c, cx, cy, 26, withAlpha(color, 20));
        circleRing(c, cx, cy, 22, withAlpha(GOLD, 150));
        circleRing(c, cx, cy, 18, withAlpha(color, 180));
        for (int i = 0; i < 8; i++) {
            float a = (float) (i * TAU / 8.0 + time * 0.15F);
            int ex = cx + Math.round((float) Math.cos(a) * 20F);
            int ey = cy + Math.round((float) Math.sin(a) * 20F);
            pixelLine(c, cx, cy, ex, ey, withAlpha(color, i % 2 == 0 ? 150 : 80));
        }
        for (int i = 0; i < 4; i++) {
            float a = (float) (i * TAU / 4.0 - time * 0.22F);
            int ex = cx + Math.round((float) Math.cos(a) * 24F);
            int ey = cy + Math.round((float) Math.sin(a) * 24F);
            pixelLine(c, cx, cy, ex, ey, withAlpha(GOLD_HI, 90));
        }
        glyph(c, cx, cy, 13, unstable ? 0x0B01D : 0x0F11E, withAlpha(color, 240));
        c.fill(cx, cy, 1, 1, GOLD_HI);
        for (int i = 0; i < 3; i++) {
            float a = time * 0.8F + i * TAU / 3F;
            int sx = cx + Math.round((float) Math.cos(a) * 29F);
            int sy = cy + Math.round((float) Math.sin(a) * 29F);
            starPx(c, sx, sy, withAlpha(color, 140), false);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  底部
    // ══════════════════════════════════════════════════════════════

    private static void footer(PreviewCanvas c, boolean textured, boolean unstable) {
        int x = panelX, y = panelY;
        divider(c, x + 12, y + 314, PANEL_W - 24);
        starPx(c, x + 22, y + 341, GOLD, false);
        c.text(unstable ? "STAR SAND  52 / 60" : "STAR SAND  36 / 60",
                x + 32, y + 336, withAlpha(TEXT_DIM, 235), false);
        c.text(unstable ? "UNSTABLE WEAVE  -  BACKLASH RISK" : "ANY ORDER. ANY SHAPE.  -  THE SKY REMEMBERS",
                x + 172, y + 336, withAlpha(unstable ? WARN : TEXT_FAINT, 220), false);

        button(c, textured, x + 432, y + 332, 62, 20, 0);
        c.textCentered("CLEAR", x + 463, y + 337, withAlpha(TEXT_DIM, 230), false);
        button(c, textured, x + 504, y + 332, 84, 20, unstable ? 2 : 1);
        c.textCentered(unstable ? "FORGE (RISKY)" : "FORGE SPELL",
                x + 546, y + 337, withAlpha(unstable ? WARN : GOLD_HI, 250), true);
    }

    // ══════════════════════════════════════════════════════════════
    //  像素绘制原语
    // ══════════════════════════════════════════════════════════════

    /** state：0 常态、1 主操作（金边）、2 危险、3 禁用。 */
    private static void button(PreviewCanvas c, boolean textured, int x, int y, int w, int h, int state) {
        if (textured) {
            int sourceX = state == 2 ? 20 : (state == 3 ? 60 : 0);
            nineSlice(c, c.texture("textures/gui/spell_weave/widgets.png"),
                    x, y, w, h, sourceX, 0);
            return;
        }
        int hi = state == 2 ? WARN : (state == 1 ? GOLD_HI : GOLD_LO);
        int face = state == 2 ? 0xFF2A1420 : PANEL_LIGHT;
        bevel(c, x, y, w, h, face, hi, FRAME_LO, OUTLINE);
        innerFrame(c, x + 2, y + 2, w - 4, h - 4, state == 2 ? WARN : GOLD_LO);
    }

    private static void nineSlice(PreviewCanvas c, PreviewTexture atlas, int x, int y,
                                  int w, int h, int sourceX, int sourceY) {
        int[] src = {0, 2, 18, 20};
        int[] dx = {0, 2, w - 2, w};
        int[] dy = {0, 2, h - 2, h};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                c.blit(atlas, x + dx[col], y + dy[row],
                        Math.max(1, dx[col + 1] - dx[col]), Math.max(1, dy[row + 1] - dy[row]),
                        (sourceX + src[col]) / 160F, (sourceY + src[row]) / 20F,
                        (sourceX + src[col + 1]) / 160F, (sourceY + src[row + 1]) / 20F);
            }
        }
    }

    private static void bevel(PreviewCanvas c, int x, int y, int w, int h,
                              int face, int hi, int lo, int border) {
        c.fill(x, y, w, h, border);
        c.fill(x + 1, y + 1, w - 2, h - 2, face);
        c.fill(x + 1, y + 1, w - 2, 1, hi);
        c.fill(x + 1, y + 1, 1, h - 2, hi);
        c.fill(x + 1, y + h - 2, w - 2, 1, lo);
        c.fill(x + w - 2, y + 1, 1, h - 2, lo);
    }

    private static void sunken(PreviewCanvas c, int x, int y, int w, int h) {
        bevel(c, x, y, w, h, PANEL_DARK, FRAME_LO, FRAME_HI, OUTLINE);
        innerFrame(c, x + 2, y + 2, w - 4, h - 4, 0xFF1C1430);
    }

    private static void innerFrame(PreviewCanvas c, int x, int y, int w, int h, int color) {
        c.fill(x, y, w, 1, color);
        c.fill(x, y, 1, h, color);
        c.fill(x, y + h - 1, w, 1, color);
        c.fill(x + w - 1, y, 1, h, color);
    }

    private static void divider(PreviewCanvas c, int x, int y, int w) {
        c.fill(x, y, w, 1, withAlpha(GOLD, 40));
    }

    private static void stat(PreviewCanvas c, int x, int y, String label, String value) {
        stat(c, x, y, label, value, TEXT);
    }

    private static void stat(PreviewCanvas c, int x, int y, String label, String value, int valueColor) {
        c.text(label, x + 9, y, withAlpha(TEXT_FAINT, 225), false);
        c.textRight(value, x + PREVIEW_W - 9, y, withAlpha(valueColor, 240), false);
    }

    private static void bar(PreviewCanvas c, int x, int y, int w, int h,
                            int track, int fill, float ratio) {
        c.fill(x, y, w, h, track);
        c.fill(x, y, Math.max(0, Math.round(w * ratio)), h, fill);
        c.fill(x, y, w, 1, withAlpha(0xFFFFFFFF, 30));
    }

    private static void drawWrapped(PreviewCanvas c, String text, int x, int y, int width,
                                    int color, int maxLines) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = y;
        int lines = 0;
        for (String word : words) {
            String probe = line.isEmpty() ? word : line + " " + word;
            if (c.textWidth(probe) > width && !line.isEmpty()) {
                c.text(line.toString(), x, lineY, color, false);
                lines++;
                lineY += 10;
                line = new StringBuilder(word);
                if (lines >= maxLines) {
                    return;
                }
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (!line.isEmpty() && lines < maxLines) {
            c.text(line.toString(), x, lineY, color, false);
        }
    }

    /** 1px Bresenham 直线。 */
    private static void pixelLine(PreviewCanvas c, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            c.fill(x0, y0, 1, 1, color);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** 隔像素的 1px 线，用于光晕。 */
    private static void pixelLineDithered(PreviewCanvas c, int x0, int y0, int x1, int y1,
                                          int color, int phase) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int step = 0;
        while (true) {
            if ((step + phase) % 2 == 0) {
                c.fill(x0, y0, 1, 1, color);
            }
            step++;
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** 菱形环（|dx|+|dy| = r）。 */
    private static void diamondRing(PreviewCanvas c, int cx, int cy, int r, int color) {
        for (int dx = -r; dx <= r; dx++) {
            int dy = r - Math.abs(dx);
            c.fill(cx + dx, cy + dy, 1, 1, color);
            if (dy != 0) {
                c.fill(cx + dx, cy - dy, 1, 1, color);
            }
        }
    }

    private static void diamondFill(PreviewCanvas c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            c.fill(cx - half, cy + dy, half * 2 + 1, 1, color);
        }
    }

    /** 像素圆环（用于法阵）；带 1px 量化。 */
    private static void circleRing(PreviewCanvas c, int cx, int cy, int r, int color) {
        int steps = Math.max(24, r * 4);
        for (int i = 0; i < steps; i++) {
            float a = (float) (i * TAU / steps);
            int px = cx + Math.round((float) Math.cos(a) * r);
            int py = cy + Math.round((float) Math.sin(a) * r);
            c.fill(px, py, 1, 1, color);
        }
    }

    /** 5px 像素星：中心 + 四臂（big 时再加四角点）。 */
    private static void starPx(PreviewCanvas c, int x, int y, int color, boolean big) {
        c.fill(x, y, 1, 1, color);
        c.fill(x - 1, y, 1, 1, color);
        c.fill(x + 1, y, 1, 1, color);
        c.fill(x, y - 1, 1, 1, color);
        c.fill(x, y + 1, 1, 1, color);
        if (big) {
            c.fill(x - 2, y, 1, 1, color);
            c.fill(x + 2, y, 1, 1, color);
            c.fill(x, y - 2, 1, 1, color);
            c.fill(x, y + 2, 1, 1, color);
        }
    }

    /** 确定性像素符文：9x9 或 11x11，左右或上下对称。 */
    private static void glyph(PreviewCanvas c, int cx, int cy, int size, int seed, int color) {
        if (size % 2 == 0) {
            size++;
        }
        int half = size / 2;
        for (int dy = -half; dy <= half; dy++) {
            for (int dx = 0; dx <= half; dx++) {
                int h = hash(dx + 11, dy + 17 + seed * 7);
                if ((h & 0x7) < 3) {
                    c.fill(cx + dx, cy + dy, 1, 1, color);
                    if (dx != 0) {
                        c.fill(cx - dx, cy + dy, 1, 1, color);
                    }
                }
            }
        }
        // 中心竖线，保证符文有"骨架"
        c.fill(cx, cy - half, 1, 1, color);
        c.fill(cx, cy + half, 1, 1, color);
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    // ══════════════════════════════════════════════════════════════
    //  数据工具
    // ══════════════════════════════════════════════════════════════

    private static Sigil[] sigils(String[] labels, int[] colors) {
        Sigil[] out = new Sigil[labels.length];
        for (int i = 0; i < labels.length; i++) {
            out[i] = new Sigil(labels[i], colors[i], 0x1000 + i * 0x37);
        }
        return out;
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
