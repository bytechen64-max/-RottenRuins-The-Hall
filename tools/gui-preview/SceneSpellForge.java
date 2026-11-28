import java.util.Locale;

/**
 * 法术合台（Spell Forge）设计稿。
 *
 * <p>这套图用来回答"法术获取的合成界面长什么样"，对应
 * {@code docs/proposals/spell-acquisition.md} 里的思路：</p>
 * <ul>
 *   <li>左列：1 张<b>蓝图</b>（原典/残页）+ 3 个材料槽（法力板 / 元素储魔瓶 / 催化剂）；</li>
 *   <li>中列：<b>熔炉核心</b>——外圈七学派节点、刻度环、刻录进度弧，中心是要产出的法术；</li>
 *   <li>右列：<b>法术预览</b>——耗蓝/冷却/类型/描述，以及"蓝图来源"；</li>
 *   <li>页签：FORGE（本图）/ CODEX（图鉴：已发现与未发现法术）。</li>
 * </ul>
 *
 * <p>产物：{@code spell_forge.png}（默认 = 刻录中）、{@code spell_forge_ready.png}、
 * {@code spell_forge_codex.png}。文案全部用英文（MC 位图字体只覆盖拉丁）。</p>
 */
public final class SceneSpellForge {

    // ══════════════════════════════════════════════════════════════
    //  布局 / 调色板
    // ══════════════════════════════════════════════════════════════

    private static final int GOLD = WheelKit.GOLD;
    private static final int GOLD_BRIGHT = WheelKit.GOLD_BRIGHT;
    private static final int TEXT = WheelKit.TEXT;
    private static final int TEXT_DIM = WheelKit.TEXT_DIM;
    private static final int TEXT_FAINT = WheelKit.TEXT_FAINT;
    private static final int TEAL = WheelKit.TEAL;
    private static final int LOCKED = WheelKit.LOCKED;
    private static final int WARN = WheelKit.WARN;

    private static final int PANEL_X = 24;
    private static final int PANEL_Y = 44;
    private static final int PANEL_W = 592;
    private static final int PANEL_H = 324;

    /** 左列（INPUTS）。 */
    private static final int LX = 40;
    private static final int LW = 148;

    /** 右列（SPELL PREVIEW）。 */
    private static final int RX = 436;
    private static final int RW = 164;

    /** 中列熔炉核心。 */
    private static final float CX = 320F;
    private static final float CY = 198F;
    private static final float R_OUT = 92F;

    private static final float TAU = (float) (Math.PI * 2.0);

    private static final float[] WATER_C = {0.33F, 0.75F, 1.00F};
    private static final float[] MYSTERY_C = {0.906F, 0.655F, 0.937F};

    /** 七学派，顺序与 {@code ElementBand} 一致（光 / 火 / 水 / 自然 / 死亡 / 虚空 / 神秘）。 */
    private static final int[] SCHOOL_COLORS = {
            0xFFF4AC, 0xFF7843, 0x53BEFF, 0x78D98A, 0xBE8BDA, 0x8B85FF, 0xE7A7EF
    };
    private static final String[] SCHOOL_NAMES = {
            "LIGHT", "FIRE", "WATER", "NATURE", "DEATH", "VOID", "MYSTERIOUS"
    };
    private static final int FIRE_NODE = 1;

    // ══════════════════════════════════════════════════════════════
    //  法术数据（基值与实机 SpellDefinition 保持一致）
    // ══════════════════════════════════════════════════════════════

    private static final WheelKit.Spell FIREBALL = spell(
            "fire_ball", "Fireball", "FIRE", "INSTANT", 40, 40,
            "Hurls a roaring orb that detonates on impact, blasting blocks and foes alike.",
            WheelKit.FIRE_C);

    private static final WheelKit.Spell TIDAL = spell(
            "tidal_pulse", "Tidal Pulse", "WATER", "INSTANT", 40, 100,
            "A crashing ring of water that knocks creatures back and quenches your own flames.",
            WATER_C);

    private static final WheelKit.Spell ARCANE = spell(
            "arcane_step", "Arcane Step", "MYSTERIOUS", "INSTANT", 40, 100,
            "Blinks you to solid ground near the crosshair and leaves a short burst of speed.",
            MYSTERY_C);

    private static final WheelKit.Spell[] KNOWN = {FIREBALL, TIDAL, ARCANE};

