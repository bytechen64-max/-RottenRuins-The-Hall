import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 离屏预览魔法星图的两支着色器（深空背景 + 槽位连接光带），导出 PNG。
 *
 * <p>不启动游戏：开一个隐藏窗口拿 OpenGL 3.2 core 上下文，编译/链接
 * {@code magic_starfield} 与 {@code magic_constellation}，按游戏里的
 * 混合语义画一遍，读回像素。用来同时做「编译检查」和「观感检查」。</p>
 *
 * <p>用法: {@code java MagicPreview <shaderDir> <outDir> [timeSeconds]}</p>
 */
public final class MagicPreview {

    static final int W = 1280;
    static final int H = 800;

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        float time = args.length > 2 ? Float.parseFloat(args[2]) : 4.0F;

        GLFW.glfwInit();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(64, 64, "magic-preview", 0L, 0L);
        if (window == 0L) {
            System.out.println("RESULT=NO_WINDOW");
            return;
        }
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();

        int starProg = link(dir.resolve("magic_starfield.vsh"), dir.resolve("magic_starfield.fsh"));
        int conProg = link(dir.resolve("magic_constellation.vsh"), dir.resolve("magic_constellation.fsh"));

        int[] target = makeTarget(W, H);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target[1]);
        GL11.glViewport(0, 0, W, H);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glClearColor(0F, 0F, 0F, 1F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        // ---- background ----
        int quadVao = makeQuadVao();
        GL20.glUseProgram(starProg);
        matrices(starProg);
        flt(starProg, "uTime", time);
        vec2(starProg, "uSize", W, H);
        flt(starProg, "uIntensity", 1F);
        GL30.glBindVertexArray(quadVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        // ---- constellation links (same node layout as MagicScreen: five slots in a W) ----
        GL20.glUseProgram(conProg);
        matrices(conProg);
        flt(conProg, "uTime", time);
        vec4(conProg, "uColor", 0.93F, 0.72F, 0.36F, 0.85F);
        flt(conProg, "uIntensity", 0.85F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        int conVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(conVao);
        int conVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, conVbo);
        int[] xs = {400, 520, 640, 760, 880};
        int[] ys = {500, 380, 500, 380, 500};
        uploadSegments(xs, ys, 7F);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, (xs.length - 1) * 6);

        GL11.glDisable(GL11.GL_BLEND);

        BufferedImage image = readPixels();
        Files.createDirectories(out);
        File file = out.resolve("magic-gui.png").toFile();
        ImageIO.write(image, "png", file);
        System.out.println("WROTE " + file.getAbsolutePath());

        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    // ── draw helpers ──────────────────────────────────────────────

    private static int makeQuadVao() {
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        float[] verts = {
                // x, y, z, u, v
                -1F, -1F, 0F, 0F, 1F,
                1F, -1F, 0F, 1F, 1F,
                1F, 1F, 0F, 1F, 0F,
                -1F, -1F, 0F, 0F, 1F,
                1F, 1F, 0F, 1F, 0F,
                -1F, 1F, 0F, 0F, 0F,
        };
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(verts.length);
            buffer.put(verts).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        }
        int pos = 0;
        int uv = 1;
        GL20.glEnableVertexAttribArray(pos);
        GL20.glVertexAttribPointer(pos, 3, GL11.GL_FLOAT, false, 5 * 4, 0L);
        GL20.glEnableVertexAttribArray(uv);
        GL20.glVertexAttribPointer(uv, 2, GL11.GL_FLOAT, false, 5 * 4, 3L * 4);
        return vao;
    }

    private static void uploadSegments(int[] xs, int[] ys, float halfWidth) {
        int count = (xs.length - 1) * 6;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(count * 5);
            for (int i = 0; i + 1 < xs.length; i++) {
                float x1 = xs[i];
                float y1 = ys[i];
                float x2 = xs[i + 1];
                float y2 = ys[i + 1];
                float dx = x2 - x1;
                float dy = y2 - y1;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                float nx = -dy / len * halfWidth;
                float ny = dx / len * halfWidth;
                float uEnd = len / 64F;

                tri(buffer, x1 - nx, y1 - ny, 0F, 1F, x1 + nx, y1 + ny, 0F, 0F, x2 + nx, y2 + ny, uEnd, 0F);
                tri(buffer, x1 - nx, y1 - ny, 0F, 1F, x2 + nx, y2 + ny, uEnd, 0F, x2 - nx, y2 - ny, uEnd, 1F);
            }
            buffer.flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
        }
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * 4, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * 4, 3L * 4);
    }

    private static void tri(FloatBuffer b, float x0, float y0, float u0, float v0,
                            float x1, float y1, float u1, float v1,
                            float x2, float y2, float u2, float v2) {
        b.put(ndcX(x0)).put(ndcY(y0)).put(0F).put(u0).put(v0);
        b.put(ndcX(x1)).put(ndcY(y1)).put(0F).put(u1).put(v1);
        b.put(ndcX(x2)).put(ndcY(y2)).put(0F).put(u2).put(v2);
    }

    private static float ndcX(float px) {
        return px / W * 2F - 1F;
    }

    private static float ndcY(float py) {
        return 1F - py / H * 2F;
    }

    // ── GL helpers ────────────────────────────────────────────────

    private static int[] makeTarget(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, tex, 0);
        return new int[]{tex, fbo};
    }

    private static BufferedImage readPixels() {
        ByteBuffer pixels = ByteBuffer.allocateDirect(W * H * 4);
        GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = ((H - 1 - y) * W + x) * 4;
                int r = pixels.get(i) & 0xFF;
                int g = pixels.get(i + 1) & 0xFF;
                int b = pixels.get(i + 2) & 0xFF;
                int a = pixels.get(i + 3) & 0xFF;
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private static void matrices(int program) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer identity = stack.mallocFloat(16);
            for (int i = 0; i < 16; i++) {
                identity.put(i % 5 == 0 ? 1F : 0F);
            }
            identity.flip();
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "ProjMat"), false, identity);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "ModelViewMat"), false, identity);
        }
    }

    private static void flt(int program, String name, float v) {
        GL20.glUniform1f(GL20.glGetUniformLocation(program, name), v);
    }

    private static void vec2(int program, String name, float x, float y) {
        GL20.glUniform2f(GL20.glGetUniformLocation(program, name), x, y);
    }

    private static void vec4(int program, String name, float r, float g, float b, float a) {
        GL20.glUniform4f(GL20.glGetUniformLocation(program, name), r, g, b, a);
    }

    private static int link(Path vs, Path fs) throws Exception {
        int v = compile(vs, GL20.GL_VERTEX_SHADER);
        int f = compile(fs, GL20.GL_FRAGMENT_SHADER);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, v);
        GL20.glAttachShader(program, f);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            throw new IllegalStateException("LINK FAIL " + vs + " + " + fs + "\n"
                    + GL20.glGetProgramInfoLog(program));
        }
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        System.out.println("LINK OK " + vs.getFileName() + " + " + fs.getFileName());
        return program;
    }

    private static int compile(Path path, int type) throws Exception {
        String source = Files.readString(path);
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            throw new IllegalStateException("COMPILE FAIL " + path + "\n"
                    + GL20.glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
