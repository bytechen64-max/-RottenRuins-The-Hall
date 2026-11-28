import java.util.Locale;

/**
 * 魔法轮盘施法界面（HUD）设计稿 —— 不启动游戏，按 640×400 逻辑画布（导出 ×2）模拟。
 *
 * <p>设计要点（与 {@code docs/spell-wheel-design.md} 对齐）：</p>
 * <ul>
 *   <li>按住施法键打开轮盘；鼠标方向瞄准；松手释放、右键取消；</li>
 *   <li>轮盘固定 8 个方位（正上=位 1，顺时针），<b>只显示已绑定的主动法术</b>，
 *       被动不知轮盘为何物；空格位只是很淡的符文插槽；</li>
 *   <li>扇区状态：普通 / hover / selected / 冷却扇形压暗 / 蓝不足锁定；</li>
 *   <li>中心：选中法术名 + 流派 + 施法类型 + 蓝耗/冷却/蓄力，外圈是法力弧与冷却环；</li>
 *   <li>底部：法力条；顶部：标题与操作提示。</li>
 * </ul>
 *
 * <p>产物（各对应一个状态帧）：{@code spell_wheel.png}（主视觉，选中火球）、
 * {@code spell_wheel_idle.png}（未选中）、{@code spell_wheel_charge.png}（审判之矛蓄力）、
 * {@code spell_wheel_cooldown.png}（冷却 + 蓝不足）。</p>
 */
public final class SceneSpellWheel {

    // 轮盘几何（640×400 逻辑画布）
    private static final float CX = 320F;
    private static final float CY = 204F;
    private static final float OUTER = 142F;
    private static final float INNER = 58F;

    private SceneSpellWheel() {
    }

    public static void render(PreviewCanvas c, float time) {
        // ── 帧 1：刚打开、未选中（瞄准提示）──
        drawFrame(c, time + 0.7F, 0.55F, -1, 182F, new float[]{0F, 0.71F, 0F},
                new float[]{0F, 21.3F, 0F}, 0F, false, WheelKit.defaultWheel());
        c.save("spell_wheel_idle");

        // ── 帧 2：审判之矛蓄力中（选中位 2，charge 0.62）──
        drawFrame(c, time + 1.9F, 1.0F, 1, 146F, new float[]{0F, 0F, 0F},
                new float[]{0F, 0F, 0F}, 0.62F, true, WheelKit.defaultWheel());
        c.save("spell_wheel_charge");

        // ── 帧 3：冷却 + 蓝不足（选中位 2 正在冷却；位 3 蓝不够）──
        drawFrame(c, time + 3.4F, 1.0F, 1, 52F, new float[]{0F, 0.45F, 0F},
                new float[]{0F, 13.6F, 0F}, 0F, false, WheelKit.defaultWheel());
        c.save("spell_wheel_cooldown");

        // ── 帧 4：空轮盘兜底（魔法池里没有主动法术）──
        drawFrame(c, time + 0.4F, 1.0F, -1, 182F, new float[]{0F, 0F, 0F},
                new float[]{0F, 0F, 0F}, 0F, false, new WheelKit.Spell[WheelKit.SLOTS]);
        c.save("spell_wheel_empty");

        // ── 帧 5：主视觉（选中火球）——最后画，主入口的自动保存就是它 ──
        drawFrame(c, time, 1.0F, 0, 182F, new float[]{0F, 0.71F, 0F},
                new float[]{0F, 21.3F, 0F}, 0F, false, WheelKit.defaultWheel());
        c.save("spell_wheel");
    }

    // ══════════════════════════════════════════════════════════════
    //  一帧的合成
    // ══════════════════════════════════════════════════════════════

