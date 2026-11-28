import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 「体积光雾」实验台：同一个剪影（程序化画的剑）分别用三种几何跑一遍，各导一张 PNG。
 *
 *   1. baseline —— 现役 outline_ring.fsh（等宽硬环）
 *   2. route2   —— 单 pass 加权径向核光晕 + fBm 扭曲半径
 *   3. route3   —— 遮罩降采样模糊链（多尺度）+ 噪声扰动的云团阈值
 *
 * 用法: java AuraLab <shaderDir> <labShaderDir> <outDir> [time]
 */
public class AuraLab {

    // 画布尺寸。默认 512 是给三条路线的形状对比用的；要看"光雾相对物品有多大"必须用
    // 游戏分辨率量级 —— 光雾半径是**固定像素**，512 画布上一团 52px 的雾会显得比
    // 游戏里(1600p)大三倍多，看图就会误判。火焰那一轮用 1600。
    static int SIZE = 512;
    static int HALF = 256;
    static int QUARTER = 128;

    public static void main(String[] args) throws Exception {
        Path shaderDir = Path.of(args[0]);
        Path labDir = Path.of(args[1]);
        Path outDir = Path.of(args[2]);
        float time = args.length > 3 ? Float.parseFloat(args[3]) : 5f;
        if (args.length > 4) {
            SIZE = Integer.parseInt(args[4]);
            HALF = SIZE / 2;
            QUARTER = SIZE / 4;
        }
        // 可选：火焰的白热核 / 冷尾颜色（十六进制 RGB），默认是熔岩橙 + 暗红
        int coreHex = args.length > 5 ? (int) Long.parseLong(args[5], 16) : 0xFF9E2D;
        int tailHex = args.length > 6 ? (int) Long.parseLong(args[6], 16) : 0x0A6B03;
        float cr = ((coreHex >> 16) & 0xFF) / 255f, cg = ((coreHex >> 8) & 0xFF) / 255f, cb = (coreHex & 0xFF) / 255f;
        float tr = ((tailHex >> 16) & 0xFF) / 255f, tg = ((tailHex >> 8) & 0xFF) / 255f, tb = (tailHex & 0xFF) / 255f;

        initGL();

        // 现役的两个 + 实验的三个（全屏 pass 一律复用 outline_ring.vsh：Position→NDC、UV0→UV）
        int maskProg = link(shaderDir.resolve("outline_mask.vsh"), shaderDir.resolve("outline_mask.fsh"));
        int ringProg = link(shaderDir.resolve("outline_ring.vsh"), shaderDir.resolve("outline_ring.fsh"));
        int haloProg = link(shaderDir.resolve("outline_ring.vsh"), labDir.resolve("lab_ring_halo.fsh"));
        int blurProg = link(shaderDir.resolve("outline_ring.vsh"), labDir.resolve("lab_blur.fsh"));
        int compProg = link(shaderDir.resolve("outline_ring.vsh"), labDir.resolve("lab_ring_composite.fsh"));
        // 真·光雾模糊 pass（游戏里那条链用的就是它）
        int haloBlurProg = link(shaderDir.resolve("outline_ring.vsh"), shaderDir.resolve("outline_halo_blur.fsh"));

        int maskTex = target(SIZE, SIZE, GL11.GL_LINEAR);
        int maskFbo = fbo(maskTex);
        int outTex = target(SIZE, SIZE, GL11.GL_NEAREST);
        int outFbo = fbo(outTex);
        int halfA = target(HALF, HALF, GL11.GL_LINEAR);
        int halfFboA = fbo(halfA);
        int halfB = target(HALF, HALF, GL11.GL_LINEAR);
        int halfFboB = fbo(halfB);
        int quarterA = target(QUARTER, QUARTER, GL11.GL_LINEAR);
        int quarterFboA = fbo(quarterA);
        int quarterB = target(QUARTER, QUARTER, GL11.GL_LINEAR);
        int quarterFboB = fbo(quarterB);

        int fullVao = fullscreenQuad();
        int itemVao = itemQuad();
        int swordTex = swordTexture();

        // ── 剪影遮罩：平坦的淡紫白色（这一轮只比"形状"，颜色刻意压成单色）──
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, maskFbo);
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_BLEND);
        GL20.glUseProgram(maskProg);
        matrix(maskProg, "ModelViewMat", new float[]{
                0.44f, 0f, 0f, 0f,
                0f, 1.10f, 0f, 0f,
                0f, 0f, 1f, 0f,
                -0.22f, -0.55f, 0f, 1f});
        matrix(maskProg, "ProjMat", identity());
        vec4(maskProg, "MaskColor", 0.78f, 0.84f, 1.0f, 1f);
        vec4(maskProg, "SecondaryColor", 0.45f, 0.35f, 0.95f, 1f);
        vec4(maskProg, "PaletteColor0", 0.78f, 0.84f, 1.0f, 1f);
        vec4(maskProg, "PaletteColor1", 0.45f, 0.35f, 0.95f, 1f);
        flt(maskProg, "ColorMode", 0f);
        flt(maskProg, "ColorScrollSpeed", 1f);
        flt(maskProg, "ColorSpeed", 4f);
        flt(maskProg, "ColorFloor", 0f);
        flt(maskProg, "Time", time);
        flt(maskProg, "PaletteSize", 2f);
        flt(maskProg, "AlphaCutoff", 0.1f);
        vec2(maskProg, "ColorFreq", 18f, 12f);
        GL20.glUniform1i(GL20.glGetUniformLocation(maskProg, "Sampler0"), 0);
        bindTex0(swordTex);
        GL30.glBindVertexArray(itemVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        // ── 1) baseline：现役硬环 ──
        beginOut(outFbo);
        GL20.glUseProgram(ringProg);
        flt(ringProg, "OutlineWidth", 3.0f);
        flt(ringProg, "Opacity", 1f);
        flt(ringProg, "AlphaTest", 0.5f);
        vec2(ringProg, "ScreenSize", SIZE, SIZE);
        GL20.glUniform1i(GL20.glGetUniformLocation(ringProg, "MaskSampler"), 0);
        bindTex0(maskTex);
        GL30.glBindVertexArray(fullVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        save(outDir.resolve("baseline.png"), outFbo);

        // ── 2) route 2：单 pass 软光晕 ──
        beginOut(outFbo);
        GL20.glUseProgram(haloProg);
        flt(haloProg, "OutlineWidth", 3.0f);
        flt(haloProg, "HaloWidth", 46f);
        flt(haloProg, "HaloStrength", 1.0f);
        flt(haloProg, "NoiseScale", 5.5f);
        flt(haloProg, "Time", time);
        flt(haloProg, "Opacity", 1f);
        flt(haloProg, "AlphaTest", 0.5f);
        vec2(haloProg, "ScreenSize", SIZE, SIZE);
        GL20.glUniform1i(GL20.glGetUniformLocation(haloProg, "MaskSampler"), 0);
        bindTex0(maskTex);
        GL30.glBindVertexArray(fullVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        save(outDir.resolve("route2.png"), outFbo);

        // ── 3) route 3：降采样模糊链 → 合成 ──
        // 1/2：X 用 2 个源纹素当步长（顺带降采样），Y 用 1 个目标纹素
        blurPass(blurProg, fullVao, maskTex, halfFboA, HALF, HALF, 2.0f / SIZE, 0f);
        blurPass(blurProg, fullVao, halfA, halfFboB, HALF, HALF, 0f, 1.0f / HALF);
        // 1/4：再降一次
        blurPass(blurProg, fullVao, halfB, quarterFboA, QUARTER, QUARTER, 2.0f / HALF, 0f);
        blurPass(blurProg, fullVao, quarterA, quarterFboB, QUARTER, QUARTER, 0f, 1.0f / QUARTER);

        beginOut(outFbo);
        GL20.glUseProgram(compProg);
        flt(compProg, "OutlineWidth", 3.0f);
        flt(compProg, "AlphaTest", 0.5f);
        flt(compProg, "Opacity", 1f);
        flt(compProg, "HaloStrength", 1.0f);
        flt(compProg, "NoiseScale", 4.5f);
        flt(compProg, "Time", time);
        vec2(compProg, "ScreenSize", SIZE, SIZE);
        GL20.glUniform1i(GL20.glGetUniformLocation(compProg, "MaskSampler"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(compProg, "HaloHalf"), 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(compProg, "HaloQuarter"), 2);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, halfB);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, quarterB);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL30.glBindVertexArray(fullVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        save(outDir.resolve("route3.png"), outFbo);

        // ── 4) 火焰 + 光雾：用**真·着色器**再跑一遍完整管线 ──
        // 遮罩重画一次，这次走火焰颜色模式（游戏里 MaskColor 来自物品的 outlineColor()）
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, maskFbo);
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_BLEND);
        GL20.glUseProgram(maskProg);
        matrix(maskProg, "ModelViewMat", new float[]{
                0.44f, 0f, 0f, 0f,
                0f, 1.10f, 0f, 0f,
                0f, 0f, 1f, 0f,
                -0.22f, -0.55f, 0f, 1f});
        matrix(maskProg, "ProjMat", identity());
        vec4(maskProg, "MaskColor", cr, cg, cb, 1f);                 // 白热核（物品的 outlineColor()）
        vec4(maskProg, "SecondaryColor", tr, tg, tb, 1f);            // 冷尾（outlineSecondaryColor()）
        vec4(maskProg, "PaletteColor0", cr, cg, cb, 1f);
        vec4(maskProg, "PaletteColor1", tr, tg, tb, 1f);
        flt(maskProg, "ColorMode", 4f);                             // ★ 火焰
        flt(maskProg, "ColorScrollSpeed", 1f);
        flt(maskProg, "ColorSpeed", 4f);
        flt(maskProg, "ColorFloor", 0f);
        flt(maskProg, "Time", time);
        flt(maskProg, "PaletteSize", 2f);
        flt(maskProg, "AlphaCutoff", 0.1f);
        vec2(maskProg, "ColorFreq", 18f, 12f);
        GL20.glUniform1i(GL20.glGetUniformLocation(maskProg, "Sampler0"), 0);
        bindTex0(swordTex);
        GL30.glBindVertexArray(itemVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        // 真·模糊链：步长与 ItemOutlinePipeline.buildHaloChain 逐字一致。
        // ★ BlurScale 必须显式设 —— 裸 GL 程序里没人设过的 uniform 是 0.0，
        //   会把步长乘成 0（等于不模糊）。游戏里这个值来自 outline_halo_blur.json。
        flt(haloBlurProg, "BlurScale", 1f);
        blurPass(haloBlurProg, fullVao, maskTex, halfFboA, HALF, HALF, 2.0f / SIZE, 0f);
        blurPass(haloBlurProg, fullVao, halfA, halfFboB, HALF, HALF, 0f, 1.0f / HALF);
        blurPass(haloBlurProg, fullVao, halfB, quarterFboA, QUARTER, QUARTER, 2.0f / HALF, 0f);
        blurPass(haloBlurProg, fullVao, quarterA, quarterFboB, QUARTER, QUARTER, 0f, 1.0f / QUARTER);

        // 对照组：只有火线；正片：火线 + 光雾（默认参数）
        fireRing(outFbo, fullVao, ringProg, maskTex, halfB, quarterB, 0f, time,
                outDir.resolve("fire_line.png"));
        fireRing(outFbo, fullVao, ringProg, maskTex, halfB, quarterB, 0.9f, time,
                outDir.resolve("fire_aura.png"));

        // 再跑一遍"更浓更散"的：扩散 1.4 倍 + 强度 1.5（给一个可选的档位）
        flt(haloBlurProg, "BlurScale", 1.4f);
        blurPass(haloBlurProg, fullVao, maskTex, halfFboA, HALF, HALF, 2.0f / SIZE, 0f);
        blurPass(haloBlurProg, fullVao, halfA, halfFboB, HALF, HALF, 0f, 1.0f / HALF);
        blurPass(haloBlurProg, fullVao, halfB, quarterFboA, QUARTER, QUARTER, 2.0f / HALF, 0f);
        blurPass(haloBlurProg, fullVao, quarterA, quarterFboB, QUARTER, QUARTER, 0f, 1.0f / QUARTER);
        ARCLAB = 1.0f;
        fireRing(outFbo, fullVao, ringProg, maskTex, halfB, quarterB, 0.9f, time,
                outDir.resolve("arc.png"));
        ARCLAB = 0.0f;

        fireRing(outFbo, fullVao, ringProg, maskTex, halfB, quarterB, 1.5f, time,
                outDir.resolve("fire_aura_strong.png"));

        // ── 5) 原型：经向平流的反馈缓冲（Eulerian flame）──
        // 与描边那套的根本差别：这里的图案不是每帧独立求值，而是"上一帧的密度被搬过来"。
        int plasmaA = target(HALF, HALF, GL11.GL_LINEAR);
        int plasmaFboA = fbo(plasmaA);
        int plasmaB = target(HALF, HALF, GL11.GL_LINEAR);
        int plasmaFboB = fbo(plasmaB);
        int advectProg = link(shaderDir.resolve("outline_ring.vsh"), labDir.resolve("lab_plasma_advect.fsh"));
        int plasmaCompProg = link(shaderDir.resolve("outline_ring.vsh"), labDir.resolve("lab_plasma_composite.fsh"));

        // 先把两张图清空（ping-pong 初始状态必须是确定的）
        for (int f : new int[]{plasmaFboA, plasmaFboB}) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, f);
            GL11.glViewport(0, 0, HALF, HALF);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        int src = plasmaA, dst = plasmaB, srcFbo = plasmaFboA, dstFbo = plasmaFboB;
        float pt = time;
        for (int i = 0; i < 140; i++) {
            pt += 1.0f / 45.0f;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, dstFbo);
            GL11.glViewport(0, 0, HALF, HALF);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL20.glUseProgram(advectProg);
            vec2(advectProg, "ScreenSize", SIZE, SIZE);
            flt(advectProg, "Time", pt);
            flt(advectProg, "Dt", 1f / 45f);
            flt(advectProg, "Buoyancy", 0.28f);
            flt(advectProg, "Dissipation", 0.955f);
            flt(advectProg, "Inject", 0.16f);
            flt(advectProg, "Turbulence", 0.55f);
            GL20.glUniform1i(GL20.glGetUniformLocation(advectProg, "Prev"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(advectProg, "Fuel"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, src);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL30.glBindVertexArray(fullVao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            int ts = src; src = dst; dst = ts;
            int tf = srcFbo; srcFbo = dstFbo; dstFbo = tf;
        }

        beginOut(outFbo);
        GL20.glUseProgram(plasmaCompProg);
        vec2(plasmaCompProg, "ScreenSize", SIZE, SIZE);
        flt(plasmaCompProg, "Time", pt);
        flt(plasmaCompProg, "Gain", 2.4f);
        flt(plasmaCompProg, "MaskGate", 1f);
        GL20.glUniform1i(GL20.glGetUniformLocation(plasmaCompProg, "Density"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(plasmaCompProg, "Mask"), 1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, src);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL30.glBindVertexArray(fullVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        save(outDir.resolve("plasma.png"), outFbo);

        // ── 6) 真·火焰：游戏里那两个着色器 + 真·RG16F 浮点场 ──
        // 这一跑同时验证三件事：flame_sim / flame_composite 能否编译链接、
        // RG16F 的 FBO 在这台驱动上能否建起来、平流场能不能真的长出羽流。
        int flameSimProg = link(shaderDir.resolve("outline_ring.vsh"), shaderDir.resolve("flame_sim.fsh"));
        int flameCompProg = link(shaderDir.resolve("outline_ring.vsh"), shaderDir.resolve("flame_composite.fsh"));
        int[] simA = floatTarget(HALF, HALF);
        int[] simB = floatTarget(HALF, HALF);
        for (int[] t : new int[][]{simA, simB}) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, t[0]);
            GL11.glViewport(0, 0, HALF, HALF);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        float ft = time;
        for (int i = 0; i < 200; i++) {
            ft += 1.0f / 45.0f;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, simA[0]);
            GL11.glViewport(0, 0, HALF, HALF);
            GL11.glDisable(GL11.GL_BLEND);
            GL20.glUseProgram(flameSimProg);
            vec2(flameSimProg, "ScreenSize", SIZE, SIZE);
            vec2(flameSimProg, "AnchorUv", 0.5f, 0.5f);   // 剑在正中
            vec2(flameSimProg, "UpUv", 0f, 1f);
            flt(flameSimProg, "Time", ft);
            flt(flameSimProg, "Dt", 1f / 45f);
            // 与 SplendidingConfig 的默认值逐字一致
            flt(flameSimProg, "Rise", 0.20f);
            flt(flameSimProg, "Buoyancy", 0.35f);
            flt(flameSimProg, "Turbulence", 0.55f);
            flt(flameSimProg, "Inject", 0.70f);
            flt(flameSimProg, "Cooling", 3.4f);
            flt(flameSimProg, "SmokeYield", 0.35f);
            flt(flameSimProg, "VentSpeed", 1.6f);
            GL20.glUniform1i(GL20.glGetUniformLocation(flameSimProg, "Prev"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(flameSimProg, "Fuel"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, simB[1]);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL30.glBindVertexArray(fullVao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            int[] swap = simA;
            simA = simB;
            simB = swap;
        }

        beginOut(outFbo);
        GL20.glUseProgram(flameCompProg);
        vec2(flameCompProg, "ScreenSize", SIZE, SIZE);
        flt(flameCompProg, "Gain", 2.4f);
        flt(flameCompProg, "TempScale", 0.85f);
        flt(flameCompProg, "SmokeDarken", 0.45f);
        flt(flameCompProg, "Sparkle", 0.55f);
        // CrimsonVow 的 ACCENT_FROM 亮粉 = 1.00/0.31/0.72（物品侧经 ItemFxParams 传进来）
        GL20.glUniform3f(GL20.glGetUniformLocation(flameCompProg, "FlameTint"), 1.0f, 0.31f, 0.72f);
        GL20.glUniform1i(GL20.glGetUniformLocation(flameCompProg, "Sim"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(flameCompProg, "Mask"), 1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, simB[1]);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL30.glBindVertexArray(fullVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        save(outDir.resolve("flame_real.png"), outFbo);

        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
                System.out.println("WROTE baseline/route2/route3/fire_line/fire_aura(+strong) -> " + outDir);
    }

    /**
     * 用**真·outline_ring.fsh** 合成一次火焰描边。
     *
     * <p>{@code strength = 0} 时 HaloStrength=0，着色器整段跳过光雾 —— 那就是"只有火线"
     * 的对照组，用来确认光雾到底加了多少东西。</p>
     */
    static void fireRing(int outFbo, int fullVao, int ringProg, int maskTex, int halfTex, int wideTex,
                         float strength, float time, Path out) throws Exception {
        beginOut(outFbo);
        GL20.glUseProgram(ringProg);
        vec2(ringProg, "ScreenSize", SIZE, SIZE);
        flt(ringProg, "OutlineWidth", 3.0f);
        flt(ringProg, "Opacity", 1f);
        flt(ringProg, "AlphaTest", 0.5f);
        flt(ringProg, "HaloStrength", strength);
        flt(ringProg, "HaloNoise", 0.8f);
        flt(ringProg, "HaloWarp", 1f);
        flt(ringProg, "Time", time);
        vec2(ringProg, "HaloNoiseOrigin", 0.5f, 0.5f);   // 剑就在画面正中
        // 电弧算子（outline_ring.fsh 新增的一层）：ARCLAB 为 0 时等于没开
        flt(ringProg, "ArcStrength", ARCLAB);
        flt(ringProg, "ArcScale", 26.0f);
        flt(ringProg, "ArcOffset", 0.0f);
        flt(ringProg, "ArcThreshold", 0.15f);
        flt(ringProg, "ArcWidth", 0.10f);
        flt(ringProg, "ArcGain", 1.0f);
        flt(ringProg, "ArcSpeed", 1.6f);
        vec2(ringProg, "ArcBandExtent", 0.0f, 0.0f);
        flt(ringProg, "ArcAniso", 1.0f);
        flt(ringProg, "ArcReach", 0.55f);   // 剪影锚定的电弧带（光雾图当距离代理）
        flt(ringProg, "ArcSub", 0.25f);
        flt(ringProg, "ArcTonemap", 1.0f);
        vec2(ringProg, "ArcNoiseOrigin", 0.5f, 0.5f);
        GL20.glUniform3f(GL20.glGetUniformLocation(ringProg, "ArcColorA"), 1.0f, 0.41f, 0.72f);
        GL20.glUniform3f(GL20.glGetUniformLocation(ringProg, "ArcColorB"), 0.62f, 0.42f, 1.0f);
        GL20.glUniform1i(GL20.glGetUniformLocation(ringProg, "MaskSampler"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(ringProg, "HaloHalf"), 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(ringProg, "HaloWide"), 2);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, halfTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, wideTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL30.glBindVertexArray(fullVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        save(out, outFbo);
    }

    // ────────────────────────── 渲染小工具 ──────────────────────────

    static long window;

    static void initGL() {
        GLFW.glfwInit();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        window = GLFW.glfwCreateWindow(64, 64, "aura-lab", 0L, 0L);
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /** 合成到主输出：清成深色背景 + 加法混合 —— 与游戏里描边的画法一致。 */
    static void beginOut(int outFbo) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, outFbo);
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glClearColor(0.045f, 0.05f, 0.07f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    static void blurPass(int prog, int vao, int srcTex, int dstFbo, int w, int h, float sx, float sy) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, dstFbo);
        GL11.glViewport(0, 0, w, h);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL20.glUseProgram(prog);
        vec2(prog, "TexelStep", sx, sy);
        GL20.glUniform1i(GL20.glGetUniformLocation(prog, "Source"), 0);
        bindTex0(srcTex);
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    static int target(int w, int h, int filter) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return tex;
    }

    /** 建一张 RG16F 浮点场，返回 {fbo, texture}。与游戏的 ItemFxFloatTarget 同格式。 */
    static int[] floatTarget(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG16F, w, h, 0,
                GL30.GL_RG, GL30.GL_HALF_FLOAT, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        int f = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, f);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("RG16F FBO 不完整: " + status);
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        System.out.println("RG16F " + w + "x" + h + " FBO OK");
        return new int[]{f, tex};
    }

    static float ARCLAB = 0f;

    static int fbo(int tex) {
        int f = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, f);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("FBO incomplete: " + status);
        }
        return f;
    }

    /** 全屏四边形：Position 直接是 NDC，UV0 = 0..1。 */
    static int fullscreenQuad() {
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        float[] v = {
                -1f, -1f, 0f, 0f, 0f,
                1f, -1f, 0f, 1f, 0f,
                1f, 1f, 0f, 1f, 1f,
                -1f, -1f, 0f, 0f, 0f,
                1f, 1f, 0f, 1f, 1f,
                -1f, 1f, 0f, 0f, 1f,
        };
        upload(v);
        // 固定属性位置（0=Position, 1=Color, 2=UV0）—— 见 link() 里的 glBindAttribLocation。
        // 这样所有 program 共用同一套 VAO 布局。
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 20, 0L);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 20, 12L);
        return vao;
    }

    /** 物品四边形：Position = 模型空间 [0,1]²，Color 全白，UV0 = 0..1。 */
    static int itemQuad() {
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        float[][] q = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 0f}, {1f, 1f}, {0f, 1f}};
        float[] v = new float[q.length * 9];
        for (int i = 0; i < q.length; i++) {
            int o = i * 9;
            v[o] = q[i][0];
            v[o + 1] = q[i][1];
            v[o + 2] = 0f;
            v[o + 3] = 1f;
            v[o + 4] = 1f;
            v[o + 5] = 1f;
            v[o + 6] = 1f;
            v[o + 7] = q[i][0];
            v[o + 8] = q[i][1];
        }
        upload(v);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 36, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 36, 12L);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 36, 28L);
        return vao;
    }

    static void upload(float[] data) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(data.length);
            fb.put(data).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        }
    }

    /** 设一个 VAO 的属性指针（按当前 program 的属性名查位置）。 */
    static void attrib(int prog, String name, int size, int stride, long offset) {
        int loc = GL20.glGetAttribLocation(prog, name);
        if (loc < 0) return;
        GL20.glEnableVertexAttribArray(loc);
        GL20.glVertexAttribPointer(loc, size, GL11.GL_FLOAT, false, stride, offset);
    }

    /** 程序化剑形贴图：剑尖 + 剑身 + 护手 + 握把 + 柄头，抗锯齿 → 一个像样的剪影。 */
    static int swordTexture() throws Exception {
        int w = 64, h = 160;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);

        Path2D blade = new Path2D.Float();
        blade.moveTo(32, 4);
        blade.lineTo(37, 20);
        blade.lineTo(37, 104);
        blade.lineTo(27, 104);
        blade.lineTo(27, 20);
        blade.closePath();
        g.fill(blade);

        g.fill(new Rectangle2D.Float(13, 104, 38, 9));      // 护手
        g.fill(new Rectangle2D.Float(29, 113, 6, 32));      // 握把
        g.fill(new Ellipse2D.Float(25, 144, 14, 13));       // 柄头
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Float(22, 26, 20, 20));        // 剑身上一个"符文"环，让剪影有内部结构
        g.dispose();

        int[] px = new int[w * h];
        img.getRGB(0, 0, w, h, px, 0, w);
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = px[y * w + x];
                buf.put((byte) ((argb >> 16) & 0xFF));
                buf.put((byte) ((argb >> 8) & 0xFF));
                buf.put((byte) (argb & 0xFF));
                buf.put((byte) ((argb >>> 24) & 0xFF));
            }
        }
        buf.flip();
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        return tex;
    }

    static void bindTex0(int tex) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
    }

    static float[] identity() {
        return new float[]{1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
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

    static void vec4(int prog, String name, float x, float y, float z, float w) {
        GL20.glUniform4f(GL20.glGetUniformLocation(prog, name), x, y, z, w);
    }

    static void save(Path path, int fbo) throws Exception {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        ByteBuffer buf = ByteBuffer.allocateDirect(SIZE * SIZE * 4);
        GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = (y * SIZE + x) * 4;
                int r = buf.get(i) & 0xFF;
                int gg = buf.get(i + 1) & 0xFF;
                int b = buf.get(i + 2) & 0xFF;
                int a = buf.get(i + 3) & 0xFF;
                img.setRGB(x, SIZE - 1 - y, (a << 24) | (r << 16) | (gg << 8) | b);
            }
        }
        ImageIO.write(img, "png", new File(path.toString()));
    }

    static int link(Path vs, Path fs) throws Exception {
        int v = compile(vs, GL20.GL_VERTEX_SHADER);
        int f = compile(fs, GL20.GL_FRAGMENT_SHADER);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        // 显式绑死属性位置，链接前必须做完 —— 否则每个 program 自己分配 slot，
        // 同一个 VAO 在不同 pass 上就会读错数据（症状是整片黑或者几何飞掉）。
        GL20.glBindAttribLocation(p, 0, "Position");
        GL20.glBindAttribLocation(p, 1, "Color");
        GL20.glBindAttribLocation(p, 2, "UV0");
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) != GL20.GL_TRUE) {
            throw new IllegalStateException("link failed " + fs + ": " + GL20.glGetProgramInfoLog(p));
        }
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        return p;
    }

    static int compile(Path path, int type) throws Exception {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, Files.readString(path));
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) != GL20.GL_TRUE) {
            throw new IllegalStateException("compile failed " + path + ": " + GL20.glGetShaderInfoLog(s));
        }
        return s;
    }
}
