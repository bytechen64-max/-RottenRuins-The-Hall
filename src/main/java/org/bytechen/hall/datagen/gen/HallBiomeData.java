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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.*;
import org.bytechen.hall.overworld.registry.SoundEventRegistry;
import org.bytechen.hall.overworld.registry.EntityTypeRegistry;
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

    /** 血肉庭园群系 ResourceKey（维度 hall:heall 的主体群系） */
    public static final ResourceKey<Biome> FLESH_MARROW = ResourceKey.create(
            Registries.BIOME, new ResourceLocation("hall", "flesh_marrow"));

    /**
     * 在 datagen 的 bootstrap 阶段注册群系。
     */
    public static void bootstrap(BootstapContext<Biome> context) {
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> configuredCarvers = context.lookup(Registries.CONFIGURED_CARVER);

        context.register(HALL_WASTELAND, createHallBiome(placedFeatures, configuredCarvers));
        context.register(FLESH_MARROW, createFleshBiome(placedFeatures, configuredCarvers));
    }

    /**
     * 创建血肉庭园群系 —— 开放天空的宏伟王庭地貌。
     * <p>
     * 与最初的「永夜 + 暗红血雾」版本不同，这里与王庭群系保持同一套视觉语言：
     * <ul>
     *   <li>苍白天空 (skyColor = 0xD8DCE8) —— 与王庭群系一致，天空可见</li>
     *   <li>白色浓雾 (fogColor = 0xE0E4EC)</li>
     *   <li>灰白水面 (waterColor = 0x889098)</li>
     *   <li>王庭草 / 花 / 王庭树构成地表植被</li>
     * </ul>
     * 注意：维度类型必须开放天空（has_ceiling = false +
     * has_skylight = true）且不注册自定义维度特效，否则天空会被顶掉。
     */
    public static Biome createFleshBiome(HolderGetter<PlacedFeature> features,
                                        HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        // 1. 生物生成：王庭溃烂系生物栖息于此，可长期对抗
        MobSpawnSettings.Builder spawnSettings = new MobSpawnSettings.Builder();
        spawnSettings.addSpawn(MobCategory.MONSTER,
                new MobSpawnSettings.SpawnerData(
                        EntityTypeRegistry.SCOUT.get(), 40, 2, 4));
        spawnSettings.addSpawn(MobCategory.MONSTER,
                new MobSpawnSettings.SpawnerData(
                        EntityTypeRegistry.MONOLITH.get(), 20, 1, 2));
        spawnSettings.addSpawn(MobCategory.MONSTER,
                new MobSpawnSettings.SpawnerData(
                        EntityTypeRegistry.BONECRUSHER.get(), 25, 1, 3));

        // 2. 地形修饰：王庭树成林，王庭草与王庭花铺开
        //
        // 注意：这里引用的是本模组自己的地物（HallFeatureData），
        // 而不是 minecraft:patch_grass_plain / flower_plains ——
        // 那两个会长出<b>原版</b>的草与花，与王庭的视觉语言不一致。
        BiomeGenerationSettings.Builder genSettings = new BiomeGenerationSettings.Builder(features, carvers);
        genSettings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                HallFeatureData.HALL_TREES);
        genSettings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                HallFeatureData.HALL_GRASS_PATCHES);
        genSettings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                HallFeatureData.HALL_FLOWER_PATCHES);
        // 王庭藤蔓：向下从崖壁与洞顶垂挂，向上从地面竖着长起来
        genSettings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                HallFeatureData.HALL_VINES_DOWN);
        genSettings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                HallFeatureData.HALL_VINES_UP);
        // 王庭烬痕枯灌木作为点缀（复用王庭群系的地表风格）
        genSettings.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                VegetationPlacements.PATCH_DEAD_BUSH);

        // 3. 环境与效果：与王庭群系同源的苍白天空 + 白色浓雾
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder()
                .waterColor(0x889098)
                .waterFogColor(0x687078)
                .fogColor(0xE0E4EC)
                .skyColor(0xD8DCE8)
                .foliageColorOverride(0x7A8A7A)
                .grassColorOverride(0x8A9A8A)
                // 白色雪花粒子：飘浮的感染孢子
                .ambientParticle(new AmbientParticleSettings(
                        ParticleTypes.SNOWFLAKE,
                        0.008f
                ))
                .ambientLoopSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP)
                .ambientMoodSound(new AmbientMoodSettings(
                        SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 6000, 8, 2.0D))
                .backgroundMusic(new Music(
                        SoundEventRegistry.BIOME_MUSIC.getHolder().orElseThrow(),
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
                // 背景音乐：使用自定义群系音乐
                .backgroundMusic(new Music(
                        SoundEventRegistry.BIOME_MUSIC.getHolder().orElseThrow(),
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
