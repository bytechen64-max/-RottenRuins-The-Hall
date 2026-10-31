package org.bytechen.rottenruins.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.api.magic.MagicStats;
import org.bytechen.rottenruins.client.MagicClientHandler;
import org.bytechen.rottenruins.client.gui.wheel.WheelClientState;
import org.bytechen.rottenruins.client.gui.wheel.WheelData;
import org.bytechen.rottenruins.client.gui.wheel.WheelRenderer;
import org.bytechen.rottenruins.network.c2s.WheelActionPacket;
import org.bytechen.rottenruins.overworld.registry.items.magic.bases.UsingType;
import org.lwjgl.glfw.GLFW;

/**
 * <b>魔法轮盘施法界面</b> —— 按住施法键（默认 {@code R}）打开、鼠标方向瞄准、
 * 松手释放 / 结束。
 *
 * <h3>为什么是 Screen（而不是纯 HUD）</h3>
 * <p>本界面用 {@code Screen} 承接键盘与鼠标事件（松手 / 右键 / {@code Esc}），
 * 但 {@link #isPauseScreen()} 返回 {@code false}：世界继续 tick，受击、冷却、
 * 法力回复与正在维持的法术都照常推进；也<b>不</b>调用 {@code renderBackground}
 * 让世界变黑，只在世界之上铺一层半透明压暗 + 深空星野，让轮盘成为视觉焦点。</p>
 *
 * <h3>手势映射（{@code docs/spell-wheel-design.md} §6.2）</h3>
 * <ul>
 *   <li><b>IMMEDIATELY</b>：不需要停留确认，松手时发 {@code CAST}；</li>
 *   <li><b>CHARGE</b>：选中并停留 {@value #CHARGE_DWELL_TICKS} tick（120 ms）后发
 *       {@code BEGIN} 开始蓄力（服务端进 {@code CHARGING}），松手发 {@code END}；
 *       鼠标移出该扇区发 {@code CANCEL} 并回到瞄准；</li>
 *   <li><b>KEEP</b>：选中并停留 {@value #KEEP_DWELL_TICKS} tick（250 ms）后发
 *       {@code BEGIN} 起手；服务端回执成功后 {@code WheelFeedbackPacket} 会把本 Screen
 *       关掉并把客户端相位切到 {@code MAINTAINING}，之后由 {@code MagicClientHandler}
 *       在松开 R 时发 {@code END}。</li>
 * </ul>
 *
 * <h3>关闭规则</h3>
 * <p>松手：瞄准态 IMMEDIATELY → {@code CAST}；蓄力中 → {@code END}；空位 / 死区 → 直接关。
 * 右键 / {@code Esc} / 被外部关闭：蓄力中补 {@code CANCEL}（零代价作废），
 * 已发送过终态动作（CAST/END）时不重复发；{@code MAINTAINING} 时不发 {@code CANCEL}
 * —— 此时轮盘已经收拢成底部维持条，松开 R 由别人收尾。</p>
 *
 * <h3>渲染</h3>
 * <p>轮盘的几何、扇区状态（hover/selected/冷却/蓝不足/蓄力环）、核心文字与 HUD chrome
 * 全部由共享的 {@link WheelRenderer} 画；这里只负责背景与把本帧的交互状态传进去。
 * 星野用项目已有的 {@link MagicGuiShaders#drawStarfield}，失败时半透明压暗就是全部背景。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpellWheelScreen extends Screen {

    // ══════════════════════════════════════════════════════════════
    //  常量
    // ══════════════════════════════════════════════════════════════

    /** CHARGE：停留 3 tick（120 ms）后发 BEGIN 开始蓄力。 */
    private static final int CHARGE_DWELL_TICKS = 3;
    /** KEEP：停留 5 tick（250 ms）后发 BEGIN 起手。 */
    private static final int KEEP_DWELL_TICKS = 5;
    /** 打开动画 180 ms（设计稿硬要求：打开瞬间必须有反馈，不能超过 200 ms）。 */
    private static final long OPEN_ANIMATION_MS = 180L;
    /** 服务端拒绝 BEGIN 后，多少 tick 内不在同一扇区重试（避免连续发包 / 提示刷屏）。 */
    private static final int BEGIN_RETRY_BLOCK_TICKS = 20;
    /** KEEP 的 BEGIN 回执最长等待：超时视为没起手，回到瞄准。 */
    private static final int KEEP_CONFIRM_TIMEOUT_TICKS = 20;

    // ══════════════════════════════════════════════════════════════
    //  运行时状态
    // ══════════════════════════════════════════════════════════════

    /** 当前轮盘页（由 {@code MagicClientHandler} 打开时传入）。 */
    private final int page;
    /** 打开时刻（墙钟，ms），用于 180 ms 展开动画。 */
    private final long openedAtMs;

    /** 最近一次 render 收到的 GUI 坐标鼠标位置（tick 里用来算朝向）。 */
    private int frameMouseX;
    private int frameMouseY;

    /** 鼠标指向的方位（-1 = 死区 / 越界 / 未移动）。 */
    private int hovered = -1;
    /** 当前选中方位，跟随 {@link #hovered}；-1 允许。 */
    private int selected = -1;

    /** 在同一个扇区上停留了多少 tick（用于 CHARGE / KEEP 的停留阈值）。 */
    private int dwellTicks;
    /** 上一次停留计数的扇区；变化时清零。 */
    private int lastDwellIndex = Integer.MIN_VALUE;
    /** 在这个 tick 之前不重试 BEGIN（服务端刚拒绝过）。 */
    private int beginBlockedUntilTick;
    /** 刚被服务端拒绝过的扇区；不主动重试，等鼠标换方向。 */
    private int failedIndex = -1;

    // ── CHARGE 蓄力会话（本地预测；服务端另有权威时钟）──
    private boolean charging;
    private int chargeIndex = -1;
    private int chargeStartTick;
    private int chargeTotalTicks;
    private float chargeFraction;

    // ── KEEP 起手等待（BEGIN 已发、回执未到）──
    private boolean keepPending;
    private int keepIndex = -1;
    private int keepPendingUntilTick;

    // ── 关闭 / 终态动作 ──
    /** 已经发出 CAST / END 这类终态动作，关闭时不要再补 CANCEL。 */
    private boolean terminalActionSent;
    /** 关闭清理只做一次（onClose 与 removed 都会进来）。 */
    private boolean closeHandled;

    // ══════════════════════════════════════════════════════════════
    //  生命周期
    // ══════════════════════════════════════════════════════════════

    public SpellWheelScreen(int page) {
        super(Component.translatable("rotten_ruins.gui.wheel.title"));
        this.page = page;
        this.openedAtMs = Util.getMillis();
    }

    @Override
    protected void init() {
        super.init();
        // 正常由 MagicClientHandler 先 beginSession 再打开；这里兜底直接 new 的情况。
        if (WheelClientState.phase() == WheelClientState.Phase.NONE) {
            WheelClientState.beginSession(page);
        }
        // Minecraft.setScreen() 在打开任何 Screen 前都会 KeyMapping.releaseAll()，
        // 把"施法键还按着"这件事丢了。KEEP 起手后 MagicClientHandler 靠
        // OPEN_WHEEL.isDown() 判断玩家是否已松手，所以这里把物理按键状态补回去。
        syncWheelKeyState();
        if (frameMouseX == 0 && frameMouseY == 0) {
            // 首帧尚未 render 时先把"鼠标"放在屏幕中心（死区，不会误选）。
            frameMouseX = width / 2;
            frameMouseY = height / 2;
        }
        lastDwellIndex = Integer.MIN_VALUE;
        dwellTicks = 0;
    }

    /** 轮盘是战斗界面：世界继续跑，不暂停。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 打开动画进度 0..1（easeOut 由 WheelRenderer 负责）。 */
    private float openProgress() {
        long elapsed = Util.getMillis() - openedAtMs;
        if (elapsed <= 0L) {
            return 0.0F;
        }
        if (elapsed >= OPEN_ANIMATION_MS) {
            return 1.0F;
        }
        return elapsed / (float) OPEN_ANIMATION_MS;
    }

    // ══════════════════════════════════════════════════════════════
    //  每 tick：选中 / 停留 / 蓄力 / 起手
    // ══════════════════════════════════════════════════════════════

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null || mc.level == null) {
            closeNow();
            return;
        }
        Player player = mc.player;
        WheelClientState.Phase phase = WheelClientState.phase();

        // KEEP 起手成功后 WheelFeedbackPacket 已经把 Screen 关了并切到 MAINTAINING；
        // 这里只是兜底（例如界面由别的路径重新打开）。
        if (phase == WheelClientState.Phase.MAINTAINING) {
            closeNow();
            return;
        }
        if (phase == WheelClientState.Phase.NONE) {
            WheelClientState.beginSession(page);
            phase = WheelClientState.phase();
        }

        // 鼠标方向 → 扇区；选中跟随悬停。只有左键点击才会真正起手，
        // 这样轮盘打开后可以慢慢看，不需要一直按住 R。
        float[] layout = WheelRenderer.layout(width, height);
        hovered = WheelRenderer.hoveredIndex(frameMouseX, frameMouseY,
                layout[0], layout[1], layout[2], layout[3]);
        selected = hovered;

        // ② KEEP 起手等待：只要回执没来（成功会关界面），这里保持不动；超时视为没起手。
        if (keepPending) {
            if (player.tickCount - keepPendingUntilTick > 0) {
                failAndReturnToAiming(player, keepIndex);
            }
            return;
        }

        // ③ CHARGE 蓄力中：服务端不在 CHARGING 说明 BEGIN 被拒/会话被取消；
        //    蓄力进度只在本地表现，松开左键时发 END。
        if (charging) {
            if (phase != WheelClientState.Phase.CHARGING) {
                failAndReturnToAiming(player, chargeIndex);
                return;
            }
            int elapsed = Math.max(0, player.tickCount - chargeStartTick);
            chargeFraction = chargeTotalTicks <= 0
                    ? 1.0F
                    : Math.min(1.0F, elapsed / (float) chargeTotalTicks);
        }
    }

    /** 选中收住了：发 BEGIN 开始 CHARGE 蓄力，并在本地立即进入蓄力表现。 */
    private void beginCharge(Player player, WheelData.Slot slot) {
        WheelActionPacket.sendBegin(page, hovered);
        WheelClientState.select(page, hovered, slot.poolSlot());
        WheelClientState.toCharging();
        charging = true;
        chargeIndex = hovered;
        chargeStartTick = player.tickCount;
        chargeTotalTicks = Math.max(1, MagicStats.chargeTicks(player, slot.stack()));
        chargeFraction = 0.0F;
        failedIndex = -1;
        lastDwellIndex = hovered;
    }

    /** 选中收住了：发 BEGIN 让服务端起手 KEEP；成功后回执会关掉本界面。 */
    private void beginKeep(Player player, WheelData.Slot slot) {
        WheelActionPacket.sendBegin(page, hovered);
        WheelClientState.select(page, hovered, slot.poolSlot());
        keepPending = true;
        keepIndex = hovered;
        keepPendingUntilTick = player.tickCount + KEEP_CONFIRM_TIMEOUT_TICKS;
        failedIndex = -1;
        lastDwellIndex = hovered;
    }

    /** 回到瞄准态（不阻塞重试）。 */
    private void resetToAiming(Player player) {
        charging = false;
        chargeIndex = -1;
        chargeFraction = 0.0F;
        keepPending = false;
        keepIndex = -1;
        lastDwellIndex = Integer.MIN_VALUE;
        dwellTicks = 0;
        if (WheelClientState.phase() != WheelClientState.Phase.MAINTAINING) {
            WheelClientState.beginSession(page);
        }
    }

    /** 服务端拒绝 / 回执超时：回瞄准，并在这个扇区上停止自动重试。 */
    private void failAndReturnToAiming(Player player, int slotIndex) {
        resetToAiming(player);
        failedIndex = slotIndex;
        beginBlockedUntilTick = player.tickCount + BEGIN_RETRY_BLOCK_TICKS;
    }

    // ══════════════════════════════════════════════════════════════
    //  渲染
    // ══════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        frameMouseX = mouseX;
        frameMouseY = mouseY;
        Minecraft mc = this.minecraft;
        if (mc == null) {
            return;
        }
        float time = WheelRenderer.timeSeconds();
        float openT = openProgress();

        // ① 半透明压暗世界：不调用 renderBackground（世界不暂停，也不该被涂黑）。
        gui.fill(0, 0, width, height, 0xB602040A);

        // ② 深空星野（着色器不可用时，上面的压暗就是全部背景）。
        float mouseNormX = width <= 0 ? 0.0F : mouseX / (float) width * 2.0F - 1.0F;
        float mouseNormY = height <= 0 ? 0.0F : mouseY / (float) height * 2.0F - 1.0F;
        if (MagicGuiShaders.drawStarfield(gui, width, height, time, mouseNormX, mouseNormY)) {
            // 星野之上再盖一层薄暗，保证轮盘、图标与文字对比度。
            gui.fill(0, 0, width, height, 0x6E000000);
        }

        // ③ 轮盘 + HUD chrome（标题 / 法力条 / 提示 / 页指示）
        float[] layout = WheelRenderer.layout(width, height);
        WheelData.Slot[] slots = WheelData.page(page);
        WheelRenderer.render(gui, layout[0], layout[1], layout[2], layout[3],
                slots, hovered, selected, openT, charging ? chargeFraction : 0.0F, false, time);
        WheelRenderer.renderHudChrome(gui, width, height, layout[0], layout[1],
                layout[2], layout[3], openT, time);
    }

    // ══════════════════════════════════════════════════════════════
    //  输入：松手释放 / 右键取消 / ESC
    // ══════════════════════════════════════════════════════════════

    /**
     * 轮盘是「按 R 打开，右键 / 再按 R 关闭」的开关式界面，不需要长按施法键。
     * 施法改为鼠标左键点击方位：
     * <ul>
     *   <li>IMMEDIATELY：左键点击 → 立即施放并关闭；</li>
     *   <li>CHARGE：按住左键蓄力、松开左键释放；</li>
     *   <li>KEEP：左键起手维持，回执成功关界面；之后右键 / 再按 R 结束维持。</li>
     * </ul>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MagicClientHandler.OPEN_WHEEL.matches(keyCode, scanCode)) {
            cancelAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** R / 右键 / ESC 关闭：蓄力中或 KEEP 等待中要通知服务端 CANCEL，避免残留会话。 */
    private void cancelAndClose() {
        if (closeHandled) {
            return;
        }
        MagicClientHandler.suppressWheelOpen();
        WheelClientState.Phase phase = WheelClientState.phase();
        if (!terminalActionSent && (charging || keepPending
                || phase == WheelClientState.Phase.CHARGING)) {
            WheelActionPacket.sendCancel();
            terminalActionSent = true;
        }
        closeNow();
    }

    /** 左键点击方位施法；右键关闭。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            cancelAndClose();
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (charging || keepPending) {
            return true;
        }
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return true;
        }
        float[] layout = WheelRenderer.layout(width, height);
        int index = WheelRenderer.hoveredIndex(mouseX, mouseY,
                layout[0], layout[1], layout[2], layout[3]);
        WheelData.Slot[] slots = WheelData.page(page);
        if (index < 0 || index >= slots.length) {
            return true;
        }
        WheelData.Slot slot = slots[index];
        if (slot == null || !slot.occupied() || slot.spell() == null) {
            return true;
        }
        hovered = index;
        selected = index;
        UsingType using = slot.spell().getUsingType();
        if (using == UsingType.IMMEDIATELY) {
            WheelActionPacket.sendCast(page, index);
            terminalActionSent = true;
            closeNow();
        } else if (using == UsingType.CHARGE) {
            beginCharge(player, slot);
        } else if (using == UsingType.KEEP) {
            beginKeep(player, slot);
        }
        return true;
    }

    /** 左键松开：CHARGE 蓄力结束 = 释放法术；其余按键不响应。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && charging) {
            WheelActionPacket.sendEnd();
            terminalActionSent = true;
            closeNow();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 把施法键的 {@code KeyMapping} 状态同步回物理按键的真实状态。
     *
     * <p>1.20.1 的 {@code Minecraft#setScreen(Screen)} 在打开 Screen 之前会调用
     * {@code KeyMapping.releaseAll()}（防止菜单里残留"还按着 W"的移动状态），
     * 这对"按住施法键"的交互是致命的：轮盘一关，{@code OPEN_WHEEL.isDown()} 恒为 false。
     * 而 {@code KEEP} 起手成功后 {@code WheelFeedbackPacket} 会先从回执里关掉本界面，
     * {@code MagicClientHandler} 再用 {@code !OPEN_WHEEL.isDown()} 判断"玩家松手没有"
     * —— 不补回真实按键状态的话，维持条会在起手的下一个 tick 就被误判成松手而结束。</p>
     *
     * <p>因此打开 / 关闭 Screen 时都按物理按键写回一次：打开时恢复"按着"；
     * 关闭时如果玩家已经松开（正常 CAST / 松手结束）立刻恢复 false，如果是回执关的界面
     * 则保持 true，直到玩家真正松开（那时 Screen 已关，原版会自己更新映射）。</p>
     */
    private void syncWheelKeyState() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        InputConstants.Key bound = MagicClientHandler.OPEN_WHEEL.getKey();
        long window = mc.getWindow().getWindow();
        boolean down = false;
        if (bound.getType() == InputConstants.Type.KEYSYM) {
            down = bound.getValue() != InputConstants.UNKNOWN.getValue()
                    && InputConstants.isKeyDown(window, bound.getValue());
        } else if (bound.getType() == InputConstants.Type.MOUSE) {
            down = GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
        }
        MagicClientHandler.OPEN_WHEEL.setDown(down);
    }

    // ══════════════════════════════════════════════════════════════
    //  关闭清理
    // ══════════════════════════════════════════════════════════════

    private void closeNow() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        cleanupOnClose();
        super.onClose();
    }

    @Override
    public void removed() {
        cleanupOnClose();
        // 关闭后把施法键的按下状态还给物理现实：KEEP 维持在关了界面之后
        // 还要靠 OPEN_WHEEL.isDown() 判断"玩家是不是还按着"。
        syncWheelKeyState();
        super.removed();
    }

    /**
     * 关闭清理：
     * <ul>
     *   <li>蓄力中（且没发过 CAST/END）：发 {@code CANCEL} 作废、零代价；</li>
     *   <li>{@code MAINTAINING}：<b>不</b>取消也不用 reset —— 维持条还要靠这份相位，
     *       松开 R 由 {@code MagicClientHandler} 发 {@code END}；</li>
     *   <li>其余情况把客户端相位清干净，避免下次打开界面时读到上一次的会话。</li>
     * </ul>
     */
    private void cleanupOnClose() {
        if (closeHandled) {
            return;
        }
        closeHandled = true;
        WheelClientState.Phase phase = WheelClientState.phase();
        if (!terminalActionSent
                && (charging || keepPending || phase == WheelClientState.Phase.CHARGING)) {
            WheelActionPacket.sendCancel();
        }
        if (phase != WheelClientState.Phase.MAINTAINING) {
            WheelClientState.reset();
        }
    }
}
