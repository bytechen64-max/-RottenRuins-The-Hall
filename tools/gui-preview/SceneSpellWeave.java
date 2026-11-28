import org.lwjgl.opengl.GL11;

/**
 * 法术编织台（Star Loom）设计稿 —— 把"合成"从材料列表改成"画星图"。
 *
 * <p>核心交互：左侧是可重复使用的<b>符印</b>（学派 / 形态 / 修饰），中间是一片<b>夜空黑板</b>，
 * 玩家把符印拖成一颗颗星并连线；连出来的星座就是一个法术。右侧实时推演出法术名、数值、
 * 效果与稳定性，底部只有一种燃料（星砂 / 势能），不再摆 3 个材料槽。</p>
 *
 * <p>这张图用项目自己的 {@code magic_starfield}（夜空）与 {@code magic_constellation}
 * （连线光带）、{@code magic_pulse}（沿连线跑的光点）三支 GUI 着色器，加上 SDF 星点 / 符文，
 * 所以和实机魔法星图是同一套视觉语言。产出两张：稳定编织 {@code spell_weave.png}、
 * 不稳定实验 {@code spell_weave_unstable.png}。</p>
 *
 * <p>设计说明见 {@code docs/proposals/spell-weave/README.md}。</p>
 */
public final class SceneSpellWeave {

    // ══════════════════════════════════════════════════════════════
    //  调色板 / 布局
    // ══════════════════════════════════════════════════════════════

    private static final int DEEP = 0xFF05030C;
    private static final int PANEL = 0xF00B0716;
    private static final int PANEL_DARK = 0xFF070410;
    private static final int GOLD = 0xFFE7C46A;
    private static final int GOLD_BRIGHT = 0xFFFFE49A;
    private static final int GOLD_DIM = 0xFFB08A4A;
    private static final int TEXT = 0xFFE8DCC6;
    private static final int TEXT_DIM = 0xFF9A8FA8;
    private static final int TEXT_FAINT = 0xFF61587A;
    private static final int TEAL = 0xFF6FD8C0;
    private static final int WARN = 0xFFFF6B5A;
    private static final int GREEN = 0xFF7FE0A0;

    private static final int PANEL_X = 16, PANEL_Y = 18, PANEL_W = 608, PANEL_H = 364;
    private static final int TRAY_X = 28, TRAY_Y = 68, TRAY_W = 152, TRAY_H = 258;
    private static final int BOARD_X = 192, BOARD_Y = 68, BOARD_W = 256, BOARD_H = 258;
    private static final int FIELD_X = 198, FIELD_Y = 88, FIELD_W = 244, FIELD_H = 218;
    private static final int PREVIEW_X = 460, PREVIEW_Y = 68, PREVIEW_W = 152, PREVIEW_H = 258;

    private static final int CORE_X = 320, CORE_Y = 276;
    private static final float TAU = (float) (Math.PI * 2.0);

    private static final int[] SCHOOL_COLORS = {
            0xFFF4AC, 0xFF7843, 0xFF53BEFF, 0xFF78D98A, 0xFFBE8BDA, 0xFF8B85FF, 0xFFE7A7EF
    };
    private static final String[] SCHOOL_NAMES = {
            "LIGHT", "FIRE", "WATER", "NATURE", "DEATH", "VOID", "MYSTERY"
    };
    private static final int FIRE = 1;

    /** 符印：名称、颜色、符文种子（runeGlyph 用）。 */
    private record Sigil(String label, int color, int seed) {
    }

