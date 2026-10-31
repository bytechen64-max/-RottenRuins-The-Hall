package org.bytechen.rottenruins.client.gui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.client.gui.wheel.WheelData;
import org.bytechen.rottenruins.client.gui.wheel.WheelRenderer;
import org.bytechen.rottenruins.client.gui.wheel.WheelI18n;
import org.bytechen.rottenruins.client.rend.gui.WheelGuiShaders;
import org.bytechen.rottenruins.network.c2s.WheelActionPacket;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.IMagicBaseItem;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>魔法装载界面</b>：给 8 个轮盘方位配置主动法术（三页轮盘各自一份草稿）。
 *
 * <h3>界面结构（640×400 参考画布，按窗口自适应）</h3>
 * <ul>
 *   <li><b>左栏</b>：法术图鉴。ACTIVE 页列出魔法池里的主动法术（图标 / 名字 / 流派·耗蓝·冷却，
 *       已绑定的显示 {@code SLOT n}）；PASSIVE 页只读（被动不可进轮盘）。卡片可点选，列表放不下时
 *       滚轮翻动。</li>
 *   <li><b>中间</b>：{@link WheelRenderer} 画出的 8 方位轮盘预览（和 HUD 同一套渲染器，
 *       {@code loadoutMode=true}）。下方是 {@code WHEEL I | n / 8 SOCKETS} 与操作提示。</li>
 *   <li><b>右栏</b>：选中法术/方位的详情（流派、类型、耗蓝、冷却、蓄力/维持、已绑定方位、描述）。</li>
 *   <li><b>底栏</b>：轮盘页签 I/II/III（切页不丢草稿）、{@code CLEAR SLOT / AUTO FILL / SAVE} 按钮、
 *       操作提示。</li>
 * </ul>
 *
 * <h3>草稿与保存</h3>
 * <p>打开时把 {@code PlayerMagicPool#copyWheelPage(p)} 的三页映射读进本地草稿；拖拽 / 右键 / 按钮
 * 只改草稿，点 <b>SAVE</b> 时把当前页发 {@link WheelActionPacket#sendConfig(int, int[])}（服务端
 * 会重新校验并全量同步回来）。离开界面不保存 = 放弃草稿，这是本界面「非即时生效」的一处刻意简化。</p>
 *
 * <h3>交互</h3>
 * <ul>
 *   <li>从左侧卡片按住拖到轮盘方位 → 绑定（同一页内先清掉该法术原来的方位，保证页内唯一）；</li>
 *   <li>点轮盘方位 → 选中并显示详情；右键方位 → 清空该位；</li>
 *   <li>{@code CLEAR SLOT} 清掉当前选中的方位（或选中卡片的绑定位）；
 *       {@code AUTO FILL} 按魔法池顺序把主动法术填进空位；</li>
 *   <li>鼠标滚轮在图鉴列表上翻页（池槽数可被属性抬高，卡片放不下时）。</li>
 * </ul>
 *
 * <p>所有画面文案为英文 ASCII（MC 位图字体只保证拉丁字符；本界面暂未接 lang key，
 * 待 i18n 阶段再统一收口）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpellLoadoutScreen extends Screen {

    /** 本地化快捷方式：所有界面文案都走 lang key（datagen 生成 en_us/zh_cn）。 */
    private static String tr(String key, Object... args) {
        return WheelI18n.tr(key, args);
    }

    // ══════════════════════════════════════════════════════════════
    // 常量
    // ══════════════════════════════════════════════════════════════

    private static final int PAGES = WheelData.PAGES;              // 3
    private static final int SLOTS = WheelData.SLOTS_PER_PAGE;     // 8
    private static final int EMPTY = -1;

    private static final String[] PAGE_LABELS = {"I", "II", "III"};
    private static final String[] BUTTON_KEYS = {
            "rotten_ruins.wheel.loadout.button.clear",
            "rotten_ruins.wheel.loadout.button.autofill",
            "rotten_ruins.wheel.loadout.button.save"};


    private static final int BTN_CLEAR = 0;
    private static final int BTN_AUTOFILL = 1;
    private static final int BTN_SAVE = 2;

    private static final int CODEX_ACTIVE = 0;
    private static final int CODEX_PASSIVE = 1;

    /** 卡片高度与行距（文字固定 9px 行高，不能跟 GUI 缩放一起缩）。 */
    private static final int CARD_H = 30;
    private static final int CARD_STEP = 34;

    // 面板 / 文字 token（与 WheelRenderer / 设计文档同一套）
    private static final int PANEL_TOP = 0xE60C1628;
    private static final int PANEL_BOTTOM = 0xE6060912;
    private static final int PANEL_BORDER = 0x99C9A227;
    private static final int CARD_TOP = 0xCC0B1220;
    private static final int CARD_BOTTOM = 0xB8080D18;
    private static final int CARD_HOVER_TOP = 0xE6141B2E;
    private static final int CARD_HOVER_BOTTOM = 0xD90F1626;
    private static final int CARD_SELECT_TOP = 0xE61A2340;
    private static final int CARD_SELECT_BOTTOM = 0xE6101830;
    private static final int CARD_DISABLED_TOP = 0xCC0A101E;
    private static final int CARD_DISABLED_BOTTOM = 0xC0080D18;

    private static final int GOLD = WheelRenderer.GOLD;
    private static final int GOLD_BRIGHT = WheelRenderer.GOLD_BRIGHT;
    private static final int DEEP = WheelRenderer.DEEP;
    private static final int TEXT = WheelRenderer.TEXT;
    private static final int TEXT_DIM = WheelRenderer.TEXT_DIM;
    private static final int TEXT_FAINT = WheelRenderer.TEXT_FAINT;
    private static final int LOCKED = WheelRenderer.LOCKED;

    private static final float TAU = (float) (Math.PI * 2.0);

    // ══════════════════════════════════════════════════════════════
    // 数据状态
    // ══════════════════════════════════════════════════════════════

    /** 三页草稿：draft[page][socket] = 魔法池槽位（-1 空位）。 */
    private final int[][] draft = new int[PAGES][SLOTS];
    private boolean draftsLoaded;
    /** 用户是否改过草稿；改过之后不再被服务端同步覆盖。 */
    private boolean dirty;

    private int page;
    private int codexTab = CODEX_ACTIVE;
    private int scrollRow;
    private int listRows;

    private int selectedSocket = -1;
    private int selectedPoolSlot = -1;

    private int dragPoolSlot = -1;
    private boolean dragging;
    private double dragStartX;
    private double dragStartY;

    private long openMs;

    // ══════════════════════════════════════════════════════════════
    // 每帧悬停结果
    // ══════════════════════════════════════════════════════════════

    private int hoveredSocket = -1;
    private int hoveredCard = -1;
    private int hoveredPage = -1;
    private int hoveredButton = -1;

    // ══════════════════════════════════════════════════════════════
    // 布局（init 里算）
    // ══════════════════════════════════════════════════════════════

    private float uiScale = 1.0F;
    private int margin;
    private int gap;
    private int titleY;
    private int subtitleY;
    private float titleScale = 3.0F;
    private int contentTop;
    private int contentBottom;
    private int panelY;
    private int panelH;
    private int leftX;
    private int panelW;
    private int rightX;
    private int bottomBarY;
    private int bottomBarH;
    private float wheelCx;
    private float wheelCy;
    private float wheelOuter;
    private float wheelInner;

    public SpellLoadoutScreen() {
        super(Component.translatable("rotten_ruins.gui.wheel.loadout.title"));
        // 草稿默认必须是空位（-1）；池不可用时也绝不能把 0 号池槽当成"已绑定"发出去。
        for (int[] row : draft) {
            java.util.Arrays.fill(row, EMPTY);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 生命周期 / 布局
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean isPauseScreen() {
        // 玩法界面：打开时游戏继续跑（和 MagicScreen / 轮盘 HUD 一致）。
        return false;
    }

    @Override
    protected void init() {
        super.init();
        if (openMs == 0L) {
            openMs = Util.getMillis();
        }
        computeLayout();
        loadDraftsIfNeeded();
    }

    /**
     * 自适应布局：以 640×400 为参考按 S 缩放，但文字固定 1x —— 所以面板宽度/轮盘半径
     * 有下限，保证小窗口里文字仍然放得下、也不越界。
     */
    private void computeLayout() {
        uiScale = clamp(Math.min(width / 640.0F, height / 400.0F), 0.6F, 1.6F);
        margin = Math.max(8, Math.round(16.0F * uiScale));
        gap = Math.max(8, Math.round(14.0F * uiScale));

        titleScale = width >= 480 ? 3.0F : 2.0F;
        titleY = Math.max(4, Math.round(8.0F * uiScale));
        subtitleY = titleY + Math.round(titleScale * 9.0F) + 2;
        contentTop = subtitleY + 13;

        bottomBarH = Math.max(46, Math.round(54.0F * uiScale));
        bottomBarY = height - bottomBarH;
        contentBottom = bottomBarY - Math.max(4, Math.round(6.0F * uiScale));

        panelY = contentTop;
        panelH = Math.max(80, contentBottom - panelY);

        // 左右面板最多占 1/3 可用宽度，中间永远给轮盘留位置。
        int usable = width - margin * 2 - gap * 2;
        int wanted = Math.round(188.0F * uiScale);
        int maxPanelByWidth = Math.max(120, (usable - 120) / 2);
        panelW = Math.max(120, Math.min(wanted, maxPanelByWidth));
        leftX = margin;
        rightX = width - margin - panelW;

        int centerLeft = leftX + panelW + gap;
        int centerRight = rightX - gap;
        wheelCx = (centerLeft + centerRight) * 0.5F;
        float byWidth = (centerRight - centerLeft) * 0.5F - 6.0F;
        float byHeight = (contentBottom - contentTop) * 0.5F - 30.0F;
        wheelOuter = clamp(Math.min(Math.min(byWidth, byHeight), Math.round(96.0F * uiScale)),
                30.0F, 150.0F);
        wheelInner = Math.max(14.0F, wheelOuter * 0.42F);
        wheelCy = contentTop + (contentBottom - contentTop) * 0.5F - Math.round(12.0F * uiScale);

        // 图鉴列表行数：面板头（标题+页签+分隔线）固定 42，底部留一行计数。
        listRows = Math.max(1, (panelH - 42 - 20) / CARD_STEP);
    }

    // ══════════════════════════════════════════════════════════════
    // 草稿：读取 / 同步 / 校验
    // ══════════════════════════════════════════════════════════════

    private PlayerMagicPool pool() {
        return WheelData.pool();
    }

    private void loadDraftsIfNeeded() {
        if (draftsLoaded) {
            return;
        }
        PlayerMagicPool pool = pool();
        if (pool == null) {
            return;
        }
        for (int p = 0; p < PAGES; p++) {
            int[] live = pool.copyWheelPage(p);
            System.arraycopy(live, 0, draft[p], 0, Math.min(SLOTS, live.length));
        }
        draftsLoaded = true;
    }

    /**
     * 草稿没被用户改过时，跟随服务端同步刷新（打开界面时会先请求一次全量同步，
     * 到包之后这里把草稿补成最新；用户一旦拖拽/清空过就不再自动覆盖）。
     */
    private void syncDraftsIfClean() {
        loadDraftsIfNeeded();
        if (!draftsLoaded || dirty) {
            return;
        }
        PlayerMagicPool pool = pool();
        if (pool == null) {
            return;
        }
        for (int p = 0; p < PAGES; p++) {
            int[] live = pool.copyWheelPage(p);
            for (int i = 0; i < SLOTS; i++) {
                draft[p][i] = i < live.length ? live[i] : EMPTY;
            }
        }
    }

    private WheelData.Slot[] activeSlots() {
        return WheelData.activeSpellsFromPool();
    }

    /** PASSIVE 页只读列表：魔法池里的被动法术（目前只有规划中的示例，框架就绪后自然出现）。 */
    private WheelData.Slot[] passiveSlots() {
        PlayerMagicPool pool = pool();
        if (pool == null) {
            return new WheelData.Slot[0];
        }
        List<WheelData.Slot> out = new ArrayList<>();
        int count = pool.getSlots();
        for (int i = 0; i < count; i++) {
            ItemStack stack = pool.getStackInSlot(i);
            if (stack.isEmpty() || !MagicStats.isSpell(stack) || !MagicStats.isPassive(stack)) {
                continue;
            }
            out.add(WheelData.slot(-1, i, stack, pool));
        }
        return out.toArray(new WheelData.Slot[0]);
    }

    private WheelData.Slot[] codexSlots() {
        return codexTab == CODEX_PASSIVE ? passiveSlots() : activeSlots();
    }

    /** 按魔法池槽位找视图（ACTIVE / PASSIVE 都找）。找不到返回 null。 */
    private WheelData.Slot findPoolSlot(int poolSlot) {
        if (poolSlot < 0) {
            return null;
        }
        for (WheelData.Slot slot : activeSlots()) {
            if (slot.poolSlot() == poolSlot) {
                return slot;
            }
        }
        for (WheelData.Slot slot : passiveSlots()) {
            if (slot.poolSlot() == poolSlot) {
                return slot;
            }
        }
        return null;
    }

    /** 当前页里这个池槽占用的方位（-1 = 不在当前页）。 */
    private int socketOfPoolSlot(int poolSlot) {
        if (poolSlot < 0) {
            return -1;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (draft[page][i] == poolSlot) {
                return i;
            }
        }
        return -1;
    }

    /** 详情面板的 ASSIGNED 文案：跨页扫描，方便看到同一法术在别页的绑定。 */
    private String assignedLabel(int poolSlot) {
        if (poolSlot < 0) {
            return "-";
        }
        for (int p = 0; p < PAGES; p++) {
            for (int i = 0; i < SLOTS; i++) {
                if (draft[p][i] == poolSlot) {
                    return tr("rotten_ruins.wheel.loadout.assigned_at",
                            PAGE_LABELS[p], i + 1);
                }
            }
        }
        return tr("rotten_ruins.wheel.loadout.not_assigned");
    }

    private int countBound(int p) {
        int bound = 0;
        for (WheelData.Slot slot : WheelData.fromMapping(draft[p])) {
            if (slot.occupied()) {
                bound++;
            }
        }
        return bound;
    }

    private WheelData.Slot[] wheelSlots() {
        return WheelData.fromMapping(draft[page]);
    }

    /** 当前详情要显示的法术：优先选中的轮盘方位，其次选中的图鉴卡片。 */
    private WheelData.Slot selectedView() {
        if (selectedSocket >= 0 && selectedSocket < SLOTS) {
            return wheelSlots()[selectedSocket];
        }
        if (selectedPoolSlot >= 0) {
            return findPoolSlot(selectedPoolSlot);
        }
        return null;
    }

    /** 服务端同步可能把池槽内容换掉：失效的选择要清掉，避免详情停在幽灵数据上。 */
    private void validateSelection() {
        if (selectedPoolSlot >= 0 && findPoolSlot(selectedPoolSlot) == null) {
            selectedPoolSlot = -1;
        }
        if (dragPoolSlot >= 0 && findPoolSlot(dragPoolSlot) == null) {
            dragPoolSlot = -1;
            dragging = false;
        }
    }

    private void clampScroll() {
        int rows = codexSlots().length;
        int maxScroll = Math.max(0, rows - listRows);
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
    }

    // ══════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        syncDraftsIfClean();
        validateSelection();
        clampScroll();
        updateHover(mouseX, mouseY);

        float time = WheelRenderer.timeSeconds();
        float openT = clamp((Util.getMillis() - openMs) / 180.0F, 0.0F, 1.0F);

        drawBackground(gui, mouseX, mouseY, time);
        drawHeader(gui);
        // 轮盘先画：wheel_aura 的加色 quad 会溢出中栏，后画的两侧面板正好把它裁掉。
        drawCenter(gui, time, openT);
        drawLeftPanel(gui);
        drawRightPanel(gui);
        drawBottomBar(gui);
        drawDragGhost(gui, mouseX, mouseY);
    }

    private void drawBackground(GuiGraphics gui, int mouseX, int mouseY, float time) {
        // 深空星图（项目真实 GUI 着色器）；失败退回分层渐变 + 静态星点。
        float nx = width <= 0 ? 0.0F : mouseX / (float) width * 2.0F - 1.0F;
        float ny = height <= 0 ? 0.0F : mouseY / (float) height * 2.0F - 1.0F;
        boolean starfield = MagicGuiShaders.drawStarfield(gui, width, height, time, nx, ny);
        if (!starfield) {
            gui.fillGradient(0, 0, width, height, 0xFF07101D, 0xFF02040A);
            gui.fillGradient(0, 0, width, height / 2, 0x33213A5E, 0x00000000);
        }
        // 整体压暗一档，面板才跳得出来。
        gui.fill(0, 0, width, height, 0x33020810);
        gui.fillGradient(0, 0, width, Math.min(height, 70), 0x88000000, 0x00000000);
    }

    private void drawHeader(GuiGraphics gui) {
        String title = tr("rotten_ruins.gui.wheel.loadout.title");
        WheelRenderer.textCenteredScaled(gui, title, width / 2.0F, titleY,
                WheelRenderer.withAlpha(GOLD_BRIGHT, 250), true, titleScale);
        String subtitle = tr("rotten_ruins.wheel.loadout.subtitle", PAGE_LABELS[page]);
        WheelRenderer.textCentered(gui, subtitle, width / 2.0F, subtitleY,
                WheelRenderer.withAlpha(GOLD, 190), false);

        // 标题两侧的金线 + 星点（和设计稿一致；放不下就自动省略）。
        if (WheelGuiShaders.sdfAvailable()) {
            int half = Math.round(font.width(title) * titleScale * 0.5F);
            float lineY = titleY + titleScale * 4.5F;
            float leftEnd = width / 2.0F - half - 8.0F;
            float rightStart = width / 2.0F + half + 8.0F;
            if (leftEnd - 26 > margin + 2) {
                WheelGuiShaders.sdfLine(gui, leftEnd - 26, lineY, leftEnd, lineY, 0.5F,
                        WheelRenderer.withAlpha(GOLD, 110));
                WheelGuiShaders.sdfStar(gui, leftEnd - 28, lineY, 2.6F, 1.0F, 4, 0.0F,
                        WheelRenderer.withAlpha(GOLD_BRIGHT, 160));
            }
            if (rightStart + 26 < width - margin - 2) {
                WheelGuiShaders.sdfLine(gui, rightStart, lineY, rightStart + 26, lineY, 0.5F,
                        WheelRenderer.withAlpha(GOLD, 110));
                WheelGuiShaders.sdfStar(gui, rightStart + 28, lineY, 2.6F, 1.0F, 4, 0.0F,
                        WheelRenderer.withAlpha(GOLD_BRIGHT, 160));
            }
        }
        // 关闭提示（M 在装载界面里不接管，只保留 ESC）。
        WheelRenderer.textRight(gui, tr("rotten_ruins.wheel.loadout.close"), width - margin, titleY + 2,
                WheelRenderer.withAlpha(TEXT_FAINT, 170), false);
    }

    // ── 中间：轮盘预览 ──

    private void drawCenter(GuiGraphics gui, float time, float openT) {
        int bound = countBound(page);

        // 轮盘背后的柔光底：让星图上的轮盘更聚光。
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfDisc(gui, wheelCx, wheelCy, wheelOuter * 1.28F,
                    WheelRenderer.withAlpha(DEEP, 90), WheelRenderer.withAlpha(DEEP, 30));
        }

        if (wheelCy - wheelOuter > panelY + 14) {
            WheelRenderer.textCentered(gui, tr("rotten_ruins.wheel.loadout.preview"), wheelCx, panelY + 4,
                    WheelRenderer.withAlpha(GOLD, 160), false);
        }

        WheelRenderer.render(gui, wheelCx, wheelCy, wheelOuter, wheelInner, wheelSlots(),
                hoveredSocket, selectedSocket, openT, 0.0F, true, time);

        // 拖拽 / 悬停方位的高亮圈（落在图标位置，和渲染器的 hover 效果叠一层）。
        if (hoveredSocket >= 0) {
            float iconRadius = (wheelOuter + wheelInner) * 0.5F;
            float sx = WheelRenderer.slotX(wheelCx, iconRadius, hoveredSocket);
            float sy = WheelRenderer.slotY(wheelCy, iconRadius, hoveredSocket);
            if (WheelGuiShaders.sdfAvailable()) {
                WheelGuiShaders.sdfRing(gui, sx, sy, 11.0F, 12.0F,
                        WheelRenderer.withAlpha(GOLD_BRIGHT, 200),
                        WheelRenderer.withAlpha(GOLD_BRIGHT, 200), false);
                if (dragging) {
                    WheelGuiShaders.sdfRing(gui, sx, sy, 14.0F, 15.0F,
                            WheelRenderer.withAlpha(GOLD, 130),
                            WheelRenderer.withAlpha(GOLD, 130), false);
                }
            }
        }

        // 轮盘下方只留一行"轮盘 I | 3/8 方位"摘要；其余操作提示已由底部栏统一显示，
        // 不再重复（三行重复文案是上一版的实际问题）。
        int statusY = Math.round(wheelCy + wheelOuter + 8);
        if (statusY + 10 < contentBottom) {
            WheelRenderer.textCentered(gui, tr("rotten_ruins.wheel.loadout.preview_summary",
                            PAGE_LABELS[page], bound, SLOTS), wheelCx, statusY,
                    WheelRenderer.withAlpha(GOLD_BRIGHT, 235), true);
        }
    }

    // ── 左栏：法术图鉴 ──

    private void drawLeftPanel(GuiGraphics gui) {
        drawPanel(gui, leftX, panelY, panelW, panelH);

        int pad = 10;
        WheelRenderer.text(gui, tr("rotten_ruins.wheel.loadout.codex"), leftX + pad, panelY + 6,
                WheelRenderer.withAlpha(GOLD_BRIGHT, 235), true);
        String count = codexTab == CODEX_PASSIVE
                ? tr("rotten_ruins.wheel.loadout.count_passive", codexSlots().length)
                : tr("rotten_ruins.wheel.loadout.count_active", codexSlots().length);
        WheelRenderer.textRight(gui, count, leftX + panelW - pad,
                panelY + 6, WheelRenderer.withAlpha(GOLD, 160), false);

        // ACTIVE / PASSIVE 页签
        int tabY = panelY + 19;
        int tabH = 16;
        int tabW = Math.max(40, (panelW - pad * 2 - 6) / 2);
        Rect activeTab = new Rect(leftX + pad, tabY, tabW, tabH);
        Rect passiveTab = new Rect(leftX + pad + tabW + 6, tabY, tabW, tabH);
        drawTab(gui, activeTab, tr("rotten_ruins.wheel.loadout.tab.active"), codexTab == CODEX_ACTIVE);
        drawTab(gui, passiveTab, tr("rotten_ruins.wheel.loadout.tab.passive"), codexTab == CODEX_PASSIVE);

        gui.fill(leftX + pad, panelY + 39, leftX + panelW - pad, panelY + 40,
                WheelRenderer.withAlpha(GOLD, 55));

        drawCodexList(gui);
    }

    private void drawCodexList(GuiGraphics gui) {
        WheelData.Slot[] slots = codexSlots();
        int pad = 8;
        int cardX = leftX + pad;
        int cardW = panelW - pad * 2;
        int listTop = panelY + 42;
        boolean passive = codexTab == CODEX_PASSIVE;

        if (slots.length == 0) {
            WheelRenderer.textCentered(gui, passive
                            ? tr("rotten_ruins.wheel.loadout.no_passive")
                            : tr("rotten_ruins.wheel.loadout.no_active"),
                    leftX + panelW / 2.0F, listTop + 18,
                    WheelRenderer.withAlpha(TEXT_FAINT, 220), false);
            WheelRenderer.textCentered(gui,
                    passive ? tr("rotten_ruins.wheel.loadout.passive_hint")
                            : tr("rotten_ruins.wheel.loadout.equip_hint"),
                    leftX + panelW / 2.0F, listTop + 32,
                    WheelRenderer.withAlpha(TEXT_FAINT, 170), false);
            return;
        }

        for (int row = 0; row < listRows; row++) {
            int index = scrollRow + row;
            if (index >= slots.length) {
                break;
            }
            int y = listTop + row * CARD_STEP;
            drawCard(gui, slots[index], index, cardX, y, cardW, CARD_H);
        }

        // 底部计数 + 滚动提示（面板太矮时省略，避免压住最后一张卡）
        int footerY = panelY + panelH - 14;
        if (footerY >= listTop + listRows * CARD_STEP + 2) {
            WheelRenderer.text(gui, passive
                            ? tr("rotten_ruins.wheel.loadout.footer_readonly")
                            : tr("rotten_ruins.wheel.loadout.footer_active"), leftX + 10, footerY,
                    WheelRenderer.withAlpha(TEXT_FAINT, 190), false);
            String right = tr("rotten_ruins.wheel.loadout.total", slots.length);
            if (slots.length > listRows) {
                right = tr("rotten_ruins.wheel.loadout.scroll",
                        scrollRow + 1, Math.min(slots.length, scrollRow + listRows), slots.length);
            }
            WheelRenderer.textRight(gui, right, leftX + panelW - 10, footerY,
                    WheelRenderer.withAlpha(TEXT_FAINT, 170), false);
        }
    }

    private void drawCard(GuiGraphics gui, WheelData.Slot slot, int index,
                          int x, int y, int w, int h) {
        boolean passive = slot.passive();
        boolean hovered = index == hoveredCard;
        boolean selected = selectedPoolSlot >= 0 && slot.poolSlot() == selectedPoolSlot;
        int boundSocket = passive ? -1 : socketOfPoolSlot(slot.poolSlot());

        int top;
        int bottom;
        int border;
        if (passive) {
            top = CARD_DISABLED_TOP;
            bottom = CARD_DISABLED_BOTTOM;
            border = 0x33C9A227;
        } else if (selected) {
            top = CARD_SELECT_TOP;
            bottom = CARD_SELECT_BOTTOM;
            border = 0xCCE7C46A;
        } else if (hovered) {
            top = CARD_HOVER_TOP;
            bottom = CARD_HOVER_BOTTOM;
            border = 0xAAC9A227;
        } else {
            top = CARD_TOP;
            bottom = CARD_BOTTOM;
            border = 0x40C9A227;
        }
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(gui, x, y, w, h, 2.0F, top, bottom, border, 1.0F);
        } else {
            gui.fillGradient(x, y, x + w, y + h, top, bottom);
            gui.renderOutline(x, y, w, h, border);
        }

        int accent = WheelData.argb(slot.color(), passive ? 0.35F : (selected || hovered ? 1.0F : 0.75F));
        gui.fill(x + 1, y + 2, x + 3, y + h - 2, accent);
        WheelRenderer.drawItemIcon(gui, slot.stack(), x + 14, y + h / 2.0F, 16.0F);

        String badge = boundSocket >= 0
                ? tr("rotten_ruins.wheel.loadout.slot_badge", boundSocket + 1) : null;
        int badgeW = badge == null ? 0 : font.width(badge) + 8;
        int textX = x + 26;
        int textMax = Math.max(12, w - 26 - 6 - badgeW - (passive ? 16 : 0));
        String name = font.plainSubstrByWidth(slot.name(), textMax, true);
        WheelRenderer.text(gui, name, textX, y + 4,
                passive ? WheelRenderer.withAlpha(TEXT_FAINT, 190)
                        : (selected ? GOLD_BRIGHT : (hovered ? TEXT : TEXT_DIM)), false);
        String sub = passive ? tr("rotten_ruins.wheel.loadout.passive_always")
                : tr("rotten_ruins.wheel.loadout.card_sub",
                        slot.school(), slot.manaCost(), cooldownText(slot));
        sub = font.plainSubstrByWidth(sub, textMax, true);
        WheelRenderer.text(gui, sub, textX, y + 16,
                WheelRenderer.withAlpha(TEXT_FAINT, passive ? 160 : 215), false);

        if (badge != null) {
            int bx = x + w - badgeW - 4;
            gui.fill(bx, y + 4, bx + badgeW, y + 14, 0xCC2A1B4D);
            gui.fill(bx, y + 4, bx + badgeW, y + 5, WheelRenderer.withAlpha(GOLD, 210));
            WheelRenderer.textCentered(gui, badge, bx + badgeW / 2.0F, y + 5, GOLD_BRIGHT, false);
        }
        if (passive) {
            drawLock(gui, x + w - 10, y + h / 2.0F, LOCKED);
        }
    }

    // ── 右栏：详情 ──

    private void drawRightPanel(GuiGraphics gui) {
        drawPanel(gui, rightX, panelY, panelW, panelH);
        int pad = Math.max(8, Math.round(10.0F * uiScale));
        int innerX = rightX + pad;
        int innerW = panelW - pad * 2;
        WheelRenderer.text(gui, tr("rotten_ruins.wheel.loadout.details"), innerX, panelY + 6,
                WheelRenderer.withAlpha(GOLD, 200), false);

        WheelData.Slot slot = selectedView();
        if (slot == null || !slot.occupied()) {
            drawRightPlaceholder(gui, innerX, innerW);
            return;
        }

        boolean passive = slot.passive();
        float[] color = slot.color();
        int iconSize = Math.max(22, Math.min(40, Math.round(40.0F * Math.min(1.0F, uiScale))));
        int centerX = rightX + panelW / 2;
        int iconCy = panelY + 6 + 9 + 8 + iconSize / 2;

        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfDisc(gui, centerX, iconCy, iconSize * 0.66F,
                    WheelRenderer.withAlpha(DEEP, 220), WheelRenderer.withAlpha(DEEP, 220));
            WheelGuiShaders.sdfRing(gui, centerX, iconCy, iconSize * 0.58F, iconSize * 0.64F,
                    WheelData.argb(color, 0.9F), WheelData.argb(color, 0.9F), false);
        } else {
            gui.fill(centerX - iconSize / 2, iconCy - iconSize / 2,
                    centerX + iconSize / 2, iconCy + iconSize / 2,
                    WheelRenderer.withAlpha(DEEP, 200));
        }
        WheelRenderer.drawItemIcon(gui, slot.stack(), centerX, iconCy, iconSize);

        int nameY = iconCy + iconSize / 2 + 6;
        WheelRenderer.textCentered(gui, font.plainSubstrByWidth(slot.name(), innerW, true),
                centerX, nameY, WheelRenderer.withAlpha(TEXT, 250), true);
        WheelRenderer.textCentered(gui, slot.school() + "   |   "
                        + (passive ? tr("rotten_ruins.wheel.loadout.tab.passive")
                        : tr("rotten_ruins.wheel.loadout.tab.active")),
                centerX, nameY + 13, WheelData.argb(color, 0.95F), false);

        int statY = nameY + 13 + Math.max(13, Math.round(15.0F * uiScale));
        int step = Math.max(11, Math.round(15.0F * Math.min(1.0F, uiScale)));
        int row = 0;
        row = drawStatRow(gui, innerX, innerW, statY, step, row,
                tr("rotten_ruins.wheel.loadout.type"),
                passive ? tr("rotten_ruins.wheel.loadout.always_on") : slot.using());
        row = drawStatRow(gui, innerX, innerW, statY, step, row,
                tr("rotten_ruins.wheel.loadout.mana"),
                passive ? "-" : slot.manaCost() + "");
        row = drawStatRow(gui, innerX, innerW, statY, step, row,
                tr("rotten_ruins.wheel.loadout.cooldown"),
                passive ? "-" : cooldownText(slot));
        if (!passive) {
            int chargeTicks = MagicStats.chargeTicks(player(), slot.stack());
            if (chargeTicks > 0) {
                row = drawStatRow(gui, innerX, innerW, statY, step, row,
                        tr("rotten_ruins.wheel.loadout.charge"), formatTicks(chargeTicks));
            }
            int sustain = MagicStats.keepCostPerSecond(player(), slot.stack());
            if (sustain > 0) {
                row = drawStatRow(gui, innerX, innerW, statY, step, row,
                        tr("rotten_ruins.wheel.loadout.sustain"),
                        tr("rotten_ruins.wheel.loadout.per_second", sustain));
            }
        }
        drawStatRow(gui, innerX, innerW, statY, step, row,
                tr("rotten_ruins.wheel.loadout.assigned"),
                passive ? "-" : assignedLabel(slot.poolSlot()));

        int dividerY = statY + (row + 1) * step + 4;
        int bottom = panelY + panelH - 8;
        if (dividerY + 4 > bottom) {
            // 面板太矮：属性行已经填满，描述区直接省略，绝不画到面板外。
            return;
        }
        gui.fill(innerX, dividerY, innerX + innerW, dividerY + 1,
                WheelRenderer.withAlpha(GOLD, 60));

        int descLabelY = dividerY + 8;
        int loreY = descLabelY + 13;
        int warnReserve = passive ? 22 : 0;
        if (loreY + 10 <= bottom - warnReserve) {
            WheelRenderer.text(gui, tr("rotten_ruins.wheel.loadout.description"), innerX, descLabelY,
                    WheelRenderer.withAlpha(TEXT_FAINT, 220), false);
            int maxLines = Math.max(1, Math.min(6, (bottom - loreY - warnReserve) / 10));
            List<String> lore = wrapLines(descriptionFor(slot), innerW, maxLines);
            for (int i = 0; i < lore.size(); i++) {
                WheelRenderer.text(gui, lore.get(i), innerX, loreY + i * 10,
                        WheelRenderer.withAlpha(TEXT_DIM, 235), false);
            }
        }
        if (passive && bottom - 20 > loreY) {
            int warnY = bottom - 20;
            WheelRenderer.text(gui, tr("rotten_ruins.wheel.loadout.passive_warn1"), innerX, warnY,
                    WheelRenderer.withAlpha(LOCKED, 235), false);
            WheelRenderer.text(gui, tr("rotten_ruins.wheel.loadout.passive_warn2"), innerX, warnY + 10,
                    WheelRenderer.withAlpha(LOCKED, 235), false);
        }
    }

    private void drawRightPlaceholder(GuiGraphics gui, int innerX, int innerW) {
        int centerX = rightX + panelW / 2;
        int centerY = panelY + panelH / 2 - 18;
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRing(gui, centerX, centerY - 22, 15.0F, 16.0F,
                    WheelRenderer.withAlpha(GOLD, 90), WheelRenderer.withAlpha(GOLD, 90), false);
            WheelGuiShaders.sdfStar(gui, centerX, centerY - 22, 8.0F, 3.0F, 4, 0.0F,
                    WheelRenderer.withAlpha(GOLD, 100));
        }
        if (selectedSocket >= 0) {
            WheelRenderer.textCentered(gui, tr("rotten_ruins.wheel.loadout.socket", selectedSocket + 1), centerX, centerY,
                    WheelRenderer.withAlpha(GOLD, 220), true);
            WheelRenderer.textCentered(gui, tr("rotten_ruins.wheel.loadout.empty_socket"), centerX, centerY + 14,
                    WheelRenderer.withAlpha(TEXT_FAINT, 220), false);
            WheelRenderer.textCentered(gui, tr("rotten_ruins.wheel.loadout.drag_here"), centerX, centerY + 27,
                    WheelRenderer.withAlpha(TEXT_FAINT, 180), false);
        } else {
            WheelRenderer.textCentered(gui, tr("rotten_ruins.wheel.loadout.no_selection"), centerX, centerY,
                    WheelRenderer.withAlpha(TEXT_FAINT, 220), false);
            WheelRenderer.textCentered(gui, tr("rotten_ruins.wheel.loadout.click_hint"), centerX, centerY + 14,
                    WheelRenderer.withAlpha(TEXT_FAINT, 180), false);
        }
    }

    // ── 底栏：页签 + 按钮 + 提示 ──

    private void drawBottomBar(GuiGraphics gui) {
        gui.fill(0, bottomBarY, width, bottomBarY + bottomBarH, 0x8C050912);
        gui.fill(0, bottomBarY, width, bottomBarY + 1,
                WheelRenderer.withAlpha(GOLD, 70));

        int rowY = bottomBarY + 9;
        boolean showPageLabel = width >= 420;
        if (showPageLabel) {
            WheelRenderer.text(gui, tr("rotten_ruins.wheel.loadout.wheel_page"), margin, rowY + 5,
                    WheelRenderer.withAlpha(GOLD, 190), false);
        }
        int tabStart = pageTabStartX();
        for (int p = 0; p < PAGES; p++) {
            Rect rect = pageTabRect(p, tabStart);
            boolean active = p == page;
            boolean hovered = p == hoveredPage;
            drawPageTab(gui, rect, PAGE_LABELS[p], active, hovered);
        }

        for (int i = 0; i < BUTTON_KEYS.length; i++) {
            drawButton(gui, buttonRect(i), tr(BUTTON_KEYS[i]), i == hoveredButton,
                    i == BTN_SAVE, isButtonEnabled(i));
        }

        String hint = tr("rotten_ruins.wheel.loadout.hint", PAGE_LABELS[page]);
        if (font.width(hint) > width - margin * 2) {
            hint = tr("rotten_ruins.wheel.loadout.hint_short");
        }
        if (font.width(hint) > width - margin * 2) {
            hint = tr("rotten_ruins.wheel.loadout.hint_tiny");
        }
        WheelRenderer.textCentered(gui, hint, width / 2.0F, bottomBarY + 34,
                WheelRenderer.withAlpha(TEXT_FAINT, 190), false);
    }

    private boolean isButtonEnabled(int index) {
        if (index == BTN_CLEAR) {
            if (selectedSocket >= 0 && draft[page][selectedSocket] >= 0) {
                return true;
            }
            return selectedPoolSlot >= 0 && socketOfPoolSlot(selectedPoolSlot) >= 0;
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // 基础控件绘制
    // ══════════════════════════════════════════════════════════════

    private void drawPanel(GuiGraphics gui, int x, int y, int w, int h) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(gui, x, y, w, h, 3.0F, PANEL_TOP, PANEL_BOTTOM,
                    PANEL_BORDER, 1.0F);
        } else {
            gui.fillGradient(x, y, x + w, y + h, PANEL_TOP, PANEL_BOTTOM);
            gui.renderOutline(x, y, w, h, PANEL_BORDER);
        }
        // 顶部金色渐变线（左→右由暗到亮再到暗）
        int lineW = Math.max(0, w - 12);
        if (lineW > 4) {
            gui.fill(x + 6, y, x + 6 + lineW, y + 1,
                WheelRenderer.withAlpha(GOLD, 60));
            gui.fill(x + 6 + lineW / 4, y, x + 6 + lineW * 3 / 4, y + 1,
                WheelRenderer.withAlpha(GOLD_BRIGHT, 110));
        }
    }

    private void drawTab(GuiGraphics gui, Rect rect, String label, boolean active) {
        int top = active ? 0x40E7C46A : 0x20000000;
        int bottom = active ? 0x30C9A227 : 0x10000000;
        int border = active ? 0xDDE7C46A : 0x55C9A227;
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(gui, rect.x(), rect.y(), rect.w(), rect.h(), 2.0F,
                    top, bottom, border, 1.0F);
        } else {
            gui.fillGradient(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), top, bottom);
            gui.renderOutline(rect.x(), rect.y(), rect.w(), rect.h(), border);
        }
        WheelRenderer.textCentered(gui, label, rect.x() + rect.w() / 2.0F,
                rect.y() + (rect.h() - 9) / 2 + 1,
                WheelRenderer.withAlpha(active ? GOLD_BRIGHT : TEXT_FAINT, 235), false);
    }

    private void drawPageTab(GuiGraphics gui, Rect rect, String label,
                             boolean active, boolean hovered) {
        int top = active ? 0x40E7C46A : (hovered ? 0x30E7C46A : 0x20000000);
        int bottom = active ? 0x30C9A227 : 0x10000000;
        int border = active ? 0xDDE7C46A : (hovered ? 0x99C9A227 : 0x55C9A227);
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(gui, rect.x(), rect.y(), rect.w(), rect.h(), 2.0F,
                    top, bottom, border, 1.0F);
        } else {
            gui.fillGradient(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), top, bottom);
            gui.renderOutline(rect.x(), rect.y(), rect.w(), rect.h(), border);
        }
        WheelRenderer.textCentered(gui, label, rect.x() + rect.w() / 2.0F,
                rect.y() + (rect.h() - 9) / 2 + 1,
                WheelRenderer.withAlpha(active ? GOLD_BRIGHT : TEXT_FAINT, active ? 245 : 210),
                false);
    }

    private void drawButton(GuiGraphics gui, Rect rect, String label,
                            boolean hovered, boolean primary, boolean enabled) {
        int top;
        int bottom;
        int border;
        if (!enabled) {
            top = 0x80070C15;
            bottom = 0x80070C15;
            border = 0x448E8776;
        } else if (primary) {
            top = hovered ? 0x66E7C46A : 0x40E7C46A;
            bottom = hovered ? 0x40C9A227 : 0x30C9A227;
            border = hovered ? 0xFFFFE49A : 0xCCE7C46A;
        } else {
            top = hovered ? 0x30E7C46A : 0xE60C1628;
            bottom = hovered ? 0x24C9A227 : 0xE6060912;
            border = hovered ? 0xCCE7C46A : 0x88C9A227;
        }
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(gui, rect.x(), rect.y(), rect.w(), rect.h(), 2.5F,
                    top, bottom, border, 1.0F);
        } else {
            gui.fillGradient(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), top, bottom);
            gui.renderOutline(rect.x(), rect.y(), rect.w(), rect.h(), border);
        }
        int color = !enabled ? WheelRenderer.withAlpha(TEXT_FAINT, 200)
                : (primary ? GOLD_BRIGHT : GOLD);
        WheelRenderer.textCentered(gui, label, rect.x() + rect.w() / 2.0F,
                rect.y() + (rect.h() - 9) / 2 + 1, color, false);
    }

    private int drawStatRow(GuiGraphics gui, int x, int w, int statY, int step, int row,
                            String label, String value) {
        int y = statY + row * step;
        WheelRenderer.text(gui, label, x, y, WheelRenderer.withAlpha(TEXT_FAINT, 220), false);
        int avail = Math.max(20, w - font.width(label) - 8);
        WheelRenderer.textRight(gui, font.plainSubstrByWidth(value, avail, true), x + w, y,
                WheelRenderer.withAlpha(TEXT, 240), false);
        return row + 1;
    }

    /** 被动卡上的小锁（SDF 可用时是圆角锁体 + 上半环提手）。 */
    private void drawLock(GuiGraphics gui, float cx, float cy, int color) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfWedge(gui, cx, cy - 3.0F, 2.4F, 3.4F,
                    (float) Math.PI, TAU, WheelRenderer.withAlpha(color, 225),
                    WheelRenderer.withAlpha(color, 225));
            WheelGuiShaders.sdfRoundRect(gui, cx - 4.0F, cy - 1.0F, 8.0F, 6.0F, 1.0F,
                    WheelRenderer.withAlpha(color, 210), WheelRenderer.withAlpha(color, 180),
                    0, 0.0F);
        } else {
            gui.fill((int) (cx - 3), (int) (cy - 1), (int) (cx + 3), (int) (cy + 4),
                    WheelRenderer.withAlpha(color, 200));
        }
    }

    // ── 拖拽幽灵 ──

    private void drawDragGhost(GuiGraphics gui, int mouseX, int mouseY) {
        if (!dragging || dragPoolSlot < 0) {
            return;
        }
        WheelData.Slot slot = findPoolSlot(dragPoolSlot);
        if (slot == null || !slot.occupied()) {
            return;
        }
        int w = 148;
        int h = 30;
        int x = Math.max(4, Math.min(mouseX + 12, width - w - 4));
        int y = Math.max(4, Math.min(mouseY + 10, height - h - 4));
        int border = hoveredSocket >= 0 ? 0xEEFFE49A : 0x99C9A227;
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(gui, x, y, w, h, 2.5F, 0xE6121B2E, 0xE60A101E,
                    border, 1.0F);
        } else {
            gui.fillGradient(x, y, x + w, y + h, 0xE6121B2E, 0xE60A101E);
            gui.renderOutline(x, y, w, h, border);
        }
        gui.fill(x + 1, y + 2, x + 3, y + h - 2,
                WheelData.argb(slot.color(), 1.0F));
        WheelRenderer.drawItemIcon(gui, slot.stack(), x + 14, y + h / 2.0F, 16.0F);
        WheelRenderer.text(gui, font.plainSubstrByWidth(slot.name(), w - 32, true),
                x + 26, y + 5, WheelRenderer.withAlpha(TEXT, 245), true);
        String sub = hoveredSocket >= 0
                ? tr("rotten_ruins.wheel.loadout.bind_to_slot", hoveredSocket + 1)
                : tr("rotten_ruins.wheel.loadout.release_on_socket");
        WheelRenderer.text(gui, sub, x + 26, y + 17,
                WheelRenderer.withAlpha(hoveredSocket >= 0 ? GOLD_BRIGHT : TEXT_FAINT, 235),
                false);
    }

    // ══════════════════════════════════════════════════════════════
    // 命中检测
    // ══════════════════════════════════════════════════════════════

    private void updateHover(int mouseX, int mouseY) {
        hoveredSocket = WheelRenderer.hoveredIndex(mouseX, mouseY,
                wheelCx, wheelCy, wheelOuter, wheelInner);

        hoveredCard = -1;
        WheelData.Slot[] slots = codexSlots();
        int listTop = panelY + 42;
        int cardX = leftX + 8;
        int cardW = panelW - 16;
        if (mouseX >= cardX && mouseX < cardX + cardW
                && mouseY >= listTop && mouseY < listTop + listRows * CARD_STEP) {
            int row = (mouseY - listTop) / CARD_STEP;
            int index = scrollRow + row;
            if (index >= 0 && index < slots.length) {
                hoveredCard = index;
            }
        }

        hoveredPage = -1;
        int tabStart = pageTabStartX();
        for (int p = 0; p < PAGES; p++) {
            // 每页都从固定起点算，避免重复累加前缀宽度导致命中矩形漂移。
            if (pageTabRect(p, tabStart).contains(mouseX, mouseY)) {
                hoveredPage = p;
            }
        }

        hoveredButton = -1;
        for (int i = 0; i < BUTTON_KEYS.length; i++) {
            if (buttonRect(i).contains(mouseX, mouseY)) {
                hoveredButton = i;
            }
        }
    }

    private int pageTabStartX() {
        return width >= 420
                ? margin + font.width(tr("rotten_ruins.wheel.loadout.wheel_page")) + 10 : margin;
    }

    private Rect pageTabRect(int pageIndex, int startX) {
        int x = startX;
        for (int p = 0; p < pageIndex; p++) {
            x += pageTabWidth(p) + 5;
        }
        return new Rect(x, bottomBarY + 9, pageTabWidth(pageIndex), 18);
    }

    /** 固定宽度：I/II/III 标签宽度不同，固定宽度才能让三个页签间距一致。 */
    private int pageTabWidth(int pageIndex) {
        return 26;
    }

    private Rect buttonRect(int index) {
        int total = 0;
        for (int i = 0; i < BUTTON_KEYS.length; i++) {
            total += buttonWidth(i) + (i > 0 ? 6 : 0);
        }
        int x = width - margin - total;
        for (int i = 0; i < index; i++) {
            x += buttonWidth(i) + 6;
        }
        return new Rect(x, bottomBarY + 9, buttonWidth(index), 18);
    }

    private int buttonWidth(int index) {
        return font.width(tr(BUTTON_KEYS[index])) + 16;
    }

    // ══════════════════════════════════════════════════════════════
    // 输入
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncDraftsIfClean();
        updateHover((int) mouseX, (int) mouseY);

        if (button == 1) {
            // 右键：清空轮盘方位（只改草稿，SAVE 时才提交）。
            if (hoveredSocket >= 0 && draft[page][hoveredSocket] >= 0) {
                draft[page][hoveredSocket] = EMPTY;
                dirty = true;
                if (selectedSocket == hoveredSocket) {
                    selectedSocket = -1;
                }
                playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.9F, 0.3F);
            }
            return hoveredSocket >= 0;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // 底栏：轮盘页签 / 按钮
        for (int p = 0; p < PAGES; p++) {
            if (pageTabRect(p, pageTabStartX()).contains(mouseX, mouseY)) {
                if (p != page) {
                    page = p;
                    selectedSocket = -1;
                    selectedPoolSlot = -1;
                    scrollRow = 0;
                    playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.3F, 0.3F);
                }
                return true;
            }
        }
        for (int i = 0; i < BUTTON_KEYS.length; i++) {
            if (buttonRect(i).contains(mouseX, mouseY)) {
                onButton(i);
                return true;
            }
        }

        // 左栏：ACTIVE / PASSIVE 页签（切页签同时清掉选择，避免详情和列表页不一致）
        if (codexTabRect(CODEX_ACTIVE).contains(mouseX, mouseY)) {
            if (codexTab != CODEX_ACTIVE) {
                codexTab = CODEX_ACTIVE;
                scrollRow = 0;
                selectedPoolSlot = -1;
                selectedSocket = -1;
                playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.3F, 0.3F);
            }
            return true;
        }
        if (codexTabRect(CODEX_PASSIVE).contains(mouseX, mouseY)) {
            if (codexTab != CODEX_PASSIVE) {
                codexTab = CODEX_PASSIVE;
                scrollRow = 0;
                selectedPoolSlot = -1;
                selectedSocket = -1;
                playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.1F, 0.3F);
            }
            return true;
        }

        // 图鉴卡片：选中；主动法术还可以拖拽绑定。
        if (hoveredCard >= 0) {
            WheelData.Slot[] slots = codexSlots();
            if (hoveredCard < slots.length) {
                WheelData.Slot slot = slots[hoveredCard];
                selectedPoolSlot = slot.poolSlot();
                selectedSocket = -1;
                playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.5F, 0.3F);
                if (!slot.passive()) {
                    dragPoolSlot = slot.poolSlot();
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                }
            }
            return true;
        }

        // 轮盘方位：选中（空位也可以选中，方便看空位提示 / CLEAR SLOT 状态）
        if (hoveredSocket >= 0) {
            if (selectedSocket == hoveredSocket) {
                selectedSocket = -1;
                selectedPoolSlot = -1;
            } else {
                selectedSocket = hoveredSocket;
                int poolSlot = draft[page][hoveredSocket];
                selectedPoolSlot = poolSlot >= 0 ? poolSlot : -1;
            }
            playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.3F);
            return true;
        }

        // 点空白处取消选择
        selectedSocket = -1;
        selectedPoolSlot = -1;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragPoolSlot >= 0) {
            if (!dragging
                    && (Math.abs(mouseX - dragStartX) > 3.0D || Math.abs(mouseY - dragStartY) > 3.0D)) {
                dragging = true;
            }
            updateHover((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragPoolSlot >= 0) {
            updateHover((int) mouseX, (int) mouseY);
            if (dragging) {
                if (hoveredSocket >= 0 && hoveredSocket < SLOTS) {
                    bindSpell(hoveredSocket, dragPoolSlot);
                } else {
                    playUiSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.4F);
                }
            }
            dragging = false;
            dragPoolSlot = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        WheelData.Slot[] slots = codexSlots();
        if (slots.length <= listRows) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int listTop = panelY + 42;
        int cardX = leftX + 8;
        int cardW = panelW - 16;
        if (mouseX >= cardX && mouseX < cardX + cardW
                && mouseY >= listTop && mouseY < listTop + listRows * CARD_STEP) {
            scrollRow -= (int) Math.signum(delta) * 1;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ══════════════════════════════════════════════════════════════
    // 操作
    // ══════════════════════════════════════════════════════════════

    private Rect codexTabRect(int tab) {
        int pad = 10;
        int tabY = panelY + 19;
        int tabH = 16;
        int tabW = Math.max(40, (panelW - pad * 2 - 6) / 2);
        int x = leftX + pad + (tab == CODEX_PASSIVE ? tabW + 6 : 0);
        return new Rect(x, tabY, tabW, tabH);
    }

    /** 把一个魔法池槽绑定到当前页的方位；同页内先去重（同一法术只出现一次）。 */
    private void bindSpell(int socket, int poolSlot) {
        if (socket < 0 || socket >= SLOTS || poolSlot < 0) {
            return;
        }
        if (draft[page][socket] == poolSlot && socketOfPoolSlot(poolSlot) == socket) {
            // 拖回原来的位置：不算改动。
            selectedSocket = socket;
            selectedPoolSlot = poolSlot;
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (draft[page][i] == poolSlot) {
                draft[page][i] = EMPTY;
            }
        }
        draft[page][socket] = poolSlot;
        dirty = true;
        selectedSocket = socket;
        selectedPoolSlot = poolSlot;
        playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.9F, 0.4F);
    }

    private void onButton(int index) {
        switch (index) {
            case BTN_CLEAR -> clearSelection();
            case BTN_AUTOFILL -> autoFill();
            case BTN_SAVE -> save();
            default -> {
            }
        }
    }

    private void clearSelection() {
        int socket = selectedSocket;
        if (socket < 0 && selectedPoolSlot >= 0) {
            socket = socketOfPoolSlot(selectedPoolSlot);
        }
        if (socket < 0 || draft[page][socket] < 0) {
            playUiSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.4F);
            return;
        }
        draft[page][socket] = EMPTY;
        dirty = true;
        if (selectedSocket >= 0 && selectedSocket == socket) {
            selectedSocket = -1;
        }
        playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.9F, 0.35F);
    }

    private void autoFill() {
        WheelData.Slot[] active = activeSlots();
        if (active.length == 0) {
            playUiSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.4F);
            return;
        }
        PlayerMagicPool pool = pool();
        int poolCount = pool == null ? 0 : Math.max(1, pool.getSlots());
        boolean[] used = new boolean[poolCount];
        for (int i = 0; i < SLOTS; i++) {
            int poolSlot = draft[page][i];
            if (poolSlot >= 0 && poolSlot < used.length) {
                used[poolSlot] = true;
            }
        }
        int next = 0;
        int filled = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (draft[page][i] >= 0) {
                continue;
            }
            while (next < active.length
                    && (active[next].poolSlot() < 0
                    || active[next].poolSlot() >= used.length
                    || used[active[next].poolSlot()])) {
                next++;
            }
            if (next >= active.length) {
                break;
            }
            int poolSlot = active[next].poolSlot();
            draft[page][i] = poolSlot;
            used[poolSlot] = true;
            next++;
            filled++;
        }
        if (filled > 0) {
            dirty = true;
            playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.45F);
        } else {
            playUiSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.4F);
        }
    }

    private void save() {
        if (!draftsLoaded) {
            // 客户端还没拿到魔法池镜像：不发配置，直接关掉（服务端本来也会拒绝）。
            onClose();
            return;
        }
        WheelActionPacket.sendConfig(page, draft[page].clone());
        dirty = false;
        playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.5F);
        onClose();
    }

    private void playUiSound(SoundEvent sound, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ══════════════════════════════════════════════════════════════
    // 文本 / 数据辅助
    // ══════════════════════════════════════════════════════════════

    private Player player() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.player;
    }

    /** 基础冷却（配置界面显示的是法术基值，不是当前剩余冷却）。 */
    private String cooldownText(WheelData.Slot slot) {
        return formatTicks(MagicStats.cooldownTicks(player(), slot.stack()));
    }

    private static String formatTicks(int ticks) {
        if (ticks <= 0) {
            return WheelI18n.tr("rotten_ruins.wheel.ready");
        }
        float seconds = ticks / 20.0F;
        if (seconds >= 10.0F) {
            return Math.round(seconds) + "s";
        }
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    private String descriptionFor(WheelData.Slot slot) {
        if (slot.passive()) {
            return tr("rotten_ruins.wheel.loadout.passive_lore");
        }
        IMagicBaseItem spell = slot.spell();
        if (spell == null) {
            return "";
        }
        UsingType using = spell.getUsingType();
        if (using == null) {
            return "";
        }
        return switch (using) {
            case IMMEDIATELY ->
                    tr("rotten_ruins.wheel.loadout.desc.instant");
            case CHARGE ->
                    tr("rotten_ruins.wheel.loadout.desc.charge");
            case KEEP ->
                    tr("rotten_ruins.wheel.loadout.desc.keep");
        };
    }

    /**
     * 按显示宽度换行：英文优先在空格处断，中文/日文没有空格，按字符断。
     * 之前只 split(" ") 处理，中文描述会被当成一个"超长单词"直接穿过右侧面板边框。
     */
    private List<String> wrapLines(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxLines <= 0 || maxWidth <= 0) {
            return lines;
        }
        String t = text.replace('\n', ' ').replace('\r', ' ');
        int i = 0;
        int n = t.length();
        while (i < n) {
            while (i < n && t.charAt(i) == ' ') {
                i++;
            }
            if (i >= n) {
                break;
            }
            if (lines.size() >= maxLines) {
                break;
            }
            int end = i;
            int lastBreak = -1;
            while (end < n) {
                char c = t.charAt(end);
                if (font.width(t.substring(i, end + 1)) > maxWidth) {
                    break;
                }
                if (c == ' ' || isCjk(c) || "：，。；！？、）》】".indexOf(c) >= 0) {
                    lastBreak = end + 1;
                }
                end++;
            }
            if (end >= n) {
                lines.add(t.substring(i).trim());
                i = n;
                break;
            }
            if (end == i) {
                // 单个字符就超宽（极窄面板）：至少吃掉一个字符，避免死循环。
                end = i + 1;
            }
            int cut = lastBreak > i ? lastBreak : end;
            lines.add(t.substring(i, cut).trim());
            i = cut;
        }
        if (i < n && !lines.isEmpty()) {
            int last = lines.size() - 1;
            int room = Math.max(8, maxWidth - font.width("..."));
            lines.set(last, font.plainSubstrByWidth(lines.get(last), room, false) + "...");
        }
        return lines;
    }

    /** 中日韩字符按字符断行；其余走空格/标点断行。 */
    private static boolean isCjk(char c) {
        return c >= 0x2E80 && c <= 0x9FFF || c >= 0xF900 && c <= 0xFAFF
                || c >= 0xFF00 && c <= 0xFFEF;
    }

    // ── 小工具 ──

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** 简单矩形（GUI 坐标）。 */
    private record Rect(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
}
