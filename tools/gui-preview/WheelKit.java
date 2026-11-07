import java.util.List;
import java.util.Locale;

/**
 * 魔法轮盘（Spell Wheel）的共享渲染器与数据模型 —— 施法 HUD 与装载界面共用同一套视觉。
 *
 * <p>这份类放在基础编译单元里（见 {@code gui_preview.ps1} 的 baseSources），
 * 所以 {@code SceneSpellWheel} 与 {@code SceneSpellLoadout} 都能用。</p>
 *
 * <h3>设计语义（与 docs/spell-wheel-design.md 对齐）</h3>
 * <ul>
 *   <li>轮盘固定 <b>8 个方位</b>（正上方 = 位 1，顺时针 1..8）；</li>
 *   <li><b>只显示主动法术</b>：轮盘数组里的槽位只能放 {@link Spell#active()} 为 true 的法术，
 *       被动法术没有轮盘位；</li>
 *   <li>空位画成很淡的符文插槽（HUD 里可关闭），不显示任何技能信息；</li>
 *   <li>每个扇区有 hover / selected / cooldown / locked（蓝不够）四种状态。</li>
 * </ul>
 *
 * <h3>为什么分"设计着色器 + CPU 兜底"</h3>
 * <p>轮盘主视觉由 {@code tools/gui-preview/shaders/wheel_slice|wheel_aura|wheel_core}
 * 三支工具私有着色器画；它们不存在时（比如只拉了仓库没跑过设计稿）退化成 CPU 画的分层弧线，
 * 形状对了只是没有那种流动质感 —— 不会直接空白。</p>
 */
final class WheelKit {

    private WheelKit() {
    }

    // ══════════════════════════════════════════════════════════════
    //  设计 token
    // ══════════════════════════════════════════════════════════════

    static final int GOLD = 0xFFE7C46A;
    static final int GOLD_BRIGHT = 0xFFFFE49A;
    static final int GOLD_DIM = 0x99C9A227;
    static final int INK = 0xFF07101D;
    static final int DEEP = 0xFF02040A;
    static final int HAZE = 0xFF2A1B4D;
    static final int TEAL = 0xFF6FD8C0;
    static final int TEXT = 0xFFE8EEFF;
    static final int TEXT_DIM = 0xFF8FA3C8;
    static final int TEXT_FAINT = 0xFF5C6C8C;
    static final int WARN = 0xFFFF8A5C;
    static final int LOCKED = 0xFF6C86B8;

    static final float[] GOLD_RGBA = {0.906F, 0.769F, 0.416F, 1F};
    static final float[] GOLD_BRIGHT_RGBA = {1.0F, 0.894F, 0.604F, 1F};
    static final float[] TEAL_RGBA = {0.435F, 0.847F, 0.753F, 1F};
    static final float[] HAZE_RGBA = {0.165F, 0.106F, 0.302F, 1F};
    /** 空插槽的冷灰蓝（刻意不用金色：空位不该和已绑定的法术抢眼）。 */
    static final float[] EMPTY_SLOT_RGBA = {0.30F, 0.38F, 0.56F, 1F};

    /** 8 个方位；与 {@link #slotAngle(int)} 一致。 */
    static final int SLOTS = 8;
    static final double TAU = Math.PI * 2.0;

    /** 轮盘半径（640×400 逻辑画布上的默认值）。 */
    static final float DEFAULT_OUTER = 142F;
    static final float DEFAULT_INNER = 58F;

    // ── 流派色（与设计文档一致）──
    static final float[] FIRE_C = {1.00F, 0.42F, 0.18F};
    static final float[] DEATH_C = {0.85F, 0.30F, 0.95F};
    static final float[] VOID_C = {0.55F, 0.35F, 1.00F};
    static final float[] MAGIC_C = {0.40F, 0.80F, 1.00F};
    static final float[] NATURE_C = {0.42F, 0.90F, 0.50F};
    static final float[] LIGHT_C = {1.00F, 0.90F, 0.55F};
    static final float[] ACE_C = {0.92F, 0.94F, 1.00F};

    // ══════════════════════════════════════════════════════════════
    //  法术数据（按项目真实数值 + 规划中的被动示例）
    // ══════════════════════════════════════════════════════════════

    /**
     * 一件法术。字段与项目 {@code IMagicBaseItem} 的语义对齐：
     * {@code school} = MagicType、{@code kind} = SpellKind、{@code using} = UsingType。
     */
    record Spell(String id, String name, String school, String kind, String using,
                 int mana, int cooldownTicks, int chargeTicks,
                 String icon, String meta, String lore, float[] color) {

        boolean active() {
            return "ACTIVE".equals(kind);
        }

        /** 冷却秒数（展示用）。 */
        float cooldownSeconds() {
            return cooldownTicks / 20.0F;
        }

        /** 英文流派名（界面里就用它，MC 位图字体只支持拉丁）。 */
        String schoolLabel() {
            return school;
        }
    }

    static final Spell FIREBALL = new Spell(
            "fire_ball", "Fireball", "FIRE", "ACTIVE", "INSTANT",
            40, 30, 0,
            "textures/item/fire_ball.png",
            "INSTANT  |  40 MANA  |  1.5s COOLDOWN",
            "A roaring orb that detonates on impact.",
            FIRE_C);

    static final Spell SPEAR = new Spell(
            "spear_of_judgment", "Spear of Judgment", "DEATH", "ACTIVE", "CHARGE 2.0s",
            100, 600, 40,
            "textures/item/spear_of_judgment.png",
            "CHARGE 2.0s  |  100 MANA  |  30s COOLDOWN",
            "Hold to gather judgment, then release; the spear falls from above.",
            DEATH_C);

    static final Spell SUPPRESSION = new Spell(
            "suppression_field", "Suppression Field", "VOID", "ACTIVE", "CHANNEL",
            80, 240, 0,
            "textures/item/suppression_field.png",
            "CHANNEL  |  80 MANA + 50/s  |  12s COOLDOWN",
            "A crushing domain that drags the air itself down.",
            VOID_C);

    /**
     * 被动示例 —— <b>规划中的框架内容</b>（项目目前只有 {@code SpellKind.PASSIVE} 分类轴，
     * 还没有被动实现）。装载界面用它演示"被动不可进轮盘"这条规则。
     */
    static final Spell EMBER_AFFINITY = new Spell(
            "ember_affinity", "Ember Affinity", "FIRE", "PASSIVE", "-",
            0, 0, 0,
            "textures/item/fire_mana_plate.png",
            "PASSIVE  |  always active",
            "Fire spells cost slightly less. Passive spells never appear on the wheel.",
            FIRE_C);

    static final Spell VOID_ATTUNEMENT = new Spell(
            "void_attunement", "Void Attunement", "VOID", "PASSIVE", "-",
            0, 0, 0,
            "textures/item/void_mana_plate.png",
            "PASSIVE  |  always active",
            "Void spells linger a little longer. Passive spells never appear on the wheel.",
            VOID_C);

    static final List<Spell> ACTIVE_SPELLS = List.of(FIREBALL, SPEAR, SUPPRESSION);
    static final List<Spell> PASSIVE_SPELLS = List.of(EMBER_AFFINITY, VOID_ATTUNEMENT);

    /** 默认轮盘绑定：前三个方位放现有主动法术，其余空置。 */
    static Spell[] defaultWheel() {
        Spell[] wheel = new Spell[SLOTS];
        wheel[0] = FIREBALL;
        wheel[1] = SPEAR;
        wheel[2] = SUPPRESSION;
        return wheel;
    }

    // ══════════════════════════════════════════════════════════════
    //  颜色 / 几何小工具
    // ══════════════════════════════════════════════════════════════