    private static void drawFrame(PreviewCanvas c, float time, float openT, int selected,
                                  float mana, float[] cooldown, float[] cooldownSeconds,
                                  float charge, boolean casting, WheelKit.Spell[] wheel) {
        c.clear(0xFF000000);
        drawNightWorld(c, time);

        WheelKit.WheelOptions options = new WheelKit.WheelOptions();
        options.time = time;
        options.openT = openT;
        options.mana = mana;
        options.maxMana = 240F;
        options.selected = selected;
        options.hovered = selected;
        options.charge = charge;
        options.casting = casting;
        options.showEmptySlots = true;
        options.showKeybinds = true;
        System.arraycopy(cooldown, 0, options.cooldown, 0, 3);
        System.arraycopy(cooldownSeconds, 0, options.cooldownSeconds, 0, 3);

        // 打开时的外扩光环（纯 CPU，随 openT 出现）
        if (openT > 0.01F) {
            float t = easeOut(openT);
            for (int i = 3; i >= 1; i--) {
                WheelKit.fillRing(c, CX, CY, OUTER * (0.9F + 0.1F * t) + i * 5F,
                        0.5F, WheelKit.withAlpha(WheelKit.GOLD,
                                Math.round(8F * t * (4 - i))), 80);
            }
        }

        WheelKit.drawWheel(c, wheel, CX, CY, OUTER, INNER, options);
        WheelKit.drawHudChrome(c, options, CX, CY, OUTER);

        // 页面指示（右上）：轮盘 I / II / III
        drawPageIndicator(c, time, openT);

        // 鼠标示意（每帧给一个不同位置，看起来像玩家在瞄）
        if (openT > 0.9F) {
            float mx;
            float my;
            if (selected == 0) {
                mx = 330F;
                my = 96F;
            } else if (selected == 1) {
                mx = 404F;
                my = 146F;
            } else if (selected == 2) {
                mx = 438F;
                my = 214F;
            } else {
                mx = 320F;
                my = 262F;
            }
            drawCursor(c, mx, my, openT);
        }
    }

    private static float mana(float value) {
        return value;
    }

    // ══════════════════════════════════════════════════════════════
    //  模拟世界背景（夜山 + 月亮 + 星空 + 暗角）
    // ══════════════════════════════════════════════════════════════

    private static void drawNightWorld(PreviewCanvas c, float time) {
        // 夜空
        c.fillGradientV(0, 0, c.width, 290, 0xFF0B1733, 0xFF1D324C);
        c.fillGradientV(0, 230, c.width, 90, 0x002B4760, 0x662B4760);

        // 星空（确定性伪随机 + 缓慢闪烁）
        for (int i = 0; i < 140; i++) {
            float n = hash(i * 37.1F);
            int x = (int) (n * c.width);
            int y = (int) (hash(i * 91.7F + 3F) * 250F);
            float twinkle = 0.5F + 0.5F * (float) Math.sin(time * 1.3F + i * 0.9F);
            int alpha = (int) (35 + 120 * twinkle * hash(i * 13.3F + 7F));
            int size = hash(i * 5.3F) > 0.88F ? 2 : 1;
            c.fill(x, y, size, size, (alpha << 24) | 0xC8D8FF);
            if (size == 2) {
                c.fill(x - 1, y, 4, 1, ((alpha / 3) << 24) | 0xC8D8FF);
                c.fill(x, y - 1, 1, 4, ((alpha / 3) << 24) | 0xC8D8FF);
            }
        }

        // 月亮 + 多层柔光
        int moonX = 534;
        int moonY = 66;
        for (int i = 6; i >= 1; i--) {
            float r = 16F + i * 5.5F;
            int alpha = Math.max(1, 7 - i);
            WheelKit.fillDisc(c, moonX, moonY, r, (alpha << 24) | 0xB8CCEE);
        }
        WheelKit.fillDisc(c, moonX, moonY, 16F, 0xFFD9E4F7);
        WheelKit.fillDisc(c, moonX - 3F, moonY - 2F, 13F, 0xFFEDF3FF);
        // 月面细节（用暗色挖几处坑）
        WheelKit.fillDisc(c, moonX + 4F, moonY + 3F, 3.0F, 0x253A4A66);
        WheelKit.fillDisc(c, moonX - 5F, moonY - 4F, 2.0F, 0x203A4A66);
        WheelKit.fillDisc(c, moonX + 1F, moonY - 8F, 1.3F, 0x1E3A4A66);

        // 三层山脊剪影
        drawRidge(c, 278F, 30F, 46F, 7.3F, 0xFF0E1B2C, 0x33A8C8E8);
        drawRidge(c, 300F, 22F, 38F, 3.1F, 0xFF091221, 0x22A8C8E8);
        drawRidge(c, 320F, 16F, 26F, 8.7F, 0xFF050B15, 0x11A8C8E8);

        // 地平线雾
        c.fillGradientV(0, 285, c.width, 42, 0x00000000, 0xAA0A1526);
        c.fillGradientV(0, 327, c.width, 73, 0xFF050A13, 0xFF010207);

        // 场景整体压暗 + 暗角，突出轮盘
        c.fill(0, 0, c.width, c.height, 0x5A000000);
        c.fillGradientV(0, 0, c.width, 62, 0x88000000, 0x00000000);
        c.fillGradientV(0, c.height - 62, c.width, 62, 0x00000000, 0xA0000000);
        // 轮盘底下那点环境光
        for (int i = 4; i >= 1; i--) {
            WheelKit.fillDisc(c, CX, CY, OUTER + i * 22F,
                    WheelKit.withAlpha(0xFF1E3C66, 4 + i * 2));
        }
    }

