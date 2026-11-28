import org.lwjgl.opengl.GL11;

/**
 * 法术合成界面（奥秘仪台）——「棱镜晶格 / 玻璃全息」风格设计稿（Prism Lattice）。
 *
 * <h3>和实机的关系</h3>
 * <p>被重绘的是 {@code client/gui/PotentialCraftingScreen.java}：现在它是一块灰蓝铁皮、
 * 圆环槽是 18×18 空框、进度与势能条挂在面板外面，三块硬拼在一起。本稿只换视觉语言，
 * <b>命中框一个都不动</b>：面板 176×256、圆环中心 (96,84) r=48、15 个 18×18 材料槽、
 * 圆心产物槽 (88,76)、按钮 (58,146,60,18)、背包 (8,182) 与快捷栏 (8,236)。
 * 三个风格稿必须能叠在一起比对，所以这些数字全部以<b>面板局部坐标</b>写成常量
 * （{@code *_X/_Y}），画的时候统一加 {@link #PX}/{@link #PY} 偏移；
 * 只有同心装饰用的 {@link #MCX}/{@link #MCY} 是已经加过偏移的画布坐标。</p>
 *
 * <h3>为什么这样排（分区）</h3>
 * <ul>
 *   <li><b>头</b>（只有局部 y 7..15 一行）：左 {@code ARCANE ALTAR}（1x，冷白）+ 右势能读数
 *       {@code POTENTIAL 9/15}；读数与它正下方那 15 枚水晶碎片同一条竖轴（右边界 168）。
 *       页眉不叠第二行、不压表盘——晶盘顶边在 y≈27，中间留白。
 *       标题走 1x 而不是 2x：表盘圆心 y=84 / r=48，页眉可用高度只有 ~16px，
 *       2x 主标题（16px 高 + 阴影）会直接压到 12 点方向的晶格槽。</li>
 *   <li><b>机</b>（局部 y 24..144）：一块 16 边形晶盘托住 15 个六边形晶格槽；圆心是棱镜核 +
 *       产物图标。已填材料到核心拉极细 {@code magic_pulse} 能量带（带跑动光点），
 *       空槽是暗玻璃 + 一点内反光 —— 空/满一眼分得出来，不用写占位字。</li>
 *   <li><b>左</b>：竖向液体光柱（合成进度，柱底留一小段余液让 0% 也有液面）+ 百分比读数；
 *       <b>右</b>：15 枚竖排水晶碎片（势能）。实机把这两条挂在面板外，是"三块硬拼"的来源，
 *       这里收进面板两侧的留白（局部 x 4..34 / x 157..168），机器仍然是唯一焦点。
 *       两列都不再写字标：页眉只有一行，标签放哪都会挤，柱子本身（液体/水晶）已经够说明。</li>
 *   <li><b>操作</b>：按钮原位置 (58,146,60,18)；状态行在按钮正下方<b>居中</b>
 *       （(88,166)：就绪 READY TO CRAFT / 合成中 CRAFTING... 4 s LEFT，两头各一颗 5px 小菱形点，
 *       不放竖线 —— 竖线会读成括号）。
 *       实机那一行在 y=168 且和 y=172 的背包标题压字，所以干脆删掉 {@code INVENTORY} 字标
 *       （它比背包格还宽），只留 y=176 的分隔线当分节。</li>
 *   <li><b>背包</b>：深玻璃格 + 冷白内边 + 内凹阴影；快捷栏 (y=236, 高 18) 比主背包亮一档，
 *       底边到 253，给面板描边留 2px。</li>
 * </ul>
 *
 * <h3>材质与光</h3>
 * <p>面板是深靛蓝玻璃（alpha 0xEE..0xF2：透一点点星场，但结构线够亮），四角切斜边；
 * 边缘做<b>色散</b>：沿边错位画品红 / 青两圈 1px 低 alpha 线，主边压在最上面。
 * 颜色不用纯黑纯白，全部带色相（薄荷 / 青 / 品红 / 靛）。</p>
 *
 * <h3>像素与平滑</h3>
 * <p>1px 结构线（边框、分隔、槽边、刻度）走 {@code c.fill} 保持硬边；一切圆/环/扇/胶囊/
 * 斜切角走 {@link SdfShapes}（逐物理像素覆盖率）；六边形槽用 {@code fillPolygon} 填 +
 * SDF 描边盖住阶梯边；星场与能量带走项目真实 {@code magic_starfield} /
 * {@code magic_pulse}。图标 16×16 一律落整数坐标，发光只叠在图标底下，不糊图标。</p>
 *
 * <p>导出：{@code spell_altar_prism_ready.png} / {@code spell_altar_prism_crafting.png}
 * （62% 合成中），默认导出（{@code spell_altar_prism.png}）= 就绪态。画面内文字只用英文。</p>
 */
public final class SceneSpellAltarPrism {

    // ══════════════════════════════════════════════════════════════
    //  画布 / 面板（SPEC.md §0：240×320 逻辑画布，面板贴 (32,32)）
    // ══════════════════════════════════════════════════════════════

    private static final int PX = 32;
    private static final int PY = 32;
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 256;
    /** 四角切斜边（倒角）的边长。 */
    private static final int CHAMFER = 7;

    // ══════════════════════════════════════════════════════════════
    //  不可改几何（SPEC.md §1：全部来自 Menu / 命中框，面板局部坐标）
    // ══════════════════════════════════════════════════════════════

    private static final int RING_X = 96;          // RING_CENTER_X
    private static final int RING_Y = 84;          // RING_CENTER_Y
    private static final int RING_R = 48;          // RING_RADIUS
    private static final int SLOT = 18;            // 材料槽边长
    private static final int SLOT_COUNT = 15;
    private static final int OUT_X = 88;           // 产物槽左上
    private static final int OUT_Y = 76;
    private static final int BTN_X = 58;
    private static final int BTN_Y = 146;
    private static final int BTN_W = 60;
    private static final int BTN_H = 18;
    private static final int INV_X = 8;
    private static final int INV_Y = 182;
    private static final int INV_COLS = 9;
    private static final int INV_ROWS = 3;
    private static final int PITCH = 18;
    private static final int HOT_Y = 236;
    /** 状态行（按钮正下方居中；实机 y=168 会和背包标题压字，这里独占一行）。 */
    private static final int STATUS_Y = 166;
    /** 分隔线；背包标题压在线上（基线仍是实机的 172）。 */
    private static final int INV_RULE_Y = 176;