    /** 未发现的法术数量（图鉴里画成暗卡）。 */
    private static final int UNKNOWN_COUNT = 5;
    private static final int TOTAL_SPELLS = KNOWN.length + UNKNOWN_COUNT;

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) throws Exception {
        c.shader("sdf_ui");

        forge(c, time, 0F, false);
        c.save("spell_forge_ready");

        codex(c, time);
        c.save("spell_forge_codex");

        // 默认导出 = 刻录中，动态信息最多的一帧。
        forge(c, time, 0.62F, true);
    }

    // ══════════════════════════════════════════════════════════════
    //  FORGE 页
    // ══════════════════════════════════════════════════════════════

    private static void forge(PreviewCanvas c, float time, float progress, boolean forging) {
        background(c, time);
        header(c, time, "SPELL FORGE",
                forging
                        ? "FORGING  -  FIREBALL  -  FIRE SCHOOL"
                        : "BLUEPRINT  -  FIREBALL  -  FIRE SCHOOL");
        panel(c);
        tabs(c, time, true);

        inputs(c, time, forging);
        core(c, time, progress, forging);
        preview(c, time, forging);
        actions(c, forging);

        hintBar(c,
                "BLUEPRINTS DROP FROM BOSSES, RUINS AND FIELD ANOMALIES",
                "FORGE  /  CODEX     -     ESC  CLOSE");
    }

    // ══════════════════════════════════════════════════════════════
    //  左列：蓝图 + 材料 + 产出
    // ══════════════════════════════════════════════════════════════

    private static void inputs(PreviewCanvas c, float time, boolean forging) {
        c.text("INPUTS", LX, 78, withAlpha(GOLD_BRIGHT, 235), true);
        c.textRight("3 / 3", LX + LW, 78, withAlpha(TEAL, 220), false);

        // —— 蓝图卡 ——
        SdfShapes.roundRect(c, LX, 90, LW, 62, 4F,
                0xE60B1626, 0xE60B1626, withAlpha(GOLD, 85), 0.8F);
        blueprintIcon(c, LX + 6F, 96F, 34F, time);
        c.text("BLUEPRINT", LX + 48, 96, withAlpha(TEXT_FAINT, 220), false);
        c.text("Fireball", LX + 48, 108, withAlpha(TEXT, 245), true);
        c.text("FIRE  SCHOOL", LX + 48, 120, argb(WheelKit.FIRE_C, 0.95F), false);

        // —— 材料 ——
        c.text("MATERIALS", LX, 164, withAlpha(GOLD_BRIGHT, 235), true);
        c.textRight("ALL MET", LX + LW, 164, withAlpha(TEAL, 220), false);

        material(c, 178, "fire_mana_plate.png", "Fire Plate", "SOURCE  -  x1");
        material(c, 214, "fire_mana_bottle.png", "Fire Bottle", "COLLECTOR  -  x3");
        material(c, 250, "magic_cata.png", "Magic Catalyst", "CRAFTED  -  x1");

        // —— 产出槽 ——
        SdfShapes.roundRect(c, LX, 286, LW, 30, 4F,
                0xE6091220, 0xE6091220, withAlpha(GOLD, 55), 0.8F);
        slot(c, LX + 4, 290, 22);
        c.blit(c.texture("textures/item/fire_ball.png"), LX + 7, 293, 16, 16, 0F, 0F, 1F, 1F,
                0x66FFFFFF);
        c.text("OUTPUT", LX + 32, 290, withAlpha(TEXT_FAINT, 220), false);
        c.text(forging ? "FORGING  -  62%" : "AWAITING FORGE",
                LX + 32, 301, withAlpha(forging ? GOLD : TEXT_DIM, 220), false);
    }

    private static void material(PreviewCanvas c, int y, String icon, String name, String sub) {
        slot(c, LX, y, 22);
        c.blit(c.texture("textures/item/" + icon), LX + 3, y + 3, 16, 16);
        c.text(name, LX + 30, y + 1, withAlpha(TEXT, 240), true);
        c.text(sub, LX + 30, y + 12, withAlpha(TEXT_FAINT, 220), false);
        check(c, LX + 141F, y + 11F, withAlpha(TEAL, 235));
    }

    // ══════════════════════════════════════════════════════════════
    //  中列：熔炉核心
    // ══════════════════════════════════════════════════════════════

    private static void core(PreviewCanvas c, float time, float progress, boolean forging) {
        c.textCentered("FORGE CORE", Math.round(CX), 78, withAlpha(GOLD_BRIGHT, 200), false);

        // 炉芯暖光（低透明度的同心圆，层数少所以不糊）
        for (int i = 5; i >= 1; i--) {
            WheelKit.fillDisc(c, CX, CY, R_OUT * 0.55F + i * 12F,
                    argb(WheelKit.FIRE_C, 0.012F * i * (forging ? 1.8F : 1F)));
        }

        // 外圈 + 刻度
        WheelKit.fillRing(c, CX, CY, R_OUT, 0.55F, withAlpha(GOLD, 55), 96);
        for (int i = 0; i < 48; i++) {
            boolean major = i % 6 == 0;
            float a = i * TAU / 48F + time * 0.05F;
            float r0 = major ? 81F : 84.5F;
            float r1 = 88F;
            WheelKit.line(c,
                    CX + (float) Math.cos(a) * r0, CY + (float) Math.sin(a) * r0,
                    CX + (float) Math.cos(a) * r1, CY + (float) Math.sin(a) * r1,
                    major ? 0.65F : 0.35F,
                    withAlpha(GOLD, major ? 95 : 45));
        }

        // 七学派节点（缓慢旋转；当前蓝图对应的火节点点亮）
        WheelKit.fillRing(c, CX, CY, 103F, 0.35F, withAlpha(GOLD, 24), 64);
        for (int i = 0; i < 7; i++) {
            float a = -TAU / 4F + i * TAU / 7F + time * 0.03F;
            float nx = CX + (float) Math.cos(a) * 103F;
            float ny = CY + (float) Math.sin(a) * 103F;
            boolean lit = i == FIRE_NODE;
            int color = SCHOOL_COLORS[i];
            if (lit) {
                for (int g = 3; g >= 1; g--) {
                    WheelKit.fillDisc(c, nx, ny, 5.5F + g * 2.6F,
                            withAlpha(color, 26 + (3 - g) * 8));
                }
                WheelKit.spark(c, nx, ny, 1.6F, withAlpha(GOLD_BRIGHT, 190));
            }
            WheelKit.fillDisc(c, nx, ny, lit ? 4.4F : 3.2F, withAlpha(color, lit ? 245 : 120));
        }

        // 进度轨道 + 进度弧
        WheelKit.fillRing(c, CX, CY, 74F, 2.0F, withAlpha(GOLD, 30), 96);
        if (progress > 0.001F) {
            float a0 = -TAU / 4F;
            float a1 = a0 + TAU * progress;
            SdfShapes.wedge(c, CX, CY, 71.5F, 76.5F, a0, a1,
                    withAlpha(GOLD_BRIGHT, 240), withAlpha(GOLD, 190));
            float hx = CX + (float) Math.cos(a1) * 74F;
            float hy = CY + (float) Math.sin(a1) * 74F;
            WheelKit.fillDisc(c, hx, hy, 3.4F, withAlpha(GOLD_BRIGHT, 255));
            WheelKit.spark(c, hx, hy, 1.4F, withAlpha(GOLD_BRIGHT, 150));
        }

        // 内环 + 中心法术
        WheelKit.fillRing(c, CX, CY, 55F, 0.45F, withAlpha(GOLD, 38), 64);
        float pulse = forging ? 1.0F + (float) Math.sin(time * 3.2F) * 0.05F : 0.75F;
        WheelKit.drawSpellIcon(c, FIREBALL, CX, CY - 6F, 40F, 1F, pulse);

        // 中心状态：百分比 / READY
        if (forging) {
            String percent = Math.round(progress * 100F) + "%";
            c.textCenteredScaled(percent, CX, CY + 23, withAlpha(GOLD_BRIGHT, 250), true, 2F);
            c.textCentered("FORGING", Math.round(CX), Math.round(CY + 43),
                    withAlpha(GOLD, 215), false);
        } else {
            c.textCenteredScaled("READY", CX, CY + 23, withAlpha(GOLD_BRIGHT, 250), true, 2F);
            c.textCentered("CLICK FORGE", Math.round(CX), Math.round(CY + 43),
                    withAlpha(TEXT_DIM, 225), false);
        }

        // 环下状态行
        String status = forging
                ? "FORGING  62%   -   1.4s REMAINING"
                : "ALL REQUIREMENTS MET";
        c.textCentered(status, Math.round(CX), 302,
                withAlpha(forging ? GOLD : TEAL, 230), false);

        // 刻录时环绕的星火
        if (forging) {
            for (int i = 0; i < 4; i++) {
                float a = time * 1.6F + i * TAU / 4F;
                float r = 64F + (float) Math.sin(time * 2.1F + i) * 3F;
                float sx = CX + (float) Math.cos(a) * r;
                float sy = CY + (float) Math.sin(a) * r;
                WheelKit.spark(c, sx, sy, 0.9F, withAlpha(GOLD_BRIGHT, 120));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  右列：法术预览
    // ══════════════════════════════════════════════════════════════

    private static void preview(PreviewCanvas c, float time, boolean forging) {
        c.text("SPELL PREVIEW", RX, 78, withAlpha(GOLD_BRIGHT, 200), false);

        WheelKit.drawSpellIcon(c, FIREBALL, RX + RW / 2F, 106F, 34F, 1F, 0.9F);
        c.textCentered(FIREBALL.name(), RX + RW / 2, 136, withAlpha(TEXT, 250), true);
        c.textCentered("FIRE  -  ACTIVE  -  INSTANT", RX + RW / 2, 148,
                argb(WheelKit.FIRE_C, 1F), false);

        divider(c, RX, 162, RW);

        stat(c, 174, "TYPE", "INSTANT");
        stat(c, 188, "MANA", "40");
        stat(c, 202, "COOLDOWN", "2.0s");
        stat(c, 216, "DAMAGE", "4.0");

        divider(c, RX, 232, RW);

        c.text("DESCRIPTION", RX, 240, withAlpha(TEXT_FAINT, 220), false);
        WheelKit.drawWrapped(c, FIREBALL.lore(), RX, 252, RW - 4,
                withAlpha(TEXT_DIM, 235), 3);

        divider(c, RX, 288, RW);

        check(c, RX + 5F, 300F, withAlpha(TEAL, 235));
        c.text("BLUEPRINT KNOWN", RX + 12, 296, withAlpha(TEAL, 235), false);
        c.text("SOURCE: METEOR SCAR", RX, 308, withAlpha(TEXT_FAINT, 220), false);
        c.text("COLLECTION 1 / " + TOTAL_SPELLS, RX, 320, withAlpha(TEXT_FAINT, 200), false);
    }

    // ══════════════════════════════════════════════════════════════
    //  底部操作 / 提示条
    // ══════════════════════════════════════════════════════════════

    private static void actions(PreviewCanvas c, boolean forging) {
        c.fill(PANEL_X + 16, 330, PANEL_W - 32, 1, withAlpha(GOLD, 40));
        if (forging) {
            c.text("FORGING  -  KEEP THE MACHINE LOADED", LX, 342,
                    withAlpha(TEXT_FAINT, 215), false);
            button(c, 452, 334, 66, 18, "CANCEL", true, false);
            button(c, 526, 334, 74, 18, "FORGING", false, false);
        } else {
            c.text("BLUEPRINT IS CONSUMED WHEN FORGED", LX, 342,
                    withAlpha(TEXT_FAINT, 215), false);
            button(c, 466, 334, 134, 20, "FORGE SPELL", true, true);
        }
    }

    /** 主/次按钮。拼写与实机一致：主操作用金色描边 + 顶高光。 */
    private static void button(PreviewCanvas c, int x, int y, int w, int h,
                               String label, boolean enabled, boolean primary) {
        int border = !enabled ? 50 : (primary ? 210 : 130);
        int fill = !enabled ? 0x80070C15 : (primary ? 0x40E7C46A : 0xD20A1424);
        int fillBottom = enabled && primary ? 0x36C9A227 : fill;
        if (!SdfShapes.roundRect(c, x, y, w, h, 3F, fill, fillBottom,
                withAlpha(GOLD_BRIGHT, border), 0.9F)) {
            c.fill(x, y, w, h, fill);
            c.fill(x, y, w, 1, withAlpha(GOLD_BRIGHT, border));
        }
        if (enabled && primary) {
            c.fill(x + 3, y + 1, w - 6, 1, withAlpha(GOLD_BRIGHT, 90));
        }
        int color = !enabled ? withAlpha(TEXT_FAINT, 200)
                : (primary ? GOLD_BRIGHT : GOLD);
        c.textCentered(label, x + w / 2, y + (h - 9) / 2 + 1, color, enabled && primary);
    }

    // ══════════════════════════════════════════════════════════════
    //  CODEX 页
    // ══════════════════════════════════════════════════════════════

    private static void codex(PreviewCanvas c, float time) {
        background(c, time);
        header(c, time, "SPELL CODEX",
                "3 / " + TOTAL_SPELLS + " SPELLS DISCOVERED  -  FIND THE REST IN THE WORLD");
        panel(c);
        tabs(c, time, false);

        // 已知卡片之间的"星座连线"（先画，卡片会压在上面）
        float[][] iconCenters = new float[KNOWN.length][2];
        for (int i = 0; i < KNOWN.length; i++) {
            int col = i % 4;
            int row = i / 4;
            float x = 40F + col * 142F;
            float y = 82F + row * 112F;
            iconCenters[i][0] = x + 21F;
            iconCenters[i][1] = y + 21F;
        }
        for (int i = 0; i + 1 < iconCenters.length; i++) {
            WheelKit.line(c,
                    iconCenters[i][0], iconCenters[i][1],
                    iconCenters[i + 1][0], iconCenters[i + 1][1],
                    0.4F, withAlpha(GOLD, 60));
            float mx = (iconCenters[i][0] + iconCenters[i + 1][0]) * 0.5F;
            float my = (iconCenters[i][1] + iconCenters[i + 1][1]) * 0.5F;
            WheelKit.fillStar(c, mx, my, 2.6F, 1.0F, 4, 0F, withAlpha(GOLD_BRIGHT, 120));
        }

        for (int i = 0; i < TOTAL_SPELLS; i++) {
            int col = i % 4;
            int row = i / 4;
            float x = 40F + col * 142F;
            float y = 82F + row * 112F;
            if (i < KNOWN.length) {
                knownCard(c, KNOWN[i], x, y, i == 0);
            } else {
                unknownCard(c, x, y);
            }
        }

        // 选中卡片（Fireball）的来源提示 + 去合台按钮
        SdfShapes.roundRect(c, 40, 300, 560, 32, 4F,
                0xE60A1422, 0xE60A1422, withAlpha(GOLD, 70), 0.8F);
        WheelKit.drawSpellIcon(c, FIREBALL, 60F, 316F, 20F, 1F, 0.4F);
        c.text("FIREBALL", 76, 304, withAlpha(GOLD_BRIGHT, 240), true);
        c.text("BLUEPRINT RECOVERED FROM A METEOR SCAR  -  READY TO FORGE",
                76, 316, withAlpha(TEXT_DIM, 230), false);
        button(c, 496, 307, 96, 18, "OPEN FORGE", true, true);

        c.textRight("KNOWN " + KNOWN.length + " / " + TOTAL_SPELLS, 600, 60,
                withAlpha(TEXT_FAINT, 220), false);

        hintBar(c,
                "UNKNOWN SPELLS REVEAL A SOURCE HINT ONCE THEIR BLUEPRINT IS FOUND",
                "FORGE  /  CODEX     -     ESC  CLOSE");
    }

    /** 已发现法术卡：图标 + 名称/学派/数值 + 蓝图就绪。 */
    private static void knownCard(PreviewCanvas c, WheelKit.Spell spell,
                                  float x, float y, boolean selected) {
        int accent = argb(spell.color(), selected ? 1F : 0.85F);
        int border = selected ? withAlpha(GOLD_BRIGHT, 150) : withAlpha(accent, 90);
        SdfShapes.roundRect(c, x, y, 132, 98, 4F,
                selected ? 0xE6141E30 : 0xE60B1422, 0xE60B1422, border, 0.8F);
        if (selected) {
            c.fill((int) x, (int) y, 132, 2, withAlpha(GOLD_BRIGHT, 130));
        }
        WheelKit.drawSpellIcon(c, spell, x + 21F, y + 21F, 22F, 1F, selected ? 0.9F : 0.4F);
        c.text(spell.name(), (int) x + 38, (int) y + 10, withAlpha(TEXT, 245), true);
        c.text(spell.schoolLabel(), (int) x + 38, (int) y + 22, accent, false);
        c.text(spell.mana() + " MANA  -  " + formatCooldown(spell.cooldownSeconds()),
                (int) x + 38, (int) y + 34, withAlpha(TEXT_FAINT, 220), false);
        c.fill((int) x + 10, (int) y + 52, 112, 1, withAlpha(GOLD, 45));
        check(c, x + 15F, y + 66F, withAlpha(TEAL, 235));
        c.text("BLUEPRINT KNOWN", (int) x + 22, (int) y + 61, withAlpha(TEAL, 215), false);
        chip(c, x + 84, y + 78, 40, "FORGE", withAlpha(GOLD, 200));
    }

    /** 未发现法术卡：没有图标、没有学派，只给来源提示的位置。 */
    private static void unknownCard(PreviewCanvas c, float x, float y) {
        SdfShapes.roundRect(c, x, y, 132, 98, 4F,
                0xC0090E18, 0xC0090E18, withAlpha(LOCKED, 55), 0.8F);
        // 暗色图标井
        SdfShapes.roundRect(c, x + 10, y + 10, 22, 22, 3F,
                0x66070C14, 0x66070C14, withAlpha(LOCKED, 45), 0.7F);
        c.textCenteredScaled("?", x + 21F, y + 14F, withAlpha(LOCKED, 190), false, 1.6F);
        c.text("UNKNOWN SPELL", (int) x + 38, (int) y + 12, withAlpha(LOCKED, 200), true);
        c.text("BLUEPRINT MISSING", (int) x + 38, (int) y + 24, withAlpha(TEXT_FAINT, 180), false);
        c.fill((int) x + 10, (int) y + 52, 112, 1, withAlpha(LOCKED, 35));
        c.textCentered("FIND ITS BLUEPRINT", Math.round(x + 66F), (int) y + 62,
                withAlpha(TEXT_FAINT, 200), false);
        c.textCentered("IN THE WORLD", Math.round(x + 66F), (int) y + 74,
                withAlpha(TEXT_FAINT, 175), false);
        lockGlyph(c, x + 66F, y + 90F, withAlpha(LOCKED, 140));
    }

    // ══════════════════════════════════════════════════════════════
    //  背景 / 头部 / 面板
    // ══════════════════════════════════════════════════════════════

    private static void background(PreviewCanvas c, float time) {
        c.clear(0xFF02040A);

        // 项目真实 GUI 着色器：流动星云 + 三层视差星点（MagicScreen 同款）。
        PreviewShader starfield = c.shader("magic_starfield");
        starfield.set("uTime", time);
        starfield.set("uSize", (float) c.width, (float) c.height);
        starfield.set("uMouse", 0F, 0F);
        starfield.set("uIntensity", 1.25F);
        c.shaderQuad(starfield, 0, 0, c.width, c.height);

        c.fill(0, 0, c.width, c.height, 0x3D020810);
        c.fillGradientV(0, 0, c.width, 86, 0x8C000000, 0x00000000);
        c.fillGradientV(0, c.height - 96, c.width, 96, 0x00000000, 0x99000000);

        // 左右暗角：没有水平渐变 API，用 2px 竖条按 alpha 阶梯近似。
        for (int i = 0; i < 22; i++) {
            int alpha = (22 - i) * 3;
            if (alpha <= 0) {
                continue;
            }
            c.fill(i * 2, 0, 2, c.height, alpha << 24);
            c.fill(c.width - (i + 1) * 2, 0, 2, c.height, alpha << 24);
        }

        // 四角金色短线
        int margin = 8;
        int arm = 22;
        int color = withAlpha(GOLD, 70);
        WheelKit.line(c, margin, margin + 6, margin, margin, 0.6F, color);
        WheelKit.line(c, margin, margin, margin + arm, margin, 0.6F, color);
        WheelKit.line(c, c.width - margin - arm, margin, c.width - margin, margin, 0.6F, color);
        WheelKit.line(c, c.width - margin, margin, c.width - margin, margin + 6, 0.6F, color);
        WheelKit.line(c, margin, c.height - margin - 6, margin, c.height - margin, 0.6F, color);
        WheelKit.line(c, margin, c.height - margin, margin + arm, c.height - margin, 0.6F, color);
        WheelKit.line(c, c.width - margin - arm, c.height - margin, c.width - margin,
                c.height - margin, 0.6F, color);
        WheelKit.line(c, c.width - margin, c.height - margin, c.width - margin,
                c.height - margin - 6, 0.6F, color);
    }

    private static void header(PreviewCanvas c, float time, String title, String subtitle) {
        c.textCenteredScaled(title, c.width / 2F, 4, withAlpha(GOLD_BRIGHT, 245), true, 2F);
        c.textCentered(subtitle, c.width / 2, 25, withAlpha(GOLD, 175), false);

        // 标题两侧的渐隐金线 + 星点
        float half = c.textWidth(title);
        float leftNear = c.width / 2F - half - 16F;
        float rightNear = c.width / 2F + half + 16F;
        flank(c, leftNear, leftNear - 88F, true);
        flank(c, rightNear, rightNear + 88F, false);

        c.text("ROTTEN RUINS", 16, 10, withAlpha(GOLD, 130), false);
        c.textRight("ESC  CLOSE", c.width - 16, 10, withAlpha(TEXT_FAINT, 190), false);

        // 标题下方流动金线（项目创造栏隔断着色器；只在此处用一次）
        PreviewShader divider = c.shader("gui_tab_divider");
        divider.set("uTime", time);
        divider.set("uSize", 180F, 3F);
        divider.set("uColorA", WheelKit.GOLD_RGBA[0], WheelKit.GOLD_RGBA[1],
                WheelKit.GOLD_RGBA[2], 1F);
        divider.set("uColorB", 1.0F, 0.894F, 0.604F, 0.35F);
        divider.set("uColorC", WheelKit.HAZE_RGBA[0], WheelKit.HAZE_RGBA[1],
                WheelKit.HAZE_RGBA[2], 0.25F);
        divider.set("uIntensity", 0.85F);
        divider.set("uGlow", 0.7F);
        c.shaderQuad(divider, c.width / 2 - 90, 38, 180, 3);
    }

    private static void flank(PreviewCanvas c, float nearX, float farX, boolean leftSide) {
        int segments = 10;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float a0 = 24F + 96F * t0 * t0;
            float a1 = 24F + 96F * t1 * t1;
            float x0 = leftSide ? farX + (nearX - farX) * t0 : nearX + (farX - nearX) * t0;
            float x1 = leftSide ? farX + (nearX - farX) * t1 : nearX + (farX - nearX) * t1;
            WheelKit.line(c, x0, 13F, x1, 13F, 0.5F,
                    withAlpha(GOLD, Math.round((a0 + a1) * 0.5F)));
        }
        WheelKit.fillStar(c, farX, 13F, 3.0F, 1.1F, 4, 0F, withAlpha(GOLD_BRIGHT, 170));
    }

    private static void panel(PreviewCanvas c) {
        // 投影
        SdfShapes.roundRect(c, PANEL_X + 3, PANEL_Y + 4, PANEL_W, PANEL_H, 10F,
                0x4D000000, 0x4D000000, 0, 0F);
        // 面板本体：深蓝到近黑竖向渐变 + 金描边
        SdfShapes.roundRect(c, PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 10F,
                0xF00D1828, 0xF0080E19, withAlpha(GOLD, 95), 0.9F);

        // 顶部 2px 金色渐变线（中间亮、两端收进描边）
        int segments = 24;
        int segW = Math.max(1, PANEL_W / segments);
        for (int i = 0; i < segments; i++) {
            float t = (i + 0.5F) / segments;
            float k = (float) Math.sin(Math.PI * t);
            int alpha = Math.round(165 * k);
            if (alpha <= 2) {
                continue;
            }
            c.fill(PANEL_X + i * segW, PANEL_Y, segW, 2, withAlpha(GOLD_BRIGHT, alpha));
        }

        // 四角短线（和装载界面同一套 chrome）
        int color = withAlpha(GOLD_BRIGHT, 110);
        int len = 7;
        int inset = 3;
        WheelKit.line(c, PANEL_X + inset, PANEL_Y + inset + len,
                PANEL_X + inset, PANEL_Y + inset, 0.5F, color);
        WheelKit.line(c, PANEL_X + inset, PANEL_Y + inset,
                PANEL_X + inset + len, PANEL_Y + inset, 0.5F, color);
        WheelKit.line(c, PANEL_X + PANEL_W - inset, PANEL_Y + inset + len,
                PANEL_X + PANEL_W - inset, PANEL_Y + inset, 0.5F, color);
        WheelKit.line(c, PANEL_X + PANEL_W - inset, PANEL_Y + inset,
                PANEL_X + PANEL_W - inset - len, PANEL_Y + inset, 0.5F, color);
        WheelKit.line(c, PANEL_X + inset, PANEL_Y + PANEL_H - inset - len,
                PANEL_X + inset, PANEL_Y + PANEL_H - inset, 0.5F, color);
        WheelKit.line(c, PANEL_X + inset, PANEL_Y + PANEL_H - inset,
                PANEL_X + inset + len, PANEL_Y + PANEL_H - inset, 0.5F, color);
        WheelKit.line(c, PANEL_X + PANEL_W - inset, PANEL_Y + PANEL_H - inset - len,
                PANEL_X + PANEL_W - inset, PANEL_Y + PANEL_H - inset, 0.5F, color);
        WheelKit.line(c, PANEL_X + PANEL_W - inset, PANEL_Y + PANEL_H - inset,
                PANEL_X + PANEL_W - inset - len, PANEL_Y + PANEL_H - inset, 0.5F, color);
    }

    private static void tabs(PreviewCanvas c, float time, boolean forgeActive) {
        tab(c, 40, 52, 62, "FORGE", forgeActive);
        tab(c, 108, 52, 62, "CODEX", !forgeActive);
    }

    private static void tab(PreviewCanvas c, float x, float y, float w, String label,
                            boolean active) {
        int fill = active ? 0x33E7C46A : 0x22020810;
        if (SdfShapes.roundRect(c, x, y, w, 17, 3F, fill, fill,
                withAlpha(GOLD, active ? 190 : 70), 0.8F)) {
            if (active) {
                c.fill((int) x + 4, (int) y + 17, (int) w - 8, 1,
                        withAlpha(GOLD_BRIGHT, 160));
            }
        }
        c.textCentered(label, Math.round(x + w / 2F), Math.round(y + 4F),
                withAlpha(active ? GOLD_BRIGHT : TEXT_FAINT, active ? 245 : 210), false);
    }

    private static void hintBar(PreviewCanvas c, String left, String right) {
        int y = 374;
        c.fill(0, y, c.width, 22, 0xB0050912);
        c.fill(0, y, c.width, 1, withAlpha(GOLD, 75));
        WheelKit.fillStar(c, 18F, y + 11F, 3.2F, 1.0F, 4, 0F, withAlpha(GOLD, 210));
        c.text(left, 28, y + 7, withAlpha(TEXT_DIM, 235), false);
        c.textRight(right, c.width - 16, y + 7, withAlpha(TEXT_FAINT, 220), false);
    }

    // ══════════════════════════════════════════════════════════════
    //  小部件
    // ══════════════════════════════════════════════════════════════

    /** 蓝图图标：深蓝卡面 + 内部星座线 + 金色四角星。 */
    private static void blueprintIcon(PreviewCanvas c, float x, float y, float size, float time) {
        SdfShapes.roundRect(c, x, y, size, size, 3F,
                0xFF0B1828, 0xFF0B1828, withAlpha(GOLD, 150), 0.8F);
        SdfShapes.roundRect(c, x + 3F, y + 3F, size - 6F, size - 6F, 2F,
                0x00000000, 0x00000000, withAlpha(GOLD, 40), 0.5F);
        // 卡面星座：4 个点两两相连
        float[] dx = {-7F, 2F, 8F, -3F};
        float[] dy = {-8F, -3F, 6F, 7F};
        for (int i = 0; i < 3; i++) {
            WheelKit.line(c, x + size * 0.5F + dx[i], y + size * 0.5F + dy[i],
                    x + size * 0.5F + dx[i + 1], y + size * 0.5F + dy[i + 1],
                    0.35F, withAlpha(TEAL, 90));
        }
        for (int i = 0; i < 4; i++) {
            WheelKit.fillDisc(c, x + size * 0.5F + dx[i], y + size * 0.5F + dy[i],
                    1.2F, withAlpha(TEAL, 170));
        }
        WheelKit.fillStar(c, x + size * 0.5F, y + size * 0.5F,
                size * 0.26F, size * 0.10F, 4, time * 0.3F,
                withAlpha(GOLD_BRIGHT, 220));
        // 右上角折页
        c.fill((int) (x + size - 9), (int) y, 9, 1, withAlpha(GOLD, 90));
        c.fill((int) (x + size - 1), (int) y, 1, 9, withAlpha(GOLD, 90));
    }

    private static void slot(PreviewCanvas c, float x, float y, float size) {
        SdfShapes.roundRect(c, x, y, size, size, 2.5F,
                0xFF08111F, 0xFF0D1828, withAlpha(GOLD, 80), 0.7F);
    }

    private static void chip(PreviewCanvas c, float x, float y, float w,
                             String label, int color) {
        if (SdfShapes.roundRect(c, x, y, w, 11, 5.5F, 0x66020810, 0x66020810, color, 0.6F)) {
            c.textCentered(label, Math.round(x + w / 2F), (int) y + 1,
                    withAlpha(color, 235), false);
        }
    }

    private static void lockGlyph(PreviewCanvas c, float cx, float cy, int color) {
        // 锁梁（上半环）+ 锁体
        WheelKit.fillArc(c, cx, cy - 1F, 3.2F, 4.4F, (float) Math.PI, TAU, color, 20);
        c.fill((int) (cx - 5F), (int) (cy + 1F), 10, 7, withAlpha(color, 190));
        c.fill((int) (cx - 1F), (int) (cy + 3F), 2, 3, 0xFF0A101C);
    }

    private static void check(PreviewCanvas c, float cx, float cy, int color) {
        WheelKit.line(c, cx - 3.5F, cy, cx - 1F, cy + 2.5F, 0.7F, color);
        WheelKit.line(c, cx - 1F, cy + 2.5F, cx + 3.5F, cy - 2.5F, 0.7F, color);
    }

    private static void divider(PreviewCanvas c, float x, float y, float w) {
        c.fill((int) x, (int) y, (int) w, 1, withAlpha(GOLD, 45));
    }

    private static void stat(PreviewCanvas c, int y, String label, String value) {
        c.text(label, RX, y, withAlpha(TEXT_FAINT, 220), false);
        c.textRight(value, RX + RW, y, withAlpha(TEXT, 240), false);
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
        return String.format(Locale.ROOT, "%.1fs COOLDOWN", ticks / 20.0F);
    }

    private static String formatCooldown(float seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    private static int argb(float[] rgb, float alpha) {
        return WheelKit.argb(rgb, alpha);
    }

    private static int withAlpha(int color, int alpha) {
        return WheelKit.withAlpha(color, alpha);
    }
}
