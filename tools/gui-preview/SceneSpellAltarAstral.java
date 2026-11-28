/**
 * 法术合成界面（奥秘仪台）重绘 · <b>A 案：星象仪</b>（Astral Instrument）。
 *
 * <h3>这一案在解决什么</h3>
 * <p>实机 {@code PotentialCraftingScreen} 的问题是"三块拼图"：一块灰蓝面板、面板左外侧一条
 * 彩虹进度条、右外侧一列势能格，三者没有共同的语言，眼睛不知道该看哪。A 案把整台机器
 * 当成<b>一台黄铜星象仪</b>来画：</p>
 * <ul>
 *   <li>面板 = 一块嵌在暗色桌面上的星图玻璃，四周一圈 1px 旧金细线 + 四角菱形铆钉；</li>
 *   <li>15 个材料槽不再是一排"灰方块"，而是星图上的 15 个<b>刻度位</b>：
 *       每个槽对应表盘上一道外向刻痕，填了材料的槽才有辐条连到中心；</li>
 *   <li>进度条与势能格不再是面板外的补丁，而是机器的<b>左右两根立柱</b>：
 *       左边一支水银柱（进度），右边一排 15 枚黄铜保险丝（势能），
 *       两根立柱共用同一套铜架与刻度语言；</li>
 *   <li>全屏只有一个高亮：中心产物井 + 那枚实金按钮。其余一律压到 20%~45% 不透明度。</li>
 * </ul>
 *
 * <h3>和实机的对应关系（可 1:1 移植）</h3>
 * <p>几何全部照抄 {@code PotentialCraftingMenu} 的常量：面板 176×256、圆心 (96,84)、
 * 15 槽 18×18 / 半径 48、产物槽 (88,76)、按钮 (58,146,60,18)、背包 (8,182)、快捷栏 (8,236)。
 * 也就是说这一案落地时**只需要换 {@code renderBg} 的画笔**，命中框与 {@code Menu} 一行不用动。
 * 只有两处刻意挪动：状态行从 y=168（跟背包标题抢位置）改到面板下方 y=166 居中并去掉
 * "INVENTORY" 字标；势能读数从面板内正上方挪到页眉右侧。</p>
 *
 * <h3>像素与平滑</h3>
 * <p>结构线（边框、刻度、文字、图标、槽框）是 1px 硬边；圆、环、扇、圆角、胶囊线全部走
 * {@code sdf_ui} 的逐物理像素覆盖率，任何 GUI scale 下边缘都干净。底噪星场用项目真实
 * {@code magic_starfield}，只在面板内裁切绘制，强度压到 0.5。</p>
 *
 * <p>产物：{@code spell_altar_astral_ready.png} / {@code _crafting.png}，
 * 默认导出 {@code spell_altar_astral.png} = 就绪态。文字一律英文（预览字体只带拉丁）。</p>
 */
public final class SceneSpellAltarAstral {

    // ══════════════════════════════════════════════════════════════
    //  画布与面板（面板局部坐标 = 实机 GuiGraphics 坐标）
    // ══════════════════════════════════════════════════════════════

    private static final int PANEL_X = 32;
    private static final int PANEL_Y = 32;
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 256;

    /** 表盘中心：槽框左上角 = 圆心 + (cos,sin)·48 − 8，所以槽框中心比圆心偏 +1。 */
    private static final int CX = 96;
    private static final int CY = 84;
    private static final int SCX = CX + 1;
    private static final int SCY = CY + 1;

    private static final int RING_SLOTS = 15;
    private static final int SLOT = 18;
    private static final int R_SLOT = 48;

    /** 表盘中心在画布上的坐标（面板局部 + 面板原点）。 */
    private static final int AX = PANEL_X + SCX;
    private static final int AY = PANEL_Y + SCY;

    private static final int OUT_X = 88;
    private static final int OUT_Y = 76;

    private static final int BTN_X = 58;
    private static final int BTN_Y = 146;
    private static final int BTN_W = 60;
    private static final int BTN_H = 18;

    private static final int INV_X = 8;
    private static final int INV_Y = 182;
    private static final int HOT_Y = 236;

    /** 左侧水银柱（实机在面板外 -22,36,16×96，这里保留同一条柱位，只是改成铜架）。 */
    private static final int BAR_X = -22;
    private static final int BAR_Y = 36;
    private static final int BAR_W = 16;
    private static final int BAR_H = 96;

    /** 右侧势能保险丝（实机 182,25，15 格 16×6、间隔 2）。 */
    private static final int PWR_X = 182;
    private static final int PWR_Y = 25;
    private static final int PWR_W = 16;
    private static final int PWR_H = 6;
    private static final int PWR_GAP = 2;
    private static final int PWR_UNITS = 15;

