import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 绯红誓约背板的离屏预览台 —— 不启动游戏，直接把
 * {@code rendertype_crimson_backplate.vsh/.fsh} 画成 PNG。
 *
 * <h3>它复现了什么</h3>
 * <ul>
 *   <li>几何：与 {@code CrimsonVowBackplateMesh} 逐字节一致的 48 段扇形
 *       （144 顶点，POSITION_TEX，UV0 = (径向 r, 裸角度 θ)）。</li>
 *   <li>变换：相机空间里的一块 billboard，缩放 = config Scale ÷ 0.86，
 *       中心在相机前方 <b>4.0（第三人称距离）+ 0.65（HeadOffset）</b> 格。
 *       与 {@code CrimsonVowBackplateRig.transform} 在 MV = 单位阵时的结果一致。</li>
 *   <li>两遍绘制：底遍 srcalpha / 1-srcalpha，亮遍加色（SRC_ALPHA, ONE），
 *       与 {@code SplendidingShaders} 的两个 RenderType 同语义。</li>
 *   <li><b>玩家剪影</b>：按 1.8 格高、第三人称 4 格距离投影出来的深色人形。
 *       背板在玩家身后，所以中下部会被身体挡住 —— 设计必须按"被挡掉约 1/3"
 *       的真实构图来评判，不能只看一张完整的圆。</li>
 * </ul>
 *
 * <h3>用法</h3>
 * <pre>
 *   tools\shader-preview\backplate.ps1                        # 用 src 里现役的着色器
 *   tools\shader-preview\backplate.ps1 -ShaderDir &lt;目录&gt;       # 跑候选着色器
 * </pre>
 *
 * <p>产物（默认 1280×720，inworld 系列带剪影）：</p>
 * <ul>
 *   <li>{@code inworld_t000/t045/t090.png} —— 三个时间点，看动画是否活着</li>
 *   <li>{@code inworld_block050/100.png} —— 格挡强度</li>
 *   <li>{@code inworld_reveal000/.../100.png} —— 展开进度</li>
 *   <li>{@code inworld_daysky.png} —— 亮背景对比度检查</li>
 *   <li>{@code closeup_t045.png} —— 完整图案（不带剪影），看"形状"</li>
 *   <li>{@code pass_base.png / pass_glow.png} —— 两遍各画了什么</li>
 * </ul>
 */
public class BackplatePreview {

    // ── 画布与实机参数 ──────────────────────────────────────────
    static final int W = 1280, H = 720;
    /** MC 默认第三人称相机距离（格）。 */
    static final float CAM_DIST = 4.0f;
    /** config crimsonBackplateHeadOffset。 */
    static final float HEAD_OFFSET = 0.65f;
    /** config crimsonBackplateScale（可被第 4 个命令行参数覆盖，做尺寸对比用）。 */
    static float CONFIG_SCALE = 1.3f;
    /** 着色器可见半径上限（Rig.SHADER_VISIBLE_RADIUS，可被第 5 个参数覆盖）。 */
    static float SHADER_VISIBLE_R = 0.86f;
    static final float EYE_HEIGHT = 1.62f;
    static final float FOV = 70f;

    /** 与 CrimsonVowBackplateMesh 一致：48 段，边缘半径 √2。 */
    static final int SIDES = 48;
    static final float EDGE_RADIUS = 1.41421356f;
    static final int STRIDE = 5;   // x,y,z,u,v

    static int prog;
    static int flatProg;
    static int fanVao, fanVbo;
    static int fbo;

