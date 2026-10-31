package org.bytechen.rottenruins.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.network.c2s.MagicPoolActionPacket;
import org.bytechen.rottenruins.overworld.registry.capability.PlayerMagicPool;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.MagicHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * <b>魔法星图</b> —— 悬浮在深空里的法术槽界面。
 *
 * <h3>布局</h3>
 * <ul>
 *   <li><b>中间</b>：{@link #SLOT_TEX} 金框槽位按一条星座折线排布，槽位之间是
 *       发丝级金色连线；默认 5 格（{@link MagicStats#BASE_SLOT_COUNT}），
 *       槽位数被属性抬高后按螺旋向外补点，不需要改界面。</li>
 *   <li><b>右侧</b>：一个半透明侧栏，只列出玩家背包里的<b>法术</b>
 *       （{@link MagicStats#isSpell}），不是整个背包。</li>
 * </ul>
 *
 * <h3>交互</h3>
 * <ul>
 *   <li>从右侧把法术<b>拖到</b>空槽 → 发 {@link MagicPoolActionPacket#sendPlace}；
 *       拖动时会预判目标：可放的空槽亮金边、悬停的非法槽铺红，ghost 靠近可放槽时吸附到格心；</li>
 *   <li><b>双击</b>右侧法术 → 直接放进第一个能放的空槽；<b>右键</b>槽里的法术 → 退回背包；</li>
 *   <li>从槽里<b>拖出去</b>（拖到右侧或任意空白处）→ 还回背包；</li>
 *   <li>在槽之间拖动 → 交换位置；</li>
 *   <li><b>放入 / 移动成功</b>时，会沿连接线向<b>所有相连的相邻槽位</b>各滚出一颗
 *       <b>一次性</b>光点（中间槽位左右各一颗，两端的槽位只有一颗；
 *       {@value #PLACEMENT_PULSE_MS} 毫秒后消失，不循环）；</li>
 *   <li><b>单击槽位</b>（按下后没拖走就松开）→ 选中该槽，之后法杖右键放的就是它；</li>
 *   <li>按 {@code M} / {@code ESC} 关闭；打开时有短暂的入场亮起动画与音效。</li>
 * </ul>
 *
 * <h3>服务端权威</h3>
 * <p>这里只画客户端那份镜像（{@code PlayerMagicPool} 由
 * {@code MagicPoolSyncPacket} 同步）。所有改动都是"发申请 → 服务端校验并改真身 →
 * 服务端回推全量快照"，界面从不自己改槽位内容。</p>
 *
 * <h3>为什么全部自绘</h3>
 * <p>槽位是星图节点，不是原版 18×18 容器网格；用原版 {@code Slot} 反而要迁就坐标。
 * 物品图标用 {@link GuiGraphics#renderItem} 自己画，拖动也是自绘的"手里那件"。</p>
 *
 * <h3>表现层</h3>
 * <p>背景、槽位连接光带与「放入时的一次性光点」由 {@link MagicGuiShaders} 的三支
 * GUI 着色器绘制（流动星云 + 鼠标视差 / 发丝级金线 / 沿线滚一次的光点）。
 * 选中槽的连线更亮、有法术的槽连线略亮、悬停槽的连线高亮；打开界面时连线从中心向外
 * 逐段亮起、槽位依次 pop 出现。着色器加载失败时退回静态星点、金色虚线与没有光点，
 * 功能不受影响。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MagicScreen extends Screen {

    // ══════════════════════════════════════════════════════════════
    // 资源 / 常量
    // ══════════════════════════════════════════════════════════════

    /** 金框槽位底图（26×26，四边是发光的旧金色）。 */
    private static final ResourceLocation GOLD_SLOT =
            ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "textures/gui/gold_slot.png");

    /** 槽位贴图边长。 */
    private static final int SLOT_TEX = 26;

    /** 右侧法术列表栏宽度。 */
    private static final int PANEL_W = 138;

    /** 屏幕四周留白。 */
    private static final int PANEL_MARGIN = 16;

    /** 右侧列表格子边长（图标 16 + 4 边距）。 */
    private static final int LIST_CELL = 20;

    /** 星图区域左边界。 */
    private static final int MAP_LEFT = 16;

    /** 放入法术时，光点从起点滚到相邻槽位所需时间（毫秒）。 */
    private static final long PLACEMENT_PULSE_MS = 650L;

    /** 打开界面时，连线与槽位的入场动画时长（毫秒）。 */
    private static final long OPEN_ANIMATION_MS = 500L;

    // ── 配色 ──
    private static final int COL_GOLD = 0xFFE7C46A;
    private static final int COL_GOLD_DIM = 0x99C9A227;
    private static final int COL_TEXT_DIM = 0x8899AABB;

    /**
     * 默认 5 格的星座折线偏移（相对星图中心，未乘布局缩放）。
     * <p>形状是一个"∧∨∧"的折线，槽位落点错开高度，比五点排成一条直线更像星图。</p>
     */
    private static final int[][] FIVE_SLOT_OFFSETS = {
            {-96, 10}, {-48, -40}, {0, 10}, {48, -40}, {96, 10}
    };

    /** 槽位数少于 5 时的紧凑排布（下标即槽数）。 */
    private static final int[][][] SMALL_SLOT_OFFSETS = {
            {},
            {{0, 0}},
            {{-60, 0}, {60, 0}},
            {{-96, 10}, {0, -32}, {96, 10}},
            {{-96, 10}, {-32, -32}, {32, -32}, {96, 10}}
    };

    // ══════════════════════════════════════════════════════════════
    // 运行时状态
    // ══════════════════════════════════════════════════════════════

    /** 背景静态星点（init 时按屏幕尺寸生成一次）。 */
    private final List<Star> stars = new ArrayList<>();

    // 布局缓存
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int mapCenterX;
    private int mapCenterY;
    private float constellationScale = 1.0F;
    private int[] slotX = new int[0];
    private int[] slotY = new int[0];
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int cachedSlotCount = -1;

    // 拖拽状态
    private boolean dragging;
    private boolean dragMoved;
    private double dragStartX;
    private double dragStartY;
    private int dragInvSlot = -1;
    private int dragPoolSlot = -1;
    private ItemStack dragStack = ItemStack.EMPTY;
    private int pressedPoolSlot = -1;

    // 放入法术的一次性光点：每次放入最多两颗（左右各一），播完即止
    private final List<PlacementPulse> placementPulses = new ArrayList<>(2);

    // 入场动画 / 音效 / 视差
    private long openAnimationStartMs;
    private boolean openSoundPlayed;
    private int frameMouseX;
    private int frameMouseY;

    // 双击右侧法术 → 快速放入第一个空槽
    private long lastInvClickMs;
    private int lastInvClickSlot = -1;

    // 上一帧悬停的背包法术（供 tooltip 用）
    private int hoverInvSlot = -1;
    private ItemStack hoverInvStack = ItemStack.EMPTY;

    /** 背景星点。 */
    private record Star(int x, int y, int size, float phase) {
    }

    /** 右侧列表里的一条：背包下标 + 那一堆法术。 */
    private record InvSpell(int slot, ItemStack stack) {
    }

    /** 一颗在播的一次性光点：从 {@code fromSlot} 滚向 {@code toSlot}。 */
    private record PlacementPulse(int fromSlot, int toSlot, long startMs) {
    }

    public MagicScreen() {
        super(Component.translatable("rotten_ruins.gui.magic.title"));
    }

    // ══════════════════════════════════════════════════════════════
    // 生命周期 / 布局
    // ══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        buildStars();
        // 强制下一帧重算布局（窗口尺寸可能变了）
        cachedWidth = -1;

        // 入场动画只在第一次打开时播；窗口 resize 也会走 init()，不重播
        if (openAnimationStartMs == 0L) {
            openAnimationStartMs = Util.getMillis();
        }
        if (!openSoundPlayed) {
            openSoundPlayed = true;
            playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.35F);
        }
    }

    @Override
    public void onClose() {
        playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.9F, 0.35F);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        // 界面打开时游戏继续跑：这是玩法界面，不是菜单。
        return false;
    }

    /** 生成固定种子的静态星空；每帧只按时间调亮度，不重排。 */
    private void buildStars() {
        stars.clear();
        Random random = new Random(0x5EED_2026L);
        int count = Math.max(48, width * height / 9000);
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(Math.max(1, width));
            int y = random.nextInt(Math.max(1, height));
            int size = random.nextInt(7) == 0 ? 2 : 1;
            float phase = random.nextFloat() * Mth.TWO_PI;
            stars.add(new Star(x, y, size, phase));
        }
    }

    /**
     * 计算槽位落点。槽数变化 / 窗口变化时才重算，其它帧直接用缓存。
     */
    private void ensureLayout() {
        int count = Math.max(1, slotCount());
        if (width == cachedWidth && height == cachedHeight && count == cachedSlotCount) {
            return;
        }
        cachedWidth = width;
        cachedHeight = height;
        cachedSlotCount = count;

        panelW = Math.min(PANEL_W, Math.max(120, width / 3));
        panelX = width - panelW - PANEL_MARGIN;
        panelY = PANEL_MARGIN;
        panelH = Math.max(120, height - PANEL_MARGIN * 2);

        int mapRight = panelX - 12;
        int availW = Math.max(120, mapRight - MAP_LEFT);
        int availH = Math.max(100, height - 96);

        int[][] offsets = offsetsFor(count);
        int maxAbsX = 1;
        int maxAbsY = 1;
        for (int[] offset : offsets) {
            maxAbsX = Math.max(maxAbsX, Math.abs(offset[0]));
            maxAbsY = Math.max(maxAbsY, Math.abs(offset[1]));
        }

        float scaleX = (availW - SLOT_TEX) / (2.0F * maxAbsX);
        float scaleY = (availH - SLOT_TEX) / (2.0F * maxAbsY);
        constellationScale = Mth.clamp(Math.min(1.0F, Math.min(scaleX, scaleY)), 0.4F, 1.0F);

        mapCenterX = MAP_LEFT + availW / 2;
        mapCenterY = height / 2 + 2;

        slotX = new int[count];
        slotY = new int[count];
        for (int i = 0; i < count; i++) {
            slotX[i] = mapCenterX + Math.round(offsets[i][0] * constellationScale);
            slotY[i] = mapCenterY + Math.round(offsets[i][1] * constellationScale);
        }
    }

    /** 生成槽位相对偏移：默认 5 格走手调星座，其它数量走紧凑排布 / 螺旋补点。 */
    private static int[][] offsetsFor(int count) {
        int[][] out = new int[count][2];
        if (count <= 0) {
            return out;
        }
        if (count < FIVE_SLOT_OFFSETS.length) {
            int[][] source = SMALL_SLOT_OFFSETS[count];
            for (int i = 0; i < count; i++) {
                out[i][0] = source[i][0];
                out[i][1] = source[i][1];
            }
            return out;
        }
        for (int i = 0; i < FIVE_SLOT_OFFSETS.length; i++) {
            out[i][0] = FIVE_SLOT_OFFSETS[i][0];
            out[i][1] = FIVE_SLOT_OFFSETS[i][1];
        }
        // 超过 5 格：从上方开始向外螺旋补点，不覆盖原有星座。
        for (int i = FIVE_SLOT_OFFSETS.length; i < count; i++) {
            int k = i - FIVE_SLOT_OFFSETS.length;
            double angle = Math.toRadians(-90 + k * 45);
            double radius = 118 + (k / 8) * 28;
            out[i][0] = (int) Math.round(Math.cos(angle) * radius);
            out[i][1] = (int) Math.round(Math.sin(angle) * radius * 0.62);
        }
        return out;
    }

    // ══════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        frameMouseX = mouseX;
        frameMouseY = mouseY;

        // 背景按轮盘/装载界面同一套顺序画在 render() 里：不再 override renderBackground，
        // 也不再让这个 Screen 走和别的界面不同的入口路径。
        guiGraphics.fill(0, 0, width, height, 0xB602040A);
        float mouseNormX = width <= 0 ? 0.0F : mouseX / (float) width * 2.0F - 1.0F;
        float mouseNormY = height <= 0 ? 0.0F : mouseY / (float) height * 2.0F - 1.0F;
        if (MagicGuiShaders.drawStarfield(guiGraphics, width, height, timeSeconds(),
                mouseNormX, mouseNormY)) {
            guiGraphics.fill(0, 0, width, height, 0x6E000000);
        } else {
            // 着色器不可用时的静态兜底：分层渐变 + 固定星点
            guiGraphics.fillGradient(0, 0, width, height, 0xFF07101D, 0xFF02040A);
            guiGraphics.fillGradient(0, 0, width, height / 2, 0x33213A5E, 0x00000000);
            guiGraphics.fillGradient(0, height / 2, width, height, 0x00000000, 0x55000000);
            drawStars(guiGraphics);
        }

        ensureLayout();

        long now = Util.getMillis();
        float openT = openProgress(now);
        int hoveredPool = hoveredPoolSlot(mouseX, mouseY);

        // 连接光带：入场时从中心向外逐段亮起；选中 / 悬停 / 有法术的段更亮
        float[] segmentIntensity = constellationIntensities(hoveredPool, openT);
        if (!MagicGuiShaders.drawConstellation(guiGraphics, slotX, slotY,
                timeSeconds(), 7.0F, segmentIntensity)) {
            drawDottedConstellation(guiGraphics, now);
        }
        // 放入法术时的一次性光点：画在连线之上、槽位之下，看起来是"滚进"旁边的槽
        drawPlacementPulse(guiGraphics);

        drawSlots(guiGraphics, hoveredPool, now, openT);
        drawHeader(guiGraphics);
        drawInventoryPanel(guiGraphics, mouseX, mouseY);
        drawHint(guiGraphics);
        drawDragStack(guiGraphics, mouseX, mouseY);

        if (!dragging) {
            drawTooltips(guiGraphics, mouseX, mouseY, hoveredPool);
        }
    }

    /** 入场动画进度 0..1。 */
    private float openProgress(long now) {
        if (openAnimationStartMs <= 0L) {
            return 1.0F;
        }
        return Mth.clamp((now - openAnimationStartMs) / (float) OPEN_ANIMATION_MS, 0.0F, 1.0F);
    }

    /**
     * 每段连线的亮度倍率。
     * <ul>
     *   <li>入场动画：按"到星图中心的距离"延迟亮起，看起来是从中心画出来的；</li>
     *   <li>选中槽的两条连线 ×1.9，有法术的槽 ×1.25；</li>
     *   <li>鼠标悬停槽的连线 ×1.35。</li>
     * </ul>
     */
    private float[] constellationIntensities(int hoveredPool, float openT) {
        int segments = Math.max(0, slotX.length - 1);
        float[] out = new float[segments];
        if (segments == 0) {
            return out;
        }
        int selected = selectedSlot();
        float maxDist = 1.0F;
        for (int i = 0; i < slotX.length; i++) {
            maxDist = Math.max(maxDist, Math.abs(slotX[i] - mapCenterX));
        }
        for (int i = 0; i < segments; i++) {
            float midX = (slotX[i] + slotX[i + 1]) * 0.5F;
            float distNorm = Math.abs(midX - mapCenterX) / maxDist;
            float appear = Mth.clamp((openT - distNorm * 0.22F) / 0.55F, 0.0F, 1.0F);
            float factor = 1.0F;
            if (selected == i || selected == i + 1) {
                factor *= 1.9F;
            } else if (!slotStack(i).isEmpty() || !slotStack(i + 1).isEmpty()) {
                factor *= 1.25F;
            }
            if (hoveredPool == i || hoveredPool == i + 1) {
                factor *= 1.35F;
            }
            out[i] = factor * appear;
        }
        return out;
    }

    /** 秒级时间；取模避免 float 精度随开机时长变差。 */
    private static float timeSeconds() {
        return (Util.getMillis() % 100_000L) * 0.001F;
    }

    private void drawStars(GuiGraphics guiGraphics) {
        long time = Util.getMillis();
        for (Star star : stars) {
            float twinkle = 0.5F + 0.5F * Mth.sin(time * 0.0015F + star.phase());
            int alpha = (int) (0x30 + 0x70 * twinkle);
            int color = (alpha << 24) | 0xBBD6FF;
            guiGraphics.fill(star.x(), star.y(), star.x() + star.size(), star.y() + star.size(), color);
        }
    }

    /** 槽位之间的金色星点连线（着色器不可用时的兜底）。 */
    private void drawDottedConstellation(GuiGraphics guiGraphics, long time) {
        for (int i = 0; i + 1 < slotX.length; i++) {
            drawDottedLine(guiGraphics, slotX[i], slotY[i], slotX[i + 1], slotY[i + 1], time);
        }
    }

    private void drawDottedLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, long time) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0D) {
            return;
        }
        int steps = Math.max(1, (int) (length / 5.0D));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(x1 + dx * t);
            int y = (int) Math.round(y1 + dy * t);
            float phase = (float) ((time * 0.0015F + i * 0.55F) % Mth.TWO_PI);
            int alpha = 0x28 + (int) (0x40 * (0.5F + 0.5F * Mth.sin(phase)));
            guiGraphics.fill(x, y, x + 1, y + 1, (alpha << 24) | 0xC9A227);
        }
    }

    private void drawHeader(GuiGraphics guiGraphics) {
        // 和其它可用的界面保持一致：标题走普通字体绘制，不接文本特效系统。
        guiGraphics.drawCenteredString(font, title, mapCenterX, 10, COL_GOLD);
        guiGraphics.drawCenteredString(font,
                Component.translatable("rotten_ruins.gui.magic.slots", countFilledSlots(), slotX.length),
                mapCenterX, 24, COL_GOLD_DIM);
    }

    private void drawHint(GuiGraphics guiGraphics) {
        guiGraphics.drawCenteredString(font,
                Component.translatable("rotten_ruins.gui.magic.hint"),
                mapCenterX, height - 16, COL_GOLD_DIM);
    }

    private void drawSlots(GuiGraphics guiGraphics, int hoveredPool, long time, float openT) {
        int selected = selectedSlot();
        int count = slotX.length;
        float maxDist = 1.0F;
        for (int i = 0; i < count; i++) {
            maxDist = Math.max(maxDist, Math.abs(slotX[i] - mapCenterX));
        }

        for (int i = 0; i < count; i++) {
            // 入场：离星图中心越远亮起越晚，同时从 0.72 倍 pop 到 1.0 倍
            float distNorm = Math.abs(slotX[i] - mapCenterX) / maxDist;
            float appear = Mth.clamp((openT - distNorm * 0.22F) / 0.55F, 0.0F, 1.0F);
            if (appear <= 0.001F) {
                continue;
            }
            float inv = 1.0F - appear;
            float scale = 0.72F + 0.28F * (1.0F - inv * inv * inv);

            PoseStack pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(slotX[i], slotY[i], 0.0F);
            pose.scale(scale, scale, 1.0F);

            int x = -SLOT_TEX / 2;
            int y = -SLOT_TEX / 2;
            boolean hovered = i == hoveredPool;
            boolean isSelected = i == selected;

            // 选中槽：呼吸的金色外圈
            if (isSelected) {
                float pulse = 0.5F + 0.5F * Mth.sin(time * 0.004F);
                int alpha = (int) (0x55 + 0x55 * pulse);
                guiGraphics.fill(x - 4, y - 4, x + SLOT_TEX + 4, y + SLOT_TEX + 4,
                        (alpha << 24) | 0xDDAA44);
            }

            // 拖拽预判：可放的空槽亮金边，悬停的非法目标铺红
            if (dragging) {
                boolean valid = canDropInto(i);
                if (valid) {
                    guiGraphics.fill(x - 5, y - 5, x + SLOT_TEX + 5, y + SLOT_TEX + 5, 0x16FFD27A);
                    guiGraphics.renderOutline(x - 3, y - 3, SLOT_TEX + 6, SLOT_TEX + 6,
                            hovered ? 0xCCFFE08A : 0x66FFD27A);
                } else if (hovered) {
                    guiGraphics.fill(x - 3, y - 3, x + SLOT_TEX + 3, y + SLOT_TEX + 3, 0x22FF5555);
                    guiGraphics.renderOutline(x - 2, y - 2, SLOT_TEX + 4, SLOT_TEX + 4, 0xAAFF5555);
                }
            }

            // 悬浮槽：先铺一层柔光，再压金框
            if (hovered) {
                guiGraphics.fill(x - 6, y - 6, x + SLOT_TEX + 6, y + SLOT_TEX + 6, 0x12FFD27A);
                guiGraphics.fill(x - 3, y - 3, x + SLOT_TEX + 3, y + SLOT_TEX + 3, 0x1CFFD27A);
            }

            guiGraphics.blit(GOLD_SLOT, x, y, 0, 0, SLOT_TEX, SLOT_TEX, SLOT_TEX, SLOT_TEX);

            ItemStack stack = slotStack(i);
            if (!stack.isEmpty() && !(dragging && dragPoolSlot == i)) {
                guiGraphics.renderItem(stack, x + (SLOT_TEX - 16) / 2, y + (SLOT_TEX - 16) / 2);
            }

            if (hovered) {
                guiGraphics.renderOutline(x - 1, y - 1, SLOT_TEX + 2, SLOT_TEX + 2, 0xFFFFE08A);
            }
            // 序号往框内收一点，不再贴着金边
            guiGraphics.drawString(font, String.valueOf(i + 1), x + 4, y + 4,
                    isSelected ? COL_GOLD : COL_GOLD_DIM, false);

            pose.popPose();
        }
    }

    private void drawInventoryPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        hoverInvSlot = -1;
        hoverInvStack = ItemStack.EMPTY;

        guiGraphics.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH,
                0xB20A0F1A, 0xC8060912);
        guiGraphics.renderOutline(panelX, panelY, panelW, panelH, 0x66C9A227);
        guiGraphics.renderOutline(panelX + 3, panelY + 3, panelW - 6, panelH - 6, 0x22C9A227);
        guiGraphics.drawString(font,
                Component.translatable("rotten_ruins.gui.magic.inventory"),
                panelX + 8, panelY + 7, COL_GOLD, false);
        guiGraphics.fill(panelX + 8, panelY + 19, panelX + panelW - 8, panelY + 20, 0x66C9A227);

        List<InvSpell> spells = inventorySpells();
        if (spells.isEmpty()) {
            // 按面板宽度折行：不同语言的句子长度差很多，不能假定一行放得下
            List<FormattedCharSequence> lines = font.split(
                    Component.translatable("rotten_ruins.gui.magic.empty"), panelW - 20);
            int lineY = panelY + 36;
            for (FormattedCharSequence line : lines) {
                guiGraphics.drawCenteredString(font, line, panelX + panelW / 2, lineY, COL_TEXT_DIM);
                lineY += 10;
            }
            return;
        }

        int limit = listColumns() * listRows();
        for (int index = 0; index < Math.min(spells.size(), limit); index++) {
            InvSpell entry = spells.get(index);
            int x = listX(index);
            int y = listY(index);
            boolean hovered = mouseX >= x && mouseX < x + LIST_CELL
                    && mouseY >= y && mouseY < y + LIST_CELL;

            if (hovered) {
                guiGraphics.fill(x, y, x + LIST_CELL, y + LIST_CELL, 0x33E7C46A);
                guiGraphics.renderOutline(x, y, LIST_CELL, LIST_CELL, 0x99E7C46A);
                hoverInvSlot = entry.slot();
                hoverInvStack = entry.stack();
            }
            if (!(dragging && dragInvSlot == entry.slot())) {
                guiGraphics.renderItem(entry.stack(), x + 2, y + 2);
            }
        }

        if (spells.size() > limit) {
            guiGraphics.drawString(font, "+" + (spells.size() - limit),
                    panelX + panelW - 26, panelY + 7, COL_TEXT_DIM, false);
        }
    }

    private void drawDragStack(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!dragging || dragStack.isEmpty() || (dragInvSlot < 0 && dragPoolSlot < 0)) {
            return;
        }
        int drawX = mouseX - 8;
        int drawY = mouseY - 8;
        // 靠近可放的空槽时吸附到格心，抬手前就能看出会落在哪里
        int snapSlot = hoveredPoolSlot(mouseX, mouseY);
        if (snapSlot >= 0 && canDropInto(snapSlot)) {
            drawX = slotX[snapSlot] - 8;
            drawY = slotY[snapSlot] - 8;
        }
        guiGraphics.renderItem(dragStack, drawX, drawY);
        guiGraphics.renderOutline(drawX - 1, drawY - 1, 18, 18, 0x99E7C46A);
    }

    private void drawTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, int hoveredPool) {
        ItemStack stack = ItemStack.EMPTY;
        if (hoveredPool >= 0) {
            stack = slotStack(hoveredPool);
        }
        if (stack.isEmpty() && hoverInvSlot >= 0) {
            stack = hoverInvStack;
        }
        if (stack.isEmpty()) {
            return;
        }
        // 不用 vanilla renderTooltip：别的正常界面都不走这条路径，这里改成和它们一致的
        // 简单面板（fill + drawString），避免 tooltip 自绘 shader 把状态留到关闭之后。
        List<FormattedCharSequence> rows = new ArrayList<>();
        for (Component line : spellTooltip(stack)) {
            rows.addAll(font.split(line, 220));
        }
        int lineH = font.lineHeight + 2;
        int maxW = 0;
        for (FormattedCharSequence row : rows) {
            maxW = Math.max(maxW, font.width(row));
        }
        int pad = 5;
        int w = maxW + pad * 2;
        int h = rows.size() * lineH + pad * 2 - 2;
        int x = Math.min(mouseX + 10, width - w - 4);
        int y = Math.min(mouseY + 10, height - h - 4);
        x = Math.max(4, x);
        y = Math.max(4, y);
        guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xCC000000);
        guiGraphics.fill(x, y, x + w, y + h, 0xE60A0F1A);
        guiGraphics.renderOutline(x, y, w, h, 0x66C9A227);
        int textY = y + pad;
        for (int i = 0; i < rows.size(); i++) {
            guiGraphics.drawString(font, rows.get(i), x + pad, textY, i == 0 ? COL_GOLD : 0xFFB8B8B8, false);
            textY += lineH;
        }
    }

    /**
     * 法术的 tooltip：名字 + 主动/被动 + 主动法术的耗蓝 / 冷却 / 蓄力。
     * <p>数值走 {@link MagicStats}（属性与附魔折叠后的最终值），不是物品基值。</p>
     */
    private List<Component> spellTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());

        boolean passive = MagicStats.isPassive(stack);
        lines.add(Component.translatable(passive
                        ? "rotten_ruins.gui.magic.kind.passive"
                        : "rotten_ruins.gui.magic.kind.active")
                .withStyle(ChatFormatting.GOLD));

        Player player = minecraft == null ? null : minecraft.player;
        if (passive || player == null) {
            return lines;
        }

        int mana = MagicStats.manaCost(player, stack);
        if (mana > 0) {
            lines.add(Component.translatable("rotten_ruins.gui.magic.mana", mana)
                    .withStyle(ChatFormatting.GRAY));
        }
        int cooldown = MagicStats.cooldownTicks(player, stack);
        if (cooldown > 0) {
            lines.add(Component.translatable("rotten_ruins.gui.magic.cooldown", seconds(cooldown))
                    .withStyle(ChatFormatting.GRAY));
        }
        int charge = MagicStats.chargeTicks(player, stack);
        if (charge > 0) {
            lines.add(Component.translatable("rotten_ruins.gui.magic.charge", seconds(charge))
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0F);
    }

    // ══════════════════════════════════════════════════════════════
    // 输入
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ensureLayout();

        // 右键：把槽里的法术退回背包（快速卸下）；拖拽中忽略
        if (button == 1) {
            if (dragging) {
                return true;
            }
            int poolSlot = hoveredPoolSlot(mouseX, mouseY);
            if (poolSlot >= 0 && !slotStack(poolSlot).isEmpty()) {
                MagicPoolActionPacket.sendTake(poolSlot);
                playUiSound(SoundEvents.ITEM_PICKUP, 0.9F, 0.35F);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        resetDrag();
        int poolSlot = hoveredPoolSlot(mouseX, mouseY);
        if (poolSlot >= 0) {
            lastInvClickSlot = -1;
            pressedPoolSlot = poolSlot;
            dragStartX = mouseX;
            dragStartY = mouseY;
            ItemStack stack = slotStack(poolSlot);
            if (!stack.isEmpty()) {
                dragPoolSlot = poolSlot;
                dragStack = stack.copy();
                playUiSound(SoundEvents.ITEM_PICKUP, 1.6F, 0.3F);
            }
            return true;
        }

        int invSlot = hoveredInventorySlot(mouseX, mouseY);
        if (invSlot >= 0) {
            long now = Util.getMillis();
            boolean doubleClick = invSlot == lastInvClickSlot && now - lastInvClickMs <= 350L;
            lastInvClickSlot = doubleClick ? -1 : invSlot;
            lastInvClickMs = now;
            if (doubleClick) {
                // 双击右侧法术 = 直接放进第一个空槽
                quickPlace(invSlot);
                return true;
            }
            dragInvSlot = invSlot;
            dragStack = inventoryStack(invSlot);
            dragStartX = mouseX;
            dragStartY = mouseY;
            playUiSound(SoundEvents.ITEM_PICKUP, 1.6F, 0.3F);
            return true;
        }

        lastInvClickSlot = -1;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragStack.isEmpty()) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (!dragMoved && (Math.abs(mouseX - dragStartX) > 3.0D
                || Math.abs(mouseY - dragStartY) > 3.0D)) {
            dragMoved = true;
            dragging = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        ensureLayout();
        boolean handled = pressedPoolSlot >= 0 || !dragStack.isEmpty();

        if (dragging) {
            int target = hoveredPoolSlot(mouseX, mouseY);
            boolean droppedBack = target >= 0 && dragInvSlot < 0 && dragPoolSlot == target;
            if (droppedBack) {
                // 拖起来又放回原槽：什么都不做
            } else if (target >= 0 && canDropInto(target)) {
                if (dragInvSlot >= 0) {
                    MagicPoolActionPacket.sendPlace(dragInvSlot, target);
                } else if (dragPoolSlot >= 0) {
                    MagicPoolActionPacket.sendMove(dragPoolSlot, target);
                }
                startPlacementPulse(target);
                playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.9F, 0.45F);
            } else if (target >= 0) {
                // 占用 / 重复 / 不可放：不发包，只给一次失败音
                playUiSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.5F);
            } else if (dragPoolSlot >= 0) {
                MagicPoolActionPacket.sendTake(dragPoolSlot);
                playUiSound(SoundEvents.ITEM_PICKUP, 0.9F, 0.35F);
            }
        } else if (pressedPoolSlot >= 0 && hoveredPoolSlot(mouseX, mouseY) == pressedPoolSlot) {
            // 按下没拖动就松开 = 选中这一格
            MagicPoolActionPacket.sendSelect(pressedPoolSlot);
            playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.35F, 0.35F);
        }

        resetDrag();
        return handled;
    }

    /**
     * 当前拖拽的法术能否放进这一格。
     *
     * <p>只做<b>客户端预判</b>（高亮 / 吸附 / 过滤发包用）：槽位空 + 没有重复法术；
     * 真正能不能放仍由服务端重新校验、以服务端为准。</p>
     */
    private boolean canDropInto(int slot) {
        if (!dragging || dragStack.isEmpty()) {
            return false;
        }
        PlayerMagicPool pool = pool();
        if (pool == null || !pool.isValidSlot(slot) || !slotStack(slot).isEmpty()) {
            return false;
        }
        if (dragInvSlot >= 0) {
            return pool.isItemValid(slot, dragStack);
        }
        return dragPoolSlot >= 0 && dragPoolSlot != slot;
    }

    /**
     * 双击右侧法术：放进第一个能放的空槽。
     *
     * <p>槽位规则（重复法术 / 槽已满）先由 {@link PlayerMagicPool#isItemValid}
     * 在客户端预判一遍；服务端仍会再校验一次。</p>
     */
    private void quickPlace(int inventorySlot) {
        ItemStack stack = inventoryStack(inventorySlot);
        PlayerMagicPool pool = pool();
        if (stack.isEmpty() || pool == null) {
            return;
        }
        for (int i = 0; i < slotX.length; i++) {
            if (pool.isValidSlot(i) && slotStack(i).isEmpty() && pool.isItemValid(i, stack)) {
                MagicPoolActionPacket.sendPlace(inventorySlot, i);
                startPlacementPulse(i);
                playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.9F, 0.45F);
                return;
            }
        }
        playUiSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.5F);
    }

    /** 客户端 UI 音效；不依赖世界里的声源位置。 */
    private void playUiSound(SoundEvent sound, float pitch, float volume) {
        if (minecraft == null) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void resetDrag() {
        dragging = false;
        dragMoved = false;
        dragInvSlot = -1;
        dragPoolSlot = -1;
        dragStack = ItemStack.EMPTY;
        pressedPoolSlot = -1;
    }

    // ══════════════════════════════════════════════════════════════
    // 放入法术的一次性光点
    // ══════════════════════════════════════════════════════════════

    /**
     * 放入 / 槽间移动成功后：从目标槽沿连线向<b>所有相连的相邻槽位</b>各发一颗
     * <b>一次性</b>光点。
     *
     * <p>中间槽位左右都有连线 → 左右各一颗（同时出发、各自只走一次）；
     * 两端槽位只有一条连线 → 只有一颗；槽位数只有 1 时没有连线可走，直接不发。
     * 这些光点只是"放进去了"的空间反馈，不表示任何数值关系。</p>
     */
    private void startPlacementPulse(int slot) {
        int count = slotX.length;
        if (count < 2 || slot < 0 || slot >= count) {
            return;
        }
        long now = Util.getMillis();
        placementPulses.clear();
        if (slot - 1 >= 0) {
            placementPulses.add(new PlacementPulse(slot, slot - 1, now));
        }
        if (slot + 1 < count) {
            placementPulses.add(new PlacementPulse(slot, slot + 1, now));
        }
    }

    /**
     * 画所有在播的光点；进度走完的自动移除。
     *
     * <p>时间用 {@link Util#getMillis()}（墙钟），与界面其它动画一致；
     * 位置用 smoothstep 缓动，起步/收尾都慢一点，不会像被弹出去。</p>
     */
    private void drawPlacementPulse(GuiGraphics guiGraphics) {
        if (placementPulses.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        placementPulses.removeIf(pulse -> now - pulse.startMs() >= PLACEMENT_PULSE_MS
                || pulse.fromSlot() < 0 || pulse.toSlot() < 0
                || pulse.fromSlot() >= slotX.length || pulse.toSlot() >= slotX.length);
        for (PlacementPulse pulse : placementPulses) {
            float progress = (now - pulse.startMs()) / (float) PLACEMENT_PULSE_MS;
            if (progress < 0.0F) {
                progress = 0.0F;
            }
            float eased = progress * progress * (3.0F - 2.0F * progress);
            if (!MagicGuiShaders.drawPlacementPulse(guiGraphics,
                    slotX[pulse.fromSlot()], slotY[pulse.fromSlot()],
                    slotX[pulse.toSlot()], slotY[pulse.toSlot()], eased, 9.0F)) {
                // 着色器不可用 / 线段长度为 0：这一帧之后不再画
                placementPulses.clear();
                return;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 查询 / 小工具
    // ══════════════════════════════════════════════════════════════

    private PlayerMagicPool pool() {
        Player player = minecraft == null ? null : minecraft.player;
        return player == null ? null : MagicHandle.getPool(player);
    }

    private int slotCount() {
        Player player = minecraft == null ? null : minecraft.player;
        return player == null ? MagicStats.BASE_SLOT_COUNT : MagicStats.slotCount(player);
    }

    private int selectedSlot() {
        PlayerMagicPool pool = pool();
        return pool == null ? 0 : pool.getSelectedSlot();
    }

    private ItemStack slotStack(int index) {
        PlayerMagicPool pool = pool();
        return pool != null && pool.isValidSlot(index) ? pool.getStackInSlot(index) : ItemStack.EMPTY;
    }

    private int countFilledSlots() {
        int filled = 0;
        for (int i = 0; i < slotX.length; i++) {
            if (!slotStack(i).isEmpty()) {
                filled++;
            }
        }
        return filled;
    }

    private List<InvSpell> inventorySpells() {
        List<InvSpell> out = new ArrayList<>();
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return out;
        }
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (MagicStats.isSpell(stack)) {
                out.add(new InvSpell(i, stack));
            }
        }
        return out;
    }

    private ItemStack inventoryStack(int slot) {
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null || slot < 0 || slot >= player.getInventory().items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getInventory().items.get(slot);
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private int hoveredPoolSlot(double mouseX, double mouseY) {
        int half = SLOT_TEX / 2 + 1;
        for (int i = 0; i < slotX.length; i++) {
            if (Math.abs(mouseX - slotX[i]) <= half && Math.abs(mouseY - slotY[i]) <= half) {
                return i;
            }
        }
        return -1;
    }

    private int hoveredInventorySlot(double mouseX, double mouseY) {
        List<InvSpell> spells = inventorySpells();
        if (spells.isEmpty()) {
            return -1;
        }
        int limit = listColumns() * listRows();
        for (int index = 0; index < Math.min(spells.size(), limit); index++) {
            int x = listX(index);
            int y = listY(index);
            if (mouseX >= x && mouseX < x + LIST_CELL && mouseY >= y && mouseY < y + LIST_CELL) {
                return spells.get(index).slot();
            }
        }
        return -1;
    }

    private int listColumns() {
        return Math.max(1, (panelW - 16) / LIST_CELL);
    }

    private int listRows() {
        return Math.max(1, (panelH - 30) / LIST_CELL);
    }

    private int listX(int index) {
        return panelX + 8 + (index % listColumns()) * LIST_CELL;
    }

    private int listY(int index) {
        return panelY + 26 + (index / listColumns()) * LIST_CELL;
    }
}
