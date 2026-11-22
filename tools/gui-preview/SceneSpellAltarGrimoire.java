/**
 * 法术合成界面（奥秘仪台）—— <b>羊皮纸手稿 / 炼金术手抄本（Grimoire Folio）</b> 风格稿。
 *
 * <h3>这是什么</h3>
 * <p>三风格对照稿之一（另两支见 {@code SceneSpellAltarAstral} / {@code SceneSpellAltarPrism}）。
 * 三者<b>只换材质与光语言，不换功能分区与命中框</b>：面板 176×256、法阵圆心 (96,84)、
 * 半径 48、15 个 18×18 材料槽、产物槽 (88,76)、按钮 (58,146,60,18)、背包 (8,182)、
 * 快捷栏 (8,236) 全部与 {@code PotentialCraftingMenu} 一致，所以三张图可以叠在一起比。</p>
 *
 * <h3>为什么这样排（把"三块硬拼"收回一张纸上）</h3>
 * <p>实机界面是「面板外挂进度条 + 面板外挂势能条 + 一块灰面板」，三块各自为政。
 * 这一版把两种读数当成<b>书页页边的批注（marginalia）</b>：左侧一条竖向墨槽（羽笔刻度尺）
 * 表示合成进度，右侧一列 15 格账簿式印点表示势能；读数全部落在页边的窄栏里 ——
 * 既没塞进正文（法阵），也不再飘到面板外。</p>
 * <p>视线落点只有一个：法阵中心的墨绘徽章（{@code fire_ball}）+ 一枚火漆印章按钮。
 * 其余元素压成"纸上的墨"，有彩度的只剩朱砂（火漆/势能/液面）与赭金（辐条/刻度）。</p>
 * <p>法阵按手抄本画法：<b>正上方开口的双圈</b>（笔尖在开口两端提笔留两个墨点，开口正好把
 * 0 号材料位让出来，页眉也就有了干净的空白）、内圈、15 个 18×18 纸面凹槽材料位
 * （实心图标 = 已填，空心 + 中央墨点 = 空）、11 根赭墨辐条从已填材料汇向中心、
 * 中心徽章带铸齿边与 4 枚三角符号。辐条的"实/虚"直接编码了 11/15 这件事。</p>
 *
 * <h3>为什么页眉只有一行</h3>
 * <p>0 号材料位在 y=28、双圈外沿在 y≈23，页眉只敢占 y=13..20 这一行：
 * 左 "ARCANE ALTAR"、右 "POTENTIAL 9/15"（右边缘对齐，数字用朱砂）。
 * 完整标题记在 {@link #TITLE_FULL}，进游戏用 lang 的真文案；设计稿受 150px 行宽限制，
 * 一行塞不下 "ARCANE INSTRUMENT ALTAR"(132) + 读数(80)，所以按版式缩写成短标题。</p>
 *
 * <h3>像素与平滑 / 一个框架坑</h3>
 * <p>1px 结构线（针脚、槽孔内阴影、刻度、账簿格线）一律 {@code c.fill}：硬边，缩放时仍是像素；
 * 一切圆/环/扇/圆角/胶囊线走 {@link SdfShapes}（逐物理像素覆盖率），-Scale 3 下没有台阶边。
 * 图标 16×16 落在整数坐标、不缩放（SPEC 明确禁止拉伸 fire_ball）。</p>
 * <p><b>注意</b>：{@code PreviewCanvas.shaderQuad()} 先对顶点做一次 2D 变换，
 * {@code drawShader()} 在 {@code hasTransform()} 时<b>再</b>做一次 —— SDF 形状放进
 * {@code pushTransform} 会整体偏移两倍。所以本场景<b>不用</b> pushTransform，
 * 面板局部坐标一律经 {@link #ox(float)} / {@link #oy(float)}（= +PX/+PY）显式平移，
 * 好处是每个数字都能和 {@code PotentialCraftingMenu} 的常量逐字对照。</p>
 *
 * <p>产物：{@code spell_altar_grimoire_ready.png}（就绪）/ {@code spell_altar_grimoire_crafting.png}
 * （62% 合成中）/ 默认导出 {@code spell_altar_grimoire.png} = 就绪态。
 * 画面内文字一律英文：预览字体只有拉丁位图字体，{@code …} 与 {@code ·} 不在 ascii.png 里，
 * 所以合成中文案写成 {@code CRAFTING...  4 s LEFT}（进游戏应由 lang 提供真文案）。</p>
 */
public final class SceneSpellAltarGrimoire {

    // ══════════════════════════════════════════════════════════════
    //  0. 画布 / 面板原点（SPEC.md §0：逻辑 240×320，面板贴 (32,32)）
    // ══════════════════════════════════════════════════════════════

    private static final int PX = 32;
    private static final int PY = 32;
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 256;

    // ══════════════════════════════════════════════════════════════
    //  1. 不可改几何（SPEC.md §1，全部面板局部坐标）
    // ══════════════════════════════════════════════════════════════

    private static final int CX = 96;              // RING_CENTER_X
    private static final int CY = 84;              // RING_CENTER_Y
    private static final int RING_RADIUS = 48;     // RING_RADIUS
    private static final int RING_SLOTS = 15;      // RING_SLOTS
    private static final int SLOT = 18;            // 18×18 命中框（图标 16×16 居中）
    private static final int OUT_X = 88;           // 产物槽左上
    private static final int OUT_Y = 76;
    private static final int BTN_X = 58;           // BUTTON_X/Y/WIDTH/HEIGHT
    private static final int BTN_Y = 146;
    private static final int BTN_W = 60;
    private static final int BTN_H = 18;
    private static final int INV_X = 8;            // 背包 3×9
    private static final int INV_Y = 182;
    private static final int INV_COLS = 9;
    private static final int INV_ROWS = 3;
    private static final int INV_STRIDE = 18;
    private static final int HOTBAR_Y = 236;       // 快捷栏（236+18=254，面板 256 → 留 2px）

    // ══════════════════════════════════════════════════════════════
    //  2. 书页装帧（皮革包边 / 针脚 / 铁包角 / 羊皮纸边）
    // ══════════════════════════════════════════════════════════════

