package org.bytechen.rottenruins.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Short-lived full-bright trail using the supplied hall_dust sprite. */
public final class HallDustParticle extends TextureSheetParticle {
    private final float initialSize;
    private float angularVelocity;
    private HallDustParticle(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        lifetime = 10 + random.nextInt(9);
        initialSize = 0.085F + random.nextFloat() * 0.075F;
        quadSize = initialSize;
        roll = oRoll = random.nextFloat() * (float) (Math.PI * 2);
        angularVelocity = (random.nextBoolean() ? 1 : -1) * (0.12F + random.nextFloat() * 0.30F);
        friction = 0.9F;
        hasPhysics = false;
        xd = vx + random.nextGaussian() * 0.018D;
        yd = vy + random.nextGaussian() * 0.018D;
        zd = vz + random.nextGaussian() * 0.018D;
        pickSprite(sprites);
    }
    @Override public void tick() {
        super.tick();
        oRoll = roll;
        roll += angularVelocity;
        angularVelocity *= 0.86F;
    }
    @Override public float getQuadSize(float partialTick) {
        float remaining = Math.max(0, 1.0F - (age + partialTick) / lifetime);
        return initialSize * remaining * remaining;
    }
    @Override protected int getLightColor(float partialTick) { return 0xF000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public HallDustParticle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new HallDustParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