    // ── 装饰用的同心中心：取产物槽中心（比 SPEC 的 (96,84) 偏 +1），这样六边形晶格
    //    （按整数命中框定位）才正好骑在同心圆上。已是画布坐标。
    private static final float MCX = PX + OUT_X + 9F;   // 129
    private static final float MCY = PY + OUT_Y + 9F;   // 117

    /** 六边形晶格槽的外接半径：切向宽 ~18.7、径向厚 ~16.2，相邻槽切向刚好相碰。 */
    private static final float HEX_R = 10.8F;
    /** 机座晶盘（16 边形）外接半径。 */
    private static final float PLATE_R = 60.5F;
    /** 晶格带的内 / 外压边半径。 */
    private static final float BAND_IN = 38.2F;
    private static final float BAND_OUT = 58.4F;

    // 左：进度光柱（面板局部）
    private static final int TUBE_X = 12;
    private static final int TUBE_Y = 44;
    private static final int TUBE_W = 13;
    private static final int TUBE_H = 98;

    // 右：势能水晶碎片列（面板局部）
    private static final int SHARD_X = 157;
    private static final int SHARD_W = 11;
    private static final int SHARD_H = 6;
    private static final int SHARD_PITCH = 9;
    private static final int SHARD_TOP = 26;
    private static final int SHARD_COUNT = 15;
    private static final int SHARD_LIT = 9;

    /** 合成中的进度（SPEC.md §2：62%）。 */
    private static final float CRAFT_PROGRESS = 0.62F;

    // ══════════════════════════════════════════════════════════════
    //  配色：深靛蓝玻璃 + 薄荷/青 + 色散的两条边（品红、青）
    // ══════════════════════════════════════════════════════════════

    private static final int C_INK = 0xFFE9F5FF;        // 冷白（带青）
    private static final int C_INK_DIM = 0xFF93AFC8;
    private static final int C_INK_FAINT = 0xFF5C7694;

    private static final int C_MINT = 0xFF8CF2D8;
    private static final int C_MINT_HI = 0xFFDCFFF5;
    private static final int C_CYAN = 0xFF6FD3FF;
    private static final int C_CYAN_LO = 0xFF3F7FAB;
    private static final int C_MAGENTA = 0xFFFF86D6;
    private static final int C_VIOLET = 0xFFA995FF;

    private static final int C_GLASS_TOP = 0xEE0C1226;  // 面板顶（alpha ≥ 0xE0）
    private static final int C_GLASS_BOT = 0xF2070A18;  // 面板底
    private static final int C_PRISM = 0xF5091020;      // 晶体底面
    private static final int C_CELL = 0xE60E1B31;       // 已填槽底
    private static final int C_CELL_EMPTY = 0xD2050914; // 空槽底
    private static final int C_CELL_EDGE = 0xFF33506E;  // 空槽边

    private static final int BG = 0xFF05030E;

    private static final float TAU = (float) (Math.PI * 2.0);

    // ══════════════════════════════════════════════════════════════
    //  内容（SPEC.md §2：材料从 0 号槽顺时针填 11 个）
    // ══════════════════════════════════════════════════════════════