    private static final int LEATHER = 6;          // 上/左/右的皮革包边宽度
    private static final int STITCH = 2;           // 针脚线距面板边（在包边中间）
    private static final int CAP = 13;             // 铁包角边长
    private static final int PAPER_X = LEATHER;                 // 6
    private static final int PAPER_Y = LEATHER;                 // 6
    private static final int PAPER_W = PANEL_W - LEATHER * 2;   // 164
    /** 纸页一直铺到 y=254：快捷栏最后一格到 254，底部只留 2px 皮革，不许切格子。 */
    private static final int PAPER_B = HOTBAR_Y + INV_STRIDE;
    private static final int PAPER_H = PAPER_B - PAPER_Y;       // 248
    private static final int PAPER_R = PAPER_X + PAPER_W;       // 170

    // ── 法阵同心半径（材料位在 r=48，18×18 凹槽外角 57.7 → 环框落在它之外）──
    private static final float R_RING_IN = 58.7F;   // 双圈内圈
    private static final float R_RING_OUT = 61.3F;  // 双圈外圈
    /** 双圈在正上方的开口（度）：把 0 号材料位让出来，也给页眉留出干净空白。 */
    private static final float GAP_A0 = -101F;
    private static final float GAP_A1 = -79F;
    private static final float R_BAND = 35.5F;      // 内圈（压在辐条上）
    private static final float R_INNER = 30.0F;     // 内圈细线
    private static final float R_BADGE = 22.5F;     // 中心徽章
    private static final float R_SEAL = 15.5F;      // 徽章内圈
    private static final float R_SPOKE_IN = 24.2F;  // 辐条内端（贴着徽章铸齿）
    private static final float R_SPOKE_OUT = 39.2F; // 辐条外端（贴着材料位内沿）
    private static final int BX = OUT_X + 9;        // 徽章圆心 = 产物槽中心 (97,85)
    private static final int BY = OUT_Y + 9;

    /** 页边读数栏：左（进度墨槽）/ 右（势能账列）都垂直对齐法阵中心 y=84。 */
    private static final int GAUGE_X = 18;
    private static final int GAUGE_W = 11;
    private static final int GAUGE_Y = 38;
    private static final int GAUGE_H = 92;
    private static final int LEDGER_X = 158;
    private static final int LEDGER_W = 10;
    private static final int LEDGER_TOP = 33;       // 15 格 ×7px - 2 = 103 → 33..135，中心 84

    // ══════════════════════════════════════════════════════════════
    //  3. 配色：纸、墨、朱砂、赭金、旧铁 —— 没有纯黑纯白
    // ══════════════════════════════════════════════════════════════

    private static final int INK = 0xFF241608;         // 深褐墨（正文/法阵）
    private static final int INK_MID = 0xFF432C17;
    private static final int INK_FAINT = 0xFF6B4F30;
    private static final int VERMILION = 0xFFA2331A;   // 朱砂
    private static final int VERMILION_HI = 0xFFC44E28;
    private static final int VERMILION_DK = 0xFF661C0C;
    private static final int OCHRE = 0xFFA87A26;       // 赭金
    private static final int OCHRE_HI = 0xFFD2A552;
    private static final int OCHRE_DK = 0xFF6E4E16;

    private static final int VELLUM_HI = 0xFFF3E7C9;
    private static final int VELLUM_LO = 0xFFD8C39B;
    private static final int PAPER_SEAL = 0xFFF5E7C6;

    private static final int LEATHER_HI = 0xFF55321F;
    private static final int LEATHER_LO = 0xFF301A0E;
    private static final int LEATHER_EDGE = 0xFF1A0E06;
    private static final int THREAD = 0xFFD3AB68;
    private static final int IRON = 0xFF6C6660;
    private static final int IRON_HI = 0xFFA79D91;
    private static final int IRON_DK = 0xFF3A3531;

    private static final int DESK_HI = 0xFF332112;
    private static final int DESK_LO = 0xFF1B1109;
    private static final int DESK_SEAM = 0xFF100A05;

    private static final int WAX_HI = 0xFFB8442C;
    private static final int WAX_LO = 0xFF86250F;
    private static final int WAX_EDGE = 0xFF521605;
    private static final int ASH_HI = 0xFF6E6459;
    private static final int ASH_LO = 0xFF4C453D;
    private static final int ASH_EDGE = 0xFF2E2A25;

    // ══════════════════════════════════════════════════════════════
    //  4. 内容（与另两支风格稿完全一致）
    // ══════════════════════════════════════════════════════════════

    /** 从 0 号槽（正上方）顺时针填 11 个；其余 4 格是空态。 */
    private static final String[] MAT_ICON = {
            "fire_mana_plate", "mana_bottle", "domite_crystal", "void_shard",
            "source_charge_sand", "domerite_ingot", "chip_null", "chip_scan",
            "chitin_polymer", "hall_bone_fragments", "true_copper"
    };

    private static final int POTENTIAL = 9;
    private static final int POTENTIAL_MAX = 15;
    private static final float CRAFT_PROGRESS = 0.62F;