    // ── 表盘装饰半径（从表盘中心 SCX,SCY 起算）──
    private static final float R_RIM = 64F;        // 外圈细线
    private static final float R_TICK_IN = 59F;    // 15 道外向刻痕
    private static final float R_TICK_OUT = 63.4F;
    private static final float R_ARC = 66F;        // 合成进度弧（只在合成中出现）
    private static final float R_WELL = 15.5F;     // 中心产物井

    // ══════════════════════════════════════════════════════════════
    //  配色：只有"旧金 + 暖白 + 一点琥珀"，没有第二种中性灰
    // ══════════════════════════════════════════════════════════════

    private static final int BG = 0xFF05040A;

    private static final int GOLD = 0xFFD9B771;
    private static final int GOLD_HI = 0xFFF7E6BF;
    private static final int GOLD_LO = 0xFF6B5228;
    private static final int INK = 0xFFE9E1CF;
    private static final int INK_DIM = 0xFF9A9078;
    private static final int AMBER = 0xFFE9A54A;

    private static final int PANEL_TOP = 0xF80D0A16;
    private static final int PANEL_BOT = 0xF5040309;
    private static final int SLOT_FILL = 0xC8080610;
    private static final int SLOT_FILL_EMPTY = 0xA806040C;

    private static final float TAU = (float) (Math.PI * 2.0);

    // ══════════════════════════════════════════════════════════════
    //  画面内容（取自实机 fire_ball 配方：11/15 材料已就位、势能 9/15）
    // ══════════════════════════════════════════════════════════════

    private static final String TITLE = "ARCANE ALTAR";
    private static final String PRODUCT_ICON = "textures/item/fire_ball.png";
    private static final String PRODUCT_NAME = "FIREBALL";
    private static final int POTENTIAL = 9;

    /** 环上 11 个材料的贴图（从正上方 0 号槽顺时针铺开），空槽不画。 */
    private static final String[] MATERIALS = {
            "textures/item/fire_mana_plate.png",
            "textures/item/mana_bottle.png",
            "textures/item/domite_crystal.png",
            "textures/item/void_shard.png",
            "textures/item/source_charge_sand.png",
            "textures/item/domerite_ingot.png",
            "textures/item/chip_null.png",
            "textures/item/chip_scan.png",
            "textures/item/chitin_polymer.png",
            "textures/item/hall_bone_fragments.png",
            "textures/item/true_copper.png"
    };

    /** 背包里放几件东西，别让下半张图空成一张表。 */
    private static final String[] BAG = {
            "textures/item/mana_bottle.png",
            "textures/item/chip_extract.png",
            "textures/item/true_copper.png",
            "textures/item/void_shard.png",
            "textures/item/domite_crystal.png",
            "textures/item/source_charge_sand.png"
    };
    /** 背包占位物品落在哪几个格子（列 + 行*9）。 */
    private static final int[] BAG_CELL = {0, 1, 4, 7, 10, 13};

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        shot(c, time, 0F, false);
        c.save("spell_altar_astral_ready");

        shot(c, time, 0.62F, true);
        c.save("spell_altar_astral_crafting");