    static int argb(float[] rgb, float alpha) {
        int a = clamp255(alpha * 255F);
        return (a << 24) | (clamp255(rgb[0] * 255F) << 16)
                | (clamp255(rgb[1] * 255F) << 8) | clamp255(rgb[2] * 255F);
    }

    static int argb(float[] rgb, int alpha) {
        return (alpha << 24) | (clamp255(rgb[0] * 255F) << 16)
                | (clamp255(rgb[1] * 255F) << 8) | clamp255(rgb[2] * 255F);
    }

    static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    static int mix(int c1, int c2, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 0..1 浮点颜色分量 → 0..255（只做钳制+取整，不再乘 255）。 */
    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0F, Math.min(1F, t));
    }

    /** 方位 i 的中心角（弧度，屏幕坐标：0=右、π/2=下；位 0 = 正上方）。 */
    static float slotAngle(int i) {
        return (float) (-Math.PI / 2.0 + i * TAU / SLOTS);
    }

    static float slotStart(int i) {
        return slotAngle(i) - (float) (TAU / SLOTS / 2.0);
    }

    static float slotEnd(int i) {
        return slotAngle(i) + (float) (TAU / SLOTS / 2.0);
    }

    static float slotX(float cx, float radius, int i) {
        return cx + (float) Math.cos(slotAngle(i)) * radius;
    }

    static float slotY(float cy, float radius, int i) {
        return cy + (float) Math.sin(slotAngle(i)) * radius;
    }

    /** 鼠标位置落在哪个方位（-1 表示离轮盘中心太近/太远，不算选中）。 */
    static int slotAt(float mx, float my, float cx, float cy, float inner, float outer) {
        float dx = mx - cx;
        float dy = my - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < inner * 0.55F || dist > outer * 1.18F) {
            return -1;
        }
        double angle = Math.atan2(dy, dx);
        // 以正上方为 0，顺时针折算
        double relative = angle + Math.PI / 2.0;
        double step = TAU / SLOTS;
        int index = (int) Math.round(relative / step);
        return Math.floorMod(index, SLOTS);
    }

    // ══════════════════════════════════════════════════════════════
    //  基础绘制（都走 PreviewCanvas.fillMesh，顶点会吃 2D 变换）
    // ══════════════════════════════════════════════════════════════

    static void fillDisc(PreviewCanvas c, float cx, float cy, float radius, int argb) {
        if (SdfShapes.disc(c, cx, cy, radius, argb, argb)) {
            return;
        }
        fillArc(c, cx, cy, 0F, radius, 0F, (float) TAU, argb, Math.max(24, (int) (radius * 0.9F)));
    }

    static void fillArc(PreviewCanvas c, float cx, float cy,
                        float r0, float r1, float a0, float a1, int argb, int segments) {
        if (SdfShapes.wedge(c, cx, cy, r0, r1, a0, a1, argb, argb)) {
            return;
        }
        float[] a = colorOf(argb);
        fillArcGradient(c, cx, cy, r0, r1, a0, a1, a, a, segments);
    }

    static void fillRing(PreviewCanvas c, float cx, float cy, float radius, float halfWidth,
                         int argb, int segments) {
        if (SdfShapes.ring(c, cx, cy, radius - halfWidth, radius + halfWidth,
                argb, argb, false)) {
            return;
        }
        fillArc(c, cx, cy, radius - halfWidth, radius + halfWidth,
                0F, (float) TAU, argb, segments);
    }

    /** 环形扇区：内缘/外缘颜色可以不同（做出径向渐变感）。 */
    static void fillArcGradient(PreviewCanvas c, float cx, float cy,
                                float r0, float r1, float a0, float a1,
                                float[] inner, float[] outer, int segments) {
        if (segments < 1 || r1 <= 0) {
            return;
        }
        float[] buf = new float[segments * 6 * 6];
        int o = 0;
        for (int s = 0; s < segments; s++) {
            float t0 = s / (float) segments;
            float t1 = (s + 1) / (float) segments;
            float ang0 = lerp(a0, a1, t0);
            float ang1 = lerp(a0, a1, t1);
            float c0 = (float) Math.cos(ang0), s0 = (float) Math.sin(ang0);
            float c1 = (float) Math.cos(ang1), s1 = (float) Math.sin(ang1);
            o = quad(buf, o,
                    cx + c0 * r0, cy + s0 * r0, inner,
                    cx + c0 * r1, cy + s0 * r1, outer,
                    cx + c1 * r1, cy + s1 * r1, outer,
                    cx + c1 * r0, cy + s1 * r0, inner);
        }
        c.fillMesh(buf, o, segments * 6);
    }

    private static int quad(float[] buf, int o,
                            float x0, float y0, float[] c0,
                            float x1, float y1, float[] c1,
                            float x2, float y2, float[] c2,
                            float x3, float y3, float[] c3) {
        o = vert(buf, o, x0, y0, c0);
        o = vert(buf, o, x1, y1, c1);
        o = vert(buf, o, x2, y2, c2);
        o = vert(buf, o, x0, y0, c0);
        o = vert(buf, o, x2, y2, c2);
        return vert(buf, o, x3, y3, c3);
    }

    private static int vert(float[] buf, int o, float x, float y, float[] color) {
        buf[o++] = x;
        buf[o++] = y;
        buf[o++] = color[0];
        buf[o++] = color[1];
        buf[o++] = color[2];
        buf[o++] = color.length > 3 ? color[3] : 1F;
        return o;
    }

    static float[] colorOf(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255F,
                ((argb >> 8) & 0xFF) / 255F,
                (argb & 0xFF) / 255F,
                ((argb >>> 24) & 0xFF) / 255F};
    }

    static void line(PreviewCanvas c, float x1, float y1, float x2, float y2,
                     float halfWidth, int argb) {
        if (SdfShapes.line(c, x1, y1, x2, y2, halfWidth, argb)) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001F) {
            return;
        }
        float nx = -dy / len * halfWidth;
        float ny = dx / len * halfWidth;
        float[] col = colorOf(argb);
        float[] buf = new float[36];
        int o = 0;
        o = vert(buf, o, x1 - nx, y1 - ny, col);
        o = vert(buf, o, x1 + nx, y1 + ny, col);
        o = vert(buf, o, x2 + nx, y2 + ny, col);
        o = vert(buf, o, x1 - nx, y1 - ny, col);
        o = vert(buf, o, x2 + nx, y2 + ny, col);
        o = vert(buf, o, x2 - nx, y2 - ny, col);
        c.fillMesh(buf, o, 6);
    }

    /** 用点集拼多边形（凸多边形 / 星形；顶点按顺序给出）。 */
    static void fillPolygon(PreviewCanvas c, float[] xs, float[] ys, int count, int argb) {
        if (count < 3) {
            return;
        }
        float[] col = colorOf(argb);
        float[] buf = new float[(count - 2) * 18];
        int o = 0;
        for (int i = 1; i + 1 < count; i++) {
            o = vert(buf, o, xs[0], ys[0], col);
            o = vert(buf, o, xs[i], ys[i], col);
            o = vert(buf, o, xs[i + 1], ys[i + 1], col);
        }
        c.fillMesh(buf, o, (count - 2) * 3);
    }

    /** 星形/花瓣（n 个尖，内外半径交替）。 */
    static void fillStar(PreviewCanvas c, float cx, float cy, float rOuter, float rInner,
                         int points, float rotation, int argb) {
        if (SdfShapes.star(c, cx, cy, rOuter, rInner, points, rotation, argb)) {
            return;
        }
        int n = points * 2;
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            double ang = rotation + i * TAU / n;
            float r = (i % 2 == 0) ? rOuter : rInner;
            xs[i] = cx + (float) Math.cos(ang) * r;
            ys[i] = cy + (float) Math.sin(ang) * r;
        }
        // 星形是凹多边形，用扇形三角化
        float[] col = colorOf(argb);
        float[] buf = new float[n * 18];
        int o = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            o = vert(buf, o, cx, cy, col);
            o = vert(buf, o, xs[i], ys[i], col);
            o = vert(buf, o, xs[j], ys[j], col);
        }
        c.fillMesh(buf, o, n * 3);
    }

    // ══════════════════════════════════════════════════════════════
    //  符文 / 徽记 / 图标 / 卡片
    // ══════════════════════════════════════════════════════════════

    /** 程序化小符文（确定性 hash，不依赖随机源）：空槽位和学派徽记都用它。 */
    static void runeGlyph(PreviewCanvas c, float cx, float cy, float size, int seed,
                          int argb, float alpha) {
        int col = withAlpha(argb, clamp255(alpha * ((argb >>> 24) & 0xFF)));
        int s = seed & 0x7FFFFFFF;
        int strokes = 3 + (s % 3);
        for (int i = 0; i < strokes; i++) {
            s = s * 1103515245 + 12345;
            float ax = (((s >> 8) & 0xFF) / 255F - 0.5F) * size;
            float ay = (((s >> 16) & 0xFF) / 255F - 0.5F) * size;
            s = s * 1103515245 + 12345;
            float bx = (((s >> 8) & 0xFF) / 255F - 0.5F) * size;
            float by = (((s >> 16) & 0xFF) / 255F - 0.5F) * size;
            line(c, cx + ax, cy + ay, cx + bx, cy + by, 0.7F, col);
        }
        // 中心一点
        fillDisc(c, cx, cy, 1.2F, col);
    }

    /** 法术图标（16×16 最近邻放大），带暗色圆底与学派色描边。 */
    static void drawSpellIcon(PreviewCanvas c, Spell spell, float cx, float cy,
                              float iconSize, float alpha, float glow) {
        int rim = argb(spell.color, 0.85F * alpha);
        int rimSoft = argb(spell.color, 0.25F * alpha);
        fillDisc(c, cx, cy, iconSize * 0.66F, withAlpha(DEEP, clamp255(alpha * 210)));
        fillRing(c, cx, cy, iconSize * 0.62F, 1.0F, rimSoft, 28);
        if (glow > 0.01F) {
            for (int i = 3; i >= 1; i--) {
                fillRing(c, cx, cy, iconSize * 0.62F + i * 2.2F, 0.8F,
                        argb(spell.color, 0.05F * glow * alpha), 28);
            }
        }
        fillRing(c, cx, cy, iconSize * 0.62F, 1.0F, rim, 28);
        c.pushTransform();
        c.translate(cx - iconSize * 0.5F, cy - iconSize * 0.5F);
        c.scale(iconSize / 16F, iconSize / 16F);
        c.blit(c.texture(spell.icon()), 0, 0, 16, 16, 0F, 0F, 1F, 1F,
                withAlpha(TEXT, clamp255(alpha * 255)));
        c.popTransform();
    }

    /** 学派徽记（轮到中心空白状态 / 详情面板用）：六芒星 + 圆环 + 符文。 */
    static void drawSchoolSigil(PreviewCanvas c, Spell spell, float cx, float cy,
                                float radius, float time, float alpha) {
        int color = argb(spell.color, 0.9F * alpha);
        int faint = argb(spell.color, 0.18F * alpha);
        fillRing(c, cx, cy, radius, 0.8F, faint, 40);
        fillStar(c, cx, cy, radius * 0.82F, radius * 0.42F, 6,
                time * 0.15F, argb(spell.color, 0.10F * alpha));
        fillStar(c, cx, cy, radius * 0.62F, radius * 0.30F, 4,
                -time * 0.22F + 0.4F, faint);
        runeGlyph(c, cx, cy, radius * 0.9F, spell.id().hashCode(), color, alpha * 0.8F);
        c.textCentered(spell.schoolLabel(), Math.round(cx),
                Math.round(cy + radius * 0.55F), withAlpha(TEXT_DIM, clamp255(alpha * 255)), false);
    }

    /** 未选中时的中性轮盘徽记（金 + 青，不绑定某个流派）。 */
    static void drawNeutralSigil(PreviewCanvas c, float cx, float cy, float radius,
                                 float time, float alpha) {
        int goldFaint = withAlpha(GOLD, clamp255(alpha * 65));
        fillRing(c, cx, cy, radius, 0.8F, goldFaint, 44);
        fillRing(c, cx, cy, radius * 0.72F, 0.5F, withAlpha(TEAL, clamp255(alpha * 55)), 36);
        fillStar(c, cx, cy, radius * 0.78F, radius * 0.36F, 8, time * 0.08F,
                withAlpha(GOLD, clamp255(alpha * 38)));
        fillStar(c, cx, cy, radius * 0.50F, radius * 0.24F, 4, -time * 0.12F + 0.4F,
                withAlpha(TEAL, clamp255(alpha * 62)));
        runeGlyph(c, cx, cy, radius * 0.8F, 0x5EED,
                withAlpha(GOLD, clamp255(alpha * 210)), alpha * 0.75F);
        for (int i = 3; i >= 1; i--) {
            fillDisc(c, cx, cy, i * 1.6F, withAlpha(GOLD_BRIGHT, clamp255(alpha * 16)));
        }
    }

    /** 键位徽章：暗底 + 金边 + 数字（1..8）。 */
    static void keyBadge(PreviewCanvas c, float cx, float cy, int number, float alpha) {
        int a = clamp255(alpha * 255);
        fillDisc(c, cx, cy, 6.0F, withAlpha(DEEP, clamp255(a * 0.92F)));
        fillRing(c, cx, cy, 6.0F, 0.8F, withAlpha(GOLD, clamp255(a * 0.85F)), 18);
        c.textCentered(String.valueOf(number), Math.round(cx), Math.round(cy - 4),
                withAlpha(GOLD_BRIGHT, a), false);
    }

    /** 冷却/锁定提示胶囊（画在扇区图标下方）。 */
    static void statusPill(PreviewCanvas c, float cx, float cy, String label, int color,
                           float alpha) {
        int width = c.textWidth(label) + 10;
        float pillX = cx - width * 0.5F;
        float pillY = cy - 2F;
        float pillH = 13F;
        if (SdfShapes.roundRect(c, pillX, pillY, width, pillH, pillH * 0.5F,
                withAlpha(DEEP, clamp255(alpha * 210)), withAlpha(DEEP, clamp255(alpha * 210)),
                withAlpha(color, clamp255(alpha * 140)), 0.8F)) {
            c.textCentered(label, Math.round(cx), Math.round(cy + 1),
                    withAlpha(color, clamp255(alpha * 255)), false);
            return;
        }
        fillDisc(c, cx, cy + 4, (width / 2F) + 1F, withAlpha(DEEP, clamp255(alpha * 200)));
        fillRing(c, cx, cy + 4, (width / 2F) + 1F, 0.7F, withAlpha(color, clamp255(alpha * 120)), 16);
        c.textCentered(label, Math.round(cx), Math.round(cy), withAlpha(color, clamp255(alpha * 255)), false);
    }

    /** 粒子：一点星火 + 很淡的十字光晕。 */
    static void spark(PreviewCanvas c, float x, float y, float size, int argb) {
        float[] col = colorOf(argb);
        float alpha = col[3];
        fillDisc(c, x, y, size, argb);
        line(c, x - size * 2.2F, y, x + size * 2.2F, y, 0.35F,
                withAlpha(argb, clamp255(alpha * 60)));
        line(c, x, y - size * 2.2F, x, y + size * 2.2F, 0.35F,
                withAlpha(argb, clamp255(alpha * 60)));
    }

    // ══════════════════════════════════════════════════════════════
    //  轮盘主体
    // ══════════════════════════════════════════════════════════════

    /** 轮盘状态参数（HUD 与装载界面共用）。 */
    static final class WheelOptions {
        float time;
        float openT = 1F;                 // 0..1 打开动画
        float mana = 182F;
        float maxMana = 240F;
        int selected = -1;                // 当前选中方位（-1 = 未选中）
        int hovered = -1;                 // 鼠标悬停方位（可与 selected 不同）
        int target = -1;                  // 装载界面：当前要绑定的方位
        /** 每个方位的冷却剩余比例：0 = 就绪，1 = 刚进冷却。 */
        final float[] cooldown = new float[SLOTS];
        /** 每个方位的冷却剩余秒数（展示用）。 */
        final float[] cooldownSeconds = new float[SLOTS];
        float charge = 0F;                // 选中法术的蓄力比例 0..1
        boolean casting = false;          // 正在施法/蓄力
        boolean showEmptySlots = true;    // 空位是否画符文插槽
        boolean showKeybinds = true;      // 是否画 1..8 键位
        boolean showSlotNumbers = false;  // 装载界面：数字代替键位
        boolean showCenterInfo = true;
        boolean showChrome = true;        // 标题 / 法力条 / 提示
        boolean loadoutMode = false;      // 装载界面视觉（不画世界背景光、不显示"RELEASE"）
        float iconScale = 2.0F;           // 图标放大倍数（16px × scale）
        float intensity = 1F;
        /** 未选中且轮盘为空时的中心文案（HUD 的"没有主动法术"兜底）。 */
        String emptyTitle = "AIM A SIGIL";
        String emptySub = "release to cast";
    }

    static void drawWheel(PreviewCanvas c, Spell[] wheel, float cx, float cy,
                          float outer, float inner, WheelOptions o) {
        float open = easeOut(o.openT);
        float radius = lerp(outer * 0.86F, outer, open);
        float alpha = clamp(open * o.intensity, 0F, 1F);
        if (alpha < 0.01F) {
            return;
        }

        boolean fancy = hasDesignShaders(c);
        // ① 背景光环（加色）
        if (fancy) {
            PreviewShader aura = c.shader("wheel_aura");
            float auraSpan = radius * 3.1F;
            aura.set("uCenter", cx, cy);
            aura.set("uQuadOrigin", cx - auraSpan * 0.5F, cy - auraSpan * 0.5F);
            aura.set("uQuadSize", auraSpan, auraSpan);
            aura.set("uOuter", radius);
            aura.set("uInner", inner);
            aura.set("uTime", o.time);
            float hoverAngle = o.selected >= 0 ? slotAngle(o.selected)
                    : (o.hovered >= 0 ? slotAngle(o.hovered) : 0F);
            aura.set("uHoverAngle", hoverAngle);
            aura.set("uHoverStrength", o.selected >= 0 ? 0.9F : (o.hovered >= 0 ? 0.45F : 0F));
            aura.set("uIntensity", alpha * (o.loadoutMode ? 0.55F : 1F));
            aura.set("uColorA", GOLD_RGBA[0], GOLD_RGBA[1], GOLD_RGBA[2], 1F);
            aura.set("uColorB", TEAL_RGBA[0], TEAL_RGBA[1], TEAL_RGBA[2], 1F);
            aura.set("uColorC", HAZE_RGBA[0], HAZE_RGBA[1], HAZE_RGBA[2], 1F);
            c.shaderQuad(aura, cx - auraSpan * 0.5F, cy - auraSpan * 0.5F, auraSpan, auraSpan);
        }

        // ② CPU 装饰环：内外金环 + 刻度
        drawCpuRings(c, cx, cy, radius, inner, o.time, alpha, fancy);

        // ③ 8 个扇区
        for (int i = 0; i < SLOTS; i++) {
            Spell spell = wheel[i];
            boolean occupied = spell != null;
            if (!occupied && !o.showEmptySlots) {
                continue;
            }
            float start = slotStart(i) + 1.2F * (float) Math.PI / 180F;
            float end = slotEnd(i) - 1.2F * (float) Math.PI / 180F;
            float selected = (i == o.selected) ? 1F : 0F;
            float hovered = (i == o.hovered) ? 1F : (i == o.target ? 1F : 0F);
            float locked = occupied && o.mana < spell.mana() ? 1F : 0F;
            float cooldown = o.cooldown[i];
            float[] school = occupied ? spell.color() : EMPTY_SLOT_RGBA;
            float slotAlpha = alpha * (occupied ? 1F : 0.22F);
            if (fancy) {
                PreviewShader slice = c.shader("wheel_slice");
                // quad 比扇区外径大一圈：让 hover/selected 的外发光有地方衰减，
                // 否则发光会顶在 quad 直边上、在夜空背景里露出发光的矩形边。
                float span = radius * 2.7F;
                float qx = cx - span * 0.5F;
                float qy = cy - span * 0.5F;
                slice.set("uCenter", cx, cy);
                slice.set("uQuadOrigin", qx, qy);
                slice.set("uQuadSize", span, span);
                slice.set("uRadius", radius);
                slice.set("uInnerRadius", inner);
                slice.set("uStartAngle", start);
                slice.set("uEndAngle", end);
                slice.set("uColorA", school[0], school[1], school[2], 1F);
                slice.set("uColorB", school[0] * 0.22F, school[1] * 0.22F, school[2] * 0.28F, 1F);
                slice.set("uTime", o.time);
                slice.set("uHover", hovered);
                slice.set("uSelected", selected);
                slice.set("uLocked", locked);
                slice.set("uCooldown", cooldown);
                slice.set("uIntensity", slotAlpha);
                c.shaderQuad(slice, qx, qy, span, span);
            } else {
                drawFallbackSlice(c, cx, cy, radius, inner, start, end, school, slotAlpha,
                        hovered, selected, locked, cooldown);
            }
        }

        // ④ 星图连线：从核心到每个已绑定方位（复用项目 magic_constellation）
        drawConstellation(c, wheel, cx, cy, inner, radius, o, alpha);

        // ⑤ 图标 + 状态
        float iconRadius = (radius + inner) * 0.5F;
        float iconSize = 16F * o.iconScale * lerp(0.9F, 1F, open);
        // 图标是 16×16 像素图：逻辑尺寸取 8 的倍数，2x 原生渲染后每个 texel 才能落到
        // 整数个物理像素上（否则会出现"有的像素 4px、有的 5px"的粗糙感）。
        float iconSnap = Math.max(16F, Math.round(iconSize / 8F) * 8F);
        for (int i = 0; i < SLOTS; i++) {
            Spell spell = wheel[i];
            if (spell == null) {
                if (o.showEmptySlots) {
                    float sx = slotX(cx, iconRadius, i);
                    float sy = slotY(cy, iconRadius, i);
                    fillDisc(c, sx, sy, iconSize * 0.52F, withAlpha(DEEP, clamp255(alpha * 70)));
                    fillRing(c, sx, sy, iconSize * 0.52F, 0.6F,
                            withAlpha(LOCKED, clamp255(alpha * 60)), 22);
                    runeGlyph(c, sx, sy, iconSize * 0.55F, 0x51 + i * 7, LOCKED, alpha * 0.30F);
                    if (o.showKeybinds || o.showSlotNumbers) {
                        float badgeRadius = Math.max(inner + 8F,
                                Math.min(inner + 22F, iconRadius - iconSize * 0.62F - 8F));
                        keyBadge(c, slotX(cx, badgeRadius, i), slotY(cy, badgeRadius, i),
                                i + 1, alpha * 0.38F);
                    }
                }
                continue;
            }
            boolean sel = i == o.selected;
            boolean hov = i == o.hovered || i == o.target;
            float pop = sel ? 1.2F : (hov ? 1.05F : 1F);
            float sx = slotX(cx, iconRadius, i);
            float sy = slotY(cy, iconRadius, i);
            float myIconSize = Math.max(16F, Math.round(iconSnap * pop / 8F) * 8F);
            float glow = sel ? 1F : (hov ? 0.6F : 0.18F);
            drawSpellIcon(c, spell, sx, sy, myIconSize, alpha, glow * lerp(0.4F, 1F, open));

            boolean onCooldown = o.cooldown[i] > 0.001F;
            boolean noMana = o.mana < spell.mana();
            if (onCooldown) {
                statusPill(c, sx, sy + myIconSize * 0.75F,
                        formatCooldown(o.cooldownSeconds[i]), GOLD, alpha);
            } else if (noMana) {
                statusPill(c, sx, sy + myIconSize * 0.75F, spell.mana() + " MANA", LOCKED, alpha);
            }
            // 键位 / 方位号：小轮盘（装载界面）上 inner+22 可能压到图标，
            // 夹到"图标内侧安全半径"；大轮盘仍然用 inner+22。
            float badgeRadius = Math.max(inner + 8F,
                    Math.min(inner + 22F, iconRadius - iconSize * 0.62F - 8F));
            if (o.showKeybinds || o.showSlotNumbers) {
                keyBadge(c, slotX(cx, badgeRadius, i), slotY(cy, badgeRadius, i),
                        i + 1, alpha * (sel ? 1F : 0.78F));
            }
            // 选中方位：外缘指示弧
            if (sel) {
                fillArc(c, cx, cy, radius + 3.5F, radius + 5.0F,
                        slotStart(i) + 0.05F, slotEnd(i) - 0.05F,
                        argb(spell.color(), 0.85F * alpha), 20);
            }
        }

        // ⑥ 中心核心
        drawCore(c, wheel, cx, cy, inner, o, alpha, fancy);

        // ⑦ 蓄力进度环（选中扇区外侧）
        if (o.casting && o.selected >= 0 && o.charge > 0.001F) {
            Spell spell = wheel[o.selected];
            float ringRadius = radius + 9F;
            fillArc(c, cx, cy, ringRadius - 1.2F, ringRadius + 1.2F,
                    (float) -Math.PI / 2F,
                    (float) (-Math.PI / 2F + TAU * clamp(o.charge, 0F, 1F)),
                    argb(spell != null ? spell.color() : GOLD_RGBA, 0.9F * alpha), 64);
        }

        // ⑧ 金色粒子
        drawMotes(c, cx, cy, radius, o.time, alpha);

        // ⑨ 左上角状态（HUD 模式）：只统计已绑定的主动法术
        if (!o.loadoutMode && o.showChrome) {
            int bound = 0;
            for (Spell spell : wheel) {
                if (spell != null) {
                    bound++;
                }
            }
            int a2 = clamp255(alpha * 190);
            c.text("ACTIVE SIGILS  " + bound + " / " + SLOTS, 22, 52,
                    withAlpha(TEXT_FAINT, a2), false);
            WheelKit.fillStar(c, 16F, 56F, 3.0F, 1.1F, 4, 0F,
                    withAlpha(GOLD, a2));
        }
    }

    // ── 中心核心 ──

    private static void drawCore(PreviewCanvas c, Spell[] wheel, float cx, float cy,
                                 float inner, WheelOptions o, float alpha, boolean fancy) {
        float coreRadius = inner - 4F;
        fillDisc(c, cx, cy, coreRadius + 1F, withAlpha(DEEP, clamp255(alpha * 235)));
        fillRing(c, cx, cy, coreRadius, 0.8F, withAlpha(GOLD, clamp255(alpha * 110)), 40);
        fillRing(c, cx, cy, coreRadius * 0.72F, 0.6F, withAlpha(GOLD, clamp255(alpha * 60)), 40);

        Spell selected = o.selected >= 0 ? wheel[o.selected] : null;
        float manaFrac = clamp(o.mana / Math.max(1F, o.maxMana), 0F, 1F);
        float cooldownFrac = o.selected >= 0 ? o.cooldown[o.selected] : 0F;
        if (fancy) {
            PreviewShader core = c.shader("wheel_core");
            float quad = coreRadius * 2.6F;
            core.set("uCenter", cx, cy);
            core.set("uQuadOrigin", cx - quad * 0.5F, cy - quad * 0.5F);
            core.set("uQuadSize", quad, quad);
            core.set("uCoreRadius", coreRadius * 0.92F);
            core.set("uTime", o.time);
            core.set("uMana", manaFrac);
            core.set("uCharge", o.casting ? clamp(o.charge, 0F, 1F) : 0F);
            core.set("uCooldown", o.selected >= 0 ? cooldownFrac : 0F);
            float[] tint = selected != null ? selected.color() : GOLD_RGBA;
            core.set("uColorA", tint[0], tint[1], tint[2], 1F);
            core.set("uColorB", GOLD_BRIGHT_RGBA[0], GOLD_BRIGHT_RGBA[1], GOLD_BRIGHT_RGBA[2], 1F);
            core.set("uIntensity", alpha);
            c.shaderQuad(core, (int) (cx - quad * 0.5F), (int) (cy - quad * 0.5F),
                    (int) quad, (int) quad);
        } else {
            drawFallbackCore(c, cx, cy, coreRadius, o, alpha, selected);
        }

        // 中心压暗一层：让名字/状态行压在暗底上，核心的光只当边框氛围
        fillDisc(c, cx, cy, coreRadius * 0.88F, withAlpha(DEEP, clamp255(alpha * 96)));

        if (!o.showCenterInfo) {
            return;
        }

        // 中心文字：未选中显示"瞄准"提示，选中显示法术信息
        if (selected == null) {
            drawNeutralSigil(c, cx, cy - 2F, coreRadius * 0.58F, o.time, alpha * 0.95F);
            int bound = 0;
            for (Spell spell : wheel) {
                if (spell != null) {
                    bound++;
                }
            }
            String title = bound == 0 ? "NO ACTIVE SIGILS" : o.emptyTitle;
            String sub = bound == 0 ? "bind active spells in the loadout" : o.emptySub;
            c.textCentered(title, Math.round(cx), Math.round(cy + coreRadius * 0.30F),
                    withAlpha(bound == 0 ? WARN : GOLD_BRIGHT, clamp255(alpha * 235)), true);
            c.textCentered(sub, Math.round(cx), Math.round(cy + coreRadius * 0.52F),
                    withAlpha(TEXT_DIM, clamp255(alpha * 200)), false);
            return;
        }

        int a = clamp255(alpha * 255);
        boolean onCooldown = o.selected >= 0 && o.cooldown[o.selected] > 0.001F;
        boolean noMana = o.mana < selected.mana();
        String name = selected.name();
        int nameScale = (c.textWidth(name) * 2 <= (int) (coreRadius * 1.7F)) ? 2 : 1;
        c.textCenteredScaled(name, cx, cy - coreRadius * 0.52F,
                withAlpha(TEXT, a), true, nameScale);
        int schoolLine = mix(0xFFFFFFFF, argb(selected.color(), 1F), 0.55F);
        c.textCentered(selected.schoolLabel() + "  |  " + usingShort(selected),
                Math.round(cx), Math.round(cy - coreRadius * 0.10F),
                withAlpha(schoolLine, a), true);

        String line;
        int color;
        if (o.casting && o.charge > 0F) {
            float totalSeconds = selected.chargeTicks() > 0
                    ? selected.chargeTicks() / 20.0F
                    : 2.0F;
            line = String.format(Locale.ROOT, "CHARGING %.1f / %.1fs",
                    clamp(o.charge, 0F, 1F) * totalSeconds, totalSeconds);
            color = GOLD_BRIGHT;
        } else if (onCooldown) {
            line = "COOLDOWN " + formatCooldown(o.cooldownSeconds[o.selected]);
            color = TEXT_DIM;
        } else if (noMana) {
            line = "NEED " + selected.mana() + " MANA";
            color = LOCKED;
        } else {
            line = "READY  |  " + selected.mana() + " MANA";
            color = GOLD;
        }
        c.textCentered(line, Math.round(cx), Math.round(cy + coreRadius * 0.20F),
                withAlpha(color, a), false);
        if (!o.loadoutMode) {
            String hint = (onCooldown || noMana) ? "cannot cast" : "RELEASE TO CAST";
            c.textCentered(hint, Math.round(cx), Math.round(cy + coreRadius * 0.44F),
                    withAlpha((onCooldown || noMana) ? TEXT_FAINT : GOLD_BRIGHT,
                            clamp255(alpha * 220)), false);
        }
    }

    // ── CPU 兜底与装饰 ──

    private static void drawCpuRings(PreviewCanvas c, float cx, float cy, float radius,
                                     float inner, float time, float alpha, boolean fancy) {
        int a = clamp255(alpha * 255);
        // 外圈双环
        fillRing(c, cx, cy, radius + 2.0F, 0.9F, withAlpha(GOLD, clamp255(a * 0.55F)), 96);
        fillRing(c, cx, cy, radius + 6.0F, 0.45F, withAlpha(GOLD, clamp255(a * 0.28F)), 96);
        fillRing(c, cx, cy, inner - 1.5F, 0.6F, withAlpha(GOLD, clamp255(a * 0.45F)), 64);
        // 外圈刻度（60 格，每 5 格长刻度）
        int ticks = 60;
        for (int i = 0; i < ticks; i++) {
            double ang = i * TAU / ticks + time * 0.02F;
            boolean major = i % 5 == 0;
            float r0 = radius + 3.0F;
            float r1 = radius + (major ? 9.0F : 6.0F);
            float c0 = (float) Math.cos(ang), s0 = (float) Math.sin(ang);
            int color = withAlpha(GOLD, clamp255(a * (major ? 0.55F : 0.25F)));
            line(c, cx + c0 * r0, cy + s0 * r0, cx + c0 * r1, cy + s0 * r1,
                    major ? 0.7F : 0.45F, color);
        }
        if (!fancy) {
            // 没有设计着色器时，此处之外再由 drawFallbackSlice 补足
        }
    }

    private static void drawFallbackSlice(PreviewCanvas c, float cx, float cy,
                                          float radius, float inner,
                                          float start, float end, float[] school,
                                          float alpha, float hovered, float selected,
                                          float locked, float cooldown) {
        float outer = radius + selected * 5F + hovered * 2F;
        float[] fillIn = {school[0] * 0.20F, school[1] * 0.20F, school[2] * 0.26F, 0.92F * alpha};
        float[] fillOut = {school[0] * 0.55F, school[1] * 0.55F, school[2] * 0.62F,
                0.30F * alpha + (selected * 0.18F + hovered * 0.10F)};
        fillArcGradient(c, cx, cy, inner, outer, start, end, fillIn, fillOut, 14);
        int edge = withAlpha(argb(school, 1F), clamp255(alpha * (0.30F + 0.55F * selected + 0.25F * hovered)));
        // 内外缘细线
        fillArc(c, cx, cy, inner, inner + 1.2F, start, end, withAlpha(edge, clamp255(alpha * 70)), 16);
        fillArc(c, cx, cy, outer - 1.2F, outer, start, end,
                withAlpha(edge, clamp255(alpha * (90 + 120 * selected))), 24);
        if (cooldown > 0.001F) {
            float sweepEnd = lerp(start, end, cooldown);
            fillArc(c, cx, cy, inner, outer, start, sweepEnd,
                    withAlpha(DEEP, clamp255(alpha * 150)), 18);
        }
        if (locked > 0.5F) {
            fillArc(c, cx, cy, inner, outer, start, end,
                    withAlpha(LOCKED, clamp255(alpha * 60)), 12);
        }
    }

    private static void drawFallbackCore(PreviewCanvas c, float cx, float cy, float radius,
                                         WheelOptions o, float alpha, Spell selected) {
        float[] tint = selected != null ? selected.color() : GOLD_RGBA;
        for (int i = 6; i >= 1; i--) {
            float r = radius * (i / 6F);
            float k = 1F - (i / 7F);
            fillDisc(c, cx, cy, r, argb(tint, 0.05F * alpha + 0.16F * alpha * k));
        }
        fillRing(c, cx, cy, radius * 0.9F, 0.7F, withAlpha(GOLD, clamp255(alpha * 90)), 40);
    }

    private static void drawConstellation(PreviewCanvas c, Spell[] wheel, float cx, float cy,
                                          float inner, float radius, WheelOptions o, float alpha) {
        float iconRadius = (radius + inner) * 0.5F;
        PreviewShader constellation = null;
        try {
            constellation = c.shader("magic_constellation");
        } catch (RuntimeException ignored) {
            // 项目着色器不在时退化成 CPU 发丝线
        }
        for (int i = 0; i < SLOTS; i++) {
            Spell spell = wheel[i];
            if (spell == null) {
                continue;
            }
            float x1 = slotX(cx, inner + 2F, i);
            float y1 = slotY(cy, inner + 2F, i);
            float x2 = slotX(cx, iconRadius - 18F, i);
            float y2 = slotY(cy, iconRadius - 18F, i);
            float strength = (i == o.selected) ? 2.2F : (i == o.hovered ? 1.5F : 0.75F);
            int base = withAlpha(argb(spell.color(), 1F), clamp255(alpha * 90 * strength));
            if (constellation != null) {
                // magic_constellation 用 xy 两端 + uColor/uIntensity 的线段几何
                float dx = x2 - x1;
                float dy = y2 - y1;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > 0.01F) {
                    float nx = -dy / len * 0.8F;
                    float ny = dx / len * 0.8F;
                    float[] verts = {
                            x1 - nx, y1 - ny, 0, 0, 0,
                            x1 + nx, y1 + ny, 0, 0, 1,
                            x2 + nx, y2 + ny, 0, len / 64F, 1,
                            x2 - nx, y2 - ny, 0, len / 64F, 0};
                    // 转成 6 顶点两个三角形
                    float[] tris = {
                            verts[0], verts[1], verts[2], verts[3], verts[4],
                            verts[5], verts[6], verts[7], verts[8], verts[9],
                            verts[15], verts[16], verts[17], verts[18], verts[19],
                            verts[0], verts[1], verts[2], verts[3], verts[4],
                            verts[15], verts[16], verts[17], verts[18], verts[19],
                            verts[10], verts[11], verts[12], verts[13], verts[14]};
                    constellation.set("uTime", o.time);
                    constellation.set("uColor", spell.color()[0], spell.color()[1],
                            spell.color()[2], 0.9F);
                    constellation.set("uIntensity", 0.55F * strength * alpha);
                    c.shaderMesh(constellation, tris, tris.length, 6, GL_MODE_TRIANGLES);
                }
            } else {
                line(c, x1, y1, x2, y2, 0.5F, base);
            }
        }
    }

    private static final int GL_MODE_TRIANGLES = 0x0004; // GL11.GL_TRIANGLES，避免这里 import LWJGL

    private static void drawMotes(PreviewCanvas c, float cx, float cy, float radius,
                                  float time, float alpha) {
        for (int i = 0; i < 34; i++) {
            float seed = i * 12.9898F + 4.1414F;
            float orbit = radius * (1.02F + 0.22F * fract((float) Math.sin(seed) * 43758.5453F));
            float speed = 0.05F + 0.12F * fract((float) Math.sin(seed * 1.7F) * 43758.5453F);
            double ang = i * TAU / 34.0 + time * speed;
            float x = cx + (float) Math.cos(ang) * orbit;
            float y = cy + (float) Math.sin(ang) * orbit;
            float twinkle = 0.35F + 0.65F * (0.5F + 0.5F * (float) Math.sin(time * 2.1F + i * 1.7F));
            float size = 0.8F + 1.1F * fract((float) Math.sin(seed * 2.3F) * 43758.5453F);
            int color = withAlpha(GOLD_BRIGHT, clamp255(alpha * 150 * twinkle));
            spark(c, x, y, size, color);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  HUD 外框：标题 / 法力条 / 提示
    // ══════════════════════════════════════════════════════════════

    static void drawHudChrome(PreviewCanvas c, WheelOptions o, float cx, float cy, float outer) {
        float alpha = clamp(o.openT, 0F, 1F) * o.intensity;
        int a = clamp255(alpha * 255);

        // 标题
        c.textCenteredScaled("ARCANE WHEEL", Math.round(cx), 14,
                withAlpha(GOLD_BRIGHT, a), true, 2F);
        int titleHalf = c.textWidth("ARCANE WHEEL"); // 2x 后的一半宽 = 原宽度
        drawTitleOrnament(c, cx - titleHalf - 16F, 22F, true, a);
        drawTitleOrnament(c, cx + titleHalf + 16F, 22F, false, a);
        c.textCentered("HOLD  [R]   |   AIM   |   RELEASE TO CAST",
                Math.round(cx), 34, withAlpha(TEXT_DIM, clamp255(a * 0.85F)), false);

        // 角落装饰（四角短金线）
        int len = 26;
        int m = 14;
        int corner = withAlpha(GOLD, clamp255(a * 0.55F));
        line(c, m, m + 6, m, m, 0.6F, corner);
        line(c, m, m, m + len, m, 0.6F, corner);
        line(c, c.width - m - len, m, c.width - m, m, 0.6F, corner);
        line(c, c.width - m, m, c.width - m, m + 6, 0.6F, corner);
        line(c, m, c.height - m - 6, m, c.height - m, 0.6F, corner);
        line(c, m, c.height - m, m + len, c.height - m, 0.6F, corner);
        line(c, c.width - m - len, c.height - m, c.width - m, c.height - m, 0.6F, corner);
        line(c, c.width - m, c.height - m, c.width - m, c.height - m - 6, 0.6F, corner);

        // 法力条
        float barW = 260F;
        float barH = 10F;
        float bx = cx - barW * 0.5F;
        float by = c.height - 40F;
        float frac = clamp(o.mana / Math.max(1F, o.maxMana), 0F, 1F);
        // 轨道（暗底 + 圆头）
        fillRoundBar(c, bx, by, barW, barH, withAlpha(DEEP, clamp255(a * 235)),
                withAlpha(INK, clamp255(a * 235)));
        fillRingBarOutline(c, bx, by, barW, barH, withAlpha(GOLD, clamp255(a * 110)));
        // 填充（金色渐变 + 圆头）
        fillRoundBar(c, bx + 1F, by + 1F, (barW - 2F) * frac, barH - 2F,
                withAlpha(GOLD_BRIGHT, clamp255(a * 235)),
                withAlpha(GOLD, clamp255(a * 235)));
        // 刻度
        for (int i = 1; i < 8; i++) {
            float tx = bx + barW * i / 8F;
            line(c, tx, by + 1F, tx, by + barH - 1F, 0.35F,
                    withAlpha(GOLD, clamp255(a * 80)));
        }
        c.textCentered(String.format(Locale.ROOT, "MANA  %d / %d", Math.round(o.mana),
                        Math.round(o.maxMana)), Math.round(cx), Math.round(by - 14F),
                withAlpha(GOLD_BRIGHT, clamp255(a * 235)), true);

        // 左右提示
        c.text("RELEASE TO CAST", 20, c.height - 20, withAlpha(TEXT_FAINT, clamp255(a * 180)), false);
        c.textRight("RMB  CANCEL", c.width - 20, c.height - 20,
                withAlpha(TEXT_FAINT, clamp255(a * 180)), false);
    }

    /** 标题两侧的小装饰：一条渐隐金线 + 菱形。 */
    private static void drawTitleOrnament(PreviewCanvas c, float x, float y, boolean left, int alpha) {
        int a = clamp255(alpha * 0.8F);
        int lineColor = withAlpha(GOLD, clamp255(a * 0.55F));
        float dir = left ? -1F : 1F;
        float x0 = x;
        float x1 = x + dir * 26F;
        // 三节渐隐线
        line(c, x0, y, x0 + dir * 10F, y, 0.6F, lineColor);
        line(c, x0 + dir * 12F, y, x0 + dir * 20F, y, 0.45F,
                withAlpha(GOLD, clamp255(a * 0.32F)));
        line(c, x0 + dir * 22F, y, x1, y, 0.35F,
                withAlpha(GOLD, clamp255(a * 0.18F)));
        // 菱形
        fillStar(c, x0 + dir * 1F, y, 3.4F, 1.2F, 4, 0F, withAlpha(GOLD_BRIGHT, a));
    }

    /** 圆头横条：矩形 + 两端圆帽，颜色可上下渐变。 */
    private static void fillRoundBar(PreviewCanvas c, float x, float y, float w, float h,
                                     int color, int capColor) {
        if (w <= 0.5F || h <= 0.5F) {
            return;
        }
        float radius = h * 0.5F;
        if (SdfShapes.roundRect(c, x, y, w, h, radius, color, capColor, 0, 0F)) {
            // 顶部高光：留一点长度，避免贴到圆角外
            float inset = Math.min(radius, w * 0.25F);
            c.fill((int) (x + inset), (int) y, Math.max(1, (int) (w - inset * 2F)),
                    Math.max(1, (int) (h * 0.3F)),
                    withAlpha(0xFFFFFFFF, (((color >>> 24) & 0xFF) / 3)));
            return;
        }
        if (w <= h) {
            fillDisc(c, x + w * 0.5F, y + radius, w * 0.5F, color);
            return;
        }
        c.fill((int) (x + radius), (int) y, (int) (w - h), (int) h, color);
        fillDisc(c, x + radius, y + radius, radius, capColor);
        fillDisc(c, x + w - radius, y + radius, radius, capColor);
        // 顶部一道高光
        c.fill((int) (x + radius), (int) y, (int) (w - h), Math.max(1, (int) (h * 0.3F)),
                withAlpha(0xFFFFFFFF, (((color >>> 24) & 0xFF) / 3)));
    }

    private static void fillRingBarOutline(PreviewCanvas c, float x, float y, float w, float h,
                                          int argb) {
        line(c, x, y, x + w, y, 0.45F, argb);
        line(c, x, y + h, x + w, y + h, 0.45F, argb);
        line(c, x, y, x, y + h, 0.45F, argb);
        line(c, x + w, y, x + w, y + h, 0.45F, argb);
    }

    // ══════════════════════════════════════════════════════════════
    //  装载界面可复用的卡片 / 详情
    // ══════════════════════════════════════════════════════════════

    /**
     * 法术卡片（装载界面左侧图鉴用）。
     *
     * @param state 0 = 常态，1 = hover，2 = 已绑定（右侧显示绑定徽章），3 = 禁用（被动页）
     */
    static void drawSpellCard(PreviewCanvas c, Spell spell, float x, float y, float w, float h,
                              int state, int assignedSlot, float time) {
        boolean hovered = state == 1;
        boolean disabled = state == 3;
        int accent = argb(spell.color(), disabled ? 0.35F : (hovered ? 1F : 0.8F));
        int fill = disabled ? 0xCC0A101E : (hovered ? 0xE6141B2E : 0xD90B1220);
        if (!SdfShapes.roundRect(c, x, y, w, h, 3F, fill, fill,
                withAlpha(GOLD, hovered ? 110 : 55), 0.9F)) {
            c.fill((int) x, (int) y, (int) w, (int) h, fill);
            c.fill((int) x, (int) y, (int) w, 1, withAlpha(GOLD, hovered ? 130 : 70));
            c.fill((int) x, (int) (y + h - 1), (int) w, 1, withAlpha(GOLD, 45));
        }
        c.fill((int) x, (int) y, 2, (int) h, withAlpha(accent, hovered ? 235 : 150));
        if (hovered) {
            c.fill((int) x, (int) y, (int) w, (int) h, 0x10FFFFFF);
        }
        float iconCx = x + 15F;
        float iconCy = y + h * 0.5F;
        drawSpellIcon(c, spell, iconCx, iconCy, 16F, disabled ? 0.55F : 1F, hovered ? 0.9F : 0.15F);
        int textX = (int) (x + 29F);
        int textColor = disabled ? withAlpha(TEXT_DIM, 170) : withAlpha(TEXT, 245);
        c.text(spell.name(), textX, (int) (y + 7F), textColor, true);
        String sub = spell.active()
                ? spell.schoolLabel() + "  |  " + spell.mana() + " MANA  |  "
                  + formatCooldown(spell.cooldownSeconds())
                : "PASSIVE  |  ALWAYS ON";
        c.text(sub, textX, (int) (y + 19F),
                disabled ? withAlpha(TEXT_FAINT, 170) : withAlpha(TEXT_FAINT, 215), false);
        if (spell.active() && !disabled && assignedSlot >= 0) {
            String chip = "SLOT " + (assignedSlot + 1);
            int chipW = c.textWidth(chip) + 8;
            int chipX = (int) (x + w - chipW - 4F);
            c.fill(chipX, (int) (y + 4F), chipW, 9, 0xD92A1B4D);
            c.fill(chipX, (int) (y + 4F), chipW, 1, withAlpha(accent, 200));
            c.textCentered(chip, chipX + chipW / 2, (int) (y + 5F),
                    withAlpha(GOLD_BRIGHT, 240), false);
        }
        if (disabled) {
            // 被动不可装载：右侧小锁 + 斜线
            int lx = (int) (x + w - 14F);
            int ly = (int) (y + 5F);
            c.fill(lx, ly + 3, 8, 6, withAlpha(TEXT_FAINT, 150));
            c.fill(lx + 2, ly, 4, 4, withAlpha(TEXT_FAINT, 150));
            c.fill(lx + 2, ly + 2, 4, 3, withAlpha(0xFF0A101E, 255));
        }
    }

    /** 右侧详情面板（装载界面）。 */
    static void drawSpellDetail(PreviewCanvas c, Spell spell, float x, float y, float w, float h,
                                int assignedSlot, float time) {
        if (!SdfShapes.roundRect(c, x, y, w, h, 3F, 0xE60A1020, 0xE60A1020,
                withAlpha(GOLD, 65), 0.9F)) {
            c.fill((int) x, (int) y, (int) w, (int) h, 0xE60A1020);
            c.fill((int) x, (int) y, (int) w, 1, withAlpha(GOLD, 90));
            c.fill((int) x, (int) y, 1, (int) h, withAlpha(GOLD, 60));
            c.fill((int) (x + w - 1), (int) y, 1, (int) h, withAlpha(GOLD, 40));
            c.fill((int) x, (int) (y + h - 1), (int) w, 1, withAlpha(GOLD, 40));
        }
        float iconCx = x + w * 0.5F;
        float iconCy = y + 38F;
        drawSpellIcon(c, spell, iconCx, iconCy, 40F, 1F, 1F);
        c.textCentered(spell.name(), Math.round(iconCx), Math.round(y + 66F),
                withAlpha(TEXT, 250), true);
        c.textCentered(spell.schoolLabel() + "  |  " + (spell.active() ? "ACTIVE" : "PASSIVE"),
                Math.round(iconCx), Math.round(y + 79F),
                withAlpha(argb(spell.color(), 1F), 240), false);

        float lineY = y + 96F;
        statRow(c, x, lineY, w, "TYPE", spell.using().equals("-") ? "ALWAYS ON" : spell.using());
        statRow(c, x, lineY + 15F, w, "MANA", spell.active() ? spell.mana() + "" : "-");
        statRow(c, x, lineY + 30F, w, "COOLDOWN", spell.active()
                ? formatCooldown(spell.cooldownSeconds()) : "-");
        statRow(c, x, lineY + 45F, w, "ASSIGNED", assignedSlot >= 0
                ? "WHEEL SLOT " + (assignedSlot + 1) : (spell.active() ? "NOT ASSIGNED" : "--"));

        c.fill((int) (x + 10), (int) (lineY + 60F), (int) (w - 20), 1, withAlpha(GOLD, 50));
        c.text("DESCRIPTION", (int) (x + 10), (int) (lineY + 68F),
                withAlpha(TEXT_FAINT, 220), false);
        drawWrapped(c, spell.lore(), (int) (x + 10), (int) (lineY + 82F),
                (int) (w - 20), withAlpha(TEXT_DIM, 235), 4);
        if (!spell.active()) {
            c.text("PASSIVE SPELLS CANNOT", (int) (x + 10), (int) (y + h - 30F),
                    withAlpha(LOCKED, 230), false);
            c.text("BE PLACED ON THE WHEEL", (int) (x + 10), (int) (y + h - 20F),
                    withAlpha(LOCKED, 230), false);
        }
    }

    private static void statRow(PreviewCanvas c, float x, float y, float w, String label, String value) {
        c.text(label, (int) (x + 10F), (int) y, withAlpha(TEXT_FAINT, 220), false);
        c.textRight(value, (int) (x + w - 10F), (int) y, withAlpha(TEXT, 240), false);
    }

    /** 简单按字符宽度换行（英文空格断词）。 */
    static void drawWrapped(PreviewCanvas c, String text, int x, int y, int width,
                            int argb, int maxLines) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = y;
        int lines = 0;
        for (String word : words) {
            String probe = line.isEmpty() ? word : line + " " + word;
            if (c.textWidth(probe) > width && !line.isEmpty()) {
                c.text(line.toString(), x, lineY, argb, false);
                lines++;
                lineY += 10;
                line = new StringBuilder(word);
                if (lines >= maxLines) {
                    return;
                }
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (!line.isEmpty() && lines < maxLines) {
            c.text(line.toString(), x, lineY, argb, false);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  杂项
    // ══════════════════════════════════════════════════════════════

    private static boolean hasDesignShaders(PreviewCanvas c) {
        try {
            c.shader("wheel_slice");
            c.shader("wheel_aura");
            c.shader("wheel_core");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String formatCooldown(float seconds) {
        if (seconds <= 0F) {
            return "READY";
        }
        if (seconds >= 10F) {
            return Math.round(seconds) + "s";
        }
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    private static String usingShort(Spell spell) {
        if (!spell.active()) {
            return "PASSIVE";
        }
        if (spell.chargeTicks() > 0) {
            return "CHARGE";
        }
        return switch (spell.using()) {
            case "CHANNEL" -> "CHANNEL";
            case "CHARGE 2.0s" -> "CHARGE";
            default -> "INSTANT";
        };
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float easeOut(float t) {
        t = clamp(t, 0F, 1F);
        float inv = 1F - t;
        return 1F - inv * inv * inv;
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    /** 方位对应的示意角度（给场景做鼠标指向线/高亮用）。 */
    static float angleForSlot(int slot) {
        return slotAngle(slot);
    }
}
