package org.bytechen.hall.client.entity.render.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictFeedback;

/**
 * 天穹裁决的<b>相机反馈</b>：抖动 + FOV 冲击。
 *
 * <h3>为什么服务端和客户端不在一处</h3>
 * <p>服务端只知道"这一招有多重"，不知道玩家的相机在哪、朝哪看、屏幕有多大；
 * 客户端反过来。所以分工是：</p>
 * <ul>
 *   <li>服务端（{@link VerdictFeedback#shake} / {@link VerdictFeedback#fovKick}）
 *       只往一张按 UUID 索引的表里写<b>强度</b>；</li>
 *   <li>这里在每帧的相机事件里读表、做方向与可见性判定、应用偏移，并按帧衰减。</li>
 * </ul>
 * <p>好处是不需要新增任何网络包，而且"退世界/换维度"不会留下残留状态
 * （表项在衰减到阈值以下时自己消失）。</p>
 *
 * <h3>抖动必须是"噪声"而不是"正弦"</h3>
 * <p>纯正弦会让相机像节拍器一样规律地摆，看起来很假；用三个<b>互质频率</b>
 * 的正弦叠加（3/4.7/7.3）就能得到不重复的伪噪声，代价为零。
 * 再加一条 {@code smoothstep} 包络，让抖动"起得突然、收得干净"，
 * 而不是从头到尾同一振幅地晃。</p>
 *
 * <h3>为什么幅度压得这么小</h3>
 * <p>{@link #SHAKE_ANGLE} 只有 1.35 度。第一人称相机抖动超过 2 度就开始干扰瞄准，
 * 超过 4 度会让人晕。手感上的"重"来自<b>衰减速度</b>（很短、很明显），
 * 而不是幅度上限 —— 所以宁可给小角度 + 快衰减。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VerdictCameraEffects {

    /** 最大抖动角度（度）。俯仰/偏航用它，滚转用它的 0.6 倍。 */
    private static final float SHAKE_ANGLE = 1.35f;
    /** 滚转的相对系数：滚转最容易被察觉，所以给得最小。 */
    private static final float ROLL_SCALE = 0.6f;
    /** 每帧的强度保留比例（一阶衰减）。0.80 在 60fps 下约 0.3 秒内归零。 */
    private static final float SHAKE_DECAY = 0.80f;
    /** FOV 每帧的保留比例。比抖动慢一点，速度感需要"留一下"。 */
    private static final float FOV_DECAY = 0.86f;

    private VerdictCameraEffects() {}

    private static float t = 0f;

    /**
     * 上一次消耗强度所用的 {@code partialTick}。
     *
     * <p>{@code ComputeCameraAngles} / {@code ComputeFov} 在一次帧里都会被调用<b>多次</b>
     * （主投影、手持物投影、望远镜各算一遍），而"消耗"是有副作用的 ——
     * 每帧消耗两三次的话，3 度的视野冲击会在半秒内被抽干，玩家几乎看不到。</p>
     *
     * <p>用 {@code partialTick} 当帧标识而不是系统时间：它由 {@code DeltaTracker} 每帧
     * 算一次，<b>同一帧内的所有调用拿到的是同一个值</b>，而相邻两帧的值一定不同
     * （即使暂停或未渲染 tick 也不会重复）。系统时钟做不到这一点 ——
     * 高帧率下两帧可能落在同一毫秒，于是第二帧会被误判成"同一帧"而丢失。</p>
     */
    private static float lastFovPartial = -1f;
    private static float lastShakePartial = -1f;

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Player player = localPlayer();
        if (player == null) return;
        // 抖的是"你挥出的那一招"：相机实体必须是本人（旁观/跟随相机下不抖）
        if (event.getCamera().getEntity() != player) return;

        float partial = (float) event.getPartialTick();
        if (partial == lastShakePartial) return;      // 同一帧的后续调用（手持物投影等）
        lastShakePartial = partial;

        float strength = VerdictFeedback.consumeShake(player.getUUID(), SHAKE_DECAY);
        if (strength <= 0.004f) return;

        // 全局时间推进：不依赖玩家实体 tick（相机事件本身每帧一次，是更稳的驱动源）
        t += 0.9f;

        // 三个互质频率叠加 → 不重复的伪噪声；再乘 smoothstep 包络让收尾干净
        float env = strength * strength * (3f - 2f * strength);
        float nx = Mth.sin(t * 3.1f) * 0.62f + Mth.sin(t * 4.7f + 1.7f) * 0.26f
                 + Mth.sin(t * 7.3f + 0.4f) * 0.12f;
        float ny = Mth.sin(t * 3.7f + 2.1f) * 0.62f + Mth.sin(t * 5.3f + 0.9f) * 0.26f
                 + Mth.sin(t * 8.1f + 2.6f) * 0.12f;

        event.setPitch(event.getPitch() + ny * SHAKE_ANGLE * env);
        event.setYaw(event.getYaw() + nx * SHAKE_ANGLE * env);
        event.setRoll(event.getRoll() + nx * SHAKE_ANGLE * ROLL_SCALE * env);
    }

    /**
     * FOV 冲击。
     *
     * <p>用 {@code +=} 而不是 {@code =}：原版在这个事件之前已经算好了基础 FOV
     * （疾跑、速度效果、望远镜都在这上面），直接覆盖会把它们全吃掉。
     * 同样地，这里必须<b>只加自己那一份</b>，否则和别的模组会互相踩。</p>
     *
     * <p>{@code usedConfiguredFov()} 为 false 的那些趟（望远镜/传送门扭曲等附加投影）
     * 既不加、<b>也不消耗</b> —— 强度留着给真正那一趟用。</p>
     */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Player player = localPlayer();
        if (player == null) return;
        if (event.getCamera().getEntity() != player) return;
        if (!event.usedConfiguredFov()) return;

        float partial = (float) event.getPartialTick();
        if (partial == lastFovPartial) return;        // 同一帧只施加一次
        lastFovPartial = partial;

        float kick = VerdictFeedback.consumeFovKick(player.getUUID(), FOV_DECAY);
        if (Math.abs(kick) <= 0.02f) return;

        event.setFOV(event.getFOV() + kick);
    }

    /**
     * 取本地玩家。相机实体另行判定（见 {@link #onComputeCameraAngles}）——
     * 这里返回的是"操作者"，不是"相机挂在谁身上"。
     */
    private static Player localPlayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player;
    }

    // 距离衰减刻意<b>不</b>放在这里：那个方法要由服务端的技能结算调用，
    // 而服务端类不该 import 一个 Dist.CLIENT 的类（专用服务器上通常不会真的加载失败，
    // 但那是运气而不是契约）。所以衰减放在 VerdictFeedback#shakeNear，
    // 它只用玩家自己的坐标，两端都安全。
}
