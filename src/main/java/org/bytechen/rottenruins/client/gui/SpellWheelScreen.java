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
import org.bytechen.rottenruins.gameplay.magic.stats.MagicStats;
import org.bytechen.rottenruins.client.MagicClientHandler;
import org.bytechen.rottenruins.client.gui.wheel.WheelClientState;
import org.bytechen.rottenruins.client.gui.wheel.WheelData;
import org.bytechen.rottenruins.client.gui.wheel.WheelRenderer;
import org.bytechen.rottenruins.platform.forge.magic.network.c2s.WheelActionPacket;
import org.bytechen.rottenruins.core.magic.model.UsingType;
import org.lwjgl.glfw.GLFW;

/**
 * 魔法轮盘施法界面：按施法键（默认 R）打开、鼠标瞄准扇区、左键点击施法。
 * 用 Screen 承接键盘/鼠标事件，但 isPauseScreen() 返回 false（世界继续 tick），
 * 也不调 renderBackground，只叠半透明压暗 + 星野，让轮盘成为视觉焦点。
 * 手势：IMMEDIATELY 松手 CAST；CHARGE 停留 {@value #CHARGE_DWELL_TICKS} tick 发 BEGIN、
 * 松手 END；KEEP 停留 {@value #KEEP_DWELL_TICKS} tick 发 BEGIN，回执成功即关界面并转 MAINTAINING。
 * 轮盘几何、扇区状态与 HUD chrome 全由 {@link WheelRenderer} 画，这里只管背景与本帧交互状态。
 */
@OnlyIn(Dist.CLIENT)
public final class SpellWheelScreen extends Screen {

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

    /** 当前轮盘页（由 {@code MagicClientHandler} 打开时传入）。 */
    private final int page;
    /** 打开时刻（墙钟，ms），用于 180 ms 展开动画。 */
    private final long openedAtMs;

    /** 最近一次 render 收到的 GUI 坐标鼠标位置（tick 里用来算朝向）。 */
    private int frameMouseX;
    private int frameMouseY;

    /** 鼠标指向的方位（-1 = 死区 / 越界 / 未移动）。 */
    private int hovered = -1;
    private int selected = -1;

    /** 在同一个扇区上停留了多少 tick（用于 CHARGE / KEEP 的停留阈值）。 */
    private int dwellTicks;
    private int lastDwellIndex = Integer.MIN_VALUE;
    /** 在这个 tick 之前不重试 BEGIN（服务端刚拒绝过）。 */
    private int beginBlockedUntilTick;
    private int failedIndex = -1;

    // CHARGE 会话（本地预测；服务端另有权威时钟）
    private boolean charging;
    private int chargeIndex = -1;
    private int chargeStartTick;
    private int chargeTotalTicks;
    private float chargeFraction;

    // KEEP 起手等待（BEGIN 已发、回执未到）
    private boolean keepPending;
    private int keepIndex = -1;
    private int keepPendingUntilTick;

    /** 已经发出 CAST / END 这类终态动作，关闭时不要再补 CANCEL。 */
    private boolean terminalActionSent;
    /** 关闭清理只做一次（onClose 与 removed 都会进来）。 */
    private boolean closeHandled;

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
        // setScreen() 打开任何 Screen 前都会 KeyMapping.releaseAll()，把"施法键还按着"丢了；
        // KEEP 起手后要靠 OPEN_WHEEL.isDown() 判断玩家是否松手，所以把物理按键状态补回去。
        syncWheelKeyState();
        if (frameMouseX == 0 && frameMouseY == 0) {
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

        // MAINTAINING 兜底：正常路径下回执已关界面并切相位
        if (phase == WheelClientState.Phase.MAINTAINING) {
            closeNow();
            return;
        }
        if (phase == WheelClientState.Phase.NONE) {
            WheelClientState.beginSession(page);
            phase = WheelClientState.phase();
        }

        // 鼠标方向 → 扇区，选中跟随悬停；只有左键点击才真正起手，所以能慢慢看
        float[] layout = WheelRenderer.layout(width, height);
        hovered = WheelRenderer.hoveredIndex(frameMouseX, frameMouseY,
                layout[0], layout[1], layout[2], layout[3]);
        selected = hovered;

        // KEEP 起手等待：回执没来的话保持不动（成功会关界面），超时视为没起手
        if (keepPending) {
            if (player.tickCount - keepPendingUntilTick > 0) {
                failAndReturnToAiming(player, keepIndex);
            }
            return;
        }

        // 蓄力中：服务端不在 CHARGING 说明 BEGIN 被拒 / 会话被取消；进度只在本地表现
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

        // 半透明压暗世界：不调 renderBackground（世界不暂停，也不该被涂黑）
        gui.fill(0, 0, width, height, 0xB602040A);

        // 深空星野；着色器不可用时上面的压暗就是全部背景
        float mouseNormX = width <= 0 ? 0.0F : mouseX / (float) width * 2.0F - 1.0F;
        float mouseNormY = height <= 0 ? 0.0F : mouseY / (float) height * 2.0F - 1.0F;
        if (MagicGuiShaders.drawStarfield(gui, width, height, time, mouseNormX, mouseNormY)) {
            gui.fill(0, 0, width, height, 0x6E000000);
        }

        float[] layout = WheelRenderer.layout(width, height);
        WheelData.Slot[] slots = WheelData.page(page);
        WheelRenderer.render(gui, layout[0], layout[1], layout[2], layout[3],
                slots, hovered, selected, openT, charging ? chargeFraction : 0.0F, false, time);
        WheelRenderer.renderHudChrome(gui, width, height, layout[0], layout[1],
                layout[2], layout[3], openT, time);
    }

    /**
     * 开关式界面：R / 右键关闭，不必长按施法键。施法走左键 —— IMMEDIATELY 点击即放并关闭，
     * CHARGE 按住蓄力、松开释放，KEEP 左键起手维持、之后右键或再按 R 结束。
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
     * 把施法键的 KeyMapping 状态同步回物理按键：setScreen(Screen) 打开界面前会调
     * KeyMapping.releaseAll()，于是 OPEN_WHEEL.isDown() 恒为 false，KEEP 维持会在下一 tick
     * 被当成松手结束。打开 / 关闭时各写回一次。
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
        // 关界面后把施法键状态还给物理现实：KEEP 维持仍要靠 OPEN_WHEEL.isDown()
        syncWheelKeyState();
        super.removed();
    }

    /**
     * 关闭清理：蓄力中补发 CANCEL（零代价作废）；MAINTAINING 不动相位（松开 R 由别人发 END）；
     * 其余情况重置客户端相位，避免下次打开界面读到旧会话。
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
