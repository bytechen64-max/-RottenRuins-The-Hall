package org.bytechen.hall.client.rend;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 举剑格挡的临时诊断探针。
 *
 * <h3>为什么需要它</h3>
 * <p>连续几轮"看不见 / 变成空手"，说明<b>已经不该继续靠推算改数值</b>了：
 * 症状既可能是"渲染路径根本没执行"，也可能是"执行了但位置在画面外"，
 * 两者靠调参数无法区分。这个探针把关键事实写到文件，一次性把可能性分开。</p>
 *
 * <h3>看什么</h3>
 * <p>游戏跑起来后按住右键，然后看 {@code run/logs/crimsonvow-blockpose.log}：</p>
 * <ul>
 *   <li><b>文件不存在</b> → 混入没有生效（注入失败 / 没走到 renderArmWithItem），
 *       问题在注入层，与姿态数值无关；</li>
 *   <li><b>有 {@code ENTER} 但没有 {@code POSE}</b> → 中途异常/提前返回；</li>
 *   <li><b>有 {@code POSE}</b> → 路径通了，那么 {@code viewX/Y/Z} 就是物品原点在
 *       相机空间的位置：{@code viewZ} 为负才是"在镜头前方"，若 |viewX| 或
 *       |viewY| 明显大于 Z 的量级，就是被摆到画面外了。</li>
 * </ul>
 *
 * <p>诊断完把 {@link #enabled} 改回 false（或删掉本类）即可，不影响正式逻辑。</p>
 */
public final class CrimsonVowBlockDebug {

    /** 总开关。排查期置 true，之后置 false。 */
    public static boolean enabled = true;

    /** 只记录前若干次，避免每帧刷屏。 */
    private static final int MAX_LINES = 60;

    private static int written = 0;

    private CrimsonVowBlockDebug() {}

    /** 日志落在游戏工作目录的 logs/ 下（开发环境即 run/logs/）。 */
    private static Path logPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("crimsonvow-blockpose.log");
    }

    public static void log(String message) {
        if (!enabled) return;
        if (written >= MAX_LINES) return;
        try {
            Path path = logPath();
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(written + ": " + message + System.lineSeparator());
            }
            written++;
        } catch (IOException ignored) {
            // 探针自己绝不能影响渲染
        }
    }

    // 原版 applyItemArmTransform 在 equipProgress=1、主手（RIGHT）时的落点，
    // 也就是"正常拿剑"的位置 —— 拿它当参照系，差值一眼能看出往哪个方向偏了多少。
    private static final float VANILLA_BASE_X = 0.56F;
    private static final float VANILLA_BASE_Y = -0.52F - 0.6F;
    private static final float VANILLA_BASE_Z = -0.72F;

    /**
     * 记录姿态：把物品局部空间的几个关键点，经 <b>projMat × 相机空间位置</b>
     * 换算成屏幕像素坐标。
     *
     * <h3>为什么必须算出屏幕坐标</h3>
     * <p>先前只看"相机空间偏移"解释不了"到底偏到哪去了"—— 那需要知道屏幕映射方向。
     * 直接用游戏**真实的 projMat** 投影，就能回答唯一重要的问题：
     * <b>这些点落不落在 viewport 里</b>，以及落在哪一侧。</p>
     *
     * @param pose    当前（已应用完整姿态变换）的模型视图矩阵
     * @param viewport 视口宽高（像素），由调用方从 {@code RenderSystem.getViewport} 风格的信息给出
     */
    public static void logPose(org.joml.Matrix4f pose, int viewportWidth, int viewportHeight) {
        if (!enabled || written >= MAX_LINES) return;

        org.joml.Matrix4f proj = new org.joml.Matrix4f(
                com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        String label = "屏幕=?";
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("POSE viewport=%dx%d | ", viewportWidth, viewportHeight));

            // 物品模型局部空间的三个关键点：
            //  handheld 模型里剑沿 +Y 伸直、厚度在 Z；这些点足以判断整把剑的朝向与位置
            float[][] pts = {
                    {0.0F, 0.0F, 0.0F, 1.0F},   // 原点（握把底部）
                    {0.0F, 0.5F, 0.0F, 2.0F},   // 剑身中段
                    {0.0F, 1.0F, 0.0F, 3.0F},   // 剑尖
            };
            String[] names = {"握把", "剑身", "剑尖"};

            for (int i = 0; i < pts.length; i++) {
                org.joml.Vector4f v = new org.joml.Vector4f(pts[i][0], pts[i][1], pts[i][2], 1.0F);
                pose.transform(v);                       // -> 相机空间
                float camX = v.x, camY = v.y, camZ = v.z;

                org.joml.Vector4f c = new org.joml.Vector4f(camX, camY, camZ, 1.0F);
                proj.transform(c);                       // -> 裁剪空间

                if (c.w <= 0.0F) {
                    sb.append(String.format("%s=背后(w=%.2f) ", names[i], c.w));
                    continue;
                }
                float ndcX = c.x / c.w;
                float ndcY = c.y / c.w;
                int px = Math.round((ndcX * 0.5F + 0.5F) * viewportWidth);
                int py = Math.round((0.5F - ndcY * 0.5F) * viewportHeight);
                boolean inside = px >= 0 && px < viewportWidth && py >= 0 && py < viewportHeight;
                sb.append(String.format("%s=(%d,%d)%s ", names[i], px, py, inside ? "" : "★出界"));
            }

            sb.append(String.format("| 原点相机空间=(%.3f, %.3f, %.3f) delta_vs_vanilla=(%.3f, %.3f, %.3f)",
                    pose.m30(), pose.m31(), pose.m32(),
                    pose.m30() - VANILLA_BASE_X, pose.m31() - VANILLA_BASE_Y, pose.m32() - VANILLA_BASE_Z));
            label = sb.toString();
        } catch (Throwable t) {
            label = "POSE 投影计算失败: " + t;
        }
        log(label);
    }
}
