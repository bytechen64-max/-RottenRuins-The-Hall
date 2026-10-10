package org.bytechen.hall.datagen.gen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ParticleDescriptionProvider;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.RegisterParticles;

public class ParticleData extends ParticleDescriptionProvider {
    public ParticleData(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, existingFileHelper);
    }

    @Override
    protected void addDescriptions() {
        // 溃烂肉屑（贴图：assets/hall/textures/particle/ulcerated_meat.png）
        // 注意：原版粒子图集（assets/minecraft/atlases/particles.json）的 prefix 是空的，
        // 精灵名就是文件名本身，不能写成 hall:particle/ulcerated_meat，否则会显示成紫黑缺失贴图。
        sprite(RegisterParticles.ULCERATED_MEAT.get(),
                new ResourceLocation(HallMod.MODID, "ulcerated_meat"));

        // 失心粒子（贴图：assets/hall/textures/particle/heart_lose0.png ~ heart_lose4.png）
        //
        // ── 为什么逐帧枚举，而不是用 baseName + 帧数那个重载 ──
        //
        // Forge 的 spriteSet(type, baseName, numOfTextures, reverse) 有两处坑，
        // 是实测撞出来的：
        //   ① 它拼出来的资源名是 baseName + "_" + 序号，即 hall:heart_lose_0。
        //      而美术给的贴图叫 heart_lose0（不带下划线），于是 datagen 直接抛
        //      "Texture 'hall:heart_lose_4' does not exist in any known resource pack"。
        //   ② 第四个参数名字叫 reverse，含义是"倒放"，不是"生成动画段"。
        //      ParticleDescriptionProvider 写出来的 JSON 就是一句纯数组
        //      {"textures":["hall:heart_lose0", ...]}，里面没有任何动画段
        //      —— 序列播放完全是客户端 setSpriteFromAge 的事（见 HeartLoseParticle）。
        //      所以这里给 false 才是"按 0→4 的正序"，给 true 会把帧序颠倒。
        //
        // 用 spriteSet(type, Iterable) 逐帧枚举就绕开了命名约束：
        // 顺序由我们自己的数组决定，帧数也不再依赖 Forge 的编号规则，
        // 而且它同样会逐张校验 png 是否存在（写错文件名会在 datagen 期就报错，不会拖到运行期）。
        spriteSet(RegisterParticles.HEART_LOSE.get(),
                RegisterParticles.heartLoseFrames());
    }
}
