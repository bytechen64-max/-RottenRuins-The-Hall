package org.bytechen.hall.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * 浮点离屏场（{@code RG16F} / {@code RGBA16F}）。
 *
 * <h3>为什么不能用 MC 的 {@code TextureTarget}</h3>
 * <p>1.20.1 的 {@code RenderTarget} 内部格式硬编码 {@code RGBA8}。拿它存"跨帧累积的能量"
 * 有两个致命问题：</p>
 * <ul>
 *   <li><b>低值被量化截断</b>（1/255 以下直接归零）—— 火焰羽流的尾部还没飘出去就死了，
 *       拖尾上会出现一条条等值线状色带；</li>
 *   <li>没有 HDR 余量，密度只能挤在 0~1 里。</li>
 * </ul>
 *
 * <p>{@code RG16F} / {@code RGBA16F} 都在 GL 3.2 core 的<b>必支持可渲染格式</b>表里，
 * 不需要查扩展。注意 {@code RGB16F} <b>不在</b>那张表里 —— 别用。</p>
 *
 * <h3>为什么这里不挂深度附件</h3>
 * <p>平流与合成都是全屏 pass，全程关闭深度测试；只有颜色附件时 FBO 就是完整的。
 * 也正因为不挂深度，它跟"场景深度附件"那套机制完全不相干，不会重蹈"实体变透明"的覆辙。</p>
 */
public final class ItemFxFloatTarget {

    private int fbo;
    private int texture;
    private int width = -1;
    private int height = -1;
    /** GL 内部格式（{@code GL_RG16F} / {@code GL_RGBA16F}），换格式会重建。 */
    private int internalFormat = -1;

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int colorTextureId() {
        return texture;
    }

    public boolean isCreated() {
        return texture != 0 && width > 0 && height > 0;
    }

    /**
     * 保证这张场存在且尺寸正确。
     *
     * @param format {@link ItemFxFormat#RG16F} 或 {@link ItemFxFormat#RGBA16F}
     */
    public void ensure(int wantWidth, int wantHeight, ItemFxFormat format) {
        int internal = format == ItemFxFormat.RG16F ? GL30.GL_RG16F : GL30.GL_RGBA16F;
        if (isCreated() && width == wantWidth && height == wantHeight && internalFormat == internal) {
            return;
        }
        release();
        width = Math.max(1, wantWidth);
        height = Math.max(1, wantHeight);
        internalFormat = internal;

        texture = GlStateManager._genTexture();
        GlStateManager._bindTexture(texture);
        // 通道数与格式必须配对：RG16F 用 GL_RG，RGBA16F 用 GL_RGBA。混用会得到全黑。
        int channels = internal == GL30.GL_RG16F ? GL30.GL_RG : GL11.GL_RGBA;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, width, height, 0,
                channels, GL30.GL_HALF_FLOAT, (ByteBuffer) null);
        setFilterMode(GL11.GL_LINEAR);

        fbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("浮点场 FBO 不完整: " + status
                    + " (" + width + "x" + height + ", format=" + format + ")");
        }
    }

    /** 采样过滤。平流必须 LINEAR —— NEAREST 会让火焰变成颗粒状抖动。 */
    public void setFilterMode(int glFilter) {
        if (texture == 0) return;
        GlStateManager._bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, glFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, glFilter);
        // 平流会往回采到边界外；CLAMP_TO_EDGE 让边界外取到边缘值，REPEAT 会让火烧穿屏幕。
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    public void bindWrite(boolean setViewport) {
        if (!isCreated()) return;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        if (setViewport) {
            RenderSystem.viewport(0, 0, width, height);
        }
    }

    public void clear(float r, float g, float b, float a) {
        if (!isCreated()) return;
        bindWrite(true);
        RenderSystem.clearColor(r, g, b, a);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);
    }

    public void release() {
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
            texture = 0;
        }
        width = -1;
        height = -1;
    }
}
