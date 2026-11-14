package org.bytechen.rottenruins.client.story;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

/** Reuses each font atlas's vanilla render state, overriding only the story shader. */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StoryTextShader {
    private static final Map<RenderType, RenderType> TYPES = new IdentityHashMap<>();
    private static ShaderInstance shader;
    private static float dissolve;

    private StoryTextShader() {}

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(HallMod.MODID, "story_text"),
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), loaded -> {
            shader = loaded;
            TYPES.clear();
        });
    }

    public static void setDissolve(float value) { dissolve = value; }
    public static boolean available() { return shader != null; }

    public static RenderType renderType(RenderType original) {
        return TYPES.computeIfAbsent(original, type -> new RenderType("rotten_ruins_story_text",
                // Preserve painter order: distance sorting can put an offset shadow over its glyph.
                type.format(), type.mode(), type.bufferSize(), false, StoryTextLayers.SORT_QUADS, () -> {
                    type.setupRenderState();
                    if (shader != null) {
                        // CompositeRenderType.toString() wraps its name in "RenderType[...]".
                        // Inspect the actual vanilla shader before replacing it, rather than its debug text.
                        ShaderInstance atlasShader = RenderSystem.getShader();
                        boolean intensity = atlasShader != null
                                && (atlasShader == GameRenderer.getRendertypeTextIntensityShader()
                                || atlasShader == GameRenderer.getRendertypeTextIntensitySeeThroughShader());
                        RenderSystem.setShader(() -> shader);
                        shader.safeGetUniform("Dissolve").set(dissolve);
                        // TTF atlases store coverage in red; bitmap font atlases use RGBA.
                        shader.safeGetUniform("Intensity").set(intensity ? 1f : 0f);
                    }
                }, type::clearRenderState) {});
    }
}
