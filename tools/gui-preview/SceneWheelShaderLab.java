import java.util.List;
import java.util.Locale;

/**
 * 场景：魔法轮盘施法界面 —— 工具私有着色器的实验台（{@code wheel_shader_lab}）。
 *
 * <p>这一帧是「魔法轮盘 + 魔法装载」界面设计稿的着色器验证图，一次演示全部状态：</p>
 *
 * <ul>
 *   <li><b>wheel_aura</b>（加色，下层）：轮盘外的大范围柔光、24 条慢速光束、外圈符文环
 *       （96 格、每 6 格加粗、整体缓慢旋转）、8 根辐条/八芒星暗示，以及 hover 方向的
 *       ±14° 锥形辉光。</li>
 *   <li><b>wheel_slice</b>（普通 alpha，中层）：8 个 45° 扇区共用一支 shader，只靠 uniform
 *       区分状态 —— 0 普通(FIRE) / 1 hover(MAGIC) / 2 selected(GOLD) / 3 冷却 0.55 /
 *       4 locked / 5 普通(DEATH) / 6 普通(VOID) / 7 空位（低 uIntensity）。
 *       hover/selected 外扩 4~7px 并发光，冷却从 start 角顺时针压暗 + 亮扫边 + 内缘刻度，
 *       locked 去饱和偏冷蓝。</li>
 *   <li><b>wheel_core</b>（加色，中心）：多层旋转 sin 噪声亮丝的中心漩涡球、12 点方向顺时针
 *       填充的法力环、逆时针的冷却环、蓄力脉冲环；球下面先画一层 CPU 暗盘 + “76 / MANA”
 *       文字（Lead 的正式场景也是这样垫底的）。</li>
 *   <li>CPU 侧只做框架性的装饰：外圈金环、8 个扇区分界刻度、内环插座圆环、hover 方向菱形
 *       标记，以及暗盘（{@code c.fillMesh} 拼的圆盘）。</li>
 * </ul>
 *
 * <p>背景：{@code c.backdrop()} + 项目 shader {@code magic_starfield} 做出深空，再压一层
 * 半透明暗色让前景跳出来。</p>
 *
 * <p>产物（{@code build/gui-preview/}）：</p>
 * <ul>
 *   <li>{@code wheel_shader_lab.png} —— 主图（render 返回后由 GuiPreview 自动保存）</li>
 *   <li>{@code wheel_shader_lab_t2.png} / {@code wheel_shader_lab_t4.png} —— 时间 +2.35s /
 *       +4.90s 的两帧，用来确认光晕、符文环、漩涡和脉冲确实在动</li>
 * </ul>
 */
public final class SceneWheelShaderLab {

    private SceneWheelShaderLab() {}

    // ══════════════════════════════════════════════════════════════
    //  轮盘几何（GUI 像素；画布默认 640×400）
    // ══════════════════════════════════════════════════════════════

    private static final float CENTER_X = 320.0F;
    private static final float CENTER_Y = 200.0F;
    private static final float OUTER = 150.0F;
    private static final float INNER = 62.0F;
    private static final float CORE_RADIUS = 46.0F;

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float HALF_PI = (float) (Math.PI * 0.5);
    private static final float SECTOR = TAU / 8.0F;

    /** 第 1 扇区中心角（hover）：正上方 -90° 起，45° 一个扇区 → -22.5°。 */
    private static final float HOVER_ANGLE = -HALF_PI + 1.5F * SECTOR;
    /** 第 2 扇区中心角（selected）：+22.5°。 */
    private static final float SELECTED_ANGLE = -HALF_PI + 2.5F * SECTOR;

    /** 每支 shader 的 quad 半边长（逻辑像素）。 */
    private static final int AURA_HALF = 208;    // 416 px：够 1.36×uOuter 的柔光 + 符文环
    private static final int SLICE_HALF = 178;   // 356 px：够 hover/selected 外扩 + 光晕
    private static final int CORE_HALF = 78;     // 156 px：够法力环与脉冲环

    // 中心 core 的演示数值
    private static final float CORE_MANA = 0.76F;
    private static final float CORE_CHARGE = 0.55F;
    private static final float CORE_COOLDOWN = 0.22F;

    // ── 配色 token（见 task-6）──
    private static final int GOLD = 0xFFE7C46A;
    private static final int BRIGHT_GOLD = 0xFFFFE49A;
    private static final int CYAN = 0xFF6FD8C0;

