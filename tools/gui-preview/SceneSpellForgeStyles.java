import java.util.Locale;

/**
 * 法术合台 GUI 的两套 Minecraft 风格提案。
 *
 * <ul>
 *   <li><b>A / DRAWN</b>：不用任何新贴图，全部用 {@code GuiGraphics.fill} 画原版风格的
 *       凸起/凹陷 1px 斜角、18×18 槽位井和灰色按钮；后续只能改绘制代码。</li>
 *   <li><b>B / TEXTURED</b>：静态底板来自 {@code assets/rotten_ruins/textures/gui/spell_forge/}
 *       下的占位 PNG（1 texel = 1 GUI 像素），文字 / 物品图标 / 进度 / 选中态仍由运行时叠加。
 *       美术后续直接重绘 PNG 即可换肤。</li>
 * </ul>
 *
 * <p>两套方案几何完全一致，都使用 640×400 逻辑画布与 592×324 面板：
 * 左列蓝图 + 三材料 + 产出，中列熔炉窗口 + 七学派选择 + 进度条，右列法术 tooltip 预览，
 * 顶部 Forge / Codex 两个原版按钮式页签。Codex 页画出"已发现 3 / 未知 5"的卡片网格。</p>
 *
 * <p>几何锚点（相对面板左上角，1 GUI 像素）：
 * 标题 (10,6)、页签 (10,21)/(78,21) 64×18、标题分隔线 y=43/44、内容顶 y=52、
 * 左列槽位 x=10，蓝图 y=64，材料 y=106/134/162，产出 y=208、中列熔炉窗口 (216,66) 160×150、
 * 产物槽 (276,82) 40×40、学派按钮 y=146、进度槽 (226,224) 140×8、右列 tooltip (412,64) 170×200、
 * 底部分隔线 y=286/287、按钮行 y=292。</p>
 *
 * <p>产物在 {@code build/gui-preview/}：A/B 各三张（就绪 / 刻录中 / 图鉴）。
 * 另有 1280×440 的并排对比图（{@code -Width 1280 -Height 440 -Scale 1}）。
 * 文字一律英文：预览工具只带 MC 拉丁位图字体。</p>
 */
public final class SceneSpellForgeStyles {

    // ══════════════════════════════════════════════════════════════
    //  原版容器调色板
    // ══════════════════════════════════════════════════════════════

    private static final int BORDER = 0xFF373737;
    private static final int FACE = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int INSET = 0xFFDBDBDB;
    private static final int WELL_FACE = 0xFF8B8B8B;
    private static final int WELL_TOP = 0xFF5F5F5F;
    private static final int WELL_BOTTOM = 0xFFC6C6C6;
    private static final int DARK_WINDOW = 0xFF2B2B2B;
    private static final int DARK_WINDOW_TOP = 0xFF101010;
    private static final int DARK_WINDOW_BOTTOM = 0xFF4A4A4A;
    private static final int TRACK_FACE = 0xFF303030;
    private static final int TRACK_TOP = 0xFF101010;
    private static final int TRACK_BOTTOM = 0xFF555555;

    private static final int INK = 0xFF404040;
    private static final int INK_SOFT = 0xFF606060;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TOOL_GRAY = 0xFFA8A8A8;
    private static final int TOOL_DIM = 0xFFC8C8C8;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFFA0;
    private static final int DISABLED_TEXT = 0xFFB0B0B0;

    private static final int PANEL_W = 592;
    private static final int PANEL_H = 324;

    /** 面板左上角；对比图里会被挪到左右两栏。 */
    private static int panelX = 24;
    private static int panelY = 44;

    private static final float[] WATER_C = {0.33F, 0.75F, 1.00F};
    private static final float[] MYSTERY_C = {0.906F, 0.655F, 0.937F};

    /** 七学派，顺序与 ElementBand 一致（光/火/水/自然/死亡/虚空/神秘）。 */
    private static final int[] SCHOOL_COLORS = {
            0xFFF4AC, 0xFF7843, 0xFF53BEFF, 0xFF78D98A, 0xFFBE8BDA, 0xFF8B85FF, 0xFFE7A7EF
    };
    private static final int FIRE_NODE = 1;

    // ══════════════════════════════════════════════════════════════
    //  法术数据（基值与实机 SpellDefinition 一致）
    // ══════════════════════════════════════════════════════════════

