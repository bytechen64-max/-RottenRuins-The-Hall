package org.bytechen.hall.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 溃烂肉屑粒子 —— 溃烂系生物身上掉下来的血肉碎块。
 * <p>
 * 比普通粒子更沉：有重力、会往下掉，碰到地面会停住（{@code hasPhysics}），
 * 贴图取自 {@code assets/hall/textures/particle/ulcerated_meat.png}
 * （描述文件由 datagen 生成到 {@code assets/hall/particles/ulcerated_meat.json}）。
 */
public class UlceratedMeatParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected UlceratedMeatParticle(ClientLevel level, double x, double y, double z,
                                    double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.pickSprite(sprites); // 贴图集里随机取一张

        this.gravity = 0.9F;      // 往下掉落
        this.friction = 0.96F;
        this.hasPhysics = true;
        this.lifetime = 30 + this.random.nextInt(30);
        this.quadSize = 0.16F + this.random.nextFloat() * 0.12F;

        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites); // 贴图集有多帧时按存活时间切换（单帧也无副作用）
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 客户端提供器：{@code RegisterParticleProvidersEvent#registerSpriteSet} */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public UlceratedMeatParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                    double x, double y, double z,
                                                    double vx, double vy, double vz) {
            return new UlceratedMeatParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
