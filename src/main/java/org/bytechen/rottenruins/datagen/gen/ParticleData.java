package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ParticleDescriptionProvider;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;
import org.bytechen.rottenruins.utils.ModUtils;

public class ParticleData extends ParticleDescriptionProvider {
    public ParticleData(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, existingFileHelper);
    }

    @Override
    protected void addDescriptions() {
        // 精灵名就是文件名本身（原版粒子图集 assets/minecraft/atlases/particles.json 的 prefix 为空），写成 hall:particle/xxx 会显示成紫黑缺失贴图。
        sprite(RegisterParticles.ULCERATED_MEAT.get(),
                ModUtils.modLoc("ulcerated_meat"));

        // 序列帧用 spriteSet(type, Iterable) 逐帧枚举：Forge 的 baseName+序号重载拼出的是 xxx_0，而贴图叫 heart_lose0（无下划线），datagen 会直接报 Texture does not exist；该重载第四参 reverse 是倒放，不是分段数。
        spriteSet(RegisterParticles.HEART_LOSE.get(),
                RegisterParticles.heartLoseFrames());

        // 单张静态图必须用 sprite(...)：误用 spriteSet(type, baseName, 1, false) 生成的 JSON 相同，但注册侧语义要求按序列帧处理。
        sprite(RegisterParticles.BLOOD.get(),
                ModUtils.modLoc("blood"));

        sprite(RegisterParticles.MEAT.get(),
                ModUtils.modLoc("meat"));

        // explosion_bloody 是 16 帧序列帧（explosion_bloody0..15），误写成单图 sprite(...) 会变紫黑块；帧序与客户端 lifetime 统一取自 RegisterParticles。
        spriteSet(RegisterParticles.EXPLOSION_BLOODY.get(),
                RegisterParticles.explosionBloodyFrames());
    }
}
