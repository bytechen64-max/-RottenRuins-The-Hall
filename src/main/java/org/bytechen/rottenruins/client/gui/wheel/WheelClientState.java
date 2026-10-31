package org.bytechen.rottenruins.client.gui.wheel;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.api.magic.CastResult;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端轮盘会话状态（只存在于渲染/输入侧，服务端另有权威状态）。
 *
 * <p>存在这个类而不是全塞进 Screen 的原因：{@code KEEP} 起手成功后轮盘会收拢成
 * 底部的维持条，<b>Screen 已经关了</b>，但"松开 R 要结束维持"这件事还得有个地方记着。
 * 所以相位与当前方位放在这个静态状态里，由 {@code SpellWheelScreen}、{@code WheelHudOverlay}
 * 和 {@code MagicClientHandler} 共享。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WheelClientState {

    public enum Phase {
        /** 没有轮盘会话。 */
        NONE,
        /** 轮盘打开、正在瞄准（还没进入蓄力/维持）。 */
        AIMING,
        /** CHARGE：服务端已收到 BEGIN，正在蓄力。 */
        CHARGING,
        /** KEEP：服务端已起手，正在维持（轮盘已收拢为维持条）。 */
        MAINTAINING
    }

    private static Phase phase = Phase.NONE;
    private static int page;
    private static int wheelIndex = -1;
    private static int poolSlot = -1;
    /** 会话开始时客户端 tick（用于蓄力环/维持条计时显示）。 */
    private static int startTick;
    /** 本会话最近一次服务端回执。 */
    @Nullable
    private static CastResult lastResult;
    /** 本会话最近一次服务端回执对应的动作（BEGIN/CAST/END/CANCEL）。 */
    @Nullable
    private static String lastAction;

    private WheelClientState() {
    }

    public static Phase phase() {
        return phase;
    }

    public static boolean active() {
        return phase != Phase.NONE;
    }

    public static int page() {
        return page;
    }

    public static int wheelIndex() {
        return wheelIndex;
    }

    public static int poolSlot() {
        return poolSlot;
    }

    public static int startTick() {
        return startTick;
    }

    @Nullable
    public static CastResult lastResult() {
        return lastResult;
    }

    @Nullable
    public static String lastAction() {
        return lastAction;
    }

    /** 打开轮盘 / 开始一次瞄准会话。 */
    public static void beginSession(int targetPage) {
        phase = Phase.AIMING;
        page = targetPage;
        wheelIndex = -1;
        poolSlot = -1;
        startTick = currentTick();
        lastResult = null;
        lastAction = null;
    }

    /** 选中某个方位（用于蓄力/维持显示）。 */
    public static void select(int targetPage, int index, int targetPoolSlot) {
        page = targetPage;
        wheelIndex = index;
        poolSlot = targetPoolSlot;
    }

    public static void toCharging() {
        phase = Phase.CHARGING;
    }

    public static void toMaintaining(int targetPage, int index, int targetPoolSlot) {
        phase = Phase.MAINTAINING;
        page = targetPage;
        wheelIndex = index;
        poolSlot = targetPoolSlot;
    }

    /** 服务端回执写入（不改变相位，相位由具体回执语义决定）。 */
    public static void acceptFeedback(@Nullable String action, @Nullable CastResult result) {
        lastAction = action;
        lastResult = result;
    }

    public static void reset() {
        phase = Phase.NONE;
        wheelIndex = -1;
        poolSlot = -1;
        lastResult = null;
        lastAction = null;
    }

    /** 客户端持有的"已维持多少秒"，用于维持条读秒；无会话返回 0。 */
    public static float maintainingSeconds() {
        if (phase != Phase.MAINTAINING) {
            return 0.0F;
        }
        return Math.max(0, currentTick() - startTick) / 20.0F;
    }

    private static int currentTick() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.tickCount;
    }
}