    /** 完整标题（进游戏用 lang 真文案；画面上受页眉一行 150px 限制，见类注释）。 */
    private static final String TITLE_FULL = "ARCANE INSTRUMENT ALTAR";
    private static final String TITLE_LINE = "ARCANE ALTAR";
    private static final String STATUS_READY = "READY TO CRAFT";
    /** ascii.png 没有 … 与 ·，用 ASCII 近似（进游戏由 lang 提供真文案）。 */
    private static final String STATUS_CRAFTING = "CRAFTING...  4 s LEFT";
    private static final String BTN_READY = "CRAFT";
    private static final String BTN_BUSY = "CRAFTING";
    private static final String OUT_ICON = "fire_ball";

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        shot(c, time, false);
        c.save("spell_altar_grimoire_ready");
        shot(c, time, true);
        c.save("spell_altar_grimoire_crafting");
        // 默认导出：GuiPreview 在 render 返回后会再存一张 <scene>.png
        shot(c, time, false);
    }

    private static void shot(PreviewCanvas c, float time, boolean crafting) {
        desk(c, time);
        binding(c, time);
        page(c, time, crafting);
    }

    // ══════════════════════════════════════════════════════════════
    //  5. 暗色木桌 + 烛光 + 暗角（程序化，不用贴图）
    // ══════════════════════════════════════════════════════════════

    private static void desk(PreviewCanvas c, float time) {
        int w = c.width;
        int h = c.height;
        c.clear(DESK_LO);

        // 木板：三段，接缝 = 1px 深色 + 1px 上沿高光，木纹只做水平细丝
        int[] seam = {0, 78, 176, 272, h};
        for (int p = 0; p + 1 < seam.length; p++) {
            int y0 = seam[p];
            int y1 = seam[p + 1];
            c.fillGradientV(0, y0, w, y1 - y0, DESK_HI, DESK_LO);
            if (p > 0) {
                c.fill(0, y0, w, 1, DESK_SEAM);
                c.fill(0, y0 + 1, w, 1, 0x18E8C79A);
            }
            for (int i = 0; i < 24; i++) {
                int gy = y0 + 4 + (int) (hash(p * 71 + i) * (y1 - y0 - 8));
                int gx = (int) (hash(p * 131 + i * 7 + 3) * w) - 40;
                int gw = 18 + (int) (hash(p * 17 + i * 29) * 104);
                c.fill(gx, gy, gw, 1, (i & 1) == 0 ? 0x18E8C79A : 0x240A0603);
            }
        }

        // 烛光：光源在画布左上角外，15 层同心盘叠出柔和暖晕（中心一点更亮）
        int warm = 0xFFF2B45E;
        for (int i = 14; i >= 0; i--) {
            float r = 30F + i * 12F;
            int a = 3 + (14 - i);
            SdfShapes.disc(c, 10F, 2F, r, withAlpha(warm, a), withAlpha(warm, a));
        }
        SdfShapes.disc(c, 8F, 0F, 13F, 0x80F8DEA6, 0x30F8DEA6);

        // 四边暗角：上下渐变 + 左右逐列叠加（越靠边越暗）
        c.fillGradientV(0, 0, w, 64, 0x9C0B0704, 0x000B0704);
        c.fillGradientV(0, h - 78, w, 78, 0x000B0704, 0xC80B0704);
        for (int i = 0; i < 54; i++) {
            float t = 1F - i / 54F;
            int a = Math.round(148F * t * t * t);
            if (a <= 0) {
                continue;
            }
            c.fill(i, 0, 1, h, a << 24);
            c.fill(w - 1 - i, 0, 1, h, a << 24);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  6. 书页装帧（画布坐标：阴影 → 皮革 → 针脚 → 羊皮纸 → 铁包角）
    // ══════════════════════════════════════════════════════════════

    private static void binding(PreviewCanvas c, float time) {
        // 纸片浮在桌上：三层外扩圆角矩形当软阴影，往下偏 2px
        for (int i = 3; i >= 1; i--) {
            float pad = i * 2.6F;
            SdfShapes.roundRect(c, PX - pad, PY - pad + 2.2F,
                    PANEL_W + pad * 2F, PANEL_H + pad * 2F, 4F + i * 1.6F,
                    0x34070302, 0x2A050201, 0, 0F);
        }

        // 皮革封面
        SdfShapes.roundRect(c, PX, PY, PANEL_W, PANEL_H, 4F,
                LEATHER_HI, LEATHER_LO, LEATHER_EDGE, 1.2F);
        // 皮面斑驳：只沿包边撒几团更深的斑（确定性位置）
        for (int i = 0; i < 26; i++) {
            float fx = hash(i * 13 + 5) * PANEL_W;
            float fy = hash(i * 29 + 11) * PANEL_H;
            boolean onBand = fx < LEATHER + 1 || fx > PANEL_W - LEATHER - 1
                    || fy < LEATHER + 1 || fy > PANEL_H - LEATHER - 1;
            if (!onBand) {
                continue;
            }
            SdfShapes.disc(c, PX + fx, PY + fy, 1.6F + hash(i * 7) * 2.2F,
                    0x22140B06, 0x14140B06);
        }
        // 封面内圈压边：羊皮纸和皮革之间一条深缝
        SdfShapes.roundRect(c, PX + PAPER_X - 0.5F, PY + PAPER_Y - 0.5F,
                PAPER_W + 1F, PAPER_H + 1F, 3F, 0x00000000, 0x00000000, 0x9A0F0803, 1F);

        stitches(c);

        // 羊皮纸：竖向渐变（上亮下暗）+ 一圈暗边
        SdfShapes.roundRect(c, PX + PAPER_X, PY + PAPER_Y, PAPER_W, PAPER_H, 2.5F,
                VELLUM_HI, VELLUM_LO, 0x9A452F18, 1F);
        paperAge(c);

        // 铁包角画在最后：压在纸角与包边上（9×9 角块 + 两条铁条，让开 x=9 起的槽位）
        corners(c);
    }

    /**
     * 手缝线：上、左、右三条边各一串 3~4px 的针脚，间距 6px，长短与颜色都略有出入。
     * 底边不缝 —— 纸页铺到 y=254，底部只剩 2px 皮革，正好当书口。
     */
    private static void stitches(PreviewCanvas c) {
        int a = CAP + 2;
        int b = PANEL_W - CAP - 2;
        for (int x = a; x < b; x += 6) {
            int len = Math.min(3 + ((x >> 1) & 1), b - x);
            int col = (x % 12 == 0) ? THREAD : withAlpha(THREAD, 176);
            c.fill(PX + x, PY + STITCH, len, 1, col);
        }
        int yb = PANEL_H - CAP - 2;
        for (int y = a; y < yb; y += 6) {
            int len = Math.min(3 + ((y >> 1) & 1), yb - y);
            int col = (y % 12 == 0) ? THREAD : withAlpha(THREAD, 176);
            c.fill(PX + STITCH, PY + y, 1, len, col);
            c.fill(PX + PANEL_W - STITCH - 1, PY + y, 1, len, col);
        }
    }

    /** 纸面老化：四边由外向内 7 层暗边、墨点纤维、几处淡淡的茶渍。 */
    private static void paperAge(PreviewCanvas c) {
        for (int i = 0; i < 7; i++) {
            int a = 40 - i * 5;
            int col = withAlpha(0x6B4A26, a);
            c.fill(PX + PAPER_X + i, PY + PAPER_Y, 1, PAPER_H, col);
            c.fill(PX + PAPER_R - 1 - i, PY + PAPER_Y, 1, PAPER_H, col);
            c.fill(PX + PAPER_X, PY + PAPER_Y + i, PAPER_W, 1, col);
            c.fill(PX + PAPER_X, PY + PAPER_B - 1 - i, PAPER_W, 1, col);
        }
        // 纤维/墨点：一半偏暗一半偏亮，位置固定
        for (int i = 0; i < 130; i++) {
            int x = PAPER_X + 2 + (int) (hash(i * 3 + 1) * (PAPER_W - 4));
            int y = PAPER_Y + 2 + (int) (hash(i * 5 + 2) * (PAPER_H - 4));
            boolean dark = (i & 1) == 0;
            int a = 6 + (int) (hash(i * 11 + 7) * 14);
            c.fill(PX + x, PY + y, 1, 1,
                    dark ? (a << 24) | 0x5A3A1A : (a << 24) | 0xFCF2DA);
        }
        // 茶渍：只在几处空白纸面上留很淡的渍（标题区不放，免得像脏点）
        stain(c, 150F, 146F, 9F, 12);
        stain(c, 30F, 176F, 7F, 10);
        stain(c, 146F, 26F, 6F, 8);
    }

    private static void stain(PreviewCanvas c, float x, float y, float r, int alpha) {
        SdfShapes.disc(c, PX + x, PY + y, r,
                withAlpha(INK_FAINT, alpha), withAlpha(INK_FAINT, alpha / 2));
        SdfShapes.disc(c, PX + x + r * 0.45F, PY + y - r * 0.35F, r * 0.62F,
                withAlpha(INK_FAINT, alpha / 2), withAlpha(INK_FAINT, 2));
        SdfShapes.disc(c, PX + x - r * 0.4F, PY + y + r * 0.5F, r * 0.5F,
                withAlpha(INK_FAINT, alpha / 2), withAlpha(INK_FAINT, 2));
    }

    /**
     * 四角铁包角：9×9 的角块（正好停在角上，不压到快捷栏第 1/9 格）+ 沿包边走的两段铁条。
     * 9 而不是 13：快捷栏格子从 x=9 开始，包角必须让开；视觉上靠两条铁条补足"包"的感觉。
     */
    private static void corners(PreviewCanvas c) {
        cap(c, PX, PY, 1, 1);
        cap(c, PX + PANEL_W, PY, -1, 1);
        cap(c, PX, PY + PANEL_H, 1, -1);
        cap(c, PX + PANEL_W, PY + PANEL_H, -1, -1);
    }

    private static void cap(PreviewCanvas c, int cx, int cy, int sx, int sy) {
        final float plate = 9F;
        float px = sx > 0 ? cx : cx - plate;
        float py = sy > 0 ? cy : cy - plate;
        // 沿两条包边走的铁条（整段落在 6px 皮革带上，纸上一个像素都不占）
        float hx = sx > 0 ? cx + 5F : cx - 5F - 18F;
        float hy = sy > 0 ? cy + 0.4F : cy - 5.6F;
        SdfShapes.roundRect(c, hx, hy, 18F, 5.2F, 1.6F, IRON_HI, IRON, 0x8E17140F, 0.8F);
        float vx = sx > 0 ? cx + 0.4F : cx - 5.6F;
        float vy = sy > 0 ? cy + 5F : cy - 5F - 18F;
        SdfShapes.roundRect(c, vx, vy, 5.2F, 18F, 1.6F, IRON_HI, IRON, 0x8E17140F, 0.8F);

        SdfShapes.roundRect(c, px, py, plate, plate, 2F, IRON_HI, IRON_DK, 0xFF17140F, 1F);
        SdfShapes.roundRect(c, px + 2.2F, py + 2.2F, plate - 4.4F, plate - 4.4F, 1.2F,
                0x00000000, 0x00000000, 0x66D5CBBE, 0.9F);
        SdfShapes.disc(c, cx + sx * 3.2F, cy + sy * 3.2F, 1.5F, 0xFFCCC2B4, 0xFF6A6259);
        SdfShapes.disc(c, cx + sx * 12F, cy + sy * 2.6F, 1.0F, 0xB8AFA39A, 0x8E4A443D);
        SdfShapes.disc(c, cx + sx * 2.6F, cy + sy * 12F, 1.0F, 0xB8AFA39A, 0x8E4A443D);
    }

    // ══════════════════════════════════════════════════════════════
    //  7. 页面（面板局部坐标：所有坐标都经过 ox/oy 平移到画布）
    // ══════════════════════════════════════════════════════════════

    private static void page(PreviewCanvas c, float time, boolean crafting) {
        float progress = crafting ? CRAFT_PROGRESS : 0F;

        rite(c, time, crafting);       // 法阵底 + 开口双圈 + 内圈
        spokes(c, crafting);           // 已填材料 → 徽章
        slots(c, time);                // 15 个 18×18 纸面凹槽
        badge(c, time, crafting);      // 中心徽章（fire_ball）
        ledger(c, crafting);           // 右页边：15 格势能
        gauge(c, progress);            // 左页边：羽笔刻度尺
        header(c, crafting);           // 一行页眉
        button(c, crafting);           // 火漆印章按钮
        status(c, crafting);
        pack(c);                       // 背包 / 快捷栏
    }

    // ── 法阵底 / 开口双圈 / 内圈 ──

    private static void rite(PreviewCanvas c, float time, boolean crafting) {
        // 法阵范围内的纸面渍：让法阵落在一块"被墨沁过"的纸上
        SdfShapes.disc(c, ox(CX), oy(CY), 57F, 0x16A6722F, 0x08A6722F);

        // 双圈：两圈同心墨线，正上方留 44° 开口（正好让出 0 号材料位与页眉）
        double a0 = Math.toRadians(GAP_A1);
        double a1 = Math.toRadians(GAP_A0 + 360.0);
        inkArc(c, CX, CY, R_RING_OUT, 0.5F, (float) a0, (float) a1, withAlpha(INK, 200));
        inkArc(c, CX, CY, R_RING_IN, 0.45F, (float) a0, (float) a1, withAlpha(INK, 178));
        // 开口两端各点一个墨点：笔尖提起留下的收笔
        for (int s = 0; s < 2; s++) {
            double ang = Math.toRadians(s == 0 ? GAP_A1 : GAP_A0);
            for (int k = 0; k < 2; k++) {
                float rr = k == 0 ? R_RING_IN : R_RING_OUT;
                SdfShapes.disc(c, ox(CX + (float) Math.cos(ang) * rr),
                        oy(CY + (float) Math.sin(ang) * rr), 1.15F,
                        withAlpha(INK, 182), withAlpha(INK, 150));
            }
        }

        // 内层两道细圈：压在辐条上，形成同心 + 辐射的经典法阵
        SdfShapes.ring(c, ox(CX), oy(CY), R_INNER - 0.4F, R_INNER + 0.4F,
                withAlpha(INK, 132), withAlpha(INK, 108), false);
        SdfShapes.ring(c, ox(CX), oy(CY), R_BAND - 0.35F, R_BAND + 0.35F,
                withAlpha(OCHRE_DK, 104), withAlpha(OCHRE_DK, 78), false);

        // 合成中：徽章外再套一圈没画满的朱砂弧（62%），把"进行中"写进法阵
        if (crafting) {
            float b0 = (float) (-Math.PI / 2.0);
            float b1 = b0 + (float) (Math.PI * 2.0) * CRAFT_PROGRESS;
            SdfShapes.wedge(c, ox(CX), oy(CY), R_BADGE + 1.9F, R_BADGE + 2.7F, b0, b1,
                    withAlpha(VERMILION, 44), withAlpha(VERMILION_HI, 210));
            float hx = CX + (float) Math.cos(b1) * (R_BADGE + 2.3F);
            float hy = CY + (float) Math.sin(b1) * (R_BADGE + 2.3F);
            SdfShapes.disc(c, ox(hx), oy(hy), 1.8F, withAlpha(VERMILION_HI, 235),
                    withAlpha(VERMILION, 200));
        }
    }

    /**
     * 手抄本墨弧：主笔画一段，再用两段"回笔"（半径略偏、alpha 更低）补在弧上，
     * 于是线看起来是人手画的，而不是 CAD 圆弧。
     */
    private static void inkArc(PreviewCanvas c, float cx, float cy, float r, float halfW,
                               float a0, float a1, int argb) {
        SdfShapes.wedge(c, ox(cx), oy(cy), r - halfW, r + halfW, a0, a1, argb, argb);
        int ghost = withAlpha(argb, 72);
        float span = a1 - a0;
        SdfShapes.wedge(c, ox(cx), oy(cy), r + halfW + 0.25F, r + halfW + 0.95F,
                a0 + span * 0.06F, a0 + span * 0.34F, ghost, ghost);
        SdfShapes.wedge(c, ox(cx), oy(cy), r - halfW - 0.95F, r - halfW - 0.25F,
                a0 + span * 0.45F, a0 + span * 0.72F, ghost, ghost);
        SdfShapes.wedge(c, ox(cx), oy(cy), r + halfW + 0.2F, r + halfW + 0.8F,
                a0 + span * 0.80F, a0 + span * 0.96F, ghost, ghost);
    }

    /** 整圈墨线（内圈/徽章用）。 */
    private static void inkCircle(PreviewCanvas c, float cx, float cy, float r,
                                  float halfW, int argb) {
        SdfShapes.ring(c, ox(cx), oy(cy), r - halfW, r + halfW, argb, argb, false);
        int ghost = withAlpha(argb, 66);
        SdfShapes.wedge(c, ox(cx), oy(cy), r + halfW + 0.25F, r + halfW + 1.0F,
                (float) Math.toRadians(8), (float) Math.toRadians(96), ghost, ghost);
        SdfShapes.wedge(c, ox(cx), oy(cy), r - halfW - 1.0F, r - halfW - 0.25F,
                (float) Math.toRadians(126), (float) Math.toRadians(214), ghost, ghost);
        SdfShapes.wedge(c, ox(cx), oy(cy), r + halfW + 0.3F, r + halfW + 1.1F,
                (float) Math.toRadians(240), (float) Math.toRadians(308), ghost, ghost);
    }

    // ── 辐条：已填材料 → 徽章（空位用虚点，缺的那几根一眼看得见）──

    private static void spokes(PreviewCanvas c, boolean crafting) {
        for (int i = 0; i < RING_SLOTS; i++) {
            double a = Math.toRadians(-90 + i * 24);
            float ca = (float) Math.cos(a);
            float sa = (float) Math.sin(a);
            float x0 = CX + ca * R_SPOKE_IN;
            float y0 = CY + sa * R_SPOKE_IN;
            float x1 = CX + ca * R_SPOKE_OUT;
            float y1 = CY + sa * R_SPOKE_OUT;
            if (i < MAT_ICON.length) {
                SdfShapes.line(c, ox(x0), oy(y0), ox(x1), oy(y1), 0.4F,
                        withAlpha(OCHRE_DK, crafting ? 190 : 168));
                SdfShapes.disc(c, ox(x0), oy(y0), 1.05F,
                        withAlpha(INK_MID, 160), withAlpha(INK_MID, 160));
                SdfShapes.disc(c, ox(x1), oy(y1), 0.95F,
                        withAlpha(OCHRE_DK, 170), withAlpha(OCHRE_DK, 130));
            } else {
                for (int k = 0; k < 4; k++) {
                    float t0 = 0.12F + k * 0.22F;
                    float t1 = t0 + 0.10F;
                    SdfShapes.line(c,
                            ox(x0 + (x1 - x0) * t0), oy(y0 + (y1 - y0) * t0),
                            ox(x0 + (x1 - x0) * t1), oy(y0 + (y1 - y0) * t1),
                            0.32F, withAlpha(INK_FAINT, 84));
                }
            }
        }
    }

    // ── 15 个材料位：18×18 纸面凹槽（空的一眼看得见是"槽"）──

    private static void slots(PreviewCanvas c, float time) {
        for (int i = 0; i < RING_SLOTS; i++) {
            int[] box = slotBox(i);
            slotWell(c, box[0], box[1]);
            if (i < MAT_ICON.length) {
                c.blit(c.texture("textures/item/" + MAT_ICON[i] + ".png"),
                        PX + box[0] + 1, PY + box[1] + 1, 16, 16);
            } else {
                // 空槽：中心一个小墨点（看得出是"等着放东西的凹槽"）
                SdfShapes.disc(c, ox(box[0] + 9F), oy(box[1] + 9F), 1.3F,
                        withAlpha(INK_FAINT, 150), withAlpha(INK_FAINT, 110));
            }
        }
    }

    /**
     * 一枚 18×18 的纸面凹槽：16×16 的孔用 SDF 画成圆角压印（1px 墨线 + 内侧上/左阴影 +
     * 下沿高光），四角留 1px 微缝，标出 18×18 的命中区又不把纸面切成"表单格"。
     */
    private static void slotWell(PreviewCanvas c, int x, int y) {
        float vx = x + 1F;
        float vy = y + 1F;
        SdfShapes.roundRect(c, ox(vx), oy(vy), 16F, 16F, 1.8F,
                0x2A6E4A20, 0x1A553714, withAlpha(INK, 128), 1F);
        c.fill(PX + x + 2, PY + y + 1, 14, 1, withAlpha(INK, 96));
        c.fill(PX + x + 1, PY + y + 2, 1, 14, withAlpha(INK, 78));
        c.fill(PX + x + 2, PY + y + 16, 14, 1, withAlpha(0xFFF7ECCF, 62));
        c.fill(PX + x + 16, PY + y + 2, 1, 14, withAlpha(0xFFF7ECCF, 50));

        int tick = withAlpha(INK, 96);
        c.fill(PX + x, PY + y, 1, 1, tick);
        c.fill(PX + x + SLOT - 1, PY + y, 1, 1, tick);
        c.fill(PX + x, PY + y + SLOT - 1, 1, 1, tick);
        c.fill(PX + x + SLOT - 1, PY + y + SLOT - 1, 1, 1, tick);
    }

    /** 与 {@code PotentialCraftingMenu.ringAngleDegrees} 完全一致的槽位（含 floor 取整）。 */
    private static int[] slotBox(int index) {
        double angle = Math.toRadians(-90 + index * 24);
        int x = CX + (int) Math.floor(Math.cos(angle) * RING_RADIUS) - SLOT / 2;
        int y = CY + (int) Math.floor(Math.sin(angle) * RING_RADIUS) - SLOT / 2;
        return new int[]{x, y};
    }

    // ── 中心徽章 ──

    private static void badge(PreviewCanvas c, float time, boolean crafting) {
        // 徽章：双墨圈 + 铸齿边
        SdfShapes.disc(c, ox(BX), oy(BY), R_BADGE + 2.4F, 0x16A2331A, 0x08A2331A);
        inkCircle(c, BX, BY, R_BADGE, 0.5F, withAlpha(INK, 212));

        // 产物图标底托：更深的一层纸面压印 + 双墨圈 + 4 个方位刻痕
        // （和环上 15 个凹槽同一套语言：图标不直接躺在纸面上）
        SdfShapes.disc(c, ox(BX), oy(BY), 13.4F, 0x44513012, 0x2E3A2209);
        SdfShapes.ring(c, ox(BX), oy(BY), 12.6F, 13.4F,
                withAlpha(INK, 176), withAlpha(INK, 132), false);
        SdfShapes.ring(c, ox(BX), oy(BY), R_SEAL - 0.4F, R_SEAL + 0.4F,
                withAlpha(INK, 150), withAlpha(INK, 116), false);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(-90 + 90 * i);
            float c0 = (float) Math.cos(a);
            float s0 = (float) Math.sin(a);
            SdfShapes.line(c,
                    ox(BX + c0 * 11.0F), oy(BY + s0 * 11.0F),
                    ox(BX + c0 * 16.3F), oy(BY + s0 * 16.3F),
                    0.38F, withAlpha(INK, 180));
        }

        // 铸齿边：32 道极短刻痕，把徽章外沿做成印章的滚花边
        for (int i = 0; i < 32; i++) {
            double a = Math.toRadians(i * 360.0 / 32.0);
            float ca = (float) Math.cos(a);
            float sa = (float) Math.sin(a);
            SdfShapes.line(c,
                    ox(BX + ca * (R_BADGE - 2.1F)), oy(BY + sa * (R_BADGE - 2.1F)),
                    ox(BX + ca * (R_BADGE - 0.4F)), oy(BY + sa * (R_BADGE - 0.4F)),
                    0.35F, withAlpha(INK, (i & 1) == 0 ? 175 : 112));
        }

        // 4 枚手绘三角符号（贴在 R_SEAL 与 R_BADGE 之间的环带里）
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(-45 + i * 90);
            float ca = (float) Math.cos(a);
            float sa = (float) Math.sin(a);
            float tx = -sa;
            float ty = ca;
            float apexX = BX + ca * 16.4F;
            float apexY = BY + sa * 16.4F;
            float b1x = BX + ca * 21.2F + tx * 2.8F;
            float b1y = BY + sa * 21.2F + ty * 2.8F;
            float b2x = BX + ca * 21.2F - tx * 2.8F;
            float b2y = BY + sa * 21.2F - ty * 2.8F;
            int col = withAlpha(INK, crafting ? 190 : 160);
            SdfShapes.line(c, ox(apexX), oy(apexY), ox(b1x), oy(b1y), 0.45F, col);
            SdfShapes.line(c, ox(apexX), oy(apexY), ox(b2x), oy(b2y), 0.45F, col);
        }

        // 产物图标：16×16 原尺寸，整数坐标（SPEC：绝对不拉伸）
        c.blit(c.texture("textures/item/" + OUT_ICON + ".png"),
                PX + OUT_X + 1, PY + OUT_Y + 1, 16, 16);
    }

    // ── 右页边：15 格势能账簿（垂直居中于法阵）──

    private static void ledger(PreviewCanvas c, boolean crafting) {
        final int cellH = 5;
        final int gap = 2;

        // 账列上下两道横线（账簿的栏头 / 合计线）
        c.fill(PX + LEDGER_X - 2, PY + LEDGER_TOP - 2, LEDGER_W + 4, 1, withAlpha(INK_FAINT, 86));
        int bottom = LEDGER_TOP + POTENTIAL_MAX * (cellH + gap) - gap;
        c.fill(PX + LEDGER_X - 2, PY + bottom + 1, LEDGER_W + 4, 1, withAlpha(INK_FAINT, 96));

        for (int i = 0; i < POTENTIAL_MAX; i++) {
            int y = LEDGER_TOP + i * (cellH + gap);
            if (i < POTENTIAL) {
                // 亮格 = 按下的朱砂印：朱砂渐变 + 纸色边 + 一道高光
                SdfShapes.roundRect(c, PX + LEDGER_X, PY + y, LEDGER_W, cellH, 1F,
                        0xFFB2442A, 0xFF8E2A14, withAlpha(PAPER_SEAL, 190), 0.75F);
                c.fill(PX + LEDGER_X + 2, PY + y + 1, LEDGER_W - 4, 1,
                        withAlpha(0xFFEBA07C, 120));
            } else {
                // 空格 = 压暗的凹印（不是描边，而是"按灭了"的灰墨）
                SdfShapes.roundRect(c, PX + LEDGER_X, PY + y, LEDGER_W, cellH, 1F,
                        0x2A462C16, 0x1C2A1C0D, withAlpha(INK_MID, 108), 0.75F);
                c.fill(PX + LEDGER_X + 2, PY + y + 1, LEDGER_W - 4, 1, withAlpha(0x12080502, 150));
            }
        }
    }

    // ── 左页边：羽笔刻度尺（竖向墨槽）──

    private static void gauge(PreviewCanvas c, float progress) {
        // 槽身：墨线框 + 内凹的暗纸
        SdfShapes.roundRect(c, PX + GAUGE_X, PY + GAUGE_Y, GAUGE_W, GAUGE_H, 2.5F,
                withAlpha(0xFFE8DCBA, 240), withAlpha(0xFFD4BD94, 240), withAlpha(INK, 200), 1F);
        SdfShapes.roundRect(c, PX + GAUGE_X + 2.5F, PY + GAUGE_Y + 2.5F,
                GAUGE_W - 5F, GAUGE_H - 5F, 1.2F,
                0x2A3A240E, 0x14301E0C, withAlpha(INK, 120), 0.8F);

        int innerX = GAUGE_X + 3;
        int innerY = GAUGE_Y + 3;
        int innerW = GAUGE_W - 6;
        int innerH = GAUGE_H - 6;

        // 槽底残墨：即便 0% 也留一层干涸的朱砂，读得出"这是液位刻度柱"
        SdfShapes.roundRect(c, PX + innerX, PY + innerY + innerH - 4F, innerW, 4, 1F,
                withAlpha(VERMILION_DK, 130), withAlpha(VERMILION_DK, 210), 0, 0F);

        // 墨液：自下而上染（下朱砂 → 上赭金），液面带一道亮线
        int fillH = Math.round(innerH * clamp01(progress));
        if (fillH > 0) {
            int fy = innerY + innerH - fillH;
            SdfShapes.roundRect(c, PX + innerX, PY + fy, innerW, fillH, 1F,
                    withAlpha(OCHRE_HI, 242), withAlpha(VERMILION, 242), 0, 0F);
            c.fill(PX + innerX + 1, PY + fy, innerW - 2, 1, withAlpha(0xFFFFD79A, 235));
            c.fill(PX + innerX, PY + fy + 1, innerW, 1, withAlpha(VERMILION_DK, 80));
        }

        // 刻度：每 8px 一格，每 3 格加长；刻痕在墨槽左侧
        for (int i = 0; i <= 12; i++) {
            int y = innerY + Math.round(innerH * (1F - i / 12F));
            boolean major = i % 3 == 0;
            int len = major ? 5 : 3;
            c.fill(PX + GAUGE_X - 1 - len, PY + Math.min(y, GAUGE_Y + GAUGE_H - 1), len, 1,
                    withAlpha(INK_FAINT, major ? 160 : 96));
        }

        // 羽笔尖：实心墨笔尖（收在柱口上）+ 中间开缝 + 呼吸孔 + 柱口横箍 —— 和柱体连住
        float nibX = GAUGE_X + GAUGE_W * 0.5F;
        float tipY = GAUGE_Y - 9.5F;
        float fer = GAUGE_Y + 0.5F;
        float[] xs = {PX + nibX, PX + GAUGE_X + 0.6F, PX + GAUGE_X + GAUGE_W - 0.6F};
        float[] ys = {PY + tipY, PY + fer, PY + fer};
        WheelKit.fillPolygon(c, xs, ys, 3, withAlpha(INK, 205));
        int slitX = PX + Math.round(nibX);
        int slitY = PY + Math.round(tipY) + 3;
        c.fill(slitX, slitY, 1, Math.max(1, Math.round(fer) - Math.round(tipY) - 4),
                withAlpha(VELLUM_HI, 215));
        SdfShapes.disc(c, ox(nibX), oy(tipY + 2.6F), 1.0F,
                withAlpha(VELLUM_HI, 225), withAlpha(VELLUM_HI, 170));
        // 柱口横箍：笔尖与墨槽之间的一圈铁箍
        SdfShapes.line(c, ox(GAUGE_X - 0.8F), oy(fer), ox(GAUGE_X + GAUGE_W + 0.8F), oy(fer),
                0.45F, withAlpha(INK, 226));

        // 读数：柱脚下方一个百分比（就绪态 0），落在页边、不占正文
        String pct = Math.round(clamp01(progress) * 100F) + "%";
        c.textCentered(pct, PX + GAUGE_X + GAUGE_W / 2, PY + GAUGE_Y + GAUGE_H + 4,
                progress > 0.001F ? withAlpha(VERMILION, 244) : withAlpha(INK_FAINT, 220), false);
    }

    // ── 页眉：一整行，左标题 / 右读数（y=13，两头顶到纸边留 8px 空气）──

    private static void header(PreviewCanvas c, boolean crafting) {
        final int y = 13;
        final int left = 10;
        final int right = PAPER_R - 3;      // 167：离右内缝 3px，给标题留出分栏的空气

        c.text(TITLE_LINE, PX + left, PY + y, withAlpha(INK, 236), false);

        String num = POTENTIAL + "/" + POTENTIAL_MAX;
        int numW = c.textWidth(num);
        int numX = right - numW;
        c.text(num, PX + numX, PY + y, crafting ? VERMILION_HI : withAlpha(VERMILION, 244), false);
        int labelX = numX - 5;
        c.textRight("POTENTIAL", PX + labelX, PY + y, withAlpha(INK_FAINT, 240), false);

        // 竖分隔：把标题和读数分开（一行的页眉也要有"分栏"）
        int divX = PX + labelX - c.textWidth("POTENTIAL") - 3;
        c.fill(divX, PY + y - 1, 1, 9, withAlpha(INK_FAINT, 96));
    }

    // ── 火漆印章按钮 ──

    private static void button(PreviewCanvas c, boolean crafting) {
        boolean on = !crafting;
        // 印章压在纸上：先一道很轻的落影，再是蜡体
        SdfShapes.roundRect(c, PX + BTN_X + 0.5F, PY + BTN_Y + 1.9F, BTN_W, BTN_H - 0.5F, 4F,
                0x40180A03, 0x2A120702, 0, 0F);

        if (on) {
            SdfShapes.roundRect(c, PX + BTN_X, PY + BTN_Y + 0.5F, BTN_W, BTN_H - 1.5F, 4F,
                    WAX_HI, WAX_LO, WAX_EDGE, 1F);
            // 蜡体的外缘暗边 + 印章压出的内圈（凹进去的体积）
            SdfShapes.roundRect(c, PX + BTN_X + 1.5F, PY + BTN_Y + 2F, BTN_W - 3F, BTN_H - 4.5F,
                    3F, 0x00000000, 0x00000000, withAlpha(WAX_EDGE, 120), 0.8F);
            SdfShapes.roundRect(c, PX + BTN_X + 4F, PY + BTN_Y + 4F, BTN_W - 8F, BTN_H - 8.6F,
                    2.2F, 0x00000000, 0x00000000, withAlpha(0xFFE98A66, 170), 0.9F);
            c.fill(PX + BTN_X + 3, PY + BTN_Y + 2, BTN_W - 6, 1, withAlpha(0xFFDA7550, 110));
            c.fill(PX + BTN_X + 3, PY + BTN_Y + BTN_H - 3, BTN_W - 6, 1,
                    withAlpha(WAX_EDGE, 170));
            // 纸色字 + 1px 暗影 = 压进蜡里的凸字
            c.textCentered(BTN_READY, PX + BTN_X + BTN_W / 2, PY + BTN_Y + 5, PAPER_SEAL, true);
        } else {
            // 禁用：蜡色褪成灰墨，内圈变成虚点（"章没盖下去"）
            SdfShapes.roundRect(c, PX + BTN_X, PY + BTN_Y + 0.5F, BTN_W, BTN_H - 1.5F, 4F,
                    ASH_HI, ASH_LO, ASH_EDGE, 1F);
            for (int i = 0; i < 14; i++) {
                float a0 = (float) (Math.PI * 2.0 * i / 14.0);
                float a1 = a0 + 0.20F;
                SdfShapes.wedge(c, PX + BTN_X + BTN_W * 0.5F, PY + BTN_Y + BTN_H * 0.5F,
                        5.2F, 6.0F, a0, a1,
                        withAlpha(0xFFD6CCBA, 100), withAlpha(0xFFD6CCBA, 70));
            }
            c.fill(PX + BTN_X + 2, PY + BTN_Y + 2, BTN_W - 4, 1, withAlpha(0xFF9E9484, 80));
            c.textCentered(BTN_BUSY, PX + BTN_X + BTN_W / 2, PY + BTN_Y + 5, 0xFFD8CEBC, false);
        }
    }

    // ── 状态行：面板中心 (88,166) ──

    private static void status(PreviewCanvas c, boolean crafting) {
        String text = crafting ? STATUS_CRAFTING : STATUS_READY;
        int col = crafting ? withAlpha(VERMILION, 246) : withAlpha(INK_MID, 246);
        int cx = (PAPER_X + PAPER_R) / 2;      // 88 = 页面中线
        c.textCentered(text, PX + cx, PY + 166, col, false);
        if (crafting) {
            // 行首一枚小朱砂点，像刚按下的指印
            SdfShapes.disc(c, ox(cx - c.textWidth(text) / 2.0F - 5F), oy(169.5F), 1.6F,
                    withAlpha(VERMILION_HI, 200), withAlpha(VERMILION, 160));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  8. 背包 / 快捷栏（纸面凹槽格；快捷栏最后一格到 y=254）
    // ══════════════════════════════════════════════════════════════

    private static void pack(PreviewCanvas c) {
        // 口袋区：先垫一层极淡的暗 + 一圈墨线，把 4 行格子收成"一块"，不像散落的表单
        SdfShapes.roundRect(c, PX + 6.5F, PY + 179.5F, 163F, 74F, 2F,
                0x0C3A240E, 0x0C3A240E, withAlpha(INK, 66), 1F);

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                well(c, INV_X + col * INV_STRIDE, INV_Y + row * INV_STRIDE, false);
            }
        }
        // 快捷栏与背包之间的 1px 分隔（快捷栏不属于这台机器），并用更浅的槽区分
        c.fill(PX + INV_X + 1, PY + HOTBAR_Y - 1, INV_COLS * INV_STRIDE - 2, 1,
                withAlpha(INK, 112));

        for (int col = 0; col < INV_COLS; col++) {
            well(c, INV_X + col * INV_STRIDE, HOTBAR_Y, true);
        }

        // 占位物品：背包 4 格 + 快捷栏 2 格，让画面不空
        item(c, INV_X, INV_Y, "mana_bottle");
        item(c, INV_X + INV_STRIDE, INV_Y, "domite_crystal");
        item(c, INV_X + INV_STRIDE * 5, INV_Y, "void_shard");
        item(c, INV_X + INV_STRIDE * 2, INV_Y + INV_STRIDE * 2, "true_copper");
        item(c, INV_X, HOTBAR_Y, "fire_ball");
        item(c, INV_X + INV_STRIDE * 7, HOTBAR_Y, "mana_bottle");
    }

    /** 一个纸面凹槽：16×16 的孔（SDF 圆角压印）+ 内侧上/左阴影、下沿高光。 */
    private static void well(PreviewCanvas c, int x, int y, boolean hotbar) {
        int top = hotbar ? 0x265E4422 : 0x4A5E3D18;
        int bottom = hotbar ? 0x183A2810 : 0x3840280F;
        SdfShapes.roundRect(c, ox(x + 1F), oy(y + 1F), 16F, 16F, 1.4F,
                top, bottom, withAlpha(INK, hotbar ? 116 : 140), 1F);
        c.fill(PX + x + 2, PY + y + 1, 14, 1, withAlpha(INK, 92));
        c.fill(PX + x + 1, PY + y + 2, 1, 14, withAlpha(INK, 74));
        c.fill(PX + x + 2, PY + y + 16, 14, 1, withAlpha(0xFFF7ECCF, 58));
        c.fill(PX + x + 16, PY + y + 2, 1, 14, withAlpha(0xFFF7ECCF, 44));
    }

    private static void item(PreviewCanvas c, int cellX, int cellY, String icon) {
        c.blit(c.texture("textures/item/" + icon + ".png"),
                PX + cellX + 1, PY + cellY + 1, 16, 16);
    }

    // ══════════════════════════════════════════════════════════════
    //  9. 小工具
    // ══════════════════════════════════════════════════════════════

    /** 面板局部 → 画布坐标（SDF 用 float 版）。 */
    private static float ox(float x) {
        return PX + x;
    }

    private static float oy(float y) {
        return PY + y;
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    private static float clamp01(float v) {
        return Math.max(0F, Math.min(1F, v));
    }

    /** 确定性 hash（0..1）：纸纤维/木纹的位置每次出图都一样，方便反复比对。 */
    private static float hash(int seed) {
        int h = seed * 0x27D4EB2D + 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        return (h & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }
}
