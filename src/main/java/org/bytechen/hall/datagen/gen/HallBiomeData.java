package org.bytechen.hall.datagen.gen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Hall 感染群系数据生成器。
 * <p>
 * 定义一个被 Hall 感染后的群系 —— Hall Wasteland，
 * 特征为白色浓雾、苍白天空、白色粒子飘浮。
 */
@SuppressWarnings("removal")
public class HallBiomeData {

    /** Hall 废土群系 ResourceKey */
    public static final ResourceKey<Biome> HALL_WASTELAND = ResourceKey.create(
            Registries.BIOME, new ResourceLocation("hall", "hall_wasteland"));

    /**
     * 在 datagen 的 bootstrap 阶段注册群系。
     */
    public static void bootstrap(BootstapContext<Biome> context) {
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> configuredCarvers = context.lookup(Registries.CONFIGURED_CARVER);

        context.register(HALL_WASTELAND, createHallBiome(placedFeatures, configuredCarvers));
    }

    /**
     * 创建 Hall 废土群系。
     * <p>
     * 视觉特征：
     * <ul>
     *   <li>白色浓雾 (fogColor = 0xD0D8E0)</li>
     *   <li>苍白天空 (skyColor = 0xC8D0D8)</li>
     *   <li>灰白水面 (waterColor = 0xA0A8B0, waterFogColor = 0x889098)</li>
     *   <li>白色雪花粒子 (ambientParticle = SNOWFLAKE)</li>
     *   <li>褪色植被 (foliageColorOverride = 0x8A9A8A, grassColorOverride = 0x9AAA9A)</li>
     * </ul>
     */
    public static Biome createHallBiome(HolderGetter<PlacedFeature> features,
                                         HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        // 1. 生物生成设置：空（死寂群系）
        MobSpawnSettings.Builder spawnSettings = new MobSpawnSettings.Builder();

        // 2. 地形修饰：枯死灌木
        BiomeGenerationSettings.Builder genSettings = new BiomeGenerationSettings.Builder(features, carvers);
        genSettings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                VegetationPlacements.PATCH_DEAD_BUSH);

        // 3. 环境与效果：纯白天空 + 白雾风格
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder()
                // 水体：深灰白
                .waterColor(0x889098)
                .waterFogColor(0x687078)
                // 核心：白色浓雾（利用原版群系颜色混合实现自然过渡）
                .fogColor(0xE0E4EC)
                // 纯白天空（原版 ClientLevel.getSkyColor() 会在群系边界做平滑混合）
                .skyColor(0xD8DCE8)
                // 褪色植被（更深的白化色）
                .foliageColorOverride(0x7A8A7A)
                .grassColorOverride(0x8A9A8A)
                // 白色雪花粒子：模拟感染孢子在大气中飘浮
                .ambientParticle(new AmbientParticleSettings(
                        ParticleTypes.SNOWFLAKE,
                        0.008f
                ))
                // 环境音效：使用灵魂沙谷的低语风声
                .ambientLoopSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP)
                .ambientMoodSound(new AmbientMoodSettings(
                        SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 6000, 8, 2.0D))
                // 背景音乐：使用末地音乐（空灵感）
                .backgroundMusic(new Music(
                        SoundEvents.MUSIC_END,
                        600,
                        2400,
                        true
                ));

        return new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(0.5f)
                .downfall(0.3f)
                .specialEffects(effects.build())
                .mobSpawnSettings(spawnSettings.build())
                .generationSettings(genSettings.build())
                .build();
    }
}
