package org.bytechen.hall.overworld.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Hall 各维度的运行时标识。
 * <p>
 * 这里是维度的<b>唯一事实来源</b>：datagen（{@code FleshDimensionData}）
 * 与运行时逻辑（如 {@code FleshRiftBlock} 的传送）都引用这些常量，
 * 避免维度 ID 在多处重复书写而失配。
 * <p>
 * 注意 {@link #HEALL_STEM} 与 {@link #HEALL_LEVEL} 使用
 * <b>同一个 ResourceLocation</b>：存档中的 LevelStem 载入后即以此键
 * 注册成实际可传送的 Level。
 */
public final class HallDimensions {

    /**
     * 血肉庭园维度 ID：{@code hall:heall}。
     * <p>
     * 它同时是维度类型（{@code minecraft:dimension_type}）、
     * 维度本体（{@code minecraft:dimension}）与运行时 Level 的注册名。
     * <p>
     * <b>注意与群系名不一致：</b>群系用的是 {@code hall:flesh_marrow}，
     * 噪声设置也是 {@code hall:flesh_marrow}。两者本就允许不同名，
     * 但查注册表时别混淆。
     */
    public static final ResourceLocation HEALL_ID =
            ResourceLocation.fromNamespaceAndPath("hall", "heall");

    /** 维度类型（datapack 注册表 {@code minecraft:dimension_type}） */
    public static final ResourceKey<DimensionType> HEALL_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, HEALL_ID);

    /** 维度本体（datapack 注册表 {@code minecraft:dimension}） */
    public static final ResourceKey<LevelStem> HEALL_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, HEALL_ID);

    /** 运行时维度键，用于 {@code server.getLevel(...)} 与传送 */
    public static final ResourceKey<Level> HEALL_LEVEL =
            ResourceKey.create(Registries.DIMENSION, HEALL_ID);

    private HallDimensions() {}
}
