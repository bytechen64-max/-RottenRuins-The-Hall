/**
 * 场景：<b>魔法装载界面</b>（Spell Loadout / 轮盘配置）—— 不启动游戏，按 640×400 逻辑画布
 * （导出 ×2 = 1280×800）模拟。
 *
 * <p>设计目标：把「魔法轮盘施法 HUD」的 8 个方位配置化。左侧是法术图鉴（主动 / 被动两页），
 * 中间是轮盘预览（用 {@link WheelKit#drawWheel} 的真实渲染器，所以和 HUD 完全同一套视觉），
 * 右侧是选中法术详情 + 操作按钮；底部是操作提示与轮盘页签。</p>
 *
 * <h3>三帧</h3>
 * <ul>
 *   <li>{@code spell_loadout_drag.png} —— 拖拽态：Suppression Field 卡片拖向第 6 个 socket，
 *       光标处半透明卡片幽灵、目标 socket 脉冲环、轮盘外圈金色瞄准环 + 一条金色虚线；</li>
 *   <li>{@code spell_loadout_passive.png} —— PASSIVE 页：两张被动卡禁用带锁，右侧详情显示
 *       "PASSIVE SPELLS CANNOT BE PLACED ON THE WHEEL"，轮盘压暗成只读；</li>
 *   <li>{@code spell_loadout.png} —— 主图（<b>最后画</b>，主入口的自动保存就是它）：ACTIVE 页、
 *       悬停 Fireball 卡片、轮盘已绑定 3 个、右侧详情 Fireball、按钮与页签齐备。</li>
 * </ul>
 *
 * <h3>与 WheelKit 的衔接</h3>
 * <p>轮盘、卡片、详情全部调用 {@code WheelKit} 的共享渲染器，数据也直接用
 * {@code WheelKit.ACTIVE_SPELLS / PASSIVE_SPELLS / defaultWheel()}（Fireball / Spear of
 * Judgment / Suppression Field 的数值取自项目真实 {@code MagicStats} 语义）。</p>
 * <p>轮盘比 HUD 小（外径 84 / 内径 34），方位角标直接用 {@code WheelKit} 的自适应半径
 * （它会收进核心与图标之间，见 {@link #BADGE_RADIUS} 的同一公式），拖拽帧再额外圈住目标角标。</p>
 *
 * <p>画面文字全部英文 ASCII（MC 位图字体只覆盖拉丁/符号；离屏字体里没有 {@code ·}、
 * {@code —} 这类码点，所以场景自己的文案一律用 {@code -} / {@code |} 分隔）。</p>
 */
public final class SceneSpellLoadout {

    private SceneSpellLoadout() {
    }

    // ══════════════════════════════════════════════════════════════
    //  布局常量（640×400 逻辑画布）
    // ══════════════════════════════════════════════════════════════

    private static final int PANEL_Y = 48;
    private static final int PANEL_H = 304;

    private static final int LEFT_X = 14;
    private static final int LEFT_W = 198;

    private static final int RIGHT_X = 428;
    private static final int RIGHT_W = 198;

    /** 轮盘预览几何。外径 84 是给外圈瞄准环（半径 ~96）留出左右各 12px 的余量。 */
    private static final float WHEEL_CX = 320F;
    private static final float WHEEL_CY = 196F;
    private static final float WHEEL_OUTER = 84F;
    private static final float WHEEL_INNER = 34F;

    /** 轮盘图标中心半径与图标尺寸；用来复算 WheelKit 的自适应角标半径（拖拽帧要圈住目标角标）。 */
    private static final float ICON_RADIUS = (WHEEL_OUTER + WHEEL_INNER) * 0.5F;   // 59
    private static final float ICON_SIZE = 16F * 1.5F;                            // iconScale = 1.5
    /** 与 WheelKit.drawWheel 里 badgeRadius 的自适应公式保持一致。 */
    private static final float BADGE_RADIUS = Math.max(WHEEL_INNER + 8F,
            Math.min(WHEEL_INNER + 22F, ICON_RADIUS - ICON_SIZE * 0.62F - 8F));   // 42

    /** 右侧详情面板高度；余下 54px 是按钮脚栏（和左侧面板底部对齐）。 */
    private static final int DETAIL_H = 250;
    private static final int FOOTER_Y = PANEL_Y + DETAIL_H;             // 298
    private static final int FOOTER_H = PANEL_H - DETAIL_H;             // 54

    /** 底部提示条。 */
    private static final int BOTTOM_BAR_Y = 360;
    private static final int BOTTOM_BAR_H = 34;

    /** 拖拽帧的目标 socket（第 6 个方位 = 下标 5）。 */
    private static final int DRAG_TARGET_SLOT = 5;

    // ══════════════════════════════════════════════════════════════
    //  调色（与 MagicScreen / WheelKit 同一套：金色 #E7C46A、深空底）
    // ══════════════════════════════════════════════════════════════

    private static final int PANEL_BG = 0xE60A1020;

    // ══════════════════════════════════════════════════════════════
    //  状态
    // ══════════════════════════════════════════════════════════════

    private enum Mode {
        /** 主图：ACTIVE 页、悬停 Fireball、轮盘 3 个绑定。 */
        MAIN,
        /** 拖拽中：Suppression -> socket 6。 */
        DRAG,
        /** PASSIVE 页：只读。 */
        PASSIVE
    }

    private static boolean diagnosticsLogged;

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        logDiagnosticsOnce(c);

        // 两张状态图先画（相位错开一点，让脉冲/呼吸动画不重样）。
        drawFrame(c, time + 0.9F, Mode.DRAG);
        c.save("spell_loadout_drag");

        drawFrame(c, time + 2.1F, Mode.PASSIVE);
        c.save("spell_loadout_passive");

