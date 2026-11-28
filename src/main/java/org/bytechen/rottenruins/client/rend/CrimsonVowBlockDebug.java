package org.bytechen.rottenruins.client.rend;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 举剑格挡的临时诊断探针：日志写进 run/logs/crimsonvow-blockpose.log，区分"路径没执行"与"摆到画面外"。 */
public final class CrimsonVowBlockDebug {

    /**
     * 总开关：<b>默认关闭</b>（发布版不应往 run/logs 写诊断文件）。排查期临时置 true。
     * 目前 {@code log/logPose} 没有任何调用方，属于待清理的诊断探针。
     */
    public static boolean enabled = false;

    /** 只记录前若干次，避免每帧刷屏。 */
    private static final int MAX_LINES = 60;

    private static int written = 0;

    private CrimsonVowBlockDebug() {}

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

    // 原版 applyItemArmTransform 在 equipProgress=1、主手（RIGHT）时的落点，作为"正常拿剑"的参照系。
    private static final float VANILLA_BASE_X = 0.56F;
    private static final float VANILLA_BASE_Y = -0.52F - 0.6F;
    private static final float VANILLA_BASE_Z = -0.72F;

    /** 记录姿态：把物品局部空间关键点经 projMat 投成屏幕像素，回答"这些点落不落在 viewport 里"。 */
    public static void logPose(org.joml.Matrix4f pose, int viewportWidth, int viewportHeight) {
        if (!enabled || written >= MAX_LINES) return;

        org.joml.Matrix4f proj = new org.joml.Matrix4f(
                com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        String label = "屏幕=?";
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("POSE viewport=%dx%d | ", viewportWidth, viewportHeight));

            // handheld 模型里剑沿 +Y 伸直、厚度在 Z；三点足以判断整把剑的朝向与位置
            float[][] pts = {
                    {0.0F, 0.0F, 0.0F, 1.0F},
                    {0.0F, 0.5F, 0.0F, 2.0F},
                    {0.0F, 1.0F, 0.0F, 3.0F},
            };
            String[] names = {"握把", "剑身", "剑尖"};

            for (int i = 0; i < pts.length; i++) {
                org.joml.Vector4f v = new org.joml.Vector4f(pts[i][0], pts[i][1], pts[i][2], 1.0F);
                pose.transform(v);
                float camX = v.x, camY = v.y, camZ = v.z;

                org.joml.Vector4f c = new org.joml.Vector4f(camX, camY, camZ, 1.0F);
                proj.transform(c);

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