    // ══════════════════════════════════════════════════════════════
    //  8 个扇区：一次演示所有状态
    //  A = uColorA（亮缘/外沿）  B = uColorB（深底/内部）
    // ══════════════════════════════════════════════════════════════

    private record Slice(String name, String state,
                         float aR, float aG, float aB,
                         float bR, float bG, float bB,
                         float hover, float selected, float locked, float cooldown,
                         float intensity, int textColor, int swatch) {
    }

    private static final List<Slice> SLICES = List.of(
            // 0 ── 普通：FIRE 橙金
            new Slice("FIRE", "ready",
                    1.000F, 0.620F, 0.280F, 0.190F, 0.070F, 0.026F,
                    0.0F, 0.0F, 0.0F, 0.0F, 1.00F, 0xFFFFB070, 0xFFFF8A4C),
            // 1 ── hover：MAGIC 青
            new Slice("MAGIC", "hover",
                    0.550F, 0.920F, 1.000F, 0.045F, 0.130F, 0.220F,
                    1.0F, 0.0F, 0.0F, 0.0F, 1.00F, 0xFFD6F4FF, 0xFF66CCFF),
            // 2 ── selected：金（最强外扩 + 辉光）
            new Slice("GOLD", "selected",
                    1.000F, 0.895F, 0.600F, 0.260F, 0.170F, 0.055F,
                    0.0F, 1.0F, 0.0F, 0.0F, 1.00F, 0xFFFFF0C8, 0xFFFFE49A),
            // 3 ── 冷却 60%：FIRE 色但被压暗 + 扫边（专门验证顺时针压暗比例）
            new Slice("FIRE", "cooldown 60%",
                    0.950F, 0.600F, 0.280F, 0.160F, 0.060F, 0.030F,
                    0.0F, 0.0F, 0.0F, 0.60F, 1.00F, 0xFFFFB080, 0xFFFF9A5C),
            // 4 ── locked：去饱和冷蓝
            new Slice("LOCKED", "cold / dim",
                    0.420F, 0.540F, 0.740F, 0.035F, 0.050F, 0.090F,
                    0.0F, 0.0F, 1.0F, 0.0F, 0.95F, 0xFFAFC4E2, 0xFF7A93C4),
            // 5 ── 普通：DEATH 品红
            new Slice("DEATH", "ready",
                    1.000F, 0.500F, 1.000F, 0.115F, 0.025F, 0.160F,
                    0.0F, 0.0F, 0.0F, 0.0F, 1.00F, 0xFFFFA8FF, 0xFFFF7AF5),
            // 6 ── 普通：VOID 紫
            new Slice("VOID", "ready",
                    0.700F, 0.520F, 1.000F, 0.055F, 0.030F, 0.140F,
                    0.0F, 0.0F, 0.0F, 0.0F, 1.00F, 0xFFC6B6FF, 0xFF9A86FF),
            // 7 ── 空位：几乎透明，只剩一点冷灰轮廓
            new Slice("EMPTY", "no spell",
                    0.360F, 0.400F, 0.520F, 0.025F, 0.030F, 0.045F,
                    0.0F, 0.0F, 0.0F, 0.0F, 0.16F, 0xFF8A93A6, 0xFF6C7688)
    );

    // ══════════════════════════════════════════════════════════════
    //  入口：主图 + 两帧时间对照
    // ══════════════════════════════════════════════════════════════

    public static void render(PreviewCanvas c, float time) {
        // 先存两帧「时间前移」的对照图，证明 aura/core 的动画真的在动
        drawFrame(c, time + 2.35F, "frame t+" + fmt(2.35F));
        c.save("wheel_shader_lab_t2");
        drawFrame(c, time + 4.90F, "frame t+" + fmt(4.90F));
        c.save("wheel_shader_lab_t4");
        // 最后一帧留给 GuiPreview 自动存成 wheel_shader_lab.png（主图）
        drawFrame(c, time, "MAIN");
    }

    // ══════════════════════════════════════════════════════════════
    //  一帧完整画面
    // ══════════════════════════════════════════════════════════════

