package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.bytechen.rottenruins.client.rend.glint.LateOutlineRenderState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 绯红誓约「起誓」系特效的延后回放队列（光影包兼容）：检测到 Oculus/Iris 就不画，只把当帧参数快照进队列，
 * 等 pack 合成到主帧缓冲之后（{@code renderLevel()} 的 TAIL）在 MAIN_TARGET 上回放 —— 世界 pass 写的是 GBuffer，
 * 自发光特效写进去会被当成材质重新参与光照与雾。矩阵与相机必须快照（回放已是下一帧）；快照视基而非矩阵，两条路径才同源。
 */
public final class CrimsonVowLateRenderQueue {

    private CrimsonVowLateRenderQueue() {}

    private record FieldEntry(CrimsonVowFieldRenderer.Params params,
                              float[] basis, double camX, double camY, double camZ,
                              Matrix4f projection) {}

    private static final List<FieldEntry> FIELDS = new ArrayList<>();
    private static final List<ChargeEntry> CHARGES = new ArrayList<>();

    private record ChargeEntry(CrimsonVowChargeRenderer.Snapshot snapshot,
                               float[] basis, double camX, double camY, double camZ) {}

    /** 入队一个巨型法阵。必须在渲染线程、主 pass 的那一刻调用（要快照矩阵）。 */
    public static void enqueueField(CrimsonVowFieldRenderer.Params params,
                                    float[] basis, Vec3 camera, Matrix4f projection) {
        FIELDS.add(new FieldEntry(params, basis.clone(),
                camera.x, camera.y, camera.z, new Matrix4f(projection)));
    }

    public static void enqueueCharge(CrimsonVowChargeRenderer.Snapshot snapshot,
                                     float[] basis, Vec3 camera) {
        if (snapshot == null || !snapshot.valid) return;
        CHARGES.add(new ChargeEntry(snapshot, basis.clone(), camera.x, camera.y, camera.z));
    }

    /** 由 {@code CosmicAfterLevelMixin} 在 {@code GameRenderer.renderLevel()} 的 TAIL 调用。 */
    public static void renderAll() {
        if (FIELDS.isEmpty() && CHARGES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }

        LateOutlineRenderState.prepareMainTargetPass();

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        try {
            // 投影矩阵取队首那一份：同一帧里所有入队项用的是同一个投影。
            Matrix4f projection = FIELDS.isEmpty() ? savedProjection : FIELDS.get(0).projection();
            RenderSystem.setProjectionMatrix(new Matrix4f(projection), VertexSorting.DISTANCE_TO_ORIGIN);

            // 场景拷贝：此刻主帧缓冲里已是 pack 合成后的最终画面，正是扭曲柱要采样的"底图"。
            if (!FIELDS.isEmpty()) ShockwaveRenderer.doSceneCopy();

            for (FieldEntry e : FIELDS) {
                Vec3 cam = new Vec3(e.camX(), e.camY(), e.camZ());
                Matrix4f view = viewFromBasis(e.basis(), cam);
                RenderSystem.disableScissor();
                CrimsonVowFieldRenderer.drawAllDeferred(e.params(), view, e.basis(), cam);
            }

            for (ChargeEntry e : CHARGES) {
                Vec3 cam = new Vec3(e.camX(), e.camY(), e.camZ());
                Matrix4f view = viewFromBasis(e.basis(), cam);
                RenderSystem.disableScissor();
                CrimsonVowChargeRenderer.draw(e.snapshot(), view, mc.level);
            }
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            LateOutlineRenderState.finishMainTargetPass();
            clear();
        }
    }

    private static void clear() {
        FIELDS.clear();
        CHARGES.clear();
    }

    /** 由视基 + 相机位置重建 {@code R · T(-camera)}。
     *  {@code basis} 存的是 R 的三<b>行</b>，而 JOML 命名是 {@code m<列><行>}，所以按列写：
     *  第 0 列 = (basis[0], basis[3], basis[6])。 */
    private static Matrix4f viewFromBasis(float[] basis, Vec3 cam) {
        Matrix3f rot = new Matrix3f(
                basis[0], basis[3], basis[6],
                basis[1], basis[4], basis[7],
                basis[2], basis[5], basis[8]);
        return new Matrix4f().identity().set(rot)
                .translate((float) -cam.x, (float) -cam.y, (float) -cam.z);
    }
}
