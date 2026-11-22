import org.lwjgl.opengl.GL11;

/**
 * 法术合成界面（奥秘仪台）——「棱镜晶格 × 星象仪」<b>混搭稿</b>（Prism Lattice × Astral Instrument）。
 *
 * <h3>混的是什么</h3>
 * <p>主体是 <b>C 案（棱镜晶格）</b>：深靛蓝玻璃面板 + 色散描边 + 十六边形晶盘 + 15 个六边形晶体槽
 * + 材料到核心的 {@code magic_pulse} 能量带 + 玻璃胶囊按钮。</p>
 *
 * <p><b>左右两条读数条整条换成 A 案（星象仪）的形状、位置与配色</b>：左侧进度柱挂在面板<em>外</em>
 * （局部 -22 / 36，16×96：外框 + 玻璃管 + 水银自下而上填 + 5 档水线 + 顶端指示灯），
 * 右侧势能排挂在面板<em>外</em>（局部 182 / 25，15 格 16×6、间隔 2 的保险丝排）。
 * 这两个方法的<b>画法、几何、alpha、颜色全部与 {@code SceneSpellAltarAstral} 逐字符相同</b>。</p>
 *
 * <p>为什么读数条不跟着统一成 C 的冷色：它们挂在面板外，本来就是"另外两件仪器"；
 * 一旦染成和面板同族的青色，就会和面板那圈 1px 色散描边糊成一片，
 * "仪表在框外"这个结构信息直接消失（这一版之前就翻过这个车）。
 * 暖金与冷靛蓝的反差正是 A 案的结构语言，所以颜色统一只做在<b>面板与机器</b>上；
 * 想看染成 C 色的对照版，跑一次场景，{@code _tinted_*} 两帧就是（{@link #setGaugePalette}）。</p>
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
 *       {@code POTENTIAL 9/15}（右边界 168，和面板外那排保险丝同一竖轴）。
 *       页眉不叠第二行、不压表盘——晶盘顶边在 y≈27，中间留白。
 *       标题走 1x 而不是 2x：表盘圆心 y=84 / r=48，页眉可用高度只有 ~16px，
 *       2x 主标题（16px 高 + 阴影）会直接压到 12 点方向的晶格槽。</li>
 *   <li><b>机</b>（局部 y 24..144）：一块 16 边形晶盘托住 15 个六边形晶格槽；圆心是棱镜核 +
 *       产物图标。已填材料到核心拉极细 {@code magic_pulse} 能量带（带跑动光点），
 *       空槽是暗玻璃 + 一点内反光 —— 空/满一眼分得出来，不用写占位字。</li>
 *   <li><b>左 / 右两条读数条</b>：形状、尺寸、位置、配色全部照 A 案，挂在面板<em>外</em>两侧的留白里
 *       （左 16×96 @ 局部 -22/36，右 15×(16×6) @ 局部 182/25）。
 *       两条都不写字标、柱下也<b>不写百分比</b> —— 与 A 案一致，进度只靠液面高度与
 *       5 档水线读（多写一行数字就又是"我自己加的东西"，不再算 A 案）。
 *       面板内那两根挂轨（{@link #mountRails}）属于额外装饰，A/C 都没有，
 *       由 {@link #MOUNT_RAILS} 控制，默认<b>关</b>。</li>
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
 * <p>导出：{@code spell_altar_hybrid_ready.png} / {@code spell_altar_hybrid_crafting.png}
 * （62% 合成中），默认导出（{@code spell_altar_hybrid.png}）= 就绪态。画面内文字只用英文。</p>
 */
public final class SceneSpellAltarHybrid {

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

    // 左：进度柱（面板外）—— 与 A 案同名同值，方便逐行对照 SceneSpellAltarAstral#mercury
    private static final int BAR_X = -22;
    private static final int BAR_Y = 36;
    private static final int BAR_W = 16;
    private static final int BAR_H = 96;

