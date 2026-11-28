package org.bytechen.rottenruins.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;

/** 按 0..8 顺序播放用户提供的九帧粒子，每帧持续两个 tick。 */
public final class ManicDiscomfortParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private ManicDiscomfortParticle(ClientLevel level, double x, double y, double z,
                                    double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        lifetime = RegisterParticles.MANIC_DISCOMFORT_FRAMES * 2;
        quadSize = 0.12F + random.nextFloat() * 0.04F;
        gravity = -0.015F;
        friction = 0.94F;
        hasPhysics = false;
        xd = vx;
        yd = vy + 0.012D;
        zd = vz;
        setSprite(sprites.get(0, RegisterParticles.MANIC_DISCOMFORT_FRAMES - 1));
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) setSprite(sprites.get(Math.min(age / 2,
                RegisterParticles.MANIC_DISCOMFORT_FRAMES - 1), RegisterParticles.MANIC_DISCOMFORT_FRAMES - 1));
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public ManicDiscomfortParticle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new ManicDiscomfortParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
