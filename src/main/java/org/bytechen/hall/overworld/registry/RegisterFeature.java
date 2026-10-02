package org.bytechen.hall.overworld.registry;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bytechen.hall.HallMod;
import org.bytechen.hall.overworld.registry.feature.HallPatchFeature;
import org.bytechen.hall.overworld.registry.feature.HallTreeFeature;
import org.bytechen.hall.overworld.registry.feature.HallVineFeature;

/**
 * Hall 地物（Feature）注册。
 * <p>
 * 地物的 Java 实现必须落在 {@code minecraft:feature} 这个 Forge 注册表里，
 * 而「长什么样」（ConfiguredFeature）与「长在哪」（PlacedFeature）则由
 * datagen 产出，见 {@code HallFeatureData}。
 *
 * <h2>为什么草和花是「地物」而不是直接用原版的 patch</h2>
 * 原版的 {@code minecraft:patch_grass_plain} / {@code flower_plains} 长出的是
 * <b>原版</b>的草与花。血肉庭园要长的是王庭草（{@code hall:hall_grass}）与
 * 王庭花（{@code hall:hall_flower}），因此各自需要一个自定义地物。
 */
public class RegisterFeature {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, HallMod.MODID);

    /** 王庭树：王庭原木 + 王庭树叶 */
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> HALL_TREE =
            FEATURES.register("hall_tree",
                    () -> new HallTreeFeature(NoneFeatureConfiguration.CODEC));

    /**
     * 王庭草丛：把 {@code hall:hall_grass} 铺成一片。
     * <p>
     * 注意这里在 supplier 内部才调用 {@code RegisterBlock.X.get()} ——
     * 方块与地物的注册顺序不保证，提前取值可能拿到 null。
     */
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> HALL_GRASS_PATCH =
            FEATURES.register("hall_grass_patch",
                    () -> new HallPatchFeature(NoneFeatureConfiguration.CODEC,
                            RegisterBlock.HALL_GRASS.get()));

    /** 王庭花丛：把 {@code hall:hall_flower} 铺成一片 */
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> HALL_FLOWER_PATCH =
            FEATURES.register("hall_flower_patch",
                    () -> new HallPatchFeature(NoneFeatureConfiguration.CODEC,
                            RegisterBlock.HALL_FLOWER.get()));

    /** 王庭藤蔓（向下）：从崖壁与洞顶垂挂 */
    public static final RegistryObject<Feature<HallVineFeature.Config>> HALL_VINE_DOWN =
            FEATURES.register("hall_vine_down",
                    () -> new HallVineFeature(HallVineFeature.Config.CODEC));

    /** 王庭藤蔓（向上）：从地面竖着长起来 */
    public static final RegistryObject<Feature<HallVineFeature.Config>> HALL_VINE_UP =
            FEATURES.register("hall_vine_up",
                    () -> new HallVineFeature(HallVineFeature.Config.CODEC));

    private RegisterFeature() {}
}
