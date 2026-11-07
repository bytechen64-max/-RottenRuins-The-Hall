package org.bytechen.rottenruins.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * 全屏四边形绘制（ndc -1..1，UV 0..1）。
 *
 * <p>单独抽出来是因为框架里每个效果都要画全屏 pass，而它们分散在不同的包
 * （{@code client.fx.effects} 与 {@code client.rend.glint}），没法共用私有方法。
 * 用 {@code Tesselator.getInstance().getBuilder()} 与既有的 ring/光雾 pass 保持一致 ——
 * 那些 pass 已经证明了这个时点用共享 builder 是安全的。</p>
 */
public final class ItemFxQuad {

    private ItemFxQuad() { }

    public static void draw(ShaderInstance shader) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1D, -1D, 0D).uv(0F, 0F).endVertex();
        builder.vertex(1D, -1D, 0D).uv(1F, 0F).endVertex();
        builder.vertex(1D, 1D, 0D).uv(1F, 1F).endVertex();
        builder.vertex(-1D, 1D, 0D).uv(0F, 1F).endVertex();
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    public static void setFloat(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(value);
    }

    public static void setVec2(ShaderInstance shader, String name, float x, float y) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(x, y);
    }
}
