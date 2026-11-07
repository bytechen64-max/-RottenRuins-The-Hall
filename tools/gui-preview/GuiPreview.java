import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * GUI 离屏预览的总入口。
 *
 * <p>用法（一般通过 {@code gui_preview.ps1} 调，不直接 java）：</p>
 * <pre>
 *   java GuiPreview &lt;scene&gt; &lt;outDir&gt; [timeSeconds] [logicalWidth] [logicalHeight] [pixelScale]
 * </pre>
 *
 * <p>每个场景是一个独立的 {@code Scene<Name>.java}，约定：</p>
 * <pre>
 *   public final class SceneFoo {
 *       public static void render(PreviewCanvas c, float time) { ... }
 *   }
 * </pre>
 *
 * <p>用反射按名字找类，是为了让每个场景<b>单独编译</b>：A 场景写坏的时候 B 场景的工具
 * 还能跑（多个人同时改这个工具集时很重要）。场景名到类名：
 * {@code collapsar_bar → SceneCollapsarBar}。</p>
 */
public final class GuiPreview {

    private GuiPreview() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: GuiPreview <scene> <outDir> [timeSeconds] [width] [height] [scale]");
            System.out.println("       scene 例：smoke / tooltip / collapsar_bar / gui_effects / tab_divider");
            System.exit(2);
        }
        String scene = args[0];
        Path outDir = Path.of(args[1]);
        float time = args.length > 2 ? Float.parseFloat(args[2]) : 0.0F;
        int width = args.length > 3 ? Integer.parseInt(args[3]) : 640;
        int height = args.length > 4 ? Integer.parseInt(args[4]) : 400;
        int scale = args.length > 5 ? Integer.parseInt(args[5]) : 2;
        if (width <= 0 || height <= 0 || scale <= 0) {
            throw new IllegalArgumentException("画布尺寸/倍率必须是正数");
        }
        Files.createDirectories(outDir);
        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        String className = "Scene" + camel(scene);
        Class<?> sceneClass;
        try {
            sceneClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            System.out.println("[gui-preview] 没有这个场景：" + scene + "（找不到 " + className + ".java）");
            System.exit(3);
            return;
        }
        Method render = sceneClass.getMethod("render", PreviewCanvas.class, float.class);

        long started = System.nanoTime();
        try (PreviewGl gl = PreviewGl.open(width, height, scale)) {
            PreviewCanvas canvas = new PreviewCanvas(gl, projectRoot, outDir);
            canvas.clear(0xFF000000);
            render.invoke(null, canvas, time);
            canvas.save(scene);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            System.out.println("[gui-preview] 场景 " + scene + " 渲染失败：");
            cause.printStackTrace(System.out);
            System.exit(1);
            return;
        }
        long millis = (System.nanoTime() - started) / 1_000_000L;
        System.out.println("[gui-preview] 场景 " + scene + " 完成，用时 " + millis + "ms");
    }

    /** {@code collapsar_bar} / {@code gui-effects} → {@code CollapsarBar} / {@code GuiEffects}。 */
    static String camel(String scene) {
        String[] parts = scene.toLowerCase(Locale.ROOT).split("[-_]");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }
}
