import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
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
 * 离屏跑一遍「剪影遮罩 → 光雾模糊链 → 环形合成 + 火焰光环」，把结果导出成 PNG。
 *
 * <p>用法: {@code java ShaderPreview <shaderDir> <outDir> <colorMode> <time> <freqX> <floor>
 * <itemTexture> <outlineWidth> <fireStrength>}</p>
 *
 * <p>它不是游戏：这里没有 RenderType / 深度 / GUI，验证的是<b>着色器算法与观感</b>，
 * 不是 Java 侧的坐标簿记（见同目录 README 的"抓不到什么"）。</p>
 */
public class ShaderPreview {

    /** 画布边长。坐标固定：物品方片画在 [128,192)² 这一块里。 */
    static final int SIZE = 320;
    static final float ITEM_X = 128f;
    static final float ITEM_Y = 128f;
    static final float ITEM_W = 64f;

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        float colorMode = Float.parseFloat(args[2]);
        float time = Float.parseFloat(args[3]);
        float freqX = args.length > 4 ? Float.parseFloat(args[4]) : 18f;
        float floorV = args.length > 5 ? Float.parseFloat(args[5]) : 0f;
        String itemTexture = args.length > 6 ? args[6] : "";
        float outlineWidth = args.length > 7 ? Float.parseFloat(args[7]) : 10f;
        float fireStrength = args.length > 8
                ? Float.parseFloat(args[8])
                : (colorMode > 4.5f ? 1f : 0f);

        GLFW.glfwInit();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(64, 64, "preview", 0L, 0L);
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();

        // 体积火焰单独一档（ColorMode 9）：不需要遮罩/模糊链，只验证 raymarch 的观感与数学。
        if (colorMode > 8.5f && colorMode < 9.5f) {
            volumePreview(dir, out, time, fireStrength);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
            System.out.println("WROTE " + out.resolve("volume.png"));
            return;
        }

        int maskProg = link(dir.resolve("outline_mask.vsh"), dir.resolve("outline_mask.fsh"));
        int ringProg = link(dir.resolve("outline_ring.vsh"), dir.resolve("outline_ring.fsh"));
        int blurProg = link(dir.resolve("outline_ring.vsh"), dir.resolve("outline_halo_blur.fsh"));

        // ── 画布与目标 ──
        int[] maskTarget = makeTarget(SIZE, true);
        int[] outTarget = makeTarget(SIZE, false);

        int[] halfA = makeTarget(SIZE / 2, true);
        int[] halfB = makeTarget(SIZE / 2, true);
        int[] quartetA = makeTarget(SIZE / 4, true);
        int[] quartetB = makeTarget(SIZE / 4, true);

        // 火焰密度场（与管线 buildFireDensity 同一套四趟，只是分辨率固定）
        int[] fireHalfA = makeTarget(SIZE / 2, true);
        int[] fireHalfB = makeTarget(SIZE / 2, true);
        int[] fireWideA = makeTarget(SIZE / 4, true);
        int[] fireWideB = makeTarget(SIZE / 4, true);

        // Sampler0：默认 1×1 白，给了贴图就换成真实贴图（同时算出不透明包围盒）
        int itemTex = itemTexture.isEmpty() ? whiteTexture() : loadItemTexture(itemTexture);
        float[] opaqueBox = itemTexture.isEmpty()
                ? new float[]{0f, 0f, 1f, 1f}
                : lastOpaqueBox;

        // ── 全屏四边形（模糊与合成共用）──
        int quadVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(quadVao);
        int quadVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        uploadFullscreenQuad();

