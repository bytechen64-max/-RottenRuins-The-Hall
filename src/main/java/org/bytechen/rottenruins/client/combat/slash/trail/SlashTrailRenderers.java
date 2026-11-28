package org.bytechen.rottenruins.client.combat.slash.trail;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bytechen.rottenruins.api.combat.slash.*;
import org.bytechen.rottenruins.client.combat.slash.*;
import org.bytechen.rottenruins.client.rend.slash.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client extension registry: each weapon may select any registered renderer key. */
@Mod.EventBusSubscriber(modid = "rotten_ruins", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlashTrailRenderers {
    public record Frame(AbstractClientPlayer player, SlashClient.Active active, float partialTick,
                        float front, float tail, float alpha) {}
    @FunctionalInterface public interface Drawer { void draw(Frame frame, PoseStack poses, MultiBufferSource buffers); }
    private static final Map<ResourceLocation, Drawer> DRAWERS = new ConcurrentHashMap<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(8192));
    private SlashTrailRenderers() {}
    public static void register(ResourceLocation key, Drawer drawer) { DRAWERS.put(key, drawer); }
    public static boolean hasRenderer(ResourceLocation key) { return DRAWERS.containsKey(key); }

    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !ItemSlashAnimation.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PoseStack poses = event.getPoseStack(); poses.pushPose();
        try {
            for (var entry : SlashClient.activeEntries()) {
                if (entry.getKey().level() != mc.level) continue;
                draw(entry.getKey(), entry.getValue(), event.getPartialTick(), poses);
            }
        } finally { poses.popPose(); }
    }
    private static void draw(AbstractClientPlayer player, SlashClient.Active active, float partialTick, PoseStack poses) {
        var visual = active.visual(); var trail = visual.trail();
        Drawer drawer = DRAWERS.get(trail.renderer());
        if (drawer == null) return;
        float elapsed = player.tickCount - active.startTick() + partialTick;
        float start = visual.animationTicks() * SlashMotion.PREPARE_END, end = visual.animationTicks() * SlashMotion.CUT_END;
        if (elapsed <= start || elapsed >= end + trail.lifetimeTicks()) return;
        float front = Math.max(0, Math.min(1, (elapsed - start) / (end - start)));
        float alpha = elapsed < end ? 1 : 1 - (elapsed - end) / trail.lifetimeTicks();
        drawer.draw(new Frame(player, active, partialTick, front, Math.max(0, front - 0.65F), alpha), poses, BUFFERS);
        BUFFERS.endBatch();
    }
}
