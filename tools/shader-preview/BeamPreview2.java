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
 * 绯红誓约「终末轰击光柱」（rendertype_crimson_sigil 的 uMode=3）离屏预览台。
 *
 * <h3>它为什么存在</h3>
 * <p>光柱在实机里的观感与此前所有离屏图都不一样 —— 根因是
 * {@code POSITION_COLOR_NORMAL} 的 NORMAL 是 <b>3 个 signed byte、归一化</b>，
 * 而 Java 把世界 Y（36~70）写进 {@code Normal.x}：{@code BufferVertexConsumer.normalIntValue()}
 * 先 {@code clamp(x, -1, 1)} 再 ×127，于是整根柱子的 vHeight 全是 <b>1.0</b>。
 * 所有按高度算的图案（电弧相位、下扫环、前缘光斑、tail 淡出）都会冻在
 * {@code hN≈0} / {@code wy0≈-地面高度} 上 —— 实机看到的是一根"均匀竖条纹的塑料管"。</p>
 *
 * <p>所以本工具<b>如实复现这个 byte 编码</b>（Color u8 / Normal i8 + 归一化 +
 * clamp），这样：</p>
 * <ul>
 *   <li>{@code -Mode old}：跑现役着色器 → 复现实机那根难看的管子（对照片用）；</li>
 *   <li>{@code -Mode new}：跑候选（片元用 uBeamAxisVS/uBeamAxisDir 反解世界高度）
 *       → 看到修复后的真实样子。</li>
 * </ul>
 *
 * <p>几何/GL 状态/uniform 全部照抄 {@code CrimsonVowFieldRenderer.drawBeam} +
 * {@code CrimsonVowRenderSupport.emitCylinder}，唯一被替换的是 Minecraft 本身。</p>
 */
public class BeamPreview2 {

    static final int W = 1280, H = 720;
    static final float FOV = 70f;

    // ── 与 CrimsonVowFieldEntity / Renderer 一致 ──
    static final float BEAM_TOP = 70f;
    static final float BEAM_LENGTH = 34f;
    static final int FALL_TICKS = 14;
    static final float LINGER_TICKS = 28f;
    static final int CYL_SEGMENTS = 56;
    static final int RINGS = 24;
    static final float TOP_TAPER = 0.62f;
    static final float HALO_RATIO = 0.40f;
    static final float BODY_RATIO = 0.245f;
    static final float CORE_RATIO = 0.075f;
    static final float WOBBLE_HALO = 0.26f;
    static final float WOBBLE_BODY = 0.18f;
    static final float WOBBLE_CORE = 0.05f;
    static final float GROUND_Y = 0f;
    static final float FIELD_RADIUS = 14f;

    static final float[] COLOR_A = { 1.000f, 0.310f, 0.722f };
    static final float[] COLOR_B = { 0.608f, 0.302f, 1.000f };
    static final float[] COLOR_C = { 0.910f, 0.651f, 1.000f };

