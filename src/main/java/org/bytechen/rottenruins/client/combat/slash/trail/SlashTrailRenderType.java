package org.bytechen.rottenruins.client.combat.slash.trail;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.bytechen.rottenruins.api.combat.slash.SlashTrail;
import java.util.HashMap;
import java.util.Map;

public final class SlashTrailRenderType extends RenderType {
    private static final Map<ResourceLocation, RenderType> TEXTURES = new HashMap<>();
    private static final RenderType AIR = create("slash_air", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 32768, false, false, CompositeState.builder()
            .setShaderState(new ShaderStateShard(()->SlashTrailShaders.air)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    public static RenderType air(){return AIR;}
    private static final RenderType SHADER = create("slash_procedural", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 4096, false, true, state(true, SlashTrail.DEFAULT_TEXTURE));
    private SlashTrailRenderType() { super("unused", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {}); }
    public static RenderType texture(ResourceLocation texture) {
        return TEXTURES.computeIfAbsent(texture, value -> create("slash_texture", DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS, 4096, false, true, state(false, value)));
    }
    public static RenderType shader() { return SHADER; }
    private static CompositeState state(boolean shader, ResourceLocation texture) {
        return CompositeState.builder().setShaderState(new ShaderStateShard(shader ? () -> SlashTrailShaders.shader : GameRenderer::getPositionColorTexShader))
                .setTextureState(new TextureStateShard(texture, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false);
    }
}