        // ── pass 1: 剪影遮罩 ──
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, maskTarget[1]);
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        int itemVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(itemVao);
        int itemVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, itemVbo);
        uploadItemQuad();
        int stride = 9 * 4;
        int mPos = GL20.glGetAttribLocation(maskProg, "Position");
        int mCol = GL20.glGetAttribLocation(maskProg, "Color");
        int mUv = GL20.glGetAttribLocation(maskProg, "UV0");
        GL20.glEnableVertexAttribArray(mPos);
        GL20.glVertexAttribPointer(mPos, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(mCol);
        GL20.glVertexAttribPointer(mCol, 4, GL11.GL_FLOAT, false, stride, 12L);
        GL20.glEnableVertexAttribArray(mUv);
        GL20.glVertexAttribPointer(mUv, 2, GL11.GL_FLOAT, false, stride, 28L);

        GL20.glUseProgram(maskProg);
        matrix(maskProg, "ModelViewMat", new float[]{
                0.4f, 0f, 0f, 0f,
                0f, 0.4f, 0f, 0f,
                0f, 0f, 1f, 0f,
                -0.2f, -0.2f, 0f, 1f});
        matrix(maskProg, "ProjMat", new float[]{
                1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f});
        vec4(maskProg, "MaskColor", 1.0f, 0.42f, 0.09f, 1f);
        vec4(maskProg, "SecondaryColor", 0.35f, 0.05f, 0.02f, 1f);
        vec4(maskProg, "PaletteColor0", 1.0f, 0.42f, 0.09f, 1f);
        vec4(maskProg, "PaletteColor1", 0.35f, 0.05f, 0.02f, 1f);
        flt(maskProg, "ColorMode", colorMode);
        flt(maskProg, "ColorScrollSpeed", 1f);
        flt(maskProg, "Time", time);
        flt(maskProg, "PaletteSize", 2f);
        flt(maskProg, "AlphaCutoff", 0.1f);
        flt(maskProg, "ColorSpeed", 4f);
        flt(maskProg, "ColorFloor", floorV);
        vec2(maskProg, "ColorFreq", freqX, 12f);
        vec4(maskProg, "ItemRect", ITEM_X, ITEM_Y, ITEM_W, ITEM_W);
        vec4(maskProg, "ItemOpaque", opaqueBox[0], opaqueBox[1], opaqueBox[2], opaqueBox[3]);
        GL20.glUniform1i(GL20.glGetUniformLocation(maskProg, "Sampler0"), 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemTex);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        save(out.resolve("mask.png"), maskTarget[1], SIZE);

        // ── pass 2: 光雾模糊链（1/2 → 1/4）──
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL20.glUseProgram(blurProg);
        GL20.glUniform1i(GL20.glGetUniformLocation(blurProg, "Source"), 0);
        GL30.glBindVertexArray(quadVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        int bPos = GL20.glGetAttribLocation(blurProg, "Position");
        int bUv = GL20.glGetAttribLocation(blurProg, "UV0");
        GL20.glEnableVertexAttribArray(bPos);
        GL20.glVertexAttribPointer(bPos, 3, GL11.GL_FLOAT, false, 20, 0L);
        GL20.glEnableVertexAttribArray(bUv);
        GL20.glVertexAttribPointer(bUv, 2, GL11.GL_FLOAT, false, 20, 12L);

        blur(blurProg, quadVao, halfA, maskTarget[0], 2f / SIZE, 0f, 1f);
        blur(blurProg, quadVao, halfB, halfA[0], 0f, 1f / (SIZE / 2), 1f);
        blur(blurProg, quadVao, quartetA, halfB[0], 2f / (SIZE / 2), 0f, 1f);
        blur(blurProg, quadVao, quartetB, quartetA[0], 0f, 1f / (SIZE / 4), 1f);

        // ── pass 2.5: 火焰密度场（半径按物品尺寸放大：reach = 物品高 × 1.6）──
        float fireReachPx = ITEM_W * 2.5f;
        float fireScale = Math.max(0.5f, Math.min(6f, fireReachPx / 52f));
        blur(blurProg, quadVao, fireHalfA, maskTarget[0], 2f / SIZE, 0f, fireScale);
        blur(blurProg, quadVao, fireHalfB, fireHalfA[0], 0f, 1f / (SIZE / 2), fireScale);
        blur(blurProg, quadVao, fireWideA, fireHalfB[0], 2f / (SIZE / 2), 0f, fireScale);
        blur(blurProg, quadVao, fireWideB, fireWideA[0], 0f, 1f / (SIZE / 4), fireScale);
        save(out.resolve("fire_spread.png"), fireWideB[1], SIZE / 4);

        // ── pass 3: 环形合成（硬环 + 光雾 + 火焰光环）──
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, outTarget[1]);
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glClearColor(0.05f, 0.06f, 0.06f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL30.glBindVertexArray(quadVao);
        GL20.glUseProgram(ringProg);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        int rPos = GL20.glGetAttribLocation(ringProg, "Position");
        int rUv = GL20.glGetAttribLocation(ringProg, "UV0");
        GL20.glEnableVertexAttribArray(rPos);
        GL20.glVertexAttribPointer(rPos, 3, GL11.GL_FLOAT, false, 20, 0L);
        GL20.glEnableVertexAttribArray(rUv);
        GL20.glVertexAttribPointer(rUv, 2, GL11.GL_FLOAT, false, 20, 12L);

        vec2(ringProg, "ScreenSize", (float) SIZE, (float) SIZE);
        flt(ringProg, "OutlineWidth", outlineWidth);
        flt(ringProg, "Opacity", 1f);
        flt(ringProg, "AlphaTest", 0.5f);

        flt(ringProg, "FireStrength", fireStrength);
        vec4(ringProg, "FireRect", ITEM_X, ITEM_Y, ITEM_W, ITEM_W);
        vec3(ringProg, "FireColorA", 1.0f, 0.42f, 0.09f);
        vec3(ringProg, "FireColorB", 0.35f, 0.05f, 0.02f);

        flt(ringProg, "HaloStrength", 0.9f);
        flt(ringProg, "HaloNoise", 0.8f);
        flt(ringProg, "HaloWarp", 1f);
        flt(ringProg, "Time", time);
        vec2(ringProg, "HaloNoiseOrigin", 0.5f, 0.5f);

        GL20.glUniform1i(GL20.glGetUniformLocation(ringProg, "MaskSampler"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(ringProg, "HaloHalf"), 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(ringProg, "HaloWide"), 2);
        GL20.glUniform1i(GL20.glGetUniformLocation(ringProg, "FireSpread"), 3);

        // 按游戏里的加法混合画（fire / fire_2 都是 additive：黑 = 不画）。
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTarget[0]);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, halfB[0]);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, quartetB[0]);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fireWideB[0]);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        save(out.resolve("ring.png"), outTarget[1], SIZE);

        // 对照：只留硬环（关光雾、关火）
        flt(ringProg, "HaloStrength", 0f);
        flt(ringProg, "FireStrength", 0f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        save(out.resolve("ring_hard.png"), outTarget[1], SIZE);
        GL11.glDisable(GL11.GL_BLEND);

        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        System.out.println("WROTE " + out.resolve("mask.png") + " / " + out.resolve("ring.png"));
    }

    // ── 体积火焰预览 ──

    /**
     * 用一组假的相机/盒子/物品参数跑一遍 {@code fire_volume}，导出 {@code volume.png}。
     *
     * <p>验证的是：ray-box 求交、视线反解、世界空间噪声与 Beer 累积。深度用 {@code UseDepth=0}
     * （这里没有场景），所以"墙后不画"这条要靠游戏内验证。</p>
     */
    static void volumePreview(Path dir, Path out, float time, float opacity) throws Exception {
        int prog = link(dir.resolve("fire_volume.vsh"), dir.resolve("fire_volume.fsh"));

        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        uploadFullscreenQuad();
        int pos = GL20.glGetAttribLocation(prog, "Position");
        int uvLoc = GL20.glGetAttribLocation(prog, "UV0");
        GL20.glEnableVertexAttribArray(pos);
        GL20.glVertexAttribPointer(pos, 3, GL11.GL_FLOAT, false, 20, 0L);
        GL20.glEnableVertexAttribArray(uvLoc);
        GL20.glVertexAttribPointer(uvLoc, 2, GL11.GL_FLOAT, false, 20, 12L);

        // 相机在原点、看向 -z；盒子/物品都是**视图空间**坐标（和管线一致）
        float[] camPos = {0f, 0f, 0f};
        float[] right = {1f, 0f, 0f};
        float[] up = {0f, 1f, 0f};
        float[] fwd = {0f, 0f, 1f};   // 视图 +z 朝后

        // 物品在 z=-3 处；固定盒子半宽 0.45、高 1.0
        float[] itemMin = {-0.20f, -0.05f, -3.10f};
        float[] itemMax = {0.20f, 0.10f, -2.90f};
        float[] boxMin = {-0.45f, -0.09f, -3.35f};
        float[] boxMax = {0.45f, 0.91f, -2.65f};

        float[] invProj = invPerspective(70f, 1f, 0.05f, 128f);

        int outTarget = makeTarget(SIZE, false)[1];
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, outTarget);
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glClearColor(0.05f, 0.06f, 0.06f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

        GL20.glUseProgram(prog);
        vec2(prog, "ScreenSize", (float) SIZE, (float) SIZE);
        matrix(prog, "InvProj", invProj);
        vec3(prog, "BoxMin", boxMin[0], boxMin[1], boxMin[2]);
        vec3(prog, "BoxMax", boxMax[0], boxMax[1], boxMax[2]);
        vec3(prog, "ItemMin", itemMin[0], itemMin[1], itemMin[2]);
        vec3(prog, "ItemMax", itemMax[0], itemMax[1], itemMax[2]);
        // 视图 +z 对应世界里的 -forward
        vec3(prog, "CamRight", right[0], right[1], right[2]);
        vec3(prog, "CamUp", up[0], up[1], up[2]);
        vec3(prog, "CamFwd", -fwd[0], -fwd[1], -fwd[2]);
        vec3(prog, "CamPos", camPos[0], camPos[1], camPos[2]);
        flt(prog, "ItemBottomY", itemMin[1]);
        flt(prog, "Time", time);
        flt(prog, "Opacity", Math.max(0f, Math.min(1f, opacity)));
        flt(prog, "UseDepth", 0f);
        vec3(prog, "ColorHot", 1.0f, 0.42f, 0.09f);
        vec3(prog, "ColorCool", 0.35f, 0.05f, 0.02f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        save(out.resolve("volume.png"), outTarget, SIZE);
        GL11.glDisable(GL11.GL_BLEND);
    }

    static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    static float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1.0e-6f) return new float[]{0f, 0f, 1f};
        return new float[]{v[0] / len, v[1] / len, v[2] / len};
    }

    /** 透视投影的逆（列主序），相机在原点、看向 -z。 */
    static float[] invPerspective(float fovDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovDeg) * 0.5));
        float a = aspect / f;
        float b = 1.0f / f;
        float c = (near - far) / (2f * far * near);
        float d = (far + near) / (2f * far * near);
        return new float[]{
                a, 0f, 0f, 0f,
                0f, b, 0f, 0f,
                0f, 0f, 0f, c,
                0f, 0f, -1f, d};
    }

    // ── 纹理与目标 ──

    static float[] lastOpaqueBox = {0f, 0f, 1f, 1f};

    /** 生成一张可采样目标（RGBA8），返回 {textureId, framebufferId}。 */
    static int[] makeTarget(int size, boolean linear) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, tex, 0);
        GL11.glViewport(0, 0, size, size);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        return new int[]{tex, fbo, size};
    }

    static int whiteTexture() {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        ByteBuffer px = ByteBuffer.allocateDirect(4);
        px.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return tex;
    }

    /** 上传一张物品贴图（v=0 = 图片底部），并算出"不透明内容"的归一化包围盒。 */
    static int loadItemTexture(String path) throws Exception {
        BufferedImage img = ImageIO.read(new File(path));
        int w = img.getWidth();
        int h = img.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                buf.put((byte) ((argb >> 16) & 0xFF));
                buf.put((byte) ((argb >> 8) & 0xFF));
                buf.put((byte) (argb & 0xFF));
                buf.put((byte) ((argb >>> 24) & 0xFF));
                if (((argb >>> 24) & 0xFF) < 25) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        buf.flip();
        if (maxX >= minX && maxY >= minY) {
            lastOpaqueBox = new float[]{
                    minX / (float) w,
                    1f - (maxY + 1) / (float) h,
                    (maxX - minX + 1) / (float) w,
                    (maxY - minY + 1) / (float) h};
        }
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        System.out.println("Sampler0 = " + path + " (" + w + "x" + h + ") opaqueBox="
                + java.util.Arrays.toString(lastOpaqueBox));
        return tex;
    }

    // ── 绘制 ──

    static void uploadFullscreenQuad() {
        float[] verts = {
                -1f, -1f, 0f, 0f, 0f,
                1f, -1f, 0f, 1f, 0f,
                1f, 1f, 0f, 1f, 1f,
                -1f, -1f, 0f, 0f, 0f,
                1f, 1f, 0f, 1f, 1f,
                -1f, 1f, 0f, 0f, 1f,
        };
        upload(verts);
    }

    static void uploadItemQuad() {
        // [0,1]² 的方片，拆成两个三角形；Color 全白、UV 与位置一致
        float[][] quad = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 0f}, {1f, 1f}, {0f, 1f}};
        float[] verts = new float[quad.length * 9];
        for (int i = 0; i < quad.length; i++) {
            int o = i * 9;
            verts[o] = quad[i][0];
            verts[o + 1] = quad[i][1];
            verts[o + 2] = 0f;
            verts[o + 3] = 1f;
            verts[o + 4] = 1f;
            verts[o + 5] = 1f;
            verts[o + 6] = 1f;
            verts[o + 7] = quad[i][0];
            verts[o + 8] = quad[i][1];
        }
        upload(verts);
    }

    static void upload(float[] data) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(data.length);
            fb.put(data).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        }
    }

    /** 一趟可分离高斯：把 srcTex 画进 dst（步长是 UV 单位）。 */
    static void blur(int prog, int vao, int[] dst, int srcTex, float stepX, float stepY, float scale) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, dst[1]);
        GL11.glViewport(0, 0, dst[2], dst[2]);
        GL30.glBindVertexArray(vao);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
        vec2(prog, "TexelStep", stepX, stepY);
        flt(prog, "BlurScale", scale);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    static void save(Path path, int fbo, int size) throws Exception {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        ByteBuffer buf = ByteBuffer.allocateDirect(size * size * 4);
        GL11.glReadPixels(0, 0, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = (y * size + x) * 4;
                int r = buf.get(i) & 0xFF;
                int g = buf.get(i + 1) & 0xFF;
                int b = buf.get(i + 2) & 0xFF;
                int a = buf.get(i + 3) & 0xFF;
                // GL 原点在左下，PNG 在上左 → 上下翻转
                img.setRGB(x, size - 1 - y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", new File(path.toString()));
    }

    // ── 小程序 / uniform 小工具 ──

    static int link(Path vs, Path fs) throws Exception {
        int v = compile(vs, GL20.GL_VERTEX_SHADER);
        int f = compile(fs, GL20.GL_FRAGMENT_SHADER);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) != GL20.GL_TRUE) {
            throw new IllegalStateException("link failed: " + GL20.glGetProgramInfoLog(p));
        }
        return p;
    }

    static int compile(Path path, int type) throws Exception {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, Files.readString(path));
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) != GL20.GL_TRUE) {
            throw new IllegalStateException("compile failed " + path + ": "
                    + GL20.glGetShaderInfoLog(s));
        }
        return s;
    }

    static void matrix(int prog, String name, float[] m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            fb.put(m).flip();
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(prog, name), false, fb);
        }
    }

    static void flt(int prog, String name, float v) {
        GL20.glUniform1f(GL20.glGetUniformLocation(prog, name), v);
    }

    static void vec2(int prog, String name, float x, float y) {
        GL20.glUniform2f(GL20.glGetUniformLocation(prog, name), x, y);
    }

    static void vec3(int prog, String name, float x, float y, float z) {
        GL20.glUniform3f(GL20.glGetUniformLocation(prog, name), x, y, z);
    }

    static void vec4(int prog, String name, float x, float y, float z, float w) {
        GL20.glUniform4f(GL20.glGetUniformLocation(prog, name), x, y, z, w);
    }
}