    private static final WheelKit.Spell FIREBALL = spell(
            "fire_ball", "Fireball", "FIRE", "INSTANT", 40, 40,
            "Hurls a roaring orb that detonates on impact, blasting blocks and foes alike.",
            WheelKit.FIRE_C);
    private static final WheelKit.Spell TIDAL = spell(
            "tidal_pulse", "Tidal Pulse", "WATER", "INSTANT", 40, 100,
            "A crashing ring of water that knocks creatures back and quenches your flames.",
            WATER_C);
    private static final WheelKit.Spell ARCANE = spell(
            "arcane_step", "Arcane Step", "MYSTERIOUS", "INSTANT", 40, 100,
            "Blinks you to solid ground near the crosshair with a short burst of speed.",
            MYSTERY_C);

    private static final WheelKit.Spell[] KNOWN = {FIREBALL, TIDAL, ARCANE};
    private static final int UNKNOWN_COUNT = 5;
    private static final int TOTAL_SPELLS = KNOWN.length + UNKNOWN_COUNT;

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) throws Exception {
        if (c.width >= 1200) {
            comparison(c);
            return;
        }
        // A：纯绘制。B：占位 PNG。
        view(c, false, false, false);
        c.save("spell_forge_a_drawn");
        view(c, false, false, true);
        c.save("spell_forge_a_drawn_forging");
        view(c, false, true, false);
        c.save("spell_forge_a_codex");

        view(c, true, false, false);
        c.save("spell_forge_b_textured");
        view(c, true, false, true);
        c.save("spell_forge_b_textured_forging");
        view(c, true, true, false);
        c.save("spell_forge_b_codex");

        // 默认导出 <scene>.png = B / 就绪态。
        view(c, true, false, false);
    }

    private static void view(PreviewCanvas c, boolean textured, boolean codex, boolean forging) {
        panelX = 24;
        panelY = 44;
        c.backdrop();
        drawStatic(c, textured, codex);
        drawTitleAndTabs(c, textured, codex);
        if (codex) {
            drawCodex(c, textured);
        } else {
            drawForge(c, textured, forging);
        }
        drawFooter(c, textured, codex, forging);
    }

    /** 1280×440 并排对比图（Scale 1）：左 A 右 B，几何完全一致。 */
    private static void comparison(PreviewCanvas c) {
        c.backdrop();
        c.text("A / DRAWN - VANILLA BEVELS", 12, 20, WHITE, true);
        c.text("B / TEXTURED - EDITABLE PLACEHOLDER PNG", 676, 20, WHITE, true);

        panelX = 12;
        panelY = 84;
        drawStatic(c, false, false);
        drawTitleAndTabs(c, false, false);
        drawForge(c, false, false);
        drawFooter(c, false, false, false);

        panelX = 676;
        drawStatic(c, true, false);
        drawTitleAndTabs(c, true, false);
        drawForge(c, true, false);
        drawFooter(c, true, false, false);

        c.textCentered(
                "Same layout, slots and hit boxes  /  1 GUI pixel = 1 texture pixel"
                        + "  /  repaint spell_forge/*.png to reskin B",
                640, 424, WHITE, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  静态几何：A 用 fill 画，B 直接 blit 底板 PNG
    // ══════════════════════════════════════════════════════════════

    private static void drawStatic(PreviewCanvas c, boolean textured, boolean codex) {
        if (textured) {
            c.blit(c.texture("textures/gui/spell_forge/"
                    + (codex ? "codex" : "forge") + ".png"), panelX, panelY, PANEL_W, PANEL_H);
            return;
        }
        // 外壳 + 内嵌亮线
        bevel(c, panelX, panelY, PANEL_W, PANEL_H, FACE, LIGHT, DARK);
        hline(c, panelX + 3, panelY + 3, PANEL_W - 6, INSET);

        // 原版容器的标题分隔线
        hline(c, panelX + 8, panelY + 43, PANEL_W - 16, DARK);
        hline(c, panelX + 8, panelY + 44, PANEL_W - 16, LIGHT);

        // 底部分隔线
        hline(c, panelX + 8, panelY + 286, PANEL_W - 16, DARK);
        hline(c, panelX + 8, panelY + 287, PANEL_W - 16, LIGHT);

        if (codex) {
            return;
        }
        // 三列分隔线只在 FORGE 页出现（CODEX 是干净的卡片网格）
        vline(c, panelX + 184, panelY + 48, 238, DARK);
        vline(c, panelX + 185, panelY + 48, 238, LIGHT);
        vline(c, panelX + 406, panelY + 48, 238, DARK);
        vline(c, panelX + 407, panelY + 48, 238, LIGHT);
        // 左列：蓝图 / 三材料 / 产出槽
        well(c, panelX + 10, panelY + 64, 18, 18);
        well(c, panelX + 10, panelY + 106, 18, 18);
        well(c, panelX + 10, panelY + 134, 18, 18);
        well(c, panelX + 10, panelY + 162, 18, 18);
        well(c, panelX + 10, panelY + 208, 18, 18);

        // 中列：熔炉窗口 + 产物槽 + 进度槽
        bevel(c, panelX + 216, panelY + 66, 160, 150, DARK_WINDOW,
                DARK_WINDOW_TOP, DARK_WINDOW_BOTTOM);
        well(c, panelX + 276, panelY + 82, 40, 40);
        well(c, panelX + 226, panelY + 224, 140, 8, TRACK_FACE, TRACK_TOP, TRACK_BOTTOM);

        // 右列：原版 tooltip 风格预览框
        tooltipFrame(c, panelX + 412, panelY + 64, 170, 200);
    }

    // ══════════════════════════════════════════════════════════════
    //  标题 + 页签（两套风格共用排版，只换按钮的实现）
    // ══════════════════════════════════════════════════════════════

    private static void drawTitleAndTabs(PreviewCanvas c, boolean textured, boolean codex) {
        c.text("Spell Forge", panelX + 10, panelY + 6, INK, false);
        tab(c, textured, panelX + 10, panelY + 21, 64, 18, "Forge", !codex);
        tab(c, textured, panelX + 78, panelY + 21, 64, 18, "Codex", codex);
    }

    private static void tab(PreviewCanvas c, boolean textured, int x, int y, int w, int h,
                            String label, boolean selected) {
        button(c, textured, x, y, w, h, selected ? 1 : 0);
        c.textCentered(label, x + w / 2, y + (h - 9) / 2 + 1,
                selected ? YELLOW : WHITE, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  FORGE 页
    // ══════════════════════════════════════════════════════════════

    private static void drawForge(PreviewCanvas c, boolean textured, boolean forging) {
        int centerX = panelX + PANEL_W / 2;

        // ── 左列：蓝图 ──
        c.text("Blueprint", panelX + 10, panelY + 52, INK, false);
        scrollIcon(c, textured, panelX + 11, panelY + 65);
        c.text("Fireball", panelX + 32, panelY + 63, INK, false);
        schoolDot(c, panelX + 32, panelY + 75, 8, SCHOOL_COLORS[FIRE_NODE]);
        c.text("Fire School", panelX + 42, panelY + 75, INK, false);

        // ── 左列：材料 ──
        c.text("Materials", panelX + 10, panelY + 94, INK, false);
        materialRow(c, panelX + 10, panelY + 106, "fire_mana_plate.png",
                "Fire Mana Plate", "Source machine  -  x1");
        materialRow(c, panelX + 10, panelY + 134, "fire_mana_bottle.png",
                "Fire Mana Bottle", "Collector  -  x3");
        materialRow(c, panelX + 10, panelY + 162, "magic_cata.png",
                "Magic Catalyst", "Crafted  -  x1");

        // ── 左列：产出 ──
        c.text("Output", panelX + 10, panelY + 196, INK, false);
        c.blit(c.texture("textures/item/fire_ball.png"), panelX + 11, panelY + 209, 16, 16,
                0F, 0F, 1F, 1F, 0x66FFFFFF);
        c.text("Spell item", panelX + 32, panelY + 207, INK_SOFT, false);
        c.text(forging ? "Forging..." : "Awaiting forge",
                panelX + 32, panelY + 218, INK_SOFT, false);

        // ── 中列：熔炉窗口 ──
        c.textCentered("Forge", centerX, panelY + 52, INK, false);
        c.blit(c.texture("textures/item/fire_ball.png"), panelX + 280, panelY + 86, 32, 32);
        c.textCentered("Fireball", centerX, panelY + 130, WHITE, true);

        for (int i = 0; i < 7; i++) {
            schoolButton(c, textured, panelX + 234 + i * 18, panelY + 146,
                    SCHOOL_COLORS[i], i == FIRE_NODE);
        }
        c.textCentered(forging ? "Forging..." : "All requirements met",
                centerX, panelY + 176, forging ? YELLOW : GREEN, true);

        // 进度条填充（轨道本身在静态层）
        float progress = forging ? 0.62F : 0F;
        if (progress > 0F) {
            int inner = 138;
            int fill = Math.max(1, Math.round(inner * progress));
            c.fill(panelX + 227, panelY + 225, fill, 6, 0xFFC98A2A);
            c.fill(panelX + 227, panelY + 225, fill, 1, 0xFFFFD37A);
            c.fill(panelX + 227, panelY + 230, fill, 1, 0xFF8A5A12);
        }
        c.textRight(Math.round(progress * 100F) + "%", panelX + 390, panelY + 222, INK, false);
        c.textCentered(forging ? "Forging  -  62%  -  1.4s remaining" : "Ready to forge",
                centerX, panelY + 238, INK, false);

        // ── 右列：tooltip 预览 ──
        c.text("Preview", panelX + 412, panelY + 52, INK, false);
        c.blit(c.texture("textures/item/fire_ball.png"), panelX + 422, panelY + 76, 16, 16);
        c.text("Fireball", panelX + 442, panelY + 75, WHITE, true);
        schoolDot(c, panelX + 422, panelY + 94, 8, SCHOOL_COLORS[FIRE_NODE]);
        c.text("Fire School", panelX + 432, panelY + 94, SCHOOL_COLORS[FIRE_NODE], true);

        statRow(c, panelY + 108, "Type", "Instant");
        statRow(c, panelY + 120, "Mana", "40");
        statRow(c, panelY + 132, "Cooldown", "2.0s");
        statRow(c, panelY + 144, "Damage", "4.0");

        c.fill(panelX + 417, panelY + 156, 160, 1, 0xFF2A2A2A);
        c.text("Description", panelX + 422, panelY + 162, TOOL_GRAY, false);
        WheelKit.drawWrapped(c, FIREBALL.lore(), panelX + 422, panelY + 174, 152,
                TOOL_DIM, 4);

        c.fill(panelX + 417, panelY + 212, 160, 1, 0xFF2A2A2A);
        checkIcon(c, textured, panelX + 422, panelY + 220);
        c.text("Blueprint known", panelX + 432, panelY + 216, GREEN, true);
        c.text("Source: Meteor Scar", panelX + 422, panelY + 230, TOOL_GRAY, false);
        c.text("Collection 1 / " + TOTAL_SPELLS, panelX + 422, panelY + 242, TOOL_GRAY, false);
    }

    private static void materialRow(PreviewCanvas c, int x, int y, String icon,
                                    String name, String source) {
        c.blit(c.texture("textures/item/" + icon), x + 1, y + 1, 16, 16);
        c.text(name, x + 22, y, INK, false);
        c.text(source, x + 22, y + 11, INK_SOFT, false);
        checkIcon(c, false, x + 148, y + 5);
    }

    /** 右列 tooltip 的一行：左侧灰标签，右侧白色数值。 */
    private static void statRow(PreviewCanvas c, int y, String label, String value) {
        c.text(label, panelX + 422, y, TOOL_GRAY, false);
        c.textRight(value, panelX + 572, y, WHITE, false);
    }

    // ══════════════════════════════════════════════════════════════
    //  CODEX 页
    // ══════════════════════════════════════════════════════════════

    private static void drawCodex(PreviewCanvas c, boolean textured) {
        for (int i = 0; i < TOTAL_SPELLS; i++) {
            int col = i % 4;
            int row = i / 4;
            int x = panelX + 10 + col * 144;
            int y = panelY + 56 + row * 110;
            boolean selected = i == 0;
            if (i < KNOWN.length) {
                knownCard(c, textured, KNOWN[i], x, y, selected);
            } else {
                unknownCard(c, textured, x, y);
            }
        }
    }

    private static void knownCard(PreviewCanvas c, boolean textured, WheelKit.Spell spell,
                                  int x, int y, boolean selected) {
        if (selected) {
            c.fill(x - 1, y - 1, 134, 100, WHITE);
        }
        if (textured) {
            c.blit(c.texture("textures/gui/spell_forge/cards.png"),
                    x, y, 132, 98, 0F, 0F, 132F / 264F, 1F);
        } else {
            bevel(c, x, y, 132, 98, FACE, LIGHT, DARK);
            hline(c, x + 3, y + 3, 126, INSET);
        }

        c.blit(c.texture("textures/item/" + spell.id() + ".png"), x + 8, y + 8, 16, 16);
        c.text(spell.name(), x + 28, y + 8, INK, false);
        schoolDot(c, x + 28, y + 21, 6, argb(spell.color()));
        c.text(spell.schoolLabel(), x + 36, y + 20, INK, false);
        c.text(spell.mana() + " mana  -  " + cooldownLabel(spell.cooldownTicks()),
                x + 28, y + 32, INK_SOFT, false);

        c.fill(x + 8, y + 48, 116, 1, 0xFFA0A0A0);
        checkIcon(c, textured, x + 8, y + 58);
        c.text("Blueprint known", x + 18, y + 55, INK, false);
        button(c, textured, x + 78, y + 76, 46, 16, 0);
        c.textCentered("Forge", x + 78 + 23, y + 80, WHITE, true);
    }

    private static void unknownCard(PreviewCanvas c, boolean textured, int x, int y) {
        if (textured) {
            c.blit(c.texture("textures/gui/spell_forge/cards.png"),
                    x, y, 132, 98, 132F / 264F, 0F, 1F, 1F);
        } else {
            bevel(c, x, y, 132, 98, DARK_WINDOW, DARK_WINDOW_TOP, DARK_WINDOW_BOTTOM);
        }
        c.textCenteredScaled("?", x + 22F, y + 12F, 0xFFD0D0D0, true, 2F);
        c.text("Unknown Spell", x + 38, y + 11, WHITE, true);
        c.text("Blueprint missing", x + 38, y + 23, 0xFF9A9A9A, false);
        lockIcon(c, textured, x + 112, y + 8);

        c.fill(x + 8, y + 48, 116, 1, 0xFF555555);
        c.text("Find its blueprint", x + 10, y + 58, 0xFFB0B0B0, false);
        c.text("in the world", x + 10, y + 70, 0xFFB0B0B0, false);
    }

    // ══════════════════════════════════════════════════════════════
    //  底部操作行
    // ══════════════════════════════════════════════════════════════

    private static void drawFooter(PreviewCanvas c, boolean textured, boolean codex, boolean forging) {
        if (codex) {
            c.text("3 / " + TOTAL_SPELLS + " spells discovered", panelX + 10, panelY + 296,
                    INK, false);
            button(c, textured, panelX + 502, panelY + 292, 80, 20, 0);
            c.textCentered("Open Forge", panelX + 542, panelY + 297, WHITE, true);
            return;
        }
        c.text("Blueprint is consumed when forged.", panelX + 10, panelY + 296, INK, false);
        int forgeState = forging ? 3 : 0;
        button(c, textured, panelX + 434, panelY + 292, 60, 20, 0);
        c.textCentered("Cancel", panelX + 464, panelY + 297, WHITE, true);
        button(c, textured, panelX + 502, panelY + 292, 80, 20, forgeState);
        c.textCentered(forging ? "Forging..." : "Forge Spell", panelX + 542, panelY + 297,
                forging ? DISABLED_TEXT : WHITE, !forging);
    }

    // ══════════════════════════════════════════════════════════════
    //  风格化小部件：同一几何，两套实现
    // ══════════════════════════════════════════════════════════════

    /** state：0 常态、1 按下/选中、2 悬停、3 禁用。 */
    private static void button(PreviewCanvas c, boolean textured, int x, int y, int w, int h,
                               int state) {
        if (textured) {
            nineSlice(c, c.texture("textures/gui/spell_forge/widgets.png"),
                    x, y, w, h, state * 20, 0);
            return;
        }
        switch (state) {
            case 1 -> bevel(c, x, y, w, h, 0xFFA0A0A0, DARK, LIGHT);
            case 2 -> bevel(c, x, y, w, h, 0xFFD8D8D8, LIGHT, DARK);
            case 3 -> bevel(c, x, y, w, h, 0xFF8B8B8B, 0xFFBDBDBD, DARK);
            default -> bevel(c, x, y, w, h, FACE, LIGHT, DARK);
        }
    }

    /** 20×20 状态图集的九宫格：四边各固定 2px，中心与边条拉伸。 */
    private static void nineSlice(PreviewCanvas c, PreviewTexture atlas, int x, int y,
                                  int w, int h, int sourceX, int sourceY) {
        int[] src = {0, 2, 18, 20};
        int[] dx = {0, 2, w - 2, w};
        int[] dy = {0, 2, h - 2, h};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                c.blit(atlas, x + dx[col], y + dy[row],
                        Math.max(1, dx[col + 1] - dx[col]), Math.max(1, dy[row + 1] - dy[row]),
                        (sourceX + src[col]) / 112F, (sourceY + src[row]) / 20F,
                        (sourceX + src[col + 1]) / 112F, (sourceY + src[row + 1]) / 20F);
            }
        }
    }

    /** 16×16 蓝图卷轴：A 用矩形拼，B 用图集。 */
    private static void scrollIcon(PreviewCanvas c, boolean textured, int x, int y) {
        if (textured) {
            c.blit(c.texture("textures/gui/spell_forge/widgets.png"),
                    x, y, 16, 16, 80F / 112F, 0F, 96F / 112F, 16F / 20F);
            return;
        }
        c.fill(x + 2, y + 1, 12, 14, 0xFFE8DFC0);
        c.fill(x, y + 1, 3, 14, 0xFFC9B98C);
        c.fill(x + 13, y + 1, 3, 14, 0xFFC9B98C);
        // 卷轴外框
        c.fill(x + 2, y + 1, 12, 1, 0xFF6E5A3A);
        c.fill(x + 2, y + 14, 12, 1, 0xFF6E5A3A);
        c.fill(x + 2, y + 1, 1, 14, 0xFF6E5A3A);
        c.fill(x + 13, y + 1, 1, 14, 0xFF6E5A3A);
        c.fill(x, y + 1, 16, 1, 0xFFF5EED6);
        c.fill(x, y + 14, 16, 1, 0xFF8A7450);
        hline(c, x + 5, y + 5, 6, 0xFF8A7A5A);
        hline(c, x + 5, y + 8, 6, 0xFF8A7A5A);
        hline(c, x + 5, y + 11, 6, 0xFF8A7A5A);
        c.fill(x + 7, y + 3, 1, 1, 0xFFC9A227);
    }

    /** 8×8 对勾：A 逐像素画，B 取图集。 */
    private static void checkIcon(PreviewCanvas c, boolean textured, int x, int y) {
        if (textured) {
            c.blit(c.texture("textures/gui/spell_forge/widgets.png"),
                    x, y, 8, 8, 96F / 112F, 0F, 104F / 112F, 8F / 20F);
            return;
        }
        int green = 0xFF55AA55;
        c.fill(x + 6, y + 1, 2, 1, green);
        c.fill(x + 5, y + 2, 2, 1, green);
        c.fill(x, y + 3, 1, 1, green);
        c.fill(x + 4, y + 3, 2, 1, green);
        c.fill(x + 1, y + 4, 2, 1, green);
        c.fill(x + 2, y + 4, 2, 1, green);
        c.fill(x + 2, y + 5, 1, 1, green);
        c.fill(x + 3, y + 5, 1, 1, green);
    }

    /** 8×8 锁：A 逐像素画，B 取图集。 */
    private static void lockIcon(PreviewCanvas c, boolean textured, int x, int y) {
        if (textured) {
            c.blit(c.texture("textures/gui/spell_forge/widgets.png"),
                    x, y, 8, 8, 104F / 112F, 0F, 112F / 112F, 8F / 20F);
            return;
        }
        int gray = 0xFF8C8C96;
        c.fill(x + 2, y, 4, 1, gray);
        c.fill(x + 1, y + 1, 1, 1, gray);
        c.fill(x + 6, y + 1, 1, 1, gray);
        c.fill(x + 1, y + 2, 1, 1, gray);
        c.fill(x + 6, y + 2, 1, 1, gray);
        c.fill(x, y + 3, 8, 4, gray);
        c.fill(x + 3, y + 4, 2, 2, 0xFF3A3A44);
    }

    /** 8×8（或任意）纯色学派色块。 */
    private static void schoolDot(PreviewCanvas c, int x, int y, int size, int color) {
        c.fill(x, y, size, size, color);
        c.fill(x, y, size, 1, lighten(color));
        c.fill(x, y + size - 1, size, 1, darken(color));
    }

    /** 学派选择按钮：未选中为凸起灰钮，选中为凹陷 + 白框。 */
    private static void schoolButton(PreviewCanvas c, boolean textured, int x, int y,
                                     int color, boolean selected) {
        if (selected) {
            c.fill(x - 1, y - 1, 18, 18, WHITE);
            well(c, x, y, 16, 16);
        } else {
            bevel(c, x, y, 16, 16, FACE, LIGHT, DARK);
        }
        c.fill(x + 4, y + 4, 8, 8, color);
        c.fill(x + 4, y + 4, 8, 1, lighten(color));
        c.fill(x + 4, y + 11, 8, 1, darken(color));
    }

    // ══════════════════════════════════════════════════════════════
    //  1px 原版斜角 / 线 / tooltip
    // ══════════════════════════════════════════════════════════════

    private static void bevel(PreviewCanvas c, int x, int y, int w, int h,
                              int face, int light, int dark) {
        c.fill(x, y, w, h, BORDER);
        c.fill(x + 1, y + 1, w - 2, h - 2, face);
        c.fill(x + 1, y + 1, w - 2, 1, light);
        c.fill(x + 1, y + 1, 1, h - 2, light);
        c.fill(x + 1, y + h - 2, w - 2, 1, dark);
        c.fill(x + w - 2, y + 1, 1, h - 2, dark);
    }

    private static void well(PreviewCanvas c, int x, int y, int w, int h) {
        well(c, x, y, w, h, WELL_FACE, WELL_TOP, WELL_BOTTOM);
    }

    private static void well(PreviewCanvas c, int x, int y, int w, int h,
                             int face, int top, int bottom) {
        bevel(c, x, y, w, h, face, top, bottom);
    }

    private static void tooltipFrame(PreviewCanvas c, int x, int y, int w, int h) {
        c.fillGradientV(x, y, w, h, 0xF0100010, 0xF0180024);
        c.fill(x + 1, y, w - 2, 1, 0x995000FF);
        c.fill(x + 1, y + h - 1, w - 2, 1, 0x9930007F);
        c.fill(x, y + 1, 1, h - 2, 0x883C0099);
        c.fill(x + w - 1, y + 1, 1, h - 2, 0x883C0099);
    }

    private static void hline(PreviewCanvas c, int x, int y, int w, int color) {
        c.fill(x, y, w, 1, color);
    }

    private static void vline(PreviewCanvas c, int x, int y, int h, int color) {
        c.fill(x, y, 1, h, color);
    }

    // ══════════════════════════════════════════════════════════════
    //  数据工具
    // ══════════════════════════════════════════════════════════════

    private static WheelKit.Spell spell(String id, String name, String school, String using,
                                       int mana, int cooldownTicks, String lore, float[] color) {
        String meta = using + "  |  " + mana + " MANA  |  " + cooldownLabel(cooldownTicks);
        return new WheelKit.Spell(id, name, school, "ACTIVE", using, mana, cooldownTicks, 0,
                "textures/item/" + id + ".png", meta, lore, color);
    }

    private static String cooldownLabel(int ticks) {
        return String.format(Locale.ROOT, "%.1fs", ticks / 20.0F);
    }

    private static int argb(float[] rgb) {
        int r = Math.round(Math.max(0F, Math.min(1F, rgb[0])) * 255F);
        int g = Math.round(Math.max(0F, Math.min(1F, rgb[1])) * 255F);
        int b = Math.round(Math.max(0F, Math.min(1F, rgb[2])) * 255F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 56);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 56);
        int b = Math.min(255, (color & 0xFF) + 56);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color) {
        int r = Math.max(0, ((color >> 16) & 0xFF) - 56);
        int g = Math.max(0, ((color >> 8) & 0xFF) - 56);
        int b = Math.max(0, (color & 0xFF) - 56);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
