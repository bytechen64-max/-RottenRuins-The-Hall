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

/** 魔法星图的三支 GUI 着色器：深空背景 + 槽位连线 + 放入法术时滚动一次的光点。 */
@OnlyIn(Dist.CLIENT)
public final class MagicGuiShaders {

    // 三条规矩：① 先 gui.flush() 把已压着的 GUI 顶点刷出去，否则它们会盖在自绘内容上；
    // ② uniform 设完立刻画（真正上传发生在这次 draw 的 ShaderInstance.apply 里）；③ 画完把 RenderSystem 状态还回去。
    // 着色器没加载成功时三个方法都返回 false，由 MagicScreen 退回静态星点与虚线，界面仍可用。

    /** 单位阵：GUI 的 ProjMat / ModelViewMat 由 RenderSystem 提供，顶点直接写屏幕坐标。 */
    private static final Matrix4f IDENTITY = new Matrix4f();

    /** 每个出问题的 uniform 只报一次，避免每帧刷日志。 */
    private static final Set<String> WARNED_UNIFORMS = new HashSet<>();

    private MagicGuiShaders() {
    }

    //  深空背景

    /** 画全屏深空背景（流动星云 + 三层视差星点）；timeSeconds 用取模后的毫秒换算以免 float 精度变差，着色器不可用时返回 false。 */
    public static boolean drawStarfield(GuiGraphics gui, int width, int height,
                                        float timeSeconds,
                                        float mouseNormX, float mouseNormY) {
        ShaderInstance shader = SplendidingShaders.magicStarfieldShader;
        if (shader == null || width <= 0 || height <= 0) {
            return false;
        }

        set1(shader, "uTime", timeSeconds);
        set2(shader, "uSize", width, height);
        set2(shader, "uMouse", mouseNormX, mouseNormY);
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

    //  槽位连接光带

    /** 用发丝级金线连接相邻槽位，每段亮度可单独给（选中/悬停/有法术更亮），入场动画靠它逐段画出来 + 每段一次 draw call。 */
    public static boolean drawConstellation(GuiGraphics gui, int[] xs, int[] ys,
                                           float timeSeconds, float ribbonHalfWidth,
                                           float[] segmentIntensity) {
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

        gui.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        for (int i = 0; i < segments; i++) {
            float intensity = segmentIntensity != null && i < segmentIntensity.length
                    ? segmentIntensity[i]
                    : 1.0F;
            if (intensity <= 0.001F) {
                continue;
            }
            set1(shader, "uIntensity", 0.85F * intensity);

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
            // 法线 = 线段方向的垂线；UV.x = 像素长度 / 64
            float nx = -dy / length * ribbonHalfWidth;
            float ny = dx / length * ribbonHalfWidth;
            float uEnd = length / 64.0F;

            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(IDENTITY, x1 - nx, y1 - ny, 0.0F).uv(0.0F, 1.0F).endVertex();
            buffer.vertex(IDENTITY, x1 + nx, y1 + ny, 0.0F).uv(0.0F, 0.0F).endVertex();
            buffer.vertex(IDENTITY, x2 + nx, y2 + ny, 0.0F).uv(uEnd, 0.0F).endVertex();
            buffer.vertex(IDENTITY, x2 - nx, y2 - ny, 0.0F).uv(uEnd, 1.0F).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        }

        // 还原
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    //  放入法术时的一次性光点

    /** 沿一段连线画一颗只播一次的光点（progress 0→1，由 MagicScreen 计时）；halfWidth 是光带半宽（GUI 像素），着色器缺失返回 false。 */
    public static boolean drawPlacementPulse(GuiGraphics gui,
                                             float x1, float y1, float x2, float y2,
                                             float progress, float halfWidth) {
        ShaderInstance shader = SplendidingShaders.magicPulseShader;
        if (shader == null) {
            return false;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-3F) {
            return false;
        }

        set1(shader, "uProgress", progress);
        set4(shader, "uColor", 1.0F, 0.86F, 0.52F, 1.0F);
        set1(shader, "uIntensity", 0.9F);

        gui.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        float nx = -dy / length * halfWidth;
        float ny = dx / length * halfWidth;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(IDENTITY, x1 - nx, y1 - ny, 0.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(IDENTITY, x1 + nx, y1 + ny, 0.0F).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(IDENTITY, x2 + nx, y2 + ny, 0.0F).uv(1.0F, 0.0F).endVertex();
        buffer.vertex(IDENTITY, x2 - nx, y2 - ny, 0.0F).uv(1.0F, 1.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    //  内部：矩形 / uniform

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
