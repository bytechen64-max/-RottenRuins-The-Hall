import org.lwjgl.opengl.GL11;

/**
 * 法术合成界面 —— 新版视觉稿（"仪式环"）。
 *
 * <h3>和旧提案的区别</h3>
 * <p>旧稿是"原版容器"路线：灰底面板、斜角槽位、三个材料槽 + 进度条，信息密度靠边框堆出来。
 * 这一版换成<b>整屏构图</b>：没有面板外框，界面直接浮在星场上——</p>
 * <ul>
 *   <li><b>左</b>：一枚仪式环。外环 + 60 格刻度 + 虚线环 + 七学派刻度带 + 三颗材料星
 *       （120° 均分在环上）+ 中心法术核心；材料到核心之间是真实 {@code magic_pulse}
 *       势流，绑定中沿外环跑一圈进度弧。</li>
 *   <li><b>右</b>：一张深色圆角卡，只放"结果"：法术名、学派/形态、耗蓝/冷却/伤害、
 *       效果句、稳定性、复杂度。文字全部左对齐、数值右对齐，行距 14。</li>
 *   <li><b>头/脚</b>：极小化的字标 + 一行发丝线；底部只有星砂刻度与两枚按钮。</li>
 * </ul>
 *
 * <h3>为什么这样排</h3>
 * <p>界面里只能有一个焦点。旧稿三个区、四个框、两套按钮，眼睛没有落点；这一版把
 * "中心法术 + 绑定按钮"做唯一高亮（法术图标 32×32 原尺寸、按钮实金填充），
 * 其余一律压到 20%~40% 不透明度；颜色只保留七学派色与一种旧金，不出现第二种中性灰。</p>
 *
 * <h3>像素与平滑</h3>
 * <p>结构（发丝线、刻度、文字、按钮边框、图标）是硬边或 1px；圆/环/扇形/圆角/胶囊线
 * 全部走 {@code sdf_ui} 逐物理像素覆盖率，任意 GUI scale 下边缘都干净；
 * 星场与势流走项目真实 {@code magic_starfield} / {@code magic_pulse}。
 * 着色器缺失时 {@link SdfShapes} 会退化到 CPU 多边形，界面仍成立。</p>
 *
 * <p>产物：{@code spell_synth_ready.png} / {@code spell_synth_binding.png} /
 * {@code spell_synth_missing.png}，默认导出 = 就绪态。文字一律英文（预览字体只带拉丁）。</p>
 */
public final class SceneSpellSynth {

    // ══════════════════════════════════════════════════════════════
    //  布局：中心 (CX,CY) + 一圈半径。改一处即可整体缩放。
    // ══════════════════════════════════════════════════════════════

    private static final int CX = 196;
    private static final int CY = 196;

    private static final int R_OUT = 108;        // 外环
    private static final int R_TICK_IN = 100;    // 刻度内端（长刻度再往里 5）
    private static final int R_TICK_OUT = 105;   // 刻度外端
    private static final int R_DASH = 90;        // 虚线环
    private static final int R_BAND_IN = 80;     // 学派细弧
    private static final int R_BAND_OUT = 82;
    private static final int R_MARK = 87;        // 学派标记点
    private static final int R_NODE = 60;        // 材料星所在半径
    private static final int R_CORE = 40;        // 法术核心

    private static final int NODE_BOX = 28;

    private static final int CARD_X = 350;
    private static final int CARD_Y = 74;
    private static final int CARD_W = 254;
    private static final int CARD_H = 248;

    private static final int HEADER_RULE_Y = 66;
    private static final int FOOTER_RULE_Y = 338;
    private static final int BODY_LEFT = 28;
    private static final int BODY_RIGHT = 612;

    // ══════════════════════════════════════════════════════════════
    //  配色：只有"旧金 + 七学派色 + 一档暖白"，没有中性灰
    // ══════════════════════════════════════════════════════════════

    private static final int INK = 0xFFF0E6D2;
    private static final int INK_DIM = 0xFF9C92AC;
    private static final int INK_FAINT = 0xFF5E5670;

    private static final int GOLD = 0xFFD8B26A;
    private static final int GOLD_HI = 0xFFF3DFA6;
    private static final int GOLD_LO = 0xFF6B5330;
    private static final int WARN = 0xFFFF7A5A;