    private static void drawFrame(PreviewCanvas c, float time, String frameTag) {
        int width = c.width;
        int height = c.height;

        // ── 背景：深空（项目 magic_starfield）+ 半透明暗色压一层 ──
        c.clear(0xFF02040A);
        c.backdrop();
        PreviewShader starfield = c.shader("magic_starfield");
        starfield.set("uTime", time);
        starfield.set("uSize", (float) width, (float) height);
        starfield.set("uMouse", 0.18F, -0.10F);
        starfield.set("uIntensity", 0.95F);
        c.shaderQuad(starfield, 0, 0, width, height);
        c.fill(0, 0, width, height, 0x8A050810);

        // ── 图层顺序：aura（加色，最底）→ 8 扇区（alpha）→ CPU 装饰 → core → 文字 ──
        drawAura(c, time);
        drawSlices(c, time);
        drawCpuRings(c, time);
        drawCore(c, time);

        drawTitleAndLegend(c, time, frameTag);
    }

    // ══════════════════════════════════════════════════════════════
    //  wheel_aura：加色柔光 / 光束 / 符文环 / 辐条 / hover 锥
    // ══════════════════════════════════════════════════════════════

    private static void drawAura(PreviewCanvas c, float time) {
        int x = (int) CENTER_X - AURA_HALF;
        int y = (int) CENTER_Y - AURA_HALF;
        int size = AURA_HALF * 2;

        PreviewShader aura = c.shader("wheel_aura");
        aura.set("uCenter", CENTER_X, CENTER_Y);
        aura.set("uQuadOrigin", (float) x, (float) y);
        aura.set("uQuadSize", (float) size, (float) size);
        aura.set("uOuter", OUTER);
        aura.set("uInner", INNER);
        aura.set("uTime", time);
        aura.set("uHoverAngle", HOVER_ANGLE);
        aura.set("uHoverStrength", 0.85F);
        aura.set("uIntensity", 1.0F);
        aura.set("uColorA", 0.905F, 0.770F, 0.420F, 1.0F);   // 金 #E7C46A
        aura.set("uColorB", 0.435F, 0.847F, 0.753F, 1.0F);   // 青 #6FD8C0
        aura.set("uColorC", 0.470F, 0.320F, 0.900F, 1.0F);   // 紫（提亮版紫雾）
        c.shaderQuad(aura, x, y, size, size);
    }

    // ══════════════════════════════════════════════════════════════
    //  wheel_slice：8 个扇区，一支 shader 只换 uniform
    // ══════════════════════════════════════════════════════════════