        // 默认导出（GuiPreview 在 render 返回后自动存 spell_altar_astral.png）
        shot(c, time, 0F, false);
    }

    private static void shot(PreviewCanvas c, float time, float progress, boolean crafting) {
        background(c, time);
        panel(c, time);
        dial(c, time, progress, crafting);
        spokes(c, crafting, time);
        slots(c, crafting);
        well(c, time, progress, crafting);
        rails(c, time, progress, crafting);
        button(c, crafting);
        inventory(c);
        header(c, time, crafting);
        footer(c, crafting);
    }

    // ══════════════════════════════════════════════════════════════
    //  背景：一张暗色桌面 + 面板的暖投影
    // ══════════════════════════════════════════════════════════════

    private static void background(PreviewCanvas c, float time) {
        c.clear(BG);

        PreviewShader sf = c.shader("magic_starfield");
        sf.set("uTime", time);
        sf.set("uSize", (float) c.width, (float) c.height);
        sf.set("uMouse", 0F, 0F);
        sf.set("uIntensity", 0.85F);
        c.shaderQuad(sf, 0, 0, c.width, c.height);

        // 面板背后一层极淡的暖光：让面板"压在桌面上"，而不是浮在真空里
        SdfShapes.disc(c, PANEL_X + PANEL_W * 0.5F, PANEL_Y + PANEL_H * 0.5F, 168F,
                withAlpha(AMBER, 16), withAlpha(AMBER, 0));

        // 四边暗角
        c.fillGradientV(0, 0, c.width, 70, 0xE005040A, 0x0005040A);
        c.fillGradientV(0, c.height - 92, c.width, 92, 0x0005040A, 0xF005040A);
        int band = 64;
        for (int i = 0; i < band; i++) {
            int a = Math.round(72F * (1F - i / (float) band) * (1F - i / (float) band));
            if (a <= 0) {
                continue;
            }
            c.fill(i, 0, 1, c.height, a << 24);
            c.fill(c.width - 1 - i, 0, 1, c.height, a << 24);
        }

        // 几颗固定位置的散星（位置写死：每次出图一致，只做亮度呼吸）
        int[][] stars = {
                {22, 74}, {204, 60}, {18, 232}, {226, 196}, {96, 18},
                {150, 306}, {38, 300}, {210, 292}
        };
        for (int i = 0; i < stars.length; i++) {
            float twinkle = 0.5F + 0.5F * (float) Math.sin(time * 1.15F + i * 1.9F);
            c.fill(stars[i][0], stars[i][1], 1, 1,
                    (Math.round(64F * twinkle) << 24) | 0xE4D8BF);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  面板：星图玻璃 + 旧金细线 + 菱形铆钉
    // ══════════════════════════════════════════════════════════════

    private static void panel(PreviewCanvas c, float time) {
        // 投影：由外向内逐层加深，避免一刀切的硬黑边
        for (int i = 8; i >= 1; i--) {
            int a = 4 + (8 - i) * 3;
            c.fill(PANEL_X - i, PANEL_Y - i + 4, PANEL_W + i * 2, PANEL_H + i * 2, a << 24);
        }

        c.fillGradientV(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, PANEL_TOP, PANEL_BOT);

        // 面板内的星场：裁切绘制，压到很淡（结构线要压得住它）
        c.pushClip(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        PreviewShader sf = c.shader("magic_starfield");
        sf.set("uTime", time * 0.6F);
        sf.set("uSize", (float) PANEL_W, (float) PANEL_H);
        sf.set("uMouse", 0F, 0F);
        sf.set("uIntensity", 0.5F);
        c.shaderQuad(sf, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        c.fill(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, 0x8C050409);
        c.popClip();

        // 桌面到星图之间的一圈暖色内晕
        SdfShapes.roundRect(c, PANEL_X + 1, PANEL_Y + 1, PANEL_W - 2, PANEL_H - 2, 2F,
                0x00000000, withAlpha(AMBER, 10), 0, 0F);

        // 四周内暗角：让边缘退到暗处，中心的表盘才浮起来
        int vig = 34;
        c.fillGradientV(PANEL_X, PANEL_Y, PANEL_W, vig, 0x70000000, 0x00000000);
        c.fillGradientV(PANEL_X, PANEL_Y + PANEL_H - vig, PANEL_W, vig, 0x00000000, 0x70000000);
        c.fillGradientV(PANEL_X, PANEL_Y, vig, PANEL_H, 0x50000000, 0x00000000);
        c.fillGradientV(PANEL_X + PANEL_W - vig, PANEL_Y, vig, PANEL_H, 0x00000000, 0x50000000);

        // 外框：2px 黄铜包边（上亮下暗）+ 一根内细线，读成"嵌进桌面的仪器"
        c.fillGradientV(PANEL_X - 2, PANEL_Y - 2, PANEL_W + 4, 2,
                withAlpha(GOLD_HI, 150), withAlpha(GOLD, 60));
        c.fillGradientV(PANEL_X - 2, PANEL_Y + PANEL_H, PANEL_W + 4, 2,
                withAlpha(GOLD, 60), withAlpha(GOLD_LO, 170));
        c.fill(PANEL_X - 2, PANEL_Y, 2, PANEL_H, withAlpha(GOLD, 96));
        c.fill(PANEL_X + PANEL_W, PANEL_Y, 2, PANEL_H, withAlpha(GOLD, 96));
        c.strokeRect(PANEL_X - 1, PANEL_Y - 1, PANEL_W + 2, PANEL_H + 2,
                withAlpha(GOLD_HI, 190), withAlpha(GOLD, 90));
        c.strokeRect(PANEL_X + 2, PANEL_Y + 2, PANEL_W - 4, PANEL_H - 4,
                withAlpha(GOLD, 46), withAlpha(GOLD, 26));

        // 四角菱形铆钉
        int inset = 5;
        rivet(c, PANEL_X + inset, PANEL_Y + inset);
        rivet(c, PANEL_X + PANEL_W - inset - 1, PANEL_Y + inset);
        rivet(c, PANEL_X + inset, PANEL_Y + PANEL_H - inset - 1);
        rivet(c, PANEL_X + PANEL_W - inset - 1, PANEL_Y + PANEL_H - inset - 1);
    }

    /** 3×3 菱形铆钉：纯 1px 像素画，缩放到任何 GUI scale 都还是铆钉。 */
    private static void rivet(PreviewCanvas c, int x, int y) {
        int col = withAlpha(GOLD, 170);
        c.fill(x + 1, y, 1, 1, col);
        c.fill(x, y + 1, 3, 1, col);
        c.fill(x + 1, y + 2, 1, 1, col);
        c.fill(x + 1, y + 1, 1, 1, withAlpha(GOLD_HI, 220));
    }

    // ══════════════════════════════════════════════════════════════
    //  表盘：内圈刻度 + 8 芒星底纹 + 外圈 + 15 道刻痕 + 进度弧
    // ══════════════════════════════════════════════════════════════

    private static void dial(PreviewCanvas c, float time, float progress, boolean crafting) {
        // 内圈：只有一道"物镜"细环，井沿与它之间留给产物名
        SdfShapes.ring(c, AX, AY, R_WELL + 13F, R_WELL + 13.8F, withAlpha(GOLD, 30), withAlpha(GOLD, 30), false);

        // 表盘底：一层极淡的暖光圆盘，把 15 个槽"焊"成一块仪器面板
        SdfShapes.disc(c, AX, AY, 62F, withAlpha(AMBER, 20), withAlpha(AMBER, 4));

        // 8 芒星底纹：压在槽位下面的一层雕刻，给"表盘"而不是"15 个格子"的读法
        SdfShapes.star(c, AX, AY, 36F, 19F, 8, time * 0.006F, withAlpha(GOLD, 20));

        // 内圈 60 格细刻度：读数区的"游标"，只画在井外、槽内这一圈空地上
        for (int i = 0; i < 60; i++) {
            float a = i * TAU / 60F;
            boolean five = i % 5 == 0;
            float ri = five ? 29.5F : 31.5F;
            SdfShapes.line(c,
                    AX + (float) Math.cos(a) * ri, AY + (float) Math.sin(a) * ri,
                    AX + (float) Math.cos(a) * 35.5F, AY + (float) Math.sin(a) * 35.5F,
                    five ? 0.45F : 0.3F, withAlpha(GOLD, five ? 84 : 44));
        }

        // 外圈：一根细线 + 一圈缓慢反向旋转的虚线环
        SdfShapes.ring(c, AX, AY, R_RIM - 0.6F, R_RIM + 0.6F,
                withAlpha(GOLD, 104), withAlpha(GOLD, 104), false);
        SdfShapes.ring(c, AX, AY, R_RIM - 2.6F, R_RIM - 1.8F,
                withAlpha(GOLD, 34), withAlpha(GOLD, 34), false);
        dashedRing(c, AX, AY, R_RIM - 6.5F, 36, 0.58F, -time * 0.02F, withAlpha(GOLD, 40));

        // 15 道外向刻痕：一道对一个槽位，长刻痕在外、短点在内
        for (int i = 0; i < RING_SLOTS; i++) {
            float a = slotAngle(i);
            boolean major = i % 5 == 0;
            float r0 = major ? R_TICK_IN - 2.5F : R_TICK_IN;
            SdfShapes.line(c,
                    AX + (float) Math.cos(a) * r0, AY + (float) Math.sin(a) * r0,
                    AX + (float) Math.cos(a) * R_TICK_OUT, AY + (float) Math.sin(a) * R_TICK_OUT,
                    major ? 0.55F : 0.4F, withAlpha(GOLD, major ? 130 : 76));
            if (major) {
                SdfShapes.star(c,
                        AX + (float) Math.cos(a) * (R_TICK_OUT + 1.6F),
                        AY + (float) Math.sin(a) * (R_TICK_OUT + 1.6F),
                        1.8F, 0.6F, 4, 0F, withAlpha(GOLD_HI, 190));
            }
        }

        // 四个斜角上的小菱形（表盘的方位标记）
        for (int i = 0; i < 4; i++) {
            float a = (float) Math.toRadians(45 + i * 90);
            float x = AX + (float) Math.cos(a) * R_RIM;
            float y = AY + (float) Math.sin(a) * R_RIM;
            SdfShapes.star(c, x, y, 2.6F, 0.9F, 4, 0F, withAlpha(GOLD, 150));
        }

        // 合成进度：外侧一圈 2px 亮弧 + 弧头光点（就绪态完全不画，画面更干净）
        if (crafting && progress > 0.001F) {
            float a0 = -TAU / 4F;
            float a1 = a0 + TAU * Math.min(1F, progress);
            SdfShapes.wedge(c, AX, AY, R_ARC - 1.1F, R_ARC + 1.1F, a0, a1,
                    withAlpha(GOLD, 96), withAlpha(GOLD_HI, 232));
            float hx = AX + (float) Math.cos(a1) * R_ARC;
            float hy = AY + (float) Math.sin(a1) * R_ARC;
            SdfShapes.disc(c, hx, hy, 2.6F, withAlpha(GOLD_HI, 130), withAlpha(GOLD_HI, 30));
            c.fill(Math.round(hx), Math.round(hy), 1, 1, withAlpha(GOLD_HI, 255));
        }
    }

    /** 虚线环：segments 段，每段占 fill 比例。 */
    private static void dashedRing(PreviewCanvas c, float cx, float cy, float r,
                                   int segments, float fill, float rotation, int argb) {
        float step = TAU / segments;
        for (int i = 0; i < segments; i++) {
            float a0 = rotation + i * step;
            SdfShapes.wedge(c, cx, cy, r - 0.35F, r + 0.35F, a0, a0 + step * fill,
                    argb, withAlpha(argb, alpha(argb) / 2));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  辐条：只有"有材料的槽"才连到中心
    // ══════════════════════════════════════════════════════════════

    private static void spokes(PreviewCanvas c, boolean crafting, float time) {
        for (int i = 0; i < MATERIALS.length; i++) {
            float a = slotAngle(i);
            float r0 = R_WELL + 14F;
            float r1 = R_SLOT - 6F;
            float x0 = AX + (float) Math.cos(a) * r0;
            float y0 = AY + (float) Math.sin(a) * r0;
            float x1 = AX + (float) Math.cos(a) * r1;
            float y1 = AY + (float) Math.sin(a) * r1;
            SdfShapes.line(c, x0, y0, x1, y1, 0.5F, withAlpha(GOLD, crafting ? 60 : 40));

            if (crafting) {
                // 合成中：每根辐条上跑一颗光头，相位错开
                float t = ((time * 0.45F + i * 0.11F) % 1F);
                float x = x0 + (x1 - x0) * t;
                float y = y0 + (y1 - y0) * t;
                SdfShapes.disc(c, x, y, 1.6F, withAlpha(AMBER, 120), withAlpha(AMBER, 0));
                c.fill(Math.round(x), Math.round(y), 1, 1, withAlpha(GOLD_HI, 220));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  15 个材料槽
    // ══════════════════════════════════════════════════════════════

    private static void slots(PreviewCanvas c, boolean crafting) {
        for (int i = 0; i < RING_SLOTS; i++) {
            int x = PANEL_X + slotX(i);
            int y = PANEL_Y + slotY(i);
            boolean filled = i < MATERIALS.length;
            slotFrame(c, x, y, filled, crafting);
            if (filled) {
                c.blit(c.texture(MATERIALS[i]), x + 1, y + 1, 16, 16);
            } else {
                // 空位：一枚 1px 小菱形，不写问号（问号像报错，不像空槽）
                c.fill(x + 8, y + 7, 2, 1, withAlpha(GOLD, 52));
                c.fill(x + 7, y + 8, 4, 1, withAlpha(GOLD, 52));
                c.fill(x + 8, y + 9, 2, 1, withAlpha(GOLD, 52));
            }
        }
    }

    /** 槽框：18×18 暗底 + 1px 旧金细线 + 四角小刻痕；有材料的槽线更亮、底部有一道暖光。 */
    private static void slotFrame(PreviewCanvas c, int x, int y, boolean filled, boolean crafting) {
        c.fill(x, y, SLOT, SLOT, filled ? SLOT_FILL : SLOT_FILL_EMPTY);
        int line = withAlpha(GOLD, filled ? (crafting ? 120 : 96) : 44);
        c.strokeRect(x, y, SLOT, SLOT, line, withAlpha(GOLD, filled ? 44 : 24));

        if (filled) {
            // 底缘一道暖光：强调"这格有东西"，同时把冷色星场压下去
            c.fillGradientV(x + 1, y + SLOT - 4, SLOT - 2, 3,
                    withAlpha(AMBER, 0), withAlpha(AMBER, 46));
            int tick = withAlpha(GOLD_HI, 130);
            c.fill(x + 2, y + 2, 2, 1, tick);
            c.fill(x + 2, y + 2, 1, 2, tick);
            c.fill(x + SLOT - 4, y + SLOT - 3, 2, 1, tick);
            c.fill(x + SLOT - 3, y + SLOT - 4, 1, 2, tick);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  中心产物井
    // ══════════════════════════════════════════════════════════════

    private static void well(PreviewCanvas c, float time, float progress, boolean crafting) {
        // 井底：先一层暖光晕，再一块近黑的圆盘，最后才是产物槽的方框
        SdfShapes.disc(c, AX, AY, R_WELL + 12F,
                withAlpha(AMBER, crafting ? 26 : 16), withAlpha(AMBER, 0));
        SdfShapes.disc(c, AX, AY, R_WELL, 0xF7090710, 0xFF040208);
        SdfShapes.ring(c, AX, AY, R_WELL - 0.6F, R_WELL + 0.4F,
                withAlpha(GOLD, 110), withAlpha(GOLD, 46), false);

        // 井内三点缓慢旋转的定位刻痕
        float rot = time * 0.04F;
        for (int i = 0; i < 3; i++) {
            float a = rot + i * TAU / 3F;
            SdfShapes.line(c,
                    AX + (float) Math.cos(a) * (R_WELL - 6.5F), AY + (float) Math.sin(a) * (R_WELL - 6.5F),
                    AX + (float) Math.cos(a) * (R_WELL - 1.5F), AY + (float) Math.sin(a) * (R_WELL - 1.5F),
                    0.35F, withAlpha(GOLD, 70));
        }

        // 产物槽：只留一根细金线（去掉不透明底，让圆形井面带得出来）+ 图标
        c.strokeRect(PANEL_X + OUT_X, PANEL_Y + OUT_Y, SLOT, SLOT,
                withAlpha(GOLD_HI, 120), withAlpha(GOLD, 46));
        c.blit(c.texture(PRODUCT_ICON), PANEL_X + OUT_X + 1, PANEL_Y + OUT_Y + 1, 16, 16);

        // 产物名：放在"井沿"与"内刻度环"之间的空环里（16~28 这一圈正是空的）
        c.textCentered(PRODUCT_NAME, AX, AY + 19, withAlpha(GOLD_HI, 215), false);
    }

    // ══════════════════════════════════════════════════════════════
    //  左右两根立柱：水银柱（进度）+ 黄铜保险丝（势能）
    // ══════════════════════════════════════════════════════════════

    private static void rails(PreviewCanvas c, float time, float progress, boolean crafting) {
        mercury(c, time, progress, crafting);
        fuses(c, time, crafting);
    }

    /** 左柱：铜架里一支水银柱，从下往上填；上方留一小段空管当"还没到位"。 */
    private static void mercury(PreviewCanvas c, float time, float progress, boolean crafting) {
        int x = PANEL_X + BAR_X;
        int y = PANEL_Y + BAR_Y;

        // 铜架
        SdfShapes.roundRect(c, x - 1.5F, y - 3F, BAR_W + 3F, BAR_H + 6F, 3F,
                0xF00B0810, 0xF006040A, withAlpha(GOLD, 92), 1F);
        // 玻璃管：外壁一根亮边、内里压暗，让它像"玻璃"而不是"黑条"
        SdfShapes.roundRect(c, x + 3.5F, y + 3F, BAR_W - 7F, BAR_H - 6F, 2F,
                0xC00A0813, 0xC0020206, withAlpha(GOLD, 58), 1F);

        int innerTop = y + 5;
        int innerH = BAR_H - 10;
        int filled = Math.round(innerH * clamp01(progress));
        int colLeft = x + 5;
        int colW = BAR_W - 10;

        // 空管底也留一小滴：进度 0 时这根柱子才不像"坏掉的空槽"
        int minDrop = 3;
        int fillPx = Math.max(minDrop, filled);
        int top = innerTop + innerH - fillPx;
        c.fillGradientV(colLeft, top, colW, fillPx,
                withAlpha(progress > 0.01F ? GOLD_HI : AMBER, progress > 0.01F ? 235 : 170),
                withAlpha(AMBER, 200));
        // 液面：一道白高光 + 一道暖边
        c.fill(colLeft, top, colW, 1, withAlpha(0xFFFFFFFF, progress > 0.01F ? 200 : 110));
        if (filled > 0) {
            SdfShapes.disc(c, x + BAR_W * 0.5F, top + 2F, 8F,
                    withAlpha(AMBER, 54), withAlpha(AMBER, 0));
        }
        // 玻璃反光：管壁上一根 1px 竖直亮线
        c.fill(colLeft, innerTop, 1, innerH, withAlpha(0xFFFFFF, 26));

        // 刻度：横贯管身的 5 档水线（读数用），长短交替
        for (int i = 0; i <= 4; i++) {
            int ty = innerTop + innerH - Math.round(innerH * i / 4F);
            int inset = i % 2 == 0 ? 0 : 3;
            c.fill(colLeft + inset, ty, colW - inset, 1,
                    withAlpha(GOLD, i % 2 == 0 ? 78 : 44));
        }

        // 上端一颗指示灯：合成中是实心琥珀，平时是暗金
        float pulse = 0.6F + 0.4F * (float) Math.sin(time * 3.0F);
        SdfShapes.disc(c, x + BAR_W * 0.5F, y - 1F, 2.6F,
                withAlpha(crafting ? AMBER : GOLD_LO, crafting ? Math.round(200 * pulse) : 120),
                withAlpha(crafting ? AMBER : GOLD_LO, 0));
    }

    /** 右柱：15 枚黄铜保险丝，一格一点势能；亮的是暖金芯，灭的是空槽。 */
    private static void fuses(PreviewCanvas c, float time, boolean crafting) {
        int x = PANEL_X + PWR_X;
        int y = PANEL_Y + PWR_Y;
        int totalH = PWR_UNITS * PWR_H + (PWR_UNITS - 1) * PWR_GAP;

        SdfShapes.roundRect(c, x - 3F, y - 3F, PWR_W + 6F, totalH + 6F, 3F,
                0xF00B0810, 0xF006040A, withAlpha(GOLD, 92), 1F);

        for (int i = 0; i < PWR_UNITS; i++) {
            int py = y + i * (PWR_H + PWR_GAP);
            boolean lit = i < POTENTIAL;
            SdfShapes.roundRect(c, x, py, PWR_W, PWR_H, 1.2F,
                    lit ? withAlpha(GOLD_LO, 150) : 0x8C05040A,
                    lit ? withAlpha(GOLD_LO, 90) : 0x8C020206,
                    withAlpha(GOLD, lit ? 70 : 34), 1F);
            if (lit) {
                // 保险丝芯：中间一条亮金横条 + 两端小点
                c.fill(x + 2, py + 2, PWR_W - 4, 2, withAlpha(GOLD_HI, 225));
                c.fill(x + 1, py + 2, 1, 2, withAlpha(AMBER, 200));
                c.fill(x + PWR_W - 2, py + 2, 1, 2, withAlpha(AMBER, 200));
            }
        }

        // 合成中：整排保险丝随进度整体呼吸一次（只在合成帧里出现）
        if (crafting) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(time * 2.4F);
            SdfShapes.roundRect(c, x - 1.5F, y - 1.5F, PWR_W + 3F, totalH + 3F, 2.5F,
                    0x00000000, 0x00000000, withAlpha(AMBER, Math.round(70 * pulse)), 1F);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  按钮：黄铜铭牌
    // ══════════════════════════════════════════════════════════════

    private static void button(PreviewCanvas c, boolean crafting) {
        int x = PANEL_X + BTN_X;
        int y = PANEL_Y + BTN_Y;

        SdfShapes.roundRect(c, x, y, BTN_W, BTN_H, 2.5F,
                crafting ? 0xF00A0810 : 0xF81B1308,
                crafting ? 0xF006040A : 0xF80D0A05,
                withAlpha(crafting ? GOLD_LO : GOLD, crafting ? 96 : 178), 1F);

        if (!crafting) {
            // 内侧第二道细线 + 两端菱形装饰，读成"铭牌"而不是"按钮块"
            c.strokeRect(x + 2, y + 2, BTN_W - 4, BTN_H - 4,
                    withAlpha(GOLD, 54), withAlpha(GOLD, 30));
            SdfShapes.star(c, x + 5.5F, y + BTN_H * 0.5F, 2.2F, 0.8F, 4, 0F, withAlpha(GOLD, 150));
            SdfShapes.star(c, x + BTN_W - 5.5F, y + BTN_H * 0.5F, 2.2F, 0.8F, 4, 0F, withAlpha(GOLD, 150));
            // 顶缘高光
            c.fill(x + 2, y + 1, BTN_W - 4, 1, withAlpha(GOLD_HI, 46));
        }

        String label = crafting ? "CRAFTING" : "CRAFT";
        c.textCentered(label, PANEL_X + BTN_X + BTN_W / 2, y + 5,
                crafting ? withAlpha(INK_DIM, 150) : withAlpha(GOLD_HI, 245), true);
    }

    // ══════════════════════════════════════════════════════════════
    //  玩家背包：凹槽 + 极淡的星点，跟面板同一套语言
    // ══════════════════════════════════════════════════════════════

    private static void inventory(PreviewCanvas c) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                inventoryCell(c, PANEL_X + INV_X + col * 18, PANEL_Y + INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            inventoryCell(c, PANEL_X + INV_X + col * 18, PANEL_Y + HOT_Y);
        }

        for (int i = 0; i < BAG.length; i++) {
            int cell = BAG_CELL[i];
            int col = cell % 9;
            int row = cell / 9;
            c.blit(c.texture(BAG[i]),
                    PANEL_X + INV_X + col * 18 + 1, PANEL_Y + INV_Y + row * 18 + 1, 16, 16);
        }
        // 快捷栏第 1、3 格也放两件，让底部不至于空一条
        c.blit(c.texture("textures/item/mana_bottle.png"), PANEL_X + INV_X + 1, PANEL_Y + HOT_Y + 1, 16, 16);
        c.blit(c.texture("textures/item/true_copper.png"), PANEL_X + INV_X + 2 * 18 + 1,
                PANEL_Y + HOT_Y + 1, 16, 16);
    }

    private static void inventoryCell(PreviewCanvas c, int x, int y) {
        c.fill(x, y, SLOT, SLOT, 0x9C05040B);
        c.strokeRect(x, y, SLOT, SLOT, withAlpha(GOLD, 34), withAlpha(GOLD, 20));
        // 左上内阴影：一格凹陷
        c.fill(x + 1, y + 1, SLOT - 2, 1, 0x5C000000);
        c.fill(x + 1, y + 1, 1, SLOT - 2, 0x40000000);
    }

    // ══════════════════════════════════════════════════════════════
    //  页眉 / 页脚：标题 + 势能读数（同一行）、状态行
    // ══════════════════════════════════════════════════════════════

    private static void header(PreviewCanvas c, float time, boolean crafting) {
        // 一行标题 + 一行读数挤在同一行：表盘要占满 17~150 这整段高度，页眉不能有第二行
        c.text(TITLE, PANEL_X + 8, PANEL_Y + 7, withAlpha(GOLD_HI, 240), true);

        String label = "POTENTIAL";
        String value = POTENTIAL + "/" + PWR_UNITS;
        int valueRight = PANEL_X + PANEL_W - 8;
        int valueLeft = valueRight - c.textWidth(value);
        c.text(value, valueLeft, PANEL_Y + 7, withAlpha(POTENTIAL > 0 ? GOLD_HI : AMBER, 235), true);
        c.textRight(label, valueLeft - 5, PANEL_Y + 7, withAlpha(INK_DIM, 170), false);

        // 发丝分隔线 + 中央菱形：所有区块都靠它分栏，不再画框
        c.fill(PANEL_X + 8, PANEL_Y + 17, PANEL_W - 16, 1, withAlpha(GOLD, 70));
        SdfShapes.star(c, PANEL_X + PANEL_W * 0.5F, PANEL_Y + 17.5F, 2.4F, 0.9F, 4, 0F,
                withAlpha(GOLD, 170));
    }

    private static void footer(PreviewCanvas c, boolean crafting) {
        // 状态行只写状态（产物名交给表盘内部），合成中给读秒
        String status = crafting ? "CRAFTING...   4 s LEFT" : "READY TO CRAFT";
        int color = crafting ? withAlpha(INK, 215) : withAlpha(GOLD_HI, 220);
        c.textCentered(status, PANEL_X + PANEL_W / 2, PANEL_Y + 166, color, true);

        if (crafting) {
            // 合成中：状态行左侧一枚跳动的小点
            SdfShapes.disc(c, PANEL_X + PANEL_W * 0.5F - c.textWidth(status) * 0.5F - 6F,
                    PANEL_Y + 170F, 1.8F, withAlpha(AMBER, 220), withAlpha(AMBER, 40));
        }

        // 背包上方的第二道发丝线：把"操作区"和"背包"分开，不再靠空白硬隔
        c.fill(PANEL_X + 8, PANEL_Y + 178, PANEL_W - 16, 1, withAlpha(GOLD, 44));
        SdfShapes.star(c, PANEL_X + PANEL_W * 0.5F, PANEL_Y + 178.5F, 2.0F, 0.8F, 4, 0F,
                withAlpha(GOLD, 120));
    }

    // ══════════════════════════════════════════════════════════════
    //  几何小工具
    // ══════════════════════════════════════════════════════════════

    private static float slotAngle(int index) {
        return (float) Math.toRadians(-90 + index * (360F / RING_SLOTS));
    }

    private static int slotX(int index) {
        return Math.round(SCX + (float) Math.cos(slotAngle(index)) * R_SLOT - SLOT * 0.5F);
    }

    private static int slotY(int index) {
        return Math.round(SCY + (float) Math.sin(slotAngle(index)) * R_SLOT - SLOT * 0.5F);
    }

    private static float clamp01(float v) {
        return v < 0F ? 0F : Math.min(v, 1F);
    }

    private static int withAlpha(int argb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0x00FFFFFF);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }
}