    private static final int CARD_TOP = 0xF20C0818;
    private static final int CARD_BOT = 0xF2070510;
    private static final int CARD_EDGE = 0xFF2C2242;

    private static final int BG = 0xFF04030A;

    /** 七学派色，顺序与 ElementBand 一致；注意必须显式带上 0xFF 的 alpha。 */
    private static final int[] SCHOOL = {
            0xFFFFF4AC, 0xFFFF7843, 0xFF53BEFF, 0xFF78D98A,
            0xFFBE8BDA, 0xFF8B85FF, 0xFFE7A7EF
    };
    private static final int FIRE = 1;

    private static final float TAU = (float) (Math.PI * 2.0);

    // ══════════════════════════════════════════════════════════════
    //  数据（取自实机 SpellDefinition：40 mana / 40 ticks / 4.0 伤害）
    // ══════════════════════════════════════════════════════════════

    private static final String SPELL_NAME = "FIREBALL";
    private static final String SPELL_TAGS = "FIRE  /  BOLT  /  INSTANT";
    private static final int SPELL_MANA = 40;
    private static final double SPELL_COOLDOWN_SEC = 2.0D;
    private static final double SPELL_DAMAGE = 4.0D;
    private static final String EFFECT =
            "Hurls a roaring orb that detonates on impact, blasting blocks and foes alike.";

    private static final String[] MAT_ICON = {
            "textures/item/fire_mana_plate.png",
            "textures/item/fire_mana_bottle.png",
            "textures/item/magic_cata.png"
    };
    private static final String[] MAT_COUNT = {"x1", "x3", "x1"};
    /** 三颗材料星的角度（度）：正上、左下、右下。 */
    private static final int[] MAT_DEG = {-90, 150, 30};

    private static final int MODE_READY = 0;
    private static final int MODE_BINDING = 1;
    private static final int MODE_MISSING = 2;

