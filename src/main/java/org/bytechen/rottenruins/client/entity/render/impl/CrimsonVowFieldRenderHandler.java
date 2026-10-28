package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.rend.ScreenShake;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.rottenruins.overworld.registry.entities.population.skills.CrimsonVowChargeEntity;
import org.bytechen.rottenruins.overworld.registry.entities.population.skills.CrimsonVowFieldEntity;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 绯红誓约「起誓」系特效的绘制入口（蓄力法阵 + 巨型法阵）。
 *
 * <h3>为什么必须晚于实体</h3>
 * <p>法阵是<b>贴地</b>的东西，深度对手是"当前已经写进深度缓冲的内容"。
 * 放在 {@code EntityRenderer.render} 里画的话，它会在方块与实体<b>之前</b>
 * 落进缓冲区，于是前方的方块和生物完全挡不住它 —— 症状是"法阵穿透一切"。
 * 挪到 {@code AFTER_TRIPWIRE_BLOCKS} 之后，地形与实体都已经写好深度，
 * 圆盘与光柱开 {@code LEQUAL} + 不写深度，遮挡关系就自然成立了。</p>
 *
 * <h3>坐标系：相机平移由本类施加，且只有这一处</h3>
 * <p>顶点喂<b>世界坐标</b>，由 pose 栈的矩阵完成"世界 → 视空间"的变换。
 * 所以这里 push 一层并 {@code translate(-camera)}，渲染器内部<b>不得</b>
 * 再平移一次 —— 两处叠加的结果是几何被推到两倍距离外，症状是"彻底看不见"。
 * 反过来漏了这一处，几何会被当成相机相对坐标投影，症状是"不管在哪放
 * 都出现在世界原点"。</p>
 *
 * <h3>每帧的绘制顺序</h3>
 * <pre>
 *   ① 场景拷贝（仅当场上有法阵；全帧一次）
 *   ② 扭曲柱   —— 替换像素，必须在所有加法混合之前
 *   ③ 法阵 / 光柱 / 立方体 —— 加法混合
 * </pre>
 * <p>顺序的理由写在 {@link CrimsonVowFieldRenderer} 的类注释里。</p>
 *
 * <h3>终末不再有屏幕空间的东西</h3>
 * <p>上一版在这里额外调用过一个全屏 Strand+Muzzle 叠层
 * （{@code CrimsonVowFinaleOverlay}）。实机反馈是"激光看起来在屏幕上、
 * 而且在从左往右扫"—— 那个类整个是屏幕空间的，所以已经删掉，
 * 终末现在完全由世界空间的光柱几何表达（见 {@code drawBeam}）。</p>
 *
 * <h3>光影包</h3>
 * <p>{@code Oculus/Iris} 激活时世界 pass 写的是 GBuffer，自发光特效会被
 * pack 当成材质重新光照，颜色与亮度全部失控。所以那一路只入队、不绘制，
 * 由 {@link CrimsonVowLateRenderQueue} 在 {@code renderLevel()} 的 TAIL
 * 回放到 MAIN_TARGET。第一个守卫是 shadow pass —— 不挡住的话，
 * 法阵会被画进阴影贴图，表现为"地上多出一块诡异的阴影"。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CrimsonVowFieldRenderHandler {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // ── 收集本帧存在的法阵 ──
        //  先收集再画（而不是边遍历边画）有两个原因：需要在画任何东西之前
        //  决定"要不要做场景拷贝"，以及要让扭曲柱严格排在加法混合之前。
        List<CrimsonVowFieldEntity> fields = null;
        List<CrimsonVowChargeEntity> charges = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CrimsonVowFieldEntity field) {
                if (fields == null) fields = new ArrayList<>(2);
                fields.add(field);
            } else if (entity instanceof CrimsonVowChargeEntity charge) {
                if (charges == null) charges = new ArrayList<>(2);
                charges.add(charge);
            }
        }
        if (fields == null && charges == null) {
            LAST_PHASE.clear();
            return;
        }

        PoseStack ps = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float pt = event.getPartialTick();
        Matrix4f projection = event.getProjectionMatrix();

        // 屏幕抖动：抓"刚进入触地"这一帧。必须在绘制之前更新 ——
        // 投影矩阵是在 renderLevel 开头就算好的，这里只是把"下一次"的偏移
        // 交给下一帧；一秒之内的事件，差一帧看不出来。
        updateScreenShake(fields, camera);

        // 唯一的相机平移在这里。
        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f view = new Matrix4f(ps.last().pose());
        float[] basis = new float[9];
        CrimsonVowRenderSupport.viewBasis(ps.last(), basis);

        try {
            if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
                // 光影包：只快照并入队，一个像素都不在这里画。
                if (fields != null) {
                    for (CrimsonVowFieldEntity field : fields) {
                        CrimsonVowFieldRenderer.Params p =
                                CrimsonVowFieldRenderer.paramsOf(field, pt);
                        CrimsonVowLateRenderQueue.enqueueField(p, basis, camera, projection);
                    }
                }
                if (charges != null) {
                    for (CrimsonVowChargeEntity charge : charges) {
                        CrimsonVowLateRenderQueue.enqueueCharge(
                                CrimsonVowChargeRenderer.snapshotOf(charge, pt, mc.level),
                                basis, camera);
                    }
                }
                return;
            }

            // 场景拷贝：扭曲柱要采样的那张"底图"。全帧只做一次，
            // 而且必须在画任何自发光之前 —— 否则法阵与立方体会被写进拷贝，
            // 然后被扭曲柱自己采样一遍（症状是柱内出现一块方形重影）。
            if (fields != null) ShockwaveRenderer.doSceneCopy();

            if (fields != null) {
                for (CrimsonVowFieldEntity field : fields) {
                    CrimsonVowFieldRenderer.Params p = CrimsonVowFieldRenderer.paramsOf(field, pt);
                    CrimsonVowFieldRenderer.drawAll(p, view, basis, camera);
                }
            }
            if (charges != null) {
                for (CrimsonVowChargeEntity charge : charges) {
                    CrimsonVowChargeRenderer.draw(
                            CrimsonVowChargeRenderer.snapshotOf(charge, pt, mc.level), view, mc.level);
                }
            }
        } finally {
            ps.popPose();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  屏幕抖动（触地那一帧）
    // ══════════════════════════════════════════════════════════════

    /**
     * 每个法阵上一次看到的阶段。
     *
     * <p>用 {@code Entity#getId()} 当键：客户端与服务端对同一个实体的 id 一致，
     * 且不需要额外的同步字段。每帧用"本帧还活着的 id 集合"做一次 retain，
     * 法阵散场后条目自动消失，不会随着游戏时长越积越多。</p>
     */
    private static final Map<Integer, Integer> LAST_PHASE = new HashMap<>();

    /**
     * 在"刚进入触地"那一帧触发一次屏幕抖动。
     *
     * <h3>为什么在客户端抓相位，而不是让服务端发包</h3>
     * <p>触地这件事客户端本来就知道：{@code PHASE_IMPACT} 是同步数据，
     * 玩家站在阵边时眼睛看到的与判定用的是同一个字段。为一次纯视觉抖动
     * 新增一个网络包（还要算序列号、还要防重放）不值得 —— 这里只是读
     * 一个已经在同步的值。</p>
     *
     * <h3>强度按距离衰减</h3>
     * <p>这是半径 14 格的一击：站得越近越该被顶一下。距离用<b>水平</b>
     * 衰减就够了（{@code 1 - d/64}），并且留 0.2 的底 —— 远处的人"听到"
     * 这一击但不该被晃到看不清路。相机抖动那一层由服务端的
     * {@code VerdictFeedback.shakeNear} 负责，两层叠加。</p>
     */
    private static void updateScreenShake(List<CrimsonVowFieldEntity> fields, Vec3 camera) {
        Set<Integer> alive = new HashSet<>();
        if (fields != null) {
            for (CrimsonVowFieldEntity field : fields) {
                int id = field.getId();
                alive.add(id);
                int phase = field.getPhase();
                Integer prev = LAST_PHASE.put(id, phase);
                if (phase == CrimsonVowFieldEntity.PHASE_IMPACT
                        && (prev == null || prev != CrimsonVowFieldEntity.PHASE_IMPACT)) {
                    double dist = Math.sqrt(field.distanceToSqr(camera));
                    float near = Mth.clamp((float) (1.0 - dist / 64.0), 0f, 1f);
                    ScreenShake.impact(0.20f + 0.80f * near);
                }
            }
        }
        LAST_PHASE.keySet().retainAll(alive);
    }
}
