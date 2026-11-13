package org.bytechen.rottenruins.client.entity.render.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictFeedback;

/**
 * 天穹裁决的相机反馈：抖动 + FOV 冲击。服务端只往按 UUID 索引的表里写强度，这里每帧读取、应用并衰减；
 * 抖动用三个互质频率正弦叠加（纯正弦像节拍器）+ smoothstep 包络，幅度 1.35 度（>2 度干扰瞄准、>4 度致晕）。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VerdictCameraEffects {

    private static final float SHAKE_ANGLE = 1.35f;
    private static final float ROLL_SCALE = 0.6f;
    private static final float SHAKE_DECAY = 0.80f;
    private static final float FOV_DECAY = 0.86f;

    private VerdictCameraEffects() {}

    private static float t = 0f;

    /** 上一次消耗强度所用的 partialTick：一帧内这两个相机事件会被多次调用，重复消耗会抽干视野冲击。 */
    private static float lastFovPartial = -1f;
    private static float lastShakePartial = -1f;

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Player player = localPlayer();
        if (player == null) return;
        if (event.getCamera().getEntity() != player) return;

        float partial = (float) event.getPartialTick();
        if (partial == lastShakePartial) return;
        lastShakePartial = partial;

        float strength = VerdictFeedback.consumeShake(player.getUUID(), SHAKE_DECAY);
        if (strength <= 0.004f) return;

        // 全局时间推进：不依赖玩家实体 tick（相机事件本身每帧一次，是更稳的驱动源）
        t += 0.9f;

        float env = strength * strength * (3f - 2f * strength);
        float nx = Mth.sin(t * 3.1f) * 0.62f + Mth.sin(t * 4.7f + 1.7f) * 0.26f
                 + Mth.sin(t * 7.3f + 0.4f) * 0.12f;
        float ny = Mth.sin(t * 3.7f + 2.1f) * 0.62f + Mth.sin(t * 5.3f + 0.9f) * 0.26f
                 + Mth.sin(t * 8.1f + 2.6f) * 0.12f;

        event.setPitch(event.getPitch() + ny * SHAKE_ANGLE * env);
        event.setYaw(event.getYaw() + nx * SHAKE_ANGLE * env);
        event.setRoll(event.getRoll() + nx * SHAKE_ANGLE * ROLL_SCALE * env);
    }

    /** FOV 用 += 而非 =：原版此前已算好基础 FOV（疾跑/速度效果），覆盖会全吃掉；usedConfiguredFov() 为 false 的趟既不加也不消耗。 */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Player player = localPlayer();
        if (player == null) return;
        if (event.getCamera().getEntity() != player) return;
        if (!event.usedConfiguredFov()) return;

        float partial = (float) event.getPartialTick();
        if (partial == lastFovPartial) return;
        lastFovPartial = partial;

        float kick = VerdictFeedback.consumeFovKick(player.getUUID(), FOV_DECAY);
        if (Math.abs(kick) <= 0.02f) return;

        event.setFOV(event.getFOV() + kick);
    }

    private static Player localPlayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player;
    }

}
