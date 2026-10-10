package org.bytechen.hall.client.entity.render.impl;

import net.minecraft.client.Minecraft;
import org.bytechen.hall.client.rend.glint.LateOutlineRenderState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 使徒斩击扭曲的延迟渲染队列（光影兼容用）。
 *
 * <h3>为什么需要延迟</h3>
 * 扭曲要采样"已经画完的场景"，而且必须写进<b>最终合成后的</b>主帧缓冲。
 * Oculus/Iris 激活时，{@code AFTER_TRIPWIRE_BLOCKS} 时刻主帧缓冲里还是 GBuffer 原始数据，
 * 此刻做场景拷贝会拷到一堆法线/反照率，扭曲出来是一团乱码。
 * 所以那时只快照参数入队，等 {@code GameRenderer.renderLevel()} 跑到 TAIL
 * （光影已经把最终画面合成到主帧缓冲）再回放。
 *
 * <p>回放入口由 {@code CosmicAfterLevelMixin} 调用，与冲击波、黑洞共用同一批回放点。</p>
 *
 * @see ApostleSlashWarp
 * @see ShockwaveLateRenderQueue
 */
public final class ApostleSlashWarpQueue {

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private ApostleSlashWarpQueue() {}

    /** 入队一条斩击扭曲（渲染线程调用）。 */
    public static void enqueue(Matrix4f pose, Matrix3f normal,
                               float radius, float alpha, float lifeProgress) {
        ENTRIES.add(new Entry(new Matrix4f(pose), new Matrix3f(normal),
                radius, alpha, lifeProgress));
    }

    /** 回放本帧入队的全部斩击扭曲。 */
    public static void renderAll() {
        if (ENTRIES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        LateOutlineRenderState.prepareMainTargetPass();
        try {
            // 一次拷贝供本帧所有斩击共用（扭曲是屏幕空间效果，画面只取一次即可）
            ApostleSlashWarp.copySceneForDeferred();
            for (Entry e : ENTRIES) {
                ApostleSlashWarp.renderDeferred(e.pose, e.normal,
                        e.radius, e.alpha, e.lifeProgress);
            }
        } finally {
            LateOutlineRenderState.finishMainTargetPass();
            ENTRIES.clear();
        }
    }

    // ── internal ──

    private record Entry(Matrix4f pose, Matrix3f normal,
                         float radius, float alpha, float lifeProgress) {}
}
