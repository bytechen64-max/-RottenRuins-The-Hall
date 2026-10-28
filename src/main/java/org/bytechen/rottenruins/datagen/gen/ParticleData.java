package org.bytechen.rottenruins.datagen.gen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ParticleDescriptionProvider;
import org.bytechen.rottenruins.HallMod;
import org.bytechen.rottenruins.overworld.registry.RegisterParticles;

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
        //      {"textures":["rotten_ruins:heart_lose0", ...]}，里面没有任何动画段
        //      —— 序列播放完全是客户端 setSpriteFromAge 的事（见 HeartLoseParticle）。
        //      所以这里给 false 才是"按 0→4 的正序"，给 true 会把帧序颠倒。
        //
        // 用 spriteSet(type, Iterable) 逐帧枚举就绕开了命名约束：
        // 顺序由我们自己的数组决定，帧数也不再依赖 Forge 的编号规则，
        // 而且它同样会逐张校验 png 是否存在（写错文件名会在 datagen 期就报错，不会拖到运行期）。
        spriteSet(RegisterParticles.HEART_LOSE.get(),
                RegisterParticles.heartLoseFrames());

        // 血滴（贴图：assets/hall/textures/particle/blood.png）
        //
        // ── 单张精灵用 sprite(...)，不要用 spriteSet(...) ──
        //
        // blood.png 只有一张图（不是序列帧），所以这里必须走 sprite(...)：它生成的是
        // {"textures":["rotten_ruins:blood"]}，客户端 pickSprite 取到的就是这一张。
        // 反过来如果误用 spriteSet(type, baseName, 1, false)，虽然也能生成同一份 JSON，
        // 但"spriteSet"在注册侧要求客户端按序列帧处理，语义上就错了 ——
        // 这里用 sprite() 是"一张图"的准确表达。
        //
        // 注意精灵名就是文件名本身（不带 textures/particle/ 前缀）：
        // 原版粒子图集（assets/minecraft/atlases/particles.json）的 prefix 是空的，
        // 写成 hall:particle/blood 会在图集里找不到、显示成紫黑缺失贴图。
        sprite(RegisterParticles.BLOOD.get(),
                new ResourceLocation(HallMod.MODID, "blood"));

        // 肉块（贴图：assets/hall/textures/particle/meat.png）—— 同样是一张静态图
        sprite(RegisterParticles.MEAT.get(),
                new ResourceLocation(HallMod.MODID, "meat"));

        // 血腥爆炸（贴图：assets/hall/textures/particle/explosion_bloody0.png ~ explosion_bloody15.png）
        //
        // 16 帧的序列帧，和 heart_lose 一样用 spriteSet(type, Iterable) 逐帧枚举：
        // 文件名是 explosion_bloody0（不带下划线），Forge 的 baseName+下划线重载拼不出来；
        // 顺序 / 帧数由 RegisterParticles.explosionBloodyFrames() 这一份定义统一提供，
        // 客户端 ExplosionBloodyParticle 的 lifetime 也取自同一个常量。
        //
        // 这里最容易踩的坑是"看起来像单图就写了 sprite(EXPLOSION_BLOODY, 'explosion_bloody')"
        // —— 那会写成 {"textures":["rotten_ruins:explosion_bloody"]}，而图集里根本没有这张图
        // （实际叫 explosion_bloody0..15），结果是紫黑块。datagen 的 fileHelper 校验只能
        // 查出"文件不存在"，查不出"你把序列帧写成了单图"，所以这条注释留在这里当护栏。
        spriteSet(RegisterParticles.EXPLOSION_BLOODY.get(),
                RegisterParticles.explosionBloodyFrames());
    }
}
