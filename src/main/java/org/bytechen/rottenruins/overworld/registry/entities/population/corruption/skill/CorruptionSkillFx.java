package org.bytechen.rottenruins.overworld.registry.entities.population.corruption.skill;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 技能视觉反馈工具（服务端调用，自动广播给追踪客户端）。
 *
 * <p>与 {@code CorruptionSkillFx} 分离的原因：生物技能只需要"命中反馈"这一层，
 * 而 Ball/Icosahedron 渲染属于客户端专属。这里只做服务端 sendParticles，
 * 因此任何生物在任何端都能安全调用。
 */
public final class CorruptionSkillFx {

    private CorruptionSkillFx() {}

    /** 把 ARGB 拆成 0~1 的 RGB */
    private static DustParticleOptions dustOf(int argb, float scale) {
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        return new DustParticleOptions(new Vector3f(r, g, b), scale);
    }

    /** 命中反馈：一层向内收的红点 + 一层烟雾，体积小但辨识度高 */
    public static void impact(Level level, Vec3 at, int color, float radius) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        DustParticleOptions dust = dustOf(color, Math.max(0.6F, radius * 4.0F));
        serverLevel.sendParticles(dust, at.x, at.y, at.z, 14, radius * 0.6, radius * 0.6, radius * 0.6, 0.05);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z,
                5, radius * 0.4, radius * 0.4, radius * 0.4, 0.02);
    }

    /** 冲击环：水平扩散的一圈粒尘，用于范围技能的"释放"帧 */
    public static void shockwaveRing(Level level, Vec3 center, double radius, int color, int count) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        DustParticleOptions dust = dustOf(color, 1.6F);
        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2 / count);
            double vx = Math.cos(angle) * 0.35;
            double vz = Math.sin(angle) * 0.35;
            serverLevel.sendParticles(dust,
                    center.x + Math.cos(angle) * 0.5,
                    center.y + 0.1,
                    center.z + Math.sin(angle) * 0.5,
                    1, vx, 0.05, vz, 0.6);
        }
    }

    /** 柱状喷发：用于召唤 / 破土而出的竖直粒子柱 */
    public static void pillar(Level level, Vec3 base, double height, int color, int perTick) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        DustParticleOptions dust = dustOf(color, 1.8F);
        for (int i = 0; i < perTick; i++) {
            double y = base.y + serverLevel.random.nextDouble() * height;
            serverLevel.sendParticles(dust,
                    base.x + (serverLevel.random.nextDouble() - 0.5) * 0.8,
                    y,
                    base.z + (serverLevel.random.nextDouble() - 0.5) * 0.8,
                    1, 0, 0.08, 0, 0.0);
        }
    }

    /** 方向性吐息：沿视线方向的锥形粒子，用于远程 / 喷射技能 */
    public static void breath(Level level, Vec3 origin, Vec3 direction,
                              double length, int color, int count) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        DustParticleOptions dust = dustOf(color, 1.5F);
        Vec3 dir = direction.normalize();
        for (int i = 0; i < count; i++) {
            double t = serverLevel.random.nextDouble() * length;
            double spread = 0.15 + t * 0.08;
            serverLevel.sendParticles(dust,
                    origin.x + dir.x * t + (serverLevel.random.nextDouble() - 0.5) * spread,
                    origin.y + dir.y * t + (serverLevel.random.nextDouble() - 0.5) * spread,
                    origin.z + dir.z * t + (serverLevel.random.nextDouble() - 0.5) * spread,
                    1, dir.x * 0.08, dir.y * 0.08, dir.z * 0.08, 0.02);
        }
    }

    /** 血雾：受伤 / 自残类技能的反馈 */
    public static void bloodMist(Level level, Vec3 at, int count) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + 0.4, at.z, count, 0.3, 0.3, 0.3, 0.05);
    }

    /** 通用粒子直通（保留扩展口子） */
    public static void emit(Level level, ParticleOptions particle, Vec3 at,
                            int count, double spread, double speed) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }
}
