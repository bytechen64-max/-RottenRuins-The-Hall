package org.bytechen.hall.client.entity.render.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.bytechen.hall.client.rend.glint.LateOutlineRenderState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 背部六芒星光环的<b>光影延迟回放队列</b>（与冲击波/黑洞/斩击扭曲同一套策略）。
 *
 * <h3>为什么必须延迟</h3>
 * 光环在 {@code AFTER_TRIPWIRE_BLOCKS} 阶段画。光影包（Oculus/Iris）激活时，
 * 那一刻主帧缓冲里还只是 GBuffer 的原始数据（albedo/normal/depth 等），
 * 光影的 composite pass 还在后面 —— 此刻直接画进去的东西会被当成普通几何体
 * 重新着色，效果直接丢掉（甚至整帧看不见）。
 * 所以这里和冲击波一样：<b>快照 → 入队 → 等 {@code GameRenderer.renderLevel()} TAIL
 * 光影合成完成后再回放</b>，回放时用 {@link LateOutlineRenderState} 绑主 RT 直写，
 * 绕开 GBuffer。
 *
 * <h3>和冲击波的区别</h3>
 * 光环不采样场景贴图（纯程序化加法发光），所以<b>不需要</b>做场景拷贝这一步，
 * 快照里也没有纹理、只有已经算好的四个角点和 uniform 值。
 *
 * @see CollapsarHaloRenderer
 * @see org.bytechen.hall.mixin.CosmicAfterLevelMixin
 */
public final class CollapsarHaloLateRenderQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private CollapsarHaloLateRenderQueue() {}

    /** 快照一个光环。必须在渲染线程调用。 */
    public static void enqueue(Matrix4f pose, Vec3 bl, Vec3 tl, Vec3 tr, Vec3 br,
                               float time, float alpha, float content, float agitation) {
        // 投影矩阵也一起快照：光环的顶点是在「相机相对空间」里烘焙好的，
        // 回放时必须用<b>当时</b>的投影矩阵才能对上。第一人称手部渲染会临时换成
        // 手部 FOV 的投影，回放时机在它之后，不重新设一次就有可能整体错位/看不见。
        ENTRIES.add(new Entry(new Matrix4f(pose), bl, tl, tr, br,
                new Matrix4f(RenderSystem.getProjectionMatrix()), time, alpha, content, agitation));
    }

    /**
     * 回放本帧所有光环。由 {@code CosmicAfterLevelMixin} 在 {@code renderLevel()} TAIL 调用
     * —— 此时光影已经把最终画面合成进主帧缓冲，深度也已写入。
     */
    public static void renderAll() {
        if (ENTRIES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        LateOutlineRenderState.prepareMainTargetPass();

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        try {
            // 本帧所有光环共用入队时的同一份投影（同一帧内不会变），设一次即可
            RenderSystem.setProjectionMatrix(new Matrix4f(ENTRIES.get(0).projection()),
                    VertexSorting.DISTANCE_TO_ORIGIN);
            for (Entry e : ENTRIES) {
                CollapsarHaloRenderer.drawHalo(e.pose, e.bl, e.tl, e.tr, e.br,
                        e.time, e.alpha, e.content, e.agitation);
            }
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            LateOutlineRenderState.finishMainTargetPass();
            ENTRIES.clear();
        }
    }

    // ── internal ──────────────────────────────────────────────

    private record Entry(Matrix4f pose, Vec3 bl, Vec3 tl, Vec3 tr, Vec3 br,
                         Matrix4f projection, float time, float alpha, float content, float agitation) {}
}