    private static final String[] MAT_ICON = {
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
    private static final int MAT_FILLED = MAT_ICON.length;

    /** 背包占位物：col, row, 贴图（3~4 格，让下半屏不空）。 */
    private static final Object[][] INV_ITEMS = {
            {0, 0, "textures/item/mana_bottle.png"},
            {1, 0, "textures/item/domite_crystal.png"},
            {2, 0, "textures/item/void_shard.png"},
            {4, 1, "textures/item/chip_null.png"},
            {7, 0, "textures/item/true_copper.png"}
    };
    private static final Object[][] HOT_ITEMS = {
            {0, "textures/item/chip_scan.png"},
            {2, "textures/item/source_charge_sand.png"}
    };

    /** 15 个材料槽的整数命中框（和实机 Slot.x/y 同一算式：中心 + floor(cos·r) − 8）。 */
    private static final int[][] SLOT_BOX = new int[SLOT_COUNT][2];

    static {
        for (int i = 0; i < SLOT_COUNT; i++) {
            double a = Math.toRadians(-90.0 + i * 24.0);
            SLOT_BOX[i][0] = RING_X + floor(Math.cos(a) * RING_R) - SLOT / 2;
            SLOT_BOX[i][1] = RING_Y + floor(Math.sin(a) * RING_R) - SLOT / 2;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  入口
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        shot(c, time, false);
        c.save("spell_altar_prism_ready");
        shot(c, time, true);
        c.save("spell_altar_prism_crafting");
        // 默认导出（GuiPreview 会在 render 返回后再存一张 <scene>.png）
        shot(c, time, false);
    }

    private static void shot(PreviewCanvas c, float time, boolean crafting) {
        background(c, time);
        panel(c);
        machine(c, time, crafting);
        tube(c, time, crafting ? CRAFT_PROGRESS : 0F);
        shards(c, time, crafting);
        header(c);
        button(c, crafting);
        footer(c, time, crafting);
        inventoryLayer(c);
    }

    // ══════════════════════════════════════════════════════════════
    //  背景：项目真实星场 + 蓝紫染色 + 四边暗角 + 几颗闪星
    // ══════════════════════════════════════════════════════════════

    private static void background(PreviewCanvas c, float time) {
        c.clear(BG);

        PreviewShader sf = c.shader("magic_starfield");
        sf.set("uTime", time);
        sf.set("uSize", (float) c.width, (float) c.height);
        sf.set("uMouse", 0F, 0F);
        sf.set("uIntensity", 0.85F);
        c.shaderQuad(sf, 0, 0, c.width, c.height);

        // 蓝紫染色：上紫下靛，把星场压成"全息底片"而不是黑色太空
        c.fillGradientV(0, 0, c.width, c.height, 0x38151A4E, 0x4005071A);

        // 四边暗角
        c.fillGradientV(0, 0, c.width, 96, 0xD0040412, 0x00040412);
        c.fillGradientV(0, c.height - 112, c.width, 112, 0x00040412, 0xE8040412);
        int band = 84;
        for (int i = 0; i < band; i++) {
            float t = 1F - i / (float) band;
            int a = Math.round(110F * t * t);
            if (a <= 0) {
                continue;
            }
            int argb = (a << 24) | 0x060818;
            c.fill(i, 0, 1, c.height, argb);
            c.fill(c.width - 1 - i, 0, 1, c.height, argb);
        }

        // 固定散星（位置写死保证三帧一致；只做亮度呼吸）。全部落在面板外的留白里。
        int[][] stars = {
                {16, 74}, {22, 176}, {12, 252}, {26, 296},
                {224, 98}, {230, 208}, {214, 302}, {222, 44},
                {96, 13}, {168, 17}, {62, 306}, {150, 308},
                {18, 128}, {226, 158}
        };
        for (int i = 0; i < stars.length; i++) {
            float twinkle = 0.5F + 0.5F * (float) Math.sin(time * 1.35F + i * 1.9F);
            int a = Math.round(70F * twinkle);
            c.fill(stars[i][0], stars[i][1], 1, 1, (a << 24) | 0xD8ECFF);
            if (i % 3 == 0) {
                int b = Math.round(30F * twinkle);
                c.fill(stars[i][0] - 1, stars[i][1], 3, 1, (b << 24) | 0xCFE8FF);
                c.fill(stars[i][0], stars[i][1] - 1, 1, 3, (b << 24) | 0xCFE8FF);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  面板：切角玻璃板 + 斜向高光 + 色散描边
    // ══════════════════════════════════════════════════════════════

    private static void panel(PreviewCanvas c) {
        // 外发光：向外三圈切角描边，越外越淡（全息板的光溢出）
        for (int k = 3; k >= 1; k--) {
            chamferOutline(c, PX - k, PY - k, PANEL_W + 2 * k, PANEL_H + 2 * k, CHAMFER + k,
                    A(C_MINT, 8 + (3 - k) * 7));
        }

        // 主体：逐行填（一次拿到"竖向渐变 + 四角切斜边"），半透明玻璃
        for (int row = 0; row < PANEL_H; row++) {
            int inset = chamferInset(row, PANEL_H, CHAMFER);
            c.fill(PX + inset, PY + row, PANEL_W - inset * 2, 1,
                    mix(C_GLASS_TOP, C_GLASS_BOT, row / (float) (PANEL_H - 1)));
        }

        // 板内斜向高光：两道极淡的玻璃反光（只铺在空玻璃上，内容会盖住它）
        c.pushClip(PX, PY, PANEL_W, PANEL_H);
        SdfShapes.line(c, PX + 8, PY + 224, PX + 50, PY + 34, 11F, A(C_INK, 10));
        SdfShapes.line(c, PX + 22, PY + 236, PX + 70, PY + 26, 2.2F, A(C_INK, 16));
        SdfShapes.line(c, PX + 120, PY + 250, PX + 170, PY + 116, 8F, A(C_MINT, 7));
        c.popClip();

        // 内衬细线（切角内 3px）：玻璃的"厚度"
        chamferOutline(c, PX + 3, PY + 3, PANEL_W - 6, PANEL_H - 6, CHAMFER - 2, A(C_INK, 20));

        // 边缘色散：品红在外、青在内，主边最后压上
        chamferOutline(c, PX - 1, PY - 1, PANEL_W + 2, PANEL_H + 2, CHAMFER + 1, A(C_MAGENTA, 64));
        chamferOutline(c, PX + 1, PY + 1, PANEL_W - 2, PANEL_H - 2, CHAMFER - 1, A(C_CYAN, 88));
        chamferOutline(c, PX, PY, PANEL_W, PANEL_H, CHAMFER, A(C_INK, 150));

        // 上沿再亮一档：光从上方来，玻璃顶边最亮
        c.fill(PX + CHAMFER, PY, PANEL_W - CHAMFER * 2, 1, A(C_MINT_HI, 190));
        c.fill(PX, PY + CHAMFER, 1, PANEL_H - CHAMFER * 2, A(C_MINT_HI, 60));
    }

    /** 切角矩形描边：直边用 c.fill（硬边），四个斜切角用 SDF 线（任意倍率都干净）。 */
    private static void chamferOutline(PreviewCanvas c, int x, int y, int w, int h, int ch,
                                       int argb) {
        c.fill(x + ch, y, w - ch * 2, 1, argb);
        c.fill(x + ch, y + h - 1, w - ch * 2, 1, argb);
        c.fill(x, y + ch, 1, h - ch * 2, argb);
        c.fill(x + w - 1, y + ch, 1, h - ch * 2, argb);
        SdfShapes.line(c, x + ch + 0.5F, y + 0.5F, x + 0.5F, y + ch + 0.5F, 0.5F, argb);
        SdfShapes.line(c, x + w - ch - 0.5F, y + 0.5F, x + w - 0.5F, y + ch + 0.5F, 0.5F, argb);
        SdfShapes.line(c, x + ch + 0.5F, y + h - 0.5F, x + 0.5F, y + h - ch - 0.5F, 0.5F, argb);
        SdfShapes.line(c, x + w - ch - 0.5F, y + h - 0.5F, x + w - 0.5F, y + h - ch - 0.5F, 0.5F,
                argb);
    }

    /** 第 row 行在切角矩形里的左右收边。 */
    private static int chamferInset(int row, int h, int ch) {
        if (row < ch) {
            return ch - row;
        }
        if (row >= h - ch) {
            return ch - (h - 1 - row);
        }
        return 0;
    }

    // ══════════════════════════════════════════════════════════════
    //  头部：小字标 + 2x 主标 + 势能读数
    // ══════════════════════════════════════════════════════════════

    private static void header(PreviewCanvas c) {
        // 页眉只占 local y 7..15 一行（表盘从 y≈27 起，中间留白给晶盘顶边）：
        // 左标题 1x + 右读数，同一基线；不叠小字标 / 2x 主标 / 列标三层。
        c.text("ARCANE ALTAR", PX + 8, PY + 7, C_INK, true);

        // 读数拆成"标签 + 数字"：标签压暗、数字提亮 —— 读起来是读数，不是第二个标题。
        // 右端 168 与下面那 15 枚水晶碎片同一条竖轴。
        int right = PX + 168;
        c.textRight("9/15", right, PY + 7, A(C_MINT_HI, 248), true);
        c.textRight("POTENTIAL", right - c.textWidth("9/15") - 5, PY + 7,
                A(0xFF7FB4D4, 235), false);
    }

    // ══════════════════════════════════════════════════════════════
    //  机器：晶盘 / 六边形晶格 / 能量带 / 棱镜核
    // ══════════════════════════════════════════════════════════════

    private static void machine(PreviewCanvas c, float time, boolean crafting) {
        plate(c, crafting);
        instrumentRings(c, time);
        ambient(c);
        if (crafting) {
            chargeRings(c, time);
        }
        ribbons(c, time, crafting);
        lattice(c, time, crafting);
        core(c, time, crafting);
    }

    /** 内区环境光 + 一圈极淡的棱面刻线：核心到晶格带之间不要是一块死黑。 */
    private static void ambient(PreviewCanvas c) {
        SdfShapes.ring(c, MCX, MCY, 0F, 46F, A(C_MINT, 10), A(C_MINT, 0), true);
        SdfShapes.ring(c, MCX, MCY, 20F, 46F, A(C_VIOLET, 0), A(C_VIOLET, 15), true);
        for (int k = 0; k < 8; k++) {
            float a = rad(k * 45.0 + 22.5);
            SdfShapes.line(c,
                    MCX + (float) Math.cos(a) * 34.6F, MCY + (float) Math.sin(a) * 34.6F,
                    MCX + (float) Math.cos(a) * 37.2F, MCY + (float) Math.sin(a) * 37.2F,
                    0.35F, A(C_CYAN, 34));
        }
    }

    /** 机座：16 边晶盘（近似圆但带切面，保住"晶体"的语言）。 */
    private static void plate(PreviewCanvas c, boolean crafting) {
        float[] xs = new float[16];
        float[] ys = new float[16];
        for (int k = 0; k < 16; k++) {
            float a = rad(-90.0 + 11.25 + k * 22.5);
            xs[k] = MCX + (float) Math.cos(a) * PLATE_R;
            ys[k] = MCY + (float) Math.sin(a) * PLATE_R;
        }

        WheelKit.fillPolygon(c, xs, ys, 16, 0x7A070C1B);
        // 盘内径向压暗：环带与核心都要从这块底上"浮"起来
        SdfShapes.ring(c, MCX, MCY, 0F, 54F, A(0xFF060A16, 100), A(0xFF060A16, 0), true);

        // 盘缘切面：16 条短棱线
        for (int k = 0; k < 16; k++) {
            float a = rad(-90.0 + 11.25 + k * 22.5);
            SdfShapes.line(c,
                    MCX + (float) Math.cos(a) * (PLATE_R - 7F),
                    MCY + (float) Math.sin(a) * (PLATE_R - 7F),
                    MCX + (float) Math.cos(a) * PLATE_R,
                    MCY + (float) Math.sin(a) * PLATE_R,
                    0.4F, A(C_CYAN, 38));
        }

        // 盘边：色散两圈 + 主边
        polyOutline(c, xs, ys, scale(xs, ys, 0.986F), 0.5F, A(C_MAGENTA, 62));
        polyOutline(c, xs, ys, scale(xs, ys, 1.012F), 0.5F, A(C_CYAN, 80));
        polyOutline(c, xs, ys, null, 0.5F, A(crafting ? C_MINT_HI : C_MINT, crafting ? 160 : 110));
    }

    /** 晶格带的内外压边 + 核心外的仪表环（让 15 格读成一条"带"而不是散点）。 */
    private static void instrumentRings(PreviewCanvas c, float time) {
        // 晶格带压边：外圈（错位色散）+ 内圈
        SdfShapes.ring(c, MCX, MCY, BAND_OUT - 0.9F, BAND_OUT - 0.2F,
                A(C_MAGENTA, 46), A(C_MAGENTA, 46), false);
        SdfShapes.ring(c, MCX, MCY, BAND_OUT, BAND_OUT + 0.7F,
                A(C_MINT, 96), A(C_MINT, 96), false);
        SdfShapes.ring(c, MCX, MCY, BAND_IN, BAND_IN + 0.8F,
                A(C_MINT, 84), A(C_MINT, 84), false);

        // 反向虚线环：仪表感，也被能量带穿过
        float spin = time * 0.06F;
        for (int i = 0; i < 24; i++) {
            float a0 = i * TAU / 24F - spin;
            SdfShapes.wedge(c, MCX, MCY, 31.4F, 32.0F, a0, a0 + TAU / 24F * 0.42F,
                    A(C_CYAN, 38), A(C_CYAN, 10));
        }
    }

    /** 合成中：从核心向晶格扩散的能量环（就绪态不画，避免抢焦点）。 */
    private static void chargeRings(PreviewCanvas c, float time) {
        for (int k = 0; k < 2; k++) {
            float p = fract(time * 0.42F + k * 0.5F);
            float r = 16F + p * 42F;
            int a = Math.round(90F * (1F - p) * (1F - p));
            SdfShapes.ring(c, MCX, MCY, r - 0.5F, r + 0.5F, A(C_MINT, a), A(C_MINT, a), false);
        }
    }

    /** 已填材料 → 核心 的真实 magic_pulse 能量带（梯形带 + 跑动光点）。 */
    private static void ribbons(PreviewCanvas c, float time, boolean crafting) {
        for (int i = 0; i < MAT_FILLED; i++) {
            float a = rad(-90.0 + i * 24.0);
            float r0 = RING_R - 9.6F;          // 从六边形槽内沿出发
            float r1 = 16.0F;                  // 收敛到棱镜核外沿
            float x0 = MCX + (float) Math.cos(a) * r0;
            float y0 = MCY + (float) Math.sin(a) * r0;
            float x1 = MCX + (float) Math.cos(a) * r1;
            float y1 = MCY + (float) Math.sin(a) * r1;

            int col = ribbonColor(i);
            // 外层柔光 + 底带：没有光点时也看得出"这格连着核心"，
            // 有光点在跑时读成光束，而不是一根刻痕
            SdfShapes.line(c, x0, y0, x1, y1, 2.3F, A(col, crafting ? 20 : 14));
            SdfShapes.line(c, x0, y0, x1, y1, 0.7F, A(col, crafting ? 112 : 80));

            float phase = fract(time * (crafting ? 0.85F : 0.42F) + i * 0.173F);
            pulse(c, x0, y0, x1, y1, col, phase, crafting ? 1.95F : 1.45F);
        }
    }

    /** 三条棱镜色循环：薄荷 / 青 / 淡紫。 */
    private static int ribbonColor(int i) {
        switch (i % 3) {
            case 0:
                return C_MINT;
            case 1:
                return C_CYAN;
            default:
                return C_VIOLET;
        }
    }

    /** 15 个六边形晶格槽。 */
    private static void lattice(PreviewCanvas c, float time, boolean crafting) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            int bx = PX + SLOT_BOX[i][0];
            int by = PY + SLOT_BOX[i][1];
            float hx = bx + SLOT * 0.5F;
            float hy = by + SLOT * 0.5F;
            float dir = rad(-90.0 + i * 24.0);
            boolean filled = i < MAT_FILLED;

            float[] xs = new float[6];
            float[] ys = new float[6];
            for (int k = 0; k < 6; k++) {
                float v = dir + rad(30.0 + k * 60.0);
                xs[k] = hx + (float) Math.cos(v) * HEX_R;
                ys[k] = hy + (float) Math.sin(v) * HEX_R;
            }

            if (filled) {
                // 槽外沿学派色微光（很低，只是"这格是活的"）
                int school = schoolColor(i);
                float beat = 0.72F + 0.28F * (float) Math.sin(time * 1.9F + i * 0.9F);
                SdfShapes.ring(c, hx, hy, 0F, HEX_R + (crafting ? 5.6F : 4.4F),
                        A(school, Math.round((crafting ? 74 : 54) * beat)), A(school, 0), true);

                // 晶体底面 + 一片亮棱面（左上受光）
                WheelKit.fillPolygon(c, xs, ys, 6, C_CELL);
                WheelKit.fillPolygon(c, new float[]{xs[2], xs[3], hx},
                        new float[]{ys[2], ys[3], hy}, 3, A(C_MINT_HI, 14));

                // 边：先色散，再主棱线；内沿（朝圆心那条面）最亮
                polyOutline(c, xs, ys, scale(xs, ys, 1.055F), 0.4F, A(C_MAGENTA, 52));
                polyOutline(c, xs, ys, scale(xs, ys, 0.94F), 0.4F, A(C_CYAN, 58));
                polyOutline(c, xs, ys, null, 0.5F, A(C_MINT, 175));
                SdfShapes.line(c, xs[2], ys[2], xs[3], ys[3], 0.55F, A(C_MINT_HI, 235));
                SdfShapes.line(c, xs[0], ys[0], xs[1], ys[1], 0.5F, A(C_CYAN, 150));

                // 图标：16×16 落整数坐标（槽左上加 1，和实机 icon 位置一致）
                c.blit(c.texture(MAT_ICON[i]), bx + 1, by + 1, 16, 16);
            } else {
                // 空槽：暗玻璃 + 一道内反光 + 中心一枚小六边形空巢（红瞳感，别写字）
                WheelKit.fillPolygon(c, xs, ys, 6, C_CELL_EMPTY);
                SdfShapes.line(c, hx - 3.6F, hy - 2.6F, hx - 1.2F, hy - 0.4F, 0.45F, A(C_INK, 46));
                polyOutline(c, xs, ys, null, 0.45F, A(C_CELL_EDGE, 205));
                SdfShapes.line(c, xs[5], ys[5], xs[0], ys[0], 0.45F, A(C_INK_DIM, 78));
                float[] ex = new float[6];
                float[] ey = new float[6];
                for (int k = 0; k < 6; k++) {
                    float v = dir + rad(30.0 + k * 60.0);
                    ex[k] = hx + (float) Math.cos(v) * 4.4F;
                    ey[k] = hy + (float) Math.sin(v) * 4.4F;
                }
                polyOutline(c, ex, ey, null, 0.4F, A(C_CELL_EDGE, 120));
            }
        }
    }

    /** 棱镜核：径向光核 + 六面晶体 + 缓慢旋转的光栅 + 产物图标。 */
    private static void core(PreviewCanvas c, float time, boolean crafting) {
        float breath = 0.5F + 0.5F * (float) Math.sin(time * 1.5F);
        float hot = crafting ? 1F : 0.55F + 0.3F * breath;

        // 光核（径向渐隐，不用纯白，走薄荷→青）
        SdfShapes.ring(c, MCX, MCY, 0F, 34F, A(C_MINT, Math.round(36F * hot)), A(C_MINT, 0), true);
        SdfShapes.ring(c, MCX, MCY, 0F, 21F, A(C_MINT_HI, Math.round(40F * hot)), A(C_CYAN, 0), true);

        // 光栅：外圈三段 60° 弧正转，内圈十二段短弧反转（比整圈实线安静）
        float spin = time * (crafting ? 30F : 12F);
        for (int s = 0; s < 3; s++) {
            float a0 = rad(spin + s * 120F);
            SdfShapes.wedge(c, MCX, MCY, 23.0F, 24.6F, a0, a0 + rad(58F),
                    A(C_CYAN, 190), A(C_CYAN, 40));
        }
        float spin2 = -time * (crafting ? 22F : 15F);
        for (int s = 0; s < 12; s++) {
            float a0 = rad(spin2 + s * 30F);
            SdfShapes.wedge(c, MCX, MCY, 18.2F, 19.0F, a0, a0 + rad(13F),
                    A(C_MINT, 175), A(C_MINT, 24));
        }

        // 六面晶体（尖朝上下）：先填，再色散描边，最后棱面线
        // 顶点序（y 向下）：0=下 1=左下 2=左上 3=上 4=右上 5=右下
        float[] xs = new float[6];
        float[] ys = new float[6];
        for (int k = 0; k < 6; k++) {
            float a = rad(90.0 + k * 60.0);
            xs[k] = MCX + (float) Math.cos(a) * 13.6F;
            ys[k] = MCY + (float) Math.sin(a) * 13.6F;
        }
        WheelKit.fillPolygon(c, xs, ys, 6, C_PRISM);
        WheelKit.fillPolygon(c, new float[]{xs[2], xs[3], MCX},
                new float[]{ys[2], ys[3], MCY}, 3, A(C_MINT_HI, 30));
        WheelKit.fillPolygon(c, new float[]{xs[5], xs[0], MCX},
                new float[]{ys[5], ys[0], MCY}, 3, A(0xFF03060F, 90));
        SdfShapes.line(c, xs[2], ys[2], MCX, MCY, 0.45F, A(C_MINT_HI, 90));
        SdfShapes.line(c, xs[3], ys[3], MCX, MCY, 0.45F, A(C_MINT_HI, 70));
        SdfShapes.line(c, xs[5], ys[5], MCX, MCY, 0.45F, A(C_CYAN, 60));
        SdfShapes.line(c, xs[0], ys[0], MCX, MCY, 0.45F, A(C_CYAN, 54));
        polyOutline(c, xs, ys, scale(xs, ys, 1.06F), 0.45F, A(C_MAGENTA, 90));
        polyOutline(c, xs, ys, scale(xs, ys, 0.93F), 0.45F, A(C_CYAN, 100));
        polyOutline(c, xs, ys, null, 0.55F, A(crafting ? C_MINT_HI : C_MINT, 225));

        // 图标底下再垫一层薄荷亮点（画在晶体之后才看得见），产物是整屏焦点
        SdfShapes.ring(c, MCX, MCY, 0F, 11.5F,
                A(C_MINT_HI, Math.round(52F * hot)), A(C_MINT, 0), true);

        // 产物图标：16×16，落在产物槽 (88,76) 的内侧 1px
        c.blit(c.texture("textures/item/fire_ball.png"), PX + OUT_X + 1, PY + OUT_Y + 1, 16, 16);
    }

    // ══════════════════════════════════════════════════════════════
    //  左：竖向液体光柱（合成进度）
    // ══════════════════════════════════════════════════════════════

    private static void tube(PreviewCanvas c, float time, float progress) {
        int x = PX + TUBE_X;
        int y = PY + TUBE_Y;
        int w = TUBE_W;
        int h = TUBE_H;
        float r = w * 0.5F;

        // 外壳（玻璃管）+ 管内介质一层极淡青色（空管才不像一条黑槽）
        // + 左侧一条内反光 / 右侧一条暗边，玻璃管才有体积
        SdfShapes.roundRect(c, x, y, w, h, r, 0xE00A1224, 0xF00D1830, 0, 0F);
        SdfShapes.roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(1F, r - 1F),
                A(0xFF1B8E9E, 24), A(0xFF0E4A66, 40), 0, 0F);
        for (int row = 3; row < h - 3; row++) {
            int inset = roundInset(row + 0.5F, h, r);
            c.fill(x + inset + 1, y + row, 2, 1, A(C_INK, 26));
            c.fill(x + w - inset - 2, y + row, 1, 1, A(0xFF040914, 90));
        }
        // 上下管口各一根细亮线：读成"玻璃管"，不是滚动条
        c.fill(x + 3, y + 3, w - 6, 1, A(C_INK, 42));
        c.fill(x + 3, y + h - 4, w - 6, 1, A(C_INK, 30));

        // 液体：自下而上逐行填，两端按胶囊收边
        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = w - 2;
        int innerH = h - 2;
        float innerR = r - 1F;
        int span = innerH - 2;
        int fillPx = Math.round(span * clamp01(progress));
        for (int i = 0; i < fillPx; i++) {
            int row = innerH - 2 - i;
            float t = i / (float) Math.max(1, fillPx - 1);
            int col = mix(0xFF15837A, 0xFFB4FFE8, (float) Math.pow(t, 0.72F));
            int inset = roundInset(row + 0.5F, innerH, innerR);
            int rowW = innerW - inset * 2;
            c.fill(innerX + inset, innerY + row, rowW, 1, A(col, 200));
            if (rowW > 4) {
                // 左 1px 亮芯、右 1px 暗边：竖柱才有圆柱体积感
                c.fill(innerX + inset, innerY + row, 1, 1, A(C_MINT_HI, 150));
                c.fill(innerX + inset + rowW - 1, innerY + row, 1, 1, A(0xFF06403E, 120));
            }
        }
        if (fillPx > 0) {
            // 液面：一根亮线 + 上方两行柔光
            int surf = innerY + innerH - 2 - fillPx;
            int inset = roundInset(surf - innerY + 0.5F, innerH, innerR);
            c.fill(innerX + inset, surf, innerW - inset * 2, 1, A(C_MINT_HI, 250));
            c.fill(innerX + inset, surf - 1, innerW - inset * 2, 1, A(C_MINT, 95));
            c.fill(innerX + inset, surf - 2, innerW - inset * 2, 1, A(C_MINT, 40));

            // 上浮气泡点
            for (int i = 0; i < 5; i++) {
                float ph = fract(time * 0.28F + i * 0.21F);
                int by = surf + Math.round(4F + ph * Math.max(2, fillPx - 7));
                if (by >= innerY + innerH - 3) {
                    continue;
                }
                int bx = innerX + 3 + ((i * 5 + 1) % 6);
                c.fill(bx, by, 2, 2, A(C_MINT_HI, 170));
                c.fill(bx, by, 1, 1, A(C_INK, 240));
            }
        } else {
            // 就绪态：柱底留 3px 薄荷余液 + 一根液面高光 —— 读成"0% 的液面"，
            // 而不是一段深色玻璃；液面上方再放两行极淡的余光。
            int surf = innerY + innerH - 6;
            for (int row = surf; row < innerY + innerH - 1; row++) {
                int inset = roundInset(row - innerY + 0.5F, innerH, innerR);
                int rowW = innerW - inset * 2;
                c.fill(innerX + inset, row, rowW, 1, A(0xFF6FF0DA, 158));
                if (rowW > 4) {
                    c.fill(innerX + inset + 1, row, 1, 1, A(C_MINT_HI, 180));
                }
            }
            int inset = roundInset(surf - innerY + 0.5F, innerH, innerR);
            c.fill(innerX + inset, surf, innerW - inset * 2, 1, A(C_MINT_HI, 238));
            c.fill(innerX + inset, surf - 1, innerW - inset * 2, 1, A(C_MINT, 78));
            c.fill(innerX + inset, surf - 2, innerW - inset * 2, 1, A(C_MINT, 32));
        }

        // 管壁：内衬 1px + 外沿色散
        SdfShapes.roundRect(c, x + 1.4F, y + 1.4F, w - 2.8F, h - 2.8F, Math.max(1F, r - 1.4F),
                0, 0, A(C_INK, 40), 1F);
        SdfShapes.roundRect(c, x - 0.8F, y - 0.8F, w, h, r, 0, 0, A(C_MAGENTA, 80), 1F);
        SdfShapes.roundRect(c, x + 0.8F, y + 0.8F, w, h, r, 0, 0, A(C_CYAN, 100), 1F);
        SdfShapes.roundRect(c, x, y, w, h, r, 0, 0, A(C_INK, 160), 1F);

        // 右侧刻度（25/50/75%）
        for (int i = 1; i <= 3; i++) {
            int ty = y + h - 3 - Math.round(span * i / 4F);
            c.fill(x + w + 1, ty, i == 2 ? 4 : 2, 1, A(C_CYAN, 90));
        }

        // 百分比读数
        String pct = Math.round(clamp01(progress) * 100F) + "%";
        c.textCentered(pct, PX + TUBE_X + w / 2, PY + 146,
                progress > 0.01F ? A(C_MINT_HI, 245) : A(C_INK_FAINT, 205), true);
    }

    // ══════════════════════════════════════════════════════════════
    //  右：15 枚势能水晶碎片（9 亮 / 6 灭）
    // ══════════════════════════════════════════════════════════════

    private static void shards(PreviewCanvas c, float time, boolean crafting) {
        int sx = PX + SHARD_X;
        int top = PY + SHARD_TOP;
        // 竖向中轴，把 15 枚读成一列（不是 15 个孤立方块）
        c.fill(sx + SHARD_W / 2, top + 2, 1,
                (SHARD_COUNT - 1) * SHARD_PITCH + SHARD_H - 2, A(C_CYAN_LO, 54));

        for (int i = 0; i < SHARD_COUNT; i++) {
            int y = top + i * SHARD_PITCH;
            boolean lit = i < SHARD_LIT;
            float[] xs = {sx, sx + 2.5F, sx + SHARD_W - 2.5F,
                    sx + SHARD_W, sx + SHARD_W - 2.5F, sx + 2.5F};
            float[] ys = {y + 3F, y, y, y + 3F, y + SHARD_H, y + SHARD_H};

            if (lit) {
                float beat = 0.75F + 0.25F * (float) Math.sin(time * 2.2F + i * 0.8F);
                SdfShapes.ring(c, sx + SHARD_W * 0.5F, y + SHARD_H * 0.5F, 0F, 7.6F,
                        A(C_MINT, Math.round((crafting ? 46 : 34) * beat)), A(C_MINT, 0), true);
                WheelKit.fillPolygon(c, xs, ys, 6, 0xF06FF0D4);
                c.fill(sx + 3, y + 1, SHARD_W - 6, 1, A(C_MINT_HI, 245));
                c.fill(sx + 2, y + SHARD_H - 1, SHARD_W - 4, 1, A(0xFF2FB4A8, 225));
                c.fill(sx + 2, y + 3, 1, 1, A(C_INK, 215));
            } else {
                WheelKit.fillPolygon(c, xs, ys, 6, 0xE61A2740);
                c.fill(sx + 2, y + 1, SHARD_W - 4, 1, A(0xFF4C6F92, 215));
                c.fill(sx + 2, y + SHARD_H - 1, SHARD_W - 4, 1, A(0xFF0A1220, 200));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  按钮：玻璃胶囊 + 薄荷→青渐变 + 色散描边
    // ══════════════════════════════════════════════════════════════

    private static void button(PreviewCanvas c, boolean crafting) {
        int x = PX + BTN_X;
        int y = PY + BTN_Y;
        int w = BTN_W;
        int h = BTN_H;
        float r = h * 0.5F;

        if (!crafting) {
            // 外发光：玻璃胶囊在暗底上"自身发光"
            SdfShapes.roundRect(c, x - 3F, y - 2.5F, w + 6, h + 5, r + 2.5F,
                    A(C_MINT, 44), A(C_CYAN, 14), 0, 0F);
            // 实心：薄荷 → 青（玻璃胶囊）
            SdfShapes.roundRect(c, x, y, w, h, r, 0xFFB4F9E4, 0xFF1F86B6, A(C_MINT_HI, 225), 1F);
            // 上半高光 + 下沿内阴影 = 玻璃的厚度
            SdfShapes.roundRect(c, x + 3F, y + 1.4F, w - 6F, h * 0.46F, 5F,
                    0x46E8FFFF, 0x00E8FFFF, 0, 0F);
            SdfShapes.roundRect(c, x + 3F, y + h - 5.5F, w - 6F, 4F, 2F,
                    0x00123A48, 0x55123A48, 0, 0F);
            // 色散：实心之后沿胶囊外沿错位描边，边缘才有品红/青的色边
            SdfShapes.roundRect(c, x - 0.8F, y - 0.8F, w + 1.6F, h + 1.6F, r + 0.8F,
                    0, 0, A(C_MAGENTA, 115), 1F);
            SdfShapes.roundRect(c, x + 0.8F, y + 0.8F, w - 1.6F, h - 1.6F, r - 0.8F,
                    0, 0, A(C_CYAN, 105), 1F);
            // 字用深色反白（深靛，不是纯黑）
            c.textCentered("CRAFT", x + w / 2, y + 5, 0xFF06202B, false);
        } else {
            // 禁用：去饱和的冷玻璃，只剩一圈低对比描边
            SdfShapes.roundRect(c, x, y, w, h, r, 0xFF2C3644, 0xFF18212C, A(0xFF74899E, 150), 1F);
            SdfShapes.roundRect(c, x + 3F, y + 1.4F, w - 6F, h * 0.42F, 5F,
                    A(0xFFA8C2D8, 20), A(0xFFA8C2D8, 0), 0, 0F);
            SdfShapes.roundRect(c, x - 0.8F, y - 0.8F, w + 1.6F, h + 1.6F, r + 0.8F,
                    0, 0, A(0xFF7FB4D8, 60), 1F);
            c.textCentered("CRAFTING", x + w / 2, y + 5, 0xFF8EA5B8, false);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  页脚：状态行 + 分隔线（背包字标已删：它比背包格还宽，且会撞状态行）
    // ══════════════════════════════════════════════════════════════

    private static void footer(PreviewCanvas c, float time, boolean crafting) {
        // 状态行居中（local 88,166）：只留状态本身，两头各一颗 1px 小菱形点
        String status = crafting ? "CRAFTING...  4 s LEFT" : "READY TO CRAFT";
        int color = crafting ? A(C_MINT_HI, 248) : A(C_MINT, 242);
        c.textCentered(status, PX + 88, PY + STATUS_Y, color, true);

        int half = c.textWidth(status) / 2;
        int dot = crafting ? C_CYAN : C_MINT;
        float breath = crafting
                ? 0.55F + 0.45F * (float) Math.sin(time * 3.4F)
                : 0.55F + 0.25F * (float) Math.sin(time * 1.6F);
        int dotA = Math.round(70F * breath + 70F);
        diamond(c, PX + 88 - half - 9, PY + STATUS_Y + 4, A(dot, dotA));
        diamond(c, PX + 88 + half + 9, PY + STATUS_Y + 4, A(dot, dotA));

        // 分隔线：整条压暗，左端一小段更亮（和页面其它发丝线同一套语言）
        c.fill(PX + INV_X, PY + INV_RULE_Y, PANEL_W - INV_X * 2, 1, A(C_CYAN_LO, 82));
        c.fill(PX + INV_X, PY + INV_RULE_Y, 24, 1, A(C_MINT, 105));
        c.fill(PX + INV_X, PY + INV_RULE_Y + 1, PANEL_W - INV_X * 2, 1, A(0xFF03060F, 120));
    }

    // ══════════════════════════════════════════════════════════════
    //  背包：3×9 + 快捷栏（深玻璃格 + 冷白内边 + 内凹阴影）
    // ══════════════════════════════════════════════════════════════

    private static void inventoryLayer(PreviewCanvas c) {
        // 背包底板：一层极淡的玻璃，让 27 个格子读成一个组件。
        // 底边到 253：面板描边在 255，留 2px 呼吸（快捷栏 236..253 也在里面）。
        c.fill(PX + INV_X - 3, PY + INV_Y - 3, PANEL_W - (INV_X - 3) * 2,
                (HOT_Y + SLOT - INV_Y) + 3, A(0xFF0A1120, 96));

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                cell(c, PX + INV_X + col * PITCH, PY + INV_Y + row * PITCH, false);
            }
        }
        for (int col = 0; col < INV_COLS; col++) {
            cell(c, PX + INV_X + col * PITCH, PY + HOT_Y, true);
        }

        for (Object[] item : INV_ITEMS) {
            c.blit(c.texture((String) item[2]),
                    PX + INV_X + (Integer) item[0] * PITCH + 1,
                    PY + INV_Y + (Integer) item[1] * PITCH + 1, 16, 16);
        }
        for (Object[] item : HOT_ITEMS) {
            c.blit(c.texture((String) item[1]),
                    PX + INV_X + (Integer) item[0] * PITCH + 1, PY + HOT_Y + 1, 16, 16);
        }
    }

    private static void cell(PreviewCanvas c, int x, int y, boolean hotbar) {
        // 18×18 槽面：外圈 1px 冷暗蓝 = 玻璃格边（压暗，免得 27 格连成一张表单格子），
        // 里面 16×16 是井底；上/左压暗、下/右冷白，做出"内凹玻璃"的厚度。
        c.fill(x, y, SLOT, SLOT, A(hotbar ? 0xFF2B4258 : 0xFF1D2E42, 205));
        c.fill(x + 1, y + 1, SLOT - 2, SLOT - 2, hotbar ? 0xE0091322 : 0xE0060C16);
        c.fill(x + 1, y + 1, SLOT - 2, 1, A(0xFF02060E, 200));
        c.fill(x + 1, y + 1, 1, SLOT - 2, A(0xFF02060E, 200));
        c.fill(x + 1, y + SLOT - 2, SLOT - 2, 1, A(0xFFBFE0FF, hotbar ? 60 : 44));
        c.fill(x + SLOT - 2, y + 1, 1, SLOT - 2, A(0xFFBFE0FF, hotbar ? 60 : 44));
    }

    // ══════════════════════════════════════════════════════════════
    //  着色器小工具：magic_pulse 梯形带
    // ══════════════════════════════════════════════════════════════

    /** 沿线段跑一颗光头（真实 magic_pulse，uProgress 0..1）。 */
    private static void pulse(PreviewCanvas c, float x1, float y1, float x2, float y2,
                              int color, float progress, float intensity) {
        PreviewShader sh = c.shader("magic_pulse");
        sh.set("uProgress", progress);
        sh.set("uColor", ((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F,
                (color & 0xFF) / 255F, 1F);
        sh.set("uIntensity", intensity);
        ribbon(c, sh, x1, y1, x2, y2, 1.35F);
    }

    /** Position(3) + UV0(2)：UV0.x 沿带、UV0.y 跨带。 */
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
    //  几何 / 颜色小工具
    // ══════════════════════════════════════════════════════════════

    /** 多边形描边（逐边 SDF 线段；{@code scaled} 为色散用的缩放顶点，可为 null）。 */
    private static void polyOutline(PreviewCanvas c, float[] xs, float[] ys, float[] scaled,
                                    float halfWidth, int argb) {
        int n = xs.length;
        for (int k = 0; k < n; k++) {
            int j = (k + 1) % n;
            if (scaled == null) {
                SdfShapes.line(c, xs[k], ys[k], xs[j], ys[j], halfWidth, argb);
            } else {
                SdfShapes.line(c, scaled[k * 2], scaled[k * 2 + 1],
                        scaled[j * 2], scaled[j * 2 + 1], halfWidth, argb);
            }
        }
    }

    /** 以重心为原点缩放多边形顶点，返回 {x0,y0,x1,y1,...}（色散描边用）。 */
    private static float[] scale(float[] xs, float[] ys, float factor) {
        float cx = 0F;
        float cy = 0F;
        for (int i = 0; i < xs.length; i++) {
            cx += xs[i];
            cy += ys[i];
        }
        cx /= xs.length;
        cy /= ys.length;
        float[] out = new float[xs.length * 2];
        for (int i = 0; i < xs.length; i++) {
            out[i * 2] = cx + (xs[i] - cx) * factor;
            out[i * 2 + 1] = cy + (ys[i] - cy) * factor;
        }
        return out;
    }

    /** 小菱形点（状态行两头的装饰；3px 的那点看着像"+"，所以用 5px 的菱形）。 */
    private static void diamond(PreviewCanvas c, int cx, int cy, int argb) {
        c.fill(cx, cy - 2, 1, 1, argb);
        c.fill(cx - 1, cy - 1, 3, 1, argb);
        c.fill(cx - 2, cy, 5, 1, argb);
        c.fill(cx - 1, cy + 1, 3, 1, argb);
        c.fill(cx, cy + 2, 1, 1, argb);
    }

    /** 胶囊/圆角矩形在第 localY 行（0..h）的左右收边像素。 */
    private static int roundInset(float localY, float h, float rad) {
        float dy = 0F;
        if (localY < rad) {
            dy = rad - localY;
        } else if (localY > h - 1 - rad) {
            dy = localY - (h - 1 - rad);
        }
        if (dy <= 0F) {
            return 0;
        }
        float r = Math.max(0.001F, rad);
        float dx = r - (float) Math.sqrt(Math.max(0F, r * r - dy * dy));
        return Math.max(0, Math.round(dx));
    }

    /** 15 个材料槽的学派色（只用在槽外沿微光上，不参与主体配色）。 */
    private static int schoolColor(int i) {
        int[] school = {
                0xFFFFF4AC, 0xFFFF7843, 0xFF53BEFF, 0xFF78D98A,
                0xFFBE8BDA, 0xFF8B85FF, 0xFFE7A7EF
        };
        return school[i % school.length];
    }

    private static int A(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    private static int mix(int c1, int c2, float t) {
        t = clamp01(t);
        int a = Math.round(((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = Math.round(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = Math.round(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp01(float v) {
        return v < 0F ? 0F : (v > 1F ? 1F : v);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private static float rad(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
