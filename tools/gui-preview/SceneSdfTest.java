/**
 * SDF 形状自检（诊断用场景）：在纯色背景上分别画 disc / ring / roundRect /
 * line / star，确认颜色、几何、抗锯齿与 uniform 绑定都正确。
 *
 * <pre>cmd /c "tools\gui-preview\gui_preview.cmd -Scene sdf_test -Time 0"</pre>
 */
final class SceneSdfTest {

    public static void render(PreviewCanvas c, float time) {
        c.clear(0xFF0A1020);

        // 直接加载一次：编译失败时这里会抛异常并打印真实 GLSL 错误（SdfShapes 内部会静默降级）
        c.shader("sdf_ui");

        // 1) 实心圆（深色 + 低 alpha 测试混合）
        SdfShapes.disc(c, 90, 90, 46, 0xE60A1020, 0xE60A1020);
        c.text("disc DEEP", 58, 146, 0xFFFFFFFF, false);

        // 2) 金色圆环
        SdfShapes.ring(c, 220, 90, 34, 40, 0xFFE7C46A, 0xFFE7C46A, false);
        c.text("ring GOLD", 190, 146, 0xFFFFFFFF, false);

        // 3) 圆角矩形 + 内描边
        SdfShapes.roundRect(c, 300, 44, 120, 60, 12, 0xE6141B2E, 0xE6141B2E,
                0xFFE7C46A, 1.2F);
        c.text("roundRect", 330, 116, 0xFFFFFFFF, false);

        // 4) 胶囊线（斜线 + 竖线）
        SdfShapes.line(c, 470, 50, 590, 120, 2.0F, 0xFF6FD8C0);
        SdfShapes.line(c, 510, 60, 510, 130, 0.8F, 0xFFE7C46A);
        c.text("capsule line", 480, 146, 0xFFFFFFFF, false);

        // 5) 星形
        SdfShapes.star(c, 90, 250, 40, 16, 5, 0F, 0xFFFFE49A);
        c.text("star 5", 64, 306, 0xFFFFFFFF, false);

        // 6) 半透明发光层叠（低 alpha disc 叠加）
        for (int i = 4; i >= 1; i--) {
            SdfShapes.disc(c, 230, 250, 10F * i, 0x30E7C46A, 0x30E7C46A);
        }
        c.text("alpha glow", 190, 306, 0xFFFFFFFF, false);

        // 7) 细环（0.8 宽）与细线，检查覆盖率会不会让线条消失
        SdfShapes.ring(c, 340, 250, 30, 31, 0xFFE7C46A, 0xFFE7C46A, false);
        SdfShapes.ring(c, 340, 250, 20, 20.8F, 0xFFE7C46A, 0xFFE7C46A, false);
        SdfShapes.line(c, 280, 250, 400, 250, 0.4F, 0xFF6FD8C0);
        c.text("thin rings", 300, 306, 0xFFFFFFFF, false);

        c.save("sdf_test");
    }
}