    static final float[] IDENTITY = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1 };

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: BackplatePreview <shaderDir> <outDir> [shaderBase] [scale] [visibleR]");
            return;
        }
        Path shaderDir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String base = args.length > 2 ? args[2] : "rendertype_crimson_backplate";
        if (args.length > 3) CONFIG_SCALE = Float.parseFloat(args[3]);
        if (args.length > 4) SHADER_VISIBLE_R = Float.parseFloat(args[4]);
        Files.createDirectories(out);

        if (!GLFW.glfwInit()) { System.out.println("RESULT=NO_GLFW"); return; }
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long win = GLFW.glfwCreateWindow(64, 64, "backplate", 0L, 0L);
        if (win == 0L) { System.out.println("RESULT=NO_WINDOW"); return; }
        GLFW.glfwMakeContextCurrent(win);
        GL.createCapabilities();

        prog = link(shaderDir.resolve(base + ".vsh"), shaderDir.resolve(base + ".fsh"));
        if (prog == 0) { System.out.println("RESULT=SHADER_FAIL"); return; }
        flatProg = buildFlat();
        buildFan();

        fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        int color = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, W, H, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, color, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            System.out.println("RESULT=FBO_INCOMPLETE");
            return;
        }

        System.out.println("shaderDir = " + shaderDir);
        System.out.println("canvas    = " + W + "x" + H + "  fov=" + FOV
                + "  plateDepth=" + (CAM_DIST + HEAD_OFFSET) + "  scale=" + (CONFIG_SCALE / SHADER_VISIBLE_R));

        // ── 实机构图系列（带玩家剪影）──
        inWorld(out, "inworld_t000", 0f, 0f, 1f, BG_NIGHT, true);
        inWorld(out, "inworld_t045", 45f, 0f, 1f, BG_NIGHT, true);
        inWorld(out, "inworld_t090", 90f, 0f, 1f, BG_NIGHT, true);
        inWorld(out, "inworld_block050", 45f, 0.5f, 1f, BG_NIGHT, true);
        inWorld(out, "inworld_block100", 45f, 1.0f, 1f, BG_NIGHT, true);
        inWorld(out, "inworld_reveal000", 45f, 0f, 0.0f, BG_NIGHT, true);
        inWorld(out, "inworld_reveal040", 45f, 0f, 0.4f, BG_NIGHT, true);
        inWorld(out, "inworld_reveal070", 45f, 0f, 0.7f, BG_NIGHT, true);
        inWorld(out, "inworld_reveal100", 45f, 0f, 1.0f, BG_NIGHT, true);
        inWorld(out, "inworld_daysky", 45f, 0f, 1f, BG_DAY, true);
        inWorld(out, "inworld_nosilh", 45f, 0f, 1f, BG_NIGHT, false);

        // ── 两遍各自画了什么 ──
        passes(out, "pass_base", 45f, 0f, true);
        passes(out, "pass_glow", 45f, 0f, false);

        // ── 完整图案特写（无剪影）：这才看得出"形状"本身 ──
        closeup(out, "closeup_t045", 45f, 0f, 1f);
        closeup(out, "closeup_block100", 45f, 1f, 1f);
        closeup(out, "closeup_t200", 200f, 0f, 1f);
        closeup(out, "closeup_glowonly", 45f, 0f, 1f, true);

        GLFW.glfwDestroyWindow(win);
        GLFW.glfwTerminate();
        System.out.println("DONE -> " + out.toAbsolutePath());
    }

    // ══════════════════════════════════════════════════════════════
    //  背景
    // ══════════════════════════════════════════════════════════════

    static final int[] BG_NIGHT = { 0x11141d, 0x05060a };
    static final int[] BG_DAY = { 0xcfe3f5, 0x7fa8d8 };

    // ══════════════════════════════════════════════════════════════
    //  三类镜头
    // ══════════════════════════════════════════════════════════════

    static void inWorld(Path out, String name, float time, float block, float reveal,
                        int[] bg, boolean silhouette) throws Exception {
        beginFrame(bg);
        float[] proj = perspective(FOV, (float) W / H, 0.05f, 1024f);
        float depth = CAM_DIST + HEAD_OFFSET;
        float s = CONFIG_SCALE / SHADER_VISIBLE_R;
        drawPass(proj, 0f, 0f, -depth, s, time, block, reveal, 1.0f, true, false);
        drawPass(proj, 0f, 0f, -depth, s, time, block, reveal,
                0.55f + 1.05f * block, false, true);
        if (silhouette) drawPlayerSilhouette();
        save(out.resolve(name + ".png"));
        System.out.println("  " + name + "  time=" + time + " block=" + block
                + " reveal=" + reveal + (silhouette ? "" : " (no silhouette)"));
    }

    /** 单遍出图（看"底"/"亮"各画了什么）。 */
    static void passes(Path out, String name, float time, float block, boolean basePass)
            throws Exception {
        beginFrame(BG_NIGHT);
        float[] proj = perspective(FOV, (float) W / H, 0.05f, 1024f);
        float depth = CAM_DIST + HEAD_OFFSET;
        float s = CONFIG_SCALE / SHADER_VISIBLE_R;
        drawPass(proj, 0f, 0f, -depth, s, time, block, 1f, 1f, basePass, !basePass);
        save(out.resolve(name + ".png"));
    }

    /** 特写：正交投影，盘子占满画布高度（不带剪影）。 */
    static void closeup(Path out, String name, float time, float block, float reveal)
            throws Exception {
        closeup(out, name, time, block, reveal, false);
    }

    static void closeup(Path out, String name, float time, float block, float reveal,
                        boolean glowOnly) throws Exception {
        beginFrame(BG_NIGHT);
        float aspect = (float) W / H;
        float hh = 0.92f;                       // 垂直半高 → 盘子直径约占 0.86/0.92 ≈ 93% 高度
        float[] ortho = ortho(-hh * aspect, hh * aspect, -hh, hh, -1f, 1f);
        if (!glowOnly) {
            drawPass(ortho, 0f, 0f, 0f, 1f, time, block, reveal, 1.0f, true, false);
        }
        drawPass(ortho, 0f, 0f, 0f, 1f, time, block, reveal,
                glowOnly ? 1.0f : 0.55f + 1.05f * block, false, true);
        save(out.resolve(name + ".png"));
    }

    // ══════════════════════════════════════════════════════════════
    //  绘制
    // ══════════════════════════════════════════════════════════════

    static void beginFrame(int[] bg) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, W, H);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        drawBackground(bg);
    }

    static void drawBackground(int[] bg) {
        GL20.glUseProgram(flatProg);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        float[] top = rgb(bg[0]);
        float[] bot = rgb(bg[1]);
        float[] v = {
                -1, -1, bot[0], bot[1], bot[2],
                -1, 1, top[0], top[1], top[2],
                1, 1, top[0], top[1], top[2],
                -1, -1, bot[0], bot[1], bot[2],
                1, 1, top[0], top[1], top[2],
                1, -1, bot[0], bot[1], bot[2] };
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(v.length).put(v).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 5 * 4, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 5 * 4, 2L * 4);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }

    /**
     * 画一遍背板。
     *
     * @param cx,cy,cz 相机空间中心
     * @param s        矩阵缩放（几何坐标 × s）
     * @param basePass true = 底遍（alpha 混合），false = 亮遍（加色）
     */
    static void drawPass(float[] proj, float cx, float cy, float cz, float s,
                         float time, float block, float reveal, float intensity,
                         boolean basePass, boolean glowPass) {
        GL20.glUseProgram(prog);
        matrix(prog, "ProjMat", proj);
        matrix(prog, "ModelViewMat", IDENTITY);
        flt(prog, "Time", time);
        flt(prog, "Reveal", reveal);
        flt(prog, "Block", block);
        flt(prog, "Intensity", intensity);
        flt(prog, "PixelWidth", 1f / W);
        vec2(prog, "RingRadius", 0.80f, 0.60f);
        vec3(prog, "Spin", 0.55f, 1.0f, 2.4f);
        vec3(prog, "VowColor", 0.66f, 0.055f, 0.20f);
        vec3(prog, "AccentColor", 0.96f, 0.50f, 1.0f);
        // 候选着色器可以用的可选 uniform（现役着色器里没有 → 静默跳过）
        flt(prog, "GlowPass", glowPass ? 1f : 0f);
        flt(prog, "Aspect", (float) W / H);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        if (basePass) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        }

        GL30.glBindVertexArray(fanVao);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, fanVbo);
        FloatBuffer buf = BufferUtils.createFloatBuffer(FAN_VERTICES.length);
        for (int i = 0; i < FAN_VERTICES.length; i += STRIDE) {
            buf.put(FAN_VERTICES[i] * s + cx);
            buf.put(FAN_VERTICES[i + 1] * s + cy);
            buf.put(cz);
            buf.put(FAN_VERTICES[i + 3]);
            buf.put(FAN_VERTICES[i + 4]);
        }
        buf.flip();
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, buf);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, FAN_VERTICES.length / STRIDE);
        GL30.glBindVertexArray(0);
    }

    /** "玩家剪影"：头 + 躯干 + 两臂 + 两腿，屏幕空间方块。 */
    static void drawPlayerSilhouette() {
        double pxPerBlock = (H * 0.5) / (CAM_DIST * Math.tan(Math.toRadians(FOV) / 2.0));
        double cx = W * 0.5;
        double cy = H * 0.5;
        List<float[]> quads = new ArrayList<>();
        float eye = EYE_HEIGHT;
        addQuad(quads, -0.25f, 1.875f - eye, 0.25f, 1.406f - eye);   // 头
        addQuad(quads, -0.25f, 1.406f - eye, 0.25f, 0.703f - eye);   // 躯干
        addQuad(quads, 0.25f, 1.406f - eye, 0.50f, 0.703f - eye);    // 右臂
        addQuad(quads, -0.50f, 1.406f - eye, -0.25f, 0.703f - eye);  // 左臂
        addQuad(quads, 0.0f, 0.703f - eye, 0.25f, -eye);             // 右腿
        addQuad(quads, -0.25f, 0.703f - eye, 0.0f, -eye);            // 左腿

        GL20.glUseProgram(flatProg);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float[] col = { 0.045f, 0.045f, 0.055f };
        float[] verts = new float[quads.size() * 6 * 5];
        int p = 0;
        for (float[] q : quads) {
            float x0 = (float) (cx + q[0] * pxPerBlock);
            float x1 = (float) (cx + q[2] * pxPerBlock);
            float y0 = (float) (cy + q[1] * pxPerBlock);
            float y1 = (float) (cy + q[3] * pxPerBlock);
            float[] a = ndc(x0, y0), b = ndc(x0, y1), c = ndc(x1, y1), d = ndc(x1, y0);
            p = put(verts, p, a, col); p = put(verts, p, b, col); p = put(verts, p, c, col);
            p = put(verts, p, a, col); p = put(verts, p, c, col); p = put(verts, p, d, col);
        }
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(verts.length).put(verts).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 5 * 4, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 5 * 4, 2L * 4);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verts.length / 5);
        GL30.glBindVertexArray(0);
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }

    static void addQuad(List<float[]> list, float x0, float y0, float x1, float y1) {
        list.add(new float[] { x0, y0, x1, y1 });
    }

    static float[] ndc(float px, float py) {
        return new float[] { px / (W * 0.5f) - 1f, py / (H * 0.5f) - 1f };
    }

    static int put(float[] dst, int p, float[] xy, float[] rgb) {
        dst[p++] = xy[0];
        dst[p++] = xy[1];
        dst[p++] = rgb[0];
        dst[p++] = rgb[1];
        dst[p++] = rgb[2];
        return p;
    }

    // ══════════════════════════════════════════════════════════════
    //  几何（与 CrimsonVowBackplateMesh.bake() 完全一致）
    // ══════════════════════════════════════════════════════════════

    static final float[] FAN_VERTICES = bakeFan();

    static float[] bakeFan() {
        float[] data = new float[SIDES * 3 * STRIDE];
        int p = 0;
        for (int s = 0; s < SIDES; s++) {
            float thetaA = (float) (s * (Math.PI * 2.0) / SIDES);
            float thetaB = (float) ((s + 1) * (Math.PI * 2.0) / SIDES);
            // 圆心：UV0 = (0, 0)
            data[p++] = 0f; data[p++] = 0f; data[p++] = 0f;
            data[p++] = 0f; data[p++] = 0f;
            // 边缘 A：UV0 传**单位平面局部坐标**（不是 r,θ）
            float ax = (float) Math.cos(thetaA) * EDGE_RADIUS;
            float ay = (float) Math.sin(thetaA) * EDGE_RADIUS;
            data[p++] = ax; data[p++] = ay; data[p++] = 0f;
            data[p++] = ax; data[p++] = ay;
            // 边缘 B
            float bx = (float) Math.cos(thetaB) * EDGE_RADIUS;
            float by = (float) Math.sin(thetaB) * EDGE_RADIUS;
            data[p++] = bx; data[p++] = by; data[p++] = 0f;
            data[p++] = bx; data[p++] = by;
        }
        return data;
    }

    static void buildFan() {
        fanVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(fanVao);
        fanVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, fanVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) FAN_VERTICES.length * 4L, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE * 4, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE * 4, 3L * 4);
        GL30.glBindVertexArray(0);
    }

    // ══════════════════════════════════════════════════════════════
    //  GL 小工具
    // ══════════════════════════════════════════════════════════════

    static void save(Path path) throws Exception {
        GL11.glFinish();
        ByteBuffer buf = BufferUtils.createByteBuffer(W * H * 4);
        GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        long sum = 0;
        int max = 0, bright = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = ((H - 1 - y) * W + x) * 4;
                int r = buf.get(i) & 0xFF, g = buf.get(i + 1) & 0xFF, b = buf.get(i + 2) & 0xFF;
                int lum = Math.max(r, Math.max(g, b));
                sum += lum;
                if (lum > max) max = lum;
                if (lum > 24) bright++;
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", new File(path.toString()));
        System.out.printf("    %-24s max=%3d mean=%6.2f bright=%6d/%d%n",
                path.getFileName(), max, sum / (double) (W * H), bright, W * H);
    }

    static int buildFlat() throws Exception {
        int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vs, "#version 150 core\n"
                + "in vec2 FlatPos;\nin vec3 FlatColor;\nout vec3 vColor;\n"
                + "void main(){ gl_Position = vec4(FlatPos, 0.0, 1.0); vColor = FlatColor; }\n");
        GL20.glCompileShader(vs);
        int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fs, "#version 150 core\n"
                + "in vec3 vColor;\nout vec4 fragColor;\n"
                + "void main(){ fragColor = vec4(vColor, 1.0); }\n");
        GL20.glCompileShader(fs);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glBindAttribLocation(p, 0, "FlatPos");
        GL20.glBindAttribLocation(p, 1, "FlatColor");
        GL30.glBindFragDataLocation(p, 0, "fragColor");
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            System.out.println("FLAT LINK FAIL: " + GL20.glGetProgramInfoLog(p));
        }
        return p;
    }

    static int link(Path vsPath, Path fsPath) throws Exception {
        int v = shader(vsPath, GL20.GL_VERTEX_SHADER);
        int f = shader(fsPath, GL20.GL_FRAGMENT_SHADER);
        if (v == 0 || f == 0) return 0;
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glBindAttribLocation(p, 0, "Position");
        GL20.glBindAttribLocation(p, 1, "UV0");
        GL30.glBindFragDataLocation(p, 0, "fragColor");
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            System.out.println("LINK FAIL: " + GL20.glGetProgramInfoLog(p));
            return 0;
        }
        return p;
    }

    static int shader(Path path, int type) throws Exception {
        if (!Files.exists(path)) {
            System.out.println("MISSING shader: " + path);
            return 0;
        }
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, Files.readString(path));
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            System.out.println("COMPILE FAIL " + path + ":\n" + GL20.glGetShaderInfoLog(s));
            return 0;
        }
        return s;
    }

    static float[] perspective(float fovDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovDeg) / 2.0));
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = 2f * far * near / (near - far);
        return m;
    }

    static float[] ortho(float l, float r, float b, float t, float n, float f) {
        float[] m = new float[16];
        m[0] = 2f / (r - l);
        m[5] = 2f / (t - b);
        m[10] = -2f / (f - n);
        m[12] = -(r + l) / (r - l);
        m[13] = -(t + b) / (t - b);
        m[14] = -(f + n) / (f - n);
        m[15] = 1f;
        return m;
    }

    static float[] rgb(int hex) {
        return new float[] {
                ((hex >> 16) & 0xFF) / 255f,
                ((hex >> 8) & 0xFF) / 255f,
                (hex & 0xFF) / 255f };
    }

    static void matrix(int p, String n, float[] m) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l < 0) return;
        FloatBuffer b = BufferUtils.createFloatBuffer(16);
        b.put(m).flip();
        GL20.glUniformMatrix4fv(l, false, b);
    }

    static void flt(int p, String n, float v) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l < 0) return;
        GL20.glUniform1f(l, v);
    }

    static void vec2(int p, String n, float x, float y) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l < 0) return;
        GL20.glUniform2f(l, x, y);
    }

    static void vec3(int p, String n, float x, float y, float z) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l < 0) return;
        GL20.glUniform3f(l, x, y, z);
    }
}