    private static final Sigil[] SCHOOL_SIGILS = sigils(
            new String[]{"LIGHT", "FIRE", "WATER", "NATURE", "DEATH", "VOID", "MYSTERY"},
            SCHOOL_COLORS);
    private static final Sigil[] FORM_SIGILS = {
            new Sigil("BOLT", GOLD, 0x0B017),
            new Sigil("BEAM", GOLD_BRIGHT, 0x0BEA7),
            new Sigil("FIELD", GOLD, 0x0F1E1D),
            new Sigil("AURA", GOLD_BRIGHT, 0x0A0BA),
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
    /** CHAIN 在实验态仍是未发现符印。 */
    private static final int LOCKED_SIGIL = 2;

    /** 星图上的一颗星。 */
    private record Star(float x, float y, float radius, String label, int color, int seed) {
    }

    private static final Star STABLE_CORE =
            new Star(CORE_X, CORE_Y, 15F, "SEED", GOLD_BRIGHT, 0x0C0DE);
    private static final Star[] STABLE_PATH = {
            new Star(284F, 238F, 12F, "FIRE", SCHOOL_COLORS[FIRE], 0x0F11E),
            new Star(352F, 228F, 12F, "BOLT", GOLD_BRIGHT, 0x0B017),
            new Star(398F, 186F, 11F, "SPLIT", TEAL, 0x05B117),
            new Star(346F, 158F, 11F, "DELAY", TEAL, 0x0DE1A7),
            new Star(282F, 180F, 11F, "EMPOWER", GOLD, 0x0E3B00)
    };
    /** 稳定星图的连线（下标针对 [核心, 路径...] 的拼接表）。 */
    private static final int[][] STABLE_LINKS = {
            {0, 1}, {1, 2}, {2, 4}, {4, 5}, {2, 3}
    };

    private static final Star UNSTABLE_CORE =
            new Star(CORE_X, CORE_Y, 15F, "SEED", GOLD_BRIGHT, 0x0C0DE);
    private static final Star[] UNSTABLE_PATH = {
            new Star(286F, 232F, 12F, "VOID", SCHOOL_COLORS[5], 0x0B01D),
            new Star(356F, 236F, 12F, "FIELD", GOLD, 0x0F1E1D),
            new Star(402F, 188F, 11F, "LINGER", TEAL, 0x0F1A6E),
            new Star(344F, 156F, 11F, "PIERCE", TEAL, 0x0F1E4C),
            new Star(280F, 170F, 11F, "EMPOWER", GOLD, 0x0E3B00)
    };
    private static final int[][] UNSTABLE_LINKS = {
            {0, 1}, {1, 2}, {2, 3}, {1, 4}
    };

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) throws Exception {
        c.shader("sdf_ui");
        weave(c, time, false);
        c.save("spell_weave");
        weave(c, time, true);
        c.save("spell_weave_unstable");
        weave(c, time, false);
    }

