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
 * 绯红誓约「起誓」系特效的<b>延后回放队列</b>（光影包兼容）。
 *
 * <h3>为什么需要它</h3>
 * <p>与裁决领域 / 天穹裁决光柱是同一个原因：{@code Oculus/Iris} 激活时
 * 世界 pass 写的是 <b>GBuffer</b>（albedo / normal / depth / specular），
 * 最后由 pack 的合成趟重建画面。我们这三样东西都是<b>自发光</b>的自定义
 * 着色器特效，写进 GBuffer 会被 pack 当成"一块贴在那里的材质"重新参与
 * 光照与雾 —— 颜色与亮度全部失控。</p>
 * <p>做法：检测到 pack 就<b>不画</b>，只把当帧的全部参数快照进队列；
 * 等 pack 把最终画面合成到主帧缓冲之后（{@code renderLevel()} 的 TAIL），
 * 在 MAIN_TARGET 上原样回放一遍。</p>
 *
 * <h3>⚠️ 矩阵与相机必须快照</h3>
 * <p>回放发生在第一人称手部渲染之后，而且已经是<b>下一帧</b>：</p>
 * <ul>
 *   <li>投影矩阵在那期间会被手持物那趟换掉；</li>
 *   <li>{@code getMainCamera()} 在回放时拿到的是下一帧的相机。</li>
 * </ul>
 * <p>所以入队时把<b>视基</b>（世界→视旋转的三行）与相机世界位置一起快照，
 * 回放时用它们重建 {@code R · T(-camera)}。视基而不是矩阵：
 * 立即路径本来就是从 pose 的法线矩阵取这三个向量的，两条路径因此
 * <b>同源</b>，不可能出现"立即画对了、光影下歪了"这种分叉。</p>
 * <p>投影矩阵也一并快照：它只用来在回放趟里
 * {@code RenderSystem.setProjectionMatrix} 还原当时的投影，好让回放出来的
 * 光柱与立即路径的透视完全一致。</p>
 */
public final class CrimsonVowLateRenderQueue {

    private CrimsonVowLateRenderQueue() {}

    /** 一个法阵的快照：参数 + 视基 + 相机 + 投影。 */
    private record FieldEntry(CrimsonVowFieldRenderer.Params params,
                              float[] basis, double camX, double camY, double camZ,
                              Matrix4f projection) {}

    private static final List<FieldEntry> FIELDS = new ArrayList<>();
    private static final List<ChargeEntry> CHARGES = new ArrayList<>();

    /** 一个蓄力法阵的快照：它不需要视基（着色器只吃视空间坐标），但需要相机与投影。 */
    private record ChargeEntry(CrimsonVowChargeRenderer.Snapshot snapshot,
                               float[] basis, double camX, double camY, double camZ) {}

    /** 入队一个巨型法阵。<b>必须在渲染线程、主 pass 的那一刻调用</b>（要快照矩阵）。 */
    public static void enqueueField(CrimsonVowFieldRenderer.Params params,
                                    float[] basis, Vec3 camera, Matrix4f projection) {
        FIELDS.add(new FieldEntry(params, basis.clone(),
                camera.x, camera.y, camera.z, new Matrix4f(projection)));
    }

    /** 入队一个蓄力法阵。 */
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

            // 场景拷贝：此刻主帧缓冲里已经是 pack 合成后的最终画面，
            // 所以这一张正是扭曲柱要采样的"底图"。
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

    /**
     * 由视基 + 相机位置重建 {@code R · T(-camera)}。
     *
     * <p>{@code basis} 存的是 R 的三<b>行</b>（视空间各轴在世界空间的方向）。
     * JOML 的命名是 {@code m<列><行>}，所以填进矩阵时要按列写：
     * 第 0 列 = (m00,m01,m02) = (row0.x, row1.x, row2.x) = (basis[0], basis[3], basis[6])。</p>
     */
    private static Matrix4f viewFromBasis(float[] basis, Vec3 cam) {
        Matrix3f rot = new Matrix3f(
                basis[0], basis[3], basis[6],
                basis[1], basis[4], basis[7],
                basis[2], basis[5], basis[8]);
        return new Matrix4f().identity().set(rot)
                .translate((float) -cam.x, (float) -cam.y, (float) -cam.z);
    }
}
