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

/**
 * 绯红誓约「法阵」离屏预览台（不启动游戏）。
 *
 * <p>把 {@code rendertype_crimson_sigil.fsh} 的 <b>uMode = 0（蓄力法阵）</b>与
 * <b>uMode = 1（巨型法阵）</b>按实机几何 / 相机 / 加法混合画成 PNG：</p>
 * <ul>
 *   <li>几何：与 {@code CrimsonVowRenderSupport.emitDisc} 逐字节一致
 *       （6 环 × 96 扇区，POSITION_COLOR_NORMAL；Color.rg = 径向/角度归一化，
 *       Color 是 u8、Normal 是 i8 —— 这里连量化一起复现，免得离屏看不见的
 *       锯齿在实机里出现）；</li>
 *   <li>变换：Java 侧把 translate(-camera) 折进 pose，所以顶点是<b>视空间</b>
 *       坐标；这里直接算 {@code R·(world − cam)} 再交给 {@code ProjMat}；</li>
 *   <li>混合：实机是加法（ONE, ONE）+ 关深度写 + 不写深度。</li>
 * </ul>
 *
 * <h3>镜头</h3>
 * <ul>
 *   <li>{@code top}  —— 正上方俯视，用来评判图案本身（透视最小）；</li>
 *   <li>{@code in}   —— 玩家站在阵内、眼睛高度看向阵心（实机最常见的读法）；</li>
 *   <li>{@code out}  —— 玩家站在阵外斜看（召唤出去第一眼看到的构图）；</li>
 *   <li>{@code charge} —— 蓄力阵在玩家脚下，第三人称后上方俯视。</li>
 * </ul>
 *
 * <p>用法：</p>
 * <pre>
 *   .\tools\shader-preview\sigil.ps1                                  # 现役着色器
 *   .\tools\shader-preview\sigil.ps1 -ShaderDir build\bp-candidates\sigil
 * </pre>
 */
public class SigilPreview {

    static final int W = 1280, H = 720;
    static final float FOV = 70f;

    /** 与 CrimsonVowRenderSupport 一致。 */
    static final int DISC_SEGMENTS = 96;
    static final int DISC_RINGS = 6;
    static final float HALO_RATIO = 1.18f;

    static int prog;
    static int vao, posVbo, colorVbo, normalVbo;
    static int fbo;

