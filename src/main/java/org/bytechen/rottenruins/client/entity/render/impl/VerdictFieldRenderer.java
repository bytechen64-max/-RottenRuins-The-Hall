package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.VerdictFieldEntity;
import org.bytechen.rottenruins.overworld.registry.items.verdict.VerdictDebug;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 裁决领域渲染器 —— 贴地的地面光纹圆盘。实体渲染阶段（{@code EntityRenderer.render}）画会被深度测试
 * 判到"实体与块模型之前"，表现为穿透方块，所以本类什么都不做，真正绘制交给 {@link VerdictFieldRenderHandler}
 * 在 {@code AFTER_TRIPWIRE_BLOCKS} 阶段执行（那时地形与实体已写好深度）。几何是一个平面圆盘：
 * 只在领域中心采一次地表高度，与判定形状（平面圆柱）一致；顶点喂<b>世界坐标</b>，矩阵完成世界→相机相对变换。
 */
public class VerdictFieldRenderer extends EntityRenderer<VerdictFieldEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 圆盘分段数（同时是辐条的角分辨率上限）。 */
    static final int SEGMENTS = 96;
    static final int RINGS = 8;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    /** 网格半径 / 逻辑半径。多出来的一圈供片元画 halo。 */
    static final float HALO_RATIO = 1.28f;

    /** 从施法高度向上最多找几格地表（允许领域放在比自己高的台地上）。 */
    private static final int SCAN_UP = 6;
    /** 从施法高度向下最多找几格地表。超过这个深度的落差视为"没地面"。 */
    private static final int SCAN_DEPTH = 24;
    /** 找到地表后抬高的量，避免与地面共面产生 z-fighting。 */
    private static final float SURFACE_LIFT = 0.02f;

    /** 与 DomeriteLongsword 视觉语言一致的两个色值。 */
    private static final float[] FIELD_COLOR = { 0.529f, 0.808f, 0.980f };   // 0x87CEFA
    private static final float[] CORE_COLOR  = { 0.941f, 0.973f, 1.000f };   // 0xF0F8FF

    public VerdictFieldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(VerdictFieldEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
    }

    /** 画一个领域；{@code poseStack} 已是 translate 过 {@code -camera} 的坐标系。 */
    public static void renderOne(VerdictFieldEntity entity, float partialTick, PoseStack poseStack) {
        ShaderInstance shader = SplendidingShaders.verdictFieldShader;
        if (shader == null) { noteOnce("shader", "shader=null（着色器没加载，领域不可见）"); return; }

        float intensity = entity.intensity(partialTick);
        if (intensity <= 0.004f) { noteOnce("intensity", "intensity≈0：已是生命周期末尾"); return; }

        float radius = entity.getRadius();
        float meshRadius = radius * HALO_RATIO;
        if (radius <= 0.1f) { noteOnce("radius", "radius=" + radius + "（半径过小，跳过）"); return; }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) { noteOnce("level", "level=null"); return; }

        float life = entity.lifeProgress(partialTick);
        // 时间基准带上 entity id 低位：两个领域同时存在时呼吸相位不同步（同步会读成"特效复制了两份"）。
        float time = (entity.tickCount + partialTick) * 0.05f + (entity.getId() % 17) * 0.37f;
        int interval = Math.max(1, entity.swordInterval());
        float pulsePhase = (entity.getAge() + partialTick) % interval / interval;
        float ownerDim = entity.isOwnerPresent() ? 1.0f : 0.45f;

        // 地表高度只在中心采一次，整块圆盘统一落在这一层（逐列贴地会变成起皱的薄膜，已按反馈去掉）。
        float yOff = groundOffset(level, entity) + SURFACE_LIFT;

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        noteOnce("field", String.format(
                "entity=(%.1f,%.1f,%.1f) cam=(%.1f,%.1f,%.1f) yOff=%.2f radius=%.1f intensity=%.2f"
                        + " → 圆盘平面 y=%.2f，x[%.1f..%.1f] z[%.1f..%.1f]",
                entity.getX(), entity.getY(), entity.getZ(),
                cam.x, cam.y, cam.z, yOff, radius, intensity,
                entity.getY() + yOff,
                entity.getX() - meshRadius, entity.getX() + meshRadius,
                entity.getZ() - meshRadius, entity.getZ() + meshRadius));

        // 光影兼容：pack 激活时世界 pass 画的是 GBuffer，自发光光纹会被当成地面材质重新参与光照与雾，
        // 颜色亮度失控 —— 入队，等 pack 合成完最终场景后在 MAIN_TARGET 上回放。
        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            float groundY = (float) (entity.getY() + yOff);
            // 投影矩阵、相机矩阵、相机位置全部<b>此刻</b>快照（回放发生在下一帧更晚阶段）。
            VerdictFieldLateRenderQueue.enqueue(
                    RenderSystem.getProjectionMatrix(),
                    RenderSystem.getModelViewMatrix(),
                    cam.x, cam.y, cam.z,
                    (float) entity.getX(), groundY, (float) entity.getZ(),
                    radius, meshRadius,
                    time, intensity * ownerDim, pulsePhase, life,
                    entity.isOwnerPresent() ? 1f : 0f);
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);

        poseStack.pushPose();

        // 坐标系契约：顶点喂**世界坐标**，矩阵完成"世界 → 相机相对"的变换（顶点着色器只需 ProjMat * Position）。
        // ⚠️ 别自己算"相机相对坐标"，也别再 translate(-cam)：前者会被当世界坐标投影（永远在世界原点），
        // 后者变双重平移（彻底看不见）；这个契约唯一可靠的验证方式是看画面。

        setUniform(shader, "uTime", time);
        setUniform(shader, "uIntensity", intensity * ownerDim);
        setUniform(shader, "uRadius", radius);
        setUniform(shader, "uMeshRadius", meshRadius);
        setUniform(shader, "uHalo", HALO_RATIO);
        setUniform(shader, "uPulse", pulsePhase);
        setUniform(shader, "uProgress", life);
        // 施法者在不在场：离场时阵法熄火，Java 侧同时停止出剑，两边必须一致。
        setUniform(shader, "uOwnerPresent", entity.isOwnerPresent() ? 1f : 0f);
        setUniform3(shader, "uFieldColor", FIELD_COLOR);
        setUniform3(shader, "uCoreColor", CORE_COLOR);

        Matrix4f mat = poseStack.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        emitDisc(buffer, mat,
                (float) entity.getX(), (float) (entity.getY() + yOff), (float) entity.getZ(),
                meshRadius);
        // 用 drawWithShader 而不是 Tesselator.end()：明确按当前绑定的着色器把顶点直送 GPU。
        BufferUploader.drawWithShader(buffer.end());

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** 延后回放入口（光影包激活时由 {@link VerdictFieldLateRenderQueue#renderAll()} 调用）：与立即绘制共用同一份
     *  uniform 与几何，几何仍喂<b>世界坐标</b>，矩阵由调用方保证是"相机旋转 × translate(−camera)"。 */
    static void drawDeferred(ShaderInstance shader, Matrix4f matrix,
                             float worldX, float worldY, float worldZ,
                             float radius, float meshRadius,
                             float time, float intensity, float pulsePhase,
                             float life, float ownerPresent) {
        setUniform(shader, "uTime", time);
        setUniform(shader, "uIntensity", intensity);
        setUniform(shader, "uRadius", radius);
        setUniform(shader, "uMeshRadius", meshRadius);
        setUniform(shader, "uHalo", HALO_RATIO);
        setUniform(shader, "uPulse", pulsePhase);
        setUniform(shader, "uProgress", life);
        setUniform(shader, "uOwnerPresent", ownerPresent);
        setUniform3(shader, "uFieldColor", FIELD_COLOR);
        setUniform3(shader, "uCoreColor", CORE_COLOR);

        RenderSystem.setShader(() -> shader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        emitDisc(buffer, matrix, worldX, worldY, worldZ, meshRadius);
        BufferUploader.drawWithShader(buffer.end());
    }

    /** 求领域地表高度（相对实体 y 的偏移）：只在<b>中心</b>采一次、整盘同高 —— 与判定形状（平面圆柱）一致；
     *  逐点贴地会让圆盘跨台阶时"上下补丁"成起皱薄膜。先向下找，脚下是空再向上（台地边缘），都不行就用 0。 */
    private static float groundOffset(Level level, VerdictFieldEntity entity) {
        double baseY = entity.getY();
        int baseYFloor = Mth.floor(baseY);
        int bx = Mth.floor(entity.getX());
        int bz = Mth.floor(entity.getZ());

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        float down = scanColumn(level, pos, bx, bz, baseYFloor, baseYFloor - SCAN_DEPTH, -1, baseY);
        if (!Float.isNaN(down)) return down;
        float up = scanColumn(level, pos, bx, bz, baseYFloor + 1, baseYFloor + SCAN_UP, 1, baseY);
        if (!Float.isNaN(up)) return up;
        return 0f;
    }

    /** 沿一列扫描，返回地表相对实体 y 的偏移；没找到返回 {@link Float#NaN}。{@code step}：+1 向上 / -1 向下。 */
    private static float scanColumn(Level level, BlockPos.MutableBlockPos pos,
                                    int bx, int bz, int fromY, int toY, int step, double baseY) {
        for (int by = fromY; step > 0 ? by <= toY : by >= toY; by += step) {
            pos.set(bx, by, bz);
            if (!level.hasChunkAt(pos)) return Float.NaN;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            // 取碰撞盒顶面：台阶/栅栏的顶面与整格不同，空形状（草、火把）已被上面跳过。
            return (float) (by + shape.max(Direction.Axis.Y) - baseY);
        }
        return Float.NaN;
    }

    /** 发出水平圆盘的三角扇。顶点属性 {@code Color.rg} 与 {@code Normal.xy} 都是 (径向, 角度) 归一化极坐标。 */
    private static void emitDisc(BufferBuilder buffer, Matrix4f mat,
                                 float cx, float cy, float cz, float meshRadius) {
        for (int ring = 0; ring < RINGS; ring++) {
            float r0 = ring / (float) RINGS;
            float r1 = (ring + 1) / (float) RINGS;

            for (int seg = 0; seg < SEGMENTS; seg++) {
                int s0 = seg;
                int s1 = (seg + 1) % SEGMENTS;
                float a0 = seg / (float) SEGMENTS;
                float a1 = (seg + 1) / (float) SEGMENTS;

                discVertex(buffer, mat, cx, cy, cz, r0, a0, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r1, a0, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r1, a1, meshRadius);

                discVertex(buffer, mat, cx, cy, cz, r0, a0, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r1, a1, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r0, a1, meshRadius);
            }
        }
    }

    /** 发一个圆盘顶点：<b>发的是世界坐标</b>，矩阵负责世界 → 相机相对的变换（着色器只需 ProjMat * Position）。 */
    private static void discVertex(BufferBuilder buffer, Matrix4f mat,
                                   float cx, float cy, float cz,
                                   float rNorm, float aNorm,
                                   float meshRadius) {
        float angle = aNorm * TWO_PI;
        float x = cx + Mth.cos(angle) * rNorm * meshRadius;
        float z = cz + Mth.sin(angle) * rNorm * meshRadius;

        buffer.vertex(mat, x, cy, z)
                .color(rNorm, aNorm, 0f, 1f)
                .normal(rNorm, aNorm, 0f)
                .endVertex();
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        var u = shader.getUniform(name);
        if (u != null) u.set(value);
    }

    private static void setUniform3(ShaderInstance shader, String name, float[] rgb) {
        var u = shader.getUniform(name);
        if (u != null) u.set(rgb[0], rgb[1], rgb[2]);
    }

    /** 限频诊断：同一 key 每 {@value #NOTE_INTERVAL_MS} 毫秒最多写一行（按内容去重不行 —— 相机每帧都在动）。 */
    private static final java.util.Map<String, Long> NOTE_LAST = new java.util.HashMap<>();
    private static final long NOTE_INTERVAL_MS = 500L;

    private static void noteOnce(String key, String what) {
        long now = System.currentTimeMillis();
        Long last = NOTE_LAST.get(key);
        if (last != null && now - last < NOTE_INTERVAL_MS) return;
        NOTE_LAST.put(key, now);
        VerdictDebug.log("FieldRenderer[%s] %s", key, what);
    }

    @Override
    public ResourceLocation getTextureLocation(VerdictFieldEntity entity) {
        return DUMMY_TEXTURE;
    }
}
