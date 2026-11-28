package org.bytechen.rottenruins.client.entity.render.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import org.bytechen.rottenruins.network.s2c.OlympiadImpactPacket;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** Short, depth-tested white/violet cracks; never synthesize hits from nearby entities. */
@Mod.EventBusSubscriber(modid = HallMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OlympiadImpactRenderer {
    private static final int LIFE = 5;
    private static final int MAX_VISIBLE = 256;
    private static final Deque<Impact> IMPACTS = new ArrayDeque<>();
    private static ClientLevel currentLevel;

    private record Impact(int entityId, UUID uuid, Vec3 fallback, float size, long start) {}

    private OlympiadImpactRenderer() {}

    public static void register() {
        PacketActionRegistry.register(OlympiadImpactPacket.ACTION, (data, ctx) -> {
            if (ctx.getDirection() != NetworkDirection.PLAY_TO_CLIENT
                    || !(data.getValue() instanceof CompoundTag tag)) return;
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || !level.dimension().location().toString().equals(tag.getString("dimension"))) return;
            resetForLevel(level);
            var hits = tag.getList("hits", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(hits.size(), OlympiadImpactPacket.MAX_HITS); i++) {
                CompoundTag hit = hits.getCompound(i);
                if (!hit.hasUUID("uuid")) continue;
                while (IMPACTS.size() >= MAX_VISIBLE) IMPACTS.removeFirst();
                IMPACTS.addLast(new Impact(hit.getInt("entity"), hit.getUUID("uuid"),
                        new Vec3(hit.getDouble("x"), hit.getDouble("y"), hit.getDouble("z")),
                        Mth.clamp(hit.getFloat("size"), 0.18f, 0.7f), level.getGameTime()));
            }
        });
    }

    private static void resetForLevel(ClientLevel level) {
        if (currentLevel != level) {
            IMPACTS.clear();
            currentLevel = level;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        resetForLevel(level);
        if (level != null) IMPACTS.removeIf(hit -> level.getGameTime() - hit.start >= LIFE);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        resetForLevel(mc.level);
        if (mc.level == null || IMPACTS.isEmpty()) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Impact hit : IMPACTS) {
                float age = mc.level.getGameTime() - hit.start + event.getPartialTick();
                if (age < 0 || age >= LIFE) continue;
                Entity target = mc.level.getEntity(hit.entityId);
                Vec3 center = hit.fallback;
                double surface = hit.size * 1.35d;
                if (target != null && target.getUUID().equals(hit.uuid) && !target.isRemoved()) {
                    center = new Vec3(Mth.lerp(event.getPartialTick(), target.xOld, target.getX()),
                            Mth.lerp(event.getPartialTick(), target.yOld, target.getY()) + target.getBbHeight() * 0.5d,
                            Mth.lerp(event.getPartialTick(), target.zOld, target.getZ()));
                    surface = target.getBbWidth() * 0.72d + 0.04d;
                }
                if (center.distanceToSqr(camera) > 64 * 64) continue;
                Vec3 front = center.add(camera.subtract(center).normalize().scale(surface));
                pose.pushPose();
                pose.translate(front.x - camera.x, front.y - camera.y, front.z - camera.z);
                pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
                emitCrack(buffer, pose.last().pose(), hit.size, age / LIFE, hit.entityId);
                pose.popPose();
            }
            tess.end();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void emitCrack(BufferBuilder buffer, Matrix4f matrix, float size, float t, int seed) {
        float alpha = (1.0f - t) * (1.0f - t);
        float s = size * (0.65f + 0.35f * Math.min(1.0f, t * 4.0f));
        Vec3[] diamond = {new Vec3(0, s, 0), new Vec3(s * 0.65d, 0, 0),
                new Vec3(0, -s * 0.85d, 0), new Vec3(-s * 0.65d, 0, 0)};
        for (int i = 0; i < 4; i++) {
            line(buffer, matrix, diamond[i], diamond[(i + 1) % 4], alpha);
            double angle = i * Math.PI / 2 + seed * 0.31d;
            Vec3 direction = new Vec3(Math.cos(angle), Math.sin(angle), 0);
            Vec3 tip = direction.scale(s * (1.2d + t * 1.6d));
            Vec3 bend = direction.scale(s * 0.7d).add(-direction.y * s * 0.25d, direction.x * s * 0.25d, 0);
            line(buffer, matrix, Vec3.ZERO, bend, alpha * 0.75f);
            line(buffer, matrix, bend, tip, alpha * 0.65f);
            // Detached short splinters continue outward as the central crack fades.
            Vec3 shard = direction.scale(size * (1.5d + t * 3.0d));
            line(buffer, matrix, shard, shard.add(direction.scale(size * 0.3d)), alpha);
        }
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, float alpha) {
        OlympiadCubeRenderer.emitEdge(buffer, matrix, a, b, 0.018f, 0.60f, 0.26f, 1.0f, alpha * 0.45f);
        OlympiadCubeRenderer.emitEdge(buffer, matrix, a, b, 0.005f, 0.95f, 0.86f, 1.0f, alpha);
    }
}
