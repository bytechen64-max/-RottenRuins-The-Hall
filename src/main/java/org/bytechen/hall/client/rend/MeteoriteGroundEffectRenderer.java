package org.bytechen.hall.client.rend;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;
import java.util.UUID;

/**
 * 陨石撞击地面 —— 垂直矩形光柱特效。
 * <p>
 * 0–6 ticks：ease-out 拔地生长<br>
 * 6–46 ticks (2s)：完全可见<br>
 * 46–76 ticks (1.5s)：线性淡出<br>
 * 矩形统一尺寸，底部不透明纯白渐变为顶部完全透明，加法混合发光。
 */
public class MeteoriteGroundEffectRenderer
        extends AbstractStaticEffectRenderer<MeteoriteGroundEffectRenderer.GroundEffectInstance> {

    public static final MeteoriteGroundEffectRenderer INSTANCE = new MeteoriteGroundEffectRenderer();
    private static final String TYPE_ID = "meteorite_ground";

    /** 生长动画持续 tick 数 */
    private static final int GROW_TICKS = 6;
    /** 完全可见持续 tick 数 */
    private static final int VISIBLE_TICKS = 40;
    /** 淡出开始时刻 = GROW + VISIBLE */
    private static final int FADE_START = GROW_TICKS + VISIBLE_TICKS;
    /** 淡出持续 tick 数 */
    private static final int FADE_TICKS = 30;
    /** 全生命周期 */
    private static final int TOTAL_TICKS = GROW_TICKS + VISIBLE_TICKS + FADE_TICKS;

    /** 统一矩形尺寸 */
    private static final float RECT_HALF_WIDTH = 1.95f;
    private static final float RECT_HALF_HEIGHT = 1.08f; // 离地高度 2.16 格 (2.4×)

    static {
        registerRenderer(INSTANCE);
    }

    private MeteoriteGroundEffectRenderer() {}

    @Override public String getTypeId() { return TYPE_ID; }

    @Override
    protected GroundEffectInstance createInstance(UUID id, CompoundTag data) {
        return new GroundEffectInstance(id, data);
    }

    // ═══════════════════════════════════════════════════════════════
    // 渲染
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void doRender(PoseStack poseStack, GroundEffectInstance inst, float partialTick) {
        if (inst.rectangles == null || inst.rectangles.length == 0) return;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();

        // 生长进度 0→1（ease-out：先快后慢，平滑到达全高）
        float raw = Math.min(1f, (float) inst.age / GROW_TICKS);
        float growProgress = 1f - (1f - raw) * (1f - raw) * (1f - raw);
        // 淡出 alpha 1→0（FADE_START→TOTAL_TICKS）
        float fadeAlpha;
        if (inst.age < FADE_START) {
            fadeAlpha = 1.0f;
        } else {
            fadeAlpha = 1.0f - (float)(inst.age - FADE_START) / FADE_TICKS;
        }

        for (RectData rect : inst.rectangles) {
            renderRectangle(buffer, matrix, rect, growProgress, fadeAlpha);
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * 渲染单个矩形 —— 3 列 × 3 行网格（左边缘/中心/右边缘 × 底部/中部/顶部）。
     * <p>
     * 底部行整体不透明（地面交线无边缘淡化），中部行中心亮边缘暗，
     * 顶部行完全透明，GPU 在三角形面上自然插值。
     */
    private void renderRectangle(BufferBuilder buffer, Matrix4f matrix,
                                 RectData rect, float growProgress, float fadeAlpha) {
        float cx = rect.centerX, cy = rect.centerY, cz = rect.centerZ;
        float hw = rect.halfWidth, hh = rect.halfHeight;
        float nx = rect.normalX, nz = rect.normalZ;

        float tx = -nz, tz = nx;

        // 3 列世界坐标
        float lx = cx + tx * hw, lz = cz + tz * hw;  // 左
        float rx = cx - tx * hw, rz = cz - tz * hw;  // 右

        // 3 行 Y 坐标
        float fullTy = cy + hh * 2f;
        float by = cy;                                  // 底部
        float my = cy + hh * growProgress;              // 中部（受生长动画影响）
        float ty = by + (fullTy - by) * growProgress;   // 顶部

        float ba = fadeAlpha * 1.5f; // 底部 alpha — 完全明亮
        float ea = ba * 0.15f;       // 中部边缘 alpha — 明显暗淡
        float ca = ba * 0.85f;       // 中部中心 alpha — 依然明亮
        float ta = 0f;                // 顶部 — 完全透明

        // 顶点 (列, 行)
        // 左列: v0, v3, v6 ; 中列: v1, v4, v7 ; 右列: v2, v5, v8
        // 底行: v0,v1,v2 ; 中行: v3,v4,v5 ; 顶行: v6,v7,v8
        float[][] verts = {
            {lx, by, lz, ba}, {cx, by, cz, ba}, {rx, by, rz, ba},        // 底
            {lx, my, lz, ea}, {cx, my, cz, ca}, {rx, my, rz, ea},        // 中
            {lx, ty, lz, ta}, {cx, ty, cz, ta}, {rx, ty, rz, ta},        // 顶
        };

        // 4 个四边形 = 8 个三角形 × 正反双面 = 16 个三角形
        // 四边形: (v0,v1,v4,v3) (v1,v2,v5,v4) (v3,v4,v7,v6) (v4,v5,v8,v7)
        quad(buffer, matrix, verts, 0,1,4,3, true);
        quad(buffer, matrix, verts, 0,1,4,3, false);
        quad(buffer, matrix, verts, 1,2,5,4, true);
        quad(buffer, matrix, verts, 1,2,5,4, false);
        quad(buffer, matrix, verts, 3,4,7,6, true);
        quad(buffer, matrix, verts, 3,4,7,6, false);
        quad(buffer, matrix, verts, 4,5,8,7, true);
        quad(buffer, matrix, verts, 4,5,8,7, false);
    }

    /** 绘制一个四边形（2 个三角形），可选正面或反面顶点顺序 */
    private static void quad(BufferBuilder b, Matrix4f m, float[][] v,
                             int i0, int i1, int i2, int i3, boolean front) {
        if (front) {
            tri(b, m, v[i0], v[i1], v[i2]);
            tri(b, m, v[i0], v[i2], v[i3]);
        } else {
            tri(b, m, v[i0], v[i3], v[i2]);
            tri(b, m, v[i0], v[i2], v[i1]);
        }
    }

    private static void tri(BufferBuilder b, Matrix4f m,
                            float[] p0, float[] p1, float[] p2) {
        b.vertex(m, p0[0], p0[1], p0[2]).color(1f, 1f, 1f, p0[3]).endVertex();
        b.vertex(m, p1[0], p1[1], p1[2]).color(1f, 1f, 1f, p1[3]).endVertex();
        b.vertex(m, p2[0], p2[1], p2[2]).color(1f, 1f, 1f, p2[3]).endVertex();
    }

    // ═══════════════════════════════════════════════════════════════
    // 实例数据
    // ═══════════════════════════════════════════════════════════════

    public static class GroundEffectInstance extends EffectInstance {
        public RectData[] rectangles;

        public GroundEffectInstance(UUID id, CompoundTag tag) {
            super(id, tag.contains("maxAge") ? tag.getInt("maxAge") : TOTAL_TICKS);
            generateOnce(id, tag);
        }

        /**
         * 只在首次创建时生成矩形布局。
         * 后续 applySyncData 不会再改变矩形位置。
         */
        @Override
        public void readFromNBT(CompoundTag tag) {
            // 拒绝重复生成：矩形一旦确定就不再改变
            // 这样即使网络包被重复处理，渲染位置也始终一致
        }

        @Override
        public void writeToNBT(CompoundTag tag) {
            // 仅由服务端写入
        }

        private void generateOnce(UUID id, CompoundTag tag) {
            if (rectangles != null) return;

            Vec3 impactPos = new Vec3(
                    tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            float radius = tag.getFloat("radius");
            int count = tag.getInt("count");

            long seed = id.getLeastSignificantBits() ^ id.getMostSignificantBits();
            Random random = new Random(seed);
            rectangles = new RectData[count];

            for (int i = 0; i < count; i++) {
                RectData rect = new RectData();

                double angle = random.nextDouble() * 2.0 * Math.PI;
                double dist = Math.sqrt(random.nextDouble()) * radius;
                rect.centerX = (float)(impactPos.x + Math.cos(angle) * dist);
                rect.centerZ = (float)(impactPos.z + Math.sin(angle) * dist);
                rect.centerY = (float) impactPos.y;

                rect.halfWidth = RECT_HALF_WIDTH;
                rect.halfHeight = RECT_HALF_HEIGHT;

                double faceAngle = random.nextDouble() * 2.0 * Math.PI;
                rect.normalX = (float) Math.cos(faceAngle);
                rect.normalZ = (float) Math.sin(faceAngle);

                rectangles[i] = rect;
            }
        }
    }

    public static class RectData {
        public float centerX, centerY, centerZ;
        public float halfWidth, halfHeight;
        public float normalX, normalZ;
    }

    // ═══════════════════════════════════════════════════════════════

    public static void handleSyncPacket(UUID instanceId, CompoundTag data) {
        INSTANCE.applySyncData(instanceId, data);
    }
}
