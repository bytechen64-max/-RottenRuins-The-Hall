package org.bytechen.rottenruins.client.rend;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.entities.population.skills.MeteorEventEntity;

/**
 * 陨石出现时把玩家的视线"拉"向天空里的十字星。
 *
 * <h3>为什么不直接锁相机</h3>
 * <p>锁死会让玩家失去操作感（而且和鼠标输入打架），读起来像过场动画而不是
 * 世界里发生的事。这里用的是<b>每帧把当前角度朝目标插值一小部分</b>：
 * 玩家的鼠标输入照常生效，只是每一帧都会被轻轻拽向十字星。玩家如果坚持
 * 往别处看，拉力会随着 {@code fade} 在 2.5 秒内退到 0 —— 想不看就能不看。</p>
 *
 * <h3>为什么用 {@code ComputeCameraAngles}</h3>
 * <p>那是原版每一帧算完相机角度之后、投影之前唯一的可写入口，
 * 与 {@code VerdictCameraEffects} 的抖动走的是同一条路。区别只在于：
 * 那边加的是噪声，这边加的是"指向目标的偏差量"。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorEventCameraPull {

    private MeteorEventCameraPull() {}

    /** 只拉前 50 tick（2.5 秒）。 */
    private static final float PULL_TICKS = 50f;
    /** 超过这个距离就不拉了。 */
    private static final double RANGE = 320.0;
    /** 每帧收敛比例（60fps 下约 0.35 秒走完大半）。 */
    private static final float K = 0.075f;
    /** 单帧最大转角（度）：60fps 下约 180°/s。 */
    private static final float MAX_YAW_STEP = 3.0f;
    private static final float MAX_PITCH_STEP = 2.2f;

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        if (event.getCamera().getEntity() != player) return;

        float pt = (float) event.getPartialTick();
        MeteorEventEntity target = null;
        float targetAge = 0f;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof MeteorEventEntity meteor)) continue;
            float age = meteor.getAge() + pt;
            if (age > PULL_TICKS) continue;
            if (meteor.position().distanceTo(player.position()) > RANGE) continue;
            if (target == null || age < targetAge) {
                target = meteor;
                targetAge = age;
            }
        }
        if (target == null) return;

        // 越到后面越轻：最后 1 秒基本放手，交还给玩家
        float fade = 1f - Mth.clamp(targetAge / PULL_TICKS, 0f, 1f);
        float k = K * fade * fade;

        Vec3 eye = player.getEyePosition();
        // 十字星在斜上方，瞄准点必须是它的真实位置（以前取的是落点正上方）
        Vec3 to = target.signPos().subtract(eye);
        float dx = (float) to.x;
        float dy = (float) to.y;
        float dz = (float) to.z;
        float targetYaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float targetPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz))
                * 180.0 / Math.PI);

        float dYaw = Mth.wrapDegrees(targetYaw - event.getYaw());
        float dPitch = Mth.wrapDegrees(targetPitch - event.getPitch());

        // ⚠️ 单帧转角必须限速：十字星现在在**斜上方**，方位可能差到 180°，
        //    纯插值在第一帧就会甩出去（实机反馈"开始视角移动不对"）。
        //    限成 3°/帧（60fps ≈ 180°/s），最远的方位也只要 1 秒转过去。
        float stepYaw = Mth.clamp(dYaw * k, -MAX_YAW_STEP, MAX_YAW_STEP);
        float stepPitch = Mth.clamp(dPitch * k, -MAX_PITCH_STEP, MAX_PITCH_STEP);
        event.setYaw(event.getYaw() + stepYaw);
        event.setPitch(Mth.clamp(event.getPitch() + stepPitch, -90f, 90f));
    }
}