    private static void drawSlices(PreviewCanvas c, float time) {
        int x = (int) CENTER_X - SLICE_HALF;
        int y = (int) CENTER_Y - SLICE_HALF;
        int size = SLICE_HALF * 2;

        PreviewShader slice = c.shader("wheel_slice");
        for (int i = 0; i < SLICES.size(); i++) {
            Slice s = SLICES.get(i);
            float start = -HALF_PI + i * SECTOR;
            float end = start + SECTOR;

            slice.set("uCenter", CENTER_X, CENTER_Y);
            slice.set("uQuadOrigin", (float) x, (float) y);
            slice.set("uQuadSize", (float) size, (float) size);
            slice.set("uRadius", OUTER);
            slice.set("uInnerRadius", INNER);
            slice.set("uStartAngle", start);
            slice.set("uEndAngle", end);
            slice.set("uColorA", s.aR(), s.aG(), s.aB(), 1.0F);
            slice.set("uColorB", s.bR(), s.bG(), s.bB(), 1.0F);
            slice.set("uTime", time);
            slice.set("uHover", s.hover());
            slice.set("uSelected", s.selected());
            slice.set("uLocked", s.locked());
            slice.set("uCooldown", s.cooldown());
            slice.set("uIntensity", s.intensity());
            c.shaderQuad(slice, x, y, size, size);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  CPU 装饰：外圈金环 + 扇区分界刻度 + 内环 + hover 菱形
    //  （fillMesh 顶点布局：x, y, r, g, b, a）
    // ══════════════════════════════════════════════════════════════

    private static void drawCpuRings(PreviewCanvas c, float time) {
        ColorMesh deco = new ColorMesh();

        // 外圈：一条细金环把扇区外缘和符文环隔开
        addRingArc(deco, CENTER_X, CENTER_Y, OUTER + 2.2F, OUTER + 3.05F,
                0.0F, TAU, rgba(0x99E7C46A), 128);

        // 8 个扇区分界刻度（短而暗，指向外侧）
        for (int i = 0; i < 8; i++) {
            float angle = -HALF_PI + i * SECTOR;
            addPolarTick(deco, CENTER_X, CENTER_Y, angle,
                    OUTER + 4.2F, OUTER + 9.0F, 0.55F, rgba(0x88E7C46A));
        }

        // 内环插座：轮盘内孔边缘的一圈暖金细线
        addRingArc(deco, CENTER_X, CENTER_Y, INNER - 2.4F, INNER - 1.5F,
                0.0F, TAU, rgba(0x77E7C46A), 96);

        // hover 方向的菱形标记（和 aura 的锥形辉光同角度，呼吸缩放）
        float pulse = 1.0F + 0.18F * mthSin(time * 2.2F);
        for (int k = 0; k < 2; k++) {
            float angle = HOVER_ANGLE + (k == 0 ? -0.075F : 0.075F);
            float radius = OUTER + 15.5F;
            float x = CENTER_X + mthCos(angle) * radius;
            float y = CENTER_Y + mthSin(angle) * radius;
            addDiamond(deco, x, y, 2.3F * pulse, rgba(0xCCFFE49A));
        }

        deco.flush(c);
    }

    // ══════════════════════════════════════════════════════════════
    //  wheel_core：中心暗盘 + 文字 + 加色漩涡球 / 法力环 / 冷却环 / 脉冲
    // ══════════════════════════════════════════════════════════════

    private static void drawCore(PreviewCanvas c, float time) {
        // 先画中心暗盘（Lead 的正式场景也会在 core 下面先垫一层暗色圆盘）
        ColorMesh disc = new ColorMesh();
        addDisc(disc, CENTER_X, CENTER_Y, CORE_RADIUS + 7.0F, 64,
                rgba(0xFF0B111E), rgba(0xFF070B14), rgba(0x00040812));
        disc.flush(c);

        // 暗盘上的法力读数：文字在 core shader 下面，会被加色光球“点亮”
        textCenteredScaled(c, "76", (int) CENTER_X, (int) (CENTER_Y + 18.0F),
                0xFFE6F0FF, true, 2.0F);
        textCentered(c, "MANA", (int) CENTER_X, (int) (CENTER_Y + 38.0F),
                0xFFBFD8FF, true);

        int x = (int) CENTER_X - CORE_HALF;
        int y = (int) CENTER_Y - CORE_HALF;
        int size = CORE_HALF * 2;

        PreviewShader core = c.shader("wheel_core");
        core.set("uCenter", CENTER_X, CENTER_Y);
        core.set("uQuadOrigin", (float) x, (float) y);
        core.set("uQuadSize", (float) size, (float) size);
        core.set("uCoreRadius", CORE_RADIUS);
        core.set("uTime", time);
        core.set("uMana", CORE_MANA);
        core.set("uCharge", CORE_CHARGE);
        core.set("uCooldown", CORE_COOLDOWN);
        // 和 WheelKit 的调用约定一致：uColorA = 流派主色，uColorB = 金色 chrome
        core.set("uColorA", 1.000F, 0.420F, 0.180F, 1.0F);   // FIRE 流派色
        core.set("uColorB", 1.000F, 0.894F, 0.604F, 1.0F);   // 亮金 #FFE49A
        core.set("uIntensity", 1.0F);
        c.shaderQuad(core, x, y, size, size);
    }

    // ══════════════════════════════════════════════════════════════
    //  标题 / 左侧状态图例 / 右侧 uniform 说明 / 底部说明
    // ══════════════════════════════════════════════════════════════

    private static void drawTitleAndLegend(PreviewCanvas c, float time, String frameTag) {
        int width = c.width;

        // ── 顶部小字 ──
        c.text("OFFLINE GUI PREVIEW", 8, 6, 0xFF55617A, false);
        c.textRight("640 x 400 @ 2x", width - 8, 6, 0xFF55617A, false);

        // ── 标题（放大 2 倍）+ 副标题 ──
        textCenteredScaled(c, "ARCANE WHEEL", 320, 6, 0xFFFFE49A, true, 2.0F);
        int titleW = c.textWidth("ARCANE WHEEL") * 2;
        c.fill(320 - titleW / 2, 26, titleW, 1, 0x66FFE49A);
        textCentered(c, "SHADER LAB  -  wheel_slice / wheel_aura / wheel_core", 320, 31,
                0xFFAEBBD6, true);

        // ── 左侧：8 扇区状态图例 ──
        c.text("SECTOR STATES", 10, 56, 0xFF7C8AA8, false);
        for (int i = 0; i < SLICES.size(); i++) {
            Slice s = SLICES.get(i);
            int rowY = 68 + i * 11;
            c.fill(10, rowY + 1, 3, 5, s.swatch());
            String line = String.format(Locale.ROOT, "%d %-6s %s", i, s.name(), s.state());
            c.text(line, 17, rowY, 0xFF000000 | (s.swatch() & 0xFFFFFF), false);
        }

        // ── 右侧：三支 shader / 关键 uniform ──
        String[] info = {
                "THREE TOOL SHADERS",
                "wheel_slice   srcalpha",
                "wheel_aura    additive",
                "wheel_core    additive",
                "uMana " + fmt(CORE_MANA) + "  uCharge " + fmt(CORE_CHARGE),
                "uCooldown " + fmt(CORE_COOLDOWN),
                "aura quad 416 px",
                "core quad 156 px",
                "t = " + fmt(time) + " s"
        };
        for (int i = 0; i < info.length; i++) {
            int rowY = 56 + i * 12;
            int color = i == 0 ? 0xFF7C8AA8 : 0xFF93A2C0;
            c.textRight(info[i], width - 10, rowY, color, false);
        }

        // ── 底部：状态/实现提示（英文 ASCII，位图字体只有拉丁）──
        textCentered(c,
                "hover / selected: outer lift + breathing glow   |   cooldown: clockwise sweep   |   locked: cold desaturate",
                320, 358, 0xFF8C9AB8, false);
        textCentered(c,
                "aura: bloom + 24 beams + rune ring + 8 spokes   core: swirl orb + mana/cooldown arcs + charge pulse",
                320, 370, 0xFF6E7B96, false);

        // ── 帧标记（右下角）──
        c.textRight(frameTag, width - 10, 386, 0xFF4E5A72, false);
    }

    // ══════════════════════════════════════════════════════════════
    //  CPU 网格工具（fillMesh：x, y, r, g, b, a；颜色 0..1）
    // ══════════════════════════════════════════════════════════════

    /** 可增长的 6-float 顶点缓冲，最后一次性交给 {@link PreviewCanvas#fillMesh}。 */
    private static final class ColorMesh {
        private float[] data = new float[1 << 14];
        private int size;

        void vertex(float x, float y, float[] color) {
            if (size + 6 > data.length) {
                float[] grown = new float[data.length * 2];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = x;
            data[size++] = y;
            data[size++] = color[0];
            data[size++] = color[1];
            data[size++] = color[2];
            data[size++] = color[3];
        }

        void flush(PreviewCanvas c) {
            if (size > 0) {
                c.fillMesh(data, size, size / 6);
                size = 0;
            }
        }
    }

    /** 圆环扇段（r0..r1，角度 a0..a1，弧度）。 */
    private static void addRingArc(ColorMesh mesh, float cx, float cy,
                                   float r0, float r1, float a0, float a1,
                                   float[] color, int segments) {
        for (int i = 0; i < segments; i++) {
            float t0 = a0 + (a1 - a0) * i / segments;
            float t1 = a0 + (a1 - a0) * (i + 1) / segments;
            float c0 = mthCos(t0);
            float s0 = mthSin(t0);
            float c1 = mthCos(t1);
            float s1 = mthSin(t1);

            float ix0 = cx + c0 * r0, iy0 = cy + s0 * r0;
            float ox0 = cx + c0 * r1, oy0 = cy + s0 * r1;
            float ix1 = cx + c1 * r0, iy1 = cy + s1 * r0;
            float ox1 = cx + c1 * r1, oy1 = cy + s1 * r1;

            mesh.vertex(ix0, iy0, color);
            mesh.vertex(ox0, oy0, color);
            mesh.vertex(ox1, oy1, color);

            mesh.vertex(ix0, iy0, color);
            mesh.vertex(ox1, oy1, color);
            mesh.vertex(ix1, iy1, color);
        }
    }

    /** 极坐标下的径向刻度（从 r0 到 r1，宽度 halfWidthPx 像素）。 */
    private static void addPolarTick(ColorMesh mesh, float cx, float cy, float angle,
                                     float r0, float r1, float halfWidthPx, float[] color) {
        float midR = (r0 + r1) * 0.5F;
        float da = halfWidthPx / Math.max(midR, 1.0F);
        float a0 = angle - da;
        float a1 = angle + da;

        float ix0 = cx + mthCos(a0) * r0, iy0 = cy + mthSin(a0) * r0;
        float ox0 = cx + mthCos(a0) * r1, oy0 = cy + mthSin(a0) * r1;
        float ox1 = cx + mthCos(a1) * r1, oy1 = cy + mthSin(a1) * r1;
        float ix1 = cx + mthCos(a1) * r0, iy1 = cy + mthSin(a1) * r0;

        mesh.vertex(ix0, iy0, color);
        mesh.vertex(ox0, oy0, color);
        mesh.vertex(ox1, oy1, color);

        mesh.vertex(ix0, iy0, color);
        mesh.vertex(ox1, oy1, color);
        mesh.vertex(ix1, iy1, color);
    }

    /** 菱形标记（四角朝上下左右）。 */
    private static void addDiamond(ColorMesh mesh, float cx, float cy, float radius,
                                   float[] color) {
        mesh.vertex(cx, cy - radius, color);
        mesh.vertex(cx + radius, cy, color);
        mesh.vertex(cx, cy + radius, color);

        mesh.vertex(cx, cy - radius, color);
        mesh.vertex(cx, cy + radius, color);
        mesh.vertex(cx - radius, cy, color);
    }

    /**
     * 暗色圆盘：中心色 → 0.78R 中间色 → R 处淡出到透明，两圈三角带拼成一个软边圆盘。
     */
    private static void addDisc(ColorMesh mesh, float cx, float cy, float radius,
                                int segments, float[] center, float[] mid, float[] edge) {
        for (int i = 0; i < segments; i++) {
            float t0 = (float) (i * TAU / segments);
            float t1 = (float) ((i + 1) * TAU / segments);
            float c0 = mthCos(t0);
            float s0 = mthSin(t0);
            float c1 = mthCos(t1);
            float s1 = mthSin(t1);

            float mx0 = cx + c0 * radius * 0.78F, my0 = cy + s0 * radius * 0.78F;
            float mx1 = cx + c1 * radius * 0.78F, my1 = cy + s1 * radius * 0.78F;
            float ex0 = cx + c0 * radius, ey0 = cy + s0 * radius;
            float ex1 = cx + c1 * radius, ey1 = cy + s1 * radius;

            // 内带：中心 → 0.78R
            mesh.vertex(cx, cy, center);
            mesh.vertex(mx0, my0, mid);
            mesh.vertex(mx1, my1, mid);

            // 外带：0.78R → R（alpha 收到 0，边缘自然消失）
            mesh.vertex(mx0, my0, mid);
            mesh.vertex(ex0, ey0, edge);
            mesh.vertex(ex1, ey1, edge);
            mesh.vertex(mx0, my0, mid);
            mesh.vertex(ex1, ey1, edge);
            mesh.vertex(mx1, my1, mid);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  文字辅助（长行切段，避免 MemoryStack 64KB 溢出）
    // ══════════════════════════════════════════════════════════════

    private static void textCentered(PreviewCanvas c, String text, int centerX, int y,
                                     int argb, boolean shadow) {
        drawChunked(c, text, centerX - c.textWidth(text) / 2, y, argb, shadow);
    }

    private static void textCenteredScaled(PreviewCanvas c, String text, int centerX, int y,
                                           int argb, boolean shadow, float scale) {
        float width = c.textWidth(text) * scale;
        c.textScaled(text, centerX - width * 0.5F, y, argb, shadow, scale);
    }

    private static void drawChunked(PreviewCanvas c, String text, int x, int y,
                                    int argb, boolean shadow) {
        int penX = x;
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + 16);
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end - 1);
                if (space > i) {
                    end = space + 1;
                }
            }
            String chunk = text.substring(i, end);
            c.text(chunk, penX, y, argb, shadow);
            penX += c.textWidth(chunk);
            i = end;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  小工具
    // ══════════════════════════════════════════════════════════════

    /** ARGB int → fillMesh 用的 0..1 浮点 RGBA。 */
    private static float[] rgba(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                ((argb >>> 24) & 0xFF) / 255.0F};
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static float mthSin(float v) {
        return (float) Math.sin(v);
    }

    private static float mthCos(float v) {
        return (float) Math.cos(v);
    }
}
