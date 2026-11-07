/**
 * UI 形状的 SDF 抗锯齿绘制（逐物理像素覆盖率）。
 *
 * <p>参考 FIS-Cordyceps（MIT）的 {@code wheel_sdf.fsh} 思路：Minecraft 的
 * {@code GuiGraphics.fill} 只有轴对齐矩形，用三角光栅化拼圆/环/扇形/圆角矩形时
 * 边缘没有覆盖率抗锯齿；把形状交给 SDF 着色器，在片元里算「到边界的距离」，
 * 再按 1 物理像素宽度做 coverage，就能在任意 GUI 缩放下保持平滑。</p>
 *
 * <p>本类只画一个覆盖形状包围盒的 quad（UV 0..1），所有几何参数走 uniform；
 * {@link #pixelScale(PreviewCanvas)} 取当前"1 GUI 像素 = 多少物理像素"
 * （预览里是原生渲染倍率；进游戏时传 GUI scale），SDF 的抗锯齿宽度据此对齐。</p>
 */
final class SdfShapes {

    private static PreviewShader shader;
    private static boolean unavailable;

    private SdfShapes() {
    }

    static boolean available(PreviewCanvas c) {
        if (unavailable) {
            return false;
        }
        if (shader == null) {
            try {
                shader = c.shader("sdf_ui");
            } catch (RuntimeException e) {
                unavailable = true;
                return false;
            }
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  形状
    // ══════════════════════════════════════════════════════════════

    /** 实心圆盘（可选上下渐变）。 */
    static boolean disc(PreviewCanvas c, float cx, float cy, float radius,
                        int colorTop, int colorBottom) {
        return ring(c, cx, cy, 0F, radius, colorTop, colorBottom, false);
    }

    /** 圆环；{@code radial=true} 时按半径从内色渐变到外色，否则按竖直方向。 */
    static boolean ring(PreviewCanvas c, float cx, float cy, float rIn, float rOut,
                        int colorIn, int colorOut, boolean radial) {
        if (rOut <= 0.05F || !available(c)) {
            return false;
        }
        float pad = 2.0F;
        float x0 = cx - rOut - pad;
        float y0 = cy - rOut - pad;
        float w = (rOut + pad) * 2.0F;
        float h = (rOut + pad) * 2.0F;
        begin(c, shader, x0, y0, w, h);
        shader.set("uMode", 0F);
        shader.set("uCenter", cx - x0, cy - y0);
        shader.set("uRIn", Math.max(0F, rIn));
        shader.set("uROut", rOut);
        shader.set("uA0", 0F);
        shader.set("uA1", (float) (Math.PI * 2.0));
        shader.set("uGradMode", radial ? 0F : 1F);
        setColors(shader, colorIn, colorOut, 0);
        c.shaderQuad(shader, x0, y0, w, h);
        return true;
    }

    /** 圆环扇区（radial 渐变）。 */
    static boolean wedge(PreviewCanvas c, float cx, float cy, float rIn, float rOut,
                         float a0, float a1, int colorIn, int colorOut) {
        if (rOut <= 0.05F || !available(c)) {
            return false;
        }
        float pad = 2.0F;
        float x0 = cx - rOut - pad;
        float y0 = cy - rOut - pad;
        float w = (rOut + pad) * 2.0F;
        float h = (rOut + pad) * 2.0F;
        begin(c, shader, x0, y0, w, h);
        shader.set("uMode", 0F);
        shader.set("uCenter", cx - x0, cy - y0);
        shader.set("uRIn", Math.max(0F, rIn));
        shader.set("uROut", rOut);
        shader.set("uA0", a0);
        shader.set("uA1", Math.min(a1, a0 + (float) (Math.PI * 2.0)));
        shader.set("uGradMode", 0F);
        setColors(shader, colorIn, colorOut, 0);
        c.shaderQuad(shader, x0, y0, w, h);
        return true;
    }

    /** 圆角矩形（可选 1px 内描边）。 */
    static boolean roundRect(PreviewCanvas c, float x, float y, float w, float h,
                             float radius, int colorTop, int colorBottom,
                             int borderColor, float borderWidth) {
        if (w <= 0.5F || h <= 0.5F || !available(c)) {
            return false;
        }
        begin(c, shader, x, y, w, h);
        shader.set("uMode", 1F);
        shader.set("uCenter", w * 0.5F, h * 0.5F);
        shader.set("uHalfSize", w * 0.5F, h * 0.5F);
        shader.set("uRound", Math.max(0F, Math.min(radius, Math.min(w, h) * 0.5F)));
        shader.set("uBorder", Math.max(0F, borderWidth));
        shader.set("uGradMode", colorTop == colorBottom ? 2F : 0F);
        setColors(shader, colorTop, colorBottom, borderColor);
        c.shaderQuad(shader, x, y, w, h);
        return true;
    }

    /** 胶囊线段（圆头）：把斜线/符文笔画交给 SDF，避免阶梯边。 */
    static boolean line(PreviewCanvas c, float x1, float y1, float x2, float y2,
                        float halfWidth, int argb) {
        if (halfWidth <= 0F || !available(c)) {
            return false;
        }
        float pad = halfWidth + 2.0F;
        float x0 = Math.min(x1, x2) - pad;
        float y0 = Math.min(y1, y2) - pad;
        float w = Math.abs(x2 - x1) + pad * 2.0F;
        float h = Math.abs(y2 - y1) + pad * 2.0F;
        if (w < 0.5F || h < 0.5F) {
            return false;
        }
        begin(c, shader, x0, y0, w, h);
        shader.set("uMode", 2F);
        shader.set("uP0", x1 - x0, y1 - y0);
        shader.set("uP1", x2 - x0, y2 - y0);
        shader.set("uThickness", halfWidth);
        shader.set("uGradMode", 2F);
        setColors(shader, argb, argb, argb);
        c.shaderQuad(shader, x0, y0, w, h);
        return true;
    }

    /** 星形（points 个尖）：标题装饰/光标/法阵徽记用。 */
    static boolean star(PreviewCanvas c, float cx, float cy, float rOut, float rIn,
                        int points, float rotation, int argb) {
        if (rOut <= 0.1F || points < 3 || !available(c)) {
            return false;
        }
        float pad = 2.0F;
        float x0 = cx - rOut - pad;
        float y0 = cy - rOut - pad;
        float size = (rOut + pad) * 2.0F;
        begin(c, shader, x0, y0, size, size);
        shader.set("uMode", 3F);
        shader.set("uCenter", cx - x0, cy - y0);
        shader.set("uROut", rOut);
        shader.set("uRIn", Math.max(0.05F, rIn));
        shader.set("uPoints", (float) points);
        // 多边形边星形：外尖出现在 rot + k·2π/n，和 CPU 星形一致
        shader.set("uRot", rotation);
        shader.set("uGradMode", 2F);
        setColors(shader, argb, argb, argb);
        c.shaderQuad(shader, x0, y0, size, size);
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    //  内部
    // ══════════════════════════════════════════════════════════════

    /** 每个形状画之前把可能残留的 uniform 全部复位（uniform 是粘滞的）。 */
    private static void begin(PreviewCanvas c, PreviewShader sh, float x0, float y0,
                              float w, float h) {
        sh.set("uCanvas", w, h);
        sh.set("uCenter", 0F, 0F);
        sh.set("uHalfSize", 0F, 0F);
        sh.set("uP0", 0F, 0F);
        sh.set("uP1", 0F, 0F);
        sh.set("uRIn", 0F);
        sh.set("uROut", 0F);
        sh.set("uA0", 0F);
        sh.set("uA1", 0F);
        sh.set("uRound", 0F);
        sh.set("uBorder", 0F);
        sh.set("uThickness", 0F);
        sh.set("uPoints", 4F);
        sh.set("uRot", 0F);
        sh.set("uSoft", 0F);
        sh.set("uScale", pixelScale(c));
        sh.set("uGradMode", 2F);
    }

    private static void setColors(PreviewShader sh, int colorIn, int colorOut, int borderColor) {
        sh.set("uColorIn", r(colorIn), g(colorIn), b(colorIn), a(colorIn));
        sh.set("uColorOut", r(colorOut), g(colorOut), b(colorOut), a(colorOut));
        sh.set("uBorderColor", r(borderColor), g(borderColor), b(borderColor), a(borderColor));
    }

    /** 1 个 GUI 像素对应的物理像素数：预览里 = 原生渲染倍率。 */
    static float pixelScale(PreviewCanvas c) {
        return Math.max(1F, c.pixelScale());
    }

    private static float r(int argb) {
        return ((argb >> 16) & 0xFF) / 255F;
    }

    private static float g(int argb) {
        return ((argb >> 8) & 0xFF) / 255F;
    }

    private static float b(int argb) {
        return (argb & 0xFF) / 255F;
    }

    private static float a(int argb) {
        return ((argb >>> 24) & 0xFF) / 255F;
    }
}
