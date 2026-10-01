package org.bytechen.hall.client.entity.render.impl;

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
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.overworld.registry.entities.population.skills.VerdictFieldEntity;
import org.bytechen.hall.overworld.registry.items.verdict.VerdictDebug;
import org.joml.Matrix4f;

/**
 * 裁决领域渲染器 —— 贴地的地面光纹圆盘。
 *
 * <h3>为什么它不在实体渲染阶段画</h3>
 * <p>实体渲染阶段（{@code EntityRenderer.render}）画出来的东西，
 * 深度测试的对手是"当前已经写进深度缓冲的内容"。地面光纹贴在地面上，
 * 而<b>实体和块模型在它之后才画</b>，所以它会浮在所有东西前面 ——
 * 表现就是"穿透实体和方块"。这与 {@code ShockwaveRenderHandler} 遇到的是
 * 同一类问题（那里是"场景拷贝必须包含所有实体"）。</p>
 *
 * <p>所以：实体渲染器<b>什么都不做</b>，真正的绘制交给
 * {@link VerdictFieldRenderHandler} 在 {@code RenderLevelStageEvent} 的
 * {@code AFTER_TRIPWIRE_BLOCKS} 阶段执行 —— 那时地形与实体都已经写好了深度，
 * 光纹自然会被前方的方块和生物正确遮挡。这就是最初那个深度问题的解法。</p>
 *
 * <h3>几何是一个平面圆盘</h3>
 * <p>领域实体记录"施法那一刻玩家脚下的 y"，渲染时只在<b>领域中心采一次</b>
 * 地表高度，整块圆盘统一落在那一层（见 {@link #groundOffset}）。
 * 圆盘因此是一个干净的平面 —— 与领域的判定形状（平面圆柱）一致，边缘一眼可辨。</p>
 *
 * <p>这里试过"逐网格点各自向下扫、让圆盘贴着地形起伏"，实战里不好看：
 * 跨过台阶或沟槽时圆盘会上下打补丁，变成一层起皱的薄膜，反而看不清领域范围。
 * 按反馈去掉了。</p>
 *
 * <h3>坐标系</h3>
 * <p>顶点喂<b>世界坐标</b>，由 poseStack 的矩阵完成"世界 → 相机相对"的变换，
 * 顶点着色器因此只需 {@code ProjMat * Position}。这与
 * {@link CollapsarHaloRenderer}（项目里已验证可用的世界空间特效）完全一致。
 * 详见 {@link #discVertex} 里那段踩坑记录。</p>
 */
