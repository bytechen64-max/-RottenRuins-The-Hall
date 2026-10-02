package org.bytechen.hall.datagen.gen;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import org.bytechen.hall.HallMod;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Hall 世界生成数据提供器。
 * <p>
 * 在 datagen 阶段将自定义群系、维度类型、噪声设置与维度本体
 * 注册到数据包中，使其可以在游戏中被引用。
 * <p>
 * <b>为什么要把原版噪声数据也列进来：</b>
 * datagen 的 {@link RegistrySetBuilder} 是一个全新、空白的注册表，
 * 只在玩家存档里才存在「原版数据已加载」的前提。血肉维度的地形复用了
 * 原版的 {@code minecraft:nether/base_3d_noise} 密度函数与
 * {@code minecraft:patch} 噪声，如果不在 builder 里注册原版噪声数据，
 * 我们自己的噪声设置解析时会报
 * {@code Trying to access unbound value}。
 * <p>
 * 注册时只保留本模组（hall）命名空间的产物，原版条目不会写进本模组的数据包，
 * 它们只用于让 builder 内部的引用可解析。
 * <p>
 * 顺序要求（后者依赖前者）：
 * 噪声 → 噪声设置 → ConfiguredFeature → PlacedFeature → 群系 → 维度类型 → 维度本体。
 */
public class HallWorldGenData extends DatapackBuiltinEntriesProvider {

    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.NOISE, HallNoiseData::bootstrap)
            .add(Registries.NOISE_SETTINGS, FleshDimensionData::bootstrapNoiseSettings)
            .add(Registries.CONFIGURED_FEATURE, HallFeatureData::bootstrapConfiguredFeatures)
            .add(Registries.PLACED_FEATURE, HallFeatureData::bootstrapPlacedFeatures)
            .add(Registries.BIOME, HallBiomeData::bootstrap)
            .add(Registries.DIMENSION_TYPE, FleshDimensionData::bootstrapDimensionType)
            .add(Registries.LEVEL_STEM, Lifecycle.stable(), FleshDimensionData::bootstrapLevelStem);

    public HallWorldGenData(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(HallMod.MODID));
    }
}
