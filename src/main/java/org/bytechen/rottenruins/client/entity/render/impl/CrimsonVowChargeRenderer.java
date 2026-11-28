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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.skills.CrimsonVowChargeEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** 蓄力法阵渲染器：实体渲染阶段什么都不画，绘制由 {@link CrimsonVowFieldRenderHandler} 在 {@code AFTER_TRIPWIRE_BLOCKS} 执行。 */
public class CrimsonVowChargeRenderer extends EntityRenderer<CrimsonVowChargeEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 立柱最高到几格（满蓄时）。 */
    private static final float BEAM_MAX_HEIGHT = 6.5f;
    private static final float BEAM_RADIUS_RATIO = 0.42f;

    public CrimsonVowChargeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /** 实体渲染阶段不做任何事：贴地物会在地形与实体之前落进深度缓冲，前方方块挡不住它。 */
    @Override
    public void render(CrimsonVowChargeEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
    }

    /** 一帧里这个蓄力法阵要用到的全部量，已做 partialTick 插值（同步只有 20Hz，直接画会一跳一跳）。 */
    static final class Snapshot {
        final double x, y, z;
        final float radius, charge, intensity, time;
        final boolean valid;

        Snapshot(double x, double y, double z,
                 float radius, float charge, float intensity, float time, boolean valid) {
            this.x = x; this.y = y; this.z = z;
            this.radius = radius; this.charge = charge;
            this.intensity = intensity; this.time = time; this.valid = valid;
        }
    }

    /** 从实体算出这一帧的快照；不可见时 {@code valid = false}。 */
    static Snapshot snapshotOf(CrimsonVowChargeEntity entity, float partialTick, Level level) {
        int age = entity.getAge();
        // 短淡入：起阵那一瞬间不该是"啪"地出现
        float intensity = Mth.clamp((age + partialTick) / 4.0f, 0f, 1f);
        float charge = entity.charge(partialTick);
        float radius = entity.radius(partialTick);
        float time = (age + partialTick) * 0.05f + (entity.getId() % 13) * 0.37f;
        // 位置也要插值，否则疾跑时法阵按 20Hz 一跳一跳地跟，而不是黏在脚下。
        float pt = Mth.clamp(partialTick, 0f, 1f);
        double x = Mth.lerp(pt, entity.xOld, entity.getX());
        double y = Mth.lerp(pt, entity.yOld, entity.getY());
        double z = Mth.lerp(pt, entity.zOld, entity.getZ());
        return new Snapshot(x, y, z, radius, charge, intensity, time,
                intensity > 0.004f && radius > 0.05f);
    }

    /** 真正绘制。{@code mat} 是视图矩阵（世界 → 视空间），已含 {@code translate(-camera)}。 */
    static void draw(Snapshot s, Matrix4f mat, Level level) {
        if (!s.valid) return;
        ShaderInstance shader = SplendidingShaders.crimsonSigilShader;
        if (shader == null) return;

        float groundY = CrimsonVowRenderSupport.groundYAt(level, s.x, s.y, s.z) + 0.03f;
        float meshRadius = s.radius * CrimsonVowRenderSupport.HALO_RATIO;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> shader);

        CrimsonVowRenderSupport.setUniform(shader, "uTime", s.time);
        CrimsonVowRenderSupport.setUniform(shader, "uIntensity", s.intensity);
        CrimsonVowRenderSupport.setUniform(shader, "uMode", 0f);
        CrimsonVowRenderSupport.setUniform(shader, "uRadius", s.radius);
        CrimsonVowRenderSupport.setUniform(shader, "uMeshRadius", meshRadius);
        CrimsonVowRenderSupport.setUniform(shader, "uCharge", s.charge);
        CrimsonVowRenderSupport.setUniform(shader, "uPulse", 0f);
        CrimsonVowRenderSupport.setUniform(shader, "uProgress", 0f);
        CrimsonVowRenderSupport.setUniform(shader, "uFinale", 0f);
        CrimsonVowRenderSupport.setUniform(shader, "uSeed", 0f);
        // 必须显式清零：uniform 是 program 级全局状态，别处光柱会把它设成 0.05~0.26，本圆盘会被震波推皱。
        CrimsonVowRenderSupport.setUniform(shader, "uBeamWobble", 0f);
        CrimsonVowRenderSupport.setUniform(shader, "uBeamRadius", 0f);
        CrimsonVowRenderSupport.setPalette(shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitDisc(buffer, mat, (float) s.x, groundY, (float) s.z, meshRadius);
        BufferUploader.drawWithShader(buffer.end());

        // 立柱走与终末光柱同一模式（uMode = 3），蓄力时看到的就是缩小版终末柱。
        // 1.8 倍：终末柱亮度按三层叠加标的，这里只画主体一层（uLayer = 0），不补会暗一半。
        float beamI = s.intensity * s.charge * s.charge * 1.8f;
        if (beamI > 0.01f) {
            float beamR = Math.max(0.25f, s.radius * BEAM_RADIUS_RATIO);
            float beamH = 1.0f + BEAM_MAX_HEIGHT * s.charge;
            CrimsonVowRenderSupport.setUniform(shader, "uIntensity", beamI);
            CrimsonVowRenderSupport.setUniform(shader, "uMode", 3f);
            CrimsonVowRenderSupport.setUniform(shader, "uProgress", s.charge);
            CrimsonVowRenderSupport.setUniform(shader, "uFinale", 0f);
            // uBeamHead/uBeamSpan 是世界高度上的前缘与可见长度；uBeamBase 必须是地面世界 Y（写 0 会让片元爆闪坐标飞出 200 倍）。
            CrimsonVowRenderSupport.setUniform(shader, "uImpact", 0f);
            CrimsonVowRenderSupport.setUniform(shader, "uWarn", 0f);
            CrimsonVowRenderSupport.setUniform(shader, "uImpactAge", -1f);
            // 柱轴（视空间）：片元靠它反解世界高度、算视线到柱轴距离做体积感，缺了会退回"均匀白管"。
            Vector4f axisV = mat.transform(new Vector4f((float) s.x, groundY, (float) s.z, 1.0f));
            Vector3f axisDir = mat.transformDirection(new Vector3f(0.0f, 1.0f, 0.0f));
            CrimsonVowRenderSupport.setUniform3(shader, "uBeamAxisVS", axisV.x, axisV.y, axisV.z);
            CrimsonVowRenderSupport.setUniform3(shader, "uBeamAxisDir", axisDir.x, axisDir.y, axisDir.z);
            CrimsonVowRenderSupport.setUniform(shader, "uBeamHead", groundY);
            CrimsonVowRenderSupport.setUniform(shader, "uBeamSpan", beamH);
            CrimsonVowRenderSupport.setUniform(shader, "uBeamBase", groundY);
            CrimsonVowRenderSupport.setUniform(shader, "uBeamWobble", 0f);
            CrimsonVowRenderSupport.setUniform(shader, "uImpact", 0f);
            CrimsonVowRenderSupport.setUniform(shader, "uWarn", 0f);
            CrimsonVowRenderSupport.setUniform(shader, "uImpactAge", -1f);
            CrimsonVowRenderSupport.setUniform(shader, "uFinale", 0f);

            // 三层：外光晕（4）/ 主体（0）/ 白热核（1）—— 与终末光柱同一套语言。
            float[] cRadii = { beamR * 1.63f, beamR, Math.max(0.10f, beamR * 0.30f) };
            float[] cLayers = { 4f, 0f, 1f };
            float[] cScales = { 0.55f, 1.00f, 1.00f };
            for (int li = 0; li < 3; li++) {
                float lr = cRadii[li];
                CrimsonVowRenderSupport.setUniform(shader, "uLayer", cLayers[li]);
                CrimsonVowRenderSupport.setUniform(shader, "uRadius", lr);
                CrimsonVowRenderSupport.setUniform(shader, "uMeshRadius", lr);
                CrimsonVowRenderSupport.setUniform(shader, "uBeamRadius", lr);
                CrimsonVowRenderSupport.setUniform(shader, "uIntensity", beamI * cScales[li]);
                buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                // emitCylinder 后两个参数是 (topY, rings)，都是绝对世界高度：底 groundY、顶 groundY + beamH。
                CrimsonVowRenderSupport.emitCylinder(buffer, mat,
                        (float) s.x, groundY, (float) s.z, lr, lr, groundY + beamH, 6);
                BufferUploader.drawWithShader(buffer.end());
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** 便捷入口：直接从实体画（内部做快照）。 */
    static void draw(CrimsonVowChargeEntity entity, float partialTick, Matrix4f mat) {
        draw(snapshotOf(entity, partialTick, entity.level()), mat,
                Minecraft.getInstance().level);
    }

    @Override
    public ResourceLocation getTextureLocation(CrimsonVowChargeEntity entity) {
        return DUMMY_TEXTURE;
    }
}
