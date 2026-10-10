package org.bytechen.hall.client.entity.render.impl;

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
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.bytechen.hall.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.hall.overworld.registry.entities.population.apostle.CollapsarEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * 坍缩使徒的<b>背部六芒星光环</b>。
 *
 * <h3>画在哪、怎么保证"在背后"</h3>
 * 一张朝向相机的正方形 billboard，中心取本体身体中段，再沿<b>视线反方向</b>后退
 * {@link #BACK_OFFSET} 格。实体是在 {@code AFTER_TRIPWIRE_BLOCKS} 之前画完的，
 * 它的深度已经写进深度缓冲，所以：
 * <ul>
 *   <li>光环中心那部分会被本体自己挡住 ⇒ 视觉上就是"光环在本体背后"；</li>
 *   <li>被方块挡住的部分也会正常消失（本阶段用 LEQUAL，不写深度）。</li>
 * </ul>
 *
 * <h3>插值（为什么不用实体的 tick 位置）</h3>
 * 渲染发生在两次 tick 之间，实体本身是按 {@code partialTick} 插值画的。
 * 光环如果直接用 {@code getX()/getY()/getZ()}，就会比本体"慢半拍"，
 * 快速飞行时表现为光环在身后一顿一顿地追。所以这里全部走插值：
 * <ul>
 *   <li>位置：{@code Mth.lerp(pt, entity.xo, entity.getX())} 等三个轴；</li>
 *   <li>时间：{@code (gameTime + pt) / 20} ⇒ 自转、噪声流动、色相都在帧间连续；</li>
 *   <li>血量：用插值后的血量比例算 {@code uAgitation}（濒死时转得更快更亮）。</li>
 * </ul>
 *
 * @see org.bytechen.hall.client.rend.SplendidingShaders#collapsarHaloShader
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
    /**
     * 空心六芒星边框的厚度（uv 空间，星形尖角半径 = 1.0）。
     * <p>0.085 ⇒ 边框总厚 0.17（沿轮廓向内外各 0.085），换算到屏幕大约是整个光环直径的 8.5%，
     * 是"看得出厚度但没糊住内容"的档位。</p>
     */
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
            // 诊断：日志里只有这一行、没有「首帧」那行 → 着色器没注册成功（注册异常会被
            // SplendidingShaders 里那个 catch 打成堆栈，先看有没有堆栈）
            if (!loggedNoShader) {
                loggedNoShader = true;
                HallMod.LOGGER.warn("[CollapsarHalo] collapsarHaloShader 为 null，光环不绘制");
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // 注意：这里<b>不能</b>判 mc.options.hideGui。
        // 那是 HUD 元素的规矩（F1 隐藏界面时 HUD 就该一起消失），而本光环是世界空间特效 ——
        // 玩家按 F1 只是想看干净的风景，不该把 boss 身上最有辨识度的东西也一起关掉。

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

        // 诊断：处理器进来了、着色器也有，但本帧一个本体都没找到 —— 这种情况
        // 只可能是 entitiesForRendering() 里没有它（例如本体已死、或还没同步到客户端）
        if (found == 0 && !loggedNoEntity) {
            loggedNoEntity = true;
            HallMod.LOGGER.info("[CollapsarHalo] 处理器已运行但本帧未找到 CollapsarEntity");
        }

        ps.popPose();
    }

    // ── 单个光环 ──────────────────────────────────────────────

    private static void renderOne(CollapsarEntity collapsar,
                                  float partialTick, Matrix4f pose, Vec3 camera) {
        // ① 位置：三轴都插值（本体也是这么画的，不插值就会"慢半拍"）
        double x = Mth.lerp(partialTick, collapsar.xo, collapsar.getX());
        double y = Mth.lerp(partialTick, collapsar.yo, collapsar.getY());
        double z = Mth.lerp(partialTick, collapsar.zo, collapsar.getZ());
        Vec3 center = new Vec3(x, y + collapsar.getBbHeight() * CENTER_HEIGHT, z);

        // ② 相机方向的基（billboard）
        Vec3 toCamera = camera.subtract(center);
        if (toCamera.lengthSqr() < 1.0E-6D) return;
        toCamera = toCamera.normalize();

        Vec3 right = toCamera.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);           // 正上方/正下方看时的退化处理
        }
        right = right.normalize();
        Vec3 up = right.cross(toCamera).normalize();

        // ③ 平面中心：沿视线反方向后退 → 本体挡住光环中心
        Vec3 plane = center.subtract(toCamera.scale(BACK_OFFSET));

        double r = HALO_RADIUS;
        Vec3 bl = plane.subtract(right.scale(r)).subtract(up.scale(r));
        Vec3 tl = plane.subtract(right.scale(r)).add(up.scale(r));
        Vec3 tr = plane.add(right.scale(r)).add(up.scale(r));
        Vec3 br = plane.add(right.scale(r)).subtract(up.scale(r));

        // ④ 血量：插值不了血量本身（它是离散同步的），但比例足够平滑，
        //    它同时决定转速、亮度——濒死时光环更躁动
        float max = collapsar.getMaxHealth();
        float health = collapsar.getHealth();
        float agitation = max <= 0.0F ? 0.0F : Mth.clamp(1.0F - health / max, 0.0F, 1.0F);

        // ⑤ 时间：gameTime + partialTick ⇒ 自转与噪声逐帧连续
        float time = (collapsar.level().getGameTime() + partialTick) / 20.0F;

        // 一次性诊断：光环不显示时，这一行能直接区分四种情况 ——
        //   ① 日志里没有这行 → 处理器没进来 / 没找到实体；
        //   ② 视图空间 z ≥ 0 → 几何跑到相机背后了；
        //   ③ NDC 超出 [-1,1] → 几何落在屏幕外（矩阵约定错）；
        //   ④ 两者都正常 → 问题在片元输出或混合状态。
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

        // 再记一行绘制前的 GL 状态：光环"完全看不见"最常见的两个原因是
        // 混合被关掉、或深度函数不是 LEQUAL（几何在离相机 1 格之外就被裁掉）
        if (!loggedFirstGl) {
            loggedFirstGl = true;
            HallMod.LOGGER.info("[CollapsarHalo] 绘制前 GL: blend={} depthFunc={} (515=LEQUAL) cull={}",
                    GL11.glGetBoolean(GL11.GL_BLEND),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetBoolean(GL11.GL_CULL_FACE));
        }

        float alpha = BASE_ALPHA * (0.78F + 0.30F * agitation);
        float content = CONTENT_STRENGTH * (0.85F + 0.55F * agitation);

        // ⑦ 光影兼容（详见类注释与 CollapsarHaloLateRenderQueue）：
        //    · 阴影贴图 pass 里绝不画 —— 那是从光源视角重建的，此刻画进去会让地上/墙上
        //      多出一整块六芒星阴影；
        //    · 光影包激活时也不能"此刻"画：AFTER_TRIPWIRE_BLOCKS 时主帧缓冲里还是 GBuffer
        //      原始数据，此刻画进去会被光影的 composite 当成普通几何重新着色，效果直接丢掉。
        //      改成快照入队，等 renderLevel() TAIL 光影合成完成后再直写主 RT 回放。
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;
        if (HeldItemOutlineCompat.isOculusShaderPackActive()) {
            CollapsarHaloLateRenderQueue.enqueue(pose, bl, tl, tr, br, time, alpha, content, agitation);
            return;
        }

        drawHalo(pose, bl, tl, tr, br, time, alpha, content, agitation);
    }

    /**
     * 延迟回放用：把已经算好的光环再画一遍。
     *
     * <p>与 {@link #renderOne} 的区别只有两点：着色器在回放时刻重新查一次
     * （光影加载/重载后字段可能已经换了），以及调用方已经用
     * {@link org.bytechen.hall.client.rend.glint.LateOutlineRenderState}
     * 绑好主 RT、清好 scissor 与混合状态。</p>
     */
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