    private static void drawRidge(PreviewCanvas c, float baseY, float amp, float amp2,
                                  float phase, int fill, int rim) {
        int steps = 14;
        float[] xs = new float[steps * 2];
        float[] ys = new float[steps * 2];
        float[] top = new float[steps];
        for (int i = 0; i < steps; i++) {
            float t = i / (float) (steps - 1);
            float x = t * c.width;
            float y = baseY - (float) (Math.sin(t * 6.1F + phase) * amp)
                    - (float) (Math.sin(t * 17.3F + phase * 2.3F) * amp2 * 0.35F)
                    - amp * 0.35F;
            top[i] = y;
            xs[i * 2] = x;
            ys[i * 2] = y;
            xs[i * 2 + 1] = x;
            ys[i * 2 + 1] = c.height;
        }
        WheelKit.fillPolygon(c, xs, ys, steps * 2, fill);
        // 山脊线
        for (int i = 0; i + 1 < steps; i++) {
            WheelKit.line(c, xs[i * 2], ys[i * 2], xs[(i + 1) * 2], ys[(i + 1) * 2],
                    0.6F, WheelKit.withAlpha(rim, (rim >>> 24) & 0xFF));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  装饰 / 光标
    // ══════════════════════════════════════════════════════════════

    private static void drawPageIndicator(PreviewCanvas c, float time, float openT) {
        int alpha = Math.round(180 * clamp(openT, 0F, 1F));
        int x = c.width - 108;
        int y = 14;
        c.text("WHEEL", x, y, WheelKit.withAlpha(WheelKit.GOLD, alpha), false);
        for (int i = 0; i < 3; i++) {
            int px = x + 42 + i * 18;
            boolean active = i == 0;
            WheelKit.fillDisc(c, px + 4, y + 4, 3.2F,
                    WheelKit.withAlpha(active ? WheelKit.GOLD_BRIGHT : WheelKit.TEXT_FAINT,
                            active ? alpha : alpha / 2));
            if (active) {
                WheelKit.fillRing(c, px + 4, y + 4, 5.2F, 0.5F,
                        WheelKit.withAlpha(WheelKit.GOLD, alpha / 2), 16);
            }
        }
    }

    private static void drawCursor(PreviewCanvas c, float x, float y, float alpha) {
        int a = Math.round(230 * clamp(alpha, 0F, 1F));
        // 菱形指针 + 十字
        WheelKit.fillStar(c, x, y, 6.5F, 2.2F, 4, 0F,
                WheelKit.withAlpha(WheelKit.GOLD_BRIGHT, a));
        WheelKit.fillStar(c, x, y, 3.2F, 1.2F, 4, 0F,
                WheelKit.withAlpha(0xFFFFFFFF, a));
        WheelKit.fillRing(c, x, y, 10F, 0.5F, WheelKit.withAlpha(WheelKit.GOLD, a / 3), 20);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float easeOut(float t) {
        t = clamp(t, 0F, 1F);
        float inv = 1F - t;
        return 1F - inv * inv * inv;
    }

    /** 稳定的伪随机 0..1（场景内不用 Random，保证每帧一致）。 */
    private static float hash(float seed) {
        float v = (float) Math.sin(seed * 12.9898F) * 43758.5453F;
        return v - (float) Math.floor(v);
    }
}