public class VerdictFieldRenderer extends EntityRenderer<VerdictFieldEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 圆盘分段数（同时是辐条的角分辨率上限）。 */
    static final int SEGMENTS = 96;
    /** 圆盘同心环数。 */
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

    /** 实体渲染阶段不做任何事 —— 真正的绘制在 {@link VerdictFieldRenderHandler}。 */
    @Override
    public void render(VerdictFieldEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // no-op
    }

    // ══════════════════════════════════════════════════════════════
    //  真正绘制（由 RenderLevelStageEvent 调用）
    // ══════════════════════════════════════════════════════════════

    /**
     * 画一个领域。
     *
     * @param poseStack 已经 translate 过 {@code -camera} 的坐标系（与其它实体一致）
     */
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
        float time = (entity.tickCount + partialTick) * 0.05f;
        int interval = Math.max(1, entity.swordInterval());
        float pulsePhase = (entity.getAge() + partialTick) % interval / interval;
        float ownerDim = entity.isOwnerPresent() ? 1.0f : 0.45f;

        // 地表高度：只在中心采一次，整块圆盘统一落在这一层（见 groundOffset 的说明）。
        // 长度/上下补丁那套逐列贴地在实战里不好看，已按反馈去掉。
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

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);

        poseStack.pushPose();

        // ── 坐标系契约（本渲染器与调用方的分工）──
        //
        //  顶点喂**世界坐标**，由 poseStack 的矩阵完成"世界 → 相机相对"的变换，
        //  顶点着色器因此只需 `ProjMat * Position`。
        //  这与 CollapsarHaloRenderer（项目里已验证可用的世界空间特效）完全一致。
        //
        //  ⚠️ 这里不要自己算"相机相对坐标"，也不要再 translate(-cam)：
        //   · 自己算 → 几何被当成世界坐标投影，症状是"不管在哪放都出现在世界原点"；
        //   · 再平移一次 → 双重平移，几何被推到远处，症状是"彻底看不见"。
        //  这两个错误我都实际犯过。
        //
        //  （曾在这里放过一个"矩阵平移分量 vs -cam"的断言探针，已删除：
        //    poseStack 含相机旋转，旋转与平移会混合，那个比较方式本身不成立，
        //    只会持续误报。这个契约唯一可靠的验证方式是看画面。）

        // ── ① 地面光纹 ──
        setUniform(shader, "uTime", time);
        setUniform(shader, "uIntensity", intensity * ownerDim);
        setUniform(shader, "uRadius", radius);
        setUniform(shader, "uMeshRadius", meshRadius);
        setUniform(shader, "uHalo", HALO_RATIO);
        setUniform(shader, "uPulse", pulsePhase);
        setUniform(shader, "uProgress", life);
        setUniform3(shader, "uFieldColor", FIELD_COLOR);
        setUniform3(shader, "uCoreColor", CORE_COLOR);

        Matrix4f mat = poseStack.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        emitDisc(buffer, mat,
                (float) entity.getX(), (float) (entity.getY() + yOff), (float) entity.getZ(),
                meshRadius);
        // 与 CollapsarHaloRenderer 保持一致：用 drawWithShader 而不是 Tesselator.end()。
        // 前者明确按当前绑定的着色器把顶点直送 GPU，不依赖 Tesselator 内部状态。
        BufferUploader.drawWithShader(buffer.end());

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // ══════════════════════════════════════════════════════════════
    //  地形采样
    // ══════════════════════════════════════════════════════════════

    /**
     * 求领域所在的地表高度（相对实体 y 的偏移）。
     *
     * <h3>只在中心采一次</h3>
     * <p>早期版本是<b>逐网格点</b>各自向下扫描、让圆盘沿地形起伏。那在平地上看起来不错，
     * 但代价是：领域跨过台阶、沟槽或悬崖时，圆盘会跟着地形"上下补丁"——
     * 有方块的地方补上去、缺方块的地方掉下去，变成一层贴着地形的起皱薄膜，
     * 既不好看也让人看不出领域的实际范围。</p>
     *
     * <p>而领域的判定本来就是<b>一个平面圆柱</b>（半径 + 高度），并不是贴着地形的曲面。
     * 所以这里只在<b>中心</b>采一次地面高度，整块圆盘统一落在那个高度上：
     * 圆盘是一个干净的平面，与判定形状一致，边缘也一眼看得出来。</p>
     *
     * <p>先向下找（站姿绝大多数情况地面就在脚下），脚下是空的再向上找
     * （站在台地边缘时背后可能有平台）。都找不到就用实体自己那一层。</p>
     */
    private static float groundOffset(Level level, VerdictFieldEntity entity) {
        double baseY = entity.getY();
        int baseYFloor = Mth.floor(baseY);
        int bx = Mth.floor(entity.getX());
        int bz = Mth.floor(entity.getZ());

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 先向下
        float down = scanColumn(level, pos, bx, bz, baseYFloor, baseYFloor - SCAN_DEPTH, -1, baseY);
        if (!Float.isNaN(down)) return down;
        // 脚下没有才向上
        float up = scanColumn(level, pos, bx, bz, baseYFloor + 1, baseYFloor + SCAN_UP, 1, baseY);
        if (!Float.isNaN(up)) return up;
        // 都没有：用实体自己那一层（保证领域永远可见）
        return 0f;
    }

    /**
     * 沿一列扫描，返回地表相对实体 y 的偏移；没找到返回 {@link Float#NaN}。
     *
     * @param fromY 起始方块 y
     * @param toY   终止方块 y
     * @param step  +1 向上 / -1 向下
     */
    private static float scanColumn(Level level, BlockPos.MutableBlockPos pos,
                                    int bx, int bz, int fromY, int toY, int step, double baseY) {
        for (int by = fromY; step > 0 ? by <= toY : by >= toY; by += step) {
            pos.set(bx, by, bz);
            if (!level.hasChunkAt(pos)) return Float.NaN;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            // 取碰撞盒顶面 —— 台阶/栅栏的顶面与整格不同，只有用碰撞盒高度才贴得准。
            // 空形状（草、火把）已被上面跳过。
            return (float) (by + shape.max(Direction.Axis.Y) - baseY);
        }
        return Float.NaN;
    }

    // ══════════════════════════════════════════════════════════════
    //  地面圆盘
    // ══════════════════════════════════════════════════════════════

    /**
     * 发出水平圆盘的三角扇。整块圆盘都在同一个高度（领域中心采样到的地表）。
     *
     * <p>属性打包：{@code Color.rg} = (径向归一化, 角度归一化)，
     * {@code Normal.xy} = 同上极坐标。片元只需要 r/g，Normal 留着是为了
     * 将来要按径向做方向性东西时不用改顶点格式。</p>
     */
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

                // 三角 1：内环 a0 → 外环 a0 → 外环 a1
                discVertex(buffer, mat, cx, cy, cz, r0, a0, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r1, a0, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r1, a1, meshRadius);

                // 三角 2：内环 a0 → 外环 a1 → 内环 a1
                discVertex(buffer, mat, cx, cy, cz, r0, a0, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r1, a1, meshRadius);
                discVertex(buffer, mat, cx, cy, cz, r0, a1, meshRadius);
            }
        }
    }

    /**
     * 发一个圆盘顶点。
     *
     * <p><b>发的是世界坐标</b>，不是"相机相对"坐标 —— 这是整件事的关键。
     * 对照 {@code CollapsarHaloRenderer}（项目里已验证可用的世界空间特效）：
     * 它在 handler 里把 {@code -camera} 平移进 pose，拿到那个矩阵后，
     * 顶点直接喂<b>世界坐标</b>，由矩阵完成"世界 → 相机相对"的变换，
     * 顶点着色器因此只需 {@code ProjMat * Position}。</p>
     *
     * <p>我最初写反了：自己先算出相机相对的局部坐标，再指望矩阵里没有相机平移 ——
     * 于是几何被当成世界坐标投影，表现就是"不管在哪放，都出现在世界原点"；
     * 后来我又加了一次平移去"纠正"，结果叠加成双重平移，几何被推到远处、彻底看不见。</p>
     */
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

    // ══════════════════════════════════════════════════════════════
    //  uniform 工具
    // ══════════════════════════════════════════════════════════════

    private static void setUniform(ShaderInstance shader, String name, float value) {
        var u = shader.getUniform(name);
        if (u != null) u.set(value);
    }

    private static void setUniform3(ShaderInstance shader, String name, float[] rgb) {
        var u = shader.getUniform(name);
        if (u != null) u.set(rgb[0], rgb[1], rgb[2]);
    }

    // ══════════════════════════════════════════════════════════════
    //  诊断
    // ══════════════════════════════════════════════════════════════

    /**
     * 限频诊断：<b>同一 key 每 {@value #NOTE_INTERVAL_MS} 毫秒最多写一行</b>。
     *
     * <p>这里踩过一次坑：按"内容变化"去重看着很聪明，但相机每帧都在动
     * （{@code camY} 一直在变），于是每一帧的字符串都不同、每一帧都写一行 ——
     * 一秒几十行、把日志刷成几万行。所以正确的维度是<b>时间</b>，不是内容。</p>
     *
     * <p>500ms 的间隔足以看清"放领域时到底发生了什么"，又不会污染日志。</p>
     */
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