    static int prog, flatProg;
    static int vao, posVbo, colorVbo, normalVbo;
    static int flatVao, flatVbo;
    static int fbo;
    static int vertexCount;
    static boolean newMode;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: BeamPreview2 <shaderDir> <outDir> [old|new] [shaderBase]");
            return;
        }
        Path shaderDir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String mode = args.length > 2 ? args[2] : "new";
        String base = args.length > 3 ? args[3] : "rendertype_crimson_sigil";
        newMode = !"old".equalsIgnoreCase(mode);
        Files.createDirectories(out);

        if (!GLFW.glfwInit()) { System.out.println("RESULT=NO_GLFW"); return; }
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long win = GLFW.glfwCreateWindow(64, 64, "beam2", 0L, 0L);
        if (win == 0L) { System.out.println("RESULT=NO_WINDOW"); return; }
        GLFW.glfwMakeContextCurrent(win);
        GL.createCapabilities();

        prog = link(shaderDir.resolve(base + ".vsh"), shaderDir.resolve(base + ".fsh"));
        if (prog == 0) { System.out.println("RESULT=SHADER_FAIL"); return; }
        flatProg = buildFlat();
        buildBuffers();
        fbo = createFbo();

        System.out.println("shaderDir = " + shaderDir);
        System.out.println("mode      = " + (newMode ? "new (chord volumetric + worldY from axis)" : "old (in-game byte-normal behaviour)")
                + "   canvas=" + W + "x" + H);

        // ── 下落：raw 0.15 / 0.55 / 0.90（中景 + 广角 + 低机位） ──
        beamShot(out, "fall_00_raw15", 0.15f, 0f, "mid");
        beamShot(out, "fall_01_raw55", 0.55f, 0f, "mid");
        beamShot(out, "fall_02_raw90", 0.90f, 0f, "low");
        beamShot(out, "fall_03_wide", 0.70f, 0f, "wide");

        // ── 触地：0 / 4 / 10 / 20 tick ──
        beamShot(out, "impact_00_t000", 1f, 0f, "mid");
        beamShot(out, "impact_01_t004", 1f, 4f, "mid");
        beamShot(out, "impact_02_t010", 1f, 10f, "mid");
        beamShot(out, "impact_03_t020", 1f, 20f, "mid");
        beamShot(out, "impact_04_close", 1f, 0f, "close");
        beamShot(out, "impact_05_lookup", 1f, 0f, "lookup");

        // ── 蓄力柱（同一个 uMode=3，只有主体层） ──
        chargeShot(out, "charge_00_column");

        GLFW.glfwDestroyWindow(win);
        GLFW.glfwTerminate();
        System.out.println("DONE -> " + out.toAbsolutePath());
    }

    // ══════════════════════════════════════════════════════════════
    //  轰击光柱一站
    // ══════════════════════════════════════════════════════════════

    static void beamShot(Path out, String name, float fallRaw, float impactElapsed,
                         String view) throws Exception {
        float[] cam;
        float[] target;
        switch (view) {
            case "wide"  -> { cam = new float[] { 0f, 1.62f, 60f }; target = new float[] { 0f, 22f, 0f }; }
            case "low"   -> { cam = new float[] { 0f, 1.62f, 18f }; target = new float[] { 0f, 7f, 0f }; }
            case "close" -> { cam = new float[] { 0f, 1.62f, 9f };  target = new float[] { 0f, 3.0f, 0f }; }
            case "lookup"-> { cam = new float[] { 6f, 1.62f, 12f }; target = new float[] { 0f, 45f, 0f }; }
            default      -> { cam = new float[] { 0f, 1.62f, 30f }; target = new float[] { 0f, 16f, 0f }; }
        }
        float[] R = viewBasis(cam, target);
        float[] proj = perspective(FOV, (float) W / H, 0.05f, 2000f);

        float fall = (float) Math.pow(Math.max(0f, Math.min(1f, fallRaw)), 1.45);
        boolean impacted = fallRaw >= 1f;
        float beamElapsed = impacted ? FALL_TICKS + impactElapsed : fallRaw * FALL_TICKS;
        float impactElapsed2 = impacted ? impactElapsed : 0f;
        float impact = Math.max(0f, 1f - impactElapsed2 / 10f);
        float collapse = impacted ? smoothstep01(impactElapsed2 / 16f) : 0f;

        float bottom;
        float top;
        if (!impacted) {
            bottom = GROUND_Y + BEAM_TOP * (1f - fall);
            top = bottom + BEAM_LENGTH;
        } else {
            bottom = GROUND_Y + collapse * (BEAM_LENGTH - 1.5f);
            top = GROUND_Y + BEAM_LENGTH + collapse * 1.5f;
        }
        top = Math.max(top, bottom + 0.75f);

        float fadeIn = 1f;
        float imp = Math.max(0f, Math.min(1f, impactElapsed2 / LINGER_TICKS));
        float baseI = Math.min(0.18f + 0.82f * Math.max(0f, Math.min(1f, fallRaw)), 1f - imp) * fadeIn;
        float time = beamElapsed / 20f + 3.7f;
        float impactAge = impacted ? impactElapsed2 / 20f : -1f;

        beginFrame(R, cam, proj);
        drawGround(R, cam, proj);

        float shrink = 1f - 0.18f * collapse;
        float haloR = Math.max(0.55f, FIELD_RADIUS * HALO_RATIO) * shrink;
        float bodyR = Math.max(0.38f, FIELD_RADIUS * BODY_RATIO) * shrink;
        float coreR = Math.max(0.16f, FIELD_RADIUS * CORE_RATIO) * shrink;

        // 旧版：三层都走 uLayer=0/1（halo 与 body 同为 0，靠不同半径+亮度区分）
        // 新版：新增 uLayer=4（外光晕）与 uLayer=5（触地飞溅），枪尖分层的长度/亮度
        float[] radii = { haloR, bodyR, coreR };
        float[] wobbles = { WOBBLE_HALO, WOBBLE_BODY, WOBBLE_CORE };
        float[] scales = { 0.55f, 1.00f, 1.00f };
        float[] layers = newMode ? new float[] { 4f, 0f, 1f } : new float[] { 0f, 0f, 1f };
        float[] tipFactor = newMode ? new float[] { 0.30f, 0.55f, 0.95f }
                                    : new float[] { 1f, 1f, 1f };
        float[] tipI = newMode ? new float[] { 0.00f, 0.70f, 1.00f }
                               : new float[] { 0.80f, 0.80f, 0.80f };
        float tipFade = 1f - collapse;

        for (int i = 0; i < 3; i++) {
            float r = radii[i];
            float hi = baseI * scales[i];
            float headLen = Math.max(0.60f, r * 0.55f) * tipFactor[i];
            float shaftBottom = Math.min(top, bottom + headLen);
            if (top - shaftBottom > 0.05f) {
                setBeamUniforms(time, hi, layers[i], r,
                        bottom, top, r, r * TOP_TAPER, wobbles[i],
                        impact, impactAge);
                emitCylinder(0f, 0f, shaftBottom, r, r * TOP_TAPER, top, RINGS, R, cam);
                flushAndDraw();
            }
            float ti = hi * tipFade * tipI[i];
            if (ti > 0.004f) {
                float tipTop = Math.min(top, bottom + headLen);
                setBeamUniforms(time, ti, layers[i], r,
                        bottom, top, r, r * TOP_TAPER, 0f, impact, impactAge);
                emitCylinder(0f, 0f, bottom, 0.02f, r, tipTop, 4, R, cam);
                flushAndDraw();
            }
        }

        // 新版：触地瞬间在柱底补一层"飞溅"（宽喇叭口，快速衰减）
        if (newMode && impacted && impact > 0.01f) {
            setBeamUniforms(time, baseI * impact * 1.05f, 5f, bodyR * 1.25f,
                    GROUND_Y, GROUND_Y + 3.4f, bodyR * 1.25f, bodyR * 4.2f, 0f,
                    impact, impactAge);
            emitCylinder(0f, 0f, GROUND_Y, bodyR * 4.2f, bodyR * 1.25f,
                    GROUND_Y + 3.4f, 6, R, cam);
            flushAndDraw();
        }

        save(out.resolve(name + ".png"));
        System.out.printf("  %-22s view=%-6s fall=%.2f impactElapsed=%.0f collapse=%.2f%n",
                name, view, fallRaw, impactElapsed2, collapse);
    }

    // ══════════════════════════════════════════════════════════════
    //  蓄力柱
    // ══════════════════════════════════════════════════════════════

    static void chargeShot(Path out, String name) throws Exception {
        float radius = 4.6f;
        float beamR = Math.max(0.25f, radius * 0.42f);   // = 1.932
        float beamH = 1.0f + 6.5f;                       // charge = 1
        float[] cam = { 0f, 2.4f, 7.0f };
        float[] target = { 0f, 3.2f, 0f };
        float[] R = viewBasis(cam, target);
        float[] proj = perspective(FOV, (float) W / H, 0.05f, 2000f);

        beginFrame(R, cam, proj);
        drawGround(R, cam, proj);

        float time = 2.6f;
        setBeamUniforms(time, 1.8f, 0f, beamR, GROUND_Y, GROUND_Y + beamH,
                beamR, beamR, 0f, 0f, -1f);
        emitCylinder(0f, 0f, GROUND_Y, beamR, beamR, GROUND_Y + beamH, 6, R, cam);
        flushAndDraw();

        if (newMode) {
            float coreR = beamR * 0.30f;
            setBeamUniforms(time, 1.8f, 1f, coreR, GROUND_Y, GROUND_Y + beamH,
                    coreR, coreR, 0f, 0f, -1f);
            emitCylinder(0f, 0f, GROUND_Y, coreR, coreR, GROUND_Y + beamH, 6, R, cam);
            flushAndDraw();
        }

        save(out.resolve(name + ".png"));
        System.out.println("  " + name + "  charge column r=" + beamR + " h=" + beamH);
    }

    // ══════════════════════════════════════════════════════════════
    //  uniform
    // ══════════════════════════════════════════════════════════════

    static void setBeamUniforms(float time, float intensity, float layer,
                                float layerRadius, float fxBottom, float fxTop,
                                float baseR, float topR, float wobble,
                                float impact, float impactAge) {
        GL20.glUseProgram(prog);
        flt("uTime", time);
        flt("uIntensity", intensity);
        flt("uMode", 3f);
        flt("uLayer", layer);
        flt("uRadius", layerRadius);
        flt("uMeshRadius", layerRadius);
        flt("uProgress", 0.92f);
        flt("uFinale", 1f);
        flt("uSeed", 0f);
        flt("uWarn", 1f);
        flt("uImpactAge", impactAge);
        flt("uImpact", Math.max(0f, Math.min(1f, impact)));
        flt("uBeamBase", GROUND_Y);
        flt("uBeamHead", fxBottom);
        flt("uBeamSpan", Math.max(fxTop - fxBottom, 0.75f));
        flt("uBeamRadius", layerRadius);
        flt("uBeamWobble", wobble);
        vec3("uColorA", COLOR_A[0], COLOR_A[1], COLOR_A[2]);
        vec3("uColorB", COLOR_B[0], COLOR_B[1], COLOR_B[2]);
        vec3("uColorC", COLOR_C[0], COLOR_C[1], COLOR_C[2]);
    }

    /** 每帧的相机/柱轴 uniform（ProjMat 与 uBeamAxis* 都来自这里）。 */
    static float[] curProj;
    static float[] curAxisVS = new float[3];
    static float[] curAxisDir = new float[3];

    static void applyCameraUniforms(float[] proj, float[] R, float[] cam) {
        curProj = proj;
        // 柱轴 = 世界 (0, groundY, 0) 那条竖线（法阵在原点）
        float dx = 0f - cam[0];
        float dy = GROUND_Y - cam[1];
        float dz = 0f - cam[2];
        curAxisVS[0] = R[0] * dx + R[1] * dy + R[2] * dz;
        curAxisVS[1] = R[3] * dx + R[4] * dy + R[5] * dz;
        curAxisVS[2] = R[6] * dx + R[7] * dy + R[8] * dz;
        // 世界 +Y 在视空间的方向 = R 的第 1 列
        curAxisDir[0] = R[1];
        curAxisDir[1] = R[4];
        curAxisDir[2] = R[7];
    }

    // ══════════════════════════════════════════════════════════════
    //  几何（与 CrimsonVowRenderSupport.emitCylinder 一致）
    // ══════════════════════════════════════════════════════════════

    static void emitCylinder(float cx, float cz, float baseY,
                             float baseRadius, float topRadius, float topY,
                             int rings, float[] R, float[] cam) {
        int rn = Math.max(1, rings);
        for (int ring = 0; ring < rn; ring++) {
            float t0 = ring / (float) rn;
            float t1 = (ring + 1) / (float) rn;
            float r0 = lerp(t0, baseRadius, topRadius);
            float r1 = lerp(t1, baseRadius, topRadius);
            float y0 = lerp(t0, baseY, topY);
            float y1 = lerp(t1, baseY, topY);
            for (int seg = 0; seg < CYL_SEGMENTS; seg++) {
                float a0 = seg / (float) CYL_SEGMENTS;
                float a1 = (seg + 1) / (float) CYL_SEGMENTS;
                float ang0 = (float) (a0 * Math.PI * 2.0);
                float ang1 = (float) (a1 * Math.PI * 2.0);
                float x00 = cx + (float) Math.cos(ang0) * r0;
                float z00 = cz + (float) Math.sin(ang0) * r0;
                float x01 = cx + (float) Math.cos(ang1) * r0;
                float z01 = cz + (float) Math.sin(ang1) * r0;
                float x10 = cx + (float) Math.cos(ang0) * r1;
                float z10 = cz + (float) Math.sin(ang0) * r1;
                float x11 = cx + (float) Math.cos(ang1) * r1;
                float z11 = cz + (float) Math.sin(ang1) * r1;
                cylVertex(R, cam, x00, y0, z00, y0, a0);
                cylVertex(R, cam, x01, y0, z01, y0, a1);
                cylVertex(R, cam, x11, y1, z11, y1, a1);
                cylVertex(R, cam, x00, y0, z00, y0, a0);
                cylVertex(R, cam, x11, y1, z11, y1, a1);
                cylVertex(R, cam, x10, y1, z10, y1, a0);
            }
        }
    }

    static void cylVertex(float[] R, float[] cam, float x, float y, float z,
                          float worldY, float aNorm) {
        float dx = x - cam[0], dy = y - cam[1], dz = z - cam[2];
        POS.put(R[0] * dx + R[1] * dy + R[2] * dz);
        POS.put(R[3] * dx + R[4] * dy + R[5] * dz);
        POS.put(R[6] * dx + R[7] * dy + R[8] * dz);

        // Color.rg = (0, 角度)，u8 归一化
        COL.put((byte) 0);
        COL.put(normU8(aNorm));
        COL.put((byte) 0);
        COL.put((byte) 255);

        // Normal.x = 世界 Y —— **这就是实机的 bug 点**：normalIntValue 会先 clamp 到
        // [-1,1] 再 ×127，所以世界 Y 超过 1 的顶点全部变成 127 → 片元里 vHeight = 1.0。
        NOR.put(normI8(worldY));
        NOR.put(normI8(aNorm));
        NOR.put((byte) 0);
        vertexCount++;
    }

    /** 复现 BufferVertexConsumer.normalIntValue(float)：(byte)((int)(clamp(f,-1,1)*127) & 255)。 */
    static byte normI8(float f) {
        float c = Math.max(-1f, Math.min(1f, f));
        return (byte) (((int) (c * 127f)) & 255);
    }

    static byte normU8(float f) {
        return (byte) Math.round(Math.max(0f, Math.min(1f, f)) * 255f);
    }

    static FloatBuffer POS;
    static ByteBuffer COL;
    static ByteBuffer NOR;

    static void buildBuffers() {
        vao = GL30.glGenVertexArrays();
        posVbo = GL15.glGenBuffers();
        colorVbo = GL15.glGenBuffers();
        normalVbo = GL15.glGenBuffers();

        flatVao = GL30.glGenVertexArrays();
        flatVbo = GL15.glGenBuffers();
    }

    /** 本帧的几何先攒在 CPU 缓冲里，提交时上传一次。 */
    static void beginGeometry() {
        POS = BufferUtils.createFloatBuffer(1 << 20);
        COL = BufferUtils.createByteBuffer(1 << 20);
        NOR = BufferUtils.createByteBuffer(1 << 20);
        vertexCount = 0;
    }

    static void flushAndDraw() {
        // 把三块属性分别上传（与实机 POSITION_COLOR_NORMAL 的三段布局等价）
        POS.flip(); COL.flip(); NOR.flip();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, posVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, POS, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, colorVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, COL, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 4, 0L);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, NOR, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 3, GL11.GL_BYTE, true, 3, 0L);

        GL20.glUseProgram(prog);
        matrix("ProjMat", curProj);
        vec3("uBeamAxisVS", curAxisVS[0], curAxisVS[1], curAxisVS[2]);
        vec3("uBeamAxisDir", curAxisDir[0], curAxisDir[1], curAxisDir[2]);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);      // 实机：加法
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);
        beginGeometry();                                 // 下一层从零攒
    }

    // ══════════════════════════════════════════════════════════════
    //  背景：夜空渐变 + 地面
    // ══════════════════════════════════════════════════════════════

    static void beginFrame(float[] R, float[] cam, float[] proj) {
        applyCameraUniforms(proj, R, cam);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, W, H);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        // 夜空：顶部近黑、地平线带一点紫（和实机夜晚接近）
        for (int i = 0; i < 64; i++) {
            float t0 = i / 64f;
            GL11.glScissor(0, (int) (H * t0), W, (int) Math.ceil(H / 64f) + 1);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            float r = lerp(0.020f, 0.075f, t0);
            float g = lerp(0.020f, 0.045f, t0);
            float b = lerp(0.045f, 0.095f, t0);
            GL11.glClearColor(r, g, b, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        beginGeometry();
    }

    /** 地面：一块巨大的水平面（夜地色），用最简单的 position-only 程序画。 */
    static void drawGround(float[] R, float[] cam, float[] proj) {
        GL20.glUseProgram(flatProg);
        matrix(flatProg, "ProjMat", proj);
        int loc = GL20.glGetUniformLocation(flatProg, "Color");
        if (loc >= 0) GL20.glUniform3f(loc, 0.085f, 0.10f, 0.075f);

        float e = 400f;
        float y = GROUND_Y;
        float[][] quad = {
                { -e, y, -e }, { e, y, -e }, { e, y, e },
                { -e, y, -e }, { e, y, e }, { -e, y, e } };
        FloatBuffer fb = BufferUtils.createFloatBuffer(6 * 3);
        for (float[] p : quad) {
            float dx = p[0] - cam[0], dy = p[1] - cam[1], dz = p[2] - cam[2];
            fb.put(R[0] * dx + R[1] * dy + R[2] * dz);
            fb.put(R[3] * dx + R[4] * dy + R[5] * dz);
            fb.put(R[6] * dx + R[7] * dy + R[8] * dz);
        }
        fb.flip();
        GL30.glBindVertexArray(flatVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, flatVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        beginGeometry();
    }

    // ══════════════════════════════════════════════════════════════
    //  GL / 数学
    // ══════════════════════════════════════════════════════════════

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

    static int buildFlat() throws Exception {
        int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vs, "#version 150 core\nin vec3 Position;\nuniform mat4 ProjMat;\n"
                + "void main(){ gl_Position = ProjMat * vec4(Position,1.0); }\n");
        GL20.glCompileShader(vs);
        int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fs, "#version 150 core\nuniform vec3 Color;\nout vec4 fragColor;\n"
                + "void main(){ fragColor = vec4(Color,1.0); }\n");
        GL20.glCompileShader(fs);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glBindAttribLocation(p, 0, "Position");
        GL30.glBindFragDataLocation(p, 0, "fragColor");
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            System.out.println("FLAT LINK FAIL: " + GL20.glGetProgramInfoLog(p));
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

    /**
     * 返回 R 的 9 个元素（行主序）：right / up2 / −forward，
     * 满足 view = R·(world − cam)。与 SigilPreview 同一套。
     */
    static float[] viewBasis(float[] cam, float[] target) {
        float fx = target[0] - cam[0];
        float fy = target[1] - cam[1];
        float fz = target[2] - cam[2];
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        float ux = 0f, uy = 1f, uz = 0f;
        if (Math.abs(fy) > 0.999f) { ux = 0f; uy = 0f; uz = -1f; }
        float rx = fy * uz - fz * uy;
        float ry = fz * ux - fx * uz;
        float rz = fx * uy - fy * ux;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl; ry /= rl; rz /= rl;
        float u2x = ry * fz - rz * fy;
        float u2y = rz * fx - rx * fz;
        float u2z = rx * fy - ry * fx;
        return new float[] { rx, ry, rz, u2x, u2y, u2z, -fx, -fy, -fz };
    }

    static float[] perspective(float fovDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovDeg) / 2.0));
        float[] m = new float[16];
        m[0] = f / aspect; m[5] = f;
        m[10] = (far + near) / (near - far); m[11] = -1f;
        m[14] = 2f * far * near / (near - far);
        return m;
    }

    static float smoothstep01(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }

    static float lerp(float t, float a, float b) { return a + (b - a) * t; }

    static void matrix(String n, float[] m) {
        int l = GL20.glGetUniformLocation(prog, n);
        if (l < 0) l = GL20.glGetUniformLocation(flatProg, n);
        if (l < 0) return;
        FloatBuffer b = BufferUtils.createFloatBuffer(16).put(m).flip();
        GL20.glUniformMatrix4fv(l, false, b);
    }

    static void matrix(int p, String n, float[] m) {
        int l = GL20.glGetUniformLocation(p, n);
        if (l < 0) return;
        FloatBuffer b = BufferUtils.createFloatBuffer(16).put(m).flip();
        GL20.glUniformMatrix4fv(l, false, b);
    }

    static void flt(String n, float v) {
        int l = GL20.glGetUniformLocation(prog, n);
        if (l < 0) return;
        GL20.glUniform1f(l, v);
    }

    static void vec3(String n, float x, float y, float z) {
        int l = GL20.glGetUniformLocation(prog, n);
        if (l < 0) return;
        GL20.glUniform3f(l, x, y, z);
    }
}