    private static void weave(PreviewCanvas c, float time, boolean unstable) {
        background(c, time);
        panel(c, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        header(c, time, unstable);
        tray(c, time);
        board(c, time, unstable);
        preview(c, time, unstable);
        footer(c, time, unstable);
    }

    // ══════════════════════════════════════════════════════════════
    //  背景 / 外框 / 头部
    // ══════════════════════════════════════════════════════════════

    private static void background(PreviewCanvas c, float time) {
        c.clear(DEEP);
        PreviewShader starfield = c.shader("magic_starfield");
        starfield.set("uTime", time);
        starfield.set("uSize", (float) c.width, (float) c.height);
        starfield.set("uMouse", 0F, 0F);
        starfield.set("uIntensity", 1.1F);
        c.shaderQuad(starfield, 0, 0, c.width, c.height);
        c.fill(0, 0, c.width, c.height, 0x66030A10);

        // 上下暗角，让面板从星云里浮出来。
        c.fillGradientV(0, 0, c.width, 80, 0x99000000, 0x00000000);
        c.fillGradientV(0, c.height - 84, c.width, 84, 0x00000000, 0x99000000);
        for (int i = 0; i < 20; i++) {
            int alpha = (20 - i) * 3;
            c.fill(i * 2, 0, 2, c.height, alpha << 24);
            c.fill(c.width - (i + 1) * 2, 0, 2, c.height, alpha << 24);
        }
        corners(c, 8, withAlpha(GOLD, 70));
    }

    /** 深色星术面板：双线金边 + 四角星。 */
    private static void panel(PreviewCanvas c, int x, int y, int w, int h) {
        c.fill(x + 4, y + 5, w, h, 0x66000000);
        SdfShapes.roundRect(c, x, y, w, h, 4F, PANEL, PANEL_DARK, withAlpha(GOLD, 120), 1.0F);
        SdfShapes.roundRect(c, x + 3, y + 3, w - 6, h - 6, 3F,
                0x00000000, 0x00000000, withAlpha(GOLD, 35), 0.6F);
        // 四角星饰
        int color = withAlpha(GOLD_BRIGHT, 140);
        WheelKit.fillStar(c, x + 9F, y + 9F, 3.0F, 1.0F, 4, 0F, color);
        WheelKit.fillStar(c, x + w - 9F, y + 9F, 3.0F, 1.0F, 4, 0F, color);
        WheelKit.fillStar(c, x + 9F, y + h - 9F, 3.0F, 1.0F, 4, 0F, color);
        WheelKit.fillStar(c, x + w - 9F, y + h - 9F, 3.0F, 1.0F, 4, 0F, color);
    }

    private static void corners(PreviewCanvas c, int margin, int color) {
        int arm = 20;
        WheelKit.line(c, margin, margin + 6, margin, margin, 0.6F, color);
        WheelKit.line(c, margin, margin, margin + arm, margin, 0.6F, color);
        WheelKit.line(c, c.width - margin - arm, margin, c.width - margin, margin, 0.6F, color);
        WheelKit.line(c, c.width - margin, margin, c.width - margin, margin + 6, 0.6F, color);
        WheelKit.line(c, margin, c.height - margin - 6, margin, c.height - margin, 0.6F, color);
        WheelKit.line(c, margin, c.height - margin, margin + arm, c.height - margin, 0.6F, color);
        WheelKit.line(c, c.width - margin - arm, c.height - margin, c.width - margin,
                c.height - margin, 0.6F, color);
        WheelKit.line(c, c.width - margin, c.height - margin - 6, c.width - margin,
                c.height - margin, 0.6F, color);
    }

    private static void header(PreviewCanvas c, float time, boolean unstable) {
        c.text("ROTTEN RUINS", 30, 26, withAlpha(GOLD, 130), false);
        c.textRight("ESC  CLOSE", 598, 26, withAlpha(TEXT_FAINT, 190), false);

        c.textCenteredScaled("STAR LOOM", c.width / 2F, 22, withAlpha(GOLD_BRIGHT, 250), true, 2F);
        c.textCentered("WEAVE A CONSTELLATION  -  FORGE A SPELL", c.width / 2, 44,
                withAlpha(GOLD, 180), false);

        // 标题下的星线（magic_constellation 用于面板装饰也成立）。
        PreviewShader line = c.shader("magic_constellation");
        line.set("uTime", time);
        line.set("uColor", 0.90F, 0.77F, 0.42F, 1F);
        line.set("uIntensity", 0.7F);
        ribbon(c, line, 250F, 56F, 320F, 56F, 1.0F, 0F, 1.1F);
        ribbon(c, line, 320F, 56F, 390F, 56F, 1.0F, 0F, 1.1F);
        WheelKit.fillStar(c, 250F, 56F, 3.2F, 1.1F, 4, 0F, withAlpha(GOLD_BRIGHT, 180));
        WheelKit.fillStar(c, 390F, 56F, 3.2F, 1.1F, 4, 0F, withAlpha(GOLD_BRIGHT, 180));
        WheelKit.fillStar(c, 320F, 56F, 4.2F, 1.4F, 4, 0.2F, withAlpha(GOLD_BRIGHT, 230));
    }

    // ══════════════════════════════════════════════════════════════
    //  左：符印托盘
    // ══════════════════════════════════════════════════════════════

    private static void tray(PreviewCanvas c, float time) {
        innerPanel(c, TRAY_X, TRAY_Y, TRAY_W, TRAY_H);

        c.text("SIGIL TRAY", TRAY_X + 9, TRAY_Y + 8, withAlpha(GOLD_BRIGHT, 235), true);
        c.textRight("17 / 18", TRAY_X + TRAY_W - 9, TRAY_Y + 8,
                withAlpha(TEXT_FAINT, 210), false);

        category(c, "SCHOOLS", TRAY_Y + 24);
        tokenRow(c, time, SCHOOL_SIGILS, 4, TRAY_X + 9, TRAY_Y + 40);
        tokenRow(c, time, SCHOOL_SIGILS, 3, TRAY_X + 9, TRAY_Y + 66, 4);

        category(c, "FORMS", TRAY_Y + 94);
        tokenRow(c, time, FORM_SIGILS, 4, TRAY_X + 9, TRAY_Y + 110);
        tokenRow(c, time, FORM_SIGILS, 1, TRAY_X + 9, TRAY_Y + 136, 4);

        category(c, "MODIFIERS", TRAY_Y + 164);
        tokenRow(c, time, MODIFIER_SIGILS, 4, TRAY_X + 9, TRAY_Y + 180, 0, LOCKED_SIGIL);
        tokenRow(c, time, MODIFIER_SIGILS, 2, TRAY_X + 9, TRAY_Y + 206, 4, LOCKED_SIGIL);

        c.textCentered("DRAG A SIGIL INTO THE SKY", TRAY_X + TRAY_W / 2, TRAY_Y + TRAY_H - 20,
                withAlpha(TEXT_FAINT, 220), false);
    }

    private static void category(PreviewCanvas c, String label, int y) {
        c.text(label, TRAY_X + 9, y, withAlpha(GOLD, 170), false);
        c.fill(TRAY_X + 9, y + 10, TRAY_W - 18, 1, withAlpha(GOLD, 30));
    }

    private static void tokenRow(PreviewCanvas c, float time, Sigil[] sigils, int count,
                                 int x, int y) {
        tokenRow(c, time, sigils, count, x, y, 0, -1);
    }

    private static void tokenRow(PreviewCanvas c, float time, Sigil[] sigils, int count,
                                 int x, int y, int from) {
        tokenRow(c, time, sigils, count, x, y, from, -1);
    }

    private static void tokenRow(PreviewCanvas c, float time, Sigil[] sigils, int count,
                                 int x, int y, int from, int locked) {
        for (int i = 0; i < count; i++) {
            int index = from + i;
            Sigil sigil = sigils[index];
            boolean lockedToken = index == locked;
            float cx = x + 11F + i * 32F;
            float cy = y + 11F;
            int color = lockedToken ? TEXT_FAINT : sigil.color();
            // 选中态示例：SPLIT 是"已拿在手上"的符印。
            boolean selected = !lockedToken && "SPLIT".equals(sigil.label());
            if (selected) {
                WheelKit.fillRing(c, cx, cy, 12.4F, 0.8F, withAlpha(GOLD_BRIGHT, 190), 48);
                WheelKit.fillStar(c, cx, cy - 15.5F, 2.4F, 0.9F, 4, 0F, withAlpha(GOLD_BRIGHT, 190));
            }
            glow(c, cx, cy, 11F, color, 0.12F);
            WheelKit.fillDisc(c, cx, cy, 8.4F, withAlpha(PANEL_DARK, 240));
            WheelKit.fillRing(c, cx, cy, 8.0F, 1.0F, withAlpha(color, selected ? 245 : 165), 32);
            WheelKit.runeGlyph(c, cx, cy, 8.6F, sigil.seed(),
                    withAlpha(color, selected ? 250 : 190), 0.85F);
            if (lockedToken) {
                lockGlyph(c, cx + 4.5F, cy + 3.5F, withAlpha(TEXT_FAINT, 230));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  中：夜空星图
    // ══════════════════════════════════════════════════════════════

    private static void board(PreviewCanvas c, float time, boolean unstable) {
        innerPanel(c, BOARD_X, BOARD_Y, BOARD_W, BOARD_H);
        c.text("CONSTELLATION", BOARD_X + 9, BOARD_Y + 8, withAlpha(GOLD_BRIGHT, 235), true);
        c.textRight("24 NODES", BOARD_X + BOARD_W - 9, BOARD_Y + 8,
                withAlpha(TEXT_FAINT, 210), false);

        c.pushClip(FIELD_X, FIELD_Y, FIELD_W, FIELD_H);
        PreviewShader starfield = c.shader("magic_starfield");
        starfield.set("uTime", time);
        starfield.set("uSize", (float) FIELD_W, (float) FIELD_H);
        starfield.set("uMouse", 0F, 0F);
        starfield.set("uIntensity", 1.0F);
        c.shaderQuad(starfield, FIELD_X, FIELD_Y, FIELD_W, FIELD_H);
        c.fill(FIELD_X, FIELD_Y, FIELD_W, FIELD_H, 0x3D030812);
        c.fillGradientV(FIELD_X, FIELD_Y, FIELD_W, 26, 0x88000000, 0x00000000);
        c.fillGradientV(FIELD_X, FIELD_Y + FIELD_H - 30, FIELD_W, 30, 0x00000000, 0x99000000);
        c.popClip();
        // 区域边框 + 四角小星
        c.fill(FIELD_X - 1, FIELD_Y - 1, FIELD_W + 2, 1, withAlpha(GOLD, 95));
        c.fill(FIELD_X - 1, FIELD_Y + FIELD_H, FIELD_W + 2, 1, withAlpha(GOLD, 70));
        c.fill(FIELD_X - 1, FIELD_Y - 1, 1, FIELD_H + 2, withAlpha(GOLD, 95));
        c.fill(FIELD_X + FIELD_W, FIELD_Y - 1, 1, FIELD_H + 2, withAlpha(GOLD, 70));

        // 未使用的暗星散在四周（两颗用 4 角星，其余是点）。
        float[][] dust = {
                {226F, 116F}, {252F, 284F}, {416F, 128F}, {428F, 274F},
                {232F, 198F}, {410F, 258F}, {320F, 112F}, {262F, 150F}, {384F, 292F}
        };
        for (int i = 0; i < dust.length; i++) {
            float x = dust[i][0], y = dust[i][1];
            if (i % 3 == 0) {
                WheelKit.fillStar(c, x, y, 3.0F, 1.0F, 4, 0F, withAlpha(GOLD, 70));
            } else {
                WheelKit.fillDisc(c, x, y, 1.2F, withAlpha(TEXT_FAINT, 110));
            }
        }

        if (unstable) {
            drawConstellation(c, time, UNSTABLE_CORE, UNSTABLE_PATH, UNSTABLE_LINKS, true);
        } else {
            drawConstellation(c, time, STABLE_CORE, STABLE_PATH, STABLE_LINKS, false);
        }

        c.text("6 SIGILS / 12 NODES", BOARD_X + 9, BOARD_Y + BOARD_H - 18,
                withAlpha(TEXT_FAINT, 220), false);
        c.textRight(unstable ? "WILD SHAPE" : "ANY ORDER. ANY SHAPE.",
                BOARD_X + BOARD_W - 9, BOARD_Y + BOARD_H - 18,
                withAlpha(unstable ? WARN : GOLD_DIM, 200), false);
    }

    /** 画一个星座：连线、沿线的光脉冲、星点，最后是标签。 */
    private static void drawConstellation(PreviewCanvas c, float time, Star core, Star[] path,
                                          int[][] links, boolean unstable) {
        Star[] stars = new Star[path.length + 1];
        stars[0] = core;
        System.arraycopy(path, 0, stars, 1, path.length);

        for (int i = 0; i < links.length; i++) {
            Star a = stars[links[i][0]];
            Star b = stars[links[i][1]];
            boolean risky = unstable && i == links.length - 1;
            int color = risky ? WARN : b.color();
            constellationLine(c, time, a.x(), a.y(), b.x(), b.y(), color, risky ? 1.6F : 1.25F);
            if (risky) {
                // 不稳定连接：额外画一段红色脉冲 + 一层红色光雾，视觉上"发烫"。
                pulse(c, time, a.x(), a.y(), b.x(), b.y(), WARN, (time * 0.7F + 0.2F) % 1F, 1.8F);
                constellationLine(c, time, a.x(), a.y(), b.x(), b.y(), WARN, 2.2F);
            }
        }

        // 一条主链上跑光点，示意"势流"。
        Star pulseA = stars[0];
        Star pulseB = stars[1];
        pulse(c, time, pulseA.x(), pulseA.y(), pulseB.x(), pulseB.y(), GOLD_BRIGHT,
                (time * 0.55F) % 1F, 1.0F);

        // 先画全部星点，再统一压一层近黑衬底，让标签不会被线穿。
        for (int i = 0; i < stars.length; i++) {
            Star star = stars[i];
            boolean risky = unstable && i == stars.length - 1;
            node(c, time, star, i == 0, risky);
        }
    }

    private static void node(PreviewCanvas c, float time, Star star, boolean core, boolean risky) {
        int color = risky ? WARN : star.color();
        float pulse = 0.9F + 0.10F * (float) Math.sin(time * 2.2F + star.seed() * 0.001F);
        glow(c, star.x(), star.y(), star.radius() + 8F, color, 0.16F * pulse);
        WheelKit.fillDisc(c, star.x(), star.y(), star.radius(), withAlpha(PANEL_DARK, 250));
        WheelKit.fillRing(c, star.x(), star.y(), star.radius(), 1.0F,
                withAlpha(color, core ? 255 : 220), 48);
        if (core) {
            WheelKit.fillStar(c, star.x(), star.y(), star.radius() * 0.92F,
                    star.radius() * 0.40F, 4, time * 0.1F, withAlpha(color, 220));
            WheelKit.fillStar(c, star.x(), star.y(), star.radius() * 0.55F,
                    star.radius() * 0.22F, 4, -time * 0.16F, withAlpha(0xFFFFFFFF, 150));
        } else {
            WheelKit.runeGlyph(c, star.x(), star.y(), star.radius() * 1.15F,
                    star.seed(), withAlpha(color, 235), 0.85F);
        }
        c.textCentered(star.label(), Math.round(star.x()), Math.round(star.y() + star.radius() + 3F),
                withAlpha(risky ? WARN : (core ? GOLD_BRIGHT : TEXT), 215), true);
    }

    // ══════════════════════════════════════════════════════════════
    //  右：实时推演
    // ══════════════════════════════════════════════════════════════

    private static void preview(PreviewCanvas c, float time, boolean unstable) {
        innerPanel(c, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H);
        c.text("WEAVE", PREVIEW_X + 9, PREVIEW_Y + 8, withAlpha(GOLD_BRIGHT, 235), true);
        c.textRight(unstable ? "UNSTABLE" : "READY", PREVIEW_X + PREVIEW_W - 9, PREVIEW_Y + 8,
                withAlpha(unstable ? WARN : TEAL, 220), false);

        // 生成的"织法徽记"：外圈 + 旋转双星 + 学派符文。
        float sealX = PREVIEW_X + PREVIEW_W / 2F;
        float sealY = PREVIEW_Y + 54F;
        int sealColor = unstable ? WARN : SCHOOL_COLORS[FIRE];
        glow(c, sealX, sealY, 26F, sealColor, 0.22F);
        WheelKit.fillRing(c, sealX, sealY, 22F, 0.8F, withAlpha(GOLD, 110), 64);
        WheelKit.fillRing(c, sealX, sealY, 18F, 0.5F, withAlpha(sealColor, 120), 56);
        WheelKit.fillStar(c, sealX, sealY, 18F, 7F, 8, time * 0.10F, withAlpha(sealColor, 60));
        WheelKit.fillStar(c, sealX, sealY, 12F, 4.5F, 4, -time * 0.18F, withAlpha(GOLD_BRIGHT, 120));
        WheelKit.runeGlyph(c, sealX, sealY, 20F, unstable ? 0x0B01D : 0x0F11E,
                withAlpha(sealColor, 230), 0.8F);
        WheelKit.fillDisc(c, sealX, sealY, 2.2F, withAlpha(GOLD_BRIGHT, 230));
        for (int i = 0; i < 3; i++) {
            float a = time * 0.8F + i * TAU / 3F;
            WheelKit.fillStar(c, sealX + (float) Math.cos(a) * 27F,
                    sealY + (float) Math.sin(a) * 27F, 2.2F, 0.8F, 4, a,
                    withAlpha(sealColor, 120));
        }

        c.textCenteredScaled(unstable ? "WILD SURGE" : "EMBER LANCE",
                sealX, PREVIEW_Y + 82F, withAlpha(GOLD_BRIGHT, 250), true, 2F);
        if (unstable) {
            c.textCentered("VOID  -  FIELD", Math.round(sealX), PREVIEW_Y + 100,
                    withAlpha(SCHOOL_COLORS[5], 235), false);
            c.textCentered("LINGER  -  PIERCE", Math.round(sealX), PREVIEW_Y + 111,
                    withAlpha(WARN, 200), false);
        } else {
            c.textCentered("FIRE  -  BOLT", Math.round(sealX), PREVIEW_Y + 100,
                    withAlpha(SCHOOL_COLORS[FIRE], 235), false);
            c.textCentered("SPLIT  -  DELAY  -  EMPOWER", Math.round(sealX), PREVIEW_Y + 111,
                    withAlpha(TEAL, 200), false);
        }

        divider(c, PREVIEW_X + 9, PREVIEW_Y + 122, PREVIEW_W - 18);

        if (unstable) {
            stat(c, PREVIEW_Y + 130, "MANA", "92");
            stat(c, PREVIEW_Y + 142, "COOLDOWN", "6.5s");
            stat(c, PREVIEW_Y + 154, "ECHOES", "2");
            stat(c, PREVIEW_Y + 166, "FIZZLE", "38%", WARN);
        } else {
            stat(c, PREVIEW_Y + 130, "MANA", "78");
            stat(c, PREVIEW_Y + 142, "COOLDOWN", "4.6s");
            stat(c, PREVIEW_Y + 154, "SHARDS", "3");
            stat(c, PREVIEW_Y + 166, "DELAY", "1.2s");
        }

        divider(c, PREVIEW_X + 9, PREVIEW_Y + 180, PREVIEW_W - 18);

        c.text("EFFECTS", PREVIEW_X + 9, PREVIEW_Y + 186, withAlpha(GOLD, 170), false);
        String effects = unstable
                ? "A collapsing field echoes twice; the last link backfires."
                : "Impact splits into 3 shards that detonate after 1.2s.";
        WheelKit.drawWrapped(c, effects, PREVIEW_X + 9, PREVIEW_Y + 198, PREVIEW_W - 18,
                withAlpha(TEXT_DIM, 235), 3);

        // 稳定性 / 复杂度
        int stability = unstable ? 22 : 74;
        int stabilityColor = unstable ? WARN : TEAL;
        c.text("STABILITY", PREVIEW_X + 9, PREVIEW_Y + 228, withAlpha(TEXT_FAINT, 220), false);
        c.textRight(stability + "%", PREVIEW_X + PREVIEW_W - 9, PREVIEW_Y + 228,
                withAlpha(stabilityColor, 235), false);
        bar(c, PREVIEW_X + 9, PREVIEW_Y + 238, PREVIEW_W - 18, 5,
                withAlpha(TEXT_FAINT, 45), withAlpha(stabilityColor, 220), stability / 100F);
        c.textRight("COMPLEXITY  " + (unstable ? "7" : "6") + " / 8",
                PREVIEW_X + PREVIEW_W - 9, PREVIEW_Y + 248, withAlpha(TEXT_FAINT, 200), false);
    }

    // ══════════════════════════════════════════════════════════════
    //  底部：燃料 / 操作
    // ══════════════════════════════════════════════════════════════

    private static void footer(PreviewCanvas c, float time, boolean unstable) {
        divider(c, PANEL_X + 12, PANEL_Y + PANEL_H - 40, PANEL_W - 24);
        WheelKit.fillStar(c, PANEL_X + 20, PANEL_Y + PANEL_H - 24, 3.6F, 1.2F, 4, 0F,
                withAlpha(GOLD, 210));
        c.text(unstable ? "STAR SAND  52 / 60" : "STAR SAND  36 / 60",
                PANEL_X + 30, PANEL_Y + PANEL_H - 29, withAlpha(TEXT_DIM, 235), false);
        c.text(unstable ? "UNSTABLE WEAVE  -  BACKLASH RISK" : "ANY ORDER. ANY SHAPE.  -  THE SKY REMEMBERS",
                PANEL_X + 170, PANEL_Y + PANEL_H - 29,
                withAlpha(unstable ? WARN : TEXT_FAINT, 220), false);

        ghostButton(c, PANEL_X + PANEL_W - 168, PANEL_Y + PANEL_H - 34, 62, 20, "CLEAR");
        forgeButton(c, PANEL_X + PANEL_W - 96, PANEL_Y + PANEL_H - 34, 84, 20,
                unstable ? "FORGE (RISKY)" : "FORGE SPELL", unstable);
    }

    private static void ghostButton(PreviewCanvas c, int x, int y, int w, int h, String label) {
        if (SdfShapes.roundRect(c, x, y, w, h, 3F, 0x00000000, 0x00000000,
                withAlpha(GOLD, 90), 0.8F)) {
            c.textCentered(label, x + w / 2, y + (h - 9) / 2 + 1, withAlpha(TEXT_DIM, 220), false);
        }
    }

    private static void forgeButton(PreviewCanvas c, int x, int y, int w, int h,
                                    String label, boolean risky) {
        int color = risky ? WARN : GOLD_BRIGHT;
        SdfShapes.roundRect(c, x, y, w, h, 3F, 0x33E7C46A, 0x22C9A227,
                withAlpha(color, 200), 0.9F);
        c.fill(x + 3, y + 1, w - 6, 1, withAlpha(color, 100));
        c.textCentered(label, x + w / 2, y + (h - 9) / 2 + 1, withAlpha(color, 245), true);
    }

    // ══════════════════════════════════════════════════════════════
    //  星图绘制：连线 / 光脉冲 / 辉光
    // ══════════════════════════════════════════════════════════════

    /** 用项目真实的 magic_constellation 着色器画一条发光连线（一次 quad 一条）。 */
    private static void constellationLine(PreviewCanvas c, float time,
                                          float x1, float y1, float x2, float y2,
                                          int color, float alpha) {
        PreviewShader shader = c.shader("magic_constellation");
        shader.set("uTime", time);
        shader.set("uColor", ((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F,
                (color & 0xFF) / 255F, 1F);
        shader.set("uIntensity", 0.85F * alpha);
        ribbon(c, shader, x1, y1, x2, y2, 1.15F, 0F, 1F);
        // 第二层很淡的光晕，让细线在星云上仍然可读。
        shader.set("uIntensity", 0.22F * alpha);
        ribbon(c, shader, x1, y1, x2, y2, 3.2F, 0F, 1F);
    }

    /** 用 magic_pulse 画一个沿连线跑动的光点。 */
    private static void pulse(PreviewCanvas c, float time, float x1, float y1,
                              float x2, float y2, int color, float progress, float alpha) {
        PreviewShader shader = c.shader("magic_pulse");
        shader.set("uProgress", progress);
        shader.set("uColor", ((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F,
                (color & 0xFF) / 255F, 1F);
        shader.set("uIntensity", alpha);
        ribbon(c, shader, x1, y1, x2, y2, 1.9F, 0F, 1F);
    }

    /**
     * 生成一条沿 (x1,y1)-(x2,y2) 的 ribbon（两个三角），UV0.x 是长度方向、UV0.y 横向。
     * 顶点布局按 magic_constellation/magic_pulse 的 json：Position(3) + UV0(2)。
     */
    private static void ribbon(PreviewCanvas c, PreviewShader shader,
                               float x1, float y1, float x2, float y2,
                               float halfWidth, float u0, float u1) {
        float dx = x2 - x1;
        float dy = y2 - y1;
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

    /** 叠三层低透明度圆盘做出柔光。 */
    private static void glow(PreviewCanvas c, float cx, float cy, float radius,
                             int color, float strength) {
        WheelKit.fillDisc(c, cx, cy, radius, withAlpha(color, Math.round(38 * strength)));
        WheelKit.fillDisc(c, cx, cy, radius * 0.68F, withAlpha(color, Math.round(56 * strength)));
        WheelKit.fillDisc(c, cx, cy, radius * 0.40F, withAlpha(color, Math.round(80 * strength)));
    }

    private static void lockGlyph(PreviewCanvas c, float x, float y, int color) {
        WheelKit.fillArc(c, x, y - 1F, 2.4F, 3.2F, (float) Math.PI, TAU, color, 16);
        c.fill((int) (x - 4F), (int) y, 8, 5, color);
        c.fill((int) (x - 1F), (int) (y + 1F), 2, 2, 0xFF0A0614);
    }

    // ══════════════════════════════════════════════════════════════
    //  小部件
    // ══════════════════════════════════════════════════════════════

    private static void innerPanel(PreviewCanvas c, int x, int y, int w, int h) {
        SdfShapes.roundRect(c, x, y, w, h, 3F, 0xB0080414, 0xE0070410,
                withAlpha(GOLD, 65), 0.7F);
    }

    private static void divider(PreviewCanvas c, int x, int y, int w) {
        c.fill(x, y, w, 1, withAlpha(GOLD, 35));
    }

    private static void stat(PreviewCanvas c, int y, String label, String value) {
        c.text(label, PREVIEW_X + 9, y, withAlpha(TEXT_FAINT, 220), false);
        c.textRight(value, PREVIEW_X + PREVIEW_W - 9, y, withAlpha(TEXT, 240), false);
    }

    private static void stat(PreviewCanvas c, int y, String label, String value, int valueColor) {
        c.text(label, PREVIEW_X + 9, y, withAlpha(TEXT_FAINT, 220), false);
        c.textRight(value, PREVIEW_X + PREVIEW_W - 9, y, withAlpha(valueColor, 240), false);
    }

    private static void bar(PreviewCanvas c, int x, int y, int w, int h,
                            int track, int fill, float ratio) {
        c.fill(x, y, w, h, track);
        c.fill(x, y, Math.max(0, Math.round(w * ratio)), h, fill);
        c.fill(x, y, w, 1, withAlpha(0xFFFFFFFF, 35));
    }

    // ══════════════════════════════════════════════════════════════
    //  工具
    // ══════════════════════════════════════════════════════════════

    private static Sigil[] sigils(String[] labels, int[] colors) {
        Sigil[] out = new Sigil[labels.length];
        for (int i = 0; i < labels.length; i++) {
            out[i] = new Sigil(labels[i], colors[i], 0x1000 + i * 0x37);
        }
        return out;
    }

    private static int withAlpha(int color, int alpha) {
        return WheelKit.withAlpha(color, Math.max(0, Math.min(255, alpha)));
    }
}