    // 右：势能保险丝（面板外，与 A 案同一位置：局部 182 / 25，15 格 16x6、间隔 2）
    private static final int PWR_X = 182;
    private static final int PWR_Y = 25;
    private static final int PWR_W = 16;
    private static final int PWR_H = 6;
    private static final int PWR_GAP = 2;
    private static final int PWR_UNITS = 15;
    private static final int PWR_LIT = 9;

    /** 面板内的两根挂轨：A/C 都没有，是我为了填空补的，默认关。 */
    private static final boolean MOUNT_RAILS = false;

    /** 读数条底下那块暖色底板（见 {@link #gaugeMount}）：关掉就完全等于 A 案的半透明柱体。 */
    private static final boolean GAUGE_MOUNT = true;

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

    // ══════════════════════════════════════════════════════════════
    //  读数条的两套配色：A 案原色（默认）/ C 案冷色（对照）
    // ══════════════════════════════════════════════════════════════

    // A 案原色（暖）：旧金 + 亮金 + 暗金 + 琥珀
    private static final int W_GOLD = 0xFFD9B771;
    private static final int W_GOLD_HI = 0xFFF7E6BF;
    private static final int W_GOLD_LO = 0xFF6B5228;
    private static final int W_AMBER = 0xFFE9A54A;

    // 读数条里"容器底色"的两套：A 案暖木/铜底 vs C 案暗靛玻璃
    // 注意这几个是 4 个八位字面量，不参与色相映射表，单独成对切换
    private static int G_FRAME_TOP = 0xF00B0810;
    private static int G_FRAME_BOT = 0xF006040A;
    private static int G_GLASS_TOP = 0xC00A0813;
    private static int G_GLASS_BOT = 0xC0020206;
    private static int G_UNLIT_TOP = 0x8C05040A;
    private static int G_UNLIT_BOT = 0x8C020206;

    // 当前生效的四种"描线/液体"色（下面 setGaugePalette 会改写）
    private static int A_GOLD = W_GOLD;
    private static int A_GOLD_HI = W_GOLD_HI;
    private static int A_GOLD_LO = W_GOLD_LO;
    private static int A_AMBER = W_AMBER;

    /**
     * 切换读数条配色 —— <b>只改色相与容器底色，几何/alpha/画法一律不动</b>。
     *
     * @param astral {@code true} = A 案原色（黄铜 + 水银金 + 琥珀），默认；
     *               {@code false} = 染成 C 案的青/薄荷（对照用，见渲染出的 {@code _tinted_*} 帧）
     *
     * <p>为什么默认是 A 案原色：两条读数条挂在面板<em>外</em>，本来就是"另外两件仪器"；
     * 染成和面板同族的青色之后，它们会和面板的 1px 色散描边糊成一片，
     * "仪表在框外"的读法就没了。暖金与冷靛蓝的对比正是 A 案的结构信息，
     * 颜色统一只做在面板与机器上。</p>
     */
    private static void setGaugePalette(boolean astral) {
        if (astral) {
            A_GOLD = W_GOLD;
            A_GOLD_HI = W_GOLD_HI;
            A_GOLD_LO = W_GOLD_LO;
            A_AMBER = W_AMBER;
            G_FRAME_TOP = 0xF00B0810;
            G_FRAME_BOT = 0xF006040A;
            G_GLASS_TOP = 0xC00A0813;
            G_GLASS_BOT = 0xC0020206;
            G_UNLIT_TOP = 0x8C05040A;
            G_UNLIT_BOT = 0x8C020206;
        } else {
            // 按亮度对齐 A 案：GOLD(185)→C_CYAN(186)、GOLD_LO(85)→C_CELL_EDGE(75)，
            // 不能把 GOLD_LO 映射到 C_CYAN_LO(113)，否则亮格整块发蓝
            A_GOLD = C_CYAN;
            A_GOLD_HI = C_MINT_HI;
            A_GOLD_LO = C_CELL_EDGE;
            A_AMBER = C_MINT;
            G_FRAME_TOP = 0xF00C1226;
            G_FRAME_BOT = 0xF0060A18;
            G_GLASS_TOP = 0xC00A1224;
            G_GLASS_BOT = 0xC0040812;
            G_UNLIT_TOP = 0x8C050914;
            G_UNLIT_BOT = 0x8C03060F;
        }
    }

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
        // ① 默认版：读数条用 A 案原色（黄铜 / 水银金 / 琥珀），主体仍是 C 案的冷色玻璃
        setGaugePalette(true);
        shot(c, time, false);
        c.save("spell_altar_hybrid_ready");
        shot(c, time, true);
        c.save("spell_altar_hybrid_crafting");

