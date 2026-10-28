package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.client.rend.MeteorScreenEffect;
import org.bytechen.rottenruins.client.rend.ScreenShake;
import org.bytechen.rottenruins.client.rend.glint.HeldItemOutlineCompat;
import org.bytechen.rottenruins.overworld.registry.entities.population.skills.MeteorEventEntity;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 陨石事件的绘制入口（{@code AFTER_TRIPWIRE_BLOCKS}）。
 *
 * <p>为什么不在 {@code EntityRenderer#render} 里画：十字星在 150 格高空、
 * 拖尾几十格长，都远超实体自己的包围盒；而且实体渲染阶段地形还没写完深度，
 * 高空的东西会"穿透"山体。挪到地形之后，遮挡关系才成立。</p>
 *
 * <p>"刚砸中"那一帧在这里抓：{@link MeteorEventEntity#consumeClientImpact()}
 * 是一次性的，读到就触发屏幕抖动与 3bit 屏效 —— 不需要额外的网络包。</p>
 */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MeteorEventRenderHandler {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;
        if (HeldItemOutlineCompat.isOculusShadowPass()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<MeteorEventEntity> list = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof MeteorEventEntity meteor) {
                if (list == null) list = new ArrayList<>(1);
                list.add(meteor);
            }
        }
        if (list == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float pt = event.getPartialTick();

        ps.pushPose();
        ps.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f view = new Matrix4f(ps.last().pose());
        float[] basis = new float[9];
        CrimsonVowRenderSupport.viewBasis(ps.last(), basis);

        try {
            for (MeteorEventEntity meteor : list) {
                if (meteor.consumeClientImpact()) {
                    double dist = meteor.position().distanceTo(camera);
                    float near = (float) Mth.clamp(1.0 - dist / 140.0, 0.0, 1.0);
                    ScreenShake.impact(1.0f);
                    MeteorScreenEffect.trigger(meteor.getSeed() % 977, meteor.position());
                }
                MeteorEventRenderer.draw(meteor, pt, view, basis, camera);
            }
        } finally {
            ps.popPose();
        }
    }
}
