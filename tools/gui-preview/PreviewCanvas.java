import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * GUI 场景的绘制画布：把「GUI 缩放坐标」上的矩形、贴图、文字和项目里的 core shader
 * 画进 {@link PreviewGl} 的离屏 FBO，最后导出 PNG。
 *
 * <h3>坐标系</h3>
 * <p>原点左上、y 向下、单位 = 游戏里的 GUI 像素（和 {@code GuiGraphics} 的坐标同一层）。
 * 默认逻辑 640×400 + 导出 ×2 = 1280×800，即"1280×800 窗口、GUI scale 2"。
 * 血条是 182×32、文字行高 9、tooltip 内边距 4 —— 这些实机常量在场景里可以照抄。</p>
 *
 * <h3>和实机的对应关系</h3>
 * <ul>
 *   <li>{@link #fill} / {@link #fillGradientV} / {@link #blit} 对应
 *       {@code GuiGraphics.fill/fillGradient/blit}（标准 alpha 混合、无深度）。</li>
 *   <li>{@link #shaderQuad} / {@link #shaderMesh} 对应游戏里
 *       {@code BufferUploader.drawWithShader} 的"立即绘制"路径：顶点直接是 GUI 坐标，
 *       {@code ProjMat} 由画布提供（正交投影），{@code ModelViewMat} = 单位阵。</li>
 *   <li>项目 shader 的 json {@code blend} 由 {@link PreviewShader#applyBlend()} 生效 ——
 *       与 MC 一样，json 里的混合定义<b>赢过</b>调用前的 RenderSystem 状态。</li>
 * </ul>
 *
 * <h3>不模拟什么</h3>
 * <p>{@code GuiGraphics} 的 PoseStack 变换、批处理顺序、RenderType/StateShard 簿记、
 * 深度缓冲、光影包上下文。这些属于 Java 侧的状态簿记，离屏工具验不了 ——
 * 和 {@code tools/shader-preview} 的边界一致。</p>
 */
final class PreviewCanvas {

    final int width;
    final int height;

    private final PreviewGl gl;
    private final Path resourcesRoot;
    private final Path shaderDir;
    /** 工具私有着色器目录（设计稿专用，先进游戏前不进 assets）。 */
    private final Path toolShaderDir;
    private final Path outDir;
    private final PreviewFont font;

    private final int fillProgram;
    private final int texProgram;
    private final int fillProjLocation;
    private final int texProjLocation;

    private final int vao;
    private final int vbo;
    private final float[] projMat;
    private final float[] identity = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1};

    private final Map<String, PreviewTexture> textureCache = new HashMap<>();
    private final Map<String, java.awt.image.BufferedImage> imageCache = new HashMap<>();
    private final Map<String, PreviewShader> shaderCache = new HashMap<>();
    private final Deque<int[]> clipStack = new ArrayDeque<>();

    /**
     * 2D 仿射变换栈（设计稿画旋转图标/弧形文字用）：局部 → 屏幕
     * <pre>x' = a*x + c*y + e
     *y' = b*x + d*y + f</pre>
     * 只影响 fill/blit/text/shaderQuad/shaderMesh 的顶点位置，不影响剪裁矩形。
     */
    private final Deque<float[]> transformStack = new ArrayDeque<>();
    private float ta = 1, tb = 0, tc = 0, td = 1, te = 0, tf = 0;

    PreviewCanvas(PreviewGl gl, Path projectRoot, Path outDir) throws IOException {
        this.gl = gl;
        this.width = gl.width;
        this.height = gl.height;
        this.outDir = outDir;
        this.resourcesRoot = projectRoot.resolve("src/main/resources/assets/rotten_ruins");
        this.shaderDir = resourcesRoot.resolve("shaders/core");
        this.toolShaderDir = projectRoot.resolve("tools/gui-preview/shaders");
        if (!Files.isDirectory(shaderDir)) {
            throw new IOException("找不到着色器目录：" + shaderDir.toAbsolutePath());
        }
        this.font = PreviewFont.load(projectRoot.resolve("build/gui-preview/mc-assets"));

        fillProgram = linkProgram(FILL_VERTEX, FILL_FRAGMENT, "Position", "Color");
        texProgram = linkProgram(TEX_VERTEX, TEX_FRAGMENT, "Position", "UV0", "Color");
        fillProjLocation = GL20.glGetUniformLocation(fillProgram, "ProjMat");
        texProjLocation = GL20.glGetUniformLocation(texProgram, "ProjMat");
        GL20.glUseProgram(texProgram);
        int sampler = GL20.glGetUniformLocation(texProgram, "Sampler0");
        if (sampler >= 0) {
            GL20.glUniform1i(sampler, 0);
        }

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        projMat = ortho(width, height);
        gl.bind();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    // ══════════════════════════════════════════════════════════════
    //  基础查询
    // ══════════════════════════════════════════════════════════════

    PreviewFont font() {
        return font;
    }

    Path outDir() {
        return outDir;
    }

    int fontHeight() {
        return PreviewFont.LINE_HEIGHT;
    }

    /** 1 个 GUI 逻辑像素对应多少物理像素（原生渲染倍率；SDF 抗锯齿按它对齐）。 */
    int pixelScale() {
        return gl.scale;
    }

    int textWidth(String text) {
        return font.width(text);
    }

    // ══════════════════════════════════════════════════════════════
    //  矩形 / 贴图（对应 GuiGraphics.fill / blit）
    // ══════════════════════════════════════════════════════════════

    void clear(int argb) {
        gl.clear(argb);
        clipStack.clear();
    }

    void fill(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        useStandardBlend();
        drawQuad(fillProgram, false, null, x, y, w, h, 0, 0, 1, 1,
                argb, argb, argb, argb);
    }

    void fillGradientV(int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        useStandardBlend();
        drawQuad(fillProgram, false, null, x, y, w, h, 0, 0, 1, 1,
                top, bottom, bottom, top);
    }

    void blit(PreviewTexture texture, int x, int y, int w, int h) {
        blitUV(texture, x, y, w, h, 0, 0, 1, 1, 0xFFFFFFFF);
    }

    void blit(PreviewTexture texture, int x, int y, int w, int h,
              float u0, float v0, float u1, float v1) {
        blitUV(texture, x, y, w, h, u0, v0, u1, v1, 0xFFFFFFFF);
    }

    void blit(PreviewTexture texture, int x, int y, int w, int h,
              float u0, float v0, float u1, float v1, int tint) {
        blitUV(texture, x, y, w, h, u0, v0, u1, v1, tint);
    }

    /** 1px 描边（GUI 里就是 4 次 fill，顺序和实机一致：上、下、左、右）。 */
    void strokeRect(int x, int y, int w, int h, int topColor, int bottomColor) {
        fill(x, y, w, 1, topColor);
        fill(x, y + h - 1, w, 1, bottomColor);
        fill(x, y + 1, 1, h - 2, bottomColor);
        fill(x + w - 1, y + 1, 1, h - 2, bottomColor);
    }

    // ══════════════════════════════════════════════════════════════
    //  文字（MC 位图字体；行高 9，8px 字面）
    // ══════════════════════════════════════════════════════════════

    void text(String text, int x, int y, int argb, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (shadow) {
            drawText(text, x + 1, y + 1, shadowColor(argb));
        }
        drawText(text, x, y, argb);
    }

    void textCentered(String text, int centerX, int y, int argb, boolean shadow) {
        text(text, centerX - textWidth(text) / 2, y, argb, shadow);
    }

    void textRight(String text, int rightX, int y, int argb, boolean shadow) {
        text(text, rightX - textWidth(text), y, argb, shadow);
    }

    /** 放大文字（MC 里对应 pose.scale；位图字体用最近邻，所以 2x/3x 依旧是像素风）。 */
    void textScaled(String text, float x, float y, int argb, boolean shadow, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        pushTransform();
        translate(x, y);
        scale(scale, scale);
        if (shadow) {
            drawText(text, 1, 1, shadowColor(argb));
        }
        drawText(text, 0, 0, argb);
        popTransform();
    }

    void textCenteredScaled(String text, float centerX, float y, int argb, boolean shadow,
                            float scale) {
        float width = textWidth(text) * scale;
        textScaled(text, centerX - width * 0.5F, y, argb, shadow, scale);
    }

    // ══════════════════════════════════════════════════════════════
    //  2D 变换（旋转图标 / 弧形文字 / 分段圆环）
    // ══════════════════════════════════════════════════════════════

    void pushTransform() {
        transformStack.push(new float[]{ta, tb, tc, td, te, tf});
    }

    void popTransform() {
        float[] m = transformStack.poll();
        if (m != null) {
            ta = m[0];
            tb = m[1];
            tc = m[2];
            td = m[3];
            te = m[4];
            tf = m[5];
        }
    }

    /** 在当前坐标系里平移（先平移后映射：对局部坐标生效）。 */
    void translate(float dx, float dy) {
        te += ta * dx + tc * dy;
        tf += tb * dx + td * dy;
    }

    /** 在当前坐标系里旋转（角度制，顺时针为正，与 y 向下一致）。 */
    void rotate(float degrees) {
        double rad = Math.toRadians(degrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float na = ta * cos + tc * sin;
        float nb = tb * cos + td * sin;
        float nc = -ta * sin + tc * cos;
        float nd = -tb * sin + td * cos;
        ta = na;
        tb = nb;
        tc = nc;
        td = nd;
    }

    /** 绕指定点旋转（GUI 坐标）。 */
    void rotateAround(float degrees, float cx, float cy) {
        translate(cx, cy);
        rotate(degrees);
        translate(-cx, -cy);
    }

    void scale(float sx, float sy) {
        ta *= sx;
        tb *= sx;
        tc *= sy;
        td *= sy;
    }

    private float mapX(float x, float y) {
        return ta * x + tc * y + te;
    }

    private float mapY(float x, float y) {
        return tb * x + td * y + tf;
    }

    private boolean hasTransform() {
        return ta != 1F || tb != 0F || tc != 0F || td != 1F || te != 0F || tf != 0F;
    }

    /**
     * 画任意三角形网格（纯色、无贴图）。顶点布局：每顶点 {@code x, y, r, g, b, a}
     * （6 个 float；颜色 0..1）。弧形色块、圆环、径向发光多边形都靠它拼，
     * 顶点会经过当前 2D 变换。
     */
    void fillMesh(float[] xyRgba, int floats, int vertexCount) {
        if (floats <= 0 || vertexCount <= 0) {
            return;
        }
        float[] data = new float[vertexCount * 7];
        int src = 0;
        int dst = 0;
        for (int i = 0; i < vertexCount; i++) {
            float x = xyRgba[src++];
            float y = xyRgba[src++];
            float r = xyRgba[src++];
            float g = xyRgba[src++];
            float b = xyRgba[src++];
            float a = xyRgba[src++];
            data[dst++] = mapX(x, y);
            data[dst++] = mapY(x, y);
            data[dst++] = 0;
            data[dst++] = r;
            data[dst++] = g;
            data[dst++] = b;
            data[dst++] = a;
        }
        useStandardBlend();
        gl.bind();
        GL20.glUseProgram(fillProgram);
        uploadMatrix(fillProjLocation, projMat);
        uploadAndDraw(data, data.length, vertexCount, FILL_ATTRIBUTE, GL11.GL_TRIANGLES);
    }

    // ══════════════════════════════════════════════════════════════
    //  项目 core shader（对应游戏里的立即绘制路径）
    // ══════════════════════════════════════════════════════════════

    PreviewTexture texture(String relativePath) {
        return textureCache.computeIfAbsent(relativePath, key -> {
            Path file = resourcesRoot.resolve(key);
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("找不到贴图：" + file.toAbsolutePath());
            }
            try {
                return PreviewTexture.load(file);
            } catch (IOException e) {
                throw new RuntimeException("加载贴图失败：" + file, e);
            }
        });
    }

    /** 绝对路径贴图（用于玩家自己的图片 / 字体以外的资源）。 */
    PreviewTexture textureFromFile(Path file) {
        return textureCache.computeIfAbsent(file.toString(), key -> {
            try {
                return PreviewTexture.load(file);
            } catch (IOException e) {
                throw new RuntimeException("加载贴图失败：" + file, e);
            }
        });
    }

    /**
     * 读一张项目贴图的 ARGB 像素（只读，用于"照着贴图算几何"，例如血条按 mask 的
     * 亮行范围定黑槽位置）。返回数组是行优先、图像坐标（左上原点）。
     */
    java.awt.image.BufferedImage image(String relativePath) {
        return imageCache.computeIfAbsent(relativePath, key -> {
            Path file = resourcesRoot.resolve(key);
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("找不到图片：" + file.toAbsolutePath());
            }
            try {
                java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(file.toFile());
                if (image == null) {
                    throw new IllegalArgumentException("不是 PNG：" + file);
                }
                return image;
            } catch (IOException e) {
                throw new RuntimeException("读取图片失败：" + file, e);
            }
        });
    }

    PreviewShader shader(String shaderName) {
        return shaderCache.computeIfAbsent(shaderName, key -> {
            try {
                // 先找工具私有着色器（设计稿专用），再回落到项目 assets
                Path local = toolShaderDir.resolve(key + ".json");
                Path dir = Files.isRegularFile(local) ? toolShaderDir : shaderDir;
                return PreviewShader.load(dir, key);
            } catch (IOException e) {
                throw new RuntimeException("加载着色器失败：" + key, e);
            }
        });
    }

    /** 整块 quad（UV 0..1）走项目 shader。调用前用 {@code shader.set(...)} 设好自定义 uniform。 */
    void shaderQuad(PreviewShader shader, int x, int y, int w, int h) {
        shaderQuad(shader, x, y, w, h, 0, 0, 1, 1);
    }

    void shaderQuad(PreviewShader shader, int x, int y, int w, int h,
                    float u0, float v0, float u1, float v1) {
        shaderQuad(shader, (float) x, (float) y, (float) w, (float) h, u0, v0, u1, v1);
    }

    /** 浮点坐标版本（UV 0..1）：轮盘这类按角度算出来的几何需要亚像素精度。 */
    void shaderQuad(PreviewShader shader, float x, float y, float w, float h) {
        shaderQuad(shader, x, y, w, h, 0F, 0F, 1F, 1F);
    }

    /** 浮点坐标版本：轮盘这类按角度算出来的几何需要亚像素精度。 */
    void shaderQuad(PreviewShader shader, float x, float y, float w, float h,
                    float u0, float v0, float u1, float v1) {
        FloatList vertices = new FloatList();
        // 两个三角形显式展开（TRIANGLE_STRIP 的顶点顺序很容易把 quad 画成"V"形缺口）
        addShaderVertex(shader, vertices, x, y, u0, v0);
        addShaderVertex(shader, vertices, x, y + h, u0, v1);
        addShaderVertex(shader, vertices, x + w, y + h, u1, v1);
        addShaderVertex(shader, vertices, x, y, u0, v0);
        addShaderVertex(shader, vertices, x + w, y + h, u1, v1);
        addShaderVertex(shader, vertices, x + w, y, u1, v0);
        drawShader(shader, vertices.data, vertices.size, 6, GL11.GL_TRIANGLES);
    }

    /**
     * 自定义网格。{@code data} 按 json {@code attributes} 的顺序交错排布
     * （Position 3、UV0 2、Color 4、Normal 3）；{@code mode} 用
     * {@code GL11.GL_TRIANGLES} 或 {@code GL11.GL_TRIANGLE_STRIP}。
     */
    void shaderMesh(PreviewShader shader, float[] data, int floats, int vertexCount, int mode) {
        drawShader(shader, data, floats, vertexCount, mode);
    }

    // ══════════════════════════════════════════════════════════════
    //  剪裁 / 导出
    // ══════════════════════════════════════════════════════════════

    void pushClip(int x, int y, int w, int h) {
        int x0 = x;
        int y0 = y;
        int x1 = x + w;
        int y1 = y + h;
        if (!clipStack.isEmpty()) {
            int[] parent = clipStack.peek();
            x0 = Math.max(x0, parent[0]);
            y0 = Math.max(y0, parent[1]);
            x1 = Math.min(x1, parent[0] + parent[2]);
            y1 = Math.min(y1, parent[1] + parent[3]);
        }
        int[] clip = {x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0)};
        clipStack.push(clip);
        applyClip(clip);
    }

    void popClip() {
        if (!clipStack.isEmpty()) {
            clipStack.pop();
        }
        if (clipStack.isEmpty()) {
            gl.bind();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            applyClip(clipStack.peek());
        }
    }

    /** 导出当前帧（逻辑分辨率按倍率最近邻放大）。 */
    void save(String fileName) {
        try {
            gl.save(outDir, fileName);
            System.out.println("[gui-preview] WROTE " + outDir.resolve(
                    fileName.endsWith(".png") ? fileName : fileName + ".png").toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("导出 PNG 失败：" + fileName, e);
        }
    }

    /**
     * 一张"世界背景"底图（深色渐变 + 星空 + 暗角）。
     * 用来判断 GUI 元素在半透明世界画面上是否读得清 —— 只画纯黑底会高估对比度。
     */
    void backdrop() {
        fillGradientV(0, 0, width, height, 0xFF0A0C18, 0xFF03040A);
        java.util.Random random = new java.util.Random(20261031L);
        for (int i = 0; i < 170; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height * 3 / 4);
            int alpha = 18 + random.nextInt(70);
            int size = random.nextInt(12) == 0 ? 2 : 1;
            fill(x, y, size, size, (alpha << 24) | 0xB8C8E8);
        }
        // 两团很淡的"星云"，避免背景是一块死黑
        fillGradientV(30, 40, 220, 90, 0x101E2A50, 0x001E2A50);
        fillGradientV(width - 260, height - 150, 240, 110, 0x0C2A1E3A, 0x002A1E3A);
        // 暗角：上下各一条渐变，左右用窄条叠加
        fillGradientV(0, 0, width, 48, 0x90000000, 0x00000000);
        fillGradientV(0, height - 48, width, 48, 0x00000000, 0xA0000000);
    }

    // ══════════════════════════════════════════════════════════════
    //  内部实现
    // ══════════════════════════════════════════════════════════════

    private void applyClip(int[] clip) {
        gl.bind();
        // 剪裁矩形是 GUI 逻辑坐标，但 glScissor 吃物理像素；乘上原生渲染倍率，
        // 否则 2x 渲染时剪裁框只会盖住左上 1/4。
        int sx = gl.scale;
        int sy = gl.scale;
        if (clip[2] <= 0 || clip[3] <= 0) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, 0, 0);
            return;
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        // OpenGL 的 scissor 原点在左下
        GL11.glScissor(clip[0] * sx, (height - (clip[1] + clip[3])) * sy,
                clip[2] * sx, clip[3] * sy);
    }

    private void blitUV(PreviewTexture texture, int x, int y, int w, int h,
                        float u0, float v0, float u1, float v1, int tint) {
        if (w <= 0 || h <= 0) {
            return;
        }
        useStandardBlend();
        texture.bind(0);
        drawQuad(texProgram, true, texture, x, y, w, h, u0, v0, u1, v1,
                tint, tint, tint, tint);
    }

    /** 恢复 GUI 的默认混合（SRC_ALPHA / ONE_MINUS_SRC_ALPHA）。 */
    private void useStandardBlend() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /**
     * 画一个 quad（TRIANGLE_STRIP 顺序：左上、左下、右下、右上）。
     * 颜色参数顺序：左上、左下、右下、右上 —— fillGradient 就是让上下两组不同。
     */
    private void drawQuad(int program, boolean textured, PreviewTexture texture,
                          float x, float y, float w, float h,
                          float u0, float v0, float u1, float v1,
                          int topLeft, int bottomLeft, int bottomRight, int topRight) {
        gl.bind();
        GL20.glUseProgram(program);
        int projLocation = program == fillProgram ? fillProjLocation : texProjLocation;
        uploadMatrix(projLocation, projMat);
        if (textured) {
            texture.bind(0);
        }
        int stride = textured ? 9 : 7;
        float[] data = new float[stride * 6];
        // 四个角先过一遍 2D 变换，再按两个三角形展开
        float x0 = mapX(x, y), y0 = mapY(x, y);
        float x1 = mapX(x, y + h), y1 = mapY(x, y + h);
        float x2 = mapX(x + w, y + h), y2 = mapY(x + w, y + h);
        float x3 = mapX(x + w, y), y3 = mapY(x + w, y);
        int i = 0;
        i = putVertex(data, i, x0, y0, textured, u0, v0, topLeft);
        i = putVertex(data, i, x1, y1, textured, u0, v1, bottomLeft);
        i = putVertex(data, i, x2, y2, textured, u1, v1, bottomRight);
        i = putVertex(data, i, x0, y0, textured, u0, v0, topLeft);
        i = putVertex(data, i, x2, y2, textured, u1, v1, bottomRight);
        putVertex(data, i, x3, y3, textured, u1, v0, topRight);
        if (textured) {
            uploadAndDraw(data, data.length, 6, TEXTURED_ATTRIBUTE, GL11.GL_TRIANGLES);
        } else {
            uploadAndDraw(data, data.length, 6, FILL_ATTRIBUTE, GL11.GL_TRIANGLES);
        }
    }

    private static int putVertex(float[] data, int offset, float x, float y, boolean textured,
                                 float u, float v, int argb) {
        data[offset++] = x;
        data[offset++] = y;
        data[offset++] = 0;
        if (textured) {
            data[offset++] = u;
            data[offset++] = v;
        }
        data[offset++] = ((argb >> 16) & 0xFF) / 255.0F;
        data[offset++] = ((argb >> 8) & 0xFF) / 255.0F;
        data[offset++] = (argb & 0xFF) / 255.0F;
        data[offset++] = ((argb >>> 24) & 0xFF) / 255.0F;
        return offset;
    }

    private static final int[] FILL_ATTRIBUTE = {3, 4};
    private static final int[] TEXTURED_ATTRIBUTE = {3, 2, 4};

    private void drawText(String text, int startX, int startY, int argb) {
        useStandardBlend();
        if (!font.available()) {
            drawMissingText(text, startX, startY, argb);
            return;
        }
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float a = ((argb >>> 24) & 0xFF) / 255.0F;

        int cellW = font.cellWidth();
        int cellH = font.cellHeight();
        FloatList vertices = new FloatList();
        int penX = startX;
        int penY = startY;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n') {
                penX = startX;
                penY += PreviewFont.LINE_HEIGHT;
                continue;
            }
            PreviewFont.Glyph glyph = font.glyph(cp);
            if (glyph == null) {
                // 未覆盖的码点（多半是中文）：画一个小空心方块保住布局
                addTextQuad(vertices, font.texture(), penX + 1, penY + 1, 5, 7,
                        penX + 1, penY + 1, r, g, b, 0.20F);
                penX += 7;
                continue;
            }
            int baseX = glyph.col() * cellW;
            int baseY = glyph.row() * cellH;
            for (int row = 0; row < cellH; row++) {
                int runStart = -1;
                for (int col = 0; col <= cellW; col++) {
                    boolean on = col < cellW && font.alphaAt(baseX + col, baseY + row) > 0;
                    if (on && runStart < 0) {
                        runStart = col;
                    } else if (!on && runStart >= 0) {
                        addTextQuad(vertices, font.texture(),
                                penX + runStart, penY + row, col - runStart, 1,
                                baseX + runStart, baseY + row, r, g, b, a);
                        runStart = -1;
                    }
                }
            }
            penX += font.advance(cp);
        }

        if (vertices.size > 0) {
            gl.bind();
            GL20.glUseProgram(texProgram);
            uploadMatrix(texProjLocation, projMat);
            font.bind(0);
            uploadAndDraw(vertices.data, vertices.size, vertices.size / 9, TEXTURED_ATTRIBUTE,
                    GL11.GL_TRIANGLES);
        }
    }

    private void addTextQuad(FloatList out, PreviewTexture texture,
                             float x, float y, float w, float h,
                             float pixelX, float pixelY,
                             float r, float g, float b, float a) {
        float imageW = (float) font.imageWidth();
        float imageH = (float) font.imageHeight();
        float u0 = (pixelX + 0.25F) / imageW;
        float u1 = (pixelX + w - 0.25F) / imageW;
        float v0 = (pixelY + 0.25F) / imageH;
        float v1 = (pixelY + h - 0.25F) / imageH;
        // 四个角过 2D 变换（旋转/缩放字体时字形跟着转）
        float x0 = mapX(x, y), y0 = mapY(x, y);
        float x1 = mapX(x, y + h), y1 = mapY(x, y + h);
        float x2 = mapX(x + w, y + h), y2 = mapY(x + w, y + h);
        float x3 = mapX(x + w, y), y3 = mapY(x + w, y);
        // 两个三角形（TRIANGLES）
        addTextVertex(out, x0, y0, u0, v0, r, g, b, a);
        addTextVertex(out, x1, y1, u0, v1, r, g, b, a);
        addTextVertex(out, x2, y2, u1, v1, r, g, b, a);

        addTextVertex(out, x0, y0, u0, v0, r, g, b, a);
        addTextVertex(out, x2, y2, u1, v1, r, g, b, a);
        addTextVertex(out, x3, y3, u1, v0, r, g, b, a);
    }

    private static void addTextVertex(FloatList out, float x, float y, float u, float v,
                                      float r, float g, float b, float a) {
        out.add(x, y, 0, u, v, r, g, b, a);
    }

    /** 字体不可用时的兜底：用色块表示文字占位，宽度按 4px/字符。 */
    private void drawMissingText(String text, int x, int y, int argb) {
        int penX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                y += PreviewFont.LINE_HEIGHT;
                penX = x;
                continue;
            }
            if (c != ' ') {
                fill(penX, y + 1, 3, 6, argb);
            }
            penX += 4;
        }
    }

    private static int shadowColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >> 16) & 0xFF) / 4;
        int g = ((argb >> 8) & 0xFF) / 4;
        int b = (argb & 0xFF) / 4;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void addShaderVertex(PreviewShader shader, FloatList out,
                                 float x, float y, float u, float v) {
        float px = mapX(x, y);
        float py = mapY(x, y);
        for (int i = 0; i < shader.attributes.size(); i++) {
            switch (shader.attributes.get(i)) {
                case "Position" -> out.add(px, py, 0);
                case "UV0" -> out.add(u, v);
                case "Color" -> out.add(1, 1, 1, 1);
                case "Normal" -> out.add(0, 0, 0);
                default -> {
                    for (int k = 0; k < shader.attributeSizes[i]; k++) {
                        out.add(0);
                    }
                }
            }
        }
    }

    private void drawShader(PreviewShader shader, float[] data, int floats,
                            int vertexCount, int mode) {
        gl.bind();
        GL20.glUseProgram(shader.program);
        shader.setMat4("ProjMat", projMat);
        shader.setMat4("ModelViewMat", identity);
        shader.applyBlend();
        shader.bindSamplers();
        float[] vertices = data;
        if (hasTransform()) {
            // 自定义网格（第一个 attribute 是 Position vec3）同样吃 2D 变换
            vertices = data.clone();
            int stride = 0;
            for (int size : shader.attributeSizes) {
                stride += size;
            }
            for (int v = 0; v < vertexCount; v++) {
                int offset = v * stride;
                if (offset + 1 < vertices.length) {
                    float ox = vertices[offset];
                    float oy = vertices[offset + 1];
                    vertices[offset] = mapX(ox, oy);
                    vertices[offset + 1] = mapY(ox, oy);
                }
            }
        }
        uploadAndDraw(vertices, floats, vertexCount, shader.attributeSizes, mode);
    }

    private void uploadAndDraw(float[] data, int floats, int vertexCount, int[] attributeSizes,
                               int mode) {
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        // 小批量走 MemoryStack（快）；大批量（长文本/大网格）走堆缓冲。
        // MemoryStack 默认只有 64KB，float 超过 16K 就会 OutOfMemoryError: Out of stack space
        // —— 这正是长 tooltip / 大段文字第一次跑就崩的原因。
        if (floats > 16384) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(floats);
            buffer.put(data, 0, floats).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);
        } else {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(floats);
                buffer.put(data, 0, floats).flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);
            }
        }
        int strideFloats = 0;
        for (int size : attributeSizes) {
            strideFloats += size;
        }
        int stride = strideFloats * 4;
        int offsetFloats = 0;
        for (int i = 0; i < attributeSizes.length; i++) {
            GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(i, attributeSizes[i], GL11.GL_FLOAT, false,
                    stride, (long) offsetFloats * 4L);
            offsetFloats += attributeSizes[i];
        }
        GL11.glDrawArrays(mode, 0, vertexCount);
    }

    private static void uploadMatrix(int location, float[] columnMajor16) {
        if (location < 0) {
            return;
        }
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(columnMajor16).flip();
        GL20.glUniformMatrix4fv(location, false, buffer);
    }

    private static int linkProgram(String vertexSource, String fragmentSource,
                                   String... attributeNames) {
        int vertex = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        for (int i = 0; i < attributeNames.length; i++) {
            GL20.glBindAttribLocation(program, i, attributeNames[i]);
        }
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            throw new IllegalStateException("内置程序链接失败: " + GL20.glGetProgramInfoLog(program));
        }
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            throw new IllegalStateException("内置着色器编译失败: " + GL20.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private static float[] ortho(int width, int height) {
        return new float[]{
                2.0F / width, 0, 0, 0,
                0, -2.0F / height, 0, 0,
                0, 0, -0.001F, 0,
                -1, 1, 0, 1};
    }

    // ══════════════════════════════════════════════════════════════
    //  内部小工具
    // ══════════════════════════════════════════════════════════════

    /** 可增长的 float 数组，避免每个 quad 都建 ArrayList。 */
    private static final class FloatList {
        float[] data = new float[1 << 12];
        int size;

        void add(float... values) {
            if (size + values.length > data.length) {
                float[] grown = new float[Math.max(data.length * 2, size + values.length)];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }
    }

    // ── 内置着色器（等价于原版 position_color / position_tex_color 的 GUI 用法）──

    private static final String FILL_VERTEX = """
            #version 150 core
            in vec3 Position;
            in vec4 Color;
            uniform mat4 ProjMat;
            out vec4 vColor;
            void main() {
                vColor = Color;
                gl_Position = ProjMat * vec4(Position, 1.0);
            }
            """;

    private static final String FILL_FRAGMENT = """
            #version 150 core
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                fragColor = vColor;
            }
            """;

    private static final String TEX_VERTEX = """
            #version 150 core
            in vec3 Position;
            in vec2 UV0;
            in vec4 Color;
            uniform mat4 ProjMat;
            out vec2 vUV;
            out vec4 vColor;
            void main() {
                vUV = UV0;
                vColor = Color;
                gl_Position = ProjMat * vec4(Position, 1.0);
            }
            """;

    private static final String TEX_FRAGMENT = """
            #version 150 core
            uniform sampler2D Sampler0;
            in vec2 vUV;
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                vec4 texel = texture(Sampler0, vUV);
                fragColor = vec4(texel.rgb * vColor.rgb, texel.a * vColor.a);
            }
            """;
}