        // 主图最后画：GuiPreview 在 render 返回后会把当前帧自动存成 spell_loadout.png。
        drawFrame(c, time, Mode.MAIN);
        c.save("spell_loadout");
    }

    /** 一次性把关键文案宽度打出来，方便确认没有任何一行溢出卡片 / 面板。 */
    private static void logDiagnosticsOnce(PreviewCanvas c) {
        if (diagnosticsLogged) {
            return;
        }
        diagnosticsLogged = true;
        System.out.println("[scene-spell-loadout] canvas=" + c.width + "x" + c.height
                + "  panels: left=(" + LEFT_X + "," + PANEL_Y + "," + LEFT_W + "," + PANEL_H
                + ") right=(" + RIGHT_X + "," + PANEL_Y + "," + RIGHT_W + "," + PANEL_H
                + ")  wheel=(" + (int) WHEEL_CX + "," + (int) WHEEL_CY
                + ",r" + (int) WHEEL_OUTER + "/" + (int) WHEEL_INNER + ")");
        String[] widths = {
                "ARCANE LOADOUT",
                "WHEEL CONFIGURATION  -  PAGE I",
                "SPELL CODEX",
                "PASSIVE SPELLS ARE ALWAYS ACTIVE",
                "AND CANNOT BE BOUND TO THE WHEEL",
                "CLEAR SLOT",
                "AUTO FILL",
                "SLOTS 3/8",
                "SOCKET 6  -  RELEASE TO BIND",
                "WHEEL LOCKED  -  PASSIVE PAGE",
                "HOLD  [R]  TO OPEN THIS WHEEL",
                "Drag a spell into a socket   |   Right-click to clear   |   Passives cannot be bound",
                "RIGHT-CLICK A SOCKET TO CLEAR",
                "WHEEL I  -  TARGETING SOCKET 6"
        };
        for (String s : widths) {
            System.out.println("[scene-spell-loadout] textWidth " + c.textWidth(s) + "  \"" + s + "\"");
        }
    }

    private static void drawFrame(PreviewCanvas c, float time, Mode mode) {
        drawBackground(c, time);
        // 轮盘先画：wheel_aura 的加色 quad 会溢出到两侧面板的位置，
        // 后画的面板底色正好把它裁掉，不会在面板边缘留下加色光带。
        drawWheelPreview(c, time, mode);
        drawHeader(c, time);
        drawLeftPanel(c, time, mode);
        drawRightPanel(c, time, mode);
        drawCenterOverlay(c, time, mode);
        drawBottomBar(c, time, mode);
        if (mode == Mode.MAIN) {
            drawPointer(c, 188F, 123F);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  背景：项目深空星图 + 暗角
    // ══════════════════════════════════════════════════════════════

    private static void drawBackground(PreviewCanvas c, float time) {
        c.clear(0xFF02040A);

        // 项目真实 GUI 着色器：流动星云 + 三层视差星点（MagicScreen.renderBackground 同款）。
        PreviewShader starfield = c.shader("magic_starfield");
        starfield.set("uTime", time);
        starfield.set("uSize", (float) c.width, (float) c.height);
        starfield.set("uMouse", 0F, 0F);
        starfield.set("uIntensity", 1F);
        c.shaderQuad(starfield, 0, 0, c.width, c.height);

        // 压暗一层，让三块面板从星空中跳出来。
        c.fill(0, 0, c.width, c.height, 0x4D020810);
        // 上下深空渐变。
        c.fillGradientV(0, 0, c.width, 84, 0x8C000000, 0x00000000);
        c.fillGradientV(0, c.height - 96, c.width, 96, 0x00000000, 0x99000000);
        // 左右暗角：没有水平渐变 API，用 2px 竖条按 alpha 阶梯近似。
        for (int i = 0; i < 24; i++) {
            int alpha = (24 - i) * 3;
            if (alpha <= 0) {
                continue;
            }
            c.fill(i * 2, 0, 2, c.height, alpha << 24);
            c.fill(c.width - (i + 1) * 2, 0, 2, c.height, alpha << 24);
        }

        drawScreenCorners(c);
    }

    /** 整屏四角的金色装饰短线（HUD 里 drawHudChrome 的四角装饰，这里按新画布重画）。 */
    private static void drawScreenCorners(PreviewCanvas c) {
        int margin = 8;
        int arm = 22;
        int color = WheelKit.withAlpha(WheelKit.GOLD, 70);
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

    // ══════════════════════════════════════════════════════════════
    //  顶部标题
    // ══════════════════════════════════════════════════════════════

    private static void drawHeader(PreviewCanvas c, float time) {
        c.textCenteredScaled("ARCANE LOADOUT", (int) WHEEL_CX, 6,
                WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 245), true, 2F);
        c.textCentered("WHEEL CONFIGURATION  -  PAGE I", (int) WHEEL_CX, 27,
                WheelKit.withAlpha(WheelKit.GOLD, 175), false);

        // 标题两侧的金线 + 星点（长度按标题实际宽度算，改文案也不会撞）。
        float titleHalf = c.textWidth("ARCANE LOADOUT") * 1F;      // 2x 后的半宽
        float leftEnd = WHEEL_CX - titleHalf - 16F;
        float rightStart = WHEEL_CX + titleHalf + 16F;
        drawFlankLine(c, leftEnd, 176F, true);
        drawFlankLine(c, rightStart, 462F, false);

        // 左上品牌 / 右上关闭提示（MagicScreen 的 M / ESC 关闭语义）。
        c.text("ROTTEN RUINS", 16, 10, WheelKit.withAlpha(WheelKit.GOLD, 130), false);
        c.textRight("M / ESC   CLOSE", c.width - 16, 10,
                WheelKit.withAlpha(WheelKit.TEXT_FAINT, 190), false);

        // 标题下方的流动金线（项目创造栏 gui_tab_divider 着色器；只在此处用一次）。
        PreviewShader divider = c.shader("gui_tab_divider");
        divider.set("uTime", time);
        divider.set("uSize", 180F, 3F);
        divider.set("uColorA", WheelKit.GOLD_RGBA[0], WheelKit.GOLD_RGBA[1], WheelKit.GOLD_RGBA[2], 1F);
        divider.set("uColorB", 1.0F, 0.894F, 0.604F, 0.35F);
        divider.set("uColorC", WheelKit.HAZE_RGBA[0], WheelKit.HAZE_RGBA[1], WheelKit.HAZE_RGBA[2], 0.25F);
        divider.set("uIntensity", 0.85F);
        divider.set("uGlow", 0.7F);
        c.shaderQuad(divider, (int) WHEEL_CX - 90, 40, 180, 3);
    }

    /** 标题一侧的渐隐金线：alpha 从外端到标题端逐渐变亮，末端一颗小星。 */
    private static void drawFlankLine(PreviewCanvas c, float nearX, float farX, boolean leftSide) {
        int segments = 10;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float a0 = 24F + 96F * t0 * t0;
            float a1 = 24F + 96F * t1 * t1;
            float x0 = leftSide ? farX + (nearX - farX) * t0 : nearX + (farX - nearX) * t0;
            float x1 = leftSide ? farX + (nearX - farX) * t1 : nearX + (farX - nearX) * t1;
            WheelKit.line(c, x0, 15F, x1, 15F, 0.5F,
                    WheelKit.withAlpha(WheelKit.GOLD, Math.round((a0 + a1) * 0.5F)));
        }
        WheelKit.fillStar(c, farX, 15F, 3.0F, 1.1F, 4, 0F,
                WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 170));
    }

    // ══════════════════════════════════════════════════════════════
    //  左面板：SPELL CODEX（ACTIVE / PASSIVE 两页）
    // ══════════════════════════════════════════════════════════════

    private static void drawLeftPanel(PreviewCanvas c, float time, Mode mode) {
        boolean passivePage = mode == Mode.PASSIVE;
        drawPanelChrome(c, LEFT_X, PANEL_Y, LEFT_W, PANEL_H);

        // 标题行
        c.text("SPELL CODEX", LEFT_X + 10, PANEL_Y + 9,
                WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 240), true);
        String count = passivePage ? "2 PASSIVE" : "3 ACTIVE";
        c.textRight(count, LEFT_X + LEFT_W - 10, PANEL_Y + 9,
                WheelKit.withAlpha(WheelKit.GOLD, 165), false);

        // ACTIVE / PASSIVE 页签
        int tabY = PANEL_Y + 24;
        int tabW = 85;
        drawTab(c, LEFT_X + 10, tabY, tabW, 17, "ACTIVE", !passivePage, time);
        drawTab(c, LEFT_X + 101, tabY, tabW, 17, "PASSIVE", passivePage, time);
        c.fill(LEFT_X + 10, PANEL_Y + 47, LEFT_W - 20, 1,
                WheelKit.withAlpha(WheelKit.GOLD, 45));

        // 卡片区
        int cardX = LEFT_X + 10;
        int cardW = LEFT_W - 20;
        if (!passivePage) {
            for (int i = 0; i < WheelKit.ACTIVE_SPELLS.size(); i++) {
                WheelKit.Spell spell = WheelKit.ACTIVE_SPELLS.get(i);
                int state = (mode == Mode.MAIN && i == 0) || (mode == Mode.DRAG && i == 2) ? 1 : 0;
                WheelKit.drawSpellCard(c, spell, cardX, PANEL_Y + 54 + i * 38, cardW, 34,
                        state, i, time);
            }
            drawTipBox(c, cardX, PANEL_Y + 176, cardW, 44);
            // 悬停卡的额外四角括号：静态图里也能一眼看出鼠标停在哪张卡上
            int hovered = mode == Mode.MAIN ? 0 : (mode == Mode.DRAG ? 2 : -1);
            if (hovered >= 0) {
                drawCardHoverAccent(c, cardX, PANEL_Y + 54 + hovered * 38, cardW, 34, time);
            }
        } else {
            for (int i = 0; i < WheelKit.PASSIVE_SPELLS.size(); i++) {
                WheelKit.Spell spell = WheelKit.PASSIVE_SPELLS.get(i);
                // state=3：禁用 + 锁，和「被动不能进轮盘」的规则一致。
                WheelKit.drawSpellCard(c, spell, cardX, PANEL_Y + 54 + i * 38, cardW, 34,
                        3, -1, time);
            }
            drawPassiveHint(c);
        }

        drawSocketStatus(c, passivePage);
    }

    /** 一个页签：选中页金色下划线 + 呼吸辉光，未选页只留一条暗金发丝线。 */
    private static void drawTab(PreviewCanvas c, int x, int y, int w, int h,
                                String label, boolean selected, float time) {
        if (selected) {
            int pulse = 200 + Math.round(35F * (0.5F + 0.5F * (float) Math.sin(time * 2.4F)));
            c.fill(x, y, w, h, 0x2EE7C46A);
            c.fill(x, y + h - 2, w, 2, WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, pulse));
            c.fill(x, y + h, w, 2, WheelKit.withAlpha(WheelKit.GOLD, 45));
            c.textCentered(label, x + w / 2, y + 5,
                    WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 245), false);
            WheelKit.fillStar(c, x + 7F, y + 8.5F, 2.6F, 1.0F, 4, 0F,
                    WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 220));
        } else {
            c.fill(x, y, w, h, 0x26000000);
            c.fill(x, y + h - 1, w, 1, WheelKit.withAlpha(WheelKit.GOLD, 55));
            c.textCentered(label, x + w / 2, y + 5,
                    WheelKit.withAlpha(WheelKit.TEXT_FAINT, 235), false);
            WheelKit.fillDisc(c, x + 7F, y + 8.5F, 1.4F,
                    WheelKit.withAlpha(WheelKit.TEXT_FAINT, 130));
        }
    }

    /** 悬停卡片外圈的四角金色括号（呼吸）。 */
    private static void drawCardHoverAccent(PreviewCanvas c, int x, int y, int w, int h,
                                            float time) {
        int glow = 70 + Math.round(30F * (0.5F + 0.5F * (float) Math.sin(time * 3F)));
        int color = WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, glow);
        int arm = 8;
        int x0 = x - 2;
        int y0 = y - 2;
        int x1 = x + w + 1;
        int y1 = y + h + 1;
        WheelKit.line(c, x0, y0 + arm, x0, y0, 0.6F, color);
        WheelKit.line(c, x0, y0, x0 + arm, y0, 0.6F, color);
        WheelKit.line(c, x1 - arm, y0, x1, y0, 0.6F, color);
        WheelKit.line(c, x1, y0, x1, y0 + arm, 0.6F, color);
        WheelKit.line(c, x0, y1 - arm, x0, y1, 0.6F, color);
        WheelKit.line(c, x0, y1, x0 + arm, y1, 0.6F, color);
        WheelKit.line(c, x1 - arm, y1, x1, y1, 0.6F, color);
        WheelKit.line(c, x1, y1, x1, y1 - arm, 0.6F, color);
    }

    /** ACTIVE 页卡片下方的小提示盒，顺手把空白区撑起来。 */
    private static void drawTipBox(PreviewCanvas c, int x, int y, int w, int h) {
        c.fill(x, y, w, h, 0x40060C16);
        c.fill(x, y, 2, h, WheelKit.withAlpha(WheelKit.GOLD, 110));
        c.fill(x, y, w, 1, WheelKit.withAlpha(WheelKit.GOLD, 60));
        c.fill(x, y + h - 1, w, 1, WheelKit.withAlpha(WheelKit.GOLD, 30));
        c.text("HOW TO BIND", x + 8, y + 7, WheelKit.withAlpha(WheelKit.GOLD, 210), false);
        WheelKit.drawWrapped(c, "Pick a card, drag it onto a numbered socket, then release.",
                x + 8, y + 19, w - 16, WheelKit.withAlpha(WheelKit.TEXT_DIM, 225), 2);
    }

    /** PASSIVE 页：锁形标记 + 两行规则提示 + 一句说明。 */
    private static void drawPassiveHint(PreviewCanvas c) {
        int centerX = LEFT_X + LEFT_W / 2;
        drawLockGlyph(c, centerX, 184F, 1.0F, WheelKit.LOCKED);
        c.textCentered("PASSIVE SPELLS ARE ALWAYS ACTIVE", centerX, 206,
                WheelKit.withAlpha(WheelKit.GOLD, 225), false);
        c.textCentered("AND CANNOT BE BOUND TO THE WHEEL", centerX, 218,
                WheelKit.withAlpha(WheelKit.TEXT_DIM, 238), false);
        c.fill(LEFT_X + 10, 234, LEFT_W - 20, 1, WheelKit.withAlpha(WheelKit.GOLD, 35));
        WheelKit.drawWrapped(c, "Their effects stay on while learned; no socket is required.",
                LEFT_X + 16, 242, LEFT_W - 32, WheelKit.withAlpha(WheelKit.TEXT_FAINT, 225), 3);
    }

    /** 锁：上半环提手 + 锁体 + 钥匙孔（纯 CPU 几何，和卡片的锁图标呼应）。 */
    private static void drawLockGlyph(PreviewCanvas c, float cx, float topY, float scale, int color) {
        WheelKit.fillArc(c, cx, topY + 5F * scale, 4.6F * scale, 6.4F * scale,
                (float) Math.PI, (float) (Math.PI * 2.0), WheelKit.withAlpha(color, 225), 16);
        float w = 16F * scale;
        float h = 11F * scale;
        float bodyY = topY + 5F * scale;
        c.fill((int) (cx - w / 2F), (int) bodyY, (int) w, (int) h,
                WheelKit.withAlpha(WheelKit.INK, 235));
        int border = WheelKit.withAlpha(color, 215);
        WheelKit.line(c, cx - w / 2F, bodyY, cx + w / 2F, bodyY, 0.7F, border);
        WheelKit.line(c, cx - w / 2F, bodyY + h, cx + w / 2F, bodyY + h, 0.7F, border);
        WheelKit.line(c, cx - w / 2F, bodyY, cx - w / 2F, bodyY + h, 0.7F, border);
        WheelKit.line(c, cx + w / 2F, bodyY, cx + w / 2F, bodyY + h, 0.7F, border);
        WheelKit.fillDisc(c, cx, bodyY + h * 0.4F, 1.7F * scale, WheelKit.withAlpha(color, 235));
        WheelKit.line(c, cx, bodyY + h * 0.5F, cx, bodyY + h * 0.85F, 0.9F * scale,
                WheelKit.withAlpha(color, 235));
    }

    /** 左面板底部：轮盘 socket 使用情况（8 格小方块）+ 两行操作提示。 */
    private static void drawSocketStatus(PreviewCanvas c, boolean readOnly) {
        int x = LEFT_X + 10;
        int w = LEFT_W - 20;
        c.fill(x, 284, w, 1, WheelKit.withAlpha(WheelKit.GOLD, 45));
        c.text("SOCKETS", x, 292, WheelKit.withAlpha(WheelKit.GOLD, 205), false);
        c.textRight("3 / 8", x + w, 292,
                WheelKit.withAlpha(readOnly ? WheelKit.TEXT_FAINT : WheelKit.GOLD_BRIGHT, 230), false);

        int pipY = 306;
        int pipW = 18;
        int pipH = 5;
        int gap = 3;
        for (int i = 0; i < 8; i++) {
            int px = x + i * (pipW + gap);
            if (i < 3) {
                c.fill(px, pipY, pipW, pipH,
                        WheelKit.withAlpha(WheelKit.GOLD, readOnly ? 90 : 190));
                c.fill(px, pipY, pipW, 1,
                        WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, readOnly ? 60 : 150));
            } else {
                c.fill(px, pipY, pipW, pipH, 0x50020810);
                c.fill(px, pipY, pipW, 1,
                        WheelKit.withAlpha(WheelKit.GOLD, readOnly ? 22 : 55));
            }
        }

        if (readOnly) {
            c.text("WHEEL IS READ-ONLY", x, 320, WheelKit.withAlpha(WheelKit.TEXT_FAINT, 230), false);
            c.text("PASSIVES NEED NO SOCKET", x, 332,
                    WheelKit.withAlpha(WheelKit.TEXT_FAINT, 210), false);
        } else {
            c.text("DRAG A CARD ONTO THE WHEEL", x, 320,
                    WheelKit.withAlpha(WheelKit.TEXT_FAINT, 230), false);
            c.text("RIGHT-CLICK A SOCKET TO CLEAR", x, 332,
                    WheelKit.withAlpha(WheelKit.TEXT_FAINT, 210), false);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  中间：轮盘预览
    // ══════════════════════════════════════════════════════════════

    private static void drawWheelPreview(PreviewCanvas c, float time, Mode mode) {
        boolean passive = mode == Mode.PASSIVE;
        WheelKit.Spell[] wheel = WheelKit.defaultWheel();

        // 轮盘底下的环境光（和 HUD 一样的冷蓝底光，用 CPU 圆盘叠出来）。
        for (int i = 5; i >= 1; i--) {
            WheelKit.fillDisc(c, WHEEL_CX, WHEEL_CY, WHEEL_OUTER + i * 15F,
                    WheelKit.withAlpha(0x1E3C66, 4 + i * 2));
        }

        WheelKit.WheelOptions options = new WheelKit.WheelOptions();
        options.time = time;
        options.openT = 1F;
        options.mana = 182F;
        options.maxMana = 240F;
        options.loadoutMode = true;
        options.showCenterInfo = false;      // 中心文字由场景自己写（"SLOTS 3/8" / "WHEEL I"）
        options.showEmptySlots = true;
        options.showKeybinds = false;
        // WheelKit 的方位角标半径是自适应的（小轮盘下会收进核心与图标之间），直接用它。
        options.showSlotNumbers = true;
        options.iconScale = 1.5F;
        options.intensity = passive ? 0.34F : 1F;
        if (mode == Mode.DRAG) {
            options.target = DRAG_TARGET_SLOT;
            // wheel_aura 的暖色锥只读 hovered / selected，这里让锥指向目标方位。
            options.hovered = DRAG_TARGET_SLOT;
        }
        WheelKit.drawWheel(c, wheel, WHEEL_CX, WHEEL_CY, WHEEL_OUTER, WHEEL_INNER, options);

        drawWheelCenter(c, wheel, mode);
    }

    /** 轮盘中心：1x 金色 "SLOTS 3/8" + "WHEEL I"（小轮盘的核心只有 60px 宽）。 */
    private static void drawWheelCenter(PreviewCanvas c, WheelKit.Spell[] wheel, Mode mode) {
        boolean passive = mode == Mode.PASSIVE;
        int bound = 0;
        for (WheelKit.Spell spell : wheel) {
            if (spell != null) {
                bound++;
            }
        }
        int bright = passive ? 120 : 240;
        int dim = passive ? 85 : 180;

        // 核心着色器本身很亮，文字直接压上去会被辉光吃掉；中间垫一层暗色圆盘当“表盘”。
        WheelKit.fillDisc(c, WHEEL_CX, WHEEL_CY, 27.5F,
                WheelKit.withAlpha(0xFF04070D, passive ? 190 : 210));
        WheelKit.fillRing(c, WHEEL_CX, WHEEL_CY, 27.5F, 0.5F,
                WheelKit.withAlpha(WheelKit.GOLD, passive ? 45 : 85), 44);

        c.textCentered("SLOTS " + bound + "/8", (int) WHEEL_CX, Math.round(WHEEL_CY - 15F),
                WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, bright), true);
        if (passive) {
            // 只读：中间的分隔线断成两截
            c.fill((int) (WHEEL_CX - 15F), (int) (WHEEL_CY - 3F), 10, 1,
                    WheelKit.withAlpha(WheelKit.GOLD, 45));
            c.fill((int) (WHEEL_CX + 5F), (int) (WHEEL_CY - 3F), 10, 1,
                    WheelKit.withAlpha(WheelKit.GOLD, 45));
        } else {
            c.fill((int) (WHEEL_CX - 15F), (int) (WHEEL_CY - 3F), 30, 1,
                    WheelKit.withAlpha(WheelKit.GOLD, 85));
        }
        c.textCentered("WHEEL I", (int) WHEEL_CX, Math.round(WHEEL_CY + 3F),
                WheelKit.withAlpha(WheelKit.GOLD, dim), false);
    }

    // ══════════════════════════════════════════════════════════════
    //  中间：轮盘外的说明 / 状态 / 拖拽反馈
    // ══════════════════════════════════════════════════════════════

    private static void drawCenterOverlay(PreviewCanvas c, float time, Mode mode) {
        // 轮盘上方的说明行
        String caption;
        int captionColor;
        switch (mode) {
            case DRAG -> {
                caption = "WHEEL I  -  TARGETING SOCKET 6";
                captionColor = WheelKit.GOLD_BRIGHT;
            }
            case PASSIVE -> {
                caption = "WHEEL I  -  READ ONLY";
                captionColor = WheelKit.TEXT_FAINT;
            }
            default -> {
                caption = "WHEEL I  -  8 SOCKETS";
                captionColor = WheelKit.GOLD;
            }
        }
        c.textCentered(caption, (int) WHEEL_CX, 91,
                WheelKit.withAlpha(captionColor, 200), false);
        int captionHalf = c.textWidth(caption) / 2 + 12;
        WheelKit.fillStar(c, WHEEL_CX - captionHalf, 95F, 3.0F, 1.0F, 4, 0F,
                WheelKit.withAlpha(captionColor, 150));
        WheelKit.fillStar(c, WHEEL_CX + captionHalf, 95F, 3.0F, 1.0F, 4, 0F,
                WheelKit.withAlpha(captionColor, 150));

        // 轮盘下方的状态胶囊
        String pill;
        int pillBorder;
        int pillText;
        switch (mode) {
            case DRAG -> {
                pill = "SOCKET 6  -  RELEASE TO BIND";
                pillBorder = WheelKit.GOLD_BRIGHT;
                pillText = WheelKit.GOLD_BRIGHT;
            }
            case PASSIVE -> {
                pill = "WHEEL LOCKED  -  PASSIVE PAGE";
                pillBorder = WheelKit.LOCKED;
                pillText = WheelKit.LOCKED;
            }
            default -> {
                pill = "3 OF 8 SOCKETS BOUND";
                pillBorder = WheelKit.GOLD;
                pillText = WheelKit.GOLD_BRIGHT;
            }
        }
        drawPill(c, WHEEL_CX, 294F, pill, pillBorder, pillText, time, mode == Mode.DRAG);

        // 轮盘区底部的装饰分隔线
        drawOrnamentDivider(c, 228F, 412F, 330F);

        if (mode == Mode.DRAG) {
            drawDragOverlay(c, time);
        }
    }

    /** 状态胶囊：深底 + 1px 描边 + 居中文字（拖拽帧带呼吸）。 */
    private static void drawPill(PreviewCanvas c, float cx, float cy, String label,
                                 int border, int textColor, float time, boolean pulse) {
        int w = c.textWidth(label) + 22;
        int h = 14;
        int x = Math.round(cx - w / 2F);
        int y = Math.round(cy - h / 2F);
        int alpha = pulse
                ? 210 + Math.round(30F * (0.5F + 0.5F * (float) Math.sin(time * 6F)))
                : 225;
        if (!SdfShapes.roundRect(c, x, y, w, h, h * 0.5F,
                WheelKit.withAlpha(0xFF05090F, 235), WheelKit.withAlpha(0xFF05090F, 235),
                WheelKit.withAlpha(border, 150), 0.8F)) {
            c.fill(x, y, w, h, WheelKit.withAlpha(0xFF05090F, 235));
            c.fill(x, y, w, 1, WheelKit.withAlpha(border, 160));
            c.fill(x, y + h - 1, w, 1, WheelKit.withAlpha(border, 80));
            c.fill(x, y, 1, h, WheelKit.withAlpha(border, 90));
            c.fill(x + w - 1, y, 1, h, WheelKit.withAlpha(border, 60));
        }
        c.textCentered(label, x + w / 2, y + 3, WheelKit.withAlpha(textColor, alpha), false);
    }

    private static void drawOrnamentDivider(PreviewCanvas c, float x0, float x1, float y) {
        WheelKit.line(c, x0, y, x1, y, 0.5F, WheelKit.withAlpha(WheelKit.GOLD, 45));
        WheelKit.fillStar(c, (x0 + x1) * 0.5F, y, 4.0F, 1.4F, 4, 0F,
                WheelKit.withAlpha(WheelKit.GOLD, 110));
    }

    /**
     * 拖拽态：外圈瞄准环 + 目标扇区亮弧 + 向外扩散的脉冲 + 金色虚线 + 半透明卡片幽灵。
     * <p>都在面板画完之后绘制，所以虚线可以“压”在左面板右缘与轮盘扇区上，读起来就是
     * 「从图鉴里把卡片拖进 socket」。绘制顺序：瞄准环 → 虚线 → 卡片幽灵 → 指针 →
     * 目标 socket 的脉冲环（落点永远在最上层，不会被幽灵盖住）。</p>
     */
    private static void drawDragOverlay(PreviewCanvas c, float time) {
        WheelKit.Spell dragged = WheelKit.SUPPRESSION;

        float socketX = WheelKit.slotX(WHEEL_CX, (WHEEL_OUTER + WHEEL_INNER) * 0.5F, DRAG_TARGET_SLOT);
        float socketY = WheelKit.slotY(WHEEL_CY, (WHEEL_OUTER + WHEEL_INNER) * 0.5F, DRAG_TARGET_SLOT);
        float rimX = WheelKit.slotX(WHEEL_CX, 90F, DRAG_TARGET_SLOT);
        float rimY = WheelKit.slotY(WHEEL_CY, 90F, DRAG_TARGET_SLOT);

        // ① 外圈瞄准环：整圈淡环 + 目标扇区亮弧 + 两道向外扩散的脉冲。
        WheelKit.fillRing(c, WHEEL_CX, WHEEL_CY, 96F, 0.5F,
                WheelKit.withAlpha(WheelKit.GOLD, 55), 120);
        float arcStart = WheelKit.slotStart(DRAG_TARGET_SLOT) - 0.10F;
        float arcEnd = WheelKit.slotEnd(DRAG_TARGET_SLOT) + 0.10F;
        // 目标扇区铺一层很淡的金色，再把内外缘提亮，落点一眼可辨。
        float sectorStart = WheelKit.slotStart(DRAG_TARGET_SLOT) + 0.02F;
        float sectorEnd = WheelKit.slotEnd(DRAG_TARGET_SLOT) - 0.02F;
        WheelKit.fillArc(c, WHEEL_CX, WHEEL_CY, WHEEL_INNER + 2F, WHEEL_OUTER - 1F,
                sectorStart, sectorEnd, WheelKit.withAlpha(WheelKit.GOLD, 40), 18);
        WheelKit.fillArc(c, WHEEL_CX, WHEEL_CY, WHEEL_INNER + 2F, WHEEL_INNER + 3.2F,
                sectorStart, sectorEnd, WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 150), 18);
        WheelKit.fillArc(c, WHEEL_CX, WHEEL_CY, WHEEL_OUTER - 2.2F, WHEEL_OUTER - 1F,
                sectorStart, sectorEnd, WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 150), 18);
        WheelKit.fillArc(c, WHEEL_CX, WHEEL_CY, 94.5F, 96.5F, arcStart, arcEnd,
                WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 225), 20);
        for (int k = 0; k < 2; k++) {
            float phase = (time * 0.55F + k * 0.5F) % 1F;
            float radius = 96F + phase * 9F;
            int alpha = Math.round(140F * (1F - phase));
            WheelKit.fillArc(c, WHEEL_CX, WHEEL_CY, radius, radius + 1.2F,
                    arcStart, arcEnd, WheelKit.withAlpha(WheelKit.GOLD, alpha), 20);
        }
        // 轮缘上的目标菱形
        WheelKit.fillStar(c, rimX, rimY, 5.5F, 2.2F, 4, 0F, WheelKit.GOLD_BRIGHT);
        WheelKit.fillRing(c, rimX, rimY, 8F, 0.5F,
                WheelKit.withAlpha(WheelKit.GOLD, 120), 16);
        // 目标方位的角标（WheelKit 自适应半径处）也圈一圈，落点不只在图标上
        float badgeX = WheelKit.slotX(WHEEL_CX, BADGE_RADIUS, DRAG_TARGET_SLOT);
        float badgeY = WheelKit.slotY(WHEEL_CY, BADGE_RADIUS, DRAG_TARGET_SLOT);
        int badgePulse = 150 + Math.round(70F * (0.5F + 0.5F * (float) Math.sin(time * 5F)));
        WheelKit.fillRing(c, badgeX, badgeY, 8F, 0.9F,
                WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 220), 20);
        WheelKit.fillRing(c, badgeX, badgeY, 10.5F, 0.5F,
                WheelKit.withAlpha(WheelKit.GOLD, badgePulse), 22);

        // ② 从「Suppression Field」卡片右缘拖向 socket 的金色虚线（流动相位朝目标）。
        float cardRightX = LEFT_X + 10 + (LEFT_W - 20) - 2F;
        float cardCenterY = PANEL_Y + 54 + 2 * 38 + 17F;
        drawDashedLine(c, cardRightX, cardCenterY, socketX, socketY, 9F, 4F, time * 26F,
                1.0F, WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 240));

        // ③ 光标处的半透明卡片幽灵（压在卡片被“拎起来”的位置，不挡中心文字与落点）
        drawDragGhost(c, 222F, 186F, dragged);

        // ④ 落点脉冲环 + 中心小星（最后画，确保目标永远看得见）
        float ringPhase = (time * 0.9F) % 1F;
        WheelKit.fillRing(c, socketX, socketY, 12F + ringPhase * 8F, 0.5F,
                WheelKit.withAlpha(WheelKit.GOLD, Math.round(170F * (1F - ringPhase))), 22);
        WheelKit.fillRing(c, socketX, socketY, 17F, 0.4F,
                WheelKit.withAlpha(WheelKit.GOLD, 70), 24);
        WheelKit.fillRing(c, socketX, socketY, 12F, 0.8F,
                WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 190), 22);
        WheelKit.fillStar(c, socketX, socketY, 3.0F, 1.1F, 4, 0F, WheelKit.GOLD_BRIGHT);
    }

    /** 拖拽中的卡片幽灵：半透明卡底 + 流派色条 + 图标 + 名称/消耗（128...136 宽刚好不挡落点）。 */
    private static void drawDragGhost(PreviewCanvas c, float cx, float cy, WheelKit.Spell spell) {
        int w = 136;
        int h = 24;
        int x = Math.round(cx - w * 0.5F);
        int y = Math.round(cy - h * 0.5F);
        int accent = WheelKit.argb(spell.color(), 1F);

        // 投影
        c.fill(x + 3, y + 4, w, h, 0x66000000);
        c.fill(x, y, w, h, 0xB80B1424);
        c.fill(x, y, 2, h, WheelKit.withAlpha(accent, 210));
        c.fill(x, y, w, 1, WheelKit.withAlpha(WheelKit.GOLD, 170));
        c.fill(x, y + h - 1, w, 1, WheelKit.withAlpha(WheelKit.GOLD, 70));
        c.fill(x, y, 1, h, WheelKit.withAlpha(WheelKit.GOLD, 80));
        c.fill(x + w - 1, y, 1, h, WheelKit.withAlpha(WheelKit.GOLD, 55));

        WheelKit.drawSpellIcon(c, spell, x + 15F, cy, 18F, 0.92F, 0.75F);
        c.text(spell.name(), x + 28, y + 4, WheelKit.withAlpha(WheelKit.TEXT, 235), true);
        c.text("VOID  -  80 MANA", x + 28, y + 14,
                WheelKit.withAlpha(WheelKit.TEXT_DIM, 225), false);
    }

    /** 金色虚线（按弧长等距分段，phase 让它朝目标方向流动）。 */
    private static void drawDashedLine(PreviewCanvas c, float x1, float y1, float x2, float y2,
                                       float dash, float gap, float phase, float halfWidth,
                                       int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5F) {
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float period = dash + gap;
        float start = -(((phase % period) + period) % period);
        for (float s = start; s < length; s += period) {
            float a = Math.max(0F, s);
            float b = Math.min(length, s + dash);
            if (b <= a) {
                continue;
            }
            WheelKit.line(c, x1 + ux * a, y1 + uy * a, x1 + ux * b, y1 + uy * b,
                    halfWidth, argb);
        }
    }

    /** 鼠标指针：暖白箭头 + 深色描边（画在最后，永远压在最上面）。 */
    private static void drawPointer(PreviewCanvas c, float x, float y) {
        float[] xs = {x, x, x + 3.6F, x + 6.0F, x + 9.2F, x + 6.8F, x + 11.6F};
        float[] ys = {y, y + 15F, y + 11.6F, y + 17.2F, y + 15.8F, y + 10.6F, y + 10.6F};
        // 描边：沿多边形每条边画一条暗线
        int outline = WheelKit.withAlpha(0x000000, 170);
        for (int i = 0; i < xs.length; i++) {
            int j = (i + 1) % xs.length;
            WheelKit.line(c, xs[i], ys[i], xs[j], ys[j], 1.1F, outline);
        }
        WheelKit.fillPolygon(c, xs, ys, xs.length, 0xFFFFF6DC);
        c.fill((int) x + 1, (int) y + 2, 1, 2, 0xFF2A2418);
    }

    // ══════════════════════════════════════════════════════════════
    //  右面板：法术详情 + 按钮脚栏
    // ══════════════════════════════════════════════════════════════

    private static void drawRightPanel(PreviewCanvas c, float time, Mode mode) {
        WheelKit.Spell spell;
        int assignedSlot;
        switch (mode) {
            case DRAG -> {
                spell = WheelKit.SUPPRESSION;
                assignedSlot = 2;
            }
            case PASSIVE -> {
                spell = WheelKit.EMBER_AFFINITY;
                assignedSlot = -1;
            }
            default -> {
                spell = WheelKit.FIREBALL;
                assignedSlot = 0;
            }
        }

        // 投影 + 详情面板（drawSpellDetail 自带面板底/描边/图标/属性/描述）。
        c.fill(RIGHT_X + 4, PANEL_Y + 4, RIGHT_W, DETAIL_H, 0x55000000);
        WheelKit.drawSpellDetail(c, spell, RIGHT_X, PANEL_Y, RIGHT_W, DETAIL_H, assignedSlot, time);
        drawTopGradientLine(c, RIGHT_X, PANEL_Y, RIGHT_W, 150);
        drawPanelTopCorners(c, RIGHT_X, PANEL_Y, RIGHT_W);
        c.text("DETAIL", RIGHT_X + 9, PANEL_Y + 5,
                WheelKit.withAlpha(WheelKit.GOLD, 160), false);

        if (mode == Mode.PASSIVE) {
            // 描述与「不能进轮盘」警告之间补一条分隔线，避免两块文字粘在一起。
            c.fill(RIGHT_X + 10, 261, RIGHT_W - 20, 1,
                    WheelKit.withAlpha(WheelKit.GOLD, 45));
        } else {
            // 主动法术页：详情面板底部留白放一句轮盘开启方式（被动页那里是规则警告）。
            int centerX = RIGHT_X + RIGHT_W / 2;
            c.textCentered("HOLD  [R]  TO OPEN THIS WHEEL", centerX, 266,
                    WheelKit.withAlpha(WheelKit.GOLD, 130), false);
            int starOffset = c.textWidth("HOLD  [R]  TO OPEN THIS WHEEL") / 2 + 12;
            WheelKit.fillStar(c, centerX - starOffset, 270F, 2.8F, 1.0F, 4, 0F,
                    WheelKit.withAlpha(WheelKit.GOLD, 120));
            WheelKit.fillStar(c, centerX + starOffset, 270F, 2.8F, 1.0F, 4, 0F,
                    WheelKit.withAlpha(WheelKit.GOLD, 120));
        }

        drawFooter(c, time, mode);
    }

    /** 右侧脚栏：3 个金色描边按钮竖排（整宽更好读，也不会像横排那样贴边）+ 按键提示。 */
    private static void drawFooter(PreviewCanvas c, float time, Mode mode) {
        int y = FOOTER_Y;
        int h = FOOTER_H;
        c.fill(RIGHT_X + 1, y + 1, RIGHT_W - 2, h - 2, PANEL_BG);
        c.fill(RIGHT_X + 1, y, RIGHT_W - 2, 1, WheelKit.withAlpha(WheelKit.GOLD, 95));
        c.fill(RIGHT_X, y + 1, 1, h - 2, WheelKit.withAlpha(WheelKit.GOLD, 40));
        c.fill(RIGHT_X + RIGHT_W - 1, y + 1, 1, h - 2,
                WheelKit.withAlpha(WheelKit.GOLD, 40));
        c.fill(RIGHT_X, y + h - 1, RIGHT_W, 1, WheelKit.withAlpha(WheelKit.GOLD, 45));
        drawPanelBottomCorners(c, RIGHT_X, y, RIGHT_W, h);

        boolean enabled = mode != Mode.PASSIVE;
        int leftX = RIGHT_X + 10;
        int innerW = RIGHT_W - 20;
        int gapX = 6;
        int halfW = (innerW - gapX) / 2;
        int rowY = y + 9;
        // 前两个是次要操作并排，SAVE 作为主操作整宽放在下面。
        drawButton(c, leftX, rowY, halfW, 16, "CLEAR SLOT", enabled, false);
        drawButton(c, leftX + halfW + gapX, rowY, halfW, 16, "AUTO FILL", enabled, false);
        drawButton(c, leftX, rowY + 19, innerW, 16, "SAVE", enabled, true);
    }

    private static void drawButton(PreviewCanvas c, int x, int y, int w, int h,
                                   String label, boolean enabled, boolean primary) {
        int border = enabled ? (primary ? 210 : 130) : 55;
        int fill = enabled ? (primary ? 0x40E7C46A : 0xD20A1424) : 0x80070C15;
        int fillBottom = enabled && primary ? 0x36C9A227 : fill;
        if (!SdfShapes.roundRect(c, x, y, w, h, 3F, fill, fillBottom,
                WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, border), 0.9F)) {
            c.fill(x, y, w, h, fill);
            c.fill(x, y, w, 1, WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, border));
            c.fill(x, y + h - 1, w, 1, WheelKit.withAlpha(WheelKit.GOLD, Math.max(20, border / 2)));
            c.fill(x, y, 1, h, WheelKit.withAlpha(WheelKit.GOLD, Math.max(20, border * 2 / 3)));
            c.fill(x + w - 1, y, 1, h, WheelKit.withAlpha(WheelKit.GOLD, Math.max(20, border / 2)));
        }
        if (enabled && primary) {
            c.fill(x + 3, y + 1, w - 6, 1, WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 90));
        }
        int color = !enabled ? WheelKit.withAlpha(WheelKit.TEXT_FAINT, 200)
                : (primary ? WheelKit.GOLD_BRIGHT : WheelKit.GOLD);
        c.textCentered(label, x + w / 2, y + (h - 9) / 2 + 1, color, enabled && primary);
    }

    // ══════════════════════════════════════════════════════════════
    //  底部提示条：操作说明 + 轮盘页签 I / II / III
    // ══════════════════════════════════════════════════════════════

    private static void drawBottomBar(PreviewCanvas c, float time, Mode mode) {
        int y = BOTTOM_BAR_Y;
        c.fill(0, y, c.width, BOTTOM_BAR_H, 0xB0050912);
        c.fill(0, y, c.width, 1, WheelKit.withAlpha(WheelKit.GOLD, 75));
        c.fill(0, y + BOTTOM_BAR_H - 1, c.width, 1,
                WheelKit.withAlpha(WheelKit.GOLD, 28));

        WheelKit.fillStar(c, 18F, y + 17F, 3.2F, 1.0F, 4, 0F,
                WheelKit.withAlpha(WheelKit.GOLD, 210));
        c.text("Drag a spell into a socket   |   Right-click to clear   |   Passives cannot be bound",
                28, y + 13, WheelKit.withAlpha(WheelKit.TEXT_DIM, 235), false);

        c.textRight("WHEEL", 528, y + 13, WheelKit.withAlpha(WheelKit.GOLD, 165), false);
        for (int i = 0; i < 3; i++) {
            int tabX = 538 + i * 30;
            String label = i == 0 ? "I" : (i == 1 ? "II" : "III");
            boolean active = i == 0;
            if (!SdfShapes.roundRect(c, tabX, y + 8, 26, 18, 3F,
                    active ? 0x33E7C46A : 0x33020810, active ? 0x33E7C46A : 0x33020810,
                    WheelKit.withAlpha(WheelKit.GOLD, active ? 200 : 70), 0.8F)) {
                c.fill(tabX, y + 8, 26, 1, WheelKit.withAlpha(WheelKit.GOLD, active ? 200 : 70));
                c.fill(tabX, y + 25, 26, 1, WheelKit.withAlpha(WheelKit.GOLD, active ? 130 : 50));
                c.fill(tabX, y + 8, 1, 18, WheelKit.withAlpha(WheelKit.GOLD, active ? 130 : 45));
                c.fill(tabX + 25, y + 8, 1, 18, WheelKit.withAlpha(WheelKit.GOLD, active ? 90 : 40));
            }
            c.textCentered(label, tabX + 13, y + 12,
                    WheelKit.withAlpha(active ? WheelKit.GOLD_BRIGHT : WheelKit.TEXT_FAINT,
                            active ? 245 : 210), false);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  面板通用 chrome
    // ══════════════════════════════════════════════════════════════

    /** 深色半透明底 + 1px 金描边 + 顶部金色渐变线 + 四角短线。 */
    private static void drawPanelChrome(PreviewCanvas c, int x, int y, int w, int h) {
        c.fill(x + 4, y + 4, w, h, 0x55000000);
        // 面板底/描边走 SDF 圆角：四角是解析抗锯齿的（2x 原生渲染下每个物理像素一个覆盖率），
        // 不再是「先画矩形再拿 1px 线段拼角」的阶梯。
        if (!SdfShapes.roundRect(c, x, y, w, h, 4F, PANEL_BG, PANEL_BG,
                WheelKit.withAlpha(WheelKit.GOLD, 95), 0.9F)) {
            c.fill(x, y, w, h, PANEL_BG);
            c.fill(x, y, w, 1, WheelKit.withAlpha(WheelKit.GOLD, 95));
            c.fill(x, y + h - 1, w, 1, WheelKit.withAlpha(WheelKit.GOLD, 45));
            c.fill(x, y, 1, h, WheelKit.withAlpha(WheelKit.GOLD, 62));
            c.fill(x + w - 1, y, 1, h, WheelKit.withAlpha(WheelKit.GOLD, 42));
        }
        drawTopGradientLine(c, x, y, w, 165);
        drawPanelTopCorners(c, x, y, w);
        drawPanelBottomCorners(c, x, y, w, h);
    }

    /** 顶部 2px 金色渐变线：中间亮、两端收进描边里。 */
    private static void drawTopGradientLine(PreviewCanvas c, int x, int y, int w, int peakAlpha) {
        int segments = 16;
        int segW = Math.max(1, w / segments);
        for (int i = 0; i < segments; i++) {
            float t = (i + 0.5F) / segments;
            float k = (float) Math.sin(Math.PI * t);
            int alpha = Math.round(peakAlpha * k);
            if (alpha <= 2) {
                continue;
            }
            c.fill(x + i * segW, y, segW, 2, WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, alpha));
        }
    }

    private static void drawPanelTopCorners(PreviewCanvas c, int x, int y, int w) {
        int color = WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 120);
        int len = 7;
        int inset = 3;
        WheelKit.line(c, x + inset, y + inset + len, x + inset, y + inset, 0.5F, color);
        WheelKit.line(c, x + inset, y + inset, x + inset + len, y + inset, 0.5F, color);
        WheelKit.line(c, x + w - inset, y + inset + len, x + w - inset, y + inset, 0.5F, color);
        WheelKit.line(c, x + w - inset, y + inset, x + w - inset - len, y + inset, 0.5F, color);
    }

    private static void drawPanelBottomCorners(PreviewCanvas c, int x, int y, int w, int h) {
        int color = WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, 100);
        int len = 7;
        int inset = 3;
        WheelKit.line(c, x + inset, y + h - inset - len, x + inset, y + h - inset, 0.5F, color);
        WheelKit.line(c, x + inset, y + h - inset, x + inset + len, y + h - inset, 0.5F, color);
        WheelKit.line(c, x + w - inset, y + h - inset - len, x + w - inset,
                y + h - inset, 0.5F, color);
        WheelKit.line(c, x + w - inset, y + h - inset, x + w - inset - len,
                y + h - inset, 0.5F, color);
    }
}
