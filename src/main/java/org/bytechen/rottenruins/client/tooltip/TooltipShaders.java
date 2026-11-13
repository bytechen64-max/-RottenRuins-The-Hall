package org.bytechen.rottenruins.client.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.api.TooltipShaderSpec;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.joml.Matrix4f;

// tooltip 底板的着色器效果：立即绘制（BufferUploader.drawWithShader），不建 RenderType —— 少一套 StateShard。
// 三条规矩：先 gui.flush()（否则先前 GUI 顶点会盖在上面）、uniform 设完立刻画（攒着会用到上一份值）、画完还原 RenderSystem 状态。
// 混合以 json 的 "blend" 为准（apply 发生在手动状态之后）；json 的 depthtest/depthwrite 在 1.20.1 不生效，深度只能靠 RenderSystem。
@OnlyIn(Dist.CLIENT)
public final class TooltipShaders {

    private TooltipShaders() {}

    /** 热羽特征尺寸（GUI 像素）：调大 = 大团热浪，调小 = 细密丝；是像素而非 uv，面板变大粗细不变。 */
    private static final float THERMAL_SCALE = 96.0F;

    /** 对流速度（噪声空间/秒）：约 15 GUI 像素/秒的上升。 */
    private static final float THERMAL_SPEED = 0.16F;

    /** 这个 key 现在能不能画；false 时调用方要退回纯色底板（着色器加载失败不该变成一块透明玻璃）。 */
    public static boolean isReady(String key) {
        return TooltipShaderSpec.THERMAL.equals(key) && SplendidingShaders.tooltipThermalShader != null;
    }

    /** 在 (x, y, w, h) 上画效果（矩形由调用方按内容区外扩 4px 算）；time 单位为秒，取游戏时间 + 帧插值，暂停时热流也停住。 */
    public static void draw(String key, GuiGraphics gui, int x, int y, int w, int h,
                            TooltipShaderSpec spec, float time) {
        if (!isReady(key)) return;
        if (TooltipShaderSpec.THERMAL.equals(key)) {
            drawThermal(gui, x, y, w, h, spec, time);
        }
    }

    private static void drawThermal(GuiGraphics gui, int x, int y, int w, int h,
                                    TooltipShaderSpec spec, float time) {
        ShaderInstance shader = SplendidingShaders.tooltipThermalShader;
        if (shader == null) return;

        set1(shader, "uTime", time);
        set1(shader, "uSpeed", THERMAL_SPEED);
        set1(shader, "uScale", THERMAL_SCALE);
        set1(shader, "uIntensity", spec.intensity());
        set2(shader, "uPanelSize", w, h);
        set4(shader, "uBaseColor", spec.baseColor());
        set4(shader, "uFlowColor", spec.flowColor());
        set4(shader, "uHotColor", spec.hotColor());

        gui.flush();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        drawQuad(x, y, w, h);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** 发一个 UV 0..1 的矩形（左上角在 x, y）：顶点用 GUI 缩放坐标、矩阵用单位阵，ProjMat/ModelViewMat 由 RenderSystem 提供；uv.y = 0 在上沿，「热源在下沿」靠这个约定。 */
    private static void drawQuad(int x, int y, int w, int h) {
        Matrix4f pose = new Matrix4f();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, x,     y,     0.0F).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(pose, x,     y + h, 0.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + w, y + h, 0.0F).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + w, y,     0.0F).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    // uniform 名与 json 一一对应，缺一个就静默不生效。两层保护：① getUniform 判空（json 少声明时 safeGetUniform 只给哑对象，写了没反应）；
    // ② 外层 catch —— int 型 uniform 写 float 会 NPE 把客户端带崩，判空挡不住，故静默跳过并只报一次日志。
    private static void set1(ShaderInstance shader, String name, float v) {
        if (shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(v);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    private static void set2(ShaderInstance shader, String name, float x, float y) {
        if (shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(x, y);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    /** ARGB → 归一化 RGBA；alpha 参与，底色的透明度就是靠它。 */
    private static void set4(ShaderInstance shader, String name, int argb) {
        if (shader.getUniform(name) == null) return;
        try {
            shader.safeGetUniform(name).set(
                    ((argb >> 16) & 0xFF) / 255.0F,
                    ((argb >> 8) & 0xFF) / 255.0F,
                    (argb & 0xFF) / 255.0F,
                    ((argb >>> 24) & 0xFF) / 255.0F);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    /** 每个出问题的 uniform 只报一次，避免每帧刷日志。 */
    private static final java.util.Set<String> WARNED_UNIFORMS = new java.util.HashSet<>();

    private static void warnUniformOnce(String name, Throwable t) {
        if (WARNED_UNIFORMS.add(name)) {
            HallMod.LOGGER.warn("[TooltipThermal] uniform '{}' 上传失败"
                    + "（多半是 json 里的 type 与 shader 声明不一致）：{}", name, t.toString());
        }
    }
}
