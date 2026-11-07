import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一个隐藏 GLFW 窗口 + 一张离屏 FBO，GLCanvas 的所有绘制都落在这里。
 *
 * <p>分辨率有两层：<b>逻辑分辨率</b>（GUI 坐标，默认 640×400，对应游戏里 GUI scale 2
 * 的 1280×800 窗口）与<b>渲染倍率</b>（默认 2）。</p>
 *
 * <h3>为什么要"原生分辨率渲染"而不是"低分辨率渲染 + 最近邻放大"</h3>
 * <p>早期版本是先用 640×400 渲染、再把每个逻辑像素复制成 2×2 写成 1280×800。
 * 那等于把整张图先降采样到 GUI 逻辑分辨率再放大，所有斜线/圆弧的锯齿都会被放大；
 * 而与 MC 实机一致的做法是：<b>布局仍用 GUI 逻辑坐标，但光栅化发生在物理分辨率</b>
 * —— 投影矩阵把逻辑坐标映射到整个物理 FBO，于是圆环/扇区的着色器柔边、三角形边缘
 * 都在 1280×800 上逐像素采样，斜线只剩 1 物理像素的台阶；位图字体因为纹理是
 * NEAREST、逻辑尺寸整数，反而依旧保持清晰的像素风。</p>
 *
 * <p>所以 {@code scale} 现在的含义是"原生渲染倍率"：{@code -Scale 2} = 逻辑 640×400、
 * 物理 1280×800 直接出图（不再有后处理放大）。</p>
 */
final class PreviewGl implements AutoCloseable {

    /** GUI 逻辑宽高（场景坐标系）。 */
    final int width;
    final int height;
    /** 物理像素宽高（FBO / PNG 尺寸）。 */
    final int pixelWidth;
    final int pixelHeight;
    final int scale;

    private final long window;
    private final int fbo;
    private final int colorTexture;
    private ByteBuffer readBuffer;

    private PreviewGl(int width, int height, int scale,
                      long window, int fbo, int colorTexture) {
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.pixelWidth = width * scale;
        this.pixelHeight = height * scale;
        this.window = window;
        this.fbo = fbo;
        this.colorTexture = colorTexture;
    }

    static PreviewGl open(int width, int height, int scale) {
        if (width <= 0 || height <= 0 || scale <= 0) {
            throw new IllegalArgumentException("画布尺寸/倍率必须是正数");
        }
        int pixelW = width * scale;
        int pixelH = height * scale;
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("GLFW 初始化失败（没有可用的显示环境？）");
        }
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        long window = GLFW.glfwCreateWindow(64, 64, "gui-preview", 0L, 0L);
        if (window == 0L) {
            GLFW.glfwTerminate();
            throw new IllegalStateException("创建隐藏 GLFW 窗口失败（驱动不支持 GL 3.2 core？）");
        }
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();

        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        int color = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, pixelW, pixelH, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, color, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("FBO 不完整: 0x" + Integer.toHexString(status));
        }

        GL11.glViewport(0, 0, pixelW, pixelH);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        System.out.println("[gui-preview] GL " + GL11.glGetString(GL11.GL_VERSION)
                + " / 逻辑画布 " + width + "x" + height
                + " / 原生渲染 " + pixelW + "x" + pixelH + "（×" + scale + "）");

        return new PreviewGl(width, height, scale, window, fbo, color);
    }

    /** 绑定离屏 FBO 并设好 viewport（物理分辨率）—— 每次绘制前都调用一次。 */
    void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, pixelWidth, pixelHeight);
    }

    void clear(int argb) {
        bind();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                ((argb >>> 24) & 0xFF) / 255.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    /** 读回物理分辨率的一帧（左下原点翻成左上原点，直接得到 ARGB）。 */
    BufferedImage read() {
        bind();
        int bytes = pixelWidth * pixelHeight * 4;
        if (readBuffer == null || readBuffer.capacity() < bytes) {
            readBuffer = BufferUtils.createByteBuffer(bytes);
        }
        readBuffer.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, pixelWidth, pixelHeight,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readBuffer);

        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight,
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixelHeight; y++) {
            int srcRow = (pixelHeight - 1 - y) * pixelWidth * 4;
            for (int x = 0; x < pixelWidth; x++) {
                int i = srcRow + x * 4;
                int r = readBuffer.get(i) & 0xFF;
                int g = readBuffer.get(i + 1) & 0xFF;
                int b = readBuffer.get(i + 2) & 0xFF;
                // 输出的 PNG 固定不透明：离屏混合会把 FBO 的 alpha 越叠越低，
                // 看图软件会把低 alpha 区域铺成白色 —— 那会把"暗角"误读成"发白"。
                image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    /**
     * 直接写物理分辨率的 PNG（不再做最近邻放大）。
     *
     * <p>写文件走"临时文件 + 原子替换 + 失败重试"：多个队友可能同时跑同一个场景，
     * 直接往目标文件写会撞上 Windows 的文件占用（表现为 Can't create an ImageOutputStream）。</p>
     */
    void save(Path outDir, String fileName) throws IOException {
        BufferedImage out = read();
        String name = fileName.endsWith(".png") ? fileName : fileName + ".png";
        Files.createDirectories(outDir);
        Path target = outDir.resolve(name);
        Path temp = outDir.resolve(name + ".tmp" + ProcessHandle.current().pid()
                + "-" + System.nanoTime());
        ImageIO.write(out, "png", temp.toFile());
        IOException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                try {
                    Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(60L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        Files.deleteIfExists(temp);
        throw last != null ? last : new IOException("无法写入 " + target);
    }

    @Override
    public void close() {
        GL11.glDeleteTextures(colorTexture);
        GL30.glDeleteFramebuffers(fbo);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}