    /** 当前这帧的顶点数（每帧重建）。 */
    static int vertexCount;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: SigilPreview <shaderDir> <outDir> [shaderBase]");
            return;
        }
        Path shaderDir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String base = args.length > 2 ? args[2] : "rendertype_crimson_sigil";
        Files.createDirectories(out);

        if (!GLFW.glfwInit()) { System.out.println("RESULT=NO_GLFW"); return; }
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long win = GLFW.glfwCreateWindow(64, 64, "sigil", 0L, 0L);
        if (win == 0L) { System.out.println("RESULT=NO_WINDOW"); return; }
        GLFW.glfwMakeContextCurrent(win);
        GL.createCapabilities();

        prog = link(shaderDir.resolve(base + ".vsh"), shaderDir.resolve(base + ".fsh"));
        if (prog == 0) { System.out.println("RESULT=SHADER_FAIL"); return; }
        buildBuffers();
        fbo = createFbo();

        System.out.println("shaderDir = " + shaderDir);
        System.out.println("canvas    = " + W + "x" + H + "  fov=" + FOV);

        // ══════════════════════════════════════════════════════════
        //  蓄力法阵（uMode 0）：半径 0.85 → 4.6，随蓄力扩散
        // ══════════════════════════════════════════════════════════
        chargeShot(out, "charge_00_early",  1.10f, 0.10f, 0.0f, "charge");
        chargeShot(out, "charge_01_mid",    2.60f, 0.55f, 0.0f, "charge");
        chargeShot(out, "charge_02_full",   4.60f, 1.00f, 0.0f, "charge");
        chargeShot(out, "charge_03_overload", 4.60f, 1.00f, 0.0f, "charge", 0.55f);
        chargeShot(out, "charge_04_top",    3.60f, 0.80f, 0.0f, "top");
        chargeShot(out, "charge_05_t2",     3.60f, 0.80f, 2.0f, "charge");

        // ══════════════════════════════════════════════════════════
        //  巨型法阵（uMode 1）：半径 14，20 秒生命周期
        // ══════════════════════════════════════════════════════════
        fieldShot(out, "field_00_deploy", 0.06f, 0.0f, 0.0f, 0.0f, -1.0f, "out");
        fieldShot(out, "field_01_mid",    0.50f, 0.0f, 0.0f, 0.0f, -1.0f, "in");
        fieldShot(out, "field_02_top",    0.50f, 0.0f, 0.0f, 0.0f, -1.0f, "top");
        fieldShot(out, "field_03_out",    0.50f, 0.0f, 0.0f, 0.0f, -1.0f, "out");
        fieldShot(out, "field_04_wave30", 0.50f, 0.30f, 0.0f, 0.0f, -1.0f, "top");
        fieldShot(out, "field_05_wave70", 0.50f, 0.70f, 0.0f, 0.0f, -1.0f, "in");
        fieldShot(out, "field_06_warn",   0.80f, 0.0f, 0.40f, 0.0f, -1.0f, "in");
        fieldShot(out, "field_07_finale", 0.95f, 0.0f, 0.85f, 0.85f, -1.0f, "out");
        fieldShot(out, "field_08_impact", 0.99f, 0.0f, 0.95f, 1.00f, 0.30f, "in");
        fieldShot(out, "field_09_altseed", 0.50f, 0.0f, 0.0f, 0.0f, -1.0f, "top", 3.7f);

        GLFW.glfwDestroyWindow(win);
        GLFW.glfwTerminate();
        System.out.println("DONE -> " + out.toAbsolutePath());
    }

    // ══════════════════════════════════════════════════════════════
    //  镜头
    // ══════════════════════════════════════════════════════════════

    /** 蓄力阵：第三人称后上方看向脚下。半径从 1 长到 4.6。 */
    static void chargeShot(Path out, String name, float radius, float charge,
                           float time, String view, float... extra) throws Exception {
        float overload = extra.length > 0 ? extra[0] : 0f;
        // 玩家身高 1.8、相机后 3.4 格、高 2.4 格
        float camDist = radius * 1.15f + 2.4f;
        float camY = 2.4f;
        float[] cam = { 0f, camY, camDist };
        float[] target = { 0f, 0f, 0f };
        shot(out, name, radius, charge, 0f, 0f, 0f, 0f, 0f, time, overload,
                view, cam, target, radius * HALO_RATIO * 1.10f);
    }

    /** 巨型法阵：半径 14。 */
    static void fieldShot(Path out, String name, float life, float wave,
                          float warn, float finale, float impactAge, String view,
                          float... extra) throws Exception {
        float seed = extra.length > 0 ? extra[0] : 0f;
        float radius = 14f;
        float[] cam;
        float[] target = { 0f, 0f, 0f };
        switch (view) {
            case "top" -> cam = new float[] { 0f, radius * 2.35f, 0.01f };
            case "in"  -> cam = new float[] { 0f, 1.62f, radius * 0.55f };
            default    -> cam = new float[] { 0f, 3.2f, radius * 1.55f };
        }
        shot(out, name, radius, 0f, 0f, wave, warn, finale, impactAge, life, 0f,
                view, cam, target, radius * HALO_RATIO * 1.02f);
    }

    // ══════════════════════════════════════════════════════════════
    //  一站
    // ══════════════════════════════════════════════════════════════

    /**
     * @param radius     逻辑半径（格）
     * @param charge     蓄力进度 0..1（uMode 0）
     * @param pulse      扩散波相位（uMode 1）
     * @param warn       终末预警 0..1
     * @param finale     终末进度 0..1
     * @param impactAge  触地后秒数（<0 = 未触地）
     * @param intensity  uIntensity
     * @param time       uTime（秒）
     * @param overload   蓄力过载闪烁强度（0 = 关；用来对照）
     */
    static void shot(Path out, String name, float radius, float charge,
                     float unused0, float pulse, float warn, float finale,
                     float impactAge, float time, float overload,
                     String view, float[] cam, float[] target,
                     float meshRadius) throws Exception {
        beginFrame();
        float[] proj = perspective(FOV, (float) W / H, 0.05f, 2000f);
        float[] R = viewBasis(cam, target);

        // 视基同时给 vsh 的柱面波动用（圆盘模式 wobble=0，不会读到，但给全）
        float[] basis = new float[9];
        for (int i = 0; i < 3; i++) { basis[i * 3] = R[0 * 3 + i]; basis[i * 3 + 1] = R[1 * 3 + i]; basis[i * 3 + 2] = R[2 * 3 + i]; }

        GL20.glUseProgram(prog);
        matrix(prog, "ProjMat", proj);
        flt(prog, "uTime", time);
        flt(prog, "uIntensity", 1.0f);
        flt(prog, "uMode", radius > 8f ? 1f : 0f);
        flt(prog, "uRadius", radius);
        flt(prog, "uMeshRadius", meshRadius);
        flt(prog, "uHalo", HALO_RATIO);
        flt(prog, "uCharge", charge);
        flt(prog, "uPulse", pulse);
        flt(prog, "uProgress", 0.5f);
        flt(prog, "uFinale", finale);
        flt(prog, "uSeed", 0f);
        flt(prog, "uWarn", warn);
        flt(prog, "uImpactAge", impactAge);
        // 柱面/裂口模式的 uniform 一律归零，避免读到随机初值
        flt(prog, "uImpact", 0f);
        flt(prog, "uLayer", 0f);
        flt(prog, "uBeamWobble", 0f);
        flt(prog, "uBeamRadius", 0f);
        flt(prog, "uBeamHead", 0f);
        flt(prog, "uBeamSpan", 1f);
        flt(prog, "uBeamBase", 0f);
        vec3(prog, "uBeamAxisVS", 0f, 0f, 0f);
        vec3(prog, "uBeamAxisDir", 0f, 1f, 0f);
        vec3(prog, "uViewRight", basis[0], basis[1], basis[2]);
        vec3(prog, "uViewUp", basis[3], basis[4], basis[5]);
        vec3(prog, "uViewFwd", basis[6], basis[7], basis[8]);
        vec3(prog, "uColorA", 1.000f, 0.310f, 0.722f);
        vec3(prog, "uColorB", 0.608f, 0.302f, 1.000f);
        vec3(prog, "uColorC", 0.910f, 0.651f, 1.000f);

        // 复用 uIntensity 做过载对照：把 1.0 提到 1+overload
        if (overload > 0f) flt(prog, "uIntensity", 1.0f + overload);

        buildDiscGeometry(R, cam, radius, meshRadius, view);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);   // 实机：加法

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);

        save(out.resolve(name + ".png"));
        System.out.println("  " + name + "  view=" + view + " radius=" + radius
                + " charge=" + charge + " wave=" + pulse
                + " warn=" + warn + " finale=" + finale + " impactAge=" + impactAge);
    }

    // ══════════════════════════════════════════════════════════════
    //  几何：与 CrimsonVowRenderSupport.emitDisc 一致
    // ══════════════════════════════════════════════════════════════

    static void buildDiscGeometry(float[] R, float[] cam, float radius,
                                  float meshRadius, String view) {
        // 每次重建 —— 顶点不多（6*96*6=3456），避免增量更新的坑。
        int tris = DISC_RINGS * DISC_SEGMENTS * 2;
        int verts = tris * 3;
        FloatBuffer pos = BufferUtils.createFloatBuffer(verts * 3);
        ByteBuffer col = BufferUtils.createByteBuffer(verts * 4);
        ByteBuffer nor = BufferUtils.createByteBuffer(verts * 3);

        // 世界 y：贴地。俯视时把 y 稍微抬一点避免与"地面"重叠（这里没有地面，
        // 无所谓，但保持与实机 groundY + 0.03 一致）。
        float y = 0.0f;
        for (int ring = 0; ring < DISC_RINGS; ring++) {
            float r0 = ring / (float) DISC_RINGS;
            float r1 = (ring + 1) / (float) DISC_RINGS;
            for (int seg = 0; seg < DISC_SEGMENTS; seg++) {
                float a0 = seg / (float) DISC_SEGMENTS;
                float a1 = (seg + 1) / (float) DISC_SEGMENTS;
                quad(pos, col, nor, R, cam, y, meshRadius,
                        r0, a0, r1, a0, r1, a1);
                quad(pos, col, nor, R, cam, y, meshRadius,
                        r0, a0, r1, a1, r0, a1);
            }
        }
        pos.flip();
        col.flip();
        nor.flip();
        vertexCount = verts;

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, posVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, pos, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, colorVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, col, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 4, 0L);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, nor, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 3, GL11.GL_BYTE, true, 3, 0L);
    }

    static void quad(FloatBuffer pos, ByteBuffer col, ByteBuffer nor,
                     float[] R, float[] cam, float y, float meshRadius,
                     float r0, float a0, float r1, float a0b, float r1b, float a1) {
        vert(pos, col, nor, R, cam, y, meshRadius, r0, a0);
        vert(pos, col, nor, R, cam, y, meshRadius, r1, a0b);
        vert(pos, col, nor, R, cam, y, meshRadius, r1b, a1);
    }

    static void vert(FloatBuffer pos, ByteBuffer col, ByteBuffer nor,
                     float[] R, float[] cam, float y, float meshRadius,
                     float rNorm, float aNorm) {
        float ang = (float) (aNorm * Math.PI * 2.0);
        float wx = (float) Math.cos(ang) * rNorm * meshRadius;
        float wz = (float) Math.sin(ang) * rNorm * meshRadius;
        // 视空间 = R·(world − cam)，R 三行分别是 right/up2/−fwd
        float dx = wx - cam[0];
        float dy = y - cam[1];
        float dz = wz - cam[2];
        pos.put(R[0] * dx + R[1] * dy + R[2] * dz);
        pos.put(R[3] * dx + R[4] * dy + R[5] * dz);
        pos.put(R[6] * dx + R[7] * dy + R[8] * dz);

        // Color.rg = (径向, 角度)，u8 归一化 —— 与 POSITION_COLOR_NORMAL 一致
        col.put((byte) Math.round(Math.max(0f, Math.min(1f, rNorm)) * 255f));
        col.put((byte) Math.round(Math.max(0f, Math.min(1f, aNorm)) * 255f));
        col.put((byte) 0);
        col.put((byte) 255);

        // Normal.xy = 同值，i8 归一化
        nor.put((byte) Math.round(Math.max(-1f, Math.min(1f, rNorm)) * 127f));
        nor.put((byte) Math.round(Math.max(-1f, Math.min(1f, aNorm)) * 127f));
        nor.put((byte) 0);
    }

    // ══════════════════════════════════════════════════════════════
    //  相机
    // ══════════════════════════════════════════════════════════════

    /**
     * 返回 R 的 9 个元素（行主序）：三行分别是
     * right / up2 / −forward，满足 view = R·(world − cam)。
     */
    static float[] viewBasis(float[] cam, float[] target) {
        float fx = target[0] - cam[0];
        float fy = target[1] - cam[1];
        float fz = target[2] - cam[2];
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        // 正上方俯视时 worldUp 与 forward 平行，换一个上向量
        float ux = 0f, uy = 1f, uz = 0f;
        if (Math.abs(fy) > 0.999f) { ux = 0f; uy = 0f; uz = -1f; }
        // right = fwd × up
        float rx = fy * uz - fz * uy;
        float ry = fz * ux - fx * uz;
        float rz = fx * uy - fy * ux;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl; ry /= rl; rz /= rl;
        // up2 = right × fwd
        float u2x = ry * fz - rz * fy;
        float u2y = rz * fx - rx * fz;
        float u2z = rx * fy - ry * fx;
        return new float[] {
                rx, ry, rz,
                u2x, u2y, u2z,
                -fx, -fy, -fz };
    }

    // ══════════════════════════════════════════════════════════════
    //  GL 基础
    // ══════════════════════════════════════════════════════════════

    static void beginFrame() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, W, H);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        // 地面：带一点绿灰的中性底色，接近夜里草地/石地，便于判断加法亮度
        drawGradient(0x1b211a, 0x0a0d0a);
    }

    static void drawGradient(int topHex, int botHex) {
        // 用一个不依赖 shader 的简单办法：清屏 + 画两个大三角形太麻烦，
        // 这里直接清成平均色，再用 8 条横带渐变 —— 视觉上足够。
        float[] top = rgb(topHex), bot = rgb(botHex);
        for (int i = 0; i < 48; i++) {
            float t0 = i / 48f, t1 = (i + 1) / 48f;
            GL11.glScissor(0, (int) (H * t0), W, (int) Math.ceil(H * (t1 - t0)));
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(lerp(bot[0], top[0], t0), lerp(bot[1], top[1], t0),
                    lerp(bot[2], top[2], t0), 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static void save(Path path) throws Exception {
        GL11.glFinish();
        ByteBuffer buf = BufferUtils.createByteBuffer(W * H * 4);
        GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = ((H - 1 - y) * W + x) * 4;
                int r = buf.get(i) & 0xFF, g = buf.get(i + 1) & 0xFF, b = buf.get(i + 2) & 0xFF;
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", new File(path.toString()));
    }

    static void buildBuffers() {
        vao = GL30.glGenVertexArrays();
        posVbo = GL15.glGenBuffers();
        colorVbo = GL15.glGenBuffers();
        normalVbo = GL15.glGenBuffers();
    }

    static int createFbo() {
        int f = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, f);
        int color = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, W, H, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, color, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            System.out.println("FBO INCOMPLETE");
        }
        return f;
    }

    // ── shader / uniform 小工具 ──────────────────────────────

    static int link(Path vsPath, Path fsPath) throws Exception {
        int v = shader(vsPath, GL20.GL_VERTEX_SHADER);
        int f = shader(fsPath, GL20.GL_FRAGMENT_SHADER);
        if (v == 0 || f == 0) return 0;
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glBindAttribLocation(p, 0, "Position");
        GL20.glBindAttribLocation(p, 1, "Color");
        GL20.glBindAttribLocation(p, 2, "Normal");
        GL30.glBindFragDataLocation(p, 0, "fragColor");
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            System.out.println("LINK FAIL: " + GL20.glGetProgramInfoLog(p));
            return 0;
        }
        return p;
    }

    static int shader(Path path, int type) throws Exception {
        if (!Files.exists(path)) { System.out.println("MISSING shader: " + path); return 0; }
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
        m[0] = f / aspect; m[5] = f;
        m[10] = (far + near) / (near - far); m[11] = -1f;
        m[14] = 2f * far * near / (near - far);
        return m;
    }

    static float[] rgb(int hex) {
        return new float[] { ((hex >> 16) & 0xFF) / 255f,
                ((hex >> 8) & 0xFF) / 255f, (hex & 0xFF) / 255f };
    }

    static void matrix(int p, String n, float[] m) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l < 0) return;
        FloatBuffer b = BufferUtils.createFloatBuffer(16).put(m).flip();
        GL20.glUniformMatrix4fv(l, false, b);
    }

    static void flt(int p, String n, float v) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l < 0) return;
        GL20.glUniform1f(l, v);
    }

    static void vec3(int p, String n, float x, float y, float z) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l < 0) return;
        GL20.glUniform3f(l, x, y, z);
    }
}
