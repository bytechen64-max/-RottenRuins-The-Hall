package org.bytechen.rottenruins.client.gui;

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
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * 魔法星图的两支 GUI 着色器：深空背景 + 槽位连线。
 *
 * <h3>为什么走"立即绘制"而不是 RenderType</h3>
 * <p>和 tooltip 底板、创造栏隔断行、坍缩使徒血条同一条路：GUI 里画少量自绘内容，
 * 用 {@code BufferUploader.drawWithShader} 直接提交。少一套 StateShard，
 * 也就不用给剔除 / 深度 / 写掩码各配一遍默认值。</p>
 *
 * <h3>三条必须守住的规矩（都在项目里踩过）</h3>
 * <ol>
 *   <li><b>先 {@code gui.flush()}。</b> GUI 批处理里可能还压着先前元素的顶点，
 *       不先刷出去它们会盖在自绘内容之上。</li>
 *   <li><b>uniform 设完立刻画，中间不插别的绘制。</b> {@code Uniform.set()} 只是记值，
 *       真正上传发生在这次 draw 的 {@code ShaderInstance.apply()} 里。</li>
 *   <li><b>画完把 RenderSystem 状态还回去。</b> 后面还要画槽位、文字与 tooltip。</li>
 * </ol>
 *
 * <h3>着色器没加载成功时</h3>
 * <p>两个方法都返回 {@code false}，由 {@link MagicScreen} 退回静态星点与星点虚线 ——
 * 驱动老旧或被资源包覆盖 shader 时，界面仍然可用，只是少了流动感。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MagicGuiShaders {

    /** 单位阵：GUI 的 ProjMat / ModelViewMat 由 RenderSystem 提供，顶点直接写屏幕坐标。 */
    private static final Matrix4f IDENTITY = new Matrix4f();

    /** 每个出问题的 uniform 只报一次，避免每帧刷日志。 */
    private static final Set<String> WARNED_UNIFORMS = new HashSet<>();

    private MagicGuiShaders() {
    }

    // ──────────────────────────────────────────────────────────────
    //  深空背景
    // ──────────────────────────────────────────────────────────────

    /**
     * 画全屏深空背景（流动星云 + 三层视差星点）。
     *
     * @param timeSeconds 秒；用取模后的毫秒换算，避免 float 精度随开机时间变差
     * @return 着色器不可用 / 绘制失败时返回 {@code false}
     */
    public static boolean drawStarfield(GuiGraphics gui, int width, int height, float timeSeconds) {
        ShaderInstance shader = SplendidingShaders.magicStarfieldShader;
        if (shader == null || width <= 0 || height <= 0) {
            return false;
        }

        set1(shader, "uTime", timeSeconds);
        set2(shader, "uSize", width, height);
        set1(shader, "uIntensity", 1.0F);

        gui.flush();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        drawQuad(0, 0, width, height);

        // 还原：后面还有普通 GUI 绘制
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    //  槽位连接光带
    // ──────────────────────────────────────────────────────────────

    /**
     * 把相邻槽位用发光光带连起来（一次 draw call 画完所有段）。
     *
     * @param ribbonHalfWidth 光带半宽（GUI 像素）
     * @return 着色器不可用 / 没有可画的段时返回 {@code false}
     */
    public static boolean drawConstellation(GuiGraphics gui, int[] xs, int[] ys,
                                            float timeSeconds, float ribbonHalfWidth) {
        ShaderInstance shader = SplendidingShaders.magicConstellationShader;
        if (shader == null || xs == null || ys == null) {
            return false;
        }
        int segments = Math.min(xs.length, ys.length) - 1;
        if (segments <= 0) {
            return false;
        }

        set1(shader, "uTime", timeSeconds);
        set4(shader, "uColor", 0.93F, 0.72F, 0.36F, 0.85F);
        set1(shader, "uIntensity", 0.85F);

        gui.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < segments; i++) {
            float x1 = xs[i];
            float y1 = ys[i];
            float x2 = xs[i + 1];
            float y2 = ys[i + 1];
            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 1.0E-3F) {
                continue;
            }
            // 法线方向 = 线段方向的垂线
            float nx = -dy / length * ribbonHalfWidth;
            float ny = dx / length * ribbonHalfWidth;
            // UV.x 用"像素长度 / 64"，这样不同长度的段流动频率一致
            float uEnd = length / 64.0F;

            buffer.vertex(IDENTITY, x1 - nx, y1 - ny, 0.0F).uv(0.0F, 1.0F).endVertex();
            buffer.vertex(IDENTITY, x1 + nx, y1 + ny, 0.0F).uv(0.0F, 0.0F).endVertex();
            buffer.vertex(IDENTITY, x2 + nx, y2 + ny, 0.0F).uv(uEnd, 0.0F).endVertex();
            buffer.vertex(IDENTITY, x2 - nx, y2 - ny, 0.0F).uv(uEnd, 1.0F).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        // 还原
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    //  内部：矩形 / uniform
    // ──────────────────────────────────────────────────────────────

    /** 发一个 UV 0..1 的屏幕空间矩形（左上角 x,y）。 */
    private static void drawQuad(int x, int y, int w, int h) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(IDENTITY, x, y, 0.0F).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(IDENTITY, x, y + h, 0.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(IDENTITY, x + w, y + h, 0.0F).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(IDENTITY, x + w, y, 0.0F).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void set1(ShaderInstance shader, String name, float v) {
        if (shader.getUniform(name) == null) {
            return;
        }
        try {
            shader.safeGetUniform(name).set(v);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    private static void set2(ShaderInstance shader, String name, float x, float y) {
        if (shader.getUniform(name) == null) {
            return;
        }
        try {
            shader.safeGetUniform(name).set(x, y);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    private static void set4(ShaderInstance shader, String name, float r, float g, float b, float a) {
        if (shader.getUniform(name) == null) {
            return;
        }
        try {
            shader.safeGetUniform(name).set(r, g, b, a);
        } catch (Throwable t) {
            warnUniformOnce(name, t);
        }
    }

    private static void warnUniformOnce(String name, Throwable t) {
        if (WARNED_UNIFORMS.add(name)) {
            HallMod.LOGGER.warn("[MagicGuiShaders] uniform '{}' 上传失败"
                    + "（多半是 json 里的 type 与 shader 声明不一致）：{}", name, t.toString());
        }
    }
}
