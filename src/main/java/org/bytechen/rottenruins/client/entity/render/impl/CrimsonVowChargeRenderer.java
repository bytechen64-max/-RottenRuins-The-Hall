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

/**
 * 蓄力法阵渲染器 —— 玩家身下那一圈 "正在往外推" 的法阵。
 *
 * <h3>为什么实体渲染阶段什么都不做</h3>
 * <p>与裁决领域同一个理由：贴地的东西如果在 {@code EntityRenderer.render}
 * 里画，它会在地形与实体<b>之前</b>落进深度缓冲，于是前方的方块和生物
 * 完全挡不住它。真正的绘制由 {@link CrimsonVowFieldRenderHandler} 在
 * {@code RenderLevelStageEvent.AFTER_TRIPWIRE_BLOCKS} 阶段执行。</p>
 *
 * <h3>为什么先做一份"快照"再画</h3>
 * <p>同步数据只有 20Hz，直接拿来画的话法阵会以每秒 20 次的台阶向外跳。
 * 所以先把这一帧要用的全部量（含 {@code partialTick} 插值之后的蓄力进度
 * 与半径）算进 {@link Snapshot}，再交给绘制函数。这样：</p>
 * <ul>
 *   <li>立即绘制与光影包下的延后回放用的是同一份数据形状 ——
 *       回放发生在下一帧，那时实体可能已经散掉了，只有快照可信；</li>
 *   <li>插值只做一次，不会出现"半径用了新值、亮度还用着旧值"。</li>
 * </ul>
 */
public class CrimsonVowChargeRenderer extends EntityRenderer<CrimsonVowChargeEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 立柱最高到几格（满蓄时）。 */
    private static final float BEAM_MAX_HEIGHT = 6.5f;
    /** 立柱半径 / 法阵半径。 */
    private static final float BEAM_RADIUS_RATIO = 0.42f;

    public CrimsonVowChargeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /** 实体渲染阶段不做任何事（见类注释）。 */
    @Override
    public void render(CrimsonVowChargeEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // no-op
    }

    // ══════════════════════════════════════════════════════════════
    //  快照
    // ══════════════════════════════════════════════════════════════

    /** 一帧里这个蓄力法阵要用到的全部量，已经做过 partialTick 插值。 */
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
        // 位置也要插值：法阵每 tick 跟着玩家走，直接用 getX() 会让它在
        // 快速移动（疾跑 / 冲刺）时以 20Hz 一跳一跳地跟，而不是黏在脚下。
        float pt = Mth.clamp(partialTick, 0f, 1f);
        double x = Mth.lerp(pt, entity.xOld, entity.getX());
        double y = Mth.lerp(pt, entity.yOld, entity.getY());
        double z = Mth.lerp(pt, entity.zOld, entity.getZ());
        return new Snapshot(x, y, z, radius, charge, intensity, time,
                intensity > 0.004f && radius > 0.05f);
    }

    // ══════════════════════════════════════════════════════════════
    //  绘制
    // ══════════════════════════════════════════════════════════════

    /**
     * 真正绘制。由 {@link CrimsonVowFieldRenderHandler} 在
     * {@code AFTER_TRIPWIRE_BLOCKS} 调用。
     *
     * @param mat 视图矩阵（世界 → 视空间），已含 {@code translate(-camera)}
     */
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

        // ── ① 地面法阵 ──
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
        // ⚠️ 圆盘不接受柱面轮廓波动，必须显式清零：同一个 program 的 uniform 是
        // 全局状态，场上另一张巨型法阵的光柱那一趟会把 uBeamWobble 设成 0.05~0.26。
        // 不清零的话，这个圆盘会按"顶点径向坐标"被震波推皱（老 bug，别再踩）。
        CrimsonVowRenderSupport.setUniform(shader, "uBeamWobble", 0f);
        CrimsonVowRenderSupport.setUniform(shader, "uBeamRadius", 0f);
        CrimsonVowRenderSupport.setPalette(shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        CrimsonVowRenderSupport.emitDisc(buffer, mat, (float) s.x, groundY, (float) s.z, meshRadius);
        BufferUploader.drawWithShader(buffer.end());

        // ── ② 从阵心升起的立柱（满蓄时最亮）──
        //  它的作用是"把力量正在往上收"这件事画出来；没有它，蓄力只是
        //  地上一个越画越大的圈，读不出"快要放了"。
        //
        //  它走的是和终末光柱**同一个模式**（uMode = 3）：蓄力时玩家看到的是
        //  缩小版的终末那根柱子 —— 一眼就能把"我现在攒的东西"和"等一下会落下
        //  什么"对上，比另开一套花纹更省事也更清楚。
        //
        //  ⚠️ 1.8 倍：终末光柱的亮度是按"三层叠加"标的（光晕 + 主体 + 白热核），
        //  而这里的立柱只画了主体一层（uLayer = 0）。不补这个系数，蓄力立柱会比
        //  改版前暗一半 —— 那正是"快放了"这个提示最不该消失的时候。
        float beamI = s.intensity * s.charge * s.charge * 1.8f;
        if (beamI > 0.01f) {
            float beamR = Math.max(0.25f, s.radius * BEAM_RADIUS_RATIO);
            float beamH = 1.0f + BEAM_MAX_HEIGHT * s.charge;
            CrimsonVowRenderSupport.setUniform(shader, "uIntensity", beamI);
            CrimsonVowRenderSupport.setUniform(shader, "uMode", 3f);
            CrimsonVowRenderSupport.setUniform(shader, "uProgress", s.charge);
            CrimsonVowRenderSupport.setUniform(shader, "uFinale", 0f);
            // 光柱模式的专属 uniform（语义见 rendertype_crimson_sigil.fsh 的 ③）：
            //  · uBeamHead/uBeamSpan 是**世界高度**上的前缘与可见长度。蓄力立柱的
            //    前缘就在地上，于是 head 光斑落在柱底，正好是"力量正在往上收"的位置；
            //  · uBeamBase 是**地面世界 Y**（不是 0）。旧版写 0，而地面通常在 64 附近，
            //    片元里的爆闪局部坐标会飞出 200 多倍，那一下就永远不会亮。
            //  · uWarn/uImpactAge 与终末无关，显式归零/置 -1，不带上一次绘制的残值。
            CrimsonVowRenderSupport.setUniform(shader, "uImpact", 0f);
            CrimsonVowRenderSupport.setUniform(shader, "uWarn", 0f);
            CrimsonVowRenderSupport.setUniform(shader, "uImpactAge", -1f);
            // 柱轴（视空间）：片元靠它反解世界高度、算"视线到柱轴的距离"做体积感。
            // 少了这两个 uniform，蓄力柱在实机里会退回"均匀白管"（见 beamSigil 的注释）。
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
                // ⚠ emitCylinder 的后两个参数是 (topY, rings)，**都是绝对世界高度**：
                //  底是 groundY、顶是 groundY + beamH。把 beamH 当 topY 传的话，
                //  柱子会从地面一路插到 y≈7 —— 一根穿透世界的倒挂圆柱。
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
