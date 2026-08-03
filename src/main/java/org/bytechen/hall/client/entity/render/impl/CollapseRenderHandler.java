package org.bytechen.hall.client.entity.render.impl;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.entities.population.skills.CollapseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 渲染所有活跃的坍缩实体。
 * <p>
 * 使用 {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES} 阶段（在光影后处理之前），
 * 并通过 {@link MultiBufferSource.BufferSource} 接入光影渲染管道，
 * 确保与 OptiFine / Iris 等光影模组兼容。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CollapseRenderHandler {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float pt = event.getPartialTick();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CollapseEntity collapse) {
                CollapseRenderer.renderOne(collapse, pt, ps, bufferSource);
            }
        }

        ps.popPose();
        bufferSource.endBatch(CollapseRenderType.COLLAPSE);
    }
}