    private static final float BIND_PROGRESS = 0.62F;

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        shot(c, time, MODE_READY);
        c.save("spell_synth_ready");
        shot(c, time, MODE_BINDING);
        c.save("spell_synth_binding");
        shot(c, time, MODE_MISSING);
        c.save("spell_synth_missing");
        // 默认导出（GuiPreview 会在 render 返回后再存一张 <scene>.png）
        shot(c, time, MODE_READY);
    }

    private static void shot(PreviewCanvas c, float time, int mode) {
        float progress = mode == MODE_BINDING ? BIND_PROGRESS : (mode == MODE_READY ? 1F : 0F);

        background(c, time);
        links(c, time, mode);          // 势流（在环与星之下）
        apparatus(c, time, progress);  // 外环 / 刻度 / 虚线环 / 学派弧
        nodes(c, time, mode);          // 三颗材料星
        core(c, time, mode, progress); // 中心法术
        card(c, time, mode, progress); // 右侧结果卡
        header(c, time, mode);
        footer(c, time, mode);
    }

    // ══════════════════════════════════════════════════════════════
    //  背景：星场 + 四边暗角 + 少量闪烁星
    // ══════════════════════════════════════════════════════════════

    private static void background(PreviewCanvas c, float time) {
        c.clear(BG);

        PreviewShader sf = c.shader("magic_starfield");
        sf.set("uTime", time);
        sf.set("uSize", (float) c.width, (float) c.height);
        sf.set("uMouse", 0F, 0F);
        sf.set("uIntensity", 0.9F);
        c.shaderQuad(sf, 0, 0, c.width, c.height);

        // 四边暗角：竖直方向用渐变，水平方向逐列叠到透明。
        c.fillGradientV(0, 0, c.width, 92, 0xE004030A, 0x0004030A);
        c.fillGradientV(0, c.height - 104, c.width, 104, 0x0004030A, 0xF204030A);
        int band = 96;
        for (int i = 0; i < band; i++) {
            float t = 1F - i / (float) band;
            int a = Math.round(96F * t * t);
            if (a <= 0) {
                continue;
            }
            int argb = a << 24;
            c.fill(i, 0, 1, c.height, argb);
            c.fill(c.width - 1 - i, 0, 1, c.height, argb);
        }

        // 固定星座感的散星（位置写死，保证每次出图一致；只做亮度呼吸）。
        int[][] stars = {
                {66, 96}, {92, 268}, {318, 78}, {336, 306}, {612, 128},
                {580, 262}, {72, 330}, {268, 356}, {620, 356}, {430, 44},
                {24, 196}, {628, 210}
        };
        for (int i = 0; i < stars.length; i++) {
            float twinkle = 0.55F + 0.45F * (float) Math.sin(time * 1.3F + i * 1.7F);
            int a = Math.round(58F * twinkle);
            c.fill(stars[i][0], stars[i][1], 1, 1, (a << 24) | 0xE8DCC0);
            if (i % 3 == 0) {
                int b = Math.round(26F * twinkle);
                c.fill(stars[i][0] - 1, stars[i][1], 3, 1, (b << 24) | 0xE8DCC0);
                c.fill(stars[i][0], stars[i][1] - 1, 1, 3, (b << 24) | 0xE8DCC0);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  仪式环
    // ══════════════════════════════════════════════════════════════

    private static void apparatus(PreviewCanvas c, float time, float progress) {
        float spin = time * 0.02F;

        // 表盘面：让刻度和材料星落在一块暗面上，而不是直接飘在星空里；
        // 外缘用一圈径向渐隐把硬边化开。
        float faceR = 92F;
        SdfShapes.disc(c, CX, CY, faceR, 0xAC0B0718, 0x9C05030B);
        SdfShapes.ring(c, CX, CY, faceR, faceR + 7F,
                0x60070512, 0x00070512, true);
        // 上半圈一道极淡的顶光，给"表盘"一点体积，不做全环高光
        SdfShapes.wedge(c, CX, CY, faceR - 1.6F, faceR - 0.6F,
                (float) Math.toRadians(200), (float) Math.toRadians(340),
                withAlpha(GOLD, 54), withAlpha(GOLD, 6));

        // 外环（一根 1px，不描双线）
        SdfShapes.ring(c, CX, CY, R_OUT - 0.5F, R_OUT + 0.5F,
                withAlpha(GOLD, 44), withAlpha(GOLD, 44), false);

        // 60 格刻度，每 5 格加长（长刻度只到外环，不压线）
        for (int i = 0; i < 60; i++) {
            float a = i * TAU / 60F + spin;
            boolean major = i % 5 == 0;
            float r0 = major ? R_TICK_IN - 5F : R_TICK_IN;
            float x0 = CX + (float) Math.cos(a) * r0;
            float y0 = CY + (float) Math.sin(a) * r0;
            float x1 = CX + (float) Math.cos(a) * R_TICK_OUT;
            float y1 = CY + (float) Math.sin(a) * R_TICK_OUT;
            SdfShapes.line(c, x0, y0, x1, y1, major ? 0.6F : 0.4F,
                    withAlpha(GOLD, major ? 78 : 26));
        }

        // 反向旋转的虚线环（36 段）
        for (int i = 0; i < 36; i++) {
            float a0 = i * TAU / 36F - spin * 1.8F;
            float a1 = a0 + TAU / 36F * 0.56F;
            SdfShapes.wedge(c, CX, CY, R_DASH - 0.4F, R_DASH + 0.4F, a0, a1,
                    withAlpha(GOLD, 20), withAlpha(GOLD, 10));
        }

        // 七学派环：只有一根金环 + 7 道分隔刻痕，不再铺七色饼块；
        // 颜色只留给"当前法术的学派"，其余六格保持金色，画面才不花。
        // 整个环转一下，让激活学派正好落在正上方（和顶上的材料星同轴）。
        SdfShapes.ring(c, CX, CY, R_BAND_IN, R_BAND_OUT,
                withAlpha(GOLD, 32), withAlpha(GOLD, 32), false);
        float step = TAU / 7F;
        float base = -TAU / 4F - step * (FIRE + 0.5F);
        for (int i = 0; i < 7; i++) {
            float a = base + i * step;
            boolean lit = i == FIRE;
            SdfShapes.line(c,
                    CX + (float) Math.cos(a) * (R_BAND_IN - 2.5F),
                    CY + (float) Math.sin(a) * (R_BAND_IN - 2.5F),
                    CX + (float) Math.cos(a) * (R_BAND_OUT + 2.5F),
                    CY + (float) Math.sin(a) * (R_BAND_OUT + 2.5F),
                    0.4F, withAlpha(GOLD, lit ? 110 : 62));

            float am = a + step * 0.5F;
            float mx = CX + (float) Math.cos(am) * R_MARK;
            float my = CY + (float) Math.sin(am) * R_MARK;
            if (lit) {
                // 当前学派：唯一一处上色，放在刻度环外，避开材料星
                SdfShapes.disc(c, mx, my, 6.5F, withAlpha(SCHOOL[FIRE], 46), withAlpha(SCHOOL[FIRE], 8));
                float[] xs = {mx, mx + 3.4F, mx, mx - 3.4F};
                float[] ys = {my - 3.4F, my, my + 3.4F, my};
                WheelKit.fillPolygon(c, xs, ys, 4, withAlpha(SCHOOL[FIRE], 240));
                WheelKit.spark(c, mx, my, 1.4F, withAlpha(SCHOOL[FIRE], 200));
            } else {
                float[] xs = {mx, mx + 2.0F, mx, mx - 2.0F};
                float[] ys = {my - 2.0F, my, my + 2.0F, my};
                WheelKit.fillPolygon(c, xs, ys, 4, withAlpha(GOLD, 130));
            }
        }

        // 材料星轨道（极淡的虚线，三颗星落在这条环上）
        for (int i = 0; i < 24; i++) {
            float a0 = i * TAU / 24F + spin * 0.6F;
            float a1 = a0 + TAU / 24F * 0.40F;
            SdfShapes.wedge(c, CX, CY, R_NODE - 0.35F, R_NODE + 0.35F, a0, a1,
                    withAlpha(GOLD, 18), withAlpha(GOLD, 9));
        }

        // 绑定进度：外环外侧再套一圈 2px 亮弧 + 弧头光点
        if (progress > 0.001F) {
            float a0 = -TAU / 4F;
            float a1 = a0 + TAU * Math.max(0F, Math.min(1F, progress));
            SdfShapes.wedge(c, CX, CY, R_OUT + 4.0F, R_OUT + 6.2F, a0, a1,
                    withAlpha(GOLD, 100), withAlpha(GOLD_HI, 230));
            float hx = CX + (float) Math.cos(a1) * (R_OUT + 5.1F);
            float hy = CY + (float) Math.sin(a1) * (R_OUT + 5.1F);
            WheelKit.spark(c, hx, hy, 1.5F, withAlpha(GOLD_HI, 235));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  材料星 → 核心 的势流
    // ══════════════════════════════════════════════════════════════

    private static void links(PreviewCanvas c, float time, int mode) {
        for (int i = 0; i < 3; i++) {
            boolean empty = mode == MODE_MISSING && i == MAT_INDEX_MISSING;
            float a = (float) Math.toRadians(MAT_DEG[i]);
            // 整条"臂"：从学派环内缘一路插到核心，材料星就骑在臂上
            float r0 = R_BAND_IN - 2F;
            float r1 = R_CORE + 2F;
            float x0 = CX + (float) Math.cos(a) * r0;
            float y0 = CY + (float) Math.sin(a) * r0;
            float x1 = CX + (float) Math.cos(a) * r1;
            float y1 = CY + (float) Math.sin(a) * r1;

            int col = empty ? WARN : SCHOOL[FIRE];
            SdfShapes.line(c, x0, y0, x1, y1, 0.6F,
                    withAlpha(empty ? WARN : GOLD, empty ? 24 : 58));

            if (!empty) {
                float phase = (time * 0.55F + i * 0.33F) % 1F;
                pulse(c, x0, y0, x1, y1, col, phase, 0.85F);
            }
        }

        // 三颗材料星之间拉一圈虚线弦：把"三个材料"读成一个图案，而不是三个孤立的槽
        for (int i = 0; i < 3; i++) {
            float a1 = (float) Math.toRadians(MAT_DEG[i]);
            float a2 = (float) Math.toRadians(MAT_DEG[(i + 1) % 3]);
            dashedLine(c,
                    CX + (float) Math.cos(a1) * R_NODE, CY + (float) Math.sin(a1) * R_NODE,
                    CX + (float) Math.cos(a2) * R_NODE, CY + (float) Math.sin(a2) * R_NODE,
                    0.4F, withAlpha(GOLD, 38), 10);
        }
    }

    /** 虚线：把线段切成若干段画，段间留空。 */
    private static void dashedLine(PreviewCanvas c, float x1, float y1, float x2, float y2,
                                   float halfWidth, int argb, int dashes) {
        for (int i = 0; i < dashes; i++) {
            float t0 = (i + 0.14F) / dashes;
            float t1 = (i + 0.80F) / dashes;
            SdfShapes.line(c,
                    x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0,
                    x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1,
                    halfWidth, argb);
        }
    }

    /** 真实 magic_pulse 沿线段跑一颗光头。 */
    private static void pulse(PreviewCanvas c, float x1, float y1, float x2, float y2,
                              int color, float progress, float intensity) {
        PreviewShader sh = c.shader("magic_pulse");
        sh.set("uProgress", progress);
        sh.set("uColor", ((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F,
                (color & 0xFF) / 255F, 1F);
        sh.set("uIntensity", intensity);
        ribbon(c, sh, x1, y1, x2, y2, 1.7F);
    }

    /** Position(3) + UV0(2)，两种魔法 shader 共用。 */
    private static void ribbon(PreviewCanvas c, PreviewShader sh,
                               float x1, float y1, float x2, float y2, float halfWidth) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001F) {
            return;
        }
        float px = -dy / len * halfWidth;
        float py = dx / len * halfWidth;
        float[] data = {
                x1 + px, y1 + py, 0F, 0F, 0F,
                x1 - px, y1 - py, 0F, 0F, 1F,
                x2 + px, y2 + py, 0F, 1F, 0F,
                x2 + px, y2 + py, 0F, 1F, 0F,
                x1 - px, y1 - py, 0F, 0F, 1F,
                x2 - px, y2 - py, 0F, 1F, 1F
        };
        c.shaderMesh(sh, data, data.length, 6, GL11.GL_TRIANGLES);
    }

    // ══════════════════════════════════════════════════════════════
    //  材料星
    // ══════════════════════════════════════════════════════════════

    private static final int MAT_INDEX_MISSING = 2;

    private static void nodes(PreviewCanvas c, float time, int mode) {
        for (int i = 0; i < 3; i++) {
            boolean empty = mode == MODE_MISSING && i == MAT_INDEX_MISSING;
            float a = (float) Math.toRadians(MAT_DEG[i]);
            float nx = CX + (float) Math.cos(a) * R_NODE;
            float ny = CY + (float) Math.sin(a) * R_NODE;
            int bx = Math.round(nx - NODE_BOX * 0.5F);
            int by = Math.round(ny - NODE_BOX * 0.5F);

            // 底座：深色圆角 + 1px 边框；空槽用警示色
            int edge = empty ? withAlpha(WARN, 120) : withAlpha(SCHOOL[FIRE], 110);
            SdfShapes.roundRect(c, bx, by, NODE_BOX, NODE_BOX, 4F,
                    0xE60A0714, 0xE606040D, edge, 1F);

            if (empty) {
                c.textCenteredScaled("?", nx, ny - 8F, withAlpha(WARN, 210), true, 2F);
            } else {
                c.blit(c.texture(MAT_ICON[i]), Math.round(nx) - 8, Math.round(ny) - 8, 16, 16);
                // 四角小刻痕，强调"这是可点击的槽"
                int t = withAlpha(GOLD, 120);
                c.fill(bx + 2, by + 2, 3, 1, t);
                c.fill(bx + 2, by + 2, 1, 3, t);
                c.fill(bx + NODE_BOX - 5, by + NODE_BOX - 3, 3, 1, t);
                c.fill(bx + NODE_BOX - 3, by + NODE_BOX - 5, 1, 3, t);
            }

            // 数量/状态：贴在星下方，1x 小字
            String label = empty ? "EMPTY" : MAT_COUNT[i];
            c.textCentered(label, Math.round(nx), by + NODE_BOX + 4,
                    empty ? withAlpha(WARN, 190) : withAlpha(INK_DIM, 210), false);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  中心法术
    // ══════════════════════════════════════════════════════════════

    private static void core(PreviewCanvas c, float time, int mode, float progress) {
        boolean missing = mode == MODE_MISSING;
        int accent = missing ? WARN : SCHOOL[FIRE];

        // 暗井：只用一圈很淡的外晕 + 实心近黑，避免在星场上留下"圆盘"边
        SdfShapes.disc(c, CX, CY, R_CORE + 9F,
                withAlpha(accent, 7), withAlpha(accent, 2));
        SdfShapes.disc(c, CX, CY, R_CORE, 0xF7050409, 0xFF030208);
        // 井底一层暖光：中心不至于是一块死黑
        SdfShapes.disc(c, CX, CY, 30F,
                withAlpha(SCHOOL[FIRE], 30), withAlpha(SCHOOL[FIRE], 6));

        // 三根缓慢旋转的发丝辐条 + 一圈内环：比实心六芒星安静得多
        float rot = time * 0.05F;
        for (int i = 0; i < 3; i++) {
            float a = rot + i * TAU / 3F;
            SdfShapes.line(c, CX + (float) Math.cos(a) * 12F, CY + (float) Math.sin(a) * 12F,
                    CX + (float) Math.cos(a) * 33F, CY + (float) Math.sin(a) * 33F,
                    0.4F, withAlpha(GOLD, 30));
        }
        SdfShapes.ring(c, CX, CY, 33.6F, 34.4F, withAlpha(GOLD, 26), withAlpha(GOLD, 26), false);
        SdfShapes.ring(c, CX, CY, R_CORE - 0.5F, R_CORE + 0.5F,
                withAlpha(accent, 110), withAlpha(accent, 50), false);

        // 法术图标：32×32 = 16px 素材的整 2 倍，不糊
        c.blit(c.texture("textures/item/fire_ball.png"), CX - 16, CY - 16, 32, 32);

        // 绑定百分比只放在井内、图标下方：环外保持干净，不写字
        if (mode == MODE_BINDING) {
            c.textCentered(Math.round(progress * 100F) + "%", CX, CY + 24,
                    withAlpha(GOLD_HI, 240), true);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  右侧结果卡
    // ══════════════════════════════════════════════════════════════

    private static void card(PreviewCanvas c, float time, int mode, float progress) {
        boolean missing = mode == MODE_MISSING;
        boolean binding = mode == MODE_BINDING;

        SdfShapes.roundRect(c, CARD_X, CARD_Y, CARD_W, CARD_H, 7F,
                CARD_TOP, CARD_BOT, CARD_EDGE, 1F);
        // 内侧 1px 发丝，让卡片有"两层"的厚度，但不加第二圈亮边
        SdfShapes.roundRect(c, CARD_X + 3.5F, CARD_Y + 3.5F, CARD_W - 7F, CARD_H - 7F, 5F,
                0x00000000, 0x00000000, withAlpha(GOLD, 16), 1F);

        int x = CARD_X + 16;
        int r = CARD_X + CARD_W - 16;
        int y = CARD_Y;

        // 左侧一条学派色竖条：卡片和左环的"当前学派"是同一个颜色
        c.fill(CARD_X + 8, CARD_Y + 14, 2, 26, withAlpha(SCHOOL[FIRE], 200));

        // 法术名 + 状态
        c.textScaled(SPELL_NAME, x, y + 14, INK, true, 2F);
        String state = missing ? "INCOMPLETE" : (binding ? "BINDING" : "READY");
        int stateColor = missing ? WARN : GOLD_HI;
        c.textRight(state, r, y + 18, withAlpha(stateColor, 220), true);
        c.text(SPELL_TAGS, x, y + 36, withAlpha(SCHOOL[FIRE], 225), true);

        hairline(c, x, r, y + 50);

        statRow(c, x, r, y + 62, "MANA", Integer.toString(SPELL_MANA));
        statRow(c, x, r, y + 76, "COOLDOWN", String.format("%.1fs", SPELL_COOLDOWN_SEC));
        statRow(c, x, r, y + 90, "DAMAGE", String.format("%.1f", SPELL_DAMAGE));

        hairline(c, x, r, y + 104);

        c.text("EFFECT", x, y + 112, withAlpha(INK_FAINT, 220), false);
        WheelKit.drawWrapped(c, EFFECT, x, y + 124, CARD_W - 32, withAlpha(INK_DIM, 235), 3);

        hairline(c, x, r, y + 162);

        // 稳定性
        c.text("STABILITY", x, y + 170, withAlpha(INK_FAINT, 220), false);
        int stability = missing ? 0 : 82;
        c.textRight(stability + "%", r, y + 170,
                withAlpha(missing ? WARN : GOLD_HI, 230), true);
        bar(c, x, y + 182, CARD_W - 32, 5, stability / 100F, missing ? WARN : GOLD);

        // 复杂度（和缺失材料无关，永远保持金色）
        c.text("COMPLEXITY", x, y + 194, withAlpha(INK_FAINT, 220), false);
        pips(c, r, y + 194, 8, 5, GOLD);

        // 材料清单：和左侧三颗材料星一一对应，横向铺满一行
        c.text("INGREDIENTS", x, y + 212, withAlpha(INK_FAINT, 220), false);
        for (int i = 0; i < 3; i++) {
            boolean lacking = mode == MODE_MISSING && i == MAT_INDEX_MISSING;
            int ix = x + 96 + i * 46;
            if (lacking) {
                c.blit(c.texture(MAT_ICON[i]), ix, y + 210, 16, 16, 0F, 0F, 1F, 1F, 0x4DFFFFFF);
                c.text("x0", ix + 18, y + 214, withAlpha(WARN, 230), false);
            } else {
                c.blit(c.texture(MAT_ICON[i]), ix, y + 210, 16, 16);
                c.text(MAT_COUNT[i], ix + 18, y + 214, withAlpha(INK_DIM, 225), false);
            }
        }

        if (missing) {
            c.text("MISSING  /  CATALYST x1", x, y + 230, withAlpha(WARN, 225), true);
        } else {
            c.text("BLUEPRINT IS CONSUMED ON BIND", x, y + 230,
                    withAlpha(INK_FAINT, 190), false);
        }
    }

    private static void statRow(PreviewCanvas c, int x, int right, int y, String label, String value) {
        c.text(label, x, y, withAlpha(INK_FAINT, 230), false);
        c.textRight(value, right, y, withAlpha(INK, 240), true);
    }

    /** 1px 发丝线：左亮右暗的短渐变段 + 余下的低透明度底。 */
    private static void hairline(PreviewCanvas c, int x, int right, int y) {
        int w = right - x;
        c.fill(x, y, w, 1, withAlpha(GOLD, 26));
        c.fill(x, y, Math.min(72, w), 1, withAlpha(GOLD, 84));
    }

    /** 细条：底槽 + 填充 + 顶部高光，3 行像素做出"有厚度"的进度。 */
    private static void bar(PreviewCanvas c, int x, int y, int w, int h, float value, int color) {
        c.fill(x, y, w, h, 0x66000000);
        c.fill(x, y, w, 1, withAlpha(GOLD, 30));
        int fill = Math.max(0, Math.round(w * Math.max(0F, Math.min(1F, value))));
        if (fill > 0) {
            c.fill(x, y, fill, h, withAlpha(color, 170));
            c.fill(x, y, fill, 1, withAlpha(GOLD_HI, 190));
            c.fill(x, y + h - 1, fill, 1, withAlpha(GOLD_LO, 140));
        }
    }

    private static void pips(PreviewCanvas c, int right, int y, int total, int filled, int color) {
        int w = 7;
        int gap = 2;
        int start = right - (total * (w + gap) - gap);
        for (int i = 0; i < total; i++) {
            int px = start + i * (w + gap);
            if (i < filled) {
                c.fill(px, y, w, w, withAlpha(color, 200));
                c.fill(px, y, w, 1, withAlpha(GOLD_HI, 170));
            } else {
                c.fill(px, y, w, w, withAlpha(GOLD, 26));
                c.fill(px, y, w, 1, withAlpha(GOLD, 44));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  页眉 / 页脚
    // ══════════════════════════════════════════════════════════════

    private static void header(PreviewCanvas c, float time, int mode) {
        c.text("ROTTEN RUINS  /  ARCANE WORKBENCH", BODY_LEFT, 16,
                withAlpha(INK_FAINT, 210), false);

        // 标题左侧的 4 角星，缓慢呼吸
        float pulse = 0.85F + 0.15F * (float) Math.sin(time * 1.6F);
        if (SdfShapes.available(c)) {
            SdfShapes.star(c, BODY_LEFT + 2F, 36F, 6F * pulse, 2.2F, 4, 0F,
                    withAlpha(GOLD_HI, 200));
        }
        c.textScaled("SPELL SYNTHESIS", BODY_LEFT + 14, 27, INK, true, 2F);

        String sub = mode == MODE_BINDING
                ? "3 / 3 MATERIALS  /  BINDING  " + Math.round(BIND_PROGRESS * 100F) + "%"
                : (mode == MODE_MISSING
                        ? "2 / 3 MATERIALS  /  1 SLOT EMPTY"
                        : "3 / 3 MATERIALS  /  BLUEPRINT: METEOR SCAR");
        c.text(sub, BODY_LEFT + 14, 48, withAlpha(INK_DIM, 225), false);

        // ESC 键标 + CLOSE
        int chipW = 26;
        int chipX = BODY_RIGHT - 60 - chipW;
        SdfShapes.roundRect(c, chipX, 28, chipW, 14, 3F,
                0x660C0818, 0x66070510, withAlpha(GOLD, 70), 1F);
        c.textCentered("ESC", chipX + chipW / 2, 31, withAlpha(INK_DIM, 230), false);
        c.textRight("CLOSE", BODY_RIGHT, 32, withAlpha(INK_FAINT, 220), false);

        hairline(c, BODY_LEFT, BODY_RIGHT, HEADER_RULE_Y);
    }

    private static void footer(PreviewCanvas c, float time, int mode) {
        hairline(c, BODY_LEFT, BODY_RIGHT, FOOTER_RULE_Y);

        // 左：星砂刻度（10 格，填 6 格 = 36/60）
        int y = 350;
        if (SdfShapes.available(c)) {
            SdfShapes.star(c, BODY_LEFT + 2F, y + 3F, 4.2F, 1.6F, 4, 0F,
                    withAlpha(GOLD, 190));
        }
        c.text("STAR SAND", BODY_LEFT + 14, y, withAlpha(INK_FAINT, 230), false);
        int px = BODY_LEFT + 82;
        for (int i = 0; i < 10; i++) {
            int cell = px + i * 7;
            if (i < 6) {
                c.fill(cell, y + 1, 4, 7, withAlpha(GOLD, 190));
                c.fill(cell, y + 1, 4, 1, withAlpha(GOLD_HI, 200));
            } else {
                c.fill(cell, y + 1, 4, 7, withAlpha(GOLD, 28));
                c.fill(cell, y + 1, 4, 1, withAlpha(GOLD, 56));
            }
        }
        c.text("36 / 60", px + 10 * 7 + 8, y, withAlpha(INK_DIM, 230), false);

        // 右：CLEAR + BIND
        boolean missing = mode == MODE_MISSING;
        boolean binding = mode == MODE_BINDING;
        button(c, 424, 344, 76, 24, "CLEAR", false, true);
        button(c, 508, 344, 96, 22, binding ? "BINDING" : "BIND SPELL", true, !missing && !binding);
    }

    /** 两枚按钮：主操作实金填充 + 深色字；次操作只有发丝边框。 */
    private static void button(PreviewCanvas c, int x, int y, int w, int h,
                               String label, boolean primary, boolean enabled) {
        if (primary && enabled) {
            SdfShapes.roundRect(c, x, y, w, h, 4F, 0xFFF3DFA6, 0xFFC9994C,
                    withAlpha(GOLD_HI, 220), 1F);
            c.textCentered(label, x + w / 2, y + (h - 8) / 2 + 1, 0xFF1A1206, false);
            return;
        }
        int edge = primary ? withAlpha(GOLD_LO, 120) : withAlpha(GOLD, 40);
        int fill = primary ? 0x66140E08 : 0x330C0818;
        SdfShapes.roundRect(c, x, y, w, h, 4F, fill, fill, edge, 1F);
        c.textCentered(label, x + w / 2, y + (h - 8) / 2 + 1,
                withAlpha(primary ? INK_FAINT : INK_DIM, primary ? 150 : 210), false);
    }

    // ══════════════════════════════════════════════════════════════
    //  小工具
    // ══════════════════════════════════════════════════════════════

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