        // ② 对照版：读数条也染成 C 案的青/薄荷（几何与 alpha 完全相同，只换色相）
        setGaugePalette(false);
        shot(c, time, false);
        c.save("spell_altar_hybrid_tinted_ready");
        shot(c, time, true);
        c.save("spell_altar_hybrid_tinted_crafting");

        // 默认导出（GuiPreview 会在 render 返回后再存一张 <scene>.png）= A 案原色版
        setGaugePalette(true);
        shot(c, time, false);
    }

    private static void shot(PreviewCanvas c, float time, boolean crafting) {
        background(c, time);
        panel(c);
        if (MOUNT_RAILS) {
            mountRails(c);
        }
        machine(c, time, crafting);
        gaugeMount(c);
        mercury(c, time, crafting ? CRAFT_PROGRESS : 0F, crafting);
        fuses(c, time, crafting);
        header(c);
        button(c, crafting);
        footer(c, time, crafting);
        inventoryLayer(c);
    }

    // ══════════════════════════════════════════════════════════════
    //  面板内的两根"仪表挂轨"：把面板外那两条读数条和面板连起来
    // ══════════════════════════════════════════════════════════════

    /**
     * 两条读数条挂到面板外以后，面板内左右各空出一条窄带（左 32px、右 16px，因为晶盘圆心
     * 在 x=96 而不是 88）。这里补两根极淡的竖向挂轨（各自离晶盘边缘 ~10px，视觉上对称） + 几道短刻度 + 两端菱形：
     * 读成"仪表从这两根轨上挂出去"，既补上空白，也让外侧两根柱子不显得是贴上去的。
     *
     * <p>透明度压到 30~90：它是衬底，不许比晶格亮。</p>
     */
    private static void mountRails(PreviewCanvas c) {
        int top = PY + 30;
        int bottom = PY + 140;
        rail(c, PX + 26, top, bottom);
        rail(c, PX + 167, top, bottom);
    }

    private static void rail(PreviewCanvas c, int x, int top, int bottom) {
        c.fill(x, top, 1, bottom - top, A(C_CYAN_LO, 44));
        for (int i = 0; i <= 6; i++) {
            int ty = top + Math.round((bottom - top) * i / 6F);
            int len = i % 2 == 0 ? 4 : 2;
            c.fill(x - (len / 2), ty, len, 1, A(C_INK, i % 2 == 0 ? 62 : 36));
        }
        SdfShapes.star(c, x + 0.5F, top - 2F, 2.0F, 0.7F, 4, 0F, A(C_CYAN, 96));
        SdfShapes.star(c, x + 0.5F, bottom + 2F, 2.0F, 0.7F, 4, 0F, A(C_CYAN, 96));
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
    //  读数条的安装底板：把"框外的仪表"和面板的青色描边隔开
    // ══════════════════════════════════════════════════════════════

    /**
     * A 案的两条读数条本身是<b>半透明</b>的（外框 α240、玻璃 α192），在 A 案里它们压在暖色桌面上，
     * 透出来的底色也是暖的，所以看着就是黄铜；搬到 C 案的靛蓝星场上之后，
     * 同一份像素会透进蓝底，1:1 看整图时柱体会显得发青、并且和面板那圈青色色散描边糊在一起 ——
     * 这正是"单看柱子的裁图是对的、看整图颜色却不对"的来源。
     *
     * <p>解法不是改柱子的颜色（那就不再是 A 案了），而是给它补一块 A 案里本来就存在的
     * <b>暖色底板</b>：不透光的暗木色圆角块，比柱体外框再大 4~5px，带 1px 暖金细边。
     * 这样柱子的合成底色回到 A 案的暖调，同时和面板之间有了明确的物理边界。</p>
     */
    private static void gaugeMount(PreviewCanvas c) {
        if (!GAUGE_MOUNT) {
            return;
        }
        // 左柱底板（外框 19×102 @ 局部 -23.5/33）
        SdfShapes.roundRect(c, PX + BAR_X - 6F, PY + BAR_Y - 8F, BAR_W + 11F, BAR_H + 16F, 4F,
                0xFF0C0908, 0xFF070506, A(A_GOLD, 44), 1F);
        // 右排底板（底板 22×(totalH+6) @ 局部 179/22）
        int totalH = PWR_UNITS * PWR_H + (PWR_UNITS - 1) * PWR_GAP;
        SdfShapes.roundRect(c, PX + PWR_X - 7F, PY + PWR_Y - 8F,
                PWR_W + 14F, totalH + 16F, 4F,
                0xFF0C0908, 0xFF070506, A(A_GOLD, 44), 1F);
    }

    // ══════════════════════════════════════════════════════════════
    //  左：进度柱 —— A 案的画法逐行照搬，只换色相
    // ══════════════════════════════════════════════════════════════

    /**
     * 与 {@code SceneSpellAltarAstral#mercury} <b>逐行同构</b>，一个几何数字都没改：
     *
     * <ul>
     *   <li>外框 {@code (x-1.5, y-3, 19, 102, r3)}，边 α92；</li>
     *   <li>玻璃管 {@code (x+3.5, y+3, 9, 90, r2)}，边 α58；</li>
     *   <li>液柱 {@code x+5 / 宽 6 / y+5 / 高 86}，两段 {@code fillGradientV}，0% 留 3px 残液；</li>
     *   <li>液面 1 行白高光（α200/110）+ 液面下一枚 r8 光晕；</li>
     *   <li>管壁 1 根 1px 反光；5 条长短交替水线（inset 0/3，α78/44）；</li>
     *   <li>顶端指示灯 {@code (x+8, y-1, r2.6)}，合成中 α200·pulse，平时 α120。</li>
     * </ul>
     *
     * <p>改的只有颜色常量 {@link #A_GOLD} / {@link #A_GOLD_HI} / {@link #A_GOLD_LO} /
     * {@link #A_AMBER}：旧金四色 → C 案的青/薄荷。也**没有**百分比读数——A 案不写字，
     * 这里同样不写，进度靠液面高度与 5 档水线读。</p>
     */
    private static void mercury(PreviewCanvas c, float time, float progress, boolean crafting) {
        int x = PX + BAR_X;
        int y = PY + BAR_Y;

        // 外框（A 案 19×102 的铜架位置；只是把暖木底换成 C 案的暗靛玻璃）
        SdfShapes.roundRect(c, x - 1.5F, y - 3F, BAR_W + 3F, BAR_H + 6F, 3F,
                0xF00C1226, 0xF0060A18, A(A_GOLD, 92), 1F);
        // 玻璃管：外壁一根亮边、内里压暗
        SdfShapes.roundRect(c, x + 3.5F, y + 3F, BAR_W - 7F, BAR_H - 6F, 2F,
                G_GLASS_TOP, G_GLASS_BOT, A(A_GOLD, 58), 1F);

        int innerTop = y + 5;
        int innerH = BAR_H - 10;
        int filled = Math.round(innerH * clamp01(progress));
        int colLeft = x + 5;
        int colW = BAR_W - 10;

        // 0% 也留一小滴，空管才不像"坏掉的空槽"
        int minDrop = 3;
        int fillPx = Math.max(minDrop, filled);
        int top = innerTop + innerH - fillPx;
        c.fillGradientV(colLeft, top, colW, fillPx,
                A(progress > 0.01F ? A_GOLD_HI : A_AMBER, progress > 0.01F ? 235 : 170),
                A(A_AMBER, 200));
        // 液面：一道白高光
        c.fill(colLeft, top, colW, 1, A(0xFFFFFFFF, progress > 0.01F ? 200 : 110));
        if (filled > 0) {
            SdfShapes.disc(c, x + BAR_W * 0.5F, top + 2F, 8F,
                    A(A_AMBER, 54), A(A_AMBER, 0));
        }
        // 玻璃反光：管壁上一根 1px 竖直亮线
        c.fill(colLeft, innerTop, 1, innerH, A(0xFFFFFF, 26));

        // 刻度：横贯管身的 5 档水线，长短交替
        for (int i = 0; i <= 4; i++) {
            int ty = innerTop + innerH - Math.round(innerH * i / 4F);
            int inset = i % 2 == 0 ? 0 : 3;
            c.fill(colLeft + inset, ty, colW - inset, 1,
                    A(A_GOLD, i % 2 == 0 ? 78 : 44));
        }

        // 上端指示灯：合成中实心呼吸，平时压暗
        float pulse = 0.6F + 0.4F * (float) Math.sin(time * 3.0F);
        SdfShapes.disc(c, x + BAR_W * 0.5F, y - 1F, 2.6F,
                A(crafting ? A_AMBER : A_GOLD_LO, crafting ? Math.round(200 * pulse) : 120),
                A(crafting ? A_AMBER : A_GOLD_LO, 0));
    }

    // ══════════════════════════════════════════════════════════════
    //  右：15 枚势能保险丝 —— A 案的画法逐行照搬，只换色相
    // ══════════════════════════════════════════════════════════════

    /**
     * 与 {@code SceneSpellAltarAstral#fuses} <b>逐行同构</b>：
     * 底板 {@code (x-3, y-3, 22, totalH+6, r3)} 边 α92；每格 {@code (x, py, 16, 6, r1.2)}，
     * 亮格底 α150/90、边 α70，亮芯 {@code (x+2, py+2, 12, 2)} α225 + 两端 1px 接线柱 α200；
     * 灭格底 0x8C...、边 α34；合成中整排外圈呼吸（{@code x-1.5, y-1.5, 19, totalH+3}，α70·pulse）。
     * 颜色同样只换成 C 案的四色。
     */
    private static void fuses(PreviewCanvas c, float time, boolean crafting) {
        int x = PX + PWR_X;
        int y = PY + PWR_Y;
        int totalH = PWR_UNITS * PWR_H + (PWR_UNITS - 1) * PWR_GAP;

        SdfShapes.roundRect(c, x - 3F, y - 3F, PWR_W + 6F, totalH + 6F, 3F,
                0xF00C1226, 0xF0060A18, A(A_GOLD, 92), 1F);

        for (int i = 0; i < PWR_UNITS; i++) {
            int py = y + i * (PWR_H + PWR_GAP);
            boolean lit = i < PWR_LIT;
            SdfShapes.roundRect(c, x, py, PWR_W, PWR_H, 1.2F,
                    lit ? A(A_GOLD_LO, 150) : G_UNLIT_TOP,
                    lit ? A(A_GOLD_LO, 90) : G_UNLIT_BOT,
                    A(A_GOLD, lit ? 70 : 34), 1F);
            if (lit) {
                // 保险丝芯：中间一条亮横条 + 两端小点
                c.fill(x + 2, py + 2, PWR_W - 4, 2, A(A_GOLD_HI, 225));
                c.fill(x + 1, py + 2, 1, 2, A(A_AMBER, 200));
                c.fill(x + PWR_W - 2, py + 2, 1, 2, A(A_AMBER, 200));
            }
        }

        // 合成中：整排保险丝随进度整体呼吸一次
        if (crafting) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(time * 2.4F);
            SdfShapes.roundRect(c, x - 1.5F, y - 1.5F, PWR_W + 3F, totalH + 3F, 2.5F,
                    0x00000000, 0x00000000, A(A_AMBER, Math.round(70 * pulse)), 1F);
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
