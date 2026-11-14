package org.bytechen.rottenruins.client.story;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.NetworkDirection;
import org.bytechen.rottenruins.network.all.PacketActionRegistry;
import org.bytechen.rottenruins.network.s2c.FleshbedRitualParticles;
import org.joml.Vector3f;

public final class FleshbedRitualParticlesClient {
    private FleshbedRitualParticlesClient() {}

    public static void register() {
        PacketActionRegistry.register(FleshbedRitualParticles.ACTION, (data, ctx) -> {
            if (ctx.getDirection() != NetworkDirection.PLAY_TO_CLIENT || !(data.getValue() instanceof CompoundTag tag)) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.dimension().location().toString().equals(tag.getString("dimension"))) return;
            var random = mc.level.random;
            var pos = BlockPos.of(tag.getLong("pos"));
            int count = Math.max(0, Math.min(256, tag.getInt("count")));
            for (int i = 0; i < count; i++) {
                var color = random.nextInt(3) == 0 ? new Vector3f(0.025f, 0.008f, 0.012f)
                        : new Vector3f(0.85f, 0.015f, 0.025f);
                var dust = new DustParticleOptions(color, 1.1f + random.nextFloat() * 0.8f);
                var particle = mc.particleEngine.createParticle(dust, pos.getX() + 0.15 + random.nextDouble() * 0.7,
                        pos.getY() + 0.85, pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0, 0, 0);
                if (particle != null) {
                    // The vanilla constructor normalizes speed; assign it afterwards for a true jet.
                    particle.setParticleSpeed((random.nextDouble() - 0.5) * 0.12,
                            0.35 + random.nextDouble() * 0.45, (random.nextDouble() - 0.5) * 0.12);
                    particle.setLifetime(30 + random.nextInt(20));
                }
            }
        });
    }
}
