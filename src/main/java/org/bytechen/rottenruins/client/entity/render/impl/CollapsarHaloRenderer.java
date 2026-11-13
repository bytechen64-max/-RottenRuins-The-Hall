package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.rottenruins.overworld.registry.entities.population.hall.apostle.CollapsarEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * 坍缩使徒的背部六芒星光环：朝相机的 billboard，沿视线反方向后退 {@link #BACK_OFFSET} 格，让本体挡住光环中心；
 * 位置 / 时间 / 血量全部按 {@code partialTick} 插值，否则会比本体"慢半拍"；Oculus 激活时快照入队，等 renderLevel TAIL 回放。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CollapsarHaloRenderer {

    /** 六芒星尖角半径（格）—— 光环直径是这个值 ×2。 */
    private static final double HALO_RADIUS = 2.8D;
    /** 沿视线反方向后退的距离（格）：让本体挡住光环中心。 */
    private static final double BACK_OFFSET = 1.15D;
    /** 光环中心在本体高度上的位置（0 = 脚底，1 = 头顶）。 */
    private static final double CENTER_HEIGHT = 0.55D;
    /** 满血时的基础不透明度。 */
    private static final float BASE_ALPHA = 0.80F;
    /** 一次性诊断开关（见 renderOne 里的说明）。 */
    private static boolean loggedFirstHalo = false;
    private static boolean loggedFirstGl = false;
    private static boolean loggedNoEntity = false;
    private static boolean loggedNoShader = false;
    /** 空心六芒星边框厚度（uv 空间，尖角半径 = 1.0）：0.085 ⇒ 边框总厚 0.17，约光环直径的 8.5%。 */
    private static final float BORDER_THICKNESS = 0.085F;
    /** 六芒星内部着色器内容的基础强度（濒死时会自己变亮）。 */
    private static final float CONTENT_STRENGTH = 1.25F;

    private CollapsarHaloRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 与冲击波/黑洞/斩击扭曲同一个阶段：此时实体已经画完，深度也就位了
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;

        ShaderInstance shader = SplendidingShaders.collapsarHaloShader;
        if (shader == null) {
            // 诊断：只出现这一行而没有「首帧」日志 ⇒ 着色器没注册成功（注册异常在 SplendidingShaders 里已打成堆栈）。
            if (!loggedNoShader) {
                loggedNoShader = true;
                HallMod.LOGGER.warn("[CollapsarHalo] collapsarHaloShader 为 null，光环不绘制");
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // 不能判 mc.options.hideGui：本光环是世界空间特效，F1 只该隐藏 HUD，不该把 boss 的辨识物一起关掉。

        float partialTick = event.getPartialTick();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);    // 世界 → 相机相对（与其它特效同约定）

        int found = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CollapsarEntity collapsar) {
                found++;
                renderOne(collapsar, partialTick, ps.last().pose(), camera);
            }
        }

        // 处理器进来了、着色器也有，但本帧一个都没找到 ⇒ entitiesForRendering() 里没有它（已死或还没同步到客户端）
        if (found == 0 && !loggedNoEntity) {
            loggedNoEntity = true;
            HallMod.LOGGER.info("[CollapsarHalo] 处理器已运行但本帧未找到 CollapsarEntity");
        }

        ps.popPose();
    }

    private static void renderOne(CollapsarEntity collapsar,
                                  float partialTick, Matrix4f pose, Vec3 camera) {
        // 位置：三轴都插值（本体也是这么画的，不插值就会"慢半拍"）
        double x = Mth.lerp(partialTick, collapsar.xo, collapsar.getX());
        double y = Mth.lerp(partialTick, collapsar.yo, collapsar.getY());
        double z = Mth.lerp(partialTick, collapsar.zo, collapsar.getZ());
        Vec3 center = new Vec3(x, y + collapsar.getBbHeight() * CENTER_HEIGHT, z);

        Vec3 toCamera = camera.subtract(center);
        if (toCamera.lengthSqr() < 1.0E-6D) return;
        toCamera = toCamera.normalize();

        Vec3 right = toCamera.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);           // 正上方/正下方看时的退化处理
        }
        right = right.normalize();
        Vec3 up = right.cross(toCamera).normalize();

        // 平面中心：沿视线反方向后退 → 本体挡住光环中心
        Vec3 plane = center.subtract(toCamera.scale(BACK_OFFSET));

        double r = HALO_RADIUS;
        Vec3 bl = plane.subtract(right.scale(r)).subtract(up.scale(r));
        Vec3 tl = plane.subtract(right.scale(r)).add(up.scale(r));
        Vec3 tr = plane.add(right.scale(r)).add(up.scale(r));
        Vec3 br = plane.add(right.scale(r)).subtract(up.scale(r));

        // 血量是离散同步的插值不了，但比例足够平滑，且同时决定转速与亮度（濒死更躁动）
        float max = collapsar.getMaxHealth();
        float health = collapsar.getHealth();
        float agitation = max <= 0.0F ? 0.0F : Mth.clamp(1.0F - health / max, 0.0F, 1.0F);

        // 时间：gameTime + partialTick ⇒ 自转与噪声逐帧连续
        float time = (collapsar.level().getGameTime() + partialTick) / 20.0F;

        // 一次性诊断：光环不显示时这一行能区分四种情况 —— 没有它 ⇒ 处理器没进来/没找到实体；
        // 视图空间 z ≥ 0 ⇒ 几何跑到相机背后；NDC 超出 [-1,1] ⇒ 落在屏幕外（矩阵约定错）；都正常 ⇒ 问题在片元输出或混合状态。
        if (!loggedFirstHalo) {
            loggedFirstHalo = true;
            Vector3f viewCenter = pose.transformPosition(new Vector3f(
                    (float) plane.x, (float) plane.y, (float) plane.z), new Vector3f());
            Vector4f clip = new Vector4f(viewCenter.x, viewCenter.y, viewCenter.z, 1.0F)
                    .mul(RenderSystem.getProjectionMatrix());
            float ndcX = clip.w == 0.0F ? Float.NaN : clip.x / clip.w;
            float ndcY = clip.w == 0.0F ? Float.NaN : clip.y / clip.w;
            HallMod.LOGGER.info("[CollapsarHalo] 首帧: 实体={} 相机={} 视图空间中心=({}, {}, {}) NDC=({}, {}) 半径={} 血量={}/{} agitation={} uAlpha={}",
                    collapsar.blockPosition(), camera,
                    viewCenter.x, viewCenter.y, viewCenter.z,
                    ndcX, ndcY, HALO_RADIUS, health, max, agitation,
                    BASE_ALPHA * (0.78F + 0.30F * agitation));
        }

        // 再记一行绘制前 GL 状态：光环"完全看不见"最常见于混合被关掉、或深度函数不是 LEQUAL
        if (!loggedFirstGl) {
            loggedFirstGl = true;
            HallMod.LOGGER.info("[CollapsarHalo] 绘制前 GL: blend={} depthFunc={} (515=LEQUAL) cull={}",
                    GL11.glGetBoolean(GL11.GL_BLEND),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetBoolean(GL11.GL_CULL_FACE));
        }

        float alpha = BASE_ALPHA * (0.78F + 0.30F * agitation);
        float content = CONTENT_STRENGTH * (0.85F + 0.55F * agitation);

        // 光影兼容：阴影贴图 pass 里绝不画（否则地上/墙上会多出一整块六芒星阴影）；
        // 光影包激活时也不能此刻画（主帧缓冲还是 GBuffer，会被 composite 当普通几何重新着色）⇒ 快照入队，renderLevel TAIL 再直写主 RT。
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;
        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            CollapsarHaloLateRenderQueue.enqueue(pose, bl, tl, tr, br, time, alpha, content, agitation);
            return;
        }

        drawHalo(pose, bl, tl, tr, br, time, alpha, content, agitation);
    }

    /** 延迟回放用：与 {@link #renderOne} 的区别是着色器在回放时刻重查（光影重载后字段可能已换），
     *  且调用方已用 {@code LateOutlineRenderState} 绑好主 RT、清好 scissor 与混合状态。 */
    static void drawHalo(Matrix4f pose, Vec3 bl, Vec3 tl, Vec3 tr, Vec3 br,
                         float time, float alpha, float content, float agitation) {
        ShaderInstance shader = SplendidingShaders.collapsarHaloShader;
        if (shader == null) return;

        setUniform(shader, "uTime", time);
        setUniform(shader, "uRot", 0.0F);
        setUniform(shader, "uAlpha", alpha);
        setUniform(shader, "uSize", 1.0F);
        setUniform(shader, "uBorder", BORDER_THICKNESS);
        setUniform(shader, "uContent", content);
        setUniform(shader, "uAgitation", agitation);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(buffer, pose, bl, 0.0F, 0.0F);
        vertex(buffer, pose, tl, 0.0F, 1.0F);
        vertex(buffer, pose, tr, 1.0F, 1.0F);
        vertex(buffer, pose, br, 1.0F, 0.0F);
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, Vec3 pos, float u, float v) {
        buffer.vertex(pose, (float) pos.x, (float) pos.y, (float) pos.z).uv(u, v).endVertex();
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) shader.safeGetUniform(name).set(value);
    }
}
