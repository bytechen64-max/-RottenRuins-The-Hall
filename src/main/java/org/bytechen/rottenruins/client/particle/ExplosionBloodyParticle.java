package org.bytechen.rottenruins.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;

/**
 * 血腥爆炸粒子：16 帧序列动画的血雾。lifetime 必取帧数（{@link RegisterParticles#EXPLOSION_BLOODY_FRAMES}）：
 * SpriteSet 用 index = age*(n-1)/lifetime 整数除法，寿命小于帧数会跳帧。批次必须用
 * PARTICLE_SHEET_TRANSLUCENT（LIT 会 disableBlend()，alpha 边缘被硬切成黑边）。
 */
public class ExplosionBloodyParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected ExplosionBloodyParticle(ClientLevel level, double x, double y, double z,
                                      double xSpeed, double ySpeed, double zSpeed,
                                      SpriteSet sprites) {
        super(level, x, y, z);

        this.sprites = sprites;

        // 不随重力下落，也不采用调用方传进来的速度（爆炸固定在实体原位置）
        this.gravity = 0.0F;
        this.friction = 0.9F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;

        this.quadSize = 1.4F;

        // 帧数 == 寿命（理由见类注释）；出生即要贴图，渲染可能发生在第一次 tick 之前
        this.lifetime = RegisterParticles.EXPLOSION_BLOODY_FRAMES;

        this.setSpriteFromAge(this.sprites);
    }

    /** 固定全亮（0xF000F0 = 天空光15 | 方块光15）：爆炸是发光事件，夜里与洞穴里也得看得见。 */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public ExplosionBloodyParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                      double x, double y, double z,
                                                      double vx, double vy, double vz) {
            return new ExplosionBloodyParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
