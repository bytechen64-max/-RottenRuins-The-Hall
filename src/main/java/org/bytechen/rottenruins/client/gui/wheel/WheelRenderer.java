package org.bytechen.rottenruins.client.gui.wheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.client.rend.gui.WheelGuiShaders;

/**
 * 轮盘 HUD / 装载界面的<b>绘制器</b>：把 {@link WheelData} 的视图画成一个八角星盘。
 *
 * <p>几何与配色都来自设计稿（{@code docs/spell-wheel-design.md}），但这里是<b>实机渲染</b>：
 * 布局用 GUI 缩放坐标，曲线元素全部走 {@link WheelGuiShaders} 的 SDF / 轮盘着色器，
 * 文字用原版 {@link Font}，法术图标用原版物品渲染（16×16 像素图按整数倍放大）。</p>
 *
 * <p>屏幕和 HUD 共用这一份绘制：{@code SpellWheelScreen} 传入鼠标状态，
 * {@code SpellLoadoutScreen} 传入草稿映射与 target 高亮，HUD 维持条只画底部一条。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WheelRenderer {

    public static final int SLOTS = WheelData.SLOTS_PER_PAGE;

    // 设计稿 token（和预览工具 / 设计文档一致）
    public static final int GOLD = 0xFFE7C46A;
    public static final int GOLD_BRIGHT = 0xFFFFE49A;
    public static final int GOLD_DIM = 0xFFC9A227;
    public static final int TEXT = 0xFFF2E7CE;
    public static final int TEXT_DIM = 0xFFB9AE96;
    public static final int TEXT_FAINT = 0xFF8E8776;
    public static final int DEEP = 0xFF05090F;
    public static final int LOCKED = 0xFF6B8AC8;
    public static final int DANGER = 0xFFE06A5A;

    private static final float[] EMPTY_SLOT_COLOR = {0.30F, 0.38F, 0.56F};
    private static final float TAU = (float) (Math.PI * 2.0);

    private WheelRenderer() {
    }

    // ══════════════════════════════════════════════════════════════
    //  轮盘主体
    // ══════════════════════════════════════════════════════════════

    /**
     * 画完整轮盘。
     *
     * @param cx,cy       轮盘圆心（GUI 缩放坐标）
     * @param outer,inner 外/内半径
     * @param slots       8 个方位（{@link WheelData#fromMapping(int[])} 或 {@link WheelData#page(int)}）
     * @param hovered     鼠标悬停方位（-1 无）
     * @param selected    当前选中方位（-1 无）
     * @param openT       打开动画 0..1
     * @param charge      蓄力比例 0..1
     * @param loadoutMode 装载界面视觉（光环减弱、不显示"release to cast"）
     */
    public static void render(GuiGraphics gui, float cx, float cy, float outer, float inner,
                              WheelData.Slot[] slots, int hovered, int selected,
                              float openT, float charge, boolean loadoutMode, float time) {
        float open = easeOut(openT);
        float radius = lerp(outer * 0.86F, outer, open);
        float alpha = open;
        if (alpha <= 0.01F) {
            return;
        }
        int mana = WheelData.mana();
        int maxMana = Math.max(1, WheelData.maxMana());

        if (WheelGuiShaders.wheelAvailable()) {
            WheelGuiShaders.Slice[] slices = new WheelGuiShaders.Slice[SLOTS];
            for (int i = 0; i < SLOTS; i++) {
                WheelData.Slot slot = slots[i];
                boolean occupied = slot != null && slot.occupied();
                float[] school = occupied ? slot.color() : EMPTY_SLOT_COLOR;
                float slotAlpha = alpha * (occupied ? 1.0F : 0.22F);
                boolean locked = occupied && mana < slot.manaCost();
                float cooldown = occupied ? slot.cooldownFrac() : 0.0F;
                // 注意：扇区边界是「该方位的中心角 ± 半格」，不是两个相邻中心角——
                // 用相邻中心角会把整个扇区顺时针平移半格，图标和发光就会错位。
                slices[i] = new WheelGuiShaders.Slice(
                        slotStart(i) + 1.2F * (float) Math.PI / 180.0F,
                        slotEnd(i) - 1.2F * (float) Math.PI / 180.0F,
                        school[0], school[1], school[2],
                        school[0] * 0.22F, school[1] * 0.22F, school[2] * 0.28F,
                        hovered == i ? 1.0F : 0.0F,
                        selected == i ? 1.0F : 0.0F,
                        locked ? 1.0F : 0.0F,
                        cooldown,
                        slotAlpha);
            }
            WheelData.Slot selectedSlot = selected >= 0 && selected < SLOTS ? slots[selected] : null;
            float[] coreColor = selectedSlot != null && selectedSlot.occupied()
                    ? selectedSlot.color() : new float[]{0.906F, 0.769F, 0.416F};
            WheelGuiShaders.drawWheel(gui, cx, cy, radius, inner,
                    time, loadoutMode, open, slices, hovered, selected,
                    mana / (float) maxMana, charge, selectedSlot == null ? 0.0F : selectedSlot.cooldownFrac(),
                    coreColor[0], coreColor[1], coreColor[2]);
        } else {
            // 着色器全挂：至少画一块深色底盘 + 图标，界面仍可用
            gui.fill((int) (cx - radius), (int) (cy - radius),
                    (int) (cx + radius), (int) (cy + radius), 0xAA04070E);
            WheelGuiShaders.sdfRing(gui, cx, cy, inner, radius, 0x33E7C46A, 0x33E7C46A, true);
        }

        // ② CPU 装饰：内外金环 + 刻度
        float ringAlpha = alpha;
        WheelGuiShaders.sdfRing(gui, cx, cy, radius - 0.7F, radius + 0.7F,
                argb(GOLD, 0.55F * ringAlpha), argb(GOLD, 0.55F * ringAlpha), true);
        WheelGuiShaders.sdfRing(gui, cx, cy, radius - 4.5F, radius - 3.6F,
                argb(GOLD_DIM, 0.35F * ringAlpha), argb(GOLD_DIM, 0.35F * ringAlpha), true);
        for (int i = 0; i < 96; i++) {
            double a = -Math.PI / 2.0 + i * TAU / 96.0;
            float r0 = radius + 4.0F;
            float r1 = radius + (i % 6 == 0 ? 9.0F : 6.5F);
            WheelGuiShaders.sdfLine(gui,
                    cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0,
                    cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    0.35F, argb(GOLD, 0.35F * ringAlpha));
        }
        WheelGuiShaders.sdfRing(gui, cx, cy, inner - 1.0F, inner + 0.6F,
                argb(GOLD, 0.45F * ringAlpha), argb(GOLD, 0.45F * ringAlpha), true);

        drawSlots(gui, cx, cy, radius, inner, slots, hovered, selected, alpha, charge, mana,
                loadoutMode);

        // ③ 中心文字
        drawCenterText(gui, cx, cy, inner, slots, selected, charge, mana, maxMana,
                loadoutMode, alpha);
    }

    private static void drawSlots(GuiGraphics gui, float cx, float cy, float radius, float inner,
                                  WheelData.Slot[] slots, int hovered, int selected,
                                  float alpha, float charge, int mana, boolean loadoutMode) {
        float iconRadius = (radius + inner) * 0.5F;
        // HUD 图标固定 32 逻辑像素（2 倍渲染下每个 texel 4 物理像素）；
        // 装载预览的轮盘更小，图标盘也按环带宽度收小，避免圆盘互相/与轮圈溢出。
        float iconSize = loadoutMode
                ? Math.max(18.0F, Math.min(25.0F, (radius - inner) * 0.5F))
                : 32.0F;
        boolean drawIcons = alpha >= 0.25F;
        for (int i = 0; i < SLOTS; i++) {
            WheelData.Slot slot = slots[i];
            float sx = slotX(cx, iconRadius, i);
            float sy = slotY(cy, iconRadius, i);
            boolean occupied = slot != null && slot.occupied();
            boolean sel = i == selected;
            boolean hov = i == hovered;

            // 图标底盘 + 流派色描边
            float discR = iconSize * 0.78F;
            WheelGuiShaders.sdfDisc(gui, sx, sy, discR, argb(0xFF05090F, 0.82F * alpha),
                    argb(0xFF0A101E, 0.72F * alpha));
            if (occupied) {
                float[] color = slot.color();
                int ringColor = argb(color, (sel ? 0.95F : hov ? 0.75F : 0.55F) * alpha);
                WheelGuiShaders.sdfRing(gui, sx, sy, discR - 1.4F, discR, ringColor, ringColor, false);
                if (drawIcons) {
                    drawItemIcon(gui, slot.stack(), sx, sy, iconSize * (sel ? 1.15F : 1.0F));
                }
                // HUD 才画状态胶囊：只显示数字（魔能/秒数），单位由界面文案表达。
                // 位置放在图标"外侧"（径向向外），和内侧的键位标号分开，避免压住 1..8。
                // 装载界面的轮盘只是配置预览，魔能已经显示在左侧图鉴卡片上，不再重复。
                if (!loadoutMode) {
                    // 图标半对角线约 0.71*iconSize，胶囊再往外让一点，不能压图标。
                    float pillR = iconRadius + iconSize * 1.02F;
                    float pillX = slotX(cx, pillR, i);
                    float pillY = slotY(cy, pillR, i);
                    if (slot.cooldownSeconds() > 0.0F) {
                        drawPill(gui, pillX, pillY,
                                WheelData.formatCooldown(slot.cooldownSeconds()), GOLD, alpha);
                    } else if (mana < slot.manaCost()) {
                        // 蓝量不足：只显示数字（不标注单位），颜色用锁定色警示。
                        drawPill(gui, pillX, pillY,
                                String.valueOf(slot.manaCost()), LOCKED, alpha);
                    } else {
                        // 蓝量足够时也显示"需要的魔能"数字，方便规划消耗。
                        drawPill(gui, pillX, pillY,
                                String.valueOf(slot.manaCost()), TEXT_DIM, alpha);
                    }
                }
            } else {
                float[] c = EMPTY_SLOT_COLOR;
                WheelGuiShaders.sdfRing(gui, sx, sy, discR - 0.6F, discR + 0.2F,
                        argb(c, 0.22F * alpha), argb(c, 0.22F * alpha), false);
                WheelGuiShaders.sdfStar(gui, sx, sy, iconSize * 0.22F, iconSize * 0.08F,
                        4, i * 0.3F, argb(c, 0.35F * alpha));
            }

            // 键位/方位号
            // 方位号再向内收一点：把空间让给图标外侧的魔能/冷却数字。
            float badgeRadius = Math.max(inner + 8.0F,
                    Math.min(inner + 8.0F, iconRadius - iconSize * 0.62F - 8.0F));
            float bx = slotX(cx, badgeRadius, i);
            float by = slotY(cy, badgeRadius, i);
            WheelGuiShaders.sdfDisc(gui, bx, by, 6.0F, argb(DEEP, 0.9F * alpha), argb(DEEP, 0.9F * alpha));
            WheelGuiShaders.sdfRing(gui, bx, by, 5.2F, 6.4F,
                    argb(GOLD, (sel ? 0.95F : 0.7F) * alpha), argb(GOLD, (sel ? 0.95F : 0.7F) * alpha), false);
            textCentered(gui, String.valueOf(i + 1), bx, by - 4, argb(GOLD_BRIGHT, alpha), false);

            // 选中方位外缘指示弧
            if (sel) {
                float[] color = occupied ? slot.color() : new float[]{0.906F, 0.769F, 0.416F};
                WheelGuiShaders.sdfWedge(gui, cx, cy, radius + 3.5F, radius + 5.0F,
                        slotStart(i) + 0.05F, slotEnd(i) - 0.05F,
                        argb(color, 0.85F * alpha), argb(color, 0.85F * alpha));
            }
        }
        // 蓄力环
        if (charge > 0.001F && selected >= 0) {
            WheelData.Slot slot = slots[selected];
            float[] color = slot != null && slot.occupied() ? slot.color()
                    : new float[]{0.906F, 0.769F, 0.416F};
            WheelGuiShaders.sdfWedge(gui, cx, cy, radius + 7.8F, radius + 10.2F,
                    (float) (-Math.PI / 2.0),
                    (float) (-Math.PI / 2.0 + TAU * Math.min(1.0F, charge)),
                    argb(color, 0.9F * alpha), argb(color, 0.9F * alpha));
        }
    }

    private static void drawCenterText(GuiGraphics gui, float cx, float cy, float inner,
                                       WheelData.Slot[] slots, int selected, float charge,
                                       int mana, int maxMana, boolean loadoutMode, float alpha) {
        int coreR = (int) (inner - 4.0F);
        WheelGuiShaders.sdfDisc(gui, cx, cy, coreR + 1.0F,
                argb(DEEP, 0.92F * alpha), argb(DEEP, 0.92F * alpha));
        WheelGuiShaders.sdfRing(gui, cx, cy, coreR, coreR + 1.2F,
                argb(GOLD, 0.45F * alpha), argb(GOLD, 0.45F * alpha), true);
        WheelGuiShaders.sdfRing(gui, cx, cy, coreR * 0.72F, coreR * 0.72F + 0.8F,
                argb(GOLD, 0.22F * alpha), argb(GOLD, 0.22F * alpha), true);

        // 可读性暗盘（盖在核心光球上，保证文字对比度）
        WheelGuiShaders.sdfDisc(gui, cx, cy, coreR * 0.86F,
                argb(0xFF05090F, 0.40F * alpha), argb(0xFF05090F, 0.30F * alpha));

        boolean anyBound = false;
        for (WheelData.Slot slot : slots) {
            if (slot != null && slot.occupied()) {
                anyBound = true;
                break;
            }
        }
        WheelData.Slot slot = selected >= 0 && selected < SLOTS ? slots[selected] : null;
        // 装载界面：轮盘中间只留"轮盘"两个字，具体法术信息在左右面板。
        if (loadoutMode) {
            String title = WheelI18n.tr("rotten_ruins.wheel.center.wheel");
            float titleScale = textWidth(title) * 2 <= inner * 1.7F ? 2.0F : 1.0F;
            textCenteredScaled(gui, title, cx, cy - 6, argb(TEXT, 0.95F * alpha), true, titleScale);
            return;
        }
        if (slot == null || !slot.occupied()) {
            if (!anyBound) {
                String title = WheelI18n.tr("rotten_ruins.wheel.center.no_active");
                float titleScale = textWidth(title) * 2 <= inner * 1.7F ? 2.0F : 1.0F;
                textCenteredScaled(gui, title, cx, cy - 6, argb(DANGER, 0.95F * alpha),
                        true, titleScale);
                textCentered(gui, WheelI18n.tr("rotten_ruins.wheel.center.bind_hint"),
                        cx, cy + 10, argb(TEXT_FAINT, alpha), false);
            } else {
                // HUD 未选中：中心只显示"轮盘"两个字；操作提示在底部（松手施法 / 右键取消）。
                String title = WheelI18n.tr("rotten_ruins.wheel.center.wheel");
                float titleScale = textWidth(title) * 2 <= inner * 1.7F ? 2.0F : 1.0F;
                textCenteredScaled(gui, title, cx, cy - 6, argb(TEXT, 0.95F * alpha),
                        true, titleScale);
            }
            return;
        }

        float[] color = slot.color();
        float nameScale = textWidth(slot.name()) * 2 <= inner * 1.7F ? 2.0F : 1.0F;
        textCenteredScaled(gui, slot.name(), cx, cy - 30, argb(TEXT, alpha), true, nameScale);
        textCentered(gui, slot.school() + "  |  " + slot.using(),
                cx, cy - 10, argb(mixWhite(color, 0.55F), alpha), false);
        String status;
        int statusColor;
        if (slot.cooldownSeconds() > 0.0F) {
            status = WheelI18n.tr("rotten_ruins.wheel.center.cooldown",
                    WheelData.formatCooldown(slot.cooldownSeconds()));
            statusColor = GOLD;
        } else if (mana < slot.manaCost()) {
            status = WheelI18n.tr("rotten_ruins.wheel.center.not_enough_mana");
            statusColor = LOCKED;
        } else if (charge > 0.001F) {
            status = WheelI18n.tr("rotten_ruins.wheel.center.charging",
                    String.format(java.util.Locale.ROOT, "%.1f", charge));
            statusColor = GOLD_BRIGHT;
        } else {
            status = WheelI18n.tr("rotten_ruins.wheel.center.ready", slot.manaCost());
            statusColor = TEXT_DIM;
        }
        textCentered(gui, status, cx, cy + 6, argb(statusColor, alpha), false);
        textCentered(gui, loadoutMode ? WheelI18n.tr("rotten_ruins.wheel.center.selected")
                        : WheelI18n.tr("rotten_ruins.wheel.release_to_cast"),
                cx, cy + 20, argb(TEXT_FAINT, 0.9F * alpha), false);
    }

    // ══════════════════════════════════════════════════════════════
    //  HUD chrome（标题 / 法力条 / 提示）
    // ══════════════════════════════════════════════════════════════

    /** HUD 的标题、法力条与底部提示；装载界面不调用。 */
    public static void renderHudChrome(GuiGraphics gui, int width, int height,
                                       float cx, float cy, float outer, float inner,
                                       float openT, float time) {
        float S = outer / 142.0F;
        float open = easeOut(openT);
        int a = Math.round(255 * open);

        // 顶部标题 + 副标题
        int titleY = Math.max(8, Math.round(cy - outer - 48.0F * Math.max(0.6F, S)));
        String wheelTitle = WheelI18n.tr("rotten_ruins.wheel.title");
        // textCenteredScaled 里缩放了 2 倍，金线必须按缩放后的实际宽度往外排，
        // 否则线会插进标题文字里（实机截图里"奥术轮盘"被横线穿过就是这个）。
        float titleScale = 2.0F;
        int titleW = Math.round(textWidth(wheelTitle) * titleScale);
        textCenteredScaled(gui, wheelTitle, cx, titleY,
                withAlpha(GOLD_BRIGHT, a), true, titleScale);
        String castKey = org.bytechen.rottenruins.client.MagicClientHandler.OPEN_WHEEL
                .getTranslatedKeyMessage().getString();
        textCentered(gui, WheelI18n.tr("rotten_ruins.wheel.subtitle", castKey),
                cx, titleY + 18, withAlpha(TEXT_FAINT, a), false);
        ornament(gui, cx - titleW - 44, titleY + 7, cx - titleW - 12, titleY + 7, a);
        ornament(gui, cx + titleW + 12, titleY + 7, cx + titleW + 44, titleY + 7, a);

        // 左上角状态
        WheelData.Slot[] slots = WheelData.page(WheelClientState.page());
        int bound = 0;
        for (WheelData.Slot slot : slots) {
            if (slot != null && slot.occupied()) {
                bound++;
            }
        }
        text(gui, WheelI18n.tr("rotten_ruins.wheel.active_sigils", bound, SLOTS),
                14, Math.max(28, titleY + 34), withAlpha(TEXT_FAINT, a), false);

        // 法力条
        int maxMana = Math.max(1, WheelData.maxMana());
        int mana = WheelData.mana();
        int barW = Math.min(260, Math.max(140, Math.round(250 * Math.max(0.7F, S))));
        int barH = Math.max(6, Math.round(10 * Math.max(0.7F, S)));
        int barX = Math.round(cx - barW / 2.0F);
        int barY = Math.min(height - 26, Math.round(cy + outer + 16.0F * Math.max(0.7F, S)));
        textCentered(gui, WheelI18n.tr("rotten_ruins.wheel.mana", mana, maxMana), cx, barY - 12,
                withAlpha(TEXT_FAINT, a), false);
        WheelGuiShaders.sdfRoundRect(gui, barX, barY, barW, barH, barH * 0.5F,
                withAlpha(0xFF141B2E, a), withAlpha(0xFF0A101E, a),
                withAlpha(GOLD, Math.round(70 * open)), 0.8F);
        int fillW = Math.max(1, Math.round(barW * (mana / (float) maxMana)));
        WheelGuiShaders.sdfRoundRect(gui, barX, barY, fillW, barH, barH * 0.5F,
                withAlpha(GOLD_BRIGHT, a), withAlpha(GOLD_DIM, a), 0, 0.0F);

        // 底部提示
        text(gui, WheelI18n.tr("rotten_ruins.wheel.release_to_cast"), 14, height - 18,
                withAlpha(TEXT_FAINT, a), false);
        textRight(gui, WheelI18n.tr("rotten_ruins.wheel.rmb_cancel"), width - 14, height - 18,
                withAlpha(TEXT_FAINT, a), false);
        // 轮盘页指示
        textRight(gui, WheelI18n.tr("rotten_ruins.wheel.page", WheelI18n.pageLabel(0)),
                width - 14, Math.max(14, titleY), withAlpha(GOLD, Math.round(160 * open)), false);
    }

    /** KEEP 维持条（轮盘收拢后的底部小条）。 */
    public static void renderMaintainBar(GuiGraphics gui, int width, int height,
                                         float manaFrac, float seconds) {
        WheelData.Slot slot = null;
        int page = WheelClientState.page();
        int index = WheelClientState.wheelIndex();
        WheelData.Slot[] slots = WheelData.page(page);
        if (index >= 0 && index < SLOTS) {
            slot = slots[index];
        }
        if (slot == null || !slot.occupied()) {
            return;
        }
        float barW = 200;
        float barH = 24;
        float x = width / 2.0F - barW / 2.0F;
        float y = height - 54;
        WheelGuiShaders.sdfRoundRect(gui, x, y, barW, barH, 6.0F,
                withAlpha(0xFF0A101E, 235), withAlpha(0xFF05090F, 235),
                withAlpha(GOLD, 120), 1.0F);
        drawItemIcon(gui, slot.stack(), x + 14, y + barH / 2.0F, 18.0F);
        text(gui, slot.name(), (int) (x + 28), (int) (y + 5), withAlpha(TEXT, 245), true);
        text(gui, WheelI18n.tr("rotten_ruins.wheel.maintain",
                        String.format(java.util.Locale.ROOT, "%.1f", seconds)),
                (int) (x + 28), (int) (y + 15), withAlpha(GOLD, 210), false);
        // 法力条
        float mw = 46;
        float mx = x + barW - mw - 8;
        float my = y + 8;
        WheelGuiShaders.sdfRoundRect(gui, mx, my, mw, 7, 3.5F,
                withAlpha(0xFF141B2E, 220), withAlpha(0xFF141B2E, 220), 0, 0.0F);
        WheelGuiShaders.sdfRoundRect(gui, mx, my, Math.max(1, mw * Math.min(1.0F, manaFrac)), 7, 3.5F,
                withAlpha(GOLD, 230), withAlpha(GOLD_DIM, 230), 0, 0.0F);
        textRight(gui, WheelI18n.tr("rotten_ruins.wheel.maintain_end"), (int) (x + barW - 8), (int) (y + 15),
                withAlpha(TEXT_FAINT, 190), false);
    }

    // ══════════════════════════════════════════════════════════════
    //  几何 / 绘制辅助
    // ══════════════════════════════════════════════════════════════

    /** 第 i 个方位中心角（正上 = 0，顺时针）。 */
    public static float slotAngle(int index) {
        return (float) (-Math.PI / 2.0 + index * TAU / SLOTS);
    }

    public static float slotStart(int index) {
        return slotAngle(index) - (float) (Math.PI / SLOTS);
    }

    public static float slotEnd(int index) {
        return slotAngle(index) + (float) (Math.PI / SLOTS);
    }

    public static float slotX(float cx, float radius, int index) {
        return cx + (float) Math.cos(slotAngle(index)) * radius;
    }

    public static float slotY(float cy, float radius, int index) {
        return cy + (float) Math.sin(slotAngle(index)) * radius;
    }

    /**
     * 鼠标指向的方位；圆心死区与轮盘外返回 -1。
     */
    public static int hoveredIndex(double mouseX, double mouseY,
                                   float cx, float cy, float outer, float inner) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < inner * 0.85 || dist > outer + 8) {
            return -1;
        }
        double relative = (Math.atan2(dy, dx) + Math.PI / 2.0) / (TAU / SLOTS);
        int index = (int) Math.round(relative) % SLOTS;
        return (index + SLOTS) % SLOTS;
    }

    /** 根据屏幕尺寸算轮盘中心 / 半径（短边自适应，GUI 缩放坐标）。 */
    public static float[] layout(int width, int height) {
        float outer = clamp(Math.min(width, height) * 0.355F, 72.0F, 180.0F);
        float inner = outer * 0.408F;
        float cx = width / 2.0F;
        float cy = height / 2.0F + 4.0F;
        return new float[]{cx, cy, outer, inner};
    }

    public static void drawItemIcon(GuiGraphics gui, ItemStack stack, float cx, float cy, float size) {
        if (stack.isEmpty()) {
            return;
        }
        gui.pose().pushPose();
        gui.pose().translate(cx - size / 2.0F, cy - size / 2.0F, 0.0F);
        gui.pose().scale(size / 16.0F, size / 16.0F, 1.0F);
        gui.renderItem(stack, 0, 0);
        gui.pose().popPose();
    }

    private static void drawPill(GuiGraphics gui, float cx, float cy, String label,
                                 int color, float alpha) {
        int w = textWidth(label) + 10;
        float h = 12.0F;
        float x = cx - w / 2.0F;
        float y = cy - h / 2.0F;
        WheelGuiShaders.sdfRoundRect(gui, x, y, w, h, h * 0.5F,
                argb(0xFF05090F, 0.86F * alpha), argb(0xFF05090F, 0.86F * alpha),
                argb(color, 0.55F * alpha), 0.8F);
        textCentered(gui, label, cx, cy - 4, argb(color, alpha), false);
    }

    private static void ornament(GuiGraphics gui, float x0, float y0, float x1, float y1, int alpha) {
        WheelGuiShaders.sdfLine(gui, x0, y0, x1, y1, 0.5F, withAlpha(GOLD_DIM, Math.round(alpha * 0.7F)));
        WheelGuiShaders.sdfStar(gui, (x0 + x1) * 0.5F, y0, 3.0F, 1.0F, 4, 0.0F,
                withAlpha(GOLD, alpha));
    }

    // ── 文本 ──

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int textWidth(String text) {
        return font().width(text);
    }

    public static void text(GuiGraphics gui, String text, int x, int y, int color, boolean shadow) {
        gui.drawString(font(), text, x, y, color, shadow);
    }

    public static void textCentered(GuiGraphics gui, String text, float cx, float y, int color, boolean shadow) {
        gui.drawString(font(), text, Math.round(cx - font().width(text) / 2.0F), Math.round(y), color, shadow);
    }

    public static void textRight(GuiGraphics gui, String text, int rightX, int y, int color, boolean shadow) {
        gui.drawString(font(), text, rightX - font().width(text), y, color, shadow);
    }

    /** 整数倍放大文字（2x/3x 像素风；和设计稿一致，不做非整数缩放）。 */
    public static void textCenteredScaled(GuiGraphics gui, String text, float cx, float y,
                                          int color, boolean shadow, float scale) {
        gui.pose().pushPose();
        gui.pose().translate(cx, y, 0.0F);
        gui.pose().scale(scale, scale, 1.0F);
        gui.drawString(font(), text, Math.round(-font().width(text) / 2.0F), 0, color, shadow);
        gui.pose().popPose();
    }

    // ── 颜色 / 数学 ──

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** 给已有 ARGB 颜色换 alpha（0..1）。 */
    public static int argb(int color, float alpha) {
        int baseA = (color >>> 24) & 0xFF;
        int a = clamp255(baseA * clamp(alpha, 0.0F, 1.0F));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int argb(float[] rgb, float alpha) {
        int a = clamp255(alpha * 255.0F);
        int r = clamp255(rgb[0] * 255.0F);
        int g = clamp255(rgb[1] * 255.0F);
        int b = clamp255(rgb[2] * 255.0F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 和白色混合，提升在深色光球上的可读性。 */
    public static int mixWhite(float[] rgb, float amount) {
        return argb(new float[]{
                rgb[0] + (1.0F - rgb[0]) * amount,
                rgb[1] + (1.0F - rgb[1]) * amount,
                rgb[2] + (1.0F - rgb[2]) * amount}, 1.0F);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float easeOut(float t) {
        float v = clamp(t, 0.0F, 1.0F);
        float inv = 1.0F - v;
        return 1.0F - inv * inv * inv;
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    /** 当前时间（秒，模 1000 避免 float 精度问题）。 */
    public static float timeSeconds() {
        return (float) (System.currentTimeMillis() % 1_000_000L) / 1000.0F;
    }
}
