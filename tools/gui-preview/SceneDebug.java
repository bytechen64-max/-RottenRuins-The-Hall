/**
 * 框架调试场景：分阶段保存，用来定位是"哪一类绘制"出问题。
 * 产物：build/gui-preview/debug_*.png（不参与正式场景）。
 */
public final class SceneDebug {

    private SceneDebug() {}

    public static void render(PreviewCanvas c, float time) {
        // 1) 纯色
        c.clear(0xFF203040);
        c.save("debug_clear");

        // 2) fill 两个矩形
        c.fill(40, 40, 120, 60, 0xFFFF0000);
        c.fill(200, 40, 120, 60, 0x8000FF00);
        c.save("debug_fill");

        // 3) 竖向渐变 + 描边
        c.fillGradientV(40, 140, 200, 60, 0xFFFF4FB8, 0xFF203060);
        c.strokeRect(40, 220, 200, 60, 0xFFFF4FB8, 0xFF9B4DFF);
        c.save("debug_gradient");

        // 4) 文字（有阴影 / 无阴影）
        c.text("ABC xyz 0123 shadow", 40, 320, 0xFFFFFFFF, true);
        c.text("ABC xyz 0123 plain", 40, 340, 0xFFA8E0FF, false);
        c.save("debug_text");

        // 5) 贴图
        c.blit(c.texture("textures/item/crimson_vow.png"), 320, 40, 32, 32);
        c.save("debug_texture");

        // 6) shader：tab divider
        PreviewShader divider = c.shader("gui_tab_divider");
        divider.set("uTime", time);
        divider.set("uSize", 162F, 18F);
        divider.set("uIntensity", 1F);
        divider.set("uGlow", 0.6F);
        c.shaderQuad(divider, 320, 120, 162, 18);
        c.save("debug_tab_divider");

        // 7) shader：thermal
        PreviewShader thermal = c.shader("rendertype_tooltip_thermal");
        thermal.set("uTime", time);
        thermal.set("uSpeed", 0.16F);
        thermal.set("uScale", 96F);
        thermal.set("uIntensity", 0.9F);
        thermal.set("uPanelSize", 180F, 60F);
        thermal.set("uBaseColor", 0.118F, 0.051F, 0.157F, 0.94F);
        thermal.set("uFlowColor", 1F, 0.31F, 0.72F, 1F);
        thermal.set("uHotColor", 0.91F, 0.65F, 1F, 1F);
        c.shaderQuad(thermal, 320, 160, 180, 60);
        c.save("debug_thermal");

        // 8) shader：voronoi 小方块（ScreenSize 与 quad 尺寸一致）
        PreviewShader voronoi = c.shader("rendertype_gui_horror_voronoi");
        voronoi.set("ScreenSize", 200F, 90F);
        voronoi.set("Time", time);
        voronoi.set("uIntensity", 0.75F);
        c.shaderQuad(voronoi, 320, 240, 200, 90);
        c.save("debug_voronoi");

        // 9) 背景
        c.clear(0xFF000000);
        c.backdrop();
        c.save("debug_backdrop");
    }
}
