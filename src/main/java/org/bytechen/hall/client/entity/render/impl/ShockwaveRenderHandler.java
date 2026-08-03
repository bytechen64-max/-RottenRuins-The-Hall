package org.bytechen.hall.client.entity.render.impl;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.entities.population.skills.ShockwaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders all active shockwave entities after all other entities
 * have been drawn, so the framebuffer copy captures the complete
 * scene including mobs and items within the distortion area.
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShockwaveRenderHandler {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float pt = event.getPartialTick();

        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ShockwaveEntity sw) {
                ShockwaveRenderer.renderOne(sw, pt, ps);
            }
        }

        ps.popPose();
    }
}
