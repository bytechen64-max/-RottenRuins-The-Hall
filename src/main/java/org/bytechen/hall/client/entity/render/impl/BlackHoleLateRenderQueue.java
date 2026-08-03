package org.bytechen.hall.client.entity.render.impl;

import org.bytechen.hall.client.rend.glint.LateOutlineRenderState;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 黑洞延迟渲染队列 —— Oculus/Iris 光影兼容（与 {@link ShockwaveLateRenderQueue} 相同策略）。
 *
 * <h3>Why</h3>
 * 黑洞着色器需要场景拷贝纹理（从主帧缓冲 blit），并直写自定义着色画面。
 * 光影激活时主帧缓冲在 {@code AFTER_TRIPWIRE_BLOCKS} 阶段是原始 GBuffer 数据，
 * 尚未合成最终画面。延迟到 {@code GameRenderer.renderLevel()} TAIL 回放，
 * 场景拷贝捕获的是光影合成后的完整画面，且经 {@code LateOutlineRenderState}
 * 绑定主 RT 直写，绕过光影的 GBuffer 管线。
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>{@link BlackHoleRenderer#renderOne} 检测到光影激活 →
 *       快照黑洞视图位置 + 球体 modelView 矩阵入队。</li>
 *   <li>{@code CosmicAfterLevelMixin} TAIL 调用 {@link #renderAll()}。</li>
 *   <li>回放：拷贝合成后主帧 → 绘制球体（主 RT 直写）。</li>
 * </ol>
 *
 * @see BlackHoleRenderer
 * @see org.bytechen.hall.mixin.CosmicAfterLevelMixin
 */
public final class BlackHoleLateRenderQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private BlackHoleLateRenderQueue() {}

    /** 入队一个黑洞的延迟渲染。必须在渲染线程调用。 */
    public static void enqueue(Vector3f bhView, Matrix4f modelView, float radius, float bend) {
        ENTRIES.add(new Entry(new Vector3f(bhView), new Matrix4f(modelView), radius, bend));
    }

    /** 回放所有入队的黑洞。由 {@code CosmicAfterLevelMixin} 在 renderLevel() TAIL 调用。 */
    public static void renderAll() {
        if (ENTRIES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        LateOutlineRenderState.prepareMainTargetPass();

        try {
            for (Entry e : ENTRIES) {
                BlackHoleRenderer.renderOneDeferred(e.bhView, e.modelView, e.radius, e.bend);
            }
        } finally {
            LateOutlineRenderState.finishMainTargetPass();
            ENTRIES.clear();
        }
    }

    // ── internal ──────────────────────────────────────────────

    private record Entry(Vector3f bhView, Matrix4f modelView, float radius, float bend) {}
}
