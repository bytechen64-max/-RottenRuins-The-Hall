/**
 * 框架自检场景：把「背景 / 文字 / fill / 渐变 / 描边 / 贴图 / 各 GUI core shader」
 * 各画一块，用来在动手做具体场景前确认整套工具链是通的。
 *
 * <p>产物：{@code smoke.png}（总览）、{@code smoke_overlay.png}（Voronoi 全屏叠加）。
 * 这个场景同时也是 API 的活文档：新场景照这里的调用方式抄即可。</p>
 */
public final class SceneSmoke {

    private SceneSmoke() {}

    public static void render(PreviewCanvas c, float time) {
        PreviewShader voronoi = c.shader("rendertype_gui_horror_voronoi");

        // ── 帧 1：恐怖 Voronoi 全屏叠加（检查它压在世界画面上是否可读）──
        c.clear(0xFF000000);
        c.backdrop();
        voronoi.set("ScreenSize", (float) c.width, (float) c.height);
        voronoi.set("Time", time);
        voronoi.set("uIntensity", 0.75F);
        c.shaderQuad(voronoi, 0, 0, c.width, c.height);
        c.save("smoke_overlay");

        // ── 帧 2：总览（主入口返回后还会把同一帧存成 smoke.png）──
        c.clear(0xFF000000);
        c.backdrop();

        c.text("gui-preview smoke test", 12, 10, 0xFFFFFFFF, true);
        c.text("font: ABC xyz 0123 <=> [9px]  |  time=" + String.format("%.2f", time),
                12, 24, 0xFFA8E0FF, false);

        // ── 基础图元 ──
        c.fill(12, 40, 120, 14, 0xFF3A2A55);
        c.fillGradientV(140, 40, 120, 14, 0xFFFF4FB8, 0xFF2A1B3D);
        c.strokeRect(12, 60, 248, 48, 0xFFFF4FB8, 0xFF9B4DFF);
        c.text("fill / fillGradientV / strokeRect", 18, 66, 0xFFCCCCCC, false);

        // ── 贴图 ──
        PreviewTexture vow = c.texture("textures/item/crimson_vow.png");
        PreviewTexture daylight = c.texture("textures/item/silent_daylight.png");
        PreviewTexture hard = c.texture("textures/gui/difficulty_hard.png");
        c.fill(16, 82, 24, 24, 0x40FFFFFF);
        c.blit(vow, 18, 84, 20, 20);
        c.blit(daylight, 44, 84, 20, 20);
        c.blit(hard, 70, 84, 20, 20);
        c.text("blit()", 98, 88, 0xFFCCCCCC, false);

        // ── 创造栏隔断（gui_tab_divider）──
        PreviewShader divider = c.shader("gui_tab_divider");
        divider.set("uTime", time);
        divider.set("uSize", 162F, 18F);
        divider.set("uColorA", 0.79F, 0.63F, 0.15F, 1F);
        divider.set("uColorB", 0.27F, 0.78F, 0.62F, 0.85F);
        divider.set("uColorC", 0.60F, 0.16F, 0.26F, 0.70F);
        divider.set("uIntensity", 1F);
        divider.set("uGlow", 0.6F);
        c.shaderQuad(divider, 12, 120, 162, 18);
        c.text("gui_tab_divider", 180, 125, 0xFFCCCCCC, false);

        // ── tooltip 热力底板（rendertype_tooltip_thermal）──
        PreviewShader thermal = c.shader("rendertype_tooltip_thermal");
        thermal.set("uTime", time);
        thermal.set("uSpeed", 0.16F);
        thermal.set("uScale", 96F);
        thermal.set("uIntensity", 0.9F);
        thermal.set("uPanelSize", 180F, 60F);
        thermal.set("uBaseColor", 0.118F, 0.051F, 0.157F, 0.94F);
        thermal.set("uFlowColor", 1F, 0.31F, 0.72F, 1F);
        thermal.set("uHotColor", 0.91F, 0.65F, 1F, 1F);
        c.shaderQuad(thermal, 12, 150, 180, 60);
        c.strokeRect(8, 146, 188, 68, 0x80FF4FB8, 0x809B4DFF);
        c.text("rendertype_tooltip_thermal", 200, 165, 0xFFCCCCCC, false);

        // ── 坍缩使徒血条（rendertype_gui_collapsar_bar）──
        PreviewTexture mask = c.texture("textures/gui/collapsar_health_fullmask.png");
        PreviewTexture outline = c.texture("textures/gui/collapsar_health_outline.png");
        java.awt.image.BufferedImage maskImage = c.image("textures/gui/collapsar_health_fullmask.png");
        int bandTop = maskImage.getHeight();
        int bandBottom = -1;
        for (int y = 0; y < maskImage.getHeight(); y++) {
            for (int x = 0; x < maskImage.getWidth(); x++) {
                if (((maskImage.getRGB(x, y) >> 16) & 0xFF) > 128) {
                    bandTop = Math.min(bandTop, y);
                    bandBottom = Math.max(bandBottom, y);
                    break;
                }
            }
        }
        float bandMin = bandTop / (float) maskImage.getHeight();
        float bandMax = (bandBottom + 1) / (float) maskImage.getHeight();
        int barW = 182;
        int barH = Math.round(barW * maskImage.getHeight() / (float) maskImage.getWidth());
        PreviewShader bar = c.shader("rendertype_gui_collapsar_bar");
        bar.setSampler("MaskTexture", mask);
        bar.set("uTime", time);
        bar.set("uFill", 0.72F);
        bar.set("uAlpha", 1F);
        bar.set("uStarStrength", 1F);
        bar.set("uStarDensity", 1F);
        bar.set("uShake", 0F);
        bar.set("uBarAspect", maskImage.getWidth() / (float) maskImage.getHeight());
        bar.set("uBandMin", bandMin);
        bar.set("uBandMax", bandMax);
        bar.set("uUseGeomBand", 0F);
        // 画布 fill 是 (x, y, w, h)（不是 GuiGraphics 的 x1,y1,x2,y2）
        c.fill(12, 230 + Math.round(bandMin * barH), barW,
                Math.round((bandMax - bandMin) * barH), 0xFF000000);
        c.shaderQuad(bar, 12, 230, barW, barH);
        c.blit(outline, 12, 230, barW, barH);
        c.text("rendertype_gui_collapsar_bar + outline", 202, 240, 0xFFCCCCCC, false);

        // ── GUI 光晕（rendertype_gui_glow，套在难度图标上）──
        PreviewShader glow = c.shader("rendertype_gui_glow");
        glow.setSampler("IconTexture", hard);
        glow.set("IconUVMin", 0F, 0F);
        glow.set("IconUVMax", 1F, 1F);
        glow.set("GlowColor", 0.992F, 0.059F, 0.349F, 1F);
        glow.set("uTime", time);
        c.shaderQuad(glow, 12, 280, 44, 44);
        c.blit(hard, 26, 294, 16, 16);
        c.text("rendertype_gui_glow", 64, 296, 0xFFCCCCCC, false);

        // ── 恐怖 Voronoi 小区域（同一支 shader，区块尺度）──
        voronoi.set("ScreenSize", 200F, 90F);
        voronoi.set("Time", time);
        voronoi.set("uIntensity", 0.75F);
        c.pushClip(12, 280, 200, 90);
        c.shaderQuad(voronoi, 276, 280, 200, 90);
        c.popClip();
        c.text("rendertype_gui_horror_voronoi", 276, 250, 0xFFCCCCCC, false);

        c.save("smoke_frame1");
    }
}
