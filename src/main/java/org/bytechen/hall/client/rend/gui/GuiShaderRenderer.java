package org.bytechen.hall.client.rend.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.client.rend.SplendidingShaders;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuiShaderRenderer {

    private GuiShaderRenderer() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            GuiShaderManager.getInstance().tick();
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            GuiShaderManager.getInstance().clearAll();
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlayPost(RenderGuiOverlayEvent.Post event) {
        // Only render once per frame — use HOTBAR layer (drawn last before chat)
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        if (!GuiShaderManager.getInstance().hasActiveEffects()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        renderAll(mc);
    }

    private static void renderAll(Minecraft mc) {
        float time = (float) (System.currentTimeMillis() % 1_000_000L) / 1000f;
        float w = (float) mc.getWindow().getGuiScaledWidth();
        float h = (float) mc.getWindow().getGuiScaledHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        for (GuiShaderEffect effect : GuiShaderManager.getInstance().getActiveEffects()) {
            ShaderInstance shader = getShader(effect.getEffectType());
            if (shader == null) continue;

            float intensity = effect.getIntensity();
            if (intensity <= 0.005f) continue;

            renderEffect(shader, w, h, time, intensity);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderEffect(ShaderInstance shader,
                                      float w, float h, float time, float intensity) {
        setUniformSafe(shader, "ScreenSize", w, h);
        setUniformSafe(shader, "Time", time);
        setUniformSafe(shader, "uIntensity", intensity);

        RenderSystem.setShader(() -> shader);

        Matrix4f pose = new Matrix4f();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(pose,  0,  0, 0).uv(0, 0).endVertex();
        buf.vertex(pose,  0,  h, 0).uv(0, 1).endVertex();
        buf.vertex(pose,  w,  h, 0).uv(1, 1).endVertex();
        buf.vertex(pose,  w,  0, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    @Nullable
    static ShaderInstance getShader(String effectType) {
        return switch (effectType) {
            case GuiShaderManager.TYPE_HORROR_VORONOI -> SplendidingShaders.guiHorrorVoronoiShader;
            default -> null;
        };
    }

    private static void setUniformSafe(ShaderInstance shader, String name, float v) {
        if (shader.getUniform(name) != null) {
            shader.safeGetUniform(name).set(v);
        }
    }

    private static void setUniformSafe(ShaderInstance shader, String name, float v0, float v1) {
        if (shader.getUniform(name) != null) {
            shader.safeGetUniform(name).set(v0, v1);
        }
    }
}
