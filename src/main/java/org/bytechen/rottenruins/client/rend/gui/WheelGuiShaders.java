package org.bytechen.rottenruins.client.rend.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.rend.SplendidingShaders;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * 轮盘 HUD / 装载界面在游戏里的<b>立即绘制</b>层。
 *
 * <p>和 {@link org.bytechen.rottenruins.client.gui.MagicGuiShaders} 一样的规矩
 * （先 {@code gui.flush()}、uniform 设完立刻画、画完把 RenderSystem 状态还回去），
 * 只是这里多了 {@code sdf_ui} 的通用形状入口：圆、环、扇区、圆角矩形、胶囊、星形
 * 全部走同一个着色器，{@code uScale = GUI scale}，边界永远是 1 个物理像素的过渡。</p>
 *
 * <p>不依赖任何贴图；数据由 {@code WheelRenderer} 算好再调这里。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WheelGuiShaders {

    /** GUI 阶段的 ModelViewMat 是单位阵；顶点直接写 GUI 缩放坐标（和 MagicGuiShaders 一致）。 */
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static final Set<String> WARNED = new HashSet<>();

    private WheelGuiShaders() {
    }

    /** 轮盘三支设计着色器是否至少加载成功（失败时 WheelRenderer 走纯色降级）。 */
    public static boolean wheelAvailable() {
        return SplendidingShaders.wheelSliceShader != null
                && SplendidingShaders.wheelAuraShader != null
                && SplendidingShaders.wheelCoreShader != null;
    }

    /** SDF 形状是否可用（圆/环/圆角/胶囊/星形）。 */
    public static boolean sdfAvailable() {
        return SplendidingShaders.wheelSdfShader != null;
    }

    // ══════════════════════════════════════════════════════════════
    //  轮盘三支着色器
    // ══════════════════════════════════════════════════════════════

    /** 一个扇区的渲染状态（由 WheelRenderer 按槽位算好）。 */
    public record Slice(float startAngle, float endAngle,
                        float colorA_r, float colorA_g, float colorA_b,
                        float colorB_r, float colorB_g, float colorB_b,
                        float hover, float selected, float locked, float cooldown,
                        float intensity) {
    }

    /**
     * 一次性画完整轮盘：外圈光环 → 8 个扇区 → 中心核心。
     * 三组各只 flush / 切 program 一次；中间的 CPU 装饰（图标/文字）由调用方在这之后画。
     *
     * @param radius      轮盘外半径（GUI 像素，已含打开动画）
     * @param inner       内半径
     * @param hovered     悬停扇区；-1 表示没有
     * @param selected    当前选中扇区；-1 表示没有
     * @param manaFrac    法力比例 0..1（中心法力弧）
     * @param charge      蓄力比例 0..1（中心脉冲；未蓄力传 0）
     * @param cooldownFrac 选中扇区的冷却比例（中心逆时针弧）
     * @param coreColor   核心主色 rgb（通常取选中法术的流派色）
     */
    public static void drawWheel(GuiGraphics gui,
                                 float cx, float cy, float radius, float inner,
                                 float time, boolean loadoutMode, float openT,
                                 Slice[] slices, int hovered, int selected,
                                 float manaFrac, float charge, float cooldownFrac,
                                 float coreR, float coreG, float coreB) {
        ShaderInstance aura = SplendidingShaders.wheelAuraShader;
        ShaderInstance slice = SplendidingShaders.wheelSliceShader;
        ShaderInstance core = SplendidingShaders.wheelCoreShader;
        if (aura == null || slice == null || core == null) {
            return;
        }
        float alpha = clamp01(openT);

        // ① 外圈光环（加色）
        begin(gui, aura);
        {
            float span = radius * 3.1F;
            float qx = cx - span * 0.5F;
            float qy = cy - span * 0.5F;
            set(aura, "uCenter", cx, cy);
            set(aura, "uQuadOrigin", qx, qy);
            set(aura, "uQuadSize", span, span);
            set(aura, "uOuter", radius);
            set(aura, "uInner", inner);
            set(aura, "uTime", time);
            float hoverAngle = selected >= 0 ? slotAngle(selected)
                    : (hovered >= 0 ? slotAngle(hovered) : 0.0F);
            set(aura, "uHoverAngle", hoverAngle);
            set(aura, "uHoverStrength", selected >= 0 ? 0.9F : (hovered >= 0 ? 0.45F : 0.0F));
            set(aura, "uIntensity", alpha * (loadoutMode ? 0.55F : 1.0F));
            set(aura, "uColorA", 0.906F, 0.769F, 0.416F, 1.0F);
            set(aura, "uColorB", 0.435F, 0.847F, 0.753F, 1.0F);
            set(aura, "uColorC", 0.165F, 0.106F, 0.302F, 1.0F);
            quad(qx, qy, span, span);
        }
        end();

        // ② 8 个扇区
        begin(gui, slice);
        for (Slice s : slices) {
            if (s == null) {
                continue;
            }
            // quad 比外径大一圈，给 hover/selected 外发光衰减空间（避免露出 quad 直边）
            float span = radius * 2.7F;
            float qx = cx - span * 0.5F;
            float qy = cy - span * 0.5F;
            set(slice, "uCenter", cx, cy);
            set(slice, "uQuadOrigin", qx, qy);
            set(slice, "uQuadSize", span, span);
            set(slice, "uRadius", radius);
            set(slice, "uInnerRadius", inner);
            set(slice, "uStartAngle", s.startAngle());
            set(slice, "uEndAngle", s.endAngle());
            set(slice, "uColorA", s.colorA_r(), s.colorA_g(), s.colorA_b(), 1.0F);
            set(slice, "uColorB", s.colorB_r(), s.colorB_g(), s.colorB_b(), 1.0F);
            set(slice, "uTime", time);
            set(slice, "uHover", s.hover());
            set(slice, "uSelected", s.selected());
            set(slice, "uLocked", s.locked());
            set(slice, "uCooldown", s.cooldown());
            set(slice, "uIntensity", s.intensity());
            quad(qx, qy, span, span);
        }
        end();

        // ③ 中心核心（加色）
        begin(gui, core);
        {
            float coreRadius = Math.max(8.0F, inner - 4.0F);
            float quadSize = coreRadius * 2.6F;
            float qx = cx - quadSize * 0.5F;
            float qy = cy - quadSize * 0.5F;
            set(core, "uCenter", cx, cy);
            set(core, "uQuadOrigin", qx, qy);
            set(core, "uQuadSize", quadSize, quadSize);
            set(core, "uCoreRadius", coreRadius * 0.92F);
            set(core, "uTime", time);
            set(core, "uMana", clamp01(manaFrac));
            set(core, "uCharge", clamp01(charge));
            set(core, "uCooldown", clamp01(cooldownFrac));
            set(core, "uColorA", coreR, coreG, coreB, 1.0F);
            set(core, "uColorB", 1.0F, 0.894F, 0.604F, 1.0F);
            set(core, "uIntensity", alpha);
            quad(qx, qy, quadSize, quadSize);
        }
        end();
    }

    // ══════════════════════════════════════════════════════════════
    //  SDF 通用形状
    // ══════════════════════════════════════════════════════════════

    /** 实心圆 / 圆盘（颜色按竖直方向渐变，可同色）。 */
    public static void sdfDisc(GuiGraphics gui, float cx, float cy, float radius,
                               int colorTop, int colorBottom) {
        sdfRing(gui, cx, cy, 0.0F, radius, colorTop, colorBottom, false);
    }

    /** 圆环；radial=true 时按半径从 inner 色渐变到 outer 色。 */
    public static void sdfRing(GuiGraphics gui, float cx, float cy, float rIn, float rOut,
                               int colorIn, int colorOut, boolean radial) {
        ShaderInstance sdf = SplendidingShaders.wheelSdfShader;
        if (sdf == null || rOut <= 0.05F) {
            return;
        }
        float pad = 2.0F;
        float x0 = cx - rOut - pad;
        float y0 = cy - rOut - pad;
        float span = (rOut + pad) * 2.0F;
        begin(gui, sdf);
        sdfBase(sdf, x0, y0, span, span);
        set(sdf, "uMode", 0.0F);
        set(sdf, "uCenter", cx - x0, cy - y0);
        set(sdf, "uRIn", Math.max(0.0F, rIn));
        set(sdf, "uROut", rOut);
        set(sdf, "uA0", 0.0F);
        set(sdf, "uA1", (float) (Math.PI * 2.0));
        set(sdf, "uGradMode", radial ? 0.0F : 1.0F);
        setColor(sdf, "uColorIn", colorIn);
        setColor(sdf, "uColorOut", colorOut);
        quad(x0, y0, span, span);
        end();
    }

    /** 圆环扇区（按半径渐变）。 */
    public static void sdfWedge(GuiGraphics gui, float cx, float cy, float rIn, float rOut,
                                float a0, float a1, int colorIn, int colorOut) {
        ShaderInstance sdf = SplendidingShaders.wheelSdfShader;
        if (sdf == null || rOut <= 0.05F) {
            return;
        }
        float pad = 2.0F;
        float x0 = cx - rOut - pad;
        float y0 = cy - rOut - pad;
        float span = (rOut + pad) * 2.0F;
        begin(gui, sdf);
        sdfBase(sdf, x0, y0, span, span);
        set(sdf, "uMode", 0.0F);
        set(sdf, "uCenter", cx - x0, cy - y0);
        set(sdf, "uRIn", Math.max(0.0F, rIn));
        set(sdf, "uROut", rOut);
        set(sdf, "uA0", a0);
        set(sdf, "uA1", Math.min(a1, a0 + (float) (Math.PI * 2.0)));
        set(sdf, "uGradMode", 0.0F);
        setColor(sdf, "uColorIn", colorIn);
        setColor(sdf, "uColorOut", colorOut);
        quad(x0, y0, span, span);
        end();
    }

    /** 圆角矩形（可选 1px 内描边；borderWidth<=0 表示不描边）。 */
    public static void sdfRoundRect(GuiGraphics gui, float x, float y, float w, float h,
                                    float radius, int colorTop, int colorBottom,
                                    int borderColor, float borderWidth) {
        ShaderInstance sdf = SplendidingShaders.wheelSdfShader;
        if (sdf == null || w <= 0.5F || h <= 0.5F) {
            return;
        }
        begin(gui, sdf);
        sdfBase(sdf, x, y, w, h);
        set(sdf, "uMode", 1.0F);
        set(sdf, "uCenter", w * 0.5F, h * 0.5F);
        set(sdf, "uHalfSize", w * 0.5F, h * 0.5F);
        set(sdf, "uRound", Math.max(0.0F, Math.min(radius, Math.min(w, h) * 0.5F)));
        set(sdf, "uBorder", Math.max(0.0F, borderWidth));
        set(sdf, "uGradMode", colorTop == colorBottom ? 2.0F : 0.0F);
        setColor(sdf, "uColorIn", colorTop);
        setColor(sdf, "uColorOut", colorBottom);
        setColor(sdf, "uBorderColor", borderColor);
        quad(x, y, w, h);
        end();
    }

    /** 胶囊线段（圆头）。 */
    public static void sdfLine(GuiGraphics gui, float x1, float y1, float x2, float y2,
                               float halfWidth, int argb) {
        ShaderInstance sdf = SplendidingShaders.wheelSdfShader;
        if (sdf == null || halfWidth <= 0.0F) {
            return;
        }
        float pad = halfWidth + 2.0F;
        float x0 = Math.min(x1, x2) - pad;
        float y0 = Math.min(y1, y2) - pad;
        float w = Math.abs(x2 - x1) + pad * 2.0F;
        float h = Math.abs(y2 - y1) + pad * 2.0F;
        begin(gui, sdf);
        sdfBase(sdf, x0, y0, w, h);
        set(sdf, "uMode", 2.0F);
        set(sdf, "uP0", x1 - x0, y1 - y0);
        set(sdf, "uP1", x2 - x0, y2 - y0);
        set(sdf, "uThickness", halfWidth);
        set(sdf, "uGradMode", 2.0F);
        setColor(sdf, "uColorIn", argb);
        setColor(sdf, "uColorOut", argb);
        quad(x0, y0, w, h);
        end();
    }

    /** 星形（精确多边形尖角）。 */
    public static void sdfStar(GuiGraphics gui, float cx, float cy, float rOut, float rIn,
                               int points, float rotation, int argb) {
        ShaderInstance sdf = SplendidingShaders.wheelSdfShader;
        // sdf_ui 的星形循环上限是 16 条边，也就是最多 8 个尖
        points = Math.max(3, Math.min(8, points));
        if (sdf == null || rOut <= 0.1F) {
            return;
        }
        float pad = 2.0F;
        float x0 = cx - rOut - pad;
        float y0 = cy - rOut - pad;
        float size = (rOut + pad) * 2.0F;
        begin(gui, sdf);
        sdfBase(sdf, x0, y0, size, size);
        set(sdf, "uMode", 3.0F);
        set(sdf, "uCenter", cx - x0, cy - y0);
        set(sdf, "uROut", rOut);
        set(sdf, "uRIn", Math.max(0.05F, rIn));
        set(sdf, "uPoints", (float) points);
        set(sdf, "uRot", rotation);
        set(sdf, "uGradMode", 2.0F);
        setColor(sdf, "uColorIn", argb);
        setColor(sdf, "uColorOut", argb);
        quad(x0, y0, size, size);
        end();
    }

    // ══════════════════════════════════════════════════════════════
    //  内部：状态 / uniform / quad
    // ══════════════════════════════════════════════════════════════

    private static void begin(GuiGraphics gui, ShaderInstance shader) {
        gui.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);
    }

    private static void end() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
    }

    /** SDF 通用 uniform：canvas + scale + 全部复位（uniform 是粘滞的）。 */
    private static void sdfBase(ShaderInstance sdf, float x, float y, float w, float h) {
        set(sdf, "uCanvas", w, h);
        set(sdf, "uCenter", 0.0F, 0.0F);
        set(sdf, "uHalfSize", 0.0F, 0.0F);
        set(sdf, "uP0", 0.0F, 0.0F);
        set(sdf, "uP1", 0.0F, 0.0F);
        set(sdf, "uRIn", 0.0F);
        set(sdf, "uROut", 0.0F);
        set(sdf, "uA0", 0.0F);
        set(sdf, "uA1", 0.0F);
        set(sdf, "uRound", 0.0F);
        set(sdf, "uBorder", 0.0F);
        set(sdf, "uThickness", 0.0F);
        set(sdf, "uPoints", 4.0F);
        set(sdf, "uRot", 0.0F);
        set(sdf, "uSoft", 0.0F);
        set(sdf, "uGradMode", 2.0F);
        set(sdf, "uScale", Math.max(1.0F,
                (float) Minecraft.getInstance().getWindow().getGuiScale()));
    }

    /** 发一个覆盖 (x,y,w,h) 的 UV 0..1 矩形。 */
    private static void quad(float x, float y, float w, float h) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(IDENTITY, x, y, 0.0F).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(IDENTITY, x, y + h, 0.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(IDENTITY, x + w, y + h, 0.0F).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(IDENTITY, x + w, y, 0.0F).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void setColor(ShaderInstance shader, String name, int argb) {
        set(shader, name,
                ((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                ((argb >>> 24) & 0xFF) / 255.0F);
    }

    private static void set(ShaderInstance shader, String name, float v) {
        if (shader.getUniform(name) == null) {
            return;
        }
        try {
            shader.safeGetUniform(name).set(v);
        } catch (Throwable t) {
            warnOnce(name, t);
        }
    }

    private static void set(ShaderInstance shader, String name, float a, float b) {
        if (shader.getUniform(name) == null) {
            return;
        }
        try {
            shader.safeGetUniform(name).set(a, b);
        } catch (Throwable t) {
            warnOnce(name, t);
        }
    }

    private static void set(ShaderInstance shader, String name, float a, float b, float c, float d) {
        if (shader.getUniform(name) == null) {
            return;
        }
        try {
            shader.safeGetUniform(name).set(a, b, c, d);
        } catch (Throwable t) {
            warnOnce(name, t);
        }
    }

    private static void warnOnce(String name, Throwable t) {
        if (WARNED.add(name)) {
            HallMod.LOGGER.warn("[WheelGuiShaders] uniform '{}' 上传失败：{}", name, t.toString());
        }
    }

    /** 第 i 个扇区的中心角（正上 = 0 号位，顺时针）。 */
    public static float slotAngle(int index) {
        return (float) (-Math.PI / 2.0 + index * Math.PI * 2.0 / 8.0);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }
}
