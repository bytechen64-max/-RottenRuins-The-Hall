package org.bytechen.rottenruins.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.client.rend.gui.WheelGuiShaders;
import org.bytechen.rottenruins.overworld.inventory.PotentialCraftingMenu;
import org.bytechen.rottenruins.overworld.registry.PotentialCraftingRecipes;
import org.bytechen.rottenruins.overworld.registry.blockentity.PotentialCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 奥秘仪台（法术合成台）界面 —— 「棱镜晶格 × 星象仪」重绘版。
 *
 * <h3>这一版长什么样</h3>
 * <ul>
 *   <li><b>面板</b>：半透深靛玻璃 + 四角切斜边 + 品红/青两圈 1px 错位描边（色散），
 *       整屏底子是项目真实的 {@code magic_starfield}；</li>
 *   <li><b>机器</b>：一块十六边形晶盘托住 15 个六边形晶格槽，圆心是棱镜核；
 *       已填材料到核心拉一根极细能量带 + 一颗跑动的光点；</li>
 *   <li><b>左右两条读数条</b>：与星象仪稿（{@code SceneSpellAltarAstral}）同一套画法、
 *       同一套位置，黄铜/水银金配色 —— 它们挂在面板<em>外</em>，是"另外两件仪器"，
 *       所以<b>不跟着面板统一成冷色</b>；</li>
 *   <li><b>焦点</b>：中心产物井 + 那枚按钮，其余元素压到 20%~45% 不透明度。</li>
 * </ul>
 *
 * <h3>抗锯齿：曲线与斜线全部走 SDF</h3>
 * <p>圆、圆环、圆角矩形、胶囊线段、正多边形（六边形晶格槽 / 十六边形晶盘）都走项目已有的
 * {@code sdf_ui} 着色器，入口是 {@link WheelGuiShaders}；一层里用
 * {@link WheelGuiShaders#sdfBegin}/{@link WheelGuiShaders#sdfEnd} 批量，
 * 一帧只 flush 一次，{@code uScale = GUI scale}，边界永远是 1 个物理像素的过渡。
 * 只有 1px 硬边（面板切角、发丝线、分隔线、槽外细线）用 {@code GuiGraphics.fill}，
 * 保持 MC 的像素风。着色器加载失败时自动退回扫描线绘制，界面仍可用。</p>
 *
 * <h3>没动的东西</h3>
 * <p>命中框、槽位坐标、按钮矩形、背包排布全在 {@link PotentialCraftingMenu} 里，
 * 这里只换画笔：15 个材料槽的 x/y 仍然由 {@link #updateRingLayout()} 每帧按旋转/收拢动画写入，
 * 槽框跟着 {@code slot.x/y} 画，所以动画表现与旧版完全一致。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class PotentialCraftingScreen extends AbstractContainerScreen<PotentialCraftingMenu> {

    // ══════════════════════════════════════════════════════════════
    //  不可改几何（全部来自 Menu / 命中框，面板局部坐标）
    // ══════════════════════════════════════════════════════════════

    private static final int PANEL_W = PotentialCraftingMenu.IMAGE_WIDTH;
    private static final int PANEL_H = PotentialCraftingMenu.IMAGE_HEIGHT;
    /** 四角切斜边的边长。 */
    private static final int CHAMFER = 7;

    private static final int RING_X = PotentialCraftingMenu.RING_CENTER_X;
    private static final int RING_Y = PotentialCraftingMenu.RING_CENTER_Y;
    private static final int RING_R = PotentialCraftingMenu.RING_RADIUS;
    private static final int SLOT = 18;

    private static final int BTN_X = PotentialCraftingMenu.BUTTON_X;
    private static final int BTN_Y = PotentialCraftingMenu.BUTTON_Y;
    private static final int BTN_W = PotentialCraftingMenu.BUTTON_WIDTH;
    private static final int BTN_H = PotentialCraftingMenu.BUTTON_HEIGHT;

    private static final int INV_X = 8;
    private static final int INV_Y = 182;
    private static final int HOT_Y = 236;
    private static final int PITCH = 18;

    /** 状态行（按钮正下方居中；旧版在 y=168 与背包标题压字，这里独占一行）。 */
    private static final int STATUS_Y = 166;
    /** 背包上方的分隔发丝线。 */
    private static final int INV_RULE_Y = 178;

    /** 装饰中心：取产物槽中心，晶格与同心圆才对得上。 */
    private static final float MCX_LOCAL = PotentialCraftingMenu.RING_CENTER_X + 1;
    private static final float MCY_LOCAL = PotentialCraftingMenu.RING_CENTER_Y + 1;

    /** 六边形晶格槽外接半径（切向 ~18.7、径向 ~16.2，相邻槽刚好相碰）。 */
    private static final float HEX_R = 10.8F;
    /** 晶盘外接半径 / 晶格带内外压边。 */
    private static final float PLATE_R = 60.5F;
    private static final float BAND_IN = 38.2F;
    private static final float BAND_OUT = 58.4F;

    // 左：进度柱（面板外）—— 与星象仪稿同名同值
    private static final int BAR_X = -22;
    private static final int BAR_Y = 36;
    private static final int BAR_W = 16;
    private static final int BAR_H = 96;

    // 右：势能保险丝（面板外）—— 与星象仪稿同名同值
    private static final int PWR_X = 182;
    private static final int PWR_Y = 25;
    private static final int PWR_W = 16;
    private static final int PWR_H = 6;
    private static final int PWR_GAP = 2;
    private static final int PWR_UNITS = PotentialCraftingTableBlockEntity.POWER_UNITS;

    /** 星场的时间基准：取模到 10 分钟，避免 float 精度变差。 */
    private static final long TIME_PERIOD_MS = 600_000L;

    // ══════════════════════════════════════════════════════════════
    //  配色：面板与机器走 C 案冷色；两条读数条走 A 案暖色
    // ══════════════════════════════════════════════════════════════

    private static final int C_INK = 0xFFE9F5FF;
    private static final int C_INK_DIM = 0xFF93AFC8;
    private static final int C_INK_FAINT = 0xFF5C7694;
    private static final int C_MINT = 0xFF8CF2D8;
    private static final int C_MINT_HI = 0xFFDCFFF5;
    private static final int C_CYAN = 0xFF6FD3FF;
    private static final int C_CYAN_LO = 0xFF3F7FAB;
    private static final int C_MAGENTA = 0xFFFF86D6;
    private static final int C_GLASS_TOP = 0xEE0C1226;
    private static final int C_GLASS_BOT = 0xF2070A18;
    private static final int C_PRISM = 0xF5091020;
    private static final int C_CELL = 0xE60E1B31;
    private static final int C_CELL_EMPTY = 0xD2050914;
    private static final int C_CELL_EDGE = 0xFF33506E;
    private static final int BG_FALLBACK = 0xFF05030E;

    private static final int W_GOLD = 0xFFD9B771;
    private static final int W_GOLD_HI = 0xFFF7E6BF;
    private static final int W_GOLD_LO = 0xFF6B5228;
    private static final int W_AMBER = 0xFFE9A54A;
    private static final int W_FRAME_TOP = 0xF00B0810;
    private static final int W_FRAME_BOT = 0xF006040A;
    private static final int W_GLASS_TOP = 0xC00A0813;
    private static final int W_GLASS_BOT = 0xC0020206;
    private static final int W_UNLIT_TOP = 0x8C05040A;
    private static final int W_UNLIT_BOT = 0x8C020206;
    private static final int W_MOUNT_TOP = 0xFF0C0908;
    private static final int W_MOUNT_BOT = 0xFF070506;

    private static final float TAU = (float) (Math.PI * 2.0);

    // ══════════════════════════════════════════════════════════════
    //  运行时状态
    // ══════════════════════════════════════════════════════════════

    private float smoothedProgress;
    /** 每帧从 {@code slot.x/y} 读回来的 15 个槽中心（<b>菜单局部坐标</b>；画的时候要加 leftPos/topPos）。 */
    private final float[] slotCx = new float[PotentialCraftingTableBlockEntity.RING_SLOTS];
    private final float[] slotCy = new float[PotentialCraftingTableBlockEntity.RING_SLOTS];

    public PotentialCraftingScreen(PotentialCraftingMenu menu, @NotNull Inventory inventory,
                                   @NotNull Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.titleLabelX = 10;
        this.titleLabelY = 7;
        updateRingLayout();
    }

    // ══════════════════════════════════════════════════════════════
    //  每帧：把 Menu 的槽位坐标读回来（槽位动画仍由旧逻辑写 slot.x/y）
    // ══════════════════════════════════════════════════════════════

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateRingLayout();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** 自转 + 向心收缩；未合成时回到静止布局（数值与旧版一致）。 */
    private void updateRingLayout() {
        smoothedProgress = Mth.lerp(0.22f, smoothedProgress, menu.progressFraction());
        boolean crafting = menu.isCrafting();
        float progress = crafting ? Mth.clamp(smoothedProgress, 0.0f, 1.0f) : 0.0f;

        float seconds = (float) ((System.currentTimeMillis() % 1_000_000L) / 1000.0d);
        float spin = crafting ? seconds * 1.5f * (0.5f + progress) : 0.0f;
        float radius = Mth.lerp(progress, (float) RING_R, 20.0f);

        for (int index = 0; index < PotentialCraftingTableBlockEntity.RING_SLOTS; index++) {
            double angle = Math.toRadians(PotentialCraftingMenu.ringAngleDegrees(index)) + spin;
            Slot slot = menu.slots.get(index);
            slot.x = RING_X + Mth.floor(Math.cos(angle) * radius) - 8;
            slot.y = RING_Y + Mth.floor(Math.sin(angle) * radius) - 8;
            slotCx[index] = slot.x + SLOT * 0.5f;
            slotCy[index] = slot.y + SLOT * 0.5f;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  交互（不变）
    // ══════════════════════════════════════════════════════════════

    private boolean insideButton(double mouseX, double mouseY) {
        double x = leftPos + BTN_X;
        double y = topPos + BTN_Y;
        return mouseX >= x && mouseX < x + BTN_W
                && mouseY >= y && mouseY < y + BTN_H;
    }

    /** 客户端也判得了：客户端有配方表，容器内容也已同步。 */
    private boolean canStart() {
        if (menu.isCrafting() || menu.power() <= 0 || minecraft == null || minecraft.level == null) {
            return false;
        }
        return PotentialCraftingRecipes.findMatch(minecraft.level, menu.container()) != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideButton(mouseX, mouseY) && canStart()
                && minecraft != null && minecraft.gameMode != null) {
            // 只发按钮点击包：材料校验与开始都在服务端做
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    PotentialCraftingMenu.BUTTON_START);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ══════════════════════════════════════════════════════════════
    //  背景：真实星场 + 蓝紫染色 + 四边暗角
    // ══════════════════════════════════════════════════════════════

    /** 深空底。在 {@link #renderBg} 最前面画，盖掉原版的世界压暗。 */
    private void background(GuiGraphics graphics) {
        float t = (System.currentTimeMillis() % TIME_PERIOD_MS) / 1000.0F;
        boolean stars = MagicGuiShaders.drawStarfield(graphics, this.width, this.height, t, 0.0F, 0.0F);
        if (!stars) {
            graphics.fill(0, 0, this.width, this.height, BG_FALLBACK);
        } else {
            graphics.fill(0, 0, this.width, this.height, 0x38151A4E);
        }

        int h = this.height;
        int w = this.width;
        graphics.fillGradient(0, 0, w, 96, 0xD0040412, 0x00040412);
        graphics.fillGradient(0, h - 112, w, h, 0x00040412, 0xE8040412);
        int band = Math.min(84, w / 2);
        for (int i = 0; i < band; i++) {
            float k = 1.0F - i / (float) band;
            int a = Math.round(110.0F * k * k);
            if (a <= 0) {
                continue;
            }
            int argb = (a << 24) | 0x060818;
            graphics.fill(i, 0, i + 1, h, argb);
            graphics.fill(w - 1 - i, 0, w - i, h, argb);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  前景：三段画法 —— ① 原版硬边 ② SDF 曲线 ③ 原版细节
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float progress = menu.isCrafting() ? Mth.clamp(smoothedProgress, 0.0F, 1.0F) : 0.0F;
        float time = (System.currentTimeMillis() % TIME_PERIOD_MS) / 1000.0F;
        boolean crafting = menu.isCrafting();
        boolean sdf = WheelGuiShaders.sdfAvailable();

        // ① 原版几何：星场、面板玻璃、切角、暗角、色散描边、背包格
        background(g);
        panel(g);
        inventory(g);

        // ② SDF：所有圆角 / 圆环 / 斜线 / 正多边形（整段只 flush 一次）
        if (sdf) {
            WheelGuiShaders.sdfBegin(g);
        }
        try {
            mountPlates(g);
            gaugeShells(g, time, crafting);
            machine(g, time, crafting);
            slots(g, crafting);
            buttonShell(g, crafting);
        } finally {
            if (sdf) {
                WheelGuiShaders.sdfEnd();
            }
        }

        // ③ 原版细节：液体、光面、水线、灯丝芯、按钮高光（盖在 SDF 之上）
        gaugeInteriors(g, progress);
        buttonGloss(g, crafting, insideButton(mouseX, mouseY));
        outputFrame(g);
    }

    // ── 面板：切角玻璃 + 色散描边 + 内暗角（硬边，全走 fill）──

    private void panel(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        int w = PANEL_W;
        int h = PANEL_H;
        int c = CHAMFER;

        g.fillGradient(x + c, y, x + w - c, y + c, C_GLASS_TOP, C_GLASS_TOP);
        g.fillGradient(x, y + c, x + w, y + h - c, C_GLASS_TOP, C_GLASS_BOT);
        g.fillGradient(x + c, y + h - c, x + w - c, y + h, C_GLASS_BOT, C_GLASS_BOT);
        for (int i = 0; i < c; i++) {
            int inset = c - i;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, C_GLASS_TOP);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, C_GLASS_BOT);
        }

        int vig = 30;
        g.fillGradient(x, y, x + w, y + vig, 0x70000000, 0x00000000);
        g.fillGradient(x, y + h - vig, x + w, y + h, 0x00000000, 0x70000000);
        g.fillGradient(x, y, x + vig, y + h, 0x50000000, 0x00000000);
        g.fillGradient(x + w - vig, y, x + w, y + h, 0x00000000, 0x50000000);

        chromaticEdge(g, x - 1, y - 1, w + 2, h + 2, C_MAGENTA, 90);
        chromaticEdge(g, x + 1, y + 1, w - 2, h - 2, C_CYAN, 110);
        chromaticEdge(g, x, y, w, h, C_INK, 170);
    }

    /** 1px 切角描边。 */
    private void chromaticEdge(GuiGraphics g, int x, int y, int w, int h, int color, int alpha) {
        int argb = withAlpha(color, alpha);
        int c = CHAMFER;
        g.fill(x + c, y, x + w - c, y + 1, argb);
        g.fill(x + c, y + h - 1, x + w - c, y + h, argb);
        g.fill(x, y + c, x + 1, y + h - c, argb);
        g.fill(x + w - 1, y + c, x + w, y + h - c, argb);
        for (int i = 0; i < c; i++) {
            g.fill(x + c - i - 1, y + i, x + c - i, y + i + 1, argb);
            g.fill(x + w - c + i, y + i, x + w - c + i + 1, y + i + 1, argb);
            g.fill(x + c - i - 1, y + h - 1 - i, x + c - i, y + h - i, argb);
            g.fill(x + w - c + i, y + h - 1 - i, x + w - c + i + 1, y + h - i, argb);
        }
    }

    // ── 读数条的暖色底板（SDF 圆角）──

    private void mountPlates(GuiGraphics g) {
        int totalH = PWR_UNITS * PWR_H + (PWR_UNITS - 1) * PWR_GAP;
        roundedRect(g, leftPos + BAR_X - 6, topPos + BAR_Y - 8, BAR_W + 11, BAR_H + 16, 4,
                W_MOUNT_TOP, W_MOUNT_BOT, withAlpha(W_GOLD, 44), 1.0F);
        roundedRect(g, leftPos + PWR_X - 5, topPos + PWR_Y - 8, PWR_W + 12, totalH + 16, 4,
                W_MOUNT_TOP, W_MOUNT_BOT, withAlpha(W_GOLD, 44), 1.0F);
    }

    // ── 读数条外壳（SDF）：外框 / 玻璃管 / 保险丝格 / 指示灯 ──

    private void gaugeShells(GuiGraphics g, float time, boolean crafting) {
        int x = leftPos + BAR_X;
        int y = topPos + BAR_Y;

        roundedRect(g, x - 2, y - 3, BAR_W + 3, BAR_H + 6, 3,
                W_FRAME_TOP, W_FRAME_BOT, withAlpha(W_GOLD, 92), 1.0F);
        roundedRect(g, x + 4, y + 3, BAR_W - 7, BAR_H - 6, 2,
                W_GLASS_TOP, W_GLASS_BOT, withAlpha(W_GOLD, 58), 1.0F);

        float pulse = 0.6F + 0.4F * Mth.sin(time * 3.0F);
        int lamp = crafting ? W_AMBER : W_GOLD_LO;
        solidCircle(g, x + BAR_W * 0.5F, y - 1.0F, 2.6F,
                withAlpha(lamp, crafting ? Math.round(200 * pulse) : 120));

        int px = leftPos + PWR_X;
        int py = topPos + PWR_Y;
        int totalH = PWR_UNITS * PWR_H + (PWR_UNITS - 1) * PWR_GAP;
        roundedRect(g, px - 3, py - 3, PWR_W + 6, totalH + 6, 3,
                W_FRAME_TOP, W_FRAME_BOT, withAlpha(W_GOLD, 92), 1.0F);

        int power = menu.power();
        for (int i = 0; i < PWR_UNITS; i++) {
            int qy = py + i * (PWR_H + PWR_GAP);
            boolean lit = i < power;
            roundedRect(g, px, qy, PWR_W, PWR_H, 1,
                    lit ? withAlpha(W_GOLD_LO, 150) : W_UNLIT_TOP,
                    lit ? withAlpha(W_GOLD_LO, 90) : W_UNLIT_BOT,
                    withAlpha(W_GOLD, lit ? 70 : 34), 1.0F);
        }

        if (crafting) {
            float beat = 0.5F + 0.5F * Mth.sin(time * 2.4F);
            roundedOutline(g, px - 2, py - 2, PWR_W + 4, totalH + 4, 2,
                    withAlpha(W_AMBER, Math.round(70 * beat)));
        }
    }

    // ── 读数条内部（原版 fill）：液体、液面、反光、水线、灯丝芯 ──

    private void gaugeInteriors(GuiGraphics g, float progress) {
        int x = leftPos + BAR_X;
        int y = topPos + BAR_Y;
        int innerTop = y + 5;
        int innerH = BAR_H - 10;
        int colLeft = x + 5;
        int colW = BAR_W - 10;

        int filled = Math.round(innerH * Mth.clamp(progress, 0.0F, 1.0F));
        int fillPx = Math.max(3, filled);
        int top = innerTop + innerH - fillPx;

        g.fillGradient(colLeft, top, colLeft + colW, top + fillPx,
                withAlpha(progress > 0.01F ? W_GOLD_HI : W_AMBER, progress > 0.01F ? 235 : 170),
                withAlpha(W_AMBER, 200));
        g.fill(colLeft, top, colLeft + colW, top + 1,
                withAlpha(0xFFFFFF, progress > 0.01F ? 200 : 110));
        g.fill(colLeft, innerTop, colLeft + 1, innerTop + innerH, withAlpha(0xFFFFFF, 26));

        for (int i = 0; i <= 4; i++) {
            int ty = innerTop + innerH - Math.round(innerH * i / 4.0F);
            int inset = i % 2 == 0 ? 0 : 3;
            g.fill(colLeft + inset, ty, colLeft + colW, ty + 1,
                    withAlpha(W_GOLD, i % 2 == 0 ? 78 : 44));
        }

        int px = leftPos + PWR_X;
        int py = topPos + PWR_Y;
        int power = menu.power();
        for (int i = 0; i < PWR_UNITS; i++) {
            if (i >= power) {
                continue;
            }
            int qy = py + i * (PWR_H + PWR_GAP);
            g.fill(px + 2, qy + 2, px + PWR_W - 2, qy + 4, withAlpha(W_GOLD_HI, 225));
            g.fill(px + 1, qy + 2, px + 2, qy + 4, withAlpha(W_AMBER, 200));
            g.fill(px + PWR_W - 2, qy + 2, px + PWR_W - 1, qy + 4, withAlpha(W_AMBER, 200));
        }
    }

    // ── 机器：晶盘 + 六边形晶格 + 棱镜核 + 能量带（SDF）──

    private void machine(GuiGraphics g, float time, boolean crafting) {
        int cx = leftPos + Math.round(MCX_LOCAL);
        int cy = topPos + Math.round(MCY_LOCAL);

        solidCircle(g, cx, cy, PLATE_R, 0x1A6FD3FF);
        solidCircle(g, cx, cy, PLATE_R - 8, 0x140A1424);
        polygon(g, cx, cy, PLATE_R, 16, time * 0.004F, 0, 0, withAlpha(C_CYAN_LO, 130), 1.0F);
        for (int i = 0; i < 16; i++) {
            float a = time * 0.004F + i * TAU / 16.0F;
            segment(g, cx + cos(a) * (PLATE_R - 2.5F), cy + sin(a) * (PLATE_R - 2.5F),
                    cx + cos(a) * (PLATE_R + 1.5F), cy + sin(a) * (PLATE_R + 1.5F),
                    0.5F, withAlpha(C_CYAN, 90));
        }

        bandRing(g, cx, cy, BAND_IN, 1.2F, withAlpha(C_CYAN_LO, 80));
        bandRing(g, cx, cy, BAND_OUT, 1.2F, withAlpha(C_CYAN_LO, 100));

        for (int i = 0; i < 24; i++) {
            float a0 = -time * 0.05F + i * TAU / 24.0F;
            float a1 = a0 + TAU / 24.0F * 0.5F;
            wedge(g, cx, cy, 30.4F, 31.6F, a0, a1,
                    withAlpha(C_CYAN, 70), withAlpha(C_CYAN, 30));
        }

        polygon(g, cx, cy, 24.5F, 14, -time * 0.03F, 0, 0, withAlpha(C_CYAN_LO, 80), 1.0F);

        // 能量带：已填材料 → 核心；光点沿带跑动
        for (int i = 0; i < PotentialCraftingTableBlockEntity.RING_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) {
                continue;
            }
            float sx = leftPos + slotCx[i];
            float sy = topPos + slotCy[i];
            float dx = cx - sx;
            float dy = cy - sy;
            float len = Mth.sqrt(dx * dx + dy * dy);
            if (len < 1.0E-3F) {
                continue;
            }
            float ux = dx / len;
            float uy = dy / len;
            segment(g, sx + ux * 11.0F, sy + uy * 11.0F, cx - ux * 13.0F, cy - uy * 13.0F,
                    0.5F, withAlpha(C_CYAN, crafting ? 120 : 84));
            float phase = (time * 0.45F + i * 0.11F) % 1.0F;
            float travel = 11.0F + phase * Math.max(1.0F, len - 24.0F);
            solidCircle(g, sx + ux * travel, sy + uy * travel, 1.4F, withAlpha(C_MINT_HI, 220));
        }

        // 棱镜核
        float hexRot = (float) Math.toRadians(90);
        polygon(g, cx, cy, 13.6F, 6, hexRot, C_PRISM, C_PRISM, 0x00000000, 0.0F);
        polygon(g, cx, cy, 13.6F, 6, hexRot, 0, 0,
                withAlpha(crafting ? C_MINT_HI : C_MINT, 225), 1.0F);
        segment(g, cx, cy - 13.6F, cx, cy + 13.6F, 0.5F, withAlpha(C_MINT_HI, 70));
        segment(g, cx - 11.8F, cy - 6.8F, cx + 11.8F, cy + 6.8F, 0.5F, withAlpha(C_CYAN, 60));
        polygon(g, cx, cy, 16.6F, 6, hexRot, 0, 0, withAlpha(C_MAGENTA, 70), 1.0F);
        bandRing(g, cx, cy, 19.0F, 1.0F, withAlpha(C_MINT, 90));
    }

    /** 产物槽方框（硬边，画在 SDF 之后、物品之前）。 */
    private void outputFrame(GuiGraphics g) {
        Slot out = menu.slots.get(PotentialCraftingTableBlockEntity.RING_SLOTS);
        int ox = leftPos + out.x;
        int oy = topPos + out.y;
        frame(g, ox, oy, SLOT, SLOT, withAlpha(C_MINT_HI, 120), withAlpha(C_MINT, 60));
    }

    // ── 15 个六边形晶格槽（SDF 多边形，槽框跟着 slot.x/y）──

    private void slots(GuiGraphics g, boolean crafting) {
        float hexRot = (float) Math.toRadians(90);
        for (int i = 0; i < PotentialCraftingTableBlockEntity.RING_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            float cx = leftPos + slot.x + SLOT * 0.5F;
            float cy = topPos + slot.y + SLOT * 0.5F;
            if (slot.hasItem()) {
                polygon(g, cx, cy, HEX_R, 6, hexRot, C_CELL, C_CELL,
                        withAlpha(C_CYAN, crafting ? 150 : 120), 1.0F);
                segment(g, cx - 6.0F, cy - 2.4F, cx - 2.4F, cy - 6.0F, 0.6F,
                        withAlpha(C_MINT_HI, 90));
            } else {
                polygon(g, cx, cy, HEX_R, 6, hexRot, C_CELL_EMPTY, C_CELL_EMPTY,
                        withAlpha(C_CELL_EDGE, 210), 1.0F);
                polygon(g, cx, cy, HEX_R - 3.2F, 6, hexRot, 0, 0,
                        withAlpha(C_CYAN_LO, 120), 1.0F);
            }
        }
    }

    // ── 按钮：玻璃胶囊（SDF 外壳 + 原版高光）──

    private void buttonShell(GuiGraphics g, boolean crafting) {
        int x = leftPos + BTN_X;
        int y = topPos + BTN_Y;
        int r = BTN_H / 2;

        if (!crafting) {
            roundedRect(g, x, y, BTN_W, BTN_H, r, 0xF0A8F4E4, 0xF03FCFC0,
                    withAlpha(C_MINT_HI, 210), 1.0F);
            roundedOutline(g, x - 1, y - 1, BTN_W + 2, BTN_H + 2, r + 1, withAlpha(C_MAGENTA, 70));
            roundedOutline(g, x + 1, y + 1, BTN_W - 2, BTN_H - 2, r - 1, withAlpha(C_CYAN, 90));
        } else {
            roundedRect(g, x, y, BTN_W, BTN_H, r, 0xE60C1524, 0xE6060A14,
                    withAlpha(C_CYAN_LO, 150), 1.0F);
        }
    }

    private void buttonGloss(GuiGraphics g, boolean crafting, boolean hovered) {
        if (crafting) {
            return;
        }
        int x = leftPos + BTN_X;
        int y = topPos + BTN_Y;
        g.fillGradient(x + 2, y + 2, x + BTN_W - 2, y + BTN_H / 2,
                hovered ? 0xFFE7FFF8 : 0xD8DCFFF5, withAlpha(C_MINT, hovered ? 190 : 120));
        g.fill(x + 3, y + BTN_H - 3, x + BTN_W - 3, y + BTN_H - 2, withAlpha(0xFF0B4A4A, 120));
    }

    // ── 背包：深玻璃格 + 冷白内边（硬边）──

    private void inventory(GuiGraphics g) {
        g.fill(leftPos + 8, topPos + INV_RULE_Y, leftPos + PANEL_W - 8, topPos + INV_RULE_Y + 1,
                withAlpha(C_CYAN_LO, 90));
        g.fill(leftPos + PANEL_W / 2 - 1, topPos + INV_RULE_Y, leftPos + PANEL_W / 2 + 1,
                topPos + INV_RULE_Y + 1, withAlpha(C_CYAN, 150));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                cell(g, leftPos + INV_X + col * PITCH, topPos + INV_Y + row * PITCH, false);
            }
        }
        for (int col = 0; col < 9; col++) {
            cell(g, leftPos + INV_X + col * PITCH, topPos + HOT_Y, true);
        }
    }

    private void cell(GuiGraphics g, int x, int y, boolean hotbar) {
        g.fill(x, y, x + SLOT, y + SLOT, hotbar ? 0xB20A1220 : 0xA00A1220);
        frame(g, x, y, SLOT, SLOT, withAlpha(C_CYAN_LO, hotbar ? 130 : 90), withAlpha(C_CYAN_LO, 60));
        g.fill(x + 1, y + 1, x + SLOT - 1, y + 2, 0x4C000000);
        g.fill(x + 1, y + 1, x + 2, y + SLOT - 1, 0x3C000000);
    }

    // ══════════════════════════════════════════════════════════════
    //  文字：页眉（标题 + 势能读数）、状态行、按钮字
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.getString(), titleLabelX, titleLabelY, C_INK, true);

        Component power = Component.translatable("gui.rotten_ruins.potential_crafting.power",
                menu.power(), PWR_UNITS);
        String powerText = power.getString();
        int powerX = PANEL_W - 10 - font.width(powerText);
        g.drawString(font, powerText, powerX, titleLabelY,
                menu.power() > 0 ? C_MINT_HI : 0xFFFF7A5A, true);

        g.fill(8, 17, PANEL_W - 8, 18, withAlpha(C_CYAN_LO, 110));
        g.fill(PANEL_W / 2 - 1, 17, PANEL_W / 2 + 1, 18, withAlpha(C_CYAN, 170));

        Component status;
        int color = C_INK_DIM;
        if (menu.isCrafting()) {
            status = Component.translatable("gui.rotten_ruins.potential_crafting.remaining",
                    remainingSeconds());
            color = C_MINT_HI;
        } else if (menu.power() <= 0) {
            status = Component.translatable("gui.rotten_ruins.potential_crafting.needs_power")
                    .withStyle(ChatFormatting.RED);
            color = 0xFFFF7A5A;
        } else if (menu.ringEmpty()) {
            status = Component.translatable("gui.rotten_ruins.potential_crafting.idle");
        } else {
            status = Component.translatable("gui.rotten_ruins.potential_crafting.no_recipe");
            color = 0xFFFFD27A;
        }
        String statusText = status.getString();
        int statusX = (PANEL_W - font.width(statusText)) / 2;
        g.drawString(font, statusText, statusX, STATUS_Y, color, true);

        diamond(g, statusX - 6, STATUS_Y + 3, C_MINT, 190);
        diamond(g, statusX + font.width(statusText) + 4, STATUS_Y + 3, C_MINT, 190);

        Component buttonText = Component.translatable(menu.isCrafting()
                ? "gui.rotten_ruins.potential_crafting.crafting"
                : "gui.rotten_ruins.potential_crafting.start");
        String buttonLabel = buttonText.getString();
        g.drawString(font, buttonLabel,
                BTN_X + (BTN_W - font.width(buttonLabel)) / 2, BTN_Y + 5,
                menu.isCrafting() ? withAlpha(C_INK_FAINT, 220) : 0xFF06222A,
                !menu.isCrafting());
    }

    private int remainingSeconds() {
        float left = menu.totalTicks() * (1.0F - Mth.clamp(smoothedProgress, 0.0F, 1.0F));
        return Math.max(1, Mth.ceil(left / 20.0F));
    }

    // ══════════════════════════════════════════════════════════════
    //  绘制封装：优先 SDF（逐物理像素抗锯齿），着色器不可用时退回扫描线
    // ══════════════════════════════════════════════════════════════

    private static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    private static float cos(float radians) {
        return (float) Math.cos(radians);
    }

    private static float sin(float radians) {
        return (float) Math.sin(radians);
    }

    /** 1px 边框（上亮下暗）。 */
    private static void frame(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        g.fill(x, y, x + w, y + 1, top);
        g.fill(x, y + h - 1, x + w, y + h, bottom);
        g.fill(x, y + 1, x + 1, y + h - 1, bottom);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, bottom);
    }

    /** 小菱形（状态行装饰）。 */
    private static void diamond(GuiGraphics g, int x, int y, int color, int alpha) {
        int argb = withAlpha(color, alpha);
        g.fill(x, y + 1, x + 1, y + 2, argb);
        g.fill(x + 1, y, x + 2, y + 1, argb);
        g.fill(x + 1, y + 1, x + 2, y + 2, argb);
        g.fill(x + 2, y + 1, x + 3, y + 2, argb);
        g.fill(x + 1, y + 2, x + 2, y + 3, argb);
    }

    /** 圆角矩形：SDF 填充 + 可选描边。 */
    private static void roundedRect(GuiGraphics g, float x, float y, float w, float h, float radius,
                                    int top, int bottom, int border, float borderWidth) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(g, x, y, w, h, radius, top, bottom, border, borderWidth);
        } else {
            cpuRoundRect(g, x, y, w, h, radius, top, bottom);
            if (borderWidth > 0.0F) {
                cpuRoundRectBorder(g, x, y, w, h, radius, border);
            }
        }
    }

    /** 只有描边的圆角矩形。 */
    private static void roundedOutline(GuiGraphics g, float x, float y, float w, float h,
                                       float radius, int color) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRoundRect(g, x, y, w, h, radius, 0, 0, color, 1.0F);
        } else {
            cpuRoundRectBorder(g, x, y, w, h, radius, color);
        }
    }

    /** 实心圆。 */
    private static void solidCircle(GuiGraphics g, float cx, float cy, float radius, int color) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfDisc(g, cx, cy, radius, color, color);
        } else {
            cpuDisc(g, cx, cy, radius, color);
        }
    }

    /** 圆环（宽度以像素给）。 */
    private static void bandRing(GuiGraphics g, float cx, float cy, float radius, float width, int color) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfRing(g, cx, cy, Math.max(0.0F, radius - width * 0.5F),
                    radius + width * 0.5F, color, color, false);
        } else {
            cpuRing(g, cx, cy, radius, width, color);
        }
    }

    /** 圆环扇区。 */
    private static void wedge(GuiGraphics g, float cx, float cy, float rIn, float rOut,
                              float a0, float a1, int colorIn, int colorOut) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfWedge(g, cx, cy, rIn, rOut, a0, a1, colorIn, colorOut);
        } else {
            cpuWedge(g, cx, cy, rIn, rOut, a0, a1, colorIn);
        }
    }

    /** 胶囊线段（halfWidth = 0.5 即 1px 线）。 */
    private static void segment(GuiGraphics g, float x1, float y1, float x2, float y2,
                                float halfWidth, int color) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfLine(g, x1, y1, x2, y2, halfWidth, color);
        } else {
            cpuLine(g, x1, y1, x2, y2, color);
        }
    }

    /** 正多边形（填充或描边）。 */
    private static void polygon(GuiGraphics g, float cx, float cy, float radius, int sides,
                                float rotation, int fillTop, int fillBottom,
                                int borderColor, float borderWidth) {
        if (WheelGuiShaders.sdfAvailable()) {
            WheelGuiShaders.sdfPolygon(g, cx, cy, radius, sides, rotation,
                    fillTop, fillBottom, borderColor, borderWidth);
        } else {
            if (borderWidth <= 0.0F) {
                cpuPolygonFill(g, cx, cy, radius, sides, rotation, fillTop);
            }
            cpuPolygonOutline(g, cx, cy, radius, sides, rotation, borderColor);
        }
    }

    // ── 以下为着色器缺失时的扫描线兜底（形状一致，只是没有抗锯齿）──

    private static void cpuRoundRect(GuiGraphics g, float x, float y, float w, float h,
                                     float radius, int top, int bottom) {
        int r = Math.round(radius);
        int xi = Math.round(x);
        int yi = Math.round(y);
        int wi = Math.round(w);
        int hi = Math.round(h);
        if (r <= 0) {
            g.fillGradient(xi, yi, xi + wi, yi + hi, top, bottom);
            return;
        }
        for (int i = 0; i < r; i++) {
            float f = (r - i - 0.5F) / r;
            int inset = r - Math.round(Mth.sqrt(Math.max(0.0F, 1.0F - f * f)) * r);
            g.fill(xi + inset, yi + i, xi + wi - inset, yi + i + 1, top);
            g.fill(xi + inset, yi + hi - 1 - i, xi + wi - inset, yi + hi - i, bottom);
        }
        g.fillGradient(xi, yi + r, xi + wi, yi + hi - r, top, bottom);
    }

    private static void cpuRoundRectBorder(GuiGraphics g, float x, float y, float w, float h,
                                           float radius, int color) {
        int r = Math.round(radius);
        int xi = Math.round(x);
        int yi = Math.round(y);
        int wi = Math.round(w);
        int hi = Math.round(h);
        if (r <= 0) {
            frame(g, xi, yi, wi, hi, color, color);
            return;
        }
        g.fill(xi + r, yi, xi + wi - r, yi + 1, color);
        g.fill(xi + r, yi + hi - 1, xi + wi - r, yi + hi, color);
        g.fill(xi, yi + r, xi + 1, yi + hi - r, color);
        g.fill(xi + wi - 1, yi + r, xi + wi, yi + hi - r, color);
        for (int i = 0; i < r; i++) {
            float f = (r - i - 0.5F) / r;
            int inset = r - Math.round(Mth.sqrt(Math.max(0.0F, 1.0F - f * f)) * r);
            g.fill(xi + inset, yi + i, xi + inset + 1, yi + i + 1, color);
            g.fill(xi + wi - 1 - inset, yi + i, xi + wi - inset, yi + i + 1, color);
            g.fill(xi + inset, yi + hi - 1 - i, xi + inset + 1, yi + hi - i, color);
            g.fill(xi + wi - 1 - inset, yi + hi - 1 - i, xi + wi - inset, yi + hi - i, color);
        }
    }

    private static void cpuDisc(GuiGraphics g, float cx, float cy, float radius, int color) {
        int r = Math.round(radius);
        for (int dy = -r; dy <= r; dy++) {
            float half = Mth.sqrt(Math.max(0.0F, radius * radius - dy * dy));
            int x0 = Math.round(cx - half);
            int x1 = Math.round(cx + half);
            if (x1 > x0) {
                g.fill(x0, Math.round(cy) + dy, x1, Math.round(cy) + dy + 1, color);
            }
        }
    }

    private static void cpuRing(GuiGraphics g, float cx, float cy, float radius, float width, int color) {
        float rOut = radius + width * 0.5F;
        float rIn = Math.max(0.0F, radius - width * 0.5F);
        int r = Math.round(rOut);
        for (int dy = -r; dy <= r; dy++) {
            float yo = Math.abs(dy);
            if (yo > rOut) {
                continue;
            }
            float xo = Mth.sqrt(Math.max(0.0F, rOut * rOut - yo * yo));
            float xi = yo < rIn ? Mth.sqrt(Math.max(0.0F, rIn * rIn - yo * yo)) : 0.0F;
            int cxr = Math.round(cx);
            int y = Math.round(cy) + dy;
            g.fill(cxr - Math.round(xo), y, cxr - Math.round(xi), y + 1, color);
            g.fill(cxr + Math.round(xi), y, cxr + Math.round(xo), y + 1, color);
        }
    }

    private static void cpuWedge(GuiGraphics g, float cx, float cy, float rIn, float rOut,
                                 float a0, float a1, int color) {
        int steps = Math.max(4, Math.round(Math.abs(a1 - a0) / 0.04F));
        for (int i = 0; i <= steps; i++) {
            float a = a0 + (a1 - a0) * i / (float) steps;
            int x0 = Math.round(cx + cos(a) * rIn);
            int y0 = Math.round(cy + sin(a) * rIn);
            int x1 = Math.round(cx + cos(a) * rOut);
            int y1 = Math.round(cy + sin(a) * rOut);
            g.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1) + 1, color);
        }
    }

    private static void cpuLine(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
        int steps = Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * t);
            int y = Math.round(y0 + (y1 - y0) * t);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void cpuPolygonOutline(GuiGraphics g, float cx, float cy, float radius,
                                          int sides, float rotation, int color) {
        float step = TAU / sides;
        for (int i = 0; i < sides; i++) {
            float a0 = rotation + i * step;
            float a1 = rotation + (i + 1) * step;
            cpuLine(g, cx + cos(a0) * radius, cy + sin(a0) * radius,
                    cx + cos(a1) * radius, cy + sin(a1) * radius, color);
        }
    }

    /** 凸多边形扫描线填充：每行与 n 条边求交，取最小/最大 x。 */
    private static void cpuPolygonFill(GuiGraphics g, float cx, float cy, float radius,
                                       int sides, float rotation, int color) {
        float step = TAU / sides;
        int r = Math.round(radius);
        for (int dy = -r; dy <= r; dy++) {
            float y = dy + 0.5F;
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            for (int i = 0; i < sides; i++) {
                float a0 = rotation + i * step;
                float a1 = rotation + (i + 1) * step;
                float x0 = cos(a0) * radius;
                float y0 = sin(a0) * radius;
                float x1 = cos(a1) * radius;
                float y1 = sin(a1) * radius;
                if ((y0 <= y && y1 > y) || (y1 <= y && y0 > y)) {
                    float t = (y - y0) / (y1 - y0);
                    float xi = x0 + (x1 - x0) * t;
                    minX = Math.min(minX, xi);
                    maxX = Math.max(maxX, xi);
                }
            }
            if (minX < maxX) {
                g.fill(Math.round(cx + minX), Math.round(cy) + dy,
                        Math.round(cx + maxX), Math.round(cy) + dy + 1, color);
            }
        }
    }
}
