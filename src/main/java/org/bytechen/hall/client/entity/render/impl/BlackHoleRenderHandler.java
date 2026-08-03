package org.bytechen.hall.client.entity.render.impl;

import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.entities.population.skills.BlackHoleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 在所有实体绘制后渲染黑洞 billboard，确保场景拷贝捕获完整场景
 * （与 {@link ShockwaveRenderHandler} 相同的注入时机）。
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlackHoleRenderHandler {

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
            if (entity instanceof BlackHoleEntity bh) {
                BlackHoleRenderer.renderOne(bh, pt, ps);
            }
        }

        ps.popPose();
    }
}
